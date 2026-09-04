/** 관리자 회원 전체 보기(/admin/members). 기존 회원 DB(users)를 그대로 읽는다. */
(function () {
  let currentUser = null;

  function fmtDate(v) {
    if (!v) return '';
    const d = new Date(v);
    if (isNaN(d)) return '';
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function escapeHtml(str) {
    return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  async function loadMembers() {
    const body = document.getElementById('memberBody');
    try {
      const res = await fetchAuth('/api/admin/members');
      if (!res.ok) throw new Error();
      const members = await res.json();
      document.getElementById('sectionSubtitle').textContent =
        `총 ${members.length}명. 기존 회원 DB 기준 · 가입일 내림차순.`;
      if (!members.length) {
        body.innerHTML = '<tr><td colspan="3" class="member-empty">아직 가입 회원이 없습니다.</td></tr>';
        return;
      }
      body.innerHTML = members.map(m => `
        <tr>
          <td>${escapeHtml(m.displayName)}<br><span class="login">${escapeHtml(m.loginId)}</span></td>
          <td><span class="member-role">${escapeHtml(m.role)}</span></td>
          <td>${fmtDate(m.joinedAt)}</td>
        </tr>`).join('');
    } catch {
      body.innerHTML = '<tr><td colspan="3" class="member-empty">회원 목록을 불러오지 못했습니다.</td></tr>';
    }
  }

  (async () => {
    currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/home'; return; }
    document.getElementById('userName').textContent = window.userDisplayName(currentUser);
    loadMembers();
  })();
})();
