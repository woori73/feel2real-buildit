const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync(require('node:path').resolve(__dirname, '../../main/resources/static/js/home_exam_promo.js'), 'utf8');

function setup({ reduced = false, rejected = false, absent = false, observerAvailable = true, saveData = false } = {}) {
  const events = () => ({ handlers: {}, addEventListener(name, fn) {
    const previous = this.handlers[name];
    this.handlers[name] = previous ? () => { previous(); fn(); } : fn;
  } });
  const video = Object.assign(events(), { muted: true, paused: true, plays: 0, error: null,
    play() { this.plays++; if (rejected) return Promise.reject(new Error('blocked')); this.paused = false; return Promise.resolve(); },
    pause() { this.paused = true; } });
  const sound = Object.assign(events(), { hidden: true, setAttribute(k, v) { this[k] = v; } });
  const status = { hidden: true }, fallback = { hidden: true }, button = events();
  const ids = { examPromoVideo: absent ? null : video, examPromoSound: sound, examPromoStatus: status, examPromoFallback: fallback };
  const document = Object.assign(events(), { hidden: false, getElementById(id) { return ids[id]; }, querySelectorAll() { return [button]; } });
  const window = Object.assign(events(), { matchMedia() { return { matches: reduced }; } });
  let intersection;
  let disconnected = false;
  function IntersectionObserver(callback) {
    intersection = callback;
    this.observe = () => {};
    this.disconnect = () => { disconnected = true; };
  }
  if (observerAvailable) window.IntersectionObserver = IntersectionObserver;
  const navigator = { connection: { saveData } };
  vm.runInNewContext(code, { window, document, IntersectionObserver, navigator });
  return { video, sound, status, fallback, button, document, window,
    enter(ratio = 1) { intersection([{ isIntersecting: ratio > 0, intersectionRatio: ratio }]); },
    disconnected() { return disconnected; } };
}

test('given disabled slot when initialized then no media starts', () => {
  assert.equal(setup({ absent: true }).video.plays, 0);
});
test('given reduced motion when initialized then manual sound button can start playback', () => {
  const { video, sound } = setup({ reduced: true });
  assert.equal(video.plays, 0);
  sound.handlers.click();
  assert.equal(video.plays, 1);
  assert.equal(video.muted, false);
  assert.equal(sound['aria-pressed'], 'true');
});
test('given autoplay rejection when promise settles then controls stay available with a manual hint', async () => {
  const { sound, status, fallback, enter } = setup({ rejected: true });
  enter();
  await Promise.resolve();
  assert.equal(sound.hidden, false);
  assert.equal(fallback.hidden, true);
  assert.match(status.textContent, /재생 버튼/);
});
test('given load failure when autoplay also rejects then failure poster and error notice are preserved', async () => {
  const { video, sound, status, fallback, enter } = setup({ rejected: true });
  enter();
  video.error = { code: 4 };
  video.handlers.error();
  await Promise.resolve();
  assert.equal(video.paused, true);
  assert.equal(video.hidden, true);
  assert.equal(sound.hidden, true);
  assert.equal(fallback.hidden, false);
  assert.match(status.textContent, /불러오지 못했습니다/);
});
test('given playing promo when a character is selected or tab hidden then promotional audio stops', () => {
  const { video, button, document, window, enter, disconnected } = setup();
  enter();
  button.handlers.click(); assert.equal(video.paused, true);
  video.play(); document.hidden = true; document.handlers.visibilitychange(); assert.equal(video.paused, true);
  video.play(); window.handlers.pagehide(); assert.equal(video.paused, true);
  assert.equal(disconnected(), true);
});

test('given a below-fold promo when entering the viewport then autoplay starts muted once and leaving pauses', () => {
  const { video, enter } = setup();
  assert.equal(video.plays, 0);
  enter(0); enter(0.25); assert.equal(video.plays, 0);
  enter(0.5); assert.equal(video.plays, 1); assert.equal(video.muted, true);
  enter(0); assert.equal(video.paused, true);
  enter(); assert.equal(video.plays, 1); assert.equal(video.paused, true);
});

test('given reduced motion or a hidden tab when entering viewport then no automatic playback starts', () => {
  const reduced = setup({ reduced: true }); reduced.enter(); assert.equal(reduced.video.plays, 0);
  const hidden = setup(); hidden.document.hidden = true; hidden.enter(); assert.equal(hidden.video.plays, 0);
});

test('given no visibility observer when loaded then native controls remain manual', () => {
  const { video, sound } = setup({ observerAvailable: false });
  assert.equal(video.plays, 0);
  sound.handlers.click(); assert.equal(video.plays, 1);
});

test('given 데이터 절약 모드 when 영상이 화면에 들어오면 then 저절로 받지 않고 소리 버튼으로는 재생된다', () => {
  // given
  const { video, sound, enter } = setup({ saveData: true });
  // when
  enter();
  // then
  assert.equal(video.plays, 0);
  sound.handlers.click(); assert.equal(video.plays, 1);
});
