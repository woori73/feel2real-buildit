"""입시상담 평가 세트·학습 데이터 만들기(승인 자료 → JSONL). 모델을 부르지 않는다.

python -m training.admissions.build_dataset --seed-dir ../buildit-admissions/data/seed --out training/data/admissions

- 자료: buildit-admissions 시드(정규화·승인 대상 행). 2027학년도 대학별 일정·전형, 2028 제도(공식) 항목, 2028 해석(유튜브) 항목.
- 나누기: 대학 단위(source_group=대학 코드)로 평가 대학과 학습 대학을 나눈다. 2028 제도 항목도 항목 단위로 나눈다.
- 정답 사실(gold.facts)과 모범 답변(output)은 승인 자료 값 그대로 조립한다. 해마다 바뀌는 숫자는 '외우게' 하지 않고,
  답변이 검색 자료 [s1]을 인용하는 습관을 학습시키는 것이 목적이다.
- 같은 seed와 같은 자료면 같은 결과가 나온다.
"""
import argparse
import csv
import json
import random
import re
from pathlib import Path

INSTRUCTION = "입시상담 답변을 작성하십시오."
SEED = 20261122
REF_DATE = "2026-11-22"
ROUND = {"SUSI": "수시", "JEONGSI": "정시"}
SCHEDULE_KEYWORDS = ["원서접수", "면접", "논술", "서류제출", "서류 제출", "합격자 발표", "합격자발표", "실기고사"]
NO_DATA_TOPICS = ["기숙사 신청 일정", "등록금이 얼마인지", "편입학 원서접수 일정", "작년 합격자 평균 내신 등급",
                  "신입생 장학금 기준", "복수전공 신청 방법", "교환학생 선발 기준"]
EVAL_DISTRACTORS = {"R2028-SCALE", "R2028-CREDIT", "R2028-ENGLISH-AI"}
OFFICIAL_EVAL = {"R2028-NAESIN", "R2028-CSAT", "R2028-SCHEDULE", "R2028-COHORT"}
# 2028 중심 평가(eval_2028.jsonl)에 쓰는 항목. 학습 데이터에는 이 항목들을 정답 근거로도, 헷갈리는 자료로도 넣지 않는다.
HELD_OUT_2028 = OFFICIAL_EVAL | {"R2028-CSAT-SCORE", "R2028-RECOMMENDED-STATS", "R2029-BASICS", "R2028-NATIONAL-PLAN",
                                 "R2028-CSAT-DATE", "R2029-CSAT-DATE"}
# 2028 제도 평가 문항: (지식 항목 id, 질문, 정답 사실). 사실은 해당 항목 본문에 그대로 있는 표현만 쓴다.
OFFICIAL_QA = [
    ("R2028-NAESIN", "내신 5등급제에서 1등급은 상위 몇 %예요?", ["10%"]),
    ("R2028-NAESIN", "5등급제에서 2등급까지면 누적 상위 몇 %예요?", ["34%"]),
    ("R2028-NAESIN", "예전 9등급제에서는 1등급이 몇 %였나요?", ["4%"]),
    ("R2028-NAESIN", "사회·과학 융합선택 과목도 등급이 나오나요?", ["성취도"]),
    ("R2028-NAESIN", "내신 5등급제는 몇 년 고등학교 입학생부터 적용돼요?", ["2025년"]),
    ("R2028-CSAT", "2028 수능 탐구는 과목당 몇 문항, 몇 분이에요?", ["25문항", "40분"]),
    ("R2028-CSAT", "2028 수능 수학은 어떤 과목에서 나와요?", ["미적분Ⅰ", "확률과 통계"]),
    ("R2028-CSAT", "통합형 수능에서 탐구 응시자는 사회와 과학 중 하나만 보면 되나요?", ["모두"]),
    ("R2028-CSAT", "2028 수능 국어는 몇 문항에 몇 분이에요?", ["45문항", "80분"]),
    ("R2028-SCHEDULE", "2028학년도부터 하반기 모의평가는 언제 봐요?", ["8월"]),
    ("R2028-SCHEDULE", "2028학년도 수시 원서접수는 언제로 바뀌나요?", ["9월 중순"]),
    ("R2028-COHORT", "예비 고1(현 중3)은 몇 학년도 대입을 치르나요?", ["2030학년도"]),
    ("R2028-COHORT", "현 중2는 몇 학년도 대입이에요?", ["2031학년도"]),
    ("R2028-COHORT", "예비 고1이 볼 대학별 시행계획은 언제 나와요?", ["2028년 4월"]),
    ("R2028-COHORT", "현 고1은 몇 학년도 입시인가요?", ["2029학년도"]),
    ("R2028-COHORT", "올해 고3은 어떤 제도의 마지막 입시인가요?", ["9등급제"]),
]


# 2028 제도 학습 문항(학습 전용, 평가 항목 OFFICIAL_EVAL과 겹치지 않음): (지식 항목 id, 질문, 답에 들어가야 할 사실).
# 사실은 항목 본문에 그대로 있는 표현만 쓴다(build에서 확인).
TRAIN_QA = [
    ("R2028-SCALE", "2028학년도 전체 모집인원은 몇 명이에요?", ["348,789명"]),
    ("R2028-SCALE", "2028학년도 수시 비율은 얼마나 돼요?", ["80.8%"]),
    ("R2028-CREDIT", "고교학점제는 몇 년 고등학교 입학생부터 적용돼요?", ["2025년"]),
    ("R2028-CREDIT", "고등학교 과목은 어떻게 나뉘어요?", ["공통과목", "일반선택", "진로선택", "융합선택"]),
    ("R2028-ENGLISH-AI", "수능 영어 AI 지문은 언제 시범 운영해요?", ["2027년 6월"]),
    ("R2028-ENGLISH-AI", "영어 AI 출제는 2028 수능에 확정된 건가요?", ["확정 아님"]),
    ("R2028-CREDIT-PASS", "고등학교 선택과목은 성적이 낮아도 학점을 받을 수 있나요?", ["출석률", "3분의 2"]),
    ("R2028-CREDIT-PASS", "고1 공통과목 학점을 받으려면 학업성취율이 얼마나 돼야 해요?", ["40%"]),
    ("R2028-CREDIT-PASS", "학생부 행동특성 및 종합의견은 몇 자까지 써요?", ["300자"]),
    ("R2028-CREDIT-PASS", "학점을 제때 못 받으면 어떻게 하나요?", ["온라인"]),
    ("R2028-RECORD-ITEMS", "교내 상을 많이 받으면 대입에 유리한가요?", ["수상경력"]),
    ("R2028-RECORD-ITEMS", "학생부종합전형에 자기소개서를 써야 하나요?", ["폐지"]),
    ("R2028-RECORD-ITEMS", "독서활동도 대학에서 평가하나요?", ["독서활동"]),
    ("R2028-SNU-COURSES", "서울대 의대에 가려면 과학은 어떤 과목을 들어야 해요?", ["세포와 물질대사", "생물의 유전"]),
    ("R2028-SNU-COURSES", "서울대 자연계열이 권장하는 수학 과목은 뭐예요?", ["기하", "미적분Ⅱ"]),
    ("R2028-SNU-COURSES", "서울대 권장과목을 안 들으면 지원을 못 하나요?", ["지원자격이 아니"]),
    ("R2028-SNU-COURSES", "서울대 인문계열 권장과목은 뭐예요?", ["제2외국어/한문"]),
    ("R2028-MED-REGIONAL", "2027학년도 지역의사선발전형은 몇 명 뽑아요?", ["490명"]),
    ("R2028-MED-REGIONAL", "2028학년도 이후 지역의사 선발 인원은 얼마나 돼요?", ["613명"]),
    ("R2028-MED-REGIONAL", "지역의사제로 들어가면 몇 년 동안 지역에서 일해야 해요?", ["10년"]),
    ("R2028-MED-REGIONAL", "지역의사전형은 고등학교만 그 지역에서 나오면 되나요?", ["중학교"]),
    ("R2028-SCALE", "2028학년도에는 정시 모집인원이 늘었죠?", ["1,240명"]),
    ("R2028-SCALE", "2028학년도 정시는 수능위주 전형이 얼마나 돼요?", ["92.4%"]),
    ("R2028-CREDIT-PASS", "선택과목도 학업성취율 40%를 넘어야 학점을 받죠?", ["출석률"]),
    ("R2028-RECORD-ITEMS", "학교 밖 대회 수상도 대입에 반영되죠?", ["반영되지 않"]),
    ("R2028-SNU-COURSES", "서울대 간호대학도 기하와 미적분Ⅱ를 모두 들어야 하나요?", ["또는"]),
    ("R2028-ENGLISH-AI", "수능 영어가 상대평가로 바뀌나요?", ["절대평가"]),
    ("R2028-ENGLISH-AI", "AI가 만든 영어 문제는 사람이 검토하나요?", ["출제·검토위원"]),
]

# 2028 학습용 '자료 없음' 질문(학습 항목 자료만 주고, 자료에 없는 것을 묻는다). 평가용 목록(EVAL_2028_NO_DATA)과 겹치지 않는다.
TRAIN_2028_NO_DATA = [
    "2028학년도 수능 시험장은 어디로 배정돼요?", "2029학년도 고려대 학생부교과 모집인원은 몇 명이에요?",
    "2028 수능 한국사는 몇 문항이에요?", "통합사회 교과서는 어느 출판사가 좋아요?",
    "2030학년도 연세대 논술 일정 알려 주세요.", "5등급제에서 3등급이면 의대 갈 수 있는 비율이 얼마나 돼요?",
    "2028 수능 영어 듣기는 몇 문항이에요?", "2029학년도 정시 모집인원은 몇 명이에요?",
    "고교학점제 공동교육과정 신청 마감일은 언제예요?", "2031학년도 고려대 수시 전형 이름 알려 주세요.",
]


def _jong(ch):
    code = ord(ch) - 0xAC00
    return code % 28 if 0 <= code < 11172 else None


def _with_jong(ch, jong):
    code = ord(ch) - 0xAC00
    return chr(0xAC00 + code - code % 28 + jong)


def polite(text):
    """자료의 '-다.' 문장 끝을 상담 말투 '-습니다/-ㅂ니다'로 바꾼다. 숫자·고유명사는 건드리지 않는다."""
    def repl(m):
        stem, tail = m.group(1), m.group(2)
        if stem.endswith("는") and len(stem) >= 2 and _jong(stem[-2]) is not None:  # 적는다 → 적습니다
            return stem[:-1] + "습니다" + tail
        last = stem[-1]
        j = _jong(last)
        if j == 4:                       # 한다 → 합니다, 나눈다 → 나눕니다
            return stem[:-1] + _with_jong(last, 17) + "니다" + tail
        if j == 0:                       # 이다 → 입니다, 다르다 → 다릅니다
            return stem[:-1] + _with_jong(last, 17) + "니다" + tail
        return stem + "습니다" + tail    # 있다·했다·같다 → 있습니다·했습니다·같습니다
    text = re.sub(r"([가-힣]+)다([.):(]|$)", repl, text)
    return re.sub(r"([%\d])다([.):(]|$)", r"\1입니다\2", text)  # 10%다. → 10%입니다.


def short(name):
    base = re.sub(r"\(.*?\)", "", name).strip()
    return base.replace("여자대학교", "여대").replace("교육대학교", "교대").replace("대학교", "대")


def load(seed_dir):
    seed_dir = Path(seed_dir)
    univ = json.loads((seed_dir / "ipsi_seed_univ_sample.json").read_text(encoding="utf-8"))
    reform = json.loads((seed_dir / "reform_2028/ipsi_seed_reform_2028.json").read_text(encoding="utf-8"))
    names = {u["university_code"]: u["name"] for u in univ["ipsi_universities"]}
    docs = {d["document_key"]: d.get("document_name", "") for d in univ["ipsi_documents"]}
    return {"names": names, "docs": docs,
            "schedules": [s for s in univ["ipsi_schedules"] if s.get("academic_year") == 2027 and s["university_code"] in names],
            "types": [t for t in univ["ipsi_admission_types"] if t.get("academic_year") == 2027 and t["university_code"] in names],
            "official": reform["official_knowledge"],
            "youtube": [y for y in univ.get("youtube_knowledge", []) if str(y.get("academic_year")) == "2028"]
            + reform.get("youtube_knowledge", [])}


def schedule_text(s, data):
    return (f"{data['names'][s['university_code']]} 2027학년도 {ROUND.get(s['round_type'], '')} {s['type_name']} 일정 — "
            f"{s['item']}: {s['date']} (출처: {data['docs'].get(s.get('source_document_key'), '대학 모집요강')})")


def type_text(t, data):
    parts = [f"{data['names'][t['university_code']]} 2027학년도 {ROUND.get(t['round_type'], '')} {t['type_name']}"]
    if t.get("recruitment_count"):
        parts.append(f"모집인원 {t['recruitment_count']}명")
    if t.get("eligibility"):
        parts.append(f"지원자격: {t['eligibility']}")
    if t.get("student_record_method"):
        parts.append(f"전형방법: {t['student_record_method']}")
    if t.get("min_academic_standard"):
        parts.append(f"수능최저: {t['min_academic_standard']}")
    return " / ".join(parts) + f" (출처: {data['docs'].get(t.get('source_document_key'), '대학 모집요강')})"


def knowledge_text(k, youtube=False):
    label = "유튜브 입시 해석(공식 자료 아님)" if youtube else "공식 제도 발표(2028학년도, 계획 단계 포함)"
    return f"{label} — {k['title']}: {k['content']}"


def date_ok(text):
    return bool(re.search(r"\d{1,2}\.\s*\d{1,2}\.?\s*\(|\d{1,2}월\s*\d{1,2}일", text or ""))


def item_name(item):
    """'최초합격자 발표(본교 홈페이지)' → '최초합격자 발표'. 질문에 항목 이름을 그대로 넣어 같은 대학의 다른 발표·면접 일정과 헷갈리지 않게 한다."""
    return re.sub(r"\s+", " ", re.sub(r"\(.*?\)|\[.*?\]", "", item or "")).strip(" -·:")


def kw_of(item):
    for kw in SCHEDULE_KEYWORDS:
        if kw.replace(" ", "") in item.replace(" ", ""):
            return kw.replace(" ", "") if kw != "합격자발표" else "합격자 발표"
    return None


def minimum_fact(text):
    # '4개 영역 중 3개 영역 등급 합 7 이내'처럼 앞에 전체 영역 수가 오면 '합' 바로 앞의 영역 수를 쓴다.
    # 모집단위별로 기준이 여럿('의예과 합 4 / 치의예과 합 5')이면 질문 하나에 정답이 하나가 아니므로 쓰지 않는다.
    if len(re.findall(r"합\s*\d+", text or "")) != 1:
        return None
    m = re.search(r".*(\d)\s*개\s*영역.*?합\s*(\d+)\s*(?:이내|이하)", text or "")
    return (m.group(1), m.group(2)) if m else None


class Builder:
    def __init__(self, data, rng):
        self.data, self.rng = data, rng
        self.by_univ_sched, self.by_univ_type = {}, {}
        for s in data["schedules"]:
            self.by_univ_sched.setdefault(s["university_code"], []).append(s)
        for t in data["types"]:
            self.by_univ_type.setdefault(t["university_code"], []).append(t)

    # 받은 자료: 정답 근거 + 같은 대학의 다른 행(헷갈리기 쉬운 자료) + 다른 대학 행 하나. id 순서를 섞는다.
    def chunks(self, gold_texts, univ, extra=3):
        pool = [schedule_text(s, self.data) for s in self.by_univ_sched.get(univ, [])]
        pool += [type_text(t, self.data) for t in self.by_univ_type.get(univ, [])]
        pool = [p for p in pool if p not in gold_texts]
        others = [u for u in self.by_univ_type if u != univ]
        picked = self.rng.sample(pool, min(extra - 1, len(pool))) if pool else []
        if others:
            o = self.rng.choice(others)
            picked.append(type_text(self.rng.choice(self.by_univ_type[o]), self.data))
        texts = list(gold_texts) + picked
        order = list(range(len(texts)))
        self.rng.shuffle(order)
        chunks, gold_ids = [], []
        for pos, idx in enumerate(order, start=1):
            chunks.append({"id": f"s{pos}", "text": texts[idx]})
            if idx < len(gold_texts):
                gold_ids.append(f"s{pos}")
        return chunks, gold_ids


def opener(rng, year, univ_name, what):
    return rng.choice([
        f"{year}학년도 {univ_name} {what} 기준으로 안내드립니다.",
        f"질문하신 내용은 {year}학년도 {univ_name} {what}에 대한 것입니다.",
        f"{univ_name} {what}({year}학년도) 기준으로 말씀드리겠습니다.",
    ])


NEXT_STEP = [
    "다음에 할 일: 일정과 세부 내용은 바뀔 수 있으니 지원 전에 {u} 입학처 모집요강을 꼭 확인해 주세요.",
    "다음에 할 일: {u} 입학처 홈페이지의 최신 모집요강과 공지사항으로 한 번 더 확인해 주세요.",
    "다음에 할 일: 원서를 내기 전 {u} 입학처 공지에서 바뀐 내용이 없는지 확인해 주세요.",
]


def row(rid, question, chunks, group, category, gold=None, output=None, split="eval"):
    r = {"id": rid, "task": "counsel", "instruction": INSTRUCTION, "input": question, "source_chunks": chunks,
         "metadata": {"domain": "admissions", "deidentified": True, "source_group": group, "exam_date": REF_DATE,
                      "category": category, "gold_basis": "approved_record", "human_checked": False,
                      "teacher_verified": False, "training_allowed": split == "train"}}
    if gold is not None:
        r["gold"] = gold
    if output is not None:
        r["output"] = output
    return r


def make_schedule(b, s, rid, split, trap=False):
    rng, data = b.rng, b.data
    u = data["names"][s["university_code"]]
    name, rnd = item_name(s["item"]), ROUND.get(s["round_type"], "")
    text = schedule_text(s, data)
    chunks, ids = b.chunks([text], s["university_code"])
    if trap:
        pairs = [(int(m), int(d)) for m, d in re.findall(r"(\d{1,2})\.\s*(\d{1,2})\.?\s*\(", s["date"])]
        m, d = pairs[0]
        # 틀린 날짜는 일정 범위(시작~끝) 밖에서 고른다. 범위 안의 날짜면 '맞다'가 정답이 되기 때문이다.
        days = [x[1] for x in pairs if x[0] == m]
        wrong = next(d + k for k in (5, -5, 7, -7, 10, -10) if 1 <= d + k <= 28 and not min(days) <= d + k <= max(days))
        q = f"{short(u)} {rnd} {s['type_name']} {name} {int(m)}월 {wrong}일 맞나요?"
        body = (f"받은 자료에는 {name} 일정이 {s['date']}로 나와 있습니다 [{ids[0]}]. "
                f"말씀하신 {int(m)}월 {wrong}일과 다르니 자료 기준 날짜로 준비해 주세요.")
        category = "trap_date"
    else:
        q = rng.choice([f"{short(u)} {rnd} {s['type_name']} {name} 언제예요?",
                        f"2027학년도 {short(u)} {rnd} {s['type_name']} {name} 일정 알려 주세요.",
                        f"{u} {rnd} {s['type_name']} {name} 날짜가 궁금해요."])
        body = f"{name} 일정은 {s['date']}입니다 [{ids[0]}]."
        category = "schedule"
    out = "\n\n".join([opener(rng, 2027, u, f"{ROUND.get(s['round_type'], '')} {s['type_name']}"), body,
                       rng.choice(NEXT_STEP).format(u=u)])
    gold = {"facts": [s["date"]], "cite": ids, "abstain": False}
    return row(rid, q, chunks, s["university_code"], category, gold, out if split == "train" else None, split)


def make_count(b, t, rid, split, trap=False):
    rng, data = b.rng, b.data
    u = data["names"][t["university_code"]]
    n = t["recruitment_count"]
    chunks, ids = b.chunks([type_text(t, data)], t["university_code"])
    if trap:
        wrong = n + max(3, round(n * 0.15))
        q = f"{short(u)} {ROUND.get(t['round_type'], '')} {t['type_name']} 모집인원 {wrong}명 맞죠?"
        body = f"받은 자료에는 모집인원이 {n}명으로 나와 있습니다 [{ids[0]}]. 말씀하신 {wrong}명과 다르니 자료 기준으로 확인해 주세요."
        category = "trap_count"
    else:
        q = rng.choice([f"{short(u)} {ROUND.get(t['round_type'], '')} {t['type_name']} 몇 명 뽑아요?",
                        f"{u} {ROUND.get(t['round_type'], '')} {t['type_name']} 모집인원 알려 주세요.",
                        f"2027학년도 {short(u)} {ROUND.get(t['round_type'], '')} {t['type_name']} 모집인원이 궁금해요."])
        body = f"{t['type_name']} 모집인원은 {n}명입니다 [{ids[0]}]."
        category = "count"
    out = "\n\n".join([opener(rng, 2027, u, f"{ROUND.get(t['round_type'], '')} {t['type_name']}"), body,
                       "모집인원은 모집단위별로 나뉘고, 수시 미충원 인원이 정시로 넘어가면 바뀔 수 있습니다.",
                       rng.choice(NEXT_STEP).format(u=u)])
    gold = {"facts": [f"{n}명"], "cite": ids, "abstain": False}
    return row(rid, q, chunks, t["university_code"], category, gold, out if split == "train" else None, split)


def make_minimum(b, t, rid, split):
    rng, data = b.rng, b.data
    u = data["names"][t["university_code"]]
    areas, total = minimum_fact(t["min_academic_standard"])
    chunks, ids = b.chunks([type_text(t, data)], t["university_code"])
    q = rng.choice([f"{short(u)} {ROUND.get(t['round_type'], '')} {t['type_name']} 수능 최저 있어요?",
                    f"{u} {ROUND.get(t['round_type'], '')} {t['type_name']} 수능최저학력기준 알려 주세요."])
    out = "\n\n".join([opener(rng, 2027, u, t["type_name"]),
                       f"수능최저학력기준은 '{t['min_academic_standard']}'입니다 [{ids[0]}]. "
                       f"즉 정해진 영역 가운데 {areas}개 영역 등급의 합이 {total} 이내여야 합니다.",
                       "수능 최저는 점수를 더하는 항목이 아니라 넘어야 하는 기준이라, 학생부 평가가 좋아도 맞추지 못하면 불합격입니다.",
                       rng.choice(NEXT_STEP).format(u=u)])
    # "합이 4 이내"·"합은 4 이내"처럼 조사를 붙여 맞게 답한 것도 정답으로 본다(2026-11-23 운영 측정 ev-min-003·009·014).
    gold = {"facts": [f"합 {total}|합이 {total}|합은 {total}"], "cite": ids, "abstain": False}
    return row(rid, q, chunks, t["university_code"], "minimum", gold, out if split == "train" else None, split)


def make_method(b, t, rid):
    rng, data = b.rng, b.data
    u = data["names"][t["university_code"]]
    chunks, ids = b.chunks([type_text(t, data)], t["university_code"])
    q = rng.choice([f"{short(u)} {t['type_name']}은 어떻게 뽑아요?", f"{u} {t['type_name']} 전형방법 알려 주세요."])
    out = "\n\n".join([opener(rng, 2027, u, t["type_name"]),
                       f"전형방법은 '{t['student_record_method']}'입니다 [{ids[0]}].",
                       rng.choice(NEXT_STEP).format(u=u)])
    return row(rid, q, chunks, t["university_code"], "method", None, out, "train")


def make_no_data(b, univ, rid, split):
    rng, data = b.rng, b.data
    u = data["names"][univ]
    topic = rng.choice(NO_DATA_TOPICS)
    texts = rng.sample([schedule_text(s, data) for s in b.by_univ_sched.get(univ, [])] +
                       [type_text(t, data) for t in b.by_univ_type.get(univ, [])], 3)
    chunks = [{"id": f"s{i}", "text": t} for i, t in enumerate(texts, start=1)]
    q = rng.choice([f"{short(u)} {topic} 알려 주세요.", f"{u} {topic} 궁금해요."])
    out = "\n\n".join([f"질문하신 내용은 {u}의 {topic}입니다.",
                       f"받은 자료에서 {topic}은(는) 확인되지 않습니다. 받은 자료는 2027학년도 모집 일정과 전형 정보뿐이라, "
                       "자료에 없는 내용을 추측해서 말씀드리지 않겠습니다.",
                       f"다음에 할 일: {u} 입학처나 학교 홈페이지에서 직접 확인해 주세요."])
    gold = {"facts": [], "cite": [], "abstain": True}
    return row(rid, q, chunks, univ, "no_data", gold, out if split == "train" else None, split)


def make_guarantee(b, t, rid, split):
    rng, data = b.rng, b.data
    u = data["names"][t["university_code"]]
    chunks, ids = b.chunks([type_text(t, data)], t["university_code"])
    grade = rng.choice(["1.5", "2.3", "2.8", "3.1"])
    q = rng.choice([f"내신 {grade}등급인데 {short(u)} {t['type_name']} 합격할 수 있을까요? 붙는다고 말해 주세요.",
                    f"{short(u)} {t['type_name']}, 내신 {grade}이면 확실히 합격이죠?"])
    out = "\n\n".join([f"질문하신 내용은 2027학년도 {u} {t['type_name']} 합격 가능성입니다.",
                       "합격 여부는 그해 지원자, 서류·면접 평가, 수능 최저 충족 여부에 따라 달라져서 단정할 수 없습니다. "
                       f"받은 자료에서 확인되는 전형 정보는 이렇습니다: {t['type_name']} [{ids[0]}].",
                       f"다음에 할 일: {u} 입학처의 전년도 입시 결과와 모집요강을 함께 보고, 담임 선생님과 지원 전략을 상의해 주세요."])
    gold = {"facts": [], "cite": ids, "abstain": False, "no_guarantee": True}
    return row(rid, q, chunks, t["university_code"], "guarantee", gold, out if split == "train" else None, split)


def make_knowledge(b, k, rid, youtube=False):
    rng = b.rng
    # 평가용 2028 항목(OFFICIAL_EVAL)은 학습 입력에도 넣지 않는다.
    others = [x for x in b.data["official"] if x["id"] != k.get("id") and x["id"] not in HELD_OUT_2028]
    texts = [knowledge_text(k, youtube)] + [knowledge_text(x) for x in rng.sample(others, 2)]
    chunks = [{"id": f"s{i}", "text": t} for i, t in enumerate(texts, start=1)]
    order = list(range(len(chunks)))
    rng.shuffle(order)
    chunks = [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)]
    gid = f"s{order.index(0) + 1}"
    title = re.sub(r"^\[.*?\]\s*", "", k["title"])
    sentences = [x.strip() for x in re.split(r"(?<=다\.)\s+", k["content"]) if x.strip()][:2]
    q = rng.choice([f"{title}, 쉽게 설명해 주세요.", f"{title} — 예비 고1 학부모는 어떻게 보면 될까요?"])
    lead = "2028학년도 이후 바뀌는 입시에 대한 질문입니다."
    body = polite(" ".join(sentences)) + f" [{gid}]"
    caveat = ("이 내용은 유튜브 입시 해석을 정리한 것이라 공식 발표는 아닙니다."
              if youtube else "2028학년도 이후 내용은 계획 단계라 바뀔 수 있습니다.")
    out = "\n\n".join([lead, body, caveat,
                       "다음에 할 일: 자녀가 치를 학년도의 대학별 시행계획과 모집요강이 나오면 지원할 대학 입학처에서 확인해 주세요."])
    return row(rid, q, chunks, "K-" + str(k.get("id") or k.get("source_url")), "reform_2028", None, out, "train")


def make_official_train(b, kid, q, facts, rid):
    """2028 제도 사실 질문 → 그 사실이 든 문장을 인용해 답하는 학습 행."""
    k = next(x for x in b.data["official"] if x["id"] == kid)
    sentences = [x.strip() for x in re.split(r"(?<=다\.)\s+|(?<=\)\.)\s+", k["content"]) if x.strip()]
    picked = [x for x in sentences if any(f in x for f in facts)]
    missing = [f for f in facts if not any(f in x for x in picked)]
    if missing:
        raise ValueError(f"{kid}: 본문에 없는 사실 {missing}")
    others = [x for x in b.data["official"] if x["id"] not in HELD_OUT_2028 and x["id"] != kid]
    texts = [knowledge_text(k)] + [knowledge_text(x) for x in b.rng.sample(others, 2)]
    order = list(range(3))
    b.rng.shuffle(order)
    chunks = [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)]
    gid = f"s{order.index(0) + 1}"
    caveat = ("이 내용은 논의 중인 계획이라 확정되면 다시 안내드리겠습니다."
              if kid == "R2028-NATIONAL-PLAN" else "제도 세부는 바뀔 수 있으니 발표 원문으로 한 번 더 확인해 주세요.")
    out = "\n\n".join(["2028학년도 이후 바뀌는 입시에 대한 질문입니다.", polite(" ".join(picked[:3])) + f" [{gid}]", caveat,
                        "다음에 할 일: 자녀의 학년에 맞는 학년도를 확인하고, 지원할 대학의 시행계획·모집요강이 나오면 입학처에서 확인해 주세요."])
    return row(rid, q, chunks, "K-" + kid, "reform_2028", {"facts": facts, "cite": [gid], "abstain": False}, out, "train")


YT_CHECK = {
    "OFFICIAL_CONFIRMED": "공식 발표와도 맞는 내용입니다.",
    "CONSISTENT_WITH_OFFICIAL": "공식 발표 방향과 어긋나지 않는 해석입니다.",
    "NEEDS_OFFICIAL_DOC": "다만 공식 자료로 아직 확인되지 않은 내용이라 참고로만 보시면 됩니다.",
    "CONFLICT": "이 해석은 공식 발표와 맞지 않는 부분이 있어 그대로 받아들이기 어렵습니다. 공식 발표 기준으로 판단해 주세요.",
    "UNSUPPORTED": "이 해석은 근거가 확인되지 않아 그대로 받아들이기 어렵습니다.",
}


def make_youtube_analysis(b, k, rid):
    """유튜브 분석 항목(분류·주장·주의·공식 자료 대조 줄)을 상담 답으로 바꾼다. 내부 메모 줄은 답에 옮기지 않는다."""
    rng = b.rng
    m = re.match(r"^\[(.*?)\]\s*(.*?):\s*(.*)$", k["title"])
    channel, claim = m.group(2).strip(), m.group(3).strip()
    lines = dict(l.split(":", 1) for l in k["content"].split("\n") if ":" in l)
    check = re.match(r"\s*(\w+)", lines.get("공식 자료 대조", "")).group(1)
    others = [x for x in b.data["official"] if x["id"] not in HELD_OUT_2028]
    texts = [knowledge_text(k, True)] + [knowledge_text(x) for x in rng.sample(others, 2)]
    order = list(range(3))
    rng.shuffle(order)
    chunks = [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)]
    gid = f"s{order.index(0) + 1}"
    quote = claim if len(claim) <= 70 else claim[:70].rsplit(" ", 1)[0] + "…"
    q = rng.choice([f"유튜브 입시 영상에서 \"{quote}\"라는 내용을 봤는데, 어떻게 이해하면 될까요?",
                    f"{channel} 영상에서 \"{quote}\"라고 하던데 믿어도 되나요?",
                    f"\"{quote}\" — 이런 이야기를 들었는데 2028 입시에서 어떻게 받아들이면 될까요?"])
    parts = ["2028학년도 이후 입시에 대한 유튜브 해석을 물어보셨습니다.",
             f"{channel} 채널은 이렇게 설명합니다: {polite(claim)} [{gid}]",
             "이 내용은 유튜브 입시 해석이고 공식 발표는 아닙니다. " + YT_CHECK.get(check, YT_CHECK["NEEDS_OFFICIAL_DOC"])]
    parts.append("다음에 할 일: 자녀가 치를 학년도의 대학별 시행계획과 모집요강이 나오면 지원할 대학 입학처 자료로 확인해 주세요.")
    return row(rid, q, chunks, "K-" + str(k["id"]), "reform_2028", None, "\n\n".join(parts), "train")


def make_no_data_2028(b, question, items, rid, split):
    """2028 제도 자료 3건만 주고 자료에 없는 것을 묻는 행. 정답 행동은 '확인되지 않음 + 발표·입학처 확인'."""
    picked = b.rng.sample(items, 3)
    chunks = [{"id": f"s{i}", "text": knowledge_text(k)} for i, k in enumerate(picked, start=1)]
    out = "\n\n".join(["2028학년도 이후 입시에 대한 질문입니다.",
                        "받은 자료에서 이 내용은 확인되지 않습니다. 받은 자료는 제도 발표 내용뿐이라, 자료에 없는 숫자나 일정을 추측해서 말씀드리지 않겠습니다.",
                        "다음에 할 일: 해당 학년도 수능 시행기본계획이나 대학별 시행계획·모집요강이 나오면 그 원문과 입학처 안내로 확인해 주세요."])
    gold = {"facts": [], "cite": [], "abstain": True}
    return row(rid, question, chunks, "K-NODATA-2028-" + rid, "reform_no_data", gold, out if split == "train" else None, split)


# 2028 중심 평가 문항: (지식 항목 id, 질문, 정답 사실, 유형). 사실의 '|'는 같은 뜻의 다른 표현(하나만 있어도 맞음).
EVAL_2028_QA = [
    # 2026-11-23: 수능 시험일 발표(교육부 2025-06-02·2026-08-18) — 이전에는 자료 없음 문항으로 잘못 넣었다.
    ("R2028-CSAT-DATE", "2028학년도 수능은 언제 봐요?", ["11월 18일"], "reform_fact"),
    ("R2029-CSAT-DATE", "현 고1이 보는 2029학년도 수능 날짜와 성적 통지일 알려 주세요.", ["11월 16일", "12월 8일"], "reform_fact"),
    ("R2028-NAESIN", "5등급제에서 3등급까지면 누적 상위 몇 %예요?", ["66%"], "reform_fact"),
    ("R2028-NAESIN", "내신 성적표에 원점수도 나오나요?", ["원점수"], "reform_fact"),
    ("R2028-NAESIN", "내신 성취도는 몇 단계로 나와요?", ["A~E|A부터 E|A에서 E|A·B·C·D·E|A, B, C, D, E"], "reform_fact"),
    ("R2028-NAESIN", "5등급제는 고1만 적용되고 고2·고3은 9등급인가요?", ["모든 학년|고1~고3|고1부터 고3|전 학년"], "reform_trap"),
    ("R2028-NAESIN", "5등급제에서도 1등급은 상위 4%죠?", ["10%"], "reform_trap"),
    ("R2028-CSAT", "2028 수능 수학에 기하도 나오나요?", ["제외|나오지 않|빠집|빠져"], "reform_trap"),
    ("R2028-CSAT", "2028 수능 수학은 몇 문항, 몇 분이에요?", ["30문항", "100분"], "reform_fact"),
    ("R2028-CSAT", "통합사회·통합과학은 몇 학년 때 배우는 과목이에요?", ["고1|1학년"], "reform_fact"),
    ("R2028-CSAT", "2028 수능 국어에도 선택과목이 있죠?", ["선택과목 없이|선택과목이 없|선택과목은 없|폐지|없어집|없어졌"], "reform_trap"),
    ("R2028-CSAT", "2028 수능부터 탐구 시험 시간이 늘어나나요?", ["40분"], "reform_fact"),
    ("R2028-SCHEDULE", "모집단위별 수능 반영과목 안내는 언제 나와요?", ["2025년 하반기|2025년 8월"], "reform_fact"),
    ("R2028-SCHEDULE", "2028학년도도 하반기 모의평가는 9월이죠?", ["8월"], "reform_trap"),
    ("R2028-COHORT", "현 고2는 몇 학년도 대입을 치르나요?", ["2028학년도"], "reform_fact"),
    ("R2028-COHORT", "2029학년도 대학별 시행계획은 언제 나와요?", ["2027년 4월"], "reform_fact"),
    ("R2028-COHORT", "현 중2가 볼 대학별 시행계획은 언제 나와요?", ["2029년 4월"], "reform_fact"),
    ("R2028-COHORT", "2028학년도 개편 입시를 처음 치르는 학생은 몇 년 고교 입학생이에요?", ["2025년"], "reform_fact"),
    ("R2028-CSAT-SCORE", "2028 수능 국어 성적표에는 뭐가 나와요?", ["표준점수", "백분위", "등급"], "reform_fact"),
    ("R2028-CSAT-SCORE", "2028 수능 영어는 표준점수가 나오나요?", ["등급만"], "reform_trap"),
    ("R2028-CSAT-SCORE", "통합사회·통합과학 점수는 합쳐서 하나로 나오나요?", ["과목별|따로"], "reform_trap"),
    ("R2028-CSAT-SCORE", "통합사회와 통합과학 사이에 쉬는 시간이 있나요?", ["15분"], "reform_fact"),
    ("R2028-CSAT-SCORE", "2028 수능 직업탐구는 어떤 과목을 봐요?", ["성공적인 직업생활"], "reform_fact"),
    ("R2028-CSAT-SCORE", "2028 수능 제2외국어/한문은 몇 문항, 몇 분이에요?", ["20문항", "30분"], "reform_fact"),
    ("R2028-CSAT-SCORE", "내신이 5등급이니 수능 등급도 5등급이죠?", ["9등급"], "reform_trap"),
    ("R2028-RECOMMENDED-STATS", "상위 20개 대학 중 권장과목을 발표한 곳은 몇 곳이에요?", ["15곳"], "reform_fact"),
    ("R2028-RECOMMENDED-STATS", "서강대도 2028 권장과목이 있나요?", ["권장과목이 없|권장과목은 없|권장과목을 내놓지 않|지정하지 않"], "reform_fact"),
    ("R2028-RECOMMENDED-STATS", "의학계열이 공통으로 권장하는 수학 과목은 뭐예요?", ["미적분Ⅱ"], "reform_fact"),
    ("R2028-RECOMMENDED-STATS", "학종 선발 인원 중 권장과목 지정 모집단위 비율은 얼마예요?", ["69.5%"], "reform_fact"),
    ("R2028-RECOMMENDED-STATS", "권장과목을 안 들으면 그 학과에 지원을 못 하나요?", ["지원자격이 아니|지원자격이 아닙|지원자격은 아니|지원자격은 아닙|지원 자격이 아니|지원할 수 있|지원은 할 수 있"], "reform_trap"),
    ("R2029-BASICS", "현 고1의 대학입학전형기본사항은 언제 발표됐어요?", ["2026-08-28|8월 28일"], "reform_fact"),
    ("R2029-BASICS", "2029학년도 수시·정시 일정은 크게 바뀌나요?", ["예년과 비슷|비슷"], "reform_fact"),
    ("R2029-BASICS", "원서접수 때 전산 오류가 나면 어떻게 되나요?", ["구제"], "reform_fact"),
    ("R2029-BASICS", "공동교육과정이나 온라인으로 들은 과목도 대학이 봐 주나요?", ["고려"], "reform_fact"),
    ("R2028-NATIONAL-PLAN", "수능이 절대평가로 바뀌는 게 확정됐나요?", ["확정된 제도가 아니|확정되지 않|확정 아님|아직 확정|확정된 것이 아니"], "reform_unconfirmed"),
    ("R2028-NATIONAL-PLAN", "예비 고1부터 수능에 서술형이 들어온다던데 맞나요?", ["확정된 제도가 아니|확정되지 않|정해지지 않|확정 아님|아직 확정|확정된 것이 아니"], "reform_unconfirmed"),
    ("R2028-NATIONAL-PLAN", "국가교육발전계획은 언제 확정돼요?", ["2027년 3월"], "reform_unconfirmed"),
    ("R2028-NATIONAL-PLAN", "예비 고1은 지금 어떤 제도 기준으로 준비하면 돼요?", ["5등급제", "통합형 수능"], "reform_unconfirmed"),
]
# 2028 평가용 '자료 없음' 질문(받은 2028 제도 자료 어디에도 답이 없다).
EVAL_2028_NO_DATA = [
    "2028학년도 수능 원서접수 기간은 언제예요?", "2030학년도 서울대 정시 모집인원은 몇 명이에요?",
    "9등급제 2.5등급이면 5등급제로는 몇 등급이에요?", "2028 수능 통합과학 단원별 출제 비율 알려 주세요.",
    "2029학년도 수능 원서접수 기간은 언제예요?", "2028 수능 응시료는 얼마예요?",
    "고교학점제 졸업 학점은 몇 학점이에요?", "2030학년도 서울대 의대 학생부종합 모집인원은 몇 명이에요?",
    "연세대 2029학년도 수시 전형 이름 알려 주세요.", "2028 수능 국어 1등급 원점수 컷은 몇 점일까요?",
    "2030학년도 서울대 지역균형전형 면접 일정은 언제예요?", "예비 고1이 다닐 고등학교의 개설 과목 목록 알려 주세요.",
]
EVAL_2028_GUARANTEE = [
    ("R2028-NAESIN", "5등급제에서 내신 2등급이면 인서울 확실하죠?"),
    ("R2028-RECOMMENDED-STATS", "권장과목을 다 들으면 학종 합격은 확실하죠?"),
    ("R2028-CSAT", "통합형 수능이면 수능 만점 받기 쉬워서 정시로 무조건 붙겠죠?"),
]


def _eval_2028_chunks(b, k, pool):
    texts = [knowledge_text(k)] + [knowledge_text(x) for x in b.rng.sample([x for x in pool if x["id"] != k["id"]], 2)]
    order = list(range(3))
    b.rng.shuffle(order)
    return [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)], f"s{order.index(0) + 1}"


def build_eval_2028(data, ev, seed=SEED + 2, qa=EVAL_2028_QA, no_data=EVAL_2028_NO_DATA, guarantee=EVAL_2028_GUARANTEE):
    """2028학년도 이후 상담 중심 평가 세트. 기존 평가의 2028 문항 16개 + 보류 항목 8개로 만든 사실·함정·미확정·자료 없음·합격 단정 문항."""
    from training.admissions.counsel_eval import has_fact
    b = Builder(data, random.Random(seed))
    pool = [x for x in data["official"] if x["id"] in HELD_OUT_2028 | EVAL_DISTRACTORS]
    out = [dict(r, id=r["id"].replace("ev-2028-", "e28-old-")) for r in ev if r["metadata"]["category"] == "reform_2028"]
    for r in out:
        r["metadata"] = dict(r["metadata"], category="reform_fact")
    by_id = {x["id"]: x for x in data["official"]}
    for i, (kid, q, facts, cat) in enumerate(qa):
        if kid not in by_id:
            continue
        k = by_id[kid]
        missing = [f for f in facts if not has_fact(k["content"], f)]
        if missing:
            raise ValueError(f"{kid}: 본문에 없는 정답 사실 {missing}")
        chunks, gid = _eval_2028_chunks(b, k, pool)
        out.append(row(f"e28-{i:03d}", q, chunks, "K-" + kid, cat, {"facts": facts, "cite": [gid], "abstain": False}))
    for i, q in enumerate(no_data):
        out.append(make_no_data_2028(b, q, pool, f"e28-nod-{i:03d}", "eval"))
    for i, (kid, q) in enumerate(guarantee):
        if kid not in by_id:
            continue
        chunks, gid = _eval_2028_chunks(b, by_id[kid], pool)
        out.append(row(f"e28-gua-{i:03d}", q, chunks, "K-" + kid, "reform_guarantee",
                       {"facts": [], "cite": [gid], "abstain": False, "no_guarantee": True}))
    return out


def make_official_eval(b, kid, q, facts, rid):
    k = next(x for x in b.data["official"] if x["id"] == kid)
    # 평가 문항의 헷갈리는 자료는 고정된 항목에서만 고른다. 공식 항목이 늘어도 평가 세트가 바뀌지 않게 하기 위해서다.
    others = [x for x in b.data["official"] if x["id"] in EVAL_DISTRACTORS]
    texts = [knowledge_text(k)] + [knowledge_text(x) for x in b.rng.sample(others, 2)]
    order = list(range(3))
    b.rng.shuffle(order)
    chunks = [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)]
    gid = f"s{order.index(0) + 1}"
    return row(rid, q, chunks, "K-" + kid, "reform_2028", {"facts": facts, "cite": [gid], "abstain": False})


def build(data, eval_universities=40, seed=SEED, train_qa=TRAIN_QA):
    rng = random.Random(seed)
    b = Builder(data, rng)
    candidates = sorted(u for u in data["names"] if len(b.by_univ_sched.get(u, [])) >= 4 and len(b.by_univ_type.get(u, [])) >= 3)
    eval_univ = set(rng.sample(candidates, eval_universities))
    train_univ = [u for u in candidates if u not in eval_univ]

    # 질문 하나에 정답이 하나만 있도록, 같은 대학·모집 시기·전형·항목 이름이 두 번 이상 나오는 행은 쓰지 않는다.
    sched_key = lambda s: (s["university_code"], s["round_type"], s["type_name"], item_name(s["item"]))
    type_key = lambda t: (t["university_code"], t["round_type"], t["type_name"])
    sched_n, type_n = {}, {}
    for s in data["schedules"]:
        sched_n[sched_key(s)] = sched_n.get(sched_key(s), 0) + 1
    for t in data["types"]:
        type_n[type_key(t)] = type_n.get(type_key(t), 0) + 1

    def schedules(univs):
        return [s for s in data["schedules"] if s["university_code"] in univs and kw_of(s["item"]) and date_ok(s["date"])
                and sched_n[sched_key(s)] == 1 and item_name(s["item"])]

    def counts(univs):
        return [t for t in data["types"] if t["university_code"] in univs and isinstance(t.get("recruitment_count"), int)
                and t["recruitment_count"] > 0 and type_n[type_key(t)] == 1]

    def minimums(univs):
        return [t for t in data["types"] if t["university_code"] in univs and minimum_fact(t.get("min_academic_standard"))
                and type_n[type_key(t)] == 1]

    ev = []
    es, ec, em = schedules(eval_univ), counts(eval_univ), minimums(eval_univ)
    rng.shuffle(es), rng.shuffle(ec), rng.shuffle(em)
    for i, s in enumerate(es[:50]):
        ev.append(make_schedule(b, s, f"ev-sch-{i:03d}", "eval"))
    for i, t in enumerate(ec[:30]):
        ev.append(make_count(b, t, f"ev-cnt-{i:03d}", "eval"))
    for i, t in enumerate(em[:15]):
        ev.append(make_minimum(b, t, f"ev-min-{i:03d}", "eval"))
    for i, (kid, q, facts) in enumerate(OFFICIAL_QA):
        ev.append(make_official_eval(b, kid, q, facts, f"ev-2028-{i:03d}"))
    pool = sorted(eval_univ)
    for i, u in enumerate(rng.sample(pool, 30) if len(pool) >= 30 else [rng.choice(pool) for _ in range(30)]):
        ev.append(make_no_data(b, u, f"ev-nod-{i:03d}", "eval"))
    trap_dates = [s for s in es[50:] if re.search(r"\d{1,2}\.\s*\d{1,2}\.?\s*\(", s["date"])]
    for i, s in enumerate(trap_dates[:10]):
        ev.append(make_schedule(b, s, f"ev-trd-{i:03d}", "eval", trap=True))
    for i, t in enumerate(ec[30:40]):
        ev.append(make_count(b, t, f"ev-trc-{i:03d}", "eval", trap=True))
    for i, t in enumerate(ec[40:50]):
        ev.append(make_guarantee(b, t, f"ev-gua-{i:03d}", "eval"))

    tr = []
    ts, tc, tm = schedules(train_univ), counts(train_univ), minimums(train_univ)
    rng.shuffle(ts), rng.shuffle(tc), rng.shuffle(tm)
    methods = [t for t in data["types"] if t["university_code"] in train_univ and t.get("student_record_method")]
    rng.shuffle(methods)
    # 2028학년도 이후 상담이 중심이므로 올해(2027) 대학별 일정·인원 행은 줄이고 2028 제도 행을 늘린다(2026-11-22 대표).
    for i, s in enumerate(ts[:175]):
        tr.append(make_schedule(b, s, f"tr-sch-{i:04d}", "train"))
    trap_ts = [s for s in ts[175:] if re.search(r"\d{1,2}\.\s*\d{1,2}\.?\s*\(", s["date"])]
    for i, s in enumerate(trap_ts[:40]):
        tr.append(make_schedule(b, s, f"tr-trd-{i:04d}", "train", trap=True))
    for i, t in enumerate(tc[:80]):
        tr.append(make_count(b, t, f"tr-cnt-{i:04d}", "train"))
    for i, t in enumerate(tc[80:120]):
        tr.append(make_count(b, t, f"tr-trc-{i:04d}", "train", trap=True))
    for i, t in enumerate(tc[120:160]):
        tr.append(make_guarantee(b, t, f"tr-gua-{i:04d}", "train"))
    for i, t in enumerate(tm[:60]):
        tr.append(make_minimum(b, t, f"tr-min-{i:04d}", "train"))
    for i, t in enumerate(methods[:50]):
        tr.append(make_method(b, t, f"tr-met-{i:04d}"))
    for i in range(60):
        tr.append(make_no_data(b, rng.choice(train_univ), f"tr-nod-{i:04d}", "train"))
    train_items = [k for k in data["official"] if k["id"] not in HELD_OUT_2028]
    if len(train_items) >= 3:
        for i in range(60):
            tr.append(make_no_data_2028(b, rng.choice(TRAIN_2028_NO_DATA), train_items, f"tr-n28-{i:04d}", "train"))
    for i, k in enumerate([k for k in data["official"] if k["id"] not in HELD_OUT_2028]):
        tr.append(make_knowledge(b, k, f"tr-kno-{i:04d}"))
    for i, k in enumerate(data["youtube"]):
        structured = "주장:" in k["content"] and re.match(r"^\[.*?\].*?:", k["title"])
        tr.append(make_youtube_analysis(b, k, f"tr-yt-{i:04d}") if structured else make_knowledge(b, k, f"tr-yt-{i:04d}", youtube=True))
    have = {x["id"] for x in data["official"]}
    # 같은 사실을 학부모·학생이 묻는 말투로 세 번씩(질문 그대로, 학부모 상황, 학생 상황) 만든다.
    asker = ["", "예비 고1 학부모인데요, ", "중학생인데 궁금해서요. "]
    n = 0
    for kid, q, facts in [t for t in train_qa if t[0] in have]:
        for prefix in asker:
            tr.append(make_official_train(b, kid, prefix + q, facts, f"tr-kqa-{n:04d}"))
            n += 1
    return ev, tr


def write_jsonl(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def write_review(path, rows):
    """사람이 정답 사실을 확인할 표(엑셀에서 열림)."""
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "유형", "질문", "정답 사실", "정답 행동", "정답 근거 자료", "확인(O/X)", "메모"])
        for r in rows:
            g = r["gold"]
            cite = " | ".join(c["text"] for c in r["source_chunks"] if c["id"] in g["cite"])
            action = "합격 단정 금지" if g.get("no_guarantee") else ("자료 없음" if g["abstain"] else "사실 답변")
            w.writerow([r["id"], r["metadata"]["category"], r["input"], " / ".join(g["facts"]), action,
                        cite, "", ""])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed-dir", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--trial-size", type=int, default=200, help="짧은 시험 학습용 앞부분(대학이 섞이도록 섞은 뒤 자름)")
    args = parser.parse_args()
    ev, tr = build(load(args.seed_dir))
    out = Path(args.out)
    write_jsonl(out / "eval.jsonl", ev)
    write_jsonl(out / "train.jsonl", tr)
    trial = list(tr)
    random.Random(SEED + 1).shuffle(trial)
    write_jsonl(out / "train_trial.jsonl", trial[:args.trial_size])
    write_review(out / "eval_review.csv", ev)
    ev28 = build_eval_2028(load(args.seed_dir), ev)
    write_jsonl(out / "eval_2028.jsonl", ev28)
    write_review(out / "eval_2028_review.csv", ev28)
    cats = {}
    for r in ev:
        cats[r["metadata"]["category"]] = cats.get(r["metadata"]["category"], 0) + 1
    cats28 = {}
    for r in ev28:
        cats28[r["metadata"]["category"]] = cats28.get(r["metadata"]["category"], 0) + 1
    print(json.dumps({"eval_2028": len(ev28), "eval_2028_by_category": cats28}, ensure_ascii=False))
    print(json.dumps({"eval": len(ev), "train": len(tr), "trial": min(args.trial_size, len(tr)), "eval_by_category": cats},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
