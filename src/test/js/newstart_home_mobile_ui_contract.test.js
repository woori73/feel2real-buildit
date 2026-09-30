'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/index.html'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/newstart.css'), 'utf8');

test('NewStart home은 보이는 진입점만 노출하고 삭제된 4코어 껍데기를 되살리지 않는다', () => {
  // 4진입점 practical grid + 채팅 서비스 카드가 유일한 카드 진입점이다.
  assert.match(html, /class="ns-core-grid ns-practical-grid"/);
  assert.equal((html.match(/<a class="ns-feature-card" href="\/newstart\/fss-helper"/g) || []).length, 1);
  ['/newstart/fss-helper', '/newstart/a-to-z', '/newstart/questions', '/newstart/galmuri'].forEach((route) => {
    assert.match(html, new RegExp('<a class="ns-feature-card" href="' + route + '"'));
  });
  // 2026-09-30 사용자 지시: 상단 '지피지기 Agent' 중복 카드 삭제 — 대표 질문 진입점은 아래 섹션(ns-ai) 1개.
  assert.doesNotMatch(html, /<a class="ns-feature-card" href="#ns-ai"/);
  assert.equal((html.match(/질문하기 →/g) || []).length, 0, '상단 카드 CTA 잔존 금지');
  assert.match(html, /ns-service-card ns-service-chat" href="\/newstart\/chat"/);
  // 화면 밖(hidden)에 숨겨둔 구 4코어 카드 껍데기는 되돌리면 안 된다(빈 영역·중복 진입점의 원인).
  assert.doesNotMatch(html, /<section class="ns-core"[^>]*hidden>/);
  assert.doesNotMatch(html, /class="ns-feature-card ns-feature-\w+/);
  // 4기능은 여전히 홈에서 reachable하다.
  ['내 채무상황', '지피지기', '회원 채팅', '관련 기관 공시 업데이트'].forEach((label) => assert.ok(html.includes(label), label));
  assert.doesNotMatch(html, /최신 정보|최신정보|전체 업데이트 보기/);
  // 채무상황 CTA는 별도 줄이 아니라 제목과 같은 header row에 있다(2026-09-30 지시).
  assert.match(html, /<div class="ns-section-heading ns-debt-heading">\s*<h2>채무상황<\/h2>[\s\S]{0,300}?내 채무상황 정리하기 →[\s\S]{0,120}?ns-debt-home-state/);
  assert.doesNotMatch(html, /<h3>정보·가이드<\/h3>|<h3>실제 사례<\/h3>/);
});

test('NewStart home 장식 영어 섹션 라벨 제거와 지피지기 Agent 표기 통일을 유지한다', () => {
  // 2026-09-30 UI 지시: 사용자 화면에서 장식용 영어 kicker/category 삭제, 한글 제목 단일화.
  assert.doesNotMatch(html, /ns-section-kicker">(MY DEBT|PRACTICAL TOOLS|COMMUNITY|MY STATUS|LATEST)/);
  assert.doesNotMatch(html, /class="ns-feature-category"/);
  assert.doesNotMatch(html, /지피지기 AI|NewStart AI|Jipijigi AI|AI Agent/);
  assert.ok(html.includes('지피지기 Agent'), '지피지기 Agent 표기 유지');
  // 공식 서비스명·확정 브랜드는 유지 (§3 예외).
  ['Credit4U', 'NewStart · Feel2Real'].forEach((n) => assert.ok(html.includes(n), n));
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
  // 2026-09-30 사용자 지시: NewStart는 footer 사업자/출판사 블록 자체를 렌더하지 않는다(다른 서비스는 유지).
  assert.match(html, /_public_footer :: publicFooterNewStart/);
  assert.doesNotMatch(html, /_public_footer :: publicFooterBranded\(true, true\)/);
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
