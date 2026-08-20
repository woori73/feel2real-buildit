const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const apiScript = fs.readFileSync(
  path.join(__dirname, '../../main/resources/static/js/api.js'),
  'utf8',
);

function loadArchiveAwareUrl(pathname) {
  const location = { pathname };
  const window = {};
  vm.runInNewContext(apiScript, {
    alert() {},
    fetch() {},
    location,
    sessionStorage: {
      clear() {},
      getItem() { return null; },
      setItem() {},
    },
    window,
  });
  return window.archiveAwareUrl;
}

test('Given exact archived page, When API URL is built, Then archived prefix is added', () => {
  const archiveAwareUrl = loadArchiveAwareUrl('/archived');

  const actual = archiveAwareUrl('/api/auth/me');

  assert.equal(actual, '/archived/api/auth/me');
});

test('Given archived child page, When API URL is built, Then archived prefix is added', () => {
  const archiveAwareUrl = loadArchiveAwareUrl('/archived/admin/students');

  const actual = archiveAwareUrl('/api/admin/students');

  assert.equal(actual, '/archived/api/admin/students');
});

test('Given near-prefix or normal page, When API URL is built, Then URL is unchanged', () => {
  assert.equal(loadArchiveAwareUrl('/archived-preview')('/api/auth/me'), '/api/auth/me');
  assert.equal(loadArchiveAwareUrl('/home')('/api/auth/me'), '/api/auth/me');
});

test('Given archived page, When non-API or already archived URL is built, Then URL is unchanged', () => {
  const archiveAwareUrl = loadArchiveAwareUrl('/archived');

  assert.equal(archiveAwareUrl('/home'), '/home');
  assert.equal(archiveAwareUrl('https://example.com/api/auth/me'), 'https://example.com/api/auth/me');
  assert.equal(archiveAwareUrl('/archived/api/auth/me'), '/archived/api/auth/me');
  assert.equal(archiveAwareUrl({ path: '/api/auth/me' }).path, '/api/auth/me');
});
