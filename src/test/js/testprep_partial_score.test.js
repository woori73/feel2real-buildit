'use strict';
// SAT "오답만 다시 풀기"(부분 응시)는 추정 점수 대신 정답 수를 보여 주는지, 복습 화면의 해설이 줄바꿈을 줄로 보여 주는지 검사한다.
// 결과 화면 조각(TP.satScoreHtml)은 순수 함수로, 복습(testprep_review.js)·내 점수(testprep_scores.js)는 가짜 화면에서 실제로 실행한다.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(JS, file), 'utf8');
const SAT_NAMES = { RW: 'Reading and Writing', MATH: 'Math' };

// testprep.js(+ SAT 서식 sat_rich.js)를 올린 가짜 화면. apiResult = TP.api가 돌려줄 값.
function page(search, apiResult) {
  const els = {};
  const el = id => els[id] || (els[id] = { id, innerHTML: '', textContent: '' });
  const ctx = {
    document: { cookie: '', body: { appendChild() {} }, addEventListener() {}, getElementById: el, createElement: () => ({}) },
    location: { pathname: '/testprep/review', search, protocol: 'https:' },
    fetch: async () => ({ ok: false, status: 401 }),
    addEventListener() {}, URLSearchParams, encodeURIComponent, console
  };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('testprep.js'), ctx);
  vm.runInContext(read('sat_rich.js'), ctx);
  ctx.TP.user = { loginId: 'student' };
  ctx.TP.loadMe = async () => ctx.TP.user;
  ctx.TP.api = async () => apiResult;
  return { ctx, el, run: async file => { vm.runInContext(read(file), ctx); await new Promise(r => setTimeout(r, 10)); } };
}

const item = over => Object.assign({ questionId: 1, section: 'RW', taskType: 'SAT_RW_MC', prompt: 'Which choice?', bodyText: null, audioMode: 'NONE',
  choices: ['a', 'b', 'c', 'd'], yourAnswer: 'A', correctAnswer: 'A', isCorrect: true, autoScore: true, explanation: 'Because.', orderNo: 1, koHelpJson: null }, over);

test('given 영역별 정답 수, when correctCountText를 부르면, then 맞힌 수와 자동 채점 문항 수를 더해 "n / m"으로 쓴다', () => {
  // given
  const { ctx } = page('', null);
  const sections = [{ section: 'RW', correct: 2, autoCount: 3 }, { section: 'MATH', correct: 1, autoCount: 2 }];
  // when / then
  assert.strictEqual(ctx.TP.correctCountText(sections), '3 / 5');
  assert.strictEqual(ctx.TP.correctCountText([]), '0 / 0');
  assert.strictEqual(ctx.TP.correctCountText(undefined), '0 / 0');
});

test('given SAT 전체 응시 결과, when satScoreHtml을 부르면, then 기존처럼 합계(/ 1600)와 영역 추정 점수·정답 수를 그린다', () => {
  // given
  const { ctx } = page('', null);
  const card = { totalScaled: 1330, sections: [
    { section: 'RW', scaled: 680, correct: 8, autoCount: 10 }, { section: 'MATH', scaled: 650, correct: 6, autoCount: 8 }] };
  // when
  const html = ctx.TP.satScoreHtml(card, SAT_NAMES);
  // then
  assert.strictEqual(html, '<h2>Your practice score</h2><p class="ko">연습 점수(추정)</p>'
    + '<div class="sat-total">1330<small>/ 1600</small></div>'
    + '<div class="sat-score-row">'
    + '<div class="sat-score-sec"><span>Reading and Writing</span><b>680</b><em>8 / 10 correct</em></div>'
    + '<div class="sat-score-sec"><span>Math</span><b>650</b><em>6 / 8 correct</em></div></div>');
});

test('given 오답만 다시 푼 SAT 결과(추정 점수 없음), when satScoreHtml을 부르면, then 800·1600 대신 정답 수("n / m correct")를 보여 준다', () => {
  // given — 틀린 3문항을 다시 풀어 모두 맞혔고 서버가 scaled를 비운 결과
  const { ctx } = page('', null);
  const card = { totalScaled: null, sections: [
    { section: 'RW', scaled: null, scaleMax: null, correct: 2, autoCount: 2 }, { section: 'MATH', scaled: null, scaleMax: null, correct: 1, autoCount: 1 }] };
  // when
  const html = ctx.TP.satScoreHtml(card, SAT_NAMES);
  // then
  assert.ok(html.includes('<div class="sat-total">3 / 3<small>correct</small></div>'));
  assert.ok(html.includes('<span>Reading and Writing</span><b>2 / 2</b><em>correct</em>'));
  assert.ok(html.includes('<span>Math</span><b>1 / 1</b><em>correct</em>'));
  assert.ok(html.includes('오답만 다시 풀기'));
  assert.ok(!html.includes('1600') && !html.includes('800') && !html.includes('Your practice score'));
});

test('given SAT 시험 화면(sat_run.js), when 결과 화면을 그리면, then 점수 부분을 TP.satScoreHtml에 맡긴다', () => {
  // given / when
  const src = read('sat_run.js');
  // then — 화면 전체는 가짜 화면에서 실행하지 못하므로 연결만 확인한다
  assert.ok(src.includes("'<div class=\"sat-done\">' + TP.satScoreHtml(card, SECTION_NAME)"));
  assert.ok(!src.includes('/ 1600'));
});

test('given 오답만 다시 푼 SAT 응시, when 복습 화면을 열면, then 합계 자리에 정답 수를 쓰고 "—"를 합계로 쓰지 않는다', async () => {
  // given
  const p = page('?attempt=7', { kind: 'SAT', examTitle: 'SAT Mini', submittedAt: '2026-11-22T10:00:00', overtime: false, totalScaled: null, totalMax: null,
    bandDisclaimer: 'x', sections: [{ section: 'RW', scaled: null, correct: 2, autoCount: 3 }], items: [item({})] });
  p.el('rv-items');
  // when
  await p.run('testprep_review.js');
  // then
  const score = p.el('rv-score').innerHTML;
  assert.ok(score.includes('<b>2 / 3 correct</b>'));
  assert.ok(score.includes('오답만 다시 풀기'));
  assert.ok(!score.includes('400–1600'));
  assert.ok(score.includes('Reading and Writing · 2/3'));
});

test('given SAT 전체 응시, when 복습 화면을 열면, then 기존처럼 합계와 영역 추정 점수를 쓴다', async () => {
  // given
  const p = page('?attempt=8', { kind: 'SAT', examTitle: 'SAT Mini', submittedAt: '', overtime: false, totalScaled: 1330, totalMax: 1600,
    bandDisclaimer: 'x', sections: [{ section: 'RW', scaled: 680, correct: 8, autoCount: 10 }, { section: 'MATH', scaled: 650, correct: 6, autoCount: 8 }], items: [item({})] });
  // when
  await p.run('testprep_review.js');
  // then
  const score = p.el('rv-score').innerHTML;
  assert.ok(score.includes('<div class="stat"><b>1330</b><span>total (estimate, 400–1600)'));
  assert.ok(score.includes('<b>680</b><span>Reading and Writing · 8/10</span>'));
  assert.ok(!score.includes('correct</b>'));
});

test('given 줄바꿈 뒤에 "포인트:" 줄이 붙은 TOEIC 해설, when 복습 화면을 그리면, then 해설 문단이 줄바꿈을 줄로 보여 주고(pre-line) 글은 그대로다', async () => {
  // given
  const explanation = '[Part5-품사] 정답 (B). 근거 …\n포인트: 명사 자리 <b>';
  const p = page('?attempt=9', { kind: 'TOEIC', examTitle: 'TOEIC RC', submittedAt: '', overtime: false, totalScaled: 395, totalMax: 495, bandDisclaimer: 'x',
    sections: [{ section: 'READING', scaled: 395, scaleMax: 495, correct: 80, autoCount: 100 }],
    items: [item({ section: 'READING', taskType: 'TOEIC_P5', orderNo: 101, explanation })] });
  // when
  await p.run('testprep_review.js');
  // then — 줄바꿈 글자는 남기고 태그는 이스케이프한다
  const html = p.el('rv-items').innerHTML;
  assert.ok(html.includes('white-space:pre-line">💡 [Part5-품사] 정답 (B). 근거 …\n포인트: 명사 자리 &lt;b&gt;</p>'));
});

test('given 줄바꿈이 든 SAT·iBT 해설, when 복습 화면을 그리면, then 같은 문단에 서식(SAT)·이스케이프(iBT)가 그대로 적용된다', async () => {
  // given
  const sat = page('?attempt=1', { kind: 'SAT', examTitle: 'SAT', submittedAt: '', overtime: false, totalScaled: 800, bandDisclaimer: 'x',
    sections: [{ section: 'MATH', scaled: 800, correct: 1, autoCount: 1 }], items: [item({ section: 'MATH', explanation: 'Choice A is *best*.\nx^{2} = 4' })] });
  const ibt = page('?attempt=2', { kind: 'PRACTICE', examTitle: 'iBT', submittedAt: '', overtime: false, overallBand: 5, accuracy: 100, bandDisclaimer: 'x',
    sections: [{ section: 'READING', band: 5, correct: 1, autoCount: 1 }], items: [item({ section: 'READING', taskType: 'READ_PASSAGE', explanation: 'Line one.\nLine <two>.' })] });
  // when
  await sat.run('testprep_review.js');
  await ibt.run('testprep_review.js');
  // then
  assert.ok(sat.el('rv-items').innerHTML.includes('white-space:pre-line">💡 Choice A is <i>best</i>.\nx<sup>2</sup> = 4</p>'));
  assert.ok(ibt.el('rv-items').innerHTML.includes('white-space:pre-line">💡 Line one.\nLine &lt;two&gt;.</p>'));
});

test('given SAT 전체·부분 응시와 TOEIC 부분 응시 기록, when 내 점수 화면을 그리면, then SAT 부분 응시만 점수 대신 "n / m correct"를 쓴다', async () => {
  // given
  const row = over => Object.assign({ attemptId: 1, examTitle: 'SAT Mini', kind: 'SAT', status: 'SUBMITTED', startedAt: '2026-11-22T10:00:00', overtime: false,
    overallBand: null, accuracy: 100, questionCount: 3, totalScaled: null, totalMax: null, correctCount: null, autoCount: null }, over);
  const p = page('', [
    row({ attemptId: 1, totalScaled: 1330, totalMax: 1600, correctCount: 14, autoCount: 18, questionCount: 18 }),
    row({ attemptId: 2, correctCount: 3, autoCount: 3 }),
    row({ attemptId: 3, kind: 'TOEIC', correctCount: 2, autoCount: 2 }),
    row({ attemptId: 4, status: 'IN_PROGRESS' })]);
  // when
  await p.run('testprep_scores.js');
  // then
  const cells = p.el('score-list').innerHTML.split('<tr>').slice(2).map(r => r.split('<td>')[5].split('</td>')[0]);
  assert.strictEqual(cells[0], '<strong>1330</strong> <span style="font-size:12px;color:var(--muted)">/ 1600 est.</span>');
  assert.strictEqual(cells[1], '<strong>3 / 3</strong> <span style="font-size:12px;color:var(--muted)">correct</span>');
  assert.strictEqual(cells[2], '—'); // TOEIC 부분 응시는 기존 표기 그대로
  assert.strictEqual(cells[3], '—'); // 풀고 있는 SAT 응시
});
