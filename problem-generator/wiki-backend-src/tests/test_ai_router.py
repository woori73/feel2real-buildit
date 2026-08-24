import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-router-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()
import ai_router  # noqa: E402
import main  # noqa: E402


class AiRouterTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'router.db')
        main.init_db()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_openrouter_mock_returns_assistant_text(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'model': 'stealth/ox-alpha', 'choices': [{'message': {'content': 'Ox 응답'}}]}).encode()
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), patch('urllib.request.urlopen', return_value=response):
            result = ai_router.generate('ox-alpha', 'system', 'hello')
        self.assertEqual(('ox-alpha', 'stealth/ox-alpha', 'Ox 응답', 'server'), (result.provider, result.model, result.text, result.key_source))

    def test_request_openrouter_key_precedes_server_and_is_not_returned(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'model': 'stealth/ox-alpha', 'choices': [{'message': {'content': 'safe'}}]}).encode()
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-secret'}), patch('urllib.request.urlopen', return_value=response) as call:
            result = ai_router.generate('ox-alpha', 'system', 'hello', openrouter_key='browser-secret')
        self.assertEqual('client', result.key_source)
        self.assertIn('browser-secret', call.call_args.args[0].headers['Authorization'])
        self.assertNotIn('browser-secret', repr(result))
        self.assertNotIn('server-secret', repr(result))

    def test_provider_availability_never_returns_secret_values(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'do-not-return', 'GEMINI_API_KEY': 'also-secret'}):
            body = json.dumps(main.ai_providers())
        self.assertNotIn('do-not-return', body)
        self.assertNotIn('also-secret', body)

    def test_free_models_use_pricing_metadata_and_return_no_secret(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'data': [
            {'id': 'vendor/zero', 'name': 'Zero', 'pricing': {'prompt': '0', 'completion': '0'}, 'context_length': 131072,
             'architecture': {'output_modalities': ['text']}, 'supported_parameters': ['response_format']},
            {'id': 'vendor/paid', 'pricing': {'prompt': '0.1', 'completion': '0'}},
            {'id': 'vendor/tagged:free', 'pricing': {'prompt': '1', 'completion': '1'}, 'architecture': {'output_modalities': ['text']}},
        ]}).encode()
        ai_router._models_cache = (0.0, [])
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'never-return'}), patch('urllib.request.urlopen', return_value=response):
            body = json.dumps(main.openrouter_models())
        self.assertIn('vendor/zero', body)
        self.assertIn('vendor/tagged:free', body)
        self.assertNotIn('vendor/paid', body)
        self.assertNotIn('never-return', body)

    def test_explicit_free_model_does_not_fallback(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': 'router-key'}), patch('urllib.request.urlopen', side_effect=TimeoutError()), patch.object(ai_router.pl, 'call_gemini_text') as gemini:
            with self.assertRaises(ai_router.ProviderError):
                ai_router.generate('openrouter:vendor/model:free', 'system', 'hello')
        gemini.assert_not_called()

    def test_missing_openrouter_key_is_unavailable_and_auto_uses_gemini(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': ''}, clear=False), patch.object(ai_router.pl, 'call_gemini_text', return_value='Gemini fallback'):
            self.assertFalse(ai_router.availability()['providers']['ox-alpha']['available'])
            result = ai_router.generate('auto', 'system', 'hello')
        self.assertEqual(('gemini', 'Gemini fallback'), (result.provider, result.text))

    def test_explicit_ox_without_key_never_falls_back_to_gemini(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': ''}, clear=False), patch.object(ai_router.pl, 'call_gemini_text', return_value='safe fallback'):
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('ox-alpha', 'system', 'hello')
        self.assertEqual('ox-alpha', raised.exception.provider)

    def test_openrouter_timeout_is_normalized_without_html_or_secret(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'never-print-me'}), patch('urllib.request.urlopen', side_effect=TimeoutError()):
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('ox-alpha', 'system', 'hello')
        self.assertEqual(504, raised.exception.status)
        self.assertIn('응답 시간이 초과', str(raised.exception))
        self.assertNotIn('never-print-me', str(raised.exception))

    def test_openrouter_uses_configured_model_and_bounded_timeout(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'choices': [{'message': {'content': 'safe'}}]}).encode()
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'key', 'OX_ALPHA_MODEL': 'stealth/ox-alpha', 'AI_PROVIDER_TIMEOUT_SECONDS': '90'}), patch('urllib.request.urlopen', return_value=response) as call:
            result = ai_router.generate('ox-alpha', 'system', 'hello')
        request = call.call_args.args[0]
        self.assertEqual('stealth/ox-alpha', json.loads(request.data)['model'])
        self.assertEqual(90.0, call.call_args.kwargs['timeout'])
        self.assertEqual('stealth/ox-alpha', result.model)

    def test_ab_evaluation_save_and_read(self):
        created = main.save_ab_evaluation(main.AbEvaluationCreate(school='신구중', grade='중2', exam='중간', twin_id=1, question_type='어법', gemini_model='flash', gemini_result_id='g1', ox_model='stealth/ox-alpha', ox_result_id='o1', choice='ox-alpha'))
        rows = main.list_ab_evaluations()
        self.assertEqual(created['id'], rows[0]['id'])
        self.assertEqual(('gemini', 'ox-alpha', 'ox-alpha'), (rows[0]['gemini_provider'], rows[0]['ox_provider'], rows[0]['choice']))


if __name__ == '__main__':
    unittest.main()


