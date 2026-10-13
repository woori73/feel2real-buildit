(function () {
  let businesses = [];

  async function init() {
    await Easy.guardAdmin();
    businesses = await Easy.loadBusinesses();
    const select = document.getElementById('business');
    businesses.forEach(b => select.add(new Option(b.name, String(b.businessId))));
    if (!businesses.length) throw new Error('활성 사업체가 없습니다.');
    if (location.pathname.endsWith('/evidence')) {
      document.getElementById('pageTitle').textContent = '증빙 검토';
      document.getElementById('kind').value = 'EVIDENCE';
    }
    document.getElementById('search').addEventListener('click', load);
    await load();
  }

  async function load() {
    const businessId = document.getElementById('business').value;
    const kind = document.getElementById('kind').value;
    const params = new URLSearchParams({ businessId, status: 'OPEN' });
    if (kind) params.set('kind', kind);
    const res = await fetchAuth('/api/admin/easy/workspace/reviews?' + params);
    if (!res.ok) throw new Error(await res.text() || '검토함 조회 실패');
    render(await res.json(), businessId);
  }

  function render(items, businessId) {
    const ul = document.getElementById('reviews');
    ul.innerHTML = '';
    document.getElementById('count').textContent = items.length + '건';
    if (!items.length) {
      ul.innerHTML = '<li class="empty">확인할 항목이 없습니다.</li>';
      return;
    }
    items.forEach(item => {
      const li = document.createElement('li'); li.className = 'item';
      const title = document.createElement('div'); title.className = 'row';
      title.innerHTML = '<span class="t"></span><span class="badge"></span>';
      title.children[0].textContent = item.title || item.kindLabel;
      title.children[1].textContent = item.kindLabel || item.kind;
      const detail = document.createElement('div'); detail.className = 's';
      detail.textContent = [item.occurredOn, item.detail,
        item.amount == null ? null : Easy.fmtWon(item.amount)].filter(Boolean).join(' · ');
      const actions = document.createElement('div'); actions.className = 'btn-row';
      const approve = button('승인', 'btn-primary', () => approveItem(item, businessId));
      const hold = button('보류', 'btn-secondary', () => holdItem(item, businessId));
      const ignore = button('제외', 'btn-danger-link', () => approveItem(item, businessId, true));
      actions.append(approve, hold, ignore); li.append(title, detail, actions); ul.appendChild(li);
    });
  }

  function button(label, cls, action) {
    const b = document.createElement('button'); b.type = 'button'; b.className = cls;
    b.textContent = label; b.addEventListener('click', action); return b;
  }

  async function approveItem(item, businessId, ignore) {
    const body = { resolution: ignore ? '관리자 제외' : '관리자 확인', ignore: !!ignore };
    if (!ignore && item.kind === 'EVIDENCE') body.evidenceReview = 'VERIFIED';
    if (!ignore && item.kind === 'WITHHOLDING') body.withholdingReview = 'CHECKED';
    if (!ignore && item.kind === 'UNCLASSIFIED') {
      const raw = prompt('반영할 계정 ID를 입력하세요.');
      if (!raw) return;
      body.accountId = Number(raw);
    }
    const res = await fetchAuth('/api/admin/easy/workspace/reviews/'
      + encodeURIComponent(item.reviewId) + '/approve?businessId=' + encodeURIComponent(businessId), {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
    if (!res.ok) { alert(res.status === 409 ? '이미 처리되었거나 마감된 기간입니다.' : await res.text()); return; }
    await load();
  }

  async function holdItem(item, businessId) {
    const res = await fetchAuth('/api/admin/easy/workspace/reviews/'
      + encodeURIComponent(item.reviewId) + '/hold?businessId=' + encodeURIComponent(businessId), {
      method: 'POST'
    });
    if (!res.ok) {
      alert(res.status === 409 ? '이미 처리된 검토입니다.' : await res.text());
      return;
    }
    alert('보류했습니다. 검토 항목과 장부 데이터는 변경되지 않았습니다.');
    await load();
  }

  document.addEventListener('DOMContentLoaded', () => init().catch(console.error));
})();
