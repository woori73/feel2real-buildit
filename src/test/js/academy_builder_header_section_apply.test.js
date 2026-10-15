const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadBrowserScript } = require('./helpers/browser_script_harness');
const { BUILDER_JS, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * 헤더 '적용' 버튼 = 섹션 컨트롤 "적용"의 미러.
 *
 * 섹션 컨트롤은 섹션 박스의 top:-14px에 absolute로 붙는다(renderSectionTextApply). 그래서
 * sec-intro처럼 긴 섹션의 아래쪽을 편집하면 컨트롤이 캔버스 스크롤 영역 위로 밀려나 원장이
 * 적용 버튼을 아예 보지 못한다. 편집 중에는 항상 보이는 헤더에도 같은 동작을 노출한다.
 *
 * 이 버튼은 activeTextEditSection을 그대로 비추므로, 대입 지점(편집 진입 / 선택 해제 /
 * 캔버스 재렌더)이 전부 setActiveTextEditSection setter를 타야 한다. 한 곳만 새면 편집이
 * 끝난 뒤에도 버튼이 남아 이미 사라진 섹션에 적용을 시도하게 된다.
 */

const builderJs = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'), 'utf8');
const builderTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'), 'utf8');

/** 헤더 '적용' 버튼과 캔버스를 함께 가진 최소 document 스텁. */
function documentWithHeaderApply() {
  const label = { textContent: '적용' };
  const applyBtn = {
    hidden: true,
    dataset: {},
    addEventListener() {},
    querySelector(selector) { return selector === '[data-section-apply-label]' ? label : null; }
  };
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
    applyBtn,
    label,
    document: {
      addEventListener() {},
      getElementById(id) {
        if (id === 'pageContent') return pageContent;
        if (id === 'builderSectionApplyBtn') return applyBtn;
        return null;
      },
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
  const { applyBtn, label, document } = documentWithHeaderApply();
  const { window } = loadBrowserScript(BUILDER_JS, { document });
  return { hooks: window.F2RAcademyRenderTestHooks, applyBtn, label };
}

/** classList만 있으면 setter가 다루기에 충분한 섹션 스텁. */
function sectionStub(sectionKey) {
  return { dataset: { builderSectionKey: sectionKey }, classList: { add() {}, remove() {} } };
}

test('given 섹션 편집에 진입할 때 when 편집 상태가 잡히면 then 헤더 적용 버튼이 섹션명과 함께 나타난다', () => {
  // given
  const { hooks, applyBtn, label } = loadBuilder();
  assert.equal(applyBtn.hidden, true, '사전 조건: 편집 전에는 숨어 있어야 한다');

  // when
  hooks.setActiveTextEditSection(sectionStub('sec-intro'));

  // then — '발행'과 역할이 헷갈리지 않게 대상 섹션명을 라벨에 넣는다
  assert.equal(applyBtn.hidden, false);
  assert.equal(label.textContent, '학원 소개 적용');
});

test('given 편집 상태가 풀릴 때 when 캔버스를 재렌더 then 헤더 적용 버튼도 함께 사라진다', () => {
  // given — 편집 중인 섹션이 잡혀 헤더 버튼이 떠 있는 상태
  const { hooks, applyBtn } = loadBuilder();
  hooks.setActiveTextEditSection(sectionStub('sec-intro'));
  assert.equal(applyBtn.hidden, false, '사전 조건: 버튼이 떠 있어야 한다');

  // when — 섹션 토글·순서변경·저장 등이 유발하는 캔버스 재렌더가 편집 참조를 끊는다
  hooks.renderPublicPreview(academyFixture());

  // then — 사라진 섹션에 적용을 시도할 수 없게 버튼도 내려간다
  assert.equal(hooks.getActiveTextEditSection(), null);
  assert.equal(applyBtn.hidden, true, '편집이 끝났는데 헤더 적용 버튼이 남아 있다');
});

test('given 섹션명 맵에 없는 섹션일 때 when 편집에 진입 then 라벨은 기본 문구로 떨어진다', () => {
  // given — GNB(sec-header)는 STYLE_PANEL_SECTION_NAMES에 없다
  const { hooks, applyBtn, label } = loadBuilder();

  // when
  hooks.setActiveTextEditSection(sectionStub('sec-header'));

  // then — 'undefined 적용'이 노출되지 않는다
  assert.equal(applyBtn.hidden, false);
  assert.equal(label.textContent, '적용');
});

test('given 헤더 적용 버튼 when 마크업과 배선을 확인 then 발행 왼쪽에 있고 활성 섹션에 적용을 건다', () => {
  // given / when / then — 헤더 액션 영역에서 '적용'이 '발행'보다 앞에 온다
  const actions = builderTemplate.slice(
    builderTemplate.indexOf('<div class="tb-actions">'),
    builderTemplate.indexOf('</header>')
  );
  assert.ok(
    actions.indexOf('builderSectionApplyBtn') < actions.indexOf('builderPublishBtn'),
    "'적용'이 '발행' 왼쪽에 없다"
  );
  // 기본 상태는 숨김이어야 한다 — 편집 중이 아닐 때 뜨면 무엇에 적용하는지 알 수 없다.
  assert.match(actions, /id="builderSectionApplyBtn"[^>]*hidden/);
  assert.match(builderTemplate, /\.tb-btn\[hidden\]\{display:none;\}/);

  // then — 클릭은 활성 섹션의 적용 경로를 그대로 태운다(섹션 컨트롤과 같은 동작)
  assert.match(builderJs, /if \(activeTextEditSection\) applySectionTextChanges\(activeTextEditSection\);/);
  assert.match(builderJs, /wireHeaderSectionApply\(\);/);

  // then — 섹션 컨트롤은 그대로 남는다. '적용'만 헤더로 옮기면 섹션에 '삭제'만 남아
  //        눈에 보이는 유일한 컨트롤이 삭제가 된다.
  assert.match(builderJs, /sectionControlButton\('fa-check', '적용'/);
  assert.match(builderJs, /sectionControlButton\('fa-trash', '삭제'/);

  // then — 편집 상태 대입은 setter 한 곳으로만 흐른다(미러가 어긋나지 않는 근거).
  //        선언(let)을 뺀 대입이 2개 이상이면 setter를 우회하는 경로가 생긴 것이다.
  assert.equal((builderJs.match(/(?<!let )activeTextEditSection = /g) || []).length, 1);
  assert.match(builderJs, /function setActiveTextEditSection\(section\) \{\n\s+activeTextEditSection = section \|\| null;\n\s+syncHeaderSectionApply\(\);/);
});
