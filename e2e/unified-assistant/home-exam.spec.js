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
    await page.goto('/');
    await expect(page.locator('#siHomeTitle')).toHaveText('기출과 아주 유사한영어 기말 대비 문제!');
    await expect(page.locator('#homeChat')).toContainText('160문항');
    await expect(page.locator('#homeName')).toHaveText('서수니');
    await expect(page.locator('#exam-payment')).toContainText('20,000원');
    expect(await page.locator('main').innerText()).not.toMatch(/3주|240문항|총 6회|30,000원|서수니입니다/);
    expect(await page.locator('meta[name="description"]').getAttribute('content')).toContain('총 4회');
    expect(await page.locator('meta[property="og:description"]').getAttribute('content')).toContain('160문항');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
    await expect(page.locator('#examPromo')).toBeHidden();
    for(const agent of ['seven','suni']) {
      await page.locator(`[data-home-agent="${agent}"]`).click();
      await expect.poll(()=>page.locator(`[data-home-agent="${agent}"] img`).evaluate(i=>i.complete&&i.naturalWidth>0),{timeout:5000}).toBe(true);
      await expect.poll(()=>page.locator('#homeStage video').evaluateAll((vs,a)=>vs.some(v=>v.currentSrc.includes('/'+a+'/')&&v.readyState>=2&&!v.paused),agent),{timeout:5000}).toBe(true);
    }
    for(const tile of await page.locator('.si-holo-tile').all()) {
      await tile.click(); await expect(page.locator('#homeChat .is-agent').last()).toContainText('총 4회');
    }
    await page.locator('#homeInput').fill('zqxv 알 수 없는 질문');await page.locator('#homeForm button').click();
    await expect(page.locator('#homeChat .is-agent').last()).toContainText('아래에서 골라');
    await page.locator('#homeWelcomeAgain').click();await expect(page.locator('#homeChat .is-agent').last()).toContainText('160문항');
    await expect(page.locator('#enExamMail')).toHaveAttribute('href','mailto:woorinaesin@naver.com');
    // Observe the actual native click without launching an external email app in a CI runner.
    await page.evaluate(()=>{ window.mailClicks=[]; document.addEventListener('click',e=>{const a=e.target.closest('#enExamMail');if(a){window.mailClicks.push({href:a.href,prevented:e.defaultPrevented});e.preventDefault();}}); });
    await page.locator('#enExamMail').click();
    expect(await page.evaluate(()=>window.mailClicks)).toEqual([{href:'mailto:woorinaesin@naver.com',prevented:false}]);
    await page.locator('[data-copy-address]').click();
    await expect(page.locator('#examCopyStatus')).toContainText('자동 복사를 사용할 수 없습니다');
    await expect(page.locator('[data-copy-address]')).toBeEnabled();
    await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async v=>{window.copiedAddress=v;}}}));
    await page.locator('[data-copy-address]').click();
    await expect(page.locator('#examCopyStatus')).toHaveText('이메일 주소를 복사했습니다.');
    expect(await page.evaluate(()=>window.copiedAddress)).toBe('woorinaesin@naver.com');
    fs.mkdirSync(output,{recursive:true});
    await page.screenshot({path:path.join(output,`${width}-contact.png`)});
    await page.evaluate(()=>scrollTo(0,0));
    await page.screenshot({path:path.join(output,`${width}-home.png`)});
    await page.screenshot({path:path.join(output,`${width}-full.png`),fullPage:true});
    await page.locator('[data-auth-login]').first().click();await expect(page.locator('[data-login-modal]')).toBeVisible();
    expect(errors).toEqual([]);
  } finally {await context.close();}
});
test('given failed clip when rendering then approved-path poster remains visible',async({page})=>{
  await isolate(page,{brokenVideo:true}); await page.goto('/');
  await expect(page.locator('#stagePoster')).toBeVisible();
  await expect.poll(()=>page.locator('#stagePoster').evaluate(i=>i.complete&&i.naturalWidth>0),{timeout:5000}).toBe(true);
  await expect(page.locator('#homeChat')).toContainText('160문항');
});
for(const outcome of ['success','failed','blocked']) test(`optional promo ${outcome}: waits for actual Suni frame and restores character`,async({page})=>{
  // The promo asset is absent in production. This local-only isolated DOM exercises the final slot wiring.
  await page.route('**/*',route=>route.request().url().endsWith('/test-fixture')?route.fulfill({contentType:'text/html',body:'<!doctype html><html></html>'}):route.request().url().endsWith('exam-test.mp4')&&outcome==='failed'?route.fulfill({status:404}):route.fulfill({path:fixture('test-only.mp4'),contentType:'video/mp4'}));
  await page.goto('http://localhost:8080/test-fixture');
  await page.setContent('<button data-home-agent="suni" aria-pressed="true"></button><video id="homeVideo" muted playsinline loop></video><video id="homeVideoAlt"></video><section id="examPromo" hidden data-src="/video/landing/exam-test.mp4"><video id="examPromoVideo" controls muted playsinline></video></section>');
  if(outcome==='blocked') await page.evaluate(()=>document.getElementById('examPromoVideo').play=()=>Promise.reject(new DOMException('Blocked','NotAllowedError')));
  await page.addScriptTag({path:path.join(__dirname,'../../src/main/resources/static/js/home_exam_promo.js')});
  await expect(page.locator('#examPromo')).toBeHidden();
  await page.evaluate(async()=>{const v=document.getElementById('homeVideo');v.src='/agent-media/public/suni/idle.mp4';await v.play();v.classList.add('is-on');});
  if(outcome==='success') {
    await expect(page.locator('#examPromo')).toBeVisible();
    await expect.poll(()=>page.locator('#examPromoVideo').evaluate(v=>v.readyState>=2&&!v.paused)).toBe(true);
    await page.evaluate(()=>document.getElementById('examPromoVideo').dispatchEvent(new Event('ended')));
  } else await expect.poll(()=>page.locator('#examPromoVideo').getAttribute('src')).toContain('exam-test.mp4');
  await expect(page.locator('#examPromo')).toBeHidden();
  expect(await page.locator('#homeVideo').evaluate(v=>!v.paused)).toBe(true);
});
