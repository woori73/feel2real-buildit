/* 내 강의실 화면. */
(function () {
  'use strict';

  var grid = document.querySelector('[data-myclass-grid]');

  function render() {
    return window.LmsData.listCourses({}).then(function (courses) {
      var enrollments = window.LmsState.getEnrollments();
      grid.innerHTML = '';

      if (enrollments.length === 0) {
        var empty = document.createElement('p');
        empty.className = 'myclass-empty';
        empty.textContent = '수강 중인 강좌가 없습니다.';
        grid.appendChild(empty);
        return;
      }

      enrollments.forEach(function (en) {
        var course = courses.find(function (c) { return c.id === en.courseId; });
        if (!course) return;
        var card = document.createElement('div');
        card.className = 'myclass-card';
        var head = document.createElement('div');
        var school = document.createElement('div');
        school.className = 'school';
        school.textContent = course.school + ' · ' + course.grade;
        var title = document.createElement('h3');
        title.textContent = course.title;
        head.appendChild(school);
        head.appendChild(title);
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn';
        btn.textContent = '강의 이어보기';
        btn.addEventListener('click', function () {
          window.Lms.go('/lecture/' + course.id);
        });
        card.appendChild(head);
        card.appendChild(btn);
        grid.appendChild(card);
      });
    });
  }

  document.addEventListener('DOMContentLoaded', render);
})();
