const assert = require('node:assert/strict');
const test = require('node:test');

const preview = require('../../main/resources/static/js/landing_preview.js');

test('Given a tuition amount, When formatting the landing preview, Then Korean won formatting is returned', () => {
  assert.equal(preview.formatTuition('300000'), '300,000원');
  assert.equal(preview.formatTuition(''), '—');
});

test('Given a Korean academy name, When building the public host preview, Then whitespace and punctuation are removed', () => {
  assert.equal(preview.compactAcademyHandle(' 압구정 에듀홀릭! '), '압구정에듀홀릭');
  assert.equal(preview.compactAcademyHandle(''), 'my-academy');
});

test('Given multiple selected subjects, When building preview state, Then every subject is reflected', () => {
  const actual = preview.buildPreviewState({
    name: '모두 학원',
    slogan: '함께 성장하는 수업',
    subjects: ['영어', '수학'],
    tuition: 250000
  });

  assert.deepEqual(actual, {
    name: '모두 학원',
    slogan: '함께 성장하는 수업',
    subjectSummary: '영어 · 수학 전문',
    tuition: '250,000원',
    publicHost: '모두학원.feel2real.co.kr'
  });
});

test('Given empty inputs, When building preview state, Then safe visible defaults are used', () => {
  const actual = preview.buildPreviewState({});

  assert.equal(actual.name, '내 학원');
  assert.equal(actual.slogan, '성장을 만드는 수업');
  assert.equal(actual.subjectSummary, '영어 전문');
  assert.equal(actual.tuition, '—');
});

test('Given hero rotation indexes, When selecting roles, Then each Korean particle matches its role', () => {
  assert.deepEqual(preview.roleForIndex(0), { word: '강사', particle: '를' });
  assert.deepEqual(preview.roleForIndex(1), { word: '원장', particle: '을' });
  assert.deepEqual(preview.roleForIndex(2), { word: '강사', particle: '를' });
});
