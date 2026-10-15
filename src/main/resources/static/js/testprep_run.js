(function () {
  'use strict';

  const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
  const MC_TYPES = ['COMPLETE_THE_WORDS', 'READ_DAILY_LIFE', 'READ_PASSAGE', 'LISTEN_RESPONSE',
    'LISTEN_CONVERSATION', 'LISTEN_ANNOUNCEMENT', 'LISTEN_TALK', 'VOCAB_E2S', 'VOCAB_S2E', 'VOCAB_CLOZE'];

  let attempt = null;
  let questions = [];
  let answers = {}; // questionId -> value
  let dirty = new Set();
  let current = 0;
  let deadlineEpoch = 0;
  let timerId = null;
  let submitting = false;

  const stage = document.getElementById('stage');

  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { location.href = '/testprep/practice'; return; }
    await TP.loadMe();
    try {
      attempt = await TP.api('/api/testprep/attempts/' + id);
    } catch (err) {
      stage.innerHTML = '<p class="lead">Cannot open this attempt: ' + TP.esc(err.message) + '<span class="ko">이 응시를 열 수 없습니다: ' + TP.esc(err.message) + '</span></p>';
      return;
    }
    if (attempt.status === 'SUBMITTED') { location.href = '/testprep/review?attempt=' + attempt.attemptId; return; }
    questions = attempt.questions;
    answers = {};
    questions.forEach(q => { answers[q.questionId] = attempt.savedAnswers[q.questionId] || null; });
    deadlineEpoch = Date.now() + attempt.remainingSec * 1000;
    document.getElementById('exam-title').textContent = attempt.title;
    renderTabs();
    renderQuestion();
    startTimer();
    setInterval(flushDirty, 15000);
    window.addEventListener('beforeunload', () => { flushDirty().catch(() => {}); });
  }

  function startTimer() {
    const el = document.getElementById('timer');
    const tick = () => {
      const left = Math.max(0, Math.floor((deadlineEpoch - Date.now()) / 1000));
      const m = String(Math.floor(left / 60)).padStart(2, '0');
      const s = String(left % 60).padStart(2, '0');
      el.textContent = m + ':' + s;
      el.classList.toggle('low', left <= 120);
      if (left <= 0 && !submitting) {
        submitting = true;
        flushDirty().then(() => doSubmit(true)).catch(() => {});
      }
    };
    tick();
    timerId = setInterval(tick, 1000);
  }

  function sections() {
    const seen = [];
    questions.forEach(q => { if (!seen.includes(q.section)) seen.push(q.section); });
    return seen;
  }

  function renderTabs() {
    const tabs = document.getElementById('sec-tabs');
    tabs.innerHTML = sections().map(s => '<button class="sec-tab" data-sec="' + TP.esc(s) + '">' + TP.esc(s) + '</button>').join('');
    tabs.querySelectorAll('[data-sec]').forEach(b => b.addEventListener('click', () => {
      const first = questions.findIndex(q => q.section === b.dataset.sec);
      if (first >= 0) { current = first; renderQuestion(); }
    }));
  }

  function markTab() {
    document.querySelectorAll('.sec-tab').forEach(b => b.classList.toggle('active', b.dataset.sec === questions[current].section));
    const done = Object.entries(answers).filter(([, v]) => v !== null && v !== undefined && v !== '').length;
    document.getElementById('progress').textContent = done + ' / ' + questions.length + ' answered';
  }

  function isMC(q) { return q.choices.length > 0 && MC_TYPES.includes(q.taskType); }

  function renderQuestion() {
    const q = questions[current];
    if (!q) return;
    let inner = '<div class="q-card"><div class="q-type">' + TP.esc(q.section) + ' · ' + TP.esc(q.taskType.replace(/_/g, ' '))
      + ' · Q' + (current + 1) + '/' + questions.length + '</div>'
      + '<p class="q-prompt">' + TP.esc(q.prompt) + '</p>';

    if (q.audioMode === 'TTS' && q.bodyText) {
      inner += '<div class="audio-row"><button class="btn btn-outline btn-sm" data-tts>▶ Play audio</button>'
        + '<span class="saved-note">Practice mode: browser speech. The real test uses recorded audio.<span class="ko">연습 모드: 브라우저 음성입니다. 실제 시험에서는 녹음된 음성을 사용합니다.</span></span></div>';
    } else if (q.bodyText) {
      inner += '<div class="q-body">' + TP.esc(q.bodyText) + '</div>';
    }

    if (isMC(q)) {
      inner += '<div class="opts">' + q.choices.map((c, i) => `
        <label class="opt ${answers[q.questionId] === LETTERS[i] ? 'sel' : ''}" data-letter="${LETTERS[i]}">
          <input type="radio" name="q${q.questionId}" value="${LETTERS[i]}" ${answers[q.questionId] === LETTERS[i] ? 'checked' : ''}>
          <span><strong>${LETTERS[i]}.</strong> ${TP.esc(c)}</span></label>`).join('') + '</div>';
    } else if (q.taskType === 'BUILD_SENTENCE') {
      inner += buildSentenceHtml(q);
    } else if (q.taskType === 'WRITE_EMAIL' || q.taskType === 'WRITE_DISCUSSION') {
      inner += '<textarea class="answer" id="wta" placeholder="Type your response… / 답변을 입력하세요…">' + TP.esc(answers[q.questionId] || '') + '</textarea>'
        + '<p class="saved-note" id="wc"></p>'
        + '<p class="saved-note">Writing is stored for manual tutor review — TestPrep does not auto-score essays, and no AI score is shown.<span class="ko">쓰기 답변은 강사의 수동 검토를 위해 저장됩니다 — TestPrep은 에세이를 자동 채점하지 않으며 AI 점수도 표시하지 않습니다.</span></p>';
    } else if (q.taskType === 'SPEAK_REPEAT' || q.taskType === 'SPEAK_INTERVIEW') {
      inner += '<div class="audio-row">'
        + '<button class="btn btn-outline btn-sm" id="rec-btn">● Start recording</button>'
        + '<span class="saved-note" id="rec-state"></span></div>'
        + '<audio id="playback" controls ' + (answers[q.questionId] ? '' : 'hidden ') + 'src="' + TP.esc(answers[q.questionId] || '') + '"></audio>'
        + '<p class="saved-note">Recording is saved for your tutor\'s manual review — TestPrep does not auto-score speaking.<span class="ko">녹음 파일은 강사의 수동 검토를 위해 저장됩니다 — TestPrep은 말하기를 자동 채점하지 않습니다.</span></p>';
    } else if (q.choices.length > 0) {
      inner += '<div class="opts">' + q.choices.map((c, i) => `
        <label class="opt" data-letter="${LETTERS[i]}"><input type="radio" name="q${q.questionId}" value="${LETTERS[i]}">
        <span><strong>${LETTERS[i]}.</strong> ${TP.esc(c)}</span></label>`).join('') + '</div>';
    }

    inner += '<p class="saved-note" id="save-note"></p></div>'
      + '<div class="q-pager"><button class="btn btn-outline" id="prev"' + (current === 0 ? ' disabled' : '') + '>← Previous</button>'
      + '<button class="btn btn-primary" id="next">' + (current === questions.length - 1 ? 'Review &amp; Submit' : 'Next →') + '</button></div>';

    stage.innerHTML = inner;
    bindQuestion(q);
    markTab();
  }

  function buildSentenceHtml(q) {
    const saved = (answers[q.questionId] || '').split(/\s+/).filter(Boolean);
    const pool = q.choices.slice();
    saved.forEach(w => { const i = pool.indexOf(w); if (i >= 0) pool.splice(i, 1); });
    return '<div class="sentence-box" id="sbox">' + saved.map(w => '<button class="chip" data-placed="' + TP.esc(w) + '">' + TP.esc(w) + '</button>').join('') + '</div>'
      + '<div class="chips" id="cpool">' + pool.map(w => '<button class="chip" data-word="' + TP.esc(w) + '">' + TP.esc(w) + '</button>').join('') + '</div>'
      + '<p class="saved-note">Tap words in order. Tap a placed word to remove it. End the sentence with its punctuation word.<span class="ko">단어를 순서대로 탭하세요. 배치한 단어를 탭하면 제거됩니다. 문장부호 단어로 문장을 마무리하세요.</span></p>';
  }

  function bindQuestion(q) {
    const tts = stage.querySelector('[data-tts]');
    if (tts) tts.addEventListener('click', () => speak(q.bodyText));

    stage.querySelectorAll('.opt input').forEach(r => r.addEventListener('change', () => {
      setAnswer(q.questionId, r.value);
      stage.querySelectorAll('.opt').forEach(o => o.classList.toggle('sel', o.contains(r) && r.checked));
    }));

    const wta = document.getElementById('wta');
    if (wta) {
      const wc = document.getElementById('wc');
      wta.addEventListener('input', () => { wc.textContent = wta.value.trim().split(/\s+/).filter(Boolean).length + ' words'; setAnswer(q.questionId, wta.value); });
      wc.textContent = (answers[q.questionId] || '').trim().split(/\s+/).filter(Boolean).length + ' words';
    }

    const sbox = document.getElementById('sbox');
    if (sbox) {
      sbox.parentElement.querySelectorAll('.chip').forEach(bindChip);
      setAnswer(q.questionId, currentSentence(sbox), true);
    }

    const recBtn = document.getElementById('rec-btn');
    if (recBtn) bindRecorder(q, recBtn);

    document.getElementById('prev').addEventListener('click', () => { flushDirty(); if (current > 0) { current--; renderQuestion(); } });
    document.getElementById('next').addEventListener('click', () => {
      flushDirty();
      if (current < questions.length - 1) { current++; renderQuestion(); } else { submitFlow(); }
    });
  }

  function bindChip(chip) {
    chip.addEventListener('click', () => {
      const sbox = document.getElementById('sbox');
      const cpool = document.getElementById('cpool');
      if (chip.dataset.placed !== undefined) {
        const back = document.createElement('button');
        back.className = 'chip'; back.dataset.word = chip.dataset.placed; back.textContent = chip.dataset.placed;
        cpool.appendChild(back); bindChip(back); chip.remove();
      } else {
        const placed = document.createElement('button');
        placed.className = 'chip'; placed.dataset.placed = chip.dataset.word; placed.textContent = chip.dataset.word;
        sbox.appendChild(placed); bindChip(placed); chip.remove();
      }
      const q = questions[current];
      setAnswer(q.questionId, currentSentence(sbox));
    });
  }

  function currentSentence(sbox) {
    return Array.from(sbox.querySelectorAll('.chip')).map(c => c.dataset.placed).join(' ');
  }

  function bindRecorder(q, btn) {
    const state = document.getElementById('rec-state');
    let rec = null, chunks = [], recording = false;
    if (!navigator.mediaDevices || !window.MediaRecorder) {
      state.textContent = 'Recording is not supported in this browser — ask your tutor about alternatives.';
      btn.disabled = true; return;
    }
    btn.addEventListener('click', async () => {
      if (!recording) {
        try {
          const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
          chunks = [];
          rec = new MediaRecorder(stream);
          rec.ondataavailable = e => chunks.push(e.data);
          rec.onstop = async () => {
            stream.getTracks().forEach(t => t.stop());
            const blob = new Blob(chunks, { type: 'audio/webm' });
            if (blob.size > 3_000_000) { state.textContent = 'Recording too long (>90s?) — please re-record shorter.'; return; }
            const reader = new FileReader();
            reader.onload = () => {
              setAnswer(q.questionId, reader.result);
              const a = document.getElementById('playback');
              a.src = reader.result; a.hidden = false;
              state.textContent = 'Saved ✓ ' + Math.round(blob.size / 1024) + ' KB';
            };
            reader.readAsDataURL(blob);
          };
          rec.start();
          recording = true;
          btn.textContent = '■ Stop recording';
          state.textContent = 'Recording… (max 90 s)';
          setTimeout(() => { if (recording) rec.stop(); }, 90000);
        } catch (e) { state.textContent = 'Microphone permission denied.'; }
      } else {
        recording = false;
        btn.textContent = '● Start recording';
        rec.stop();
      }
    });
  }

  function speak(text) {
    if (!('speechSynthesis' in window)) { alert('This browser cannot speak audio.'); return; }
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'en-US'; u.rate = 0.95;
    window.speechSynthesis.speak(u);
  }

  function setAnswer(questionId, value, silent) {
    answers[questionId] = value;
    dirty.add(questionId);
    markTab();
    if (!silent) scheduleFlush();
  }

  let flushTimer = null;
  function scheduleFlush() { clearTimeout(flushTimer); flushTimer = setTimeout(flushDirty, 600); }

  async function flushDirty() {
    if (!dirty.size || submitting) return;
    const ids = Array.from(dirty);
    for (const id of ids) {
      dirty.delete(id);
      try {
        await TP.api('/api/testprep/attempts/' + attempt.attemptId + '/answers', {
          method: 'PUT', body: JSON.stringify({ questionId: id, answerText: answers[id] }) });
        const note = document.getElementById('save-note');
        if (note) note.textContent = 'Answer saved';
      } catch (e) { dirty.add(id); return; }
    }
  }

  async function submitFlow() {
    submitting = false;
    await flushDirty();
    const unanswered = questions.filter(q => !answers[q.questionId]).length;
    if (unanswered > 0 && !confirm(unanswered + ' question(s) are unanswered. Submit anyway?\n\n미답변 문항이 ' + unanswered + '개 있습니다. 그래도 제출할까요?')) { submitting = false; return; }
    submitting = true;
    doSubmit(false);
  }

  async function doSubmit(auto) {
    try {
      const card = await TP.api('/api/testprep/attempts/' + attempt.attemptId + '/submit', { method: 'POST', body: JSON.stringify({}) });
      clearInterval(timerId);
      showScorecard(card, auto);
    } catch (e) { submitting = false; alert('Submit failed: ' + e.message + '\n제출 실패: ' + e.message); }
  }

  function showScorecard(card, auto) {
    const rows = (card.sections || []).map(s =>
      '<tr><td>' + TP.esc(s.section) + '</td><td>' + (s.band != null ? 'Band ' + s.band : 'manual review')
      + '</td><td>' + (s.accuracy != null ? s.accuracy + '%' : '—')
      + '</td><td>' + s.correct + '/' + s.autoCount + ' auto · ' + s.totalCount + ' total</td></tr>').join('');
    stage.innerHTML = `<div class="q-card" style="text-align:center">
      <div class="q-type">${auto ? 'Auto-submitted at time limit' : 'Submitted'}</div>
      <div class="band-big">${card.overallBand != null ? 'Band ' + card.overallBand : 'No auto score'}</div>
      <p class="lead">overall internal estimate${card.accuracy != null ? ' · accuracy ' + card.accuracy + '%' : ''}<span class="ko">종합 내부 추정치${card.accuracy != null ? ' · 정답률 ' + card.accuracy + '%' : ''}</span></p>
      <table class="tp" style="margin:16px 0"><tr><th>Section<span class="ko">영역</span></th><th>Band<span class="ko">밴드</span></th><th>Accuracy<span class="ko">정답률</span></th><th>Auto-scored<span class="ko">자동 채점</span></th></tr>${rows}</table>
      <p class="disclaimer" style="display:inline-block">${TP.esc(card.bandDisclaimer)}<span class="ko" style="color:#7a5300">TestPrep 내부 밴드 추정치 — 공식 ETS TOEFL iBT 점수가 아닙니다.</span></p>
      <div style="margin-top:16px;display:flex;gap:10px;justify-content:center">
        <a class="btn btn-primary" href="/testprep/review?attempt=${card.attemptId}">Review answers &amp; explanations</a>
        <a class="btn btn-outline" href="/testprep/scores">My Scores</a>
      </div></div>`;
    document.getElementById('btn-submit').disabled = true;
  }

  document.getElementById('btn-submit').addEventListener('click', submitFlow);
  boot();
})();
