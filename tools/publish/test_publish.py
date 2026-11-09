"""Offline regression for the shared Modrinth/CurseForge publication map."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import ANY, patch

import publish


class PublisherTest(unittest.TestCase):
    def run_publisher(self, modules, arguments):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "mod.jar").write_bytes(b"test artifact")
            (root / "CHANGELOG.md").write_text("## 1.1.0\n\nRelease notes.\n")
            mapping = root / "projects.json"
            mapping.write_text(json.dumps({"modules": modules}))
            output = io.StringIO()
            with patch.object(publish, "ROOT", root), patch.object(publish, "MAP", mapping), \
                    patch("sys.argv", ["publish.py", *arguments]), \
                    patch.object(publish, "published_versions", return_value=set()) as lookup, \
                    patch.object(publish, "upload") as upload, \
                    contextlib.redirect_stdout(output):
                status = publish.main()
            upload.assert_not_called()
            return status, output.getvalue(), lookup

    @staticmethod
    def module(addon, project):
        return {"addon": addon, "project_id": project, "publish": True,
                "name": addon + " 1.1.0", "version_number": "1.1.0", "loader": "fabric",
                "game_version": "1.21.1", "file": "mod.jar", "changelog": "CHANGELOG.md",
                "dependencies": []}

    def test_curseforge_only_selection_never_contacts_modrinth_even_with_upload(self):
        status, output, lookup = self.run_publisher([self.module("cf_only", None)], ["--upload"])
        self.assertEqual(status, 0)
        self.assertIn("no Modrinth project", output)
        self.assertIn("publish_tui.py", output)
        lookup.assert_not_called()

    def test_mixed_map_keeps_modrinth_dry_run_and_skips_curseforge_only(self):
        status, output, lookup = self.run_publisher(
            [self.module("cf_only", None), self.module("both_sites", "mr-id")], [])
        self.assertEqual(status, 0)
        self.assertIn("to upload: 1", output)
        self.assertIn("problems: 0", output)
        self.assertIn("dry run: nothing was sent", output)
        lookup.assert_called_once_with("mr-id", ANY)


if __name__ == "__main__":
    unittest.main()
