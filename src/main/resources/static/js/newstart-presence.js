(function () {
  'use strict';

  /* NewStart 접속 회원 + 내부 쪽지 패널. 로그인 회원만 사용한다.
     - 첫 ping이 401이면(비로그인) 진입점 자체를 숨긴 채로 둔다.
     - 렌더는 textContent/createElement만 사용한다(innerHTML 금지 — 닉네임·쪽지 XSS 방지).
     - 수신자 userId는 JS 변수에만 두고 DOM/요청에 닉네임으로 식별하지 않는다. */

  var edgeBtn = document.getElementById('ns-presence-edge');
  var rail = document.getElementById('ns-presence-rail');
  var panel = document.getElementById('ns-presence-panel');
  if (!edgeBtn || !rail || !panel) return;

  var countEls = [
    document.getElementById('ns-presence-edge-count'),
    document.getElementById('ns-presence-rail-count'),
    document.getElementById('ns-presence-online-count')
  ];
  var unreadBadges = [
    document.getElementById('ns-presence-edge-unread'),
    document.getElementById('ns-presence-rail-unread')
  ];
  var list = document.getElementById('ns-presence-list');
  var inbox = document.getElementById('ns-presence-inbox');
  var moreBtn = document.getElementById('ns-presence-more');
  var unreadWrap = document.getElementById('ns-presence-unread');
  var unreadCount = document.getElementById('ns-presence-unread-count');
  var compose = document.getElementById('ns-presence-compose');
  var toName = document.getElementById('ns-presence-to-name');
  var cancelBtn = document.getElementById('ns-presence-cancel');
  var input = document.getElementById('ns-presence-input');
  var sendBtn = document.getElementById('ns-presence-send');
  var msg = document.getElementById('ns-presence-msg');
  var hideBox = document.getElementById('ns-presence-hide');
  var blockBox = document.getElementById('ns-presence-block');
  var closeBtn = document.getElementById('ns-presence-close');

  var targetUserId = null;
  var oldestId = null;
  var loggedIn = false;
  var timers = [];

  function json(method, url, body) {
    return fetch(url, {
      method: method,
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  function setMessage(text, isError) {
    msg.textContent = text || '';
    msg.hidden = !text;
  }

  function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function setCounts(online, unread) {
    countEls.forEach(function (el) { if (el) el.textContent = String(online); });
    if (unreadCount) unreadCount.textContent = String(unread);
    if (unreadWrap) unreadWrap.hidden = !(unread > 0);
    unreadBadges.forEach(function (el) {
      if (!el) return;
      el.textContent = String(unread);
      el.hidden = !(unread > 0);
    });
  }

  function renderOnline(members) {
    clear(list);
    if (!members.length) {
      var empty = document.createElement('li');
      empty.className = 'ns-empty';
      empty.textContent = '지금 접속 중인 회원이 없습니다.';
      list.appendChild(empty);
      return;
    }
    members.forEach(function (m) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'ns-presence-member';
      btn.setAttribute('data-user-id', String(m.userId));
      var dot = document.createElement('span');
      dot.className = 'ns-presence-dot';
      dot.setAttribute('aria-hidden', 'true');
      var name = document.createElement('span');
      name.textContent = m.displayNickname || '회원';
      btn.appendChild(dot);
      btn.appendChild(name);
      btn.addEventListener('click', function () {
        targetUserId = m.userId;
        toName.textContent = m.displayNickname || '회원';
        compose.hidden = false;
        setMessage('', false);
        input.value = '';
        input.focus();
      });
      li.appendChild(btn);
      list.appendChild(li);
    });
  }

  function renderInbox(items, append) {
    if (!append) {
      clear(inbox);
      oldestId = null;
    }
    items.forEach(function (it) {
      var li = document.createElement('li');
      li.className = 'ns-presence-dm' + (it.read ? '' : ' is-unread');
      var head = document.createElement('p');
      head.className = 'ns-presence-dm-head';
      var from = document.createElement('strong');
      from.textContent = it.senderNickname || '회원';
      var time = document.createElement('time');
      time.textContent = (it.createdAt || '').slice(0, 16).replace('T', ' ');
      head.appendChild(from);
      head.appendChild(time);
      var body = document.createElement('p');
      body.className = 'ns-presence-dm-body';
      body.textContent = it.message || '';
      li.appendChild(head);
      li.appendChild(body);
      var del = document.createElement('button');
      del.type = 'button';
      del.className = 'ns-presence-dm-del';
      del.textContent = '삭제';
      del.setAttribute('aria-label', '쪽지 삭제');
      del.addEventListener('click', function (e) {
        e.stopPropagation();
        deleteMessage(it.messageId, li);
      });
      li.appendChild(del);
      if (!it.read) {
        li.setAttribute('role', 'button');
        li.setAttribute('tabindex', '0');
        var mark = function () { markRead(it.messageId, li); };
        li.addEventListener('click', mark);
        li.addEventListener('keydown', function (e) {
          if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); mark(); }
        });
      }
      inbox.appendChild(li);
      oldestId = it.messageId;
    });
    moreBtn.hidden = !(items.length >= 20);
  }

  function refreshOnline() {
    return fetch('/api/newstart/presence/online', { credentials: 'same-origin' })
      .then(function (res) {
        if (res.status === 401) { setLoggedOut(); return null; }
        if (!res.ok) throw new Error('online failed');
        return res.json();
      })
      .then(function (members) {
        if (!members) return;
        renderOnline(members);
        refreshUnread(members.length);
      })
      .catch(function () { /* 다음 주기에 재시도 */ });
  }

  function refreshUnread(online) {
    return fetch('/api/newstart/dm/unread-count', { credentials: 'same-origin' })
      .then(function (res) {
        if (res.status === 401) { setLoggedOut(); return null; }
        if (!res.ok) throw new Error('unread failed');
        return res.json();
      })
      .then(function (data) {
        if (!data) return;
        setCounts(typeof online === 'number' ? online : currentOnline(), data.unreadCount || 0);
      })
      .catch(function () { /* 다음 주기에 재시도 */ });
  }

  function currentOnline() {
    var n = parseInt((countEls[0] && countEls[0].textContent) || '0', 10);
    return isNaN(n) ? 0 : n;
  }

  function refreshInbox(append) {
    var url = '/api/newstart/dm/inbox?limit=20' + (append && oldestId ? '&before=' + oldestId : '');
    return fetch(url, { credentials: 'same-origin' })
      .then(function (res) {
        if (res.status === 401) { setLoggedOut(); return null; }
        if (!res.ok) throw new Error('inbox failed');
        return res.json();
      })
      .then(function (items) {
        if (!items) return;
        renderInbox(items, !!append);
      })
      .catch(function () { /* 다음 주기에 재시도 */ });
  }

  function markRead(id, li) {
    json('POST', '/api/newstart/dm/' + id + '/read', {})
      .then(function (res) {
        if (res.status === 401) { setLoggedOut(); return; }
        if (!res.ok) throw new Error('read failed');
        li.classList.remove('is-unread');
        li.removeAttribute('role');
        refreshUnread();
      })
      .catch(function () { /* 다음 주기에 재시도 */ });
  }

  function deleteMessage(id, li) {
    json('DELETE', '/api/newstart/dm/' + id)
      .then(function (res) {
        if (res.status === 401) { setLoggedOut(); return; }
        if (!res.ok) throw new Error('delete failed');
        if (li.parentNode) li.parentNode.removeChild(li);
        refreshUnread();
      })
      .catch(function () { /* 다음 주기에 재시도 */ });
  }

  function setLoggedOut() {
    loggedIn = false;
    edgeBtn.hidden = true;
    rail.hidden = true;
    panel.hidden = true;
    timers.forEach(function (t) { clearInterval(t); });
    timers = [];
  }

  function openPanel() {
    panel.hidden = false;
    requestAnimationFrame(function () { panel.classList.add('is-open'); });
    refreshOnline();
    refreshInbox(false);
  }

  function closePanel() {
    panel.classList.remove('is-open');
    panel.hidden = true;
  }

  edgeBtn.addEventListener('click', openPanel);
  rail.addEventListener('click', openPanel);
  closeBtn.addEventListener('click', closePanel);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !panel.hidden) closePanel();
  });

  cancelBtn.addEventListener('click', function () {
    targetUserId = null;
    compose.hidden = true;
    input.value = '';
    setMessage('', false);
  });

  sendBtn.addEventListener('click', function () {
    if (!targetUserId) return;
    var text = (input.value || '').trim();
    if (!text) {
      setMessage('쪽지 내용을 입력해 주세요.', true);
      return;
    }
    sendBtn.disabled = true;
    json('POST', '/api/newstart/dm/send', { receiverUserId: targetUserId, message: text })
      .then(function (res) {
        return res.json().then(function (data) { return { ok: res.ok, status: res.status, data: data }; });
      })
      .then(function (result) {
        if (result.ok) {
          input.value = '';
          setMessage('쪽지를 보냈습니다.', false);
          refreshInbox(false);
          refreshUnread();
        } else if (result.status === 401) {
          setLoggedOut();
        } else {
          setMessage((result.data && result.data.errorMessage) || '보내지 못했습니다.', true);
        }
      })
      .catch(function () {
        setMessage('서버 연결에 실패했습니다.', true);
      })
      .finally(function () {
        sendBtn.disabled = false;
      });
  });

  moreBtn.addEventListener('click', function () { refreshInbox(true); });

  function applySettings() {
    json('PUT', '/api/newstart/presence/settings', {
      hideOnline: !!hideBox.checked,
      dmBlocked: !!blockBox.checked
    }).catch(function () { /* 다음 기회에 재시도 */ });
  }
  hideBox.addEventListener('change', applySettings);
  blockBox.addEventListener('change', applySettings);

  // 로그인 확인 겸 heartbeat. 401이면 진입점을 숨긴 채로 둔다.
  json('POST', '/api/newstart/presence/ping', {})
    .then(function (res) {
      if (res.status === 401) { setLoggedOut(); return null; }
      if (!res.ok) throw new Error('ping failed');
      return res.json();
    })
    .then(function (settings) {
      if (!settings) return;
      loggedIn = true;
      hideBox.checked = !!settings.hideOnline;
      blockBox.checked = !!settings.dmBlocked;
      edgeBtn.hidden = false;
      rail.hidden = false;
      refreshOnline();
      refreshInbox(false);
      timers.push(setInterval(function () {
        if (!document.hidden) refreshOnline();
      }, 60000));
      timers.push(setInterval(function () {
        if (!document.hidden) refreshUnread();
      }, 30000));
      timers.push(setInterval(function () {
        if (!document.hidden && loggedIn) {
          json('POST', '/api/newstart/presence/ping', {}).catch(function () {});
        }
      }, 60000));
    })
    .catch(function () { /* 비로그인 또는 일시 장애 — 진입점 숨김 유지 */ });
})();
