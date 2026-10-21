const test = require('node:test');
const assert = require('node:assert/strict');
const load = require('../../main/resources/static/js/admissions-admin-load.js');

const SEED = {
  ipsi_universities: [{ name: '가대학교', university_code: 'GA' }],
  ipsi_documents: [{ document_key: 'doc_ga_2027_susi', document_name: '가대 수시', applicable_academic_year: 2027, source_url: 'https://x.test/a' }],
  ipsi_departments: [],
  ipsi_admission_types: [{ university_code: 'GA', academic_year: 2027, type_name: '교과', source_document_key: 'doc_ga_2027_susi' }],
  ipsi_schedules: [{ university_code: 'GA', academic_year: 2027, item: '원서접수', type_name: '수시', date: '2026.9.8.', source_document_key: 'doc_ga_2027_susi' }],
};

test('given a valid seed when previewed then counts, years and no warnings are reported', () => {
  // given
  const text = JSON.stringify(SEED);
  // when
  const r = load.summarizeSeed(text);
  // then
  assert.equal(r.ok, true);
  assert.deepEqual(r.counts, { universities: 1, documents: 1, departments: 0, types: 1, schedules: 1 });
  assert.deepEqual(r.years, [2027]);
  assert.deepEqual(r.warnings, []);
});

test('given broken JSON when previewed then it fails without throwing', () => {
  const r = load.summarizeSeed('{not json');
  assert.equal(r.ok, false);
  assert.match(r.error, /JSON/);
});

test('given a seed without universities when previewed then it is rejected', () => {
  const r = load.summarizeSeed(JSON.stringify({ ipsi_universities: [] }));
  assert.equal(r.ok, false);
  assert.match(r.error, /ipsi_universities/);
});

test('given a row with an unknown university code when previewed then load is blocked and warned', () => {
  const seed = JSON.parse(JSON.stringify(SEED));
  seed.ipsi_schedules[0].university_code = 'ZZ';
  const r = load.summarizeSeed(JSON.stringify(seed));
  assert.equal(r.ok, false);
  assert.ok(r.warnings.some((w) => w.includes('university_code')));
});

test('given rows with missing or dangling source documents when previewed then it warns but stays loadable', () => {
  const seed = JSON.parse(JSON.stringify(SEED));
  seed.ipsi_admission_types[0].source_document_key = 'doc_missing';
  delete seed.ipsi_schedules[0].source_document_key;
  const r = load.summarizeSeed(JSON.stringify(seed));
  assert.equal(r.ok, true);
  assert.ok(r.warnings.some((w) => w.includes('source_document_key') && w.includes('2건')));
});

test('given unsupported academic years when previewed then it warns', () => {
  const seed = JSON.parse(JSON.stringify(SEED));
  seed.ipsi_admission_types[0].academic_year = 2029;
  const r = load.summarizeSeed(JSON.stringify(seed));
  assert.ok(r.warnings.some((w) => w.includes('2027·2028 외')));
});

test('given a year when building the confirmation then it matches the server rule', () => {
  assert.equal(load.confirmationFor(2027), 'ACTIVATE-ALL-2027');
  assert.equal(load.confirmationFor(2028), 'ACTIVATE-ALL-2028');
});
