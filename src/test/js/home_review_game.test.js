const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/home_review_game.js'), 'utf8');
const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/landing.html'), 'utf8');

function load() {
  const window = {};
  vm.runInNewContext(code, { window });
  return window.f2rHomeReviewGame;
}

function setup({ fullscreen, phone = false, rootFullscreen } = {}) {
  const clicks = {};
  const classes = new Set();
  const node = (id, extra = {}) => Object.assign({ id, hidden: false, children: [],
    addEventListener(name, fn) { clicks[id] = fn; },
    appendChild(child) { this.children.push(child); },
    removeChild(child) { this.children = this.children.filter(c => c !== child); } }, extra);
  const paused = [];
  const media = id => ({ pause() { paused.push(id); } });
  const nodes = {
    homeGame: node('homeGame', { getAttribute: name => name === 'data-game-src' ? '/games/g1-oct-review/v1/' : null,
      classList: { toggle: (c, on) => { if (on) classes.add(c); else classes.delete(c); } },
      ...(rootFullscreen ? { requestFullscreen: rootFullscreen } : {}) }),
    homeGameCover: node('homeGameCover'), homeGameStage: node('homeGameStage', { hidden: true }),
    homeGameTools: node('homeGameTools', { hidden: true }), homeGameStart: node('homeGameStart'),
    homeGameClose: node('homeGameClose'), homeGameFullscreen: node('homeGameFullscreen'),
    examPromoVideo: media('examPromoVideo'), homeAgentTrailer: media('homeAgentTrailer') };
  const opened = [];
  const focus = { game: 0 };
  const listeners = {};
  const doc = { getElementById: id => nodes[id] || null, documentElement: { style: { overflow: '' } }, fullscreenElement: null,
    addEventListener(type, fn) { listeners[type] = fn; },
    exitFullscreen() { this.exited = (this.exited || 0) + 1; this.fullscreenElement = null; return Promise.resolve(); },
    createElement: tag => ({ tag, attrs: {}, setAttribute(k, v) { this.attrs[k] = v; },
      contentWindow: { focus() { focus.game++; } },
      ...(fullscreen ? { requestFullscreen: fullscreen } : {}) }) };
  const win = { open: (...args) => opened.push(args), matchMedia: query => ({ matches: phone && query === '(max-width: 760px)' }) };
  const game = load().init(doc, win);
  return { game, nodes, clicks, paused, opened, classes, doc, focus, listeners };
}

test('given 홈 when 처음 열면 then 게임 파일을 불러오지 않고 시작 버튼만 보인다', () => {
  // given / when
  const { game, nodes } = setup();
  // then
  assert.equal(game.frame(), null);
  assert.equal(nodes.homeGameStage.children.length, 0);
  assert.equal(nodes.homeGameCover.hidden, false);
});

test('given 게임 시작 when 누르면 then 같은 출처 게임 페이지를 한 번만 넣고 다른 영상을 멈춘다', () => {
  // given
  const { clicks, nodes, paused } = setup();
  // when
  clicks.homeGameStart();
  clicks.homeGameStart();
  // then
  const [frame] = nodes.homeGameStage.children;
  assert.equal(nodes.homeGameStage.children.length, 1);
  assert.equal(frame.src, '/games/g1-oct-review/v1/');
  assert.equal(frame.attrs.sandbox, 'allow-scripts allow-same-origin allow-pointer-lock allow-popups');
  assert.doesNotMatch(frame.attrs.sandbox, /allow-top-navigation|allow-forms/);
  assert.equal(nodes.homeGameCover.hidden, true);
  assert.equal(nodes.homeGameTools.hidden, false);
  assert.deepEqual(paused, ['examPromoVideo', 'homeAgentTrailer']);
});

test('given 실행 중인 게임 when 닫으면 then iframe을 지우고 시작 화면으로 돌아간다', () => {
  // given
  const { clicks, nodes, game } = setup();
  clicks.homeGameStart();
  // when
  clicks.homeGameClose();
  // then
  assert.equal(game.frame(), null);
  assert.equal(nodes.homeGameStage.children.length, 0);
  assert.equal(nodes.homeGameCover.hidden, false);
  assert.equal(nodes.homeGameTools.hidden, true);
});

test('given 전체 화면을 못 쓰는 휴대폰 when 전체 화면을 누르면 then 게임 페이지를 새 창으로 연다', async () => {
  // given
  const noApi = setup();
  noApi.clicks.homeGameStart();
  const rejected = setup({ fullscreen: () => Promise.reject(new Error('denied')) });
  rejected.clicks.homeGameStart();
  // when
  noApi.clicks.homeGameFullscreen();
  rejected.clicks.homeGameFullscreen();
  await new Promise(resolve => setImmediate(resolve));
  // then
  assert.deepEqual(noApi.opened, [['/games/g1-oct-review/v1/', '_blank', 'noopener']]);
  assert.deepEqual(rejected.opened, [['/games/g1-oct-review/v1/', '_blank', 'noopener']]);
});

test('given 홈 템플릿 when 읽으면 then 게임 영역·시작 버튼·Nimble 대체 링크가 있다', () => {
  // given / when / then
  assert.match(html, /id="homeGame" th:if="\$\{!#strings\.isEmpty\(homeReviewGamePath\)\}" th:attr="data-game-src=\$\{homeReviewGamePath\}"/);
  for (const id of ['homeGameStart', 'homeGameStage', 'homeGameTools', 'homeGameFullscreen', 'homeGameClose', 'homeReviewGame']) {
    assert.match(html, new RegExp('id="' + id + '"'));
  }
  assert.match(html, /home_review_game\.js/);
  assert.doesNotMatch(html, /<iframe/);
});

test('given 휴대폰 when 게임을 시작하면 then 카드 대신 화면 전체로 열고 브라우저 전체 화면을 요청하며 뒤 페이지 스크롤을 막는다', async () => {
  // given
  let requested = 0;
  const { clicks, classes, doc, game } = setup({ phone: true, rootFullscreen: () => { requested++; return Promise.reject(new Error('iOS')); } });
  // when
  clicks.homeGameStart();
  await new Promise(resolve => setImmediate(resolve));
  // then: 전체 화면이 거절돼도(아이폰) 화면 전체 레이어는 유지된다
  assert.equal(requested, 1);
  assert.ok(classes.has('is-immersive'));
  assert.equal(game.immersive(), true);
  assert.equal(doc.documentElement.style.overflow, 'hidden');
});

test('given 휴대폰 화면 전체 게임 when 닫으면 then 전체 화면을 끄고 레이어·스크롤을 원래대로 돌린다', () => {
  // given
  const { clicks, classes, doc, nodes } = setup({ phone: true, rootFullscreen: () => Promise.resolve() });
  clicks.homeGameStart();
  doc.fullscreenElement = nodes.homeGame;
  // when
  clicks.homeGameClose();
  // then
  assert.equal(doc.exited, 1);
  assert.equal(classes.has('is-immersive'), false);
  assert.equal(doc.documentElement.style.overflow, '');
  assert.equal(nodes.homeGameStage.children.length, 0);
});

test('given 휴대폰 화면 전체 게임 when 전체 화면 버튼을 누르면 then 새 창 없이 브라우저 전체 화면만 켜고 끈다', () => {
  // given
  let requested = 0;
  const { clicks, doc, nodes, opened } = setup({ phone: true, rootFullscreen: () => { requested++; return Promise.resolve(); } });
  clicks.homeGameStart();
  // when
  doc.fullscreenElement = nodes.homeGame;
  clicks.homeGameFullscreen();
  doc.fullscreenElement = null;
  clicks.homeGameFullscreen();
  // then
  assert.equal(doc.exited, 1);
  assert.equal(requested, 2);
  assert.deepEqual(opened, []);
});

test('given PC when 게임을 시작하면 then 카드 안에서 열고 전체 화면을 저절로 요청하지 않는다', () => {
  // given
  let requested = 0;
  const { clicks, classes, doc } = setup({ rootFullscreen: () => { requested++; return Promise.resolve(); } });
  // when
  clicks.homeGameStart();
  // then
  assert.equal(requested, 0);
  assert.equal(classes.has('is-immersive'), false);
  assert.equal(doc.documentElement.style.overflow, '');
});

test('given 휴대폰 게임 when 홈페이지의 전체 화면 버튼을 누르면 then 포커스를 게임으로 되돌려 다음 문제가 준비 상태에 멈추지 않는다', () => {
  // given: 게임은 자기 화면에 포커스가 있을 때만 문제를 진행한다
  const { clicks, focus, listeners, doc, nodes } = setup({ phone: true, rootFullscreen: () => Promise.resolve() });
  clicks.homeGameStart();
  const afterStart = focus.game;
  // when: 켜기, 끄기, 전체 화면 전환이 끝난 뒤
  clicks.homeGameFullscreen();
  doc.fullscreenElement = nodes.homeGame;
  clicks.homeGameFullscreen();
  listeners.fullscreenchange();
  // then
  assert.ok(afterStart >= 1);
  assert.equal(focus.game, afterStart + 3);
});

test('given 게임 시작 페이지 when 읽으면 then 게임 안을 터치하면 포커스를 되찾는 처리가 Unity 로딩보다 먼저 걸린다', () => {
  // given
  const starter = fs.readFileSync(path.resolve(__dirname, '../../main/resources/games/g1-oct-review/v1/game.js'), 'utf8');
  // when
  const reclaim = starter.indexOf("['touchstart', 'pointerdown', 'mousedown'].forEach");
  const loader = starter.indexOf("loader.src = 'Build/WebGLBuild.loader.js'");
  // then
  assert.ok(reclaim > 0 && reclaim < loader);
  assert.match(starter, /if \(document\.hasFocus\(\)\) return;\s*window\.focus\(\);\s*canvas\.focus\(\);/);
  assert.match(starter, /capture: true, passive: true/);
});
