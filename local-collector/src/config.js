import os from 'node:os';
import path from 'node:path';

// Downloaded files must live OUTSIDE any git worktree (Section 9). Default: a per-user folder
// under the OS profile directory, matching the "work-input/" naming convention already used
// elsewhere in this project for local-only financial data, but never inside a repo checkout.
const DEFAULT_DOWNLOAD_DIR = path.join(os.homedir(), 'easy-local-collector', 'work-input');

export const config = {
  easyBaseUrl: process.env.EASY_BASE_URL || 'http://localhost:8080',
  collectorToken: process.env.EASY_COLLECTOR_TOKEN || '',
  downloadDir: process.env.EASY_COLLECTOR_DOWNLOAD_DIR || DEFAULT_DOWNLOAD_DIR,
  fileKeyBase64: process.env.EASY_COLLECTOR_FILE_KEY_BASE64 || '',
  pollIntervalMs: Number(process.env.EASY_COLLECTOR_POLL_INTERVAL_MS || 5000),
  authWaitTimeoutMs: Number(process.env.EASY_COLLECTOR_AUTH_TIMEOUT_MS || 10 * 60 * 1000), // 10 min
};
