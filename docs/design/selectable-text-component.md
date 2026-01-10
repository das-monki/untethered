# Selectable Text Component

## Overview

### Problem Statement

Text selection does not work in the message detail view or other views that display selectable text. The SwiftUI `.textSelection(.enabled)` modifier on `Text` views inside `ScrollView` is unreliable on both iOS (long-press fails to trigger selection UI) and macOS (click-drag selection doesn't work). Users cannot select and copy portions of messages—they can only copy entire messages.

### Goals

1. Enable reliable text selection in message detail view (both iOS and macOS)
2. Enable reliable text selection in command output views
3. Create a shared component that works identically on both platforms
4. Maintain visual consistency with current `Text` rendering
5. Minimize code complexity

### Non-goals

- Code block-specific copy buttons (future enhancement)
- Markdown rendering or syntax highlighting
- Selection state tracking for "Copy Selection" buttons
- Changes to the main message list (performance-critical, uses truncated text)

## Background & Context

### Current State

Four views use `.textSelection(.enabled)` on `Text` views:

| View | Location | Purpose | In Scope |
|------|----------|---------|----------|
| `MessageDetailView` | `ConversationView.swift:1206` | Full message content | ✓ |
| `CommandOutputDetailView` | `CommandOutputDetailView.swift:167` | Command output | ✓ |
| `DebugLogsView` | `DebugLogsView.swift:43` | Debug logs | ✓ |
| `CommandExecutionView` | `CommandExecutionView.swift:162` | Streaming output lines | ✗ (see Scope Note) |

Three views (MessageDetailView, CommandOutputDetailView, DebugLogsView) use a single `Text` in a `ScrollView`:
```swift
Text(content)
    .font(...)
    .textSelection(.enabled)
```

This pattern fails because SwiftUI's text selection gesture recognizers conflict with `ScrollView` scrolling gestures.

### Why Now

Users cannot copy portions of Claude responses, which is a core workflow for code assistance. The workaround (copying entire messages) is inadequate for long responses.

### Related Work

- @docs/design/desktop-ux-improvements.md - Related macOS UX improvements
- @STANDARDS.md - Platform-specific patterns

## Detailed Design

### Approach

Replace `Text` + `.textSelection(.enabled)` with platform-native text views wrapped for SwiftUI:
- **iOS**: `UITextView` with `isEditable = false`
- **macOS**: `NSTextView` with `isEditable = false`

Both provide reliable native text selection without the disabled/grayed appearance of `TextEditor.disabled(true)`.

### Component API

```swift
// SelectableText.swift
// A cross-platform text view with reliable native text selection

import SwiftUI
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct SelectableText: View {
    let text: String
    var isMonospaced: Bool = false
    var fontSize: CGFloat? = nil
    var textColor: Color? = nil  // nil = use system label color (adapts to light/dark)

    var body: some View {
        #if os(iOS)
        SelectableTextView_iOS(
            text: text,
            isMonospaced: isMonospaced,
            fontSize: fontSize,
            textColor: textColor
        )
        #elseif os(macOS)
        SelectableTextView_macOS(
            text: text,
            isMonospaced: isMonospaced,
            fontSize: fontSize,
            textColor: textColor
        )
        #endif
    }
}
```

**API Parameters:**
- `text`: The string content to display
- `isMonospaced`: Use monospaced font (default: `false`)
- `fontSize`: Override default font size in points (default: `nil` = system body size)
- `textColor`: Override text color (default: `nil` = system label color, adapts to dark mode)

### iOS Implementation

```swift
#if os(iOS)
struct SelectableTextView_iOS: UIViewRepresentable {
    let text: String
    let isMonospaced: Bool
    let fontSize: CGFloat?
    let textColor: Color?

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.isEditable = false
        textView.isSelectable = true
        textView.isScrollEnabled = false  // Let parent ScrollView handle scrolling
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.setContentHuggingPriority(.defaultHigh, for: .vertical)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        textView.text = text
        textView.font = uiFont
        textView.textColor = uiColor
    }

    private var uiFont: UIFont {
        let size = fontSize ?? UIFont.preferredFont(forTextStyle: .body).pointSize
        if isMonospaced {
            return UIFont.monospacedSystemFont(ofSize: size, weight: .regular)
        } else {
            return UIFont.systemFont(ofSize: size)
        }
    }

    private var uiColor: UIColor {
        if let color = textColor {
            return UIColor(color)
        }
        return .label  // System label color adapts to light/dark mode
    }
}
#endif
```

### macOS Implementation

```swift
#if os(macOS)
struct SelectableTextView_macOS: NSViewRepresentable {
    let text: String
    let isMonospaced: Bool
    let fontSize: CGFloat?
    let textColor: Color?

    func makeNSView(context: Context) -> NSScrollView {
        let scrollView = NSTextView.scrollableTextView()
        guard let textView = scrollView.documentView as? NSTextView else {
            return scrollView
        }

        textView.isEditable = false
        textView.isSelectable = true
        textView.backgroundColor = .clear
        textView.drawsBackground = false
        textView.textContainerInset = .zero
        textView.textContainer?.lineFragmentPadding = 0

        // Disable scroll view's own scrolling - parent handles it
        scrollView.hasVerticalScroller = false
        scrollView.hasHorizontalScroller = false
        scrollView.borderType = .noBorder
        scrollView.drawsBackground = false

        return scrollView
    }

    func updateNSView(_ scrollView: NSScrollView, context: Context) {
        guard let textView = scrollView.documentView as? NSTextView else { return }
        textView.string = text
        textView.font = nsFont
        textView.textColor = nsColor
    }

    private var nsFont: NSFont {
        let size = fontSize ?? NSFont.systemFontSize
        if isMonospaced {
            return NSFont.monospacedSystemFont(ofSize: size, weight: .regular)
        } else {
            return NSFont.systemFont(ofSize: size)
        }
    }

    private var nsColor: NSColor {
        if let color = textColor {
            return NSColor(color)
        }
        return .labelColor  // System label color adapts to light/dark mode
    }
}
#endif
```

### Sizing Behavior

Native text views need to work within SwiftUI's layout system.

**iOS:** Content compression/hugging priorities are set in `makeUIView` to allow the text view to size naturally within the parent layout.

**macOS:** NSTextView in a non-scrolling container calculates its intrinsic size based on content.

Both rely on the parent `ScrollView` (in SwiftUI) to handle scrolling of long content.

### Scope Note: CommandExecutionView

`CommandExecutionView` uses `.textSelection(.enabled)` on individual lines within a `LazyVStack`. This is a different pattern from the other views (single large text block).

**Decision:** Exclude `CommandExecutionView` from initial scope. The per-line selection may work differently, and using `SelectableText` for many small views in a `LazyVStack` could cause performance issues. If selection is broken there, it should be addressed separately with performance testing.

### Usage Migration

**Before (MessageDetailView):**
```swift
ScrollView {
    Text(message.text)
        .font(.body)
        .textSelection(.enabled)
        .padding()
}
```

**After:**
```swift
ScrollView {
    SelectableText(text: message.text)
        .padding()
}
```

**Before (CommandOutputDetailView):**
```swift
Text(output.output)
    .font(.system(.body, design: .monospaced))
    .textSelection(.enabled)
    .padding(12)
    .background(Color.secondarySystemBackground)
    .cornerRadius(8)
```

**After:**
```swift
SelectableText(text: output.output, isMonospaced: true)
    .padding(12)
    .background(Color.secondarySystemBackground)
    .cornerRadius(8)
```

**Before (DebugLogsView):**
```swift
Text(logs)
    .font(.system(size: 10, design: .monospaced))
    .textSelection(.enabled)
```

**After:**
```swift
SelectableText(text: logs, isMonospaced: true, fontSize: 10)
```

### File Location

New file: `ios/VoiceCode/Utils/SelectableText.swift`

This follows the existing pattern of utility components in the Utils folder (see `ClipboardUtility.swift`, `NavigationController.swift`).

### Files to Modify

| File | Change |
|------|--------|
| `ConversationView.swift` | Replace `Text(...).textSelection(.enabled)` with `SelectableText` in `MessageDetailView` |
| `CommandOutputDetailView.swift` | Replace `Text(...).textSelection(.enabled)` with `SelectableText` |
| `DebugLogsView.swift` | Replace `Text(...).textSelection(.enabled)` with `SelectableText(fontSize: 10)` |

**Note:** `CommandExecutionView.swift` is excluded from initial scope (see Scope Note above).

## Verification Strategy

### Testing Approach

#### Manual Testing

| Platform | View | Test Case | Expected Result |
|----------|------|-----------|-----------------|
| iOS | MessageDetailView | Long-press on text | Selection handles appear |
| iOS | MessageDetailView | Drag selection handles | Selection adjusts |
| iOS | MessageDetailView | Tap "Copy" in popup | Selected text copied |
| iOS | CommandOutputDetailView | Long-press on output | Selection handles appear |
| iOS | DebugLogsView | Long-press on logs | Selection handles appear |
| macOS | MessageDetailView | Click and drag | Text selects |
| macOS | MessageDetailView | Cmd+A | All text selects |
| macOS | MessageDetailView | Cmd+C | Selected text copied |
| macOS | CommandOutputDetailView | Click and drag | Text selects |
| macOS | DebugLogsView | Click and drag | Text selects |

#### Visual Verification

- Text renders with same appearance as current `Text` view
- No gray/disabled appearance
- Correct font (body or monospaced as appropriate)
- Correct font size (especially 10pt for DebugLogsView)
- Text fills available width
- Parent ScrollView scrolls correctly
- No double-scrolling issues
- **Dark mode**: Text color adapts correctly (white/light text on dark background)

#### Unit Tests

SwiftUI views with `UIViewRepresentable`/`NSViewRepresentable` are difficult to unit test meaningfully. The core functionality (text selection) requires user interaction and cannot be verified programmatically.

**Approach:** Rely on manual testing and visual verification. No unit tests for `SelectableText` itself.

**Compile-time verification:** The build will fail if the API is used incorrectly, which provides basic correctness checking.

### Acceptance Criteria

1. Long-press selects text on iOS in MessageDetailView
2. Click-drag selects text on macOS in MessageDetailView
3. Selected text can be copied via system popup (iOS) or Cmd+C (macOS)
4. Text appearance matches current rendering (font, size, color)
5. Monospaced text renders correctly in command output views
6. Small font (10pt) renders correctly in DebugLogsView
7. Parent ScrollView scrolling works correctly (no conflicts)
8. No gray/disabled appearance on either platform
9. Empty text does not crash
10. Very long text renders without performance issues

## Alternatives Considered

### 1. TextEditor with disabled(true)

**Approach:** Use SwiftUI's `TextEditor` with `.disabled(true)`.

**Pros:**
- Pure SwiftUI, no platform wrappers
- Simple implementation

**Cons:**
- Grays out text on iOS when disabled
- Still has selection issues on some iOS versions

**Decision:** Rejected due to gray appearance requirement.

### 2. Custom gesture recognizer

**Approach:** Implement custom selection via gesture recognizers and rendering.

**Pros:**
- Full control over selection behavior
- Consistent across platforms

**Cons:**
- High complexity
- Must reimplement selection handles, copy popup, etc.
- Accessibility concerns

**Decision:** Rejected due to complexity.

### 3. WebView-based rendering

**Approach:** Render text in WKWebView with HTML/CSS.

**Pros:**
- Full text selection support
- Markdown rendering possible

**Cons:**
- Heavy for simple text display
- Memory overhead
- Complexity for message passing

**Decision:** Rejected due to overhead.

### 4. Keep current implementation, document limitation

**Approach:** Accept that selection doesn't work reliably.

**Decision:** Rejected because text selection is core functionality.

## Risks & Mitigations

### 1. Height Calculation Issues

**Risk:** Native text views may not size correctly in SwiftUI layouts.

**Mitigation:**
- Test with various text lengths
- Use intrinsic content size
- Add explicit frame constraints if needed

### 2. Scroll Conflicts

**Risk:** Native text view's internal scrolling may conflict with parent ScrollView.

**Mitigation:**
- Disable internal scrolling (`isScrollEnabled = false` on iOS)
- Configure NSScrollView to not show scrollers on macOS
- Test scroll behavior thoroughly

### 3. Keyboard Appearance on macOS

**Risk:** NSTextView may show cursor or respond to keyboard input even when not editable.

**Mitigation:**
- Set `isEditable = false` explicitly
- Test that clicking doesn't show cursor
- Test that typing doesn't insert text

### 4. Font Mismatch

**Risk:** Platform-native fonts may render differently than SwiftUI `Text`.

**Mitigation:**
- Use system fonts with matching styles
- Visual comparison testing
- Adjust font configuration if needed

### 5. Accessibility / Dynamic Type

**Risk:** Custom `fontSize` parameter bypasses Dynamic Type, making text inaccessible for users with vision impairments.

**Mitigation:**
- Only use custom `fontSize` where absolutely necessary (DebugLogsView uses 10pt for information density)
- Default font sizing uses `UIFont.preferredFont` (iOS) which respects Dynamic Type
- For macOS, `NSFont.systemFontSize` is used; Dynamic Type support on macOS is more limited
- Document that `fontSize` overrides accessibility scaling

**Future improvement:** Consider a `fontStyle` parameter that maps to accessibility-aware text styles rather than raw point sizes.

### Rollback Strategy

1. Changes are isolated to one new file and simple replacements in existing views
2. Can revert to `Text` + `.textSelection(.enabled)` by removing `SelectableText` usage
3. No data model or backend changes
4. Feature flag not needed—straightforward swap
