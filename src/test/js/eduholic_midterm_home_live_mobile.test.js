const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_midterm_home_live.js'), 'utf8');

test('school LIVE cards keep Wiki to trend to actual lesson video order', () => {
  assert.ok(js.indexOf("tag: 'Wiki'") < js.indexOf("tag: '출제 경향'"));
  assert.ok(js.indexOf("tag: '출제 경향'") < js.indexOf("tag:'실제 수업 영상'"));
});
test('mobile video policy is lazy muted inline and pauses offscreen or competing playback', () => {
  ["preload = 'none'","defaultMuted = true","playsInline = true","pauseOtherVideos(video)","prepareVisibleVideo(false)","IntersectionObserver","data-src"].forEach((token) => assert.ok(js.includes(token), token));
});
