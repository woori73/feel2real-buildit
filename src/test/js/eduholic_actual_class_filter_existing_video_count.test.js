const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');

test('non-Hero LESSON videos remain in the Actual Class input', () => {
  let output;
  const sections = { renderVideos(_view, ctx) { output = ctx.videoList(); return ''; } };
  vm.runInNewContext(source, { window: { location: { hostname: 'eduholic.feel2real.co.kr' }, F2RAcademySections: sections } });
  const videos = [
    { originalFilename: 'lesson-a.mp4', purpose: 'LESSON' },
    { originalFilename: '신구중3_2학기_내신대비_라이브_최종_53초.mp4', purpose: 'LESSON' },
    { originalFilename: 'lesson-b.mp4', purpose: 'LESSON' }
  ];
  sections.renderVideos({}, { videoList: () => videos });
  assert.equal(output.length, 2);
  assert.deepEqual(Array.from(output, v => v.originalFilename), ['lesson-a.mp4', 'lesson-b.mp4']);
});
