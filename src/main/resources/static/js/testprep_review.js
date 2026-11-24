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
      rv = await TP.api(TP.attemptsApi() + '/' + id + '/review');
    } catch (err) { box.innerHTML = '<p class="lead">Cannot load review: ' + TP.esc(err.message) + '<span class="ko">복습 화면을 불러오지 못했습니다: ' + TP.esc(err.message) + '</span></p>'; return; }

    const titleEl = document.getElementById('rv-title');
    titleEl.innerHTML = TP.esc(rv.examTitle) + '<span class="ko">정답 복습</span>';
    document.getElementById('rv-sub').innerHTML =
      TP.esc((rv.submittedAt || '') + ' · ' + rv.items.length + ' questions' + (rv.overtime ? ' · submitted after time limit' : ''))
      + '<span class="ko">' + TP.esc(rv.submittedAt || '') + ' · 문항 ' + rv.items.length + '개' + (rv.overtime ? ' · 시간 제한 종료 후 제출' : '') + '</span>';

    const isSat = rv.kind === 'SAT' && window.SATRich;
    const isToeic = rv.kind === 'TOEIC';
    const rich = t => isSat ? '<div class="sat-prompt sat-review-rich">' + SATRich.render(t) + '</div>' : TP.esc(t);
    const richInline = t => isSat ? SATRich.inline(t) : TP.esc(t);
    const SEC = { RW: 'Reading and Writing', MATH: 'Math' };
    const koHelpHtml = raw => {
      let h = null;
      try { h = raw ? JSON.parse(raw) : null; } catch (e) { h = null; }
      if (!h) return '';
      return '<details class="rv-kohelp"><summary>한글 해설 보기 (' + TP.esc(h.translationTitle || '해석') + ' · 어휘 · 풀이 · 전략)</summary>'
        + (h.translation ? '<h4>' + TP.esc(h.translationTitle || '한글 해석') + '</h4>' + SATRich.render(h.translation) : '')
        + '<h4>영어 어휘</h4><table class="help-vocab"><tbody>' + (h.vocab || []).map(v => '<tr><th>' + TP.esc(v[0]) + '</th><td>' + TP.esc(v[1]) + '</td></tr>').join('') + '</tbody></table>'
        + '<h4>풀이 설명</h4>' + SATRich.render(h.solution || '')
        + '<h4>풀이 전략</h4>' + SATRich.render(h.strategy || '') + '</details>';
    };
    if (isSat) {
      // 오답만 다시 풀기(부분 응시)는 추정 점수가 없다(totalScaled = null) → 점수 자리에 정답 수를 보여 준다.
      const partial = rv.totalScaled == null;
      document.getElementById('rv-score').innerHTML =
        '<div class="stat-row">'
        + (partial
          ? '<div class="stat"><b>' + TP.correctCountText(rv.sections) + ' correct</b><span>wrong-answer retry — no score estimate<span class="ko">오답만 다시 풀기 — 정답 수(추정 점수 없음)</span></span></div>'
          : '<div class="stat"><b>' + rv.totalScaled + '</b><span>total (estimate, 400–1600)<span class="ko">합계(추정)</span></span></div>')
        + (rv.sections || []).map(s => '<div class="stat"><b>' + (s.scaled != null ? s.scaled : '—') + '</b><span>' + TP.esc(SEC[s.section] || s.section) + ' · ' + s.correct + '/' + s.autoCount + '</span></div>').join('')
        + '</div><p class="disclaimer">' + TP.esc(rv.bandDisclaimer) + '<span class="ko" style="color:#7a5300">F2R 내부 추정 점수 — College Board 공식 SAT 점수가 아닙니다.</span></p>';
    } else if (isToeic) {
      // TOEIC: 영역별 추정 점수(5~495)와 정답 수. ETS 환산표가 아니라 F2R 내부 추정치다.
      document.getElementById('rv-score').innerHTML =
        '<div class="stat-row">'
        + '<div class="stat"><b>' + TP.esc(TP.scaledText(rv.totalScaled, rv.totalMax)) + '</b><span>total (estimate)<span class="ko">합계(추정)</span></span></div>'
        + (rv.sections || []).map(s => '<div class="stat"><b>' + TP.esc(TP.scaledText(s.scaled, null)) + '</b><span>' + TP.esc(TP.TOEIC_SECTION_NAMES[s.section] || s.section) + ' · ' + s.correct + '/' + s.autoCount + '</span></div>').join('')
        + '</div><p class="disclaimer">' + TP.esc(rv.bandDisclaimer) + '<span class="ko" style="color:#7a5300">F2R 내부 추정 점수 — ETS 공식 TOEIC 점수가 아닙니다.</span></p>';
    } else
    document.getElementById('rv-score').innerHTML =
      '<div class="stat-row">'
      + '<div class="stat"><b>' + (rv.overallBand != null ? rv.overallBand : '—') + '</b><span>overall band (internal)<span class="ko">종합 밴드(내부)</span></span></div>'
      + '<div class="stat"><b>' + (rv.accuracy != null ? rv.accuracy + '%' : '—') + '</b><span>auto-score accuracy<span class="ko">자동 채점 정답률</span></span></div>'
      + (rv.sections || []).map(s => '<div class="stat"><b>' + (s.band != null ? s.band : '—') + '</b><span>' + TP.esc(s.section) + ' · ' + s.correct + '/' + s.autoCount + '</span></div>').join('')
      + '</div><p class="disclaimer">' + TP.esc(rv.bandDisclaimer) + '<span class="ko" style="color:#7a5300">TestPrep 내부 밴드 추정치 — 공식 ETS TOEFL iBT 점수가 아닙니다.</span></p>';

    // 지문·대본 칸(TOEIC·iBT). 듣기는 대본([[show]] 앞)을 글로, [[show]] 뒤의 표·그림은 시각자료로 보여 준다.
    const bodyHtml = it => {
      if (it.audioMode !== 'TTS') return '<div class="q-body">' + TP.richBody(TP.visibleBody(it)) + '</div>';
      const parts = TP.splitShown(it.bodyText);
      return '<div class="q-body">' + (isToeic ? '<strong>Script · 대본</strong>\n' : '') + TP.esc(parts.spoken) + '</div>'
        + (parts.shown ? '<div class="q-body q-visual">' + TP.richBody(parts.shown) + '</div>' : '');
    };
    // withBody = false: 묶음의 문항(지문·대본은 묶음 위에 한 번만 그렸다).
    const itemHtml = (it, idx, withBody) => {
      const verdict = !it.autoScore ? '<span class="tag neutral">manual review<span class="ko">수동 검토</span></span>'
        : it.isCorrect ? '<span class="tag good">correct<span class="ko">정답</span></span>' : '<span class="tag bad">wrong<span class="ko">오답</span></span>';
      let answerHtml = '';
      if (it.choices.length && it.taskType !== 'BUILD_SENTENCE') {
        answerHtml = '<div class="opts">' + it.choices.map((c, i) => {
          const isKey = it.correctAnswer === LETTERS[i];
          const isYours = it.yourAnswer === LETTERS[i];
          const cls = isKey ? 'sel' : '';
          return '<div class="opt ' + cls + '"><span><strong>' + LETTERS[i] + '.</strong> ' + richInline(c) + ' '
            + (isKey ? '✔ key / 정답' : '') + (isYours && !isKey ? ' (your answer / 당신의 답)' : '') + '</span></div>';
        }).join('') + '</div>';
      } else if (it.taskType === 'BUILD_SENTENCE') {
        answerHtml = '<p style="margin:6px 0"><strong>Your order:</strong> ' + TP.esc(it.yourAnswer || '(blank)') + '<span class="ko">당신의 배열: ' + TP.esc(it.yourAnswer || '(비어 있음)') + '</span></p>'
          + '<p style="margin:6px 0"><strong>Correct:</strong> ' + TP.esc(it.correctAnswer || '—') + '<span class="ko">정답: ' + TP.esc(it.correctAnswer || '—') + '</span></p>';
      } else if (it.taskType === 'SAT_MATH_SPR') {
        answerHtml = '<p style="margin:6px 0"><strong>Your answer:</strong> ' + TP.esc(it.yourAnswer || '(blank)') + '<span class="ko">입력한 답: ' + TP.esc(it.yourAnswer || '(비어 있음)') + '</span></p>'
          + '<p style="margin:6px 0"><strong>Accepted answers:</strong> ' + TP.esc((it.correctAnswer || '—').split('|').join(' or ')) + '<span class="ko">정답으로 인정하는 답</span></p>';
      } else {
        answerHtml = '<div class="q-body">' + TP.esc(it.yourAnswer || '(no response recorded)') + '<span class="ko">작성한 답변: ' + TP.esc(it.yourAnswer || '(기록된 응답 없음)') + '</span></div>';
      }
      return `<div class="q-card review-item ${it.autoScore ? (it.isCorrect ? 'correct' : 'wrong') : ''}">
        <div class="q-type">${isToeic ? TP.esc(TP.questionLabel(it, idx)) : TP.esc(isSat ? (SEC[it.section] || it.section) : it.section) + ' · ' + TP.esc(isSat ? (it.taskType === 'SAT_MATH_SPR' ? 'Student-produced response' : 'Multiple choice') : it.taskType.replace(/_/g, ' ')) + ' · Q' + (idx + 1)}</div>
        ${isSat && it.bodyText ? rich(it.bodyText) : ''}
        ${withBody && isToeic && it.bodyText ? bodyHtml(it) : ''}
        <div class="q-prompt">${isSat ? rich(it.prompt) : TP.esc(it.prompt)}</div>
        ${withBody && !isSat && !isToeic && it.bodyText ? bodyHtml(it) : ''}
        ${verdict} ${answerHtml}
        ${it.correctAnswer && it.choices.length ? '<p style="margin:8px 0 0;font-size:14px"><strong>Correct answer: ' + TP.esc(it.correctAnswer) + '</strong><span class="ko">정답: ' + TP.esc(it.correctAnswer) + '</span></p>' : ''}
        ${it.explanation ? '<p style="margin:8px 0 0;font-size:14px;color:var(--muted);white-space:pre-line">💡 ' + richInline(it.explanation) + '</p>' : ''}
        ${isSat ? koHelpHtml(it.koHelpJson) : ''}
      </div>`;
    };
    // 같은 지문·대본을 쓰는 이웃 문항(TP.groupQuestions)은 지문을 한 번만 보여 주고 그 아래에 문항별 답·해설을 둔다. SAT은 묶지 않는다.
    const groups = isSat ? rv.items.map((it, i) => ({ start: i, questions: [it] })) : TP.groupQuestions(rv.items);
    box.innerHTML = groups.map(g => g.questions.length === 1 ? itemHtml(g.questions[0], g.start, true)
      : '<div class="rv-group"><div class="q-card rv-passage"><div class="q-type">' + TP.esc(TP.groupLabel(g)) + '</div>' + bodyHtml(g.questions[0]) + '</div>'
        + g.questions.map((it, i) => itemHtml(it, g.start + i, false)).join('') + '</div>').join('')
      + '<div style="display:flex;gap:10px;margin:18px 0 40px">'
      + (TP.isApp() ? '<a class="btn btn-primary" href="/naesintest/app">앱 첫 화면으로</a>'
        : (isSat ? '<a class="btn btn-primary" href="/testprep/sat">Back to Digital SAT</a>'
          : isToeic ? '<a class="btn btn-primary" href="/testprep/toeic">Back to TOEIC</a>' : '<a class="btn btn-primary" href="/testprep/learning">Practice wrong words again</a>')
          + '<a class="btn btn-outline" href="/testprep/scores">Back to My Scores</a>')
      + '</div>';
  }
  boot();
})();
