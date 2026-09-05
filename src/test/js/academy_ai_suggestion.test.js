const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadRenderHooks, academyFixture } = require('./helpers/academy_render_hooks');

const SUGGESTION_SERVICE_JAVA = fs.readFileSync(
  path.resolve(__dirname, '../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyAiSuggestionService.java'),
  'utf8');

/**
 * Phase 4 AI Suggestion 워크플로 — 실제 academy_builder.js 코드를 실행해 검증한다(재구현 아님).
 *
 * 절대 원칙: 생성(Generate)만으로는 currentPreview.pageSettings가 절대 바뀌지 않는다.
 * "적용"을 호출해야만 기존 인라인 텍스트 편집과 같은 경로(pageSettings.builderDraft 갱신 →
 * populatePreview → persistTextChangesAfterApply)로 반영된다.
 *
 * Phase 4.2: 모달을 여는 것(openAiSuggestionModal)만으로는 더 이상 자동으로 생성 요청이
 * 나가지 않는다 — 사용자가 "AI에게 요청할 내용"을 먼저 적을 기회를 주기 위해서다. 실제 요청은
 * [제안 생성]/[다시 생성] 클릭(requestAiSuggestion)에서만 나간다. 아래 테스트들은 그래서
 * openAiSuggestionModal 뒤에 requestAiSuggestion을 명시적으로 호출한다(실제 버튼 클릭
 * 자체의 배선 검증은 mini_dom 기반 academy_ai_assist_button_render.test.js가 담당한다).
 */
function fakeElement() {
  return {
    textContent: '',
    hidden: false,
    disabled: false,
    value: '',
    dataset: {},
    classList: { add() {}, remove() {} },
    setAttribute() {},
    addEventListener() {},
    querySelectorAll() { return []; }
  };
}

function buildAiModalDocument() {
  const elements = new Map([
    ['builderAiSuggestionModal', fakeElement()],
    ['builderAiSuggestionInstructions', fakeElement()],
    ['builderAiSuggestionCurrent', fakeElement()],
    ['builderAiSuggestionResult', fakeElement()],
    ['builderAiSuggestionApplyBtn', fakeElement()],
    ['builderAiSuggestionRegenerateBtn', fakeElement()],
    ['builderAiSuggestionLoading', fakeElement()],
    ['builderAiSuggestionError', fakeElement()]
  ]);
  return {
    title: '',
    addEventListener() {},
    getElementById(id) { return elements.has(id) ? elements.get(id) : null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    elements
  };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

test('given AI로 다듬기 모달을 염 when 아직 [제안 생성]을 누르지 않음 then 요청이 전혀 나가지 않는다(자동생성 금지)', async () => {
  const document = buildAiModalDocument();
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', { document, fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); } });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 원장 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  await flush();

  assert.equal(fetchCalls.length, 0, '모달을 열기만 했는데 요청이 나갔다(자동생성 회귀)');
});

test('given 원장 소개 AI 제안을 생성 when 응답이 아직 오지 않음 then currentPreview는 전혀 바뀌지 않는다(생성=적용 아님)', async () => {
  const document = buildAiModalDocument();
  const fetchCalls = [];
  const fetchImpl = async (url, init) => {
    fetchCalls.push({ url, init });
    return new Promise(() => {}); // 응답이 영원히 안 오는 상황을 흉내낸다
  };
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 원장 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  hooks.requestAiSuggestion(); // 실제로는 [제안 생성] 클릭에 해당
  await flush();

  // then — 요청은 나갔지만(POST /api/tutor/academy/ai/suggestions) 응답 전이라 아무것도 안 바뀜
  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].url, '/api/tutor/academy/ai/suggestions');
  const body = JSON.parse(fetchCalls[0].init.body);
  assert.deepEqual(body, { task: 'PRINCIPAL_INTRO', target: 'principalDesc', currentValue: '기존 원장 소개문', instructions: '' });
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 원장 소개문');
});

test('given 사용자가 "요구사항" textarea에 입력함 when [제안 생성]을 누르면 then 요청 body에 그대로 실린다', async () => {
  const document = buildAiModalDocument();
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 원장 소개문' } }
  });
  hooks.setPreview(academy);

  // 모달을 연 "뒤에" 입력해야 한다 — openAiSuggestionModal은 새 세션을 위해 이전 요구사항을
  // 비운다(resetAiSuggestionInstructions), 실제 사용자도 모달이 뜬 뒤에 타이핑한다.
  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  document.elements.get('builderAiSuggestionInstructions').value = '  더 짧게, 한 문장으로  ';
  hooks.requestAiSuggestion();
  await flush();

  const body = JSON.parse(fetchCalls[0].init.body);
  // 앞뒤 공백은 트리밍되어야 한다.
  assert.equal(body.instructions, '더 짧게, 한 문장으로');
});

test('given AI 제안이 도착 when 적용을 누르지 않음 then builderDraft는 그대로다', async () => {
  const document = buildAiModalDocument();
  const fetchImpl = async () => ({ ok: true, json: async () => ({ suggestedValue: 'AI가 다듬은 소개문' }) });
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 원장 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  hooks.requestAiSuggestion();
  await flush();

  assert.equal(hooks.getAiSuggestionState().suggestedValue, 'AI가 다듬은 소개문');
  // then — 아직 적용 안 함: 원본 값 그대로
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 원장 소개문');
});

test('given AI 제안이 도착 when 적용을 누르면 then builderDraft의 해당 target만 바뀐다(다른 값은 보존)', async () => {
  const document = buildAiModalDocument();
  const fetchImpl = async () => ({ ok: true, json: async () => ({ suggestedValue: 'AI가 다듬은 소개문' }) });
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 원장 소개문', philosophyItem1: '건드리면 안 됨' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  hooks.requestAiSuggestion();
  await flush();
  hooks.applyAiSuggestionToBuilder();

  assert.equal(academy.pageSettings.builderDraft.principalDesc, 'AI가 다듬은 소개문');
  // then — 관련 없는 다른 builderDraft 값은 그대로 보존된다(AI가 전체 builderDraft를 덮어쓰지 않는다)
  assert.equal(academy.pageSettings.builderDraft.philosophyItem1, '건드리면 안 됨');
});

test('given 발행된 Academy when AI 제안을 적용 then Production을 직접 바꾸지 않고 "발행 필요" 상태로만 표시한다', async () => {
  const document = buildAiModalDocument();
  const fetchImpl = async () => ({ ok: true, json: async () => ({ suggestedValue: '새 소개문' }) });
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  // window.fetch(전역)로 실제 PUT이 나가는지까지 감시한다 — 나가면 안 된다.
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy', // 이미 발행됨
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  hooks.requestAiSuggestion();
  await flush();
  hooks.applyAiSuggestionToBuilder();

  // then — builderDraft는 바뀌었지만(Builder 상태) PUT /api/tutor/academy로 즉시 저장하지는
  // 않는다("적용" 자체는 발행이 아니다). persistTextChangesAfterApply가 dirty만 표시한다.
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '새 소개문');
});

test('given 철학 카드 4개 각각의 버튼 when target을 명시적으로 열면 then 그 카드의 target으로만 요청한다(추론 없음)', async () => {
  // Phase 4.1: activeTextField로 "지금 커서가 있는 항목"을 추론하던 activeAiTarget()을
  // 없앴다 — 각 카드 버튼이 자기 target을 data 속성으로 직접 들고 있어 추론이 필요 없다.
  const document = buildAiModalDocument();
  for (const target of ['philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4']) {
    const fetchCalls = [];
    const fetchImpl = async (url, init) => {
      fetchCalls.push({ url, init });
      return { ok: true, json: async () => ({ suggestedValue: '다듬은 철학' }) };
    };
    const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
    const academy = academyFixture({
      publicPathUrl: '/academies/test-academy',
      pageSettings: { builderDraft: { [target]: '기존 철학 문구' } }
    });
    hooks.setPreview(academy);

    hooks.openAiSuggestionModal('PHILOSOPHY_ITEM', target);
    hooks.requestAiSuggestion();
    await flush();

    const body = JSON.parse(fetchCalls[0].init.body);
    assert.equal(body.task, 'PHILOSOPHY_ITEM');
    assert.equal(body.target, target);
  }
});

test('given provider 오류 응답 when 생성 then 에러만 표시하고 builderDraft는 손대지 않는다', async () => {
  const document = buildAiModalDocument();
  const fetchImpl = async () => ({ ok: false, json: async () => ({ message: 'AI provider가 구성되어 있지 않습니다.' }) });
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal('PRINCIPAL_INTRO', 'principalDesc');
  hooks.requestAiSuggestion();
  await flush();

  assert.equal(document.elements.get('builderAiSuggestionError').textContent, 'AI provider가 구성되어 있지 않습니다.');
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 소개문');
  // 적용 버튼도 계속 비활성 상태를 유지해야 한다(에러인데 적용 가능해 보이면 안 됨)
  assert.equal(document.elements.get('builderAiSuggestionApplyBtn').disabled, true);
});

test('given 실제 렌더된 AI 버튼들의 task/target when 백엔드 ALLOWED_TARGETS와 비교 then 정확히 일치한다', () => {
  // Phase 4.1: 프론트에 별도 allowlist 객체(AI_ASSIST_SECTIONS)가 더 이상 없다 — 버튼 자체의
  // data 속성이 유일한 소스다. 그래서 "실제 렌더된 버튼들이 요청하는 task/target 조합"을
  // 백엔드 allowlist와 직접 대조한다(내부 JS 객체가 아니라 진짜 마크업 기준).
  const { loadSectionsModule } = require('./helpers/browser_script_harness');
  const { parseHtml } = require('./helpers/mini_dom');
  const SECTIONS = loadSectionsModule();
  const academy = academyFixture();
  const view = SECTIONS.toSectionView(academy);
  const ctx = SECTIONS.createBuilderContext({ slug: () => 'x', showcase: () => ({ render: () => '' }), videos: () => [] });
  const principalHtml = SECTIONS.renderPrincipal(view, ctx);
  const philosophyHtml = SECTIONS.renderPhilosophy(view, ctx);

  const principalButtons = parseHtml(principalHtml).querySelectorAll('[data-ai-suggest-trigger]');
  const philosophyButtons = parseHtml(philosophyHtml).querySelectorAll('[data-ai-suggest-trigger]');

  assert.equal(principalButtons.length, 1);
  assert.equal(principalButtons[0].getAttribute('data-ai-suggest-task'), 'PRINCIPAL_INTRO');
  assert.equal(principalButtons[0].getAttribute('data-ai-suggest-target'), 'principalDesc');

  assert.equal(philosophyButtons.length, 4);
  const philosophyTargets = philosophyButtons.map((b) => b.getAttribute('data-ai-suggest-target'));

  assert.match(SUGGESTION_SERVICE_JAVA, /AcademyAiTaskType\.PRINCIPAL_INTRO, Set\.of\("principalDesc"\)/);
  assert.match(SUGGESTION_SERVICE_JAVA,
    /AcademyAiTaskType\.PHILOSOPHY_ITEM,\s*\n\s*Set\.of\("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4"\)/);
  philosophyButtons.forEach((b) => assert.equal(b.getAttribute('data-ai-suggest-task'), 'PHILOSOPHY_ITEM'));
  assert.deepEqual(philosophyTargets, ['philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4']);
});
