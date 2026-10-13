const assert = require('node:assert/strict');
const test = require('node:test');
const E = require('../../main/resources/static/js/assistant_export.js');

test('given 학원 이름에 금지 글자와 공백이 있을 때 when 파일 이름을 만들면 then 안전한 이름에 날짜가 붙는다', () => {
  // given
  const name = '새봄 영어/학원: "A"';
  // when
  const file = E.fileName(name, new Date(2026, 9, 13));
  // then
  assert.equal(file, '새봄-영어-학원-A-20261013.html');
});

test('given 이름이 비었을 때 when 파일 이름을 만들면 then homepage를 쓴다', () => {
  // given / when
  const file = E.fileName('  ', new Date(2026, 0, 5));
  // then
  assert.equal(file, 'homepage-20260105.html');
});

test('given 스크립트가 든 HTML when 내려받기 내용으로 바꾸면 then 스크립트는 없고 base와 doctype이 들어간다', () => {
  // given
  const html = '<html><head><title>t</title><script>alert(1)</script></head><body><p>hi</p><noscript>x</noscript></body></html>';
  // when
  const out = E.finalizeHtml(html, 'https://f2r.example/');
  // then
  assert.ok(out.startsWith('<!DOCTYPE html>'));
  assert.ok(!/<script/i.test(out));
  assert.ok(!/<noscript/i.test(out));
  assert.match(out, /<head><base href="https:\/\/f2r\.example\/">/);
  assert.match(out, /<p>hi<\/p>/);
});

test('given 이미 base와 doctype이 있을 때 when 바꾸면 then 중복으로 넣지 않는다', () => {
  // given
  const html = '<!doctype html><html><head><base href="/x/"></head><body></body></html>';
  // when
  const out = E.finalizeHtml(html, 'https://f2r.example/');
  // then
  assert.equal((out.match(/<base /gi) || []).length, 1);
  assert.equal((out.match(/<!doctype/gi) || []).length, 1);
});

test('given 서버 버전 정보 when 한 줄로 바꾸면 then 번호·시각·메모가 보인다', () => {
  // given
  const v = { versionNo: 3, createdAt: '2026-10-13T07:40:12.123', note: '게시 전 공개본' };
  // when / then
  assert.equal(E.versionLabel(v), 'v3 · 10.13 07:40 · 게시 전 공개본');
  assert.equal(E.versionLabel({ versionNo: 1 }), 'v1 · 기록');
  assert.equal(E.versionLabel(null), '버전 · 기록');
});

test('given 상대 경로 url이 든 CSS when 절대 주소로 바꾸면 then 상대만 바뀌고 data·https는 그대로다', () => {
  // given
  const css = "a{background:url(../img/a.png)} b{background:url('/x/b.png')} c{background:url(data:image/png;base64,AAA)} d{background:url(https://cdn.example/d.png)}";
  // when
  const out = E.absolutizeCssUrls(css, 'https://f2r.example/css/site.css');
  // then
  assert.match(out, /url\(https:\/\/f2r\.example\/img\/a\.png\)/);
  assert.match(out, /url\('https:\/\/f2r\.example\/x\/b\.png'\)/);
  assert.match(out, /url\(data:image\/png;base64,AAA\)/);
  assert.match(out, /url\(https:\/\/cdn\.example\/d\.png\)/);
});
