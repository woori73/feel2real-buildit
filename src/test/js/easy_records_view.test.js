const { test } = require('node:test');
const assert = require('node:assert/strict');
const V = require('../../main/resources/static/js/easy_records_view.js');

test('deepLink: 숫자만 받고 나머지는 버린다', () => {
  // given & when & then
  assert.deepEqual(V.deepLink('?businessId=3&studentId=11&grading=7'), { businessId: '3', studentId: '11', grading: '7' });
  assert.deepEqual(V.deepLink('?studentId=<script>&grading=1e3'), { businessId: '', studentId: '', grading: '' });
});

test('lessonLines: 빈 칸은 빼고 숙제 확인은 말로', () => {
  // given
  const l = { lessonDate: '2026-11-20', lessonRange: '능률 3과 p.40~45', homeworkCheck: 'PARTIAL', nextHomework: 'p.46~47' };
  // when & then
  assert.deepEqual(V.lessonLines(l), ['수업 범위: 능률 3과 p.40~45', '숙제 일부 해 옴', '낸 숙제: p.46~47']);
});

test('gradingHeadline·wrongLine·topicsText', () => {
  // given
  const g = { gradedOn: '2026-11-20', subjectArea: '문법', totalCount: 20, correctCount: 18 };
  // when & then
  assert.equal(V.gradingHeadline(g), '11월 20일 · 문법 · 18/20');
  assert.equal(V.wrongLine({ no: '3', topic: '관계대명사', reason: 'what/that 혼동' }), '3번 · 관계대명사 — what/that 혼동');
  assert.equal(V.wrongLine({ no: '', topic: '', reason: '' }), '번호 없음');
  assert.equal(V.topicsText([{ topic: '관계대명사', count: 3 }, { topic: '시제', count: 1 }]), '관계대명사 3회 · 시제 1회');
  assert.match(V.topicsText([]), /없습니다/);
});

test('parseWrongText: "번호, 유형, 이유" 줄을 오답 목록으로', () => {
  // given & when
  const out = V.parseWrongText('3, 관계대명사, what/that 혼동, 주의\n\n7, 시제');
  // then
  assert.deepEqual(out, [{ no: '3', topic: '관계대명사', reason: 'what/that 혼동, 주의' }, { no: '7', topic: '시제', reason: '' }]);
});

test('statusText·weaknessLine·levelLabel: 채점 전 안내와 약점 한 줄', () => {
  // given & when & then
  assert.match(V.statusText({ status: 'PENDING', photos: [] }), /사진을 올려/);
  assert.match(V.statusText({ status: 'PENDING', photos: [{}] }), /채점해줘/);
  assert.equal(V.statusText({ status: 'GRADED', photos: [] }), '');
  assert.equal(V.weaknessLine({ topic: '관계대명사', level: 'HIGH', evidence: '11/20 3·7번' }), '관계대명사 (많이 약함) — 11/20 3·7번');
  assert.equal(V.levelLabel('X'), 'X');
});
