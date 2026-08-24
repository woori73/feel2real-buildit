import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch
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

    def test_persisted_columns_are_the_digital_twin_response_contract(self):
        with main.get_db() as conn:
            now = '2026.08.23 12:00'
            cursor = conn.execute(
                "INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)",
                ('실제학교', '중2', '1학기 기말', '{"school":"오래된학교","grade":"","exam":""}', 4, now, now),
            )
            twin_id = cursor.lastrowid

        profile = main.get_digital_twin(twin_id)
        self.assertEqual('실제학교', profile['school'])
        self.assertEqual('중2', profile['grade'])
        self.assertEqual('1학기 기말', profile['exam'])
        self.assertEqual(now, profile['updatedAt'])

        with main.get_db() as conn:
            loaded = main._load_twin(conn, twin_id, None, None, None)
        self.assertEqual(twin_id, loaded['id'])
        self.assertEqual('실제학교', loaded['school'])

    def test_selected_twin_is_passed_to_generation_context_and_saved(self):
        with main.get_db() as conn:
            now = '2026.08.23 12:00'
            cursor = conn.execute(
                "INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)",
                ('생성연결중', '중3', '2학기 기말', '{"school":"과거값","dna":"학교 DNA","types":[],"rules":[]}', 2, now, now),
            )
            twin_id = cursor.lastrowid
        ai_response = '{"mcQuestions":[{"questionType":"grammar","sourceText":"Passage","stem":"문제","options":["A","B","C","D","E"],"answer":[1],"multi":false,"score":2}],"saQuestions":[]}'
        ai_result = main.ai_router.ProviderResult(ai_response, 'openrouter', 'qwen/qwen3.7-flash', 'server')
        with patch.object(main.ai_router, 'generate', return_value=ai_result) as ai:
            result = main.generate_questions(main.NewQuestionRequest(
                api_key='test-key', twin_id=twin_id, school='다른학교', grade='중1', exam='다른시험',
                passage='Passage', grammar='Grammar', count=1, types=['grammar'],
            ))

        self.assertEqual(twin_id, result['twin']['id'])
        self.assertEqual('생성연결중', result['twin']['school'])
        self.assertIn('생성연결중', ai.call_args.args[1])
        with main.get_db() as conn:
            saved = conn.execute('SELECT twin_id, school, grade, exam, raw_json FROM generated_questions').fetchone()
        self.assertEqual((twin_id, '생성연결중', '중3', '2학기 기말'), tuple(saved)[:4])
        self.assertEqual('Grammar', json.loads(saved['raw_json'])['grammarFocus'])

    def test_incomplete_short_answer_retries_once_then_saves_nothing(self):
        with main.get_db() as conn:
            now = '2026.08.24 12:00'
            cursor = conn.execute(
                "INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)",
                ('압구정중', '중3', '기말고사', '{"types":[{"name":"서술형"}]}', 1, now, now),
            )
            twin_id = cursor.lastrowid
        incomplete = json.dumps({'mcQuestions': [], 'saQuestions': [{
            'questionType': 'writing', 'sourceText': 'Passage', 'stem': '7개 단어로 영작하시오.',
            'conditions': ['7개 단어'], 'answerFormat': '완전한 문장',
            'acceptedAnswers': ['He wanted to teach in Korean because'],
            'sampleAnswer': 'He wanted to teach in Korean because...', 'gradingRule': '조건 충족', 'score': 5,
        }]})
        ai_result = main.ai_router.ProviderResult(incomplete, 'openrouter', 'qwen/qwen3.7-flash', 'server')
        with patch.object(main.ai_router, 'generate', return_value=ai_result) as ai:
            with self.assertRaises(main.HTTPException) as raised:
                main.generate_questions(main.NewQuestionRequest(
                    api_key='test-key', twin_id=twin_id, passage='Passage', grammar='가정법', count=1,
                ))
        self.assertEqual(2, ai.call_count)
        self.assertEqual(502, raised.exception.status_code)
        with main.get_db() as conn:
            self.assertEqual(0, conn.execute('SELECT COUNT(*) FROM generated_questions').fetchone()[0])


if __name__ == '__main__':
    unittest.main()
