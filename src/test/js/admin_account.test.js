const test = require('node:test');
const assert = require('node:assert/strict');
const { validate } = require('../../main/resources/static/js/admin_account.js');

const ok = { currentPassword: 'current-password-value', newLoginId: 'owner-console-7391', newPassword: 'a-long-new-password-0042', confirmPassword: 'a-long-new-password-0042' };

test('given 유효한 입력 when 검증하면 then 통과(null)한다', () => {
  assert.equal(validate(ok), null);
});

test('given 빈 값 when 검증하면 then 모든 항목 입력 안내를 돌려준다', () => {
  assert.match(validate({ ...ok, newLoginId: '' }), /모든 항목/);
});

test('given 형식이 틀린 ID(짧음·공백·특수문자) when 검증하면 then 거부한다', () => {
  for (const id of ['abc', 'has space1', '-startdash1', 'a'.repeat(51), '한글아이디입니다1']) {
    assert.match(validate({ ...ok, newLoginId: id }), /로그인 ID/, id);
  }
});

test('given 확인값 불일치·16자 미만·ID 포함·현재와 동일 when 검증하면 then 각각 거부한다', () => {
  assert.match(validate({ ...ok, confirmPassword: 'different-password-0042' }), /일치/);
  assert.match(validate({ ...ok, newPassword: 'short-pw-15char', confirmPassword: 'short-pw-15char' }), /16자/);
  const withId = 'x-' + ok.newLoginId + '-yyyy';
  assert.match(validate({ ...ok, newPassword: withId, confirmPassword: withId }), /포함/);
  assert.match(validate({ ...ok, newPassword: ok.currentPassword + '-zz', confirmPassword: ok.currentPassword + '-zz', currentPassword: ok.currentPassword + '-zz' }), /달라야/);
});
