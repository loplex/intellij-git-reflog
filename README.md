# Git Reflog

*An IntelliJ IDEA plugin that puts `git reflog` in the Git tool window.*

`git reflog` is the record of every movement of `HEAD` - commits, checkouts, resets, rebases, amends.\
It is also the only place where a commit that no branch points at any more can still be found.\
IntelliJ IDEA has no UI for it, so recovering work after a bad reset means leaving the IDE for a terminal.

This plugin adds a **Reflog** tab to the Git tool window, between the Log and the Console tab.

## Installing it

The plugin is not on the JetBrains Marketplace yet, so it is installed from a build of its own:

```bash
./gradlew buildPlugin
```

That leaves a zip under `build/distributions/`, which **Settings | Plugins | ⚙ | Install Plugin from Disk...**
takes. Building it, running it from sources and finding your way around it are in
[CONTRIBUTING.md](CONTRIBUTING.md).

It is built against IntelliJ IDEA 2025.3.5 and needs Git to be an active VCS in the project. It declares no
upper bound on the IDE version, so a later IDE is allowed to run it rather than refused; 2025.3 is the earliest
it will install into.

Licensed under the [Apache License 2.0](LICENSE).

## What is where

- [The Reflog tab](#the-reflog-tab) - the columns, the toolbar, and when the tab re-reads itself.
- [The files of the selected entry, and their diff](#the-files-of-the-selected-entry-and-their-diff) - the two
  panes, where they sit and how to put either away.
- [What the files are compared against](#what-the-files-are-compared-against) - **Compare**, its four readings,
  and when each is withheld.
- [Which refs can be shown](#which-refs-can-be-shown) - the ref selector.
- [Filtering](#filtering) - the filter field and the action filter.
- [Actions on the selected entry](#actions-on-the-selected-entry) - Show Diff, Checkout Revision, New Branch
  from Here, Reset Current Branch to Here.
- [Reading past the first page](#reading-past-the-first-page) - **Load More** and what the count says.
- [How the tab is wired in](#how-the-tab-is-wired-in) - which platform pieces it is built out of. For a reader
  of the sources; skip it to use the tab.

## The Reflog tab

The tab lists the reflog of one ref of one repository, newest entry first:

| Column      | Content                                                                                  |
|-------------|------------------------------------------------------------------------------------------|
| Selector    | `HEAD@{0}`, `HEAD@{1}`, ... - what `git reset`, `git checkout` and `git show` accept       |
| Date        | when the entry was written, which for a checkout is unrelated to the commit's own date    |
| Action      | the operation that moved `HEAD`: `commit`, `checkout`, `reset`, `rebase (finish)`, ...    |
| Description | the rest of the reflog message, for example `moving from master to feature`               |
| Commit      | short hash of the commit `HEAD` pointed at afterwards                                     |
| Commit Message | first line of that commit's own message                                               |
| Author      | author of that commit                                                                    |

The last three columns describe the commit rather than the movement: a `checkout: moving from master to feature`
entry says nothing about what it moved to, and the commit is often one no branch reaches any more, so the Log
cannot be asked either. They cost nothing extra to read - `git reflog show` is `git log --walk-reflogs`, so the
commit's own placeholders are available in the same format string.

Stash entries are the exception to the Action/Description split: `git stash` writes subjects such as
`WIP on master: eddeef8 first`, where the colon separates the branch from the commit rather than an action from
its details. The whole subject is kept as the description there and the Action column stays empty.

The toolbar row holds the filter field first, then the ref selector, the action filter and - in projects with
more than one Git repository - a repository selector. At the other end of the row stand the count of what has
been read, a **Load More** link where there is more to read, and then the buttons that place the two panes and
Refresh.

The tab re-reads the reflog on its own whenever the state of the repository changes, which covers every operation
that writes a reflog record. The selected entry survives such a re-read: it is recognised by what it records
rather than by its selector, since every new record pushes `HEAD@{0}` down to `HEAD@{1}` and selectors therefore
name a different entry after every commit.

Re-reads are throttled to one every 300 ms, because an interactive rebase publishes the change it listens to once
per step while each read costs a `rev-parse`, a walk of the log directory and a `git reflog show`. The first
change of a burst schedules the read and the rest fold into it, so a long-running operation still refreshes while
it runs rather than only at its end.

### The files of the selected entry, and their diff

Below and beside the table sit the same two panes the Log has: the files the selected entry's commit changed,
and the diff of the file selected among them. The files pane sits next to the table, the diff wraps both - the
arrangement the Log uses, for the same reason: a diff is the wider of the two, so it goes around the table
rather than beside it.

The files are read from git directly, so they are there for a commit no ref reaches any more - the same reason
Show Diff works where Select in Git Log does not.

#### What the files are compared against

A reflog is a timeline of one ref, which means "the state before" and "the state after" exist even where the
commits are on branches that never met. More than one comparison is therefore worth making, and **Compare** on
the file pane's toolbar picks between them:

| Comparison | What it shows | Selection it needs |
| --- | --- | --- |
| **Reflog Step** | What the selected movements did to the working tree, from the state before the oldest to the state after the newest | Any, as long as the reflog still holds the entry before the oldest selected one |
| **Between Selected** | How the newest selected state differs from the oldest one, leaving out the movement that produced the oldest | Two or more entries |
| **Selected Commits** | What each selected commit changed against its own parent, merged into one tree, as the Log answers a multiple selection | Any on a stash; on a timeline, more than one entry only where they sit on one line of history |
| **Against Working Tree** | How the working tree differs from the selected state | A single entry |

Reflog Step is where the tab starts, and the one that sets the reflog apart from the Log: for a `commit` entry
it is the commit's own diff, but for a `checkout` or a `reset` it is "what changed under me", which no diff
against a parent can show.

Comparisons are withheld where they have nothing to say. A stash reflog is a stack of unrelated entries rather
than a timeline of one state, so the two readings that ask what a movement did are not offered for it. And on a
timeline, merging the changes of commits that sit on branches which never met produces a tree of changes that
undo one another, so **Selected Commits** is offered for several entries only once git has confirmed each is an
ancestor of the next.

That last check is asked of timelines only. Stash entries are never ancestors of one another - that is true of
every pair of them there has ever been, so it says nothing about any particular pair - and what they hold are
independent sets of work over a common base, all of them forward changes. Merging several of them is a union of
what they hold rather than a pile of contradictions, so **Selected Commits** fits a stash selection of any size.
Nothing to compare therefore means nothing selected.

A comparison that comes back empty says which one it made and of what: `Nothing changed between 3f0a91c2 and
8dede56f`, or `The working tree matches 8dede56f`. Where the movement left the ref where it found it - a checkout
between two branches standing on the same commit, or the reset `git stash` makes internally once it has put the
work away - the answer is given in words instead: `Nothing moved: HEAD stood at 8dede56f both before and after`.
Naming a range there would print one and the same hash on both sides, which reads as a fault in the tab rather
than as the answer it is.

The choice is remembered outside the project, so the tab opens the way it was last left. A choice that has no
answer for the selection of the moment gives way to the nearest one that does, rather than emptying the pane -
and comes back as soon as a selection it suits is made again. What the toolbar and the ticks in the menu show is
always the comparison on screen, so a selection being answered by a different one is never silent. Both the
toolbar and the context menus - the table's as well as the file pane's, the entries being selected in the table -
list every comparison and grey out the ones the selection has no answer for: a set
that changed with the selection could not be learnt, and a comparison that is merely absent leaves nothing to
explain itself, where a greyed one says that it exists and that this selection is not for it.

Reading the files is held back 150 ms after the selection moves, so walking the table with the arrow keys does
not start a git call per row passed over. A selection that comes back to the same entries under the same
comparison - which is what every re-read does, by restoring the selection it had - is left alone rather than
read again: what a commit changed cannot change.

**Against Working Tree** is the exception, being the one reading whose answer can change without the reflog
changing at all. It is read again every time the pane is asked to, and the tab listens to the change lists on top
of the repository, so that an edit which is merely saved - touching no git state for a repository listener to
hear about - still reaches it.

Whether the selected entries sit on one line of history takes a `git merge-base --is-ancestor` per neighbouring
pair to answer, so it is asked only where the answer can change which comparison is shown: of a selection of
several, on a timeline, and never on the way past a row. On a stash it is not asked at all - the answer there is
the same for every pair of entries, and no reading turns on it.

**Show Changed Files** on the toolbar puts the file pane away and brings it back. It is a button of its own
rather than a third state of the diff buttons below, because which files an entry touched and what it did to one
of them are separate questions: either answer is worth having without the other, and the table on its own, with
neither pane, is worth having too. With both away nothing is read at all - there is nobody for the read to
answer, and the selection still moves.

The diff pane is worth having without the file pane in particular, which is less obvious than it sounds. With
nothing selected among the files the platform hands the diff every change of the entry rather than none, so the
pane becomes the entry's whole diff at the width of the tab, paged through with the diff viewer's own navigation
instead of by clicking a list. What is given up is jumping straight to a named file.

The diff pane is placed from the toolbar, by the platform's pair of preview buttons - **Preview Diff on the
Right** and **Preview Diff at the Bottom**. Between them they cover all three states in a single click: neither
pressed means no diff pane, either one pressed shows it on that side, and pressing the one already down hides it
again. It starts at the bottom, where the Log starts its own.

With no diff pane the viewer is not merely hidden but disposed, since it would otherwise keep loading file
contents behind every move of the selection. The file pane stays either way: it is what says which files an entry
touched, and it costs the same one `git show` regardless.

The file pane carries the toolbar the Log's own file pane carries - Show Diff, Revert, Show History for Revision
and Group By - and the platform's repository menu on right click: Show Diff with Local, Open Repository Version,
Revert, Create Patch, Get Version, Show History for Revision.

Where the diff pane sits is remembered outside the project, so the tab opens the way it was last left in any
project, and so are the positions of the two splitters.

### Which refs can be shown

Every ref the repository actually holds a reflog for, grouped in the selector by what kind of ref it is: `HEAD`,
branches, remote-tracking branches, the stash, and anything else under `refs/` that has been logged.

The list is read from the reflog files themselves rather than derived from the refs, because neither of the
alternatives is dependable:

- `git reflog list` would answer it outright, but it only arrived in git 2.45;
- the refs disagree with the reflogs in both directions - a fresh clone leaves `refs/remotes/origin/HEAD` with a
  reflog and `refs/remotes/origin/master` without one, and `refs/stash` shows up only once something is stashed.

Switching the repository resets the ref to `HEAD`, and so does asking for a ref that has been deleted meanwhile.

### Filtering

Two filters narrow what the table shows, and they combine:

- **the filter field** - matched against everything shown as text: the description, the action, the selector, the
  commit message and the author, plus the beginning of the hash, so a pasted hash prefix finds its entry. The
  table narrows as the text is typed, which leaves Enter free to mean what it means in the IDE's other search
  fields: keep this one. The field's history is kept across sessions, a reflog being where one goes back to look
  for the same lost commit twice;
- **the action filter** - a checkbox per kind of operation, every one of them ticked to begin with, since every
  kind is shown. Unticking one is what narrows the table, and **All** above them - or the filter's reset button -
  ticks them all again. Unticking every kind, by hand or through **All**, is taken as ticking them all instead:
  nobody asks to be shown nothing, and an empty table would read as a fault rather than as an answer. The kinds
  are collected from the entries at hand, so the list never offers an operation this reflog does not
  contain, and `commit (amend)` is offered under `commit` rather than as a kind of its own. A reflog whose
  entries carry no action at all - the stash - leaves the filter disabled.

  What is remembered is which kinds were unticked, not which were left ticked. The kinds on offer come from the
  entries read so far, so a Load More can bring one that did not exist when the filter was set - and a kind
  nobody has unticked has to keep passing.

Both run over the entries already read, which is what makes them instant. Whenever the table shows fewer entries
than were read, the count at the end of the toolbar says so.

All three are drawn by the platform's `FilterComponent`, which is what the Log's Branch, User and Date filters
are built on, so they look the same in both tabs: the name, then the value - **Ref: HEAD**, **Actions: All**,
**Actions: 3 kinds**.

Unlike the Log's, each of them always has a value worth showing - there is one repository being read, one ref
being shown, and some set of kinds getting through - so none has the Log's unset state where the name stands
alone and a reset button clears it. They keep a drop-down arrow where a Log filter has its reset, and the popup
is where a filter is put back. Since `FilterComponent` writes the `": "` between name and value only for a filter
it considers set, the tab supplies that separator itself, which keeps the name and the value two labels, each in
the colour the platform gives it.

### Actions on the selected entry

- **Show Diff: <comparison>** - opens in the diff viewer whatever the file pane is showing, and is named for
  it: on its own, "Show Diff" cannot say which of the four comparisons it means, and the pane that would
  otherwise be the answer is one of the things the tab lets you put away. Also opened by a double click on an
  entry, that being the same gesture said faster.
- **Show Diff As** - the same four comparisons, opened in the diff viewer without changing which one the file
  pane is showing. Greyed and withheld exactly as **Compare** is, what fits being a property of the selection
  rather than of which menu is asking.
- **Checkout Revision** - checks the working tree out at the commit, leaving the repository on a detached `HEAD`.
- **New Branch from Here** - creates a branch at the commit and checks it out. A branch keeps the commit alive
  past the expiry of the reflog, which is what makes this the way to rescue work rather than only look at it.
- **Reset Current Branch to Here** - resets the branch the repository is on, in the mode picked in the dialog
  (Soft, Mixed, Hard or Keep). It is always the current branch that moves, whichever ref's reflog is on screen.
- **Select in Git Log** - jumps to the commit in the Log tab.
- **Copy Revision Number** - the full hash.

The three operations are carried out by git4idea itself - `GitBrancher` and `GitResetOperation` - so local
changes, the progress and the notifications are handled exactly as they are for the same operations started from
the Log, and the reset is undoable through Local History.

### Caveat: Select in Git Log only finds commits the Log knows

The Log is built from commits reachable from refs, so an entry left behind by a reset or a rebase will not be found
there. Show Diff reads the commit directly and works for those as well - which is the case the reflog exists for.

### Reading past the first page

Reflogs of long-lived repositories hold tens of thousands of records, so a read asks for the newest 1000 of them
rather than all. When a read comes back full - and only then - a **Load More** link appears and reads another
1000 on top. A read that comes back short of what it asked for has reached the end of the reflog, which is how
the tab knows there is nothing more to offer without asking git a second time.

The link is drawn beside the count of what has been read, rather than contributed to the toolbar as an action.
The two say one thing between them - how much is on screen, and that there is more - and a click moves the number
it stands beside, which is the answer to whether the click did anything. An action would also have had to wait
for a toolbar to ask it what to show, where a toolbar has no way of knowing that git has answered.

The count says which of three states the reading is in: `Showing 1,000 of the newest 1,000 entries read` while a
page is left unread, `Showing 12 of 1,037` while a filter is narrowing what was read, and `Showing all 1,037
entries` once the whole reflog is on screen and nothing is hidden. That last one is what replaces Load More when
the last page has been read.

This matters because the filters run over what was read: an entry older than the last page is not found by
filtering either. The selected entry survives a Load More the same way it survives a re-read.

Switching the repository starts again at one page for every ref: another repository has another set of refs, and
what was read of the previous one's says nothing here. Switching the ref does not. A ref is read from its first
page the first time it is shown - another reflog being another length - and from as far as it had been read on
every return to it, so that a glance at the stash does not cost the pages of HEAD that were loaded to look at.

## How the tab is wired in

- The tab is contributed through the `com.intellij.changesViewContent` extension point, which is how the persistent
  tabs of the Git and Commit tool windows are registered, and is shown for projects that have Git as an active VCS.
- Entries are read with `git reflog show` in a machine-readable format: one record per output line, fields separated
  by `0x01`. See [GitReflogReader.kt][file:GitReflogReader.kt] for why the timestamp has to come from the `%gd`
  placeholder, and [GitReflogParser.kt][file:GitReflogParser.kt] for why the `HEAD@{n}` index comes from the
  record's position in the output. The parser is a separate object so that the format can be covered by tests
  without a repository.
- Every action acts on what the tab publishes into the data context - the selected entries for the actions over
  a single entry, the whole selection against its reflog and which comparisons fit it for the ones that compare,
  and the revision numbers for Select in Git Log and Copy Revision Number, which are platform actions the plugin
  only references. Publishing a snapshot rather than letting an action read the panel is what lets every action
  update off the EDT: working out which comparisons fit reads the table, and only the EDT may do that.
- The file pane's toolbar and menu add the platform's `Vcs.RepositoryChangesBrowserToolbar` and
  `Vcs.RepositoryChangesBrowserMenu` groups to what `ChangesBrowserBase` already brings. Where the Log keeps the
  placement of its diff pane in a View Options popup on that same toolbar, the tab keeps it as two buttons on its
  own toolbar instead, next to Refresh.
- The file and diff panes are the platform's `SimpleAsyncChangesBrowser` and the viewer that
  `TreeHandlerEditorDiffPreview.createDefaultViewer` builds on top of its tree. The Log's own equivalents,
  `VcsLogChangesBrowser` and its `FrameDiffPreview`, are internal to the Log, but they are built out of exactly
  these two pieces, which is why the panes behave the same - including the Combined Diff viewer when it is
  enabled. The diff viewer is registered under a place of the plugin's own, so the settings its toolbar writes
  belong to this tab rather than following the Log.
- The filters are `FilterComponent` subclasses added to the toolbar row directly. The Log's own wrapper for
  putting one in a toolbar, `VcsLogPopupComponentAction`, is marked internal, so the plugin opens the popup
  itself - as an action group updated off the EDT, with speed search, which is what the Log does too.
- The reset dialog is the plugin's own because git4idea's is built around a `VcsFullCommitDetails` loaded from the
  Log, which is exactly what an unreachable commit does not have. The mode names and descriptions still come from
  git4idea, so the choice reads the same as in the Log.
- A ref that exists but was never logged is not an error for git - it answers with an empty reflog - while a ref
  that is gone is, so every read resolves the ref against the refs that currently have a reflog and falls back to
  `HEAD` rather than surface that error.


[file:GitReflogParser.kt]: ./src/main/kotlin/cz/loplex/reflog/GitReflogParser.kt
[file:GitReflogReader.kt]: ./src/main/kotlin/cz/loplex/reflog/GitReflogReader.kt
