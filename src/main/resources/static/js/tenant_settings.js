/* BuildIt 2단계 — 테넌트 기능 설정 공유 로더.
 *
 * 공개 페이지(academy_public.html)에서 학원별 기능(campaignPopup / featuredCta /
 * liveMode / videoFilter)의 on/off 와 파라미터를 읽는다. 설정은 서버의
 * Academy.page_settings.builderDraft 에 `tenant.*` 키의 JSON 문자열로 저장된다.
 *
 * - 설정이 없거나 enabled !== true 이면 해당 기능은 동작하지 않는다(기본 비활성화).
 * - eduholic.feel2real.co.kr 호스트에서는 기존 eduholic_* 스크립트가 담당하므로
 *   테넌트 스크립트들은 실행하지 않는다(중복 실행 방지). 이 로더 자체는 중립적이다.
 * - 공개 API GET /api/academies/{slug} 응답의 pageSettings.builderDraft 를 사용한다.
 *   추가 요청이 아니라 페이지가 이미 호출하는 API 를 공유하므로 별도 fetch 는 1회만 수행하고
 *   4개 스크립트가 결과를 공유한다.
 */
(function () {
  'use strict';
  if (window.F2RTenantSettings) return;

  var cache = {};      // key -> config object | null (null = 비활성/없음)
  var pending = {};    // key -> [resolve...]
  var loaded = false;

  function detectSlug() {
    try {
      var root = document.getElementById('publicView');
      if (root && root.dataset && root.dataset.slug) return root.dataset.slug;
    } catch (_) {}
    try {
      var parts = String(location.pathname || '').split('/').filter(Boolean);
      if (parts.length) {
        var last = decodeURIComponent(parts[parts.length - 1] || '');
        // 경로형이 아닐 때(루트 등) 마지막 조각이 파일명일 수 있으므로 academies 경로만 인정
        if (parts[0] === 'academies' && parts.length >= 2) return last;
        if (last && last.indexOf('.') === -1) return last;
      }
      var m = String(location.hostname || '').match(/^([a-z0-9][a-z0-9-]*)\.feel2real\.co\.kr$/);
      if (m && m[1] !== 'www') return m[1];
    } catch (_) {}
    return '';
  }

  function parseDraft(draft) {
    var out = {};
    if (!draft || typeof draft !== 'object') return out;
    Object.keys(draft).forEach(function (k) {
      if (k.indexOf('tenant.') !== 0) return;
      var cfg = null;
      try {
        var parsed = JSON.parse(draft[k]);
        if (parsed && typeof parsed === 'object' && parsed.enabled === true) cfg = parsed;
      } catch (_) { /* 깨진 JSON 은 비활성 취급 */ }
      out[k] = cfg;
    });
    return out;
  }

  function settle(parsed) {
    loaded = true;
    Object.keys(parsed).forEach(function (k) { cache[k] = parsed[k]; });
    Object.keys(pending).forEach(function (k) {
      var val = Object.prototype.hasOwnProperty.call(cache, k) ? cache[k] : null;
      pending[k].forEach(function (r) { try { r(val); } catch (_) {} });
    });
    pending = {};
  }

  function load() {
    if (loaded || load._started) return;
    load._started = true;
    var slug = detectSlug();
    if (!slug) { settle({}); return; }
    fetch('/api/academies/' + encodeURIComponent(slug), {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (academy) {
        var draft = academy && academy.pageSettings && academy.pageSettings.builderDraft;
        settle(parseDraft(draft));
      })
      .catch(function () { settle({}); });
  }

  function get(key) {
    return new Promise(function (resolve) {
      if (loaded) {
        resolve(Object.prototype.hasOwnProperty.call(cache, key) ? cache[key] : null);
        return;
      }
      (pending[key] = pending[key] || []).push(resolve);
    });
  }

  function getSync(key) {
    if (!loaded) return undefined; // 아직 로딩 중
    return Object.prototype.hasOwnProperty.call(cache, key) ? cache[key] : null;
  }

  window.F2RTenantSettings = { get: get, getSync: getSync, _load: load };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', load);
  }
  // DOMContentLoaded 를 기다리지 않고 즉시 시도 — videoFilter 는 academy_public.js 보다
  // 먼저 설정이 필요하므로, 경로/서브도메인에서 slug 를 바로 뽑을 수 있으면 즉시 fetch 한다.
  load();
})();
