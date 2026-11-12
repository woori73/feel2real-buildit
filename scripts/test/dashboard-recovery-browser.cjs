// Actual browser assets with synthetic read-only API responses; never contacts production.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../..');
const resources = path.join(root, 'src/main/resources');
const evidence = path.join(root, 'build/verification/dashboard-recovery');
fs.mkdirSync(evidence, { recursive: true });
const read = p => fs.readFileSync(path.join(resources, p), 'utf8');

// Expand the existing navigation fragment for a static UI test. Server rendering/auth stays in Build Check.
function template(file) {
  const nav = read('templates/fragments/_admin_topnav.html').split('<th:block th:fragment="topnav(activeMenu)">')[1].split('</body>')[0];
  return read('templates/' + file)
    .replace(/<th:block th:replace="~\{fragments\/_admin_topnav :: head\}"><\/th:block>/g, '<link rel="stylesheet" href="/css/admin_topnav.css">')
    .replace(/<th:block th:replace="~\{fragments\/_admin_topnav :: topnav\('[^']+'\)\}"><\/th:block>/g, nav)
    .replace(/<th:block th:replace="[^\"]*"><\/th:block>/g, '');
}
const businesses = [{businessId: 1, name: '합성 테스트 학원'}, {businessId: 2, name: '합성 테스트 출판사'}];
const classes = [{classId: 1, id: 1, name: '합성 중1반', status: 'ACTIVE', active: true, memberCount: 2, weekdays: [], startTime: null}];
const students = [{studentId: 1, name: '합성학생가', weekdays: ['THU']}, {studentId: 2, name: '합성학생나', weekdays: ['THU']}];
function widgets(id) {
  return {generatedAt: '2026-11-12T08:00:00+09:00', widgets: [
    {id: 'easy-finance-summary', data: {cards: [
      {label: '수납', value: id === '2' ? 2200000 : 1200000}, {label: '지출', value: 400000}, {label: '미수금', value: 0}, {label: '원생 수', value: 0}
    ]}},
    {id: 'easy-overdue-students', data: {items: []}},
    {id: 'easy-recent-transactions', data: {items: [{title: '합성 교재비', subtitle: '10,000원', meta: '2026-11-11'}]}}
  ]};
}

(async () => {
  const browser = await chromium.launch({headless: true, ...(process.env.CHROMIUM_PATH ? {executablePath: process.env.CHROMIUM_PATH} : {})});
  const results = [];
  try {
    for (const role of ['ADMIN', 'TUTOR']) {
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}, locale: 'ko-KR', timezoneId: 'Asia/Seoul'});
      const page = await context.newPage();
      const errors = []; const writes = []; const unexpected = [];
      let financeError = false;
      page.on('pageerror', e => errors.push(e.message));
      await page.route('**/*', async route => {
        const request = route.request(); const url = new URL(request.url());
        if (request.method() !== 'GET') { writes.push(url.pathname); return route.fulfill({status: 405}); }
        const json = (body, status = 200) => route.fulfill({status, contentType: 'application/json', body: JSON.stringify(body)});
        if (url.origin !== 'http://dashboard.test') { unexpected.push(request.url()); return route.abort(); }
        if (url.pathname === '/dashboard') return route.fulfill({contentType: 'text/html', body: template('dashboard.html')});
        if (url.pathname === '/easy/attendance') return route.fulfill({contentType: 'text/html', body: template('easy/attendance.html')});
        if (/^\/(js|css|img)\//.test(url.pathname)) {
          const file = path.join(resources, 'static', url.pathname);
          if (fs.existsSync(file)) return route.fulfill({path: file});
          return route.fulfill({status: 404});
        }
        if (url.pathname === '/api/auth/me') return json({id: 5, role, displayName: '합성 테스트 계정'});
        if (url.pathname === '/api/studio-access') return json({allowed: role === 'ADMIN'});
        if (/\/businesses$/.test(url.pathname)) return json(businesses);
        if (url.pathname === '/api/dashboard/widgets') return json(widgets(url.searchParams.get('businessId')), financeError ? 500 : 200);
        if (url.pathname === '/academycraft/api/rea/integration/context') return json({academyId: 7, role: 'OWNER', builditRole: role, academyOptions: [{id: 7, name: '합성 테스트 학원'}]});
        if (/\/dashboard\/stats$/.test(url.pathname)) return json({studentCount: 2});
        if (/\/student-overview$/.test(url.pathname) || /\/students$/.test(url.pathname)) return json(students);
        if (/\/classes$/.test(url.pathname)) return json(classes);
        if (/\/consultations$/.test(url.pathname) || url.pathname === '/api/admin/academy-move/entries') return json([]);
        unexpected.push(url.pathname); return json({}, 404);
      });
      await page.goto('http://dashboard.test/dashboard');
      await page.locator('#ahStudentCount').filter({hasText: '재원생 2명'}).waitFor();
      await page.locator('#ahFinanceCards').filter({hasText: '1,200,000원'}).waitFor();
      assert.equal(await page.locator('#ahWorkClasses').getAttribute('href'), role === 'ADMIN' ? '/easy/attendance?businessId=1&tab=classes' : '/academycraft/screens/CLS-01-class-list.html');
      assert.equal(await page.locator('#ahWorkAttendance').getAttribute('href'), role === 'ADMIN' ? '/easy/attendance?businessId=1' : '/academycraft/screens/ATT-03-attendance-roster.html');
      assert.equal(await page.locator('#ahAdminNav').isVisible(), role === 'ADMIN');
      assert.equal(await page.locator('.ah-work-group a[href="/admin/hub/exam-requests"]').isVisible(), role === 'ADMIN');
      for (const href of ['/dashboard/info', '/problem-generator', '/testprep', '/ledger', '/builder', '/admissions/workspace', '/dashboard/assistant#all']) {
        assert.ok(await page.locator('a[href="' + href + '"]:visible').count(), href);
      }
      assert.equal(await page.locator('#agentStage, #statusPending, #uaCommandForm').count(), 0);
      await page.screenshot({path: path.join(evidence, role.toLowerCase() + '-desktop.png'), fullPage: true});
      await page.selectOption('#ahFinancePick', '2');
      await page.locator('#ahFinanceCards').filter({hasText: '2,200,000원'}).waitFor();
      assert.match(await page.locator('#ahStudentCount').innerText(), /2명/);
      financeError = true;
      await page.locator('#ahRefresh').click();
      await page.locator('#ahFinanceCards').filter({hasText: '실패'}).waitFor();
      assert.equal(await page.locator('#ahWorkStudents').isVisible(), true);
      financeError = false;
      await page.locator('#ahRefresh').click();
      await page.locator('#ahFinanceCards').filter({hasText: '2,200,000원'}).waitFor();
      await page.setViewportSize({width: 390, height: 844});
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await page.screenshot({path: path.join(evidence, role.toLowerCase() + '-mobile.png'), fullPage: true});
      if (role === 'ADMIN') {
        await page.locator('#ahWorkClasses').click();
        await page.getByRole('heading', {name: '반 만들기'}).waitFor();
        assert.equal(await page.locator('#tab-classes').isVisible(), true);
        assert.equal(await page.locator('#tab-sheet').isVisible(), false);
        assert.equal(await page.locator('#business').inputValue(), '1');
        await page.screenshot({path: path.join(evidence, 'admin-classes-mobile.png'), fullPage: true});
      }
      assert.deepEqual(errors, []); assert.deepEqual(writes, []); assert.deepEqual(unexpected, []);
      results.push({role, result: 'passed', checks: ['direct links', 'canonical student count', 'business switch', 'finance failure/recovery', '390px overflow', 'no writes', ...(role === 'ADMIN' ? ['class tab deep link'] : ['admin links hidden'])]});
      await context.close();
    }
    fs.writeFileSync(path.join(evidence, 'results.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify(results));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
