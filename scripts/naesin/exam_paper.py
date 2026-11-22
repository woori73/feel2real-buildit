"""내신 예상문제 문제지 조판: 감독이 승인한 문제지(신구중2 11회)와 같은 편집으로 A4 2단 HTML을 만든다.

입력은 내신위키 저장 계약 문항이다(exam_check.py와 같은 파일). 문항 내용은 고치지 않고 모양만 입힌다.

승인본에서 뽑은 편집 규칙
- 명조 글꼴, "번호. 발문 (2점)" — 복수정답은 "(정답 2개/4점)". 발문의 부정어·강조어는 굵은 밑줄.
- 공통 지문: "[12~14] 다음 글을 읽고 물음에 답하시오."(대화면 "다음 대화를") + 테두리 상자(제목은 가운데 굵게) + 묶음 문항.
- 예문(stem의 둘째 줄부터)은 발문 아래 테두리 상자. `<보 기>` 줄이 있으면 상자 위 테두리에 제목.
- `(선택지 표: ⓐ | ⓑ | ⓒ)` 줄이 있으면 선택지를 표로. `[[img:경로]]` 줄은 그림.
- 짧은 낱말 선택지는 가로 3칸, 문장 선택지는 세로.

단 나누기는 줄 수 어림이 아니라 브라우저가 한다: 문서 끝의 짧은 스크립트가 조각(.piece)의 실제 높이를 재어
왼쪽 단 → 오른쪽 단 → 다음 쪽으로 통째로 옮긴다(제작원칙 3). 화면과 인쇄가 같은 쪽 구조를 쓴다.
"""
from __future__ import annotations

import base64
import html
import re
from pathlib import Path

import exam_check

MARKS = exam_check.MARKS
MAKER = '제작 : 압구정에듀홀릭 내신 wiki'
STATIC_DIR = Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'static'
LOGO_NAME = 'F2R_logo_pure.svg'
# 선택지가 모두 이 너비(한글은 2칸) 이하면 한 줄에 3개씩 가로 배열, 넘으면 세로 배열.
ROW_CHOICE_MAX_WIDTH = 12
SHORT_ANSWER_LINES = 2
# 이 글자 수 이상인 줄은 줄글 문단으로 보고 첫 줄을 들여 쓰고 양쪽을 맞춘다.
PROSE_MIN_CHARS = 100
TITLE_MAX_CHARS = 60
EMPHASIS_WORDS = ('않은', '않는', '없는', '아닌', '어색한', '틀린', '잘못된', '모두')
MIME = {'.webp': 'image/webp', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
        '.svg': 'image/svg+xml'}

TABLE_LINE = re.compile(r'^\(\s*선택지 표\s*:(.+)\)$')
IMAGE_LINE = re.compile(r'^\[\[img:(.+?)\]\]$')
VIEW_LINE = re.compile(r'^[<〈]\s*보\s*기\s*[>〉]$')
DIALOGUE = re.compile(r'^([A-Z][A-Za-z.]{0,11}):\s+(.+)$')
SCORE_LABEL = re.compile(r'\((?:정답\s*\d+\s*개\s*/\s*)?\d+(?:\.\d+)?\s*점\)')
EMPHASIS = re.compile(r'<u>(.*?)</u>|(%s)' % '|'.join(EMPHASIS_WORDS))

CSS = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: #fff; }
body { font-family: "Noto Serif KR", "KoPubWorldBatang", "Batang", "바탕", serif; font-size: 10pt;
  line-height: 1.63; color: #111; }
p { margin: 0; }
u { text-decoration-thickness: 0.8pt; text-underline-offset: 2.4pt; }
u.em { font-weight: 700; text-decoration-thickness: 1.2pt; }
.page { width: 210mm; height: 297mm; padding: 11mm 11mm 12mm; position: relative; display: flex;
  flex-direction: column; overflow: hidden; break-after: page; }
.page:last-child { break-after: auto; }
.cols { display: flex; gap: 7mm; flex: 1; min-height: 0; }
.col { flex: 0 0 calc(50% - 3.5mm); width: calc(50% - 3.5mm); display: flex; flex-direction: column;
  gap: 4.2mm; overflow: hidden; }
.col > * { flex: 0 0 auto; }
.exam-header { border: 1.2pt solid #000; margin-bottom: 4mm; }
.exam-header table { width: 100%; border-collapse: collapse; }
.exam-header td { border: 0.6pt solid #333; padding: 1.6mm 2mm; text-align: center; }
.exam-header .title-row td { position: relative; font-size: 14pt; font-weight: 700; height: 15mm; }
.exam-header .logo { position: absolute; left: 2.5mm; top: 50%; transform: translateY(-50%); height: 12mm; }
.exam-header .label { width: 16%; font-weight: 700; background: #f2f2f2; }
.run-header { display: flex; justify-content: space-between; border-bottom: 0.8pt solid #000;
  padding-bottom: 1mm; margin-bottom: 3.5mm; font-size: 9pt; }
.page-footer { position: absolute; bottom: 4.5mm; left: 0; right: 0; text-align: center; font-size: 8pt; color: #444; }
.piece { break-inside: avoid; }
.group-head { margin-bottom: 1.2mm; }
.q { margin-top: 3.4mm; }
.q:first-child { margin-top: 0; }
.q-head { display: flex; gap: 1.2mm; }
.q-no { flex: 0 0 auto; }
.pts { white-space: nowrap; }
.box { position: relative; border: 0.9pt solid #111; padding: 1.5mm 2.6mm; margin-top: 1.2mm; }
.box.view { margin-top: 3.4mm; padding-top: 2.6mm; }
.box-title { position: absolute; top: -0.8em; left: 50%; transform: translateX(-50%); background: #fff;
  padding: 0 2mm; font-weight: 700; white-space: nowrap; }
.ptitle { text-align: center; font-weight: 700; }
.para { text-align: justify; text-indent: 1em; }
.dlg { display: flex; gap: 1.2mm; }
.dlg b { flex: 0 0 1.6em; }
.dot { padding-left: 1.1em; text-indent: -0.9em; }
.arrow { padding-left: 1.5em; }
.notes { font-size: 8.8pt; }
.fig { margin-top: 1.4mm; }
.fig.framed { border: 0.9pt solid #111; padding: 1.6mm; }
.fig img { display: block; width: 100%; height: auto; }
.fig.missing { border: 0.9pt dashed #b00; color: #b00; padding: 2mm; text-align: center; }
.choices { margin: 1mm 0 0 1.2mm; }
.choices.row { display: grid; grid-template-columns: repeat(3, 1fr); column-gap: 2mm; }
.ch { display: flex; gap: 1.3mm; }
.ch-mark { flex: 0 0 auto; }
.ch-text b { font-weight: 700; }
.choices.vertical .ch.multi-line { margin-bottom: 0.9mm; }
.choice-table { width: calc(100% - 1.2mm); border-collapse: collapse; margin: 1.6mm 0 0 1.2mm; }
.choice-table td, .choice-table th { text-align: center; font-weight: 400; padding: 0 1mm; }
.choice-table td:first-child, .choice-table th:first-child { width: 2em; text-align: left; padding-left: 0.6mm; }
.ans-line { border-bottom: 0.6pt solid #333; height: 8mm; }
#src { width: 210mm; margin: 0 auto; padding: 11mm; }
#src .run-header { display: none; }
#flow { column-count: 2; column-gap: 7mm; }
#flow .piece { margin-bottom: 4.2mm; }
@media print { .page { height: 296.5mm; } }
"""

# 조각의 실제 높이를 재어 A4 쪽의 왼쪽 단 → 오른쪽 단 → 다음 쪽으로 통째로 옮긴다.
PAGINATE_JS = """
(function () {
  var MAX_EXTRA_GAP = 32;
  function build() {
    var src = document.getElementById('src'), flow = document.getElementById('flow');
    var first = document.getElementById('exam-header'), run = document.getElementById('run-header');
    var pieces = Array.prototype.slice.call(flow.querySelectorAll('.piece'));
    var pages = [], col;
    function addPage() {
      var page = document.createElement('div');
      page.className = 'page';
      page.appendChild(pages.length ? run.cloneNode(true) : first);
      var cols = document.createElement('div');
      cols.className = 'cols';
      cols.innerHTML = '<div class="col"></div><div class="col"></div>';
      page.appendChild(cols);
      var foot = document.createElement('div');
      foot.className = 'page-footer';
      page.appendChild(foot);
      document.body.appendChild(page);
      pages.push(page);
      return cols.firstChild;
    }
    function spread(done) {
      var n = done.children.length;
      if (n < 2) return;
      var free = done.getBoundingClientRect().bottom - done.lastChild.getBoundingClientRect().bottom;
      if (free > 0 && free / (n - 1) <= MAX_EXTRA_GAP) done.style.justifyContent = 'space-between';
    }
    function fits() { return col.scrollHeight <= col.clientHeight + 1; }
    // 왼쪽 단에 공통 지문만 들어가면 지문은 왼쪽, 첫 문항은 같은 쪽 오른쪽 단 맨 위에 둔다(쪽을 넘기지는 않는다).
    function splitLead(piece) {
      if (!col.nextSibling || !piece.querySelector('.group-head')) return;
      var lead = document.createElement('div');
      lead.className = 'piece';
      while (!piece.firstChild.classList.contains('q')) lead.appendChild(piece.firstChild);
      col.insertBefore(lead, piece);
      col.removeChild(piece);
      if (fits()) return;
      while (lead.lastChild) piece.insertBefore(lead.lastChild, piece.firstChild);
      col.replaceChild(piece, lead);
    }
    col = addPage();
    pieces.forEach(function (piece) {
      col.appendChild(piece);
      if (!fits() && col.children.length > 1) {
        var done = col;
        splitLead(piece);
        col = col.nextSibling || addPage();
        col.appendChild(piece);
        spread(done);
      }
    });
    pages.forEach(function (page, i) {
      page.lastChild.textContent = flow.getAttribute('data-maker') + '  |  ' + (i + 1) + ' / ' + pages.length;
    });
    src.parentNode.removeChild(src);
  }
  window.addEventListener('load', function () {
    (document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve()).then(build);
  });
})();
"""


def inline(text: str) -> str:
    """글을 HTML로 바꾼다. 태그는 모두 글자로 찍고 밑줄 <u>…</u>만 살린다(<보 기>는 글자다).

    빈칸 "(   )"처럼 이어진 공백은 브라우저가 하나로 줄이지 않게 줄바꿈 없는 공백으로 바꾼다.
    """
    out = re.sub(r'&lt;(/?)u&gt;', r'<\1u>', html.escape(text or '', quote=False))
    return re.sub(r' {2,}', lambda m: '&nbsp;' * len(m.group(0)), out)


def split_stem(stem: str) -> dict:
    """stem을 발문(첫 줄)과 그 아래 재료(예문 줄, 선택지 표 머리, 그림, <보 기> 제목)로 나눈다."""
    lines = [line.strip() for line in (stem or '').replace('\r\n', '\n').split('\n') if line.strip()]
    parts = {'prompt': lines[0] if lines else '', 'table_head': [], 'images': [], 'view_title': '', 'lines': []}
    for line in lines[1:]:
        table, image = TABLE_LINE.match(line), IMAGE_LINE.match(line)
        if table:
            parts['table_head'] = [cell.strip() for cell in table.group(1).split('|')]
        elif image:
            parts['images'].append(image.group(1).strip())
        elif VIEW_LINE.match(line):
            parts['view_title'] = line
        else:
            parts['lines'].append(line)
    return parts


def emphasize(prompt_html: str) -> str:
    """발문의 부정어·강조어에 굵은 밑줄을 넣는다. 이미 <u>로 감싼 낱말은 한 번만 찍는다."""
    def repl(match):
        word = match.group(2) or match.group(1)
        return '<u class="em">%s</u>' % word if word in EMPHASIS_WORDS else match.group(0)
    return EMPHASIS.sub(repl, prompt_html)


def score_label(question: dict) -> str:
    """발문 끝에 붙는 배점: (2점), 복수정답은 (정답 2개/4점)."""
    if question.get('multi'):
        return '(정답 %d개/%s점)' % (len(question.get('answer') or []), question.get('score'))
    return '(%s점)' % question.get('score')


def prompt_html(question: dict, prompt: str) -> str:
    """발문 HTML. 배점 표기가 이미 있으면 그대로 두고, 없으면 score·multi로 붙인다."""
    text = emphasize(inline(prompt))
    if SCORE_LABEL.search(text):
        return SCORE_LABEL.sub(lambda m: '<span class="pts">%s</span>' % m.group(0), text, count=1)
    return '%s <span class="pts">%s</span>' % (text, score_label(question))


def display_width(text: str) -> int:
    """태그를 뺀 글의 너비. 한글은 2칸으로 센다."""
    plain = re.sub(r'<[^>]+>', '', text or '').strip()
    return sum(2 if '가' <= ch <= '힣' else 1 for ch in plain)


def choice_layout(options: list[str]) -> str:
    """짧은 낱말 선택지는 가로(row), 문장이거나 여러 줄인 선택지는 세로(vertical)."""
    short = options and all('\n' not in o and display_width(o) <= ROW_CHOICE_MAX_WIDTH for o in options)
    return 'row' if short else 'vertical'


def table_cells(option: str, count: int) -> list[str] | None:
    """'ⓐ: in / ⓑ: with / ⓒ: for' 를 칸 값 ['in', 'with', 'for']로. 칸 수가 머리와 다르면 None."""
    cells = [re.sub(r'^\s*\S+\s*:\s*', '', cell).strip() for cell in (option or '').split(' / ')]
    return cells if len(cells) == count and all(cells) else None


def option_html(option: str) -> str:
    """선택지 한 개의 글. 줄바꿈은 줄로 나누고 ⇒ 줄은 들여 쓰고 대화의 A:/B:는 굵게."""
    out = []
    for line in (option or '').replace('\r\n', '\n').split('\n'):
        talk = DIALOGUE.match(line.strip())
        if talk:
            out.append('<p><b>%s:</b> %s</p>' % (talk.group(1), inline(talk.group(2))))
        elif line.strip().startswith('⇒'):
            out.append('<p class="arrow">%s</p>' % inline(line.strip()))
        else:
            out.append('<p>%s</p>' % inline(line.strip()))
    return ''.join(out)


def choices_html(options: list[str], table_head: list[str]) -> str:
    """선택지 HTML: 표 머리가 있고 모든 선택지가 칸으로 나뉘면 표, 아니면 가로·세로 목록."""
    rows = [table_cells(o, len(table_head)) for o in options] if table_head else []
    if rows and all(rows):
        head = '<tr><th></th>%s</tr>' % ''.join('<th><u>%s</u></th>' % inline(h) for h in table_head)
        body = ''.join('<tr><td>%s</td>%s</tr>' % (MARKS[i], ''.join('<td>%s</td>' % inline(c) for c in cells))
                       for i, cells in enumerate(rows))
        return '<table class="choice-table">%s%s</table>' % (head, body)
    items = ''.join('<div class="ch%s"><span class="ch-mark">%s</span><div class="ch-text">%s</div></div>'
                    % (' multi-line' if '\n' in o else '', MARKS[i], option_html(o)) for i, o in enumerate(options))
    return '<div class="choices %s">%s</div>' % (choice_layout(options), items)


def image_data_uri(ref: str, asset_dir: Path | None = None, static_dir: Path = STATIC_DIR) -> str | None:
    """그림 줄의 경로를 data URI로. 저장소 static 아래 같은 경로 → asset_dir의 같은 파일 이름 순서로 찾는다."""
    candidates = [static_dir / ref.lstrip('/')] + ([Path(asset_dir) / Path(ref).name] if asset_dir else [])
    for path in candidates:
        if path.is_file() and path.suffix.lower() in MIME:
            return 'data:%s;base64,%s' % (MIME[path.suffix.lower()], base64.b64encode(path.read_bytes()).decode('ascii'))
    return None


def figure_html(ref: str, framed: bool, asset_dir: Path | None = None, static_dir: Path = STATIC_DIR) -> str:
    """그림 한 장. 파일을 못 찾으면 빨간 점선 칸에 파일 이름을 적어 눈에 띄게 한다."""
    uri = image_data_uri(ref, asset_dir, static_dir)
    if not uri:
        return '<div class="fig missing">[그림 없음: %s]</div>' % html.escape(Path(ref).name)
    return '<div class="fig%s"><img src="%s" alt="그림"></div>' % (' framed' if framed else '', uri)


def is_title(lines: list[str]) -> bool:
    """지문의 첫 줄이 제목인가: 뒤에 본문이 있고, 짧고, 대화가 아니고, 문장 부호로 끝나지 않는다."""
    if len(lines) < 2:
        return False
    first = re.sub(r'<[^>]+>', '', lines[0]).strip()
    return bool(first) and len(first) <= TITLE_MAX_CHARS and not DIALOGUE.match(first) \
        and first[0] not in '◦*' and first[-1] not in '.?!"”’)'


def body_html(lines: list[str]) -> tuple[str, str]:
    """상자 안 줄들과 상자 아래 각주(*로 시작하는 줄)를 HTML로 돌려준다."""
    body, notes = [], []
    for line in lines:
        talk = DIALOGUE.match(line)
        if line.startswith('*'):
            notes.append(inline(line))
        elif talk:
            body.append('<p class="dlg"><b>%s:</b><span>%s</span></p>' % (talk.group(1), inline(talk.group(2))))
        elif line.startswith('◦'):
            body.append('<p class="dot">%s</p>' % inline(line))
        elif line.startswith('⇒'):
            body.append('<p class="arrow">%s</p>' % inline(line))
        else:
            body.append('<p%s>%s</p>' % (' class="para"' if len(line) >= PROSE_MIN_CHARS else '', inline(line)))
    return ''.join(body), ''.join('<p class="notes">%s</p>' % note for note in notes)


def box_html(lines: list[str], view_title: str = '', titled: bool = False) -> str:
    """테두리 상자. titled면 첫 줄이 제목인지 보고 가운데 굵게, view_title이 있으면 위 테두리에 <보 기>."""
    lines = [line.strip() for line in lines if line.strip()]
    if not lines:
        return ''
    title = ''
    if titled and is_title(lines):
        title, lines = '<p class="ptitle">%s</p>' % inline(lines[0]), lines[1:]
    body, notes = body_html(lines)
    label = '<span class="box-title">%s</span>' % inline(view_title) if view_title else ''
    return '<div class="box%s">%s%s%s</div>%s' % (' view' if view_title else '', label, title, body, notes)


def passage_lines(text: str) -> list[str]:
    return [line.strip() for line in (text or '').replace('\r\n', '\n').split('\n') if line.strip()]


def is_dialogue(lines: list[str]) -> bool:
    """각주를 뺀 모든 줄이 'G: …' 꼴이면 대화다."""
    spoken = [line for line in lines if not line.startswith('*')]
    return bool(spoken) and all(DIALOGUE.match(line) for line in spoken)


def group_head(first_no: int, last_no: int, lines: list[str]) -> str:
    """공통 지문 머리말: [12~14] 다음 글을(대화를) 읽고 물음에 답하시오."""
    kind = '대화를' if is_dialogue(lines) else '글을'
    return '<div class="group-head">[%d~%d] 다음 %s 읽고 물음에 답하시오.</div>' % (first_no, last_no, kind)


def passage_groups(questions: list[dict]) -> list[str]:
    """이웃한 문항이 같은 지문을 쓰면 같은 묶음 이름을 준다. 혼자 쓰는 지문은 빈 이름."""
    groups = [''] * len(questions)
    for i, q in enumerate(questions):
        text = exam_check._plain(q.get('sourceText'))
        if not text:
            continue
        prev_same = i > 0 and exam_check._plain(questions[i - 1].get('sourceText')) == text
        next_same = i + 1 < len(questions) and exam_check._plain(questions[i + 1].get('sourceText')) == text
        if prev_same:
            groups[i] = groups[i - 1]
        elif next_same:
            groups[i] = 'p%d' % (i + 1)
    return groups


def question_html(question: dict, no: int, own_passage: bool = True, asset_dir: Path | None = None,
                  static_dir: Path = STATIC_DIR) -> str:
    """문항 하나: 번호·발문·배점 → 그림 → 예문 상자 → 선택지(또는 조건·답란).

    own_passage가 False면 공통 지문 묶음의 문항이라 sourceText를 다시 찍지 않는다.
    """
    stem = split_stem(question.get('stem'))
    lines = stem['lines']
    passage = passage_lines(question.get('sourceText')) if own_passage else []
    # 승인본: 그림만 있는 문항은 그림을 테두리 상자에 넣고, <보 기> 상자가 따라오면 그림은 그대로 둔다.
    framed = not (lines or passage)
    out = ['<div class="q-head"><span class="q-no">%d.</span><div class="q-text">%s</div></div>'
           % (no, prompt_html(question, stem['prompt']))]
    out.extend(figure_html(ref, framed, asset_dir, static_dir) for ref in stem['images'])
    out.append(box_html(passage, titled=True))
    out.append(box_html(lines, stem['view_title']))
    if question.get('type') == 'mc':
        out.append(choices_html(question.get('options') or [], stem['table_head']))
    else:
        out.append(box_html(question.get('conditions') or [], '<조 건>'))
        out.append('<div class="ans-line"></div>' * SHORT_ANSWER_LINES)
    return '<div class="q" data-qnum="%d">%s</div>' % (no, ''.join(out))


def pieces_html(questions: list[dict], asset_dir: Path | None = None, static_dir: Path = STATIC_DIR) -> list[str]:
    """단 사이에서 갈라지면 안 되는 조각 목록. 공통 지문은 머리말·지문·첫 문항이 한 조각이다.

    (왼쪽 단에 지문만 들어갈 때는 쪽 나누기 스크립트가 지문과 첫 문항을 같은 쪽의 두 단으로 나눈다.)
    """
    groups = passage_groups(questions)
    pieces = []
    for i, question in enumerate(questions):
        lead = ''
        if groups[i] and (i == 0 or groups[i - 1] != groups[i]):
            last = max(j for j in range(i, len(questions)) if groups[j] == groups[i])
            lines = passage_lines(question.get('sourceText'))
            lead = group_head(i + 1, last + 1, lines) + box_html(lines, titled=True)
        body = question_html(question, i + 1, own_passage=not groups[i], asset_dir=asset_dir, static_dir=static_dir)
        pieces.append('<div class="piece">%s%s</div>' % (lead, body))
    return pieces


def logo_data_uri(static_dir: Path = STATIC_DIR) -> str:
    """F2R 로고 data URI. 스크립트 옆 복사본 → 저장소 static/img 순서로 찾는다. 없으면 만들지 않는다."""
    for path in (Path(__file__).resolve().parent / LOGO_NAME, static_dir / 'img' / LOGO_NAME):
        if path.is_file():
            return 'data:image/svg+xml;base64,' + base64.b64encode(path.read_bytes()).decode('ascii')
    raise FileNotFoundError('F2R 로고(%s)를 찾지 못했습니다. 스크립트 옆이나 static/img에 두세요.' % LOGO_NAME)


def header_html(title: str, sub: str, logo_uri: str) -> str:
    """1쪽 머리 표: 왼쪽 위 F2R 로고(학교 이름 자리) + 제목 / 과목·학년·범위 / 이름·점수 칸."""
    return ('<div class="exam-header" id="exam-header"><table>'
            '<tr class="title-row"><td colspan="4"><img class="logo" src="%s" alt="F2R">%s</td></tr>'
            '<tr class="sub-row"><td colspan="4">%s</td></tr>'
            '<tr><td class="label">이름</td><td></td><td class="label">점수</td><td></td></tr>'
            '</table></div>') % (logo_uri, html.escape(title), html.escape(sub))


def render_paper(exam: dict, asset_dir: Path | None = None, static_dir: Path = STATIC_DIR) -> str:
    """문제지 HTML 한 장(자체 완결: 글꼴은 PC 글꼴, 그림·로고는 data URI)."""
    questions = exam.get('questions') or []
    title = exam.get('examTitle') or '내신 예상문제'
    sub = exam.get('subTitle') or '%d문항' % len(questions)
    run = '<div class="run-header" id="run-header"><span>%s</span><span>%s</span></div>' % (
        html.escape(exam.get('runHeader') or title), html.escape(exam.get('subject') or '영어'))
    return ('<!doctype html>\n<html lang="ko"><head><meta charset="utf-8"><title>%s</title>\n<style>%s</style></head>\n'
            '<body>\n<div id="src">%s%s\n<div id="flow" data-maker="%s">\n%s\n</div></div>\n<script>%s</script>\n'
            '</body></html>\n') % (
        html.escape(title), CSS, header_html(title, sub, logo_data_uri(static_dir)), run, html.escape(MAKER),
        '\n'.join(pieces_html(questions, asset_dir, static_dir)), PAGINATE_JS)
