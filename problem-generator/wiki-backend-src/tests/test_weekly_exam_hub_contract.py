from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2] / 'wiki-src' / 'src'


class WeeklyExamHubContractTest(unittest.TestCase):
    def setUp(self):
        self.hub = (ROOT / 'components' / 'WeeklyExamHub.tsx').read_text(encoding='utf-8')
        self.naesin = (ROOT / 'components' / 'NaesinWikiV1.tsx').read_text(encoding='utf-8')
        self.css = (ROOT / 'styles' / 'generator.css').read_text(encoding='utf-8')

    def test_supported_school_grade_set_policies(self):
        for school, grade, expected in (
            ('압구정중학교', '2학년', "['40','70']"),
            ('신구중학교', '2학년', "['40','70']"),
            ('신구중학교', '3학년', "['40A','40B']"),
            ('신사중학교', '3학년', "['40A','40B']"),
        ):
            self.assertIn(f"'{school}|{grade}':{expected}", self.hub)

    def test_entry_points_share_selected_exam_context(self):
        self.assertIn('<WeeklyExamHub', self.naesin)
        for prop in ('context={context}', 'pastExamCount={pastExams.length}',
                     'schoolPrintCount={prints.length}', 'wrongCount={wrongs.length}',
                     'hasDigitalTwin={Boolean(twinProfile)}'):
            self.assertIn(prop, self.naesin)
        self.assertIn("setWorkspace('final')", self.naesin)
        self.assertIn("setCore('student_wrong_note')", self.naesin)

    def test_given_hub_source_when_scanned_then_no_approval_step_remains(self):
        # given: 승인 단계를 없앤 허브 화면 소스
        # when / then: 승인·확정·상태 표시와 전이 호출이 남아 있지 않다
        for removed in ("transition(", 'DRAFT 등록', 'DRAFT로', 'FINAL 확정', '최근 FINAL', '승인', '검토 중', '반려', '수정 요청'):
            self.assertNotIn(removed, self.hub)
        # then: 이미 올린 완성본 파일(final_deliverables)을 같은 칸에서 연다
        self.assertIn('finals={finals}', self.naesin)
        self.assertIn('final-deliverables/${item.id}/original', self.hub)
        self.assertIn('final-deliverables/${item.id}/html', self.hub)
        self.assertNotIn('승인 완성본', self.naesin)

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
