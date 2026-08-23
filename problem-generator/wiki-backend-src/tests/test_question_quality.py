import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))

import prompt_logic as pl  # noqa: E402


class QuestionQualityTest(unittest.TestCase):
    def test_long_clause_underline_is_rejected(self):
        question = {
            'type': 'mc', 'questionType': 'grammar_underlined', 'stem': '밑줄 친 부분을 고르시오.',
            'sourceText': '①[[u]]first came[[/u]] ②[[u]]learning Korean[[/u]] ③[[u]]teach in Korean[[/u]] '
                          '④[[u]]helped the Korean independence movement and watched the police[[/u]] ⑤[[u]]had to go[[/u]]',
            'options': [], 'answer': [2],
        }
        self.assertTrue(any('1~6단어' in issue for issue in pl.question_quality_issues(question)))

    def test_sequence_without_three_paragraphs_is_rejected(self):
        question = {
            'type': 'mc', 'questionType': 'sequence', 'stem': '이어질 순서를 고르시오.',
            'sourceText': '(A) first (B) second', 'options': ['A-B-C'] * 5, 'answer': [1],
        }
        self.assertTrue(any('(A), (B), (C)' in issue for issue in pl.question_quality_issues(question)))

    def test_regular_multiple_choice_requires_five_options(self):
        question = {
            'type': 'mc', 'questionType': 'definition', 'stem': '영영풀이를 고르시오.',
            'sourceText': '', 'options': [], 'answer': [5],
        }
        self.assertTrue(any('선택지 5개' in issue for issue in pl.question_quality_issues(question)))

    def test_short_answer_rejects_dangling_conjunction_and_ellipsis(self):
        question = {
            'type': 'sa', 'questionType': 'writing', 'stem': '완전한 문장으로 영작하시오.',
            'sampleAnswer': 'He wanted to teach in Korean because...',
            'acceptedAnswers': ['He wanted to teach in Korean because'],
        }
        self.assertTrue(any('완결' in issue for issue in pl.question_quality_issues(question)))

    def test_short_answer_enforces_requested_word_count(self):
        question = {
            'type': 'sa', 'questionType': 'writing', 'stem': '다음 의미가 되도록 7개 단어로 영작하시오.',
            'sampleAnswer': 'He wanted to teach Korean to his students.',
            'acceptedAnswers': ['He wanted to teach Korean to his students.'],
        }
        self.assertTrue(any('7단어' in issue for issue in pl.question_quality_issues(question)))


if __name__ == '__main__':
    unittest.main()
