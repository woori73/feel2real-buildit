const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '../../main/resources/templates');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('landing: /wiki 는 모의고사위키로 표기하고, 내신위키 표기는 /wiki 가 아닌 내신위키(/problem-generator)로만 연결한다', () => {
  // given
  const html = read('public/landing.html') + read('public/exam.html');
  // when
  const anchors = [...html.matchAll(/<a\b[^>]*>[^<]*<\/a>/g)].map((m) => m[0]);
  // then: 옛 표기(/wiki → 내신위키)는 없다
  assert.ok(html.includes('모의고사위키'));
  assert.ok(!anchors.some((a) => /href="\/wiki"/.test(a) && a.includes('내신위키')));
  // 내신위키 라벨은 모두 실제 내신위키 경로로 간다
  const naesin = anchors.filter((a) => a.includes('내신위키'));
  assert.ok(naesin.length >= 3, 'PC 메뉴·모바일 메뉴·칩에 내신위키가 있어야 한다');
  naesin.forEach((a) => assert.match(a, /href="\/problem-generator"/));
});

test('landing: PC 상단 메뉴·모바일 메뉴·서비스 칩에 내신위키가 있고 모의고사위키와 함께 유지된다', () => {
  // given
  const html = read('public/landing.html');
  const nav = html.slice(html.indexOf('<nav class="bi-nav"'), html.indexOf('id="biMenu"'));
  const menu = html.slice(html.indexOf('id="biMenu"'), html.indexOf('id="homeFirst"'));
  const chips = html.slice(html.indexOf('class="svc-chips"'), html.indexOf('</nav>', html.indexOf('class="svc-chips"')));
  // then
  assert.ok(nav.includes('<a href="/problem-generator" class="btn outline nav-cta">내신위키</a>'));
  assert.ok(nav.includes('<a href="/wiki" class="btn outline nav-cta">모의고사위키</a>'));
  assert.ok(menu.includes('<a href="/problem-generator">내신위키</a>'));
  assert.ok(menu.includes('<a href="/wiki">모의고사위키</a>'));
  assert.ok(chips.includes('href="/problem-generator" aria-label="내신위키"'));
});

test('home(/home): 상단 메뉴에도 내신위키가 있고 모의고사 Wiki 링크는 그대로다', () => {
  // given
  const html = read('public/home.html');
  // then
  assert.ok(html.includes('<a href="/problem-generator">내신위키</a>'));
  assert.ok(html.includes('<a href="/wiki">모의고사 Wiki</a>'));
});

test('landing: Agent 히어로가 홈페이지 입력 섹션보다 먼저 나온다', () => {
  // given
  const html = read('public/landing.html');
  // when
  const hero = html.indexOf('agent-hero');
  const first = html.indexOf('home-first');
  // then
  assert.ok(hero > 0 && hero < first);
  assert.ok(html.includes('디지털영어학원 Agent'));
});

test('landing: 기출분석 카드는 /exam-analysis로 연결하고 위키로 보내지 않는다', () => {
  // given
  const html = read('public/landing.html');
  // when
  const tag = html.match(/<a[^>]*data-svc-tile="problem"[^>]*>/)[0];
  // then
  assert.match(tag, /href="\/exam-analysis"/);
  assert.ok(!/problem-generator/.test(tag));
});

test('landing: 문의 모달·푸터 이메일은 BuildIt 공식 이메일을 쓴다', () => {
  // given
  const landing = read('public/landing.html');
  const footer = read('fragments/_public_footer.html');
  // when / then
  assert.ok(landing.includes("'woorinaesin@naver.com'"));
  assert.ok(footer.includes('woorinaesin@naver.com'));
});

test('exam_insight 화면: 안내 문구와 두 옵션이 있다', () => {
  // given
  const html = read('public/exam_insight.html');
  // when / then
  assert.ok(html.includes('최근 학교 기출문제와 이번 시험범위 자료를 올려주세요.'));
  assert.ok(html.includes('분석 자료를 다운로드하여 구독 중인 ChatGPT, Claude 등에서 직접 문제를 제작하세요.'));
  assert.ok(html.includes('문제 제작 의뢰하기'));
  assert.ok(html.includes('20,000원'));
  assert.ok(html.includes('40문항'));
});
