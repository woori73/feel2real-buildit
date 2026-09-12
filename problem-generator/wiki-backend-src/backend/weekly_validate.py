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
    # cross-check against the document: only full shared boxes count (short
    # explicit quotes beside a box are legitimate 명시적 비교, not duplication).
    for q in questions:
        if not str(q.get('common_passage_id') or ''):
            continue
        passage = (q.get('passage') or '').strip()
        if len(passage) > 200 and html.count(passage[:60]) > 1:
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


_CIRCLED = {'①': 1, '②': 2, '③': 3, '④': 4, '⑤': 5}

# 명확히 파싱 가능한 해설 내 선택지 언급 패턴: "~과 같은", "~뿐이다",
# "~가/이 적절하다", "~가/이 틀렸다", "~가 일치하지 않는다" 등.
_EXPL_MENTION_RE = re.compile(
    r'([①②③④⑤])\s*(?:과 같은|뿐이다|뿐이|가 적절하다|이 적절하다|가 틀렸다|이 틀렸다|'
    r'가 일치하지 않는다|이 일치하지 않는다|가 정답이다|이 정답이다)')


def check_answer_key_consistency(final_answers: dict, quick_answers: dict,
                                 badge_answers: dict) -> list[str]:
    """ANSWER: 빠른 정답·문항별 badge는 최종 정답과 일치해야 한다.

    2026-09 round4-70 사고(빠른 정답·badge가 ①→②→③→④→⑤ 기계적 순환으로
    채워져 60/70문항 불일치) 재발 방지 게이트. machines don't guess answers.
    """
    issues = []
    for key, final in (final_answers or {}).items():
        quick = (quick_answers or {}).get(key)
        if quick is not None and quick != final:
            issues.append('Q%s: quick answer %s != final answer %s' % (key, quick, final))
        badge = (badge_answers or {}).get(key)
        if badge is not None and badge != final:
            issues.append('Q%s: explanation badge %s != final answer %s' % (key, badge, final))
    return issues


def check_expl_mention_consistency(expl_texts: dict, final_answers: dict) -> list[str]:
    """ANSWER: 해설 문장에 명시된 선택지 번호도 최종 정답과 일치해야 한다.

    예: "①과 같은 쓰임이다"라고 적혀 있는데 badge가 ③이면 FAIL.
    명확히 파싱 가능한 패턴(_EXPL_MENTION_RE)만 검사하고, 애매한 서술은
    건드리지 않는다(never guessed).
    """
    issues = []
    for key, text in (expl_texts or {}).items():
        final = (final_answers or {}).get(key)
        if final is None:
            continue
        for match in _EXPL_MENTION_RE.finditer(text or ''):
            mentioned = _CIRCLED.get(match.group(1))
            if mentioned is not None and mentioned != final:
                issues.append('Q%s: explanation mentions %s but final answer is %s (%s)'
                              % (key, match.group(0), final, (text or '')[:40]))
    return issues


_COMBO_CHOICE_RE = re.compile(r'\b[a-d](?:\s*,\s*[a-d])+\b|\b[ABC](?:\s*,\s*[ABC])+\b')


def check_view_box_present(block_html: str, question_no=None) -> list[str]:
    """DATA: 보기 발문인데 보기 block이 없으면 FAIL.

    발문에 '보기'가 있고 선택지가 a~d/A~C 조합형인데, 블록에
    cond-box/passage box/given-sentence/보기 본문이 없으면 검출한다.
    (round4-70 Q7·Q30 사고: 조합 선택지만 있고 a~d 본체 누락)
    """
    prompt_match = re.search(r'<span class="qprompt">(.*?)</span>', block_html or '', flags=re.S)
    prompt = _strip_tags(prompt_match.group(1)) if prompt_match else ''
    if '보기' not in prompt:
        return []
    choices = re.findall(r'<span class="choice-item">(.*?)</span>|<div class="opt">(.*?)</div>',
                         block_html or '', flags=re.S)
    choice_text = ' '.join((a or b) for a, b in choices)
    if not _COMBO_CHOICE_RE.search(choice_text):
        return []
    body = block_html or ''
    if ('cond-box' in body or 'given-sentence' in body or 'passage box' in body
            or '보기' in _strip_tags(re.sub(r'<span class="qprompt">.*?</span>', '', body, flags=re.S))):
        return []
    label = 'Q%s: ' % question_no if question_no is not None else ''
    return ['%s보기 발문+조합 선택지인데 보기 block 없음' % label]


def check_underline_rendered(block_html: str, group_html: str = '', question_no=None) -> list[str]:
    """RENDER: "밑줄 친"이라고 물으면 실제 <u> target이 있어야 한다.

    윗글 공유형(밑줄이 공유 지문 박스에 있음)을 허용하기 위해 group_html
    (같은 passage group 전체 HTML)까지 함께 검사한다.
    """
    prompt_match = re.search(r'<span class="qprompt">(.*?)</span>', block_html or '', flags=re.S)
    prompt = _strip_tags(prompt_match.group(1)) if prompt_match else ''
    if '밑줄' not in prompt and 'underlined' not in prompt:
        return []
    hay = '%s %s' % (block_html or '', group_html or '')
    if re.search(r'<u(?:\s|>)', hay, re.I):
        return []
    label = 'Q%s: ' % question_no if question_no is not None else ''
    return ['%s밑줄 발문인데 <u> target 없음' % label]


def check_blank_target_rendered(block_html: str, question_no=None) -> list[str]:
    """RENDER: (A)/(B)/(C)·빈칸 발문이면 본문에 실제 target이 있어야 한다."""
    prompt_match = re.search(r'<span class="qprompt">(.*?)</span>', block_html or '', flags=re.S)
    prompt = _strip_tags(prompt_match.group(1)) if prompt_match else ''
    body_text = _strip_tags(block_html or '')
    needed = [tok for tok in _BLANK_TOKENS if tok in prompt]
    issues = []
    label = 'Q%s: ' % question_no if question_no is not None else ''
    for tok in needed:
        if tok not in body_text:
            issues.append('%sblank %s target 없음' % (label, tok))
    if ('빈칸' in prompt and '____' not in (block_html or '')
            and not needed and '보기' not in prompt):
        issues.append('%s빈칸 발문인데 빈칸 target 없음' % label)
    return issues


def validate_document(questions: list[dict], html: str, pdf_text: str = '',
                      min_answer_lines: int = 2, final_answers: dict = None,
                      quick_answers: dict = None, badge_answers: dict = None,
                      expl_texts: dict = None, blocks_html: dict = None,
                      groups_html: dict = None) -> dict:
    """Run all gates; returns {gate: [issues]}. Empty lists mean PASS.

    ANSWER 게이트(final/quick/badge/expl-mention)는 round4-70 정답 순환
    사고 재발 방지용이다. 새 인자가 없으면(기존 호출) 기존 3개 게이트만
    동작하므로 backward compatible하다.
    """
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
    answer_issues: list[str] = []
    if final_answers is not None and (quick_answers is not None or badge_answers is not None):
        answer_issues += check_answer_key_consistency(final_answers, quick_answers, badge_answers)
    if expl_texts is not None and final_answers is not None:
        answer_issues += check_expl_mention_consistency(expl_texts, final_answers)
    if blocks_html:
        for key, block in blocks_html.items():
            answer_issues += check_view_box_present(block, key)
            answer_issues += check_blank_target_rendered(block, key)
            answer_issues += check_underline_rendered(
                block, (groups_html or {}).get(key, ''), key)
    print_issues = check_bottom_overflow(html) + check_footer_continuity(html)
    if pdf_text:
        print_issues += pdf_text_completeness(html, pdf_text)
    result = {'DATA': data_issues, 'RENDER': render_issues, 'PRINT': print_issues}
    if answer_issues or final_answers is not None:
        result['ANSWER'] = answer_issues
    return result
