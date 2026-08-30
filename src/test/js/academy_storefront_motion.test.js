const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const SRC = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_storefront_motion.js'), 'utf8');

/** 최소 DOM 스텁 + IntersectionObserver/matchMedia/setTimeout 제어 */
function loadMotion({ reduced = false, hasIO = true } = {}) {
  const timers = [];
  const observers = [];
  function el(children = []) {
    return {
      _classes: new Set(),
      children,
      classList: {
        add(...c) { c.forEach((x) => this === undefined || null); },
      },
      querySelectorAll(sel) {
        // 매우 단순: 자식 중 _match 함수가 sel 을 통과시키는 것
        return children.filter((c) => c._match && c._match(sel));
      }
    };
  }
  // 실제로는 classList 를 제대로 구현
  function makeEl(match) {
    const classes = new Set();
    return {
      _match: match || (() => false),
      classList: {
        add: (...c) => c.forEach((x) => classes.add(x)),
        contains: (x) => classes.has(x)
      },
      has: (x) => classes.has(x)
    };
  }

  const win = {
    matchMedia: (q) => ({ matches: reduced && /reduce/.test(q) }),
    setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; }
  };
  if (hasIO) {
    win.IntersectionObserver = function (cb, opts) {
      const o = { cb, opts, observed: [], disconnected: false,
        observe(t) { this.observed.push(t); observers.push(this); },
        disconnect() { this.disconnected = true; },
        fireEnter() { this.cb([{ isIntersecting: true, target: this.observed[0] }]); }
      };
      return o;
    };
  }
  const sandbox = { window: win, globalThis: win };
  vm.createContext(sandbox);
  vm.runInContext(SRC, sandbox);
  const runTimers = () => {
    // 오름차순(ms)으로 실행, 실행 중 추가된 타이머도 처리
    let guard = 0;
    while (timers.length && guard++ < 500) {
      timers.sort((a, b) => a.ms - b.ms);
      const t = timers.shift();
      t.fn();
    }
  };
  return { M: win.F2RStorefrontMotion, makeEl, observers, timers, runTimers };
}

test('primitive API 노출', () => {
  const { M } = loadMotion();
  assert.equal(typeof M.onEnter, 'function');
  assert.equal(typeof M.revealSequence, 'function');
  assert.equal(typeof M.processProgress, 'function');
});

test('revealSequence: 진입 전엔 아무것도, 진입 후 DOM 순서대로 is-in', () => {
  const { M, makeEl, observers, runTimers } = loadMotion();
  const items = [makeEl(), makeEl(), makeEl()];
  const root = { querySelectorAll: (s) => (s === '.f2r-reveal' ? items : []) };
  M.revealSequence(root, { step: 50 });
  assert.ok(items.every((i) => !i.has('is-in')), '진입 전 이미 등장함');
  observers[0].fireEnter();
  runTimers();
  assert.ok(items.every((i) => i.has('is-in')), '진입 후 전부 등장하지 않음');
});

test('processProgress: 커넥터(is-linked) → 카드(is-active), 지나간 단계 is-done, 끝나면 is-complete', () => {
  const { M, makeEl, observers, runTimers } = loadMotion();
  const steps = [makeEl(), makeEl(), makeEl()];
  const list = { _c: new Set(), classList: { add: (x) => list._c.add(x), contains: (x) => list._c.has(x) },
    querySelectorAll: (s) => (s === '[data-step]' ? steps : []) };
  M.processProgress(list, { start: 0, step: 10, linkLead: 5 });
  observers[0].fireEnter();
  runTimers();
  // 첫 단계는 커넥터 없이 바로 active
  assert.ok(steps[0].has('is-active'));
  // 이후 단계는 is-linked(커넥터) 도 붙는다
  assert.ok(steps[1].has('is-linked') && steps[1].has('is-active'));
  assert.ok(steps[2].has('is-linked') && steps[2].has('is-active'));
  // 지나간 단계는 is-done
  assert.ok(steps[0].has('is-done') && steps[1].has('is-done') && steps[2].has('is-done'));
  // 리스트 완료
  assert.ok(list._c.has('is-complete'));
});

test('reduced-motion: IntersectionObserver 없이 즉시 최종 상태', () => {
  const { M, makeEl, observers } = loadMotion({ reduced: true });
  const items = [makeEl(), makeEl()];
  const steps = [makeEl(), makeEl()];
  const root = { querySelectorAll: () => items };
  const list = { _c: new Set(), classList: { add: (x) => list._c.add(x) }, querySelectorAll: () => steps };
  M.revealSequence(root);
  M.processProgress(list);
  assert.equal(observers.length, 0, 'reduced-motion인데 IO를 만들었다');
  assert.ok(items.every((i) => i.has('is-in')));
  assert.ok(steps.every((s) => s.has('is-active') && s.has('is-done') && s.has('is-linked')));
  assert.ok(list._c.has('is-complete'));
});

test('IntersectionObserver 미지원 환경: 즉시 최종 상태', () => {
  const { M, makeEl, observers } = loadMotion({ hasIO: false });
  const items = [makeEl()];
  const root = { querySelectorAll: () => items };
  M.revealSequence(root);
  assert.equal(observers.length, 0);
  assert.ok(items[0].has('is-in'));
});

test('빈 대상/누락 대상은 조용히 무시', () => {
  const { M } = loadMotion();
  assert.doesNotThrow(() => M.revealSequence(null));
  assert.doesNotThrow(() => M.processProgress(null));
  assert.doesNotThrow(() => M.revealSequence({ querySelectorAll: () => [] }));
});
