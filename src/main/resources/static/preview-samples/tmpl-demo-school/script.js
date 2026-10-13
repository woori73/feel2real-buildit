/* ============================================================
   tmpl-demo-school — 상호작용 스크립트 (바닐라 JS)
   100% 오리지널 코드 (MIT) · 외부 의존성 없음
   ============================================================ */
(function () {
  "use strict";

  /* ---------- 상담 신청 폼 검증 ---------- */
  var form = document.getElementById("biForm");
  var errBox = document.getElementById("biFormError");
  var doneBox = document.getElementById("biFormDone");

  function showError(msg, focusEl) {
    errBox.textContent = msg;
    errBox.removeAttribute("hidden");
    doneBox.setAttribute("hidden", "");
    if (focusEl) focusEl.focus();
  }

  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var nameEl = document.getElementById("biName");
      var phoneEl = document.getElementById("biPhone");
      var name = nameEl.value.trim();
      var phone = phoneEl.value.trim();

      if (name.length < 2) {
        showError("학생 이름을 2자 이상 입력해 주세요.", nameEl);
        return;
      }
      var digits = phone.replace(/\D/g, "");
      if (!/^01[0-9]\d{7,8}$/.test(digits)) {
        showError("연락처를 올바르게 입력해 주세요. (예: 010-0000-0000)", phoneEl);
        return;
      }

      // 데모: 실제 전송 없이 접수 완료 표시
      errBox.setAttribute("hidden", "");
      doneBox.removeAttribute("hidden");
      form.querySelector('button[type="submit"]').setAttribute("disabled", "");
    });

    /* ---------- 전화번호 자동 하이픈 ---------- */
    var phoneInput = document.getElementById("biPhone");
    phoneInput.addEventListener("input", function () {
      var d = phoneInput.value.replace(/\D/g, "").slice(0, 11);
      if (d.length > 7) {
        phoneInput.value = d.replace(/(\d{3})(\d{4})(\d{0,4})/, "$1-$2-$3").replace(/-$/, "");
      } else if (d.length > 3) {
        phoneInput.value = d.replace(/(\d{3})(\d{0,4})/, "$1-$2");
      } else {
        phoneInput.value = d;
      }
    });
  }

  /* ---------- 빠른 메뉴 터치 피드백 (저사양 기기 대응) ---------- */
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!reduceMotion) {
    document.querySelectorAll(".bi-quick-item").forEach(function (item) {
      item.addEventListener("touchstart", function () {
        item.style.transform = "scale(.96)";
      }, { passive: true });
      item.addEventListener("touchend", function () {
        item.style.transform = "";
      }, { passive: true });
    });
  }
})();
