import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

import exam_build as build
from test_exam_check import balanced, mc, sa


class ConvertTest(unittest.TestCase):
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

    def test_given_note_with_book_title_and_view_marker_when_row_built_then_italic_kept_and_marker_is_text(self):
        # given: 해설에 책 제목 <i>와 밑줄 <u>, 유형 이름에 꺾쇠 글 <보기>가 있다
        question = mc(2, score=2)
        question['questionType'] = '그림 <보기> 빈칸'
        question['verify'] = {'status': '일치', 'note': '해설: <i>Fahrenheit 451</i> (1953)의 <u>firemen</u> & <보 기>'}
        # when
        row = build.to_explanation_row(question, 32)
        # then
        self.assertEqual('해설: <i>Fahrenheit 451</i> (1953)의 <u>firemen</u> &amp; &lt;보 기&gt;', row['expl'])
        self.assertTrue(row['meta'].startswith('유형: 그림 &lt;보기&gt; 빈칸 · 배점: 2점'))

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

    def test_given_missing_figure_when_main_runs_then_warning_and_asset_dir_fixes_it(self):
        # given: 그림 줄이 있는 시험. 그림은 저장소가 아니라 작업 폴더에만 있다
        exam = self.exam()
        exam['questions'][0]['stem'] += chr(10) + '[[img:fig-made-up.svg]]'
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, 'r12.json')
            path.write_text(json.dumps(exam, ensure_ascii=False), encoding='utf-8')
            assets = Path(tmp, 'figs')
            assets.mkdir()
            (assets / 'fig-made-up.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg"/>', encoding='utf-8')
            # when
            without, with_assets = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(without):
                build.main([str(path)])
            with contextlib.redirect_stdout(with_assets):
                build.main([str(path), '--asset-dir', str(assets)])
            html = Path(tmp, 'r12_문제지.html').read_text(encoding='utf-8')
        # then
        self.assertIn('그림 1개를 찾지 못했습니다', without.getvalue())
        self.assertNotIn('찾지 못했습니다', with_assets.getvalue())
        self.assertIn('<img src="data:image/svg+xml;base64,', html)

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
