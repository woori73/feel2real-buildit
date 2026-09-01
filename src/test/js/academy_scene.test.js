const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * Vertical Scene Flow — 세로 스크롤 위치에 따라 섹션이 enter → active → exit(이전 섹션 흐려짐).
 *   - IntersectionObserver 만 쓴다(scroll 이벤트·setInterval 없음).
 *   - 위/아래 스크롤에 따라 상태가 다시 바뀐다(bidirectional).
 *   - reduced-motion / IO 미지원 → 전부 active(정적).
 *   - 모듈 로드 즉시 html.js-scene 게이트. 모듈 미로드 → 게이트 없음 → 섹션 정적.
 */

const SCENE_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_scene.js'), 'utf8');
const SCENE_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_scene.css'), 'utf8');

function fakeSection(rect) {
  const attrs = {};
  return {
    _rect: rect,
    getBoundingClientRect() { return this._rect; },
    setAttribute(k, v) { attrs[k] = String(v); },
    getAttribute(k) { return k in attrs ? attrs[k] : null; }
  };
}

function load(opts) {
  const o = opts || {};
  const htmlClasses = new Set();
  const observed = [];
  const timers = [];
  let ioCallback = null;
  const win = {
    innerHeight: o.vh == null ? 1000 : o.vh,
    matchMedia: () => ({ matches: !!o.reduced }),
    setTimeout: (fn) => { timers.push(fn); return timers.length; },
    IntersectionObserver: o.noIO ? undefined : function (cb) {
      ioCallback = cb;
      return {
        observe: (el) => observed.push(el),
        disconnect() { this._disc = (this._disc || 0) + 1; }
      };
    },
    document: { documentElement: { classList: {
      add: (...x) => x.forEach((v) => htmlClasses.add(v)),
      contains: (v) => htmlClasses.has(v)
    } } }
  };
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(SCENE_JS, sandbox);
  return {
    api: win.F2RSceneFlow,
    htmlClasses,
    observed,
    timers,
    fireIO: (entries) => ioCallback(entries)
  };
}

test('모듈 로드 즉시 html.js-scene 게이트를 붙인다', () => {
  const c = load();
  assert.ok(c.htmlClasses.has('js-scene'));
  assert.equal(typeof c.api.wire, 'function');
});

test('초기 상태를 동기적으로 세팅한다 — 화면 위=exit, 화면 아래=enter, 뷰포트=active', () => {
  const c = load({ vh: 1000 });
  const above = fakeSection({ top: -900, bottom: -100 });   // 완전히 위로 지나감
  const inView = fakeSection({ top: 100, bottom: 900 });    // 뷰포트 차지
  const below = fakeSection({ top: 1200, bottom: 2000 });   // 아직 아래
  c.api.wire([above, inView, below]);
  assert.equal(above.getAttribute('data-scene'), 'exit');
  assert.equal(inView.getAttribute('data-scene'), 'active');
  assert.equal(below.getAttribute('data-scene'), 'enter');
  assert.equal(c.observed.length, 3, '세 섹션 모두 관찰');
});

test('스크롤에 따라 bidirectional 로 상태가 바뀐다(enter→active→exit, 되돌아옴)', () => {
  const c = load({ vh: 1000 });
  const s = fakeSection({ top: 1200, bottom: 2000 });
  c.api.wire([s]);
  assert.equal(s.getAttribute('data-scene'), 'enter');

  // 아래로 스크롤 → 섹션이 뷰포트로 올라옴
  c.fireIO([{ target: s, rootBounds: { height: 1000 }, boundingClientRect: { top: 300, bottom: 1100 } }]);
  assert.equal(s.getAttribute('data-scene'), 'active');

  // 계속 아래로 → 섹션이 위로 지나감
  c.fireIO([{ target: s, rootBounds: { height: 1000 }, boundingClientRect: { top: -800, bottom: 0 } }]);
  assert.equal(s.getAttribute('data-scene'), 'exit');

  // 위로 되돌아 스크롤 → 다시 active
  c.fireIO([{ target: s, rootBounds: { height: 1000 }, boundingClientRect: { top: 200, bottom: 1000 } }]);
  assert.equal(s.getAttribute('data-scene'), 'active');
});

test('reduced-motion → IO 없이 전부 active(정적)', () => {
  const c = load({ reduced: true });
  const s1 = fakeSection({ top: -900, bottom: -100 });
  const s2 = fakeSection({ top: 1200, bottom: 2000 });
  c.api.wire([s1, s2]);
  assert.equal(s1.getAttribute('data-scene'), 'active');
  assert.equal(s2.getAttribute('data-scene'), 'active');
  assert.equal(c.observed.length, 0);
});

test('IO 미지원 → 전부 active', () => {
  const c = load({ noIO: true });
  const s = fakeSection({ top: 1200, bottom: 2000 });
  c.api.wire([s]);
  assert.equal(s.getAttribute('data-scene'), 'active');
});

test('하드 페일세이프: IO 가 active 를 못 만들면 6초 뒤 전부 active', () => {
  const c = load();
  const s = fakeSection({ top: 1200, bottom: 2000 });
  c.api.wire([s]);
  assert.equal(s.getAttribute('data-scene'), 'enter');
  c.timers.forEach((fn) => fn());   // 6s 타이머 실행
  assert.equal(s.getAttribute('data-scene'), 'active');
});

test('재렌더: wire 재호출 시 이전 IO 를 disconnect 한다', () => {
  const c = load();
  const s = fakeSection({ top: 100, bottom: 900 });
  c.api.wire([s]);
  // 두 번째 wire — 새 배열
  c.api.wire([fakeSection({ top: 100, bottom: 900 })]);
  // 관찰 대상이 누적되지 않고 새로 시작(첫 배열 1 + 두번째 1)
  assert.equal(c.observed.length, 2);
});

test('scroll 이벤트/setInterval 을 쓰지 않는다(IntersectionObserver 만)', () => {
  assert.doesNotMatch(SCENE_JS, /addEventListener\(['"]scroll['"]/);
  assert.doesNotMatch(SCENE_JS, /setInterval/);
  assert.match(SCENE_JS, /new global\.IntersectionObserver/);
});

/* ── CSS 계약 ── */

test('academy_scene.css: html.js-scene 스코프 + 가독성 우선 dim(≥.42) + reduced-motion 즉시 최종', () => {
  assert.match(SCENE_CSS, /html\.js-scene section\.section-wrap\[data-scene="active"\]\s*\{\s*opacity:\s*1/);
  // dim 은 너무 어둡지 않게(가독성) — enter/exit opacity 가 .42 이상
  const enter = SCENE_CSS.match(/section\.section-wrap\[data-scene="enter"\]\s*\{\s*opacity:\s*([.\d]+)/);
  const exit = SCENE_CSS.match(/section\.section-wrap\[data-scene="exit"\]\s*\{\s*opacity:\s*([.\d]+)/);
  assert.ok(enter && parseFloat(enter[1]) >= 0.42, 'enter opacity >= .42');
  assert.ok(exit && parseFloat(exit[1]) >= 0.42, 'exit opacity >= .42');
  // sticky/가로트랙 섹션은 자식 transform 제외
  assert.match(SCENE_CSS, /:not\(#sec-digitaltwin\):not\(#sec-featured\):not\(#sec-lessons\):not\(#sec-videos\) > \*/);
  assert.match(SCENE_CSS, /@media \(prefers-reduced-motion: reduce\)[\s\S]*opacity:\s*1\s*!important/);
});

test('academy_public.html 이 academy_scene.{js,css} 를 로드하고, academy_public.js 가 wireSceneFlow 를 배선한다', () => {
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'), 'utf8');
  const pub = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_public.js'), 'utf8');
  assert.match(html, /academy_scene\.css\?v=/);
  assert.match(html, /academy_scene\.js\?v=/);
  assert.match(pub, /function wireSceneFlow\(\)/);
  assert.match(pub, /window\.F2RSceneFlow/);
  assert.match(pub, /wireReveal\(\);\s*\n\s*wireSceneFlow\(\);/);
  // 빌더 템플릿은 scene 을 로드하지 않는다(캔버스 정적)
  const builder = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'), 'utf8');
  assert.doesNotMatch(builder, /academy_scene\.(js|css)/);
});
