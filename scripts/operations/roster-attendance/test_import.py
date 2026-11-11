import importlib.util
import os
import unittest

spec = importlib.util.spec_from_file_location('imp', os.path.join(os.path.dirname(__file__), 'import.py'))
imp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(imp)


class FakeClient:
    def __init__(self, years=(2026,), names=None):
        self.years, self.calls = years, []
        self.names = names if names is not None else [
            {'name': 'A', 'suggestedAction': 'CREATE'}, {'name': 'B', 'suggestedAction': 'MATCH', 'suggestedStudentId': 7}]

    def call(self, method, path, body=None):
        self.calls.append((method, path.split('?')[0], body))
        if path.startswith('/api/admin/easy/businesses'):
            return [{'businessId': 2, 'code': 'PUBLISHING'}, {'businessId': 1, 'code': 'ACADEMY'}]
        if path.startswith('/api/admin/student-roster/entries'):
            return [{'year': y} for y in self.years]
        if 'roster-import/preview' in path:
            return {'year': 2026, 'previewHash': 'h', 'lessonDays': 3, 'futureLessonDays': 1, 'names': self.names}
        if method == 'GET' and path.startswith('/api/admin/easy/attendance/classes'):
            return []
        if method == 'POST' and path.startswith('/api/admin/easy/attendance/classes'):
            return {'classId': 9}
        if 'roster-import/apply' in path:
            return {'eventsCreated': 3}
        if 'monthly' in path:
            return {'students': [{'counts': {'CHECK_IN': 1}}]}
        raise AssertionError(path)


class ImportTest(unittest.TestCase):
    def test_given_unique_and_missing_names_when_auto_mappings_then_match_and_create(self):
        # given / when
        m = imp.auto_mappings([{'name': 'A', 'suggestedAction': 'CREATE'},
                               {'name': 'B', 'suggestedAction': 'MATCH', 'suggestedStudentId': 7}])
        # then
        self.assertEqual(m, [{'name': 'A', 'action': 'CREATE', 'studentId': None},
                             {'name': 'B', 'action': 'MATCH', 'studentId': 7}])

    def test_given_ambiguous_name_when_auto_mappings_then_stop(self):
        with self.assertRaises(imp.Stop):
            imp.auto_mappings([{'name': 'A', 'suggestedAction': 'CHOOSE'}])

    def test_given_preview_mode_when_run_then_no_write(self):
        # given
        c = FakeClient()
        # when
        imp.run(c, 2026, '2026 진행표 전체', False, '')
        # then
        self.assertFalse([x for x in c.calls if x[0] == 'POST'])

    def test_given_wrong_confirm_when_apply_then_stop_before_write(self):
        c = FakeClient()
        with self.assertRaises(imp.Stop):
            imp.run(c, 2026, '2026 진행표 전체', True, 'IMPORT-ROSTER-2025')
        self.assertFalse([x for x in c.calls if x[0] == 'POST'])

    def test_given_missing_year_when_run_then_stop(self):
        with self.assertRaises(imp.Stop):
            imp.run(FakeClient(years=(2025,)), 2026, 'x', False, '')

    def test_given_confirm_when_apply_then_class_created_and_academy_business_used(self):
        # given
        c = FakeClient()
        # when
        imp.run(c, 2026, '2026 진행표 전체', True, 'IMPORT-ROSTER-2026')
        # then
        apply = [x for x in c.calls if x[1].endswith('roster-import/apply')][0][2]
        self.assertEqual(apply['businessId'], 1)
        self.assertEqual(apply['classId'], 9)
        self.assertEqual(len(apply['mappings']), 2)

    def test_given_preview_when_summarize_then_counts_only(self):
        s = imp.summarize_preview({'year': 2026, 'lessonDays': 3, 'futureLessonDays': 1,
                                   'names': [{'name': '실명', 'suggestedAction': 'CREATE'}]})
        self.assertNotIn('실명', str(s))
        self.assertEqual(s['suggested'], {'CREATE': 1})


if __name__ == '__main__':
    unittest.main()
