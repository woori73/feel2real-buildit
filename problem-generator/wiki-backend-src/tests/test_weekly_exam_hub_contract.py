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

    def test_private_draft_final_workflow_is_explicit(self):
        for phrase in ('DRAFT', 'FINAL 확정', 'FINAL은 외부 공개를 의미하지 않습니다.'):
            self.assertIn(phrase, self.hub)
        for removed in ("transition(item.id,'publish')", "transition(item.id,'approve')"):
            self.assertNotIn(removed, self.hub)

    def test_context_is_living_and_partial_is_visible(self):
        self.assertIn("'PARTIAL'|'LIVING'|'COMPLETE'", self.hub)
        self.assertIn('context_snapshot', self.hub)
        self.assertIn('currentStatus', self.hub)
        self.assertIn('자료 보기 · AI Context', self.hub)

    def test_mobile_layout_has_single_column_and_no_fixed_width(self):
        self.assertRegex(self.css, re.compile(r'@media\(max-width:720px\).*?\.repeat-entry-grid\{grid-template-columns:1fr\}', re.S))
        self.assertIn('@media(max-width:375px)', self.css)
        self.assertNotRegex(self.css, re.compile(r'\.naesin-repeat-hub[^}]*width:\s*[4-9]\d{2}px'))


if __name__ == '__main__':
    unittest.main()
