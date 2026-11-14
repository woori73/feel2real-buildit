/**
 * 첫 화면 「입시상담 AI」 카드 아래 「바뀌는 입시」 제목 목록. 공개 목록(GET /api/admissions/articles)을 읽기만 하고,
 * 회차 순(1-① → 1-⑦)으로 두 칸에 놓는다. 제목을 누르면 그 글로 간다. 회차 값(DB)은 그대로 두고 표시만 나눈다.
 */
(function () {
  'use strict';
  var V = window.AdmissionsArticlesView;
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function render(list) {
    var box = document.getElementById('hlAdmissionsArticles');
    var ul = document.getElementById('hlAdmissionsArticleList');
    if (!box || !ul || !V) return;
    var items = V.seriesItems(list, 10);
    if (!items.length) return;
    ul.replaceChildren();
    items.forEach(function (it) {
      var a = el('a');
      a.href = it.href;
      var parts = V.episodeParts(it.episode);
      var ep = el('span', 'ep');
      ep.append(document.createTextNode(parts.head), el('span', 'ep-n', parts.num));
      var title = el('strong', null, it.title);
      title.title = it.title;
      a.append(ep, title);
      var li = el('li');
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
