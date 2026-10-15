const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * 수업상품 탭 접기/펼치기(#244 기믹).
 *
 * 탭은 닫힌 채 렌더되고, 클릭하면 그 패널이 열린다. 열린 탭을 다시 누르면 통째로 닫히고,
 * 다른 탭을 누르면 닫지 않고 내용만 바꾼다. jsdom 없이 toggle()이 만지는 DOM API만 흉내 낸다.
 */
function loadShowcase() {
  const file = path.join(
    __dirname, '../../main/resources/static/js/lesson_product_showcase.js');
  const source = fs.readFileSync(file, 'utf8');
  const sandbox = {
    document: {
      documentElement: { dataset: { lessonProductShowcaseWired: 'true' } },
      addEventListener() {}
    }
  };
  sandbox.window = sandbox;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  return sandbox.F2RLessonProductShowcase;
}

const showcase = loadShowcase();

function classList(initial) {
  const set = new Set(initial || []);
  return {
    contains: (name) => set.has(name),
    add: (name) => set.add(name),
    remove: (name) => set.delete(name),
    toggle: (name, on) => (on ? set.add(name) : set.delete(name))
  };
}

function node(dataset) {
  return {
    dataset,
    classList: classList(),
    attrs: {},
    hidden: true,
    setAttribute(name, value) { this.attrs[name] = value; }
  };
}

/** 탭 2개짜리 쇼케이스의 최소 DOM. */
function fakeShowcase() {
  const tabs = [node({ lessonIndex: '0', theme: 'NAVY' }), node({ lessonIndex: '1', theme: 'TEAL' })];
  const panels = [node({ lessonPanel: '0' }), node({ lessonPanel: '1' })];
  const root = {
    dataset: { activeIndex: '', theme: 'NAVY' },
    classList: classList(),
    querySelectorAll: (selector) => (selector === '[data-lesson-showcase-tab]' ? tabs : panels)
  };
  tabs.forEach((tab) => { tab.closest = () => root; });
  return { root, tabs, panels };
}

test('given 닫힌 탭 when 탭을 클릭 then 해당 패널이 열린다', () => {
  // given
  const { root, tabs, panels } = fakeShowcase();

  // when
  showcase.toggle(tabs[0], 0);

  // then
  assert.equal(root.classList.contains('is-expanded'), true, '펼침 상태가 아님');
  assert.equal(root.dataset.activeIndex, '0');
  assert.equal(panels[0].hidden, false, '선택한 패널이 열리지 않음');
  assert.equal(panels[1].hidden, true, '선택하지 않은 패널이 열림');
  assert.equal(tabs[0].attrs['aria-expanded'], 'true');
});

test('given 열린 탭 when 같은 탭을 다시 클릭 then 통째로 닫힌다', () => {
  // given: 한 번 열어둔 상태
  const { root, tabs, panels } = fakeShowcase();
  showcase.toggle(tabs[0], 0);

  // when
  showcase.toggle(tabs[0], 0);

  // then
  assert.equal(root.classList.contains('is-expanded'), false, '같은 탭 재클릭인데 닫히지 않음');
  assert.equal(root.dataset.activeIndex, '');
  assert.equal(tabs[0].classList.contains('active'), false);
  assert.equal(tabs[0].attrs['aria-expanded'], 'false');
  assert.equal(panels[0].attrs['aria-hidden'], 'true');
});

test('given 열린 탭 when 다른 탭을 클릭 then 닫지 않고 내용만 바꾼다', () => {
  // given
  const { root, tabs, panels } = fakeShowcase();
  showcase.toggle(tabs[0], 0);

  // when
  showcase.toggle(tabs[1], 1);

  // then
  assert.equal(root.classList.contains('is-expanded'), true, '다른 탭 선택인데 닫힘');
  assert.equal(root.dataset.activeIndex, '1');
  assert.equal(root.dataset.theme, 'TEAL', '탭 테마가 쇼케이스에 반영되지 않음');
  assert.equal(panels[0].hidden, true);
  assert.equal(panels[1].hidden, false);
  assert.equal(tabs[0].classList.contains('active'), false);
  assert.equal(tabs[1].classList.contains('active'), true);
});

test('given 렌더 결과 when 초기 상태를 확인 then 모든 패널이 닫혀 있다', () => {
  // given / when
  const html = showcase.render(
    [{ lessonId: 1, name: '중2 내신반', theme: 'NAVY' }, { lessonId: 2, name: '고1 독해반', theme: 'NAVY' }],
    { slug: 'my-academy' });

  // then
  assert.equal(html.includes('class="lesson-product-showcase"'), true);
  assert.equal(html.includes('is-expanded'), false, '처음부터 펼쳐진 채로 렌더됨');
  assert.equal(html.includes('data-active-index=""'), true);
  assert.equal((html.match(/aria-hidden="true" hidden/g) || []).length, 2, '닫히지 않은 패널이 있음');
  assert.equal(html.includes('lesson-product-tab-hint'), true, '클릭 안내 문구가 없음');
});
