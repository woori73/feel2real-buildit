// 입시상담 Agent 실제 LLM·RAG 22건 평가 러너 (격리 CI DB 전용, 운영 접근 없음).
// 입력: simulation_results.json(학생 질문 22건). 출력: results.json + summary.md. 키는 출력하지 않는다.
const fs = require('fs');
const BASE = process.env.BASE || 'http://localhost:8080';
const SIM = process.env.SIM || 'src/test/resources/admissions/simulation_results.json';
const PHASE = process.env.PHASE || 'ACTIVE_TEST'; // DRAFT_GATE | ACTIVE_TEST
// ── 자동 판정 기준(답변 전문은 로그에 남기지 않는다. results-*.json 아티팩트에만 저장) ──
const KB_TEXT = process.env.KB ? fs.readFileSync(process.env.KB, 'utf8') : '';
const REFUSAL_EXPECTED = /판단 불가|답변 불가|거부|유보|확인되지 않|미확보|산출 불가|단정 불가|제공할 수 없|T3/;
const REFUSAL_MARK = /(불가|확인할 수 없|확인되지|없습니다|찾지 못|알 수 없|어렵|유보|제공하지)/;
function nums(t) { return (t.match(/\d[\d,.]*\d|\d/g) || []).map((x) => x.replace(/,/g, '')).filter((x) => x.length >= 2); }
function judge(q, a, sources, httpStatus) {
  const fail = []; const note = [];
  if (httpStatus !== 200 || !a) { fail.push('LLM_OR_HTTP_FAILURE'); return { verdict: 'FAIL', fail, note }; }
  const noEvidence = /찾지 못했습니다/.test(a);
  if (!sources.length && !noEvidence) fail.push('ANSWER_WITHOUT_EVIDENCE'); // 근거 없이 답변을 만들어냄
  const expectRefuse0 = REFUSAL_EXPECTED.test(q.answer || '');
  if (noEvidence && !expectRefuse0) fail.push('NO_EVIDENCE_UNANSWERED(안전하지만 질문에 답하지 못함)');
  if (noEvidence && expectRefuse0) note.push('SAFE_REFUSAL_AS_EXPECTED');
  if (/(합격\s*(가능성|확률)|합격선)[^.\n]{0,12}\d+\s*(%|점|등급)/.test(a)) fail.push('ADMISSION_PROBABILITY_ASSERTED');
  const allowed = q.question + JSON.stringify(sources) + KB_TEXT;
  const ungrounded = nums(a).filter((n) => !allowed.includes(n) && !/^20(2[6-9]|30)$/.test(n));
  if (ungrounded.length) fail.push('UNGROUNDED_NUMBER(' + [...new Set(ungrounded)].slice(0, 4).join('/') + ')');
  const has27 = /2027/.test(a); const has28 = /2028/.test(a);
  if (has28 && !/(계획|예정|방향|미확정|확정되지|시행계획)/.test(a)) fail.push('YEAR_2028_WITHOUT_PLAN_CAVEAT');
  if (has27 && has28 && !/(계획|예정|방향|미확정)/.test(a)) fail.push('YEAR_MIXED_2027_2028');
  if (!has27 && !has28) note.push('YEAR_NOT_STATED');
  const expectRefuse = REFUSAL_EXPECTED.test(q.answer || '');
  if (expectRefuse && !REFUSAL_MARK.test(a)) fail.push('REFUSAL_EXPECTED_BUT_ANSWERED');
  if (!noEvidence && a.length < 60) note.push('VERY_SHORT_ANSWER');
  return { verdict: fail.length ? 'FAIL' : 'PASS', fail, note };
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let cookie = '';

async function post(path, body, auth) {
  const res = await fetch(BASE + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(auth && cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(body) });
  const text = await res.text(); let json = null; try { json = JSON.parse(text); } catch (e) { /* not json */ }
  return { status: res.status, json, text, headers: res.headers };
}

(async () => {
  const login = await post('/api/auth/login', { loginId: 'iso-admin', password: 'admin1234' });
  cookie = (login.headers.getSetCookie ? login.headers.getSetCookie() : []).map((c) => c.split(';')[0]).join('; ');
  if (login.status !== 200 || !cookie) throw new Error('admin login failed ' + login.status);

  if (!process.env.SKIP_LOAD) { // 로더는 학과·전형·일정이 멱등이 아니므로 한 번만 적재한다.
    const seed = fs.readFileSync(process.env.SEED, 'utf8'); const kb = fs.readFileSync(process.env.KB, 'utf8');
    const s = await post('/api/admin/admissions/load/seed', { seedJson: seed }, true);
    const k = await post('/api/admin/admissions/load/knowledge-base', { knowledgeBaseJson: kb }, true);
    console.log('load seed', s.status, JSON.stringify(s.json), 'kb', k.status, JSON.stringify(k.json));
  }

  const all = JSON.parse(fs.readFileSync(SIM, 'utf8')).questions; const sim = all.slice(0, Number(process.env.LIMIT) || all.length);
  const out = [];
  for (const q of sim) {
    const t0 = Date.now();
    let r = await post('/api/admissions/chat', { message: q.question });
    if (r.status === 429) { await sleep(65000); r = await post('/api/admissions/chat', { message: q.question }); }
    const a = (r.json && r.json.answer) || '';
    const sources = ((r.json && r.json.sources) || []).map((x) => x.title || x.refId || JSON.stringify(x).slice(0, 80));
    const j = judge(q, a, sources, r.status);
    const flags = j.fail.concat(j.note.map((n) => 'note:' + n));
    if (!a) flags.push('NO_ANSWER(' + (r.json && r.json.errorMessage) + ')');
    if (/찾지 못했습니다/.test(a)) flags.push('NO_EVIDENCE_REPLY');
    if (a && !sources.length) flags.push('ANSWER_WITHOUT_SOURCES');
    if (/(합격\s*(가능성|확률)|합격선)[^.\n]{0,12}\d+\s*(%|점|등급)/.test(a)) flags.push('ADMISSION_PROBABILITY_ASSERTED');
    if (/2028/.test(a) && !/(계획|예정|방향|미확정|확정되지|시행계획)/.test(a)) flags.push('CHECK_2028_WITHOUT_PLAN_CAVEAT');
    out.push({ verdict: j.verdict, failReasons: j.fail, notes: j.note, id: q.id, category: q.category, question: q.question, httpStatus: r.status, ms: Date.now() - t0, model: r.json && r.json.model,
      answer: a, sources, academicYear: r.json && r.json.academicYear, yearConfirmed: r.json && r.json.yearConfirmed, flags,
      staticReference: { grade: q.grade, answer: q.answer } });
    console.log(q.id, j.verdict, r.status, (Date.now() - t0) + 'ms', 'sources=' + sources.length, j.fail.join(',') || '-', j.note.join(',') || '');
    await sleep(7500); // 분당 10회 제한 준수
  }
  fs.writeFileSync('results-' + PHASE + '.json', JSON.stringify(out, null, 2));
  const md = ['# 입시상담 실제 LLM·RAG 평가 (' + PHASE + ')', '', '| ID | 판정 | HTTP | ms | 출처수 | 실패 사유 |', '|---|---|---|---|---|---|']
    .concat(out.map((o) => `| ${o.id} | ${o.verdict} | ${o.httpStatus} | ${o.ms} | ${o.sources.length} | ${o.failReasons.join(', ') || '-'} |`)).join('\n');
  fs.writeFileSync('summary-' + PHASE + '.md', md); console.log('PHASE ' + PHASE + ' PASS=' + out.filter((o) => o.verdict === 'PASS').length + ' FAIL=' + out.filter((o) => o.verdict === 'FAIL').length + ' HTTP200=' + out.filter((o) => o.httpStatus === 200).length + '/' + out.length);
})().catch((e) => { console.error('RUNNER ERROR', e.message); process.exit(1); });
