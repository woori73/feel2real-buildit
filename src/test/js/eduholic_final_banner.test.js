'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/eduholic_final_banner.js'), 'utf8');

function makeDom(hostname) {
  const created = [];
  function node(tag) {
    return {
      tag, id: '', className: '', textContent: '', children: [], attrs: {}, href: '',
      style: { setProperty() {} },
      appendChild(c) { this.children.push(c); return c; },
      setAttribute(k, v) { this.attrs[k] = v; },
      insertAdjacentElement(pos, el) { created.push({ pos, el }); }
    };
  }
  const hero = node('section'); hero.id = 'sec-hero';
  const source = { attrs: {}, setAttribute(k, v) { this.attrs[k] = v; } };
  const video = { attrs: {}, style: { setProperty() {} }, loaded: false,
    getAttribute(k) { return this.attrs[k] || null; }, setAttribute(k, v) { this.attrs[k] = v; },
    querySelector(sel) { return sel === 'source' ? source : null; }, load() { this.loaded = true; } };
  hero.querySelector = (sel) => (sel === 'video.tablet-video' ? video : null);
  const byId = { 'sec-hero': hero };
  const styles = [];
  const logos = [1, 2].map(() => ({ attrs: { src: '/api/academies/x/logo' },
    getAttribute(k) { return this.attrs[k] || null; }, setAttribute(k, v) { this.attrs[k] = v; } }));
  const document = {
    readyState: 'complete',
    head: { appendChild(c) { styles.push(c); } },
    querySelectorAll: (sel) => (sel === '.eh-logo-symbol img' ? logos : []),
    body: {},
    createElement: (t) => node(t),
    createTextNode: (t) => ({ text: t }),
    getElementById: (id) => byId[id] || null,
    addEventListener() {}
  };
  const window = { location: { hostname } };
  return { document, window, created, video, source, styles, logos };
}

test('given eduholic host when script runs then a final-exam banner with three school links goes right after the hero', () => {
  // given
  const { document, window, created } = makeDom('eduholic.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(created.length, 1);
  assert.strictEqual(created[0].pos, 'afterend');
  const banner = created[0].el;
  const title = banner.children.find((c) => c.tag === 'h2');
  assert.strictEqual(title.textContent, '2026 2학기 기말고사 내신 LIVE');
  const list = banner.children.find((c) => c.className === 'efb-schools');
  const row = banner.children.find((c) => c.className === 'efb-row');
  const shorts = row.children.find((c) => c.className === 'efb-shorts');
  const video = shorts.children.find((c) => c.tag === 'video');
  assert.ok(video.src.startsWith('/video/'));
  const tools = row.children.find((c) => c.className === 'efb-tools');
  assert.deepStrictEqual(tools.children.filter((c) => c.tag === 'a').map((a) => a.href), // 문제 풀기·앱
    ['https://feel2real.co.kr/learn', 'https://feel2real.co.kr/naesintest/app']);
  const loc = banner.children.find((c) => c.className === 'efb-loc');
  const locText = loc.children.find((c) => c.className === 'efb-loc-text');
  assert.ok(locText.children.some((c) => c.textContent === '11월 25일 새 주소로 이전'));
  assert.ok(locText.children.some((c) => String(c.textContent).startsWith('서울 강남구 언주로173길 12, 압구정빌딩 4층')));
  assert.deepStrictEqual(locText.children.filter((c) => c.tag === 'a').map((a) => a.href.slice(0, 26)),
    ['tel:02-6324-1453', 'https://map.naver.com/p/se']);
  const frame = loc.children.find((c) => c.tag === 'iframe');
  assert.strictEqual(frame.attrs.loading, 'lazy');
  const more = tools.children.find((c) => c.className === 'efb-more');
  assert.deepStrictEqual(more.children.map((a) => a.href), [
    'https://feel2real.co.kr/admissions/guide',
    'https://feel2real.co.kr/testprep',
    'https://feel2real.co.kr/csat/2027-english'
  ]);
  assert.deepStrictEqual(list.children.map((a) => a.href), [
    'https://feel2real.co.kr/naesintest/school/apgujeong-m2',
    'https://feel2real.co.kr/naesintest/school/singu-m2',
    'https://feel2real.co.kr/naesintest/school/apgujeong-m1'
  ]);
});

test('given another academy host when script runs then nothing is inserted', () => {
  // given
  const { document, window, created } = makeDom('other.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(created.length, 0);
});

test('given eduholic host when script runs then the hero tablet video becomes the F2R 영어 recruit video', () => {
  // given
  const { document, window, video, source } = makeDom('eduholic.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(source.attrs.src, '/video/landing/f2r-class-ad-20261120.mp4');
  assert.strictEqual(video.attrs.poster, '/img/landing/f2r-class-ad-20261120.jpg');
  assert.strictEqual(video.loaded, true);
});

test('given eduholic host when script runs then header and footer logos become the F2R logo', () => {
  // given
  const { document, window, logos } = makeDom('eduholic.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.deepStrictEqual(logos.map((l) => l.attrs.src), ['/img/F2R_logo_pure.svg', '/img/F2R_logo_pure.svg']);
  assert.ok(logos.every((l) => l.attrs.alt === 'F2R 영어 로고'));
});

test('given eduholic host when script runs then the principal photo is hidden, the tablet is 16:9 and the duplicate LIVE section is hidden', () => {
  // given
  const { document, window, styles, video } = makeDom('eduholic.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  const css = styles.map((s) => s.textContent).join('\n');
  assert.match(css, /#sec-hero \.tablet-photo,#sec-hero \.tablet-cap\{display:none!important\}/);
  assert.match(css, /#sec-hero \.tablet-screen\{aspect-ratio:16\/9!important/);
  assert.match(css, /#sec-featured\{display:none!important\}/);
  assert.strictEqual(video.loop, true, '사진으로 돌아가지 않고 영상만 반복');
});

test('given another academy host when script runs then logos and sections are untouched', () => {
  // given
  const { document, window, styles, logos } = makeDom('other.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(styles.length, 0);
  assert.ok(logos.every((l) => l.attrs.src === '/api/academies/x/logo'));
});
