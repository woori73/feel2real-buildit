(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RAcademyBusinessInfoEditor = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function idle() {
    return {
      phase: 'idle',
      snapshotRaw: null,
      draftRaw: null,
      dirty: false,
      error: '',
      errorField: null
    };
  }

  function begin(raw) {
    const snapshot = Object.assign({}, raw || {});
    return {
      phase: 'editing',
      snapshotRaw: snapshot,
      draftRaw: Object.assign({}, snapshot),
      dirty: false,
      error: '',
      errorField: null
    };
  }

  function withDraft(state, draftRaw, dirty) {
    if (!state || state.phase !== 'editing') return state;
    return Object.assign({}, state, {
      draftRaw: Object.assign({}, draftRaw || {}),
      dirty: Boolean(dirty),
      error: '',
      errorField: null
    });
  }

  function withFieldValue(state, key, value) {
    if (!state || state.phase !== 'editing' || !key) return state;
    const draftRaw = Object.assign({}, state.draftRaw || {}, {
      [key]: value == null ? '' : String(value)
    });
    return withDraft(state, draftRaw, !sameRaw(draftRaw, state.snapshotRaw));
  }

  function revertField(state, key) {
    if (!state || state.phase !== 'editing' || !key) return state;
    return withFieldValue(state, key, state.snapshotRaw && state.snapshotRaw[key]);
  }

  function beginSaving(state) {
    if (!state || state.phase !== 'editing') return { accepted: false, state };
    return {
      accepted: true,
      state: Object.assign({}, state, { phase: 'saving', error: '', errorField: null })
    };
  }

  function fail(state, message, key) {
    return Object.assign({}, state || idle(), {
      phase: 'editing',
      error: message || '',
      errorField: key || null
    });
  }

  function footerDecision(state, publicLink) {
    if (publicLink && state.phase === 'idle') return 'allow-link';
    return state.phase === 'idle' ? 'enter' : 'allow-editor';
  }

  function outsideDecision(state) {
    if (state.phase === 'saving') return 'block-saving';
    if (state.phase !== 'editing') return 'allow';
    return state.dirty ? 'apply-allow' : 'cancel-allow';
  }

  function keyDecision(state, key, modified, publicLink) {
    if (state.phase === 'idle') {
      if (publicLink || (key !== 'Enter' && key !== ' ')) return 'allow';
      return 'enter';
    }
    if (state.phase === 'saving') {
      return key === 'Escape' || key === 'Enter' ? 'block' : 'allow';
    }
    if (key === 'Escape') return 'cancel';
    if (key === 'Enter' && modified) return 'apply';
    return key === 'Enter' ? 'block' : 'allow';
  }

  function persistPending(options) {
    if (!options.request) throw new Error('pending publish request is missing');
    const nextRequest = cloneJson(options.request);
    nextRequest.pageSettings = Object.assign({}, nextRequest.pageSettings || {}, {
      businessInfo: Object.assign({}, options.raw || {})
    });
    options.writeRequest(nextRequest);

    const basePreview = options.cachedPreview
      || options.currentPreview
      || options.previewFromRequest(nextRequest);
    const nextPreview = options.mergePreview(basePreview, nextRequest);
    let previewCacheWritten = true;
    try {
      options.writePreview(nextPreview);
    } catch (_) {
      previewCacheWritten = false;
    }
    return { nextRequest, nextPreview, previewCacheWritten };
  }

  function cloneJson(value) {
    if (value == null) return value;
    return JSON.parse(JSON.stringify(value));
  }

  function sameRaw(left, right) {
    const keys = new Set(Object.keys(left || {}).concat(Object.keys(right || {})));
    return Array.from(keys).every((key) => String(left && left[key] || '') === String(right && right[key] || ''));
  }

  return {
    idle,
    begin,
    withDraft,
    withFieldValue,
    revertField,
    beginSaving,
    fail,
    footerDecision,
    outsideDecision,
    keyDecision,
    persistPending
  };
});
