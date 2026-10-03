"""WIKI ④ 학생 약점 DNA — 오답 원문 파싱 · 반복 약점 판정 · 평가원 출제 DNA 연결.

SOURCE OF TRUTH는 (1) ADMIN이 ③에 등록한 학생 오답 HTML 원문, (2) ①②에 등록된 평가원
기출 문항과 출제 DNA다. 이 모듈은 두 자료만으로 분석하며 새로운 문제·근거를 만들어내지 않는다.

불변 규칙 (docs/MONDAY_DAY2_HANDOFF.md §0 · §3-12):
- 원문에 없는 정보(학생이 고른 선지 등)는 지어내지 않고 None으로 둔다. 화면은
  "원문 미표기"로 표시한다.
- 약점은 "2개 이상 서로 다른 시험(source) × 2개 이상 문항"에서 반복된 유형만 확정한다.
  단발 오답은 observations("관찰")로만 남기고 약점 CLAIM을 만들지 않는다.
- 모든 약점 카드는 근거 문항(item_id · source · number · answer)을 그대로 실어
  ③ 오답 원문과 연결한다.

의존성: 표준 라이브러리만 사용한다 (main.py와 같은 방식으로 import 된다).
"""
from __future__ import annotations

import json
import re
from datetime import datetime

ARTICLE_RE = re.compile(r'<article\s+class=["\']wrong-answer["\'][^>]*>.*?</article>', re.S | re.I)
TAG_RE = re.compile(r'<[^>]+>')
BLOCK_RE = re.compile(r'</?(?:div|section|p|h1|h2|h3|li|pre|dd|dt|dl|article|ol|ul)\b[^>]*>', re.I)
WS_RE = re.compile(r'[ \t\r\f\v]+')
CIRCLED = {'①': '1', '②': '2', '③': '3', '④': '4', '⑤': '5'}


def init_schema(conn) -> None:
    """④ 분석 결과를⑤(개인별 대비 → 맞춤 복습 문제지)가 그대로 소비할 수 있는 구조로 보존한다."""
    conn.execute('''CREATE TABLE IF NOT EXISTS csat_weakness_analysis (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        student_id INTEGER NOT NULL UNIQUE,
        wrong_html_id INTEGER,
        wrong_total INTEGER NOT NULL DEFAULT 0,
        dna_count INTEGER NOT NULL DEFAULT 0,
        observation_count INTEGER NOT NULL DEFAULT 0,
        student_answer_known INTEGER NOT NULL DEFAULT 0,
        payload_json TEXT NOT NULL DEFAULT '{}',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL DEFAULT '',
        FOREIGN KEY (student_id) REFERENCES students(id),
        FOREIGN KEY (wrong_html_id) REFERENCES csat_wrong_html(id)
    )''')
    conn.execute('''CREATE TABLE IF NOT EXISTS csat_weakness_dna (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        analysis_id INTEGER NOT NULL,
        student_id INTEGER NOT NULL,
        dna_key TEXT NOT NULL,
        category TEXT NOT NULL,
        title TEXT NOT NULL,
        wrong_count INTEGER NOT NULL DEFAULT 0,
        source_count INTEGER NOT NULL DEFAULT 0,
        payload_json TEXT NOT NULL DEFAULT '{}',
        created_at TEXT NOT NULL,
        UNIQUE (analysis_id, dna_key),
        FOREIGN KEY (analysis_id) REFERENCES csat_weakness_analysis(id),
        FOREIGN KEY (student_id) REFERENCES students(id)
    )''')
    conn.execute('CREATE INDEX IF NOT EXISTS idx_csat_weakness_dna_student ON csat_weakness_dna(student_id, dna_key)')


# ── 오답 원문 파싱 ─────────────────────────────────────────────────────────────

def _strip_tags(fragment: str) -> str:
    fragment = BLOCK_RE.sub('\n', fragment)
    text = TAG_RE.sub('', fragment)
    for entity, char in (('&nbsp;', ' '), ('&#x27;', "'"), ('&amp;', '&'), ('&lt;', '<'), ('&gt;', '>'), ('&quot;', '"')):
        text = text.replace(entity, char)
    lines = [WS_RE.sub(' ', line).strip() for line in text.split('\n')]
    return '\n'.join(line for line in lines if line)


def _attr(head: str, name: str) -> str:
    match = re.search(rf'{name}=["\']([^"\']*)["\']', head)
    return match.group(1).strip() if match else ''


def _answer_key(value: str) -> str:
    value = (value or '').strip()
    return CIRCLED.get(value, value)


def parse_wrong_items(raw: bytes | str) -> list[dict]:
    """오답 HTML 원문에서 문항 레코드를 읽는다. 원문에 없는 필드는 채우지 않는다.

    반환 항목: item_id(원문 article id) · source(시험/모의 지문 자료명) · number ·
    type_label · answer(정답, 원문 표기 그대로) · student_answer(원문에 없으면 None) ·
    excerpt(지문/발문 앞부분) · index(원문 순서).
    """
    if isinstance(raw, bytes):
        raw = raw.decode('utf-8', 'replace')
    items: list[dict] = []
    for index, block in enumerate(ARTICLE_RE.findall(raw), start=1):
        head_end = block.find('>')
        head = block[:head_end + 1]
        item_id = _attr(head, 'id') or f'item-{index}'
        source = _attr(head, 'data-source')
        number = _attr(head, 'data-number')
        type_label = _attr(head, 'data-type')
        answer = _attr(head, 'data-answer')
        body = block[head_end + 1:]
        if not type_label:
            found = re.search(r'<dt>\s*유형\s*</dt>\s*<dd>(.*?)</dd>', body, re.S | re.I)
            if not found:
                found = re.search(r'<b>\s*유형\s*:</b>\s*([^<]+)', body, re.S | re.I)
            type_label = _strip_tags(found.group(1)).strip() if found else ''
        if not answer:
            found = re.search(r'<dt>\s*정답\s*</dt>\s*<dd>(.*?)</dd>', body, re.S | re.I)
            if not found:
                found = re.search(r'<b>\s*정답\s*:</b>\s*([^<]+)', body, re.S | re.I)
            answer = _strip_tags(found.group(1)).strip() if found else ''
        passage = re.search(r'<div\s+class=["\']passage["\'][^>]*>(.*?)</div>', body, re.S | re.I)
        pre = re.search(r'<pre[^>]*>(.*?)</pre>', body, re.S | re.I)
        source_text = _strip_tags(passage.group(1) if passage else (pre.group(1) if pre else body))
        items.append({
            'item_id': item_id,
            'source': source or '출처 미표기',
            'number': number,
            'type_label': type_label or '유형 미표기',
            'answer': answer,
            'answer_known': bool(answer) and answer != '미확인',
            # 원문(사진 판독본)에는 학생이 고른 선지가 적혀 있지 않다 — 추정하지 않는다.
            'student_answer': None,
            'excerpt': source_text[:400],
            'index': index,
        })
    return items


# ── 유형 분류 (원문 표기 → 표준 csat 유형 id) ─────────────────────────────────

TYPE_BY_LABEL = {
    '빈칸': 'blank', '빈칸 추론': 'blank',
    '글의 순서': 'order', '순서': 'order',
    '문장 삽입': 'insertion',
    '어법': 'grammar',
    '어휘': 'vocabulary', '어휘 선택': 'vocabulary', '문맥상 낱말 쓰임': 'vocabulary',
    '밑줄 의미': 'implication', '함축 의미': 'implication',
    '제목': 'title', '주제': 'topic', '요지': 'main_idea',
    '필자 주장': 'claim', '주장': 'claim',
    '무관한 문장': 'irrelevant',
    '요약': 'summary', '내용 일치': 'content_match', '내용 불일치': 'content_match',
    '안내문': 'notice', '도표': 'chart', '목적': 'purpose', '심경': 'emotion',
}

# category는 학생 오답 근거로 화면에 표시하는 5분류(어휘/구문/논리/문항 풀이/오답 행동) 중
# "오답 행동"은 원문에 판단 과정 기록이 없어 표시하지 않는다(§0 근거 없는 분석 금지).
TYPE_META = {
    'blank': {
        'name': '빈칸', 'category': '문항 풀이',
        'title': '빈칸 — 문맥·논리 흐름으로 빈자리 역할 확정',
        'demand': '빈칸 앞뒤의 논리 신호(인과·역접·예시)를 읽어 빈자리가 담당할 역할을 정하고 어휘까지 확정하는 판단',
        'steps': ['빈칸 앞뒤 1~2문장만 먼저 읽고 접속 신호(역접·인과·예시)에 표시',
                  '빈칸이 채워야 할 역할을 한 단어로 확정(주장/예시/결론/대조)',
                  '선택지 5개를 그 역할 기준으로 먼저 정리',
                  '동일 어휘를 그대로 반복하는 선지는 워드에코 함정 후보로 미리 제외',
                  '최종 선택 전, 자리를 채운 문장을 앞뒤와 이어서 낭독 확인'],
    },
    'order': {
        'name': '글의 순서', 'category': '논리',
        'title': '글의 순서 — 제시문 연결 순서 판단',
        'demand': '제시문들 사이의 지시어·재진술·인과/대조 연결고리를 찾아 순서를 배치하는 판단',
        'steps': ['제시문마다 앞뒤 문장의 접속 신호와 지시어(it/they/this)에 밑줄',
                  '앞 문장의 마지막 어휘와 뒷 문장의 재진술을 짝짓기(의미 재진술)',
                  '짝이 확정된 문장 쌍부터 임시 배치',
                  '남은 제시문은 시작·마무리 신호(예시/결론)로 자리 확정',
                  '배치 결과를 소리 내어 이어 읽고 끊김이 없으면 채택'],
    },
    'insertion': {
        'name': '문장 삽입', 'category': '논리',
        'title': '문장 삽입 — 연결고리 위치 판단',
        'demand': '주어진 문장의 지시어·재진술 어휘가 가리키는 근거 문장을 찾아 (①~⑤) 위치를 확정하는 판단',
        'steps': ['주어진 문장의 지시어·재진술 어휘(대명사, 반복·재구성 어휘)에 밑줄',
                  '본문에서 그 어휘가 가리키는 근거 문장 위치를 먼저 찾기',
                  '위치 후보마다 앞 문장 끝 → 주어진 문장 시작 → 뒤 문장 시작을 잇고 어색함 비교',
                  '들어가기에 가장 자연스러운 위치 1곳만 확정(여러 곳이 어색하면 재검토)'],
    },
    'irrelevant': {
        'name': '무관한 문장', 'category': '논리',
        'title': '무관한 문장 — 본문 주장과의 관련성 판단',
        'demand': '각 문단의 주장과 주어진 문장의 관련성을 대조해 본문에 도움이 되지 않는 문장을 가려내는 판단',
        'steps': ['각 문단의 주제 문장(주장)을 한 줄로 요약',
                  '주어진 문장이 어느 문단의 주장을 지지·설명하는지 연결 확인',
                  '어떤 문단의 주장과도 연결되지 않는 문장 후보를 표시',
                  '표시된 문장을 본문에서 빼도 흐름이 유지되는지 확인 후 선택'],
    },
    'grammar': {
        'name': '어법', 'category': '구문',
        'title': '어법 — 밑줄 구문의 문법 정합 판단',
        'demand': '밑줄 친 구문의 주어·동사·수식 구조를 분해해 어색한 요소 하나를 가려내는 판단',
        'steps': ['밑줄 구문마다 주어와 동사 위치를 먼저 확정',
                  '시제·태·수·격·품사(형용사/부사)를 하나씩 점검',
                  '구문 전체가 아니라 의심되는 요소 하나만 좁혀 비교',
                  '나머지 4개가 왜 정상인지 문법 근거로 확인 후 확정'],
    },
    'vocabulary': {
        'name': '어휘', 'category': '어휘',
        'title': '어휘 — 문맥상 낱말 의미 확정',
        'demand': '글 전체의 흐름 속에서 해당 낱말의 문맥 의미를 좁혀 선택하는 판단',
        'steps': ['해당 문장만 보지 말고 앞뒤 1~2문장의 흐름을 먼저 확인',
                  '보기 낱말들의 기본 뜻 중 그 자리에 쓰일 수 없는 것부터 제외',
                  '정답 후보를 문장에 넣어 읽어 자연스러운 하나 선택',
                  '동의어·반의어 관계(예: scarce↔abundant)가 글의 방향과 맞는지 최종 확인'],
    },
    'implication': {
        'name': '밑줄·함축 의미', 'category': '어휘',
        'title': '밑줄 표현의 함축 의미 — 문맥에서 함축 추론',
        'demand': '밑줄 표현이 글 속에서 실제로 함축하는 의미를 문맥 근거로 추론하는 판단',
        'steps': ['밑줄 표현이 등장한 문장과 직전 문장을 정독',
                  '표현의 사전적 뜻이 아니라 글 속 역할(비유/함축)으로 해석',
                  '선택지를 문장에 대입해 글의 방향과 충돌하는 것부터 제외',
                  '직접 근거 문장이 떠오르지 않으면 다음 문단까지 근거를 넓혀 재확인'],
    },
    'title': {
        'name': '제목', 'category': '문항 풀이',
        'title': '제목 추론 — 변화 방향·핵심 소재 포착',
        'demand': '글의 전개 방향(변화·미해결·갈등)을 한 문장으로 요약해 제목 후보를 고르는 판단',
        'steps': ['첫 문단과 마지막 문단에서 "무엇이 달라졌는가"를 한 줄로 정리',
                  '글에서 가장 비중 있게 다뤄진 소재 1~2개를 확정',
                  '선택지 중 위 두 조건을 모두 만족하는 것만 후보로 유지',
                  '본문에 없는 새로운 사건을 가져오는 제목은 즉시 제외'],
    },
    'topic': {
        'name': '주제', 'category': '문항 풀이',
        'title': '주제 추론 — 글 전체가 말하려는 중심',
        'demand': '글 전체가 말하려는 중심 주장을 요약해 주제 후보를 고르는 판단',
        'steps': ['각 문단의 첫 문장(주제 문장)만 모아 읽기',
                  '반복되는 핵심 어휘 2~3개를 확정',
                  '선택지가 "그 어휘들에 관한 주장"인지 확인',
                  '지문의 일부 사실만 옮긴 선택지는 제외'],
    },
    'main_idea': {
        'name': '요지', 'category': '문항 풀이',
        'title': '요지 파악 — 결론문과 사실 진술 구분',
        'demand': '필자의 결론문을 사실·예시 진술과 구분해 요지를 고르는 판단',
        'steps': ['However / Thus / Therefore 등 전환·결론 신호 문장에 표시',
                  '그 신호 바로 뒤 문장을 요지 후보로 확정',
                  '선택지 중 결론문과 같은 의미인 것(paraphrase)만 유지',
                  '본문의 세부 사실을 반복하는 선택지는 요지가 아니므로 제외'],
    },
    'claim': {
        'name': '주장', 'category': '문항 풀이',
        'title': '필자 주장 — 주장과 근거 구분',
        'demand': '필자의 주장을 근거 진술과 구분해 정확한 형태로 확정하는 판단',
        'steps': ['"필자는 무엇을 주장하는가"를 한 문장으로 먼저 기술',
                  '그 문장의 근거(예시·인용·수치)를 곁에 따로 표시',
                  '선택지가 주장과 일치하는지, 근거만 옮겼는지 구분',
                  '범위를 넓히거나 줄인 선택지는 즉시 제외'],
    },
    'summary': {
        'name': '요약', 'category': '문항 풀이',
        'title': '요약 — 정보 누락·왜곡 없는 요약 선택',
        'demand': '요약문의 각 항목이 본문과 정확히 일치하는지 대조하는 판단',
        'steps': ['요약문 각 항목에 해당 본문 문장 위치를 표시',
                  '범위 확대·주체 전환이 있는 항목부터 표시',
                  '본문에 없는 내용을 추가한 항목 제외',
                  '전부 일치하는 조합만 남겨 선택'],
    },
    'other': {
        'name': '기타 유형', 'category': '문항 풀이',
        'title': '기타 유형 — 원문 표기 기준 반복 오류',
        'demand': '원문에 표기된 문항 유형이 요구하는 판단',
        'steps': ['문항 유형이 요구하는 판단 지점을 먼저 한 줄로 정리',
                  '근거 문장을 지문에서 찾아 표시',
                  '선택지를 근거와 대조해 제거 후 선택'],
    },
}

FALLBACK_META = dict(TYPE_META['other'])


def type_key(label: str) -> str:
    label = (label or '').strip()
    return TYPE_BY_LABEL.get(label, f'other:{label}' if label else 'other:미상')


def meta_for(key: str) -> dict:
    if key in TYPE_META:
        return TYPE_META[key]
    # other:<원문 표기> — 표기된 유형명을 카드 제목에 그대로 노출한다.
    label = key.split(':', 1)[1] if ':' in key else '기타 유형'
    meta = dict(FALLBACK_META)
    meta['name'] = label
    meta['title'] = f'{label} — 원문 표기 기준 반복 오류'
    return meta


# ── 평가원 출제 DNA ↔ 학생 오답 유형 연결 ─────────────────────────────────────

def collect_exam_links(questions: list[dict], entries: list[dict], claims: list[dict]) -> dict:
    """평가원 문항-DNA를 학생 오답 유형(csat_type_id) 단위로 묶는다.

    questions: {'id','exam_id','question_no','csat_type_id','exam_label'}
    entries:   문항 DNA/TRAP 행 (+ exam_label, question_no, csat_type_id)
    claims:    회차 CLAIM 행 (+ exam_label)
    반환: {type_key: {'entries': [...], 'claims': [...], 'tags': [{'entry_type','tag','count'}]}}
    """
    question_type = {question['id']: question for question in questions}
    links: dict[str, dict] = {}

    def bucket(key: str) -> dict:
        return links.setdefault(key, {'entries': [], 'claims': [], 'tags': []})

    tag_counter: dict[str, dict] = {}
    for entry in entries:
        question = question_type.get(entry.get('question_id'))
        key = question['csat_type_id'] if question else (entry.get('csat_type_id') or 'other:미상')
        item = {
            'exam_id': entry.get('exam_id'),
            'exam_label': entry.get('exam_label') or '',
            'question_no': entry.get('question_no') or (question or {}).get('question_no'),
            'entry_type': entry.get('entry_type'),
            'tag': entry.get('tag'),
            'choice_no': entry.get('choice_no'),
            'note': entry.get('note') or '',
            'provenance': entry.get('provenance') or '',
            'expert': entry.get('expert') or '',
            'agreement_kind': entry.get('agreement_kind') or '',
            'agreement': bool(entry.get('agreement')),
            'timestamp_start': entry.get('timestamp_start'),
            'video_url': entry.get('video_url') or entry.get('expert_video_url') or '',
            'status': entry.get('status') or '',
            'evidence': entry.get('evidence') or [],
        }
        bucket(key)['entries'].append(item)
        counter = tag_counter.setdefault((key, item['entry_type'], item['tag']), {'entry_type': item['entry_type'], 'tag': item['tag'], 'count': 0})
        counter['count'] += 1
    for (key, entry_type, tag), counter in tag_counter.items():
        bucket(key)['tags'].append({'entry_type': entry_type, 'tag': tag, 'count': counter['count']})
    for claim in claims:
        for key, data in links.items():
            if claim.get('tag') and claim['tag'] in {tag['tag'] for tag in data['tags']}:
                data['claims'].append({
                    'exam_id': claim.get('exam_id'),
                    'exam_label': claim.get('exam_label') or '',
                    'claim_type': claim.get('claim_type'),
                    'tag': claim.get('tag'),
                    'claim_text': claim.get('claim_text') or '',
                    'provenance': claim.get('provenance') or '',
                    'expert': claim.get('expert') or '',
                    'agreement_kind': claim.get('agreement_kind') or '',
                    'agreement': bool(claim.get('agreement')),
                    'timestamp_start': claim.get('timestamp_start'),
                    'video_url': claim.get('video_url') or claim.get('expert_video_url') or '',
                    'status': claim.get('status') or '',
                    'evidence': claim.get('evidence') or [],
                })
    for data in links.values():
        data['entries'].sort(key=lambda item: (item['exam_label'], item['question_no'] or 0, item['entry_type'] or '', item['tag'] or ''))
        data['tags'].sort(key=lambda item: (item['entry_type'] or '', item['tag'] or ''))
        data['claims'].sort(key=lambda item: (item['exam_label'], item['claim_type'] or ''))
    return links


# ── 약점 DNA 생성 ─────────────────────────────────────────────────────────────

def analyze(items: list[dict], exam_links: dict, student_name: str, wrong_html_id: int | None = None,
            min_sources: int = 2, min_items: int = 2) -> dict:
    """오답 원문 항목과 평가원 DNA 연결을 넣어 학생 약점 DNA 구조를 만든다.

    반복 규칙: 서로 다른 시험(source) min_sources개 이상 × 문항 min_items개 이상에서만
    약점으로 확정하고, 나머지는 observations("관찰")로 남긴다.
    """
    groups: dict[str, list[dict]] = {}
    for item in items:
        groups.setdefault(type_key(item['type_label']), []).append(item)

    dna_cards: list[dict] = []
    observations: list[dict] = []
    for key, rows in groups.items():
        meta = meta_for(key)
        sources = sorted({row['source'] for row in rows if row['source'] and row['source'] != '출처 미표기'})
        links = exam_links.get(key, {'entries': [], 'claims': [], 'tags': []})
        evidence = [
            {'item_id': row['item_id'], 'source': row['source'], 'number': row['number'],
             'type_label': row['type_label'], 'student_answer': row['student_answer'],
             'answer': row['answer'], 'answer_known': row['answer_known'], 'excerpt': row['excerpt']}
            for row in sorted(rows, key=lambda row: (row['source'], _numeric(row['number']), row['index']))
        ]
        if len(rows) >= min_items and len(sources) >= min_sources:
            dna_cards.append({
                'key': key,
                'category': meta['category'],
                'title': meta['title'],
                'type_name': meta['name'],
                'wrong_count': len(rows),
                'source_count': len(sources),
                'sources': sources,
                'student_answer_known': any(row['student_answer'] for row in rows),
                'problem': (f"등록된 오답 원문 {len(rows)}건이 「{meta['name']}」 유형에 모여 있습니다. "
                            f"{len(sources)}개의 서로 다른 시험지에서 반복된 틀림이라, "
                            f"한두 번의 실수가 아니라 같은 판단에서 되풀이되는 패턴입니다."),
                'failed_process': (f"확인 가능한 근거는 결과와 유형뿐입니다. {len(rows)}건 전부 "
                                   f"{meta['demand']}를 요구하는 문항이고, 그 판단 단계에서 반복 실패가 확인됩니다. "
                                   f"학생이 고른 선지는 원문에 표기되어 있지 않아 "
                           f"\"어떤 보기에 끌렸는지\" 단계까지의 확인은 못했습니다."),
                'evidence': evidence,
                'exam_dna': links['entries'],
                'exam_dna_tags': links['tags'],
                'claims': links['claims'],
                'steps': list(meta['steps']),
            })
        else:
            observations.append({
                'key': key,
                'category': meta['category'],
                'title': meta['title'],
                'type_name': meta['name'],
                'wrong_count': len(rows),
                'source_count': len(sources),
                'sources': sources,
                'reason': (f"반복 규칙(서로 다른 시험 {min_sources}개 이상 × 문항 {min_items}개 이상)을 넘기지 못한 "
                           f"관찰 건입니다. 약점으로 단정하지 않습니다."),
                'evidence': evidence,
            })
    dna_cards.sort(key=lambda card: (-card['wrong_count'], card['key']))
    observations.sort(key=lambda row: (-row['wrong_count'], row['key']))
    return {
        'student_name': student_name,
        'wrong_html_id': wrong_html_id,
        'wrong_total': len(items),
        'dna_count': len(dna_cards),
        'observation_count': len(observations),
        'student_answer_known': any(item['student_answer'] for item in items),
        'analyzed_at': datetime.now().isoformat(),
        'dna': dna_cards,
        'observations': observations,
        # ⑤ 개인별 대비 → 맞춤 복습 문제지가 그대로 소비하는 처방 구조.
        'prescription': [{'key': card['key'], 'category': card['category'], 'title': card['title'],
                          'wrong_count': card['wrong_count'], 'steps': card['steps']} for card in dna_cards],
    }


def _numeric(value: str) -> int:
    match = re.match(r'\d+', (value or '').strip())
    return int(match.group(0)) if match else 10 ** 6


def row_to_card(row) -> dict:
    """DB에 저장한 카드 행을 API 응답 형태로 되돌린다."""
    card = json.loads(row['payload_json'])
    card.setdefault('key', row['dna_key'])
    card.setdefault('category', row['category'])
    card.setdefault('title', row['title'])
    card.setdefault('wrong_count', row['wrong_count'])
    card.setdefault('source_count', row['source_count'])
    return card
