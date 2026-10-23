const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// 통합 AI 업무 비서 첫 방문 안내: 메뉴 클릭·목적 선택 규칙과 화면 id 계약.
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/unified_assistant.js'), 'utf8');
const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dashboard.html'), 'utf8');

function loadTour() {
  const window = {};
  const document = { readyState: 'loading', addEventListener() {}, getElementById() { return null; } };
  vm.runInNewContext(JS, { window, document, localStorage: {}, fetch() {} });
  return window.unifiedAssistantTour;
}

test('given 현재 단계와 메뉴 when 강조된 메뉴를 누르면 then 다음 단계로 저장된다', () => {
  const tour = loadTour();
  assert.equal(tour.stepAfterMenuClick(1, 'uaStudents'), 2);
  assert.equal(tour.stepAfterMenuClick(2, 'uaAttendance'), 3);
  assert.equal(tour.stepAfterMenuClick(4, 'uaLedger'), 5);
});

test('given 이미 지나간 메뉴 when 다시 누르면 then 단계가 뒤로 가지 않는다', () => {
  const tour = loadTour();
  assert.equal(tour.stepAfterMenuClick(3, 'uaStudents'), 3);
  assert.equal(tour.stepAfterMenuClick(2, 'unknownMenu'), 2);
});

test('given 사용 목적 when 선택하면 then 해당 메뉴 단계가 추천된다', () => {
  const tour = loadTour();
  assert.deepEqual(['students', 'attendance', 'consult', 'ledger', 'x'].map(tour.stepForPurpose), [1, 2, 3, 4, 0]);
});

test('given unified_assistant.js when $(id) 참조를 모으면 then 모두 dashboard 템플릿에 존재한다', () => {
  const ids = new Set([...JS.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]).filter((id) => !id.startsWith('uaField_')));
  assert.ok(ids.size > 30);
  for (const id of ids) assert.match(HTML, new RegExp('id="' + id + '"'), `템플릿에 id="${id}" 없음`);
});

test('given 안내 메뉴 when 읽으면 then 지원하지 않는 기능을 약속하지 않는다', () => {
  assert.doesNotMatch(HTML, /기록·정정·월별 조회/);
  assert.match(JS, /출결 정정은 사유 입력이 필요해 출결 화면에서 직접 처리/);
});
