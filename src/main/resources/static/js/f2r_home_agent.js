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
    { id: 'exam', words: ['예상문제', '시험지', '내신 시험', '의뢰', '시험 대비', '중간고사', '기말고사'] },
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

  /** 의도별 안내 문장과 버튼. staff: 로그인한 원장·강사·관리자, studio: 제작협업실 접근 확인 여부(문구만 달라짐). */
  function replyFor(intent, ctx) {
    var c = ctx || {};
    switch (intent) {
      case 'exam':
        return { text: '학교별 시험 범위와 출제 특성을 반영해 내신 영어 예상문제 40문항과 정답·해설을 만듭니다. 1회 20,000원입니다.',
          links: [{ label: '예상문제 의뢰하기 (카톡)', href: KAKAO, external: true, primary: true }, { label: '실제 적중 사례 보기', href: '#exam-hit' }] };
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

  // 대시보드와 같은 규칙: 생각 중에는 think.mp4, 그 밖에는 idle.mp4. 해당 영상이 없으면 idle, 그것도 없으면 null.
  // 홈은 음성 립싱크가 없으므로 speak.mp4를 쓰지 않는다.
  function clipFor(state, files) {
    var list = files || [];
    var clip = state === 'thinking' ? 'think' : 'idle';
    if (list.indexOf(clip + '.mp4') >= 0) return clip;
    return list.indexOf('idle.mp4') >= 0 ? 'idle' : null;
  }

  var api = { INTENTS: INTENTS, detectIntent: detectIntent, replyFor: replyFor, clipFor: clipFor };
  if (typeof window !== 'undefined') window.f2rHomeAgent = api;
  if (typeof document === 'undefined' || !document.getElementById) return;

  var $ = function (id) { return document.getElementById(id); };
  var WELCOME_KEY = 'f2r.home.welcome';
  var AGENT_KEY = 'f2r.dashboard.agent';
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (_) { /* 저장 불가 환경 */ } }
  };
  var ui = { agent: 'seven', user: null, media: null, base: null, state: 'idle' };

  function staff() { return !!(ui.user && (ui.user.role === 'ADMIN' || ui.user.role === 'TUTOR')); }
  function studioAllowed() { return !!(ui.user && ui.user.role === 'ADMIN'); }

  function el(tag, text, cls) { var e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; }

  function setState(s) {
    var stage = $('homeStage');
    var changed = ui.state !== s;
    ui.state = s;
    if (stage) stage.dataset.state = s;
    if (changed && stage && stage.dataset.media === 'ready') portrait();
    var label = $('homeStateLabel');
    if (label) label.textContent = { idle: '대기', listening: '듣는 중', thinking: '생각 중', speaking: '안내 중' }[s] || '대기';
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
      { label: 'AI 비서와 업무 시작', primary: true, onClick: function () { respond('work'); } },
      { label: 'AI 영어교육', onClick: function () { respond('english'); } },
      { label: 'AI AGENT 제작협업실', onClick: function () { respond('studio'); } }
    ];
  }

  function welcome(force) {
    var log = $('homeChat');
    if (!log) return;
    var name = AGENTS[ui.agent].name;
    var later = store.get(WELCOME_KEY) === 'later' && !force;
    if (later) { bubble('agent', '다시 오셨네요. 무엇을 도와드릴까요?'); return; }
    var choices = mainChoices().concat([{ label: '나중에', onClick: function () { store.set(WELCOME_KEY, 'later'); bubble('agent', '알겠습니다. 필요할 때 아래 입력창이나 홀로그램 버튼을 눌러 주세요.'); } }]);
    bubble('agent', '안녕하세요. 저는 ' + name + '입니다. 무엇을 만들고 싶으신가요?', choices);
  }

  function portrait() {
    var stage = $('homeStage');
    var info = ui.media && ui.media.agents && ui.media.agents[ui.agent];
    var files = info && info.files ? info.files : [];
    var video = $('homeVideo');
    $('homeCode').textContent = AGENTS[ui.agent].code;
    $('homeName').textContent = AGENTS[ui.agent].name;
    document.querySelectorAll('button[data-home-agent]').forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.homeAgent === ui.agent ? 'true' : 'false'); });
    var clip = clipFor(ui.state, files);
    if (ui.base && clip) {
      var src = ui.base + ui.agent + '/' + clip + '.mp4';
      if (files.indexOf('poster.jpg') >= 0) video.poster = ui.base + ui.agent + '/poster.jpg'; else video.removeAttribute('poster');
      if (video.getAttribute('src') !== src) { video.src = src; var p = video.play && video.play(); if (p && p.catch) p.catch(function () {}); }
      stage.dataset.media = 'ready';
    } else {
      video.removeAttribute('src');
      stage.dataset.media = 'missing';
    }
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
      bubble('user', text);
      respond(detectIntent(text));
    });
    input.addEventListener('input', function () { setState(input.value.trim() ? 'listening' : 'idle'); });
    document.querySelectorAll('button[data-home-intent]').forEach(function (b) {
      b.addEventListener('click', function () { lastAsk = ''; bubble('user', (b.querySelector('b') || b).textContent.trim()); respond(b.dataset.homeIntent); });
    });
    document.querySelectorAll('button[data-home-agent]').forEach(function (b) {
      b.addEventListener('click', function () {
        ui.agent = AGENTS[b.dataset.homeAgent] ? b.dataset.homeAgent : 'seven';
        store.set(AGENT_KEY, ui.agent);
        document.body.dataset.agentTheme = ui.agent;
        portrait();
        bubble('agent', AGENTS[ui.agent].name + '입니다. 이어서 도와드릴게요.');
      });
    });
    var again = $('homeWelcomeAgain');
    if (again) again.addEventListener('click', function () { welcome(true); });
  }

  function init() {
    if (!$('homeStage')) return;
    var saved = store.get(AGENT_KEY);
    ui.agent = AGENTS[saved] ? saved : 'seven';
    document.body.dataset.agentTheme = ui.agent;
    ui.user = readUser();
    bind();
    portrait();
    welcome(false);
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
