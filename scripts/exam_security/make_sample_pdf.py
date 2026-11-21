"""F2R 종이 시험지 보안 무늬 견본 A4 PDF 만들기 — docs/exam-security/PAPER_MARK.md, TEST_PLAN 4-4.

사용: python3 scripts/exam_security/make_sample_pdf.py <출력.pdf>
필요: Pillow, reportlab(인쇄 도구라 CI 대상 아님). 무늬 규칙은 paper_mark.fill_texture와 같다(test_make_sample_pdf.py가 확인).

무늬는 1200dpi 그림으로 만들어 실제 크기(1px = 1/1200인치)로 PDF에 넣는다. 인쇄할 때 '실제 크기(100%)'가 아니면
점 크기가 바뀌어 시험 결과가 달라진다. 페이지의 50㎜ 눈금으로 확인한다.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import paper_mark as pm  # noqa: E402

DPI = 1200
REPO = Path(__file__).resolve().parents[2]
FONT_DIR = REPO / 'ops/designcraft/templates/ko-textbook-a4/Document Fonts'


def mm_to_px(mm: float, dpi: int = DPI) -> int:
    return round(mm / 25.4 * dpi)


def texture_image(w: int, h: int, kind: str, phase: int = 0,
                  mark_dot: int = pm.MARK_DOT, mark_step: int = pm.MARK_STEP) -> Image.Image:
    """paper_mark.fill_texture와 같은 무늬를 PIL 1비트 그림으로(빠르게) 만든다. phase = 무늬 시작점 어긋남(px)."""
    step, dot = (2, 1) if kind == 'base' else (mark_step, mark_dot)
    tile = Image.new('1', (step, step), 1)
    for y in range(dot):
        for x in range(dot):
            tile.putpixel((x, y), 0)
    img = Image.new('1', (w, h), 1)
    for y in range(-phase % step - step, h, step):
        for x in range(-phase % step - step, w, step):
            img.paste(tile, (x, y))
    return img


def seat_band_image(seat: int, block_px: int, phase: int, mark_dot: int = pm.MARK_DOT,
                    mark_step: int = pm.MARK_STEP) -> Image.Image:
    bits = pm.seat_bits(seat)
    img = Image.new('1', (block_px * len(bits), block_px), 1)
    for i, b in enumerate(bits):
        img.paste(texture_image(block_px, block_px, 'mark' if b else 'base', phase, mark_dot, mark_step), (i * block_px, 0))
    return img


def build(out_path: str) -> None:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.units import mm
    from reportlab.lib.utils import ImageReader
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.pdfgen import canvas

    pdfmetrics.registerFont(TTFont('KR', str(FONT_DIR / 'NotoSansKR-Regular.ttf')))
    pdfmetrics.registerFont(TTFont('KRB', str(FONT_DIR / 'NotoSansKR-Bold.ttf')))
    c = canvas.Canvas(out_path, pagesize=A4)
    W, H = A4
    left = 18 * mm

    def text(x, y, s, size=9, bold=False, gray=0.0):
        c.setFont('KRB' if bold else 'KR', size)
        c.setFillGray(gray)
        c.drawString(x, y, s)

    def place(img, x, y, w_mm):
        """그림을 실제 크기로: 1px = 1/1200인치."""
        w = img.width / DPI * 25.4 * mm
        h = img.height / DPI * 25.4 * mm
        assert abs(w - w_mm * mm) < 0.2 * mm, 'not at true size'
        c.drawImage(ImageReader(img.convert('L')), x, y, width=w, height=h)
        return h

    y = H - 18 * mm
    text(left, y, 'F2R 종이 시험지 보안 무늬 견본 v0.1', 15, True)
    text(left + 112 * mm, y, '시험용 · 실제 시험지 아님 · 2026-11-21', 8, gray=0.35)
    y -= 7 * mm
    text(left, y, '인쇄: 반드시 「실제 크기(100%)」, 흑백, 토너 절약 끔. 레이저 1200dpi 1장 · 600dpi 1장 · 잉크젯 1장.', 8.5)
    y -= 5 * mm
    text(left, y, '아래 눈금이 자로 정확히 50㎜인지 먼저 확인하세요. 다르면 인쇄 배율이 바뀐 것이라 시험 결과를 쓰지 않습니다.', 8.5)
    y -= 6 * mm
    c.setLineWidth(0.5)
    c.line(left, y, left + 50 * mm, y)
    for i in range(11):
        c.line(left + i * 5 * mm, y, left + i * 5 * mm, y + (2.5 if i % 2 else 4) * mm)
    text(left + 52 * mm, y, '50㎜', 8)

    # A. 비교 칸
    y -= 12 * mm
    text(left, y, 'A. 눈·사진 비교 칸 (각 25×25㎜, 잉크 양 같음)', 11, True)
    y -= 4.5 * mm
    text(left, y, '30㎝에서 세 칸이 같은 회색으로 보이는지 적고, 15·25·40㎝에서 휴대폰으로 찍어 사진에서 다르게 보이는지 확인합니다.', 8, gray=0.3)
    side = mm_to_px(25)
    boxes = [('가', texture_image(side, side, 'base')), ('나', texture_image(side, side, 'mark')),
             ('다', texture_image(side, side, 'mark', 0, 4, 8))]
    y -= 28 * mm
    for i, (label, img) in enumerate(boxes):
        x = left + i * 34 * mm
        place(img, x, y, 25)
        text(x, y - 4.5 * mm, f'{label}', 9, True)
    text(left + 104 * mm, y + 20 * mm, '정답(인쇄 후 가려 두고 확인):', 8, gray=0.35)
    text(left + 104 * mm, y + 15 * mm, '가 = 바탕(1×1점 / 2칸)', 8, gray=0.35)
    text(left + 104 * mm, y + 10 * mm, '나 = 표시(3×3점 / 6칸, 기본)', 8, gray=0.35)
    text(left + 104 * mm, y + 5 * mm, '다 = 표시(4×4점 / 8칸, 큰 점)', 8, gray=0.35)

    # B. 큰 칸 좌석 코드
    y -= 14 * mm
    text(left, y, 'B. 좌석 코드 · 큰 칸(칸 4㎜, 사진에서 눈으로 확인용)', 11, True)
    y -= 4.5 * mm
    text(left, y, '사진을 확대해 어두운 칸 = 1로 읽습니다. 칸 순서: 시작 1 · 좌석 10칸 · 검사 2칸 · 끝 0. 한 좌석을 3줄(무늬 0·1·2px 어긋남)로 인쇄했습니다.', 7.5, gray=0.3)
    blk = mm_to_px(4)
    for seat, md, ms, note in ((555, 3, 6, '기본 무늬'), (37, 3, 6, '기본 무늬'), (555, 4, 8, '큰 점 무늬')):
        y -= 6 * mm
        text(left, y, f'좌석 {seat} · {note} · 기대값 ' + ''.join(map(str, pm.seat_bits(seat))), 8)
        for ph in (0, 1, 2):
            y -= 4.6 * mm
            place(seat_band_image(seat, blk, ph, md, ms), left, y, 56)

    # C. 실제 크기 좌석 코드 + 모의 문항
    y -= 12 * mm
    text(left, y, 'C. 실제 크기 좌석 코드(칸 1㎜, 띠 14㎜)와 모의 문항', 11, True)
    y -= 4.5 * mm
    text(left, y, '시험지 여백에 넣을 실제 크기입니다. 문항을 읽는 데 거슬리는지 적어 주세요.', 8, gray=0.3)
    y -= 8 * mm
    sentences = [
        '3. 다음 글의 밑줄 친 부분 중, 어법상 틀린 것은?',
        'The girl ① who won the prize ② is my sister. She practiced every day,',
        'and her teacher, ③ whom she respected, said that her effort ④ was paid off.',
        '① who    ② is    ③ whom    ④ was paid off    ⑤ 없음',
    ]
    small = 48  # paper_mark 모형과 같은 칸 크기(1.02㎜)
    for seat in (314,):
        for ph in (0, 1, 2):
            place(seat_band_image(seat, small, ph), W - 18 * mm - 14.2 * mm, y + 6 * mm - ph * 1.6 * mm, 14.2)
    text(W - 18 * mm - 30 * mm, y + 9.5 * mm, '좌석 314 →', 7, gray=0.35)
    for s in sentences:
        text(left, y, s, 10)
        y -= 6.5 * mm
    y -= 2 * mm
    for seat in (0, 1023):
        text(left, y, f'좌석 {seat}', 7.5, gray=0.35)
        for ph in (0, 1, 2):
            place(seat_band_image(seat, small, ph), left + 16 * mm, y - ph * 1.6 * mm, 14.2)
        y -= 7 * mm

    # D. 기록표
    y -= 4 * mm
    text(left, y, 'D. 기록 (TEST_PLAN 4-4)', 11, True)
    rows = ['프린터·해상도:', '눈 시험(30㎝) 가·나·다 같아 보임? 결이 보임?', '본문 읽기 불편? (C)',
            '휴대폰 기종:', '15㎝ 사진: 나·다 구분 / 좌석 읽힘', '25㎝ 사진: 나·다 구분 / 좌석 읽힘',
            '40㎝ 사진: 나·다 구분 / 좌석 읽힘', '복사본 사진: 좌석 읽힘']
    c.setLineWidth(0.3)
    for r in rows:
        y -= 6.2 * mm
        text(left, y + 1.5 * mm, r, 8.5)
        c.setStrokeGray(0.6)
        c.line(left + 78 * mm, y + 1 * mm, W - 18 * mm, y + 1 * mm)
    text(left, 10 * mm, '사진은 측정이 끝나면 지웁니다. 사람(학생)이 찍힌 사진은 쓰지 않습니다. 이 무늬의 효과는 실측 전 계산 모형 결과입니다.', 7, gray=0.4)
    c.showPage()
    c.save()


if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else 'paper_mark_sample.pdf')
