/* 결제 화면. */
(function () {
  'use strict';

  var deliveryFields = document.querySelector('[data-delivery-fields]');
  var nameInput = document.querySelector('[data-delivery-name]');
  var phoneInput = document.querySelector('[data-delivery-phone]');
  var addressInput = document.querySelector('[data-delivery-address]');

  function isDelivery() {
    return window.LmsState.getDeliveryMethod() === 'delivery';
  }

  function render() {
    return window.LmsData.listCourses({}).then(function (courses) {
      var cart = window.LmsState.getCart();
      var names = cart
        .map(function (id) {
          return window.Lms.courseName(courses.find(function (c) { return c.id === id; }));
        })
        .filter(Boolean);
      document.querySelector('[data-pay-order-list]').textContent =
        names.join(', ') || '선택된 강좌 없음';

      document.querySelectorAll('input[name="payDelivery"]').forEach(function (r) {
        r.checked = (r.value === window.LmsState.getDeliveryMethod());
      });
      deliveryFields.hidden = !isDelivery();

      var info = window.LmsState.getDeliveryInfo();
      nameInput.value = info.name;
      phoneInput.value = info.phone;
      addressInput.value = info.address;

      document.querySelector('[data-pay-amount]').textContent =
        window.Lms.fmt(window.Lms.cartSubtotal(courses, cart) + window.Lms.shippingFee());
      window.Lms.refreshCartCount();
    });
  }

  function saveDeliveryInfo() {
    window.LmsState.setDeliveryInfo({
      name: nameInput.value.trim(),
      phone: phoneInput.value.trim(),
      address: addressInput.value.trim()
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('input[name="payDelivery"]').forEach(function (r) {
      r.addEventListener('change', function (e) {
        window.LmsState.setDeliveryMethod(e.target.value);
        render();
      });
    });
    [nameInput, phoneInput, addressInput].forEach(function (el) {
      el.addEventListener('blur', saveDeliveryInfo);
    });

    document.querySelector('[data-pay-btn]').addEventListener('click', function () {
      var name = document.querySelector('[data-pay-name]').value.trim();
      var phone = document.querySelector('[data-pay-phone]').value.trim();
      if (!name || !phone) {
        alert('수강생 이름과 연락처를 입력해 주세요.');
        return;
      }
      var cart = window.LmsState.getCart();
      if (cart.length === 0) {
        alert('결제할 강좌가 없습니다.');
        return;
      }
      saveDeliveryInfo();
      var info = window.LmsState.getDeliveryInfo();
      if (isDelivery() && (!info.name || !info.phone || !info.address)) {
        alert('택배 배송 정보를 모두 입력해 주세요.');
        return;
      }

      cart.forEach(function (id) { window.LmsState.addEnrollment(id); });
      window.LmsState.clearCart();
      alert('결제가 완료되었습니다.');
      window.Lms.go('/my');
    });

    render();
  });
})();
