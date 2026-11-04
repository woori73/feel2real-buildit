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
  assert.deepEqual(r.counts, { universities: 1, documents: 1, departments: 0, types: 1, schedules: 1, youtube: 0, priorResults: 0, jeongsiFormulas: 0 });
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

test('given blank exclude input when parsing then no keys are sent', () => {
  assert.deepEqual(load.parseExcludeKeys(''), []);
  assert.deepEqual(load.parseExcludeKeys('   '), []);
  assert.deepEqual(load.parseExcludeKeys(null), []);
  assert.deepEqual(load.parseExcludeKeys(undefined), []);
});

test('given comma-separated exclude keys when parsing then they are trimmed and deduplicated', () => {
  assert.deepEqual(load.parseExcludeKeys('doc_cau_2027_susi'), ['doc_cau_2027_susi']);
  assert.deepEqual(load.parseExcludeKeys(' doc_a , doc_b,,doc_a , '), ['doc_a', 'doc_b']);
});

test('given a running status when formatting then percent, progress and current item are shown', () => {
  // given
  const st = { total: 188, done: 47, missing: 141, running: true, indexSize: 0, indexReady: false,
               lastRun: { mode: 'MISSING', result: 'RUNNING', total: 141, processed: 12, embedded: 12, failed: 0, currentId: 99, failures: {} } };
  // when
  const v = load.formatReindexStatus(st);
  // then
  assert.equal(v.percent, 25);
  assert.equal(v.running, true);
  assert.match(v.headline, /전체 188 · 완료 47 · 미완료 141/);
  assert.match(v.headline, /진행 중 12\/141 \(항목 99 처리 중\)/);
  assert.match(v.headline, /준비 안 됨/);
  assert.equal(v.hasFailures, false);
});

test('given a finished run with failures when formatting then failure reasons are listed per item', () => {
  // given
  const st = { total: 4, done: 3, missing: 1, running: false, indexSize: 3, indexReady: true,
               lastRun: { result: 'COMPLETED', total: 4, processed: 4, embedded: 3, failed: 1, currentId: null, failures: { 7: '타임아웃 60000ms' } } };
  // when
  const v = load.formatReindexStatus(st);
  // then
  assert.equal(v.percent, 75);
  assert.equal(v.running, false);
  assert.match(v.headline, /이번 실행 실패 1/);
  assert.match(v.headline, /마지막 실행 완료/);
  assert.match(v.headline, /준비됨/);
  assert.deepEqual(v.failureLines, ['항목 7: 타임아웃 60000ms']);
});

test('given no run record after restart when formatting then DB counts still show and total 0 is safe', () => {
  const v = load.formatReindexStatus({ total: 188, done: 100, missing: 88, running: false, indexSize: 100, indexReady: true, lastRun: null });
  assert.equal(v.percent, 53);
  assert.doesNotMatch(v.headline, /마지막 실행/);
  assert.equal(load.formatReindexStatus({ total: 0, done: 0, missing: 0 }).percent, 0);
  assert.equal(load.formatReindexStatus(null).percent, 0);
});

test('given a load response with update and archive counts when formatting then they are shown', () => {
  // given
  const r = { universities: 0, materials: 8, departments: 112, types: 79, schedules: 264, skippedUnknownYear: 0,
              departmentsUpdated: 433, typesUpdated: 31, schedulesArchived: 15, supersededArchived: 25, schedulesSourced: 51, status: 'LOADED' };
  // when
  const text = load.formatLoadResult(r);
  // then
  assert.match(text, /학과 112/);
  assert.match(text, /갱신 학과 433 · 갱신 전형 31/);
  assert.match(text, /보관 일정 15/);
  assert.match(text, /예전 자료 보관 25 · 일정 출처 연결 51/);
});

test('given an older server response without update counts when formatting then the extra parts are omitted', () => {
  // given
  const r = { universities: 1, materials: 1, departments: 2, types: 1, schedules: 1, skippedUnknownYear: 0, status: 'LOADED' };
  // when
  const text = load.formatLoadResult(r);
  // then
  assert.doesNotMatch(text, /갱신|보관/);
  assert.match(text, /상태 LOADED/);
});

test('given a seed with youtube_knowledge when previewed then the youtube count is reported', () => {
  // given
  const text = JSON.stringify(Object.assign({}, SEED, { youtube_knowledge: [{ id: 'YT-A001' }, { id: 'YT-A002' }] }));
  // when
  const r = load.summarizeSeed(text);
  // then
  assert.equal(r.counts.youtube, 2);
});

test('given a load response with youtube knowledge count when formatting then it is shown only when positive', () => {
  // given
  const base = { universities: 0, materials: 0, departments: 0, types: 0, schedules: 0, skippedUnknownYear: 0, status: 'LOADED' };
  // when
  const withYt = load.formatLoadResult(Object.assign({ youtubeKnowledge: 110 }, base));
  const zeroYt = load.formatLoadResult(Object.assign({ youtubeKnowledge: 0 }, base));
  // then
  assert.match(withYt, /유튜브 해석 110/);
  assert.doesNotMatch(zeroYt, /유튜브/);
});

test('given a load in progress when controls are computed then file picker and load button are both locked', () => {
  // given / when
  const c = load.loadControls({ loading: true, loaded: false, previewOk: true });
  // then
  assert.deepEqual(c, { fileDisabled: true, runDisabled: true });
});

test('given a finished load when controls are computed then the button stays off until a new file is chosen', () => {
  // given / when
  const done = load.loadControls({ loading: false, loaded: true, previewOk: true });
  const fresh = load.loadControls({ loading: false, loaded: false, previewOk: true });
  // then
  assert.deepEqual(done, { fileDisabled: false, runDisabled: true });
  assert.deepEqual(fresh, { fileDisabled: false, runDisabled: false });
});

test('given no valid preview when controls are computed then the load button is off', () => {
  assert.deepEqual(load.loadControls({ loading: false, loaded: false, previewOk: false }), { fileDisabled: false, runDisabled: true });
});

test('given a seed with prior_results when previewed then the prior result count is reported and unknown codes are warned', () => {
  // given
  const text = JSON.stringify(Object.assign({}, SEED, { prior_results: [
    { university_code: SEED.ipsi_universities[0].university_code, academic_year: 2027, source_document_key: SEED.ipsi_documents[0].document_key },
    { university_code: 'NOPE', academic_year: 2027, source_document_key: SEED.ipsi_documents[0].document_key }] }));
  // when
  const r = load.summarizeSeed(text);
  // then
  assert.equal(r.counts.priorResults, 2);
  assert.ok(r.warnings.some(w => /university_code/.test(w)));
});

test('given a load response with prior result counts when formatting then new and changed counts are shown', () => {
  // given
  const r = { universities: 0, materials: 2, departments: 0, types: 0, schedules: 0, skippedUnknownYear: 0,
              priorResults: 950, priorResultsUpdated: 3, status: 'LOADED' };
  // when
  const text = load.formatLoadResult(r);
  // then
  assert.match(text, /지난해 입시결과 신규 950 · 변경 3/);
});

test('given two valid seed previews when combined then counts are summed and years merged', () => {
  // given
  const a = load.summarizeSeed(JSON.stringify(SEED));
  const b = load.summarizeSeed(JSON.stringify({ ...SEED, ipsi_schedules: [] }));
  // when
  const c = load.combinePreviews([{ name: '01.json', preview: a }, { name: '02.json', preview: b }]);
  // then
  assert.equal(c.ok, true);
  assert.equal(c.files, 2);
  assert.equal(c.counts.universities, 2);
  assert.equal(c.counts.types, 2);
  assert.equal(c.counts.schedules, 1);
  assert.deepEqual(c.years, [2027]);
});

test('given one broken file when combined then the whole batch is blocked with that file name', () => {
  // given
  const good = load.summarizeSeed(JSON.stringify(SEED));
  const bad = load.summarizeSeed('{not json');
  // when
  const c = load.combinePreviews([{ name: '01.json', preview: good }, { name: '02.json', preview: bad }]);
  // then
  assert.equal(c.ok, false);
  assert.match(c.error, /^02\.json: /);
});

test('given build job states when formatted then running, done and error read plainly', () => {
  // given / when / then
  assert.match(load.formatBuildStatus({ running: true, lastRun: { result: 'RUNNING', startedAt: 't0' } }), /생성 중/);
  assert.equal(load.formatBuildStatus({ running: false, lastRun: { result: 'COMPLETED', created: 3, updated: 1, unchanged: 10 } }),
    '지식 항목 생성 — 새로 3 · 갱신 1 · 변경 없음 10 · 완료');
  assert.match(load.formatBuildStatus({ running: false, lastRun: { result: 'ERROR', error: 'IllegalStateException' } }), /실패: IllegalStateException/);
  assert.equal(load.formatBuildStatus({ running: false, lastRun: null }), '');
});

test('given a seed with jeongsi_formulas when previewed then the formula count is reported and unknown codes are warned', () => {
  // given
  const seed = { ...SEED, jeongsi_formulas: [
    { university_code: 'GA', academic_year: 2027, formula_key: 'ga_hum', source_document_key: 'doc_ga_2027_susi' },
    { university_code: 'ZZ', academic_year: 2027, formula_key: 'zz_nat', source_document_key: 'doc_ga_2027_susi' }] };
  // when
  const r = load.summarizeSeed(JSON.stringify(seed));
  // then
  assert.equal(r.counts.jeongsiFormulas, 2);
  assert.equal(r.ok, false);
  assert.ok(r.warnings.some(w => /university_code/.test(w)));
});

test('given a load response with jeongsi formula counts when formatting then they are shown only when present', () => {
  // given
  const base = { universities: 0, materials: 0, departments: 0, types: 0, schedules: 0, skippedUnknownYear: 0, status: 'LOADED' };
  // when
  const shown = load.formatLoadResult({ ...base, jeongsiFormulas: 156, jeongsiFormulasUpdated: 0 });
  const hidden = load.formatLoadResult({ ...base, jeongsiFormulas: 0, jeongsiFormulasUpdated: 0 });
  // then
  assert.match(shown, /정시 계산식 신규 156 · 변경 0/);
  assert.doesNotMatch(hidden, /정시 계산식/);
});
