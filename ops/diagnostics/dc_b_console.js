// DesignCraft B안 통합 테스트 (ADMIN 브라우저 콘솔용). 재시도 없음, 합성 작업 1건만 다룬다.
// 순서: 사전조건 → resume → 작업 생성 → RUNNING 대기 → 원고 → PNG → PDF export → DONE·산출물 확인 → 다운로드
//       → [finally] 내가 만든 작업만 정리(취소는 QUEUED/RUNNING일 때만, 그 뒤 삭제) → drain → CLOSED·0·0 확인.
// 정리를 drain보다 먼저 하는 이유: drain 이후에는 /api/publishing/** 가 503으로 막힌다.
async function dcSynthTest(fetchFn, opt = {}) {
  const H = { 'X-DesignCraft-Maintenance': '1' };
  const TITLE = 'SYNTH-DC-TEST-' + (opt.suffix || Date.now());
  const deadline = Date.now() + (opt.totalMs ?? 300000);
  const tick = opt.tickMs ?? 3000;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const out = { title: TITLE, steps: [], ok: false };
  const L = (k, v) => { out.steps.push([k, v]); console.log(k, JSON.stringify(v)); };
  const J = async (r) => ({ http: r.status, body: await r.json().catch(() => null) });
  const must = (cond, msg) => { if (!cond) throw new Error(msg); };
  const timeLeft = () => must(Date.now() < deadline, 'global deadline exceeded');
  const status = () => fetchFn('/api/admin/publishing/maintenance').then(J);
  let jobId = null, resumed = false;
  try {
    must(opt.confirmWatchdog === true, '서버 감시기(watchdog) 가동 확인 전에는 시작하지 않음');
    const s0 = await status(); L('0 status before', s0);
    must(s0.http === 200 && s0.body.state === 'CLOSED' && s0.body.activeOperations === 0 && s0.body.liveCliProcesses === 0, '사전조건 불충족(CLOSED/0/0 아님)');

    const r = await fetchFn('/api/admin/publishing/maintenance/resume', { method: 'POST', headers: H }).then(J); L('1 resume', r);
    must(r.http === 200 && r.body && r.body.state === 'OPEN', 'resume 실패'); resumed = true;

    const c = await fetchFn('/api/publishing/jobs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: TITLE, template: 'ko-textbook-a4' }) }).then(J); L('2 create', c);
    must(c.http === 202 && c.body && c.body.jobId && c.body.title === TITLE && c.body.status === 'QUEUED', '작업 생성 응답 이상');
    jobId = c.body.jobId;

    for (;;) {                       // QUEUED → RUNNING (Worker 폴링 10초, 서버가 시도 3회로 제한)
      await sleep(tick); timeLeft();
      const j = await fetchFn('/api/publishing/jobs/' + jobId).then(J); L('3 poll', [j.http, j.body && j.body.status, j.body && j.body.currentStep, j.body && j.body.error]);
      must(j.http === 200 && j.body, '상태 조회 실패');
      if (j.body.status === 'RUNNING') break;
      must(j.body.status === 'QUEUED', '예상 밖 상태: ' + j.body.status);
    }

    const fd = new FormData();
    fd.append('manuscript', new Blob(['# 합성 테스트\n\n한글과 English가 섞인 짧은 합성 문장입니다. (개인정보 없음)\n\n## 1절\n\n두 번째 문단입니다.\n'], { type: 'text/markdown' }), 'synthetic.md');
    const m = await fetchFn(`/api/publishing/jobs/${jobId}/manuscript`, { method: 'POST', body: fd }).then(J); L('4 manuscript', m);
    must(m.http === 200 && m.body && m.body.applied === true && m.body.pageCount >= 1, '원고 반입 실패');

    timeLeft();
    const p = await fetchFn(`/api/publishing/jobs/${jobId}/pages/1/png`); const pb = await p.blob(); const ct = p.headers.get('content-type') || '';
    L('5 png', [p.status, ct, pb.size]); must(p.status === 200 && /image\/png/.test(ct) && pb.size > 1000, 'PNG 미리보기 실패');

    timeLeft();
    const e = await fetchFn(`/api/publishing/jobs/${jobId}/export`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ format: 'pdf', standard: 'x4' }) }).then(J); L('6 export', e);
    must(e.http === 202 && e.body && e.body.artifactId && e.body.format === 'pdf' && e.body.fileSize > 0, 'PDF export 실패(재시도하지 않음)');
    const artifactId = e.body.artifactId;

    // export는 동기 처리: 202 응답 시점에 산출물이 등록되고 작업은 DONE이 된다. 그래도 직접 확인한 뒤에만 다운로드한다.
    const jd = await fetchFn('/api/publishing/jobs/' + jobId).then(J); L('7 job after export', [jd.http, jd.body && jd.body.status]);
    must(jd.http === 200 && jd.body.status === 'DONE', 'export 후 작업이 DONE이 아님');
    const al = await fetchFn(`/api/publishing/jobs/${jobId}/artifacts`).then(J); L('8 artifacts', al);
    const art = al.body && al.body.artifacts && al.body.artifacts.find((x) => x.artifactId === artifactId);
    must(al.http === 200 && art && art.fileSize === e.body.fileSize, '산출물 목록에서 확인되지 않음');

    timeLeft();
    const dl = await fetchFn(`/api/publishing/jobs/${jobId}/artifacts/${artifactId}`); const buf = await dl.arrayBuffer();
    const head = new TextDecoder().decode(buf.slice(0, 5)); const dct = dl.headers.get('content-type') || '';
    L('9 pdf download', [dl.status, dct, buf.byteLength, head]);
    must(dl.status === 200 && /application\/pdf/.test(dct) && head === '%PDF-' && buf.byteLength === art.fileSize, 'PDF 다운로드 검증 실패');
    out.ok = true;
  } catch (err) {
    L('STOP', String(err && err.message || err));
  } finally {
    // (a) 내가 만든 합성 작업만 정리. 제목·ID가 일치할 때만 건드린다. drain 전에 해야 한다(drain 후엔 503).
    if (jobId !== null && resumed) {
      try {
        const j = await fetchFn('/api/publishing/jobs/' + jobId).then(J);
        if (j.http === 200 && j.body && j.body.title === TITLE) {
          if (j.body.status === 'QUEUED' || j.body.status === 'RUNNING') L('cleanup cancel', await fetchFn(`/api/publishing/jobs/${jobId}/cancel`, { method: 'POST' }).then(J));
          const d = await fetchFn('/api/publishing/jobs/' + jobId, { method: 'DELETE' }); L('cleanup delete', d.status);
        } else { L('cleanup skipped', '제목 불일치 또는 조회 실패 — 아무것도 삭제하지 않음'); }
      } catch (e2) { L('cleanup error', String(e2)); }
    }
    // (b) 반드시 drain (최대 3회 시도) → CLOSED·0·0 확인
    if (resumed) {
      for (let i = 0; i < 3; i++) {
        try { const d = await fetchFn('/api/admin/publishing/maintenance/drain', { method: 'POST', headers: H }).then(J); L('drain#' + (i + 1), d); if (d.http === 200) break; } catch (e3) { L('drain error', String(e3)); }
        await sleep(2000);
      }
      let s = null;
      for (let i = 0; i < 20; i++) { try { s = await status(); } catch (e4) { s = { error: String(e4) }; } if (s.body && s.body.state === 'CLOSED' && s.body.activeOperations === 0 && s.body.liveCliProcesses === 0) break; await sleep(tick); }
      L('10 status after', s); out.finalClosed = !!(s && s.body && s.body.state === 'CLOSED' && s.body.activeOperations === 0 && s.body.liveCliProcesses === 0);
    }
    out.jobId = jobId;
    console.log('RESULT', JSON.stringify({ ok: out.ok, finalClosed: out.finalClosed, jobId, title: TITLE }));
  }
  return out;
}
if (typeof module !== 'undefined') module.exports = { dcSynthTest };
