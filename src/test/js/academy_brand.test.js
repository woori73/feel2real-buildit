const test = require('node:test');
const assert = require('node:assert/strict');
const brand = require('../../main/resources/static/js/academy_brand.js');

test('given Korean design intent when recommending then only a registered preset is returned', () => {
  const cases = [
    ['좀 더 고급스럽게 만들어줘','PREMIUM'],
    ['깔끔하고 현대적인 학원으로 보여줘','CLEAN'],
    ['초등학생에게 친근한 느낌으로','FRIENDLY'],
    ['입시 전문학원처럼 신뢰감 있게','ACADEMIC'],
    ['폰트가 너무 딱딱해','FRIENDLY'],
    ['제목만 좀 더 고급스럽게','PREMIUM']
  ];
  cases.forEach(([input, expected]) => assert.equal(brand.inferPreset(input), expected));
});

test('given untrusted draft when resolving then font ids and mobile sizes stay in registry bounds', () => {
  const resolved = brand.resolve({ typographyPreset:'UNKNOWN', headingFont:'url(evil)', logoSizeMobile:'999', bodySizeMobile:'2' });
  assert.equal(resolved.presetId, 'MODERN');
  assert.ok(brand.FONT_REGISTRY[resolved.headingFont]);
  assert.equal(resolved.logoMobile, 104);
  assert.equal(resolved.bodyMobile, 15);
});

test('given preset when creating draft then safe registry font ids are stored', () => {
  const draft = brand.presetDraft('PREMIUM', { logoSizeMobile:'76' });
  assert.equal(draft.typographyPreset, 'PREMIUM');
  assert.equal(draft.headingFont, 'NOTO_SERIF_KR');
  assert.equal(draft.bodyFont, 'PRETENDARD');
  assert.equal(draft.logoSizeMobile, '76');
});

function fakeRoot() {
  const props = {};
  return { dataset: {}, style: { setProperty: (k, v) => { props[k] = v; }, removeProperty: (k) => { delete props[k]; } }, props };
}

test('given motionStyle in draft when applying then data-motion is set only for registered values', () => {
  ['none', 'fade', 'slide'].forEach((value) => {
    const root = fakeRoot();
    brand.apply(root, { motionStyle: value });
    assert.equal(root.dataset.motion, value);
  });
  const evil = fakeRoot();
  brand.apply(evil, { motionStyle: 'url(evil)' });
  assert.equal(evil.dataset.motion, undefined);
});

test('given motionStyle removed from draft when applying again then data-motion is cleared (existing sites unchanged)', () => {
  const root = fakeRoot();
  brand.apply(root, { motionStyle: 'slide' });
  brand.apply(root, {});
  assert.equal(root.dataset.motion, undefined);
});
