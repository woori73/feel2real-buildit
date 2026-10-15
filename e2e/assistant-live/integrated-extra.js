// 통합 E2E 보강: 사진 추가·삭제(예약→게시 승격) · 5개 화면 폭 스크린샷 · 사용자별 권한 · 동시 수정 충돌 · 기존 학원 호환.
// live-flow.js(실제 AI 연속 흐름) 직후, 같은 격리 CI 앱·DB에서 실행한다. 운영 접근 없음. AI 키는 다루지 않는다.
const fs = require('fs');
const path = require('path');
const { launch, newCtx, BASE, PNG } = require('../visual-editor/lib');

const OUT = path.resolve(__dirname, 'artifacts');
fs.mkdirSync(OUT, { recursive: true });
const PASS = 'admin1234';

(async () => {
  const b = await launch();
  const rows = [];
  const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const errors = [];
  const ctx = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: PASS });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  const api = async (c, m, u, data) => { const r = await c.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const wait = (ms) => page.waitForTimeout(ms);
  const idle = () => page.waitForFunction(() => !document.getElementById('ahWorkInput').disabled, null, { timeout: 150000 });
  const state = async (c = ctx) => (await api(c, 'GET', '/api/tutor/academy/assistant/state')).j;
  const wc = async (c = ctx) => (await api(c, 'GET', '/api/tutor/academy/working-copy')).j;
  const say = async (text) => { await page.fill('#ahWorkInput', text); await page.click('#ahWorkSend'); await idle(); await wait(700); };
  const status = async (c, u) => (await c.request.fetch(BASE + u)).status();

  const me = (await api(ctx, 'GET', '/api/tutor/academy/me')).j;
  const slug = me.slug;
  const pubStatus = (slot) => fetch(BASE + '/api/academies/' + slug + '/hero-media/' + slot).then(r => r.status);
  const pubJson = async () => JSON.stringify(await (await fetch(BASE + '/api/academies/' + slug)).json());
  const mediaStatus = (slot) => status(ctx, '/api/tutor/academy/assistant/media/' + slot);

  await page.goto(BASE + '/builder'); await page.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 }); await wait(1500);

  // ── 1. 사진 추가·삭제 ─────────────────────────────────────────
  console.log('\n[1] 사진 추가·삭제 (예약 → 게시 때만 공개)');
  const pubBefore = await pubJson();
  const pubPhoto0 = await pubStatus('principal');
  ok('시작 시 공개 원장 사진 없음(404)', pubPhoto0 === 404, pubPhoto0);
  await page.click('#ahPhotoBtn'); await page.click('#ahPhotoMenu [data-slot="principal"]');
  await page.setInputFiles('#ahPhotoFile', { name: 'principal.png', mimeType: 'image/png', buffer: PNG });
  await idle(); await wait(1500);
  ok('사진 올리기 후 작업본 미리보기에는 보인다(200)', (await mediaStatus('principal')) === 200);
  ok('  → 공개 원장 사진은 그대로 없음(404)', (await pubStatus('principal')) === 404);
  ok('  → 공개 홈페이지 데이터 그대로', (await pubJson()) === pubBefore);
  ok('  → 되돌리기 단계가 생김', parseInt(await page.locator('#ahUndoCount').innerText(), 10) >= 1);
  await page.click('#ahUndo'); await wait(1800);
  ok('되돌리기 → 예약된 사진이 사라짐(404)', (await mediaStatus('principal')) === 404);
  await page.click('#ahPhotoBtn'); await page.click('#ahPhotoMenu [data-slot="principal"]');
  await page.setInputFiles('#ahPhotoFile', { name: 'principal.png', mimeType: 'image/png', buffer: PNG });
  await idle(); await wait(1500);
  await say('원장 사진 지워줘');
  ok('대화로 "원장 사진 지워줘" → 작업본에서 사진이 사라짐', (await mediaStatus('principal')) === 404, await mediaStatus('principal'));
  await page.click('#ahUndo'); await wait(1800);
  ok('삭제를 되돌리면 사진이 다시 보임(200)', (await mediaStatus('principal')) === 200);
  await page.click('#ahPublish'); await page.waitForSelector('#ahConfirm:not([hidden])'); await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /게시됨/.test(document.getElementById('ahStatus').innerText) && !/수정 있음/.test(document.getElementById('ahStatus').innerText), null, { timeout: 30000 });
  ok('게시 승인 후에만 공개 원장 사진이 생김(200)', (await pubStatus('principal')) === 200);
  await say('원장 사진 지워줘');
  ok('게시된 사진을 지워달라 해도 승인 전에는 공개가 그대로(200)', (await pubStatus('principal')) === 200);
  await page.click('#ahPublish'); await page.waitForSelector('#ahConfirm:not([hidden])'); await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /게시됨/.test(document.getElementById('ahStatus').innerText) && !/수정 있음/.test(document.getElementById('ahStatus').innerText), null, { timeout: 30000 });
  ok('삭제도 게시 승인 후에 공개에 반영됨(404)', (await pubStatus('principal')) === 404);

  // ── 2. 화면 폭 5종 스크린샷(작업 화면 + 공개 홈페이지) ──────────────
  console.log('\n[2] 화면 폭 360/390/430 · 태블릿 768 · PC 1280');
  const publicPath = new URL(me.publicUrl, BASE).pathname.startsWith('/') && !/^https?:\/\//.test(me.publicUrl) ? me.publicUrl : '/academy/' + slug;
  for (const [name, w, h] of [['360', 360, 740], ['390', 390, 844], ['430', 430, 932], ['tablet768', 768, 1024], ['pc1280', 1280, 900]]) {
    const c = await newCtx(b, { viewport: { width: w, height: h } }, { loginId: 'e2e-live', password: PASS });
    const p = await c.newPage();
    await p.goto(BASE + '/builder'); await p.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 }); await p.waitForTimeout(2500);
    await p.screenshot({ path: path.join(OUT, `work-${name}.png`), fullPage: false });
    ok(`${name}px 작업 화면 가로 스크롤 없음`, await p.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
    ok(`${name}px 입력창·게시 버튼이 보임`, (await p.locator('#ahWorkInput').isVisible()) && (await p.locator('#ahPublish').isVisible()));
    const pub = await c.newPage();
    const resp = await pub.goto(BASE + publicPath, { waitUntil: 'networkidle' }).catch(() => null);
    await pub.waitForTimeout(1500);
    // 스크롤 등장 효과가 있는 섹션이 빈 채로 찍히지 않도록 끝까지 천천히 내려갔다 올라온다.
    await pub.evaluate(async () => { const h = document.documentElement.scrollHeight; for (let y = 0; y < h; y += 400) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 120)); } window.scrollTo(0, 0); });
    await pub.waitForTimeout(800);
    await pub.screenshot({ path: path.join(OUT, `public-${name}.png`), fullPage: true });
    const pubErr = await pub.evaluate(() => { const e = document.getElementById('errorMsg'); return e ? e.textContent.trim() : ''; });
    ok(`${name}px 공개 홈페이지에 오류 문구가 없음(#errorMsg 비어 있음)`, pubErr === '', pubErr);
    ok(`${name}px 공개 홈페이지 정상 응답`, !!resp && resp.status() === 200, resp && resp.status());
    ok(`${name}px 공개 홈페이지 가로 스크롤 없음`, await pub.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
    await c.close();
  }

  // ── 3. 사용자별 권한 ─────────────────────────────────────────
  console.log('\n[3] 사용자별 권한');
  const liveDraft = JSON.stringify(await wc());
  const other = await newCtx(b, {}, { loginId: 'e2e-other', password: PASS });
  const oMedia = await status(other, '/api/tutor/academy/assistant/media/principal');
  ok('다른 원장은 내 작업본 사진을 읽지 못함(404/4xx)', oMedia >= 400, oMedia);
  const oState = await state(other);
  ok('다른 원장의 상태에는 내 학원이 없음', oState && oState.hasAcademy === false, oState);
  const oSave = await api(other, 'POST', '/api/tutor/academy/working-copy/discard', { revision: (await state()).revision });
  ok('다른 원장이 내 revision으로 작업본을 건드려도 내 작업본은 불변', JSON.stringify(await wc()) === liveDraft, oSave.s);
  const anon = await b.newContext();
  const aTurn = await anon.request.fetch(BASE + '/api/tutor/academy/assistant/turn', { method: 'POST', data: { message: 'x' } });
  ok('비로그인은 대화 API를 못 씀(401/403)', [401, 403].includes(aTurn.status()), aTurn.status());
  const aMedia = await anon.request.fetch(BASE + '/api/tutor/academy/assistant/media/principal');
  ok('비로그인은 작업본 사진을 못 읽음(401/403)', [401, 403].includes(aMedia.status()), aMedia.status());
  ok('다른 원장의 공개 홈페이지 데이터도 영향 없음', (await pubJson()).length > 0);

  // ── 4. 동시 수정 충돌 ─────────────────────────────────────────
  console.log('\n[4] 동시 수정 충돌(revision)');
  const a = await newCtx(b, {}, { loginId: 'e2e-live', password: PASS });
  const bb = await newCtx(b, {}, { loginId: 'e2e-live', password: PASS });
  const r0 = (await state(a)).revision;
  // "이미 적용됨"으로 끝나지 않도록 현재 주색과 다른 색 두 가지를 고른다.
  const curColor = (((await wc(a)).pageSettings || {}).builderDraft || {}).colorPrimary || '';
  const colors = [['진한 파란색', '#2563eb'], ['진한 초록색', '#16a34a'], ['진한 빨간색', '#dc2626']].filter(c => c[1] !== curColor.toLowerCase());
  const turnA = await api(a, 'POST', '/api/tutor/academy/assistant/turn', { message: `주색을 ${colors[0][0]}으로 바꿔줘`, revision: r0 });
  ok('창 A의 수정은 성공', turnA.s === 200 && turnA.j && turnA.j.revision > r0, turnA);
  const turnB = await api(bb, 'POST', '/api/tutor/academy/assistant/turn', { message: `주색을 ${colors[1][0]}으로 바꿔줘`, revision: r0 });
  ok('창 B가 낡은 revision으로 수정하면 409', turnB.s === 409, turnB);
  const afterB = await state(a);
  ok('충돌한 창 B의 수정은 반영되지 않음(작업본 revision 그대로)', afterB.revision === turnA.j.revision, { afterB, turnA: turnA.j && turnA.j.revision });
  const saveB = await api(bb, 'PUT', '/api/tutor/academy/working-copy', { revision: r0, intro: '충돌 테스트' });
  ok('낡은 revision의 작업본 직접 저장도 409', saveB.s === 409, saveB.s);

  // ── 5. 기존(대화 기록 없는) 학원 호환 ─────────────────────────
  console.log('\n[5] 기존 방식 학원 호환');
  const legacy = await newCtx(b, {}, { loginId: 'e2e-legacy', password: PASS });
  const created = await api(legacy, 'POST', '/api/tutor/academy', {
    name: '기존방식수학학원', intro: '기존 빌더로 만든 학원입니다.', subjectIds: [2], targetGroups: ['MIDDLE'], regions: [{ city: '서울', district: '강남구' }] });
  ok('기존 방식(JSON API)으로 학원 생성', created.s === 200 && created.j && created.j.slug, created);
  const lslug = created.j && created.j.slug;
  const lpub = async () => JSON.stringify(await (await fetch(BASE + '/api/academies/' + lslug)).json());
  const lBefore = lslug ? await lpub() : '';
  const html = await (await legacy.request.fetch(BASE + '/builder')).text();
  ok('기능이 켜져 있어도 기존 방식 학원은 기존 빌더로 열림(대화형 시작 화면 없음)', !/id="ahStartForm"/.test(html) && !/id="ahWork"/.test(html), html.slice(0, 120));
  const htmlLive = await (await ctx.request.fetch(BASE + '/builder?classic=1')).text();
  ok('?classic=1이면 대화로 만든 학원도 기존 빌더', !/id="ahWork"/.test(htmlLive));
  const lTurn = await api(legacy, 'POST', '/api/tutor/academy/assistant/turn', { message: '주색을 진한 빨간색으로 바꿔줘', revision: (await state(legacy)).revision || 1 });
  ok('기존 학원에 대화 API를 직접 호출하면 409 NOT_ASSISTANT_ACADEMY', lTurn.s === 409 && lTurn.j && lTurn.j.code === 'NOT_ASSISTANT_ACADEMY', lTurn);
  ok('호출 뒤에도 공개 데이터는 그대로', lslug && (await lpub()) === lBefore);
  const htmlAfter = await (await legacy.request.fetch(BASE + '/builder')).text();
  ok('호출 뒤에도 /builder는 기존 빌더(화면 모드 불변)', !/id="ahWork"|id="ahStartForm"/.test(htmlAfter));

  ok('페이지 오류 없음', errors.length === 0, errors);
  console.log(`\n검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch(e => { console.error('E2E 실패:', e); process.exit(1); });
