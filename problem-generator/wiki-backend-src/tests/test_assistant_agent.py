import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-assistant-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


class AssistantAgentTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'assistant.db')
        main.init_db()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _request(self, message, state=None, attachments=None):
        return main.AssistantRequest(
            message=message,
            state=main.AssistantState(**(state or {})),
            attachments=[main.AssistantAttachment(**item) for item in (attachments or [])],
        )

    def test_a_existing_twin_is_explained_before_generator_link(self):
        now = '2026.08.23 12:00'
        with main.get_db() as conn:
            cursor = conn.execute(
                'INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)',
                ('신구중', '중2', '중간고사', '{}', 2, now, now),
            )
        result = main.assistant_chat(self._request('신구중 2학년 중간고사 문제 만들고 싶어'))
        self.assertEqual(cursor.lastrowid, result['state']['selected_twin_id'])
        self.assertIn('Digital Twin을 확인했습니다', result['text'])
        self.assertIn('지문이나 학교 프린트', result['text'])
        self.assertEqual('home', result['page'])

    def test_b_school_without_twin_explains_import_need(self):
        result = main.assistant_chat(self._request('새봄중 1학년 기말고사 문제를 만들고 싶어'))
        self.assertIn('Digital Twin을 찾지 못했습니다', result['text'])
        self.assertIn('기출 또는 Digital Twin', result['text'])

    def test_c_attachment_uses_existing_upload_tool(self):
        pdf = main.assistant_chat(self._request('이 학교 프린트로 문제 만들자', attachments=[{'name': '시험범위.pdf', 'type': 'application/pdf'}]))
        twin = main.assistant_chat(self._request('이 자료를 적용해줘', attachments=[{'name': 'profile.json', 'type': 'application/json'}]))
        wrong = main.assistant_chat(self._request('학생 오답 자료야', attachments=[{'name': 'wrong.jpg', 'type': 'image/jpeg'}]))
        self.assertEqual(('school', '학교 프린트 OCR 열기'), (pdf['page'], pdf['action']))
        self.assertEqual('twin', twin['page'])
        self.assertEqual('wrongnote', wrong['page'])

    def test_d_follow_up_remembers_school_grade_and_exam(self):
        first = main.assistant_chat(self._request('신구중 2학년 중간고사 문제 만들고 싶어'))
        follow = main.assistant_chat(self._request('지문은 다음에 줄게', state=first['state']))
        self.assertEqual(('신구중', '중2', '중간고사'), (follow['state']['school'], follow['state']['grade'], follow['state']['exam']))
        self.assertIn('신구중 중2 중간고사', follow['text'])

    def test_e_model_failure_returns_rules_fallback(self):
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'server-only-key'}), patch.object(main.pl, 'call_gemini_text', side_effect=RuntimeError('offline')):
            result = main.assistant_chat(self._request('신구중 2학년 중간고사 문제 만들고 싶어'))
        self.assertEqual('rules-fallback', result['model'])
        self.assertIn('문제 제작 화면', result['text'])

    def test_f_compact_school_grade_parses_and_returns_real_twin_context(self):
        now = '2026.08.24 12:00'
        profile = '{"types":[{"name":"어법"},{"name":"서술형"}],"rules":["가정법 우선"]}'
        with main.get_db() as conn:
            cursor = conn.execute(
                'INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)',
                ('압구정중', '중3', '2학기 기말고사', profile, 3, now, now),
            )
        result = main.assistant_chat(self._request('압구정중3 문제 제작을 하려면'))
        self.assertEqual(('압구정중', '중3'), (result['state']['school'], result['state']['grade']))
        self.assertEqual(cursor.lastrowid, result['state']['selected_twin_id'])
        self.assertEqual(['어법', '서술형'], result['context']['questionTypes'])
        self.assertIn('시험 범위 주요 문법 사항', result['state']['needed_materials'])
        self.assertEqual(('home', '문제 제작 화면 열기'), (result['page'], result['action']))

    def test_g_request_api_key_uses_qwen_before_fallback(self):
        with patch.object(main, 'call_openrouter', return_value=('확인된 기억에 맞춘 실제 Agent 응답입니다.', 'qwen/qwen3.7-flash')) as qwen:
            request = self._request('압구정중3 문제 제작을 하려면')
            request.api_key = 'browser-key'
            result = main.assistant_chat(request)
        self.assertEqual('qwen/qwen3.7-flash', result['model'])
        self.assertEqual('browser-key', qwen.call_args.args[0])
        self.assertEqual('확인된 기억에 맞춘 실제 Agent 응답입니다.', result['text'])


if __name__ == '__main__':
    unittest.main()
