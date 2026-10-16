/* 내신영어 기출분석 + 출제방향 제작 화면. 업로드 파일·결과는 서버에 저장되지 않는다(응답 후 이 화면에만 존재). */
(function (root) {
  'use strict';

  var EMAIL = 'woorinaesin@naver.com';

  function extOf(name) {
    var i = String(name || '').lastIndexOf('.');
    return i < 0 ? '' : name.slice(i + 1).toLowerCase();
  }

  /** 지원 형식이 아닌 파일명 목록. 서버 검증 전에 화면에서 먼저 알려 준다. */
  function unsupportedFiles(names, supported) {
    return names.filter(function (n) { return supported.indexOf(extOf(n)) < 0; });
  }

  function formatsNotice(cfg) {
    return '처리 가능한 형식: ' + cfg.supportedFormats.map(function (s) { return s.toUpperCase(); }).join(', ')
      + ' (텍스트가 들어 있는 파일만). 처리할 수 없는 형식: ' + cfg.unsupported.join(', ')
      + '. 이미지·스캔본은 글자를 읽지 못하므로 텍스트로 붙여넣어 주세요.';
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

  var api = { extOf: extOf, unsupportedFiles: unsupportedFiles, formatsNotice: formatsNotice,
    slug: slug, buildOrderMessage: buildOrderMessage, externalPrompt: externalPrompt };
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
  root.ExamInsight = api;
  if (typeof document === 'undefined' || !document.getElementById('eiForm')) { return; }

  var $ = function (id) { return document.getElementById(id); };
  var cfg = null, last = null;

  function showErr(msg) { var e = $('eiErr'); e.textContent = msg || ''; e.hidden = !msg; }
  function names(input) { return Array.prototype.map.call(input.files, function (f) { return f.name; }); }
  function renderList(input, ul) {
    ul.innerHTML = '';
    names(input).forEach(function (n) {
      var li = document.createElement('li');
      li.textContent = n + (unsupportedFiles([n], cfg ? cfg.supportedFormats : []).length ? ' — ⚠ 처리할 수 없는 형식' : '');
      ul.appendChild(li);
    });
  }

  fetch('/api/exam-insight/config', { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (c) {
    cfg = c;
    $('eiFormats').textContent = formatsNotice(c);
    $('eiQuota').textContent = c.enabled ? '오늘 남은 분석 횟수: ' + c.remainingToday + '/' + c.perUserDailyLimit : '';
    if (!c.enabled) { showErr('기출분석 기능이 현재 비활성화되어 있습니다.'); $('eiSubmit').disabled = true; }
  }).catch(function () { showErr('설정을 불러오지 못했습니다. 새로고침해 주세요.'); });

  $('eiPastFiles').addEventListener('change', function () { renderList($('eiPastFiles'), $('eiPastList')); });
  $('eiScopeFiles').addEventListener('change', function () { renderList($('eiScopeFiles'), $('eiScopeList')); });

  $('eiForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    showErr('');
    if (!cfg) { return; }
    var past = $('eiPastFiles').files, scope = $('eiScopeFiles').files;
    if (!past.length && !$('eiPastText').value.trim()) { showErr('기출문제 파일 또는 텍스트를 입력해 주세요.'); return; }
    if (!scope.length && !$('eiScopeText').value.trim()) { showErr('시험범위 자료 파일 또는 텍스트를 입력해 주세요.'); return; }
    var bad = unsupportedFiles(names($('eiPastFiles')).concat(names($('eiScopeFiles'))), cfg.supportedFormats);
    if (bad.length) { showErr('처리할 수 없는 형식입니다: ' + bad.join(', ')); return; }
    var fd = new FormData();
    Array.prototype.forEach.call(past, function (f) { fd.append('pastFiles', f); });
    Array.prototype.forEach.call(scope, function (f) { fd.append('scopeFiles', f); });
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
        $('eiQuota').textContent = '오늘 남은 분석 횟수: ' + x.j.remainingToday + '/' + cfg.perUserDailyLimit;
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
