const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');
test('guard documents shared LESSON taxonomy preservation', () => assert.match(source, /shared LESSON taxonomy/));
