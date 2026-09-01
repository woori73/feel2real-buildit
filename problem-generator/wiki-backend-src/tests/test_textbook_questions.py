import io
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-tbq-bootstrap.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())
import main  # noqa: E402
import workspace  # noqa: E402


def canonical(stem='다음 밑줄 친 부분 중 어법상 틀린 것은?', kind='mc', score=3, passage='He runs fast.', choices=('run', 'runs')):
    return {
        'schema_version': 'wiki-question-v1', 'kind': kind, 'question_type': '어법' if kind == 'mc' else '서답형',
        'instruction': {'text': stem, 'html': f'<p>{stem}</p>'},
        'passage': {'text': passage, 'html': f'<p>{passage}</p>', 'status': 'complete' if passage else 'not_applicable'},
        'choices': [{'number': i + 1, 'text': c, 'html': c} for i, c in enumerate(choices)] if kind == 'mc' else [],
        'conditions': [],
        'answer': {'choice_numbers': [1] if kind == 'mc' else [], 'accepted_answers': [] if kind == 'mc' else ['gradually'], 'sample_answer': '' if kind == 'mc' else 'gradually'},
        'explanation': {'text': '주어가 3인칭 단수', 'html': '주어가 3인칭 단수'},
        'scoring': {'score': score, 'multi': False, 'answer_format': '', 'grading_rule': ''},
        'source': {'source_material_id': None, 'source_page': '', 'source_question_number': ''},
    }


class TextbookQuestionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp.name) / 'test.db')
        main.init_db(); main.migrate_schema()
        self.material = main.create_textbook_material(
            grade='중2 (2022 개정)', publisher='동아(윤정미)', lesson='5과', title='5과 문제자료',
            tags='', author='박정민 원장', page_total=55, page_extracted=55, content_html='',
            file=UploadFile(filename='y.pdf', file=io.BytesIO(b'%PDF-1.4 ' + b'x' * 100),
                            headers={'content-type': 'application/pdf'}))

    def tearDown(self): self.temp.cleanup()

    def test_one_pdf_becomes_many_question_records_with_page_provenance(self):
        result = main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=32, question_number='12', extraction_status='ok', question=canonical()),
            main.TextbookQuestionItem(page=32, question_number='13', extraction_status='ok', question=canonical(stem='빈칸에 알맞은 것은?')),
            main.TextbookQuestionItem(page=33, question_number='14', extraction_status='review', question=canonical(stem='순서로 알맞은 것은?')),
        ]))
        self.assertEqual({'total': 3, 'ok': 2, 'review': 1, 'failed': 0}, {k: result[k] for k in ('total', 'ok', 'review', 'failed')})
        rows = main.list_textbook_material_questions(self.material['id'])
        self.assertEqual([32, 32, 33], [r['page'] for r in rows])
        self.assertEqual(['12', '13', '14'], [r['question_number'] for r in rows])
        self.assertEqual('textbook', rows[0]['source_kind'])
        self.assertEqual('wiki-question-v1', rows[0]['canonical']['schema_version'])
        self.assertEqual('동아(윤정미)', rows[0]['publisher'])

    def test_missing_stem_is_recorded_as_failed_not_silently_ok(self):
        broken = canonical(); broken['instruction'] = {'text': '', 'html': ''}
        result = main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, extraction_status='ok', question=canonical()),
            main.TextbookQuestionItem(page=1, extraction_status='ok', question=broken),
        ]))
        self.assertEqual(1, result['ok'])
        self.assertEqual(1, result['failed'])

    def test_replace_re_segments_without_duplicating(self):
        main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, question=canonical()) for _ in range(3)]))
        main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, question=canonical()) for _ in range(2)]))
        self.assertEqual(2, len(main.list_textbook_material_questions(self.material['id'])))

    def test_search_filters_by_publisher_lesson_type_and_status(self):
        main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, extraction_status='ok', question=canonical()),
            main.TextbookQuestionItem(page=2, extraction_status='review', question=canonical(kind='sa', passage='', choices=())),
        ]))
        self.assertEqual(2, len(main.search_textbook_questions(publisher='동아(윤정미)', lesson='5과')))
        self.assertEqual(1, len(main.search_textbook_questions(question_type='어법')))
        self.assertEqual(1, len(main.search_textbook_questions(status='review')))
        self.assertEqual(0, len(main.search_textbook_questions(publisher='비상(황종배)')))

    def test_edit_and_delete_one_question(self):
        result = main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, extraction_status='review', question=canonical())]))
        qid = result['questions'][0]['id']
        edited = canonical(stem='수정된 발문입니다');
        updated = main.update_textbook_question(qid, main.TextbookQuestionPatch(question=edited, extraction_status='ok'))
        self.assertEqual('수정된 발문입니다', updated['stem'])
        self.assertEqual('ok', updated['extraction_status'])
        self.assertEqual(204, main.delete_textbook_question(qid).status_code)
        self.assertEqual([], main.list_textbook_material_questions(self.material['id']))

    def test_deleting_material_cascades_to_its_questions(self):
        main.save_textbook_questions(self.material['id'], main.TextbookQuestionsSave(replace=True, items=[
            main.TextbookQuestionItem(page=1, question=canonical())]))
        main.delete_textbook_material(self.material['id'])
        self.assertEqual([], main.search_textbook_questions())

    def test_table_is_workspace_isolation_aware(self):
        self.assertIn('textbook_questions', workspace.OWNED_TABLES)
        with main.get_db() as conn:
            workspace.migrate_owned_tables(conn, workspace.LEGACY_WORKSPACE_ID)
            cols = {r[1] for r in conn.execute('PRAGMA table_info(textbook_questions)')}
        self.assertIn('workspace_id', cols)


if __name__ == '__main__':
    unittest.main()
