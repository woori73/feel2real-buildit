const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = process.env.LMS_SOURCE_ROOT || path.join(__dirname, '../../main/resources');
const source = fs.readFileSync(path.join(root, 'static/js/lms_data.js'), 'utf8');

function load(fetchAuth) {
  const mock = { courses: [{ id: 1, title: 'MOCK' }] };
  const window = { LMS_MOCK: mock, fetchAuth };
  vm.runInNewContext(source, { window, console, Error, JSON, Number, Promise, encodeURIComponent });
  return { data: window.LmsData, mock };
}

test('given a handle when listing manage courses then the encoded owner endpoint is fetched without auth redirect', async () => {
  // given
  const calls = [];
  const expected = [{ lessonId: 7, name: '실제 수업' }];
  const { data, mock } = load(async (url, options) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => expected };
  });

  // when
  const result = await data.listManageCourses('my academy');

  // then
  assert.deepEqual(result, expected);
  assert.deepEqual(JSON.parse(JSON.stringify(calls)), [{
    url: '/api/tutor/lms/my%20academy/lessons',
    options: { redirectOnAuthError: false }
  }]);
  assert.deepEqual(mock.courses, [{ id: 1, title: 'MOCK' }]);
});

test('given a lesson id when deleting then only the existing soft-delete endpoint is called', async () => {
  // given
  const calls = [];
  const { data, mock } = load(async (url, options) => {
    calls.push({ url, options });
    return { ok: true, status: 204 };
  });

  // when
  await data.deleteManageCourse('7/unsafe');

  // then
  assert.deepEqual(JSON.parse(JSON.stringify(calls)), [{
    url: '/api/tutor/academy/lessons/7%2Funsafe',
    options: { method: 'DELETE', redirectOnAuthError: false }
  }]);
  assert.deepEqual(mock.courses, [{ id: 1, title: 'MOCK' }]);
});

test('given a 409 response when deleting then status is preserved on the error and MOCK stays unchanged', async () => {
  // given
  const { data, mock } = load(async () => ({ ok: false, status: 409 }));

  // when / then
  await assert.rejects(data.deleteManageCourse(7), (error) => error.status === 409);
  assert.deepEqual(mock.courses, [{ id: 1, title: 'MOCK' }]);
});
