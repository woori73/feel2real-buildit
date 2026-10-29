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
        const metrics = await page.evaluate(() => {
          const rect = node => { const r = node.getBoundingClientRect(); return { x:r.x, y:r.y + scrollY, width:r.width, height:r.height, right:r.right, bottom:r.bottom + scrollY }; };
          const visible = node => !!node && getComputedStyle(node).display !== 'none' && node.getBoundingClientRect().height > 0;
          return { overflow:document.documentElement.scrollWidth - innerWidth,
            header:{ logo:rect(document.querySelector('.en-logo')), actions:rect(document.querySelector('.en-actions')) },
            menu:[...document.querySelectorAll('.en-menu > a, .en-menu > details > summary')].filter(visible).map(n => n.textContent.trim()),
            offerCta:rect(document.querySelector('#pass-offer .en-btn-primary')), promo:rect(document.querySelector('#examPromo')),
            jump:[...document.querySelectorAll('#homeJump a')].map(a => ({ href:a.getAttribute('href'), ...rect(a) })),
            order:['pass-offer','examPromo','homeJump','ai-content','review-game','chart-showcase','aiHome','svc-exam'].map(id => rect(document.getElementById(id)).y),
            stage:rect(document.querySelector('#homeStage')), input:rect(document.querySelector('#homeInput')), send:rect(document.querySelector('#homeForm button')) };
        });
        fs.mkdirSync(output, { recursive:true });
        const id = `${width}-${font}`;
        await page.screenshot({ path:path.join(output, `${id}-home.png`), fullPage:false });
        await page.locator('#aiHome').screenshot({ path:path.join(output, `${id}-assistant.png`) });
        if (width === 430 && font === 'fallback') await page.screenshot({ path:path.join(output, `${id}-full.png`), fullPage:true });
        const replies = [];
        for (const question of questions) {
          const before = await page.locator('#homeChat .is-agent').count();
          await page.locator('#homeInput').fill(question);
          await act(page.locator('#homeForm button'));
          await expect(page.locator('#homeChat .is-agent')).toHaveCount(before+1);
          const reply = page.locator('#homeChat .is-agent').last();
          replies.push({ question, text:await reply.locator('p').innerText(), links:await reply.locator('a').evaluateAll(a=>a.map(x=>({ label:x.textContent, href:x.getAttribute('href') }))) });
        }
        await page.locator('#aiHome').screenshot({ path:path.join(output, `${id}-intake.png`) });
        fs.writeFileSync(path.join(output, `${id}.json`), JSON.stringify({ width,height,font,metrics,replies }, null, 2));
        // Soft checks keep every defect and all evidence instead of stopping at the first one.
        expect.soft(metrics.overflow).toBe(0);
        expect.soft(metrics.header.logo.x).toBeGreaterThanOrEqual(0);
        expect.soft(metrics.header.actions.right).toBeLessThanOrEqual(width);
        if (width > 1060) expect.soft(metrics.menu).toEqual(['문법 문제은행', '모의고사위키', '내신 제작소', 'AI AGENT 제작협업실', '더보기']);
        expect.soft(metrics.offerCta.bottom).toBeLessThanOrEqual(height);
        expect.soft(metrics.promo.y).toBeLessThan(height);
        expect.soft(metrics.jump.map(j => j.href)).toEqual(['#ai-content', '#review-game', '#chart-showcase']);
        for (const button of metrics.jump) expect.soft(button.height).toBeGreaterThanOrEqual(44);
        for (let i = 1; i < metrics.order.length; i++) expect.soft(metrics.order[i]).toBeGreaterThan(metrics.order[i-1]);
        expect.soft(metrics.stage.width).toBeLessThanOrEqual(64);
        for (const control of [metrics.input, metrics.send]) expect.soft(control.height).toBeGreaterThanOrEqual(44);
        for (const reply of replies) {
          expect.soft(reply.text).toContain('20,000원');
          expect.soft(reply.text).toContain('카톡·이메일');
          expect.soft(reply.links.map(x=>x.href)).toEqual(expect.arrayContaining([kakao,email]));
        }
        for (const [link, target] of [['#ai-content', '#brandTitle'], ['#review-game', '#reviewGameTitle'], ['#chart-showcase', '#enChartTitle']]) {
          await act(page.locator(`#homeJump a[href="${link}"]`));
          await expect(page.locator(target)).toBeInViewport();
        }
        await expect(page.locator('#enHeroStudio')).toHaveAttribute('href', '/ai-agent-studio');
        await expect(page.locator('#enHeroStudio img')).toBeVisible();
        const submit = page.locator('#enExamCta');
        await expect(submit).toHaveAttribute('href', kakao);
        const popupPromise = page.waitForEvent('popup');
        await act(submit);
        const popup = await popupPromise; await popup.close(); // external document is blocked; never submit an application
        await expect(page.locator('#enExamMail')).toHaveAttribute('href', email);
        await act(page.locator('[data-home-agent="seven"]'));
        await expect(page.locator('#homeName')).toHaveText('세븐');
        await act(page.locator('[data-home-agent="suni"]'));
        await expect(page.locator('#homeName')).toHaveText('서수니');
        await expect(page.locator('[data-home-agent="suni"]')).toHaveAttribute('aria-pressed', 'true');
        expect(errors).toEqual([]);
        expect(externalRequests.some(url=>url===kakao)).toBe(true);
      } finally { await context.close(); }
    });
  }
}
