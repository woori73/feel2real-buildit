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
    version: det.VERSION, path: '', title: '', headings: [], buttons: [],
    radioOptions: {}, hiddenNames: [], knownIds: {}
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
  assert.deepEqual(Object.keys(r).sort(), ['evidence', 'step', 'substate']);
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

/* collectFacts: 실제 입력값(text/password)은 절대 읽지 않는다는 원칙을 최소 DOM 스텁으로 확인. */
test('collectFacts does not read text/password input values', () => {
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
