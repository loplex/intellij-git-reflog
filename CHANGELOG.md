<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Reflog Changelog

## [Unreleased]

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
- The file pane's own toolbar and menu, as in the Log: Show Diff, Group By, Revert, Show History for Revision,
  and the platform's repository menu on right click.
- Show Diff, Select in Git Log and Copy Revision Number on the selected entry.
- Checkout Revision, New Branch from Here and Reset Current Branch to Here on the selected entry.
