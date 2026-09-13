import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-wrong-flow.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


def _upload(name: str, payload: bytes, content_type: str) -> UploadFile:
    return UploadFile(filename=name, file=io.BytesIO(payload), headers={'content-type': content_type})


class WrongNoteSaveViewFlowTest(unittest.TestCase):
    """실사용 검증(PRF #280 사후) 재현 테스트. given/when/then:
    1) 승인 저장(원본 N건 + wrong-note 1건) → student_id 정확 연결
    2) GET wrong-notes / wrong-materials 조회가 저장 직후 즉시 동일 데이터를 반환(새로고침 재조회 동일)
    3) 원본 사진별 exam_material_sets blob + 복원 JSON 보존 확인
    """

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        with main.get_db() as conn:
            self.student_id = conn.execute(
                "INSERT INTO students (name, school, grade, semester, exam_type, created_at) VALUES (?,?,?,?,?,?)",
                ('최승원', '세화고', '3학년', '2학기', '기말', '2026.09.13 10:00'),
            ).lastrowid
        self.saved_material_ids = []
        self.saved_note = None

    def tearDown(self):
        self.temp_dir.cleanup()

    def _approve_save_like_ui(self, photo_count: int = 6):
        """UI 승인 저장과 동일한 호출: 사진 파일마다 exam-materials 업로드 → wrong-notes 1건."""
        restored_html = '<section><h2>문항 1</h2><p>Choose: <u>She</u> goes.</p><ol><li>① go</li><li>② goes</li></ol></section>'
        json_payload = json.dumps({
            'kind': 'wrong-photo-ai-restore', 'title': '최승원 오답 복원 문제',
            'pages': [f'photo-{i}.jpg' for i in range(1, photo_count + 1)],
            'reviewMarkCount': 0, 'restoredHtml': restored_html, 'canonicalJson': {'version': 2},
        }, ensure_ascii=False)
        for i in range(1, photo_count + 1):
            created = main.create_exam_material(
                core_type='student_wrong_note', school='세화고', grade='3학년', year=2026, semester='2학기',
                exam='기말고사', title='최승원 오답 복원 문제', student_id=self.student_id,
                original=_upload(f'photo-{i}.jpg', f'JPGDATA-{i}'.encode(), 'image/jpeg'),
                json_file=None, json_text=json_payload, html_text='')
            self.saved_material_ids.append(created['id'])
        self.saved_note = main.create_wrong_note(self.student_id, main.WrongNoteCreate(
            exam_label='2026 2학기 기말고사', title='최승원 오답 복원 문제', question_type='AI 복원',
            question_text='문항 1 Choose: She goes.', answer_text='', source_text='1. Choose the correct word.'))

    def test_six_photos_are_six_rows_linked_to_the_selected_student(self):
        # given/when: UI와 동일한 승인 저장(사진 6개).
        self._approve_save_like_ui(photo_count=6)

        # then: 원본은 사진별 6행, wrong-note는 1행 — student_id는 선택한 학생 그대로.
        with main.get_db() as conn:
            materials = conn.execute(
                "SELECT * FROM exam_material_sets WHERE student_id = ? AND core_type = 'student_wrong_note'",
                (self.student_id,)).fetchall()
            notes = conn.execute('SELECT * FROM student_wrong_notes WHERE student_id = ?', (self.student_id,)).fetchall()
        self.assertEqual(6, len(materials), '사진 6개는 하나의 묶음이 아니라 각각의 row로 보관된다')
        self.assertTrue(all(row['original_blob'] == f'JPGDATA-{index + 1}'.encode() for index, row in enumerate(materials)))
        self.assertTrue(all('restoredHtml' in json.loads(row['json_text']) for row in materials), '복원 JSON도 원본과 함께 보관된다')
        self.assertEqual(1, len(notes))
        self.assertEqual('최승원', conn.execute('SELECT name FROM students WHERE id = ?', (notes[0]['student_id'],)).fetchone()['name'])

    def test_query_apis_return_saved_rows_immediately_and_are_refresh_stable(self):
        # given: 승인 저장 완료.
        self._approve_save_like_ui(photo_count=6)

        # when: 저장 직후 목록 API를 두 번 조회한다(즉시 조회 + 새로고침 재조회).
        notes_first = main.list_wrong_notes(self.student_id)
        notes_second = main.list_wrong_notes(self.student_id)
        materials_first = main.list_student_wrong_materials(self.student_id)
        materials_second = main.list_student_wrong_materials(self.student_id)

        # then: 두 조회가 동일하고, 저장한 내용(시험명/제목/복원 텍스트/저장일)을 그대로 노출한다.
        self.assertEqual(1, len(notes_first))
        self.assertEqual(notes_first, notes_second)
        note = notes_first[0]
        self.assertEqual('2026 2학기 기말고사', note['exam_label'])
        self.assertEqual('최승원 오답 복원 문제', note['title'])
        self.assertIn('Choose: She goes', note['wrong_text'])
        self.assertTrue(note['created_at'])
        self.assertEqual(6, len(materials_first))
        self.assertEqual(materials_first, materials_second)
        self.assertEqual({'photo-1.jpg', 'photo-6.jpg'}, {m['original_name'] for m in materials_first} & {'photo-1.jpg', 'photo-6.jpg'})
        self.assertTrue(all(m['id'] in self.saved_material_ids for m in materials_first))

    def test_wrong_materials_metadata_distinguishes_images_from_files(self):
        # given: 학생 원본을 이미지 1건 + PDF 1건으로 승인 저장한다.
        json_payload = json.dumps({'kind': 'wrong-photo-ai-restore', 'restoredHtml': '<p>x</p>'}, ensure_ascii=False)
        for name, ctype in (('photo-1.jpg', 'image/jpeg'), ('worksheet.pdf', 'application/pdf')):
            main.create_exam_material(
                core_type='student_wrong_note', school='세화고', grade='3학년', year=2026, semester='2학기',
                exam='기말고사', title='최승원 오답 복원 문제', student_id=self.student_id,
                original=_upload(name, b'BYTES', ctype), json_file=None, json_text=json_payload, html_text='')

        # when: 보관 원본 메타데이터를 조회한다.
        materials = main.list_student_wrong_materials(self.student_id)

        # then: original_type이 그대로 노출되어 UI가 이미지 썸네일/파일 링크를 구분할 수 있다.
        by_name = {m['original_name']: m['original_type'] for m in materials}
        self.assertEqual({'photo-1.jpg': 'image/jpeg', 'worksheet.pdf': 'application/pdf'}, by_name)

    def test_original_blob_endpoint_serves_a_preserved_photo(self):
        # given: 승인 저장된 원본 1건.
        self._approve_save_like_ui(photo_count=1)
        material_id = self.saved_material_ids[0]

        # when: 기존 /original 계약으로 blob을 연다.
        response = main.open_exam_material_original(material_id)

        # then: 업로드한 사진 바이트가 그대로 보존되어 내려온다.
        self.assertEqual('JPGDATA-1'.encode(), response.body)
        self.assertEqual('image/jpeg', response.media_type)

    def test_restore_endpoint_still_writes_nothing_by_itself(self):
        # given: AI 복원 결과.
        ai_result = main.ai_router.ProviderResult(
            json.dumps({'html': '<section><h2>문항 1</h2><p>원래 문제</p></section>', 'canonicalJson': {}}),
            'openrouter', 'qwen/qwen3.7-flash', 'server')
        payload = main.WrongRestoreRequest(pages=[
            main.WrongRestorePage(page=1, name='wrong.jpg', ocr_text='1. 원래 문제', image_data_url='')])

        # when: 복원만 실행한다.
        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result):
            main.restore_student_wrong_photo(payload)

        # then: 복원 단계에서는 여전히 아무것도 저장되지 않는다(승인 후에만 저장).
        with main.get_db() as conn:
            self.assertEqual(0, conn.execute('SELECT COUNT(*) c FROM student_wrong_notes').fetchone()['c'])
            self.assertEqual(0, conn.execute('SELECT COUNT(*) c FROM exam_material_sets').fetchone()['c'])


if __name__ == '__main__':
    unittest.main()
