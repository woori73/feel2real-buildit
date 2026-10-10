#!/usr/bin/env python3
"""
verify_pdf.py — PyMuPDF 기반 PDF 품질 검사 (DesignCraft M-1)

검사 항목:
- 페이지 수 (기대값과 비교)
- 페이지 크기 (A4: 595×842pt 허용 오차 ±2pt)
- 폰트 임베드 확인 (서브셋 폰트명 파싱)
- 기대 한글 문구 존재 확인 (텍스트 추출)

종료 코드: PASS=0, FAIL=1

사용법:
  python verify_pdf.py <pdf_path> [--expect-pages N] [--expect-text "문구1,문구2"] [--expect-size A4]

예:
  python verify_pdf.py test.pdf --expect-pages 2 --expect-text "제1장,한글 테스트"

라이선스: 이 스크립트는 MIT. PyMuPDF는 AGPL-3.0 — CI·로컬 QA용으로만 사용, 서버 통합 금지.

검사 한계:
- 텍스트 추출 성공 ≠ 모든 글리프 정상. 추출된 텍스트에 없는 글리프는 검출 불가.
- 예: PDF에 "가"가 있고 추출되면 PASS지만, "힣"이 tofu로 렌더링되어도
  추출 텍스트에 없으면 검출 못 함.
- 완전한 검사는 DesignCraft preflight의 missingGlyphs와 병행해야 함.
"""

import argparse
import sys

try:
    import pymupdf  # PyMuPDF (AGPL-3.0) — CI·로컬 QA용으로만 사용
except ImportError:
    print("[ERROR] PyMuPDF가 설치되지 않았습니다: pip install pymupdf", file=sys.stderr)
    print("  (AGPL-3.0 라이선스 — CI·로컬 QA용으로만 사용, 서버 통합 금지)", file=sys.stderr)
    sys.exit(2)


# 페이지 크기 정의 (pt)
PAGE_SIZES = {
    "A4": (595, 842),
    "A5": (420, 595),
    "B5": (499, 709),
    "LETTER": (612, 792),
}
SIZE_TOLERANCE = 2.0  # pt


def check_pdf(pdf_path, expect_pages=None, expect_texts=None, expect_size=None):
    """PDF를 검사하고 (passed: bool, messages: list) 반환."""
    messages = []
    passed = True

    try:
        doc = pymupdf.open(pdf_path)
    except Exception as e:
        return False, [f"  - ERROR: PDF 열기 실패: {e}"]

    # 1. 페이지 수
    actual_pages = len(doc)
    if expect_pages is not None:
        if actual_pages == expect_pages:
            messages.append(f"  - pages: {actual_pages} (expected {expect_pages})")
        else:
            messages.append(f"  - pages: {actual_pages} (expected {expect_pages}) MISMATCH")
            passed = False
    else:
        messages.append(f"  - pages: {actual_pages}")

    # 2. 페이지 크기 (첫 페이지 기준)
    if doc:
        page = doc[0]
        w, h = page.rect.width, page.rect.height
        # 세로/가로 모두 허용 (회전 대응)
        size_ok = False
        size_name = "unknown"
        if expect_size and expect_size.upper() in PAGE_SIZES:
            ew, eh = PAGE_SIZES[expect_size.upper()]
            if (abs(w - ew) <= SIZE_TOLERANCE and abs(h - eh) <= SIZE_TOLERANCE) or \
               (abs(w - eh) <= SIZE_TOLERANCE and abs(h - ew) <= SIZE_TOLERANCE):
                size_ok = True
                size_name = expect_size.upper()
        else:
            # 자동 감지
            for name, (ew, eh) in PAGE_SIZES.items():
                if (abs(w - ew) <= SIZE_TOLERANCE and abs(h - eh) <= SIZE_TOLERANCE) or \
                   (abs(w - eh) <= SIZE_TOLERANCE and abs(h - ew) <= SIZE_TOLERANCE):
                    size_name = name
                    size_ok = True
                    break

        if expect_size:
            if size_ok:
                messages.append(f"  - size: {size_name} ({w:.0f}x{h:.0f}pt)")
            else:
                messages.append(f"  - size: {w:.0f}x{h:.0f}pt (expected {expect_size}) MISMATCH")
                passed = False
        else:
            messages.append(f"  - size: {size_name} ({w:.0f}x{h:.0f}pt)")

    # 3. 폰트 임베드 확인
    fonts = set()
    for page in doc:
        for f in page.get_fonts():
            # f = (xref, ext, type, basefont, name, encoding, referencer)
            font_name = f[3] or f[4] or "unknown"
            fonts.add(font_name)

    if fonts:
        messages.append(f"  - fonts: {len(fonts)} embedded ({', '.join(sorted(fonts))})")
    else:
        messages.append("  - fonts: none found (WARNING: 텍스트가 이미지일 수 있음)")

    # 4. 기대 한글 문구 확인
    if expect_texts:
        full_text = ""
        for page in doc:
            full_text += page.get_text()

        # 공백 정규화: NBSP(U+00A0) 등을 일반 공백으로 변환
        # PyMuPDF는 단어 구분에 NBSP를 사용할 수 있음
        import re
        normalized = re.sub(r'[\xa0\u2000-\u200b\t]+', ' ', full_text)
        normalized = re.sub(r' +', ' ', normalized)

        found = 0
        for phrase in expect_texts:
            # 기대 문구도 정규화
            norm_phrase = re.sub(r'[\xa0\u2000-\u200b\t]+', ' ', phrase)
            norm_phrase = re.sub(r' +', ' ', norm_phrase)
            if norm_phrase in normalized:
                found += 1
            else:
                messages.append(f"  - text MISSING: '{phrase}' (glyph missing suspected)")
                passed = False
        messages.append(f"  - text: {found}/{len(expect_texts)} phrases found")
        if found < len(expect_texts):
            messages.append("  - reason: expected phrase not found in extracted text")

    doc.close()
    return passed, messages


def main():
    parser = argparse.ArgumentParser(description="PDF 품질 검사 (PyMuPDF 기반)")
    parser.add_argument("pdf_path", help="검사할 PDF 파일 경로")
    parser.add_argument("--expect-pages", type=int, default=None, help="기대 페이지 수")
    parser.add_argument("--expect-text", default=None,
                        help="기대 문구 (쉼표로 구분)")
    parser.add_argument("--expect-size", default=None,
                        help="기대 페이지 크기 (A4, A5, B5, LETTER)")
    args = parser.parse_args()

    expect_texts = None
    if args.expect_text:
        expect_texts = [t.strip() for t in args.expect_text.split(",") if t.strip()]

    passed, messages = check_pdf(
        args.pdf_path,
        expect_pages=args.expect_pages,
        expect_texts=expect_texts,
        expect_size=args.expect_size,
    )

    status = "PASS" if passed else "FAIL"
    print(f"[{status}] {args.pdf_path}")
    for m in messages:
        print(m)

    # 검사 한계 안내 (FAIL 시에만 간략히)
    if not passed:
        print("  (참고: 텍스트 추출 기반 검사의 한계 — README.md 참조)")

    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
