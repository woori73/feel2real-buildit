const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/easy/tutor_agent.html'), 'utf8');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/easy_tutor_agent.js'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/easy_tutor_agent.css'), 'utf8');

test('ledger starts with monthly dashboard and exposes practical book surfaces', () => {
  [
    'summaryIncome', 'summaryPurchase', 'summaryExpense', 'summaryProfit',
    'incomeBreakdown', 'expenseBreakdown', 'attentionList', 'recentTransactions',
    'transactionTableBody', 'transactionCards', 'journalList',
    'trialBalanceButton', 'bookResult'
  ].forEach(id => assert.match(html, new RegExp(`id="${id}"`)));

  assert.match(html, /이달 현황/);
  assert.match(html, /수입·지출 장부/);
  assert.match(html, /복식부기 장부/);
  assert.match(html, /고급 회계/);
  assert.match(html, /수강료·교재비/);
  assert.match(html, /강사료·임차료·운영비/);
});

test('AI helper stays contextual and all writes use explicit approval', () => {
  ['agentPanel', 'agentToggle', 'chatMessages', 'chatForm'].forEach(id => {
    assert.match(html, new RegExp(`id="${id}"`));
  });
  assert.match(html, /23번 거래를 교재비로 수정해 줘/);
  assert.match(js, /\/api\/tutor\/easy/);
  assert.match(js, /\/agent\/actions\//);
  assert.match(js, /decideAction\(card,\s*'approve'\)/);
  assert.match(js, /decideAction\(card,\s*'reject'\)/);
  assert.doesNotMatch(js, /\/api\/admin\/easy/);
  assert.doesNotMatch(js, /method:\s*['"]DELETE['"]/);
  assert.doesNotMatch(js, /occurredOn\s*:/);
  assert.doesNotMatch(js, /amount\s*:/);
});

test('dashboard, transaction and journal reads remain tenant-scoped', () => {
  assert.match(js, /\/workspace\/monthly/);
  assert.match(js, /\/workspace\/transactions/);
  assert.match(js, /\/workspace\/reviews/);
  assert.match(js, /\/accounting\/journal/);
  assert.match(js, /businessId/);
});

test('responsive layout switches from desktop table to mobile transaction cards', () => {
  assert.match(css, /grid-template-columns:minmax\(0,1fr\) 370px/);
  assert.match(css, /@media\(max-width:760px\)/);
  assert.match(css, /\.ledger-table-wrap\{display:none\}/);
  assert.match(css, /\.transaction-cards\{display:grid/);
  assert.match(css, /@media\(max-width:390px\)/);
});
