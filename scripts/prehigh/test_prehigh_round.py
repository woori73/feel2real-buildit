# -*- coding: utf-8 -*-
"""prehigh_round.py 단위 테스트. 실행: python -m unittest discover -s scripts/prehigh -p "test_*.py" """
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import prehigh_round as lib  # noqa: E402


def question(number=18, **over):
    base = {'number': number, 'stem': '다음 글의 목적으로 가장 적절한 것은?', 'answer': 2, 'passageKey': None,
            'blocks': [['p', 'We ask you to join the Product Team. It is on the fifth floor.']],
            'choices': ['가', '나', '다', '라', '마'], 'evidence': ['join the Product Team'],
            'explanation': '정답 ② · 글의 목적 (2점)'}
    base.update(over)
    return base


def round_data():
    marks = [{'part': 'question', 'kind': 'evidence', 'x': 1, 'y': 1, 'width': 10, 'height': 10},
             {'part': 'question', 'kind': 'answer', 'x': 1, 'y': 20, 'width': 10, 'height': 10}]
    reading = [{'image': f'q{18 + i}.webp', 'passage': None, 'answer': 1, 'explanation': '정답 ① · 유형', 'marks': list(marks)}
               for i in range(28)]
    words = [{'text': f'w{i}', 'choices': ['a', 'b', 'c', 'd', 'e'], 'answer': 3} for i in range(50)]
    return {'number': 1, 'source': '내부 기록', 'reading': reading, 'words': words}


class InlineTextTest(unittest.TestCase):
    def test_smart_converts_quotes_outside_tags(self):
        # given
        text = '<u class="x">it\'s</u> "fine"'
        # when
        result = lib.smart(text)
        # then
        self.assertEqual(result, '<u class="x">it’s</u> “fine”')

    def test_inline_escapes_ampersand_and_draws_blank(self):
        # given
        text = 'Date & Time ( ① ) is <blank/>.'
        # when
        result = lib.inline(text)
        # then
        self.assertIn('Date &amp; Time', result)
        self.assertIn('<span class="nw">( ① )</span>', result)
        self.assertIn('<span class="blank"></span>', result)

    def test_stem_underlines_negative_word_and_keeps_points_together(self):
        # given
        stem = '다음 글의 밑줄 친 부분 중, 어법상 틀린 것은? [3점]'
        # when
        result = lib.stem_html(29, stem)
        # then
        self.assertIn('<b class="qn">29.</b>', result)
        self.assertIn('<u>틀린</u> 것은?', result)
        self.assertIn('<span class="nw">[3점]</span>', result)

    def test_stem_underlines_quoted_english_phrase(self):
        # given
        stem = "밑줄 친 tomorrow's dollar arrives already shrunken이 다음 글에서 의미하는 바로 가장 적절한 것은?"
        # when
        result = lib.stem_html(21, stem)
        # then
        self.assertIn('<u>tomorrow’s dollar arrives already shrunken</u>이 다음 글에서', result)

    def test_stem_without_negative_word_has_no_underline(self):
        # given / when
        result = lib.stem_html(18, '다음 글의 목적으로 가장 적절한 것은?')
        # then
        self.assertNotIn('<u>', result)


class ChoiceTest(unittest.TestCase):
    def test_choice_layout_follows_choice_shape(self):
        # given
        cases = {
            'ab': ['divided … manageable'] * 5,
            'order': ['(A) - (C) - (B)', '(B) - (A) - (C)', '(B) - (C) - (A)', '(C) - (A) - (B)', '(C) - (B) - (A)'],
            'row5': ['(a)', '(b)', '(c)', '(d)', '(e)'],
            'row3': ['anxious → assured', 'exposure', 'resistance', 'comparison', 'delegation'],
            'row2': ['anxious → assured', 'delighted → regretful', 'indifferent → thrilled', 'hopeful → frustrated', 'relieved → annoyed'],
            'col': ['a quick decision can be wiser', '나', '다', '라', '마'],
        }
        for expected, choices in cases.items():
            # when / then
            self.assertEqual(lib.choice_layout(choices), expected)

    def test_choices_html_marks_only_the_answer(self):
        # given / when
        column = lib.choices_html(['가', '나', '다', '라', '마'], 4, 'answer:18')
        table = lib.choices_html(['a … b'] * 5, 1, 'answer:40')
        # then
        self.assertEqual(column.count('data-m="answer:18"'), 1)
        self.assertIn('<div class="choice" data-m="answer:18">④ 라</div>', column)
        self.assertIn('<tr data-m="answer:40"><td>①</td>', table)
        self.assertEqual(lib.choices_html(None, 1, 'answer:25'), '')

    def test_mark_inline_answer_picks_insert_slot_underline_or_circle(self):
        # given
        slot = lib.inline('A. ( ③ ) B. ( ④ ) C.')
        underline = '① <u>placed</u> and ③ <u>were</u>'
        circle = '① One. ⑤ Five.'
        # when / then
        self.assertIn('<span class="nw"><span data-m="answer:38">( ④ )</span></span>', lib.mark_inline_answer(slot, 4, 'answer:38'))
        self.assertEqual(lib.mark_inline_answer(underline, 3, 'answer:29'), '① <u>placed</u> and ③ <u data-m="answer:29">were</u>')
        self.assertEqual(lib.mark_inline_answer(circle, 5, 'answer:25'), '① One. <span data-m="answer:25">⑤</span> Five.')

    def test_passage_marks_the_underlined_word_of_letter_questions(self):
        # given
        passage = {'head': '[41~42] 다음 글을 읽고, 물음에 답하시오.', 'blocks': [['p', 'feels (a) <u>bad</u> and (d) <u>good</u>.']]}
        questions = [question(41, passageKey='41-42', blocks=[]),
                     question(42, passageKey='41-42', blocks=[], answer=4, choices=['(a)', '(b)', '(c)', '(d)', '(e)'])]
        # when
        section = lib.passage_section('41-42', passage, questions)
        # then
        self.assertIn('id="p41-42" data-part="passage"', section)
        self.assertIn('<span data-m="answer:42">(d) <u>good</u></span>', section)
        self.assertNotIn('answer:41', section)
        self.assertNotIn('data-m', lib.passage_section('41-42', passage, questions, marks=False))


class BlockTest(unittest.TestCase):
    def test_blocks_html_renders_every_block_kind(self):
        # given
        blocks = [['p', 'One.'], ['letter', 'Dear A,\n' + 'x' * 70], ['box', 'Given.'], ['lp', '(A)', 'Next.'],
                  ['lpb', '(B)', 'Long.'], ['fn', '* a: 가  ** b: 나'], ['boxp', 'Body.', '* c: 다'],
                  ['summary', 'It is <b>(A)</b> and <b>(B)</b>.'],
                  ['chart', {'title': ['T'], 'legend': ['2020', '2026'], 'rows': [['Desktop computer', 30, 12], ['Tablet', 8, 68]]}],
                  ['notice', {'title': 'Camp', 'lines': [['lead', 'Hi'], ['item', 'Fee: $15'], ['head', 'Schedule'],
                                                         ['trow', '1 p.m. – 2 p.m.: Coding'], ['sub', '· Note']]}]]
        # when
        result = lib.blocks_html(blocks)
        # then
        for part in ('<p class="pt">One.</p>', '<p class="line">Dear A,</p>', '<div class="given"><p class="pt">Given.</p></div>',
                     '<span class="lab">(A)</span>', '<div class="plabel">(B)</div>', '<span class="fi">** b: 나</span>',
                     '<span class="ab">(A)</span>', '<svg class="chart"', '>80</text>', '<b>Fee:</b> $15',
                     '<td class="n-time">1 p.m. – 2 p.m.</td><td>Coding</td>'):
            self.assertIn(part, result)

    def test_blocks_html_rejects_unknown_kind(self):
        # given / when / then
        with self.assertRaises(ValueError):
            lib.blocks_html([['video', 'x']])


class EvidenceTest(unittest.TestCase):
    def test_wrap_evidence_wraps_the_quoted_words(self):
        # given
        section = '<section><p class="pt">We ask you to join the team today.</p></section>'
        # when
        result = lib.wrap_evidence(section, 'join the team', 'evidence:18:0')
        # then
        self.assertEqual(result, '<section><p class="pt">We ask you to <span data-m="evidence:18:0">join the team</span> today.</p></section>')

    def test_wrap_evidence_crosses_underline_circle_and_quote_marks(self):
        # given
        section = '<p>their first birthdays ③ <u>were</u> similar to it. He said, “no trouble,” and left our (d) <u>resentment</u>.</p>'
        # when
        crossing = lib.wrap_evidence(section, 'birthdays were similar', 'evidence:29:0')
        quoted = lib.wrap_evidence(section, 'said, "no trouble," and', 'evidence:30:1')
        letter = lib.wrap_evidence(section, 'left our resentment.', 'evidence:42:0')
        # then
        self.assertIn('<span data-m="evidence:29:0">birthdays ③ </span><u><span data-m="evidence:29:0">were</span></u>'
                      '<span data-m="evidence:29:0"> similar</span> to it.', crossing)
        self.assertIn('<span data-m="evidence:30:1">said, “no trouble,” and</span>', quoted)
        self.assertIn('<span data-m="evidence:42:0">left our (d) </span><u><span data-m="evidence:42:0">resentment</span></u>', letter)

    def test_wrap_evidence_returns_none_when_missing_and_does_not_join_blocks(self):
        # given
        section = '<p>ends here</p><p>starts there</p><svg><text>inside chart</text></svg>'
        # when / then
        self.assertIsNone(lib.wrap_evidence(section, 'not in the passage', 'evidence:18:0'))
        self.assertIsNone(lib.wrap_evidence(section, 'herestarts', 'evidence:18:0'))
        self.assertIsNone(lib.wrap_evidence(section, 'inside chart', 'evidence:18:0'))

    def test_marked_sections_puts_passage_first_and_fails_on_missing_quote(self):
        # given
        content = {'passages': {'41-42': {'head': '[41~42]', 'blocks': [['p', 'A dull afternoon deserves care.']]}},
                   'reading': [question(18), question(41, passageKey='41-42', blocks=[], evidence=['A dull afternoon'])]}
        # when
        sections = lib.marked_sections(content)
        # then
        self.assertEqual([s.split('id="')[1].split('"')[0] for s in sections], ['q18', 'p41-42', 'q41'])
        self.assertIn('<span data-m="evidence:18:0">join the Product Team</span>', sections[0])
        self.assertIn('<span data-m="evidence:41:0">A dull afternoon</span>', sections[1])
        content['reading'][0]['evidence'] = ['words that are not there']
        with self.assertRaises(ValueError):
            lib.marked_sections(content)


class CoordinateTest(unittest.TestCase):
    def test_merge_lines_joins_pieces_on_one_line_and_keeps_lines_apart(self):
        # given: 첫 줄 두 조각, 둘째 줄 한 조각, 크기 없는 조각
        rects = [[100, 10, 50, 36], [150, 12, 30, 34], [20, 55, 200, 36], [0, 0, 0, 0]]
        # when
        result = lib.merge_lines(rects)
        # then
        self.assertEqual(result, [[100, 10, 80, 36], [20, 55, 200, 36]])

    def test_to_mark_scales_to_ten_thousandths_and_clips(self):
        # given
        width, height = 940, 1000
        # when
        inside = lib.to_mark([26.5, 357.1, 891.5, 36.1], width, height, 'question', 'evidence')
        outside = lib.to_mark([900, 990, 80, 30], width, height, 'passage', 'answer')
        # then
        self.assertEqual(inside, {'part': 'question', 'kind': 'evidence', 'x': 282, 'y': 3571, 'width': 9484, 'height': 361})
        self.assertEqual(outside['x'] + outside['width'], 10000)
        self.assertEqual(outside['y'] + outside['height'], 10000)

    def test_marks_for_takes_only_that_question_evidence_first(self):
        # given
        figure = {'w': 1000, 'h': 1000, 'items': [
            {'m': 'answer:42 answer:44', 'rects': [[10, 10, 100, 30]]},
            {'m': 'evidence:42:0', 'rects': [[10, 100, 400, 30], [10, 140, 50, 30]]},
            {'m': 'evidence:42:0', 'rects': [[60, 140, 90, 30]]},
            {'m': 'evidence:41:0', 'rects': [[10, 300, 400, 30]]}]}
        # when
        marks = lib.marks_for(42, figure, 'passage')
        # then: 같은 구절의 조각은 줄마다 하나로 합쳐진다
        self.assertEqual([m['kind'] for m in marks], ['evidence', 'evidence', 'answer'])
        self.assertEqual((marks[1]['x'], marks[1]['width']), (100, 1400))
        self.assertEqual(marks[2], {'part': 'passage', 'kind': 'answer', 'x': 100, 'y': 100, 'width': 1000, 'height': 300})
        self.assertEqual(len(lib.marks_for(44, figure, 'passage')), 1)

    def test_chunk_figures_splits_at_the_height_limit(self):
        # given
        heights = [('q18', 900), ('q19', 800), ('q20', 400), ('q21', 1000)]
        # when
        chunks = lib.chunk_figures(heights, limit=1800)
        # then
        self.assertEqual(chunks, [[('q18', 0, 900), ('q19', 900, 800)], [('q20', 0, 400), ('q21', 400, 1000)]])
        self.assertEqual(lib.chunk_figures([('p43-45', 3000)], limit=1800), [[('p43-45', 0, 3000)]])

    def test_parse_measure_reads_dumped_dom(self):
        # given
        dom = '<body><pre id="measure">[{&quot;id&quot;:&quot;q18&quot;,&quot;w&quot;:940,&quot;h&quot;:10,&quot;items&quot;:[]}]</pre></body>'
        # when
        figures = lib.parse_measure(dom)
        # then
        self.assertEqual(figures['q18']['w'], 940)
        with self.assertRaises(ValueError):
            lib.parse_measure('<body></body>')


class RoundTest(unittest.TestCase):
    def test_assemble_round_keeps_only_server_fields(self):
        # given
        content = {'number': 1, 'source': '내부 기록',
                   'reading': [question(18), question(41, passageKey='41-42', blocks=[])],
                   'words': [{'text': 'idle', 'choices': ['a', 'b', 'c', 'd', 'e'], 'answer': 2, 'extra': 1}]}
        figures = {'q18': {'w': 100, 'h': 100, 'items': [{'m': 'answer:18', 'rects': [[0, 0, 50, 10]]}]},
                   'q41': {'w': 100, 'h': 100, 'items': [{'m': 'answer:41', 'rects': [[0, 0, 50, 10]]}]},
                   'p41-42': {'w': 100, 'h': 100, 'items': [{'m': 'evidence:41:0', 'rects': [[0, 50, 50, 10]]}]}}
        # when
        data = lib.assemble_round(content, figures, number=3)
        # then
        self.assertEqual(data['number'], 3)
        self.assertEqual(set(data['reading'][0]), {'image', 'passage', 'answer', 'explanation', 'marks'})
        self.assertEqual((data['reading'][0]['image'], data['reading'][0]['passage']), ('q18.webp', None))
        self.assertEqual(data['reading'][1]['passage'], 'p41-42.webp')
        self.assertEqual([(m['part'], m['kind']) for m in data['reading'][1]['marks']], [('question', 'answer'), ('passage', 'evidence')])
        self.assertEqual(data['words'], [{'text': 'idle', 'choices': ['a', 'b', 'c', 'd', 'e'], 'answer': 2}])
        json.dumps(data)

    def test_validate_round_accepts_good_data(self):
        # given / when / then
        self.assertEqual(lib.validate_round(round_data()), [])

    def test_validate_round_reports_each_problem(self):
        # given
        data = round_data()
        data['reading'][0]['marks'] = [m for m in data['reading'][0]['marks'] if m['kind'] != 'answer']
        data['reading'][1]['marks'][0]['x'] = 9999
        data['reading'][2]['marks'][0]['part'] = 'passage'
        data['reading'][3]['explanation'] = '해설'
        data['words'][4]['choices'] = ['a', 'a', 'b', 'c', 'd']
        data['words'].pop()
        # when
        problems = '\n'.join(lib.validate_round(data))
        # then
        for expected in ('어휘 문항이 49개', '18번: 정답 표시가 없습니다', '19번: 표시 좌표', '20번: 표시 좌표', '21번: 해설', '어휘 5번'):
            self.assertIn(expected, problems)


class PageTest(unittest.TestCase):
    def content(self):
        return {'number': 1, 'source': '2026년 10월 고2 학력평가 변형',
                'passages': {'41-42': {'head': '[41~42] 다음 글을 읽고, 물음에 답하시오.', 'blocks': [['p', 'A dull afternoon.']]}},
                'reading': [question(18), question(41, passageKey='41-42', blocks=[], evidence=['A dull afternoon'])], 'words': []}

    def test_banned_words_finds_source_words(self):
        # given
        text = '제1회 고2 영어 독해 변형 모의고사 · 2026년 10월 고2 학력평가 변형'
        # when
        found = lib.banned_words(text, '2026년 10월 고2 학력평가 변형')
        # then
        self.assertEqual(found, ['학력평가', '변형', '2026년 10월 고2 학력평가 변형'])
        self.assertEqual(lib.banned_words('제1회 고2 영어 독해 실전 모의고사', '내부 기록'), [])

    def test_visible_text_drops_tags_style_and_script(self):
        # given
        page = '<style>.a{}</style><p>Date &amp; <b>Time</b></p><script>var x = "출처";</script>'
        # when / then
        self.assertEqual(lib.visible_text(page), 'Date & Time')

    def test_pages_have_no_source_words_and_paper_has_no_marks(self):
        # given
        content = self.content()
        # when
        figures = lib.figures_page(content)
        paper = lib.paper_page(content, 1, '고2')
        # then
        self.assertIn('data-m="evidence:41:0"', figures)
        self.assertIn("pre.id = 'measure'", figures)
        self.assertNotIn('data-m', paper)
        self.assertIn('제1회 고2 영어 독해 실전 모의고사 (18~45번)', paper)
        self.assertIn('28문항 · 45분', paper)
        self.assertIn('<div class="cols set newpage"><section class="fig" id="p41-42"', paper)
        self.assertEqual(lib.banned_words(lib.visible_text(figures) + lib.visible_text(paper), content['source']), [])


if __name__ == '__main__':
    unittest.main()
