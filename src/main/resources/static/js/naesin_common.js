/*
 * 내신 실전 연습(/naesintest) 공통 도우미 — 로그인 확인, API 호출, 로그인 안내.
 * 데이터는 /api/testprep(kind=NAESIN)을 그대로 쓰고, 화면 주소만 /naesintest로 분리한다.
 */
(function () {
  'use strict';
  const root = typeof window !== 'undefined' ? window : globalThis;
  const NT = root.NT = { user: null };
  const CIRC = ['①', '②', '③', '④', '⑤'];
  const LETTERS = ['A', 'B', 'C', 'D', 'E'];
  // 학교별 페이지 주소: 시험 코드 앞부분 → 학교 페이지(NaesinTestPageController.SCHOOLS와 같게 유지).
  const SCHOOLS = { 'TP-NAESIN-APM2-': 'apgujeong-m2', 'TP-NAESIN-SGM2-': 'singu-m2', 'TP-NAESIN-APM1-': 'apgujeong-m1' };
  // 타과목 디지털 모의고사(국어·수학·사회·과학): 시험 코드 머리 → 문제 풀기(/learn) 탭(LearnHubCatalog.SUBJECTS와 같게 유지).
  const SUBJECTS = { 'TP-NAESIN-KOR-': 'korean', 'TP-NAESIN-MATH-': 'math', 'TP-NAESIN-SOC-': 'social', 'TP-NAESIN-SCI-': 'science' };
  NT.schoolUrl = function (examCode) {
    const subject = Object.keys(SUBJECTS).find(p => String(examCode || '').startsWith(p));
    if (subject) return '/learn?tab=' + SUBJECTS[subject];
    const key = Object.keys(SCHOOLS).find(p => String(examCode || '').startsWith(p));
    return key ? '/naesintest/school/' + SCHOOLS[key] : '/naesintest';
  };
  // 'B' → '②', 'B,D'(모두 고르기 답) → '②, ④'
  NT.circ = letter => String(letter || '').split(',').map(x => CIRC[LETTERS.indexOf(x.trim())] || '').filter(Boolean).join(', ');
  // "~을 모두 고르면?" 문항(tp_questions.task_type) — 답을 여러 개 칠할 수 있다.
  NT.isMulti = q => q && q.taskType === 'NAESIN_MC5_MULTI';
  NT.letter = i => LETTERS[i];

  NT.esc = v => String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));

  // 재원생 앱(/naesintest/app/*) 화면은 <body data-nt-app>로 표시한다(2026-11-21).
  // 앱 화면은 응시 API를 /api/naesintest/app/attempts로, 화면 주소를 /naesintest/app으로 쓰고, 로그인은 접속 코드로 한다.
  NT.isApp = () => typeof document !== 'undefined' && !!document.body && document.body.hasAttribute('data-nt-app');
  NT.attemptsApi = () => NT.isApp() ? '/api/naesintest/app/attempts' : '/api/testprep/attempts';
  NT.pageBase = () => NT.isApp() ? '/naesintest/app' : '/naesintest';
  NT.listUrl = examCode => NT.isApp() ? '/naesintest/app' : NT.schoolUrl(examCode);

  NT.api = async function (url, opts) {
    const res = await fetch(url, Object.assign({ credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } }, opts || {}));
    if (res.status === 401) {
      if (NT.isApp()) { location.href = '/naesintest/app'; throw new Error('접속 코드로 다시 로그인해 주세요.'); }
      NT.openLogin(); throw new Error('로그인이 필요합니다.');
    }
    if (!res.ok) {
      let msg = res.statusText;
      try { const j = await res.json(); msg = j.message || msg; } catch (e) { /* 본문 없음 */ }
      const err = new Error(msg); err.status = res.status; throw err;
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  };

  NT.loadMe = async function () {
    if (NT.isApp()) return NT.user; // 앱은 회원 로그인을 쓰지 않는다
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      NT.user = res.ok ? await res.json() : null;
    } catch (e) { NT.user = null; }
    return NT.user;
  };

  // 로그인 후 돌아올 곳은 /naesintest 안쪽 경로만 허용한다(서버도 내부 경로만 허용).
  NT.safeReturn = function (url) {
    if (typeof url !== 'string' || !/^\/naesintest(\/|\?|$)/.test(url)) return null;
    if (/[\\\x00-\x1f\x7f]|\.\./.test(url)) return null;
    return url;
  };

  NT.openLogin = function (target) {
    if (document.getElementById('nt-login')) return;
    const back = NT.safeReturn(target) || NT.safeReturn(location.pathname + location.search.replace(/[?&](login|returnUrl)=[^&]*/g, '').replace(/^&/, '?')) || '/naesintest';
    const wrap = document.createElement('div');
    wrap.id = 'nt-login';
    wrap.className = 'nt-modal';
    wrap.innerHTML = '<div class="nt-modal-card" role="dialog" aria-modal="true" aria-labelledby="nt-login-title">'
      + '<h2 id="nt-login-title">로그인이 필요합니다</h2>'
      + '<p>시험을 보고 채점 결과를 저장하려면 로그인해 주세요.</p>'
      + '<div class="nt-modal-btns"><button type="button" class="nt-btn nt-btn-line" data-close>닫기</button>'
      + '<a class="nt-btn" data-naver href="/oauth2/authorization/naver">네이버로 로그인</a></div></div>';
    document.body.appendChild(wrap);
    wrap.querySelector('[data-naver]').addEventListener('click', () => {
      const secure = location.protocol === 'https:' ? '; Secure' : '';
      document.cookie = 'oauth_return_url=' + encodeURIComponent(back) + '; Path=/; Max-Age=300; SameSite=Lax' + secure;
    });
    wrap.querySelector('[data-close]').addEventListener('click', () => wrap.remove());
  };

  // 문항 그림 표기 [[img:/naesin/...webp]] → 경로. 정해진 폴더의 webp만 허용한다.
  NT.imgPath = function (markup) {
    const m = /^\[\[img:(\/naesin\/[a-z0-9\/_.-]+\.webp)\]\]$/i.exec(String(markup || '').trim());
    return m && m[1].indexOf('..') < 0 ? m[1] : null;
  };

  // 문항 글(발문·지문). 통째로 그림 표기면 그림, 아니면 글을 그대로 보이고(줄바꿈 유지) 글 속 그림 표기만 그림으로 바꾼다.
  // 영어 내신 문항은 지금까지 모두 그림이었고, 제작 공장에서 올리는 국어·수학·사회·과학과 글 문항은 글로 온다(2026-11-22).
  NT.sheetHtml = function (markup, alt, cls) {
    const text = String(markup == null ? '' : markup).trim();
    if (!text) return '';
    const whole = NT.imgPath(text);
    if (whole) return '<img class="nt-img' + (cls ? ' ' + cls : '') + '" loading="lazy" src="' + whole + '" alt="' + NT.esc(alt) + '">';
    const html = NT.esc(text).replace(/\[\[img:([^\]]*)\]\]/g, (m, inner) => {
      const p = NT.imgPath('[[img:' + inner.replace(/&amp;/g, '&') + ']]');
      return p ? '<img class="nt-img" loading="lazy" src="' + p + '" alt="' + NT.esc(alt) + '">' : '';
    }).replace(/\n/g, '<br>');
    return '<div class="nt-text' + (cls ? ' ' + cls : '') + '">' + html + '</div>';
  };

  // 선택지 글. 「①」…「⑤」만 있는 그림 문항은 선택지가 그림 안에 있으므로 빈 글.
  NT.choicesHtml = function (q) {
    const list = (q && q.choices) || [];
    if (!list.length || list.every((c, i) => String(c).trim() === CIRC[i])) return '';
    return '<ol class="nt-choices">' + list.map((c, i) => '<li><span>' + CIRC[i] + '</span> ' + NT.esc(c) + '</li>').join('') + '</ol>';
  };

  // 시험지 머리의 과목·학년: 시험 코드 머리로 과목(없으면 영어), 제목의 「N학년」으로 학년(없으면 비움).
  const SUBJECT_LABEL = { 'TP-NAESIN-KOR-': '국어', 'TP-NAESIN-MATH-': '수학', 'TP-NAESIN-SOC-': '사회', 'TP-NAESIN-SCI-': '과학' };
  NT.subjectLabel = examCode => SUBJECT_LABEL[Object.keys(SUBJECT_LABEL).find(p => String(examCode || '').startsWith(p))] || '영어';
  NT.gradeLabel = title => { const m = /(\d)학년/.exec(String(title || '')); return m ? m[1] + '학년' : '-'; };

  NT.fmtTime = function (sec) {
    sec = Math.max(0, Math.floor(sec));
    return String(Math.floor(sec / 60)).padStart(2, '0') + ':' + String(sec % 60).padStart(2, '0');
  };

  /** 해설 문자열 → 줄 단위 HTML. 첫 줄(정답·유형·배점)은 굵게, '근거:'·'어법 포인트:' 줄은 따로 표시한다. */
  NT.expHtml = function (text) {
    const lines = String(text || '').split('\n').map(s => s.trim()).filter(Boolean);
    return lines.map((l, i) => {
      if (i === 0 && /^정답/.test(l)) return '<p class="nt-exp-head">' + NT.esc(l) + '</p>';
      if (/^(근거|어법 포인트)\s*:/.test(l)) return '<p class="nt-exp-tag">' + NT.esc(l) + '</p>';
      return '<p>' + NT.esc(l) + '</p>';
    }).join('');
  };

  // 비동기 저장을 순서대로 처리한다(자동 저장 중 제출이 먼저 닿지 않게).
  NT.serialize = function (fn) {
    let tail = Promise.resolve();
    return function () {
      const args = arguments;
      tail = tail.then(() => fn.apply(null, args), () => fn.apply(null, args));
      return tail;
    };
  };
})();
