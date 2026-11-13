/** 입시상담 첫 화면의 「바뀌는 입시」 카드. 공개 목록(GET /api/admissions/articles)을 읽기만 한다. 글이 없거나 실패하면 카드를 숨긴 채 둔다. */
(function () {
  'use strict';
  var V = window.AdmissionsArticlesView;
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function render(list) {
    var box = document.getElementById('ae-articles');
    var ul = document.getElementById('ae-articles-list');
    if (!box || !ul || !V) return;
    var items = V.homeItems(list, 3);
    if (!items.length) return;
    ul.replaceChildren();
    items.forEach(function (it) {
      var li = el('li', 'ae-article');
      var a = el('a');
      a.href = it.href;
      var meta = el('span', 'ae-article-meta', it.label + (it.date ? ' · ' + it.date : ''));
      a.append(meta, el('strong', null, it.title), el('span', 'ae-article-summary', it.summary));
      li.append(a);
      ul.append(li);
    });
    box.hidden = false;
  }
  function init() {
    fetch('/api/admissions/articles', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(render, function () {});
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
