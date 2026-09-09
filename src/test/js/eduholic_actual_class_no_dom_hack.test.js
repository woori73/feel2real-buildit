const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

test('placement fix filters render input instead of hiding/removing rendered cards', () => {
  assert.doesNotMatch(source, /querySelector|remove\(|style\.display|MutationObserver/);
  assert.match(source, /originalRenderVideos\(view, scopedCtx\)/);
});
