const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

// 업무 대시보드: 기존 기능의 직접 접근과 현황을 유지하며 AI 콘솔·승인 카운터는 별도 화면에 둔다.
const ROOT = path.resolve(__dirname, '../../main/resources');
const HTML = fs.readFileSync(path.join(ROOT, 'templates/dashboard.html'), 'utf8');
const JS = fs.readFileSync(path.join(ROOT, 'static/js/academy_home.js'), 'utf8');
const ASSISTANT = fs.readFileSync(path.join(ROOT, 'templates/dashboard_assistant.html'), 'utf8');

test('given academy_home.js when $(id) 참조를 모으면 then 모두 홈 템플릿에 있고 중복 id가 없다', () => {
  // given
  const ids = new Set([...JS.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]));
  const all = [...HTML.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
  // then
  assert.ok(ids.size >= 15);
  for (const id of ids) assert.match(HTML, new RegExp('id="' + id + '"'), `템플릿에 id="${id}" 없음`);
  assert.equal(new Set(all).size, all.length);
});

test('given 홈 템플릿 when 읽으면 then AI 캐릭터·승인 카운터·플로팅 버튼·지휘창을 강제하지 않는다', () => {
  // given / when / then
  for (const gone of ['id="siMenu"', 'id="statusPending"', 'id="statusDone"', 'id="statusReview"', 'id="agentStage"', 'id="aiFab"',
    'id="siMediaForm"', 'id="uaUploadForm"', 'id="uaCommandForm"', 'id="kpiRow"', 'id="widgetContainer"', 'id="bizSelect"',
    'f2r_agent_dashboard.js', 'unified_assistant.js', 'easy-finance-summary']) {
    assert.ok(!HTML.includes(gone), gone + ' 가 홈에 남아 있음');
  }
  assert.ok(JS.includes('/api/dashboard/widgets'), '기존 장부 현황을 홈에서 직접 조회한다');
});

test('given 홈 템플릿 when 읽으면 then 학원명·홈·학생·반/수업·출결·상담 메뉴와 오늘의 학원·재원생 링크·다음 수업 배너가 있다', () => {
  for (const needle of ['id="ahAcademyName"', '>대시보드<', '>학생<', '>반/수업<', '>출결<', '>상담<', '<h1>업무 대시보드</h1>',
    'id="ahStudentCount" href="/academycraft/screens/MBR-01-student-list.html"', 'id="ahBanner"', 'id="ahBannerAction"', '학생 챙기기', '이번 주 중점 업무']) {
    assert.ok(HTML.includes(needle), needle);
  }
  // 상담은 원장·강사용 문의함으로 연다(CNS-01은 학부모 인증키 화면).
  assert.ok(HTML.includes('href="/academycraft/screens/CNS-03-inquiry-inbox.html">상담<'));
  // AI 화면을 거치지 않고 기존 상세 화면을 직접 연다.
  for (const href of ['/ledger', '/dashboard/info', '/dashboard/assistant']) assert.ok(HTML.includes('href="' + href + '"'), href);
  for (const href of ['/admin/hub/exam-requests', '/admin/student-roster', '/problem-generator', '/testprep', '/builder', '/admissions/workspace', '/dashboard/assistant#all']) assert.ok(HTML.includes('href="' + href + '"'), href);
  assert.ok(HTML.includes('fragments/_admin_topnav :: topnav('), '관리자 전체 메뉴를 유지한다');
});

test('given 예전 #ai·?ask= 주소 when 홈을 열면 then AI 업무 비서 화면으로 옮기고 #all·빈 주소는 홈을 연다', () => {
  // given
  const script = HTML.slice(HTML.indexOf('<script>'), HTML.indexOf('</script>')).replace(/\/\/.*$/gm, '');
  // then
  assert.match(script, /location\.hash === '#ai' \|\| \/\[\?&\]ask=\/\.test\(q\)/);
  assert.match(script, /location\.replace\('\/dashboard\/assistant' \+ q \+ '#ai'\)/);
  assert.doesNotMatch(script, /#all/);
  // AI 업무 비서 화면은 기존 기능을 그대로 두고 기본 보기를 AI 대화로 연다.
  assert.match(ASSISTANT, /data-default-view="ai"/);
  assert.match(ASSISTANT, /id="uaCommandForm"/);
});

test('given 현황 조회 스크립트 when 읽으면 then 서버 문자열은 textContent로 넣고 조회 과정에 쓰기 요청이 없다', () => {
  assert.doesNotMatch(JS, /\.innerHTML\s*=|insertAdjacentHTML|document\.write|eval\(/);
  assert.doesNotMatch(JS, /method:\s*'(POST|PUT|DELETE)'/);
  // 학원 전환 시 이전 학원 값을 먼저 지우고, 늦게 온 응답은 세대 번호로 버린다.
  assert.match(JS, /var g = \+\+generation;\s*resetView\(\);/);
  assert.match(JS, /if \(g !== generation\) return;/);
});

test('given 요일별 학생·반과 수정 화면 when 템플릿을 읽으면 then 관련 id가 모두 있고 수정·기록 모듈이 로드된다', () => {
  const script = fs.readFileSync(path.join(ROOT, 'static/js/academy_students.js'), 'utf8');
  for (const match of script.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)) assert.ok(HTML.includes('id="' + match[1] + '"'), match[1]);
  assert.ok(HTML.includes('/js/academy_students.js'));
  assert.doesNotMatch(script, /\.innerHTML\s*=|insertAdjacentHTML|eval\(/);
});
