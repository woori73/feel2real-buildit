// 사용자 흐름 하나를 처음부터 끝까지 실제 브라우저로 확인한다(어제 확정한 Lovable형 대화창 흐름).
//   한 문장 입력 → 홈페이지 생성 → 대화 속 미리보기 카드 → 전체 화면 확인 → 대화로 돌아와 수정 → 결과 확인
//   → 학원 정보 수정 → 게시 → (수정·게시 한 번 더) → 이전 버전 불러오기 → HTML 다운로드
// 격리 DB·로컬 앱에서만 실행한다(운영 접근 없음). 계정: e2e-lov (TUTOR, 학원 없음).
const fs = require('fs');
const path = require('path');
const { launch, newCtx, BASE } = require('../visual-editor/lib');

const SHOTS = process.env.SHOT_DIR || path.resolve(__dirname, '../../lovable-shots');
fs.mkdirSync(SHOTS, { recursive: true });
const PROMPT = process.env.E2E_PROMPT || '영어학원 홈페이지를 세련된 스타일로 만들어줘.';
const FOLLOWUP = process.env.E2E_FOLLOWUP || '첫 화면을 밝은 색상으로 바꾸고 상담 신청 버튼을 추가해줘.';

(async () => {
  const b = await launch();
  const rows = [];
  const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const shot = async (page, name) => { await page.screenshot({ path: path.join(SHOTS, name + '.png') }); };
  const errors = [];

  const ctx = await newCtx(b, { viewport: { width: 1280, height: 860 }, acceptDownloads: true }, { loginId: 'e2e-lov', password: 'admin1234' });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  const msgCount = () => page.evaluate(() => document.querySelectorAll('#ahMsgs .ah-msg').length);
  const waitIdle = async (before, timeout = 150000) => {
    await page.waitForFunction((n) => document.getElementById('ahBusy').hidden && document.querySelectorAll('#ahMsgs .ah-msg').length >= n + 2, before, { timeout });
  };
  const lastBot = () => page.evaluate(() => { const l = document.querySelectorAll('#ahMsgs .ah-msg.bot, #ahMsgs .ah-msg.err'); const m = l[l.length - 1]; return m ? { cls: m.className, text: m.textContent } : null; });
  const fullText = () => page.evaluate(() => { const d = document.getElementById('ahFrame').contentDocument; return d && d.body ? d.body.innerText : ''; });
  const waitFullRendered = () => page.waitForFunction(() => { const d = document.getElementById('ahFrame').contentDocument; return d && d.body && d.body.innerText.length > 80; }, null, { timeout: 30000 });

  // ── 1. 시작 화면: 입력창 하나 + 샘플 ──
  console.log('\n[1] 시작 화면');
  await page.goto(BASE + '/builder');
  await page.waitForSelector('#ahPrompt');
  ok('시작 화면: 입력창이 있고 학원 이름 폼은 없다', (await page.$('#ahPrompt')) && !(await page.$('#ahName')));
  await page.waitForFunction(() => document.querySelectorAll('#ahSamples .ah-sample').length >= 4, null, { timeout: 15000 });
  ok('샘플 4개 이상', (await page.$$('#ahSamples .ah-sample')).length >= 4);
  await shot(page, '01-start-desktop');

  // ── 2. 한 문장 → 생성 → 대화 속 미리보기 카드 ──
  console.log('\n[2] 한 문장으로 생성');
  await page.fill('#ahPrompt', PROMPT);
  await page.click('#ahStartBtn');
  await page.waitForSelector('#ahWork:not([hidden])', { timeout: 150000 });
  ok('작업 대화 화면으로 이동', true);
  const texts = await page.$$eval('#ahMsgs .ah-msg', (els) => els.map((e) => e.textContent));
  ok('내 문장이 말풍선으로 남는다', texts.some((t) => t.includes('영어학원 홈페이지를 세련된 스타일')), texts);
  ok('AI 안내(초안을 만들었어요 + 임시 정보 안내)', texts.some((t) => /초안을 만들었어요/.test(t)) && texts.some((t) => /임시로 채운 정보/.test(t)), texts);
  ok('대화 속 미리보기 카드가 보인다', await page.isVisible('#ahCard'));
  await page.waitForFunction(() => { const d = document.getElementById('ahCardFrame').contentDocument; return d && d.body && d.body.innerText.length > 80; }, null, { timeout: 30000 });
  ok('카드 미리보기에 홈페이지가 그려졌다', true);
  await shot(page, '02-chat-card-desktop');

  // ── 3. 전체 화면 → 대화로 돌아가기 ──
  console.log('\n[3] 전체 화면 보기');
  await page.click('#ahCardFull');
  await page.waitForSelector('#ahFull:not([hidden])');
  await waitFullRendered();
  ok('전체 화면에서 홈페이지 전체가 보인다', (await fullText()).length > 80);
  const beforeShot = await page.screenshot();
  await shot(page, '03-fullscreen');
  await page.click('#ahFull .ah-seg [data-dev="desktop"]');
  await sleep(600);
  await shot(page, '03b-fullscreen-pc');
  await page.click('#ahFullBack');
  ok('대화로 돌아가기', await page.isHidden('#ahFull') && await page.isVisible('#ahWorkInput'));

  // ── 4. 대화로 수정 ──
  console.log('\n[4] 대화로 수정: ' + FOLLOWUP);
  const n0 = await msgCount();
  await page.fill('#ahWorkInput', FOLLOWUP);
  await page.click('#ahWorkSend');
  await waitIdle(n0);
  const bot = await lastBot();
  const changeCount = await page.$$eval('#ahMsgs .ah-msg.bot:last-of-type .ah-changes li', (l) => l.length).catch(() => 0);
  ok('수정 결과 안내가 말풍선으로 나온다(오류 아님)', bot && bot.cls.includes('bot') && !bot.cls.includes('err'), bot);
  console.log('    AI 답변: ' + (bot ? bot.text.replace(/\s+/g, ' ').slice(0, 300) : ''));
  ok('변경 내용 목록이 있다', changeCount > 0, changeCount);
  await page.click('#ahCardFull');
  await page.waitForSelector('#ahFull:not([hidden])');
  await waitFullRendered();
  await sleep(500);
  const afterShot = await page.screenshot();
  await shot(page, '04-after-edit-fullscreen');
  ok('수정 전후 화면이 달라졌다', Buffer.compare(beforeShot, afterShot) !== 0);
  const hasConsult = /상담/.test(await fullText());
  ok('전체 화면에 "상담" 문구가 보인다', hasConsult);
  await page.click('#ahFullBack');
  await shot(page, '05-chat-after-edit');

  // ── 5. 학원 정보(임시 값 → 실제 값) ──
  console.log('\n[5] 학원 정보 수정');
  await page.click('#ahProfileBtn');
  await page.waitForSelector('#ahProfile:not([hidden])');
  await sleep(500);
  await page.fill('#ahProfName', '새봄영어학원');
  await page.fill('#ahProfRegion', '서울 강남구');
  await page.click('#ahProfileSave');
  await page.waitForSelector('#ahProfile', { state: 'hidden' });
  await page.waitForFunction(() => { const d = document.getElementById('ahCardFrame').contentDocument; return d && d.body && d.body.innerText.includes('새봄영어학원'); }, null, { timeout: 30000 }).then(() => ok('카드 미리보기에 바뀐 학원 이름 반영', true), () => ok('카드 미리보기에 바뀐 학원 이름 반영', false));

  // ── 6. 게시 ──
  console.log('\n[6] 게시');
  await page.click('#ahPublish');
  await page.waitForSelector('#ahConfirm:not([hidden])');
  await shot(page, '06-publish-confirm');
  await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /게시했어요/.test(document.getElementById('ahMsgs').textContent), null, { timeout: 30000 });
  ok('게시 완료 안내', true);
  const me = await (await ctx.request.get(BASE + '/api/tutor/academy/me')).json();
  const pub = await fetch(BASE + '/api/academies/' + me.slug);
  ok('공개 홈페이지 API가 응답한다', pub.status === 200, pub.status);
  const pubJson = await pub.json();
  ok('공개 홈페이지에 바뀐 학원 이름이 보인다', pubJson && pubJson.name === '새봄영어학원', pubJson && pubJson.name);

  const lateProfile = await ctx.request.post(BASE + '/api/tutor/academy/assistant/profile', { data: { academyName: '게시후변경' } });
  ok('게시 뒤에는 학원 정보 변경이 거절된다(400)', lateProfile.status() === 400, lateProfile.status());

  // ── 7. 한 번 더 수정·게시 → 이전 버전 불러오기 ──
  console.log('\n[7] 이전 버전 불러오기');
  const n1 = await msgCount();
  await page.fill('#ahWorkInput', '주색을 진한 파란색으로 바꿔줘');
  await page.click('#ahWorkSend');
  await waitIdle(n1);
  await page.click('#ahPublish');
  await page.waitForSelector('#ahConfirm:not([hidden])');
  await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /수정한 내용을 게시했어요/.test(document.getElementById('ahMsgs').textContent), null, { timeout: 30000 });
  await page.click('#ahVersionsBtn');
  await page.waitForSelector('#ahVersions:not([hidden])');
  await page.waitForSelector('#ahVersionList button', { timeout: 15000 });
  await shot(page, '07-versions');
  const vcount = (await page.$$('#ahVersionList button')).length;
  ok('버전 목록에 이전 공개본이 있다', vcount >= 1, vcount);
  await page.click('#ahVersionList button');
  await page.waitForSelector('#ahVersions', { state: 'hidden', timeout: 30000 });
  await page.waitForFunction(() => /이전 버전을 작업본에 불러왔어요/.test(document.getElementById('ahMsgs').textContent), null, { timeout: 30000 });
  ok('버전을 작업본에 불러왔고 안내가 나온다', true);
  ok('불러온 뒤 되돌리기 횟수는 0', (await page.textContent('#ahUndoCount')) === '0');
  await shot(page, '08-after-restore');

  // ── 8. 다운로드 ──
  console.log('\n[8] HTML 다운로드');
  const [dl] = await Promise.all([page.waitForEvent('download', { timeout: 60000 }), page.click('#ahDownloadBtn')]);
  const file = path.join(SHOTS, dl.suggestedFilename());
  await dl.saveAs(file);
  const html = fs.readFileSync(file, 'utf8');
  ok('내려받은 파일 이름이 학원 이름.html (헤드리스 Chromium은 한글 이름을 download로 보고하므로 그것도 허용)', /^(새봄영어학원-\d{8}\.html|download)$/.test(dl.suggestedFilename()), dl.suggestedFilename());
  ok('내려받은 파일: doctype·학원 이름 포함, 스크립트 없음', html.startsWith('<!DOCTYPE html>') && html.includes('새봄영어학원') && !/<script/i.test(html), html.slice(0, 200));
  console.log('    파일 크기: ' + html.length + ' bytes → ' + file);

  // ── 9. 모바일 폭에서도 같은 화면 ──
  console.log('\n[9] 모바일 폭(390)');
  await page.setViewportSize({ width: 390, height: 844 });
  await sleep(500);
  await shot(page, '09-mobile-chat');
  await page.click('#ahCardFull');
  await waitFullRendered();
  await sleep(500);
  await shot(page, '10-mobile-fullscreen');
  ok('모바일에서 가로 스크롤 없음', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));

  ok('페이지 JS 오류 없음', errors.length === 0, errors);
  await b.close();
  const pass = rows.filter(Boolean).length;
  console.log('\n' + pass + '/' + rows.length + ' 통과');
  process.exit(pass === rows.length ? 0 : 1);
})().catch((e) => { console.error('E2E 중단:', e); process.exit(2); });
