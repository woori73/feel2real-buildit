'use strict';

// A to Z 콘텐츠 계약: docs/newstart/NEWSTART_ATOZ_CURRENT_SOURCE_2026-09-24.md 가 SOURCE OF TRUTH.
// MD 의 모든 섹션·공식 URL·태그 구분이 사이트에서 누락되면 실패한다.

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const atoz = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/a-to-z.html'), 'utf8');
const home = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/index.html'), 'utf8');
const source = fs.readFileSync(path.resolve(__dirname, '../../../docs/newstart/NEWSTART_ATOZ_CURRENT_SOURCE_2026-09-24.md'), 'utf8');

test('A to Z renders all 14 journey steps from the source MD', () => {
  for (let i = 0; i <= 13; i++) assert.match(atoz, new RegExp('id="step-' + i + '"'));
  assert.match(atoz, /전체 여정 14단계/);
});

test('A to Z preserves the four source-truth tags and legend', () => {
  ['공식·현재', '운영자 경험', '회원 사례', '현재 재확인 필요'].forEach((tag) => {
    assert.ok((atoz.match(new RegExp(tag, 'g')) || []).length >= 2, tag + ' must appear as legend and content marker');
  });
});

test('A to Z keeps every official URL from the source MD', () => {
  const urls = [
    'https://www.newstartfund.or.kr/Safc/List.do',
    'https://www.newstartfund.or.kr/Contents/Objc.do',
    'https://www.newstartfund.or.kr/Faq/List.do',
    'https://www.newstartfund.or.kr/Board/List.do',
    'https://home.newstartfund.or.kr/Rslt/Rslt.do',
    'https://www.credit4u.or.kr/',
    'https://www.kamco.or.kr/portal/bbs/view.do?bIdx=26065',
    'https://ecfs.scourt.go.kr/',
    'https://ecfs.scourt.go.kr/psp/index.on?m=PSP004M01',
    'https://www.fsc.go.kr/',
    'https://cafe.naver.com/newhopero/659',
    'https://cafe.naver.com/newhopero/325',
  ];
  urls.forEach((u) => assert.ok(atoz.includes(u.replace(/&/g, '&amp;')) || atoz.includes(u), 'missing official URL: ' + u));
});

test('A to Z links each stage to the same canonical helpers as HOME', () => {
  ['/newstart/fss-helper', '/newstart/tools/credit4u', '/newstart/tools/application', '/newstart/tools/status', '/newstart/tools/court'].forEach((route) => {
    assert.match(home, new RegExp('href="' + route + '"'));
    assert.match(atoz, new RegExp('href="' + route + '"'));
  });
});

test('A to Z preserves key content units of every MD section', () => {
  const units = [
    /협약 가입 채권금융회사/,            // 0
    /12월 13일/,                         // 1-1 연체 계산
    /3개월 이상 연체/,                   // 1-1 공식
    /토스·카카오페이·네이버페이/,        // 1-1 보조수단
    /새출발기금 채무조정을 통해 채무를 정리할 예정입니다/, // 1-2 운영자 응대 예
    /신청 익일부터 추심중단·강제집행 중지/, // 1-2 정정
    /민원 안 넣으면 계속 추심/,          // 1-3 주의
    /2주가 지나기 전까지 이의신청/,      // 2-1
    /나의사건현황/,                      // 2-2
    /공동인증서 비밀번호가 반드시 1회 필요/, // 2-2 정정
    /이의신청서 제출 경험/,              // 2-3 운영자 경험
    /kcredit\.or\.kr:1441/,             // 3-1
    /채권자 변동정보/,                   // 3-1 확인항목
    /장기연체코드 0101/,                 // 3-2 재확인
    /장기연체정보 등록 확인 요청/,       // 3-3 민원 예시 제목
    /2020\.4~2025\.6/,                  // 4-1
    /폐업 법인은 신청 불가/,             // 4-1
    /임대차계약/,                        // 4-2
    /가상자산 잔고자료/,                 // 4-2
    /계좌정보통합관리서비스/,            // 4-2 공식
    /신청여부 조회/,                     // 4-3 흐름
    /대표자 신청/,                       // 4-4 영상
    /원장 초기화/,                       // 5
    /newstartfund@kamcocs\.co\.kr/,     // 5 과거 이메일(보존)
    /신분증을 과거 주소로 임의 전송/,    // 5 개인정보 주의
    /15억원\(담보 10억 \+ 무담보 5억\)/, // 6
    /6개월 내 신규대출 30%/,             // 6 주의
    /신복위 중개형/,                     // 7-1
    /1\/17/, /4\/30/, /11\/18/, /11\/20/, /11\/21/, /11\/24/, // 7-2 타임라인
    /신용점수 150점/,                    // 7-2
    /5회차 반드시 실효/,                 // 7-2 정정
    /후순위 사업자 담보/,                // 8
    /금리 4\.6%/,                        // 8 재확인
    /영업용 승합차/,                     // 9
    /잔존채무 부채증명서/,               // 9
    /신용보증재단\/기금 지급보증/,       // 10-1
    /재산은닉\/명의이전/,                // 10-2
    /연락 없음=통과/,                    // 10-2 정정
    /채권양도 관련 등기/,                // 10-3
    /소득·나이·부양가족/,               // 10-4
    /최대 10%p 추가 원금감면 우대\(최대 감면율 90%\)/, // 11-1
    /국민취업지원제도/,                  // 11-1
    /희망리턴패키지/,                    // 11-1
    /개인\(신용\)정보 수집·이용·제공·조회 동의서/, // 11-2
    /e-러닝 5시간 기초교육/,             // 11-3
    /IAP 수립/,                          // 11-3
    /재창업사업화 이수증/,               // 11-4
    /1년간 성실상환 시 공공정보 해제/,   // 11-5 공식
    /폴리텍/,                            // 11-5
    /재도전특별 직접대출/,               // 12-1 ①
    /다시서기 4\.0/,                     // 12-1 ②
    /전환보증플러스/,                    // 12-1 ③
    /최대 10%~최소 5% 추가감면/,         // 12-2 매입형
    /하한 3\.25%/,                       // 12-2 중개형
    /육아휴직/,                          // 12-2 상환유예
    /비금융 재기지원/,                   // 12-3
    /지연 여부를 판단하지 말고/,         // 13 주의
  ];
  const missing = units.filter((re) => !re.test(atoz));
  assert.deepEqual(missing, [], 'MD content units missing from A to Z: ' + missing.join(', '));
});

test('A to Z keeps the fact-check hold list and official references', () => {
  assert.match(atoz, /공개 전 팩트체크 보류목록/);
  ['장기연체코드 0101', 'newstartfund@kamcocs.co.kr', '담보 고정금리 4.6%', '공공정보 즉시 해제'].forEach((item) => assert.ok(atoz.includes(item), 'hold item missing: ' + item));
  assert.match(atoz, /공식 근거/);
});

test('every A to Z external CTA is a real visible link with safe target', () => {
  const ctas = atoz.match(/<a class="az-cta az-cta-(official|video)"[^>]*>[\s\S]*?<\/a>/g) || [];
  assert.ok(ctas.length >= 10, 'expected strong CTAs, got ' + ctas.length);
  ctas.forEach((c) => {
    assert.match(c, /href="https:\/\//);
    assert.match(c, /target="_blank"/);
    assert.match(c, /rel="noopener noreferrer"/);
  });
});

test('A to Z and HOME contain no placeholder hrefs', () => {
  [atoz, home].forEach((html) => {
    assert.doesNotMatch(html, /href="#"/);
    assert.doesNotMatch(html, /javascript:void/);
  });
});

test('HOME promotes A to Z and member chat to primary service cards', () => {
  // 모션 Phase 1의 추가 속성(data-ns-reveal 등)을 허용하고 클래스·라우트 계약만 검증
  assert.match(home, /<a class="ns-service-card ns-service-atoz" href="\/newstart\/a-to-z"[^>]*>/);
  assert.match(home, /<a class="ns-service-card ns-service-chat" href="\/newstart\/chat"[^>]*>/);
  assert.match(home, /A to Z 시작하기/);
  assert.match(home, /채팅방 들어가기/);
});

test('source MD section headers all map into the page or hold list', () => {
  // MD 의 최상위 numbered sections 0..13 must exist somewhere on the A to Z page.
  const sectionTitles = ['협약기관', '연체', '지급명령', 'Credit4U', '신청', '원장 초기화', '지원 가능한 대출', '부실우려', '부동산 담보', '차량 담보', '임시안', '추가감면', '성실상환', '신청결과'];
  const missing = sectionTitles.filter((t) => !source.includes(t) || !atoz.includes(t));
  assert.deepEqual(missing, [], 'sections missing between MD and page: ' + missing.join(', '));
});
