/*
 * 학원 LMS 데이터 접근 seam.
 *
 * 모든 메서드가 Promise를 반환한다. 강좌관리 course tab의 LessonProduct 조회/삭제만 실 API를
 * 사용하며, 공개 강좌·후기·장바구니 등 나머지 LMS 기능은 기존 MOCK seam을 유지한다.
 * 페이지 스크립트가 LMS_MOCK을 직접 참조하는 것은 계속 금지한다.
 *
 * ⚠ 1단계 mutation은 메모리 반영일 뿐이라 새로고침하면 사라진다. 의도된 동작이다
 *   (이건 목업 데이터라는 성질이 수명에 그대로 드러나야 한다).
 */
(function () {
  'use strict';

  var mock = window.LMS_MOCK;

  function clone(value) {
    return value == null ? value : JSON.parse(JSON.stringify(value));
  }

  function find(courseId) {
    var id = Number(courseId);
    return mock.courses.find(function (c) { return c.id === id; }) || null;
  }

  function responseError(response, fallbackMessage) {
    var error = new Error(fallbackMessage);
    error.status = response.status;
    return error;
  }

  window.LmsData = {
    /** filters: {keyword, school, grade} — 전부 선택. */
    listCourses: function (filters) {
      var f = filters || {};
      var keyword = (f.keyword || '').trim();
      var list = mock.courses.filter(function (c) {
        if (f.school && c.school !== f.school) return false;
        if (f.grade && c.grade !== f.grade) return false;
        if (keyword && (c.school + c.title + c.desc).indexOf(keyword) === -1) return false;
        return true;
      });
      return Promise.resolve(clone(list));
    },

    /** owner 전용 강좌관리 표의 실제 LessonProduct 목록. */
    listManageCourses: function (handle) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/lessons';
      return window.fetchAuth(url, { redirectOnAuthError: false }).then(function (response) {
        if (!response.ok) throw responseError(response, '강좌 목록을 불러오지 못했습니다.');
        return response.json();
      });
    },

    getCourse: function (courseId) {
      return Promise.resolve(clone(find(courseId)));
    },

    listVideos: function (courseId) {
      var course = find(courseId);
      return Promise.resolve(course ? clone(course.videos) : []);
    },

    /* addCourse는 제거했다 — 강좌 등록은 MOCK 배열에 행을 넣는 대신
       /tutor/academy/lessons/editor(수업 상품 전용 편집기)로 이동한다. */

    /** patch: {title, school, grade, price, desc} 중 일부. */
    updateCourse: function (courseId, patch) {
      var course = find(courseId);
      if (!course) return Promise.resolve(null);
      Object.keys(patch || {}).forEach(function (key) { course[key] = patch[key]; });
      return Promise.resolve(clone(course));
    },

    deleteCourse: function (courseId) {
      var id = Number(courseId);
      mock.courses = mock.courses.filter(function (c) { return c.id !== id; });
      return Promise.resolve();
    },

    /** owner 전용 강좌관리 표의 실제 LessonProduct soft-delete. */
    deleteManageCourse: function (lessonId) {
      var url = '/api/tutor/academy/lessons/' + encodeURIComponent(lessonId);
      return window.fetchAuth(url, {
        method: 'DELETE',
        redirectOnAuthError: false
      }).then(function (response) {
        if (!response.ok) throw responseError(response, '강좌를 삭제하지 못했습니다.');
      });
    },

    /** patch: {title, desc, url, fileName, uploadDate} 중 일부. */
    updateVideo: function (courseId, videoKey, patch) {
      var course = find(courseId);
      if (!course) return Promise.resolve(null);
      var video = course.videos.find(function (v) { return v.key === videoKey; });
      if (!video) return Promise.resolve(null);
      Object.keys(patch || {}).forEach(function (key) { video[key] = patch[key]; });
      return Promise.resolve(clone(video));
    },

    // ── 콘텐츠 관리(실 API, 에듀홀릭 LMS Phase 1) ──────────────────────────
    // 아래 함수들은 LMS_MOCK을 건드리지 않는다 — 재원생 복습영상은 별도 도메인이다.

    /** 강좌관리 콘텐츠 탭: 강좌 선택 목록 + 등록된 콘텐츠 전체. */
    listAdminContents: function (handle) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/contents';
      return window.fetchAuth(url, { redirectOnAuthError: false }).then(function (response) {
        if (!response.ok) throw responseError(response, '콘텐츠 목록을 불러오지 못했습니다.');
        return response.json();
      });
    },

    /** formData: lessonId/title/description/schoolName/gradeLabel/examName/unitLabel/video. */
    createAdminContent: function (handle, formData) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/contents';
      return window.fetchAuth(url, {
        method: 'POST',
        body: formData,
        redirectOnAuthError: false
      }).then(function (response) {
        if (!response.ok) throw responseError(response, '콘텐츠를 저장하지 못했습니다.');
        return response.json();
      });
    },

    /** patch: {lessonId, title, description, schoolName, gradeLabel, examName, unitLabel, displayOrder}. */
    updateAdminContent: function (handle, lmsContentId, patch) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/contents/' + encodeURIComponent(lmsContentId);
      return window.fetchAuth(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(patch),
        redirectOnAuthError: false
      }).then(function (response) {
        if (!response.ok) throw responseError(response, '콘텐츠를 수정하지 못했습니다.');
        return response.json();
      });
    },

    setAdminContentPublished: function (handle, lmsContentId, published) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/contents/'
        + encodeURIComponent(lmsContentId) + '/publish';
      return window.fetchAuth(url, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ published: !!published }),
        redirectOnAuthError: false
      }).then(function (response) {
        if (!response.ok) throw responseError(response, '공개 상태를 변경하지 못했습니다.');
        return response.json();
      });
    },

    deleteAdminContent: function (handle, lmsContentId) {
      var url = '/api/tutor/lms/' + encodeURIComponent(handle) + '/contents/' + encodeURIComponent(lmsContentId);
      return window.fetchAuth(url, { method: 'DELETE', redirectOnAuthError: false }).then(function (response) {
        if (!response.ok) throw responseError(response, '콘텐츠를 삭제하지 못했습니다.');
      });
    },

    // ── 내 복습수업(실 API, 학생) ────────────────────────────────────────────

    listMyReviewLessons: function (handle) {
      var url = '/api/lms/' + encodeURIComponent(handle) + '/my/lessons';
      return window.fetchAuth(url, { redirectOnAuthError: false }).then(function (response) {
        if (!response.ok) throw responseError(response, '복습수업 목록을 불러오지 못했습니다.');
        return response.json();
      });
    },

    getReviewLesson: function (handle, lessonId) {
      var url = '/api/lms/' + encodeURIComponent(handle) + '/my/lessons/' + encodeURIComponent(lessonId);
      return window.fetchAuth(url, { redirectOnAuthError: false }).then(function (response) {
        if (!response.ok) throw responseError(response, '복습수업 정보를 불러오지 못했습니다.');
        return response.json();
      });
    }
  };
})();
