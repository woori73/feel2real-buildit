const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const template = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'),
  'utf8'
);

function inlineSetViewScript() {
  const match = template.match(/function setView\(mode, btn\)\{[\s\S]*?\n\}/);
  assert.ok(match, 'actual inline script containing setView must remain');
  return match[0];
}

test('given builder view toggle when rendering then only desktop and mobile choices remain', () => {
  // given / when / then
  assert.doesNotMatch(template, /setView\(['"]tablet['"]/);
  assert.match(template, /setView\('desktop',this\)/);
  assert.match(template, /setView\('mobile',this\)/);
});

test('actual inline setView keeps desktop and mobile widths with exclusive active state', () => {
  // given
  class FakeClassList {
    constructor(active = false) { this.values = new Set(active ? ['active'] : []); }
    add(value) { this.values.add(value); }
    remove(value) { this.values.delete(value); }
    contains(value) { return this.values.has(value); }
  }
  const buttons = [
    { classList: new FakeClassList(true) },
    { classList: new FakeClassList() }
  ];
  const frame = { style: {} };
  const document = {
    querySelectorAll(selector) { return selector === '.tb-view-btn' ? buttons : []; },
    getElementById(id) { return id === 'canvasFrame' ? frame : null; },
    addEventListener() {},
    querySelector() { return null; }
  };
  const context = vm.createContext({
    console,
    document,
    window: {},
    showToast() {},
    setTimeout() {}
  });
  vm.runInContext(inlineSetViewScript(), context);

  // when / then
  // 데스크탑은 canvas-area가 주는 폭을 그대로 쓴다(none). 고정 폭(900px)이면 넓은 화면에서도
  // 프리뷰가 좁아 폭 기반 @container 규칙이 항상 모바일 레이아웃으로 발화한다.
  [
    ['desktop', buttons[0], 'none'],
    ['mobile', buttons[1], '390px']
  ].forEach(([mode, button, width], selectedIndex) => {
    context.setView(mode, button);
    assert.equal(frame.style.maxWidth, width);
    assert.equal(frame.style.margin, '0 auto');
    buttons.forEach((candidate, index) => {
      assert.equal(candidate.classList.contains('active'), index === selectedIndex);
    });
  });
});
