const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { loadBrowserScript, memoryStorage } = require('./helpers/browser_script_harness');

const BUILDER = path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js');
// sec-digitaltwin은 Education Storefront 개편에서 future → opt-in(기본 OFF)로 승격됐다.
// 아래 목록은 여전히 렌더 대상이 아닌 진짜 future 섹션만 담는다.
const FUTURE_IDS = [
  'sec-reviews',
  'sec-curriculum',
  'sec-effect',
  'sec-market',
  'sec-payment'
];
const FULL_ORDER = [
  'sec-hero',
  'sec-featured',
  'sec-intro',
  'sec-principal',
  'sec-philosophy',
  'sec-videos',
  'sec-minilectures',
  'sec-reviews',
  'sec-lessons',
  'sec-curriculum',
  'sec-effect',
  'sec-digitaltwin',
  'sec-market',
  'sec-payment',
  'sec-social'
];

function loadHooks(options = {}) {
  const loaded = loadBrowserScript(BUILDER, options);
  const value = loaded.window.F2RAcademyBuilderTestHooks
    || loaded.window.F2RAcademySectionOrderTestHooks;
  assert.ok(value, 'production Builder test hooks must be exposed');
  return { ...loaded, hooks: value };
}

function completeAcademy(visibility) {
  return {
    name: '회귀 학원',
    handle: 'regression-academy',
    intro: '소개',
    subjects: [{ subjectId: 7, nameKo: '영어' }],
    targetGroups: ['MIDDLE'],
    regions: [{ city: '서울시', district: '강남구' }],
    pageSettings: {
      blogUrl: 'https://blog.naver.com/regression',
      sectionVisibility: visibility,
      sectionOrder: FULL_ORDER,
      builderDraft: { monthlyTuition: '300,000원' },
      businessInfo: { businessName: '회귀 학원' }
    }
  };
}

test('normalization forces every future section OFF without mutating compatibility values or its input', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const input = {
    'sec-intro': true,
    blogPostsSection: false,
    'sec-legacy-custom': true,
    'sec-reviews': true,
    'sec-payment': null
  };
  const snapshot = structuredClone(input);

  // when
  const normalized = builderHooks.normalizeSectionVisibility(input);

  // then
  assert.deepEqual(input, snapshot);
  assert.equal(normalized['sec-intro'], true);
  assert.equal(normalized.blogPostsSection, false);
  assert.equal(normalized['sec-legacy-custom'], true);
  FUTURE_IDS.forEach((id) => assert.equal(normalized[id], false, id));
});

for (const legacyValue of [true, false, null, undefined]) {
  test(`effective visibility excludes a future section when its stored value is ${String(legacyValue)}`, () => {
    // given
    const loaded = loadHooks();
    const builderHooks = loaded.hooks;
    const visibility = { 'sec-intro': true };
    if (legacyValue !== undefined) visibility['sec-reviews'] = legacyValue;
    const academy = completeAcademy(visibility);

    // when
    const visible = builderHooks.visibleSectionIds(academy);

    // then
    assert.ok(!visible.includes('sec-reviews'));
    assert.ok(visible.includes('sec-intro'));
  });
}

test('effective visibility excludes all future sections when the entire map is missing', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const academy = completeAcademy(undefined);
  delete academy.pageSettings.sectionVisibility;

  // when
  const visible = builderHooks.visibleSectionIds(academy);

  // then
  FUTURE_IDS.forEach((id) => assert.ok(!visible.includes(id), id));
  assert.ok(visible.includes('sec-intro'));
  assert.ok(visible.includes('sec-lessons'));
  assert.ok(visible.includes('sec-social'));
});

test('layout excludes future sections while orderedSectionIds retains the canonical full order', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const academy = completeAcademy({
    'sec-intro': true,
    'sec-principal': false,
    'sec-philosophy': true,
    'sec-lessons': true,
    'sec-social': true,
    'sec-reviews': true
  });

  // when
  const ordered = builderHooks.orderedSectionIds(academy);
  const layout = builderHooks.layoutSectionIds(academy);

  // then
  assert.deepEqual(ordered, FULL_ORDER);
  assert.deepEqual(layout, ['sec-hero', 'sec-intro', 'sec-philosophy', 'sec-videos', 'sec-lessons', 'sec-social', 'sec-footer']);
});

test('supported hidden section reappears in its saved slot without enabling future sections', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const academy = completeAcademy({ 'sec-principal': false, 'sec-reviews': true });
  const hidden = builderHooks.visibleSectionIds(academy);

  // when
  academy.pageSettings.sectionVisibility['sec-principal'] = true;
  const restored = builderHooks.visibleSectionIds(academy);

  // then
  assert.ok(!hidden.includes('sec-principal'));
  assert.equal(restored.indexOf('sec-principal'), 2);
  FUTURE_IDS.forEach((id) => assert.ok(!restored.includes(id), id));
});

test('full Academy update request preserves supported fields and normalizes the complete visibility map', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const academy = completeAcademy({
    'sec-intro': true,
    blogPostsSection: false,
    'sec-legacy-custom': true,
    'sec-payment': true
  });

  // when
  const request = structuredClone(builderHooks.academyUpdateRequest(academy));

  // then
  assert.equal(request.name, academy.name);
  assert.equal(request.handle, academy.handle);
  assert.equal(request.intro, academy.intro);
  assert.deepEqual(request.subjectIds, [7]);
  assert.deepEqual(request.targetGroups, ['MIDDLE']);
  assert.deepEqual(request.regions, [{ city: '서울시', district: '강남구' }]);
  assert.deepEqual(request.pageSettings.builderDraft, academy.pageSettings.builderDraft);
  assert.deepEqual(request.pageSettings.businessInfo, academy.pageSettings.businessInfo);
  assert.deepEqual(request.pageSettings.sectionOrder, FULL_ORDER);
  assert.equal(request.pageSettings.sectionVisibility.blogPostsSection, false);
  assert.equal(request.pageSettings.sectionVisibility['sec-legacy-custom'], true);
  FUTURE_IDS.forEach((id) => assert.equal(request.pageSettings.sectionVisibility[id], false, id));
});

test('partial page-settings request preserves the complete visibility map and only applies the requested field', () => {
  // given
  const loaded = loadHooks();
  const builderHooks = loaded.hooks;
  const current = {
    blogUrl: 'https://blog.naver.com/old',
    naverMapUrl: 'https://map.naver.com/old',
    sectionVisibility: {
      'sec-intro': true,
      blogPostsSection: false,
      'sec-legacy-custom': true,
      'sec-payment': true
    },
    builderDraft: { monthlyTuition: '300,000원' }
  };

  // when
  const body = builderHooks.pageSettingsWriteBody(current, { heroYoutubeUrl: 'https://youtu.be/abcdefghijk' });

  // then
  assert.equal(body.heroYoutubeUrl, 'https://youtu.be/abcdefghijk');
  assert.equal(body.sectionVisibility.blogPostsSection, false);
  assert.equal(body.sectionVisibility['sec-legacy-custom'], true);
  FUTURE_IDS.forEach((id) => assert.equal(body.sectionVisibility[id], false, id));
  assert.ok(!Object.hasOwn(body, 'builderDraft'), 'partial endpoint must not synthesize unsupported fields');
  assert.ok(!Object.hasOwn(body, 'naverMapUrl'), 'unchanged partial fields must not be synthesized');
});

test('Builder initialization with legacy future visibility performs no entry-time Academy write', async () => {
  // given
  let domReady;
  const fetchCalls = [];
  const academy = completeAcademy({ 'sec-reviews': true });
  academy.slug = 'regression';
  academy.publicPathUrl = '/academies/regression';
  const document = {
    title: '',
    head: { appendChild() {} },
    documentElement: { dataset: {} },
    addEventListener(type, callback) { if (type === 'DOMContentLoaded') domReady = callback; },
    getElementById(id) {
      if (id === 'pageContent') {
        return {
          dataset: {
            academySlug: academy.slug,
            academyJson: JSON.stringify(academy),
            academyPathUrl: academy.publicPathUrl,
            academyDisplayUrl: 'regression.feel2real.co.kr'
          },
          innerHTML: '',
          addEventListener() {},
          contains() { return false; },
          querySelector() { return null; },
          querySelectorAll() { return []; },
          scrollTo() {}
        };
      }
      return null;
    },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() {
      return { id: '', textContent: '', dataset: {}, style: {}, setAttribute() {}, appendChild() {} };
    }
  };
  loadBrowserScript(BUILDER, {
    document,
    fetch: async (url, init = {}) => {
      fetchCalls.push({ url, init });
      return { ok: true, json: async () => [] };
    }
  });

  // when
  await domReady();

  // then
  assert.equal(fetchCalls.filter(({ url, init }) =>
    init.method === 'PUT' && (
      url === '/api/tutor/academy'
      || url === '/api/tutor/academy/page-settings'
    )).length, 0);
});

test('final publish renormalizes stale pending visibility immediately before multipart POST', async () => {
  // given
  const storage = memoryStorage({
    pendingAcademyPublish: {
      name: '회귀 학원',
      intro: '소개',
      subjectIds: [7],
      targetGroups: ['MIDDLE'],
      regions: [{ city: '서울시', district: '강남구' }],
      pageSettings: { sectionVisibility: { 'sec-payment': true, 'sec-intro': true } }
    }
  });
  const loaded = loadHooks({ sessionStorage: storage });
  const builderHooks = loaded.hooks;
  assert.equal(typeof builderHooks.publishRequestFromPending, 'function');

  // when
  storage.setItem('pendingAcademyPublish', JSON.stringify({
    name: '회귀 학원',
    intro: '소개',
    subjectIds: [7],
    targetGroups: ['MIDDLE'],
    regions: [{ city: '서울시', district: '강남구' }],
    pageSettings: {
      sectionVisibility: {
        'sec-intro': true,
        blogPostsSection: false,
        'sec-legacy-custom': true,
        'sec-payment': true
      }
    }
  }));
  const request = builderHooks.publishRequestFromPending();

  // then
  assert.equal(request.pageSettings.sectionVisibility['sec-intro'], true);
  assert.equal(request.pageSettings.sectionVisibility.blogPostsSection, false);
  assert.equal(request.pageSettings.sectionVisibility['sec-legacy-custom'], true);
  FUTURE_IDS.forEach((id) => assert.equal(request.pageSettings.sectionVisibility[id], false, id));
});
