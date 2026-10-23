/*
 * F2R 통합 AI 운영 대시보드 — 캐릭터 중심 대화 콘솔(세븐·서수니).
 * - 실제 업무 처리(대화·초안·승인·저장)는 기존 unified_assistant.js + /api/dashboard/assistant/v1이 맡는다.
 * - 이 파일은 캐릭터 상태(대기·듣기·생각·응답·완료), 응답 표시, 서비스 활성화, 화면 전환만 담당하며 서버에 쓰지 않는다.
 * - 캐릭터 영상·이미지는 공개 저장소가 아닌 비공개 폴더에서 /dashboard/agent-media/** 로 받는다(ADMIN·TUTOR 전용).
 * - 음성·립싱크는 window.F2RAgentVoice.register(adapter)로 연결한다. 연결 전에는 텍스트 응답으로만 표시하고
 *   말하는 영상처럼 꾸미지 않는다.
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
  var MEDIA_API = '/api/dashboard/agent-media';
  var MEDIA_BASE = '/dashboard/agent-media/';

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

  // 상태별 표시 문구와 영상. speaking은 음성 연결 여부에 따라 아래 speakingClip이 결정한다.
  var STATES = {
    idle: { label: '대기', clip: 'idle' },
    listening: { label: '듣는 중', clip: 'idle' },
    thinking: { label: '생각 중', clip: 'think' },
    speaking: { label: '답변 표시 중', clip: 'idle' },
    done: { label: '업무 완료', clip: 'idle' }
  };

  // 정본 승인 상태: 서버 manifest 값. APPROVED만 정본이며 나머지는 확정이 아님을 화면에 표시한다.
  var BADGES = {
    APPROVED: '정본',
    REFERENCE_APPROVED: '승인 얼굴 기준 · 영상 확정 전',
    CANDIDATE: '외형 승인 전 후보',
    MISSING: '캐릭터 자료 미연결'
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

  // 대화 사건 → 캐릭터 상태. 응답 도착 전 입력·초점 변화가 '생각 중'을 끊지 않도록 막는다.
  function nextState(current, event) {
    switch (event) {
      case 'typing': return current === 'idle' || current === 'done' ? 'listening' : current;
      case 'cleared': return current === 'listening' ? 'idle' : current;
      case 'submit': return 'thinking';
      case 'reply': return current === 'thinking' || current === 'listening' || current === 'idle' ? 'speaking' : current;
      case 'spoken': return current === 'speaking' ? 'idle' : current;
      case 'verified': return 'done';
      case 'timeout': return current === 'listening' ? current : 'idle';
      default: return current;
    }
  }

  function badgeFor(status) {
    return BADGES[status] || BADGES.CANDIDATE;
  }

  // 음성 어댑터 능력 → 응답 방식. 어댑터가 없으면 텍스트 응답만 한다.
  function speakingMode(adapter) {
    if (!adapter || typeof adapter.speak !== 'function') return 'text';
    return adapter.lipSync === true ? 'voice-lipsync' : 'voice';
  }

  // 응답 중 영상: 립싱크 어댑터와 speak.mp4가 모두 있을 때만 말하는 영상을 쓴다.
  function speakingClip(mode, files) {
    return mode === 'voice-lipsync' && (files || []).indexOf('speak.mp4') >= 0 ? 'speak' : 'idle';
  }

  // 요청한 영상이 없으면 대기 영상으로, 대기 영상도 없으면 null(표식 표시).
  function resolveClip(clip, files) {
    var list = files || [];
    if (list.indexOf(clip + '.mp4') >= 0) return clip;
    return list.indexOf('idle.mp4') >= 0 ? 'idle' : null;
  }

  var api = {
    AGENTS: AGENTS, SERVICES: SERVICES, STATES: STATES, pickAgent: pickAgent, pickView: pickView, matchServices: matchServices,
    countDraftStatus: countDraftStatus, nextState: nextState, badgeFor: badgeFor, speakingMode: speakingMode,
    speakingClip: speakingClip, resolveClip: resolveClip
  };
  if (typeof window !== 'undefined') window.f2rAgentDashboard = api;
  if (typeof document === 'undefined' || !document.getElementById) return;

  // ---- 화면 ----
  var $ = function (id) { return document.getElementById(id); };
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (_) { /* 저장 불가 환경 */ } }
  };
  var ui = { agent: DEFAULT_AGENT, state: 'idle', timer: null, typing: null, media: null, clip: null, front: 0, awaiting: false, voice: null };
  api.currentName = function () { return AGENTS[ui.agent] ? AGENTS[ui.agent].name : null; };
  api.state = function () { return ui.state; };

  // 음성·립싱크 연결 지점. adapter = { speak(text, {agent, onStart, onEnd, onViseme}) → Promise, stop(), lipSync: bool }
  window.F2RAgentVoice = {
    register: function (adapter) { ui.voice = adapter || null; renderMode(); },
    mode: function () { return speakingMode(ui.voice); }
  };

  function files() {
    var a = ui.media && ui.media.agents && ui.media.agents[ui.agent];
    return a && a.files ? a.files : [];
  }
  function mediaStatus() {
    var a = ui.media && ui.media.agents && ui.media.agents[ui.agent];
    return a ? a.status : 'MISSING';
  }

  function clips() { return [$('agentVideo'), $('agentVideoAlt')]; }

  // 두 영상 요소를 번갈아 쓰며 다음 영상이 재생 준비된 뒤 겹쳐 바꾼다(끊김·얼굴 급변 방지).
  function showClip(wanted) {
    var stage = $('agentStage');
    var clip = resolveClip(wanted, files());
    if (!clip) { stage.dataset.media = ui.media ? 'missing' : 'loading'; ui.clip = null; return; }
    stage.dataset.media = 'ready';
    var src = MEDIA_BASE + ui.agent + '/' + clip + '.mp4';
    if (ui.clip === src) return;
    ui.clip = src;
    var pair = clips(), cur = pair[ui.front], next = pair[1 - ui.front];
    if (!cur || !next) return;
    // 이미 화면에 나온 영상이면 다시 불러오지 않는다(같은 영상 재적재로 생기는 깜박임 방지).
    if (cur.getAttribute('src') === src && cur.classList.contains('is-on')) { next.removeAttribute('src'); return; }
    var swapped = false;
    var swap = function () {
      if (swapped || ui.clip !== src) return;
      swapped = true;
      var p = next.play && next.play();
      if (p && p.catch) p.catch(function () { /* 자동재생 차단 시 정지 화면 유지 */ });
      next.classList.add('is-on');
      cur.classList.remove('is-on');
      ui.front = 1 - ui.front;
      setTimeout(function () { if (!cur.classList.contains('is-on') && cur.pause) cur.pause(); }, 500);
    };
    next.poster = MEDIA_BASE + ui.agent + '/poster.jpg';
    next.src = src;
    if (next.readyState >= 2) swap();
    else {
      next.addEventListener('canplay', swap, { once: true });
      setTimeout(function () { if (next.readyState >= 1) swap(); }, 1500);
    }
  }

  function setState(next) {
    ui.state = next;
    $('agentStage').dataset.state = next;
    document.body.dataset.agentState = next;
    var label = STATES[next].label;
    if (next === 'speaking' && speakingMode(ui.voice) !== 'text') label = '말하는 중';
    $('agentStateLabel').textContent = label;
    showClip(next === 'speaking' ? speakingClip(speakingMode(ui.voice), files()) : STATES[next].clip);
    clearTimeout(ui.timer);
    if (next === 'thinking') ui.timer = setTimeout(function () { setState(nextState(ui.state, 'timeout')); }, 30000);
    if (next === 'done') ui.timer = setTimeout(function () { setState(nextState(ui.state, 'timeout')); }, 6000);
  }

  function renderMode() {
    var mode = speakingMode(ui.voice);
    var el = $('speechMode');
    if (el) el.textContent = mode === 'text' ? '텍스트 응답 · 음성 미연결' : mode === 'voice' ? '음성 응답' : '음성·입 모양 연동';
  }

  function renderBadge() {
    var status = mediaStatus();
    var badge = $('agentBadge');
    badge.textContent = badgeFor(status);
    badge.dataset.status = status;
    badge.hidden = status === 'APPROVED';
  }

  function renderAgent() {
    var a = AGENTS[ui.agent];
    document.body.dataset.agent = a.key;
    $('agentName').textContent = a.name;
    $('agentCode').textContent = a.code;
    $('agentRole').textContent = a.role;
    $('speechName').textContent = a.name;
    $('statusAgent').textContent = a.name + ' (' + a.code + ')';
    var mini = $('agentMiniImg');
    if (mini) mini.src = MEDIA_BASE + a.key + '/front.webp';
    document.querySelectorAll('[data-agent-pick]').forEach(function (b) {
      b.setAttribute('aria-pressed', b.dataset.agentPick === a.key ? 'true' : 'false');
    });
    renderBadge();
    ui.clip = null;
    showClip(ui.state === 'speaking' ? speakingClip(speakingMode(ui.voice), files()) : STATES[ui.state].clip);
  }

  function say(text, opts) {
    var el = $('agentGreeting');
    clearInterval(ui.typing);
    el.classList.remove('is-typing');
    var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var mode = speakingMode(ui.voice);
    var finish = function () {
      el.textContent = text;
      el.classList.remove('is-typing');
      if (!(opts && opts.keepState)) setState(nextState(ui.state, 'spoken'));
    };
    if (mode !== 'text') {
      // 음성 어댑터가 있으면 실제 발화 시간에 맞춰 상태를 바꾼다.
      el.textContent = text;
      Promise.resolve(ui.voice.speak(text, { agent: ui.agent })).then(finish, finish);
      return;
    }
    if (reduce || !text) { finish(); return; }
    var i = 0, step = Math.max(1, Math.ceil(text.length / 60));
    el.textContent = '';
    el.classList.add('is-typing');
    ui.typing = setInterval(function () {
      i = Math.min(text.length, i + step);
      el.textContent = text.slice(0, i);
      if (i >= text.length) { clearInterval(ui.typing); setTimeout(finish, 900); }
    }, 24);
  }

  function chooseAgent(key) {
    ui.agent = pickAgent(key);
    store.set(AGENT_KEY, ui.agent);
    renderAgent();
    var stage = $('agentStage');
    stage.classList.remove('is-flash'); void stage.offsetWidth; stage.classList.add('is-flash');
    if (ui.state === 'idle' && !ui.awaiting) say(AGENTS[ui.agent].greeting, { keepState: true });
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
    row.innerHTML = '';
    SERVICES.forEach(function (s) {
      var a = document.createElement('a');
      a.className = 'svc-card'; a.href = s.href; a.dataset.service = s.id;
      var n = document.createElement('strong'); n.textContent = s.name;
      var d = document.createElement('span'); d.textContent = s.desc;
      var badge = document.createElement('em'); badge.className = 'svc-on'; badge.textContent = '연결됨';
      a.append(n, d, badge);
      row.append(a);
    });
  }

  function activate(ids) {
    var active = ids.slice(0, 3);
    document.querySelectorAll('[data-service]').forEach(function (el) {
      el.classList.toggle('is-active', active.indexOf(el.dataset.service) >= 0);
    });
    var tags = $('statusServices');
    tags.innerHTML = '';
    var box = $('agentSuggest');
    box.querySelectorAll('a[data-svc-link]').forEach(function (x) { x.remove(); });
    active.forEach(function (id) {
      var s = SERVICES.filter(function (x) { return x.id === id; })[0];
      var tag = document.createElement('em'); tag.textContent = s.name; tags.append(tag);
      var a = document.createElement('a'); a.href = s.href; a.dataset.svcLink = s.id; a.textContent = s.name + ' 열기'; box.append(a);
    });
    box.hidden = !box.children.length;
  }

  function offerApproval(card) {
    var box = $('agentSuggest');
    var old = box.querySelector('button[data-draft]');
    if (old) old.remove();
    var b = document.createElement('button');
    b.type = 'button'; b.dataset.draft = card.dataset.uaDraft;
    b.textContent = (card.querySelector('header strong') || {}).textContent + ' 초안 확인하고 승인';
    b.addEventListener('click', function () {
      var target = document.querySelector('[data-ua-draft="' + b.dataset.draft + '"]');
      if (!target) return;
      target.classList.add('is-focus');
      target.scrollIntoView({ block: 'center', behavior: 'smooth' });
      var approve = target.querySelector('.ua-primary');
      if (approve) approve.focus({ preventScroll: true });
    });
    box.prepend(b);
    box.hidden = false;
  }

  function updateProgress() {
    var labels = Array.prototype.map.call(document.querySelectorAll('#uaDrafts .ua-status'), function (e) { return e.textContent; });
    var c = countDraftStatus(labels);
    $('statusPending').textContent = c.pending + '건';
    $('statusDone').textContent = c.done + '건';
    $('statusReview').textContent = c.review + '건';
    return c;
  }

  // 대화 기록의 변화(응답 말풍선·초안 카드·승인 결과)를 캐릭터 응답과 상태로 연결한다.
  function watchConversation() {
    var log = $('uaDrafts');
    var lastDone = 0;
    new MutationObserver(function (records) {
      var added = [];
      records.forEach(function (r) { Array.prototype.forEach.call(r.addedNodes, function (n) { if (n.nodeType === 1) added.push(n); }); });
      var c = updateProgress();
      // 처음 불러온 기록·다른 화면의 승인 결과는 캐릭터 반응 대상이 아니다. 이 화면에서 보낸 요청의 결과만 반응한다.
      var verifiedNow = ui.awaiting && c.done > lastDone;
      lastDone = c.done;
      if (!ui.awaiting) return;
      var replies = added.filter(function (n) { return n.classList.contains('ua-assistant'); });
      var drafts = added.filter(function (n) { return n.classList.contains('ua-draft'); });
      drafts.forEach(function (d) {
        var st = d.querySelector('.ua-status');
        if (st && st.textContent === '승인 대기') offerApproval(d);
      });
      if (verifiedNow) {
        ui.awaiting = false;
        var done = added.filter(function (n) { return n.classList.contains('ua-draft'); }).pop();
        var msg = done && done.querySelector('.ua-result p') ? done.querySelector('.ua-result p').textContent : '저장 결과를 다시 조회했습니다.';
        var pending = $('agentSuggest').querySelector('button[data-draft]');
        if (pending && done && pending.dataset.draft === done.dataset.uaDraft) pending.remove();
        setState(nextState(ui.state, 'verified'));
        say('처리했습니다. ' + msg, { keepState: true });
        return;
      }
      var settled = drafts.filter(function (d) { var st = d.querySelector('.ua-status'); return st && st.textContent !== '승인 대기'; });
      if (replies.length || settled.length) {
        ui.awaiting = false;
        var text = replies.map(function (n) { var p = n.querySelector('p'); return p ? p.textContent : ''; })
          .concat(settled.map(function (d) { return (d.querySelector('header strong') || {}).textContent + ': ' + d.querySelector('.ua-status').textContent; }))
          .join('\n');
        setState(nextState(ui.state, 'reply'));
        say(text);
      }
    }).observe(log, { childList: true, subtree: true });
    // 승인 버튼을 누르면 결과가 올 때까지 '생각 중'
    log.addEventListener('click', function (e) {
      var b = e.target.closest && e.target.closest('.ua-draft .ua-actions button');
      if (!b) return;
      ui.awaiting = true;
      setState(nextState(ui.state, 'submit'));
    }, true);
  }

  function bindComposer() {
    var input = $('uaCommand');
    input.addEventListener('input', function () {
      setState(nextState(ui.state, input.value.trim() ? 'typing' : 'cleared'));
    });
    input.addEventListener('blur', function () { if (!input.value.trim()) setState(nextState(ui.state, 'cleared')); });
    $('uaCommandForm').addEventListener('submit', function () {
      if (!input.value.trim()) return;
      ui.awaiting = true;
      activate(matchServices(input.value));
      setState(nextState(ui.state, 'submit'));
      $('agentGreeting').textContent = '';
      $('agentGreeting').classList.add('is-typing');
    }, true);
    document.querySelectorAll('[data-ua-example]').forEach(function (b) {
      b.addEventListener('click', function () { activate(matchServices(b.dataset.uaExample)); }, true);
    });
  }

  function bindTourView() {
    // 첫 방문 자동 안내는 화면을 바꾸지 않고, 사용자가 안내 버튼을 누른 뒤부터 대상 화면으로 따라간다.
    var step = $('uaStep');
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

  function loadMedia() {
    return fetch(MEDIA_API, { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .catch(function () { return null; })
      .then(function (m) { ui.media = m || { configured: false, agents: {} }; renderAgent(); });
  }

  function init() {
    if (!$('agentStage') || typeof MutationObserver === 'undefined') return;
    ui.agent = pickAgent(store.get(AGENT_KEY));
    setView(pickView(location.hash, store.get(VIEW_KEY)));
    renderServices();
    renderMode();
    renderAgent();
    setState('idle');
    say(AGENTS[ui.agent].greeting, { keepState: true });
    loadMedia();

    clips().forEach(function (v) {
      v.addEventListener('error', function () {
        // 서버가 자료를 알려줬지만 재생에 실패하면 표식으로 대체한다(다른 얼굴을 대신 보여주지 않는다).
        if (v.classList.contains('is-on') || !clips().some(function (x) { return x.classList.contains('is-on') && x.readyState >= 2; })) $('agentStage').dataset.media = 'missing';
      });
    });
    document.querySelectorAll('[data-agent-pick] img').forEach(function (img) {
      var off = function () { img.closest('[data-agent-pick]').classList.add('no-portrait'); };
      img.addEventListener('error', off);
      if (img.complete && img.naturalWidth === 0) off();
    });
    document.querySelectorAll('[data-agent-pick]').forEach(function (b) {
      b.addEventListener('click', function () { chooseAgent(b.dataset.agentPick); });
    });
    document.querySelectorAll('[data-view-btn]').forEach(function (b) {
      b.addEventListener('click', function () { setView(b.dataset.viewBtn); window.scrollTo(0, 0); });
    });
    var fab = $('aiFab');
    if (fab) fab.addEventListener('click', function () { setView('ai'); }, true);

    bindComposer();
    watchConversation();
    bindTourView();
    updateProgress();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
