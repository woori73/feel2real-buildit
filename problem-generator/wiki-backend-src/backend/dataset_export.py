"""T07(DB 경로) — 내신위키 Generated Question Store → f2r-eval-v1 학습 JSONL. PLAN 4-6.

python3 backend/dataset_export.py --db /path/wiki.db --out training/data/wiki_train.jsonl

- DB는 읽기 전용(mode=ro)으로 연다. 운영 DB를 바꾸지 않는다.
- 내보내는 문항: verify.status='일치'이고 teacher.action이 기록된(교사 확인) 문항. 교사가 고친 문항은 저장된
  최종본이 고친 뒤 버전이다. 근거 지문(source_text)이 없는 문항은 뺀다.
- 개인정보: 전화번호·이메일·주민번호 모양이 보이는 문항은 내보내지 않는다(지우지 않고 통째로 뺀다).
  학생 오답(student_wrong_notes)·채무상담 자료는 읽지 않는다.
- exam_date: 시험 날짜가 DB에 없어 학기·시험으로 대표 날짜를 정한다(1학기 중간 04-25, 1학기 기말 07-01,
  2학기 중간 10-15, 2학기 기말 12-10). 학교별 실제 날짜가 있으면 --exam-dates JSON으로 덮어쓴다.
- 저장소 자료(naesintest·수능형·SAT·iBT)는 training/export/build_dataset.py가 맡는다. 두 파일을 합쳐 학습한다.
"""
import argparse
import json
import re
import sqlite3
from pathlib import Path

PII = re.compile(r"01[016789][- .]?\d{3,4}[- .]?\d{4}|[\w.+-]+@[\w-]+\.[\w.]+|\d{6}[- ]?[1-4]\d{6}")
DEFAULT_DATES = {("1", "중간"): "04-25", ("1", "기말"): "07-01", ("2", "중간"): "10-15", ("2", "기말"): "12-10"}


def exam_date(year, semester, exam, overrides=None, school=""):
    key = f"{school}|{year}|{semester}|{exam}"
    if overrides and key in overrides:
        return overrides[key]
    sem = re.sub(r"\D", "", str(semester or "")) or "1"
    kind = "기말" if "기말" in str(exam or "") else "중간"
    return f"{int(year)}-{DEFAULT_DATES[(sem, kind)]}" if year else None


def export(db_path, overrides=None):
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    rows, skipped = [], {"no_verify_match": 0, "no_teacher": 0, "no_source": 0, "pii": 0, "no_date": 0}
    sql = """SELECT q.*, t.year t_year, t.semester t_semester, t.exam t_exam, t.school t_school, t.grade t_grade,
                    t.twin_label, t.twin_pattern, t.title t_title, t.id t_id
             FROM generated_questions q JOIN generated_tests t ON t.id = q.test_id ORDER BY q.id"""
    for r in conn.execute(sql):
        raw = json.loads(r["raw_json"] or "{}")
        verify, teacher, evidence = raw.get("verify") or {}, raw.get("teacher") or {}, raw.get("evidence") or {}
        if verify.get("status") != "일치":
            skipped["no_verify_match"] += 1
            continue
        if not str(teacher.get("action", "")).strip():
            skipped["no_teacher"] += 1
            continue
        if not (r["source_text"] or "").strip():
            skipped["no_source"] += 1
            continue
        text = " ".join(str(r[k] or "") for k in ("stem", "source_text", "options_json", "sample_answer"))
        if PII.search(text):
            skipped["pii"] += 1
            continue
        date_ = exam_date(r["t_year"], r["t_semester"], r["t_exam"], overrides, r["t_school"])
        if not date_:
            skipped["no_date"] += 1
            continue
        question = {
            "type": r["q_kind"], "questionType": r["question_type"], "sourceText": r["source_text"], "stem": r["stem"],
            "options": json.loads(r["options_json"] or "[]"), "answer": json.loads(r["answer_json"] or "[]"),
            "multi": bool(r["multi"]), "score": r["score"], "conditions": json.loads(r["conditions_json"] or "[]"),
            "answerFormat": r["answer_format"], "acceptedAnswers": json.loads(r["accepted_answers_json"] or "[]"),
            "sampleAnswer": r["sample_answer"], "gradingRule": r["grading_rule"],
        }
        cid = f"wiki-q{r['id']}-src"
        rows.append({
            "id": f"wiki-q{r['id']}",
            "instruction": f"{r['t_school']} {r['t_grade']}학년 출제자({r['twin_label'] or '기출'})의 발문·선택지·기호 형식을 따른다. {r['twin_pattern'] or ''}".strip(),
            "input": {"track": "내신", "school": r["t_school"], "grade": r["t_grade"],
                      "exam": f"{r['t_year']} {r['t_semester']} {r['t_exam']}", "questionType": r["question_type"],
                      "evidence": {k: evidence.get(k, "") for k in ("past_exam", "school_print")},
                      "request": f"근거 자료로 '{r['question_type']}' 문항 1개를 만드십시오."},
            "source_chunks": [{"id": cid, "text": r["source_text"]}],
            "output": {"question": question, "evidence_refs": [{"id": cid, "quote": r["source_text"][:160]}],
                       "explanation": str((raw.get("canonical") or {}).get("explanation") or "")},
            "metadata": {"domain": "education", "deidentified": True,
                         "source_group": f"wiki:{r['t_school']}:{r['t_grade']}:{r['t_year']}:{r['t_semester']}:{r['t_exam']}",
                         "exam_date": date_, "teacher_verified": True, "training_allowed": True, "track": "naesin",
                         "verify_status": verify.get("status"), "teacher_action": teacher.get("action", "")[:200]},
        })
    conn.close()
    return rows, skipped


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--db", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--exam-dates", help='{"학교|연도|학기|시험": "YYYY-MM-DD"} JSON 파일')
    a = p.parse_args()
    overrides = json.loads(Path(a.exam_dates).read_text(encoding="utf-8")) if a.exam_dates else None
    rows, skipped = export(a.db, overrides)
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(json.dumps({"exported": len(rows), "skipped": skipped}, ensure_ascii=False))


if __name__ == "__main__":
    main()
