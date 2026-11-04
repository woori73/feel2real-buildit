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
