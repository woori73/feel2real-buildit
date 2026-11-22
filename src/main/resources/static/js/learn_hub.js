/* 학습 콘텐츠 모음(/learn): 학년·학교로 카드를 거른다. 서버 요청 없이 화면에 있는 카드만 숨기고 보인다. */
(function (root) {
  'use strict';
  const LH = {};

  /** 카드가 고른 학년·학교에 맞는지. 학교를 고르면 그 학교 카드만, 학년을 고르면 그 학년이 든 카드만 남는다. */
  LH.matches = (card, audience, school) => {
    const audiences = (card.audiences || '').split(',').filter(Boolean);
    if (audience && !audiences.includes(audience)) return false;
    if (school && card.school !== school) return false;
    return true;
  };

  function apply(doc, state) {
    let shownTotal = 0;
    doc.querySelectorAll('.lh-group').forEach(group => {
      let shown = 0;
      group.querySelectorAll('.lh-card').forEach(card => {
        const ok = LH.matches({ audiences: card.dataset.audiences, school: card.dataset.school || '' }, state.audience, state.school);
        card.hidden = !ok;
        if (ok) shown++;
      });
      group.hidden = shown === 0;
      shownTotal += shown;
    });
    doc.getElementById('lhEmpty').hidden = shownTotal > 0;
  }

  function boot(doc) {
    const state = { audience: '', school: '' };
    const bind = (attr, key) => doc.querySelectorAll('[data-' + attr + ']').forEach(b => {
      if (b.tagName !== 'BUTTON') return;
      b.addEventListener('click', () => {
        state[key] = b.dataset[attr];
        doc.querySelectorAll('button[data-' + attr + ']').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
        apply(doc, state);
      });
    });
    bind('audience', 'audience');
    bind('school', 'school');
  }

  root.LearnHub = LH;
  if (typeof module !== 'undefined' && module.exports) module.exports = LH;
  if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('DOMContentLoaded', () => boot(document));
})(typeof window !== 'undefined' ? window : globalThis);
