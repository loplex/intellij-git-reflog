<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Reflog Changelog

## [Unreleased]

### Added

- Reflog tab in the Git tool window, listing the reflog of the selected repository.
- Ref selector: any ref the repository has a reflog for - `HEAD`, branches, remote-tracking branches, the stash.
- Commit Message and Author columns, read from the same `git reflog show` call as the rest.
- Filtering by text and by kind of operation.
- Show Diff, Select in Git Log and Copy Revision Number on the selected entry.
- Checkout Revision, New Branch from Here and Reset Current Branch to Here on the selected entry.
