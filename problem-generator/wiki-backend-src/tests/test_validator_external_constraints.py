import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / 'backend'))
import prompt_logic as pl  # noqa: E402


class ValidatorExternalConstraintsTest(unittest.TestCase):
    def test_grammar_focus_forbidden_token_is_enforced_when_model_omits_condition(self):
        question = {
            'type': 'sa',
            'stem': '다음 문장을 같은 의미로 바꾸어 쓰시오.',
            'conditions': [],
            'grammarFocus': 'because 절 사용 금지',
            'answerFormat': '완전한 영어 문장',
            'sampleAnswer': 'Mina walked to school because she missed the bus.',
            'acceptedAnswers': [],
            'gradingRule': 'because를 포함할 것.',
        }

        issues = pl.question_quality_issues(question)

        self.assertIn('because 사용 금지 조건과 모범답안이 모순됨', issues)
        self.assertIn('because 사용 금지 조건과 채점 기준이 모순됨', issues)


if __name__ == '__main__':
    unittest.main()
