/* 내신영어 기출분석 + 출제방향 제작 화면. 업로드 파일·결과는 서버에 저장되지 않는다(응답 후 이 화면에만 존재).
 * 파일(문서·HTML·사진)은 먼저 /api/exam-insight/extract 로 읽어 파일별 텍스트를 확인·수정한 뒤, 그 텍스트만 분석에 보낸다. */
(function (root) {
  'use strict';

  var EMAIL = 'woorinaesin@naver.com';
  var BATCH = 5;
  var MAX_DOC_BYTES = 20 * 1024 * 1024;

  function extOf(name) {
    var i = String(name || '').lastIndexOf('.');
    return i < 0 ? '' : name.slice(i + 1).toLowerCase();
  }

  /** 지원 형식이 아닌 파일명 목록. 서버 검증 전에 화면에서 먼저 알려 준다. */
  function unsupportedFiles(names, supported) {
    return names.filter(function (n) { return supported.indexOf(extOf(n)) < 0; });
  }

  /** 파일 종류: image | html | document | unsupported. 사진은 서버가 사진 분석을 켰을 때만 image다. */
  function classify(name, cfg) {
    var e = extOf(name);
    if ((cfg.imageFormats || []).indexOf(e) >= 0) { return 'image'; }
    if ((cfg.webFormats || []).indexOf(e) >= 0) { return 'html'; }
    if ((cfg.documentFormats || []).indexOf(e) >= 0) { return 'document'; }
    return 'unsupported';
  }

  function upper(list) { return list.map(function (s) { return s.toUpperCase(); }).join('·'); }

  function formatsNotice(cfg) {
    var parts = [];
    if (cfg.documentFormats && cfg.documentFormats.length) { parts.push('문서 ' + upper(cfg.documentFormats)); }
    if (cfg.webFormats && cfg.webFormats.length) { parts.push('웹 문서 ' + upper(cfg.webFormats)); }
    if (cfg.imageFormats && cfg.imageFormats.length) { parts.push('사진 ' + upper(cfg.imageFormats)); }
    if (!parts.length && cfg.supportedFormats) { parts.push(upper(cfg.supportedFormats)); }
    var text = '처리 가능한 형식: ' + parts.join(' / ') + '. 문서·HTML은 글자가 들어 있는 파일만 읽어요.';
    if (cfg.imageFormats && cfg.imageFormats.length) {
      text += ' 사진은 AI가 글자를 읽어 옮기고, 읽은 내용을 확인·수정할 수 있어요.';
    }
    text += ' 처리할 수 없는 형식: ' + cfg.unsupported.join(', ') + '.';
    return text;
  }

  function formatBytes(n) {
    if (n < 1024) { return n + 'B'; }
    if (n < 1024 * 1024) { return Math.round(n / 1024) + 'KB'; }
    return (Math.round(n / 1024 / 102.4) / 10) + 'MB';
  }

  /**
   * 새로 고른 파일을 검증한다: 형식·크기·개수·중복. 통과한 것과 사유가 있는 반려를 나눠 돌려준다.
   * files: [{name,size,key}], existing: 이미 목록에 있는 항목(key 포함).
   */
  function validateNew(files, existing, cfg) {
    var accepted = [], rejected = [];
    var keys = {};
    existing.forEach(function (it) { keys[it.key] = true; });
    var total = existing.length;
    var images = existing.filter(function (it) { return it.kind === 'image'; }).length;
    files.forEach(function (f) {
      var kind = classify(f.name, cfg);
      if (keys[f.key]) { rejected.push({ name: f.name, message: '이미 추가한 파일이에요.' }); return; }
      if (kind === 'unsupported') {
        var heic = /^(heic|heif)$/.test(extOf(f.name));
        rejected.push({ name: f.name, message: heic
          ? 'HEIC는 이 서버에서 변환할 수 없어요. JPG로 저장하거나 휴대폰에서 호환성 우선으로 촬영해 주세요.'
          : '처리할 수 없는 형식이에요.' });
        return;
      }
      var limit = kind === 'image' ? cfg.maxImageBytes : MAX_DOC_BYTES;
      if (f.size > limit) { rejected.push({ name: f.name, message: '파일이 너무 커요(최대 ' + formatBytes(limit) + ').' }); return; }
      if (total >= cfg.maxFilesPerGroup) { rejected.push({ name: f.name, message: '이 항목에는 최대 ' + cfg.maxFilesPerGroup + '개까지 올릴 수 있어요.' }); return; }
      if (kind === 'image' && images >= cfg.maxImagesPerRequest) { rejected.push({ name: f.name, message: '사진은 최대 ' + cfg.maxImagesPerRequest + '장까지 올릴 수 있어요.' }); return; }
      keys[f.key] = true;
      total++;
      if (kind === 'image') { images++; }
      accepted.push({ name: f.name, size: f.size, key: f.key, kind: kind });
    });
    return { accepted: accepted, rejected: rejected };
  }

  /** 목록에서 index 항목을 dir(-1 위, +1 아래)로 옮긴 새 배열. 범위를 벗어나면 그대로. */
  function moveItem(list, index, dir) {
    var to = index + dir;
    if (index < 0 || index >= list.length || to < 0 || to >= list.length) { return list.slice(); }
    var copy = list.slice();
    var tmp = copy[index]; copy[index] = copy[to]; copy[to] = tmp;
    return copy;
  }

  function tagOf(kind) { return kind === 'image' ? '[사진] ' : (kind === 'html' ? '[HTML] ' : ''); }

  /** 분석에 보낼 항목(읽기 완료된 것만, 사용자가 정한 순서대로). */
  function buildItemsPayload(items) {
    return items.filter(function (it) { return it.status === 'ok' && String(it.text || '').trim(); })
      .map(function (it) { return { label: tagOf(it.kind) + it.name, text: it.text }; });
  }

  /** 분석을 시작해도 되는지: 읽는 중·대기·오류 항목이 남아 있으면 사유를 돌려준다(없으면 null). */
  function blockingReason(items) {
    var busy = items.filter(function (it) { return it.status === 'queued' || it.status === 'reading'; }).length;
    if (busy) { return '파일을 읽는 중이에요. 잠시 기다려 주세요.'; }
    var bad = items.filter(function (it) { return it.status === 'error'; }).length;
    if (bad) { return '읽지 못한 파일이 ' + bad + '개 있어요. 삭제하거나 다시 시도해 주세요.'; }
    return null;
  }

  function statusLabel(it) {
    if (it.status === 'queued') { return '대기 중'; }
    if (it.status === 'reading') { return it.kind === 'image' ? '사진 읽는 중…' : '읽는 중…'; }
    if (it.status === 'ok') { return '읽기 완료 · ' + it.text.length + '자' + (it.uncertain ? ' · 확인 필요 ' + it.uncertain + '곳' : ''); }
    return '오류: ' + (it.message || '읽지 못했어요.');
  }

  function slug(id, kind) { return (kind === 'analysis' ? 'exam-analysis' : 'exam-direction') + '_' + id + '.md'; }

  function buildOrderMessage(id, meta) {
    return ['[BuildIt 영어 내신 예상문제 제작 의뢰]', '- 상품: 영어 내신 예상문제 40문항 / 1회 20,000원',
      '- 분석 번호: ' + id, '- 학교/학년: ' + [meta.school, meta.grade].filter(Boolean).join(' / '),
      '- 교과서: ' + (meta.publisher || ''), '- 시험: ' + (meta.examType || ''),
      '※ 분석 문서(.md 2개)를 함께 보내드리겠습니다.'].join('\n');
  }

  function externalPrompt(doc1, doc2) {
    return '아래 [기출 분석]과 [출제방향]을 근거로 영어 내신 예상문제 40문항을 만들어 주세요. '
      + '자료에 없는 사실은 지어내지 말고, 추정은 "추정"이라고 표시해 주세요.\n\n[기출 분석]\n' + doc1
      + '\n\n[출제방향]\n' + doc2;
  }

  var api = { extOf: extOf, unsupportedFiles: unsupportedFiles, classify: classify, formatsNotice: formatsNotice,
    formatBytes: formatBytes, validateNew: validateNew, moveItem: moveItem, buildItemsPayload: buildItemsPayload,
    blockingReason: blockingReason, statusLabel: statusLabel, tagOf: tagOf,
    slug: slug, buildOrderMessage: buildOrderMessage, externalPrompt: externalPrompt };
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
  root.ExamInsight = api;
  if (typeof document === 'undefined' || !document.getElementById('eiForm')) { return; }

  var $ = function (id) { return document.getElementById(id); };
  var cfg = null, last = null, seq = 0;
  var groups = {
    past: { items: [], list: $('eiPastList'), text: $('eiPastText'), files: $('eiPastFiles'), cam: $('eiPastCam'), camLabel: $('eiPastCamLabel') },
    scope: { items: [], list: $('eiScopeList'), text: $('eiScopeText'), files: $('eiScopeFiles'), cam: $('eiScopeCam'), camLabel: $('eiScopeCamLabel') }
  };

  function showErr(msg) { var e = $('eiErr'); e.textContent = msg || ''; e.hidden = !msg; }

  function badge(kind) { return kind === 'image' ? '사진' : (kind === 'html' ? 'HTML' : '문서'); }

  function render(name) {
    var g = groups[name];
    g.list.innerHTML = '';
    g.items.forEach(function (it, idx) {
      var li = document.createElement('li');
      li.className = 'ei-item';
      li.setAttribute('data-status', it.status);
      var row = document.createElement('div');
      row.className = 'ei-item-row';
      var nm = document.createElement('span');
      nm.className = 'ei-item-name';
      nm.textContent = (idx + 1) + '. ' + it.name + ' (' + formatBytes(it.size) + ')';
      var b = document.createElement('span');
      b.className = 'ei-badge';
      b.textContent = badge(it.kind);
      var st = document.createElement('span');
      st.className = 'ei-status is-' + it.status;
      st.textContent = statusLabel(it);
      row.appendChild(nm); row.appendChild(b); row.appendChild(st);
      function btn(label, fn, disabled) {
        var x = document.createElement('button');
        x.type = 'button'; x.textContent = label; x.disabled = !!disabled;
        x.addEventListener('click', fn);
        row.appendChild(x);
      }
      btn('▲', function () { g.items = moveItem(g.items, idx, -1); render(name); }, idx === 0);
      btn('▼', function () { g.items = moveItem(g.items, idx, 1); render(name); }, idx === g.items.length - 1);
      if (it.status === 'ok') {
        btn(it.open ? '접기' : '내용 확인·수정', function () { it.open = !it.open; render(name); });
      }
      if (it.status === 'error' && it.file) {
        btn('다시 시도', function () { it.status = 'queued'; it.message = ''; render(name); process(name); });
      }
      btn('삭제', function () { g.items.splice(idx, 1); render(name); });
      li.appendChild(row);
      if (it.status === 'ok' && it.uncertain) {
        var u = document.createElement('div');
        u.className = 'ei-uncertain';
        u.textContent = '읽기 불확실한 부분이 ' + it.uncertain + '곳 있어요. 내용 확인·수정에서 [확인필요] 표시를 사진과 비교해 고쳐 주세요. 고치지 않으면 분석에서 사실로 쓰지 않아요.';
        li.appendChild(u);
      }
      (it.notices || []).forEach(function (n) {
        var d = document.createElement('div');
        d.className = 'ei-uncertain';
        d.textContent = n;
        li.appendChild(d);
      });
      if (it.status === 'ok' && it.open) {
        var ta = document.createElement('textarea');
        ta.value = it.text;
        ta.setAttribute('aria-label', it.name + ' 추출 내용');
        ta.addEventListener('input', function () { it.text = ta.value; });
        li.appendChild(ta);
      }
      g.list.appendChild(li);
    });
  }

  function addFiles(name, fileList) {
    if (!cfg) { return; }
    showErr('');
    var g = groups[name];
    var picked = Array.prototype.map.call(fileList, function (f) {
      return { file: f, name: f.name, size: f.size, key: f.name + '|' + f.size + '|' + f.lastModified };
    });
    var res = validateNew(picked, g.items, cfg);
    var byKey = {};
    picked.forEach(function (p) { byKey[p.key] = p.file; });
    res.accepted.forEach(function (a) {
      g.items.push({ id: ++seq, key: a.key, name: a.name, size: a.size, kind: a.kind, file: byKey[a.key],
        status: 'queued', text: '', uncertain: 0, message: '', notices: [], open: false });
    });
    res.rejected.forEach(function (r) {
      g.items.push({ id: ++seq, key: 'x' + (++seq), name: r.name, size: 0, kind: 'document', file: null,
        status: 'error', text: '', uncertain: 0, message: r.message, notices: [], open: false });
    });
    render(name);
    process(name);
  }

  /** 대기 중인 파일을 BATCH개씩 서버에 보내 읽는다(순서대로). */
  function process(name) {
    var g = groups[name];
    if (g.busy) { return; }
    var batch = g.items.filter(function (it) { return it.status === 'queued'; }).slice(0, BATCH);
    if (!batch.length) { return; }
    g.busy = true;
    batch.forEach(function (it) { it.status = 'reading'; });
    render(name);
    var fd = new FormData();
    fd.append('group', name);
    batch.forEach(function (it) { fd.append('files', it.file); });
    fetch('/api/exam-insight/extract', { method: 'POST', body: fd, credentials: 'same-origin' })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
      .then(function (x) {
        if (!x.ok || x.j.status !== 'done') { throw new Error(x.j.message || '파일을 읽지 못했어요.'); }
        batch.forEach(function (it, i) {
          var r = x.j.items[i] || { status: 'error', message: '결과를 받지 못했어요.' };
          if (r.status === 'ok') {
            it.status = 'ok'; it.text = r.text; it.uncertain = r.uncertainCount || 0; it.kind = r.kind || it.kind;
            it.notices = r.notices || []; it.open = it.uncertain > 0;
          } else { it.status = 'error'; it.message = r.message || '읽지 못했어요.'; }
        });
        if (typeof x.j.remainingImagesToday === 'number') { cfg.remainingImagesToday = x.j.remainingImagesToday; showQuota(); }
      })
      .catch(function (e) {
        batch.forEach(function (it) { it.status = 'error'; it.message = e.message || '파일을 읽지 못했어요.'; });
      })
      .then(function () { g.busy = false; render(name); process(name); });
  }

  function showQuota() {
    var q = '';
    if (cfg.enabled) {
      q = '오늘 남은 분석 횟수: ' + cfg.remainingToday + '/' + cfg.perUserDailyLimit;
      if (cfg.imageAnalysis) { q += ' · 오늘 읽을 수 있는 사진: ' + cfg.remainingImagesToday + '/' + cfg.imageDailyLimit + '장'; }
    }
    $('eiQuota').textContent = q;
  }

  function acceptAttr(c) {
    var exts = (c.documentFormats || []).concat(c.webFormats || [], c.imageFormats || []);
    var mimes = (c.imageFormats || []).length ? ['image/jpeg', 'image/png', 'image/webp'] : [];
    return exts.map(function (e) { return '.' + e; }).concat(mimes).join(',');
  }

  fetch('/api/exam-insight/config', { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (c) {
    cfg = c;
    $('eiFormats').textContent = formatsNotice(c);
    showQuota();
    ['past', 'scope'].forEach(function (n) {
      groups[n].files.setAttribute('accept', acceptAttr(c));
      groups[n].camLabel.hidden = !c.imageAnalysis;
    });
    if (!c.enabled) { showErr('기출분석 기능이 현재 비활성화되어 있습니다.'); $('eiSubmit').disabled = true; }
  }).catch(function () { showErr('설정을 불러오지 못했습니다. 새로고침해 주세요.'); });

  ['past', 'scope'].forEach(function (n) {
    var g = groups[n];
    [g.files, g.cam].forEach(function (input) {
      input.addEventListener('change', function () {
        addFiles(n, input.files);
        input.value = ''; // 같은 파일을 다시 고를 수 있게 비운다
      });
    });
    var box = g.list.parentNode;
    box.addEventListener('dragover', function (ev) { ev.preventDefault(); box.classList.add('ei-dragover'); });
    box.addEventListener('dragleave', function () { box.classList.remove('ei-dragover'); });
    box.addEventListener('drop', function (ev) {
      ev.preventDefault(); box.classList.remove('ei-dragover');
      if (ev.dataTransfer && ev.dataTransfer.files) { addFiles(n, ev.dataTransfer.files); }
    });
  });

  $('eiForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    showErr('');
    if (!cfg) { return; }
    var names = { past: '기출문제', scope: '시험범위 자료' };
    var payload = {};
    for (var n in groups) {
      if (!Object.prototype.hasOwnProperty.call(groups, n)) { continue; }
      var g = groups[n];
      var why = blockingReason(g.items);
      if (why) { showErr(names[n] + ': ' + why); return; }
      payload[n] = buildItemsPayload(g.items);
      if (!payload[n].length && !g.text.value.trim()) { showErr(names[n] + ' 파일·사진 또는 텍스트를 입력해 주세요.'); return; }
    }
    var fd = new FormData();
    fd.append('pastItems', JSON.stringify(payload.past));
    fd.append('scopeItems', JSON.stringify(payload.scope));
    [['pastText', 'eiPastText'], ['scopeText', 'eiScopeText'], ['school', 'eiSchool'], ['grade', 'eiGrade'],
      ['publisher', 'eiPublisher'], ['examType', 'eiExamType'], ['note', 'eiNote']].forEach(function (p) {
      fd.append(p[0], $(p[1]).value);
    });
    $('eiSubmit').disabled = true;
    $('eiProgress').hidden = false;
    $('eiResult').hidden = true;
    fetch('/api/exam-insight/analyze', { method: 'POST', body: fd, credentials: 'same-origin' })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
      .then(function (x) {
        if (!x.ok || x.j.status !== 'done') { throw new Error(x.j.message || '분석에 실패했습니다.'); }
        last = x.j;
        $('eiDoc1').textContent = x.j.analysisMarkdown;
        $('eiDoc2').textContent = x.j.directionMarkdown;
        $('eiId').textContent = '#' + x.j.analysisId;
        $('eiNotices').textContent = (x.j.notices || []).join(' ');
        cfg.remainingToday = x.j.remainingToday;
        showQuota();
        $('eiResult').hidden = false;
        $('eiResult').scrollIntoView({ behavior: 'smooth' });
      })
      .catch(function (e) { showErr(e.message || '분석에 실패했습니다. 잠시 후 다시 시도해 주세요.'); })
      .then(function () { $('eiProgress').hidden = true; $('eiSubmit').disabled = false; });
  });

  function docOf(kind) { return kind === 'analysis' ? last.analysisMarkdown : last.directionMarkdown; }
  function copy(text, btn) {
    (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()).then(function () {
      var o = btn.textContent; btn.textContent = '복사됨'; setTimeout(function () { btn.textContent = o; }, 1500);
    }).catch(function () { showErr('복사에 실패했습니다. 다운로드를 이용해 주세요.'); });
  }
  document.addEventListener('click', function (ev) {
    var t = ev.target; if (!last || !t.closest) { return; }
    var dl = t.closest('[data-dl]'), cp = t.closest('[data-copy]');
    if (dl) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([docOf(dl.dataset.dl)], { type: 'text/markdown;charset=utf-8' }));
      a.download = slug(last.analysisId, dl.dataset.dl);
      document.body.appendChild(a); a.click(); a.remove();
    } else if (cp) { copy(docOf(cp.dataset.copy), cp); }
  });
  $('eiCopyPrompt').addEventListener('click', function () {
    if (last) { copy(externalPrompt(last.analysisMarkdown, last.directionMarkdown), $('eiCopyPrompt')); }
  });
  $('eiOrder').addEventListener('click', function () {
    if (!last) { return; }
    var msg = buildOrderMessage(last.analysisId, { school: $('eiSchool').value, grade: $('eiGrade').value,
      publisher: $('eiPublisher').value, examType: $('eiExamType').value });
    $('eiOrderId').textContent = last.analysisId;
    $('eiOrderMsg').value = msg;
    $('eiOrderMail').href = 'mailto:' + EMAIL + '?subject=' + encodeURIComponent('[BuildIt] 예상문제 제작 의뢰 ' + last.analysisId)
      + '&body=' + encodeURIComponent(msg);
    $('eiOrderModal').hidden = false;
  });
  $('eiOrderCopy').addEventListener('click', function () { copy($('eiOrderMsg').value, $('eiOrderCopy')); });
  $('eiOrderClose').addEventListener('click', function () { $('eiOrderModal').hidden = true; });
})(typeof window !== 'undefined' ? window : globalThis);
