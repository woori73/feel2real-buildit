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

test('given 비서 상태 when 입력·전송·응답·승인 사건이 오면 then 대기→듣기→생각→응답→완료로 전환한다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.nextState('idle', 'typing'), 'listening');
  assert.equal(d.nextState('listening', 'cleared'), 'idle');
  assert.equal(d.nextState('listening', 'submit'), 'thinking');
  assert.equal(d.nextState('thinking', 'typing'), 'thinking');
  assert.equal(d.nextState('thinking', 'reply'), 'speaking');
  assert.equal(d.nextState('speaking', 'spoken'), 'idle');
  assert.equal(d.nextState('thinking', 'verified'), 'done');
  assert.equal(d.nextState('done', 'reply'), 'done');
  assert.equal(d.nextState('done', 'timeout'), 'idle');
  assert.equal(d.nextState('listening', 'timeout'), 'listening');
});

test('given 음성 어댑터 when 응답 방식을 정하면 then 연결 전에는 텍스트만, 립싱크가 있을 때만 말하는 영상을 쓴다', () => {
  // given
  const d = load();
  const files = ['idle.mp4', 'think.mp4', 'speak.mp4'];
  // when / then
  assert.equal(d.speakingMode(null), 'text');
  assert.equal(d.speakingMode({}), 'text');
  assert.equal(d.speakingMode({ speak() {} }), 'voice');
  assert.equal(d.speakingMode({ speak() {}, lipSync: true }), 'voice-lipsync');
  assert.equal(d.speakingClip('text', files), 'idle');
  assert.equal(d.speakingClip('voice', files), 'idle');
  assert.equal(d.speakingClip('voice-lipsync', files), 'speak');
  assert.equal(d.speakingClip('voice-lipsync', ['idle.mp4']), 'idle');
});

test('given 서버가 알려준 자료 목록 when 영상을 고르면 then 없는 영상은 대기 영상으로, 대기 영상도 없으면 표식으로 대체한다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.resolveClip('think', ['idle.mp4', 'think.mp4']), 'think');
  assert.equal(d.resolveClip('think', ['idle.mp4']), 'idle');
  assert.equal(d.resolveClip('idle', []), null);
});

test('given 정본 승인 상태 when 배지를 정하면 then 승인 전 자료를 최종 캐릭터로 표시하지 않는다', () => {
  // given
  const d = load();
  // when / then
  assert.equal(d.badgeFor('APPROVED'), '정본');
  assert.equal(d.badgeFor('REFERENCE_APPROVED'), '승인 얼굴 기준 · 영상 확정 전');
  assert.equal(d.badgeFor('CANDIDATE'), '외형 승인 전 후보');
  assert.equal(d.badgeFor('MISSING'), '캐릭터 자료 미연결');
  assert.equal(d.badgeFor('FINAL'), '외형 승인 전 후보');
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
  // 캐릭터 자료는 공개 정적 경로가 아니라 권한 확인 경로로만 읽는다
  assert.doesNotMatch(HTML, /src="\/agent-media\//);
  assert.match(HTML, /src="\/dashboard\/agent-media\/seven\/front\.webp"/);
  // 캐릭터 아래 응답 → 입력창 순서
  assert.ok(HTML.indexOf('id="agentStage"') < HTML.indexOf('id="agentSpeech"'));
  assert.ok(HTML.indexOf('id="agentSpeech"') < HTML.indexOf('id="uaCommandForm"'));
  // PR #892 제작소 링크는 같은 표식(data-studio-entry, 기본 hidden)으로 유지한다
  assert.match(HTML, /<a class="ops-btn" href="\/ai-agent-studio" data-studio-entry hidden>AI AGENT 제작소<\/a>/);
  assert.match(HTML, /src="\/js\/studio_entry_link\.js\?v=20261023-1"/);
  for (const id of ['agentStage', 'agentVideo', 'agentVideoAlt', 'agentName', 'agentCode', 'agentRole', 'agentGreeting', 'agentStateLabel', 'agentBadge', 'agentSuggest', 'agentSpeech', 'speechName', 'speechMode', 'agentMiniImg', 'statusAgent', 'statusPending', 'statusDone', 'statusReview', 'statusServices', 'shortcutRow']) {
    assert.match(HTML, new RegExp('id="' + id + '"'), `템플릿에 id="${id}" 없음`);
  }
});

test('given 사용법 안내 버튼 when 누르면 then 진행 기록과 무관하게 1단계부터 다시 연다', () => {
  // given / when / then
  assert.match(UA, /if\(!state\.progress\)state\.progress=\{step:0,completed:false,version:null,local:true\}/);
  assert.match(UA, /await saveProgress\(0,false\)/);
  assert.match(UA, /showTour\(true\);\$\('uaTour'\)\.scrollIntoView/);
});
