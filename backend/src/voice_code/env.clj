(ns voice-code.env
  "Environment variable helpers for process execution based on working directory.
   
   Provides automatic detection of git worktrees and transparent setup of BEADS_DB
   environment variable to isolate beads databases per worktree."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defonce worktree-cache
  ;; Cache of directory path -> {:worktree? bool :name string-or-nil}.
  ;; Populated lazily by detect-worktree.
  (atom {}))

(defn detect-worktree
  "Detect if directory is a git worktree.
   Returns {:worktree? bool :name string-or-nil}.
   Results are memoized in worktree-cache."
  [dir]
  (if-let [cached (get @worktree-cache dir)]
    cached
    (let [result (shell/sh "git" "rev-parse" "--git-dir" :dir dir)
          git-dir (str/trim (:out result))
          ;; Worktrees have git-dir like: /path/to/.git/worktrees/<name>
          worktree? (and (zero? (:exit result))
                         (str/includes? git-dir "/worktrees/"))
          name (when worktree? (last (str/split git-dir #"/")))]
      (let [info {:worktree? worktree? :name name}]
        (swap! worktree-cache assoc dir info)
        (log/debug "Detected worktree status" {:dir dir :info info})
        info))))

(defn ensure-beads-local!
  "Ensure .beads-local/ database exists for a worktree.
   Creates and initializes if missing.
   Returns {:success true :created true} if newly created,
           {:success true :existed true} if already exists,
           {:success false :error string} on failure."
  [worktree-path worktree-name]
  (let [beads-dir (io/file worktree-path ".beads-local")
        db-path (str (.getPath beads-dir) "/local.db")]
    (if (.exists (io/file db-path))
      {:success true :existed true}
      (do
        (.mkdirs beads-dir)
        ;; Note: shell/sh :env REPLACES the environment, so we must merge
        ;; with System/getenv to preserve PATH, HOME, etc.
        (let [env-with-beads-db (merge (into {} (System/getenv)) {"BEADS_DB" db-path})
              result (shell/sh "bd" "init"
                               "--prefix" worktree-name
                               "--skip-hooks"
                               "--skip-merge-driver"
                               "--force"
                               :dir worktree-path
                               :env env-with-beads-db)]
          (if (zero? (:exit result))
            (do
              (log/info "Created beads local database" {:path db-path :worktree worktree-name})
              {:success true :created true})
            {:success false
             :error (str "bd init failed: " (:err result))}))))))

(defn env-for-directory
  "Return environment variables map for executing commands in directory.
   Sets BEADS_DB for worktrees with existing local database.
   Returns empty map for non-worktrees or worktrees without db."
  [dir]
  (let [{:keys [worktree?]} (detect-worktree dir)]
    (if worktree?
      (let [db-path (str dir "/.beads-local/local.db")]
        (if (.exists (io/file db-path))
          {"BEADS_DB" db-path}
          ;; Database doesn't exist yet - will be created on first use
          {}))
      {})))

(defn clear-cache!
  "Clear the worktree detection cache. Primarily for testing."
  []
  (reset! worktree-cache {}))
