const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');
test('guard targets sec-videos through renderVideos only', () => { assert.match(source, /renderVideos/); assert.doesNotMatch(source, /renderMainFeature\s*=/); });
