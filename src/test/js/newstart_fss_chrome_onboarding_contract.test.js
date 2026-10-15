'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/newstart/fss-helper.html'), 'utf8');

test('fss-helper top onboarding states PC Chrome requirement plainly', () => {
  assert.match(html, /금감원 민원 도우미는 PC의 Google Chrome에서 사용하는 기능입니다\./);
  assert.match(html, /현재 단계에 맞는 작성 안내를 받을 수 있습니다/);
});

test('fss-helper shows the four-step usage flow without dev jargon', () => {
  assert.match(html, /<li>PC에서 Google Chrome을 실행합니다\.<\/li>/);
  assert.match(html, /<li>NewStart Chrome Agent를 설치합니다\./);
  assert.match(html, /<li>NewStart의 「금감원 민원 도우미」를 실행합니다\./);
  assert.match(html, /<li>금융감독원 사이트에서 화면 안내에 따라 민원을 작성합니다\.<\/li>/);
});

test('store published: official URL constant, active install CTA, zero pending copy', () => {
  // 유일한 Store URL 출처 = 상수 1개, 공식 URL exact match
  const OFFICIAL = 'https://chromewebstore.google.com/detail/aickncaeacnplhgpipmnffillpmocfhf';
  assert.match(html, new RegExp('var CHROME_WEB_STORE_URL = "' + OFFICIAL.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '";'));
  const storeUrls = html.match(/https:\/\/[a-z.]*webstore[a-z.]*[^\s"']*/g) || [];
  assert.ok(storeUrls.length > 0);
  storeUrls.forEach((u) => assert.equal(u, OFFICIAL, 'non-official store URL: ' + u));
  // 설치는 상수 주입(innerHTML 없음) — 마크업에는 literal URL 중복 금지
  assert.equal(html.split(OFFICIAL).length - 1, 1, 'official URL must appear exactly once');
  // 설치 CTA 활성: disabled/aria-disabled 없음
  assert.match(html, /<button type="button" class="ns-btn ns-btn-primary" id="ns-ext-install">Chrome Agent 설치<\/button>/);
  assert.equal(/id="ns-ext-install"[^>]*disabled/.test(html), false);
  // pending 문구 0
  ['Chrome Web Store 검토 중', '출시 준비 중', '검토 대기', '심사', '승인이 끝나면'].forEach((s) => {
    assert.equal(html.includes(s), false, 'pending copy remains: ' + s);
  });
  // 구형/가짜 Store 링크 금지
  assert.equal(html.includes('chrome.google.com/webstore'), false);
});

test('manual-only promises are stated: auth, password, OTP/CAPTCHA, submit', () => {
  ['공동인증서/금융인증서 인증을 대신하지 않습니다.',
    '비밀번호를 입력하거나 저장하지 않습니다.',
    'OTP/CAPTCHA를 대신 처리하지 않습니다.',
    '민원을 자동 제출하지 않습니다.',
    '최종 제출은 사용자가 직접 확인하고 진행합니다.'].forEach((line) => {
    assert.ok(html.includes(line), 'missing: ' + line);
  });
  // 과장 금지 — 접수·제출 대행 주장
  assert.equal(html.includes('AI가 대신 민원을 접수'), false);
  assert.equal(html.includes('자동으로 민원을 제출'), false);
});

test('privacy notice links to existing /privacy policy', () => {
  assert.match(html, /비밀번호 등 민감한 입력값은 확장프로그램이 읽거나 저장하지 않습니다/);
  assert.match(html, /href="\/privacy"/);
});

test('mobile path never pushes extension install — PC access note instead', () => {
  assert.match(html, /이 기능은 PC Chrome에서 이용해주세요\./);
  assert.match(html, /금감원 민원 도우미의 Chrome 연동 기능은 PC에서 이용할 수 있습니다/);
  // 모바일 배너는 기본 숨김, 좁은 화면에서만 표시 (기존 mobilenote와 동일 방식)
  assert.match(html, /\.ns-fss-onb-mobile\{display:none/);
  assert.match(html, /\.ns-fss-onb-mobile\{display:block\}/);
});

test('extension bridge contract preserved: badge id, ping source, message source', () => {
  assert.match(html, /id="ns-chrome-badge"/);
  assert.match(html, /newstart-page-ping/);
  assert.match(html, /source !== 'newstart-agent'|'newstart-agent'/);
});
