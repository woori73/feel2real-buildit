/* 감사회원 최초 1회 셀프 인증(회원 화면).
 * 회원은 카페 닉네임만 1회 입력한다. 판정(닉네임·네이버 이메일·연결 상태)은 모두 서버가 하며, 이 화면은 결과만 보여준다.
 * ADMIN 에게는 인증 섹션 자체를 보여주지 않는다. 모든 값은 textContent 로만 그린다(HTML 문자열 삽입 없음). */
(function () {
  'use strict';
  var root = document.getElementById('ns-thanks-verify');
  if (!root) return;
  var box = document.getElementById('ns-thanks-verify-body');
  if (!box) return;

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function clear() { box.textContent = ''; }
  function note(text) { box.appendChild(el('p', 'ns-note', text)); }
  function api(method, url, data) {
    var opt = { method: method, credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } };
    if (data) opt.body = JSON.stringify(data);
    return fetch(url, opt).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) { return { status: res.status, json: json }; });
    });
  }
  function hideSection() { root.style.display = 'none'; }

  function renderLogin() {
    clear();
    note('감사회원 인증은 네이버 로그인 후 최초 1회만 하면 됩니다. 이후에는 네이버 로그인만으로 감사회원 기능을 쓸 수 있습니다.');
    var a = el('a', 'ns-btn ns-btn-primary', '네이버로 로그인');
    a.setAttribute('href', '/oauth2/authorization/naver');
    box.appendChild(a);
  }

  function renderApproved(msg) {
    clear();
    box.appendChild(el('p', null, msg || '감사회원으로 확인되었습니다.'));
    var a = el('a', 'ns-btn ns-btn-primary', '지피지기 Agent 사용하기');
    a.setAttribute('href', '/newstart#ns-ai');
    box.appendChild(a);
  }

  function renderException(msg) {
    clear();
    box.appendChild(el('p', null, '운영자 확인 중입니다.'));
    note(msg || '자동으로 확인하지 못해 운영자에게 전달했습니다. 확인되면 안내 없이 바로 이용할 수 있습니다.');
  }

  function renderForm(message) {
    clear();
    note('카페에서 쓰는 닉네임을 정확히 입력해 주세요. 한 번 확인되면 이후에는 입력 없이 네이버 로그인만으로 이용할 수 있습니다.');
    var input = el('input', 'ns-input');
    input.setAttribute('type', 'text');
    input.setAttribute('maxlength', '50');
    input.setAttribute('placeholder', '카페 닉네임');
    input.setAttribute('aria-label', '카페 닉네임');
    box.appendChild(input);
    var btn = el('button', 'ns-btn ns-btn-primary', '감사회원 확인');
    btn.setAttribute('type', 'button');
    btn.style.marginTop = '8px';
    var msg = el('p', 'ns-note', message || '');
    btn.addEventListener('click', function () {
      btn.disabled = true;
      api('POST', '/api/newstart/thanks-verification', { nickname: input.value }).then(function (r) {
        btn.disabled = false;
        if (r.status === 401) { renderLogin(); return; }
        render(r.json);
      }).catch(function () {
        btn.disabled = false;
        msg.textContent = '잠시 후 다시 시도해 주세요.';
      });
    });
    box.appendChild(btn);
    box.appendChild(msg);
  }

  function render(o) {
    if (!o) { renderForm(); return; }
    switch (o.state) {
      case 'ADMIN': hideSection(); break;                       // 운영자는 인증 화면을 보지 않는다
      case 'APPROVED': renderApproved(o.message); break;
      case 'EXCEPTION': renderException(o.message); break;
      case 'FAILED': renderForm(o.message); break;
      case 'ERROR': renderForm(o.message); break;
      default: renderForm();
    }
  }

  api('GET', '/api/newstart/thanks-verification/status').then(function (r) {
    if (r.status === 401) { renderLogin(); return; }
    render(r.json);
  }).catch(function () { clear(); note('상태를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); });
})();
