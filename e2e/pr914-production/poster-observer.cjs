// Passive Playwright instrumentation. No fetch/retry, page content, credentials or response bodies.
const fs = require('node:fs');
const path = require('node:path');
const {createHash, randomUUID} = require('node:crypto');
const {collectPosterSnapshot} = require('./poster-snapshot.cjs');
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
    collectionErrors: [], diagnostics: [], saveStatus: 'not-attempted', automaticIncidentDecision: false};
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
      const data = await Promise.race([page.evaluate(collectPosterSnapshot, {origin, selector: SELECTOR}), new Promise((_,reject) => { timer = setTimeout(() => reject(new Error('Snapshot deadline')), 1000); })]);
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
  function diagnostic(code, error) {
    const errorCode=['ENOSPC','EACCES','EROFS','EIO','ENOENT','EDQUOT','EPERM'].includes(error?.code)?error.code:'UNKNOWN';
    if(report.diagnostics.length<10)report.diagnostics.push({code,errorCode});
    // No paths, exception messages, tokens or page data in fallback output.
    try { process.stderr.write(JSON.stringify({source:'poster-observation',code,errorCode})+'\n'); } catch { /* logging must not change the gate */ }
  }
  async function save(file) {
    let temporary, fd;
    try {
      await checkpoint('final');
      fs.mkdirSync(path.dirname(file), {recursive:true});
      temporary=path.join(path.dirname(file),'.poster-observation-'+randomUUID()+'.tmp');
      fd=fs.openSync(temporary,'wx',0o600);
      report.saveStatus='saved';
      fs.writeFileSync(fd,JSON.stringify(report,null,2));
      fs.closeSync(fd); fd=undefined;
      fs.renameSync(temporary,file); temporary=undefined;
    } catch(error) {
      report.saveStatus='failed'; diagnostic('SAVE_FAILED',error);
    } finally {
      if(fd!==undefined)try { fs.closeSync(fd); } catch(error) { diagnostic('CLOSE_FAILED',error); }
      if(temporary)try { fs.unlinkSync(temporary); } catch(error) { if(error.code!=='ENOENT')diagnostic('TEMP_CLEANUP_FAILED',error); }
    }
    return report;
  }
  function dispose() { for(const [event,callback] of listeners.splice(0))page.off(event,callback); }
  async function finish(file, close) {
    try { await save(file); } finally {
      try { dispose(); } finally {
        try { await close(); } catch(error) { diagnostic('CONTEXT_CLOSE_FAILED',error); }
      }
    }
  }
  return {checkpoint, save, report, dispose, finish};
}
module.exports = {observePosters};
