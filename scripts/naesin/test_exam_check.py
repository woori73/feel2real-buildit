import contextlib
import io
import json
import random
import tempfile
import unittest
from pathlib import Path

import exam_check as check


def distinct_passage(no):
    """문항 번호마다 낱말이 전혀 다른 지문(서로 중복으로 잡히지 않게). 번호가 같으면 늘 같은 글."""
    rng = random.Random(no)
    return ' '.join(''.join(rng.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(rng.randint(3, 9)))
                    for _ in range(14))


def mc(answer, score=2, stem='다음 글의 주제로 가장 적절한 것은?', source=None, multi=False, no=0):
    """객관식 문항 하나. source를 주지 않으면 문항마다 다른 지문을 만든다."""
    return {
        'type': 'mc', 'questionType': '주제', 'stem': stem,
        'sourceText': source if source is not None else distinct_passage(no),
        'options': ['one', 'two', 'three', 'four', 'five'],
        'answer': answer if isinstance(answer, list) else [answer], 'multi': multi, 'score': score,
        'conditions': [], 'answerFormat': '', 'acceptedAnswers': [], 'sampleAnswer': '', 'gradingRule': '',
    }


def sa(score=5, accepted=('went',), no=0):
    q = mc(1, score=score, stem='빈칸에 알맞은 말을 쓰시오.', no=no)
    q.update({'type': 'sa', 'options': [], 'answer': [], 'acceptedAnswers': list(accepted)})
    return q


def balanced(count=40):
    """정답이 ①~⑤로 고르게 돌아가는 문항 묶음."""
    return [mc(i % 5 + 1, no=i) for i in range(count)]


class CountAndScoreTest(unittest.TestCase):
    def test_given_40_ordered_when_39_written_then_count_issue(self):
        # given
        questions = balanced(39)
        # when
        issues = check.check_count(questions, 40)
        # then
        self.assertEqual(['문항 수 39개: 주문은 40개'], issues)

    def test_given_no_order_count_when_checked_then_pass(self):
        # given
        questions = balanced(7)
        # when
        issues = check.check_count(questions, None)
        # then
        self.assertEqual([], issues)

    def test_given_scores_sum_98_when_total_100_then_total_issue(self):
        # given
        questions = balanced(40)
        questions[0]['score'] = 20  # 2점 39개 + 20점 = 98점
        # when
        issues = check.check_scores(questions, 100)
        # then
        self.assertEqual(['배점 합계 98점: 만점은 100점'], issues)

    def test_given_zero_score_when_checked_then_item_issue_before_total(self):
        # given
        questions = balanced(3)
        questions[1]['score'] = 0
        # when
        issues = check.check_scores(questions, 6)
        # then
        self.assertEqual(['2번: 배점이 0 이하이거나 숫자가 아님'], issues)

    def test_given_scores_sum_100_when_total_100_then_pass(self):
        # given
        questions = balanced(40) + []
        for q in questions[:10]:
            q['score'] = 4  # 4점 10개 + 2점 30개 = 100점
        # when
        issues = check.check_scores(questions, 100)
        # then
        self.assertEqual([], issues)


class AnswerBalanceTest(unittest.TestCase):
    def test_given_even_answers_when_checked_then_pass(self):
        # given
        questions = balanced(40)
        # when
        issues = check.check_answer_balance(questions)
        # then
        self.assertEqual([], issues)

    def test_given_answer_3_is_half_when_checked_then_too_many_and_too_few(self):
        # given: ③ 20개, ①②④ 각 6개, ⑤ 2개를 번갈아 놓는다
        pattern = [3, 1, 3, 2, 3, 4, 3, 1, 3, 2, 3, 4, 3, 1, 3, 2, 3, 4, 3, 1,
                   3, 2, 3, 4, 3, 1, 3, 2, 3, 4, 3, 1, 3, 2, 3, 4, 3, 5, 3, 5]
        questions = [mc(a, no=i) for i, a in enumerate(pattern)]
        # when
        issues = check.check_answer_balance(questions)
        # then
        self.assertEqual(['정답 ③가 20/40개(50%): 너무 많음', '정답 ⑤가 2/40개(5%): 너무 적음'], issues)

    def test_given_same_answer_three_in_a_row_when_checked_then_run_issue(self):
        # given
        questions = [mc(a, no=i) for i, a in enumerate([1, 2, 2, 2, 3])]
        # when
        issues = check.check_answer_balance(questions)
        # then
        self.assertEqual(['2번~4번: 정답 ②가 3번 연속'], issues)

    def test_given_two_in_a_row_and_few_items_when_checked_then_pass(self):
        # given: 2번 연속까지는 허용, 20문항 미만은 비율 검사를 하지 않는다
        questions = [mc(a, no=i) for i, a in enumerate([4, 4, 1, 4])]
        # when
        issues = check.check_answer_balance(questions)
        # then
        self.assertEqual([], issues)

    def test_given_short_answer_between_when_checked_then_run_skips_it(self):
        # given: 서답형과 "모두 고르기"는 정답 번호 통계에서 뺀다
        questions = [mc(5, no=0), mc(5, no=1), sa(no=2), mc([1, 2], multi=True, no=3), mc(5, no=4)]
        # when
        issues = check.check_answer_balance(questions)
        # then
        self.assertEqual(['1번~5번: 정답 ⑤가 3번 연속'], issues)


class PassageDuplicateTest(unittest.TestCase):
    PASSAGE = 'Minho went to the library after school and borrowed three books about space travel.'

    def test_given_same_passage_and_same_stem_when_checked_then_duplicate(self):
        # given
        questions = [mc(1, source=self.PASSAGE), mc(2, no=1), mc(3, source='<p>' + self.PASSAGE.upper() + '</p>')]
        # when
        issues = check.check_passage_duplicates(questions)
        # then
        self.assertEqual(['1번·3번: 지문과 발문이 모두 같음'], issues)

    def test_given_shared_passage_with_different_stems_when_checked_then_pass(self):
        # given: 공통 지문에 다른 발문을 붙이는 것은 정상이다
        questions = [mc(1, source=self.PASSAGE), mc(2, source=self.PASSAGE, stem='윗글의 내용과 일치하지 않는 것은?')]
        # when
        issues = check.check_passage_duplicates(questions)
        # then
        self.assertEqual([], issues)

    def test_given_one_word_changed_and_same_stem_when_checked_then_near_duplicate(self):
        # given
        questions = [mc(1, source=self.PASSAGE), mc(2, source=self.PASSAGE.replace('three', 'four'))]
        # when
        issues = check.check_passage_duplicates(questions)
        # then
        self.assertEqual(['1번·2번: 거의 같은 지문에 같은 발문'], issues)

    def test_given_short_identical_sources_when_checked_then_ignored(self):
        # given: 단어 한두 개짜리 지문은 비교하지 않는다
        questions = [mc(1, source='apple'), mc(2, source='apple')]
        # when
        issues = check.check_passage_duplicates(questions)
        # then
        self.assertEqual([], issues)


class FormatTest(unittest.TestCase):
    def test_given_valid_questions_when_checked_then_pass(self):
        # given
        questions = [mc(1), mc([2, 4], multi=True, no=1), sa(no=2)]
        # when
        issues = check.check_format(questions)
        # then
        self.assertEqual([], issues)

    def test_given_missing_contract_field_when_checked_then_named(self):
        # given
        question = mc(1)
        del question['gradingRule']
        # when
        issues = check.check_format([question])
        # then
        self.assertEqual(['1번: 빠진 항목 gradingRule'], issues)

    def test_given_score_before_stem_when_checked_then_rule_issue(self):
        # given
        question = mc(1, stem='[3점] 다음 글의 주제는?')
        # when
        issues = check.check_format([question])
        # then
        self.assertEqual(['1번: 발문 앞에 점수 표기(제작원칙 1-1)'], issues)

    def test_given_broken_options_and_answer_when_checked_then_each_reported(self):
        # given
        question = mc(6)
        question['options'] = ['one', 'one', '', 'four']
        # when
        issues = check.check_format([question])
        # then
        self.assertEqual(['1번: 선택지 4개(5개여야 함)', '1번: 빈 선택지', '1번: 같은 선택지가 두 번',
                          '1번: 정답 번호가 없거나 1~5 밖'], issues)

    def test_given_two_answers_without_multi_flag_when_checked_then_mismatch(self):
        # given
        question = mc([1, 3], multi=False)
        # when
        issues = check.check_format([question])
        # then
        self.assertEqual(['1번: 정답 개수와 "모두 고르기" 표시가 다름'], issues)

    def test_given_short_answer_without_answer_when_checked_then_issue(self):
        # given
        question = sa(accepted=())
        # when
        issues = check.check_format([question])
        # then
        self.assertEqual(['1번: 서답형 정답이 없음'], issues)


class WholeExamTest(unittest.TestCase):
    def exam(self):
        questions = balanced(40)
        for q in questions[:10]:
            q['score'] = 4
        return {'examTitle': '신구중 2학년 기말 1회', 'expectedCount': 40, 'totalScore': 100, 'questions': questions}

    def run_main(self, exam, *args):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, 'exam.json')
            path.write_text(json.dumps(exam, ensure_ascii=False), encoding='utf-8')
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = check.main([str(path), *args])
        return code, out.getvalue()

    def test_given_clean_exam_when_main_runs_then_exit_0_and_pass_report(self):
        # given
        exam = self.exam()
        # when
        code, text = self.run_main(exam)
        # then
        self.assertEqual(0, code)
        self.assertIn('검수: 신구중 2학년 기말 1회 (40문항, 100점)', text)
        self.assertIn('정답 분포: ① 8  ② 8  ③ 8  ④ 8  ⑤ 8', text)
        self.assertTrue(text.rstrip().endswith('결과: 통과'))

    def test_given_count_option_differs_when_main_runs_then_exit_1_and_json_lists_issue(self):
        # given
        exam = self.exam()
        # when
        code, text = self.run_main(exam, '--count', '70', '--json')
        # then
        self.assertEqual(1, code)
        self.assertEqual(['문항 수 40개: 주문은 70개'], json.loads(text)['문항 수'])


if __name__ == '__main__':
    unittest.main()
