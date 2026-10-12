// AI Agent 제안 전체 흐름 E2E (실제 서버·실제 DB·실제 브라우저, 응답 모킹 없음).
//   Agent 토큰 발급(UI) → Agent가 제안 생성(Bearer API) → 편집기 배지/패널 → 미리보기(본 편집기·공개 페이지 불변)
//   → 승인(적용·저장) → 새로고침 후 유지 → 게시해야 공개 페이지 반영 → 거절 → AI 공급자 실패 시 무해 → 모바일 390px
// 전제: e2e.js와 같다(앱 local 프로파일, tutor/admin1234, 초안·게시본 비어 있음).
const { launch, newCtx, BASE, ART } = require('./lib');

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
  const ctx = await newCtx(browser, { viewport: { width: 1440, height: 900 } });
  const api = async (m, u, data) => { const r = await ctx.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const agent = async (token, m, path, body) => {
    const r = await fetch(AG + path, { method: m, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ }
    return { s: r.status, j };
  };
  const me = (await api('GET', '/api/tutor/academy/me')).j;
  const slug = me.slug;
  if ((await api('GET', VE + '/status')).j.draftExists) { console.error('초안이 이미 있습니다. README의 초기화 SQL을 먼저 실행하세요.'); process.exit(2); }
  const publicHtml = async () => (await (await fetch(BASE + '/academies/' + slug)).text()).replace(/\s+/g, ' ');
  const publicApi = async () => await (await fetch(BASE + '/api/academies/' + slug)).text();

  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)));
  let dialogAnswer = 'accept';
  page.on('dialog', (d) => (dialogAnswer === 'accept' ? d.accept() : d.dismiss()).catch(() => {}));
  const state = () => page.evaluate(() => window.__ve.state());
  const open = async () => { await page.goto(BASE + '/tutor/academy/editor'); await page.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 }); };
  const html = () => page.evaluate(() => window.editor.getHtml());

  section('A. 준비: 편집기에서 홈페이지를 만들고 저장');
  await open();
  await page.evaluate(() => {
    window.editor.setComponents('<section id="hero"><h1 id="title">우리 학원</h1><p id="intro">기본 소개 문구입니다</p><img id="photo" src="https://via.placeholder.com/10x10"></section>');
  });
  await sleep(700);
  await page.locator('#saveBtn').click();
  await page.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  const pubBefore = await publicHtml();
  const pubApiBefore = await publicApi();
  const draft0 = (await api('GET', VE + '/draft')).j;
  check('초안이 저장되었다', draft0.html.includes('우리 학원'));

  section('B. Agent 토큰을 UI에서 발급하고, Agent가 Bearer로 제안을 만든다');
  await page.locator('#aiBtn').click();
  await page.waitForSelector('#aiPanel:not([hidden])');
  await page.locator('.ai-agent summary').click();
  await page.fill('#aiTokenLabel', 'E2E 에이전트');
  await page.locator('#aiTokenNew').click();
  await page.waitForFunction(() => { const t = document.getElementById('aiTokenShow'); return t && !t.hidden && t.value.startsWith('vea_'); });
  const token = await page.inputValue('#aiTokenShow');
  check('토큰이 vea_ 평문으로 한 번 표시된다', token.startsWith('vea_') && token.length > 40);
  const ctxRes = await agent(token, 'GET', '/context');
  check('Agent가 컨텍스트에서 id 있는 요소를 본다', ctxRes.s === 200 && JSON.stringify(ctxRes.j.components).includes('title'), ctxRes.s);
  const badTok = await agent('vea_wrong', 'GET', '/context');
  check('잘못된 토큰은 거부된다', badTok.s === 401 || badTok.s === 403, badTok.s);
  const evil = await agent(token, 'POST', '/proposals', { summary: 'x', changes: [{ componentId: 'title', type: 'text', after: '<script>alert(1)</script>' }] });
  check('위험한 제안 입력은 400', evil.s === 400, evil.s);
  const prop = await agent(token, 'POST', '/proposals', {
    baseRevision: ctxRes.j.revision, summary: '제목·소개·사진·스타일 개선',
    changes: [
      { componentId: 'title', type: 'text', after: '영어는 우리 학원' },
      { componentId: 'intro', type: 'text', after: '1:1 맞춤 지도로 성적을 올립니다' },
      { componentId: 'photo', type: 'image', after: 'https://via.placeholder.com/30x30' },
      { componentId: 'title', type: 'style', styles: { color: 'rgb(220, 38, 38)' } },
      { componentId: 'title', type: 'style', styles: { 'font-size': '22px' }, mediaText: '(max-width: 768px)' },
    ],
  });
  check('제안이 PENDING으로 만들어진다', prop.s === 201 && prop.j.status === 'PENDING', prop);
  const pid = prop.j.proposalId;

  section('C. 승인 전: 편집기·초안·공개 페이지는 그대로이고 배지/패널에 제안이 보인다');
  check('제안 생성만으로 초안은 바뀌지 않는다', (await api('GET', VE + '/draft')).j.revision === draft0.revision);
  check('공개 페이지(HTML/API)가 그대로다', (await publicHtml()) === pubBefore && (await publicApi()) === pubApiBefore);
  await open();
  await page.waitForSelector('#aiBadge:not([hidden])', { timeout: 10000 }).catch(() => {});
  check('편집기 배지에 대기 제안 1건', (await page.locator('#aiBadge').isVisible()) && (await page.locator('#aiBadge').innerText()) === '1');
  await page.locator('#aiBtn').click();
  await page.waitForSelector('.ai-card');
  const cardText = await page.locator('.ai-card').innerText();
  check('패널에 요약·변경 전/후가 보인다', cardText.includes('제목·소개·사진·스타일 개선') && cardText.includes('우리 학원') && cardText.includes('영어는 우리 학원'), cardText.slice(0, 200));
  await page.screenshot({ path: ART + '/agent-panel-desktop.png' });

  section('D. 미리보기: 제안 적용 후 모습을 보여주되 편집기 내용은 바뀌지 않는다');
  await page.locator('.ai-card button[data-act="preview"]').click();
  await page.waitForSelector('#previewModal #previewProposalBar:not([hidden])');
  const frameText = await page.frameLocator('#previewFrame').locator('body').innerText();
  check('미리보기에 제안이 적용된 문구가 보인다', frameText.includes('영어는 우리 학원') && frameText.includes('맞춤 지도'), frameText.slice(0, 120));
  check('미리보기 중에도 편집기 본 내용은 그대로', (await html()).includes('우리 학원') && !(await html()).includes('영어는 우리 학원') && (await state()).hasChanges === false);
  check('미리보기 중 pending 상태가 아니다(아직 승인 안 됨)', (await state()).pendingAppliedProposalId === null);
  await page.screenshot({ path: ART + '/agent-preview-desktop.png' });
  await page.locator('#previewModal #closePreview').click();
  check('미리보기를 닫아도 변경이 없다', (await state()).hasChanges === false);

  section('E. 승인(적용·저장) → 새로고침 후 유지');
  await page.locator('.ai-card button[data-act="approve"]').click();
  await page.waitForFunction(() => window.__ve.state().pendingAppliedProposalId === null && !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  const saved = (await api('GET', VE + '/draft')).j;
  check('초안에 제안 내용(텍스트·이미지·CSS)이 저장된다', saved.html.includes('영어는 우리 학원') && saved.html.includes('30x30') && saved.css.includes('rgb(220, 38, 38)') && saved.css.includes('22px'), saved.css.slice(0, 200));
  const after = await agent(token, 'GET', '/proposals/' + pid);
  check('서버에서 제안이 APPLIED로 기록된다(DB 기준 revision)', after.j.status === 'APPLIED', after.j.status);
  check('승인 전 공개 페이지는 아직 그대로다(게시 전)', (await publicHtml()) === pubBefore && (await publicApi()) === pubApiBefore);
  await open();
  check('새로고침 후에도 승인된 내용이 편집기에 유지된다', (await html()).includes('영어는 우리 학원') && (await html()).includes('1:1 맞춤'));
  check('새로고침 후 변경 없음 상태이고 제안 배지가 없다', (await state()).hasChanges === false && !(await page.locator('#aiBadge').isVisible()));

  section('F. 게시해야 공개 페이지에 반영된다');
  await page.locator('#publishBtn').click();
  await page.waitForFunction(() => document.getElementById('publicLink') && !document.getElementById('publicLink').hidden, null, { timeout: 15000 });
  await sleep(800);
  const pubAfter = await publicHtml();
  check('게시 후 공개 페이지에 승인된 문구가 보인다', pubAfter.includes('영어는 우리 학원') || (await page.evaluate(async (s) => (await (await fetch('/api/academies/' + s + '/homepage')).text()), slug)).includes('영어는 우리 학원'));

  section('G. 거절 / 중복 승인 방지 / 권한');
  const p2 = await agent(token, 'POST', '/proposals', { summary: '거절될 제안', changes: [{ componentId: 'intro', type: 'text', after: '거절될 문구' }] });
  await open();
  await page.locator('#aiBtn').click();
  await page.waitForSelector('.ai-card');
  await page.locator('.ai-card button[data-act="reject"]').click();
  await page.waitForFunction(() => document.querySelectorAll('.ai-card').length === 0, null, { timeout: 8000 });
  check('거절하면 목록에서 사라지고 REJECTED', (await agent(token, 'GET', '/proposals/' + p2.j.proposalId)).j.status === 'REJECTED');
  const rev = (await api('GET', VE + '/draft')).j;
  const dup = await api('PUT', VE + '/draft', { html: rev.html, css: rev.css, projectData: rev.projectData, revision: rev.revision, appliedProposalId: pid });
  check('이미 적용된 제안으로 다시 저장하면 409 PROPOSAL_NOT_PENDING', dup.s === 409 && dup.j.code === 'PROPOSAL_NOT_PENDING', dup);
  const cookieOnAgent = await api('GET', '/api/agent/academy/visual-editor/context');
  check('교육자 쿠키로는 Agent 경로를 쓸 수 없다', cookieOnAgent.s === 403, cookieOnAgent.s);

  section('H. AI 수정 요청: 공급자가 없을 때도 홈페이지·편집은 정상');
  await open();
  await page.locator('#aiBtn').click();
  await page.fill('#aiInstruction', '제목을 더 눈에 띄게 바꿔줘');
  const beforeAi = (await api('GET', VE + '/draft')).j;
  await page.locator('#aiAsk').click();
  await page.waitForFunction(() => { const m = document.getElementById('aiMsg'); return m && (m.className.includes('error') || m.className.includes('ok')); }, null, { timeout: 30000 });
  const aiMsg = await page.locator('#aiMsg').innerText();
  const afterAi = (await api('GET', VE + '/draft')).j;
  check('AI 공급자 실패 시 안내 메시지가 보이고', aiMsg.length > 0 && aiMsg !== 'AI가 수정안을 만드는 중입니다...', aiMsg);
  check('초안·편집 상태는 그대로다', afterAi.revision === beforeAi.revision && (await state()).hasChanges === false);
  check('편집기는 계속 정상 저장된다', await (async () => {
    await page.evaluate(() => { window.editor.getWrapper().find('#intro')[0].components([{ type: 'textnode', content: 'AI 실패 후에도 저장됨' }]); });
    await sleep(600);
    await page.locator('#aiClose').click();
    await page.locator('#saveBtn').click();
    await page.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
    return (await api('GET', VE + '/draft')).j.html.includes('AI 실패 후에도 저장됨');
  })());

  section('I. 모바일 390px: 기본 수정과 승인');
  const mctx = await newCtx(browser, { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });
  const mpage = await mctx.newPage();
  mpage.on('pageerror', (e) => errors.push('m:' + String(e).slice(0, 200)));
  mpage.on('dialog', (d) => d.accept().catch(() => {}));
  await mpage.goto(BASE + '/tutor/academy/editor');
  await mpage.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 });
  const mState = () => mpage.evaluate(() => window.__ve.state());
  const p3 = await agent(token, 'POST', '/proposals', { summary: '모바일 승인 테스트', changes: [{ componentId: 'intro', type: 'text', after: '모바일에서 승인한 문구' }] });
  check('모바일: AI 제안 버튼이 보인다', await mpage.locator('#aiBtn').isVisible());
  await mpage.locator('#aiBtn').tap();
  await mpage.waitForSelector('#aiPanel:not([hidden]) .ai-card');
  const box = await mpage.locator('#aiPanel').boundingBox();
  check('모바일: 패널이 화면 폭(390px)에 맞고 가로 스크롤이 없다', Math.round(box.width) === 390 && (await mpage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)), box);
  const btnH = await mpage.evaluate(() => Math.min(...[...document.querySelectorAll('.ai-card button')].map((b) => b.getBoundingClientRect().height)));
  check('모바일: 버튼 터치 영역이 44px 이상이다', btnH >= 44, btnH);
  await mpage.screenshot({ path: ART + '/agent-panel-mobile.png' });
  await mpage.locator('.ai-card button[data-act="preview"]').tap();
  await mpage.waitForSelector('#previewProposalBar:not([hidden])');
  const pw = await mpage.evaluate(() => document.getElementById('previewFrame').getBoundingClientRect().width);
  const mText = await mpage.frameLocator('#previewFrame').locator('body').innerText();
  check('모바일: 미리보기 폭이 화면 안이고 제안 문구가 보인다', pw <= 390 && mText.includes('모바일에서 승인한 문구'), { pw });
  await mpage.screenshot({ path: ART + '/agent-preview-mobile.png' });
  await mpage.locator('#previewProposalBar button.approve').tap();
  await mpage.waitForFunction(() => window.__ve.state().pendingAppliedProposalId === null && !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  const mSaved = (await api('GET', VE + '/draft')).j;
  check('모바일: 승인 후 저장되어 서버에 반영된다', mSaved.html.includes('모바일에서 승인한 문구'));
  check('모바일: 서버에서 APPLIED', (await agent(token, 'GET', '/proposals/' + p3.j.proposalId)).j.status === 'APPLIED');
  await mpage.reload(); await mpage.waitForFunction(() => window.editor && window.__ve.state().ready);
  check('모바일: 새로고침 후 유지', (await mpage.evaluate(() => window.editor.getHtml())).includes('모바일에서 승인한 문구'));
  // 기본 수정(제안 없이): 텍스트 수정 → 저장 → 유지
  await mpage.evaluate(() => { window.editor.getWrapper().find('#intro')[0].components([{ type: 'textnode', content: '모바일 직접 수정' }]); });
  await sleep(600);
  await mpage.locator('#saveBtn').tap();
  await mpage.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 15000 });
  check('모바일: 기본 수정·저장이 동작한다', (await api('GET', VE + '/draft')).j.html.includes('모바일 직접 수정'));

  check('페이지 오류가 없다', errors.length === 0, errors);
  const failed = results.filter((r) => !r.ok);
  console.log(`\n결과: ${results.length - failed.length}/${results.length} 통과`);
  require('fs').writeFileSync(ART + '/e2e-agent-flow-result.json', JSON.stringify(results, null, 2));
  await browser.close();
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
