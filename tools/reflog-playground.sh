#!/usr/bin/env bash
# Builds a throwaway repository whose reflog holds every state the Reflog tab has a case for.
# Usage: reflog-playground.sh [directory]
set -euo pipefail

DIR="${1:-$PWD/reflog-playground}"
rm -rf "$DIR"
mkdir -p "$DIR"
cd "$DIR"

git init --quiet
git config user.name "Martin Lopatar"
git config user.email "martin@example.com"
git config commit.gpgsign false

commit() { # commit <file> <content> <subject> [author]
  echo "$2" > "$1"
  git add "$1"
  if [ -n "${4:-}" ]; then
    GIT_AUTHOR_NAME="$4" GIT_AUTHOR_EMAIL="${4// /.}@example.com" git commit -q -m "$3"
  else
    git commit -q -m "$3"
  fi
}

# --- oldest: the entries Load More has to reach ---------------------------------------------------
commit parser.txt "v1" "Add the reflog parser"
commit reader.txt "v1" "Read the reflog from the log files rather than from git reflog list" "Dana Novakova"
commit panel.txt "v1" "Add the tab panel"
git commit -q --amend -m "Add the tab panel, with its toolbar"          # commit (amend)
commit filter.txt "v1" "Add the action filter" "Petr Svoboda"

# a branch that never meets master again - the case Union must refuse
git checkout -q -b abandoned
commit abandoned.txt "v1" "Work on the abandoned branch"
commit abandoned.txt "v2" "More work that never landed"
git checkout -q master

commit columns.txt "v1" "Add the Commit Message and Author columns"
git reset -q --hard HEAD~1                                             # reset
commit columns.txt "v2" "Add the Commit Message and Author columns, narrower"

# a rebase, for the rebase (start)/(finish) entries
git checkout -q -b rebased HEAD~2
commit rebased.txt "v1" "A commit to rebase"
git rebase -q master || git rebase --abort
git checkout -q master

# --- the bulk, to push the interesting entries past the first page -------------------------------
# Not --allow-empty: a reflog of empty commits makes every comparison answer "nothing changed", which reads as
# a broken tab rather than as an honest answer.
echo "Writing 1010 entries to get past the 1000-entry page..."
for i in $(seq 1 1010); do
  echo "line $i" >> filler.txt
  git add filler.txt
  git commit -q -m "Filler commit $i"
done

# --- newest: what the first page shows ------------------------------------------------------------
commit diff.txt "v1" "Add the four comparisons to the file pane"
commit diff.txt "v2" "Let the file pane be put away" "Dana Novakova"
git checkout -q -b detached-demo
git checkout -q master                                                 # two checkout entries

# stash entries: a stack of unrelated ones, which the timeline readings must refuse
echo "wip one" > stash-a.txt && git add stash-a.txt && git stash -q
echo "wip two" > stash-b.txt && git add stash-b.txt && git stash -q

# something uncommitted, so Against Working Tree has an answer
echo "edited but not committed" >> diff.txt

echo
echo "Ready: $DIR"
echo "Reflog entries: $(git reflog | wc -l)   stashes: $(git stash list | wc -l)"
