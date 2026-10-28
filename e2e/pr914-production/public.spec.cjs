// Read-only anonymous production UI checks. No real form submissions or paid calls.
const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const {observePosters}=require('./poster-observer.cjs');
const origin = 'https://feel2real.co.kr';
const phase = process.env.EVIDENCE_PHASE || 'before';
if (!['before', 'after', 'rollback'].includes(phase)) throw new Error('Invalid phase');
const titles = ['기말고사 이용권','맞춤 문제 신청','시험 범위 제출','개별 신청','학부모·학생 신청','접수 방법'];
for (const width of [1440,360,390,430]) {
  test(`${phase} ${width}: characters, videos, assistant, menus, login`, async ({browser}, info) => {
    const context=await browser.newContext({viewport:{width,height:width===1440?1050:900},isMobile:width<500,hasTouch:width<500,locale:'ko-KR',timezoneId:'Asia/Seoul'});
    const media=[], blocked=[];
    await context.route('**/*', route => {
      const req=route.request(), u=new URL(req.url());
      if (!['GET','HEAD'].includes(req.method())) { blocked.push({method:req.method(),path:u.pathname}); return route.abort(); }
      // No analytics writes, auth POST, applications, LLM or paid requests.
      return route.continue();
    });
    const page=await context.newPage();
    const observer=await observePosters(page,{origin});
    page.on('response', response => { const u=new URL(response.url()); if (u.origin===origin && u.pathname.startsWith('/agent-media/public/')) media.push({path:u.pathname,status:response.status()}); });
    const out=path.resolve('release-evidence',phase,String(width));fs.mkdirSync(out,{recursive:true});
    try {
      await page.goto(origin,{waitUntil:'domcontentloaded'});
      await expect(page.locator('#homeChat .is-agent').first()).toBeVisible();
      await expect(page.locator('img[src*="F2R_logo_pure.svg"]').first()).toBeVisible();
      await expect(page.locator('.si-holo-tile')).toHaveCount(6);
      const visibleTitles=await page.locator('.si-holo-tile b').allTextContents();
      expect(visibleTitles.map(s=>s.trim())).toEqual(titles);
      if (phase==='after') {
        await expect(page.locator('link[href*="f2r_home_compact.css"]')).toHaveCount(1);
        await expect(page.locator('#homeStage .orbit svg')).toBeVisible();
        if (width===1440) {
          const ratio=await page.evaluate(()=>document.querySelector('#homeConsole').getBoundingClientRect().width/document.querySelector('#aiHome').getBoundingClientRect().width);
          expect(ratio).toBeGreaterThan(0.70); expect(ratio).toBeLessThan(0.81);
        }
        expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
        const sizes=await page.locator('.si-holo-tile, #homeForm button, [data-home-agent]').evaluateAll(nodes=>nodes.map(n=>({w:n.getBoundingClientRect().width,h:n.getBoundingClientRect().height})));
        expect(sizes.every(s=>s.w>=44 && s.h>=44)).toBe(true);
      }
      for (const [agent,name] of [['seven','세븐'],['suni','서수니']]) {
        await page.locator(`[data-home-agent="${agent}"]`).click();
        await expect(page.locator('#homeName')).toHaveText(name);
        const portrait=page.locator(`[data-home-agent="${agent}"] img`);
        try {
          await expect.poll(()=>portrait.evaluate(e=>e.complete && e.naturalWidth>0)).toBe(true);
        } finally {
          await observer.checkpoint('portrait-check-ended:'+agent);
        }
        // Real MP4 playback, not only a src attribute or synthetic fixture.
        await expect.poll(()=>page.locator('#homeStage video').evaluateAll((nodes,a)=>nodes.some(v=>v.currentSrc.includes('/'+a+'/') && v.readyState>=2 && !v.error),agent)).toBe(true);
        const start=await page.locator('#homeStage video').evaluateAll((nodes,a)=>Math.max(...nodes.filter(v=>v.currentSrc.includes('/'+a+'/')).map(v=>v.currentTime)),agent);
        // Sample advancing time with the selected video; loops are allowed.
        await expect.poll(()=>page.locator('#homeStage video').evaluateAll((nodes,arg)=>nodes.some(v=>v.currentSrc.includes('/'+arg.agent+'/') && !v.paused && v.currentTime!==arg.start),{agent,start})).toBe(true);
        await page.screenshot({path:path.join(out,agent+'.png'),fullPage:true});
      }
      for (const title of titles) {
        const count=await page.locator('#homeChat .is-agent').count();
        await page.locator('.si-holo-tile').filter({has:page.locator('b',{hasText:new RegExp('^'+title+'$')})}).click();
        await expect(page.locator('#homeChat .is-agent')).toHaveCount(count+1);
        await expect(page.locator('#homeChat .is-agent').last().locator('a[href="https://open.kakao.com/o/sMf4ifRi"]')).toBeVisible();
      }
      await page.locator('#homeInput').fill('알 수 없는 질문 zqxv');
      await page.locator('#homeForm button').click();
      await expect(page.locator('#homeChat .is-agent').last()).toContainText('아래에서 골라');
      await expect(page.locator('#homeChat .is-agent').last().locator('button')).not.toHaveCount(0);
      await page.locator('#homeWelcomeAgain').click();
      await expect(page.locator('#homeChat .is-agent').last()).toContainText('서수니');
      await page.locator('[data-auth-login]').first().click();
      await expect(page.locator('[data-login-modal]')).toBeVisible();
      await expect(page.locator('[data-login-modal] input[name="loginId"]')).toBeVisible();
      await page.screenshot({path:path.join(out,'login.png'),fullPage:false});
      await page.locator('[data-login-modal-close]').click();
      await page.screenshot({path:path.join(out,'menus-assistant.png'),fullPage:true});
      expect(media.filter(r=>r.status>=400)).toEqual([]);
      expect(media.some(r=>r.path.endsWith('.mp4') && [200,206].includes(r.status))).toBe(true);
    } finally {
      try {
        await page.screenshot({path:path.join(out,'final.png'),fullPage:true}).catch(()=>{});
        fs.writeFileSync(path.join(out,'network-summary.json'),JSON.stringify({media,blocked,phase,production:true},null,2));
      } finally {
        await observer.finish(path.join(out,'poster-observation.json'),()=>context.close());
      }
    }
  });
}
