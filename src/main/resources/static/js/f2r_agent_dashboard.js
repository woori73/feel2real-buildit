/*
 * F2R 통합 AI 운영 대시보드 — 캐릭터 비서(세븐·서수니), 상태창, 서비스 안내/활성화, 화면 전환.
 * 실제 업무 처리(대화·초안·승인)는 기존 unified_assistant.js와 /api/dashboard/assistant/v1이 맡는다.
 * 이 파일은 화면 연출과 서비스 연결만 담당하며 서버에 쓰지 않는다.
 */
(function () {
  'use strict';

  var AGENTS = {
    seven: { key: 'seven', name: '세븐', code: 'H-07', role: '업무 분석 · 전략', greeting: '세븐입니다. 오늘 처리할 업무를 말씀해 주세요. 필요한 서비스를 바로 연결하겠습니다.' },
    suni: { key: 'suni', name: '서수니', code: 'SUNI', role: '업무 확인 · 실행', greeting: '서수니입니다. 확인할 업무부터 알려 주세요. 처리 순서대로 함께 진행하겠습니다.' }
  };
  var DEFAULT_AGENT = 'seven';
  var AGENT_KEY = 'f2r.dashboard.agent';
  var VIEW_KEY = 'f2r.dashboard.view';

  // 서비스 목록: 실제 경로가 있는 서비스만. keywords는 대화 내용으로 서비스를 활성화할 때 쓴다.
  var SERVICES = [
    { id: 'academycraft', name: 'AcademyCraft', desc: '학생·반·출결·상담 관리', href: '/academycraft', keywords: ['학생', '반 ', '반편성', '수업', '출결', '출석', '결석', '지각', '등원', '하원', '상담', '학부모', '강사', '공지'] },
    { id: 'ledger', name: '이지장부', desc: '거래·분류·증빙 확인', href: '/ledger', keywords: ['장부', '거래', '증빙', '영수증', '세금계산서', '카드', '지출', '수입', '매출', '미수금', '재무', '정산'] },
    { id: 'easy', name: '에듀홀릭 장부', desc: '운영자용 복식부기·수납', href: '/easy', keywords: ['복식부기', '수납', '에듀홀릭 장부', '분개'] },
    { id: 'wiki', name: '내신위키', desc: '내신 문제 제작·관리', href: '/problem-generator', keywords: ['내신', '문제', '시험지', '예상문제', '문항', '기출', '위키'] },
    { id: 'testprep', name: 'TESTPREP', desc: '모의고사 응시·성적', href: '/testprep', keywords: ['모의고사', 'testprep', '모의 시험', '성적'] },
    { id: 'admissions', name: '입시상담 AI', desc: '대학·전형 상담', href: '/admissions/workspace', keywords: ['입시', '대학', '수시', '정시', '전형', '학종', '원서'] },
    { id: 'builder', name: '홈페이지 빌더', desc: '학원 홈페이지 제작', href: '/builder', keywords: ['홈페이지', '사이트', '빌더', '웹사이트', '블로그'] }
  ];

  var STATES = {
    idle: { label: '대기', clip: 'idle' },
    analyze: { label: '분석 중', clip: 'talk' },
    talk: { label: '응답', clip: 'talk' },
    done: { label: '업무 완료', clip: 'idle' }
  };

  // ---- 순수 규칙 (단위 테스트 대상) ----
  function pickAgent(saved) {
    return Object.prototype.hasOwnProperty.call(AGENTS, saved) ? saved : DEFAULT_AGENT;
  }

  function pickView(hash, saved) {
    if (hash === '#all') return 'all';
    if (hash === '#ai') return 'ai';
    return saved === 'all' ? 'all' : 'ai';
  }

  function matchServices(text) {
    var t = String(text || '').toLowerCase();
    if (!t.trim()) return [];
    var found = [];
    SERVICES.forEach(function (s) {
      var score = s.keywords.reduce(function (n, k) { return t.indexOf(k.toLowerCase()) >= 0 ? n + 1 : n; }, 0);
      if (score > 0) found.push({ id: s.id, score: score });
    });
    found.sort(function (a, b) { return b.score - a.score; });
    return found.map(function (f) { return f.id; });
  }

  function countDraftStatus(labels) {
    var out = { pending: 0, done: 0, review: 0 };
    (labels || []).forEach(function (l) {
      if (l === '승인 대기') out.pending++;
      else if (l === '저장 검증 완료') out.done++;
      else if (l === '검토 필요' || l === '결과 확인 필요' || l === '저장 실패') out.review++;
    });
    return out;
  }

  function nextState(current, event) {
    if (event === 'submit') return 'analyze';
    if (event === 'reply') return current === 'analyze' || current === 'idle' || current === 'talk' ? 'talk' : current;
    if (event === 'verified') return 'done';
    if (event === 'timeout') return 'idle';
    return current;
  }

  if (typeof window !== 'undefined') {
    window.f2rAgentDashboard = { AGENTS: AGENTS, SERVICES: SERVICES, pickAgent: pickAgent, pickView: pickView, matchServices: matchServices, countDraftStatus: countDraftStatus, nextState: nextState };
  }
  if (typeof document === 'undefined' || !document.getElementById) return;
  window.f2rAgentDashboard.currentName = function () { return AGENTS[ui.agent] ? AGENTS[ui.agent].name : null; };

  // ---- 화면 ----
  var $ = function (id) { return document.getElementById(id); };
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (_) { /* 저장 불가 환경 */ } }
  };
  var ui = { agent: DEFAULT_AGENT, state: 'idle', timer: null, active: [], missing: {} };

  function mediaBase() {
    var meta = document.querySelector('meta[name="f2r-agent-media"]');
    var base = meta && meta.content ? meta.content : '/agent-media/';
    return base.charAt(base.length - 1) === '/' ? base : base + '/';
  }

  function showEmblem(on) {
    var stage = $('agentStage');
    if (stage) stage.classList.toggle('no-media', !!on);
  }

  function loadClip(clip) {
    var video = $('agentVideo');
    if (!video || ui.missing[ui.agent]) return;
    var src = mediaBase() + ui.agent + '/' + clip + '.mp4';
    if (video.dataset.src === src) return;
    video.dataset.src = src;
    video.poster = mediaBase() + ui.agent + '/poster.jpg';
    video.src = src;
    var p = video.play && video.play();
    if (p && p.catch) p.catch(function () { /* 자동재생 차단 시 포스터 유지 */ });
  }

  function setState(next) {
    ui.state = next;
    var stage = $('agentStage');
    if (stage) stage.dataset.state = next;
    var badge = $('agentStateLabel');
    if (badge) badge.textContent = STATES[next].label;
    loadClip(STATES[next].clip);
    clearTimeout(ui.timer);
    if (next !== 'idle') ui.timer = setTimeout(function () { setState(nextState(ui.state, 'timeout')); }, next === 'analyze' ? 20000 : 7000);
  }

  function renderAgent() {
    var a = AGENTS[ui.agent];
    document.body.dataset.agent = a.key;
    $('agentName').textContent = a.name;
    $('agentCode').textContent = a.code;
    $('agentRole').textContent = a.role;
    $('agentGreeting').textContent = a.greeting;
    $('statusAgent').textContent = a.name + ' (' + a.code + ')';
    document.querySelectorAll('[data-agent-pick]').forEach(function (b) {
      var on = b.dataset.agentPick === a.key;
      b.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    var video = $('agentVideo');
    if (video) video.dataset.src = '';
    showEmblem(!!ui.missing[a.key]);
    loadClip(STATES[ui.state].clip);
  }

  function chooseAgent(key) {
    ui.agent = pickAgent(key);
    store.set(AGENT_KEY, ui.agent);
    renderAgent();
    flash();
  }

  function flash() {
    var stage = $('agentStage');
    if (!stage) return;
    stage.classList.remove('is-flash');
    void stage.offsetWidth;
    stage.classList.add('is-flash');
  }

  function setView(view) {
    document.body.dataset.view = view;
    store.set(VIEW_KEY, view);
    document.querySelectorAll('[data-view-btn]').forEach(function (b) {
      b.setAttribute('aria-pressed', b.dataset.viewBtn === view ? 'true' : 'false');
    });
  }

  function renderServices() {
    var row = $('shortcutRow');
    if (!row) return;
    row.innerHTML = '';
    SERVICES.forEach(function (s) {
      var a = document.createElement('a');
      a.className = 'svc-card';
      a.href = s.href;
      a.dataset.service = s.id;
      var n = document.createElement('strong'); n.textContent = s.name;
      var d = document.createElement('span'); d.textContent = s.desc;
      var badge = document.createElement('em'); badge.className = 'svc-on'; badge.textContent = '연결됨';
      a.append(n, d, badge);
      row.append(a);
    });
  }

  function activate(ids) {
    if (!ids.length) return;
    ui.active = ids.slice(0, 3);
    document.querySelectorAll('[data-service]').forEach(function (el) {
      el.classList.toggle('is-active', ui.active.indexOf(el.dataset.service) >= 0);
    });
    var box = $('agentSuggest');
    var list = $('statusServices');
    box.innerHTML = '';
    var names = [];
    ui.active.forEach(function (id) {
      var s = SERVICES.filter(function (x) { return x.id === id; })[0];
      if (!s) return;
      names.push(s.name);
      var a = document.createElement('a');
      a.href = s.href; a.className = 'suggest-link'; a.textContent = s.name + ' 열기';
      box.append(a);
    });
    box.hidden = false;
    var lead = document.createElement('p');
    lead.textContent = AGENTS[ui.agent].name + ': 관련 서비스를 연결했습니다.';
    box.prepend(lead);
    list.textContent = names.join(' · ');
  }

  function updateProgress() {
    var labels = Array.prototype.map.call(document.querySelectorAll('#uaDrafts .ua-status'), function (e) { return e.textContent; });
    var c = countDraftStatus(labels);
    $('statusPending').textContent = c.pending + '건';
    $('statusDone').textContent = c.done + '건';
    $('statusReview').textContent = c.review + '건';
    return c;
  }

  function watchConversation() {
    var log = $('uaDrafts');
    if (!log || typeof MutationObserver === 'undefined') return;
    var lastDone = 0;
    new MutationObserver(function (records) {
      var reply = records.some(function (r) {
        return Array.prototype.some.call(r.addedNodes, function (n) { return n.classList && (n.classList.contains('ua-assistant') || n.classList.contains('ua-draft')); });
      });
      var c = updateProgress();
      if (c.done > lastDone && ui.state !== 'idle') setState(nextState(ui.state, 'verified'));
      else if (reply && ui.state === 'analyze') setState(nextState(ui.state, 'reply'));
      lastDone = c.done;
    }).observe(log, { childList: true, subtree: true });
  }

  function bindTourView() {
    // 사용법 안내 단계의 대상이 숨겨진 화면에 있으면 그 화면으로 전환한다.
    // 첫 방문 자동 안내는 화면을 바꾸지 않고, 사용자가 안내 버튼을 누른 뒤부터 따라간다.
    var step = $('uaStep');
    if (!step || typeof MutationObserver === 'undefined') return;
    var following = false;
    document.addEventListener('click', function (e) {
      if (!e.target.closest) return;
      if (e.target.closest('#uaResume,#uaNext,#uaBack,[data-ua-purpose]')) following = true;
      if (e.target.closest('#uaResume')) $('uaTour').classList.add('is-floating');
      if (e.target.closest('#uaPause')) { following = false; $('uaTour').classList.remove('is-floating'); }
    }, true);
    new MutationObserver(function () {
      if (!following) return;
      var target = document.querySelector('.ua-tour-target');
      if (!target) return;
      var pane = target.closest('[data-pane]');
      if (pane) setView(pane.dataset.pane);
      target.scrollIntoView({ block: 'center' });
    }).observe(step, { childList: true, characterData: true, subtree: true });
  }

  function init() {
    if (!$('agentStage')) return;
    ui.agent = pickAgent(store.get(AGENT_KEY));
    setView(pickView(location.hash, store.get(VIEW_KEY)));
    renderServices();
    renderAgent();
    setState('idle');

    var video = $('agentVideo');
    if (video) video.addEventListener('error', function () { ui.missing[ui.agent] = true; video.removeAttribute('poster'); showEmblem(true); });
    var portrait = function (img) {
      var off = function () { img.closest('[data-agent-pick]').classList.add('no-portrait'); };
      img.addEventListener('error', off);
      if (img.complete && img.naturalWidth === 0) off();
    };
    document.querySelectorAll('[data-agent-pick] img').forEach(portrait);

    document.querySelectorAll('[data-agent-pick]').forEach(function (b) {
      b.addEventListener('click', function () { chooseAgent(b.dataset.agentPick); });
    });
    document.querySelectorAll('[data-view-btn]').forEach(function (b) {
      b.addEventListener('click', function () { setView(b.dataset.viewBtn); window.scrollTo(0, 0); });
    });

    var form = $('uaCommandForm');
    if (form) form.addEventListener('submit', function () {
      var text = $('uaCommand').value;
      setState(nextState(ui.state, 'submit'));
      activate(matchServices(text));
    }, true);
    document.querySelectorAll('[data-ua-example]').forEach(function (b) {
      b.addEventListener('click', function () { activate(matchServices(b.dataset.uaExample)); }, true);
    });

    var fab = $('aiFab');
    if (fab) fab.addEventListener('click', function () { setView('ai'); }, true);

    watchConversation();
    bindTourView();
    updateProgress();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
