/* 온라인 내신특강(강좌 목록) 화면. */
(function () {
  'use strict';

  var grid = document.querySelector('[data-course-grid]');
  var searchInput = document.querySelector('[data-filter-search]');
  var schoolSelect = document.querySelector('[data-filter-school]');
  var gradeSelect = document.querySelector('[data-filter-grade]');

  function showSkeleton() {
    grid.innerHTML = new Array(3).fill(
      '<div class="course-card skeleton-card">'
      + '<div class="skeleton skeleton-thumb"></div>'
      + '<div class="skeleton skeleton-line" style="width:40%"></div>'
      + '<div class="skeleton skeleton-line" style="width:80%"></div>'
      + '<div class="skeleton skeleton-line" style="width:60%"></div>'
      + '</div>').join('');
  }

  function render() {
    return window.LmsData.listCourses({
      keyword: searchInput.value,
      school: schoolSelect.value,
      grade: gradeSelect.value
    }).then(function (courses) {
      grid.innerHTML = '';
      courses.forEach(function (c) {
        var card = document.createElement('div');
        card.className = 'course-card';
        card.innerHTML = '<div class="course-thumb">🎬</div>'
          + '<div class="course-card-body">'
          + '<div class="school"></div>'
          + '<h3></h3>'
          + '<p></p>'
          + '<div class="price"></div>'
          + '<div class="course-card-actions">'
          + '<button class="btn-outline btn-trailer" type="button">🎬 2분 맛보기</button>'
          + '<button class="btn btn-enroll" type="button">수강신청</button>'
          + '</div></div>';
        // user-generated가 될 값들은 textContent로 넣는다(2단계에 실데이터가 들어와도 안전하도록).
        card.querySelector('.school').textContent = c.school + ' · ' + c.grade;
        card.querySelector('h3').textContent = c.title;
        card.querySelector('p').textContent = c.desc;
        card.querySelector('.price').textContent = window.Lms.fmt(c.price);
        card.querySelector('.btn-enroll').addEventListener('click', function () {
          window.Lms.go('/courses/' + c.id);
        });
        card.querySelector('.btn-trailer').addEventListener('click', function () {
          window.LmsPlayer.openTrailerModal(c);
        });
        grid.appendChild(card);
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    // 헤더 검색(GET ?q=)에서 넘어온 키워드를 필터에 채운다.
    var q = new URLSearchParams(window.location.search).get('q');
    if (q) searchInput.value = q;

    [searchInput, schoolSelect, gradeSelect].forEach(function (el) {
      el.addEventListener('input', render);
      el.addEventListener('change', render);
    });

    showSkeleton();
    setTimeout(render, 450);
  });
})();
