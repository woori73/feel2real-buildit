const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

test('guard does not replace the shared LESSON taxonomy', () => {
  assert.doesNotMatch(source, /purpose\s*!==\s*['"]LESSON['"]/);
  assert.match(source, /return videos\.filter\(function \(video\) \{ return !isHomepageHero\(video\); \}\);/);
});
