const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// F2R AI 운영 대시보드: 비서 선택·화면 선택·대화 기반 서비스 활성화·상태 전환 규칙과 화면 계약.
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/f2r_agent_dashboard.js'), 'utf8');
const UA = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/unified_assistant.js'), 'utf8');
const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dashboard.html'), 'utf8');

function load() {
  const window = {};
  vm.runInNewContext(JS, { window });
  return window.f2rAgentDashboard;
}

test('given 저장된 비서 값 when 고르면 then 세븐·서수니만 허용하고 나머지는 세븐으로 시작한다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.pickAgent('suni'), 'suni');
  assert.equal(d.pickAgent('seven'), 'seven');
  assert.equal(d.pickAgent('toString'), 'seven');
  assert.equal(d.pickAgent(null), 'seven');
});

test('given 주소 해시와 저장값 when 첫 화면을 고르면 then 기본은 AI 메인이고 #all만 전체 대시보드를 연다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.pickView('', null), 'ai');
  assert.equal(d.pickView('#all', null), 'all');
  assert.equal(d.pickView('#ai', 'all'), 'ai');
  assert.equal(d.pickView('', 'all'), 'all');
  assert.equal(d.pickView('', 'weird'), 'ai');
});

test('given 자유 대화 when 서비스 키워드가 있으면 then 관련 서비스를 많이 맞은 순서로 활성화한다', () => {
  // given
  const d = load();
  // when
  const ledgerFirst = d.matchServices('이번 달 장부 거래랑 증빙, 그리고 학생 한 명 확인');
  // then
  assert.deepEqual([...ledgerFirst], ['ledger', 'academycraft']);
  assert.deepEqual([...d.matchServices('수시 전형 상담 일정')], ['admissions', 'academycraft']);
  assert.deepEqual([...d.matchServices('중2 내신 예상문제 만들어줘')], ['wiki']);
  assert.deepEqual([...d.matchServices('TESTPREP 모의고사 성적')], ['testprep']);
  assert.deepEqual([...d.matchServices('   ')], []);
  assert.deepEqual([...d.matchServices('오늘 날씨 어때')], []);
});

test('given 서비스 목록 when 읽으면 then 실제 경로가 있는 서비스만 노출한다', () => {
  // given
  const d = load();
  // when
  const hrefs = [...d.SERVICES.map((s) => s.href)];
  // then
  assert.deepEqual(hrefs, ['/academycraft', '/ledger', '/easy', '/problem-generator', '/testprep', '/admissions/workspace', '/builder']);
});

test('given 초안 상태 문구 when 집계하면 then 승인 대기·완료·확인 필요로 나눈다', () => {
  // given
  const d = load();
  // when
  const c = d.countDraftStatus(['승인 대기', '저장 검증 완료', '검토 필요', '취소', '승인 대기', '저장 실패']);
  // then
  assert.deepEqual({ ...c }, { pending: 2, done: 1, review: 2 });
});

test('given 비서 상태 when 대화 사건이 오면 then 분석→응답→완료→대기로 전환한다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.nextState('idle', 'submit'), 'analyze');
  assert.equal(d.nextState('analyze', 'reply'), 'talk');
  assert.equal(d.nextState('talk', 'verified'), 'done');
  assert.equal(d.nextState('done', 'reply'), 'done');
  assert.equal(d.nextState('done', 'timeout'), 'idle');
});

test('given 대시보드 템플릿 when 읽으면 then F2R 홈 로고·전체 대시보드 전환·사용법 안내·두 화면이 있다', () => {
  // given / when / then
  assert.match(HTML, /class="ops-logo" data-f2r-home href="\/"/);
  assert.doesNotMatch(HTML, /<b>Build<\/b><i>It<\/i>/);
  assert.doesNotMatch(HTML, />홈으로</);
  assert.match(HTML, /data-view-btn="all"[^>]*>전체 대시보드</);
  assert.match(HTML, /id="uaResume"/);
  assert.match(HTML, /data-pane="ai"/);
  assert.match(HTML, /data-pane="all"/);
  for (const id of ['agentStage', 'agentVideo', 'agentName', 'agentCode', 'agentRole', 'agentGreeting', 'agentStateLabel', 'agentSuggest', 'statusAgent', 'statusPending', 'statusDone', 'statusReview', 'statusServices', 'shortcutRow']) {
    assert.match(HTML, new RegExp('id="' + id + '"'), `템플릿에 id="${id}" 없음`);
  }
});

test('given 사용법 안내 버튼 when 누르면 then 진행 기록과 무관하게 1단계부터 다시 연다', () => {
  // given / when / then
  assert.match(UA, /if\(!state\.progress\)state\.progress=\{step:0,completed:false,version:null,local:true\}/);
  assert.match(UA, /await saveProgress\(0,false\)/);
  assert.match(UA, /showTour\(true\);\$\('uaTour'\)\.scrollIntoView/);
});
