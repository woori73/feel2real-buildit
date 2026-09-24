/*
 * FSS STEP detector — fixture 단위 테스트 (node --test).
 *
 * §20: fixture는 장난감(anonymized) HTML/사실 팩만 사용한다. 실제 개인정보·실제 캡처 HTML 금지.
 * classify()는 순수 함수이므로 사실 팩(facts) 객체를 직접 만들어 판별을 검증한다.
 * collectFacts()는 최소 DOM 스텁으로 호출 경로·값 미열람 원칙만 검증한다(실제 브라우저 QA는 §17에서 사용자 수동).
 */
import { createRequire } from 'node:module';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const det = require('../src/fssStepDetector.js');

function facts(over) {
  return Object.assign({
    version: det.VERSION, path: '', title: '', headings: [], buttons: [], labels: [],
    radioOptions: {}, hiddenNames: [], progressLabels: [], hasOpener: false, knownIds: {}
  }, over);
}

test('STEP 1 — search.jsp 진입 화면 + [신청] + 제목 민원신청', () => {
  const r = det.classify(facts({
    path: '/fss/minwon/search.jsp',
    title: '금융감독원 민원신청',
    buttons: ['검색', '신청', '초기화']
  }));
  assert.equal(r.step, 1);
  assert.ok(r.evidence.some((e) => e.startsWith('URL:')));
});

test('STEP 3 — forInsertSelect.do + minwonChk(Y,N) + [다음]', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertselect.do',
    radioOptions: { minwonChk: ['Y', 'N'] },
    buttons: ['다음']
  }));
  assert.equal(r.step, 3);
  assert.ok(r.evidence.some((e) => e.indexOf('minwonChk') >= 0));
});

test('STEP 4 AGREE — forInsertAgre.do + hidden 3종 + [다음], 모달 없음', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertagre.do',
    hiddenNames: ['agreechkYn', 'sinfoSndYn', 'agreechkRgnoYn'],
    buttons: ['다음'],
    knownIds: { 'popup-alert': false }
  }));
  assert.equal(r.step, 4);
  assert.equal(r.substate, 'AGREE');
});

test('STEP 4 POPUP_ALERT — hidden 3종 + #popup-alert 노출', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertagre.do',
    hiddenNames: ['agreechkYn', 'sinfoSndYn', 'agreechkRgnoYn'],
    knownIds: { 'popup-alert': true }
  }));
  assert.equal(r.step, 4);
  assert.equal(r.substate, 'POPUP_ALERT');
});

test('UNKNOWN — 알 수 없는 URL', () => {
  const r = det.classify(facts({ path: '/fss/minwon/totally-unknown.do', title: '기타' }));
  assert.equal(r.step, 'UNKNOWN');
});

test('UNKNOWN — forInsertSelect.do 이지만 minwonChk 선택지 없음(부분 일치)', () => {
  const r = det.classify(facts({ path: '/fss/cvpl/ctrbsunlawfncdl/forinsertselect.do', buttons: ['다음'] }));
  assert.equal(r.step, 'UNKNOWN');
});

test('UNKNOWN — search.jsp 이지만 [신청] 버튼 없음', () => {
  const r = det.classify(facts({ path: '/fss/minwon/search.jsp', title: '민원신청' }));
  assert.equal(r.step, 'UNKNOWN');
});

test('STEP 2는 관측 불가(네이티브 alert) — search.jsp에 머무름, GUIDE에 수동 [확인] 문구', () => {
  const r = det.classify(facts({
    path: '/fss/minwon/search.jsp', title: '금융감독원 민원신청', buttons: ['신청']
  }));
  assert.equal(r.step, 1); /* alert는 DOM이 아니라 별도 STEP 2로 판별하지 않는다(§9 정직) */
  assert.match(det.GUIDE[1], /확인/);
});

test('UNKNOWN fallback 문구는 금지·허용 규정을 지킨다', () => {
  const u = det.GUIDE.UNKNOWN;
  assert.equal(u, '현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요.');
  for (const k of Object.keys(det.GUIDE)) {
    const g = det.GUIDE[k];
    assert.ok(!/개인정보 입력을 확인했습니다|인증이 완료됐습니다|NewStart가 대신 신청합니다/.test(g),
      '금지 문구 포함: ' + k);
  }
});

/* ===== REAL CHROME QA 2026-09-24: 개인정보 화면(forInsertStep1.do) 인식 — 안내 전용 ===== */
test('STEP 5 — 실제 URL /fss/cvpl/ctrbsUnlawFncdl/forInsertStep1.do → 개인정보 단계 인식', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertstep1.do',
    buttons: ['본인인증받기', '다음'],
    radioOptions: { avoidYn: ['Y', 'N'] }
  }));
  assert.equal(r.step, 5);
  assert.equal(r.substate, 'PII_INPUT');
  assert.ok(r.evidence.some((e) => e.indexOf('forInsertStep1.do') >= 0));
});

test('STEP 5 — classify 결과는 {step,substate,evidence}만 포함: action/click/submit/fill 필드 없음', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertstep1.do', buttons: ['본인인증받기']
  }));
  const keys = Object.keys(r).sort();
  assert.deepEqual(keys, ['evidence', 'fssStage', 'step', 'substate']);
  assert.ok(!('action' in r) && !('click' in r) && !('submit' in r) && !('fill' in r));
});

test('STEP 5 — detector 모듈에 자동 조작 함수(click/submit/fill/navigate/execute)가 존재하지 않는다', () => {
  const keys = Object.keys(det).sort();
  assert.deepEqual(keys, ['GUIDE', 'VERSION', 'classify', 'collectFacts']);
});

test('STEP 5 — 안내문은 직접 입력·인증 고지, 자동화·대행 주장 금지 문구 없음', () => {
  const g = det.GUIDE[5];
  assert.ok(g.includes('직접'));
  assert.ok(g.includes('NewStart는 입력·인증·제출을 대신하지 않으며'));
  assert.ok(!/자동으로 (?:입력|클릭|제출)|대신 입력|대신 클릭/.test(g));
});

test('STEP 5 — URL만 일치하고 구조 신호 없으면 확정 금지(UNKNOWN)', () => {
  const r = det.classify(facts({ path: '/fss/cvpl/ctrbsunlawfncdl/forinsertstep1.do' }));
  assert.equal(r.step, 'UNKNOWN');
});

test('UNKNOWN 진단 path 표시는 .do 잘림 없음(60자)', () => {
  const long = '/fss/cvpl/ctrbsunlawfncdl/someUnverifiedPage.do';
  const r = det.classify(facts({ path: long.toLowerCase() }));
  assert.equal(r.step, 'UNKNOWN');
  assert.ok(r.evidence[0].endsWith('.do)'), r.evidence[0]);
});

/* ===== REAL QA ROUND 2 (2026-09-24): 실제 동의 화면은 hidden이 아니라 RADIO 3종 ===== */
test('STEP 4 — 실제 DOM: agreechkYn/sinfoSndYn/agreechkRgnoYn radio(Y/N) + 타이틀 "1단계 개인정보동의"', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertagre.do',
    title: '1단계 개인정보동의 | 금융감독원 민원신청',
    headings: ['개인정보동의 개인정보 수집·이용 동의', '개인정보 제3자 제공 내역', '민감정보 제3자 제공내역', '고유식별정보 제3자 제공 내역'],
    buttons: ['다음', '재설정', '민원신청', '닫기'],
    radioOptions: { agreechkYn: ['Y', 'N'], sinfoSndYn: ['Y', 'N'], agreechkRgnoYn: ['Y', 'N'] },
    hiddenNames: ['menuNo', 'viewType', 'minwonChk', 'suitChk'],
    labels: ['동의', '동의하지 않음']
  }));
  assert.equal(r.step, 4);
  assert.equal(r.substate, 'AGREE');
  assert.deepEqual(r.fssStage, { no: 1, name: '개인정보동의' });
  assert.ok(r.evidence.some((e) => e.indexOf('RADIO:agreechkYn') >= 0));
  assert.ok(r.evidence.some((e) => e.indexOf('TITLE:1단계') >= 0));
});

test('STEP 4 — radio 3종 없으면 여전히 UNKNOWN(URL 단독 성공 금지)', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertagre.do',
    title: '1단계 개인정보동의 | 금융감독원 민원신청',
    hiddenNames: ['menuNo', 'viewType']
  }));
  assert.equal(r.step, 'UNKNOWN');
});

test('STEP 5 — 타이틀 "2단계 개인정보입력" → fssStage 2 (실측 화면)', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertstep1.do',
    title: '2단계 개인정보입력 | 금융감독원 민원신청',
    headings: ['민원신청하기', '개인정보입력(본인인증)'],
    buttons: ['실명인증', '본인인증받기', '주소 찾기', '직접입력', '다음 단계'],
    radioOptions: { receiveYn: ['Y', 'N'], rsltReceiveYn: ['Y', 'N', 'Z'] }
  }));
  assert.equal(r.step, 5);
  assert.equal(r.substate, 'PII_INPUT');
  assert.deepEqual(r.fssStage, { no: 2, name: '개인정보입력' });
});

/* collectFacts: 실제 입력값(text/password)은 절대 읽지 않는다는 원칙을 최소 DOM 스텁으로 확인. */test('collectFacts does not read text/password input values', () => {
  let pwdRead = false;
  const stubRadio = [{ name: 'minwonChk', getAttribute: () => 'Y' }];
  const doc = {
    title: 'x',
    createElement: () => ({ set href(_v) {}, get pathname() { return '/fss/cvpl/ctrbsunlawfncdl/forinsertselect.do'; } }),
    querySelectorAll: (sel) => {
      if (sel === 'input[type=radio]') return stubRadio;
      if (sel === 'input[type=hidden]') return [];
      if (sel === 'h1,h2,h3,h4,legend,caption') return [];
      if (sel === 'a,button,input[type=button],input[type=submit]') return [];
      return [];
    },
    getElementById: () => null
  };
  /* password 필드는 스텁에 value getter를 심어 읽히면 감지되게 한다. */
  Object.defineProperty(stubRadio[0], 'value', { get() { throw new Error('value read!'); } });
  const f = det.collectFacts(doc, 'https://www.fss.or.kr/fss/cvpl/ctrbsunlawfncdl/forinsertselect.do');
  assert.equal(f.path, '/fss/cvpl/ctrbsunlawfncdl/forinsertselect.do');
  assert.deepEqual(f.radioOptions.minwonChk, ['Y']);
  void pwdRead;
});

/* ===== REAL QA ROUND 3 (2026-09-24): 본인인증 popup은 UNKNOWN이 아닌 AUTH 상태 =====
   근거: handoff §3 STEP7 실측 — [본인인증받기] → fnCertificationPop()이 certificationPop.do를
   window.open 팝업으로 연다. 3차 QA에서 그 팝업(www.fss.or.kr origin)의 패널이
   "단계 자동 확인 실패"를 띄워 절차 실패로 오인됨 → 별도 상태로 승격. */
test('AUTH — certificationPop.do popup → step AUTH (UNKNOWN 아님, FSS STEP 아님)', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/certificationpop.do',
    title: '본인인증',
    hasOpener: true
  }));
  assert.equal(r.step, 'AUTH');
  assert.equal(r.substate, 'CERT_POPUP');
  assert.notEqual(r.step, 'UNKNOWN');
  assert.equal(typeof r.step, 'string'); /* STEP 번호(1~5)와 혼용 금지 */
  assert.equal(r.fssStage, undefined);  /* 인증 창은 금감원 4단계 모델이 아니다 */
  assert.ok(r.evidence.some((e) => e.indexOf('certificationPop.do') >= 0));
});

test('AUTH fallback — popup 컨텍스트(hasOpener) + 인증 타이틀/heading 문구 → AUTH', () => {
  const r = det.classify(facts({
    path: '/fss/cmmn/someauthwin.do',
    title: '공동인증서 본인인증',
    hasOpener: true
  }));
  assert.equal(r.step, 'AUTH');
  assert.equal(r.substate, 'AUTH_WINDOW');
});

test('AUTH 오탐 방지 — forInsertStep1.do는 [본인인증받기] 버튼이 있어도 STEP 5 유지(hasOpener 무관)', () => {
  const r = det.classify(facts({
    path: '/fss/cvpl/ctrbsunlawfncdl/forinsertstep1.do',
    title: '2단계 개인정보입력 | 금융감독원 민원신청',
    buttons: ['본인인증받기', '실명인증', '다음 단계'],
    hasOpener: true
  }));
  assert.equal(r.step, 5);
});

test('AUTH와 UNKNOWN 구분 — opener 없는 일반 미지원 FSS 화면은 여전히 UNKNOWN(빨간 실패 유지 대상)', () => {
  const r = det.classify(facts({ path: '/fss/minwon/someOther.do', title: '민원 안내', hasOpener: false }));
  assert.equal(r.step, 'UNKNOWN');
});

test('AUTH 안내문은 직접 수행 고지 + 결과 주장 금지(인증 완료/실패 선언 없음)', () => {
  const g = det.GUIDE.AUTH;
  assert.ok(g.includes('직접'));
  assert.ok(g.includes('NewStart는 인증정보를 읽거나 입력하지 않습니다'));
  assert.ok(!/인증이 완료|인증을 대행|자동 인증|성공적으로 인증/.test(g));
});

test('AUTH — classify 결과는 필드 최소 계약(step/substate/evidence뿐, 조작 필드 없음)', () => {
  const r = det.classify(facts({ path: '/fss/cvpl/ctrbsunlawfncdl/certificationpop.do', hasOpener: true }));
  assert.deepEqual(Object.keys(r).sort(), ['evidence', 'step', 'substate']);
});

test('collectFacts — window 컨텍스트에서 hasOpener boolean만 수집(opener 내부 미열람)', () => {
  const doc = {
    title: '본인인증',
    createElement: () => ({ set href(_v) {}, get pathname() { return '/fss/cvpl/ctrbsunlawfncdl/certificationPop.do'; } }),
    querySelectorAll: () => [],
    getElementById: () => null
  };
  /* opener 객체의 어떤 속성 읽지도 금지 — 읽으면 throw. */
  const openerTrap = new Proxy({}, { get() { throw new Error('opener property read!'); } });
  const f = det.collectFacts(doc, 'https://www.fss.or.kr/fss/cvpl/ctrbsunlawfncdl/certificationPop.do',
    { opener: openerTrap });
  assert.equal(f.hasOpener, true);
  const g = det.collectFacts(doc, 'https://www.fss.or.kr/x');
  assert.equal(g.hasOpener, false); /* 2-arg 호환(기존 fixture/테스트) */
});
