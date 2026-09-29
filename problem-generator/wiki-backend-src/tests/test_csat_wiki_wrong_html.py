"""WIKI ③ 학생 오답 RAW HTML — ADMIN 업로드/목록/보기/삭제 계약.

핵심 검증은 "업로드 시 아무 분석도 하지 않는다": 원문 바이트가 그대로 보존되고,
문항 row·question_id 연결·csat_wrong_answers 구조화가 발생하지 않는다.
"""

import hashlib
import hmac
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from fastapi.testclient import TestClient

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False); tmp.close()
os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
import main


def signed_headers(role, user_id, login_id):
    timestamp = str(int(time.time()))
    identity = main.wiki_workspace.WikiIdentity(user_id, login_id, role)
    payload = main.wiki_workspace.signature_payload(identity, timestamp)
    signature = hmac.new(os.environ['WIKI_IDENTITY_SECRET'].encode(), payload, hashlib.sha256).hexdigest()
    return {'x-buildit-user-id': user_id, 'x-buildit-login-id': login_id, 'x-buildit-role': role,
            'x-buildit-timestamp': timestamp, 'x-buildit-signature': signature}


RAW_HTML = b'<html><body><p>25\x052 20\x052 \xd50c \xc0\xcc\xb7\xc1 \xd6d\xc7\xd1</p><script>alert(1)</script></body></html>'


class CsatWikiWrongHtmlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
        main.DB_PATH = Path(tmp.name)
        main.init_db()
        main.migrate_schema()
        cls.client = TestClient(main.app)

    def setUp(self):
        with main.get_db() as conn:
            conn.execute('DELETE FROM csat_wrong_html')
            conn.execute('DELETE FROM students')
        student = self.client.post('/api/students', json={'name': '김테스트'})
        self.assertIn(student.status_code, (200, 201))
        self.student_id = student.json()['id']

    def upload(self, filename='wrong-20260929.html', data=RAW_HTML, content_type='text/html', **form):
        return self.client.post('/api/csat/wrong-html',
                                data={'student_id': self.student_id, **form},
                                files={'file': (filename, data, content_type)})

    def test_upload_stores_raw_bytes_only(self):
        # given/when: 기존 학생을 골라 .html 원문을 올린다
        response = self.upload(title='9월 모평 오답')
        # then: 201 + 메타만 저장(학생이름·파일명·제목·등록일), 바이트는 /raw에서 그대로 복원
        self.assertEqual(201, response.status_code)
        item = response.json()
        self.assertEqual(self.student_id, item['student_id'])
        self.assertEqual('김테스트', item['student_name'])
        self.assertEqual('wrong-20260929.html', item['original_name'])
        self.assertEqual('9월 모평 오답', item['title'])
        self.assertTrue(item['created_at'])
        self.assertNotIn('original_blob', item)
        raw = self.client.get(f"/api/csat/wrong-html/{item['id']}/raw")
        self.assertEqual(200, raw.status_code)
        self.assertEqual(RAW_HTML, raw.content)
        self.assertTrue(raw.headers['content-type'].startswith('text/plain'))

    def test_upload_does_not_parse_or_link(self):
        # given: 오답 HTML을 올리기 전과 후의 구조화 테이블 row 수를 비교한다
        with main.get_db() as conn:
            before = {t: conn.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0]
                      for t in ('csat_wrong_answers', 'csat_questions', 'csat_question_dna')}
        response = self.upload()
        self.assertEqual(201, response.status_code)
        with main.get_db() as conn:
            after = {t: conn.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0]
                     for t in ('csat_wrong_answers', 'csat_questions', 'csat_question_dna')}
        # then: 문항 row 생성·question_id/DNA·TRAP 연결·오답 구조화가 전혀 일어나지 않는다
        self.assertEqual(before, after)
        listed = self.client.get('/api/csat/wrong-html').json()
        self.assertEqual([{'id', 'student_id', 'student_name', 'title', 'original_name',
                           'original_type', 'created_at'},], [set(row) for row in listed])

    def test_upload_validation(self):
        self.assertEqual(400, self.upload(filename='notes.pdf', content_type='application/pdf').status_code)
        self.assertEqual(400, self.upload(data=b'').status_code)
        missing = self.client.post('/api/csat/wrong-html', data={'student_id': 999999},
                                   files={'file': ('a.htm', RAW_HTML, 'text/html')})
        self.assertEqual(404, missing.status_code)
        self.assertEqual(0, len(self.client.get('/api/csat/wrong-html').json()))

    def test_delete_roundtrip(self):
        item = self.upload().json()
        self.assertEqual(204, self.client.delete(f"/api/csat/wrong-html/{item['id']}").status_code)
        self.assertEqual([], self.client.get('/api/csat/wrong-html').json())
        self.assertEqual(404, self.client.delete(f"/api/csat/wrong-html/{item['id']}").status_code)

    def test_writes_are_admin_only(self):
        tutor = signed_headers('TUTOR', '901', 'tutor-wrong-html')
        blocked = self.client.post('/api/csat/wrong-html', data={'student_id': self.student_id},
                                   files={'file': ('a.html', RAW_HTML, 'text/html')}, headers=tutor)
        self.assertEqual(403, blocked.status_code)
        item = self.upload().json()
        self.assertEqual(403, self.client.delete(f"/api/csat/wrong-html/{item['id']}", headers=tutor).status_code)
        self.assertEqual(200, self.client.get('/api/csat/wrong-html', headers=tutor).status_code)


if __name__ == '__main__':
    unittest.main()
