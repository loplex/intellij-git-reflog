#!/usr/bin/env python3
"""Resolves every link the documentation makes to something in the repository.

The documents point at each other, at their own sections and at source files, and a renamed heading or a moved
file breaks those pointers without breaking anything a build would notice. This walks them instead:

  - a relative target must exist in the working tree;
  - a `#fragment` must match a heading in the file it points into, slugged the way GitHub slugs headings.

What it does not see: external URLs, which fail for reasons that have nothing to do with the commit; prose that
refers to a section by name rather than by link, which is why such references are worth writing as links; and
whether a link that resolves points at the right thing.

Exits non-zero on the first document that has a broken link, having reported all of them.
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# ``` or ~~~ fences, and the inline code spans between backticks. Blanked before anything is read out of a
# document, so that a `#` comment in a shell sample is not taken for a heading and a link in an example is not
# taken for a link.
FENCE = re.compile(r"^(?P<fence>```+|~~~+).*?^(?P=fence)[^\S\n]*$", re.MULTILINE | re.DOTALL)
INLINE_CODE = re.compile(r"`[^`\n]*`")

HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*$", re.MULTILINE)
INLINE_LINK = re.compile(r"!?\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
REFERENCE_LINK = re.compile(r"^\[[^\]]+\]:\s*<?(\S+?)>?\s*(?:\"[^\"]*\")?$", re.MULTILINE)

EXTERNAL = re.compile(r"^(?:[a-z][a-z0-9+.-]*:|//)", re.IGNORECASE)


def blank_code(text: str) -> str:
    """Replaces code with spaces, keeping every other character at the offset it had."""

    def blanked(match: re.Match) -> str:
        return re.sub(r"[^\n]", " ", match.group(0))

    return INLINE_CODE.sub(blanked, FENCE.sub(blanked, text))


def slug(heading: str) -> str:
    """The anchor GitHub gives a heading: its text, stripped of formatting and of everything but word
    characters, spaces and hyphens, lowercased, with the spaces turned into hyphens."""

    text = re.sub(r"`([^`]*)`", r"\1", heading)  # code spans keep their contents
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)  # links keep their text
    text = re.sub(r"[*_~]", "", text)  # emphasis marks are not part of the anchor
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    return text.strip().lower().replace(" ", "-")


def anchors(text: str) -> set[str]:
    """Every anchor a document offers. A heading repeating one already taken gets -1, -2 and so on, as GitHub
    numbers them."""

    seen: dict[str, int] = {}
    found = set()
    for _, heading in HEADING.findall(blank_code(text)):
        base = slug(heading)
        count = seen.get(base, 0)
        seen[base] = count + 1
        found.add(base if count == 0 else f"{base}-{count}")
    return found


def links(text: str):
    """Every link target in a document, with the line it sits on."""

    stripped = blank_code(text)
    for pattern in (INLINE_LINK, REFERENCE_LINK):
        for match in pattern.finditer(stripped):
            yield stripped.count("\n", 0, match.start()) + 1, match.group(1)


def documents() -> list[Path]:
    """The documents the repository tracks, or the ones named on the command line."""

    if sys.argv[1:]:
        return [Path(name).resolve() for name in sys.argv[1:]]
    listed = subprocess.run(
        ["git", "ls-files", "-z", "*.md"], cwd=REPO, capture_output=True, text=True, check=True
    )
    return [REPO / name for name in listed.stdout.split("\0") if name]


def main() -> int:
    cache: dict[Path, set[str]] = {}
    problems = []

    for document in documents():
        text = document.read_text(encoding="utf-8")
        cache[document] = anchors(text)

        for line, target in links(text):
            where = f"{document.relative_to(REPO) if document.is_relative_to(REPO) else document}:{line}"
            if EXTERNAL.match(target):
                continue

            path, _, fragment = target.partition("#")
            resolved = document.parent / path if path else document

            if not resolved.exists():
                problems.append(f"{where}: no such file: {target}")
                continue
            if not fragment:
                continue
            if resolved.suffix != ".md":
                problems.append(f"{where}: {path} is not a document and has no anchors: {target}")
                continue

            resolved = resolved.resolve()
            if resolved not in cache:
                cache[resolved] = anchors(resolved.read_text(encoding="utf-8"))
            if fragment not in cache[resolved]:
                problems.append(f"{where}: no heading in {path or document.name} anchors #{fragment}")

    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(f"\n{len(problems)} broken link(s)", file=sys.stderr)
        return 1

    print(f"Every link in {len(documents())} documents resolves.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
