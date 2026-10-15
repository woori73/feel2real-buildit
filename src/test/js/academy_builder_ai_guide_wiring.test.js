const assert = require('node:assert/strict');
const test = require('node:test');
const { loadRenderHooks, academyFixture } = require('./helpers/academy_render_hooks');
const { memoryStorage } = require('./helpers/browser_script_harness');
const AI_GUIDE = require('../../main/resources/static/js/academy_builder_ai_guide.js');

/**
 * Phase 5B — AI Guide 배선(실제 academy_builder.js 코드 실행).
 *
 * 절대 원칙:
 *   1) requestAiGuideRecommendations(=[문구 추천받기])은 읽기 전용 — currentPreview가 안 바뀐다.
 *   2) applyAiGuideCandidate 를 호출해야만 builderDraft(또는 intro)만 바뀐다.
 *   3) Publish/PUT는 어떤 경로로도 자동 실행되지 않는다.
 *   4) 기존 [AI로 다듬기]/[AI로 Academy 구성하기] 훅은 그대로 노출된다(대체하지 않는다).
 */

const flush = () => new Promise((resolve) => setImmediate(resolve));

const GUIDE_IDS = [
  'builderAiGuidePanel', 'builderAiGuideToggle', 'builderAiGuideClose', 'builderAiGuideRestart',
  'builderAiGuidePrev', 'builderAiGuideNext', 'builderAiGuideSkip', 'builderAiGuideSteps',
  'builderAiGuideBody', 'builderAiGuideProgressFill', 'builderComposeTrigger', 'pageContent'
];

function fakeElement() {
  const el = {
    textContent: '', hidden: false, disabled: false, value: '', innerHTML: '',
    style: {}, dataset: {},
    classList: {
      _s: new Set(),
      add(n) { this._s.add(n); }, remove(n) { this._s.delete(n); },
      toggle(n, f) { const v = f === undefined ? !this._s.has(n) : f; if (v) this._s.add(n); else this._s.delete(n); return v; },
      contains(n) { return this._s.has(n); }
    },
    setAttribute(k, v) { this.dataset['_attr_' + k] = v; },
    getAttribute(k) { return this.dataset['_attr_' + k] == null ? null : this.dataset['_attr_' + k]; },
    addEventListener() {},
    appendChild() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    contains() { return false; },
    focus() {},
    scrollTo() {}
  };
  return el;
}

function buildGuideDocument() {
  const elements = new Map(GUIDE_IDS.map((id) => [id, fakeElement()]));
  const body = fakeElement();
  return {
    body, head: fakeElement(), title: '',
    addEventListener() {},
    getElementById(id) { return elements.has(id) ? elements.get(id) : null; },
    querySelector(sel) {
      if (typeof sel === 'string' && sel[0] === '#') return this.getElementById(sel.slice(1));
      return null;
    },
    querySelectorAll() { return []; },
    createElement() { return fakeElement(); },
    createRange() { return { selectNodeContents() {}, collapse() {}, setStart() {} }; },
    elements
  };
}

function loadGuide(options = {}) {
  const document = buildGuideDocument();
  const fetchCalls = [];
  const sessionStorage = options.savedSession
    ? memoryStorage({ [AI_GUIDE.SESSION_STORAGE_KEY]: options.savedSession })
    : undefined;
  const hooks = loadRenderHooks('builder', {
    document,
    sessionStorage,
    window: { getComputedStyle: () => ({}), getSelection: () => ({ removeAllRanges() {}, addRange() {} }) },
    fetch: async (url, init) => {
      fetchCalls.push({ url, init });
      if (options.fetch) return options.fetch(url, init);
      return new Promise(() => {});
    }
  });
  return { hooks, document, fetchCalls };
}

test('신규 Academy 첫 진입 = 이름/주소 단계, 주소 preview = xxx.feel2real.co.kr', () => {
  const { hooks, document } = loadGuide();
  hooks.setPreview(academyFixture({ publicPathUrl: '/academies/x' })); // handle 없음 = 신규/미설정
  hooks.wireAiGuide(academyFixture({ publicPathUrl: '/academies/x' }));

  const body = document.getElementById('builderAiGuideBody');
  assert.equal(hooks.getAiGuideSession().currentStepId, 'naming');
  assert.match(body.innerHTML, /학원명|홈페이지 주소/);
  assert.match(body.innerHTML, /\.feel2real\.co\.kr/);
  assert.match(body.innerHTML, /이 이름과 주소로 시작하기/);
});

test('이미 확정된 이름/주소가 있는 Academy는 naming 을 건너뛰고 스타일부터 시작한다', () => {
  const { hooks, document } = loadGuide();
  const academy = academyFixture({ publicPathUrl: '/academies/x', handle: 'apgujeong-eduholic' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  assert.equal(hooks.getAiGuideSession().currentStepId, 'style');
  assert.ok(hooks.getAiGuideSession().completedStepIds.indexOf('naming') >= 0);
  assert.match(document.getElementById('builderAiGuideBody').innerHTML, /홈페이지 스타일|어떤 느낌/);
});

test('Scenario B — #195 이전 sessionStorage(currentStepId=style) + 이름/주소 미설정 → naming STEP 1 부터 시작', () => {
  const { hooks, document } = loadGuide({
    savedSession: { currentStepId: 'style', onboarded: true, completedStepIds: ['style'] }
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x' }); // handle 없음 = 미설정
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  assert.equal(hooks.getAiGuideSession().currentStepId, 'naming', 'CSS가 아니라 진행 상태를 교정해야 한다');
  assert.match(document.getElementById('builderAiGuideBody').innerHTML, /학원명|홈페이지 주소/);
});

test('Scenario C — 기존 Academy(이름+실제 handle) 는 저장된 진행 상태를 존중한다(naming 강제 안 함)', () => {
  const { hooks } = loadGuide({
    savedSession: { currentStepId: 'philosophy', onboarded: true, completedStepIds: ['naming', 'style', 'hero', 'principal'] }
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', handle: 'apgujeong-eduholic' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  assert.equal(hooks.getAiGuideSession().currentStepId, 'philosophy', '진행 중이던 사용자를 되돌리면 안 된다');
});

test('Scenario D — 긴 추천안 3개는 캐러셀로 한 번에 1개만, [적용]·이전/다음 추천이 항상 노출된다', async () => {
  const long = (n) => `추천안${n} 원장은 20년간 지역 학생을 지도하며 실력을 끌어올린 교육자로 이 배경을 한 문단으로 풀어 학부모에게 신뢰를 줍니다`;
  const { hooks, document } = loadGuide({
    fetch: async () => ({ ok: true, json: async () => ({ target: 'principalDesc', candidates: [long(1), long(2), long(3)] }) })
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', handle: 'apgujeong-eduholic', pageSettings: { builderDraft: { principalDesc: '' } } });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');
  hooks.requestAiGuideRecommendations();
  await flush();

  const body = () => document.getElementById('builderAiGuideBody').innerHTML;
  assert.match(body(), /추천안 1 \/ 3/);
  assert.match(body(), /data-guide-action="apply"/, '[적용]은 항상 노출');
  assert.match(body(), /data-guide-action="cand-next"/);
  // 첫 화면에는 후보 1만 — 3개를 세로로 다 쌓지 않는다.
  assert.equal((body().match(/data-guide-action="apply"/g) || []).length, 1);
  assert.ok(body().indexOf(long(1)) >= 0 && body().indexOf(long(2)) < 0, '후보 2는 아직 렌더 안 함');

  // 다음 추천 → 후보 2 표시, [적용]은 여전히 1개, index 갱신
  hooks.stepAiGuideCandidate(1);
  assert.equal(hooks.getAiGuideCandidateIndex(), 1);
  assert.match(body(), /추천안 2 \/ 3/);
  assert.ok(body().indexOf(long(2)) >= 0 && body().indexOf(long(1)) < 0);
  assert.equal((body().match(/data-guide-action="apply"/g) || []).length, 1);

  // 경계에서 clamp
  hooks.stepAiGuideCandidate(1);
  hooks.stepAiGuideCandidate(1);
  assert.equal(hooks.getAiGuideCandidateIndex(), 2);

  // 적용은 현재 보이는 후보(index 2)를 반영한다
  hooks.applyAiGuideCandidate(2);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, long(3));
  assert.equal(hooks.getAiGuidePending(), null);
  assert.equal(hooks.getAiGuideCandidateIndex(), 0, '적용 후 index 초기화');
});

test('[문구 추천받기]는 읽기 전용 — POST만 나가고 currentPreview는 안 바뀐다', async () => {
  const { hooks, document, fetchCalls } = loadGuide({
    fetch: async () => ({ ok: true, json: async () => ({ target: 'intro', candidates: ['후보 1', '후보 2', '후보 3'] }) })
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', intro: '원래 히어로 소개' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('hero'); // intro target

  hooks.requestAiGuideRecommendations();
  await flush();

  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].url, '/api/tutor/academy/ai/guide-suggestions');
  assert.equal(JSON.parse(fetchCalls[0].init.body).target, 'intro');
  assert.equal(academy.intro, '원래 히어로 소개', '추천만 받았는데 intro가 바뀌었다');
  assert.deepEqual(hooks.getAiGuidePending().candidates, ['후보 1', '후보 2', '후보 3']);
});

test('후보 [적용] → builderDraft/intro만 바뀌고 PUT(발행)은 안 나간다', async () => {
  const putCalls = [];
  const { hooks } = loadGuide({
    fetch: async (url, init) => {
      if (init && init.method === 'PUT') { putCalls.push(url); return { ok: true, json: async () => ({}) }; }
      return { ok: true, json: async () => ({ target: 'principalDesc', candidates: ['20년간 지역 학생을 지도한 원장이 직접 가르칩니다.'] }) };
    }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/x',
    pageSettings: { builderDraft: { principalDesc: '', philosophyItem1: '건드리면 안 됨' } }
  });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');

  hooks.requestAiGuideRecommendations();
  await flush();
  hooks.applyAiGuideCandidate(0);

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생을 지도한 원장이 직접 가르칩니다.');
  assert.equal(academy.pageSettings.builderDraft.philosophyItem1, '건드리면 안 됨');
  assert.equal(putCalls.length, 0, 'Guide 적용이 발행 PUT을 자동 실행하면 안 된다');
  assert.ok(hooks.getAiGuideSession().completedStepIds.indexOf('principal') >= 0);
});

test('발행된 Academy에서 후보 적용 시 Production PUT 없이 builderDraft만 갱신(dirty 표시)', async () => {
  const putCalls = [];
  const { hooks } = loadGuide({
    fetch: async (url, init) => {
      if (init && init.method === 'PUT') { putCalls.push(url); return { ok: true, json: async () => ({}) }; }
      return { ok: true, json: async () => ({ target: 'philosophyTitle', candidates: ['문장을 직접 조립하며 배우는 영어'] }) };
    }
  });
  const academy = academyFixture({ publicPathUrl: '/academies/live', pageSettings: { builderDraft: {} } });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('philosophy');
  hooks.requestAiGuideRecommendations();
  await flush();
  hooks.applyAiGuideCandidate(0);

  assert.equal(academy.pageSettings.builderDraft.philosophyTitle, '문장을 직접 조립하며 배우는 영어');
  assert.equal(putCalls.length, 0);
});

test('malformed AI 응답(candidates 누락) → 적용 대상이 생기지 않고 currentPreview 불변', async () => {
  const { hooks } = loadGuide({
    fetch: async () => ({ ok: true, json: async () => ({ target: 'principalDesc' }) })
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', pageSettings: { builderDraft: { principalDesc: '기존' } } });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');
  hooks.requestAiGuideRecommendations();
  await flush();

  assert.equal(hooks.getAiGuidePending(), null);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존');
});

test('AI Router 장애(HTTP 503) → 에러 표시하고 Academy 보존', async () => {
  const { hooks, document } = loadGuide({
    fetch: async () => ({ ok: false, json: async () => ({ message: 'AI 서비스 연결에 실패했습니다.' }) })
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', pageSettings: { builderDraft: { principalDesc: '기존' } } });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');
  hooks.requestAiGuideRecommendations();
  await flush();

  assert.match(document.getElementById('builderAiGuideBody').innerHTML, /AI 서비스 연결에 실패|다시 시도/);
  assert.equal(academy.pageSettings.builderDraft.principalDesc, '기존');
  assert.equal(hooks.getAiGuidePending(), null);
});

test('스텝 이동: 다음/이전/건너뛰기가 세션 진행 상태에 반영된다', () => {
  const { hooks } = loadGuide();
  const academy = academyFixture({ publicPathUrl: '/academies/x', handle: 'apgujeong-eduholic' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy); // 이름/주소 확정 → style 부터 시작

  hooks.moveAiGuideStep('next'); // style -> hero, style completed
  assert.equal(hooks.getAiGuideSession().currentStepId, 'hero');
  assert.ok(hooks.getAiGuideSession().completedStepIds.indexOf('style') >= 0);

  hooks.skipAiGuideStep(); // hero skipped -> principal
  assert.ok(hooks.getAiGuideSession().skippedStepIds.indexOf('hero') >= 0);
  assert.equal(hooks.getAiGuideSession().currentStepId, 'principal');

  hooks.moveAiGuideStep('prev'); // principal -> hero
  assert.equal(hooks.getAiGuideSession().currentStepId, 'hero');
});

test('restartAiGuide는 세션을 초기화한다', () => {
  const { hooks } = loadGuide();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('philosophy');
  hooks.restartAiGuide();
  assert.equal(hooks.getAiGuideSession().currentStepId, 'naming');
  assert.deepEqual(hooks.getAiGuideSession().completedStepIds, []);
});

test('허용되지 않은 target으로는 후보 적용이 거부된다(프론트 이중 방어)', async () => {
  const { hooks } = loadGuide({
    fetch: async () => ({ ok: true, json: async () => ({ target: 'principalDesc', candidates: ['정상 후보'] }) })
  });
  const academy = academyFixture({ publicPathUrl: '/academies/x', pageSettings: { builderDraft: { monthlyTuition: '30만원' } } });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');
  hooks.requestAiGuideRecommendations();
  await flush();
  // pending을 손상시켜 위험 target을 강제로 넣어도 적용이 안 되는지
  const pending = hooks.getAiGuidePending();
  pending.target = 'monthlyTuition';
  hooks.applyAiGuideCandidate(0);
  assert.equal(academy.pageSettings.builderDraft.monthlyTuition, '30만원');
});

test('기존 AI 훅(다듬기 / Academy 구성하기)은 Guide 추가 후에도 그대로 노출된다', () => {
  const { hooks } = loadGuide();
  ['openAiSuggestionModal', 'applyAiSuggestionToBuilder', 'wireAcademyComposeModal',
   'openAcademyComposeModal', 'applyComposeProposalById'].forEach((name) => {
    assert.equal(typeof hooks[name], 'function', `${name} 훅이 사라졌다(기존 AI 기능 회귀)`);
  });
});
