const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

test('guard changes only renderVideos and leaves the homepage public video pool available to Hero', () => {
  assert.match(source, /sections\.renderVideos\s*=/);
  assert.doesNotMatch(source, /F2RAcademyPublicVideos\s*=/);
  assert.doesNotMatch(source, /academyVideos\s*=/);
});
