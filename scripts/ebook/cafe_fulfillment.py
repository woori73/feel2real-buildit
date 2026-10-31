"""Connect paid orders to verified Cafe upgrades and the existing roster.

Browser adapter output is checked before it can unlock email delivery. No
identity is inferred from a masked ID or a buyer's display name.
"""
import datetime as dt
import json
import re
import subprocess
import sys


class ReviewRequired(RuntimeError):
    pass


def naver_id(email):
    address = (email or '').strip().lower()
    match = re.fullmatch(r'([a-z0-9][a-z0-9_-]{4,19})@naver\.com', address)
    if not match:
        raise ReviewRequired('CAFE_ID_REQUIRED')
    return match[1]


def validate_receipt(receipt, identity, now):
    if receipt.get('searched_id') != identity or receipt.get('result_count') != 1:
        raise ReviewRequired('EXACT_UNIQUE_MEMBER_REQUIRED')
    if receipt.get('grade') != '감사멤버' or receipt.get('member_level') != 150:
        raise ReviewRequired('COMPLETED_THANKS_GRADE_REQUIRED')
    prefix = receipt.get('naver_id_prefix', '').lower()
    if not re.fullmatch(r'[a-z0-9_-]{1,4}', prefix) or not identity.startswith(prefix):
        raise ReviewRequired('MEMBER_PREFIX_MISMATCH')
    if not re.fullmatch(r'[a-z0-9]{12}', receipt.get('member_tag', '')):
        raise ReviewRequired('MEMBER_TAG_REQUIRED')
    if receipt.get('cafe_id') != 31312978 or not receipt.get('nickname'):
        raise ReviewRequired('CAFE_MEMBER_REQUIRED')
    when = dt.datetime.fromisoformat(receipt['observed_at'].replace('Z', '+00:00'))
    if when.tzinfo is None or abs((now-when).total_seconds()) > 300:
        raise ReviewRequired('FRESH_GRADE_CONFIRMATION_REQUIRED')
    return receipt


def browser_upgrade(identity, config):
    runner = config.get('cafe_runner', '/opt/f2r-cafe-runtime/order_member.py')
    command = ['runuser', '-u', 'ec2-user', '--', 'env',
               'PLAYWRIGHT_BROWSERS_PATH=/opt/f2r-cafe-runtime/browsers',
               '/opt/f2r-cafe-runtime/venv/bin/python', runner,
               '--lookup-id', identity, '--upgrade']
    result = subprocess.run(command, capture_output=True, text=True, timeout=150)
    if result.returncode:
        raise ReviewRequired('CAFE_BROWSER_REVIEW')
    receipt = json.loads(result.stdout)
    return receipt


def sync_roster(receipt):
    sys.path.insert(0, '/opt/f2r-cafe-runtime')
    import newstart_roster_sync
    return newstart_roster_sync.sync(receipt)


def complete_ready(db, api, config, worker, upgrade=browser_upgrade, sync=sync_roster):
    db.execute('''CREATE TABLE IF NOT EXISTS fulfillment_reviews(
      order_id TEXT PRIMARY KEY, code TEXT NOT NULL, checked_at TEXT NOT NULL)''')
    db.commit()
    counts = {'completed': 0, 'review': 0}
    retry_before = (dt.datetime.now(worker.UTC)-dt.timedelta(minutes=30)).isoformat()
    queued = db.execute("""SELECT o.* FROM orders o LEFT JOIN fulfillment_reviews r
      ON r.order_id=o.id WHERE o.status='READY' AND o.delivery='QUEUED' AND o.cafe='REVIEW'
      AND (r.order_id IS NULL OR r.checked_at<?) ORDER BY o.updated_at LIMIT 1""",
      (retry_before,)).fetchall()
    for queued_order in queued:
        oid = queued_order['id']
        try:
            identity = naver_id(queued_order['email'])
            fresh = api.details([oid])
            if len(fresh) != 1 or str(fresh[0].get('productOrder', {}).get('productOrderId')) != oid:
                raise ReviewRequired('CURRENT_ORDER_REQUIRED')
            now = dt.datetime.now(worker.UTC)
            worker.observe(db, fresh, config, worker.instant(config['activation_from']), now)
            current = db.execute('SELECT * FROM orders WHERE id=?', (oid,)).fetchone()
            if current['status'] != 'READY':
                continue
            if current['email'] != queued_order['email']:
                raise ReviewRequired('RECIPIENT_CHANGED')
            receipt = validate_receipt(upgrade(identity, config), identity, dt.datetime.now(worker.UTC))
            # Registration must succeed before the configured message claims it did.
            roster_id = sync(receipt)
            if not isinstance(roster_id, int) or roster_id <= 0:
                raise ReviewRequired('ROSTER_RECEIPT_REQUIRED')
            worker.confirm_cafe(db, oid, receipt['member_tag'], receipt['nickname'],
                                receipt['verified_by'], receipt['grade'])
            with db:
                db.execute('DELETE FROM fulfillment_reviews WHERE order_id=?', (oid,))
            counts['completed'] += 1
        except Exception as error:
            # Do not put raw browser, SQL or SMTP exception messages in logs.
            code = str(error) if isinstance(error, ReviewRequired) else type(error).__name__
            with db:
                db.execute('INSERT OR REPLACE INTO fulfillment_reviews VALUES(?,?,?)',
                           (oid, code, dt.datetime.now(worker.UTC).isoformat()))
            counts['review'] += 1
    return counts
