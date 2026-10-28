const path = require('node:path');
const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({ testDir: __dirname, testMatch: 'public.spec.cjs', timeout: 90000,
  workers: 1, retries: 0, use: { browserName: 'chromium', trace: 'off', video: 'off' },
  reporter: [['list'], ['json', { outputFile: path.resolve(__dirname, '../../release-evidence/browser-results.json') }]] });
