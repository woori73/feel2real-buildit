/*
 * 사이트 이벤트 수집 클라이언트(홈페이지 섹션 노출·버튼 클릭·서비스 이동·흐름 시작).
 *
 * - 대상은 마크업의 data-track-* 속성으로만 정한다. 서버가 허용 목록에 있는 (이벤트, 페이지, 대상)만 저장한다.
 * - 섹션 노출: IntersectionObserver로 "50% 이상 보이거나 화면 높이의 50% 이상 차지"하는 상태가 1초 이상 이어질 때
 *   페이지 방문당 1번만 보낸다(스쳐 지나간 영역·탭이 숨겨진 시간은 세지 않는다).
 * - 개인정보 없음: 입력값·URL·사용자 식별값을 보내지 않는다. viewId는 페이지를 열 때마다 만드는 무작위 토큰이다.
 * - 관리자 화면·iframe(미리보기)에서는 동작하지 않는다.
 */
(function (root) {
  'use strict';

  var ENDPOINT = '/api/analytics/events';
  var DWELL_MS = 1000;
  var FLUSH_MS = 2000;
  var MAX_BATCH = 20;
  var MAX_REPEAT = 5;

  function uuid(win) {
    var c = win.crypto;
    if (c && typeof c.randomUUID === 'function') return c.randomUUID();
    var b = new Uint8Array(16);
    if (c && c.getRandomValues) c.getRandomValues(b); else for (var i = 0; i < 16; i++) b[i] = Math.floor(Math.random() * 256);
    b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
    var h = Array.prototype.map.call(b, function (x) { return ('0' + x.toString(16)).slice(-2); }).join('');
    return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
  }

  /** 노출로 인정할 수 있는 가시 상태인가(비율 50% 이상, 또는 보이는 높이가 화면 높이의 50% 이상). */
  function qualifies(entry, viewportHeight) {
    if (!entry.isIntersecting) return false;
    if (entry.intersectionRatio >= 0.5) return true;
    return viewportHeight > 0 && entry.intersectionRect && entry.intersectionRect.height >= viewportHeight * 0.5;
  }

  /** env: { win, doc, send(url, json) } — 테스트에서 가짜를 주입한다. */
  function createTracker(env) {
    var win = env.win, doc = env.doc;
    var page = doc.body && doc.body.getAttribute('data-track-page');
    var tracker = { enabled: false, queue: [], sent: [] };
    var path = (win.location && win.location.pathname) || '';
    if (!page || path.indexOf('/admin') === 0 || win.top !== win) return tracker;
    tracker.enabled = true;

    var viewId = uuid(win);
    var repeat = {};
    var seenSection = {};
    var timers = {};
    var visibleNow = {};
    var flushTimer = null;

    function enqueue(type, target, seq) {
      tracker.queue.push({ type: type, target: target, seq: seq || 0 });
      if (tracker.queue.length >= MAX_BATCH) flush();
      else if (!flushTimer) flushTimer = win.setTimeout(flush, FLUSH_MS);
    }

    function flush() {
      if (flushTimer) { win.clearTimeout(flushTimer); flushTimer = null; }
      while (tracker.queue.length) {
        var events = tracker.queue.splice(0, MAX_BATCH);
        var body = JSON.stringify({ page: page, viewId: viewId, events: events });
        tracker.sent.push(body);
        env.send(ENDPOINT, body);
      }
    }

    function record(type, target) {
      var k = type + '|' + target;
      var n = repeat[k] || 0;
      if (n >= MAX_REPEAT) return;
      repeat[k] = n + 1;
      enqueue(type, target, n);
    }

    function armSection(id) {
      if (seenSection[id] || timers[id] || doc.hidden || !visibleNow[id]) return;
      timers[id] = win.setTimeout(function () {
        timers[id] = null;
        if (seenSection[id] || !visibleNow[id] || doc.hidden) return;
        seenSection[id] = true;
        if (tracker.observer) tracker.observer.unobserve(tracker.elements[id]);
        enqueue('SECTION_VIEW', id, 0);
      }, DWELL_MS);
    }

    function disarmSection(id) {
      if (timers[id]) { win.clearTimeout(timers[id]); timers[id] = null; }
    }

    tracker.elements = {};
    var idByElement = typeof Map === 'function' ? new Map() : null;
    tracker.onIntersect = function (entries) {
      entries.forEach(function (entry) {
        var id = idByElement && idByElement.has(entry.target) ? idByElement.get(entry.target) : entry.target.getAttribute('data-track-section');
        if (!id || seenSection[id]) return;
        visibleNow[id] = qualifies(entry, win.innerHeight || 0);
        if (visibleNow[id]) armSection(id); else disarmSection(id);
      });
    };

    if (env.IntersectionObserver) {
      tracker.observer = new env.IntersectionObserver(tracker.onIntersect, { threshold: [0, 0.25, 0.5, 0.75, 1] });
      Array.prototype.forEach.call(doc.querySelectorAll('[data-track-section]'), function (el) {
        var id = el.getAttribute('data-track-section');
        // display:contents 섹션은 박스가 없어 IntersectionObserver가 동작하지 않는다 → 지정한 하위 요소를 관찰한다.
        var sel = el.getAttribute('data-track-observe');
        var target = (sel && el.querySelector && el.querySelector(sel)) || el;
        tracker.elements[id] = target;
        if (idByElement) idByElement.set(target, id);
        tracker.observer.observe(target);
      });
    }

    doc.addEventListener('visibilitychange', function () {
      Object.keys(tracker.elements).forEach(function (id) { if (doc.hidden) disarmSection(id); else armSection(id); });
      if (doc.hidden) flush();
    });
    win.addEventListener('pagehide', flush);

    doc.addEventListener('click', function (e) {
      var el = e.target && e.target.closest && e.target.closest('[data-track-click],[data-track-move],[data-track-flow]');
      if (!el) return;
      var click = el.getAttribute('data-track-click');
      var move = el.getAttribute('data-track-move');
      var flow = el.getAttribute('data-track-flow');
      if (click) record('CLICK', click);
      if (move) record('SERVICE_MOVE', move);
      if (flow) record('FLOW_START', flow);
      if (move) flush(); // 페이지를 떠나기 전에 보낸다.
    }, true);

    tracker.flow = function (target) { record('FLOW_START', target); flush(); };
    tracker.flush = flush;
    return tracker;
  }

  function browserSend(url, json) {
    try {
      if (navigator.sendBeacon && navigator.sendBeacon(url, new Blob([json], { type: 'application/json' }))) return;
    } catch (e) { /* fall through */ }
    try { fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: json, keepalive: true, credentials: 'same-origin' }); } catch (e) { /* 통계 실패는 무시 */ }
  }

  var api = { createTracker: createTracker, qualifies: qualifies, uuid: uuid };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root && root.document) {
    root.F2RSiteEvents = createTracker({ win: root, doc: root.document, send: browserSend, IntersectionObserver: root.IntersectionObserver });
  }
})(typeof window !== 'undefined' ? window : null);
