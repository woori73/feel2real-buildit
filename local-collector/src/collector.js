// EASY Visible Local Browser Collector — the generic engine (Section 6).
// Normal collection never calls an LLM. Every action below is a deterministic Playwright call
// driven by a declarative workflow (src/workflows/*). This file never reads/stores/reports a
// certificate, password, OTP, CVC, or full card number — the human enters those directly into
// the visible browser during WAITING_FOR_USER_AUTH, and this process never touches that page.
import { chromium } from 'playwright';
import { createInterface } from 'node:readline/promises';
import { config } from './config.js';
import { backendClient } from './backendClient.js';
import { resolveWorkflow } from './workflows/index.js';
import { encryptionKey, mockOrigin, saveEncryptedDownload } from './downloadSafety.js';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function runStep(page, step) {
  if (step.action === 'fill') {
    await page.fill(step.selector, step.value);
  } else if (step.action === 'click') {
    await page.click(step.selector);
  } else {
    throw new Error(`Unknown workflow step action: ${step.action}`);
  }
}

async function runTask(browser, task, workflow, targetBaseUrl, testHooks) {
  const allowedOrigin = mockOrigin(targetBaseUrl);
  const key = encryptionKey(config.fileKeyBase64);
  const context = await browser.newContext({ acceptDownloads: true, serviceWorkers: 'block' });
  // All requests, including redirects and subresources, stay inside the approved mock origin.
  await context.route('**/*', route => new URL(route.request().url()).origin === allowedOrigin
    ? route.continue() : route.abort('blockedbyclient'));
  await context.routeWebSocket('**/*', socket => socket.close());
  let download;
  try {
    const page = await context.newPage();

    console.log(`[collector] task ${task.taskId}: opening ${workflow.loginUrl(targetBaseUrl)}`);
    await page.goto(workflow.loginUrl(targetBaseUrl));

    await backendClient.waitingForAuth(task.taskId);
    console.log(`[collector] task ${task.taskId}: WAITING_FOR_USER_AUTH — 열린 브라우저에서 직접 인증해 주세요.`);

    // TEST-ONLY seam: an unattended E2E verification run has no live human to click the real
    // login button, so it may pass onWaitingForAuth(page) to act on the SAME page/context/browser
    // a real human would use (never a second/new browser). In production this hook is never
    // supplied, and this process always blocks for a real human — it never fills in or guesses
    // the auth UI itself (Section 3/14).
    if (testHooks && typeof testHooks.onWaitingForAuth === 'function') {
      await testHooks.onWaitingForAuth(page);
    }

    if (workflow.authDetect.type === 'selector') {
      await page.waitForSelector(workflow.authDetect.selector, { timeout: config.authWaitTimeoutMs });
    } else if (workflow.authDetect.type === 'urlPattern') {
      await page.waitForURL(workflow.authDetect.pattern, { timeout: config.authWaitTimeoutMs });
    } else {
      throw new Error(`Unknown authDetect.type: ${workflow.authDetect.type}`);
    }

    // Login success is not consent to collect. No automated credential/challenge handling.
    const consent = testHooks?.onCollectionConsent
      ? await testHooks.onCollectionConsent(task)
      : await askCollectionConsent(task);
    if (consent !== true) throw new Error('USER_COLLECTION_CONSENT_REQUIRED');
    await backendClient.resume(task.taskId);
    console.log(`[collector] task ${task.taskId}: AUTHENTICATED — same browser session resumed.`);

    await backendClient.collecting(task.taskId);
    for (const step of workflow.collectSteps(task)) {
      await runStep(page, step);
    }
    console.log(`[collector] task ${task.taskId}: COLLECTING steps done.`);

    await backendClient.downloading(task.taskId);
    [download] = await Promise.all([
      page.waitForEvent('download'),
      runStep(page, workflow.downloadTrigger),
    ]);

    const metadata = await saveEncryptedDownload(download, config.downloadDir, key);
    await backendClient.completed(task.taskId, metadata);
    console.log(`[collector] task ${task.taskId}: COMPLETED.`);

  } finally {
    await download?.delete().catch(() => {});
    await context.close(); // deletes Chromium-managed temporary download and session cookies
  }
}

async function askCollectionConsent(task) {
  if (!process.stdin.isTTY) return false;
  const terminal = createInterface({ input: process.stdin, output: process.stdout });
  try {
    const answer = await terminal.question(
      `수집 승인: ${task.sourceCode}, ${task.requestedFrom} ~ ${task.requestedTo}. 계속하려면 y 입력: `);
    return answer.trim().toLowerCase() === 'y';
  } finally { terminal.close(); }
}

export async function pollAndRun(
    { once = false, targetBaseUrl = config.easyBaseUrl, testHooks = undefined } = {}) {
  mockOrigin(targetBaseUrl); // test hooks cannot enable real-site authentication or network access
  for (;;) {
    await backendClient.heartbeat().catch((err) => console.warn('[collector] heartbeat failed:', err.message));
    const task = await backendClient.nextTask();
    if (!task) {
      if (once) {
        console.log('[collector] no queued task (--once) — exiting.');
        return;
      }
      await sleep(config.pollIntervalMs);
      continue;
    }
    console.log(`[collector] claimed task ${task.taskId} sourceCode=${task.sourceCode} status=${task.status}`);
    let browser;
    try {
      const workflow = resolveWorkflow(task.sourceCode);
      encryptionKey(config.fileKeyBase64);
      browser = await chromium.launch({ headless: false }); // VISIBLE — Section 4 requirement
      await runTask(browser, task, workflow, targetBaseUrl, testHooks);
    } catch (err) {
      console.error(`[collector] task ${task.taskId}: collection failed; use manual upload`);
      await backendClient.failed(task.taskId, 'COLLECTION_FAILED_USE_MANUAL_UPLOAD').catch(() => {});
    } finally {
      await browser?.close();
    }
    if (once) return;
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const once = process.argv.includes('--once');
  pollAndRun({ once }).catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
