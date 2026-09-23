(function () {
  'use strict';

  var list = document.getElementById('ns-updates-list');
  var filters = document.querySelectorAll('[data-source]');
  if (!list) return;

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
  }

  function formatDate(value) {
    if (!value) return '발표일 확인 필요';
    return value.slice(0, 10).replace(/-/g, '.');
  }

  function load(source) {
    list.innerHTML = '<p class="ns-empty">불러오는 중...</p>';
    var url = '/api/newstart/updates' + (source ? '?sourceType=' + encodeURIComponent(source) : '');
    fetch(url, { credentials: 'same-origin' })
      .then(function (response) {
        if (!response.ok) throw new Error('updates request failed');
        return response.json();
      })
      .then(function (items) {
        if (!items.length) {
          list.innerHTML = '<p class="ns-empty">현재 공개된 공식 업데이트가 없습니다.</p>';
          return;
        }
        list.innerHTML = items.map(function (item) {
          return '<a class="ns-update-item" href="/newstart/updates/' + encodeURIComponent(item.updateId) + '">'
            + '<span class="ns-update-source">' + escapeHtml(item.sourceName) + '</span>'
            + '<span class="ns-update-copy"><strong>' + escapeHtml(item.title) + '</strong><small>' + escapeHtml(item.summary) + '</small></span>'
            + '<time class="ns-update-date">' + escapeHtml(formatDate(item.publishedAt)) + '</time><span aria-hidden="true">→</span></a>';
        }).join('');
      })
      .catch(function () {
        list.innerHTML = '<p class="ns-empty">업데이트를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>';
      });
  }

  for (var i = 0; i < filters.length; i++) {
    filters[i].addEventListener('click', function () {
      for (var j = 0; j < filters.length; j++) filters[j].classList.remove('ns-btn-primary');
      this.classList.add('ns-btn-primary');
      load(this.getAttribute('data-source') || '');
    });
  }

  load('');
})();
