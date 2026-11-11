const {test}=require('node:test');const assert=require('node:assert/strict');
const view=require('../../main/resources/static/js/admin_overview_view.js');

const ROWS=[
  {name:'가나',school:'새봄중',grade:2,weekdays:['WED','MON'],weekdaySource:'ATTENDANCE',classes:['A반'],monthCheckIns:3,lastCheckIn:'2026-11-06'},
  {name:'다라',school:'푸른고',grade:0,weekdays:['WED'],weekdaySource:'CLASS',classes:[],monthCheckIns:0,lastCheckIn:null},
  {name:'마바',school:'',grade:null,weekdays:[],weekdaySource:null,classes:[],monthCheckIns:0,lastCheckIn:null}
];

test('given 요일 코드 when 글로 바꾸면 then 월~일 순서로 점을 찍고 비면 -',()=>{
  // given / when / then
  assert.equal(view.weekdayText(['WED','MON']),'월·수');
  assert.equal(view.weekdayText([]),'-');
  assert.equal(view.weekdayText(null),'-');
});
test('given 요일 출처 when 짧은 말 then 최근 4주 등원 / 반 요일 / 기록 없음',()=>{
  assert.equal(view.sourceNote('ATTENDANCE'),'최근 4주 등원');
  assert.equal(view.sourceNote('CLASS'),'반 요일');
  assert.equal(view.sourceNote(null),'기록 없음');
});
test('given 학교·학년 when 글로 바꾸면 then 학년 0·없음은 빼고 둘 다 없으면 -',()=>{
  assert.equal(view.schoolText('새봄중',2),'새봄중 2학년');
  assert.equal(view.schoolText('푸른고',0),'푸른고');
  assert.equal(view.schoolText('',null),'-');
});
test('given 날짜 when 글로 바꾸면 then "11월 6일", 없으면 -',()=>{
  assert.equal(view.dayText('2026-11-06'),'11월 6일');
  assert.equal(view.dayText(null),'-');
});
test('given 학생 목록 when 요일별 학생 수 then 월~일 7칸, 0명 요일 포함',()=>{
  // when
  const counts=view.weekdayCounts(ROWS);
  // then
  assert.deepEqual(counts.map(c=>c.label),['월','화','수','목','금','토','일']);
  assert.equal(counts.find(c=>c.day==='WED').count,2);
  assert.equal(counts.find(c=>c.day==='MON').count,1);
  assert.equal(counts.find(c=>c.day==='SUN').count,0);
});
test('given 검색어·요일 when 거르면 then 이름·학교로 찾고 요일이 있으면 그 요일 학생만',()=>{
  // when / then
  assert.deepEqual(view.filterRows(ROWS,'','WED').map(r=>r.name),['가나','다라']);
  assert.deepEqual(view.filterRows(ROWS,'푸른','').map(r=>r.name),['다라']);
  assert.deepEqual(view.filterRows(ROWS,'가나','FRI'),[]);
  assert.equal(view.filterRows(ROWS,'  ','').length,3);
});
test('given 위젯 응답 when 재무 카드 then 단위 붙은 숫자와 설명, 오류면 안내 문장',()=>{
  // given
  const widgets=[{id:'easy-finance-summary',data:{cards:[{label:'수납',value:3055000,unit:'원'},{label:'미수금',value:0,unit:'원'}]}}];
  // when
  const fin=view.financeCards(widgets);
  // then
  assert.equal(fin.error,null);
  assert.deepEqual(fin.cards[0],{label:'수납',value:'3,055,000원',help:'이번 달 들어온 돈'});
  assert.match(view.financeCards([{id:'easy-finance-summary',error:'x'}]).error,/불러오지/);
  assert.deepEqual(view.financeCards([]),{cards:[],error:null});
});
test('given 사업체 이름 when Claude 질문 then 도구 이름과 사업체가 들어간다',()=>{
  const p=view.claudePrompt('압구정에듀홀릭학원');
  assert.match(p,/read_admin_dashboard/);
  assert.match(p,/압구정에듀홀릭학원/);
  assert.match(view.claudePrompt(''),/우리 학원/);
});
test('given 의논 예시 when 확인 then 3개이고 비어 있지 않다',()=>{
  assert.equal(view.DISCUSS_EXAMPLES.length,3);
  assert.ok(view.DISCUSS_EXAMPLES.every(t=>t.trim()));
});
