(()=>{
  const buttons = document.querySelectorAll('[data-now]');
  if (!buttons.length) return;
  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = 'now-' + btn.dataset.now;
      document.querySelectorAll('.now-panel').forEach(p => p.classList.toggle('show', p.id === target));
      document.getElementById(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });
})();
