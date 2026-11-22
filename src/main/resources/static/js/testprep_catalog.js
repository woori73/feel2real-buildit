(async function () {
  'use strict';
  const box = document.getElementById('catalog');
  try {
    if (!window.TP) throw new Error('app not loaded');
    await (TP.user ? Promise.resolve() : TP.loadMe());
    const exams = await TP.api('/api/testprep/exams?kind=' + window.TP_KIND);
    // 전 세트 이용자인지(재원생·관리자). 못 불러오면 표시 없이 두고, 시작할 때 서버가 막는다.
    let full = true;
    try { full = !!(await TP.api('/api/naesintest/access')).full; } catch (e) { /* 표시만 생략 */ }
    if (!exams.length) { box.innerHTML = '<p class="lead">No published exams yet.<span class="ko">아직 공개된 시험이 없습니다.</span></p>'; return; }
    box.innerHTML = exams.map((e, i) => `
      <div class="card exam-card" style="margin-bottom:14px">
        <div>
          <h3>${TP.esc(e.title)}</h3>
          <p>${TP.esc(e.subtitle || '')}</p>
          ${e.kind === 'SAT'
            ? '<div class="meta">' + e.questionCount + ' questions · timed by module<span class="ko">문항 ' + e.questionCount + '개 · 모듈별 시간 제한</span></div>'
            : `<div class="meta">${e.questionCount} questions · ${Math.round(e.timeLimitSec / 60)} min${e.difficulty ? ' · ' + TP.esc(e.difficulty) : ''}<span class="ko">문항 ${e.questionCount}개 · 시간 ${Math.round(e.timeLimitSec / 60)}분</span></div>`}
        </div>
        ${TP.startButtonHtml(e, i, full)}
      </div>`).join('');
    box.querySelectorAll('[data-start]').forEach(btn => btn.addEventListener('click', () => {
      TP.requireLogin(async () => {
        btn.disabled = true;
        try {
          const attempt = await TP.api('/api/testprep/attempts', { method: 'POST', body: JSON.stringify({ examCode: btn.dataset.start }) });
          location.href = TP.runUrl(attempt.kind, attempt.attemptId);
        } catch (err) {
          btn.disabled = false;
          alert('Could not start: ' + err.message + '\n시작할 수 없습니다: ' + err.message);
        }
      });
    }));
  } catch (err) {
    box.innerHTML = '<p class="lead">Failed to load exams: ' + TP.esc(err.message) + '<span class="ko">시험을 불러오지 못했습니다: ' + TP.esc(err.message) + '</span></p>';
  }
})();
