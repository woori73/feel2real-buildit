/* Presentation only. Conversation, intent, greeting, auth and clip selection stay in main's unchanged modules. */
(() => {
  'use strict';
  document.querySelectorAll('#aiHome [data-home-intent]').forEach(button => button.addEventListener('click', () => {
    document.getElementById('assistant').scrollIntoView({block: 'nearest'});
  }));
  document.querySelector('#aiHome [data-panel="naesin"]').addEventListener('click', () => {
    document.getElementById('naesin-panel').hidden = false;
  });
  const stage = document.getElementById('homeStage');
  const poster = document.getElementById('stagePoster');
  const status = document.getElementById('mediaStatus');
  const videos = [document.getElementById('homeVideo'), document.getElementById('homeVideoAlt')];
  const buttons = [...document.querySelectorAll('[data-home-agent]')];
  function syncPresentation() {
    const selected = buttons.find(b => b.getAttribute('aria-pressed') === 'true');
    if (!selected) return;
    const agent = selected.dataset.homeAgent;
    const name = document.getElementById('homeName').textContent;
    const label = document.getElementById('assistant-name');
    if (label.textContent !== name) label.textContent = name;
    buttons.forEach(b => {
      const chosen = b === selected;
      b.classList.toggle('selected', chosen);
      const word = chosen ? '선택됨' : '선택하기';
      if (b.querySelector('.selected-word').textContent !== word) b.querySelector('.selected-word').textContent = word;
    });
    // Only reuse a URL actually assigned by main for this agent. Never retain the previous agent's poster.
    const belongs = url => url && url.includes('/' + agent + '/');
    const candidate = videos.map(v => v.getAttribute('poster')).find(belongs)
      || selected.querySelector('img').getAttribute('src');
    if (candidate && belongs(candidate)) {
      if (poster.getAttribute('src') !== candidate) { poster.hidden = true; poster.src = candidate; }
    } else { poster.removeAttribute('src'); poster.hidden = true; }
    const frame = stage.dataset.media === 'ready' && videos.some(v =>
      v.classList.contains('is-on') && belongs(v.getAttribute('src')) && v.readyState >= 2 && !v.error);
    stage.classList.toggle('has-frame', frame);
    const hasPoster = !!poster.getAttribute('src') && poster.complete && poster.naturalWidth > 0;
    poster.hidden = frame || !hasPoster;
    status.hidden = frame || hasPoster;
  }
  poster.addEventListener('load', syncPresentation);
  poster.addEventListener('error', syncPresentation);
  videos.forEach(v => ['loadeddata', 'playing', 'error', 'ended'].forEach(event => v.addEventListener(event, syncPresentation)));
  const observer = new MutationObserver(syncPresentation);
  observer.observe(stage, { subtree: true, attributes: true, attributeFilter: ['src', 'poster', 'aria-pressed', 'data-media', 'class'] });
  syncPresentation();
})();

// A smaller log remains keyboard-readable. Leave keys on its buttons/links untouched.
(() => {
  const conversation = document.getElementById('homeChat');
  conversation.addEventListener('keydown', event => {
    if (event.target !== conversation || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    if (event.key !== 'Home' && event.key !== 'End') return;
    event.preventDefault();
    conversation.scrollTo({ top: event.key === 'Home' ? 0 : conversation.scrollHeight, behavior: 'instant' });
  });
})();
