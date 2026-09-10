import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'weekly-bootstrap.db')
import main
import weekly_exam
import workspace as wiki_workspace


class WeeklyExamWorkflowTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='3학년', year=2026, semester='2학기', examType='중간고사')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db_path = str(Path(self.tmp.name) / 'owner.db')
        main.DB_PATH = self.db_path
        self.workspace_token = wiki_workspace.set_current_workspace(
            wiki_workspace.WikiWorkspace('owner-workspace', self.db_path, False))
        self.identity_token = wiki_workspace.set_current_identity(
            wiki_workspace.WikiIdentity('1', 'teacher', 'TUTOR'))
        main.init_db()
        main.migrate_schema()
        weekly_exam.configure(main.get_db, self.context, main._sanitize_exam_html)

    def tearDown(self):
        wiki_workspace.reset_current_identity(self.identity_token)
        wiki_workspace.reset_current_workspace(self.workspace_token)
        self.tmp.cleanup()

    def context(self, school, grade, year, semester, exam):
        return {
            'exam_context': dict(school=school, grade=grade, year=year, semester=semester, exam=exam),
            'digital_twin': {'id': 7, 'version': 3},
            'past_exams': [{'id': 11, 'material_type': 'PAST_EXAM'}],
            'school_prints': [{'id': 12, 'material_type': 'SCHOOL_PRINT'}],
            'ai_work_logs': [{'id': 13, 'material_type': 'AI_WORK_LOG'}],
            'student_errors': [{'id': 14, 'material_type': 'STUDENT_WRONG_NOTE'}],
            'class_insights': [], 'generated_questions': [], 'generated_tests': [],
            'context_budget': {},
        }

    def payload(self, **overrides):
        values = {
            **self.CTX, 'round': 1, 'setType': '40A', 'questionCount': 40,
            'studentPaper': '<h1>신구중3 문제</h1><script>alert(1)</script>',
            'answerExplanation': '<h1>정답</h1><p>1. ③</p>',
            'productionVerification': '# 출제근거\n- 실제 기출 연결',
            'sourceMaterialIds': [11, 12, 13, 14], 'contextStatus': 'LIVING',
        }
        values.update(overrides)
        return weekly_exam.WeeklyExamReviewRequest(**values)

    def test_agent_registration_is_draft_and_snapshot_is_immutable(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        self.assertEqual('DRAFT', saved['status'])
        self.assertEqual('REVIEW', saved['legacy_status'])
        self.assertEqual([11, 12, 13, 14], saved['source_material_ids'])
        self.assertEqual(3, saved['context_snapshot']['digitalTwin']['version'])
        self.assertNotIn('status', weekly_exam.WeeklyExamReviewRequest.model_fields)

    def test_anonymous_can_only_read_safe_final_metadata(self):
        draft = weekly_exam.register_draft(self.payload())
        weekly_exam.transition_weekly_exam(
            draft['id'], weekly_exam.TransitionRequest(action='finalize'), None)
        wiki_workspace.reset_current_identity(self.identity_token)
        self.identity_token = wiki_workspace.set_current_identity(None)

        public = weekly_exam.list_public_weekly_metadata(
            '신구중', '3학년', 2026, '2학기', '중간고사')
        self.assertEqual(1, len(public))
        self.assertEqual(
            {'school', 'grade', 'year', 'semester', 'examType', 'round', 'setType',
             'questionCount', 'available', 'updatedAt'},
            set(public[0]))
        private_calls = [
            lambda: weekly_exam.list_weekly_exams('신구중', '3학년', 2026, '2학기', '중간고사'),
            lambda: weekly_exam.view_artifact(draft['id'], 'student-paper'),
            lambda: weekly_exam.view_artifact(draft['id'], 'answer-explanation'),
            lambda: weekly_exam.view_artifact(draft['id'], 'production-verification'),
            lambda: weekly_exam.view_context_snapshot(draft['id']),
        ]
        for call in private_calls:
            with self.assertRaises(HTTPException) as denied:
                call()
            self.assertEqual(401, denied.exception.status_code)

    def test_owner_can_view_all_private_artifacts_and_snapshot(self):
        saved = weekly_exam.register_draft(self.payload())
        for kind in weekly_exam.ARTIFACTS:
            response = weekly_exam.view_artifact(saved['id'], kind)
            self.assertEqual(200, response.status_code)
            self.assertNotIn('<script', response.body.decode().lower())
            self.assertNotIn('attachment', response.headers.get('content-disposition', '').lower())
        snapshot = weekly_exam.view_context_snapshot(saved['id'])
        self.assertEqual(saved['context_hash'], snapshot['contextHash'])

    def test_other_workspace_cannot_resolve_private_asset(self):
        saved = weekly_exam.register_draft(self.payload())
        other_path = str(Path(self.tmp.name) / 'other.db')
        wiki_workspace.reset_current_workspace(self.workspace_token)
        self.workspace_token = wiki_workspace.set_current_workspace(
            wiki_workspace.WikiWorkspace('other-workspace', other_path, False))
        main.init_db()
        with self.assertRaises(HTTPException) as missing:
            weekly_exam.view_artifact(saved['id'], 'student-paper')
        self.assertEqual(404, missing.exception.status_code)

    def test_owner_finalizes_and_reopens_with_audit(self):
        saved = weekly_exam.register_draft(self.payload())
        final = weekly_exam.transition_weekly_exam(
            saved['id'], weekly_exam.TransitionRequest(action='finalize'), None)
        reopened = weekly_exam.transition_weekly_exam(
            saved['id'], weekly_exam.TransitionRequest(action='reopen'), None)
        self.assertEqual(('FINAL', 'DRAFT'), (final['status'], reopened['status']))
        with main.get_db() as conn:
            actions = [row['action'] for row in conn.execute(
                'SELECT action FROM weekly_exam_audit ORDER BY id').fetchall()]
        self.assertEqual(['CREATED', 'FINALIZED', 'REOPENED'], actions)

    def test_legacy_statuses_are_preserved_and_compatibly_projected(self):
        ids = []
        for round_no, legacy in enumerate(('REVIEW', 'APPROVED', 'FINAL', 'PUBLISHED'), 1):
            saved = weekly_exam.register_draft(self.payload(round=round_no))
            with main.get_db() as conn:
                conn.execute('UPDATE weekly_exam_sets SET status=? WHERE id=?', (legacy, saved['id']))
            ids.append(saved['id'])
        rows = weekly_exam.list_weekly_exams('신구중', '3학년', 2026, '2학기', '중간고사')
        mapped = {row['legacy_status']: row['status'] for row in rows}
        self.assertEqual(
            {'REVIEW': 'DRAFT', 'APPROVED': 'FINAL', 'FINAL': 'FINAL', 'PUBLISHED': 'FINAL'},
            mapped)
        public = weekly_exam.list_public_weekly_metadata(
            '신구중', '3학년', 2026, '2학기', '중간고사')
        self.assertEqual(3, len(public))

    def test_draft_is_not_public_and_bad_source_is_rejected(self):
        weekly_exam.register_draft(self.payload())
        self.assertEqual([], weekly_exam.list_public_weekly_metadata(
            '신구중', '3학년', 2026, '2학기', '중간고사'))
        with self.assertRaises(HTTPException) as raised:
            weekly_exam.register_draft(self.payload(round=2, sourceMaterialIds=[999]))
        self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
