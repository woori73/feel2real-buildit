/**
 * PhotoCraft 이미지 편집 플로우 UI.
 *
 * <p>AI 채팅에서 이미지 편집 의도가 감지되면 이 모듈이 활성화된다.
 * 일반 채팅 흐름에는 영향을 주지 않는다.
 *
 * <p>플로우:
 * 1. 채팅 메시지 전송 전 /flow/detect 호출
 * 2. imageEditDetected=true이면 이미지 편집 확인 UI 표시
 * 3. 사용자가 "미리보기" 클릭 → /flow/edit 호출
 * 4. 원본 vs 수정본 나란히 비교 모달 표시
 * 5. 승인 / 재수정 / 취소 버튼 제공
 * 6. 승인 시 /flow/approve 호출 → WorkingCopy 스테이징
 */
(function() {
  'use strict';

  var FLOW_API = '/api/tutor/academy/images/flow';

  /**
   * 채팅 메시지가 이미지 편집 의도인지 확인.
   * @param {string} message 채팅 메시지
   * @returns {Promise<Object>} 감지 결과
   */
  function detectImageIntent(message) {
    return fetch(FLOW_API + '/detect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: message })
    }).then(function(res) {
      if (!res.ok) throw new Error('의도 감지 실패: ' + res.status);
      return res.json();
    });
  }

  /**
   * 이미지 편집 실행 및 미리보기 데이터 조회.
   */
  function executeEdit(slotKey, operations) {
    return fetch(FLOW_API + '/edit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ slotKey: slotKey, operations: operations })
    }).then(function(res) {
      if (!res.ok) throw new Error('편집 실행 실패: ' + res.status);
      return res.json();
    });
  }

  /**
   * 승인/거부.
   */
  function approveImage(assetId, approved, reason) {
    return fetch(FLOW_API + '/approve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assetId: assetId, approved: approved, reason: reason || null })
    }).then(function(res) {
      if (!res.ok) throw new Error('승인 처리 실패: ' + res.status);
      return res.json();
    });
  }

  /**
   * 원본 vs 수정본 비교 모달 표시.
   * @param {Object} previewData ImagePreviewData
   */
  function showCompareModal(previewData) {
    // 기존 모달 제거
    var existing = document.getElementById('image-compare-modal');
    if (existing) existing.remove();

    var modal = document.createElement('div');
    modal.id = 'image-compare-modal';
    modal.className = 'image-compare-modal';
    modal.innerHTML =
      '<div class="image-compare-backdrop"></div>' +
      '<div class="image-compare-dialog">' +
        '<div class="image-compare-header">' +
          '<h3>이미지 편집 미리보기</h3>' +
          '<p class="image-compare-desc">' + escapeHtml(previewData.editDescription || '') + '</p>' +
        '</div>' +
        '<div class="image-compare-body">' +
          '<div class="image-compare-pane">' +
            '<div class="image-compare-label">원본</div>' +
            (previewData.originalImageUrl
              ? '<img src="' + escapeHtml(previewData.originalImageUrl) + '" alt="원본" />'
              : '<div class="image-compare-empty">원본 없음</div>') +
          '</div>' +
          '<div class="image-compare-pane">' +
            '<div class="image-compare-label">수정본 (v' + previewData.version + ')</div>' +
            '<img src="' + escapeHtml(previewData.editedImageUrl) + '" alt="수정본" />' +
          '</div>' +
        '</div>' +
        '<div class="image-compare-footer">' +
          '<button class="btn-approve" data-action="approve">승인</button>' +
          '<button class="btn-reedit" data-action="reedit">재수정</button>' +
          '<button class="btn-restore" data-action="restore">원본 복원</button>' +
          '<button class="btn-cancel" data-action="cancel">취소</button>' +
        '</div>' +
      '</div>';

    document.body.appendChild(modal);

    // 버튼 이벤트
    modal.querySelector('[data-action="approve"]').addEventListener('click', function() {
      approveImage(previewData.assetId, true, null).then(function(result) {
        closeModal();
        showToast('승인되었습니다. 미리보기에서 확인 후 발행하세요.');
        // 미리보기 새로고침 트리거
        if (window.dispatchEvent) {
          window.dispatchEvent(new CustomEvent('image-approved', { detail: previewData }));
        }
      }).catch(function(err) {
        showToast('승인 실패: ' + err.message);
      });
    });

    modal.querySelector('[data-action="reedit"]').addEventListener('click', function() {
      closeModal();
      showToast('재수정을 위해 채팅에 다시 요청해주세요.');
    });

    modal.querySelector('[data-action="restore"]').addEventListener('click', function() {
      if (!confirm('수정 내용을 버리고 원본으로 되돌리시겠습니까?')) return;
      revertToOriginal(previewData.assetId).then(function() {
        closeModal();
        showToast('원본으로 복원되었습니다.');
        if (window.dispatchEvent) {
          window.dispatchEvent(new CustomEvent('image-restored', { detail: previewData }));
        }
      }).catch(function(err) {
        showToast('복원 실패: ' + err.message);
      });
    });

    modal.querySelector('[data-action="cancel"]').addEventListener('click', function() {
      approveImage(previewData.assetId, false, '사용자 취소').then(function() {
        closeModal();
        showToast('취소되었습니다.');
      }).catch(function(err) {
        showToast('취소 실패: ' + err.message);
      });
    });

    modal.querySelector('.image-compare-backdrop').addEventListener('click', function() {
      closeModal();
    });

    function closeModal() {
      modal.remove();
    }
  }

  function escapeHtml(s) {
    return String(s || '').replace(/[&<>"']/g, function(c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function showToast(msg) {
    // 기존 toast 시스템이 있으면 사용, 없으면 alert
    if (window.showToast) {
      window.showToast(msg);
    } else {
      console.log('[ImageFlow] ' + msg);
    }
  }

  /**
   * 원본으로 복원.
   * @param {number} assetId 이미지 자산 ID
   */
  function revertToOriginal(assetId) {
    return fetch('/api/tutor/academy/images/' + assetId + '/revert', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }).then(function(res) {
      if (!res.ok) throw new Error('복원 실패: ' + res.status);
      return res.json();
    });
  }

  /**
   * 이미지가 없을 때 업로드 UI로 연결.
   * 기존 HeroMedia 업로드 흐름을 재사용한다.
   * @param {string} slotKey 슬롯 키
   */
  function connectUpload(slotKey) {
    // 기존 학원 미디어 업로드 UI가 있으면 트리거
    var uploadBtn = document.querySelector('[data-upload-slot="' + slotKey + '"]');
    if (uploadBtn) {
      uploadBtn.click();
      return;
    }
    // 없으면 안내 메시지
    showToast('이미지를 먼저 업로드해주세요. (슬롯: ' + slotKey + ')');
    // 업로드 페이지로 이동 옵션
    if (confirm('이미지 업로드 페이지로 이동하시겠습니까?')) {
      window.location.href = '/tutor/academy/media';
    }
  }

  // 전역 노출 (채팅 모듈에서 사용)
  window.ImageEditFlow = {
    detectImageIntent: detectImageIntent,
    executeEdit: executeEdit,
    approveImage: approveImage,
    showCompareModal: showCompareModal,
    revertToOriginal: revertToOriginal,
    connectUpload: connectUpload
  };
})();
