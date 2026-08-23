import json
import os
import sys
import tempfile
import unittest
import io
import zipfile
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False); tmp.close()
os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
import main


class CsatLibraryTest(unittest.TestCase):
    def test_number_to_standard_type(self):
        expected = {18:'purpose', 21:'implication', 24:'title', 29:'grammar', 30:'vocabulary', 31:'blank', 34:'blank', 36:'order', 39:'insertion', 40:'summary', 42:'long_complex', 45:'long_sequence_reference_content'}
        for number, type_id in expected.items():
            with self.subTest(number=number): self.assertEqual(type_id, main.csat_type_for_number(number)['id'])

    def test_prompt_structure_mismatch_is_flagged(self):
        self.assertFalse(main.classify_csat_question(29, '다음 글의 밑줄 친 부분 중 어법상 틀린 것은?', ['a'] * 5)['type_mismatch'])
        self.assertTrue(main.classify_csat_question(29, '다음 글의 제목으로 적절한 것은?', ['a'] * 5)['type_mismatch'])

    def test_hwpx_adapter_extracts_section_text(self):
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as archive:
            archive.writestr('Contents/section0.xml', '<hp:section xmlns:hp="urn:test"><hp:t>21. 의미하는 것은?</hp:t><hp:t>Passage</hp:t></hp:section>')
        self.assertIn('21. 의미하는 것은?', main._extract_hwpx_text(payload.getvalue()))

    def test_question_persists_and_reads_back(self):
        with main.get_db() as conn:
            source = conn.execute("INSERT INTO csat_question_sources(exam_kind,source_filename,exam_metadata_json,parser,raw_text,created_at) VALUES('half','sample.json','{}','json','', 'now')")
            conn.execute("""INSERT INTO csat_questions(source_id,exam_kind,question_no,csat_type_id,csat_type_name,passage,prompt,choices_json,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)""", (source.lastrowid,'half',24,'title','제목','Passage','제목으로 가장 적절한 것은?',json.dumps(['A','B','C','D','E']),'now'))
        rows = main.list_csat_questions(exam_kind='half')
        self.assertEqual(24, rows[0]['question_no']); self.assertEqual(5, len(rows[0]['choices']))

    def test_print_html_is_editable_two_column_buildit_document(self):
        html = main.render_csat_html([{'question_no':21,'prompt':'의미하는 것은?','passage':'Text','choices':['A','B'],'type_mismatch':False}], '하프 모의고사')
        self.assertIn('column-count:2', html); self.assertIn('contenteditable="true"', html); self.assertIn('BuildIt 자체 제작 시험지', html)


if __name__ == '__main__': unittest.main()
