/**
 * 회원 전체 보기(/admin/members) 표시 변환(순수 함수). 브라우저는 window.AdminMembersView, 테스트(Node)는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AdminMembersView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const ROLE_LABEL = { STUDENT: '학생', TUTOR: '강사', ADMIN: '관리자', CREATOR: 'CHOONGGI 운영자' };

  function roleLabel(role) {
    return ROLE_LABEL[role] || String(role || '');
  }

  /** 관리자 계정에는 임시 비밀번호를 발급하지 않는다(관리자 로그인 화면의 비밀번호 변경을 쓴다). */
  function canIssue(member) {
    return !!member && member.role !== 'ADMIN' && member.userId != null;
  }

  /** "2026-11-16" 형식. 잘못된 값이면 빈 글. */
  function fmtDate(v) {
    if (!v) return '';
    const d = new Date(v);
    if (isNaN(d)) return '';
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  /** "2026-11-19 14:05" 형식(서버가 한국 시간으로 보낸 값). */
  function fmtDateTime(v) {
    if (!v) return '';
    const d = new Date(v);
    if (isNaN(d)) return '';
    return fmtDate(v) + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  /** 확인 창에 보일 줄(같은 닉네임이 여러 명일 수 있어 아이디·가입일·구분을 함께 보여 준다). */
  function confirmRows(member) {
    return [
      ['닉네임', member.displayName || ''],
      ['아이디', member.loginId || ''],
      ['가입일', fmtDate(member.joinedAt)],
      ['구분', roleLabel(member.role)]
    ];
  }

  /** 같은 닉네임 회원 수(본인 포함). 2 이상이면 확인 창에 주의 문장을 띄운다. */
  function sameNicknameCount(members, member) {
    return (members || []).filter(m => m && m.displayName === member.displayName).length;
  }

  return { roleLabel, canIssue, fmtDate, fmtDateTime, confirmRows, sameNicknameCount };
});
