'use strict';
// TestPrep 응시·복습 화면의 묶음 화면(같은 지문·대본을 쓰는 이웃 문항), 듣기 1회 재생(TOEIC), [[show]] 시각자료,
// 지문 속 표·그림 표기, 문장 만들기 칩 섞기를 검사한다. 순수 함수는 직접 부르고, 화면은 가짜 화면에서 실제로 실행한다.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(JS, file), 'utf8');

// testprep.js + sat_rich.js만 올린 화면(순수 함수용). storage = 가짜 sessionStorage(없으면 지원하지 않는 브라우저).
function loadTP(storage) {
  const ctx = {
    document: { cookie: '', body: { appendChild() {} }, addEventListener() {}, getElementById: () => null, createElement: () => ({}) },
    location: { pathname: '/testprep', search: '', protocol: 'https:' },
    fetch: async () => ({ ok: false, status: 401 }), addEventListener() {}, URLSearchParams, encodeURIComponent, console
  };
  if (storage) ctx.sessionStorage = storage;
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('sat_rich.js'), ctx);
  vm.runInContext(read('testprep.js'), ctx);
  return ctx.TP;
}

const fakeStorage = () => { const data = {}; return { data, getItem: k => (k in data ? data[k] : null), setItem(k, v) { data[k] = String(v); } }; };
const plain = v => JSON.parse(JSON.stringify(v)); // vm 안에서 만든 값을 바깥 값과 비교할 수 있게 옮긴다.
const mc = (id, bodyText, over) => Object.assign({ questionId: id, section: 'READING', taskType: 'TOEIC_P7', orderNo: 146 + id, prompt: 'Q' + id,
  bodyText, audioMode: 'NONE', choices: ['a', 'b', 'c', 'd'] }, over);
const shape = groups => plain(groups.map(g => ({ start: g.start, ids: g.questions.map(q => q.questionId) })));

// ---------- 묶음 ----------

test('given 같은 지문을 쓰는 이웃 3문항, when groupQuestions를 부르면, then 한 묶음이 되고 앞뒤 단독 문항은 따로다', () => {
  // given
  const TP = loadTP();
  const questions = [mc(1, null, { taskType: 'TOEIC_P5' }), mc(2, 'PASSAGE A'), mc(3, 'PASSAGE A'), mc(4, 'PASSAGE A'), mc(5, 'PASSAGE B')];
  // when
  const groups = TP.groupQuestions(questions);
  // then
  assert.deepStrictEqual(shape(groups), [{ start: 0, ids: [1] }, { start: 1, ids: [2, 3, 4] }, { start: 4, ids: [5] }]);
  assert.strictEqual(groups[1].questions[0], questions[1]); // 문항 객체는 그대로 쓴다
});

test('given 떨어져 있는 같은 지문, when groupQuestions를 부르면, then 이웃한 것만 묶고 떨어진 것은 묶지 않는다', () => {
  // given
  const TP = loadTP();
  const questions = [mc(1, 'SAME'), mc(2, 'OTHER'), mc(3, 'SAME'), mc(4, 'SAME ')];
  // when / then — 끝에 빈칸 하나만 달라도 다른 글이다
  assert.deepStrictEqual(shape(TP.groupQuestions(questions)), [{ start: 0, ids: [1] }, { start: 1, ids: [2] }, { start: 2, ids: [3] }, { start: 3, ids: [4] }]);
});

test('given bodyText가 없는 이웃 문항, when groupQuestions를 부르면, then 묶지 않는다', () => {
  // given
  const TP = loadTP();
  const questions = [mc(1, null), mc(2, null), mc(3, ''), mc(4, '')];
  // when / then
  assert.deepStrictEqual(shape(TP.groupQuestions(questions)).map(g => g.ids.length), [1, 1, 1, 1]);
  assert.deepStrictEqual(plain(TP.groupQuestions([])), []);
  assert.deepStrictEqual(plain(TP.groupQuestions(undefined)), []);
});

test('given 부분 응시로 묶음의 일부만 있을 때, when groupQuestions를 부르면, then 남은 이웃 문항끼리만 묶는다', () => {
  // given — 원래 2·3·4가 한 지문, 5·6이 한 지문인데 오답만 다시 풀기로 2·4·5만 남았다
  const TP = loadTP();
  const questions = [mc(2, 'PASSAGE A'), mc(4, 'PASSAGE A'), mc(5, 'PASSAGE B')];
  // when / then
  assert.deepStrictEqual(shape(TP.groupQuestions(questions)), [{ start: 0, ids: [2, 4] }, { start: 2, ids: [5] }]);
});

test('given 듣기와 읽기가 섞여 있을 때, when groupQuestions를 부르면, then 글이 같아도 듣기·읽기는 따로 묶는다', () => {
  // given
  const TP = loadTP();
  const tts = { audioMode: 'TTS', section: 'LISTENING', taskType: 'TOEIC_P3' };
  const questions = [mc(1, 'SCRIPT', tts), mc(2, 'SCRIPT', tts), mc(3, 'SCRIPT'), mc(4, 'SCRIPT')];
  // when / then
  assert.deepStrictEqual(shape(TP.groupQuestions(questions)), [{ start: 0, ids: [1, 2] }, { start: 2, ids: [3, 4] }]);
});

test('given 같은 글을 쓰는 쓰기·문장 만들기 문항, when groupQuestions를 부르면, then 객관식이 아니므로 묶지 않는다', () => {
  // given
  const TP = loadTP();
  const questions = [
    mc(1, 'PROMPT', { taskType: 'WRITE_EMAIL', choices: [] }), mc(2, 'PROMPT', { taskType: 'WRITE_EMAIL', choices: [] }),
    mc(3, 'PROMPT', { taskType: 'BUILD_SENTENCE', choices: ['a', 'b'] }), mc(4, 'PROMPT', { taskType: 'BUILD_SENTENCE', choices: ['a', 'b'] })];
  // when / then
  assert.deepStrictEqual(shape(TP.groupQuestions(questions)).map(g => g.ids.length), [1, 1, 1, 1]);
});

test('given TOEIC·iBT 묶음, when groupLabel을 부르면, then TOEIC은 실제 번호 범위·iBT는 화면 순서 범위를 쓴다', () => {
  // given
  const TP = loadTP();
  const toeic = { start: 25, questions: [32, 33, 34].map(n => mc(n, 'S', { taskType: 'TOEIC_P3', orderNo: n })) };
  const ibt = { start: 4, questions: [1, 2, 3].map(n => mc(n, 'S', { section: 'LISTENING', taskType: 'LISTEN_TALK' })) };
  // when / then
  assert.strictEqual(TP.groupLabel(toeic, 194), 'Part 3 · Conversations · No. 32–34');
  assert.strictEqual(TP.groupLabel(ibt, 28), 'LISTENING · LISTEN TALK · Q5–7/28');
  assert.strictEqual(TP.groupLabel(ibt), 'LISTENING · LISTEN TALK · Q5–7');
});

// ---------- [[show]] ----------

test('given [[show]] 줄이 있는 bodyText, when splitShown을 부르면, then 앞은 읽을 글·뒤는 보여 줄 시각자료다', () => {
  // given
  const TP = loadTP();
  const body = 'Woman: Which room is free?\nMan: Check the schedule.\n[[show]]\n| Room | Time\n| A | 9:00';
  // when
  const parts = TP.splitShown(body);
  // then
  assert.deepStrictEqual(plain(parts), { spoken: 'Woman: Which room is free?\nMan: Check the schedule.', shown: '| Room | Time\n| A | 9:00' });
});

test('given [[show]]가 없거나 줄 가운데에만 있는 bodyText, when splitShown을 부르면, then 전체가 읽을 글이고 보여 줄 것은 없다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.deepStrictEqual(plain(TP.splitShown('Man: Hello.\nWoman: Hi.')), { spoken: 'Man: Hello.\nWoman: Hi.', shown: '' });
  assert.deepStrictEqual(plain(TP.splitShown('Type [[show]] here.')), { spoken: 'Type [[show]] here.', shown: '' });
  assert.deepStrictEqual(plain(TP.splitShown(null)), { spoken: '', shown: '' });
});

test('given 대본 없이 [[show]]로 시작하거나 CRLF 줄바꿈인 bodyText, when splitShown을 부르면, then 똑같이 나눈다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.deepStrictEqual(plain(TP.splitShown('[[show]]\n[[img:/testprep/img/a.svg]]')), { spoken: '', shown: '[[img:/testprep/img/a.svg]]' });
  assert.deepStrictEqual(plain(TP.splitShown('A. One.\r\n  [[show]]  \r\n| a | b')), { spoken: 'A. One.', shown: '| a | b' });
});

test('given 듣기·읽기 문항, when visibleBody를 부르면, then 듣기는 [[show]] 뒤만·읽기는 전체를 보여 준다', () => {
  // given
  const TP = loadTP();
  const body = 'Man: Hello.\n[[show]]\n| a | b';
  // when / then
  assert.strictEqual(TP.visibleBody({ audioMode: 'TTS', bodyText: body }), '| a | b');
  assert.strictEqual(TP.visibleBody({ audioMode: 'TTS', bodyText: 'Man: Hello.' }), ''); // 대본은 화면에 보여 주지 않는다
  assert.strictEqual(TP.visibleBody({ audioMode: 'NONE', bodyText: 'Passage.' }), 'Passage.');
  assert.strictEqual(TP.visibleBody({ audioMode: 'NONE', bodyText: body }), 'Man: Hello.\n| a | b'); // 읽기는 구분 줄만 뺀다
});

test('given [[show]]가 있는 듣기 문항, when speechText·speechQueue를 부르면, then [[show]] 앞만 읽는다', () => {
  // given
  const TP = loadTP();
  const q = { taskType: 'TOEIC_P3', audioMode: 'TTS', bodyText: 'Man: Hello.\nWoman: Hi.\n[[show]]\n| Room | Time\n| A | 9:00' };
  // when
  const queue = plain(TP.speechQueue(q));
  // then
  assert.strictEqual(TP.speechText(q), 'Man: Hello.\nWoman: Hi.');
  assert.deepStrictEqual(queue.map(p => p.text), ['Hello.', 'Hi.']);
  assert.ok(!JSON.stringify(queue).includes('Room'));
});

test('given TOEIC Part 1 대본과 그림, when speechText를 부르면, then 보기 머리를 "Statement A."로 읽고 그림 줄은 읽지 않는다', () => {
  // given
  const TP = loadTP();
  const q = { taskType: 'TOEIC_P1', audioMode: 'TTS', bodyText: 'A. A man is holding a cup.\nB. He is reading.\nC. A door is open.\nD. Chairs are stacked.\n[[show]]\n[[img:/testprep/img/full02_p1_01.webp]]' };
  // when / then
  assert.strictEqual(TP.speechText(q), 'Statement A. A man is holding a cup.\nStatement B. He is reading.\nStatement C. A door is open.\nStatement D. Chairs are stacked.');
  assert.strictEqual(TP.visibleBody(q), '[[img:/testprep/img/full02_p1_01.webp]]');
});

// ---------- 표·그림 ----------

test('given 표·그림이 없는 지문, when richBody를 부르면, then 이스케이프한 글 그대로다(기존 출력과 같다)', () => {
  // given
  const TP = loadTP();
  const text = 'Questions 147-148 refer to the following notice.\n\n*Offer* valid <today> & "now" — a | b';
  // when / then
  assert.strictEqual(TP.richBody(text), TP.esc(text));
  assert.strictEqual(TP.richBody(null), '');
});

test('given | 표 줄이 있는 지문, when richBody를 부르면, then 글은 이스케이프한 덩어리로·표는 SAT와 같은 표로 그린다', () => {
  // given
  const TP = loadTP();
  const text = 'Look at <the> schedule.\n\n| Room | Time\n| A | 9:00\n| B | **10:30**\n\nThank you.';
  // when
  const html = TP.richBody(text);
  // then
  assert.strictEqual(html, '<div class="tp-text">Look at &lt;the&gt; schedule.</div>'
    + '<table class="sat-table"><thead><tr><th>Room</th><th>Time</th></tr></thead><tbody><tr><td>A</td><td>9:00</td></tr><tr><td>B</td><td><b>10:30</b></td></tr></tbody></table>'
    + '<div class="tp-text">Thank you.</div>');
});

test('given V1135가 쓰는 그림 파일 11개, when richBody로 그리면, then 모두 허용 규칙을 지나 <img>로 나오고 파일도 실제로 있다', () => {
  // given — V1135__testprep_graphics.sql 안의 그림 줄([[img:…]]) 전부
  const TP = loadTP();
  const sql = fs.readFileSync(path.join(__dirname, '../../main/resources/db/migration/V1135__testprep_graphics.sql'), 'utf8');
  const lines = [...new Set(sql.match(/\[\[img:[^\]]*\]\]/g))];
  // when
  const html = lines.map(l => TP.richBody('Title\n' + l));
  // then
  assert.strictEqual(lines.length, 11); // Part 1 6장 + 평면도 1장 + SAT 4장
  lines.forEach((l, i) => {
    const src = l.slice('[[img:'.length, -2);
    assert.ok(html[i].includes('<img class="sat-img" src="' + src + '" alt="">'), l);
    assert.ok(fs.existsSync(path.join(__dirname, '../../main/resources/static', src)), src);
  });
});

test('given 그림 줄이 있는 지문, when richBody를 부르면, then 허용 경로만 <img>로 그리고 그 밖의 경로는 지운다', () => {
  // given
  const TP = loadTP();
  const text = 'Map:\n[[img:/testprep/img/full02_q62.svg]]\n[[img:https://evil.example/x.png]]\n[[img:/testprep/img/../../js/x.svg]]\n[[img:/testprep/img/x.svg" onerror="alert(1)]]\n[[img:javascript:alert(1)]]\nEnd [[img:/testprep/img/inline.png]] stays text.';
  // when
  const html = TP.richBody(text);
  // then
  assert.strictEqual(html, '<div class="tp-text">Map:</div><img class="sat-img" src="/testprep/img/full02_q62.svg" alt="">'
    + '<div class="tp-text">End [[img:/testprep/img/inline.png]] stays text.</div>');
});

test('given 그림 경로, when SATRich.image를 부르면, then /testprep/img/ 아래 svg·webp·png만 그린다', () => {
  // given
  loadTP();
  const ctx = { console }; ctx.globalThis = ctx; vm.createContext(ctx); vm.runInContext(read('sat_rich.js'), ctx);
  const R = ctx.SATRich;
  // when / then
  assert.strictEqual(R.image('/testprep/img/a.svg'), '<img class="sat-img" src="/testprep/img/a.svg" alt="">');
  assert.strictEqual(R.image('/testprep/img/set-01/p1_02.webp'), '<img class="sat-img" src="/testprep/img/set-01/p1_02.webp" alt="">');
  assert.strictEqual(R.image(' /testprep/img/a.png '), '<img class="sat-img" src="/testprep/img/a.png" alt="">');
  ['/testprep/img/a.gif', '/testprep/img/a.svg?x=1', '/img/a.svg', '//evil.example/testprep/img/a.svg', '/testprep/img/a/../b.svg',
    '/testprep/img/a b.svg', '/testprep/img/<b>.svg', 'data:image/svg+xml,x', '', null].forEach(p => assert.strictEqual(R.image(p), '', String(p)));
  assert.strictEqual(R.imageLine('  [[img:/testprep/img/a.svg]] '), '/testprep/img/a.svg');
  assert.strictEqual(R.imageLine('see [[img:/testprep/img/a.svg]]'), null);
});

test('given 그림 줄이 있는 SAT 지문, when SATRich.render를 부르면, then 문단 사이에 그림을 넣고 나쁜 경로는 지운다', () => {
  // given
  const ctx = { console }; ctx.globalThis = ctx; vm.createContext(ctx); vm.runInContext(read('sat_rich.js'), ctx);
  // when
  const html = ctx.SATRich.render('The figure shows *f*.\n[[img:/testprep/img/sat_m1_q3.svg]]\n[[img:/etc/passwd.png]]\nWhat is x?');
  // then
  assert.strictEqual(html, '<p>The figure shows <i>f</i>.</p><img class="sat-img" src="/testprep/img/sat_m1_q3.svg" alt=""><p>What is x?</p>');
});

// ---------- 듣기 1회 재생 ----------

test('given TOEIC·iBT 듣기 문항, when playsOnce를 부르면, then TOEIC만 한 번 재생이다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.strictEqual(TP.playsOnce({ taskType: 'TOEIC_P2' }), true);
  assert.strictEqual(TP.playsOnce({ taskType: 'TOEIC_P1' }), true);
  assert.strictEqual(TP.playsOnce({ taskType: 'LISTEN_CONVERSATION' }), false);
  assert.strictEqual(TP.playsOnce({}), false);
});

test('given 재생 표시, when markPlayed 뒤 isPlayed를 부르면, then sessionStorage에 응시·묶음 첫 문항 기준으로 남는다', () => {
  // given
  const storage = fakeStorage();
  const TP = loadTP(storage);
  const key = TP.playedKey(42, 1001);
  // when
  const before = TP.isPlayed(key);
  TP.markPlayed(key);
  // then
  assert.strictEqual(key, 'tp-played:42:1001');
  assert.strictEqual(before, false);
  assert.strictEqual(TP.isPlayed(key), true);
  assert.strictEqual(storage.data['tp-played:42:1001'], '1');
  assert.strictEqual(TP.isPlayed(TP.playedKey(42, 1002)), false);
  assert.strictEqual(loadTP(storage).isPlayed(key), true); // 새로 고침(화면을 다시 올림) 뒤에도 유지
});

test('given sessionStorage를 쓸 수 없는 브라우저, when markPlayed를 부르면, then 오류 없이 이 화면 동안은 기억한다', () => {
  // given
  const TP = loadTP({ getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); } });
  // when
  TP.markPlayed('k');
  // then
  assert.strictEqual(TP.isPlayed('k'), true);
  assert.strictEqual(TP.isPlayed('other'), false);
});

// ---------- 문장 만들기 칩 섞기 ----------

const CHIPS = ['The', 'library', 'opens', 'at', 'nine', 'on', 'weekdays', '.'];

test('given 같은 seed, when shuffleChips를 부르면, then 언제나 같은 순서이고 원래 배열은 바꾸지 않는다', () => {
  // given
  const TP = loadTP();
  const chips = CHIPS.slice();
  // when
  const a = plain(TP.shuffleChips(chips, 1234));
  const b = plain(loadTP().shuffleChips(chips, 1234));
  // then
  assert.deepStrictEqual(a, b);
  assert.deepStrictEqual(chips, CHIPS);
});

test('given 서로 다른 seed, when shuffleChips를 부르면, then 대체로 다른 순서가 나온다', () => {
  // given
  const TP = loadTP();
  // when
  const orders = new Set();
  for (let seed = 1; seed <= 50; seed++) orders.add(TP.shuffleChips(CHIPS, seed).join(' '));
  // then — 50개 문항 가운데 적어도 40가지 순서
  assert.ok(orders.size >= 40, 'orders: ' + orders.size);
});

test('given 정답 순서로 들어 있는 칩, when shuffleChips를 부르면, then 낱말 묶음은 그대로이고 원래(정답) 순서와는 다르다', () => {
  // given
  const TP = loadTP();
  // when / then — 칩 2개짜리까지 seed 300개 모두
  [CHIPS, ['Yes', '.'], ['I', 'agree', '.']].forEach(chips => {
    for (let seed = 0; seed < 300; seed++) {
      const out = plain(TP.shuffleChips(chips, seed));
      assert.deepStrictEqual(out.slice().sort(), chips.slice().sort());
      assert.notDeepStrictEqual(out, chips, 'seed ' + seed);
    }
  });
});

test('given 같은 낱말 칩이 둘인 문장, when shuffleChips를 부르면, then 중복 낱말을 그대로 둘 다 남긴다', () => {
  // given
  const TP = loadTP();
  const chips = ['She', 'is', 'as', 'tall', 'as', 'her', 'brother', '.'];
  // when
  const out = plain(TP.shuffleChips(chips, 77));
  // then
  assert.strictEqual(out.filter(c => c === 'as').length, 2);
  assert.deepStrictEqual(out.slice().sort(), chips.slice().sort());
  assert.notDeepStrictEqual(out, chips);
});

test('given 칩이 없거나 하나이거나 모두 같은 낱말, when shuffleChips를 부르면, then 그대로 돌려준다', () => {
  // given
  const TP = loadTP();
  // when / then
  assert.deepStrictEqual(plain(TP.shuffleChips([], 1)), []);
  assert.deepStrictEqual(plain(TP.shuffleChips(undefined, 1)), []);
  assert.deepStrictEqual(plain(TP.shuffleChips(['Go'], 1)), ['Go']);
  assert.deepStrictEqual(plain(TP.shuffleChips(['no', 'no', 'no'], 1)), ['no', 'no', 'no']);
  assert.deepStrictEqual(plain(TP.shuffleChips(['a', 'b'], 'not-a-number')), ['b', 'a']); // seed가 숫자가 아니어도 섞는다
});

// ---------- 응시 화면(testprep_run.js)을 가짜 화면에서 실행 ----------

async function bootRun(attempt, storage) {
  const PAGE_IDS = ['stage', 'exam-title', 'sec-tabs', 'timer', 'progress', 'btn-submit', 'prev', 'next', 'save-note'];
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
    speechSynthesis: { cancel() { speech.cancels++; }, speak(u) { speech.spoken.push(u.text); } },
    SpeechSynthesisUtterance: function (text) { this.text = text; },
    URLSearchParams, encodeURIComponent, console
  };
  if (storage) ctx.sessionStorage = storage;
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('sat_rich.js'), ctx);
  vm.runInContext(read('testprep.js'), ctx);
  ctx.TP.loadMe = async () => null;
  ctx.TP.api = async (url, o) => (o && o.method) ? null : attempt;
  vm.runInContext(read('testprep_run.js'), ctx);
  await new Promise(r => setTimeout(r, 10));
  return { speech, el, stage: () => el('stage').innerHTML, click: async id => { await el(id).handlers.click(); await new Promise(r => setTimeout(r, 10)); } };
}

const count = (html, piece) => html.split(piece).length - 1;
const P3_SCRIPT = 'Questions 32 through 34 refer to the following conversation.\n\nMan: Hello.\nWoman: Hi.';
const P7_PASSAGE = 'Questions 147-148 refer to the following notice.\n\nThe pool is closed <today>.';
const listen = (id, no, over) => Object.assign({ questionId: id, section: 'LISTENING', taskType: 'TOEIC_P3', orderNo: no, prompt: 'Listening question ' + no,
  audioMode: 'TTS', choices: ['a', 'b', 'c', 'd'], bodyText: P3_SCRIPT }, over);
const TOEIC_ATTEMPT = { attemptId: 9, kind: 'TOEIC', status: 'IN_PROGRESS', title: 'TOEIC', remainingSec: 600, savedAnswers: { 12: 'B' },
  questions: [
    listen(1, 31, { taskType: 'TOEIC_P2', choices: ['', '', ''], bodyText: 'Where is it?\nA. A desk.\nB. At noon.\nC. Yes.' }),
    listen(11, 32), listen(12, 33), listen(13, 34),
    mc(21, P7_PASSAGE, { orderNo: 147, prompt: 'Reading question 147' }), mc(22, P7_PASSAGE, { orderNo: 148, prompt: 'Reading question 148' }),
    mc(31, null, { taskType: 'TOEIC_P5', orderNo: 101, prompt: 'Single question 101' })] };

test('given 같은 대본을 쓰는 Part 3 세 문항, when 응시 화면이 그 묶음을 그리면, then 재생 버튼 하나와 세 문항이 한 화면에 있고 대본은 보이지 않는다', async () => {
  // given — 첫 화면은 Part 2 단독 문항
  const run = await bootRun(TOEIC_ATTEMPT);
  assert.ok(run.stage().includes('No. 31') && !run.stage().includes('q-group'));
  // when — 다음(묶음 단위 이동)
  await run.click('next');
  // then
  const html = run.stage();
  assert.strictEqual(count(html, 'data-tts'), 1);
  assert.ok(html.includes('q-group q-group-listen'));
  assert.ok(html.includes('Part 3 · Conversations · No. 32–34'));
  ['Listening question 32', 'Listening question 33', 'Listening question 34'].forEach(p => assert.ok(html.includes(p), p));
  ['No. 32', 'No. 33', 'No. 34'].forEach(p => assert.ok(html.includes('Part 3 · Conversations · ' + p + '<'), p));
  assert.ok(!html.includes('Man: Hello.'));
  assert.ok(html.includes('name="q12" value="B" data-q="12" checked')); // 이어 풀기: 저장된 답은 문항별로 복원
  assert.strictEqual(count(html, ' checked'), 1);
  assert.strictEqual(run.el('progress').textContent, '1 / 7 answered'); // 진행 표시는 문항 단위
});

test('given 묶음 화면, when 이전·다음을 누르면, then 묶음 단위로 움직이고 마지막 묶음에서 제출 버튼이 된다', async () => {
  // given
  const run = await bootRun(TOEIC_ATTEMPT);
  // when — 다음 2번: Part 3 묶음 → Part 7 묶음
  await run.click('next');
  await run.click('next');
  // then — 읽기 묶음: 지문 한 번 + 두 문항, 2단용 묶음
  let html = run.stage();
  assert.ok(html.includes('q-group q-group-read'));
  assert.strictEqual(count(html, 'The pool is closed &lt;today&gt;.'), 1);
  assert.ok(html.includes('Reading question 147') && html.includes('Reading question 148'));
  assert.ok(html.includes('Next →'));
  // when — 다음: 마지막 단독 문항(기존 화면 모양)
  await run.click('next');
  html = run.stage();
  assert.ok(html.includes('Single question 101') && !html.includes('q-group'));
  assert.ok(html.includes('Review &amp; Submit'));
  // when — 이전: 다시 Part 7 묶음 전체
  await run.click('prev');
  assert.ok(run.stage().includes('Reading question 147'));
  // when — 이전 2번: 첫 화면에서는 이전 버튼이 꺼져 있다
  await run.click('prev');
  await run.click('prev');
  assert.ok(run.stage().includes('No. 31') && run.stage().includes('id="prev" disabled'));
});

test('given TOEIC 듣기 묶음, when 재생 버튼을 두 번 누르면, then 한 번만 읽고 버튼이 꺼지며 다시 와도 재생할 수 없다', async () => {
  // given
  const storage = fakeStorage();
  const run = await bootRun(TOEIC_ATTEMPT, storage);
  await run.click('next');
  // when
  await run.click('tts-button');
  const spokenOnce = run.speech.spoken.length;
  await run.click('tts-button');
  // then — 묶음 첫 문항(11) 기준으로 표시
  assert.ok(spokenOnce > 0);
  assert.strictEqual(run.speech.spoken.length, spokenOnce);
  assert.strictEqual(run.el('tts-button').disabled, true);
  assert.ok(run.el('tts-button').textContent.includes('이미 재생했습니다'));
  assert.strictEqual(storage.data['tp-played:9:11'], '1');
  // when — 다른 묶음에 갔다가 돌아온다
  await run.click('next');
  await run.click('prev');
  // then — 버튼이 꺼진 채로 그려진다
  assert.ok(run.stage().includes('data-tts disabled'));
  assert.ok(run.stage().includes('이미 재생했습니다'));
  await run.click('tts-button');
  assert.strictEqual(run.speech.spoken.length, spokenOnce);
  // when — 이어 풀기(화면을 새로 연다)
  const again = await bootRun(TOEIC_ATTEMPT, storage);
  await again.click('next');
  // then
  assert.ok(again.stage().includes('data-tts disabled'));
  assert.ok(!(await bootRun(TOEIC_ATTEMPT, storage)).stage().includes('data-tts disabled')); // 재생하지 않은 Part 2 문항은 켜져 있다
});

test('given iBT 듣기 묶음, when 재생 버튼을 여러 번 누르면, then 기존처럼 제한 없이 다시 읽는다', async () => {
  // given
  const ibt = over => listen(over.questionId, 0, Object.assign({ taskType: 'LISTEN_CONVERSATION', bodyText: 'Student: Hello.\nClerk: Hi.' }, over));
  const run = await bootRun({ attemptId: 5, kind: 'PRACTICE', status: 'IN_PROGRESS', title: 'iBT', remainingSec: 600, savedAnswers: {},
    questions: [ibt({ questionId: 1 }), ibt({ questionId: 2 })] }, fakeStorage());
  // when
  await run.click('tts-button');
  await run.click('tts-button');
  // then
  assert.deepStrictEqual(run.speech.spoken, ['Hello.', 'Hi.', 'Hello.', 'Hi.']);
  assert.ok(run.stage().includes('LISTENING · LISTEN CONVERSATION · Q1–2/2'));
  assert.ok(!run.stage().includes('한 번만 재생'));
  assert.strictEqual(run.el('tts-button').disabled, false);
});

test('given [[show]] 뒤에 표가 있는 듣기 문항과 그림이 있는 Part 1 문항, when 응시 화면을 그리면, then 표·그림은 보이고 대본은 보이지 않는다', async () => {
  // given
  const graphic = listen(1, 62, { bodyText: 'Man: Which room?\nWoman: See the schedule.\n[[show]]\n| Room | Time\n| A | 9:00' });
  const photo = listen(2, 1, { taskType: 'TOEIC_P1', choices: ['', '', '', ''], prompt: 'Look at the picture.',
    bodyText: 'A. A man is holding a cup.\nB. He is reading.\nC. A door is open.\nD. Chairs are stacked.\n[[show]]\n[[img:/testprep/img/p1_01.svg]]' });
  const run = await bootRun({ attemptId: 6, kind: 'TOEIC', status: 'IN_PROGRESS', title: 'TOEIC', remainingSec: 600, savedAnswers: {}, questions: [graphic, photo] });
  // when / then — 그래픽 문항
  let html = run.stage();
  assert.ok(html.includes('<div class="q-body q-visual"><table class="sat-table"><thead><tr><th>Room</th><th>Time</th></tr></thead>'));
  assert.ok(!html.includes('Which room?') && !html.includes('[[show]]'));
  await run.click('tts-button');
  assert.deepStrictEqual(run.speech.spoken, ['Which room?', 'See the schedule.']);
  // when / then — Part 1: 그림 + 재생 버튼 + 글 없는 A~D
  await run.click('next');
  html = run.stage();
  assert.ok(html.includes('Part 1 · Photographs · No. 1'));
  assert.ok(html.includes('<img class="sat-img" src="/testprep/img/p1_01.svg" alt="">'));
  assert.ok(!html.includes('holding a cup'));
  assert.strictEqual(count(html, 'data-tts'), 1);
  assert.ok(html.includes('<strong>D.</strong> </span>') && !html.includes('<strong>E.</strong>'));
});

test('given 문장 만들기 문항, when 응시 화면을 그리면, then 칩을 섞어 보여 주고 놓아 둔 칩은 저장된 순서대로 복원한다', async () => {
  // given — 칩이 정답 순서로 들어 있고 "as"가 둘이며, 앞의 두 낱말을 이미 놓았다
  const chips = ['She', 'is', 'as', 'tall', 'as', 'her', 'brother', '.'];
  const q = { questionId: 501, section: 'WRITING', taskType: 'BUILD_SENTENCE', orderNo: 1, prompt: 'Build a sentence.', bodyText: null, audioMode: 'NONE', choices: chips };
  const attempt = saved => ({ attemptId: 3, kind: 'PRACTICE', status: 'IN_PROGRESS', title: 'iBT', remainingSec: 600, savedAnswers: saved, questions: [q] });
  const words = (html, attr) => html.split('data-' + attr + '="').slice(1).map(s => s.split('"')[0]);
  // when
  const fresh = (await bootRun(attempt({}))).stage();
  const resumed = (await bootRun(attempt({ 501: 'She as' }))).stage();
  // then — 처음: 놓은 칩 없음, 풀의 칩은 섞인 순서(정답 순서 아님)이고 낱말 묶음은 같다
  assert.deepStrictEqual(words(fresh, 'placed'), []);
  assert.notDeepStrictEqual(words(fresh, 'word'), chips);
  assert.deepStrictEqual(words(fresh, 'word').sort(), chips.slice().sort());
  assert.deepStrictEqual(words(fresh, 'word'), words((await bootRun(attempt({}))).stage(), 'word')); // 새로 고침해도 같은 순서
  // then — 이어 풀기: 놓은 칩은 저장된 순서, 풀에는 나머지("as" 하나 포함)가 남는다
  assert.deepStrictEqual(words(resumed, 'placed'), ['She', 'as']);
  assert.deepStrictEqual(words(resumed, 'word').sort(), ['is', 'tall', 'as', 'her', 'brother', '.'].sort());
});

// ---------- 복습 화면(testprep_review.js) ----------

async function review(rv) {
  const els = {};
  const el = id => els[id] || (els[id] = { id, innerHTML: '', textContent: '' });
  const ctx = {
    document: { cookie: '', body: { appendChild() {} }, addEventListener() {}, getElementById: el, createElement: () => ({}) },
    location: { pathname: '/testprep/review', search: '?attempt=1', protocol: 'https:' },
    fetch: async () => ({ ok: false, status: 401 }), addEventListener() {}, URLSearchParams, encodeURIComponent, console
  };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('testprep.js'), ctx);
  vm.runInContext(read('sat_rich.js'), ctx);
  ctx.TP.loadMe = async () => null;
  ctx.TP.api = async () => rv;
  vm.runInContext(read('testprep_review.js'), ctx);
  await new Promise(r => setTimeout(r, 10));
  return el('rv-items').innerHTML;
}

const rvItem = (q, over) => Object.assign({}, q, { yourAnswer: 'A', correctAnswer: 'B', isCorrect: false, autoScore: true, explanation: 'Why ' + q.questionId, koHelpJson: null }, over);
const rvBase = (kind, items) => ({ kind, examTitle: 'T', submittedAt: '', overtime: false, totalScaled: 100, totalMax: 990, overallBand: 4, accuracy: 50, bandDisclaimer: 'x', sections: [], items });

test('given 같은 지문·대본을 쓰는 TOEIC 문항들, when 복습 화면을 그리면, then 지문·대본은 묶음마다 한 번만 나오고 문항별 답·해설이 그 아래에 있다', async () => {
  // given
  const items = TOEIC_ATTEMPT.questions.map(q => rvItem(q));
  // when
  const html = await review(rvBase('TOEIC', items));
  // then
  assert.strictEqual(count(html, 'The pool is closed &lt;today&gt;.'), 1);
  assert.strictEqual(count(html, 'Man: Hello.'), 1);
  assert.strictEqual(count(html, '<strong>Script · 대본</strong>'), 2); // Part 2 단독 + Part 3 묶음
  assert.strictEqual(count(html, 'class="rv-group"'), 2);
  assert.ok(html.includes('Part 3 · Conversations · No. 32–34') && html.includes('Part 7 · Reading Comprehension · No. 147–148'));
  assert.strictEqual(count(html, 'review-item'), 7);
  [1, 11, 12, 13, 21, 22, 31].forEach(id => assert.ok(html.includes('💡 Why ' + id + '<'), 'explanation ' + id));
  assert.ok(html.indexOf('The pool is closed') < html.indexOf('Reading question 147'));
  assert.ok(html.indexOf('Reading question 147') < html.indexOf('Reading question 148'));
});

test('given 같은 지문을 쓰는 iBT 문항과 [[show]] 표가 있는 듣기 문항, when 복습 화면을 그리면, then iBT도 지문을 한 번만·듣기는 대본과 표를 함께 보여 준다', async () => {
  // given
  const read1 = mc(1, 'Shared <passage>.', { taskType: 'READ_PASSAGE', prompt: 'iBT one' });
  const read2 = mc(2, 'Shared <passage>.', { taskType: 'READ_PASSAGE', prompt: 'iBT two' });
  const graphic = listen(3, 62, { bodyText: 'Man: Which room?\n[[show]]\n| Room | Time\n| A | 9:00' });
  // when
  const ibt = await review(rvBase('PRACTICE', [rvItem(read1), rvItem(read2)]));
  const toeic = await review(rvBase('TOEIC', [rvItem(graphic)]));
  // then
  assert.strictEqual(count(ibt, 'Shared &lt;passage&gt;.'), 1);
  assert.ok(ibt.includes('READING · READ PASSAGE · Q1–2'));
  assert.ok(ibt.includes('READING · READ PASSAGE · Q2<'));
  assert.ok(toeic.includes('<strong>Script · 대본</strong>\nMan: Which room?</div><div class="q-body q-visual"><table class="sat-table">'));
  assert.ok(!toeic.includes('[[show]]'));
});

test('given 묶이지 않는 문항만 있는 복습(SAT 포함), when 복습 화면을 그리면, then 묶음 칸 없이 기존처럼 문항마다 그린다', async () => {
  // given — SAT은 같은 지문이 이웃해도 묶지 않는다
  const sat = [1, 2].map(id => rvItem(mc(id, 'Same *passage*.', { section: 'RW', taskType: 'SAT_RW_MC' })));
  const ibt = [rvItem(mc(1, 'Only one.', { taskType: 'READ_PASSAGE' })), rvItem(mc(2, null, { taskType: 'VOCAB_E2S' }))];
  // when
  const satHtml = await review(rvBase('SAT', sat));
  const ibtHtml = await review(rvBase('PRACTICE', ibt));
  // then
  assert.ok(!satHtml.includes('rv-group') && !ibtHtml.includes('rv-group'));
  assert.strictEqual(count(satHtml, 'Same <i>passage</i>.'), 2);
  assert.ok(ibtHtml.includes('<div class="q-prompt">Q1</div>\n        <div class="q-body">Only one.</div>'));
});
