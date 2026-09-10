from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1] / 'src'


class WeeklyExamHubContractTest(unittest.TestCase):
    def setUp(self):
        self.hub = (ROOT / 'components' / 'WeeklyExamHub.tsx').read_text(encoding='utf-8')
        self.naesin = (ROOT / 'components' / 'NaesinWikiV1.tsx').read_text(encoding='utf-8')
        self.css = (ROOT / 'styles' / 'generator.css').read_text(encoding='utf-8')

    def test_supported_school_grade_set_policies(self):
        for school, grade, expected in (
            ('압구정중학교', '2학년', "['40', '70']"),
            ('신구중학교', '2학년', "['40', '70']"),
            ('신구중학교', '3학년', "['40A', '40B']"),
            ('신사중학교', '3학년', "['40A', '40B']"),
        ):
            self.assertIn(f"'{school}|{grade}': {expected}", self.hub)

    def test_entry_points_share_selected_exam_context(self):
        self.assertIn('<WeeklyExamHub', self.naesin)
        for prop in ('context={context}', 'pastExamCount={pastExams.length}',
                     'schoolPrintCount={prints.length}', 'wrongCount={wrongs.length}',
                     'hasDigitalTwin={Boolean(twinProfile)}'):
            self.assertIn(prop, self.naesin)
        self.assertIn("setWorkspace('final')", self.naesin)
        self.assertIn("setCore('student_wrong_note')", self.naesin)

    def test_human_approval_gate_is_explicit(self):
        for status in ('DRAFT', 'REVIEW', 'APPROVED', 'FINAL/PUBLISHED'):
            self.assertIn(status, self.hub)
        self.assertIn('AI 제작 완료는 공개가 아닙니다.', self.hub)

    def test_context_is_living_and_partial_is_visible(self):
        self.assertIn('Context PARTIAL', self.hub)
        self.assertIn('Context LIVING', self.hub)
        self.assertIn('새 자료 등록 시 갱신', self.hub)
        self.assertIn('자료 보기 · AI Context', self.hub)

    def test_mobile_layout_has_single_column_and_no_fixed_width(self):
        self.assertRegex(self.css, re.compile(r'@media\(max-width:720px\).*?\.repeat-entry-grid\{grid-template-columns:1fr\}', re.S))
        self.assertIn('@media(max-width:375px)', self.css)
        self.assertNotRegex(self.css, re.compile(r'\.naesin-repeat-hub[^}]*width:\s*[4-9]\d{2}px'))


if __name__ == '__main__':
    unittest.main()
