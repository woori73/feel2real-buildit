'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const read = (p) => fs.readFileSync(path.resolve(__dirname, '../../main/resources', p), 'utf8');
const home = read('templates/newstart/index.html');
const consult = read('templates/newstart/consultations.html');
const css = read('static/css/newstart.css');
const sharedCss = read('static/css/account_menu.css');

// 2026-10-02 결함: 홈 헤더에는 .newstart-brand가 없어 topbar가 유일한 자식이고 프로필 아이콘이
// 좌측(x=26)에 붙는다. 공용 account_menu.css의 .account-menu-panel{right:0}은 패널을 아이콘
// 왼쪽으로 154~168px 펼치므로 360px에서 좌변 -84px, 1300px에서 -24px까지 나가고
// html·body{overflow-x:clip}에 잘렸다. 실측 근거를 계약으로 고정한다.
const mobileRule = css.match(/@media \(max-width:\s*768px\)\s*\{([\s\S]*?)\n\}/);

test('홈은 topbar가 헤더의 유일한 자식이라 프로필 아이콘이 좌측에 있다(앵커 반전 전제)', () => {
  assert.match(home, /<header class="newstart-top">\s*<nav class="newstart-topbar"[^>]*>\s*<th:block th:replace="~\{fragments\/_newstart_auth :: authControls\}"/);
  assert.doesNotMatch(home, /class="newstart-brand"/);
  // 브랜드가 있는 페이지는 topbar가 좌측에 붙지 않으므로 잘림이 없다 — 선택자 전제가 유지되는지 확인.
  assert.match(consult, /class="newstart-brand"/);
});

test('모바일(≤768) 프로필 메뉴는 헤더 폭 기준으로 중앙 정렬하고 좌우 16px 여유를 둔다', () => {
  assert.ok(mobileRule, '@media (max-width: 768px) 규칙이 newstart.css에 있어야 한다');
  const body = mobileRule[1];
  assert.match(body, /\.newstart-page \.newstart-top\s*\{\s*position:\s*relative/);
  assert.match(body, /\.newstart-page \.newstart-topbar \.account-menu\s*\{\s*position:\s*static/);
  assert.match(body, /\.newstart-page \.newstart-topbar \.account-menu-panel\s*\{[\s\S]*?top:\s*calc\(100% \+ 8px\);[\s\S]*?left:\s*50%;[\s\S]*?right:\s*auto;[\s\S]*?transform:\s*translateX\(-50%\);[\s\S]*?max-width:\s*calc\(100% - 32px\)/);
});

test('769~1399px 홈 메뉴는 아이콘 오른쪽으로 열리고 1400px 이상 데스크톱은 그대로 둔다', () => {
  const flip = css.match(/@media \(min-width:\s*769px\) and \(max-width:\s*1399px\)\s*\{([\s\S]*?)\n\}/);
  assert.ok(flip, '앵커 반전 규칙이 있어야 한다');
  assert.match(flip[1], /\.newstart-page \.newstart-top > \.newstart-topbar:only-child \.account-menu-panel\s*\{\s*left:\s*0;\s*right:\s*auto/);
  // 1400px 이상에서는 어떤 새 규칙도 패널 앵커를 건드리면 안 된다(기존 데스크톱 동작 보존).
  assert.doesNotMatch(css, /@media[^{]*min-width:\s*1[4-9]\d\dpx[^{]*\{[^}]*account-menu-panel/);
});

test('NewStart 범위로만 고치고 공용 account_menu.css·js는 다른 서비스용으로 원형 유지', () => {
  assert.match(sharedCss, /\.account-menu-panel\s*\{\s*position:\s*absolute;\s*top:\s*calc\(100% \+ 8px\);\s*right:\s*0;/);
  assert.doesNotMatch(sharedCss, /newstart/i);
  assert.doesNotMatch(css, /\.account-menu-panel\s*\{\s*position:\s*fixed/);
});
