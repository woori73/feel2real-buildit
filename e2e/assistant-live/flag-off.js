// 기능 스위치 OFF(BUILDIT_ASSISTANT_ENABLED=false)로 다시 띄운 격리 앱에서 확인한다. 운영 접근 없음.
// 사실 확인용: 화면(/builder)뿐 아니라 API도 막히는지 함께 기록한다.
const { launch, newCtx, BASE, PNG } = require('../visual-editor/lib');

(async () => {
  const b = await launch();
  const rows = [];
  const ok = (n, v, d) => { rows.push(v); console.log((v ? '  ✔ ' : '  ✘ ') + n + (v ? '' : '  → ' + JSON.stringify(d))); };
  const ctx = await newCtx(b, {}, { loginId: 'e2e-live', password: 'admin1234' });
  const html = await (await ctx.request.fetch(BASE + '/builder')).text();
  ok('스위치 OFF: /builder는 기존 빌더(대화형 화면 없음)', !/id="ahWork"/.test(html) && !/id="ahStartForm"/.test(html));
  const st = await ctx.request.fetch(BASE + '/api/tutor/academy/assistant/state');
  ok('스위치 OFF: 대화 상태 API도 막힘(403/404)', [403, 404].includes(st.status()), { status: st.status() });
  const turn = await ctx.request.fetch(BASE + '/api/tutor/academy/assistant/turn', { method: 'POST', data: { message: '주색을 진한 파란색으로 바꿔줘', revision: 0 } });
  ok('스위치 OFF: 대화 턴 API도 막힘(403/404)', [403, 404].includes(turn.status()), { status: turn.status() });
  const undo = await ctx.request.fetch(BASE + '/api/tutor/academy/assistant/undo', { method: 'POST', data: { revision: 0 } });
  ok('스위치 OFF: 되돌리기 API도 막힘(403/404)', [403, 404].includes(undo.status()), { status: undo.status() });
  const publish = await ctx.request.fetch(BASE + '/api/tutor/academy/assistant/publish', { method: 'POST', data: { revision: 0 } });
  ok('스위치 OFF: 게시 API도 막힘(403/404)', [403, 404].includes(publish.status()), { status: publish.status() });
  const media = await ctx.request.post(BASE + '/api/tutor/academy/assistant/media', {
    multipart: { slot: 'principal', revision: '0', image: { name: 'p.png', mimeType: 'image/png', buffer: PNG } } });
  ok('스위치 OFF: 사진 올리기 API도 막힘(403/404)', [403, 404].includes(media.status()), { status: media.status() });
  const classic = await ctx.request.fetch(BASE + '/api/tutor/academy/working-copy');
  ok('스위치 OFF: 기존 빌더의 작업본 API는 영향 없음(200)', classic.status() === 200, { status: classic.status() });
  console.log(`\n검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch(e => { console.error('E2E 실패:', e); process.exit(1); });
