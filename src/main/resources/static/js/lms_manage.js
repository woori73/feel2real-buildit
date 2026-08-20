/* 강좌관리 화면 (운영자 전용 — 접근 통제는 서버가 이미 끝냈다). */
(function () {
  'use strict';

  var body = document.querySelector('[data-manage-body]');
  var countEl = document.querySelector('[data-manage-count]');
  var adminReviewList = document.querySelector('[data-admin-review-list]');
  var manageCourses = [];

  function cell(value) {
    var td = document.createElement('td');
    td.textContent = value == null || value === '' ? '-' : value;
    return td;
  }

  function formatPrice(course) {
    if (course.priceUnit === 'NEGOTIABLE') return '가격 협의';
    if (course.priceAmount == null) return '-';
    var amount = Number(course.priceAmount).toLocaleString('ko-KR') + '원';
    if (course.priceUnit === 'MONTH') return amount + ' / 월';
    if (course.priceUnit === 'COURSE') return amount + ' / 강좌';
    return amount;
  }

  function statusRow(message, className) {
    var tr = document.createElement('tr');
    var td = document.createElement('td');
    td.colSpan = 6;
    td.className = className;
    td.textContent = message;
    tr.appendChild(td);
    body.appendChild(tr);
  }

  function updateCount() {
    countEl.textContent = '등록된 강좌 ' + manageCourses.length + '개';
  }

  function renderCourseRows() {
    body.innerHTML = '';
    updateCount();

    if (manageCourses.length === 0) {
      statusRow('등록된 강좌가 없습니다.', 'manage-empty');
      return;
    }

    manageCourses.forEach(function (course) {
        var tr = document.createElement('tr');
        tr.dataset.lessonId = String(course.lessonId);
        tr.appendChild(cell(course.schoolName));
        tr.appendChild(cell(course.gradeLabel));
        tr.appendChild(cell(course.name));
        tr.appendChild(cell(formatPrice(course)));
        tr.appendChild(cell(course.description));

        var actionTd = document.createElement('td');
        var deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'delete';
        deleteBtn.textContent = '삭제';
        deleteBtn.addEventListener('click', function () {
          if (!confirm('삭제하시겠습니까?')) return;
          deleteBtn.disabled = true;
          window.LmsData.deleteManageCourse(course.lessonId).then(function () {
            manageCourses = manageCourses.filter(function (item) {
              return item.lessonId !== course.lessonId;
            });
            renderCourseRows();
          }).catch(function (error) {
            deleteBtn.disabled = false;
            if (error && error.status === 409) {
              alert('신청 내역이 있는 강좌는 삭제할 수 없습니다.');
              return;
            }
            alert('강좌를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.');
          });
        });

        actionTd.appendChild(deleteBtn);
        tr.appendChild(actionTd);
        body.appendChild(tr);
    });
  }

  function renderCourses() {
    body.innerHTML = '';
    countEl.textContent = '강좌 목록을 불러오는 중입니다.';
    statusRow('강좌 목록을 불러오는 중입니다.', 'manage-loading');

    return window.LmsData.listManageCourses(window.Lms.handle).then(function (courses) {
      manageCourses = Array.isArray(courses) ? courses : [];
      renderCourseRows();
    }).catch(function () {
      manageCourses = [];
      body.innerHTML = '';
      updateCount();
      statusRow('강좌 목록을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.', 'manage-error');
    });
  }

  function renderAdminReviews() {
    return window.LmsData.listCourses({}).then(function (courses) {
      var reviews = window.LmsState.getReviews();
      adminReviewList.innerHTML = '';

      if (reviews.length === 0) {
        var empty = document.createElement('p');
        empty.className = 'admin-review-empty';
        empty.textContent = '등록된 후기가 없습니다.';
        adminReviewList.appendChild(empty);
        return;
      }

      reviews.forEach(function (r) {
        var course = courses.find(function (c) { return c.id === r.courseId; });
        var item = document.createElement('div');
        item.className = 'admin-review';

        var name = document.createElement('div');
        name.style.fontWeight = '700';
        name.style.marginBottom = '6px';
        name.textContent = course ? window.Lms.courseName(course) : '삭제된 강좌';
        item.appendChild(name);

        var stars = document.createElement('div');
        stars.className = 'review-star';
        stars.textContent = '★'.repeat(r.rating) + '☆'.repeat(5 - r.rating);
        item.appendChild(stars);

        var content = document.createElement('div');
        content.className = 'review-content';
        content.textContent = r.content;
        item.appendChild(content);

        if (r.next) {
          var next = document.createElement('div');
          next.className = 'review-next';
          next.textContent = '다음에 필요한 콘텐츠: ' + r.next;
          item.appendChild(next);
        }

        var replyBox = document.createElement('textarea');
        replyBox.rows = 2;
        replyBox.placeholder = '관리자 답변을 입력하세요';
        replyBox.value = r.reply || '';
        item.appendChild(replyBox);

        var actions = document.createElement('div');
        actions.className = 'admin-review-actions';
        var saveBtn = document.createElement('button');
        saveBtn.type = 'button';
        saveBtn.className = 'btn-flat';
        saveBtn.textContent = '답변 저장';
        saveBtn.addEventListener('click', function () {
          window.LmsState.setReviewReply(r.id, replyBox.value.trim());
          alert('답변이 저장되었습니다.');
        });
        var delBtn = document.createElement('button');
        delBtn.type = 'button';
        delBtn.className = 'btn-flat';
        delBtn.textContent = '삭제';
        delBtn.addEventListener('click', function () {
          if (!confirm('삭제하시겠습니까?')) return;
          window.LmsState.removeReview(r.id);
          renderAdminReviews();
        });
        actions.appendChild(saveBtn);
        actions.appendChild(delBtn);
        item.appendChild(actions);

        adminReviewList.appendChild(item);
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.tab-bar button').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.querySelectorAll('.tab-bar button').forEach(function (b) {
          b.classList.remove('active');
        });
        btn.classList.add('active');
        document.querySelectorAll('.manage-tab').forEach(function (panel) {
          panel.hidden = (panel.dataset.tabPanel !== btn.dataset.tab);
        });
        if (btn.dataset.tab === 'review') renderAdminReviews();
      });
    });

    // "+ 강좌 등록"은 JS 핸들러가 아니라 템플릿의 <a href="/tutor/academy/lessons/editor">다.
    // /tutor/academy의 "수업 상품 생성 +"와 같은 동작으로 맞췄다.

    renderCourses();
  });
})();
