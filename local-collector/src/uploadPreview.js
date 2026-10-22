// Explicit, local-only bridge from an encrypted download to the independent EASY preview API.
// The token here authenticates to EASY, never to a bank/card company/Hometax.
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createInterface } from 'node:readline/promises';
import { encryptionKey, mockOrigin } from './downloadSafety.js';

export async function uploadEncryptedPreview({ filename, format, keyBase64, baseUrl, accessToken,
  businessId, dataset, accountRef, from, to, userApprovedUpload = false }) {
  if (userApprovedUpload !== true) throw new Error('USER_UPLOAD_CONSENT_REQUIRED');
  const origin = mockOrigin(baseUrl);
  if (!['csv', 'xls', 'xlsx', 'pdf'].includes(format)) throw new Error('UNSUPPORTED_FORMAT');
  if (!/^[A-Za-z0-9_.-]+$/.test(accessToken || '')) throw new Error('EASY_ACCESS_TOKEN_REQUIRED');
  if ((await fs.stat(filename)).size > 10 * 1024 * 1024 + 29) throw new Error('FILE_SIZE_LIMIT');
  const envelope = await fs.readFile(filename);
  if (envelope.length < 30 || envelope[0] !== 1) throw new Error('INVALID_ENVELOPE');
  const cipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(keyBase64), envelope.subarray(1, 13));
  cipher.setAAD(Buffer.from(path.basename(filename)));
  cipher.setAuthTag(envelope.subarray(13, 29));
  const plaintext = Buffer.concat([cipher.update(envelope.subarray(29)), cipher.final()]);
  const body = new FormData();
  for (const [name, value] of Object.entries({ businessId, dataset, accountRef, from, to, userApprovedUpload: true })) {
    body.set(name, String(value));
  }
  body.set('file', new Blob([plaintext]), `download.${format}`);
  // No redirects: an untrusted response must not forward the EASY cookie to another host.
  const response = await fetch(`${origin}/api/admin/easy/financial-collector/previews`, {
    method: 'POST', headers: { Cookie: `ACCESS_TOKEN=${accessToken}` }, body, redirect: 'error',
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error(`EASY_PREVIEW_HTTP_${response.status}`);
  return response.json();
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [filename, format, businessId, dataset, accountRef, from, to] = process.argv.slice(2);
  try {
    if (!process.stdin.isTTY) throw new Error('INTERACTIVE_UPLOAD_APPROVAL_REQUIRED');
    const terminal = createInterface({ input: process.stdin, output: process.stdout });
    let approved;
    try {
      approved = (await terminal.question('복호화한 자료를 로컬 EASY 거래 초안으로 전송합니다. 승인하려면 y 입력: ')).trim() === 'y';
    } finally { terminal.close(); }
    const result = await uploadEncryptedPreview({ filename, format, businessId, dataset, accountRef, from, to,
      keyBase64: process.env.EASY_COLLECTOR_FILE_KEY_BASE64, baseUrl: process.env.EASY_BASE_URL,
      accessToken: process.env.EASY_PREVIEW_ACCESS_TOKEN, userApprovedUpload: approved });
    // Report IDs and counts only; never emit transaction rows or the application token.
    console.log(JSON.stringify({ collectionId: result.collectionId, status: result.report.status,
      fetched: result.report.fetched, valid: result.report.valid, failed: result.report.failed,
      ledgerSaved: result.ledgerSaved }));
  } catch {
    console.error('PREVIEW_UPLOAD_FAILED: 로컬 설정·승인·파일을 확인하세요.');
    process.exitCode = 1;
  }
}
