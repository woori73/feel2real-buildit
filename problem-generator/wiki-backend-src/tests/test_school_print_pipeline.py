import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'buildit-wiki-print-pipeline.db')
os.environ['BUILDIT_WIKI_WORKSPACE'] = tempfile.gettempdir()

import main  # noqa: E402


class SchoolPrintPipelineTest(unittest.TestCase):
    def test_two_stage_contract_sends_each_page_text_and_image_and_preserves_canonical_linkage(self):
        html = '''<article><section><h2>A</h2><table><thead><tr><th>국가</th><th>국적</th><th>국가</th><th>국적</th></tr></thead></table></section><section><h2>B</h2><div class="definition-box">영영풀이</div></section><section><h2>C</h2><u>밑줄</u><div class="answer-line"></div></section></article>'''
        response = {'html': html, 'canonicalJson': {'sections': [], 'blocks': []}}
        ai_result = main.ai_router.ProviderResult(json.dumps(response), 'openrouter', 'qwen/qwen3.7-flash', 'server')
        pages = [
            main.SchoolPrintPage(page=1, name='word-plus-1.jpg', ocr_text='A. 국가 / 국적 4열 표', image_data_url='data:image/jpeg;base64,AAA'),
            main.SchoolPrintPage(page=2, name='word-plus-2.jpg', ocr_text='B. 영영풀이\nC. 밑줄과 답안선', image_data_url='data:image/jpeg;base64,BBB'),
        ]

        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result) as generate:
            result = main.reconstruct_school_print(main.SchoolPrintReconstructRequest(
                model='qwen/qwen3.7-flash', metadata={'school': '신사중', 'title': 'Word Plus'}, pages=pages,
            ))

        model, system, content = generate.call_args.args[:3]
        self.assertEqual('qwen/qwen3.7-flash', model)
        self.assertIn('source of truth', system)
        self.assertIn('4열 표', system)
        self.assertIn('definition-box', system)
        self.assertIn('answer-line', system)
        self.assertEqual(2, sum(item['type'] == 'image_url' for item in content))
        self.assertIn('A. 국가 / 국적 4열 표', content[1]['text'])
        self.assertIn('B. 영영풀이', content[3]['text'])
        self.assertIn('<table>', result['html'])
        canonical = result['canonicalJson']
        self.assertEqual('structured', canonical['restorationStatus'])
        self.assertEqual(2, canonical['restorationVersion'])
        self.assertEqual(2, canonical['sourceLinkage']['pageCount'])
        self.assertEqual('B. 영영풀이\nC. 밑줄과 답안선', canonical['extractedText'][1]['text'])
        self.assertEqual('word-plus-1.jpg', canonical['pages'][0]['sourceName'])
        self.assertEqual(html, canonical['editedHtml'])

    def test_empty_qwen_html_is_a_second_stage_failure(self):
        ai_result = main.ai_router.ProviderResult('{"html":"","canonicalJson":{}}', 'openrouter', 'qwen/qwen3.7-flash', 'server')
        payload = main.SchoolPrintReconstructRequest(pages=[
            main.SchoolPrintPage(page=1, name='page.jpg', ocr_text='1차 추출 원문', image_data_url='data:image/jpeg;base64,AAA')
        ])
        with patch.object(main.ai_router, 'generate_multimodal', return_value=ai_result):
            with self.assertRaises(main.HTTPException) as raised:
                main.reconstruct_school_print(payload)
        self.assertEqual(502, raised.exception.status_code)


if __name__ == '__main__':
    unittest.main()
