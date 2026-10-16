import tempfile
import unittest
from pathlib import Path
from hwpx import HwpxDocument
from hwpx_editor import _count_merged_cells, _find_merge_origin, apply_edits

class WorkerRegressionTest(unittest.TestCase):
    def test_unmerged_cells_are_not_counted_as_merged(self):
        d = HwpxDocument.new()
        d.add_table(2, 2)
        self.assertEqual(_count_merged_cells(d), 0)

    def test_adjacent_equal_spans_have_different_origins(self):
        d = HwpxDocument.new()
        t = d.add_table(2, 4)
        t.merge_cells(0, 0, 0, 1)
        t.merge_cells(0, 2, 0, 3)
        self.assertEqual(_find_merge_origin(t, 0, 3), (0, 2))
        self.assertEqual(_count_merged_cells(d), 2)

    def test_edit_save_reopen_preserves_korean_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, target = Path(tmp)/'source.hwpx', Path(tmp)/'out.hwpx'
            d = HwpxDocument.new()
            d.add_paragraph('한글 본문')
            t = d.add_table(2, 4)
            t.merge_cells(0, 0, 0, 1)
            t.merge_cells(0, 2, 0, 3)
            t.cell(0, 0).text = '왼쪽'
            t.cell(0, 2).text = '오른쪽'
            d.save_to_path(str(source))
            result = apply_edits(source, [{'table':0,'row':0,'col':3,'text':'승인 수정'}], target)
            self.assertTrue(result.ok, result.errors)
            reopened = HwpxDocument.open(str(target))
            table = list(reopened.tables)[0]
            self.assertEqual(table.cell(0, 0).text, '왼쪽')
            self.assertEqual(table.cell(0, 2).text, '승인 수정')
            self.assertEqual(_count_merged_cells(reopened), 2)

if __name__ == '__main__':
    unittest.main()
