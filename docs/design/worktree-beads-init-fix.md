# Worktree Beads Initialization Fix

## Overview

### Problem Statement

Creating a new session with a git worktree fails at the beads initialization step. The backend correctly creates the git worktree but then `bd init -q` returns exit code 1, causing the entire worktree session creation to fail. The worktree directory exists with a `.beads` folder (from git checkout), but the Claude prompt never runs.

### Goals

1. Fix worktree session creation to successfully initialize beads and invoke Claude
2. Handle the case where `.beads` base files are already present from git checkout
3. Maintain backward compatibility with repos that don't track `.beads` files

### Non-goals

- Changing whether `.beads` files should be tracked in git (that's a beads project decision)
- Modifying the beads CLI behavior
- Changing the worktree creation flow beyond the beads initialization step

## Background & Context

### Current State

The worktree session creation flow in @backend/src/voice_code/server.clj:1485-1564:

1. **Validate inputs** - Check session name and parent directory
2. **Compute paths** - Generate branch name, worktree path
3. **Validate paths** - Check worktree doesn't exist, branch doesn't exist
4. **Create git worktree** - `git worktree add -b <branch> <path> HEAD`
5. **Initialize beads** - `bd init -q` in worktree directory ← **FAILS HERE**
6. **Invoke Claude** - Send initial prompt to Claude Code

The `init-beads!` function in @backend/src/voice_code/worktree.clj:84-107:

```clojure
(defn init-beads!
  [worktree-path]
  (try
    (log/info "Initializing Beads in worktree" {:worktree-path worktree-path})
    (let [result (shell/sh "bd" "init" "-q" :dir worktree-path)]
      (if (zero? (:exit result))
        {:success true}
        {:success false
         :error (format "Beads initialization failed: %s" (:err result))
         :stderr (:err result)}))
    (catch Exception e
      (log/error e "Exception during Beads initialization")
      {:success false
       :error (format "Exception during Beads initialization: %s" (ex-message e))})))
```

### Why Now

The voice-code repository tracks `.beads` base files in git:
- `.beads/.gitignore`
- `.beads/README.md`
- `.beads/config.yaml`
- `.beads/interactions.jsonl`
- `.beads/issues.jsonl`
- `.beads/metadata.json`

When a git worktree is created, these files are checked out into the new worktree. When `bd init -q` runs, it detects an existing database (via some path resolution that finds the main repo's `beads.db`) and returns exit code 1 with the message:

```
⚠ Found existing database: /path/to/main-repo/.beads/beads.db
This workspace is already initialized.
Aborting.
```

This is a regression introduced when `.beads` files started being tracked in the voice-code repository.

### Related Work

- @STANDARDS.md - WebSocket protocol and message types
- @backend/src/voice_code/worktree.clj - Worktree creation functions

## Detailed Design

### Root Cause Analysis

The `bd init` command:
1. Checks if a `.beads` directory exists
2. If it exists, checks for a database file
3. The database path resolution follows git worktree links back to the main repository
4. Finding the main repo's `beads.db`, it concludes the workspace is "already initialized"
5. Returns exit code 1

The solution is to use `bd init --force` which:
- Reinitializes even when an existing database is detected
- Creates a new, independent database for the worktree
- Returns exit code 0 on success

### Code Changes

#### Option A: Use `--force` flag (Recommended)

Change the `bd init -q` command to `bd init -q --force`:

**File: backend/src/voice_code/worktree.clj**

```clojure
(defn init-beads!
  "Initialize Beads in the worktree directory.

  Uses --force flag to handle cases where .beads base files already exist
  from git checkout (when the parent repo tracks .beads skeleton files).

  Parameters:
  - worktree-path: Path to the worktree directory

  Returns:
  {:success true/false
   :error \"error message\" (if failed)
   :stderr \"bd stderr\" (if failed)}"
  [worktree-path]
  (try
    (log/info "Initializing Beads in worktree" {:worktree-path worktree-path})
    (let [result (shell/sh "bd" "init" "-q" "--force" :dir worktree-path)]
      (if (zero? (:exit result))
        {:success true}
        {:success false
         :error (format "Beads initialization failed: %s" (:err result))
         :stderr (:err result)}))
    (catch Exception e
      (log/error e "Exception during Beads initialization")
      {:success false
       :error (format "Exception during Beads initialization: %s" (ex-message e))})))
```

**Pros:**
- Single-line fix
- `--force` is designed for exactly this scenario
- Works for both tracked and untracked `.beads` repos

**Cons:**
- If a worktree somehow had a legitimate existing database with data, `--force` would overwrite it (unlikely in practice since worktrees are new directories)

#### Option B: Check for existing beads directory first

Check if `.beads` directory exists (from git checkout) and use `--force` only when needed:

```clojure
(defn beads-dir-exists?
  "Check if a .beads directory exists (e.g., from git checkout of tracked files)."
  [path]
  (.exists (io/file path ".beads")))

(defn init-beads!
  [worktree-path]
  (try
    (log/info "Initializing Beads in worktree" {:worktree-path worktree-path})
    ;; Use --force when .beads dir exists (from git checkout) but needs reinitialization
    (let [use-force? (beads-dir-exists? worktree-path)
          args (cond-> ["bd" "init" "-q"]
                 use-force? (conj "--force"))
          result (apply shell/sh (concat args [:dir worktree-path]))]
      (if (zero? (:exit result))
        {:success true}
        {:success false
         :error (format "Beads initialization failed: %s" (:err result))
         :stderr (:err result)}))
    (catch Exception e
      (log/error e "Exception during Beads initialization")
      {:success false
       :error (format "Exception during Beads initialization: %s" (ex-message e))})))
```

**Pros:**
- More conservative, only uses `--force` when `.beads` directory already exists

**Cons:**
- More complex
- Adds an extra filesystem check that `--force` handles implicitly
- No practical benefit since `--force` is safe for fresh directories

### Recommendation

**Use Option A** (`bd init -q --force`). The `--force` flag is designed for this exact scenario. Since worktrees are always fresh directories, there's no risk of overwriting an existing legitimate database.

### Error Handling

The existing error handling is adequate. The function already:
- Returns success/failure status
- Captures stderr for debugging
- Handles exceptions

No changes needed to error handling.

## Verification Strategy

### Unit Tests

No unit tests needed for this change - the function shells out to an external command.

### Integration Tests

Create a manual test script. Run from the voice-code directory:

```bash
#!/bin/bash
# Test worktree session creation with tracked .beads files
# Run from: ~/code/mono/active/voice-code (or wherever voice-code is checked out)

set -e

REPO_DIR="$(pwd)"
if [[ ! -d ".beads" ]]; then
    echo "Error: Run this script from a directory with tracked .beads files"
    exit 1
fi

# 1. Create a test worktree
TEST_BRANCH="test-worktree-beads-$(date +%s)"
TEST_DIR="../voice-code-$TEST_BRANCH"

echo "Creating worktree..."
git worktree add -b "$TEST_BRANCH" "$TEST_DIR" HEAD

# 2. Verify .beads files exist (from git checkout)
echo "Checking .beads files from git checkout..."
ls -la "$TEST_DIR/.beads/"

# 3. Verify the bug: bd init WITHOUT --force should fail
echo "Verifying bug exists (bd init without --force should fail)..."
cd "$TEST_DIR"
if bd init -q 2>/dev/null; then
    echo "UNEXPECTED: bd init succeeded without --force"
    cd "$REPO_DIR"
    git worktree remove "$TEST_DIR"
    git branch -D "$TEST_BRANCH"
    exit 1
else
    echo "Confirmed: bd init fails without --force (exit code $?)"
fi

# 4. Verify the fix: bd init WITH --force should succeed
echo "Verifying fix (bd init --force should succeed)..."
bd init -q --force
if [[ ! -f ".beads/beads.db" ]]; then
    echo "FAILED: beads.db was not created"
    cd "$REPO_DIR"
    git worktree remove "$TEST_DIR"
    git branch -D "$TEST_BRANCH"
    exit 1
fi
echo "Success: beads.db created"

# 5. Cleanup
cd "$REPO_DIR"
git worktree remove "$TEST_DIR"
git branch -D "$TEST_BRANCH"

echo ""
echo "=== All tests passed ==="
```

### End-to-End Test

1. Start the backend server
2. Connect iOS app
3. Create a new session with "Create worktree" option
4. Verify:
   - Worktree directory is created
   - `.beads/beads.db` exists in worktree
   - Claude prompt executes successfully
   - `worktree-session-created` message is received (not `worktree-session-error`)

### Acceptance Criteria

1. Creating a worktree session succeeds when parent repo tracks `.beads` files
2. Creating a worktree session succeeds when parent repo does NOT track `.beads` files
3. The worktree gets its own independent beads database
4. Claude Code receives the initial worktree prompt
5. The iOS app receives a `worktree-session-created` response

## Alternatives Considered

### Alternative 1: Skip beads init if `.beads` directory exists

```clojure
(defn init-beads!
  [worktree-path]
  (if (.exists (io/file worktree-path ".beads"))
    {:success true}  ; Skip init, assume already set up
    (shell/sh "bd" "init" "-q" :dir worktree-path)))
```

**Rejected because:** The `.beads` directory existing (from git checkout) doesn't mean beads is properly initialized. The database file is missing, and beads won't work without running `bd init`.

### Alternative 2: Remove `.beads` directory before init

```clojure
(defn init-beads!
  [worktree-path]
  (let [beads-dir (io/file worktree-path ".beads")]
    (when (.exists beads-dir)
      (shell/sh "rm" "-rf" (str beads-dir)))
    (shell/sh "bd" "init" "-q" :dir worktree-path)))
```

**Rejected because:**
- Deletes tracked files, causing git to show them as deleted
- Loses any configuration in `config.yaml`
- `bd init --force` handles this more elegantly

### Alternative 3: Use `bd sync` or `bd doctor --fix` instead

**Rejected because:** These commands don't create the database; they expect it to already exist.

## Risks & Mitigations

### Risk 1: `--force` overwrites existing data

**Likelihood:** Very low. Worktrees are created in new directories that don't have existing beads data.

**Mitigation:** The worktree creation flow validates that the directory doesn't exist before creating it (line 1503 in server.clj).

### Risk 2: Future beads versions change `--force` behavior

**Likelihood:** Low. The `--force` flag is a stable API.

**Mitigation:** Pin beads CLI version or test during upgrades.

### Risk 3: Different behavior for repos without tracked `.beads`

**Likelihood:** None. `bd init -q --force` works identically whether or not `.beads` exists.

**Mitigation:** Tested in acceptance criteria #2.

## Implementation Checklist

- [ ] Update `init-beads!` function in `worktree.clj` to use `--force` flag
- [ ] Update docstring to explain why `--force` is needed
- [ ] Manually test worktree creation from iOS app
- [ ] Verify end-to-end flow completes successfully
