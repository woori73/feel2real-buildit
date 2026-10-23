const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// 홈 첫 화면 AI 비서(안내 모드): 요청 → 공개 서비스 안내 규칙과 화면 계약.
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/f2r_home_agent.js'), 'utf8');
const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/landing.html'), 'utf8');

function load() {
  const window = {};
  vm.runInNewContext(JS, { window });
  return window.f2rHomeAgent;
}

test('given 홈 요청 when 의도를 고르면 then 가장 많이 맞은 서비스를 고른다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.detectIntent('내신 예상문제 의뢰하고 싶어요'), 'exam');
  assert.equal(h.detectIntent('영어 문법 공부'), 'english');
  assert.equal(h.detectIntent('모의고사 해설'), 'wiki');
  assert.equal(h.detectIntent('학원 학생 출결 관리'), 'work');
  assert.equal(h.detectIntent('AI AGENT 웹툰 보고 싶어'), 'studio');
  assert.equal(h.detectIntent('학원 홈페이지 만들기'), 'builder');
  assert.equal(h.detectIntent('오늘 날씨'), null);
  assert.equal(h.detectIntent('   '), null);
});

test('given 로그인 전후 when 업무 요청에 답하면 then 로그인 전에는 로그인 안내, 원장·강사는 AI 비서 화면으로 넘긴다', () => {
  // given
  const h = load();
  // when
  const anon = h.replyFor('work', { staff: false });
  const staff = h.replyFor('work', { staff: true });
  // then
  assert.equal(anon.links[0].href, '/?login=required&returnUrl=%2Fdashboard');
  assert.equal(staff.links[0].handoff, true);
});

test('given 제작소 권한 when 답하면 then 권한이 있을 때만 제작소 링크를 준다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.replyFor('studio', { studio: true }).links[0].href, '/ai-agent-studio');
  assert.ok(h.replyFor('studio', { studio: false }).links.every((l) => l.href !== '/ai-agent-studio'));
  assert.equal(h.replyFor(null, {}).choices, true);
  assert.equal(h.replyFor('exam', {}).links[0].href, 'https://open.kakao.com/o/sMf4ifRi');
});

test('given 홈 템플릿 when 읽으면 then AI 비서 첫 화면이 기존 상품 영역보다 앞에 있고 대화·입력창·홀로그램 버튼이 있다', () => {
  // given / when / then
  const ai = HTML.indexOf('id="aiHome"');
  const hero = HTML.indexOf('class="en-hero"');
  assert.ok(ai > 0 && ai < hero);
  assert.match(HTML, /Human Intelligence <span class="si-x">×<\/span> AI/);
  assert.match(HTML, /당신이 느끼고 상상한 것을 현실로\./);
  for (const id of ['homeStage', 'homeChat', 'homeForm', 'homeInput', 'homeMode', 'homeWelcomeAgain']) assert.match(HTML, new RegExp('id="' + id + '"'));
  for (const intent of ['english', 'exam', 'wiki', 'work', 'studio', 'builder']) assert.match(HTML, new RegExp('data-home-intent="' + intent + '"'));
  assert.match(HTML, /안내 모드/);
  // 홈 첫 화면 제목은 하나(h1)
  assert.equal((HTML.match(/<h1[\s>]/g) || []).length, 1);
});
