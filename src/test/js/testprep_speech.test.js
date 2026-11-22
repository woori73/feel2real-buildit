'use strict';
// 듣기 음성(TTS): 대본을 화자 차례로 나누고(TP.speechTurns), 긴 차례를 문장 덩어리로 나누고(TP.speechChunks),
// 큐에 넣을 발화 목록(TP.speechQueue)을 만드는 순수 함수 검사. 실제 소리는 검사하지 못한다.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/testprep.js'), 'utf8');

function loadTP() {
  const document = { cookie: '', body: { appendChild() {} }, addEventListener() {}, getElementById: () => null, createElement: () => ({}) };
  const window = { addEventListener() {} };
  const ctx = { window, document, location: { pathname: '/testprep', search: '', protocol: 'https:' }, fetch: async () => ({ ok: false, status: 401 }), URLSearchParams, encodeURIComponent, console };
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx);
  return window.TP;
}

// vm 안에서 만든 배열·객체는 원형(prototype)이 달라 deepStrictEqual이 실패하므로 JSON으로 옮겨 비교한다.
const plain = v => JSON.parse(JSON.stringify(v));

test('given 줄표로 이은 화자 2명의 대화(iBT), when speechTurns를 부르면, then 차례 2개로 나누고 꼬리표는 글에서 뺀다', () => {
  // given
  const TP = loadTP();
  const script = 'Woman: I booked a study room for two hours. Can I change it? — Man: Yes, but only on the same day.';
  // when
  const turns = plain(TP.speechTurns(script));
  // then
  assert.deepStrictEqual(turns, [
    { speaker: 'Woman', text: 'I booked a study room for two hours. Can I change it?' },
    { speaker: 'Man', text: 'Yes, but only on the same day.' }]);
});

test('given 줄바꿈으로 나눈 화자 3명의 대화(TOEIC Part 3, "Man 1:"), when speechTurns를 부르면, then 안내 줄은 화자 없는 차례·나머지는 화자별 차례가 된다', () => {
  // given
  const TP = loadTP();
  const script = 'Questions 32 through 34 refer to the following conversation with three speakers.\n\n'
    + 'Man 1: Has the projector arrived?\nWoman: Not yet. It ships Thursday.\nMan 2: Then let\'s borrow one.\nMan 1: Good idea.';
  // when
  const turns = plain(TP.speechTurns(script));
  // then
  assert.deepStrictEqual(turns, [
    { speaker: null, text: 'Questions 32 through 34 refer to the following conversation with three speakers.' },
    { speaker: 'Man 1', text: 'Has the projector arrived?' },
    { speaker: 'Woman', text: 'Not yet. It ships Thursday.' },
    { speaker: 'Man 2', text: 'Then let\'s borrow one.' },
    { speaker: 'Man 1', text: 'Good idea.' }]);
});

test('given 화자가 없는 글(강의·안내 방송·Part 2), when speechTurns를 부르면, then 글을 바꾸지 않고 한 차례로 둔다', () => {
  // given
  const TP = loadTP();
  const talk = 'Let us turn to transport networks — the roads between cities. Railways show it clearly: a single line barely pays.';
  const announcement = 'Attention cast members: tonight\'s music rehearsal will start thirty minutes late.';
  const part2 = 'Where is it?\nResponse A. A desk.\nResponse B. At noon.\nResponse C. Yes.';
  // when / then — 문장 속 줄표·콜론, "Attention cast members:"는 화자가 아니다
  assert.deepStrictEqual(plain(TP.speechTurns(talk)), [{ speaker: null, text: talk }]);
  assert.deepStrictEqual(plain(TP.speechTurns(announcement)), [{ speaker: null, text: announcement }]);
  assert.deepStrictEqual(plain(TP.speechTurns(part2)), [{ speaker: null, text: part2 }]);
  assert.deepStrictEqual(plain(TP.speechTurns('')), []);
  assert.deepStrictEqual(plain(TP.speechTurns(null)), []);
});

test('given 시각 "9:30"과 문장 속 콜론이 든 대화, when speechTurns를 부르면, then 시각·콜론을 화자로 오인하지 않는다', () => {
  // given — 차례 맨 앞이 시각으로 시작하고, 줄표 뒤 조각에도 콜론이 있다
  const TP = loadTP();
  const script = 'Woman: When does the shuttle leave? — Man: 9:30 on weekdays, and at 10:15 on Sundays. — Woman: One more thing — the fare: is it still two dollars?';
  // when
  const turns = plain(TP.speechTurns(script));
  // then
  assert.deepStrictEqual(turns, [
    { speaker: 'Woman', text: 'When does the shuttle leave?' },
    { speaker: 'Man', text: '9:30 on weekdays, and at 10:15 on Sundays.' },
    { speaker: 'Woman', text: 'One more thing — the fare: is it still two dollars?' }]);
});

test('given 소문자 낱말이 든 꼬리표("Lab worker:")와 혼자 말하는 "Professor:", when speechTurns를 부르면, then 대화의 꼬리표와 한 낱말 꼬리표를 화자로 본다', () => {
  // given
  const TP = loadTP();
  const dialogue = 'Student: The scale shows an error. — Lab worker: Press the zero button.';
  const lecture = 'Professor: Today we look at ant colonies.\nNo single ant gives orders.';
  // when / then
  assert.deepStrictEqual(plain(TP.speechTurns(dialogue)), [
    { speaker: 'Student', text: 'The scale shows an error.' },
    { speaker: 'Lab worker', text: 'Press the zero button.' }]);
  assert.deepStrictEqual(plain(TP.speechTurns(lecture)), [
    { speaker: 'Professor', text: 'Today we look at ant colonies.\nNo single ant gives orders.' }]);
});

test('given 짧은 글과 긴 글, when speechChunks를 부르면, then 짧은 글은 그대로·긴 글은 문장 단위로 한도 이하 덩어리가 된다', () => {
  // given
  const TP = loadTP();
  const short = 'Where is it?\nResponse A. A desk.';
  const long = 'Hi, Ms. Petrosyan. It is Rob from Brightway Flooring. The carpet has not come in yet! Can we start on Friday? Please call me back.';
  // when
  const one = plain(TP.speechChunks(short, 60));
  const many = plain(TP.speechChunks(long, 60));
  // then
  assert.deepStrictEqual(one, [short]);
  assert.deepStrictEqual(many, [
    'Hi, Ms. Petrosyan. It is Rob from Brightway Flooring.', // "Ms." 뒤에서는 끊지 않는다
    'The carpet has not come in yet! Can we start on Friday?',
    'Please call me back.']);
  assert.ok(many.every(c => c.length <= 60));
  assert.strictEqual(many.join(' '), long); // 빠지는 글이 없다
  assert.deepStrictEqual(plain(TP.speechChunks('', 60)), []);
  assert.deepStrictEqual(plain(TP.speechChunks(long)), [long]); // 기본 한도(200자) 이하
});

test('given 화자 3명의 TOEIC Part 3 문항, when speechQueue를 부르면, then 꼬리표 없는 발화 목록과 화자별 높이(1.0·0.8·1.2)를 돌려준다', () => {
  // given
  const TP = loadTP();
  const q = { taskType: 'TOEIC_P3', bodyText: 'Questions 32 through 34 refer to the following conversation.\n\n'
    + 'Man 1: Has the projector arrived?\nWoman: Not yet.\nMan 2: Then let\'s borrow one.\nMan 1: Good idea.' };
  // when
  const queue = plain(TP.speechQueue(q));
  // then — 안내 줄(화자 없음)은 1.0, 같은 화자는 같은 높이
  assert.deepStrictEqual(queue, [
    { text: 'Questions 32 through 34 refer to the following conversation.', pitch: 1 },
    { text: 'Has the projector arrived?', pitch: 1 },
    { text: 'Not yet.', pitch: 0.8 },
    { text: 'Then let\'s borrow one.', pitch: 1.2 },
    { text: 'Good idea.', pitch: 1 }]);
  assert.ok(queue.every(u => !/Man 1|Man 2|Woman/.test(u.text)));
});

test('given 화자가 없는 문항(Part 2·긴 강의), when speechQueue를 부르면, then Part 2는 한 발화("Response A.")·긴 강의는 문장 덩어리 여러 개가 된다', () => {
  // given
  const TP = loadTP();
  const p2 = { taskType: 'TOEIC_P2', bodyText: 'Where is it?\nA. A desk.\nB. At noon.\nC. Yes.' };
  const sentence = 'A route becomes valuable when many independent users need the same link.';
  const talk = { taskType: 'LISTEN_TALK', bodyText: Array(6).fill(sentence).join(' ') }; // 431자
  // when
  const p2Queue = plain(TP.speechQueue(p2));
  const talkQueue = plain(TP.speechQueue(talk));
  // then
  assert.deepStrictEqual(p2Queue, [{ text: 'Where is it?\nResponse A. A desk.\nResponse B. At noon.\nResponse C. Yes.', pitch: 1 }]);
  assert.strictEqual(talkQueue.length, 3);
  assert.ok(talkQueue.every(u => u.pitch === 1 && u.text.length <= 200));
  assert.strictEqual(talkQueue.map(u => u.text).join(' '), talk.bodyText);
  assert.deepStrictEqual(plain(TP.speechQueue({ taskType: 'LISTEN_TALK', bodyText: null })), []);
});
