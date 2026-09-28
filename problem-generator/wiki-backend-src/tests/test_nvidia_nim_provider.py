import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-nvidia-router.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()
import ai_router  # noqa: E402


def _nim_response(content, model='nvidia/nemotron-3.5-lightning-30b-a3b', reasoning=None):
    message = {'role': 'assistant', 'content': content}
    if reasoning is not None:
        message['reasoning_content'] = reasoning
    response = MagicMock()
    response.__enter__.return_value.read.return_value = json.dumps(
        {'model': model, 'choices': [{'message': message}]}).encode()
    return response


class NvidiaNimProviderTest(unittest.TestCase):
    OFFICIAL_MODEL = 'nvidia/nemotron-3.5-lightning-30b-a3b'
    OFFICIAL_BASE = 'https://integrate.api.nvidia.com/v1/chat/completions'

    def test_given_nim_key_when_generate_nvidia_then_official_contract_is_sent_and_reasoning_discarded(self):
        """given NVIDIA_API_KEY when provider='nvidia' then NIM 공식 contract 파라미터로 호출하고 content만 반환한다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'nim-server-secret'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('한 문장 답변', reasoning='내부 사고 과정')) as call:
            result = ai_router.generate('nvidia', 'system', '질문')
        request = call.call_args.args[0]
        self.assertEqual(self.OFFICIAL_BASE, request.full_url)
        self.assertIn('nim-server-secret', request.headers['Authorization'])
        body = json.loads(request.data)
        self.assertEqual(self.OFFICIAL_MODEL, body['model'])
        self.assertEqual(1, body['temperature'])
        self.assertEqual(0.95, body['top_p'])
        self.assertEqual(16384, body['max_tokens'])
        self.assertTrue(body['chat_template_kwargs']['enable_thinking'])
        self.assertEqual(16384, body['reasoning_budget'])
        self.assertEqual(('nvidia', self.OFFICIAL_MODEL, '한 문장 답변', 'server'),
                         (result.provider, result.model, result.text, result.key_source))
        self.assertNotIn('내부 사고 과정', result.text)
        self.assertNotIn('nim-server-secret', repr(result))

    def test_given_lightning_override_env_when_generate_nvidia_then_env_model_id_wins(self):
        """given NVIDIA_NEMOTRON_MODEL when provider='nvidia' then 설정한 모델 ID를 사용한다(Ultra 등 모델 설정 확장)."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k', 'NVIDIA_NEMOTRON_MODEL': 'nvidia/custom-nemotron'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('x', model='nvidia/custom-nemotron')) as call:
            result = ai_router.generate('nvidia', 'system', '질문')
        self.assertEqual('nvidia/custom-nemotron', json.loads(call.call_args.args[0].data)['model'])
        self.assertEqual('nvidia/custom-nemotron', result.model)

    def test_given_model_id_when_generate_nvidia_colon_then_explicit_model_passthrough(self):
        """given 'nvidia:<model-id>' when generate then 해당 모델 ID를 그대로 NIM에 전달한다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('x', model='vendor/ultra-120b')) as call:
            result = ai_router.generate('nvidia:vendor/ultra-120b', 'system', '질문')
        self.assertEqual('vendor/ultra-120b', json.loads(call.call_args.args[0].data)['model'])
        self.assertEqual('nvidia:vendor/ultra-120b', result.provider)

    def test_given_ultra_env_when_generate_nvidia_ultra_then_ultra_model_used(self):
        """given NVIDIA_NEMOTRON_ULTRA_MODEL when provider='nvidia-ultra' then Ultra 모델 ID로 호출한다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k', 'NVIDIA_NEMOTRON_ULTRA_MODEL': 'nvidia/nemotron-ultra-x'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('x', model='nvidia/nemotron-ultra-x')) as call:
            result = ai_router.generate('nvidia-ultra', 'system', '질문')
        self.assertEqual('nvidia/nemotron-ultra-x', json.loads(call.call_args.args[0].data)['model'])
        self.assertEqual('nvidia-ultra', result.provider)

    def test_given_ultra_unset_when_generate_nvidia_ultra_then_503_without_network_call(self):
        """given Ultra 미설정 when provider='nvidia-ultra' then 네트워크 호출 없이 503으로 실패한다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k', 'NVIDIA_NEMOTRON_ULTRA_MODEL': ''}, clear=False), \
             patch('urllib.request.urlopen') as call:
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('nvidia-ultra', 'system', '질문')
        self.assertEqual(503, raised.exception.status)
        call.assert_not_called()

    def test_given_missing_nim_key_when_generate_nvidia_then_fails_without_qwen_fallback(self):
        """given NVIDIA_API_KEY 없음 when provider='nvidia' then Qwen fallback 없이 실패한다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': '', 'OPENROUTER_API_KEY': 'router-key'}, clear=False), \
             patch.object(ai_router, '_openrouter') as openrouter:
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('nvidia', 'system', '질문')
        self.assertEqual('nvidia', raised.exception.provider)
        self.assertEqual(503, raised.exception.status)
        openrouter.assert_not_called()

    def test_given_nvidia_timeout_when_generate_then_504_without_secret(self):
        """given NIM timeout when provider='nvidia' then 504 ProviderError, 키 값은 메시지에 없다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'never-print-me'}, clear=False), \
             patch('urllib.request.urlopen', side_effect=TimeoutError()):
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('nvidia', 'system', '질문')
        self.assertEqual(504, raised.exception.status)
        self.assertNotIn('never-print-me', str(raised.exception))

    def test_given_nvidia_empty_content_when_generate_then_error_not_reasoning_leak(self):
        """given content가 비고 reasoning만 있는 응답 when NIM 호출 then 빈 응답 오류로 처리한다."""
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps(
            {'model': self.OFFICIAL_MODEL, 'choices': [{'message': {'role': 'assistant', 'content': '', 'reasoning_content': '사고'}}]}).encode()
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k'}, clear=False), \
             patch('urllib.request.urlopen', return_value=response):
            with self.assertRaises(ai_router.ProviderError) as raised:
                ai_router.generate('nvidia', 'system', '질문')
        self.assertNotIn('사고', str(raised.exception))

    def test_given_schema_when_generate_nvidia_then_json_contract_appended_not_response_format(self):
        """given schema when provider='nvidia' then response_format 대신 프롬프트에 JSON 계약을 병합한다."""
        schema = {'type': 'object', 'properties': {'ok': {'type': 'boolean'}}, 'required': ['ok']}
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('{"ok":true}')) as call:
            ai_router.generate('nvidia', 'system', '질문', schema=schema)
        body = json.loads(call.call_args.args[0].data)
        self.assertNotIn('response_format', body)
        prompt = body['messages'][1]['content']
        self.assertIn('반드시 다음 JSON Schema를 따르는 JSON 객체만 반환하라:', prompt)
        appended = json.loads(prompt.split('반드시 다음 JSON Schema를 따르는 JSON 객체만 반환하라:\n', 1)[1])
        self.assertFalse(appended['additionalProperties'])
        self.assertEqual('질문', prompt.split('\n\n반드시', 1)[0])

    def test_given_nim_key_when_auto_then_chain_unchanged_and_nim_never_called(self):
        """given NVIDIA 키가 있어도 auto 체인은 Qwen 유지 — production default 변경 금지 보장."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'k', 'OPENROUTER_API_KEY': 'router-key'}, clear=False), \
             patch.object(ai_router, '_openrouter', return_value=ai_router.ProviderResult('t', 'openrouter', ai_router.QWEN_MODEL, 'server')) as openrouter, \
             patch.object(ai_router, '_nvidia_nim_request') as nim:
            result = ai_router.generate('auto', 'system', '질문', allow_noncritical_fallback=True)
        self.assertEqual('openrouter', result.provider)
        openrouter.assert_called_once()
        nim.assert_not_called()

    def test_given_nim_key_when_availability_then_nvidia_reported_without_secret(self):
        """given availability() then nvidia/nvidia-ultra 상태를 노출하되 키 값은 절대 포함하지 않는다."""
        with patch.dict(os.environ, {'NVIDIA_API_KEY': 'do-not-return', 'NVIDIA_NEMOTRON_ULTRA_MODEL': ''}, clear=False):
            body = json.dumps(ai_router.availability())
        self.assertIn('"nvidia"', body)
        self.assertIn(self.OFFICIAL_MODEL, body)
        self.assertNotIn('do-not-return', body)
        with patch.dict(os.environ, {'NVIDIA_API_KEY': ''}, clear=False):
            self.assertFalse(ai_router.availability()['providers']['nvidia']['available'])

    def test_given_existing_providers_when_generate_then_openrouter_contract_untouched(self):
        """given qwen when auto/명시 호출 then OpenRouter URL·본문·키가 NVIDIA 변경과 무관하게 유지된다."""
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'router-key', 'NVIDIA_API_KEY': 'k'}, clear=False), \
             patch('urllib.request.urlopen', return_value=_nim_response('qwen 답변', model='qwen/qwen3.7-flash')) as call:
            result = ai_router.generate('qwen', 'system', '질문')
        request = call.call_args.args[0]
        self.assertEqual('https://openrouter.ai/api/v1/chat/completions', request.full_url)
        self.assertIn('router-key', request.headers['Authorization'])
        self.assertNotIn('chat_template_kwargs', json.loads(request.data))
        self.assertEqual('qwen 답변', result.text)


if __name__ == '__main__':
    unittest.main()
