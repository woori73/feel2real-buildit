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

test('given 홈 템플릿 when 읽으면 then AI 비서 첫 화면이 상품 영역보다 앞에 있고 20,000원·3주·6회·240문항 안내와 개별 신청 메뉴가 있다', () => {
  // given / when / then
  const ai = HTML.indexOf('id="aiHome"');
  const hero = HTML.indexOf('class="en-hero"');
  assert.ok(ai > 0 && ai < hero);
  // 최종 적용 기준: 기말고사 시험기간 이용권 20,000원·3주·주 2회·총 6회·240문항(회당 40문항)
  assert.match(HTML, /시험기간 이용권 20,000원/);
  assert.match(HTML, /3주 · 주 2회 · 총 6회 · 240문항/);
  assert.match(HTML, /1회 20,000원\(40문항\)/);
  // Human Intelligence 문구는 삭제됨
  assert.doesNotMatch(HTML, /Human Intelligence/);
  // 간소화된 홀로그램 메뉴: 개별 신청 타일
  assert.match(HTML, /개별 신청/);
  assert.match(HTML, /si-holo-tile/);
  for (const id of ['homeStage', 'homeChat', 'homeForm', 'homeInput', 'homeMode', 'homeWelcomeAgain']) assert.match(HTML, new RegExp('id="' + id + '"'));
  // 승인된 신청 중심 타일은 신청 안내로 연결하고, 다른 서비스의 공개 경로는 메뉴에 유지한다.
  assert.equal((HTML.match(/class="si-holo-tile" data-home-intent="exam"/g) || []).length, 6);
  for (const href of ['/grammar-items', '/wiki', '/ai-agent-studio', '/builder']) assert.ok(HTML.includes('href="' + href + '"'));
  assert.match(HTML, /안내 모드/);
  // 결제는 별도 안내 후 진행 (미완성 결제 기능을 결제 가능한 것처럼 표시하지 않음)
  assert.match(HTML, /결제는 별도 안내 후 진행됩니다/);
  // 신청 접수는 카톡·이메일
  assert.match(HTML, /카톡·이메일로 접수합니다/);
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

test('given 캐릭터·자료 when 인사 대사를 고르면 then 세븐·서수니 각자의 대사를 speak.mp4가 있을 때만 돌려준다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.greetingFor('seven', ['idle.mp4', 'speak.mp4']), '세븐입니다. Feel to Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?');
  assert.equal(h.greetingFor('seven', ['idle.mp4']), null);
  assert.equal(h.greetingFor('suni', ['idle.mp4', 'speak.mp4']), '서수니입니다. Feel to Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?');
  assert.equal(h.greetingFor('suni', ['idle.mp4']), null);
  assert.equal(h.greetingFor('toString', ['speak.mp4']), null);
  assert.equal(h.greetingFor('seven', null), null);
});

test('given 홈 화면 when 캐릭터를 고르면 then 인사 버튼 없이 인사 영상을 소리와 함께 바로 재생한다', () => {
  // given / when
  const pick = JS.slice(JS.indexOf("querySelectorAll('button[data-home-agent]').forEach(function (b) {\n      b.addEventListener"), JS.indexOf("video.addEventListener('ended'"));
  // then
  assert.doesNotMatch(HTML, /homeGreet/);
  assert.match(HTML, /<video class="is-on" id="homeVideo" muted loop playsinline/);
  assert.match(pick, /if \(greetingFor\(ui\.agent, mediaFiles\(\)\)\) playGreeting\(\);/);
});

test('given 홈 화면 when 영상을 바꾸면 then 대시보드와 같은 전환 모듈로 두 영상 요소를 겹쳐 바꾼다', () => {
  // given / when
  const alt = HTML.match(/<video[^>]*id="homeVideoAlt"[^>]*>/);
  const order = HTML.indexOf('f2r_agent_clip_switch.js') >= 0 && HTML.indexOf('f2r_agent_clip_switch.js') < HTML.indexOf('f2r_home_agent.js');
  // then
  assert.ok(alt, '두 번째 영상 요소가 있어야 한다');
  assert.match(alt[0], /muted loop playsinline/);
  assert.ok(order, '전환 모듈을 홈 스크립트보다 먼저 불러야 한다');
  assert.match(JS, /sw\.show\(pair, ui\.sw, src,/);
  assert.match(JS, /restart: talking \|\| prev === 'speak'/);
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

test('given 비서 자료 파일 목록 when 선택 버튼 사진을 고르면 then front.webp, 없으면 poster.jpg, 둘 다 없으면 이름만 쓴다', () => {
  // given
  const h = load();
  // when / then
  assert.equal(h.portraitFile(['idle.mp4', 'front.webp', 'poster.jpg']), 'front.webp');
  assert.equal(h.portraitFile(['idle.mp4', 'poster.jpg']), 'poster.jpg');
  assert.equal(h.portraitFile(['idle.mp4']), null);
  assert.equal(h.portraitFile(null), null);
});

test('given 홈 상태창 when 대시보드와 비교하면 then 비서 선택은 사진+이름, 말하는 중 색과 2D 대체 화면 규칙이 같다', () => {
  // given
  const CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/f2r_home_agent.css'), 'utf8');
  // when
  const seven = HTML.match(/<button[^>]*data-home-agent="seven"[^>]*>(.*?)<\/button>/);
  // then
  assert.match(seven[0], /class="[^"]*\bno-portrait\b[^"]*"/);
  assert.match(seven[1], /<img alt="세븐">/);
  assert.match(seven[1], /<strong>세븐<\/strong>/);
  assert.match(HTML, /class="command" id="homeConsole" data-state="idle"/);
  assert.match(CSS, /\.si-home-stage\[data-state="greeting"\] \{ --si-state: var\(--si-violet\); \}/);
  assert.match(CSS, /\.si-home-pick button\.no-portrait img \{ display: none; \}/);
  assert.match(JS, /\|\| !stageHasFrame\(\)\) \$\('homeStage'\)\.dataset\.media = 'missing'/);
});

test('given 홈과 대시보드 CSS when 홀로그램 주사선을 비교하면 then 공통 디자인 값(3px 간격·진하기 0.10)이 같고 영상이 보일 때만 그린다', () => {
  // given
  const home = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/f2r_home_agent.css'), 'utf8');
  const dash = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/f2r_agent_dashboard.css'), 'utf8');
  // when / then
  for (const css of [home, dash]) {
    assert.match(css, /--holo-scan-gap: 3px; --holo-scan-alpha: \.10;/);
    assert.match(css, /\[data-media="ready"\] \.(si-home-hud|agent-hud) \{ background: repeating-linear-gradient\(to bottom, rgba\(0,0,0,var\(--holo-scan-alpha\)\) 0 1px, transparent 1px var\(--holo-scan-gap\)\); \}/);
  }
});
