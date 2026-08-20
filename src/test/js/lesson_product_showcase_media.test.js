const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadShowcase() {
  const file = path.join(
    __dirname, '../../main/resources/static/js/lesson_product_showcase.js');
  const source = fs.readFileSync(file, 'utf8');
  // 모듈이 아니라 IIFE라 global을 흉내 내 로드한다. wire()가 document를 만지므로 최소 stub을 준다.
  const sandbox = {
    document: {
      // wire()가 이미 배선된 것으로 보게 해 이벤트 등록을 건너뛴다.
      documentElement: { dataset: { lessonProductShowcaseWired: 'true' } },
      addEventListener() {}
    }
  };
  sandbox.window = sandbox;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  return sandbox.F2RLessonProductShowcase;
}

const showcase = loadShowcase();

function lesson(overrides) {
  return Object.assign({ lessonId: 1, name: '고2 영어', theme: 'NAVY' }, overrides || {});
}

test('given a lesson without media then no media markup is rendered', () => {
  // given — 빈 박스를 그리면 미등록 수업이 깨져 보인다.
  const html = showcase.render([lesson()], { slug: 'my-academy' });

  // then
  assert.equal(html.includes('course-media-area'), false);
});

test('given a video lesson then the thumbnail renders but no stream url', () => {
  // given — 초기 렌더에 stream이 들어가면 탭을 여는 것만으로 서버가 mp4 전량을 전송한다.
  const html = showcase.render(
    [lesson({ mediaType: 'VIDEO', mediaVideoId: 12 })], { slug: 'my-academy' });

  // then
  assert.equal(html.includes('/videos/12/thumbnail'), true);
  assert.equal(html.includes('/videos/12/stream') && !html.includes('data-lesson-video-src'), false);
  assert.equal(html.includes('<video'), false);
  assert.equal(html.includes('loading="lazy"'), true);
});

test('given an image lesson then the public hero-image endpoint is used', () => {
  // given — 응답 DTO에 storage key가 없으므로 전용 엔드포인트로만 받는다.
  const html = showcase.render(
    [lesson({ mediaType: 'IMAGE' })], { slug: 'my-academy' });

  // then
  assert.equal(html.includes('/api/academies/my-academy/lessons/1/hero-image'), true);
});

test('given a builder context then the tutor hero-image endpoint is used', () => {
  // given — 빌더는 DRAFT 학원을 렌더하는데 공개 경로는 발행 상태를 요구해 404가 난다.
  const html = showcase.render(
    [lesson({ mediaType: 'IMAGE' })], { heroImageBase: '/api/tutor/academy/lessons' });

  // then
  assert.equal(html.includes('/api/tutor/academy/lessons/1/hero-image'), true);
  assert.equal(html.includes('/api/academies/'), false);
});

test('given an image lesson without slug or builder base then media is skipped', () => {
  // given — URL을 만들 수 없으면 깨진 img를 그리는 대신 아무것도 그리지 않는다.
  const html = showcase.render([lesson({ mediaType: 'IMAGE' })], {});

  // then
  assert.equal(html.includes('course-media-area'), false);
});

test('given separate hero and OT videos then both render independently', () => {
  // given — 두 슬롯은 서로 다른 video id를 유지해야 한다.
  const html = showcase.render(
    [lesson({ mediaType: 'VIDEO', mediaVideoId: 12, otVideoId: 34, explanation1: '설명' })],
    { slug: 'my-academy' });

  // then
  assert.equal(html.includes('/videos/12/thumbnail'), true);
  assert.equal(html.includes('/videos/34/thumbnail'), true);
  assert.equal(html.includes('aria-label="수업 영상 재생"'), true);
  assert.equal(html.includes('aria-label="수업 OT 영상 재생"'), true);
});
