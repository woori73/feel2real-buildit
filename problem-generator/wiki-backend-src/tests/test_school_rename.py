import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-school-rename-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402
import workspace as wiki_workspace  # noqa: E402


def _request(role: str = 'ADMIN'):
    identity = wiki_workspace.WikiIdentity('u1', 'admin', role)
    return SimpleNamespace(state=SimpleNamespace(wiki_identity=identity))


class SchoolRenameTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        # given: 같은 학교가 학교 프린트·FINAL 완성본·생성 문항·Digital Twin에 '덕명중'으로 흩어져 있다
        main.create_exam_material(
            'school_print', '덕명중', '2학년', 2026, '2학기', '기말고사', 'Lesson 5 학습지', None,
            UploadFile(filename='l5.pdf', file=io.BytesIO(b'%PDF-l5'), headers={'content-type': 'application/pdf'}),
            None, '{}')
        with main.get_db() as conn:
            conn.execute("INSERT INTO final_deliverables (kind, school, grade, year, semester, exam, title, original_blob, html_text, created_at) VALUES ('final','덕명중','2학년',2026,'2학기','기말고사','L5 예상문제',X'',?,?)",
                         ('<p>x</p>', '2026-11-24'))
            conn.execute("INSERT INTO generated_questions (school, grade, exam, created_at) VALUES ('덕명중','2학년','기말고사','2026-11-24')")
            conn.execute("INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES ('덕명중','2학년','기말고사',?,1,1,'n','n')",
                         (json.dumps({'school': '덕명중', 'grade': '2학년'}, ensure_ascii=False),))
            conn.execute("INSERT INTO generated_questions (school, grade, exam, created_at) VALUES ('신구중','2학년','기말고사','2026-11-24')")

    def tearDown(self):
        self.temp_dir.cleanup()

    def _count(self, table: str, school: str) -> int:
        with main.get_db() as conn:
            return conn.execute(f'SELECT COUNT(*) FROM {table} WHERE school = ?', (school,)).fetchone()[0]

    def test_dry_run_reports_counts_without_changing_rows(self):
        # when: 미리보기로 요청한다
        result = main.rename_school(main.SchoolRenameRequest(from_school='덕명중', to_school='덕명여중'), _request())
        # then: 테이블별 건수만 알려 주고 실제 행은 그대로다
        self.assertTrue(result['dry_run'])
        self.assertEqual({'digital_twin_profiles': 1, 'exam_material_sets': 1, 'final_deliverables': 1, 'generated_questions': 1}, result['counts'])
        self.assertEqual(4, result['total'])
        self.assertEqual(1, self._count('exam_material_sets', '덕명중'))

    def test_rename_moves_every_table_and_twin_json(self):
        # when: 실제로 바꾼다
        result = main.rename_school(main.SchoolRenameRequest(from_school='덕명중', to_school='덕명여중', dry_run=False), _request())
        # then: 모든 자료가 새 이름으로 옮겨지고, 다른 학교는 건드리지 않는다
        self.assertEqual(4, result['total'])
        for table in ('exam_material_sets', 'final_deliverables', 'generated_questions', 'digital_twin_profiles'):
            self.assertEqual(0, self._count(table, '덕명중'), table)
            self.assertEqual(1, self._count(table, '덕명여중'), table)
        self.assertEqual(1, self._count('generated_questions', '신구중'))
        with main.get_db() as conn:
            profile = json.loads(conn.execute("SELECT profile_json FROM digital_twin_profiles WHERE school='덕명여중'").fetchone()[0])
        self.assertEqual('덕명여중', profile['school'])
        self.assertEqual(1, len(main.list_exam_materials('덕명여중', '2학년', 2026, '2학기', '기말고사')))
        self.assertIn('덕명여중', {item['school'] for item in main.school_hierarchy()})

    def test_non_admin_is_rejected(self):
        # given: 관리자가 아닌 사용자
        # when / then: 403으로 거부된다
        with self.assertRaises(HTTPException) as raised:
            main.rename_school(main.SchoolRenameRequest(from_school='덕명중', to_school='덕명여중', dry_run=False), _request('TUTOR'))
        self.assertEqual(403, raised.exception.status_code)
        self.assertEqual(1, self._count('final_deliverables', '덕명중'))

    def test_blank_or_same_name_is_rejected(self):
        # when / then: 빈 이름이나 같은 이름은 400
        for src, dst in (('덕명중', ' '), ('덕명중', '덕명중')):
            with self.assertRaises(HTTPException) as raised:
                main.rename_school(main.SchoolRenameRequest(from_school=src, to_school=dst, dry_run=False), _request())
            self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
