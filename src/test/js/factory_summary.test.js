const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const FS = require(path.resolve(__dirname, '../../main/resources/static/js/factory_summary.js'));

test('given 단계가 섞인 작업 when summarize then 제작·검수 중, 검수 단계, 게시 승인 대기, 게시됨을 센다', () => {
  // given
  const board = { jobs: [
    { stage: 'ANALYSIS', state: 'WAITING' },
    { stage: 'DRAFT', state: 'WORKING' },
    { stage: 'REVIEW', state: 'WORKING' },
    { stage: 'REVISE', state: 'WORKING' },
    { stage: 'PUBLISH', state: 'OWNER_CONFIRM' },
    { stage: 'PUBLISH', state: 'READY', canApprove: true },
    { stage: 'PUBLISH', state: 'PUBLISHED' },
    { stage: null, state: 'CLOSED' }
  ] };
  // when
  const sum = FS.summarize(board);
  // then
  assert.deepEqual(sum, { working: 5, review: 2, approve: 1, published: 1 });
});

test('given 빈 응답·작업 없음 when summarize then 모두 0', () => {
  // given & when & then
  assert.deepEqual(FS.summarize(null), { working: 0, review: 0, approve: 0, published: 0 });
  assert.deepEqual(FS.summarize({ jobs: [] }), { working: 0, review: 0, approve: 0, published: 0 });
});

test('given 요약 칸 when render then 숫자를 채우고 숫자 줄을 보이며, 승인 대기가 있을 때만 알림을 보인다', () => {
  // given
  const cell = () => ({ textContent: '', hidden: true });
  const cells = { working: cell(), approve: cell(), published: cell() };
  const nums = cell(), alert = cell();
  const box = {
    querySelector: sel => { const m = /data-fs="(\w+)"/.exec(sel); return m ? cells[m[1]] || null : sel === '[data-fs-alert]' ? alert : null; },
    querySelectorAll: sel => sel === '[data-fs-nums]' ? [nums] : []
  };
  // when
  FS.render(box, { working: 3, review: 1, approve: 2, published: 4 });
  // then
  assert.equal(cells.working.textContent, '3');
  assert.equal(cells.approve.textContent, '2');
  assert.equal(cells.published.textContent, '4');
  assert.equal(nums.hidden, false);
  assert.equal(alert.hidden, false);
  FS.render(box, { working: 0, review: 0, approve: 0, published: 0 });
  assert.equal(alert.hidden, true);
});
