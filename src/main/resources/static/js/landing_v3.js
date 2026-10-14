/**
 * BuildIt 랜딩 v3 보조 스크립트 (탭·등장 연출·입력창 예시 타자·도입 문의 가드).
 *
 * 인증·역할 분기(시작하기 게이트)는 landing_buildit.js가 전담한다. 이 파일은 그 로직을 건드리지 않는다.
 * JS가 실패해도 내용이 가려지지 않도록, 숨김 상태는 v3-js 클래스를 붙인 뒤에만 적용된다.
 */
(function (root) {
  'use strict';

  var EXAMPLES = [
    '초등·중등 영어학원, 밝고 세련되게',
    '입시·내신 전문 학원, 신뢰감 있게',
    '영어회화 학원, 활기차게'
  ];

  /** 탭 키보드 이동: ←/→는 순환, Home/End는 처음/끝. 처리하지 않는 키는 -1. */
  function nextTabIndex(key, index, total) {
    if (!total) return -1;
    if (key === 'ArrowRight') return (index + 1) % total;
    if (key === 'ArrowLeft') return (index - 1 + total) % total;
    if (key === 'Home') return 0;
    if (key === 'End') return total - 1;
    return -1;
  }

  /** 입력창 placeholder 타자 효과의 다음 프레임. 순수 함수라 시간 없이 검증할 수 있다. */
  function typedStep(state, texts) {
    var s = texts[state.i];
    var del = state.del;
    var i = state.i;
    var j = state.j + (del ? -1 : 1);
    var delay = del ? 25 : 55;
    if (!del && j >= s.length) {
      j = s.length;
      del = true;
      delay = 1600;
    } else if (del && j <= 0) {
      j = 0;
      del = false;
      i = (i + 1) % texts.length;
      delay = 400;
    }
    return { i: i, j: j, del: del, text: s.slice(0, j), delay: delay };
  }

  /** 도입 문의는 공용 문의 모달이 있을 때만 활성화한다. 없으면 임의 경로를 만들지 않고 비활성으로 표시한다. */
  function inquiryAvailable(doc, win) {
    return typeof win.openContactModal === 'function' && !!doc.getElementById('contactModal');
  }

  function guardInquiry(doc, win) {
    var buttons = doc.querySelectorAll('[data-v3-inquiry]');
    if (inquiryAvailable(doc, win)) return 0;
    Array.prototype.forEach.call(buttons, function (btn) {
      btn.setAttribute('aria-disabled', 'true');
      btn.disabled = true;
      btn.textContent = '준비 중';
    });
    return buttons.length;
  }

  function initTabs(doc) {
    var tabs = Array.prototype.slice.call(doc.querySelectorAll('.v3-tab'));
    if (!tabs.length) return;
    var panes = Array.prototype.slice.call(doc.querySelectorAll('.v3-pane'));
    function select(tab, focus) {
      tabs.forEach(function (t) {
        var on = t === tab;
        t.setAttribute('aria-selected', String(on));
        t.tabIndex = on ? 0 : -1;
      });
      panes.forEach(function (p) {
        var on = p.id === tab.getAttribute('aria-controls');
        p.classList.toggle('on', on);
        var video = p.querySelector && p.querySelector('video');
        if (video) {
          if (on) {
            var played = video.play && video.play();
            if (played && typeof played.catch === 'function') played.catch(function () { /* 자동재생 차단은 무시 */ });
          } else if (video.pause) {
            video.pause();
          }
        }
      });
      if (focus) tab.focus();
    }
    tabs.forEach(function (tab, index) {
      tab.addEventListener('click', function () { select(tab, false); });
      tab.addEventListener('keydown', function (event) {
        var next = nextTabIndex(event.key, index, tabs.length);
        if (next < 0) return;
        event.preventDefault();
        select(tabs[next], true);
      });
    });
  }

  function initReveal(doc, win) {
    var items = Array.prototype.slice.call(doc.querySelectorAll('.v3-rv'));
    if (!items.length || typeof win.IntersectionObserver !== 'function') return;
    doc.documentElement.classList.add('v3-js');
    var io = new win.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('v3-in');
        io.unobserve(entry.target);
      });
    }, { threshold: 0.1 });
    items.forEach(function (el) { io.observe(el); });
  }

  function initTyped(doc, win) {
    var input = doc.querySelector('[data-buildit-prompt]');
    if (!input) return;
    var reduced = win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) {
      input.placeholder = '예) ' + EXAMPLES[0];
      return;
    }
    var state = { i: 0, j: 0, del: false };
    (function tick() {
      state = typedStep(state, EXAMPLES);
      input.placeholder = '예) ' + state.text;
      win.setTimeout(tick, state.delay);
    })();
  }

  function init(doc, win) {
    initTabs(doc);
    initReveal(doc, win);
    initTyped(doc, win);
    guardInquiry(doc, win);
  }

  if (typeof document !== 'undefined' && typeof window !== 'undefined') init(document, window);
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { nextTabIndex: nextTabIndex, typedStep: typedStep, inquiryAvailable: inquiryAvailable, guardInquiry: guardInquiry, initTabs: initTabs, initReveal: initReveal, EXAMPLES: EXAMPLES };
  }
})(typeof window !== 'undefined' ? window : globalThis);
