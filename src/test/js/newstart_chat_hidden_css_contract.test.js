'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const CSS_PATH = path.resolve(__dirname, '../../main/resources/static/css/newstart.css');

/**
 * 회귀: #349가 newstart-chat.js의 인증 판정을 헤더와 통일한 뒤에도 /newstart/chat은
 * 계속 "회원 채팅은 로그인 회원만 사용할 수 있습니다"를 보여줬다.
 *
 * Production 실측(DevTools) 증거 — GET /api/newstart/chat/messages?limit=30 = 200,
 * initiator newstart-chat.js: 이는 loadCurrentUser()가 인증된 사용자를 정확히 식별해
 * enterRoom() → loadHistory()까지 실제로 실행됐다는 뜻이다(#349는 정상 동작). 즉 JS의
 * 인증 판정/흐름 자체는 문제가 아니었다.
 *
 * Root cause — CSS 캐스케이드: enterRoom()은 `PAGE.loginBox.hidden = true`를 정확히
 * 설정하지만(HTML hidden 속성 자체는 실제로 붙는다), newstart.css의 `.ns-chat-login {
 * display: flex; ... }`(작성자 스타일)가 UA 기본 스타일 `[hidden] { display: none }`보다
 * cascade 우선순위가 높아 그대로 이긴다 — 그 결과 hidden 속성이 있어도 element는
 * `display: flex`로 계속 렌더된다. `.ns-chat-form`도 동일한 구조적 결함을 갖고 있었다
 * (로그인 폼이 로그인 회원에게는 안 보이고, 비로그인 방문자에게는 보이는 정반대 회귀로
 * 이어질 수 있었다 — production evidence는 messages 200을 보여줬으므로 실제로는 로그인
 * 상태에서 관찰됐다). 이 문제는 account_menu.css가 이미 `.auth-actions[hidden],
 * .account-menu[hidden], .login-modal[hidden] { display: none !important; }`로 명시적
 * 방어하는 것과 동일한 구조의 결함이었다 — newstart.css에는 그 방어 규칙이 없었다.
 *
 * 이 테스트는 newstart.css 소스에 `.ns-chat-login[hidden]`/`.ns-chat-form[hidden]`
 * override가 실제로 존재하고 `display: none`을 선언하는지 검증한다. 순수 DOM 구조
 * 테스트(newstart_chat_auth_source.test.js)는 JS가 hidden 속성을 올바르게 설정하는지만
 * 확인할 수 있고, CSS 캐스케이드가 그 속성을 실제로 반영하는지는 검증할 수 없다 — 그래서
 * 이번 회귀를 그 테스트가 잡지 못했다. 이 테스트가 그 사각지대를 메운다.
 */

function stripComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, '');
}

function parseRules(css) {
  const rules = [];
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let m;
  while ((m = re.exec(stripComments(css)))) {
    rules.push({ selectors: m[1].trim(), body: m[2].trim() });
  }
  return rules;
}

function declaredDisplay(body) {
  const m = /display\s*:\s*([^;]+);/i.exec(body);
  return m ? m[1].trim() : null;
}

function findRuleForSelector(rules, selector) {
  return rules.find((r) => r.selectors.split(',').map((s) => s.trim()).includes(selector));
}

test('given .ns-chat-login/.ns-chat-form가 display:flex 기반 클래스일 때 when [hidden] override를 확인하면 then 두 클래스 모두 [hidden]에서 display:none !important를 선언한다(hidden 속성이 실제로 화면에 반영됨)', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');
  const rules = parseRules(css);

  ['.ns-chat-login', '.ns-chat-form'].forEach((selector) => {
    const base = findRuleForSelector(rules, selector);
    assert.ok(base, selector + ' 기본 규칙을 찾지 못함');
    const baseDisplay = declaredDisplay(base.body);
    assert.ok(baseDisplay && baseDisplay !== 'none',
      selector + '는 display:flex 등으로 항상 보이는 것을 전제로 한다(현재: ' + baseDisplay + ')');

    const hiddenSelector = selector + '[hidden]';
    const hiddenRule = findRuleForSelector(rules, hiddenSelector);
    assert.ok(hiddenRule,
      hiddenSelector + ' override가 newstart.css에 없다 — hidden 속성이 ' + selector +
      '의 display:' + baseDisplay + '를 이기지 못해 실제로 숨겨지지 않는다(2026-09-21 회귀 재발)');

    const hiddenDisplay = declaredDisplay(hiddenRule.body);
    assert.equal(hiddenDisplay, 'none !important',
      hiddenSelector + '는 "display: none !important"를 선언해야 UA 기본값을 확실히 이긴다');
  });
});

test('given account_menu.css의 기존 [hidden] 방어 패턴 when newstart-chat 전용 클래스와 비교하면 then 같은 구조(display:none !important)를 따른다(인증 UI 전체에서 일관된 hidden 처리)', () => {
  const accountMenuCssPath = path.resolve(__dirname, '../../main/resources/static/css/account_menu.css');
  const accountMenuCss = fs.readFileSync(accountMenuCssPath, 'utf8');
  assert.match(accountMenuCss, /\[hidden\][^{]*\{[^}]*display:\s*none\s*!important/,
    '참조 패턴(account_menu.css)이 바뀌었다면 newstart.css의 방어 규칙도 같이 재검토해야 한다');
});
