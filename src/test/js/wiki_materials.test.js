const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const WM = require(path.resolve(__dirname, '../../main/resources/static/js/wiki_materials.js'));
const ctx = { school: '신구중', grade: '2학년', year: 2026, semester: '2학기', exam: '기말' };

test('given 시험 Context 여러 개 when label·group·counts then 이름·학교 학년 묶음·자료 수 요약을 만든다', () => {
  // given
  const list = [Object.assign({ past_exam: 2, school_print: 3, final: 1, exam_analysis: 0, weekly: 0 }, ctx),
    Object.assign({}, ctx, { exam: '중간', past_exam: 0, school_print: 1, final: 0, exam_analysis: 0, weekly: 2 }),
    { school: '압구정중', grade: '1학년', year: 2026, semester: '2학기', exam: '기말', past_exam: 1 }];
  // when & then
  assert.equal(WM.label(ctx), '신구중 2학년 2026 2학기 기말');
  assert.deepEqual(WM.group(list).map(g => [g.name, g.contexts.length]), [['신구중 2학년', 2], ['압구정중 1학년', 1]]);
  assert.equal(WM.counts(list[0]), '기출 2 · 프린트 3 · 완성본 1');
  assert.equal(WM.counts(list[1]), '프린트 1 · 주간 모의고사 2');
});

test('given 위키 자료·완성본 when openUrl then 원본이 있으면 원본, 없으면 HTML, 완성본은 HTML 우선', () => {
  // given & when & then
  assert.equal(WM.openUrl({ from: 'material', id: 7, original_name: 'a.pdf' }), '/problem-generator/wiki/api/exam-materials/7/original');
  assert.equal(WM.openUrl({ from: 'material', id: 8, original_name: '' }), '/problem-generator/wiki/api/exam-materials/8/html');
  assert.equal(WM.openUrl({ from: 'final', id: 3, has_html: 1 }), '/problem-generator/wiki/api/final-deliverables/3/html');
  assert.equal(WM.openUrl({ from: 'final', id: 4, has_html: 0 }), '/problem-generator/wiki/api/final-deliverables/4/original');
});

test('given 올리기 값 when checkUpload then 빈 칸·연도·종류·확장자·크기를 막고, 맞으면 빈 글', () => {
  // given
  const v = Object.assign({ type: 'PAST_EXAM' }, ctx);
  const pdf = { name: '2025기말.PDF', size: 1000 };
  // when & then
  assert.equal(WM.checkUpload(v, pdf), '');
  assert.match(WM.checkUpload(Object.assign({}, v, { school: ' ' }), pdf), /학교/);
  assert.match(WM.checkUpload(Object.assign({}, v, { year: '26' }), pdf), /네 자리/);
  assert.match(WM.checkUpload(Object.assign({}, v, { type: 'X' }), pdf), /종류/);
  assert.match(WM.checkUpload(v, null), /파일/);
  assert.match(WM.checkUpload(Object.assign({}, v, { type: 'EXAM_ANALYSIS' }), { name: 'a.png', size: 1 }), /기출 분석/);
  assert.match(WM.checkUpload(v, { name: 'big.pdf', size: 31 * 1024 * 1024 }), /30MB/);
});

test('given 위키 자료 when ref then 제작 요청 칸에 넣을 「내신위키 #번호 제목(종류)」', () => {
  // given & when & then
  assert.equal(WM.ref({ from: 'material', id: 12, title: '2025 기말 기출', kind: 'PAST_EXAM' }), '내신위키 #12 2025 기말 기출(기출문제)');
  assert.equal(WM.ref({ from: 'final', id: 3, title: '예상문제 FINAL', kind: 'final' }), '내신위키 #F3 예상문제 FINAL(완성본(FINAL))');
  assert.equal(WM.query(ctx), 'school=%EC%8B%A0%EA%B5%AC%EC%A4%91&grade=2%ED%95%99%EB%85%84&year=2026&semester=2%ED%95%99%EA%B8%B0&exam=%EA%B8%B0%EB%A7%90');
});
