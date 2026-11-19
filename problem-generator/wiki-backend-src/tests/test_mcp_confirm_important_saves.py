"""중요한 Wiki 저장(FINAL·직보·Weekly Exam 등록)은 사용자 확인(confirm_token) 뒤에만 한다 (2026-11-19)."""
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-confirm.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())
import mcp_server  # noqa: E402

CTX = dict(school='가짜중', grade='중2', year=2026, semester='2학기', exam='기말고사')


class ConfirmImportantSavesTest(unittest.TestCase):

    def setUp(self):
        self.env = mock.patch.dict(os.environ, {mcp_server.MCP_API_KEY_ENV: 'test-key'})
        self.env.start()
        self.saved = []
        self.patch = mock.patch.object(mcp_server, '_save_deliverable',
                                       side_effect=lambda *a: self.saved.append(a) or {'id': 1})
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.env.stop()

    def test_given_no_token_when_save_final_then_not_saved_and_summary_with_token(self):
        # given / when
        out = mcp_server.save_final(**CTX, title='기말 FINAL', html_text='<p>x</p>')
        # then
        self.assertEqual(out['status'], 'needs_confirmation')
        self.assertFalse(out['saved'])
        self.assertIn('가짜중 중2 2026년 2학기 기말고사 FINAL 완성본 저장', out['summary'])
        self.assertTrue(out['confirm_token'])
        self.assertEqual(self.saved, [])

    def test_given_same_values_and_token_when_save_final_then_saved_once(self):
        # given
        first = mcp_server.save_final(**CTX, html_text='<p>x</p>')
        # when
        out = mcp_server.save_final(**CTX, html_text='<p>x</p>', confirm_token=first['confirm_token'])
        # then
        self.assertEqual(out, {'id': 1})
        self.assertEqual(len(self.saved), 1)
        self.assertEqual(self.saved[0][0], 'final')

    def test_given_changed_content_when_confirm_then_rejected_and_not_saved(self):
        # given
        first = mcp_server.save_jikbo(**CTX, html_text='<p>확인한 내용</p>')
        # when / then
        with self.assertRaisesRegex(ValueError, '다릅니다'):
            mcp_server.save_jikbo(**CTX, html_text='<p>바뀐 내용</p>', confirm_token=first['confirm_token'])
        with self.assertRaisesRegex(ValueError, '다릅니다'):
            mcp_server.save_final(**CTX, html_text='<p>확인한 내용</p>', confirm_token=first['confirm_token'])
        self.assertEqual(self.saved, [])

    def test_given_expired_or_broken_token_when_confirm_then_rejected(self):
        # given
        first = mcp_server.save_final(**CTX, html_text='<p>x</p>')
        later = time.time() + mcp_server.CONFIRM_TTL_SECONDS + 5
        # when / then
        with mock.patch.object(mcp_server.time, 'time', return_value=later):
            with self.assertRaisesRegex(ValueError, '30분'):
                mcp_server.save_final(**CTX, html_text='<p>x</p>', confirm_token=first['confirm_token'])
        with self.assertRaisesRegex(ValueError, '형식'):
            mcp_server.save_final(**CTX, html_text='<p>x</p>', confirm_token='garbage')
        self.assertEqual(self.saved, [])

    def test_given_no_server_key_when_save_final_then_refused(self):
        # given
        with mock.patch.dict(os.environ, {mcp_server.MCP_API_KEY_ENV: ''}):
            # when / then
            with self.assertRaisesRegex(ValueError, '인증 키'):
                mcp_server.save_final(**CTX, html_text='<p>x</p>')

    def test_given_weekly_exam_when_no_token_then_not_registered(self):
        # given
        with mock.patch('weekly_exam.register_draft') as register:
            # when
            out = mcp_server.register_weekly_exam_draft(
                school='가짜중', grade='중2', year=2026, semester='2학기', exam_type='기말고사', round=1,
                set_type='STD', question_count=20, student_paper_html='<html>p</html>',
                answer_explanation_html='<html>a</html>')
            # then
            self.assertEqual(out['status'], 'needs_confirmation')
            self.assertIn('1회차 STD Weekly Exam 등록', out['summary'])
            register.assert_not_called()


if __name__ == '__main__':
    unittest.main()
