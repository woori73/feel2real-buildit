const assert = require('node:assert/strict');
const test = require('node:test');
const { loadBrowserScript } = require('./helpers/browser_script_harness');
const { BUILDER_JS, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * 빌더 캔버스 재렌더와 인라인 텍스트 편집 상태의 정합성.
 *
 * renderPublicPreview는 pageContent.innerHTML을 통째로 교체해 캔버스의 모든 노드를
 * detach한다. 그런데 편집 중인 섹션을 가리키는 activeTextEditSection이 초기화되지 않으면
 * detach된 노드를 계속 참조하게 되고, 두 가지 장애가 난다.
 *
 *   1) 유령 쓰기 — 다음 섹션을 선택할 때 applySectionTextChanges가 detach된 노드에서
 *      옛 텍스트를 읽어 currentPreview에 커밋한다. 사라진 줄 알았던 값이 되살아난다.
 *   2) 소프트락 — 편집 값이 빈 문자열이면 focusEditableTarget(detached)이 무효라
 *      enterSectionTextEditMode가 false를 반환하고, 이후 캔버스에서 어떤 섹션도 선택할 수
 *      없다. 새로고침 전까지 복구되지 않는다.
 *
 * 재렌더 경로는 섹션 토글·순서변경·저장 등 10곳이 넘고, 신규 섹션이 늘수록 발생 확률이
 * 올라간다. 그래서 innerHTML 교체 지점에서 참조를 끊는 것으로 한 곳에서 막는다.
 */

/** innerHTML을 받아줄 최소 pageContent를 가진 document 스텁. */
function documentWithCanvas() {
  const pageContent = {
    innerHTML: '',
    dataset: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    addEventListener() {},
    querySelectorAll() { return []; },
    querySelector() { return null; },
    contains() { return false; }
  };
  return {
    pageContent,
    document: {
      addEventListener() {},
      getElementById(id) { return id === 'pageContent' ? pageContent : null; },
      querySelector() { return null; },
      querySelectorAll() { return []; },
      createElement() {
        return {
          dataset: {}, style: {},
          classList: { add() {}, remove() {}, toggle() {} },
          setAttribute() {}, appendChild() {}
        };
      },
      head: { appendChild() {} }
    }
  };
}

function loadBuilder() {
  const { pageContent, document } = documentWithCanvas();
  const { window } = loadBrowserScript(BUILDER_JS, { document });
  return { hooks: window.F2RAcademyRenderTestHooks, pageContent };
}

test('given 섹션을 편집 중일 때 when 캔버스를 재렌더 then 편집 상태 참조를 끊는다', () => {
  // given — 편집 중인 섹션이 잡혀 있는 상태
  const { hooks, pageContent } = loadBuilder();
  const detachedSection = { classList: { add() {}, remove() {} } };
  hooks.setActiveTextEditSection(detachedSection);
  assert.equal(hooks.getActiveTextEditSection(), detachedSection, '사전 조건: 편집 상태가 잡혀 있어야 한다');

  // when — 섹션 토글·순서변경·저장 등이 유발하는 캔버스 재렌더
  hooks.renderPublicPreview(academyFixture());

  // then — detach된 노드를 계속 가리키지 않는다
  assert.equal(hooks.getActiveTextEditSection(), null,
    '재렌더 후에도 편집 상태가 detach된 노드를 가리킨다 (유령 쓰기·소프트락 원인)');
  assert.ok(pageContent.innerHTML.includes('builder-public-preview'), '캔버스가 실제로 재렌더되지 않았다');
});

test('given 편집 중이 아닐 때 when 캔버스를 재렌더 then 편집 상태는 계속 비어 있다', () => {
  // given
  const { hooks } = loadBuilder();
  assert.equal(hooks.getActiveTextEditSection(), null);

  // when
  hooks.renderPublicPreview(academyFixture());

  // then
  assert.equal(hooks.getActiveTextEditSection(), null);
});

test('given 미리볼 학원이 없을 때 when 캔버스를 재렌더 then 안내를 표시하고 편집 상태를 건드리지 않는다', () => {
  // given — preview가 없으면 renderPublicPreview는 빈 상태 안내만 그리고 조기 반환한다
  const { hooks, pageContent } = loadBuilder();
  const section = { classList: { add() {}, remove() {} } };
  hooks.setActiveTextEditSection(section);

  // when
  hooks.renderPublicPreview(null);

  // then — 조기 반환 경로라 innerHTML 교체가 일어나지 않으므로 참조를 끊을 이유도 없다
  assert.ok(pageContent.innerHTML.includes('builder-preview-empty'));
  assert.equal(hooks.getActiveTextEditSection(), section);
});
