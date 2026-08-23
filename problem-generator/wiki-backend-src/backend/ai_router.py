"""Server-side AI provider router. Secrets are accepted per request or read from env only."""
import json
import os
import socket
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Optional

import prompt_logic as pl

DEFAULT_PROVIDER_TIMEOUT_SECONDS = 90.0


def provider_timeout_seconds() -> float:
    try:
        value = float(os.getenv('AI_PROVIDER_TIMEOUT_SECONDS', str(DEFAULT_PROVIDER_TIMEOUT_SECONDS)))
    except ValueError:
        value = DEFAULT_PROVIDER_TIMEOUT_SECONDS
    return max(10.0, min(value, 120.0))


class ProviderError(Exception):
    def __init__(self, message: str, provider: str, status: Optional[int] = None):
        super().__init__(message)
        self.provider = provider
        self.status = status


@dataclass
class ProviderResult:
    text: str
    provider: str
    model: str
    key_source: str


def availability() -> dict:
    return {
        'defaultProvider': 'gemini',
        'providers': {
            'gemini': {'available': bool(os.getenv('GEMINI_API_KEY', '').strip()), 'experimental': False, 'serverKeyConfigured': bool(os.getenv('GEMINI_API_KEY', '').strip())},
            'ox-alpha': {'available': bool(os.getenv('OPENROUTER_API_KEY', '').strip()), 'experimental': True, 'serverKeyConfigured': bool(os.getenv('OPENROUTER_API_KEY', '').strip()), 'model': os.getenv('OX_ALPHA_MODEL', 'stealth/ox-alpha')},
        },
    }


def _openrouter(key: str, system: str, prompt: str, schema: Optional[dict], temperature: float) -> ProviderResult:
    model = os.getenv('OX_ALPHA_MODEL', 'stealth/ox-alpha').strip() or 'stealth/ox-alpha'
    user_prompt = prompt
    if schema:
        user_prompt += '\n\n반드시 다음 JSON Schema를 따르는 JSON 객체만 반환하라:\n' + json.dumps(schema, ensure_ascii=False)
    body = json.dumps({'model': model, 'messages': [{'role': 'system', 'content': system}, {'role': 'user', 'content': user_prompt}], 'temperature': temperature}, ensure_ascii=False).encode('utf-8')
    request = urllib.request.Request('https://openrouter.ai/api/v1/chat/completions', data=body, method='POST', headers={'Authorization': f'Bearer {key}', 'Content-Type': 'application/json', 'HTTP-Referer': 'https://feel2real.com', 'X-Title': 'BuildIt Wiki'})
    try:
        with urllib.request.urlopen(request, timeout=provider_timeout_seconds()) as response:
            payload = json.loads(response.read().decode('utf-8'))
        text = payload.get('choices', [{}])[0].get('message', {}).get('content', '')
        if isinstance(text, list):
            text = ''.join(item.get('text', '') for item in text if isinstance(item, dict))
        if not str(text).strip():
            raise ProviderError('OpenRouter 응답 내용이 없습니다.', 'ox-alpha')
        return ProviderResult(str(text), 'ox-alpha', payload.get('model') or model, 'client')
    except urllib.error.HTTPError as exc:
        raise ProviderError(f'OpenRouter 요청 실패 ({exc.code})', 'ox-alpha', exc.code) from exc
    except (TimeoutError, socket.timeout) as exc:
        raise ProviderError('Ox Alpha 응답 시간이 초과되었습니다. 다시 시도하거나 Gemini로 전환해 주세요.', 'ox-alpha', 504) from exc
    except urllib.error.URLError as exc:
        if isinstance(exc.reason, (TimeoutError, socket.timeout)):
            raise ProviderError('Ox Alpha 응답 시간이 초과되었습니다. 다시 시도하거나 Gemini로 전환해 주세요.', 'ox-alpha', 504) from exc
        raise ProviderError('Ox Alpha 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.', 'ox-alpha', 502) from exc
    except json.JSONDecodeError as exc:
        raise ProviderError('Ox Alpha 응답을 해석하지 못했습니다. 다시 시도해 주세요.', 'ox-alpha', 502) from exc


def generate(provider: str, system: str, prompt: str, *, schema: Optional[dict] = None, temperature: float = 0.25, gemini_key: str = '', openrouter_key: str = '') -> ProviderResult:
    requested = provider if provider in ('gemini', 'ox-alpha') else 'auto'
    gemini = gemini_key.strip() or os.getenv('GEMINI_API_KEY', '').strip()
    openrouter = openrouter_key.strip() or os.getenv('OPENROUTER_API_KEY', '').strip()
    candidates = ['gemini', 'ox-alpha'] if requested == 'auto' else [requested]
    last_error = None
    for candidate in candidates:
        try:
            if candidate == 'gemini' and gemini:
                text = pl.call_gemini_structured(gemini, system, prompt, schema, temperature) if schema else pl.call_gemini_text(gemini, system, prompt, temperature)
                return ProviderResult(text, 'gemini', 'gemini-flash', 'client' if gemini_key.strip() else 'server')
            if candidate == 'ox-alpha' and openrouter:
                result = _openrouter(openrouter, system, prompt, schema, temperature)
                result.key_source = 'client' if openrouter_key.strip() else 'server'
                return result
        except Exception as exc:
            last_error = exc
            if requested == 'gemini':
                raise
    if last_error:
        raise last_error
    raise ProviderError('선택한 AI provider에 사용할 client/server 키가 없습니다.', requested)

