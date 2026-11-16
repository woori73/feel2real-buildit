const { test } = require('node:test');
const assert = require('node:assert/strict');
const V = require('../../main/resources/static/js/admin_members_view.js');
const P = require('../../main/resources/static/js/password_change_required.js');

test('canIssue: 관리자 계정·userId 없는 줄에는 버튼을 두지 않는다', () => {
  // given
  const student = { userId: 1, role: 'STUDENT' };
  const admin = { userId: 2, role: 'ADMIN' };
  // when & then
  assert.equal(V.canIssue(student), true);
  assert.equal(V.canIssue(admin), false);
  assert.equal(V.canIssue({ role: 'TUTOR' }), false);
  assert.equal(V.canIssue(null), false);
});

test('confirmRows: 닉네임·아이디·가입일·구분 순서', () => {
  // given
  const m = { displayName: '도도', loginId: 'jky2013', joinedAt: '2026-03-02T10:00:00', role: 'STUDENT' };
  // when
  const rows = V.confirmRows(m);
  // then
  assert.deepEqual(rows, [['닉네임', '도도'], ['아이디', 'jky2013'], ['가입일', '2026-03-02'], ['구분', '학생']]);
});

test('sameNicknameCount: 같은 닉네임 회원 수', () => {
  // given
  const list = [{ displayName: '도도', loginId: 'jky2013' }, { displayName: '도도', loginId: 'gongdohyun' }, { displayName: '하나' }];
  // when & then
  assert.equal(V.sameNicknameCount(list, list[0]), 2);
  assert.equal(V.sameNicknameCount(list, list[2]), 1);
});

test('fmtDate·fmtDateTime·roleLabel: 잘못된 값은 빈 글, 모르는 구분은 그대로', () => {
  // given & when & then
  assert.equal(V.fmtDate(null), '');
  assert.equal(V.fmtDate('x'), '');
  assert.equal(V.fmtDateTime('2026-11-19T14:05:00'), '2026-11-19 14:05');
  assert.equal(V.roleLabel('TUTOR'), '강사');
  assert.equal(V.roleLabel('ETC'), 'ETC');
});

test('PasswordChangeRequired.validate: 8자 미만·확인 불일치를 막는다', () => {
  // given & when & then
  assert.match(P.validate('short', 'short'), /8자/);
  assert.match(P.validate('longenough1', 'longenough2'), /일치/);
  assert.equal(P.validate('longenough1', 'longenough1'), null);
  assert.match(P.validate(null, null), /8자/);
});
