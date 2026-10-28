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
      if (status) status.textContent = copied ? '이메일 주소를 복사했습니다.'
        : '자동 복사를 사용할 수 없습니다. 아래 이메일 주소를 길게 누르거나 선택해서 복사해 주세요.';
    } finally { button.disabled = false; }
  });
})(typeof window !== 'undefined' ? window : globalThis);
