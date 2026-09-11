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
.qhead { display:flex; gap: 1.3mm; align-items:baseline; }
.qnum { font-weight:700; flex-shrink:0; }
.qprompt { font-weight:500; }
.passage { margin: 1mm 0 1mm 4.6mm; }
.passage.box { border: 0.7pt solid #666; padding: 1.5mm 2mm; background:#fbfbfb; line-height:1.55; }
.passage.dialog { border: 0.7pt solid #666; padding: 1.5mm 2mm; background:#fbfbfb; line-height:1.55; }
.choices.row { margin: 1mm 0 0 4.6mm; }
.choice-item { display: inline-block; white-space: nowrap; margin: 0 3.5mm 0.6mm 0; }
.choices.vertical .opt { margin: 0.6mm 0 0 4.6mm; }
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

# Line-budget packing tuned to round-1 FINAL density (roughly 7-9 short mc
# items per A4 page, fewer when shared passage boxes are present).
_CHARS_PER_LINE = 62
_LINES_PER_COL_FIRST = 48
_LINES_PER_COL = 56


def _text_len(fragment: str) -> int:
    return len(_WS_RE.sub(' ', _TAG_RE.sub(' ', fragment or '')))


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
    return lines + 1


def _render_question(question: dict) -> str:
    parts = []
    if question.get('section'):
        parts.append('<div class="section-title">%s</div>' % question['section'])
    parts.append('<div class="qblock" data-qnum="%s">' % _html.escape(str(question['no'])))
    parts.append('  <div class="qhead"><span class="qnum">%s.</span><span class="qprompt">%s</span></div>'
                 % (_html.escape(str(question['no'])), question['prompt']))
    if question.get('passage'):
        cls = question.get('passage_class') or 'prose'
        parts.append('<div class="passage %s">%s</div>' % (cls, question['passage']))
    choices = question.get('choices') or []
    if choices:
        if (question.get('layout') or 'row') == 'vertical':
            opts = ''.join('<div class="opt">%s</div>' % choice for choice in choices)
            parts.append('<div class="choices vertical">%s</div>' % opts)
        else:
            items = ''.join('<span class="choice-item">%s</span>' % choice for choice in choices)
            parts.append('<div class="choices row">%s</div>' % items)
    parts.append('</div>')
    return ''.join(parts)


def _pack_columns(questions: list[dict]) -> list[list[list[dict]]]:
    """Pack questions into pages of two columns, left -> right -> next page.
    A question block never splits across columns (page-break-inside: avoid)."""
    pages: list[list[list[dict]]] = []
    current: list[list[dict]] = [[], []]
    budgets = [_LINES_PER_COL_FIRST, _LINES_PER_COL_FIRST]
    used = [0, 0]
    col = 0
    first_page = True
    for question in questions:
        need = estimate_lines(question)
        if used[col] + need > budgets[col] and used[col] > 0:
            if col == 0:
                col = 1
            else:
                pages.append(current)
                current, used, col = [[], []], [0, 0], 0
                first_page = False
                budgets = [_LINES_PER_COL, _LINES_PER_COL]
        if used[col] + need > budgets[col] and used[col] == 0:
            pass  # single oversized block still placed whole (never split)
        current[col].append(question)
        used[col] += need
    if current[0] or current[1]:
        pages.append(current)
    return pages


def render_student_paper(*, title_row: str, sub_row: str, run_header_left: str,
                         run_header_right: str, maker: str, questions: list[dict]) -> str:
    """Render the student paper. Section titles come from the data (None means
    no title, matching round-1 FINAL where mc items carry no section header)."""
    if not questions:
        raise ValueError('questions는 최소 1개 이상이어야 합니다.')
    pages = _pack_columns(questions)
    total = len(pages)
    out = ['<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n<title>%s</title>\n<style>%s</style>\n</head>\n<body>'
           % (_html.escape(title_row), PAPER_CSS)]
    for index, (left, right) in enumerate(pages):
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
        out.append('<div class="cols"><div class="col">%s</div><div class="col">%s</div></div>'
                   % (''.join(_render_question(q) for q in left),
                      ''.join(_render_question(q) for q in right)))
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
