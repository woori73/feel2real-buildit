const test = require('node:test');
const assert = require('node:assert');
const ei = require('../../main/resources/static/js/exam_insight.js');

test('extOf: 확장자를 소문자로 돌려준다', () => {
  // given a filename with uppercase ext
  const name = 'Exam.PDF';
  // when
  const ext = ei.extOf(name);
  // then
  assert.strictEqual(ext, 'pdf');
});

test('unsupportedFiles: 이미지·hwp는 처리 불가로 분류한다', () => {
  // given
  const supported = ['pdf', 'docx', 'hwpx', 'txt', 'md'];
  // when
  const bad = ei.unsupportedFiles(['a.pdf', 'b.jpg', 'c.hwp', 'd.hwpx'], supported);
  // then
  assert.deepStrictEqual(bad, ['b.jpg', 'c.hwp']);
});

test('formatsNotice: 지원/미지원 형식을 모두 안내한다', () => {
  // given
  const cfg = { supportedFormats: ['pdf'], unsupported: ['이미지(JPG·PNG 등)'] };
  // when
  const text = ei.formatsNotice(cfg);
  // then
  assert.match(text, /PDF/);
  assert.match(text, /이미지/);
});

test('buildOrderMessage: 분석 번호와 상품 정보를 포함한다', () => {
  // given
  const meta = { school: 'A고', grade: '고2', publisher: '능률', examType: '중간고사' };
  // when
  const msg = ei.buildOrderMessage('EI-123', meta);
  // then
  assert.match(msg, /EI-123/);
  assert.match(msg, /40문항/);
  assert.match(msg, /20,000원/);
  assert.match(msg, /A고 \/ 고2/);
});

test('externalPrompt: 두 문서를 모두 담고 추정 표기를 요구한다', () => {
  // given/when
  const p = ei.externalPrompt('DOC-ONE', 'DOC-TWO');
  // then
  assert.match(p, /DOC-ONE/);
  assert.match(p, /DOC-TWO/);
  assert.match(p, /추정/);
});

test('slug: 문서 종류별 파일명을 만든다', () => {
  // given/when/then
  assert.strictEqual(ei.slug('X1', 'direction'), 'exam-direction_X1.md');
  assert.strictEqual(ei.slug('X1', 'analysis'), 'exam-analysis_X1.md');
});
