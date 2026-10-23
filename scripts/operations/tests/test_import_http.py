"""Opt-in canonical #876 common API path against isolated MySQL; never production."""
import importlib.util
import json
import os
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]

@unittest.skipUnless(os.getenv('F2R_STUDIO_IMPORT_HTTP_TEST') == 'true', 'requires isolated local MySQL/app fixture')
class CanonicalImportHttpTest(unittest.TestCase):
    def test_canonical_register_and_noop_preserve_536_sources_and_approvals(self):
        fixture = json.loads(pathlib.Path(os.getenv('F2R_STUDIO_HTTP_FIXTURE', '/tmp/f2r-assets-browser-fixture.json')).read_text())
        self.assertRegex(fixture['jdbcUrl'], r'^jdbc:mysql://127\.0\.0\.1:3318/f2r_assets_[a-f0-9]{12}\?')
        spec = importlib.util.spec_from_file_location('canonical_http_import', ROOT / 'scripts/ai-agent-studio/import-production.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        client = module.Client('http://127.0.0.1:18090', fixture['adminCookie'], proxies={}, retries=1)
        if not client.json('project').get('initialized'):
            client.json('project', b'{}')  # Test fixture only; the production CLI never initializes projects.
        unique = module.verify_source(ROOT / 'docs/ai-agent-production', json.loads((ROOT / module.CATALOG).read_bytes()))
        before = module.revision_approvals(client)
        registered = client.json('source-import')['registeredSourcePaths']
        with tempfile.TemporaryDirectory() as temp:
            state = pathlib.Path(temp) / 'state.json'
            for number in range(2):
                result = module.stage_and_register(client, ROOT / 'docs/ai-agent-production', unique, state, True)
                self.assertEqual('registered' if number == 0 and registered == 0 else 'already-registered', result['action'])
                self.assertEqual(536, client.json('source-import')['registeredSourcePaths'])
                after = module.revision_approvals(client)
                self.assertTrue(all(after.get(k) == v for k, v in before.items()))
                self.assertEqual(5, len([k for k in after if k.startswith('character-approved-')]))
                if result['action'] == 'registered':
                    self.assertEqual(316, result['storedOriginalsVerified'])
                    self.assertEqual(678, result['registration']['createdRevisions'])

if __name__ == '__main__':
    unittest.main()
