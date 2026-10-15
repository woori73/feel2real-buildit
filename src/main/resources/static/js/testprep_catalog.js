(async function () {
  'use strict';
  const box = document.getElementById('catalog');
  try {
    if (!window.TP) throw new Error('app not loaded');
    await (TP.user ? Promise.resolve() : TP.loadMe());
    const exams = await TP.api('/api/testprep/exams?kind=' + window.TP_KIND);
    if (!exams.length) { box.innerHTML = '<p class="lead">No published exams yet.<span class="ko">아직 공개된 시험이 없습니다.</span></p>'; return; }
    box.innerHTML = exams.map(e => `
      <div class="card exam-card" style="margin-bottom:14px">
        <div>
          <h3>${TP.esc(e.title)}</h3>
          <p>${TP.esc(e.subtitle || '')}</p>
          <div class="meta">${e.questionCount} questions · ${Math.round(e.timeLimitSec / 60)} min${e.difficulty ? ' · ' + TP.esc(e.difficulty) : ''}<span class="ko">문항 ${e.questionCount}개 · 시간 ${Math.round(e.timeLimitSec / 60)}분</span></div>
        </div>
        <button class="btn btn-primary" data-start="${TP.esc(e.examCode)}">Start</button>
      </div>`).join('');
    box.querySelectorAll('[data-start]').forEach(btn => btn.addEventListener('click', () => {
      TP.requireLogin(async () => {
        btn.disabled = true;
        try {
          const attempt = await TP.api('/api/testprep/attempts', { method: 'POST', body: JSON.stringify({ examCode: btn.dataset.start }) });
          location.href = '/testprep/run?attempt=' + attempt.attemptId;
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
