const { expect } = require('@playwright/test');
async function selectHomePane(page, key) {
  const link = page.locator(`#homeMenu [data-home-nav="${key}"]`);
  if (!(await link.isVisible())) await page.locator('#enBurger').click();
  await link.click();
  await expect(link).toHaveAttribute('aria-current', 'page');
}
module.exports = { selectHomePane };
