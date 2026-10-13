const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

// 대화형 화면(assistant_home): JS가 참조하는 모든 요소 id가 템플릿에 있어야 한다(작업 화면 구조 계약).
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/assistant_home.js'), 'utf8');
const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/tutor/assistant_home.html'), 'utf8');

test('given assistant_home.js when $(id) 참조를 모으면 then 모두 템플릿에 존재한다', () => {
  const ids = new Set([...JS.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]));
  assert.ok(ids.size > 30, '참조 id가 충분히 수집돼야 한다');
  for (const id of ids) assert.match(HTML, new RegExp('id="' + id + '"'), `템플릿에 id="${id}" 없음`);
});

test('given 템플릿 when 읽기 then 같은 id가 중복되지 않는다', () => {
  const ids = [...HTML.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
  assert.equal(new Set(ids).size, ids.length, '중복 id: ' + ids.filter((x, i) => ids.indexOf(x) !== i));
});

test('given 작업 화면 when 읽기 then 기기 3종·되돌리기·게시 승인 창·하단 입력이 있다', () => {
  for (const needle of ['data-dev="mobile"', 'data-dev="tablet"', 'data-dev="desktop"', 'id="ahUndo"', 'id="ahConfirm"', 'id="ahWorkComposer"']) {
    assert.ok(HTML.includes(needle), needle);
  }
});

test('given assistant_home.js when 읽기 then 서버 문자열은 innerHTML이 아니라 textContent로만 넣는다', () => {
  assert.doesNotMatch(JS, /\.innerHTML\s*=/);
  assert.doesNotMatch(JS, /insertAdjacentHTML|document\.write|eval\(/);
});

test('given 수정 요청 when 보내면 then revision을 함께 보내 동시 수정을 감지한다', () => {
  assert.match(JS, /'\/turn',\s*\{\s*message:\s*text,\s*revision:\s*ctx\.revision\s*\}/);
  assert.match(JS, /'\/undo',\s*\{\s*revision:\s*ctx\.revision\s*\}/);
});

test('given v5 대화 화면 when 읽기 then Agent 이름·정식 배포 등록 버튼·공개 주소 미리보기가 있다', () => {
  // given / when / then
  assert.ok(HTML.includes('BuildIt 가이드'));
  assert.equal((HTML.match(/>홈페이지 정식 배포 등록</g) || []).length >= 3, true);
  assert.ok(HTML.includes('id="ahConfirmAddr"'));
  assert.doesNotMatch(HTML, />게시하기</);
});

test('given v5 입력창 when 읽기 then 아바타·+·원형 전송 버튼 구조다', () => {
  // given / when / then
  for (const needle of ['class="ah-avatar"', 'id="ahPhotoBtn"', 'class="ah-send-round" type="submit" id="ahWorkSend"']) assert.ok(HTML.includes(needle), needle);
});

test('given 공개 직후 when 읽기 then 바로가기·주소 복사·계속 수정하기 카드를 만든다', () => {
  // given / when / then
  for (const label of ['홈페이지 바로가기', '주소 복사', '계속 수정하기']) assert.ok(JS.includes(label), label);
});

test('given 더보기 메뉴 when 읽기 then 보조 기능 5개가 메뉴 안에 있고 열고 닫는 JS가 연결돼 있다', () => {
  // given / when / then
  for (const id of ['ahMoreBtn', 'ahMenu', 'ahUndo', 'ahProfileBtn', 'ahVersionsBtn', 'ahDownloadBtn', 'ahClassicWork']) assert.ok(HTML.includes('id="' + id + '"'), id);
  const menu = HTML.slice(HTML.indexOf('id="ahMenu"'), HTML.indexOf('</header>', HTML.indexOf('id="ahMenu"')));
  for (const id of ['ahUndo', 'ahProfileBtn', 'ahVersionsBtn', 'ahDownloadBtn', 'ahClassicWork']) assert.ok(menu.includes('id="' + id + '"'), id + ' 는 메뉴 안에 있어야 한다');
  assert.match(JS, /function setMenu\(open\)/);
  assert.match(JS, /\$\('ahMoreBtn'\)\.addEventListener\('click'/);
  assert.match(JS, /e\.key !== 'Escape'[\s\S]*ahMenu/);
});

test('given 미리보기 카드 when 읽기 then 카드 폭에 맞춰 PC·모바일 배치로 크게 그리고 전체 화면 버튼이 주요 버튼이다', () => {
  // given / when / then
  assert.match(JS, /function fitCard\(\)/);
  assert.match(JS, /desktop = w >= 560, lw = desktop \? 1280 : 390/);
  assert.match(HTML, /class="ah-primary ah-pill-btn"[^>]*id="ahCardFull"/);
});

test('given 추천 요청 when 읽기 then 3개 이하로 줄여 화면을 단순하게 둔다', () => {
  // given
  const m = JS.match(/var CHIPS = \[([^\]]*)\]/);
  // when
  const n = m[1].split("'").filter((_, i) => i % 2 === 1).length;
  // then
  assert.ok(n <= 3, '추천 요청 수: ' + n);
});
