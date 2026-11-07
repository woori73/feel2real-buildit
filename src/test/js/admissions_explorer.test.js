const test=require('node:test');
const assert=require('node:assert/strict');
const model=require('../../main/resources/static/js/admissions-explorer.js');

test('given 국립대 약칭 when 검색 정규화 then 같은 대학명과 일치',()=>{
  assert.equal(model.norm('국립강원대학교'),model.norm('강원대'));
  assert.notEqual(model.norm('한양대학교(서울)'),model.norm('한양대학교(ERICA)'));
});
test('given 비공개와 영점 when 원문 값 표시 then null을 0으로 채우지 않음',()=>{
  assert.equal(model.text(null),'미공개');assert.equal(model.text(0),'0');
  assert.equal(model.metric({metricValue:2.31,rawValueText:'2.31*'}),'2.31*');
  assert.equal(model.metric({metricValue:null,rawValueText:'-'}),'-');
});
test('given 탭 저장 후보 when 복원 then 올바른 모집단위 3개만 유지하고 성적은 버림',()=>{
  const row={id:1,year:2027,name:'컴퓨터공학과',universityName:'테스트대학교',score:120,userId:3};
  const result=model.cleanCandidates([row,row,{...row,id:2},{...row,id:3},{...row,id:4},{...row,id:-1}]);
  assert.equal(result.length,3);assert.deepEqual(Object.keys(result[0]),['id','year','name','universityName']);
  assert.deepEqual(model.cleanCandidates([{...row,year:2026},null,{id:'1'}]),[]);
});
test('given 수능최저 판정 결과 when 화면 문구로 바꾸면 then 합격이 아니라 기준 충족 여부만 말한다',()=>{
  // given / when / then
  assert.equal(model.minimumLabel('MET'),'충족');
  assert.equal(model.minimumLabel('NOT_MET'),'미충족');
  assert.equal(model.minimumLabel('NO_MINIMUM'),'수능최저 없음');
  assert.equal(model.minimumLabel('NEEDS_INPUT'),'등급 입력 필요');
  assert.equal(model.minimumLabel('WITHHELD_NEEDS_REVIEW'),'원문 확인 필요');
  assert.equal(model.minimumLabel('SOMETHING'),'확인할 수 없음');
  assert.doesNotMatch(Object.values({a:model.minimumLabel('MET'),b:model.minimumLabel('NOT_MET')}).join(),/합격/);
});
test('given 모집인원표 when 정시 군별로 나누면 then 군 표기만 쓰고 0명·합계·수시는 뺀다',()=>{
  // given
  const quota=JSON.stringify({'지역균형전형':10,'정시 일반전형(나군)':36,'정시 지역균형전형(나군)':0,'정시 기회균형(가군)':2,'정시 합계':38,'수시 합계':10});
  // when
  const g=model.jeongsiGroups(quota);
  // then
  assert.deepEqual(g['나'],[{type:'정시 일반전형(나군)',count:36}]);
  assert.deepEqual(g['가'],[{type:'정시 기회균형(가군)',count:2}]);
  assert.deepEqual(g['다'],[]);assert.deepEqual(g.unclear,[]);assert.equal(g.readable,true);
});
test('given 군이 둘 적힌 전형과 군 없는 정시 전형 when 나누면 then 한 군으로 단정하지 않는다',()=>{
  // given
  const quota=JSON.stringify({'정시 농어촌학생전형(정원외)(가·다군)':3,'정시 특성화고등을졸업한재직자전형(정원외)':5});
  // when
  const g=model.jeongsiGroups(quota);
  // then
  assert.equal(g.unclear.length,2);assert.deepEqual([g['가'],g['나'],g['다']],[[],[],[]]);
});
test('given 읽을 수 없는 모집인원표 when 나누면 then 읽을 수 없음으로 표시',()=>{
  // given / when / then
  assert.equal(model.jeongsiGroups('수시 10명').readable,false);
  assert.equal(model.jeongsiGroups(null).readable,false);
  assert.equal(model.jeongsiGroups('[1,2]').readable,false);
});
test('given 한국 시간 자정 직후 when 오늘 날짜 then 한국 날짜',()=>{
  // given: UTC 2026-11-06 15:30 = KST 2026-11-07 00:30
  const now=Date.UTC(2026,10,6,15,30);
  // when / then
  assert.equal(model.todayKst(now),'2026-11-07');
});
test('given 두 대학 일정 when 합치면 then 날짜순·중복 제거·지난 일정 표시',()=>{
  // given
  const a={universityName:'가대학교',schedules:[{title:'원서접수 (일반)',startDate:'2026-12-29',endDate:'2026-12-31'},{title:'면접',startDate:'2026-10-03',endDate:'2026-10-03'},{title:'합격자 발표',startDate:null,endDate:null}]};
  const b={universityName:'나대학교',schedules:[{title:'원서접수',startDate:'2026-12-29',endDate:'2026-12-31'}]};
  // when: 같은 대학 후보가 둘(가대학교 두 번)
  const rows=model.mergeSchedules([a,b,a],'2026-11-07');
  // then
  assert.deepEqual(rows.map(r=>r.universityName+':'+r.title),['가대학교:면접','가대학교:원서접수 (일반)','나대학교:원서접수','가대학교:합격자 발표']);
  assert.deepEqual(rows.map(r=>r.past),[true,false,false,false]);
});
test('given 입시결과 구분 when 표시 then OTHER는 구분 없음, 모르는 값은 미상',()=>{
  // given / when / then
  assert.equal(model.roundLabel('SUSI'),'수시');
  assert.equal(model.roundLabel('JEONGSI'),'정시');
  assert.equal(model.roundLabel('OTHER'),'수시·정시 구분 없음');
  assert.equal(model.roundLabel(undefined),'구분 미상');
});
