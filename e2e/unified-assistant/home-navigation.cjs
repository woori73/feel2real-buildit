const { expect } = require('@playwright/test');
// 밝은 개편 홈(2026-11-01)은 한 페이지다. 「전체 메뉴」에서 항목을 고르면 메뉴가 닫히고 그 구역으로 이동한다.
async function selectHomePane(page, key) {
  if (key === 'home') {
    await page.evaluate(() => window.scrollTo(0, 0));
    await expect(page.locator('#homeHero')).toBeInViewport();
    return;
  }
  await page.locator('#enBurger').click();
  await expect(page.locator('#homeMenuPanel')).toBeVisible();
  const link = page.locator(`#homeMenu [data-home-nav="${key}"]`);
  const href = await link.getAttribute('href');
  await link.click();
  await expect(page.locator('#homeMenuPanel')).toBeHidden();
  if (href.startsWith('#')) await expect(page.locator(href)).toBeInViewport();
}
module.exports = { selectHomePane };
