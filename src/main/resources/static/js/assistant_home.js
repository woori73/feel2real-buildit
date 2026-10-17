/**
 * BuildIt 대화형 홈페이지 화면 (2-A 생성 · 2-B 미리보기 · 2-C 대화 수정/되돌리기).
 * API: /api/tutor/academy/assistant/{samples,state,turn,undo,publish,versions,restore,profile} (docs/API.md)
 *
 * 화면 흐름: 시작 화면(신규) → 생성 대화(부족한 정보 되묻기) → 작업 화면(결과물 미리보기 + 하단 대화 입력).
 * 작업 화면의 모든 수정은 비공개 작업본에만 적용되고, 공개 홈페이지는 '게시하기'에서만 바뀐다.
 * 서버가 준 문자열은 textContent로만 넣는다(XSS 방지).
 */
(function () {
  'use strict';
  var API = '/api/tutor/academy/assistant';
  var PREVIEW = '/tutor/academy/preview';
  var DEVICES = { mobile: 390, tablet: 820, desktop: 1280 };
  var QUESTIONS = {
    academyName: '학원 이름을 알려 주세요.',
    description: '어떤 학원인지 한두 문장으로 알려 주세요. (예: 중학생 영어 내신 전문)',
    region: '학원이 있는 지역을 알려 주세요. 주소 전체가 아니라 \'서울 강남구\'처럼 지역만 있으면 돼요.',
    subject: '어떤 과목을 가르치나요? (예: 영어, 수학)',
    target: '어떤 학생을 가르치나요? (예: 초등학생, 중학생, 고등학생)'
  };
  var FIELD_OF_NEED = { academyName: 'name', description: 'description', region: 'region', subject: 'subject', target: 'target' };
  var CREATE_STEPS = ['학원 정보 정리', '소개 문구 작성', '디자인 적용', '미리보기 준비'];
  var EDIT_STEPS = ['요청을 이해하는 중', '변경 계획을 세우는 중', '작업본에 적용하는 중', '미리보기를 새로 그리는 중'];
  var CHIPS = ['더 고급스럽게 바꿔 줘', '밝고 친근한 분위기로', '상담 신청을 맨 위로'];

  var $ = function (id) { return document.getElementById(id); };
  var ctx = {
    name: '', description: '', region: '', subject: '', target: '', sampleKey: null, pendingNeed: null,
    created: false, revision: null, status: null, publicUrl: null, busy: false, undoDepth: 0, redoDepth: 0, savedRevision: null, savedAt: null, dirty: false, dev: 'mobile'
  };
  var history = [];

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function api(method, path, body, opts) {
    var isForm = typeof FormData !== 'undefined' && body instanceof FormData;
    var ctl = (opts && opts.timeoutMs && typeof AbortController !== 'undefined') ? new AbortController() : null;
    var timer = ctl ? setTimeout(function () { ctl.abort(); }, opts.timeoutMs) : null;
    return fetch(API + path, {
      method: method, credentials: 'same-origin',
      headers: body && !isForm ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
      signal: ctl ? ctl.signal : undefined
    }).then(function (r) {
      if (timer) clearTimeout(timer);
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) {
          var err = new Error(j.message || '요청을 처리하지 못했어요.');
          err.status = r.status; err.code = j.code; err.details = j.details; throw err;
        }
        syncStack(j);
        return j;
      });
    }, function (e) {
      if (timer) clearTimeout(timer);
      if (e && e.name === 'AbortError') { var te = new Error(AssistantWait.timeoutMessage()); te.timeout = true; throw te; }
      throw e;
    });
  }
  function toast(msg) {
    var t = $('ahToast'); t.textContent = msg; t.hidden = false;
    clearTimeout(toast.h); toast.h = setTimeout(function () { t.hidden = true; }, 3500);
  }

  /* ───── 시작 화면 · 생성 대화 ───── */
  function showChat() { $('ahStart').hidden = true; $('ahChat').hidden = false; }
  function scrollDown() { var m = $('ahMessages'); m.scrollTop = m.scrollHeight; }
  function bubble(role, text) { var b = el('div', 'ah-msg ' + role, text); $('ahMessages').appendChild(b); scrollDown(); return b; }

  /** 진행 상태 표시: steps를 순서대로 'now'로 옮기고, 마지막 단계에서 응답을 기다린다. */
  function progress(listEl, steps, intervalMs) {
    listEl.textContent = '';
    var items = steps.map(function (t) { var li = el('li', null, t); listEl.appendChild(li); return li; });
    var i = 0; items[0].className = 'now';
    var timer = setInterval(function () {
      if (i < items.length - 1) { items[i].className = 'done'; i++; items[i].className = 'now'; }
    }, intervalMs);
    return { stop: function () { clearInterval(timer); } };
  }

  function sendCreateTurn() {
    if (ctx.busy) return;
    ctx.busy = true; setCreateBusy(true);
    var b = el('div', 'ah-msg bot'); var ul = el('ul', 'ah-steps'); b.appendChild(ul);
    $('ahMessages').appendChild(b); scrollDown();
    var p = progress(ul, CREATE_STEPS, 2200);
    api('POST', '/turn', {
      academyName: ctx.name || undefined, sampleKey: ctx.sampleKey, message: ctx.description,
      region: ctx.region, subject: ctx.subject, target: ctx.target, instant: true
    }).then(function (r) {
      p.stop(); if (b.parentNode) b.parentNode.removeChild(b);
      if (r.created) {
        ctx.created = true; ctx.revision = r.revision; ctx.status = 'DRAFT'; ctx.pendingNeed = null; ctx.dirty = true; ctx.undoDepth = 0; ctx.redoDepth = 0; ctx.savedRevision = null;
        history = [{ role: 'USER', kind: 'TEXT', content: ctx.description || '샘플로 시작' },
          { role: 'ASSISTANT', kind: 'RESULT', content: r.reply }];
        enterWork({ notes: r.notes || [] });
      } else if (r.needs && r.needs.length) {
        ctx.pendingNeed = r.needs[0]; bubble('bot', QUESTIONS[ctx.pendingNeed] || r.reply);
      } else {
        ctx.pendingNeed = null; bubble('bot', r.reply);
      }
    }).catch(function (e) {
      p.stop(); if (b.parentNode) b.parentNode.removeChild(b); bubble('bot', e.message);
    }).then(function () { ctx.busy = false; setCreateBusy(false); });
  }
  function setCreateBusy(b) { $('ahSend').disabled = b; $('ahStartBtn').disabled = b; }

  function submitStart(ev) {
    ev.preventDefault();
    var prompt = $('ahPrompt').value.trim();
    if (!prompt) { $('ahPrompt').focus(); return; }
    ctx.name = ''; ctx.description = prompt;
    showChat();
    bubble('user', prompt);
    sendCreateTurn();
  }
  function submitChat(ev) {
    ev.preventDefault();
    var text = $('ahInput').value.trim(); if (!text || ctx.busy) return;
    $('ahInput').value = ''; bubble('user', text);
    if (ctx.pendingNeed) { ctx[FIELD_OF_NEED[ctx.pendingNeed]] = text; sendCreateTurn(); }
  }

  /* ───── 작업 화면 ───── */
  function enterWork(opts) {
    $('ahApp').classList.add('work');
    $('ahStart').hidden = true; $('ahChat').hidden = true;
    var top = document.querySelector('.ah-top'); if (top) top.hidden = true;
    $('ahWork').hidden = false;
    renderChips(); renderStatus(); renderUndo(); setDevice(ctx.dev);
    renderThread();
    $('ahCard').hidden = false; fitCard(); loadPreview(null);
    if (opts && opts.notes && opts.notes.length) {
      var nb = el('div', 'ah-msg bot'); var nd = el('details', 'ah-log');
      nd.appendChild(el('summary', null, '생성 안내 ' + opts.notes.length + '건')); nd.appendChild(el('div', 'ah-lognote', opts.notes.join('\n')));
      nb.appendChild(nd); $('ahMsgs').appendChild(nb); scrollThread();
    }
  }

  /* ───── 대화(스레드): 말풍선 + 결과 미리보기 카드 ───── */
  function scrollThread() { var t = $('ahThread'); if (t) t.scrollTop = t.scrollHeight; }
  function addBubble(kind, text, changes) {
    var b = el('div', 'ah-msg ' + kind, text);
    if (changes && changes.length) {
      // 변경 내역은 대화를 길게 만들지 않도록 접어 둔다. 필요할 때 '변경 기록'을 펼쳐 확인한다.
      var det = el('details', 'ah-log'); det.appendChild(el('summary', null, '변경 기록 ' + changes.length + '건'));
      var ul = el('ul', 'ah-changes'); renderChanges(ul, changes); det.appendChild(ul); b.appendChild(det);
    }
    $('ahMsgs').appendChild(b); scrollThread(); return b;
  }
  function renderThread() {
    var box = $('ahMsgs'); box.textContent = '';
    // 새 홈페이지 만들기 이전 대화는 접어 두고, 현재 제작 대화만 보여 준다(새로고침 후에도 같다).
    var parts = AssistantExport.splitThread(history);
    if (parts.earlier.length) {
      var old = el('details', 'ah-log ah-earlier'); old.appendChild(el('summary', null, '이전 제작 대화 ' + parts.earlier.length + '건'));
      parts.earlier.forEach(function (m) { old.appendChild(el('div', 'ah-msg ' + (m.role === 'USER' ? 'user' : 'bot'), m.content)); });
      box.appendChild(old);
    }
    parts.current.forEach(function (m) {
      var mine = m.role === 'USER';
      addBubble(mine ? 'user' : 'bot', m.content, !mine && m.payload && m.payload.changes ? m.payload.changes : []);
    });
  }
  /** 이전 호출부 호환: 결과·오류 안내를 대화 말풍선으로 남긴다. */
  function showStrip(text, isError, changes) {
    addBubble(isError ? 'err' : 'bot', text, changes);
    renderUndo();
  }

  function renderStatus() {
    var s = $('ahStatus'); s.textContent = '';
    s.appendChild(el('b', null, ctx.status === 'PUBLISHED' ? '공개 중' : '비공개 초안'));
    if (ctx.dirty) s.appendChild(document.createTextNode(' · 공개본과 다름'));
    s.appendChild(document.createTextNode(isSaved() ? ' · 저장됨' : ' · 저장 안 됨'));
    var pb = $('ahProfileBtn'); if (pb) pb.hidden = ctx.status === 'PUBLISHED';
    var bd = $('ahCardBadge'); if (bd) bd.textContent = ctx.status === 'PUBLISHED' ? (ctx.dirty ? '공개 중 · 수정 있음' : '공개 중') : '비공개 초안';
  }
  /** 서버 응답에 실린 되돌리기·다시 실행·저장점 정보를 화면 상태에 반영한다. */
  function syncStack(j) {
    if (!j || typeof j !== 'object') return;
    if (typeof j.undoDepth === 'number') ctx.undoDepth = j.undoDepth;
    if (typeof j.redoDepth === 'number') ctx.redoDepth = j.redoDepth;
    if ('savedRevision' in j) { ctx.savedRevision = j.savedRevision; ctx.savedAt = j.savedAt || null; }
  }
  function isSaved() { return ctx.savedRevision != null && ctx.savedRevision === ctx.revision; }
  function renderUndo() {
    $('ahUndoCount').textContent = String(ctx.undoDepth);
    $('ahUndo').disabled = ctx.busy || ctx.undoDepth < 1;
    $('ahRedo').disabled = ctx.busy || ctx.redoDepth < 1;
    $('ahReset').disabled = ctx.busy || !ctx.created;
    $('ahNew').disabled = ctx.busy || !ctx.created;
    $('ahSave').disabled = ctx.busy || !ctx.created;
    $('ahPreviewBtn').disabled = ctx.busy || !ctx.created;
    $('ahSave').setAttribute('data-saved', isSaved() ? 'true' : 'false');
    $('ahSave').title = isSaved() ? '저장됨: 이 상태가 초기화 기준이에요' : '저장: 현재 작업본을 저장점으로 기록해요(초기화 기준)';
  }
  function renderChips() {
    var box = $('ahChips'); box.textContent = '';
    CHIPS.forEach(function (c) {
      var b = el('button', 'ah-chip', c); b.type = 'button';
      b.addEventListener('click', function () { sendEdit(c); });
      box.appendChild(b);
    });
  }
  function setDockBusy(b) {
    $('ahWorkSend').disabled = b; $('ahWorkInput').disabled = b; $('ahPhotoBtn').disabled = b; $('ahPublish').disabled = b; $('ahFullPublish').disabled = b;
    Array.prototype.forEach.call($('ahChips').children, function (c) { c.disabled = b; });
    renderUndo();
  }

  /** 변경 요약 목록(이전 → 이후). 섹션이 있는 변경은 '보기'로 미리보기를 그 위치로 스크롤한다. */
  function renderChanges(ul, changes) {
    ul.textContent = '';
    (changes || []).forEach(function (c) {
      var li = el('li');
      li.appendChild(el('b', null, c.label + ': '));
      if (c.before) { li.appendChild(el('s', null, c.before)); li.appendChild(document.createTextNode(' → ')); }
      li.appendChild(document.createTextNode(c.after));
      if (c.sectionId) {
        var go = el('button', null, '보기'); go.type = 'button';
        go.addEventListener('click', function () { openFull(c.sectionId); });
        li.appendChild(go);
      }
      ul.appendChild(li);
    });
  }
  /* 미리보기 — 같은 비공개 작업본 페이지를 기기 폭으로 맞춰 보여 준다 */
  function fit() {
    var stage = $('ahStage'); if (!stage || $('ahFull').hidden) return;
    var W = DEVICES[ctx.dev] || 390;
    var availW = Math.max(200, stage.clientWidth - 24), availH = Math.max(200, stage.clientHeight - 24);
    var scale = Math.min(1, availW / W);
    var wrap = $('ahFrameWrap'), dev = $('ahDevice');
    wrap.style.width = Math.round(W * scale) + 'px'; wrap.style.height = availH + 'px';
    dev.style.width = W + 'px'; dev.style.height = Math.round(availH / scale) + 'px';
    dev.style.transform = 'scale(' + scale + ')';
  }
  function setDevice(dev) {
    ctx.dev = dev;
    Array.prototype.forEach.call(document.querySelectorAll('.ah-seg [data-dev]'), function (b) {
      var on = b.dataset.dev === dev; b.classList.toggle('on', on); b.setAttribute('aria-pressed', String(on));
    });
    fit();
  }
  /** 대화 속 미리보기 카드: 카드 폭에 맞춰 같은 페이지를 PC(1280) 또는 모바일(390) 배치로 그려 크고 또렷하게 보여 준다. */
  function fitCard() {
    var th = $('ahThumb'), fr = $('ahCardFrame'); if (!th || !fr || $('ahCard').hidden) return;
    var inner = th.firstElementChild, w = th.clientWidth; if (!w || !inner) return;
    var desktop = w >= 560, lw = desktop ? 1280 : 390, lh = desktop ? 800 : 420, scale = w / lw;
    var h = Math.round(lh * scale) + 'px';
    th.style.height = h; inner.style.width = w + 'px'; inner.style.height = h;
    fr.style.width = lw + 'px'; fr.style.height = lh + 'px'; fr.style.transform = 'scale(' + scale + ')';
  }
  /**
   * 카드 속 미리보기는 스크롤하지 않는 정지 화면이다. 공개 페이지의 등장 애니메이션(반투명→불투명)이 끝나기 전·보이지 않는 구간에서
   * 글자가 흐리게 남지 않도록, 불러온 뒤 최종 상태로 고정하는 스타일을 넣는다(애니메이션 시간 0, 등장 요소는 처음부터 보임).
   * 내려받는 HTML에는 스크립트가 없어 등장 효과가 일어나지 않으므로 이 스타일이 그대로 남아야 모든 글자가 보인다.
   */
  var CARD_FREEZE_CSS = 'html.js-reveal [data-reveal-item]{opacity:1!important;transform:none!important}' +
    '*,*::before,*::after{animation-duration:0s!important;animation-delay:0s!important;transition-duration:0s!important;transition-delay:0s!important}';
  function freezeCardMotion() {
    var d = null; try { d = $('ahCardFrame').contentDocument; } catch (e) { return; }
    if (!d || !d.head || d.getElementById('ahCardFreeze')) return;
    var st = d.createElement('style'); st.id = 'ahCardFreeze'; st.textContent = CARD_FREEZE_CSS; d.head.appendChild(st);
  }
  /** 더보기 메뉴(학원 정보·버전·내려받기·기존 빌더): 열고 닫는다. 주요 편집 기능은 상단 도구 모음에 있다. */
  function setMenu(open) {
    $('ahMenu').hidden = !open; $('ahMoreBtn').setAttribute('aria-expanded', String(open));
  }
  var previewTimer = null;
  /** 카드 미리보기를 새로 그리고, 전체 화면이 열려 있으면 그것도 새로 그린다. */
  function loadPreview(sectionId) {
    var cf = $('ahCardFrame'); if (cf) cf.src = PREVIEW + '?t=' + Date.now();
    ctx.fullStale = true;
    if (!$('ahFull').hidden) loadFull(sectionId);
  }
  function loadFull(sectionId) {
    var f = $('ahFrame'); f.src = PREVIEW + '?t=' + Date.now(); ctx.fullStale = false;
    if (sectionId) { f.addEventListener('load', function once() { f.removeEventListener('load', once); scrollPreviewTo(sectionId); }); }
  }
  function openFull(sectionId) {
    $('ahFull').hidden = false; fit();
    if (ctx.fullStale || !$('ahFrame').getAttribute('src')) loadFull(sectionId);
    else if (sectionId) scrollPreviewTo(sectionId);
  }
  function closeFull() { $('ahFull').hidden = true; scrollThread(); }
  /** 공개 페이지는 로드 뒤 비동기로 그려지므로 대상 섹션이 생길 때까지 잠시 기다린 뒤 스크롤한다. */
  function scrollPreviewTo(sectionId) {
    clearInterval(previewTimer); var tries = 0;
    previewTimer = setInterval(function () {
      tries++;
      try {
        var doc = $('ahFrame').contentDocument; var t = doc && doc.getElementById(sectionId);
        if (t) { t.scrollIntoView({ block: 'start', behavior: 'smooth' }); clearInterval(previewTimer); }
      } catch (e) { clearInterval(previewTimer); }
      if (tries > 25) clearInterval(previewTimer);
    }, 200);
  }

  /* 수정 턴 */
  function sendEdit(text) {
    text = String(text || '').trim();
    if (!text || ctx.busy) return;
    ctx.busy = true; setDockBusy(true);
    $('ahWorkInput').value = '';
    var ub = addBubble('user', text);
    var busyBox = $('ahBusy'); $('ahThread').insertBefore(busyBox, $('ahCard')); busyBox.hidden = false; scrollThread();
    var p = progress($('ahBusySteps'), EDIT_STEPS, 1600);
    // 오래 걸리면 경과 시간과 안내를 보여 준다. 한도를 넘기면 기다리기를 멈추고 서버 작업본 상태를 다시 읽는다.
    var note = $('ahBusyNote'); var t0 = Date.now(); note.textContent = '';
    var noteTimer = setInterval(function () { note.textContent = AssistantWait.noticeFor(Date.now() - t0); }, 1000);
    var revisionBefore = ctx.revision;
    var stopWait = function () { p.stop(); clearInterval(noteTimer); note.textContent = ''; busyBox.hidden = true; };
    history.push({ role: 'USER', kind: 'TEXT', content: text });
    api('POST', '/turn', { message: text, revision: ctx.revision }, { timeoutMs: AssistantWait.timeoutMs(window.__AH_TURN_TIMEOUT_MS__) }).then(function (r) {
      stopWait();
      if (r.revision != null) ctx.revision = r.revision;
      if (r.undoDepth != null) ctx.undoDepth = r.undoDepth;
      history.push({ role: 'ASSISTANT', kind: r.edited ? 'EDIT' : 'TEXT', content: r.reply, payload: { changes: r.changes } });
      if (r.edited) {
        ctx.dirty = true;
        var first = (r.changes || []).filter(function (c) { return c.sectionId; })[0];
        showStrip(r.reply, false, r.changes);
        loadPreview(first ? first.sectionId : null);
      } else {
        showStrip(r.reply, !!r.failed, []);
      }
      renderStatus();
    }).catch(function (e) {
      stopWait(); history.pop(); if (ub && ub.parentNode) ub.parentNode.removeChild(ub);
      if (e.timeout) {
        $('ahWorkInput').value = text; // 입력 보존: 다시 보낼 수 있다
        // 서버가 늦게라도 끝냈을 수 있으니 서버 작업본을 정본으로 다시 읽는다.
        return refresh().then(function () {
          if (ctx.revision !== revisionBefore) showStrip(AssistantWait.lateAppliedMessage(), false, []);
          else showStrip(AssistantWait.timeoutMessage(), true, []);
        }, function () { showStrip(AssistantWait.timeoutMessage(), true, []); });
      }
      if (e.code === 'AI_TIMEOUT') {
        // 서버 AI 한도 초과: 수정이 아니다. 오류로 표시하고 입력을 돌려놓아 다시 보낼 수 있게 한다(작업본·공개본 그대로).
        $('ahWorkInput').value = text;
        showStrip(e.message, true, []);
        return;
      }
      if (e.status === 409 && e.code === 'REVISION_CONFLICT') {
        showStrip(e.message + ' 최신 작업본을 다시 불러왔어요. 같은 요청을 다시 보내 주세요.', true, []);
        $('ahWorkInput').value = text;
        return refresh();
      }
      showStrip((e.message || '요청을 처리하지 못했어요.') + ' 작업본은 그대로예요.', true, []);
      $('ahWorkInput').value = text; // 입력 보존: 다시 보낼 수 있다
    }).then(function () { ctx.busy = false; setDockBusy(false); });
  }

  /** 사진 올리기 — 작업본의 이미지 예약으로만 저장된다(공개는 게시 전까지 그대로). 되돌리기 가능. */
  function uploadPhoto(slot, file) {
    if (!file || ctx.busy) return;
    ctx.busy = true; setDockBusy(true);
    var busyBox = $('ahBusy'); $('ahThread').insertBefore(busyBox, $('ahCard')); busyBox.hidden = false; scrollThread();
    var p = progress($('ahBusySteps'), ['사진을 확인하고 있어요', '작업본에 올리고 있어요', '미리보기에 반영하고 있어요'], 1500);
    var form = new FormData();
    form.append('slot', slot); form.append('revision', String(ctx.revision)); form.append('image', file);
    api('POST', '/media', form).then(function (r) {
      p.stop(); busyBox.hidden = true;
      if (r.revision != null) ctx.revision = r.revision;
      if (r.undoDepth != null) ctx.undoDepth = r.undoDepth;
      history.push({ role: 'USER', kind: 'TEXT', content: '사진 올리기' });
      history.push({ role: 'ASSISTANT', kind: 'EDIT', content: r.reply, payload: { changes: r.changes } });
      ctx.dirty = true;
      var first = (r.changes || []).filter(function (c) { return c.sectionId; })[0];
      showStrip(r.reply, false, r.changes);
      loadPreview(first ? first.sectionId : null);
      renderStatus();
    }).catch(function (e) {
      p.stop(); busyBox.hidden = true;
      if (e.status === 409 && e.code === 'REVISION_CONFLICT') {
        showStrip(e.message + ' 최신 작업본을 다시 불러왔어요. 사진을 다시 올려 주세요.', true, []);
        return refresh();
      }
      showStrip((e.message || '사진을 올리지 못했어요.') + ' 작업본은 그대로예요.', true, []);
    }).then(function () { ctx.busy = false; setDockBusy(false); });
  }

  function undo() {
    if (ctx.busy || ctx.undoDepth < 1) return;
    ctx.busy = true; setDockBusy(true);
    api('POST', '/undo', { revision: ctx.revision }).then(function (r) {
      if (r.revision != null) ctx.revision = r.revision;
      if (r.undoDepth != null) ctx.undoDepth = r.undoDepth;
      history.push({ role: 'USER', kind: 'TEXT', content: '되돌리기' });
      history.push({ role: 'ASSISTANT', kind: 'UNDO', content: r.reply, payload: { changes: r.changes } });
      if (r.failed) { showStrip(r.reply, true, []); return; }
      ctx.dirty = true; showStrip(r.reply, false, r.changes);
      var first = (r.changes || []).filter(function (c) { return c.sectionId; })[0];
      loadPreview(first ? first.sectionId : null); renderStatus();
    }).catch(function (e) {
      if (e.status === 409) { showStrip(e.message + ' 최신 작업본을 다시 불러왔어요.', true, []); return refresh(); }
      showStrip(e.message || '되돌리지 못했어요. 작업본은 그대로예요.', true, []);
    }).then(function () { ctx.busy = false; setDockBusy(false); });
  }

  /** 공통: 서버 편집 동작(다시 실행·저장·초기화)을 호출하고 결과를 대화와 미리보기에 반영한다. */
  function editAction(path, userText, failText) {
    if (ctx.busy || ctx.revision == null) return Promise.resolve();
    ctx.busy = true; setDockBusy(true);
    return api('POST', path, { revision: ctx.revision }).then(function (r) {
      if (r.revision != null) ctx.revision = r.revision;
      history.push({ role: 'USER', kind: 'TEXT', content: userText });
      history.push({ role: 'ASSISTANT', kind: 'EDIT', content: r.reply, payload: { changes: r.changes } });
      if (r.failed) { showStrip(r.reply, true, []); return; }
      var changed = r.changes && r.changes.length;
      if (changed) ctx.dirty = true;
      showStrip(r.reply, false, r.changes);
      if (changed) {
        var first = (r.changes || []).filter(function (c) { return c.sectionId; })[0];
        loadPreview(first ? first.sectionId : null);
      }
      renderStatus();
    }).catch(function (e) {
      if (e.status === 409) { showStrip(e.message + ' 최신 작업본을 다시 불러왔어요.', true, []); return refresh(); }
      showStrip(e.message || failText, true, []);
    }).then(function () { ctx.busy = false; setDockBusy(false); renderUndo(); });
  }
  function redo() { if (ctx.redoDepth >= 1) editAction('/redo', '다시 실행', '다시 실행하지 못했어요. 작업본은 그대로예요.'); }
  function saveWork() { editAction('/save', '저장', '저장하지 못했어요. 작업본은 그대로예요.'); }
  function openResetConfirm() {
    if (ctx.busy) return;
    $('ahResetScope').textContent = ctx.savedRevision != null
      ? '작업본의 문구·디자인·섹션·예약한 사진이 마지막 저장 상태로 돌아가요.'
      : '아직 저장한 적이 없어요. 공개 중인 홈페이지는 그 공개 내용으로 돌아가고, 공개 전 초안은 아무것도 바뀌지 않아요(먼저 저장해 두면 그 상태로 돌아갈 수 있어요). 새 홈페이지 만들기가 아니에요.';
    $('ahResetConfirm').hidden = false; $('ahResetCancel').focus();
  }
  function closeResetConfirm() { $('ahResetConfirm').hidden = true; }
  function doReset() {
    closeResetConfirm();
    editAction('/reset', '초기화', '초기화하지 못했어요. 작업본은 그대로예요.');
  }

  /* 새 홈페이지 만들기 — 수정 내용 초기화·되돌리기와 별개다. 열기만 해서는 서버 상태가 바뀌지 않는다. */
  var NEW_STEPS = ['요청을 분석하는 중', '어울리는 디자인을 고르는 중', '새 초안을 작업본에 만드는 중', '미리보기를 새로 그리는 중'];
  function openNew() {
    if (ctx.busy) return;
    $('ahNewPane').hidden = false; $('ahThread').hidden = true; document.querySelector('.ah-dock').hidden = true;
    $('ahNewInput').value = ''; $('ahNewInput').focus();
  }
  function closeNew() {
    $('ahNewPane').hidden = true; $('ahThread').hidden = false; document.querySelector('.ah-dock').hidden = false;
  }
  function submitNew(ev) {
    ev.preventDefault();
    var text = $('ahNewInput').value.trim();
    if (!text) { $('ahNewInput').focus(); return; }
    if (ctx.busy) return;
    ctx.busy = true; setDockBusy(true); $('ahNewOk').disabled = true; $('ahNewCancel').disabled = true;
    var box = $('ahNewBusy'); box.hidden = false;
    var p = progress($('ahNewSteps'), NEW_STEPS, 1800);
    api('POST', '/restart', { message: text, revision: ctx.revision }, { timeoutMs: AssistantWait.timeoutMs(window.__AH_TURN_TIMEOUT_MS__) }).then(function (r) {
      p.stop(); box.hidden = true;
      return refresh().then(function () { // 서버 기록(RESTART 표시 이후)이 정본이다
        closeNew();
        if (r.failed) showStrip(r.reply, true, []);
        renderStatus();
      });
    }).catch(function (e) {
      p.stop(); box.hidden = true;
      toast((e.message || '새 홈페이지를 만들지 못했어요.') + ' 기존 작업본은 그대로예요.');
    }).then(function () { ctx.busy = false; setDockBusy(false); $('ahNewOk').disabled = false; $('ahNewCancel').disabled = false; });
  }

  /** 서버 상태로 화면을 맞춘다(재진입·충돌 후). */
  function refresh() {
    return api('GET', '/state').then(function (s) {
      ctx.revision = s.revision; ctx.undoDepth = s.undoDepth || 0; ctx.status = s.status; ctx.dirty = !!s.dirty; ctx.publicUrl = s.publicUrl;
      history = (s.messages || []).map(function (m) {
        var payload = null; try { payload = m.payloadJson ? JSON.parse(m.payloadJson) : null; } catch (e) { /* 무시 */ }
        return { role: m.role, kind: m.kind, content: m.content, payload: payload };
      });
      renderStatus(); renderUndo(); renderThread(); loadPreview(null);
    });
  }

  /* 게시 — 최종 사용자 승인(확인 창) 뒤에만 공개된다 */
  var forcePublish = false;
  function addressText(url) {
    if (!url) return '';
    try { var u = new URL(url, location.origin); return u.host + (u.pathname === '/' ? '' : decodeURIComponent(u.pathname)); } catch (e) { return String(url); }
  }
  function openConfirm() {
    if (ctx.busy) return;
    if (!ctx.dirty && ctx.status === 'PUBLISHED') { toast('새로 등록할 변경이 없어요.'); return; }
    forcePublish = false;
    $('ahConfirmTitle').textContent = '홈페이지 정식 배포 등록';
    $('ahConfirmText').textContent = ctx.status === 'PUBLISHED'
      ? '지금 미리보기의 내용이 공개 홈페이지에 반영돼요. 등록 전 공개 내용은 버전 기록에 보관돼요.'
      : '등록하면 아래 주소로 누구나 홈페이지를 볼 수 있어요. 미리보기에서 내용을 확인하셨나요?';
    $('ahConfirmDetail').hidden = true; $('ahConfirmOk').textContent = '홈페이지 정식 배포 등록';
    var addr = $('ahConfirmAddr'); addr.hidden = true;
    $('ahConfirm').hidden = false; $('ahConfirmOk').focus();
    var show = function (url) { if (url) { ctx.publicUrl = url; $('ahConfirmAddrText').textContent = addressText(url); addr.hidden = false; } };
    if (ctx.publicUrl) show(ctx.publicUrl);
    else api('GET', '/state').then(function (s) { show(s.publicUrl); }).catch(function () { /* 주소 없이도 등록은 가능 */ });
  }
  function closeConfirm() { $('ahConfirm').hidden = true; }
  function overwriteText(d) {
    if (!d) return '';
    var lines = [];
    if (d.introChanged) lines.push('· 소개 문구: "' + (d.introBefore || '') + '" → "' + (d.introAfter || '') + '"');
    (d.builderDraftChanges || []).forEach(function (c) { lines.push('· ' + c.key + ': "' + (c.before || '') + '" → "' + (c.after || '') + '"'); });
    (d.changedSettings || []).forEach(function (k) { lines.push('· 설정 변경: ' + k); });
    return lines.join('\n');
  }
  /** 공개 직후 대화 안에 보여 주는 결과 카드: 주소 · 바로가기 · 주소 복사 · 계속 수정하기. */
  function addPublishedCard(url) {
    var abs; try { abs = new URL(url, location.origin).href; } catch (e) { abs = url; }
    var card = el('div', 'ah-pubcard');
    card.appendChild(el('h3', null, '홈페이지가 공개됐어요'));
    var field = el('div', 'ah-field'); field.appendChild(el('small', null, '공개된 주소')); field.appendChild(el('b', null, addressText(url)));
    card.appendChild(field);
    card.appendChild(el('p', 'ah-note', '공개된 뒤에도 대화로 계속 수정할 수 있어요. 수정한 내용은 다시 정식 배포 등록을 눌러야 공개 홈페이지에 반영돼요.'));
    var go = el('a', 'ah-primary ah-pill', '홈페이지 바로가기'); go.href = abs; go.target = '_blank'; go.rel = 'noopener';
    var copy = el('button', 'ah-secondary ah-pill', '주소 복사'); copy.type = 'button';
    copy.addEventListener('click', function () {
      var done = function () { toast('주소를 복사했어요.'); };
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(abs).then(done, function () { toast(abs); });
      else toast(abs);
    });
    var more = el('button', 'ah-link ah-center', '계속 수정하기'); more.type = 'button';
    more.addEventListener('click', function () { $('ahWorkInput').focus(); });
    card.appendChild(go); card.appendChild(copy); card.appendChild(more);
    $('ahMsgs').appendChild(card); scrollThread();
  }
  function doPublish() {
    if (ctx.busy || ctx.revision == null) return;
    ctx.busy = true; $('ahConfirmOk').disabled = true;
    api('POST', '/publish', { revision: ctx.revision, force: forcePublish }).then(function (r) {
      ctx.revision = r.revision; ctx.status = 'PUBLISHED'; ctx.dirty = false; ctx.undoDepth = 0; ctx.redoDepth = 0; ctx.savedRevision = null;
      closeConfirm(); if (!$('ahFull').hidden) closeFull();
      var msg = r.firstPublish ? '홈페이지가 공개됐어요.' : '수정한 내용을 공개 홈페이지에 반영했어요.';
      history.push({ role: 'ASSISTANT', kind: 'PUBLISHED', content: msg });
      toast(msg); showStrip(msg, false, []);
      return api('GET', '/state').then(function (s) {
        ctx.publicUrl = s.publicUrl;
        if (s.publicUrl) addPublishedCard(s.publicUrl);
      });
    }).catch(function (e) {
      if (e.status === 409 && e.code === 'LIVE_CHANGED') {
        forcePublish = true;
        $('ahConfirmTitle').textContent = '공개 내용이 먼저 바뀌었어요';
        $('ahConfirmText').textContent = e.message;
        var d = overwriteText(e.details); $('ahConfirmDetail').textContent = d; $('ahConfirmDetail').hidden = !d;
        $('ahConfirmOk').textContent = '그래도 배포 등록';
        return;
      }
      closeConfirm(); showStrip(e.message || '배포 등록하지 못했어요.', true, []);
    }).then(function () { ctx.busy = false; $('ahConfirmOk').disabled = false; renderStatus(); renderUndo(); });
  }

  /* 버전: 이전 버전을 비공개 작업본에 불러온다(공개본 불변) */
  function openVersions() {
    var ul = $('ahVersionList'); ul.textContent = '';
    ul.appendChild(el('li', 'ah-muted', '불러오는 중…'));
    $('ahVersions').hidden = false;
    api('GET', '/versions').then(function (list) {
      ul.textContent = '';
      if (!list || !list.length) { ul.appendChild(el('li', 'ah-muted', '아직 버전 기록이 없어요. 정식 배포 등록을 하면 그 전 공개본이 여기에 쌓여요.')); return; }
      list.forEach(function (v) {
        var li = el('li'); li.appendChild(el('span', null, AssistantExport.versionLabel(v)));
        var b = el('button', 'ah-secondary ah-small', '불러오기'); b.type = 'button';
        b.addEventListener('click', function () { restoreVersion(v.id); });
        li.appendChild(b); ul.appendChild(li);
      });
    }).catch(function (e) { ul.textContent = ''; ul.appendChild(el('li', 'ah-muted', e.message || '버전 기록을 불러오지 못했어요.')); });
  }
  function restoreVersion(versionId) {
    if (ctx.busy || ctx.revision == null) return;
    ctx.busy = true; setDockBusy(true);
    api('POST', '/restore', { revision: ctx.revision, versionId: versionId }).then(function (r) {
      ctx.revision = r.revision; ctx.undoDepth = 0; ctx.redoDepth = 0; ctx.savedRevision = null; ctx.dirty = true;
      history.push({ role: 'ASSISTANT', kind: 'RESTORED', content: '이전 버전을 작업본에 불러왔어요.' });
      $('ahVersions').hidden = true;
      showStrip('이전 버전을 작업본에 불러왔어요. 미리보기를 확인하고 마음에 들면 홈페이지 정식 배포 등록을 눌러 주세요.', false, []);
      loadPreview(null); renderStatus();
    }).catch(function (e) {
      $('ahVersions').hidden = true;
      if (e.status === 409) { showStrip(e.message + ' 최신 작업본을 다시 불러왔어요.', true, []); return refresh(); }
      showStrip((e.message || '버전을 불러오지 못했어요.') + ' 작업본은 그대로예요.', true, []);
    }).then(function () { ctx.busy = false; setDockBusy(false); });
  }

  /* 학원 정보: 한 문장 시작에서 임시로 채운 이름·지역·대상을 게시 전에 실제 값으로 바꾼다 */
  function getJson(url) {
    return fetch(url, { credentials: 'same-origin' }).then(function (r) { if (!r.ok) throw new Error('불러오지 못했어요.'); return r.json(); });
  }
  function openProfile() {
    var box = $('ahProfTargets'); box.textContent = '';
    AssistantExport.TARGETS.forEach(function (t) {
      var lb = el('label', 'ah-check'); var cb = document.createElement('input'); cb.type = 'checkbox'; cb.value = t.key;
      lb.appendChild(cb); lb.appendChild(document.createTextNode(' ' + t.label)); box.appendChild(lb);
    });
    $('ahProfile').hidden = false;
    getJson('/api/tutor/academy/working-copy/preview').then(function (a) {
      $('ahProfName').value = a.name || '';
      var r = (a.regions || [])[0];
      $('ahProfRegion').value = r ? (r.city + (r.district && r.district !== 'ALL' ? ' ' + r.district : '')) : '';
      var set = {}; (a.targetGroups || []).forEach(function (g) { set[g] = true; });
      Array.prototype.forEach.call(box.querySelectorAll('input'), function (c) { c.checked = !!set[c.value]; });
    }).catch(function () { /* 비워 둔 채로도 입력 가능 */ });
  }
  function saveProfile(ev) {
    ev.preventDefault();
    var targets = Array.prototype.filter.call($('ahProfTargets').querySelectorAll('input'), function (c) { return c.checked; })
      .map(function (c) { return c.value; });
    if (!targets.length) { toast('대상을 1개 이상 골라 주세요.'); return; }
    $('ahProfileSave').disabled = true;
    api('POST', '/profile', { academyName: $('ahProfName').value.trim(), region: $('ahProfRegion').value.trim(), targets: targets }).then(function () {
      $('ahProfile').hidden = true; toast('학원 정보를 바꿨어요.'); loadPreview(null);
    }).catch(function (e) { toast(e.message || '저장하지 못했어요.'); }).then(function () { $('ahProfileSave').disabled = false; });
  }

  /* 내려받기: 미리보기에 그려진 홈페이지를 HTML 파일 하나로 저장한다(스크립트 제거) */
  /** 공개 페이지는 로드 뒤 비동기로 그려지므로 글자가 채워질 때까지 잠시(최대 12초) 기다린다. */
  function waitRendered(frame, done) {
    var tries = 0;
    (function poll() {
      var d = null; try { d = frame.contentDocument; } catch (e) { /* 아래에서 처리 */ }
      var ready = d && d.body && d.body.innerText && d.body.innerText.trim().length > 80;
      if (ready || ++tries > 60) done(ready ? d : null); else setTimeout(poll, 200);
    })();
  }
  function downloadHtml() {
    // 카드 미리보기는 변경 때마다 새로 그려지므로 항상 최신 작업본이다(전체 화면 프레임은 열 때만 갱신된다).
    toast('파일을 만드는 중이에요…');
    waitRendered($('ahCardFrame'), function (doc) {
      if (!doc) { toast('미리보기가 아직 준비되지 않았어요. 잠시 후 다시 눌러 주세요.'); return; }
      buildAndSave(doc);
    });
  }
  function buildAndSave(doc) {
    var f = $('ahCardFrame');
    var clone = doc.documentElement.cloneNode(true);
    var links = Array.prototype.slice.call(clone.querySelectorAll('link[rel~="stylesheet"][href]'));
    var imgs = Array.prototype.slice.call(clone.querySelectorAll('img[src]'));
    var inlineCss = function (l) {
      var href = l.href;
      return fetch(href, { credentials: 'same-origin' }).then(function (r) { if (!r.ok) throw new Error('css'); return r.text(); }).then(function (css) {
        var st = doc.createElement('style'); st.textContent = AssistantExport.absolutizeCssUrls(css, href); l.parentNode.replaceChild(st, l);
      }).catch(function () { l.setAttribute('href', href); /* 못 합치면 절대 주소로 남긴다 */ });
    };
    var inlineImg = function (img) {
      var abs; try { abs = new URL(img.getAttribute('src'), f.contentWindow.location.href); } catch (e) { return Promise.resolve(); }
      if (abs.protocol === 'data:') return Promise.resolve();
      if (abs.origin !== location.origin) { img.setAttribute('src', abs.href); return Promise.resolve(); }
      return fetch(abs.href, { credentials: 'same-origin' }).then(function (r) { if (!r.ok) throw new Error('img'); return r.blob(); }).then(function (b) {
        return new Promise(function (res) { var fr = new FileReader(); fr.onload = function () { img.setAttribute('src', fr.result); res(); }; fr.onerror = res; fr.readAsDataURL(b); });
      }).catch(function () { img.setAttribute('src', abs.href); });
    };
    var academyName = doc.title;
    getJson('/api/tutor/academy/working-copy/preview').then(function (a) { if (a && a.name) academyName = a.name; }, function () { /* 제목으로 대신 */ })
      .then(function () { return Promise.all(links.map(inlineCss)); }).then(function () { return Promise.all(imgs.map(inlineImg)); }).then(function () {
      var html = AssistantExport.finalizeHtml('<!DOCTYPE html>\n' + clone.outerHTML, location.origin + '/');
      var a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }));
      a.download = AssistantExport.fileName(academyName, new Date());
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 2000);
      toast('HTML 파일을 내려받았어요.');
    }).catch(function () { toast('파일을 만들지 못했어요.'); });
  }

  /* 자료 첨부(시작 화면): 텍스트 파일(.txt/.md)의 내용을 설명 입력에 붙인다. PDF·Word는 아직 지원하지 않는다. */
  function attachText(file) {
    if (!file) return;
    if (!/\.(txt|md)$/i.test(file.name) && file.type !== 'text/plain') { toast('텍스트 파일(.txt, .md)만 붙일 수 있어요. PDF·Word는 준비 중이에요.'); return; }
    if (file.size > 200000) { toast('파일이 너무 커요. 200KB 이하 텍스트 파일을 올려 주세요.'); return; }
    var fr = new FileReader();
    fr.onload = function () {
      var ta = $('ahPrompt'); var max = ta.maxLength > 0 ? ta.maxLength : 2000;
      var cur = ta.value ? ta.value + '\n' : '';
      var text = String(fr.result || '').trim();
      var room = Math.max(0, max - cur.length);
      ta.value = cur + text.slice(0, room);
      toast(text.length > room ? '앞부분 ' + room + '자만 붙였어요.' : '자료를 붙였어요.');
      ta.focus();
    };
    fr.readAsText(file);
  }

  function init() {
    var pending = AssistantExport.takePendingPrompt(sessionStorage, Date.now());
    if (pending) $('ahPrompt').value = pending.slice(0, $('ahPrompt').maxLength > 0 ? $('ahPrompt').maxLength : 2000);
    $('ahStartForm').addEventListener('submit', submitStart);
    $('ahPrompt').addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); submitStart(e); }
    });
    $('ahVersionsBtn').addEventListener('click', openVersions);
    $('ahVersionsClose').addEventListener('click', function () { $('ahVersions').hidden = true; });
    $('ahProfileBtn').addEventListener('click', openProfile);
    $('ahProfileCancel').addEventListener('click', function () { $('ahProfile').hidden = true; });
    $('ahProfileForm').addEventListener('submit', saveProfile);
    $('ahDownloadBtn').addEventListener('click', downloadHtml);
    $('ahComposer').addEventListener('submit', submitChat);
    $('ahWorkComposer').addEventListener('submit', function (ev) {
      ev.preventDefault();
      var text = $('ahWorkInput').value.trim();
      if (!text || ctx.busy) return;
      if (AssistantExport.isPublishIntent(text)) { // 공개 요청은 AI에 보내지 않고 정식 배포 등록 확인 창으로 안내한다(등록은 사용자가 직접 누른다)
        $('ahWorkInput').value = '';
        addBubble('user', text); addBubble('bot', '공개 전에 한 번 더 확인해 주세요. 아래 버튼을 누르면 정식으로 등록돼요.');
        openConfirm(); return;
      }
      sendEdit(text);
    });
    $('ahPhotoBtn').addEventListener('click', function () { var m = $('ahPhotoMenu'); m.hidden = !m.hidden; });
    Array.prototype.forEach.call($('ahPhotoMenu').querySelectorAll('[data-slot]'), function (b) {
      b.addEventListener('click', function () {
        ctx.photoSlot = b.getAttribute('data-slot'); $('ahPhotoMenu').hidden = true;
        $('ahPhotoFile').value = ''; $('ahPhotoFile').click();
      });
    });
    $('ahPhotoFile').addEventListener('change', function () {
      var f = this.files && this.files[0]; if (f && ctx.photoSlot) uploadPhoto(ctx.photoSlot, f);
    });
    $('ahUndo').addEventListener('click', undo);
    $('ahRedo').addEventListener('click', redo);
    $('ahSave').addEventListener('click', saveWork);
    $('ahReset').addEventListener('click', openResetConfirm);
    $('ahNew').addEventListener('click', openNew);
    $('ahNewCancel').addEventListener('click', closeNew);
    $('ahNewForm').addEventListener('submit', submitNew);
    $('ahResetCancel').addEventListener('click', closeResetConfirm);
    $('ahResetOk').addEventListener('click', doReset);
    $('ahPreviewBtn').addEventListener('click', function () { openFull(null); });
    $('ahCardFrame').addEventListener('load', freezeCardMotion);
    $('ahMoreBtn').addEventListener('click', function (e) { e.stopPropagation(); setMenu($('ahMenu').hidden); });
    $('ahMenu').addEventListener('click', function () { setMenu(false); });
    document.addEventListener('click', function (e) { if (!$('ahMenu').hidden && !$('ahMenu').contains(e.target)) setMenu(false); });
    $('ahPublish').addEventListener('click', openConfirm);
    $('ahConfirmCancel').addEventListener('click', closeConfirm);
    $('ahConfirmOk').addEventListener('click', doPublish);
    $('ahCardFull').addEventListener('click', function () { openFull(null); });
    $('ahThumb').addEventListener('click', function () { openFull(null); });
    $('ahThumb').addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openFull(null); } });
    $('ahFullBack').addEventListener('click', closeFull);
    $('ahFullPublish').addEventListener('click', openConfirm);
    $('ahAttachBtn').addEventListener('click', function () { $('ahAttachFile').value = ''; $('ahAttachFile').click(); });
    $('ahAttachFile').addEventListener('change', function () { attachText(this.files && this.files[0]); });
    Array.prototype.forEach.call(document.querySelectorAll('.ah-seg [data-dev]'), function (b) {
      b.addEventListener('click', function () { setDevice(b.dataset.dev); });
    });
    window.addEventListener('resize', function () { fit(); fitCard(); });
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      if (!$('ahMenu').hidden) setMenu(false);
      else if (!$('ahResetConfirm').hidden) closeResetConfirm();
      else if (!$('ahNewPane').hidden && !ctx.busy) closeNew();
      else if (!$('ahConfirm').hidden) closeConfirm();
      else if (!$('ahVersions').hidden) $('ahVersions').hidden = true;
      else if (!$('ahProfile').hidden) $('ahProfile').hidden = true;
      else if (!$('ahFull').hidden) closeFull();
    });
    api('GET', '/state').then(function (s) {
      if (!s.hasAcademy) return;
      ctx.created = true; ctx.revision = s.revision; ctx.status = s.status; ctx.publicUrl = s.publicUrl; ctx.dirty = !!s.dirty; ctx.undoDepth = s.undoDepth || 0;
      history = (s.messages || []).map(function (m) {
        var payload = null; try { payload = m.payloadJson ? JSON.parse(m.payloadJson) : null; } catch (e) { /* 무시 */ }
        return { role: m.role, kind: m.kind, content: m.content, payload: payload };
      });
      enterWork(null);
      if (pending) { $('ahWorkInput').value = pending.slice(0, 1000); toast('입력하신 요청을 입력창에 가져왔어요. 보내기를 누르면 수정해요.'); }
    }).catch(function () { /* 비로그인 등: 시작 화면 유지 */ });
  }
  init();
})();
