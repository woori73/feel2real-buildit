/* 강좌상세 화면. courseId는 URL(/lms/{handle}/courses/{courseId})에서 온다. */
(function () {
  'use strict';

  var currentCourseId = window.Lms.courseId;

  function el(name) {
    return document.querySelector('[data-detail-' + name + ']');
  }

  function renderCourseSelect(courses) {
    var box = document.querySelector('[data-course-select]');
    box.innerHTML = '';
    courses.forEach(function (c) {
      var b = document.createElement('button');
      b.type = 'button';
      b.textContent = c.school + ' ' + c.grade;
      if (c.id === currentCourseId) b.classList.add('active');
      // 강좌를 바꾸면 URL도 바뀌어야 한다(화면당 URL 1개 원칙).
      b.addEventListener('click', function () { window.Lms.go('/courses/' + c.id); });
      box.appendChild(b);
    });
  }

  function renderCurriculum(videos) {
    var box = document.querySelector('[data-curriculum-list]');
    box.innerHTML = '';
    videos.forEach(function (v) {
      var accessLabel = v.type === 'trailer' ? '누구나 시청 가능'
        : v.type === 'OT' ? '로그인 회원' : '결제자만';
      var icon = v.type === 'trailer' ? '🎬' : '▶';
      var row = document.createElement('div');
      row.className = 'curriculum-row';
      var title = document.createElement('span');
      title.textContent = icon + ' ' + v.title;
      var meta = document.createElement('span');
      meta.className = 'curriculum-meta';
      meta.textContent = (v.length || '') + ' · ' + accessLabel;
      row.appendChild(title);
      row.appendChild(meta);
      box.appendChild(row);
    });
  }

  function render() {
    return Promise.all([
      window.LmsData.listCourses({}),
      window.LmsData.getCourse(currentCourseId)
    ]).then(function (results) {
      var courses = results[0];
      var course = results[1];
      renderCourseSelect(courses);
      if (!course) {
        el('title').textContent = '강좌를 찾을 수 없습니다.';
        return;
      }
      el('title').textContent = window.Lms.courseName(course);
      el('desc').textContent = course.desc;
      el('instructor').textContent = course.instructor || '강사 소개를 입력해 주세요.';
      el('target').textContent = course.target || '수강 대상을 입력해 주세요.';
      el('price').textContent = window.Lms.fmt(course.price);

      var trailer = course.videos.find(function (v) { return v.type === 'trailer'; });
      window.LmsPlayer.mount(document.querySelector('[data-detail-player]'), course, trailer, true);
      renderCurriculum(course.videos);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelector('[data-enroll-btn]').addEventListener('click', function () {
      if (!window.Lms.isAuthenticated) {
        alert('로그인이 필요합니다.');
        return;
      }
      window.LmsState.addToCart(currentCourseId);
      window.Lms.go('/cart');
    });

    document.querySelector('[data-detail-trailer-btn]').addEventListener('click', function () {
      document.querySelector('[data-detail-player]')
        .scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    render();
  });
})();
