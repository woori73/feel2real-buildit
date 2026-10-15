const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.resolve(
  __dirname, '../../main/resources/static/js/lesson_product_editor.js'), 'utf8');
const css = fs.readFileSync(path.resolve(
  __dirname, '../../main/resources/static/css/lesson_product_editor.css'), 'utf8');

test('lesson video picker renders the original filename and raw video preview without a thumbnail', () => {
  assert.match(source, /video\.originalFilename \|\| '이름 없음'/);
  assert.match(source, /videoPreviewMarkup\(video, ''\)/);
  assert.match(source, /preload="metadata" muted playsinline/);
  assert.match(css, /\.media-video-item video/);
});

test('broken lesson thumbnail falls back to the original video stream', () => {
  assert.match(source, /data-fallback-video/);
  assert.match(source, /document\.addEventListener\('error', onMediaPreviewError, true\)/);
  assert.match(source, /image\.replaceWith\(previewVideo\)/);
});
