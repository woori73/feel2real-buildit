"""Opt-in real HTTP/MySQL check; never connects to a production origin."""
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import re
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[3]


@unittest.skipUnless(os.getenv('F2R_STUDIO_IMPORT_HTTP_TEST') == 'true', 'requires isolated local MySQL/app fixture')
class ProductionModeHttpTest(unittest.TestCase):
    def test_production_branch_twice_preserves_all_536_sources_and_approvals(self):
        fixture = json.loads(pathlib.Path(os.getenv('F2R_STUDIO_HTTP_FIXTURE', '/tmp/f2r-assets-browser-fixture.json')).read_text())
        self.assertRegex(fixture['jdbcUrl'], r'^jdbc:mysql://127\.0\.0\.1:3318/f2r_assets_[a-f0-9]{12}\?')
        spec = importlib.util.spec_from_file_location('http_import', ROOT / 'scripts/ai-agent-studio/import-production.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        local = 'http://127.0.0.1:18090'
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            auth = root / 'auth.json'
            auth.write_text(json.dumps({'adminCookie': fixture['adminCookie']}))
            auth.chmod(0o600)
            approval = root / 'approval.json'
            approval.write_text(json.dumps(dict(approved=True, action='studio-source-import', account='425805163960',
                origin=local, projectId=module.PROJECT, sourceCommit=module.PIN,
                approvedBy='ISOLATED-TEST-ONLY', reference='NOT-A-PRODUCTION-APPROVAL')))
            for number in range(2):
                receipt = root / ('receipt-' + str(number) + '.json')
                argv = ['import-production.py', '--production', '--apply', '--initialize-project', '--source', str(ROOT / 'docs/ai-agent-production'),
                        '--auth-file', str(auth), '--approval-file', str(approval), '--receipt', str(receipt)]
                # A test-only monkeypatch exercises the production policy branch on the hardcoded
                # loopback fixture. The public CLI has no override for its HTTPS allowlist.
                with patch.object(module, 'PRODUCTION_ORIGIN', local), patch.object(sys, 'argv', argv), \
                     contextlib.redirect_stdout(io.StringIO()):
                    module.main()
                result = json.loads(receipt.read_text())
                self.assertEqual('applied-production', result['mode'])
                self.assertEqual(536, result['registrationVerified']['registeredSourcePaths'])
                self.assertEqual(316, result['storedOriginalsVerified'])
                self.assertEqual(int(os.getenv('F2R_EXPECT_CREATED_FIRST', '0')) if number == 0 else 0,
                                 result['registration']['createdRevisions'])
                self.assertTrue(result['existingApprovalsPreserved'])
                self.assertEqual(5, len(result['approvedOriginals']))


if __name__ == '__main__':
    unittest.main()
