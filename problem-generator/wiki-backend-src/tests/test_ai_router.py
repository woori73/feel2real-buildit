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

    def test_missing_openrouter_key_is_unavailable_and_auto_uses_gemini(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': ''}, clear=False), patch.object(ai_router.pl, 'call_gemini_text', return_value='Gemini fallback'):
            self.assertFalse(ai_router.availability()['providers']['ox-alpha']['available'])
            result = ai_router.generate('auto', 'system', 'hello')
        self.assertEqual(('gemini', 'Gemini fallback'), (result.provider, result.text))

    def test_explicit_ox_without_key_gracefully_falls_back_to_gemini(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': ''}, clear=False), patch.object(ai_router.pl, 'call_gemini_text', return_value='safe fallback'):
            result = ai_router.generate('ox-alpha', 'system', 'hello')
        self.assertEqual('gemini', result.provider)

    def test_ab_evaluation_save_and_read(self):
        created = main.save_ab_evaluation(main.AbEvaluationCreate(school='신구중', grade='중2', exam='중간', twin_id=1, question_type='어법', gemini_model='flash', gemini_result_id='g1', ox_model='stealth/ox-alpha', ox_result_id='o1', choice='ox-alpha'))
        rows = main.list_ab_evaluations()
        self.assertEqual(created['id'], rows[0]['id'])
        self.assertEqual(('gemini', 'ox-alpha', 'ox-alpha'), (rows[0]['gemini_provider'], rows[0]['ox_provider'], rows[0]['choice']))


if __name__ == '__main__':
    unittest.main()
