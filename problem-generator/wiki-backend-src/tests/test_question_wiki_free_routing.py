import json
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import ai_router  # noqa: E402


class QuestionWikiQwenRoutingTest(unittest.TestCase):
    MARKER = ('QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: '
              '{"questionWiki":true,"questionWikiId":31,"grade":2,"examYear":2026,'
              '"questionNo":31,"type":"vocabulary"}')

    def _response(self, model=None):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({
            'model': model or ai_router.QWEN_MODEL,
            'choices': [{'message': {'content': '{"ok":true}'}}],
        }).encode()
        return response

    def test_question_wiki_uses_qwen37_with_throughput_routing_and_fallbacks(self):
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}, 'required': ['ok']}
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('private_reference_bank.output_issues', return_value=[]), \
             patch('urllib.request.urlopen', return_value=self._response()) as call:
            ai_router.generate('openrouter-free', 'system', 'variant\n' + self.MARKER, schema=schema)
        self.assertEqual(1, call.call_count)
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual('qwen/qwen3.7-flash', body['model'])
        self.assertNotIn('models', body)
        self.assertEqual({'sort': 'throughput', 'allow_fallbacks': True}, body['provider'])
        self.assertEqual('json_schema', body['response_format']['type'])
        self.assertNotEqual(ai_router.FREE_ROUTER, body['model'])

    def test_non_question_wiki_openrouter_free_keeps_existing_router_without_provider_override(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('urllib.request.urlopen', return_value=self._response(ai_router.FREE_ROUTER)) as call:
            ai_router.generate('openrouter-free', 'system', 'ordinary free request')
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(ai_router.FREE_ROUTER, body['model'])
        self.assertNotIn('models', body)
        self.assertNotIn('provider', body)

    def test_generic_qwen_keeps_existing_routing_without_provider_override(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('urllib.request.urlopen', return_value=self._response()) as call:
            ai_router.generate('qwen', 'system', 'ordinary qwen request')
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(ai_router.QWEN_MODEL, body['model'])
        self.assertNotIn('provider', body)

    def test_quality_retry_keeps_question_wiki_throughput_routing(self):
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}, 'required': ['ok']}
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('private_reference_bank.output_issues', side_effect=[['TYPE_MISMATCH'], []]), \
             patch('urllib.request.urlopen', side_effect=[self._response(), self._response()]) as call:
            ai_router.generate('openrouter-free', 'system', 'variant\n' + self.MARKER, schema=schema)
        self.assertEqual(2, call.call_count)
        for provider_call in call.call_args_list:
            body = json.loads(provider_call.args[0].data)
            self.assertEqual({'sort': 'throughput', 'allow_fallbacks': True}, body['provider'])
            self.assertEqual(ai_router.QWEN_MODEL, body['model'])

    def test_qwen_model_is_existing_server_model(self):
        self.assertEqual('qwen/qwen3.7-flash', ai_router.QWEN_MODEL)


if __name__ == '__main__':
    unittest.main()
