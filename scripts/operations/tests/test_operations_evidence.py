import base64
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.request

import yaml


ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("archive_release", ROOT / "scripts/deploy/archive_release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def workflow(name):
    return yaml.load((ROOT / f".github/workflows/{name}.yml").read_text(), Loader=yaml.BaseLoader)


class ReleaseArchiveTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        folder = Path(self.tmp.name)
        self.files = [folder / n for n in ("jar", "wiki", "script")]
        for file in self.files:
            file.write_bytes(file.name.encode())
        self.receipt = folder / "receipt.json"
        self.calls = []

    def aws(self, args, **kwargs):
        self.assertEqual(args[:3], ["aws", "s3api", "put-object"])
        self.assertEqual(args[args.index("--if-none-match") + 1], "*")
        self.assertEqual(args[args.index("--server-side-encryption") + 1], "AES256")
        checksum = base64.b64encode(hashlib.sha256(Path(args[args.index("--body") + 1]).read_bytes()).digest()).decode()
        self.assertEqual(args[args.index("--checksum-sha256") + 1], checksum)
        key = args[args.index("--key") + 1]
        self.calls.append(key)
        return subprocess.CompletedProcess(args, 0, stdout=checksum + "\n")

    def run_archive(self, **overrides):
        args = dict(bucket="f2r-deploy-425805163960", sha="a" * 40, run_id="123", attempt="2",
                    jar=self.files[0], wiki=self.files[1], deploy_script=self.files[2], receipt=self.receipt)
        args.update(overrides)
        return release.archive(**args)

    def test_complete_manifest_is_last_and_only_payloads_are_uploaded(self):
        with patch.object(release.subprocess, "run", side_effect=self.aws):
            data = self.run_archive()
        self.assertEqual([Path(k).name for k in self.calls], ["app.jar", "buildit-wiki-backend.tgz", "deploy-buildit.sh", "manifest.json"])
        self.assertTrue(all(k.startswith("releases/" + "a" * 40 + "/123-2/") for k in self.calls))
        self.assertEqual(json.loads(self.receipt.read_text()), data)
        self.assertTrue(data["complete"])

    def test_upload_failure_leaves_no_completion_marker_or_receipt(self):
        def fail_second(args, **kwargs):
            if len(self.calls) == 1:
                raise subprocess.CalledProcessError(1, args)
            return self.aws(args, **kwargs)
        with patch.object(release.subprocess, "run", side_effect=fail_second):
            with self.assertRaises(subprocess.CalledProcessError):
                self.run_archive()
        self.assertFalse(self.receipt.exists())
        self.assertFalse(any(k.endswith("manifest.json") for k in self.calls))

    def test_wrong_s3_checksum_cannot_create_a_complete_release(self):
        with patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="wrong")):
            with self.assertRaises(RuntimeError):
                self.run_archive()
        self.assertFalse(self.receipt.exists())

    def test_invalid_source_is_rejected_before_any_remote_write(self):
        self.files[1].unlink()
        with patch.object(release.subprocess, "run") as aws:
            with self.assertRaises(ValueError):
                self.run_archive()
            aws.assert_not_called()

    def test_prefix_injection_and_wrong_bucket_are_rejected(self):
        for values in ({"sha": "../main"}, {"run_id": "1/../../app.jar"}, {"attempt": "0"}, {"bucket": "other-bucket"}):
            with patch.object(release.subprocess, "run") as aws:
                with self.assertRaises(ValueError):
                    self.run_archive(**values)
                aws.assert_not_called()

    def test_existing_archive_is_never_overwritten(self):
        with patch.object(release.subprocess, "run", side_effect=subprocess.CalledProcessError(1, [], stderr="PreconditionFailed")):
            with self.assertRaises(subprocess.CalledProcessError):
                self.run_archive()
        self.assertFalse(self.receipt.exists())


class RetentionTest(unittest.TestCase):
    def execute_cleanup(self, dry_run="true", days="90"):
        conf = workflow("artifact-cleanup")
        text = conf["jobs"]["cleanup"]["steps"][1]["run"]
        code = text.split("<<'PYEOF'\n", 1)[1].rsplit("PYEOF", 1)[0]
        now = datetime.now(timezone.utc)
        artifacts = [dict(id=i, name=name, created_at=(now - timedelta(days=age)).isoformat(), size_in_bytes=10)
                     for i, name, age in [(1, "deploy-gate-test-report", 8), (2, "build-check-test-report", 89),
                                          (3, "mysql-test-report", 91), (4, "app-jar-aaaaaaaa", 200),
                                          (5, "app-jar-bbbbbbbb", 91), (6, "f2r-transfer-cipher", 2)]]
        requests = []

        def open_api(req, **kwargs):
            requests.append((req.method, req.full_url))
            if req.method == "DELETE":
                return io.BytesIO(b"")
            if "/actions/artifacts?" in req.full_url:
                data = {"artifacts": artifacts}
            elif "/workflows/deploy-ec2.yml/runs?" in req.full_url:
                data = {"workflow_runs": [{"head_sha": "a" * 40, "conclusion": "success"}]}
            elif "/actions/runs?" in req.full_url:
                data = {"workflow_runs": []}
            else:
                raise AssertionError(req.full_url)
            return io.BytesIO(json.dumps(data).encode())

        scope = {}
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {
            "REPO": "fixture/repo", "GH_TOKEN": "test-only", "DRY_RUN": dry_run,
            "REPORT_RETENTION_DAYS": days, "GITHUB_STEP_SUMMARY": str(Path(folder) / "summary")
        }), patch.object(urllib.request, "urlopen", side_effect=open_api):
            exec(compile(code, "artifact-cleanup", "exec"), scope)
        return scope, requests

    def test_scheduled_cleanup_defaults_to_no_deletion(self):
        self.assertEqual(workflow("artifact-cleanup")["env"]["DRY_RUN_DEFAULT"], "true")
        scope, requests = self.execute_cleanup()
        self.assertEqual([a["id"] for a in scope["delete"]], [3, 5])
        self.assertFalse(any(m == "DELETE" for m, _ in requests))

    def test_manual_delete_retains_90_days_and_recent_deployed_sha(self):
        _, requests = self.execute_cleanup(dry_run="false", days="7")
        deleted = [u.rsplit("/", 1)[-1] for m, u in requests if m == "DELETE"]
        self.assertEqual(deleted, ["3", "5"])

    def test_diagnostics_and_deploy_remain_manual_with_no_static_credentials(self):
        for name in ("prod-readonly-status", "prod-diagnostic", "deploy-ec2"):
            conf = workflow(name)
            self.assertEqual(list(conf["on"]), ["workflow_dispatch"])
            self.assertEqual(conf["permissions"]["id-token"], "write")
            for job in conf["jobs"].values():
                for step in job.get("steps", []):
                    if step.get("uses", "").startswith("aws-actions/configure-aws-credentials"):
                        self.assertEqual(step["with"]["role-duration-seconds"], "3600")
                        self.assertNotIn("aws-access-key-id", step["with"])
                        self.assertNotIn("aws-secret-access-key", step["with"])


if __name__ == "__main__":
    unittest.main()
