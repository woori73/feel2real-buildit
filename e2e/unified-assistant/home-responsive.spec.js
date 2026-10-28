const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const phase = process.env.MOBILE_EVIDENCE_PHASE || 'after';
if (!['before', 'after'].includes(phase)) throw new Error('MOBILE_EVIDENCE_PHASE must be before or after');
const output = path.resolve(__dirname, '../../build/verification/home-responsive', phase);
const kakao = 'https://open.kakao.com/o/sMf4ifRi';
const email = 'mailto:woorinaesin@naver.com';
const titles = ['기말고사 이용권', '맞춤 문제 신청', '시험 범위 제출', '개별 신청', '학부모·학생 신청', '접수 방법'];
const sizes = [[360, 800], [390, 844], [430, 932], [768, 1024], [1440, 1050]];

for (const font of ['fallback', 'pretendard']) {
  for (const [width, height] of sizes) {
    test(`home ${width}x${height}, ${font}: geometry, all six menus, intake links and assistant`, async ({ browser }) => {
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
          const rect = node => { const r = node.getBoundingClientRect(); return { x:r.x, y:r.y, width:r.width, height:r.height, right:r.right, bottom:r.bottom }; };
          const stage = document.querySelector('#homeStage'), name = document.querySelector('.stage-bottom');
          const text = node => { const r = document.createRange(); r.selectNodeContents(node); return rect(r); };
          return { command:rect(document.querySelector('#homeConsole')), stage:rect(stage), caption:rect(name), name:text(document.querySelector('#homeName')),
            subtitle:text(name.querySelector('span')), rows:getComputedStyle(stage).gridTemplateRows,
            overflow:document.documentElement.scrollWidth - innerWidth,
            header:{ logo:rect(document.querySelector('.en-logo')), actions:rect(document.querySelector('.en-actions')) },
            buttons:[...document.querySelectorAll('.si-holo-tile')].map(button => {
              const r=rect(button), hit=document.elementFromPoint(r.x+r.width/2, r.y+r.height/2);
              return { title:button.querySelector('b').textContent, intent:button.dataset.homeIntent,
                ...r, hittable:!!hit && (hit===button || button.contains(hit)), transform:getComputedStyle(button).transform };
            }) };
        });
        fs.mkdirSync(output, { recursive:true });
        const id = `${width}-${font}`;
        await page.screenshot({ path:path.join(output, `${id}-home.png`), fullPage:false });
        await page.locator('#homeStage').screenshot({ path:path.join(output, `${id}-stage.png`) });
        if (width === 430 && font === 'fallback') await page.screenshot({ path:path.join(output, `${id}-full.png`), fullPage:true });
        const replies = [];
        for (const title of titles) {
          const before = await page.locator('#homeChat .is-agent').count();
          await act(page.locator('.si-holo-tile').filter({ has:page.locator('b', { hasText:new RegExp('^'+title+'$') }) }));
          await expect(page.locator('#homeChat .is-agent')).toHaveCount(before+1);
          const reply = page.locator('#homeChat .is-agent').last();
          replies.push({ title, text:await reply.locator('p').innerText(), links:await reply.locator('a').evaluateAll(a=>a.map(x=>({ label:x.textContent, href:x.getAttribute('href') }))) });
        }
        await page.screenshot({ path:path.join(output, `${id}-intake.png`), fullPage:false });
        fs.writeFileSync(path.join(output, `${id}.json`), JSON.stringify({ width,height,font,metrics,replies }, null, 2));
        // Soft checks retain every baseline defect and all evidence, rather than stopping at the first tile.
        expect.soft(metrics.overflow).toBe(0);
        expect.soft(metrics.header.logo.x).toBeGreaterThanOrEqual(0);
        expect.soft(metrics.header.actions.right).toBeLessThanOrEqual(width);
        for (const text of [metrics.name, metrics.subtitle]) expect.soft(text.bottom).toBeLessThanOrEqual(metrics.stage.bottom-1);
        for (const button of metrics.buttons) {
          expect.soft(button.bottom).toBeLessThanOrEqual(metrics.command.bottom+1);
          expect.soft(button.height).toBeGreaterThanOrEqual(44);
          expect.soft(button.width).toBeGreaterThanOrEqual(44);
          // Compact mobile cards sit below the character; Playwright tap above verifies they remain hittable after scrolling.
        }
        for (const reply of replies) {
          expect.soft(reply.text).toContain('20,000원');
          expect.soft(reply.text).toContain('카톡·이메일');
          expect.soft(reply.links.map(x=>x.href)).toEqual(expect.arrayContaining([kakao,email]));
        }
        await expect(page.locator('#enHeroStudio')).toHaveAttribute('href', '/ai-agent-studio');
        await expect(page.locator('#enHeroStudio img')).toBeVisible();
        const submit = page.locator('#enExamCta');
        await expect(submit).toHaveAttribute('href', kakao);
        const popupPromise = page.waitForEvent('popup');
        await act(submit);
        const popup = await popupPromise; await popup.close(); // external document is blocked; never submit an application
        await expect(page.locator('#enExamMail')).toHaveAttribute('href', email);
        const beforeInputReply = await page.locator('#homeChat .is-agent').count();
        await page.locator('#homeInput').fill('기말고사 이용권 신청');
        await act(page.locator('#homeForm button'));
        await expect(page.locator('#homeChat .is-agent')).toHaveCount(beforeInputReply+1);
        await expect(page.locator('#homeChat .is-agent').last()).toContainText('160문항');
        await act(page.locator('[data-home-agent="suni"]'));
        await expect(page.locator('#homeName')).toHaveText('서수니');
        await expect(page.locator('[data-home-agent="suni"]')).toHaveAttribute('aria-pressed', 'true');
        await act(page.locator('#homeWelcomeAgain'));
        await expect(page.locator('#homeChat .is-agent').last()).toContainText('160문항');
        await act(page.locator('#homeChat .is-agent').last().getByRole('button', { name:'나중에', exact:true }));
        await expect(page.locator('#homeChat .is-agent').last()).toContainText('필요할 때');
        await act(page.locator('#homeWelcomeAgain'));
        await expect(page.locator('#homeChat .is-agent').last().getByRole('button', { name:'나중에', exact:true })).toBeVisible();
        expect(errors).toEqual([]);
        expect(externalRequests.some(url=>url===kakao)).toBe(true);
      } finally { await context.close(); }
    });
  }
}
