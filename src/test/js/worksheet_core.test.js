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
  assert.ok(a.includes('<div class="ws-q-no">3</div>') && a.includes('② </span> goes') === false && a.includes('goes'));
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
