const {test}=require('node:test');const assert=require('node:assert/strict');
const view=require('../../main/resources/static/js/hub_view.js');

const WIDGETS=[
  {id:'easy-finance-summary',title:'이번달 요약',type:'stat-cards',data:{cards:[
    {label:'수납',value:1234000,unit:'원'},{label:'지출',value:500000,unit:'원'},
    {label:'미수금',value:90000,unit:'원',trend:'3명'},{label:'원생 수',value:41,unit:'명'}]}},
  {id:'easy-overdue-students',title:'미수 학생',type:'list',link:'/easy/receivables',data:{items:[
    {title:'학생 A',subtitle:'90,000원',meta:'10월분'},{title:'학생 B'},{title:'학생 C'},{title:'학생 D'},{title:'학생 E'},{title:'학생 F'}]}},
  {id:'easy-recent-transactions',title:'최근 거래',type:'list',data:{items:[],emptyText:'최근 거래가 없습니다.'}}
];

test('given 부탁 글 when 주소를 만들면 then /dashboard/assistant?ask= 로 인코딩해 넘긴다',()=>{
  // given
  const text='  미수금 정리해줘 & 문자 초안  ';
  // when
  const url=view.askUrl(text);
  // then
  assert.equal(url,'/dashboard/assistant?ask='+encodeURIComponent('미수금 정리해줘 & 문자 초안'));
});
test('given 빈 글 when 주소를 만들면 then AI 업무 비서 화면만 연다',()=>{
  assert.equal(view.askUrl('   '),'/dashboard/assistant');
  assert.equal(view.askUrl(null),'/dashboard/assistant');
});
test('given 아주 긴 글 when 주소를 만들면 then 500자까지만 넘긴다',()=>{
  // given
  const long='가'.repeat(600);
  // when
  const back=view.askFromSearch(view.askUrl(long).split('?')[1]);
  // then
  assert.equal(back.length,500);
});
test('given ?ask= 주소 when 읽으면 then 글을 돌려주고 없거나 비면 null',()=>{
  // given / when / then
  assert.equal(view.askFromSearch('?ask='+encodeURIComponent('학생 등록해줘')),'학생 등록해줘');
  assert.equal(view.askFromSearch('?ask=%20%20'),null);
  assert.equal(view.askFromSearch('?other=1'),null);
  assert.equal(view.askFromSearch(''),null);
});
test('given 위젯 응답 when 숫자 카드 then 단위 붙은 큰 숫자와 한 줄 설명·장부 주소가 붙는다',()=>{
  // given / when
  const sum=view.summaryCards(WIDGETS);
  // then
  assert.equal(sum.error,null);
  assert.deepEqual(sum.cards.map(c=>c.label),['수납','지출','미수금','원생 수']);
  assert.equal(sum.cards[0].value,'1,234,000원');
  assert.equal(sum.cards[0].help,'이번 달 들어온 수강료');
  assert.equal(sum.cards[2].trend,'3명');
  assert.equal(sum.cards[3].href,'/easy');
});
test('given 요약 위젯 오류 when 숫자 카드 then 카드 없이 쉬운 안내 문장만',()=>{
  // given
  const widgets=[{id:'easy-finance-summary',error:'DB timeout'}];
  // when
  const sum=view.summaryCards(widgets);
  // then
  assert.deepEqual(sum.cards,[]);
  assert.match(sum.error,/새로고침/);
});
test('given 요약 위젯 없음 when 숫자 카드 then 빈 목록',()=>{
  assert.deepEqual(view.summaryCards([]),{cards:[],error:null});
});
test('given 모르는 카드 이름 when 설명 then 빈 설명과 장부 주소',()=>{
  assert.deepEqual(view.cardHelp('기타'),{help:'',href:'/easy'});
});
test('given 목록 위젯 6건 when 미리보기 5건 then 앞 5건과 전체 수·두 줄 설명',()=>{
  // given / when
  const p=view.listPreview(WIDGETS,'easy-overdue-students',5);
  // then
  assert.equal(p.total,6);
  assert.equal(p.items.length,5);
  assert.deepEqual(p.items[0],{title:'학생 A',sub:'90,000원 · 10월분'});
  assert.equal(p.items[1].sub,'');
  assert.equal(p.href,'/easy/receivables');
});
test('given 빈 목록·없는 위젯 when 미리보기 then 빈 안내 문장 또는 null',()=>{
  // given / when
  const empty=view.listPreview(WIDGETS,'easy-recent-transactions',5);
  // then
  assert.equal(empty.items.length,0);
  assert.equal(empty.emptyText,'최근 거래가 없습니다.');
  assert.equal(empty.href,'/easy');
  assert.equal(view.listPreview(WIDGETS,'nope',5),null);
});
test('given 부탁 예시 when 확인 then 4개이고 모두 비어 있지 않다',()=>{
  assert.equal(view.ASK_EXAMPLES.length,4);
  assert.ok(view.ASK_EXAMPLES.every(t=>t.trim().length>0));
});
