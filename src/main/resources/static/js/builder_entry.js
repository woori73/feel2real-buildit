/**
 * BuildIt 빌더 진입 화면
 * - AI 신규 제작: /builder 로 이동 (TUTOR/ADMIN 권한 필요)
 * - 샘플 선택: 선택한 샘플 정보를 담아 /builder 로 이동
 * - 크게 보기: 샘플 상세 모달 (build2 demo.html iframe)
 */
(function() {
  'use strict';

  // 모달 Escape 키 처리: 전역에 1회만 등록
  // - 포커스가 부모 문서에 있을 때: keydown으로 모달 닫기
  // - 포커스가 미리보기 iframe 내부에 있을 때: iframe 로드 시 contentDocument에도 리스너 등록
  function closeOpenModals() {
    var closed = false;
    ['preparingModal', 'samplePreviewModal'].forEach(function(id) {
      var m = document.getElementById(id);
      if (m) {
        m.remove();
        closed = true;
      }
    });
    return closed;
  }
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' || e.keyCode === 27) {
      closeOpenModals();
    }
  });

  // AI 입력창: 입력 내용 자동 저장/복원 (유실 방지)
  // 참고: 프롬프트의 /builder 자동 전달은 준비 중 (백엔드 연동은 Claude 담당)
  var PROMPT_KEY = 'buildit_entry_prompt';
  var promptEl = document.querySelector('#aiPrompt');
  if (promptEl) {
    // 페이지 로드 시 이전 입력 복원
    try {
      var saved = sessionStorage.getItem(PROMPT_KEY);
      if (saved) promptEl.value = saved;
    } catch (e) {}
    // 입력할 때마다 자동 저장
    promptEl.addEventListener('input', function() {
      try {
        sessionStorage.setItem(PROMPT_KEY, promptEl.value);
      } catch (e) {}
    });
  }

  // AI로 만들기 버튼
  document.querySelectorAll('[data-action="ai-create"]').forEach(function(btn) {
    btn.addEventListener('click', function() {
      // 입력 내용을 저장한 뒤 기존 /builder로 이동 (TUTOR/ADMIN 권한 체크 적용)
      // 프롬프트 자동 전달 기능은 준비 중임을 UI에 명시
      if (promptEl) {
        try {
          sessionStorage.setItem(PROMPT_KEY, promptEl.value);
        } catch (e) {}
      }
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
    // 모달에 포커스를 주어 Escape 키가 동작하도록 함
    modal.setAttribute('tabindex', '-1');
    modal.focus();
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
    // 모달에 포커스를 주어 Escape 키가 부모 문서에서 동작하도록 함
    modal.setAttribute('tabindex', '-1');
    modal.focus();
    // iframe 내부에 포커스가 있을 때도 Escape가 동작하도록 same-origin iframe에 리스너 등록
    var iframe = modal.querySelector('iframe');
    if (iframe) {
      iframe.addEventListener('load', function() {
        try {
          var doc = iframe.contentDocument || iframe.contentWindow.document;
          doc.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' || e.keyCode === 27) {
              closeOpenModals();
            }
          });
        } catch (err) {
          // cross-origin iframe인 경우 접근 불가 - 무시
        }
      });
    }
  }

  function escapeHtml(str) {
    var div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();
