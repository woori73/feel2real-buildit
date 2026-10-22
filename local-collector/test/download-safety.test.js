import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { Readable } from 'node:stream';
import http from 'node:http';
import { mockOrigin, encryptionKey, saveEncryptedDownload } from '../src/downloadSafety.js';
import { uploadEncryptedPreview } from '../src/uploadPreview.js';

test('given real or credential-bearing URL, mock policy rejects before opening browser', () => {
  for (const url of ['https://www.cardsales.or.kr/', 'http://192.168.1.1:80',
    'http://127.0.0.1.evil.test:8000', 'http://user:password@localhost:1234', 'file:///tmp/file']) {
    assert.throws(() => mockOrigin(url));
  }
  assert.equal(mockOrigin('http://127.0.0.1:4173/'), 'http://127.0.0.1:4173');
  assert.throws(() => encryptionKey(''));
});

test('given traversal filename, encrypted download uses random private filename and authenticates contents', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'easy-financial-test-'));
  const plain = Buffer.from('SAMPLE_ONLY,date,amount\n2026-10-01,12000');
  const key = crypto.randomBytes(32);
  try {
    const result = await saveEncryptedDownload({
      suggestedFilename: () => '../../windows\\escape.csv',
      createReadStream: async () => Readable.from([plain]),
    }, directory, key);
    assert.match(result.filename, /^[a-f0-9-]{36}\.enc$/);
    const target = path.join(directory, result.filename);
    const envelope = await fs.readFile(target);
    assert.equal(envelope.includes(plain), false);
    assert.equal((await fs.stat(target)).mode & 0o777, 0o600);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, envelope.subarray(1, 13));
    decipher.setAAD(Buffer.from(result.filename));
    decipher.setAuthTag(envelope.subarray(13, 29));
    assert.deepEqual(Buffer.concat([decipher.update(envelope.subarray(29)), decipher.final()]), plain);
    assert.equal(result.size, plain.length);
    assert.equal(result.sha256, crypto.createHash('sha256').update(plain).digest('hex'));
    envelope[envelope.length - 1] ^= 1;
    const tampered = crypto.createDecipheriv('aes-256-gcm', key, envelope.subarray(1, 13));
    tampered.setAAD(Buffer.from(result.filename));
    tampered.setAuthTag(envelope.subarray(13, 29));
    tampered.update(envelope.subarray(29));
    assert.throws(() => tampered.final());
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
});

test('given empty, oversized or disallowed download, no archive is written', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'easy-financial-test-'));
  try {
    for (const [name, body] of [['sample.exe', Buffer.from('x')], ['sample.csv', Buffer.alloc(0)],
      ['sample.csv', Buffer.alloc(10 * 1024 * 1024 + 1)]]) {
      await assert.rejects(() => saveEncryptedDownload({
        suggestedFilename: () => name, createReadStream: async () => Readable.from([body]),
      }, directory, crypto.randomBytes(32)));
    }
    assert.deepEqual(await fs.readdir(directory), []);
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
});

test('given user-approved encrypted sample, bridge uploads plaintext only in memory to local preview contract', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'easy-financial-bridge-'));
  const key = crypto.randomBytes(32);
  const plain = Buffer.from('date,direction,amount\n2026-10-01,EXPENSE,12000');
  let requests = 0;
  let received;
  const server = http.createServer(async (req, res) => {
    requests++;
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    received = { url: req.url, cookie: req.headers.cookie, body: Buffer.concat(chunks).toString() };
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ collectionId: 'sample-id', report: { status: 'PARTIAL' }, ledgerSaved: 0 }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const saved = await saveEncryptedDownload({ suggestedFilename: () => 'sample.csv',
      createReadStream: async () => Readable.from([plain]) }, directory, key);
    const options = { filename: path.join(directory, saved.filename), format: 'csv',
      keyBase64: key.toString('base64'), baseUrl: `http://127.0.0.1:${server.address().port}`,
      accessToken: 'sample.easy.token', businessId: 1, dataset: 'BANK_ACCOUNT', accountRef: 'sample',
      from: '2026-10-01', to: '2026-10-31' };
    await assert.rejects(() => uploadEncryptedPreview(options), /USER_UPLOAD_CONSENT_REQUIRED/);
    assert.equal(requests, 0);
    const report = await uploadEncryptedPreview({ ...options, userApprovedUpload: true });
    assert.equal(report.ledgerSaved, 0);
    assert.equal(received.url, '/api/admin/easy/financial-collector/previews');
    assert.equal(received.cookie, 'ACCESS_TOKEN=sample.easy.token');
    assert.ok(received.body.includes(plain.toString()));
    assert.equal((await fs.readdir(directory)).length, 1);
    await assert.rejects(() => uploadEncryptedPreview({ ...options, userApprovedUpload: true,
      baseUrl: 'https://www.hometax.go.kr/' }), /MOCK_LOOPBACK_ORIGIN_REQUIRED/);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await fs.rm(directory, { recursive: true, force: true });
  }
});
