(function () {
  'use strict';
  var body = document.getElementById('updates-body');
  var form = document.getElementById('update-form');
  var message = document.getElementById('form-message');
  if (!body || !form) return;

  function jsonOptions(method, data) {
    return { method: method, credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) };
  }
  function value(formData, key) {
    var v = formData.get(key);
    return v === '' ? null : v;
  }
  function load() {
    fetch('/api/admin/newstart/updates', { credentials: 'same-origin' }).then(function (r) { if (!r.ok) throw new Error(); return r.json(); }).then(function (items) {
      if (!items.length) { body.innerHTML = '<tr><td colspan="5">등록된 후보가 없습니다.</td></tr>'; return; }
      body.innerHTML = items.map(function (item) {
        var actions = '';
        if (item.reviewStatus === 'REVIEW') actions = '<button data-action="publish" data-id="' + item.updateId + '">게시</button><button data-action="reject" data-id="' + item.updateId + '">거절</button>';
        if (item.reviewStatus === 'REJECTED') actions = '<button data-action="review" data-id="' + item.updateId + '">재검토</button>';
        if (item.reviewStatus === 'PUBLISHED') actions = '<button data-action="archive" data-id="' + item.updateId + '">보관</button><button data-action="featured" data-id="' + item.updateId + '">' + (item.featured ? '주요 해제' : '주요 지정') + '</button>';
        return '<tr><td>' + item.reviewStatus + (item.featured ? '<small>FEATURED</small>' : '') + '</td><td><strong>' + item.sourceName + '</strong><small>' + item.title + '</small><small>' + item.summary + '</small></td><td>' + (item.publishedAt || '확인 필요') + '</td><td>' + (item.originalUrl ? '<a href="' + item.originalUrl + '" target="_blank" rel="noopener">원문</a>' : '없음') + '</td><td><div class="update-actions">' + actions + '</div></td></tr>';
      }).join('');
    }).catch(function () { body.innerHTML = '<tr><td colspan="5">목록을 불러오지 못했습니다.</td></tr>'; });
  }
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var data = new FormData(form);
    var payload = { sourceType: data.get('sourceType'), sourceName: data.get('sourceName'), title: data.get('title'), summary: data.get('summary'), originalUrl: value(data, 'originalUrl'), officialDocumentId: value(data, 'officialDocumentId'), publishedAt: value(data, 'publishedAt'), effectiveFrom: value(data, 'effectiveFrom'), effectiveTo: value(data, 'effectiveTo'), category: data.get('category') };
    fetch('/api/admin/newstart/updates', jsonOptions('POST', payload)).then(function (r) { if (!r.ok) return r.text().then(function (text) { throw new Error(text || '등록 실패'); }); return r.json(); }).then(function () { message.textContent = 'REVIEW 후보로 등록했습니다.'; form.reset(); load(); }).catch(function (error) { message.textContent = error.message; });
  });
  body.addEventListener('click', function (event) {
    var button = event.target.closest('button[data-action]');
    if (!button) return;
    var action = button.getAttribute('data-action');
    var id = button.getAttribute('data-id');
    var url = '/api/admin/newstart/updates/' + id + '/' + action;
    if (action === 'featured') url += '?enabled=' + (button.textContent === '주요 지정');
    fetch(url, { method: 'POST', credentials: 'same-origin' }).then(function (r) { if (!r.ok) throw new Error(); load(); }).catch(function () { message.textContent = '상태 변경에 실패했습니다.'; });
  });
  load();
})();
