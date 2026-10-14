const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/tutor_agent.html'), 'utf8');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/easy_tutor_agent.js'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/easy_tutor_agent.css'), 'utf8');

test('tutor Easy screen exposes chat, CSV, proposal, and ledger surfaces', () => {
  ['chatMessages', 'chatForm', 'uploadForm', 'batchList', 'bookResult', 'trialBalanceButton'].forEach(id => {
    assert.match(html, new RegExp(`id="${id}"`));
  });
  assert.match(html, /이번 달 매출과 지출 알려줘/);
  assert.match(html, /아직 분류하지 않은 거래 보여줘/);
  assert.match(html, /장부에 반영해줘/);
});

test('all writes use tutor Agent approval contract and never mutate protected source fields', () => {
  assert.match(js, /\/api\/tutor\/easy/);
  assert.match(js, /\/agent\/actions\//);
  assert.match(js, /decideAction\(card, 'approve'\)/);
  assert.match(js, /decideAction\(card, 'reject'\)/);
  assert.doesNotMatch(js, /\/api\/admin\/easy/);
  assert.doesNotMatch(js, /method:\s*['"]DELETE['"]/);
  assert.doesNotMatch(js, /occurredOn\s*:/);
  assert.doesNotMatch(js, /amount\s*:/);
});

test('responsive layout has desktop and mobile breakpoints', () => {
  assert.match(css, /@media \(min-width: 900px\)/);
  assert.match(css, /@media \(max-width: 560px\)/);
  assert.match(css, /grid-template-columns:minmax\(0,1fr\) 360px/);
});
