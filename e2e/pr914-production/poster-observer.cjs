// Passive Playwright instrumentation. No fetch/retry, page content, credentials or response bodies.
const fs = require('node:fs');
const path = require('node:path');
const {createHash} = require('node:crypto');
const SELECTOR = '[data-home-agent] img, #stagePoster';
const MEDIA = /^\/agent-media\/public\/(seven|suni)\/(poster\.jpg|front\.webp)$/;
const MANIFEST = '/api/agent-media/public';
const safeNumber = n => Number.isFinite(n) && n >= 0 ? n : null;
function resourcePath(url, origin) {
  try { const u = new URL(url); return u.origin === origin && (MEDIA.test(u.pathname) || u.pathname === MANIFEST) ? u.pathname : null; } catch { return null; }
}
function installImageEvents({origin, limit}) {
  const key = '__f2rPosterObservationV1';
  if (window[key]) return;
  const state = {events: [], dropped: 0};
  Object.defineProperty(window, key, {value: state});
  const listener = event => {
    const e = event.target;
    if (!(e instanceof HTMLImageElement) || !e.matches('[data-home-agent] img, #stagePoster')) return;
    let url; try { url = new URL(e.currentSrc || e.src); } catch { return; }
    if (url.origin !== origin || !/^\/agent-media\/public\/(seven|suni)\/(poster\.jpg|front\.webp)$/.test(url.pathname)) return;
    if (state.events.length === limit) { state.dropped++; return; }
    state.events.push({type: event.type, path: url.pathname, atMs: performance.now(),
      role: e.id === 'stagePoster' ? 'stage-poster' : 'choice', agent: url.pathname.split('/')[3]});
  };
  document.addEventListener('load', listener, true);
  document.addEventListener('error', listener, true);
}
async function observePosters(page, {origin, limit = 200} = {}) {
  origin = new URL(origin).origin;
  if (!Number.isInteger(limit) || limit < 1 || limit > 1000) throw new Error('Invalid observation limit');
  const report = {schemaVersion: 1, gatePolicy: 'Existing assertions and 5000ms deadline unchanged',
    requests: [], browserErrors: [], snapshots: [], dropped: {requests: 0, browserErrors: 0, snapshots: 0},
    collectionErrors: [], automaticIncidentDecision: false};
  const tracked = new WeakMap(), listeners = [];
  let nextId = 0;
  const append = (key, value) => {
    if (report[key].length >= (key === 'snapshots' ? Math.min(limit,20) : limit)) { report.dropped[key]++; return false; }
    report[key].push(value); return true;
  };
  const on = (event, callback) => { page.on(event, callback); listeners.push([event, callback]); };
  function requestPath(request) {
    for (let r = request, depth = 0; r && depth < 10; r = r.redirectedFrom(), depth++) {
      const p = resourcePath(r.url(), origin); if (p) return p;
    }
    return null;
  }
  function recordRequest(request) {
    if (tracked.has(request)) return tracked.get(request);
    const p = requestPath(request); if (!p) return null;
    const row = {id: ++nextId, path: p, redirected: !!request.redirectedFrom(), status: null,
      contentType: null, cache: {policy: null, ageSeconds: null, source: 'unknown'},
      finished: false, transportError: null, timing: null};
    if (!append('requests', row)) return null;
    tracked.set(request, row); return row;
  }
  function timing(request, row) {
    const t = request.timing();
    row.timing = Object.fromEntries(['startTime','requestStart','responseStart','responseEnd'].map(k => [k, safeNumber(t[k])]));
    row.timing.requestToHeadersMs = t.requestStart >= 0 && t.responseStart >= t.requestStart ? t.responseStart - t.requestStart : null;
    row.timing.fetchToCompleteMs = safeNumber(t.responseEnd);
  }
  on('request', recordRequest);
  on('response', response => {
    const request = response.request(), row = recordRequest(request); if (!row) return;
    const h = response.headers(), mime = (h['content-type'] || '').split(';')[0].trim().toLowerCase();
    row.status = response.status();
    row.contentType = /^[a-z-]+\/[a-z0-9.+-]{1,50}$/.test(mime) ? mime : null;
    // Only known cache directives, never raw arbitrary headers/ETags/cookies.
    row.cache.policy = (h['cache-control'] || '').toLowerCase().split(',').map(s => s.trim())
      .filter(s => /^(public|private|no-store|no-cache|must-revalidate|immutable|max-age=\d+|s-maxage=\d+)$/.test(s)).join(', ') || null;
    row.cache.ageSeconds = /^\d+$/.test(h.age || '') ? Number(h.age) : null;
    timing(request, row);
  });
  on('requestfinished', request => { const row = tracked.get(request); if (row) { row.finished = true; timing(request, row); } });
  on('requestfailed', request => {
    const row = tracked.get(request); if (!row) return;
    const message = request.failure()?.errorText || '';
    row.transportError = /^net::ERR_[A-Z_]+$/.test(message) ? message : 'UNCLASSIFIED_TRANSPORT_ERROR';
    timing(request, row);
  });
  function browserError(kind, message, location = {}) {
    // Hash text rather than serializing console arguments, tokens, user input or stacks.
    let source = null;
    try { const u = new URL(location.url); if (u.origin === origin && /^\/js\/f2r_[a-z_]+\.js$/.test(u.pathname)) source = u.pathname; } catch { /* no trusted source */ }
    append('browserErrors', {kind, atEpochMs: Date.now(), messageSha256: createHash('sha256').update(String(message)).digest('hex'),
      source, line: safeNumber(location.lineNumber), column: safeNumber(location.columnNumber)});
  }
  on('pageerror', e => browserError('uncaught-exception', e.message));
  on('console', m => { if (['error','warning'].includes(m.type())) browserError('console-' + m.type(), m.text(), m.location()); });
  await page.addInitScript(installImageEvents, {origin, limit});
  // Also support attaching to an already-loaded page; absence of network events is not a fault.
  await page.evaluate(installImageEvents, {origin, limit});
  async function checkpoint(label) {
    let timer;
    try {
      const data = await Promise.race([page.evaluate(({origin, selector}) => {
        const safePath = url => { try { const u = new URL(url, location.href); return u.origin === origin && /^\/agent-media\/public\/(seven|suni)\/(poster\.jpg|front\.webp)$/.test(u.pathname) ? u.pathname : null; } catch { return null; } };
        const state = window.__f2rPosterObservationV1;
        return {timeOrigin: performance.timeOrigin, atMs: performance.now(), images: [...document.querySelectorAll(selector)].slice(0,10).map(e => {
          const assigned = !!e.getAttribute('src'), rect = e.getBoundingClientRect(), style = getComputedStyle(e);
          const agent = e.closest('[data-home-agent]')?.dataset.homeAgent;
          return {role: e.id === 'stagePoster' ? 'stage-poster' : 'choice', agent: ['seven','suni'].includes(agent) ? agent : null,
            path: assigned ? safePath(e.currentSrc || e.src) : null, assigned, complete: e.complete,
            naturalWidth: e.naturalWidth, naturalHeight: e.naturalHeight,
            state: !assigned ? 'not-requested' : !e.complete ? 'loading' : e.naturalWidth > 0 ? 'loaded' : 'not-decoded',
            visible: !!(rect.width && rect.height && style.visibility === 'visible' && style.display !== 'none' && (!e.checkVisibility || e.checkVisibility({checkOpacity:true,checkVisibilityCSS:true}))),
            width: rect.width, height: rect.height};
        }), imageEvents: state?.events || [], imageEventsDropped: state?.dropped || 0,
        resourceTiming: performance.getEntriesByType('resource').filter(r => safePath(r.name)).slice(-200).map(r => ({path: safePath(r.name),
          durationMs: r.duration, transferSize: r.transferSize, encodedBodySize: r.encodedBodySize, decodedBodySize: r.decodedBodySize,
          workerStart: r.workerStart, deliveryType: ['cache','navigational-prefetch'].includes(r.deliveryType) ? r.deliveryType : 'unknown'}))};
      }, {origin, selector: SELECTOR}), new Promise((_,reject) => { timer = setTimeout(() => reject(new Error('Snapshot deadline')), 1000); })]);
      // Loading is a state, never an automatic incident. Preserve later successful recovery.
      for (const image of data.images) {
        image.loadedAfterObservedWait = image.state === 'loaded' && report.snapshots.some(s => s.timeOrigin === data.timeOrigin && s.images.some(old =>
          old.role === image.role && old.agent === image.agent && old.path === image.path && old.state === 'loading'));
      }
      append('snapshots', {label: String(label).replace(/[^a-zA-Z0-9:_-]/g,'').slice(0,60), ...data});
    } catch { if (report.collectionErrors.length < 10) report.collectionErrors.push('DOM_SNAPSHOT_UNAVAILABLE'); }
    finally { clearTimeout(timer); }
    return report;
  }
  async function save(file) {
    await checkpoint('final');
    fs.mkdirSync(path.dirname(file), {recursive:true});
    fs.writeFileSync(file, JSON.stringify(report,null,2));
    return report;
  }
  return {checkpoint, save, report, dispose: () => listeners.forEach(([event, callback]) => page.off(event, callback))};
}
module.exports = {observePosters};
