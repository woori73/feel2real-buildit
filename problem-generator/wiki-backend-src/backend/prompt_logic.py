"""
중등 내신 영어 문제생성기 HTML(V14.1 Gemini API 버전)의 AI 프롬프트 로직과
문항 후처리 로직을 그대로 이식한 모듈.

- TYPE_DEFS: 원본 HTML의 var TYPE_DEFS 를 그대로 옮김
- build_new_rules(): 원본 buildRules() 를 그대로 옮기되, SCHOOL_PROFILES 하드코딩 대신
  DB에서 읽은 Digital Twin(twin dict)을 학교 DNA 소스로 사용한다.
- build_transform_rules(): 원본 buildTransformRules() 를 그대로 옮기되, 마찬가지로
  DB Digital Twin을 학교 DNA 소스로 사용하고, 유형비율/출제규칙/난이도/함정패턴/
  선택지 스타일/서술형 스타일을 프롬프트에 명시적으로 포함한다.
- call_gemini_structured(): 원본 callGemini() 의 모델 폴백 체인 + 429 재시도 로직을 이식.
- prepare_question()/repair_transform_question(): 원본의 문항 후처리(밑줄, 빈칸, 영영풀이,
  순서/삽입 서식) 로직을 이식.
"""
import json
import re
import time
from urllib import error, request

MODEL_CANDIDATES = ['gemini-3.1-flash-lite', 'gemini-3.5-flash', 'gemini-flash-latest', 'gemini-2.5-flash', 'gemini-2.0-flash']

# 원본 HTML var TYPE_DEFS 그대로 이식 (문제 유형 카탈로그 · 자유 유형 선택 · 자동 추천에 사용)
TYPE_DEFS = [
    {"id": "grammar_error", "cat": "문법·어법", "label": "어법 오류 찾기", "prompt": "밑줄 친 부분의 수일치·시제·태·관계사·분사·준동사 오류를 판별"},
    {"id": "grammar_multi", "cat": "문법·어법", "label": "복합 어법·복수정답", "prompt": "여러 문법 요소를 동시에 검토하고 정답 2개를 모두 고르는 고난도 유형"},
    {"id": "sentence_transform", "cat": "문법·어법", "label": "문장 전환·동일 의미", "prompt": "핵심 문법을 사용해 의미가 같도록 문장을 전환하거나 바꿔쓰기"},
    {"id": "word_order", "cat": "문법·어법", "label": "어순 배열·문장 완성", "prompt": "주어진 어구를 문법적으로 배열해 완전한 영어 문장을 구성"},
    {"id": "context_vocab", "cat": "어휘", "label": "문맥 어휘·어색한 낱말", "prompt": "문맥상 적절한 어휘 또는 어색한 낱말을 판별"},
    {"id": "definition", "cat": "어휘", "label": "영영풀이·정의 매칭", "prompt": "영어 정의와 단어를 정확히 대응하고 미세한 정의 차이를 구별"},
    {"id": "synonym", "cat": "어휘", "label": "동의어·대체 표현", "prompt": "문맥을 유지하면서 바꿔 쓸 수 있는 단어나 표현을 판별"},
    {"id": "polysemy", "cat": "어휘", "label": "다의어·공통 단어", "prompt": "서로 다른 문맥에 공통으로 들어갈 다의어를 추론"},
    {"id": "content_match", "cat": "독해", "label": "내용 일치·불일치", "prompt": "지문의 사실·인과·세부정보와 선택지를 정밀 대조"},
    {"id": "blank", "cat": "독해", "label": "빈칸·연결어 추론", "prompt": "문맥과 논리 관계에 맞는 어구·연결어·문장을 빈칸에 선택"},
    {"id": "sequence", "cat": "독해", "label": "사건·글의 순서", "prompt": "시간 표지와 인과관계를 이용해 사건 또는 문단 순서를 배열"},
    {"id": "insertion", "cat": "독해", "label": "문장 삽입·논리 연결", "prompt": "지시어·연결어·응집성을 근거로 문장의 적절한 위치를 판단"},
    {"id": "main_idea", "cat": "독해", "label": "주제·제목·요지", "prompt": "글 전체의 핵심 주장·주제·제목을 추론"},
    {"id": "summary", "cat": "독해", "label": "요약문·정보 변환", "prompt": "지문을 요약문·표·메모·수칙 등 다른 형식으로 재구성"},
    {"id": "emotion", "cat": "추론·문학", "label": "인물 심리·태도 NOT", "prompt": "서사 속 인물의 감정·태도 중 드러나지 않은 것을 판별"},
    {"id": "discourse", "cat": "추론·문학", "label": "담화·함축 의미 추론", "prompt": "대사·표현의 숨은 의도와 문맥적 의미를 추론"},
    {"id": "transfer", "cat": "추론·문학", "label": "개념 전이·외부 사례 적용", "prompt": "지문에서 학습한 개념을 새로운 사례나 예문에 적용"},
    {"id": "irony", "cat": "추론·문학", "label": "반어·문학 개념", "prompt": "반어법·대조·상징 등 문학 개념을 새로운 보기에서 판별"},
    {"id": "communication", "cat": "의사소통", "label": "화행·대화 완성", "prompt": "요청·제안·동의·거절·충고 등 화행의 의도와 자연스러운 응답을 판단"},
    {"id": "roleplay", "cat": "의사소통", "label": "역할극·실용 상황", "prompt": "생활 속 역할극·만화·말풍선 상황에 적절한 표현을 선택"},
    {"id": "writing_cond", "cat": "서답형", "label": "조건 제한형 영작", "prompt": "단어 수·필수어·문법·완전문장 등 복수 조건을 만족하는 영작"},
    {"id": "writing_evidence", "cat": "서답형", "label": "근거 찾기·근거 영작", "prompt": "지문의 영어 근거를 찾아 쓰거나 자신의 말로 패러프레이징"},
    {"id": "writing_correction", "cat": "서답형", "label": "오류 수정·문장 재작성", "prompt": "제시된 영어 문장의 오류를 고쳐 완전한 문장으로 재작성"},
    {"id": "writing_summary", "cat": "서답형", "label": "요약·핵심문장 영작", "prompt": "지문 핵심을 제한된 단어 수와 지정 문법으로 영어 요약"},
]
TYPE_DEF_BY_ID = {t["id"]: t for t in TYPE_DEFS}

# 원본 HTML var SCHOOL_PROFILES 중 6개 기본 학교 프리셋을 그대로 이식.
# (integrated 통합형은 사용자가 개별 학교를 선택하는 현재 구조상 기본 시드에서 제외)
# 최초 실행 시 DB가 비어 있으면 이 6개를 자동으로 Digital Twin으로 저장한다.
DEFAULT_SCHOOL_PROFILES = [
    {"school": "신사중", "grade": "3학년", "desc": "복수정답·문학개념 전이·심리/담화 추론·조건형 영작",
     "types": ['grammar_multi', 'content_match', 'sequence', 'emotion', 'discourse', 'transfer', 'irony', 'writing_cond', 'writing_evidence', 'sentence_transform']},
    {"school": "신구중", "grade": "2학년", "desc": "영영풀이·패러프레이징·수동태/관계사 어법·복수정답·조건표 서답형",
     "types": ['definition', 'synonym', 'grammar_error', 'grammar_multi', 'content_match', 'writing_cond', 'writing_correction', 'sentence_transform']},
    {"school": "신구중", "grade": "3학년", "desc": "문학 통합·수능형 어휘/순서/빈칸·실용 화행·정보 변환·복합 영작",
     "types": ['context_vocab', 'sequence', 'blank', 'irony', 'communication', 'roleplay', 'summary', 'grammar_error', 'writing_cond', 'writing_summary']},
    {"school": "압구정중", "grade": "2학년", "desc": "내용일치·연결어·문맥어휘·어법·문장전환·현재완료·어순 영작",
     "types": ['content_match', 'blank', 'context_vocab', 'grammar_error', 'sentence_transform', 'word_order', 'definition', 'writing_cond']},
    {"school": "압구정중", "grade": "3학년", "desc": "문법 고배점·정밀 어휘·독해 추론·킬러 문항 중심의 복합 평가",
     "types": ['grammar_error', 'grammar_multi', 'context_vocab', 'definition', 'synonym', 'content_match', 'main_idea', 'blank', 'writing_correction', 'writing_cond']},
    {"school": "언북중", "grade": "3학년", "desc": "어휘·어법·내용이해를 결합하고 함정 선택지와 서술형 조건을 강화",
     "types": ['definition', 'context_vocab', 'grammar_error', 'content_match', 'blank', 'insertion', 'sequence', 'main_idea', 'writing_cond', 'writing_evidence']},
]

DIFFICULTY_LABELS = {
    'actual': '실제 시험 수준의 중상 난이도',
    'easy': '기본 개념 중심',
    'hard': '복합 사고 중심',
    'killer': '킬러 문항 포함',
    'writing': '서답형 조건 강화',
}
STRENGTH_LABELS = {
    'light': '핵심 구조는 유지하되 소재·표현·선택지를 부분 변경',
    'normal': '출제 의도와 평가 요소만 유지하고 발문·근거·선택지를 충분히 재작성',
    'strong': '원본의 평가 목표만 유지하고 문장·선택지·정답 근거를 새로 설계',
}
TARGET_LABELS = {
    'same': '원본과 동일한 문제 유형을 유지',
    'related': '같은 평가 영역 안에서 유사 유형으로 전환',
    'different': '원본과 다른 문제 유형으로 전환하되 같은 지문과 학습 목표를 사용',
}


def _twin_name(twin: dict) -> str:
    parts = [twin.get('school', ''), twin.get('grade', ''), twin.get('exam', '')]
    return ' '.join(p for p in parts if p) or '업로드 자료'


def _twin_dna_desc(twin: dict) -> str:
    return twin.get('dna') or twin.get('description') or '업로드 자료를 기준으로 출제 특성을 분석합니다.'


def _twin_type_labels(twin: dict):
    out = []
    for item in twin.get('types', []) or []:
        if isinstance(item, dict) and item.get('name'):
            label = item['name']
            if item.get('ratio'):
                label += f"({item['ratio']})"
            out.append(label)
        elif isinstance(item, str) and item:
            out.append(item)
    return out


def build_new_rules(twin: dict, ctx: dict) -> str:
    """원본 buildRules(ctx) 이식. 학교 DNA는 DB Digital Twin에서 읽는다."""
    name = _twin_name(twin)
    desc = _twin_dna_desc(twin)
    sel_labels = ctx.get('type_labels') or _twin_type_labels(twin)
    diff = DIFFICULTY_LABELS.get(ctx.get('difficulty', 'actual'), DIFFICULTY_LABELS['actual'])
    mc, sa = ctx.get('mc', 0), ctx.get('sa', 0)
    start, end = ctx.get('start', 1), ctx.get('end', mc + sa)
    offset = max(0, start - 1)
    planned = []
    if sel_labels:
        for z in range(mc + sa):
            planned.append(sel_labels[(offset + z) % len(sel_labels)])
    lines = [
        f"너는 {name} 영어 내신 출제자 디지털 트윈이다.",
        f"이번 묶음은 전체 시험 {start}번부터 {end}번까지이며 객관식 {mc}문항, 서답형 {sa}문항을 정확히 생성한다.",
        f"학교 DNA: {desc}",
        "선택 유형: " + (' / '.join(sel_labels) if sel_labels else '자료 기준 자유 구성'),
        "이번 묶음의 강제 유형 순서: " + (' → '.join(planned) if planned else '학교 DNA에 맞게 자유 구성') + ". 가능한 한 이 순서대로 서로 다른 형식의 문항을 만든다.",
        f"난이도: {diff}",
        "[중복 절대 금지] 앞서 생성된 문항과 stem, 정답 근거, 사용 문장, 선택지 구조, 서답형 조건 중 하나라도 실질적으로 같으면 새 소재와 새 유형으로 다시 설계한다. 같은 지문의 같은 문장으로 어법 문제·내용일치 문제·영작 문제를 반복하지 않는다.",
        f"앞서 생성된 문항 상세 요약: {ctx.get('previous') or '없음'}",
        "[번호 중복 금지] options 문자열 앞에 1., 1), ① 같은 선택지 번호를 절대 넣지 않는다. 화면에서 선택지 번호를 자동 부여한다. 질문 번호도 stem에 넣지 않는다.",
        "[단어·구 밑줄 번호형] 밑줄 친 부분의 번호를 고르는 어법·어휘 문항은 questionType을 반드시 grammar_underlined 또는 vocab_underlined로 한다. sourceText 한 개의 자연스러운 영어 지문 안에 ①[[u]]대상1[[/u]], ②[[u]]대상2[[/u]], ③[[u]]대상3[[/u]], ④[[u]]대상4[[/u]], ⑤[[u]]대상5[[/u]]를 직접 넣는다. 동그라미 번호는 반드시 밑줄 단어 또는 짧은 어구의 바로 왼쪽에 붙인다. 밑줄은 원칙적으로 1~5단어, 최대 6단어까지만 허용하며 절·문장 전체를 길게 밑줄 치지 않는다. options는 빈 배열 []로 출력한다. 아래에 단어를 다시 나열하는 선택지는 절대 만들지 않는다. answer는 틀린 밑줄 번호 배열이다.",
        "[밑줄 필수] stem에 밑줄 친 부분을 묻는다고 썼다면 sourceText에 반드시 ①~⑤와 [[u]]...[[/u]]가 함께 실제로 존재해야 한다.",
        "[내용 일치·불일치] 축약 설명이나 요약문을 sourceText로 쓰지 않는다. 사용자가 입력한 전체 영어 지문을 가능한 한 그대로 sourceText에 제공한다. 지문이 너무 길 때도 문제 해결에 필요한 모든 문단과 세부정보를 포함한 충분한 길이의 원문을 제시하고, 선택지 5개가 모두 그 지문만으로 판별되게 한다.",
        "[영영풀이 유형 고정] definition 유형은 stem을 반드시 \"다음 중 위 글의 네 단어의 영영풀이로 적절하지 않은 것을 고르시오.\"로 작성한다. sourceText는 빈 문자열로 출력한다. options는 \"word: English definition\" 형식의 5개 쌍이며 정확히 1개만 정의가 틀려야 한다. \"본문에서 찾아 쓰시오\" 또는 질문과 맞지 않는 단어 목록형 선택지는 금지한다.",
        "[의미 추론 밑줄] 특정 영어 단어·구·문장(예: Absolutely not)의 의미를 묻는 문항은 stem을 문제 번호 옆 상단에 배치하고, sourceText 안의 해당 표현을 반드시 [[u]]...[[/u]]로 밑줄 표시한다.",
        "[어구 배열 단서 금지] 주어진 어구 배열 문항에서는 sourceText에 완성 정답 문장이나 정답 순서를 그대로 알려 주는 문장을 절대 제시하지 않는다. 필요한 한국어 의미와 어구만 stem에 제시한다.",
        "[다의어 문맥 문제] 특정 표제어의 서로 다른 쓰임을 묻는 문항은 각 선택지 문장 속 해당 표제어와 굴절형에 반드시 [[u]]...[[/u]] 밑줄을 표시한다. 예: root, roots, rooted, rooting.",
        "[삽입·순서 수능형] 문장 삽입 또는 글의 순서 유형은 sourceText에 충분한 길이의 영어 지문을 반드시 제공한다. 삽입 문제는 발문과 삽입할 문장을 stem 맨 위에 함께 제시하고, 그 아래 sourceText 지문 문장 사이에 ① ② ③ ④ ⑤를 직접 표시한다. 순서 문제는 발문을 먼저 제시하고, 주어진 도입문 다음에 (A), (B), (C)를 각각 반드시 새 줄의 가장 왼쪽에서 시작하게 한다. 각 문단은 줄바꿈으로 분리하며 선택지는 실제 배열 조합으로 만든다.",
        "[객관식 공통] 모든 stem은 한국어 시험지 문체이고 영어 보기와 인용은 영어로 작성한다. 일반 객관식은 options 5개, 단어·구 밑줄 번호형만 options 빈 배열이다.",
        "[통제형 서답형] 자유 감상·자유 요약처럼 채점 기준이 흔들리는 문항은 금지한다. 정확한 단어/구 찾기, 빈칸 완성, 주어진 어구 배열, 오류 수정, 시작어·끝어 고정 문장전환, 필수어와 정확한 단어 수가 지정된 영작 중 하나로 만든다. 정답이 1개 또는 허용 가능한 소수 표현으로 확정되어야 한다.",
        "[서답형 필드] sourceText에는 풀이에 필요한 영어 문장·대화문·지문을 넣는다. answerFormat에는 답안 형태와 칸 수 또는 시작어를 명시한다. acceptedAnswers에는 정답으로 인정할 표현을 배열로 넣는다. gradingRule에는 대소문자·철자·문장부호·필수 문법 등 명확한 채점 기준을 쓴다.",
    ]
    if twin.get('rules'):
        lines.append("[DB 출제 규칙] 다음 학교별 출제 규칙을 반드시 반영한다: " + ' / '.join(twin['rules']))
    if twin.get('trapPatterns'):
        lines.append("[DB 함정패턴] 오답 선택지를 만들 때 다음 함정패턴을 활용한다: " + ' / '.join(twin['trapPatterns']))
    if twin.get('choiceStyle'):
        lines.append(f"[DB 선택지 스타일] {twin['choiceStyle']}")
    if twin.get('writingStyle'):
        lines.append(f"[DB 서술형 스타일] {twin['writingStyle']}")
    if twin.get('questionRatio'):
        ratio_text = ' / '.join(f"{k} {v}" for k, v in twin['questionRatio'].items())
        lines.append(f"[DB 유형 비율] {ratio_text}")
    lines.append("정답과 모범답안은 새 지문과 핵심 문법에 100% 근거해야 한다. 코드펜스 없이 순수 JSON만 출력한다.")
    return ' '.join(lines)


def build_transform_rules(twin: dict, cfg: dict) -> str:
    """원본 buildTransformRules(cfg) 이식. 학교 DNA는 DB Digital Twin에서 읽고,
    유형비율/출제규칙/난이도/함정패턴/선택지 스타일/서술형 스타일을 명시적으로 포함한다."""
    name = _twin_name(twin)
    desc = _twin_dna_desc(twin)
    strength = STRENGTH_LABELS.get(cfg.get('strength', 'normal'), STRENGTH_LABELS['normal'])
    target = TARGET_LABELS.get(cfg.get('target', 'same'), TARGET_LABELS['same'])
    count = cfg.get('count', 1)
    lines = [
        f"너는 {name} 영어 내신 출제자 디지털 트윈이자 문제 변형 전문가다.",
        f"원본 문제를 분석해 정확히 {count}개의 변형 문제를 만든다.",
        f"학교 출제 DNA: {desc}",
        f"변형 강도: {strength}.",
        f"{target}.",
        '선택지는 모두 새로 작성한다.' if cfg.get('options') else '필요한 범위에서 선택지를 수정한다.',
        '객관식은 원본과 다른 정답 번호가 되도록 선택지 순서를 설계한다.' if cfg.get('answerPos') else '정답 위치 변경은 필수가 아니다.',
        '원본보다 한 단계 어렵게 만든다.' if cfg.get('harder') else '난이도는 원본과 비슷하게 유지한다.',
        '학교 출제 DNA의 함정·발문·난이도를 적극 반영한다.' if cfg.get('schoolStyle') else '일반적인 중학교 내신 형식으로 작성한다.',
        "[복제 금지] 원본과 동일한 발문, 동일한 보기 문장, 동일한 정답 근거를 그대로 복사하지 않는다. 단순 단어 치환만 한 문제는 금지한다.",
        "[논리 검증] 지문만으로 정답이 하나 또는 명시된 복수 개로 확정되어야 한다. 오답도 문법·문맥상 왜 틀린지 명확해야 한다.",
        "[출력 형식] questions의 각 항목은 type을 mc 또는 sa로 지정한다. 객관식은 options 5개와 answer 배열을 제공한다. 서답형은 통제형으로 만들고 conditions, answerFormat, acceptedAnswers, sampleAnswer, gradingRule을 모두 작성한다.",
        "[밑줄 규칙—필수] 어법·어휘 밑줄형이면 sourceText 본문 안에 반드시 ①[[u]]대상1[[/u]] ②[[u]]대상2[[/u]] ③[[u]]대상3[[/u]] ④[[u]]대상4[[/u]] ⑤[[u]]대상5[[/u]]를 직접 넣는다. 번호는 밑줄 단어 바로 왼쪽에 붙이고 options는 반드시 빈 배열 []로 한다. 밑줄 없이 별도 선택지만 만드는 것은 금지한다.",
        "[빈칸 규칙—필수] 빈칸 유형이면 sourceText의 정답 어구가 있던 실제 위치를 반드시 __________ 로 바꾼다. 빈칸은 발문이나 선택지가 아니라 영어 지문 안에 존재해야 한다. options는 5개를 제공하고 지문 문맥만으로 정답이 하나로 확정되어야 한다.",
        "[수능형 규칙] 삽입 문제는 삽입 문장을 stem 상단에 제시하고 지문 속 위치에 ①~⑤를 표시한다. 순서 문제는 (A)(B)(C)를 각각 새 줄 가장 왼쪽에 둔다.",
    ]
    # 항목 8: DB에서 읽은 유형비율/출제규칙/난이도/함정패턴/선택지 스타일/서술형 스타일을 프롬프트에 반드시 포함
    if twin.get('difficulty'):
        lines.append(f"[DB 난이도 기준] {twin['difficulty']}")
    if twin.get('questionRatio'):
        ratio_text = ' / '.join(f"{k} {v}" for k, v in twin['questionRatio'].items())
        lines.append(f"[DB 유형 비율] {ratio_text}")
    if twin.get('rules'):
        lines.append("[DB 출제 규칙] " + ' / '.join(twin['rules']))
    if twin.get('trapPatterns'):
        lines.append("[DB 함정패턴] 오답을 설계할 때 다음 함정패턴을 활용한다: " + ' / '.join(twin['trapPatterns']))
    if twin.get('choiceStyle'):
        lines.append(f"[DB 선택지 스타일] {twin['choiceStyle']}")
    if twin.get('writingStyle'):
        lines.append(f"[DB 서술형 스타일] {twin['writingStyle']}")
    # 항목 9: 원하는 유형으로 변경 (사용자가 명시적으로 선택한 경우 target 설정을 덮어씀)
    if cfg.get('desiredTypeLabel'):
        lines.append(f"[유형 강제 지정] target 설정과 무관하게 반드시 다음 문제 유형으로 변환한다: {cfg['desiredTypeLabel']}. 이 유형에 맞는 questionType과 stem 문구를 사용한다.")
        if cfg.get('desiredTypePrompt'):
            lines.append(f"[유형 세부 지침] {cfg['desiredTypePrompt']}")
    elif cfg.get('recommended'):
        # 항목 10: 자동 추천된 유형
        lines.append(f"[자동 추천 유형] 사용자가 유형을 직접 지정하지 않아 학교 Digital Twin 기준으로 다음 유형을 추천 적용한다: {cfg['recommended']}.")
    lines.append("코드펜스 없이 순수 JSON만 출력한다.")
    return ' '.join(lines)


def schema_new(mc: int, sa: int) -> dict:
    return {
        "type": "object", "required": ["examTitle", "mcQuestions", "saQuestions"],
        "properties": {
            "examTitle": {"type": "string"},
            "mcQuestions": {"type": "array", "minItems": mc, "maxItems": mc, "items": {
                "type": "object", "required": ["questionType", "sourceText", "stem", "options", "answer", "multi", "score"],
                "properties": {
                    "questionType": {"type": "string"}, "sourceText": {"type": "string"}, "stem": {"type": "string"},
                    "options": {"type": "array", "minItems": 0, "maxItems": 5, "items": {"type": "string"}},
                    "answer": {"type": "array", "minItems": 1, "maxItems": 2, "items": {"type": "integer"}},
                    "multi": {"type": "boolean"}, "score": {"type": "integer"},
                },
            }},
            "saQuestions": {"type": "array", "minItems": sa, "maxItems": sa, "items": {
                "type": "object", "required": ["questionType", "sourceText", "stem", "conditions", "answerFormat", "acceptedAnswers", "sampleAnswer", "gradingRule", "score"],
                "properties": {
                    "questionType": {"type": "string"}, "sourceText": {"type": "string"}, "stem": {"type": "string"},
                    "conditions": {"type": "array", "items": {"type": "string"}}, "answerFormat": {"type": "string"},
                    "acceptedAnswers": {"type": "array", "items": {"type": "string"}}, "sampleAnswer": {"type": "string"},
                    "gradingRule": {"type": "string"}, "score": {"type": "integer"},
                },
            }},
        },
    }


def schema_transform(count: int) -> dict:
    return {
        "type": "object", "required": ["examTitle", "questions"],
        "properties": {
            "examTitle": {"type": "string"},
            "questions": {"type": "array", "minItems": count, "maxItems": count, "items": {
                "type": "object",
                "required": ["type", "questionType", "sourceText", "stem", "options", "answer", "multi", "score", "conditions", "answerFormat", "acceptedAnswers", "sampleAnswer", "gradingRule"],
                "properties": {
                    "type": {"type": "string", "enum": ["mc", "sa"]}, "questionType": {"type": "string"},
                    "sourceText": {"type": "string"}, "stem": {"type": "string"},
                    "options": {"type": "array", "items": {"type": "string"}},
                    "answer": {"type": "array", "items": {"type": "integer"}},
                    "multi": {"type": "boolean"}, "score": {"type": "integer"},
                    "conditions": {"type": "array", "items": {"type": "string"}}, "answerFormat": {"type": "string"},
                    "acceptedAnswers": {"type": "array", "items": {"type": "string"}}, "sampleAnswer": {"type": "string"},
                    "gradingRule": {"type": "string"},
                },
            }},
        },
    }


class GeminiCallError(Exception):
    def __init__(self, message, status=None, http_status=None):
        super().__init__(message)
        self.status = status
        self.http_status = http_status


def call_gemini_structured(key: str, rules: str, user_msg: str, schema: dict, temperature: float = 0.75) -> str:
    """원본 callGemini(key, rules, userMsg, sc) 이식: 모델 폴백 체인 + 429 재시도."""
    last_err = None
    for model in MODEL_CANDIDATES:
        for attempt in range(3):
            url = f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent'
            body = {
                'contents': [{'parts': [{'text': user_msg}]}],
                'systemInstruction': {'parts': [{'text': rules}]},
                'generationConfig': {'responseMimeType': 'application/json', 'responseSchema': schema, 'maxOutputTokens': 8192, 'temperature': temperature},
            }
            req = request.Request(url, data=json.dumps(body).encode('utf-8'), headers={'Content-Type': 'application/json', 'x-goog-api-key': key}, method='POST')
            try:
                with request.urlopen(req, timeout=60) as response:
                    data = json.loads(response.read().decode('utf-8'))
                http_status = response.status
            except error.HTTPError as exc:
                http_status = exc.code
                try:
                    data = json.loads(exc.read().decode('utf-8'))
                except Exception:
                    data = {'error': {'message': str(exc), 'status': 'UNKNOWN'}}
            except (error.URLError, TimeoutError) as exc:
                last_err = exc
                break
            if 'error' not in data:
                text = (((data.get('candidates') or [{}])[0]).get('content') or {}).get('parts', [{}])[0].get('text')
                if text:
                    return text
                last_err = GeminiCallError('응답 내용이 없습니다.')
                break
            err_info = data['error']
            status = err_info.get('status')
            last_err = GeminiCallError(err_info.get('message', 'API 오류'), status=status, http_status=http_status)
            if (http_status == 429 or status == 'RESOURCE_EXHAUSTED') and attempt < 2:
                time.sleep((attempt + 1) * 2)
                continue
            if status in ('NOT_FOUND', 'UNAVAILABLE') or http_status == 429:
                break
            raise last_err
    raise last_err or GeminiCallError('사용 가능한 모델이 없습니다.')


# ---------------- 문항 후처리 (원본 prepareQuestion / repairTransformQuestion 이식) ----------------

_CHOICE_PREFIX_RE = re.compile(r'^\s*(?:[①②③④⑤]|[1-5][.)]|\([1-5]\))\s*')
_MARKS = ['①', '②', '③', '④', '⑤']


def strip_choice_prefix(s: str) -> str:
    return _CHOICE_PREFIX_RE.sub('', s or '')


def normalize_underline_markup(s: str) -> str:
    x = str(s or '').replace('<u>', '[[u]]').replace('</u>', '[[/u]]').replace('<U>', '[[u]]').replace('</U>', '[[/u]]')
    return x


def add_inline_circle_marks(text: str) -> str:
    """[[u]]...[[/u]] 구간 앞에 ①~⑤ 순서대로 동그라미 번호를 붙인다.
    문제8(5차): 원본 지문에 남아 있던 (A)~(E) 같은 알파벳 위치 표식이 밑줄 바로 앞에 붙어 있으면
    새로 붙는 원문자와 중복되므로 함께 제거한다. 밑줄과 무관한, 지문의 다른 위치에 있는 (A)~(E)는
    건드리지 않는다(문제 풀이에 필요한 위치 표식일 수 있음)."""
    marks_iter = iter(_MARKS)
    def repl(m):
        try:
            mark = next(marks_iter)
        except StopIteration:
            mark = ''
        return mark + '[[u]]' + m.group(1) + '[[/u]]'
    pattern = re.compile(r'(?:\([A-Ea-e]\)\s*)?(?:[①②③④⑤]\s*)?\[\[u\]\]([\s\S]*?)\[\[/u\]\]')
    return pattern.sub(repl, normalize_underline_markup(text))


def format_sequence_source(text: str) -> str:
    def repl(m):
        return '\n(' + m.group(1).upper() + ') '
    return re.sub(r'\s*\(([ABCabc])\)\s*', repl, str(text or '')).strip()


def quoted_english_target(stem: str) -> str:
    m = re.search(r'["\'\u2018\u2019\u201c\u201d]([A-Za-z][A-Za-z\s\u2019-]{0,60})["\'\u2018\u2019\u201c\u201d]', str(stem or ''))
    return m.group(1).strip() if m else ''


def underline_target(text: str, target: str, all_forms: bool) -> str:
    if not target:
        return str(text or '')
    raw = str(text or '')
    if '[[u]]' in raw:
        return raw
    if all_forms and target.lower() == 'root':
        pattern = re.compile(r'\b(root(?:s|ed|ing)?)\b', re.IGNORECASE)
    else:
        pattern = re.compile(r'\b(' + re.escape(target) + r')\b', re.IGNORECASE)
    return pattern.sub(lambda m: '[[u]]' + m.group(1) + '[[/u]]', raw, count=1)


def underline_word_in_options(q: dict) -> dict:
    target = quoted_english_target(q.get('stem', ''))
    if not target:
        return q
    if re.search(r'서로 다른 문맥|쓰인 문장|쓰임', q.get('stem', '') or ''):
        q['options'] = [underline_target(o, target, True) for o in q.get('options') or []]
    elif q.get('sourceText'):
        q['sourceText'] = underline_target(q['sourceText'], target, False)
    return q


def is_inline_underline(q: dict) -> bool:
    qtype = q.get('questionType', '') or ''
    stem = q.get('stem', '') or ''
    source = q.get('sourceText', '') or ''
    if re.search(r'(?:grammar|vocab)_underlined', qtype, re.IGNORECASE):
        return True
    return bool(re.search(r'밑줄', stem) and len(re.findall(r'\[\[u\]\]', source)) >= 3)


def prepare_question(q: dict) -> dict:
    """원본 prepareQuestion(q) 이식."""
    q['sourceText'] = q.get('sourceText') or ''
    q['options'] = [strip_choice_prefix(o) for o in (q.get('options') or [])]
    if is_inline_underline(q):
        q['questionType'] = 'vocab_underlined' if re.search(r'vocab', q.get('questionType', '') or '', re.IGNORECASE) else 'grammar_underlined'
        q['sourceText'] = add_inline_circle_marks(q['sourceText'])
        q['options'] = []
    stem = q.get('stem', '') or ''
    if re.search(r'sequence', q.get('questionType', '') or '', re.IGNORECASE) or re.search(r'이어질 순서|올바른 순서', stem):
        q['sourceText'] = format_sequence_source(q['sourceText'])
    if re.search(r'배열', stem) or re.search(r'어구.*순서', stem):
        q['sourceText'] = ''
    q = underline_word_in_options(q)
    if re.search(r'영영풀이', stem) or re.search(r'definition', q.get('questionType', '') or '', re.IGNORECASE):
        q['questionType'] = 'definition_invalid'
        q['stem'] = '다음 중 위 글의 네 단어의 영영풀이로 적절하지 않은 것을 고르시오.'
        q['sourceText'] = ''
    return q


def repair_transform_question(q: dict) -> dict:
    """원본 repairTransformQuestion(q) 이식."""
    q = q or {}
    q['sourceText'] = str(q.get('sourceText') or '')
    q['options'] = [strip_choice_prefix(o) for o in (q.get('options') or [])]
    q['answer'] = q.get('answer') or [1]
    stem = str(q.get('stem') or '')
    asks_underline = bool(re.search(r'밑줄|어법상|문맥상 어색한 낱말', stem))
    if asks_underline and '[[u]]' not in q['sourceText'] and q['options']:
        src = q['sourceText']
        used = 0
        for i, opt in enumerate(q['options'][:5]):
            term = strip_choice_prefix(opt).strip()
            if not term:
                continue
            pos = src.lower().find(term.lower())
            if pos >= 0:
                src = src[:pos] + _MARKS[i] + '[[u]]' + src[pos:pos + len(term)] + '[[/u]]' + src[pos + len(term):]
                used += 1
        if used >= 2:
            q['sourceText'] = src
            q['options'] = []
            q['questionType'] = 'vocab_underlined' if re.search(r'어휘|낱말', stem) else 'grammar_underlined'
    asks_blank = bool(re.search(r'빈칸', stem)) or bool(re.search(r'blank', q.get('questionType', '') or '', re.IGNORECASE))
    if asks_blank and not re.search(r'_{5,}|\(\s*\)|\[\s*\]', q['sourceText']):
        try:
            ai = max(0, int((q.get('answer') or [1])[0]) - 1)
        except (TypeError, ValueError):
            ai = 0
        ans = strip_choice_prefix(q['options'][ai]).strip() if ai < len(q['options']) else ''
        if ans:
            pos2 = q['sourceText'].lower().find(ans.lower())
            if pos2 >= 0:
                q['sourceText'] = q['sourceText'][:pos2] + '__________' + q['sourceText'][pos2 + len(ans):]
    return prepare_question(q)


# ---------------- 학생 오답 관리 (항목3) ----------------
# OCR/AI가 채점된 학생 시험지 텍스트를 읽어 "틀린 문제만" 원본과 최대한 비슷한 형태로 추출하고,
# 정답은 "N번 정답" 형식의 간단한 목록으로 별도 정리한다.
# 오답 유형 분석·취약점 분석·학습 추천·통계 등은 절대 만들지 않는다.

def build_wrong_note_rules() -> str:
    return ' '.join([
        '너는 채점이 끝난 중학교 내신 영어 시험지에서 학생이 틀린 문제만 골라내는 보조교사이다.',
        '입력 텍스트는 문제 번호, 발문, 지문, 선택지와 함께 채점 표시(틀림, X, 빗금, 학생이 고른 오답 등)가 섞여 있을 수 있다.',
        '학생이 틀린 문제만 골라 원본 시험지와 최대한 동일한 순서와 형태(문제 번호, 발문, 지문, 선택지)로 정리해 wrongText에 담는다.',
        '맞은 문제는 절대 포함하지 않는다.',
        '정답은 틀린 문제 번호와 정답만 "N번 정답기호" 형식으로 한 줄씩 간단히 정리해 answerText에 담는다. 예: "1번 ③", "5번 ②".',
        '오답 유형 분류, 취약점 분석, 학습 추천, 통계, 해설 등은 절대 추가하지 않는다. 오직 틀린 문제 원문과 정답만 정리한다.',
        '틀린 문제를 입력에서 찾을 수 없으면 wrongText와 answerText를 빈 문자열로 둔다.',
        '코드펜스 없이 순수 JSON만 출력한다.',
    ])


def schema_wrong_note() -> dict:
    return {
        'type': 'object', 'required': ['wrongText', 'answerText'],
        'properties': {'wrongText': {'type': 'string'}, 'answerText': {'type': 'string'}},
    }


# ---------------- 오답 사진/PDF OCR 2차 정리 ----------------
# 「F2R PDF·사진 한글 텍스트 추출 편집기 v1」의 promptFor(literal/exam) 방식을 참고해,
# 원본 시험지 구조를 그대로 유지한 채 OCR 오류·줄바꿈만 교정하도록 한다 (요약/재작성 금지).

def build_ocr_clean_rules() -> str:
    return ' '.join([
        '너는 채점이 끝난 중학교 내신 영어 시험지의 OCR 추출 결과를 원본 형태로 정돈하는 편집기다.',
        '문제 번호, 영어 지문, 한글 발문, ①②③④⑤ 선택지, (A)(B)(C) 표시, 밑줄 대상, 보기, 정답 표시, 학생이 고른 답, 채점 흔적(틀림·X·빗금·O 등)을',
        '원문 그대로 유지하면서 줄바꿈과 띄어쓰기, 잘못 끊긴 줄만 읽기 좋게 정리하라.',
        '절대로 문제를 요약하거나 새로 만들거나 문장을 바꾸지 마라. 확실하지 않은 글자는 원문 추정 그대로 두고 임의로 지어내지 마라.',
        '설명, 안내 문구, 코드펜스 없이 정리된 시험지 본문 텍스트만 출력하라.',
    ])


def call_gemini_text(key: str, rules: str, user_msg: str, temperature: float = 0.2) -> str:
    """구조화 JSON 스키마 없이 순수 텍스트 응답이 필요한 호출(OCR 결과 정리 등)에 사용.
    call_gemini_structured와 동일한 모델 폴백/429 재시도 로직을 사용하되 responseSchema를 지정하지 않는다."""
    last_err = None
    for model in MODEL_CANDIDATES:
        for attempt in range(3):
            url = f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent'
            body = {
                'contents': [{'parts': [{'text': user_msg}]}],
                'systemInstruction': {'parts': [{'text': rules}]},
                'generationConfig': {'maxOutputTokens': 8192, 'temperature': temperature},
            }
            req = request.Request(url, data=json.dumps(body).encode('utf-8'), headers={'Content-Type': 'application/json', 'x-goog-api-key': key}, method='POST')
            try:
                with request.urlopen(req, timeout=60) as response:
                    data = json.loads(response.read().decode('utf-8'))
                http_status = response.status
            except error.HTTPError as exc:
                http_status = exc.code
                try:
                    data = json.loads(exc.read().decode('utf-8'))
                except Exception:
                    data = {'error': {'message': str(exc), 'status': 'UNKNOWN'}}
            except (error.URLError, TimeoutError) as exc:
                last_err = exc
                break
            if 'error' not in data:
                text = (((data.get('candidates') or [{}])[0]).get('content') or {}).get('parts', [{}])[0].get('text')
                if text:
                    return text
                last_err = GeminiCallError('응답 내용이 없습니다.')
                break
            err_info = data['error']
            status = err_info.get('status')
            last_err = GeminiCallError(err_info.get('message', 'API 오류'), status=status, http_status=http_status)
            if (http_status == 429 or status == 'RESOURCE_EXHAUSTED') and attempt < 2:
                time.sleep((attempt + 1) * 2)
                continue
            if status in ('NOT_FOUND', 'UNAVAILABLE') or http_status == 429:
                break
            raise last_err
    raise last_err or GeminiCallError('사용 가능한 모델이 없습니다.')
