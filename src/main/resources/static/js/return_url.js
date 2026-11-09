(function () {
  function safeInternalReturnUrl(value) {
    if (!value) return '/';
    var raw = String(value).trim();
    if (!raw) return '/';
    try {
      for (var i = 0; i < 2; i += 1) {
        var decoded = decodeURIComponent(raw);
        if (decoded === raw) break;
        raw = decoded;
      }
    } catch (_) {
      return '/';
    }
    raw = raw.replace(/\\/g, '/');
    if (!raw.startsWith('/')) return '/';
    if (raw.startsWith('//')) return '/';
    if (/^\/[\s\u0000-\u001f]*\//.test(raw)) return '/';
    if (/^[a-z][a-z0-9+.-]*:/i.test(raw)) return '/';
    if (/^\/(?:%2f|%5c)/i.test(String(value).trim())) return '/';
    return raw || '/';
  }

  window.safeInternalReturnUrl = safeInternalReturnUrl;
})();
