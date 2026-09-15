# Why the tab behaves as it does

*The reasoning behind the Reflog tab, and the platform pieces it is built out of.*

To use the tab you need none of this: [usage.md](usage.md) states every rule without deriving it.\
This document is for when something surprises you, or when you are about to change it.

- [What is read, and when](#what-is-read-and-when) - the `git reflog show` format, the commit columns, the
  300 ms throttle.
- [The comparisons](#the-comparisons) - why there are four, why some are refused, and why a refusal is greyed
  rather than absent.
- [What costs a git call](#what-costs-a-git-call) - the 150 ms hold-back, the ancestry check, and what the panes
  cost when hidden.
- [Where the ref list comes from](#where-the-ref-list-comes-from) - why the reflog files rather than the refs.
- [Why the filters look like the Log's](#why-the-filters-look-like-the-logs) - `FilterComponent`, and the
  separator it does not write.
- [Why Load More is a link](#why-load-more-is-a-link) - and not an action on the toolbar.
- [How the tab is wired in](#how-the-tab-is-wired-in) - the extension point, the data context, and the platform
  components the panes are.

## What is read, and when

Entries are read with `git reflog show` in a machine-readable format: one record per output line, fields
separated by `0x01`.

- The timestamp has to come from the `%gd` placeholder - see [GitReflogReader.kt][file:GitReflogReader.kt].
- The `HEAD@{n}` index comes from the record's position in the output - see
  [GitReflogParser.kt][file:GitReflogParser.kt].

The parser is a separate object so that the format can be covered by tests without a repository.

### The commit columns are what the Log cannot be asked for

A `checkout: moving from master to feature` entry says nothing about what it moved to, and the commit is often
one no branch reaches any more, so the Log cannot answer it either.

They cost nothing extra to read: `git reflog show` is `git log --walk-reflogs`, so the commit's own placeholders
are available in the same format string.

### Re-reads are throttled to one every 300 ms

An interactive rebase publishes the change the tab listens to once per step, while each read costs a
`rev-parse`, a walk of the log directory and a `git reflog show`.

The first change of a burst schedules the read and the rest fold into it, so a long-running operation still
refreshes while it runs rather than only at its end.

## The comparisons

A reflog is a timeline of one ref, which means "the state before" and "the state after" exist even where the
commits are on branches that never met. More than one comparison is therefore worth making, which is what
**Compare** picks between.

### Why two comparisons are refused on a stash

A stash reflog is a stack of unrelated entries rather than a timeline of one state, so the two readings that ask
what a movement did have nothing to say about it.

### Why Selected Commits needs an ancestry check on a timeline

Merging the changes of commits that sit on branches which never met produces a tree of changes that undo one
another. So for several entries on a timeline, the comparison is offered only once git has confirmed each is an
ancestor of the next.

### The ancestry check is asked of timelines only

Stash entries are never ancestors of one another - that is true of every pair of them there has ever been, so it
says nothing about any particular pair.

What they hold are independent sets of work over a common base, all of them forward changes. Merging several of
them is a union of what they hold rather than a pile of contradictions, so **Selected Commits** fits a stash
selection of any size.

### A comparison that does not fit is greyed rather than withheld

A set of comparisons that changed with the selection could not be learnt.

A comparison that is merely absent leaves nothing to explain itself, where a greyed one says both that it exists
and that this selection is not for it.

### Caveat: an empty comparison between one commit and itself is answered in words

Where the movement left the ref where it found it, naming a range would print one and the same hash on both
sides, which reads as a fault in the tab rather than as the answer it is. Hence `Nothing moved: HEAD stood at
8dede56f both before and after` instead.

## What costs a git call

### Reading the files is held back 150 ms

Walking the table with the arrow keys would otherwise start a git call per row passed over.

A selection that comes back to the same entries under the same comparison - which is what every re-read does, by
restoring the selection it had - is left alone rather than read again: what a commit changed cannot change.

### Against Working Tree is the exception

It is the one reading whose answer can change without the reflog changing at all.

It is read again every time the pane is asked to, and the tab listens to the change lists on top of the
repository, so that an edit which is merely saved - touching no git state for a repository listener to hear
about - still reaches it.

### The ancestry check costs a call per neighbouring pair

`git merge-base --is-ancestor` is asked only where the answer can change which comparison is shown: of a
selection of several, on a timeline, and never on the way past a row.

### With both panes away, nothing is read

There is nobody for the read to answer, and the selection still moves.

With no diff pane the viewer is not merely hidden but disposed, since it would otherwise keep loading file
contents behind every move of the selection.

The files pane stays either way: it is what says which files an entry touched, and it costs the same one
`git show` regardless.

## Where the ref list comes from

The list is read from the reflog files themselves rather than derived from the refs, because neither of the
alternatives is dependable:

- `git reflog list` would answer it outright, but it only arrived in git 2.45;
- the refs disagree with the reflogs in both directions - a fresh clone leaves `refs/remotes/origin/HEAD` with a
  reflog and `refs/remotes/origin/master` without one, and `refs/stash` shows up only once something is stashed.

### Why a deleted ref falls back rather than erroring

A ref that exists but was never logged is not an error for git - it answers with an empty reflog - while a ref
that is gone is.

So every read resolves the ref against the refs that currently have a reflog, and falls back to `HEAD` rather
than surface that error.

## Why the filters look like the Log's

All three are `FilterComponent` subclasses added to the toolbar row directly, `FilterComponent` being what the
Log's Branch, User and Date filters are built on. So they look the same in both tabs.

The Log's own wrapper for putting one in a toolbar, `VcsLogPopupComponentAction`, is marked internal, so the
plugin opens the popup itself - as an action group updated off the EDT, with speed search, which is what the Log
does too.

### Why the tab writes the `": "` separator itself

`FilterComponent` writes the separator between name and value only for a filter it considers set, and none of
these three has an unset state.

Supplying it in the tab keeps the name and the value two labels, each in the colour the platform gives it.

## Why Load More is a link

The link is drawn beside the count of what has been read, rather than contributed to the toolbar as an action.

The two say one thing between them - how much is on screen, and that there is more - and a click moves the
number it stands beside, which is the answer to whether the click did anything.

An action would also have had to wait for a toolbar to ask it what to show, where a toolbar has no way of
knowing that git has answered.

## How the tab is wired in

- The tab is contributed through the `com.intellij.changesViewContent` extension point, which is how the
  persistent tabs of the Git and Commit tool windows are registered, and is shown for projects that have Git as
  an active VCS.
- Every action acts on what the tab publishes into the data context: the selected entries, the whole selection
  against its reflog and which comparisons fit it, and the revision numbers for Select in Git Log and Copy
  Revision Number, which are platform actions the plugin only references.
- Publishing a snapshot rather than letting an action read the panel is what lets every action update off the
  EDT: working out which comparisons fit reads the table, and only the EDT may do that.
- The files pane's toolbar and menu add the platform's `Vcs.RepositoryChangesBrowserToolbar` and
  `Vcs.RepositoryChangesBrowserMenu` groups to what `ChangesBrowserBase` already brings. Where the Log keeps the
  placement of its diff pane in a View Options popup on that same toolbar, the tab keeps it as two buttons on
  its own toolbar instead, next to Refresh.
- The files and diff panes are the platform's `SimpleAsyncChangesBrowser` and the viewer that
  `TreeHandlerEditorDiffPreview.createDefaultViewer` builds on top of its tree.
- The Log's own equivalents, `VcsLogChangesBrowser` and its `FrameDiffPreview`, are internal to the Log, but
  they are built out of exactly these two pieces - which is why the panes behave the same, including the
  Combined Diff viewer when it is enabled.
- The diff viewer is registered under a place of the plugin's own, so the settings its toolbar writes belong to
  this tab rather than following the Log.
- The reset dialog is the plugin's own because git4idea's is built around a `VcsFullCommitDetails` loaded from
  the Log, which is exactly what an unreachable commit does not have. The mode names and descriptions still
  come from git4idea, so the choice reads the same as in the Log.


[file:GitReflogParser.kt]: ../src/main/kotlin/cz/loplex/reflog/GitReflogParser.kt
[file:GitReflogReader.kt]: ../src/main/kotlin/cz/loplex/reflog/GitReflogReader.kt
