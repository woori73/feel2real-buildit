import hashlib
import asyncio
import io
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

from fastapi import HTTPException, UploadFile


BACKEND = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'backend'))
if BACKEND not in sys.path:
    sys.path.insert(0, BACKEND)

import main


class AutomationContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = os.path.join(self.tmp.name, 'automation.db')
        main.init_db()
        main.migrate_schema()
        self.payload = {
            'questions': [
                {
                    'question_no': 1,
                    'question_type': '내용 일치',
                    'passage': 'A source passage.',
                    'prompt': 'Which statement is correct?',
                    'grammar_points': ['현재시제'],
                    'vocabulary_features': ['중1'],
                    'choice_structure': ['5지선다'],
                    'trap_patterns': ['부분 일치'],
                    'difficulty': '중',
                    'short_answer_features': [],
                    'other_features': ['원문 근거'],
                }
            ]
        }

    def tearDown(self):
        self.tmp.cleanup()

    def _upload(self):
        content = json.dumps(self.payload, ensure_ascii=False).encode()
        return asyncio.run(main.ingest_digital_twin_source(
            school='신사중', grade='1학년', exam='1학기 중간', material_type='past_exam',
            file=UploadFile(filename='sample.json', file=io.BytesIO(content)),
        ))

    def test_source_is_split_saved_versioned_and_duplicate_is_blocked(self):
        first = self._upload()
        self.assertEqual('stored', first['status'])
        self.assertEqual(1, first['question_count'])
        with self.assertRaises(HTTPException) as duplicate:
            self._upload()
        self.assertEqual(409, duplicate.exception.status_code)
        self.assertEqual('duplicate_source', duplicate.exception.detail['status'])
        with main.get_db() as conn:
            self.assertEqual(1, conn.execute('SELECT COUNT(*) FROM digital_twin_sources').fetchone()[0])
            self.assertEqual(1, conn.execute('SELECT COUNT(*) FROM digital_twin_question_dna').fetchone()[0])
            self.assertEqual(1, conn.execute('SELECT COUNT(*) FROM digital_twin_profiles WHERE is_active=1').fetchone()[0])

    def test_missing_twin_returns_needs_source_material_without_ai(self):
        response = main.automation_generate_questions(main.AutomationGenerationRequest(
            task='generate_questions', school='없는중', grade='1학년', exam='중간',
            question_count=3, source_passage='Passage', requirements=['서답형 1개'], digital_twin='auto',
        ))
        self.assertEqual('needs_source_material', response['status'])

    def test_validator_failure_does_not_save(self):
        self._upload()
        with main.get_db() as conn:
            before = conn.execute('SELECT COUNT(*) FROM generated_questions').fetchone()[0]
        with patch.object(main.ai_router, 'generate') as generate:
            generate.return_value = type('Result', (), {'text': '{"bad":true}', 'provider': 'openrouter', 'model': 'qwen/qwen3.7-flash'})()
            with self.assertRaises(HTTPException) as response:
                main.automation_generate_questions(main.AutomationGenerationRequest(
                    school='신사중', grade='1학년', exam='1학기 중간', question_count=3,
                    source_passage='Passage', requirements=['서답형 1개'], digital_twin='auto',
                ))
        self.assertEqual(502, response.exception.status_code)
        with main.get_db() as conn:
            after = conn.execute('SELECT COUNT(*) FROM generated_questions').fetchone()[0]
        self.assertEqual(before, after)


if __name__ == '__main__':
    unittest.main()
