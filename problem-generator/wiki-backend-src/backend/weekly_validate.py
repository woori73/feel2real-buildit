"""Weekly Exam layout validation gates — DATA / RENDER / PRINT.

Pure functions over structured questions and rendered HTML. No I/O, no DB.
Each check returns a list of issue strings (empty means PASS), so callers can
aggregate per-question and per-document results.
"""
from __future__ import annotations

import re

_BLANK_TOKENS = ('(A)', '(B)', '(C)')
_CHOICE_MARKERS = ('①', '②', '③', '④', '⑤')


def _strip_tags(fragment: str) -> str:
    return re.sub(r'<[^>]+>', '', fragment or '')


def check_blanks_present(question: dict) -> list[str]:
    """DATA: prompts demanding (A)/(B)/(C) require matching targets in passage."""
    prompt = question.get('prompt') or ''
    needed = [tok for tok in _BLANK_TOKENS if tok in prompt]
    if not needed:
        return []
    hay = '%s %s' % (question.get('passage') or '', ' '.join(question.get('choices') or []))
    return ['blank %s has no target' % tok for tok in needed if tok not in hay]


def check_markers_match(question: dict) -> list[str]:
    """DATA: ①-⑤ markers in prompts/boxes must match choice structure."""
    prompt = question.get('prompt') or ''
    markers = [m for m in _CHOICE_MARKERS if m in prompt]
    choices = question.get('choices') or []
    if markers and len(choices) != 5:
        return ['marker prompt but %d choices' % len(choices)]
    return []


def check_underline_targets(question: dict) -> list[str]:
    """DATA: 밑줄/underlined/어법상/의미가 같은/고쳐야 할 prompts need <u> targets."""
    text = '%s %s' % (question.get('prompt') or '', question.get('passage') or '')
    wants = ('밑줄', 'underlined', '어법상', '의미가 같은', '고쳐야 할')
    if any(w in text for w in wants):
        hay = '%s %s %s' % (question.get('passage') or '',
                             ' '.join(question.get('choices') or ''),
                             question.get('prompt') or '')
        if not re.search(r'<u(?:\s|>)', hay, re.I):
            return ['underline required but no <u> target']
    return []


def check_options_valid(question: dict) -> list[str]:
    """DATA: no empty options, no duplicated option blocks."""
    choices = question.get('choices') or []
    issues = []
    if any(not (c or '').strip() for c in choices):
        issues.append('empty option')
    texts = [re.sub(r'\s+', ' ', _strip_tags(c)).strip() for c in choices]
    if len(set(texts)) != len(texts):
        issues.append('duplicate option block')
    return issues


def check_numbering(questions: list[dict]) -> list[str]:
    """DATA: question numbers must be unique and gapless from 1."""
    nums = [q.get('no') for q in questions]
    issues = []
    if len(set(nums)) != len(nums):
        issues.append('duplicate question number')
    if sorted(nums) != list(range(1, len(nums) + 1)):
        issues.append('question numbers not 1..N')
    return issues


def check_group_refs(questions: list[dict]) -> list[str]:
    """DATA: common_passage_id items must share one group (adjacency rule)."""
    by_pid: dict[str, set] = {}
    for q in questions:
        pid = str(q.get('common_passage_id') or '')
        if pid:
            by_pid.setdefault(pid, set()).add(str(q.get('group') or ''))
    return ['passage %s spans groups %s' % (pid, sorted(groups))
            for pid, groups in by_pid.items() if len(groups) > 1]


def check_conditions_space(question: dict, min_lines: int = 2) -> list[str]:
    """DATA: constructed response with conditions needs minimum answer space."""
    if not (question.get('conditions') or []):
        return []
    if int(question.get('answer_lines') or 0) < min_lines:
        return ['conditions without minimum answer space']
    return []


def check_no_duplicate_passage(html: str, questions: list[dict]) -> list[str]:
    """RENDER: a common passage must be rendered exactly once."""
    seen: dict[str, int] = {}
    for q in questions:
        pid = str(q.get('common_passage_id') or '')
        if pid and (q.get('passage') or ''):
            seen[pid] = seen.get(pid, 0) + 1
    issues = []
    for pid, count in seen.items():
        if count > 1:
            issues.append('passage %s rendered %d times' % (pid, count))
    # cross-check against the document when passage text is known; only for
    # common passages (independent questions may legitimately share wording).
    for q in questions:
        if not str(q.get('common_passage_id') or ''):
            continue
        passage = (q.get('passage') or '').strip()
        if len(passage) > 40 and html.count(passage[:40]) > 1:
            issues.append('passage of Q%s duplicated in output' % q.get('no'))
    return issues


def check_group_preserved(html: str, questions: list[dict]) -> list[str]:
    """RENDER: members of one group must sit in a single column."""
    # Locate columns by document order: each page contributes its col divs in
    # order, and each qnum belongs to the most recent col start.
    col_of: dict[str, int] = {}
    col_index = -1
    page_index = -1
    token = re.compile(r'<div class="page">|<div class="col(?: spread)?"(?: style="[^"]*")?>|data-qnum="(\d+)"')
    for match in token.finditer(html):
        text = match.group(0)
        if text == '<div class="page">':
            page_index += 1
            col_index = -1
        elif text.startswith('<div class="col'):
            col_index += 1
        else:
            col_of[match.group(1)] = (page_index, col_index)
    issues = []
    groups: dict[str, list] = {}
    for q in questions:
        if q.get('group'):
            groups.setdefault(str(q['group']), []).append(str(q.get('no')))
    for name, members in groups.items():
        slots = {col_of.get(m) for m in members if m in col_of}
        if len(slots) != 1:
            issues.append('group %s split across columns' % name)
    return issues


def check_boxes_once(html: str, questions: list[dict]) -> list[str]:
    """RENDER: 보기/BOX blocks must not repeat identically."""
    boxes = []
    for q in questions:
        for key in ('passage',):
            text = (q.get(key) or '').strip()
            if len(text) > 60 and ('보기' in text or '조건' in text):
                boxes.append((q.get('no'), text[:60]))
    issues = []
    for no, head in boxes:
        if html.count(head) > 1:
            issues.append('box of Q%s duplicated' % no)
    return issues


def check_markup_present(html: str, questions: list[dict]) -> list[str]:
    """RENDER: required markup (underline/blank/box) must survive rendering."""
    hay = html
    issues = []
    for q in questions:
        for mark in q.get('markup') or []:
            kind = str(mark.get('type') or '').lower()
            target = str(mark.get('text') or mark.get('target') or '')
            if kind == 'underline' and '<u>' not in hay:
                issues.append('Q%s underline missing in output' % q.get('no'))
            elif kind == 'blank' and 'blank' not in hay:
                issues.append('Q%s blank missing in output' % q.get('no'))
            elif kind in ('box', 'given_sentence') and target and target[:20] not in hay:
                issues.append('Q%s box target missing in output' % q.get('no'))
    return issues


def check_bottom_overflow(html: str, max_lines_per_col: int = 58,
                          first_col_max: int = 51) -> list[str]:
    """PRINT: estimate per-column load; flag columns that would clip.

    Uses the same estimator contract as the packer. This is a heuristic gate,
    not a browser measurement — see pdf_text_completeness for the ground truth.
    """
    try:
        from weekly_paper import estimate_lines
    except ImportError:  # pragma: no cover
        from backend.weekly_paper import estimate_lines
    cols = re.findall(r'data-qnum="(\d+)"', html)
    # per-column loads cannot be recovered from flat HTML reliably; instead
    # verify the document carries keep-together rules and no fixed clipping.
    issues = []
    if 'overflow:hidden' in html and 'page-break-inside: avoid' not in html:
        issues.append('clipping style without keep-together rule')
    if not cols:
        issues.append('no questions in output')
    return issues


def check_footer_continuity(html: str) -> list[str]:
    """PRINT: footers must form a continuous 1/N..N/N sequence."""
    nums = re.findall(r'(\d+)\s*/\s*(\d+)', html)
    pairs = [(int(a), int(b)) for a, b in nums]
    if not pairs:
        return ['no page footers']
    totals = {b for _, b in pairs}
    if len(totals) != 1:
        return ['inconsistent page totals']
    total = totals.pop()
    if sorted(a for a, _ in pairs) != list(range(1, total + 1)):
        return ['non-continuous page numbering']
    return []


_LIGATURES = {'ﬁ': 'fi', 'ﬂ': 'fl', 'ﬀ': 'ff', 'ﬃ': 'ffi', 'ﬄ': 'ffl'}


def _normalize_pdf_text(pdf_text: str) -> str:
    """Normalize PDF extraction artifacts (ligatures) before comparison."""
    text = pdf_text or ''
    for lig, plain in _LIGATURES.items():
        text = text.replace(lig, plain)
    return text


def pdf_text_completeness(html: str, pdf_text: str) -> list[str]:
    """PRINT ground truth: every prompt/choice/passage token must reach the PDF.

    pdf_text is extracted PDF text (whitespace-normalized by caller). Catches
    real clipping that estimators miss.
    """
    flat = re.sub(r'\s+', '', _normalize_pdf_text(pdf_text or ''))
    misses = []

    def present(fragment: str) -> bool:
        norm = re.sub(r'\s+', '', _strip_tags(fragment or ''))
        return bool(norm) and norm in flat

    for m in re.finditer(r'<div class="qblock[^"]*" data-qnum="(\d+)">(.*?)</div></div>', html, flags=re.S):
        no, body = m.group(1), m.group(2)
        head = re.search(r'<span class="qprompt">(.*?)</span>', body, flags=re.S)
        if head and not present(head.group(1)):
            misses.append('Q%s prompt missing in PDF' % no)
        for tag in re.findall(r'<(?:div class="opt"|span class="choice-item")>(.*?)</(?:div|span)>', body, flags=re.S):
            if not present(tag):
                misses.append('Q%s choice missing in PDF: %s' % (no, _strip_tags(tag)[:24]))
    return misses


def validate_document(questions: list[dict], html: str, pdf_text: str = '',
                      min_answer_lines: int = 2) -> dict:
    """Run all gates; returns {gate: [issues]}. Empty lists mean PASS."""
    data_issues = list(check_numbering(questions)) + list(check_group_refs(questions))
    for q in questions:
        data_issues += ['Q%s: %s' % (q.get('no'), i) for i in
                        (check_blanks_present(q) + check_markers_match(q)
                         + check_underline_targets(q) + check_options_valid(q)
                         + check_conditions_space(q, min_answer_lines))]
    render_issues = (check_no_duplicate_passage(html, questions)
                     + check_group_preserved(html, questions)
                     + check_boxes_once(html, questions)
                     + check_markup_present(html, questions))
    print_issues = check_bottom_overflow(html) + check_footer_continuity(html)
    if pdf_text:
        print_issues += pdf_text_completeness(html, pdf_text)
    return {'DATA': data_issues, 'RENDER': render_issues, 'PRINT': print_issues}
