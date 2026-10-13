/**
 * BuildIt 대화형 홈페이지 화면 (2-A 생성 · 2-B 미리보기 · 2-C 대화 수정/되돌리기).
 * API: /api/tutor/academy/assistant/{samples,state,turn,undo,publish} (docs/API.md)
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
  var PRESET_COLORS = {
    EXAM: ['#0c238a', '#4a7fd4'], ENGLISH: ['#0f766e', '#f59e0b'], DYNAMIC: ['#e11d48', '#f97316'],
    STUDY: ['#2563eb', '#16a34a'], DARK: ['#111827', '#d4af37'], MINIMAL: ['#ffffff', '#111827']
  };
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
  var CHIPS = ['더 고급스럽게 바꿔 줘', '밝고 친근한 분위기로', '깔끔하게 정리해 줘', '주색을 파란색으로', '수업 영상 섹션 숨겨 줘', '상담 신청을 맨 위로'];

  var $ = function (id) { return document.getElementById(id); };
  var ctx = {
    name: '', description: '', region: '', subject: '', target: '', sampleKey: null, pendingNeed: null,
    created: false, revision: null, status: null, publicUrl: null, busy: false, undoDepth: 0, dirty: false, dev: 'mobile'
  };
  var samples = [];
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

  function renderSamples() {
    var box = $('ahSamples'); box.textContent = '';
    samples.forEach(function (s) {
      var btn = el('button', 'ah-sample'); btn.type = 'button'; btn.setAttribute('aria-pressed', 'false'); btn.dataset.key = s.key;
      var sw = el('div', 'ah-swatch'); var cols = PRESET_COLORS[s.preset] || ['#999', '#ccc'];
      sw.style.background = cols[0] === '#ffffff' ? '#f3f4f6' : cols[0];
      cols.forEach(function (c) { var i = el('i'); i.style.background = c; sw.appendChild(i); });
      btn.appendChild(sw); btn.appendChild(el('b', null, s.title)); btn.appendChild(el('span', null, s.description));
      btn.addEventListener('click', function () { chooseSample(s); });
      box.appendChild(btn);
    });
  }
  function chooseSample(s) {
    ctx.sampleKey = s.key;
    Array.prototype.forEach.call($('ahSamples').children, function (b) { b.setAttribute('aria-pressed', String(b.dataset.key === s.key)); });
    $('ahChosen').hidden = false; $('ahChosenName').textContent = s.title;
    if (!$('ahName').value.trim()) $('ahName').focus();
  }
  function clearSample() {
    ctx.sampleKey = null; $('ahChosen').hidden = true;
    Array.prototype.forEach.call($('ahSamples').children, function (b) { b.setAttribute('aria-pressed', 'false'); });
  }

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
      academyName: ctx.name, sampleKey: ctx.sampleKey, message: ctx.description,
      region: ctx.region, subject: ctx.subject, target: ctx.target
    }).then(function (r) {
      p.stop(); if (b.parentNode) b.parentNode.removeChild(b);
      if (r.created) {
        ctx.created = true; ctx.revision = r.revision; ctx.status = 'DRAFT'; ctx.pendingNeed = null; ctx.dirty = true; ctx.undoDepth = 0;
        history = [{ role: 'USER', kind: 'TEXT', content: '학원 이름: ' + ctx.name + (ctx.description ? '\n' + ctx.description : '') },
          { role: 'ASSISTANT', kind: 'RESULT', content: r.reply }];
        enterWork({ reply: r.reply, notes: r.notes || [] });
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
    ctx.name = $('ahName').value.trim(); ctx.description = $('ahDesc').value.trim();
    if (!ctx.name) { $('ahName').focus(); return; }
    showChat();
    var label = '학원 이름: ' + ctx.name + (ctx.description ? '\n' + ctx.description : '');
    var sample = samples.filter(function (s) { return s.key === ctx.sampleKey; })[0];
    if (sample) label += '\n샘플: ' + sample.title;
    bubble('user', label);
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
    loadPreview(null);
    if (opts && opts.reply) {
      var extra = (opts.notes || []).join('\n');
      showStrip(opts.reply + (extra ? '\n' + extra : ''), false, []);
    }
  }

  function renderStatus() {
    var s = $('ahStatus'); s.textContent = '';
    s.appendChild(el('b', null, ctx.status === 'PUBLISHED' ? '게시됨' : '비공개 초안'));
    if (ctx.dirty) s.appendChild(document.createTextNode(' · 수정 있음'));
  }
  function renderUndo() {
    $('ahUndoCount').textContent = String(ctx.undoDepth);
    $('ahUndo').disabled = ctx.busy || ctx.undoDepth < 1;
    $('ahStripUndo').hidden = ctx.undoDepth < 1;
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
    $('ahWorkSend').disabled = b; $('ahWorkInput').disabled = b; $('ahPhotoBtn').disabled = b; $('ahPublish').disabled = b;
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
        go.addEventListener('click', function () { scrollPreviewTo(c.sectionId); });
        li.appendChild(go);
      }
      ul.appendChild(li);
    });
  }
  function showStrip(text, isError, changes) {
    var strip = $('ahStrip'); strip.hidden = false; strip.classList.toggle('err', !!isError);
    $('ahStripText').textContent = text;
    renderChanges($('ahChanges'), changes);
    renderUndo();
  }

  /* 미리보기 — 같은 비공개 작업본 페이지를 기기 폭으로 맞춰 보여 준다 */
  function fit() {
    var stage = $('ahStage'); if (!stage || $('ahWork').hidden) return;
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
  var previewTimer = null;
  function loadPreview(sectionId) {
    var f = $('ahFrame'); f.src = PREVIEW + '?t=' + Date.now();
    if (sectionId) { f.addEventListener('load', function once() { f.removeEventListener('load', once); scrollPreviewTo(sectionId); }); }
  }
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
    var busyBox = $('ahBusy'); busyBox.hidden = false;
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
      stopWait(); history.pop();
      if (e.timeout) {
        $('ahWorkInput').value = text; // 입력 보존: 다시 보낼 수 있다
        // 서버가 늦게라도 끝냈을 수 있으니 서버 작업본을 정본으로 다시 읽는다.
        return refresh().then(function () {
          if (ctx.revision !== revisionBefore) showStrip(AssistantWait.lateAppliedMessage(), false, []);
          else showStrip(AssistantWait.timeoutMessage(), true, []);
        }, function () { showStrip(AssistantWait.timeoutMessage(), true, []); });
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
    var busyBox = $('ahBusy'); busyBox.hidden = false;
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

  /** 서버 상태로 화면을 맞춘다(재진입·충돌 후). */
  function refresh() {
    return api('GET', '/state').then(function (s) {
      ctx.revision = s.revision; ctx.undoDepth = s.undoDepth || 0; ctx.status = s.status; ctx.dirty = !!s.dirty; ctx.publicUrl = s.publicUrl;
      history = (s.messages || []).map(function (m) {
        var payload = null; try { payload = m.payloadJson ? JSON.parse(m.payloadJson) : null; } catch (e) { /* 무시 */ }
        return { role: m.role, kind: m.kind, content: m.content, payload: payload };
      });
      renderStatus(); renderUndo(); loadPreview(null);
    });
  }

  /* 게시 — 최종 사용자 승인(확인 창) 뒤에만 공개된다 */
  var forcePublish = false;
  function openConfirm() {
    if (ctx.busy) return;
    if (!ctx.dirty && ctx.status === 'PUBLISHED') { toast('게시할 변경이 없어요.'); return; }
    forcePublish = false;
    $('ahConfirmTitle').textContent = ctx.status === 'PUBLISHED' ? '수정 내용을 게시할까요?' : '홈페이지를 처음 게시할까요?';
    $('ahConfirmText').textContent = ctx.status === 'PUBLISHED'
      ? '지금 미리보기의 내용이 공개 홈페이지에 반영돼요. 게시 전 공개 내용은 버전 기록에 보관돼요.'
      : '게시하면 누구나 홈페이지를 볼 수 있어요. 미리보기에서 내용을 확인하셨나요?';
    $('ahConfirmDetail').hidden = true; $('ahConfirmOk').textContent = '게시하기';
    $('ahConfirm').hidden = false; $('ahConfirmOk').focus();
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
  function doPublish() {
    if (ctx.busy || ctx.revision == null) return;
    ctx.busy = true; $('ahConfirmOk').disabled = true;
    api('POST', '/publish', { revision: ctx.revision, force: forcePublish }).then(function (r) {
      ctx.revision = r.revision; ctx.status = 'PUBLISHED'; ctx.dirty = false; ctx.undoDepth = 0;
      closeConfirm();
      var msg = r.firstPublish ? '게시했어요! 이제 누구나 홈페이지를 볼 수 있어요.' : '수정한 내용을 게시했어요.';
      history.push({ role: 'ASSISTANT', kind: 'PUBLISHED', content: msg });
      toast(msg); showStrip(msg, false, []);
      return api('GET', '/state').then(function (s) {
        ctx.publicUrl = s.publicUrl;
        if (s.publicUrl) {
          var a = el('a', 'ah-link', s.publicUrl); a.href = s.publicUrl; a.target = '_blank'; a.rel = 'noopener';
          $('ahStripText').appendChild(document.createElement('br')); $('ahStripText').appendChild(a);
        }
      });
    }).catch(function (e) {
      if (e.status === 409 && e.code === 'LIVE_CHANGED') {
        forcePublish = true;
        $('ahConfirmTitle').textContent = '공개 내용이 먼저 바뀌었어요';
        $('ahConfirmText').textContent = e.message;
        var d = overwriteText(e.details); $('ahConfirmDetail').textContent = d; $('ahConfirmDetail').hidden = !d;
        $('ahConfirmOk').textContent = '그래도 게시';
        return;
      }
      closeConfirm(); showStrip(e.message || '게시하지 못했어요.', true, []);
    }).then(function () { ctx.busy = false; $('ahConfirmOk').disabled = false; renderStatus(); renderUndo(); });
  }

  /* 대화 기록 시트 */
  function openHistory() {
    var list = $('ahHistoryList'); list.textContent = '';
    history.forEach(function (m) {
      var mine = m.role === 'USER';
      var b = el('div', 'ah-msg ' + (mine ? 'user' : 'bot') + (m.kind === 'EDIT' || m.kind === 'UNDO' ? ' edit' : ''), m.content);
      if (!mine && m.payload && m.payload.changes && m.payload.changes.length) {
        var ul = el('ul', 'ah-changes'); renderChanges(ul, m.payload.changes); b.appendChild(ul);
      }
      list.appendChild(b);
    });
    $('ahHistory').hidden = false; list.scrollTop = list.scrollHeight;
  }

  function init() {
    $('ahStartForm').addEventListener('submit', submitStart);
    $('ahComposer').addEventListener('submit', submitChat);
    $('ahChosenClear').addEventListener('click', clearSample);
    $('ahWorkComposer').addEventListener('submit', function (ev) { ev.preventDefault(); sendEdit($('ahWorkInput').value); });
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
    $('ahStripUndo').addEventListener('click', undo);
    $('ahPublish').addEventListener('click', openConfirm);
    $('ahConfirmCancel').addEventListener('click', closeConfirm);
    $('ahConfirmOk').addEventListener('click', doPublish);
    $('ahShowHistory').addEventListener('click', openHistory);
    $('ahHistoryClose').addEventListener('click', function () { $('ahHistory').hidden = true; });
    Array.prototype.forEach.call(document.querySelectorAll('.ah-seg [data-dev]'), function (b) {
      b.addEventListener('click', function () { setDevice(b.dataset.dev); });
    });
    window.addEventListener('resize', fit);
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      if (!$('ahConfirm').hidden) closeConfirm(); else if (!$('ahHistory').hidden) $('ahHistory').hidden = true;
    });
    api('GET', '/samples').then(function (list) { samples = list; renderSamples(); }).catch(function () { /* 샘플 없이도 시작 가능 */ });
    api('GET', '/state').then(function (s) {
      if (!s.hasAcademy) return;
      ctx.created = true; ctx.revision = s.revision; ctx.status = s.status; ctx.publicUrl = s.publicUrl; ctx.dirty = !!s.dirty; ctx.undoDepth = s.undoDepth || 0;
      history = (s.messages || []).map(function (m) {
        var payload = null; try { payload = m.payloadJson ? JSON.parse(m.payloadJson) : null; } catch (e) { /* 무시 */ }
        return { role: m.role, kind: m.kind, content: m.content, payload: payload };
      });
      enterWork(null);
      var last = history.filter(function (m) { return m.role !== 'USER'; }).pop();
      if (last) showStrip(last.content, false, last.payload && last.payload.changes ? last.payload.changes : []);
    }).catch(function () { /* 비로그인 등: 시작 화면 유지 */ });
  }
  init();
})();
