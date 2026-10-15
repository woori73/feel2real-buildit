(async function () {
  'use strict';
  const wrongsBox = document.getElementById('wrongs');
  await TP.loadMe();
  if (!TP.user) { wrongsBox.innerHTML = '<p class="lead">Please <a href="/home">log in</a> to see your learning data.<span class="ko">학습 데이터를 확인하려면 <a href="/home">로그인</a>하세요.</span></p>'; return; }

  try {
    const lv = await TP.api('/api/testprep/learning');
    document.getElementById('stats').innerHTML = '<div class="stat-row">'
      + '<div class="stat"><b>' + lv.totalAttempts + '</b><span>attempts<span class="ko">응시</span></span></div>'
      + '<div class="stat"><b>' + lv.submittedAttempts + '</b><span>submitted<span class="ko">제출</span></span></div>'
      + '<div class="stat"><b>' + lv.practiceAttempts + '</b><span>practice tests<span class="ko">모의고사</span></span></div>'
      + '<div class="stat"><b>' + lv.vocabAttempts + '</b><span>vocab quizzes<span class="ko">어휘 퀴즈</span></span></div>'
      + '<div class="stat"><b>' + (lv.bestBand != null ? lv.bestBand : '—') + '</b><span>best internal band<span class="ko">최고 내부 밴드</span></span></div>'
      + '</div><p class="disclaimer">' + TP.esc(lv.bandDisclaimer) + '<span class="ko" style="color:#7a5300">TestPrep 내부 밴드 추정치 — 공식 ETS TOEFL iBT 점수가 아닙니다.</span></p>';

    document.getElementById('sections').innerHTML = lv.latestSectionScores.length
      ? '<table class="tp"><tr><th>Section<span class="ko">영역</span></th><th>Band<span class="ko">밴드</span></th><th>Accuracy<span class="ko">정답률</span></th><th>Auto-scored<span class="ko">자동 채점</span></th></tr>'
      + lv.latestSectionScores.map(s => '<tr><td>' + TP.esc(s.section) + '</td><td>' + (s.band != null ? s.band : '—')
        + '</td><td>' + (s.accuracy != null ? s.accuracy + '%' : '—') + '</td><td>' + s.correct + '/' + s.autoCount + '</td></tr>').join('') + '</table>'
      : '<p class="lead">Finish a practice test to see your first section bands.<span class="ko">모의고사를 완료하면 첫 영역별 밴드가 표시됩니다.</span></p>';

    if (!lv.wrongItems.length) { wrongsBox.innerHTML = '<p class="lead">No wrong answers on record. Perfect — or nothing taken yet.<span class="ko">기록된 오답이 없습니다. 완벽하거나, 아직 응시한 기록이 없거나입니다.</span></p>'; return; }

    const byExam = {};
    lv.wrongItems.forEach(w => { (byExam[w.examCode] = byExam[w.examCode] || []).push(w); });
    wrongsBox.innerHTML = Object.entries(byExam).map(([code, items]) => `
      <div class="card" style="margin-bottom:14px">
        <h3>${TP.esc(items[0].examTitle)}</h3>
        <p>${items.length} wrong item(s) from this exam.<span class="ko">이 시험에서 ${items.length}개의 오답이 있습니다.</span></p>
        <table class="tp"><tr><th>Last wrong<span class="ko">최종 오답일</span></th><th>Section<span class="ko">영역</span></th><th>Question<span class="ko">문항</span></th></tr>
        ${items.map(w => '<tr><td>' + TP.esc((w.lastWrongAt || '').slice(0, 16)) + '</td><td>' + TP.esc(w.section)
          + '</td><td>' + TP.esc(w.promptShort) + '</td></tr>').join('')}</table>
        <button class="btn btn-primary" style="margin-top:12px" data-retry="${TP.esc(code)}"
          data-ids="${items.map(w => w.questionId).join(',')}">Retry these ${items.length} items</button>
      </div>`).join('');
    wrongsBox.querySelectorAll('[data-retry]').forEach(btn => btn.addEventListener('click', async () => {
      btn.disabled = true;
      try {
        const attempt = await TP.api('/api/testprep/attempts', { method: 'POST', body: JSON.stringify({
          examCode: btn.dataset.retry, questionIds: btn.dataset.ids.split(',').map(Number) }) });
        location.href = '/testprep/run?attempt=' + attempt.attemptId;
      } catch (e) { btn.disabled = false; alert('Retry failed: ' + e.message + '\n다시 풀기 실패: ' + e.message); }
    }));
  } catch (err) {
    wrongsBox.innerHTML = '<p class="lead">Failed to load: ' + TP.esc(err.message) + '<span class="ko">불러오기 실패: ' + TP.esc(err.message) + '</span></p>';
  }
})();
