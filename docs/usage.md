# Driving the Reflog tab

*What the tab shows, and what each control on it does.*

What the plugin is, and how to install it, is in [the README](../README.md).\
Why it behaves as it does is in [design.md](design.md). Nothing here repeats that.

- [The table](#the-table) - the seven columns, and the one kind of entry that fills them differently.
- [The toolbar](#the-toolbar) - what stands at either end of the row.
- [The two panes](#the-two-panes) - **Show Changed Files**, **Preview Diff on the Right**, **Preview Diff at the
  Bottom**.
- [Compare](#compare) - the four comparisons, when each is offered, and what an empty answer says.
- [Which refs can be shown](#which-refs-can-be-shown) - the ref selector, and what resets it.
- [Filtering](#filtering) - the filter field and the action filter, and what is remembered of them.
- [Actions on the selection](#actions-on-the-selection) - Show Diff, Checkout Revision, New Branch from Here,
  Reset Current Branch to Here.
- [Reading past the first page](#reading-past-the-first-page) - **Load More**, and the three things the count
  says.

## The table

The tab lists the reflog of one ref of one repository, newest entry first:

| Column | Content |
|---|---|
| Selector | `HEAD@{0}`, `HEAD@{1}`, ... - what `git reset`, `git checkout` and `git show` accept |
| Date | when the entry was written, which for a checkout is unrelated to the commit's own date |
| Action | the operation that moved `HEAD`: `commit`, `checkout`, `reset`, `rebase (finish)`, ... |
| Description | the rest of the reflog message, for example `moving from master to feature` |
| Commit | short hash of the commit `HEAD` pointed at afterwards |
| Commit Message | first line of that commit's own message |
| Author | author of that commit |

The last three columns describe the commit rather than the movement, which is the [reason they are
there](design.md#the-commit-columns-are-what-the-log-cannot-be-asked-for).

### Caveat: on a stash entry, the whole message is the description

`git stash` writes subjects such as `WIP on master: eddeef8 first`, where the colon separates the branch from the
commit rather than an action from its details. The whole subject is kept as the description there, and the Action
column stays empty.

### The tab re-reads itself on its own

The reflog is read again whenever the state of the repository changes, which covers every operation that writes
a reflog record.

The selected entry survives such a re-read: it is recognised by what it records rather than by its selector,
since every new record pushes `HEAD@{0}` down to `HEAD@{1}`.

## The toolbar

| End of the row | What stands there |
|---|---|
| Left | the filter field, then the ref selector, the action filter, and - in projects with more than one Git repository - a repository selector |
| Right | the count of what has been read, a **Load More** link where there is more to read, then the buttons that place the two panes, and Refresh |

## The two panes

Below and beside the table sit the same two panes the Log has: the files the selected entry's commit changed,
and the diff of the file selected among them.

The files pane sits next to the table, and the diff wraps both - the arrangement the Log uses.

The files are read from git directly, so they are there for a commit no ref reaches any more. That is the same
reason Show Diff works where **Select in Git Log** does not.

### Show Changed Files puts the files pane away

It is a button of its own rather than a third state of the diff buttons, so that either pane can be had without
the other - and the table can be had without both. With both away, nothing is read at all.

### Preview Diff on the Right and Preview Diff at the Bottom place the diff pane

The platform's pair of preview buttons cover all three states in a single click:

- neither pressed - no diff pane;
- either one pressed - the diff pane on that side;
- pressing the one already down - hidden again.

It starts at the bottom, where the Log starts its own.

### The diff pane is worth having without the files pane

With nothing selected among the files, the platform hands the diff every change of the entry rather than none.

The pane becomes the entry's whole diff at the width of the tab, paged through with the diff viewer's own
navigation instead of by clicking a list. What is given up is jumping straight to a named file.

### The files pane's own toolbar and menu

The toolbar carries what the Log's own files pane carries - Show Diff, Revert, Show History for Revision and
Group By - and right click adds the platform's repository menu: Show Diff with Local, Open Repository Version,
Revert, Create Patch, Get Version, Show History for Revision.

### What is remembered

Remembered outside the project, so the tab opens the way it was last left in any project:

- which comparison **Compare** is set to;
- which side the diff pane is on, or that there is none;
- the positions of the two splitters.

## Compare

A reflog is a timeline of one ref, so "the state before" and "the state after" exist even where the commits are
on branches that never met. **Compare**, on the files pane's toolbar, picks which of them the files are read
from:

| Comparison | What it shows | Selection it needs |
|---|---|---|
| **Reflog Step** | What the selected movements did to the working tree, from the state before the oldest to the state after the newest | Any, as long as the reflog still holds the entry before the oldest selected one |
| **Between Selected** | How the newest selected state differs from the oldest one, leaving out the movement that produced the oldest | Two or more entries |
| **Selected Commits** | What each selected commit changed against its own parent, merged into one tree, as the Log answers a multiple selection | Any on a stash; on a timeline, more than one entry only where they sit on one line of history |
| **Against Working Tree** | How the working tree differs from the selected state | A single entry |

Reflog Step is where the tab starts, and the one that sets the reflog apart from the Log: for a `commit` entry
it is the commit's own diff, but for a `checkout` or a `reset` it is "what changed under me".

### A comparison the selection has no answer for is greyed, not hidden

Both the toolbar and the context menus - the table's as well as the files pane's - list every comparison, and
grey out the ones that do not fit the selection. [Why greyed rather than
absent](design.md#a-comparison-that-does-not-fit-is-greyed-rather-than-withheld).

A choice that has no answer for the selection of the moment gives way to the nearest one that does, rather than
emptying the pane - and comes back as soon as a selection it suits is made again.

What the toolbar and the ticks in the menu show is always the comparison on screen, so a selection being
answered by a different one is never silent.

### Which comparisons a selection has no answer for

- **On a stash**, the two readings that ask what a movement did are not offered: a stash reflog is a stack of
  unrelated entries rather than a timeline of one state.
- **On a timeline, for several entries**, **Selected Commits** is offered only once git has confirmed each entry
  is an ancestor of the next. On a stash that check is [not asked at
  all](design.md#the-ancestry-check-is-asked-of-timelines-only), and **Selected Commits** fits a stash selection
  of any size.
- **With nothing selected**, there is nothing to compare.

### What an empty comparison says

It says which comparison it made, and of what:

- `Nothing changed between 3f0a91c2 and 8dede56f`
- `The working tree matches 8dede56f`
- `Nothing moved: HEAD stood at 8dede56f both before and after` - where the movement left the ref where it found
  it, such as a checkout between two branches standing on the same commit, or the reset `git stash` makes
  internally once it has put the work away.

## Which refs can be shown

Every ref the repository actually holds a reflog for, grouped in the selector by what kind of ref it is: `HEAD`,
branches, remote-tracking branches, the stash, and anything else under `refs/` that has been logged.

The selector opens on the ref being shown, with a tick against it, so Enter straight after opening confirms what
is already on screen.

The ref resets to `HEAD` when the repository is switched, and when a ref that was being shown is deleted
meanwhile.

## Filtering

Two filters narrow what the table shows, and they combine. Both run over the entries already read, which is what
makes them instant.

**The filter field** is matched against everything shown as text - the description, the action, the selector,
the commit message and the author - plus the beginning of the hash, so a pasted hash prefix finds its entry.

- The table narrows as the text is typed, which leaves Enter to mean what it means in the IDE's other search
  fields: keep this one.
- The field's history is kept across sessions.

**The action filter** is a checkbox per kind of operation, every one of them ticked to begin with.

- Unticking one is what narrows the table. **All** above them, or the filter's reset button, ticks them all
  again.
- Unticking every kind, by hand or through **All**, is taken as ticking them all instead.
- The kinds are collected from the entries at hand, so the list never offers an operation this reflog does not
  contain, and `commit (amend)` is offered under `commit` rather than as a kind of its own.
- What is remembered is which kinds were unticked, not which were left ticked, so a kind that arrives with a
  later page keeps passing until somebody unticks it.
- A reflog whose entries carry no action at all - the stash - leaves the filter disabled.

Whenever the table shows fewer entries than were read, the count at the end of the toolbar says so.

### The filters read as the Log's do

All three - the ref selector, the action filter and the repository selector - are drawn by the platform's
`FilterComponent`, which is what the Log's Branch, User and Date filters are built on: the name, then the value.
**Ref: HEAD**, **Actions: All**, **Actions: 3 kinds**.

Unlike the Log's, each of them always has a value worth showing, so none has the Log's unset state where the
name stands alone. They keep a drop-down arrow where a Log filter has its reset, and the popup is where a filter
is put back.

## Actions on the selection

On the table's context menu, and on the selected entries:

| Action | What it does |
|---|---|
| **Show Diff: \<comparison\>** | Opens in the diff viewer whatever the files pane is showing, and is named for it. Also opened by a double click on an entry |
| **Show Diff As** | The same four comparisons, opened in the diff viewer without changing which one the files pane is showing. Greyed and withheld exactly as **Compare** is |
| **Checkout Revision** | Checks the working tree out at the commit, leaving the repository on a detached `HEAD` |
| **New Branch from Here** | Creates a branch at the commit and checks it out. A branch keeps the commit alive past the expiry of the reflog, which is what makes this the way to rescue work rather than only look at it |
| **Reset Current Branch to Here** | Resets the branch the repository is on, in the mode picked in the dialog - Soft, Mixed, Hard or Keep. It is always the current branch that moves, whichever ref's reflog is on screen |
| **Select in Git Log** | Jumps to the commit in the Log tab |
| **Copy Revision Number** | The full hash |

The three operations that move something are carried out by git4idea itself, so local changes, the progress and
the notifications are handled exactly as they are for the same operations started from the Log, and the reset is
undoable through Local History.

### Caveat: Select in Git Log only finds commits the Log knows

The Log is built from commits reachable from refs, so an entry left behind by a reset or a rebase will not be
found there.

Show Diff reads the commit directly and works for those as well - which is the case the reflog exists for.

## Reading past the first page

A read asks for the newest 1000 records rather than all of them, reflogs of long-lived repositories holding tens
of thousands.

**Load More** appears when a read comes back full, and reads another 1000 on top. A read that comes back short
of what it asked for has reached the end of the reflog, so there is nothing more to offer.

The selected entry survives a Load More the same way it survives a re-read.

### What the count says

| The count | What it means |
|---|---|
| `Showing 1,000 of the newest 1,000 entries read` | a page is left unread - **Load More** is beside it |
| `Showing 12 of 1,037` | a filter is narrowing what was read |
| `Showing all 1,037 entries` | the whole reflog is on screen and nothing is hidden |

That last one is what replaces **Load More** when the last page has been read.

### Caveat: a filter only finds what has been read

An entry older than the last page read is not found by filtering either, both filters running over the entries
already read.

### Switching the ref keeps its pages; switching the repository does not

- **A ref** is read from its first page the first time it is shown, and from as far as it had been read on every
  return to it - so a glance at the stash does not cost the pages of `HEAD` that were loaded to look at.
- **A repository** starts again at one page for every ref: another repository has another set of refs, and what
  was read of the previous one's says nothing here.
