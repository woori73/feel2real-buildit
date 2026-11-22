import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import dataset_export as de  # noqa: E402

COLS = ('id', 'test_id', 'q_kind', 'question_type', 'stem', 'source_text', 'options_json', 'answer_json', 'multi',
        'score', 'conditions_json', 'answer_format', 'accepted_answers_json', 'sample_answer', 'grading_rule', 'raw_json')


def make_db(questions):
    path = tempfile.NamedTemporaryFile(suffix='.db', delete=False).name
    conn = sqlite3.connect(path)
    conn.execute('CREATE TABLE generated_tests (id INTEGER, year INTEGER, semester TEXT, exam TEXT, school TEXT, '
                 'grade TEXT, twin_label TEXT, twin_pattern TEXT, title TEXT)')
    conn.execute('INSERT INTO generated_tests VALUES (1, 2026, "2", "기말", "압구정중", "2", "", "", "t")')
    conn.execute('CREATE TABLE generated_questions (%s)' % ', '.join(COLS))
    for i, (source, raw) in enumerate(questions, 1):
        conn.execute('INSERT INTO generated_questions VALUES (%s)' % ','.join('?' * len(COLS)),
                     (i, 1, 'mc', '어법', '어법상 틀린 것은?', source, '["a","b","c","d","e"]', '[2]', 0, 3, '[]',
                      '', '[]', '', '', json.dumps(raw, ensure_ascii=False)))
    conn.commit()
    conn.close()
    return path


OK = {'verify': {'status': '일치'}, 'teacher': {'action': '확인'}}


class DatasetExportTest(unittest.TestCase):
    def test_given_verified_and_teacher_checked_when_export_then_only_those_rows(self):
        # given
        db = make_db([('He is the boy who won.', OK),
                      ('He is the boy who won.', {'verify': {'status': '불일치'}, 'teacher': {'action': '확인'}}),
                      ('He is the boy who won.', {'verify': {'status': '일치'}}),
                      ('', OK),
                      ('Call 010-1234-5678 now.', OK)])
        # when
        rows, skipped = de.export(db)
        # then
        self.assertEqual([r['id'] for r in rows], ['wiki-q1'])
        self.assertEqual(skipped, {'no_verify_match': 1, 'no_teacher': 1, 'no_source': 1, 'pii': 1, 'no_date': 0})
        self.assertEqual(rows[0]['metadata']['exam_date'], '2026-12-10')
        self.assertEqual(rows[0]['output']['question']['answer'], [2])

    def test_exam_date_override(self):
        self.assertEqual(de.exam_date(2026, '1학기', '중간고사'), '2026-04-25')
        self.assertEqual(de.exam_date(2026, '2', '기말', {'압구정중|2026|2|기말': '2026-12-08'}, '압구정중'), '2026-12-08')


if __name__ == '__main__':
    unittest.main()
