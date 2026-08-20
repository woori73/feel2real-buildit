/*
 * 학원 LMS 공통 셸 스크립트 — 8개 페이지가 모두 로드한다.
 *
 * 담당: 페이지 컨텍스트(handle/base/인증상태) 노출, 공통 포맷 헬퍼, 헤더 장바구니 배지 hydration.
 * 목업의 showView()/updateAuthUI()는 이식하지 않는다 — 화면 전환은 서버가 URL로 강제하고,
 * 로그인 UI는 레포 공통 _account_menu(account_menu.js)가 담당한다.
 */
(function () {
  'use strict';

  var layout = document.querySelector('.lms-layout');

  window.Lms = {
    handle: layout ? layout.dataset.handle : '',
    /** /lms/{handle} — 페이지 이동 링크의 base. */
    base: layout ? layout.dataset.lmsBase : '',
    /** 서버가 내려준 인증 상태. 목업의 클라이언트 isLoggedIn 토글을 대체한다. */
    isAuthenticated: layout ? layout.dataset.authenticated === 'true' : false,
    /** 강좌상세/강의보기가 URL에서 받은 courseId. 해당 페이지 외에는 없다. */
    courseId: layout && layout.dataset.courseId ? Number(layout.dataset.courseId) : null,

    SHIPPING_FEE: 3000,

    fmt: function (n) {
      return Number(n || 0).toLocaleString('ko-KR') + '원';
    },
    parsePrice: function (s) {
      return parseInt(String(s).replace(/[^0-9]/g, ''), 10) || 0;
    },
    courseName: function (c) {
      return c ? (c.school + ' ' + c.grade + ' ' + c.title) : '';
    },
    go: function (path) {
      window.location.href = this.base + path;
    },

    /** 장바구니 소계 — 현재 장바구니에 담긴 강좌들의 가격 합. */
    cartSubtotal: function (courses, cart) {
      return cart.reduce(function (sum, id) {
        var c = courses.find(function (x) { return x.id === id; });
        return sum + (c ? c.price : 0);
      }, 0);
    },
    shippingFee: function () {
      return window.LmsState.getDeliveryMethod() === 'delivery' ? this.SHIPPING_FEE : 0;
    },

    /**
     * 헤더 배지 갱신. 서버는 장바구니 개수를 알 수 없으므로 hydration 전까지 hidden으로 두고,
     * 여기서 값을 채운 뒤 노출한다(0이 깜빡이는 것을 막는다).
     */
    refreshCartCount: function () {
      var badge = document.querySelector('[data-cart-count]');
      if (!badge) return;
      badge.textContent = window.LmsState.getCart().length;
      badge.hidden = false;
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    window.Lms.refreshCartCount();
  });
})();
