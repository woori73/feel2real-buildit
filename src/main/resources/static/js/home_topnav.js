/*
 * 홈 상단 펼침 메뉴(2026-11-21). 마우스를 올리면 CSS(:hover)로 열리고, 버튼을 누르면 이 스크립트가 연다.
 * 한 번에 하나만 열린다. Esc·바깥 누르기·메뉴 안 링크 누르기로 닫히고, Esc는 그 버튼으로 포커스를 돌려준다.
 */
(function () {
  'use strict';
  function setOpen(dd, open) {
    dd.classList.toggle('is-open', open);
    const btn = dd.querySelector('.hl-dd-btn');
    if (btn) btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }
  function init(doc) {
    const items = Array.prototype.slice.call(doc.querySelectorAll('.hl-dd'));
    const closeAll = except => items.forEach(d => { if (d !== except) setOpen(d, false); });
    items.forEach(dd => {
      const btn = dd.querySelector('.hl-dd-btn');
      if (!btn) return;
      btn.addEventListener('click', () => { const open = !dd.classList.contains('is-open'); closeAll(dd); setOpen(dd, open); });
      dd.querySelectorAll('.hl-dd-panel a').forEach(a => a.addEventListener('click', () => closeAll(null)));
    });
    doc.addEventListener('click', e => { if (!items.some(d => d.contains(e.target))) closeAll(null); });
    doc.addEventListener('keydown', e => {
      if (e.key !== 'Escape') return;
      const open = items.find(d => d.classList.contains('is-open'));
      if (!open) return;
      closeAll(null);
      const btn = open.querySelector('.hl-dd-btn');
      if (btn && btn.focus) btn.focus();
    });
    return { items, closeAll };
  }
  const api = { init, setOpen };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', () => init(document));
})();
