const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync(
  path.join(__dirname, '../../main/resources/static/js/landing_publish.js'),
  'utf8'
);

test('Given a class-based subject field, When landing publish initializes, Then required target and region controls are inserted', async () => {
  // given
  let domReady;
  let closestSelector;
  let inserted;
  const subjectBlock = {
    insertAdjacentElement(position, element) {
      inserted = { position, element };
    }
  };
  const subjectButtons = {
    closest(selector) {
      closestSelector = selector;
      return selector === '.builder-field' ? subjectBlock : null;
    }
  };
  const documentRef = {
    addEventListener(event, callback) {
      if (event === 'DOMContentLoaded') domReady = callback;
    },
    createElement() {
      return { style: {}, innerHTML: '' };
    },
    getElementById(id) {
      return id === 'subjectBtns' ? subjectButtons : null;
    }
  };

  vm.runInNewContext(script, {
    alert() {},
    console,
    document: documentRef,
    fetch: async () => ({ ok: false }),
    location: { href: '' },
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    window: {}
  });

  // when
  await domReady();

  // then
  assert.equal(closestSelector, '.builder-field');
  assert.equal(inserted.position, 'afterend');
  assert.match(inserted.element.innerHTML, /id="targetBtns"/);
  assert.match(inserted.element.innerHTML, /id="regionCity"/);
});

test('Given a valid tutor landing draft, When publish is clicked, Then pending request and preview normalize all future sections without posting Academy', async () => {
  // given
  let domReady;
  let publishClick;
  const fetchCalls = [];
  const values = {
    academyNameInput: { value: '회귀 학원' },
    sloganInput: { value: '소개 문구' },
    tuitionInput: { value: '300000' },
    regionCity: { value: '서울시' },
    publishBtn: {
      addEventListener(type, callback) {
        if (type === 'click') publishClick = callback;
      }
    }
  };
  const subjectBlock = { insertAdjacentElement() {} };
  const documentRef = {
    addEventListener(type, callback) {
      if (type === 'DOMContentLoaded') domReady = callback;
    },
    createElement() {
      return { className: '', style: {}, innerHTML: '' };
    },
    getElementById(id) {
      if (id === 'subjectBtns') {
        return { closest(selector) { return selector === '.builder-field' ? subjectBlock : null; } };
      }
      return values[id] || null;
    },
    querySelectorAll(selector) {
      if (selector === '#subjectBtns .subj-btn.active') return [{ dataset: { subj: '영어' } }];
      if (selector === '#targetBtns .subj-btn.active') return [{ dataset: { target: 'MIDDLE' } }];
      return [];
    }
  };
  const stored = new Map([['currentUser', JSON.stringify({ role: 'TUTOR' })]]);
  const sessionStorage = {
    getItem(key) { return stored.get(key) || null; },
    setItem(key, value) { stored.set(key, String(value)); },
    removeItem(key) { stored.delete(key); }
  };
  const location = { href: '' };
  vm.runInNewContext(script, {
    alert() {},
    console,
    document: documentRef,
    fetch: async (url, init = {}) => {
      fetchCalls.push({ url, init });
      if (url === '/api/subjects') {
        return { ok: true, json: async () => [{ subjectId: 7, nameKo: '영어' }] };
      }
      if (url === '/api/tutor/academy/me') return { ok: false, status: 404 };
      throw new Error(`unexpected fetch ${url}`);
    },
    location,
    sessionStorage,
    window: {}
  });
  await domReady();

  // when
  await publishClick();

  // then
  const request = JSON.parse(stored.get('pendingAcademyPublish'));
  const preview = JSON.parse(stored.get('pendingAcademyPreview'));
  const futureIds = [
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment'
  ];
  assert.equal(request.pageSettings.builderDraft.monthlyTuition, '300,000원');
  assert.equal(preview.pageSettings.builderDraft.monthlyTuition, '300,000원');
  futureIds.forEach((id) => {
    assert.equal(request.pageSettings.sectionVisibility[id], false, id);
    assert.equal(preview.pageSettings.sectionVisibility[id], false, id);
  });
  assert.equal(location.href, '/builder');
  assert.equal(fetchCalls.some(({ url, init }) =>
    url === '/api/tutor/academy' && init.method === 'POST'), false);
});
