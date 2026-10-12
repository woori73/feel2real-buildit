// BuildIt 시각 편집기 E2E 공용 헬퍼.
// 외부 CDN(unpkg/cdnjs/jsdelivr)은 로컬 npm 패키지로 대체해 서빙하고(오프라인/재현 가능),
// 이미지 서비스(via.placeholder.com)는 800x400 회색 PNG로 대체한다.
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const NM = path.resolve(__dirname, 'node_modules');
const ART = path.resolve(__dirname, 'artifacts');
fs.mkdirSync(ART, { recursive: true });

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function placeholderPng(w, h) {
  const chunk = (t, d) => { const len = Buffer.alloc(4); len.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(w * 3, 0)]);
  for (let i = 0; i < w; i++) { row[1 + i * 3] = 0xcc; row[2 + i * 3] = 0xd5; row[3 + i * 3] = 0xe0; }
  const raw = Buffer.concat(Array.from({ length: h }, () => row));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const PNG = placeholderPng(800, 400);

async function launch() {
  return chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium', headless: true, args: ['--no-sandbox'] });
}

async function installRoutes(ctx) {
  await ctx.route(u => !/^https?:\/\/(localhost|127\.0\.0\.1)/.test(u.href), route => {
    const u = route.request().url();
    if (u.includes('grapes.min.js')) return route.fulfill({ body: fs.readFileSync(NM + '/grapesjs/dist/grapes.min.js'), contentType: 'application/javascript' });
    if (u.includes('grapes.min.css')) return route.fulfill({ body: fs.readFileSync(NM + '/grapesjs/dist/css/grapes.min.css'), contentType: 'text/css' });
    if (u.includes('font-awesome') && u.endsWith('.css')) return route.fulfill({ body: fs.readFileSync(NM + '/font-awesome/css/font-awesome.min.css'), contentType: 'text/css' });
    if (u.includes('fontawesome-webfont')) {
      const f = u.split('/').pop().split('?')[0]; const fp = NM + '/font-awesome/fonts/' + f;
      if (fs.existsSync(fp)) return route.fulfill({ body: fs.readFileSync(fp), contentType: 'application/octet-stream' });
    }
    if (u.includes('pretendard')) return route.fulfill({ body: '', contentType: 'text/css' });
    if (u.includes('placeholder.com')) return route.fulfill({ body: PNG, contentType: 'image/png' });
    return route.abort();
  });
}

async function newCtx(browser, opts = {}, login = { loginId: 'tutor', password: 'admin1234' }) {
  const ctx = await browser.newContext(opts);
  await installRoutes(ctx);
  if (login) {
    const r = await ctx.request.post(BASE + '/api/auth/login', { data: login });
    if (!r.ok()) throw new Error('login failed: ' + r.status());
  }
  return ctx;
}

function collect(page) {
  const log = { console: [], pageerrors: [], dialogs: [] };
  page.on('console', m => { if (m.type() === 'error') log.console.push(m.text().slice(0, 240)); });
  page.on('pageerror', e => log.pageerrors.push(String(e).slice(0, 300)));
  page.on('dialog', d => { log.dialogs.push(d.type() + ':' + d.message().slice(0, 80)); d.accept().catch(() => {}); });
  return log;
}

module.exports = { chromium, launch, newCtx, installRoutes, collect, BASE, PNG, ART, NM };
