<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Reflog Changelog

## [Unreleased]

## [0.1.0] - 2026-09-15

### Added

- Reflog tab in the Git tool window, listing the reflog of the selected repository.
- Ref selector: any ref the repository has a reflog for - `HEAD`, branches, remote-tracking branches, the stash.
- Commit Message and Author columns, read from the same `git reflog show` call as the rest.
- Filtering by text and by kind of operation, drawn with the platform's `FilterComponent` so that the toolbar
  matches the Log tab's.
- Load More, which reads another page of older records once a read comes back full.
- File and diff panes beside the table, as in the Log: the files the selected entry's commit changed, and
  the diff of the file selected among them.
- Preview Diff on the Right and Preview Diff at the Bottom on the toolbar, the platform's pair of preview
  buttons: they place the diff pane, and hide it when neither is pressed.
- Show Changed Files on the toolbar, which puts the file pane away and brings it back, so that either pane can
  be had without the other and the table can be had without both.
- The file pane's own toolbar and menu, as in the Log: Show Diff, Group By, Revert, Show History for Revision,
  and the platform's repository menu on right click.
- Compare on the file pane, choosing what the files of the selected entries are compared against: the reflog
  step they make, the two selected states, each selected commit against its own parent, or the working tree.
  Several entries can now be selected, which the file pane used to have no answer for.
- Show Diff, Select in Git Log and Copy Revision Number on the selected entry.
- Checkout Revision, New Branch from Here and Reset Current Branch to Here on the selected entry.

[Unreleased]: https://github.com/loplex/intellij-git-reflog/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/loplex/intellij-git-reflog/commits/v0.1.0
