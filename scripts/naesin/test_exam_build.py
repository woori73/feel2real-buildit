import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

import exam_build as build
from test_exam_check import balanced, mc, sa

PASSAGE = 'Minho went to the library after school and borrowed three books about space travel.'


class ChoiceLayoutTest(unittest.TestCase):
    def test_given_short_words_when_layout_then_row(self):
        # given
        options = ['get', 'take', 'choose', 'express', 'explain']
        # when
        layout = build.choice_layout(options)
        # then
        self.assertEqual('row', layout)

    def test_given_one_sentence_option_when_layout_then_vertical(self):
        # given: 태그는 글자 수에서 뺀다
        options = ['<u>get</u>', 'take', 'choose', 'express', 'Andy saw Suho swimming in the pool.']
        # when
        layout = build.choice_layout(options)
        # then
        self.assertEqual('vertical', layout)


class PassageGroupTest(unittest.TestCase):
    def test_given_neighbours_share_passage_when_grouped_then_same_name(self):
        # given: 2~3번이 같은 지문, 1·4번은 혼자
        questions = [mc(1, no=0), mc(1, source=PASSAGE), mc(2, source=PASSAGE, stem='윗글과 일치하는 것은?'), mc(3, no=3)]
        # when
        groups = build.passage_groups(questions)
        # then
        self.assertEqual(['', 'p2', 'p2', ''], groups)

    def test_given_same_passage_far_apart_when_grouped_then_not_grouped(self):
        # given: 같은 지문이 떨어져 있으면 묶지 않는다(공통 지문은 연속 배치가 원칙)
        questions = [mc(1, source=PASSAGE), mc(2, no=1), mc(3, source=PASSAGE)]
        # when
        groups = build.passage_groups(questions)
        # then
        self.assertEqual(['', '', ''], groups)


class ConvertTest(unittest.TestCase):
    def test_given_mc_question_when_converted_then_marks_added_and_content_kept(self):
        # given
        question = mc(3, stem='밑줄 친 <u>that</u>과 쓰임이 같은 것은?', source=PASSAGE)
        # when
        paper = build.to_paper_question(question, 7, 'p7')
        # then
        self.assertEqual(7, paper['no'])
        self.assertEqual('밑줄 친 <u>that</u>과 쓰임이 같은 것은?', paper['prompt'])
        self.assertEqual(PASSAGE, paper['passage'])
        self.assertEqual(['① one', '② two', '③ three', '④ four', '⑤ five'], paper['choices'])
        self.assertEqual(('row', 0, 'p7', 'p7'),
                         (paper['layout'], paper['answer_lines'], paper['group'], paper['common_passage_id']))

    def test_given_newlines_in_dialogue_when_converted_then_br_in_every_field(self):
        # given: 저장 계약은 대화·예문 줄바꿈을 개행 문자로 적는다
        question = mc(1, stem='짝지은 대화가 어색한 것은?\n(정답 1개)', source='A: Hi.\r\nB: Hello.')
        question['options'][0] = 'A: Thanks.\nB: Sure.'
        question['conditions'] = ['첫째 줄\n둘째 줄']
        # when
        paper = build.to_paper_question(question, 1)
        # then
        self.assertEqual(('짝지은 대화가 어색한 것은?<br>(정답 1개)', 'A: Hi.<br>B: Hello.',
                          '① A: Thanks.<br>B: Sure.', ['첫째 줄<br>둘째 줄']),
                         (paper['prompt'], paper['passage'], paper['choices'][0], paper['conditions']))

    def test_given_short_answer_when_converted_then_answer_space_and_no_choices(self):
        # given
        question = sa()
        question['conditions'] = ['과거형으로 쓸 것']
        # when
        paper = build.to_paper_question(question, 40)
        # then
        self.assertEqual(([], 2, ['과거형으로 쓸 것'], None),
                         (paper['choices'], paper['answer_lines'], paper['conditions'], paper['group']))
        self.assertNotIn('common_passage_id', paper)

    def test_given_answers_when_labelled_then_marks_or_text(self):
        # given
        single, multi, short = mc(3), mc([4, 2], multi=True), sa(accepted=('went', 'did go'))
        no_accepted = sa(accepted=())
        no_accepted['sampleAnswer'] = 'I went home.'
        # when
        labels = [build.answer_label(q) for q in (single, multi, short, no_accepted)]
        # then
        self.assertEqual(['③', '②, ④', 'went / did go', 'I went home.'], labels)

    def test_given_evidence_and_verify_when_row_built_then_explanation_and_meta(self):
        # given
        question = mc(2, score=4)
        question['verify'] = {'independent_answer': '②', 'status': '일치', 'note': '해설: two가 맞다'}
        question['evidence'] = {'past_exam': '2학기 중간 8번 형식', 'school_print': '필기본 15쪽', 'changed': ''}
        # when
        row = build.to_explanation_row(question, 5)
        # then
        self.assertEqual({'no': 5, 'answer': '②', 'expl': '해설: two가 맞다',
                          'meta': '유형: 주제 · 배점: 4점 · 기출 근거: 2학기 중간 8번 형식 · 범위 근거: 필기본 15쪽 · 독립 검증: 일치'},
                         row)

    def test_given_mixed_questions_when_summarized_then_only_matched_are_verified(self):
        # given
        questions = [mc(1), mc([1, 2], multi=True, no=1), sa(no=2)]
        questions[0]['verify'] = {'status': '일치'}
        questions[1]['verify'] = {'status': '복수정답 의심'}
        # when
        summary = build.summarize(questions)
        # then
        self.assertEqual({'total': 3, 'mc': 2, 'sa': 1, 'multi': 1, 'verified': 1, 'review': 2}, summary)


class BuildTest(unittest.TestCase):
    def exam(self):
        questions = balanced(40)
        for q in questions[:10]:
            q['score'] = 4
        return {'examTitle': '신구중 2학년 기말 대비 12회', 'subTitle': '영어 · 40문항',
                'expectedCount': 40, 'totalScore': 100, 'questions': questions}

    def run_main(self, exam):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, 'r12.json')
            path.write_text(json.dumps(exam, ensure_ascii=False), encoding='utf-8')
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = build.main([str(path)])
            files = {p.name: p.read_text(encoding='utf-8') for p in Path(tmp).glob('*.html')}
        return code, out.getvalue(), files

    def test_given_clean_exam_when_built_then_paper_has_40_items_in_order_and_maker(self):
        # given
        exam = self.exam()
        # when
        paper, explanation = build.build(exam)
        # then
        self.assertEqual([str(i) for i in range(1, 41)], re.findall(r'data-qnum="(\d+)"', paper))
        self.assertIn('신구중 2학년 기말 대비 12회', paper)
        self.assertIn('제작 : 압구정에듀홀릭 내신 wiki', paper)
        self.assertEqual(40, len(re.findall(r'<div class="qrow">', explanation)))

    def test_given_clean_exam_when_main_runs_then_two_files_written(self):
        # given
        exam = self.exam()
        # when
        code, text, files = self.run_main(exam)
        # then
        self.assertEqual(0, code)
        self.assertEqual(['r12_문제지.html', 'r12_정답해설.html'], sorted(files))
        self.assertIn('결과: 통과', text)

    def test_given_exam_with_issue_when_main_runs_then_no_file_and_exit_1(self):
        # given: 배점 합계가 틀린 시험
        exam = self.exam()
        exam['totalScore'] = 90
        # when
        code, text, files = self.run_main(exam)
        # then
        self.assertEqual(1, code)
        self.assertEqual({}, files)
        self.assertIn('배점 합계 100점: 만점은 90점', text)
        self.assertIn('시험지를 만들지 않았습니다', text)


if __name__ == '__main__':
    unittest.main()
