#!/usr/bin/env python3
"""Private SmartStore queue. Read-only Commerce API; sending is explicitly opt-in."""
import argparse
import base64
import crypt
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import smtplib
import sqlite3
import ssl
import time
from email.message import EmailMessage
import urllib.error
import urllib.parse
import urllib.request

UTC = dt.timezone.utc
BASE = 'https://api.commerce.naver.com/external/v1'
EMAIL = re.compile(r'(?<![\w.+-])[A-Za-z0-9.!#$%&\x27*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+')


def iso(value):
    return value.astimezone(UTC).isoformat(timespec='milliseconds')


def instant(value):
    if not isinstance(value, str):
        raise ValueError('Timestamp required')
    parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        raise ValueError('Timezone required')
    return parsed.astimezone(UTC)


def recipient(memo):
    found = set(EMAIL.findall(memo or ''))
    return next(iter(found)) if len(found) == 1 else None


def classification(product, cutoff):
    if product.get('productOrderStatus') != 'PAYED' or product.get('claimStatus'):
        return 'INELIGIBLE'
    try:
        if instant(product['paymentDate']) < cutoff:
            return 'HISTORICAL_REVIEW'
    except (KeyError, ValueError, TypeError):
        return 'PAYMENT_REVIEW'
    return 'READY' if recipient(product.get('shippingMemo')) else 'EMAIL_REVIEW'


def database(directory):
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    db = sqlite3.connect(directory / 'orders.sqlite3')
    db.row_factory = sqlite3.Row
    db.executescript('''
      CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS orders(
        id TEXT PRIMARY KEY,status TEXT NOT NULL,email TEXT,buyer TEXT,
        delivery TEXT NOT NULL DEFAULT 'QUEUED',cafe TEXT NOT NULL DEFAULT 'REVIEW',
        message_id TEXT,sent_at TEXT,updated_at TEXT NOT NULL);
    ''')
    db.execute("UPDATE orders SET delivery='UNKNOWN' WHERE delivery='SENDING'")
    db.commit()
    return db


class Commerce:
    def __init__(self, config):
        self.config = config
        self.auth = None
        self.expires = 0

    def http(self, method, path, data=None, json_body=None, params=None, auth=True):
        url = BASE + path
        if params:
            url += '?' + urllib.parse.urlencode(params)
        headers = {}
        body = None
        if data is not None:
            body = urllib.parse.urlencode(data).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        if json_body is not None:
            body = json.dumps(json_body).encode()
            headers['Content-Type'] = 'application/json'
        if auth:
            headers['Authorization'] = 'Bearer ' + self.auth
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            raise RuntimeError('COMMERCE_HTTP_' + str(error.code)) from None

    def token(self):
        if time.time() < self.expires:
            return
        timestamp = str(int(time.time() * 1000))
        hashed = crypt.crypt(self.config['client_id'] + '_' + timestamp,
                             self.config['client_secret'])
        if not hashed or not hashed.startswith('$2'):
            raise RuntimeError('BCRYPT_UNAVAILABLE')
        data = self.http('POST', '/oauth2/token', data={
            'client_id': self.config['client_id'], 'timestamp': timestamp,
            'client_secret_sign': base64.b64encode(hashed.encode()).decode(),
            'grant_type': 'client_credentials', 'type': 'SELF'}, auth=False)
        self.auth = data['access_token']
        self.expires = time.time() + min(int(data.get('expires_in', 3600)), 3600) - 60

    def request(self, method, path, **kwargs):
        self.token()
        data = self.http(method, path, **kwargs)
        if data.get('code'):
            raise RuntimeError('COMMERCE_RESPONSE_ERROR')
        return data.get('data')

    def changes(self, start, end):
        params = {'lastChangedFrom': iso(start), 'lastChangedTo': iso(end), 'limitCount': 300}
        seen = set()
        for _ in range(100):
            data = self.request('GET', '/pay-order/seller/product-orders/last-changed-statuses',
                                params=params) or {}
            for row in data.get('lastChangeStatuses', []):
                yield row['productOrderId']
            more = data.get('more')
            if not more:
                return
            cursor = (more['moreFrom'], more['moreSequence'])
            if cursor in seen:
                raise RuntimeError('REPEATED_PAGE')
            seen.add(cursor)
            params.update(lastChangedFrom=cursor[0], moreSequence=cursor[1])
        raise RuntimeError('PAGE_LIMIT')

    def details(self, ids):
        if not ids:
            return []
        return self.request('POST', '/pay-order/seller/product-orders/query',
                            json_body={'productOrderIds': ids}) or []


def observe(db, details, config, cutoff, now):
    for detail in details:
        product = dict(detail.get('productOrder', {}))
        order = detail.get('order', {})
        if str(product.get('productId')) != str(config['product_id']):
            continue
        # paymentDate belongs to order in the detail response.
        product['paymentDate'] = order.get('paymentDate') or product.get('paymentDate')
        state = classification(product, cutoff)
        email = recipient(product.get('shippingMemo'))
        buyer = order.get('ordererName')
        db.execute('''INSERT INTO orders(id,status,email,buyer,updated_at) VALUES(?,?,?,?,?)
          ON CONFLICT(id) DO UPDATE SET status=excluded.status,email=excluded.email,
          buyer=excluded.buyer,updated_at=excluded.updated_at''',
                   (str(product['productOrderId']), state, email, buyer, iso(now)))
    db.commit()


def poll(db, api, config, now):
    cutoff = instant(config['activation_from'])
    previous = db.execute("SELECT value FROM meta WHERE key='cursor'").fetchone()
    start = instant(previous[0]) - dt.timedelta(minutes=5) if previous else now-dt.timedelta(hours=23)
    if now-start > dt.timedelta(days=14):
        raise RuntimeError('OUTAGE_REQUIRES_REVIEW')
    while start < now:
        end = min(start + dt.timedelta(hours=23), now)
        ids = list(dict.fromkeys(api.changes(start, end)))
        for offset in range(0, len(ids), 100):
            observe(db, api.details(ids[offset:offset+100]), config, cutoff, now)
        db.execute("INSERT OR REPLACE INTO meta VALUES('cursor',?)", (iso(end),))
        db.commit()
        start = end


def deliver(db, api, config):
    if not config.get('live_send'):
        return
    asset = Path(config['pdf_path'])
    content = asset.read_bytes()
    if asset.is_symlink() or not content.startswith(b'%PDF-'):
        raise RuntimeError('PDF_INVALID')
    if hashlib.sha256(content).hexdigest() != config['pdf_sha256']:
        raise RuntimeError('PDF_CHANGED')
    smtp_config = json.loads(Path(config['smtp_config']).read_text())
    for queued in db.execute("SELECT * FROM orders WHERE status='READY' AND delivery='QUEUED'").fetchall():
        # Recheck current payment/claim immediately before sending.
        fresh = api.details([queued['id']])
        if len(fresh) != 1:
            raise RuntimeError('ORDER_RECHECK_FAILED')
        observe(db, fresh, config, instant(config['activation_from']), dt.datetime.now(UTC))
        row = db.execute('SELECT * FROM orders WHERE id=?', (queued['id'],)).fetchone()
        if row['status'] != 'READY':
            continue
        message = EmailMessage()
        message['From'] = smtp_config['username']
        message['To'] = row['email']
        message['Subject'] = config['subject']
        mid = '<ebook-' + hashlib.sha256(row['id'].encode()).hexdigest() + '@' + smtp_config['username'].split('@')[1] + '>'
        message['Message-ID'] = mid
        message.set_content(Path(config['body_path']).read_text())
        message.add_attachment(content, maintype='application', subtype='pdf',
                               filename=config.get('attachment_name', '새출발 2억2천 탕감.pdf'))
        # Authenticate before changing delivery state. A failed connection can safely retry.
        with smtplib.SMTP_SSL(smtp_config['host'], int(smtp_config.get('port', 465)),
                              timeout=30, context=ssl.create_default_context()) as smtp:
            smtp.login(smtp_config['username'], smtp_config['password'])
            db.execute("UPDATE orders SET delivery='SENDING',message_id=? WHERE id=? AND delivery='QUEUED'", (mid, row['id']))
            db.commit()
            try:
                refused = smtp.send_message(message)
                if refused:
                    raise RuntimeError('RECIPIENT_REFUSED')
            except Exception:
                # SMTP acceptance can be ambiguous; never resend automatically.
                db.execute("UPDATE orders SET delivery='UNKNOWN' WHERE id=?", (row['id'],))
                db.commit()
                raise RuntimeError('SMTP_OUTCOME_REVIEW') from None
            db.execute("UPDATE orders SET delivery='SMTP_ACCEPTED',sent_at=? WHERE id=?",
                       (iso(dt.datetime.now(UTC)), row['id']))
            db.commit()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', default='/etc/f2r-ebook/commerce.json')
    parser.add_argument('--state-dir', default='/var/lib/f2r-ebook')
    args = parser.parse_args()
    os.umask(0o077)
    directory = Path(args.state_dir)
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (directory/'worker.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        config = json.loads(Path(args.config).read_text())
        db = database(directory)
        api = Commerce(config)
        poll(db, api, config, dt.datetime.now(UTC))
        deliver(db, api, config)
        print(json.dumps({'counts': [dict(r) for r in db.execute(
            'SELECT status,delivery,cafe,COUNT(*) AS count FROM orders GROUP BY status,delivery,cafe')],
            'live_send': bool(config.get('live_send')), 'cafe_auto_upgrade': False}))
        db.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # No exception messages/tracebacks: HTTP responses and SMTP errors can contain PII.
        print(json.dumps({'error_type': type(exc).__name__, 'ok': False}))
        raise SystemExit(1)
