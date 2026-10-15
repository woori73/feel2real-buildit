/**
 * 감사회원 명단 관리 — /admin/newstart/thanks-roster
 * API: /api/admin/newstart/thanks-roster (ROLE_ADMIN)
 */
(function () {
  'use strict';

  var API = '/api/admin/newstart/thanks-roster';
  var body = document.getElementById('tr-body');
  var msg = document.getElementById('tr-msg');
  var nicknameEl = document.getElementById('tr-nickname');
  var prefixEl = document.getElementById('tr-prefix');
  var dateEl = document.getElementById('tr-date');
  var addBtn = document.getElementById('tr-add-btn');

  // 오늘 날짜를 기본값으로
  if (dateEl && !dateEl.value) {
    dateEl.value = new Date().toISOString().slice(0, 10);
  }

  function setMsg(text, ok) {
    msg.textContent = text;
    msg.className = ok ? 'ok' : 'err';
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function loadList() {
    body.innerHTML = '<tr><td colspan="7">불러오는 중…</td></tr>';
    fetch(API + '?limit=200', { credentials: 'same-origin' })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (list) {
        if (!list || !list.length) {
          body.innerHTML = '<tr><td colspan="7">등록된 명단이 없습니다.</td></tr>';
          return;
        }
        body.innerHTML = list.map(function (it) {
          var badge = it.linked
            ? '<span class="tr-badge linked">연결됨</span>'
            : '<span class="tr-badge">' + esc(it.matchStatus || 'UNMATCHED') + '</span>';
          var delBtn = it.linked
            ? ''
            : '<button class="tr-btn danger" type="button" data-del="' + it.rosterId + '">삭제</button>';
          return '<tr>'
            + '<td>' + it.rosterId + '</td>'
            + '<td>' + esc(it.nickname) + '</td>'
            + '<td>' + esc(it.naverIdPrefix) + '</td>'
            + '<td>' + esc(it.grade) + '</td>'
            + '<td>' + esc(it.gradeChangedAt || '') + '</td>'
            + '<td>' + badge + '</td>'
            + '<td>' + delBtn + '</td>'
            + '</tr>';
        }).join('');
        body.querySelectorAll('[data-del]').forEach(function (btn) {
          btn.addEventListener('click', function () {
            onDelete(btn.getAttribute('data-del'));
          });
        });
      })
      .catch(function (e) {
        body.innerHTML = '<tr><td colspan="7">불러오기 실패: ' + esc(e.message) + '</td></tr>';
      });
  }

  function onAdd() {
    var nickname = nicknameEl.value.trim();
    var prefix = prefixEl.value.trim();
    var date = dateEl.value;
    if (!nickname || !prefix) {
      setMsg('닉네임과 네이버ID 앞자리를 입력하세요.', false);
      return;
    }
    addBtn.disabled = true;
    setMsg('등록 중…', true);
    fetch(API, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: nickname, naverIdPrefix: prefix, gradeChangedAt: date || null })
    })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, body: j }; }); })
      .then(function (res) {
        addBtn.disabled = false;
        if (res.ok && res.body.status === 'ok') {
          setMsg('등록 완료 (ID ' + res.body.roster.rosterId + ').', true);
          nicknameEl.value = '';
          prefixEl.value = '';
          loadList();
        } else {
          setMsg('등록 실패: ' + (res.body.errorMessage || '알 수 없는 오류'), false);
        }
      })
      .catch(function (e) {
        addBtn.disabled = false;
        setMsg('등록 실패: ' + e.message, false);
      });
  }

  function onDelete(rosterId) {
    if (!confirm('ID ' + rosterId + ' 명단을 삭제할까요? 연결된 회원이 없는 경우만 삭제됩니다.')) return;
    fetch(API + '/' + rosterId, { method: 'DELETE', credentials: 'same-origin' })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, body: j }; }); })
      .then(function (res) {
        if (res.ok && res.body.status === 'ok') {
          setMsg('삭제 완료.', true);
          loadList();
        } else {
          setMsg('삭제 실패: ' + (res.body.errorMessage || '알 수 없는 오류'), false);
        }
      })
      .catch(function (e) {
        setMsg('삭제 실패: ' + e.message, false);
      });
  }

  addBtn.addEventListener('click', onAdd);
  loadList();
})();
