import os
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote

from fastapi import HTTPException

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'weekly-artifact-html.db')
import main
import weekly_exam
import workspace as wiki_workspace

CANONICAL_PAPER = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>신구중2 2학기 중간고사 대비 4회 40문항</title>
<style>@page { size: A4 portrait; margin: 0; }
.page { width: 210mm; height: 297mm; }
.cols { display: flex; }
.qblock { page-break-inside: avoid; }</style>
</head>
<body>
<div class="page"><div class="cols"><div class="col"><div class="qblock" data-qnum="1">
<div class="qhead"><span class="qnum">1.</span></div>
</div></div></div></div>
<script>alert(1)</script>
</body>
</html>"""


class WeeklyExamArtifactHtmlTest(unittest.TestCase):
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
            'school_prints': [{'id': 11, 'material_type': 'SCHOOL_PRINT'},
                              {'id': 12, 'material_type': 'SCHOOL_PRINT'}],
            'ai_work_logs': [],
            'student_errors': [],
            'class_insights': [], 'generated_questions': [], 'generated_tests': [],
            'context_budget': {},
        }

    def payload(self, **overrides):
        values = {
            **self.CTX, 'round': 4, 'setType': '40', 'questionCount': 40,
            'studentPaper': CANONICAL_PAPER,
            'answerExplanation': CANONICAL_PAPER.replace('40문항</title>', '40문항 정답해설</title>'),
            'productionVerification': '# 출제근거\n- 실제 학습지 연결',
            'sourceMaterialIds': [11, 12], 'contextStatus': 'LIVING',
        }
        values.update(overrides)
        return weekly_exam.WeeklyExamReviewRequest(**values)

    def stored_bodies(self, exam_id):
        with main.get_db() as conn:
            row = conn.execute(
                'SELECT student_paper_html,answer_explanation_html FROM weekly_exam_sets WHERE id=?',
                (exam_id,)).fetchone()
        return row['student_paper_html'], row['answer_explanation_html']

    def test_registered_paper_html_is_preserved_canonical(self):
        # given: A4/print CSS와 2단 class를 포함한 완성 HTML + script 1개
        # when: DRAFT 등록
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        # then: style/class/문서구조는 그대로, script만 제거되어 저장된다
        paper, explanation = self.stored_bodies(saved['id'])
        for token in ('<style', '@page', 'class="page"', 'class="cols"', 'class="qblock"',
                      'page-break-inside', '<html', '40문항</title>'):
            self.assertIn(token, paper, token)
        self.assertNotIn('<script', paper.lower())
        self.assertNotIn('<script', explanation.lower())
        self.assertIn('40문항 정답해설</title>', explanation)

    def test_view_renders_stored_html_raw(self):
        # given: 등록된 DRAFT
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        paper, _ = self.stored_bodies(saved['id'])
        # when: 문제지 보기
        response = weekly_exam.view_artifact(saved['id'], 'student-paper')
        # then: 저장 canonical과 동일한 바이트를 text/html로 반환한다
        self.assertEqual(200, response.status_code)
        self.assertIn('text/html', response.headers['content-type'])
        self.assertEqual(paper.encode('utf-8'), response.body)
        self.assertNotIn('markdown-view', response.body.decode('utf-8'))

    def test_view_and_download_bytes_are_identical(self):
        # given: 등록된 DRAFT
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        # when: 보기와 다운로드
        viewed = weekly_exam.view_artifact(saved['id'], 'answer-explanation')
        downloaded = weekly_exam.download_artifact(saved['id'], 'answer-explanation')
        # then: 세 바이트(저장/보기/다운로드)가 동일하다
        _, stored = self.stored_bodies(saved['id'])
        self.assertEqual(stored.encode('utf-8'), viewed.body)
        self.assertEqual(stored.encode('utf-8'), downloaded.body)

    def test_download_returns_attachment_with_utf8_filename(self):
        # given: 등록된 DRAFT
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        # when: 문제지 다운로드
        response = weekly_exam.download_artifact(saved['id'], 'student-paper')
        # then: attachment + 한글 파일명(filename*) + 동일 바이트
        self.assertEqual(200, response.status_code)
        self.assertIn('text/html', response.headers['content-type'])
        disposition = response.headers['content-disposition']
        self.assertIn('attachment', disposition)
        self.assertIn("filename*=UTF-8''", disposition)
        encoded = disposition.split("filename*=UTF-8''", 1)[1]
        self.assertIn('신구중', unquote(encoded))
        paper, _ = self.stored_bodies(saved['id'])
        self.assertEqual(paper.encode('utf-8'), response.body)

    def test_finalize_keeps_canonical_bytes(self):
        # given: 등록된 DRAFT
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        before, _ = self.stored_bodies(saved['id'])
        # when: FINAL 확정
        weekly_exam.transition_weekly_exam(
            saved['id'], weekly_exam.TransitionRequest(action='finalize'), None)
        # then: FINAL 보기 바이트가 등록 당시와 동일하다
        response = weekly_exam.view_artifact(saved['id'], 'student-paper')
        self.assertEqual(before.encode('utf-8'), response.body)

    def test_seventy_set_artifact_round_trip(self):
        # given: 70문항 세트 등록
        saved = weekly_exam.register_draft(
            self.payload(round=2, setType='70', questionCount=70), actor='mcp-agent')
        # when: 보기와 다운로드
        viewed = weekly_exam.view_artifact(saved['id'], 'student-paper')
        downloaded = weekly_exam.download_artifact(saved['id'], 'student-paper')
        # then: 동일 바이트가 오간다
        self.assertEqual(viewed.body, downloaded.body)
        self.assertIn('attachment', downloaded.headers['content-disposition'])

    def test_anonymous_view_and_download_are_blocked(self):
        # given: 등록된 DRAFT 후 비로그인 상태
        saved = weekly_exam.register_draft(self.payload(), actor='mcp-agent')
        wiki_workspace.reset_current_identity(self.identity_token)
        self.identity_token = wiki_workspace.set_current_identity(None)
        # when/then: 보기·다운로드 모두 401
        for call in (lambda: weekly_exam.view_artifact(saved['id'], 'student-paper'),
                     lambda: weekly_exam.download_artifact(saved['id'], 'student-paper'),
                     lambda: weekly_exam.download_artifact(saved['id'], 'answer-explanation')):
            with self.assertRaises(HTTPException) as denied:
                call()
            self.assertEqual(401, denied.exception.status_code)


if __name__ == '__main__':
    unittest.main()
