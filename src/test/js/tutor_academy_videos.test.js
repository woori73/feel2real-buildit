const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.resolve(
  __dirname, '../../main/resources/static/js/tutor_academy_videos.js'), 'utf8');
const template = fs.readFileSync(path.resolve(
  __dirname, '../../main/resources/templates/tutor/tutor_academy_videos.html'), 'utf8');

test('client boundaries remain inclusive at 120 seconds and 200MiB', () => {
  assert.match(source, /MAX_DURATION_SEC = 120/);
  assert.match(source, /MAX_SIZE_BYTES = 209715200/);
  assert.match(source, /file\.size > MAX_SIZE_BYTES/);
  assert.match(source, /duration > MAX_DURATION_SEC/);
  assert.match(template, /120초 이하 · 200MiB 이하/);
});

test('upload sends one same-origin multipart request through the application server', () => {
  assert.match(source, /const body = new FormData\(\)/);
  assert.match(source, /body\.append\('video', file\)/);
  assert.match(source, /fetchAuth\('\/api\/tutor\/academy\/videos', \{method: 'POST', body\}\)/);
  assert.doesNotMatch(source, /uploadUrl|ETag|file\.slice|PART_CONCURRENCY/);
});

test('upload UI accepts only MP4 and MOV and reports server errors', () => {
  assert.match(template, /accept="\.mp4,\.mov,video\/mp4,video\/quicktime"/);
  assert.match(source, /isSupportedFile/);
  assert.match(source, /await errorMessage\(res, '업로드에 실패했습니다\.'\)/);
  assert.match(source, /영상을 업로드하고 변환하고 있습니다/);
});

test('missing or broken thumbnail falls back to the video preview', () => {
  assert.match(source, /list\.addEventListener\('error', onThumbnailError, true\)/);
  assert.match(source, /preview\.src = image\.dataset\.videoUrl/);
  assert.match(source, /image\.replaceWith\(preview\)/);
});
