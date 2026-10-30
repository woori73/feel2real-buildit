const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const source = '/video/landing/f2r-exam-remotion-v3-20261029.mp4';
const hash = '09a62f5696459dc8df79b5468a4ec2f0c6a5a276340fd35a9427042327d8e0af';
const output = process.env.PROMO_EVIDENCE_DIR || path.join(__dirname, '../../build/verification/home-promo');

async function isolate(page, failed = false) {
  await page.route('**/*', route => {
    const u = new URL(route.request().url());
    if (u.origin !== 'http://localhost:8080') return route.abort();
    if (u.pathname.startsWith('/api/analytics/')) return route.fulfill({ status: 204 });
    if (u.pathname === '/api/agent-media/public') return route.fulfill({ json: { configured: false, agents: {} } });
    if (failed && u.pathname === source) return route.fulfill({ status: 404 });
    return route.continue();
  });
}

for (const width of [360, 390, 430, 1440]) {
  test(`approved promo ${width}: second section, actual playback, sound, controls and contact`, async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width, height: width === 1440 ? 1050 : 900 },
      isMobile: width < 500, hasTouch: width < 500, locale: 'ko-KR' });
    try {
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await isolate(page);
      await page.goto('/');
      const video = page.locator('#examPromoVideo');
      await expect(page.locator('main > section').first()).toHaveAttribute('id', 'pass-offer');
      expect(await page.locator('main > section').evaluateAll(sections => sections.slice(0, 4).map(s => s.id)))
        .toEqual(['pass-offer', 'examPromo', 'ai-content', 'review-game']);
      // The promo sits right under the short offer, so it starts muted once it is half visible.
      expect(await video.evaluate(v => v.getBoundingClientRect().top)).toBeLessThan(width === 1440 ? 1050 : 900);
      fs.mkdirSync(output, { recursive: true });
      await page.screenshot({ path: path.join(output, `${width}-request-first.png`) });
      await video.scrollIntoViewIfNeeded();
      await expect(video).toBeVisible();
      await expect(video).toHaveAttribute('src', source);
      await expect.poll(() => video.evaluate(v => v.readyState >= 2 && !v.paused && v.currentTime > 0), { timeout: 5000 }).toBe(true);
      const initial = await video.evaluate(v => ({ duration: v.duration, muted: v.muted, controls: v.controls,
        inline: v.playsInline, width: v.videoWidth, height: v.videoHeight, fit: getComputedStyle(v).objectFit,
        rect: v.getBoundingClientRect().toJSON() }));
      expect(initial.duration).toBeCloseTo(20, 0);
      expect(initial).toMatchObject({ muted: true, controls: true, inline: true, width: 1920, height: 1080, fit: 'contain' });
      expect(initial.rect.x).toBeGreaterThanOrEqual(0);
      expect(initial.rect.right).toBeLessThanOrEqual(width);
      expect(initial.rect.y).toBeGreaterThanOrEqual(0);
      expect(initial.rect.bottom).toBeLessThanOrEqual(width === 1440 ? 1050 : 900);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
      await page.locator('#examPromoSound').click();
      await expect(page.locator('#examPromoSound')).toHaveText('소리 끄기');
      expect(await video.evaluate(v => v.muted)).toBe(false);
      await page.locator('#examPromoSound').click();
      expect(await video.evaluate(v => v.muted)).toBe(true);
      // Capture the actual supplied video at its final offer frame, not a synthetic frame.
      await video.evaluate(v => { v.pause(); v.currentTime = 19; });
      await expect.poll(() => video.evaluate(v => !v.seeking && v.readyState >= 2)).toBe(true);
      fs.mkdirSync(output, { recursive: true });
      await page.screenshot({ path: path.join(output, `${width}-promo-visible.png`) });
      await page.locator('#examPromo').screenshot({ path: path.join(output, `${width}-promo-section.png`) });
      await page.locator('#review-game').screenshot({ path: path.join(output, `${width}-game-link.png`) });
      await page.locator('#ai-content').screenshot({ path: path.join(output, `${width}-trailer.png`) });
      await expect(page.locator('#homeReviewGame')).toHaveAttribute('href', 'https://app.nimblefox.ai/g/g1y7QeOYd8');
      await expect(page.locator('#homeReviewGame')).toHaveAttribute('target', '_blank');
      await expect(page.locator('#homeReviewGame')).toHaveAttribute('rel', 'noopener noreferrer');
      await expect(page.locator('#homeAgentTrailer')).toHaveAttribute('src', '/video/landing/ai-agent-trailer-v16i-20261029.mp4');
      await expect(page.locator('#homeReviewGamePending')).toHaveCount(0);
      await expect(page.locator('#homeAgentTrailerPending')).toHaveCount(0);
      await expect(page.locator('#homeGameStart')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
      await video.scrollIntoViewIfNeeded();
      fs.writeFileSync(path.join(output, `${width}.json`), JSON.stringify(initial, null, 2));
      await video.evaluate(v => v.play());
      await page.locator('#pass-offer a.en-btn-primary').click(); // 영상 아래 중복 버튼은 2026-10-30에 정리
      await expect(page.locator('#examContactTitle')).toBeInViewport();
      await expect.poll(() => video.evaluate(v => v.paused)).toBe(true);
      await expect(page.locator('#enExamMail')).toHaveAttribute('href', 'mailto:woorinaesin@naver.com');
      await expect(page.locator('#aiHome')).toHaveAttribute('data-intake-enabled', 'false');
      expect(errors).toEqual([]);
    } finally { await context.close(); }
  });
}

test('supplied MP4 is byte-identical and supports browser range requests', async ({ request }) => {
  const full = await request.get(source);
  expect(full.status()).toBe(200);
  expect(full.headers()['content-type']).toContain('video/mp4');
  expect(crypto.createHash('sha256').update(await full.body()).digest('hex')).toBe(hash);
  const partial = await request.get(source, { headers: { Range: 'bytes=0-31' } });
  expect(partial.status()).toBe(206);
  expect(partial.headers()['content-range']).toBe('bytes 0-31/14649213');
  expect((await partial.body()).length).toBe(32);
});

test('game CTA hands off to the user-provided public URL in an isolated tab (destination is a fixture)', async ({ page, context }) => {
  await isolate(page);
  // No live Nimble request: this verifies the link/navigation boundary, not game content or availability.
  const destination = 'https://app.nimblefox.ai/g/g1y7QeOYd8';
  await context.route('https://app.nimblefox.ai/**', route => route.fulfill({
    contentType: 'text/html', body: '<title>Navigation fixture — not the live game</title>'
  }));
  await page.goto('/');
  const opened = page.waitForEvent('popup');
  await page.locator('#homeReviewGame').click();
  const popup = await opened;
  await popup.waitForURL(destination);
  expect(popup.url()).toBe(destination);
  expect(await popup.evaluate(() => window.opener === null)).toBe(true);
  await popup.close();
  await expect(page.locator('#homeReviewGame')).toBeVisible();
});

test('reduced motion keeps the promo visible and manually playable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await isolate(page);
  await page.goto('/');
  await expect(page.locator('#examPromo')).toBeVisible();
  expect(await page.locator('#examPromoVideo').evaluate(v => v.paused)).toBe(true);
  await page.locator('#examPromoSound').click();
  await expect.poll(() => page.locator('#examPromoVideo').evaluate(v => !v.paused && v.readyState >= 2), { timeout: 5000 }).toBe(true);
});

test('autoplay rejection retains native controls and a user gesture recovers playback', async ({ page }) => {
  await isolate(page);
  await page.addInitScript(() => {
    const play = HTMLMediaElement.prototype.play;
    let rejected = false;
    HTMLMediaElement.prototype.play = function () {
      if (this.id === 'examPromoVideo' && !rejected) { rejected = true; return Promise.reject(new DOMException('Autoplay blocked', 'NotAllowedError')); }
      return play.call(this);
    };
  });
  await page.goto('/');
  await page.locator('#examPromoVideo').scrollIntoViewIfNeeded();
  await expect(page.locator('#examPromoStatus')).toContainText('재생 버튼');
  await expect(page.locator('#examPromoVideo')).toBeVisible();
  await page.locator('#examPromoSound').click();
  await expect.poll(() => page.locator('#examPromoVideo').evaluate(v => !v.paused && v.readyState >= 2), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#examPromoStatus')).toBeHidden();
});

test('failed video keeps the original-frame poster, contact and assistant available', async ({ page }) => {
  await isolate(page, true);
  await page.goto('/');
  await expect(page.locator('#examPromoFallback')).toBeVisible();
  await expect.poll(() => page.locator('#examPromoFallback').evaluate(i => i.complete && i.naturalWidth > 0), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#examPromoStatus')).toContainText('영상을 불러오지 못했습니다');
  await expect(page.locator('#examPromoSound')).toBeHidden();
  await page.locator('#pass-offer a.en-btn-primary').click(); // 영상 아래 중복 버튼은 2026-10-30에 정리
  await expect(page.locator('#examContactTitle')).toBeInViewport();
  await page.locator('[data-home-agent="seven"]').click();
  await expect(page.locator('#stagePoster')).toBeVisible();
  await page.locator('#homeInput').fill('기말고사 이용권 신청');await page.locator('#homeForm button').click();await expect(page.locator('#homeChat .is-agent').last()).toContainText('160문항');
});

test('ended video remains visible for replay and character selection pauses its audio', async ({ page }) => {
  await isolate(page);
  await page.goto('/');
  const video = page.locator('#examPromoVideo');
  await video.scrollIntoViewIfNeeded();
  await expect.poll(() => video.evaluate(v => !v.paused && v.readyState >= 2), { timeout: 5000 }).toBe(true);
  await video.evaluate(v => { v.currentTime = v.duration - 0.15; });
  await expect.poll(() => video.evaluate(v => v.ended), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#examPromo')).toBeVisible();
  await page.locator('#examPromoSound').click();
  await expect.poll(() => video.evaluate(v => !v.paused)).toBe(true);
  await page.locator('[data-home-agent="seven"]').click();
  await expect.poll(() => video.evaluate(v => v.paused)).toBe(true);
});
