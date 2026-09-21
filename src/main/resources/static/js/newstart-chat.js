(function () {
  'use strict';

  var PAGE = {
    userId: null,
    socket: null,
    socketWanted: false,
    oldestId: null,
    hasMore: false,
    loadingHistory: false,
    busy: false,
    lastEventId: null,
    reconnectTimer: null,
    reconnectDelay: 1000
  };

  function init() {
    PAGE.list = document.getElementById('ns-chat-list');
    PAGE.more = document.getElementById('ns-chat-more');
    PAGE.empty = document.getElementById('ns-chat-empty');
    PAGE.form = document.getElementById('ns-chat-form');
    PAGE.input = document.getElementById('ns-chat-input');
    PAGE.send = document.getElementById('ns-chat-send');
    PAGE.loginBox = document.getElementById('ns-chat-login');
    PAGE.loginBtn = document.getElementById('ns-chat-login-btn');
    PAGE.loginAlert = document.getElementById('ns-chat-login-alert');
    PAGE.status = document.getElementById('ns-chat-status');
    if (!PAGE.list) return;

    PAGE.more.addEventListener('click', function () { loadOlder(); });
    PAGE.form.addEventListener('submit', function (e) {
      e.preventDefault();
      sendMessage();
    });
    autoGrow();
    PAGE.input.addEventListener('input', autoGrow);
    PAGE.input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });

    loadCurrentUser();
  }

  function autoGrow() {
    PAGE.input.style.height = 'auto';
    PAGE.input.style.height = Math.min(PAGE.input.scrollHeight, 96) + 'px';
  }

  /* ── 로그인 상태: /api/auth/me (account_menu.js와 동일한 응답 계약) ── */
  function loadCurrentUser() {
    fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) return null;
        return res.json();
      })
      .then(function (user) {
        if (user && user.userId) {
          PAGE.userId = user.userId;
          enterRoom();
        } else {
          showLoginRequired();
        }
      })
      .catch(function () { showLoginRequired(); });
  }

  /* ── 로그인 실패/취소 안내 — callback의 ?login= 마커를 표시 후 URL에서 정리한다. 서버 오류
     세부(credential/token 등)는 노출하지 않는다. 2026-09-21 cleanup: 네이버 로그인 V1/V2/공식
     OAuth2 구현을 전부 제거해 지금은 이 마커를 만드는 backend가 없다 — 다음 로그인 구현이 자기
     코드를 여기에 추가할 때까지 비워 둔다(메커니즘 자체는 범용이라 재사용). ── */
  var LOGIN_ALERTS = {};

  function showLoginAlert() {
    if (!PAGE.loginAlert) return;
    var code = null;
    try {
      code = new URLSearchParams(window.location.search).get('login');
    } catch (_) {
      return;
    }
    if (!code || !LOGIN_ALERTS[code]) return;
    PAGE.loginAlert.textContent = LOGIN_ALERTS[code];
    PAGE.loginAlert.hidden = false;
    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }

  function showLoginRequired() {
    PAGE.loginBox.hidden = false;
    PAGE.form.hidden = true;
    PAGE.more.hidden = true;
    PAGE.empty.hidden = true;
    showLoginAlert();
    // 2026-09-21 cleanup: #ns-chat-login-btn은 현재 disabled 버튼이다(네이버 로그인 V1/V2/공식
    // OAuth2 구현 전부 제거, 다음 세션에서 재구현) — 클릭해도 아무 일도 일어나지 않는다.
    document.addEventListener('f2r:login', function () {
      loadCurrentUser();
    });
  }

  function enterRoom() {
    PAGE.loginBox.hidden = true;
    PAGE.form.hidden = false;
    setStatus('');
    loadHistory(null);
    connect();
  }

  /* ── 히스토리: 최근 30건 → 위로 스크롤 시 이전 30건 ── */
  function loadHistory(before) {
    if (PAGE.loadingHistory) return;
    PAGE.loadingHistory = true;
    var url = '/api/newstart/chat/messages?limit=30' + (before ? '&before=' + before : '');
    fetch(url, { credentials: 'same-origin' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        var messages = (data && data.messages) || [];
        PAGE.hasMore = !!(data && data.hasMore);
        if (before) {
          var keepScroll = PAGE.list.scrollHeight - PAGE.list.scrollTop;
          for (var i = messages.length - 1; i >= 0; i--) {
            renderMessage(messages[i], true);
          }
          PAGE.list.scrollTop = PAGE.list.scrollHeight - keepScroll;
        } else {
          messages.forEach(function (m) { renderMessage(m, false); });
          PAGE.list.scrollTop = PAGE.list.scrollHeight;
          if (!messages.length) PAGE.empty.hidden = false;
        }
        PAGE.more.hidden = !PAGE.hasMore;
        if (!before) {
          var last = messages.length ? messages[messages.length - 1].id : null;
          if (last != null && (PAGE.lastEventId == null || last > PAGE.lastEventId)) PAGE.lastEventId = last;
        }
      })
      .catch(function () {
        setStatus('대화를 불러오지 못했습니다. 새로고침해 주세요.');
      })
      .then(function () { PAGE.loadingHistory = false; });
  }

  function loadOlder() {
    if (PAGE.oldestId == null) return;
    loadHistory(PAGE.oldestId);
  }

  /* ── 메시지 렌더 (XSS 방지: textContent만 사용, innerHTML 금지) ── */
  function renderMessage(view, prepend) {
    if (!view || view.id == null) return;
    if (document.getElementById('ns-msg-' + view.id)) return; // 중복 방지
    var item = document.createElement('div');
    item.className = 'ns-msg' + (PAGE.userId != null && view.senderUserId === PAGE.userId ? ' ns-msg-mine' : '');
    item.id = 'ns-msg-' + view.id;

    var meta = document.createElement('div');
    meta.className = 'ns-msg-meta';
    var who = document.createElement('strong');
    who.className = 'ns-msg-name';
    who.textContent = view.deleted ? '' : (view.displayName || '회원');
    var time = document.createElement('span');
    time.className = 'ns-msg-time';
    time.textContent = shortTime(view.createdAt);
    meta.appendChild(who);
    meta.appendChild(time);

    var body = document.createElement('div');
    body.className = 'ns-msg-body';
    if (view.deleted) {
      item.classList.add('ns-msg-deleted');
      body.textContent = '삭제된 메시지입니다.';
    } else {
      body.textContent = view.message;
      if (!view.deleted) {
        var mine = PAGE.userId != null && view.senderUserId === PAGE.userId;
        if (mine) {
          var del = document.createElement('button');
          del.type = 'button';
          del.className = 'ns-msg-del';
          del.textContent = '삭제';
          del.setAttribute('aria-label', '내 메시지 삭제');
          del.addEventListener('click', function () { deleteMessage(view.id); });
          meta.appendChild(del);
        }
      }
    }

    item.appendChild(meta);
    item.appendChild(body);
    PAGE.empty.hidden = true;

    var more = PAGE.more;
    if (prepend && more && more.parentNode === PAGE.list) {
      PAGE.list.insertBefore(item, more.nextSibling);
    } else {
      PAGE.list.appendChild(item);
    }
    if (PAGE.oldestId == null || view.id < PAGE.oldestId) PAGE.oldestId = view.id;
  }

  function shortTime(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    var h = d.getHours();
    var ampm = h < 12 ? '오전' : '오후';
    var h12 = h % 12; if (h12 === 0) h12 = 12;
    return ampm + ' ' + h12 + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  /* ── 전송: WebSocket. 서버는 principal로 작성자를 결정한다(클라이언트 신원 전송 없음) ── */
  function sendMessage() {
    var message = (PAGE.input.value || '').trim();
    if (!message) return;
    if (PAGE.busy) return;
    if (!PAGE.socket || PAGE.socket.readyState !== WebSocket.OPEN) {
      setStatus('연결이 끊어졌습니다. 다시 연결 중입니다.');
      connect();
      return;
    }
    PAGE.busy = true;
    PAGE.send.disabled = true;
    PAGE.socket.send(JSON.stringify({ message: message }));
    PAGE.input.value = '';
    autoGrow();
    window.setTimeout(function () {
      PAGE.busy = false;
      PAGE.send.disabled = false;
    }, 400);
  }

  function deleteMessage(id) {
    fetch('/api/newstart/chat/messages/' + id, {
      method: 'DELETE',
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (res.ok) return;
        return res.json().catch(function () { return {}; }).then(function (data) {
          setStatus((data && data.errorMessage) || '삭제하지 못했습니다.');
        });
      })
      .catch(function () { setStatus('삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.'); });
  }

  /* ── WebSocket 연결 + 지수 백오프 재접속 ── */
  function connect() {
    if (PAGE.socket && (PAGE.socket.readyState === WebSocket.OPEN || PAGE.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    PAGE.socketWanted = true;
    var proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    var socket;
    try {
      socket = new WebSocket(proto + location.host + '/ws/newstart-chat');
    } catch (e) {
      scheduleReconnect();
      return;
    }
    PAGE.socket = socket;

    socket.onopen = function () {
      PAGE.reconnectDelay = 1000;
      setStatus('');
      syncAfter();
    };
    socket.onmessage = function (event) {
      var data;
      try { data = JSON.parse(event.data); } catch (e) { return; }
      if (data.type === 'new') {
        var atBottom = PAGE.list.scrollHeight - PAGE.list.scrollTop - PAGE.list.clientHeight < 60;
        renderMessage(data, false);
        if (data.id != null && (PAGE.lastEventId == null || data.id > PAGE.lastEventId)) {
          PAGE.lastEventId = data.id;
        }
        if (atBottom) PAGE.list.scrollTop = PAGE.list.scrollHeight;
      } else if (data.type === 'deleted') {
        markDeleted(data.id);
      } else if (data.type === 'error') {
        setStatus(data.message || '전송하지 못했습니다.');
      }
    };
    socket.onclose = function () {
      PAGE.socket = null;
      if (PAGE.socketWanted) scheduleReconnect();
    };
    socket.onerror = function () {
      try { socket.close(); } catch (e) { /* no-op */ }
    };
  }

  function scheduleReconnect() {
    if (PAGE.reconnectTimer) return;
    setStatus('연결이 끊어졌습니다. 다시 연결 중…');
    PAGE.reconnectTimer = window.setTimeout(function () {
      PAGE.reconnectTimer = null;
      connect();
    }, PAGE.reconnectDelay);
    PAGE.reconnectDelay = Math.min(PAGE.reconnectDelay * 2, 15000);
  }

  /** 재접속 직후: 마지막 메시지 id 이후분을 REST로 이어받아 구간을 메운다. */
  function syncAfter() {
    if (PAGE.lastEventId == null) return;
    fetch('/api/newstart/chat/after?after=' + PAGE.lastEventId, { credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : []; })
      .then(function (messages) {
        (messages || []).forEach(function (m) {
          renderMessage(m, false);
          if (m.id != null && (PAGE.lastEventId == null || m.id > PAGE.lastEventId)) {
            PAGE.lastEventId = m.id;
          }
        });
        PAGE.list.scrollTop = PAGE.list.scrollHeight;
      })
      .catch(function () { /* 다음 reconnect에서 재시도 */ });
  }

  function markDeleted(id) {
    var node = document.getElementById('ns-msg-' + id);
    if (!node) return;
    node.classList.add('ns-msg-deleted');
    node.classList.remove('ns-msg-mine');
    var body = node.querySelector('.ns-msg-body');
    if (body) body.textContent = '삭제된 메시지입니다.';
    var del = node.querySelector('.ns-msg-del');
    if (del) del.remove();
    var name = node.querySelector('.ns-msg-name');
    if (name) name.textContent = '';
  }

  function setStatus(text) {
    if (PAGE.status) PAGE.status.textContent = text || '';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
