"""Проверки файлов и отказов backup-скриптов без Docker и баз данных."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"


class BackupScriptsTest(unittest.TestCase):
    def run_backup(self, script: str, mode: str, root: Path) -> subprocess.CompletedProcess:
        bin_dir = root / "bin"
        bin_dir.mkdir(exist_ok=True)
        docker = bin_dir / "docker"
        docker.write_text(
            '#!/usr/bin/env bash\n'
            'printf "%s\\n" "$@" > "$ARGUMENTS_FILE"\n'
            'case "$FAKE_MODE" in\n'
            '  success) printf "test archive" ;;\n'
            '  failure) printf "partial archive"; exit 42 ;;\n'
            '  empty) exit 0 ;;\n'
            'esac\n'
        )
        docker.chmod(0o700)
        return subprocess.run(
            ["bash", str(SCRIPTS / script), str(root / "backup files")],
            cwd=root,
            env={**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}",
                 "FAKE_MODE": mode, "ARGUMENTS_FILE": str(root / "arguments"),
                 "MONGO_DB": "test_db"},
            capture_output=True, text=True, check=False,
        )

    def test_successful_archives_are_private_and_unique(self):
        for script, service, command, suffix in [
            ("backup_postgres.sh", "postgres", "pg_dump", ".dump"),
            ("backup_mongodb.sh", "mongodb", "mongodump", ".archive.gz"),
        ]:
            with self.subTest(script=script), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                paths = []
                for _ in range(2):
                    result = self.run_backup(script, "success", root)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    archive = Path(result.stdout.strip())
                    self.assertTrue(str(archive).endswith(suffix))
                    self.assertEqual(archive.read_bytes(), b"test archive")
                    self.assertEqual(archive.stat().st_mode & 0o777, 0o600)
                    paths.append(archive)
                self.assertNotEqual(paths[0], paths[1])
                self.assertFalse(list((root / "backup files").glob("*.partial")))
                args = (root / "arguments").read_text()
                self.assertEqual(Path(args.splitlines()[2]).resolve(), SCRIPTS.parent / "docker-compose.yml")
                self.assertIn(service, args)
                self.assertIn(command, args)
                self.assertIn("-T\n", args)
                if service == "mongodb":
                    self.assertIn("--db\ntest_db\n", args)

    def test_failed_or_empty_dump_is_not_published(self):
        for script in ["backup_postgres.sh", "backup_mongodb.sh"]:
            for mode in ["failure", "empty"]:
                with self.subTest(script=script, mode=mode), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    backup_dir = root / "backup files"
                    backup_dir.mkdir()
                    existing = backup_dir / "previous.dump"
                    existing.write_bytes(b"existing backup")
                    result = self.run_backup(script, mode, root)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(result.stdout, "")
                    self.assertEqual(list(backup_dir.iterdir()), [existing])
                    self.assertEqual(existing.read_bytes(), b"existing backup")


if __name__ == "__main__":
    unittest.main()
