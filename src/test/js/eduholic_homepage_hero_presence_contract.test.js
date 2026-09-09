const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const homeLive = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_midterm_home_live.js'),
  'utf8');

test('53-second asset remains the dedicated 16:9 homepage main feature', () => {
  assert.match(homeLive, /MAIN_FEATURE_MARKER\s*=\s*'내신대비_라이브_최종_53초'/);
  assert.match(homeLive, /function renderMainFeature\(\)/);
  assert.match(homeLive, /mt-main-feature__video/);
  assert.match(homeLive, /aspect-ratio:16\/9/);
});
