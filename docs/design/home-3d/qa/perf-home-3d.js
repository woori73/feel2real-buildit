/* 홈 성능 측정(LCP·CLS·FCP·롱태스크·3D 자산 전송량). 사용법: node perf-home-3d.js <baseUrl> [label]
 * 소프트웨어 렌더링(SwiftShader) 환경이라 GPU 시간은 실기기와 다르다. 비교용 상대값으로 본다. */
const { chromium } = require('playwright-core');
const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const EXE = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const ARGS = ['--no-sandbox', '--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'];
const init = () => {
  window.__m = { lcp: 0, cls: 0, longMs: 0, longN: 0 };
  new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__m.lcp = e.startTime; }).observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) window.__m.cls += e.value; }).observe({ type: 'layout-shift', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) { window.__m.longMs += Math.max(0, e.duration - 50); window.__m.longN++; } }).observe({ type: 'longtask', buffered: true });
};
async function run(browser, name, opts, cpu) {
  const ctx = await browser.newContext(opts);
  const p = await ctx.newPage();
  await p.addInitScript(init);
  if (cpu) { const c = await ctx.newCDPSession(p); await c.send('Emulation.setCPUThrottlingRate', { rate: cpu }); }
  await p.goto(BASE + '/', { waitUntil: 'load' });
  await p.waitForTimeout(3500);
  const r = await p.evaluate(() => {
    const m = window.__m;
    const nav = performance.getEntriesByType('navigation')[0];
    const fcp = (performance.getEntriesByName('first-contentful-paint')[0] || {}).startTime || 0;
    const f3d = performance.getEntriesByType('resource').filter((e) => /landing_3d/.test(e.name)).map((e) => ({ n: e.name.split('/').pop().split('?')[0], t: e.transferSize, d: e.decodedBodySize }));
    return { fcp, lcp: m.lcp, cls: m.cls, tbtMs: m.longMs, longTasks: m.longN, load: nav.loadEventEnd, f3d };
  });
  const frames = await p.evaluate(() => new Promise((res) => { let n = 0; const t0 = performance.now(); (function f() { n++; if (performance.now() - t0 < 1500) requestAnimationFrame(f); else res(n / 1.5); })(); }));
  console.log(JSON.stringify({ name, fcp: Math.round(r.fcp), lcp: Math.round(r.lcp), cls: +r.cls.toFixed(4), tbtMs: Math.round(r.tbtMs), longTasks: r.longTasks, loadMs: Math.round(r.load), fps: Math.round(frames), f3dAssets: r.f3d }));
  await ctx.close();
}
(async () => {
  const browser = await chromium.launch({ executablePath: EXE, args: ARGS });
  await run(browser, 'desktop-1440', { viewport: { width: 1440, height: 900 } });
  await run(browser, 'desktop-1440-reduced-motion(static)', { viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
  await run(browser, 'mobile-390-cpu4x', { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true }, 4);
  await browser.close();
})();
