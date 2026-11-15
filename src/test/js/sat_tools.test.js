'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js/');

function load(file) {
  const ctx = { console };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(JS + file, 'utf8'), ctx);
  return ctx;
}

const { SATRich } = load('sat_rich.js');
const { SATCalc } = load('sat_calc.js');

test('given 지문에 HTML 태그가 섞여 있을 때, when render하면, then 태그는 글자로 이스케이프된다', () => {
  // given
  const text = '<img src=x onerror=alert(1)> *safe*';
  // when
  const html = SATRich.render(text);
  // then
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(html.includes('<i>safe</i>'));
});

test('given 서식 표기가 있을 때, when inline하면, then 기울임·굵게·위첨자·분수·빈칸으로 바뀐다', () => {
  // given / when
  const html = SATRich.inline('**Text 1** 8,000(1.03)^{*t*} \\frac{5}{13} ______');
  // then
  assert.ok(html.includes('<b>Text 1</b>'));
  assert.ok(html.includes('<sup><i>t</i></sup>'));
  assert.ok(html.includes('<span class="sat-num">5</span><span class="sat-den">13</span>'));
  assert.ok(html.includes('class="sat-blank"'));
});

test('given | 줄과 • 줄이 있을 때, when render하면, then 표와 목록이 만들어진다', () => {
  // given
  const text = 'Intro line\n\n| Club | Hours\n| Drama | 5.5\n\n• first\n• second';
  // when
  const html = SATRich.render(text);
  // then
  assert.ok(html.startsWith('<p>Intro line</p>'));
  assert.ok(html.includes('<thead><tr><th>Club</th><th>Hours</th></tr></thead>'));
  assert.ok(html.includes('<td>Drama</td><td>5.5</td>'));
  assert.ok(html.includes('<ul class="sat-list"><li>first</li><li>second</li></ul>'));
});

test('given 사칙연산과 거듭제곱 우선순위, when evaluate하면, then 수학 규칙대로 계산한다', () => {
  // given / when / then
  assert.strictEqual(SATCalc.evaluate('3(2+5)^2'), 147);
  assert.strictEqual(SATCalc.evaluate('2^3^2'), 512);
  assert.strictEqual(SATCalc.evaluate('-2^2'), -4);
  assert.strictEqual(SATCalc.evaluate('12 ÷ 4 × 3 − 1'), 8);
});

test('given 암묵적 곱과 변수 x, when compile 후 x를 넣으면, then 식 값을 돌려준다', () => {
  // given
  const f = SATCalc.compile('y = 2x^2 - 3x + 1');
  // when / then
  assert.strictEqual(f({ x: 2 }), 3);
  assert.strictEqual(f({ x: 0 }), 1);
});

test('given 함수와 상수, when 도(DEG) 모드로 계산하면, then sin 30° = 0.5', () => {
  // given / when
  const v = SATCalc.evaluate('sin(30)', { deg: true });
  // then
  assert.ok(Math.abs(v - 0.5) < 1e-12);
  assert.ok(Math.abs(SATCalc.evaluate('sqrt(2)π') - Math.SQRT2 * Math.PI) < 1e-12);
  assert.strictEqual(SATCalc.evaluate('ans + 1', { ans: 41 }), 42);
});

test('given 잘못된 식이나 코드, when evaluate하면, then 실행하지 않고 오류를 낸다', () => {
  // given / when / then
  assert.throws(() => SATCalc.evaluate('alert(1)'));
  assert.throws(() => SATCalc.evaluate('(1+2'));
  assert.throws(() => SATCalc.evaluate('1 +'));
  assert.throws(() => SATCalc.evaluate(''));
});

test('given 계산 결과, when format하면, then 군더더기 0 없이 보여 준다', () => {
  // given / when / then
  assert.strictEqual(SATCalc.format(0.1 + 0.2), '0.3');
  assert.strictEqual(SATCalc.format(-2.5), '−2.5');
  assert.strictEqual(SATCalc.format(1 / 0), '∞');
  assert.strictEqual(SATCalc.format(NaN), 'Error');
});
