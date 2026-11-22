# -*- coding: utf-8 -*-
"""예비고1 디지털 수능 영어 모의고사 '실전 독해'(18~45번) 회차 자료를 만드는 순수 함수 모음.

내용 JSON(round_content.json) → 문항 그림용 HTML, 문제지 HTML, 근거 좌표(marks), round.json.
크롬·파일을 다루는 부분은 build_round.py에 있고, 여기 함수는 모두 입력만으로 결과가 정해진다.
"""
import html
import json
import math
import re

CIRC = '①②③④⑤'
FIRST_NUMBER = 18
READING_COUNT = 28
WORD_COUNT = 50
MAX_MARKS = 30
FIG_WIDTH = 940
BANNED_WORDS = ('원본', '출처', '학평', '학력평가', '변형')


# ---------- 글자 처리 ----------
def smart(text):
    """태그 밖의 곧은따옴표를 둥근따옴표로 바꾼다."""
    def convert(part):
        part = re.sub(r'(^|[\s(\[—])"', r'\1“', part)
        part = part.replace('"', '”')
        part = re.sub(r"(^|[\s(\[])'", r'\1‘', part)
        return part.replace("'", '’')
    return ''.join(p if p.startswith('<') else convert(p) for p in re.split(r'(<[^>]+>)', text))


def inline(text):
    """내용 JSON의 글(<u>·<i>·<b>·<blank/>만 허용)을 화면용 HTML로 바꾼다."""
    text = smart(text.replace('&', '&amp;'))
    text = text.replace('<blank/>', '<span class="blank"></span>')
    return re.sub(r'\( ([①-⑤]) \)', r'<span class="nw">( \1 )</span>', text)


def stem_html(number, stem):
    """발문 한 줄. 부정어(않는·틀린…)와 '밑줄 친 영어 구'에 밑줄을, [3점]은 줄바꿈 없이 붙인다."""
    text = stem.replace('&', '&amp;')
    points = ''
    if text.endswith(' [3점]'):
        text, points = text[:-len(' [3점]')], ' <span class="nw">[3점]</span>'
    if '<u>' not in text:
        text = re.sub(r'(않는|않은|틀린|없는|다른)(?= (?:것은|문장은)\?)', r'<u>\1</u>', text)
        text = re.sub(r'(?<=밑줄 친 )([A-Za-z][^가-힣]*[A-Za-z])(?=[이가] 다음 글에서)', r'<u>\1</u>', text)
    return f'<p class="stem"><b class="qn">{number}.</b> {smart(text)}{points}</p>'


def choice_layout(choices):
    """선택지 모양으로 배치를 정한다: ab(요약문 표)·order(순서)·row5((a)~(e))·row3(짧은 낱말)·row2(긴 낱말 쌍)·col(한 줄에 하나)."""
    if all(' … ' in c for c in choices):
        return 'ab'
    if all(re.fullmatch(r'\([A-D]\)( - \([A-D]\)){2}', c) for c in choices):
        return 'order'
    if all(re.fullmatch(r'\([a-e]\)', c) for c in choices):
        return 'row5'
    if all(' → ' in c or re.fullmatch(r'[A-Za-z-]+', c) for c in choices):
        return 'row3' if max(len(c) for c in choices) <= 18 else 'row2'
    return 'col'


def uses_passage_letters(question):
    """공통 지문의 밑줄 (a)~(e) 가운데 하나가 정답인 문항인가(장문 어휘·지칭 대상)."""
    choices = question.get('choices')
    return bool(question.get('passageKey') and choices and choice_layout(choices) == 'row5')


def mark_inline_answer(text, answer, mark):
    """선택지가 지문 안에 있는 문항의 정답 자리에 data-m을 단다: ( ④ ) 전체, 밑줄 낱말, 또는 원문자."""
    c = CIRC[answer - 1]
    rules = (
        (rf'\( {c} \)', lambda m: f'<span data-m="{mark}">{m.group(0)}</span>'),
        (rf'({c}\s*)<u>', lambda m: f'{m.group(1)}<u data-m="{mark}">'),
        (c, lambda m: f'<span data-m="{mark}">{c}</span>'),
    )
    for pattern, replace in rules:
        text, count = re.subn(pattern, replace, text, count=1)
        if count:
            break
    return text


def mark_passage_letters(text, letter_marks):
    """공통 지문의 '(d) <u>낱말</u>'에 data-m을 단다. letter_marks: {'d': 'answer:42'}."""
    def replace(m):
        mark = letter_marks.get(m.group(1))
        return f'<span data-m="{mark}">{m.group(0)}</span>' if mark else m.group(0)
    return re.sub(r'\(([a-e])\) <u>.*?</u>', replace, text)


# ---------- 블록 → HTML ----------
def footnote_html(text):
    items = [item for item in smart(text.replace('&', '&amp;')).split('  ') if item.strip()]
    return '<p class="fn">' + ' '.join(f'<span class="fi">{item}</span>' for item in items) + '</p>'


def chart_html(chart):
    """묶음 막대 도표(흑백). chart: {title: [줄…], legend: [a, b], rows: [[이름, a값, b값]…], note?}."""
    width, height, left, right, top, bottom = 430, 262, 34, 6, 44, 44
    rows = chart['rows']
    top_value = int(math.ceil((max(max(a, b) for _, a, b in rows) + 5) / 10) * 10)
    plot_w, plot_h = width - left - right, height - top - bottom
    legend = [html.escape(str(item)) for item in chart['legend']]
    s = [f'<svg class="chart" viewBox="0 0 {width} {height}" xmlns="http://www.w3.org/2000/svg">',
         f'<text x="{left - 28}" y="{top - 12}" font-size="13">(%)</text>',
         f'<rect x="{width / 2 - 80}" y="{top - 32}" width="13" height="13" fill="#2b2b2b"/>'
         f'<text x="{width / 2 - 62}" y="{top - 21}" font-size="14">{legend[0]}</text>',
         f'<rect x="{width / 2 + 20}" y="{top - 32}" width="13" height="13" fill="#a0a0a0" stroke="#333"/>'
         f'<text x="{width / 2 + 38}" y="{top - 21}" font-size="14">{legend[1]}</text>']
    for value in range(0, top_value + 1, 10):
        y = top + plot_h - plot_h * value / top_value
        s.append(f'<line x1="{left}" y1="{y:.1f}" x2="{width - right}" y2="{y:.1f}" '
                 f'stroke="{"#000" if value == 0 else "#ccc"}" stroke-width="{1.5 if value == 0 else 1}"/>')
        s.append(f'<text x="{left - 6}" y="{y + 4.5:.1f}" font-size="13" text-anchor="end">{value}</text>')
    group_w, bar_w = plot_w / len(rows), 24
    for i, (name, a, b) in enumerate(rows):
        center = left + group_w * i + group_w / 2
        for j, (value, fill) in enumerate(((a, '#2b2b2b'), (b, '#a0a0a0'))):
            x = center - bar_w - 1 + j * (bar_w + 2)
            bar_h = plot_h * value / top_value
            y = top + plot_h - bar_h
            s.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w}" height="{bar_h:.1f}" fill="{fill}" stroke="#222"/>')
            s.append(f'<text x="{x + bar_w / 2:.1f}" y="{y - 3:.1f}" font-size="13" text-anchor="middle">{value}</text>')
        words = html.escape(name).split(' ')
        lines = words if len(words) == 2 else [' '.join(words)]
        for k, line in enumerate(lines):
            s.append(f'<text x="{center:.1f}" y="{top + plot_h + 16 + 15 * k}" font-size="13" text-anchor="middle">{line}</text>')
    s.append('</svg>')
    title = '<div class="chart-title">' + ''.join(f'<span>{inline(line)}</span>' for line in chart['title']) + '</div>'
    note = f'<div class="chart-note">{inline(chart["note"])}</div>' if chart.get('note') else ''
    return '<div class="chartbox">' + title + ''.join(s) + note + '</div>'


def notice_html(notice):
    """안내문 상자. lines: [종류, 글] — lead·item('이름: 값')·head·sub·trow('시간: 내용' 표 줄)."""
    rows, table = [f'<div class="n-title">{inline(notice["title"])}</div>'], []

    def flush():
        if table:
            rows.append('<table class="n-table">' + ''.join(
                f'<tr><td class="n-time">{a}</td><td>{b}</td></tr>' for a, b in table) + '</table>')
            table.clear()
    for kind, text in notice['lines']:
        text = inline(text)
        if kind == 'trow':
            table.append(tuple(text.split(': ', 1)))
            continue
        flush()
        if kind == 'item' and ':' in text:
            name, value = text.split(':', 1)
            text = f'<b>{name}:</b>{value}'
        if kind == 'head':
            text = f'<b>{text}</b>'
        rows.append(f'<div class="n-{kind}">{text}</div>')
    flush()
    return '<div class="notice">' + ''.join(rows) + '</div>'


def blocks_html(blocks, answer_mark=None, answer=0, letter_marks=None):
    """지문 블록들을 HTML로. answer_mark가 있으면 지문 속 정답 자리에, letter_marks가 있으면 (a)~(e)에 표시를 단다."""
    def text_html(text):
        text = inline(text)
        if answer_mark:
            text = mark_inline_answer(text, answer, answer_mark)
        return mark_passage_letters(text, letter_marks) if letter_marks else text

    ordered = any(block[0] == 'lp' for block in blocks)
    out = []
    for block in blocks:
        kind = block[0]
        if kind == 'p':
            out.append(f'<p class="pt">{text_html(block[1])}</p>')
        elif kind == 'letter':
            out.append('<div class="letter">' + ''.join(
                f'<p class="{"pt" if len(line) > 60 else "line"}">{inline(line)}</p>' for line in block[1].split('\n')) + '</div>')
        elif kind == 'box':
            out.append(f'<div class="given"><p class="{"pt" if ordered else "line"}">{inline(block[1])}</p></div>')
        elif kind == 'boxp':
            out.append(f'<p class="pt">{text_html(block[1])}</p>{footnote_html(block[2])}')
        elif kind == 'lp':
            out.append(f'<div class="lp"><span class="lab">{block[1]}</span><p>{inline(block[2])}</p></div>')
        elif kind == 'lpb':
            out.append(f'<div class="plabel">{block[1]}</div><div class="given"><p class="pt">{text_html(block[2])}</p></div>')
        elif kind == 'fn':
            out.append(footnote_html(block[1]))
        elif kind == 'summary':
            text = inline(block[1]).replace('<b>(A)</b>', '<span class="ab">(A)</span>').replace('<b>(B)</b>', '<span class="ab">(B)</span>')
            out.append(f'<div class="arrow">↓</div><div class="given summary"><p class="line">{text}</p></div>')
        elif kind == 'chart':
            out.append(chart_html(block[1]))
        elif kind == 'notice':
            out.append(notice_html(block[1]))
        else:
            raise ValueError(f'모르는 블록 종류: {kind}')
    return ''.join(out)


def choices_html(choices, answer, answer_mark=None):
    if not choices:
        return ''
    def mark(i):
        return f' data-m="{answer_mark}"' if answer_mark and i + 1 == answer else ''
    layout = choice_layout(choices)
    if layout == 'ab':
        rows = ''.join(
            f'<tr{mark(i)}><td>{CIRC[i]}</td><td>{inline(c.split(" … ")[0])}</td><td>……</td><td>{inline(c.split(" … ")[1])}</td></tr>'
            for i, c in enumerate(choices))
        return f'<table class="ab-table"><tr><th></th><th>(A)</th><th></th><th>(B)</th></tr>{rows}</table>'
    items = ''.join(f'<div class="choice"{mark(i)}>{CIRC[i]} {inline(c)}</div>' for i, c in enumerate(choices))
    return f'<div class="choices {layout}">{items}</div>'


def question_section(question, marks=True):
    """문항 그림 한 장(q18 … q45)의 HTML."""
    number, answer = question['number'], question['answer']
    mark = f'answer:{number}' if marks else None
    choices = question.get('choices')
    body = blocks_html(question.get('blocks') or [], None if choices else mark, answer)
    return (f'<section class="fig" id="q{number}" data-part="question">{stem_html(number, question["stem"])}'
            f'{body}{choices_html(choices, answer, mark)}</section>')


def passage_section(key, passage, questions, marks=True):
    """공통 지문 그림(p41-42, p43-45)의 HTML. 밑줄 (a)~(e)가 정답인 문항의 정답 낱말에 표시를 단다."""
    letter_marks = {}
    if marks:
        for q in questions:
            if q.get('passageKey') == key and uses_passage_letters(q):
                letter = 'abcde'[q['answer'] - 1]
                letter_marks[letter] = (letter_marks.get(letter, '') + f' answer:{q["number"]}').strip()
    return (f'<section class="fig" id="p{key}" data-part="passage"><p class="group">{inline(passage["head"])}</p>'
            f'{blocks_html(passage["blocks"], letter_marks=letter_marks)}</section>')


# ---------- 인용 구절(evidence) 찾기 ----------
_TOKEN = re.compile(r'<svg.*?</svg>|<[^>]+>|&[#\w]+;|.', re.S)
_INLINE_TAGS = {'u', 'i', 'b', 'span', 'em', 'strong'}
_QUOTES = {'“': '"', '”': '"', '‘': "'", '’': "'"}


def _is_tag(token):
    return len(token) > 1 and token.startswith('<')


def _normalize(chars):
    """(글자, 자리) 목록을 비교용 글로 바꾼다: 따옴표 통일, 원문자·빈 괄호·(a) 표지 제거, 공백 한 칸."""
    text = ''.join(_QUOTES.get(c, ' ' if c in CIRC or c.isspace() else c) for c, _ in chars)
    for pattern in (r'\(\s*\)', r'\([a-e]\) '):
        text = re.sub(pattern, lambda m: ' ' * len(m.group(0)), text)
    out, where = [], []
    for c, (_, index) in zip(text, chars):
        if c == ' ' and (not out or out[-1] == ' '):
            continue
        out.append(c)
        where.append(index)
    return ''.join(out), where


def normalize_text(text):
    return _normalize([(c, None) for c in text])[0].strip()


def wrap_evidence(section, phrase, mark):
    """section HTML에서 phrase(해설이 인용한 영어 구절)를 찾아 글자 구간을 <span data-m>으로 감싼다.

    구절이 밑줄·원문자 태그를 가로지르면 태그 사이사이의 글자 묶음마다 span을 따로 만든다. 못 찾으면 None.
    """
    tokens = _TOKEN.findall(section)
    chars = []
    for i, token in enumerate(tokens):
        if _is_tag(token):
            name = re.match(r'</?\s*([A-Za-z0-9]+)', token)
            if not name or name.group(1).lower() not in _INLINE_TAGS:
                chars.append((' ', None))  # 블록 경계에서는 낱말이 붙지 않게 한다
        else:
            chars.append((html.unescape(token) if len(token) > 1 else token, i))
    hay, where = _normalize(chars)
    needle = normalize_text(phrase)
    at = hay.find(needle) if needle else -1
    if at < 0:
        return None
    first, last = where[at], where[at + len(needle) - 1]
    out, opened = [], False
    for i, token in enumerate(tokens):
        inside = first <= i <= last and not _is_tag(token)
        if inside and not opened:
            out.append(f'<span data-m="{mark}">')
        elif not inside and opened:
            out.append('</span>')
        opened = inside
        out.append(token)
    if opened:
        out.append('</span>')
    return ''.join(out)


def marked_sections(content):
    """그림용 section HTML 목록(공통 지문은 첫 문항 앞). 모든 evidence 구절을 찾아 표시하고, 못 찾으면 멈춘다."""
    reading, passages = content['reading'], content.get('passages') or {}
    sections = {f'p{key}': passage_section(key, passage, reading) for key, passage in passages.items()}
    order = []
    for q in reading:
        qid, pid = f'q{q["number"]}', f'p{q["passageKey"]}' if q.get('passageKey') else None
        if pid and pid not in order:
            order.append(pid)
        order.append(qid)
        sections[qid] = question_section(q)
        for i, phrase in enumerate(q.get('evidence') or []):
            for target in filter(None, (pid, qid)):
                wrapped = wrap_evidence(sections[target], phrase, f'evidence:{q["number"]}:{i}')
                if wrapped:
                    sections[target] = wrapped
                    break
            else:
                raise ValueError(f'{q["number"]}번 인용 구절을 지문에서 찾지 못했습니다: {phrase}')
    return [sections[name] for name in order]


# ---------- 좌표 ----------
def merge_lines(rects):
    """[left, top, width, height] 사각형들을 줄마다 하나로 합친다(세로로 절반 넘게 겹치면 같은 줄)."""
    lines = []
    for left, top, width, height in sorted((r for r in rects if r[2] > 0.5 and r[3] > 0.5), key=lambda r: (r[1], r[0])):
        for line in lines:
            overlap = min(line[3], top + height) - max(line[1], top)
            if overlap > 0.5 * min(line[3] - line[1], height):
                line[0], line[1] = min(line[0], left), min(line[1], top)
                line[2], line[3] = max(line[2], left + width), max(line[3], top + height)
                break
        else:
            lines.append([left, top, left + width, top + height])
    return [[l, t, r - l, b - t] for l, t, r, b in lines]


def to_mark(rect, fig_width, fig_height, part, kind):
    """픽셀 사각형을 그림 크기에 대한 1/10000 단위 좌표로 바꾼다(그림 밖으로 나가지 않게 자른다)."""
    left, top, width, height = rect
    x = min(max(round(left / fig_width * 10000), 0), 9999)
    y = min(max(round(top / fig_height * 10000), 0), 9999)
    return {'part': part, 'kind': kind, 'x': x, 'y': y,
            'width': max(1, min(round(width / fig_width * 10000), 10000 - x)),
            'height': max(1, min(round(height / fig_height * 10000), 10000 - y))}


def marks_for(number, figure, part):
    """한 그림의 측정값(figure: {w, h, items: [{m, rects}]})에서 number번 문항의 marks를 만든다. 근거 먼저, 정답 나중."""
    marks = []
    for kind in ('evidence', 'answer'):
        groups = {}  # 한 구절이 여러 조각(span)으로 나뉘어 있으면 모아서 줄마다 하나로 합친다
        for item in figure['items']:
            for name in item['m'].split():
                if name.split(':')[:2] == [kind, str(number)]:
                    groups.setdefault(name, []).extend(item['rects'])
        for rects in groups.values():
            marks += [to_mark(rect, figure['w'], figure['h'], part, kind) for rect in merge_lines(rects)]
    return marks


# ---------- round.json ----------
def assemble_round(content, figures, number=None):
    """내용과 측정값으로 서버가 읽는 round.json을 만든다. figures: {'q18': {w, h, items}, 'p41-42': …}."""
    reading = []
    for q in content['reading']:
        key = q.get('passageKey')
        marks = marks_for(q['number'], figures[f'q{q["number"]}'], 'question')
        if key:
            marks += marks_for(q['number'], figures[f'p{key}'], 'passage')
        reading.append({'image': f'q{q["number"]}.webp', 'passage': f'p{key}.webp' if key else None,
                        'answer': q['answer'], 'explanation': q['explanation'], 'marks': marks})
    return {'number': number or content['number'], 'source': content.get('source', ''), 'reading': reading,
            'words': [{'text': w['text'], 'choices': w['choices'], 'answer': w['answer']} for w in content['words']]}


def valid_mark(mark, has_passage):
    return (mark.get('part') in (('question', 'passage') if has_passage else ('question',))
            and mark.get('kind') in ('evidence', 'answer')
            and mark['x'] >= 0 and mark['y'] >= 0 and mark['width'] > 0 and mark['height'] > 0
            and mark['x'] + mark['width'] <= 10000 and mark['y'] + mark['height'] <= 10000)


def validate_round(data):
    """서버(PreHighPart2Store.validate)와 테스트가 요구하는 조건을 미리 확인한다. 문제 목록을 돌려준다(없으면 빈 목록)."""
    problems = []
    reading, words = data.get('reading') or [], data.get('words') or []
    if len(reading) != READING_COUNT:
        problems.append(f'독해 문항이 {len(reading)}개입니다({READING_COUNT}개여야 함).')
    if len(words) != WORD_COUNT:
        problems.append(f'어휘 문항이 {len(words)}개입니다({WORD_COUNT}개여야 함).')
    for i, item in enumerate(reading):
        n, marks = FIRST_NUMBER + i, item.get('marks') or []
        if not item.get('image') or not 1 <= item.get('answer', 0) <= 5:
            problems.append(f'{n}번: 그림 이름 또는 정답 번호가 잘못되었습니다.')
        if not (item.get('explanation') or '').startswith('정답 '):
            problems.append(f'{n}번: 해설이 "정답 "으로 시작하지 않습니다.')
        if len(marks) > MAX_MARKS or not all(valid_mark(m, bool(item.get('passage'))) for m in marks):
            problems.append(f'{n}번: 표시 좌표가 범위를 벗어났거나 {MAX_MARKS}개를 넘습니다.')
        for kind, label in (('evidence', '근거'), ('answer', '정답')):
            if not any(m.get('kind') == kind for m in marks):
                problems.append(f'{n}번: {label} 표시가 없습니다.')
    for i, word in enumerate(words, 1):
        choices = word.get('choices') or []
        if not (word.get('text') or '').strip() or not 1 <= word.get('answer', 0) <= 5 or len(set(choices)) != 5:
            problems.append(f'어휘 {i}번: 낱말, 정답 번호 또는 선택지 5개를 확인해 주세요.')
    return problems


def visible_text(page):
    """HTML에서 사람이 읽는 글자만 남긴다(출처 낱말 검사용)."""
    page = re.sub(r'<(style|script)\b.*?</\1>', ' ', page, flags=re.S)
    return re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[^>]+>', ' ', page))).strip()


def banned_words(text, source=''):
    """문제지·화면 글자에 있으면 안 되는 낱말(원본·출처·학평·학력평가·변형, 그리고 source 문자열)을 찾는다."""
    found = [word for word in BANNED_WORDS if word in text]
    if source and source in text:
        found.append(source)
    return found


# ---------- 화면(HTML 문서) ----------
# 글자·간격은 em으로 적어, 그림(본문 30px·폭 940px)과 문제지(본문 9.4pt·A4 2단)가 같은 규칙을 쓴다.
COMMON_CSS = '''
* { box-sizing: border-box; }
html, body { margin: 0; background: #fff; color: #000; }
body { font-family: "Liberation Serif", "Times New Roman", "Noto Serif CJK KR", "Noto Serif KR", "Batang", serif; line-height: 1.5; }
p { margin: 0; }
.stem { margin-bottom: .45em; padding-left: 1.45em; text-indent: -1.45em; font-size: 1.04em; line-height: 1.48; }
.qn { font-size: 1.1em; }
.group { margin-bottom: .2em; }
.nw { white-space: nowrap; }
.pt { padding: 0 0 0 .13em; text-align: justify; text-indent: 1.2em; }
.line { padding-left: .13em; text-align: justify; }
.given { border: .067em solid #000; padding: .4em .65em; margin: .13em 0 .5em; }
.given .pt, .given .line { padding-left: 0; }
.lp { display: flex; gap: .35em; margin-bottom: .2em; }
.lp p { flex: 1; text-align: justify; }
.plabel { text-align: center; }
.fn { text-align: right; font-size: .9em; margin-top: .1em; }
.fi { display: inline-block; margin-left: .6em; }
.blank { display: inline-block; width: 8.5em; height: 1em; border-bottom: .067em solid #000; vertical-align: baseline; }
.choices { margin-top: .2em; padding-left: .13em; line-height: 1.57; }
.choice { padding-left: 1.3em; text-indent: -1.3em; }
.choices.row5 { display: grid; grid-template-columns: repeat(5, 1fr); }
.choices.row3, .choices.order { display: grid; grid-template-columns: repeat(3, 1fr); }
.choices.row2 { display: grid; grid-template-columns: repeat(2, 1fr); }
.ab-table { border-collapse: collapse; margin: .3em 0 0 .2em; line-height: 1.4; }
.ab-table td, .ab-table th { padding: 0 .45em 0 0; text-align: left; font-weight: normal; }
.arrow { text-align: center; line-height: 1; margin: .3em 0; }
.summary { line-height: 1.85; }
.ab { display: inline-block; min-width: 6.5em; margin: 0 .2em; border-bottom: .067em solid #000; text-align: center; line-height: 1.5; }
.notice { border: .067em solid #000; padding: .6em .8em; margin: .13em 0 .3em; font-size: .94em; line-height: 1.45; }
.n-title { text-align: center; font-weight: bold; font-size: 1.15em; margin-bottom: .35em; }
.n-lead { margin: .2em 0; }
.n-sub { padding-left: 1em; }
.n-head { margin-top: .25em; }
.n-table { border-collapse: collapse; width: 100%; margin: .2em 0; }
.n-table td { border: 1px solid #000; padding: .1em .5em; }
.n-table .n-time { white-space: nowrap; text-align: center; width: 36%; }
.chartbox { text-align: center; margin: .13em 0 .6em; }
.chart-title { font-weight: bold; font-size: 1.04em; line-height: 1.2; margin-bottom: .2em; }
.chart-title span { display: block; }
.chart-note { font-size: .75em; text-align: right; }
svg.chart { display: block; width: 100%; height: auto; font-family: inherit; }
u { text-underline-offset: .13em; }
'''

FIGURE_CSS = f'''
html {{ overflow: hidden; }}
body {{ width: {FIG_WIDTH}px; font-size: 30px; }}
.fig {{ width: {FIG_WIDTH}px; padding: 20px 22px 46px 24px; }}
#measure {{ display: none; }}
'''

# 그림 높이를 정수 픽셀로 맞춘 뒤, 주소 끝에 #q18,q19처럼 그림 이름이 있으면 그 그림들만 남기고(찍기용),
# 없으면 모든 그림과 표시 위치를 재어 <pre id="measure">에 쓴다.
FIGURE_SCRIPT = '''
window.addEventListener('load', function () {
  var only = decodeURIComponent(location.hash.slice(1));
  var figs = Array.prototype.slice.call(document.querySelectorAll('.fig'));
  figs.forEach(function (f) { f.style.height = Math.ceil(f.getBoundingClientRect().height) + 'px'; });
  if (only) { figs.forEach(function (f) { if (only.split(',').indexOf(f.id) < 0) f.style.display = 'none'; }); return; }
  var out = figs.map(function (f) {
    var box = f.getBoundingClientRect();
    return { id: f.id, part: f.dataset.part, w: box.width, h: box.height,
      items: Array.prototype.map.call(f.querySelectorAll('[data-m]'), function (e) {
        return { m: e.dataset.m, rects: Array.prototype.map.call(e.getClientRects(), function (r) {
          return [r.left - box.left, r.top - box.top, r.width, r.height]; }) };
      }) };
  });
  var pre = document.createElement('pre');
  pre.id = 'measure';
  pre.textContent = JSON.stringify(out);
  document.body.appendChild(pre);
});
'''


def figures_page(content):
    """문항·공통 지문 그림 30장을 한 줄로 세운 HTML 문서. 크롬이 열어 좌표를 재고 한 장씩 찍는다."""
    return ('<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>figures</title><style>'
            + COMMON_CSS + FIGURE_CSS + '</style></head><body>' + ''.join(marked_sections(content))
            + '<script>' + FIGURE_SCRIPT + '</script></body></html>')


def parse_measure(dom):
    """크롬 --dump-dom 출력에서 측정 결과를 꺼내 {그림 이름: 측정값}으로 돌려준다."""
    found = re.search(r'<pre id="measure">(.*?)</pre>', dom, re.S)
    if not found:
        raise ValueError('측정 결과(<pre id="measure">)가 없습니다. 크롬이 페이지를 끝까지 실행했는지 확인해 주세요.')
    return {figure['id']: figure for figure in json.loads(html.unescape(found.group(1)))}


def chunk_figures(heights, limit=8000):
    """그림들을 한 번에 찍을 묶음으로 나눈다. heights: [(이름, 높이)] → [[(이름, 위, 높이)…]…], 묶음 높이는 limit 이하."""
    chunks, top = [], 0
    for name, height in heights:
        if not chunks or top + height > limit:
            chunks.append([])
            top = 0
        chunks[-1].append((name, top, height))
        top += height
    return chunks


def paper_titles(number, grade):
    """문제지 표지 제목·부제·쪽 아래 줄. '변형' 같은 출처 낱말은 쓰지 않는다."""
    return (f'제{number}회 {grade} 영어 독해 실전 모의고사 (18~45번)', f'{READING_COUNT}문항 · 45분',
            f'제{number}회 {grade} 영어 독해 실전')


def paper_page(content, number, grade, brand='F2R영어'):
    """전체 문제지(A4 2단) HTML. 41~42번 묶음은 새 쪽에서 시작하고, 묶음(공통 지문+문항)은 쪽을 넘기지 않는다."""
    title, sub, footer = paper_titles(number, grade)
    css = COMMON_CSS + f'''
@page {{ size: A4; margin: 14mm 11mm 13mm;
  @top-left {{ content: "영어 영역"; font: 9pt "Noto Serif KR", "Batang", serif; }}
  @top-right {{ content: "{brand} " counter(page); font: 9pt "Noto Serif KR", "Batang", serif; }}
  @bottom-center {{ content: "{footer} · {brand} · " counter(page) " / " counter(pages); font: 8.5pt "Noto Serif KR", "Batang", serif; }} }}
body {{ font-size: 9.4pt; line-height: 1.4; }}
.stem {{ line-height: 1.4; }}
.choices {{ line-height: 1.42; }}
.cover {{ text-align: center; border-bottom: 2.5px solid #000; padding-bottom: 6px; margin-bottom: 10px; }}
.cover h1 {{ font-size: 18pt; margin: 0; letter-spacing: 1px; }}
.cover div {{ font-size: 10.5pt; margin-top: 4px; }}
.cols {{ column-count: 2; column-gap: 9mm; column-rule: 1px solid #000; padding: 0 .4mm; }}
.fig {{ break-inside: avoid; padding-bottom: 1.2em; }}
.fig[data-part="passage"] {{ break-inside: auto; }}
.given {{ break-inside: avoid; }}
.set {{ break-inside: avoid-page; }}
.set + .set {{ margin-top: 1em; }}
.newpage {{ break-before: page; }}
'''
    reading, passages = content['reading'], content.get('passages') or {}
    body, first_set = [], True
    for i, q in enumerate(reading):
        key = q.get('passageKey')
        if key and (i == 0 or reading[i - 1].get('passageKey') != key):
            body.append(f'</div><div class="cols set{" newpage" if first_set else ""}">' + passage_section(key, passages[key], reading, marks=False))
            first_set = False
        body.append(question_section(q, marks=False))
    return (f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>{title}</title><style>{css}</style></head><body>'
            f'<div class="cover"><h1>{title}</h1><div>{sub}</div></div><div class="cols">' + ''.join(body) + '</div></body></html>')
