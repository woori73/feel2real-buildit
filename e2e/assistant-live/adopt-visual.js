// 기존 빌더 상단 바의 'AI로 이어서 만들기' 버튼 배치·가독성·모바일 배치 점검(스크린샷 + 수치).
// 실제 CSS(앱 자체 CSS + FontAwesome)가 적용된 브라우저로 본다. 외부 CDN은 막지 않는다 — 접근이 안 되는 환경에서는
// LOCAL_FA 경로(@fortawesome/fontawesome-free 6.5.1 설치 위치)를 주면 같은 버전을 대신 서빙한다. 운영 접근 없음.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');
const BASE = process.env.BASE_URL || 'http://localhost:8080';
const OUT = path.resolve(__dirname, 'artifacts');
fs.mkdirSync(OUT, { recursive: true });
const LOCAL_FA = process.env.LOCAL_FA; // 예: /tmp/fa/node_modules/@fortawesome/fontawesome-free

const lum = (rgb) => { const [r, g, b] = rgb.map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
const parse = (c) => (c.match(/[\d.]+/g) || [0, 0, 0]).slice(0, 3).map(Number);

(async () => {
  const b = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium', headless: true, args: ['--no-sandbox'] });
  const rows = []; const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const mkCtx = async (viewport) => {
    const ctx = await b.newContext({ viewport });
    await ctx.route((u) => !/^https?:\/\/(localhost|127\.0\.0\.1)/.test(u.href), (route) => {
      const u = route.request().url();
      if (LOCAL_FA && /font-awesome\/6\.5\.1\/css\/all\.min\.css/.test(u)) return route.fulfill({ body: fs.readFileSync(LOCAL_FA + '/css/all.min.css'), contentType: 'text/css' });
      if (LOCAL_FA && /font-awesome\/6\.5\.1\/webfonts\//.test(u)) {
        const f = LOCAL_FA + '/webfonts/' + u.split('/').pop().split('?')[0];
        if (fs.existsSync(f)) return route.fulfill({ body: fs.readFileSync(f), contentType: 'font/woff2' });
      }
      if (LOCAL_FA) return route.abort(); // 그 밖의 외부 자원은 접근 불가 환경이므로 즉시 실패시킨다
      return route.continue();
    });
    return ctx;
  };
  // 전환 대상 학원(기존 방식) 준비
  const setup = await mkCtx({ width: 1280, height: 900 });
  const login = await setup.request.post(BASE + '/api/auth/login', { data: { loginId: 'e2e-visual', password: 'admin1234' } });
  if (!login.ok()) throw new Error('login failed ' + login.status());
  const created = await setup.request.fetch(BASE + '/api/tutor/academy', { method: 'POST', data: {
    name: '시각확인영어학원', intro: '기존 빌더로 만든 학원', subjectIds: [1], targetGroups: ['MIDDLE'], regions: [{ city: '서울', district: '강남구' }],
    pageSettings: { builderDraft: { designPreset: 'EXAM' } } } });
  if (![200, 409].includes(created.status())) throw new Error('create failed ' + created.status());
  const cookies = await setup.cookies();

  for (const [name, w, h] of [['pc1440', 1440, 900], ['laptop1024', 1024, 768], ['tablet768', 768, 1024], ['mobile430', 430, 932], ['mobile390', 390, 844], ['mobile360', 360, 740]]) {
    const ctx = await mkCtx({ width: w, height: h });
    await ctx.addCookies(cookies);
    const page = await ctx.newPage();
    await page.goto(BASE + '/builder', { waitUntil: 'load' });
    await page.waitForSelector('#adoptBtn', { timeout: 30000 });
    await page.waitForTimeout(800);
    const m = await page.evaluate(() => {
      const btn = document.getElementById('adoptBtn');
      const r = btn.getBoundingClientRect(); const cs = getComputedStyle(btn);
      const bar = document.querySelector('.builder-topbar').getBoundingClientRect();
      const sibs = [document.querySelector('.builder-topbar'), document.querySelector('.builder-body')].map((e) => e.getBoundingClientRect()).concat([...document.querySelectorAll('.adopt-note')].map((e) => e.getBoundingClientRect()));
      const overlap = sibs.some((s) => !(s.right <= r.left + 0.5 || s.left >= r.right - 0.5 || s.bottom <= r.top + 0.5 || s.top >= r.bottom - 0.5));
      const barBg = getComputedStyle(document.querySelector('.builder-topbar')).backgroundColor;
      return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, w: r.width, h: r.height, vw: window.innerWidth, vh: window.innerHeight,
        barTop: bar.top, barBottom: bar.bottom, fontSize: cs.fontSize, color: cs.color, bg: cs.backgroundColor, barBg,
        clipped: btn.scrollWidth > btn.clientWidth + 1, hScroll: document.documentElement.scrollWidth > window.innerWidth + 1,
        wraps: btn.getBoundingClientRect().height > parseFloat(cs.fontSize) * 2.6, overlap, barH: document.querySelector('.adopt-bar').getBoundingClientRect().height, bodyTop: document.querySelector('.builder-body').getBoundingClientRect().top, barBottomY: document.querySelector('.adopt-bar').getBoundingClientRect().bottom, hiddenByOverflow: r.right > window.innerWidth || r.left < 0 };
    });
    await page.screenshot({ path: path.join(OUT, `adopt-button-${name}.png`), clip: { x: 0, y: 0, width: w, height: Math.min(h, 420) } });
    ok(`${name}: 버튼이 화면 안에 보인다(가로 ${Math.round(m.left)}~${Math.round(m.right)} / ${m.vw}px)`, !m.hiddenByOverflow && m.w > 40, m);
    ok(`${name}: 상단 바·본문과 겹치지 않는다(본문이 안내 줄 아래 ${Math.round(m.bodyTop)}px에서 시작)`, m.bodyTop >= m.barBottomY - 0.5, m);
    ok(`${name}: 글자가 잘리거나 두 줄로 꺾이지 않는다`, !m.clipped && !m.wraps, m);
    ok(`${name}: 페이지에 가로 스크롤이 생기지 않는다`, !m.hScroll, m);
    const c = contrast(parse(m.color), parse(m.bg.startsWith('rgba(0, 0, 0, 0)') ? m.barBg : m.bg));
    ok(`${name}: 글자 대비 ${c.toFixed(1)}:1 (4.5 이상)`, c >= 4.5, { color: m.color, bg: m.bg });
    ok(`${name}: 터치 높이 ${Math.round(m.h)}px (모바일 32px 이상 권장)`, name.startsWith('mobile') ? m.h >= 32 : m.h >= 28, m);
    console.log(`     버튼 ${Math.round(m.w)}x${Math.round(m.h)} 글자 ${m.fontSize} 상단바 높이 ${Math.round(m.barBottom - m.barTop)}px`);
    if (name === 'pc1440' || name === 'mobile390') {
      await page.click('#adoptBtn');
      await page.waitForTimeout(300);
      await page.screenshot({ path: path.join(OUT, `adopt-modal-${name}.png`) });
      const mm = await page.evaluate(() => { const r = document.querySelector('.adopt-card').getBoundingClientRect(); return { l: r.left, r: r.right, t: r.top, b: r.bottom, vw: innerWidth, vh: innerHeight }; });
      ok(`${name}: 확인 창이 화면 안에 들어온다`, mm.l >= 0 && mm.r <= mm.vw && mm.t >= 0 && mm.b <= mm.vh, mm);
    }
    await ctx.close();
  }
  console.log(`\n검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch((e) => { console.error('실패:', e); process.exit(1); });
