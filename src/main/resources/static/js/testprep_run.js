(function () {
  'use strict';

  const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
  const MC_TYPES = ['COMPLETE_THE_WORDS', 'READ_DAILY_LIFE', 'READ_PASSAGE', 'LISTEN_RESPONSE',
    'LISTEN_CONVERSATION', 'LISTEN_ANNOUNCEMENT', 'LISTEN_TALK', 'VOCAB_E2S', 'VOCAB_S2E', 'VOCAB_CLOZE'];

  let attempt = null;
  let questions = [];
  let groups = []; // 한 화면에 함께 보이는 문항 묶음(TP.groupQuestions) — 이전/다음은 묶음 단위로 움직인다.
  let answers = {}; // questionId -> value
  let dirty = new Set();
  let current = 0;
  let deadlineEpoch = 0;
  let timerId = null;
  let submitting = false;

  const stage = document.getElementById('stage');
  const PLAYED_TEXT = '✓ Already played · 이미 재생했습니다';

  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { location.href = TP.homeUrl('/testprep/practice'); return; }
    await TP.loadMe();
    try {
      attempt = await TP.api(TP.attemptsApi() + '/' + id);
    } catch (err) {
      stage.innerHTML = '<p class="lead">Cannot open this attempt: ' + TP.esc(err.message) + '<span class="ko">이 응시를 열 수 없습니다: ' + TP.esc(err.message) + '</span></p>';
      return;
    }
    if (attempt.status === 'SUBMITTED') { location.href = TP.reviewUrl(attempt.attemptId); return; }
    if (attempt.kind === 'SAT') { location.href = TP.runUrl('SAT', attempt.attemptId); return; }
    questions = attempt.questions;
    groups = TP.groupQuestions(questions);
    answers = {};
    questions.forEach(q => { answers[q.questionId] = attempt.savedAnswers[q.questionId] || null; });
    deadlineEpoch = Date.now() + attempt.remainingSec * 1000;
    document.getElementById('exam-title').textContent = attempt.title;
    TP.markNav(document, TP.navHref(attempt.kind));
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

  // 탭 묶음: TOEIC은 Part별, 그 밖은 영역별(TP.groupOf).
  function sections() {
    const seen = [];
    questions.forEach(q => { const g = TP.groupOf(q); if (!seen.includes(g)) seen.push(g); });
    return seen;
  }

  function renderTabs() {
    const tabs = document.getElementById('sec-tabs');
    tabs.innerHTML = sections().map(s => '<button class="sec-tab" data-sec="' + TP.esc(s) + '">' + TP.esc(s) + '</button>').join('');
    tabs.querySelectorAll('[data-sec]').forEach(b => b.addEventListener('click', () => {
      const first = questions.findIndex(q => TP.groupOf(q) === b.dataset.sec);
      if (first >= 0) { current = first; renderQuestion(); }
    }));
  }

  function markTab() {
    document.querySelectorAll('.sec-tab').forEach(b => b.classList.toggle('active', b.dataset.sec === TP.groupOf(questions[current])));
    const done = Object.entries(answers).filter(([, v]) => v !== null && v !== undefined && v !== '').length;
    document.getElementById('progress').textContent = done + ' / ' + questions.length + ' answered';
  }

  function isMC(q) { return q.choices.length > 0 && (MC_TYPES.includes(q.taskType) || TP.toeicPart(q.taskType) != null); }

  // 듣기 칸: 재생 버튼 하나 + ([[show]] 뒤의 표·그림이 있으면) 시각자료. 대본은 보여 주지 않는다.
  // TOEIC은 묶음당 한 번만 재생한다(TP.playsOnce) — 이미 재생했으면 버튼이 꺼져 있다.
  function listenHtml(q) {
    const once = TP.playsOnce(q);
    const played = once && TP.isPlayed(TP.playedKey(attempt.attemptId, q.questionId));
    const shown = TP.visibleBody(q);
    return '<div class="audio-row"><button class="btn btn-outline btn-sm" data-tts' + (played ? ' disabled' : '') + '>' + (played ? PLAYED_TEXT : '▶ Play audio') + '</button>'
      + '<span class="saved-note">Practice mode: browser speech. The real test uses recorded audio.<span class="ko">연습 모드: 브라우저 음성입니다. 실제 시험에서는 녹음된 음성을 사용합니다.</span>'
      + (once ? '<span class="ko" id="tts-note">' + (played ? '이미 재생했습니다.' : '실제 시험처럼 한 번만 재생됩니다.') + '</span>' : '')
      + '</span></div>'
      + (shown ? '<div class="q-body q-visual">' + TP.richBody(shown) + '</div>' : '');
  }

  function passageHtml(q) {
    return '<div class="q-body">' + TP.richBody(TP.visibleBody(q)) + '</div>';
  }

  // 문항의 답 칸(선택지·문장 만들기·쓰기·말하기).
  function answerHtml(q) {
    if (isMC(q)) {
      return '<div class="opts">' + q.choices.map((c, i) => `
        <label class="opt ${answers[q.questionId] === LETTERS[i] ? 'sel' : ''}" data-letter="${LETTERS[i]}" data-q="${q.questionId}">
          <input type="radio" name="q${q.questionId}" value="${LETTERS[i]}" data-q="${q.questionId}" ${answers[q.questionId] === LETTERS[i] ? 'checked' : ''}>
          <span><strong>${LETTERS[i]}.</strong> ${TP.esc(c)}</span></label>`).join('') + '</div>';
    } else if (q.taskType === 'BUILD_SENTENCE') {
      return buildSentenceHtml(q);
    } else if (q.taskType === 'WRITE_EMAIL' || q.taskType === 'WRITE_DISCUSSION') {
      return '<textarea class="answer" id="wta" placeholder="Type your response… / 답변을 입력하세요…">' + TP.esc(answers[q.questionId] || '') + '</textarea>'
        + '<p class="saved-note" id="wc"></p>'
        + '<p class="saved-note">Writing is stored for manual tutor review — TestPrep does not auto-score essays, and no AI score is shown.<span class="ko">쓰기 답변은 강사의 수동 검토를 위해 저장됩니다 — TestPrep은 에세이를 자동 채점하지 않으며 AI 점수도 표시하지 않습니다.</span></p>';
    } else if (q.taskType === 'SPEAK_REPEAT' || q.taskType === 'SPEAK_INTERVIEW') {
      return '<div class="audio-row">'
        + '<button class="btn btn-outline btn-sm" id="rec-btn">● Start recording</button>'
        + '<span class="saved-note" id="rec-state"></span></div>'
        + '<audio id="playback" controls ' + (answers[q.questionId] ? '' : 'hidden ') + 'src="' + TP.esc(answers[q.questionId] || '') + '"></audio>'
        + '<p class="saved-note">Recording is saved for your tutor\'s manual review — TestPrep does not auto-score speaking.<span class="ko">녹음 파일은 강사의 수동 검토를 위해 저장됩니다 — TestPrep은 말하기를 자동 채점하지 않습니다.</span></p>';
    } else if (q.choices.length > 0) {
      return '<div class="opts">' + q.choices.map((c, i) => `
        <label class="opt" data-letter="${LETTERS[i]}" data-q="${q.questionId}"><input type="radio" name="q${q.questionId}" value="${LETTERS[i]}" data-q="${q.questionId}">
        <span><strong>${LETTERS[i]}.</strong> ${TP.esc(c)}</span></label>`).join('') + '</div>';
    }
    return '';
  }

  // 묶음이 아닌 문항 하나(기존 화면 그대로).
  function singleHtml(q) {
    // TOEIC 읽기(Part 6·7)는 실제 시험지처럼 지문을 먼저 보여 주고 그 아래에 질문을 둔다.
    const passageFirst = TP.toeicPart(q.taskType) != null && q.audioMode !== 'TTS' && q.bodyText;
    let inner = '<div class="q-card"><div class="q-type">' + TP.esc(TP.questionLabel(q, current, questions.length)) + '</div>'
      + (passageFirst ? passageHtml(q) : '')
      + '<p class="q-prompt">' + TP.esc(q.prompt) + '</p>';

    if (passageFirst) {
      // 지문은 위에서 이미 그렸다.
    } else if (q.audioMode === 'TTS' && q.bodyText) {
      inner += listenHtml(q);
    } else if (q.bodyText) {
      inner += passageHtml(q);
    }
    return inner + answerHtml(q) + '<p class="saved-note" id="save-note"></p></div>';
  }

  // 묶음(같은 지문·대본을 쓰는 이웃 문항들)을 한 화면에: 지문(읽기) 또는 재생 버튼 하나(듣기) + 문항들.
  // 읽기 묶음은 넓은 화면에서 왼쪽 지문 / 오른쪽 문항 2단이다(testprep.css의 .q-group-read).
  function groupHtml(group) {
    const first = group.questions[0];
    const listening = first.audioMode === 'TTS';
    return '<div class="q-group ' + (listening ? 'q-group-listen' : 'q-group-read') + '"><div class="q-group-passage">'
      + (listening
        ? '<div class="q-card"><div class="q-type">' + TP.esc(TP.groupLabel(group, questions.length)) + '</div>' + listenHtml(first) + '</div>'
        : passageHtml(first))
      + '</div><div class="q-group-items">'
      + group.questions.map((q, i) => '<div class="q-card"><div class="q-type">' + TP.esc(TP.questionLabel(q, group.start + i, questions.length)) + '</div>'
        + '<p class="q-prompt">' + TP.esc(q.prompt) + '</p>' + answerHtml(q) + '</div>').join('')
      + '<p class="saved-note" id="save-note"></p></div></div>';
  }

  // 지금 문항(current)이 들어 있는 묶음의 자리.
  function groupIndex() {
    return groups.findIndex(g => current >= g.start && current < g.start + g.questions.length);
  }

  function renderQuestion() {
    const gi = groupIndex();
    const group = groups[gi];
    if (!group) return;
    TP.stopSpeech(); // 문항·탭을 옮기면 앞 문항의 음성을 멈춘다.
    stage.innerHTML = (group.questions.length > 1 ? groupHtml(group) : singleHtml(group.questions[0]))
      + '<div class="q-pager"><button class="btn btn-outline" id="prev"' + (gi === 0 ? ' disabled' : '') + '>← Previous</button>'
      + '<button class="btn btn-primary" id="next">' + (gi === groups.length - 1 ? 'Review &amp; Submit' : 'Next →') + '</button></div>';
    // 읽기 묶음의 지문 칸이 위쪽 고정 머리(메뉴 + 시험 머리) 바로 아래에 붙도록 그 높이를 알려 준다.
    const head = document.querySelector ? document.querySelector('.run-head') : null;
    if (head && stage.style) stage.style.setProperty('--tp-sticky-top', Math.round(head.getBoundingClientRect().bottom + 10) + 'px');
    bindQuestion(group.questions[0]);
    markTab();
  }

  function buildSentenceHtml(q) {
    const saved = (answers[q.questionId] || '').split(/\s+/).filter(Boolean);
    const pool = TP.shuffleChips(q.choices, q.questionId); // 칩이 정답 순서대로 들어 있는 세트가 있어 화면에서는 섞어 보여 준다.
    saved.forEach(w => { const i = pool.indexOf(w); if (i >= 0) pool.splice(i, 1); });
    return '<div class="sentence-box" id="sbox">' + saved.map(w => '<button class="chip" data-placed="' + TP.esc(w) + '">' + TP.esc(w) + '</button>').join('') + '</div>'
      + '<div class="chips" id="cpool">' + pool.map(w => '<button class="chip" data-word="' + TP.esc(w) + '">' + TP.esc(w) + '</button>').join('') + '</div>'
      + '<p class="saved-note">Tap words in order. Tap a placed word to remove it. End the sentence with its punctuation word.<span class="ko">단어를 순서대로 탭하세요. 배치한 단어를 탭하면 제거됩니다. 문장부호 단어로 문장을 마무리하세요.</span></p>';
  }

  function bindQuestion(q) {
    const tts = stage.querySelector('[data-tts]');
    if (tts) tts.addEventListener('click', () => {
      const key = TP.playedKey(attempt.attemptId, q.questionId);
      if (TP.playsOnce(q) && TP.isPlayed(key)) return;
      if (!speak(q) || !TP.playsOnce(q)) return;
      // TOEIC: 한 번 재생하면 버튼을 끈다(재생 중 문항을 옮겨 멈춘 뒤에도 다시 재생할 수 없다).
      TP.markPlayed(key);
      tts.disabled = true;
      tts.textContent = PLAYED_TEXT;
      const note = document.getElementById('tts-note');
      if (note) note.textContent = '이미 재생했습니다.';
    });

    // 선택지는 문항별로(data-q) 답을 저장한다 — 묶음 화면에는 여러 문항의 선택지가 함께 있다.
    stage.querySelectorAll('.opt input').forEach(r => r.addEventListener('change', () => {
      setAnswer(Number(r.dataset.q), r.value);
      stage.querySelectorAll('.opt[data-q="' + r.dataset.q + '"]').forEach(o => o.classList.toggle('sel', o.contains(r) && r.checked));
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

    const go = gi => { current = groups[gi].start; renderQuestion(); if (window.scrollTo) window.scrollTo(0, 0); };
    document.getElementById('prev').addEventListener('click', () => { flushDirty(); const gi = groupIndex(); if (gi > 0) go(gi - 1); });
    document.getElementById('next').addEventListener('click', () => {
      flushDirty();
      const gi = groupIndex();
      if (gi < groups.length - 1) go(gi + 1); else submitFlow();
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

  // 대본을 화자 차례·문장 덩어리로 나눈 발화 목록(TP.speechQueue)을 순서대로 큐에 넣는다. 화자 꼬리표는 읽지 않는다.
  // 재생을 시작했으면 true, 음성을 지원하지 않는 브라우저면 false.
  function speak(q) {
    if (!('speechSynthesis' in window)) { alert('This browser cannot speak audio.'); return false; }
    window.speechSynthesis.cancel();
    TP.speechQueue(q).forEach(part => {
      const u = new SpeechSynthesisUtterance(part.text);
      u.lang = 'en-US'; u.rate = 0.95; u.pitch = part.pitch;
      window.speechSynthesis.speak(u);
    });
    return true;
  }

  function setAnswer(questionId, value, silent) {
    answers[questionId] = value;
    dirty.add(questionId);
    markTab();
    if (!silent) scheduleFlush();
  }

  let flushTimer = null;
  function scheduleFlush() { clearTimeout(flushTimer); flushTimer = setTimeout(flushDirty, 600); }

  // 저장 요청을 직렬화한다. 다음/제출 버튼이 flushDirty()를 await하지 않아도, 이후 submitFlow의 flushDirty()가
  // 앞선 저장이 끝날 때까지 기다리므로 제출(POST)이 마지막 답안 저장(PUT)과 겹치지 않는다.
  async function flushOnce() {
    if (!dirty.size || submitting) return;
    const ids = Array.from(dirty);
    for (const id of ids) {
      dirty.delete(id);
      try {
        await TP.api(TP.attemptsApi() + '/' + attempt.attemptId + '/answers', {
          method: 'PUT', body: JSON.stringify({ questionId: id, answerText: answers[id] }) });
        const note = document.getElementById('save-note');
        if (note) note.textContent = 'Answer saved';
      } catch (e) { dirty.add(id); return; }
    }
  }

  const flushDirty = TP.serialize(flushOnce);

  async function submitFlow() {
    submitting = false;
    await flushDirty();
    const unanswered = questions.filter(q => !answers[q.questionId]).length;
    if (unanswered > 0 && !confirm(unanswered + ' question(s) are unanswered. Submit anyway?\n\n미답변 문항이 ' + unanswered + '개 있습니다. 그래도 제출할까요?')) { submitting = false; return; }
    submitting = true;
    doSubmit(false);
  }

  async function doSubmit(auto) {
    TP.stopSpeech(); // 제출하면 읽던 음성을 멈춘다.
    try {
      const card = await TP.api(TP.attemptsApi() + '/' + attempt.attemptId + '/submit', { method: 'POST', body: JSON.stringify({}) });
      clearInterval(timerId);
      showScorecard(card, auto);
    } catch (e) { submitting = false; alert('Submit failed: ' + e.message + '\n제출 실패: ' + e.message); }
  }

  function showScorecard(card, auto) {
    if (card.kind === 'TOEIC') { showToeicScorecard(card, auto); return; }
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
        <a class="btn btn-primary" href="${TP.esc(TP.reviewUrl(card.attemptId))}">Review answers &amp; explanations</a>
        <a class="btn btn-outline" href="${TP.esc(TP.homeUrl('/testprep/scores'))}">${TP.isApp() ? '앱 첫 화면' : 'My Scores'}</a>
      </div></div>`;
    document.getElementById('btn-submit').disabled = true;
  }

  // TOEIC: 밴드 대신 영역별 추정 점수(5~495)와 정답 수를 보여 준다.
  function showToeicScorecard(card, auto) {
    stage.innerHTML = TP.toeicScorecardHtml(card, auto);
    document.getElementById('btn-submit').disabled = true;
  }

  document.getElementById('btn-submit').addEventListener('click', submitFlow);
  boot();
})();
