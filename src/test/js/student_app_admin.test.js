const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const STATIC = path.resolve(__dirname, '../../main/resources/static');
const SA = require(path.join(STATIC, 'js/easy_student_app.js'));
const ER = require(path.join(STATIC, 'js/exam_results.js'));

test('given 코드 상태 when 표시하면 then 사용 중이면 끝 두 글자·최근 사용, 없으면 없음', () => {
  // given / when / then
  assert.equal(SA.codeLabel({ active: true, tail: 'QT', lastUsedAt: '2026-11-26T16:00:00' }), '사용 중(…QT) · 최근 2026.11.26 16:00');
  assert.equal(SA.codeLabel({ active: true, tail: 'QT', lastUsedAt: null }), '사용 중(…QT) · 아직 안 씀');
  assert.equal(SA.codeLabel({ active: false }), '없음');
  assert.equal(SA.duration(754), '12분 34초');
});

test('given 새 응시 기록 when 그리면 then 학생·회차·몇 번째·점수·틀린 문항(유형)과 점검 버튼, 점검한 기록은 버튼 없음', () => {
  // given
  const r = { resultId: 3, studentName: '가명 학생A', examTitle: '1회', attemptNo: 2, score: 80, maxScore: 100, correctCount: 30,
    totalCount: 40, durationSec: 754, submittedAt: '2026-11-26T16:12:00', checkedAt: null,
    wrongItems: [{ no: '3', topic: '관계대명사', reason: '' }, { no: '7', topic: '', reason: '' }] };
  // when
  const fresh = ER.item(r);
  const checked = ER.item(Object.assign({}, r, { checkedAt: '2026-11-26T18:00:00' }));
  // then
  assert.match(fresh, /가명 학생A · 1회 · 2번째 응시/);
  assert.match(fresh, /80\/100점/);
  assert.match(fresh, /틀린 문항: 3번\(관계대명사\), 7번/);
  assert.match(fresh, /data-check="3"/);
  assert.ok(!/data-check/.test(checked));
  assert.match(checked, /11월 26일 18:00 점검/);
});
