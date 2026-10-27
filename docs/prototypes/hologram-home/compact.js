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
