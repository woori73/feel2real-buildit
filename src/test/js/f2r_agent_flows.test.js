const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// AI 비서 단계별 업무 안내: 언제 시작하는지, 무엇을 묻는지, 모은 값을 기존 API 어디로 보내는지.
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/f2r_agent_flows.js'), 'utf8');

function load() {
  const window = {};
  vm.runInNewContext(JS, { window });
  return window.f2rAgentFlows;
}

test('given 세부 내용 없는 업무 요청 when 판별하면 then 해당 단계 안내를 시작한다', () => {
  // given
  const f = load();
  // when / then
  assert.equal(f.detectFlow('학생 등록하고 싶어'), 'student');
  assert.equal(f.detectFlow('새 학생 추가'), 'student');
  assert.equal(f.detectFlow('출결 확인하고 싶어'), 'attendance');
  assert.equal(f.detectFlow('오늘 결석 기록'), 'attendance');
  assert.equal(f.detectFlow('상담 기록 남기기'), 'consult');
  assert.equal(f.detectFlow('공지 써줘'), 'notice');
  assert.equal(f.detectFlow('학습자료 보여줘'), 'materials');
});

test('given 이름·ID·항목이 이미 있는 요청 when 판별하면 then 기존 대화 분석에 맡긴다', () => {
  // given
  const f = load();
  // when / then
  assert.equal(f.detectFlow('학생 김하늘 등록해줘'), null);
  assert.equal(f.detectFlow('학생 등록해줘, 학교: 새봄중'), null);
  assert.equal(f.detectFlow('학생 ID 1 출결 조회해줘'), null);
  assert.equal(f.detectFlow('공지 작성해줘, 제목: 휴강'), null);
  assert.equal(f.detectFlow('출결 정정해줘'), null);
  assert.equal(f.detectFlow('학생 삭제'), null);
  assert.equal(f.detectFlow('학원 운영과 재무 현황 보여줘'), null);
  assert.equal(f.detectFlow(''), null);
});

test('given 출결 안내 when 확인을 고르면 then 출결 구분·메모는 묻지 않는다', () => {
  // given
  const f = load();
  // when / then
  assert.equal(f.nextStep('attendance', {}), 0);
  assert.equal(f.nextStep('attendance', { mode: 'query' }), 1);
  assert.equal(f.nextStep('attendance', { mode: 'query', studentId: '3' }), -1);
  assert.equal(f.nextStep('attendance', { mode: 'record', studentId: '3' }), 2);
  assert.equal(f.nextStep('attendance', { mode: 'record', studentId: '3', eventType: 'ABSENT' }), 3);
  assert.equal(f.nextStep('attendance', { mode: 'record', studentId: '3', eventType: 'ABSENT', note: '' }), -1);
  assert.equal(f.nextStep('overview', {}), -1);
  assert.equal(f.nextStep('unknown', {}), -1);
});

test('given 단계별 입력 when 확인하면 then 필수·선택지·길이·연락처 규칙을 지킨다', () => {
  // given
  const f = load();
  const [name, grade, , , phone] = f.FLOWS.student.steps;
  // when / then
  assert.deepEqual({ ...f.acceptAnswer(name, ' 김하늘 ') }, { ok: true, value: '김하늘', label: '김하늘' });
  assert.equal(f.acceptAnswer(name, '').ok, false);
  assert.equal(f.acceptAnswer(name, '가'.repeat(21)).ok, false);
  assert.equal(f.acceptAnswer(grade, '').skip, true);
  assert.equal(f.acceptAnswer(grade, '중2').value, '중2');
  assert.equal(f.acceptAnswer(grade, '대학생').ok, false);
  assert.equal(f.acceptAnswer(phone, '010 1234 5678').value, '010-1234-5678');
  assert.equal(f.acceptAnswer(phone, '02-123-4567').value, '02-123-4567');
  assert.equal(f.acceptAnswer(phone, '연락처 없음').ok, false);
  const mode = f.FLOWS.attendance.steps[0];
  assert.equal(f.acceptAnswer(mode, '출결 기록').value, 'record');
});

test('given 학생 목록 when 이름으로 찾으면 then 같은 이름 우선·여러 명이면 모두 돌려준다', () => {
  // given
  const f = load();
  const list = [{ id: 1, name: '김하늘' }, { id: 2, name: '이서준' }, { id: 4, name: '김하늘' }, { id: 5, name: '김하늘별' }];
  // when / then
  assert.deepEqual(f.findStudents(list, '김하늘').map((s) => s.id), [1, 4]);
  assert.deepEqual(f.findStudents(list, '서준').map((s) => s.id), [2]);
  assert.deepEqual(f.findStudents(list, '5').map((s) => s.id), [5]);
  assert.deepEqual(f.findStudents(list, '없음').map((s) => s.id), []);
});

test('given 모은 값 when 마치면 then 쓰기는 기존 초안 API, 조회·공지는 기존 대화, 자료는 기존 화면으로 보낸다', () => {
  // given
  const f = load();
  // when
  const student = f.finish('student', { name: '김하늘', grade: '중2', schoolName: '', parentName: '', parentPhone: '010-1234-5678' });
  const record = f.finish('attendance', { mode: 'record', studentId: '3', eventType: 'LATE', note: '' });
  const query = f.finish('attendance', { mode: 'query', studentId: '3' });
  const consult = f.finish('consult', { studentId: '7', title: '학습 상담', content: '다음 수업 계획' });
  const notice = f.finish('notice', { title: '휴강 "안내"', content: '내일은, 휴강합니다' });
  const materials = f.finish('materials', { kind: 'study-materials' });
  // then
  assert.deepEqual(JSON.parse(JSON.stringify(student)), { kind: 'draft', operation: 'STUDENT_CREATE', input: { name: '김하늘', grade: '중2', parentPhone: '010-1234-5678' } });
  assert.deepEqual(JSON.parse(JSON.stringify(record)), { kind: 'draft', operation: 'ATTENDANCE_RECORD', input: { studentId: 3, eventType: 'LATE' } });
  assert.equal(query.message, '학생 ID 3 출결 조회해줘');
  assert.deepEqual(JSON.parse(JSON.stringify(consult.input)), { studentId: 7, title: '학습 상담', content: '다음 수업 계획' });
  // 쉼표가 있어도 기존 공지 규칙이 내용을 끝까지 읽도록 따옴표로 감싼다
  assert.equal(notice.message, '공지 작성해줘, 제목: "휴강 ”안내”", 내용: "내일은, 휴강합니다"');
  assert.equal(materials.href, '/tutor/conversation/materials?menu=study-materials');
  assert.equal(f.finish('overview', {}).message, '학원 운영과 재무 현황 보여줘');
});
