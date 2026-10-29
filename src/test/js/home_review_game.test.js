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

function setup({ fullscreen } = {}) {
  const clicks = {};
  const node = (id, extra = {}) => Object.assign({ id, hidden: false, children: [],
    addEventListener(name, fn) { clicks[id] = fn; },
    appendChild(child) { this.children.push(child); },
    removeChild(child) { this.children = this.children.filter(c => c !== child); } }, extra);
  const paused = [];
  const media = id => ({ pause() { paused.push(id); } });
  const nodes = {
    homeGame: node('homeGame', { getAttribute: name => name === 'data-game-src' ? '/games/g1-oct-review/v1/' : null }),
    homeGameCover: node('homeGameCover'), homeGameStage: node('homeGameStage', { hidden: true }),
    homeGameTools: node('homeGameTools', { hidden: true }), homeGameStart: node('homeGameStart'),
    homeGameClose: node('homeGameClose'), homeGameFullscreen: node('homeGameFullscreen'),
    examPromoVideo: media('examPromoVideo'), homeAgentTrailer: media('homeAgentTrailer') };
  const opened = [];
  const doc = { getElementById: id => nodes[id] || null,
    createElement: tag => ({ tag, attrs: {}, setAttribute(k, v) { this.attrs[k] = v; },
      ...(fullscreen ? { requestFullscreen: fullscreen } : {}) }) };
  const win = { open: (...args) => opened.push(args) };
  const game = load().init(doc, win);
  return { game, nodes, clicks, paused, opened };
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
