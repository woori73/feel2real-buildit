'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources');
const tool = fs.readFileSync(path.join(root, 'templates/newstart/practical-tool.html'), 'utf8');
const home = fs.readFileSync(path.join(root, 'templates/newstart/index.html'), 'utf8');
const js = fs.readFileSync(path.join(root, 'static/js/newstart.js'), 'utf8');

/* HOME → Credit4U → 기존 본인채무 상태창(#ns-status-panel) → 지피지기 연결의 navigation 계약. */

test('HOME의 채무상태 배지는 MY DEBT 진입행에 남아 있고 상태창으로 바로 열린다', () => {
  // Phase2 §2: Credit4U 카드는 HOME 5개 진입점에서 빠졌지만 배지·상태창·재진입 링크는 보존
  assert.match(home, /href="#ns-status-panel"[^>]*data-ns-open="ns-status-panel">내 채무상황 정리하기/);
  assert.match(home, /id="ns-debt-home-state" data-state="NOT_STARTED">미확인<\/span>/);
  assert.match(home, /href="\/newstart\/tools\/credit4u"/);
});

test('Credit4U 가이드는 전자책 3줄 stub이 아니라 공식 메뉴 기준 단계별 안내다', () => {
  const credit = tool.slice(tool.indexOf("tool == 'credit4u'"), tool.indexOf("tool == 'application'"));
  ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨'].forEach((marker) => assert.ok(credit.includes(marker), marker));
  ['일반신용정보', '신용정보 등록현황', '신용도판단정보', '공공정보', '개인대출정보',
    '개설·발급정보', '채무보증정보', '채권자 변동정보', '개인회생 신청 등 정보', '대출채권소각정보']
    .forEach((menu) => assert.ok(credit.includes(menu), menu));
  assert.doesNotMatch(credit, /왜 확인하나요\?/);
});

test('Credit4U는 기록 항목과 장기연체 실전 팁을 주되 자격 확정을 단정하지 않는다', () => {
  const credit = tool.slice(tool.indexOf("tool == 'credit4u'"), tool.indexOf("tool == 'application'"));
  ['채권기관', '채무 종류', '현재 잔액', '보증채무', '채권자 변경'].forEach((item) => assert.ok(credit.includes(item), item));
  assert.match(credit, /신용도판단정보에서 연체 관련 등록 내용을 확인/);
  assert.match(credit, /실제 새출발기금 지원대상 여부는 새출발기금의 현재 공식 기준과 심사 결과에 따릅니다/);
  assert.match(credit, /NewStart는 ID, 비밀번호, 인증번호, 공동·금융인증 정보를 받거나 Credit4U에 대신 로그인하지 않습니다/);
  assert.doesNotMatch(credit, /0101|0202|부실차주 확정|신청 대상 확정|자격 확정/);
});

test('Credit4U 하단 CTA가 기존 상태창으로, 공식 URL 외부 링크가 유지된다', () => {
  const credit = tool.slice(tool.indexOf("tool == 'credit4u'"), tool.indexOf("tool == 'application'"));
  assert.match(credit, /href="https:\/\/www\.credit4u\.or\.kr:2443\/"/);
  assert.match(credit, /rel="noopener noreferrer"/);
  assert.match(credit, /href="\/newstart#ns-status-panel"/);
  assert.match(credit, /확인한 채무정보 정리하기/);
});

test('기존 #ns-status-panel이 SOURCE OF TRUTH — 6개 체크·메모·단계·저장/지피지기 버튼', () => {
  ['ns-flag-credit4u', 'ns-flag-creditor', 'ns-flag-loan', 'ns-flag-overdue',
    'ns-flag-guarantee', 'ns-flag-creditor-change'].forEach((id) => assert.ok(home.includes('id="' + id + '"'), id));
  assert.match(home, /id="ns-debt-save"[^>]*>내 채무상황 저장<\/button>/);
  assert.match(home, /id="ns-debt-ask-agent">내 채무상황으로 지피지기 Agent에게 물어보기<\/button>/);
  assert.doesNotMatch(home, /서버에 저장되지 않습니다|메모 저장 — 준비 중/);
});

test('newstart.js가 채무상황 API와 해시 복귀-open, userId 미수신을 강제한다', () => {
  assert.match(js, /\/api\/newstart\/debt-status\/me/);
  assert.match(js, /method: 'PUT'/);
  assert.match(js, /openFromHash/);
  assert.match(js, /#ns-status-panel/);
  assert.match(js, /로그인 후 저장할 수 있습니다/);
  assert.doesNotMatch(js, /localStorage\.|sessionStorage\./);
  assert.doesNotMatch(js, /userId\s*[:=]/);
});
