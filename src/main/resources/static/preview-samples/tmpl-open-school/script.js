/* ============================================================
   tmpl-open-school — 상호작용 스크립트 (바닐라 JS)
   100% 오리지널 코드 (MIT)
   ============================================================ */
(function () {
  "use strict";

  /* ---------- 모바일 메뉴 ---------- */
  var menuBtn = document.getElementById("biMenuBtn");
  var mobileNav = document.getElementById("biMobileNav");

  if (menuBtn && mobileNav) {
    menuBtn.addEventListener("click", function () {
      var open = menuBtn.getAttribute("aria-expanded") === "true";
      menuBtn.setAttribute("aria-expanded", String(!open));
      menuBtn.setAttribute("aria-label", open ? "메뉴 열기" : "메뉴 닫기");
      if (open) {
        mobileNav.setAttribute("hidden", "");
      } else {
        mobileNav.removeAttribute("hidden");
      }
    });

    // 링크 클릭 시 메뉴 닫기
    mobileNav.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        menuBtn.setAttribute("aria-expanded", "false");
        menuBtn.setAttribute("aria-label", "메뉴 열기");
        mobileNav.setAttribute("hidden", "");
      });
    });
  }

  /* ---------- 커리큘럼 탭 ---------- */
  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-bi-tab]"));

  function activateTab(tab) {
    tabs.forEach(function (t) {
      var active = t === tab;
      t.classList.toggle("bi-is-active", active);
      t.setAttribute("aria-selected", String(active));
      t.tabIndex = active ? 0 : -1;
      var panel = document.getElementById(t.getAttribute("aria-controls"));
      if (panel) {
        panel.classList.toggle("bi-is-active", active);
        if (active) {
          panel.removeAttribute("hidden");
        } else {
          panel.setAttribute("hidden", "");
        }
      }
    });
  }

  tabs.forEach(function (tab, i) {
    tab.addEventListener("click", function () { activateTab(tab); });
    tab.addEventListener("keydown", function (e) {
      var next = null;
      if (e.key === "ArrowRight") next = tabs[(i + 1) % tabs.length];
      if (e.key === "ArrowLeft") next = tabs[(i - 1 + tabs.length) % tabs.length];
      if (next) {
        e.preventDefault();
        activateTab(next);
        next.focus();
      }
    });
  });

  /* ---------- 스크롤 리빌 ---------- */
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (!reduceMotion && "IntersectionObserver" in window) {
    var targets = document.querySelectorAll(
      ".bi-notice-list, .bi-cards, .bi-form, .bi-section-head"
    );
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("bi-reveal", "bi-is-visible");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
    targets.forEach(function (el) {
      el.classList.add("bi-reveal");
      io.observe(el);
    });
  }

  /* ---------- 히어로 숫자 카운트업 ---------- */
  var counters = document.querySelectorAll("[data-bi-count]");

  function animateCount(el) {
    var target = parseInt(el.getAttribute("data-bi-count"), 10);
    if (reduceMotion || !target) {
      el.textContent = target.toLocaleString("ko-KR");
      return;
    }
    var start = null;
    var duration = 1400;
    function step(ts) {
      if (!start) start = ts;
      var p = Math.min((ts - start) / duration, 1);
      var eased = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(target * eased).toLocaleString("ko-KR");
      if (p < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  if ("IntersectionObserver" in window) {
    var cio = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          animateCount(entry.target);
          cio.unobserve(entry.target);
        }
      });
    }, { threshold: 0.4 });
    counters.forEach(function (el) { cio.observe(el); });
  } else {
    counters.forEach(animateCount);
  }

  /* ---------- 상담 신청 폼 ---------- */
  var form = document.getElementById("biConsultForm");
  var errBox = document.getElementById("biFormError");
  var doneBox = document.getElementById("biFormDone");

  function showError(msg) {
    errBox.textContent = msg;
    errBox.removeAttribute("hidden");
    doneBox.setAttribute("hidden", "");
  }

  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var name = document.getElementById("biName").value.trim();
      var phone = document.getElementById("biPhone").value.trim();
      var course = document.getElementById("biCourse").value;

      if (name.length < 2) {
        showError("학생 이름을 2자 이상 입력해 주세요.");
        document.getElementById("biName").focus();
        return;
      }
      var digits = phone.replace(/\D/g, "");
      if (!/^01[0-9]\d{7,8}$/.test(digits)) {
        showError("연락처를 올바르게 입력해 주세요. (예: 010-0000-0000)");
        document.getElementById("biPhone").focus();
        return;
      }
      if (!course) {
        showError("관심 과정을 선택해 주세요.");
        document.getElementById("biCourse").focus();
        return;
      }

      // 데모: 실제 전송 없이 접수 완료 표시
      errBox.setAttribute("hidden", "");
      doneBox.removeAttribute("hidden");
      form.querySelector(".bi-form-submit").setAttribute("disabled", "");
    });

    // 전화번호 자동 하이픈
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
})();
