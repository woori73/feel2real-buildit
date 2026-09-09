const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'),
  'utf8');

test('placement guard is loaded only from the public academy template', () => {
  assert.match(html, /eduholic_actual_class_video_filter\.js/);
});
