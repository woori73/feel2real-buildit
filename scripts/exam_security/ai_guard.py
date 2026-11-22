"""F2R 시험지 AI 정답 방지 장치(시뮬레이션) — docs/exam-security/AI_ANSWER_GUARD.md.

두 가지를 계산 모형으로 확인한다.
1. 사진에만 보이는 미끼 숫자(2겹): paper_mark의 두 무늬(같은 잉크 25%)로 숫자를 그린다.
   사람 눈에는 고른 회색 띠, 가까이 찍은 사진에서는 숫자가 드러난다. AI가 이 숫자를 정답 근거로 쓰게 하려는 장치다.
   상용 AI가 실제로 속는지는 모른다(측정 전 가설). 여기서는 "사람 눈에는 안 보이고 사진에는 보인다"까지만 확인한다.
2. 미끼 오답 신호: 미끼가 가리키는 오답을 여러 문항에서 고른 학생을 우연으로 보기 어려운지 이항분포로 계산한다.
   신호일 뿐 처벌 근거가 아니다(감독 절차가 확인한다).

표준 라이브러리만 쓴다(테스트·CI용). 실제 휴대폰·인쇄기에서 드러나는지는 TEST_PLAN 4-5 측정 전까지 모른다.
"""
from __future__ import annotations

from math import comb
from typing import Dict, List

import paper_mark as pm

# 5×7 숫자 글꼴. '#' = 표시 무늬(사진에서 어두움), '.' = 바탕 무늬
DIGIT_FONT: Dict[str, List[str]] = {
    '0': ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'],
    '1': ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
    '2': ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'],
    '3': ['#####', '...#.', '..#..', '...#.', '....#', '#...#', '.###.'],
    '4': ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'],
    '5': ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
    '6': ['..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'],
    '7': ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
    '8': ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'],
    '9': ['.###.', '#...#', '#...#', '.####', '....#', '...#.', '.##..'],
}
GLYPH_W, GLYPH_H = 5, 7
GAP = 1  # 숫자 사이 빈 칸(바탕 무늬)
PAD = 1  # 띠 가장자리 바탕 무늬 칸


def _layout(n_digits: int) -> tuple[int, int]:
    """띠의 칸 수(가로, 세로). 가장자리 PAD + 숫자 n개 + 사이 GAP."""
    cols = PAD * 2 + n_digits * GLYPH_W + (n_digits - 1) * GAP
    rows = PAD * 2 + GLYPH_H
    return cols, rows


def render_decoy_digits(text: str, block: int = 48) -> pm.Image:
    """숫자 문자열을 '표시/바탕' 무늬 칸으로 그린다. 칸 하나 = block px(1200dpi에서 48px ≈ 1㎜).

    잉크 양이 모든 칸에서 25%로 같아서 사람 눈에는 글자 없는 회색 띠로 보인다.
    """
    if not text or any(ch not in DIGIT_FONT for ch in text):
        raise ValueError('decoy text must be digits 0-9')
    cols, rows = _layout(len(text))
    img = pm.blank(rows * block, cols * block)
    for r in range(rows):
        for c in range(cols):
            pm.fill_texture(img, c * block, r * block, block, block, 'base')
    for i, ch in enumerate(text):
        x0 = PAD + i * (GLYPH_W + GAP)
        for gr, line in enumerate(DIGIT_FONT[ch]):
            for gc, cell in enumerate(line):
                if cell == '#':
                    pm.fill_texture(img, (x0 + gc) * block, (PAD + gr) * block, block, block, 'mark')
    return img


def _correlation(a: List[float], b: List[float]) -> float:
    ma, mb = sum(a) / len(a), sum(b) / len(b)
    num = sum((x - ma) * (y - mb) for x, y in zip(a, b))
    da = sum((x - ma) ** 2 for x in a) ** 0.5
    db = sum((y - mb) ** 2 for y in b) ** 0.5
    return num / (da * db) if da and db else 0.0


def read_decoy_digits(view: pm.Image, n_digits: int, min_contrast: float = 0.03,
                      min_match: float = 0.6) -> str | None:
    """보이는 그림(사진·AI 입력·사람 눈 모형)에서 칸마다 평균 밝기를 재 숫자 글꼴과 맞춰 본다.

    칸 사이 밝기 차이가 min_contrast보다 작으면(사람 눈처럼 고른 회색) 읽지 못한 것으로 본다.
    가장 닮은 숫자의 상관이 min_match보다 낮아도 읽지 못한 것으로 본다(잘못 읽기보다 못 읽기를 택한다).
    """
    cols, rows = _layout(n_digits)
    h, w = len(view), len(view[0])
    cw, ch = w / cols, h / rows
    pad_x, pad_y = max(1, int(cw / 6)), max(1, int(ch / 6))

    def cell_mean(c: int, r: int) -> float:
        x0, y0 = int(c * cw) + pad_x, int(r * ch) + pad_y
        return pm.region_mean(view, x0, y0, max(1, int(cw) - 2 * pad_x), max(1, int(ch) - 2 * pad_y))

    means = [[cell_mean(c, r) for c in range(cols)] for r in range(rows)]
    flat = [v for row in means for v in row]
    lo, hi = min(flat), max(flat)
    if hi - lo < min_contrast:
        return None
    out = []
    for i in range(n_digits):
        x0 = PAD + i * (GLYPH_W + GAP)
        dark = [(hi - means[PAD + r][x0 + c]) / (hi - lo) for r in range(GLYPH_H) for c in range(GLYPH_W)]
        scores = {d: _correlation(dark, [1.0 if cell == '#' else 0.0 for line in glyph for cell in line])
                  for d, glyph in DIGIT_FONT.items()}
        best = max(scores, key=scores.get)
        if scores[best] < min_match:
            return None
        out.append(best)
    return ''.join(out)


def decoy_flag(decoy_picks: int, decoy_items: int, human_rate: float, alpha: float = 0.01) -> dict:
    """미끼 오답 신호. 정직한 학생이 미끼 오답을 고를 확률이 human_rate일 때,
    decoy_items 문항 중 decoy_picks개 이상을 우연히 고를 확률(p)을 이항분포로 계산한다.

    p < alpha이면 flag=True(감독관 확인 대상). human_rate는 사람만 푼 예비 시험에서 잰 값을 쓴다.
    """
    if decoy_items <= 0 or not 0 <= decoy_picks <= decoy_items:
        raise ValueError('need 0 <= decoy_picks <= decoy_items and decoy_items > 0')
    if not 0.0 < human_rate < 1.0:
        raise ValueError('human_rate must be between 0 and 1 (measured on human-only pilot)')
    p = sum(comb(decoy_items, k) * human_rate ** k * (1 - human_rate) ** (decoy_items - k)
            for k in range(decoy_picks, decoy_items + 1))
    return {'p_value': p, 'flag': p < alpha}
