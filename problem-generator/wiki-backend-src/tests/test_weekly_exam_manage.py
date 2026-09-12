import hashlib
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'weekly-manage.db')
import main
import weekly_exam
import workspace as wiki_workspace


def _sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


class WeeklyExamManageTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='2학년', year=2026, semester='2학기', examType='중간고사')

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
            'digital_twin': {'id': 2, 'version': 1},
            'past_exams': [{'id': 49, 'material_type': 'PAST_EXAM'}],
            'school_prints': [{'id': 11, 'material_type': 'SCHOOL_PRINT'}],
            'ai_work_logs': [],
            'student_errors': [],
            'class_insights': [], 'generated_questions': [], 'generated_tests': [],
            'context_budget': {},
        }

    def payload(self, **overrides):
        values = {
            **self.CTX, 'round': 4, 'setType': '70', 'questionCount': 70,
            'studentPaper': '<h1>문제지</h1><p>Q1</p>',
            'answerExplanation': '<h1>해설</h1><p>1. ①</p>',
            'productionVerification': '# 근거\n- v1',
            'sourceMaterialIds': [49, 11], 'contextStatus': 'LIVING',
        }
        values.update(overrides)
        return weekly_exam.WeeklyExamReviewRequest(**values)

    def update_payload(self, **overrides):
        values = {
            'studentPaper': '<h1>문제지</h1><p>Q1 v2</p>',
            'answerExplanation': '<h1>해설</h1><p>1. ②</p>',
            'productionVerification': '# 근거\n- v2',
        }
        values.update(overrides)
        return weekly_exam.WeeklyExamArtifactsUpdate(**values)

    def hashes(self, exam_id):
        with main.get_db() as conn:
            row = conn.execute(
                'SELECT student_paper_hash,answer_explanation_hash,verification_hash FROM weekly_exam_sets WHERE id=?',
                (exam_id,)).fetchone()
            return tuple(row) if row else None

    def audit_count(self, exam_id):
        with main.get_db() as conn:
            return conn.execute('SELECT COUNT(*) c FROM weekly_exam_audit WHERE weekly_exam_id=?',
                                (exam_id,)).fetchone()['c']

    def test_draft_delete_success_removes_set_and_audit(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        out = weekly_exam.delete_weekly_exam(saved['id'])
        self.assertEqual(saved['id'], out['id'])
        self.assertEqual(4, out['round'])
        self.assertEqual([], weekly_exam.list_weekly_exams(
            '신구중', '2학년', 2026, '2학기', '중간고사'))
        self.assertIsNone(self.hashes(saved['id']))
        self.assertEqual(0, self.audit_count(saved['id']))
        with self.assertRaises(HTTPException) as missing:
            weekly_exam.view_artifact(saved['id'], 'student-paper')
        self.assertEqual(404, missing.exception.status_code)

    def test_final_delete_is_rejected(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        weekly_exam.transition_weekly_exam(
            saved['id'], weekly_exam.TransitionRequest(action='finalize'), None)
        with self.assertRaises(HTTPException) as denied:
            weekly_exam.delete_weekly_exam(saved['id'])
        self.assertEqual(409, denied.exception.status_code)
        self.assertEqual(1, len(weekly_exam.list_weekly_exams(
            '신구중', '2학년', 2026, '2학기', '중간고사')))

    def test_missing_delete_is_404(self):
        with self.assertRaises(HTTPException) as missing:
            weekly_exam.delete_weekly_exam(9999)
        self.assertEqual(404, missing.exception.status_code)

    def test_draft_replace_keeps_id_status_and_snapshot(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        before_hashes = self.hashes(saved['id'])
        before_snapshot = weekly_exam.view_context_snapshot(saved['id'])['contextHash']
        out = weekly_exam.replace_weekly_exam_artifacts(saved['id'], self.update_payload())
        self.assertEqual(saved['id'], out['id'])
        self.assertEqual('DRAFT', out['status'])
        self.assertEqual(saved['version'], out['version'])
        self.assertEqual(saved['round'], out['round'])
        self.assertEqual(saved['set_type'], out['set_type'])
        after_hashes = self.hashes(saved['id'])
        self.assertNotEqual(before_hashes, after_hashes)
        self.assertEqual(_sha('<h1>문제지</h1><p>Q1 v2</p>'), after_hashes[0])
        self.assertEqual(
            before_snapshot, weekly_exam.view_context_snapshot(saved['id'])['contextHash'])
        body = weekly_exam.view_artifact(saved['id'], 'student-paper').body.decode()
        self.assertIn('Q1 v2', body)
        md = weekly_exam.view_artifact(saved['id'], 'production-verification').body.decode()
        self.assertIn('v2', md)

    def test_final_replace_is_rejected(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        weekly_exam.transition_weekly_exam(
            saved['id'], weekly_exam.TransitionRequest(action='finalize'), None)
        with self.assertRaises(HTTPException) as denied:
            weekly_exam.replace_weekly_exam_artifacts(saved['id'], self.update_payload())
        self.assertEqual(409, denied.exception.status_code)
        body = weekly_exam.view_artifact(saved['id'], 'student-paper').body.decode()
        self.assertIn('Q1</p>', body)

    def test_replace_with_unknown_source_ref_is_rejected_without_change(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        before_hashes = self.hashes(saved['id'])
        with self.assertRaises(HTTPException) as bad:
            weekly_exam.replace_weekly_exam_artifacts(
                saved['id'], self.update_payload(sourceMaterialIds=[9999]))
        self.assertEqual(400, bad.exception.status_code)
        self.assertEqual(before_hashes, self.hashes(saved['id']))

    def test_missing_replace_is_404(self):
        with self.assertRaises(HTTPException) as missing:
            weekly_exam.replace_weekly_exam_artifacts(9999, self.update_payload())
        self.assertEqual(404, missing.exception.status_code)

    def test_deleted_key_can_be_registered_again_without_duplicate(self):
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        weekly_exam.delete_weekly_exam(saved['id'])
        again = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        self.assertNotEqual(saved['id'], again['id'])
        rows = weekly_exam.list_weekly_exams('신구중', '2학년', 2026, '2학기', '중간고사')
        self.assertEqual(1, len(rows))
        self.assertEqual('DRAFT', rows[0]['status'])


if __name__ == '__main__':
    unittest.main()
