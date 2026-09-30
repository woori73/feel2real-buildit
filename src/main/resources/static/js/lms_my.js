/* 내 강의실 화면. */
(function () {
  'use strict';

  var grid = document.querySelector('[data-myclass-grid]');
  var reviewGrid = document.querySelector('[data-review-lessons-grid]');

  /** 재원생 복습수업(실 API) — LMS_MOCK/LmsState와 무관하다. */
  function renderReviewLessons() {
    if (!reviewGrid) return Promise.resolve();
    return window.LmsData.listMyReviewLessons(window.Lms.handle).then(function (lessons) {
      reviewGrid.innerHTML = '';
      if (!lessons || lessons.length === 0) {
        var empty = document.createElement('p');
        empty.className = 'myclass-empty';
        empty.textContent = '아직 배정된 복습수업이 없습니다.';
        reviewGrid.appendChild(empty);
        return;
      }
      lessons.forEach(function (lesson) {
        var card = document.createElement('div');
        card.className = 'myclass-card';
        var head = document.createElement('div');
        var school = document.createElement('div');
        school.className = 'school';
        school.textContent = [lesson.schoolName, lesson.gradeLabel].filter(Boolean).join(' ');
        var title = document.createElement('h3');
        title.textContent = lesson.lessonName;
        head.appendChild(school);
        head.appendChild(title);
        var count = document.createElement('p');
        count.className = 'review-content-count';
        count.textContent = lesson.contentCount + '개 영상';
        head.appendChild(count);
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn';
        btn.textContent = '복습 시작';
        btn.addEventListener('click', function () {
          window.Lms.go('/review/' + lesson.lessonId);
        });
        card.appendChild(head);
        card.appendChild(btn);
        reviewGrid.appendChild(card);
      });
    }).catch(function () {
      reviewGrid.innerHTML = '';
      var error = document.createElement('p');
      error.className = 'myclass-empty';
      error.textContent = '복습수업 목록을 불러오지 못했습니다.';
      reviewGrid.appendChild(error);
    });
  }

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

  document.addEventListener('DOMContentLoaded', function () {
    renderReviewLessons();
    render();
  });
})();
