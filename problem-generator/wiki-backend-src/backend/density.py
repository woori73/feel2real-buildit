"""T02: 다이내믹 덴서티(동태밀도) D 점수 — docs/f2r-llm/DYNAMIC_DENSITY.md 2·5장.

D = I · (dΨ/dt + 0.1) / (V + ε)
  I     : 청크 태그(단원·문법)가 그 학교 과거 기출에서 차지한 비중(0~1)
  dΨ/dt : 최근 2회 시험 비중 − 그 이전 비중(줄었으면 0)
  V     : 청크 분량(천 자) × 노이즈 비율(반복 줄·서식 기호·형식적 서두)
  ε     : 1e-3

순수 함수만 둔다. DB·모델 호출·자동 배포 판단은 하지 않는다.
D는 검색된 근거의 **순위와 컨텍스트 배정만** 바꾼다. 검색되지 않은 청크를 D로 끌어올리지 않는다.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Iterable, Mapping, Sequence

EPS = 1e-3
BASE_TREND = 0.1  # 변화가 없어도 기출 비중은 반영한다
RECENT_EXAMS = 2

_BOILERPLATE = re.compile(r"^\s*(\d+\.\s*)?(다음|아래)\s.*(물음에 답하시오|읽고)|^\s*\[?\d+\s*[~\-]\s*\d+\]?\s*$")
_FORMAT_CHARS = re.compile(r"[\s\-=_*#|·•○●□■◆◇▶▷…~]")


def _norm_tags(tags: Iterable[str]) -> frozenset[str]:
    return frozenset(t.strip() for t in (tags or []) if t and t.strip())


@dataclass(frozen=True)
class ExamQuestion:
    """과거 기출 문항 하나. exam_key는 시험 단위(예: '2026-1-기말'), exam_order는 시간 순서 비교용 값."""

    exam_key: str
    exam_order: str
    tags: frozenset[str] = field(default_factory=frozenset)


class ExamStats:
    """기출 문항 태그 집계. 평가할 시험보다 늦은 문항은 넣지 않는다(호출하는 쪽 책임, DYNAMIC_DENSITY 3장)."""

    def __init__(self, questions: Sequence[ExamQuestion], recent_exams: int = RECENT_EXAMS):
        orders = sorted({(q.exam_order, q.exam_key) for q in questions})
        recent_keys = {k for _, k in orders[-recent_exams:]} if recent_exams > 0 else set()
        self._all = list(questions)
        self._recent = [q for q in questions if q.exam_key in recent_keys]
        self._prior = [q for q in questions if q.exam_key not in recent_keys]

    @staticmethod
    def _share(pool: Sequence[ExamQuestion], tags: frozenset[str]) -> float:
        if not pool or not tags:
            return 0.0
        hit = sum(1 for q in pool if q.tags & tags)
        return hit / len(pool)

    def exam_share(self, tags: Iterable[str]) -> float:
        return self._share(self._all, _norm_tags(tags))

    def recent_share(self, tags: Iterable[str]) -> float:
        return self._share(self._recent, _norm_tags(tags))

    def prior_share(self, tags: Iterable[str]) -> float:
        return self._share(self._prior, _norm_tags(tags))


def noise_ratio(text: str) -> float:
    """반복된 줄·서식 기호·형식적 서두가 본문에서 차지하는 비율(0~1)."""
    if not text:
        return 1.0
    total = len(text)
    noisy = len(_FORMAT_CHARS.findall(text))
    seen: set[str] = set()
    for line in text.splitlines():
        key = line.strip()
        if not key:
            continue
        if key in seen or _BOILERPLATE.search(key):
            noisy += len(_FORMAT_CHARS.sub("", key))
        seen.add(key)
    return min(1.0, noisy / total)


def density_score(text: str, tags: Iterable[str], stats: ExamStats, eps: float = EPS) -> float:
    tag_set = _norm_tags(tags)
    i = stats.exam_share(tag_set)
    psi = max(0.0, stats.recent_share(tag_set) - stats.prior_share(tag_set))
    v = noise_ratio(text) * len(text or "") / 1000
    return i * (psi + BASE_TREND) / (v + eps)


def estimate_tokens(text: str) -> int:
    """대략적인 토큰 수(한글·영문 섞인 교재 기준 2.5자당 1토큰). 실제 측정값이 아니라 배정용 근사다."""
    return max(1, round(len(text or "") / 2.5))


def allocate_context(
    chunks: Sequence[Mapping],
    stats: ExamStats,
    token_budget: int,
    min_similarity: float = 0.0,
) -> dict:
    """검색된 청크(dict: id, text, tags, similarity)를 D 순으로 토큰 예산 안에 담는다(LLM 경량화).

    - similarity < min_similarity 인 청크는 D와 상관없이 뺀다(근거 없음 → 답 보류로 이어지게).
    - 예산을 넘는 청크는 건너뛰고 다음 청크를 본다(작은 고밀도 청크가 들어갈 자리를 남긴다).
    반환: {'selected': [...], 'dropped': [...], 'used_tokens': int, 'budget': int}
    """
    eligible, dropped = [], []
    for c in chunks:
        if float(c.get("similarity", 0.0)) < min_similarity:
            dropped.append({**c, "reason": "low_similarity"})
            continue
        d = density_score(c.get("text", ""), c.get("tags", []), stats)
        eligible.append({**c, "d_score": d, "tokens": estimate_tokens(c.get("text", ""))})
    eligible.sort(key=lambda c: (-c["d_score"], -float(c.get("similarity", 0.0))))
    selected, used = [], 0
    for c in eligible:
        if used + c["tokens"] <= token_budget:
            selected.append(c)
            used += c["tokens"]
        else:
            dropped.append({**c, "reason": "over_budget"})
    return {"selected": selected, "dropped": dropped, "used_tokens": used, "budget": token_budget}
