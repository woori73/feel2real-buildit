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

test('given 더보기 메뉴 when 읽기 then 보조 기능 4개만 메뉴 안에 있고 열고 닫는 JS가 연결돼 있다', () => {
  // given
  const menu = HTML.slice(HTML.indexOf('id="ahMenu"'), HTML.indexOf('</header>', HTML.indexOf('id="ahMenu"')));
  // when / then
  for (const id of ['ahMoreBtn', 'ahMenu', 'ahProfileBtn', 'ahVersionsBtn', 'ahDownloadBtn', 'ahClassicWork']) assert.ok(HTML.includes('id="' + id + '"'), id);
  for (const id of ['ahProfileBtn', 'ahVersionsBtn', 'ahDownloadBtn', 'ahClassicWork']) assert.ok(menu.includes('id="' + id + '"'), id + ' 는 메뉴 안에 있어야 한다');
  for (const id of ['ahUndo', 'ahRedo', 'ahReset', 'ahPreviewBtn', 'ahSave']) assert.ok(!menu.includes('id="' + id + '"'), id + ' 는 메뉴 밖으로 꺼내야 한다');
  assert.match(JS, /function setMenu\(open\)/);
  assert.match(JS, /\$\('ahMoreBtn'\)\.addEventListener\('click'/);
  assert.match(JS, /e\.key !== 'Escape'[\s\S]*ahMenu/);
});

test('given 편집 도구 모음 when 읽기 then 6개 독립 버튼이 정식 배포 등록 버튼 앞에 있고 이름·툴팁을 가진다', () => {
  // given
  const bar = HTML.slice(HTML.indexOf('<header class="ah-bar">'), HTML.indexOf('</header>', HTML.indexOf('<header class="ah-bar">')));
  const tools = bar.slice(bar.indexOf('class="ah-tools"'), bar.indexOf('class="ah-bar-actions"'));
  // when / then
  const expected = [['ahNew', '새 홈페이지'], ['ahUndo', '되돌리기'], ['ahRedo', '다시 실행'], ['ahReset', '수정 초기화'], ['ahPreviewBtn', '미리보기'], ['ahSave', '저장']];
  let last = -1;
  for (const [id, label] of expected) {
    const at = tools.indexOf('id="' + id + '"');
    assert.ok(at > last, id + ' 순서');
    last = at;
    assert.ok(tools.slice(at, tools.indexOf('</button>', at)).includes(label), id + ' 라벨');
  }
  assert.equal((tools.match(/ title="/g) || []).length, 6, '툴팁 6개');
  assert.ok(bar.indexOf('id="ahPublish"') > bar.indexOf('id="ahSave"'), '배포 버튼은 편집 버튼들과 떨어진 오른쪽에 있다');
});

test('given 초기화 when 읽기 then 확인 창 문구·범위 안내가 있고 서버 reset을 호출하며 공개본은 지우지 않는다고 밝힌다', () => {
  // given / when / then
  assert.ok(HTML.includes('현재 편집 중인 변경사항을 초기화하시겠습니까?'));
  assert.ok(HTML.includes('공개 중인 홈페이지(운영 배포본)와 저장된 버전 기록은 삭제되지 않아요'));
  assert.match(JS, /editAction\('\/reset'/);
  assert.match(JS, /editAction\('\/redo'/);
  assert.match(JS, /editAction\('\/save'/);
  assert.match(JS, /\$\('ahReset'\)\.addEventListener\('click', openResetConfirm\)/);
  assert.match(JS, /\$\('ahResetOk'\)\.addEventListener\('click', doReset\)/);
});

test('given 서버 응답 when syncStack 하면 then 되돌리기·다시 실행 단계와 저장점이 화면 상태에 반영된다', () => {
  // given / when / then
  assert.match(JS, /function syncStack\(j\)/);
  assert.match(JS, /typeof j\.redoDepth === 'number'/);
  assert.match(JS, /\$\('ahRedo'\)\.disabled = ctx\.busy \|\| ctx\.redoDepth < 1/);
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

test('given 미리보기 카드 when 불러오면 then 등장 애니메이션을 최종 상태로 고정해 글자가 흐리지 않다', () => {
  // given / when / then
  assert.match(JS, /\$\('ahCardFrame'\)\.addEventListener\('load', freezeCardMotion\)/);
  assert.match(JS, /\[data-reveal-item\]\{opacity:1!important;transform:none!important\}/);
  assert.match(JS, /animation-duration:0s!important/);
});

test('given 새 홈페이지 만들기 when 읽기 then 수정 초기화와 분리된 /restart 호출이고 입력 안내 문구가 있다', () => {
  // given / when / then
  assert.ok(HTML.includes('어떤 홈페이지를 만들까요? 학원 이름, 과목, 원하는 분위기를 알려주세요.'));
  assert.match(JS, /api\('POST', '\/restart'/);
  assert.doesNotMatch(JS, /editAction\('\/restart'/);
  assert.match(JS, /\$\('ahNew'\)\.addEventListener\('click', openNew\)/);
  assert.ok(HTML.includes('저장하지 않은 변경만') || HTML.includes('수정 내용 초기화'));
});

test('given 새 홈페이지 패널 열기 when openNew 읽기 then 서버 호출 없이 기존 대화·미리보기를 가린다', () => {
  // given
  const open = JS.slice(JS.indexOf('function openNew()'), JS.indexOf('function closeNew()'));
  // when / then
  assert.doesNotMatch(open, /api\(/);
  assert.match(open, /\$\('ahThread'\)\.hidden = true/);
});

test('given 변경 내역 when 읽기 then 대화 위에 쌓지 않고 접이식 details로 둔다', () => {
  // given / when / then
  assert.match(JS, /el\('details', 'ah-log'\)/);
  assert.match(JS, /변경 기록 /);
  assert.doesNotMatch(JS, /b\.appendChild\(ul\);\s*\}\s*\$\('ahMsgs'\)/);
});
