"""Responses contract and routing checks: no paid API calls."""
import json
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
import ai_router

class SitewideLunaTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'SITEWIDE_LUNA_ENABLED': 'true', 'OPENAI_API_KEY': 'server-only-test'})
        self.env.start()
        self.addCleanup(self.env.stop)

    def response(self, text='answer', status='completed'):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'status': status, 'output': [
            {'type': 'reasoning', 'summary': [{'text': 'private'}]},
            {'type': 'message', 'role': 'assistant', 'content': [{'type': 'output_text', 'text': text}]}]}).encode()
        return response

    def test_all_legacy_choices_use_luna_and_server_key(self):
        for provider in ('auto', 'gemini', 'qwen', 'openrouter-free', 'ox-alpha', 'openrouter:vendor/free'):
            with self.subTest(provider=provider), patch('urllib.request.urlopen', return_value=self.response()) as call:
                result = ai_router.generate(provider, 'original policy', 'original context', gemini_key='client-secret')
                request = call.call_args.args[0]
                body = json.loads(request.data)
                self.assertEqual('https://api.openai.com/v1/responses', request.full_url)
                self.assertEqual('Bearer server-only-test', request.headers['Authorization'])
                self.assertEqual(('gpt-6-luna', 'original policy', 'original context', False),
                                 (body['model'], body['instructions'], body['input'], body['store']))
                self.assertEqual(('answer', 'openai', 'gpt-6-luna'), (result.text, result.provider, result.model))
                self.assertNotIn('server-only-test', repr(result))
                self.assertEqual(1, call.call_count)

    def test_schema_uses_json_object_and_rejects_invalid_content(self):
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}}
        with patch('urllib.request.urlopen', return_value=self.response('{"ok":true}')) as call:
            ai_router.generate('auto', 'system', 'prompt', schema=schema)
            self.assertEqual({'type': 'json_object'}, json.loads(call.call_args.args[0].data)['text']['format'])
        with patch('urllib.request.urlopen', return_value=self.response('not json')):
            with self.assertRaises(ai_router.ProviderError):
                ai_router.generate('auto', 'system', 'prompt', schema=schema)

    def test_reference_enrichment_and_one_quality_retry_are_retained(self):
        with patch.object(ai_router.private_reference_bank, 'has_request', return_value=True), \
             patch.object(ai_router.private_reference_bank, 'enrich_prompt', return_value='private reference + question'), \
             patch.object(ai_router.private_reference_bank, 'output_issues', side_effect=[['type'], []]), \
             patch('urllib.request.urlopen', return_value=self.response()) as call:
            ai_router.generate('openrouter-free', 'policy', 'question')
            self.assertEqual(2, call.call_count)
            self.assertIn('private reference + question', json.loads(call.call_args.args[0].data)['input'])
            self.assertIn('QUALITY RETRY', json.loads(call.call_args.args[0].data)['input'])

    def test_invalid_after_retry_is_blocked(self):
        with patch.object(ai_router.private_reference_bank, 'has_request', return_value=True), \
             patch.object(ai_router.private_reference_bank, 'enrich_prompt', return_value='reference'), \
             patch.object(ai_router.private_reference_bank, 'output_issues', return_value=['type']), \
             patch('urllib.request.urlopen', return_value=self.response()) as call:
            with self.assertRaises(ai_router.ProviderError) as caught:
                ai_router.generate('openrouter-free', 'policy', 'question')
            self.assertEqual(422, caught.exception.status)
            self.assertEqual(2, call.call_count)

    def test_multimodal_maps_image_and_text_without_dropping_parts(self):
        with patch('urllib.request.urlopen', return_value=self.response()) as call:
            ai_router.generate_multimodal('legacy', 'policy', [
                {'type': 'text', 'text': 'read'}, {'type': 'image_url', 'image_url': {'url': 'data:image/png;base64,test'}}])
            parts = json.loads(call.call_args.args[0].data)['input'][0]['content']
            self.assertEqual(['input_text', 'input_image'], [p['type'] for p in parts])
            self.assertEqual('data:image/png;base64,test', parts[1]['image_url'])
        with self.assertRaises(ai_router.ProviderError):
            ai_router.generate_multimodal('legacy', 'policy', [{'type': 'unknown'}])

    def test_missing_key_and_incomplete_answer_fail_without_legacy_fallback(self):
        with patch.dict(os.environ, {'OPENAI_API_KEY': ''}), patch('urllib.request.urlopen') as call:
            with self.assertRaises(ai_router.ProviderError): ai_router.generate('auto', 'policy', 'prompt')
            call.assert_not_called()
        with patch('urllib.request.urlopen', return_value=self.response(status='incomplete')) as call:
            with self.assertRaises(ai_router.ProviderError): ai_router.generate('auto', 'policy', 'prompt', allow_noncritical_fallback=True)
            self.assertEqual(1, call.call_count)

    def test_availability_reports_actual_paid_model_without_keys(self):
        body = ai_router.availability()
        self.assertEqual('gpt-6-luna', body['defaultModel'])
        self.assertFalse(body['providers']['openrouter-free']['free'])
        self.assertNotIn('server-only-test', json.dumps(body))

if __name__ == '__main__': unittest.main()
