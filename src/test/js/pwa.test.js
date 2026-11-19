const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const STATIC = path.resolve(__dirname, '../../main/resources/static');

test('manifest: 시작 화면은 관리자 바로가기(/easy/app), 아이콘 파일이 실제로 있다', () => {
  // given
  const m = JSON.parse(fs.readFileSync(path.join(STATIC, 'manifest.json'), 'utf8'));
  // when & then
  assert.equal(m.start_url, '/easy/app');
  assert.equal(m.display, 'standalone');
  assert.ok(m.icons.some(i => i.sizes === '512x512' && i.purpose === 'maskable'));
  for (const icon of m.icons) assert.ok(fs.existsSync(path.join(STATIC, icon.src)), icon.src);
});

test('service worker: 화면 이동(GET navigate)만 다루고 API·파일·POST는 건드리지 않는다(개인 자료 저장 안 함)', () => {
  // given
  global.self = { addEventListener() {}, skipWaiting() {}, clients: { claim() {} } };
  const sw = require(path.join(STATIC, 'sw.js'));
  // when & then
  assert.equal(sw.isNavigation({ mode: 'navigate', method: 'GET' }), true);
  assert.equal(sw.isNavigation({ mode: 'cors', method: 'GET' }), false);
  assert.equal(sw.isNavigation({ mode: 'navigate', method: 'POST' }), false);
  assert.equal(sw.OFFLINE_URL, '/offline.html');
  const src = fs.readFileSync(path.join(STATIC, 'sw.js'), 'utf8');
  assert.equal((src.match(/cache\.add\(/g) || []).length, 1, '미리 저장하는 것은 연결 끊김 안내 1개뿐');
  assert.ok(!/cache\.put\(/.test(src), '응답을 저장하지 않는다');
});
