// 실제 AI(Muse Spark)로 처음부터 끝까지: 최초 생성 → 연속 대화 수정 → 미리보기 → 되돌리기 → 게시 승인.
// 격리된 CI DB·로컬 앱에서만 실행한다(운영 접근 없음). 키는 앱 환경변수로만 주입되며 이 스크립트는 다루지 않는다.
const { launch, newCtx, BASE } = require('../visual-editor/lib');

(async () => {
  const b = await launch();
  const rows = []; const turns = [];
  const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const errors = [];
  const ctx = await newCtx(b, { viewport: { width: 1280, height: 900 } }, { loginId: 'e2e-live', password: 'admin1234' });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  page.on('response', async (r) => {
    if (/\/assistant\/turn$/.test(r.url()) && r.request().method() === 'POST') {
      try { const j = await r.json(); turns.push({ engine: j.engine, aiMs: j.aiMs, edited: j.edited, failed: j.failed, changes: (j.changes || []).length, skipped: (j.skipped || []).length, question: !!j.question, created: !!j.created, t: Date.now() }); } catch (_) { /* 무시 */ }
    }
  });
  const api = async (m, u, data) => { const r = await ctx.request.fetch(BASE + u, { method: m, data }); let j = null; try { j = await r.json(); } catch (_) {} return { s: r.status(), j }; };
  const wait = (ms) => page.waitForTimeout(ms);
  const idle = () => page.waitForFunction(() => !document.getElementById('ahWorkInput').disabled, null, { timeout: 150000 });
  const wcDraft = async () => (await api('GET', '/api/tutor/academy/working-copy')).j.pageSettings;
  const say = async (text) => { const t0 = Date.now(); await page.fill('#ahWorkInput', text); await page.click('#ahWorkSend'); await idle(); await wait(600); return Date.now() - t0; };

  // 1. 최초 생성(실제 AI가 쓰이는 경로 포함)
  await page.goto(BASE + '/builder'); await page.waitForSelector('#ahStartForm');
  await page.fill('#ahName', '새봄영어학원');
  await page.fill('#ahDesc', '초등·중등 학생에게 영어회화와 내신을 소수정예로 가르치는, 따뜻하고 친근한 학원이에요');
  const tCreate = Date.now(); await page.click('#ahStartBtn');
  for (let i = 0; i < 5 && !(await page.locator('#ahWork:not([hidden])').count()); i++) {
    await page.waitForFunction(() => /지역|과목|대상/.test(document.getElementById('ahMessages').innerText) || !document.getElementById('ahWork').hidden, null, { timeout: 150000 });
    if (await page.locator('#ahWork:not([hidden])').count()) break;
    const tail = (await page.locator('#ahMessages').innerText()).split('\n').slice(-4).join(' ');
    const ans = /지역/.test(tail) ? '서울 강남구' : (/과목/.test(tail) ? '영어' : '초등학생, 중학생');
    await page.fill('#ahInput', ans); await page.click('#ahSend'); await wait(1500);
  }
  await page.waitForSelector('#ahWork:not([hidden])', { timeout: 150000 }); await wait(3000);
  const createMs = Date.now() - tCreate;
  const d0 = (await wcDraft()).builderDraft || {};
  ok('최초 생성 완료(작업 화면 진입), 소요 ' + Math.round(createMs / 1000) + 's', true);
  ok('최초 생성에 디자인 프리셋이 정해짐', !!d0.designPreset, d0);
  const me = (await api('GET', '/api/tutor/academy/me')).j; const slug = me.slug;
  ok('최초 게시 전 공개되지 않음', (await fetch(BASE + '/api/academies/' + slug)).status !== 200 || (await (await fetch(BASE + '/api/academies/' + slug)).json()).status !== 'PUBLISHED');
  await page.click('#ahPublish'); await page.waitForSelector('#ahConfirm:not([hidden])'); await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /게시됨/.test(document.getElementById('ahStatus').innerText), null, { timeout: 30000 });
  const pubSnap = JSON.stringify(await (await fetch(BASE + '/api/academies/' + slug)).json());
  const pubSame = async () => JSON.stringify(await (await fetch(BASE + '/api/academies/' + slug)).json()) === pubSnap;
  const base = await wcDraft(); const baseJson = JSON.stringify(base);

  // 2. 실제 AI 연속 수정
  const steps = [
    ['전체적으로 더 고급스럽고 신뢰가 가는 분위기로 바꿔줘', async (p) => p.builderDraft.designPreset !== d0.designPreset || p.builderDraft.typographyPreset !== d0.typographyPreset, '포괄적 디자인(복합 변경)'],
    ['주색을 진한 초록색으로 바꿔줘', async (p) => /^#/.test(p.builderDraft.colorPrimary || ''), '색상'],
    ['수업 영상 섹션은 숨겨주고 상담 신청 섹션을 맨 위로 올려줘', async (p) => (p.sectionVisibility || {})['sec-videos'] === false && (p.sectionOrder || [])[1] === 'sec-cta', '섹션 숨김+순서(복합)'],
    ['학원 소개 제목을 아이의 속도에 맞춰 함께 걷는다는 느낌의 따뜻한 문장으로 바꿔줘', async (p) => !!p.builderDraft.introHeading, '문구 수정'],
    // 최초 생성이 이미 같은 효과를 골랐을 수 있으니 지금과 다른 효과를 요청한다("이미 적용" 응답과 구분).
    [d0.motionStyle === 'slide' ? '스크롤할 때 자연스럽게 서서히 등장하게 해줘' : '카드들이 스크롤할 때 아래에서 차례대로 올라오게 해줘',
      async (p) => !!p.builderDraft.motionStyle && p.builderDraft.motionStyle !== d0.motionStyle, '등장 효과(자산)'],
    ['학교처럼 신뢰감 있는 학교형으로 바꿔줘', async (p) => !!p.builderDraft.designAsset, '디자인 자산 선택']];
  let depth = 0; let editedCount = 0; const stepMs = [];
  for (const [text, check, label] of steps) {
    const ms = await say(text); stepMs.push(ms); depth++;
    const t = turns[turns.length - 1] || {};
    const p = await wcDraft();
    const edited = t.edited === true;
    if (edited) editedCount++;
    ok(`수정 ${depth}: ${label} — 엔진=${t.engine} 서버AI=${t.aiMs}ms 화면=${ms}ms 변경=${t.changes}건`, edited && (await check(p)), { t, builderDraft: p.builderDraft });
    ok('  → 변경 요약 표시', (await page.locator('#ahChanges li').count()) >= 1);
    ok('  → 공개 홈페이지 그대로', await pubSame());
    if (depth === 1) await wait(2500);
  }
  const before = await wcDraft();
  await say('후기를 3개 지어서 소개 문단에 넣어줘');
  const tn = turns[turns.length - 1] || {};
  const after = await wcDraft();
  ok('말하지 않은 후기·실적은 만들어 넣지 않는다(소개 문단에 숫자·후기 없음)', JSON.stringify(after) === JSON.stringify(before) || !/후기|합격|\d{2,}/.test(JSON.stringify(after.builderDraft.introBodySecondary || '')), { tn });
  ok('  → 공개 홈페이지 그대로', await pubSame());

  // 3. 3기기 미리보기
  for (const dev of ['mobile', 'tablet', 'desktop']) {
    const btn = page.locator(`[data-dev="${dev}"]`).first();
    if (await btn.count()) { await btn.click(); await wait(1200); ok(`${dev} 미리보기 가로 스크롤 없음`, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)); }
  }

  // 4. 되돌리기
  let n = parseInt(await page.locator('#ahUndoCount').innerText(), 10);
  ok('되돌리기 단계가 실제 적용된 수정 횟수와 같다', n === editedCount, { n, editedCount });
  while (n > 0) { await page.click('#ahUndo'); await wait(1500); n = parseInt(await page.locator('#ahUndoCount').innerText(), 10); }
  const restored = JSON.stringify(await wcDraft());
  const rb = JSON.parse(restored).builderDraft || {}; const ob = JSON.parse(baseJson).builderDraft || {};
  ok('전부 되돌리면 게시 직후 상태로 복원(builderDraft 동일)', JSON.stringify(rb) === JSON.stringify(ob), { rb, ob });
  ok('되돌린 뒤에도 공개 홈페이지 그대로', await pubSame());

  // 5. 승인 후 게시
  await say('더 고급스럽게 바꿔줘');
  await page.click('#ahPublish'); await page.waitForSelector('#ahConfirm:not([hidden])');
  ok('게시 확인 창이 먼저 뜨고 승인 전 공개는 그대로', await pubSame());
  await page.click('#ahConfirmOk'); await page.waitForFunction(() => /게시됨/.test(document.getElementById('ahStatus').innerText) && !/수정 있음/.test(document.getElementById('ahStatus').innerText), null, { timeout: 30000 });
  ok('승인 후 공개 홈페이지가 바뀜', !(await pubSame()));
  ok('페이지 오류 없음', errors.length === 0, errors);

  // 요약
  const aiTurns = turns.filter(t => t.engine && String(t.engine).startsWith('AI'));
  const ms = aiTurns.map(t => t.aiMs).filter(x => x != null).sort((a, c) => a - c);
  const pct = (q) => ms.length ? ms[Math.min(ms.length - 1, Math.ceil(ms.length * q) - 1)] : null;
  console.log('\n== 실제 AI 연속 흐름 요약 ==');
  console.log(`턴 ${turns.length}개 중 AI 엔진 ${aiTurns.length}개, 규칙 폴백 ${turns.filter(t => t.engine === 'RULE').length}개`);
  console.log(`서버 AI 처리시간 p50=${pct(0.5)}ms p95=${pct(0.95)}ms max=${ms[ms.length - 1]}ms, 화면 기준 수정 소요(ms)=${JSON.stringify(stepMs)}`);
  console.log(`최초 생성 소요 ${createMs}ms`);
  console.log(`검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch(e => { console.error('E2E 실패:', e); process.exit(1); });
