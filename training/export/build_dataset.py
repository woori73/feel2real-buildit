"""T07 저장소 자료 → f2r-eval-v1 JSONL(학습·평가) 내보내기.

python3 -m training.export.build_dataset --out training/data

읽는 자료(모두 저장소 안, DB·외부 서버 접속 없음):
  내신  : naesintest 마이그레이션(압구정중2 V1116, 신구중2 V1120·V1121, 압구정중1 V1127·V1128) + 문항 그림 OCR
  수능형: 예비고1 2부 고1 실전 독해 변형 10회(prehigh-part2), 평가원 기출 3회분(fixtures/csat_day1),
          2027 수능(static/img/csat/2027-en), 9월 전국연합 고1·2 변형(private-reference, 평가 전용)
  문법  : 예비고1 문법(pre-high-english/grammar, F2R 제작)
  SAT   : TestPrep SAT 미니 세트(V1113·V1114, F2R 제작)
  iBT   : training/export/sources/ibt_f2r_original.json(F2R 제작)

나누기(track별, 같은 출처 묶음·지문이 학습과 평가에 같이 들어가지 않게):
  내신  : 압구정중1(다른 교과서·다른 출제 교사)을 통째로 평가에 둔다(school holdout).
  수능형: 2026-09-01 이전 시험은 학습, 이후(9월 모평·9월 전국연합·2027 수능)는 평가(date holdout).
  SAT·iBT·문법: 학습만(평가 세트 없음, 개수가 작음).
OCR 문항은 metadata.ocr=true, 선택지를 5개로 못 나눈 문항은 학습에서 뺀다.
"""
import argparse
import glob
import gzip
import json
import random
import re
from collections import Counter
from pathlib import Path

from . import sql_rows
from ..common import validate_record
from .ocr_images import cache_path
from .text_utils import CIRCLED, digest, fix_insertion_marks, norm_space, ocr_noise, split_question, strip_score

REPO = Path(__file__).resolve().parents[2]
MIG = REPO / "src/main/resources/db/migration"
LETTER = {c: i + 1 for i, c in enumerate("ABCDE")}

PRINCIPLES_COMMON = (
    "F2R 영어 출제 원칙: 근거 자료(교과서 본문·학습지·기출 지문)에서 벗어나지 않는 문항을 만든다. 정답은 하나로만 정해져야 한다. "
    "지문 하나에 객관식 문항이 둘 이상이면 발문을 '윗글…', 하나면 '다음 글…'로 쓴다. 무관한 문장 문항 발문에는 '다음 글의 ①~⑤ 중' 또는 '윗글의 ①~⑤ 중'을 붙인다. "
    "어법 문항은 관사 같은 지엽적 포인트가 아니라 관계사·접속사·주어와 동사의 수일치·도치·강조구문 등을 묻는다. 과장된 표현을 쓰지 않고, 해설은 존댓말로 정답 근거와 오답 이유를 쓴다."
)
NAESIN_SCHOOLS = {
    "APM2": {"school": "압구정중", "grade": "2", "exam": "2026학년도 2학기 기말", "files": ["V1116"],
             "style": "압구정중 2학년 출제 교사의 직전 기출(1학기 기말·2학기 중간) 발문·선택지·기호를 따른다. 범위는 Lesson 7·8 교과서와 학습지 필기본."},
    "SGM2": {"school": "신구중", "grade": "2", "exam": "2026학년도 2학기 기말", "files": ["V1120", "V1121"],
             "style": "신구중 2학년 2학기 중간고사 기출의 발문·선택지·편집·부호를 따른다. 범위는 2학기말 학습지 필기본 Lesson 7·8."},
    "APM1": {"school": "압구정중", "grade": "1", "exam": "2026학년도 2학기 기말", "files": ["V1127"],
             "style": "출제 교사의 압구정중 2024학년도 3학년 기출의 발문·선택지·부호를 따른다. 범위는 교과서(천재 소영순 1학년) Lesson 3~5 본문·대화문과 2학기말 학습지 필기본."},
}
NAESIN_EXAM_DATE = "2026-12-10"  # 2학기 기말 대상(정확한 시험일은 학교마다 다름 — 내신은 날짜가 아니라 학교로 나눈다)
CSAT_CUTOFF = "2026-09-01"
NOISE_LIMIT = 2  # 발문·선택지 한글 줄에 OCR 잡음 토큰이 이만큼 있으면 뺀다
CSAT_EXAMS = {  # 파일 → (시험 이름, 시행일)
    "payload_nov2025csat.json": ("2026학년도 수능(2025-11-13 시행)", "2025-11-13"),
    "payload_june.json": ("2027학년도 6월 모의평가", "2026-06-04"),
    "payload_sept.json": ("2027학년도 9월 모의평가", "2026-09-03"),
}


def meta(track, group, exam_date, *, verified=True, allowed=True, ocr=False, **extra):
    m = {"domain": "education", "deidentified": True, "source_group": group, "exam_date": exam_date,
         "teacher_verified": verified, "training_allowed": allowed, "track": track, "ocr": ocr}
    m.update(extra)
    return m


def mc_question(qtype, source, stem, options, answer, score):
    return {"type": "mc", "questionType": qtype, "sourceText": source, "stem": stem, "options": options,
            "answer": answer, "multi": len(answer) > 1, "score": score, "conditions": [], "answerFormat": "",
            "acceptedAnswers": [], "sampleAnswer": "", "gradingRule": ""}


def sa_question(qtype, source, stem, accepted, sample, fmt, rule, score):
    return {"type": "sa", "questionType": qtype, "sourceText": source, "stem": stem, "options": [], "answer": [],
            "multi": False, "score": score, "conditions": [], "answerFormat": fmt, "acceptedAnswers": accepted,
            "sampleAnswer": sample, "gradingRule": rule}


def evidence_for(chunk_id, chunk_text, explanation):
    """해설이 인용한 영어 구절이 근거 청크에 있으면 그 구절, 없으면 청크 첫 문장을 근거로 둔다."""
    flat = norm_space(chunk_text)
    for quote in re.findall(r"[‘'\"“]([A-Za-z][^’'\"”]{8,120})[’'\"”]", explanation or ""):
        if norm_space(quote) in flat:
            return [{"id": chunk_id, "quote": norm_space(quote)}]
    for phrase in re.findall(r"[A-Za-z][A-Za-z ,'’\-]{15,80}", explanation or ""):
        p = norm_space(phrase).strip(" ,")
        if len(p) > 15 and p in flat:
            return [{"id": chunk_id, "quote": p}]
    first = re.split(r"(?<=[.!?])\s", flat, maxsplit=1)[0][:160]
    return [{"id": chunk_id, "quote": first}] if first else []


def ocr_text(web_path):
    """'/naesin/...webp'(정적 경로) 또는 저장소 상대 경로 → OCR 캐시 글자."""
    rel = web_path.lstrip("/")
    image = REPO / ("src/main/resources/static/" + rel if rel.startswith("naesin/") else rel)
    path = cache_path(image, REPO)
    if not path.exists():
        raise FileNotFoundError(f"OCR 캐시 없음: {path} — 먼저 training.export.ocr_images 실행")
    return path.read_text(encoding="utf-8"), image


IMG = re.compile(r"\[\[img:([^\]]+)\]\]")


def explanation_head(explanation):
    """'정답 ③ · 유형 또는 발문 (3점)' 첫 줄에서 유형(또는 발문)을 꺼낸다."""
    first = (explanation or "").split("\n", 1)[0]
    m = re.match(r"정답\s*[①-⑤,\s]+·\s*(.+?)\s*(?:\(\d+점\))?$", first)
    return m.group(1).strip() if m else ""


def naesin_rows(key, info, stats):
    rows = []
    files = [glob.glob(str(MIG / f"{f}__*.sql"))[0] for f in info["files"]]
    for path in files:
        for code, block in sql_rows.exam_blocks(path).items():
            exam = block["exam"]
            for q in block["questions"]:
                stats[f"{key}:문항"] += 1
                qimg = IMG.search(q["prompt"] or "")
                if not qimg:
                    stats[f"{key}:그림 없음"] += 1
                    continue
                q_ocr, _ = ocr_text(qimg.group(1))
                number, stem_ocr, body, options = split_question(q_ocr)
                passage = ""
                pimg = IMG.search(q["body_text"] or "")
                if pimg:
                    p_ocr, _ = ocr_text(pimg.group(1))
                    passage = p_ocr.strip()
                head = explanation_head(q["explanation"])
                # 압구정중1 해설 첫 줄은 발문 전체, 다른 학교는 유형 이름이다.
                stem = head if (key == "APM1" and head.endswith(("?", "?)"))) else stem_ocr
                qtype = head if key != "APM1" else (re.sub(r"\s+", " ", head)[:40])
                answer = sorted(LETTER[c] for c in str(q["answer_key"]).replace(" ", "").split(",") if c in LETTER)
                source = "\n".join(x for x in [passage, body] if x).strip()
                source, _ = fix_insertion_marks(source)
                if options is None:
                    stats[f"{key}:선택지 인식 실패"] += 1
                chunks = [{"id": f"{code}-p", "text": source}] if source else []
                question = mc_question(qtype or "내신", source, stem, options or [], answer, int(q["points"] or 0))
                qid = f"naesin-{code}-q{int(q['order_no']):02d}"
                rows.append({
                    "id": qid,
                    "instruction": PRINCIPLES_COMMON + " " + info["style"],
                    "input": {"track": "내신", "school": info["school"], "grade": info["grade"], "exam": info["exam"],
                              "set": exam["title"], "scope": exam["subtitle"], "questionType": qtype,
                              "score": int(q["points"] or 0),
                              "request": f"근거 자료로 {int(q['points'] or 0)}점짜리 '{qtype}' 객관식 문항 1개를 이 학교 형식으로 만드십시오."},
                    "source_chunks": chunks,
                    "output": {"question": question,
                               "evidence_refs": evidence_for(f"{code}-p", source, q["explanation"]) if chunks else [],
                               "explanation": q["explanation"]},
                    "reference_question": norm_space(stem + " " + " ".join(options or [])),
                    "metadata": meta("naesin", f"naesin:{key}", NAESIN_EXAM_DATE, ocr=True,
                                     school_key=key, options_parsed=options is not None,
                                     ocr_noise=ocr_noise(stem, "\n".join(options or [])),
                                     dedup=digest(stem + "|".join(options or []) + source[:200])),
                })
    return rows


def prehigh_part2_rows(stats):
    """예비고1 2부: 고1 사설 모의고사 18~45번을 F2R이 수능 논리로 바꾼 변형 10회(다른 세션 2차 감수)."""
    rows = []
    for rnd in sorted((REPO / "src/main/resources/prehigh-part2").glob("r*/round.json")):
        d = json.loads(rnd.read_text(encoding="utf-8"))
        year = re.search(r"(20\d\d)", d["source"])
        exam_date = f"{year.group(1)}-05-01" if year else "2025-01-01"  # 원본 사설 모의고사 시행 연도(평가 cutoff보다 이전)
        for i, item in enumerate(d["reading"]):
            no = 18 + i
            stats["prehigh2:문항"] += 1
            q_ocr, _ = ocr_text(str((rnd.parent / item["image"]).relative_to(REPO)))
            number, stem, body, options = split_question(q_ocr)
            passage = ""
            if item.get("passage"):
                passage = ocr_text(str((rnd.parent / item["passage"]).relative_to(REPO)))[0].strip()
            source, _ = fix_insertion_marks("\n".join(x for x in [passage, body] if x))
            if options is None:
                stats["prehigh2:선택지 인식 실패"] += 1
            head = explanation_head(item["explanation"])
            score = 3 if "(3점)" in item["explanation"].split("\n", 1)[0] else 2
            cid = f"PH2-R{d['number']:02d}-{no}"
            rows.append({
                "id": f"csat-h1var-r{d['number']:02d}-q{no}",
                "instruction": PRINCIPLES_COMMON + " 수능 영어 독해 형식(18~45번)을 따른다. 선택지는 하나만 정답이 되게 매력적인 오답을 만들고, 해설은 [유형 전략]·[풀이 과정]·[정답 근거]·[오답 분석]·[어휘] 순서로 쓴다.",
                "input": {"track": "수능형", "level": "고1", "questionNo": no, "questionType": head,
                          "score": score, "request": f"근거 지문으로 수능 {no}번 '{head}' 유형 문항 1개를 만드십시오."},
                "source_chunks": [{"id": cid, "text": source}] if source else [],
                "output": {"question": mc_question(head, source, stem, options or [], [int(item["answer"])], score),
                           "evidence_refs": evidence_for(cid, source, item["explanation"]) if source else [],
                           "explanation": item["explanation"]},
                "reference_question": norm_space(stem + " " + " ".join(options or [])),
                "metadata": meta("csat", f"csat-h1var:r{d['number']:02d}", exam_date, ocr=True,
                                 options_parsed=options is not None, origin=d["source"],
                                 ocr_noise=ocr_noise(stem, "\n".join(options or [])),
                                 note="원본 사설 모의고사를 F2R이 지문·선택지 변형하고 해설을 새로 씀",
                                 dedup=digest(stem + "|".join(options or []) + source[:200])),
            })
    return rows


def _choices(raw):
    if isinstance(raw, str):
        try:
            raw = json.loads(raw.replace("'", '"'))
        except json.JSONDecodeError:
            raw = [x.strip(" '") for x in raw.strip("[]").split("', '")]
    return [re.sub(r"^\s*[1-5]\.\s*", "", c).strip() for c in raw]


def kice_rows(stats):
    """평가원 기출(수능·모평) 글자본. 지문·문항 원문은 평가원 저작물 — 학습에 쓰되 생성물 겹침을 평가한다."""
    rows = []
    base = REPO / "problem-generator/wiki-backend-src/fixtures/csat_day1"
    for name, (label, date_) in CSAT_EXAMS.items():
        d = json.loads((base / name).read_text(encoding="utf-8"))
        for q in d["questions"]:
            stats["kice:문항"] += 1
            options = _choices(q["choices"])
            answer = [int(str(q["answer"]).strip()[0])] if str(q["answer"]).strip()[:1].isdigit() else []
            if len(options) != 5 or not answer:
                stats["kice:형식 불완전"] += 1
                continue
            no = str(q["question_no"])
            cid = f"KICE-{date_}-{no}"
            passage = q["passage"] or ""
            rows.append({
                "id": f"kice-{date_}-q{no}",
                "instruction": PRINCIPLES_COMMON + " 수능 영어 독해 형식(18~45번)을 따른다.",
                "input": {"track": "수능형", "exam": label, "questionNo": no, "questionType": q["prompt"],
                          "request": f"근거 지문으로 수능 {no}번형 문항 1개를 만드십시오."},
                "source_chunks": [{"id": cid, "text": passage}],
                "output": {"question": mc_question(q["prompt"], passage, q["prompt"], options, answer, 2),
                           "evidence_refs": evidence_for(cid, passage, q.get("solution", "")),
                           "explanation": q.get("solution", "")},
                "reference_question": norm_space(q["prompt"] + " " + " ".join(options)),
                "gold": {"answer": answer},
                "metadata": meta("csat", f"kice:{date_}", date_, gold_source="평가원 정답", exam_name=label,
                                 note="평가원 기출 원문(지문 띄어쓰기 오류 일부 있음)"),
            })
    return rows


def csat2027_rows(stats):
    """2027학년도 수능(2026-11-19) 18~45번: 문항 그림 OCR + 평가원 가답안 + F2R 해설. 평가 전용."""
    rows = []
    base = REPO / "src/main/resources/static/img/csat/2027-en"
    d = json.loads((base / "data.json").read_text(encoding="utf-8"))
    for q in d["questions"]:
        if q["section"] != "독해" or len(q["nos"]) != 1:
            continue
        no = q["nos"][0]
        stats["csat2027:문항"] += 1
        text = "\n".join(ocr_text(str((base / b["src"]).relative_to(REPO)))[0]
                         for b in q["left"] if b.get("kind") == "img")
        number, stem, body, options = split_question(text)
        explanation = "\n".join(" ".join(p["body"]) if p.get("body") else
                                " ".join(f"{c['no']} {c['text']}" for c in p.get("choices", []))
                                for p in q["right"])
        if options is None:
            stats["csat2027:선택지 인식 실패"] += 1
            continue
        source, _ = fix_insertion_marks(body)
        cid = f"CSAT2027-{no}"
        ans = [int(q["answers"][str(no)])]
        rows.append({
            "id": f"csat2027-q{no}",
            "instruction": PRINCIPLES_COMMON + " 수능 영어 독해 형식(18~45번)을 따른다.",
            "input": {"track": "수능형", "exam": "2027학년도 수능", "questionNo": no, "questionType": q["type"],
                      "request": f"근거 지문으로 수능 {no}번 '{q['type']}' 유형 문항 1개를 만드십시오."},
            "source_chunks": [{"id": cid, "text": source}],
            "output": {"question": mc_question(q["type"], source, stem, options, ans, 2),
                       "evidence_refs": evidence_for(cid, source, explanation), "explanation": explanation},
            "reference_question": norm_space(stem + " " + " ".join(options)),
            "gold": {"answer": ans},
            "metadata": meta("csat", "kice:2026-11-19", "2026-11-19", ocr=True, allowed=False,
                             gold_source="평가원 공개 가답안(2026-11-19)", exam_name="2027학년도 수능"),
        })
    return rows


def private_reference_rows(stats):
    """9월 전국연합(고1·2) 변형 문항(외부 자료로 보임, 이용 권한 미확인) — 학습 금지, 풀이 평가 전용."""
    rows = []
    for f in sorted(glob.glob(str(REPO / "problem-generator/wiki-backend-src/backend/private-reference/g*-q*.json.gz"))):
        for r in json.load(gzip.open(f))["records"]:
            stats["private-ref:문항"] += 1
            ch = r.get("choices") or []
            ans = (r.get("answer") or {}).get("choice")
            if len(ch) != 5 or not isinstance(ans, int) or r.get("validation", {}).get("status") != "PASS":
                continue
            opts = [c["text"] for c in ch]
            stem = re.sub(r"\s*\[\d+(-\d+)?\]\s*$", "", r["instruction"])
            cid = f"PREF-g{r['grade']}-{r['variantIndex']}"
            body = r.get("bodyText") or ""
            rows.append({
                "id": f"pref-g{r['grade']}-v{r['variantIndex']}",
                "task": "solve",
                "instruction": "다음 문항을 풀어, 문항을 그대로 question 객체로 옮기고 answer에 고른 번호를 넣으십시오. 근거가 된 지문 구절을 evidence_refs에 인용하십시오.",
                "input": {"track": "수능형", "stem": stem, "options": opts, "givenSentence": r.get("givenSentence") or "",
                          "summarySentence": r.get("summarySentence") or ""},
                "source_chunks": [{"id": cid, "text": body}] if body.strip() else [],
                "gold": {"answer": [ans]},
                "metadata": meta("csat", f"pref:{r['examYear']}-09:g{r['grade']}", "2026-09-03", allowed=False,
                                 gold_source="출처 자료 정답(PDF 대조 PASS)", origin=r["provenance"]["sourceFile"]),
            })
    return rows


def grammar_rows(stats):
    rows = []
    for f in sorted((REPO / "src/main/resources/pre-high-english/grammar").glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        for i, q in enumerate(d["questions"], 1):
            stats["grammar:문항"] += 1
            cid = f"GR-{f.stem}-{i}"
            chunk = f"{q['completed']}\n({q['translation']})\n문법 포인트: {q['topic']} — {q['principle']}"
            expl = (f"정답 {CIRCLED[q['answer'] - 1]} · {q['topic']}\n[원리] {q['principle']}\n[전략] {q['strategy']}\n[근거] {q['evidence']}\n[선택지] "
                    + " / ".join(f"{CIRCLED[k]} {t}" for k, t in enumerate(q["reasons"])))
            rows.append({
                "id": f"grammar-{f.stem}-{i:03d}",
                "instruction": PRINCIPLES_COMMON + " 예비고1 문법 5지선다: 빈칸 하나에 다섯 선택지가 같은 자리를 두고 경쟁하게 만든다.",
                "input": {"track": "문법", "unit": d.get("title", f.stem), "topic": q["topic"],
                          "request": "근거 문장으로 빈칸 문법 문항 1개와 해설을 만드십시오."},
                "source_chunks": [{"id": cid, "text": chunk}],
                "output": {"question": mc_question("문법", q["text"], q["instruction"], q["choices"], [q["answer"]], 1),
                           "evidence_refs": [{"id": cid, "quote": q["completed"]}], "explanation": expl},
                "reference_question": norm_space(q["text"] + " " + " ".join(q["choices"])),
                "metadata": meta("grammar", f"grammar:{f.stem}", "2026-11-01", origin="F2R 예비고1 문법"),
            })
    return rows


def sat_rows(stats):
    rows = []
    for f in ("V1113", "V1114"):
        for code, block in sql_rows.exam_blocks(glob.glob(str(MIG / f"{f}__*.sql"))[0]).items():
            for q in block["questions"]:
                stats["sat:문항"] += 1
                body = q["body_text"] or ""
                cid = f"{code}-{q['order_no']}"
                if q["task_type"] == "SAT_MATH_SPR":
                    acc = [a for a in str(q["answer_key"]).split("|") if a]
                    question = sa_question("SAT Math SPR", body, q["prompt"], acc, acc[0], "숫자", "허용 답 중 하나와 같으면 정답", 1)
                else:
                    opts = json.loads(q["choices_json"] or "[]")
                    question = mc_question(f"SAT {q['section']}", body, q["prompt"], opts, [LETTER[q["answer_key"]]], 1)
                rows.append({
                    "id": f"sat-{code}-{q['order_no']:02d}",
                    "instruction": "Digital SAT 형식 문항을 만든다. College Board 문항을 베끼지 않는다. 정답은 하나로 정해져야 하고, 해설은 영어 근거와 한국어 풀이를 함께 쓴다.",
                    "input": {"track": "SAT", "section": q["section"], "taskType": q["task_type"],
                              "request": "근거 자료로 같은 영역의 SAT 문항 1개를 만드십시오."},
                    "source_chunks": [{"id": cid, "text": body}] if body.strip() else [],
                    "output": {"question": question,
                               "evidence_refs": evidence_for(cid, body, q["explanation"]) if body.strip() else [],
                               "explanation": q["explanation"]},
                    "metadata": meta("sat", f"sat:{code}", "2026-11-01", origin="F2R 제작 SAT 연습 문항"),
                })
    return rows


def ibt_rows(stats):
    d = json.loads((Path(__file__).with_name("sources") / "ibt_f2r_original.json").read_text(encoding="utf-8"))
    rows = []
    for it in d["items"]:
        stats["ibt:문항"] += 1
        cid = it["id"] + "-src"
        if it["type"] == "mc":
            q = mc_question(f"TOEFL iBT {it['task']}", it["sourceText"], it["stem"], it["options"], it["answer"], 1)
        else:
            q = sa_question(f"TOEFL iBT {it['task']}", it["sourceText"], it["stem"], it["acceptedAnswers"],
                            it["sampleAnswer"], it["answerFormat"], it["gradingRule"], 1)
        rows.append({
            "id": it["id"],
            "instruction": "2026년 개편 TOEFL iBT 형식 문항을 만든다. ETS 문항을 베끼지 않는다. 정답이 하나로 정해져야 하고 해설은 근거 문장을 짚는다.",
            "input": {"track": "iBT", "section": it["section"], "task": it["task"],
                      "request": f"근거 자료로 '{it['task']}' 문항 1개를 만드십시오."},
            "source_chunks": [{"id": cid, "text": it["sourceText"]}] if it["sourceText"] else [],
            "output": {"question": q, "evidence_refs": evidence_for(cid, it["sourceText"], it["explanation"]) if it["sourceText"] else [],
                       "explanation": it["explanation"]},
            "metadata": meta("ibt", f"ibt:{it['task']}", "2026-11-01", origin="F2R 제작 iBT 문항(ETS 문항 미사용)"),
        })
    return rows


def solve_view(row):
    """생성용 행을 같은 문항의 '풀이' 평가 행으로 바꾼다(정답은 gold로만, 입력에서 뺀다)."""
    q = row["output"]["question"]
    return {"id": row["id"] + "-solve", "task": "solve",
            "instruction": "다음 문항을 풀어, 문항을 그대로 question 객체로 옮기고 answer에 고른 번호를 넣으십시오. 근거가 된 지문 구절을 evidence_refs에 인용하십시오.",
            "input": {"track": row["input"]["track"], "stem": q["stem"], "options": q["options"]},
            "source_chunks": row["source_chunks"], "gold": {"answer": q["answer"]},
            "metadata": dict(row["metadata"])}


def gen_eval_view(row):
    """평가용 생성 행: 모범 output은 빼고, 원문 겹침 계산용 reference_question만 남긴다."""
    out = {k: v for k, v in row.items() if k not in ("output", "gold")}
    out["task"] = "generate"
    return out


def build(out_dir, seed=20261122):
    stats = Counter()
    rows = []
    for key, info in NAESIN_SCHOOLS.items():
        rows += naesin_rows(key, info, stats)
    rows += prehigh_part2_rows(stats) + kice_rows(stats) + csat2027_rows(stats)
    rows += grammar_rows(stats) + sat_rows(stats) + ibt_rows(stats)
    pref = private_reference_rows(stats)

    # 같은 문항(발문·선택지·지문 앞부분이 같은 것: 종합 회차 재수록 등)은 한 번만 남긴다.
    seen, uniq = set(), []
    for r in rows:
        k = r["metadata"].get("dedup")
        if k and k in seen:
            stats["중복 제거"] += 1
            continue
        seen.add(k)
        uniq.append(r)
    rows = uniq

    train, eval_gen, eval_solve = [], [], []
    for r in rows:
        m = r["metadata"]
        usable = bool(r["output"]["question"]["options"]) or r["output"]["question"]["type"] == "sa"
        if m.get("ocr_noise", 0) >= NOISE_LIMIT:
            stats["OCR 잡음 많음(학습·평가 제외)"] += 1
            continue
        if m["track"] == "naesin" and m["school_key"] == "APM1":
            if usable and r["source_chunks"]:
                eval_gen.append(gen_eval_view(r))
                eval_solve.append(solve_view(r))
            continue
        if m["track"] == "csat" and m["exam_date"] >= CSAT_CUTOFF:
            eval_gen.append(gen_eval_view(r))
            eval_solve.append(solve_view(r) if "gold" not in r else
                              {**solve_view(r), "gold": r["gold"]})
            continue
        if not m["training_allowed"]:
            continue
        if not usable:
            stats["학습 제외(선택지 인식 실패)"] += 1
            continue
        train.append({k: v for k, v in r.items() if k not in ("gold",)})
    rnd = random.Random(seed)
    pref_sample = rnd.sample(pref, min(len(pref), max(0, 200 - sum(1 for r in eval_solve if r["metadata"]["track"] == "csat"))))
    eval_solve += pref_sample

    def valid(data, **kw):
        kept = []
        for r in data:
            r["source_chunks"] = [c for c in r.get("source_chunks", []) if c["text"].strip()]
            if kw.get("evaluation") and not r["source_chunks"]:
                stats["평가 제외(근거 지문 없음)"] += 1
                continue
            try:
                validate_record(r, **kw)
            except ValueError as exc:
                stats[f"계약 검사 실패: {exc}"] += 1
                continue
            kept.append(r)
        return kept

    train = valid(train, training=True)
    eval_gen = valid(eval_gen, evaluation=True)
    eval_solve = valid(eval_solve, evaluation=True)
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    files = {"train.jsonl": train, "eval_generate.jsonl": eval_gen, "eval_solve.jsonl": eval_solve,
             "eval.jsonl": eval_gen + eval_solve}
    for name, data in files.items():
        with (out / name).open("w", encoding="utf-8") as fh:
            for r in data:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary = {
        "stats": dict(stats),
        "train": dict(Counter(r["metadata"]["track"] for r in train)),
        "eval_generate": dict(Counter(r["metadata"]["track"] for r in eval_gen)),
        "eval_solve": dict(Counter(r["metadata"]["track"] for r in eval_solve)),
        "csat_cutoff": CSAT_CUTOFF, "naesin_holdout_school": "압구정중1(APM1)",
    }
    (out / "export_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--out", default=str(REPO / "training/data"))
    a = p.parse_args()
    print(json.dumps(build(a.out), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
