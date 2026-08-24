import sys
import unittest
from pathlib import Path

from pydantic import ValidationError

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import question_contract as qc  # noqa: E402


class QuestionContractTest(unittest.TestCase):
    def test_documented_snake_case_aliases_are_normalized_without_coercion(self):
        value = {'exam_title': '시험', 'mc_questions': [{
            'question_type': 'grammar', 'source_text': 'Passage', 'stem': '문제',
            'options': ['A', 'B', 'C', 'D', 'E'], 'answer': [1], 'multi': False, 'score': 2,
        }], 'sa_questions': []}
        result = qc.validate_new(value)
        self.assertEqual('grammar', result['mcQuestions'][0]['questionType'])

    def test_strict_validation_rejects_type_coercion_and_unknown_fields(self):
        value = {'examTitle': '시험', 'mcQuestions': [{
            'questionType': 'grammar', 'sourceText': 'Passage', 'stem': '문제',
            'options': ['A', 'B', 'C', 'D', 'E'], 'answer': ['1'], 'multi': False, 'score': 2,
            'unexpected': True,
        }], 'saQuestions': []}
        with self.assertRaises(ValidationError):
            qc.validate_new(value)


if __name__ == '__main__':
    unittest.main()
