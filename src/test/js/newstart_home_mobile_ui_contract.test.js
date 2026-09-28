'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/index.html'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/newstart.css'), 'utf8');

test('NewStart home은 보이는 진입점만 노출하고 삭제된 4코어 껍데기를 되살리지 않는다', () => {
  // 5진입점 practical grid + 채팅 서비스 카드가 유일한 카드 진입점이다.
  assert.match(html, /class="ns-core-grid ns-practical-grid"/);
  assert.equal((html.match(/<a class="ns-feature-card" href="\/newstart\/fss-helper"/g) || []).length, 1);
  ['/newstart/fss-helper', '/newstart/a-to-z', '/newstart/questions', '/newstart/galmuri'].forEach((route) => {
    assert.match(html, new RegExp('<a class="ns-feature-card" href="' + route + '"'));
  });
  assert.match(html, /<a class="ns-feature-card" href="#ns-ai"/);
  assert.match(html, /ns-service-card ns-service-chat" href="\/newstart\/chat"/);
  // 화면 밖(hidden)에 숨겨둔 구 4코어 카드 껍데기는 되돌리면 안 된다(빈 영역·중복 진입점의 원인).
  assert.doesNotMatch(html, /<section class="ns-core"[^>]*hidden>/);
  assert.doesNotMatch(html, /class="ns-feature-card ns-feature-\w+/);
  // 4기능은 여전히 홈에서 reachable하다.
  ['내 채무상황', '지피지기', '회원 채팅', '최신 정보'].forEach((label) => assert.ok(html.includes(label), label));
  assert.doesNotMatch(html, /<h3>정보·가이드<\/h3>|<h3>실제 사례<\/h3>/);
});

test('NewStart home exposes the review-gated official update feed contract', () => {
  assert.match(html, /id="ns-updates"/);
  assert.match(html, /latestUpdates/);
  assert.match(html, /\/newstart\/updates/);
  assert.doesNotMatch(html, /새출발기금 관련 공식자료를 검토하고 있습니다/);
});

test('NewStart home keeps verified routes and auth fragment contracts', () => {
  assert.match(html, /href="#ns-status-panel"[^>]*data-ns-open="ns-status-panel"/);
  assert.match(html, /href="#ns-ai"/);
  assert.match(html, /href="\/newstart\/chat"/);
  assert.match(html, /id="ns-updates"[^>]*aria-labelledby="ns-updates-title"/);
  assert.match(html, /href="\/newstart\/updates"/);
  assert.match(html, /_newstart_auth :: authControls/);
  assert.match(html, /id="ns-ai-form"/);
});

test('shared footer styles and explicit SVG limits prevent giant logo regression', () => {
  assert.match(html, /_public_footer :: publicFooterStyles/);
  assert.match(css, /\.public-footer \.footer-parent-logo-box img[\s\S]*?max-height:\s*28px\s*!important/);
  assert.match(css, /\.ns-footer-mark[\s\S]*?max-width:\s*28px/);
});

test('mobile layout has one-column cards, safe widths and touch targets', () => {
  assert.match(css, /\.ns-core-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.match(css, /html, body\s*\{[^}]*overflow-x:\s*clip/);
  assert.match(css, /\.ns-feature-cta\s*\{[^}]*min-height:\s*44px/);
  assert.match(css, /@media \(min-width:\s*1024px\)/);
});

test('#350 hidden display contract remains intact', () => {
  assert.match(css, /\.ns-chat-login\[hidden\],\s*\.ns-chat-form\[hidden\]\s*\{\s*display:\s*none\s*!important/);
});
