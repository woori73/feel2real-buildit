const { test, expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');

test.use({ isMobile: true, hasTouch: true });

const resources = path.resolve(__dirname, '../../src/main/resources');
const origin = 'http://codi.test';
const conversation = '/api/dashboard/assistant/v1/conversation';
const question = '오늘 수업에서 확인할 내용은 무엇인가요?';
const answer = Array.from({ length: 12 }, (_, index) =>
  `${index + 1}번 문항을 함께 검토해 주세요. 학생이 어려워한 표현을 다시 읽고, 다음 문장에 적용해 보는 수업입니다.`
).join('\n\n');

// Keep layout and interaction code identical to the app while controlling delayed and failed API turns.
async function openCodi(page, width, onConversation, overview = {}, apiFixtures = {}) {
  await page.setViewportSize({ width, height: 780 });
  const fixtures = {
    '/api/auth/me': { id: 5, role: 'ADMIN' },
    '/api/dashboard/businesses': [],
    '/api/dashboard/assistant/v1/progress': { step: 0, completed: true, version: 1 },
    '/api/dashboard/assistant/v1/overview': { asOf: '2026-11-26T01:00:00Z', ...overview },
    '/api/dashboard/assistant/v1/drafts': [],
    '/api/dashboard/assistant/v1/audit': [],
    ...apiFixtures
  };
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    if (url.pathname === conversation && onConversation) return onConversation(route);
    if (Object.hasOwn(fixtures, url.pathname)) return route.fulfill({ json: fixtures[url.pathname] });
    if (url.pathname === '/dashboard/assistant') {
      return route.fulfill({ path: path.join(resources, 'templates/dashboard_assistant.html') });
    }
    if (url.pathname === '/dashboard') {
      return route.fulfill({ path: path.join(resources, 'templates/dashboard.html') });
    }
    if (url.pathname.startsWith('/api/')) return route.fulfill({ status: 404, json: {} });
    const asset = path.join(resources, 'static', url.pathname);
    try {
      await fs.access(asset);
    } catch {
      return route.fulfill({ status: 404, body: '' });
    }
    return route.fulfill({ path: asset });
  });
  await page.goto(origin + '/dashboard/assistant');
  await page.waitForFunction(() => window.unifiedAssistant?.ready());
}

function bottomGap(log) {
  return log.evaluate(el => el.scrollHeight - el.clientHeight - el.scrollTop);
}

for (const width of [360, 390, 430]) {
  test(`${width}px에서 긴 답변과 두 줄 입력의 경계, 읽던 위치, 최신 이동을 유지한다`, async ({ page }) => {
    let finish;
    const reply = new Promise(resolve => { finish = resolve; });
    const sent = [];
    await openCodi(page, width, async route => {
      sent.push(route.request().postDataJSON().message);
      await reply;
      return route.fulfill({ json: { message: answer, issues: [], drafts: [] } });
    });
    const entry = page.locator('#uaCommand');
    const log = page.getByRole('log');
    const jump = page.getByRole('button', { name: '최신 메시지로 이동' });
    await expect(entry).toHaveValue('');
    await expect(page.locator('#codiAvatar')).toContainText('F2R 코디');
    await expect(page.locator('#codiNav [aria-current="page"]')).toHaveAccessibleName('대화');
    await expect(jump).toBeHidden();
    const originalHeight = (await entry.boundingBox()).height;
    await entry.fill(question + '\n이전 수업 내용도 알려 주세요.');
    await expect.poll(async () => (await entry.boundingBox()).height).toBeGreaterThan(originalHeight);
    await page.evaluate(text => window.unifiedAssistant.bubble('assistant', text), answer);
    await expect.poll(() => bottomGap(log)).toBeLessThanOrEqual(2);
    await page.getByRole('button', { name: '메시지 보내기' }).click();
    await expect.poll(() => sent.length).toBe(1);
    await log.evaluate(el => { el.scrollTop = 40; });
    await expect(jump).toBeVisible();
    const readingAt = await log.evaluate(el => el.scrollTop);
    finish();
    await expect(log.locator('.ua-assistant').last()).toContainText('12번');
    await expect(entry).toHaveValue('');
    await expect.poll(() => log.evaluate(el => el.scrollTop)).toBe(readingAt);
    await jump.click();
    await expect(jump).toBeHidden();
    await expect.poll(() => bottomGap(log)).toBeLessThanOrEqual(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    const avatar = await page.locator('#codiAvatar').boundingBox();
    const reader = await log.boundingBox();
    expect(reader.y).toBeGreaterThanOrEqual(avatar.y + avatar.height - 1);
    const last = await log.locator('.ua-assistant p').last().boundingBox();
    const input = await entry.boundingBox();
    expect(last.y + last.height).toBeLessThanOrEqual(input.y);
  });
}

test('한글 조합 Enter, 전송 대기 중 재제출, 실패 후 같은 내용 재시도를 처리한다', async ({ page }) => {
  let fail;
  const firstTurn = new Promise(resolve => { fail = resolve; });
  const requests = [];
  await openCodi(page, 390, async route => {
    requests.push(route.request().postDataJSON());
    if (requests.length === 1) {
      await firstTurn;
      return route.fulfill({ status: 503, json: { message: '회귀 시험 연결 실패' } });
    }
    return route.fulfill({ json: { message: '다시 받은 응답', drafts: [] } });
  });
  const entry = page.locator('#uaCommand');
  await entry.fill(question);
  await entry.dispatchEvent('compositionstart');
  await entry.dispatchEvent('keydown', { key: 'Enter', code: 'Enter', isComposing: true });
  await entry.dispatchEvent('compositionend');
  await expect(entry).toHaveValue(question);
  expect(requests).toHaveLength(0);
  await entry.press('Enter');
  await expect.poll(() => requests.length).toBe(1);
  await page.locator('#uaCommandForm').evaluate(form => form.requestSubmit());
  fail();
  await expect(page.getByRole('log')).toContainText('회귀 시험 연결 실패');
  await expect(entry).toHaveValue(question);
  expect(requests).toHaveLength(1);
  await page.getByRole('button', { name: '메시지 보내기' }).click();
  await expect(page.getByRole('log')).toContainText('다시 받은 응답');
  await expect(entry).toHaveValue('');
  expect(requests.map(request => request.message)).toEqual([question, question]);
});

test('키보드 높이 변화에 맞춰 입력창을 배치하고 닫은 뒤 학원관리로 돌아간다', async ({ page }) => {
  await openCodi(page, 390, route => route.fulfill({ json: { message: '확인했어요.', drafts: [] } }));
  const nav = page.locator('#codiNav');
  const entry = page.locator('#uaCommand');
  await expect(nav).toBeVisible();
  await entry.focus();
  // A desktop Chromium test has no system IME. Simulate the reported visible height only.
  await entry.evaluate(() => {
    Object.defineProperty(window.visualViewport, 'height', { configurable: true, get: () => 430 });
    window.visualViewport.dispatchEvent(new Event('resize'));
  });
  await expect(nav).toBeHidden();
  const avatarBox = await page.locator('#codiAvatar').boundingBox();
  const firstLine = await page.locator('#uaDrafts .ua-assistant p').first().boundingBox();
  const menuBox = await page.locator('#siMenuBtn').boundingBox();
  const historyBox = await page.locator('#uaHistoryRefresh').boundingBox();
  expect(avatarBox.y).toBeGreaterThanOrEqual(0);
  expect(avatarBox.x).toBeGreaterThanOrEqual(menuBox.x + menuBox.width);
  expect(avatarBox.x + avatarBox.width).toBeLessThanOrEqual(historyBox.x);
  expect(avatarBox.y + avatarBox.height).toBeLessThanOrEqual(firstLine.y + 1);
  const inputBox = await entry.boundingBox();
  expect(firstLine.y + firstLine.height).toBeLessThan(inputBox.y);
  expect(inputBox.y + inputBox.height).toBeLessThanOrEqual(431);
  await entry.fill(Array(5).fill(question).join('\n'));
  await page.evaluate(text => window.unifiedAssistant.bubble('assistant', text), answer);
  const reader = await page.getByRole('log').boundingBox();
  const expandedInput = await entry.boundingBox();
  expect(expandedInput.height).toBeGreaterThan(inputBox.height);
  expect(expandedInput.y + expandedInput.height).toBeLessThanOrEqual(431);
  expect(reader.y).toBeGreaterThanOrEqual(avatarBox.y + avatarBox.height - 1);
  expect(reader.height).toBeGreaterThan(44);
  expect(reader.y + reader.height).toBeLessThanOrEqual(expandedInput.y + 1);
  await page.getByRole('button', { name: '메시지 보내기' }).tap();
  await expect(page.getByRole('log')).toContainText('확인했어요.');
  await expect(entry).toBeFocused();
  await expect(nav).toBeHidden();
  await entry.evaluate(el => {
    delete window.visualViewport.height;
    window.visualViewport.dispatchEvent(new Event('resize'));
    el.blur();
  });
  await expect(nav).toBeVisible();
  await page.locator('#siMenuBtn').click();
  await page.locator('#siMenu').getByRole('link', { name: '학원관리 대시보드' }).click();
  await expect(page).toHaveURL(origin + '/dashboard');
});

test('새싹 코디가 기본으로 표시되고 외형 선택과 자료 첨부가 동작한다', async ({ page }) => {
  await openCodi(page, 390);
  const portrait = page.locator('#codiPortrait');
  const menu = page.locator('#siMenuBtn');
  async function checkPortrait(key) {
    await expect(portrait).toBeVisible();
    await expect(page.locator('#codiAvatar')).toHaveAttribute('data-look', key);
    if (key === 'codi') {
      await expect(page.locator('#codiAvatar')).toHaveAttribute('data-pose', 'idle');
    } else {
      await expect(portrait).toHaveAttribute('src', `/img/landing/home-agent-${key}.webp`);
    }
    await expect.poll(() => portrait.evaluate(el => el.complete && el.naturalWidth > 0)).toBe(true);
  }
  await checkPortrait('codi');
  for (const [key, name] of [['suni', '서수니'], ['seven', '세븐'], ['codi', '새싹 코디']]) {
    await menu.click();
    const choice = page.locator('#codiCharacter').getByRole('button', { name });
    await choice.click();
    await expect(page.locator(`#codiCharacter [data-codi-look="${key}"]`)).toHaveAttribute('aria-pressed', 'true');
    await checkPortrait(key);
    await page.reload();
    await page.waitForFunction(() => window.unifiedAssistant?.ready());
    await checkPortrait(key);
  }
  await page.getByRole('button', { name: '업무 자료 첨부' }).click();
  await expect(page.locator('#uaFile')).toBeFocused();
  await expect(page.locator('#uaUploadForm')).toBeInViewport();
  await expect(page.locator('#uaFile')).toHaveAttribute('accept', /\.csv,\.txt/);
});

async function inspectCodiPose(page, pose) {
  const avatar = page.locator('#codiAvatar');
  const image = avatar.locator('#codiPortrait');
  await expect(avatar).toHaveAttribute('data-look', 'codi');
  await expect(avatar).toHaveAttribute('data-pose', pose);
  await expect.poll(() => image.evaluate(el => new URL(el.src).pathname)).toBe(`/img/agents/codi/${pose}.webp`);
  await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate(el => el.complete && el.naturalWidth > 0)).toBe(true);
  // Check the decoded image itself. An opaque screenshot on a CSS background would otherwise pass.
  const pixels = await image.evaluate(async el => {
    await el.decode();
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(el, 0, 0, 64, 64);
    const rgba = ctx.getImageData(0, 0, 64, 64).data;
    let clear = 0, solid = 0;
    for (let i = 3; i < rgba.length; i += 4) {
      if (rgba[i] <= 8) clear++;
      if (rgba[i] >= 224) solid++;
    }
    return { clear, solid, signature: canvas.toDataURL() };
  });
  expect(pixels.clear).toBeGreaterThan(20);
  expect(pixels.solid).toBeGreaterThan(20);
  return pixels.signature;
}

test('코디가 입력·처리·완료·오류에 서로 다른 투명 이미지로 반응하며 동작 줄이기 설정을 지킨다', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  let turn = 0, finish;
  const firstReply = new Promise(resolve => { finish = resolve; });
  await openCodi(page, 390, async route => {
    if (++turn === 1) {
      await firstReply;
      return route.fulfill({ json: { message: '수업 일정을 확인했습니다.', drafts: [] } });
    }
    return route.fulfill({ status: 503, json: { message: '일시적 오류입니다.' } });
  });
  const poses = [await inspectCodiPose(page, 'idle')];
  const entry = page.locator('#uaCommand');
  await entry.fill(question);
  poses.push(await inspectCodiPose(page, 'input'));
  await page.getByRole('button', { name: '메시지 보내기' }).tap();
  poses.push(await inspectCodiPose(page, 'work'));
  expect(await page.locator('#codiAvatar').evaluate(el =>
    el.getAnimations({ subtree: true }).filter(animation => animation.playState === 'running').length
  )).toBe(0);
  finish();
  await expect(page.getByRole('log')).toContainText('수업 일정을 확인했습니다.');
  poses.push(await inspectCodiPose(page, 'result'));
  await entry.fill('다시 확인해 주세요.');
  await page.getByRole('button', { name: '메시지 보내기' }).tap();
  await expect(page.getByRole('log')).toContainText('일시적 오류입니다.');
  poses.push(await inspectCodiPose(page, 'error'));
  expect(new Set(poses).size).toBe(poses.length);
});

test('초안 작성 중에는 쓰기 자세, 승인 대기 카드가 도착하면 검토 자세를 보여 준다', async ({ page }) => {
  await openCodi(page, 390);
  let finish;
  const pendingDraft = new Promise(resolve => { finish = resolve; });
  await page.route('**/api/dashboard/assistant/v1/drafts', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    await pendingDraft;
    return route.fulfill({ json: {
      id: 'fixture-draft', operation: 'STUDENT_CREATE', status: 'PENDING', academyId: 7,
      expiresAt: '2027-01-01T00:00:00Z', preview: { name: '검수 학생' }
    } });
  });
  await page.locator('#codiNav').getByRole('button', { name: '업무 도구' }).tap();
  await page.locator('#uaEdit summary').click();
  await page.locator('#uaField_name').fill('검수 학생');
  await page.getByRole('button', { name: '초안 만들기', exact: true }).click();
  await page.locator('#siMenuBtn').click();
  await page.locator('#siMenu [data-view-btn="ai"]').click();
  const writing = await inspectCodiPose(page, 'write');
  finish();
  await expect(page.locator('[data-ua-draft="fixture-draft"]')).toContainText('승인 대기');
  const reading = await inspectCodiPose(page, 'read');
  expect(writing === reading).toBe(false);
});


test('일반 대화가 학원 업무 초안과 분리되고 조합 중 입력·출처·대화 만료를 처리한다', async ({ page }) => {
  const sent = [];
  const academyContext = { academyId: 7, role: 'ACADEMY_OWNER', academyOptions: [
    { id: 7, name: '첫 번째 시험 학원' }, { id: 8, name: '두 번째 시험 학원' }
  ] };
  await openCodi(page, 390, async route => {
    sent.push(route.request().postDataJSON());
    if (sent.length === 2) return route.fulfill({ status: 410, json: { message: '일반 대화가 만료되었습니다.' } });
    return route.fulfill({ json: {
      message: '공개 정보에 대한 답변입니다.',
      data: { conversationId: 'general-thread', sources: [{ title: '공식 자료', url: 'https://example.org/info' }] }
    } });
  }, { academyContext });

  const general = page.getByRole('button', { name: '일반 대화', exact: true });
  const work = page.getByRole('button', { name: '학원 업무', exact: true });
  const input = page.locator('#uaCommand');
  const log = page.getByRole('log', { name: '코디 일반 대화' });
  await input.fill('내부 업무 입력');
  await general.tap();
  await expect(input).toHaveValue('');
  await expect(log).toContainText('공개 정보만 입력');
  await expect(page.locator('#codiAttach')).toBeHidden();
  // A local work-flow keyword must reach the general route as plain text.
  await input.fill('학생 등록하고 싶어');
  await input.dispatchEvent('compositionstart');
  await input.dispatchEvent('keydown', { key: 'Enter', code: 'Enter', isComposing: true });
  await page.locator('#uaCommandForm').evaluate(form => form.requestSubmit());
  await input.dispatchEvent('compositionend');
  expect(sent).toEqual([]);
  await input.press('Enter');
  await expect(log).toContainText('공개 정보에 대한 답변');
  expect(sent[0]).toEqual({ mode: 'general', academyId: 7, message: '학생 등록하고 싶어', webSearch: true });
  await expect(log.getByRole('link', { name: '공식 자료' })).toHaveAttribute('href', 'https://example.org/info');
  await input.fill('이어서 설명해 주세요');
  await input.press('Enter');
  await expect(log).toContainText('다시 보내면 새 일반 대화를 시작합니다');
  await expect(input).toHaveValue('이어서 설명해 주세요');
  expect(sent[1].conversationId).toBe('general-thread');
  await input.press('Enter');
  await expect.poll(() => sent.length).toBe(3);
  expect(sent[2]).not.toHaveProperty('conversationId');
  await work.tap();
  await expect(input).toHaveValue('내부 업무 입력');
  await expect(log).toBeHidden();
  await expect(page.locator('#uaDrafts')).not.toContainText('공개 정보에 대한 답변');
  await expect(page.locator('#codiAttach')).toBeVisible();
  await page.route('**/api/dashboard/assistant/v1/overview?academyId=8', route =>
    route.fulfill({ json: { academyContext: { ...academyContext, academyId: 8 } } }));
  await page.locator('#siMenuBtn').tap();
  await page.locator('#uaAcademy').selectOption('8');
  await page.locator('#siMenuClose').tap();
  await page.waitForFunction(() => window.unifiedAssistant?.ready() && window.unifiedAssistant.academy() === 8);
  await general.tap();
  await expect(log).not.toContainText('공개 정보에 대한 답변');
  await input.fill('두 번째 학원에서 보낸 질문');
  await input.press('Enter');
  await expect.poll(() => sent.length).toBe(4);
  expect(sent[3].academyId).toBe(8);
  expect(sent[3]).not.toHaveProperty('conversationId');
  await expect(log).toContainText('공개 정보에 대한 답변');
  await input.fill('새 질문');
  await page.reload();
  await page.waitForFunction(() => window.unifiedAssistant?.ready());
  await expect(work).toHaveAttribute('aria-pressed', 'true');
  await general.tap();
  await expect(log).not.toContainText('공개 정보에 대한 답변');
  await expect(input).toHaveValue('');
});

test('학원관리의 코디 링크로 진입하고 메뉴에서 학원관리로 돌아간다', async ({ page }) => {
  await openCodi(page, 360);
  await page.goto(origin + '/dashboard');
  const homeLink = page.getByRole('link', { name: 'F2R 코디', exact: true });
  await expect(homeLink).toBeInViewport();
  await homeLink.tap();
  await expect(page).toHaveURL(origin + '/dashboard/assistant');
  await page.getByRole('button', { name: '메뉴 열기' }).tap();
  await page.getByRole('link', { name: '학원관리 대시보드' }).tap();
  await expect(page).toHaveURL(origin + '/dashboard');
});

test('기기 본문 크기를 따라가고 대화·메뉴·도구의 터치 영역은 44px 이상이다', async ({ page }) => {
  const session = await page.context().newCDPSession(page);
  await openCodi(page, 360, route => route.fulfill({
    json: { message: '공개 자료를 확인했습니다.', data: { sources: [{ title: '원문', url: 'https://example.org' }] } }
  }), { academyContext: { academyId: 7, role: 'ACADEMY_OWNER', academyOptions: [{ id: 7, name: '시연 학원' }] } });

  async function checkTargets() {
    const short = await page.locator('a[href], button, input:not([type=hidden]), select, textarea, summary').evaluateAll(nodes =>
      nodes.filter(node => node.checkVisibility({ visibilityProperty: true }) && !node.closest('details:not([open]) > :not(summary)'))
        .flatMap(node => {
          const box = node.getBoundingClientRect();
          return box.width < 44 || box.height < 44 ? [node.id || node.textContent.trim() || node.tagName] : [];
        }));
    expect(short).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
  }

  await page.locator('#uaCommand').fill('첫 줄\n둘째 줄');
  for (const size of [16, 24, 32]) {
    // Browser default font preference, changed while the same page stays open.
    await session.send('Page.setFontSizes', { fontSizes: { standard: size } });
    await expect(page.locator('#uaDrafts .ua-assistant p').first()).toHaveCSS('font-size', size + 'px');
    await expect(page.locator('#uaCommand')).toHaveCSS('font-size', size + 'px');
    await expect.poll(() => page.locator('#uaCommand').evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1);
    await checkTargets();
  }
  await page.getByRole('button', { name: '일반 대화', exact: true }).tap();
  await page.locator('#uaCommand').fill(question);
  await page.locator('#codiSend').tap();
  await expect(page.getByRole('link', { name: '원문', exact: true })).toBeVisible();
  await checkTargets();
  await page.locator('#uaCommand').focus();
  await page.evaluate(() => {
    Object.defineProperty(visualViewport, 'height', { configurable: true, get: () => 380 });
    visualViewport.dispatchEvent(new Event('resize'));
  });
  await expect(page.locator('#codiNav')).toBeHidden();
  const avatar = await page.locator('#codiAvatar').boundingBox();
  const reader = await page.getByRole('log', { name: '코디 일반 대화' }).boundingBox();
  const composer = await page.locator('#uaCommandForm').boundingBox();
  expect(avatar.y + avatar.height).toBeLessThanOrEqual(reader.y);
  expect(reader.height).toBeGreaterThanOrEqual(44);
  expect(reader.y + reader.height).toBeLessThanOrEqual(composer.y + 1);
  expect(composer.y + composer.height).toBeLessThanOrEqual(380);
  await page.evaluate(() => {
    delete visualViewport.height;
    visualViewport.dispatchEvent(new Event('resize'));
    document.activeElement.blur();
  });
  await expect(page.locator('#codiNav')).toBeVisible();
  await page.locator('#siMenuBtn').tap();
  await expect(page.locator('#siMenu')).toBeVisible();
  await checkTargets();
  await page.locator('#siMenu [data-view-btn=all]').tap();
  await page.locator('#uaEdit summary').tap();
  await checkTargets();
});

test('첨부 형식별 한도를 넘는 파일·빈 파일은 전송하지 않고 선택창에서 다시 고칠 수 있다', async ({ page }) => {
  await openCodi(page, 360);
  const posted = [];
  await page.route(url => url.pathname.endsWith('/v1/materials'), async route => {
    posted.push(route.request().postDataBuffer().length);
    return route.fulfill({ json: { message: 'CSV 분석 결과입니다.', drafts: [], issues: [], data: {} } });
  });
  await page.getByRole('button', { name: '업무 자료 첨부' }).tap();
  const picker = page.locator('#uaFile');
  const submit = page.locator('#uaUploadSubmit');
  const feedback = page.locator('#uaUploadFeedback');
  for (const [name, size, error] of [
    ['큰.csv', 256 * 1024 + 1, '256KB'], ['큰.TXT', 256 * 1024 + 1, '256KB'],
    ['큰.HTML', 5 * 1024 * 1024 + 1, '5MB'], ['큰.pdf', 5 * 1024 * 1024 + 1, '5MB'],
    ['빈.htm', 0, '빈 파일'], ['다른.zip', 1, '파일을 선택']
  ]) {
    await picker.setInputFiles({ name, mimeType: 'application/octet-stream', buffer: Buffer.alloc(size) });
    await submit.tap();
    await expect(feedback).toContainText(error);
    await expect(picker).toBeFocused();
  }
  expect(posted).toEqual([]);
  await picker.setInputFiles({ name: '경계.csv', mimeType: 'text/csv', buffer: Buffer.alloc(256 * 1024, 32) });
  await expect(submit).toHaveText('자료 분석하고 초안 만들기');
  await submit.tap();
  await expect(feedback).toContainText('결과를 확인');
  expect(posted).toHaveLength(1);
});

test('학원 업무에서 HTML 본문과 PDF의 OCR 안내를 읽고 일반 대화와 구분한다', async ({ page }) => {
  await openCodi(page, 390);
  let calls = 0;
  await page.route(url => url.pathname.endsWith('/v1/materials'), route => {
    const html = ++calls === 1;
    return route.fulfill({ json: { message: html ? '읽은 본문을 확인해 주세요.' : '이미지 PDF입니다. OCR이 필요합니다.',
      data: { document: html
        ? { format: 'HTML', status: 'READ', text: '원문에 적힌 <b>준비물</b>을 확인해 주세요.', truncated: true }
        : { format: 'PDF', status: 'NEEDS_OCR', text: '', truncated: false, pageCount: 4 } } } });
  });
  await page.getByRole('button', { name: '업무 자료 첨부' }).tap();
  await expect(page.locator('#uaFile')).toHaveAttribute('accept', /\.html,\.htm,\.pdf/);
  await page.locator('#uaFile').setInputFiles({ name: '자료.HTM', mimeType: 'application/octet-stream', buffer: Buffer.alloc(5 * 1024 * 1024, 32) });
  await page.getByRole('button', { name: '문서 읽기' }).tap();
  const article = page.getByRole('article', { name: 'HTML 문서 읽기 결과' });
  await expect(article).toBeFocused();
  await article.getByText('본문 읽기', { exact: true }).tap();
  await expect(article).toContainText('원문에 적힌 <b>준비물</b>');
  await expect(article.locator('.ua-material-text b')).toHaveCount(0);
  await expect(article).toContainText('1,200자');
  await page.getByRole('button', { name: '일반 대화', exact: true }).tap();
  await expect(article).toBeHidden();
  // A PDF submitted through the tools view returns to its work-scoped result.
  await page.locator('#codiNav').getByRole('button', { name: '업무 도구' }).tap();
  await page.locator('#uaFile').setInputFiles({ name: '스캔.PdF', mimeType: 'application/pdf', buffer: Buffer.alloc(340000) });
  await page.getByRole('button', { name: '문서 읽기' }).tap();
  await expect(page.getByRole('button', { name: '학원 업무', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const pdf = page.getByRole('article', { name: 'PDF 문서 읽기 결과' });
  await expect(pdf).toContainText('OCR이 필요');
  await expect(pdf).toContainText('4쪽');
  await expect(pdf.locator('summary')).toHaveCount(0);
  expect(calls).toBe(2);
});

test('카드사 자료는 원문 대신 문의 초안과 진행표를 제공하고 계약 확인 선택은 화면에만 둔다', async ({ page }) => {
  await openCodi(page, 360);
  const writes = [];
  page.on('request', request => { if (request.method() === 'POST') writes.push(new URL(request.url()).pathname); });
  const rows = ['BC카드', '시연카드'].map(issuer => ({
    issuer, status: '문의 전 · 접수 미확인', documents: '가맹점 변경 요청서',
    inquiry: issuer + ' 담당자께 요청 서류와 제출 경로를 문의합니다.'
  }));
  await page.route(url => url.pathname.endsWith('/v1/materials'), route => route.fulfill({ json: {
    message: '카드사별 문의를 준비했습니다.',
    data: { document: { format: 'PDF', status: 'READ', text: '화면에 공개하지 않을 시험 원문', pageCount: 4 },
      cardMerchantGuide: { note: 'BC 문의 번호는 공식 확인 전까지 미확인입니다.', rows } }
  } }));
  await page.getByRole('button', { name: '업무 자료 첨부' }).tap();
  await page.locator('#uaFile').setInputFiles({ name: '가명 안내.pdf', mimeType: 'application/pdf', buffer: Buffer.from('test') });
  await page.getByRole('button', { name: '문서 읽기' }).tap();
  const card = page.getByRole('article', { name: 'PDF 문서 읽기 결과' });
  await expect(card).toContainText('공식 확인 전까지 미확인');
  await expect(card).not.toContainText('화면에 공개하지 않을 시험 원문');
  const table = card.getByRole('table', { name: '카드사별 진행표' });
  await expect(table.getByText('계약 미확인', { exact: true })).toHaveCount(2);
  const only = card.getByRole('checkbox', { name: '확인한 카드사만 보기' });
  await only.check();
  await expect(card).toContainText('계약을 확인한 카드사가 없습니다.');
  await only.uncheck();
  await table.getByRole('checkbox', { name: 'BC카드 계약 확인' }).check();
  await only.check();
  await expect(table.getByRole('row')).toHaveCount(2);
  await expect(table.getByRole('checkbox', { name: 'BC카드 계약 확인' })).toBeChecked();
  await expect(card.getByText('시연카드 문의 초안', { exact: true })).toBeHidden();
  await card.getByText('BC카드 문의 초안', { exact: true }).tap();
  await expect(card.getByText(rows[0].inquiry, { exact: true })).toBeVisible();
  await expect(card.getByText('가맹점 변경 요청서', { exact: true }).first()).toBeVisible();
  await table.getByRole('checkbox', { name: 'BC카드 계약 확인' }).uncheck();
  await expect(only).toBeFocused();
  expect(writes).toEqual(['/api/dashboard/assistant/v1/materials']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});

const photoTargets = '/api/dashboard/assistant/v1/grading-photo-targets';
const photoRecord = {
  gradingId: 9, studentId: 2, studentName: '시연 학생', gradedOn: '2026-11-27',
  subjectArea: '영어', material: '중간 평가', status: 'PENDING', photoCount: 0
};
const photoFixture = format => path.join(__dirname, 'fixtures', 'answer-sheet.' + format);

async function openPhotos(page, { list, role = 'TUTOR', useDocumentPicker = false } = {}) {
  const writes = [];
  const legacyRequests = [];
  page.on('request', request => {
    const url = new URL(request.url());
    if (request.method() !== 'GET' && url.pathname.startsWith('/api/')) writes.push(request);
    if (url.pathname.startsWith('/api/admin/easy/')) legacyRequests.push(url.pathname);
  });
  await openCodi(page, 390, undefined, {
    academyContext: { academyId: 3, role: 'ACADEMY_OWNER', academyOptions: [{ id: 3, name: '시연 학원' }] }
  }, {
    '/api/auth/me': { id: 5, role },
    '/api/dashboard/businesses': [{ businessId: 7, name: '시연 사업체' }, { businessId: 8, name: '다른 사업체' }],
    '/api/dashboard/widgets': { widgets: [] }
  });
  await page.route(url => url.pathname === photoTargets, list || (route => {
    expect(new URL(route.request().url()).searchParams.get('businessId')).toBe('7');
    return route.fulfill({ json: { targets: [photoRecord], nextBefore: null } });
  }));
  await page.locator('#codiAttach').tap();
  await expect(page.locator('#uaFile')).toBeFocused();
  if (!useDocumentPicker) {
    await page.locator('#uaPhotoOpen').tap();
    await expect(page.locator('#uaPhotoFile')).toBeVisible();
  }
  return { writes, legacyRequests };
}

for (const [format, mimeType, kind] of [['jpg', 'image/jpeg', 'STUDENT'], ['png', 'image/png', 'ANSWER_KEY']]) {
  test(format + ' 채점 사진은 미리보기와 대상 확인 후 선택한 사진 종류로 한 번만 첨부한다', async ({ page }, testInfo) => {
    const useDocumentPicker = format === 'jpg';
    const { writes, legacyRequests } = await openPhotos(page, { useDocumentPicker });
    const picker = page.locator(useDocumentPicker ? '#uaFile' : '#uaPhotoFile');
    const submit = page.locator('#uaPhotoSubmit');
    const grading = page.locator('#uaPhotoGrading');
    const preview = page.locator('#uaPhotoPreview');
    const routePath = photoTargets + '/9/photos';
    const sample = await fs.readFile(photoFixture(format));
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    await page.route(url => url.pathname === routePath, async route => {
      await pending;
      await route.fulfill({ json: { photoId: 42, kind, kindLabel: kind === 'STUDENT' ? '학생 답안' : '정답지', contentType: mimeType, sizeBytes: sample.length } });
    });
    await picker.setInputFiles({ name: 'answer-sheet.' + format, mimeType, buffer: sample });
    await expect(page.locator('#uaUploadForm')).toBeHidden();
    await expect(grading).toHaveValue('');
    await expect(page.locator('#uaPhotoKind')).toHaveValue('STUDENT');
    await expect(preview).toBeVisible();
    await expect.poll(() => preview.evaluate(img => img.complete && img.naturalWidth)).toBe(660);
    await expect(submit).toBeDisabled();
    await expect(grading.locator('option[value="9"]')).toContainText('시연 학생');
    await expect(grading.locator('option[value="9"]')).toContainText('중간 평가');
    await grading.selectOption('9');
    await page.locator('#uaPhotoKind').selectOption(kind);
    await expect(submit).toBeEnabled();
    expect(writes).toHaveLength(0);
    if (format === 'jpg') {
      await page.locator('#uaPhotoForm').screenshot({ path: testInfo.outputPath('photo-preview.png') });
    }
    await submit.tap();
    await expect.poll(() => writes.length).toBe(1);
    await expect(submit).toBeDisabled();
    await page.locator('#uaPhotoForm').evaluate(form => form.requestSubmit());
    release();
    await expect(page.locator('#uaPhotoFeedback')).toContainText(/첨부.*완료|첨부했|저장했/);
    const [upload] = writes;
    const url = new URL(upload.url());
    expect(writes).toHaveLength(1);
    expect(legacyRequests).toEqual([]);
    expect(url.pathname).toBe(routePath);
    expect(Object.fromEntries(url.searchParams)).toEqual({ businessId: '7', studentId: '2', kind });
    expect(upload.headers()['content-type']).toMatch(/^multipart\/form-data; boundary=/);
    const multipart = upload.postDataBuffer();
    expect(multipart.includes(Buffer.from('name="file"; filename="answer-sheet.' + format + '"'))).toBe(true);
    expect(multipart.includes(Buffer.from('Content-Type: ' + mimeType))).toBe(true);
    expect(multipart.includes(sample)).toBe(true);
    await expect(preview).toBeHidden();
    await expect(submit).toBeDisabled();
  });
}

test('채점 사진을 교체하고 취소한 뒤 문서 첨부로 돌아갈 때 사진을 저장하지 않는다', async ({ page }) => {
  const { writes } = await openPhotos(page);
  const picker = page.locator('#uaPhotoFile');
  const preview = page.locator('#uaPhotoPreview');
  await picker.setInputFiles(photoFixture('jpg'));
  await expect.poll(() => preview.evaluate(img => img.complete && img.naturalWidth)).toBe(660);
  const original = await preview.getAttribute('src');
  await picker.setInputFiles(photoFixture('png'));
  await expect(preview).not.toHaveAttribute('src', original);
  await expect.poll(() => preview.evaluate(img => img.complete && img.naturalWidth)).toBe(660);
  await page.locator('#uaPhotoGrading').selectOption('9');
  await expect(page.locator('#uaPhotoSubmit')).toBeEnabled();
  await page.locator('#uaPhotoCancel').tap();
  await expect(picker).toHaveValue('');
  await expect(preview).toBeHidden();
  await expect(page.locator('#uaPhotoSubmit')).toBeDisabled();
  expect(writes).toHaveLength(0);
  await page.locator('#uaDocumentOpen').tap();
  await expect(page.locator('#uaPhotoForm')).toBeHidden();
  const documentPath = '/api/dashboard/assistant/v1/materials';
  await page.route(url => url.pathname === documentPath, route => route.fulfill({ json: {
    message: '문서를 읽었습니다.', data: { document: { format: 'HTML', status: 'READ', text: '시연 수업 계획', truncated: false } }
  } }));
  await page.locator('#uaFile').setInputFiles({ name: '수업.html', mimeType: 'text/html', buffer: Buffer.from('<p>시연 수업 계획</p>') });
  await page.getByRole('button', { name: '문서 읽기' }).tap();
  await expect(page.getByRole('article', { name: 'HTML 문서 읽기 결과' })).toBeVisible();
  expect(writes.map(request => new URL(request.url()).pathname)).toEqual([documentPath]);
});

test('채점 사진의 실패를 안내하고 사업체를 바꾸면 이전 사진과 기록을 비운다', async ({ page }) => {
  const { writes } = await openPhotos(page, { role: 'ADMIN', list: route => {
    const business = new URL(route.request().url()).searchParams.get('businessId');
    return route.fulfill({ json: { targets: business === '7' ? [photoRecord] : [], nextBefore: null } });
  } });
  await page.route(url => url.pathname === photoTargets + '/9/photos', route => route.fulfill({
    status: 403, json: { message: '이 기록에 첨부할 권한이 없습니다.' }
  }));
  await page.locator('#uaPhotoFile').setInputFiles(photoFixture('png'));
  await page.locator('#uaPhotoGrading').selectOption('9');
  await page.locator('#uaPhotoSubmit').tap();
  await expect(page.locator('#uaPhotoFeedback')).toContainText(/권한/);
  await page.locator('#bizSelect').selectOption('8', { force: true });
  await expect(page.locator('#uaPhotoFile')).toHaveValue('');
  await expect(page.locator('#uaPhotoPreview')).toBeHidden();
  await expect(page.locator('#uaPhotoSubmit')).toBeDisabled();
  await expect(page.locator('#uaPhotoGrading option[value="9"]')).toHaveCount(0);
  expect(writes.map(request => new URL(request.url()).pathname)).toEqual([photoTargets + '/9/photos']);
});

test('채점 사진의 형식·용량과 이전 기록을 확인한 뒤 올바른 파일로 다시 선택할 수 있다', async ({ page }) => {
  const cursors = [];
  const { writes } = await openPhotos(page, { list: route => {
    const before = new URL(route.request().url()).searchParams.get('before');
    cursors.push(before);
    const target = before ? photoRecord : { ...photoRecord, gradingId: 12, photoCount: 10 };
    return route.fulfill({ json: { targets: [target], nextBefore: before ? null : 12 } });
  } });
  const picker = page.locator('#uaPhotoFile');
  const feedback = page.locator('#uaPhotoFeedback');
  const submit = page.locator('#uaPhotoSubmit');
  for (const [name, buffer, hint] of [
    ['empty.jpg', Buffer.alloc(0), '10MB'],
    ['too-large.png', Buffer.alloc(10 * 1024 * 1024 + 1), '10MB'],
    ['wrong-type.jpg', Buffer.from('plain text'), 'JPEG'],
    ['unreadable.png', Buffer.from([137,80,78,71,13,10,26,10]), '읽을 수 없습니다']
  ]) {
    await picker.setInputFiles({ name, mimeType: name.endsWith('.png') ? 'image/png' : 'image/jpeg', buffer });
    await expect(feedback).toContainText(hint);
    await expect(page.locator('#uaPhotoPreview')).toBeHidden();
    await expect(submit).toBeDisabled();
  }
  await expect(page.locator('#uaPhotoGrading option[value="12"]')).toBeDisabled();
  await page.locator('#uaPhotoMore').tap();
  await page.locator('#uaPhotoGrading').selectOption('9');
  await picker.setInputFiles(photoFixture('jpg'));
  await expect(submit).toBeEnabled();
  expect(cursors).toEqual([null, '12']);
  expect(writes).toHaveLength(0);
});
