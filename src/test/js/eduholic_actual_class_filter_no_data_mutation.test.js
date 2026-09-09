const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

test('placement guard performs no API or storage mutation', () => {
  assert.doesNotMatch(source, /fetch\(|localStorage|sessionStorage|DELETE|POST|PUT|PATCH/);
});
