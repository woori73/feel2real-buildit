import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import private_reference_bank as bank  # noqa: E402


def marker(kind):
    return ('QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: '
            '{"questionWiki":true,"questionWikiId":31,"grade":2,'
            '"examYear":2026,"questionNo":31,"type":"' + kind + '"}')


class QuestionWikiTypeContractTest(unittest.TestCase):
    def test_written_forbids_multiple_choice_options(self):
        schema = bank.question_wiki_schema(marker('written'))
        self.assertEqual(['written'], schema['properties']['type']['enum'])
        self.assertEqual(0, schema['properties']['choices']['maxItems'])
        self.assertEqual(1, schema['properties']['instruction']['minLength'])
        self.assertEqual(1, schema['properties']['answer']['minLength'])
        self.assertEqual(1, schema['properties']['explanation']['minLength'])

    def test_multiple_choice_types_require_exactly_five_options(self):
        for kind in ('similar','grammar','vocabulary','blank','order','insertion','title-topic'):
            with self.subTest(kind=kind):
                choices = bank.question_wiki_schema(marker(kind))['properties']['choices']
                self.assertEqual(5, choices['minItems'])
                self.assertEqual(5, choices['maxItems'])

    def test_generic_request_has_no_question_wiki_schema(self):
        self.assertIsNone(bank.question_wiki_schema('ordinary generic AI request'))

    def test_written_mechanics_explicitly_require_constructed_response(self):
        mechanics = bank._TYPE_MECHANICS['written']
        self.assertIn('CONSTRUCTED RESPONSE ONLY', mechanics)
        self.assertIn('no multiple-choice options', mechanics)
        self.assertIn('answer/explanation', mechanics)


if __name__ == '__main__':
    unittest.main()
