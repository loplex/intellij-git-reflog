# Working on the plugin

How to build this plugin, run it, and find your way around its sources.

What the tab *does* is in [docs/usage.md](docs/usage.md), and why it behaves as it does in
[docs/design.md](docs/design.md). Nothing here repeats either.

- [Build and test](#build-and-test) - `./gradlew test`, `buildPlugin`, `verifyPlugin`.
- [Running the tab](#running-the-tab) - `runIde`, and the repository worth pointing it at.
- [Project layout](#project-layout) - where each kind of file lives.
- [The build script](#the-build-script) - the three Gradle plugins, and the platform compiled against.
- [The plugin manifest](#the-plugin-manifest) - `plugin.xml`, and the one value in it that may never change.
- [Releasing](#releasing) - the one button that cuts a release, the version it reads, and the secrets it needs.

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
├── .github/
│   ├── scripts/            The README's claims, and their tests
│   └── workflows/          Build, Prepare release, Publish the accepted release
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
├── .python-version         The Python the scripts under .github/scripts/ are run with
├── build.gradle.kts        Build configuration
├── LICENSE                 Apache 2.0
├── gradle.properties       Group, the version being worked on, the tag prefix, and the Gradle caches
├── CHANGELOG.md            Kept by hand, in Keep a Changelog form
└── settings.gradle.kts     Project settings
```

There is no `src/main/java`: the plugin is Kotlin throughout. Adding Java means creating that directory, which
the Kotlin plugin already compiles alongside.

## The build script

[`build.gradle.kts`](build.gradle.kts) applies three Gradle plugins:

| Plugin                            | What it brings                                                          |
|-----------------------------------|-------------------------------------------------------------------------|
| `org.jetbrains.kotlin.jvm`        | Kotlin                                                                  |
| `org.jetbrains.intellij.platform` | The platform to compile against, and the tasks that run and package it  |
| `org.jetbrains.changelog`         | The change notes the Marketplace shows, read out of `CHANGELOG.md`      |

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

A release is asked for in the Actions tab and finished by accepting a draft. Nothing between the two is typed,
and nothing is typed to start it either: `gradle.properties` already names the version being worked on.

```
main: version=0.1.1-SNAPSHOT

[Actions -> Prepare release, on main]
  └─ .github/workflows/prepare-release.yml
       ├─ release-flow/prepare
       │    ├─ the release rules: version (0.1.1-SNAPSHOT -> 0.1.1), changelog, ancestry
       │    ├─ [Unreleased] becomes [0.1.1], dated and linked - an empty one is refused, unless
       │    │    0.1.1 closes a pre-release train and takes in the train's entries
       │    └─ branch release/0.1.1, one commit: that, and version=0.1.1 in gradle.properties
       ├─ intellij/build:      ./gradlew check, verifyPlugin, signPlugin
       └─ release-flow/draft:  push release/0.1.1, draft naming tag v0.1.1, carrying
                               ij-git-reflog-0.1.1-signed.zip

[the draft is reviewed - the archive can be installed from it - and published]
  └─ GitHub creates tag v0.1.1, on the release commit
  └─ .github/workflows/release-publish.yml
       ├─ release-flow/merge-back:  version=0.1.2-SNAPSHOT, then main fast-forwarded onto release/0.1.1
       │                            and Build asked for over it
       ├─ intellij/publish:         the archive off the release, to the Marketplace upload API,
       │                            then asked back of the Marketplace and compared with what is served
       └─ release-flow/warn:        a warning on the release where that did not complete
```

Each step is an action of [loplex/release-ci](https://github.com/loplex/release-ci/blob/v0.2.0/README.md),
pinned to its tag `v0.2.0`. What each one checks and refuses is said in its `action.yml`; this section says only
what that means here.

The archive is built once. What the Marketplace receives is the file the draft was accepted with, not a second
build of the same sources.

### The version is read, never typed

`gradle.properties` names the version being worked on, marked `-SNAPSHOT`, and dropping that marker is what a
release is. So the number is decided in a commit, in a diff someone can review, rather than in a dispatch form
that leaves no trace.

release-ci's [`check-release`](https://github.com/loplex/release-ci/blob/v0.2.0/README.md#check-release) is
what reads it, and it refuses:

- a version that is not one;
- a version that does not come after the last one released to the same people, since an IDE offers an update by
  comparing versions and a release that does not outrank the last one is offered to nobody;
- a step other than the three SemVer permits - one part up by one, everything to its right zeroed. From `0.1.0`
  that is `0.1.1`, `0.2.0` or `1.0.0`, and nothing else.

A pre-release train stays inside one of those: `0.2.0-rc.1`, `0.2.0-rc.2`, `0.2.0`. The suffix is not decoration
- it names the Marketplace channel the version is published to, so `0.2.0-beta.1` is offered only to whoever
subscribed to `beta`, and it marks the GitHub release as a pre-release.

"The last one released to the same people" is what lets a stable hotfix go out while a train is open: `0.1.1`
goes to everyone and is measured against the last final release, not against `0.2.0-rc.1`, which only the
channel's subscribers ever see. The other side of that coin is that the hotfix cannot be tried on a channel
first - `0.1.1-rc.1` sorts below `0.2.0-rc.1` and is refused.

### What is still written by hand

The entries under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md), as the work is done. They are the release
notes on GitHub and the change notes on the Marketplace both, so a release made without them says nothing about
itself - which is why Prepare release refuses to run on an empty section rather than dating one.

A final release that closes a pre-release train is the one exception. `0.1.1` after `0.1.1-beta.1` takes the
entries of the pre-releases into its own section, so it goes through with nothing new under `[Unreleased]`.

Everything after that is mechanical and is done for you: the version, the changelog section and its date and
links, the branch, the commits, the tag, the bump, and moving `main`.

### Nothing reaches `main` until the release is accepted

The release commit goes onto `release/<version>`, not onto the default branch, and the draft is built from it.
A draft that is thrown away leaves nothing to undo: no commit to revert, and no tag to delete. What it does
leave - the branch and the draft - the next run for the same version replaces.

The tag is named by the draft but created by GitHub, at that commit, only when the draft is published. So every
tag in the repository names a release that someone accepted.

The release rules, `check` and the Plugin Verifier, which Build asks on every push to `main` and every pull
request, are asked again there, over the release commit itself. The release commit lives on a branch Build does not
see before the release is published, so nothing else ties the release to a run that was green.

Prepare release runs only when started on `main`. Started on another branch it would cut the release from that
branch, and the merge-back would then carry it onto `main` without anyone having reviewed it there.

### What the Marketplace is asked, rather than told

Publishing uploads the accepted archive over the Marketplace's own upload API, and then asks the Marketplace
for that version back and compares it with the file that was uploaded - by payload, since the two can never be
the same file: the Marketplace counter-signs what it serves.

The upload's own status is deliberately not what decides the job. What the release needs to be true is that the
Marketplace ends up serving the archive that was accepted, and the comparison asks exactly that. Every way the
upload can fail is then one the comparison already answers, and no wording JetBrains may change one day is
load-bearing.

A failed comparison is said on the release itself, as a warning above the release notes, because that is where
someone holding the archive will be looking. The same goes for an upload that never ran because a step before
it failed. A later run whose publish completes takes the warning back off.

### Caveat: rewriting the release branch orphans the tag

`v0.1.1` points at the release commit inside `release/0.1.1`, and that tag is the only thing that still says
which commit the archive was built and signed from. Anything that rewrites the commits around it leaves the tag
pointing at a commit no branch reaches - a squash merge, a rebase merge, GitHub's **Update with rebase**, a
force-push.

The `ancestry` rule asks whether every released tag is still reachable, on every push to `main` and every pull
request. It is asked as a property rather than forbidden one cause at a time, because the list of ways to rewrite
a branch is GitHub's to extend and not this project's to keep up with.

Worth turning the common cases off as well, so that the merge button cannot do it:

```bash
gh api -X PATCH repos/loplex/intellij-git-reflog \
  -F allow_squash_merge=false -F allow_rebase_merge=false -F allow_merge_commit=true
```

### Caveat: a released changelog section may never change

The `changelog` rule compares every released section against the tag that released it, on every push to `main`
and every pull request. It exists for one merge in particular.

Merging the release branch back is where a released section can quietly change. The release commit rewrote
`[Unreleased]` into `[0.1.1]`; work that landed on `main` in the meantime added entries further down the same
section. Those are different hunks, so Git merges them **cleanly** - and the new entries come out under
`[0.1.1]`, a text that has already been published as the release notes and the change notes.

Git merges lines and cannot see that a heading is a container, so there is no conflict to stop it. A check is
the only thing that will.

For the check to be worth anything it has to run over the tree that actually lands, so the merge-back merges
`main` into the release branch itself and asks `changelog` and `ancestry` of the result before it pushes. That is
deliberate rather than left to **Require branches to be up to date before merging**: a branch protection has to
be stricter than everyone who can bypass it, and a merge the workflow performs is simply there.

Where the merge would conflict, or its result is refused, nothing is pushed onto `main`: a pull request carries
the release instead, to be resolved where a conflict is resolved. Merge it with a merge commit, as its body says,
so that the tag stays reachable.

### Caveat: the first upload to the Marketplace has to be made by hand

The upload API updates a plugin that is already listed. It cannot create the listing: JetBrains require the
first version of a new plugin to be uploaded through the Marketplace's own **Upload plugin** form, and it goes
through their review before it appears. Only from the second version onwards does the workflow above do the
whole job. That has been done here: 0.1.0 was uploaded by hand, and the listing it created is what every
release after it updates.

A release like that is finished by running the job in `release-publish.yml` again once the version is approved,
within the 30 days GitHub lets a run be re-run. The re-run finds the release on `main` already and carries
nothing back a second time; the upload is tried again, and the version asked for again.

Carrying back and uploading do not wait on each other to succeed, for this reason among others. A Marketplace
upload that fails is no reason for `main` to go on disagreeing with a tag that exists.

### The secrets the workflows expect

| Secret                 | What it is                                                                          |
|------------------------|-------------------------------------------------------------------------------------|
| `PUBLISH_TOKEN`        | A Marketplace personal access token, from your profile page there. It is shown once |
| `CERTIFICATE_CHAIN`    | The signing certificate chain, in PEM                                               |
| `PRIVATE_KEY`          | The signing private key, in PEM                                                     |
| `PRIVATE_KEY_PASSWORD` | What the private key was encrypted with                                             |

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
