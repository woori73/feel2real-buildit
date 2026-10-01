/* 감사회원 최초 1회 본인확인(회원 화면).
 * 어떤 단계도 권한을 직접 부여하지 않는다 — 요청(PENDING)만 만들고, 부여는 운영자 승인 뒤에만 일어난다.
 * 모든 값은 textContent로만 그린다(HTML 문자열 삽입 없음). */
(function () {
  'use strict';
  var root = document.getElementById('ns-thanks-claim');
  if (!root) return;
  var box = document.getElementById('ns-thanks-claim-body');
  if (!box) return;

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function clear() { box.textContent = ''; }
  function api(method, url, data) {
    var opt = { method: method, credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } };
    if (data) opt.body = JSON.stringify(data);
    return fetch(url, opt).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) { return { status: res.status, json: json }; });
    });
  }
  function note(text) { box.appendChild(el('p', 'ns-note', text)); }

  function renderLogin() {
    clear();
    note('감사회원 본인확인은 네이버 로그인 후 최초 1회만 하면 됩니다. 확인이 끝나면 이후에는 네이버 로그인만으로 감사회원 기능을 쓸 수 있습니다.');
    var a = el('a', 'ns-btn ns-btn-primary', '네이버로 로그인');
    a.setAttribute('href', '/oauth2/authorization/naver');
    box.appendChild(a);
  }

  function renderApproved(msg) {
    clear();
    box.appendChild(el('p', 'ns-note', msg || '감사회원으로 확인되었습니다.'));
    var a = el('a', 'ns-btn ns-btn-primary', '지피지기 Agent 사용하기');
    a.setAttribute('href', '/newstart#ns-ai');
    box.appendChild(a);
  }

  function renderPending(c) {
    clear();
    box.appendChild(el('p', null, '운영자 확인 대기 중입니다.'));
    note('선택한 명단: ' + (c.rosterNickname || '') + ' (' + (c.maskedId || '') + ')');
    var codeWrap = el('p', null, '인증코드: ');
    codeWrap.appendChild(el('strong', 'ns-thanks-code', c.verifyCode || ''));
    box.appendChild(codeWrap);
    note('위 인증코드를 카페 쪽지(구매자 성함·카페 닉네임을 보낸 그 쪽지)로 운영자에게 보내 주세요. 운영자가 쪽지 보낸 카페 계정과 코드를 확인한 뒤 승인하면 감사회원 기능이 열립니다. 코드는 이 화면에서만 확인할 수 있고 타인에게 알려주지 마세요.');
    var cancel = el('button', 'ns-btn', '요청 취소');
    cancel.type = 'button';
    cancel.addEventListener('click', function () {
      api('DELETE', '/api/newstart/thanks-claim').then(load);
    });
    box.appendChild(cancel);
    var refresh = el('button', 'ns-btn', '승인 여부 새로고침');
    refresh.type = 'button';
    refresh.style.marginLeft = '8px';
    refresh.addEventListener('click', load);
    box.appendChild(refresh);
  }

  function renderForm(extraMsg) {
    clear();
    if (extraMsg) note(extraMsg);
    note('카페에서 쓰는 닉네임을 정확히 입력하면 명단에서 후보를 찾습니다. 본인 닉네임을 선택하면 인증코드가 만들어집니다(자동 승인되지 않습니다).');
    var input = el('input', 'ns-input');
    input.type = 'text';
    input.maxLength = 50;
    input.placeholder = '카페 닉네임';
    input.setAttribute('aria-label', '카페 닉네임');
    box.appendChild(input);
    var find = el('button', 'ns-btn ns-btn-primary', '후보 찾기');
    find.type = 'button';
    find.style.marginTop = '8px';
    var list = el('div', null, null);
    list.style.marginTop = '10px';
    find.addEventListener('click', function () {
      list.textContent = '';
      api('POST', '/api/newstart/thanks-claim/candidates', { nickname: input.value }).then(function (r) {
        if (r.status === 401) { renderLogin(); return; }
        if (!r.json || r.json.status !== 'ok') { list.appendChild(el('p', 'ns-note', (r.json && r.json.errorMessage) || '후보를 찾지 못했습니다.')); return; }
        if (!r.json.candidates.length) { list.appendChild(el('p', 'ns-note', '일치하는 명단이 없습니다. 닉네임을 다시 확인하거나 카페 쪽지로 운영자에게 문의해 주세요.')); return; }
        r.json.candidates.forEach(function (cand) {
          var b = el('button', 'ns-btn', cand.nickname + ' (' + cand.maskedId + ') — 이 닉네임이 맞습니다');
          b.type = 'button';
          b.style.display = 'block';
          b.style.marginBottom = '6px';
          b.addEventListener('click', function () {
            api('POST', '/api/newstart/thanks-claim', { rosterId: cand.rosterId }).then(function (s) {
              if (s.json && s.json.status === 'ok') render(s.json.claim);
              else list.appendChild(el('p', 'ns-note', (s.json && s.json.errorMessage) || '요청하지 못했습니다.'));
            });
          });
          list.appendChild(b);
        });
      }).catch(function () { list.appendChild(el('p', 'ns-note', '잠시 후 다시 시도해 주세요.')); });
    });
    box.appendChild(find);
    box.appendChild(list);
  }

  function render(c) {
    if (!c) { renderForm(); return; }
    if (c.state === 'APPROVED') renderApproved(c.message);
    else if (c.state === 'PENDING') renderPending(c);
    else if (c.state === 'REJECTED') renderForm(c.message);
    else renderForm();
  }

  function load() {
    api('GET', '/api/newstart/thanks-claim/status').then(function (r) {
      if (r.status === 401) { renderLogin(); return; }
      render(r.json);
    }).catch(function () { clear(); note('상태를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); });
  }
  load();
})();
