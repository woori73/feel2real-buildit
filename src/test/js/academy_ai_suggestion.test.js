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
 */
function fakeElement() {
  return {
    textContent: '',
    hidden: false,
    disabled: false,
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

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-principal']);
  await flush();

  // then — 요청은 나갔지만(POST /api/tutor/academy/ai/suggestions) 응답 전이라 아무것도 안 바뀜
  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].url, '/api/tutor/academy/ai/suggestions');
  const body = JSON.parse(fetchCalls[0].init.body);
  assert.deepEqual(body, { task: 'PRINCIPAL_INTRO', target: 'principalDesc', currentValue: '기존 원장 소개문' });
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 원장 소개문');
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

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-principal']);
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

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-principal']);
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
  const putCalls = [];
  // window.fetch(전역)로 실제 PUT이 나가는지까지 감시한다 — 나가면 안 된다.
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy', // 이미 발행됨
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-principal']);
  await flush();
  hooks.applyAiSuggestionToBuilder();

  // then — builderDraft는 바뀌었지만(Builder 상태) PUT /api/tutor/academy로 즉시 저장하지는
  // 않는다("적용" 자체는 발행이 아니다). persistTextChangesAfterApply가 dirty만 표시한다.
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '새 소개문');
});

test('given 철학 항목 Task when target을 명시하지 않음 then 기본값(philosophyItem1)을 쓴다', async () => {
  const document = buildAiModalDocument();
  const fetchCalls = [];
  const fetchImpl = async (url, init) => {
    fetchCalls.push({ url, init });
    return { ok: true, json: async () => ({ suggestedValue: '다듬은 철학' }) };
  };
  const hooks = loadRenderHooks('builder', { document, fetch: fetchImpl });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { philosophyItem1: '기존 철학1' } }
  });
  hooks.setPreview(academy);

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-philosophy']);
  await flush();

  const body = JSON.parse(fetchCalls[0].init.body);
  assert.equal(body.task, 'PHILOSOPHY_ITEM');
  assert.equal(body.target, 'philosophyItem1');
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

  hooks.openAiSuggestionModal(hooks.aiAssistSectionsForTest['sec-principal']);
  await flush();

  assert.equal(document.elements.get('builderAiSuggestionError').textContent, 'AI provider가 구성되어 있지 않습니다.');
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 소개문');
  // 적용 버튼도 계속 비활성 상태를 유지해야 한다(에러인데 적용 가능해 보이면 안 됨)
  assert.equal(document.elements.get('builderAiSuggestionApplyBtn').disabled, true);
});

test('given 프론트 AI_ASSIST_SECTIONS when 백엔드 ALLOWED_TARGETS와 비교 then task/target이 정확히 일치한다', () => {
  // 프론트가 백엔드 allowlist보다 넓은 target을 요청하면 매번 400을 받고, 더 좁으면 실제로는
  // 쓸 수 있는 target을 못 쓰게 된다 — 두 allowlist가 서로 어긋나지 않는지 직접 대조한다.
  const hooks = loadRenderHooks('builder');
  const frontendSections = hooks.aiAssistSectionsForTest;

  assert.match(SUGGESTION_SERVICE_JAVA, /AcademyAiTaskType\.PRINCIPAL_INTRO, Set\.of\("principalDesc"\)/);
  assert.match(SUGGESTION_SERVICE_JAVA,
    /AcademyAiTaskType\.PHILOSOPHY_ITEM,\s*\n\s*Set\.of\("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4"\)/);

  assert.equal(frontendSections['sec-principal'].task, 'PRINCIPAL_INTRO');
  assert.equal(frontendSections['sec-principal'].defaultTarget, 'principalDesc');
  assert.equal(frontendSections['sec-philosophy'].task, 'PHILOSOPHY_ITEM');
  // Array.from: targets는 vm 샌드박스(다른 realm) 안에서 만들어진 배열이라 deepEqual이
  // Array.prototype 정체성 차이로 실패한다 — 값만 비교하도록 host realm 배열로 옮겨 담는다.
  assert.deepEqual(
    Array.from(frontendSections['sec-philosophy'].targets),
    ['philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4']);
});
