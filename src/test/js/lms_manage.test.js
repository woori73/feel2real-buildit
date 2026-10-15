const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const root = process.env.LMS_SOURCE_ROOT || path.join(__dirname, '../../main/resources');
const source = fs.readFileSync(path.join(root, 'static/js/lms_manage.js'), 'utf8');

function functionBody(name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `${name} must exist`);
  const next = source.indexOf('\n  function ', start + 1);
  return source.slice(start, next === -1 ? source.length : next);
}

test('given the live course renderer then it is read-only and never mutates LMS session or MOCK state', () => {
  // given / when
  const rows = functionBody('renderCourseRows');

  // then
  assert.match(rows, /deleteManageCourse\(course\.lessonId\)/);
  assert.doesNotMatch(rows, /contentEditable|updateCourse|deleteCourse|LmsState|sessionStorage|refreshCartCount|LMS_MOCK/);
});

test('given manage load and review rendering then only manage uses live data while reviews retain MOCK semantics', () => {
  // given / when
  const manage = functionBody('renderCourses');
  const reviews = functionBody('renderAdminReviews');

  // then
  assert.match(manage, /listManageCourses\(window\.Lms\.handle\)/);
  assert.doesNotMatch(manage, /listCourses|LmsState/);
  assert.match(reviews, /listCourses\(\{\}\)/);
  assert.match(reviews, /LmsState\.getReviews\(\)/);
});

test('given delete failure handling then 409 and generic branches preserve rows and count', () => {
  // given / when
  const rows = functionBody('renderCourseRows');

  // then
  const removal = rows.indexOf('manageCourses = manageCourses.filter');
  const failure = rows.indexOf('.catch(function (error)');
  assert.ok(removal > -1 && failure > removal);
  assert.doesNotMatch(rows.slice(failure), /manageCourses\s*=\s*manageCourses\.filter|renderCourseRows\(\)/);
  assert.match(rows.slice(failure), /error\.status === 409/);
});
