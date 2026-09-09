const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'),
  'utf8');

test('placement guard loads after sections and before academy_public', () => {
  const sections = html.indexOf('/js/academy_sections.js');
  const guard = html.indexOf('/js/eduholic_actual_class_video_filter.js');
  const publicRenderer = html.indexOf('/js/academy_public.js');
  assert.ok(sections >= 0);
  assert.ok(guard > sections);
  assert.ok(publicRenderer > guard);
});
