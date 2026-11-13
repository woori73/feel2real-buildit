/** 「바뀌는 입시」 글 관리(ADMIN). 권한은 서버(/api/admin/**)가 최종 결정한다. */
(function () {
  'use strict';
  var V = window.AdmissionsArticlesView;
  var API = '/api/admin/admissions/articles';
  var rows = [];
  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function msg(text, bad) { $('arMsg').textContent = text; $('arMsg').style.color = bad ? '#b42318' : ''; }

  async function call(method, url, body) {
    var res = await fetchAuth(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    var data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    if (!res.ok) throw new Error((data && data.message) || ('요청 실패 (' + res.status + ')'));
    return data;
  }

  function fill(a) {
    $('fId').value = a ? a.id : '';
    $('fSeries').value = a ? a.series : '바뀌는 입시';
    $('fEpisode').value = a ? a.episode : '';
    $('fTitle').value = a ? a.title : '';
    $('fSummary').value = a ? a.summary : '';
    $('fDate').value = a ? a.publishedOn : '';
    $('fOrder').value = a ? a.sortOrder : 0;
    $('fVisible').checked = a ? a.visible : true;
    $('fBody').value = a ? a.bodyHtml : '';
    $('arFormTitle').textContent = a ? '글 수정 · ' + a.episode + ' ' + a.title : '새 글 추가';
  }

  function render() {
    var box = $('arAdminList');
    box.replaceChildren();
    if (!rows.length) { box.append(el('p', 'ar-empty', '등록된 글이 없습니다.')); return; }
    rows.forEach(function (a) {
      var row = el('div', 'ar-admin-row');
      var left = el('div');
      left.append(el('span', 'ar-chip', a.series + ' ' + a.episode), document.createTextNode(' '), el('strong', null, a.title),
        el('div', 'ar-meta', V.dateText(a.publishedOn) + ' · 순서 ' + a.sortOrder + ' · ' + (a.visible ? '공개' : '숨김')));
      var right = el('div');
      var view = el('a', 'ar-btn', '보기'); view.href = V.articleUrl(a.id); view.target = '_blank'; view.rel = 'noopener';
      var edit = el('button', 'ar-btn', '수정'); edit.type = 'button';
      edit.addEventListener('click', function () { fill(a); msg(''); $('fTitle').focus(); });
      var toggle = el('button', 'ar-btn', a.visible ? '숨기기' : '다시 공개'); toggle.type = 'button';
      toggle.addEventListener('click', async function () {
        try { await call('POST', API + '/' + a.id + '/visibility', { visible: !a.visible }); await load(); msg(a.visible ? '숨겼습니다.' : '다시 공개했습니다.'); }
        catch (e) { msg(e.message, true); }
      });
      if (!a.visible) view.hidden = true;
      right.append(view, document.createTextNode(' '), edit, document.createTextNode(' '), toggle);
      row.append(left, right);
      box.append(row);
    });
  }

  async function load() { rows = await call('GET', API); render(); }

  async function save(e) {
    e.preventDefault();
    var body = V.formBody({ series: $('fSeries').value, episode: $('fEpisode').value, title: $('fTitle').value, summary: $('fSummary').value,
      bodyHtml: $('fBody').value, publishedOn: $('fDate').value, visible: $('fVisible').checked, sortOrder: $('fOrder').value });
    var id = $('fId').value;
    try {
      var saved = await call(id ? 'PUT' : 'POST', id ? API + '/' + id : API, body);
      await load();
      fill(saved);
      msg('저장했습니다.');
    } catch (err) { msg(err.message, true); }
  }

  async function init() {
    var user = await ensureCurrentUser();
    if (!user || user.role !== 'ADMIN') { location.href = '/admin/login'; return; }
    $('arForm').addEventListener('submit', save);
    $('fReset').addEventListener('click', function () { fill(null); msg(''); });
    try { await load(); } catch (e) { msg(e.message, true); }
  }
  init();
})();
