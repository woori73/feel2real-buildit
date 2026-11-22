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
OFFICIAL_EVAL = {"R2028-NAESIN", "R2028-CSAT", "R2028-SCHEDULE", "R2028-COHORT"}
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
            "youtube": [y for y in univ.get("youtube_knowledge", []) if str(y.get("academic_year")) == "2028"]}


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
    gold = {"facts": [f"합 {total}"], "cite": ids, "abstain": False}
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
    others = [x for x in b.data["official"] if x["id"] != k.get("id")]
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
    body = " ".join(sentences) + f" [{gid}]"
    caveat = ("이 내용은 유튜브 입시 해석을 정리한 것이라 공식 발표는 아닙니다."
              if youtube else "2028학년도 이후 내용은 계획 단계라 바뀔 수 있습니다.")
    out = "\n\n".join([lead, body, caveat,
                       "다음에 할 일: 자녀가 치를 학년도의 대학별 시행계획과 모집요강이 나오면 지원할 대학 입학처에서 확인해 주세요."])
    return row(rid, q, chunks, "K-" + str(k.get("id") or k.get("source_url")), "reform_2028", None, out, "train")


def make_official_eval(b, kid, q, facts, rid):
    k = next(x for x in b.data["official"] if x["id"] == kid)
    others = [x for x in b.data["official"] if x["id"] not in OFFICIAL_EVAL]
    texts = [knowledge_text(k)] + [knowledge_text(x) for x in b.rng.sample(others, 2)]
    order = list(range(3))
    b.rng.shuffle(order)
    chunks = [{"id": f"s{pos}", "text": texts[idx]} for pos, idx in enumerate(order, start=1)]
    gid = f"s{order.index(0) + 1}"
    return row(rid, q, chunks, "K-" + kid, "reform_2028", {"facts": facts, "cite": [gid], "abstain": False})


def build(data, eval_universities=40, seed=SEED):
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
    for i, s in enumerate(ts[:380]):
        tr.append(make_schedule(b, s, f"tr-sch-{i:04d}", "train"))
    trap_ts = [s for s in ts[380:] if re.search(r"\d{1,2}\.\s*\d{1,2}\.?\s*\(", s["date"])]
    for i, s in enumerate(trap_ts[:40]):
        tr.append(make_schedule(b, s, f"tr-trd-{i:04d}", "train", trap=True))
    for i, t in enumerate(tc[:180]):
        tr.append(make_count(b, t, f"tr-cnt-{i:04d}", "train"))
    for i, t in enumerate(tc[180:220]):
        tr.append(make_count(b, t, f"tr-trc-{i:04d}", "train", trap=True))
    for i, t in enumerate(tc[220:260]):
        tr.append(make_guarantee(b, t, f"tr-gua-{i:04d}", "train"))
    for i, t in enumerate(tm[:100]):
        tr.append(make_minimum(b, t, f"tr-min-{i:04d}", "train"))
    for i, t in enumerate(methods[:80]):
        tr.append(make_method(b, t, f"tr-met-{i:04d}"))
    for i in range(90):
        tr.append(make_no_data(b, rng.choice(train_univ), f"tr-nod-{i:04d}", "train"))
    for i, k in enumerate([k for k in data["official"] if k["id"] not in OFFICIAL_EVAL]):
        tr.append(make_knowledge(b, k, f"tr-kno-{i:04d}"))
    for i, k in enumerate(data["youtube"]):
        tr.append(make_knowledge(b, k, f"tr-yt-{i:04d}", youtube=True))
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
    cats = {}
    for r in ev:
        cats[r["metadata"]["category"]] = cats.get(r["metadata"]["category"], 0) + 1
    print(json.dumps({"eval": len(ev), "train": len(tr), "trial": min(args.trial_size, len(tr)), "eval_by_category": cats},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
