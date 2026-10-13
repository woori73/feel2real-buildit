/**
 * BuildIt 빌더 진입 화면
 * - AI 신규 제작: /builder 로 이동 (TUTOR/ADMIN 권한 필요)
 * - 샘플 선택: 선택한 샘플 정보를 담아 /builder 로 이동
 * - 크게 보기: 샘플 상세 모달 (build2 demo.html iframe)
 */
(function() {
  'use strict';

  // AI로 만들기 버튼
  // 참고: 입력된 프롬프트는 현재 /builder로 전달되지 않음 (백엔드 연동은 Claude 담당)
  // 버튼 클릭 시 기존 /builder로 이동만 수행
  document.querySelectorAll('[data-action="ai-create"]').forEach(function(btn) {
    btn.addEventListener('click', function() {
      // 기존 /builder로 이동 (TUTOR/ADMIN 권한 체크 적용)
      window.location.href = '/builder';
    });
  });

  // 이 디자인으로 시작 버튼
  document.querySelectorAll('[data-action="use-sample"]').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var sampleId = btn.getAttribute('data-sample-id');
      // 샘플 정보를 sessionStorage에 저장
      try {
        sessionStorage.setItem('buildit_entry_sample', sampleId);
      } catch (e) {}
      // 준비 중 안내 (백엔드 샘플 기반 생성 API는 Claude 담당)
      showPreparingModal('샘플 기반 제작', '선택한 샘플을 기반으로 하는 AI 제작 기능은 준비 중입니다.\n현재는 /builder에서 직접 제작할 수 있습니다.');
    });
  });

  // 크게 보기 버튼
  document.querySelectorAll('[data-action="preview-sample"]').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var sampleId = btn.getAttribute('data-sample-id');
      var previewUrl = btn.getAttribute('data-preview-url');
      if (previewUrl) {
        openPreviewModal(sampleId, previewUrl);
      } else {
        showPreparingModal('미리보기', '실제 미리보기를 준비 중입니다.');
      }
    });
  });

  function showPreparingModal(title, message) {
    var existing = document.getElementById('preparingModal');
    if (existing) existing.remove();
    
    var modal = document.createElement('div');
    modal.id = 'preparingModal';
    modal.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1000;';
    modal.innerHTML = 
      '<div style="background:#fff;border-radius:16px;padding:32px;max-width:400px;text-align:center;">' +
      '<h3 style="margin-bottom:12px;">' + escapeHtml(title) + '</h3>' +
      '<p style="color:#666;font-size:14px;white-space:pre-line;margin-bottom:20px;">' + escapeHtml(message) + '</p>' +
      '<button onclick="document.getElementById(\'preparingModal\').remove()" ' +
      'style="background:#111;color:#fff;border:none;border-radius:8px;padding:10px 24px;cursor:pointer;">확인</button>' +
      '</div>';
    modal.addEventListener('click', function(e) {
      if (e.target === modal) modal.remove();
    });
    document.body.appendChild(modal);
  }

  function openPreviewModal(sampleId, previewUrl) {
    var existing = document.getElementById('samplePreviewModal');
    if (existing) existing.remove();
    
    var modal = document.createElement('div');
    modal.id = 'samplePreviewModal';
    modal.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.7);display:flex;align-items:center;justify-content:center;z-index:1000;padding:20px;';
    modal.innerHTML = 
      '<div style="background:#fff;border-radius:16px;max-width:1000px;width:100%;max-height:90vh;overflow:hidden;display:flex;flex-direction:column;">' +
      '<div style="padding:16px 20px;border-bottom:1px solid #eee;display:flex;justify-content:space-between;align-items:center;">' +
      '<h3>샘플 미리보기</h3>' +
      '<button onclick="document.getElementById(\'samplePreviewModal\').remove()" style="background:none;border:none;font-size:20px;cursor:pointer;">×</button>' +
      '</div>' +
      '<div style="flex:1;overflow:auto;"><iframe src="' + escapeHtml(previewUrl) + '" style="width:100%;height:600px;border:none;"></iframe></div>' +
      '</div>';
    modal.addEventListener('click', function(e) {
      if (e.target === modal) modal.remove();
    });
    document.body.appendChild(modal);
  }

  function escapeHtml(str) {
    var div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();
