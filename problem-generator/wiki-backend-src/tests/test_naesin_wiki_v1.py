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
        """기존(원본 파일 기반) 학교 프린트 데이터는 이번 HTML+JSON 전환 이후에도 그대로 조회되어야 한다."""
        created = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '학교 1차 프린트', None,
            UploadFile(filename='print.pdf', file=io.BytesIO(b'%PDF-original'), headers={'content-type': 'application/pdf'}),
            None, json.dumps({'items': [{'number': 1}]}, ensure_ascii=False))
        rows = main.list_exam_materials('신구중', '중2', 2026, '2학기', '중간고사')
        self.assertEqual([created['id']], [row['id'] for row in rows])
        self.assertEqual(0, rows[0]['has_html'])
        self.assertEqual(b'%PDF-original', main.open_exam_material_original(created['id']).body)
        self.assertEqual(1, json.loads(main.open_exam_material_json(created['id']).body)['items'][0]['number'])

    def test_school_print_saves_html_and_json_without_any_original_file(self):
        """새 방식: 학교 프린트는 사진/PDF/HWP 원본 없이 HTML+JSON만으로 저장·조회할 수 있어야 한다."""
        created = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '5과 HTML 자료', None,
            None, None, json.dumps({'items': [{'number': 1}]}, ensure_ascii=False),
            html_text='<h1>5과 본문</h1><p>학교 프린트 HTML 원문</p>')
        self.assertEqual('', created['original_name'])
        rows = main.list_exam_materials('신구중', '중2', 2026, '2학기', '중간고사')
        self.assertEqual(1, rows[0]['has_html'])
        html_body = main.open_exam_material_html(created['id']).body.decode('utf-8')
        self.assertIn('학교 프린트 HTML 원문', html_body)
        json_body = json.loads(main.open_exam_material_json(created['id']).body)
        self.assertEqual(1, json_body['items'][0]['number'])

    def test_school_print_requires_html_or_original(self):
        """HTML도, 원본 파일도 없는 학교 프린트 저장은 거부되어야 한다."""
        with self.assertRaises(HTTPException) as raised:
            main.create_exam_material('school_print', '신구중', '중2', 2026, '2학기', '중간고사', '빈 자료', None,
                None, None, '')
        self.assertEqual(400, raised.exception.status_code)

    def test_wrong_note_requires_selected_student(self):
        with self.assertRaises(HTTPException) as raised:
            main.create_exam_material('student_wrong_note', '신구중', '중2', 2026, '2학기', '중간고사', '', None,
                UploadFile(filename='wrong.jpg', file=io.BytesIO(b'image')), None, '{}')
        self.assertEqual(400, raised.exception.status_code)

    def test_school_print_can_be_saved_without_json_and_added_later(self):
        """V1.1 STEP1 fix: 학교 프린트는 JSON을 나중에 추가할 수 있도록 선택 입력이다."""
        created = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '학교 2차 프린트', None,
            UploadFile(filename='print2.pdf', file=io.BytesIO(b'%PDF-2'), headers={'content-type': 'application/pdf'}),
            None, '')
        self.assertEqual('{}', main.open_exam_material_json(created['id']).body.decode())

    def test_student_wrong_note_still_requires_json(self):
        """학생 오답은 원본+JSON 세트가 필수라는 기존 규칙을 이번 fix에서도 유지한다."""
        student = main.create_student(main.StudentCreate(name='박서준', school='신구중', grade='중2'))
        with self.assertRaises(HTTPException) as raised:
            main.create_exam_material('student_wrong_note', '신구중', '중2', 2026, '2학기', '중간고사', '', student['id'],
                UploadFile(filename='wrong2.jpg', file=io.BytesIO(b'image')), None, '')
        self.assertEqual(400, raised.exception.status_code)

    def test_school_print_can_be_downloaded_then_deleted_and_list_updates(self):
        """STEP1 자료 관리: 저장된 학교 프린트를 HTML로 내려받고, 삭제하면 목록에서 즉시 사라진다."""
        keep = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '남길 자료', None,
            None, None, '', html_text='<p>keep</p>')
        drop = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '지울 자료', None,
            None, None, '', html_text='<p>drop</p>')
        self.assertIn('drop', main.open_exam_material_html(drop['id']).body.decode('utf-8'))

        self.assertEqual(204, main.delete_exam_material(drop['id']).status_code)

        remaining = main.list_exam_materials('신구중', '중2', 2026, '2학기', '중간고사')
        self.assertEqual([keep['id']], [row['id'] for row in remaining])
        with self.assertRaises(HTTPException) as raised:
            main.delete_exam_material(drop['id'])
        self.assertEqual(404, raised.exception.status_code)

    def test_delete_exam_material_only_touches_its_own_context_row(self):
        """다른 학교/학년/시험 자료는 id로만 지우므로 현재 Context 자료를 지워도 남는다."""
        other = main.create_exam_material(
            'school_print', '신사중', '중3', 2026, '1학기', '기말고사', '다른 시험', None,
            None, None, '', html_text='<p>other</p>')
        target = main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '이번 시험', None,
            None, None, '', html_text='<p>target</p>')
        main.delete_exam_material(target['id'])
        self.assertEqual(
            [other['id']],
            [row['id'] for row in main.list_exam_materials('신사중', '중3', 2026, '1학기', '기말고사')])

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


class FinalDeliverableTest(unittest.TestCase):
    """FINAL/직보 결과물 저장 영역. exam_material_sets(자료)와 별개로, 외부 LLM/강사가 완성한
    산출물을 학교/학년/연도/학기/시험 Context 기준으로 저장·조회하는 자리를 검증한다."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp_dir.cleanup()

    def test_final_deliverable_requires_original_or_html(self):
        with self.assertRaises(HTTPException) as raised:
            main.create_final_deliverable('final', '신구중', '중2', 2026, '2학기', '중간고사', 'FINAL',
                None, None, '', '')
        self.assertEqual(400, raised.exception.status_code)

    def test_final_deliverable_saves_html_and_json_without_original_file(self):
        created = main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', '기말 FINAL',
            None, None, json.dumps({'sections': ['국어']}, ensure_ascii=False),
            html_text='<h1>FINAL 완성본</h1>')
        self.assertEqual('final', created['kind'])
        rows = main.list_final_deliverables('신구중', '중2', 2026, '2학기', '중간고사', kind='final')
        self.assertEqual([created['id']], [row['id'] for row in rows])
        self.assertEqual(1, rows[0]['has_html'])
        html_body = main.open_final_deliverable_html(created['id']).body.decode('utf-8')
        self.assertIn('FINAL 완성본', html_body)
        json_body = json.loads(main.open_final_deliverable_json(created['id']).body)
        self.assertEqual(['국어'], json_body['sections'])

    def test_final_deliverable_saves_original_file_without_html(self):
        """FINAL 결과물은 학교 프린트와 달리 외부에서 만든 실제 파일(원본)도 그대로 받을 수 있어야 한다."""
        created = main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', 'FINAL docx',
            UploadFile(filename='final.docx', file=io.BytesIO(b'docx-bytes'),
                       headers={'content-type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}),
            None, '', html_text='')
        self.assertEqual(0, main.list_final_deliverables('신구중', '중2', 2026, '2학기', '중간고사', kind='final')[0]['has_html'])
        self.assertEqual(b'docx-bytes', main.open_final_deliverable_original(created['id']).body)

    def test_final_deliverable_defaults_kind_to_final(self):
        created = main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', 'FINAL',
            None, None, '', html_text='<p>x</p>')
        self.assertEqual('final', created['kind'])

    def test_list_final_deliverables_kind_filter_excludes_jikbo(self):
        """이번 작업은 kind='jikbo' 저장 UI를 만들지 않지만, 향후 MCP save_jikbo가 같은 API를
        쓸 수 있도록 kind 필터가 이미 정확히 동작해야 한다."""
        main.create_final_deliverable('final', '신구중', '중2', 2026, '2학기', '중간고사', 'FINAL',
            None, None, '', html_text='<p>final</p>')
        main.create_final_deliverable('jikbo', '신구중', '중2', 2026, '2학기', '중간고사', '직보',
            None, None, '', html_text='<p>jikbo</p>')
        final_only = main.list_final_deliverables('신구중', '중2', 2026, '2학기', '중간고사', kind='final')
        self.assertEqual(1, len(final_only))
        self.assertEqual('final', final_only[0]['kind'])
        both = main.list_final_deliverables('신구중', '중2', 2026, '2학기', '중간고사', kind=None)
        self.assertEqual(2, len(both))

    def test_final_deliverable_original_view_404s_when_no_original_saved(self):
        created = main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', 'FINAL',
            None, None, '', html_text='<p>x</p>')
        with self.assertRaises(HTTPException) as raised:
            main.open_final_deliverable_original(created['id'])
        self.assertEqual(404, raised.exception.status_code)


class WorkspaceContextTest(unittest.TestCase):
    """GET /api/workspaces/final|jikbo/context — 향후 MCP/외부 LLM이 한 번의 요청으로 읽을
    시험 Context를 DB 원본에서 그대로 조립하는지 검증한다(화면을 긁지 않는다)."""

    CTX = dict(school='신구중', grade='중2', year=2026, semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp_dir.cleanup()

    def test_final_context_is_empty_state_when_nothing_saved(self):
        result = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(self.CTX, result['exam_context'])
        self.assertIsNone(result['digital_twin'])
        for key in ('school_prints', 'student_errors', 'class_insights', 'generated_questions', 'final_deliverables'):
            self.assertEqual([], result[key])

    def test_jikbo_context_is_not_ready_without_final(self):
        result = main.get_jikbo_workspace_context(**self.CTX)
        self.assertFalse(result['ready'])
        self.assertIsNone(result['latest_final'])
        self.assertEqual([], result['post_final_insights'])

    def test_final_context_assembles_every_category_with_metadata(self):
        main.import_digital_twin(main.ImportedTwinProfile(
            school='신구중', grade='중2', exam='중간고사', dna='시험 DNA', description='신구중 중2 Twin'))
        main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '5과 프린트', None,
            None, None, json.dumps({'items': [1]}, ensure_ascii=False), html_text='<p>학교 프린트 HTML</p>')
        student = main.create_student(main.StudentCreate(name='김민준', school='신구중', grade='중2'))
        main.create_exam_material(
            'student_wrong_note', '신구중', '중2', 2026, '2학기', '중간고사', '6번 오답', student['id'],
            UploadFile(filename='wrong.jpg', file=io.BytesIO(b'jpeg'), headers={'content-type': 'image/jpeg'}),
            None, json.dumps({'wrong_text': '6번 오답'}, ensure_ascii=False))
        main.create_educator_insight(main.EducatorInsightCreate(
            content='서술형 3번 강조', school='신구중', grade='중2', year=2026, semester='2학기',
            exam='중간고사', source='school_teacher'))
        with main.get_db() as conn:
            conn.execute(
                "INSERT INTO generated_questions (school, grade, exam, question_type, stem, created_at) "
                "VALUES (?,?,?,?,?,?)", ('신구중', '중2', '중간고사', '어법', '다음 중 어법상 옳은 것은?', '2026-01-01T00:00:00'))
        main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', '기말 FINAL', None, None, '', html_text='<h1>FINAL</h1>')

        result = main.get_final_workspace_context(**self.CTX)

        self.assertEqual('신구중 중2 Twin', result['digital_twin']['title'])
        self.assertEqual('digital_twin', result['digital_twin']['type'])

        self.assertEqual(1, len(result['school_prints']))
        print_item = result['school_prints'][0]
        self.assertEqual({'id', 'type', 'material_type', 'title', 'created_at', 'uploaded_by', 'source',
                           'provenance', 'student_id', 'student_name', 'has_original', 'original_name',
                           'original_api_path', 'has_html', 'html_text', 'json_text'}, set(print_item.keys()))
        self.assertTrue(print_item['has_html'])
        self.assertIn('학교 프린트 HTML', print_item['html_text'])
        self.assertEqual({'items': [1]}, print_item['json_text'])

        self.assertEqual(1, len(result['student_errors']))
        self.assertEqual('김민준', result['student_errors'][0]['student_name'])
        self.assertFalse(result['student_errors'][0]['has_html'])

        self.assertEqual(1, len(result['class_insights']))
        self.assertEqual('school_teacher', result['class_insights'][0]['source'])

        self.assertEqual(1, len(result['generated_questions']))
        self.assertIn('어법', result['generated_questions'][0]['title'])

        self.assertEqual(1, len(result['final_deliverables']))
        self.assertEqual('기말 FINAL', result['final_deliverables'][0]['title'])

    def test_school_print_context_item_keeps_html_and_json_separate(self):
        main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '둘다', None,
            None, None, json.dumps({'a': 1}, ensure_ascii=False), html_text='<p>html-part</p>')
        result = main.get_final_workspace_context(**self.CTX)
        item = result['school_prints'][0]
        self.assertEqual('<p>html-part</p>', item['html_text'])
        self.assertEqual({'a': 1}, item['json_text'])

    def test_jikbo_context_ready_with_final_shows_correct_order_and_excludes_bulk_materials(self):
        """직보는 학교 프린트/제작 문제/Digital Twin 전체를 다시 담지 않는다."""
        main.import_digital_twin(main.ImportedTwinProfile(school='신구중', grade='중2', exam='중간고사', dna='x'))
        main.create_exam_material(
            'school_print', '신구중', '중2', 2026, '2학기', '중간고사', '프린트', None,
            None, None, '', html_text='<p>x</p>')
        main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', '기말 FINAL', None, None, '', html_text='<h1>FINAL</h1>')

        result = main.get_jikbo_workspace_context(**self.CTX)

        self.assertTrue(result['ready'])
        self.assertEqual('기말 FINAL', result['latest_final']['title'])
        self.assertNotIn('school_prints', result)
        self.assertNotIn('digital_twin', result)
        self.assertNotIn('generated_questions', result)

    def test_post_final_insights_only_includes_insights_recorded_after_final(self):
        created_insight = main.create_educator_insight(main.EducatorInsightCreate(
            content='FINAL 이전 인사이트', school='신구중', grade='중2', year=2026, semester='2학기',
            exam='중간고사', source='director'))
        with main.get_db() as conn:
            conn.execute("UPDATE educator_insights SET created_at=? WHERE id=?",
                         ('2026-01-01T00:00:00', created_insight['id']))
        created_final = main.create_final_deliverable(
            'final', '신구중', '중2', 2026, '2학기', '중간고사', '기말 FINAL', None, None, '', html_text='<h1>FINAL</h1>')
        with main.get_db() as conn:
            conn.execute("UPDATE final_deliverables SET created_at=? WHERE id=?",
                         ('2026-06-01T00:00:00', created_final['id']))
        with main.get_db() as conn:
            conn.execute(
                "INSERT INTO educator_insights (content, school, grade, year, semester, exam, source, created_at) "
                "VALUES (?,?,?,?,?,?,?,?)",
                ('FINAL 이후 인사이트', '신구중', '중2', 2026, '2학기', '중간고사', 'school_teacher', '2026-06-02T00:00:00'))

        result = main.get_jikbo_workspace_context(**self.CTX)

        contents = [item['content'] for item in result['post_final_insights']]
        self.assertIn('FINAL 이후 인사이트', contents)
        self.assertNotIn('FINAL 이전 인사이트', contents)


if __name__ == '__main__': unittest.main()
