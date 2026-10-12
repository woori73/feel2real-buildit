// BuildIt 시각 편집기 실제 브라우저 E2E.
//
// 전제: 앱이 BASE_URL(기본 http://localhost:8080)에서 local 프로파일로 떠 있고, dev-seed 계정(tutor/admin1234)과
// 그 학원이 있으며, 그 학원의 시각 편집기 초안·게시본이 비어 있어야 한다(README의 초기화 SQL 참고).
//   npm install && npm run e2e
// 결과는 콘솔에 ✔/✘로 출력되고 하나라도 실패하면 종료 코드 1이다. 스크린샷은 artifacts/ 에 저장된다.
const fs = require('fs');
const { launch, newCtx, collect, BASE, ART, PNG } = require('./lib');

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail });
  console.log((ok ? '  ✔ ' : '  ✘ ') + name + (detail !== undefined && !ok ? '  → ' + JSON.stringify(detail) : ''));
}
const section = (t) => console.log('\n■ ' + t);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 요소를 id로 짝지어 비교할 스타일 시그니처 (캔버스/미리보기/공개 페이지 공통)
const SIG_PROPS = ['display', 'fontSize', 'fontWeight', 'color', 'backgroundColor', 'textAlign', 'lineHeight',
  'marginTop', 'marginBottom', 'paddingTop', 'paddingLeft', 'fontFamily'];
const SIG_FN = `(root) => { const o = {}; root.querySelectorAll('[id]').forEach(e => { if (e.id.startsWith('gjs-')) return; const c = getComputedStyle(e); const s = {}; ${JSON.stringify(SIG_PROPS)}.forEach(p => { s[p] = p === 'fontFamily' ? c[p].split(',')[0].replace(/["']/g, '').trim() : c[p]; }); s.w = Math.round(e.getBoundingClientRect().width); o[e.id] = s; }); return o; }`;
function diffSig(a, b, ignore = []) {
  const diffs = []; let compared = 0;
  Object.keys(a).forEach((id) => {
    if (!b[id]) return;
    compared++;
    Object.keys(a[id]).forEach((k) => { if (!ignore.includes(k) && a[id][k] !== b[id][k]) diffs.push(`${id}.${k}: ${a[id][k]} ≠ ${b[id][k]}`); });
  });
  return { compared, diffs: diffs.slice(0, 6), total: diffs.length };
}

(async () => {
  const browser = await launch();
  const ctx = await newCtx(browser, { viewport: { width: 1440, height: 900 } });
  const api = async (m, u, data) => { const r = await ctx.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const VE = '/api/tutor/academy/page-settings/visual-editor';
  const puts = []; // 편집기가 보낸 draft PUT 기록

  const page = await ctx.newPage();
  const log = collect(page);
  page.on('request', (r) => { if (r.method() === 'PUT' && r.url().includes('/visual-editor/draft')) puts.push(Date.now()); });
  const frame = () => page.frameLocator('.gjs-frame');
  const state = () => page.evaluate(() => window.__ve.state());
  const open = async () => { await page.goto(BASE + '/tutor/academy/editor'); await page.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 }); };
  const compCount = () => page.evaluate(() => window.editor.getComponents().length);

  const me = (await api('GET', '/api/tutor/academy/me')).j;
  const slug = me.slug;
  const st0 = (await api('GET', VE + '/status')).j;
  if (st0.draftExists) { console.error('초안이 이미 있습니다. README의 초기화 SQL을 먼저 실행하세요.'); process.exit(2); }

  // ───────── A. 빈 상태 / 기본 패널 / 변경 감지 ─────────
  section('A. 빈 상태 · GrapesJS 기본 패널 · 변경 없는 페이지의 자동저장/이탈 경고');
  await open();
  check('GrapesJS 기본 패널이 없다(캔버스를 가리지 않음)', (await page.locator('.gjs-pn-panel').count()) === 0);
  check('캔버스가 영역 맨 위부터 시작한다', (await page.evaluate(() => Math.round(document.querySelector('.gjs-cv-canvas').getBoundingClientRect().top - document.querySelector('.editor-canvas-wrap').getBoundingClientRect().top))) === 0);
  check('빈 상태 안내가 오버레이로 보인다', (await page.locator('#emptyState.show').count()) === 1);
  check('안내 문구가 컴포넌트로 들어가지 않는다(컴포넌트 0개)', (await compCount()) === 0);
  check('열자마자 "변경 없음" 상태다', (await state()).hasChanges === false && !(await page.locator('#saveStatus').innerText()).includes('저장되지 않은'));
  check('게시 버튼이 활성화되어 있다(더 이상 "준비 중" 아님)', !(await page.locator('#publishBtn').isDisabled()) && !(await page.locator('#publishBtn').innerText()).includes('준비 중'));
  await page.screenshot({ path: ART + '/after-empty-desktop.png' });
  const putsBefore = puts.length;
  await sleep(33000);
  check('변경 없이 33초 지나도 자동저장 요청이 없다', puts.length === putsBefore, puts.length - putsBefore);
  log.dialogs.length = 0;
  await page.reload({ waitUntil: 'load' });
  await page.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready);
  check('변경 없이 새로고침해도 이탈 경고가 없다', !log.dialogs.some((d) => d.startsWith('beforeunload')), log.dialogs);

  // ───────── B. 프리셋 / 블록 클릭 추가 ─────────
  section('B. 프리셋 5개 · 블록 클릭 추가');
  for (const key of ['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE']) {
    await page.locator('.sidebar-tab button[data-panel="presets"]').click();
    const before = await page.evaluate((k) => window.editor.getHtml().split('data-preset="' + k + '"').length - 1, key);
    await page.locator(`.preset-btn[data-preset="${key}"]`).click();
    await sleep(250);
    const after = await page.evaluate((k) => window.editor.getHtml().split('data-preset="' + k + '"').length - 1, key);
    check(`프리셋 ${key}: 히어로+특징 2개 섹션이 추가된다`, after - before === 2, [before, after]);
  }
  check('프리셋 추가 후 안내 오버레이가 사라진다', (await page.locator('#emptyState.show').count()) === 0);
  await page.locator('.sidebar-tab button[data-panel="blocks"]').click();
  // 선택된 섹션이 있으면 GrapesJS는 그 안에 추가하므로 "본문에 새 블록이 생겼는가"로 확인한다.
  const countText = () => page.evaluate(() => window.editor.getHtml().split('텍스트를 입력하세요').length - 1);
  const t0 = await countText();
  await page.locator('.gjs-block', { hasText: '텍스트' }).first().click();
  await sleep(250);
  check('블록을 클릭만 해도 추가된다(appendOnClick)', (await countText()) === t0 + 1, [t0, await countText()]);
  await sleep(500);
  check('편집하면 "변경 있음"이 된다', (await state()).hasChanges === true);

  // ───────── C. 기존 홈페이지 불러오기 ─────────
  section('C. 기존 홈페이지 불러오기');
  await page.locator('#importBtn').click();   // 내용이 있으므로 confirm(자동 수락)
  await page.waitForFunction(() => document.querySelector('#importBtn') && !document.querySelector('#importBtn').disabled && window.editor.getHtml().includes('id="ve1"'), null, { timeout: 120000 });
  await sleep(800);
  const imp = await page.evaluate(() => ({ sections: window.editor.getComponents().length, cssBytes: new TextEncoder().encode(window.editor.getCss()).length, hasP0Presets: window.editor.getHtml().includes('data-preset'), html: window.editor.getHtml().length }));
  check('P0 섹션을 불러왔다(섹션 ≥ 3, 이전 프리셋 내용은 대체됨)', imp.sections >= 3 && !imp.hasP0Presets, imp);
  check('불러온 스타일이 저장 한도(64KB) 안이다', imp.cssBytes < 65535, imp.cssBytes);
  check('불러오기 직후 "변경 있음"(저장 전)', (await state()).hasChanges === true);
  await page.screenshot({ path: ART + '/after-imported-desktop.png' });

  // ───────── D. 편집 (텍스트 / 스타일 / 이미지 / 삭제) ─────────
  section('D. 편집: 텍스트 · CSS · 이미지 교체 · 구성요소 삭제');
  const h1 = frame().locator('h1').first();
  await h1.scrollIntoViewIfNeeded(); await h1.dblclick(); await page.keyboard.press('Control+A'); await page.keyboard.type('E2E 새 학원명');
  await frame().locator('body').click({ position: { x: 3, y: 3 } }); await sleep(400);
  check('텍스트 수정이 모델에 반영된다', (await page.evaluate(() => window.editor.getHtml())).includes('E2E 새 학원명'));

  await h1.click(); await sleep(300);
  const sec = page.locator('.gjs-sm-sector', { hasText: '타이포그래피' }).first();
  if (!/gjs-sm-open/.test((await sec.getAttribute('class')) || '')) await sec.locator('.gjs-sm-sector-title').click();
  const ci = sec.locator('.gjs-sm-property__color input[type=text]').first();
  await ci.fill('rgb(220, 38, 38)'); await ci.press('Enter'); await sleep(300);
  check('스타일 패널 라벨이 한국어다(글자 색·바깥 여백 등, 영어 Float/Display 아님)', (await page.locator('#gjs-styles').innerText()).includes('글자 색') && (await page.locator('#gjs-styles').innerText()).includes('바깥 여백') && !(await page.locator('#gjs-styles').innerText()).includes('Float'));
  check('스타일 패널로 색을 바꾸면 캔버스에 적용된다', (await h1.evaluate((e) => getComputedStyle(e).color)) === 'rgb(220, 38, 38)');

  const imgCount0 = await page.evaluate(() => window.editor.getWrapper().find('img').length);
  await page.locator('.gjs-block', { hasText: '이미지' }).first().click(); await sleep(400);
  check('이미지 블록 추가(클릭)', (await page.evaluate(() => window.editor.getWrapper().find('img').length)) === imgCount0 + 1);
  const newImg = page.frameLocator('.gjs-frame').locator('img').last();
  await newImg.scrollIntoViewIfNeeded(); await newImg.dblclick(); await sleep(800);
  check('이미지를 더블클릭하면 이미지 선택 창이 열린다', (await page.locator('.gjs-mdl-dialog:visible').count()) === 1);
  fs.writeFileSync(ART + '/upload.png', PNG);
  await page.locator('.gjs-mdl-dialog input[type=file]').first().setInputFiles(ART + '/upload.png'); await sleep(1500);
  await page.locator('.gjs-am-asset').first().dblclick(); await sleep(600);
  const replaced = await page.evaluate(() => { const imgs = window.editor.getWrapper().find('img'); return (imgs[imgs.length - 1].getAttributes().src || '').slice(0, 22); });
  check('이미지 교체: 업로드한 이미지가 src로 들어간다(data:image)', replaced.startsWith('data:image/png'), replaced);

  const secCount0 = await compCount();
  await frame().locator('section').last().scrollIntoViewIfNeeded();
  await frame().locator('section').last().click({ position: { x: 4, y: 4 } }); await sleep(300);
  await page.locator('.gjs-toolbar-item').last().click(); await sleep(400);
  check('구성요소 삭제(툴바 휴지통)', (await compCount()) === secCount0 - 1, [secCount0, await compCount()]);

  // ───────── E. 수동 저장 → 새로고침 유지 ─────────
  section('E. 수동 저장 · 새로고침 후 유지');
  const snap = await page.evaluate(() => ({ html: window.editor.getHtml(), css: window.editor.getCss() }));
  const putP = page.waitForResponse((r) => r.url().includes('/visual-editor/draft') && r.request().method() === 'PUT');
  await page.locator('#saveBtn').click();
  const putR = await putP;
  check('수동 저장: PUT 200 (이전에는 항상 400)', putR.status() === 200, putR.status());
  await sleep(500);
  const afterSave = await state();
  check('저장 후 "변경 없음"으로 돌아간다', afterSave.hasChanges === false, afterSave);
  const srv = (await api('GET', VE + '/draft')).j;
  check('서버 초안 revision이 편집기와 같다', srv.revision === afterSave.currentRevision, [srv.revision, afterSave.currentRevision]);
  check('서버 projectData에 GrapesJS head/docEl이 보존된다', srv.projectData.includes('"head"') && srv.projectData.includes('"docEl"'));
  log.dialogs.length = 0;
  await open();
  const reloaded = await page.evaluate(() => ({ html: window.editor.getHtml(), css: window.editor.getCss() }));
  const norm = (x) => x.replace(/\s+/g, '');
  check('새로고침 후 HTML이 그대로다', norm(reloaded.html) === norm(snap.html));
  check('새로고침 후 CSS가 그대로다', norm(reloaded.css) === norm(snap.css));
  check('새로고침 후 수정한 텍스트·색·이미지가 유지된다', reloaded.html.includes('E2E 새 학원명') && (await frame().locator('h1').first().evaluate((e) => getComputedStyle(e).color)) === 'rgb(220, 38, 38)' && reloaded.html.includes('data:image/png'));
  check('새로고침 직후 "변경 없음"이다', (await state()).hasChanges === false);
  await page.screenshot({ path: ART + '/after-reloaded-desktop.png' });

  // ───────── F. 재불러오기 후 재저장 + 자동저장 ─────────
  section('F. 재불러오기 후 재저장 · 자동저장(30초)');
  const h1b = frame().locator('h1').first();
  await h1b.scrollIntoViewIfNeeded(); await h1b.dblclick(); await page.keyboard.press('End'); await page.keyboard.type(' (자동저장)');
  await frame().locator('body').click({ position: { x: 3, y: 3 } }); await sleep(600);
  check('재불러오기 후 편집하면 "변경 있음"', (await state()).hasChanges === true);
  const pAuto = page.waitForResponse((r) => r.url().includes('/visual-editor/draft') && r.request().method() === 'PUT', { timeout: 45000 });
  const autoRes = await pAuto;
  check('자동저장(30초): PUT 200', autoRes.status() === 200, autoRes.status());
  await sleep(500);
  check('자동저장 후 "변경 없음"', (await state()).hasChanges === false);
  check('서버 초안에 자동저장 내용이 있다', (await api('GET', VE + '/draft')).j.html.includes('(자동저장)'));
  // 수동 재저장(재불러오기 projectData 경로: pages[0].type=main 등)
  await h1b.dblclick(); await page.keyboard.press('End'); await page.keyboard.type('!');
  await frame().locator('body').click({ position: { x: 3, y: 3 } }); await sleep(500);
  const re2 = page.waitForResponse((r) => r.url().includes('/visual-editor/draft') && r.request().method() === 'PUT');
  await page.locator('#saveBtn').click();
  check('재불러오기한 프로젝트의 재저장도 200', (await re2).status() === 200);
  await sleep(500);

  // ───────── G. 미리보기 폭 / 편집 결과 일치 ─────────
  section('G. 미리보기 폭(PC/태블릿/모바일 실제 375px) · 편집·미리보기 일치');
  await page.locator('.btn-device[data-device="Mobile"]').click(); await sleep(700);
  const canvasW = await page.evaluate(() => Math.round(document.querySelector('.gjs-frame').getBoundingClientRect().width));
  check('모바일 디바이스 캔버스 폭이 375px', canvasW === 375, canvasW);
  const canvasMobile = await frame().locator('body').evaluate(new Function('b', `return (${SIG_FN})(b)`));
  await page.locator('#previewBtn').click(); await sleep(1200);
  const pvW = await page.evaluate(() => Math.round(document.getElementById('previewFrame').getBoundingClientRect().width));
  check('모바일 디바이스에서 열린 미리보기 폭이 실제 375px', pvW === 375, pvW);
  const pvMobile = await page.frameLocator('#previewFrame').locator('body').evaluate(new Function('b', `return (${SIG_FN})(b)`));
  const dM = diffSig(canvasMobile, pvMobile);
  check('모바일: 캔버스와 미리보기의 요소별 스타일이 같다', dM.compared > 20 && dM.total === 0, dM);
  await page.screenshot({ path: ART + '/after-preview-mobile.png' });
  await page.locator('#previewModal [data-pv-device="Tablet"]').click(); await sleep(900);
  check('미리보기 태블릿 폭 768px', (await page.evaluate(() => Math.round(document.getElementById('previewFrame').getBoundingClientRect().width))) === 768);
  await page.locator('#previewModal [data-pv-device="Desktop"]').click(); await sleep(900);
  const pvDesktop = await page.frameLocator('#previewFrame').locator('body').evaluate(new Function('b', `return (${SIG_FN})(b)`));
  await page.screenshot({ path: ART + '/after-preview-desktop.png' });
  await page.locator('#closePreview').click();
  await page.locator('.btn-device[data-device="Desktop"]').click(); await sleep(600);
  const canvasDesktop = await frame().locator('body').evaluate(new Function('b', `return (${SIG_FN})(b)`));
  const dD = diffSig(canvasDesktop, pvDesktop, ['w']);
  check('PC: 캔버스와 미리보기의 요소별 스타일이 같다(폭 제외)', dD.compared > 20 && dD.total === 0, dD);

  // ───────── H. 게시 → 공개 페이지 일치 ─────────
  section('H. 게시 · 공개 페이지(PC/모바일) 일치 · P0 CSS 격리');
  const before = await ctx.newPage();
  await before.goto(BASE + '/academies/' + slug); await before.waitForTimeout(3000);
  const p0Probe = await before.evaluate(() => ({ boxSizing: getComputedStyle(document.documentElement).boxSizing, bodyMargin: getComputedStyle(document.body).margin }));
  await before.close();
  await page.locator('#publishBtn').click();
  await page.waitForFunction(() => document.getElementById('publishStatus').textContent.includes('게시됨'), null, { timeout: 30000 });
  await sleep(600);
  const st1 = (await api('GET', VE + '/status')).j;
  check('게시 후 서버 상태: custom · 미게시 변경 없음', st1.renderMode === 'custom' && st1.hasUnpublishedChanges === false, st1);
  check('게시 후 화면 상태 문구가 서버 사실과 같다', (await page.locator('#publishStatus').innerText()).includes('🟢 게시됨'));
  check('게시 취소 버튼과 공개 페이지 링크가 나타난다', !(await page.locator('#unpublishBtn').isHidden()) && !(await page.locator('#publicLink').isHidden()));

  const pubDesktopCtx = await newCtx(browser, { viewport: { width: 1440, height: 900 } });
  const pubPage = await pubDesktopCtx.newPage();
  await pubPage.goto(BASE + '/academies/' + slug); await pubPage.waitForTimeout(3500);
  const pubInfo = await pubPage.evaluate(() => ({ host: !!document.querySelector('#ve-custom-body'), shadow: !!(document.querySelector('#ve-custom-body') && document.querySelector('#ve-custom-body').shadowRoot), leaked: document.querySelectorAll('style[data-visual-editor]').length, boxSizing: getComputedStyle(document.documentElement).boxSizing, bodyMargin: getComputedStyle(document.body).margin }));
  check('공개 페이지가 편집기 콘텐츠를 Shadow DOM으로 격리해 그린다', pubInfo.host && pubInfo.shadow, pubInfo);
  check('편집기 CSS가 문서 전역으로 새지 않는다(P0 GNB·푸터 불변)', pubInfo.leaked === 0 && pubInfo.boxSizing === p0Probe.boxSizing && pubInfo.bodyMargin === p0Probe.bodyMargin, [p0Probe, pubInfo]);
  const pubDesktop = await pubPage.evaluate(new Function(`return (${SIG_FN})(document.querySelector('#ve-custom-body').shadowRoot)`));
  const dPD = diffSig(canvasDesktop, pubDesktop, ['w']);
  check('PC: 편집 캔버스와 공개 페이지의 요소별 스타일이 같다(제목·문단 여백 포함)', dPD.compared > 20 && dPD.total === 0, dPD);
  await pubPage.screenshot({ path: ART + '/after-public-desktop.png', fullPage: true });

  const pubMobileCtx = await newCtx(browser, { viewport: { width: 375, height: 800 }, deviceScaleFactor: 2 });
  const pubM = await pubMobileCtx.newPage();
  await pubM.goto(BASE + '/academies/' + slug); await pubM.waitForTimeout(3500);
  const pubMobile = await pubM.evaluate(new Function(`return (${SIG_FN})(document.querySelector('#ve-custom-body').shadowRoot)`));
  const dPM = diffSig(canvasMobile, pubMobile);
  check('모바일(375px): 캔버스와 공개 페이지의 요소별 스타일·폭이 같다', dPM.compared > 20 && dPM.total === 0, dPM);
  check('모바일 공개 페이지에 가로 넘침이 없다', (await pubM.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)));
  await pubM.screenshot({ path: ART + '/after-public-mobile.png', fullPage: true });
  await pubMobileCtx.close(); await pubDesktopCtx.close();

  // ───────── I. 게시 취소 ─────────
  section('I. 게시 취소 → 공개 페이지가 P0로 복귀');
  await page.locator('#unpublishBtn').click();
  await page.waitForFunction(() => document.getElementById('publishStatus').textContent.includes('미게시'), null, { timeout: 20000 });
  const verCtx = await newCtx(browser, { viewport: { width: 1440, height: 900 } });
  const ver = await verCtx.newPage(); await ver.goto(BASE + '/academies/' + slug); await ver.waitForTimeout(3000);
  check('게시 취소 후 공개 페이지에 편집기 콘텐츠가 없다(P0)', (await ver.locator('#ve-custom-body').count()) === 0);
  await verCtx.close();

  // ───────── J. 모바일 편집기 ─────────
  section('J. 모바일 편집기(390×844, 터치)');
  const mctx = await newCtx(browser, { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
  const mp = await mctx.newPage(); const mlog = collect(mp);
  await mp.goto(BASE + '/tutor/academy/editor'); await mp.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 });
  const lay = await mp.evaluate(() => ({ scrollW: document.documentElement.scrollWidth, innerW: window.innerWidth, toolbarH: Math.round(document.querySelector('.editor-toolbar').getBoundingClientRect().height), canvasW: Math.round(document.querySelector('.gjs-frame').getBoundingClientRect().width), minBtn: Math.min(...Array.from(document.querySelectorAll('.editor-toolbar button')).filter((b) => b.offsetParent).map((b) => Math.round(b.getBoundingClientRect().height))) }));
  check('모바일: 가로 스크롤이 없다', lay.scrollW <= lay.innerW, lay);
  check('모바일: 툴바가 한 줄(높이 ≤ 70px)이다(이전 194px)', lay.toolbarH <= 70, lay.toolbarH);
  check('모바일: 툴바 버튼 터치 영역 ≥ 38px', lay.minBtn >= 38, lay.minBtn);
  check('모바일: 캔버스가 화면 폭을 쓴다', lay.canvasW >= 380, lay.canvasW);
  check('모바일: GrapesJS 기본 패널이 없다', (await mp.locator('.gjs-pn-panel').count()) === 0);
  await mp.screenshot({ path: ART + '/after-editor-mobile.png' });
  await mp.locator('#toggleLeftPanel').tap(); await sleep(400);
  await mp.screenshot({ path: ART + '/after-editor-mobile-blocks.png' });
  const mc0 = await mp.evaluate(() => window.editor.getComponents().length);
  await mp.locator('.gjs-block', { hasText: '히어로 (영어)' }).first().tap(); await sleep(600);
  check('모바일: 블록을 탭하면 추가된다(이전에는 불가)', (await mp.evaluate(() => window.editor.getComponents().length)) > mc0);
  check('모바일: 추가 후 블록 패널이 닫힌다', !(await mp.evaluate(() => document.getElementById('blocksPanel').classList.contains('mobile-open'))));
  await mp.locator('#previewBtn').tap(); await sleep(1200);
  check('모바일: 미리보기가 열리고 모바일 폭(≤375px)', (await mp.evaluate(() => Math.round(document.getElementById('previewFrame').getBoundingClientRect().width))) <= 375);
  await mp.screenshot({ path: ART + '/after-preview-on-phone.png' });
  const mErr = mlog.console.filter((c) => !/ERR_FAILED|ERR_TUNNEL|status of 500/.test(c));
  check('모바일: 콘솔에 앱 오류가 없다', mErr.length === 0 && mlog.pageerrors.length === 0, [mErr, mlog.pageerrors]);
  await mctx.close();

  // ───────── K. 콘솔 오류 ─────────
  section('K. 콘솔');
  const appErr = log.console.filter((c) => !/ERR_FAILED|ERR_TUNNEL|status of 500|status of 400/.test(c));
  check('데스크톱 편집기 콘솔에 앱 오류가 없다', appErr.length === 0 && log.pageerrors.length === 0, [appErr, log.pageerrors]);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n결과: ${results.length - failed.length}/${results.length} 통과, ${failed.length} 실패`);
  fs.writeFileSync(ART + '/e2e-result.json', JSON.stringify({ at: new Date().toISOString(), total: results.length, failed: failed.length, results }, null, 2));
  await browser.close();
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error('E2E 중단:', e); process.exit(2); });
