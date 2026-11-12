const {test}=require('node:test');const assert=require('node:assert/strict');
const view=require('../../main/resources/static/js/easy_attendance_view.js');

test('given 반 정보 when 한 줄 설명 then 요일·시각·강의실·정원·담당을 보여 준다',()=>{
  // given
  const c={weekdays:['MON','WED'],startTime:'15:00:00',endTime:'16:30:00',room:'201호',capacity:8,teacherName:'가나'};
  // when / then
  assert.equal(view.classLine(c),'월·수 15:00~16:30 · 201호 · 정원 8명 · 담당 가나');
  assert.equal(view.classLine({weekdays:[]}),'');
});
test('given 출결 구분 when 표시 then 한글 이름과 한 글자 표시',()=>{
  assert.equal(view.label('LATE'),'지각');assert.equal(view.mark('ABSENT'),'결');assert.equal(view.mark('X'),'?');
});
test('given 진행표 기록(시각 미상) when 시각 표시 then 시각 미상',()=>{
  assert.equal(view.timeText({source:'ROSTER_IMPORT',note:'진행표 기록 · 수업 2건 · 시각 미상',occurredAt:'2026-03-02T00:00:00'}),'시각 미상');
  assert.equal(view.timeText({source:'MANUAL',occurredAt:'2026-03-02T15:05:00'}),'15:05');
});
test('given 월별 응답 when 표로 바꾸면 then 날짜 칸 글자와 구분별 합계가 나온다',()=>{
  // given
  const data={daysInMonth:3,students:[{studentId:1,name:'가나',days:{'1':['CHECK_IN'],'3':['LATE','CHECK_OUT']},counts:{CHECK_IN:1,LATE:1,CHECK_OUT:1}}]};
  // when
  const g=view.monthGrid(data);
  // then
  assert.deepEqual(g.days,[1,2,3]);
  assert.deepEqual(g.rows[0].cells,['등','','지하']);
  assert.deepEqual(g.rows[0].counts,[1,1,0,0,1]);
  assert.deepEqual(g.countHeaders,['등원','지각','결석','조퇴','하원']);
});
test('given 진행표 미리보기 이름들 when 기본 선택 then 후보 1명 연결·없으면 새로 등록·여러 명은 비워 둔다',()=>{
  // given
  const names=[{name:'가나',suggestedAction:'MATCH',suggestedStudentId:7},{name:'다라',suggestedAction:'CREATE'},{name:'마바',suggestedAction:'CHOOSE'}];
  // when
  const m=view.defaultMappings(names);
  // then
  assert.deepEqual(m,[{name:'가나',action:'MATCH',studentId:7},{name:'다라',action:'CREATE',studentId:null},{name:'마바',action:'',studentId:null}]);
  const p=view.mappingProblems(m);
  assert.equal(p.ok,false);assert.deepEqual(p.unchosen,['마바']);
  assert.equal(view.mappingProblems([{name:'가나',action:'MATCH',studentId:null}]).noStudent[0],'가나');
});
test('given 적용 결과 when 요약 then 한 문장',()=>{
  assert.equal(view.applySummary({createdStudents:2,matchedStudents:1,skippedNames:0,eventsCreated:30,eventsAlreadyThere:4}),
    '새 학생 2명 · 기존 학생 연결 1명 · 건너뜀 0명 · 등원 기록 30건 저장 · 이미 있던 기록 4건');
});

test('given 학원 홈 바로가기 주소 when 읽으면 then 사업체·반·날짜를 꺼내고 형식이 틀린 값은 버린다', () => {
  // given / when
  const ok = view.deepLink('?businessId=1&classId=3&date=2026-11-11');
  const bad = view.deepLink('?businessId=0&classId=abc&date=2026/11/11');
  // then
  assert.deepEqual(ok, { businessId: '1', classId: '3', date: '2026-11-11', tab: 'sheet' });
  assert.deepEqual(bad, { businessId: null, classId: null, date: null, tab: 'sheet' });
  assert.deepEqual(view.deepLink(''), { businessId: null, classId: null, date: null, tab: 'sheet' });
});

test('given 대시보드 반 관리 바로가기 when 주소를 읽으면 then 반 관리 탭이며 알 수 없는 탭은 출결부다', () => {
  // given / when / then
  assert.equal(view.deepLink('?businessId=1&tab=classes').tab, 'classes');
  assert.equal(view.deepLink('?tab=month').tab, 'month');
  assert.equal(view.deepLink('?tab=import').tab, 'import');
  assert.equal(view.deepLink('?tab=unknown').tab, 'sheet');
});
