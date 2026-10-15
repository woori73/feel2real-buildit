/**
 * 랜딩 '/' 베타 의견 등록 폼.
 *
 * 등록은 인증 필수다(SecurityConfig의 /api/** catch-all). 미로그인은 401로 떨어지므로
 * 사전 로그인 체크 없이 제출 후 401/403에서 공통 로그인 모달을 연다 — community_post.js와 같은 흐름.
 *
 * api.js를 쓰지 않는 이유: fetchAuth는 401에서 /home으로 redirect해버려 랜딩에 머물러야 하는
 * 이 폼과 맞지 않는다.
 */
(function () {
  'use strict';

  const form = document.getElementById('betaCommentForm');
  if (!form) return;

  const nicknameInput = document.getElementById('betaCommentNickname');
  const typeSelect = document.getElementById('betaCommentType');
  const commentInput = document.getElementById('betaCommentText');
  const statusOutput = document.getElementById('betaCommentStatus');
  const submitButton = form.querySelector('.beta-comment-submit');

  form.addEventListener('submit', submit);

  async function submit(event) {
    event.preventDefault();

    const nickname = nicknameInput.value.trim();
    const comment = commentInput.value.trim();
    if (!nickname) {
      setStatus('이름 또는 별명을 입력해 주세요.', true);
      nicknameInput.focus();
      return;
    }
    if (!comment) {
      setStatus('의견을 입력해 주세요.', true);
      commentInput.focus();
      return;
    }

    setBusy(true);
    setStatus('의견을 등록하는 중입니다.', false);
    try {
      const response = await fetch('/api/beta-comments', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          nickname: nickname,
          participantType: typeSelect.value,
          comment: comment
        })
      });

      if (response.status === 401 || response.status === 403) {
        setStatus('로그인 후 의견을 남길 수 있습니다.', true);
        if (typeof openLoginModal === 'function') openLoginModal();
        return;
      }
      if (!response.ok) {
        setStatus(await responseMessage(response), true);
        return;
      }

      form.reset();
      setStatus('의견을 등록했습니다. 아래 목록에서 확인하실 수 있습니다.', false);
      // 등록 즉시 노출되므로 새로 쓴 카드가 보이도록 캐러셀을 다시 그린다.
      // 캐러셀은 SSR 모델로 렌더되어 부분 갱신 경로가 없다 — 전체 리로드가 가장 단순하고 정확하다.
      window.setTimeout(function () { location.reload(); }, 1200);
    } catch (_) {
      setStatus('의견을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
    } finally {
      setBusy(false);
    }
  }

  function setBusy(busy) {
    submitButton.disabled = busy;
    nicknameInput.disabled = busy;
    typeSelect.disabled = busy;
    commentInput.disabled = busy;
  }

  function setStatus(message, error) {
    if (!statusOutput) return;
    statusOutput.textContent = message;
    statusOutput.classList.toggle('is-error', Boolean(error));
    statusOutput.setAttribute('role', error ? 'alert' : 'status');
  }

  /** 서버 검증 실패는 GlobalExceptionHandler가 평문 400으로 내린다 (JSON 아님). */
  async function responseMessage(response) {
    try {
      const text = (await response.text()).trim();
      return text || '의견을 등록하지 못했습니다.';
    } catch (_) {
      return '의견을 등록하지 못했습니다.';
    }
  }
})();
