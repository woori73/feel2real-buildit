const assert = require('node:assert/strict');
const test = require('node:test');
const W = require('../../main/resources/static/js/assistant_wait.js');

test('given 경과 8초 미만 when 안내를 물으면 then 빈 문자열이다', () => {
  // given / when
  const note = W.noticeFor(W.SLOW_MS - 1);

  // then
  assert.equal(note, '');
});

test('given 경과 8~30초 when 안내를 물으면 then 오래 걸린다는 안내와 경과 초를 보여 주고 작업 내용이 안전하다고 알린다', () => {
  // given / when
  const note = W.noticeFor(12500);

  // then
  assert.match(note, /평소보다 오래 걸리고 있어요 \(12초\)/);
  assert.match(note, /작업 내용은 그대로 안전해요/);
});

test('given 경과 30초 이상 when 안내를 물으면 then 자동으로 멈출 수 있음을 알린다', () => {
  // given / when
  const note = W.noticeFor(45000);

  // then
  assert.match(note, /\(45초\)/);
  assert.match(note, /자동으로 멈추고 작업 내용은 그대로/);
});

test('given 재정의 값이 없거나 잘못됨 when 대기 한도를 구하면 then 기본 100초다', () => {
  // given / when / then
  assert.equal(W.timeoutMs(undefined), 100000);
  assert.equal(W.timeoutMs('abc'), 100000);
  assert.equal(W.timeoutMs(0), 100000);
  assert.equal(W.timeoutMs(-5), 100000);
});

test('given 양수 재정의 값 when 대기 한도를 구하면 then 그 값을 쓴다', () => {
  // given / when / then
  assert.equal(W.timeoutMs(6000), 6000);
  assert.equal(W.timeoutMs('7000'), 7000);
});

test('given 타임아웃 when 메시지를 만들면 then 작업 내용이 그대로임을 알리고 재시도를 권한다', () => {
  // given / when
  const m = W.timeoutMessage();

  // then
  assert.match(m, /작업 내용은 그대로예요/);
  assert.match(m, /다시 시도/);
});

test('given 서버가 늦게 끝낸 경우 when 메시지를 만들면 then 되돌리기 안내를 포함한다', () => {
  // given / when
  const m = W.lateAppliedMessage();

  // then
  assert.match(m, /되돌리기/);
});
