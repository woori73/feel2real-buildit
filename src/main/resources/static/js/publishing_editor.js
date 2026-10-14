/* publishing_editor.js — 편집기 화면 컨트롤러 (Qoder, F1~F8)
 * PublishingApiONLY 사용. DOM/상태만 담당, 백엔드·CLI 지시 없음. */
(function (global) {
  'use strict';

  var api = global.PublishingApi;

  var S = {
    consts: null,
    jobId: null,
    project: null,
    page: 1,
    pageCount: 0,
    scale: 1,
    spread: false,
    thumbCache: {},
    pageCache: {},
    preflight: null,
    jobTimer: null,
    exportTimer: null,
    barMode: null,   // 'job' | 'export' | null
    activeBarJob: null
  };

  var ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2];
  var PAGE_BASE_W = 520;

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined) n.textContent = text;
    return n;
  }
  function fmtTime(ts) {
    var d = new Date(ts);
    function p(x) { return (x < 10 ? '0' : '') + x; }
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
  }
  var toastTimer = null;
  function toast(msg, isError) {
    var t = $('toast');
    t.textContent = msg;
    t.className = 'toast' + (isError ? ' error' : '');
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 3200);
  }

  /* ═══════════ 뷰 전환 ═══════════ */
  function showProjects() {
    stopTimers();
    document.body.classList.remove('view-editor', 'has-jobbar');
    document.body.classList.add('view-projects');
    $('view-projects').hidden = false;
    $('view-editor').hidden = true;
    renderProjects();
  }
  function showEditor(jobId) {
    S.jobId = jobId;
    S.page = 1; S.spread = false; S.thumbCache = {}; S.pageCache = {};
    S.preflight = null; S.scale = 1;
    document.body.classList.remove('view-projects');
    document.body.classList.add('view-editor');
    $('view-projects').hidden = true;
    $('view-editor').hidden = false;
    $('chk-spread').checked = false;
    initEditor();
  }
  function stopTimers() {
    clearInterval(S.jobTimer); clearInterval(S.exportTimer);
    S.jobTimer = S.exportTimer = null; S.barMode = null;
  }

  /* ═══════════ F1: 프로젝트 목록·생성 ═══════════ */
  function templateOf(id) {
    var list = S.consts.TEMPLATES;
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return list[0];
  }

  function renderProjects() {
    api.listJobs().then(function (jobs) {
      var ul = $('project-list');
      ul.innerHTML = '';
      $('project-empty').hidden = jobs.length > 0;
      jobs.forEach(function (j) {
        var li = el('li', 'project-card');
        li.tabIndex = 0;
        var tpl = templateOf(j.template);
        var h = el('h3', null, j.title);
        var meta = el('div', 'meta');
        meta.appendChild(el('span', 'chip', tpl.name));
        meta.appendChild(el('span', 'status-tag status-' + j.status, j.status));
        meta.appendChild(el('span', null, '생성 ' + fmtTime(j.createdAt)));
        var sub = el('div', 'meta', 'jobId: ' + j.jobId + (j.pageCount ? ' · ' + j.pageCount + '쪽' : ''));
        li.appendChild(h); li.appendChild(meta); li.appendChild(sub);
        function open() { showEditor(j.jobId); }
        li.addEventListener('click', open);
        li.addEventListener('keydown', function (e) { if (e.key === 'Enter') open(); });
        ul.appendChild(li);
      });
    });
  }

  function openNewProjectModal() {
    var box = $('np-templates');
    box.innerHTML = '';
    S.consts.TEMPLATES.forEach(function (t, i) {
      var label = el('label', 'template-option' + (i === 0 ? ' selected' : ''));
      var input = el('input');
      input.type = 'radio'; input.name = 'np-template'; input.value = t.id;
      if (i === 0) input.checked = true;
      var sw = el('span', 'template-swatch');
      sw.style.aspectRatio = t.mm[0] + ' / ' + t.mm[1];
      var txt = el('span');
      txt.appendChild(el('span', 't-name', t.name));
      txt.appendChild(el('br'));
      txt.appendChild(el('span', 't-size', t.id + ' · ' + t.size));
      label.appendChild(input); label.appendChild(sw); label.appendChild(txt);
      input.addEventListener('change', function () {
        Array.prototype.forEach.call(box.children, function (c) { c.classList.remove('selected'); });
        label.classList.add('selected');
      });
      box.appendChild(label);
    });
    $('np-name').value = '';
    $('np-file').value = '';
    $('modal-new-project').hidden = false;
    $('np-name').focus();
  }

  function createProject() {
    var name = $('np-name').value.trim();
    if (!name) { toast('프로젝트명을 입력하세요.', true); $('np-name').focus(); return; }
    var tpl = document.querySelector('input[name="np-template"]:checked').value;
    var file = $('np-file').files[0] || null;
    $('modal-new-project').hidden = true;
    api.createJob(tpl, name, file).then(function (res) {
      showEditor(res.jobId);
    });
  }

  /* ═══════════ 편집기 초기화 ═══════════ */
  function initEditor() {
    api.getProject(S.jobId).then(function (p) {
      if (!p) { toast('프로젝트를 찾을 수 없습니다.', true); showProjects(); return; }
      S.project = p;
      S.pageCount = p.pageCount;
      $('ed-title').textContent = p.title;
      $('ed-template').textContent = templateOf(p.template).name + ' · ' + templateOf(p.template).id;
      buildStylePanels();
      renderThumbs();
      showPage(1);
      if (p.status === 'QUEUED' || p.status === 'RUNNING') startJobBar(p.jobId);
      if (p.lastManuscript) {
        var u = $('upload-last');
        u.hidden = false;
        u.textContent = '최근 반입: ' + p.lastManuscript.name + ' (' + fmtTime(p.lastManuscript.importedAt) + ')';
      } else {
        $('upload-last').hidden = true;
      }
    });
  }

  /* ═══════════ F3: 미리보기·썸네일·확대·대향 ═══════════ */
  function fitWidth() {
    var scroll = $('preview-scroll');
    var vw = Math.max(220, window.innerWidth - 64);
    var avail = Math.max(220, Math.min(scroll.clientWidth - 48, vw));
    var base = Math.min(PAGE_BASE_W, avail);
    if (S.spread && avail > 420) base = Math.min(PAGE_BASE_W, (avail - 8) / 2);
    return Math.round(base * S.scale);
  }

  function spreadPair(page) {
    /* 짝수 쪽이 왼쪽(배면), 홀수 쪽이 오른쪽(표지 면) */
    var left = page % 2 === 0 ? page : page - 1;
    var right = left + 1;
    return [left, right];
  }

  function pageImg(jobId, n, w) {
    var key = jobId + ':' + n + ':' + w;
    if (S.pageCache[key]) return Promise.resolve(S.pageCache[key]);
    return api.getPagePng(jobId, n, w / PAGE_BASE_W).then(function (url) {
      S.pageCache[key] = url;
      return url;
    });
  }

  function showPage(n) {
    if (!S.jobId || !S.project) return;
    n = Math.max(1, Math.min(S.pageCount, n));
    S.page = n;
    $('page-input').value = n;
    $('page-input').max = S.pageCount;
    $('page-total').textContent = '/ ' + S.pageCount;
    $('btn-prev-page').disabled = n <= 1;
    $('btn-next-page').disabled = n >= S.pageCount;
    $('style-target').textContent = n + '쪽 본문';
    markThumbActive(n);

    var holder = $('preview-holder');
    holder.style.width = '';
    var w = fitWidth();
    var imgL = $('page-img'), imgR = $('page-img-r');
    $('preview-loading').hidden = false;

    var left = n, right = null;
    if (S.spread) { var pr = spreadPair(n); left = pr[0]; right = pr[1]; }
    var jobs = [pageImg(S.jobId, left, w)];
    if (right !== null && right <= S.pageCount) jobs.push(pageImg(S.jobId, right, w));

    Promise.all(jobs).then(function (urls) {
      imgL.src = urls[0]; imgL.style.width = w + 'px'; imgL.alt = left + '쪽 미리보기';
      imgL.hidden = false;
      if (right !== null && urls.length > 1) {
        imgR.src = urls[1]; imgR.style.width = w + 'px'; imgR.hidden = false;
        imgR.alt = right + '쪽 미리보기';
      } else if (right !== null) {
        /* 대향 보기에서 없는 쪽(마지막 홀수 쪽 등)은 흰 여백으로 대체 */
        imgR.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
        imgR.style.width = w + 'px';
        imgR.style.background = '#fff';
        imgR.hidden = false;
      } else {
        imgR.hidden = true;
      }
      $('preview-loading').hidden = true;
    });
  }

  function refreshPages(pages) {
    /* 적용으로 바뀐 쪽: 캐시 무효화 후 재렌더 */
    (pages || []).forEach(function (p) {
      Object.keys(S.pageCache).forEach(function (k) {
        if (k.indexOf(S.jobId + ':' + p + ':') === 0) delete S.pageCache[k];
      });
      delete S.thumbCache[S.jobId + ':' + p];
    });
    if (pages && pages.length) {
      renderThumbs();
      showPage(S.page);
    }
  }

  function renderThumbs() {
    var ul = $('thumb-list');
    ul.innerHTML = '';
    for (var n = 1; n <= S.pageCount; n++) {
      (function (n) {
        var li = el('li', 'thumb-item');
        li.dataset.page = n;
        var img = el('img');
        img.alt = n + '쪽 썸네일';
        img.loading = 'lazy';
        var key = S.jobId + ':' + n;
        function set(src) { img.src = src; }
        if (S.thumbCache[key]) set(S.thumbCache[key]);
        else api.getThumbPng(S.jobId, n).then(function (url) { S.thumbCache[key] = url; set(url); });
        li.appendChild(img);
        li.appendChild(el('div', 'thumb-no', n + '쪽'));
        li.addEventListener('click', function () { showPage(n); });
        ul.appendChild(li);
      })(n);
    }
    markThumbActive(S.page);
  }
  function markThumbActive(n) {
    Array.prototype.forEach.call($('thumb-list').children, function (li) {
      li.classList.toggle('is-active', Number(li.dataset.page) === n);
    });
  }

  function setZoom(idx) {
    S.scale = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, idx))];
    $('zoom-label').textContent = Math.round(S.scale * 100) + '%';
    showPage(S.page);
  }
  function zoomIdx() {
    for (var i = 0; i < ZOOMS.length; i++) if (ZOOMS[i] === S.scale) return i;
    return 2;
  }

  /* ═══════════ 작업 진행 바 (폴링 2s, 명세 4.5) ═══════════ */
  function setBar(mode, jobId) {
    S.barMode = mode; S.activeBarJob = jobId;
    document.body.classList.toggle('has-jobbar', !!mode);
    $('job-progress').hidden = !mode;
  }
  function updateBar(data) {
    $('job-step').textContent = data.currentStep || '';
    $('job-pct').textContent = (data.progress || 0) + '%';
    $('job-bar').style.width = (data.progress || 0) + '%';
  }
  function startJobBar(jobId) {
    setBar('job', jobId);
    S.jobTimer = setInterval(function () {
      api.getJob(jobId).then(function (d) {
        updateBar(d);
        if (d.status === 'DONE' || d.status === 'FAILED' || d.status === 'CANCELLED') {
          clearInterval(S.jobTimer); S.jobTimer = null;
          setTimeout(function () { setBar(null); }, 900);
          if (d.status === 'DONE') { toast('조판 완료 — 미리보기 생성됨'); initEditor(); }
          else toast('작업 ' + d.status + (d.error ? ': ' + d.error : ''), true);
        }
      });
    }, 2000);
  }

  /* ═══════════ F4: 스타일 패널 ═══════════ */
  var stylePanelsBuilt = false;
  function buildStylePanels() {
    if (stylePanelsBuilt) return;
    stylePanelsBuilt = true;
    var pl = $('para-style-list');
    pl.innerHTML = '';
    S.consts.PARAGRAPH_STYLES.forEach(function (st) {
      var li = el('li');
      var names = el('span');
      names.appendChild(el('span', 's-name', st.label));
      names.appendChild(el('span', 's-label', '  ' + st.id));
      var btn = el('button', 'btn btn-sm', '적용');
      btn.addEventListener('click', function () { applyStyleCommand('style.paragraph.apply', st.id); });
      li.appendChild(names); li.appendChild(btn);
      pl.appendChild(li);
    });
    var cl = $('char-style-list');
    cl.innerHTML = '';
    S.consts.CHAR_STYLES.forEach(function (st) {
      var li = el('li');
      var names = el('span');
      names.appendChild(el('span', 's-name', st.label));
      names.appendChild(el('span', 's-label', '  ' + st.id));
      var btn = el('button', 'btn btn-sm', '적용');
      btn.addEventListener('click', function () { applyStyleCommand('style.character.apply', st.id); });
      li.appendChild(names); li.appendChild(btn);
      cl.appendChild(li);
    });
  }

  function applyStyleCommand(commandId, style) {
    var cmds = [{ id: commandId, params: { page: S.page, style: style } }];
    api.applyCommands(S.jobId, cmds, S.page + '쪽 ' + style + ' 적용').then(function (r) {
      if (!r.applied) { toast('적용 실패', true); return; }
      toast('적용됨 · 스냅샷 ' + r.snapshotId + ' 생성');
      markPreflightStale();
      refreshPages(r.touchedPages || [S.page]);
    });
  }

  /* ═══════════ F5: AI 수정 채팅 (제안→승인→적용) ═══════════ */
  function aiAppend(cls, text) {
    var log = $('ai-log');
    var m = el('div', 'ai-msg ' + cls, text);
    log.appendChild(m);
    log.scrollTop = log.scrollHeight;
    return m;
  }

  function sendAiMessage() {
    var input = $('ai-input');
    var msg = input.value.trim();
    if (!msg) return;
    input.value = '';
    $('ai-send').disabled = true;
    aiAppend('user', msg);
    api.aiPlan(S.jobId, msg).then(function (res) {
      $('ai-send').disabled = false;
      if (res.error) { aiAppend('agent', '계획 생성에 실패했습니다. (' + res.error + ')'); return; }
      if (res.blocked) {
        aiAppend('agent', '⛔ ' + res.reason + ' — 계획이 생성되지 않았습니다.');
        return;
      }
      renderPlanCard(res.plan);
    }).catch(function () {
      $('ai-send').disabled = false;
      aiAppend('agent', '네트워크/서버 오류로 계획을 만들지 못했습니다.');
    });
  }

  function renderPlanCard(plan) {
    var log = $('ai-log');
    var card = el('div', 'plan-card');
    card.appendChild(el('h4', null, '수정 계획 미리보기 (' + plan.length + '개 명령) — 아직 실행되지 않았습니다'));
    var ol = document.createElement('ol');
    plan.forEach(function (p) {
      var li = el('li');
      li.appendChild(document.createTextNode(p.description + ' — '));
      li.appendChild(el('code', null, p.command + ' ' + JSON.stringify(p.params)));
      ol.appendChild(li);
    });
    card.appendChild(ol);
    card.appendChild(el('p', 'plan-warn', '승인 시 적용 전에 자동 스냅샷이 생성되고, allowlist 명령만 실행됩니다.'));
    var acts = el('div', 'plan-actions');
    var ok = el('button', 'btn btn-primary btn-sm', '승인 후 적용');
    var no = el('button', 'btn btn-sm', '거절');
    ok.addEventListener('click', function () {
      ok.disabled = no.disabled = true;
      api.applyRevision(S.jobId, plan).then(function (r) {
        card.classList.add('resolved');
        if (r.applied) {
          aiAppend('system', '✓ 적용 완료 · 스냅샷 ' + r.snapshotId);
          toast('AI 수정 적용됨 (스냅샷 생성)');
          markPreflightStale();
          refreshPages(r.touchedPages || []);
        } else {
          aiAppend('system', '적용 실패');
        }
      });
    });
    no.addEventListener('click', function () {
      card.classList.add('resolved');
      aiAppend('system', '계획이 거절되었습니다. 아무것도 변경되지 않았습니다.');
    });
    acts.appendChild(ok); acts.appendChild(no);
    card.appendChild(acts);
    log.appendChild(card);
    log.scrollTop = log.scrollHeight;
  }

  /* ═══════════ F6: 검수 (preflight) ═══════════ */
  function runPreflight() {
    var box = $('preflight-result');
    box.innerHTML = '<p class="muted">검수 실행 중…</p>';
    switchTab('preflight');
    api.runPreflight(S.jobId).then(function (r) {
      S.preflight = r;
      box.innerHTML = '';
      var sum = el('div', 'preflight-summary ' + (r.passed ? 'pass' : 'fail'),
        r.passed ? '✓ 검수 통과' : '✕ 검수 실패 — 아래 문제를 확인하세요');
      box.appendChild(sum);
      appendFindings(box, 'sev-err', '빠짐 글리프 (missingGlyphs)', r.missingGlyphs, function (f) {
        return '쪽 ' + f.page + ' · ' + f.detail;
      });
      appendFindings(box, 'sev-warn', '본문 넘침 (overset)', r.overset, function (f) {
        return '쪽 ' + f.page + ' · ' + f.detail;
      });
      appendFindings(box, 'sev-warn', '저해상도 이미지 (lowPpi)', r.lowPpi, function (f) {
        return '쪽 ' + f.page + ' · ' + f.detail;
      });
      if (r.missingGlyphs.length > 0) {
        box.appendChild(el('p', 'export-blocked-note',
          '⚠ 빠짐 글리프 ' + r.missingGlyphs.length + '건 — 안전망: PDF 출력 버튼이 비활성화됩니다. AI 채팅에 "글리프 문제 해결해줘"라고 요청해 보세요.'));
      }
      updateExportButtonState();
    });
  }

  function appendFindings(box, sev, title, list, fmt) {
    if (!list.length) return;
    var g = el('div', 'finding-group ' + sev);
    g.appendChild(el('h4', null, title + ' (' + list.length + ')'));
    list.forEach(function (f) {
      var d = el('div', 'finding' + (sev === 'sev-err' ? ' sev-err' : ''));
      var pg = el('span', 'f-page', '→ ' + f.page + '쪽 보기');
      pg.addEventListener('click', function () { showPage(f.page); });
      d.appendChild(pg);
      d.appendChild(document.createTextNode(' ' + fmt(f)));
      g.appendChild(d);
    });
    box.appendChild(g);
  }

  function updateExportButtonState() {
    var blocked = S.preflight && S.preflight.missingGlyphs.length > 0;
    $('btn-export-top').disabled = !!blocked;
    $('btn-export-top').title = blocked ? '빠짐 글리프가 남아 있어 PDF 출력이 차단됩니다 (검수 안전망)' : '';
  }

  function markPreflightStale() {
    if (!S.preflight) return;
    S.preflight = null;
    $('preflight-result').innerHTML = '<p class="export-blocked-note" style="color:var(--warn)">' +
      '이후 편집이 변경되어 이전 검수 결과는 무효입니다. 다시 [검수 실행]하세요.</p>';
    updateExportButtonState();
  }

  /* ═══════════ F7: PDF 출력·산출물 ═══════════ */
  function startExport() {
    var standard = document.querySelector('input[name="ex-standard"]:checked').value;
    $('modal-export').hidden = true;
    api.startExport(S.jobId, 'pdf', standard).then(function (r) {
      toast('출력 작업 시작 (' + standard.toUpperCase() + ') — 2초 간격 폴링');
      setBar('export', S.jobId);
      $('job-step').textContent = 'PDF 출력 대기…';
      S.exportTimer = setInterval(function () {
        api.getExportState(S.jobId).then(function (d) {
          if (!d) return;
          updateBar(d);
          if (d.status === 'DONE') {
            clearInterval(S.exportTimer); S.exportTimer = null;
            setTimeout(function () { setBar(null); }, 900);
            renderArtifacts();
            switchTab('artifacts');
            toast('PDF 출력 완료 — 산출물 탭에서 다운로드');
          }
        });
      }, 2000);
    });
  }

  function renderArtifacts() {
    api.listArtifacts(S.jobId).then(function (r) {
      var ul = $('artifact-list');
      ul.innerHTML = '';
      $('artifact-empty').hidden = r.artifacts.length > 0;
      r.artifacts.forEach(function (a) {
        var li = el('li');
        var info = el('div');
        info.appendChild(el('div', 'a-name', a.fileName || (a.format + '-' + a.standard)));
        info.appendChild(el('div', 'a-meta', a.format.toUpperCase() + ' · ' + a.standard.toUpperCase() + ' · ' + fmtTime(a.createdAt)));
        var dl = el('a', null, '다운로드');
        dl.href = '#';
        dl.addEventListener('click', function (e) {
          e.preventDefault();
          api.downloadArtifact(S.jobId, a.artifactId).then(function (d) {
            if (!d) { toast('산출물을 찾을 수 없습니다.', true); return; }
            var link = document.createElement('a');
            link.href = d.url;
            link.download = d.fileName || (a.artifactId + '.pdf');
            document.body.appendChild(link);
            link.click();
            link.remove();
          });
        });
        li.appendChild(info); li.appendChild(dl);
        ul.appendChild(li);
      });
    });
  }

  /* ═══════════ F8: 버전 이력·복원 ═══════════ */
  function openVersions() {
    $('modal-versions').hidden = false;
    var ul = $('version-list');
    ul.innerHTML = '<li class="muted">불러오는 중…</li>';
    api.listVersions(S.jobId).then(function (r) {
      ul.innerHTML = '';
      if (!r.versions.length) { ul.appendChild(el('li', 'muted', '버전 기록이 없습니다.')); return; }
      r.versions.forEach(function (v, idx) {
        var li = el('li');
        var info = el('div');
        info.appendChild(el('div', 'v-label', v.label));
        info.appendChild(el('div', 'v-meta', v.source + ' · ' + fmtTime(v.createdAt)));
        li.appendChild(info);
        if (idx === 0) {
          li.appendChild(el('span', 'v-current', '최신'));
        } else {
          var btn = el('button', 'btn btn-sm', '이 시점으로 복원');
          btn.addEventListener('click', function () {
            if (!confirm('"' + v.label + '" 시점으로 복원할까요?\n현재 상태도 먼저 스냅샷됩니다.')) return;
            api.restoreVersion(S.jobId, v.versionId).then(function (res) {
              if (res.restored) {
                toast('복원 완료 · ' + res.snapshotId);
                $('modal-versions').hidden = true;
                api.getProject(S.jobId).then(function (p) {
                  S.pageCount = p.pageCount; S.thumbCache = {}; S.pageCache = {};
                  renderThumbs(); showPage(Math.min(S.page, p.pageCount));
                });
              }
            });
          });
          li.appendChild(btn);
        }
        ul.appendChild(li);
      });
    });
  }

  /* ═══════════ F2: 원고 업로드·매핑 미리보기 ═══════════ */
  var pendingUpload = null; // {file, inspect}
  function handleManuscriptFile(file) {
    if (!file) return;
    if (!/\.(md|txt|docx|rtf)$/i.test(file.name)) {
      toast('md · txt · docx · rtf 파일만 반입할 수 있습니다.', true);
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var text = typeof reader.result === 'string' ? reader.result : '';
      api.inspectManuscript(file.name, text).then(function (insp) {
        pendingUpload = { file: file, insp: insp };
        $('map-file').textContent = file.name + ' · ' + Math.round(file.size / 102.4) / 10 + ' KB' +
          (insp.binary ? ' (이진 원고 — 개수는 추정치)' : '') + ' · 예상 ' + insp.estPages + '쪽';
        var tb = $('map-rows');
        tb.innerHTML = '';
        insp.rows.forEach(function (row) {
          var tr = document.createElement('tr');
          tr.appendChild(el('td', null, row.source));
          tr.appendChild(el('td', null, String(row.count)));
          tr.appendChild(el('td', null, row.target));
          tb.appendChild(tr);
        });
        $('modal-mapping').hidden = false;
      });
    };
    if (/\.(md|txt)$/i.test(file.name)) {
      reader.readAsText(file);
    } else {
      /* 이진(docx/rtf)은 텍스트 샘플 없이 추정 매핑 */
      api.inspectManuscript(file.name, '').then(showMapping);
    }
    function showMapping(insp) {
      pendingUpload = { file: file, insp: insp };
      $('map-file').textContent = file.name + ' · ' + Math.round(file.size / 102.4) / 10 + ' KB (이진 원고 — 개수는 추정치) · 예상 ' + insp.estPages + '쪽';
      var tb = $('map-rows');
      tb.innerHTML = '';
      insp.rows.forEach(function (row) {
        var tr = document.createElement('tr');
        tr.appendChild(el('td', null, row.source));
        tr.appendChild(el('td', null, String(row.count)));
        tr.appendChild(el('td', null, row.target));
        tb.appendChild(tr);
      });
      $('modal-mapping').hidden = false;
    }
  }

  function confirmImport() {
    if (!pendingUpload) return;
    $('modal-mapping').hidden = true;
    api.importManuscript(S.jobId, pendingUpload.file.name, pendingUpload.insp.estPages).then(function (r) {
      if (!r.applied) { toast('반입 실패', true); return; }
      toast('원고 반입 완료 (autoflow) · 스냅샷 ' + r.snapshotId);
      if (r.pageCount) S.pageCount = r.pageCount;
      S.thumbCache = {}; S.pageCache = {};
      renderThumbs();
      showPage(S.page);
      var u = $('upload-last');
      u.hidden = false;
      u.textContent = '최근 반입: ' + pendingUpload.file.name + ' (' + fmtTime(Date.now()) + ')';
      pendingUpload = null;
    });
  }

  /* ═══════════ 탭·전역 바인딩 ═══════════ */
  function switchTab(name) {
    Array.prototype.forEach.call(document.querySelectorAll('.right-tabs .tab'), function (t) {
      var on = t.dataset.tab === name;
      t.classList.toggle('is-active', on);
      t.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    Array.prototype.forEach.call(document.querySelectorAll('.tab-panel'), function (p) {
      p.classList.toggle('is-active', p.id === 'tab-' + name);
    });
  }

  function bind() {
    $('btn-new-project').addEventListener('click', openNewProjectModal);
    $('np-create').addEventListener('click', createProject);
    $('btn-back').addEventListener('click', showProjects);
    $('btn-cancel-job').addEventListener('click', function () {
      if (S.barMode === 'job') api.cancelJob(S.activeBarJob).then(function () {
        clearInterval(S.jobTimer); setBar(null); toast('작업 취소됨');
      });
    });

    /* 미리보기 컨트롤 */
    $('btn-prev-page').addEventListener('click', function () { showPage(S.page - (S.spread ? 2 : 1)); });
    $('btn-next-page').addEventListener('click', function () { showPage(S.page + (S.spread ? 2 : 1)); });
    $('page-input').addEventListener('change', function () { showPage(Number(this.value) || 1); });
    $('btn-zoom-in').addEventListener('click', function () { setZoom(zoomIdx() + 1); });
    $('btn-zoom-out').addEventListener('click', function () { setZoom(zoomIdx() - 1); });
    $('chk-spread').addEventListener('change', function () { S.spread = this.checked; showPage(S.page); });
    window.addEventListener('resize', function () { if (S.jobId && !$('view-editor').hidden) showPage(S.page); });

    /* 상단 액션 */
    $('btn-preflight-top').addEventListener('click', runPreflight);
    $('btn-run-preflight').addEventListener('click', runPreflight);
    $('btn-export-top').addEventListener('click', function () {
      if (!S.preflight && !confirm('검수를 아직 실행하지 않았습니다. 그대로 출력할까요?')) return;
      $('modal-export').hidden = false;
    });
    $('ex-start').addEventListener('click', startExport);
    $('btn-versions').addEventListener('click', openVersions);
    $('btn-save').addEventListener('click', function () {
      api.saveNow(S.jobId).then(function (r) {
        if (r.saved) toast('저장됨 · 스냅샷 ' + r.snapshotId);
      });
    });

    /* 탭 */
    Array.prototype.forEach.call(document.querySelectorAll('.right-tabs .tab'), function (t) {
      t.addEventListener('click', function () {
        switchTab(t.dataset.tab);
        if (t.dataset.tab === 'artifacts') renderArtifacts();
      });
    });

    /* AI */
    $('ai-form').addEventListener('submit', function (e) { e.preventDefault(); sendAiMessage(); });

    /* 검수 시나리오 (mock 전용) */
    $('preflight-scenario-select').addEventListener('change', function () {
      api.setPreflightScenario(S.jobId, this.value).then(function () {
        if (S.preflight) runPreflight();
      });
    });

    /* 업로드 */
    $('btn-pick-file').addEventListener('click', function () { $('upload-input').click(); });
    $('upload-input').addEventListener('change', function () { handleManuscriptFile(this.files[0]); });
    var dz = $('upload-dropzone');
    ['dragenter', 'dragover'].forEach(function (ev) {
      dz.addEventListener(ev, function (e) { e.preventDefault(); dz.classList.add('dragover'); });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      dz.addEventListener(ev, function (e) { e.preventDefault(); dz.classList.remove('dragover'); });
    });
    dz.addEventListener('drop', function (e) {
      if (e.dataTransfer.files.length) handleManuscriptFile(e.dataTransfer.files[0]);
    });
    $('map-confirm').addEventListener('click', confirmImport);

    /* 모달 닫기 */
    Array.prototype.forEach.call(document.querySelectorAll('[data-close]'), function (b) {
      b.addEventListener('click', function () { $(b.dataset.close).hidden = true; });
    });
    Array.prototype.forEach.call(document.querySelectorAll('.modal-backdrop'), function (bd) {
      bd.addEventListener('click', function (e) { if (e.target === bd) bd.hidden = true; });
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') Array.prototype.forEach.call(document.querySelectorAll('.modal-backdrop'), function (bd) { bd.hidden = true; });
    });
  }

  /* ═══════════ 시작 ═══════════ */
  function init() {
    api.constants().then(function (c) {
      S.consts = c;
      var mock = api.isMock();
      $('mock-badge-projects').hidden = !mock;
      $('mock-badge-editor').hidden = !mock;
      var sc = $('preflight-scenario');
      if (sc) sc.hidden = !mock;
      bind();
      showProjects();
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();

  global.PublishingEditor = S; // 디버그용
})(window);
