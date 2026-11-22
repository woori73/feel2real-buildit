(function () {
  'use strict';
  const TP = window.TP = { user: null };

  TP.esc = function (v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  };

  TP.api = async function (url, opts) {
    opts = opts || {};
    const res = await fetch(url, Object.assign({
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' }
    }, opts));
    if (res.status === 401 && url !== '/api/auth/me') { TP.openLoginModal(); throw new Error('login required'); }
    if (!res.ok) {
      let msg = res.statusText;
      try { const j = await res.json(); msg = j.message || msg; } catch (e) { /* ignore */ }
      throw new Error(msg);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  };

  TP.loadMe = async function () {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      TP.user = res.ok ? await res.json() : null;
    } catch (e) { TP.user = null; }
    TP.renderAuth();
    return TP.user;
  };

  TP.renderAuth = function () {
    const slot = document.getElementById('tp-auth');
    if (!slot) return;
    if (TP.user) {
      slot.innerHTML = '<span class="who">' + TP.esc(TP.user.loginId) + '</span>'
        + '<a class="btn btn-ghost btn-sm" href="/testprep/scores">My Scores</a>'
        + '<button class="btn btn-ghost btn-sm" type="button" data-tp-logout>Logout</button>';
      slot.querySelector('[data-tp-logout]').addEventListener('click', async () => {
        // feel2real과 세션을 공유 — TestPrep 로그아웃은 Feel2Real에서도 로그아웃된다.
        if (!confirm('TestPrep uses your Feel2Real account. Logging out here signs you out of Feel2Real too. Continue?\n\nTestPrep은 Feel2Real 계정을 사용합니다. 여기서 로그아웃하면 Feel2Real에서도 로그아웃됩니다. 계속할까요?')) return;
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
        location.href = '/';
      });
    } else {
      slot.innerHTML = '<button class="btn btn-ghost btn-sm" type="button" data-tp-login>Login</button>';
      slot.querySelector('[data-tp-login]').addEventListener('click', () => TP.openLoginModal());
    }
  };

  // 비동기 작업을 호출 순서대로 하나씩 실행하고, 호출자는 "앞선 작업까지 모두 끝난 뒤"에 resolve되는 Promise를 받는다.
  // 답안 자동저장(PUT)이 진행 중일 때 제출(POST)이 먼저 서버에 닿아 같은 (응시, 문항) 행을 동시에 INSERT하는 경쟁을 막는다.
  TP.serialize = function (fn) {
    let tail = Promise.resolve();
    return function () {
      const args = arguments;
      tail = tail.then(() => fn.apply(null, args), () => fn.apply(null, args));
      return tail;
    };
  };

  // 응시 화면 주소: SAT은 전용 시험 화면(/testprep/sat/run), 그 밖은 기존 화면(/testprep/run).
  TP.runUrl = function (kind, attemptId) {
    return (kind === 'SAT' ? '/testprep/sat/run?attempt=' : '/testprep/run?attempt=') + encodeURIComponent(attemptId);
  };

  // TOEIC 문항 종류(TOEIC_P1 ~ TOEIC_P7)에서 Part 번호를 얻는다. TOEIC 문항이 아니면 null.
  TP.toeicPart = function (taskType) {
    const m = /^TOEIC_P([1-7])$/.exec(taskType || '');
    return m ? Number(m[1]) : null;
  };

  TP.TOEIC_PART_NAMES = {
    1: 'Photographs', 2: 'Question-Response', 3: 'Conversations', 4: 'Talks',
    5: 'Incomplete Sentences', 6: 'Text Completion', 7: 'Reading Comprehension'
  };

  // 응시 화면 위쪽 탭의 묶음 이름: TOEIC은 Part별("Part 5"), 그 밖은 영역(section)별.
  TP.groupOf = function (q) {
    const part = TP.toeicPart(q.taskType);
    return part ? 'Part ' + part : q.section;
  };

  // 문항 머리말. TOEIC은 실제 시험 번호(order_no = 7~200)를 쓰고, 그 밖은 화면 순서(1부터)를 쓴다.
  TP.questionLabel = function (q, index, total) {
    const part = TP.toeicPart(q.taskType);
    if (part) return 'Part ' + part + ' · ' + TP.TOEIC_PART_NAMES[part] + ' · No. ' + q.orderNo;
    return q.section + ' · ' + String(q.taskType || '').replace(/_/g, ' ') + ' · Q' + (index + 1) + (total ? '/' + total : '');
  };

  // TOEIC 영역 이름과 추정 점수 표시("395 / 495"). 점수가 없으면 —.
  TP.TOEIC_SECTION_NAMES = { LISTENING: 'Listening', READING: 'Reading' };
  TP.scaledText = function (scaled, max) {
    if (scaled == null) return '—';
    return max != null ? scaled + ' / ' + max : String(scaled);
  };

  // 영역별 정답 수를 더한 표기("3 / 5" = 맞힌 수 / 자동 채점 문항 수). 추정 점수가 없는 부분 응시(오답만 다시 풀기)의 점수 자리에 쓴다.
  TP.correctCountText = function (sections) {
    const sum = key => (sections || []).reduce((n, s) => n + (s[key] || 0), 0);
    return sum('correct') + ' / ' + sum('autoCount');
  };

  // SAT 제출 결과 화면의 점수 부분(제목·합계·영역 칸). names = 영역 이름표({ RW: 'Reading and Writing', … }).
  // 오답만 다시 풀기는 추정 점수가 없으므로(totalScaled = null) 800·1600 대신 정답 수("3 / 5 correct")를 보여 준다.
  TP.satScoreHtml = function (card, names) {
    const sections = card.sections || [];
    const partial = card.totalScaled == null;
    const secs = sections.map(s => '<div class="sat-score-sec"><span>' + TP.esc(names[s.section] || s.section) + '</span>'
      + (partial ? '<b>' + s.correct + ' / ' + s.autoCount + '</b><em>correct</em>'
        : '<b>' + (s.scaled != null ? s.scaled : '—') + '</b><em>' + s.correct + ' / ' + s.autoCount + ' correct</em>')
      + '</div>').join('');
    return (partial
      ? '<h2>Your retry result</h2><p class="ko">오답만 다시 풀기 — 정답 수(추정 점수를 내지 않습니다)</p>'
        + '<div class="sat-total">' + TP.correctCountText(sections) + '<small>correct</small></div>'
      : '<h2>Your practice score</h2><p class="ko">연습 점수(추정)</p>'
        + '<div class="sat-total">' + card.totalScaled + '<small>/ 1600</small></div>')
      + '<div class="sat-score-row">' + secs + '</div>';
  };

  // 문항 묶음: 이웃한 문항들이 글자까지 같은 bodyText(지문·대본)를 가지면 한 묶음이다 → [{ start, questions }].
  // start = 묶음 첫 문항의 자리(questions 안의 index). 묶음이 아닌 문항은 문항 하나짜리 묶음이다.
  // - 떨어져 있는 같은 글은 묶지 않는다(이웃한 것만). bodyText가 없으면 묶지 않는다.
  // - 듣기(audioMode = 'TTS')와 읽기는 글이 같아도 묶지 않는다.
  // - 객관식만 묶는다(선택지가 있고 BUILD_SENTENCE가 아님). 쓰기·말하기·문장 만들기는 언제나 문항 하나씩이다.
  TP.groupQuestions = function (questions) {
    const canJoin = q => !!q.bodyText && Array.isArray(q.choices) && q.choices.length > 0 && q.taskType !== 'BUILD_SENTENCE';
    const groups = [];
    (questions || []).forEach((q, i) => {
      const prev = i > 0 ? questions[i - 1] : null;
      const same = prev && canJoin(prev) && canJoin(q) && prev.bodyText === q.bodyText && prev.audioMode === q.audioMode;
      if (same) groups[groups.length - 1].questions.push(q);
      else groups.push({ start: i, questions: [q] });
    });
    return groups;
  };

  // 묶음 머리말(듣기 묶음의 재생 칸·복습 화면의 지문 칸). TOEIC은 "Part 3 · Conversations · No. 32–34", 그 밖은 "LISTENING · LISTEN TALK · Q5–7/28".
  TP.groupLabel = function (group, total) {
    const first = group.questions[0];
    const last = group.questions[group.questions.length - 1];
    const part = TP.toeicPart(first.taskType);
    if (part) return 'Part ' + part + ' · ' + TP.TOEIC_PART_NAMES[part] + ' · No. ' + first.orderNo + '–' + last.orderNo;
    return first.section + ' · ' + String(first.taskType || '').replace(/_/g, ' ') + ' · Q' + (group.start + 1) + '–' + (group.start + group.questions.length) + (total ? '/' + total : '');
  };

  // bodyText를 "읽어 줄 글(spoken)"과 "듣기 문항에서도 화면에 보여 줄 시각자료(shown)"로 나눈다.
  // [[show]] 한 줄이 있으면 그 줄 앞이 spoken, 뒤가 shown이다. 없으면 전체가 spoken이고 shown은 빈 글이다.
  TP.splitShown = function (bodyText) {
    const text = String(bodyText == null ? '' : bodyText);
    const m = /(^|\n)[ \t]*\[\[show\]\][ \t]*(\r?\n|$)/.exec(text);
    if (!m) return { spoken: text, shown: '' };
    return { spoken: text.slice(0, m.index).replace(/\s+$/, ''), shown: text.slice(m.index + m[0].length) };
  };

  // 응시 화면에 보여 줄 bodyText. 듣기(TTS)는 [[show]] 뒤의 시각자료만(없으면 빈 글), 읽기는 전체([[show]] 줄이 있으면 그 줄만 뺀다).
  TP.visibleBody = function (q) {
    const parts = TP.splitShown(q.bodyText);
    if (q.audioMode === 'TTS') return parts.shown;
    return parts.shown ? (parts.spoken ? parts.spoken + '\n' : '') + parts.shown : parts.spoken;
  };

  // 지문 → 안전한 HTML. 글은 전부 이스케이프하고, 표("|"로 시작하는 줄)와 그림([[img:/testprep/img/…]] 한 줄)만 태그로 바꾼다.
  // 표·그림 렌더는 SAT 서식(sat_rich.js의 SATRich.table·image)을 그대로 부른다. 표·그림이 없는 글은 이스케이프한 글 그대로다(기존 출력과 같다).
  TP.richBody = function (text) {
    const R = window.SATRich;
    const lines = String(text == null ? '' : text).replace(/\r\n?/g, '\n').split('\n');
    const isTable = l => /^\s*\|/.test(l);
    const isImage = l => R.imageLine(l) != null;
    if (!R || !lines.some(l => isTable(l) || isImage(l))) return TP.esc(text);
    const out = [];
    let plain = [];
    const flushPlain = () => {
      const t = plain.join('\n').replace(/^\s*\n/, '').replace(/\s+$/, '');
      if (t) out.push('<div class="tp-text">' + TP.esc(t) + '</div>');
      plain = [];
    };
    for (let i = 0; i < lines.length;) {
      if (isImage(lines[i])) { flushPlain(); out.push(R.image(R.imageLine(lines[i]))); i++; continue; }
      if (isTable(lines[i])) {
        flushPlain();
        const block = [];
        while (i < lines.length && isTable(lines[i])) block.push(lines[i++].trim());
        out.push(R.table(block));
        continue;
      }
      plain.push(lines[i++]);
    }
    flushPlain();
    return out.join('');
  };

  // 듣기 재생 횟수: TOEIC 문항은 실제 시험처럼 묶음(단독 문항 포함)당 한 번만 재생한다. 그 밖(iBT·어휘)은 제한이 없다.
  TP.playsOnce = function (q) {
    return TP.toeicPart(q.taskType) != null;
  };

  // 재생 여부를 적어 두는 열쇠(응시 id + 묶음 첫 문항 id). sessionStorage에 두어 새로 고침·이어 풀기에서도 유지한다(서버에는 보내지 않는다).
  TP.playedKey = function (attemptId, questionId) {
    return 'tp-played:' + attemptId + ':' + questionId;
  };

  const playedInPage = {}; // sessionStorage를 못 쓰는 브라우저에서도 이 화면이 열려 있는 동안은 기억한다.
  TP.isPlayed = function (key) {
    if (playedInPage[key]) return true;
    try { return window.sessionStorage.getItem(key) === '1'; } catch (e) { return false; }
  };
  TP.markPlayed = function (key) {
    playedInPage[key] = true;
    try { window.sessionStorage.setItem(key, '1'); } catch (e) { /* 저장 못 해도 이 화면에서는 기억한다 */ }
  };

  // 문장 만들기(BUILD_SENTENCE) 칩을 화면에 보여 줄 순서로 섞는다. 원래 배열은 바꾸지 않는다.
  // - 결정적: 같은 seed(문항 id)면 언제나 같은 순서다(새로 고침·이어 풀기에서도 같다).
  // - 결과가 원래 순서(= 정답 순서로 들어 있는 세트가 있다)와 같으면 다시 섞는다. 칩이 1개이거나 모두 같은 낱말이면 그대로다.
  TP.shuffleChips = function (chips, seed) {
    const src = (chips || []).slice();
    if (src.length < 2 || src.every(c => c === src[0])) return src;
    const sameOrder = arr => arr.every((c, i) => c === src[i]);
    const shuffled = start => {
      let s = (Number(start) || 0) >>> 0;
      const rand = () => { // mulberry32
        s = (s + 0x6D2B79F5) >>> 0;
        let t = s;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };
      const arr = src.slice();
      for (let i = arr.length - 1; i > 0; i--) {
        const j = Math.floor(rand() * (i + 1));
        const tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
      }
      return arr;
    };
    for (let round = 0; round < 8; round++) {
      const arr = shuffled((Number(seed) || 0) + round * 7919);
      if (!sameOrder(arr)) return arr;
    }
    const arr = src.slice(); // 8번 모두 원래 순서면(사실상 없다) 첫 칩을 그와 다른 첫 낱말과 맞바꾼다.
    const j = arr.findIndex(c => c !== arr[0]);
    const tmp = arr[0]; arr[0] = arr[j]; arr[j] = tmp;
    return arr;
  };

  // 듣기 대본을 읽어 줄 글. [[show]] 뒤의 시각자료는 읽지 않는다.
  // TOEIC Part 2는 응답 줄 머리의 "A."가 관사 a처럼 들리지 않게 "Response A."로, Part 1은 "Statement A."로 바꿔 읽는다.
  // 화면·DB의 대본(bodyText)은 그대로 두고 읽는 글만 바꾼다. 그 밖의 문항은 대본 그대로.
  TP.speechText = function (q) {
    const text = TP.splitShown(q.bodyText).spoken;
    const part = TP.toeicPart(q.taskType);
    if (part === 1) return text.replace(/^([A-D])\.\s+/gm, 'Statement $1. ');
    if (part !== 2) return text;
    return text.replace(/^([A-C])\.\s+/gm, 'Response $1. ');
  };

  // 듣기 대본을 화자 차례(turn)로 나눈다 → [{ speaker, text }]. 화자 꼬리표("Woman:", "Man 1:")는 text에서 뺀다.
  // - 조각 구분: " — "(줄표) 또는 줄바꿈. 꼬리표는 조각 맨 앞에서만 찾는다(대문자로 시작하는 영문자·숫자 1~3낱말 + ":" + 공백).
  //   그래서 "9:30" 같은 시각이나 문장 가운데의 콜론은 화자로 보지 않는다.
  // - 꼬리표가 없는 조각은 앞 차례에 이어 붙인다(문장 속 줄표, Part 2의 응답 줄). 화자가 없는 글은 한 차례(speaker = null)다.
  // - 낱말이 모두 대문자로 시작하지 않는 꼬리표("Lab worker:")는 꼬리표가 둘 이상인 대화에서만 화자로 본다.
  //   안내 방송의 "Attention cast members: …"를 화자로 잘못 보아 읽지 않는 일을 막는다.
  TP.speechTurns = function (text) {
    const LABEL = /^([A-Z][A-Za-z0-9]*(?: [A-Za-z0-9]+){0,2}):\s+(?=\S)/;
    const pieces = String(text || '').split(/(\s+—\s+|\s*\n\s*)/); // 짝수 칸 = 조각, 홀수 칸 = 구분 글자
    const labelOf = piece => { const m = LABEL.exec(piece); return m ? m[1] : null; };
    const dialogue = pieces.filter((p, i) => i % 2 === 0 && labelOf(p)).length >= 2;
    const isSpeaker = label => label != null && (dialogue || label.split(' ').every(w => /^[A-Z0-9]/.test(w)));
    const turns = [];
    for (let i = 0; i < pieces.length; i += 2) {
      const piece = pieces[i];
      const label = labelOf(piece);
      if (isSpeaker(label)) {
        turns.push({ speaker: label, text: piece.replace(LABEL, '') });
      } else if (turns.length) {
        turns[turns.length - 1].text += pieces[i - 1] + piece;
      } else {
        turns.push({ speaker: null, text: piece });
      }
    }
    return turns.map(t => ({ speaker: t.speaker, text: t.text.trim() })).filter(t => t.text);
  };

  // 긴 글을 문장 단위로 나눠 max자 이하의 덩어리로 묶는다(긴 발화가 브라우저에서 중간에 끊기는 것을 줄인다).
  // max자 이하의 글은 그대로 한 덩어리다. "Ms."·"Dr." 같은 줄임말 뒤에서는 끊지 않는다.
  TP.speechChunks = function (text, max) {
    max = max || 200;
    if (text.length <= max) return text ? [text] : [];
    const sentences = [];
    const END = /[.!?]+["')\]]*\s+/g;
    let start = 0;
    for (let m = END.exec(text); m; m = END.exec(text)) {
      if (/(?:^|\s)(?:Mr|Mrs|Ms|Dr|Prof|St|Mt)$/.test(text.slice(start, m.index))) continue;
      sentences.push(text.slice(start, END.lastIndex).trim());
      start = END.lastIndex;
    }
    if (start < text.length) sentences.push(text.slice(start).trim());
    const chunks = [];
    sentences.forEach(s => {
      const last = chunks.length - 1;
      if (last >= 0 && (chunks[last] + ' ' + s).length <= max) chunks[last] += ' ' + s;
      else chunks.push(s);
    });
    return chunks;
  };

  // 화자 순서대로 주는 목소리 높이: 첫 화자 1.0, 둘째 0.8, 셋째 1.2(넷째부터 되풀이). 화자가 없는 글(해설·안내)은 1.0.
  TP.SPEAKER_PITCHES = [1, 0.8, 1.2];

  // 문항 하나를 읽을 발화 목록 → [{ text, pitch }]. 화면은 이 순서대로 발화(SpeechSynthesisUtterance)를 만들어 큐에 넣는다.
  // 꼬리표는 읽지 않고 화자마다 높이를 달리한다. 화자가 없는 글은 높이 1.0으로 문장 덩어리만 나눈다.
  TP.speechQueue = function (q) {
    const speakers = [];
    const queue = [];
    TP.speechTurns(TP.speechText(q)).forEach(turn => {
      if (turn.speaker != null && !speakers.includes(turn.speaker)) speakers.push(turn.speaker);
      const pitch = turn.speaker == null ? 1 : TP.SPEAKER_PITCHES[speakers.indexOf(turn.speaker) % TP.SPEAKER_PITCHES.length];
      TP.speechChunks(turn.text).forEach(text => queue.push({ text, pitch }));
    });
    return queue;
  };

  // 읽고 있던 브라우저 음성을 멈춘다(문항 이동·탭 이동·제출 때). 큐에 남은 발화도 모두 지운다. 음성을 지원하지 않는 브라우저에서는 아무 일도 하지 않는다.
  TP.stopSpeech = function () {
    if (window.speechSynthesis) window.speechSynthesis.cancel();
  };

  // TOEIC 제출 결과 화면: 밴드 대신 영역별 추정 점수(5~495)와 정답 수를 보여 준다.
  // 오답만 다시 풀기는 추정 점수가 없으므로(scaled = null) 정답 수를 크게 보여 준다.
  TP.toeicScorecardHtml = function (card, auto) {
    const sections = card.sections || [];
    const noEstimate = card.totalScaled == null;
    const count = key => sections.reduce((sum, s) => sum + (s[key] || 0), 0);
    const rows = sections.map(s =>
      '<tr><td>' + TP.esc(TP.TOEIC_SECTION_NAMES[s.section] || s.section) + '</td><td>' + TP.esc(TP.scaledText(s.scaled, s.scaleMax))
      + '</td><td>' + s.correct + ' / ' + s.autoCount + '</td><td>' + (s.accuracy != null ? s.accuracy + '%' : '—') + '</td></tr>').join('');
    return `<div class="q-card" style="text-align:center">
      <div class="q-type">${auto ? 'Auto-submitted at time limit' : 'Submitted'}</div>
      <div class="band-big">${noEstimate ? count('correct') + ' / ' + count('autoCount') : TP.esc(TP.scaledText(card.totalScaled, card.totalMax))}</div>
      <p class="lead">${noEstimate ? 'Correct answers — no score estimate for a wrong-answer retry<span class="ko">정답 수 — 오답만 다시 풀기는 추정 점수를 내지 않습니다</span>' : 'F2R score estimate<span class="ko">F2R 내부 추정 점수(5점 단위)</span>'}</p>
      <table class="tp" style="margin:16px 0"><tr><th>Section<span class="ko">영역</span></th><th>Estimate<span class="ko">추정 점수</span></th><th>Correct<span class="ko">정답 수</span></th><th>Accuracy<span class="ko">정답률</span></th></tr>${rows}</table>
      <p class="disclaimer" style="display:inline-block">${TP.esc(card.bandDisclaimer)}<span class="ko" style="color:#7a5300">F2R 내부 추정 점수 — ETS 공식 TOEIC 점수가 아닙니다. 정답 비율을 점수 범위에 편 값이라 실제 환산 점수와 다릅니다.</span></p>
      <div style="margin-top:16px;display:flex;gap:10px;justify-content:center">
        <a class="btn btn-primary" href="/testprep/review?attempt=${card.attemptId}">Review answers &amp; explanations</a>
        <a class="btn btn-outline" href="/testprep/toeic">TOEIC sets</a>
      </div></div>`;
  };

  // 내 학습 오답 목록의 문항 칸. TOEIC은 실제 문항 번호를 앞에 붙인다("No. 131 · …"), 그 밖은 발문만.
  TP.wrongItemLabel = function (w) {
    return (TP.toeicPart(w.taskType) && w.orderNo != null ? 'No. ' + w.orderNo + ' · ' : '') + (w.promptShort || '');
  };

  TP.requireLogin = function (then) {
    if (TP.user) { then(); return; }
    TP.openLoginModal();
    window.addEventListener('tp:login', then, { once: true });
  };

  // 로그인 후 돌아올 곳은 /testprep 내부 경로만 허용한다(open redirect 방지, 서버 핸들러도 내부 경로만 허용).
  TP.safeReturn = function (url) {
    if (typeof url !== 'string' || !/^\/testprep(\/|\?|$)/.test(url)) return null;
    if (/[\\\x00-\x1f\x7f]|\.\./.test(url)) return null;
    return url;
  };

  // target: 로그인 후 이용하려던 화면(미지정 시 현재 화면). 기존 네이버 로그인(/oauth2/authorization/naver)을 재사용한다.
  TP.openLoginModal = function (target) {
    if (document.getElementById('tp-login-modal')) return;
    const here = location.pathname + location.search.replace(/[?&](login|returnUrl)=[^&]*/g, '').replace(/^&/, '?');
    const back = TP.safeReturn(target) || TP.safeReturn(here) || '/testprep';
    const div = document.createElement('div');
    div.id = 'tp-login-modal';
    div.className = 'login-modal';
    div.innerHTML = '<div class="modal-card"><h2>TestPrep<span class="ko">로그인 필요</span></h2>'
      + '<p class="section lead" style="margin:0">TESTPREP 서비스를 이용하려면 로그인이 필요합니다.'
      + '<span class="ko">Log in to take exams, submit answers, and see your scores and review.</span></p>'
      + '<p class="modal-msg" data-msg></p>'
      + '<div style="display:flex;gap:10px;margin-top:14px;flex-wrap:wrap">'
      + '<a class="btn btn-primary" data-tp-naver href="/oauth2/authorization/naver">네이버 로그인하고 시작하기</a>'
      + '<button class="btn btn-outline" type="button" data-close>Cancel</button></div>'
      + '</div>';
    document.body.appendChild(div);
    div.querySelector('[data-tp-naver]').addEventListener('click', () => {
      // NaverOAuth2LoginSuccessHandler가 이 쿠키를 읽어 로그인 후 원래 화면으로 복귀시킨다(5분, 내부 경로만).
      const secure = location.protocol === 'https:' ? '; Secure' : '';
      document.cookie = 'oauth_return_url=' + encodeURIComponent(back) + '; Path=/; Max-Age=300; SameSite=Lax' + secure;
    });
    div.querySelector('[data-close]').addEventListener('click', () => div.remove());
    div.addEventListener('click', e => { if (e.target === div) div.remove(); });
  };

  // 비회원이 보호 화면(시험·성적·오답·학습) 링크를 누르면 이동하지 않고 로그인 안내를 띄운다.
  // 화면 숨김은 UX일 뿐이며, 실제 차단은 서버(SecurityConfig /testprep/**, /api/**)가 한다.
  TP.interceptProtectedLinks = function () {
    document.addEventListener('click', e => {
      const a = e.target.closest && e.target.closest('a[href^="/testprep/"]');
      if (!a || TP.user) return;
      e.preventDefault();
      TP.openLoginModal(a.getAttribute('href'));
    });
  };

  document.addEventListener('DOMContentLoaded', async () => {
    TP.interceptProtectedLinks();
    await TP.loadMe();
    const params = new URLSearchParams(location.search);
    const returnUrl = TP.safeReturn(params.get('returnUrl'));
    if (params.get('login') === 'required' && !TP.user) TP.openLoginModal(returnUrl);
    else if (TP.user && returnUrl) location.href = returnUrl;
  });
})();
