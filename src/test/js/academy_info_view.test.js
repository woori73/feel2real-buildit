const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const v = require('../../main/resources/static/js/academy_info_view.js');

// 정보 화면 표시 규칙과 화면 계약. 값은 테스트용 가상 데이터다.
const ROOT = path.resolve(__dirname, '../../main/resources');
const HTML = fs.readFileSync(path.join(ROOT, 'templates/dashboard_info.html'), 'utf8');
const JS = fs.readFileSync(path.join(ROOT, 'static/js/academy_info.js'), 'utf8');

test('given 대조 결과 when 합계 문장을 만들면 then 기존·추가·보류·제외 합과 원본 이름 수를 대조한다', () => {
  const ok = v.reconcileSummary({ sourceEntries: 40, sourceNames: 10, existing: 3, added: 4, hold: 2, excluded: 1 });
  assert.match(ok, /원본 기록 40건 · 서로 다른 이름 10명 = 기존 3 \+ 추가 후보 4 \+ 연결 보류 2 \+ 제외 1 \(합계 일치\)/);
  assert.match(v.reconcileSummary({ sourceEntries: 1, sourceNames: 2, existing: 1, added: 0, hold: 0, excluded: 0 }), /합계 불일치/);
});

test('given 대조 행 when 묶으면 then 사람이 해결할 보류가 먼저 온다', () => {
  const g = v.groupRows([{ status: 'NEW' }, { status: 'EXISTING' }, { status: 'HOLD' }]);
  assert.deepEqual(g.map((x) => x.status), ['HOLD', 'NEW', 'EXISTING']);
});

test('given 학생명단 자료 when 먼저 읽을 파일을 고르면 then 원본 Word를 쓴다', () => {
  const item = { files: [{ role: '미리보기 PDF', fileId: 'p' }, { role: '원본 Word', fileId: 'w' }] };
  assert.equal(v.primaryFile(item).fileId, 'w');
  assert.equal(v.primaryFile({ files: [] }), null);
});

test('given 스캔 PDF when 확인용 페이지를 만들면 then 최대 장수까지 페이지 이미지 주소를 준다', () => {
  const read = { extension: 'pdf', preview: '/api/studio/files/f/preview?projectId=student-roster', result: { pageCount: 9 } };
  assert.equal(v.pageImages(read, 3).length, 3);
  assert.equal(v.pageImages(read, 3)[2], '/api/studio/files/f/preview?projectId=student-roster&page=3');
  assert.deepEqual(v.pageImages({ extension: 'png', preview: 'x' }), []);
  assert.equal(v.size(2048), '2.0KB');
});

test('given 정보 화면 when 읽으면 then 제목 → 원문 → 원본 파일 순이고 서버 글은 textContent로만 넣는다', () => {
  const ids = new Set([...JS.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]));
  for (const id of ids) assert.match(HTML, new RegExp('id="' + id + '"'));
  assert.doesNotMatch(JS, /\.innerHTML\s*=|insertAdjacentHTML|eval\(/);
  const card = JS.slice(JS.indexOf('function card('), JS.indexOf('// ---- 학생명단'));
  assert.ok(card.indexOf("'ai-title'") < card.indexOf("'본문 읽기'") && card.indexOf("'본문 읽기'") < card.indexOf("'원본 파일'"));
  // 학생 등록은 기존 초안 → 원장 승인 경로만 쓴다(직접 POST /students 없음).
  assert.match(JS, /\/api\/dashboard\/assistant\/v1\/drafts/);
  assert.doesNotMatch(JS, /academycraft\/api\/rea\/[^'"]*\/students['"]\s*,\s*\{\s*method/);
});
