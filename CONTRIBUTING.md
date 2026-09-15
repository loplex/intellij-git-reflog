# Working on the plugin

How to build this plugin, run it, and find your way around its sources.

What the plugin *does*, and why it behaves as it does, is in [the README](README.md). Nothing here repeats it.

- [Build and test](#build-and-test) - `./gradlew test`, `buildPlugin`, `verifyPlugin`.
- [Running the tab](#running-the-tab) - `runIde`, and the repository worth pointing it at.
- [Project layout](#project-layout) - where each kind of file lives.
- [The build script](#the-build-script) - the three Gradle plugins, and the platform compiled against.
- [The plugin manifest](#the-plugin-manifest) - `plugin.xml`, and the one value in it that may never change.

## Build and test

The Gradle wrapper needs nothing installed but a JDK:

```bash
./gradlew test          # the whole suite
./gradlew buildPlugin   # build/distributions/ij-git-reflog-<version>.zip
./gradlew verifyPlugin  # the Marketplace compatibility checks
```

The zip `buildPlugin` leaves behind is what **Settings | Plugins | ⚙ | Install Plugin from Disk...** takes, so
it is also how to try a build in your own IDE rather than in the sandbox.

Expect the first run of any of them to be slow: the IntelliJ Platform is downloaded and unpacked once, and what
it unpacks to is a little over 4 GB. `verifyPlugin` fetches an IDE of its own to verify against, and another
for every version the compatibility range covers, so it costs that again per version.

The tests build the repositories they read out of a fixture, so they neither touch nor need a Git repository of
your own. They do start a headless IDE, which is what most of their runtime is.

## Running the tab

Most of this tab can only be judged by looking at it. A dozen of its faults were found that way and none of them
by a test - a separator with no height, a popup reopened by its own dismissal, a label too wide to be drawn at
all.

Two things are needed: a repository whose reflog holds the cases, and a sandbox IDE opened on it.

```bash
bash tools/reflog-playground.sh /tmp/reflog-playground
./gradlew runIde --args="/tmp/reflog-playground"
```

`runIde` takes the project to open as a program argument. Without one the sandbox opens empty and the tab has no
repository to read, which looks like the plugin failing rather than like the sandbox being empty.

What the playground script leaves behind is a reflog holding a movement that put `HEAD` back where it found it,
a stash of unrelated work, a branch that never rejoins, more entries than a single read returns, and an
uncommitted edit for the working-tree comparison to differ by.

The rest of it - driving the tab without a window manager, why a virtual display is needed even on a desktop,
and what a screenshot can and cannot show - is in `.claude/skills/run-reflog-tab/`, which is tracked with the
sources.

### The run configurations in `.run/`

Three Gradle tasks, wrapped so the IDE can start them from the gutter. Run IDE also debugs the plugin, under the
*Debug* icon.

| Configuration | Task |
|---|---|
| Run IDE with Plugin | `runIde` |
| Run Tests | `check` |
| Run Verifications | `verifyPlugin` |

## Project layout

```
.
├── .claude/skills/         Skills tracked with the sources, one per task worth not rediscovering
├── .run/                   Run/Debug configurations, described above
├── gradle/
│   ├── wrapper/            Gradle wrapper
│   └── libs.versions.toml  Version catalog - JUnit and nothing else
├── src/
│   ├── main/
│   │   ├── kotlin/         Production sources
│   │   └── resources/
│   │       ├── META-INF/   plugin.xml and the two plugin icons
│   │       └── messages/   Message bundle
│   └── test/kotlin/        Tests
├── tools/
│   └── reflog-playground.sh  Builds a repository whose reflog covers every case the tab has
├── build.gradle.kts        Build configuration
├── gradle.properties       Group, version, and the Gradle caches
├── CHANGELOG.md            Kept by hand, in Keep a Changelog form
└── settings.gradle.kts     Project settings
```

There is no `src/main/java`: the plugin is Kotlin throughout. Adding Java means creating that directory, which
the Kotlin plugin already compiles alongside.

## The build script

[`build.gradle.kts`](build.gradle.kts) applies three Gradle plugins:

| Plugin | What it brings |
|---|---|
| `org.jetbrains.kotlin.jvm` | Kotlin |
| `org.jetbrains.intellij.platform` | The platform to compile against, and the tasks that run and package it |
| `org.jetbrains.changelog` | Patching [CHANGELOG.md](CHANGELOG.md) on a release |

The `intellijPlatform` dependencies block says what is compiled against:

- `intellijIdea("2025.3.5")` - the IDE the plugin is built on;
- `bundledPlugin("Git4Idea")` - the tab reaches into git4idea for the reflog, the branch operations and the
  reset;
- `testFramework(TestFrameworkType.Platform)` and `TestFrameworkType.Plugin.VCS` - what lets a test start a
  headless IDE and build a repository to read.

### Caveat: a stale configuration cache entry can fail `instrumentTestCode`

Configuration caching is on in `gradle.properties`, and one cached entry has been seen to fail a build that
`--no-configuration-cache` ran green:

```
Execution failed for task ':instrumentTestCode'.
> skip doesn't support the nested "instrumentIdeaExtensions" element.
```

A `clean` followed by a cached run reproduces nothing, so the entry rather than the build looks to have been at
fault. Worth knowing for a CI job that caches `.gradle/` between runs, which sits in exactly that state; a job
that runs cold every time cannot hit it.

## The plugin manifest

[`plugin.xml`](src/main/resources/META-INF/plugin.xml) carries what the IDE and the Marketplace read: the id,
the name, the vendor, the description shown on the plugin page, the dependencies, and the extension points the
tab is contributed through.

**`<id>` may never change.** It is what an installed plugin is recognised by, so changing it between versions
publishes a second plugin rather than an update to the first. `rootProject.name` and `project.group` in Gradle
are free of it and need not match.
