const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'),
  'utf8');

function run(hostname) {
  let received = null;
  const sections = {
    renderVideos(view, ctx) {
      received = ctx.videoList();
      return 'rendered';
    }
  };
  const context = {
    window: {
      location: { hostname },
      F2RAcademySections: sections
    }
  };
  vm.runInNewContext(source, context);
  return { sections, getReceived: () => received };
}

test('Eduholic actual class excludes only the 53-second homepage hero asset', () => {
  const app = run('eduholic.feel2real.co.kr');
  const videos = [
    { videoId: 19, purpose: 'LESSON', originalFilename: '신구중3_박원장_상호작용.mp4' },
    { videoId: 20, purpose: 'LESSON', originalFilename: '신구중3_2학기_내신대비_라이브_최종_53초.mp4' },
    { videoId: 21, purpose: 'LESSON', originalFilename: '압구정중2_학생학습.mp4' }
  ];

  assert.equal(app.sections.renderVideos({}, { videoList: () => videos }), 'rendered');
  assert.deepEqual(Array.from(app.getReceived(), (v) => v.videoId), [19, 21]);
});

test('Other academy hosts keep the shared renderVideos path untouched', () => {
  const app = run('another.feel2real.co.kr');
  const videos = [
    { videoId: 20, purpose: 'LESSON', originalFilename: '신구중3_2학기_내신대비_라이브_최종_53초.mp4' }
  ];

  app.sections.renderVideos({}, { videoList: () => videos });
  assert.deepEqual(Array.from(app.getReceived(), (v) => v.videoId), [20]);
});
