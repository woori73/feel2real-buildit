/**
 * AI 코디네이터 승인 화면. AI(MCP)가 만든 출결 초안을 보고 [승인]하면 F2R 출결부에 기록된다. 본인 요청만 보인다.
 * ?id=<jobId>가 있으면 그 요청을 위에 크게 보여 준다(AI가 알려 준 승인 링크).
 */
(function () {
  const V = window.CoordView;
  const API = '/api/admin/coord/jobs';
  const $ = id => document.getElementById(id);

  function esc(s) {
    return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  async function readJson(res) {
    try { return await res.json(); } catch (e) { return {}; }
  }

  function card(job, big) {
    const decide = V.canDecide(job);
    return `
      <article class="coord-card${big ? ' big' : ''}" data-job="${esc(job.jobId)}">
        <div class="coord-status s-${esc(job.status)}">${esc(V.statusLabel(job.status))}</div>
        <h2>${esc(V.headline(job))}</h2>
        <p class="coord-detail">${esc(V.detailLine(job))}</p>
        ${job.summary && job.summary.businessName ? `<p class="coord-meta">사업체: ${esc(job.summary.businessName)}</p>` : ''}
        <p class="coord-meta">요청 ${esc(V.when(job.createdAt))}${decide ? ' · 승인 마감 ' + esc(V.when(job.expiresAt)) : ''}</p>
        ${decide ? `<div class="coord-actions">
            <button type="button" class="coord-approve" data-approve="${esc(job.jobId)}" data-hash="${esc(job.planHash)}">승인하고 기록</button>
            <button type="button" class="coord-reject" data-reject="${esc(job.jobId)}">거절</button>
          </div>` : `<p class="coord-result">${esc(V.resultText(job))}</p>`}
        <p class="coord-error" hidden></p>
      </article>`;
  }

  async function load() {
    const focus = new URLSearchParams(location.search).get('id');
    const list = $('coordList');
    try {
      const res = await fetchAuth(API);
      if (!res.ok) throw new Error();
      const jobs = await res.json();
      const top = focus ? jobs.find(j => j.jobId === focus) : null;
      $('coordFocus').innerHTML = top ? card(top, true) : (focus ? '<p class="coord-empty">요청을 찾지 못했습니다. 본인이 만든 요청만 보입니다.</p>' : '');
      const rest = jobs.filter(j => !top || j.jobId !== top.jobId);
      list.innerHTML = rest.length ? rest.map(j => card(j, false)).join('') : '<p class="coord-empty">다른 요청이 없습니다.</p>';
    } catch (e) {
      list.innerHTML = '<p class="coord-empty">요청 목록을 불러오지 못했습니다. 새로고침해 주세요.</p>';
    }
  }

  async function decide(btn, path, body) {
    const article = btn.closest('[data-job]');
    article.querySelectorAll('button').forEach(b => { b.disabled = true; });
    const res = await fetchAuth(path, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {})
    });
    if (!res.ok) {
      const err = article.querySelector('.coord-error');
      err.textContent = (await readJson(res)).message || '처리하지 못했습니다. 새로고침 후 다시 시도해 주세요.';
      err.hidden = false;
      article.querySelectorAll('button').forEach(b => { b.disabled = false; });
      return;
    }
    await load();
  }

  document.addEventListener('click', e => {
    const a = e.target.closest('[data-approve]');
    if (a) { decide(a, API + '/' + encodeURIComponent(a.dataset.approve) + '/approve', { planHash: a.dataset.hash }); return; }
    const r = e.target.closest('[data-reject]');
    if (r) decide(r, API + '/' + encodeURIComponent(r.dataset.reject) + '/reject');
  });

  (async () => {
    const user = await ensureCurrentUser();
    if (!user || user.role !== 'ADMIN') { location.href = '/?login=required&returnUrl=' + encodeURIComponent(location.pathname + location.search); return; }
    $('coordRefresh').addEventListener('click', load);
    load();
  })();
})();
