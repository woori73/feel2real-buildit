import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False)
tmp.close()
os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
import main


class PassageWorkspaceTest(unittest.TestCase):
    def test_memory_returns_sentence_pairs(self):
        expected = {'title': 'Play', 'sentences': [{'english': 'Play matters.', 'korean': '놀이는 중요하다.'}]}
        with patch.object(main.pl, 'call_gemini_structured', return_value=json.dumps(expected)) as call:
            self.assertEqual(expected, main.create_passage_memory(main.PassageRequest(api_key='key', source_text='Play matters.')))
            self.assertEqual(main.PASSAGE_MEMORY_SCHEMA, call.call_args.args[3])

    def test_analysis_requires_exactly_ten_tf_questions(self):
        result = {'original': 'Text.', 'translation': '글.', 'title': 'Title', 'topic': 'Topic', 'mainIdea': 'Idea', 'summary': 'Summary',
                  'sentences': [], 'flow': {'introduction': 'I', 'development': 'D', 'conclusion': 'C'},
                  'tfQuestions': [{'language': 'KO' if i < 5 else 'EN', 'statement': str(i), 'answer': 'T' if i % 2 else 'F'} for i in range(10)]}
        with patch.object(main.pl, 'call_gemini_structured', return_value=json.dumps(result)):
            self.assertEqual(10, len(main.create_passage_analysis(main.PassageRequest(api_key='key', source_text='Text.'))['tfQuestions']))

    def test_passage_rejects_empty_source(self):
        with self.assertRaises(main.HTTPException) as raised:
            main.create_passage_memory(main.PassageRequest(api_key='key', source_text=' '))
        self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
