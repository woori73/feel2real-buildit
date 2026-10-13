#!/usr/bin/env python3
"""지피지기 Qwen vs Nemotron Lightning A/B — 동일 system/prompt(동일 RAG 컨텍스트)를
Wiki AI Router의 실제 코드 경로(ai_router.generate)로 두 provider에 같은 입력으로 보내 비교한다.

- NewStartAiChatService가 조립하는 근거 컨텍스트 문자열을 파일로 뽑아 이 스크립트에 주면
  두 답변이 스키마·근거·프롬프트가 완전히 같은 조건에서 생성된다 (A/B 공정성 유지).
- 키는 서버 환경변수(OPENROUTER_API_KEY/NVIDIA_API_KEY)만 사용한다. 출력에 키를 포함하지 않는다.
- 결과 인쇄만 한다. DB/RAG/공식자료에 저장하지 않는다.

사용 예:
  python scripts/newstart_provider_ab.py --system-file ctx_system.txt --prompt-file ctx_question.txt
  python scripts/newstart_provider_ab.py --providers qwen,nvidia --prompt "질문만 있는 경우"
"""
import argparse
import sys
import time
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'problem-generator' / 'wiki-backend-src' / 'backend'
sys.path.insert(0, str(BACKEND))
import ai_router  # noqa: E402


def run(provider: str, system: str, prompt: str, max_chars: int) -> None:
    print(f'===== provider={provider} =====')
    started = time.perf_counter()
    try:
        result = ai_router.generate(provider, system, prompt, temperature=0.25)
    except Exception as exc:
        print(f'FAILED ({type(exc).__name__}): {exc}')
        return
    elapsed = time.perf_counter() - started
    text = result.text.strip()
    if max_chars > 0:
        text = text[:max_chars]
    print(f'resolved_provider={result.provider} model={result.model} elapsed_seconds={elapsed:.2f}')
    print(text)
    print()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--system', default='')
    parser.add_argument('--system-file', type=Path)
    parser.add_argument('--prompt', default='')
    parser.add_argument('--prompt-file', type=Path)
    parser.add_argument('--providers', default='qwen,nvidia')
    parser.add_argument('--max-chars', type=int, default=4000)
    args = parser.parse_args()

    system = args.system or (args.system_file.read_text(encoding='utf-8') if args.system_file else '')
    prompt = args.prompt or (args.prompt_file.read_text(encoding='utf-8') if args.prompt_file else '')
    if not prompt.strip():
        print('--prompt 또는 --prompt-file 로 질문(근거 컨텍스트 포함 가능)을 주세요.', file=sys.stderr)
        return 2
    for provider in (p.strip() for p in args.providers.split(',') if p.strip()):
        run(provider, system, prompt, args.max_chars)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
