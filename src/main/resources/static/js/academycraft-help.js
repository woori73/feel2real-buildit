(function () {
  var form = document.getElementById('ach-ask-form');
  if (!form) return;
  var input = form.querySelector('input');
  var out = document.getElementById('ach-answer');
  var src = document.getElementById('ach-sources');
  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var q = input.value.trim();
    if (!q) return;
    out.textContent = '찾는 중…';
    src.textContent = '';
    fetch('/api/help/academycraft/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ question: q })
    }).then(function (r) { return r.json(); }).then(function (d) {
      out.className = 'ach-answer' + (d.grounded ? '' : ' ungrounded');
      out.textContent = d.text || d.error || '답변을 가져오지 못했습니다.';
      (d.sources || []).forEach(function (s, i) {
        var a = document.createElement('a');
        a.href = s.link;
        a.textContent = s.title + (s.lastVerified ? ' (' + s.docId + ', 검증 ' + s.lastVerified + ')' : '');
        if (i) src.appendChild(document.createTextNode(' · '));
        src.appendChild(a);
      });
    }).catch(function () { out.textContent = '답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.'; });
  });
})();
