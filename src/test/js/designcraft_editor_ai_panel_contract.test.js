const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const ROOT = path.join(__dirname, '../../main/resources');
const HTML = fs.readFileSync(path.join(ROOT, 'templates/designcraft/editor.html'), 'utf8');
const CSS = fs.readFileSync(path.join(ROOT, 'static/css/publishing_editor.css'), 'utf8');
const JS = fs.readFileSync(path.join(ROOT, 'static/js/publishing_editor.js'), 'utf8');

/** 편집기 스크립트를 최소 DOM 스텁으로 로드한다(init은 DOMContentLoaded 대기 상태로 두어 실행되지 않게 한다). */
function loadEditor() {
  const win = { PublishingApi: {} };
  const ctx = vm.createContext({
    window: win,
    document: { readyState: 'loading', addEventListener() {} },
    console
  });
  vm.runInContext(JS, ctx);
  return win.PublishingEditor;
}

test('AI 진입점: 상단 버튼·모바일 FAB·닫기 버튼이 편집기 화면에 있다', () => {
  // given: 편집기 템플릿
  // when/then
  for (const id of ['btn-ai-open', 'ai-fab', 'btn-ai-close']) {
    assert.match(HTML, new RegExp('id="' + id + '"'), id);
  }
});

test('AI 탭이 첫 번째이며 기본 활성이다', () => {
  // given: 템플릿의 탭 패널
  // when
  const aiActive = /<section[^>]*id="tab-ai"[^>]*>/.exec(HTML)[0];
  const styleTag = /<section[^>]*id="tab-style"[^>]*>/.exec(HTML)[0];
  // then
  assert.match(aiActive, /is-active/);
  assert.doesNotMatch(styleTag, /is-active/);
  assert.ok(HTML.indexOf('data-tab="ai"') < HTML.indexOf('data-tab="style"'));
});

test('조판 엔진 오류용 지속 배너(재시도·목록 복귀)가 있다', () => {
  // given/when/then
  for (const id of ['editor-error', 'editor-error-msg', 'btn-error-retry', 'btn-error-back']) {
    assert.match(HTML, new RegExp('id="' + id + '"'), id);
  }
  assert.match(JS, /function showEditorError/);
});

test('모바일은 하단 시트로 열고 닫는다(body.ai-open)', () => {
  // given/when/then
  assert.match(CSS, /body\.ai-open\s+\.panel-right/);
  assert.match(CSS, /\.ai-fab/);
});

test('AI 예시 칩은 입력창에 채워 넣는 data 속성을 쓴다', () => {
  // given/when/then
  assert.match(HTML, /data-ai-example="/);
  assert.match(JS, /data-ai-example/);
});

test('aiErrorMessage: 상태 코드별로 사용자 안내 문장을 돌려준다', () => {
  // given
  const S = loadEditor();
  // when
  const m503 = S.aiErrorMessage(503);
  const m502 = S.aiErrorMessage(502);
  const m401 = S.aiErrorMessage(401);
  const other = S.aiErrorMessage(undefined);
  // then
  assert.match(m503, /직접 편집/);
  assert.match(m502, /다시 시도/);
  assert.match(m401, /로그인/);
  assert.match(other, /네트워크/);
});
