'use strict';

/* NewStart 접속 회원 + 내부 쪽지 패널 계약 (2026-10-01):
   HOME 전용 슬라이드 패널. GRANT/OAuth/THANKS 로직·채팅·RAG과 무관하며
   렌더는 textContent만 사용한다. */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/index.html'), 'utf8');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/newstart-presence.js'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/newstart.css'), 'utf8');

test('presence entry points exist and stay login-gated', () => {
  assert.match(html, /id="ns-presence-edge"/);
  assert.match(html, />접속회원</);
  assert.match(html, /id="ns-presence-edge-unread"/);
  assert.match(html, /id="ns-presence-rail"/);
  assert.match(html, /id="ns-presence-panel"/);
  assert.match(html, /src="\/js\/newstart-presence\.js\?v=[\d-]+"/);
  // 401이면 진입점을 숨긴 채로 둔다 — 비로그인 노출 금지.
  assert.match(js, /status === 401/);
});

test('presence panel exposes online list, compose, inbox and privacy settings', () => {
  ['ns-presence-list', 'ns-presence-compose', 'ns-presence-to-name', 'ns-presence-input',
    'ns-presence-send', 'ns-presence-inbox', 'ns-presence-more', 'ns-presence-unread-count',
    'ns-presence-hide', 'ns-presence-block'].forEach((id) => {
    assert.match(html, new RegExp('id="' + id + '"'), id);
  });
  // 수신자는 userId 변수로만 다루고 닉네임 문자열로 식별하지 않는다.
  assert.match(js, /receiverUserId/);
  assert.doesNotMatch(js, /receiverNickname/);
  // 받은 쪽지 삭제는 수신자 본인 경로로만 나간다.
  assert.match(js, /\/api\/newstart\/dm\//);
  assert.match(js, /deleteMessage/);
});

test('presence rendering is textContent-only', () => {
  assert.match(js, /textContent/);
  assert.doesNotMatch(js, /\.innerHTML\s*=/);
});

test('presence panel is a left slide-over that stays safe on small screens', () => {
  assert.match(css, /\.ns-presence-panel\s*\{[^}]*left:\s*0/);
  assert.match(css, /\.ns-presence-panel\s*\{[^}]*width:\s*min\(340px,\s*94vw\)/);
  assert.match(css, /@media \(min-width:\s*1024px\)[\s\S]{0,400}?\.ns-presence-rail/);
  assert.match(css, /prefers-reduced-motion/);
});
