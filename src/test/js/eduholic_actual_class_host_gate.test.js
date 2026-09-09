const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

test('placement guard exits before touching shared sections on non-Eduholic hosts', () => {
  const gate = source.indexOf("if (window.location.hostname !== EDUHOLIC_HOST) return;");
  const patch = source.indexOf('sections.renderVideos =');
  assert.ok(gate >= 0);
  assert.ok(patch > gate);
});
