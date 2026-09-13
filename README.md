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

Stash entries are the exception to the Action/Description split: `git stash` writes subjects such as
`WIP on master: eddeef8 first`, where the colon separates the branch from the commit rather than an action from
its details. The whole subject is kept as the description there and the Action column stays empty.

The toolbar holds the ref selector, the action filter, Refresh, and, in projects with more than one Git
repository, a repository selector. The filter field sits at the right end of the same row.

The tab re-reads the reflog on its own whenever the state of the repository changes, which covers every operation
that writes a reflog record.

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

- **the filter field** - matched against the description, the action and the selector, and against the beginning
  of the hash, so a pasted hash prefix finds its entry;
- **the action filter** - a list of checkboxes over the kinds of operation. The kinds are collected from the
  entries at hand, so the list never offers an operation this reflog does not contain, and `commit (amend)` is
  offered under `commit` rather than as a kind of its own. A reflog whose entries carry no action at all - the
  stash - leaves the filter disabled.

Both run over the entries already read, which is what makes them instant. Whenever the table shows fewer entries
than were read, the count next to the filter field says so.

### Actions on the selected entry

- **Show Diff** - the changes of the commit, read with `git show`. Also opened by a double click.
- **Select in Git Log** - jumps to the commit in the Log tab.
- **Copy Revision Number** - the full hash.

### Caveat: Select in Git Log only finds commits the Log knows

The Log is built from commits reachable from refs, so an entry left behind by a reset or a rebase will not be found
there. Show Diff reads the commit directly and works for those as well - which is the case the reflog exists for.

### Caveat: one read returns at most 1000 entries

Reflogs of long-lived repositories can hold tens of thousands of records, and the tab reads the newest 1000 of
them. Since the filters run over what was read, an entry older than that is not found by filtering either - the
count next to the filter field says when the cap is in play.

## How the tab is wired in

- The tab is contributed through the `com.intellij.changesViewContent` extension point, which is how the persistent
  tabs of the Git and Commit tool windows are registered, and is shown for projects that have Git as an active VCS.
- Entries are read with `git reflog show` in a machine-readable format: one record per output line, fields separated
  by `0x01`. See [GitReflogReader.kt][file:GitReflogReader.kt] for why the timestamp has to come from the `%gd`
  placeholder and the `HEAD@{n}` index from the record position.
- Show Diff, Select in Git Log and Copy Revision Number act on the revision numbers the tab publishes into the data
  context; the latter two are platform actions that the plugin only references.
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
