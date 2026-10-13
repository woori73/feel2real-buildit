/*
 * 기존 빌더의 'AI로 이어서 만들기' 버튼과 확인 창.
 * 기존 방식으로 만든 학원은 사용자가 확인해야만 AI 제작 방식으로 전환된다(대화 API 호출만으로는 바뀌지 않는다).
 * 서버가 모드 조회(GET /mode)에서 canAdopt=true를 줄 때만 버튼을 보이고, 전환(POST /adopt {confirm:true})은
 * 확인 체크 후에만 보낸다. 전환해도 공개 홈페이지는 그대로이고 전환 전 공개본은 버전 기록에 보관된다.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.AcademyAdopt = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  var API = '/api/tutor/academy/assistant';

  function el(doc, tag, cls, text) {
    var e = doc.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  /** 서버가 허용하고(canAdopt) 전환 가능한 학원일 때만 true. */
  function canAdopt(modeResponse) {
    return !!(modeResponse && modeResponse.canAdopt === true);
  }

  function buildModal(doc) {
    var wrap = el(doc, 'div', 'adopt-modal');
    wrap.setAttribute('role', 'dialog');
    wrap.setAttribute('aria-modal', 'true');
    wrap.setAttribute('aria-labelledby', 'adoptTitle');
    wrap.hidden = true;
    var card = el(doc, 'div', 'adopt-card');
    var title = el(doc, 'h2', null, 'AI로 이어서 만들기');
    title.id = 'adoptTitle';
    var list = el(doc, 'ul', 'adopt-points');
    [
      '지금 공개된 홈페이지는 그대로예요. 전환 전 내용은 버전 기록에 보관돼요.',
      '앞으로의 수정은 작업본에만 저장되고, \'게시하기\'를 눌러야 공개돼요.',
      '대화 화면에서 \'기존 빌더\'로 언제든 돌아올 수 있어요.'
    ].forEach(function (t) { list.appendChild(el(doc, 'li', null, t)); });
    var label = el(doc, 'label', 'adopt-check');
    var check = el(doc, 'input'); check.type = 'checkbox'; check.id = 'adoptCheck';
    label.appendChild(check);
    label.appendChild(doc.createTextNode(' 위 내용을 확인했어요'));
    var msg = el(doc, 'p', 'adopt-msg'); msg.id = 'adoptMsg'; msg.setAttribute('role', 'status');
    var actions = el(doc, 'div', 'adopt-actions');
    var cancel = el(doc, 'button', 'adopt-cancel', '취소'); cancel.type = 'button'; cancel.id = 'adoptCancel';
    var ok = el(doc, 'button', 'adopt-ok', 'AI로 이어서 만들기'); ok.type = 'button'; ok.id = 'adoptOk'; ok.disabled = true;
    actions.appendChild(cancel); actions.appendChild(ok);
    [title, list, label, msg, actions].forEach(function (n) { card.appendChild(n); });
    wrap.appendChild(card);
    return { wrap: wrap, check: check, msg: msg, cancel: cancel, ok: ok };
  }

  /**
   * @param deps {document, fetch, navigate(url), mount: 버튼을 붙일 요소}
   * @returns Promise<boolean> 버튼을 보였는지
   */
  function init(deps) {
    var doc = deps.document, fetchFn = deps.fetch;
    return fetchFn(API + '/mode', { credentials: 'same-origin' }).then(function (r) {
      if (!r.ok) return null; // 스위치 꺼짐(403) 등: 버튼을 보이지 않는다
      return r.json();
    }).then(function (mode) {
      if (!canAdopt(mode)) return false;
      // 좁은 상단 바에 끼우지 않고 바로 아래 얇은 안내 줄로 둔다(모바일에서도 보이고 눌림). 본문은 CSS로 그만큼 내려간다.
      var bar = el(doc, 'div', 'adopt-bar');
      var note = el(doc, 'span', 'adopt-note', '기존 방식으로 만든 홈페이지예요');
      var btn = el(doc, 'button', 'adopt-btn', 'AI로 이어서 만들기');
      btn.type = 'button'; btn.id = 'adoptBtn';
      bar.appendChild(note); bar.appendChild(btn);
      var modal = buildModal(doc);
      deps.mount.appendChild(bar);
      if (doc.body.classList) doc.body.classList.add('has-adopt-bar');
      doc.body.appendChild(modal.wrap);
      btn.addEventListener('click', function () {
        modal.check.checked = false; modal.ok.disabled = true; modal.msg.textContent = ''; modal.wrap.hidden = false;
      });
      modal.cancel.addEventListener('click', function () { modal.wrap.hidden = true; });
      modal.check.addEventListener('change', function () { modal.ok.disabled = !modal.check.checked; });
      modal.ok.addEventListener('click', function () {
        if (!modal.check.checked) return; // 확인 없이는 요청을 보내지 않는다
        modal.ok.disabled = true; modal.msg.textContent = '전환하는 중이에요…';
        fetchFn(API + '/adopt', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ confirm: true })
        }).then(function (r) {
          return r.json().catch(function () { return {}; }).then(function (j) {
            if (!r.ok) throw new Error(j.message || '전환하지 못했어요.');
            deps.navigate('/builder');
          });
        }).catch(function (e) {
          modal.msg.textContent = (e && e.message ? e.message : '전환하지 못했어요.') + ' 홈페이지는 그대로예요.';
          modal.ok.disabled = !modal.check.checked;
        });
      });
      return true;
    }).catch(function () { return false; });
  }

  function boot() {
    if (typeof document === 'undefined') return;
    var header = document.querySelector('.builder-topbar');
    if (!header || !header.parentNode) return;
    var mount = document.createElement('div');
    header.parentNode.insertBefore(mount, header.nextSibling);
    init({ document: document, fetch: window.fetch.bind(window), navigate: function (u) { window.location.href = u; }, mount: mount });
  }
  if (typeof document !== 'undefined' && typeof window !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
  }

  return { canAdopt: canAdopt, init: init };
});
