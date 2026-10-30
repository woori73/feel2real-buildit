const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const baseURL = process.env.POLICY_BASE_URL || 'http://localhost:8080';
if (!['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname)) {
  throw new Error('Policy regression must use an isolated local application.');
}
const output = path.resolve(__dirname, '../../build/verification/public-policies');

for (const [width, height] of [[1440, 1050], [360, 800], [390, 844], [430, 932]]) {
  test(`policy links and readable official content ${width}px, intake disabled`, async ({ browser }) => {
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
      await expect(page.locator('#homeJump a')).toHaveCount(3);
      const footer = page.locator('.footer-links');
      for (const [document, label, heading] of [
        ['privacy', '개인정보처리방침', 'Feel2Real 홈페이지 개인정보 처리방침'],
        ['terms', '이용약관', 'Feel2Real 홈페이지 이용약관'],
        ['refunds', '환불·청약철회 정책', '환불·청약철회 정책'],
        ['business', '사업자 정보', '사업자 정보']
      ]) {
        const link = footer.locator(`a[href="/policies/${document}"]`);
        await expect(link).toHaveText(label);
        await expect.poll(() => link.evaluate(x=>x.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44);
        await act(link);
        await expect(page).toHaveURL(baseURL + '/policies/' + document);
        await expect(page.locator('h1')).toHaveText(heading);
        await expect(page.locator('.policy-content')).toBeVisible();
        await expect(page.locator('main form, main input')).toHaveCount(0);
        await expect(page.locator('.policy-notice')).toHaveCount(0);
        if (document !== 'business') await expect(page.locator('.policy-effective')).toHaveText('시행일 2026년 10월 30일');
        await capture('site-' + document);
      }
      await page.goto('/policies');
      await expect(page.locator('#documents article')).toHaveCount(3);
      await expect(page.locator('main form, main input')).toHaveCount(0);
      await expect(page.locator('main')).not.toContainText('110-498-250866');
      // 2026-10-30: draft labels removed; the site-intake policy pages stay reachable by address.
      for (const path of ['/policies', '/policies/privacy', '/policies/terms', '/policies/refunds', '/exam-service/privacy', '/exam-service/terms']) {
        await page.goto(path);
        for (const word of ['검토안', '초안', '임시안', '확인 중', '미확정', '최종 승인', '보존안', '준비안'])
          await expect(page.locator('main')).not.toContainText(word);
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
      }
      await page.goto('/exam-service/privacy#privacy');
      await expect(page.locator('header')).toContainText('시행일 2026년 10월');
      await expect(page.locator('#privacy')).toContainText('만14세 미만 아동의 동의');
      await expect(page.locator('#privacy')).toContainText('일반 문의: 답변 종결 후30일');
      await expect(page.locator('#privacy')).toContainText('개인정보 보호책임자는 박정민');
      await capture('intake-privacy');
      await page.goto('/exam-service/terms#terms');
      await expect(page.locator('#terms')).toContainText('총 4회');
      await expect(page.locator('#terms tbody tr')).toHaveCount(5);
      await expect(page.locator('#terms tbody tr').first()).toContainText('20,000원');
      await expect(page.locator('#terms')).toContainText('자동으로 공제·송금하지 않습니다');
      await capture('intake-terms');
      // NewStart 확장 프로그램 안내는 홈페이지 정책과 별개 페이지로 남아 있다(홈페이지에서 링크하지 않음).
      await page.goto('/privacy');
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
