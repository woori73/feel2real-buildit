const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// V2-10: Vertical Scene Flow — 각 Story Section 이 스크롤 위치에 따라 enter → active → exit
//   (양방향). IntersectionObserver 만, scroll 이벤트 없음, transform/opacity 만.

const MOTION_SRC = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_storefront_motion.js'), 'utf8');
const SCENE_CSS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_scene.css'), 'utf8');

function makeSection(rect) {
  const attrs = {};
  return {
    _attrs: attrs,
    getBoundingClientRect: () => rect,
    getAttribute: (k) => (k in attrs ? attrs[k] : null),
    setAttribute: (k, v) => { attrs[k] = String(v); },
    removeAttribute: (k) => { delete attrs[k]; }
  };
}

function loadMotion({ reduced = false, hasIO = true, innerHeight = 800 } = {}) {
  const observers = [];
  const win = {
    innerHeight,
    matchMedia: (q) => ({ matches: reduced && /reduce/.test(q) }),
    setTimeout: () => 0,
    document: { documentElement: { classList: { add() {} } } }
  };
  if (hasIO) {
    win.IntersectionObserver = function (cb, opts) {
      const o = {
        cb, opts, targets: [], disconnected: false,
        observe(t) { this.targets.push(t); },
        disconnect() { this.disconnected = true; },
        fire(entries) { this.cb(entries); }
      };
      observers.push(o);
      return o;
    };
  }
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(MOTION_SRC, sandbox);
  return { M: win.F2RStorefrontMotion, observers };
}

test('sceneFlow: 초기 상태를 스크롤 위치로 동기 세팅한다 (위=exit, 뷰포트=active, 아래=enter)', () => {
  const { M } = loadMotion({ innerHeight: 1000 });
  const above = makeSection({ top: -900, bottom: -200 });   // 완전히 위 → exit
  const inView = makeSection({ top: 100, bottom: 700 });    // 뷰포트 안 → active
  const below = makeSection({ top: 900, bottom: 1600 });    // 아래 → enter
  M.sceneFlow([above, inView, below]);
  assert.equal(above.getAttribute('data-scene'), 'exit');
  assert.equal(inView.getAttribute('data-scene'), 'active');
  assert.equal(below.getAttribute('data-scene'), 'enter');
});

test('sceneFlow: IntersectionObserver 만 쓰고 scroll 이벤트를 걸지 않는다', () => {
  assert.doesNotMatch(MOTION_SRC, /addEventListener\(\s*['"]scroll['"]/);
  const { M, observers } = loadMotion();
  M.sceneFlow([makeSection({ top: 0, bottom: 500 })]);
  assert.equal(observers.length, 1);
  assert.deepEqual(observers[0].targets.length, 1);
});

test('sceneFlow: 양방향 — IO 콜백이 오면 스크롤 방향에 따라 상태가 다시 바뀐다', () => {
  const { M, observers } = loadMotion({ innerHeight: 1000 });
  const sec = makeSection({ top: 100, bottom: 700 });
  M.sceneFlow([sec]);
  assert.equal(sec.getAttribute('data-scene'), 'active');
  // 아래로 스크롤 → 섹션이 위로 → exit
  observers[0].fire([{ target: sec, boundingClientRect: { top: -800, bottom: -100 }, rootBounds: { height: 1000 } }]);
  assert.equal(sec.getAttribute('data-scene'), 'exit');
  // 다시 위로 스크롤 → 섹션이 되돌아옴 → active
  observers[0].fire([{ target: sec, boundingClientRect: { top: 100, bottom: 700 }, rootBounds: { height: 1000 } }]);
  assert.equal(sec.getAttribute('data-scene'), 'active');
  // 더 위로 → 섹션이 아래로 → enter
  observers[0].fire([{ target: sec, boundingClientRect: { top: 900, bottom: 1500 }, rootBounds: { height: 1000 } }]);
  assert.equal(sec.getAttribute('data-scene'), 'enter');
});

test('sceneFlow: data-story-staged(데스크톱 pin) 섹션은 건드리지 않는다', () => {
  const { M, observers } = loadMotion({ innerHeight: 1000 });
  const staged = makeSection({ top: 100, bottom: 700 });
  staged.setAttribute('data-story-staged', 'true');
  M.sceneFlow([staged]);
  assert.equal(staged.getAttribute('data-scene'), null, 'pin 섹션에 data-scene 을 붙였다');
  observers[0].fire([{ target: staged, boundingClientRect: { top: 100, bottom: 700 }, rootBounds: { height: 1000 } }]);
  assert.equal(staged.getAttribute('data-scene'), null);
});

test('sceneFlow: reduced-motion / IO 미지원 → 즉시 active(정적), IO 안 만든다', () => {
  for (const opts of [{ reduced: true }, { hasIO: false }]) {
    const { M, observers } = loadMotion(opts);
    const sec = makeSection({ top: 900, bottom: 1600 });
    M.sceneFlow([sec]);
    assert.equal(sec.getAttribute('data-scene'), 'active');
    assert.equal(observers.length, 0);
  }
});

test('sceneFlow: 빈/누락 입력은 조용히 무시', () => {
  const { M } = loadMotion();
  assert.doesNotThrow(() => M.sceneFlow(null));
  assert.doesNotThrow(() => M.sceneFlow([]));
  assert.equal(M.sceneFlow([]), null);
});

const CSS_DECLS = SCENE_CSS.replace(/\/\*[\s\S]*?\*\//g, ''); // 주석 제거 후 실제 규칙만

test('academy_scene.css: js-scene 스코프 + enter/active/exit + reduced-motion 최종상태, transform/opacity 만', () => {
  const c = CSS_DECLS.replace(/\s+/g, '');
  assert.match(c, /html\.js-scenesection\[data-scene\]\{transition:opacity/);
  assert.match(c, /section\[data-scene="enter"\]\{opacity:\.?\d/);
  assert.match(c, /section\[data-scene="enter"\]>\*\{transform:translateY\(\d+px\)/);
  assert.match(c, /section\[data-scene="active"\]>\*\{transform:none/);
  assert.match(c, /section\[data-scene="exit"\]>\*\{transform:translateY\(-\d+px\)/);
  assert.match(c, /@media\(prefers-reduced-motion:reduce\)\{[^}]*html\.js-scenesection\[data-scene\]/);
  // compositor-only: 세로 이동만. 회전·큰 zoom·parallax·scroll hijack 없음.
  assert.doesNotMatch(CSS_DECLS, /rotate|perspective|100vh|position:\s*sticky|position:\s*fixed/);
  assert.doesNotMatch(CSS_DECLS, /scale\(0?\.[0-8]\d?\)/); // 큰 축소 없음 (0.9~1 만)
});

test('academy_scene.css 는 Horizontal collection track/scroll-snap 을 건드리지 않는다', () => {
  assert.doesNotMatch(CSS_DECLS, /f2r-collection|scroll-snap|overflow-x/);
});
