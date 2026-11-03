/* 입시자료 적재·전체 승격 관리자 도구 — 기존 ADMIN 권한(/api/admin/admissions/**)만 재사용한다.
 * 새 인증 없음. 파일은 브라우저에서만 읽고, 적재는 관리자가 미리보기 확인 후 직접 실행한다. */
(function (root) {
  'use strict';

  /** 시드 JSON 텍스트의 건수와 참조 무결성을 계산한다. 서버를 호출하지 않는다. */
  function summarizeSeed(text) {
    var out = { ok: false, error: null, counts: null, years: [], warnings: [] };
    var seed;
    try { seed = JSON.parse(text); }
    catch (e) { out.error = 'JSON 형식이 올바르지 않습니다.'; return out; }
    if (!seed || typeof seed !== 'object' || Array.isArray(seed)) { out.error = '시드 최상위가 객체가 아닙니다.'; return out; }
    function list(name) { return Array.isArray(seed[name]) ? seed[name] : []; }
    var universities = list('ipsi_universities'), documents = list('ipsi_documents'), departments = list('ipsi_departments'),
        types = list('ipsi_admission_types'), schedules = list('ipsi_schedules');
    if (!universities.length) { out.error = 'ipsi_universities가 비어 있습니다.'; return out; }
    out.counts = { universities: universities.length, documents: documents.length, departments: departments.length,
                   types: types.length, schedules: schedules.length, youtube: list('youtube_knowledge').length,
                   priorResults: list('prior_results').length };
    var codes = {}, docKeys = {}, years = {};
    universities.forEach(function (u) { codes[u.university_code] = true; });
    documents.forEach(function (d) { docKeys[d.document_key] = true; years[d.applicable_academic_year] = true; });
    var badUniv = 0, badDoc = 0;
    [departments, types, schedules, list('prior_results')].forEach(function (rows) {
      rows.forEach(function (r) {
        years[r.academic_year] = true;
        if (!codes[r.university_code]) badUniv++;
        if (r.source_document_key && !docKeys[r.source_document_key]) badDoc++;
        if (!r.source_document_key) badDoc++;
      });
    });
    if (badUniv) out.warnings.push('알 수 없는 university_code 행 ' + badUniv + '건 (적재 시 전체 거부됩니다)');
    if (badDoc) out.warnings.push('출처 자료(source_document_key)가 없거나 연결되지 않은 행 ' + badDoc + '건');
    var dupKeys = documents.length - Object.keys(docKeys).length;
    if (dupKeys > 0) out.warnings.push('중복된 document_key ' + dupKeys + '건');
    out.years = Object.keys(years).filter(function (y) { return y !== 'undefined'; }).map(Number).sort();
    if (out.years.some(function (y) { return y !== 2027 && y !== 2028; })) out.warnings.push('2027·2028 외 학년도가 포함되어 있습니다 (해당 행은 적재되지 않습니다)');
    out.ok = badUniv === 0;
    return out;
  }

  /**
   * 적재 버튼·파일 선택 칸의 잠금 상태. 적재가 도는 동안에는 파일을 다시 고를 수 없고(다시 고르면 버튼이 켜져 두 번 눌리던 문제),
   * 적재가 끝난 뒤에도 같은 파일로 버튼이 다시 켜지지 않는다(파일을 새로 골라야 한다).
   */
  function loadControls(state) {
    var loading = !!(state && state.loading);
    return { fileDisabled: loading, runDisabled: loading || !(state && state.previewOk) || !!(state && state.loaded) };
  }

  /** 승격 확인 문자열. 서버 규칙(AdmissionsActivationService)과 같아야 한다. */
  function confirmationFor(year) { return 'ACTIVATE-ALL-' + year; }

  /** 승격 제외 자료 키 입력 파싱: 쉼표 구분, 공백 제거, 빈 항목·중복 제거. */
  function parseExcludeKeys(text) {
    if (!text) return [];
    var seen = {}, out = [];
    String(text).split(',').forEach(function (part) {
      var key = part.trim();
      if (key && !seen[key]) { seen[key] = true; out.push(key); }
    });
    return out;
  }

  /** 재색인 상태 응답(GET /knowledge/reindex/status)을 화면 문구·진행률로 바꾼다. 서버를 호출하지 않는다. */
  function formatReindexStatus(st) {
    var out = { percent: 0, headline: '', failureLines: [], hasFailures: false, running: false };
    if (!st) return out;
    var total = st.total || 0, done = st.done || 0;
    out.percent = total > 0 ? Math.round(done * 100 / total) : 0;
    out.running = !!st.running;
    var run = st.lastRun;
    var parts = ['전체 ' + total + ' · 완료 ' + done + ' · 미완료 ' + (st.missing || 0)];
    if (st.running && run) {
      parts.push('진행 중 ' + run.processed + '/' + run.total + (run.currentId ? ' (항목 ' + run.currentId + ' 처리 중)' : ''));
      if (st.cancelRequested) parts.push('중단 요청됨');
    }
    if (run && run.failed > 0) parts.push('이번 실행 실패 ' + run.failed);
    if (!st.running && run) {
      var label = { COMPLETED: '완료', CANCELLED: '중단됨', ERROR: '오류' }[run.result] || run.result;
      parts.push('마지막 실행 ' + label);
    }
    parts.push('검색 인덱스 ' + (st.indexSize || 0) + (st.indexReady ? ' (준비됨)' : ' (준비 안 됨)'));
    out.headline = parts.join(' · ');
    var failures = (run && run.failures) || {};
    Object.keys(failures).forEach(function (id) { out.failureLines.push('항목 ' + id + ': ' + failures[id]); });
    out.hasFailures = out.failureLines.length > 0;
    return out;
  }

  /** 적재 응답을 한 줄 문구로 바꾼다. 갱신·보관 건수는 서버가 보낸 경우에만 붙인다. */
  function formatLoadResult(r) {
    var text = '적재 결과 — 대학 ' + r.universities + ' · 공식자료 ' + r.materials + ' · 학과 ' + r.departments + ' · 전형 ' + r.types +
        ' · 일정 ' + r.schedules + ' (건너뜀 ' + r.skippedUnknownYear + ')';
    if (typeof r.departmentsUpdated === 'number') text += ' · 갱신 학과 ' + r.departmentsUpdated + ' · 갱신 전형 ' + r.typesUpdated;
    if (typeof r.schedulesArchived === 'number') text += ' · 보관 일정 ' + r.schedulesArchived;
    if (typeof r.supersededArchived === 'number') text += ' · 예전 자료 보관 ' + r.supersededArchived;
    if (typeof r.schedulesSourced === 'number') text += ' · 일정 출처 연결 ' + r.schedulesSourced;
    if (typeof r.youtubeKnowledge === 'number' && r.youtubeKnowledge > 0) text += ' · 유튜브 해석 ' + r.youtubeKnowledge;
    if (typeof r.priorResults === 'number') text += ' · 지난해 입시결과 신규 ' + r.priorResults + ' · 변경 ' + r.priorResultsUpdated;
    return text + ' · 상태 ' + r.status;
  }

  /** 여러 줄 입력에서 시드 URL 목록을 뽑는다(빈 줄·앞뒤 공백 무시). */
  function parseSeedUrls(text) {
    return String(text || '').split(/\r?\n/).map(function (l) { return l.trim(); }).filter(function (l) { return l.length > 0; });
  }

  /** URL 목록 적재 상태 → 한 줄 요약과 진행률. */
  function formatSeedUrlStatus(st) {
    var run = st && st.lastRun;
    if (!run) return { running: !!(st && st.running), percent: 0, headline: '아직 실행한 URL 목록 적재가 없습니다.', failed: false };
    var percent = run.total ? Math.floor(run.processed * 100 / run.total) : 0;
    var head = 'URL 목록 적재 ' + run.result + ' — ' + run.processed + '/' + run.total + '개 파일 · 지난해 입시결과 신규 ' +
        (run.priorResults || 0) + ' · 변경 ' + (run.priorResultsUpdated || 0);
    if (run.current) head += ' · 진행 중: ' + run.current;
    if (run.error) head += '\n실패: ' + run.error + ' (끝난 파일은 남아 있습니다. 고친 뒤 다시 실행하면 중복 없이 이어집니다.)';
    return { running: !!st.running, percent: percent, headline: head, failed: run.result === 'FAILED' };
  }

  /** 지식 항목 생성 잡 상태 → 한 줄 요약. */
  function formatBuildStatus(st) {
    var run = st && st.lastRun;
    if (!run) return { running: !!(st && st.running), headline: '아직 실행한 지식 항목 생성이 없습니다.', failed: false };
    var head = '지식 항목 생성 ' + run.result + ' — ' + run.phase + ' ' + run.phaseDone + '/' + run.phaseTotal +
        ' · 새로 ' + run.created + ' · 갱신 ' + run.updated + ' · 변경 없음 ' + run.unchanged;
    if (run.error) head += '\n오류: ' + run.error;
    return { running: !!st.running, headline: head, failed: run.result === 'ERROR' };
  }

  var api = { summarizeSeed: summarizeSeed, loadControls: loadControls, confirmationFor: confirmationFor, parseExcludeKeys: parseExcludeKeys,
              formatReindexStatus: formatReindexStatus, formatLoadResult: formatLoadResult, parseSeedUrls: parseSeedUrls,
              formatSeedUrlStatus: formatSeedUrlStatus, formatBuildStatus: formatBuildStatus };
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
  root.AdmissionsAdminLoad = api;

  if (typeof document === 'undefined') return;
  document.addEventListener('DOMContentLoaded', function () {
    var panel = document.getElementById('ag-load');
    if (!panel) return;
    function byId(id) { return document.getElementById(id); }
    var fileText = null, preview = null, loading = false, loaded = false;
    function applyLoadControls() {
      var c = loadControls({ loading: loading, loaded: loaded, previewOk: !!(preview && preview.ok) });
      byId('ag-load-file').disabled = c.fileDisabled;
      byId('ag-load-run').disabled = c.runDisabled;
    }
    var result = byId('ag-load-result');

    function show(msg, isError) { result.textContent = msg; result.className = isError ? 'ag-load-error' : ''; }
    async function post(path, body) {
      var res = await fetchAuth(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      var data = await res.json().catch(function () { return null; });
      if (!res.ok) {
        var reason = res.status === 403 ? '관리자 권한이 필요합니다.' : (data && (data.error || data.status)) || ('요청 실패 (' + res.status + ')');
        throw new Error(reason);
      }
      return data;
    }

    byId('ag-load-file').addEventListener('change', function (ev) {
      if (loading) return;
      var f = ev.target.files && ev.target.files[0];
      preview = null; fileText = null; loaded = false; applyLoadControls();
      if (!f) { show('', false); return; }
      var reader = new FileReader();
      reader.onload = function () {
        fileText = String(reader.result);
        preview = summarizeSeed(fileText);
        if (preview.error) { show('미리보기 실패: ' + preview.error, true); return; }
        var c = preview.counts;
        show('미리보기 — 대학 ' + c.universities + ' · 공식자료 ' + c.documents + ' · 학과 ' + c.departments + ' · 전형 ' + c.types +
             ' · 일정 ' + c.schedules + (c.youtube ? ' · 유튜브 해석 ' + c.youtube : '') +
             (c.priorResults ? ' · 지난해 입시결과 ' + c.priorResults : '') + ' / 학년도 ' + preview.years.join(', ') +
             (preview.warnings.length ? '\n⚠ ' + preview.warnings.join('\n⚠ ') : '\n이상 없음. 적재는 DRAFT 상태로 들어가며 기존 자료는 중복 적재되지 않습니다.'), preview.warnings.length > 0 && !preview.ok);
        applyLoadControls();
      };
      reader.readAsText(f, 'utf-8');
    });

    byId('ag-load-run').addEventListener('click', async function () {
      if (loading || loaded || !preview || !preview.ok || !fileText) return;
      var c = preview.counts;
      if (!window.confirm('대학 ' + c.universities + ' · 공식자료 ' + c.documents + ' · 전형 ' + c.types + ' · 일정 ' + c.schedules +
          (c.priorResults ? ' · 지난해 입시결과 ' + c.priorResults : '') + '건을 DRAFT로 적재합니다. DB 백업을 확인하셨습니까?')) return;
      loading = true; applyLoadControls();
      show('적재 중입니다. 1~3분 걸립니다. 결과가 나올 때까지 이 화면을 그대로 두세요.', false);
      try {
        var r = await post('/api/admin/admissions/load/seed', { seedJson: fileText });
        loaded = true;
        show(formatLoadResult(r), false);
      } catch (e) { show('적재 실패: ' + e.message, true); }
      finally { loading = false; applyLoadControls(); }
    });

    byId('ag-activate-run').addEventListener('click', async function () {
      var year = Number(byId('ag-activate-year').value);
      var typed = byId('ag-activate-confirm').value.trim();
      if (typed !== confirmationFor(year)) { show('확인 문자열이 맞지 않습니다. ' + confirmationFor(year) + ' 를 입력하세요.', true); return; }
      var excludeKeys = parseExcludeKeys(byId('ag-activate-exclude').value);
      var excludeNote = excludeKeys.length ? '\n단, 다음 자료와 그 하위 행은 승격에서 제외합니다: ' + excludeKeys.join(', ') : '';
      if (!window.confirm(year + '학년도 공식자료·학과·전형·일정(DRAFT·REVIEW)을 전체 ACTIVE로 승격합니다.' + excludeNote + ' 계속할까요?')) return;
      try {
        var body = { academicYear: year, confirm: typed, note: '관리자 화면 전체 승격' };
        if (excludeKeys.length) body.excludeFileKeys = excludeKeys;
        var r = await post('/api/admin/admissions/activate-all', body);
        show('승격 결과 — 공식자료 ' + r.materials + ' · 학과 ' + r.departments + ' · 전형 ' + r.types + ' · 일정 ' + r.schedules +
             (typeof r.excludedMaterials === 'number' ? ' · 제외 자료 ' + r.excludedMaterials + ' · 제외 하위 행 ' + r.excludedChildren : '') +
             (typeof r.knowledgeItems === 'number' ? ' · 지식항목 ' + r.knowledgeItems : '') +
             (typeof r.priorResults === 'number' ? ' · 지난해 입시결과 ' + r.priorResults : '') + ' · ' + r.status, false);
      } catch (e) { show('승격 실패: ' + e.message, true); }
    });

    async function getJson(path) {
      var res = await fetchAuth(path);
      if (!res.ok) throw new Error(res.status === 403 ? '관리자 권한이 필요합니다.' : '상태 조회 실패 (' + res.status + ')');
      return res.json();
    }

    // 지식 항목 생성: 백그라운드 잡 — 시작 후 2초마다 상태를 본다.
    var buildTimer = null;
    async function pollBuild() {
      try {
        var v = formatBuildStatus(await getJson('/api/admin/admissions/knowledge/build/status'));
        byId('ag-build-status').textContent = v.headline;
        byId('ag-build-status').className = v.failed ? 'ag-load-error' : '';
        byId('ag-knowledge-build').disabled = v.running;
        buildTimer = v.running ? setTimeout(pollBuild, 2000) : null;
      } catch (e) { byId('ag-build-status').textContent = e.message; buildTimer = null; }
    }
    byId('ag-knowledge-build').addEventListener('click', async function () {
      if (!window.confirm('전형·일정·지난해 입시결과 행을 검색용 지식 항목으로 변환합니다(백그라운드, DRAFT로 생성, 기존 항목은 내용이 바뀐 것만 갱신). 계속할까요?')) return;
      try {
        await post('/api/admin/admissions/knowledge/build', {});
        show('지식 항목 생성을 시작했습니다. 아래 진행 상황을 확인하세요.', false);
      } catch (e) { show('생성 시작 실패: ' + e.message, true); }
      if (buildTimer) clearTimeout(buildTimer);
      pollBuild();
    });
    pollBuild();

    // 시드 URL 목록 적재: 서버가 고정 커밋 raw 파일을 차례로 받아 적재한다.
    var urlTimer = null;
    async function pollSeedUrls() {
      try {
        var v = formatSeedUrlStatus(await getJson('/api/admin/admissions/load/seed-urls/status'));
        byId('ag-url-progress').value = v.percent;
        byId('ag-url-status').textContent = v.headline;
        byId('ag-url-status').className = v.failed ? 'ag-load-error' : '';
        byId('ag-url-run').disabled = v.running;
        urlTimer = v.running ? setTimeout(pollSeedUrls, 3000) : null;
      } catch (e) { byId('ag-url-status').textContent = e.message; urlTimer = null; }
    }
    byId('ag-url-run').addEventListener('click', async function () {
      var urls = parseSeedUrls(byId('ag-url-list').value);
      if (!urls.length) { show('적재할 시드 URL을 한 줄에 하나씩 넣으세요.', true); return; }
      if (!window.confirm('시드 파일 ' + urls.length + '개를 서버가 차례로 받아 DRAFT로 적재합니다. DB 백업을 확인하셨습니까?')) return;
      try {
        await post('/api/admin/admissions/load/seed-urls', { urls: urls });
        show('URL 목록 적재를 시작했습니다. 아래 진행 상황을 확인하세요.', false);
      } catch (e) { show('URL 목록 적재 실패: ' + e.message, true); }
      if (urlTimer) clearTimeout(urlTimer);
      pollSeedUrls();
    });
    pollSeedUrls();

    var pollTimer = null;
    function renderReindex(st) {
      var v = formatReindexStatus(st);
      byId('ag-reindex-progress').value = v.percent;
      byId('ag-reindex-percent').textContent = v.percent + '%';
      byId('ag-reindex-status').textContent = v.headline + (v.hasFailures ? '\n' + v.failureLines.slice(0, 20).join('\n') : '');
      byId('ag-reindex-status').className = v.hasFailures ? 'ag-load-error' : '';
      byId('ag-knowledge-reindex').disabled = v.running;
      byId('ag-knowledge-reindex-all').disabled = v.running;
      byId('ag-reindex-cancel').disabled = !v.running;
      return v.running;
    }
    async function pollReindex() {
      try {
        var res = await fetchAuth('/api/admin/admissions/knowledge/reindex/status');
        if (!res.ok) throw new Error(res.status === 403 ? '관리자 권한이 필요합니다.' : '상태 조회 실패 (' + res.status + ')');
        var running = renderReindex(await res.json());
        pollTimer = running ? setTimeout(pollReindex, 2000) : null;
      } catch (e) { byId('ag-reindex-status').textContent = '상태 조회 실패: ' + e.message; pollTimer = null; }
    }
    function startPolling() { if (pollTimer) clearTimeout(pollTimer); pollReindex(); }

    byId('ag-knowledge-reindex').addEventListener('click', async function () {
      if (!window.confirm('ACTIVE 항목 중 임베딩이 없는 것만 백그라운드로 재색인합니다(이미 완료된 항목은 건드리지 않음). 계속할까요?')) return;
      try {
        var r = await post('/api/admin/admissions/knowledge/reindex', { mode: 'MISSING' });
        show(r.status === 'NOTHING_TO_DO' ? '재색인할 항목이 없습니다(모두 완료).' : '재색인을 시작했습니다. 아래 진행 상황을 확인하세요.', false);
        renderReindex(r);
        startPolling();
      } catch (e) {
        show('재색인 실패: ' + e.message, true);
        startPolling();
      }
    });

    byId('ag-knowledge-reindex-all').addEventListener('click', async function () {
      if (!window.confirm('ACTIVE 항목 전체를 다시 임베딩합니다. 자료를 다시 적재해 내용이 바뀐 항목이 있을 때 사용하세요(시간이 더 걸립니다). 계속할까요?')) return;
      try {
        var r = await post('/api/admin/admissions/knowledge/reindex', { mode: 'ALL' });
        show(r.status === 'NOTHING_TO_DO' ? '재색인할 항목이 없습니다.' : '전체 재색인을 시작했습니다. 아래 진행 상황을 확인하세요.', false);
        renderReindex(r);
        startPolling();
      } catch (e) {
        show('재색인 실패: ' + e.message, true);
        startPolling();
      }
    });

    byId('ag-reindex-cancel').addEventListener('click', async function () {
      try { await post('/api/admin/admissions/knowledge/reindex/cancel', {}); show('중단을 요청했습니다(현재 항목까지 마치고 멈춥니다).', false); startPolling(); }
      catch (e) { show('중단 요청 실패: ' + e.message, true); }
    });

    byId('ag-reindex-refresh').addEventListener('click', startPolling);
    startPolling();

    byId('ag-index-reload').addEventListener('click', async function () {
      try {
        var r = await post('/api/admin/admissions/index/reload', {});
        show('검색 인덱스 갱신 — 크기 ' + r.size + ' · 준비 ' + r.ready, false);
      } catch (e) { show('갱신 실패: ' + e.message, true); }
    });
  });
})(typeof window !== 'undefined' ? window : globalThis);
