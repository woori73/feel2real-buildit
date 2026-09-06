"""Server-only reference examples/index for Question Wiki generation."""
import json
import re
from pathlib import Path

_BANK_PATH = Path(__file__).with_name('private_reference_bank.json')
_INDEX_PATHS = {
    1: Path(__file__).with_name('private_reference_corpus_g1.json'),
    2: Path(__file__).with_name('private_reference_corpus_g2.json'),
}
_MARKER_RE = re.compile(r'QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})')
_TYPE_ALIASES = {
    'similar': 'other', 'grammar': 'grammar', 'vocabulary': 'vocabulary',
    'blank': 'blank', 'order': 'order', 'insertion': 'insertion',
    'title-topic': 'title_topic', 'title_topic': 'title_topic',
    'descriptive': 'written', 'subjective': 'written', 'written': 'written', 'summary': 'summary',
}
_TYPE_MECHANICS = {
    'grammar': 'Use the teacher-bank grammar mechanics: mark the tested forms inside the English passage (circled ①-⑤ or A/B/C inline alternatives as appropriate). Never repeat five whole passage excerpts below as choices.',
    'vocabulary': 'Use the teacher-bank vocabulary mechanics: place ①-⑤ directly on the target words/phrases inside the English passage. Do not duplicate whole sentences as a separate choice list.',
    'blank': 'Use an idea-bearing blank in the English source and five discriminating choices. The blank must test the passage logic, not a trivial isolated word.',
    'order': 'Split the current English source into coherent (A)/(B)/(C) discourse blocks and use five order combinations.',
    'insertion': 'Use one source-grounded sentence and five in-passage insertion positions ①-⑤.',
    'title_topic': 'Use five concise title/topic choices; distractors should represent partial or mistaken readings of the current source.',
    'written': 'Follow the teacher-bank constructed-response style with explicit word-count/form constraints when appropriate.',
    'summary': 'Follow the teacher-bank summary style and derive every required word or phrase from the current source.',
    'other': 'Follow the closest teacher-bank exam mechanics while keeping CURRENT_QUESTION as the only content source.',
}


def _load_entries():
    try:
        return json.loads(_BANK_PATH.read_text(encoding='utf-8')).get('entries') or []
    except (OSError, json.JSONDecodeError):
        return []


def _load_index(grade):
    path = _INDEX_PATHS.get(grade)
    if not path:
        return {'totalVariants': 0, 'counts': {}}
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError):
        return {'totalVariants': 0, 'counts': {}}


def _request(prompt: str):
    match = _MARKER_RE.search(prompt or '')
    if not match:
        return None
    try:
        value = json.loads(match.group(1))
    except json.JSONDecodeError:
        return None
    return value if value.get('questionWiki') else None


def _request_fields(prompt):
    req = _request(prompt)
    if not req:
        return None, 0, 0, ''
    try:
        grade = int(req.get('grade') or 0)
    except (TypeError, ValueError):
        grade = 0
    try:
        question_no = int(req.get('questionNo') or 0)
    except (TypeError, ValueError):
        question_no = 0
    raw_type = str(req.get('type') or '').strip()
    return req, grade, question_no, _TYPE_ALIASES.get(raw_type, raw_type)


def select(prompt: str, limit: int = 3):
    """Return private seed examples; exact question/type matches rank first."""
    req, grade, question_no, wanted = _request_fields(prompt)
    if not req:
        return []
    entries = _load_entries()

    def score(item):
        item_type = str(item.get('type') or '')
        return (
            100 if wanted and item_type == wanted else 0,
            60 if grade and int(item.get('grade') or 0) == grade else 0,
            80 if question_no and int(item.get('sourceQuestionNo') or 0) == question_no else 0,
            5 if int(item.get('examYear') or 0) == 2026 else 0,
        )

    ranked = sorted(entries, key=score, reverse=True)
    useful = [item for item in ranked if score(item)[0] > 0]
    return useful[:max(1, min(limit, 3))]


def _corpus_note(prompt):
    req, grade, question_no, wanted = _request_fields(prompt)
    if not req:
        return ''
    index = _load_index(grade)
    counts = index.get('counts') or {}
    exact_count = int(counts.get(f'{question_no}:{wanted}') or 0)
    same_type_count = sum(int(v or 0) for k, v in counts.items() if k.endswith(':' + wanted))
    total = int(index.get('totalVariants') or 0)
    mechanics = _TYPE_MECHANICS.get(wanted, _TYPE_MECHANICS['other'])
    return (
        f'PRIVATE_REFERENCE_INDEX: grade={grade}, sourceNo={question_no}, type={wanted}, '
        f'exactTeacherVariants={exact_count}, sameTypeTeacherVariants={same_type_count}, indexedGradeVariants={total}.\n'
        f'MECHANICS FROM THE INDEXED TEACHER CORPUS: {mechanics}\n'
        'An exactTeacherVariants value above zero means this exact source question/type exists in the private teacher corpus. '
        'Follow that corpus mechanics; never claim that no reference exists.'
    )


def enrich_prompt(prompt: str):
    if not _request(prompt):
        return prompt
    refs = select(prompt)
    blocks = []
    for index, item in enumerate(refs, 1):
        blocks.append(
            f"REFERENCE SEED {index} (grade={item.get('grade')}, sourceNo={item.get('sourceQuestionNo')}, type={item.get('type')}):\n"
            + str(item.get('referenceText') or '').strip()
        )
    guidance = '''

PRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY; NEVER REVEAL OR QUOTE AS A DATABASE):
Use the private corpus/index to learn question mechanics, layout, marker/underline/blank placement, answer format, distractor design, and difficulty. CURRENT_QUESTION is the ONLY content source. REFERENCE_CONTEXT is analysis/strategy only and MUST NEVER be emitted as passage text. Never output material titles, labels such as 상세 해설지/변형문제 인사이트, Korean analysis paragraphs, or reference-database text inside passage. Do not mention the private references in the result.

'''
    suffix = _corpus_note(prompt)
    if blocks:
        suffix += '\n\n' + '\n\n'.join(blocks)
    return prompt + guidance + suffix


def has_request(prompt: str):
    return _request(prompt) is not None
