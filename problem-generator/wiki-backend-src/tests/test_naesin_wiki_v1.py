import io
import json
import os
import sys
import tempfile
import unittest
import zipfile
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

    def test_selected_exam_material_wrong_notes_export_as_single_docx(self):
        """V1.1: NaesinWikiV1의 원본+JSON 통합 저장(학생 오답)을 선택해 그대로 Word 2007로
        내보낼 수 있어야 한다 — 새 스타일이 아니라 기존 오답 export 헬퍼를 재사용한다."""
        student = main.create_student(main.StudentCreate(name='김민준', school='신구중', grade='중2'))
        created = main.create_exam_material(
            'student_wrong_note', '신구중', '중2', 2026, '2학기', '중간고사', '6번 오답', student['id'],
            UploadFile(filename='wrong.jpg', file=io.BytesIO(b'jpeg-bytes'), headers={'content-type': 'image/jpeg'}),
            None, json.dumps({'wrong_text': '6번 문장 흐름 오답', 'answer': '③'}, ensure_ascii=False))
        response = main.export_exam_material_wrong_notes_docx(
            main.ExamMaterialWrongNoteExportRequest(material_ids=[created['id']], title='김민준 오답 복습'))
        self.assertEqual(
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document', response.media_type)
        with zipfile.ZipFile(response.path) as archive:
            self.assertIn('word/document.xml', archive.namelist())
            document_xml = archive.read('word/document.xml').decode('utf-8')
        self.assertIn('김민준', document_xml)
        self.assertIn('wrong_text', document_xml)

    def test_export_requires_selected_material_ids(self):
        with self.assertRaises(HTTPException) as raised:
            main.export_exam_material_wrong_notes_docx(main.ExamMaterialWrongNoteExportRequest(material_ids=[]))
        self.assertEqual(400, raised.exception.status_code)

    def test_export_ignores_school_print_ids(self):
        """core_type='school_print' 자료는 학생 오답 export 대상이 아니다."""
        created = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '학교 프린트', None,
            UploadFile(filename='print.pdf', file=io.BytesIO(b'%PDF-1'), headers={'content-type': 'application/pdf'}),
            None, json.dumps({'items': []}, ensure_ascii=False))
        with self.assertRaises(HTTPException) as raised:
            main.export_exam_material_wrong_notes_docx(main.ExamMaterialWrongNoteExportRequest(material_ids=[created['id']]))
        self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__': unittest.main()
