// AI 제안 적용(A안) · 저장 충돌 시 미저장 내용 보호 — 실제 브라우저 E2E.
//
// 서버에는 Proposal 구현체(뮤즈)가 아직 없으므로 두 가지로 검증한다.
//  - 실제 서버: 포트 부재 시 400 PROPOSAL_NOT_AVAILABLE 계약, 제안 없이 저장, 초안 불변(원자성)
//  - 응답 모킹(route): 200 APPLIED / 409 REVISION_CONFLICT / 422 등 서버 응답에 대한 편집기 동작
// 서버 쪽 원자성·롤백·학원 격리는 VisualEditorProposalMySqlTest(실제 MySQL)가 검증한다.
// 전제: e2e.js와 같다(앱 local 프로파일, tutor/admin1234, 초안·게시본 비어 있음).
const { launch, newCtx, BASE, ART } = require('./lib');

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail });
  console.log((ok ? '  ✔ ' : '  ✘ ') + name + (detail !== undefined && !ok ? '  → ' + JSON.stringify(detail) : ''));
}
const section = (t) => console.log('\n■ ' + t);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await launch();
  const ctx = await newCtx(browser, { viewport: { width: 1440, height: 900 } });
  const VE = '/api/tutor/academy/page-settings/visual-editor';
  const api = async (m, u, data) => { const r = await ctx.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const page = await ctx.newPage();
  const pageerrors = [];
  page.on('pageerror', (e) => pageerrors.push(String(e).slice(0, 300)));

  // 다이얼로그: 테스트가 정한 답을 쓰고 기록한다.
  let dialogAnswer = 'accept';
  const dialogs = [];
  page.on('dialog', (d) => { dialogs.push(d.message().slice(0, 60)); (dialogAnswer === 'accept' ? d.accept() : d.dismiss()).catch(() => {}); });

  const st0 = (await api('GET', VE + '/status')).j;
  if (st0.draftExists) { console.error('초안이 이미 있습니다. README의 초기화 SQL을 먼저 실행하세요.'); process.exit(2); }

  const state = () => page.evaluate(() => window.__ve.state());
  const open = async () => { await page.goto(BASE + '/tutor/academy/editor'); await page.waitForFunction(() => window.editor && window.__ve && window.__ve.state().ready, null, { timeout: 30000 }); };
  const html = () => page.evaluate(() => window.editor.getHtml());
  const css = () => page.evaluate(() => window.editor.getCss());
  const serverDraft = async () => (await api('GET', VE + '/draft')).j;

  await open();
  await page.evaluate(() => {
    window.__events = [];
    window.addEventListener('ve:saved', (e) => window.__events.push({ t: 'saved', d: e.detail }));
    window.addEventListener('ve:proposal-failed', (e) => window.__events.push({ t: 'failed', d: e.detail }));
    window.editor.setComponents('<section id="ve1"><h1 id="ve5">원래 제목</h1><p id="ve6">그대로 둘 문단</p><img id="ve7" src="https://via.placeholder.com/10x10"></section>');
  });
  await sleep(700);
  await page.locator('#saveBtn').click();
  for (let i = 0; i < 30; i++) { if ((await serverDraft()).html.includes('원래 제목')) break; await sleep(500); }
  await page.waitForFunction(() => !window.__ve.state().hasChanges && !window.__ve.state().isSaving, null, { timeout: 10000 });
  const base = await serverDraft();
  const baseRev = base.revision;

  const proposal = (extra) => Object.assign({
    proposalId: 77,
    changes: [
      { componentId: 've5', type: 'text', after: 'AI가 바꾼 제목' },
      { componentId: 've7', type: 'image', after: 'https://via.placeholder.com/20x20' },
      { componentId: 've5', type: 'style', styles: { color: 'rgb(255, 0, 0)' } },
      { componentId: 've5', type: 'style', styles: { 'font-size': '20px' }, mediaText: '(max-width: 768px)' },
    ],
  }, extra || {});

  section('A. 제안 적용은 component 단위이고 전부 아니면 전무다');
  const beforeHtml = await html();
  const bad = await page.evaluate((p) => window.__ve.applyProposal(Object.assign({}, p, { changes: p.changes.concat([{ componentId: 'ghost', type: 'text', after: 'x' }]) })), proposal());
  check('없는 대상이 하나라도 있으면 적용되지 않는다', bad.applied === false && bad.reason === 'TARGET_NOT_FOUND' && bad.failed[0].componentId === 'ghost', bad);
  check('실패한 적용은 아무것도 바꾸지 않는다', (await html()) === beforeHtml && (await state()).pendingAppliedProposalId === null);
  const ok = await page.evaluate((p) => window.__ve.applyProposal(p), proposal());
  await sleep(400);
  check('정상 제안이 적용된다', ok.applied === true, ok);
  const afterHtml = await html();
  check('텍스트가 바뀐다', afterHtml.includes('AI가 바꾼 제목') && !afterHtml.includes('원래 제목'));
  check('이미지 src가 바뀐다', afterHtml.includes('20x20'));
  check('CSS(기본·모바일 미디어)가 projectData 규칙으로 들어간다', await page.evaluate(() => {
    const rules = window.editor.getProjectData().styles || [];
    const base = rules.find((r) => (r.selectors || []).includes('#ve5') && !r.mediaText);
    const mob = rules.find((r) => (r.selectors || []).includes('#ve5') && r.mediaText === '(max-width: 768px)');
    return !!(base && base.style.color && mob && mob.style['font-size'] === '20px');
  }));
  check('대상이 아닌 컴포넌트는 그대로다', afterHtml.includes('그대로 둘 문단') && (await page.evaluate(() => window.editor.getWrapper().find('#ve6').length)) === 1);
  const s1 = await state();
  check('변경됨 + pendingAppliedProposalId=77', s1.hasChanges === true && s1.pendingAppliedProposalId === 77, s1);
  check('"AI 제안 되돌리기" 버튼이 보인다', await page.locator('#undoProposalBtn').isVisible());
  check('다른 제안을 동시에 적용하려 하면 거부된다', (await page.evaluate(() => window.__ve.applyProposal({ proposalId: 78, changes: [{ componentId: 've6', type: 'text', after: 'z' }] }))).reason === 'ANOTHER_PROPOSAL_PENDING');
  await page.screenshot({ path: ART + '/proposal-applied.png' });

  section('B. 한 번에 되돌리기');
  check('undoProposal이 적용 직전 상태로 복원한다', (await page.evaluate(() => window.__ve.undoProposal())) === true && (await html()) === beforeHtml);
  const s2 = await state();
  check('되돌리면 pending이 해제되고 버튼이 사라진다', s2.pendingAppliedProposalId === null && !(await page.locator('#undoProposalBtn').isVisible()), s2);
  await sleep(400);
  check('되돌린 뒤에는 저장본과 같아 변경 없음이다', (await state()).hasChanges === false);

  section('C. 실제 서버: 구현체가 없으면 400 PROPOSAL_NOT_AVAILABLE, 초안은 저장되지 않는다');
  await page.evaluate((p) => window.__ve.applyProposal(p), proposal());
  await sleep(400);
  const bodies = [];
  await page.route('**/visual-editor/draft', async (route) => { if (route.request().method() === 'PUT') bodies.push(JSON.parse(route.request().postData())); await route.continue(); });
  dialogs.length = 0; dialogAnswer = 'dismiss';
  await page.locator('#saveBtn').click();
  await sleep(1500);
  check('PUT 본문에 appliedProposalId=77이 실린다', bodies.length === 1 && bodies[0].appliedProposalId === 77, bodies);
  check('서버가 거부하고 안내 확인창이 뜬다', dialogs.length === 1 && dialogs[0].includes('제안'), dialogs);
  const dAfterReject = await serverDraft();
  check('거부되면 서버 초안은 그대로다(원자성)', dAfterReject.revision === baseRev && dAfterReject.html.includes('원래 제목'), { rev: dAfterReject.revision });
  check('"취소"하면 편집 내용이 화면에 남아 있다', (await html()).includes('AI가 바꾼 제목') && (await state()).hasChanges === true);
  check('실패 이벤트(ve:proposal-failed)가 발행된다', await page.evaluate(() => window.__events.some((e) => e.t === 'failed' && e.d.code === 'PROPOSAL_NOT_AVAILABLE')));
  dialogs.length = 0; dialogAnswer = 'accept'; bodies.length = 0;
  await page.locator('#saveBtn').click();
  await page.waitForFunction((r) => window.__ve.state().currentRevision > r && !window.__ve.state().hasChanges, baseRev, { timeout: 15000 });
  check('"제안 없이 저장"에 동의하면 appliedProposalId 없이 저장된다', bodies.length === 2 && bodies[1].appliedProposalId === undefined, bodies.map((b) => b.appliedProposalId));
  const dSaved = await serverDraft();
  check('서버 초안에 편집 내용이 저장된다', dSaved.html.includes('AI가 바꾼 제목'));
  check('저장 후 pending이 해제된다', (await state()).pendingAppliedProposalId === null);
  await page.unroute('**/visual-editor/draft');
  let rev = dSaved.revision;

  section('D. 응답 모킹: 200 APPLIED → 이벤트·상태 정리');
  await page.evaluate(() => { window.__events.length = 0; });
  await page.evaluate(() => { window.editor.getWrapper().find('#ve6')[0].components([{ type: 'textnode', content: '두 번째 제안 대상' }]); });
  await page.evaluate((p) => window.__ve.applyProposal(p), { proposalId: 90, changes: [{ componentId: 've6', type: 'text', after: '제안이 정한 문단' }] });
  await sleep(400);
  let sent = null;
  await page.route('**/visual-editor/draft', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    sent = JSON.parse(route.request().postData());
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'DRAFT_SAVED', revision: rev + 1, updatedAt: new Date().toISOString(), message: 'ok', appliedProposal: { proposalId: 90, status: 'APPLIED', appliedRevision: rev + 1 } }) });
  });
  await page.locator('#saveBtn').click();
  await page.waitForFunction((r) => window.__ve.state().currentRevision === r + 1, rev, { timeout: 10000 });
  check('요청 본문에 appliedProposalId=90, 서버 revision은 기존 값', sent && sent.appliedProposalId === 90 && sent.revision === rev, sent && { id: sent.appliedProposalId, rev: sent.revision });
  const ev = await page.evaluate(() => window.__events.filter((e) => e.t === 'saved'));
  check('ve:saved 이벤트에 revision과 appliedProposal이 실린다', ev.length === 1 && ev[0].d.revision === rev + 1 && ev[0].d.appliedProposal.status === 'APPLIED', ev);
  check('APPLIED 응답 후 pending 해제·되돌리기 버튼 숨김', (await state()).pendingAppliedProposalId === null && !(await page.locator('#undoProposalBtn').isVisible()));
  await page.unroute('**/visual-editor/draft');
  rev = rev + 1;
  // 서버 실제 revision과 어긋났으므로 이후 단계 전에 최신 상태로 다시 연다.
  await open();
  await page.evaluate(() => { window.__events = []; window.addEventListener('ve:saved', (e) => window.__events.push({ t: 'saved', d: e.detail })); window.addEventListener('ve:proposal-failed', (e) => window.__events.push({ t: 'failed', d: e.detail })); });
  rev = (await serverDraft()).revision;

  section('E. 자동저장(30초)에도 appliedProposalId가 실리고, 자동저장 충돌은 사용자를 방해하지 않는다');
  await page.evaluate((p) => window.__ve.applyProposal(p), { proposalId: 91, changes: [{ componentId: 've6', type: 'text', after: '자동저장으로 가는 제안' }] });
  const autoBodies = [];
  let autoCalls = 0;
  await page.route('**/visual-editor/draft', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    autoBodies.push(JSON.parse(route.request().postData()));
    autoCalls++;
    if (autoCalls === 1) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ status: 409, code: 'REVISION_CONFLICT', message: '다른 세션에서 수정되었습니다.' }) });
    return route.continue();
  });
  dialogs.length = 0;
  await sleep(32500);
  check('자동저장 요청에 appliedProposalId=91이 실린다', autoBodies.length >= 1 && autoBodies[0].appliedProposalId === 91, autoBodies.map((b) => b.appliedProposalId));
  check('자동저장 충돌은 모달·확인창 없이 상태줄에만 알린다', (await page.locator('.ve-modal-back').count()) === 0 && dialogs.length === 0);
  check('상태줄이 편집 내용 보호를 안내한다', (await page.locator('#saveStatus').innerText()).includes('백업'));
  check('충돌 때 편집 내용이 브라우저에 백업된다', await page.evaluate(() => { const v = localStorage.getItem('ve:backup:' + ACADEMY_ID); return !!v && JSON.parse(v).html.includes('자동저장으로 가는 제안') && JSON.parse(v).appliedProposalId === 91; }));
  check('백업 복원 버튼이 보인다', await page.locator('#restoreBackupBtn').isVisible());
  check('화면의 편집 내용은 그대로다', (await html()).includes('자동저장으로 가는 제안'));

  section('F. 수동 저장 충돌: 3지선다 — 내 내용 유지(기본)');
  await page.unroute('**/visual-editor/draft');
  await page.route('**/visual-editor/draft', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ status: 409, code: 'REVISION_CONFLICT', message: '다른 세션에서 수정되었습니다.' }) });
  });
  await page.locator('#saveBtn').click();
  await page.waitForSelector('.ve-modal-back', { timeout: 5000 });
  check('선택 모달이 뜨고 첫 선택지가 "내 편집 내용 유지"다', (await page.locator('.ve-modal button.primary').innerText()).includes('내 편집 내용 유지'));
  await page.screenshot({ path: ART + '/proposal-conflict-modal.png' });
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('.ve-modal-back'));
  check('ESC(= 유지)면 편집 내용이 그대로이고 변경 있음 상태다', (await html()).includes('자동저장으로 가는 제안') && (await state()).hasChanges === true && (await state()).pendingAppliedProposalId === 91);
  check('이 과정에서 기존의 "수정 내용이 사라집니다" 확인창이 뜨지 않는다', dialogs.length === 0, dialogs);

  section('G. 수동 저장 충돌: 최신 초안 불러오기 → 백업에서 복원');
  await page.locator('#saveBtn').click();
  await page.waitForSelector('.ve-modal-back');
  await page.locator('.ve-modal [data-c="load"]').click();
  await page.waitForFunction(() => window.__ve.state().ready && !document.querySelector('.ve-modal-back'));
  await sleep(800);
  check('최신 초안이 화면에 불러와진다(편집 내용은 사라짐)', !(await html()).includes('자동저장으로 가는 제안'));
  check('백업이 남아 복원 버튼이 보인다', await page.locator('#restoreBackupBtn').isVisible());
  check('불러오기 후 pending은 해제된다(제안은 서버에서 PENDING 유지)', (await state()).pendingAppliedProposalId === null);
  dialogAnswer = 'accept';
  await page.locator('#restoreBackupBtn').click();
  await sleep(600);
  check('복원하면 내 편집 내용과 pending(91)이 돌아온다', (await html()).includes('자동저장으로 가는 제안') && (await state()).pendingAppliedProposalId === 91);
  check('복원 후 변경 있음 상태다', (await state()).hasChanges === true);

  section('H. 수동 저장 충돌: 내 내용으로 덮어쓰기(최신 revision 확인 후 재저장)');
  await page.unroute('**/visual-editor/draft');
  let puts = 0;
  const hBodies = [];
  await page.route('**/visual-editor/draft', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    hBodies.push(JSON.parse(route.request().postData()));
    puts++;
    if (puts === 1) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ status: 409, code: 'REVISION_CONFLICT', message: '다른 세션에서 수정되었습니다.' }) });
    return route.continue();   // 실제 서버: 구현체 없음 → 400 PROPOSAL_NOT_AVAILABLE → 확인창
  });
  dialogs.length = 0; dialogAnswer = 'accept';
  await page.locator('#saveBtn').click();
  await page.waitForSelector('.ve-modal-back');
  await page.locator('.ve-modal [data-c="overwrite"]').click();
  await page.waitForFunction((r) => window.__ve.state().currentRevision > r && !window.__ve.state().hasChanges, rev, { timeout: 15000 });
  check('덮어쓰기는 최신 revision으로 다시 저장을 시도한다(PUT 2회 이상)', hBodies.length >= 2 && hBodies[1].revision === rev, hBodies.map((b) => b.revision));
  check('서버가 제안을 처리할 수 없으면 안내 후 제안 없이 저장된다', dialogs.some((m) => m.includes('제안')) && (await serverDraft()).html.includes('자동저장으로 가는 제안'));
  check('저장되면 백업이 정리된다', !(await page.locator('#restoreBackupBtn').isVisible()));
  await page.unroute('**/visual-editor/draft');

  section('I. 422 검증 실패(모킹): 취소하면 내용·pending 유지, 동의하면 제안 없이 저장');
  await page.evaluate(() => { window.__events.length = 0; });
  await page.evaluate((p) => window.__ve.applyProposal(p), { proposalId: 92, changes: [{ componentId: 've6', type: 'text', after: '제안 92 문단' }] });
  await page.evaluate(() => { window.editor.getWrapper().find('#ve6')[0].components([{ type: 'textnode', content: '사용자가 다시 고친 문단' }]); });
  await sleep(400);
  await page.route('**/visual-editor/draft', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    const b = JSON.parse(route.request().postData());
    if (b.appliedProposalId) return route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ status: 422, code: 'PROPOSAL_VERIFICATION_FAILED', message: '제안 내용이 저장된 초안과 일치하지 않습니다.', details: ['TEXT_MISMATCH:ve6'] }) });
    return route.continue();
  });
  dialogs.length = 0; dialogAnswer = 'dismiss';
  await page.locator('#saveBtn').click();
  await sleep(1200);
  check('422면 확인창에 사유(details)가 보인다', dialogs.length === 1 && dialogs[0].includes('제안'));
  check('취소하면 내용·pending이 유지된다', (await html()).includes('사용자가 다시 고친 문단') && (await state()).pendingAppliedProposalId === 92);
  check('ve:proposal-failed에 code와 details가 실린다', await page.evaluate(() => window.__events.some((e) => e.t === 'failed' && e.d.code === 'PROPOSAL_VERIFICATION_FAILED' && e.d.details[0] === 'TEXT_MISMATCH:ve6')));
  dialogAnswer = 'accept';
  const rev422 = (await state()).currentRevision;
  await page.locator('#saveBtn').click();
  await page.waitForFunction((r) => window.__ve.state().currentRevision > r && !window.__ve.state().hasChanges, rev422, { timeout: 15000 });
  check('동의하면 제안 없이 저장되고 서버에 사용자 수정이 남는다', (await serverDraft()).html.includes('사용자가 다시 고친 문단'));
  await page.unroute('**/visual-editor/draft');

  section('J. 기존 동작 회귀: 제안 없는 저장은 appliedProposalId를 보내지 않는다');
  const jb = [];
  await page.route('**/visual-editor/draft', async (route) => { if (route.request().method() === 'PUT') jb.push(JSON.parse(route.request().postData())); await route.continue(); });
  await page.evaluate(() => { window.editor.getWrapper().find('#ve6')[0].components([{ type: 'textnode', content: '평범한 수정' }]); });
  await sleep(400);
  await page.locator('#saveBtn').click();
  await sleep(1500);
  check('일반 저장 본문에 appliedProposalId가 없다', jb.length === 1 && !('appliedProposalId' in jb[0]), jb);
  check('일반 저장이 성공하고 변경 없음 상태가 된다', (await state()).hasChanges === false);
  await page.unroute('**/visual-editor/draft');
  check('페이지 오류가 없다', pageerrors.length === 0, pageerrors);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n결과: ${results.length - failed.length}/${results.length} 통과`);
  require('fs').writeFileSync(ART + '/e2e-proposal-result.json', JSON.stringify(results, null, 2));
  await browser.close();
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
