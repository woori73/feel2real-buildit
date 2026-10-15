/* 장바구니 화면. */
(function () {
  'use strict';

  var body = document.querySelector('[data-cart-body]');
  var totalEl = document.querySelector('[data-cart-total]');
  var noteEl = document.querySelector('[data-cart-shipping-note]');

  function syncDeliveryRadios() {
    var method = window.LmsState.getDeliveryMethod();
    document.querySelectorAll('input[name="cartDelivery"]').forEach(function (r) {
      r.checked = (r.value === method);
    });
  }

  function render() {
    return window.LmsData.listCourses({}).then(function (courses) {
      var cart = window.LmsState.getCart();
      body.innerHTML = '';

      cart.forEach(function (id) {
        var course = courses.find(function (c) { return c.id === id; });
        if (!course) return;
        var tr = document.createElement('tr');
        var nameTd = document.createElement('td');
        nameTd.textContent = window.Lms.courseName(course);
        var priceTd = document.createElement('td');
        priceTd.textContent = window.Lms.fmt(course.price);
        var actionTd = document.createElement('td');
        var removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'btn-flat';
        removeBtn.textContent = '삭제';
        removeBtn.addEventListener('click', function () {
          window.LmsState.removeFromCart(id);
          render();
        });
        actionTd.appendChild(removeBtn);
        tr.appendChild(nameTd);
        tr.appendChild(priceTd);
        tr.appendChild(actionTd);
        body.appendChild(tr);
      });

      syncDeliveryRadios();
      var shipping = window.Lms.shippingFee();
      noteEl.textContent = shipping > 0 ? ('배송비 ' + window.Lms.fmt(shipping) + ' 포함') : '';
      totalEl.textContent = window.Lms.fmt(window.Lms.cartSubtotal(courses, cart) + shipping);
      window.Lms.refreshCartCount();
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('input[name="cartDelivery"]').forEach(function (r) {
      r.addEventListener('change', function (e) {
        window.LmsState.setDeliveryMethod(e.target.value);
        render();
      });
    });

    document.querySelector('[data-checkout-btn]').addEventListener('click', function () {
      if (window.LmsState.getCart().length === 0) {
        alert('장바구니가 비어 있습니다.');
        return;
      }
      window.Lms.go('/checkout');
    });

    render();
  });
})();
