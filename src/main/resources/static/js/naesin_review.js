/*
 * 내신 실전 연습 채점 화면(/naesintest/review?attempt=ID)
 * 빨간 펜 점수, 문항별 O/X 표, 틀린 문제(기본) 또는 전체 문항 해설로 복습한다.
 */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  let data = null, filter = 'wrong', examCode = null;


  function itemHtml(it, n) {
    const state = it.isCorrect ? 'is-right' : 'is-wrong';
    const mine = it.yourAnswer ? NT.circ(it.yourAnswer) : '안 씀';
    return '<article class="nt-item ' + state + '" id="q' + n + '">'
      + '<div class="nt-item-head"><span class="nt-qno" aria-label="' + n + '번 ' + (it.isCorrect ? '맞음' : '틀림') + '">' + n + '</span>'
      + '<span class="nt-ans">내 답 <b>' + NT.esc(mine) + '</b></span>'
      + '<span class="nt-ans">정답 <b class="nt-right">' + NT.esc(NT.circ(it.correctAnswer)) + '</b></span>'
      + (it.points != null ? '<span class="nt-pts">' + it.points + '점</span>' : '') + '</div>'
      + '<div class="nt-sheet">'
      + NT.questionHtml(it, n, examCode, data.examTitle)
      + '</div>'
      + '<div class="nt-exp">' + NT.expHtml(it.explanation) + '</div></article>';
  }

  function render() {
    const items = data.items;
    const sec = data.sections && data.sections[0];
    const right = items.filter(i => i.isCorrect).length;
    const wrong = items.length - right;
    const blank = items.filter(i => !i.yourAnswer).length;
    const shown = items.map((it, k) => [it, k + 1]).filter(([it]) => filter === 'all' || !it.isCorrect);
    $('review').innerHTML =
      '<div class="nt-paperhead"><h1 class="nt-paperhead-title">' + NT.esc(data.examTitle) + '<span class="nt-paperhead-sub">채점 결과</span></h1></div>'
      + '<div class="nt-score">'
      + '<div class="nt-score-pen" aria-label="점수 ' + data.totalScaled + '점, 만점 ' + data.totalMax + '점"><b>' + (data.totalScaled == null ? '-' : data.totalScaled) + '</b><span>/ ' + (data.totalMax == null ? '-' : data.totalMax) + '</span></div>'
      + '<div class="nt-score-text"><p class="nt-score-big">' + items.length + '문항 중 ' + right + '문항 맞음 · ' + wrong + '문항 틀림</p>'
      + (blank ? '<p>답하지 않은 문항 ' + blank + '개는 틀린 것으로 채점했습니다.</p>' : '')
      + (data.overtime ? '<p>시험 시간이 끝난 뒤 제출되었습니다.</p>' : '')
      + '<p><small>' + NT.esc(data.bandDisclaimer || '') + (sec && sec.accuracy != null ? ' 정답률 ' + sec.accuracy + '%' : '') + '</small></p></div></div>'
      + '<section class="nt-ox" aria-labelledby="nt-ox-t"><h2 id="nt-ox-t">문항별 채점</h2><div class="nt-ox-grid">'
      + items.map((it, k) => '<button type="button" class="nt-ox-cell' + (it.yourAnswer ? '' : ' is-blank') + '" data-jump="' + (k + 1) + '" aria-label="' + (k + 1) + '번 ' + (it.isCorrect ? '맞음' : '틀림') + '">' + (k + 1) + '<span>' + (it.isCorrect ? '○' : '╱') + '</span></button>').join('')
      + '</div></section>'
      + '<div class="nt-filter" role="group" aria-label="복습할 문항">'
      + '<button type="button" data-filter="wrong" aria-pressed="' + (filter === 'wrong') + '">틀린 문제 ' + wrong + '</button>'
      + '<button type="button" data-filter="all" aria-pressed="' + (filter === 'all') + '">전체 ' + items.length + '</button></div>'
      + '<section class="nt-review-list">'
      + (shown.length ? shown.map(([it, n]) => itemHtml(it, n)).join('') : '<p class="nt-msg">틀린 문제가 없습니다. 전체 문항 해설은 위 “전체”에서 볼 수 있습니다.</p>')
      + '</section>'
      + '<div class="nt-review-actions"><button type="button" class="nt-btn" id="nt-again">이 회차 다시 풀기</button><a class="nt-btn nt-btn-line" id="nt-list" href="' + NT.pageBase() + '">' + (NT.isApp() ? '내 시험 목록' : '다른 회차 고르기') + '</a></div>';

    $('review').querySelectorAll('[data-filter]').forEach(b => b.addEventListener('click', () => { filter = b.dataset.filter; render(); }));
    $('review').querySelectorAll('[data-jump]').forEach(b => b.addEventListener('click', () => {
      const n = Number(b.dataset.jump);
      if (filter === 'wrong' && items[n - 1].isCorrect) { filter = 'all'; render(); }
      const el = $('q' + n); if (el) el.scrollIntoView({ block: 'start' });
    }));
    $('nt-again').addEventListener('click', again);
    if (examCode) { $('nt-list').href = NT.listUrl(examCode); const back = $('nt-back'); if (back) back.href = NT.listUrl(examCode); }
  }

  async function again() {
    $('nt-again').disabled = true;
    try {
      const a = await NT.api(NT.attemptsApi(), { method: 'POST', body: JSON.stringify({ examCode }) });
      location.href = NT.pageBase() + '/run?attempt=' + a.attemptId;
    } catch (e) { $('nt-again').disabled = false; }
  }

  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { location.href = NT.pageBase(); return; }
    try { data = await NT.api(NT.attemptsApi() + '/' + encodeURIComponent(id) + '/review'); }
    catch (e) {
      $('review').innerHTML = '<p class="nt-msg">채점 결과를 열 수 없습니다. ' + NT.esc(e.message) + '</p><p class="nt-msg"><a class="nt-btn" href="' + NT.pageBase() + '">목록으로</a></p>';
      return;
    }
    if (data.kind !== 'NAESIN') { location.replace(NT.pageBase()); return; }
    // 채점 결과에는 시험 코드가 없어 응시 기록에서 찾는다(학교 페이지로 돌아가기·다시 풀기에 쓴다).
    try { const me = (await NT.api(NT.attemptsApi())).find(h => h.attemptId === data.attemptId); examCode = me && me.examCode; } catch (e) { /* 목록 주소는 기본값 */ }
    data.items.sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
    if (!data.items.some(i => !i.isCorrect)) filter = 'all';
    document.title = '채점 결과 · ' + data.examTitle;
    render();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
