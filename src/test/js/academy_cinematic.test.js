const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * Storefront Cinematic — GSAP + ScrollTrigger 를 소유하는 유일한 모듈.
 * 담당 섹션은 #sec-digitaltwin (AI Native Pipeline — 하나의 canvas / 하나의 timeline) 하나뿐.
 *   - GSAP 미로드 / reduced-motion / 모바일(<900) 이면 전부 no-op.
 *   - immediateRender 기본(true): progress 0 = 요소가 "흩어진 시작" 상태 → 스크롤하면 실제로 조립.
 *   - scrub, pin 없음, setInterval 없음.
 *   - CSR 재렌더/리사이즈에 teardown.
 */

const CINEMATIC_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_cinematic.js'), 'utf8');

// 공유 가변 rect — wireAiNativeMobile 의 sectionInView() 판정을 테스트에서 제어한다.
// { top, bottom } 만 본다(innerHeight 800 기준: top<720 && bottom>80 이면 "화면 안").
let fakeElRect = { top: 0, bottom: 500, left: 0, right: 300, width: 300, height: 500 };

function fakeEl() {
  const cls = new Set();
  const attrs = {};
  const el = {
    style: { setProperty() {} },
    classList: {
      add: (...x) => x.forEach((v) => cls.add(v)),
      remove: (...x) => x.forEach((v) => cls.delete(v)),
      toggle: (v, on) => { if (on === undefined ? !cls.has(v) : on) cls.add(v); else cls.delete(v); },
      contains: (v) => cls.has(v)
    },
    setAttribute(k, v) { attrs[k] = String(v); },
    getAttribute(k) { return k in attrs ? attrs[k] : null; },
    toggleAttribute(k, on) { if (on) attrs[k] = ''; else delete attrs[k]; return !!on; },
    hasAttribute(k) { return k in attrs; },
    querySelector() { return fakeEl(); },
    querySelectorAll() { return [fakeEl(), fakeEl(), fakeEl(), fakeEl(), fakeEl()]; },
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() { return true; },
    getBoundingClientRect() { return fakeElRect; },
    get clientWidth() { return 1200; },
    textContent: ''
  };
  return el;
}

function loadCinematic(opts) {
  const o = opts || {};
  // 섹션 위치를 테스트가 정한다: 기본 = 화면 안, sectionBelowFold = 화면 아래로 벗어남.
  fakeElRect = o.sectionBelowFold
    ? { top: 2000, bottom: 2600, left: 0, right: 300, width: 300, height: 600 }
    : { top: 0, bottom: 500, left: 0, right: 300, width: 300, height: 500 };
  const setSectionInView = () => {
    fakeElRect = { top: 40, bottom: 700, left: 0, right: 300, width: 300, height: 660 };
  };
  const htmlClasses = new Set();
  const stCreated = [];
  const killed = [];
  const listeners = {};

  const fakeTimeline = () => {
    const tl = {
      _paused: false, _progress: 0, _playedCount: 0,
      from() { return tl; }, fromTo() { return tl; }, to() { return tl; },
      set() { return tl; }, totalDuration() { return tl; },
      progress(p) { if (p !== undefined) tl._progress = p; return tl; },
      pause(p) { tl._paused = true; if (p !== undefined) tl._progress = p; return tl; },
      play(p) { tl._paused = false; tl._playedCount += 1; if (p !== undefined) tl._progress = p; return tl; },
      paused() { return tl._paused; },
      kill() { killed.push('timeline'); }
    };
    fakeTimeline._last = tl;
    return tl;
  };

  const gsap = o.noGsap ? undefined : {
    registerPlugin() {}, set() {}, to() {}, from() {},
    timeline(vars) { fakeTimeline._lastVars = vars; return fakeTimeline(); },
    plugins: {}
  };
  const ScrollTrigger = o.noGsap ? undefined : {
    config() {}, refresh() {},
    create(cfg) {
      const st = Object.assign({ kill() { killed.push(cfg.id || 'st'); } }, { start: 0, end: 1000 }, cfg);
      stCreated.push(st);
      return st;
    }
  };

  const documentEl = {
    documentElement: {
      classList: {
        add: (...x) => x.forEach((v) => htmlClasses.add(v)),
        remove: (...x) => x.forEach((v) => htmlClasses.delete(v)),
        contains: (v) => htmlClasses.has(v)
      }
    }
  };

  const ioList = [];
  function FakeIO(cb) { this.cb = cb; this.el = null; this.disconnected = false; ioList.push(this); }
  FakeIO.prototype.observe = function (el) { this.el = el; };
  FakeIO.prototype.disconnect = function () { this.disconnected = true; };
  FakeIO.prototype.fire = function (isIntersecting) {
    this.cb([{ isIntersecting: isIntersecting, target: this.el }]);
  };

  const win = {
    document: documentEl,
    gsap, ScrollTrigger,
    innerWidth: o.width == null ? 1280 : o.width,
    innerHeight: 800,
    matchMedia: () => ({ matches: !!o.reduced }),
    addEventListener: (t, fn) => { listeners[t] = fn; },
    removeEventListener: (t) => { if (listeners[t]) delete listeners[t]; },
    setTimeout: (fn) => { fn(); return 1; },
    clearTimeout() {},
    requestAnimationFrame: (fn) => { fn(); },
    scrollTo() {},
    IntersectionObserver: o.noIO ? undefined : FakeIO,
    CustomEvent: function (type, init) { this.type = type; this.detail = (init || {}).detail; }
  };

  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(CINEMATIC_JS, sandbox);

  return { api: win.F2RCinematic, root: fakeEl(), htmlClasses, stCreated, killed, listeners, gsap, fakeTimeline, ioList, setSectionInView };
}

test('export 계약: wire / refresh / active — seek* 는 노출하지 않는다(showcase 제거됨)', () => {
  const { api } = loadCinematic();
  assert.equal(typeof api.wire, 'function');
  assert.equal(typeof api.refresh, 'function');
  assert.equal(typeof api.active, 'function');
  assert.equal(api.seekFeatured, undefined);
  assert.equal(api.seekAiNative, undefined);
});

test('데스크톱 + GSAP + not-reduced → AI Native ScrollTrigger 1개 + html.js-cinematic', () => {
  const c = loadCinematic();
  c.api.wire(c.root);
  assert.ok(c.htmlClasses.has('js-cinematic'));
  const ids = c.stCreated.map((s) => s.id);
  assert.deepEqual(ids, ['cinematic-ai-native']);
  assert.ok('scrub' in c.stCreated[0]);
  assert.notEqual(c.stCreated[0].pin, true);
});

test('#sec-digitaltwin 만 담당 — cinematic-featured/wireFeatured/seekFeatured 없음', () => {
  assert.doesNotMatch(CINEMATIC_JS, /cinematic-featured|function wireFeatured|seekFeatured/);
  assert.match(CINEMATIC_JS, /root\.querySelector\('#sec-digitaltwin'\)/);
});

test('immediateRender:false 를 쓰지 않는다 — progress 0 에서 요소는 흩어진 시작 상태', () => {
  assert.doesNotMatch(CINEMATIC_JS, /immediateRender:\s*false/);
});

test('데스크톱 timeline — 정적 scene 교체가 아니라 오브젝트의 큰 위치 이동이 주 신호다', () => {
  // 데스크톱 경로는 여전히 per-scene 빌더 없이 하나의 연속 timeline 이다(모바일만 Scene 분리).
  const desktop = CINEMATIC_JS.slice(
    CINEMATIC_JS.indexOf('function wireAiNativeDesktop'),
    CINEMATIC_JS.indexOf('function buildSceneTimeline'));
  assert.doesNotMatch(desktop, /buildSceneTweens|data-ai-scene/);
  // 좌표계: 데스크톱 660×460 / 모바일 330×900 canvas 를 stage 폭에 맞춘다
  assert.match(CINEMATIC_JS, /function fitCanvas/);
  assert.match(CINEMATIC_JS, /function canvasBox/);
  assert.match(CINEMATIC_JS, /\{ w: 330, h: 560 \}\s*:\s*\{ w: 660, h: 460 \}/);
  // scrub timeline 이므로 .from() 대신 .fromTo()(양끝 명시)를 쓴다
  assert.match(CINEMATIC_JS, /tl\.fromTo\(srcs,/);
  assert.doesNotMatch(CINEMATIC_JS, /tl\.from\([^T]/);
  // A: 학교 자료가 화면 밖에서 들어온다(데스크톱 -360px, 모바일 -46px)
  assert.match(CINEMATIC_JS, /x: -360/);
  // C: 문제가 Brain 쪽에서 200px 이상 이동해 부채꼴로 나온다
  assert.match(CINEMATIC_JS, /\[210, 250, 250, 232\]/);
  // 연결선(connector)이 스크롤 내내 그려진다
  assert.match(CINEMATIC_JS, /strokeDashoffset/);
  // 하나의 timeline 을 progress 로 직접 구동한다
  assert.match(CINEMATIC_JS, /tl\.progress\(p\)/);
});

test('reduced-motion → no-op', () => {
  const c = loadCinematic({ reduced: true });
  c.api.wire(c.root);
  assert.equal(c.stCreated.length, 0);
  assert.ok(!c.htmlClasses.has('js-cinematic'));
});

test('모바일(<900) → ScrollTrigger 도 js-cinematic 도 없다(세로형 자동재생 경로)', () => {
  const c = loadCinematic({ width: 720 });
  c.api.wire(c.root);
  assert.equal(c.stCreated.length, 0, '모바일은 ScrollTrigger 를 만들지 않는다');
  assert.ok(!c.htmlClasses.has('js-cinematic'), '모바일은 scrub 마커를 붙이지 않는다');
});

test('모바일 + reduced-motion → no-op(정적 완성 Pipeline)', () => {
  const c = loadCinematic({ width: 720, reduced: true });
  c.api.wire(c.root);
  assert.equal(c.stCreated.length, 0);
  assert.ok(!c.htmlClasses.has('js-cinematic'));
});

// 모바일 단계별 횡스크롤 Story 전용 fake DOM — section > [data-ai-scenes] > [data-collection-track]
// + 5개 [data-ai-scene]. track.scrollLeft 을 바꾸고 scroll 리스너를 부르면 활성 Scene 이 바뀐다.
function loadScenes(opts) {
  const o = opts || {};
  const keys = ['sources', 'brain', 'twin', 'questions', 'final'];
  const played = [0, 0, 0, 0, 0];
  const emitted = [];
  const winListeners = {};
  const trackListeners = {};
  let ioInstances = [];

  const leaf = () => ({
    querySelector: () => leaf(), querySelectorAll: () => [leaf(), leaf(), leaf(), leaf()],
    getAttribute: () => null, style: {}
  });
  const sceneEls = keys.map((k) => ({
    getAttribute: (a) => (a === 'data-ai-scene-key' ? k : null),
    querySelector: () => leaf(),
    querySelectorAll: () => [leaf(), leaf(), leaf(), leaf()]
  }));
  const cards = [0, 1, 2, 3, 4].map((i) => ({ offsetLeft: i * 100 }));
  const track = {
    scrollLeft: 0,
    addEventListener: (t, fn) => { trackListeners[t] = fn; },
    removeEventListener: (t) => { delete trackListeners[t]; },
    querySelectorAll: () => cards
  };
  const scenesRoot = {
    querySelector: (s) => (s.indexOf('collection-track') >= 0 ? track : leaf()),
    querySelectorAll: () => sceneEls
  };
  const section = {
    querySelector: (s) => (s.indexOf('ai-scenes') >= 0 ? scenesRoot : leaf()),
    getBoundingClientRect: () => (o.belowFold
      ? { top: 3000, bottom: 3600, width: 320, height: 600 }
      : { top: 20, bottom: 620, width: 320, height: 600 })
  };
  const root = { querySelector: (s) => (s.indexOf('sec-digitaltwin') >= 0 ? section : leaf()), querySelectorAll: () => [] };

  let n = 0;
  const gsap = {
    registerPlugin() {}, set() {}, to() {},
    timeline() {
      const idx = n; n += 1;
      const tl = {
        _i: idx,
        fromTo() { return tl; }, to() { return tl; }, set() { return tl; },
        progress() { return tl; },
        pause() { return tl; },
        play() { played[idx] = (played[idx] || 0) + 1; return tl; },
        kill() {}
      };
      return tl;
    }
  };
  const win = {
    document: { documentElement: { classList: { add() {}, remove() {}, contains: () => false } } },
    gsap,
    ScrollTrigger: { config() {}, refresh() {}, create() { return { kill() {} }; } },
    innerWidth: 375, innerHeight: 800,
    matchMedia: () => ({ matches: !!o.reduced }),
    addEventListener: (t, fn) => { winListeners[t] = fn; },
    removeEventListener: (t) => { delete winListeners[t]; },
    requestAnimationFrame: (fn) => { fn(); },
    setTimeout: (fn) => { fn(); return 1; }, clearTimeout() {},
    IntersectionObserver: o.noIO ? undefined : function (cb) {
      const io = { cb, observe() {}, disconnect() {}, fire() { cb([{ isIntersecting: true }]); } };
      ioInstances.push(io);
      return io;
    },
    CustomEvent: function (t, i) { this.type = t; this.detail = (i || {}).detail; }
  };
  // emitScene → root.dispatchEvent 로 CustomEvent 를 던진다 — 활성 Scene index 를 관찰한다.
  root.dispatchEvent = (ev) => { if (ev && ev.detail) emitted.push(ev.detail.index); return true; };

  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(CINEMATIC_JS, sandbox);

  return {
    api: win.F2RCinematic, root, played, emitted,
    scrollTo(i) { track.scrollLeft = i * 100; if (trackListeners.scroll) trackListeners.scroll(); },
    fireIO() { ioInstances.forEach((io) => io.fire()); },
    winListeners
  };
}

test('모바일 Scene: 섹션 진입 시 선두 Scene(0) 을 1회 재생한다 — 전체 파이프라인 autoplay 아님', () => {
  const c = loadScenes();
  c.api.wire(c.root);
  assert.equal(c.played[0], 1, 'Scene 0 재생');
  assert.deepEqual(c.played.slice(1), [0, 0, 0, 0], '나머지 Scene 은 아직 재생 안 함(자동 다음 장면 없음)');
  assert.deepEqual(c.emitted, [0]);
});

test('모바일 Scene: swipe 로 활성 카드가 바뀔 때마다 그 Scene 을 재생(양방향, 되돌아와도 재재생)', () => {
  const c = loadScenes();
  c.api.wire(c.root);           // Scene 0 재생
  c.scrollTo(1);                // → Scene 1
  c.scrollTo(2);                // → Scene 2
  c.scrollTo(1);                // ← Scene 1 (되돌아옴)
  c.scrollTo(0);                // ← Scene 0
  assert.equal(c.played[0], 2, 'Scene 0 = 진입 + 되돌아옴 = 2회');
  assert.equal(c.played[1], 2, 'Scene 1 = 오른쪽 진입 + 왼쪽 재진입 = 2회');
  assert.equal(c.played[2], 1);
  assert.deepEqual(c.emitted, [0, 1, 2, 1, 0]);
});

test('모바일 Scene: 같은 카드에서 scroll 이 여러 번 와도 재생은 1회(무한 반복 금지)', () => {
  const c = loadScenes();
  c.api.wire(c.root);
  c.scrollTo(1);
  c.scrollTo(1);
  c.scrollTo(1);
  assert.equal(c.played[1], 1, '활성 index 가 안 바뀌면 재재생하지 않는다');
});

test('모바일 Scene: mobilePlayed(전체 1회 재생) 개념을 쓰지 않는다', () => {
  assert.doesNotMatch(CINEMATIC_JS, /mobilePlayed/);
  assert.match(CINEMATIC_JS, /function wireAiNativeScenes/);
  assert.match(CINEMATIC_JS, /function buildSceneTimeline/);
});

test('모바일 Scene: 섹션이 화면 밖이면 wire 시점에 아무 Scene 도 재생하지 않는다', () => {
  const c = loadScenes({ belowFold: true });
  c.api.wire(c.root);
  assert.deepEqual(c.played, [0, 0, 0, 0, 0]);
  // 사용자가 섹션까지 스크롤 → window scroll 폴백이 진입 판정 → Scene 0 재생
  assert.equal(typeof c.winListeners.scroll, 'function');
});

test('모바일 Scene: IntersectionObserver 미지원이어도 rAF 폴백으로 진입 Scene 재생', () => {
  const c = loadScenes({ noIO: true });
  c.api.wire(c.root);
  assert.equal(c.played[0], 1);
});

test('모바일 Scene: 자동 진행 타이머(setInterval)·timeline repeat 옵션 없음', () => {
  assert.doesNotMatch(CINEMATIC_JS, /setInterval/);
  assert.doesNotMatch(CINEMATIC_JS, /timeline\(\{[^}]*repeat/);
});

test('GSAP 미로드 → F2RCinematic 는 있지만 wire() 는 no-op', () => {
  const c = loadCinematic({ noGsap: true });
  assert.equal(typeof c.api.wire, 'function');
  c.api.wire(c.root);
  assert.equal(c.stCreated.length, 0);
  assert.ok(!c.htmlClasses.has('js-cinematic'));
  assert.equal(c.api.active(), false);
});

test('CSR 재렌더: wire() 재호출 시 이전 ScrollTrigger/timeline kill', () => {
  const c = loadCinematic();
  c.api.wire(c.root);
  assert.ok(c.stCreated.length >= 1);
  c.api.wire(c.root);
  assert.ok(c.killed.includes('cinematic-ai-native'));
  assert.ok(c.killed.includes('timeline'));
});

test('리사이즈 리스너를 한 번만 바인딩한다', () => {
  const c = loadCinematic();
  c.api.wire(c.root);
  assert.equal(typeof c.listeners.resize, 'function');
});

test('자동 진행 타이머(setInterval)나 pin:true 가 없다', () => {
  assert.doesNotMatch(CINEMATIC_JS, /setInterval/);
  assert.doesNotMatch(CINEMATIC_JS, /pin:\s*true/);
});
