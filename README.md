# Git Reflog

*An IntelliJ IDEA plugin that puts `git reflog` in the Git tool window.*

`git reflog` is the record of every movement of `HEAD` - commits, checkouts, resets, rebases, amends.\
It is also the only place where a commit that no branch points at any more can still be found.\
IntelliJ IDEA has no UI for it, so recovering work after a bad reset means leaving the IDE for a terminal.

This plugin adds a **Reflog** tab to the Git tool window, between the Log and the Console tab.

## What the tab gives you

- Every reflog entry of every ref the repository has logged - `HEAD`, branches, remote-tracking branches, the
  stash.
- The commit each movement landed on - its hash, its message and its author - beside the movement itself.
- The files that entry's commit changed, and the diff of one of them, in the two panes the Log has.
- Four readings of what those files are compared against, one of them being what changed *under* you across a
  checkout or a reset - which no diff against a parent can show.
- **New Branch from Here**, which is what rescues work rather than only looking at it: a branch keeps the commit
  alive past the expiry of the reflog.
- **Checkout Revision** and **Reset Current Branch to Here**, carried out by git4idea itself, so they behave as
  they do in the Log.
- Filtering by text and by kind of operation, over the entries already read, so it is instant.

## Installing it

The plugin is not on the JetBrains Marketplace yet, so it is installed from a build of its own:

```bash
./gradlew buildPlugin
```

That leaves a zip under `build/distributions/`, which **Settings | Plugins | ⚙ | Install Plugin from Disk...**
takes.

It is built against IntelliJ IDEA 2025.3.5 and needs Git to be an active VCS in the project. It declares no
upper bound on the IDE version, so a later IDE is allowed to run it rather than refused; 2025.3 is the earliest
it will install into.

## Where the rest is

- [Driving the Reflog tab](docs/usage.md) - the columns, the toolbar, **Compare**, the two filters, the actions on
  an entry, and reading past the first page.
- [Why the tab behaves as it does](docs/design.md) - the reasoning behind it, and the platform pieces it is
  built out of.
- [Working on the plugin](CONTRIBUTING.md) - building it, running it from sources, and finding your way around them.
- [Changelog](CHANGELOG.md) - what each version changed.

Licensed under the [Apache License 2.0](LICENSE).
