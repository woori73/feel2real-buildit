const {test}=require('node:test');const assert=require('node:assert/strict');
const v=require('../../main/resources/static/js/admissions_articles_view.js');

test('given 글 id when 주소 then 글마다 따로, 이상한 값은 목록 주소',()=>{
  // given / when / then
  assert.equal(v.articleUrl(3),'/admissions/articles/3');
  assert.equal(v.articleUrl('0'),'/admissions/articles');
  assert.equal(v.articleUrl('3;x'),'/admissions/articles');
});
test('given 게시일 when 글로 바꾸면 then "2026년 11월 13일", 형식이 아니면 빈 문자열',()=>{
  assert.equal(v.dateText('2026-11-13'),'2026년 11월 13일');
  assert.equal(v.dateText(null),'');
});
test('given 공개 목록 4편 when 첫 화면 카드 then 서버 순서대로 앞 3편과 회차·요약·주소',()=>{
  // given
  const list=[1,2,3,4].map(i=>({id:i,series:'바뀌는 입시',episode:'1-'+i,title:'제목'+i,summary:'요약'+i,publishedOn:'2026-11-13'}));
  // when
  const items=v.homeItems(list,3);
  // then
  assert.equal(items.length,3);
  assert.deepEqual(items[0],{href:'/admissions/articles/1',label:'바뀌는 입시 1-1',title:'제목1',summary:'요약1',date:'2026년 11월 13일'});
  assert.deepEqual(v.homeItems(null,3),[]);
});
test('given 관리자 폼 값 when 저장 요청 then 앞뒤 공백 정리·순서 숫자·공개 기본값',()=>{
  // given / when
  const b=v.formBody({series:' 바뀌는 입시 ',episode:'1-④ ',title:' t ',summary:' s ',bodyHtml:'<p>x</p>',publishedOn:'2026-11-14',visible:true,sortOrder:'4'});
  // then
  assert.deepEqual(b,{series:'바뀌는 입시',episode:'1-④',title:'t',summary:'s',bodyHtml:'<p>x</p>',publishedOn:'2026-11-14',visible:true,sortOrder:4});
  assert.equal(v.formBody({sortOrder:'abc'}).sortOrder,0);
});
