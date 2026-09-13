# Git Reflog

[![Twitter Follow](https://img.shields.io/badge/follow-%40JBPlatform-1DA1F2?logo=twitter)](https://twitter.com/JBPlatform)
[![Developers Forum](https://img.shields.io/badge/JetBrains%20Platform-Join-blue)][jb:forum]

## Overview

This plugin adds a **Reflog** tab to the Git tool window, between the Log and the Console tab.

`git reflog` is the record of every movement of `HEAD` - commits, checkouts, resets, rebases, amends.\
It is also the only place where a commit that no branch points at any more can still be found.\
IntelliJ IDEA has no UI for it, so recovering work after a bad reset means leaving the IDE for a terminal.

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

The toolbar holds the ref selector, the action filter, the two diff pane buttons, Refresh, Load More where there
is more to read, and, in projects with more than one Git repository, a repository selector. The filter field sits at the right end of the same row.

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
| **Selected Commits** | What each selected commit changed against its own parent, merged into one tree, as the Log answers a multiple selection | Any, and for more than one entry only where they sit on one line of history |
| **Against Working Tree** | How the working tree differs from the selected state | A single entry |

Reflog Step is where the tab starts, and the one that sets the reflog apart from the Log: for a `commit` entry
it is the commit's own diff, but for a `checkout` or a `reset` it is "what changed under me", which no diff
against a parent can show.

Two comparisons are deliberately withheld. A stash reflog is a stack of unrelated entries rather than a timeline
of one state, so the readings that treat it as a timeline are not offered for it; and merging the changes of
commits that sit on branches which never met produces a tree of changes that undo one another, so **Selected
Commits** is offered for several entries only once git has confirmed each is an ancestor of the next.

The choice is remembered outside the project, so the tab opens the way it was last left. A choice that has no
answer for the selection of the moment gives way to the nearest one that does, rather than emptying the pane -
and comes back as soon as a selection it suits is made again. What the toolbar and the ticks in the menu show is
always the comparison on screen, so a selection being answered by a different one is never silent. The context
menu lists only the comparisons that fit what is selected; the toolbar lists all of them, disabling the rest, so
that a comparison that exists can still be seen to exist.

Reading the files is held back 150 ms after the selection moves, so walking the table with the arrow keys does
not start a git call per row passed over. A selection that comes back to the same entries under the same
comparison - which is what every re-read does, by restoring the selection it had - is left alone rather than
read again: what a commit changed cannot change.

**Against Working Tree** is the exception, being the one reading whose answer can change without the reflog
changing at all. It is read again every time the pane is asked to, and the tab listens to the change lists on top
of the repository, so that an edit which is merely saved - touching no git state for a repository listener to
hear about - still reaches it.

Whether the selected entries sit on one line of history takes a `git merge-base --is-ancestor` per neighbouring
pair to answer, so it is asked only once the answer can change which comparison is shown - never on the way past
a row.

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
  commit message and the author, plus the beginning of the hash, so a pasted hash prefix finds its entry;
- **the action filter** - a checkbox per kind of operation, every one of them ticked to begin with, since every
  kind is shown. Unticking one is what narrows the table, and **All** above them - or the filter's reset button -
  ticks them all again. The kinds are collected from the entries at hand, so the list never offers an operation this reflog does not
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

- **Show Diff** - the changes of the commit, read with `git show`. Also opened by a double click.
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
rather than all. When a read comes back full - and only then - a **Load More** button appears in the toolbar and
reads another 1000 on top.

This matters because the filters run over what was read: an entry older than the last page is not found by
filtering either. The count next to the filter field says when a page boundary is in play, and the selected entry
survives the Load More the same way it survives a re-read.

Switching the ref or the repository starts again at one page, since how far the previous reflog had been read
says nothing about the new one.

## How the tab is wired in

- The tab is contributed through the `com.intellij.changesViewContent` extension point, which is how the persistent
  tabs of the Git and Commit tool windows are registered, and is shown for projects that have Git as an active VCS.
- Entries are read with `git reflog show` in a machine-readable format: one record per output line, fields separated
  by `0x01`. See [GitReflogReader.kt][file:GitReflogReader.kt] for why the timestamp has to come from the `%gd`
  placeholder, and [GitReflogParser.kt][file:GitReflogParser.kt] for why the `HEAD@{n}` index comes from the
  record's position in the output. The parser is a separate object so that the format can be covered by tests
  without a repository.
- Every action acts on what the tab publishes into the data context - the selected entries for the plugin's own
  actions, the revision numbers for Select in Git Log and Copy Revision Number, which are platform actions the
  plugin only references, and whether a page was left unread for Load More. Publishing that last one rather than
  reading it off the panel is what lets every action update off the EDT.
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

## Plugin structure

A generated project contains the following content structure:

```
.
├── .run/                   Predefined Run/Debug Configurations
├── gradle
│   ├── wrapper/            Gradle Wrapper
│   ├── libs.versions.toml  Version catalog
├── src                     Plugin sources
│   └── main
│       ├── kotlin/         Kotlin production sources
│       └── resources/      Plugin resources
│           ├── META-INF/   Plugin configuration file and logo
│           └── messages/   Message bundles
├── .gitignore              Git ignoring rules
├── build.gradle.kts        Gradle build configuration
├── gradle.properties       Gradle configuration properties
├── gradlew                 *nix Gradle Wrapper script
├── gradlew.bat             Windows Gradle Wrapper script
├── README.md               This file
└── settings.gradle.kts     Gradle project settings
```

In addition to the configuration files, the most crucial part is the `src` directory, which contains our implementation
and the manifest for our plugin – [plugin.xml][file:plugin.xml].

> [!NOTE]
> To use Java in your plugin, create the `/src/main/java` directory.

The plugin logo is placed in `src/main/resources/META-INF/pluginIcon.svg`.
See [Plugin Logo][docs:logo] for more information and logo requirements.

## Build script

The [build.gradle.kts][file:build.gradle.kts] is the core of the project definition.
It applies three Gradle plugins:

| Plugin                            | Description                                                                      |
|-----------------------------------|----------------------------------------------------------------------------------|
| `org.jetbrains.kotlin.jvm`        | Adds Kotlin support                                                              |
| `org.jetbrains.changelog`         | Simplifies patching the [CHANGELOG.md][file:CHANGELOG.md] file                   |
| `org.jetbrains.intellij.platform` | The [IntelliJ Platform Gradle Plugin][docs:intellij-platform-gradle-plugin-docs] |

The `intellijPlatform` dependencies block selects the IDE to compile against:

```kotlin
intellijIdea("2025.3.5")
```

See [Target Versions][docs:target-version] for more information.

The `intellijPlatform` dependencies block also contains a dependency on the platform testing framework:

```kotlin
testFramework(TestFrameworkType.Platform)
```

See [Testing][docs:testing] for more information

## Plugin configuration file

The plugin configuration file is a [plugin.xml][file:plugin.xml] file located in the `src/main/resources/META-INF`
directory.
It provides general information about the plugin, its dependencies, extensions, and listeners.

You can read more about this file in the [Plugin Configuration File][docs:plugin.xml] section of our documentation.

### Plugin ID and name

Generated plugin ID and name may require adjustment.

These values are generated based on _Group ID_ and _Artifact ID_ provided in the IDE Plugin wizard.
It is recommended to review `<id>` and `<name>` elements in the plugin.xml file, and adjust them if needed.

Please note that Gradle properties `rootProject.name` and `project.group` don't need to match the `<id>` and `<name>`
elements.
There is no IntelliJ Platform-related reason they should as they serve different functions.

## Predefined Run/Debug configurations

Within the default project structure, there is a `.run` directory provided containing predefined *Run/Debug
configurations* that expose corresponding Gradle tasks:

| Configuration name  | Description                                                                                                                                                                           |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Run IDE with Plugin | Runs [`:runIde`][docs:intellij-platform-gradle-plugin-runIde] IntelliJ Platform Gradle Plugin task. Use the *Debug* icon for plugin debugging.                                        |
| Run Tests           | Runs [`:check`][gradle:lifecycle-tasks] Gradle task.                                                                                                                                  |
| Run Verifications   | Runs [`:verifyPlugin`][docs:intellij-platform-gradle-plugin-verifyPlugin] IntelliJ Platform Gradle Plugin task to check the plugin compatibility against the specified IntelliJ IDEs. |

> [!NOTE]
> You can find the logs from the running task in the `idea.log` tab.

## Publishing the plugin

> [!TIP]
> Make sure to follow all guidelines listed in [Publishing a Plugin][docs:publishing] to follow all recommended and
required steps.

Releasing a plugin to [JetBrains Marketplace](https://plugins.jetbrains.com) is a straightforward operation that uses
the `publishPlugin` Gradle task provided by
the [intellij-platform-gradle-plugin][docs:intellij-platform-gradle-plugin-docs].

You can also upload the plugin to the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/upload)
manually via UI.

## Useful links

- [IntelliJ Platform SDK Plugin SDK][docs]
- [IntelliJ Platform Gradle Plugin Documentation][docs:intellij-platform-gradle-plugin-docs]
- [IntelliJ Platform Explorer][jb:ipe]
- [JetBrains Marketplace Quality Guidelines][jb:quality-guidelines]
- [IntelliJ Platform UI Guidelines][jb:ui-guidelines]
- [JetBrains Marketplace Paid Plugins][jb:paid-plugins]
- [IntelliJ SDK Code Samples][gh:code-samples]

[docs]: https://plugins.jetbrains.com/docs/intellij
[docs:plugin.xml]: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html?from=IJPluginReadmeFile
[docs:publishing]: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginReadmeFile
[docs:intellij-platform-gradle-plugin-docs]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html?from=IJPluginReadmeFile
[docs:intellij-platform-gradle-plugin-runIde]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html?from=IJPluginReadmeFile#runIde
[docs:intellij-platform-gradle-plugin-verifyPlugin]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html?from=IJPluginReadmeFile#verifyPlugin
[docs:logo]: https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html?from=IJPluginReadmeFile
[docs:target-version]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html?from=IJPluginReadmeFile#target-versions
[docs:testing]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html?from=IJPluginReadmeFile#testing

[file:build.gradle.kts]: ./build.gradle.kts
[file:CHANGELOG.md]: ./CHANGELOG.md
[file:gradle.properties]: ./gradle.properties
[file:GitReflogParser.kt]: ./src/main/kotlin/cz/loplex/reflog/GitReflogParser.kt
[file:GitReflogReader.kt]: ./src/main/kotlin/cz/loplex/reflog/GitReflogReader.kt
[file:plugin.xml]: ./src/main/resources/META-INF/plugin.xml

[gh:code-samples]: https://github.com/JetBrains/intellij-sdk-code-samples

[gradle:lifecycle-tasks]: https://docs.gradle.org/current/userguide/java_plugin.html#lifecycle_tasks

[jb:github]: https://github.com/JetBrains/.github/blob/main/profile/README.md
[jb:forum]: https://platform.jetbrains.com/
[jb:quality-guidelines]: https://plugins.jetbrains.com/docs/marketplace/quality-guidelines.html
[jb:paid-plugins]: https://plugins.jetbrains.com/docs/marketplace/paid-plugins-marketplace.html
[jb:ipe]: https://jb.gg/ipe
[jb:ui-guidelines]: https://jetbrains.github.io/ui
