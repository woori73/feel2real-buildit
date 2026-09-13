import hashlib
import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH'] = str(Path(tempfile.gettempdir()) / 'wrong-mock.db')
import main
import wrong_mock
import workspace as wiki_workspace


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _photo(name='q1.jpg', data=b'fake-image-bytes-1', title='2021년 고3 3월 서울특별시교육청 학력평가 31번 빈칸'):
    return (name, 'image/jpeg', data, title)


class WrongMockTest(unittest.TestCase):
    CTX = dict(school='BuildIt', grade='고3', year=2026, semester='1학기', exam='전체')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db_path = str(Path(self.tmp.name) / 'owner.db')
        main.DB_PATH = self.db_path
        self.workspace_token = wiki_workspace.set_current_workspace(
            wiki_workspace.WikiWorkspace('owner-workspace', self.db_path, False))
        self.identity_token = wiki_workspace.set_current_identity(
            wiki_workspace.WikiIdentity('1', 'teacher', 'TUTOR'))
        main.init_db()
        main.migrate_schema()
        wrong_mock.configure(main.get_db)

    def tearDown(self):
        wiki_workspace.reset_current_identity(self.identity_token)
        wiki_workspace.reset_current_workspace(self.workspace_token)
        self.tmp.cleanup()

    def make_round(self, **overrides):
        values = {**self.CTX, 'round': 24}
        values.update(overrides)
        return wrong_mock.create_round(wrong_mock.WrongMockRoundRequest(**values))

    def test_create_round_defaults_title_and_collecting(self):
        saved = self.make_round()
        self.assertEqual(24, saved['round'])
        self.assertEqual('오답 24회', saved['title'])
        self.assertEqual('COLLECTING', saved['status'])
        self.assertEqual('사진 등록 중', saved['status_label'])
        self.assertEqual(20, saved['question_count'])
        self.assertFalse(saved['edit_locked'])
        self.assertEqual(0, saved['photo_count'])
        self.assertIn('questionCount', json_keys(saved['spec']))

    def test_create_round_auto_round_suggests_next(self):
        self.make_round(round=24)
        nxt = wrong_mock.create_round(wrong_mock.WrongMockRoundRequest(**self.CTX))
        self.assertEqual(25, nxt['round'])
        self.assertEqual('오답 25회', nxt['title'])

    def test_create_round_duplicate_is_409(self):
        self.make_round(round=24)
        with self.assertRaises(HTTPException) as dup:
            self.make_round(round=24)
        self.assertEqual(409, dup.exception.status_code)

    def test_photo_title_is_preserved_verbatim(self):
        saved = self.make_round()
        title = '2021년 고3 3월 서울특별시교육청 학력평가 31번 빈칸'
        out = wrong_mock.add_photos(saved['id'], [_photo(title=title)])
        self.assertEqual(1, len(out['photos']))
        self.assertEqual(title, out['photos'][0]['title'])
        self.assertEqual('q1.jpg', out['photos'][0]['original_name'])
        self.assertTrue(out['photos'][0]['original_api_path'].endswith('/original'))

    def test_photo_count_is_not_constrained_to_20(self):
        saved = self.make_round()
        files = [_photo(name=f'q{i}.jpg', data=f'bytes-{i}'.encode(), title=f'제목 {i}')
                 for i in range(25)]
        out = wrong_mock.add_photos(saved['id'], files)
        self.assertEqual(25, out['photo_count'])

    def test_same_photo_in_same_round_is_blocked(self):
        saved = self.make_round()
        wrong_mock.add_photos(saved['id'], [_photo()])
        with self.assertRaises(HTTPException) as dup:
            wrong_mock.add_photos(saved['id'], [_photo(name='other-name.jpg')])
        self.assertEqual(409, dup.exception.status_code)
        self.assertIn('이미 등록된 사진입니다', dup.exception.detail)

    def test_same_photo_in_other_round_warns_then_force(self):
        first = self.make_round(round=23)
        wrong_mock.add_photos(first['id'], [_photo()])
        second = self.make_round(round=24)
        with self.assertRaises(HTTPException) as warned:
            wrong_mock.add_photos(second['id'], [_photo(name='copy.jpg')])
        self.assertEqual(409, warned.exception.status_code)
        self.assertIn('오답 23회', warned.exception.detail)
        forced = wrong_mock.add_photos(second['id'], [_photo(name='copy.jpg')], force=True)
        self.assertEqual(1, forced['photo_count'])

    def test_complete_photos_moves_to_ready_and_locks(self):
        saved = self.make_round()
        wrong_mock.add_photos(saved['id'], [_photo()])
        out = wrong_mock.complete_photo_collection(saved['id'])
        self.assertEqual('READY', out['status'])
        self.assertEqual('제작대기', out['status_label'])
        self.assertTrue(out['edit_locked'])
        self.assertEqual(1, out['photo_count'])

    def test_complete_photos_twice_is_409(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        with self.assertRaises(HTTPException) as denied:
            wrong_mock.complete_photo_collection(saved['id'])
        self.assertEqual(409, denied.exception.status_code)

    def test_locked_round_rejects_photo_add(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        with self.assertRaises(HTTPException) as denied:
            wrong_mock.add_photos(saved['id'], [_photo(data=b'new')])
        self.assertEqual(409, denied.exception.status_code)

    def test_unlock_allows_photo_add_then_relock(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        unlocked = wrong_mock.set_edit_lock(saved['id'], False)
        self.assertFalse(unlocked['edit_locked'])
        out = wrong_mock.add_photos(saved['id'], [_photo(data=b'late-photo')])
        self.assertEqual('READY', out['status'])
        self.assertEqual(1, out['photo_count'])
        relocked = wrong_mock.set_edit_lock(saved['id'], True)
        self.assertTrue(relocked['edit_locked'])

    def test_artifacts_in_collecting_is_rejected(self):
        saved = self.make_round()
        with self.assertRaises(HTTPException) as denied:
            wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
                paperHtml='<h1>문제지</h1>', explHtml='<h1>해설</h1>'))
        self.assertEqual(409, denied.exception.status_code)

    def test_partial_then_full_artifacts_completes(self):
        saved = self.make_round()
        ready = wrong_mock.complete_photo_collection(saved['id'])
        self.assertTrue(ready['edit_locked'])
        wrong_mock.set_edit_lock(saved['id'], False)
        partial = wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            paperHtml='<h1>문제지</h1><p>Q1</p>', paperName='오답_24회_20문항_문제지.html'))
        self.assertEqual('READY', partial['status'])
        self.assertTrue(partial['has_paper'])
        self.assertFalse(partial['has_explanation'])
        done = wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            explHtml='<h1>해설</h1><p>1. ③</p>'))
        self.assertEqual('COMPLETED', done['status'])
        self.assertEqual('제작완료', done['status_label'])
        self.assertTrue(done['edit_locked'])
        self.assertIn('오답_24회_20문항_문제지.html', done['paper_name'])
        self.assertIn('간략해설', done['expl_name'])

    def test_completed_locks_artifact_replace_until_unlocked(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        wrong_mock.set_edit_lock(saved['id'], False)
        wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            paperHtml='<h1>문제지</h1>', explHtml='<h1>해설</h1>'))
        with self.assertRaises(HTTPException) as denied:
            wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
                paperHtml='<h1>문제지 v2</h1>'))
        self.assertEqual(409, denied.exception.status_code)
        wrong_mock.set_edit_lock(saved['id'], False)
        out = wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            paperHtml='<h1>문제지 v2</h1>'))
        self.assertEqual('COMPLETED', out['status'])
        body = wrong_mock.view_artifact(saved['id'], 'paper').body.decode()
        self.assertIn('문제지 v2', body)

    def test_view_artifact_is_no_store_and_canonical(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        wrong_mock.set_edit_lock(saved['id'], False)
        wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            paperHtml='<h1>문제지</h1><script>alert(1)</script><p>Q1</p>',
            explHtml='<h1>해설</h1>'))
        response = wrong_mock.view_artifact(saved['id'], 'paper')
        self.assertEqual('no-store', response.headers.get('Cache-Control'))
        body = response.body.decode()
        self.assertIn('Q1', body)
        self.assertNotIn('<script', body)

    def test_download_filename_has_rfc5987_korean_name(self):
        saved = self.make_round()
        wrong_mock.complete_photo_collection(saved['id'])
        wrong_mock.set_edit_lock(saved['id'], False)
        wrong_mock.register_artifacts(saved['id'], wrong_mock.WrongMockArtifactsUpdate(
            paperHtml='<h1>문제지</h1>', explHtml='<h1>해설</h1>'))
        response = wrong_mock.download_artifact(saved['id'], 'paper')
        disposition = response.headers.get('Content-Disposition')
        self.assertIn('attachment', disposition)
        self.assertIn("filename*=UTF-8''", disposition)

    def test_photo_rename_reorder_delete(self):
        saved = self.make_round()
        out = wrong_mock.add_photos(saved['id'], [
            _photo(name='a.jpg', data=b'a', title='A'),
            _photo(name='b.jpg', data=b'b', title='B'),
        ])
        ids = [photo['id'] for photo in out['photos']]
        renamed = wrong_mock.rename_photo(ids[0], 'A 수정')
        self.assertEqual('A 수정', renamed['title'])
        reordered = wrong_mock.reorder_photos(saved['id'], [ids[1], ids[0]])
        self.assertEqual([ids[1], ids[0]], [photo['id'] for photo in reordered['photos']])
        with self.assertRaises(HTTPException) as bad:
            wrong_mock.reorder_photos(saved['id'], [ids[0]])
        self.assertEqual(400, bad.exception.status_code)
        removed = wrong_mock.delete_photo(ids[0])
        self.assertEqual(ids[0], removed['id'])
        detail = wrong_mock.get_round(saved['id'])
        self.assertEqual(1, detail['photo_count'])

    def test_photo_replace_keeps_id_and_order(self):
        saved = self.make_round()
        out = wrong_mock.add_photos(saved['id'], [
            _photo(name='a.jpg', data=b'a', title='A'),
            _photo(name='b.jpg', data=b'b', title='B'),
        ])
        ids = [photo['id'] for photo in out['photos']]
        replaced = wrong_mock.replace_photo(ids[0], 'a2.jpg', 'image/jpeg', b'a2-bytes', 'A 교체')
        self.assertEqual(ids[0], replaced['id'])
        self.assertEqual('A 교체', replaced['title'])
        detail = wrong_mock.get_round(saved['id'])
        self.assertEqual(ids, [photo['id'] for photo in detail['photos']])

    def test_delete_round_only_in_collecting(self):
        saved = self.make_round()
        out = wrong_mock.delete_round(saved['id'])
        self.assertEqual(saved['id'], out['id'])
        self.assertEqual([], wrong_mock.list_rounds(**self.CTX))
        with self.assertRaises(HTTPException) as missing:
            wrong_mock.get_round(saved['id'])
        self.assertEqual(404, missing.exception.status_code)
        ready = self.make_round(round=25)
        wrong_mock.complete_photo_collection(ready['id'])
        with self.assertRaises(HTTPException) as denied:
            wrong_mock.delete_round(ready['id'])
        self.assertEqual(409, denied.exception.status_code)

    def test_ready_list_exposes_photos_for_future_agent(self):
        saved = self.make_round()
        wrong_mock.add_photos(saved['id'], [_photo()])
        wrong_mock.complete_photo_collection(saved['id'])
        ready = wrong_mock.list_ready_rounds(**self.CTX)
        self.assertEqual(1, len(ready))
        self.assertEqual(1, len(ready[0]['photos']))
        self.assertIn('questionCount', json_keys(ready[0]['spec']))
        collecting = self.make_round(round=25)
        self.assertEqual(1, len(wrong_mock.list_ready_rounds(**self.CTX)))
        self.assertEqual(2, len(wrong_mock.list_rounds(**self.CTX)))
        self.assertEqual(collecting['id'], wrong_mock.list_rounds(**self.CTX)[0]['id'])

    def test_photo_original_view_inline_and_missing(self):
        saved = self.make_round()
        out = wrong_mock.add_photos(saved['id'], [_photo()])
        response = wrong_mock.view_photo_original(out['photos'][0]['id'])
        self.assertIn('inline', response.headers.get('Content-Disposition'))
        self.assertEqual('no-store', response.headers.get('Cache-Control'))
        with self.assertRaises(HTTPException) as missing:
            wrong_mock.view_photo_original(999999)
        self.assertEqual(404, missing.exception.status_code)


def json_keys(spec):
    return ''.join(sorted(spec.keys()))


if __name__ == '__main__':
    unittest.main()
