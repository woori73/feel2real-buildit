'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/index.html'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/newstart.css'), 'utf8');

test('NewStart home exposes exactly four mobile-first core feature cards', () => {
  const cards = html.match(/class="ns-feature-card\b/g) || [];
  assert.equal(cards.length, 4);
  ['내 상황', 'NewStart AI', '회원 채팅', '최신 정보'].forEach((label) => assert.match(html, new RegExp('>' + label + '<')));
  assert.doesNotMatch(html, /<h3>정보·가이드<\/h3>|<h3>실제 사례<\/h3>/);
});

test('NewStart home keeps verified routes and auth fragment contracts', () => {
  assert.match(html, /href="#ns-status-panel"[^>]*data-ns-open="ns-status-panel"/);
  assert.match(html, /href="#ns-ai"/);
  assert.match(html, /href="\/newstart\/chat"/);
  assert.match(html, /href="#ns-updates"/);
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
