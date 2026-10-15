import re
import shutil
import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import weekly_paper
import weekly_validate as Validate
from school_layout import SHINGU_MIDDLE_2, get_profile


def mc(no, prompt='다음 빈칸에 들어갈 말로 가장 적절한 것은?',
       passage='Modern street art comes in many different ______.',
       choices=('① forms', '② dots', '③ clues', '④ walls', '⑤ figures'),
       layout='row', **extra):
    q = {'no': no, 'prompt': prompt, 'passage': passage, 'choices': list(choices),
         'layout': layout, 'section': None, 'passage_class': 'prose', 'group': None}
    q.update(extra)
    return q


def paper(questions, **kw):
    return weekly_paper.render_student_paper(
        title_row='T', sub_row='S', run_header_left='L', run_header_right='R',
        maker='M', questions=questions, **kw)


class WeeklyExamLayoutTest(unittest.TestCase):
    def test1_plain_five_choice(self):
        # given: 일반 5지선다 1문항
        # when: DATA gate
        self.assertEqual([], Validate.check_options_valid(mc(1)))
        self.assertEqual([], Validate.check_markers_match(mc(1)))

    def test2_underline_and_markers(self):
        # given: 밑줄 + ①-⑤ markup 문항
        q = mc(1, prompt='다음 밑줄 친 부분의 의미는?',
               passage='He is <u>famous for using lines</u>.',
               markup=[{'type': 'underline', 'text': 'famous for using lines', 'field': 'passage'}])
        # when: markup 적용 후 RENDER gate
        out = paper([q])
        self.assertIn('<u>famous for using lines</u>', out)
        self.assertEqual([], Validate.check_markup_present(out, [q]))
        self.assertEqual([], Validate.check_underline_targets(q))

    def test3_abc_blanks(self):
        # given: (A)(B) 요구 발문 + 본문 target
        q = mc(1, prompt='(A)와 (B)에 들어갈 말은?',
               passage='comes in many different (A), and it makes cities (B).')
        self.assertEqual([], Validate.check_blanks_present(q))
        bad = mc(2, prompt='(A)와 (B)와 (C)에 들어갈 말은?', passage='only (A) here.')
        self.assertTrue(Validate.check_blanks_present(bad))

    def test4_common_passage_three_questions(self):
        # given: 공통지문 + 3문항 (동일 common_passage_id + group)
        shared = 'Shared box line one.<br>Shared box line two.'
        qs = [mc(1, passage=shared, passage_class='box', group='g',
                 common_passage_id='p1'),
              mc(2, passage='', group='g', common_passage_id='p1'),
              mc(3, passage='', group='g', common_passage_id='p1')]
        # when: 렌더 + RENDER gate
        out = paper(qs)
        self.assertEqual(1, out.count('Shared box line one.'))
        self.assertEqual([], Validate.check_no_duplicate_passage(out, qs))
        self.assertEqual([], Validate.check_group_refs(qs))
        self.assertEqual([], Validate.check_group_preserved(out, qs))

    def test5_box_view(self):
        # given: 보기 BOX 문항
        q = mc(1, prompt='다음 보기의 진술 중 옳은 것은?',
               passage='&lt;보기&gt;<br>a. first<br>b. second',
               passage_class='box')
        out = paper([q])
        self.assertIn('class="passage box"', out)
        self.assertEqual([], Validate.check_boxes_once(out, [q]))

    def test6_conditions_answer_space(self):
        # given: 조건 BOX + 서답형 답안공간
        q = mc(1, prompt='조건에 맞춰 쓰시오.', passage='Write it.',
               choices=[], conditions=['that을 사용할 것'], answer_lines=1)
        # when: DATA gate(최소공간) + 렌더
        self.assertTrue(Validate.check_conditions_space(q, 2))
        out = paper([dict(q, answer_lines=3)])
        self.assertIn('cond-box', out)
        self.assertEqual(3, out.count('answer-line"></div>'))
        self.assertEqual([], Validate.check_conditions_space(dict(q, answer_lines=3), 2))

    def test7_long_passage_at_boundary(self):
        # given: 경계를 넘나드는 긴 지문 + 후속 문항
        long_box = ' '.join(['Sentence %d about street art in London.' % i for i in range(30)])
        qs = [mc(1, passage=long_box, passage_class='box', group='g'),
              mc(2, passage='', group='g')] + [mc(i) for i in range(3, 12)]
        # when: 렌더
        out = paper(qs)
        # then: passage block 유지 + 그룹 분리 없음
        self.assertIn('Sentence 0 about street art', out)
        self.assertEqual([], Validate.check_group_preserved(out, qs))

    def test8_long_constructed_at_bottom(self):
        # given: 하단에 오는 긴 서답형
        qs = [mc(i) for i in range(1, 6)]
        qs.append(mc(6, prompt='조건에 맞춰 영작하시오.', passage='Summarize it.',
                     choices=[],
                     conditions=['8~15단어로 쓸 것', '완전한 문장으로 쓸 것'],
                     answer_lines=4))
        # when: 렌더
        out = paper(qs)
        # then: 조건 BOX + 4줄 답안공간 + keep-together
        self.assertIn('cond-box', out)
        self.assertEqual(4, out.count('answer-line"></div>'))
        self.assertIn('page-break-inside: avoid', out)

    def test9_synthetic_seventy_no_loss(self):
        # given: 70문항 상당 synthetic (보기/밑줄/공통지문 혼합, 실제 70문항 생성 아님)
        qs = []
        for i in range(1, 71):
            extra = {}
            if i % 10 == 1:
                extra = {'passage': 'Common box %d.<br>Second line.' % i,
                         'passage_class': 'box', 'group': 'g%d' % i,
                         'common_passage_id': 'p%d' % i}
            elif i % 10 in (2, 3):
                extra = {'passage': '', 'group': 'g%d' % (i - i % 10 + 1),
                         'common_passage_id': 'p%d' % (i - i % 10 + 1)}
            if i % 7 == 0:
                extra = {**extra, 'conditions': ['완전한 문장으로 쓸 것'], 'answer_lines': 2,
                         'choices': []}
            qs.append(mc(i, **extra))
        # when: 렌더 + 전체 gate
        out = paper(qs)
        result = Validate.validate_document(qs, out)
        self.assertEqual([], result['DATA'], result['DATA'][:3])
        self.assertEqual([], result['RENDER'], result['RENDER'][:3])
        self.assertEqual(70, len(re.findall(r'data-qnum="(\d+)"', out)))
        self.assertEqual(list(range(1, 71)), sorted(int(n) for n in re.findall(r'data-qnum="(\d+)"', out)))

    def test10_csat_weekly_regression(self):
        # given: 기존 renderer 계약 sampled
        # when: round-1 FINAL 형식 9문항 렌더
        out = paper([mc(i) for i in range(1, 10)])
        # then: A4/2단/qblock/footer 계약 유지
        for token in ('@page { size: A4 portrait', '.cols { display: flex',
                      'class="exam-header"', 'class="page-footer"'):
            self.assertIn(token, out, token)

    def test11_print_clipping_validation(self):
        # given: Chrome headless 사용 가능 시에만 실행되는 PRINT gate
        chrome = (shutil.which('chrome') or shutil.which('chrome.exe')
                  or shutil.which('google-chrome')
                  or r'C:\Program Files\Google\Chrome\Application\chrome.exe')
        import os as _os
        if not chrome or not _os.path.exists(chrome):
            self.skipTest('chrome binary unavailable')
        import subprocess as _sp
        import tempfile as _tf
        qs = [mc(1, passage='Box line one.<br>Box line two.', passage_class='box', group='g'),
              mc(2, passage='', group='g'),
              mc(3, prompt='다음 밑줄 친 부분은?', passage='word <u>target</u> here.')]
        out = paper(qs)
        with _tf.TemporaryDirectory() as tmp:
            src = str(Path(tmp) / 'p.html')
            pdf = str(Path(tmp) / 'p.pdf')
            Path(src).write_text(out, encoding='utf-8')
            _sp.run([chrome, '--headless', '--disable-gpu', '--no-sandbox',
                     '--print-to-pdf=%s' % pdf, '--print-to-pdf-no-header', src],
                    capture_output=True, timeout=120)
            try:
                from pypdf import PdfReader
            except ImportError:
                self.skipTest('pypdf unavailable')
            text = '\n'.join((pg.extract_text() or '') for pg in PdfReader(pdf).pages)
            self.assertEqual([], Validate.pdf_text_completeness(out, text))
            self.assertEqual([], Validate.check_footer_continuity(out))

    def test_profile_wiring(self):
        # given: 신구중 Layout Profile
        prof = get_profile('신구중', '2학년')
        self.assertEqual(2, prof['columns'])
        self.assertEqual('A4 portrait', prof['paper_size'])
        # when: profile 전달 렌더 (기본 동작 불변)
        plain = paper([mc(1)])
        with_prof = paper([mc(1)], profile=prof)
        self.assertEqual(plain, with_prof)
        # unknown 학교는 defaults
        self.assertEqual('A4 portrait', get_profile('없는학교', '1학년')['paper_size'])


if __name__ == '__main__':
    unittest.main()
