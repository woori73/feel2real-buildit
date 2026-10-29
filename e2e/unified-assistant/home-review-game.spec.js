const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

// The real Unity build in the JAR, served by Feel2Real itself. No request may leave localhost.
const output = process.env.GAME_EVIDENCE_DIR || path.join(__dirname, '../../build/verification/home-game');
const page_path = '/games/g1-oct-review/v1/';
const build = ['WebGLBuild.loader.js', 'WebGLBuild.framework.js', 'WebGLBuild.data', 'WebGLBuild.wasm'];

async function isolate(context, external) {
  await context.route('**/*', route => {
    const u = new URL(route.request().url());
    if (u.origin !== 'http://localhost:8080') { external.push(route.request().url()); return route.abort(); }
    if (u.pathname.startsWith('/api/analytics/')) return route.fulfill({ status: 204 });
    if (u.pathname === '/api/agent-media/public') return route.fulfill({ json: { configured: false, agents: {} } });
    return route.continue();
  });
}

test('game files are precompressed, cached by version and limited to the fixed list', async ({ request }) => {
  const page = await request.get(page_path);
  expect(page.status()).toBe(200);
  expect(page.headers()['content-security-policy']).toContain("default-src 'self'");
  expect(page.headers()['content-security-policy']).toContain("frame-ancestors 'self'");
  let total = 0;
  for (const name of build) {
    const response = await request.get(page_path + 'Build/' + name, { headers: { 'Accept-Encoding': 'br' } });
    expect(response.status(), name).toBe(200);
    expect(response.headers()['cache-control'], name).toContain('immutable');
    total += (await response.body()).length;
  }
  expect(total).toBeGreaterThan(30 * 1024 * 1024); // decoded size: the full build, not a stub
  expect((await request.get(page_path + 'Build/secret.txt', { headers: { 'Accept-Encoding': 'br' } })).status()).toBe(404);
  expect((await request.get(page_path + 'Build/WebGLBuild.wasm', { headers: { 'Accept-Encoding': 'gzip' } })).status()).toBe(406);
});

for (const [width, height] of [[390, 844], [1440, 1050]]) {
  test(`review game ${width}px: starts only on click, runs inside Feel2Real and closes cleanly`, async ({ browser }) => {
    test.setTimeout(240000);
    const mobile = width < 500;
    const context = await browser.newContext({ viewport: { width, height }, isMobile: mobile, hasTouch: mobile, locale: 'ko-KR' });
    const external = [], gameRequests = [], errors = [];
    await isolate(context, external);
    try {
      const page = await context.newPage();
      page.on('pageerror', e => errors.push(e.message));
      page.on('request', r => { if (r.url().includes(page_path)) gameRequests.push(r.url()); });
      await page.goto('/');
      await page.locator('#homeJump a[href="#review-game"]').click();
      await expect(page.locator('#reviewGameTitle')).toBeInViewport();
      expect(gameRequests).toEqual([]); // nothing heavy before the user asks for it
      await page.locator('#homeGameStart').click();
      const frame = page.locator('#homeGameStage iframe');
      await expect(frame).toHaveAttribute('src', page_path);
      await expect(page.locator('#homeGameTools')).toBeVisible();
      const game = page.frameLocator('#homeGameStage iframe');
      await expect(game.locator('#unity-canvas')).toBeVisible();
      // Unity hides its loading card once the build has started.
      await expect(game.locator('#loading')).toBeHidden({ timeout: 200000 });
      await expect(game.locator('#error')).toBeHidden();
      const box = await frame.boundingBox();
      expect(box.width).toBeLessThanOrEqual(width);
      expect(box.height).toBeGreaterThan(300);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
      fs.mkdirSync(output, { recursive: true });
      await page.waitForTimeout(1500);
      await page.locator('#review-game').screenshot({ path: path.join(output, `${width}-game.png`) });
      await page.locator('#homeGameClose').click();
      await expect(page.locator('#homeGameStage iframe')).toHaveCount(0);
      await expect(page.locator('#homeGameStart')).toBeVisible();
      // Only the homepage's own font/analytics tags are external (blocked here); the game itself never leaves Feel2Real.
      expect(external.filter(u => !/^https:\/\/(cdn\.jsdelivr\.net|fonts\.googleapis\.com|www\.googletagmanager\.com)\//.test(u))).toEqual([]);
      expect(errors).toEqual([]);
    } finally { await context.close(); }
  });
}
