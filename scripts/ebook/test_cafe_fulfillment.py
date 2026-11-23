import datetime as dt
import unittest
from unittest.mock import Mock
import cafe_fulfillment as f
import worker as w
import test_worker as fixtures
from test_worker import detail


class FulfillmentTest(unittest.TestCase):
    setUp = fixtures.WorkerTest.setUp
    tearDown = fixtures.WorkerTest.tearDown
    observe = fixtures.WorkerTest.observe
    def test_exact_identity_and_fresh_observation_required(self):
        now = dt.datetime.now(w.UTC)
        receipt = {'searched_id': 'buyer123', 'result_count': 1,
                   'grade': '감사멤버', 'member_level': 150, 'cafe_id': 31312978,
                   'nickname': '합성 회원', 'naver_id_prefix': 'buye',
                   'member_tag': 'abcdefghijkl', 'observed_at': now.isoformat(),
                   'verified_by': 'UI_RUNTIME'}
        self.assertEqual(f.validate_receipt(receipt, 'buyer123', now), receipt)
        for override in [{'result_count': 2}, {'searched_id': 'buyer124'},
                         {'naver_id_prefix': 'oops'}, {'grade': '일반멤버'},
                         {'observed_at': (now-dt.timedelta(minutes=6)).isoformat()}]:
            with self.assertRaises(f.ReviewRequired):
                f.validate_receipt({**receipt, **override}, 'buyer123', now)

    def test_cancel_and_manual_delivery_never_reach_browser(self):
        self.observe(detail(memo='buyer123@naver.com'))
        api = Mock()
        api.details.return_value = [detail(claimStatus='CANCEL_REQUEST')]
        upgrade = Mock()
        f.complete_ready(self.db, api, self.config, w, upgrade=upgrade)
        upgrade.assert_not_called()
        self.observe(detail(memo='buyer123@naver.com'))
        self.db.execute("UPDATE orders SET delivery='MANUAL_SENT'")
        self.db.commit()
        f.complete_ready(self.db, api, self.config, w, upgrade=upgrade)
        upgrade.assert_not_called()

    def test_failed_roster_never_unlocks_email(self):
        self.observe(detail(memo='buyer123@naver.com'))
        api = Mock()
        api.details.return_value = [detail(memo='buyer123@naver.com')]
        receipt = {'searched_id': 'buyer123', 'result_count': 1,
                   'grade': '감사멤버', 'member_level': 150, 'cafe_id': 31312978,
                   'nickname': '합성 회원', 'naver_id_prefix': 'buye',
                   'member_tag': 'abcdefghijkl',
                   'observed_at': dt.datetime.now(w.UTC).isoformat(),
                   'verified_by': 'UI_RUNTIME'}
        result = f.complete_ready(self.db, api, self.config, w,
                                  upgrade=Mock(return_value=receipt),
                                  sync=Mock(side_effect=RuntimeError('collision')))
        self.assertEqual(result['review'], 1)
        self.assertEqual(self.db.execute('SELECT cafe FROM orders').fetchone()[0], 'REVIEW')
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM cafe_receipts').fetchone()[0], 0)

    def test_successful_registration_unlocks_delivery_once(self):
        self.observe(detail(memo='buyer123@naver.com'))
        api = Mock()
        api.details.return_value = [detail(memo='buyer123@naver.com')]
        receipt = {'searched_id': 'buyer123', 'result_count': 1,
                   'grade': '감사멤버', 'member_level': 150, 'cafe_id': 31312978,
                   'nickname': '합성 회원', 'naver_id_prefix': 'buye',
                   'member_tag': 'abcdefghijkl',
                   'observed_at': dt.datetime.now(w.UTC).isoformat(),
                   'verified_by': 'UI_RUNTIME'}
        upgrade = Mock(return_value=receipt)
        sync = Mock(return_value=800)
        result = f.complete_ready(self.db, api, self.config, w, upgrade=upgrade, sync=sync)
        self.assertEqual(result['completed'], 1)
        self.assertEqual(self.db.execute('SELECT cafe FROM orders').fetchone()[0], 'CONFIRMED')
        f.complete_ready(self.db, api, self.config, w, upgrade=upgrade, sync=sync)
        self.assertEqual(upgrade.call_count, 1)


if __name__ == '__main__':
    unittest.main()
