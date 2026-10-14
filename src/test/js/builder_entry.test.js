const assert = require('node:assert/strict');
const test = require('node:test');
const path = require('node:path');

const entry = require(path.join(__dirname, '../../main/resources/static/js/builder_entry.js'));

test('Given 비로그인 사용자, When 제작 시작 목적지를 정하면, Then 홈의 시작 게이트로 보낸다', () => {
  // given/when
  const dest = entry.entryDestination(null);
  // then
  assert.equal(dest, '/?start=builder');
});

test('Given TUTOR 또는 ADMIN, When 제작 시작 목적지를 정하면, Then /builder로 보낸다', () => {
  // given/when/then
  assert.equal(entry.entryDestination({ role: 'TUTOR' }), '/builder');
  assert.equal(entry.entryDestination({ role: 'ADMIN' }), '/builder');
});

test('Given 학생 계정, When 제작 시작 목적지를 정하면, Then 빌더로 직접 보내지 않는다', () => {
  // given/when
  const dest = entry.entryDestination({ role: 'STUDENT' });
  // then
  assert.equal(dest, '/?start=builder');
});

test('Given 입력한 요청, When 보관 형식을 만들면, Then 빌더가 읽는 {text, at} 형식이고 2000자로 자른다', () => {
  // given
  const long = ' ' + 'a'.repeat(2500) + ' ';
  // when
  const payload = JSON.parse(entry.pendingPayload(long, 1000));
  // then
  assert.equal(payload.at, 1000);
  assert.equal(payload.text.length, 2000);
});

test('Given 빈 입력, When 보관 형식을 만들면, Then null이라 기존 보관 요청을 덮어쓰지 않는다', () => {
  // given/when/then
  assert.equal(entry.pendingPayload('   ', 1), null);
});

test('Given 이미 쓴 요청과 샘플 느낌, When 합치면, Then 쓴 내용을 지우지 않고 뒤에 덧붙인다', () => {
  // given/when
  const merged = entry.composeSamplePrompt('우리 영어학원 홈페이지', '깔끔하게 만들어줘.');
  // then
  assert.equal(merged, '우리 영어학원 홈페이지 깔끔하게 만들어줘.');
  assert.equal(entry.composeSamplePrompt('', '깔끔하게'), '깔끔하게');
  assert.equal(entry.composeSamplePrompt('내용', ''), '내용');
});
