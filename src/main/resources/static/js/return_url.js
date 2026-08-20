(function () {
  function safeInternalReturnUrl(value) {
    if (!value) return '/home';
    var raw = String(value).trim();
    if (!raw) return '/home';
    try {
      for (var i = 0; i < 2; i += 1) {
        var decoded = decodeURIComponent(raw);
        if (decoded === raw) break;
        raw = decoded;
      }
    } catch (_) {
      return '/home';
    }
    raw = raw.replace(/\\/g, '/');
    if (!raw.startsWith('/')) return '/home';
    if (raw.startsWith('//')) return '/home';
    if (/^\/[\s\u0000-\u001f]*\//.test(raw)) return '/home';
    if (/^[a-z][a-z0-9+.-]*:/i.test(raw)) return '/home';
    if (/^\/(?:%2f|%5c)/i.test(String(value).trim())) return '/home';
    return raw || '/home';
  }

  window.safeInternalReturnUrl = safeInternalReturnUrl;
})();
