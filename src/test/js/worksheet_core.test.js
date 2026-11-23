const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const WS = require(path.resolve(__dirname, '../../main/resources/static/js/worksheet_core.js'));
const q = (ref, setKey = 'S1', type = '어법') => ({ ref, setKey, type });

test('given 후보 5개 중 2개를 이미 고름 when remaining then 남은 3개만 준다', () => {
  // given
  const candidates = ['a', 'b', 'c', 'd', 'e'].map(r => q(r));
  // when
  const left = WS.remaining(candidates, ['b', 'd']);
  // then
  assert.deepEqual(left.map(x => x.ref), ['a', 'c', 'e']);
});

test('given 남은 후보 4개 when 2개 pick then 고르지 않은 것에서만 뽑고 원래 순서로 돌려준다', () => {
  // given
  const candidates = ['a', 'b', 'c', 'd', 'e'].map(r => q(r));
  // when
  const got = WS.pick(candidates, ['a'], 2, () => 0);
  // then
  assert.equal(got.length, 2);
  assert.ok(got.every(x => x.ref !== 'a'));
  assert.deepEqual(got.map(x => x.ref), got.map(x => x.ref).slice().sort());
});

test('given 남은 후보보다 많은 수·0·음수 when pick then 남은 만큼만, 0개는 빈 목록', () => {
  // given
  const candidates = ['a', 'b'].map(r => q(r));
  // when & then
  assert.equal(WS.pick(candidates, [], 10).length, 2);
  assert.equal(WS.pick(candidates, [], 0).length, 0);
  assert.equal(WS.pick(candidates, [], -3).length, 0);
});

test('given 목록 when move then 위·아래로 한 칸 옮기고 끝에서는 그대로', () => {
  // given
  const list = ['a', 'b', 'c'];
  // when & then
  assert.deepEqual(WS.move(list, 1, -1), ['b', 'a', 'c']);
  assert.deepEqual(WS.move(list, 1, 1), ['a', 'c', 'b']);
  assert.deepEqual(WS.move(list, 0, -1), ['a', 'b', 'c']);
  assert.deepEqual(list, ['a', 'b', 'c']);
});

test('given 같은 묶음·같은 유형 후보 when replacement then 아직 쓰지 않은 첫 문항, 없으면 null', () => {
  // given
  const current = q('a');
  const candidates = [q('a'), q('b'), q('c', 'S2'), q('d', 'S1', '독해'), q('e')];
  // when & then
  assert.equal(WS.replacement(current, candidates, ['a', 'b']).ref, 'e');
  assert.equal(WS.replacement(current, candidates, ['a', 'b', 'e']), null);
});

test('given 그림 표시와 꺾쇠가 섞인 글 when rich then 내신 그림만 img로 바꾸고 나머지는 글자 그대로', () => {
  // given
  const text = '[[img:/naesin/apgujeong-m2-2026-2/r01/q01.webp]]\n<script>x</script> [[img:https://evil.example/x.png]]';
  // when
  const html = WS.rich(text);
  // then
  assert.ok(html.includes('<img class="ws-q-img" loading="lazy" alt="문항 그림" src="/naesin/apgujeong-m2-2026-2/r01/q01.webp">'));
  assert.ok(html.includes('&lt;script&gt;'));
  assert.ok(!html.includes('evil.example/x.png"'));
  assert.ok(html.includes('<br>'));
});

test('given 글 문항·그림 문항 when questionHtml then 글 문항은 선택지를 적고 그림 문항은 번호뿐인 선택지를 생략한다', () => {
  // given
  const text = { source: 'GRAMMAR', stem: '빈칸에 알맞은 것은?', body: 'He ___ home.', choices: ['go', 'goes', 'going', 'gone', 'to go'] };
  const image = { source: 'NAESIN', stem: '[[img:/naesin/s/r01/q01.webp]]', body: null, choices: ['①', '②', '③', '④', '⑤'] };
  // when
  const a = WS.questionHtml(text, 3);
  const b = WS.questionHtml(image, 4);
  // then
  assert.ok(a.includes('<div class="ws-q-no">03</div>') && a.includes('id="q3"') && a.includes('② </span> goes') === false && a.includes('goes'));
  assert.ok(a.indexOf('빈칸에 알맞은') < a.indexOf('He ___ home.'));
  assert.ok(b.includes('<img') && !b.includes('ws-q-choices'));
});

test('given 배정 수 when assignSummary then 상태별 수를 한 줄로, 없으면 배정 없음', () => {
  // given & when & then
  assert.equal(WS.assignSummary({ notStarted: 2, inProgress: 1, done: 3 }), '배정 6명 · 시작 전 2 · 진행 중 1 · 완료 3');
  assert.equal(WS.assignSummary({ notStarted: 0, inProgress: 0, done: 0 }), '배정 없음');
  assert.equal(WS.day('2026-11-22T10:05:00'), '11월 22일');
  assert.equal(WS.esc('<a href="x">'), '&lt;a href=&quot;x&quot;&gt;');
});

test('given 유형이 치우친 후보 when pickBalanced then 유형을 돌아가며 고르게 뽑는다', () => {
  // given
  const candidates = ['a', 'b', 'c', 'd', 'e', 'f'].map(r => q(r)).concat([q('x', 'S1', '독해'), q('y', 'S1', '어휘')]);
  // when
  const got = WS.pickBalanced(candidates, [], 3, () => 0);
  // then
  assert.deepEqual(got.map(x => x.type).sort(), ['독해', '어법', '어휘']);
  assert.equal(WS.pickBalanced(candidates, [], 20).length, 8);
});

test('given 문항 묶음 when stats then 난이도·유형별 수를 세고 난이도 없는 문항은 없음으로 센다', () => {
  // given
  const list = [{ type: '어법', level: '상' }, { type: '어법', level: null }, { type: '독해', level: '하' }];
  // when
  const s = WS.stats(list);
  // then
  assert.equal(s.total, 3);
  assert.deepEqual(s.levels, { 하: 1, 중: 0, 상: 1, 없음: 1 });
  assert.deepEqual(s.types, { 어법: 2, 독해: 1 });
});

test('given 내신 회차와 어법 묶음 when tree then 내신은 학교·학년 → Lesson → 회차, 어법은 단원 → 1·2부, 마무리는 한 묶음', () => {
  // given
  const sets = [
    { setKey: 'N1', source: 'NAESIN', group: '압구정중 2학년', title: '압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회' },
    { setKey: 'N2', source: 'NAESIN', group: '압구정중 2학년', title: '압구정중 2학년 2학기 기말 6회 · Lesson 8 예상문제 1회' },
    { setKey: 'G1', source: 'GRAMMAR', group: '예비고1 어법', title: 'CH 1 · 수일치·시제 일치 · 1부' },
    { setKey: 'G2', source: 'GRAMMAR', group: '예비고1 어법', title: '마무리 종합 모의고사 1회' }];
  // when
  const tree = WS.tree(sets);
  // then
  assert.deepEqual(tree.map(g => g.name), ['압구정중 2학년', 'CH 1 · 수일치·시제 일치', '마무리 종합']);
  assert.deepEqual(tree[0].children.map(c => c.name), ['Lesson 7', 'Lesson 8']);
  assert.equal(tree[0].children[0].sets[0].leaf, '2학기 기말 1회');
  assert.equal(tree[1].children[0].sets[0].leaf, '1부');
});

test('given 수능·TOEIC 묶음 when tree then 학년도·종류(group)를 맨 위에, 시험 이름을 잎으로 둔다', () => {
  // given
  const sets = [
    { setKey: 'CSAT:5', source: 'CSAT', group: '2026학년도', title: '2026학년도 대학수학능력시험 영어', questionCount: 28 },
    { setKey: 'CSAT:6', source: 'CSAT', group: '2026학년도', title: '2026학년도 6월 모의평가 영어', questionCount: 28 },
    { setKey: 'TOEIC:TP-TOEIC-RC-01', source: 'TOEIC', group: 'TOEIC 독해 연습', title: 'TOEIC Reading Practice 01', questionCount: 100 }
  ];
  // when
  const tree = WS.tree(sets);
  // then
  assert.deepEqual(tree.map(g => g.name), ['2026학년도', 'TOEIC 독해 연습']);
  assert.deepEqual(tree[0].children[0].sets.map(s => s.leaf), ['2026학년도 대학수학능력시험 영어', '2026학년도 6월 모의평가 영어']);
});

test('given TOEIC 문항(지문 있음)·수능 선택지에 번호가 붙음·SAT 그림 when questionHtml then 지문이 발문 앞, 번호는 한 번만, testprep 그림만 그린다', () => {
  // given
  const toeic = { ref: 'TOEIC:1', source: 'TOEIC', stem: 'What is offered?', body: 'Questions 147-148 notice', choices: ['A', 'B', 'C', 'D'] };
  const csat = { ref: 'CSAT:5-18', source: 'CSAT', stem: '목적은?', body: '', choices: ['① to thank', '② to ask'] };
  const sat = { ref: 'SAT:1', source: 'SAT', stem: 'Which?', body: '[[img:/testprep/img/sat_prac01_q12.svg]] [[img:https://evil.example/x.svg]]', choices: ['a', 'b', 'c', 'd'] };
  // when
  const t = WS.questionHtml(toeic, 1), c = WS.questionHtml(csat, 2), s = WS.questionHtml(sat, 3);
  // then
  assert.ok(t.indexOf('Questions 147-148') < t.indexOf('What is offered?'));
  assert.ok(c.includes('<span>①</span> to thank') && !c.includes('① ①'));
  assert.ok(s.includes('src="/testprep/img/sat_prac01_q12.svg"') && !s.includes('evil.example/x.svg"'));
});
