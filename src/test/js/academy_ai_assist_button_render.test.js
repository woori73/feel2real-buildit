const assert = require('node:assert/strict');
const test = require('node:test');
const { loadRenderHooks } = require('./helpers/academy_render_hooks');
const { loadSectionsModule } = require('./helpers/browser_script_harness');
const { parseHtml, MiniElement, dispatchClick } = require('./helpers/mini_dom');

/**
 * 회귀: PR #176(Phase 4) 배포 후 "AI로 다듬기" 버튼이 원장 소개/학습 철학 어디에도 표시되지
 * 않았다.
 *
 * Root cause: 버튼이 "텍스트 편집 모드"에 들어가야만 나타나는 플로팅 툴바
 * (renderSectionTextApply → appendAiAssistControl)에만 동적으로 붙어 있었다. 이 클릭→생성
 * 경로 자체는 정상이었다(mini_dom으로 직접 재현해 확인) — 진짜 문제는 그 경로가 발견하기
 * 어렵고(클릭해야만 존재), 직전 섹션 검증 실패 등으로 편집 모드 진입 자체가 막힐 수 있어
 * "버튼이 아예 없는 것처럼" 보였다는 것이다.
 *
 * 수정: academy_sections.js(ctx.aiAssistButton)가 정적 마크업에 항상 렌더한다 — 클릭이나
 * 편집 모드 진입과 무관하게 항상 존재하고 항상 보인다. 이 테스트는 (1) 클릭 없이 렌더만
 * 해도 버튼이 있는지, (2) 그 버튼을 실제로 클릭하면 진짜 모달이 열리는지, (3) 공개 페이지
 * 렌더에는 버튼이 전혀 나오지 않는지를 검증한다 — 전부 "우회 DOM"이 아니라 실제
 * renderPrincipal/renderPhilosophy 출력과 실제 클릭 버블링을 사용한다.
 */
function buildDocument(bodyChildren) {
  const html = new MiniElement('html');
  const head = new MiniElement('head');
  const body = new MiniElement('body');
  html.appendChild(head);
  html.appendChild(body);
  const pageContent = new MiniElement('div');
  pageContent.setAttribute('id', 'pageContent');
  body.appendChild(pageContent);
  bodyChildren.forEach((child) => pageContent.appendChild(child));

  const document = {
    head,
    body,
    title: '',
    createElement(tag) { return new MiniElement(tag); },
    getElementById(id) { return html.querySelectorAll('#' + id)[0] || null; },
    querySelector(sel) { return html.querySelector(sel); },
    querySelectorAll(sel) { return html.querySelectorAll(sel); },
    addEventListener() {},
    createRange() { return { selectNodeContents() {} }; }
  };
  return { document, pageContent };
}

const FAKE_WINDOW = {
  getComputedStyle: () => ({ position: 'static' }),
  getSelection: () => ({ removeAllRanges() {}, addRange() {} })
};

function renderRealSectionHtml(sectionName, academy, ctxKind) {
  const SECTIONS = loadSectionsModule();
  const view = SECTIONS.toSectionView(academy);
  const ctx = ctxKind === 'public'
    ? SECTIONS.createPublicContext({ slug: () => academy.slug, showcase: () => ({ render: () => '' }), videos: () => [] })
    : SECTIONS.createBuilderContext({ slug: () => academy.slug, showcase: () => ({ render: () => '' }), videos: () => [] });
  const renderer = sectionName === 'principal' ? SECTIONS.renderPrincipal : SECTIONS.renderPhilosophy;
  return renderer(view, ctx);
}

function academyFixture() {
  return {
    name: '테스트학원', slug: 'test-academy', intro: '학원 소개', logoUrl: null,
    subjects: [{ nameKo: '영어' }], regions: [{ displayName: '강남구' }], targetGroups: ['MIDDLE'],
    lessons: [], heroMediaSlots: [],
    pageSettings: { sectionVisibility: {}, builderDraft: {} }
  };
}

function buildRealPageContent(academy, ctxKind) {
  const principalNodes = parseHtml(renderRealSectionHtml('principal', academy, ctxKind)).children;
  const philosophyNodes = parseHtml(renderRealSectionHtml('philosophy', academy, ctxKind)).children;
  return buildDocument([...principalNodes, ...philosophyNodes]);
}

function findSection(pageContent, sectionKey) {
  return pageContent.querySelector('[data-builder-section-key="' + sectionKey + '"]');
}

function findAiButtons(scope) {
  return scope.querySelectorAll('[data-ai-suggest-trigger]');
}

test('given 빌더 캔버스 렌더(클릭 전) when 원장 소개/학습 철학을 확인 then AI로 다듬기 버튼이 이미 존재한다(클릭 불필요)', () => {
  const academy = academyFixture();
  const { pageContent } = buildRealPageContent(academy, 'builder');

  const principal = findSection(pageContent, 'sec-principal');
  const philosophy = findSection(pageContent, 'sec-philosophy');
  assert.ok(principal, '원장 소개 섹션을 찾지 못함');
  assert.ok(philosophy, '학습 철학 섹션을 찾지 못함');

  const principalButtons = findAiButtons(principal);
  assert.equal(principalButtons.length, 1, '원장 소개에 AI 버튼이 정확히 1개 있어야 한다');
  assert.equal(principalButtons[0].getAttribute('data-ai-suggest-task'), 'PRINCIPAL_INTRO');
  assert.equal(principalButtons[0].getAttribute('data-ai-suggest-target'), 'principalDesc');
  assert.match(principalButtons[0].textContent, /AI로 다듬기/);

  // 철학 항목은 4개 카드 각각에 자기 target을 가진 버튼이 있어야 한다(활성 필드 추론 불필요).
  const philosophyButtons = findAiButtons(philosophy);
  assert.equal(philosophyButtons.length, 4, '학습 철학 4항목 각각에 AI 버튼이 있어야 한다');
  assert.deepEqual(
    Array.from(philosophyButtons).map((b) => b.getAttribute('data-ai-suggest-target')),
    ['philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4']);
  philosophyButtons.forEach((b) => assert.equal(b.getAttribute('data-ai-suggest-task'), 'PHILOSOPHY_ITEM'));
});

test('given 공개 Academy 렌더 when 원장 소개/학습 철학을 확인 then AI로 다듬기 버튼이 전혀 없다(방문자 노출 금지)', () => {
  const academy = academyFixture();
  const { pageContent } = buildRealPageContent(academy, 'public');

  const principal = findSection(pageContent, 'sec-principal');
  const philosophy = findSection(pageContent, 'sec-philosophy');
  assert.equal(findAiButtons(principal).length, 0, '공개 원장 소개에 AI 버튼이 새어나가면 안 된다');
  assert.equal(findAiButtons(philosophy).length, 0, '공개 학습 철학에 AI 버튼이 새어나가면 안 된다');
});

test('given 정적으로 렌더된 AI 버튼 when 실제로 클릭 then 클릭 버블링을 타고 모달이 열린다(우회 호출 아님)', () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  // 모달 관련 요소도 실제 DOM에 존재해야 openAiSuggestionModal이 정상 동작한다.
  const modal = new MiniElement('div');
  modal.setAttribute('id', 'builderAiSuggestionModal');
  document.body.appendChild(modal);
  ['builderAiSuggestionCurrent', 'builderAiSuggestionResult', 'builderAiSuggestionApplyBtn',
    'builderAiSuggestionRegenerateBtn', 'builderAiSuggestionLoading', 'builderAiSuggestionError'
  ].forEach((id) => {
    const el = new MiniElement('div');
    el.setAttribute('id', id);
    document.body.appendChild(el);
  });

  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); }
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();

  const principal = findSection(pageContent, 'sec-principal');
  const button = findAiButtons(principal)[0];
  assert.ok(button, 'AI로 다듬기 버튼이 렌더되지 않음');

  // 우회(hooks.openAiSuggestionModal을 직접 호출)하지 않고, 실제 버튼을 클릭한다.
  dispatchClick(button, pageContent);

  assert.equal(modal.classList.contains('is-open'), true, '실제 버튼 클릭으로 모달이 열리지 않음');
  assert.equal(fetchCalls.length, 1, '클릭 시 제안 생성 요청이 나가지 않음');
  // 이 fixture는 builderDraft.principalDesc를 아예 설정하지 않았다 — 렌더된 HTML의
  // placeholder 문구가 아니라 실제 저장값(빈 문자열)을 currentValue로 보내야 한다.
  const body = JSON.parse(fetchCalls[0].init.body);
  assert.deepEqual(body, { task: 'PRINCIPAL_INTRO', target: 'principalDesc', currentValue: '' });
});

test('given 4개 철학 카드의 서로 다른 버튼 when 각각 클릭 then 각 버튼이 자신의 target으로만 제안을 요청한다', () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const modal = new MiniElement('div');
  modal.setAttribute('id', 'builderAiSuggestionModal');
  document.body.appendChild(modal);
  ['builderAiSuggestionCurrent', 'builderAiSuggestionResult', 'builderAiSuggestionApplyBtn',
    'builderAiSuggestionRegenerateBtn', 'builderAiSuggestionLoading', 'builderAiSuggestionError'
  ].forEach((id) => {
    const el = new MiniElement('div');
    el.setAttribute('id', id);
    document.body.appendChild(el);
  });

  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async (url, init) => { fetchCalls.push(JSON.parse(init.body)); return new Promise(() => {}); }
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();

  const philosophy = findSection(pageContent, 'sec-philosophy');
  const buttons = findAiButtons(philosophy);
  dispatchClick(buttons[2], pageContent); // philosophyItem3

  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].target, 'philosophyItem3');
});

/**
 * 완결된 실제 사용자 흐름: 실제 트리거 버튼 클릭 → Before/After 도착 → 실제 [적용]/[취소]
 * 버튼 클릭. 전부 dispatchClick으로 진짜 클릭 버블링을 태우고, hooks의 내부 함수를
 * 직접 부르지 않는다.
 */
function buildFullModal(document) {
  const modal = new MiniElement('div');
  modal.setAttribute('id', 'builderAiSuggestionModal');
  const closeBtn = new MiniElement('button');
  closeBtn.setAttribute('data-ai-suggestion-close', '');
  modal.appendChild(closeBtn);
  document.body.appendChild(modal);
  const ids = ['builderAiSuggestionCurrent', 'builderAiSuggestionResult', 'builderAiSuggestionApplyBtn',
    'builderAiSuggestionRegenerateBtn', 'builderAiSuggestionLoading', 'builderAiSuggestionError'];
  const els = {};
  ids.forEach((id) => {
    const el = new MiniElement(id === 'builderAiSuggestionApplyBtn' || id === 'builderAiSuggestionRegenerateBtn' ? 'button' : 'div');
    el.setAttribute('id', id);
    document.body.appendChild(el);
    els[id] = el;
  });
  return { modal, closeBtn, ...els };
}

test('given 원장 소개 when 실제 버튼으로 생성→적용까지 끝까지 클릭으로 완주 then builderDraft가 반영되고 모달이 닫힌다', async () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const { modal, builderAiSuggestionApplyBtn } = buildFullModal(document);

  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async () => ({ ok: true, json: async () => ({ suggestedValue: 'AI가 다듬은 원장 소개문' }) })
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();
  hooks.wireAiSuggestionEditing();

  const principal = findSection(pageContent, 'sec-principal');
  const trigger = findAiButtons(principal)[0];
  dispatchClick(trigger, pageContent);
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(modal.classList.contains('is-open'), true, 'Before/After 모달이 열리지 않음');
  assert.equal(hooks.getAiSuggestionState().suggestedValue, 'AI가 다듬은 원장 소개문');
  // Apply 전에는 아직 반영되지 않아야 한다.
  assert.equal(academy.pageSettings.builderDraft.principalDesc, undefined);

  // 실제 [적용] 버튼을 클릭한다(hooks.applyAiSuggestionToBuilder 직접 호출 아님).
  dispatchClick(builderAiSuggestionApplyBtn, document.body);

  assert.equal(academy.pageSettings.builderDraft.principalDesc, 'AI가 다듬은 원장 소개문');
  assert.equal(modal.classList.contains('is-open'), false, '적용 후 모달이 닫혀야 한다');
});

/**
 * 회귀: Production에서 AI 추천이 정상 표시되는데도 [적용]을 눌러도 아무 반응이 없었다(모달도
 * 안 닫힘). Root cause — setAiSuggestionLoading(false)가 loading 인자와 무관하게 항상
 * setAiSuggestionApplyEnabled(false)를 불렀다: 생성 성공 시 requestAiSuggestion이 Apply를
 * 켜자마자(setAiSuggestionApplyEnabled(true)), 바로 뒤 finally의 setAiSuggestionLoading(false)가
 * 곧바로 다시 꺼버렸다. 버튼은 화면엔 그대로 보이지만 실제 DOM은 disabled라 실제 브라우저는
 * 클릭 이벤트 자체를 발생시키지 않는다(콘솔 에러도 없다) — 그래서 "눌러도 반응 없음"으로
 * 보였다. 기존 mini_dom의 dispatchClick은 disabled를 검사하지 않아 이 버그를 통과시켰다
 * (그래서 disabled 반영 + 클릭 차단을 mini_dom.js 자체에 추가했다). 이 테스트는 실제
 * dispatchClick으로 Apply를 눌러 값이 실제로 반영되는지 + disabled 상태 자체를 함께
 * 확인한다 — 하나라도 회귀하면 여기서 잡힌다.
 */
test('given 생성 성공 직후 when 적용 버튼 상태를 확인 then disabled가 아니라 실제 클릭이 통과한다(로딩 종료가 다시 잠그면 안 됨)', async () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const { modal, builderAiSuggestionApplyBtn } = buildFullModal(document);

  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async () => ({ ok: true, json: async () => ({ suggestedValue: '다듬은 문구 1차' }) })
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();
  hooks.wireAiSuggestionEditing();

  const principal = findSection(pageContent, 'sec-principal');
  dispatchClick(findAiButtons(principal)[0], pageContent);
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(builderAiSuggestionApplyBtn.disabled, false,
    '생성 성공 직후 적용 버튼이 disabled로 남아 있다(loading 종료 처리가 다시 잠갔을 가능성)');

  dispatchClick(builderAiSuggestionApplyBtn, document.body);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '다듬은 문구 1차',
    '실제 클릭이 disabled에 막혀 적용되지 않음');
  assert.equal(modal.classList.contains('is-open'), false);
});

test('given [다시 생성]으로 두 번째 제안을 받음 when 적용 버튼 상태를 확인 then 재생성 후에도 클릭이 통과한다', async () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const { modal, builderAiSuggestionApplyBtn, builderAiSuggestionRegenerateBtn } = buildFullModal(document);

  let call = 0;
  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async () => {
      call += 1;
      return { ok: true, json: async () => ({ suggestedValue: call === 1 ? '1차 제안' : '2차 제안' }) };
    }
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();
  hooks.wireAiSuggestionEditing();

  const principal = findSection(pageContent, 'sec-principal');
  dispatchClick(findAiButtons(principal)[0], pageContent);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(hooks.getAiSuggestionState().suggestedValue, '1차 제안');

  // 실제 [다시 생성] 버튼을 클릭한다.
  dispatchClick(builderAiSuggestionRegenerateBtn, document.body);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(hooks.getAiSuggestionState().suggestedValue, '2차 제안');

  assert.equal(builderAiSuggestionApplyBtn.disabled, false,
    '다시 생성 후 적용 버튼이 disabled로 남아 있다');
  dispatchClick(builderAiSuggestionApplyBtn, document.body);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '2차 제안');
  assert.equal(modal.classList.contains('is-open'), false);
});

test('given 학습 철학 항목 when 실제 버튼으로 생성 후 [취소]를 실제로 클릭 then builderDraft는 바뀌지 않고 모달만 닫힌다', async () => {
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const { modal, closeBtn } = buildFullModal(document);

  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async () => ({ ok: true, json: async () => ({ suggestedValue: '다듬은 철학 문구' }) })
  });
  hooks.setPreview(academy);
  hooks.wireAiAssistTriggers();
  hooks.wireAiSuggestionEditing();

  const philosophy = findSection(pageContent, 'sec-philosophy');
  const trigger = findAiButtons(philosophy)[0]; // philosophyItem1
  dispatchClick(trigger, pageContent);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(modal.classList.contains('is-open'), true);

  // 실제 [취소] 버튼을 클릭한다.
  dispatchClick(closeBtn, document.body);

  assert.equal(modal.classList.contains('is-open'), false, '취소 후 모달이 닫혀야 한다');
  assert.equal(academy.pageSettings.builderDraft.philosophyItem1, undefined, '취소했는데 값이 반영되면 안 된다');
});

test('given 실제 init() 순서대로 모든 클릭 배선(텍스트 편집/섹션 선택/AI 트리거)을 함께 건 상태 when AI 버튼을 클릭 then 다른 핸들러와 충돌 없이 AI 모달만 연다', async () => {
  // 실제 production init()은 wireTextEditing → ... → wireAiAssistTriggers → ... →
  // wirePreviewSectionSelection 순서로 전부 같은 pageContent에 클릭 리스너를 건다. AI 버튼도
  // <button>이라 isActualEditTarget()의 'button' 항목에 걸려 wirePreviewSectionSelection이
  // 섹션 편집모드로 오인해 들어가면 안 된다 — 세 핸들러를 실제로 함께 걸어 확인한다.
  const academy = academyFixture();
  const { document, pageContent } = buildRealPageContent(academy, 'builder');
  const { modal } = buildFullModal(document);

  const hooks = loadRenderHooks('builder', {
    document,
    window: FAKE_WINDOW,
    fetch: async () => ({ ok: true, json: async () => ({ suggestedValue: '추천 문구' }) })
  });
  hooks.setPreview(academy);
  hooks.wireTextEditing(academy);
  hooks.wireAiAssistTriggers();
  hooks.wirePreviewSectionSelection();

  const principal = findSection(pageContent, 'sec-principal');
  const trigger = findAiButtons(principal)[0];
  dispatchClick(trigger, pageContent);
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(modal.classList.contains('is-open'), true, 'AI 모달이 열려야 한다');
  // 섹션이 "텍스트 편집 모드"로도 같이 들어가 플로팅 툴바(.builder-section-controls)까지
  // 덩달아 생기면 안 된다 — AI 버튼 클릭은 오직 AI 모달만 열어야 한다.
  assert.equal(principal.classList.contains('builder-text-section-editing'), false,
    'AI 버튼 클릭이 섹션을 텍스트 편집 모드로도 전환시켜 버림(핸들러 충돌)');
  assert.equal(principal.querySelector('.builder-section-controls'), null,
    'AI 버튼 클릭으로 편집 툴바가 함께 생기면 안 된다');
});
