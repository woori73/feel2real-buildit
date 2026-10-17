const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const SRC = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/academy_public.js'), 'utf8');
const start = SRC.indexOf('function previewLinkAction(');
const end = SRC.indexOf('if (WORKING_COPY_PREVIEW) {', start);
const previewLinkAction = new Function(SRC.slice(start, end) + '\nreturn previewLinkAction;')();
const O = 'https://f2r.example';
const SLUG = '새-영어학원';

test('given 로고의 공개 주소 링크 when 미리보기에서 누르면 then 이동하지 않고 맨 위로 스크롤한다', () => {
  // given
  const logoHref = '/academies/' + encodeURIComponent(SLUG);
  // when / then
  assert.equal(previewLinkAction(logoHref, SLUG, O), 'scrollTop');
  assert.equal(previewLinkAction('/', SLUG, O), 'scrollTop');
  assert.equal(previewLinkAction(O + '/academies/' + encodeURIComponent(SLUG) + '/', SLUG, O), 'scrollTop');
});

test('given 같은 사이트의 다른 경로 when 미리보기에서 누르면 then 미리보기가 사라지지 않게 막는다', () => {
  // given / when / then
  assert.equal(previewLinkAction('/login', SLUG, O), 'block');
  assert.equal(previewLinkAction('/academies/other-academy', SLUG, O), 'block');
});

test('given 외부 주소·해시·tel·mailto when 누르면 then 외부는 새 탭, 나머지는 그대로 허용한다', () => {
  // given / when / then
  assert.equal(previewLinkAction('https://blog.naver.com/x', SLUG, O), 'newtab');
  assert.equal(previewLinkAction('#sec-lessons', SLUG, O), 'allow');
  assert.equal(previewLinkAction('tel:01012345678', SLUG, O), 'allow');
  assert.equal(previewLinkAction('mailto:a@b.co', SLUG, O), 'allow');
});

test('given 위험한 스킴 when 누르면 then 막는다', () => {
  // given / when / then
  assert.equal(previewLinkAction('javascript:alert(1)', SLUG, O), 'block');
  assert.equal(previewLinkAction('data:text/html,x', SLUG, O), 'block');
});

test('given 미리보기 모드가 아닐 때 when 읽으면 then 클릭 가로채기는 작업본 미리보기에만 등록된다', () => {
  // given / when / then
  assert.match(SRC, /if \(WORKING_COPY_PREVIEW\) \{\s*document\.addEventListener\('click'/);
});
