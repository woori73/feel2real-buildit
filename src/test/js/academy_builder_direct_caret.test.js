const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { loadBrowserScript } = require('./helpers/browser_script_harness');

const BUILDER = path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js');

/**
 * Phase 5B — Direct Text Editing UX.
 *
 * Production 버그: 기존 문구 일부만 고치려고 클릭해도 focusEditableTarget이
 * selectNodeContents로 문구 "전체"를 selection 해버려, 사용자가 문장 끝으로 간 뒤 방향키로
 * 되돌아와야 했다. 수정 후에는 클릭 위치에 caret 하나만 놓아야 한다(전체 선택 금지).
 */

function fakeSelection() {
  const calls = { removeAllRanges: 0, addRange: [] };
  return {
    calls,
    removeAllRanges() { calls.removeAllRanges++; },
    addRange(range) { calls.addRange.push(range); }
  };
}

function fakeRange() {
  const range = {
    startContainer: null,
    selectNodeContentsCalled: false,
    collapseArg: undefined,
    selectNodeContents() { range.selectNodeContentsCalled = true; },
    collapse(toEnd) { range.collapseArg = toEnd; },
    setStart(node) { range.startContainer = node; }
  };
  return range;
}

function loadWithDom(overrides = {}) {
  const createdRanges = [];
  const selection = fakeSelection();
  const document = {
    addEventListener() {},
    getElementById() { return null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() {
      return { dataset: {}, style: {}, classList: { add() {}, remove() {}, toggle() {} }, setAttribute() {}, appendChild() {} };
    },
    createRange() { const r = fakeRange(); createdRanges.push(r); return r; },
    head: { appendChild() {} },
    ...overrides.document
  };
  const { window } = loadBrowserScript(BUILDER, {
    document,
    window: { getSelection: () => selection, ...overrides.window }
  });
  return { hooks: window.F2RAcademyRenderTestHooks, selection, createdRanges, document };
}

function fakeTarget() {
  return {
    dataset: { builderDraft: 'principalDesc' },
    classList: { add() {}, remove() {} },
    _contains: null,
    contains(node) { return node === this._contains; },
    setAttribute() {},
    focus() {}
  };
}

test('클릭 좌표가 주어지면 그 지점 range를 그대로 selection에 넣고, 문구 전체를 selectNodeContents 하지 않는다', () => {
  const pointRange = fakeRange();
  const { hooks, selection } = loadWithDom({
    document: {
      caretRangeFromPoint(x, y) {
        assert.equal(x, 120);
        assert.equal(y, 42);
        return pointRange;
      }
    }
  });
  const target = fakeTarget();
  const clickedNode = { nodeType: 3 };
  target._contains = clickedNode;
  pointRange.startContainer = clickedNode;

  hooks.placeCaretAtPoint(target, { x: 120, y: 42 });

  assert.equal(selection.calls.addRange.length, 1);
  assert.equal(selection.calls.addRange[0], pointRange, '클릭 지점 range가 그대로 들어가야 한다');
  assert.equal(pointRange.selectNodeContentsCalled, false, '전체 선택(selectNodeContents) 금지');
});

test('좌표가 없으면 문장 끝으로 collapse 한다 — 전체 선택이 아니다', () => {
  const { hooks, selection, createdRanges } = loadWithDom();
  const target = fakeTarget();

  hooks.placeCaretAtPoint(target, undefined);

  assert.equal(createdRanges.length, 1);
  const range = createdRanges[0];
  assert.equal(range.selectNodeContentsCalled, true);
  assert.equal(range.collapseArg, false, 'collapse(false) = 끝으로 접어야 한다(전체 선택 유지 금지)');
  assert.equal(selection.calls.addRange[0], range);
});

test('클릭 지점이 대상 밖이면 그 range를 버리고 끝으로 collapse 한다', () => {
  const outsideRange = fakeRange();
  outsideRange.startContainer = { nodeType: 3 };
  const { hooks, createdRanges } = loadWithDom({
    document: { caretRangeFromPoint: () => outsideRange }
  });
  const target = fakeTarget();
  target._contains = { different: true };

  hooks.placeCaretAtPoint(target, { x: 5, y: 5 });

  assert.equal(createdRanges.length, 1, '밖이면 fallback range를 새로 만든다');
  assert.equal(createdRanges[0].collapseArg, false);
});

test('caretRangeFromPoint: 좌표가 숫자가 아니면 null (fallback으로 넘어간다)', () => {
  const { hooks } = loadWithDom({
    document: { caretRangeFromPoint: () => ({}) }
  });
  assert.equal(hooks.caretRangeFromPoint(undefined, undefined), null);
  assert.equal(hooks.caretRangeFromPoint('x', 3), null);
});

test('caretPositionFromPoint만 있는 브라우저(Firefox 계열)도 지원한다', () => {
  const node = { nodeType: 3 };
  const { hooks } = loadWithDom({
    document: {
      caretPositionFromPoint: (x, y) => ({ offsetNode: node, offset: 4 })
    }
  });
  const range = hooks.caretRangeFromPoint(10, 10);
  assert.equal(range.startContainer, node);
  assert.equal(range.collapseArg, true);
});

test('빌더 JS 소스에는 클릭 편집 시 문구 전체를 재선택하는 옛 경로가 없다', () => {
  const fs = require('node:fs');
  const src = fs.readFileSync(BUILDER, 'utf8');
  // 편집 중인 대상을 다시 클릭하면 브라우저가 caret을 놓게 그대로 둔다(재진입 시 preventDefault 금지).
  assert.ok(src.includes("target.getAttribute('contenteditable') === 'true' && activeTextEditSection === section"),
    '편집 중 재클릭 시 native caret을 허용하는 가드가 있어야 한다');
  assert.ok(src.includes('placeCaretAtPoint(target, point)'),
    'focusEditableTarget이 클릭 좌표를 caret 배치에 사용해야 한다');
  assert.ok(src.includes('const point = { x: event.clientX, y: event.clientY }'),
    '클릭 핸들러가 클릭 좌표를 캡처해야 한다');
});
