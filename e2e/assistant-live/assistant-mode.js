// 화면 모드 분리(AI 제작 / 기존 / 명시 전환)와 AI 응답 지연(진행 표시·타임아웃·작업 내용 보존) 실제 브라우저 E2E.
// live-flow.js(e2e-live 학원, AI 제작 학원) 직후 같은 격리 CI 앱·DB에서 실행한다. 운영 접근 없음. AI 키는 다루지 않는다.
const { launch, newCtx, BASE } = require('../visual-editor/lib');

const PASS = 'admin1234';

(async () => {
  const b = await launch();
  const rows = [];
  const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const errors = [];
  const api = async (c, m, u, data) => { const r = await c.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) { /* 본문 없음 */ } return { s: r.status(), j }; };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const A = '/api/tutor/academy/assistant';

  // ── 1. 최초 AI 생성 학원 = ASSISTANT 모드 ─────────────────────
  console.log('\n[1] 최초 AI 생성 학원');
  const live = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: PASS });
  ok('모드 조회 = ASSISTANT, 전환 버튼 대상 아님', await (async () => { const m = (await api(live, 'GET', A + '/mode')).j; return m && m.mode === 'ASSISTANT' && m.canAdopt === false; })());
  const liveHtml = await (await live.request.fetch(BASE + '/builder')).text();
  ok('/builder는 대화형 화면', /id="ahWork"/.test(liveHtml));

  // ── 2. 기존 방식 학원: 대화 API 직접 호출은 409, 화면 모드 불변 ──
  console.log('\n[2] 기존 방식 학원(대화 API 직접 호출)');
  const legacy = await newCtx(b, {}, { loginId: 'e2e-legacy', password: PASS });
  const lme = (await api(legacy, 'GET', '/api/tutor/academy/me')).j;
  ok('기존 방식 학원이 있음', !!(lme && lme.slug), lme);
  const lslug = lme && lme.slug;
  const lpub = async () => JSON.stringify(await (await fetch(BASE + '/api/academies/' + lslug)).json());
  const lBefore = await lpub();
  const lwcBefore = JSON.stringify((await api(legacy, 'GET', '/api/tutor/academy/working-copy')).j);
  for (const [name, call] of [
    ['상태', () => api(legacy, 'GET', A + '/state')],
    ['수정 턴', () => api(legacy, 'POST', A + '/turn', { message: '주색을 진한 빨간색으로 바꿔줘', revision: 1 })],
    ['되돌리기', () => api(legacy, 'POST', A + '/undo', { revision: 1 })],
    ['게시', () => api(legacy, 'POST', A + '/publish', { revision: 1 })]]) {
    const r = await call();
    ok(`기존 학원 ${name} API → 409 NOT_ASSISTANT_ACADEMY`, r.s === 409 && r.j && r.j.code === 'NOT_ASSISTANT_ACADEMY', r);
  }
  const lHtml = await (await legacy.request.fetch(BASE + '/builder')).text();
  ok('호출 뒤에도 /builder는 기존 빌더(대화형 화면 아님)', !/id="ahWork"/.test(lHtml) && !/id="ahStartForm"/.test(lHtml));
  ok('공개 데이터 그대로', (await lpub()) === lBefore);
  ok('작업본 그대로', JSON.stringify((await api(legacy, 'GET', '/api/tutor/academy/working-copy')).j) === lwcBefore);
  ok('모드 조회 = CLASSIC, 전환 가능', await (async () => { const m = (await api(legacy, 'GET', A + '/mode')).j; return m && m.mode === 'CLASSIC' && m.canAdopt === true; })());

  // ── 3. 'AI로 이어서 만들기' 명시 전환(브라우저) + 공개본 보존·복구 ──
  console.log('\n[3] 명시 전환(브라우저)과 공개본 보존·복구');
  const adopt = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-adopt', password: PASS });
  const created = await api(adopt, 'POST', '/api/tutor/academy', {
    name: '전환테스트학원', intro: '기존 빌더로 만든 학원', subjectIds: [2], targetGroups: ['MIDDLE'], regions: [{ city: '서울', district: '강남구' }],
    pageSettings: { builderDraft: { designPreset: 'EXAM', colorPrimary: '#112233' } } });
  ok('기존 방식(JSON API)으로 학원 생성', created.s === 200 && created.j && created.j.slug, created);
  const aslug = created.j && created.j.slug;
  const apub = async () => JSON.stringify(await (await fetch(BASE + '/api/academies/' + aslug)).json());
  const aBefore = await apub();
  const page = await adopt.newPage();
  // 기존 빌더 페이지는 외부 CDN(tailwind 등)을 쓰는데 E2E 하네스가 외부 요청을 막아 이 오류가 난다(앱 오류가 아님).
  page.on('pageerror', (e) => { if (!/tailwind is not defined/.test(String(e))) errors.push(String(e).slice(0, 160)); });
  let adoptResp = null;
  page.on('request', (r) => { if (/\/assistant\/adopt$/.test(r.url()) && r.method() === 'POST') adoptResp = adoptResp || { requested: true }; });
  await page.goto(BASE + '/builder'); await page.waitForSelector('#adoptBtn', { timeout: 60000 });
  ok("기존 빌더에 'AI로 이어서 만들기' 버튼이 보임", await page.locator('#adoptBtn').isVisible());
  await page.screenshot({ path: __dirname + '/artifacts/mode-classic-with-adopt-button.png' });
  await page.click('#adoptBtn');
  ok('확인 창이 열리고 체크 전에는 전환 버튼이 비활성', await page.locator('.adopt-modal').isVisible() && await page.locator('#adoptOk').isDisabled());
  await page.screenshot({ path: __dirname + '/artifacts/mode-adopt-confirm.png' });
  await page.click('#adoptCancel');
  ok('취소하면 아무것도 바뀌지 않음(기존 빌더 유지·공개 그대로)', !(await page.locator('.adopt-modal').isVisible()) && (await apub()) === aBefore && !adoptResp);
  await page.click('#adoptBtn'); await page.check('#adoptCheck');
  const [adoptRes] = await Promise.all([
    page.waitForResponse((r) => /\/assistant\/adopt$/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 }),
    page.waitForURL(/\/builder$/, { timeout: 30000 }),
    page.click('#adoptOk')]);
  const adoptStatus = adoptRes.status();
  await page.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 });
  await page.screenshot({ path: __dirname + '/artifacts/mode-adopted-assistant.png' });
  ok('전환 후 /builder가 대화형 화면으로 열림', await page.locator('#ahWork').isVisible());
  ok('전환 요청이 200으로 처리됨(확인 후에만 요청이 나감)', adoptStatus === 200, adoptStatus);
  ok('전환 직후 공개 홈페이지 그대로', (await apub()) === aBefore);
  const versions = (await api(adopt, 'GET', '/api/tutor/academy/ai/chat/versions')).j || [];
  ok("전환 전 공개본이 버전 기록에 보관됨(\"AI로 이어서 만들기 전 공개본\")", versions.some((v) => /AI로 이어서 만들기 전 공개본/.test(v.note || '')), versions.slice(0, 3));
  ok('모드 조회 = ASSISTANT', ((await api(adopt, 'GET', A + '/mode')).j || {}).mode === 'ASSISTANT');
  // 전환 후 수정은 작업본에만, 게시 후 복원으로 되돌릴 수 있다
  const say = async (text) => { await page.fill('#ahWorkInput', text); await page.click('#ahWorkSend'); await page.waitForFunction(() => !document.getElementById('ahWorkInput').disabled, null, { timeout: 150000 }); await page.waitForTimeout(700); };
  await say('주색을 진한 초록색으로 바꿔줘');
  ok('전환 후 수정해도 게시 전까지 공개는 그대로', (await apub()) === aBefore);
  await page.click('#ahPublish'); await page.waitForSelector('#ahConfirm:not([hidden])'); await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /게시됨/.test(document.getElementById('ahStatus').innerText) && !/수정 있음/.test(document.getElementById('ahStatus').innerText), null, { timeout: 30000 });
  ok('게시하면 공개 색이 바뀜', (await apub()) !== aBefore);
  const adoptVersion = (await api(adopt, 'GET', '/api/tutor/academy/ai/chat/versions')).j.find((v) => /AI로 이어서 만들기 전 공개본/.test(v.note || ''));
  const wcState = async () => (await api(adopt, 'GET', '/api/tutor/academy/working-copy')).j;
  const restore = await api(adopt, 'POST', '/api/tutor/academy/working-copy/restore', { revision: (await wcState()).revision, versionId: adoptVersion.id || adoptVersion.versionId });
  ok('보존된 버전을 작업본에 불러옴', restore.s === 200, restore);
  const pub2 = await api(adopt, 'POST', '/api/tutor/academy/working-copy/publish', { revision: (await wcState()).revision });
  ok('복원본 게시', pub2.s === 200, pub2);
  ok('전환 전 공개 색(#112233)으로 복구됨', /#112233/i.test(await apub()), (await apub()).slice(0, 200));

  // ── 4. AI 응답 지연: 진행 표시·타임아웃·작업 내용 보존 ──────────
  console.log('\n[4] AI 응답 지연');
  const wcOf = async (c) => JSON.stringify((await api(c, 'GET', '/api/tutor/academy/working-copy')).j);
  // 4-a. 느린 응답: 8초 이상이면 진행 안내가 보이고, 응답이 오면 정상 반영
  const slowCtx = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: PASS });
  const slow = await slowCtx.newPage();
  await slow.route('**/assistant/turn', async (route) => { await sleep(10500); await route.continue(); });
  await slow.goto(BASE + '/builder'); await slow.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 }); await slow.waitForTimeout(1500);
  await slow.fill('#ahWorkInput', '주색을 진한 파란색으로 바꿔줘'); await slow.click('#ahWorkSend');
  await slow.waitForFunction(() => /평소보다 오래 걸리고 있어요/.test(document.getElementById('ahBusyNote').textContent), null, { timeout: 15000 });
  ok('8초 넘으면 "오래 걸리고 있어요" 경과 안내가 보임', true);
  ok('안내 중에도 입력은 잠겨 있고(중복 전송 방지) 진행 단계가 보임', await slow.locator('#ahWorkInput').isDisabled() && (await slow.locator('#ahBusySteps li').count()) >= 1);
  await slow.screenshot({ path: __dirname + '/artifacts/progress-slow-notice.png' });
  await slow.waitForFunction(() => !document.getElementById('ahWorkInput').disabled, null, { timeout: 60000 });
  ok('느린 응답이 오면 정상 반영(안내 사라짐)', (await slow.locator('#ahBusyNote').innerText()) === '' && !(await slow.locator('#ahBusy').isVisible()));
  await slowCtx.close();

  // 4-b. 서버가 응답하지 않음: 한도(5초)에서 기다리기를 멈추고 작업 내용·입력 보존
  const toCtx = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: PASS });
  await toCtx.addInitScript(() => { window.__AH_TURN_TIMEOUT_MS__ = 5000; });
  const to = await toCtx.newPage();
  await to.route('**/assistant/turn', async (route) => { await sleep(15000); await route.abort().catch(() => {}); });
  await to.goto(BASE + '/builder'); await to.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 }); await to.waitForTimeout(1500);
  const wcBeforeTimeout = await wcOf(toCtx);
  const pubBeforeTimeout = JSON.stringify(await (await fetch(BASE + '/api/academies/' + ((await api(toCtx, 'GET', '/api/tutor/academy/me')).j.slug))).json());
  const msg = '주색을 진한 빨간색으로 바꿔줘';
  await to.fill('#ahWorkInput', msg); await to.click('#ahWorkSend');
  await to.waitForFunction(() => /기다리기를 멈췄어요/.test(document.getElementById('ahStrip').textContent), null, { timeout: 20000 });
  ok('한도를 넘기면 기다리기를 멈추고 안내(작업 내용은 그대로)', /작업 내용은 그대로예요/.test(await to.locator('#ahStrip').innerText()));
  ok('입력이 보존되고 다시 보낼 수 있음', (await to.inputValue('#ahWorkInput')) === msg && !(await to.locator('#ahWorkInput').isDisabled()));
  ok('진행 오버레이가 사라짐', !(await to.locator('#ahBusy').isVisible()));
  ok('서버 작업본이 그대로', (await wcOf(toCtx)) === wcBeforeTimeout);
  ok('공개 홈페이지가 그대로', JSON.stringify(await (await fetch(BASE + '/api/academies/' + ((await api(toCtx, 'GET', '/api/tutor/academy/me')).j.slug))).json()) === pubBeforeTimeout);
  await to.screenshot({ path: __dirname + '/artifacts/progress-timeout-preserved.png' });
  await toCtx.close();

  // 4-c. 응답이 늦게 도착: 서버는 이미 처리했고 화면은 한도에서 멈춘 뒤 서버 상태로 맞춘다
  const lateCtx = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: PASS });
  await lateCtx.addInitScript(() => { window.__AH_TURN_TIMEOUT_MS__ = 5000; });
  const late = await lateCtx.newPage();
  await late.route('**/assistant/turn', async (route) => {
    try { const resp = await route.fetch(); await sleep(9000); await route.fulfill({ response: resp }); } catch (_) { /* 클라이언트가 이미 포기함 */ }
  });
  await late.goto(BASE + '/builder'); await late.waitForSelector('#ahWork:not([hidden])', { timeout: 60000 }); await late.waitForTimeout(1500);
  const curColor = (((await api(lateCtx, 'GET', '/api/tutor/academy/working-copy')).j.pageSettings || {}).builderDraft || {}).colorPrimary || '';
  const pick = curColor.toLowerCase() === '#16a34a' ? ['진한 파란색', '#2563eb'] : ['진한 초록색', '#16a34a'];
  await late.fill('#ahWorkInput', `주색을 ${pick[0]}으로 바꿔줘`); await late.click('#ahWorkSend');
  await late.waitForFunction(() => /수정이 이미 끝나 있었어요|기다리기를 멈췄어요/.test(document.getElementById('ahStrip').textContent), null, { timeout: 30000 });
  ok('늦게 끝난 수정은 서버 상태를 다시 읽어 반영하고 되돌리기를 안내', /이미 끝나 있었어요/.test(await late.locator('#ahStrip').innerText()), await late.locator('#ahStrip').innerText());
  ok('서버 작업본에 수정이 반영돼 있음', (((await api(lateCtx, 'GET', '/api/tutor/academy/working-copy')).j.pageSettings || {}).builderDraft || {}).colorPrimary === pick[1]);
  await lateCtx.close();

  ok('페이지 오류 없음', errors.length === 0, errors);
  console.log(`\n검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch((e) => { console.error('E2E 실패:', e); process.exit(1); });
