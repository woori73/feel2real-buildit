import json
import sys
import unittest
from pathlib import Path
BACKEND=Path(__file__).resolve().parents[1]/'backend';sys.path.insert(0,str(BACKEND))
import private_reference_bank as bank  # noqa: E402

def marker(kind):
    return 'QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: '+json.dumps({'questionWiki':True,'questionWikiId':31,'grade':2,'examYear':2026,'questionNo':31,'type':kind},separators=(',',':'))
def result(kind,choices=None,instruction='다음 조건에 맞게 문장을 쓰시오.',passage='English passage'):
    return json.dumps({'type':kind,'instruction':instruction,'passage':passage,'choices':choices or [],'answer':'answer','explanation':'explanation','sourceQuestionId':31,'generationMode':'OPENROUTER_FREE'},ensure_ascii=False)

class QuestionWikiTypeContractTest(unittest.TestCase):
    def test_written_forbids_multiple_choice_options(self):
        schema=bank.question_wiki_schema(marker('written'));self.assertEqual(['written'],schema['properties']['type']['enum']);self.assertEqual(0,schema['properties']['choices']['maxItems']);self.assertEqual(1,schema['properties']['instruction']['minLength']);self.assertEqual(1,schema['properties']['answer']['minLength']);self.assertEqual(1,schema['properties']['explanation']['minLength'])
    def test_multiple_choice_types_require_exactly_five_options(self):
        for kind in ('similar','grammar','vocabulary','blank','order','insertion','title-topic'):
            with self.subTest(kind=kind):
                choices=bank.question_wiki_schema(marker(kind))['properties']['choices'];self.assertEqual(5,choices['minItems']);self.assertEqual(5,choices['maxItems'])
    def test_generic_request_has_no_question_wiki_schema(self):self.assertIsNone(bank.question_wiki_schema('ordinary generic AI request'))
    def test_written_mechanics_explicitly_require_constructed_response(self):
        mechanics=bank._TYPE_MECHANICS['written'];self.assertIn('CONSTRUCTED RESPONSE ONLY',mechanics);self.assertIn('no multiple-choice options',mechanics);self.assertIn('answer/explanation',mechanics)
    def test_written_output_rejects_choices(self):
        issues=bank.output_issues(marker('written'),result('written',['a','b','c','d','e']));self.assertIn('WRITTEN_HAS_CHOICES',issues)
    def test_written_output_rejects_multiple_choice_instruction(self):
        issues=bank.output_issues(marker('written'),result('written',instruction='다음 중 가장 적절한 것을 고르시오.'));self.assertIn('WRITTEN_MC_INSTRUCTION',issues)
    def test_valid_written_output_passes(self):self.assertEqual([],bank.output_issues(marker('written'),result('written')))
    def test_blank_requires_visible_blank(self):
        issues=bank.output_issues(marker('blank'),result('blank',['a','b','c','d','e'],passage='No missing span here.'));self.assertIn('BLANK_MARKER_MISSING',issues)

if __name__=='__main__':unittest.main()
