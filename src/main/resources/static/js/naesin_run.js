/*
 * 내신 실전 연습 시험 화면(/naesintest/run?attempt=ID)
 * - 실제 시험처럼 풀이 중에는 해설이 없다. 시간은 문항당 30초(40문항 20분, 70문항 35분).
 * - 답은 시험지 아래 ①~⑤ 또는 오른쪽 OMR 답안지에서 고르고, 기존 /api/testprep 응시 API로 저장·제출한다.
 * - 시간이 끝나면 자동 제출하고 채점 화면(/naesintest/review)으로 간다.
 */
(function () {
  'use strict';
  const SEC_PER_QUESTION = 30;
  const SERVER_LIMIT_SEC = 7200; // V1116 tp_exams.time_limit_sec — 서버 남은 시간으로 경과 시간을 계산한다
  const $ = id => document.getElementById(id);

  let attempt = null, qs = [], answers = {}, marked = {}, idx = 0, limitSec = 0, startEpoch = 0, submitting = false;
  const dirty = new Set();

  // 답 저장(PUT)과 제출(POST)은 한 줄로 세워 하나씩 보낸다. 같은 문항을 동시에 저장하면 서버에서 중복 오류가 난다.
  const send = NT.serialize(fn => fn());
  const put = (qid, v) => send(() => NT.api(NT.attemptsApi() + '/' + attempt.attemptId + '/answers', { method: 'PUT', body: JSON.stringify({ questionId: qid, answerText: v }) }));
  async function flush(force) {
    if (submitting && !force) return; // 제출을 시작한 뒤에는 자동 저장을 멈춘다(제출 요청이 모든 답을 함께 보낸다).
    const ids = [...dirty]; dirty.clear();
    try { await Promise.all(ids.map(id => put(Number(id), answers[id] || null))); }
    catch (e) { ids.forEach(id => dirty.add(id)); notice('답안을 저장하지 못했습니다. 인터넷 연결을 확인하세요.'); }
  }
  function stateKey() { return 'f2r-naesin-' + attempt.attemptId; }
  function saveLocal() { try { localStorage.setItem(stateKey(), JSON.stringify({ idx, marked })); } catch (e) { /* 저장 안 됨 */ } }

  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { location.href = NT.pageBase(); return; }
    await NT.loadMe();
    try { attempt = await NT.api(NT.attemptsApi() + '/' + encodeURIComponent(id)); }
    catch (e) { $('paper').innerHTML = '<p class="nt-msg">시험을 열 수 없습니다. ' + NT.esc(e.message) + '</p><p class="nt-msg"><a class="nt-btn" href="' + NT.pageBase() + '">' + (NT.isApp() ? '내 시험 목록' : '학교 고르기') + '</a></p>'; return; }
    if (attempt.status === 'SUBMITTED') { location.replace(NT.pageBase() + '/review?attempt=' + attempt.attemptId); return; }
    if (attempt.kind !== 'NAESIN') { location.replace(NT.pageBase()); return; }
    qs = attempt.questions;
    qs.forEach(q => { answers[q.questionId] = attempt.savedAnswers[q.questionId] || null; });
    try { const s = JSON.parse(localStorage.getItem(stateKey()) || '{}'); idx = Math.min(s.idx || 0, qs.length - 1); marked = s.marked || {}; } catch (e) { /* 새로 시작 */ }
    limitSec = qs.length * SEC_PER_QUESTION;
    startEpoch = Date.now() - Math.max(0, SERVER_LIMIT_SEC - attempt.remainingSec) * 1000;
    $('nt-title').textContent = attempt.title;
    $('nt-count').textContent = qs.length + '문항';
    $('nt-subject').textContent = NT.subjectLabel(attempt.examCode);
    $('nt-grade').textContent = NT.gradeLabel(attempt.title);
    $('nt-limit').textContent = (limitSec / 60) + '분';
    document.title = attempt.title + ' · 내신 실전 연습';
    renderOmr(); render();
    wire();
    tick(); setInterval(tick, 1000);
    setInterval(flush, 10000);
    window.addEventListener('beforeunload', () => { if (dirty.size && !submitting) flush(); });
  }

  function left() { return limitSec - Math.floor((Date.now() - startEpoch) / 1000); }
  function tick() {
    if (submitting) return;
    const l = left();
    $('nt-timer').textContent = NT.fmtTime(l);
    $('nt-timer-box').classList.toggle('is-low', l <= 120);
    if (l <= 0) submit(true);
  }

  // 칠한 칸인지: 모두 고르기 문항은 답이 'B,D'처럼 쉼표로 이어진다.
  function isOn(q, letter) { const v = answers[q.questionId]; return !!v && v.split(',').indexOf(letter) >= 0; }

  function render() {
    const q = qs[idx];
    const multi = NT.isMulti(q);
    $('paper').innerHTML =
      '<div class="nt-qbar"><button type="button" class="nt-nav" id="nt-prev"' + (idx === 0 ? ' disabled' : '') + ' aria-label="이전 문항">이전</button>'
      + '<span class="nt-qpos"><b>' + (idx + 1) + '</b> / ' + qs.length + '</span>'
      + '<button type="button" class="nt-nav" id="nt-next"' + (idx === qs.length - 1 ? ' disabled' : '') + ' aria-label="다음 문항">다음</button>'
      + '<label class="nt-mark"><input type="checkbox" id="nt-mark"' + (marked[q.questionId] ? ' checked' : '') + '> 다시 볼 문제</label></div>'
      + '<div class="nt-sheet">'
      + NT.questionHtml(q, idx + 1, attempt.examCode, attempt.examTitle, '<p class="nt-msg">문항을 불러오지 못했습니다.</p>')
      + '</div>'
      // 문항 아래 답란: 실제 OMR 카드의 한 줄(번호 칸 + 세로로 긴 타원 ①~⑤)처럼 그린다.
      + '<div class="nt-pick" role="' + (multi ? 'group' : 'radiogroup') + '" aria-label="' + (idx + 1) + '번 답 고르기' + (multi ? '(모두 고르기)' : '') + '">'
      + '<div class="nt-pick-head"><span>번호</span><span>답 란</span></div>'
      + '<div class="nt-pick-row"><span class="nt-pick-no">' + (idx + 1) + '</span>'
      + q.choices.map((c, i) => '<button type="button" class="nt-ob' + (isOn(q, NT.letter(i)) ? ' is-on' : '') + '" role="' + (multi ? 'checkbox' : 'radio') + '" aria-checked="' + isOn(q, NT.letter(i)) + '" aria-label="' + NT.esc(c) + '" data-pick="' + NT.letter(i) + '">' + (i + 1) + '</button>').join('')
      + '</div>' + (multi ? '<p class="nt-pick-note">답을 모두 칠하세요. 칠한 칸을 다시 누르면 지워집니다.</p>' : '') + '</div>';
    $('paper').scrollTop = 0; window.scrollTo({ top: 0 });
    $('nt-prev').addEventListener('click', () => go(idx - 1));
    $('nt-next').addEventListener('click', () => go(idx + 1));
    $('nt-mark').addEventListener('change', e => { if (e.target.checked) marked[q.questionId] = true; else delete marked[q.questionId]; saveLocal(); renderOmr(); });
    $('paper').querySelectorAll('[data-pick]').forEach(b => b.addEventListener('click', () => pick(q, b.dataset.pick)));
    document.querySelectorAll('.nt-omr-row').forEach(r => r.classList.toggle('is-current', Number(r.dataset.i) === idx));
    const cur = document.querySelector('.nt-omr-row.is-current');
    if (cur && cur.scrollIntoView) cur.scrollIntoView({ block: 'nearest' });
  }

  // 실제 OMR처럼 이미 칠한 칸을 다시 눌러도 지워지지 않는다(두 번 눌러 답이 사라지는 일 방지). 바꾸려면 다른 칸을 누른다.
  // 모두 고르기 문항은 칸마다 칠하고 지울 수 있고, 답은 'B,D'처럼 기호 순서대로 저장한다.
  function pick(q, letter) {
    if (submitting) return;
    if (NT.isMulti(q)) {
      const set = new Set((answers[q.questionId] || '').split(',').filter(Boolean));
      if (set.has(letter)) set.delete(letter); else set.add(letter);
      answers[q.questionId] = set.size ? [...set].sort().join(',') : null;
    } else {
      if (answers[q.questionId] === letter) return;
      answers[q.questionId] = letter;
    }
    dirty.add(String(q.questionId));
    clearTimeout(pick.t); pick.t = setTimeout(flush, 600);
    renderOmr();
    if (qs[idx] === q) render();
  }

  function go(i) { if (i < 0 || i >= qs.length) return; idx = i; saveLocal(); render(); }

  function renderOmr() {
    const done = qs.filter(q => answers[q.questionId]).length;
    $('nt-omr-rows').innerHTML = qs.map((q, i) => '<div class="nt-omr-row' + (i === idx ? ' is-current' : '') + (marked[q.questionId] ? ' is-marked' : '') + '" data-i="' + i + '">'
      + '<button type="button" class="nt-omr-no" data-go="' + i + '" aria-label="' + (i + 1) + '번으로">' + (i + 1) + '</button>'
      + q.choices.map((c, k) => '<button type="button" class="nt-omr-b' + (isOn(q, NT.letter(k)) ? ' is-on' : '') + '" data-q="' + i + '" data-k="' + NT.letter(k) + '" aria-label="' + (i + 1) + '번 ' + c + '">' + (k + 1) + '</button>').join('')
      + '</div>').join('');
    $('nt-omr-done').textContent = done + ' / ' + qs.length;
    $('nt-omr-left').textContent = qs.length - done;
    $('nt-omr-toggle').textContent = '답안지 ' + done + '/' + qs.length;
    $('nt-omr-rows').querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => { go(Number(b.dataset.go)); closeOmrSheet(); }));
    $('nt-omr-rows').querySelectorAll('[data-q]').forEach(b => b.addEventListener('click', () => { const q = qs[Number(b.dataset.q)]; pick(q, b.dataset.k); }));
  }

  function confirmSubmit() {
    const blank = qs.filter(q => !answers[q.questionId]).length;
    const m = $('nt-confirm');
    $('nt-confirm-text').innerHTML = blank
      ? '아직 답하지 않은 문항이 <b>' + blank + '개</b> 있습니다. 그래도 제출할까요?'
      : '모든 문항에 답했습니다. 제출하면 답을 바꿀 수 없습니다.';
    m.hidden = false;
    $('nt-confirm-ok').focus();
  }

  async function submit(auto) {
    if (submitting) return;
    submitting = true;
    $('nt-confirm').hidden = true;
    $('paper').innerHTML = '<p class="nt-msg">' + (auto ? '시험 시간이 끝났습니다. ' : '') + '답안을 제출하고 채점하는 중입니다…</p>';
    try {
      await flush(true);
      const all = qs.map(q => ({ questionId: q.questionId, answerText: answers[q.questionId] }));
      await send(() => NT.api(NT.attemptsApi() + '/' + attempt.attemptId + '/submit', { method: 'POST', body: JSON.stringify({ answers: all }) }));
      try { localStorage.removeItem(stateKey()); } catch (e) { /* 없음 */ }
      location.replace(NT.pageBase() + '/review?attempt=' + attempt.attemptId);
    } catch (e) {
      submitting = false;
      $('paper').innerHTML = '<p class="nt-msg">제출하지 못했습니다: ' + NT.esc(e.message) + '</p><p class="nt-msg"><button type="button" class="nt-btn" id="nt-retry">다시 제출</button></p>';
      $('nt-retry').addEventListener('click', () => submit(false));
    }
  }

  function notice(text) {
    const n = $('nt-notice'); n.textContent = text; n.hidden = false;
    clearTimeout(notice.t); notice.t = setTimeout(() => { n.hidden = true; }, 4000);
  }
  function closeOmrSheet() { document.body.classList.remove('omr-open'); }

  function wire() {
    $('nt-submit').addEventListener('click', confirmSubmit);
    $('nt-confirm-cancel').addEventListener('click', () => { $('nt-confirm').hidden = true; });
    $('nt-confirm-ok').addEventListener('click', () => submit(false));
    $('nt-omr-toggle').addEventListener('click', () => document.body.classList.add('omr-open'));
    $('nt-omr-close').addEventListener('click', closeOmrSheet);
    $('nt-leave').addEventListener('click', async () => { await flush(); location.href = NT.listUrl(attempt.examCode); });
    document.addEventListener('keydown', e => {
      if (submitting || !$('nt-confirm').hidden || e.ctrlKey || e.metaKey || e.altKey) return;
      if (/^[1-5]$/.test(e.key)) { pick(qs[idx], NT.letter(Number(e.key) - 1)); e.preventDefault(); }
      else if (e.key === 'ArrowRight') { go(idx + 1); e.preventDefault(); }
      else if (e.key === 'ArrowLeft') { go(idx - 1); e.preventDefault(); }
    });
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
