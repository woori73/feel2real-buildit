/*
 * 학원 홈 · 출결 · 학생 · 정보 화면 로컬 E2E (합성 테스트 자료만 사용).
 * 전제: README.md 절차로 BuildIt(8080)·AcademyCraft(8082, 이 브랜치 frontend 정적 경로)·테스트 DB가 떠 있고
 *       e2e/unified-assistant/fixtures.sql → e2e/academy-home/fixtures.sql 적용, 2026년 테스트 학생명단 업로드 완료.
 * 실행: node e2e/academy-home/run.cjs  (EVIDENCE_DIR로 캡처 폴더 지정)
 * 쿠키·storageState는 파일로 저장하지 않는다.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium, devices } = require('playwright');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const OUT = path.resolve(process.env.EVIDENCE_DIR || path.join(__dirname, '../../docs/verification/academy-home-20261111'));
fs.mkdirSync(OUT, { recursive: true });
const results = [];
async function step(name, fn) {
  try { await fn(); results.push({ name, ok: true }); console.log('PASS', name); }
  catch (e) { results.push({ name, ok: false, error: String(e && e.message || e).slice(0, 500) }); console.log('FAIL', name, '\n  ', e && e.message); }
}

async function login(browser, loginId, opts) {
  const ctx = await browser.newContext({ timezoneId: 'Asia/Seoul', locale: 'ko-KR', ...opts });
  const r = await ctx.request.post(BASE + '/api/auth/login', { data: { loginId, password: 'admin1234' }, headers: { Origin: BASE } });
  assert.ok(r.ok(), 'login ' + loginId + ' ' + r.status());
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('dialog', (d) => d.dismiss());
  return { ctx, page, errors };
}

async function countFromHome(page) {
  const t = await page.locator('#ahStudentCount').textContent();
  const m = /재원생 ([\d,]+)명/.exec(t || '');
  return m ? Number(m[1].replace(/,/g, '')) : null;
}

async function homeReady(page) {
  await page.goto(BASE + '/dashboard');
  await page.waitForFunction(() => document.getElementById('ahMain').getAttribute('aria-busy') === 'false', null, { timeout: 20000 });
}

(async () => {
  const browser = await chromium.launch();
  const desktop = { viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 };
  const phone = { ...devices['iPhone 13'] };

  for (const [label, opts] of [['desktop', desktop], ['mobile', phone]]) {
    const { ctx, page, errors } = await login(browser, 'admin', opts);
    await step(label + ': 학원 홈 첫 화면 — 학원명·재원생 링크·다음 수업 배너·오늘 수업·학생 챙기기·중점 업무', async () => {
      await homeReady(page);
      // 다중 학원 관리자는 선택 상자만, 이름은 한 번만 보인다.
      assert.equal(await page.locator('#ahAcademyPick').isVisible(), true);
      assert.equal(await page.locator('#ahAcademyName').isVisible(), false);
      assert.match(await page.locator('#ahStudentCount').textContent(), /재원생 \d+명/);
      assert.match(await page.locator('#ahBannerKind').textContent(), /지금 진행 중인 수업|다음 수업|오늘 수업 종료/);
      assert.ok(await page.locator('#ahLessons .ah-lesson').count() >= 2);
      const times = await page.locator('#ahLessons .ah-lesson-time').allTextContents();
      assert.deepEqual(times, [...times].sort(), '오늘 수업은 시간순');
      assert.ok(await page.locator('#ahCare li').count() >= 1);
      assert.match(await page.locator('#ahFocus').textContent(), /이전 계약서 확인/);
      for (const gone of ['#siMenu', '#aiFab', '#agentStage', '#uaUploadForm', '#kpiRow', '#statusPending'])
        assert.equal(await page.locator(gone).count(), 0, gone);
      const width = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
      assert.ok(width[0] <= width[1], '가로 스크롤 없음 ' + width);
      await page.screenshot({ path: path.join(OUT, label + '-01-home.png'), fullPage: true });
    });

    await step(label + ': 재원생 수 = 학생 명단 전체 인원(같은 기준)', async () => {
      const n = await countFromHome(page);
      await page.locator('#ahStudentCount').click();
      await page.waitForURL(/MBR-01-student-list\.html/);
      await page.locator('#count').filter({ hasText: '재원생 전체' }).waitFor();
      const m = /재원생 전체 (\d+)명/.exec(await page.locator('#count').textContent());
      assert.equal(Number(m[1]), n);
      assert.equal(await page.locator('#list .student-card').count(), n);
      await page.screenshot({ path: path.join(OUT, label + '-02-students.png'), fullPage: true });
    });

    await step(label + ': 오늘 수업 → 출결부 → 출석 저장 → 새로고침 후 유지', async () => {
      await homeReady(page);
      const bannerCount = Number(/수강생 (\d+)명/.exec(await page.locator('#ahBannerMeta').textContent())[1]);
      await page.locator('#ahBannerAction').click();
      await page.waitForURL(/ATT-03-attendance-roster\.html\?classId=\d+&date=\d{4}-\d{2}-\d{2}/);
      await page.locator('#summary').filter({ hasText: '전체' }).waitFor();
      assert.equal(Number(/전체 (\d+)명/.exec(await page.locator('#summary').textContent())[1]), bannerCount, '배너 수강생 = 출결부 인원');
      const before = await page.locator('#summary').textContent();
      const row = page.locator('#list .card').filter({ has: page.locator('.st-NONE') }).first();
      const name = await row.locator('.nm').textContent();
      await row.locator('select[name=eventType]').first().selectOption(label === 'desktop' ? 'CHECK_IN' : 'LATE');
      await row.locator('input[name=note]').first().fill('E2E 테스트 메모');
      await row.getByRole('button', { name: '저장' }).click();
      await page.locator('#msg.ok').waitFor();
      await page.reload();
      await page.locator('#summary').filter({ hasText: '전체' }).waitFor();
      const after = page.locator('#list .card').filter({ hasText: name });
      assert.match(await after.locator('.status').textContent(), label === 'desktop' ? /출석/ : /지각/);
      assert.notEqual(await page.locator('#summary').textContent(), before);
      await page.screenshot({ path: path.join(OUT, label + '-03-attendance-saved.png'), fullPage: true });
    });

    await step(label + ': 기존 기록 정정은 사유 없이 저장되지 않고, 사유가 있으면 정정된다', async () => {
      const card = page.locator('#list .card').filter({ has: page.locator('[aria-expanded]') }).first();
      assert.equal(await card.locator('form.correct-box').isVisible(), false, '정정 칸은 누르기 전 닫혀 있음');
      await card.getByRole('button', { name: '정정', exact: true }).click();
      const box = card.locator('form.correct-box');
      await box.locator('select[name=eventType]').selectOption('ABSENT');
      await box.getByRole('button', { name: '정정 저장' }).click();
      assert.equal(await box.locator('input[name=reason]').evaluate((e) => e.validity.valueMissing), true, '사유 필수');
      await box.locator('input[name=reason]').fill('E2E 정정 사유');
      await box.getByRole('button', { name: '정정 저장' }).click();
      await page.locator('#msg.ok').filter({ hasText: '정정' }).waitFor();
      await page.reload();
      await page.locator('#list .events').filter({ hasText: '정정됨' }).first().waitFor();
    });
    assert.deepEqual(errors, [], label + ' page errors');
    await ctx.close();
  }

  // 학생 수정·상세 (일반 폼, prompt 없음)
  {
    const { ctx, page } = await login(browser, 'admin', desktop);
    await step('학생 상세에서 한 폼으로 수정 → 새로고침 후 유지, 소속 반·출결·상담 표시', async () => {
      await page.goto(BASE + '/academycraft/screens/MBR-01-student-list.html?studentId=12');
      await page.locator('#detail h2').filter({ hasText: '테스트나' }).waitFor();
      assert.match(await page.locator('#detail').textContent(), /소속 반[\s\S]*중1 문법반[\s\S]*상담 기록[\s\S]*보강 일정 문의/);
      await page.getByRole('button', { name: '정보 수정' }).click();
      await page.locator('#f-school').fill('가상중학교(수정)');
      await page.getByRole('button', { name: '저장' }).click();
      await page.locator('#msg.ok').waitFor();
      await page.reload();
      await page.locator('#detail').filter({ hasText: '가상중학교(수정)' }).waitFor();
      await page.screenshot({ path: path.join(OUT, 'desktop-04-student-detail.png'), fullPage: true });
    });
    await step('반 필터·검색은 전체 인원과 분리된다', async () => {
      await page.goto(BASE + '/academycraft/screens/MBR-01-student-list.html?classId=202');
      await page.locator('#count').filter({ hasText: '중2 독해반' }).waitFor();
      const t = await page.locator('#count').textContent();
      const total = Number(/재원생 전체 (\d+)명/.exec(t)[1]), inClass = Number(/중2 독해반 (\d+)명/.exec(t)[1]);
      assert.ok(inClass < total, t);
      assert.equal(await page.locator('#list .student-card').count(), inClass);
    });
    await ctx.close();
  }

  // 강사(TEACHER 연결): 학생 수정은 권한 없음 안내
  {
    const { ctx, page } = await login(browser, 'tutor', desktop);
    await step('강사 연결 계정: 홈은 열리고, 학생 정보 수정은 원장 권한 안내로 끝난다', async () => {
      await homeReady(page);
      assert.match(await page.locator('#ahStudentCount').textContent(), /재원생 \d+명/);
      assert.match(await page.locator('#ahFocus').textContent(), /이 계정에 등록된 중점 업무가 없습니다/);
      await page.goto(BASE + '/academycraft/screens/MBR-01-student-list.html?studentId=13');
      await page.locator('#detail h2').waitFor();
      await page.getByRole('button', { name: '정보 수정' }).click();
      await page.locator('#f-grade').fill('중3');
      await page.getByRole('button', { name: '저장' }).click();
      await page.locator('#msg.err').waitFor();
      assert.match(await page.locator('#msg').textContent(), /권한/);
    });
    await ctx.close();
  }

  // 학원 전환·빈 학원·조회 오류·권한 없음
  {
    const { ctx, page } = await login(browser, 'admin', desktop);
    await step('학원 전환: 이전 학원 숫자가 남지 않고 빈 학원은 수업 없음으로 표시', async () => {
      await homeReady(page);
      const first = await page.locator('#ahStudentCount').textContent();
      await page.locator('#ahAcademyPick').selectOption('8');
      await page.waitForFunction((prev) => {
        const t = document.getElementById('ahStudentCount').textContent;
        return t !== prev;
      }, first);
      await page.waitForFunction(() => document.getElementById('ahMain').getAttribute('aria-busy') === 'false');
      assert.match(await page.locator('#ahBannerTitle').textContent(), /오늘 등록된 수업이 없습니다/);
      assert.match(await page.locator('#ahStudentCount').textContent(), /재원생 1명/);
      await page.screenshot({ path: path.join(OUT, 'desktop-05-empty-academy.png'), fullPage: true });
      await page.locator('#ahAcademyPick').selectOption('7');
      await page.waitForFunction(() => document.getElementById('ahMain').getAttribute('aria-busy') === 'false');
    });
    await step('조회 오류·권한 없음은 0명이 아니라 상태로 표시', async () => {
      await page.route('**/dashboard/stats', (r) => r.fulfill({ status: 500, body: '{}' }));
      await homeReady(page);
      assert.equal(await page.locator('#ahStudentCount').textContent(), '재원생 조회 실패');
      await page.unroute('**/dashboard/stats');
      await page.route('**/dashboard/stats', (r) => r.fulfill({ status: 403, body: '{}' }));
      await homeReady(page);
      assert.equal(await page.locator('#ahStudentCount').textContent(), '재원생 조회 권한 없음');
      await page.unroute('**/dashboard/stats');
    });
    await step('#ai·?ask= 호환: AI 업무 비서 화면으로 이동, #all은 홈', async () => {
      await page.goto(BASE + '/dashboard#ai');
      await page.waitForURL(/\/dashboard\/assistant#ai$/);
      await page.goto(BASE + '/dashboard?ask=' + encodeURIComponent('테스트 부탁'));
      await page.waitForURL(/\/dashboard\/assistant\?ask=/);
      await page.waitForFunction(() => document.getElementById('uaCommand') && document.getElementById('uaCommand').value === '테스트 부탁', null, { timeout: 20000 });
      await page.goto(BASE + '/dashboard#all');
      await page.locator('#ahBanner').waitFor();
      assert.ok(page.url().endsWith('/dashboard#all'));
    });
    await step('키보드: 상단 메뉴와 출결부 열기에 Tab으로 닿는다', async () => {
      await homeReady(page);
      const seen = [];
      for (let i = 0; i < 12; i++) { await page.keyboard.press('Tab'); seen.push(await page.evaluate(() => document.activeElement && (document.activeElement.id || document.activeElement.textContent.trim()))); }
      assert.ok(seen.includes('학원 홈') && seen.includes('ahNavAttendance'), seen.join('|'));
      assert.ok(seen.includes('ahStudentCount') && seen.includes('ahBannerAction'), seen.join('|'));
    });
    await ctx.close();
  }

  // 정보: 기존 자료(학생명단)·신규 자료(학원이사 PDF)·스캔 PDF·명단 대조
  for (const [label, opts] of [['desktop', desktop], ['mobile', phone]]) {
    const { ctx, page, errors } = await login(browser, 'admin', opts);
    await step(label + ': 정보 — 제목 → 원문(표·페이지) → 원본 파일, 스캔 PDF는 OCR 필요 안내', async () => {
      await page.goto(BASE + '/dashboard/info');
      await page.locator('.ai-card').first().waitFor();
      const roster = page.locator('.ai-card').filter({ hasText: '2026년 학생명단(시간표)' });
      await roster.locator('summary', { hasText: '본문 읽기' }).click();
      await roster.locator('.ai-table-wrap table thead th').first().waitFor();
      const move = page.locator('.ai-card').filter({ hasText: '이전 계약서 확인' });
      await move.locator('summary', { hasText: '본문 읽기' }).click();
      await move.locator('.ai-page h4', { hasText: '1쪽' }).waitFor();
      assert.match(await move.locator('.ai-body-in').textContent(), /학원이사 체크리스트 \(테스트 자료\)[\s\S]*2쪽/);
      const scan = page.locator('.ai-card').filter({ hasText: 'blank-scan.pdf' });
      await scan.locator('summary', { hasText: '본문 읽기' }).click();
      await scan.locator('.ai-reason').filter({ hasText: 'OCR' }).waitFor();
      assert.equal(await scan.locator('.ai-para').count(), 0, '스캔본에 지어낸 본문 없음');
      const width = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
      assert.ok(width[0] <= width[1], '가로 스크롤 없음 ' + width);
      await page.screenshot({ path: path.join(OUT, label + '-06-info.png'), fullPage: true });
    });
    assert.deepEqual(errors, [], label + ' info page errors');
    await ctx.close();
  }

  {
    const { ctx, page } = await login(browser, 'admin', desktop);
    await step('명단 반영: 보류 연결·제외 → 추가 후보는 초안 승인으로만 등록 → 재대조 시 중복 없음, 재원생 수 = 명단', async () => {
      await page.goto(BASE + '/dashboard/info');
      const roster = page.locator('.ai-card').filter({ hasText: '2026년 학생명단(시간표)' });
      await roster.locator('summary', { hasText: '명단 반영 확인' }).click();
      const sum = roster.locator('.ai-sum').filter({ hasText: '원본 기록' });
      await sum.waitFor();
      const parse = async () => { const t = await sum.textContent(); const n = (k) => Number(new RegExp(k + ' (\\d+)').exec(t)[1]); return { t, names: Number(/서로 다른 이름 (\d+)명/.exec(t)[1]), existing: n('기존'), added: n('추가 후보'), hold: n('연결 보류'), excluded: n('제외') }; };
      const s0 = await parse();
      assert.match(s0.t, /합계 일치/);
      // 보류 → 테스트가를 학생으로 연결
      const rowA = roster.locator('.ai-rows li').filter({ hasText: '테스트가' });
      await rowA.getByRole('button', { name: '이 학생으로 연결' }).click();
      await roster.locator('.ai-group h4', { hasText: '기존(연결됨)' }).waitFor();
      // 보류 → 비활성 학생과 이름만 같은 테스트마는 제외
      await roster.locator('.ai-rows li').filter({ hasText: '테스트마' }).getByRole('button', { name: '명단에서 제외' }).click();
      await roster.locator('.ai-group h4', { hasText: '제외' }).waitFor();
      const s1 = await parse();
      assert.equal(s1.existing, s0.existing + 1);
      assert.equal(s1.excluded, s0.excluded + 1);
      assert.equal(s1.existing + s1.added + s1.hold + s1.excluded, s1.names);
      // 추가 후보 → 초안 → 승인
      const countBefore = await (await ctx.request.get(BASE + '/academycraft/api/rea/7/dashboard/stats')).json();
      await roster.locator('.ai-rows li').filter({ hasText: '신규가' }).locator('input[type=checkbox]').check();
      await roster.getByRole('button', { name: '선택한 이름을 학생 등록 초안으로' }).click();
      await roster.getByRole('button', { name: '승인하고 등록' }).click();
      await roster.locator('.ai-sum').filter({ hasText: '신규가 학생 등록을 원장 승인으로 저장했습니다' }).waitFor({ timeout: 20000 });
      await roster.locator('.ai-rows li').filter({ hasText: '신규가' }).filter({ hasText: '원장이 연결한 재원 학생' }).waitFor({ timeout: 20000 });
      const s2 = await parse();
      assert.equal(s2.added, 0, '재대조에서 다시 추가 후보로 세지 않음');
      assert.equal(s2.existing, s1.existing + 1, '등록한 학생은 그 레코드 ID로 연결');
      assert.equal(s2.existing + s2.added + s2.hold + s2.excluded, s2.names);
      // 다시 열어 대조(재처리)해도 같은 결과·중복 없음
      await page.reload();
      await roster.locator('summary', { hasText: '명단 반영 확인' }).click();
      await sum.waitFor();
      const s3 = await parse();
      assert.deepEqual([s3.existing, s3.added, s3.hold, s3.excluded], [s2.existing, s2.added, s2.hold, s2.excluded]);
      await page.screenshot({ path: path.join(OUT, 'desktop-07-roster-reconcile.png'), fullPage: true });
      const stats = await (await ctx.request.get(BASE + '/academycraft/api/rea/7/dashboard/stats')).json();
      const list = await (await ctx.request.get(BASE + '/academycraft/api/rea/7/students')).json();
      assert.equal(stats.studentCount, countBefore.studentCount + 1);
      assert.equal(stats.studentCount, new Set(list.map((s) => s.id)).size);
    });
    await ctx.close();
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(results, null, 2));
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  process.exit(failed.length ? 1 : 0);
})();
