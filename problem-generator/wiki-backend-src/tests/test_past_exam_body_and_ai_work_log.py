"""PAST_EXAM 본문 추출·전달 + AI 작업기록(AI_WORK_LOG) 자료형 계약 테스트.

핵심 회귀 방지:
  - 실제 기출 HTML/MD 업로드 시 본문(body_text)이 MCP Context(past_exams[])에 그대로 실린다
    (metadata만 반환하면 실패). PDF/이미지/HWP(X)는 기존처럼 원본만 보관한다.
  - AI 작업기록 .md 업로드 → get_final_workspace_context()['ai_work_logs'] 에서 본문 재조회,
    다음 세션(새 컨텍스트 조회)에서도 동일하게 읽힌다.
  - 학교 프린트 HTML+JSON workflow 는 그대로(본문/JSON 분리 유지).
  - Context 본문이 상한을 넘으면 조용히 잘리지 않고 body_truncated/body_length 로 명시된다.
"""
import io
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-past-exam-body.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402
import mcp_server  # noqa: E402
import workspace  # noqa: E402

PAST_EXAM_HTML = (
    '<!doctype html><html><head><meta charset="utf-8"><title>2026 1학기 기말</title></head>\n'
    '<body>\n<h1>2026학년도 1학기 기말고사 영어</h1>\n<ol>\n'
    '<li id="q1">1. 다음 글의 빈칸에 들어갈 말로 가장 적절한 것은?</li>\n'
    '<li id="q10">10. 윗글의 밑줄 친 부분과 의미가 가장 가까운 것은?</li>\n'
    '</ol>\n<section id="written">\n'
    '<h2>[서답형 1] 다음 조건에 맞게 문장을 완성하시오.</h2>\n'
    '<p>작성요령: 주어진 단어를 모두 활용하고, 어법에 맞게 8단어 이상으로 쓸 것.</p>\n'
    '</section>\n</body></html>'
).encode('utf-8')

PAST_EXAM_MD = (
    "# 2026 1학기 기말고사 영어 기출\n\n"
    "## 1번\n다음 글의 요지로 가장 적절한 것은?\n\n"
    "## 10번\n밑줄 친 부분의 의미로 알맞은 것은?\n\n"
    "## 서답형 1\n작성요령: 대화의 흐름에 맞게 40자 내외로 서술할 것.\n"
).encode('utf-8')

AI_WORK_LOG_MD = (
    "# 신구중 3학년 2학기 중간 작업기록\n\n"
    "- Digital Twin 분석: 빈칸추론 4문항, 어법 2문항 비중\n"
    "- 학교 프린트 5과 본문에서 변형 지문 3개 확보\n"
    "- 다음 세션 할 일: 서답형 채점기준 확정, 오답노트 2건 반영\n"
).encode('utf-8')


class PastExamBodyTest(unittest.TestCase):
    CTX = dict(school='신구중', grade='3학년', semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        self.identity_token = workspace.set_current_identity(workspace.WikiIdentity('7', 'modu', 'TUTOR'))

    def tearDown(self):
        workspace.reset_current_identity(self.identity_token)
        self.temp_dir.cleanup()

    def _create(self, name, data, content_type, material_type, year=2026):
        return main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], year,
            self.CTX['semester'], self.CTX['exam'], f'{name} 업로드', None,
            UploadFile(filename=name, file=io.BytesIO(data), headers={'content-type': content_type}),
            None, '', '', material_type)

    # ---- PAST_EXAM HTML/MD 본문 ---------------------------------------------------

    def test_html_past_exam_body_reaches_mcp_context_verbatim(self):
        created = self._create('past.html', PAST_EXAM_HTML, 'text/html', 'PAST_EXAM')
        self.assertEqual('PAST_EXAM', created['material_type'])
        self.assertEqual(1, created['has_body'])
        self.assertEqual('html', created['body_format'])

        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        item = ctx['past_exams'][0]
        self.assertTrue(item['has_body'])
        self.assertEqual('html', item['body_format'])
        self.assertFalse(item['body_truncated'])
        body = item['body_text']
        self.assertIn('빈칸에 들어갈 말로 가장 적절한 것은?', body)   # 1번 실제 발문
        self.assertIn('밑줄 친 부분과 의미가 가장 가까운 것은?', body)  # 10번 실제 발문
        self.assertIn('[서답형 1]', body)
        self.assertIn('8단어 이상으로 쓸 것', body)                   # 서답형 1 작성요령

    def test_md_past_exam_body_is_preserved_as_markdown(self):
        self._create('past.md', PAST_EXAM_MD, 'text/markdown', 'PAST_EXAM')
        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        item = ctx['past_exams'][0]
        self.assertEqual('markdown', item['body_format'])
        self.assertIn('## 1번', item['body_text'])
        self.assertIn('## 10번', item['body_text'])
        self.assertIn('## 서답형 1', item['body_text'])
        self.assertIn('40자 내외로 서술할 것', item['body_text'])

    def test_md_extension_is_accepted_and_docx_is_still_rejected(self):
        self._create('past.markdown', PAST_EXAM_MD, 'text/markdown', 'PAST_EXAM')  # no raise
        with self.assertRaises(HTTPException) as raised:
            self._create('generated.docx', b'not-an-exam', 'application/octet-stream', 'PAST_EXAM')
        self.assertEqual(400, raised.exception.status_code)
        self.assertIn('MD', raised.exception.detail)

    def test_binary_past_exam_originals_keep_working_without_a_body(self):
        fixtures = (
            ('exam.pdf', b'%PDF-1.7\n1 0 obj', 'application/pdf'),
            ('exam.jpg', b'\xff\xd8\xff\xe0scan', 'image/jpeg'),
            ('exam.png', b'\x89PNG\r\n\x1a\nscan', 'image/png'),
        )
        for name, data, content_type in fixtures:
            with self.subTest(name=name):
                created = self._create(name, data, content_type, 'PAST_EXAM', year=2025)
                self.assertEqual(0, created['has_body'])
        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        for item in ctx['past_exams']:
            self.assertFalse(item['has_body'])
            self.assertIsNone(item['body_text'])
            self.assertIsNone(item['body_api_path'])
            self.assertTrue(item['original_api_path'])

    def test_oversized_body_is_flagged_not_silently_truncated(self):
        big = ('<p>' + 'A' * (main._CONTEXT_BODY_MAX_CHARS + 5000) + '</p>').encode('utf-8')
        self._create('huge.html', b'<!doctype html><body>' + big + b'</body>', 'text/html', 'PAST_EXAM')
        item = main.get_final_workspace_context(year=2026, **self.CTX)['past_exams'][0]
        self.assertTrue(item['body_truncated'])
        self.assertGreater(item['body_length'], main._CONTEXT_BODY_MAX_CHARS)
        self.assertEqual(main._CONTEXT_BODY_MAX_CHARS, len(item['body_text']))

    def test_past_exam_body_endpoint_returns_the_same_text(self):
        created = self._create('past.md', PAST_EXAM_MD, 'text/markdown', 'PAST_EXAM')
        response = main.open_exam_material_body(created['id'])
        self.assertIn(b'40', response.body)
        self.assertIn('text/plain', response.headers['content-type'])

    # ---- AI 작업기록 -----------------------------------------------------------

    def test_ai_work_log_md_body_is_readable_across_sessions(self):
        created = self._create('worklog.md', AI_WORK_LOG_MD, 'text/markdown', 'AI_WORK_LOG')
        self.assertEqual('AI_WORK_LOG', created['material_type'])

        first = main.get_final_workspace_context(year=2026, **self.CTX)
        self.assertEqual([], [i for i in first['school_prints'] if i['material_type'] == 'AI_WORK_LOG'])
        log = first['ai_work_logs'][0]
        self.assertEqual('ai_work_log', log['type'])
        self.assertEqual('markdown', log['body_format'])
        self.assertIn('다음 세션 할 일: 서답형 채점기준 확정', log['body_text'])

        # 다음 세션 = 새 조회. 저장은 SQLite 파일에 남으므로 그대로 다시 읽혀야 한다.
        again = main.get_final_workspace_context(year=2026, **self.CTX)
        self.assertEqual(log['body_text'], again['ai_work_logs'][0]['body_text'])
        self.assertEqual(created['id'], again['ai_work_logs'][0]['id'])

    def test_ai_work_log_rejects_binary_and_empty_uploads(self):
        with self.assertRaises(HTTPException) as bad_ext:
            self._create('scan.pdf', b'%PDF-1.7', 'application/pdf', 'AI_WORK_LOG')
        self.assertIn('MD, MARKDOWN, TXT, HTML', bad_ext.exception.detail)
        with self.assertRaises(HTTPException):
            self._create('empty.md', b'   \n  ', 'text/markdown', 'AI_WORK_LOG')

    def test_ai_work_log_txt_is_stored_as_text(self):
        created = self._create('log.txt', b'plan: fix grading rubric', 'text/plain', 'AI_WORK_LOG')
        self.assertEqual('text', created['body_format'])
        item = main.get_final_workspace_context(year=2026, **self.CTX)['ai_work_logs'][0]
        self.assertEqual('plan: fix grading rubric', item['body_text'])

    # ---- 회귀 금지: 학교 프린트 / 전체 Context ---------------------------------

    def test_school_print_html_json_workflow_is_unchanged(self):
        import json
        main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], 2026,
            self.CTX['semester'], self.CTX['exam'], '5과 프린트', None,
            None, None, json.dumps({'items': [1, 2]}, ensure_ascii=False),
            '<h1>5과 본문</h1><p>학교 프린트 원문</p>', 'SCHOOL_PRINT')
        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        self.assertEqual(1, len(ctx['school_prints']))
        item = ctx['school_prints'][0]
        self.assertIn('학교 프린트 원문', item['html_text'])
        self.assertEqual({'items': [1, 2]}, item['json_text'])
        self.assertNotIn('body_text', item)  # 학교 프린트 항목 shape 은 그대로

    def test_mcp_get_final_context_tool_returns_the_real_prompts_not_metadata(self):
        self._create('past.html', PAST_EXAM_HTML, 'text/html', 'PAST_EXAM')
        self._create('worklog.md', AI_WORK_LOG_MD, 'text/markdown', 'AI_WORK_LOG')
        # MCP tool 진입점을 그대로 호출한다 (mcp_server.get_final_context -> main.get_final_workspace_context).
        ctx = mcp_server.get_final_context(
            school=self.CTX['school'], grade=self.CTX['grade'], year=2026,
            semester=self.CTX['semester'], exam=self.CTX['exam'])
        past = ctx['past_exams'][0]
        self.assertIn('빈칸에 들어갈 말로 가장 적절한 것은?', past['body_text'])
        self.assertIn('밑줄 친 부분과 의미가 가장 가까운 것은?', past['body_text'])
        self.assertIn('8단어 이상으로 쓸 것', past['body_text'])
        self.assertIn('다음 세션 할 일', ctx['ai_work_logs'][0]['body_text'])

    def test_full_context_carries_every_source_together(self):
        import json
        main.import_digital_twin(main.ImportedTwinProfile(
            school=self.CTX['school'], grade=self.CTX['grade'], exam=self.CTX['exam'], dna='빈칸추론 중심'))
        self._create('past.html', PAST_EXAM_HTML, 'text/html', 'PAST_EXAM')
        self._create('worklog.md', AI_WORK_LOG_MD, 'text/markdown', 'AI_WORK_LOG')
        main.create_exam_material(
            'school_print', self.CTX['school'], self.CTX['grade'], 2026,
            self.CTX['semester'], self.CTX['exam'], '프린트', None, None, None,
            json.dumps({'a': 1}, ensure_ascii=False), '<p>school print body</p>', 'SCHOOL_PRINT')
        main.create_educator_insight(main.EducatorInsightCreate(
            content='서답형 채점 엄격히', school=self.CTX['school'], grade=self.CTX['grade'],
            year=2026, semester=self.CTX['semester'], exam=self.CTX['exam'], source='school_teacher'))

        ctx = main.get_final_workspace_context(year=2026, **self.CTX)
        self.assertIsNotNone(ctx['digital_twin'])
        self.assertTrue(ctx['past_exams'][0]['body_text'])
        self.assertTrue(ctx['ai_work_logs'][0]['body_text'])
        self.assertIn('school print body', ctx['school_prints'][0]['html_text'])
        self.assertEqual('school_teacher', ctx['class_insights'][0]['source'])


if __name__ == '__main__':
    unittest.main()
