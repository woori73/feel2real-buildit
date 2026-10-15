(function () {
  'use strict';
  const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
  const box = document.getElementById('rv-items');

  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { box.innerHTML = '<p class="lead">No attempt specified.<span class="ko">응시 기록이 지정되지 않았습니다.</span></p>'; return; }
    await TP.loadMe();
    let rv;
    try {
      rv = await TP.api('/api/testprep/attempts/' + id + '/review');
    } catch (err) { box.innerHTML = '<p class="lead">Cannot load review: ' + TP.esc(err.message) + '<span class="ko">복습 화면을 불러오지 못했습니다: ' + TP.esc(err.message) + '</span></p>'; return; }

    const titleEl = document.getElementById('rv-title');
    titleEl.innerHTML = TP.esc(rv.examTitle) + '<span class="ko">정답 복습</span>';
    document.getElementById('rv-sub').innerHTML =
      TP.esc((rv.submittedAt || '') + ' · ' + rv.items.length + ' questions' + (rv.overtime ? ' · submitted after time limit' : ''))
      + '<span class="ko">' + TP.esc(rv.submittedAt || '') + ' · 문항 ' + rv.items.length + '개' + (rv.overtime ? ' · 시간 제한 종료 후 제출' : '') + '</span>';

    document.getElementById('rv-score').innerHTML =
      '<div class="stat-row">'
      + '<div class="stat"><b>' + (rv.overallBand != null ? rv.overallBand : '—') + '</b><span>overall band (internal)<span class="ko">종합 밴드(내부)</span></span></div>'
      + '<div class="stat"><b>' + (rv.accuracy != null ? rv.accuracy + '%' : '—') + '</b><span>auto-score accuracy<span class="ko">자동 채점 정답률</span></span></div>'
      + (rv.sections || []).map(s => '<div class="stat"><b>' + (s.band != null ? s.band : '—') + '</b><span>' + TP.esc(s.section) + ' · ' + s.correct + '/' + s.autoCount + '</span></div>').join('')
      + '</div><p class="disclaimer">' + TP.esc(rv.bandDisclaimer) + '<span class="ko" style="color:#7a5300">TestPrep 내부 밴드 추정치 — 공식 ETS TOEFL iBT 점수가 아닙니다.</span></p>';

    box.innerHTML = rv.items.map((it, idx) => {
      const verdict = !it.autoScore ? '<span class="tag neutral">manual review<span class="ko">수동 검토</span></span>'
        : it.isCorrect ? '<span class="tag good">correct<span class="ko">정답</span></span>' : '<span class="tag bad">wrong<span class="ko">오답</span></span>';
      let answerHtml = '';
      if (it.choices.length && it.taskType !== 'BUILD_SENTENCE') {
        answerHtml = '<div class="opts">' + it.choices.map((c, i) => {
          const isKey = it.correctAnswer === LETTERS[i];
          const isYours = it.yourAnswer === LETTERS[i];
          const cls = isKey ? 'sel' : '';
          return '<div class="opt ' + cls + '"><span><strong>' + LETTERS[i] + '.</strong> ' + TP.esc(c) + ' '
            + (isKey ? '✔ key / 정답' : '') + (isYours && !isKey ? ' (your answer / 당신의 답)' : '') + '</span></div>';
        }).join('') + '</div>';
      } else if (it.taskType === 'BUILD_SENTENCE') {
        answerHtml = '<p style="margin:6px 0"><strong>Your order:</strong> ' + TP.esc(it.yourAnswer || '(blank)') + '<span class="ko">당신의 배열: ' + TP.esc(it.yourAnswer || '(비어 있음)') + '</span></p>'
          + '<p style="margin:6px 0"><strong>Correct:</strong> ' + TP.esc(it.correctAnswer || '—') + '<span class="ko">정답: ' + TP.esc(it.correctAnswer || '—') + '</span></p>';
      } else {
        answerHtml = '<div class="q-body">' + TP.esc(it.yourAnswer || '(no response recorded)') + '<span class="ko">작성한 답변: ' + TP.esc(it.yourAnswer || '(기록된 응답 없음)') + '</span></div>';
      }
      return `<div class="q-card review-item ${it.autoScore ? (it.isCorrect ? 'correct' : 'wrong') : ''}">
        <div class="q-type">${TP.esc(it.section)} · ${TP.esc(it.taskType.replace(/_/g, ' '))} · Q${idx + 1}</div>
        <p class="q-prompt">${TP.esc(it.prompt)}</p>
        ${it.bodyText ? '<div class="q-body">' + TP.esc(it.bodyText) + '</div>' : ''}
        ${verdict} ${answerHtml}
        ${it.correctAnswer && it.choices.length ? '<p style="margin:8px 0 0;font-size:14px"><strong>Correct answer: ' + TP.esc(it.correctAnswer) + '</strong><span class="ko">정답: ' + TP.esc(it.correctAnswer) + '</span></p>' : ''}
        ${it.explanation ? '<p style="margin:8px 0 0;font-size:14px;color:var(--muted)">💡 ' + TP.esc(it.explanation) + '</p>' : ''}
      </div>`;
    }).join('')
      + '<div style="display:flex;gap:10px;margin:18px 0 40px"><a class="btn btn-primary" href="/testprep/learning">Practice wrong words again</a>'
      + '<a class="btn btn-outline" href="/testprep/scores">Back to My Scores</a></div>';
  }
  boot();
})();
