const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/tutor_academy_setup.js'),
  'utf8'
);
const FUTURE_IDS = [
  'sec-reviews',
  'sec-curriculum',
  'sec-effect',
  'sec-digitaltwin',
  'sec-market',
  'sec-payment'
];

function field(value = '') {
  const listeners = {};
  return {
    value,
    checked: false,
    files: [],
    options: [],
    addEventListener(type, callback) { listeners[type] = callback; },
    insertAdjacentHTML(_position, html) {
      const match = html.match(/value="([^"]+)"/);
      if (match) this.options.push({ value: match[1] });
    },
    listeners
  };
}

async function setupHarness(existingAcademy) {
  let submit;
  const requests = [];
  const academyParts = [];
  class RecordingFormData {
    append(name, value) {
      if (name === 'academy') academyParts.push(value);
    }
  }
  const fields = {
    academyForm: {
      addEventListener(type, callback) {
        if (type === 'submit') submit = callback;
      }
    },
    statusMsg: field(),
    intro: field('소개'),
    introCount: field(),
    handle: field('regression-academy'),
    handlePreview: field(),
    handleStatus: field(),
    subjectIds: field('7'),
    name: field('회귀 학원'),
    logo: field(),
    cover: field(),
    blogUrl: field('https://blog.naver.com/new'),
    storeUrl: field('https://smartstore.naver.com/new'),
    city1: field(''),
    city2: field(''),
    city3: field(''),
    district1: field(''),
    district2: field(''),
    district3: field('')
  };
  const target = { value: 'MIDDLE', checked: true };
  const supportedVisibility = [
    { value: 'sec-principal', checked: true },
    { value: 'sec-philosophy', checked: true },
    { value: 'sec-intro', checked: true }
  ];
  const document = {
    getElementById(id) { return fields[id] || null; },
    querySelector(selector) {
      const targetMatch = selector.match(/^#targetGroups input\[value="([^"]+)"\]$/);
      return targetMatch && targetMatch[1] === target.value ? target : null;
    },
    querySelectorAll(selector) {
      if (selector === '#targetGroups input:checked') return target.checked ? [target] : [];
      if (selector === '#sectionVisibility input') return supportedVisibility;
      return [];
    }
  };
  const fetch = async (url, init = {}) => {
    requests.push({ url, init, transport: 'fetch' });
    if (url === '/api/subjects') {
      return { ok: true, json: async () => [{ subjectId: 7, nameKo: '영어' }] };
    }
    throw new Error(`unexpected fetch: ${url}`);
  };
  const fetchAuth = async (url, init = {}) => {
    requests.push({ url, init, transport: 'fetchAuth' });
    if (url === '/api/tutor/academy/me') {
      return existingAcademy
        ? { ok: true, status: 200, json: async () => existingAcademy }
        : { ok: false, status: 404 };
    }
    if (url === '/api/tutor/academy') {
      return { ok: true, status: 200, json: async () => ({ slug: 'regression' }) };
    }
    throw new Error(`unexpected fetchAuth: ${url}`);
  };
  vm.runInNewContext(script, {
    Blob,
    clearTimeout,
    console,
    document,
    fetch,
    fetchAuth,
    FormData: RecordingFormData,
    location: { href: '' },
    setTimeout() {}
  });
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  return { academyParts, fields, requests, submit, supportedVisibility };
}

async function submittedAcademy(harness) {
  harness.fields.city1.value = '서울시';
  harness.fields.district1.value = '강남구';
  await harness.submit({ preventDefault() {} });
  assert.equal(harness.academyParts.length, 1);
  return JSON.parse(await harness.academyParts[0].text());
}

test('setup initialization loads existing data without performing an Academy POST or PUT', async () => {
  // given / when
  const harness = await setupHarness({
    name: '기존 학원',
    handle: 'existing',
    intro: '기존 소개',
    subjects: [{ subjectId: 7 }],
    targetGroups: ['MIDDLE'],
    regions: [{ city: '서울시', district: '강남구' }],
    pageSettings: { sectionVisibility: { 'sec-payment': true } }
  });

  // then
  assert.equal(harness.requests.some(({ url, init }) =>
    url === '/api/tutor/academy' && ['POST', 'PUT'].includes(init.method)), false);
});

test('new setup POST emits six explicit future OFF keys and retains supported visibility', async () => {
  // given
  const harness = await setupHarness(null);

  // when
  const request = await submittedAcademy(harness);

  // then
  assert.equal(harness.requests.at(-1).init.method, 'POST');
  assert.equal(request.pageSettings.sectionVisibility['sec-principal'], true);
  assert.equal(request.pageSettings.sectionVisibility['sec-philosophy'], true);
  assert.equal(request.pageSettings.sectionVisibility['sec-intro'], true);
  FUTURE_IDS.forEach((id) => assert.equal(request.pageSettings.sectionVisibility[id], false, id));
});

test('existing setup PUT deeply preserves unrelated pageSettings and compatibility visibility keys', async () => {
  // given
  const originalSettings = {
    blogUrl: 'https://blog.naver.com/old',
    storeUrl: 'https://smartstore.naver.com/old',
    naverMapUrl: 'https://map.naver.com/example',
    heroYoutubeUrl: 'https://www.youtube.com/watch?v=abcdefghijk',
    sectionVisibility: {
      'sec-principal': true,
      blogPostsSection: false,
      'sec-legacy-custom': true,
      'sec-payment': true
    },
    sectionOrder: [
      'sec-hero', 'sec-intro', 'sec-principal', 'sec-philosophy', 'sec-videos', 'sec-reviews', 'sec-lessons',
      'sec-curriculum', 'sec-effect', 'sec-digitaltwin', 'sec-market',
      'sec-payment', 'sec-social'
    ],
    builderDraft: { monthlyTuition: '300,000원', principalCareer: '경력 A' },
    businessInfo: { businessName: '기존 학원' }
  };
  const harness = await setupHarness({
    name: '기존 학원',
    handle: 'existing',
    intro: '기존 소개',
    subjects: [{ subjectId: 7 }],
    targetGroups: ['MIDDLE'],
    regions: [{ city: '서울시', district: '강남구' }],
    pageSettings: structuredClone(originalSettings)
  });
  harness.fields.blogUrl.value = 'https://blog.naver.com/new';
  harness.fields.storeUrl.value = 'https://smartstore.naver.com/new';
  harness.supportedVisibility.find(({ value }) => value === 'sec-principal').checked = false;

  // when
  const request = await submittedAcademy(harness);

  // then
  assert.equal(harness.requests.at(-1).init.method, 'PUT');
  assert.equal(request.pageSettings.blogUrl, 'https://blog.naver.com/new');
  assert.equal(request.pageSettings.storeUrl, 'https://smartstore.naver.com/new');
  assert.equal(request.pageSettings.naverMapUrl, originalSettings.naverMapUrl);
  assert.equal(request.pageSettings.heroYoutubeUrl, originalSettings.heroYoutubeUrl);
  assert.deepEqual(request.pageSettings.sectionOrder, originalSettings.sectionOrder);
  assert.deepEqual(request.pageSettings.builderDraft, originalSettings.builderDraft);
  assert.deepEqual(request.pageSettings.businessInfo, originalSettings.businessInfo);
  assert.equal(request.pageSettings.sectionVisibility['sec-principal'], false);
  assert.equal(request.pageSettings.sectionVisibility['sec-philosophy'], true);
  assert.equal(request.pageSettings.sectionVisibility.blogPostsSection, false);
  assert.equal(request.pageSettings.sectionVisibility['sec-legacy-custom'], true);
  FUTURE_IDS.forEach((id) => assert.equal(request.pageSettings.sectionVisibility[id], false, id));
});
