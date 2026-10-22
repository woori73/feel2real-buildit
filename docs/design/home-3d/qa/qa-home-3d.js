/* 홈 3D UI 실브라우저 검증. 사용법: node qa-home-3d.js <baseUrl> [chromeExe]
 * 필요: playwright-core (npm i playwright-core). 결과는 항목별 PASS/FAIL로 출력하고 하나라도 실패하면 종료 코드 1. */
const { chromium } = require('playwright-core');
const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const EXE = process.argv[3] || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const ARGS = ['--no-sandbox', '--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'];
const results = [];
const check = (name, ok, detail = '') => { results.push({ name, ok }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? '  — ' + detail : '')); };
const noise = (e) => /googletagmanager|fonts\.g|jsdelivr|net::ERR|status of 401/.test(e);

async function page(browser, opts, init) {
  const ctx = await browser.newContext(opts);
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message));
  p.on('console', (m) => { if (m.type() === 'error' && !noise(m.text())) errs.push(m.text().slice(0, 140)); });
  if (init) await p.addInitScript(init);
  return { ctx, p, errs };
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXE, args: ARGS });

  /* 1. 데스크톱: WebGL 렌더, 틸트, 등장, 링크, 로그인 모달 */
  {
    const { ctx, p, errs } = await page(browser, { viewport: { width: 1440, height: 900 } });
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.waitForSelector('#f3dCore[data-f3d-ready]', { timeout: 8000 }).then(() => check('데스크톱: WebGL 보석이 첫 프레임을 그린다', true), () => check('데스크톱: WebGL 보석이 첫 프레임을 그린다', false));
    check('데스크톱: 모드 webgl', (await p.getAttribute('#f3dCore', 'data-f3d-mode')) === 'webgl');
    const box = await p.locator('#svc-exam').boundingBox().catch(() => null);
    await p.locator('#svc-exam').scrollIntoViewIfNeeded();
    await p.waitForTimeout(900);
    const b2 = await p.locator('#svc-exam').boundingBox();
    await p.mouse.move(b2.x + b2.width * 0.85, b2.y + b2.height * 0.2);
    await p.waitForTimeout(250);
    const rx = await p.$eval('#svc-exam', (e) => e.style.getPropertyValue('--f3d-ry'));
    check('카드 틸트: 포인터를 올리면 기울기 변수가 바뀐다', rx && rx !== '0deg', 'ry=' + rx);
    await p.mouse.move(5, 5);
    await p.waitForTimeout(250);
    check('카드 틸트: 벗어나면 0으로 복귀', (await p.$eval('#svc-exam', (e) => e.style.getPropertyValue('--f3d-ry'))) === '0deg');
    const revealed = await p.$$eval('.f3d-card', (els) => els.filter((e) => e.classList.contains('is-in')).length);
    check('스크롤 등장: 화면에 들어온 카드가 표시 상태', revealed >= 3, revealed + '개');
    const links = await p.$$eval('.f3d-quick a, .f3d-card a.en-btn', (as) => as.map((a) => a.getAttribute('href')));
    let linkOk = true; const bad = [];
    for (const h of [...new Set(links.filter((x) => x.startsWith('/')))]) {
      const r = await p.request.get(BASE + h, { maxRedirects: 0 }).catch(() => null);
      if (!r || r.status() >= 400) { linkOk = false; bad.push(h + ':' + (r && r.status())); }
    }
    check('서비스 링크가 모두 응답한다(200/30x)', linkOk, bad.join(',') || links.length + '개');
    await p.evaluate(() => window.scrollTo(0, 0));
    await p.waitForTimeout(300);
    await p.click('[data-auth-login]').catch(() => {});
    await p.waitForTimeout(400);
    check('로그인 버튼이 모달을 연다', await p.$eval('[data-login-modal]', (m) => !m.hasAttribute('hidden')).catch(() => false));
    check('데스크톱: JS 오류 없음', errs.length === 0, errs.join(' | '));
    await ctx.close();
  }

  /* 2. 모바일 390: 가로 넘침, 터치 크기, 메뉴 */
  {
    const { ctx, p, errs } = await page(browser, { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.waitForTimeout(2500);
    const over = await p.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    check('모바일 390: 가로 스크롤 없음', over <= 1, 'overflow=' + over);
    const small = await p.$$eval('.f3d-chip, .f3d-card .en-btn', (els) => els.filter((e) => e.getBoundingClientRect().height < 44).length);
    check('모바일: 버튼 터치 영역 44px 이상', small === 0, '미달 ' + small);
    await p.click('#enBurger');
    check('모바일 메뉴가 열린다', await p.$eval('#enDrawer', (d) => !d.hasAttribute('hidden')));
    check('모바일: 모드 webgl(중간 사양)', ['webgl', 'static'].includes(await p.getAttribute('#f3dCore', 'data-f3d-mode')), await p.getAttribute('#f3dCore', 'data-f3d-mode'));
    check('모바일: JS 오류 없음', errs.length === 0, errs.join(' | '));
    await ctx.close();
  }

  /* 3. 움직임 감소: GPU를 쓰지 않고 정적 UI */
  {
    const { ctx, p, errs } = await page(browser, { viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.waitForTimeout(1500);
    check('움직임 감소: 정적 모드·캔버스 미사용', (await p.getAttribute('#f3dCore', 'data-f3d-mode')) === 'static' && (await p.$eval('#f3dCore', (e) => !e.hasAttribute('data-f3d-ready'))));
    check('움직임 감소: f3d-reduce 클래스, 카드 즉시 표시', await p.evaluate(() => document.documentElement.classList.contains('f3d-reduce') && getComputedStyle(document.querySelector('.f3d-card')).opacity === '1'));
    check('움직임 감소: SVG 대체 보석이 보인다', await p.$eval('.f3d-core-fallback', (e) => getComputedStyle(e).opacity === '1'));
    check('움직임 감소: JS 오류 없음', errs.length === 0, errs.join(' | '));
    await ctx.close();
  }

  /* 4. WebGL 미지원: 정적 대체 */
  {
    const init = () => { const g = HTMLCanvasElement.prototype.getContext; HTMLCanvasElement.prototype.getContext = function (t, ...a) { return /webgl/.test(t) ? null : g.call(this, t, ...a); }; };
    const { ctx, p, errs } = await page(browser, { viewport: { width: 1440, height: 900 } }, init);
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.waitForTimeout(1500);
    check('WebGL 미지원: 정적 모드로 전환', (await p.getAttribute('#f3dCore', 'data-f3d-mode')) === 'static');
    check('WebGL 미지원: SVG 대체 보석 표시', await p.$eval('.f3d-core-fallback', (e) => getComputedStyle(e).opacity === '1'));
    check('WebGL 미지원: JS 오류 없음', errs.length === 0, errs.join(' | '));
    await ctx.close();
  }

  /* 5. JS 비활성: 내용이 숨겨지지 않는다 */
  {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, javaScriptEnabled: false });
    const p = await ctx.newPage();
    await p.goto(BASE + '/', { waitUntil: 'load' });
    const vis = await p.$$eval('.f3d-card', (els) => els.every((e) => getComputedStyle(e).opacity === '1'));
    check('JS 비활성: 서비스 카드 7개가 모두 보인다', vis && (await p.$$('.f3d-card')).length === 7);
    check('JS 비활성: 예상문제 CTA 링크 유지', (await p.getAttribute('#enExamCta', 'href')) === 'https://open.kakao.com/o/sMf4ifRi');
    await ctx.close();
  }

  /* 6. 화면 밖 렌더 중지: 보석이 보이지 않으면 requestAnimationFrame을 거의 쓰지 않는다 */
  {
    const init = () => { window.__raf = 0; const r = window.requestAnimationFrame; window.requestAnimationFrame = function (f) { window.__raf++; return r.call(window, f); }; };
    const { ctx, p } = await page(browser, { viewport: { width: 1440, height: 900 } }, init);
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.waitForSelector('#f3dCore[data-f3d-ready]', { timeout: 8000 }).catch(() => {});
    await p.waitForTimeout(500);
    const onScreen = await p.evaluate(() => { const a = window.__raf; return new Promise((r) => setTimeout(() => r(window.__raf - a), 1000)); });
    await p.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await p.waitForTimeout(800);
    const offScreen = await p.evaluate(() => { const a = window.__raf; return new Promise((r) => setTimeout(() => r(window.__raf - a), 1000)); });
    check('화면 밖에서는 렌더 루프가 멈춘다', onScreen > 20 && offScreen <= 3, `보일 때 ${onScreen}프레임/초 → 가려졌을 때 ${offScreen}`);
    await ctx.close();
  }

  await browser.close();
  const failed = results.filter((r) => !r.ok).length;
  console.log(`\n결과: ${results.length - failed}/${results.length} PASS` + (failed ? `, ${failed} FAIL` : ''));
  process.exit(failed ? 1 : 0);
})();
