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
    await page.goto('/#dashboard');
    await expect(page.locator('#siHomeTitle')).toHaveText('우리 학교 맞춤문제 1장 먼저 받아보세요.');
    // 홈 AI 비서(서수니·세븐)는 2026-11-15 겨울 톤 개편에서 내렸다.
    await expect(page.locator('#aiHome')).toHaveCount(0);
    await expect(page.locator('#exam-payment')).toContainText('20,000원');
    expect(await page.locator('main').innerText()).not.toMatch(/3주|240문항|총 6회|30,000원|서수니입니다/);
    expect(await page.locator('meta[name="description"]').getAttribute('content')).toContain('총 4회');
    expect(await page.locator('meta[property="og:description"]').getAttribute('content')).toContain('160문항');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
    await selectHomePane(page, 'home');
    await expect(page.locator('#examPromo')).toBeVisible();
    await selectHomePane(page, 'dashboard');
    await expect(page.locator('.si-holo-tile')).toHaveCount(0);
    await expect(page.locator('#enExamMail')).toHaveAttribute('href','mailto:woorinaesin@naver.com');
    // Observe the actual native click without launching an external email app in a CI runner.
    await page.evaluate(()=>{ window.mailClicks=[]; document.addEventListener('click',e=>{const a=e.target.closest('#enExamMail');if(a){window.mailClicks.push({href:a.href,prevented:e.defaultPrevented});e.preventDefault();}}); });
    await selectHomePane(page, 'request');
    await page.locator('#enExamMail').click();
    expect(await page.evaluate(()=>window.mailClicks)).toEqual([{href:'mailto:woorinaesin@naver.com',prevented:false}]);
    await page.locator('[data-copy-address="woorinaesin@naver.com"]').click();
    await expect(page.locator('#examCopyStatus')).toContainText('자동 복사를 사용할 수 없습니다');
    await expect(page.locator('[data-copy-address="woorinaesin@naver.com"]')).toBeEnabled();
    await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async v=>{window.copiedAddress=v;}}}));
    await page.locator('[data-copy-address="woorinaesin@naver.com"]').click();
    await expect(page.locator('#examCopyStatus')).toHaveText('이메일 주소를 복사했습니다.');
    expect(await page.evaluate(()=>window.copiedAddress)).toBe('woorinaesin@naver.com');
    // 무통장입금 계좌는 공개 설정이 켜진 경우에만 보이고, 복사 버튼은 계좌번호 전용 안내를 쓴다.
    await selectHomePane(page, 'guide');
    if (await page.locator('#examBank').count()) {
      await page.locator('#exam-payment [data-copy-address]').click();
      await expect(page.locator('#examBankCopyStatus')).toHaveText('계좌번호를 복사했습니다.');
      expect(await page.evaluate(()=>window.copiedAddress)).toBe('110-498-250866');
    } else {
      await expect(page.locator('#exam-payment')).toContainText('입금 계좌는 문의해 주시면 안내합니다');
    }
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
  await expect(page.locator('#homeHero')).toHaveAttribute('data-active','0');
  await expect(page.locator('#homeHero [data-hero-slide]').first()).toHaveAttribute('aria-label','1 / 6 디지털 영어 기말고사');
  await expect(page.locator('#hwAdopt')).toBeVisible();
  await expect(page.locator('#hwAdopt')).toContainText('5만원');
  await expect(page.locator('#hwAdopt')).toContainText('3만원');
  await expect(page.locator('#aiHome, #homeChat, [data-home-agent]')).toHaveCount(0);
  for (const id of ['digital-exam','dexam-guide','counsel','ebook']) await expect(page.locator('#'+id)).toHaveCount(1);
});
