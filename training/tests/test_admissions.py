"""입시상담(counsel) 데이터 계약·채점·데이터 만들기 테스트. 합성 자료만 쓴다(GPU·모델·운영 DB 없음)."""
import unittest

from training.admissions.build_dataset import (HELD_OUT_2028, Builder, build, build_eval_2028, item_name, make_official_train, make_schedule,
                                               make_youtube_analysis, minimum_fact, polite)
from training.admissions.counsel_eval import date_pairs, evaluate, has_fact, score_row
from training.common import check_split, messages_for, validate_record


def counsel(rid="c1", group="U1", gold=None, output=None, training=False):
    row = {"id": rid, "task": "counsel", "instruction": "입시상담 답변을 작성하십시오.",
           "input": "가나대 수시 학생부교과 원서접수 언제예요?",
           "source_chunks": [{"id": "s1", "text": "가나대학교 2027학년도 수시 학생부교과 일정 — 원서접수: 2026.9.8.(화) ~ 9.10.(목)"},
                             {"id": "s2", "text": "가나대학교 2027학년도 수시 학생부교과 / 모집인원 120명"}],
           "metadata": {"domain": "admissions", "deidentified": True, "source_group": group, "exam_date": "2026-11-22",
                        "category": "schedule", "gold_basis": "approved_record", "human_checked": False,
                        "training_allowed": training}}
    if gold is not None:
        row["gold"] = gold
    if output is not None:
        row["output"] = output
    return row


GOLD = {"facts": ["2026.9.8.(화) ~ 9.10.(목)"], "cite": ["s1"], "abstain": False}


def seed_data():
    """대학 6곳(각 일정 5개·전형 4개)과 2028 지식 항목으로 된 작은 합성 시드."""
    names = {f"U{i}": f"대학{i}대학교" for i in range(6)}
    schedules, types = [], []
    for code in names:
        for n, item in enumerate(["원서접수", "면접고사", "최초합격자 발표", "서류제출", "논술고사"]):
            schedules.append({"university_code": code, "academic_year": 2027, "round_type": "SUSI", "type_name": "일반전형",
                              "item": item, "date": f"2026.{9 + n % 3}.{3 + n * 4}.(월)", "source_document_key": "d"})
        for n in range(4):
            types.append({"university_code": code, "academic_year": 2027, "round_type": "SUSI", "type_name": f"전형{n}",
                          "recruitment_count": 30 + n * 11, "student_record_method": "학생부 100%",
                          "min_academic_standard": "3개 영역 등급 합 7 이내" if n == 0 else None, "source_document_key": "d"})
    official = [{"id": k, "title": k, "content": "2028학년도부터 내신은 5등급제로 바뀐다. 1등급은 10%다."}
                for k in ["R2028-NAESIN", "R2028-CSAT", "R2028-SCHEDULE", "R2028-COHORT", "R2028-SCALE", "R2028-CREDIT", "R2028-ENGLISH-AI"]]
    youtube = [{"id": "RV-1", "title": "해석", "content": "공식 발표가 아닌 해석이다."},
               {"id": "YT-A1", "title": "[전문가 해석] 가나채널: 통합수능은 쉬워진다.",
                "content": "분류: 전문가 해석\n주장: 통합수능은 쉬워진다.\n주의: 단정하지 말 것.\n공식 자료 대조: CONFLICT — 메모"}]
    return {"names": names, "docs": {"d": "모집요강"}, "schedules": schedules, "types": types, "official": official,
            "youtube": youtube}


class CounselContractTests(unittest.TestCase):
    def test_eval_row_with_gold_is_valid(self):
        # given: 승인 자료에서 뽑은 정답 사실과 근거 id가 있는 평가 행
        row = counsel(gold=GOLD)
        # when / then: 평가 계약 검사를 통과한다
        validate_record(row, evaluation=True)

    def test_eval_row_rejects_cite_outside_chunks(self):
        # given: 받은 자료에 없는 id(s9)를 정답 근거로 적은 평가 행
        row = counsel(gold={**GOLD, "cite": ["s9"]})
        # when / then: 근거 id 불일치로 거부된다
        with self.assertRaises(ValueError):
            validate_record(row, evaluation=True)

    def test_answerable_row_needs_fact_unless_no_guarantee(self):
        # given: 사실 없이 답할 수 있다고 표시한 행과, 합격 단정 금지 문항
        empty = counsel(gold={"facts": [], "cite": [], "abstain": False})
        guarantee = counsel(gold={"facts": [], "cite": ["s2"], "abstain": False, "no_guarantee": True})
        # when / then: 앞의 것은 거부되고 뒤의 것은 통과한다
        with self.assertRaises(ValueError):
            validate_record(empty, evaluation=True)
        validate_record(guarantee, evaluation=True)

    def test_training_row_requires_permission_and_output(self):
        # given: 학습 허용 표시가 없는 행
        row = counsel(output="원서접수는 9월 8일부터입니다 [s1].")
        # when / then: 학습 계약에서 거부되고, 허용하면 통과한다
        with self.assertRaises(ValueError):
            validate_record(row, training=True)
        validate_record(counsel(output="원서접수는 9월 8일부터입니다 [s1].", training=True), training=True)

    def test_unapproved_gold_basis_is_rejected(self):
        # given: 승인 자료도 사람 확인도 아닌 출처의 학습 행
        row = counsel(output="답", training=True)
        row["metadata"]["gold_basis"] = "model_output"
        # when / then: 거부된다
        with self.assertRaises(ValueError):
            validate_record(row, training=True)

    def test_messages_hold_question_and_chunks_but_not_answer(self):
        # given: 모범 답변이 있는 학습 행
        row = counsel(output="비밀 모범 답변", training=True)
        # when: 모델 입력 메시지를 만든다
        messages = messages_for(row)
        # then: 시스템 프롬프트 + 질문·자료가 들어가고 모범 답변은 들어가지 않는다
        self.assertEqual([m["role"] for m in messages], ["system", "user"])
        self.assertIn("[s1]", messages[1]["content"])
        self.assertIn("원서접수 언제예요", messages[1]["content"])
        self.assertNotIn("비밀 모범 답변", "".join(m["content"] for m in messages))

    def test_split_rejects_same_university_in_train_and_eval(self):
        # given: 같은 대학(U1)이 학습과 평가에 모두 있음
        train = [counsel("t1", "U1", output="답 [s1]", training=True)]
        evaluation = [counsel("e1", "U1", gold=GOLD)]
        # when / then: 누수로 거부되고, 다른 대학이면 통과한다
        with self.assertRaises(ValueError):
            check_split(train, evaluation, "2026-11-22")
        check_split([counsel("t1", "U2", output="답 [s1]", training=True)], evaluation, "2026-11-22")


class CounselScoringTests(unittest.TestCase):
    def test_date_written_differently_still_counts(self):
        # given: 자료는 '2026.9.8.(화) ~ 9.10.(목)', 답은 '9월 8일 ~ 9월 10일'
        answer = "원서접수는 9월 8일부터 9월 10일까지입니다 [s1]."
        # when / then: 월·일 쌍이 같아 정답으로 본다
        self.assertEqual(date_pairs(GOLD["facts"][0]), {(9, 8), (9, 10)})
        self.assertTrue(has_fact(answer, GOLD["facts"][0]))
        self.assertFalse(has_fact("원서접수는 9월 8일입니다.", GOLD["facts"][0]))

    def test_alternative_wordings_count_as_same_fact(self):
        # given: 대안 표현을 '|'로 적은 정답 사실
        fact = "모두|둘 다|두 과목"
        # when / then: 하나만 있어도 맞고, 하나도 없으면 틀린다
        self.assertTrue(has_fact("통합사회와 통합과학 둘 다 응시해야 합니다.", fact))
        self.assertTrue(has_fact("두 과목을 모두 봅니다.", fact))
        self.assertFalse(has_fact("사회만 보면 됩니다.", fact))

    def test_correct_cited_answer_scores_fully(self):
        # given: 사실과 근거를 모두 맞힌 답
        pred = {"id": "c1", "answer": "원서접수는 2026.9.8.(화) ~ 9.10.(목)입니다 [s1]."}
        # when
        row = score_row(counsel(gold=GOLD), pred)
        # then
        self.assertTrue(row["facts_ok"])
        self.assertTrue(row["citation_ok"])
        self.assertEqual(row["fabricated"], [])

    def test_invented_number_is_counted(self):
        # given: 자료에 없는 모집인원(250명)을 지어낸 답
        pred = {"id": "c1", "answer": "원서접수는 9월 8일~9월 10일이고 모집인원은 250명입니다 [s1]."}
        # when
        row = score_row(counsel(gold=GOLD), pred)
        # then: 날짜 숫자는 허용되고 250만 지어낸 숫자로 센다
        self.assertEqual(row["fabricated"], ["250"])

    def test_no_data_question_rewards_abstain(self):
        # given: 자료 없음이 정답인 문항과 두 답
        gold_row = counsel(gold={"facts": [], "cite": [], "abstain": True})
        good = {"id": "c1", "answer": "받은 자료에서 기숙사 일정은 확인되지 않습니다. 입학처에 확인해 주세요."}
        bad = {"id": "c1", "answer": "기숙사 신청은 2월 15일입니다."}
        # when / then
        self.assertTrue(score_row(gold_row, good)["abstain_correct"])
        self.assertFalse(score_row(gold_row, bad)["abstain_correct"])

    def test_guarantee_question_needs_hedge_without_promise(self):
        # given: 합격 단정을 요구하는 문항
        gold_row = counsel(gold={"facts": [], "cite": ["s2"], "abstain": False, "no_guarantee": True})
        # when / then: 단정하면 실패, '단정할 수 없다'고 하면 통과
        self.assertFalse(score_row(gold_row, {"answer": "충분히 합격할 수 있습니다."})["guarantee_ok"])
        self.assertTrue(score_row(gold_row, {"answer": "합격 여부는 단정할 수 없습니다 [s2]."})["guarantee_ok"])

    def test_guarantee_hedge_wordings_used_by_service_and_model(self):
        # given: 합격 단정 요구 문항과 실제 운영·학습 모델이 쓴 단정하지 않는 표현
        gold_row = counsel(gold={"facts": [], "cite": ["s2"], "abstain": False, "no_guarantee": True})
        hedges = ["정시로 무조건 붙는다고 단정해서 말씀드리지 않겠습니다.",
                  "내신만으로 합격을 확실히 말할 수 없습니다.",
                  "문의한 성적으로 확실히 합격한다고 말할 수 없습니다.",
                  "합격 가능성은 판단해 드릴 수 없습니다.",
                  "아니요, 확실하지 않아요."]
        # when / then: 모두 통과하고, 단정한 답은 여전히 실패
        for answer in hedges:
            self.assertTrue(score_row(gold_row, {"answer": answer})["guarantee_ok"], answer)
        self.assertFalse(score_row(gold_row, {"answer": "확실히 합격할 수 있습니다."})["guarantee_ok"])

    def test_negated_guarantee_wording_is_not_a_guarantee(self):
        # given: 단정 표현이 들어 있지만 같은 문장에서 부정한 답(2026-11-24 운영 e28-gua-001·002)
        gold_row = counsel(gold={"facts": [], "cite": ["s2"], "abstain": False, "no_guarantee": True})
        negated = ["아니요, 다 들어도 합격이 확실하지 않습니다.",
                   "정시로 무조건 합격한다는 보장도 근거에 없습니다. 대학에 따라 달라 장담할 수 없습니다."]
        # when / then: 부정문은 통과, 실제 단정은 여전히 실패
        for answer in negated:
            self.assertTrue(score_row(gold_row, {"answer": answer})["guarantee_ok"], answer)
        self.assertFalse(score_row(gold_row, {"answer": "내신 2등급이면 합격이 확실합니다. 단정할 수 없는 부분도 있지만요."})["guarantee_ok"])

    def test_date_with_weekday_word_counts(self):
        # given: 자료는 '2027. 1. 23.(토)', 답은 요일을 글자로 쓴 '2027. 1. 23. 토요일'
        fact = "2027. 1. 23.(토) 11:00 ~ 15:00"
        # when / then
        self.assertTrue(has_fact("실기고사는 2027. 1. 23. 토요일 11시부터 15시까지입니다.", fact))
        self.assertFalse(has_fact("실기고사는 2027. 1. 24. 일요일입니다.", fact))

    def test_minimum_fact_accepts_particle_wording(self):
        # given: 수능최저 정답 '합 4'에 조사를 붙인 대안 표현
        fact = "합 4|합이 4|합은 4"
        # when / then
        self.assertTrue(has_fact("3개 영역 등급의 합이 4 이내여야 합니다.", fact))
        self.assertTrue(has_fact("등급 합 4 이내", fact))
        self.assertFalse(has_fact("등급의 합이 5 이내여야 합니다.", fact))

    def test_evaluate_reports_metrics_and_missing(self):
        # given: 평가 문항 2개, 예측 1개
        gold_rows = [counsel("c1", gold=GOLD), counsel("c2", gold={"facts": [], "cite": [], "abstain": True})]
        preds = [{"id": "c1", "answer": "2026.9.8.(화) ~ 9.10.(목) [s1]", "latency_seconds": 2.0}]
        # when
        result = evaluate(gold_rows, preds)
        # then
        self.assertEqual(result["missing_predictions"], 1)
        self.assertEqual(result["fact_accuracy"], 1.0)
        self.assertEqual(result["abstain_correct"], 0.0)
        self.assertEqual(result["avg_latency_seconds"], 2.0)

    def test_no_citation_mode_leaves_citation_metric_empty(self):
        # given: 인용 형식을 쓰지 않는 운영 서비스 답
        result = evaluate([counsel(gold=GOLD)], [{"id": "c1", "answer": "9월 8일~9월 10일입니다."}], citations=False)
        # when / then
        self.assertIsNone(result["citation_match"])
        self.assertEqual(result["fact_accuracy"], 1.0)


class BuildDatasetTests(unittest.TestCase):
    def test_item_name_and_minimum_parse(self):
        # given / when / then
        self.assertEqual(item_name("최초합격자 발표(본교 홈페이지)"), "최초합격자 발표")
        self.assertEqual(minimum_fact("4개 영역 중 3개 영역 등급 합 7 이내"), ("3", "7"))
        self.assertIsNone(minimum_fact("없음"))

    def test_polite_turns_written_endings_into_counselor_speech(self):
        # given: 자료 문장(-다.)
        text = "9등급으로 나눈다(내신과 다르다). 학점을 받는다. 1등급은 상위 10%다. 발표했다."
        # when
        out = polite(text)
        # then: 존댓말로 바뀌고 숫자는 그대로다
        self.assertEqual(out, "9등급으로 나눕니다(내신과 다릅니다). 학점을 받습니다. 1등급은 상위 10%입니다. 발표했습니다.")

    def test_official_train_row_quotes_fact_sentence_or_fails(self):
        # given: 본문에 '10%'가 있는 2028 항목
        data = seed_data()
        b = Builder(data, __import__("random").Random(1))
        # when
        r = make_official_train(b, "R2028-SCALE", "1등급은 몇 %예요?", ["10%"], "x")
        # then: 사실 문장을 존댓말로 인용하고 근거 id를 붙인다. 본문에 없는 사실이면 만들지 않는다
        self.assertIn("1등급은 10%입니다.", r["output"])
        self.assertIn(f"[{r['gold']['cite'][0]}]", r["output"])
        with self.assertRaises(ValueError):
            make_official_train(b, "R2028-SCALE", "질문", ["99%"], "y")

    def test_youtube_analysis_keeps_internal_notes_out_and_flags_conflict(self):
        # given: 공식 발표와 어긋난다고 표시된 유튜브 분석 항목
        data = seed_data()
        b = Builder(data, __import__("random").Random(1))
        # when
        r = make_youtube_analysis(b, data["youtube"][1], "x")
        # then: 채널 해석임을 밝히고, 어긋남을 알리며, 내부 메모(주의·CONFLICT)는 답에 없다
        self.assertIn("가나채널", r["output"])
        self.assertIn("공식 발표와 맞지 않는", r["output"])
        self.assertNotIn("CONFLICT", r["output"])
        self.assertNotIn("말 것", r["output"])

    def test_trap_date_is_outside_the_real_range(self):
        # given: 9월 7일~9월 9일 원서접수
        data = seed_data()
        s = {**data["schedules"][0], "date": "2026.9.7.(월) ~ 9.9.(수)"}
        b = Builder(data, __import__("random").Random(1))
        # when
        r = make_schedule(b, s, "x", "eval", trap=True)
        # then: 질문의 틀린 날짜는 7~9일 밖이고, 정답은 자료 날짜다
        wrong_day = int(r["input"].split("월 ")[1].split("일")[0])
        self.assertFalse(7 <= wrong_day <= 9)
        self.assertEqual(r["gold"]["facts"], ["2026.9.7.(월) ~ 9.9.(수)"])

    def test_build_is_valid_split_and_deterministic(self):
        # given: 합성 시드
        data = seed_data()
        # when: 두 번 만든다(평가 대학 2곳)
        ev1, tr1 = build(data, eval_universities=2, train_qa=[])
        ev2, tr2 = build(seed_data(), eval_universities=2, train_qa=[])
        # then: 계약·누수 검사를 통과하고, 결과가 같으며, 학습 템플릿 답은 자기 정답에서 만점이다
        check_split(tr1, ev1, "2026-11-22")
        self.assertEqual([r["input"] for r in ev1], [r["input"] for r in ev2])
        self.assertEqual([r.get("output") for r in tr1], [r.get("output") for r in tr2])
        self.assertTrue(all("output" not in r for r in ev1))
        graded = [r for r in tr1 if "gold" in r]
        result = evaluate(graded, [{"id": r["id"], "answer": r["output"]} for r in graded])
        self.assertEqual(result["fabricated_numbers_total"], 0)
        self.assertEqual(result["fact_accuracy"], 1.0)
        self.assertEqual(result["citation_match"], 1.0)


class Eval2028Tests(unittest.TestCase):
    def data(self):
        d = seed_data()
        extra = [{"id": k, "title": k, "content": "통합사회와 통합과학은 점수가 과목별로 따로 나온다. 확정된 제도가 아니다."}
                 for k in ["R2028-CSAT-SCORE", "R2028-RECOMMENDED-STATS", "R2029-BASICS", "R2028-NATIONAL-PLAN"]]
        d["official"] = d["official"] + extra
        return d

    def test_eval_2028_builds_fact_no_data_and_guarantee_rows(self):
        # given: 2028 항목이 있는 합성 시드와 문항 목록
        data = self.data()
        ev, _ = build(data, eval_universities=2, train_qa=[])
        qa = [("R2028-CSAT-SCORE", "점수가 합쳐 나오나요?", ["과목별|따로"], "reform_trap")]
        # when
        rows = build_eval_2028(data, ev, qa=qa, no_data=["2028 수능 응시료는요?"], guarantee=[("R2028-NAESIN", "확실히 붙죠?")])
        # then: 계약을 통과하고, 유형별 행이 만들어지며, 정답 근거가 받은 자료 안에 있다
        for r in rows:
            validate_record(r, evaluation=True)
        cats = {r["metadata"]["category"] for r in rows}
        self.assertTrue({"reform_trap", "reform_no_data", "reform_guarantee"} <= cats)
        trap = next(r for r in rows if r["metadata"]["category"] == "reform_trap")
        self.assertTrue(set(trap["gold"]["cite"]) <= {c["id"] for c in trap["source_chunks"]})

    def test_eval_2028_rejects_fact_missing_from_item(self):
        # given: 본문에 없는 정답 사실
        data = self.data()
        ev, _ = build(data, eval_universities=2, train_qa=[])
        # when / then
        with self.assertRaises(ValueError):
            build_eval_2028(data, ev, qa=[("R2028-CSAT-SCORE", "질문", ["99점"], "reform_fact")], no_data=[], guarantee=[])

    def test_training_never_contains_held_out_2028_items(self):
        # given: 보류(평가용) 2028 항목 텍스트
        data = self.data()
        held = [k["title"] for k in data["official"] if k["id"] in HELD_OUT_2028]
        # when
        _, tr = build(data, eval_universities=2, train_qa=[])
        # then: 학습 행의 받은 자료 어디에도 보류 항목이 없다
        texts = " ".join(c["text"] for r in tr for c in r["source_chunks"])
        for title in held:
            self.assertNotIn(f"— {title}:", texts)


if __name__ == "__main__":
    unittest.main()
