const retry = document.getElementById('retry');
if (retry) {
  retry.addEventListener('click', () => window.location.replace(codiEntry));
} else {
  window.location.replace(codiEntry);
}
