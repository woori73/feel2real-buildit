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
import prompt_logic as pl  # noqa: E402


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

    def test_request_openrouter_key_is_ignored_and_not_returned(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'model': 'stealth/ox-alpha', 'choices': [{'message': {'content': 'safe'}}]}).encode()
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-secret'}), patch('urllib.request.urlopen', return_value=response) as call:
            result = ai_router.generate('ox-alpha', 'system', 'hello', openrouter_key='browser-secret')
        self.assertEqual('server', result.key_source)
        self.assertIn('server-secret', call.call_args.args[0].headers['Authorization'])
        self.assertNotIn('browser-secret', call.call_args.args[0].headers['Authorization'])
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

    def test_missing_openrouter_key_makes_default_qwen_unavailable_without_gemini_fallback(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'gemini-key', 'OPENROUTER_API_KEY': ''}, clear=False), patch.object(ai_router.pl, 'call_gemini_text', return_value='Gemini fallback'):
            self.assertFalse(ai_router.availability()['providers']['ox-alpha']['available'])
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('auto', 'system', 'hello')
        self.assertEqual('qwen', raised.exception.provider)

    def test_auto_uses_qwen_and_reports_actual_openrouter_metadata(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'model': 'qwen/qwen3.7-flash-202608', 'choices': [{'message': {'content': 'Qwen 응답'}}]}).encode()
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), patch('urllib.request.urlopen', return_value=response) as call:
            result = ai_router.generate('auto', 'system', 'hello')
        self.assertEqual(('openrouter', 'qwen/qwen3.7-flash-202608', 'Qwen 응답', 'server'), (result.provider, result.model, result.text, result.key_source))
        self.assertEqual(ai_router.QWEN_MODEL, json.loads(call.call_args.args[0].data)['model'])

    def test_structured_qwen_uses_strict_json_schema(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({'model': 'qwen/qwen3.7-flash', 'choices': [{'message': {'content': '{"ok":true}'}}]}).encode()
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}, 'required': ['ok']}
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), patch('urllib.request.urlopen', return_value=response) as call:
            ai_router.generate('auto', 'system', 'hello', schema=schema)
        response_format = json.loads(call.call_args.args[0].data)['response_format']
        self.assertEqual('json_schema', response_format['type'])
        self.assertTrue(response_format['json_schema']['strict'])
        self.assertEqual({**schema, 'additionalProperties': False}, response_format['json_schema']['schema'])

    def test_strict_schema_recursively_closes_nested_objects(self):
        schema = {'type': 'object', 'properties': {'items': {'type': 'array', 'items': {
            'type': 'object', 'properties': {'value': {'type': 'string'}}, 'required': ['value'],
        }}}, 'required': ['items']}
        strict = ai_router.strict_object_schema(schema)
        self.assertFalse(strict['additionalProperties'])
        self.assertFalse(strict['properties']['items']['items']['additionalProperties'])

    def test_core_auto_does_not_fallback_to_free_model_when_qwen_fails(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), patch.object(ai_router, '_openrouter', side_effect=ai_router.ProviderError('failed', 'openrouter')) as call:
            with self.assertRaises(ai_router.ProviderError):
                ai_router.generate('auto', 'system', 'hello')
        self.assertEqual(1, call.call_count)

    def test_noncritical_auto_can_use_declared_backup_chain(self):
        qwen_error = ai_router.ProviderError('failed', 'openrouter')
        fallback = ai_router.ProviderResult('backup', 'ox-alpha', 'stealth/ox-alpha', 'server')
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), patch.object(ai_router, '_openrouter', side_effect=[qwen_error, fallback]) as call:
            result = ai_router.generate('auto', 'system', 'hello', allow_noncritical_fallback=True)
        self.assertEqual('ox-alpha', result.provider)
        self.assertEqual(2, call.call_count)

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


    def test_quality_rejects_forbidden_because_in_answer_and_grading_rule(self):
        question = {
            'type': 'sa',
            'stem': "다음 문장을 because 절 없이 같은 의미로 바꾸어 쓰시오.",
            'conditions': ['because 절 사용 금지'],
            'answerFormat': '12단어 이내의 완전한 문장',
            'sampleAnswer': 'Every evening, Mina reads because she enjoys learning.',
            'acceptedAnswers': ['Mina reads because she enjoys learning.'],
            'gradingRule': "because 또는 as로 연결할 것.",
        }
        issues = pl.question_quality_issues(question)
        self.assertIn('because 사용 금지 조건과 모범답안이 모순됨', issues)
        self.assertIn('because 사용 금지 조건과 채점 기준이 모순됨', issues)


class QuestionWikiStructuredOutputTest(unittest.TestCase):
    MARKER = ('QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: '
              '{"questionWiki":true,"questionWikiId":31,"grade":2,"examYear":2024,'
              '"questionNo":31,"type":"vocabulary"}')

    def _ok(self, content, model='free/model'):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps(
            {'model': model, 'choices': [{'message': {'content': content}}]}).encode()
        return response

    def _valid_question(self):
        return json.dumps({'type': 'vocabulary', 'instruction': 'stem', 'passage': 'passage',
                           'choices': ['a', 'b', 'c', 'd', 'e'], 'answer': '1',
                           'explanation': 'why', 'sourceQuestionId': 31,
                           'generationMode': 'OPENROUTER_FREE'})

    def test_marker_request_sends_strict_schema_pinned_to_type_and_source_id(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('urllib.request.urlopen', return_value=self._ok(self._valid_question())) as call:
            result = main.ai_complete(main.AiCompleteRequest(
                system='s', prompt='new variant\n' + self.MARKER, provider='openrouter-free'))
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(ai_router.FREE_ROUTER, body['model'])
        response_format = body['response_format']
        self.assertEqual('json_schema', response_format['type'])
        self.assertTrue(response_format['json_schema']['strict'])
        schema = response_format['json_schema']['schema']
        self.assertEqual(['vocabulary'], schema['properties']['type']['enum'])
        self.assertEqual([31], schema['properties']['sourceQuestionId']['enum'])
        self.assertFalse(schema['additionalProperties'])
        self.assertEqual('free/model', result['model'])
        self.assertEqual(1, call.call_count)

    def test_generic_caller_without_marker_keeps_the_old_contract(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('urllib.request.urlopen', return_value=self._ok('plain text', model='q')) as call:
            main.ai_complete(main.AiCompleteRequest(
                system='s', prompt='summarize this sentence', provider='auto'))
        body = json.loads(call.call_args.args[0].data)
        self.assertNotIn('response_format', body)
        self.assertEqual(ai_router.QWEN_MODEL, body['model'])

    def test_marker_request_never_falls_back_to_a_paid_provider(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'g', 'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch.object(ai_router.pl, 'call_gemini_text') as gemini, \
             patch.object(ai_router.pl, 'call_gemini_structured') as gemini_structured, \
             patch('urllib.request.urlopen', side_effect=TimeoutError()) as call:
            with self.assertRaises(main.HTTPException):
                main.ai_complete(main.AiCompleteRequest(
                    system='s', prompt='variant\n' + self.MARKER, provider='openrouter-free'))
        gemini.assert_not_called()
        gemini_structured.assert_not_called()
        for request in (c.args[0] for c in call.call_args_list):
            self.assertEqual(ai_router.FREE_ROUTER, json.loads(request.data)['model'])

    def test_free_model_rejecting_json_schema_downgrades_to_json_object_once(self):
        import urllib.error
        http_error = urllib.error.HTTPError('u', 400, 'Bad Request', {}, None)
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('urllib.request.urlopen',
                   side_effect=[http_error, self._ok(self._valid_question())]) as call:
            result = main.ai_complete(main.AiCompleteRequest(
                system='s', prompt='variant\n' + self.MARKER, provider='openrouter-free'))
        self.assertEqual(2, call.call_count)
        first = json.loads(call.call_args_list[0].args[0].data)
        second = json.loads(call.call_args_list[1].args[0].data)
        self.assertEqual('json_schema', first['response_format']['type'])
        self.assertEqual('json_object', second['response_format']['type'])
        self.assertEqual(ai_router.FREE_ROUTER, second['model'])
        self.assertEqual('free/model', result['model'])

    def test_optional_schema_field_is_accepted_and_marker_schema_wins(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'server-key'}), \
             patch('private_reference_bank.enrich_prompt', side_effect=lambda p: p), \
             patch('urllib.request.urlopen', return_value=self._ok(self._valid_question())) as call:
            main.ai_complete(main.AiCompleteRequest.model_validate({
                'system': 's', 'prompt': 'variant\n' + self.MARKER, 'provider': 'openrouter-free',
                'schema': {'type': 'object', 'properties': {'x': {'type': 'string'}}, 'required': ['x']}}))
        schema = json.loads(call.call_args.args[0].data)['response_format']['json_schema']['schema']
        self.assertIn('sourceQuestionId', schema['properties'])


if __name__ == '__main__':
    unittest.main()


