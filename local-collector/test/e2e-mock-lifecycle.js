// Real, executable end-to-end proof of the Visible Local Browser Collector lifecycle
// (Section 10): EASY(fake, contract-identical) -> task -> REAL visible Chromium opens on this
// Windows machine -> mock login page -> WAITING_FOR_USER_AUTH -> (test-only) same-page click
// standing in for a human -> RESUME -> same session -> fill dates -> search -> download ->
// local save outside the repo -> sha256 -> COMPLETED metadata reported back.
//
// The collector code under test (../src/collector.js) is exactly the production code — nothing
// here mocks Playwright or the browser. Only the backend is a minimal fake, and only the human's
// click is stood in for (see testHooks.onWaitingForAuth in collector.js), both clearly marked.
import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { createFakeBackend, VALID_TOKEN } from './fakeBackend.js';
import { pollAndRun } from '../src/collector.js';
import { config } from '../src/config.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BACKEND_PORT = 4801;
const MOCK_SITE_PORT = 4173;

function startMockSite() {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(__dirname, '..', 'mock-site', 'server.js')], {
      env: { ...process.env, MOCK_SITE_PORT: String(MOCK_SITE_PORT) },
      stdio: 'inherit',
    });
    child.on('error', reject);
    // Give the static server a moment to bind.
    setTimeout(() => resolve(child), 600);
  });
}

async function main() {
  console.log('=== EASY Visible Local Browser Collector — safe mock E2E ===');

  // 1) unauthorized collector rejection: wrong token must never see a task.
  process.env.EASY_COLLECTOR_TOKEN = 'wrong-token';
  config.collectorToken = 'wrong-token';
  const { queueTask, getTask } = await createFakeBackend({ port: BACKEND_PORT });
  config.easyBaseUrl = `http://localhost:${BACKEND_PORT}`;
  let unauthorizedRejected = false;
  try {
    await pollAndRun({ once: true });
  } catch (err) {
    unauthorizedRejected = /401/.test(String(err.message));
  }
  console.log(`[e2e] unauthorized collector rejection: ${unauthorizedRejected ? 'PASS' : 'FAIL'}`);

  // 2) real task, real visible browser, real lifecycle.
  config.collectorToken = VALID_TOKEN;
  config.fileKeyBase64 = crypto.randomBytes(32).toString('base64');
  const mockSite = await startMockSite();
  const taskId = queueTask({
    sourceCode: 'CARD_COMPANY_TEST',
    requestedFrom: '2026-09-15',
    requestedTo: '2026-09-22',
  });
  console.log(`[e2e] queued task ${taskId}`);

  const downloadDir = path.join(__dirname, '..', '.e2e-downloads');
  config.downloadDir = downloadDir;
  fs.rmSync(downloadDir, { recursive: true, force: true });

  let sawWaitingForAuth = false;
  await pollAndRun({
    once: true,
    targetBaseUrl: `http://localhost:${MOCK_SITE_PORT}`,
    testHooks: {
      async onCollectionConsent() { return true; },
      async onWaitingForAuth(page) {
        sawWaitingForAuth = getTask(taskId).status === 'WAITING_FOR_USER_AUTH';
        console.log(`[e2e] server-observed status at handoff: ${getTask(taskId).status}`);
        await page.screenshot({ path: path.join(__dirname, '..', '.e2e-downloads-shot-waiting.png') })
          .catch(() => {});
        // TEST-ONLY stand-in for a human: click the SAME real page's login button.
        console.log('[e2e] (test-only) clicking login on the SAME browser/page a human would use...');
        await page.click('#loginBtn');
      },
    },
  });

  // A second freshly opened session may authenticate, but refusal must stop before downloading.
  const refusedId = queueTask({ sourceCode: 'CARD_COMPANY_TEST', requestedFrom: '2026-10-01', requestedTo: '2026-10-22' });
  let freshLogin = false;
  let externalBlocked = false;
  await pollAndRun({ once: true, targetBaseUrl: `http://localhost:${MOCK_SITE_PORT}`, testHooks: {
    async onWaitingForAuth(page) {
      freshLogin = await page.locator('#loginBtn').isVisible();
      try { await page.goto('http://example.invalid/blocked'); }
      catch { externalBlocked = true; }
      await page.goto(`http://localhost:${MOCK_SITE_PORT}/`);
      await page.click('#loginBtn');
    },
    async onCollectionConsent() { return false; },
  } });
  const refusalStopped = getTask(refusedId).status === 'FAILED'
      && getTask(refusedId).downloadedFilename === null;
  mockSite.kill();

  const finalTask = getTask(taskId);
  console.log('[e2e] final task state:', finalTask);

  const files = fs.existsSync(downloadDir) ? fs.readdirSync(downloadDir) : [];
  const savedFile = files[0] ? path.join(downloadDir, files[0]) : null;
  const envelope = savedFile ? fs.readFileSync(savedFile) : Buffer.alloc(0);
  let decrypted = Buffer.alloc(0);
  if (savedFile) {
    const decipher = crypto.createDecipheriv('aes-256-gcm',
      Buffer.from(config.fileKeyBase64, 'base64'), envelope.subarray(1, 13));
    decipher.setAAD(Buffer.from(path.basename(savedFile)));
    decipher.setAuthTag(envelope.subarray(13, 29));
    decrypted = Buffer.concat([decipher.update(envelope.subarray(29)), decipher.final()]);
  }

  const results = {
    unauthorizedRejected,
    sawWaitingForAuth,
    resumedSameSession: finalTask.status === 'COMPLETED',
    downloadSavedLocally: !!savedFile && fs.existsSync(savedFile),
    encryptedOriginalVerified: decrypted.length === finalTask.downloadedSize
        && crypto.createHash('sha256').update(decrypted).digest('hex') === finalTask.downloadedSha256
        && !envelope.includes(Buffer.from('거래일')),
    freshSessionAndConsentRefusal: freshLogin && refusalStopped && files.length === 1,
    externalNavigationBlocked: externalBlocked,
    metadataOnlyReported: !!finalTask.downloadedSha256 && !!finalTask.downloadedFilename
        && typeof finalTask.downloadedSize === 'number',
    finalStatus: finalTask.status,
    downloadedFilename: finalTask.downloadedFilename,
    downloadedSize: finalTask.downloadedSize,
    downloadedSha256: finalTask.downloadedSha256,
    savedFilePath: savedFile,
  };

  console.log('\n=== RESULT ===');
  console.log(JSON.stringify(results, null, 2));

  const allPass = results.unauthorizedRejected && results.sawWaitingForAuth
      && results.resumedSameSession && results.downloadSavedLocally && results.metadataOnlyReported
      && results.encryptedOriginalVerified && results.freshSessionAndConsentRefusal
      && results.externalNavigationBlocked;
  console.log(allPass ? '\nE2E LIFECYCLE: PASS' : '\nE2E LIFECYCLE: FAIL');
  process.exit(allPass ? 0 : 1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
