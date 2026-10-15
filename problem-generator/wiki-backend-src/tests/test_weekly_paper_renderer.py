import re
import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import weekly_paper


def mc(no, prompt='다음 빈칸에 들어갈 말로 가장 적절한 것은?',
       passage='Modern street art comes in many different ______.',
       choices=('① forms', '② dots', '③ clues', '④ walls', '⑤ figures'),
       layout='row', section=None, passage_class='prose', group=None):
    return {'no': no, 'prompt': prompt, 'passage': passage, 'choices': list(choices),
            'layout': layout, 'section': section, 'passage_class': passage_class,
            'group': group}


class WeeklyPaperRendererTest(unittest.TestCase):
    def test_paper_keeps_round1_final_contract(self):
        # given: round-1 FINAL과 같은 9문항
        questions = [mc(i) for i in range(1, 10)]
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='2026학년도 2학기 중간고사 대비 4회',
            sub_row='신구중학교 2학년 · 영어 · 40문항 (5과)',
            run_header_left='신구중학교 2학년 · 2학기 중간고사 예상문제', run_header_right='영어',
            maker='제작 : 압구정에듀홀릭 내신 wiki', questions=questions)
        # then: A4/2단 CSS·헤더·문항 순서·개수가 유지된다
        for token in ('@page { size: A4 portrait', '.cols { display: flex',
                      'class="exam-header"', 'class="qblock"', 'class="choices row"',
                      'page-break-inside: avoid', 'class="page-footer"'):
            self.assertIn(token, out, token)
        self.assertEqual([str(i) for i in range(1, 10)],
                         re.findall(r'data-qnum="(\d+)"', out))
        self.assertNotIn('서답형', out)

    def test_forty_mixed_items_paginate_without_empty_pages(self):
        # given: 지문박스·세로선택지·밑줄을 섞은 40문항
        questions = []
        for i in range(1, 41):
            passage = 'Reading box line one.<br>Reading box line two with <u>used to live</u> here.' if i % 5 == 0 else 'Short ______ blank.'
            questions.append(mc(i, passage=passage, passage_class='box' if i % 5 == 0 else 'prose',
                                layout='vertical' if i % 4 == 0 else 'row'))
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
            maker='M', questions=questions)
        # then: 40문항 순서 유지, 빈 페이지 없음, 페이지 수 상식 범위
        self.assertEqual([str(i) for i in range(1, 41)],
                         re.findall(r'data-qnum="(\d+)"', out))
        pages = re.findall(r'<div class="page">', out)
        self.assertTrue(4 <= len(pages) <= 12, len(pages))
        self.assertNotIn('data-qnum=""', out)

    def test_grouped_items_stay_together_with_group_end_gap(self):
        # given: 지문 공유 그룹(Q1 지문박스 + Q2~Q4 윗글)
        box = 'Shared-box line one.<br>Second line here.'
        questions = [mc(1, passage=box, passage_class='box', group='gA'),
                     mc(2, passage='', group='gA'),
                     mc(3, passage='', group='gA'),
                     mc(4, passage='', group='gA'),
                     mc(5)]
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
            maker='M', questions=questions)
        # then: 그룹 마지막에만 group-end 여백, 박스는 1회만 출력
        self.assertEqual(1, out.count('Shared-box'))
        self.assertEqual(1, len(re.findall(r'class="qblock group-end"', out)))
        self.assertIn('class="qblock group-end" data-qnum="4"', out)

    def test_slack_is_spread_as_item_spacing(self):
        # given: 짧은 3문항
        questions = [mc(1, passage='Short ______ blank.'),
                     mc(2, passage='Short ______ blank.'),
                     mc(3, passage='Short ______ blank.')]
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
            maker='M', questions=questions)
        # then: 남는 공간이 --xgap으로 분배되고 2.2mm를 넘지 않는다
        gaps = [float(v) for v in re.findall(r'--xgap:([\d.]+)mm', out)]
        self.assertTrue(gaps)
        self.assertTrue(all(0 < v <= 2.2 for v in gaps), gaps)

    def test_seventy_items_share_the_same_renderer(self):
        # given: 70문항
        questions = [mc(i) for i in range(1, 71)]
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
            maker='M', questions=questions)
        # then: 70문항 전부, footer 총합 일치
        self.assertEqual(70, len(re.findall(r'data-qnum="(\d+)"', out)))
        total = len(re.findall(r'<div class="page">', out))
        self.assertTrue(all('/ %d</div>' % total in out for _ in [0]))

    def test_content_is_never_rewritten(self):
        # given: 밑줄·특수문자를 포함한 문항
        prompt = '다음 중 <u>used to</u>의 쓰임이 나머지와 다른 것은?'
        passage = 'There ______ a gym & success near "my" house.'
        # when: 문제지 렌더
        out = weekly_paper.render_student_paper(
            title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
            maker='M', questions=[mc(1, prompt=prompt, passage=passage)])
        # then: 입력 바이트가 그대로 실린다
        self.assertIn(prompt, out)
        self.assertIn(passage, out)

    def test_explanation_renders_quick_grid_and_rows(self):
        # given: 40행 해설 데이터
        rows = [{'no': i, 'answer': '①', 'expl': '근거 %d' % i, 'meta': '유형: 어휘'} for i in range(1, 41)]
        # when: 해설지 렌더
        out = weekly_paper.render_answer_explanation(
            title='정답 및 해설', subtitle='교사용',
            summary={'total': 40, 'mc': 40, 'sa': 0, 'multi': 0, 'verified': 40, 'review': 0},
            quick_answers=['①'] * 40, rows=rows)
        # then: 빠른 정답 8열 그리드(5행) + 40행 + 요약표
        self.assertEqual(40, len(re.findall(r'<div class="qrow">', out)))
        grid = out.split('<table class="quick">')[1].split('</table>')[0]
        self.assertEqual(5, grid.count('<tr>'))
        self.assertIn('<td>40</td><td>40</td><td>0</td>', out)

    def test_explanation_rejects_mismatched_inputs(self):
        # given: 길이가 다른 빠른 정답
        rows = [{'no': 1, 'answer': '①', 'expl': 'e', 'meta': 'm'}]
        # when/then: 길이 불일치와 빈 입력은 거부된다
        with self.assertRaises(ValueError):
            weekly_paper.render_answer_explanation(
                title='T', subtitle='S',
                summary={'total': 1, 'mc': 1, 'sa': 0, 'multi': 0, 'verified': 1, 'review': 0},
                quick_answers=[], rows=rows)
        with self.assertRaises(ValueError):
            weekly_paper.render_student_paper(
                title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
                maker='M', questions=[])


if __name__ == '__main__':
    unittest.main()
