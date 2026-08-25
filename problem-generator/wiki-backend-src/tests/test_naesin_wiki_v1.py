import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-v1-bootstrap.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()
import main  # noqa: E402


class NaesinWikiV1Test(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp_dir.cleanup()

    def test_original_and_json_are_saved_as_one_context_set(self):
        created = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '학교 1차 프린트', None,
            UploadFile(filename='print.pdf', file=io.BytesIO(b'%PDF-original'), headers={'content-type': 'application/pdf'}),
            None, json.dumps({'items': [{'number': 1}]}, ensure_ascii=False))
        rows = main.list_exam_materials('신구중', '중2', 2026, '2학기', '중간고사')
        self.assertEqual([created['id']], [row['id'] for row in rows])
        self.assertEqual(b'%PDF-original', main.open_exam_material_original(created['id']).body)
        self.assertEqual(1, json.loads(main.open_exam_material_json(created['id']).body)['items'][0]['number'])

    def test_wrong_note_requires_selected_student(self):
        with self.assertRaises(HTTPException) as raised:
            main.create_exam_material('student_wrong_note', '신구중', '중2', 2026, '2학기', '중간고사', '', None,
                UploadFile(filename='wrong.jpg', file=io.BytesIO(b'image')), None, '{}')
        self.assertEqual(400, raised.exception.status_code)

    def test_insight_keeps_source_and_exam_context(self):
        main.create_educator_insight(main.EducatorInsightCreate(content='서술형 3번 강조', school='신구중', grade='중2',
            year=2026, semester='2학기', exam='중간고사', source='school_teacher'))
        rows = main.list_educator_insights(20, '신구중', '중2', 2026, '2학기', '중간고사')
        self.assertEqual('school_teacher', rows[0]['source'])


if __name__ == '__main__': unittest.main()
