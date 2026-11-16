/**
 * 관리자 회원 전체 보기(/admin/members). 기존 회원 DB(users)를 그대로 읽는다.
 * 각 줄의 「임시 비밀번호 발급」: 확인 창(닉네임·아이디·가입일·구분) → 발급 → 비밀번호를 한 번만 보여 주고 복사 버튼.
 * 창을 닫으면 다시 볼 수 없다(다시 필요하면 재발급). 표시 변환은 admin_members_view.js(AdminMembersView).
 */
(function () {
  const V = window.AdminMembersView;
  let currentUser = null;
  let members = [];
  let pending = null;

  function escapeHtml(str) {
    return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function $(id) { return document.getElementById(id); }

  async function loadMembers() {
    const body = $('memberBody');
    try {
      const res = await fetchAuth('/api/admin/members');
      if (!res.ok) throw new Error();
      members = await res.json();
      $('sectionSubtitle').textContent =
        `총 ${members.length}명. 기존 회원 DB 기준 · 가입일 내림차순.`;
      if (!members.length) {
        body.innerHTML = '<tr><td colspan="4" class="member-empty">아직 가입 회원이 없습니다.</td></tr>';
        return;
      }
      body.innerHTML = members.map((m, i) => `
        <tr>
          <td>${escapeHtml(m.displayName)}<br><span class="login">${escapeHtml(m.loginId)}</span></td>
          <td><span class="member-role">${escapeHtml(m.role)}</span></td>
          <td>${V.fmtDate(m.joinedAt)}</td>
          <td>${V.canIssue(m) ? `<button type="button" class="member-btn" data-issue="${i}">임시 비밀번호 발급</button>` : ''}</td>
        </tr>`).join('');
    } catch {
      body.innerHTML = '<tr><td colspan="4" class="member-empty">회원 목록을 불러오지 못했습니다.</td></tr>';
    }
  }

  function openConfirm(member) {
    pending = member;
    $('tpConfirmRows').innerHTML = V.confirmRows(member)
      .map(([k, v]) => `<tr><th>${escapeHtml(k)}</th><td>${escapeHtml(v)}</td></tr>`).join('');
    const same = V.sameNicknameCount(members, member);
    $('tpSameNick').textContent = same > 1 ? `닉네임 「${member.displayName}」 회원이 ${same}명입니다. 아이디를 꼭 확인해 주세요.` : '';
    $('tpSameNick').hidden = same <= 1;
    $('tpConfirmError').hidden = true;
    $('tpIssueBtn').disabled = false;
    $('tpConfirm').hidden = false;
  }

  function closeAll() {
    pending = null;
    $('tpConfirm').hidden = true;
    $('tpResult').hidden = true;
    $('tpPassword').textContent = '';  // 닫으면 화면에서 지운다(다시 보기 없음)
    $('tpCopied').textContent = '';
  }

  async function issue() {
    if (!pending) return;
    $('tpIssueBtn').disabled = true;
    try {
      const res = await fetchAuth(`/api/admin/members/${encodeURIComponent(pending.userId)}/temporary-password`, { method: 'POST' });
      if (!res.ok) throw new Error((await res.text()) || '발급하지 못했습니다.');
      const r = await res.json();
      $('tpConfirm').hidden = true;
      $('tpResultWho').textContent = `${r.displayName} (${r.loginId})`;
      $('tpPassword').textContent = r.temporaryPassword;
      $('tpExpires').textContent = V.fmtDateTime(r.expiresAt);
      $('tpResult').hidden = false;
    } catch (e) {
      $('tpConfirmError').textContent = e.message || '발급하지 못했습니다.';
      $('tpConfirmError').hidden = false;
      $('tpIssueBtn').disabled = false;
    }
  }

  async function copy() {
    const text = $('tpPassword').textContent;
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      $('tpCopied').textContent = '복사했습니다.';
    } catch {
      const range = document.createRange();
      range.selectNodeContents($('tpPassword'));
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      $('tpCopied').textContent = '자동 복사가 막혀 있어 글자를 선택해 두었습니다. Ctrl+C로 복사해 주세요.';
    }
  }

  function bind() {
    $('memberBody').addEventListener('click', e => {
      const b = e.target.closest('[data-issue]');
      if (b) openConfirm(members[Number(b.dataset.issue)]);
    });
    $('tpIssueBtn').addEventListener('click', issue);
    $('tpCopyBtn').addEventListener('click', copy);
    document.querySelectorAll('[data-tp-close]').forEach(b => b.addEventListener('click', closeAll));
  }

  (async () => {
    currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/'; return; }
    $('userName').textContent = window.userDisplayName(currentUser);
    bind();
    loadMembers();
  })();
})();
