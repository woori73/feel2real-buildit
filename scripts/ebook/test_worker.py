import datetime as dt
import tempfile
from pathlib import Path
import unittest
from unittest.mock import Mock, patch
import worker as w

NOW = dt.datetime(2026, 10, 31, 0, 0, tzinfo=w.UTC)


def detail(status='PAYED', memo='배송: buyer@example.invalid', **extra):
    return {'order': {'paymentDate': w.iso(NOW), 'ordererName': '합성 구매자'},
            'productOrder': {'productOrderId': 'test-order', 'productId': '12879870964',
                             'productOrderStatus': status, 'shippingMemo': memo, **extra}}


class WorkerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = w.database(Path(self.temp.name))
        self.config = {'product_id': '12879870964', 'activation_from': w.iso(NOW)}

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def observe(self, value):
        w.observe(self.db, [value], self.config, NOW, NOW)
        return self.db.execute('SELECT * FROM orders').fetchone()

    def test_single_memo_address_only(self):
        self.assertEqual(w.recipient('메일 buyer@example.invalid'), 'buyer@example.invalid')
        self.assertIsNone(w.recipient('a@example.invalid b@example.invalid'))
        self.assertIsNone(w.recipient('주소 없음'))

    def test_paid_and_historical_and_claim(self):
        self.assertEqual(self.observe(detail())['status'], 'READY')
        old = detail()
        old['order']['paymentDate'] = w.iso(NOW-dt.timedelta(seconds=1))
        self.assertEqual(self.observe(old)['status'], 'HISTORICAL_REVIEW')
        self.assertEqual(self.observe(detail(claimStatus='CANCEL_REQUEST'))['status'], 'INELIGIBLE')
        self.assertEqual(self.observe(detail('PURCHASE_DECIDED'))['status'], 'INELIGIBLE')

    def test_no_email_no_payment_and_wrong_product_fail_closed(self):
        self.assertEqual(self.observe(detail(memo='없음'))['status'], 'EMAIL_REVIEW')
        missing = detail()
        missing['order'].pop('paymentDate')
        self.assertEqual(self.observe(missing)['status'], 'PAYMENT_REVIEW')
        wrong = detail(productId='other')
        w.observe(self.db, [wrong], self.config, NOW, NOW)
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM orders').fetchone()[0], 1)

    def test_overlap_preserves_sent_and_unknown(self):
        self.observe(detail())
        for state in ['SMTP_ACCEPTED', 'UNKNOWN']:
            self.db.execute('UPDATE orders SET delivery=?', (state,))
            self.db.commit()
            self.assertEqual(self.observe(detail())['delivery'], state)

    def test_crash_sending_becomes_manual_review(self):
        self.observe(detail())
        self.db.execute("UPDATE orders SET delivery='SENDING'")
        self.db.commit()
        another = w.database(Path(self.temp.name))
        self.assertEqual(another.execute('SELECT delivery FROM orders').fetchone()[0], 'UNKNOWN')
        another.close()

    def test_failed_detail_keeps_cursor(self):
        api = Mock()
        api.changes.return_value = iter(['test-order'])
        api.details.side_effect = RuntimeError('test')
        with self.assertRaises(RuntimeError):
            w.poll(self.db, api, self.config, NOW)
        self.assertIsNone(self.db.execute("SELECT value FROM meta WHERE key='cursor'").fetchone())

    def test_pagination_uses_documented_cursor(self):
        api = w.Commerce({'client_id': 'unused', 'client_secret': 'unused'})
        with patch.object(api, 'request', side_effect=[
            {'lastChangeStatuses': [{'productOrderId': '1'}], 'more': {'moreFrom': w.iso(NOW), 'moreSequence': '2'}},
            {'lastChangeStatuses': [{'productOrderId': '2'}]}]) as call:
            self.assertEqual(list(api.changes(NOW-dt.timedelta(hours=1), NOW)), ['1', '2'])
            self.assertEqual(call.call_args.kwargs['params']['moreSequence'], '2')

    def test_dry_run_never_reads_asset_or_connects_smtp(self):
        self.observe(detail())
        with patch.object(w.smtplib, 'SMTP_SSL') as smtp:
            w.deliver(self.db, Mock(), {**self.config, 'live_send': False})
            smtp.assert_not_called()


if __name__ == '__main__':
    unittest.main()
