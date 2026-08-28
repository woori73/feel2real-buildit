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
    """PR #63 이전 save_generated_questions로 저장된 31문항을 Generated Test에 연결한다.
    문항은 복제/재생성하지 않고 test_id만 채운다. id 83/84 진단 dummy는 삭제한다."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema(); main.seed_default_twins()
        now = '2026-08-28T12:00:00'
        with main.get_db() as conn:
            for i, word in enumerate(VOCAB25 + VOCAB25[:6]):  # 31문항
                tag = f'[{word} | must-know | 실제 시험 수준의 중상 난이도 | 2 | 신사중 3학년 출제자 디지털트윈 | 복수정답·문맥어휘·개념전이]'
                conn.execute(
                    "INSERT INTO generated_questions (school, grade, exam, mode, q_kind, question_type, stem, "
                    "source_text, options_json, answer_json, score, exam_title, exam_group_id, raw_json, created_at) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    ('신사중', '3', '중간고사', 'mcp', 'mc', '문맥 어휘', f'{word} 뜻은?',
                     f'The passage uses {word}. {tag}', '["a","b","c","d","e"]', '[1]', 4,
                     'Boiling Frog Vocab', LEGACY_GID, '{}', now))
            conn.execute("INSERT INTO generated_questions (id, mode, stem, source_text, exam_group_id, created_at) "
                         "VALUES (83, 'mcp', 'test', '', 'mcp_diagnostic_a', ?)", (now,))
            conn.execute("INSERT INTO generated_questions (id, mode, stem, source_text, exam_group_id, created_at) "
                         "VALUES (84, 'mcp', 'test3', '', 'mcp_diagnostic_b', ?)", (now,))

    def tearDown(self): self.temp_dir.cleanup()

    def _counts(self):
        with main.get_db() as conn:
            return (
                conn.execute('SELECT COUNT(*) n FROM generated_tests').fetchone()['n'],
                conn.execute('SELECT COUNT(*) n FROM generated_questions WHERE test_id IS NOT NULL').fetchone()['n'],
                conn.execute('SELECT COUNT(*) n FROM generated_questions').fetchone()['n'],
            )

    def test_backfill_links_31_and_removes_diagnostics(self):
        main.backfill_legacy_generated_tests()
        tests, linked, total = self._counts()
        self.assertEqual(1, tests)
        self.assertEqual(31, linked)
        self.assertEqual(31, total)  # 83, 84 삭제됨

        with main.get_db() as conn:
            t = dict(conn.execute('SELECT * FROM generated_tests').fetchone())
            self.assertEqual('legacy-migrate-' + LEGACY_GID, t['external_id'])
            self.assertEqual(31, t['question_count'])
            self.assertEqual('The Boiling Frog Myth — Page 2 Vocabulary Test', t['title'])
            self.assertEqual('2', t['source_page'])
            self.assertEqual(1, t['twin_id'])
            self.assertEqual('신사중 3학년 출제자 디지털트윈', t['twin_label'])
            self.assertEqual(25, len(json.loads(t['target_vocabulary'])))
            self.assertIsNone(conn.execute('SELECT id FROM generated_questions WHERE id IN (83,84)').fetchone())
            q = dict(conn.execute('SELECT * FROM generated_questions WHERE exam_group_id=? LIMIT 1', (LEGACY_GID,)).fetchone())
            self.assertEqual(('신사중', '3학년', '전체'), (q['school'], q['grade'], q['exam']))
            self.assertEqual(t['id'], q['test_id'])
            audit = conn.execute("SELECT content FROM educator_insights WHERE source='claude'").fetchone()
            self.assertIn('linked 31', audit['content'])

        sm_id = main.list_source_materials(school='신사중', grade='3학년')[0]['id']
        detail = main.get_generated_test(main.list_generated_tests_for_material(sm_id)[0]['id'])
        self.assertEqual(31, len(detail['questions']))

    def test_backfill_is_idempotent(self):
        main.backfill_legacy_generated_tests()
        main.backfill_legacy_generated_tests()
        tests, linked, total = self._counts()
        self.assertEqual(1, tests)
        self.assertEqual(31, linked)
        with main.get_db() as conn:
            self.assertEqual(1, conn.execute("SELECT COUNT(*) n FROM educator_insights WHERE source='claude'").fetchone()['n'])
            self.assertEqual(1, conn.execute("SELECT COUNT(*) n FROM source_materials").fetchone()['n'])


if __name__ == '__main__':
    unittest.main()
