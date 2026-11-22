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

test('given TOEIC 1회분 채점 결과, when toeicScorecardHtml을 부르면, then 합계와 영역별 추정 점수·정답 수를 받은 순서(듣기 → 읽기)대로 그린다', () => {
  // given
  const TP = loadTP();
  const card = { attemptId: 42, kind: 'TOEIC', totalScaled: 765, totalMax: 990, bandDisclaimer: 'F2R internal score estimate',
    sections: [
      { section: 'LISTENING', scaled: 370, scaleMax: 495, correct: 70, autoCount: 94, accuracy: 74.5 },
      { section: 'READING', scaled: 395, scaleMax: 495, correct: 80, autoCount: 100, accuracy: 80 }] };
  // when
  const html = TP.toeicScorecardHtml(card, false);
  // then
  assert.ok(html.includes('<div class="band-big">765 / 990</div>'));
  assert.ok(html.includes('<td>Listening</td><td>370 / 495</td><td>70 / 94</td><td>74.5%</td>'));
  assert.ok(html.includes('<td>Reading</td><td>395 / 495</td><td>80 / 100</td><td>80%</td>'));
  assert.ok(html.indexOf('Listening') < html.indexOf('<td>Reading'));
  assert.ok(html.includes('Submitted') && !html.includes('Auto-submitted'));
  assert.ok(html.includes('/testprep/review?attempt=42') && html.includes('/testprep/toeic'));
});

test('given 오답만 다시 푼 TOEIC 결과(추정 점수 없음), when toeicScorecardHtml을 부르면, then 점수 대신 정답 수를 크게 보여 준다', () => {
  // given — 틀린 3문항을 모두 맞혔지만 서버가 scaled를 비운 결과
  const TP = loadTP();
  const card = { attemptId: 7, kind: 'TOEIC', totalScaled: null, totalMax: null, bandDisclaimer: 'x',
    sections: [{ section: 'READING', scaled: null, scaleMax: null, correct: 3, autoCount: 3, accuracy: 100 }] };
  // when
  const html = TP.toeicScorecardHtml(card, true);
  // then
  assert.ok(html.includes('<div class="band-big">3 / 3</div>'));
  assert.ok(!html.includes('495'));
  assert.ok(html.includes('<td>Reading</td><td>—</td><td>3 / 3</td><td>100%</td>'));
  assert.ok(html.includes('오답만 다시 풀기는 추정 점수를 내지 않습니다'));
  assert.ok(html.includes('Auto-submitted at time limit'));
});

test('given TOEIC Part 2 대본, when speechText를 부르면, then 응답 줄 머리만 "Response A."로 바꾸고 다른 문항은 그대로 둔다', () => {
  // given — 응답이 관사 A로 시작하는 문항
  const TP = loadTP();
  const p2 = { taskType: 'TOEIC_P2', bodyText: 'What did you buy?\nA. A new toothbrush.\nB. By the door.\nC. Yes, I did.' };
  const p3 = { taskType: 'TOEIC_P3', bodyText: 'Man: Hello.\nA. This line stays.' };
  const ibt = { taskType: 'LISTEN_RESPONSE', bodyText: 'A. This is an iBT script.' };
  // when / then
  assert.strictEqual(TP.speechText(p2), 'What did you buy?\nResponse A. A new toothbrush.\nResponse B. By the door.\nResponse C. Yes, I did.');
  assert.strictEqual(p2.bodyText.includes('Response'), false); // 원래 대본은 바꾸지 않는다
  assert.strictEqual(TP.speechText(p3), p3.bodyText);
  assert.strictEqual(TP.speechText(ibt), ibt.bodyText);
  assert.strictEqual(TP.speechText({ taskType: 'TOEIC_P2', bodyText: null }), '');
});

test('given 오답 목록의 문항, when wrongItemLabel을 부르면, then TOEIC만 실제 문항 번호를 앞에 붙인다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.strictEqual(TP.wrongItemLabel({ taskType: 'TOEIC_P6', orderNo: 131, promptShort: 'Choose the best answer for blank (131).' }),
    'No. 131 · Choose the best answer for blank (131).');
  assert.strictEqual(TP.wrongItemLabel({ taskType: 'READ_PASSAGE', orderNo: 3, promptShort: 'What is the main idea?' }), 'What is the main idea?');
  assert.strictEqual(TP.wrongItemLabel({ taskType: 'TOEIC_P5', promptShort: 'x' }), 'x'); // 번호가 없으면 붙이지 않는다
});

// 응시 화면(testprep_run.js)을 가짜 화면에서 실제로 실행한다. 음성 호출(cancel·speak)을 기록한다.
async function bootRun(attempt, opts) {
  const PAGE_IDS = ['stage', 'exam-title', 'sec-tabs', 'timer', 'progress', 'btn-submit', 'prev', 'next', 'save-note']; // 이 화면에 있는 요소
  const speech = { cancels: 0, spoken: [] };
  const els = {};
  const el = id => els[id] || (els[id] = {
    id, innerHTML: '', textContent: '', disabled: false, dataset: {}, handlers: {},
    classList: { toggle() {} },
    addEventListener(type, fn) { this.handlers[type] = fn; },
    querySelectorAll: () => [],
    querySelector(sel) { return sel === '[data-tts]' && this.innerHTML.includes('data-tts') ? el('tts-button') : null; }
  });
  const ctx = {
    document: { cookie: '', body: { appendChild() {} }, addEventListener() {}, getElementById: id => (PAGE_IDS.includes(id) ? el(id) : null), createElement: () => el('created'), querySelectorAll: () => [] },
    location: { pathname: '/testprep/run', search: '?attempt=' + attempt.attemptId, protocol: 'https:' },
    fetch: async () => ({ ok: false, status: 401 }),
    addEventListener() {}, setInterval: () => 0, clearInterval() {}, setTimeout, clearTimeout,
    confirm: () => true, alert() {},
    SpeechSynthesisUtterance: function (text) { this.text = text; },
    URLSearchParams, encodeURIComponent, console, Promise, JSON, Date, Math, Object, Array, String, Set
  };
  if (!(opts && opts.noSpeech)) ctx.speechSynthesis = { cancel() { speech.cancels++; }, speak(u) { speech.spoken.push(u.text); } };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx);
  ctx.TP.loadMe = async () => null;
  ctx.TP.api = async (url, o) => (o && o.method === 'POST')
    ? { attemptId: attempt.attemptId, kind: attempt.kind, sections: [], totalScaled: null, totalMax: null, bandDisclaimer: '' }
    : (o && o.method === 'PUT') ? null : attempt;
  vm.runInContext(fs.readFileSync(path.join(JS, 'testprep_run.js'), 'utf8'), ctx);
  await new Promise(r => setTimeout(r, 10)); // boot()가 응시를 불러와 첫 문항을 그릴 때까지
  return { speech, click: async id => { await el(id).handlers.click(); await new Promise(r => setTimeout(r, 10)); }, el };
}

const LISTENING_ATTEMPT = { attemptId: 9, kind: 'TOEIC', status: 'IN_PROGRESS', title: 'TOEIC', remainingSec: 600, savedAnswers: {},
  questions: [
    { questionId: 1, section: 'LISTENING', taskType: 'TOEIC_P2', orderNo: 7, prompt: 'Choose the best response.', audioMode: 'TTS', choices: ['', '', ''],
      bodyText: 'Where is it?\nA. A desk.\nB. At noon.\nC. Yes.' },
    { questionId: 2, section: 'LISTENING', taskType: 'TOEIC_P3', orderNo: 32, prompt: 'What does the man want?', audioMode: 'TTS', choices: ['a', 'b', 'c', 'd'],
      bodyText: 'Man: Hello.' }] };

test('given 듣기 문항의 음성이 나오는 중, when 다음 문항으로 가거나 제출하면, then 앞 음성을 멈춘다', async () => {
  // given — Part 2 문항에서 Play audio를 누른 상태
  const run = await bootRun(LISTENING_ATTEMPT);
  await run.click('tts-button');
  assert.deepStrictEqual(run.speech.spoken, ['Where is it?\nResponse A. A desk.\nResponse B. At noon.\nResponse C. Yes.']);
  const before = run.speech.cancels;
  // when — 다음 문항으로 이동
  await run.click('next');
  // then
  assert.strictEqual(run.speech.cancels, before + 1);
  assert.ok(run.el('stage').innerHTML.includes('No. 32'));
  // when — 제출
  await run.click('btn-submit');
  // then
  assert.strictEqual(run.speech.cancels, before + 2);
  assert.strictEqual(run.el('btn-submit').disabled, true);
});

test('given 음성을 지원하지 않는 브라우저, when 문항을 옮기면, then 오류 없이 다음 문항을 그린다', async () => {
  // given
  const run = await bootRun(LISTENING_ATTEMPT, { noSpeech: true });
  // when
  await run.click('next');
  // then
  assert.ok(run.el('stage').innerHTML.includes('No. 32'));
});
