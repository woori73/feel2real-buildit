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

test('given 제작협업실 요청 when 답하면 then 누구에게나 제작협업실 링크를 주고 권한은 서버가 확인한다고 안내한다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.replyFor('studio', { studio: true }).links[0].href, '/ai-agent-studio');
  assert.equal(h.replyFor('studio', { studio: false }).links[0].href, '/ai-agent-studio');
  assert.match(h.replyFor('studio', { studio: false }).text, /관리자·운영 계정으로 로그인/);
  assert.equal(h.detectIntent('제작협업실 들어가고 싶어'), 'studio');
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

test('given 캐릭터 영상 목록 when 대화 상태가 바뀌면 then 생각 중에만 think.mp4, 없으면 idle로 대신한다', () => {
  // given
  const h = load();
  const all = ['idle.mp4', 'think.mp4', 'speak.mp4', 'poster.jpg'];
  // when / then
  assert.equal(h.clipFor('thinking', all), 'think');
  assert.equal(h.clipFor('idle', all), 'idle');
  assert.equal(h.clipFor('listening', all), 'idle');
  assert.equal(h.clipFor('speaking', all), 'idle'); // 홈은 립싱크가 없어 speak.mp4를 쓰지 않는다
  assert.equal(h.clipFor('thinking', ['idle.mp4']), 'idle');
  assert.equal(h.clipFor('thinking', ['think.mp4']), 'think');
  assert.equal(h.clipFor('idle', ['think.mp4']), null);
  assert.equal(h.clipFor('idle', []), null);
});

test('given 세븐 인사 영상 when 인사를 재생하면 then speak.mp4를 쓰고 일반 안내에는 쓰지 않는다', () => {
  // given
  const h = load();
  const files = ['idle.mp4', 'speak.mp4'];
  // when / then
  assert.equal(h.clipFor('greeting', files), 'speak');
  assert.equal(h.clipFor('speaking', files), 'idle');
  assert.equal(h.clipFor('greeting', ['idle.mp4']), 'idle');
  assert.equal(h.clipFor('greeting', []), null);
});

test('given 캐릭터·자료 when 인사 대사를 고르면 then 세븐이고 speak.mp4가 있을 때만 대사를 돌려준다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.greetingFor('seven', ['idle.mp4', 'speak.mp4']), '세븐입니다. Feel to Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?');
  assert.equal(h.greetingFor('seven', ['idle.mp4']), null);
  assert.equal(h.greetingFor('suni', ['idle.mp4', 'speak.mp4']), null);
  assert.equal(h.greetingFor('seven', null), null);
});

test('given 홈 화면 when 인사 버튼을 그리면 then 처음에는 숨기고 소리는 버튼을 누른 뒤에만 재생한다', () => {
  // given / when
  const btn = HTML.match(/<button[^>]*id="homeGreet"[^>]*>/);
  // then
  assert.ok(btn, '인사 듣기 버튼이 있어야 한다');
  assert.match(btn[0], /hidden/);
  assert.match(HTML, /<video id="homeVideo" muted loop playsinline/);
  assert.match(JS, /greet\.addEventListener\('click', playGreeting\)/);
});

test('given 키보드로 보이는 영역이 줄었을 때 when 입력줄 위치를 비교하면 then 가려진 만큼만 올리고 보이면 그대로 둔다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.composerScrollDelta(700, 0, 420, 12), 292);
  assert.equal(h.composerScrollDelta(400, 0, 420, 12), 0);
  assert.equal(h.composerScrollDelta(500, 100, 420, 0), 0);
  assert.equal(h.composerScrollDelta(530.2, 100, 420, 0), 11);
});

test('given 모바일 입력줄 CSS when 읽으면 then 열이 줄어들 수 있고 보내기 버튼은 고정 너비, 안내 줄은 줄바꿈된다', () => {
  // given
  const CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/f2r_home_agent.css'), 'utf8');
  // when / then
  assert.match(CSS, /\.si-home-console \{[^}]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(CSS, /\.si-home-talk \{[^}]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(CSS, /\.si-home-composer input \{[^}]*min-width: 0/);
  assert.match(CSS, /\.si-home-composer button \{[^}]*flex: 0 0 auto/);
  assert.match(CSS, /\.si-home-mode \{[^}]*flex-wrap: wrap/);
});
