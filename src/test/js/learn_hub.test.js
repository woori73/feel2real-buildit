const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const LH = require(path.resolve(__dirname, '../../main/resources/static/js/learn_hub.js'));

test('given 주소의 tab 값 when safeTab·returnUrl then 아는 탭만 쓰고 모르는 값은 내신 탭으로 돌아온다', () => {
  // given & when & then
  assert.equal(LH.safeTab('toeic'), 'toeic');
  assert.equal(LH.safeTab('//evil.example'), 'naesin');
  assert.equal(LH.safeTab(undefined), 'naesin');
  assert.equal(LH.returnUrl('korean'), '/learn?tab=korean');
  assert.equal(LH.returnUrl('"><script>'), '/learn?tab=naesin');
  assert.deepEqual(LH.TABS, ['naesin', 'csat', 'ibt', 'sat', 'toeic', 'korean', 'math', 'social', 'science']);
});

test('given 풀이 화면 종류 when runUrl then 내신·타과목은 디지털 모의고사, SAT는 전용 화면, 그 밖은 공통 시험 화면', () => {
  // given & when & then
  assert.equal(LH.runUrl('NAESIN', 12), '/naesintest/run?attempt=12');
  assert.equal(LH.runUrl('SAT', 12), '/testprep/sat/run?attempt=12');
  assert.equal(LH.runUrl('TP', 12), '/testprep/run?attempt=12');
  assert.equal(LH.runUrl(undefined, 'a b'), '/testprep/run?attempt=a%20b');
});

test('given 풀기 단추의 값 when startBody then 시험 코드만 싣고, TOEIC 파트·유형을 고른 단추는 그 값도 싣는다', () => {
  // given & when & then
  assert.deepEqual(LH.startBody({ exam: 'TP-SAT-01' }), { examCode: 'TP-SAT-01' });
  assert.deepEqual(LH.startBody({ exam: 'TP-TOEIC-RC-01', parts: 'TOEIC_P5,TOEIC_P6' }),
    { examCode: 'TP-TOEIC-RC-01', taskTypes: ['TOEIC_P5', 'TOEIC_P6'] });
  assert.deepEqual(LH.startBody({ exam: 'TP-TOEIC-RC-01', parts: 'TOEIC_P5', tag: '문법-품사' }),
    { examCode: 'TP-TOEIC-RC-01', taskTypes: ['TOEIC_P5'], focusTag: '문법-품사' });
  assert.deepEqual(LH.startBody({ exam: 'TP-TOEIC-RC-01', parts: 'TOEIC_P7', from: '176', to: '200' }),
    { examCode: 'TP-TOEIC-RC-01', taskTypes: ['TOEIC_P7'], fromNo: 176, toNo: 200 });
});

test('given 내신 묶음 when matches then 고른 학년·학교에 맞는 묶음만 보이고 학교 없는 묶음은 전체일 때만 보인다', () => {
  // given
  const apm2 = { grade: '중2', school: '압구정중학교' };
  const app = { grade: '', school: '' };
  // when & then
  assert.equal(LH.matches(apm2, '', ''), true);
  assert.equal(LH.matches(apm2, '중2', '압구정중학교'), true);
  assert.equal(LH.matches(apm2, '중1', ''), false);
  assert.equal(LH.matches(apm2, '', '신구중학교'), false);
  assert.equal(LH.matches(app, '', ''), true);
  assert.equal(LH.matches(app, '중2', ''), false);
});

test('given 내신 회차 코드 when lockedForMember then 영어 내신은 1회(-R01)만, iBT·SAT·TOEIC은 첫 세트만 일반 회원에게 열려 있고 타과목은 잠그지 않는다', () => {
  // given & when & then
  assert.equal(LH.lockedForMember('NAESIN', 'TP-NAESIN-APM2-2602-R01', ['TP-NAESIN-APM2-2602-R01']), false);
  assert.equal(LH.lockedForMember('NAESIN', 'TP-NAESIN-DMM2-2602-R11', ['TP-NAESIN-DMM2-2602-R11']), false);
  assert.equal(LH.lockedForMember('NAESIN', 'TP-NAESIN-APM2-2602-R02'), true);
  assert.equal(LH.lockedForMember('NAESIN', 'TP-NAESIN-KOR-M2-2602-R03'), false);
  assert.equal(LH.lockedForMember('NAESIN', 'TP-NAESIN-SCI-M2-2602-R09'), false);
  assert.equal(LH.isSubject('TP-NAESIN-MATH-M2-2602-R02'), true);
  assert.equal(LH.isSubject('TP-NAESIN-SGM2-2602-R02'), false);
  // iBT·어휘·SAT·TOEIC은 종류마다 첫 세트만 열려 있다
  const open = ['TP-PRACTICE-MINI', 'TP-SAT-MINI-01', 'TP-TOEIC-FULL-01'];
  assert.equal(LH.lockedForMember('TP', 'TP-TOEIC-FULL-01', open), false);
  assert.equal(LH.lockedForMember('TP', 'TP-TOEIC-RC-02', open), true);
  assert.equal(LH.lockedForMember('SAT', 'TP-SAT-MINI-01', open), false);
  assert.equal(LH.lockedForMember('SAT', 'TP-SAT-PRAC-01', open), true);
  assert.equal(LH.lockedForMember('TP', 'TP-TOEIC-RC-02'), true);
  assert.equal(LH.lockedForMember('LINK', undefined, open), false);
});

test('given 내 오답 목록 when toeicWrongSets then TOEIC Part 5·6 오답만 세트별로 모은다', () => {
  // given
  const items = [
    { kind: 'TOEIC', taskType: 'TOEIC_P5', examCode: 'TP-TOEIC-RC-01', examTitle: 'TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)', questionId: 1 },
    { kind: 'TOEIC', taskType: 'TOEIC_P6', examCode: 'TP-TOEIC-RC-01', examTitle: 'TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)', questionId: 2 },
    { kind: 'TOEIC', taskType: 'TOEIC_P7', examCode: 'TP-TOEIC-RC-01', examTitle: 'x', questionId: 3 },
    { kind: 'TOEIC', taskType: 'TOEIC_P5', examCode: 'TP-TOEIC-FULL-01', examTitle: 'TOEIC Practice Test 01 (기출 유사 1회)', questionId: 4 },
    { kind: 'SAT', taskType: 'SAT_RW', examCode: 'TP-SAT-01', examTitle: 'SAT', questionId: 5 },
  ];
  // when
  const sets = LH.toeicWrongSets(items);
  // then
  assert.deepEqual(sets, [
    { examCode: 'TP-TOEIC-RC-01', name: '독해 추가 세트 1', ids: [1, 2] },
    { examCode: 'TP-TOEIC-FULL-01', name: '기출 유사 1회', ids: [4] },
  ]);
  assert.deepEqual(LH.toeicWrongSets(null), []);
});

test('given 글자 when esc·setName then 꺾쇠를 바꾸고 제목 끝 괄호 이름을 꺼낸다', () => {
  // given & when & then
  assert.equal(LH.esc('<b>"a"&'), '&lt;b&gt;&quot;a&quot;&amp;');
  assert.equal(LH.setName('iBT Practice Set 01'), 'iBT Practice Set 01');
  assert.equal(LH.setName(null), '');
});
