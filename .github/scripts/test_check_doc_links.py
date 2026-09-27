#!/usr/bin/env python3
"""The anchors check-doc-links.py reads out of a document, exercised without a document set to check.

Run with `python3 -m unittest discover -s .github/scripts`, which is what CI does.
"""

import unittest

import importlib.util
from pathlib import Path

specification = importlib.util.spec_from_file_location(
    "check_doc_links", Path(__file__).resolve().parent / "check-doc-links.py"
)
check_doc_links = importlib.util.module_from_spec(specification)
specification.loader.exec_module(check_doc_links)

anchors = check_doc_links.anchors
links = check_doc_links.links


class Anchors(unittest.TestCase):

    def test_inline_code_in_a_heading_is_part_of_its_anchor(self):
        text = "### A `refs/` pattern sees every ref, not only branches\n"

        self.assertEqual({"a-refs-pattern-sees-every-ref-not-only-branches"}, anchors(text))

    def test_a_heading_inside_a_fence_is_no_heading(self):
        text = "## Real\n\n```bash\n# not a heading\n```\n"

        self.assertEqual({"real"}, anchors(text))

    def test_a_repeated_heading_is_numbered(self):
        text = "## Caveat\n\n## Caveat\n"

        self.assertEqual({"caveat", "caveat-1"}, anchors(text))


class Links(unittest.TestCase):

    def test_a_link_inside_inline_code_is_no_link(self):
        text = "See [usage](docs/usage.md), not `[this](nowhere.md)`.\n"

        self.assertEqual([(1, "docs/usage.md")], list(links(text)))


if __name__ == "__main__":
    unittest.main()
