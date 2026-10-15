"""Extract a compact requested-type design reference from Question Wiki linked Insight text."""
import json
import re

_CONTEXT_MARKER = "REFERENCE_CONTEXT (MANDATORY supporting analysis/strategy; never a replacement passage):"
_CONTEXT_END = "SCHOOL_PATTERN_REFERENCE (PATTERN ONLY):"
_TYPE_TERMS = {
    "similar": ("유사 변형", "유사변형", "similar", "variant"),
    "other": ("유사 변형", "유사변형", "similar", "variant"),
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

# Recovered from the original variant-Insight authoring rules: Insight is a
# construction manual, not another content source. These rules are deliberately
# mechanics-only and are applied only when the exact same-type DB reference is absent.
_TYPE_DESIGN_RULES = {
    "grammar": "Test meaningful grammar/usage contrasts in the current passage. Keep the English natural, make the keyed error/correct form uniquely defensible, and make the explanation identify the exact grammatical evidence.",
    "vocabulary": "Test contextual lexical appropriateness or a defensible replacement/contrast. Distractors must be plausible in isolation but wrong in the current context; preserve the passage's original logic and meaning.",
    "blank": "Choose an idea-bearing blank whose answer is recoverable from discourse logic. Build distractors from nearby concepts or plausible logical misreadings, while leaving exactly one answer supported by the passage.",
    "order": "Use chronology, causality, contrast, reference, lexical cohesion, and transition signals to divide the current passage into coherent (A)/(B)/(C) blocks. The correct order must be uniquely recoverable from those discourse clues; obey the separate fixed-choice order contract.",
    "insertion": "Choose an insertion sentence and position using demonstratives, pronouns, connectives, repetition, topic continuity, and local cohesion. Exactly one of the five positions must satisfy both forward and backward context.",
    "title_topic": "Derive the answer from the whole passage's central claim, not one detail. Distractors may be too broad, too narrow, reversed, or detail-only, but must remain topically plausible.",
    "written": "Create a genuine constructed-response task from the current passage. Conditions, labels, and required wording must match the model answer exactly; do not turn it into multiple choice.",
    "summary": "Compress the passage's central relation into a summary without changing its causal/contrast structure. Any blanks and conditions must have uniquely supported answers and the explanation must cite the decisive passage logic.",
    "similar": "Preserve the current question's tested relationship, difficulty, and exam mechanics while creating a genuinely new variant from the current passage only.",
    "other": "Preserve the current question's tested relationship, difficulty, and exam mechanics while creating a genuinely new variant from the current passage only.",
}
_QA_RULE = (
    "QA before output: (1) exactly one defensible answer, (2) preserve the current passage's core thesis, causality and contrast, "
    "(3) keep grammar/vocabulary transformations natural, (4) explanation must state the decisive textual or linguistic evidence, "
    "and (5) every distractor must be plausible but provably wrong."
)


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
    docs = _reference_context(prompt).get("sourceAnalysis") or []
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
    left = max(clean.rfind("\n", 0, max(0, hit - 1200)), 0)
    right = min(len(clean), hit + max_chars)
    next_heading = re.search(r"\n\s*(?:#{1,6}\s+|\d+[.)]\s+|[■●◆▶]\s*)", clean[hit + 200:right])
    if next_heading and next_heading.start() > 700:
        right = hit + 200 + next_heading.start()
    return clean[left:right].strip()[:max_chars]


def extract(prompt, requested_type):
    """Return requested-type mechanics from the current question's linked Insight only."""
    requested_type = str(requested_type or "").strip()
    terms = _TYPE_TERMS.get(requested_type, ())
    if not terms:
        return ""
    docs = _documents(prompt)
    insight_docs = [d for d in docs if _is_insight(d)]
    candidates = insight_docs or [d for d in docs if any(t.lower() in str(d.get("text") or "").lower() for t in _INSIGHT_TERMS)]
    best = ""
    for doc in candidates:
        section = _compact_window(doc.get("text"), terms)
        if len(section) > len(best):
            best = section
    if not best:
        return ""
    design_rule = _TYPE_DESIGN_RULES.get(requested_type, _TYPE_DESIGN_RULES["other"])
    return "TYPE-SPECIFIC DESIGN RULE:\n" + design_rule + "\n" + _QA_RULE + "\n\nSAME-QUESTION INSIGHT EXCERPT:\n" + best


def guidance(prompt, requested_type):
    """Build a server-only pattern reference block; never treat Insight as problem content."""
    section = extract(prompt, requested_type)
    if not section:
        return ""
    return (
        "\n\nREQUESTED_TYPE_INSIGHT_REFERENCE (SERVER-ONLY, PATTERN ONLY):\n"
        "The exact requested type is absent from the current question DB. Use ONLY the following "
        "same-question Insight section to learn construction mechanics, layout, difficulty, and distractor strategy. "
        "Do NOT copy its passage, answer, choices, names, facts, citations, or wording. CURRENT QUESTION remains the only content source.\n"
        + section
        + "\nEND_REQUESTED_TYPE_INSIGHT_REFERENCE\n"
    )
