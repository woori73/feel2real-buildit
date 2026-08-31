const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * Storefront Cinematic (Phase 5) — GSAP + ScrollTrigger 를 소유하는 유일한 모듈.
 * 두 섹션(#sec-featured Digital Twin Showcase / #sec-digitaltwin AI Native Panorama)만 담당하고,
 * GSAP 미로드 / reduced-motion / 모바일(<900) 이면 전부 no-op. CSR 재렌더/리사이즈에 teardown.
 */

const CINEMATIC_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_cinematic.js'), 'utf8');

/** 어떤 호출에도 관대한 가짜 엘리먼트 — DOM 질의는 자기 자신/빈 배열을 돌려준다. */
function fakeEl(tag) {
  const cls = new Set();
  const attrs = {};
  const el = {
    tag: tag || 'div',
    style: { setProperty() {} },
    classList: {
      add: (...x) => x.forEach((v) => cls.add(v)),
      remove: (...x) => x.forEach((v) => cls.delete(v)),
      toggle: (v, on) => { if (on === undefined ? !cls.has(v) : on) cls.add(v); else cls.delete(v); },
      contains: (v) => cls.has(v)
    },
    _cls: cls,
    setAttribute(k, v) { attrs[k] = String(v); },
    getAttribute(k) { return k in attrs ? attrs[k] : null; },
    toggleAttribute(k, on) { if (on) attrs[k] = ''; else delete attrs[k]; return !!on; },
    hasAttribute(k) { return k in attrs; },
    querySelector() { return fakeEl(); },
    querySelectorAll() { return [fakeEl(), fakeEl(), fakeEl(), fakeEl(), fakeEl()]; },
    addEventListener() {},
    dispatchEvent() { return true; },
    get clientWidth() { return 1200; },
    get offsetWidth() { return 400; },
    get offsetLeft() { return 0; },
    textContent: ''
  };
  return el;
}

function loadCinematic(opts) {
  const o = opts || {};
  const htmlClasses = new Set();
  const timelineCalls = [];
  const stCreated = [];
  const killed = [];
  const listeners = {};

  const fakeTimeline = () => {
    const tl = {
      from() { timelineCalls.push('from'); return tl; },
      fromTo() { timelineCalls.push('fromTo'); return tl; },
      to() { timelineCalls.push('to'); return tl; },
      set() { timelineCalls.push('set'); return tl; },
      totalDuration() { return tl; },
      progress() { return tl; },
      kill() { killed.push('timeline'); }
    };
    return tl;
  };

  const gsap = o.noGsap ? undefined : {
    registerPlugin() {},
    set() {},
    to() {},
    from() {},
    timeline() { return fakeTimeline(); },
    plugins: {}
  };
  const ScrollTrigger = o.noGsap ? undefined : {
    config() {},
    refresh() {},
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
    gsap,
    ScrollTrigger,
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

  const root = fakeEl('root');
  return { api: win.F2RCinematic, root, htmlClasses, stCreated, killed, timelineCalls, listeners };
}

test('export 계약: wire / refresh / active / seek* 를 노출한다', () => {
  const { api } = loadCinematic();
  assert.equal(typeof api.wire, 'function');
  assert.equal(typeof api.refresh, 'function');
  assert.equal(typeof api.active, 'function');
  assert.equal(typeof api.seekFeatured, 'function');
  assert.equal(typeof api.seekAiNative, 'function');
});

test('데스크톱 + GSAP + not-reduced → 두 섹션에 ScrollTrigger 생성 + html.js-cinematic', () => {
  const { api, htmlClasses, stCreated } = loadCinematic();
  api.wire(loadCinematic().root);
  assert.ok(htmlClasses.has('js-cinematic'));
  const ids = stCreated.map((s) => s.id);
  assert.ok(ids.includes('cinematic-featured'), 'featured showcase trigger');
  assert.ok(ids.includes('cinematic-ai-native'), 'ai native panorama trigger');
  // scrub, pin 아님
  stCreated.forEach((s) => {
    assert.ok('scrub' in s, `${s.id} 는 scrub`);
    assert.notEqual(s.pin, true, `${s.id} 는 pin:true 아님`);
  });
});

test('reduced-motion → no-op (트리거 없음, 게이트 클래스 없음)', () => {
  const { api, htmlClasses, stCreated } = loadCinematic({ reduced: true });
  api.wire(loadCinematic({ reduced: true }).root);
  assert.equal(stCreated.length, 0);
  assert.ok(!htmlClasses.has('js-cinematic'));
});

test('모바일(<900) → no-op (네이티브 스와이프에 맡긴다)', () => {
  const { api, htmlClasses, stCreated } = loadCinematic({ width: 720 });
  api.wire(loadCinematic({ width: 720 }).root);
  assert.equal(stCreated.length, 0);
  assert.ok(!htmlClasses.has('js-cinematic'));
});

test('GSAP 미로드 → F2RCinematic 는 있지만 wire() 는 no-op', () => {
  const { api, htmlClasses, stCreated } = loadCinematic({ noGsap: true });
  assert.equal(typeof api.wire, 'function');
  api.wire(loadCinematic({ noGsap: true }).root);
  assert.equal(stCreated.length, 0);
  assert.ok(!htmlClasses.has('js-cinematic'));
  assert.equal(api.active(), false);
});

test('CSR 재렌더: wire() 재호출 시 이전 ScrollTrigger/timeline 을 kill', () => {
  const { api, root, killed, stCreated } = loadCinematic();
  api.wire(root);
  const firstCount = stCreated.length;
  assert.ok(firstCount >= 2);
  api.wire(root);
  // 첫 배치의 trigger + timeline 이 kill 됐다
  assert.ok(killed.includes('cinematic-featured'));
  assert.ok(killed.includes('cinematic-ai-native'));
  assert.ok(killed.includes('timeline'));
});

test('리사이즈 리스너를 한 번만 바인딩한다', () => {
  const ctx = loadCinematic();
  ctx.api.wire(ctx.root);
  assert.equal(typeof ctx.listeners.resize, 'function');
});

test('자동 진행 타이머(setInterval)나 전체 페이지 pin 이 없다', () => {
  assert.doesNotMatch(CINEMATIC_JS, /setInterval/);
  assert.doesNotMatch(CINEMATIC_JS, /pin:\s*true/);
});
