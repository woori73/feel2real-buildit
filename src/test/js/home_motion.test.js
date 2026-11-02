const test = require('node:test');
const assert = require('node:assert/strict');
const {init, parseCount, formatCount} = require('../../main/resources/static/js/home_motion.js');

function element(text = '') {
  const classes = new Set();
  return { textContent: text, style: { props: {}, setProperty(k, v) { this.props[k] = v; } },
    classList: { add: c => classes.add(c), contains: c => classes.has(c) },
    matches: () => false, querySelector: () => null };
}
function setup({ reduced = false, observer = true } = {}) {
  const root = element();
  const number = element('93.5%');
  const stat = element(); stat.matches = s => s === '.hit-stats li'; stat.querySelector = () => number;
  const section = element();
  const items = [section, stat];
  let callback = null; const observed = [], unobserved = [];
  const frames = [];
  const win = {
    matchMedia: () => ({ matches: reduced }),
    requestAnimationFrame: fn => frames.push(fn),
    ...(observer ? { IntersectionObserver: function (cb) { callback = cb; this.observe = el => observed.push(el); this.unobserve = el => unobserved.push(el); } } : {})
  };
  const doc = { getElementById: id => (id === 'service' ? root : null), querySelectorAll: () => items };
  const app = init(doc, win);
  return { app, root, section, stat, number, observed, unobserved, frames,
    enter: el => callback([{ isIntersecting: true, target: el }]) };
}

test('given count texts when parsed then number, decimals and suffix are kept so the final text is unchanged', () => {
  // given / when / then
  assert.deepEqual(parseCount('93.5%'), { value: 93.5, decimals: 1, suffix: '%' });
  assert.deepEqual(parseCount('92점'), { value: 92, decimals: 0, suffix: '점' });
  assert.deepEqual(parseCount('14/14'), { value: 14, decimals: 0, suffix: '/14' });
  assert.equal(parseCount('적중'), null);
  assert.equal(formatCount(parseCount('93.5%'), 1), '93.5%');
  assert.equal(formatCount(parseCount('14/14'), 0), '0/14');
  assert.equal(formatCount(parseCount('92점'), 2), '92점');
});

test('given the service area when motion is allowed then blocks are prepared, staggered and observed', () => {
  // given / when
  const s = setup();
  // then
  assert.equal(s.app.active, true);
  assert.equal(s.root.classList.contains('motion-ready'), true);
  assert.equal(s.section.classList.contains('mo-reveal'), true);
  assert.equal(s.stat.style.props['--mo-delay'], '90ms');
  assert.deepEqual(s.observed, [s.section, s.stat]);
});

test('given a hit figure when it enters the screen then it is revealed once and counts up to the original text', () => {
  // given
  const s = setup();
  // when
  s.enter(s.stat);
  s.frames.shift()(0);
  const early = s.number.textContent;
  while (s.frames.length) s.frames.shift()(5000);
  // then
  assert.equal(s.stat.classList.contains('is-in'), true);
  assert.deepEqual(s.unobserved, [s.stat]);
  assert.equal(early, '0.0%');
  assert.equal(s.number.textContent, '93.5%');
});

test('given reduced motion or no IntersectionObserver when initialised then nothing is hidden', () => {
  // given / when
  for (const options of [{ reduced: true }, { observer: false }]) {
    const s = setup(options);
    // then
    assert.equal(s.app.active, false);
    assert.equal(s.root.classList.contains('motion-ready'), false);
    assert.equal(s.section.classList.contains('mo-reveal'), false);
  }
});
