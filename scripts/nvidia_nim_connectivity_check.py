#!/usr/bin/env python3
"""NVIDIA NIM Nemotron Lightning 연결 스모크 테스트 ( 스트림 + reasoning/content 분리 확인).

용도: 지피지기 Lightning A/B 검증 전 최소 1회 연결 확인.
  - API connectivity / 한국어 출력 / stream SSE 파싱 / reasoning_content 분리
- NVIDIA_API_KEY 환경변수만 사용한다. 키를 출력·저장하지 않는다.
- 응답은 화면에 인쇄만 하고 DB/RAG/파일에 저장하지 않는다.

사용 예:
  python scripts/nvidia_nim_connectivity_check.py
  python scripts/nvidia_nim_connectivity_check.py --prompt "다른 질문"
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

NIM_CHAT_URL = 'https://integrate.api.nvidia.com/v1/chat/completions'
LIGHTNING_MODEL = 'nvidia/nemotron-3.5-lightning-30b-a3b'
DEFAULT_PROMPT = '새출발기금에서 부실차주와 부실우려차주의 차이를 한 문장으로 설명해 주세요.'


def stream_completion(api_key: str, model: str, prompt: str):
    """공식 sample 계약(temperature=1, top_p=0.95, max_tokens=16384, enable_thinking,
    reasoning_budget=16384, stream)으로 호출하고 (reasoning, content)를 분리 수집한다."""
    body = {
        'model': model,
        'messages': [{'role': 'user', 'content': prompt}],
        'temperature': 1,
        'top_p': 0.95,
        'max_tokens': 16384,
        'chat_template_kwargs': {'enable_thinking': True},
        'reasoning_budget': 16384,
        'stream': True,
    }
    request = urllib.request.Request(
        NIM_CHAT_URL,
        data=json.dumps(body, ensure_ascii=False).encode(),
        method='POST',
        headers={'Authorization': f'Bearer {api_key}', 'Content-Type': 'application/json', 'Accept': 'text/event-stream'},
    )
    reasoning_parts, content_parts = [], []
    with urllib.request.urlopen(request, timeout=180) as response:
        for raw in response:
            line = raw.decode('utf-8').strip()
            if not line.startswith('data:'):
                continue
            payload = line[len('data:'):].strip()
            if payload == '[DONE]':
                break
            chunk = json.loads(payload)
            choices = chunk.get('choices') or []
            if not choices:
                continue
            delta = choices[0].get('delta') or {}
            if delta.get('reasoning_content'):
                reasoning_parts.append(delta['reasoning_content'])
            if delta.get('content'):
                content_parts.append(delta['content'])
    return ''.join(reasoning_parts), ''.join(content_parts)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prompt', default=DEFAULT_PROMPT)
    parser.add_argument('--model', default=LIGHTNING_MODEL)
    args = parser.parse_args()

    api_key = os.getenv('NVIDIA_API_KEY', '').strip()
    if not api_key:
        print('NVIDIA_API_KEY가 환경변수에 없습니다. 운영/인강 환경을 건드리지 말고 이 PC 사용자 환경변수를 확인하세요.', file=sys.stderr)
        return 2
    started = time.perf_counter()
    try:
        reasoning, content = stream_completion(api_key, args.model, args.prompt)
    except urllib.error.HTTPError as exc:
        print(f'NIM HTTP 오류: {exc.code} {exc.reason}', file=sys.stderr)
        return 1
    except (TimeoutError, urllib.error.URLError) as exc:
        print(f'NIM 연결 오류: {exc}', file=sys.stderr)
        return 1
    elapsed = time.perf_counter() - started
    print(f'model={args.model}')
    print(f'elapsed_seconds={elapsed:.2f}')
    print(f'reasoning_stream_seen={"yes" if reasoning.strip() else "no"} (내부 처리용, 최종 답변에 포함하지 않음)')
    print(f'korean_output_ok={"yes" if any("가" <= ch <= "힣" for ch in content) else "no"}')
    print('--- 최종 답변 (assistant content만) ---')
    print(content.strip() or '(빈 응답 — 실패로 간주)')
    return 0 if content.strip() else 1


if __name__ == '__main__':
    raise SystemExit(main())
