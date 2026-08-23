import os
import sys
import tempfile
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-school-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


class SchoolHierarchyTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_existing_question_and_student_schools_are_not_dropped(self):
        with main.get_db() as conn:
            conn.execute(
                "INSERT INTO generated_questions (school, grade, exam, created_at) VALUES (?,?,?,?)",
                ('기존문제중', '중2', '기말고사', '2026-08-23'),
            )
            conn.execute(
                "INSERT INTO students (name, school, grade, exam_type, created_at) VALUES (?,?,?,?,?)",
                ('학생', '기존학생중', '중3', '중간고사', '2026-08-23'),
            )

        tree = main.school_hierarchy()
        by_school = {item['school']: item for item in tree}

        self.assertIn('기존문제중', by_school)
        self.assertIn('기존학생중', by_school)
        exam = by_school['기존문제중']['grades'][0]['exams'][0]
        self.assertFalse(exam['hasDigitalTwin'])
        self.assertIsNone(exam['twinId'])

    def test_existing_twin_is_linked_to_school_grade_exam(self):
        with main.get_db() as conn:
            now = '2026.08.23 12:00'
            cursor = conn.execute(
                "INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)",
                ('연결중', '중1', '중간고사', '{"school":"연결중","grade":"중1","exam":"중간고사"}', 3, now, now),
            )
            twin_id = cursor.lastrowid

        tree = main.school_hierarchy()
        school = next(item for item in tree if item['school'] == '연결중')
        exam = school['grades'][0]['exams'][0]
        self.assertEqual(twin_id, exam['twinId'])
        self.assertTrue(exam['hasDigitalTwin'])


if __name__ == '__main__':
    unittest.main()
