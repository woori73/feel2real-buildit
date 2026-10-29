const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const baseURL = process.env.POLICY_BASE_URL || 'http://localhost:8080';
if (!['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname)) {
  throw new Error('Policy regression must use an isolated local application.');
}
const output = path.resolve(__dirname, '../../build/verification/public-policies');

for (const [width, height] of [[1440, 1050], [360, 800], [390, 844], [430, 932]]) {
  test(`policy links and readable draft content ${width}px, intake disabled`, async ({ browser }) => {
    // given: real local SSR, touch emulation on mobile; no production or paid network requests
    const context = await browser.newContext({ baseURL, viewport: { width, height },
      isMobile: width < 500, hasTouch: width < 500, locale: 'ko-KR', deviceScaleFactor: 1 });
    const errors = [];
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin !== baseURL) return route.abort();
      if (url.pathname.startsWith('/api/analytics/') && route.request().method() === 'POST') {
        return route.fulfill({ status: 204 });
      }
      return route.continue();
    });
    fs.mkdirSync(output, { recursive: true });
    try {
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      const act = locator => width < 500 ? locator.tap() : locator.click();
      const capture = async label => {
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
        await page.screenshot({ path: path.join(output, `${width}-${label}.png`), fullPage: true });
      };
      await page.goto('/');
      await expect(page.locator('#homeChat .is-agent')).toHaveCount(1);
      await expect(page.locator('.si-holo-tile')).toHaveCount(6);
      const footer = page.locator('.footer-links');
      await expect(footer.locator('a[href="/policies#terms"]')).toHaveText('서비스 이용·환불 안내');
      await act(footer.locator('a[href="/policies#privacy"]'));
      await expect(page).toHaveURL(baseURL + '/policies#privacy');
      await expect(page.locator('#privacy')).toContainText('홈페이지 전체 정책의 최종 승인 원문을 확인 중');
      await expect(page.locator('.policy-notice')).toContainText('확정된 약관이나 시행 중인 개인정보 처리방침이 아닙니다');
      await expect(page.locator('main form, main input')).toHaveCount(0);
      await expect(page.locator('main')).not.toContainText('110-498-250866');
      await capture('policy-hub');
      // when: follow each real policy link; then: non-empty SSR and explicit status, no placeholder alert
      await act(page.locator('main a[href="/exam-service/privacy#privacy"]'));
      await expect(page.locator('header')).toContainText('운영 활성화 전 검토안');
      await expect(page.locator('#privacy')).toContainText('만14세 미만 아동의 동의');
      await expect(page.locator('#privacy')).toContainText('일반 문의: 답변 종결 후30일');
      await capture('intake-privacy');
      await page.goto('/policies#terms');
      await act(page.locator('main a[href="/exam-service/terms#terms"]'));
      await expect(page.locator('#terms')).toContainText('총 4회');
      await expect(page.locator('#terms tbody tr')).toHaveCount(5);
      await expect(page.locator('#terms tbody tr').first()).toContainText('20,000원');
      await expect(page.locator('#terms')).toContainText('자동 환불액 확정·공제·송금은 하지 않습니다');
      await capture('intake-terms');
      await page.goto('/policies#privacy');
      await act(page.locator('main a[href="/privacy"]'));
      await expect(page.locator('.pg-updated')).toContainText('확장 프로그램 v0.4.0');
      await act(page.locator('.footer-links a[onclick="openContactModal()"]'));
      await expect(page.locator('#contactModal')).toBeVisible();
      await act(page.getByRole('button', { name: '닫기', exact: true }));
      await expect(page.locator('#contactModal')).toBeHidden();
      // preservation: disabled intake remains closed (read-only GET, no request submitted)
      const disabled = await page.goto('/exam-service');
      expect(disabled.status()).toBe(404);
      await expect(page.locator('#intakeForm')).toHaveCount(0);
      expect(errors).toEqual([]);
    } finally {
      await context.close();
    }
  });
}
