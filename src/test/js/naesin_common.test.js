'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js/');

function load() {
  const ctx = { console };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(JS + 'naesin_common.js', 'utf8'), ctx);
  return ctx.NT;
}
const NT = load();

test('given 내신 문항 그림 표기, when imgPath하면, then /naesin 폴더의 webp 경로만 돌려준다', () => {
  // given
  const ok = '[[img:/naesin/apgujeong-m2-2026-2/r01/q01.webp]]';
  // when / then
  assert.strictEqual(NT.imgPath(ok), '/naesin/apgujeong-m2-2026-2/r01/q01.webp');
  assert.strictEqual(NT.imgPath('[[img:https://evil.example/x.webp]]'), null);
  assert.strictEqual(NT.imgPath('[[img:/naesin/../secret.webp]]'), null);
  assert.strictEqual(NT.imgPath('[[img:/naesin/a.png]]'), null);
  assert.strictEqual(NT.imgPath(null), null);
});

test('given 로그인 후 돌아갈 주소, when safeReturn하면, then /naesintest 안쪽 주소만 허용한다', () => {
  assert.strictEqual(NT.safeReturn('/naesintest/run?attempt=3'), '/naesintest/run?attempt=3');
  assert.strictEqual(NT.safeReturn('/naesintest'), '/naesintest');
  assert.strictEqual(NT.safeReturn('//evil.example'), null);
  assert.strictEqual(NT.safeReturn('/naesintestx'), null);
  assert.strictEqual(NT.safeReturn('/naesintest/../admin'), null);
});

test('given 남은 초, when fmtTime하면, then 분:초 두 자리로 나오고 음수는 00:00이다', () => {
  assert.strictEqual(NT.fmtTime(1200), '20:00');
  assert.strictEqual(NT.fmtTime(65), '01:05');
  assert.strictEqual(NT.fmtTime(-3), '00:00');
});

test('given 답 글자, when circ하면, then 동그라미 번호가 나온다', () => {
  assert.strictEqual(NT.circ('A'), '①');
  assert.strictEqual(NT.circ('E'), '⑤');
  assert.strictEqual(NT.circ(null), '');
});

test('given 해설 문자열, when expHtml하면, then 첫 줄·근거 줄을 구분하고 HTML은 이스케이프한다', () => {
  // given
  const text = '정답 ② · 어법 (4점)\n<b>주격</b> 관계대명사다.\n근거: 필기본 05쪽\n어법 포인트: 관계대명사 that';
  // when
  const html = NT.expHtml(text);
  // then
  assert.ok(html.startsWith('<p class="nt-exp-head">정답 ②'));
  assert.ok(html.includes('&lt;b&gt;주격'));
  assert.strictEqual((html.match(/nt-exp-tag/g) || []).length, 2);
});

test('given 순서대로 실행할 비동기 작업, when serialize로 감싸 연달아 부르면, then 부른 순서대로 끝난다', async () => {
  // given
  const done = [];
  const run = NT.serialize((n, ms) => new Promise(r => setTimeout(() => { done.push(n); r(); }, ms)));
  // when
  await Promise.all([run(1, 30), run(2, 0)]);
  // then
  assert.deepStrictEqual(done, [1, 2]);
});

test('given 시험 코드, when schoolUrl하면, then 그 학교 페이지 주소가 나오고 모르는 코드는 학교 고르기로 간다', () => {
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-APM2-2602-R01'), '/naesintest/school/apgujeong-m2');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-SGM2-2602-R03'), '/naesintest/school/singu-m2');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-APM1-2602-R01'), '/naesintest/school/apgujeong-m1');
  assert.strictEqual(NT.schoolUrl('TP-SAT-MINI-01'), '/naesintest');
  assert.strictEqual(NT.schoolUrl(null), '/naesintest');
});
