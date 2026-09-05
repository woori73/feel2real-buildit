const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadRenderHooks, academyFixture } = require('./helpers/academy_render_hooks');
const { parseHtml, MiniElement, dispatchClick } = require('./helpers/mini_dom');

const CONTEXT_SERVICE_JAVA = fs.readFileSync(
  path.resolve(__dirname, '../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyAiContextService.java'),
  'utf8');

/**
 * Phase 5A — AI Academy Builder(전체 진단 + proposal 적용).
 *
 * 절대 원칙(실제 academy_builder.js 코드를 실행해 검증한다):
 *   1) requestAcademyProposal(=[Academy 분석하기])은 읽기 전용 — currentPreview.pageSettings /
 *      builderDraft / sectionOrder 가 절대 바뀌지 않는다.
 *   2) applyComposeProposalById / applyAllComposeProposals 를 호출해야만 builderDraft(문구) /
 *      sectionOrder(섹션 순서)만 currentPreview에 반영된다.
 *   3) Publish는 어떤 경로로도 자동 실행되지 않는다(발행된 Academy는 PUT이 나가지 않고 dirty
 *      표시만).
 *   4) 허용되지 않은 target/섹션 순서/type 은 프론트에서도 거부한다.
 */
function fakeElement() {
  return {
    textContent: '',
    hidden: false,
    disabled: false,
    value: '',
    innerHTML: '',
    dataset: {},
    classList: { add() {}, remove() {}, contains() { return false; } },
    setAttribute() {},
    addEventListener() {},
    querySelectorAll() { return []; }
  };
}

const COMPOSE_ELEMENT_IDS = [
  'builderAcademyComposeModal', 'builderComposeInstructions', 'builderComposeLoading',
  'builderComposeError', 'builderComposeResults', 'builderComposeDiagnosis',
  'builderComposeToolbar', 'builderComposeApplyAllBtn', 'builderComposeRevertBtn',
  'builderComposeAppliedTag', 'builderComposeCards', 'builderComposeAnalyzeBtn',
  'builderComposeTrigger'
];

function buildComposeDocument() {
  const elements = new Map(COMPOSE_ELEMENT_IDS.map((id) => [id, fakeElement()]));
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

const DEFAULT_ORDER = [
  'sec-hero', 'sec-featured', 'sec-intro', 'sec-principal', 'sec-philosophy',
  'sec-videos', 'sec-minilectures', 'sec-reviews', 'sec-lessons', 'sec-curriculum',
  'sec-effect', 'sec-digitaltwin', 'sec-market', 'sec-payment', 'sec-social'
];

function textProposal(overrides = {}) {
  return Object.assign({
    id: 'p1', type: 'TEXT', title: '원장 소개', current: '비어 있음',
    suggestion: '신뢰감 있게 작성', target: 'principalDesc',
    textValue: '20년간 지역 학생들과 함께한 원장이 직접 지도합니다.', safeToApply: true
  }, overrides);
}

function orderProposal(overrides = {}) {
  const order = DEFAULT_ORDER.slice();
  order.splice(order.indexOf('sec-videos'), 1);
  order.splice(1, 0, 'sec-videos');
  return Object.assign({
    id: 'p2', type: 'SECTION_ORDER', title: '섹션 순서', current: '수업 영상이 뒤쪽',
    suggestion: '수업 영상을 앞으로', target: null, sectionOrder: order, safeToApply: true
  }, overrides);
}

function okJson(body) {
  return async () => ({ ok: true, json: async () => body });
}

test('given 모달을 염 when 아직 [Academy 분석하기]를 누르지 않음 then 요청이 나가지 않는다', async () => {
  const document = buildComposeDocument();
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document, fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); }
  });
  hooks.setPreview(academyFixture({ publicPathUrl: '/academies/test-academy' }));

  hooks.openAcademyComposeModal();
  await flush();

  assert.equal(fetchCalls.length, 0, '모달을 열기만 했는데 분석 요청이 나갔다');
});

test('given [Academy 분석하기] when 사용자 요구사항 입력 then POST 요청 body에 instructions가 그대로 실린다', async () => {
  const document = buildComposeDocument();
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document, fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  document.elements.get('builderComposeInstructions').value = '  수업 영상을 중심으로 보여줘  ';
  hooks.requestAcademyProposal();
  await flush();

  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].url, '/api/tutor/academy/ai/academy-proposal');
  assert.deepEqual(JSON.parse(fetchCalls[0].init.body), { instructions: '수업 영상을 중심으로 보여줘' });
  // 분석만으로는 아무것도 안 바뀐다.
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 소개문');
});

test('given 분석 결과 도착 when 적용을 누르지 않음 then builderDraft/sectionOrder는 그대로다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document, fetch: okJson({ diagnosis: '수업 콘텐츠가 뒤쪽에 있습니다.', proposals: [textProposal(), orderProposal()] })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();

  assert.equal(hooks.getAcademyComposeState().proposals.length, 2);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 소개문');
  assert.equal(document.elements.get('builderComposeDiagnosis').textContent, '수업 콘텐츠가 뒤쪽에 있습니다.');
});

test('given TEXT proposal when 개별 적용 then 해당 builderDraft 키만 바뀌고 다른 값은 보존된다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document, fetch: okJson({ diagnosis: '진단', proposals: [textProposal()] })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 소개문', philosophyItem1: '건드리면 안 됨' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('p1');

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생들과 함께한 원장이 직접 지도합니다.');
  assert.equal(academy.pageSettings.builderDraft.philosophyItem1, '건드리면 안 됨');
});

test('given SECTION_ORDER proposal when 적용 then sectionOrder만 바뀌고 PUT은 나가지 않는다', async () => {
  const document = buildComposeDocument();
  const putCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: async (url, init) => {
      if (init && init.method === 'PUT') putCalls.push({ url, init });
      return { ok: true, json: async () => ({ diagnosis: '진단', proposals: [orderProposal()] }) };
    }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: {}, sectionOrder: DEFAULT_ORDER.slice() }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('p2');

  assert.equal(academy.pageSettings.sectionOrder[1], 'sec-videos', 'sectionOrder가 반영되지 않음');
  assert.equal(academy.sectionOrder[1], 'sec-videos', '최상위 sectionOrder도 동기화돼야 한다');
  assert.equal(putCalls.length, 0, '적용만으로 PUT(발행/저장)이 나가면 안 된다');
});

test('given 여러 proposal when [추천안 전체 적용] then safeToApply=true인 것만 한 번에 반영된다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: okJson({
      diagnosis: '진단',
      proposals: [
        textProposal({ id: 'p1' }),
        textProposal({ id: 'p2', target: 'philosophyItem1', textValue: '기초부터 탄탄하게', safeToApply: false }),
        orderProposal({ id: 'p3' })
      ]
    })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { philosophyItem1: '원래 철학' }, sectionOrder: DEFAULT_ORDER.slice() }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyAllComposeProposals();

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생들과 함께한 원장이 직접 지도합니다.');
  assert.equal(academy.pageSettings.builderDraft.philosophyItem1, '원래 철학', 'safeToApply=false는 전체 적용에서 제외돼야 한다');
  assert.equal(academy.pageSettings.sectionOrder[1], 'sec-videos');
});

test('given 적용 후 되돌리기 when revert then 스냅샷 상태로 복구되고 그 사이의 직접 편집은 보존된다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document, fetch: okJson({ diagnosis: '진단', proposals: [textProposal()] })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존 소개문' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('p1');
  // 사용자가 그 사이 직접 다른 필드를 편집.
  academy.pageSettings.builderDraft.philosophyItem2 = '사용자가 직접 쓴 값';
  hooks.revertComposeChanges();

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존 소개문', 'AI가 바꾼 값은 스냅샷으로 복구돼야 한다');
  assert.equal(academy.pageSettings.builderDraft.philosophyItem2, '사용자가 직접 쓴 값', '되돌리기가 무관한 직접 편집까지 지우면 안 된다');
  assert.equal(hooks.getAcademyComposeState().applied.size, 0);
});

test('given 발행된 Academy when proposal 적용 then Production을 직접 바꾸지 않고 builderDraft만 갱신한다', async () => {
  const document = buildComposeDocument();
  const putCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: async (url, init) => {
      if (init && init.method === 'PUT') { putCalls.push(url); return { ok: true, json: async () => academy }; }
      return { ok: true, json: async () => ({ diagnosis: '진단', proposals: [textProposal()] }) };
    }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('p1');

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생들과 함께한 원장이 직접 지도합니다.');
  assert.equal(putCalls.length, 0, 'AI 적용이 발행/저장 PUT을 자동 실행하면 안 된다');
});

test('given 허용되지 않은 target proposal when 적용 시도 then 무시하고 builderDraft를 건드리지 않는다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: okJson({
      diagnosis: '진단',
      proposals: [textProposal({ id: 'evil', target: 'monthlyTuition', textValue: '10만원' })]
    })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존', monthlyTuition: '30만원' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('evil');

  assert.equal(academy.pageSettings.builderDraft.monthlyTuition, '30만원', '허용되지 않은 target이 적용되면 안 된다');
});

test('given 잘못된 SECTION_ORDER(개수 부족/sec-hero 0번 아님) when 적용 시도 then 무시한다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: okJson({
      diagnosis: '진단',
      proposals: [orderProposal({ id: 'bad', sectionOrder: ['sec-intro', 'sec-hero'] })]
    })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: {}, sectionOrder: DEFAULT_ORDER.slice() }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();
  hooks.applyComposeProposalById('bad');

  assert.deepEqual(academy.pageSettings.sectionOrder, DEFAULT_ORDER, '잘못된 섹션 순서가 적용되면 안 된다');
});

test('given malformed AI 응답(proposals 누락) when 분석 then 에러 없이 진단만 표시하고 builderDraft 불변', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document, fetch: okJson({ diagnosis: '진단만 있음' })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();

  assert.equal(hooks.getAcademyComposeState().proposals.length, 0);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존');
});

test('given AI Router 장애(HTTP 503) when 분석 then 에러만 표시하고 기존 Academy를 보존한다', async () => {
  const document = buildComposeDocument();
  const hooks = loadRenderHooks('builder', {
    document,
    fetch: async () => ({ ok: false, json: async () => ({ message: 'AI 서비스 연결에 실패했습니다.' }) })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존' } }
  });
  hooks.setPreview(academy);

  hooks.openAcademyComposeModal();
  hooks.requestAcademyProposal();
  await flush();

  assert.equal(document.elements.get('builderComposeError').textContent, 'AI 서비스 연결에 실패했습니다.');
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존');
});

test('given 프론트 COMPOSE_TEXT_TARGETS when 백엔드 EDITABLE_TEXT_TARGETS와 대조 then 정확히 일치한다', () => {
  // 프론트/백엔드 allowlist가 갈라지면 "서버는 거부하는데 프론트는 버튼을 보여주는" 상태가 된다.
  const builderJs = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'), 'utf8');
  const frontMatch = builderJs.match(/const COMPOSE_TEXT_TARGETS = \[([\s\S]*?)\];/);
  assert.ok(frontMatch, '프론트 COMPOSE_TEXT_TARGETS를 찾지 못함');
  const frontTargets = frontMatch[1].match(/'([a-zA-Z0-9]+)'/g).map((s) => s.replace(/'/g, '')).sort();

  const backMatch = CONTEXT_SERVICE_JAVA.match(/EDITABLE_TEXT_TARGETS = List\.of\(([\s\S]*?)\);/);
  assert.ok(backMatch, '백엔드 EDITABLE_TEXT_TARGETS를 찾지 못함');
  const backTargets = backMatch[1].match(/"([a-zA-Z0-9]+)"/g).map((s) => s.replace(/"/g, '')).sort();

  assert.deepEqual(frontTargets, backTargets);
});

// ── 실제 DOM 클릭 기반 검증 (Phase 4의 disabled 버튼 테스트 누락 재발 방지) ──

function buildMiniComposeDom() {
  const html = new MiniElement('html');
  const body = new MiniElement('body');
  html.appendChild(body);
  const pageContent = new MiniElement('div');
  pageContent.setAttribute('id', 'pageContent');
  body.appendChild(pageContent);

  // 완성도 패널 안의 트리거 버튼 + 모달 전체를 실제 template과 같은 태그로 구성한다.
  const trigger = new MiniElement('button');
  trigger.setAttribute('id', 'builderComposeTrigger');
  body.appendChild(trigger);

  const modal = new MiniElement('div');
  modal.setAttribute('id', 'builderAcademyComposeModal');
  body.appendChild(modal);

  const close = new MiniElement('button');
  close.setAttribute('data-compose-close', '');
  modal.appendChild(close);

  [
    ['builderComposeInstructions', 'textarea'],
    ['builderComposeLoading', 'div'],
    ['builderComposeError', 'div'],
    ['builderComposeResults', 'div'],
    ['builderComposeDiagnosis', 'p'],
    ['builderComposeToolbar', 'div'],
    ['builderComposeApplyAllBtn', 'button'],
    ['builderComposeRevertBtn', 'button'],
    ['builderComposeAppliedTag', 'span'],
    ['builderComposeCards', 'div'],
    ['builderComposeAnalyzeBtn', 'button']
  ].forEach(([id, tag]) => {
    const el = new MiniElement(tag);
    el.setAttribute('id', id);
    body.appendChild(el);
  });

  const document = {
    head: new MiniElement('head'),
    body,
    title: '',
    createElement(tag) { return new MiniElement(tag); },
    getElementById(id) { return html.querySelectorAll('#' + id)[0] || null; },
    querySelector(sel) { return html.querySelector(sel); },
    querySelectorAll(sel) { return html.querySelectorAll(sel); },
    addEventListener() {},
    createRange() { return { selectNodeContents() {} }; }
  };
  return { document, trigger, modal };
}

test('given 실제 트리거 버튼 when 클릭 then 모달이 열리고 분석 요청은 아직 나가지 않는다(우회 호출 아님)', async () => {
  const { document, trigger, modal } = buildMiniComposeDom();
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document,
    window: { getComputedStyle: () => ({}), getSelection: () => ({ removeAllRanges() {}, addRange() {} }) },
    fetch: async (url, init) => { fetchCalls.push({ url, init }); return new Promise(() => {}); }
  });
  hooks.setPreview(academyFixture({ publicPathUrl: '/academies/test-academy' }));
  hooks.wireAcademyComposeModal();

  dispatchClick(trigger, document.body);
  assert.equal(modal.classList.contains('is-open'), true, '실제 트리거 클릭으로 모달이 열리지 않음');
  assert.equal(fetchCalls.length, 0, '모달만 열렸는데 분석 요청이 나갔다');

  dispatchClick(document.getElementById('builderComposeAnalyzeBtn'), document.body);
  await flush();
  assert.equal(fetchCalls.length, 1, '[Academy 분석하기] 클릭 시 요청이 나가지 않음');
  assert.equal(fetchCalls[0].url, '/api/tutor/academy/ai/academy-proposal');
});

test('given 렌더된 proposal 카드의 실제 [적용] 버튼 when 클릭 then builderDraft에 반영된다', async () => {
  const { document } = buildMiniComposeDom();
  const hooks = loadRenderHooks('builder', {
    document,
    window: { getComputedStyle: () => ({}), getSelection: () => ({ removeAllRanges() {}, addRange() {} }) },
    fetch: okJson({ diagnosis: '진단', proposals: [textProposal()] })
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/test-academy',
    pageSettings: { builderDraft: { principalDesc: '기존' } }
  });
  hooks.setPreview(academy);
  hooks.wireAcademyComposeModal();

  dispatchClick(document.getElementById('builderComposeTrigger'), document.body);
  dispatchClick(document.getElementById('builderComposeAnalyzeBtn'), document.body);
  await flush();

  const applyBtn = document.querySelectorAll('[data-compose-apply]')[0];
  assert.ok(applyBtn, 'proposal 카드의 적용 버튼이 렌더되지 않음');
  assert.equal(applyBtn.disabled, false, '적용 버튼이 disabled 상태로 렌더됨');
  dispatchClick(applyBtn, document.body);

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생들과 함께한 원장이 직접 지도합니다.');
});

// ── 긴 추천안 목록에서 모달이 viewport를 넘지 않고 본문이 스크롤되는지 (CSS 계약) ──

const BUILDER_TEMPLATE = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'), 'utf8');

test('given AI Academy Builder 모달 마크업 when 확인 then 전용 스크롤 클래스(builder-compose-modal)를 갖는다', () => {
  // #builderAcademyComposeModal의 내부 컨테이너 div가 builder-compose-modal 클래스를 갖는지.
  const modalBlock = BUILDER_TEMPLATE.slice(
    BUILDER_TEMPLATE.indexOf('id="builderAcademyComposeModal"'),
    BUILDER_TEMPLATE.indexOf('id="builderComposeTitle"'));
  assert.match(
    modalBlock,
    /class="builder-text-modal [^"]*\bbuilder-compose-modal\b[^"]*"/,
    '모달에 builder-compose-modal 클래스가 없어 긴 콘텐츠 스크롤 규칙이 적용되지 않는다');
});

test('given 모달 CSS when 확인 then max-height는 viewport 기준이고 본문만 overflow-y:auto로 스크롤된다', () => {
  // 배경(html,body{overflow:hidden})이 아니라 "모달 콘텐츠 영역"이 스크롤되어야 한다.
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal\{[^}]*max-height:min\(90vh,[^}]*\}/,
    'modal max-height가 viewport(vh) 기준으로 제한되지 않았다');
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal\{[^}]*display:flex;flex-direction:column;[^}]*\}/,
    'modal이 flex column이 아니면 head/actions 고정 + body 스크롤이 불가능하다');
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal \.builder-text-modal-body\{[^}]*min-height:0;overflow-y:auto;[^}]*\}/,
    'modal body에 min-height:0 + overflow-y:auto가 없어 긴 추천안을 끝까지 스크롤할 수 없다');
});

test('given 모달 CSS when 확인 then head/action 영역은 고정되고 툴바는 본문 상단에 sticky다', () => {
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal \.builder-text-modal-head\{[^}]*flex-shrink:0;[^}]*\}/,
    '제목/닫기 영역이 flex-shrink:0로 고정되지 않았다');
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal \.builder-text-modal-actions\{[^}]*flex-shrink:0;[^}]*\}/,
    '하단 action(닫기·분석하기) 영역이 고정되지 않았다');
  assert.match(
    BUILDER_TEMPLATE,
    /\.builder-compose-modal \.builder-compose-toolbar\{[^}]*position:sticky;top:0;[^}]*\}/,
    '추천안 전체 적용/되돌리기 툴바가 sticky가 아니라 스크롤 시 접근 불가해질 수 있다');
});

test('given 모바일 CSS when 확인 then 좁은 화면에서도 모달이 viewport 안에 유지된다', () => {
  assert.match(
    BUILDER_TEMPLATE,
    /@media \(max-width:768px\)\{\s*\.builder-compose-modal\{[^}]*max-height:calc\(100dvh - 24px\)[^}]*\}/,
    '모바일에서 모달 max-height가 viewport 기준으로 제한되지 않았다');
});

test('given 하단 필수 action when 확인 then 전체 적용·되돌리기·닫기 버튼이 모두 존재한다(회귀 방지)', () => {
  const modalBlock = BUILDER_TEMPLATE.slice(
    BUILDER_TEMPLATE.indexOf('id="builderAcademyComposeModal"'),
    BUILDER_TEMPLATE.indexOf('id="builderLogoModal"'));
  assert.match(modalBlock, /id="builderComposeApplyAllBtn"/, '추천안 전체 적용 버튼 누락');
  assert.match(modalBlock, /id="builderComposeRevertBtn"/, '되돌리기 버튼 누락');
  assert.match(modalBlock, /id="builderComposeAnalyzeBtn"/, '분석하기 버튼 누락');
  assert.match(modalBlock, /data-compose-close/, '닫기 버튼 누락');
});
