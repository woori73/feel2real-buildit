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
                   types: types.length, schedules: schedules.length };
    var codes = {}, docKeys = {}, years = {};
    universities.forEach(function (u) { codes[u.university_code] = true; });
    documents.forEach(function (d) { docKeys[d.document_key] = true; years[d.applicable_academic_year] = true; });
    var badUniv = 0, badDoc = 0;
    [departments, types, schedules].forEach(function (rows) {
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

  /** 승격 확인 문자열. 서버 규칙(AdmissionsActivationService)과 같아야 한다. */
  function confirmationFor(year) { return 'ACTIVATE-ALL-' + year; }

  var api = { summarizeSeed: summarizeSeed, confirmationFor: confirmationFor };
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
  root.AdmissionsAdminLoad = api;

  if (typeof document === 'undefined') return;
  document.addEventListener('DOMContentLoaded', function () {
    var panel = document.getElementById('ag-load');
    if (!panel) return;
    function byId(id) { return document.getElementById(id); }
    var fileText = null, preview = null;
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
      var f = ev.target.files && ev.target.files[0];
      byId('ag-load-run').disabled = true; preview = null; fileText = null;
      if (!f) { show('', false); return; }
      var reader = new FileReader();
      reader.onload = function () {
        fileText = String(reader.result);
        preview = summarizeSeed(fileText);
        if (preview.error) { show('미리보기 실패: ' + preview.error, true); return; }
        var c = preview.counts;
        show('미리보기 — 대학 ' + c.universities + ' · 공식자료 ' + c.documents + ' · 학과 ' + c.departments + ' · 전형 ' + c.types +
             ' · 일정 ' + c.schedules + ' / 학년도 ' + preview.years.join(', ') +
             (preview.warnings.length ? '\n⚠ ' + preview.warnings.join('\n⚠ ') : '\n이상 없음. 적재는 DRAFT 상태로 들어가며 기존 자료는 중복 적재되지 않습니다.'), preview.warnings.length > 0 && !preview.ok);
        byId('ag-load-run').disabled = !preview.ok;
      };
      reader.readAsText(f, 'utf-8');
    });

    byId('ag-load-run').addEventListener('click', async function () {
      if (!preview || !preview.ok || !fileText) return;
      var c = preview.counts;
      if (!window.confirm('대학 ' + c.universities + ' · 공식자료 ' + c.documents + ' · 전형 ' + c.types + ' · 일정 ' + c.schedules +
          '건을 DRAFT로 적재합니다. DB 백업을 확인하셨습니까?')) return;
      var btn = byId('ag-load-run'); btn.disabled = true;
      try {
        var r = await post('/api/admin/admissions/load/seed', { seedJson: fileText });
        show('적재 결과 — 대학 ' + r.universities + ' · 공식자료 ' + r.materials + ' · 학과 ' + r.departments + ' · 전형 ' + r.types +
             ' · 일정 ' + r.schedules + ' (건너뜀 ' + r.skippedUnknownYear + ') · 상태 ' + r.status, false);
      } catch (e) { show('적재 실패: ' + e.message, true); }
      finally { btn.disabled = false; }
    });

    byId('ag-activate-run').addEventListener('click', async function () {
      var year = Number(byId('ag-activate-year').value);
      var typed = byId('ag-activate-confirm').value.trim();
      if (typed !== confirmationFor(year)) { show('확인 문자열이 맞지 않습니다. ' + confirmationFor(year) + ' 를 입력하세요.', true); return; }
      if (!window.confirm(year + '학년도 공식자료·학과·전형·일정(DRAFT·REVIEW)을 전체 ACTIVE로 승격합니다. 계속할까요?')) return;
      try {
        var r = await post('/api/admin/admissions/activate-all', { academicYear: year, confirm: typed, note: '관리자 화면 전체 승격' });
        show('승격 결과 — 공식자료 ' + r.materials + ' · 학과 ' + r.departments + ' · 전형 ' + r.types + ' · 일정 ' + r.schedules + ' · ' + r.status, false);
      } catch (e) { show('승격 실패: ' + e.message, true); }
    });

    byId('ag-index-reload').addEventListener('click', async function () {
      try {
        var r = await post('/api/admin/admissions/index/reload', {});
        show('검색 인덱스 갱신 — 크기 ' + r.size + ' · 준비 ' + r.ready, false);
      } catch (e) { show('갱신 실패: ' + e.message, true); }
    });
  });
})(typeof window !== 'undefined' ? window : globalThis);
