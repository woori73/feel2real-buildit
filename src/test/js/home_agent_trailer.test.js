const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync(require('node:path').resolve(__dirname, '../../main/resources/static/js/home_agent_trailer.js'), 'utf8');

function setup(absent = false) {
  const events = () => ({ handlers: {}, addEventListener(name, fn) {
    const previous = this.handlers[name];
    this.handlers[name] = previous ? () => { previous(); fn(); } : fn;
  } });
  const media = () => Object.assign(events(), { paused: true,
    play() { this.paused = false; return Promise.resolve(); }, pause() { this.paused = true; } });
  const trailer = media(), promo = media(), button = events(), status = { hidden: true };
  const playButton = events();
  const document = Object.assign(events(), { hidden: false,
    getElementById(id) { return { homeAgentTrailer: absent ? null : trailer, examPromoVideo: promo, homeAgentTrailerStatus: status, homeAgentTrailerPlay: playButton }[id]; },
    querySelectorAll() { return [button]; } });
  let visibility, disconnected = false;
  function IntersectionObserver(callback) {
    visibility = callback;
    this.observe = () => {};
    this.disconnect = () => { disconnected = true; };
  }
  const window = Object.assign(events(), { IntersectionObserver });
  vm.runInNewContext(code, { document, window, IntersectionObserver });
  return { trailer, promo, button, playButton, status, document, window,
    leave() { visibility([{ isIntersecting: false }]); }, disconnected: () => disconnected };
}

test('given no approved trailer when initialized then no media handlers are registered', () => {
  const state = setup(true);
  assert.deepEqual(state.promo.handlers, {});
});
test('given two homepage videos when either starts then the other is paused', () => {
  const { trailer, promo } = setup();
  assert.equal(trailer.paused, true);
  promo.paused = false; trailer.handlers.play(); assert.equal(promo.paused, true);
  trailer.paused = false; promo.handlers.play(); assert.equal(trailer.paused, true);
});
test('given manual controls when clicked twice then trailer plays and pauses without autoplay', () => {
  const { trailer, playButton } = setup();
  assert.equal(trailer.paused, true);
  playButton.handlers.click(); assert.equal(trailer.paused, false);
  playButton.handlers.click(); assert.equal(trailer.paused, true);
});
test('given a failed trailer when media error occurs then stop playback and show an honest banner fallback notice', () => {
  const { trailer, status } = setup();
  trailer.paused = false; trailer.handlers.error();
  assert.equal(trailer.paused, true); assert.equal(trailer.hidden, true);
  assert.equal(status.hidden, false); assert.match(status.textContent, /대표 이미지/);
});
test('given trailer playback when offscreen or character selected or tab leaves then playback stops and resources clean up', () => {
  const { trailer, button, document, window, leave, disconnected } = setup();
  trailer.paused = false; leave(); assert.equal(trailer.paused, true);
  trailer.paused = false; button.handlers.click(); assert.equal(trailer.paused, true);
  trailer.paused = false; document.hidden = true; document.handlers.visibilitychange(); assert.equal(trailer.paused, true);
  trailer.paused = false; window.handlers.pagehide(); assert.equal(trailer.paused, true);
  assert.equal(disconnected(), true);
});
