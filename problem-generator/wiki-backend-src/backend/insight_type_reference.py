"""Extract a compact requested-type design reference from Question Wiki linked Insight text."""
import json
import re

_CONTEXT_MARKER = "REFERENCE_CONTEXT (MANDATORY supporting analysis/strategy; never a replacement passage):"
_CONTEXT_END = "SCHOOL_PATTERN_REFERENCE (PATTERN ONLY):"

_TYPE_TERMS = {
    "similar": ("유사 변형", "유사변형", "similar", "variant"),
    "grammar": ("어법", "문법", "grammar", "usage"),
    "vocabulary": ("어휘", "vocabulary", "word"),
    "blank": ("빈칸", "blank", "gap"),
    "order": ("글의 순서", "문단 순서", "순서", "paragraph order", "order"),
    "insertion": ("문장 삽입", "삽입", "sentence insertion", "insertion"),
    "title_topic": ("제목/주제", "제목", "주제", "title", "topic", "main idea"),
    "written": ("서술형", "주관식", "constructed response", "written response", "subjective"),
    "summary": ("요약", "summary"),
}

_INSIGHT_TERMS = ("변형문제 인사이트", "변형 문제 인사이트", "insight", "변형문제", "출제 인사이트")


def _reference_context(prompt):
    text = str(prompt or "")
    start = text.find(_CONTEXT_MARKER)
    if start < 0:
        return {}
    start += len(_CONTEXT_MARKER)
    end = text.find(_CONTEXT_END, start)
    raw = text[start:end if end >= 0 else len(text)].strip()
    try:
        value = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def _documents(prompt):
    context = _reference_context(prompt)
    docs = context.get("sourceAnalysis") or []
    return [d for d in docs if isinstance(d, dict) and str(d.get("text") or "").strip()]


def _is_insight(doc):
    identity = " ".join(str(doc.get(k) or "") for k in ("label", "kindLabel", "title")).lower()
    return any(term.lower() in identity for term in _INSIGHT_TERMS)


def _compact_window(text, terms, max_chars=6000):
    clean = re.sub(r"\r\n?", "\n", str(text or "")).strip()
    if not clean:
        return ""
    lower = clean.lower()
    hits = [lower.find(term.lower()) for term in terms if lower.find(term.lower()) >= 0]
    if not hits:
        return ""
    hit = min(hits)
    # Prefer a section-like slice around the requested type instead of sending the whole Insight HTML/text.
    left = max(clean.rfind("\n", 0, max(0, hit - 1200)), 0)
    right = min(len(clean), hit + max_chars)
    next_heading = re.search(r"\n\s*(?:#{1,6}\s+|\d+[.)]\s+|[■●◆▶]\s*)", clean[hit + 200:right])
    if next_heading and next_heading.start() > 700:
        right = hit + 200 + next_heading.start()
    return clean[left:right].strip()[:max_chars]


def extract(prompt, requested_type):
    """Return requested-type mechanics from the current question's linked Insight only."""
    terms = _TYPE_TERMS.get(str(requested_type or "").strip(), ())
    if not terms:
        return ""
    docs = _documents(prompt)
    insight_docs = [d for d in docs if _is_insight(d)]
    # If labels are generic, permit a sourceAnalysis document only when its body explicitly looks like an Insight.
    candidates = insight_docs or [d for d in docs if any(t.lower() in str(d.get("text") or "").lower() for t in _INSIGHT_TERMS)]
    best = ""
    for doc in candidates:
        section = _compact_window(doc.get("text"), terms)
        if len(section) > len(best):
            best = section
    return best
