"""Binary preservation and HTML compatibility for weekly paper uploads."""
import base64
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
import weekly_exam
import workspace

PDF = b'%PDF-1.7\n%\xe2\xe3\xcf\xd3\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n'
PDF_DATA = weekly_exam.PDF_PREFIX + base64.b64encode(PDF).decode('ascii')


class WeeklyExamPdfTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = str(Path(self.tmp.name) / 'weekly.db')
        self.identity = workspace.set_current_identity(workspace.WikiIdentity('1', 'teacher', 'TUTOR'))
        self.scope = workspace.set_current_workspace(workspace.WikiWorkspace('pdf-owner', self.path, False))
        with self.db() as conn:
            weekly_exam.init_schema(conn)
        weekly_exam.configure(self.db, lambda *args: {}, lambda value: value)
        app = FastAPI()
        app.include_router(weekly_exam.router)
        self.client = TestClient(app)

    def db(self):
        conn = sqlite3.connect(self.path)
        conn.row_factory = sqlite3.Row
        return conn

    def tearDown(self):
        self.client.close()
        workspace.reset_current_workspace(self.scope)
        workspace.reset_current_identity(self.identity)
        self.tmp.cleanup()

    def payload(self, **overrides):
        values = dict(school='압구정중', grade='2학년', year=2026, semester='2학기',
                      examType='기말고사', round=1, questionCount=40,
                      studentPaper=PDF_DATA, studentPaperName='문제지.PDF',
                      answerExplanation=PDF_DATA, answerExplanationName='정답해설.pdf',
                      productionVerification='# 출제검수')
        values.update(overrides)
        return values

    def test_given_pdf_when_register_view_download_then_preserve_all_binary_bytes(self):
        created = self.client.post('/api/weekly-exams/draft', json=self.payload())
        self.assertEqual(201, created.status_code, created.text)
        row = created.json()
        self.assertEqual('DRAFT', row['status'])
        for kind in ('student-paper', 'answer-explanation'):
            for suffix in ('', '/download'):
                response = self.client.get(f"/api/weekly-exams/{row['id']}/artifacts/{kind}{suffix}")
                self.assertEqual(200, response.status_code)
                self.assertEqual(PDF, response.content)
                self.assertEqual('application/pdf', response.headers['content-type'])
                self.assertIn('.pdf', response.headers['content-disposition'])
                self.assertEqual('no-store', response.headers['cache-control'])
        self.assertNotIn(PDF_DATA, str(row))

    def test_given_mixed_pdf_html_when_register_then_keep_html_layout_and_clean_script(self):
        html = '<!doctype html><style>@page{size:A4}</style><h1>해설</h1><script>alert(1)</script>'
        row = weekly_exam.register_draft(weekly_exam.WeeklyExamReviewRequest(**self.payload(
            answerExplanation=html, answerExplanationName='해설.html')))
        response = weekly_exam.view_artifact(row['id'], 'answer-explanation')
        self.assertIn(b'@page{size:A4}', response.body)
        self.assertNotIn(b'<script>', response.body)
        self.assertTrue(response.headers['content-type'].startswith('text/html'))

    def test_given_html_draft_when_replace_with_pdf_then_other_artifacts_stay_unchanged(self):
        row = weekly_exam.register_draft(weekly_exam.WeeklyExamReviewRequest(**self.payload(
            studentPaper='<h1>기존 문제지</h1>', studentPaperName='paper.html')))
        updated = self.client.put(f"/api/weekly-exams/{row['id']}/artifacts", json={
            'studentPaper': PDF_DATA, 'studentPaperName': 'new.pdf'})
        self.assertEqual(200, updated.status_code, updated.text)
        self.assertEqual('new.pdf', updated.json()['student_paper_name'])
        self.assertEqual(row['answer_explanation_hash'], updated.json()['answer_explanation_hash'])
        self.assertEqual(row['verification_hash'], updated.json()['verification_hash'])
        self.assertEqual(PDF, weekly_exam.view_artifact(row['id'], 'student-paper').body)
        # Switching back to HTML must restore HTML MIME and extension too.
        weekly_exam.replace_artifacts(row['id'], weekly_exam.WeeklyExamArtifactsUpdate(
            studentPaper='<h1>수정 문제지</h1>', studentPaperName='new.html'))
        self.assertTrue(weekly_exam.view_artifact(row['id'], 'student-paper').headers['content-type'].startswith('text/html'))

    def test_given_final_when_replace_pdf_then_old_version_is_preserved(self):
        row = weekly_exam.register_draft(weekly_exam.WeeklyExamReviewRequest(**self.payload()))
        with self.db() as conn:
            conn.execute("UPDATE weekly_exam_sets SET status='FINAL' WHERE id=?", (row['id'],))
        replacement = b'%PDF-1.7\n%replacement\n%%EOF\n'
        data = weekly_exam.PDF_PREFIX + base64.b64encode(replacement).decode('ascii')
        new = weekly_exam.replace_artifacts(row['id'], weekly_exam.WeeklyExamArtifactsUpdate(
            studentPaper=data, studentPaperName='revision.pdf'))
        self.assertEqual('DRAFT', new['status'])
        self.assertEqual(2, new['version'])
        self.assertEqual(PDF, weekly_exam.view_artifact(row['id'], 'student-paper').body)
        self.assertEqual(replacement, weekly_exam.view_artifact(new['id'], 'student-paper').body)

    def test_given_invalid_pdf_when_register_then_reject_without_a_saved_set(self):
        for content in (weekly_exam.PDF_PREFIX + '%%%invalid',
                        weekly_exam.PDF_PREFIX + base64.b64encode(b'<html>fake</html>').decode(),
                        '<h1>HTML with PDF extension</h1>', 'data:text/html;base64,PHNjcmlwdD4='):
            with self.subTest(content=content[:50]):
                response = self.client.post('/api/weekly-exams/draft', json=self.payload(studentPaper=content))
                self.assertEqual(400, response.status_code)
        with self.db() as conn:
            self.assertEqual(0, conn.execute('SELECT COUNT(*) FROM weekly_exam_sets').fetchone()[0])
        with patch.object(weekly_exam, 'MAX_PAPER_BYTES', 5):
            with self.assertRaises(HTTPException):
                weekly_exam._clean_paper(PDF_DATA, 'too-large.pdf')

    def test_given_anonymous_when_viewing_pdf_then_require_the_same_operator_login(self):
        row = weekly_exam.register_draft(weekly_exam.WeeklyExamReviewRequest(**self.payload()))
        token = workspace.set_current_identity(None)
        try:
            response = self.client.get(f"/api/weekly-exams/{row['id']}/artifacts/student-paper")
            self.assertEqual(401, response.status_code)
        finally:
            workspace.reset_current_identity(token)

    def test_given_no_verification_when_registering_then_save_papers_without_a_fake_md(self):
        for round_, value in enumerate(('omitted', None, '', '   \n'), start=1):
            with self.subTest(value=value):
                payload = self.payload(round=round_)
                if value == 'omitted':
                    payload.pop('productionVerification')
                else:
                    payload['productionVerification'] = value
                response = self.client.post('/api/weekly-exams/draft', json=payload)
                self.assertEqual(201, response.status_code, response.text)
                row = response.json()
                self.assertEqual('', row['verification_name'])
                self.assertEqual('', row['verification_hash'])
                self.assertEqual(PDF, weekly_exam.view_artifact(row['id'], 'student-paper').body)
                updated = weekly_exam.replace_artifacts(row['id'], weekly_exam.WeeklyExamArtifactsUpdate(
                    studentPaper=PDF_DATA, studentPaperName='revision.pdf'))
                self.assertEqual('', updated['verification_name'])
                self.assertEqual('', updated['verification_hash'])
                for suffix in ('', '/download'):
                    absent = self.client.get(f"/api/weekly-exams/{row['id']}/artifacts/production-verification{suffix}")
                    self.assertEqual(404, absent.status_code)

    def test_given_optional_md_when_supplied_then_preserve_the_original_name_and_body(self):
        verification = '  # 제작·검수\n\n- 원문을 그대로 보관합니다.\n'
        created = self.client.post('/api/weekly-exams/draft', json=self.payload(
            productionVerification=verification, productionVerificationName='출제검수.md'))
        self.assertEqual(201, created.status_code, created.text)
        row = created.json()
        self.assertEqual('출제검수.md', row['verification_name'])
        response = self.client.get(f"/api/weekly-exams/{row['id']}/artifacts/production-verification/download")
        self.assertEqual(verification.encode('utf-8'), response.content)


if __name__ == '__main__':
    unittest.main()
