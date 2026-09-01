import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ.setdefault('BUILDIT_WIKI_DB_PATH', str(Path(tempfile.gettempdir()) / 'buildit-wiki-v1-bootstrap.db'))
os.environ.setdefault('BUILDIT_WIKI_WORKSPACE', tempfile.gettempdir())
import main  # noqa: E402
import mcp_server  # noqa: E402


def _mc(word, **overrides):
    base = {
        'type': 'mc', 'questionType': '문맥 어휘', 'sourceText': f'The climate {word} example sentence.',
        'stem': f'밑줄 친 {word}의 의미로 가장 적절한 것은?', 'options': ['A', 'B', 'C', 'D', 'E'],
        'answer': [1], 'multi': False, 'score': 4,
        'conditions': [], 'answerFormat': '', 'acceptedAnswers': [], 'sampleAnswer': '', 'gradingRule': '',
    }
    base.update(overrides)
    return base


VOCAB25 = [
    'gradually', 'adapt', 'be accustomed to', 'perceive', 'accumulate', 'emission', 'greenhouse gas',
    'global average', 'consequence', 'net zero', 'significant', 'scalding', 'trap', 'release',
    'fossil fuel', 'carbon footprint', 'offset', 'catastrophic', 'be prone to', 'be oblivious to',
    'insidious', 'mitigate', 'threshold', 'exacerbate', 'impending',
]


class GeneratedTestStoreTest(unittest.TestCase):
    CTX = dict(school='신사중', grade='3학년', year=2026, semester='2학기', exam='전체')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema(); main.seed_default_twins()

    def tearDown(self): self.temp_dir.cleanup()

    def _counts(self):
        with main.get_db() as conn:
            return {
                'source_materials': conn.execute('SELECT COUNT(*) n FROM source_materials').fetchone()['n'],
                'generated_tests': conn.execute('SELECT COUNT(*) n FROM generated_tests').fetchone()['n'],
                'generated_questions': conn.execute('SELECT COUNT(*) n FROM generated_questions').fetchone()['n'],
            }

    def _save(self, **overrides):
        payload = dict(
            school='신사중', grade='3학년', source_material='The Boiling Frog Myth', source_page='2',
            test_type='vocabulary', title='Boiling Frog Vocabulary Test #1',
            digital_twin='신사중 3학년 출제자 디지털트윈', twin_pattern='복수정답·문맥어휘·개념전이',
            difficulty='실제 시험 수준의 중상 난이도', target_vocabulary=VOCAB25, material_type='ted_ed',
            year=2026, semester='2학기', exam='전체',
            questions=[_mc(w) for w in VOCAB25],
        )
        payload.update(overrides)
        return mcp_server.save_generated_test(**payload)

    def test_full_25_question_test_round_trips(self):
        result = self._save()
        self.assertTrue(result['saved'])
        self.assertFalse(result['duplicate'])
        self.assertEqual(25, result['question_count'])
        self.assertEqual('The Boiling Frog Myth', result['source_material'])
        self.assertEqual('2', result['source_page'])

        counts = self._counts()
        self.assertEqual(1, counts['source_materials'])
        self.assertEqual(1, counts['generated_tests'])
        self.assertEqual(25, counts['generated_questions'])

        detail = main.get_generated_test(result['test_id'])
        self.assertEqual(25, len(detail['questions']))
        self.assertEqual('2', detail['test']['source_page'])
        self.assertEqual('신사중 3학년 출제자 디지털트윈', detail['test']['twin_label'])
        self.assertEqual(1, detail['test']['twin_id'])  # seeded 신사중 3학년 Twin
        self.assertEqual(25, len(detail['test']['target_vocabulary']))
        self.assertTrue(all(q['test_id'] == result['test_id'] for q in detail['questions']))
        self.assertEqual({'mcp'}, {q['mode'] for q in detail['questions']})

    def test_idempotent_resave_returns_same_test_without_duplicates(self):
        first = self._save()
        second = self._save()
        self.assertEqual(first['test_id'], second['test_id'])
        self.assertTrue(second['duplicate'])
        counts = self._counts()
        self.assertEqual(1, counts['generated_tests'])
        self.assertEqual(25, counts['generated_questions'])

    def test_explicit_external_id_dedupes(self):
        self._save(external_id='sinsa-boilingfrog-vocab-1')
        self._save(external_id='sinsa-boilingfrog-vocab-1', questions=[_mc('gradually')])
        self.assertEqual(1, self._counts()['generated_tests'])

    def test_invalid_question_saves_nothing(self):
        broken = _mc('adapt'); del broken['answer']
        with self.assertRaises(ValueError):
            self._save(questions=[_mc('gradually'), broken])
        self.assertEqual(
            {'source_materials': 0, 'generated_tests': 0, 'generated_questions': 0}, self._counts())

    def test_second_test_reuses_same_source_material(self):
        self._save(title='Vocabulary Test #1', external_id='v1')
        self._save(title='Vocabulary Test #2', external_id='v2', questions=[_mc(w) for w in VOCAB25[:10]])
        counts = self._counts()
        self.assertEqual(1, counts['source_materials'])
        self.assertEqual(2, counts['generated_tests'])
        material_id = main.list_source_materials(school='신사중', grade='3학년')[0]['id']
        tests = main.list_generated_tests_for_material(material_id)
        self.assertEqual({'Vocabulary Test #1', 'Vocabulary Test #2'}, {t['title'] for t in tests})

    def test_storage_failure_is_reported_distinct_from_generation(self):
        original = main._save_generated
        main._save_generated = lambda *a, **k: (_ for _ in ()).throw(RuntimeError('disk full'))
        try:
            with self.assertRaises(ValueError) as ctx:
                self._save(external_id='will-fail')
            self.assertIn('storage: failed', str(ctx.exception))
        finally:
            main._save_generated = original
        self.assertEqual(
            {'source_materials': 0, 'generated_tests': 0, 'generated_questions': 0}, self._counts())

    def test_existing_save_generated_questions_still_works_and_context_keys_intact(self):
        mcp_server.save_generated_questions(
            school='신사중', grade='3학년', exam='전체', questions=[_mc('gradually')])
        self._save()
        ctx = main.get_final_workspace_context(**self.CTX)
        for key in ('digital_twin', 'school_prints', 'student_errors', 'class_insights',
                    'generated_questions', 'final_deliverables', 'generated_tests'):
            self.assertIn(key, ctx)
        self.assertEqual(1, len(ctx['generated_tests']))
        self.assertEqual('The Boiling Frog Myth', ctx['generated_tests'][0]['source_material'])
        # save_generated_questions 문항은 test_id 없이 그대로 저장된다.
        loose = [q for q in ctx['generated_questions'] if q.get('test_id') is None]
        self.assertTrue(loose)


class ManualQuestionSaveTest(unittest.TestCase):
    """강사 문제 DB 「문제 저장하기」 — POST /api/questions. AI 호출 없이 기존 계약 재사용."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema(); main.seed_default_twins()
        from fastapi.testclient import TestClient
        self.c = TestClient(main.app)

    def tearDown(self): self.temp_dir.cleanup()

    def test_flat_manual_save_appends_to_question_db(self):
        r = self.c.post('/api/questions', json={
            'school': '신사중', 'grade': '3학년', 'exam': '전체', 'exam_title': '수동 붙여넣기',
            'questions': [_mc('gradually'), _sa_manual()]})
        self.assertEqual(201, r.status_code)
        self.assertEqual(2, r.json()['question_count'])
        rows = self.c.get('/api/questions').json()
        self.assertEqual(2, len(rows))
        self.assertEqual({'manual'}, {q['mode'] for q in rows})

    def test_manual_save_with_source_material_links_generated_test(self):
        r = self.c.post('/api/questions', json={
            'school': '신사중', 'grade': '3학년', 'exam': '전체',
            'source_material': 'The Boiling Frog Myth', 'source_page': '2',
            'digital_twin': '신사중 3학년 출제자 Digital Twin',
            'questions': [_mc('gradually'), _mc('adapt')]})
        self.assertEqual(201, r.status_code)
        self.assertEqual(2, r.json()['question_count'])
        self.assertIsNotNone(r.json().get('test_id'))
        sm = main.list_source_materials(school='신사중', grade='3학년')
        self.assertEqual(1, len(sm))
        self.assertEqual('The Boiling Frog Myth', sm[0]['title'])
        rows = self.c.get('/api/questions').json()
        self.assertEqual({'manual'}, {q['mode'] for q in rows})  # MCP 저장과 구분됨
        detail = main.get_generated_test(r.json()['test_id'])
        self.assertEqual('manual', detail['test']['generation_source'])

    def test_invalid_questions_rejected_nothing_saved(self):
        r = self.c.post('/api/questions', json={'school': 's', 'grade': 'g', 'questions': [{'type': 'mc'}]})
        self.assertEqual(422, r.status_code)
        self.assertEqual([], self.c.get('/api/questions').json())


def _sa_manual(**o):
    b = {'type': 'sa', 'questionType': '영작', 'sourceText': '', 'stem': 'gradually로 문장 쓰기',
         'options': [], 'answer': [], 'multi': False, 'score': 5, 'conditions': [],
         'answerFormat': '완전한 문장', 'acceptedAnswers': ['It rose gradually.'],
         'sampleAnswer': 'It rose gradually.', 'gradingRule': '문법·의미 일치'}
    b.update(o)
    return b


class McpToolDiscoveryTest(unittest.TestCase):
    """save_generated_test는 MCP tool 목록에 실제로 노출되어야 한다(코드에 함수만 있는 것과 다름)."""

    def test_save_generated_test_is_registered(self):
        import asyncio
        tools = asyncio.new_event_loop().run_until_complete(mcp_server.mcp.list_tools())
        names = {t.name for t in tools}
        self.assertIn('save_generated_test', names)
        self.assertIn('save_generated_questions', names)  # 기존 tool 회귀 없음


LEGACY_GID = 'mcp_f5061b3f4b5b43778291b2c1e25c37fc'


class LegacyBackfillTest(unittest.TestCase):
    """PR #63 이전 save_generated_questions로 저장된 30문항(운영 exam_group_id
    mcp_f5061b3f4b5b43778291b2c1e25c37fc)을 Generated Test에 연결한다. 문항은 복제/재생성하지
    않고 test_id만 채운다. sourceText의 key:value Digital Twin 태그를 정식 필드로 옮긴다."""

    # 운영 실제 태그 형식 (대소문자 혼용, 7 필드)
    def _tag(self, i, word, page):
        return (f'[문항ID:Q{i} | 대상어휘:{word} | 어휘등급:CHALLENGE | 난이도:상 | Page:{page} | '
                f'DigitalTwin근거:조건 제한형 영작 (Digital Twin 명시 유형) | DigitalTwin패턴:조건 제한형 영작]')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema(); main.seed_default_twins()
        now = '2026-08-28T12:00:00'
        words = VOCAB25 + VOCAB25[:5]  # 30문항 / 25 distinct vocab
        with main.get_db() as conn:
            for i, word in enumerate(words, start=1):
                conn.execute(
                    "INSERT INTO generated_questions (school, grade, exam, mode, q_kind, question_type, stem, "
                    "source_text, options_json, answer_json, score, exam_title, exam_group_id, raw_json, created_at) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    ('신사중', '3', '중간고사', 'mcp', 'mc', '문맥 어휘', f'{word} 뜻은?',
                     f"Page{2 + i % 5} 원문 인용 {self._tag(i, word, 2 + i % 5)}",
                     '["a","b","c","d","e"]', '[1]', 4, 'The Boiling Frog Myth — Page 2 Vocabulary Test',
                     LEGACY_GID, '{}', now))
            # id 83: 짧은 'test' dummy — 삭제 대상. id 84: 다른 group의 정상 문항 — 삭제 금지.
            conn.execute("INSERT INTO generated_questions (id, mode, stem, source_text, exam_group_id, created_at) "
                         "VALUES (83, 'mcp', 'test', '', 'mcp_diag_a', ?)", (now,))
            conn.execute("INSERT INTO generated_questions (id, mode, q_kind, stem, source_text, exam_group_id, created_at) "
                         "VALUES (84, 'mcp', 'mc', 'emission 뜻은?', "
                         "'emission page2 must-know vocabulary table [대상어휘:emission | 어휘등급:must-know | 난이도:하 | page:2 | digitaltwin근거:단순 회상형 | digitaltwin패턴:기본 어휘 확인]', "
                         "'mcp_ac5952a638a64f74a7080548ea8ca606', ?)", (now,))

    def tearDown(self): self.temp_dir.cleanup()

    def _counts(self):
        with main.get_db() as conn:
            return (
                conn.execute('SELECT COUNT(*) n FROM generated_tests').fetchone()['n'],
                conn.execute('SELECT COUNT(*) n FROM generated_questions WHERE test_id IS NOT NULL').fetchone()['n'],
                conn.execute('SELECT COUNT(*) n FROM generated_questions').fetchone()['n'],
            )

    def test_backfill_links_30_parses_kv_tag_and_removes_only_true_diagnostic(self):
        main.backfill_legacy_generated_tests()
        tests, linked, total = self._counts()
        self.assertEqual(1, tests)
        self.assertEqual(30, linked)
        self.assertEqual(31, total)  # id 83만 삭제(30 group + id 84)

        with main.get_db() as conn:
            t = dict(conn.execute('SELECT * FROM generated_tests').fetchone())
            self.assertEqual('legacy-migrate-' + LEGACY_GID, t['external_id'])
            self.assertEqual(30, t['question_count'])
            self.assertEqual('The Boiling Frog Myth — Page 2 Vocabulary Test', t['title'])
            self.assertEqual('2', t['source_page'])
            self.assertEqual(1, t['twin_id'])
            self.assertEqual('신사중 3학년 출제자 디지털트윈', t['twin_label'])
            self.assertEqual('복수정답·문학개념 전이·심리/담화 추론·조건형 영작', t['twin_pattern'])
            self.assertEqual(25, len(json.loads(t['target_vocabulary'])))  # kv 태그 파싱 성공
            self.assertIsNone(conn.execute('SELECT id FROM generated_questions WHERE id = 83').fetchone())
            self.assertIsNotNone(conn.execute('SELECT id FROM generated_questions WHERE id = 84').fetchone())  # 삭제 안 함
            q = dict(conn.execute('SELECT * FROM generated_questions WHERE exam_group_id=? LIMIT 1', (LEGACY_GID,)).fetchone())
            self.assertEqual(('신사중', '3학년', '전체'), (q['school'], q['grade'], q['exam']))
            self.assertEqual(t['id'], q['test_id'])
            audit = conn.execute("SELECT content FROM educator_insights WHERE source='claude'").fetchone()
            self.assertIn('linked 30', audit['content'])

        sm_id = main.list_source_materials(school='신사중', grade='3학년')[0]['id']
        detail = main.get_generated_test(main.list_generated_tests_for_material(sm_id)[0]['id'])
        self.assertEqual(30, len(detail['questions']))

    def test_backfill_repairs_empty_metadata_on_already_linked_test(self):
        # PR #64 이전 backfill이 태그 파싱에 실패해 target_vocabulary가 빈 상태로 연결된 경우 재보강.
        with main.get_db() as conn:
            conn.execute('UPDATE generated_questions SET test_id = 999 WHERE exam_group_id = ?', (LEGACY_GID,))
            conn.execute(
                "INSERT INTO generated_tests (id, external_id, source_material_id, school, grade, "
                "test_type, title, twin_id, twin_label, twin_pattern, target_vocabulary, question_count, created_at) "
                "VALUES (999, ?, 1, '신사중', '3학년', 'vocabulary', 't', 1, '', '', '[]', 30, '2026-08-28T00:00:00')",
                ('legacy-migrate-' + LEGACY_GID,))
            conn.execute("INSERT INTO source_materials (id, school, grade, title, created_at) "
                         "VALUES (1, '신사중', '3학년', 'The Boiling Frog Myth', '2026-08-28T00:00:00')")
        main.backfill_legacy_generated_tests()
        with main.get_db() as conn:
            t = dict(conn.execute('SELECT * FROM generated_tests WHERE id = 999').fetchone())
            self.assertEqual(25, len(json.loads(t['target_vocabulary'])))
            self.assertEqual('신사중 3학년 출제자 디지털트윈', t['twin_label'])
            self.assertTrue(t['twin_pattern'])
            self.assertEqual(1, conn.execute('SELECT COUNT(*) n FROM generated_tests').fetchone()['n'])  # 중복 없음

    def test_backfill_is_idempotent(self):
        main.backfill_legacy_generated_tests()
        main.backfill_legacy_generated_tests()
        tests, linked, _ = self._counts()
        self.assertEqual(1, tests)
        self.assertEqual(30, linked)
        with main.get_db() as conn:
            self.assertEqual(1, conn.execute("SELECT COUNT(*) n FROM educator_insights WHERE source='claude'").fetchone()['n'])
            self.assertEqual(1, conn.execute("SELECT COUNT(*) n FROM source_materials").fetchone()['n'])


class SelfContainedQuestionStoreTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir=tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH=str(Path(self.temp_dir.name)/'self-contained.db');main.init_db();main.migrate_schema()
    def tearDown(self): self.temp_dir.cleanup()

    def test_canonical_round_trip_by_qid_keeps_full_markup(self):
        passage='The problem is more complicated than it appears.'
        canonical={'schema_version':'wiki-question-v1','kind':'mc','question_type':'밑줄 의미',
          'instruction':{'text':'밑줄 친 부분의 의미는?','html':'밑줄 친 부분의 의미는?'},
          'passage':{'text':passage,'html':'The problem is <u>more complicated than it appears</u>.','status':'complete'},
          'choices':[{'number':i,'text':f'choice {i}','html':f'<strong>choice {i}</strong>'} for i in range(1,6)],
          'conditions':[{'text':'한 개를 고르시오','html':'<div class="box">한 개를 고르시오</div>'}],
          'answer':{'choice_numbers':[1],'accepted_answers':[],'sample_answer':''},'explanation':{'text':'해설','html':'<p>해설</p>'},
          'scoring':{'score':4,'multi':False,'answer_format':'','grading_rule':'해설'},'source':{'source_question_number':'18'}}
        legacy=_mc('answer',canonical=canonical,sourceQuestionNumber='18')
        with main.get_db() as conn: saved=main._save_generated(conn,None,'manual',[legacy],'시험','self-contained')
        q=main.get_question(saved[0]['id'])
        self.assertEqual('wiki-question-v1',q['schema_version']);self.assertEqual(passage,q['canonical']['passage']['text'])
        self.assertIn('<u>more complicated',q['canonical']['passage']['html']);self.assertEqual(5,len(q['canonical']['choices']))


if __name__ == '__main__':
    unittest.main()
