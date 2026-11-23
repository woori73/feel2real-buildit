const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const PF = require(path.resolve(__dirname, '../../main/resources/static/js/factory_core.js'));
const job = (over) => Object.assign({ jobId: 'j', title: '제작 작업 · 신구중 2학년 · 예상문제', exam: '신구중 2학년', track: 'NAESIN', trackLabel: '내신(영어)', stage: 'DRAFT', stageNo: 3, ranges: '', makerSession: '' }, over);

test('given 여러 줄 쪽수 구간 when parseRanges then 서버에 보낼 글 목록으로 다듬는다', () => {
  // given
  const text = '12 ~ 45\n72-83쪽, 92~115\n\n60';
  // when
  const r = PF.parseRanges(text);
  // then
  assert.equal(r.error, '');
  assert.deepEqual(r.lines, ['12~45', '72~83', '92~115', '60']);
  assert.equal(PF.pageCount(r.lines), 34 + 12 + 24 + 1);
});

test('given 글자·거꾸로 된 구간·겹치는 구간 when parseRanges then 어느 줄이 틀렸는지 알려 준다', () => {
  // given & when & then
  assert.match(PF.parseRanges('Lesson 7').error, /쪽수로/);
  assert.match(PF.parseRanges('45~12').error, /시작 쪽/);
  assert.match(PF.parseRanges('12~45\n40~50').error, /겹칩니다/);
  assert.deepEqual(PF.parseRanges('').lines, []);
});

test('given 단계·구분·검색어 when filter then 맞는 작업만, 취소·실패는 켰을 때만 보인다', () => {
  // given
  const jobs = [job({ jobId: 'a' }), job({ jobId: 'b', stage: 'REVIEW', track: 'TOEIC', trackLabel: 'TOEIC', exam: 'Part 5·6' }), job({ jobId: 'c', stage: null, stageNo: 0 })];
  // when & then
  assert.deepEqual(PF.filter(jobs, {}).map(j => j.jobId), ['a', 'b']);
  assert.deepEqual(PF.filter(jobs, { showClosed: true }).map(j => j.jobId), ['a', 'b', 'c']);
  assert.deepEqual(PF.filter(jobs, { stage: 'REVIEW' }).map(j => j.jobId), ['b']);
  assert.deepEqual(PF.filter(jobs, { track: 'NAESIN' }).map(j => j.jobId), ['a']);
  assert.deepEqual(PF.filter(jobs, { word: 'part 5' }).map(j => j.jobId), ['b']);
});

test('given 3단계 작업 when track then 앞 두 칸은 지남, 셋째 칸이 지금, 공정 밖 작업은 모두 꺼진다', () => {
  // given & when
  const html = PF.track(job());
  const closed = PF.track(job({ stageNo: 0 }));
  // then
  assert.equal((html.match(/class="done"/g) || []).length, 2);
  assert.equal((html.match(/class="now"/g) || []).length, 1);
  assert.equal((html.match(/<li/g) || []).length, 6);
  assert.ok(!closed.includes('class="now"') && !closed.includes('class="done"'));
});

test('given 검수 2/3·3/3·검수 없는 종류 when reviewBadge then 수를 보여 주고 다 채우면 ok 표시', () => {
  // given & when & then
  assert.match(PF.reviewBadge({ reviewed: true, reviewsPassed: 2, reviewsRequired: 3 }), /pf-review">2\/3/);
  assert.match(PF.reviewBadge({ reviewed: true, reviewsPassed: 3, reviewsRequired: 3 }), /pf-review ok">3\/3/);
  assert.equal(PF.reviewBadge({ reviewed: false }), '-');
});

test('given 요청 화면 값 when payload then 비운 칸은 빼고 쪽수 구간·문항 표시·숫자를 서버 형식으로 담는다', () => {
  // given
  const v = { track: 'NAESIN', type: 'EXPECTED', worker: 'CLOUD', ranges: '12~45\n72~83', labels: ['PAST_ELEMENT'], school: ' 신구중 ', grade: '중2', year: '2026', semester: '', exam: '기말', past: '', scope: '교과서', note: '', count: '40' };
  // when
  const body = PF.payload(v, 'web-abc-12345');
  // then
  assert.deepEqual(body, { requestId: 'web-abc-12345', track: 'NAESIN', type: 'EXPECTED', preferredWorker: 'CLOUD', ranges: ['12~45', '72~83'], itemLabels: ['PAST_ELEMENT'], school: '신구중', grade: '중2', exam: '기말', scopeRef: '교과서', year: 2026, count: 40 });
});

test('given 시각·난수 when requestId then 서버 규칙(8~64자 영문·숫자·-)에 맞는 번호', () => {
  // given & when
  const id = PF.requestId(1795000000000, () => 0.5);
  // then
  assert.match(id, /^[A-Za-z0-9-]{8,64}$/);
  assert.equal(id, PF.requestId(1795000000000, () => 0.5));
});

test('given 시험 묶음 요약 when examPackage then 시험·분량·이 판 검수 수·올라갈 곳을 보이고, 검수가 모자라면 그렇게 알린다', () => {
  // given
  const pkg = { title: '국어 <1회>', examCode: 'TP-NAESIN-KOR-APM2-2602-R01', subtitle: '12~45쪽', questionCount: 25, maxScore: 100,
    timeLimitSec: 1500, submittedAt: '2026-11-22T14:05:31', packageHash: 'abc123def456', passesAfterSubmit: 1,
    published: false, studentUrl: '/learn?tab=korean', firstQuestions: ['1. [그림]'] };
  // when
  const html = PF.examPackage(pkg, 3);
  const done = PF.examPackage(Object.assign({}, pkg, { passesAfterSubmit: 3, published: true }), 3);
  // then
  assert.ok(html.includes('국어 &lt;1회&gt;') && html.includes('25문항 · 100점 · 25분') && html.includes('1/3 — 이 판으로 검수를 마쳐야'));
  assert.ok(html.includes('2026-11-22 14:05') && html.includes('/learn?tab=korean') && html.includes('1. [그림]'));
  assert.ok(done.includes('3/3 — 게시할 수 있습니다') && done.includes('<a href="/learn?tab=korean">'));
  assert.equal(PF.examPackage(null, 3), '');
});

test('given 게시용 파일 글 when readPack then 객체로 읽고, JSON이 아니거나 문항·이름이 없으면 까닭을 알려 준다', () => {
  // given
  const ok = JSON.stringify({ title: '신구중 12회', questions: [{ prompt: 'q' }] });
  // when & then
  assert.equal(PF.readPack(ok).questions.length, 1);
  assert.throws(() => PF.readPack('<html>'), /JSON 형식/);
  assert.throws(() => PF.readPack('{"title":"x","questions":[]}'), /문항/);
  assert.throws(() => PF.readPack('{"questions":[{}]}'), /시험 이름/);
});
