/* axe-core 접근성 감사(PC 1440 / 모바일 390 / 움직임 감소). 사용법: node a11y-home-3d.js <baseUrl> — 필요: playwright-core, axe-core */
const { chromium } = require('playwright-core');
const fs = require('fs');
const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const EXE = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const AXE = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
(async () => {
  const b = await chromium.launch({ executablePath: EXE, args: ['--no-sandbox', '--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  let total = 0;
  for (const [name, opts] of [['PC 1440', { viewport: { width: 1440, height: 900 } }], ['모바일 390', { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true }], ['PC 1440 움직임 감소', { viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' }]]) {
    const ctx = await b.newContext(opts); const p = await ctx.newPage();
    await p.goto(BASE + '/', { waitUntil: 'load' });
    await p.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 500) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 120)); } window.scrollTo(0, 0); });
    await p.waitForTimeout(1200);
    await p.evaluate(AXE);
    const r = await p.evaluate(() => axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }));
    const mine = r.violations.map((v) => ({ id: v.id, impact: v.impact, n: v.nodes.length, f3d: v.nodes.some((n) => /f3d-/.test(n.html)), sample: v.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' | ') }));
    console.log(`\n[${name}] 위반 ${r.violations.length}종 / 통과 ${r.passes.length}종 / 검토필요 ${r.incomplete.length}종`);
    mine.forEach((m) => console.log(`  - ${m.id} (${m.impact}) x${m.n} ${m.f3d ? '[3D 요소 포함]' : '[기존 요소]'} ${m.sample}`));
    total += mine.filter((m) => m.f3d).length;
    await ctx.close();
  }
  await b.close();
  console.log(`\n3D 요소가 포함된 위반: ${total}종`);
  process.exit(total ? 1 : 0);
})();
