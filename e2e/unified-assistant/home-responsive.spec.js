const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const phase = process.env.MOBILE_EVIDENCE_PHASE || 'after';
if (!['before', 'after'].includes(phase)) throw new Error('MOBILE_EVIDENCE_PHASE must be before or after');
const output = path.resolve(__dirname, '../../build/verification/home-responsive', phase);
const kakao = 'https://open.kakao.com/o/sMf4ifRi';
const email = 'mailto:woorinaesin@naver.com';
const questions = ['기말고사 이용권 신청', '시험 범위 제출 방법', '학부모 신청 문의'];
const sizes = [[360, 800], [390, 844], [430, 932], [768, 1024], [1440, 1050]];

for (const font of ['fallback', 'pretendard']) {
  for (const [width, height] of sizes) {
    test(`home ${width}x${height}, ${font}: geometry, compact menu, content order, intake links and assistant`, async ({ browser }) => {
      const mobile = width < 500;
      const context = await browser.newContext({ viewport: { width, height }, isMobile: mobile, hasTouch: mobile,
        deviceScaleFactor: 1, locale: 'ko-KR', timezoneId: 'Asia/Seoul',
        ...(mobile ? { userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/156.0 Mobile Safari/537.36' } : {}) });
      const errors = [], externalRequests = [];
      await context.route('**/*', route => {
        const request = route.request(), url = new URL(request.url());
        if (url.pathname === '/__test-pretendard.woff2' && url.hostname === 'localhost') {
          return route.fulfill({ path: process.env.HOME_FONT_PATH, contentType: 'font/woff2' });
        }
        if (url.hostname === 'cdn.jsdelivr.net' && url.pathname.includes('pretendardvariable-dynamic-subset')) {
          if (font === 'pretendard') return route.fulfill({ contentType: 'text/css', body:
            '@font-face{font-family:"Pretendard Variable";font-style:normal;font-weight:45 920;font-display:swap;src:url(http://localhost:8080/__test-pretendard.woff2) format("woff2")}' });
          return route.abort();
        }
        if (url.origin !== 'http://localhost:8080') { externalRequests.push(request.url()); return route.abort(); }
        if (url.pathname.startsWith('/api/analytics/') && request.method() === 'POST') return route.fulfill({ status: 204 });
        return route.continue();
      });
      try {
        if (font === 'pretendard') expect(fs.existsSync(process.env.HOME_FONT_PATH || ''), 'Set HOME_FONT_PATH to Pretendard v1.3.9 WOFF2').toBe(true);
        const page = await context.newPage();
        page.on('pageerror', error => errors.push(error.message));
        const act = locator => mobile ? locator.tap() : locator.click();
        await page.goto('/');
        await expect(page.locator('#homeChat .is-agent')).toHaveCount(1);
        await page.evaluate(() => document.fonts.ready);
        if (font === 'pretendard') expect(await page.evaluate(() => [...document.fonts]
          .some(face => face.family.replaceAll('"', '').replaceAll("'", '') === 'Pretendard Variable' && face.status === 'loaded'))).toBe(true);
        const { selectHomePane } = require('./home-navigation.cjs');
        const metrics = await page.evaluate(() => {
          const rect = n => { const r = n.getBoundingClientRect(); return { x:r.x, y:r.y + scrollY, right:r.right, bottom:r.bottom + scrollY }; };
          return { overflow:document.documentElement.scrollWidth - innerWidth,
            logo:rect(document.querySelector('.en-logo')), actions:rect(document.querySelector('.en-actions')),
            offer:rect(document.querySelector('#pass-offer .en-btn-primary')), promo:rect(document.querySelector('#examPromo')),
            keys:[...document.querySelectorAll('[data-home-nav]')].map(n=>n.dataset.homeNav) };
        });
        fs.mkdirSync(output, { recursive:true });
        const id = `${width}-${font}`;
        await page.screenshot({ path:path.join(output, `${id}-home.png`), fullPage:false });
        expect(metrics.overflow).toBe(0);
        expect(metrics.logo.x).toBeGreaterThanOrEqual(0);
        expect(metrics.actions.right).toBeLessThanOrEqual(width);
        expect(metrics.offer.bottom).toBeLessThanOrEqual(height);
        expect(metrics.promo.y).toBeGreaterThan(metrics.offer.bottom);
        expect(metrics.keys).toEqual(['home','request','wiki','game','agent','dashboard','chart','guide']);
        await expect(page.locator('#homePaneTitle')).toHaveText('홈');
        await expect(page.locator('#homeNotice')).toBeVisible();
        await expect(page.locator('#homeNotice .home-notice-group').first()).toContainText('압구정중3 기출문제 적중률 분석 리포트');
        await expect(page.locator('#homeNotice .home-notice-group').first()).toContainText('모의고사 위키 지문 복습 문제 업로드');
        await page.locator('#homeNoticePause').click();
        await expect(page.locator('#homeNoticePause')).toHaveAttribute('aria-pressed','true');
        await expect(page.locator('#homeNoticeTrack')).toHaveClass(/is-paused/);
        await page.locator('#homeNoticePause').click();
        await expect(page.locator('#homeNoticeTrack')).toHaveCSS('animation-play-state','running');
        if (width <= 900) {
          await expect(page.locator('#homeMenu')).toBeHidden();
          await page.locator('#enBurger').click();
          await expect(page.locator('#homeMenu')).toBeVisible();
          await page.keyboard.press('Escape');
          await expect(page.locator('#homeMenu')).toBeHidden();
          await expect(page.locator('#enBurger')).toBeFocused();
        } else {
          await expect(page.locator('#homeSidebar')).toBeVisible();
          const side = await page.locator('#homeSidebar').boundingBox();
          const content = await page.locator('#homeContent').boundingBox();
          expect(side.x + side.width).toBeLessThan(content.x);
        }
        for (const [key,target] of [['wiki','#wiki'],['game','#review-game'],['agent','#ai-content'],['chart','#chart-showcase'],['guide','#guide']]) {
          await selectHomePane(page,key);
          await expect(page.locator(target)).toBeVisible();
          await expect(page.locator('#pass-offer')).toBeHidden();
          await expect(page.locator('#homeNotice')).toBeHidden();
          expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
          if (width <= 900) await expect(page.locator('#homeMenu')).toBeHidden();
        }
        await selectHomePane(page,'dashboard');
        const replies = [];
        for (const question of questions) {
          const before = await page.locator('#homeChat .is-agent').count();
          await page.locator('#homeInput').fill(question);
          await act(page.locator('#homeForm button'));
          await expect(page.locator('#homeChat .is-agent')).toHaveCount(before+1);
          const reply = page.locator('#homeChat .is-agent').last();
          replies.push({question,text:await reply.locator('p').innerText(),links:await reply.locator('a').evaluateAll(a=>a.map(x=>x.getAttribute('href')))});
        }
        for (const reply of replies) {
          expect(reply.text).toContain('20,000원');
          expect(reply.text).toContain('카톡·이메일');
          expect(reply.links).toEqual(expect.arrayContaining([kakao,email]));
        }
        await page.locator('#aiHome').screenshot({path:path.join(output,`${id}-assistant.png`)});
        await act(page.locator('[data-home-agent="seven"]'));
        await expect(page.locator('#homeName')).toHaveText('세븐');
        await act(page.locator('[data-home-agent="suni"]'));
        await expect(page.locator('#homeName')).toHaveText('서수니');
        await selectHomePane(page,'request');
        const submit = page.locator('#enExamCta');
        await expect(submit).toHaveAttribute('href',kakao);
        const popupPromise = page.waitForEvent('popup');
        await act(submit);
        const popup = await popupPromise; await popup.close();
        await expect(page.locator('#enExamMail')).toHaveAttribute('href',email);
        await selectHomePane(page,'home');
        for (const [link,target] of [['#ai-content','#brandTitle'],['#review-game','#reviewGameTitle'],['#chart-showcase','#enChartTitle']]) {
          await act(page.locator(`#homeJump a[href="${link}"]`));
          await expect(page.locator(target)).toBeInViewport();
          await selectHomePane(page,'home');
        }
        expect(errors).toEqual([]);
        fs.writeFileSync(path.join(output,`${id}.json`),JSON.stringify({width,height,font,metrics,replies},null,2));
      } finally { await context.close(); }
    });
  }
}

test('legacy anchors, browser history, reduced motion and no-JS fallback remain usable',async({browser})=>{
  const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});
  const localOnly=route=>new URL(route.request().url()).origin==='http://localhost:8080'?route.continue():route.abort();
  await context.route('**/*',localOnly);
  const page=await context.newPage();
  await page.goto('/#exam-contact');
  await expect(page.locator('#exam-contact')).toBeVisible();
  await expect(page.locator('[data-home-nav="request"]')).toHaveAttribute('aria-current','page');
  const {selectHomePane}=require('./home-navigation.cjs');
  await selectHomePane(page,'wiki');
  await page.reload();
  await expect(page.locator('#wiki')).toBeVisible();
  await selectHomePane(page,'home');
  expect(await page.locator('#homeNoticeTrack').evaluate(e=>getComputedStyle(e).animationName)).toBe('none');
  await page.goBack();
  await expect(page.locator('#wiki')).toBeVisible();
  await context.close();
  const fallback=await browser.newContext({javaScriptEnabled:false,viewport:{width:390,height:844}});
  await fallback.route('**/*',localOnly);
  const plain=await fallback.newPage();
  await plain.goto('/');
  for(const id of ['pass-offer','wiki','ai-content','review-game','chart-showcase','aiHome','exam-contact']) await expect(plain.locator('#'+id)).toBeVisible();
  await expect(plain.locator('#homeMenu')).toBeVisible();
  await fallback.close();
});

test('rotating hero supports manual navigation, pause, service links and reduced motion', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  await page.goto('/');
  const hero = page.locator('#homeHero');
  await expect(hero.locator('[data-hero-slide]:visible')).toHaveCount(1);
  await expect(page.locator('#heroPause')).toHaveAttribute('aria-pressed', 'true');
  await page.locator('#heroNext').click();
  await expect(page.locator('#heroCount')).toHaveText('2 / 4');
  await expect(hero.getByRole('link', { name: '모의고사 위키 열기 ↗' })).toHaveAttribute('href', '/wiki');
  await page.locator('[data-hero-go="3"]').click();
  await expect(page.locator('#heroCount')).toHaveText('4 / 4');
  await hero.getByRole('link', { name: 'AI AGENT 만나보기 →' }).click();
  await expect(page.locator('#homePaneTitle')).toHaveText('AI AGENT');
  await page.goto('/');
  await page.locator('#heroPrev').click();
  await expect(page.locator('#heroCount')).toHaveText('4 / 4');
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
  await context.close();
});
