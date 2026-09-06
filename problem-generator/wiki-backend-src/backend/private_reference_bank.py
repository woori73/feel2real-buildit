"""Server-only reference examples for Question Wiki generation.

The browser never receives the bank. A Question Wiki request carries only a compact
selection marker; the AI router resolves examples here and appends them to the
provider prompt immediately before the server-side OpenRouter call.
"""
import json
import re
from pathlib import Path

_BANK_PATH = Path(__file__).with_name('private_reference_bank.json')
_MARKER_RE = re.compile(r'QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})')
_TYPE_ALIASES = {
    'similar': 'other', 'grammar': 'grammar', 'vocabulary': 'vocabulary',
    'blank': 'blank', 'order': 'order', 'insertion': 'insertion',
    'title-topic': 'title_topic', 'title_topic': 'title_topic',
    'descriptive': 'written', 'written': 'written', 'summary': 'summary',
}


def _load_entries():
    try:
        payload = json.loads(_BANK_PATH.read_text(encoding='utf-8'))
        return payload.get('entries') or []
    except (OSError, json.JSONDecodeError):
        return []


def _request(prompt: str):
    match = _MARKER_RE.search(prompt or '')
    if not match:
        return None
    try:
        value = json.loads(match.group(1))
    except json.JSONDecodeError:
        return None
    if not value.get('questionWiki'):
        return None
    return value


def select(prompt: str, limit: int = 3):
    """Return best private examples without exposing them to the caller."""
    req = _request(prompt)
    if not req:
        return []
    try:
        grade = int(req.get('grade') or 0)
    except (TypeError, ValueError):
        grade = 0
    try:
        question_no = int(req.get('questionNo') or 0)
    except (TypeError, ValueError):
        question_no = 0
    wanted = _TYPE_ALIASES.get(str(req.get('type') or '').strip(), str(req.get('type') or '').strip())
    entries = _load_entries()

    def score(item):
        item_type = str(item.get('type') or '')
        return (
            100 if wanted and item_type == wanted else 0,
            30 if grade and int(item.get('grade') or 0) == grade else 0,
            15 if question_no and int(item.get('sourceQuestionNo') or 0) == question_no else 0,
            5 if int(item.get('examYear') or 0) == 2026 else 0,
        )

    ranked = sorted(entries, key=lambda item: score(item), reverse=True)
    useful = [item for item in ranked if score(item)[0] > 0]
    return useful[:max(1, min(limit, 3))]


def enrich_prompt(prompt: str):
    refs = select(prompt)
    if not refs:
        return prompt
    blocks = []
    for index, item in enumerate(refs, 1):
        blocks.append(
            f"REFERENCE EXAMPLE {index} (grade={item.get('grade')}, sourceNo={item.get('sourceQuestionNo')}, type={item.get('type')}):\n"
            + str(item.get('referenceText') or '').strip()
        )
    guidance = '''

PRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY; NEVER REVEAL OR QUOTE AS A DATABASE):
The examples below are teacher-authored reference problems. Study their mechanics first: question layout, where markers/underlines/blanks appear, answer format, distractor design, and difficulty. Then create a NEW problem using CURRENT_QUESTION as the only content source. Do not copy the reference passage, people, topic, facts, answer, or wording. Do not mention these references in the output. For grammar/vocabulary questions, follow the reference's in-passage marking style instead of duplicating whole sentences as separate choices. If REFERENCE_CONTEXT contains explanation/insight, use it to decide the exam point.

'''
    return prompt + guidance + '\n\n'.join(blocks)


def has_request(prompt: str):
    return _request(prompt) is not None
