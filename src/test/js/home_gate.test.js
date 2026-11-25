const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { init, isEnabled, prefersStill, prepareVideo, toggleSound } = require('../../main/resources/static/js/home_gate.js');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/landing.html'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/home-entry.css'), 'utf8');

function fakeVideo() {
  const attrs = { 'data-poster': '/video/gate/academy.jpg' };
  const sources = [{ a: { 'data-src': '/video/gate/academy.webm' } }, { a: { 'data-src': '/video/gate/academy.mp4' } }]
    .map(s => ({ ...s, setAttribute(k, v) { this.a[k] = v; }, getAttribute(k) { return this.a[k]; } }));
  return {
    attrs, sources, muted: true, loaded: 0, played: 0, paused: 0,
    getAttribute: k => attrs[k], setAttribute: (k, v) => { attrs[k] = v; },
    querySelectorAll: () => sources,
    load() { this.loaded++; }, play() { this.played++; return Promise.resolve(); }, pause() { this.paused++; },
  };
}

function win({ reduced = false, saveData = false, io = true } = {}) {
  const w = { matchMedia: () => ({ matches: reduced }), navigator: { connection: { saveData } }, observed: [] };
  if (io) w.IntersectionObserver = function (cb) { w.fire = cb; this.observe = v => w.observed.push(v); };
  return w;
}

test('given 대문 섹션 when data-gate-video 유무를 보면 then 있을 때만 켠다', () => {
  // given
  const on = { hasAttribute: k => k === 'data-gate-video' };
  const off = { hasAttribute: () => false };
  // when / then
  assert.equal(isEnabled(on), true);
  assert.equal(isEnabled(off), false);
  assert.equal(isEnabled(null), false);
});

test('given 움직임 줄이기 또는 데이터 절약 when prefersStill then 포스터만(true)', () => {
  // given / when / then
  assert.equal(prefersStill(win({ reduced: true })), true);
  assert.equal(prefersStill(win({ saveData: true })), true);
  assert.equal(prefersStill(win()), false);
});

test('given still이면 when prepareVideo then 포스터만 넣고 영상 주소는 넣지 않는다', () => {
  // given
  const v = fakeVideo();
  // when
  const ready = prepareVideo(v, true);
  // then
  assert.equal(ready, false);
  assert.equal(v.attrs.poster, '/video/gate/academy.jpg');
  assert.equal(v.sources[0].a.src, undefined);
  assert.equal(v.loaded, 0);
});

test('given 보통 환경 when prepareVideo then 포스터와 webm/mp4 주소를 넣고 load한다', () => {
  // given
  const v = fakeVideo();
  // when
  const ready = prepareVideo(v, false);
  // then
  assert.equal(ready, true);
  assert.deepEqual(v.sources.map(s => s.a.src), ['/video/gate/academy.webm', '/video/gate/academy.mp4']);
  assert.equal(v.loaded, 1);
});

test('given 소리 꺼진 전체 영상 when 소리 버튼을 두 번 누르면 then 켜졌다 꺼지고 글자·aria-pressed가 따라간다', () => {
  // given
  const v = { muted: true };
  const b = { attrs: {}, textContent: '소리 켜기', setAttribute(k, val) { this.attrs[k] = val; } };
  // when
  const first = toggleSound(v, b);
  // then
  assert.equal(first, true);
  assert.equal(b.textContent, '소리 끄기');
  assert.equal(b.attrs['aria-pressed'], 'true');
  // when
  toggleSound(v, b);
  // then
  assert.equal(v.muted, true);
  assert.equal(b.textContent, '소리 켜기');
});

function fakeDoc(enabled) {
  const video = fakeVideo();
  const article = { classes: new Set(), classList: { add(c) { article.classes.add(c); } } };
  const slot = {
    hidden: true, parentNode: article,
    hasAttribute: k => k === 'data-gate-media',
    querySelector: () => video,
  };
  const section = {
    hasAttribute: k => enabled && k === 'data-gate-video',
    querySelectorAll: () => [slot],
    querySelector: () => null,
  };
  return { doc: { getElementById: () => section }, slot, video, article };
}

test('given data-gate-video 없음 when init then 영상 칸을 열지 않는다(지금 디자인 그대로)', () => {
  // given
  const { doc, slot, video } = fakeDoc(false);
  // when
  const videos = init(doc, win());
  // then
  assert.deepEqual(videos, []);
  assert.equal(slot.hidden, true);
  assert.equal(video.attrs.poster, undefined);
});

test('given data-gate-video 있음 when init 후 화면에 들어오고 나가면 then 칸을 열고 재생·멈춤한다', () => {
  // given
  const { doc, slot, video, article } = fakeDoc(true);
  const w = win();
  // when
  init(doc, w);
  w.fire([{ isIntersecting: true, target: video }]);
  w.fire([{ isIntersecting: false, target: video }]);
  // then
  assert.equal(slot.hidden, false);
  assert.ok(article.classes.has('has-video'));
  assert.deepEqual(w.observed, [video]);
  assert.equal(video.played, 1);
  assert.equal(video.paused, 1);
});

test('given 움직임 줄이기 when init then 포스터만 보이고 재생 감시를 하지 않는다', () => {
  // given
  const { doc, slot, video } = fakeDoc(true);
  const w = win({ reduced: true });
  // when
  init(doc, w);
  // then
  assert.equal(slot.hidden, false);
  assert.equal(video.attrs.poster, '/video/gate/academy.jpg');
  assert.deepEqual(w.observed, []);
});

test('given 홈 템플릿 when 대문을 보면 then 영상 칸은 꺼진 채(hidden·preload none·data-src) 준비돼 있고 CSS는 reduced-motion을 끈다', () => {
  // given
  const gate = html.slice(html.indexOf('<section id="study-entry"'), html.indexOf('</section>', html.indexOf('<section id="study-entry"')));
  // when / then
  assert.match(gate, /<section[^>]*data-gate-video="promo"/); // 전체 홍보 영상 켜짐(4칸 배경은 아직 꺼둠)
  for (const name of ['promo', 'academy', 'coordi', 'media', 'publishing']) {
    assert.ok(gate.includes(`data-src="/video/gate/${name}.webm"`), name);
    assert.ok(gate.includes(`data-src="/video/gate/${name}.mp4"`), name);
    assert.ok(gate.includes(`data-poster="/video/gate/${name}.jpg"`), name);
  }
  assert.equal((gate.match(/preload="none"/g) || []).length, 5);
  assert.doesNotMatch(gate, /\ssrc="\/video\/gate/);
  assert.ok(gate.includes('data-gate-sound'));
  assert.match(css, /prefers-reduced-motion: reduce\)\s*\{\s*\.f2r-gate::before/);
  assert.ok(html.includes('/js/home_gate.js'));
});

test('given data-gate-video="promo" when init then 전체 영상만 켜고 4칸 배경(media)은 켜지 않는다', () => {
  // given: 전체 영상 슬롯 + 카드 배경 슬롯
  const promoVideo = fakeVideo();
  const mediaVideo = fakeVideo();
  const article = { classList: { add() {} } };
  const promoSlot = { hidden: true, hasAttribute: () => false, querySelector: () => promoVideo };
  const mediaSlot = { hidden: true, parentNode: article, hasAttribute: k => k === 'data-gate-media', querySelector: () => mediaVideo };
  const section = {
    hasAttribute: k => k === 'data-gate-video',
    getAttribute: k => (k === 'data-gate-video' ? 'promo' : null),
    querySelectorAll: () => [promoSlot, mediaSlot],
    querySelector: () => null,
  };
  const doc = { getElementById: () => section };
  // when
  init(doc, win());
  // then: 전체 영상만 열리고, 카드 배경은 닫힌 채
  assert.equal(promoSlot.hidden, false);
  assert.equal(mediaSlot.hidden, true);
  assert.equal(promoVideo.attrs.poster !== undefined, true);
  assert.equal(mediaVideo.attrs.poster, undefined);
});
