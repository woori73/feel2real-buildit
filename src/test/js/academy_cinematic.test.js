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
    dispatchEvent() { return true; },
    get clientWidth() { return 1200; },
    textContent: ''
  };
  return el;
}

function loadCinematic(opts) {
  const o = opts || {};
  const htmlClasses = new Set();
  const stCreated = [];
  const killed = [];
  const listeners = {};

  const fakeTimeline = () => {
    const tl = {
      from() { return tl; }, fromTo() { return tl; }, to() { return tl; },
      set() { return tl; }, totalDuration() { return tl; }, progress() { return tl; },
      kill() { killed.push('timeline'); }
    };
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

  const win = {
    document: documentEl,
    gsap, ScrollTrigger,
    innerWidth: o.width == null ? 1280 : o.width,
    matchMedia: () => ({ matches: !!o.reduced }),
    addEventListener: (t, fn) => { listeners[t] = fn; },
    setTimeout: (fn) => { fn(); return 1; },
    clearTimeout() {},
    requestAnimationFrame: (fn) => { fn(); },
    scrollTo() {},
    CustomEvent: function (type, init) { this.type = type; this.detail = (init || {}).detail; }
  };

  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(CINEMATIC_JS, sandbox);

  return { api: win.F2RCinematic, root: fakeEl(), htmlClasses, stCreated, killed, listeners, gsap, fakeTimeline };
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

test('하나의 timeline — 정적 scene 교체가 아니라 오브젝트의 큰 위치 이동이 주 신호다', () => {
  // 5개 scene 을 도는 per-scene 빌더는 없다(하나의 연속 파이프라인)
  assert.doesNotMatch(CINEMATIC_JS, /buildSceneTweens|querySelectorAll\('\.ai-scene'\)|data-ai-scene/);
  // 좌표계: 데스크톱 660×460 / 모바일 330×900 canvas 를 stage 폭에 맞춘다
  assert.match(CINEMATIC_JS, /function fitCanvas/);
  assert.match(CINEMATIC_JS, /function canvasBox/);
  assert.match(CINEMATIC_JS, /\{ w: 330, h: 760 \}\s*:\s*\{ w: 660, h: 460 \}/);
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
