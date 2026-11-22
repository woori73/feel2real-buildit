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

  // TOEIC 문항 종류(TOEIC_P2 ~ TOEIC_P7)에서 Part 번호를 얻는다. TOEIC 문항이 아니면 null.
  TP.toeicPart = function (taskType) {
    const m = /^TOEIC_P([2-7])$/.exec(taskType || '');
    return m ? Number(m[1]) : null;
  };

  TP.TOEIC_PART_NAMES = {
    2: 'Question-Response', 3: 'Conversations', 4: 'Talks',
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

  // 듣기 대본을 읽어 줄 글. TOEIC Part 2는 응답 줄 머리의 "A."가 관사 a처럼 들리지 않게 "Response A."로 바꿔 읽는다.
  // 화면·DB의 대본(bodyText)은 그대로 두고 읽는 글만 바꾼다. 그 밖의 문항은 대본 그대로.
  TP.speechText = function (q) {
    const text = q.bodyText || '';
    if (TP.toeicPart(q.taskType) !== 2) return text;
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
