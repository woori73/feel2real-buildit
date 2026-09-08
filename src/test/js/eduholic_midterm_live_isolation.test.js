'use strict';

/**
 * 회귀 테스트: 압구정중2 에 연결된 영상이 신구중 / 신사중 FRAME 에 절대 노출되지 않는다.
 * (작업지시서 §4 — midterm_live_video_links 의 exact school+grade 가 source of truth)
 *
 * eduholic_midterm_2026.js 를 실제로 실행한다. IntersectionObserver 콜백은 트리거하지 않아
 * scene 자동 전환/영상 재생은 일으키지 않고, 어느 FRAME 에 어떤 scene 이 "생성"됐는지만 본다.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { MiniElement } = require('./helpers/mini_dom');

const JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/eduholic_midterm_2026.js'), 'utf8');

function makeDoc() {
  const head = new MiniElement('head');
  const byId = {};
  const document = {
    head,
    documentElement: new MiniElement('html'),
    createElement(tag) { return new MiniElement(tag); },
    getElementById(id) { return byId[id] || null; },
    querySelectorAll() { return []; },
    addEventListener() {},
    _register(id, el) { byId[id] = el; }
  };
  return document;
}

function run(sources, liveVideos, showcase) {
  const document = makeDoc();
  const mtRoot = new MiniElement('div');
  mtRoot.setAttribute('id', 'mtRoot');
  mtRoot.setAttribute('data-handle', 'eduholic');
  mtRoot.setAttribute('data-slug', '압구정에듀홀릭');
  mtRoot.setAttribute('data-campaign-schools', '압구정중,신구중,신사중');
  mtRoot.setAttribute('data-ambient-video-ids', '');
  document._register('mtRoot', mtRoot);

  const routes = {
    '/midterm-campaign/wiki-sources': sources,
    '/midterm-campaign/showcase': showcase || [],
    '/midterm-campaign/live-videos': liveVideos,
    '/api/analyzes': []                // 출제 경향 없음 → twin scene 없음(가짜 금지)
  };
  function fetchStub(url) {
    const key = Object.keys(routes).find((k) => url.indexOf(k) !== -1);
    const body = key ? routes[key] : [];
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
  }

  const context = {
    document,
    window: { matchMedia: () => ({ matches: false }) },
    fetch: fetchStub,
    IntersectionObserver: function () { this.observe = function () {}; this.disconnect = function () {}; },
    setTimeout: () => 0,
    clearTimeout: () => {},
    console
  };
  context.window.document = document;
  context.window.matchMedia = context.window.matchMedia;
  vm.createContext(context);
  vm.runInContext(JS, context, { filename: 'eduholic_midterm_2026.js' });
  return { mtRoot };
}

function frameFor(mtRoot, schoolFragment) {
  return mtRoot.querySelectorAll('article.midterm-live__frame').find((a) => {
    const label = a.getAttribute('aria-label') || '';
    return label.indexOf(schoolFragment) === 0;
  }) || null;
}
function mediaScenes(frame) {
  if (!frame) return [];
  return frame.querySelectorAll('.midterm-live__scene').filter((s) => {
    const k = s.getAttribute('data-kind');
    return k === 'video' || k === 'embed' || k === 'linkcard';
  });
}

test('압구정중2 에 연결된 네이버 영상은 압구정중2 FRAME 에만 나오고 신구중/신사중엔 안 나온다', async () => {
  const sources = [
    { sourceId: 'school-print-1', kind: 'school-print', school: '압구정중', grade: '2학년',
      previewUrl: '/api/academies/eduholic/midterm-campaign/wiki-sources/school-print-1/preview',
      createdAt: '2026-09-01T00:00:00Z' },
    { sourceId: 'school-print-2', kind: 'school-print', school: '신구중', grade: '3학년',
      previewUrl: '/api/academies/eduholic/midterm-campaign/wiki-sources/school-print-2/preview',
      createdAt: '2026-09-02T00:00:00Z' },
    { sourceId: 'school-print-3', kind: 'school-print', school: '신사중', grade: '3학년',
      previewUrl: '/api/academies/eduholic/midterm-campaign/wiki-sources/school-print-3/preview',
      createdAt: '2026-09-03T00:00:00Z' }
  ];
  const liveVideos = [
    { school: '압구정중', grade: 2, role: 'STUDENT_STUDY', videoUrl: null, thumbnailUrl: null,
      sourceKind: 'EXTERNAL_LINK', externalUrl: 'https://naver.me/F4WnDovq', embedUrl: null,
      provider: 'NAVER', title: '압구정중2 수업', sortOrder: 0 }
  ];

  const { mtRoot } = run(sources, liveVideos);
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  assert.equal(mediaScenes(frameFor(mtRoot, '압구정중')).length, 1, '압구정중2 FRAME 에 영상 scene 1개');
  assert.equal(mediaScenes(frameFor(mtRoot, '신구중')).length, 0, '신구중3 FRAME 에 영상 scene 0개(격리)');
  assert.equal(mediaScenes(frameFor(mtRoot, '신사중')).length, 0, '신사중3 FRAME 에 영상 scene 0개(격리)');
});

test('EXTERNAL 영상: embedUrl 있으면 인라인 embed scene, 없으면 작은 링크 카드(거대 빈 프레임 아님)', async () => {
  function frameWith(link) {
    const sources = [{ sourceId: 'school-print-1', kind: 'school-print', school: '신구중', grade: '3학년',
      previewUrl: '/x/school-print-1/preview', createdAt: '2026-09-01T00:00:00Z' }];
    return run(sources, [Object.assign({ school: '신구중', grade: 3, role: 'STUDENT_STUDY',
      videoUrl: null, thumbnailUrl: null, sourceKind: 'EXTERNAL_LINK', sortOrder: 0 }, link)]).mtRoot;
  }

  let root = frameWith({ provider: 'YOUTUBE', embedUrl: 'https://www.youtube.com/embed/dQw4w9WgXcQ',
    externalUrl: 'https://youtu.be/dQw4w9WgXcQ', title: 'YT 수업' });
  await new Promise((r) => setImmediate(r)); await new Promise((r) => setImmediate(r));
  let scenes = mediaScenes(frameFor(root, '신구중'));
  assert.equal(scenes.length, 1);
  assert.equal(scenes[0].getAttribute('data-kind'), 'embed', 'YouTube → 인라인 embed');

  root = frameWith({ provider: 'NAVER', embedUrl: 'https://tv.naver.com/embed/12345',
    externalUrl: 'https://tv.naver.com/v/12345', title: '네이버TV 수업' });
  await new Promise((r) => setImmediate(r)); await new Promise((r) => setImmediate(r));
  assert.equal(mediaScenes(frameFor(root, '신구중'))[0].getAttribute('data-kind'), 'embed', '네이버TV → 인라인 embed');

  root = frameWith({ provider: 'NAVER', embedUrl: null,
    externalUrl: 'https://naver.me/F4WnDovq', title: '네이버 쇼츠' });
  await new Promise((r) => setImmediate(r)); await new Promise((r) => setImmediate(r));
  const card = mediaScenes(frameFor(root, '신구중'))[0];
  assert.equal(card.getAttribute('data-kind'), 'linkcard', 'embed 불가 → 작은 링크 카드');
  assert.ok(card.querySelectorAll('.midterm-live__linkcard-cta').length === 1, 'CTA 링크 1개');
});

test('grade 불일치(압구정중3)면 압구정중2 FRAME 에도 안 붙는다 — grade 정확 일치', async () => {
  const sources = [
    { sourceId: 'school-print-1', kind: 'school-print', school: '압구정중', grade: '2학년',
      previewUrl: '/x/school-print-1/preview', createdAt: '2026-09-01T00:00:00Z' }
  ];
  const liveVideos = [
    { school: '압구정중', grade: 3, role: 'STUDENT_STUDY', videoUrl: null, thumbnailUrl: null,
      sourceKind: 'EXTERNAL_LINK', externalUrl: 'https://naver.me/x', embedUrl: null,
      provider: 'NAVER', title: '압구정중3 수업', sortOrder: 0 }
  ];
  const { mtRoot } = run(sources, liveVideos);
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  assert.equal(mediaScenes(frameFor(mtRoot, '압구정중 2학년')).length, 0, 'grade 2 FRAME 에 grade 3 링크 안 붙음');
});
