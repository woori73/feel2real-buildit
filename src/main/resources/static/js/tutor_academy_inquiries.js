/**
 * 상담 문의 관리 화면. API: GET /api/tutor/academy/inquiries, PATCH /api/tutor/academy/inquiries/{id}/status
 * 서버가 준 문자열(이름·연락처·문의 내용)은 textContent로만 넣는다(XSS 방지). 개인정보는 콘솔에 남기지 않는다.
 */
(function () {
  'use strict';
  var STATUS_LABEL = { NEW: '신규', IN_PROGRESS: '처리중', DONE: '완료' };
  var NEXT = [['IN_PROGRESS', '처리중으로'], ['DONE', '완료로'], ['NEW', '신규로']];
  var state = { status: '', page: 0, size: 20, loaded: 0 };
  var $ = function (id) { return document.getElementById(id); };

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function formatWhen(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR', { year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  }
  /** tel: 링크에는 숫자와 앞 +만 허용한다. */
  function telHref(phone) {
    return /^\+?[0-9]{8,15}$/.test(phone || '') ? 'tel:' + phone : null;
  }
  function renderItem(it) {
    var li = el('li', 'inq-item'); li.dataset.id = it.id;
    var head = el('div', 'inq-head');
    head.appendChild(el('span', 'inq-who', it.anonymized ? '(삭제된 문의)' : (it.name || '')));
    var right = el('span');
    right.appendChild(el('span', 'inq-badge ' + it.status, STATUS_LABEL[it.status] || it.status));
    right.appendChild(document.createTextNode(' '));
    right.appendChild(el('span', 'inq-when', formatWhen(it.createdAt)));
    head.appendChild(right); li.appendChild(head);
    if (it.phone) {
      var p = el('div', 'inq-phone'); var href = telHref(it.phone);
      if (href) { var a = el('a', null, it.phone); a.href = href; p.appendChild(a); } else p.textContent = it.phone;
      li.appendChild(p);
    }
    if (it.message) li.appendChild(el('div', 'inq-msg', it.message));
    var act = el('div', 'inq-actions');
    NEXT.forEach(function (n) {
      var b = el('button', null, n[1]); b.type = 'button'; b.disabled = it.status === n[0];
      b.addEventListener('click', function () { change(it.id, n[0], li); });
      act.appendChild(b);
    });
    li.appendChild(act);
    return li;
  }
  function load(reset) {
    if (reset) { state.page = 0; state.loaded = 0; $('inqList').textContent = ''; }
    $('inqState').textContent = '불러오는 중…';
    var q = '?page=' + state.page + '&size=' + state.size + (state.status ? '&status=' + state.status : '');
    return window.fetchAuth('/api/tutor/academy/inquiries' + q).then(function (r) {
      if (r.status === 401 || r.status === 403) { $('inqState').textContent = '원장 계정으로 로그인해야 볼 수 있어요.'; return null; }
      if (!r.ok) throw new Error('load');
      return r.json();
    }).then(function (j) {
      if (!j) return;
      j.items.forEach(function (it) { $('inqList').appendChild(renderItem(it)); });
      state.loaded += j.items.length;
      $('inqNewCount').textContent = String(j.newCount);
      $('inqMore').hidden = state.loaded >= j.total;
      $('inqState').textContent = j.total === 0 ? '접수된 문의가 없어요.' : '총 ' + j.total + '건';
    }).catch(function () { $('inqState').textContent = '문의 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'; });
  }
  function change(id, status, li) {
    window.fetchAuth('/api/tutor/academy/inquiries/' + id + '/status', {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: status })
    }).then(function (r) {
      if (!r.ok) throw new Error('status');
      load(true);
    }).catch(function () { $('inqState').textContent = '상태를 바꾸지 못했어요. 다시 시도해 주세요.'; });
  }
  if (typeof module !== 'undefined' && module.exports) { module.exports = { telHref: telHref, formatWhen: formatWhen }; }
  if (typeof document === 'undefined') return;
  Array.prototype.forEach.call(document.querySelectorAll('.inq-chip'), function (c) {
    c.addEventListener('click', function () {
      Array.prototype.forEach.call(document.querySelectorAll('.inq-chip'), function (x) { x.classList.toggle('on', x === c); });
      state.status = c.dataset.status; load(true);
    });
  });
  $('inqMore').addEventListener('click', function () { state.page++; load(false); });
  load(true);
})();
