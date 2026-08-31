import json
import os
import sys
import tempfile
import unittest
import io
import zipfile
from pathlib import Path
from fastapi.testclient import TestClient

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False); tmp.close()
os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
import main


class CsatLibraryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Other test modules may import main first with a different temporary DB.
        # Recreate this module's schema so full-suite discovery is order-independent.
        os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
        main.DB_PATH = Path(tmp.name)
        main.init_db()
        main.migrate_schema()

    def setUp(self):
        # Other modules also exercise the shared imported app and may replace DB_PATH.
        os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
        main.DB_PATH = Path(tmp.name)
        main.init_db()
        main.migrate_schema()

    def seed_csat_questions(self, count, question_no=39):
        now = 'assessment-test'
        with main.get_db() as conn:
            source = conn.execute("INSERT INTO csat_question_sources(exam_kind,source_filename,exam_metadata_json,parser,raw_text,created_at) VALUES('half',?,'{}','test','',?)", (f'fixture-{count}-{question_no}.html', now))
            ids = []
            csat_type = main.csat_type_for_number(question_no)
            for index in range(count):
                raw = {'question_html': f'<article class="question"><h2>Original fixture {index}</h2><div class="passage">Passage {index}</div></article>',
                       'explanation_html': f'<b>fixture explanation {index}</b>'}
                cursor = conn.execute('''INSERT INTO csat_questions
                    (source_id,exam_kind,question_no,csat_type_id,csat_type_name,passage,prompt,choices_json,answer,solution,raw_json,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)''',
                    (source.lastrowid, 'half', question_no, csat_type['id'], csat_type['name'], f'Passage {index}',
                     '문장이 들어가기에 가장 적절한 곳은?', '["A","B","C","D","E"]', '1', f'Solution {index}', json.dumps(raw), now))
                ids.append(cursor.lastrowid)
        return ids

    def test_number_to_standard_type(self):
        expected = {18:'purpose', 21:'implication', 24:'title', 29:'grammar', 30:'vocabulary', 31:'blank', 34:'blank', 36:'order', 39:'insertion', 40:'summary', 42:'long_complex', 45:'long_sequence_reference_content'}
        for number, type_id in expected.items():
            with self.subTest(number=number): self.assertEqual(type_id, main.csat_type_for_number(number)['id'])

    def test_prompt_structure_mismatch_is_flagged(self):
        self.assertFalse(main.classify_csat_question(29, '다음 글의 밑줄 친 부분 중 어법상 틀린 것은?', ['a'] * 5)['type_mismatch'])
        self.assertTrue(main.classify_csat_question(29, '다음 글의 제목으로 적절한 것은?', ['a'] * 5)['type_mismatch'])

    def test_hwpx_adapter_extracts_section_text(self):
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as archive:
            archive.writestr('Contents/section0.xml', '<hp:section xmlns:hp="urn:test"><hp:t>21. 의미하는 것은?</hp:t><hp:t>Passage</hp:t></hp:section>')
        self.assertIn('21. 의미하는 것은?', main._extract_hwpx_text(payload.getvalue()))

    def test_hwp_rejects_non_ole_instead_of_saving_fake_text(self):
        with self.assertRaises(main.HTTPException) as raised:
            main._extract_hwp5_text(b'not-an-hwp')
        self.assertEqual(400, raised.exception.status_code)

    def test_canonical_schema_keeps_required_fields(self):
        item = main._canonical_question({'question_no': 24, 'prompt': '제목으로 적절한 것은?', 'passage': 'Text', 'choices': ['A']}, 'half', {'title': '6월', 'year': '2027', 'round': '6'}, 'exam.hwp', 'raw', 'now')
        for field in ('source_exam','year','session','exam_kind','question_number','csat_type','stem','passage','choices','answer','explanation','source_file','raw_text','created_at'):
            self.assertIn(field, item)

    def test_question_persists_and_reads_back(self):
        with main.get_db() as conn:
            source = conn.execute("INSERT INTO csat_question_sources(exam_kind,source_filename,exam_metadata_json,parser,raw_text,created_at) VALUES('half','sample.json','{}','json','', 'now')")
            conn.execute("""INSERT INTO csat_questions(source_id,exam_kind,question_no,csat_type_id,csat_type_name,passage,prompt,choices_json,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)""", (source.lastrowid,'half',24,'title','제목','Passage','제목으로 가장 적절한 것은?',json.dumps(['A','B','C','D','E']),'now'))
        rows = main.list_csat_questions(exam_kind='half')
        self.assertEqual(24, rows[0]['question_no']); self.assertEqual(5, len(rows[0]['choices']))

    def test_print_html_is_editable_two_column_buildit_document(self):
        html = main.render_csat_html([{'question_no':21,'prompt':'의미하는 것은?','passage':'Text','choices':['A','B'],'type_mismatch':False}], '하프 모의고사')
        self.assertIn('column-count:2', html); self.assertIn('contenteditable="true"', html); self.assertIn('BuildIt 자체 제작 시험지', html)
        self.assertIn('moveQuestion', html); self.assertIn("closest('.question').remove()", html); self.assertIn('4문항 목표', html)

    def test_html_splitter_preserves_fragments_and_classifies_prompt(self):
        html = '''<main>
        <article class="question" data-question-no="1"><h2 class="prompt">다음 글의 빈칸에 들어갈 말로 가장 적절한 것은?</h2><div class="passage"><b>Original</b> passage</div><ol class="choices"><li>A</li><li>B</li></ol><div class="answer">2</div><div class="explanation"><em>Because</em></div></article>
        <article class="question" data-question-no="2"><p class="instruction">주어진 글 다음에 이어질 글의 순서로 가장 적절한 것은?</p><div class="passage">Second</div></article>
        </main>'''
        questions = main.split_csat_html(html)
        self.assertEqual(2, len(questions))
        self.assertEqual(1, questions[0]['source_question_no'])
        self.assertEqual('blank', questions[0]['type_id'])
        self.assertEqual([31, 32, 33, 34], questions[0]['candidate_numbers'])
        self.assertEqual('review', questions[0]['classification_status'])
        self.assertIn('<b>Original</b>', questions[0]['question_html'])
        self.assertIn('<em>Because</em>', questions[0]['explanation_html'])
        self.assertEqual('order', questions[1]['type_id'])
        self.assertEqual([36, 37], questions[1]['candidate_numbers'])

    def test_html_splitter_keeps_nested_div_question_balanced(self):
        html = '<div class="question"><h2 class="prompt">다음 글의 제목으로 가장 적절한 것은?</h2><div class="passage"><div>Nested text</div></div><ol class="choices"><li>A</li><li>B</li></ol></div>'
        question = main.split_csat_html(html)[0]
        self.assertEqual('Nested text', question['passage'])
        self.assertEqual(['A', 'B'], question['choices'])
        self.assertTrue(question['question_html'].endswith('</div>'))

    def test_prompt_classifier_does_not_invent_unknown_number(self):
        result = main.classify_csat_prompt('정답을 고르시오.', [])
        self.assertEqual('review', result['type_id'])
        self.assertEqual([], result['candidate_numbers'])

    def test_html_batch_preview_accepts_multiple_files_without_writing_db(self):
        client = TestClient(main.app)
        with main.get_db() as conn:
            source_count = conn.execute('SELECT COUNT(*) FROM csat_question_sources').fetchone()[0]
        one = b'<article class="question"><h2 class="prompt">\xeb\x8b\xa4\xec\x9d\x8c \xea\xb8\x80\xec\x9d\x98 \xec\xa0\x9c\xeb\xaa\xa9\xec\x9c\xbc\xeb\xa1\x9c \xea\xb0\x80\xec\x9e\xa5 \xec\xa0\x81\xec\xa0\x88\xed\x95\x9c \xea\xb2\x83\xec\x9d\x80?</h2></article>'
        two = b'<article class="question"><h2 class="prompt">\xeb\x8b\xa4\xec\x9d\x8c \xea\xb8\x80\xec\x9d\x98 \xeb\x82\xaf\xeb\xa7\x90 \xec\xa4\x91 \xeb\xac\xb8\xeb\xa7\xa5\xec\x9d\x98 \xeb\xa7\xa5\xeb\x9d\xbd\xec\x97\x90\xec\x84\x9c \xeb\xb3\xb4\xec\x95\x84 \xec\x93\xb0\xec\x9e\x84\xec\x9d\xb4 \xec\xa0\x81\xec\xa0\x88\xed\x95\x98\xec\xa7\x80 \xec\x95\x8a\xec\x9d\x80 \xea\xb2\x83\xec\x9d\x80?</h2></article>'
        response = client.post('/api/csat/html-imports/preview', data={'exam_kind': 'half', 'metadata_json': '{}'},
                               files=[('files', ('01.html', one, 'text/html')), ('files', ('02.html', two, 'text/html'))])
        self.assertEqual(201, response.status_code)
        body = response.json()
        self.assertEqual(2, body['file_count'])
        self.assertEqual(2, body['question_count'])
        self.assertEqual(['01.html', '02.html'], [item['source_filename'] for item in body['files']])
        self.assertEqual(['READY', 'REVIEW'], [item['status'] for item in body['files']])
        with main.get_db() as conn:
            self.assertEqual(source_count, conn.execute('SELECT COUNT(*) FROM csat_question_sources').fetchone()[0])

    def test_html_batch_preview_isolates_failed_file_and_marks_duplicate_before_commit(self):
        client = TestClient(main.app)
        valid = b'<article class="question"><h2 class="prompt">\xeb\x8b\xa4\xec\x9d\x8c \xea\xb8\x80\xec\x9d\x98 \xec\xa0\x9c\xeb\xaa\xa9\xec\x9c\xbc\xeb\xa1\x9c \xea\xb0\x80\xec\x9e\xa5 \xec\xa0\x81\xec\xa0\x88\xed\x95\x9c \xea\xb2\x83\xec\x9d\x80?</h2></article>'
        mixed = client.post('/api/csat/html-imports/preview', data={'exam_kind': 'half', 'metadata_json': '{}'},
                            files=[('files', ('valid.html', valid, 'text/html')),
                                   ('files', ('broken.txt', b'not html', 'text/plain'))])
        self.assertEqual(201, mixed.status_code)
        self.assertEqual(['READY', 'FAILED'], [item['status'] for item in mixed.json()['files']])
        self.assertEqual(1, mixed.json()['failed_count'])
        preview = mixed.json()['files'][0]
        saved = client.post('/api/csat/imports', json={
            'exam_kind': 'half', 'source_filename': preview['source_filename'], 'source_hash': preview['source_hash'],
            'parser': preview['parser'], 'metadata': preview['metadata'], 'raw_text': preview['raw_text'],
            'questions': preview['questions'],
        })
        self.assertEqual(201, saved.status_code)
        repeated = client.post('/api/csat/html-imports/preview', data={'exam_kind': 'half', 'metadata_json': '{}'},
                               files=[('files', ('valid-again.html', valid, 'text/html'))])
        self.assertEqual('DUPLICATE', repeated.json()['files'][0]['status'])
        self.assertEqual(saved.json()['source_id'], repeated.json()['files'][0]['duplicate_source_id'])
        with main.get_db() as conn:
            conn.execute('DELETE FROM csat_questions WHERE source_id=?', (saved.json()['source_id'],))
            conn.execute('DELETE FROM csat_question_sources WHERE id=?', (saved.json()['source_id'],))

    def test_saved_assessment_is_the_only_path_that_records_usage(self):
        ids = self.seed_csat_questions(5, 34)
        client = TestClient(main.app)
        before = client.get('/api/csat/question-usage').json()
        preview = client.get('/api/csat/questions', params={'question_no': 34, 'exclude_used': True})
        self.assertEqual(200, preview.status_code)
        self.assertEqual(len(before), len(client.get('/api/csat/question-usage').json()))
        created = client.post('/api/csat/assessments', json={
            'assessment_type': 'half', 'title': 'Fixture HALF', 'question_ids': ids[:4], 'count': 4,
        })
        self.assertEqual(201, created.status_code)
        self.assertEqual(ids[:4], created.json()['question_ids'])
        usage = client.get('/api/csat/question-usage', params={'assessment_type': 'half'}).json()
        self.assertEqual(set(ids[:4]), {item['question_id'] for item in usage if item['assessment_id'] == created.json()['id']})
        remaining = client.get('/api/csat/questions', params={'question_no': 34, 'exclude_used': True}).json()
        self.assertIn(ids[4], [item['id'] for item in remaining])
        self.assertFalse(set(ids[:4]) & {item['id'] for item in remaining})

    def test_daily_problem_and_explanation_share_question_order_and_day_numbers(self):
        ids = self.seed_csat_questions(8, 39)
        client = TestClient(main.app)
        created = client.post('/api/csat/assessments', json={
            'assessment_type': 'daily', 'title': 'Fixture DAILY', 'question_ids': ids, 'days': 2,
        })
        self.assertEqual(201, created.status_code)
        body = created.json()
        self.assertEqual(ids, body['question_ids'])
        self.assertEqual([1, 1, 1, 1, 2, 2, 2, 2], [item['day_no'] for item in body['items']])
        problem = client.get(f"/api/csat/assessments/{body['id']}/problem.html").text
        explanation = client.get(f"/api/csat/assessments/{body['id']}/explanation.html").text
        self.assertIn('class="daily-page"', problem)
        self.assertIn('class="daily-answer-page"', explanation)
        self.assertIn('Original fixture 0', problem)
        self.assertIn('fixture explanation 0', explanation)
        for document in (problem, explanation):
            offsets = [document.index(f'data-question-id="{question_id}"') for question_id in ids]
            self.assertEqual(sorted(offsets), offsets)

        replacement = self.seed_csat_questions(1, 39)[0]
        changed = client.patch(f"/api/csat/assessments/{body['id']}/items/3", json={'question_id': replacement})
        self.assertEqual(200, changed.status_code)
        expected = [ids[0], ids[1], replacement, *ids[3:]]
        for path in ('problem.html', 'explanation.html'):
            document = client.get(f"/api/csat/assessments/{body['id']}/{path}").text
            offsets = [document.index(f'data-question-id="{question_id}"') for question_id in expected]
            self.assertEqual(sorted(offsets), offsets)
            self.assertNotIn(f'data-question-id="{ids[2]}"', document)

    def test_insufficient_exclude_used_selection_does_not_save_partial_assessment(self):
        ids = self.seed_csat_questions(1, 24)
        client = TestClient(main.app)
        first = client.post('/api/csat/assessments', json={'assessment_type': 'half', 'question_ids': ids, 'count': 1})
        self.assertEqual(201, first.status_code)
        with main.get_db() as conn:
            before = conn.execute('SELECT COUNT(*) FROM csat_assessments').fetchone()[0]
        failed = client.post('/api/csat/assessments', json={'assessment_type': 'half', 'question_no': 24, 'count': 1, 'exclude_used': True})
        self.assertEqual(409, failed.status_code)
        with main.get_db() as conn:
            self.assertEqual(before, conn.execute('SELECT COUNT(*) FROM csat_assessments').fetchone()[0])

    def test_wrong_answer_links_to_question_and_composes_without_pii_fixture(self):
        ids = self.seed_csat_questions(2, 21)
        client = TestClient(main.app)
        wrong_ids = []
        for index, question_id in enumerate(ids):
            response = client.post('/api/csat/wrong-answers', json={
                'question_id': question_id, 'student_name': f'TEST_STUDENT_{index + 1:03d}',
                'exam_name': 'TEST_EXAM', 'question_no': 21, 'wrong_answer': '2', 'correct_answer': '1',
            })
            self.assertEqual(201, response.status_code)
            self.assertEqual(question_id, response.json()['question_id'])
            wrong_ids.append(response.json()['id'])
        created = client.post('/api/csat/wrong-assessments', json={
            'title': 'Fixture WRONG', 'wrong_answer_ids': wrong_ids, 'exclude_used': False,
        })
        self.assertEqual(201, created.status_code)
        self.assertEqual(ids, created.json()['question_ids'])
        problem = client.get(f"/api/csat/assessments/{created.json()['id']}/problem.html").text
        explanation = client.get(f"/api/csat/assessments/{created.json()['id']}/explanation.html").text
        for question_id in ids:
            self.assertIn(f'data-question-id="{question_id}"', problem)
            self.assertIn(f'data-question-id="{question_id}"', explanation)

    def test_wrong_link_rejects_mismatched_question_number(self):
        question_id = self.seed_csat_questions(1, 34)[0]
        response = TestClient(main.app).post('/api/csat/wrong-answers', json={
            'question_id': question_id, 'student_name': 'TEST_STUDENT_999', 'question_no': 39,
        })
        self.assertEqual(400, response.status_code)

    def test_full_assessment_requires_one_question_for_every_number_18_to_45(self):
        for question_no in range(18, 46):
            self.seed_csat_questions(1, question_no)
        client = TestClient(main.app)
        created = client.post('/api/csat/assessments', json={
            'assessment_type': 'full', 'title': 'Fixture FULL', 'count': 1,
        })
        self.assertEqual(201, created.status_code, created.text)
        body = created.json()
        self.assertEqual(28, len(body['question_ids']))
        self.assertEqual(list(range(18, 46)), [item['question_no'] for item in body['items']])
        self.assertEqual(f"/problem-generator/wiki/api/csat/assessments/{body['id']}/problem.html", body['problem_html_url'])
        reopened = client.get(f"/api/csat/assessments/{body['id']}").json()
        self.assertEqual(body['question_ids'], reopened['question_ids'])
        for endpoint in ('problem.html', 'explanation.html'):
            document = client.get(f"/api/csat/assessments/{body['id']}/{endpoint}").text
            self.assertIn('data-template-id="csat-full-v1"', document)
            offsets = [document.index(f'data-question-id="{question_id}"') for question_id in body['question_ids']]
            self.assertEqual(sorted(offsets), offsets)

    def test_full_exclude_used_fails_atomically_when_any_number_is_missing(self):
        for question_no in range(18, 46):
            self.seed_csat_questions(1, question_no)
        client = TestClient(main.app)
        first = client.post('/api/csat/assessments', json={'assessment_type': 'full'})
        self.assertEqual(201, first.status_code)
        with main.get_db() as conn:
            now = '2026-01-01T00:00:00'
            conn.execute('''INSERT INTO csat_question_usage(question_id,assessment_id,assessment_type,used_at)
                SELECT q.id, ?, 'full', ? FROM csat_questions q
                WHERE NOT EXISTS (SELECT 1 FROM csat_question_usage u WHERE u.question_id=q.id)''', (first.json()['id'], now))
            before = conn.execute('SELECT COUNT(*) FROM csat_assessments').fetchone()[0]
        failed = client.post('/api/csat/assessments', json={'assessment_type': 'full', 'exclude_used': True})
        self.assertEqual(409, failed.status_code)
        with main.get_db() as conn:
            self.assertEqual(before, conn.execute('SELECT COUNT(*) FROM csat_assessments').fetchone()[0])


if __name__ == '__main__': unittest.main()
