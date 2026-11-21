"""F2R 종이 시험지 보안 무늬(시뮬레이션) — docs/exam-security/PAPER_MARK.md.

원리(복사 방지 'VOID' 무늬와 같은 계열): 잉크 면적 비율이 같은 두 무늬
  - 바탕: 아주 작은 점을 촘촘히(사람 눈·카메라 둘 다 낱점은 못 봄)
  - 표시: 조금 큰 점을 듬성듬성(카메라 픽셀보다 커서 점이 살아남음)
사람 눈은 둘을 같은 회색으로 섞어 본다(평균이 같음). 휴대폰 카메라는 픽셀로 나눠 담은 뒤(공간 양자화)
밝기 곡선을 세게 걸고 8비트로 자른다(밝기 양자화). 이 비선형 단계에서 바탕은 밝아지고 표시는 점이 남아
평균이 달라진다 → 사진에서만 표시(좌석 코드 등)가 드러난다.

여기 있는 것은 **카메라 처리 단계를 단순화한 계산 모형**이다. 실제 휴대폰·인쇄기·거리·각도에서
드러나는지는 TEST_PLAN 7장 측정 전까지 모른다. 표준 라이브러리만 쓴다(테스트·CI용).

다이내믹 덴서티: 보안 무늬를 시험지 어디에 얼마나 넣을지는 D = I·(dΨ/dt+0.1)/(V+ε)로 정한다
(I=정답 결정 정보량, dΨ/dt=그 유형을 AI가 최근 더 잘 푸는 정도, V=사람이 읽는 데 주는 부담).
"""
from __future__ import annotations

from typing import Iterable, List, Sequence

Image = List[List[float]]  # 0.0 = 잉크(검정), 1.0 = 흰 종이

EPS = 1e-3


# ---------- 무늬 만들기(인쇄 해상도, 1px ≈ 1200dpi 기준 0.021mm) ----------

def blank(h: int, w: int) -> Image:
    return [[1.0] * w for _ in range(h)]


MARK_DOT = 3  # 표시 점 크기(인쇄 px). 카메라 픽셀(인쇄 2px 안팎)보다 커야 점이 살아남는다
MARK_STEP = 6  # 표시 점 간격. dot/step = 1/2 → 잉크 25%로 바탕과 같다


def fill_texture(img: Image, x0: int, y0: int, w: int, h: int, kind: str,
                 mark_dot: int = MARK_DOT, mark_step: int = MARK_STEP) -> None:
    """사각형 안을 무늬로 채운다. 'base' = 1×1점을 2칸마다(잉크 25%), 'mark' = 큰 점을 듬성듬성(잉크 25%).

    표시 점이 클수록 카메라 거리·각도가 달라도 잘 드러나지만, 사람 눈에도 결이 보이기 시작한다
    (PAPER_MARK.md 3장 표: 3/6은 카메라 픽셀 2px에서만, 4/8은 2~3px에서 드러나지만 결이 더 보임).
    """
    if kind not in ("base", "mark"):
        raise ValueError("kind must be base or mark")
    if mark_dot * 2 != mark_step:
        raise ValueError("mark_dot must be half of mark_step (same 25% ink as base)")
    step, dot = (2, 1) if kind == "base" else (mark_step, mark_dot)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img[y][x] = 0.0 if (x % step) < dot and (y % step) < dot else 1.0


def ink_coverage(img: Image, x0: int, y0: int, w: int, h: int) -> float:
    cells = [img[y][x] for y in range(y0, y0 + h) for x in range(x0, x0 + w)]
    return 1.0 - sum(cells) / len(cells)


# ---------- 보는 쪽 모형 ----------

def box_blur(img: Image, r: int) -> Image:
    """(2r+1)² 평균(가장자리는 있는 칸만). 사람 눈의 흐림 근사."""
    h, w = len(img), len(img[0])
    ii = [[0.0] * (w + 1) for _ in range(h + 1)]
    for y in range(h):
        run = 0.0
        for x in range(w):
            run += img[y][x]
            ii[y + 1][x + 1] = ii[y][x + 1] + run
    out = blank(h, w)
    for y in range(h):
        y1, y2 = max(0, y - r), min(h, y + r + 1)
        for x in range(w):
            x1, x2 = max(0, x - r), min(w, x + r + 1)
            s = ii[y2][x2] - ii[y1][x2] - ii[y2][x1] + ii[y1][x1]
            out[y][x] = s / ((y2 - y1) * (x2 - x1))
    return out


def texture_visibility(view: Image, x0: int, y0: int, w: int, h: int) -> float:
    """영역 안 밝기의 표준편차. 사람 눈 모형에서 이 값이 크면 무늬 결이 보일 수 있다."""
    cells = [view[y][x] for y in range(y0, y0 + h) for x in range(x0, x0 + w)]
    m = sum(cells) / len(cells)
    return (sum((c - m) ** 2 for c in cells) / len(cells)) ** 0.5


def human_view(img: Image, acuity_px: int = 2) -> Image:
    """30㎝ 거리 시력 한계(약 0.09mm ≈ 인쇄 4~5px)를 평균 흐림으로 근사. 비선형 단계 없음."""
    return box_blur(img, acuity_px)


def downsample(img: Image, k: int, offset: int = 0) -> Image:
    """k×k 칸을 한 픽셀로 평균(카메라 픽셀이 빛을 모으는 것 = 공간 양자화). offset = 격자 어긋남."""
    h, w = len(img), len(img[0])
    out = []
    for y in range(offset, h - k + 1, k):
        row = []
        for x in range(offset, w - k + 1, k):
            row.append(sum(img[yy][xx] for yy in range(y, y + k) for xx in range(x, x + k)) / (k * k))
        out.append(row)
    return out


def tone_curve(v: float, gain: float = 1.6) -> float:
    """휴대폰이 문서 사진에 거는 대비 강화(가운데 기준 늘리고 0~1로 자름) + 8비트 양자화."""
    stretched = min(1.0, max(0.0, 0.5 + (v - 0.5) * gain))
    return round(stretched * 255) / 255


def camera_view(img: Image, pixel_px: int = 2, offset: int = 0, gain: float = 1.6) -> Image:
    return [[tone_curve(v, gain) for v in row] for row in downsample(img, pixel_px, offset)]


def ai_input_view(photo: Image, shrink: int = 4) -> Image:
    """AI가 사진을 줄여 받는 단계(입력 축소). 표시 블록은 이 단계 뒤에도 남아야 한다."""
    return downsample(photo, shrink)


def region_mean(img: Image, x0: int, y0: int, w: int, h: int) -> float:
    cells = [img[y][x] for y in range(y0, y0 + h) for x in range(x0, x0 + w)]
    return sum(cells) / len(cells)


# ---------- 좌석 코드(사진에서만 드러남) ----------

N_BITS = 14  # 시작 1 + 좌석 10 + 검사 2 + 끝 0


def seat_bits(seat: int, data_bits: int = 10) -> List[int]:
    """[1] + 좌석 번호 10비트 + 검사 2비트(1의 개수 mod 4) + [0]. 앞뒤 고정 칸은 좌석 0번도 읽히게 한다."""
    if not 0 <= seat < (1 << data_bits):
        raise ValueError("seat out of range")
    bits = [(seat >> (data_bits - 1 - i)) & 1 for i in range(data_bits)]
    check = sum(bits) % 4
    return [1] + bits + [(check >> 1) & 1, check & 1] + [0]


def bits_to_seat(bits: Sequence[int], data_bits: int = 10) -> int | None:
    bits = list(bits)
    if len(bits) != data_bits + 4 or bits[0] != 1 or bits[-1] != 0:
        return None
    data, check = bits[1:1 + data_bits], bits[1 + data_bits:3 + data_bits]
    if (sum(data) % 4) != (check[0] * 2 + check[1]):
        return None  # 검사 비트가 안 맞으면 읽지 않은 것으로 본다
    seat = 0
    for b in data:
        seat = seat * 2 + b
    return seat


def render_seat_strip(seat: int, block: int = 48, mark_dot: int = MARK_DOT, mark_step: int = MARK_STEP) -> Image:
    """여백 띠: 14칸, 1이면 'mark' 무늬, 0이면 'base' 무늬. 사람 눈에는 고른 회색 띠(잉크 25%)."""
    bits = seat_bits(seat)
    img = blank(block, block * len(bits))
    for i, b in enumerate(bits):
        fill_texture(img, i * block, 0, block, block, "mark" if b else "base", mark_dot, mark_step)
    return img


def read_seat_strip(view: Image, n_bits: int = N_BITS) -> int | None:
    """보이는 그림(사진 또는 AI 입력)에서 칸마다 평균 밝기를 재 어두운 칸을 1로 읽는다."""
    h, w = len(view), len(view[0])
    cw = w // n_bits
    pad = max(1, cw // 6)
    means = [region_mean(view, i * cw + pad, pad, cw - 2 * pad, h - 2 * pad) for i in range(n_bits)]
    lo, hi = min(means), max(means)
    if hi - lo < 0.03:  # 칸 사이 차이가 없으면(사람 눈처럼) 읽을 수 없음
        return None
    mid = (lo + hi) / 2
    return bits_to_seat([1 if m < mid else 0 for m in means])


# ---------- 다이내믹 덴서티: 어디에 무늬를 넣을지 ----------

def region_density(answer_info: float, ai_trend: float, read_cost: float, eps: float = EPS) -> float:
    """D = I·(dΨ/dt + 0.1) / (V + ε). 세 값 모두 0~1."""
    return answer_info * (max(0.0, ai_trend) + 0.1) / (read_cost + eps)


def allocate_marks(regions: Iterable[dict], area_budget: float) -> dict:
    """영역(dict: id, area, answer_info, ai_trend, read_cost) 중 D 높은 순으로 면적 예산 안에서 무늬를 넣는다.

    read_cost가 1 이상(사람이 읽는 본문 글자 바로 위)인 영역은 D와 상관없이 넣지 않는다.
    """
    scored, skipped = [], []
    for r in regions:
        if r.get("read_cost", 0.0) >= 1.0:
            skipped.append({**r, "reason": "reading_area"})
            continue
        scored.append({**r, "d": region_density(r["answer_info"], r["ai_trend"], r["read_cost"])})
    scored.sort(key=lambda r: -r["d"])
    picked, used = [], 0.0
    for r in scored:
        if used + r["area"] <= area_budget:
            picked.append(r)
            used += r["area"]
        else:
            skipped.append({**r, "reason": "over_budget"})
    return {"picked": picked, "skipped": skipped, "used_area": used, "budget": area_budget}


def render_seat_bands(seat: int, block: int = 48, mark_dot: int = MARK_DOT, mark_step: int = MARK_STEP,
                      shifts: Sequence[int] = (0, 1, 2)) -> List[Image]:
    """같은 좌석 코드를 여러 줄 인쇄하되, 줄마다 무늬 시작점을 몇 px씩 어긋나게 한다.
    카메라 픽셀 격자가 어떻게 놓이든 한 줄은 맞아떨어지게 하려는 것(격자 어긋남 보완)."""
    bands = []
    for sh in shifts:
        s = render_seat_strip(seat, block, mark_dot, mark_step)
        s = [row[sh:] + row[:sh] for row in s]
        bands.append(s[sh:] + s[:sh])
    return bands


def read_seat_bands(views: Sequence[Image]) -> int | None:
    """줄마다 읽고 가장 많이 나온 좌석 번호를 고른다. 하나도 안 읽히면 None."""
    found = [v for v in (read_seat_strip(b) for b in views) if v is not None]
    return max(set(found), key=found.count) if found else None
