const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const JS_PATH = path.resolve(__dirname, '../../main/resources/static/js/tutor_academy_inquiries.js');
const JS = fs.readFileSync(JS_PATH, 'utf8');
const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/tutor/tutor_academy_inquiries.html'), 'utf8');
const { telHref, formatWhen } = require(JS_PATH);

test('given 정상·이상한 연락처 when telHref 하면 then 숫자(+선택)만 tel 링크가 된다', () => {
  // given / when / then
  assert.equal(telHref('01012345678'), 'tel:01012345678');
  assert.equal(telHref('+821012345678'), 'tel:+821012345678');
  assert.equal(telHref('javascript:alert(1)'), null);
  assert.equal(telHref(''), null);
});

test('given 잘못된 날짜 when formatWhen 하면 then 빈 문자열', () => {
  // given / when / then
  assert.equal(formatWhen('x'), '');
  assert.equal(formatWhen(null), '');
});

test('given 문의 화면 JS when 읽기 then 서버 문자열은 innerHTML이 아니라 textContent로만 넣고 콘솔 출력이 없다', () => {
  // given / when / then
  assert.doesNotMatch(JS, /innerHTML|insertAdjacentHTML|eval\(|document\.write/);
  assert.doesNotMatch(JS, /console\./);
});

test('given 문의 화면 when 읽기 then 상태 3단계 필터·전용 API·보관 안내가 있다', () => {
  // given / when / then
  for (const s of ['NEW', 'IN_PROGRESS', 'DONE']) assert.ok(HTML.includes('data-status="' + s + '"'), s);
  assert.match(JS, /\/api\/tutor\/academy\/inquiries/);
  assert.ok(HTML.includes('1년'));
});

test('given 문의 화면 JS when $(id) 참조를 모으면 then 모두 템플릿에 있다', () => {
  // given
  const ids = new Set([...JS.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)].map((m) => m[1]));
  // when / then
  for (const id of ids) assert.match(HTML, new RegExp('id="' + id + '"'), id);
});
