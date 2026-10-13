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
