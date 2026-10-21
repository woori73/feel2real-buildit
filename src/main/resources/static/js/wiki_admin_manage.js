/**
 * BuildIt Wiki 자료 관리 버튼(수정/숨김 삭제/완전 삭제) — 공개 목록 페이지 공용.
 * /wiki, /wiki/originals, /wiki/analysis 의 원본·분석 자료 카드 아래에 붙는다.
 *
 * 원칙: "UI 표시 ≠ 권한 부여" (wiki_admin_upload.js 와 동일).
 * - ADMIN 일 때만 버튼을 만든다(표시 판단은 /api/auth/me).
 * - 실제 권한은 /api/admin/wiki/** 가 Spring Security ROLE_ADMIN 으로 결정한다.
 * - 수정은 공용 업로드 모달(wiki_admin_upload.js)의 수정 모드를 연다.
 * - 삭제 정책은 기존 관리자 페이지와 동일: 숨김 삭제(soft) / 완전 삭제(hard, 파일까지 삭제).
 */
(function () {
  var BASE = {
    original: '/api/admin/wiki/originals',
    analysis: '/api/admin/wiki/analysis-materials',
  };
  var mePromise = null;

  function me() {
    if (!mePromise) {
      mePromise = fetch('/api/auth/me', { credentials: 'same-origin' })
        .then(function (r) { return r.ok ? r.json() : null; })
        .catch(function () { return null; });
    }
    return mePromise;
  }

  async function isAdmin() {
    var m = await me();
    return !!m && m.role === 'ADMIN';
  }

  function buttonsHtml(kind, id) {
    return '<div class="card-manage" data-manage="' + kind + '">'
      + '<button type="button" data-manage-edit="' + id + '">수정</button>'
      + '<button type="button" data-manage-soft="' + id + '">숨김 삭제</button>'
      + '<button type="button" class="danger" data-manage-hard="' + id + '">완전 삭제</button>'
      + '</div>';
  }

  /**
   * container 안 관리 버튼 클릭을 한 번에 처리한다.
   * handlers: { onEdit(kind, id), reload() }
   */
  function bind(container, handlers) {
    container.addEventListener('click', async function (e) {
      var btn = e.target.closest('[data-manage-edit],[data-manage-soft],[data-manage-hard]');
      if (!btn) return;
      var wrap = btn.closest('[data-manage]');
      var kind = wrap && wrap.getAttribute('data-manage');
      if (!kind || !BASE[kind]) return;
      e.preventDefault();
      e.stopPropagation();
      if (btn.hasAttribute('data-manage-edit')) {
        handlers.onEdit(kind, btn.getAttribute('data-manage-edit'));
        return;
      }
      var hard = btn.hasAttribute('data-manage-hard');
      var id = btn.getAttribute(hard ? 'data-manage-hard' : 'data-manage-soft');
      if (!confirm(hard
        ? '이 자료를 완전히 삭제할까요? 파일까지 지워져 되돌릴 수 없습니다.'
        : '이 자료를 숨김 삭제할까요? 공개 목록에서 제외됩니다.')) return;
      try {
        var res = await fetchAuth(BASE[kind] + '/' + id + (hard ? '/hard' : ''), { method: 'DELETE', redirectOnAuthError: false });
        if (res.status === 401 || res.status === 403) { alert('관리자만 삭제할 수 있습니다.'); return; }
        if (!res.ok && res.status !== 204) { alert('삭제 실패: ' + res.status); return; }
        handlers.reload();
      } catch (_) {
        alert('삭제 중 오류가 발생했습니다.');
      }
    });
  }

  window.WikiAdminManage = { isAdmin: isAdmin, buttonsHtml: buttonsHtml, bind: bind };
})();
