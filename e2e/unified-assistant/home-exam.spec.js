const { selectHomePane } = require('./home-navigation.cjs');
const {test, expect} = require('@playwright/test');
const path = require('node:path');
const fs = require('node:fs');
const fixture = name => path.join(__dirname,'fixtures',name);
const sizes = [360,390,430,768,1440];
const output = process.env.EXAM_EVIDENCE_DIR || path.join(__dirname,'../../build/verification/home-exam');
async function isolate(page, {media=true, brokenVideo=false}={}) {
  await page.route('**/*', route => {
    const u=new URL(route.request().url());
    if(u.origin!=='http://localhost:8080') return route.abort();
    if(u.pathname.startsWith('/api/analytics/')) return route.fulfill({status:204});
    if(u.pathname==='/api/agent-media/public') return route.fulfill({json:{agents:{seven:{files:['poster.jpg','front.webp','idle.mp4','speak.mp4','think.mp4']},suni:{files:['poster.jpg','front.webp','idle.mp4','speak.mp4','think.mp4']}}}});
    if(u.pathname.startsWith('/agent-media/public/')) {
      const name=u.pathname.split('/').pop();
      if(name.endsWith('.mp4')) return brokenVideo?route.fulfill({status:404}):route.fulfill({path:fixture('test-only.mp4'),contentType:'video/mp4'});
      return route.fulfill({path:fixture(name),contentType:name.endsWith('webp')?'image/webp':'image/jpeg'});
    }
    return route.continue();
  });
}
for(const width of sizes) test(`exam ${width}: canonical terms, separate mail controls, preserved media/menu flow`,async({browser})=>{
  const context=await browser.newContext({viewport:{width,height:width===1440?1050:900},isMobile:width<500,hasTouch:width<500,locale:'ko-KR'});
  try {
    const page=await context.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await isolate(page);
    await page.addInitScript(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{throw new DOMException('Denied','NotAllowedError');}}}));
    await page.goto('/#contact');
    // 2026-11-23 대표 지시: 홈의 맞춤문제 내용(배너 장·이용료·무통장입금·신청)은 모두 삭제하고 「서비스 도입 문의」(전화·이메일)로 잇는다.
    await expect(page.locator('#siHomeTitle, #exam-payment, #request, #examBank')).toHaveCount(0);
    // 홈 AI 비서(서수니·세븐)는 2026-11-15 겨울 톤 개편에서 내렸다.
    await expect(page.locator('#aiHome')).toHaveCount(0);
    expect(await page.locator('main').innerText()).not.toMatch(/맞춤문제 신청|20,000원|160문항|무통장입금|3주|240문항|총 6회|30,000원|서수니입니다/);
    expect(await page.locator('meta[name="description"]').getAttribute('content')).not.toContain('160문항');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
    await selectHomePane(page, 'home');
    // 2026-11-25 개편(차원연산4): 홍보 영상(examPromo)은 맨 아래 접기(academy-fold) 안에 있으므로 펼친 뒤 확인한다.
    await page.locator('#academy-fold > summary').click();
    await expect(page.locator('#examPromo')).toBeVisible();
    await expect(page.locator('.hl-fab-cta')).toHaveText('서비스 도입 문의');
    await expect(page.locator('.hl-fab-cta')).toHaveAttribute('href','#contact');
    await selectHomePane(page, 'contact');
    await expect(page.locator('.si-holo-tile')).toHaveCount(0);
    const contact = page.locator('#contact');
    await expect(contact.getByRole('link', { name: '전화 02-6324-1453' })).toHaveAttribute('href','tel:02-6324-1453');
    const mail = contact.getByRole('link', { name: '이메일 보내기' });
    await expect(mail).toHaveAttribute('href','mailto:woorinaesin@naver.com');
    // Observe the actual native click without launching an external email app in a CI runner.
    await page.evaluate(()=>{ window.mailClicks=[]; document.addEventListener('click',e=>{const a=e.target.closest('#contact a[href^="mailto:"]');if(a){window.mailClicks.push({href:a.href,prevented:e.defaultPrevented});e.preventDefault();}}); });
    await mail.click();
    expect(await page.evaluate(()=>window.mailClicks)).toEqual([{href:'mailto:woorinaesin@naver.com',prevented:false}]);
    const copy = contact.locator('[data-copy-address="woorinaesin@naver.com"]');
    await copy.click();
    await expect(page.locator('#contactCopyStatus')).toContainText('자동 복사를 사용할 수 없습니다');
    await expect(copy).toBeEnabled();
    await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async v=>{window.copiedAddress=v;}}}));
    await copy.click();
    await expect(page.locator('#contactCopyStatus')).toHaveText('이메일 주소를 복사했습니다.');
    expect(await page.evaluate(()=>window.copiedAddress)).toBe('woorinaesin@naver.com');
    fs.mkdirSync(output,{recursive:true});
    await page.screenshot({path:path.join(output,`${width}-contact.png`)});
    await page.evaluate(()=>scrollTo(0,0));
    await page.screenshot({path:path.join(output,`${width}-home.png`)});
    await page.screenshot({path:path.join(output,`${width}-full.png`),fullPage:true});
    // 휴대폰 상단에는 로그인 버튼이 없고(시안) 「전체 메뉴」 안에 있다.
    if (!(await page.locator('[data-auth-login]').first().isVisible())) await page.locator('#enBurger').click();
    await page.locator('[data-auth-login]:visible').first().click();await expect(page.locator('[data-login-modal]')).toBeVisible();
    await expect(page.locator('#homeMenuPanel')).toBeHidden();
    expect(errors).toEqual([]);
  } finally {await context.close();}
});
test('given the winter home when rendering then the digital final exam banner comes first and the assistant is gone',async({page})=>{
  await isolate(page); await page.goto('/');
  // 2026-11-25 개편: 디지털 기말고사 배너·도입 안내(pass-offer)는 맨 아래 접기 안에 있으므로 펼친다.
  await page.locator('#academy-fold > summary').click();
  await expect(page.locator('#homeHero')).toHaveAttribute('data-active','0');
  await expect(page.locator('#homeHero [data-hero-slide]').first()).toHaveAttribute('aria-label','1 / 1 디지털 영어 기말고사');
  await expect(page.locator('#hwAdopt')).toBeVisible();
  await expect(page.locator('#hwAdopt')).toContainText('5만원');
  await expect(page.locator('#hwAdopt')).toContainText('3만원');
  await expect(page.locator('#aiHome, #homeChat, [data-home-agent]')).toHaveCount(0);
  for (const id of ['digital-exam','dexam-guide','counsel','ebook']) await expect(page.locator('#'+id)).toHaveCount(1);
});
