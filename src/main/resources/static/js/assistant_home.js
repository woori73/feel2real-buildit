/**
 * BuildIt 대화형 홈페이지 화면 (2-B).
 * API: /api/tutor/academy/assistant/{samples,state,turn,publish} (docs/API.md)
 * 미리보기: /tutor/academy/preview (비공개 작업본). 서버가 준 문자열은 textContent로만 넣는다(XSS 방지).
 */
(function () {
  'use strict';
  var API = '/api/tutor/academy/assistant';
  var PREVIEW = '/tutor/academy/preview';
  var PRESET_COLORS = {
    EXAM: ['#0c238a', '#4a7fd4'], ENGLISH: ['#0f766e', '#f59e0b'], DYNAMIC: ['#e11d48', '#f97316'],
    STUDY: ['#2563eb', '#16a34a'], DARK: ['#111827', '#d4af37'], MINIMAL: ['#ffffff', '#111827']
  };
  var PRESET_NAMES = {
    EXAM: '입시학원형', ENGLISH: '영어회화형', ARTS: '예체능형', STUDY: '공부방형', ONLINE: '온라인학원형',
    MINIMAL: '미니멀형', DARK: '다크형', WARM: '감성형', PHOTO: '포토형', DYNAMIC: '다이나믹형'
  };
  var QUESTIONS = {
    academyName: '학원 이름을 알려 주세요.',
    description: '어떤 학원인지 한두 문장으로 알려 주세요. (예: 중학생 영어 내신 전문)',
    region: '학원이 있는 지역을 알려 주세요. 주소 전체가 아니라 \'서울 강남구\'처럼 지역만 있으면 돼요.',
    subject: '어떤 과목을 가르치나요? (예: 영어, 수학)',
    target: '어떤 학생을 가르치나요? (예: 초등학생, 중학생, 고등학생)'
  };
  var FIELD_OF_NEED = {
    academyName: 'name', description: 'description', region: 'region', subject: 'subject', target: 'target'
  };
  var PROGRESS = ['학원 정보 정리', '소개 문구 작성', '디자인 적용', '미리보기 준비'];

  var $ = function (id) { return document.getElementById(id); };
  var ctx = { name: '', description: '', region: '', subject: '', target: '', sampleKey: null,
    pendingNeed: null, created: false, revision: null, status: null, publicUrl: null, busy: false };
  var samples = [];

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function api(method, path, body) {
    return fetch(API + path, {
      method: method, credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) { var err = new Error(j.message || '요청을 처리하지 못했어요.'); err.status = r.status; err.code = j.code; throw err; }
        return j;
      });
    });
  }

  /* ───── 화면 전환 ───── */
  function showChat() { $('ahStart').hidden = true; $('ahChat').hidden = false; }
  function scrollDown() { var m = $('ahMessages'); m.scrollTop = m.scrollHeight; }
  function bubble(role, text) { var b = el('div', 'ah-msg ' + role, text); $('ahMessages').appendChild(b); scrollDown(); return b; }

  /* ───── 샘플 ───── */
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

  /* ───── 생성 턴 ───── */
  function progressBubble() {
    var b = el('div', 'ah-msg bot'); var ul = el('ul', 'ah-steps');
    var items = PROGRESS.map(function (t) { var li = el('li', null, t); ul.appendChild(li); return li; });
    b.appendChild(ul); $('ahMessages').appendChild(b); scrollDown();
    var i = 0; items[0].className = 'now';
    var timer = setInterval(function () {
      if (i < items.length - 1) { items[i].className = 'done'; i++; items[i].className = 'now'; }
    }, 2200);
    return { stop: function () { clearInterval(timer); if (b.parentNode) b.parentNode.removeChild(b); } };
  }

  function sendTurn() {
    if (ctx.busy) return;
    ctx.busy = true; setBusy(true);
    var p = progressBubble();
    api('POST', '/turn', {
      academyName: ctx.name, sampleKey: ctx.sampleKey, message: ctx.description,
      region: ctx.region, subject: ctx.subject, target: ctx.target
    }).then(function (r) {
      p.stop();
      if (r.created) {
        ctx.created = true; ctx.revision = r.revision; ctx.status = 'DRAFT'; ctx.pendingNeed = null;
        bubble('bot', r.reply);
        resultCard(r.designPreset, r.notes || []);
      } else if (r.needs && r.needs.length) {
        ctx.pendingNeed = r.needs[0]; bubble('bot', QUESTIONS[ctx.pendingNeed] || r.reply);
      } else {
        ctx.pendingNeed = null; bubble('bot', r.reply);
      }
    }).catch(function (e) {
      p.stop(); bubble('bot', e.message);
    }).then(function () { ctx.busy = false; setBusy(false); });
  }
  function setBusy(b) { $('ahSend').disabled = b; $('ahStartBtn').disabled = b; }

  /* ───── 결과 카드 ───── */
  function resultCard(preset, notes, published) {
    var card = el('div', 'ah-result');
    var thumb = el('div', 'ah-thumb'); var inner = el('div', 'inner'); var f = el('iframe');
    f.src = PREVIEW + '?t=' + Date.now(); f.setAttribute('tabindex', '-1'); f.title = '미리보기 축소판';
    inner.appendChild(f); thumb.appendChild(inner); card.appendChild(thumb);
    var body = el('div', 'ah-resbody');
    body.appendChild(el('h3', null, ctx.name || '내 학원 홈페이지'));
    body.appendChild(el('span', 'ah-badge', (PRESET_NAMES[preset] || '디자인') + ' · ' + (published ? '게시됨' : '비공개 초안')));
    if (notes.length) { var ul = el('ul', 'ah-notes'); notes.forEach(function (n) { ul.appendChild(el('li', null, n)); }); body.appendChild(ul); }
    card.appendChild(body);
    var act = el('div', 'ah-actions');
    var full = el('button', 'ah-secondary', '전체 화면으로 보기'); full.type = 'button'; full.addEventListener('click', openFull);
    var pub = el('button', 'ah-primary', '게시하기'); pub.type = 'button'; pub.addEventListener('click', publish);
    act.appendChild(full); act.appendChild(pub); card.appendChild(act);
    $('ahMessages').appendChild(card); scrollDown();
  }

  /* ───── 전체 화면 ───── */
  function openFull() {
    $('ahFrame').src = PREVIEW + '?t=' + Date.now();
    $('ahFull').hidden = false; document.body.style.overflow = 'hidden'; $('ahBack').focus();
  }
  function closeFull() { $('ahFull').hidden = true; document.body.style.overflow = ''; }
  function setDevice(mobile) {
    $('ahFrame').className = mobile ? 'mobile' : 'desktop';
    $('ahDevMobile').classList.toggle('on', mobile); $('ahDevMobile').setAttribute('aria-pressed', String(mobile));
    $('ahDevDesktop').classList.toggle('on', !mobile); $('ahDevDesktop').setAttribute('aria-pressed', String(!mobile));
  }
  function toast(msg) {
    var t = $('ahToast'); t.textContent = msg; t.hidden = false;
    clearTimeout(toast.h); toast.h = setTimeout(function () { t.hidden = true; }, 3500);
  }

  /* ───── 게시하기 (최초/수정 모두 같은 버튼) ───── */
  function publish(force) {
    if (ctx.busy || ctx.revision == null) return;
    ctx.busy = true;
    api('POST', '/publish', { revision: ctx.revision, force: force === true }).then(function (r) {
      ctx.revision = r.revision; ctx.status = 'PUBLISHED';
      var msg = r.firstPublish ? '게시했어요! 이제 누구나 홈페이지를 볼 수 있어요.' : '수정한 내용을 게시했어요.';
      toast(msg); bubble('bot', msg);
      return api('GET', '/state').then(function (s) {
        ctx.publicUrl = s.publicUrl;
        if (s.publicUrl) { var a = el('a', 'ah-msg bot', s.publicUrl); a.href = s.publicUrl; a.target = '_blank'; a.rel = 'noopener'; $('ahMessages').appendChild(a); scrollDown(); }
      });
    }).catch(function (e) {
      if (e.status === 409 && e.code === 'LIVE_CHANGED' && window.confirm(e.message + '\n그래도 지금 내용으로 게시할까요?')) {
        ctx.busy = false; return publish(true);
      }
      toast(e.message); bubble('bot', e.message);
    }).then(function () { ctx.busy = false; });
  }

  /* ───── 입력 처리 ───── */
  function submitStart(ev) {
    ev.preventDefault();
    ctx.name = $('ahName').value.trim(); ctx.description = $('ahDesc').value.trim();
    if (!ctx.name) { $('ahName').focus(); return; }
    showChat();
    var label = '학원 이름: ' + ctx.name + (ctx.description ? '\n' + ctx.description : '');
    var sample = samples.filter(function (s) { return s.key === ctx.sampleKey; })[0];
    if (sample) label += '\n샘플: ' + sample.title;
    bubble('user', label);
    sendTurn();
  }
  function submitChat(ev) {
    ev.preventDefault();
    var text = $('ahInput').value.trim(); if (!text || ctx.busy) return;
    $('ahInput').value = ''; bubble('user', text);
    if (ctx.pendingNeed) { ctx[FIELD_OF_NEED[ctx.pendingNeed]] = text; sendTurn(); return; }
    if (ctx.created) {
      ctx.busy = true;
      api('POST', '/turn', { message: text }).then(function (r) { bubble('bot', r.reply); })
        .catch(function (e) { bubble('bot', e.message); }).then(function () { ctx.busy = false; });
    }
  }

  /* ───── 복원 ───── */
  function restore(s) {
    showChat(); ctx.created = true; ctx.revision = s.revision; ctx.status = s.status; ctx.publicUrl = s.publicUrl;
    var lastResult = -1;
    (s.messages || []).forEach(function (m, i) { if (m.kind === 'RESULT') lastResult = i; });
    (s.messages || []).forEach(function (m, i) {
      if (m.kind === 'RESULT' && i === lastResult) {
        bubble('bot', m.content);
        var payload = {}; try { payload = JSON.parse(m.payloadJson || '{}'); } catch (e) { /* 무시 */ }
        resultCard(payload.designPreset, payload.notes || [], s.status === 'PUBLISHED' && !s.dirty);
      } else {
        bubble(m.role === 'USER' ? 'user' : 'bot', m.content);
      }
    });
  }

  function init() {
    $('ahStartForm').addEventListener('submit', submitStart);
    $('ahComposer').addEventListener('submit', submitChat);
    $('ahChosenClear').addEventListener('click', clearSample);
    $('ahBack').addEventListener('click', closeFull);
    $('ahDevMobile').addEventListener('click', function () { setDevice(true); });
    $('ahDevDesktop').addEventListener('click', function () { setDevice(false); });
    $('ahFullPublish').addEventListener('click', function () { publish(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !$('ahFull').hidden) closeFull(); });
    api('GET', '/samples').then(function (list) { samples = list; renderSamples(); }).catch(function () { /* 샘플 없이도 시작 가능 */ });
    api('GET', '/state').then(function (s) { if (s.hasAcademy) restore(s); }).catch(function () { /* 비로그인 등: 시작 화면 유지 */ });
  }
  init();
})();
