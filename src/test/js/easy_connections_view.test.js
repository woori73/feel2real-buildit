const {test}=require('node:test');const assert=require('node:assert/strict');
const view=require('../../main/resources/static/js/easy_connections_view.js');
const SITES={PAYSSAM:'결제선생 (수강료 청구·수납)',KB_CARD:'KB국민카드',KB_BANK:'KB국민은행',SHINHAN_CARD:'신한카드',CREFIA_CARDSALES:'여신금융협회 카드매출 조회'};

test('given 작업용 파일 이름 when 표시 이름으로 바꾸면 then 기관·연월 파일로 읽힌다',()=>{
  // given / when / then
  assert.equal(view.labelName('KB_CARD_202609',SITES),'KB국민카드 · 2026년 9월 파일');
  assert.equal(view.labelName('crefia_academy_202609_gap',SITES),'카드매출(여신협회) · 2026년 9월 파일');
  assert.equal(view.labelName('sales_slip_20260727_15000',SITES),'카드매출 전표 · 2026년 7월 파일');
});
test('given 모르는 이름이나 기본 이름 when 바꾸면 then 원래 이름 또는 파일 넣기',()=>{
  assert.equal(view.labelName('학원 운영계좌',SITES),'학원 운영계좌');
  assert.equal(view.labelName('default',SITES),'파일 넣기');
});
test('given 엑셀·CSV 연결 when 상태 then 파일 수집이고 자동수집 상태를 쓰지 않는다',()=>{
  // given
  const conn={sourceCode:'EXCEL_FILE',label:'KB_CARD_202609',enabled:true,readiness:'NOT_CONFIGURED',authStatus:'NOT_REGISTERED'};
  // when / then
  assert.equal(view.status(conn),'파일 수집');
  assert.equal(view.method(conn),'FILE');
});
test('given 재인증 필요 연결 when 상태 then 다시 인증 필요',()=>{
  assert.equal(view.status({sourceCode:'CODEF_BANK',readiness:'NEEDS_REAUTH',authStatus:'NEEDS_REAUTH'}),'다시 인증 필요');
  assert.equal(view.status({sourceCode:'CODEF_BANK',readiness:'READY',authStatus:'REGISTERED',connectionStatus:'ACTIVE'}),'연결됨');
  assert.equal(view.status({sourceCode:'CODEF_BANK',readiness:'UNVERIFIED',authStatus:'REGISTERED'}),'다시 인증 필요');
});
test('given 직접 로그인 수집 연결 여러 개 when 표 줄 then 기관별로 한 줄로 묶고 건수를 더한다',()=>{
  // given
  const conns=[
    {sourceCode:'EXCEL_FILE',label:'MS_aaa',latestRunAdapterCode:'MCP_SITE',siteCode:'KB_BANK',siteAccountRef:'운영',ledgerSavedTotal:4,latestRunFinishedAt:'2026-11-08T10:00:00'},
    {sourceCode:'EXCEL_FILE',label:'MS_bbb',latestRunAdapterCode:'MCP_SITE',siteCode:'KB_BANK',siteAccountRef:'적립',ledgerSavedTotal:3,latestRunFinishedAt:'2026-11-09T10:00:00'},
    {sourceCode:'EXCEL_FILE',label:'KB_CARD_202609',ledgerSavedTotal:10}];
  // when
  const rows=view.rows(conns,SITES);
  // then
  assert.equal(rows.length,2);
  assert.deepEqual([rows[0].name,rows[0].method,rows[0].status,rows[0].saved,rows[0].last],['KB국민은행','직접 로그인 수집','직접 로그인 수집',7,'2026-11-09T10:00:00']);
  assert.equal(rows[1].status,'파일 수집');
});
test('given 연결 없음 / 직접 로그인 수집 1곳 when 한 줄 상태 then 문구가 바뀐다',()=>{
  assert.equal(view.summary([{sourceCode:'EXCEL_FILE',label:'KB_CARD_202609'}],SITES),'연결된 기관 0곳 · 지금은 파일 넣기로 수집 중');
  assert.equal(view.summary([{sourceCode:'EXCEL_FILE',label:'MS_a',latestRunAdapterCode:'MCP_SITE',siteCode:'KB_BANK',latestRunFinishedAt:'2026-11-09T10:00:00'}],SITES),'연결된 기관 1곳 · 마지막 수집 11월 9일');
});
test('given CSV 본문 when 미리보기 then 머리글을 확인하고 행 수·첫 날짜를 준다',()=>{
  // given
  const csv='﻿거래일,구분,금액,거래처,적요\n2026-09-03,지출,"12,000",문구점,"복사, 용지"\n2026-09-04,수입,300000,학부모,수강료\n';
  // when
  const p=view.previewCsv(csv);
  // then
  assert.equal(p.ok,true);assert.equal(p.rows,2);assert.equal(p.firstDate,'2026-09-03');assert.equal(p.sample[0][2],'12,000');assert.equal(p.sample[0][4],'복사, 용지');
  assert.equal(view.previewCsv('이름,값\na,1').ok,false);
});
test('given 기관 코드와 첫 거래일 when 파일 이름 then 기존 규칙(코드_연월)',()=>{
  assert.equal(view.fileLabel('KB_CARD','2026-09-03','2026-11-10'),'KB_CARD_202609');
  assert.equal(view.fileLabel('KB_CARD','','2026-11-10'),'KB_CARD_202611');
});
test('given 카탈로그 안내 문장 when 화면용으로 바꾸면 then 개발 용어가 남지 않는다',()=>{
  // given
  const steps=['사업용 통장을 쓰는 은행의 공식 주소(개인사업자·법인은 businessUrl)로 들어갑니다.',
    '파일 내용을 행 단위(날짜·입금/출금·금액·적요·잔액)로 정리해 save_easy_site_rows로 저장합니다.',
    '행 단위(이용일·가맹점·금액·승인번호, 취소는 IN)로 정리해 dataset=CARD_PURCHASE로 저장합니다.',
    '저장할 때 조회 건수와 금액 합계를 expectedCount·expectedTotal로 함께 보냅니다.'];
  // when
  const out=steps.map(view.plainStep).join(' ');
  // then
  assert.doesNotMatch(out,/businessUrl|save_easy_site_rows|dataset=|expectedCount/);
  assert.match(out,/기업·사업자용 주소/);assert.match(out,/Claude가 장부에 저장합니다/);
});
test('given 결제선생 파일 이름 when 바꾸면 then 결제선생 연월 파일',()=>{
  assert.equal(view.labelName('PAYSSAM_202610',SITES),'결제선생 (수강료 청구·수납) · 2026년 10월 파일');
  assert.equal(view.labelName('payssam_202610_export',{}),'결제선생 · 2026년 10월 파일');
  assert.equal(view.plainStep('행 단위로 정리해 dataset=TUITION_PAYMENT로 저장합니다.'),'행 단위로 정리해 Claude가 장부에 저장합니다.');
});
