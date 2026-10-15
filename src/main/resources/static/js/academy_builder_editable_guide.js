(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RBuilderEditableGuide = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  const COACHMARK_STORAGE_KEY = 'f2r.builder.editableGuideCoachmarkSeen.v1';
  const EDITABLE_SELECTORS = Object.freeze([
    '[data-builder-text]',
    '[data-builder-draft]',
    '[data-builder-logo]',
    '[data-builder-basic]',
    '[data-featured-lessons-edit]',
    '[data-hero-video-edit]',
    '[data-principal-video-edit]',
    '[data-principal-career-item]',
    '[data-principal-career-add]',
    '[data-principal-career-remove]',
    '[data-intro-feature-item]',
    '[data-intro-feature-body]',
    '[data-intro-feature-add]',
    '[data-intro-feature-remove]',
    '[data-social-url-edit]',
    '[data-business-info-field]',
    '.browser-url'
  ]);
  const EDITABLE_SELECTOR = EDITABLE_SELECTORS.join(',');

  function shouldShowCoachmark(storage) {
    try {
      return !storage || storage.getItem(COACHMARK_STORAGE_KEY) !== 'true';
    } catch (_) {
      return true;
    }
  }

  function markCoachmarkSeen(storage) {
    try {
      if (storage) storage.setItem(COACHMARK_STORAGE_KEY, 'true');
    } catch (_) {
      // Storage may be unavailable in privacy mode; the guide itself must still work.
    }
  }

  function closestEditableTarget(node, rootElement) {
    if (!node || typeof node.closest !== 'function') return null;
    const target = node.closest(EDITABLE_SELECTOR);
    return target && (!rootElement || rootElement.contains(target)) ? target : null;
  }

  return Object.freeze({
    COACHMARK_STORAGE_KEY,
    EDITABLE_SELECTORS,
    EDITABLE_SELECTOR,
    shouldShowCoachmark,
    markCoachmarkSeen,
    closestEditableTarget
  });
});
