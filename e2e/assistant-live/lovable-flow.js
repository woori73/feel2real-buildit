// BuildIt v5 사용자 흐름 하나를 처음부터 끝까지 실제 브라우저로 확인한다.
//   랜딩 첫 화면에서 제작 요청 입력 → 로그인(요청 복원) → BuildIt 가이드 대화 시작 → 홈페이지 생성 → 대화 속 미리보기 카드
//   → 전체 화면 확인 → 대화로 수정 → 학원 정보 → 정식 배포 등록(주소 확인 → 공개 완료 카드) → 이전 버전 → HTML 다운로드
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

  const ctx = await newCtx(b, { viewport: { width: 1280, height: 860 }, acceptDownloads: true }, null);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  const msgCount = () => page.evaluate(() => document.querySelectorAll('#ahMsgs .ah-msg').length);
  const waitIdle = async (before, timeout = 150000) => {
    await page.waitForFunction((n) => document.getElementById('ahBusy').hidden && document.querySelectorAll('#ahMsgs .ah-msg').length >= n + 2, before, { timeout });
  };
  const lastBot = () => page.evaluate(() => { const l = document.querySelectorAll('#ahMsgs .ah-msg.bot, #ahMsgs .ah-msg.err'); const m = l[l.length - 1]; return m ? { cls: m.className, text: m.textContent } : null; });
  const fullText = () => page.evaluate(() => { const d = document.getElementById('ahFrame').contentDocument; return d && d.body ? d.body.innerText : ''; });
  const openMenu = async () => { if (await page.isHidden('#ahMenu')) await page.click('#ahMoreBtn'); await page.waitForSelector('#ahMenu:not([hidden])'); };
  const waitFullRendered = () => page.waitForFunction(() => { const d = document.getElementById('ahFrame').contentDocument; return d && d.body && d.body.innerText.length > 80; }, null, { timeout: 30000 });

  // ── 0. 랜딩 첫 화면 → 제작 요청 입력 → 로그인 → 요청 복원 ──
  console.log('\n[0] 랜딩 첫 화면 + 로그인 + 요청 복원');
  await page.goto(BASE + '/');
  await page.waitForSelector('[data-buildit-prompt]');
  ok('첫 화면 제목·부제·입력창', (await page.textContent('#hero h1')).includes('어떤 홈페이지를 만들까요?') && (await page.textContent('.hero-ask-sub')).includes('BuildIt 가이드'));
  ok('첫 화면에 4단계·기능 카드가 없다(제목·부제·입력창만)', await page.evaluate(() => { const h = document.getElementById('hero'); return !h.querySelector('.flow-h, .steps, .feature-card, .hero-video-card') && h.querySelectorAll('input,button,a').length <= 3; }));
  ok('소개 영상은 첫 화면 아래(#intro)에 있다', await page.evaluate(() => { const v = document.querySelector('.hero-video-card'); return !!v && !document.getElementById('hero').contains(v) && v.getBoundingClientRect().top > window.innerHeight * 0.9; }));
  ok('첫 화면에 배경 곡선·광원 장식이 없다', await page.evaluate(() => !document.querySelector('#hero .hero-lines, #hero .brand-glow')));
  await shot(page, '00-landing-pc');
  const mp = await b.newPage({ viewport: { width: 390, height: 844 } });
  await mp.goto(BASE + '/'); await mp.waitForSelector('[data-buildit-prompt]');
  ok('모바일 첫 화면: 가로 스크롤 없음', await mp.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
  await mp.screenshot({ path: path.join(SHOTS, '00-landing-mobile.png') });
  await mp.screenshot({ path: path.join(SHOTS, '00-landing-mobile-samples.png') });
  await mp.close();
  await page.fill('[data-buildit-prompt]', PROMPT);
  await page.click('.ask-go');
  await page.waitForSelector('[data-login-modal]:not([hidden])');
  ok('로그인 전에는 로그인 창이 열린다', true);
  const signupHref = await page.evaluate(() => { const a = document.querySelector('.login-modal-signup'); a.addEventListener('click', (e) => e.preventDefault()); a.click(); return a.getAttribute('href'); });
  ok('회원가입을 누르면 /builder 복귀 주소로 간다', signupHref === '/register?returnUrl=%2Fbuilder', signupHref);
  await shot(page, '00b-landing-login');
  await page.fill('[data-login-modal-form] [name="loginId"]', process.env.E2E_LOGIN_ID || 'e2e-lov');
  await page.fill('[data-login-modal-form] [name="password"]', process.env.E2E_LOGIN_PW || 'admin1234');
  await Promise.all([page.waitForURL('**/builder', { timeout: 30000 }), page.click('.login-modal-submit')]);

  // ── 1. 시작 화면: 입력창 하나 (랜딩에서 입력한 요청이 복원되어 있다) ──
  console.log('\n[1] 시작 화면');
  await page.waitForSelector('#ahPrompt');
  ok('로그인 뒤 /builder 시작 화면에 첫 화면에서 입력한 요청이 복원된다', (await page.inputValue('#ahPrompt')) === PROMPT, await page.inputValue('#ahPrompt'));
  ok('시작 화면 제목·BuildIt 가이드 안내', (await page.textContent('.ah-start h1')).includes('어떤 홈페이지를 만들까요?') && (await page.textContent('.ah-sub')).includes('BuildIt 가이드'));
  ok('시작 화면: 입력창이 있고 학원 이름 폼은 없다', (await page.$('#ahPrompt')) && !(await page.$('#ahName')));
  await shot(page, '01-start-desktop');

  // ── 2. 한 문장 → 생성 → 대화 속 미리보기 카드 ──
  console.log('\n[2] 한 문장으로 생성');
  await page.click('#ahStartBtn');
  await page.waitForSelector('#ahWork:not([hidden])', { timeout: 150000 });
  ok('작업 대화 화면으로 이동', true);
  const texts = await page.$$eval('#ahMsgs .ah-msg', (els) => els.map((e) => e.textContent));
  ok('내 문장이 말풍선으로 남는다', texts.some((t) => t.includes('영어학원 홈페이지를 세련된 스타일')), texts);
  ok('AI 안내(초안을 만들었어요 + 임시 정보 안내)', texts.some((t) => /초안을 만들었어요/.test(t)) && texts.some((t) => /임시로 채운 정보/.test(t)), texts);
  ok('대화 속 미리보기 카드가 보인다', await page.isVisible('#ahCard'));
  await page.waitForFunction(() => { const d = document.getElementById('ahCardFrame').contentDocument; return d && d.body && d.body.innerText.length > 80; }, null, { timeout: 30000 });
  ok('카드 미리보기에 홈페이지가 그려졌다', true);
  await sleep(1500);
  const faded = await page.evaluate(() => { const d = document.getElementById('ahCardFrame').contentDocument; const items = [...d.querySelectorAll('[data-reveal-item]')]; return { n: items.length, faded: items.filter((e) => parseFloat(getComputedStyle(e).opacity) < 0.99).length, freeze: !!d.getElementById('ahCardFreeze') }; });
  ok('카드 미리보기: 등장 효과 요소가 모두 선명하다(흐린 요소 0)', faded.faded === 0 && faded.freeze, faded);
  const cardBox = await page.evaluate(() => { const t = document.getElementById('ahThumb').getBoundingClientRect(); return { w: t.width, h: t.height }; });
  ok('미리보기 카드가 크다(폭 600px 이상, 높이 380px 이상)', cardBox.w >= 600 && cardBox.h >= 380, cardBox);
  ok('전체 화면으로 보기가 강조된 주요 버튼(검정)이다', await page.evaluate(() => getComputedStyle(document.getElementById('ahCardFull')).backgroundColor === 'rgb(10, 10, 10)'));
  ok('상단: 이름·정식 배포 등록·더보기만(보조 버튼은 메뉴 안)', await page.evaluate(() => { const v = (id) => document.getElementById(id).offsetParent !== null; return v('ahPublish') && v('ahMoreBtn') && !v('ahProfileBtn') && !v('ahVersionsBtn') && !v('ahDownloadBtn'); }));
  await page.click('#ahMoreBtn');
  ok('더보기를 누르면 메뉴가 열리고 5개 항목이 보인다', (await page.isVisible('#ahMenu')) && (await page.$$eval('#ahMenu [role=menuitem]', (l) => l.filter((e) => e.offsetParent !== null).length)) === 5);
  await shot(page, '02b-more-menu');
  await page.keyboard.press('Escape');
  ok('Esc로 메뉴가 닫힌다', await page.isHidden('#ahMenu'));
  await page.click('#ahMoreBtn'); await page.click('#ahThread', { position: { x: 5, y: 5 } });
  ok('바깥을 누르면 메뉴가 닫힌다', await page.isHidden('#ahMenu'));
  await page.click('#ahMoreBtn'); await page.click('#ahMoreBtn');
  ok('더보기를 다시 누르면 닫힌다', await page.isHidden('#ahMenu'));
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
  await openMenu();
  await page.click('#ahProfileBtn');
  await page.waitForSelector('#ahProfile:not([hidden])');
  await sleep(500);
  await page.fill('#ahProfName', '새봄영어학원');
  await page.fill('#ahProfRegion', '서울 강남구');
  await page.click('#ahProfileSave');
  await page.waitForSelector('#ahProfile', { state: 'hidden' });
  await page.waitForFunction(() => { const d = document.getElementById('ahCardFrame').contentDocument; return d && d.body && d.body.innerText.includes('새봄영어학원'); }, null, { timeout: 30000 }).then(() => ok('카드 미리보기에 바뀐 학원 이름 반영', true), () => ok('카드 미리보기에 바뀐 학원 이름 반영', false));

  // ── 6. 게시 ──
  console.log('\n[6] 정식 배포 등록');
  ok('버튼 문구가 "홈페이지 정식 배포 등록"', (await page.textContent('#ahPublish')).trim() === '홈페이지 정식 배포 등록');
  ok('상단 Agent 이름이 BuildIt 가이드', (await page.textContent('.ah-agent')).trim() === 'BuildIt 가이드');
  ok('대화 구조: 사용자=회색 사각, AI=배경 없음', await page.evaluate(() => { const u = getComputedStyle(document.querySelector('#ahMsgs .ah-msg.user')); const b = getComputedStyle(document.querySelector('#ahMsgs .ah-msg.bot')); return u.backgroundColor === 'rgb(247, 247, 247)' && parseFloat(u.borderTopLeftRadius) <= 8 && b.backgroundColor === 'rgba(0, 0, 0, 0)'; }));
  await page.click('#ahPublish');
  await page.waitForSelector('#ahConfirm:not([hidden])');
  await page.waitForSelector('#ahConfirmAddr:not([hidden])', { timeout: 15000 });
  const addrText = await page.textContent('#ahConfirmAddrText');
  ok('확인 창에 공개될 주소가 보인다', addrText && addrText.length > 3, addrText);
  ok('확인 창 버튼이 "홈페이지 정식 배포 등록"', (await page.textContent('#ahConfirmOk')).trim() === '홈페이지 정식 배포 등록');
  await shot(page, '06-publish-confirm');
  await page.click('#ahConfirmOk');
  await page.waitForSelector('#ahMsgs .ah-pubcard', { timeout: 30000 });
  ok('공개 완료 카드: 홈페이지 바로가기·주소 복사·계속 수정하기', await page.evaluate(() => { const t = document.querySelector('#ahMsgs .ah-pubcard').textContent; return ['홈페이지 바로가기', '주소 복사', '계속 수정하기'].every((x) => t.includes(x)); }));
  await shot(page, '06b-published-card');
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
  // 대화로 "공개하고 싶어"라고 말하면 AI로 보내지 않고 정식 배포 등록 확인 창이 열린다(등록은 직접 누른다)
  await page.fill('#ahWorkInput', '홈페이지를 공개하고 싶어.');
  await page.click('#ahWorkSend');
  await page.waitForSelector('#ahConfirm:not([hidden])');
  ok('공개 요청 말은 확인 창으로 안내된다(공개는 아직 안 됨)', true);
  await page.click('#ahConfirmOk');
  await page.waitForFunction(() => /수정한 내용을 공개 홈페이지에 반영했어요/.test(document.getElementById('ahMsgs').textContent), null, { timeout: 30000 });
  await openMenu();
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
  ok('복원 안내가 "정식 배포 등록" 용어를 쓴다("게시" 없음)', await page.evaluate(() => { const t = [...document.querySelectorAll('#ahMsgs .ah-msg.bot')].map((e) => e.textContent).filter((x) => x.includes('이전 버전을 작업본에')).pop() || ''; return t.includes('정식 배포 등록') && !t.includes('게시'); }));
  await openMenu();
  ok('불러온 뒤 되돌리기 횟수는 0', (await page.textContent('#ahUndoCount')) === '0');
  await page.keyboard.press('Escape');
  await shot(page, '08-after-restore');

  // ── 8. 다운로드 ──
  console.log('\n[8] HTML 다운로드');
  await openMenu();
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
  await page.click('#ahFullBack');
  const mob = await page.evaluate(() => { const r = (id) => document.getElementById(id).getBoundingClientRect(); const c = r('ahThumb'); const pub = r('ahPublish'); const more = r('ahMoreBtn'); const comp = r('ahWorkComposer'); return { cardW: c.width, pubRight: pub.right, moreLeft: more.left, vw: innerWidth, compBottom: comp.bottom, vh: innerHeight, overlap: pub.right > more.left }; });
  ok('모바일: 버튼이 겹치거나 잘리지 않는다', !mob.overlap && mob.moreLeft + 40 <= mob.vw + 1, mob);
  ok('모바일: 입력창이 화면 안에 있다', mob.compBottom <= mob.vh + 1, mob);
  ok('모바일: 미리보기 카드가 화면 폭을 채운다(300px 이상)', mob.cardW >= 300, mob);
  await page.click('#ahMoreBtn'); await page.waitForSelector('#ahMenu:not([hidden])');
  ok('모바일: 더보기 메뉴가 화면 안에 열린다', await page.evaluate(() => { const m = document.getElementById('ahMenu').getBoundingClientRect(); return m.left >= 0 && m.right <= innerWidth + 1 && m.bottom <= innerHeight; }));
  await shot(page, '09b-mobile-menu');
  await page.keyboard.press('Escape');

  ok('페이지 JS 오류 없음', errors.length === 0, errors);
  await b.close();
  const pass = rows.filter(Boolean).length;
  console.log('\n' + pass + '/' + rows.length + ' 통과');
  process.exit(pass === rows.length ? 0 : 1);
})().catch((e) => { console.error('E2E 중단:', e); process.exit(2); });
