'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/testprep.js'), 'utf8');

function loadTP() {
  const document = { cookie: '', body: { children: [], appendChild() {} }, addEventListener() {}, getElementById: () => null, createElement: () => ({}) };
  const window = { addEventListener() {} };
  const ctx = { window, document, location: { pathname: '/testprep', search: '', protocol: 'https:' }, fetch: async () => ({ ok: false, status: 401 }), URLSearchParams, encodeURIComponent, console };
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx);
  return window.TP;
}

const delay = ms => new Promise(r => setTimeout(r, ms));

test('given 느린 저장이 진행 중일 때, when 제출 전 flush를 호출하면, then 앞선 저장이 끝난 뒤에 resolve된다', async () => {
  // given
  const TP = loadTP();
  const log = [];
  const flush = TP.serialize(async name => { log.push('start:' + name); await delay(sleepOf(name)); log.push('end:' + name); });
  function sleepOf(n) { return n === 'save-last-answer' ? 60 : 1; }
  // when: 버튼이 flush를 await하지 않고 호출한 뒤, 곧바로 제출 쪽 flush를 호출한다
  flush('save-last-answer');
  await flush('before-submit');
  log.push('submit');
  // then
  assert.deepStrictEqual(log, ['start:save-last-answer', 'end:save-last-answer', 'start:before-submit', 'end:before-submit', 'submit']);
});

test('given 앞선 작업이 실패해도, when 다음 작업을 호출하면, then 계속 실행된다', async () => {
  const TP = loadTP();
  const calls = [];
  const run = TP.serialize(async n => { calls.push(n); if (n === 'a') throw new Error('boom'); return n; });
  await run('a').catch(() => {});
  const result = await run('b');
  assert.strictEqual(result, 'b');
  assert.deepStrictEqual(calls, ['a', 'b']);
});

test('given 인자가 있는 호출, when 직렬화 함수를 호출하면, then 인자를 그대로 전달하고 결과를 돌려준다', async () => {
  const TP = loadTP();
  const run = TP.serialize(async (x, y) => x + y);
  assert.strictEqual(await run(2, 3), 5);
});
