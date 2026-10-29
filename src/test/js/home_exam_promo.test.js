const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync(require('node:path').resolve(__dirname, '../../main/resources/static/js/home_exam_promo.js'), 'utf8');

function setup({ reduced = false, rejected = false, absent = false } = {}) {
  const events = () => ({ handlers: {}, addEventListener(name, fn) { this.handlers[name] = fn; } });
  const video = Object.assign(events(), { muted: true, paused: true, plays: 0, error: null,
    play() { this.plays++; if (rejected) return Promise.reject(new Error('blocked')); this.paused = false; return Promise.resolve(); },
    pause() { this.paused = true; } });
  const sound = Object.assign(events(), { hidden: true, setAttribute(k, v) { this[k] = v; } });
  const status = { hidden: true }, fallback = { hidden: true }, button = events();
  const ids = { examPromoVideo: absent ? null : video, examPromoSound: sound, examPromoStatus: status, examPromoFallback: fallback };
  const document = Object.assign(events(), { hidden: false, getElementById(id) { return ids[id]; }, querySelectorAll() { return [button]; } });
  const window = Object.assign(events(), { matchMedia() { return { matches: reduced }; } });
  vm.runInNewContext(code, { window, document });
  return { video, sound, status, fallback, button, document, window };
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
  const { sound, status, fallback } = setup({ rejected: true });
  await Promise.resolve();
  assert.equal(sound.hidden, false);
  assert.equal(fallback.hidden, true);
  assert.match(status.textContent, /재생 버튼/);
});
test('given load failure when autoplay also rejects then failure poster and error notice are preserved', async () => {
  const { video, sound, status, fallback } = setup({ rejected: true });
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
  const { video, button, document, window } = setup();
  button.handlers.click(); assert.equal(video.paused, true);
  video.play(); document.hidden = true; document.handlers.visibilitychange(); assert.equal(video.paused, true);
  video.play(); window.handlers.pagehide(); assert.equal(video.paused, true);
});
