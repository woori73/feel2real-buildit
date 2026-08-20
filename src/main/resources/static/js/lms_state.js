/*
 * 학원 LMS 클라이언트 상태(장바구니/수강내역/후기/배송) — **sessionStorage를 만지는 유일한 파일**.
 *
 * 페이지 스크립트는 LmsState.getCart() 같은 API만 부른다. 3단계에서 서버 장바구니로 바꿀 때
 * 교체 대상이 이 파일 하나로 고정되기 때문이다.
 *   검증: grep -rn "sessionStorage" src/main/resources/static/js/lms_*.js → 이 파일만 나와야 한다.
 *
 * ⚠ 키 네임스페이싱이 필수다. sessionStorage는 origin+tab 단위지 **사용자 단위가 아니다** —
 *   같은 탭에서 학생 A 로그아웃 → 학생 B 로그인 시 B가 A의 장바구니를 본다.
 *   키를 lms:{handle}:{userKey}:{name}으로 가르면 인증 상태 변화가 곧 키 변화라 자동 격리된다.
 *   userKey는 서버(AcademyLmsShellModel)가 .lms-layout[data-lms-user]로 내려준다.
 *
 * sessionStorage(탭 종료 시 소멸)를 쓰는 이유: 이건 가짜 데이터라는 성질이 상태 수명에 그대로
 * 드러나야 한다. localStorage는 세션을 넘겨 살아남아 2단계에서 유령 데이터를 만든다.
 */
(function () {
  'use strict';

  var layout = document.querySelector('.lms-layout');
  var handle = layout ? layout.dataset.handle : 'unknown';
  var userKey = layout ? layout.dataset.lmsUser : 'anon';
  var prefix = 'lms:' + handle + ':' + userKey + ':';

  function read(name, fallback) {
    try {
      var raw = sessionStorage.getItem(prefix + name);
      return raw == null ? fallback : JSON.parse(raw);
    } catch (e) {
      return fallback;
    }
  }

  function write(name, value) {
    try {
      sessionStorage.setItem(prefix + name, JSON.stringify(value));
    } catch (e) {
      /* 저장 실패(사파리 프라이빗 등)해도 화면 동작은 계속되어야 한다. */
    }
  }

  window.LmsState = {
    /* ---- 장바구니: courseId 배열 ---- */
    getCart: function () {
      return read('cart', []);
    },
    addToCart: function (courseId) {
      var cart = this.getCart();
      var id = Number(courseId);
      if (cart.indexOf(id) === -1) {
        cart.push(id);
        write('cart', cart);
      }
      return cart;
    },
    removeFromCart: function (courseId) {
      var id = Number(courseId);
      var cart = this.getCart().filter(function (c) { return c !== id; });
      write('cart', cart);
      return cart;
    },
    clearCart: function () {
      write('cart', []);
    },

    /* ---- 수강내역: {courseId} 배열 ---- */
    getEnrollments: function () {
      return read('enrollments', []);
    },
    addEnrollment: function (courseId) {
      var list = this.getEnrollments();
      var id = Number(courseId);
      if (!list.some(function (e) { return e.courseId === id; })) {
        list.push({ courseId: id });
        write('enrollments', list);
      }
      return list;
    },
    isPaidFor: function (courseId) {
      var id = Number(courseId);
      return this.getEnrollments().some(function (e) { return e.courseId === id; });
    },

    /* ---- 후기: {id, courseId, rating, content, next, reply} 배열 ---- */
    getReviews: function () {
      return read('reviews', []);
    },
    addReview: function (review) {
      var list = this.getReviews();
      var nextId = read('nextReviewId', 1);
      review.id = nextId;
      list.push(review);
      write('reviews', list);
      write('nextReviewId', nextId + 1);
      return list;
    },
    removeReview: function (reviewId) {
      var list = this.getReviews().filter(function (r) { return r.id !== reviewId; });
      write('reviews', list);
      return list;
    },
    setReviewReply: function (reviewId, reply) {
      var list = this.getReviews();
      var target = list.find(function (r) { return r.id === reviewId; });
      if (target) {
        target.reply = reply;
        write('reviews', list);
      }
      return list;
    },

    /* ---- 배송 ---- */
    getDeliveryMethod: function () {
      return read('deliveryMethod', 'visit');
    },
    setDeliveryMethod: function (method) {
      write('deliveryMethod', method);
    },
    getDeliveryInfo: function () {
      return read('deliveryInfo', { name: '', phone: '', address: '' });
    },
    setDeliveryInfo: function (info) {
      write('deliveryInfo', info);
    }
  };
})();
