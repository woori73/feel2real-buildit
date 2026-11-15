/*
 * Digital SAT 연습 시험 화면 (/testprep/sat/run?attempt=ID)
 * - 답안: 기존 /api/testprep 응시 API로 저장·제출한다.
 * - 화면 상태(현재 모듈·문항, 모듈 시작 시각, 검토 표시, 지운 선택지, 형광펜·메모, 화면 비율)는 localStorage에 응시별로 둔다.
 * 설계: docs/design/testprep-digital-sat.md
 */
(function () {
  'use strict';

  const LETTERS = ['A', 'B', 'C', 'D'];
  const SECTION_NAME = { RW: 'Reading and Writing', MATH: 'Math' };
  const PER_QUESTION_SEC = { RW: 71, MATH: 95 }; // 실제 시험 문항당 평균 시간(32분/27문항, 35분/22문항)
  const WARN_SEC = 300;
  const BREAK_SEC = 600; // 섹션 사이 쉬는 시간(일찍 재개 가능)
  const SPR_ALLOWED = /^-?[0-9./]*$/;
  const R = window.SATRich;

  const DIRECTIONS = {
    RW: '<p>This module covers a range of reading and writing skills. Each question has one or more short passages, which may include a table. Read each passage and question carefully, then choose the single best answer based on the passage(s).</p>'
      + '<p>All questions in this module are multiple choice with four answer choices.</p>'
      + '<p class="ko">이 모듈은 읽기·쓰기 능력을 다룹니다. 각 문항의 짧은 지문(표 포함 가능)과 질문을 읽고 가장 알맞은 답 하나를 고르세요. 모든 문항은 4지선다입니다.</p>',
    MATH: '<p>This module covers a range of math skills. Most questions are multiple choice; some ask you to enter your own answer.</p>'
      + '<p>Use the Calculator and Reference buttons at the top whenever you need them. Unless a question says otherwise, all variables and expressions represent real numbers, figures are drawn to scale, and the domain of a function is all real numbers for which its value is a real number.</p>'
      + '<p class="ko">대부분 객관식이고 일부는 답을 직접 입력합니다. 계산기와 공식표는 위쪽 버튼으로 언제든 열 수 있습니다.</p>'
  };

  const SPR_GUIDE = '<h3>Student-produced response directions</h3><ul>'
    + '<li>If you find <b>more than one correct answer</b>, enter only one.</li>'
    + '<li>Enter up to <b>5 characters</b> for a positive answer and up to <b>6 characters</b> (including the negative sign) for a <b>negative</b> answer.</li>'
    + '<li>If your answer is a <b>fraction</b> that is too long, enter the decimal equivalent.</li>'
    + '<li>If your answer is a <b>decimal</b> that is too long, round or cut it off at the fourth digit.</li>'
    + '<li>If your answer is a <b>mixed number</b> such as 3½, enter it as an improper fraction (7/2) or a decimal (3.5).</li>'
    + '<li>Do not enter <b>symbols</b> such as a percent sign, comma, or dollar sign.</li></ul>'
    + '<table class="sat-table"><thead><tr><th>Answer</th><th>Acceptable ways to enter</th><th>Not accepted</th></tr></thead><tbody>'
    + '<tr><td>3.5</td><td>3.5 · 7/2</td><td>3 1/2</td></tr>'
    + '<tr><td>2/3</td><td>2/3 · .6666 · .6667 · 0.666 · 0.667</td><td>0.66 · .66 · 0.67 · .67</td></tr>'
    + '<tr><td>−1/4</td><td>−1/4 · −.25 · −0.25</td><td>−0.2</td></tr></tbody></table>'
    + '<p class="ko">답이 둘 이상이면 하나만 입력합니다. 양수는 5자, 음수는 부호 포함 6자까지 입력할 수 있습니다. 대분수는 가분수나 소수로 입력하고, %·쉼표·$ 기호는 넣지 않습니다.</p>';

  const SHORTCUTS = [
    ['Show or hide this list', 'F1'],
    ['Back / Next', 'Ctrl + Alt + B / Ctrl + Alt + X'],
    ['Open or close the question menu', 'Ctrl + Alt + G'],
    ['Mark for review', 'Ctrl + Alt + V'],
    ['Select option A–D', 'Ctrl + Shift + 1–4'],
    ['Turn cross-out mode on or off', 'Ctrl + Alt + O'],
    ['Cross out option A–D', 'Ctrl + Alt + 1–4'],
    ['Highlight selected text (or drag to choose a color)', 'Ctrl + H'],
    ['Line reader', 'Ctrl + L (move with ↑ ↓)'],
    ['Calculator / Reference (Math)', 'Ctrl + Alt + C / Ctrl + Alt + R'],
    ['Show or hide the timer', 'Ctrl + Alt + T'],
    ['해설(시간 멈춤) 열기·닫기', 'Ctrl + Alt + E'],
    ['Directions', 'Ctrl + Alt + Shift + D'],
    ['Close a menu or panel', 'Esc']
  ];

  const ICON = {
    mark: '<svg viewBox="0 0 16 20" width="13" height="16" aria-hidden="true"><path d="M2 1h12v18l-6-4.5L2 19z" fill="currentColor"/></svg>',
    markOff: '<svg viewBox="0 0 16 20" width="13" height="16" aria-hidden="true"><path d="M2 1h12v18l-6-4.5L2 19z" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>',
    calc: '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><rect x="5" y="3" width="14" height="18" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M8 7h8M8 11h2M12 11h2M16 11h0M8 15h2M12 15h2M8 18h2M12 18h2M16 15v3" stroke="currentColor" stroke-width="1.8"/></svg>',
    ref: '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><text x="3" y="17" font-size="14" font-style="italic" font-family="serif" fill="currentColor">x²</text></svg>',
    more: '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><circle cx="12" cy="5" r="1.8" fill="currentColor"/><circle cx="12" cy="12" r="1.8" fill="currentColor"/><circle cx="12" cy="19" r="1.8" fill="currentColor"/></svg>',
    help: '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M4 5h7a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4zM20 5h-7a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h7z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>',
    pin: '<svg viewBox="0 0 16 20" width="12" height="15" aria-hidden="true"><path d="M8 1a6 6 0 0 1 6 6c0 4.5-6 12-6 12S2 11.5 2 7a6 6 0 0 1 6-6z" fill="currentColor"/></svg>'
  };

  let attempt = null;
  let modules = [];      // [{section, questions:[...], seconds}]
  let answers = {};      // questionId -> 답 문자열
  const dirty = new Set();
  let st = null;         // localStorage 상태
  let view = 'intro';    // intro(모듈 안내) | question | review | break(쉬는 시간) | done
  let submitting = false;
  let advancing = false;  // 모듈 이동 중(시간 종료가 겹쳐 두 번 넘어가지 않게)
  let serverDeadline = 0;
  let warned = {};
  let calc = null;
  let helpUsedFinal = null;
  let wakeLock = null;
  let wakeWanted = false;
  const $ = id => document.getElementById(id);

  // ===== 상태 저장 =====
  function stateKey() { return 'f2r-sat-' + attempt.attemptId; }
  function loadState() {
    let s = null;
    try { s = JSON.parse(localStorage.getItem(stateKey()) || 'null'); } catch (e) { s = null; }
    s = s || {};
    s.mi = Number.isInteger(s.mi) ? s.mi : 0;
    s.qi = Number.isInteger(s.qi) ? s.qi : 0;
    s.starts = s.starts || {};
    s.marked = s.marked || {};
    s.elim = s.elim || {};
    s.elimOn = !!s.elimOn;
    s.hl = s.hl || {};
    s.split = s.split || 50;
    s.timerHidden = !!s.timerHidden;
    s.view = ['intro', 'question', 'review', 'break'].includes(s.view) ? s.view : 'intro';
    s.paused = s.paused || {};      // 모듈별 해설로 멈춘 누적 ms
    s.pauseAt = s.pauseAt || null;  // 해설란을 연 시각(열려 있으면 값)
    s.helpUsed = s.helpUsed || {};  // 해설을 본 문항
    s.breakStart = s.breakStart || null;
    return s;
  }
  function saveState() { try { localStorage.setItem(stateKey(), JSON.stringify(st)); } catch (e) { /* 사생활 보호 모드 등 */ } }

  // ===== 서버 저장 =====
  const putAnswer = TP.serialize(async (qid, value) => {
    await TP.api('/api/testprep/attempts/' + attempt.attemptId + '/answers', {
      method: 'PUT', body: JSON.stringify({ questionId: qid, answerText: value })
    });
  });
  async function flushDirty() {
    const ids = [...dirty];
    dirty.clear();
    try {
      await Promise.all(ids.map(id => putAnswer(Number(id), answers[id] == null ? null : answers[id])));
    } catch (err) {
      ids.forEach(id => dirty.add(id)); // 다음 기회에 다시 보낸다
      toast('Your answers could not be saved. Check your connection. / 답안을 저장하지 못했습니다. 연결을 확인하세요.');
    }
  }
  function setAnswer(qid, value) {
    answers[qid] = value === '' ? null : value;
    dirty.add(String(qid));
    clearTimeout(setAnswer.t);
    setAnswer.t = setTimeout(flushDirty, 800);
  }

  // ===== 모듈 =====
  function buildModules(questions) {
    const order = [];
    questions.forEach(q => { if (!order.includes(q.section)) order.push(q.section); });
    return order.map((sec, i) => {
      const qs = questions.filter(q => q.section === sec);
      const per = PER_QUESTION_SEC[sec] || 80;
      return { section: sec, index: i, questions: qs, seconds: Math.ceil(qs.length * per / 60) * 60 };
    });
  }
  function mod() { return modules[st.mi]; }
  function cur() { return mod().questions[st.qi]; }
  function moduleTitle(m) { return 'Section ' + (m.index + 1) + ', Module 1: ' + (SECTION_NAME[m.section] || m.section); }
  function pausedMs(i) {
    return (st.paused[i] || 0) + (st.pauseAt && i === st.mi ? Date.now() - st.pauseAt : 0);
  }
  function moduleLeft(m) {
    if (!st.starts[m.index]) return m.seconds; // 아직 시작 전(안내 화면)
    const left = Math.floor((st.starts[m.index] + m.seconds * 1000 + pausedMs(m.index) - Date.now()) / 1000);
    const serverLeft = Math.floor((serverDeadline - Date.now()) / 1000);
    return Math.max(0, Math.min(left, serverLeft));
  }
  function setView(v) { view = v; st.view = v; saveState(); }
  function isAnswered(q) { const v = answers[q.questionId]; return v !== null && v !== undefined && v !== ''; }

  // ===== 시작 =====
  async function boot() {
    const id = new URLSearchParams(location.search).get('attempt');
    if (!id) { location.href = '/testprep/sat'; return; }
    await TP.loadMe();
    try {
      attempt = await TP.api('/api/testprep/attempts/' + id);
    } catch (err) {
      $('sat-main').innerHTML = '<div class="sat-msg">Cannot open this test: ' + TP.esc(err.message) + '<span class="ko">이 시험을 열 수 없습니다.</span><p><a class="sat-btn" href="/testprep/sat">Back to Digital SAT</a></p></div>';
      return;
    }
    if (attempt.status === 'SUBMITTED') { location.href = '/testprep/review?attempt=' + attempt.attemptId; return; }
    if (attempt.kind !== 'SAT') { location.href = '/testprep/run?attempt=' + attempt.attemptId; return; }

    modules = buildModules(attempt.questions);
    attempt.questions.forEach(q => { answers[q.questionId] = attempt.savedAnswers[q.questionId] || null; });
    serverDeadline = Date.now() + attempt.remainingSec * 1000;
    st = loadState();
    if (st.mi >= modules.length) st.mi = modules.length - 1;
    if (st.qi >= mod().questions.length) st.qi = 0;
    view = st.view;
    if (view !== 'intro' && view !== 'break' && !st.starts[st.mi]) view = 'intro';
    if (st.pauseAt) { // 해설란을 연 채 나갔다면 그 사이 시간도 멈춘 것으로 친다.
      st.paused[st.mi] = (st.paused[st.mi] || 0) + (Date.now() - st.pauseAt);
      st.pauseAt = null;
    }
    setView(view);

    document.title = attempt.title + ' · Digital SAT Practice';
    wireStatic();
    renderAll();
    setInterval(tick, 1000);
    tick();
    setInterval(flushDirty, 15000);
    window.addEventListener('beforeunload', () => { if (dirty.size) flushDirty(); });
  }

  // ===== 시간 =====
  function fmt(sec) {
    const m = Math.floor(sec / 60), s = sec % 60;
    return (m >= 60 ? Math.floor(m / 60) + ':' + String(m % 60).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0');
  }
  function tick() {
    if (view === 'done' || submitting || advancing) return;
    const el = $('sat-timer');
    if (view === 'break') {
      const bl = Math.max(0, Math.ceil((st.breakStart + BREAK_SEC * 1000 - Date.now()) / 1000));
      el.textContent = fmt(bl);
      const big = $('break-clock');
      if (big) big.textContent = fmt(bl);
      if (bl <= 0) endBreak();
      return;
    }
    const left = moduleLeft(mod());
    el.textContent = fmt(left);
    el.classList.toggle('low', left <= WARN_SEC && view !== 'intro');
    el.classList.toggle('paused', !!st.pauseAt);
    $('sat-paused').hidden = !st.pauseAt;
    $('btn-timer').disabled = left <= WARN_SEC && view !== 'intro'; // 5분 남으면 숨길 수 없다
    if (view === 'intro' || st.pauseAt) return;
    if (left <= WARN_SEC && left > 0 && !warned[st.mi]) {
      warned[st.mi] = true;
      if (st.timerHidden) { st.timerHidden = false; saveState(); renderTimerVisibility(); }
      toast('5 minutes remain in this module. / 이 모듈의 남은 시간은 5분입니다.');
    }
    if (left <= 0) {
      if (st.mi < modules.length - 1 && Date.now() < serverDeadline) {
        toast('Time is up for this module. Moving to the next module. / 모듈 시간이 끝나 다음 모듈로 넘어갑니다.');
        goNextModule();
      } else {
        doSubmit(true);
      }
    }
  }
  function renderTimerVisibility() {
    if (st.timerHidden && moduleLeft(mod()) <= WARN_SEC && view !== 'intro') st.timerHidden = false;
    $('sat-timer').classList.toggle('hidden-time', st.timerHidden);
    $('btn-timer').textContent = st.timerHidden ? 'Show' : 'Hide';
    $('sat-timer-hidden').hidden = !st.timerHidden;
  }

  // ===== 그리기 =====
  function renderAll() {
    closePopovers();
    const m = mod();
    $('sat-title').textContent = view === 'break' ? 'Break · 쉬는 시간' : moduleTitle(m);
    $('btn-dir').hidden = view === 'break';
    $('dir-body').innerHTML = DIRECTIONS[m.section] || '';
    renderTools();
    renderTimerVisibility();
    if (view === 'review') renderReview();
    else if (view === 'intro') renderIntro();
    else if (view === 'break') renderBreak();
    else if (view === 'done') { /* doSubmit이 그린다 */ }
    else renderQuestion();
    renderHelp();
    renderFooter();
    keepAwake(view === 'question' || view === 'review' || view === 'intro' || view === 'break');
  }

  function renderTools() {
    const m = mod();
    const tools = [];
    const working = view === 'question' || view === 'review';
    if (working && m.section === 'MATH') {
      tools.push('<button type="button" class="sat-tool" id="tool-calc" title="Calculator (Ctrl+Alt+C)">' + ICON.calc + '<span>Calculator</span></button>');
      tools.push('<button type="button" class="sat-tool" id="tool-ref" title="Reference (Ctrl+Alt+R)">' + ICON.ref + '<span>Reference</span></button>');
    }
    tools.push('<button type="button" class="sat-tool" id="tool-more" aria-haspopup="menu">' + ICON.more + '<span>More</span></button>');
    $('sat-tools').innerHTML = tools.join('');
    if ($('tool-calc')) $('tool-calc').addEventListener('click', () => togglePanel('calc'));
    if ($('tool-ref')) $('tool-ref').addEventListener('click', () => togglePanel('ref'));
    $('tool-more').addEventListener('click', e => { e.stopPropagation(); toggleMore(); });
    if (m.section !== 'MATH') { $('calc-panel').hidden = true; $('ref-panel').hidden = true; }
    $('tool-calc') && $('tool-calc').classList.toggle('on', !$('calc-panel').hidden);
    $('tool-ref') && $('tool-ref').classList.toggle('on', !$('ref-panel').hidden);
  }

  function qHead(q, num) {
    const marked = !!st.marked[q.questionId];
    const isMC = q.choices && q.choices.length;
    return '<div class="sat-qhead"><span class="sat-qnum">' + num + '</span>'
      + '<button type="button" class="sat-mark' + (marked ? ' on' : '') + '" id="btn-mark" aria-pressed="' + marked + '">'
      + (marked ? ICON.mark : ICON.markOff) + '<span>Mark for Review</span></button>'
      + (isMC ? '<button type="button" class="sat-abc' + (st.elimOn ? ' on' : '') + '" id="btn-abc" aria-pressed="' + st.elimOn + '" title="Cross out answer choices (Ctrl+Alt+O)"><s>ABC</s></button>' : '')
      + '</div>'
      + helpButtonHtml();
  }

  // 문항마다 보이는 해설 버튼. 누르면 오른쪽 해설란이 열리고 모듈 시간이 멈춘다.
  function helpButtonHtml() {
    const open = !$('help-panel').hidden;
    return '<button type="button" class="sat-help-btn' + (open ? ' on' : '') + '" id="btn-help" aria-expanded="' + open + '" title="Ctrl+Alt+E">'
      + ICON.help + '<b>' + (open ? '해설 닫고 계속 풀기' : '한글 해설 · 풀이 전략 보기') + '</b>'
      + '<span>' + (open ? '닫으면 시간이 다시 흐릅니다' : '누르면 시간이 멈춥니다') + '</span></button>';
  }

  function choicesHtml(q) {
    const sel = answers[q.questionId];
    const out = st.elim[q.questionId] || [];
    return '<div class="sat-choices" role="radiogroup">' + q.choices.map((c, i) => {
      const L = LETTERS[i];
      const isSel = sel === L, isOut = out.includes(L);
      return '<div class="sat-crow' + (isSel ? ' sel' : '') + (isOut ? ' out' : '') + '">'
        + '<button type="button" class="sat-choice" role="radio" aria-checked="' + isSel + '" data-pick="' + L + '">'
        + '<span class="sat-letter">' + L + '</span><span class="sat-ctext">' + R.inline(c) + '</span></button>'
        + (st.elimOn ? (isOut
          ? '<button type="button" class="sat-undo" data-undo="' + L + '">Undo</button>'
          : '<button type="button" class="sat-strike" data-strike="' + L + '" aria-label="Cross out choice ' + L + '"><span>' + L + '</span></button>') : '')
        + '</div>';
    }).join('') + '</div>';
  }

  function renderQuestion() {
    const m = mod();
    const q = cur();
    const num = st.qi + 1;
    const main = $('sat-main');
    let html;
    if (q.taskType === 'SAT_MATH_SPR') {
      const v = answers[q.questionId] || '';
      html = split('<div class="sat-spr-guide">' + SPR_GUIDE + '</div>',
        qHead(q, num) + '<div class="sat-prompt">' + R.render(q.prompt) + '</div>'
        + '<label class="sat-spr-label" for="spr-input">Your answer<span class="ko">답 입력</span></label>'
        + '<input id="spr-input" class="sat-spr-input" type="text" inputmode="decimal" autocomplete="off" spellcheck="false" maxlength="6" value="' + TP.esc(v) + '">'
        + '<div class="sat-spr-preview">Answer Preview: <span id="spr-preview">' + sprPreview(v) + '</span></div>');
    } else if (m.section === 'RW') {
      html = split('<div class="sat-passage sat-hl-scope" data-scope="' + q.questionId + ':b">' + R.render(q.bodyText) + '</div>',
        qHead(q, num) + '<div class="sat-prompt sat-hl-scope" data-scope="' + q.questionId + ':p">' + R.render(q.prompt) + '</div>' + choicesHtml(q));
    } else {
      html = '<div class="sat-single">' + qHead(q, num)
        + (q.bodyText ? '<div class="sat-prompt">' + R.render(q.bodyText) + '</div>' : '')
        + '<div class="sat-prompt">' + R.render(q.prompt) + '</div>' + choicesHtml(q) + '</div>';
    }
    main.innerHTML = html;
    main.scrollTop = 0;
    wireQuestion(q);
    main.querySelectorAll('.sat-hl-scope').forEach(applyHighlights);
  }

  function split(left, right) {
    return '<div class="sat-split" style="--left:' + st.split + '%">'
      + '<section class="sat-pane sat-left">' + left + '</section>'
      + '<div class="sat-divider" id="sat-divider" role="separator" aria-orientation="vertical" tabindex="0" title="Drag to resize"><span>⟷</span></div>'
      + '<section class="sat-pane sat-right">' + right + '</section></div>';
  }

  function sprPreview(v) {
    if (!v) return '';
    const m = /^(-?)([0-9.]+)\/([0-9.]+)$/.exec(v);
    if (m) return R.inline((m[1] ? '−' : '') + '\\frac{' + m[2] + '}{' + m[3] + '}');
    return TP.esc(v.replace(/^-/, '−'));
  }

  function wireQuestion(q) {
    const main = $('sat-main');
    $('btn-mark').addEventListener('click', toggleMark);
    $('btn-help').addEventListener('click', () => toggleHelp());
    if ($('btn-abc')) $('btn-abc').addEventListener('click', toggleElimMode);
    main.querySelectorAll('[data-pick]').forEach(b => b.addEventListener('click', () => pick(b.dataset.pick)));
    main.querySelectorAll('[data-strike]').forEach(b => b.addEventListener('click', () => strike(b.dataset.strike)));
    main.querySelectorAll('[data-undo]').forEach(b => b.addEventListener('click', () => strike(b.dataset.undo)));
    const spr = $('spr-input');
    if (spr) {
      let last = spr.value;
      spr.addEventListener('input', () => {
        let v = spr.value.replace(/[−–]/g, '-').replace(/\s/g, '');
        const max = v.startsWith('-') ? 6 : 5;
        if (!SPR_ALLOWED.test(v) || v.length > max || (v.match(/\//g) || []).length > 1) v = last;
        spr.value = v; last = v;
        $('spr-preview').innerHTML = sprPreview(v);
        setAnswer(q.questionId, v);
        renderFooter();
      });
    }
    const div = $('sat-divider');
    if (div) wireDivider(div);
  }

  function wireDivider(div) {
    const splitEl = div.parentElement;
    const setTo = pct => { st.split = Math.max(25, Math.min(75, Math.round(pct))); splitEl.style.setProperty('--left', st.split + '%'); saveState(); };
    div.addEventListener('pointerdown', e => {
      div.setPointerCapture(e.pointerId);
      const move = ev => { const r = splitEl.getBoundingClientRect(); setTo((ev.clientX - r.left) / r.width * 100); };
      const up = () => { div.removeEventListener('pointermove', move); div.removeEventListener('pointerup', up); };
      div.addEventListener('pointermove', move);
      div.addEventListener('pointerup', up);
    });
    div.addEventListener('keydown', e => {
      if (e.key === 'ArrowLeft') { setTo(st.split - 5); e.preventDefault(); }
      if (e.key === 'ArrowRight') { setTo(st.split + 5); e.preventDefault(); }
    });
    div.addEventListener('dblclick', () => setTo(50));
  }

  // ===== 답·표시 =====
  function pick(L) {
    const q = cur();
    if (!q.choices || !q.choices.length) return;
    const out = st.elim[q.questionId] || [];
    if (out.includes(L)) { st.elim[q.questionId] = out.filter(x => x !== L); saveState(); }
    setAnswer(q.questionId, answers[q.questionId] === L ? null : L);
    renderQuestion(); renderFooter();
  }
  function strike(L) {
    const q = cur();
    const out = st.elim[q.questionId] || [];
    if (out.includes(L)) st.elim[q.questionId] = out.filter(x => x !== L);
    else {
      st.elim[q.questionId] = out.concat(L);
      if (answers[q.questionId] === L) setAnswer(q.questionId, null);
    }
    saveState(); renderQuestion(); renderFooter();
  }
  function toggleMark() {
    const q = cur();
    if (st.marked[q.questionId]) delete st.marked[q.questionId]; else st.marked[q.questionId] = true;
    saveState(); renderQuestion(); renderFooter();
  }
  function toggleElimMode() { st.elimOn = !st.elimOn; saveState(); renderQuestion(); }

  // ===== 아래쪽 바·이동 =====
  function renderFooter() {
    const m = mod();
    const btnNav = $('btn-nav');
    btnNav.hidden = view !== 'question';
    const inFlow = view === 'question' || view === 'review';
    btnNav.innerHTML = 'Question ' + (st.qi + 1) + ' of ' + m.questions.length + ' <span class="caret">▴</span>';
    $('btn-back').hidden = !inFlow || (view === 'question' && st.qi === 0);
    $('btn-next').hidden = !inFlow;
    $('btn-next').textContent = view === 'review' && st.mi === modules.length - 1 ? 'Submit' : 'Next';
    if ($('nav-pop') && !$('nav-pop').hidden) fillGrid($('nav-grid'), true);
  }
  function goTo(i) { st.qi = i; setView('question'); renderAll(); }
  function back() {
    if (view === 'review') { goTo(mod().questions.length - 1); return; }
    if (st.qi > 0) goTo(st.qi - 1);
  }
  function next() {
    if (view === 'question') {
      if (st.qi < mod().questions.length - 1) goTo(st.qi + 1);
      else { closeHelp(); setView('review'); renderAll(); }
      return;
    }
    if (view === 'review') confirmLeaveModule();
  }
  function confirmLeaveModule() {
    const last = st.mi === modules.length - 1;
    const left = mod().questions.filter(q => !isAnswered(q)).length;
    const msg = (left ? '<p><b>' + left + '</b> question(s) in this module are unanswered.<span class="ko">이 모듈에 답하지 않은 문항이 ' + left + '개 있습니다.</span></p>' : '')
      + (last
        ? '<p>Submit your test now? You can\'t change your answers after submitting.<span class="ko">지금 제출할까요? 제출하면 답을 바꿀 수 없습니다.</span></p>'
        : '<p>Move on to the next module? You won\'t be able to return to this module.<span class="ko">다음 모듈로 넘어갈까요? 이 모듈로 다시 돌아올 수 없습니다.</span></p>');
    modal(last ? 'Submit test' : 'Next module', msg, [
      { label: 'Stay here', cls: 'ghost' },
      { label: last ? 'Submit' : 'Continue', cls: 'primary', run: () => { if (last) doSubmit(false); else startBreak(); } }
    ]);
  }
  // 모듈 시간이 끝났을 때(시간 종료)도 쉬는 시간으로 넘어간다. 섹션이 바뀔 때만 쉬는 시간이 있다.
  async function goNextModule() { startBreak(); }
  async function startBreak() {
    if (advancing || st.mi >= modules.length - 1) return;
    advancing = true;
    closeHelp();
    try { await flushDirty(); } finally { advancing = false; }
    const sectionChanges = modules[st.mi + 1].section !== mod().section;
    st.mi += 1; st.qi = 0;
    $('calc-panel').hidden = true; $('ref-panel').hidden = true;
    if (sectionChanges) { st.breakStart = Date.now(); setView('break'); }
    else setView('intro');
    renderAll(); tick();
  }
  function endBreak() {
    st.breakStart = null;
    setView('intro');
    renderAll(); tick();
  }
  function beginModule() {
    if (!st.starts[st.mi]) st.starts[st.mi] = Date.now();
    st.qi = 0;
    setView('question');
    renderAll(); tick();
  }
  function renderIntro() {
    const m = mod();
    $('sat-main').innerHTML = '<div class="sat-intro"><h2>' + TP.esc(SECTION_NAME[m.section] || m.section) + '</h2>'
      + '<p class="sat-intro-sub">Section ' + (m.index + 1) + ' of ' + modules.length + ' · Module 1 · ' + m.questions.length + ' questions · ' + Math.round(m.seconds / 60) + ' minutes'
      + '<span class="ko">' + modules.length + '개 영역 중 ' + (m.index + 1) + '번째 · 문항 ' + m.questions.length + '개 · ' + Math.round(m.seconds / 60) + '분</span></p>'
      + '<div class="sat-intro-box">' + (DIRECTIONS[m.section] || '') + '</div>'
      + '<div class="sat-intro-tips"><b>한글 해설 · 풀이 전략</b> — 문항마다 문제 바로 위에 있는 주황색 버튼을 누르면 시간이 멈추고 한글 해석·영어 어휘·풀이 설명·풀이 전략이 나옵니다. 해설을 본 문항은 결과에 따로 표시됩니다.' + (m.section === 'RW' ? '<br><b>형광펜·메모</b> — 지문이나 질문의 글자를 드래그하면 색·밑줄·메모 도구가 나옵니다.' : '') + '</div>'
      + '<button type="button" class="sat-btn sat-begin" id="btn-begin">Begin · 시작</button></div>';
    $('btn-begin').addEventListener('click', beginModule);
  }
  function renderBreak() {
    if (!st.breakStart) { st.breakStart = Date.now(); saveState(); }
    const next = mod();
    $('sat-main').innerHTML = '<div class="sat-break"><h2>Break time<span class="ko">쉬는 시간</span></h2>'
      + '<div class="sat-break-clock" id="break-clock">' + fmt(BREAK_SEC) + '</div>'
      + '<p>You can take a break of up to 10 minutes before ' + TP.esc(SECTION_NAME[next.section] || next.section) + '. Select <b>Resume Testing</b> when you are ready.</p>'
      + '<p class="ko">' + TP.esc(SECTION_NAME[next.section] || next.section) + ' 영역 전에 최대 10분 쉴 수 있습니다. 준비되면 다시 시작을 누르세요. 시간이 다 되면 자동으로 넘어갑니다.</p>'
      + '<button type="button" class="sat-btn" id="btn-resume">Resume Testing · 다시 시작</button></div>';
    $('btn-resume').addEventListener('click', endBreak);
    tick();
  }

  // ===== 검토 화면·이동판 =====
  function fillGrid(el, withCurrent) {
    const m = mod();
    el.innerHTML = m.questions.map((q, i) => {
      const cls = ['sat-cell', isAnswered(q) ? 'answered' : 'empty', st.marked[q.questionId] ? 'marked' : '', withCurrent && i === st.qi && view === 'question' ? 'current' : ''].join(' ');
      return '<button type="button" class="' + cls + '" data-go="' + i + '" aria-label="Question ' + (i + 1) + (isAnswered(q) ? ', answered' : ', unanswered') + (st.marked[q.questionId] ? ', marked for review' : '') + '">'
        + (withCurrent && i === st.qi && view === 'question' ? '<span class="pin">' + ICON.pin + '</span>' : '')
        + (i + 1) + (st.marked[q.questionId] ? '<span class="flag">' + ICON.mark + '</span>' : '') + '</button>';
    }).join('');
    el.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => { closePopovers(); goTo(Number(b.dataset.go)); }));
  }
  function legend(withCurrent) {
    return '<div class="sat-legend">' + (withCurrent ? '<span>' + ICON.pin + ' Current</span>' : '')
      + '<span><i class="lg-empty"></i> Unanswered</span><span><i class="lg-mark">' + ICON.mark + '</i> For Review</span></div>';
  }
  function renderReview() {
    const m = mod();
    $('sat-main').innerHTML = '<div class="sat-review"><h2>Check Your Work</h2>'
      + '<p>On the real test, a module stays open until its time runs out. In this practice set, select <b>' + (st.mi === modules.length - 1 ? 'Submit' : 'Next') + '</b> when you are ready.</p>'
      + '<p class="ko">실제 시험에서는 시간이 끝날 때까지 모듈이 열려 있습니다. 이 연습에서는 준비되면 ' + (st.mi === modules.length - 1 ? '제출' : '다음') + '을 누르세요.</p>'
      + '<div class="sat-review-card"><div class="sat-review-head"><b>' + TP.esc(moduleTitle(m)) + ' Questions</b>' + legend(false) + '</div>'
      + '<div class="sat-grid" id="review-grid"></div></div></div>';
    fillGrid($('review-grid'), false);
  }
  function toggleNav() {
    const pop = $('nav-pop');
    if (!pop.hidden) { pop.hidden = true; return; }
    closePopovers();
    $('nav-title').textContent = moduleTitle(mod()) + ' Questions';
    $('nav-legend').innerHTML = legend(true);
    fillGrid($('nav-grid'), true);
    pop.hidden = false;
  }

  // ===== 제출 =====
  async function doSubmit(auto) {
    if (submitting) return;
    submitting = true;
    closePopovers();
    $('sat-main').innerHTML = '<div class="sat-msg">' + (auto ? 'Time is up. ' : '') + 'Submitting your answers…<span class="ko">답안을 제출하는 중…</span></div>';
    try {
      await flushDirty();
      const all = attempt.questions.map(q => ({ questionId: q.questionId, answerText: answers[q.questionId] }));
      const card = await TP.api('/api/testprep/attempts/' + attempt.attemptId + '/submit', { method: 'POST', body: JSON.stringify({ answers: all }) });
      helpUsedFinal = Object.assign({}, st.helpUsed);
      try { localStorage.removeItem(stateKey()); } catch (e) { /* ignore */ }
      view = 'done';
      renderDone(card);
    } catch (err) {
      submitting = false;
      $('sat-main').innerHTML = '<div class="sat-msg">Could not submit: ' + TP.esc(err.message) + '<span class="ko">제출하지 못했습니다. 잠시 후 다시 시도하세요.</span><p><button class="sat-btn" id="retry-submit">Try again</button></p></div>';
      $('retry-submit').addEventListener('click', () => doSubmit(false));
    }
  }
  function renderDone(card) {
    const helpCount = Object.keys(helpUsedFinal || {}).length;
    keepAwake(false);
    $('sat-title').textContent = attempt.title;
    $('sat-tools').innerHTML = '';
    $('sat-timer').textContent = '';
    $('btn-timer').hidden = true;
    $('btn-dir').hidden = true;
    $('calc-panel').hidden = true; $('ref-panel').hidden = true;
    const secs = (card.sections || []).map(s => '<div class="sat-score-sec"><span>' + TP.esc(SECTION_NAME[s.section] || s.section) + '</span><b>' + (s.scaled != null ? s.scaled : '—') + '</b><em>' + s.correct + ' / ' + s.autoCount + ' correct</em></div>').join('');
    $('sat-main').innerHTML = '<div class="sat-done"><h2>Your practice score</h2><p class="ko">연습 점수(추정)</p>'
      + '<div class="sat-total">' + (card.totalScaled != null ? card.totalScaled : '—') + '<small>/ 1600</small></div>'
      + '<div class="sat-score-row">' + secs + '</div>'
      + (helpCount ? '<p class="sat-help-note">해설을 본 문항: <b>' + helpCount + '</b>개 — 이 문항들은 점수에 그대로 포함되었습니다.<span class="ko">You opened the help panel on ' + helpCount + ' question(s).</span></p>' : '')
      + '<p class="sat-disclaimer">' + TP.esc(card.bandDisclaimer) + '<span class="ko">F2R 내부 추정 점수이며 College Board 공식 SAT 점수가 아닙니다. 문항 수가 적은 연습 세트라 실제 점수와 차이가 클 수 있습니다.</span></p>'
      + '<div class="sat-done-btns"><a class="sat-btn" href="/testprep/review?attempt=' + attempt.attemptId + '">Review answers<span class="ko">정답·해설 보기</span></a>'
      + '<a class="sat-btn ghost" href="/testprep/sat">Back to Digital SAT<span class="ko">목록으로</span></a></div></div>';
    renderFooter();
  }

  // ===== 형광펜·메모 =====
  function scopeMarks(key) { return st.hl[key] || []; }
  function textOffset(scope, node, offset) {
    const r = document.createRange();
    r.setStart(scope, 0);
    r.setEnd(node, offset);
    return r.toString().length;
  }
  function wrapRange(scope, s, e, mk) {
    const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
    const nodes = [];
    let pos = 0;
    for (let n = walker.nextNode(); n; n = walker.nextNode()) { nodes.push([n, pos]); pos += n.nodeValue.length; }
    nodes.forEach(([n, start]) => {
      const end = start + n.nodeValue.length;
      if (end <= s || start >= e) return;
      let mid = n;
      const a = Math.max(s, start) - start, b = Math.min(e, end) - start;
      if (a > 0) mid = mid.splitText(a);
      if (b - a < mid.nodeValue.length) mid.splitText(b - a);
      if (!mid.nodeValue.trim()) return;
      const el = document.createElement('mark');
      el.className = 'sat-hl hl-' + mk.c + (mk.u ? ' hl-u' : '') + (mk.n ? ' hl-note' : '');
      el.dataset.hl = mk.id;
      if (mk.n) el.title = mk.n;
      mid.parentNode.insertBefore(el, mid);
      el.appendChild(mid);
    });
  }
  function applyHighlights(scope) {
    scopeMarks(scope.dataset.scope).slice().sort((x, y) => x.s - y.s).forEach(mk => wrapRange(scope, mk.s, mk.e, mk));
    scope.querySelectorAll('mark[data-hl]').forEach(m => m.addEventListener('click', ev => {
      ev.stopPropagation();
      openHlBar(scope.dataset.scope, m.dataset.hl, m.getBoundingClientRect());
    }));
  }
  function currentSelection() {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
    const range = sel.getRangeAt(0);
    const scope = (range.commonAncestorContainer.nodeType === 1 ? range.commonAncestorContainer : range.commonAncestorContainer.parentElement).closest('.sat-hl-scope');
    if (!scope) return null;
    const s = textOffset(scope, range.startContainer, range.startOffset);
    const e = textOffset(scope, range.endContainer, range.endOffset);
    if (e - s < 1) return null;
    return { scope, key: scope.dataset.scope, s, e, rect: range.getBoundingClientRect() };
  }
  function addHighlight(selInfo, color) {
    const mk = { id: 'h' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6), s: selInfo.s, e: selInfo.e, c: color || 'yellow', u: false, n: '' };
    st.hl[selInfo.key] = scopeMarks(selInfo.key).concat(mk);
    saveState();
    window.getSelection().removeAllRanges();
    rerenderScope(selInfo.key);
    return mk;
  }
  function rerenderScope(key) {
    const q = cur();
    const scope = document.querySelector('.sat-hl-scope[data-scope="' + key + '"]');
    if (!scope) return;
    scope.innerHTML = R.render(key.endsWith(':b') ? q.bodyText : q.prompt);
    applyHighlights(scope);
  }
  function findMark(key, id) { return scopeMarks(key).find(m => m.id === id); }
  function updateMark(key, id, patch) {
    const mk = findMark(key, id);
    if (!mk) return;
    Object.assign(mk, patch);
    saveState(); rerenderScope(key);
  }
  function removeMark(key, id) {
    st.hl[key] = scopeMarks(key).filter(m => m.id !== id);
    saveState(); rerenderScope(key);
  }
  // 선택 직후(새 형광펜) 또는 기존 표시 클릭(편집) 때 같은 도구 막대를 쓴다.
  function openHlBar(key, id, rect, selInfo) {
    closePopovers();
    const bar = $('hl-bar');
    const mk = id ? findMark(key, id) : null;
    bar.innerHTML = ['yellow', 'blue', 'pink'].map(c => '<button type="button" class="hl-swatch hl-' + c + (mk && mk.c === c ? ' on' : '') + '" data-c="' + c + '" aria-label="' + c + ' highlight"></button>').join('')
      + '<button type="button" class="hl-btn' + (mk && mk.u ? ' on' : '') + '" data-u aria-label="Underline"><u>U</u></button>'
      + (mk ? '<button type="button" class="hl-btn" data-del aria-label="Delete highlight">🗑</button>' : '')
      + '<button type="button" class="hl-btn" data-note aria-label="Add note">✎ Note</button>';
    placeAt(bar, rect);
    bar.hidden = false;
    const ensure = (color) => {
      if (mk) return mk;
      const made = addHighlight(selInfo, color);
      return made;
    };
    bar.querySelectorAll('[data-c]').forEach(b => b.addEventListener('click', () => {
      if (mk) updateMark(key, mk.id, { c: b.dataset.c }); else ensure(b.dataset.c);
      bar.hidden = true;
    }));
    bar.querySelector('[data-u]').addEventListener('click', () => {
      if (mk) updateMark(key, mk.id, { u: !mk.u });
      else { const made = ensure('yellow'); updateMark(key, made.id, { u: true }); }
      bar.hidden = true;
    });
    if (mk) bar.querySelector('[data-del]').addEventListener('click', () => { removeMark(key, mk.id); bar.hidden = true; });
    bar.querySelector('[data-note]').addEventListener('click', () => {
      const target = ensure('yellow');
      bar.hidden = true;
      openNote(key, target.id, rect);
    });
  }
  function openNote(key, id, rect) {
    const mk = findMark(key, id);
    if (!mk) return;
    const pop = $('note-pop');
    pop.innerHTML = '<label for="note-ta">Note<span class="ko">메모</span></label><textarea id="note-ta" maxlength="500"></textarea>'
      + '<div class="note-btns"><button type="button" class="sat-btn ghost sm" data-del>Delete note</button><button type="button" class="sat-btn sm" data-save>Save</button></div>';
    pop.querySelector('textarea').value = mk.n || '';
    placeAt(pop, rect);
    pop.hidden = false;
    pop.querySelector('textarea').focus();
    pop.querySelector('[data-save]').addEventListener('click', () => { updateMark(key, id, { n: pop.querySelector('textarea').value.trim() }); pop.hidden = true; });
    pop.querySelector('[data-del]').addEventListener('click', () => { updateMark(key, id, { n: '' }); pop.hidden = true; });
  }
  function highlightSelection() {
    const info = currentSelection();
    if (info) addHighlight(info, 'yellow');
  }
  function placeAt(el, rect) {
    el.style.left = Math.max(8, Math.min(window.innerWidth - 300, rect.left)) + 'px';
    el.style.top = Math.max(70, rect.top - 50) + 'px';
  }

  // ===== 라인 리더 =====
  function toggleLineReader(force) {
    const lr = $('line-reader');
    const on = force === undefined ? lr.hidden : force;
    lr.hidden = !on;
    if (on && !lr.dataset.y) lr.dataset.y = String(Math.round(window.innerHeight * 0.35));
    moveLineReader(0);
  }
  function moveLineReader(dy) {
    const lr = $('line-reader');
    const y = Math.max(110, Math.min(window.innerHeight - 140, Number(lr.dataset.y || 200) + dy));
    lr.dataset.y = String(y);
    lr.style.top = y + 'px';
  }

  // ===== 해설란(여는 동안 모듈 시간 정지) =====
  function helpData(q) {
    if (!q || !q.koHelpJson) return null;
    try { return JSON.parse(q.koHelpJson); } catch (e) { return null; }
  }
  function toggleHelp(force) {
    if (view !== 'question' && force !== false) return;
    const panel = $('help-panel');
    const open = force === undefined ? panel.hidden : force;
    if (open === !panel.hidden) return;
    if (open) {
      $('ref-panel').hidden = true;
      panel.hidden = false;
      if (!st.pauseAt) st.pauseAt = Date.now();
      st.helpUsed[cur().questionId] = true;
    } else {
      panel.hidden = true;
      if (st.pauseAt) { st.paused[st.mi] = (st.paused[st.mi] || 0) + (Date.now() - st.pauseAt); st.pauseAt = null; }
    }
    saveState();
    const btn = $('btn-help');
    if (btn) btn.outerHTML = helpButtonHtml();
    if ($('btn-help')) $('btn-help').addEventListener('click', () => toggleHelp());
    renderHelp(); tick();
  }
  function closeHelp() { if (!$('help-panel').hidden) toggleHelp(false); }
  function renderHelp() {
    const panel = $('help-panel');
    if (panel.hidden || view !== 'question') return;
    const q = cur();
    st.helpUsed[q.questionId] = true; saveState();
    const h = helpData(q);
    const body = $('help-body');
    if (!h) { body.innerHTML = '<p class="help-empty">이 문항에는 아직 해설이 없습니다.</p>'; return; }
    const para = t => R.render(String(t || ''));
    body.innerHTML = '<p class="help-q">Q' + (st.qi + 1) + ' · ' + TP.esc(SECTION_NAME[q.section] || q.section) + '</p>'
      + '<section id="help-ko"><h3>한글 해석</h3><div class="help-text">' + para(h.translation) + '</div></section>'
      + '<section id="help-vocab"><h3>영어 어휘</h3>' + ((h.vocab || []).length
        ? '<table class="help-vocab"><tbody>' + h.vocab.map(v => '<tr><th>' + TP.esc(v[0]) + '</th><td>' + TP.esc(v[1]) + '</td></tr>').join('') + '</tbody></table>'
        : '<p class="help-empty">—</p>') + '</section>'
      + '<section id="help-sol"><h3>풀이 설명</h3><div class="help-text">' + para(h.solution) + '</div></section>'
      + '<section id="help-strategy"><h3>풀이 전략</h3><div class="help-text">' + para(h.strategy) + '</div></section>';
    body.scrollTop = 0;
    document.querySelectorAll('[data-help-tab]').forEach((x, i) => x.classList.toggle('active', i === 0));
  }

  // ===== 시험 중 화면 꺼짐 방지(Screen Wake Lock, 지원 브라우저만) =====
  async function keepAwake(on) {
    wakeWanted = on;
    try {
      if (on && !wakeLock && navigator.wakeLock && !document.hidden) {
        wakeLock = await navigator.wakeLock.request('screen');
        wakeLock.addEventListener('release', () => { wakeLock = null; });
      } else if (!on && wakeLock) {
        await wakeLock.release();
        wakeLock = null;
      }
    } catch (e) { wakeLock = null; /* 권한·배터리 절약 모드 등 — 시험 진행에는 영향 없음 */ }
  }

  // ===== 패널(계산기·공식표) =====
  function togglePanel(which) {
    if (mod().section !== 'MATH') return;
    const el = $(which === 'calc' ? 'calc-panel' : 'ref-panel');
    if (which === 'ref' && el.hidden) closeHelp();
    el.hidden = !el.hidden;
    if (which === 'calc' && !el.hidden) {
      if (!calc) calc = window.SATCalc.mount($('calc-body'));
      calc.focus();
    }
    renderTools();
  }

  // ===== 팝업 공통 =====
  function closePopovers() {
    ['nav-pop', 'more-menu', 'hl-bar', 'note-pop', 'dir-pop'].forEach(id => { if ($(id)) $(id).hidden = true; });
    $('btn-dir') && $('btn-dir').setAttribute('aria-expanded', 'false');
  }
  function toggleMore() {
    const menu = $('more-menu');
    const wasHidden = menu.hidden;
    closePopovers();
    menu.hidden = !wasHidden;
  }
  function toggleDirections() {
    const pop = $('dir-pop');
    const wasHidden = pop.hidden;
    closePopovers();
    pop.hidden = !wasHidden;
    $('btn-dir').setAttribute('aria-expanded', String(wasHidden));
  }
  function modal(title, bodyHtml, buttons) {
    const wrap = $('sat-modal');
    wrap.innerHTML = '<div class="sat-modal-card" role="dialog" aria-modal="true" aria-labelledby="sat-modal-title"><h2 id="sat-modal-title">' + TP.esc(title) + '</h2>'
      + '<div class="sat-modal-body">' + bodyHtml + '</div><div class="sat-modal-btns">'
      + buttons.map((b, i) => '<button type="button" class="sat-btn ' + (b.cls === 'ghost' ? 'ghost' : '') + '" data-mb="' + i + '">' + TP.esc(b.label) + '</button>').join('') + '</div></div>';
    wrap.hidden = false;
    wrap.querySelectorAll('[data-mb]').forEach(btn => btn.addEventListener('click', () => {
      wrap.hidden = true;
      const b = buttons[Number(btn.dataset.mb)];
      if (b.run) b.run();
    }));
    const primary = wrap.querySelector('[data-mb="' + (buttons.length - 1) + '"]');
    if (primary) primary.focus();
  }
  function showShortcuts() {
    modal('Keyboard shortcuts', '<table class="sat-table sat-keys"><thead><tr><th>Action</th><th>Keys</th></tr></thead><tbody>'
      + SHORTCUTS.map(r => '<tr><td>' + TP.esc(r[0]) + '</td><td><kbd>' + TP.esc(r[1]) + '</kbd></td></tr>').join('') + '</tbody></table>'
      + '<p class="ko">Mac에서는 Ctrl 대신 Control, Alt 대신 Option 키를 씁니다.</p>', [{ label: 'Close' }]);
  }
  function exitTest() {
    modal('Save and exit', '<p>Your answers are saved. The module timer keeps running while you are away.</p><p class="ko">답안은 저장됩니다. 나가 있는 동안에도 모듈 시간은 계속 흐릅니다.</p>', [
      { label: 'Stay', cls: 'ghost' },
      { label: 'Save and exit', run: async () => { await flushDirty(); location.href = '/testprep/sat'; } }
    ]);
  }
  let toastTimer = null;
  function toast(msg) {
    const t = $('sat-toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 5000);
  }

  // ===== 고정 요소 연결·단축키 =====
  function wireStatic() {
    $('btn-dir').addEventListener('click', e => { e.stopPropagation(); toggleDirections(); });
    $('dir-close').addEventListener('click', () => { $('dir-pop').hidden = true; });
    $('btn-timer').addEventListener('click', () => { st.timerHidden = !st.timerHidden; saveState(); renderTimerVisibility(); });
    $('btn-nav').addEventListener('click', e => { e.stopPropagation(); toggleNav(); });
    $('nav-close').addEventListener('click', () => { $('nav-pop').hidden = true; });
    $('nav-review').addEventListener('click', () => { closePopovers(); closeHelp(); setView('review'); renderAll(); });
    $('help-close').addEventListener('click', () => toggleHelp(false));
    $('help-done').addEventListener('click', () => toggleHelp(false));
    document.querySelectorAll('[data-help-tab]').forEach(b => b.addEventListener('click', () => {
      document.querySelectorAll('[data-help-tab]').forEach(x => x.classList.toggle('active', x === b));
      const target = document.getElementById('help-' + b.dataset.helpTab);
      if (target) target.scrollIntoView({ block: 'start', behavior: 'smooth' });
    }));
    $('btn-back').addEventListener('click', back);
    // Home: 답안을 저장하고 TestPrep 홈으로 간다. 같은 응시는 목록·내 점수에서 이어 풀 수 있다.
    $('btn-home').addEventListener('click', async () => { await flushDirty(); location.href = '/testprep'; });
    $('btn-next').addEventListener('click', next);
    $('menu-shortcuts').addEventListener('click', () => { closePopovers(); showShortcuts(); });
    $('menu-reader').addEventListener('click', () => { closePopovers(); toggleLineReader(); });
    $('menu-exit').addEventListener('click', () => { closePopovers(); exitTest(); });
    $('calc-close').addEventListener('click', () => togglePanel('calc'));
    $('calc-wide').addEventListener('click', () => { $('calc-panel').classList.toggle('wide'); if (calc) calc.redraw(); });
    $('ref-close').addEventListener('click', () => togglePanel('ref'));
    $('lr-close').addEventListener('click', () => toggleLineReader(false));

    // 라인 리더 끌기
    const grip = $('lr-grip');
    grip.addEventListener('pointerdown', e => {
      grip.setPointerCapture(e.pointerId);
      const lr = $('line-reader');
      const startY = e.clientY, base = Number(lr.dataset.y || 200);
      const move = ev => { lr.dataset.y = String(base + ev.clientY - startY); moveLineReader(0); };
      const up = () => { grip.removeEventListener('pointermove', move); grip.removeEventListener('pointerup', up); };
      grip.addEventListener('pointermove', move);
      grip.addEventListener('pointerup', up);
    });

    // 지문에서 글자를 고르면 형광펜 도구 막대를 띄운다.
    document.addEventListener('visibilitychange', () => { if (!document.hidden && wakeWanted) keepAwake(true); });
    $('sat-main').addEventListener('mouseup', () => {
      setTimeout(() => {
        const info = currentSelection();
        if (info) openHlBar(info.key, null, info.rect, info);
      }, 0);
    });

    document.addEventListener('click', e => {
      if (e.target.closest('.sat-pop, #nav-pop, #more-menu, #hl-bar, #note-pop, #dir-pop, #btn-nav, #tool-more, #btn-dir, mark[data-hl]')) return;
      ['nav-pop', 'more-menu', 'hl-bar'].forEach(id => { $(id).hidden = true; });
    });

    document.addEventListener('keydown', onKey);
  }

  function onKey(e) {
    if (view === 'done' || view === 'intro' || view === 'break') { if (e.key === 'F1') { e.preventDefault(); showShortcuts(); } return; }
    const k = e.key;
    const typing = /^(INPUT|TEXTAREA)$/.test((document.activeElement || {}).tagName || '');
    if (k === 'F1') { e.preventDefault(); showShortcuts(); return; }
    if (k === 'Escape') { closePopovers(); $('sat-modal').hidden = true; return; }
    if (!$('sat-modal').hidden) return;
    const ctrl = e.ctrlKey || e.metaKey;
    const digit = /^Digit([1-4])$/.exec(e.code);
    if (ctrl && e.altKey && e.shiftKey && e.code === 'KeyD') { e.preventDefault(); toggleDirections(); return; }
    if (ctrl && e.altKey && !e.shiftKey) {
      const map = {
        KeyB: back, KeyX: next, KeyG: toggleNav, KeyT: () => { st.timerHidden = !st.timerHidden; saveState(); renderTimerVisibility(); },
        KeyV: () => view === 'question' && toggleMark(),
        KeyO: () => view === 'question' && cur().choices && cur().choices.length && toggleElimMode(),
        KeyC: () => togglePanel('calc'), KeyR: () => togglePanel('ref'),
        KeyE: () => view === 'question' && toggleHelp()
      };
      if (map[e.code]) { e.preventDefault(); map[e.code](); return; }
      if (digit && view === 'question' && cur().choices && cur().choices.length) {
        e.preventDefault();
        if (!st.elimOn) { st.elimOn = true; saveState(); }
        strike(LETTERS[Number(digit[1]) - 1]);
        return;
      }
    }
    if (ctrl && e.shiftKey && !e.altKey && digit && view === 'question') { e.preventDefault(); pick(LETTERS[Number(digit[1]) - 1]); return; }
    if (ctrl && !e.altKey && !e.shiftKey && e.code === 'KeyL') { e.preventDefault(); toggleLineReader(); return; }
    if (ctrl && !e.altKey && !e.shiftKey && e.code === 'KeyH' && mod().section === 'RW') { e.preventDefault(); highlightSelection(); return; }
    if (!typing && !$('line-reader').hidden && (k === 'ArrowDown' || k === 'ArrowUp')) {
      e.preventDefault();
      moveLineReader(k === 'ArrowDown' ? 26 : -26);
    }
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
