const { test, expect } = require('@playwright/test');

test('코디 로그인은 좁은 화면에서 계정 입력을 먼저 보여 주고 키보드 공간을 남긴다', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await page.goto('/?login=required&returnUrl=%2Fcodi');
  const dialog = page.getByRole('dialog', { name: '코디 로그인' });
  const submit = dialog.getByRole('button', { name: '로그인', exact: true });
  await expect(dialog.getByRole('heading')).toBeFocused();
  await expect(submit).toBeInViewport({ ratio: 1 });
  await expect(dialog.getByRole('link', { name: '네이버 로그인' })).toBeHidden();
  for (const field of [dialog.getByLabel('아이디', { exact: true }), dialog.getByLabel('비밀번호', { exact: true })]) {
    await expect(field).toBeInViewport({ ratio: 1 });
    const bounds = await field.boundingBox();
    expect(bounds.x).toBeGreaterThanOrEqual(0);
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(320);
  }

  await page.setViewportSize({ width: 320, height: 280 });
  await dialog.getByLabel('비밀번호', { exact: true }).focus();
  await submit.scrollIntoViewIfNeeded();
  await expect(submit).toBeInViewport({ ratio: 1 });
  await dialog.locator('summary').first().click();
  await expect(dialog.getByRole('link', { name: '네이버 로그인' })).toBeVisible();
});

test('일반 로그인은 기존 간편 로그인 선택지를 표시한다', async ({ page }) => {
  await page.goto('/?login=required&returnUrl=%2Fdashboard');
  const dialog = page.getByRole('dialog', { name: '로그인', exact: true });
  await expect(dialog.getByRole('link', { name: '네이버 로그인' })).toBeVisible();
  await expect(dialog.locator('.codi-login-options')).toBeHidden();
});
