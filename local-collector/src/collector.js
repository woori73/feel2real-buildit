// EASY Visible Local Browser Collector — the generic engine (Section 6).
// Normal collection never calls an LLM. Every action below is a deterministic Playwright call
// driven by a declarative workflow (src/workflows/*). This file never reads/stores/reports a
// certificate, password, OTP, CVC, or full card number — the human enters those directly into
// the visible browser during WAITING_FOR_USER_AUTH, and this process never touches that page.
import { chromium } from 'playwright';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { config } from './config.js';
import { backendClient } from './backendClient.js';
import { resolveWorkflow } from './workflows/index.js';

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
  const context = await browser.newContext();
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

  await backendClient.resume(task.taskId);
  console.log(`[collector] task ${task.taskId}: AUTHENTICATED — same browser session resumed.`);

  await backendClient.collecting(task.taskId);
  for (const step of workflow.collectSteps(task)) {
    await runStep(page, step);
  }
  console.log(`[collector] task ${task.taskId}: COLLECTING steps done.`);

  await backendClient.downloading(task.taskId);
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    runStep(page, workflow.downloadTrigger),
  ]);

  fs.mkdirSync(config.downloadDir, { recursive: true });
  const suggested = download.suggestedFilename();
  const destPath = path.join(config.downloadDir, `task-${task.taskId}-${suggested}`);
  await download.saveAs(destPath);

  const buffer = fs.readFileSync(destPath);
  const sha256 = crypto.createHash('sha256').update(buffer).digest('hex');
  const size = buffer.length;
  console.log(`[collector] task ${task.taskId}: downloaded ${destPath} (${size} bytes, sha256=${sha256})`);

  await backendClient.completed(task.taskId, { filename: path.basename(destPath), size, sha256 });
  console.log(`[collector] task ${task.taskId}: COMPLETED.`);

  await context.close();
}

export async function pollAndRun(
    { once = false, targetBaseUrl = config.easyBaseUrl, testHooks = undefined } = {}) {
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
    const workflow = resolveWorkflow(task.sourceCode);
    const browser = await chromium.launch({ headless: false }); // VISIBLE — Section 4 requirement
    try {
      await runTask(browser, task, workflow, targetBaseUrl, testHooks);
    } catch (err) {
      console.error(`[collector] task ${task.taskId} failed:`, err);
      await backendClient.failed(task.taskId, String(err.message || err)).catch(() => {});
    } finally {
      await browser.close();
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
