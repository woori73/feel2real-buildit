const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { init, tiltFromPointer, buildLogo3d, mountMap, projectPoint, swayAngles, nodeDepth, snapPlacement } = require('../../main/resources/static/js/home_depth.js');

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
    querySelector: () => null,
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

test('given the home template when read then depth css/js are loaded and the academy location map has moved to the academy homepage', () => {
  // given / when / then
  assert.match(html, /\/css\/home_depth\.css\?v=/);
  assert.match(html, /\/js\/home_depth\.js\?v=[^"]+" defer/);
  // 2026-11-24 대표 지시: 교습소 이전 줄·주소·지도는 원장 대상 코디 칸에서 빼고 교습소 홈페이지(eduholic)로 옮겼다.
  assert.doesNotMatch(html, /class="hd-locs"/);
  assert.doesNotMatch(html, /새 주소로 이전/);
  // 첫 화면 HTML에는 iframe을 두지 않는다(복습 게임 계약과 같음).
  assert.doesNotMatch(html, /<iframe/);
});

test('given the section titles when styled then the letters stay crisp without stacked shadows, color split or tilt', () => {
  // given / when: 홈 깊이 스타일시트(css)
  // then: 대표 지적(2026-11-22) — 글자에 두께 그림자·색 어긋남·눕히기를 주지 않는다
  assert.doesNotMatch(css, /^[^{\n]*\.hl-head h2[^{\n]*\{[^}]*text-shadow/m);
  assert.doesNotMatch(css, /hdHolo/);
  assert.doesNotMatch(css, /^[^{\n]*\.hl-head h2[^{\n]*\{[^}]*rotateX/m);
  // 밑줄 위 빛 흐름은 글자가 아니라 밑줄(::after)에만 반복된다
  assert.match(css, /\.hd-ready \.hl-head h2\.hd-in::after \{[^}]*hdFlow [^;]*infinite/);
});

test('given the depth stylesheet when read then motion only runs after the script marks the page and stops for reduced motion', () => {
  // given / when / then
  // 구역 제목: 단어가 차례로 떠오르는 모션그래픽은 스크립트가 .hd-ready를 붙인 뒤에만 숨기고 움직인다
  assert.match(css, /\.hd-ready \.hl-head h2 \.hw-w > span \{[^}]*opacity: 0/);
  assert.match(css, /\.hd-ready \.hl-head h2\.hd-in \.hw-w > span \{[^}]*animation: hdWord/);
  assert.doesNotMatch(css, /^\.home-light \.hl-head h2 \{[^}]*opacity/m);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
  // 한 바퀴 회전이 끝난 뒤에는 마우스 기울기가 다시 듣도록 끝 상태를 붙잡지 않는다
  assert.match(css, /hdCoin [^;]*backwards,/);
  // 지도는 누름을 받지 않고 카드 링크가 받는다
  assert.match(css, /\.hd-loc-map iframe \{[^}]*pointer-events: none/);
});

function fakeNode(tag) {
  return { tag, className: '', attrs: {}, children: [], parentNode: null,
    style: { props: {}, setProperty(k, v) { this.props[k] = v; } },
    setAttribute(k, v) { this.attrs[k] = v; },
    appendChild(c) { if (c.parentNode) c.parentNode.children = c.parentNode.children.filter(x => x !== c); c.parentNode = this; this.children.push(c); },
    insertBefore(c, ref) { c.parentNode = this; this.children.splice(this.children.indexOf(ref), 0, c); },
    cloneNode() { const n = fakeNode(this.tag); n.attrs = { ...this.attrs }; return n; } };
}

test('given the big coordinator logo when the 3d logo is built then layers are stacked behind it and the original stays in front with its alt text', () => {
  // given
  const title = fakeNode('h2');
  const img = fakeNode('img'); img.className = 'hw-coord-logo'; img.attrs.alt = 'F2R';
  title.appendChild(img);
  const doc = { querySelector: sel => (sel === '.hw-coord-logo' ? img : null), createElement: fakeNode };
  // when
  const wrap = buildLogo3d(doc, 4);
  // then
  const spin = wrap.children[0];
  assert.equal(title.children[0], wrap);
  assert.equal(wrap.className, 'hd-logo3d');
  assert.equal(spin.children.length, 4);
  assert.deepEqual(spin.children.map(c => c.style.props['--hd-z']), [-3, -2, -1, 0]);
  assert.equal(spin.children[3], img);
  assert.equal(img.attrs.alt, 'F2R');
  spin.children.slice(0, 3).forEach(c => { assert.equal(c.attrs['aria-hidden'], 'true'); assert.equal(c.attrs.alt, ''); assert.match(c.className, /hd-logo-layer/); });
});

test('given a page without the big logo when the 3d logo is built then nothing happens', () => {
  // given / when / then
  assert.equal(buildLogo3d({ querySelector: () => null }), null);
  assert.equal(buildLogo3d({}), null);
});

test('given a map point when projected without rotation then the centre stays put, nearer points grow and farther points shrink', () => {
  // given / when
  const centre = projectPoint(0.5, 0.5, 0, 0, 0);
  const near = projectPoint(0.5, 0.5, -0.3, 0, 0);
  const far = projectPoint(0.5, 0.5, 0.3, 0, 0);
  const edge = projectPoint(1, 0.5, 0, 0, 0, 2.4, 0.82);
  // then
  assert.deepEqual({ x: centre.x, y: centre.y, s: centre.s }, { x: 0.5, y: 0.5, s: 1 });
  assert.ok(near.s > 1 && far.s < 1);
  assert.ok(Math.abs(edge.x - 0.91) < 1e-9);
});

test('given a point on the right when the map turns about the vertical axis then it moves toward the centre and changes depth', () => {
  // given / when
  const flat = projectPoint(0.9, 0.5, 0, 0, 0);
  const turned = projectPoint(0.9, 0.5, 0, 0.5, 0);
  // then
  assert.ok(turned.x < flat.x && turned.x > 0.5);
  assert.notEqual(turned.depth, 0);
});

test('given every map point and the widest sway when projected then all points stay inside the map box', () => {
  // given
  const angles = [swayAngles(0), swayAngles(6.283), swayAngles(100)].concat([{ ay: 0.42, ax: 0.26 }, { ay: -0.42, ax: 0.14 }]);
  // when / then
  angles.forEach(a => {
    assert.ok(Math.abs(a.ay) <= 0.42 + 1e-9 && a.ax >= 0.14 - 1e-9 && a.ax <= 0.26 + 1e-9);
    [[0.05, 0.05], [0.95, 0.95], [0.08, 0.94], [0.92, 0.05]].forEach(([x, y]) => [-0.32, 0.32].forEach(z => {
      const p = projectPoint(x, y, z, a.ay, a.ax);
      assert.ok(p.x > 0 && p.x < 1 && p.y > 0 && p.y < 1, `${x},${y},${z} → ${p.x},${p.y}`);
    }));
  });
});

test('given hubs and group points when depth is assigned then hubs sit near the middle, groups are split front and back, and the value is repeatable', () => {
  // given / when
  const hub = nodeDepth(0, { hub: 1 });
  const g0 = nodeDepth(7, { p: 0 });
  const g1 = nodeDepth(16, { p: 1 });
  // then
  assert.equal(hub, 0);
  assert.ok(g0 < 0 && g1 > 0);
  assert.equal(nodeDepth(7, { p: 0 }), g0);
  assert.ok(Math.abs(g0) <= 0.32 && Math.abs(g1) <= 0.32);
});

test('given a map point in a sized map box when placed then left/top snap to whole pixels and scale to two decimals', () => {
  // given: 400×300 칸 안의 점(소수점 자리)
  const point = { x: 0.50137, y: 0.24988, s: 1.03649 };
  // when
  const placed = snapPlacement(point, 400, 300);
  // then: 200.548px → 201px, 74.964px → 75px, 1.03649 → 1.04
  assert.deepEqual(placed, { left: '201px', top: '75px', scale: '1.04' });
});

test('given the map point moves by less than half a pixel when placed again then its box stays exactly the same', () => {
  // given: 천천히 도는 동안 한 프레임 사이의 작은 움직임
  const before = snapPlacement({ x: 0.5, y: 0.5, s: 1.0 }, 400, 300);
  // when
  const after = snapPlacement({ x: 0.5004, y: 0.5009, s: 1.004 }, 400, 300);
  // then: 자리·크기가 그대로라 글자가 흔들리지 않고 누를 수 있다
  assert.deepEqual(after, before);
});

test('given an unsized map box or a missing point when placed then it falls back to percent and scale 1', () => {
  // given / when
  const unsized = snapPlacement({ x: 0.25, y: 0.75, s: 0.9 }, 0, 0);
  const missing = snapPlacement(undefined, 400, 300);
  // then
  assert.deepEqual(unsized, { left: '25%', top: '75%', scale: '0.90' });
  assert.deepEqual(missing, { left: '0px', top: '0px', scale: '1.00' });
});

test('given the home template when the main area is read then every div is closed exactly once so the hero keeps its two columns', () => {
  // given
  const main = html.slice(html.indexOf('<main'), html.indexOf('</main>'));
  const hero = html.slice(html.indexOf('<div class="hw-coord-hero'), html.indexOf('</section>', html.indexOf('<div class="hw-coord-hero')));
  // when / then
  assert.equal((main.match(/<div\b/g) || []).length, (main.match(/<\/div>/g) || []).length);
  assert.ok(hero.indexOf('id="service-map"') > 0);
  assert.equal((hero.match(/<div\b/g) || []).length, (hero.match(/<\/div>/g) || []).length);
});

test('given a map holder when it is mounted then one hidden, non-focusable map frame is added and a second call adds nothing', () => {
  // given
  const holder = fakeNode('span'); holder.attrs['data-map-src'] = 'https://maps.google.com/maps?q=x&output=embed';
  holder.getAttribute = k => holder.attrs[k]; Object.defineProperty(holder, 'firstChild', { get() { return holder.children[0] || null; } });
  const doc = { createElement: fakeNode };
  // when
  const frame = mountMap(doc, holder);
  const again = mountMap(doc, holder);
  // then
  assert.equal(holder.children.length, 1);
  assert.equal(frame.tag, 'iframe');
  assert.equal(frame.attrs.src, 'https://maps.google.com/maps?q=x&output=embed');
  assert.equal(frame.attrs.tabindex, '-1');
  assert.equal(frame.attrs['aria-hidden'], 'true');
  assert.equal(again, null);
});

test('given a holder without a map address when mounted then nothing is added', () => {
  // given
  const holder = fakeNode('span'); holder.getAttribute = () => null;
  // when / then
  assert.equal(mountMap({ createElement: fakeNode }, holder), null);
  assert.equal(holder.children.length, 0);
});
