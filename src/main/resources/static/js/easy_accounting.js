(function () {
  async function init() {
    await Easy.guardAdmin();
    const businesses = await Easy.loadBusinesses();
    businesses.forEach(b => document.getElementById('business').add(new Option(b.name, b.businessId)));
    document.getElementById('year').value = new Date().getFullYear();
    document.getElementById('search').addEventListener('click', load);
    if (businesses.length) await load();
  }
  async function load() {
    const businessId = document.getElementById('business').value;
    const year = document.getElementById('year').value;
    const q = new URLSearchParams({ businessId, from: year + '-01-01', to: year + '-12-31' });
    const [trialRes, auditRes] = await Promise.all([
      fetchAuth('/api/admin/easy/accounting/trial-balance?' + q),
      fetchAuth('/api/admin/easy/accounting/balance-audit?' + q)
    ]);
    if (!trialRes.ok || !auditRes.ok) throw new Error('회계장부 조회 실패');
    const trial = await trialRes.json(); const audit = await auditRes.json();
    document.getElementById('audit').textContent = JSON.stringify(audit);
    const rows = trial.rows || trial.accounts || [];
    const ul = document.getElementById('trial'); ul.innerHTML = '';
    if (!rows.length) { ul.innerHTML = '<li class="empty">POSTED 분개가 없습니다.</li>'; return; }
    rows.forEach(row => { const li = document.createElement('li'); li.className = 'item';
      li.textContent = (row.accountName || row.name || '계정') + ' · 차변 '
        + Easy.fmtWon(row.debit || row.debitTotal) + ' · 대변 ' + Easy.fmtWon(row.credit || row.creditTotal);
      ul.appendChild(li); });
  }
  document.addEventListener('DOMContentLoaded', () => init().catch(console.error));
})();
