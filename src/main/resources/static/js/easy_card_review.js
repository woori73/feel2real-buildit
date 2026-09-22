/**
 * /easy/card-review — 카드 이벤트 검토(승인/거부). 승인 시 계정을 확정해 장부에 반영한다.
 */
(function () {
  let businesses = [];
  let accounts = [];

  async function init() {
    await Easy.guardAdmin();
    businesses = await Easy.loadBusinesses();
    accounts = await loadAccounts();
    fillBusinessSelect(document.getElementById('qBusiness'));
    document.getElementById('btnRefresh').addEventListener('click', load);
    if (businesses.length) {
      document.getElementById('qBusiness').addEventListener('change', load);
      await load();
    }
  }

  async function loadAccounts() {
    try {
      const res = await fetchAuth('/api/admin/easy/accounts');
      if (!res.ok) return [];
      return res.json();
    } catch (_) {
      return [];
    }
  }

  function fillBusinessSelect(select) {
    select.innerHTML = '';
    businesses.forEach(b => {
      const opt = document.createElement('option');
      opt.value = String(b.businessId);
      opt.textContent = b.name;
      select.appendChild(opt);
    });
  }

  function currentBusinessId() {
    return document.getElementById('qBusiness').value;
  }

  async function load() {
    Easy.hideMsg('listError');
    Easy.hideMsg('listOk');
    const businessId = currentBusinessId();
    if (!businessId) return;
    const res = await fetchAuth('/api/admin/easy/card-events?businessId=' + encodeURIComponent(businessId));
    if (!res.ok) {
      Easy.showError('listError', '검토 목록을 불러오지 못했습니다.');
      return;
    }
    render(await res.json());
  }

  function render(items) {
    const ul = document.getElementById('reviewList');
    ul.innerHTML = '';
    if (!items.length) {
      ul.innerHTML = '<li class="empty">검토 대기 중인 카드 이벤트가 없습니다.</li>';
      return;
    }
    items.forEach(item => ul.appendChild(renderItem(item)));
  }

  function renderItem(item) {
    const li = document.createElement('li');
    li.className = 'item';

    const top = document.createElement('div');
    top.className = 'row';
    const title = document.createElement('span');
    title.className = 't';
    title.textContent = (item.instrumentIssuer || '카드') + ' ****' + (item.instrumentLast4 || '');
    const amt = document.createElement('span');
    amt.className = 'amt out';
    amt.textContent = Easy.fmtWon(item.amount);
    top.appendChild(title);
    top.appendChild(amt);

    const sub = document.createElement('div');
    sub.className = 'row';
    const s = document.createElement('span');
    s.className = 's';
    s.textContent = [item.occurredAt, item.eventType, item.providerExternalId]
      .filter(Boolean).join(' · ');
    sub.appendChild(s);

    const form = document.createElement('div');
    form.className = 'row';
    const select = document.createElement('select');
    const none = document.createElement('option');
    none.value = '';
    none.textContent = '계정 선택';
    select.appendChild(none);
    accounts.filter(a => a.direction === 'EXPENSE').forEach(a => {
      const opt = document.createElement('option');
      opt.value = String(a.accountId);
      opt.textContent = a.name;
      select.appendChild(opt);
    });
    if (item.accountId) select.value = String(item.accountId);

    const approve = document.createElement('button');
    approve.type = 'button';
    approve.className = 'btn-primary';
    approve.textContent = '승인';
    approve.addEventListener('click', () => approveEvent(item, select.value));

    const reject = document.createElement('button');
    reject.type = 'button';
    reject.className = 'btn-danger-link';
    reject.textContent = '거부';
    reject.addEventListener('click', () => rejectEvent(item));

    form.appendChild(select);
    form.appendChild(approve);
    form.appendChild(reject);

    li.appendChild(top);
    li.appendChild(sub);
    li.appendChild(form);
    return li;
  }

  async function approveEvent(item, accountId) {
    if (!accountId) {
      Easy.showError('listError', '계정을 선택해 주세요.');
      return;
    }
    const res = await fetchAuth('/api/admin/easy/card-events/' + item.cardEventId + '/approve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ businessId: Number(currentBusinessId()), accountId: Number(accountId) })
    });
    if (!res.ok) {
      Easy.showError('listError', await res.text() || '승인에 실패했습니다.');
      return;
    }
    Easy.showOk('listOk', '승인 후 장부에 반영했습니다.');
    await load();
  }

  async function rejectEvent(item) {
    if (!window.confirm('이 카드 이벤트를 거부할까요?')) return;
    const res = await fetchAuth('/api/admin/easy/card-events/' + item.cardEventId + '/reject', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ businessId: Number(currentBusinessId()) })
    });
    if (!res.ok) {
      Easy.showError('listError', await res.text() || '거부에 실패했습니다.');
      return;
    }
    Easy.showOk('listOk', '거부했습니다.');
    await load();
  }

  document.addEventListener('DOMContentLoaded', () => {
    init().catch(err => console.error(err));
  });
})();
