/*
 * Feel2Real 홈 첫 화면 — AI 비서(세븐·서수니)와 대화로 시작한다.
 * - 로그인 전에는 "안내 모드": 정해진 규칙으로 요청을 이해해 공개 서비스(예상문제 의뢰·문법 문제은행·모의고사위키 등)로 안내한다.
 *   AI가 무엇을 만들었다고 표시하지 않는다. 화면에도 안내 모드임을 밝힌다.
 * - 로그인한 원장·강사·관리자는 요청 문장을 AI 비서 화면(/dashboard) 입력창에 넘겨 실제 업무로 이어 간다(자동 전송 없음).
 * - 캐릭터 영상·이미지는 로그인한 ADMIN·TUTOR에게는 비공개 경로로, 로그인 전에는 공개 허용된 캐릭터만 보여준다(기본: 표식).
 */
(function () {
  'use strict';

  var AGENTS = {
    seven: { name: '세븐', code: 'H-07' },
    suni: { name: '서수니', code: 'SUNI' }
  };
  var KAKAO = 'https://open.kakao.com/o/sMf4ifRi';

  // 요청 의도 → 안내. 링크는 이미 공개된 실제 경로만 쓴다.
  var INTENTS = [
    { id: 'exam', words: ['예상문제', '시험지', '내신 시험', '의뢰', '시험 대비', '중간고사', '기말고사', '이용권', '신청', '주문', '시험 범위', '시험범위', '자료 제출', '단체 신청', '학부모', '160문항'] },
    { id: 'english', words: ['영어', '문법', '단어', '독해', '공부', '학습', '내신', '영문법'] },
    { id: 'wiki', words: ['모의고사', '해설', '기출', '수능'] },
    // 같은 점수면 앞의 의도를 고른다: '학원 홈페이지'는 업무보다 홈페이지 제작으로 안내한다.
    { id: 'builder', words: ['홈페이지', '블로그', '사이트', '웹사이트'] },
    { id: 'work', words: ['학원', '학생', '출결', '출석', '상담', '장부', '업무', '대시보드', '운영', '공지', '수납'] },
    { id: 'studio', words: ['ai agent', '에이전트', '제작소', '제작협업실', '협업실', '웹툰', '영상', '캐릭터', '콘텐츠', '드라마'] },
    { id: 'chart', words: ['차트', '선생님 찾', '쌤', '강사 찾', '순위', 'chart'] }
  ];

  // ---- 순수 규칙 (단위 테스트 대상) ----
  function detectIntent(text) {
    var t = String(text || '').toLowerCase();
    if (!t.trim()) return null;
    var best = null, score = 0;
    INTENTS.forEach(function (it) {
      var s = it.words.reduce(function (n, w) { return t.indexOf(w) >= 0 ? n + 1 : n; }, 0);
      if (s > score) { score = s; best = it.id; }
    });
    return best;
  }

  // The server owns the current offer. Pure callers may supply the same rendered summary.
  function offerSummary(ctx) {
    var home = typeof document !== 'undefined' && document.getElementById('aiHome');
    return (ctx && ctx.offerSummary) || (home && home.dataset.offerSummary) || '이용권 구성과 금액은 홈페이지의 이용 안내를 확인해 주세요.';
  }

  /** 의도별 안내 문장과 버튼. staff: 로그인한 원장·강사·관리자, studio: 제작협업실 접근 확인 여부(문구만 달라짐). */
  function replyFor(intent, ctx) {
    var c = ctx || {};
    switch (intent) {
      case 'exam':
        var intake = typeof document !== 'undefined' && document.getElementById('aiHome') && document.getElementById('aiHome').dataset.intakeEnabled === 'true';
        return { text: offerSummary(c) + ' 학교·학년·시험 범위·최근 기출·선생님 힌트를 보내 주세요. ' + (intake ? '사이트 신청서 또는 카톡·이메일로 접수 안내하며 결제는 별도로 확인합니다.' : '현재는 카톡·이메일로 접수 안내하며 결제는 별도로 확인합니다.'),
          links: (intake ? [{ label: '사이트 신청·자료 제출', href: '/exam-service', primary: true }] : []).concat([{ label: '기말고사 이용권 신청 (카톡)', href: KAKAO, external: true, primary: true }, { label: '이메일 신청·자료 제출', href: 'mailto:woorinaesin@naver.com' }, { label: '학교별 활용 사례 보기', href: '#exam-hit' }]) };
      case 'english':
        return { text: 'AI 영어교육 서비스를 안내해 드릴게요. 문법 문제은행과 모의고사위키는 바로 볼 수 있습니다.',
          links: [{ label: '문법 문제은행', href: '/grammar-items', primary: true }, { label: '모의고사위키', href: '/wiki' }, { label: '내신 예상문제', href: '#svc-exam' }] };
      case 'wiki':
        return { text: '모의고사 해설과 자료는 모의고사위키에서 볼 수 있습니다.', links: [{ label: '모의고사위키 열기', href: '/wiki', primary: true }] };
      case 'work':
        if (c.staff) return { text: 'AI 비서 화면에서 이어서 처리할게요. 학생 등록·출결·상담·공지를 한 단계씩 여쭤보고, 승인한 것만 저장합니다.', links: [{ label: 'AI 비서 화면으로 이어 가기', handoff: true, primary: true }] };
        return { text: '로그인하면 AI 업무 비서가 학생 등록·출결·상담·공지를 한 단계씩 안내하고, 승인한 것만 저장합니다.', links: [{ label: '로그인하고 시작하기', href: '/?login=required&returnUrl=%2Fdashboard', primary: true }] };
      case 'studio':
        // 제작협업실 링크는 누구에게나 안내하고, 들어가는 권한(ADMIN·modu)은 서버가 확인한다.
        return { text: c.studio
            ? 'AI AGENT 제작협업실에서 사람과 AI가 각본·콘티·캐릭터·영상을 함께 만들고 검수합니다.'
            : 'AI AGENT 제작협업실은 사람과 AI가 각본·콘티·캐릭터·영상을 함께 만드는 곳입니다. 관리자·운영 계정으로 로그인하면 들어갈 수 있습니다.',
          links: [{ label: 'AI AGENT 제작협업실 열기', href: '/ai-agent-studio', primary: true }, { label: 'AI 콘텐츠 소개 보기', href: '#ai-content' }] };
      case 'builder':
        return { text: '학원 홈페이지를 만들고 관리할 수 있습니다.', links: [{ label: '학원 홈페이지 제작', href: '/builder', primary: true }] };
      case 'chart':
        return { text: 'BUILD IT CHART에서 지역의 색깔 있는 영어 선생님을 찾을 수 있습니다.', links: [{ label: 'BUILD IT CHART 보기', href: '/chart', primary: true }] };
      default:
        return { text: '아직 그 요청은 안내하지 못합니다. 아래에서 골라 주세요.', links: [], choices: true };
    }
  }

  // 캐릭터별 인사 영상(speak.mp4)은 음성과 입 모양을 함께 만든 고정 대사 한 편이다.
  // 임의의 안내 문장에는 입 모양이 맞지 않으므로, 이 대사를 재생할 때('greeting')에만 쓴다.
  var GREETING = {
    seven: '세븐입니다. Feel to Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?',
    suni: '서수니입니다. Feel to Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?'
  };
  function greetingFor(agent, files) {
    var line = Object.prototype.hasOwnProperty.call(GREETING, agent) ? GREETING[agent] : null;
    return line && (files || []).indexOf('speak.mp4') >= 0 ? line : null;
  }

  // 대시보드와 같은 규칙: 생각 중에는 think.mp4, 인사 재생 중에는 speak.mp4, 그 밖에는 idle.mp4.
  // 해당 영상이 없으면 idle, 그것도 없으면 null. 일반 안내 문장('speaking')에는 speak.mp4를 쓰지 않는다.
  function clipFor(state, files) {
    var list = files || [];
    var clip = state === 'thinking' ? 'think' : state === 'greeting' ? 'speak' : 'idle';
    if (list.indexOf(clip + '.mp4') >= 0) return clip;
    return list.indexOf('idle.mp4') >= 0 ? 'idle' : null;
  }

  // 휴대폰 키보드가 열려 보이는 영역(visualViewport)이 줄었을 때, 입력줄 아래쪽이 가려진 만큼(+여유) 스크롤할 거리.
  // 가려지지 않았으면 0.
  function composerScrollDelta(composerBottom, viewTop, viewHeight, margin) {
    var hidden = composerBottom + (margin || 0) - (viewTop + viewHeight);
    return hidden > 0 ? Math.ceil(hidden) : 0;
  }

  /** 비서 선택 버튼 사진 파일: front.webp → poster.jpg → 없음(null). */
  function portraitFile(files) {
    var list = files || [];
    if (list.indexOf('front.webp') >= 0) return 'front.webp';
    return list.indexOf('poster.jpg') >= 0 ? 'poster.jpg' : null;
  }

  // 홈페이지 세븐 사진은 사용자가 제공한 원본을 쓴다. 운영 영상·미디어 파일은 교체하지 않는다.
  function portraitUrl(agent, files, base, posterOnly) {
    if (agent === 'seven') return '/img/agents/seven/portrait-20261029.png';
    var file = posterOnly ? ((files || []).indexOf('poster.jpg') >= 0 ? 'poster.jpg' : null) : portraitFile(files);
    return base && file ? base + agent + '/' + file : null;
  }

  var api = { INTENTS: INTENTS, detectIntent: detectIntent, replyFor: replyFor, clipFor: clipFor, greetingFor: greetingFor, portraitFile: portraitFile, portraitUrl: portraitUrl, composerScrollDelta: composerScrollDelta };
  if (typeof window !== 'undefined') window.f2rHomeAgent = api;
  if (typeof document === 'undefined' || !document.getElementById) return;

  var $ = function (id) { return document.getElementById(id); };
  var AGENT_KEY = 'f2r.dashboard.agent';
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (_) { /* 저장 불가 환경 */ } }
  };
  var ui = { agent: 'suni', user: null, media: null, base: null, state: 'idle', clip: null, sw: { front: 0, token: 0 } };

  function staff() { return !!(ui.user && (ui.user.role === 'ADMIN' || ui.user.role === 'TUTOR')); }
  function studioAllowed() { return !!(ui.user && ui.user.role === 'ADMIN'); }

  function el(tag, text, cls) { var e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; }

  function setState(s) {
    var stage = $('homeStage');
    var changed = ui.state !== s;
    ui.state = s;
    if (stage) stage.dataset.state = s;
    var consoleEl = $('homeConsole');
    if (consoleEl) consoleEl.dataset.state = s;
    // 홈은 평소 사진만 보여 주므로(영상 없음), 인사를 시작할 때도 화면을 다시 그려 인사 영상을 붙인다.
    if (changed && stage && (stage.dataset.media === 'ready' || s === 'greeting')) portrait();
    var label = $('homeStateLabel');
    if (label) label.textContent = { idle: '대기', listening: '듣는 중', thinking: '생각 중', speaking: '안내 중', greeting: '말하는 중' }[s] || '대기';
  }

  function bubble(who, text, links) {
    var log = $('homeChat');
    var node = el('div', null, 'si-home-msg ' + (who === 'user' ? 'is-user' : 'is-agent'));
    node.append(el('strong', who === 'user' ? '나' : AGENTS[ui.agent].name), el('p', text));
    if (links && links.length) {
      var row = el('div', null, 'si-home-actions');
      links.forEach(function (l) {
        var a = el(l.onClick || l.handoff ? 'button' : 'a', l.label, l.primary ? 'is-primary' : '');
        if (a.tagName === 'A') { a.href = l.href; if (l.external) { a.target = '_blank'; a.rel = 'noopener'; } }
        else a.type = 'button';
        if (l.onClick) a.addEventListener('click', l.onClick);
        if (l.handoff) a.addEventListener('click', handoff);
        row.append(a);
      });
      node.append(row);
    }
    log.append(node);
    log.scrollTop = log.scrollHeight;
    return node;
  }

  var lastAsk = '';
  function handoff() {
    try { if (lastAsk) sessionStorage.setItem('f2r.pendingAsk', lastAsk); } catch (_) { /* 저장 불가 */ }
    location.href = '/dashboard';
  }

  function respond(intent) {
    setState('thinking');
    setTimeout(function () {
      var r = replyFor(intent, { staff: staff(), studio: studioAllowed() });
      var links = r.links.slice();
      if (r.choices) links = mainChoices();
      bubble('agent', r.text, links);
      setState('speaking');
      setTimeout(function () { setState('idle'); }, 1800);
    }, 350);
  }

  function mainChoices() {
    return [
      { label: '기말고사 이용권 신청', primary: true, onClick: function () { respond('exam'); } },
      { label: '시험 범위·자료 제출 안내', onClick: function () { respond('exam'); } },
      { label: '학원 운영 AI 비서', onClick: function () { respond('work'); } }
    ];
  }

  // 첫 안내는 한 문장. 상품 설명은 바로 위 화면에 있으므로 되풀이하지 않는다.
  function welcome() {
    if (!$('homeChat')) return;
    bubble('agent', '학교·학년·시험 범위를 적어 주세요. 접수 방법을 알려 드릴게요.');
  }

  // 홈에서는 사진을 기본으로 보여 준다(데이터 절약). 영상은 캐릭터를 골라 인사할 때만 재생한다.
  function showPoster(files) {
    var img = $('stagePoster');
    if (!img) return;
    var src = portraitUrl(ui.agent, files, ui.base, true);
    if (!src) { img.hidden = true; img.removeAttribute('src'); return; }
    if (img.getAttribute('src') === src) return;
    img.hidden = true;
    img.onload = function () { img.hidden = false; };
    img.onerror = function () { img.hidden = true; };
    img.src = src;
  }

  function portrait() {
    var stage = $('homeStage');
    var info = ui.media && ui.media.agents && ui.media.agents[ui.agent];
    var files = info && info.files ? info.files : [];
    var video = $('homeVideo');
    $('homeCode').textContent = AGENTS[ui.agent].code;
    $('homeName').textContent = AGENTS[ui.agent].name;
    document.querySelectorAll('button[data-home-agent]').forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.homeAgent === ui.agent ? 'true' : 'false'); });
    pickPortraits();
    showPoster(files);
    var clip = ui.state === 'greeting' ? clipFor(ui.state, files) : null;
    if (ui.base && clip) {
      var src = ui.base + ui.agent + '/' + clip + '.mp4';
      var poster = portraitUrl(ui.agent, files, ui.base, true);
      // 인사 영상만 소리를 켜고 한 번 재생한다. 나머지 영상은 소리 없이 반복한다.
      var talking = clip === 'speak';
      var prev = ui.clip;
      ui.clip = clip;
      var shown = [video, $('homeVideoAlt')][ui.sw.front];
      // 이미 실패한 같은 영상이면 다시 시도하지 않고 2D 표식 화면을 유지한다.
      if (shown && shown.error && shown.getAttribute('src') === src) { stage.dataset.media = 'missing'; return; }
      stage.dataset.media = 'ready';
      // 두 영상 요소를 겹쳐 서서히 바꾼다(f2r_agent_clip_switch.js). 말하기가 끝나면 대기 영상을 처음부터 보여 준다.
      var sw = window.f2rClipSwitch;
      var pair = [video, $('homeVideoAlt')];
      if (sw && pair[1]) {
        sw.show(pair, ui.sw, src, {
          muted: !talking, loop: !talking, restart: talking || prev === 'speak', poster: poster,
          fromClip: prev, toClip: clip,
          onFail: function () { if (talking) { setState('idle'); return; } stage.dataset.media = 'missing'; }
        });
        return;
      }
      if (poster) video.poster = poster; else video.removeAttribute('poster');
      video.muted = !talking;
      video.loop = !talking;
      if (video.getAttribute('src') !== src) {
        video.src = src;
        var p = video.play && video.play();
        if (p && p.catch) p.catch(function () { if (talking) setState('idle'); });
      }
    } else {
      ui.clip = null;
      [video, $('homeVideoAlt')].forEach(function (v) { if (v) { v.pause && v.pause(); v.removeAttribute('src'); } });
      stage.dataset.media = 'missing';
    }
  }

  // 세븐은 승인된 홈페이지 사진, 다른 비서는 기존 front.webp → poster.jpg 규칙을 유지한다.
  function pickPortraits() {
    document.querySelectorAll('button[data-home-agent]').forEach(function (b) {
      var img = b.querySelector('img');
      if (!img) return;
      var info = ui.media && ui.media.agents && ui.media.agents[b.dataset.homeAgent];
      var list = info && info.files ? info.files : [];
      var src = portraitUrl(b.dataset.homeAgent, list, ui.base, false);
      if (!src) { b.classList.add('no-portrait'); img.removeAttribute('src'); return; }
      if (img.getAttribute('src') === src) return;
      img.onload = function () { b.classList.remove('no-portrait'); };
      img.onerror = function () { b.classList.add('no-portrait'); };
      img.src = src;
    });
  }

  function stageHasFrame() {
    return [$('homeVideo'), $('homeVideoAlt')].some(function (v) { return v && v.classList.contains('is-on') && v.readyState >= 2 && !v.error; });
  }

  // ---- 세븐 인사(음성+영상) ----
  // 소리 있는 재생은 사용자가 캐릭터 버튼을 누른 뒤에만 시작한다(모바일 자동재생 정책). 재생 중 다시 눌러도 겹쳐 재생하지 않는다.
  function mediaFiles() {
    var info = ui.media && ui.media.agents && ui.media.agents[ui.agent];
    return info && info.files ? info.files : [];
  }
  function playGreeting() {
    var line = greetingFor(ui.agent, mediaFiles());
    if (!line || ui.state === 'greeting') return;
    bubble('agent', 'Feel2Real에 오신 걸 환영합니다. 무엇을 도와 드릴까요?');
    setState('greeting');
  }
  function stopGreeting() {
    if (ui.state === 'greeting') setState('idle');
  }

  function loadMedia() {
    var url = staff() ? '/api/dashboard/agent-media' : '/api/agent-media/public';
    ui.base = staff() ? '/dashboard/agent-media/' : '/agent-media/public/';
    return fetch(url, { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .catch(function () { return null; })
      .then(function (m) { ui.media = m; portrait(); });
  }

  function readUser() {
    try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; }
  }

  function bind() {
    var form = $('homeForm'), input = $('homeInput');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var text = input.value.trim();
      if (!text) return;
      lastAsk = text;
      input.value = '';
      stopGreeting();
      bubble('user', text);
      respond(detectIntent(text));
    });
    input.addEventListener('input', function () { setState(input.value.trim() ? 'listening' : 'idle'); });
    // 키보드가 열려도 입력창·보내기 버튼이 보이도록, 보이는 영역 아래로 내려가면 그만큼 올린다.
    var vv = window.visualViewport;
    function keepComposerVisible() {
      if (document.activeElement !== input || !vv) return;
      var d = composerScrollDelta(form.getBoundingClientRect().bottom, vv.offsetTop, vv.height, 12);
      if (d) window.scrollBy(0, d);
    }
    if (vv) vv.addEventListener('resize', keepComposerVisible);
    input.addEventListener('focus', function () { setTimeout(keepComposerVisible, 350); });
    document.querySelectorAll('button[data-home-intent]').forEach(function (b) {
      b.addEventListener('click', function () { lastAsk = ''; bubble('user', (b.querySelector('b') || b).textContent.trim()); respond(b.dataset.homeIntent); });
    });
    document.querySelectorAll('button[data-home-agent]').forEach(function (b) {
      b.addEventListener('click', function () {
        stopGreeting();
        ui.agent = AGENTS[b.dataset.homeAgent] ? b.dataset.homeAgent : 'seven';
        store.set(AGENT_KEY, ui.agent);
        document.body.dataset.agentTheme = ui.agent;
        portrait();
        // 캐릭터를 고르면(사용자 클릭 = 소리 재생 허용) 인사 영상을 소리와 함께 바로 재생한다. 인사 영상이 없으면 글로만 안내한다.
        if (greetingFor(ui.agent, mediaFiles())) playGreeting();
        else bubble('agent', '이어서 도와드릴게요. 학교와 시험 범위를 알려 주세요.');
      });
    });
    [$('homeVideo'), $('homeVideoAlt')].forEach(function (video) {
      if (!video) return;
      video.addEventListener('ended', stopGreeting);
      video.addEventListener('error', function () {
        stopGreeting();
        // 영상을 못 불러오고 보이는 영상도 없으면 기존 2D 표식 화면으로 둔다.
        if (!$('homeVideo').getAttribute('src') && !$('homeVideoAlt').getAttribute('src')) return;
        // 보여 주려던 영상이 실패하면 앞 캐릭터 영상이 남아 있어도 2D 표식으로 바꾼다.
        if (video.getAttribute('src') === ui.base + ui.agent + '/' + ui.clip + '.mp4' || !stageHasFrame()) $('homeStage').dataset.media = 'missing';
      });
    });
    // 다른 탭으로 가거나 페이지를 떠나면 인사를 멈춘다(돌아왔을 때 소리가 겹치지 않게).
    document.addEventListener('visibilitychange', function () { if (document.hidden) stopGreeting(); });
    window.addEventListener('pagehide', stopGreeting);
  }

  function init() {
    if (!$('homeStage')) return;
    var saved = store.get(AGENT_KEY);
    ui.agent = AGENTS[saved] ? saved : 'suni';
    document.body.dataset.agentTheme = ui.agent;
    ui.user = readUser();
    bind();
    portrait();
    welcome();
    loadMedia();
    // 계정 메뉴가 로그인 정보를 늦게 채우는 경우(새 탭 첫 진입) 한 번 더 확인한다. 추가 요청은 보내지 않는다.
    setTimeout(function () {
      var u = readUser();
      if (u && (!ui.user || u.role !== ui.user.role)) { ui.user = u; loadMedia(); }
      var mode = $('homeMode');
      if (mode) mode.textContent = staff() ? '로그인됨 · 업무 요청은 AI 비서 화면에서 이어집니다' : '안내 모드 · 로그인하면 AI 업무 비서와 연결됩니다';
    }, 1500);
    window.addEventListener('f2r:login', function (e) { ui.user = e.detail || readUser(); loadMedia(); });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
