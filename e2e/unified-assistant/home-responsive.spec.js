const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const phase = process.env.MOBILE_EVIDENCE_PHASE || 'after';
if (!['before', 'after'].includes(phase)) throw new Error('MOBILE_EVIDENCE_PHASE must be before or after');
const output = path.resolve(__dirname, '../../build/verification/home-responsive', phase);
const tel = 'tel:02-6324-1453';
const email = 'mailto:woorinaesin@naver.com';
const questions = ['기말고사 이용권 신청', '시험 범위 제출 방법', '학부모 신청 문의'];
const sizes = [[360, 800], [390, 844], [430, 932], [768, 1024], [1440, 1050]];

for (const font of ['fallback', 'pretendard']) {
  for (const [width, height] of sizes) {
    test(`home ${width}x${height}, ${font}: geometry, compact menu, content order and intake links`, async ({ browser }) => {
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
        await expect(page.locator('#aiHome')).toHaveCount(0); // 홈 AI 비서는 2026-11-15에 내렸다
        await page.evaluate(() => document.fonts.ready);
        if (font === 'pretendard') expect(await page.evaluate(() => [...document.fonts]
          .some(face => face.family.replaceAll('"', '').replaceAll("'", '') === 'Pretendard Variable' && face.status === 'loaded'))).toBe(true);
        const { selectHomePane } = require('./home-navigation.cjs');
        const metrics = await page.evaluate(() => {
          const rect = n => { const r = n.getBoundingClientRect(); return { x:r.x, y:r.y + scrollY, right:r.right, bottom:r.bottom + scrollY }; };
          return { overflow:document.documentElement.scrollWidth - innerWidth,
            logo:rect(document.querySelector('.hl-logo')), actions:rect(document.querySelector('.hl-actions')),
            entries:[...document.querySelectorAll('#study-entry article > .hl-btn')].map(rect), promo:rect(document.querySelector('#examPromo')),
            keys:[...document.querySelectorAll('[data-home-nav]')].map(n=>n.dataset.homeNav) };
        });
        fs.mkdirSync(output, { recursive:true });
        const id = `${width}-${font}`;
        await page.screenshot({ path:path.join(output, `${id}-home.png`), fullPage:false });
        expect(metrics.overflow).toBe(0);
        expect(metrics.logo.x).toBeGreaterThanOrEqual(0);
        expect(metrics.logo.right).toBeLessThanOrEqual(metrics.actions.x);
        expect(metrics.actions.right).toBeLessThanOrEqual(width);
        expect(metrics.entries).toHaveLength(2);
        expect(await page.locator('#study-entry article > a:last-child').count()).toBe(2);
        expect(Math.abs(metrics.entries[0].y - metrics.entries[1].y)).toBeLessThan(1);
        expect(Math.abs(metrics.entries[0].bottom - metrics.entries[1].bottom)).toBeLessThan(1);
        for (const entry of metrics.entries) {
          expect(entry.y).toBeGreaterThanOrEqual(0);
          expect(entry.bottom).toBeLessThanOrEqual(height);
        }
        // 이용자별 두 진입 버튼이 첫 화면에 함께 보인다. 배너·홍보 영상은 ⓪ 대시보드 아래에 이어진다.
        expect(metrics.promo.y).toBeGreaterThan(0);
        // 2026-11-23: 맞춤문제(request·guide) 대신 서비스 도입 문의(contact)
        expect(metrics.keys).toEqual(['home','dashboard','wiki','contact','agent','game','chart']);
        await expect(page.locator('#homePaneTitle')).toHaveText('F2R AI 코디 · F2R 영어 교습소 — 휴대폰 하나로 학원 업무를 AI에게, 압구정중·신구중 영어 내신');
        await expect(page.locator('#homeMenuPanel')).toBeHidden();
        // 2026-11-21: 상단은 펼침 메뉴 6묶음(1320px 초과에서 보임), 그 이하는 바로가기 줄.
        await expect(page.locator('.hl-links')).toBeVisible({ visible: width > 1320 });
        await expect(page.locator('.hl-chips')).toBeVisible({ visible: width <= 1320 });
        await expect(page.locator('#homeNotice')).toBeVisible();
        await expect(page.locator('#homeNotice .home-notice-group').first()).toContainText('압구정중3 기출문제 적중률 분석 리포트');
        await expect(page.locator('#homeNotice .home-notice-group').first()).toContainText('모의고사 위키 지문 복습 문제 업로드');
        await page.locator('#homeNoticePause').click();
        await expect(page.locator('#homeNoticePause')).toHaveAttribute('aria-pressed','true');
        await expect(page.locator('#homeNoticeTrack')).toHaveClass(/is-paused/);
        await page.locator('#homeNoticePause').click();
        await expect(page.locator('#homeNoticeTrack')).toHaveCSS('animation-play-state','running');
        // 전체 메뉴: 모든 화면에서 같은 버튼으로 열고, Esc로 닫으면 메뉴 버튼으로 포커스가 돌아온다.
        await page.locator('#enBurger').click();
        await expect(page.locator('#homeMenuPanel')).toBeVisible();
        await expect(page.locator('#enBurger')).toHaveAttribute('aria-expanded','true');
        await expect(page.locator('#homeMenuClose')).toBeFocused();
        await page.screenshot({path:path.join(output,`${id}-menu.png`),fullPage:false});
        expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
        await page.keyboard.press('Escape');
        await expect(page.locator('#homeMenuPanel')).toBeHidden();
        await expect(page.locator('#enBurger')).toBeFocused();
        for (const [key,target] of [['contact','#contact'],['game','#review-game'],['agent','#ai-content'],['chart','#chart-showcase']]) {
          await selectHomePane(page,key);
          await expect(page.locator(target)).toBeInViewport();
          expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBe(0);
        }
        const replies = [];
        // 2026-11-23 대표 지시: 맞춤문제 신청은 지우고 「서비스 도입 문의」를 누르면 전화·이메일로 연결한다.
        await selectHomePane(page,'contact');
        await expect(page.locator('#contact a[href="' + tel + '"]')).toBeVisible();
        await expect(page.locator('#contact a[href="' + email + '"]').first()).toBeVisible();
        await expect(page.locator('#homeIntakeCta, #request, #exam-payment')).toHaveCount(0);
        await selectHomePane(page,'home');
        for (const [link,target] of [['a[href="#contact"]:visible >> nth=0','#contactTitle'],['.hl-fab-cta','#contactTitle']]) {
          await act(page.locator(link));
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
  await page.goto('/#contact');
  await expect(page.locator('#contact')).toBeInViewport();
  await page.reload();
  await expect(page.locator('#contact')).toBeInViewport();
  const {selectHomePane}=require('./home-navigation.cjs');
  await selectHomePane(page,'chart');
  expect(await page.evaluate(()=>location.hash)).toBe('#chart-showcase');
  await page.goBack();
  expect(await page.evaluate(()=>location.hash)).toBe('#contact');
  // A link to a closed 적중 사례 item opens it on arrival.
  await page.goto('/#hitCompare');
  await expect(page.locator('#hitCompare')).toHaveAttribute('open','');
  expect(await page.locator('#homeNoticeTrack').evaluate(e=>getComputedStyle(e).animationName)).toBe('none');
  await context.close();
  const fallback=await browser.newContext({javaScriptEnabled:false,viewport:{width:390,height:844}});
  await fallback.route('**/*',localOnly);
  const plain=await fallback.newPage();
  await plain.goto('/');
  for(const id of ['pass-offer','digital-exam','counsel','ai-content','hitCase','chart-showcase','review-game','ebook','contact']) await expect(plain.locator('#'+id)).toBeVisible();
  await expect(plain.locator('.hl-chips')).toBeVisible();
  await expect(plain.locator('#homeMenuPanel')).toBeHidden();
  await fallback.close();
});

test('rotating hero supports tile selection, pause, service links, equal heights and reduced motion', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  await page.goto('/');
  const hero = page.locator('#homeHero');
  await expect(hero.locator('[data-hero-slide]:visible')).toHaveCount(1);
  await expect(page.locator('#heroPause')).toHaveAttribute('aria-pressed', 'true');
  const heights = await hero.locator('[data-hero-slide]').evaluateAll(slides => slides.map(s => Math.round(s.getBoundingClientRect().height)));
  expect(new Set(heights).size).toBe(1);
  // 2026-11-23 대표 지시: 배너는 디지털 기말고사 1장(위키·복습 게임·AI AGENT·전자책, 이어서 맞춤문제 장과 타일 삭제)
  await expect(hero.locator('[data-hero-slide]')).toHaveCount(1);
  await expect(page.locator('[data-hero-go]')).toHaveCount(0);
  await expect(hero).toHaveAttribute('data-active', '0');
  await expect(hero.getByRole('link', { name: '시험 보러 가기' })).toHaveAttribute('href', '/naesintest');
  await expect(page.locator('#hwAdopt')).toBeVisible();
  for (const img of await hero.locator('.hero-art img').all()) expect(await img.evaluate(i => i.complete && i.naturalWidth > 0)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
  await context.close();
});
