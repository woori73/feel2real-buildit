'use strict';
// TOEIC 연습(/testprep/toeic)이 기존 iBT 응시 화면(/testprep/run)을 함께 쓰기 위해 testprep.js에 더한 도우미 검사.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js');
const SRC = fs.readFileSync(path.join(JS, 'testprep.js'), 'utf8');

function loadTP() {
  const document = { cookie: '', body: { children: [], appendChild() {} }, addEventListener() {}, getElementById: () => null, createElement: () => ({}) };
  const window = { addEventListener() {} };
  const ctx = { window, document, location: { pathname: '/testprep', search: '', protocol: 'https:' }, fetch: async () => ({ ok: false, status: 401 }), URLSearchParams, encodeURIComponent, console };
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx);
  return window.TP;
}

test('given TOEIC 문항 종류, when toeicPart를 부르면, then Part 번호를 돌려주고 다른 종류는 null이다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.strictEqual(TP.toeicPart('TOEIC_P2'), 2);
  assert.strictEqual(TP.toeicPart('TOEIC_P7'), 7);
  assert.strictEqual(TP.toeicPart('TOEIC_P1'), null); // Part 1(사진 묘사)은 만들지 않는다
  assert.strictEqual(TP.toeicPart('READ_PASSAGE'), null);
  assert.strictEqual(TP.toeicPart(undefined), null);
});

test('given TOEIC과 iBT 문항, when groupOf를 부르면, then TOEIC은 Part별·iBT는 영역별로 탭이 묶인다', () => {
  // given
  const TP = loadTP();
  const questions = [
    { section: 'LISTENING', taskType: 'TOEIC_P2' }, { section: 'LISTENING', taskType: 'TOEIC_P3' },
    { section: 'READING', taskType: 'TOEIC_P5' }, { section: 'READING', taskType: 'TOEIC_P5' },
    { section: 'READING', taskType: 'READ_PASSAGE' }];
  // when
  const groups = questions.map(TP.groupOf);
  // then
  assert.deepStrictEqual(groups, ['Part 2', 'Part 3', 'Part 5', 'Part 5', 'READING']);
});

test('given TOEIC 문항, when questionLabel을 부르면, then 실제 시험 번호(order_no)와 Part 이름을 쓴다', () => {
  // given
  const TP = loadTP();
  // when
  const toeic = TP.questionLabel({ section: 'READING', taskType: 'TOEIC_P6', orderNo: 131 }, 0, 100);
  const ibt = TP.questionLabel({ section: 'READING', taskType: 'READ_DAILY_LIFE', orderNo: 3 }, 2, 28);
  const review = TP.questionLabel({ section: 'VOCAB', taskType: 'VOCAB_E2S', orderNo: 1 }, 0);
  // then
  assert.strictEqual(toeic, 'Part 6 · Text Completion · No. 131');
  assert.strictEqual(ibt, 'READING · READ DAILY LIFE · Q3/28'); // 기존 iBT 표기는 그대로
  assert.strictEqual(review, 'VOCAB · VOCAB E2S · Q1');
});

test('given 추정 점수와 만점, when scaledText를 부르면, then "점수 / 만점"으로 쓰고 점수가 없으면 —', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.strictEqual(TP.scaledText(395, 495), '395 / 495');
  assert.strictEqual(TP.scaledText(765, null), '765');
  assert.strictEqual(TP.scaledText(null, 990), '—');
});

test('given TOEIC 응시, when 응시 화면 주소를 만들면, then 기존 iBT 화면(/testprep/run)을 쓴다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.strictEqual(TP.runUrl('TOEIC', 42), '/testprep/run?attempt=42');
  assert.strictEqual(TP.runUrl('SAT', 42), '/testprep/sat/run?attempt=42');
});

test('given 화면 스크립트, when TOEIC 분기를 찾으면, then 응시·복습·점수 화면이 모두 TOEIC을 처리한다', () => {
  // given
  const run = fs.readFileSync(path.join(JS, 'testprep_run.js'), 'utf8');
  const review = fs.readFileSync(path.join(JS, 'testprep_review.js'), 'utf8');
  const scores = fs.readFileSync(path.join(JS, 'testprep_scores.js'), 'utf8');
  // when / then
  assert.ok(run.includes("card.kind === 'TOEIC'") && run.includes('TP.groupOf(q)') && run.includes('passageFirst'));
  assert.ok(review.includes("rv.kind === 'TOEIC'") && review.includes('/testprep/toeic'));
  assert.ok(scores.includes("r.kind === 'TOEIC'"));
});
