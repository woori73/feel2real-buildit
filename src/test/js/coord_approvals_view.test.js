const { test } = require('node:test');
const assert = require('node:assert/strict');
const V = require('../../main/resources/static/js/coord_approvals_view.js');

const job = (status, extra) => Object.assign({
  jobId: 'j1', status,
  summary: { student: '김민수', school: '가짜중', grade: 2, classes: ['화목 중2'], eventType: 'CHECK_IN', occurredAt: '2026-11-19T16:05' }
}, extra || {});

test('headline·detailLine: 학생·구분·시각, 학교·학년·반', () => {
  // given & when & then
  assert.equal(V.headline(job('review_required')), '김민수 학생 · 등원 · 11월 19일 16:05');
  assert.equal(V.detailLine(job('review_required')), '가짜중 · 2학년 · 화목 중2');
});

test('canDecide: 승인 대기일 때만', () => {
  // given & when & then
  assert.equal(V.canDecide(job('review_required')), true);
  assert.equal(V.canDecide(job('completed')), false);
  assert.equal(V.canDecide(null), false);
});

test('resultText: 완료·실패·거절·시간 초과', () => {
  // given & when & then
  assert.equal(V.resultText(job('completed', { result: { label: '등원', at: '2026-11-19T16:06' } })), '등원 기록 완료 (11월 19일 16:06)');
  assert.match(V.resultText(job('failed', { error: '중복' })), /중복/);
  assert.match(V.resultText(job('rejected')), /거절/);
  assert.match(V.resultText(job('expired')), /30분/);
  assert.equal(V.resultText(job('review_required')), '');
});

test('eventLabel·statusLabel·when: 모르는 값은 그대로', () => {
  // given & when & then
  assert.equal(V.eventLabel('LATE'), '지각');
  assert.equal(V.eventLabel('X'), 'X');
  assert.equal(V.statusLabel('expired'), '시간 초과');
  assert.equal(V.when('bad'), 'bad');
});

test('cancelled: 대화창에서 취소한 작업은 취소함으로 보인다', () => {
  // given
  const cancelled = job('cancelled', { result: { label: '등원', at: '2026-11-19T16:06' } });
  // when & then
  assert.equal(V.statusLabel('cancelled'), '취소함');
  assert.match(V.resultText(cancelled), /되돌렸/);
  assert.equal(V.canDecide(cancelled), false);
});

test('학생·반 작업: title을 제목으로, 완료 문구는 label 기준', () => {
  // given
  const j = { jobId: 'j2', status: 'completed', summary: { title: '김민수 → 화목 중2 반 배정' }, result: { label: '반 배정' } };
  // when & then
  assert.equal(V.headline(j), '김민수 → 화목 중2 반 배정');
  assert.equal(V.resultText(j), '반 배정 완료');
  assert.equal(V.detailLine(j), '');
});
