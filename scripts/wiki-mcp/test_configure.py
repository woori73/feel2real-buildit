import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('wiki_configure', Path(__file__).with_name('configure.py'))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class ConfigureTest(unittest.TestCase):
    def test_missing_key_is_created_with_private_permissions_and_existing_settings_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'env'
            original = b'OTHER_API_KEY=private-value\n# keep comment\nBUILDIT_MCP_API_KEY=\n'
            p.write_bytes(original)
            restart, verify = Mock(), Mock(return_value=['get_final_context'])
            with patch.object(ops, 'seal', return_value='ciphertext'):
                out = ops.configure(p, b'public', restart, verify)
            self.assertTrue(out['new_key'])
            self.assertNotIn(ops.environment_key(p.read_text()), str(out))
            self.assertTrue(p.read_text().startswith('OTHER_API_KEY=private-value\n# keep comment\n'))
            self.assertEqual(p.stat().st_mode & 0o777, 0o600)
            self.assertEqual(next(Path(d).glob('*.mcp-backup-*')).read_bytes(), original)
            restart.assert_called_once()

    def test_existing_key_is_not_rotated_or_restarted(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'env'
            original = 'OTHER=value\nBUILDIT_MCP_API_KEY="existing-private-key-123456789"\n'
            p.write_text(original)
            restart, verify = Mock(), Mock(return_value=[])
            with patch.object(ops, 'seal', return_value='ciphertext'):
                result = ops.configure(p, b'public', restart, verify)
            self.assertFalse(result['new_key'])
            self.assertEqual(p.read_text(), original)
            restart.assert_not_called()

    def test_failed_health_restores_exact_previous_environment(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'env'
            p.write_bytes(b'OTHER=keep-me\n')
            restart = Mock()
            with patch.object(ops, 'seal', return_value='ciphertext'):
                with self.assertRaises(ValueError):
                    ops.configure(p, b'public', restart, Mock(side_effect=ValueError('fail')))
            self.assertEqual(p.read_bytes(), b'OTHER=keep-me\n')
            self.assertEqual(restart.call_count, 2)

    def test_invalid_recipient_never_changes_environment(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'env'
            p.write_bytes(b'OTHER=keep-me\n')
            restart = Mock()
            with patch.object(ops, 'seal', side_effect=ValueError('invalid public key')):
                with self.assertRaises(ValueError):
                    ops.configure(p, b'invalid', restart, Mock())
            self.assertEqual(p.read_bytes(), b'OTHER=keep-me\n')
            restart.assert_not_called()

    def test_conflicting_keys_are_not_silently_rotated(self):
        with self.assertRaises(ValueError):
            ops.environment_key('BUILDIT_MCP_API_KEY=first-private-key-1234\nBUILDIT_MCP_API_KEY=second-private-key-5678\n')

    def test_json_and_sse_responses_are_understood(self):
        self.assertEqual(ops.rpc_result('{"result":{"tools":[]}}'), {'tools': []})
        self.assertEqual(ops.rpc_result('event: message\ndata: {"result":{"tools":[]}}\n\n'), {'tools': []})

    def test_key_can_only_be_decrypted_by_recipient(self):
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa, padding
        import base64
        private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        public = private.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        sealed = ops.seal('test-private-key-123456789', public)
        decrypted = private.decrypt(base64.b64decode(sealed), padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
        self.assertEqual(decrypted, b'test-private-key-123456789')


if __name__ == '__main__':
    unittest.main()
