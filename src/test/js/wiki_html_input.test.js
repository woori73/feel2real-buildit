const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const context = { window: {}, File };
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/wiki_html_input.js'), 'utf8'), context);
const fileFor = context.window.WikiHtmlInput.fileFor;
function form(source = '', name = 'study.html', file = null) {
  return { querySelector: selector => ({ '[name="file"]': { files: file ? [file] : [] }, '[name="htmlSource"]': { value: source }, '[name="htmlFilename"]': { value: name } })[selector] };
}
test('HTML text becomes a UTF-8 file without changing its contents', async () => {
  const source = '<!doctype html><html lang="ko"><p>정답 · 해설</p></html>';
  const file = fileFor(form(source, 'review.html'));
  assert.equal(file.name, 'review.html');
  assert.equal(await file.text(), source);
  assert.equal(file.size, Buffer.byteLength(source));
});
test('an ordinary upload and metadata-only edit keep their existing behavior', () => {
  const file = new File(['pdf'], 'a.pdf');
  assert.equal(fileFor(form('', '', file)), file);
  assert.equal(fileFor(form()), null);
});
test('ambiguous file plus HTML and invalid extensions are rejected', () => {
  assert.throws(() => fileFor(form('<html></html>', 'a.html', new File(['x'], 'x.html'))), /하나만/);
  for (const name of ['x.js', '../x.html', 'a\\b.html']) assert.throws(() => fileFor(form('<html></html>', name)), /파일명/);
});
test('fragments and non-HTML content are rejected before submitting', () => {
  assert.throws(() => fileFor(form('<p>fragment</p>')), /문서 전체/);
  assert.throws(() => fileFor(form('#!shell')), /문서 전체/);
});
