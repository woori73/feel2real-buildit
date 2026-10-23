// 운영 주요 화면 PC 1440·모바일 390 읽기 전용 점검. 화면 내용·스크린샷·쿠키는 출력하지 않는다(숫자·판정만).
// 입력: BASE_URL, COOKIE_FILE(JSON {adminCookie:"ACCESS_TOKEN=..."}, 0600). GET 탐색만 하며 클릭·입력·POST 없음.
const fs = require('node:fs');
const { chromium } = require('playwright');

const PAGES = ['/', '/dashboard', '/admissions/workspace', '/ai-agent-studio', '/studio-projects'];
const VIEWPORTS = [{ name: 'pc', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }];

function cookieFrom(file, base) {
  const raw = JSON.parse(fs.readFileSync(file, 'utf8')).adminCookie || '';
  const i = raw.indexOf('=');
  if (i < 1) throw new Error('cookie unavailable');
  return { name: raw.slice(0, i), value: raw.slice(i + 1), domain: new URL(base).hostname, path: '/', secure: true, httpOnly: true };
}

// 한 화면의 판정. 내용 대신 구조 지표만 돌려준다.
async function inspect(page) {
  return page.evaluate(() => ({
    overflowX: Math.max(0, document.documentElement.scrollWidth - window.innerWidth),
    headings: document.querySelectorAll('h1,h2').length,
    images: document.querySelectorAll('img').length,
    brokenImages: [...document.querySelectorAll('img')].filter((i) => i.complete && i.naturalWidth === 0).length,
    loginForm: !!document.querySelector('input[type=password]'),
  }));
}

(async () => {
  const base = process.env.BASE_URL;
  // 로컬 검증용: 설치된 Chromium 경로를 지정할 수 있다(CI는 기본값 사용).
  const browser = await chromium.launch(process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {});
  let failed = 0;
  try {
    for (const vp of VIEWPORTS) {
      const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
      await context.addCookies([cookieFrom(process.env.COOKIE_FILE, base)]);
      for (const path of PAGES) {
        const page = await context.newPage();
        let consoleErrors = 0;
        page.on('console', (m) => { if (m.type() === 'error') consoleErrors++; });
        page.on('pageerror', () => { consoleErrors++; });
        const res = await page.goto(base + path, { waitUntil: 'networkidle', timeout: 45000 }).catch(() => null);
        const status = res ? res.status() : 0;
        const finalPath = new URL(page.url()).pathname.replace(/(.)\/$/, '$1');
        const m = status ? await inspect(page) : {};
        const ok = status === 200 && finalPath === path && !m.loginForm && m.overflowX <= 1 && m.brokenImages === 0;
        if (!ok) failed++;
        console.log(JSON.stringify({ viewport: vp.name, path, status, finalPath, ...m, consoleErrors, result: ok ? 'PASS' : 'FAIL' }));
        await page.close();
      }
      await context.close();
    }
  } finally {
    await browser.close();
  }
  console.log(`screen_check_failed=${failed}`);
  process.exit(failed ? 1 : 0);
})().catch(() => { console.log('screen_check_error (details suppressed)'); process.exit(1); });
