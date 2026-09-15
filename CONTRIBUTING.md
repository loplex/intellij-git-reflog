# Working on the plugin

How to build this plugin, run it, and find your way around its sources.

What the tab *does* is in [docs/usage.md](docs/usage.md), and why it behaves as it does in
[docs/design.md](docs/design.md). Nothing here repeats either.

- [Build and test](#build-and-test) - `./gradlew test`, `buildPlugin`, `verifyPlugin`.
- [Running the tab](#running-the-tab) - `runIde`, and the repository worth pointing it at.
- [Project layout](#project-layout) - where each kind of file lives.
- [The build script](#the-build-script) - the three Gradle plugins, and the platform compiled against.
- [The plugin manifest](#the-plugin-manifest) - `plugin.xml`, and the one value in it that may never change.
- [Releasing](#releasing) - the one button that cuts a release, and the secrets it needs to have been given.

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

The documents are checked too, by a script of their own that needs neither Gradle nor a JDK:

```bash
tools/check-doc-links.py
```

It resolves every link they make into the repository - at each other, at their own sections, at source files -
and reports the ones that no longer land anywhere. External URLs are left alone, since they fail for reasons
that have nothing to do with the commit. It runs in CI beside the tests.

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
├── docs/                   usage.md, design.md, and the screenshot the README shows
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
│   ├── check-doc-links.py    Resolves every link the documents make into the repository
│   └── reflog-playground.sh  Builds a repository whose reflog covers every case the tab has
├── build.gradle.kts        Build configuration
├── LICENSE                 Apache 2.0
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

## Releasing

A release is asked for in the Actions tab and finished by accepting a draft. Nothing between the two is typed.

```
[Actions -> Prepare release -> 0.2.0]
  └─ .github/workflows/prepare-release.yml
       ├─ refuses a version already released, or one that is not a version
       ├─ refuses an empty Unreleased section
       ├─ version=0.2.0 in gradle.properties
       ├─ ./gradlew patchChangelog      ([Unreleased] becomes [0.2.0], dated and linked)
       ├─ commit "Release 0.2.0", tag v0.2.0, push
       └─ calls release-draft.yml
            ├─ ./gradlew check
            ├─ ./gradlew signPlugin
            └─ draft release, carrying ij-git-reflog-0.2.0-signed.zip

[the draft is reviewed - the archive can be installed from it - and published]
  └─ .github/workflows/release-publish.yml
       └─ ./gradlew publishPlugin, uploading that same archive
```

The archive is built once. What the Marketplace receives is the file the draft was accepted with, not a second
build of the same sources.

### What is still written by hand

The entries under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md), as the work is done. They are the release
notes on GitHub and the change notes on the Marketplace both, so a release made without them says nothing about
itself - which is why Prepare release refuses to run on an empty section rather than dating one.

Everything after that is mechanical and is done for you: the version, the changelog section and its date and
links, the commit, the tag.

### A tag pushed by hand still works

`release-draft.yml` is triggered by any `v*` tag as well as called by Prepare release, so a release can be cut
without the Actions tab - `patchChangelog`, the version, the commit and the tag done locally. A tag naming a
version other than the one in `gradle.properties` is refused rather than released, so the two cannot come apart
quietly whichever way the tag was made.

Why it has to be callable at all: a tag pushed by a workflow, with the token GitHub hands it, triggers no
workflow. That is GitHub's guard against a workflow setting itself off in a circle, and it would otherwise
leave the tag sitting there with nothing building it.

### Caveat: the first upload to the Marketplace has to be made by hand

`publishPlugin` updates a plugin that is already listed. It cannot create the listing: JetBrains require the
first version of a new plugin to be uploaded through the Marketplace's own **Upload plugin** form, and it goes
through their review before it appears. Only from the second version onwards does the workflow above do the
whole job.

### The secrets the workflows expect

| Secret | What it is |
|---|---|
| `PUBLISH_TOKEN` | A Marketplace personal access token, from your profile page there. It is shown once |
| `CERTIFICATE_CHAIN` | The signing certificate chain, in PEM |
| `PRIVATE_KEY` | The signing private key, in PEM |
| `PRIVATE_KEY_PASSWORD` | What the private key was encrypted with |

The signing key is the author's rather than the plugin's: one key pair signs every plugin you publish, and a
self-signed certificate is accepted. Generating one, if you have none:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
openssl rsa -in private_encrypted.pem -out private.pem
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

`chain.crt` goes into `CERTIFICATE_CHAIN`, `private.pem` into `PRIVATE_KEY`, and the password from the first
command into `PRIVATE_KEY_PASSWORD`. Keep `private_encrypted.pem` and the password; the plugin cannot be
updated by anyone who cannot sign with the same key.
