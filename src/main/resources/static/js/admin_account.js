// 관리자 계정 보안 — 로그인 ID·비밀번호 교체. 입력값은 이 요청 본문으로만 전송하며 저장·로그·표시하지 않는다.
(function (root) {
  'use strict';

  /** 서버 규칙과 같은 사전 검증. 통과하면 null, 아니면 사용자에게 보여 줄 문구. */
  function validate(f) {
    if (!f.currentPassword || !f.newLoginId || !f.newPassword || !f.confirmPassword) return '모든 항목을 입력해 주세요.';
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{5,49}$/.test(f.newLoginId.trim())) return '새 로그인 ID는 영문·숫자로 시작하는 6~50자(영문·숫자·. _ - 사용)여야 합니다.';
    if (f.newPassword !== f.confirmPassword) return '새 비밀번호와 확인값이 일치하지 않습니다.';
    if (f.newPassword.length < 16) return '새 비밀번호는 16자 이상이어야 합니다.';
    if (f.newPassword.toLowerCase().indexOf(f.newLoginId.trim().toLowerCase()) >= 0) return '새 비밀번호에 새 로그인 ID를 포함할 수 없습니다.';
    if (f.newPassword === f.currentPassword) return '새 비밀번호는 현재 비밀번호와 달라야 합니다.';
    return null;
  }

  var api = { validate: validate };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (!root || !root.document) return;

  var $ = function (id) { return root.document.getElementById(id); };
  function show(msg, ok) { var m = $('acMsg'); m.textContent = msg; m.className = 'ac-msg ' + (ok ? 'ok' : 'err'); }

  (async function () {
    var me = await root.ensureCurrentUser();
    if (!me || me.role !== 'ADMIN') { root.location.href = '/home'; return; }
    $('acForm').addEventListener('submit', async function (e) {
      e.preventDefault();
      var f = { currentPassword: $('acCurrent').value, newLoginId: $('acLoginId').value,
        newPassword: $('acNew').value, confirmPassword: $('acConfirm').value };
      var problem = validate(f);
      if (problem) { show(problem, false); return; }
      if (!root.confirm('변경하면 모든 관리자 세션이 종료되고 이전 ID·비밀번호는 사용할 수 없습니다. 계속할까요?')) return;
      $('acSubmit').disabled = true;
      try {
        var res = await root.fetch('/api/admin/account/credentials', {
          method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(f) });
        ['acCurrent', 'acLoginId', 'acNew', 'acConfirm'].forEach(function (id) { $(id).value = ''; });
        if (res.status === 204) {
          show('변경되었습니다. 새 ID·비밀번호로 다시 로그인해 주세요. 잠시 후 로그인 화면으로 이동합니다.', true);
          root.setTimeout(function () { root.location.href = '/admin/login'; }, 2500);
          return;
        }
        var body = await res.json().catch(function () { return {}; });
        show(body.message || (res.status === 429 ? '반복 실패로 잠시 차단되었습니다. 잠시 후 다시 시도해 주세요.' : '변경하지 못했습니다. 입력을 확인해 주세요.'), false);
      } catch (err) {
        show('요청을 보내지 못했습니다. 네트워크를 확인해 주세요.', false);
      } finally {
        $('acSubmit').disabled = false;
      }
    });
  })();
})(typeof window !== 'undefined' ? window : null);
