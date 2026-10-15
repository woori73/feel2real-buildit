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


def _mc_question(**overrides):
    base = {
        'type': 'mc', 'questionType': '어법', 'sourceText': '지문',
        'stem': '다음 중 어법상 옳은 것은?', 'options': ['A', 'B', 'C', 'D', 'E'],
        'answer': [1], 'multi': False, 'score': 4,
        'conditions': [], 'answerFormat': '', 'acceptedAnswers': [], 'sampleAnswer': '', 'gradingRule': '',
    }
    base.update(overrides)
    return base


def _sa_question(**overrides):
    base = {
        'type': 'sa', 'questionType': '서술형', 'sourceText': '지문',
        'stem': '다음을 영작하시오.', 'options': [], 'answer': [], 'multi': False, 'score': 6,
        'conditions': ['5단어 이상'], 'answerFormat': '완전한 문장',
        'acceptedAnswers': ['I go to school.'], 'sampleAnswer': 'I go to school.', 'gradingRule': '철자와 문법 일치',
    }
    base.update(overrides)
    return base


class SaveGeneratedQuestionsTest(unittest.TestCase):
    """save_generated_questions는 새 스키마/새 테이블을 만들지 않고 기존 question_contract.py의
    TransformQuestion 계약과 main._save_generated()를 그대로 재사용해야 한다."""

    CTX = dict(school='신구중', grade='중2', year=2026, semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp_dir.cleanup()

    def test_valid_questions_round_trip_through_final_context(self):
        result = mcp_server.save_generated_questions(
            school='신구중', grade='중2', exam='중간고사',
            questions=[_mc_question(), _sa_question()], exam_title='MCP 저장 시험지')

        self.assertEqual(2, len(result['questions']))
        self.assertEqual('mcp', result['questions'][0]['mode'])

        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(2, len(ctx['generated_questions']))
        self.assertEqual({'mcp'}, {row['mode'] for row in ctx['generated_questions']})
        stems = {row['stem'] for row in ctx['generated_questions']}
        self.assertIn('다음 중 어법상 옳은 것은?', stems)
        self.assertIn('다음을 영작하시오.', stems)

    def test_invalid_question_is_rejected_and_nothing_is_saved(self):
        """all-or-nothing: 검증 실패 문항이 하나라도 있으면 정상 문항도 조용히 저장되지 않는다."""
        broken = _mc_question()
        del broken['answer']
        with self.assertRaises(ValueError):
            mcp_server.save_generated_questions(
                school='신구중', grade='중2', exam='중간고사', questions=[_mc_question(), broken])

        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual([], ctx['generated_questions'])

    def test_empty_questions_list_is_rejected(self):
        with self.assertRaises(ValueError):
            mcp_server.save_generated_questions(school='신구중', grade='중2', exam='중간고사', questions=[])

    def test_unknown_extra_field_is_rejected_by_existing_contract(self):
        """MCP 전용 새 필드를 만들지 않는다 — 기존 계약(extra='forbid')이 그대로 거부해야 한다."""
        with self.assertRaises(ValueError):
            mcp_server.save_generated_questions(
                school='신구중', grade='중2', exam='중간고사',
                questions=[_mc_question(mcpAuthor='claude')])

    def test_mode_mcp_does_not_collide_with_existing_ai_generated_rows(self):
        with main.get_db() as conn:
            conn.execute(
                "INSERT INTO generated_questions (school, grade, exam, mode, question_type, stem, created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                ('신구중', '중2', '중간고사', 'new', '어법', 'AI 생성 문항', '2026-01-01T00:00:00'))
        mcp_server.save_generated_questions(
            school='신구중', grade='중2', exam='중간고사', questions=[_mc_question()])

        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(2, len(ctx['generated_questions']))
        modes = sorted(row['mode'] for row in ctx['generated_questions'])
        self.assertEqual(['mcp', 'new'], modes)


class SaveClassInsightsTest(unittest.TestCase):
    """save_class_insights는 새 테이블 없이 기존 educator_insights/create_educator_insight를
    그대로 재사용하고, source 허용값만 claude/chatgpt로 넓혀야 한다."""

    CTX = dict(school='신구중', grade='중2', year=2026, semester='2학기', exam='중간고사')

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH = str(Path(self.temp_dir.name) / 'test.db')
        main.init_db(); main.migrate_schema()

    def tearDown(self): self.temp_dir.cleanup()

    def test_claude_insight_round_trips_through_final_context(self):
        mcp_server.save_class_insights(content='Claude가 문제 제작 중 발견한 패턴', source='claude', **self.CTX)
        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(1, len(ctx['class_insights']))
        self.assertEqual('claude', ctx['class_insights'][0]['source'])

    def test_chatgpt_insight_round_trips_through_final_context(self):
        mcp_server.save_class_insights(content='ChatGPT가 검토한 인사이트', source='chatgpt', **self.CTX)
        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(1, len(ctx['class_insights']))
        self.assertEqual('chatgpt', ctx['class_insights'][0]['source'])

    def test_director_and_school_teacher_sources_still_work_unchanged(self):
        """기존 director/school_teacher 동작(및 school_teacher 우선 정렬)은 그대로 유지된다."""
        mcp_server.save_class_insights(content='박원장 메모', source='director', **self.CTX)
        mcp_server.save_class_insights(content='학교 선생님 힌트', source='school_teacher', **self.CTX)
        ctx = main.get_final_workspace_context(**self.CTX)
        sources = sorted(item['source'] for item in ctx['class_insights'])
        self.assertEqual(['director', 'school_teacher'], sources)
        self.assertEqual('school_teacher', ctx['class_insights'][0]['source'])

    def test_invalid_source_is_rejected(self):
        with self.assertRaises(ValueError):
            mcp_server.save_class_insights(content='잘못된 출처', source='intern', **self.CTX)

    def test_all_four_sources_coexist_and_are_distinguishable(self):
        for source in ('director', 'school_teacher', 'claude', 'chatgpt'):
            mcp_server.save_class_insights(content=f'{source} 인사이트', source=source, **self.CTX)
        ctx = main.get_final_workspace_context(**self.CTX)
        self.assertEqual(4, len(ctx['class_insights']))
        self.assertEqual({'director', 'school_teacher', 'claude', 'chatgpt'},
                          {item['source'] for item in ctx['class_insights']})


if __name__ == '__main__':
    unittest.main()
