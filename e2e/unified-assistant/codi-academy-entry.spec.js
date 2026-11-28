const { test, expect } = require('@playwright/test');
const path = require('node:path');
const { access } = require('node:fs/promises');

const origin = 'http://academy.test';
const resources = path.resolve(__dirname, '../../src/main/resources');
const pages = { '/codi': 'dashboard_assistant.html', '/dashboard': 'dashboard.html', '/dashboard/assistant': 'dashboard_assistant.html' };

async function academySite(page, role) {
  await page.route('**/*', async route => {
    const { origin: requestOrigin, pathname } = new URL(route.request().url());
    if (requestOrigin !== origin) return route.abort();
    if (pages[pathname]) return route.fulfill({ path: path.join(resources, 'templates', pages[pathname]) });
    if (pathname === '/') return route.fulfill({ contentType: 'text/html', body: '<html lang="ko">로그인</html>' });
    if (pathname === '/api/auth/me') {
      return role ? route.fulfill({ json: { id: 42, role } }) : route.fulfill({ status: 401, json: {} });
    }
    if (pathname === '/api/admin/easy/businesses') {
      return route.fulfill({ json: [{ businessId: 7, name: '시험 학원' }] });
    }
    if (pathname === '/academycraft/api/rea/integration/context') {
      return route.fulfill({ json: { academyId: 10, builditRole: role, academyOptions: [] } });
    }
    if (pathname.startsWith('/api/') || pathname.startsWith('/academycraft/api/')) {
      return route.fulfill({ json: [] });
    }
    const asset = path.join(resources, 'static', pathname);
    try { await access(asset); } catch { return route.fulfill({ status: 404, body: '' }); }
    return route.fulfill({ path: asset });
  });
}

for (const pathname of Object.keys(pages)) {
  test(`세션이 끊겨도 공용 로그인에 복귀 화면을 전달한다 ${pathname}`, async ({ page }) => {
    await academySite(page, null);
    await page.goto(origin + pathname);
    await expect(page).toHaveURL(origin + '/?login=required&returnUrl=' + encodeURIComponent(pathname));
  });
}

for (const role of ['ADMIN', 'TUTOR']) {
  test(`${role} 계정의 웹 업무 바로가기를 휴대폰 폭에서도 유지한다`, async ({ page }) => {
    await academySite(page, role);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(origin + '/dashboard');
    const ledger = page.locator('#ahWorkLedger');
    await expect(ledger).toHaveAttribute('href', role === 'ADMIN' ? '/easy' : '/ledger');
    const links = page.locator('.ah-workspace a:visible');
    const destinations = () => links.evaluateAll(items => items.map(a => a.getAttribute('href')).sort());
    const desktop = await destinations();
    expect(desktop).toContain('/dashboard/assistant#all');
    expect(desktop).toContain(role === 'ADMIN' ? '/easy/students' : '/academycraft/screens/MBR-01-student-list.html');
    expect(desktop.includes('/admin/hub')).toBe(role === 'ADMIN');
    await page.setViewportSize({ width: 390, height: 780 });
    expect(await destinations()).toEqual(desktop);
    const codiLink = page.locator('.ah-nav a[href="/dashboard/assistant"]');
    await expect(codiLink).toBeVisible();
    const bounds = await codiLink.boundingBox();
    expect(bounds.width).toBeGreaterThanOrEqual(44);
    expect(bounds.height).toBeGreaterThanOrEqual(44);
    await expect(page.locator('.ah-subtitle')).toHaveCSS('font-size', '16px');
    await page.evaluate(() => document.documentElement.style.fontSize = '32px');
    await expect(page.locator('.ah-subtitle')).toHaveCSS('font-size', '32px');
  });
}

for (const denied of ['businesses', 'widgets']) {
  test(`코디 첫 화면에서 ${denied} 권한 거부 후에도 두 대화 모드를 사용한다`, async ({ page }) => {
    await academySite(page, 'TUTOR');
    await page.setViewportSize({ width: 390, height: 780 });
    await page.route('**/api/dashboard/businesses', route => denied === 'businesses'
      ? route.fulfill({ status: 403, json: {} })
      : route.fulfill({ json: [{ businessId: 7, name: '시험 사업체' }] }));
    await page.route('**/api/dashboard/widgets?*', route => route.fulfill({ status: 403, json: {} }));
    const deniedResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/dashboard/' + denied && response.status() === 403);
    await page.goto(origin + '/codi');
    await deniedResponse;
    if (denied === 'businesses') {
      await expect(page.locator('#bizSelect option:checked')).toHaveText('연결된 사업체 없음');
      await expect(page.locator('#widgetContainer')).toContainText('연결된 사업체가 없습니다.');
    } else {
      await expect(page.locator('#widgetContainer')).toContainText('이 사업체에 접근할 권한이 없습니다.');
    }
    await expect(page).toHaveURL(origin + '/codi');
    await expect(page.locator('#codiAvatar')).toBeVisible();
    for (const mode of ['general', 'work']) {
      const tab = page.locator('[data-codi-mode="' + mode + '"]');
      await expect(tab).toBeEnabled();
      await tab.click();
      await expect(tab).toHaveAttribute('aria-pressed', 'true');
    }
  });
}

for (const [status, headers, target] of [
  [401, {}, '/?login=required&returnUrl=%2Fcodi'],
  [403, { 'X-Password-Change-Required': 'true' }, '/account/password-change']
]) {
  test(`코디 장부 조회 중 인증이 필요하면 ${target}로 이동한다`, async ({ page }) => {
    await academySite(page, 'TUTOR');
    await page.route('**/account/password-change', route =>
      route.fulfill({ contentType: 'text/html', body: '비밀번호 변경' }));
    await page.route('**/api/dashboard/businesses', route => route.fulfill({ status, headers, json: {} }));
    await page.goto(origin + '/codi');
    await expect(page).toHaveURL(origin + target);
  });
}
