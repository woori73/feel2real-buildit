import sys
import unittest
from pathlib import Path

from pydantic import ValidationError

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import question_contract as qc  # noqa: E402


class QuestionContractTest(unittest.TestCase):
    def test_transform_accepts_wiki_question_v1_without_dropping_legacy_fields(self):
        canonical = {
            'schema_version':'wiki-question-v1','kind':'mc','question_type':'밑줄 의미',
            'instruction':{'text':'밑줄 친 부분의 의미는?','html':'밑줄 친 부분의 의미는?'},
            'passage':{'text':'The answer matters.','html':'The <u>answer</u> matters.','status':'complete'},
            'choices':[{'number':i,'text':str(i),'html':str(i)} for i in range(1,6)],'conditions':[],
            'answer':{'choice_numbers':[1],'accepted_answers':[],'sample_answer':''},
            'explanation':{'text':'해설','html':'해설'},
            'scoring':{'score':4,'multi':False,'answer_format':'','grading_rule':'해설'},'source':{'source_question_number':'18'},
        }
        question={'type':'mc','questionType':'밑줄 의미','sourceText':'The answer matters.','stem':'밑줄 친 부분의 의미는?',
          'options':['1','2','3','4','5'],'answer':[1],'multi':False,'score':4,'conditions':[],'answerFormat':'',
          'acceptedAnswers':[],'sampleAnswer':'','gradingRule':'해설','canonical':canonical,'sourceQuestionNumber':'18'}
        result=qc.validate_transform({'examTitle':'self-contained 저장','questions':[question]})
        self.assertEqual('wiki-question-v1',result['questions'][0]['canonical']['schema_version'])

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
