/* Compose remains a native mailto link; copying has its own explicit, truthful result. */
(function (root) {
  'use strict';
  async function copyAddress(address, clipboard) {
    if (!clipboard || typeof clipboard.writeText !== 'function') return false;
    try { await clipboard.writeText(address); return true; } catch (_) { return false; }
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = { copyAddress: copyAddress };
  if (!root.document) return;
  root.document.addEventListener('click', async function (event) {
    var button = event.target.closest('button[data-copy-address]');
    if (!button || button.disabled) return;
    var address = button.dataset.copyAddress;
    var status = root.document.getElementById(button.getAttribute('aria-describedby'));
    button.disabled = true;
    try {
      var copied = await copyAddress(address, root.navigator.clipboard);
      // 버튼마다 안내 문구를 바꿀 수 있다(예: 계좌번호 복사). 없으면 기존 이메일 문구를 쓴다.
      if (status) status.textContent = copied ? (button.dataset.copyDone || '이메일 주소를 복사했습니다.')
        : (button.dataset.copyFail || '자동 복사를 사용할 수 없습니다. 아래 이메일 주소를 길게 누르거나 선택해서 복사해 주세요.');
    } finally { button.disabled = false; }
  });
})(typeof window !== 'undefined' ? window : globalThis);
