const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');

test('guard uses the dedicated 53-second marker rather than school-name heuristics', () => {
  assert.match(source, /MAIN_FEATURE_MARKER = '내신대비_라이브_최종_53초'/);
  assert.doesNotMatch(source, /신구중3.*indexOf|압구정중.*indexOf|신사중.*indexOf/);
});
