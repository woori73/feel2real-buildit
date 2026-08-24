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
        ai = main.ai_router.ProviderResult(json.dumps(expected), 'openrouter', 'qwen/qwen3.7-flash', 'server')
        with patch.object(main.ai_router, 'generate', return_value=ai) as call:
            result = main.create_passage_memory(main.PassageRequest(api_key='ignored', source_text='Play matters.'))
            self.assertEqual({**expected, 'provider': 'openrouter', 'model': 'qwen/qwen3.7-flash'}, result)
            self.assertEqual(main.PASSAGE_MEMORY_SCHEMA, call.call_args.kwargs['schema'])

    def test_analysis_requires_exactly_ten_tf_questions(self):
        result = {'title': 'Title', 'topic': 'Topic', 'mainIdea': 'Idea', 'summary': 'Summary',
                  'sentences': [{'slashed': 'Text / matters.', 'directTranslation': '글은 / 중요하다.', 'translation': '글은 중요하다.', 'grammarGuide': '주어와 동사'}],
                  'tfQuestions': [{'language': 'KO' if i < 5 else 'EN', 'statement': str(i), 'answer': 'T' if i % 2 else 'F'} for i in range(10)]}
        ai = main.ai_router.ProviderResult(json.dumps(result), 'openrouter', 'qwen/qwen3.7-flash', 'server')
        with patch.object(main.ai_router, 'generate', return_value=ai):
            response = main.create_passage_analysis(main.PassageRequest(api_key='ignored', source_text='Text.'))
            self.assertEqual(10, len(response['tfQuestions']))
            self.assertEqual(('openrouter', 'qwen/qwen3.7-flash'), (response['provider'], response['model']))

    def test_analysis_schema_does_not_duplicate_full_original(self):
        self.assertNotIn('original', main.PASSAGE_ANALYSIS_SCHEMA['properties'])
        fields = main.PASSAGE_ANALYSIS_SCHEMA['properties']['sentences']['items']['required']
        self.assertEqual(['slashed', 'directTranslation', 'translation', 'grammarGuide'], fields)

    def test_passage_rejects_empty_source(self):
        with self.assertRaises(main.HTTPException) as raised:
            main.create_passage_memory(main.PassageRequest(api_key='key', source_text=' '))
        self.assertEqual(400, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
