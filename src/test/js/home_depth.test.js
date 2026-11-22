const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { init, tiltFromPointer } = require('../../main/resources/static/js/home_depth.js');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/landing.html'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/home_depth.css'), 'utf8');

function element(name) {
  const classes = new Set();
  const listeners = {};
  return {
    name,
    style: { props: {}, setProperty(k, v) { this.props[k] = v; } },
    classList: { add: c => classes.add(c), contains: c => classes.has(c) },
    addEventListener(type, fn) { listeners[type] = fn; },
    fire(type, e) { listeners[type](e); },
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 100, height: 50 }),
  };
}

function setup({ reduced = false, observer = true, root = true } = {}) {
  const top = element('homeTop');
  const title = element('h2');
  const logo = element('logo');
  const loc = element('loc');
  let callback = null; const observed = [], unobserved = [];
  const win = {
    matchMedia: () => ({ matches: reduced }),
    ...(observer ? { IntersectionObserver: function (cb) { callback = cb; this.observe = el => observed.push(el); this.unobserve = el => unobserved.push(el); } } : {}),
  };
  const doc = {
    getElementById: id => (id === 'homeTop' && root ? top : null),
    querySelectorAll: sel => (sel.includes('.hl-head h2') ? [title, logo, loc] : [logo, loc]),
  };
  const app = init(doc, win);
  return { app, top, title, logo, loc, observed, unobserved, enter: el => callback([{ isIntersecting: true, target: el }]) };
}

test('given a pointer at the centre, edges and outside of a box when converted then tilt is 0 at centre, ±max at edges and clamped outside', () => {
  // given
  const rect = { left: 0, top: 0, width: 100, height: 50 };
  // when / then
  assert.deepEqual(tiltFromPointer(rect, 50, 25, 12), { rx: 0, ry: 0 });
  assert.deepEqual(tiltFromPointer(rect, 100, 25, 12), { rx: 0, ry: 12 });
  assert.deepEqual(tiltFromPointer(rect, 0, 0, 12), { rx: 12, ry: -12 });
  assert.deepEqual(tiltFromPointer(rect, 500, 500, 12), { rx: -12, ry: 12 });
  assert.deepEqual(tiltFromPointer(rect, 75, 25), { rx: 0, ry: 6 });
});

test('given an empty or missing box when converted then no tilt is applied', () => {
  // given / when / then
  assert.deepEqual(tiltFromPointer({ left: 0, top: 0, width: 0, height: 0 }, 10, 10, 12), { rx: 0, ry: 0 });
  assert.deepEqual(tiltFromPointer(null, 10, 10, 12), { rx: 0, ry: 0 });
});

test('given motion is allowed when the home loads then the root is prepared and titles, logo and map card are observed', () => {
  // given / when
  const s = setup();
  // then
  assert.equal(s.app.active, true);
  assert.equal(s.top.classList.contains('hd-ready'), true);
  assert.deepEqual(s.observed, [s.title, s.logo, s.loc]);
  assert.deepEqual(s.app.tilted, [s.logo, s.loc]);
});

test('given an observed title when it enters the screen then it stands up once', () => {
  // given
  const s = setup();
  // when
  s.enter(s.title);
  // then
  assert.equal(s.title.classList.contains('hd-in'), true);
  assert.deepEqual(s.unobserved, [s.title]);
});

test('given the map card when the pointer moves and leaves then it tilts toward the pointer and returns flat', () => {
  // given
  const s = setup();
  // when
  s.loc.fire('pointermove', { clientX: 100, clientY: 50 });
  const moved = { ...s.loc.style.props };
  s.loc.fire('pointerleave', {});
  // then
  assert.deepEqual(moved, { '--hd-rx': '-12deg', '--hd-ry': '12deg' });
  assert.deepEqual(s.loc.style.props, { '--hd-rx': '0deg', '--hd-ry': '0deg' });
});

test('given reduced motion or no IntersectionObserver when the home loads then nothing is hidden or laid down', () => {
  // given / when
  const reduced = setup({ reduced: true });
  const old = setup({ observer: false });
  // then
  assert.deepEqual(reduced.app, { active: false });
  assert.equal(reduced.top.classList.contains('hd-ready'), false);
  assert.deepEqual(old.app, { active: false });
  assert.equal(old.top.classList.contains('hd-ready'), false);
});

test('given another page without the home root when loaded then the script does nothing', () => {
  // given / when / then
  assert.equal(setup({ root: false }).app, null);
});

test('given the home template when read then depth css/js are loaded and the moving-to location map opens naver map for the same address', () => {
  // given
  const block = html.slice(html.indexOf('class="hd-locs"'), html.indexOf('</div>\n        </div>', html.indexOf('class="hd-locs"')));
  const cards = block.split('<div class="hd-loc ').slice(1);
  const newQ = '%EC%84%9C%EC%9A%B8%20%EA%B0%95%EB%82%A8%EA%B5%AC%20%EC%96%B8%EC%A3%BC%EB%A1%9C173%EA%B8%B8%2012';
  // when / then
  assert.match(html, /\/css\/home_depth\.css\?v=/);
  assert.match(html, /\/js\/home_depth\.js\?v=[^"]+" defer/);
  assert.equal(cards.length, 1);
  assert.match(cards[0], new RegExp(`src="https://maps\\.google\\.com/maps\\?q=${newQ}&amp;z=17&amp;output=embed"`));
  assert.match(cards[0], new RegExp(`href="https://map\\.naver\\.com/p/search/${newQ}" target="_blank" rel="noopener"`));
  // 2026-11-22 감독: "신사동 616-4 4층이 이전할 장소" — 도로명·지번을 함께 적는다
  assert.match(cards[0], /11월 25일 이전할 곳/);
  assert.match(cards[0], /언주로173길 12\(신사동 616-4\), 압구정빌딩 4층/);
  assert.doesNotMatch(html, /이전 위치/);
  // 지도는 그림으로만 쓰고 키보드 초점·스크린리더는 카드 링크가 맡는다
  assert.match(cards[0], /<iframe class="hd-loc-map"[^>]*loading="lazy" tabindex="-1" aria-hidden="true"/);
});

test('given the depth stylesheet when read then motion only runs after the script marks the page and stops for reduced motion', () => {
  // given / when / then
  assert.match(css, /\.hd-ready \.hl-head h2 \{[^}]*rotateX\(58deg\)/);
  assert.match(css, /\.hd-ready \.hl-head h2\.hd-in \{[^}]*rotateX\(0deg\)/);
  assert.doesNotMatch(css, /^\.home-light \.hl-head h2 \{[^}]*opacity/m);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
  // 한 바퀴 회전이 끝난 뒤에는 마우스 기울기가 다시 듣도록 끝 상태를 붙잡지 않는다
  assert.match(css, /hdCoin [^;]*backwards;/);
  // 지도는 누름을 받지 않고 카드 링크가 받는다
  assert.match(css, /\.hd-loc-map \{[^}]*pointer-events: none/);
});
