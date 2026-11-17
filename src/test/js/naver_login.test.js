const { test } = require('node:test');
const assert = require('node:assert/strict');
const N = require('../../main/resources/static/js/naver_login.js');

test('targetUrl: 보던 화면으로 돌아간다(로그인 안내용 login 파라미터는 뺀다)', () => {
  // given & when & then
  assert.equal(N.targetUrl('/wiki', '?grade=1'), '/wiki?grade=1');
  assert.equal(N.targetUrl('/admissions/guide', ''), '/admissions/guide');
  assert.equal(N.targetUrl('/exam', '?login=required'), '/exam');
  assert.equal(N.targetUrl('/', ''), '/');
});

test('targetUrl: 첫 화면의 ?returnUrl=이 있으면 그 주소로 돌아간다', () => {
  // given
  const search = '?login=required&returnUrl=' + encodeURIComponent('/wiki/learning/7?tab=a');
  // when & then
  assert.equal(N.targetUrl('/', search), '/wiki/learning/7?tab=a');
});

test('targetUrl: 외부 주소·관리자·가입·계정 화면은 첫 화면(/)으로', () => {
  // given & when & then
  assert.equal(N.targetUrl('/', '?returnUrl=' + encodeURIComponent('https://evil.com')), '/');
  assert.equal(N.targetUrl('/', '?returnUrl=' + encodeURIComponent('//evil.com')), '/');
  assert.equal(N.targetUrl('/', '?returnUrl=' + encodeURIComponent('/admin/dashboard')), '/');
  assert.equal(N.targetUrl('/register', ''), '/');
  assert.equal(N.targetUrl('/signin/reset/password', ''), '/');
  assert.equal(N.targetUrl('/account/password-change', ''), '/');
});

test('startHref: 네이버 시작점 주소에 복귀 주소를 인코딩해 붙인다', () => {
  // given & when & then
  assert.equal(N.startHref('/wiki?grade=1'), '/auth/naver/start?returnUrl=%2Fwiki%3Fgrade%3D1');
  assert.equal(N.startHref(''), '/auth/naver/start?returnUrl=%2F');
});

test('targetUrl: 첫 화면이 넘겨 둔 복귀 주소(pending)를 먼저 쓴다, 위험하면 /', () => {
  // given & when & then
  assert.equal(N.targetUrl('/', '', '/wiki?grade=1'), '/wiki?grade=1');
  assert.equal(N.targetUrl('/', '', '/dashboard'), '/dashboard');
  assert.equal(N.targetUrl('/', '', '//evil.com'), '/');
  assert.equal(N.targetUrl('/', '', '/admin/students'), '/');
  assert.equal(N.targetUrl('/', '', null), '/');
});

test('startHref: 강사 탭이면 role=tutor를 붙이고, 그 밖에는 붙이지 않는다', () => {
  // given & when & then
  assert.equal(N.startHref('/', 'tutor'), '/auth/naver/start?returnUrl=%2F&role=tutor');
  assert.equal(N.startHref('/', 'student'), '/auth/naver/start?returnUrl=%2F');
  assert.equal(N.startHref('/', null), '/auth/naver/start?returnUrl=%2F');
});
