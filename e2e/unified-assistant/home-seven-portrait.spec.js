const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const portrait = '/img/agents/seven/portrait-20261029.png';
const originalHash = '7896e8c4908aa9cd05ccaa0fb7fd71fe358c6a12c2004998768dce38cd4fe402';
const output = process.env.SEVEN_EVIDENCE_DIR || path.join(__dirname, '../../build/verification/home-seven');
const fixture = name => path.join(__dirname, 'fixtures', name);

// Only the existing media API/MP4s are fixtures. The new image is served by the real local JAR.
async function isolate(page, mode = 'video-failed') {
  await page.route('**/*', route => {
    const u = new URL(route.request().url());
    if (u.origin !== 'http://localhost:8080') return route.abort();
    if (u.pathname.startsWith('/api/analytics/')) return route.fulfill({ status: 204 });
    if (u.pathname === '/api/agent-media/public') {
      if (mode === 'api-failed') return route.fulfill({ status: 503 });
      const files = mode === 'empty' ? [] : ['front.webp', 'poster.jpg', 'idle.mp4', 'speak.mp4', 'think.mp4'];
      return route.fulfill({ json: { agents: { seven: { files }, suni: { files } } } });
    }
    if (u.pathname.startsWith('/agent-media/public/')) {
      const name = u.pathname.split('/').pop();
      if (name.endsWith('.mp4')) return mode === 'healthy'
        ? route.fulfill({ path: fixture('test-only.mp4'), contentType: 'video/mp4' })
        : route.fulfill({ status: 404 });
      return route.fulfill({ path: fixture(name), contentType: name.endsWith('.webp') ? 'image/webp' : 'image/jpeg' });
    }
    return route.continue();
  });
}

for (const width of [360, 390, 430, 1440]) {
  test(`Seven original ${width}: exact image, uncropped fallback and character switch`, async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width, height: width === 1440 ? 1050 : 900 },
      isMobile: width < 500, hasTouch: width < 500, locale: 'ko-KR' });
    try {
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await isolate(page);
      const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === portrait);
      await page.goto('/');
      const response = await responsePromise;
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('image/png');
      expect(crypto.createHash('sha256').update(await response.body()).digest('hex')).toBe(originalHash);
      const seven = page.locator('[data-home-agent="seven"]');
      await expect.poll(() => seven.locator('img').evaluate(i => i.complete && i.naturalWidth === 280 && i.naturalHeight === 346), { timeout: 5000 }).toBe(true);
      await seven.click();
      const poster = page.locator('#stagePoster');
      await expect(poster).toHaveAttribute('src', portrait);
      await expect(poster).toBeVisible();
      await expect.poll(() => poster.evaluate(i => i.complete && i.naturalWidth > 0), { timeout: 5000 }).toBe(true);
      for (const image of [seven.locator('img'), poster]) {
        expect(await image.evaluate(i => getComputedStyle(i).objectFit)).toBe('contain');
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
      await expect(page.locator('.si-holo-tile')).toHaveCount(6);
      await expect(page.locator('#homeChat')).toContainText('160문항');
      fs.mkdirSync(output, { recursive: true });
      await page.locator('#homeStage').screenshot({ path: path.join(output, `${width}-seven-stage.png`) });
      await page.screenshot({ path: path.join(output, `${width}-seven-home.png`), fullPage: true });
      await page.locator('[data-home-agent="suni"]').click();
      await expect(poster).toHaveAttribute('src', /\/suni\//);
      await expect(page.locator('#homeName')).toHaveText('서수니');
      await seven.click();
      await expect(poster).toHaveAttribute('src', portrait);
      await expect(poster).toBeVisible();
      expect(errors).toEqual([]);
    } finally { await context.close(); }
  });
}

for (const mode of ['api-failed', 'empty']) {
  test(`Seven original survives ${mode} without replacing Suni`, async ({ page }) => {
    await isolate(page, mode);
    await page.goto('/');
    await page.locator('[data-home-agent="seven"]').click();
    await expect(page.locator('#stagePoster')).toHaveAttribute('src', portrait);
    await expect(page.locator('#stagePoster')).toBeVisible();
    await page.locator('[data-home-agent="suni"]').click();
    await expect(page.locator('#stagePoster')).toBeHidden();
    await expect(page.locator('[data-home-agent="suni"] img')).not.toHaveAttribute('src', portrait);
  });
}

test('Seven original poster preserves existing MP4 greeting and idle playback', async ({ page }) => {
  await isolate(page, 'healthy');
  await page.goto('/');
  await page.locator('[data-home-agent="seven"]').click();
  await expect.poll(() => page.locator('#homeStage video').evaluateAll(vs => vs.some(v =>
    v.currentSrc.endsWith('/seven/speak.mp4') && v.readyState >= 2 && !v.paused)), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#stagePoster')).toBeHidden();
  await page.locator('#homeStage video[src="/agent-media/public/seven/speak.mp4"]').evaluate(v => v.dispatchEvent(new Event('ended')));
  await expect.poll(() => page.locator('#homeStage video').evaluateAll(vs => vs.some(v =>
    v.currentSrc.endsWith('/seven/idle.mp4') && v.readyState >= 2 && !v.paused)), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#homeStage video[src="/agent-media/public/seven/idle.mp4"]')).toHaveAttribute('poster', portrait);
});
