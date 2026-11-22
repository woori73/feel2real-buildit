const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const LH = require(path.resolve(__dirname, '../../main/resources/static/js/learn_hub.js'));

test('given 아무것도 고르지 않음 when matches then 모든 카드가 보인다', () => {
  // given
  const card = { audiences: '중2', school: '압구정중학교' };
  // when & then
  assert.equal(LH.matches(card, '', ''), true);
  assert.equal(LH.matches({ audiences: '고3·수능', school: '' }, '', ''), true);
});

test('given 학년을 고름 when matches then 그 학년이 든 카드만 보인다', () => {
  // given
  const card = { audiences: '예비고1,고2,고3·수능', school: '' };
  // when & then
  assert.equal(LH.matches(card, '고2', ''), true);
  assert.equal(LH.matches(card, '중2', ''), false);
});

test('given 학교를 고름 when matches then 그 학교 카드만 보이고 학교 없는 카드는 숨는다', () => {
  // given
  const school = { audiences: '중2', school: '신구중학교' };
  const general = { audiences: '중1,중2', school: '' };
  // when & then
  assert.equal(LH.matches(school, '', '신구중학교'), true);
  assert.equal(LH.matches(school, '', '압구정중학교'), false);
  assert.equal(LH.matches(general, '', '신구중학교'), false);
  assert.equal(LH.matches(school, '중1', '신구중학교'), false);
});
