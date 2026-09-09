const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');
test('53-second hero marker is excluded before Actual Class rendering', () => { assert.ok(source.indexOf('MAIN_FEATURE_MARKER') < source.indexOf('originalRenderVideos(view, scopedCtx)')); });
