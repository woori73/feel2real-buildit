/* 제작 공장(/dashboard/factory): 제작 작업 대기열을 공정 6단계 현황판으로 보여 주고, 제작 요청·게시 승인을 한다. Claude는 서버가 부르지 않는다 — 붙여 넣을 문장만 건넨다. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const API = '/api/admin/coord/factory';
  const quiet = { redirectOnAuthError: false };
  let board = { jobs: [], stages: [], options: null }, stage = '', openId = '';
  function msg(t, ok, id) { const m = $(id || 'msg'); m.textContent = t || ''; m.className = 'pp-msg' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }
  const esc = WS.esc;

  function row(j) {
    return '<tr><td class="pp-inline"><span class="pp-badge">' + esc(j.trackLabel) + '</span></td>'
      + '<td><button type="button" class="pf-link pp-title" data-open="' + esc(j.jobId) + '">' + esc(j.title.replace(/^제작 작업 · /, '')) + '</button>'
      + '<span class="pp-sub">' + [j.ranges ? '범위 ' + esc(j.ranges) : '', (j.itemLabels || []).map(esc).join(' / ')].filter(Boolean).join(' | ') + '</span></td>'
      + '<td data-label="">' + PF.track(j) + '<span class="pp-sub">' + esc(j.stateLabel) + '</span></td>'
      + '<td class="pp-inline" data-label="검수">' + PF.reviewBadge(j) + '</td>'
      + '<td class="pp-inline" data-label="맡은 세션">' + esc(j.makerSession ? j.makerSession + (j.worker ? ' · ' + j.worker : '') : '-') + '</td>'
      + '<td class="pp-inline pf-nowrap" data-label="맡긴 날">' + WS.day(j.requestedAt) + '</td>'
      + '<td><div class="pp-cell-actions"><button type="button" class="pp-btn small" data-open="' + esc(j.jobId) + '">자세히</button></div></td></tr>';
  }

  function render() {
    board.stages.forEach(s => {
      const b = document.querySelector('[data-stage="' + s.code + '"]');
      if (b) { b.querySelector('b').textContent = s.count; b.setAttribute('aria-pressed', String(stage === s.code)); }
    });
    const shown = PF.filter(board.jobs, { stage, track: $('track').value, word: $('search').value, showClosed: $('showClosed').checked });
    $('list').innerHTML = shown.map(row).join('');
    $('empty').hidden = shown.length > 0;
    $('empty').textContent = board.jobs.length ? '조건에 맞는 작업이 없습니다.' : '제작 작업이 없습니다. 「제작 요청」으로 시작해 보세요.';
    $('list').querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', () => detail(b.dataset.open)));
  }

  const line = (k, v) => v ? '<div class="pf-kv"><dt>' + k + '</dt><dd>' + v + '</dd></div>' : '';

  function detail(id) {
    const j = board.jobs.find(x => x.jobId === id);
    if (!j) return;
    openId = id;
    $('detailTitle').textContent = j.title.replace(/^제작 작업 · /, '');
    const reviews = (j.reviews || []).map(r => '<li class="' + (r.counted ? '' : 'pf-dim') + '"><span class="pp-badge ' + (r.verdict === 'PASS' ? 'DONE' : 'DRAFT') + '">' + (r.verdict === 'PASS' ? '통과' : '고칠 점') + '</span> <b>'
      + esc(r.reviewer) + '</b> <span class="pp-muted">' + esc(r.at.replace('T', ' ')) + '</span>' + (r.notes ? '<p>' + esc(r.notes) + '</p>' : '') + '</li>').join('');
    const progress = (j.progress || []).map(p => '<li><b>' + esc(p.stageLabel) + '</b> <span class="pp-muted">' + esc(p.at.replace('T', ' ')) + '</span>' + (p.note ? '<p>' + esc(p.note) + '</p>' : '') + '</li>').join('');
    const prompts = (j.prompts || []).map((p, i) => '<div class="pf-prompt"><span class="pp-label">' + esc(p.label) + '</span><code id="prompt' + i + '">' + esc(p.text) + '</code>'
      + '<button type="button" class="pp-btn small" data-copy="prompt' + i + '">복사</button></div>').join('');
    const pub = j.publish || {};
    let publish = '';
    if (j.stage === 'PUBLISH') {
      publish = '<h3>게시</h3><p>' + esc(j.stateLabel) + (pub.url ? ' — <a href="' + esc(pub.url) + '">' + esc(pub.url) + '</a>' : '') + '</p>'
        + (j.publishBlock ? '<p class="pp-muted">' + esc(j.publishBlock) + '</p>' : '')
        + (j.canApprove ? '<button type="button" class="pp-btn primary" data-act="approve">게시 승인</button> <span class="pp-muted">승인하면 학생 풀이 페이지 탑재를 맡깁니다.</span>' : '')
        + (j.canUpload ? '<p class="pp-label">① 화면에서 바로 올리기 — 그림 없는 내신(영어) 5지선다</p><div class="pf-publish"><input class="pp-input" type="file" id="pubFile" accept=".json,application/json" aria-label="게시용 파일">'
          + '<button type="button" class="pp-btn primary" data-act="upload">학생 페이지에 올리기</button></div>'
          + '<p class="pp-muted">Claude 세션이 만든 게시용 파일(publish.json)을 고릅니다. 형식이 틀리면 올리지 않고 틀린 곳을 알려 줍니다.</p>' : '')
        + (j.canMarkPublished ? '<p class="pp-label">' + (j.canUpload ? '② ' : '') + '개발 PR로 올린 뒤 표시하기</p>' : '')
        + (j.canMarkPublished ? '<div class="pf-publish"><input class="pp-input" id="pubUrl" placeholder="학생 풀이 페이지 주소 (예: /naesintest/school/singu-m2)" aria-label="학생 풀이 페이지 주소">'
          + '<button type="button" class="pp-btn primary" data-act="done">게시됨으로 표시</button></div>' : '')
        + (j.canWithdraw ? ' <button type="button" class="pp-btn small danger" data-act="withdraw">게시 내림</button>' : '');
    }
    $('detailBody').innerHTML = PF.track(j) + '<p class="pf-state">' + esc(j.stageNo ? '①②③④⑤⑥'[j.stageNo - 1] + ' ' + j.stageLabel + ' · ' : '') + esc(j.stateLabel) + '</p>'
      + '<dl class="pf-dl">' + line('과목·시험 구분', esc(j.trackLabel)) + line('시험', esc(j.exam)) + line('자료 종류', esc(j.typeLabel) + (j.count ? ' ' + j.count + '문항' : ''))
      + line('시험 범위', esc(j.ranges)) + line('문항 표시', (j.itemLabels || []).map(esc).join(' / ')) + line('기출 자료', esc(j.pastExamRef)) + line('시험범위 자료', esc(j.scopeRef))
      + line('원장님 지시', esc(j.instructions)) + line('맡은 세션', esc(j.makerSession ? j.makerSession + (j.worker ? ' · ' + j.worker : '') : ''))
      + line('맡긴 때', esc(j.requestedAt.replace('T', ' '))) + '</dl>'
      + (prompts ? '<h3>Claude 세션에 붙여 넣기</h3>' + prompts : '')
      + (j.reviewed ? '<h3>검수 ' + j.reviewsPassed + '/' + j.reviewsRequired + '</h3>' + (reviews ? '<ul class="pf-log">' + reviews + '</ul>' : '<p class="pp-muted">아직 검수 기록이 없습니다.</p>') : '')
      + (progress ? '<h3>제작 세션 보고</h3><ul class="pf-log">' + progress + '</ul>' : '')
      + (j.result ? '<h3>올린 자료</h3><dl class="pf-dl">' + line('결과', esc(j.result.summary)) + line('저장 위치', esc(j.result.wikiRef)) + line('검수한 세션', (j.result.reviewedBy || []).map(esc).join(', ')) + '</dl>' : '')
      + (j.error ? '<h3>실패 이유</h3><p>' + esc(j.error) + '</p>' : '')
      + publish
      + '<div class="pf-foot"><button type="button" class="pp-btn small" data-act="brief">제작 지시서 보기</button>'
      + (j.canCancel ? '<button type="button" class="pp-btn small danger" data-act="cancel">요청 취소</button>' : '') + '</div><pre class="pf-brief" id="briefBox" hidden></pre>';
    $('detail').hidden = false;
    $('detailBody').querySelectorAll('[data-copy]').forEach(b => b.addEventListener('click', () => copy(b)));
    $('detailBody').querySelectorAll('[data-act]').forEach(b => b.addEventListener('click', () => act(b, j)));
  }

  async function copy(b) {
    const text = $(b.dataset.copy).textContent;
    try { await navigator.clipboard.writeText(text); b.textContent = '복사됨'; }
    catch (e) { const r = document.createRange(); r.selectNodeContents($(b.dataset.copy)); const s = getSelection(); s.removeAllRanges(); s.addRange(r); b.textContent = '선택됨 — 길게 눌러 복사'; }
  }

  async function act(b, j) {
    const a = b.dataset.act, base = API + '/' + encodeURIComponent(j.jobId);
    try {
      if (a === 'brief') {
        const box = $('briefBox');
        if (box.hidden) { box.textContent = (await WS.json(base + '/brief', quiet)).briefMarkdown; }
        box.hidden = !box.hidden;
        return;
      }
      if (a === 'cancel' && !confirm('이 제작 요청을 취소할까요? 취소한 요청은 되살릴 수 없고, 기록은 남습니다.')) return;
      if (a === 'approve' && !confirm('검수 ' + j.reviewsPassed + '곳을 통과한 자료입니다. 학생 풀이 페이지에 게시하도록 승인할까요?')) return;
      if (a === 'withdraw' && !confirm(j.publish && j.publish.examCode ? '게시를 내릴까요? 화면에서 올린 회차(' + j.publish.examCode + ')는 학생 페이지에서도 내려갑니다.' : '게시 승인을 되돌릴까요? PR로 학생 페이지에 올린 자료는 따로 내려야 합니다.')) return;
      b.disabled = true;
      let updated;
      if (a === 'cancel') updated = await WS.json(base + '/cancel', Object.assign({ method: 'POST' }, quiet));
      if (a === 'approve') updated = await WS.json(base + '/publish/approve', Object.assign({ method: 'POST' }, quiet));
      if (a === 'done') updated = await WS.json(base + '/publish/done', Object.assign(WS.body('POST', { url: $('pubUrl').value.trim() }), quiet));
      if (a === 'withdraw') updated = await WS.json(base + '/publish/withdraw', Object.assign(WS.body('POST', {}), quiet));
      if (a === 'upload') {
        const file = $('pubFile').files[0];
        if (!file) throw new Error('게시용 파일을 골라 주세요.');
        const pack = PF.readPack(await file.text());
        if (!confirm(pack.title + ' · ' + pack.questions.length + '문항을 학생 풀이 페이지에 올릴까요?')) { b.disabled = false; return; }
        updated = await WS.json(base + '/publish/upload', Object.assign(WS.body('POST', pack), quiet));
      }
      await load();
      if (updated) detail(updated.jobId);
      msg({ cancel: '요청을 취소했습니다.', approve: '게시를 승인했습니다. 탑재가 끝나면 「게시됨으로 표시」해 주세요.', done: '게시됨으로 표시했습니다.', upload: '학생 풀이 페이지에 올렸습니다.', withdraw: '게시 승인을 되돌렸습니다.' }[a], true);
    } catch (e) { b.disabled = false; alert(e.message); }
  }

  /* ---------- 제작 요청 ---------- */
  const chosen = (boxId) => Array.from($(boxId).querySelectorAll('[aria-pressed="true"],[aria-checked="true"]')).map(b => b.dataset.code);

  function syncForm() {
    const track = chosen('fTrack')[0] || '';
    const school = !track || (board.options.schoolTracks || []).includes(track);
    $('schoolFields').hidden = !school;
    const r = PF.parseRanges($('fRanges').value);
    $('rangeHint').textContent = r.error ? r.error : r.lines.length ? '구간 ' + r.lines.length + '개 · 모두 ' + PF.pageCount(r.lines) + '쪽 — ' + r.lines.join(', ')
      : '시작·끝 쪽만 적으면 빠진 쪽에서 문제가 나옵니다. 구간을 나눠 적어 주세요.';
    $('rangeHint').className = r.error ? 'pf-error' : 'pp-muted';
  }

  function buildForm() {
    const o = board.options;
    $('fTrack').innerHTML = o.tracks.map((t, i) => '<button type="button" class="pp-chip" role="radio" data-code="' + t.code + '" aria-checked="' + (i === 0) + '">' + esc(t.label) + '</button>').join('');
    $('fLabels').innerHTML = o.itemLabels.map(t => '<button type="button" class="pp-chip" data-code="' + t.code + '" aria-pressed="false">' + esc(t.label) + '</button>').join('');
    o.types.forEach(t => $('fType').add(new Option(t.label, t.code)));
    o.workers.forEach(t => $('fWorker').add(new Option(t.label, t.code)));
    o.tracks.forEach(t => $('track').add(new Option(t.label, t.code)));
    $('fYear').value = new Date().getFullYear();
    $('fTrack').querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      $('fTrack').querySelectorAll('button').forEach(x => x.setAttribute('aria-checked', String(x === b)));
      syncForm();
    }));
    $('fLabels').querySelectorAll('button').forEach(b => b.addEventListener('click', () => b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true'))));
    $('fRanges').addEventListener('input', syncForm);
  }

  let requestId = '';
  async function submit(ev) {
    ev.preventDefault();
    msg('', false, 'formMsg');
    const r = PF.parseRanges($('fRanges').value);
    if (r.error) { msg('시험 범위: ' + r.error, false, 'formMsg'); return; }
    const track = chosen('fTrack')[0];
    const school = (board.options.schoolTracks || []).includes(track);
    const body = PF.payload({ track, type: $('fType').value, worker: $('fWorker').value, ranges: $('fRanges').value, labels: chosen('fLabels'),
      school: school ? $('fSchool').value : '', grade: school ? $('fGrade').value : '', year: $('fYear').value, semester: $('fSemester').value, exam: $('fExam').value,
      past: $('fPast').value, scope: $('fScope').value, note: $('fNote').value, count: $('fCount').value }, requestId);
    $('submitBtn').disabled = true;
    try {
      const made = await WS.json(API, Object.assign(WS.body('POST', body), quiet));
      $('form').hidden = true;
      $('requestForm').reset();
      $('fYear').value = new Date().getFullYear();
      await load();
      msg('대기열에 올렸습니다. 아래 문장을 Claude 세션에 붙여 넣으면 제작을 시작합니다.', true);
      detail(made.jobId);
    } catch (e) { msg(e.message, false, 'formMsg'); }
    finally { $('submitBtn').disabled = false; }
  }

  async function load() {
    const first = !board.options;
    board = await WS.json(API, quiet);
    if (first) buildForm();
    render();
  }

  async function boot() {
    $('newBtn').addEventListener('click', () => { requestId = PF.requestId(); msg('', false, 'formMsg'); syncForm(); $('form').hidden = false; });
    $('formClose').addEventListener('click', () => { $('form').hidden = true; });
    $('detailClose').addEventListener('click', () => { $('detail').hidden = true; openId = ''; });
    $('requestForm').addEventListener('submit', submit);
    $('reloadBtn').addEventListener('click', () => load().then(() => { if (openId && !$('detail').hidden) detail(openId); }).catch(e => msg(e.message)));
    ['track', 'showClosed'].forEach(id => $(id).addEventListener('change', render));
    $('search').addEventListener('input', render);
    document.querySelectorAll('[data-stage]').forEach(b => b.addEventListener('click', () => { stage = stage === b.dataset.stage ? '' : b.dataset.stage; render(); }));
    try { await load(); }
    catch (e) {
      $('newBtn').disabled = true;
      msg(e.status === 403 ? '제작 공장은 원장(관리자) 계정에서만 쓸 수 있습니다. 제작 작업 대기열이 원장 계정의 Claude 연결로 움직이기 때문입니다.' : e.message);
    }
  }

  (window.ensureCurrentUser ? window.ensureCurrentUser() : Promise.resolve(null)).then(u => {
    if (u && $('userName')) $('userName').textContent = (window.userDisplayName ? window.userDisplayName(u) : '') || '';
    boot();
  });
})();
