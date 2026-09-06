import json
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import ai_router  # noqa: E402


class QuestionWikiFreeRoutingTest(unittest.TestCase):
    MARKER = ('QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: '
              '{"questionWiki":true,"questionWikiId":31,"grade":2,"examYear":2026,'
              '"questionNo":31,"type":"vocabulary"}')

    def _response(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({
            'model': ai_router.QUESTION_WIKI_FREE_MODELS[0],
            'choices': [{'message': {'content': '{"ok":true}'}}],
        }).encode()
        return response

    def test_question_wiki_uses_only_explicit_free_models_in_one_request(self):
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}, 'required': ['ok']}
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('urllib.request.urlopen', return_value=self._response()) as call:
            ai_router.generate('openrouter-free', 'system', 'variant\n' + self.MARKER, schema=schema)
        self.assertEqual(1, call.call_count)
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(ai_router.QUESTION_WIKI_FREE_MODELS[0], body['model'])
        self.assertEqual(list(ai_router.QUESTION_WIKI_FREE_MODELS[1:]), body['models'])
        self.assertEqual('json_schema', body['response_format']['type'])
        self.assertNotIn(ai_router.FREE_ROUTER, [body['model'], *body['models']])
        self.assertTrue(all(model.endswith(':free') for model in [body['model'], *body['models']]))
        forbidden = ('qwen/', 'google/gemini', 'anthropic/', 'openai/gpt-5', 'openai/gpt-6')
        self.assertFalse(any(model.startswith(forbidden) for model in [body['model'], *body['models']]))

    def test_non_question_wiki_openrouter_free_keeps_existing_router(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('urllib.request.urlopen', return_value=self._response()) as call:
            ai_router.generate('openrouter-free', 'system', 'ordinary free request')
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(ai_router.FREE_ROUTER, body['model'])
        self.assertNotIn('models', body)

    def test_question_wiki_allowlist_is_hard_zero_cost_slugs(self):
        self.assertGreaterEqual(len(ai_router.QUESTION_WIKI_FREE_MODELS), 2)
        self.assertEqual(len(set(ai_router.QUESTION_WIKI_FREE_MODELS)), len(ai_router.QUESTION_WIKI_FREE_MODELS))
        self.assertTrue(all(model.endswith(':free') for model in ai_router.QUESTION_WIKI_FREE_MODELS))


if __name__ == '__main__':
    unittest.main()
