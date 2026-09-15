---
name: run-reflog-tab
description: Launch this plugin's sandbox IDE and drive the Reflog tab to see a change working. Use whenever the task is to run, start, open, or screenshot the plugin, to check a change in the real IDE rather than only in tests, or to verify anything about how the tab looks or behaves - greying, drawing, popups, Load More, the file pane's messages. Also use when a test cannot reach what needs checking, since much of this tab is only observable on screen.
---

# Running the Reflog tab

Most of this tab's faults have been found by looking at it, not by tests: a separator with no height, a popup
reopened by its own dismissal, a label too wide to be drawn at all. Tests cannot reach any of that, so getting
the tab on a screen is a first-class task rather than a fallback.

## Build the fixture repository first

The tab shows a reflog, so it needs a repository with an interesting one. `tools/reflog-playground.sh` builds
one holding every state the tab has a case for:

```bash
bash tools/reflog-playground.sh /tmp/reflog-playground
```

It takes a few minutes, most of it the 1,010 filler commits that push the older entries past the first page.
What it leaves behind is worth knowing, because it is what most checks need:

- **The newest four HEAD entries all point at one commit** - two checkouts between branches standing on the same
  commit, and the two resets `git stash` makes internally. This is the "a movement that stayed put" case.
- **1,034 entries**, so Load More appears and the count has all three of its states to say.
- **Two stash entries**, a stack of unrelated work, which the timeline comparisons must refuse.
- **A branch that never rejoins master**, which is the case merging diffs must refuse.
- **One file edited but not committed**, so the working-tree comparison has something to show.

## Launch on a virtual display

```bash
Xvfb :99 -screen 0 1920x1200x24 &
DISPLAY=:99 ./gradlew runIde --args="/tmp/reflog-playground"
```

`runIde` takes the project to open as a program argument. Without one the sandbox opens empty and the tab has no
repository to read, which looks like the plugin failing.

**Use Xvfb even when a real desktop is present.** On a Wayland session, capturing the desktop returns a frame of
solid black - `import -window root` fails outright and `scrot` writes black - so you cannot see what you are
driving. On Xvfb both work. Run the IDE in the background and wait for a window to appear; it takes about a
minute.

A trust dialog comes up before anything else and has to be dismissed.

## Driving it

There is no window manager on Xvfb, which shapes what works:

- `xdotool windowactivate` fails with "your windowmanager claims not to support _NET_ACTIVE_WINDOW". Keys sent to
  the display reach whatever holds focus, which is usually what you just opened.
- **Clicks inside popups are unreliable; the keyboard is not.** If a click on a menu item does nothing, arrow to
  it and press Enter rather than clicking harder.
- `xdotool windowsize <id> 1920 1200` does work, and is worth doing - the sandbox opens in a small window.

Screenshot, look at it, then act. Do not chain clicks blind: the file pane holds its read back 150 ms after the
selection moves, and git takes its own time on a repository this size.

```bash
DISPLAY=:99 import -window root shot.png          # whole screen
DISPLAY=:99 import -window root -crop WxH+X+Y out.png   # one pane, easier to read
```

Cropping to the pane under test is worth the arithmetic - a 1920x1200 screenshot of a toolbar label is mostly
empty space, and the label is what you came for.

## The ref popup opens where you left the tab

It opens on the ref being shown, with a tick against it, so arrow keys count from there and Enter straight after
opening confirms what is already on screen. Five Downs from `HEAD` reaches `stash`; five Downs from `stash` wrap
round the end of the list rather than landing five below `HEAD`.

Worth knowing because it was not always so - the popup used to open on `HEAD` whatever the tab was showing, and
a note counting arrow keys from `HEAD` is a note written before that was fixed.

## Keep the machine to yourself

A second Gradle build running beside this one can push the Kotlin compiler into
`java.lang.OutOfMemoryError: GC overhead limit exceeded`, which prints on the same `e: ` lines as a compile
error and reads exactly like a real failure. If something fails, the first question is whether it reproduces
with nothing else running - not what in the code is wrong.

## When finished

```bash
pkill -f "gradle-wrapper.jar runIde"
pkill -f "Xvfb :99"
```

Leave any other `Xvfb` alone; a desktop session has one of its own.
