import base64
import re
import tempfile
import unittest
from pathlib import Path

import exam_paper as paper
from test_exam_check import balanced, mc, sa

PASSAGE = 'Minho went to the library after school and borrowed three books about space travel.'
DIALOGUE = 'G: Hi, Minho.\nB: Hello, Sora. What a surprise!'


def static_with(*files):
    """지어낸 그림·로고 파일이 든 임시 static 폴더."""
    root = Path(tempfile.mkdtemp())
    for name in files:
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'<svg xmlns="http://www.w3.org/2000/svg"/>' if name.endswith('.svg') else b'fake-image')
    return root


class InlineTest(unittest.TestCase):
    def test_given_view_marker_and_underline_when_inlined_then_only_u_is_a_tag(self):
        # given: <보 기>는 글자이고 <u>만 태그다
        text = '<보 기>의 ⓐ<u>like</u> & <b>bold</b>'
        # when
        result = paper.inline(text)
        # then
        self.assertEqual('&lt;보 기&gt;의 ⓐ<u>like</u> &amp; &lt;b&gt;bold&lt;/b&gt;', result)

    def test_given_book_title_in_i_tag_when_inlined_then_italic_tag_kept(self):
        # given: 책 제목은 <i>로 감싼다. 그 밖의 태그(<em>, <img …>)는 글자로 찍는다
        text = 'His novel is <i>Fahrenheit 451</i> (1953), not <em>this</em> <img src=x>.'
        # when
        result = paper.inline(text)
        # then
        self.assertEqual('His novel is <i>Fahrenheit 451</i> (1953), not &lt;em&gt;this&lt;/em&gt; '
                         '&lt;img src=x&gt;.', result)

    def test_given_title_inside_underline_when_inlined_then_both_tags_kept(self):
        # given: 밑줄 친 구 안에 책 제목이 있다
        text = 'ⓐ<u>the story <i>A Sound of Thunder</i></u>'
        # when
        result = paper.inline(text)
        # then
        self.assertEqual('ⓐ<u>the story <i>A Sound of Thunder</i></u>', result)

    def test_given_blank_with_several_spaces_when_inlined_then_spaces_kept(self):
        # given: 옛 표기의 빈칸은 보통 공백 세 칸이다
        text = 'I (   ) it.'
        # when
        result = paper.inline(text)
        # then
        self.assertEqual('I (&nbsp;&nbsp;&nbsp;) it.', result)


class SplitStemTest(unittest.TestCase):
    def test_given_prompt_and_example_lines_when_split_then_first_line_is_prompt(self):
        # given
        stem = '아래 빈칸에 들어갈 수 없는 단어는?\n◦ It is ( ) today.\r\n◦ No one ( ) it.'
        # when
        parts = paper.split_stem(stem)
        # then
        self.assertEqual('아래 빈칸에 들어갈 수 없는 단어는?', parts['prompt'])
        self.assertEqual(['◦ It is ( ) today.', '◦ No one ( ) it.'], parts['lines'])
        self.assertEqual(([], [], ''), (parts['table_head'], parts['images'], parts['view_title']))

    def test_given_table_image_and_view_lines_when_split_then_they_are_not_example_lines(self):
        # given: 제작 표시 줄은 인쇄할 예문이 아니다
        stem = ('그림을 묘사하는 <보 기> 문장은?\n[[img:/naesin/x/fig-q01.webp]]\n<보 기>\n◦ He ( ) to me.\n'
                '(선택지 표: ⓐ | ⓑ | ⓒ)')
        # when
        parts = paper.split_stem(stem)
        # then
        self.assertEqual(['ⓐ', 'ⓑ', 'ⓒ'], parts['table_head'])
        self.assertEqual(['/naesin/x/fig-q01.webp'], parts['images'])
        self.assertEqual('<보 기>', parts['view_title'])
        self.assertEqual(['◦ He ( ) to me.'], parts['lines'])

    def test_given_empty_stem_when_split_then_empty_prompt(self):
        # given / when
        parts = paper.split_stem(None)
        # then
        self.assertEqual(('', []), (parts['prompt'], parts['lines']))


class PromptTest(unittest.TestCase):
    def test_given_negative_words_when_emphasized_then_bold_underline(self):
        # given
        prompt = '문장의 전환이 옳지 않은 것을 모두 고르면?'
        # when
        result = paper.emphasize(prompt)
        # then
        self.assertEqual('문장의 전환이 옳지 <u class="em">않은</u> 것을 <u class="em">모두</u> 고르면?', result)

    def test_given_already_underlined_word_when_emphasized_then_not_doubled(self):
        # given: 옛 표기는 데이터에 <u>가 들어 있다. 부정어가 아닌 밑줄은 그대로 둔다
        prompt = '밑줄 친 <u>that</u>과 쓰임이 <u>없는</u> 것은?'
        # when
        result = paper.emphasize(prompt)
        # then
        self.assertEqual('밑줄 친 <u>that</u>과 쓰임이 <u class="em">없는</u> 것은?', result)

    def test_given_single_and_multi_when_labelled_then_score_or_count_and_score(self):
        # given
        single, multi = mc(3, score=2), mc([2, 5], multi=True, score=4)
        # when
        labels = [paper.score_label(single), paper.score_label(multi)]
        # then
        self.assertEqual(['(2점)', '(정답 2개/4점)'], labels)

    def test_given_plain_prompt_when_rendered_then_score_appended_once(self):
        # given
        question = mc(1, score=3)
        # when
        result = paper.prompt_html(question, '글의 내용과 일치하는 것은?')
        # then
        self.assertEqual('글의 내용과 일치하는 것은? <span class="pts">(3점)</span>', result)

    def test_given_prompt_with_score_label_when_rendered_then_not_added_again(self):
        # given: 옛 표기는 발문에 배점이 이미 있다
        question = mc([1, 4], multi=True, score=3)
        # when
        result = paper.prompt_html(question, '단어를 <u>모두</u> 고르면? (정답 2개/3점)')
        # then
        self.assertEqual('단어를 <u class="em">모두</u> 고르면? <span class="pts">(정답 2개/3점)</span>', result)
        self.assertEqual(1, result.count('점)'))


class ChoiceTest(unittest.TestCase):
    def test_given_short_words_when_layout_then_row(self):
        # given
        options = ['get', 'take', 'choose', 'express', 'whispered']
        # when
        layout = paper.choice_layout(options)
        # then
        self.assertEqual('row', layout)

    def test_given_one_sentence_option_when_layout_then_vertical(self):
        # given: 태그는 너비에서 뺀다
        options = ['<u>get</u>', 'take', 'choose', 'express', 'Andy saw Suho swimming in the pool.']
        # when
        layout = paper.choice_layout(options)
        # then
        self.assertEqual('vertical', layout)

    def test_given_korean_phrases_when_layout_then_hangul_counts_double(self):
        # given: 한글 8자는 16칸이라 가로 3칸에 들어가지 않는다
        options = ['ⓐ: 가장 오래된', 'ⓑ: 질문에 대답한다', 'ⓒ: 주인공', 'ⓓ: 유명한', 'ⓔ: 영감']
        # when
        layout = paper.choice_layout(options)
        # then
        self.assertEqual('vertical', layout)

    def test_given_option_with_book_title_when_rendered_then_italic_and_tag_not_counted_in_width(self):
        # given: 제목만 있는 짧은 선택지와 제목이 든 문장 선택지
        short, sentence = '<i>Dune</i>', 'In <i>Fahrenheit 451</i>, books are not allowed.'
        # when
        html_text, layout = paper.option_html(sentence), paper.choice_layout([short] * 5)
        # then
        self.assertEqual('<p>In <i>Fahrenheit 451</i>, books are not allowed.</p>', html_text)
        self.assertEqual('row', layout)

    def test_given_table_option_when_parsed_then_cells_without_marks(self):
        # given
        option = 'ⓐ: to clean / ⓑ: open / ⓒ: taking'
        # when
        cells = paper.table_cells(option, 3)
        # then
        self.assertEqual(['to clean', 'open', 'taking'], cells)

    def test_given_option_with_wrong_cell_count_when_parsed_then_none(self):
        # given
        option = 'ⓐ: in / ⓑ: with'
        # when
        cells = paper.table_cells(option, 3)
        # then
        self.assertIsNone(cells)

    def test_given_table_head_when_choices_rendered_then_table_with_underlined_head(self):
        # given
        options = ['ⓐ: in / ⓑ: with', 'ⓐ: on / ⓑ: for', 'ⓐ: at / ⓑ: of', 'ⓐ: to / ⓑ: by', 'ⓐ: in / ⓑ: by']
        # when
        result = paper.choices_html(options, ['ⓐ', 'ⓑ'])
        # then
        self.assertIn('<tr><th></th><th><u>ⓐ</u></th><th><u>ⓑ</u></th></tr>', result)
        self.assertIn('<tr><td>②</td><td>on</td><td>for</td></tr>', result)
        self.assertEqual(6, result.count('<tr>'))
        self.assertNotIn('ⓐ:', result)

    def test_given_table_head_but_plain_options_when_rendered_then_falls_back_to_list(self):
        # given: 표 표시는 있는데 선택지가 표 꼴이 아니다
        options = ['one', 'two', 'three', 'four', 'five']
        # when
        result = paper.choices_html(options, ['ⓐ', 'ⓑ'])
        # then
        self.assertIn('<div class="choices row">', result)
        self.assertNotIn('<table', result)

    def test_given_arrow_and_dialogue_lines_when_option_rendered_then_separate_lines(self):
        # given
        arrow, talk = 'To sleep is good.\n⇒ It is good to sleep.', 'A: Why?\nB: Because.'
        # when
        results = [paper.option_html(arrow), paper.option_html(talk)]
        # then
        self.assertEqual(['<p>To sleep is good.</p><p class="arrow">⇒ It is good to sleep.</p>',
                          '<p><b>A:</b> Why?</p><p><b>B:</b> Because.</p>'], results)


class FigureTest(unittest.TestCase):
    def test_given_file_under_static_when_figure_rendered_then_data_uri(self):
        # given
        static = static_with('naesin/x/fig-q01.webp')
        # when
        result = paper.figure_html('/naesin/x/fig-q01.webp', True, static_dir=static)
        # then
        expected = 'data:image/webp;base64,' + base64.b64encode(b'fake-image').decode('ascii')
        self.assertEqual('<div class="fig framed"><img src="%s" alt="그림"></div>' % expected, result)

    def test_given_file_only_in_asset_dir_when_figure_rendered_then_found_by_name(self):
        # given: 저장소에는 없고 작업 폴더에만 있는 svg
        static, assets = static_with(), static_with('fig-q06.svg')
        # when
        result = paper.figure_html('fig-q06.svg', False, asset_dir=assets, static_dir=static)
        # then
        self.assertIn('<div class="fig"><img src="data:image/svg+xml;base64,', result)

    def test_given_missing_file_when_figure_rendered_then_visible_marker(self):
        # given
        static = static_with()
        # when
        result = paper.figure_html('/naesin/x/<none>.webp', True, static_dir=static)
        # then
        self.assertEqual('<div class="fig missing">[그림 없음: &lt;none&gt;.webp]</div>', result)


class BoxTest(unittest.TestCase):
    def test_given_title_then_body_when_checked_then_title(self):
        # given
        lines = ['Fur Rondy Festival in Alaska, USA', PASSAGE]
        # when / then
        self.assertTrue(paper.is_title(lines))

    def test_given_sentence_dialogue_or_single_line_when_checked_then_not_title(self):
        # given
        cases = [['He was born in 1920.', PASSAGE], DIALOGUE.split('\n'), ['Only one line']]
        # when
        results = [paper.is_title(lines) for lines in cases]
        # then
        self.assertEqual([False, False, False], results)

    def test_given_mixed_lines_when_body_rendered_then_each_kind_and_notes_apart(self):
        # given: 대화, ◦ 예문, 짧은 문장, 긴 줄글, 각주
        lines = ['G: Hi.', '◦ It is ( ⓐ ) today.', 'I was <u>scared</u>.', PASSAGE + ' ' + PASSAGE, '*ban: 금지하다']
        # when
        body, notes = paper.body_html(lines)
        # then
        self.assertEqual('<p class="dlg"><b>G:</b><span>Hi.</span></p><p class="dot">◦ It is ( ⓐ ) today.</p>'
                         '<p>I was <u>scared</u>.</p><p class="para">%s %s</p>' % (PASSAGE, PASSAGE), body)
        self.assertEqual('<p class="notes">*ban: 금지하다</p>', notes)

    def test_given_passage_and_prompt_with_book_title_when_rendered_then_italic_kept(self):
        # given: 지문의 줄글과 발문에 책 제목이 있다
        line = 'In Ray Bradbury’s story <i>A Sound of Thunder</i>, the main character steps on a butterfly. ' + PASSAGE
        # when
        body, _ = paper.body_html([line])
        prompt = paper.prompt_html(mc(1, score=2), 'Who wrote <i>Fahrenheit 451</i>?')
        # then
        self.assertTrue(body.startswith('<p class="para">In Ray Bradbury’s story <i>A Sound of Thunder</i>, the main'))
        self.assertEqual('Who wrote <i>Fahrenheit 451</i>? <span class="pts">(2점)</span>', prompt)

    def test_given_view_title_when_box_rendered_then_title_is_text_not_tag(self):
        # given
        lines = ['◦ He ( ) to me.']
        # when
        result = paper.box_html(lines, '<보 기>')
        # then
        self.assertEqual('<div class="box view"><span class="box-title">&lt;보 기&gt;</span>'
                         '<p class="dot">◦ He ( ) to me.</p></div>', result)

    def test_given_titled_passage_when_box_rendered_then_title_centered_class(self):
        # given
        lines = ['The Science Behind the Story', PASSAGE]
        # when
        result = paper.box_html(lines, titled=True)
        # then
        self.assertTrue(result.startswith('<div class="box"><p class="ptitle">The Science Behind the Story</p>'))

    def test_given_no_lines_when_box_rendered_then_nothing(self):
        # given / when / then
        self.assertEqual('', paper.box_html(['', '  ']))


class GroupTest(unittest.TestCase):
    def test_given_neighbours_share_passage_when_grouped_then_same_name(self):
        # given: 2~3번이 같은 지문, 1·4번은 혼자
        questions = [mc(1, no=0), mc(1, source=PASSAGE), mc(2, source=PASSAGE, stem='윗글과 일치하는 것은?'), mc(3, no=3)]
        # when
        groups = paper.passage_groups(questions)
        # then
        self.assertEqual(['', 'p2', 'p2', ''], groups)

    def test_given_same_passage_far_apart_when_grouped_then_not_grouped(self):
        # given: 같은 지문이 떨어져 있으면 묶지 않는다(공통 지문은 연속 배치가 원칙)
        questions = [mc(1, source=PASSAGE), mc(2, no=1), mc(3, source=PASSAGE)]
        # when
        groups = paper.passage_groups(questions)
        # then
        self.assertEqual(['', '', ''], groups)

    def test_given_prose_and_dialogue_when_head_built_then_wording_differs(self):
        # given
        prose, talk = [PASSAGE, '*borrow: 빌리다'], DIALOGUE.split('\n')
        # when
        heads = [paper.group_head(12, 14, prose), paper.group_head(1, 2, talk)]
        # then
        self.assertEqual(['<div class="group-head">[12~14] 다음 글을 읽고 물음에 답하시오.</div>',
                          '<div class="group-head">[1~2] 다음 대화를 읽고 물음에 답하시오.</div>'], heads)


class QuestionTest(unittest.TestCase):
    def test_given_mc_with_examples_when_rendered_then_number_prompt_box_choices_in_order(self):
        # given
        question = mc(2, stem='빈칸에 들어갈 수 없는 단어는?\n◦ It is ( ) today.', score=2)
        # when
        result = paper.question_html(question, 8)
        # then
        self.assertTrue(result.startswith('<div class="q" data-qnum="8"><div class="q-head"><span class="q-no">8.</span>'))
        order = [result.index(mark) for mark in ('class="pts">(2점)', '<div class="box">', '<div class="choices row">')]
        self.assertEqual(sorted(order), order)
        self.assertEqual(5, result.count('class="ch-mark"'))

    def test_given_grouped_question_when_rendered_then_passage_not_repeated(self):
        # given
        question = mc(1, source=PASSAGE)
        # when
        alone, grouped = paper.question_html(question, 1), paper.question_html(question, 1, own_passage=False)
        # then
        self.assertIn(PASSAGE, alone)
        self.assertNotIn(PASSAGE, grouped)

    def test_given_figure_only_or_figure_with_view_box_when_rendered_then_frame_differs(self):
        # given: 승인본은 그림만 있으면 상자에 넣고, <보 기> 상자가 따라오면 그림을 그대로 둔다
        static = static_with('naesin/x/fig.webp')
        only = mc(1, stem='그림과 일치하는 것은?\n[[img:/naesin/x/fig.webp]]', source='')
        with_view = mc(1, stem='그림을 묘사하는 문장은?\n[[img:/naesin/x/fig.webp]]\n<보 기>\n◦ He ( ) to me.', source='')
        # when
        results = [paper.question_html(q, 1, static_dir=static) for q in (only, with_view)]
        # then
        self.assertIn('<div class="fig framed">', results[0])
        self.assertIn('<div class="fig"><img', results[1])
        self.assertIn('<div class="box view">', results[1])

    def test_given_short_answer_when_rendered_then_condition_box_and_answer_lines(self):
        # given
        question = sa()
        question['conditions'] = ['과거형으로 쓸 것']
        # when
        result = paper.question_html(question, 40)
        # then
        self.assertIn('<span class="box-title">&lt;조 건&gt;</span><p>과거형으로 쓸 것</p>', result)
        self.assertEqual(2, result.count('<div class="ans-line"></div>'))
        self.assertNotIn('class="choices', result)


class RenderTest(unittest.TestCase):
    def exam(self):
        questions = balanced(40)
        questions[5]['sourceText'] = questions[6]['sourceText'] = questions[7]['sourceText'] = PASSAGE
        questions[6]['stem'], questions[7]['stem'] = '윗글의 제목은?', '윗글의 목적은?'
        return {'examTitle': '신구중 2학년 기말 대비 12회', 'subTitle': '영어 · 40문항', 'questions': questions}

    def test_given_group_when_pieces_built_then_head_passage_and_first_question_in_one_piece(self):
        # given
        questions = self.exam()['questions']
        # when
        pieces = paper.pieces_html(questions)
        # then
        self.assertEqual(40, len(pieces))
        self.assertTrue(pieces[5].startswith('<div class="piece"><div class="group-head">[6~8] 다음 글을'))
        self.assertEqual(1, pieces[5].count(PASSAGE))
        self.assertNotIn(PASSAGE, pieces[6] + pieces[7])

    def test_given_exam_when_rendered_then_40_items_in_order_with_logo_and_maker(self):
        # given
        exam = self.exam()
        # when
        result = paper.render_paper(exam)
        # then
        self.assertEqual([str(i) for i in range(1, 41)], re.findall(r'data-qnum="(\d+)"', result))
        self.assertEqual(1, result.count('<img class="logo" src="data:image/svg+xml;base64,'))
        self.assertIn('신구중 2학년 기말 대비 12회', result)
        self.assertIn('data-maker="제작 : 압구정에듀홀릭 내신 wiki"', result)
        self.assertIn('"Noto Serif KR"', result)
        self.assertIn('i { font-style: italic; }', result)
        self.assertNotIn('http://', result.replace('http://www.w3.org', ''))
        self.assertNotIn('https://', result)

    def test_given_no_logo_file_when_rendered_then_error_instead_of_paper_without_logo(self):
        # given: 로고가 없는 static 폴더(스크립트 옆 복사본도 없다)
        static = static_with()
        # when / then
        if not (Path(paper.__file__).resolve().parent / paper.LOGO_NAME).is_file():
            with self.assertRaises(FileNotFoundError):
                paper.logo_data_uri(static)

    def test_given_logo_in_static_when_loaded_then_svg_data_uri(self):
        # given
        static = static_with('img/' + paper.LOGO_NAME)
        # when
        uri = paper.logo_data_uri(static)
        # then
        self.assertTrue(uri.startswith('data:image/svg+xml;base64,'))

    def test_given_title_and_sub_when_header_built_then_logo_first_then_title_and_name_cell(self):
        # given / when
        result = paper.header_html('기말 대비 <1회>', '영어 · 40문항', 'data:x')
        # then
        self.assertIn('<img class="logo" src="data:x" alt="F2R">기말 대비 &lt;1회&gt;</td>', result)
        self.assertIn('<td colspan="4">영어 · 40문항</td>', result)
        self.assertIn('<td class="label">이름</td>', result)


if __name__ == '__main__':
    unittest.main()
