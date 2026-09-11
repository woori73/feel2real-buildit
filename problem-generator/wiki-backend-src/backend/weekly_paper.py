"""Weekly Exam A4 paper/explanation renderer — structured questions to canonical HTML.

Visual contract is copied verbatim from Production round-1 FINAL
(final_deliverables id=1 student paper / id=2 explanation), which shares the
FORMAT MASTER lineage with rounds 2 and 3 (round-3 verification MD: same A4
2-column system, qblock/passage/choices markup, page footer). No new design is
invented here: CSS, header/footer/run-header blocks, choice layouts and the
explanation quick-answer system are reproduced from those FINAL artifacts.

Input is trusted pre-authored HTML fragments (same trust boundary as
register_weekly_exam_draft payloads). The renderer assembles pages, it never
rewrites question content.
"""
from __future__ import annotations

import html as _html
import math
import re

PAPER_CSS = """@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; }
body { margin:0; padding:0; font-family: "Noto Sans KR","Malgun Gothic","Nanum Gothic",sans-serif; font-size: 9.5pt; line-height: 1.42; color:#111; }
.page { width: 210mm; height: 297mm; padding: 12mm 11mm 10mm 11mm; position: relative; page-break-after: always; display:flex; flex-direction:column; overflow:hidden; }
.page:last-child { page-break-after: auto; }
.cols { display: flex; gap: 7mm; flex: 1 1 auto; min-height:0; }
.col { width: calc(50% - 3.5mm); overflow: hidden; }
.exam-header { border: 1.4pt solid #000; margin-bottom: 4mm; }
.exam-header table { width:100%; border-collapse: collapse; font-size: 9.5pt; }
.exam-header td, .exam-header th { border: 0.7pt solid #444; padding: 2mm 2.5mm; }
.exam-header .title-row td { text-align:center; font-size: 14pt; font-weight:700; padding: 3mm; letter-spacing: 1px; }
.exam-header .sub-row td { text-align:center; font-size: 9.5pt; color:#333; }
.exam-header .info-row td { font-size: 9pt; }
.exam-header .label { font-weight:700; background:#f2f2f2; width: 16mm; text-align:center; }
.run-header { display:flex; justify-content:space-between; align-items:baseline; border-bottom: 0.9pt solid #000; padding-bottom: 1.5mm; margin-bottom: 3mm; font-size: 9pt; color:#333; }
.section-title { font-size: 11pt; font-weight:700; margin: 3mm 0 2mm 0; padding-bottom:1mm; border-bottom: 1.2pt solid #000; }
.section-title.cont { font-size: 10pt; color:#333; }
.qblock { margin-bottom: 3.2mm; page-break-inside: avoid; }
.qblock.group-end { margin-bottom: 6mm; }
.col.spread .qblock { margin-bottom: calc(3.2mm + var(--xgap, 0mm)); }
.col.spread .qblock.group-end { margin-bottom: calc(6mm + var(--xgap, 0mm)); }
.qhead { display:flex; gap: 1.3mm; align-items:baseline; }
.qnum { font-weight:700; flex-shrink:0; }
.qprompt { font-weight:500; }
.passage { margin: 1mm 0 1mm 4.6mm; }
.passage.box { border: 0.7pt solid #666; padding: 1.5mm 2mm; background:#fbfbfb; line-height:1.55; }
.passage.dialog { border: 0.7pt solid #666; padding: 1.5mm 2mm; background:#fbfbfb; line-height:1.55; }
.choices.row { margin: 1mm 0 0 4.6mm; }
.choice-item { display: inline-block; white-space: nowrap; margin: 0 3.5mm 0.6mm 0; }
.choices.vertical .opt { margin: 0.6mm 0 0 4.6mm; }
.cond-box { margin: 1.3mm 0 0 4.6mm; border:0.7pt solid #666; padding: 1mm 2mm; font-size: 9pt; background:#fbfbfb; }
.cond-label { font-weight:700; margin-right: 1mm; }
.answer-area { margin: 1.3mm 0 0 4.6mm; }
.answer-line { border-bottom: 0.6pt solid #999; height: 5.6mm; }
.blank { display:inline-block; min-width:14mm; border-bottom:0.7pt solid #111; }
.given-sentence { display:block; border:0.7pt solid #666; padding: 1mm 2mm; margin: 1mm 0; background:#fbfbfb; }
.marker { font-weight:700; }
u { text-underline-offset: 1.5px; }
.page-footer { position:absolute; bottom: 4mm; left:0; right:0; text-align:center; font-size: 8pt; color:#555; }"""

EXPL_CSS = """@page { size: A4 portrait; margin: 16mm 14mm; }
body { font-family: "Noto Sans KR","Malgun Gothic",sans-serif; font-size: 10.5pt; line-height:1.5; color:#111; max-width: 190mm; margin:0 auto; }
h1 { font-size: 15pt; text-align:center; margin-bottom: 2mm; }
.sub { text-align:center; color:#444; margin-bottom:8mm; font-size:10pt; }
h2 { font-size: 12.5pt; border-bottom: 1.4pt solid #000; padding-bottom:1.5mm; margin-top:9mm; }
.qrow { margin: 4mm 0; padding-bottom: 3mm; border-bottom: 0.5pt dashed #ccc; page-break-inside: avoid; }
.qnum { font-weight:700; }
.ans { display:inline-block; margin-left:2mm; padding: 0.5mm 2.5mm; background:#eef4ff; border:0.7pt solid #6b8fd1; border-radius:3px; font-weight:700; }
.expl { margin: 1.5mm 0 0 0; color:#333; font-size:9.8pt; }
.meta { margin-top:1mm; font-size:8.7pt; color:#888; }
.status { font-size:8.5pt; font-weight:700; }
.status.VERIFIED { color:#1b7a3d; }
.status.REVIEW { color:#b5651d; }
table.summary { border-collapse:collapse; margin: 4mm 0 8mm 0; font-size:9.5pt; }
table.summary td, table.summary th { border:0.6pt solid #999; padding:1.5mm 3mm; text-align:center; }
table.quick { border-collapse:collapse; margin: 4mm 0; font-size:9.5pt; }
table.quick td, table.quick th { border:0.6pt solid #999; padding:1.2mm 2.6mm; text-align:center; }"""

_TAG_RE = re.compile(r'<[^>]+>')
_WS_RE = re.compile(r'\s+')

# 하단 여백 흡수: 한 column에 남는 slack을 문항 간격으로 분배한다. 절대 금지인
# 글씨 축소·행간 압축 대신 사용하며, 문항당 최대 2.2mm까지만 넓힌다.
_MAX_XGAP_MM = 2.2
_MM_PER_LINE = 4.8
_CHARS_PER_LINE = 62
_LINES_PER_COL_FIRST = 48
_LINES_PER_COL = 56


def _text_len(fragment: str) -> int:
    return len(_WS_RE.sub(' ', _TAG_RE.sub(' ', fragment or '')))


def apply_markup(text: str, markup: list) -> str:
    """Structured markup applier (mirror of main._structured_csat_text).

    Plain text is escaped first; marks then wrap exact target substrings so the
    relation between marker and target survives rendering. Supported types:
    underline, bold, blank, box, given_sentence, and marker (symbol such as
    ①-⑤/ⓐⓑ/(A)(B)(C) linked to its target via data attributes).
    Each mark is {'type': ..., 'text'|'target': ..., 'field': ...}; only marks
    whose field matches are applied. Unknown types are ignored (never guessed).

    Compatibility: with an empty markup list the input is returned unchanged,
    preserving the legacy contract where questions arrive as pre-authored HTML
    fragments. Markup mode expects plain text (same trust rule as
    main._structured_csat_text) and escapes before wrapping.
    """
    marks = [m for m in (markup or []) if isinstance(m, dict)]
    if not marks:
        return str(text or '')
    rendered = _html.escape(str(text or ''))
    for mark in marks:
        target = _html.escape(str(mark.get('text') or mark.get('target') or ''))
        if not target or target not in rendered:
            continue
        kind = str(mark.get('type') or '').lower()
        if kind == 'underline':
            replacement = '<u>%s</u>' % target
        elif kind in ('bold', 'emphasis'):
            replacement = '<strong>%s</strong>' % target
        elif kind == 'blank':
            replacement = '<span class="blank">%s</span>' % target
        elif kind in ('box', 'given_sentence'):
            replacement = '<span class="given-sentence">%s</span>' % target
        elif kind == 'marker':
            symbol = _html.escape(str(mark.get('symbol') or ''))
            replacement = ('<span class="marker" data-symbol="%s" data-target="%s">%s</span>'
                           % (symbol, target, target))
        else:
            continue
        rendered = rendered.replace(target, replacement, 1)
    return rendered


def estimate_lines(question: dict) -> int:
    """Estimate printed lines of one question block (prompt + passage + choices)."""
    lines = 2  # qhead + spacing
    passage = question.get('passage') or ''
    if passage:
        breaks = passage.count('<br>')
        lines += 2 + breaks + math.ceil(_text_len(passage) / _CHARS_PER_LINE)
    for choice in question.get('choices') or []:
        if (question.get('layout') or 'row') == 'vertical':
            lines += 1 + math.ceil(_text_len(choice) / _CHARS_PER_LINE)
    if (question.get('layout') or 'row') == 'row' and question.get('choices'):
        lines += 1
    if question.get('section'):
        lines += 3  # section title block
    conditions = question.get('conditions') or []
    if conditions or question.get('answer_lines'):
        lines += 2 + len(conditions) + max(int(question.get('answer_lines') or 0),
                                           _DEFAULT_MIN_ANSWER_LINES)
    return lines + 1


# Default minimum answer lines for constructed response when no profile is
# passed. Mirrors school_layout.DEFAULT_PROFILE['constructed_min_answer_lines']
# without importing it, so this module stays importable in every context.
_DEFAULT_MIN_ANSWER_LINES = 2


def _dedupe_common_passages(questions: list[dict]) -> list[dict]:
    """Mirror of the CSAT common_passage_id rule: the first question carrying a
    passage renders it; later questions with the same id reference it instead
    of duplicating the output. Returns copies; inputs are unchanged."""
    marked = [dict(q) for q in questions]
    seen: set[str] = set()
    for q in marked:
        pid = str(q.get('common_passage_id') or '')
        if not pid:
            continue
        if pid in seen:
            q['passage'] = ''
            q['passage_ref'] = True
        else:
            seen.add(pid)
    return marked


def _render_question(question: dict, min_answer_lines: int = _DEFAULT_MIN_ANSWER_LINES) -> str:
    parts = []
    if question.get('section'):
        parts.append('<div class="section-title">%s</div>' % question['section'])
    classes = 'qblock group-end' if question.get('_group_end') else 'qblock'
    parts.append('<div class="%s" data-qnum="%s">' % (classes, _html.escape(str(question['no']))))
    markup = question.get('markup') or []
    parts.append('  <div class="qhead"><span class="qnum">%s.</span><span class="qprompt">%s</span></div>'
                 % (_html.escape(str(question['no'])),
                    apply_markup(question.get('prompt'), [m for m in markup if _mark_field(m) in ('prompt', 'instruction')])))
    if question.get('passage'):
        cls = question.get('passage_class') or 'prose'
        parts.append('<div class="passage %s">%s</div>'
                     % (cls, apply_markup(question.get('passage'),
                                          [m for m in markup if _mark_field(m) == 'passage'])))
    choices = question.get('choices') or []
    if choices:
        if (question.get('layout') or 'row') == 'vertical':
            opts = ''.join('<div class="opt">%s</div>' % apply_markup(
                choice, [m for m in markup if _mark_field(m) == 'choices']) for choice in choices)
            parts.append('<div class="choices vertical">%s</div>' % opts)
        else:
            items = ''.join('<span class="choice-item">%s</span>' % apply_markup(
                choice, [m for m in markup if _mark_field(m) == 'choices']) for choice in choices)
            parts.append('<div class="choices row">%s</div>' % items)
    conditions = question.get('conditions') or []
    answer_lines = int(question.get('answer_lines') or 0)
    if conditions or answer_lines:
        conds = ''.join('<div>%s</div>' % apply_markup(
            cond, [m for m in markup if _mark_field(m) == 'conditions']) for cond in conditions)
        parts.append('<div class="cond-box"><span class="cond-label">&lt;조건&gt;</span>%s</div>' % conds)
        parts.append('<div class="answer-area">%s</div>' % ''.join(
            '<div class="answer-line"></div>'
            for _ in range(max(answer_lines, min_answer_lines))))
    parts.append('</div>')
    return ''.join(parts)


def _mark_field(mark: dict) -> str:
    return str((mark or {}).get('field') or 'passage')


def _mark_groups(questions: list[dict]) -> list[dict]:
    """같은 group이 연속되면 하나의 시각적 unit으로 묶는다. unit의 마지막 문항에만
    group-end 여백을 주어 다음 독립 문항과 구분한다. 입력 dict는 변경하지 않고
    복사본에 표시한다."""
    marked = [dict(q) for q in questions]
    for i, q in enumerate(marked):
        nxt = marked[i + 1] if i + 1 < len(marked) else None
        q['_group_end'] = bool(q.get('group')) and (
            nxt is None or nxt.get('group') != q.get('group'))
    return marked


def _pack_columns(questions: list[dict]) -> list[tuple[list[dict], float, list[dict], float]]:
    """Pack questions into pages of two columns, left -> right -> next page.
    A question block never splits across columns (page-break-inside: avoid).
    같은 group unit은 들어갈 때 함께 이동한다. 각 column의 남는 slack은 문항 간격으로
    분배한다(xgap, 문항당 최대 2.2mm). 반환: [(left, left_xgap, right, right_xgap)]."""
    marked = _mark_groups(questions)
    units: list[list[dict]] = []
    for q in marked:
        if q.get('group') and units and units[-1][0].get('group') == q['group']:
            units[-1].append(q)
        else:
            units.append([q])
    pages: list[tuple[list[dict], float, list[dict], float]] = []
    current: list[list[dict]] = [[], []]
    budgets = [_LINES_PER_COL_FIRST, _LINES_PER_COL_FIRST]
    used = [0, 0]
    col = 0

    def flush_page():
        pages.append(_spread(current[0], budgets[0], used[0]) +
                     _spread(current[1], budgets[1], used[1]))

    for unit in units:
        need = sum(estimate_lines(q) for q in unit)
        if used[col] + need > budgets[col] and used[col] > 0:
            if col == 0:
                col = 1
            else:
                flush_page()
                current, used, col = [[], []], [0, 0], 0
                budgets = [_LINES_PER_COL, _LINES_PER_COL]
                # 새 페이지 첫 column에도 unit이 통째로 안 들어가면 그대로 둔다(절대 분리 금지)
        current[col].extend(unit)
        used[col] += need
    if current[0] or current[1]:
        flush_page()
    return pages


def _spread(items: list[dict], budget: int, used: int) -> tuple[list[dict], float]:
    slack = budget - used
    if not items or slack < 3:
        return (items, 0.0)
    xgap = round(min(_MAX_XGAP_MM, slack * _MM_PER_LINE / len(items) / 4), 1)
    return (items, xgap)


def _render_col(items: list[dict], xgap: float, min_answer_lines: int) -> str:
    rendered = ''.join(_render_question(q, min_answer_lines) for q in items)
    if xgap > 0:
        return '<div class="col spread" style="--xgap:%smm">%s</div>' % (xgap, rendered)
    return '<div class="col">%s</div>' % rendered


def render_student_paper(*, title_row: str, sub_row: str, run_header_left: str,
                         run_header_right: str, maker: str, questions: list[dict],
                         profile: dict | None = None) -> str:
    """Render the student paper. Section titles come from the data (None means
    no title, matching round-1 FINAL where mc items carry no section header).
    Optional profile supplies rendering parameters (see school_layout);
    without it the long-standing defaults apply unchanged. Questions carrying
    the same common_passage_id render the passage once (CSAT rule)."""
    if not questions:
        raise ValueError('questions는 최소 1개 이상이어야 합니다.')
    min_answer_lines = int((profile or {}).get('constructed_min_answer_lines')
                           or _DEFAULT_MIN_ANSWER_LINES)
    questions = _dedupe_common_passages(questions)
    pages = _pack_columns(questions)
    total = len(pages)
    out = ['<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n<title>%s</title>\n<style>%s</style>\n</head>\n<body>'
           % (_html.escape(title_row), PAPER_CSS)]
    for index, (left, left_gap, right, right_gap) in enumerate(pages):
        num = index + 1
        out.append('<div class="page">')
        if index == 0:
            out.append('<div class="exam-header"><table>\n'
                       '<tr class="title-row"><td colspan="4">%s</td></tr>\n'
                       '<tr class="sub-row"><td colspan="4">%s</td></tr>\n'
                       '<tr class="info-row"><td class="label">반</td><td style="width:20mm;">&nbsp;</td>'
                       '<td class="label">번호</td><td>&nbsp;</td></tr>\n'
                       '<tr class="info-row"><td class="label">이름</td><td colspan="3">&nbsp;</td></tr>\n'
                       '</table></div>' % (title_row, sub_row))
        else:
            out.append('<div class="run-header"><span>%s</span><span>%s</span></div>'
                       % (run_header_left, run_header_right))
        out.append('<div class="cols">%s%s</div>'
                   % (_render_col(left, left_gap, min_answer_lines),
                      _render_col(right, right_gap, min_answer_lines)))
        out.append('<div class="page-footer">%s &nbsp;|&nbsp; %d / %d</div>' % (maker, num, total))
        out.append('</div>')
    out.append('</body>\n</html>')
    return ''.join(out)


def render_answer_explanation(*, title: str, subtitle: str, summary: dict,
                              quick_answers: list[str], rows: list[dict]) -> str:
    """Render the answer/explanation paper (round-1 FINAL fd2 system + the
    round-2/3 quick-answer grid on the first page)."""
    if not rows:
        raise ValueError('rows는 최소 1개 이상이어야 합니다.')
    if len(quick_answers) != len(rows):
        raise ValueError('quick_answers 길이는 rows와 같아야 합니다.')
    out = ['<!doctype html><html lang="ko"><head><meta charset="utf-8">\n<title>%s</title>\n<style>%s</style></head><body>'
           % (_html.escape(title), EXPL_CSS)]
    out.append('<h1>%s</h1><div class="sub">%s</div>' % (title, subtitle))
    out.append('<table class="summary">\n<tr><th>전체 문항</th><th>선택형</th><th>서답형</th>'
               '<th>복수정답 문항</th><th>VERIFIED</th><th>REVIEW</th></tr>\n<tr><td>%s</td><td>%s</td><td>%s</td>'
               '<td>%s</td><td>%s</td><td>%s</td></tr>\n</table>'
               % (summary.get('total'), summary.get('mc'), summary.get('sa'),
                  summary.get('multi'), summary.get('verified'), summary.get('review')))
    # regroup per 8 answers per table row
    body = ''
    for start in range(0, len(quick_answers), 8):
        group = quick_answers[start:start + 8]
        body += '<tr>' + ''.join('<th>%d</th><td>%s</td>' % (start + i + 1, ans)
                                 for i, ans in enumerate(group)) + '</tr>\n'
    out.append('<h2>빠른 정답</h2>\n<table class="quick">\n%s</table>' % body)
    out.append('<h2>문항별 정답 및 해설 (1~%d)</h2>' % len(rows))
    for row in rows:
        out.append('<div class="qrow">\n<span class="qnum">%s.</span> <span class="ans">정답 %s</span>\n'
                   '<div class="expl">%s</div>\n<div class="meta">%s</div>\n</div>'
                   % (_html.escape(str(row['no'])), row['answer'], row['expl'], row['meta']))
    out.append('</body>\n</html>')
    return ''.join(out)
