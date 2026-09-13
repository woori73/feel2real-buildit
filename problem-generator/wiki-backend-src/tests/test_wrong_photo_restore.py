import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-wrong-restore.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


class WrongPhotoRestoreTest(unittest.TestCase):
    """오답 사진 → AI 복원(HTML) 1차 구현 계약. given/when/then 구조로 3단계를 검증한다:
    복원은 기존 자산(generate_multimodal + sanitizer)을 재사용하고, DB 저장은 하지 않으며,
    승인 저장은 기존 계약(create_wrong_note / create_exam_material)으로만 이뤄진다."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_restore_uses_multimodal_source_of_truth_and_returns_review_marks(self):
        # given: OCR 텍스트(원문 기준) + 이미지 2페이지, AI는 문항 2개 + 판독 필요 1곳을 복원했다.
        html = ('<article><section><h2>문항 1</h2><p>Choose the correct word: '
                '<u>She</u> goes to school.</p><ol><li>① go</li><li>② goes</li></ol></section>'
                '<section><h2>문항 2</h2><p>빈칸: I have ____ apples. '
                '<mark>판독 필요: 선택지 3번</mark></p></section></article>')
        canonical = {'sections': [], 'blocks': [], 'version': 3}
        ai_result = main.ai_router.ProviderResult(
            json.dumps({'html': html, 'canonicalJson': canonical}), 'openrouter', 'qwen/qwen3.7-flash', 'server')
        pages = [
            main.WrongRestorePage(page=1, name='wrong-1.jpg', ocr_text='1. Choose the correct word: She goes to school.',
                                  image_data_url='data:image/jpeg;base64,AAA'),
            main.WrongRestorePage(page=2, name='wrong-2.jpg', ocr_text='2. 빈칸: I have ____ apples.',
                                  image_data_url='data:image/jpeg;base64,BBB'),
        ]

        # when: 복원 엔드포인트를 호출한다.
        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result) as generate:
            result = main.restore_student_wrong_photo(main.WrongRestoreRequest(model='qwen/qwen3.7-flash',
                                                                               metadata={'student': '김민준'}, pages=pages))

        # then: 학교 프린트 복원기와 동일한 멀티모달 계약(OCR 텍스트 + 이미지)으로 호출되고,
        model, system, content = generate.call_args.args[:3]
        self.assertEqual('qwen/qwen3.7-flash', model)
        self.assertIn('source of truth', system)
        self.assertIn('창작', system)
        self.assertIn('판독 필요', system)
        self.assertIn('새 문제로 바꾸거나', system)
        self.assertEqual(2, sum(item['type'] == 'image_url' for item in content))
        self.assertIn('1. Choose the correct word', content[1]['text'])
        self.assertIn('2. 빈칸', content[3]['text'])
        # then: HTML은 sanitizer를 통과하고 판독 필요 mark 수가 함께 반환된다.
        self.assertIn('<u>', result['html'])
        self.assertIn('<mark>판독 필요', result['html'])
        self.assertNotIn('background-image', result['html'])
        self.assertEqual(1, result['reviewMarkCount'])
        self.assertEqual('판독 필요', result['reviewMarkText'])
        self.assertEqual(3, result['canonicalJson']['version'])
        self.assertEqual(2, result['canonicalJson']['restorationVersion'])
        self.assertEqual(2, result['canonicalJson']['sourceLinkage']['pageCount'])
        self.assertEqual('wrong-1.jpg', result['canonicalJson']['pages'][0]['sourceName'])
        self.assertIn('김민준', result['canonicalJson']['source']['student'])

    def test_restore_writes_nothing_to_the_database(self):
        # given: 정상 복원 결과.
        ai_result = main.ai_router.ProviderResult(
            json.dumps({'html': '<section><h2>문항 1</h2><p>원래 문제</p></section>', 'canonicalJson': {}}),
            'openrouter', 'qwen/qwen3.7-flash', 'server')
        payload = main.WrongRestoreRequest(pages=[
            main.WrongRestorePage(page=1, name='wrong.jpg', ocr_text='1. 원래 문제', image_data_url='data:image/jpeg;base64,AAA')])

        # when: 복원을 실행한다.
        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result):
            main.restore_student_wrong_photo(payload)

        # then: exam_material_sets와 student_wrong_notes에는 아무 row도 생기지 않는다
        # (승인 저장은 기존 계약에서만 일어난다).
        with main.get_db() as conn:
            self.assertEqual(0, conn.execute('SELECT COUNT(*) c FROM exam_material_sets').fetchone()['c'])
            self.assertEqual(0, conn.execute('SELECT COUNT(*) c FROM student_wrong_notes').fetchone()['c'])

    def test_restore_without_ocr_text_is_rejected_before_ai_call(self):
        # given: OCR 텍스트가 모두 비어 있는 페이지.
        payload = main.WrongRestoreRequest(pages=[
            main.WrongRestorePage(page=1, name='blank.jpg', ocr_text='   ', image_data_url='data:image/jpeg;base64,AAA')])

        # when & then: AI를 호출하지 않고 400으로 막는다(빈 OCR로 창작을 유도하지 않는다).
        with patch.object(main.ai_router, 'generate_multimodal') as generate:
            with self.assertRaises(main.HTTPException) as raised:
                main.restore_student_wrong_photo(payload)
        self.assertEqual(400, raised.exception.status_code)
        generate.assert_not_called()

    def test_empty_ai_html_is_a_restore_failure(self):
        # given: AI가 빈 HTML을 반환.
        ai_result = main.ai_router.ProviderResult('{"html":"","canonicalJson":{}}', 'openrouter', 'qwen/qwen3.7-flash', 'server')
        payload = main.WrongRestoreRequest(pages=[
            main.WrongRestorePage(page=1, name='wrong.jpg', ocr_text='1. 원래 문제', image_data_url='data:image/jpeg;base64,AAA')])

        # when & then: 502로 복원 실패를 알린다.
        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result):
            with self.assertRaises(main.HTTPException) as raised:
                main.restore_student_wrong_photo(payload)
        self.assertEqual(502, raised.exception.status_code)

    def test_approved_save_uses_existing_contracts_without_ai(self):
        # given: 학생 1명과 복원된 HTML(판독 필요 1곳 포함).
        with main.get_db() as conn:
            student_id = conn.execute(
                "INSERT INTO students (name, school, grade, semester, exam_type, created_at) VALUES (?,?,?,?,?,?)",
                ('김민준', '압구정중', '2학년', '2학기', '중간', '2026.09.13 10:00'),
            ).lastrowid
        restored_html = '<section><h2>문항 1</h2><p>원래 문제 <mark>판독 필요: 선택지 3번</mark></p></section>'
        json_payload = json.dumps({'kind': 'wrong-photo-ai-restore', 'restoredHtml': restored_html}, ensure_ascii=False)

        # when: 승인 저장을 기존 계약으로 실행한다(student_wrong_notes 누적 + exam_material_sets 원본 보관).
        note = main.create_wrong_note(student_id, main.WrongNoteCreate(
            exam_label='2026 2학기 중간고사', title='김민준 오답 복원 문제', question_type='AI 복원',
            question_text='문항 1 원래 문제 판독 필요: 선택지 3번', answer_text='', source_text='1. 원래 문제'))

        # then: AI 호출 없이 누적되고, wrong_text에 복원 본문이 저장된다.
        with main.get_db() as conn:
            row = conn.execute('SELECT * FROM student_wrong_notes WHERE id = ?', (note['id'],)).fetchone()
            self.assertEqual('AI 복원', row['question_type'])
            self.assertIn('판독 필요', row['wrong_text'])
            self.assertEqual(0, conn.execute('SELECT COUNT(*) c FROM exam_material_sets').fetchone()['c'])
            self.assertIn('restoredHtml', json_payload)


if __name__ == '__main__':
    unittest.main()
