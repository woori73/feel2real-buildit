// AI 최초 생성 → 시각 편집 → AI 수정 제안 → 승인 → 저장 → 게시 → 공개 화면 (실제 서버·DB·브라우저).
//   node e2e-ai-create.js [desktop|mobile]   (mobile = 390px 터치)
// 전제: e2e.js와 같다(앱 local 프로파일, tutor/admin1234, 초안·게시본 비어 있음).
const { launch, newCtx, BASE, ART } = require('./lib');
const MODE = process.argv[2] === 'mobile' ? 'mobile' : 'desktop';
const results = [];
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail });
  console.log((ok ? '  ✔ ' : '  ✘ ') + name + (detail !== undefined && !ok ? '  → ' + JSON.stringify(detail) : ''));
}
const section = (t) => console.log('\n■ ' + t);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const VE = '/api/tutor/academy/page-settings/visual-editor';
const AG = BASE + '/api/agent/academy/visual-editor';

(async () => {
  const browser = await launch();
  const mobile = MODE === 'mobile';
  const ctx = await newCtx(browser, mobile ? { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true } : { viewport: { width: 1440, height: 900 } });
  const api = async (m, u, data) => { const r = await ctx.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const agent = async (token, m, path, body) => {
    const r = await fetch(AG + path, { method: m, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status, j };
  };
  const slug = (await api('GET', '/api/tutor/academy/me')).j.slug;
  if ((await api('GET', VE + '/status')).j.draftExists) { console.error('초안이 이미 있습니다. 초기화 SQL을 먼저 실행하세요.'); process.exit(2); }
  const pubApi = async () => await (await fetch(BASE + '/api/academies/' + slug)).text();
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)));
  page.on('dialog', (d) => d.accept().catch(() => {}));
  const tap = async (sel) => (mobile ? page.locator(sel).tap() : page.locator(sel).click());
  const state = () => page.evaluate(() => window.__ve.state());
  const html = () => page.evaluate(() => window.editor.getHtml());
  const open = async () => { await page.goto(BASE + '/tutor/academy/editor'); await page.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 }); };
  const pubBefore = await pubApi();

  section(`A. [${MODE}] 빈 편집기에서 "AI로 처음 만들기"`);
  await open();
  check('빈 상태 안내에 "AI로 처음 만들기" 버튼이 보인다', await page.locator('#emptyAiBtn').isVisible());
  await tap('#emptyAiBtn');
  await page.waitForSelector('#aiPanel:not([hidden]) #aiCreateBox[open]');
  await page.fill('#aiCreateMsg', '강남에서 중학생 영어 내신을 가르치는 소수정예 학원입니다. 원어민 수업과 1:1 첨삭이 강점이에요.');
  await tap('#aiCreateBtn');
  await page.waitForSelector('#previewModal #previewProposalBar:not([hidden])', { timeout: 30000 });
  const frameText = await page.frameLocator('#previewFrame').locator('body').innerText();
  check('미리보기에 AI가 만든 문구(학원 소개/특징)가 보인다', frameText.length > 40 && frameText.includes('상담 신청하기'), frameText.slice(0, 160));
  check('승인 전: 편집기는 비어 있고 서버 초안·공개 응답은 그대로다', (await page.evaluate(() => window.editor.getComponents().length)) === 0 && !(await api('GET', VE + '/status')).j.draftExists && (await pubApi()) === pubBefore);
  await page.screenshot({ path: ART + `/ai-create-preview-${MODE}.png` });
  if (mobile) {
    const pw = await page.evaluate(() => document.getElementById('previewFrame').getBoundingClientRect().width);
    check('모바일: 미리보기가 화면 폭(390px) 안이다', pw <= 390, pw);
  }

  section(`B. [${MODE}] 승인 → 저장 → 새로고침 후 유지`);
  await tap('#previewProposalBar button.approve');
  for (let i = 0; i < 40; i++) { if (((await api('GET', VE + '/draft')).j.html || '').includes('hero-title')) break; await sleep(500); }
  await page.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  const saved = (await api('GET', VE + '/draft')).j;
  check('서버 초안에 AI 초안(고정 id 요소)이 저장된다', saved.html.includes('hero-title') && saved.html.includes('feature1-desc') && saved.html.includes('상담 신청하기'), saved.html.slice(0, 120));
  check('프리셋 스타일(색·배경·카드)이 CSS로 함께 저장된다', saved.css.length > 500 && /background/.test(saved.css) && /border-radius/.test(saved.css), saved.css.length);
  check('승인 전과 같이 공개 응답은 아직 그대로(게시 전)', (await pubApi()) === pubBefore);
  await open();
  check('새로고침 후에도 유지된다', (await html()).includes('hero-title') && (await state()).hasChanges === false);

  section(`B2. [${MODE}] 직접 시각 편집`);
  await page.evaluate(() => { window.editor.getWrapper().find('#hero-sub')[0].components([{ type: 'textnode', content: '직접 고친 한 줄 소개' }]); });
  await sleep(700);
  await tap('#saveBtn');
  await page.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  check('직접 수정이 저장된다', (await api('GET', VE + '/draft')).j.html.includes('직접 고친 한 줄 소개'));

  section(`C. [${MODE}] 대화 수정: AI 수정 제안(Agent) → 미리보기 → 승인`);
  const token = (await api('POST', VE + '/agent-tokens', { label: 'create-e2e' })).j.token;
  const ctxRes = await agent(token, 'GET', '/context');
  check('Agent 컨텍스트에 AI가 만든 요소 id가 보인다', ctxRes.s === 200 && JSON.stringify(ctxRes.j.components).includes('hero-title'), ctxRes.s);
  const prop = await agent(token, 'POST', '/proposals', { baseRevision: ctxRes.j.revision, summary: '대표 문구 강조', changes: [
    { componentId: 'hero-title', type: 'text', after: '영어는 우리 학원에서 시작하세요' },
    { componentId: 'hero-title', type: 'style', styles: { color: 'rgb(220, 38, 38)' } } ] });
  check('제안이 PENDING으로 생성된다', prop.s === 201, prop);
  await open();
  await tap('#aiBtn');
  await page.waitForSelector('.ai-card');
  await tap('.ai-card button[data-act="preview"]');
  await page.waitForSelector('#previewProposalBar:not([hidden])');
  check('제안 미리보기에 수정 문구가 보인다', (await page.frameLocator('#previewFrame').locator('body').innerText()).includes('영어는 우리 학원에서 시작하세요'));
  check('미리보기 중 편집기 본 내용은 그대로다', !(await html()).includes('영어는 우리 학원에서 시작하세요'));
  await tap('#previewProposalBar button.approve');
  await page.waitForFunction(() => window.__ve.state().pendingAppliedProposalId === null && !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  check('승인되어 저장되고 서버에서 APPLIED', (await api('GET', VE + '/draft')).j.html.includes('영어는 우리 학원에서 시작하세요') && (await agent(token, 'GET', '/proposals/' + prop.j.proposalId)).j.status === 'APPLIED');

  section(`D. [${MODE}] 게시 → 공개 화면`);
  await open();
  await tap('#publishBtn');
  await page.waitForFunction(() => document.getElementById('publicLink') && !document.getElementById('publicLink').hidden, null, { timeout: 15000 });
  await sleep(800);
  const pub = (await api('GET', '/api/academies/' + slug + '/homepage')).j;
  const pubText = JSON.stringify(pub);
  check('공개 홈페이지에 AI 생성·직접 수정·AI 수정 제안이 모두 반영된다', pubText.includes('영어는 우리 학원에서 시작하세요') && pubText.includes('직접 고친 한 줄 소개') && pubText.includes('feature1-desc'), pubText.slice(0, 160));
  const pubPage = await page.context().newPage();
  await pubPage.goto(BASE + '/academies/' + slug);
  await sleep(1500);
  const pubText2 = await pubPage.evaluate(() => { let t = document.body.innerText; document.querySelectorAll('*').forEach((e) => { if (e.shadowRoot) t += ' ' + e.shadowRoot.textContent; }); return t; });
  check('공개 페이지 화면(Shadow DOM 포함)에서 문구가 보인다', pubText2.includes('영어는 우리 학원에서 시작하세요') && pubText2.includes('직접 고친 한 줄 소개'), pubText2.slice(0, 120));
  await pubPage.screenshot({ path: ART + `/ai-create-public-${MODE}.png` });
  check('페이지 오류가 없다', errors.length === 0, errors);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n결과: ${results.length - failed.length}/${results.length} 통과`);
  require('fs').writeFileSync(ART + `/e2e-ai-create-${MODE}-result.json`, JSON.stringify(results, null, 2));
  await browser.close();
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
