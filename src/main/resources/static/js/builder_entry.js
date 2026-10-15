/**
 * BuildIt 빌더 진입 화면
 * - AI 신규 제작: 입력한 요청을 보관하고 /builder 로 이동 (TUTOR/ADMIN). 그 외에는 홈의 시작 게이트(로그인 창)로 보낸다.
 * - 샘플 선택: 샘플의 느낌을 AI 요청 문장에 채워 준다(참고용).
 * - 크게 보기: 샘플 상세 모달 (build2 demo.html iframe)
 */
(function() {
  'use strict';

  var PENDING_KEY = 'builditPendingPrompt'; // landing_buildit.js·assistant_export.js와 같은 값/형식
  var MAX_PROMPT = 2000;
  var BUILDER_URL = '/builder';
  var GATE_URL = '/?start=builder'; // 홈의 시작 게이트(로그인 창)로 이어진다

  /** 로그인한 계정이 빌더를 쓸 수 있으면 /builder, 아니면 홈의 시작 게이트로 보낸다. */
  function entryDestination(user) {
    var ok = !!user && (user.role === 'TUTOR' || user.role === 'ADMIN');
    return ok ? BUILDER_URL : GATE_URL;
  }

  /** 빌더가 읽는 보관 형식({text, at}). 비어 있으면 null. */
  function pendingPayload(text, nowMs) {
    var value = String(text || '').trim().slice(0, MAX_PROMPT);
    return value ? JSON.stringify({ text: value, at: nowMs }) : null;
  }

  /** 샘플 느낌을 요청 문장 뒤에 덧붙인다. 이미 쓴 내용은 지우지 않는다. */
  function composeSamplePrompt(current, style) {
    var base = String(current || '').trim();
    var add = String(style || '').trim();
    if (!add) return base;
    return base ? base + ' ' + add : add;
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { entryDestination: entryDestination, pendingPayload: pendingPayload, composeSamplePrompt: composeSamplePrompt };
  }
  if (typeof document === 'undefined') return;

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

  // 로그인 계정 확인(랜딩과 같은 /api/auth/me). 실패하면 비로그인으로 본다.
  function currentUser() {
    try {
      var cached = JSON.parse(sessionStorage.getItem('currentUser') || 'null');
      if (cached && cached.role) return Promise.resolve(cached);
    } catch (e) {}
    return fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function(res) { return res.ok ? res.json() : null; })
      .catch(function() { return null; });
  }

  function startBuilder() {
    var payload = pendingPayload(promptEl ? promptEl.value : '', Date.now());
    if (payload) {
      try { sessionStorage.setItem(PENDING_KEY, payload); } catch (e) {}
    }
    currentUser().then(function(user) {
      window.location.href = entryDestination(user);
    });
  }

  // 제작 시작 버튼(AI로 만들기 / + 새 홈페이지)
  document.querySelectorAll('[data-action="ai-create"], [data-action="start-builder"]').forEach(function(btn) {
    btn.addEventListener('click', startBuilder);
  });

  // 샘플 버튼: 샘플의 느낌을 요청 문장에 채우고 입력창으로 돌아간다
  document.querySelectorAll('[data-action="use-sample"]').forEach(function(btn) {
    btn.addEventListener('click', function() {
      if (!promptEl) return;
      promptEl.value = composeSamplePrompt(promptEl.value, btn.getAttribute('data-sample-style'));
      try { sessionStorage.setItem(PROMPT_KEY, promptEl.value); } catch (e) {}
      promptEl.focus();
      promptEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
