#!/usr/bin/env python3
"""
check_glyph_coverage.py — fontTools 기반 한글 글리프 커버리지 검사 (DesignCraft M-1)

검사 항목:
- 입력 폰트 파일의 한글 11,172자 (U+AC00–U+D7A3) 커버리지
- 누락 글리프 목록 출력 (처음 20개)

종료 코드: 100%면 0, 아니면 1

사용법:
  python check_glyph_coverage.py <font_path.otf|ttf>

예:
  python check_glyph_coverage.py NotoSansKR-Regular.otf

주의:
- cmap 키는 int 코드포인트로 비교해야 함. str로 비교하면 거짓 0% 결과가 나옴.
  (Qoder 실측에서 발생한 실수 — `cmap.update(t.cmap.keys())`로 정정)

라이선스: 이 스크립트는 MIT. fontTools는 MIT.
"""

import argparse
import sys

try:
    from fontTools.ttLib import TTFont
except ImportError:
    print("[ERROR] fontTools가 설치되지 않았습니다: pip install fonttools", file=sys.stderr)
    sys.exit(2)


# 한글 음절 범위: U+AC00–U+D7A3 (11,172자)
HANGUL_START = 0xAC00
HANGUL_END = 0xD7A3
HANGUL_COUNT = HANGUL_END - HANGUL_START + 1  # 11172


def check_coverage(font_path, verbose_missing=20):
    """폰트의 한글 커버리지를 검사하고 (passed, messages) 반환."""
    messages = []

    try:
        font = TTFont(font_path)
    except Exception as e:
        return False, [f"  - ERROR: 폰트 열기 실패: {e}"]

    # cmap 테이블에서 모든 코드포인트 수집 (int 키로!)
    # 주의: str 키로 비교하면 거짓 결과가 나옴
    cmap_codepoints = set()
    for table in font["cmap"].tables:
        # t.cmap.keys()는 int. set에 직접 추가.
        cmap_codepoints.update(table.cmap.keys())

    # 한글 범위 검사
    missing = []
    for cp in range(HANGUL_START, HANGUL_END + 1):
        if cp not in cmap_codepoints:
            missing.append(cp)

    covered = HANGUL_COUNT - len(missing)
    pct = covered / HANGUL_COUNT * 100.0

    messages.append(f"  - coverage: {covered}/{HANGUL_COUNT} ({pct:.1f}%)")

    if missing:
        missing_str = ", ".join(f"U+{cp:04X}" for cp in missing[:verbose_missing])
        if len(missing) > verbose_missing:
            missing_str += f", ... (총 {len(missing)}개)"
        messages.append(f"  - missing (first {verbose_missing}): {missing_str}")
        passed = False
    else:
        passed = True

    font.close()
    return passed, messages


def main():
    parser = argparse.ArgumentParser(description="한글 글리프 커버리지 검사 (fontTools 기반)")
    parser.add_argument("font_path", help="검사할 폰트 파일 경로 (.otf/.ttf/.ttc)")
    parser.add_argument("--show-missing", type=int, default=20,
                        help="표시할 누락 글리프 수 (기본 20)")
    args = parser.parse_args()

    passed, messages = check_coverage(args.font_path, verbose_missing=args.show_missing)

    status = "PASS" if passed else "FAIL"
    print(f"[{status}] {args.font_path}")
    for m in messages:
        print(m)

    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
