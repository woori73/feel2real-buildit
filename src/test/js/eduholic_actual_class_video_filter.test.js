const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/eduholic_actual_class_video_filter.js'), 'utf8');

function run(hostname, videos) {
  // Production academy_sections.js exports a frozen facade and createPublicContext()
  // returns a frozen context. The old test used mutable doubles and therefore hid the
  // exact bug that made #262/#263 ineffective in production.
  const sections = Object.freeze({
    createPublicContext() {
      return Object.freeze({
        videoList: () => videos,
        programCardMedia: (lesson) => `media:${lesson.mediaVideoId}`
      });
    }
  });
  const window = { location: { hostname }, F2RAcademySections: sections };
  vm.runInNewContext(source, { window, Object });
  return {
    sections: window.F2RAcademySections,
    ctx: window.F2RAcademySections.createPublicContext({})
  };
}

const videos = [
  { videoId: 19, purpose: 'LESSON', originalFilename: '신구중3_박원장_상호작용.mp4' },
  { videoId: 20, purpose: 'LESSON', originalFilename: '신구중3_2학기_내신대비_라이브_최종_53초.mp4' },
  { videoId: 21, purpose: 'LESSON', originalFilename: '압구정중2_학생학습.mp4' }
];

test('production-frozen Eduholic facade is replaced instead of illegally monkey-patched', () => {
  const result = run('eduholic.feel2real.co.kr', videos);
  assert.notEqual(result.sections.createPublicContext, undefined);
  assert.equal(Object.isFrozen(result.sections), true);
  assert.equal(Object.isFrozen(result.ctx), true);
});

test('Eduholic Actual Class excludes only the dedicated 53-second Hero asset', () => {
  const { ctx } = run('eduholic.feel2real.co.kr', videos);
  assert.deepEqual(Array.from(ctx.videoList(), v => v.videoId), [19, 21]);
});

test('Eduholic representative lesson does not render the 53-second Hero as program media', () => {
  const { ctx } = run('eduholic.feel2real.co.kr', videos);
  assert.equal(ctx.programCardMedia({ mediaVideoId: 20 }), '');
  assert.equal(ctx.programCardMedia({ mediaVideoId: 19 }), 'media:19');
  assert.equal(ctx.programCardMedia({ mediaVideoId: 21 }), 'media:21');
});

test('other academy hosts keep the original frozen public context unchanged', () => {
  const result = run('another.feel2real.co.kr', videos);
  assert.deepEqual(Array.from(result.ctx.videoList(), v => v.videoId), [19, 20, 21]);
  assert.equal(result.ctx.programCardMedia({ mediaVideoId: 20 }), 'media:20');
});
