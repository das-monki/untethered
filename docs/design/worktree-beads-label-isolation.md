# Worktree Beads Label-Based Isolation

## Overview

### Problem Statement

Git worktrees share the same beads database by design. When creating a new worktree for focused work (e.g., "panel-color"), agents see all 25+ existing issues from the main repository, creating noise and confusion. The original goal was complete database isolation per worktree, but beads architecture intentionally shares the database across worktrees.

### Goals

1. Provide logical isolation of beads issues per worktree using labels
2. Ensure agents working in a worktree only see issues relevant to that worktree
3. Maintain merge compatibility - issues created in a worktree merge naturally with git
4. Preserve ability to view all issues when needed (by omitting the filter)

### Non-goals

- True database isolation (beads doesn't support this for worktrees)
- Modifying beads CLI behavior
- External BEADS_DIR configuration (too complex, doesn't merge with git)

## Background & Context

### Current State

The worktree session creation flow in @backend/src/voice_code/server.clj creates a git worktree and attempts to initialize beads. Due to beads' shared database architecture, `bd list` shows all issues from the main repository.

The `init-beads!` function in @backend/src/voice_code/worktree.clj currently uses `--force` and `--no-auto-import` flags, but these don't achieve isolation because beads resolves database paths through git worktree links back to the main repository.

### Why Now

Investigation revealed that beads worktree support is designed to share the database:
- [Beads WORKTREES.md](https://github.com/steveyegge/beads/blob/main/docs/WORKTREES.md) explicitly states "all worktrees share the same .beads database"
- The only isolation option (`BEADS_DIR`) places issues outside git, breaking merge capability

### Related Work

- @docs/design/worktree-beads-init-fix.md - Previous attempt at database isolation
- @backend/src/voice_code/worktree.clj - Worktree creation functions
- @backend/src/voice_code/recipes.clj - Recipe system that uses beads commands

## Detailed Design

### Label Convention

Each worktree gets a unique label following the pattern: `wt:<sanitized-worktree-name>`

Examples:
- Worktree "panel-color" → label `wt:panel-color`
- Worktree "Fix Auth Bug" → label `wt:fix-auth-bug`

The `wt:` prefix clearly identifies worktree-scoped labels and avoids collision with user-created labels.

### Data Model

#### New File: `.beads-worktree`

A simple text file placed in the worktree root containing the worktree label:

```
wt:panel-color
```

This file:
- Is NOT tracked in git (added to the worktree's git exclude file)
- Is read by agent instructions to determine the label filter
- Serves as a marker that this is a worktree with scoped beads

**Note on git worktree structure:** Git worktrees have a `.git` file (not directory) that points to the main repo's `.git/worktrees/<name>/` directory. The exclude file is located at `<main-repo>/.git/worktrees/<worktree-name>/info/exclude`. The implementation reads the `.git` file to resolve this path.

### API Design

No WebSocket API changes required. The worktree label is communicated through:
1. The `.beads-worktree` file in the worktree
2. The initial Claude prompt which includes beads filtering instructions

### Code Changes

#### File: backend/src/voice_code/worktree.clj

Replace `init-beads!` with `setup-beads-worktree!`:

```clojure
(defn resolve-worktree-git-dir
  "Resolve the actual git directory for a worktree.

  Git worktrees have a .git file (not directory) that contains a gitdir: pointer.
  This function reads that file and returns the actual git directory path.

  Parameters:
  - worktree-path: Path to the worktree directory

  Returns:
  The resolved git directory path, or nil if not a worktree."
  [worktree-path]
  (let [git-path (io/file worktree-path ".git")]
    (when (and (.exists git-path) (.isFile git-path))
      (let [content (str/trim (slurp git-path))]
        (when (str/starts-with? content "gitdir: ")
          (subs content 8))))))

(defn setup-beads-worktree!
  "Set up beads worktree context with label-based isolation.

  Instead of trying to create an isolated database (not supported by beads),
  we use labels to logically partition work within the shared database.

  Creates:
  1. .beads-worktree file containing the worktree label
  2. Excludes this file from git tracking

  Parameters:
  - worktree-path: Path to the worktree directory
  - worktree-name: Sanitized name of the worktree (used as label suffix)

  Returns:
  {:success true/false
   :label \"wt:panel-color\" (the worktree label)
   :error \"error message\" (if failed)}"
  [worktree-path worktree-name]
  (try
    (let [label (str "wt:" worktree-name)
          config-file (io/file worktree-path ".beads-worktree")
          ;; Resolve the actual git dir for worktrees
          git-dir (or (resolve-worktree-git-dir worktree-path)
                      (str worktree-path "/.git"))
          exclude-file (io/file git-dir "info" "exclude")]

      (log/info "Setting up beads worktree context"
                {:worktree-path worktree-path
                 :git-dir git-dir
                 :label label})

      ;; Write the worktree label file
      (spit config-file label)

      ;; Ensure info directory exists
      (.mkdirs (.getParentFile exclude-file))

      ;; Add to git exclude (local only, not committed)
      (let [exclude-content (when (.exists exclude-file)
                              (slurp exclude-file))
            needs-exclude? (or (nil? exclude-content)
                               (not (str/includes? exclude-content ".beads-worktree")))]
        (when needs-exclude?
          (spit exclude-file
                (str (or exclude-content "")
                     (when (and exclude-content
                                (not (str/ends-with? exclude-content "\n")))
                       "\n")
                     ".beads-worktree\n"))))

      {:success true
       :label label})

    (catch Exception e
      (log/error e "Exception during beads worktree setup")
      {:success false
       :error (format "Exception during beads worktree setup: %s" (ex-message e))})))
```

#### File: backend/src/voice_code/worktree.clj

Update `format-worktree-prompt` to include beads instructions:

```clojure
(defn format-worktree-prompt
  "Generate Claude prompt for worktree initialization"
  [session-name worktree-path parent-directory branch-name worktree-label]
  (format "You are working in a git worktree named '%s'.
This worktree was created at %s from the repository at %s.
The branch is '%s'.

## Beads Worktree Context

This worktree uses label-based beads isolation. Filter all beads commands by: `--label %s`

**Finding work:**
- `bd ready --label %s` - See tasks for this worktree
- `bd list --label %s` - List all issues for this worktree

**Creating issues:**
- `bd create --labels %s \"Task title\"` - New tasks get the worktree label

**Important:** Always include `--label %s` when using `bd ready` or `bd list` to see only issues relevant to this worktree.

Don't do anything yet."
          session-name worktree-path parent-directory branch-name
          worktree-label worktree-label worktree-label worktree-label worktree-label))
```

#### File: backend/src/voice_code/server.clj

Update the worktree session creation to use the new function and pass the label to the prompt.

In `handle-create-worktree-session` (around line 1534), replace the `init-beads!` call with `setup-beads-worktree!`:

```clojure
;; Current code (lines 1533-1546):
;; Step 4b: Initialize Beads
(let [bd-result (worktree/init-beads! worktree-path)]
  (if-not (:success bd-result)
    (send-to-client! channel ...)
    ;; Step 4c: Invoke Claude Code
    (let [prompt (worktree/format-worktree-prompt session-name worktree-path
                                                  parent-directory branch-name)]
      ...)))

;; Replace with:
;; Step 4b: Set up beads worktree context
(let [bd-result (worktree/setup-beads-worktree! worktree-path sanitized-name)]
  (if-not (:success bd-result)
    (send-to-client! channel
                     {:type :worktree-session-error
                      :success false
                      :error (:error bd-result)
                      :error-type :beads-failed
                      :details {:step "beads_worktree_setup"}})
    ;; Step 4c: Invoke Claude Code with label in prompt
    (let [worktree-label (:label bd-result)
          prompt (worktree/format-worktree-prompt session-name worktree-path
                                                  parent-directory branch-name
                                                  worktree-label)]
      (claude/invoke-claude-async
       prompt
       (fn [response]
         ...)
       :new-session-id session-id
       :model "haiku"
       :working-directory worktree-path))))
```

Note: `sanitized-name` is already available from the destructured `paths` map at line 1500.

### Component Interactions

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Worktree Session Creation Flow                       │
└─────────────────────────────────────────────────────────────────────────┘

iOS App                    Backend                      Filesystem
   │                          │                             │
   │ create-worktree-session  │                             │
   │ {name: "panel-color"}    │                             │
   │─────────────────────────>│                             │
   │                          │                             │
   │                          │ git worktree add            │
   │                          │────────────────────────────>│
   │                          │                             │
   │                          │ setup-beads-worktree!       │
   │                          │  - write .beads-worktree    │
   │                          │  - resolve worktree git dir │
   │                          │  - update info/exclude      │
   │                          │────────────────────────────>│
   │                          │                             │
   │                          │ format-worktree-prompt      │
   │                          │ (includes beads label)      │
   │                          │                             │
   │                          │ invoke Claude CLI           │
   │                          │────────────────────────────>│ Claude
   │                          │                             │
   │ worktree-session-created │                             │
   │<─────────────────────────│                             │
   │                          │                             │


┌─────────────────────────────────────────────────────────────────────────┐
│                    Agent Working in Worktree                            │
└─────────────────────────────────────────────────────────────────────────┘

Agent (Claude)              Beads CLI                  Shared Database
   │                          │                             │
   │ bd ready --label wt:panel-color                        │
   │─────────────────────────>│                             │
   │                          │ SELECT * WHERE labels       │
   │                          │ CONTAINS 'wt:panel-color'   │
   │                          │────────────────────────────>│
   │                          │                             │
   │ [filtered results]       │<────────────────────────────│
   │<─────────────────────────│                             │
   │                          │                             │
   │ bd create --labels wt:panel-color "New task"           │
   │─────────────────────────>│                             │
   │                          │ INSERT with label           │
   │                          │────────────────────────────>│
   │                          │                             │
```

## Verification Strategy

### Unit Tests

#### File: backend/test/voice_code/worktree_test.clj

Add the following tests to the existing test file:

```clojure
;; Add to existing requires:
;; [clojure.string :as str] (if not already present)

(deftest test-resolve-worktree-git-dir
  (testing "Returns nil for regular git directory"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir") "test-git-regular")]
      (.mkdirs temp-dir)
      (try
        ;; Create a regular .git directory (not a worktree)
        (.mkdirs (io/file temp-dir ".git"))
        (is (nil? (worktree/resolve-worktree-git-dir (.getAbsolutePath temp-dir))))
        (finally
          (doseq [f (reverse (file-seq temp-dir))]
            (.delete f))))))

  (testing "Resolves worktree git directory from .git file"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir") "test-worktree-resolve")]
      (.mkdirs temp-dir)
      (try
        ;; Create a .git file like a real worktree has
        (spit (io/file temp-dir ".git") "gitdir: /path/to/main/.git/worktrees/my-worktree\n")
        (is (= "/path/to/main/.git/worktrees/my-worktree"
               (worktree/resolve-worktree-git-dir (.getAbsolutePath temp-dir))))
        (finally
          (doseq [f (reverse (file-seq temp-dir))]
            (.delete f)))))))

(deftest test-setup-beads-worktree
  (testing "Creates .beads-worktree file with correct label"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "worktree-test-" (System/currentTimeMillis)))
          git-info-dir (io/file temp-dir ".git" "info")]
      (.mkdirs git-info-dir)
      (try
        (spit (io/file git-info-dir "exclude") "# existing excludes\n")

        (let [result (worktree/setup-beads-worktree! (.getAbsolutePath temp-dir) "panel-color")]
          ;; Verify success
          (is (:success result))
          (is (= "wt:panel-color" (:label result)))

          ;; Verify .beads-worktree file
          (is (.exists (io/file temp-dir ".beads-worktree")))
          (is (= "wt:panel-color" (slurp (io/file temp-dir ".beads-worktree"))))

          ;; Verify git exclude
          (let [exclude-content (slurp (io/file git-info-dir "exclude"))]
            (is (clojure.string/includes? exclude-content ".beads-worktree"))))

        (finally
          (doseq [f (reverse (file-seq temp-dir))]
            (.delete f))))))

  (testing "Handles missing .git/info/exclude file"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "worktree-test-" (System/currentTimeMillis)))
          git-info-dir (io/file temp-dir ".git" "info")]
      (.mkdirs git-info-dir)
      (try
        ;; No exclude file exists
        (let [result (worktree/setup-beads-worktree! (.getAbsolutePath temp-dir) "test-branch")]
          (is (:success result))
          (is (.exists (io/file git-info-dir "exclude")))
          (is (clojure.string/includes? (slurp (io/file git-info-dir "exclude"))
                                        ".beads-worktree")))
        (finally
          (doseq [f (reverse (file-seq temp-dir))]
            (.delete f)))))))

;; Update existing test to use 5-argument version:
(deftest test-format-worktree-prompt-with-label
  (testing "Format worktree prompt includes beads label instructions"
    (let [prompt (worktree/format-worktree-prompt
                  "Panel Color"
                  "/Users/travis/code/voice-code-panel-color"
                  "/Users/travis/code/voice-code"
                  "panel-color"
                  "wt:panel-color")]
      (is (clojure.string/includes? prompt "Panel Color"))
      (is (clojure.string/includes? prompt "wt:panel-color"))
      (is (clojure.string/includes? prompt "bd ready --label"))
      (is (clojure.string/includes? prompt "bd create --labels"))
      (is (clojure.string/includes? prompt "Don't do anything yet")))))
```

### Integration Tests

Manual test script:

```bash
#!/bin/bash
# Test worktree beads label isolation
# Run from: voice-code directory

set -e

REPO_DIR="$(pwd)"
TEST_BRANCH="test-label-isolation-$(date +%s)"
TEST_DIR="../voice-code-$TEST_BRANCH"

echo "=== Creating test worktree ==="
git worktree add -b "$TEST_BRANCH" "$TEST_DIR" HEAD

echo ""
echo "=== Verifying shared database (shows all issues) ==="
cd "$TEST_DIR"
ISSUE_COUNT=$(bd list --limit 100 -q 2>/dev/null | wc -l)
echo "Total issues visible: $ISSUE_COUNT"

echo ""
echo "=== Creating worktree label file ==="
echo "wt:$TEST_BRANCH" > .beads-worktree
cat .beads-worktree

echo ""
echo "=== Creating test issue with label ==="
ISSUE_ID=$(bd create --labels "wt:$TEST_BRANCH" "Test task for $TEST_BRANCH" --silent)
echo "Created: $ISSUE_ID"

echo ""
echo "=== Verifying filtered list ==="
echo "bd list --label wt:$TEST_BRANCH:"
bd list --label "wt:$TEST_BRANCH"

echo ""
echo "=== Verifying bd ready with label ==="
echo "bd ready --label wt:$TEST_BRANCH:"
bd ready --label "wt:$TEST_BRANCH" --limit 5

echo ""
echo "=== Cleanup: closing test issue ==="
bd close "$ISSUE_ID" --reason "test complete"

echo ""
echo "=== Removing worktree ==="
cd "$REPO_DIR"
git worktree remove "$TEST_DIR"
git branch -D "$TEST_BRANCH"

echo ""
echo "=== All tests passed ==="
```

### End-to-End Test

1. Start the backend server
2. Connect iOS app
3. Create a new session with "Create worktree" option, name: "Panel Color"
4. Verify:
   - Worktree directory created: `voice-code-panel-color/`
   - File exists: `voice-code-panel-color/.beads-worktree` containing `wt:panel-color`
   - File excluded: `voice-code/.git/worktrees/voice-code-panel-color/info/exclude` contains `.beads-worktree`
   - Claude prompt includes beads label instructions
   - Running `bd ready --label wt:panel-color` in worktree shows 0 issues (fresh start)
   - Creating issue with `bd create --labels wt:panel-color "Test"` works
   - Running `bd ready` (without filter) still shows all 25+ issues

### Acceptance Criteria

1. Worktree creation succeeds and creates `.beads-worktree` file with correct label
2. Claude initial prompt includes beads filtering instructions
3. `bd ready --label wt:<name>` shows only issues with that label (empty for new worktree)
4. `bd create --labels wt:<name>` creates issues with the worktree label
5. `bd list` (no filter) still shows all issues in the shared database
6. Issues created in worktree are committed to git and merge with branch
7. `.beads-worktree` file is not committed to git

## Alternatives Considered

### Alternative 1: True Database Isolation

Create a separate beads database per worktree.

**Rejected because:** Beads architecture intentionally shares the database across worktrees. The `BEADS_DIR` option places issues outside git, breaking merge capability.

### Alternative 2: External BEADS_DIR

Set `BEADS_DIR` environment variable to an external location.

**Rejected because:**
- Issues don't merge with git branches
- Complex environment setup required
- Defeats the purpose of git-integrated issue tracking

### Alternative 3: Skip Beads Entirely in Worktrees

Don't initialize or configure beads in worktrees.

**Rejected because:**
- Loses issue tracking capability in worktrees
- Forces manual beads setup
- Inconsistent experience between main repo and worktrees

### Alternative 4: Prefix in Issue Title

Use title prefixes like `[panel-color] Task name` instead of labels.

**Rejected because:**
- Less clean than labels
- Search/filter is more complex
- Labels are the standard beads way to categorize issues

## Risks & Mitigations

### Risk 1: Agent Forgets to Use Label Filter

Agents may run `bd ready` or `bd list` without the `--label` flag, seeing all issues.

**Likelihood:** Medium

**Mitigation:**
- Initial prompt explicitly instructs agents to use label filters
- Consider updating @backend/src/voice_code/recipes.clj to include label parameter
- `.beads-worktree` file serves as a reminder

### Risk 2: Label Collision

User creates a label that conflicts with `wt:` prefix.

**Likelihood:** Very low

**Mitigation:** The `wt:` prefix is unlikely to be used manually. Document the convention.

### Risk 3: Agent Creates Issue Without Label

Agent creates an issue but forgets `--labels wt:panel-color`.

**Likelihood:** Medium

**Mitigation:**
- Instructions emphasize always including the label
- Issues can be retroactively labeled with `bd label add <id> wt:panel-color`
- Consider a git hook that validates labels (future enhancement)

### Risk 4: Worktree Name Collision

Two worktrees with similar names get the same label.

**Likelihood:** Low (names are sanitized consistently)

**Mitigation:** Branch name uniqueness is already enforced by git.

## Implementation Checklist

- [ ] Update `setup-beads-worktree!` function in `worktree.clj`
- [ ] Remove old `init-beads!` function
- [ ] Update `format-worktree-prompt` to accept and include label
- [ ] Update `server.clj` to use new function and pass label
- [ ] Add unit tests for new functions
- [ ] Run manual integration test script
- [ ] Test end-to-end from iOS app
- [ ] Update recipes to support optional label parameter (future)
