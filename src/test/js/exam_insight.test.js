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

test('unsupportedFiles: 지원 목록에 없는 형식(hwp·heic·gif)을 처리 불가로 분류한다', () => {
  // given
  const supported = ['pdf', 'docx', 'hwpx', 'txt', 'md', 'html', 'htm', 'jpg', 'jpeg', 'png', 'webp'];
  // when
  const bad = ei.unsupportedFiles(['a.pdf', 'b.jpg', 'c.hwp', 'd.hwpx', 'e.HEIC', 'f.html', 'g.gif'], supported);
  // then
  assert.deepStrictEqual(bad, ['c.hwp', 'e.HEIC', 'g.gif']);
});

const CFG = {
  documentFormats: ['pdf', 'docx', 'hwpx', 'txt', 'md'], webFormats: ['html', 'htm'],
  imageFormats: ['jpg', 'jpeg', 'png', 'webp'], unsupported: ['HEIC(JPG로 저장해 올려 주세요)', '스캔한 PDF'],
  maxFilesPerGroup: 20, maxImagesPerRequest: 20, maxImageBytes: 10 * 1024 * 1024,
};

test('formatsNotice: 문서·웹 문서·사진 형식과 미지원 형식을 실제 지원 범위대로 안내한다', () => {
  // given/when
  const text = ei.formatsNotice(CFG);
  // then
  assert.match(text, /문서 PDF·DOCX·HWPX·TXT·MD/);
  assert.match(text, /웹 문서 HTML·HTM/);
  assert.match(text, /사진 JPG·JPEG·PNG·WEBP/);
  assert.match(text, /HEIC/);
  assert.doesNotMatch(text, /이미지.*처리할 수 없|글자를 읽지 못하므로/);
});

test('formatsNotice: 사진 분석이 꺼져 있으면 사진 형식을 지원한다고 안내하지 않는다', () => {
  // given
  const cfg = Object.assign({}, CFG, { imageFormats: [], unsupported: ['사진(JPG·PNG·WebP) — 현재 사진 분석을 사용할 수 없어요'] });
  // when
  const text = ei.formatsNotice(cfg);
  // then
  assert.doesNotMatch(text, /사진 JPG/);
  assert.match(text, /사진 분석을 사용할 수 없어요/);
});

test('classify: 확장자로 사진·HTML·문서·미지원을 나눈다 (사진 분석이 꺼지면 사진은 미지원)', () => {
  assert.strictEqual(ei.classify('a.JPG', CFG), 'image');
  assert.strictEqual(ei.classify('a.webp', CFG), 'image');
  assert.strictEqual(ei.classify('w.htm', CFG), 'html');
  assert.strictEqual(ei.classify('a.docx', CFG), 'document');
  assert.strictEqual(ei.classify('a.heic', CFG), 'unsupported');
  assert.strictEqual(ei.classify('a.jpg', Object.assign({}, CFG, { imageFormats: [] })), 'unsupported');
});

test('validateNew: 형식·크기·개수·중복을 사유와 함께 걸러 낸다', () => {
  // given
  const existing = [{ key: 'a|1|1', kind: 'image' }];
  const files = [
    { name: 'a.jpg', size: 1, key: 'a|1|1' },
    { name: 'b.jpg', size: 100, key: 'b|100|1' },
    { name: 'IMG_1.HEIC', size: 100, key: 'h|100|1' },
    { name: 'big.png', size: 11 * 1024 * 1024, key: 'big|1|1' },
    { name: 'c.html', size: 500, key: 'c|500|1' },
  ];
  // when
  const r = ei.validateNew(files, existing, CFG);
  // then
  assert.deepStrictEqual(r.accepted.map((x) => [x.name, x.kind]), [['b.jpg', 'image'], ['c.html', 'html']]);
  assert.strictEqual(r.rejected.length, 3);
  assert.match(r.rejected[0].message, /이미 추가/);
  assert.match(r.rejected[1].message, /HEIC.*JPG/);
  assert.match(r.rejected[2].message, /너무 커요/);
});

test('validateNew: 항목당 파일 수와 사진 수 상한을 지킨다', () => {
  // given
  const cfg = Object.assign({}, CFG, { maxFilesPerGroup: 3, maxImagesPerRequest: 2 });
  const files = ['1.jpg', '2.jpg', '3.jpg', '4.html'].map((n) => ({ name: n, size: 10, key: n }));
  // when
  const r = ei.validateNew(files, [], cfg);
  // then: 사진은 2장까지, 전체는 3개까지
  assert.deepStrictEqual(r.accepted.map((x) => x.name), ['1.jpg', '2.jpg', '4.html']);
  assert.match(r.rejected[0].message, /사진은 최대 2장/);
});

test('moveItem: 사진 순서를 위·아래로 바꾸고 범위를 벗어나면 그대로 둔다', () => {
  assert.deepStrictEqual(ei.moveItem([1, 2, 3], 1, -1), [2, 1, 3]);
  assert.deepStrictEqual(ei.moveItem([1, 2, 3], 1, 1), [1, 3, 2]);
  assert.deepStrictEqual(ei.moveItem([1, 2, 3], 0, -1), [1, 2, 3]);
  assert.deepStrictEqual(ei.moveItem([1, 2, 3], 2, 1), [1, 2, 3]);
});

test('buildItemsPayload: 읽기 완료된 항목만, 사용자가 수정한 텍스트와 순서로, 종류 표시를 붙여 보낸다', () => {
  // given
  const items = [
    { name: 'p2.jpg', kind: 'image', status: 'ok', text: '수정된 둘째 장' },
    { name: 'w.html', kind: 'html', status: 'ok', text: 'html text' },
    { name: 'bad.jpg', kind: 'image', status: 'error', text: '' },
    { name: 'blank.txt', kind: 'document', status: 'ok', text: '   ' },
    { name: 'n.txt', kind: 'document', status: 'ok', text: 'doc' },
  ];
  // when
  const payload = ei.buildItemsPayload(items);
  // then
  assert.deepStrictEqual(payload, [
    { label: '[사진] p2.jpg', text: '수정된 둘째 장' },
    { label: '[HTML] w.html', text: 'html text' },
    { label: 'n.txt', text: 'doc' },
  ]);
});

test('blockingReason: 읽는 중이거나 오류 항목이 있으면 분석을 막고 사유를 알린다', () => {
  assert.strictEqual(ei.blockingReason([{ status: 'ok' }]), null);
  assert.match(ei.blockingReason([{ status: 'reading' }]), /읽는 중/);
  assert.match(ei.blockingReason([{ status: 'queued' }, { status: 'ok' }]), /읽는 중/);
  assert.match(ei.blockingReason([{ status: 'error' }, { status: 'error' }]), /2개/);
});

test('statusLabel: 상태별 문구와 확인 필요 개수를 보여 준다', () => {
  assert.strictEqual(ei.statusLabel({ status: 'queued', kind: 'image' }), '대기 중');
  assert.strictEqual(ei.statusLabel({ status: 'reading', kind: 'image' }), '사진 읽는 중…');
  assert.match(ei.statusLabel({ status: 'ok', kind: 'image', text: 'abcd', uncertain: 2 }), /4자 · 확인 필요 2곳/);
  assert.match(ei.statusLabel({ status: 'error', message: '읽지 못했어요' }), /오류: 읽지 못했어요/);
});

test('formatBytes: 읽기 쉬운 크기 표기', () => {
  assert.strictEqual(ei.formatBytes(500), '500B');
  assert.strictEqual(ei.formatBytes(2048), '2KB');
  assert.strictEqual(ei.formatBytes(3 * 1024 * 1024), '3MB');
});

test('buildOrderMessage: 분석 번호와 상품 정보를 포함한다', () => {
  // given
  const meta = { school: 'A고', grade: '고2', publisher: '능률', examType: '중간고사' };
  // when
  const msg = ei.buildOrderMessage('EI-123', meta, '2주 · 주 2회 · 총 4회 · 회당 40문항 · 총 160문항 · 20,000원');
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
