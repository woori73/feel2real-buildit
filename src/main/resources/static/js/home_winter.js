/* Feel to Real 홈 — 겨울 톤 개편(2026-11-15) 움직임과 디지털 기말고사 화면 예시.
 * 동작 줄이기 설정이면 움직임 없이 마지막 모습만 보여 준다. 요소가 없으면 조용히 건너뛴다. */
(function () {
  'use strict';
  var doc = document, win = window;
  var reduce = win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var hasIO = 'IntersectionObserver' in win;
  function $(id) { return doc.getElementById(id); }
  function all(sel, root) { return Array.prototype.slice.call((root || doc).querySelectorAll(sel)); }

  /* 배너: 첫 장(디지털 영어 기말고사)에 학원 도입 안내가 있어 자동 넘김은 멈춘 채 시작한다. 타일로 고르고 ▶로 다시 돌릴 수 있다. */
  var heroPause = $('heroPause');
  if (heroPause && heroPause.getAttribute('aria-pressed') !== 'true') heroPause.click();

  /* 제목: 단어를 나눠 아래에서 차례로 올린다 */
  function split(el) {
    var i = 0, out = doc.createDocumentFragment();
    Array.prototype.slice.call(el.childNodes).forEach(function (n) {
      if (n.nodeType === 3) {
        n.textContent.split(/(\s+)/).forEach(function (t) {
          if (!t) return;
          if (/^\s+$/.test(t)) { out.appendChild(doc.createTextNode(t)); return; }
          var w = doc.createElement('span'), s = doc.createElement('span');
          w.className = 'hw-w'; s.textContent = t; s.style.setProperty('--i', i++); w.appendChild(s); out.appendChild(w);
        });
      } else if (n.nodeName === 'BR') { out.appendChild(n); }
      else {
        var w2 = doc.createElement('span'), s2 = doc.createElement('span');
        w2.className = 'hw-w'; s2.style.setProperty('--i', i++); s2.appendChild(n); w2.appendChild(s2); out.appendChild(w2);
      }
    });
    el.innerHTML = ''; el.appendChild(out);
  }
  function countUp(el, to) {
    var t0 = performance.now() + 450;
    (function st(now) {
      var k = Math.max(0, Math.min((now - t0) / 900, 1));
      el.textContent = Math.round(to * (1 - Math.pow(1 - k, 3)));
      if (k < 1) win.requestAnimationFrame(st);
    })(performance.now());
  }
  function play(el) {
    el.classList.remove('hw-play'); void el.offsetWidth; el.classList.add('hw-play');
    var num = el.querySelector('.hw-num'); if (num) countUp(num, 40);
    var slide = el.closest('[data-hero-slide]');
    var ty = slide && slide.querySelector('[data-hw-type]');
    if (ty) { ty.classList.remove('hw-type'); void ty.offsetWidth; ty.classList.add('hw-type'); }
  }
  all('[data-hw-type]').forEach(function (el) {
    var t = el.textContent;
    el.innerHTML = t.split('').map(function (c, i) { return c === ' ' ? ' ' : '<span class="hw-ch" style="--c:' + i + '">' + c + '</span>'; }).join('');
    el.setAttribute('aria-label', t);
  });
  /* 구역 제목이 hl-head 밖에 있는 곳(복습 게임)도 같은 제목 움직임을 쓴다 */
  all('#review-game > h2').forEach(function (h) { h.setAttribute('data-hw-motion', ''); });
  var titles = all('[data-hw-motion], .hl-head h2');
  titles.forEach(split);
  if (!reduce) {
    var hero = $('homeHero');
    function playActive() {
      var s = hero && hero.querySelector('[data-hero-slide]:not([hidden]) [data-hw-motion]');
      if (s) play(s);
    }
    if (hero && 'MutationObserver' in win) {
      new MutationObserver(playActive).observe(hero, { attributes: true, attributeFilter: ['data-active'] });
    }
    playActive();
    /* 「40문항」: 배너가 보이는 동안 몇 초마다 숫자를 다시 세고 밑줄을 다시 긋는다(2026-11-16) */
    var hl = hero && hero.querySelector('.hero-dexam .hw-hl');
    if (hl) {
      var heroSeen = true;
      if (hasIO) new IntersectionObserver(function (es) { heroSeen = es[0].isIntersecting; }).observe(hero);
      win.setInterval(function () {
        if (doc.hidden || !heroSeen || hero.getAttribute('data-active') !== '0') return;
        hl.classList.remove('hw-pulse'); void hl.offsetWidth; hl.classList.add('hw-pulse');
        var n40 = hl.querySelector('.hw-num'); if (n40) countUp(n40, 40);
      }, 7000);
    }
    if (hasIO) {
      var to = new IntersectionObserver(function (es) {
        es.forEach(function (en) { if (en.isIntersecting) { play(en.target); to.unobserve(en.target); } });
      }, { threshold: .6 });
      titles.forEach(function (el) { if (!el.closest('#homeHero') && el.getBoundingClientRect().top > win.innerHeight * .8) to.observe(el); });
    }
  }

  /* OMR 줄 */
  function omrRows(n, ans, cur, cls) {
    var h = '';
    for (var i = 1; i <= n; i++) {
      h += '<div class="hw-or' + (i === cur ? ' cur' : '') + '"><em>' + i + '</em>';
      for (var c = 1; c <= 5; c++) h += '<i class="' + (ans[i] === c ? 'f' : '') + '"></i>';
      h += '</div>';
    }
    return h;
  }

  /* 배너 시험지: 문제가 넘어가며 답이 칠해지고 OMR이 따라 칠해진다 */
  var MQ = [
    { q: '빈칸에 들어갈 말로 알맞은 것은?', pre: 'The girl ______ is singing on the stage is my sister.', c: ['who', 'which', 'what', 'whose', 'whom'], a: 1 },
    { q: '어법상 틀린 문장은?', c: ['Each student has a locker.', 'The books on the desk are mine.', 'One of my friends live in Busan.', 'Reading novels is fun.', 'Tom and I are good friends.'], a: 3 },
    { q: '빈칸에 공통으로 들어갈 말은?', pre: 'I don\'t know ______ he will come. / Ask him ______ he likes it.', c: ['if', 'that', 'what', 'because', 'so'], a: 1 },
    { q: '밑줄 친 -ing의 쓰임이 나머지와 다른 하나는?', c: ['Do you know the girl <u>playing</u> the guitar?', 'Who is the boy <u>reading</u> a book?', 'I saw a dog <u>running</u> on the street.', '<u>Reading</u> books is a good hobby.', 'Many people were <u>asking</u> him questions.'], a: 4 },
    { q: '밑줄 친 It의 쓰임이 나머지와 다른 하나는?', c: ['<u>It</u> is important to read every day.', '<u>It</u> was nice to meet you.', '<u>It</u> is hard to learn Chinese.', '<u>It</u> is my new bike.', '<u>It</u> is fun to play soccer.'], a: 4 }
  ];
  var mBox = $('hwMiniQ'), mOmr = $('hwMiniOmr'), mNo = $('hwMiniNo');
  if (mBox && mOmr) {
    var mAns = {}, mCur = 1;
    var drawMini = function (pop) {
      mOmr.innerHTML = '<h5>OMR</h5>' + omrRows(20, mAns, mCur);
      if (pop) { var r = mOmr.querySelectorAll('.hw-or')[pop - 1]; var b = r && r.querySelectorAll('i')[mAns[pop] - 1]; if (b) b.classList.add('pop'); }
    };
    var showQ = function (n) {
      var d = MQ[(n - 1) % MQ.length];
      mBox.innerHTML = '<p>' + n + '. ' + d.q + '</p>' + (d.pre ? '<div class="pre">' + d.pre + '</div>' : '') +
        d.c.map(function (t, k) { return '<div class="hw-c"><i>' + (k + 1) + '</i><span>' + t + '</span></div>'; }).join('');
      mBox.classList.remove('in'); void mBox.offsetWidth; mBox.classList.add('in');
      if (mNo) mNo.textContent = n + ' / 40번';
      mCur = n; drawMini();
    };
    var markQ = function (n) {
      var d = MQ[(n - 1) % MQ.length], c = mBox.querySelectorAll('.hw-c')[d.a - 1];
      if (c) c.classList.add('on'); mAns[n] = d.a; drawMini(n);
    };
    showQ(1);
    if (!reduce) {
      var n = 1;
      (function loop() {
        win.setTimeout(function () {
          markQ(n);
          win.setTimeout(function () { n++; if (n > 20) { n = 1; mAns = {}; } showQ(n); loop(); }, 700);
        }, 1100);
      })();
    } else { markQ(1); }
  }

  /* 남은 시간(예시) */
  var tLeft = 14 * 60 + 32, timers = [$('hwMiniTime')].filter(Boolean);
  if (timers.length) {
    win.setInterval(function () {
      if (tLeft > 0) tLeft--;
      var s = String(Math.floor(tLeft / 60)).padStart(2, '0') + ':' + String(tLeft % 60).padStart(2, '0');
      timers.forEach(function (t) { t.textContent = s; });
    }, 1000);
  }

  /* 채점표: ○ ╱ 도장, 점수 올리기 */
  var marks = $('hwMarks'), pen = $('hwPen');
  if (marks) {
    var wrong = { 7: 1, 15: 1, 22: 1, 29: 1, 33: 1, 38: 1 }, mk = '';
    for (var k = 1; k <= 40; k++) mk += '<div><b style="--i:' + k + '">' + (wrong[k] ? '╱' : '○') + '</b>' + k + '</div>';
    marks.innerHTML = mk;
    if (!reduce && hasIO) {
      var mo = new IntersectionObserver(function (es) {
        if (!es[0].isIntersecting) return; mo.disconnect(); marks.classList.add('in');
        if (pen) { var v = 0, iv = win.setInterval(function () { v += 2; pen.textContent = Math.min(v, 86); if (v >= 86) win.clearInterval(iv); }, 18); }
      }, { threshold: .3 });
      mo.observe(marks);
    }
  }

  /* 아래쪽 카드도 화면에 들어올 때 차례로 떠오르게 한다(2026-11-16) */
  /* #service 안 카드는 home_motion.js가 이미 차례로 띄우므로 그 밖의 카드만 */
  all('#ai-content .hl-trailer, #ai-content .hl-studio, #review-game .home-game-cover, #chart-showcase .hl-chart, #counsel .hl-admissions, .hw-plan > div').forEach(function (el) {
    el.classList.add('hw-reveal');
    var sib = el.parentNode ? Array.prototype.indexOf.call(el.parentNode.children, el) : 0;
    el.style.animationDelay = (sib % 4) * 90 + 'ms';
  });
  /* 회차 상자 숫자(40)는 보일 때 0부터 센다 */
  if (!reduce && hasIO) all('.hw-plan b').forEach(function (b) {
    var to = parseInt(b.textContent, 10); if (!to) return;
    var po = new IntersectionObserver(function (es) { if (!es[0].isIntersecting) return; po.disconnect(); countUp(b, to); }, { threshold: .6 });
    po.observe(b);
  });

  /* 화면에 들어올 때 살짝 떠오르기(처음 화면 아래쪽만) */
  if (!reduce && hasIO) {
    var ro = new IntersectionObserver(function (es) { es.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('in'); ro.unobserve(en.target); } }); }, { threshold: .15 });
    all('.hw-reveal').forEach(function (el) { if (el.getBoundingClientRect().top > win.innerHeight) ro.observe(el); });
  }

  /* 이메일 복사 */
  all('[data-hw-copy]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.getAttribute('data-hw-copy'), label = btn.textContent;
      var done = function (msg) { btn.textContent = msg; win.setTimeout(function () { btn.textContent = label; }, 1800); };
      try { navigator.clipboard.writeText(text).then(function () { done('복사했습니다'); }, function () { done('주소를 선택해 복사해 주세요'); }); }
      catch (e) { done('주소를 선택해 복사해 주세요'); }
    });
  });

  /* 사이드 메뉴·바로가기: 보고 있는 구역 표시 */
  var navLinks = all('.hw-side a[href^="#"], .hl-chips a[href^="#"]');
  if (hasIO && navLinks.length) {
    var so = new IntersectionObserver(function (es) {
      es.forEach(function (en) {
        if (!en.isIntersecting) return;
        var id = '#' + en.target.id;
        navLinks.forEach(function (a) { a.classList.toggle('is-on', a.getAttribute('href') === id); });
      });
    }, { rootMargin: '-45% 0px -50% 0px' });
    var seen = {};
    navLinks.forEach(function (a) { var id = a.getAttribute('href').slice(1); var el = id && !seen[id] && $(id); if (el) { seen[id] = 1; so.observe(el); } });
  }

  /* 배너 눈 */
  var hero2 = $('homeHero');
  if (hero2) {
    var cv = doc.createElement('canvas'); cv.className = 'hw-snow'; cv.setAttribute('aria-hidden', 'true');
    hero2.insertBefore(cv, hero2.firstChild);
    var ctx = cv.getContext('2d'), flakes = [], W = 0, H = 0;
    var size = function () { var d = Math.min(win.devicePixelRatio || 1, 2); W = cv.clientWidth; H = cv.clientHeight; cv.width = W * d; cv.height = H * d; ctx.setTransform(d, 0, 0, d, 0, 0); };
    size(); win.addEventListener('resize', size);
    for (var f = 0; f < 60; f++) flakes.push({ x: Math.random(), y: Math.random(), r: Math.random() * 2.2 + .8, s: Math.random() * .25 + .12, w: Math.random() * 6.28 });
    var snow = function () {
      ctx.clearRect(0, 0, W, H); ctx.fillStyle = 'rgba(255,255,255,.95)';
      flakes.forEach(function (p) {
        if (!reduce) { p.y += p.s / Math.max(H, 1) * 2.2; p.w += .01; if (p.y > 1.02) { p.y = -.02; p.x = Math.random(); } }
        ctx.beginPath(); ctx.arc(p.x * W + Math.sin(p.w) * 10, p.y * H, p.r, 0, 6.283); ctx.fill();
      });
      if (!reduce && !doc.hidden) win.requestAnimationFrame(snow);
    };
    snow();
    doc.addEventListener('visibilitychange', function () { if (!doc.hidden && !reduce) win.requestAnimationFrame(snow); });
  }

  /* F2R 서비스 지도: 점과 선 */
  var net = $('hwNet');
  if (net) {
    var ncv = doc.createElement('canvas'); ncv.setAttribute('aria-hidden', 'true'); net.appendChild(ncv);
    var nx = ncv.getContext('2d');
    /* 2026-11-21 개편: 묶음 4개(Coordinator·대시보드·교습소 실증 / 학습 콘텐츠 / 부정행위 방지 / 다른 서비스).
       첫 화면으로 옮기며 점을 늘렸다. d: 1 = 세부 점(좁은 화면에서는 숨겨 겹치지 않게 한다). */
    var N = [
      { t: 'AI 코디', x: .2, y: .45, hub: 1, h: '#coordinator' },
      { t: '학습 콘텐츠', x: .64, y: .21, hub: 1, h: '#learning-content' },
      { t: '부정행위 방지', x: .5, y: .77, hub: 1, h: '#exam-security' },
      { t: '다른 서비스', x: .84, y: .53, hub: 1, h: '#other-services' },
      { t: '새 학원', s: '11.25 이전', x: .16, y: .05, h: '#coordinator', p: 0 },
      { t: '대시보드', s: '원장', x: .42, y: .07, h: '/dashboard', p: 0 },
      { t: '장부', x: .08, y: .17, h: '/ledger', p: 0 },
      { t: '코디가 하는 일', x: .3, y: .25, h: '#coordinator-work', p: 0, d: 1 },
      { t: '디지털 기말고사', s: '학교별', x: .18, y: .33, h: '#digital-exam', p: 0 },
      { t: '시험 보는 방법', x: .44, y: .37, h: '#dexam-guide', p: 0, d: 1 },
      { t: '예비고1', s: '모의고사', x: .12, y: .57, h: '#pre-high-english', p: 0 },
      { t: '재원생 앱', s: '설치', x: .36, y: .57, h: '#student-app', p: 0 },
      { t: '적중 사례', s: '96.8%', x: .12, y: .69, h: '#hitCase', p: 0 },
      { t: '직보 쇼츠', s: '압구정중2', x: .34, y: .67, h: '/naesintest/school/apgujeong-m2#shorts', p: 0, d: 1 },
      { t: '2028 입시상담 AI', x: .2, y: .85, h: '#counsel', p: 0 },
      { t: '맞춤문제', s: '2주 4회', x: .62, y: .37, h: '#request', p: 1 },
      { t: '모의고사 위키', x: .66, y: .06, h: '/wiki', p: 1 },
      { t: 'New iBT · SAT', x: .88, y: .12, h: '/testprep', p: 1 },
      { t: '2027 수능 해설', x: .87, y: .29, h: '/csat/2027-english', p: 1 },
      { t: '렌즈 찾기', s: '11.21 배포', x: .36, y: .94, h: '#exam-security', p: 2 },
      { t: '보안 무늬', s: '준비 중', x: .66, y: .94, h: '#exam-security', p: 2 },
      { t: '시연 문의', x: .66, y: .7, h: '#exam-contact', p: 2 },
      { t: '논문·기사', x: .6, y: .85, h: '/exam-security', p: 2, d: 1 },
      { t: 'AI AGENT', x: .9, y: .43, h: '#ai-content', p: 3 },
      { t: '제작협업실', x: .92, y: .63, h: '/ai-agent-studio', p: 3, d: 1 },
      { t: '빌드잇 차트', x: .9, y: .74, h: '#chart-showcase', p: 3 },
      { t: '복습 게임', x: .84, y: .84, h: '#review-game', p: 3 },
      { t: '전자책', x: .92, y: .94, h: '#ebook', p: 3 },
      { t: '학원 홈페이지', x: .74, y: .6, h: '/builder', p: 3, d: 1 }
    ];
    var E = [[0, 1], [0, 2], [1, 3], [2, 3]];
    N.forEach(function (n, i) { if (n.p !== undefined) E.push([n.p, i]); });
    [[5, 6], [8, 9], [10, 12], [11, 0], [19, 20], [26, 27]].forEach(function (e) { E.push(e); });
    var narrow = false, hidden = function (i) { return narrow && N[i].d; };
    var hot = -1, els = [], visible = true, SQ = 1, NW = 0, NH = 0;
    /* 좁은 화면(지도 폭 480 미만): 세부 점을 숨기고 아래 위치(가로 x, 세로 y)로 다시 놓아 글자가 겹치지 않게 한다 */
    var M = { 'AI 코디': [.25, .42], '학습 콘텐츠': [.62, .18], '부정행위 방지': [.55, .85], '다른 서비스': [.78, .5],
      '새 학원': [.2, .04], '대시보드': [.2, .11], '장부': [.12, .18], '디지털 기말고사': [.25, .26], '예비고1': [.15, .5],
      '재원생 앱': [.38, .58], '적중 사례': [.15, .64], '2028 입시상담 AI': [.24, .78], '맞춤문제': [.62, .34], '모의고사 위키': [.75, .04],
      'New iBT · SAT': [.78, .11], '2027 수능 해설': [.8, .26], '렌즈 찾기': [.22, .93], '보안 무늬': [.6, .93], '시연 문의': [.6, .71],
      'AI AGENT': [.85, .42], '빌드잇 차트': [.82, .64], '복습 게임': [.84, .78], '전자책': [.88, .93] };
    var X = function (n) { return narrow && M[n.t] ? M[n.t][0] : .5 + (n.x - .5) * SQ; };
    var Y = function (n) { return narrow && M[n.t] ? M[n.t][1] : n.y; };
    var linked = function (i, j) { return E.some(function (e) { return (e[0] === i && e[1] === j) || (e[1] === i && e[0] === j); }); };
    var mark = function () { els.forEach(function (a, j) { a.classList.toggle('hot', hot >= 0 && j !== hot && linked(hot, j)); }); };
    N.forEach(function (n, i) {
      var a = doc.createElement('a'); a.className = 'hw-nd' + (n.hub ? ' is-hub' : '') + (n.d ? ' is-detail' : ''); a.href = n.h;
      a.innerHTML = n.t + (n.s ? '<sup>' + n.s + '</sup>' : '');
      ['mouseenter', 'focus'].forEach(function (ev) { a.addEventListener(ev, function () { hot = i; mark(); }); });
      ['mouseleave', 'blur'].forEach(function (ev) { a.addEventListener(ev, function () { hot = -1; mark(); }); });
      net.appendChild(a); els.push(a);
    });
    var dots = []; for (var q = 0; q < 60; q++) dots.push({ x: Math.random(), y: Math.random(), p: Math.random() * 6.28, h: q % 4 });
    var frame = function (ts) {
      var tt = (ts || 0) / 1000; nx.clearRect(0, 0, NW, NH);
      dots.forEach(function (d) {
        var hb = N[d.h], dx = d.x * NW + Math.sin(tt * .35 + d.p) * 7, dy = d.y * NH + Math.cos(tt * .28 + d.p) * 7;
        nx.strokeStyle = 'rgba(141,187,224,.10)'; nx.lineWidth = 1; nx.beginPath(); nx.moveTo(X(hb) * NW, Y(hb) * NH); nx.lineTo(dx, dy); nx.stroke();
        nx.fillStyle = 'rgba(170,205,235,.75)'; nx.beginPath(); nx.arc(dx, dy, 1.8, 0, 6.283); nx.fill();
      });
      E.forEach(function (e, i) {
        if (hidden(e[0]) || hidden(e[1])) return;
        var a = N[e[0]], b = N[e[1]], on = hot >= 0 && (e[0] === hot || e[1] === hot);
        nx.strokeStyle = on ? 'rgba(220,236,250,.9)' : 'rgba(141,187,224,.38)'; nx.lineWidth = on ? 1.6 : 1;
        nx.beginPath(); nx.moveTo(X(a) * NW, Y(a) * NH); nx.lineTo(X(b) * NW, Y(b) * NH); nx.stroke();
        var k = (tt * .22 + i * .137) % 1; nx.fillStyle = 'rgba(255,255,255,.9)';
        nx.beginPath(); nx.arc(X(a) * NW + (X(b) - X(a)) * NW * k, Y(a) * NH + (Y(b) - Y(a)) * NH * k, 1.6, 0, 6.283); nx.fill();
      });
      if (!reduce && visible) win.requestAnimationFrame(frame);
    };
    var nsize = function () {
      var d = Math.min(win.devicePixelRatio || 1, 2); NW = net.clientWidth; NH = net.clientHeight;
      ncv.width = NW * d; ncv.height = NH * d; nx.setTransform(d, 0, 0, d, 0, 0); SQ = NW < 560 ? .74 : 1; narrow = NW < 480;
      N.forEach(function (n, i) { els[i].style.left = X(n) * 100 + '%'; els[i].style.top = Y(n) * 100 + '%'; els[i].style.display = hidden(i) ? 'none' : ''; });
      if (reduce || !visible) frame(0);
    };
    nsize(); win.addEventListener('resize', nsize);
    /* 첫 화면 오른쪽 칸에 들어가면 높이가 늦게 정해지므로 칸 크기가 바뀔 때도 다시 그린다 */
    if (win.ResizeObserver) new win.ResizeObserver(function () { if (net.clientWidth !== NW || net.clientHeight !== NH) nsize(); }).observe(net);
    if (hasIO) new IntersectionObserver(function (es) { var was = visible; visible = es[0].isIntersecting; if (visible && !was && !reduce) win.requestAnimationFrame(frame); }).observe(net);
    frame(0);
  }
})();
