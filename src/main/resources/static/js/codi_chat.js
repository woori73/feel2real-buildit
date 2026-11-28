(function () {
  'use strict';

  const conversationPath = '/api/dashboard/assistant/v1/conversation';
  const looks = { codi: '새싹 코디', seven: '세븐', suni: '서수니' };
  const poses = {
    idle: '자유대화', input: '듣는 중', read: '메시지 확인 중', work: '답변을 생각하는 중',
    write: '답변 작성 중', result: '답변 완료', error: '연결 확인'
  };
  function requestBody(message, conversationId) {
    const payload = { mode: 'general', message, webSearch: false };
    if (conversationId) payload.conversationId = conversationId;
    return payload;
  }

  function sourceUrl(value) {
    try {
      const url = new URL(value);
      if (['http:', 'https:'].includes(url.protocol) && !url.username && !url.password) return url;
    } catch (_) { /* A source without a usable public link stays out of the answer. */ }
    return null;
  }

  function authDestination(response) {
    if (response.status === 401) return '/?login=required&returnUrl=%2Fcodi';
    if (response.status === 403 && response.headers.get('X-Password-Change-Required') === 'true') {
      return '/account/password-change';
    }
    return null;
  }

  function failureMessage(status) {
    switch (status) {
      case 400: return '질문은 2,000자 이내로, 연락처와 개인 식별 정보를 빼고 입력해 주세요.';
      case 403: return '학원 계정으로 로그인했는지 확인해 주세요.';
      case 410: return '이전 대화 연결이 만료됐습니다. 다시 보내면 새 대화로 시작합니다.';
      case 429: return '지금은 대화 사용 한도에 도달했습니다. 잠시 후 다시 보내 주세요.';
      case 502:
      case 503: return '지금은 답변 서비스를 연결할 수 없습니다. 잠시 후 다시 보내 주세요.';
      default: return '답변을 받지 못했습니다. 연결을 확인하고 다시 보내 주세요.';
    }
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = { requestBody, sourceUrl, failureMessage, authDestination };
  if (typeof document === 'undefined') return;

  const $ = id => document.getElementById(id);
  const app = $('codiChat');
  const log = $('codiLog');
  const input = $('codiCommand');
  const menu = $('codiMenu');
  const toggle = $('codiMenuToggle');
  const status = $('codiStatus');
  const avatar = $('codiAvatar');
  const portrait = $('codiPortrait');
  const lookKey = 'f2r.codi.appearance';
  const loginPath = '/?login=required&returnUrl=%2Fcodi';
  let look = 'codi';
  let pose = 'idle';
  let principal = null;
  let authCheck = 0;
  let authenticated = false;
  let conversationId = null;
  let pending = null;
  let composing = false;
  let compositionEnded = -Infinity;
  let readTimer = 0;

  function setPose(next) {
    pose = next;
    avatar.dataset.look = look;
    avatar.dataset.pose = pose;
    avatar.setAttribute('aria-label', looks[look] + ', ' + poses[pose]);
    $('codiPoseText').textContent = poses[pose];
    const src = look === 'codi' ? '/img/agents/codi/' + pose + '.webp?v=matte-2' : '/img/landing/home-agent-' + look + '.webp';
    if (portrait.getAttribute('src') !== src) portrait.src = src;
    document.querySelectorAll('[data-codi-look]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.codiLook === look));
    });
  }

  function sizeInput() {
    input.style.height = 'auto';
    input.style.height = input.scrollHeight + 'px';
    $('codiSend').disabled = !authenticated || Boolean(pending) || !input.value.trim();
  }

  function setMenu(open, focusButton) {
    menu.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    if (open) menu.querySelector('[aria-pressed=true]').focus();
    else if (focusButton) toggle.focus();
  }

  function nearEnd() { return log.scrollHeight - log.clientHeight - log.scrollTop < 42; }
  function syncLatest() { $('codiLatest').hidden = nearEnd(); }

  function viewport() {
    const vv = window.visualViewport;
    const height = Math.round(vv ? vv.height : window.innerHeight);
    app.style.setProperty('--codi-height', height + 'px');
    app.style.setProperty('--codi-top', Math.max(0, vv?.offsetTop || 0) + 'px');
    app.classList.toggle('compact', height < 520 || (document.activeElement === input && window.innerHeight - height > 100));
    sizeInput();
    syncLatest();
  }

  function clearChat() {
    if (pending) pending.abort();
    pending = null;
    clearTimeout(readTimer);
    conversationId = null;
    input.value = '';
    composing = false;
    log.replaceChildren();
    app.classList.remove('has-chat');
    status.replaceChildren();
    setMenu(false);
    setPose('idle');
    sizeInput();
    syncLatest();
  }

  function leaveSession(path) {
    authenticated = false;
    principal = null;
    authCheck++;
    clearChat();
    if (path === loginPath) {
      try { sessionStorage.removeItem('currentUser'); } catch (_) {}
    }
    window.location.replace(path);
  }

  function showAccountRequired() {
    principal = null;
    clearChat();
    status.textContent = '학원 계정으로 로그인해 주세요. ';
    const link = document.createElement('a');
    link.href = loginPath;
    link.textContent = '로그인';
    status.append(link);
  }

  async function refreshSession() {
    const current = ++authCheck;
    authenticated = false;
    app.classList.add('checking');
    sizeInput();
    try {
      const response = await fetch('/api/auth/me', { credentials: 'same-origin', cache: 'no-store' });
      if (current !== authCheck) return;
      const destination = authDestination(response);
      if (destination) { leaveSession(destination); return; }
      if (response.status === 403) { showAccountRequired(); return; }
      if (!response.ok) throw new Error();
      const user = await response.json();
      if (current !== authCheck) return;
      if (user.passwordChangeRequired) { leaveSession('/account/password-change'); return; }
      const id = user.userId ?? user.id;
      if (!['ADMIN', 'TUTOR'].includes(user.role) || id == null) { showAccountRequired(); return; }
      const next = user.role + '/' + id;
      if (principal !== null && principal !== next) clearChat();
      principal = next;
      authenticated = true;
      if (status.querySelector('[data-auth-retry]')) status.replaceChildren();
      app.classList.remove('checking');
    } catch (_) {
      if (current !== authCheck) return;
      status.textContent = '로그인 상태를 확인할 수 없습니다. ';
      const retry = document.createElement('button');
      retry.type = 'button';
      retry.dataset.authRetry = '';
      retry.textContent = '다시 확인';
      retry.addEventListener('click', refreshSession);
      status.append(retry);
    } finally {
      if (current === authCheck) sizeInput();
    }
  }

  function bubble(question, message) {
    const node = document.createElement('article');
    node.className = question ? 'codi-question' : 'codi-answer';
    node.setAttribute('aria-label', question ? '내 메시지' : '코디 답변');
    const text = document.createElement('p');
    text.textContent = message;
    node.append(text);
    log.append(node);
    return node;
  }

  function appendSources(row, sources) {
    const list = document.createElement('ul');
    list.setAttribute('aria-label', '답변 출처');
    for (const item of Array.isArray(sources) ? sources : []) {
      const url = sourceUrl(item?.url);
      if (!url) continue;
      const link = document.createElement('a');
      link.href = url.href;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = typeof item.title === 'string' && item.title.trim() ? item.title : url.hostname;
      const li = document.createElement('li');
      li.append(link);
      list.append(li);
    }
    if (list.childElementCount) row.append(list);
  }

  async function send(message, failedRow) {
    if (!authenticated || pending || !message.trim() || message.length > 2000) return;
    const controller = new AbortController();
    pending = controller;
    if (failedRow) failedRow.remove();
    const original = input.value;
    if (!failedRow || original.trim() === message) input.value = '';
    status.replaceChildren();
    app.classList.add('has-chat');
    const question = bubble(true, message);
    log.scrollTop = log.scrollHeight;
    setPose('work');
    sizeInput();
    try {
      const response = await fetch(conversationPath, {
        method: 'POST', credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(requestBody(message, conversationId))
      });
      if (pending !== controller) return;
      const destination = authDestination(response);
      if (destination) { leaveSession(destination); return; }
      if (response.status === 410) conversationId = null;
      if (!response.ok) throw new Error(failureMessage(response.status));
      setPose('write');
      const answer = await response.json();
      if (pending !== controller) return;
      if (typeof answer.message !== 'string' || !answer.message.trim()) throw new Error(failureMessage());
      conversationId = typeof answer.data?.conversationId === 'string' ? answer.data.conversationId : null;
      const follow = nearEnd();
      const row = bubble(false, answer.message);
      appendSources(row, answer.data?.sources);
      if (follow) row.scrollIntoView({ block: 'start' });
      setPose('result');
    } catch (error) {
      if (pending !== controller) return;
      status.textContent = error instanceof TypeError || error instanceof SyntaxError ? failureMessage() : error.message;
      setPose('error');
      if (!input.value) {
        input.value = original.trim() === message ? original : message;
        question.remove();
      } else {
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.textContent = '이 메시지 다시 보내기';
        retry.addEventListener('click', () => send(message, question));
        question.append(retry);
      }
    } finally {
      if (pending === controller) {
        pending = null;
        sizeInput();
        syncLatest();
      }
    }
  }

  try {
    const saved = localStorage.getItem(lookKey);
    if (Object.hasOwn(looks, saved)) look = saved;
  } catch (_) {}
  setPose('idle');
  toggle.addEventListener('click', () => setMenu(menu.hidden));
  menu.addEventListener('click', event => {
    const choice = event.target.closest('[data-codi-look]');
    if (!choice || !Object.hasOwn(looks, choice.dataset.codiLook)) return;
    look = choice.dataset.codiLook;
    try { localStorage.setItem(lookKey, look); } catch (_) {}
    setPose(pose);
    setMenu(false, true);
  });
  document.addEventListener('pointerdown', event => {
    if (!menu.hidden && !menu.contains(event.target) && !toggle.contains(event.target)) setMenu(false);
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !menu.hidden) setMenu(false, true);
  });
  $('codiComposer').addEventListener('submit', event => {
    event.preventDefault();
    if (composing) {
      input.blur();
      setTimeout(() => send(input.value.trim()), 0);
    } else {
      void send(input.value.trim());
    }
  });
  input.addEventListener('compositionstart', () => { composing = true; sizeInput(); });
  input.addEventListener('compositionend', () => { composing = false; compositionEnded = performance.now(); sizeInput(); });
  input.addEventListener('keydown', event => {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || composing || event.keyCode === 229 || performance.now() - compositionEnded < 45) return;
    if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return;
    event.preventDefault();
    void send(input.value.trim());
  });
  input.addEventListener('input', () => {
    if (!pending) setPose(input.value.trim() ? 'input' : 'idle');
    sizeInput();
  });
  function markReading() {
    if (!pending && !nearEnd() && !input.value && log.childElementCount) {
      setPose('read');
      clearTimeout(readTimer);
      readTimer = setTimeout(() => { if (pose === 'read') setPose('idle'); }, 1300);
    }
  }
  log.addEventListener('scroll', syncLatest, { passive: true });
  log.addEventListener('wheel', markReading, { passive: true });
  log.addEventListener('touchmove', markReading, { passive: true });
  $('codiLatest').addEventListener('click', () => { log.scrollTop = log.scrollHeight; syncLatest(); });
  window.visualViewport?.addEventListener('resize', viewport);
  window.visualViewport?.addEventListener('scroll', viewport);
  window.addEventListener('resize', viewport);
  window.addEventListener('pagehide', () => {
    authCheck++;
    authenticated = false;
    principal = null;
    clearChat();
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { app.classList.add('checking'); authenticated = false; authCheck++; sizeInput(); }
    else void refreshSession();
  });
  window.addEventListener('pageshow', event => { if (event.persisted) void refreshSession(); });
  viewport();
  void refreshSession();
})();
