const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');

test('missing videoList delegates to the original renderer', () => {
  assert.match(source, /if \(!ctx \|\| typeof ctx\.videoList !== 'function'\)/);
  assert.match(source, /return originalRenderVideos\(view, ctx\)/);
});
