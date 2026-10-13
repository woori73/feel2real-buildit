// 기능 스위치 OFF(BUILDIT_ASSISTANT_ENABLED=false)로 다시 띄운 격리 앱에서 확인한다. 운영 접근 없음.
// 사실 확인용: 화면(/builder)뿐 아니라 API도 막히는지 함께 기록한다.
const { launch, newCtx, BASE } = require('../visual-editor/lib');

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
  const media = await ctx.request.fetch(BASE + '/api/tutor/academy/assistant/media/principal');
  ok('스위치 OFF: 작업본 사진 API도 막힘(403/404)', [403, 404].includes(media.status()), { status: media.status() });
  console.log(`\n검증 항목 ${rows.filter(Boolean).length}/${rows.length} 통과`);
  await b.close();
  process.exit(rows.every(Boolean) ? 0 : 1);
})().catch(e => { console.error('E2E 실패:', e); process.exit(1); });
