"""입시상담(counsel) 데이터 계약·채점·데이터 만들기 테스트. 합성 자료만 쓴다(GPU·모델·운영 DB 없음)."""
import unittest

from training.admissions.build_dataset import Builder, build, item_name, make_schedule, minimum_fact
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
                for k in ["R2028-NAESIN", "R2028-CSAT", "R2028-SCHEDULE", "R2028-COHORT", "R2028-SCALE", "R2028-CREDIT",
                          "R2028-ENGLISH-AI"]]
    return {"names": names, "docs": {"d": "모집요강"}, "schedules": schedules, "types": types, "official": official,
            "youtube": [{"id": "YT-1", "title": "해석", "content": "공식 발표가 아닌 해석이다."}]}


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
        ev1, tr1 = build(data, eval_universities=2)
        ev2, tr2 = build(seed_data(), eval_universities=2)
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


if __name__ == "__main__":
    unittest.main()
