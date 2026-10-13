// 입시상담 Agent 실제 LLM·RAG 22건 평가 러너 (격리 CI DB 전용, 운영 접근 없음).
// 입력: simulation_results.json(학생 질문 22건). 출력: results.json + summary.md. 키는 출력하지 않는다.
const fs = require('fs');
const BASE = process.env.BASE || 'http://localhost:8080';
const SIM = process.env.SIM || 'src/test/resources/admissions/simulation_results.json';
const PHASE = process.env.PHASE || 'ACTIVE_TEST'; // DRAFT_GATE | ACTIVE_TEST
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
    const flags = [];
    if (!a) flags.push('NO_ANSWER(' + (r.json && r.json.errorMessage) + ')');
    if (/찾지 못했습니다/.test(a)) flags.push('NO_EVIDENCE_REPLY');
    if (a && !sources.length) flags.push('ANSWER_WITHOUT_SOURCES');
    if (/(합격\s*(가능성|확률)|합격선)[^.\n]{0,12}\d+\s*(%|점|등급)/.test(a)) flags.push('ADMISSION_PROBABILITY_ASSERTED');
    if (/2028/.test(a) && !/(계획|예정|방향|미확정|확정되지|시행계획)/.test(a)) flags.push('CHECK_2028_WITHOUT_PLAN_CAVEAT');
    out.push({ id: q.id, category: q.category, question: q.question, httpStatus: r.status, ms: Date.now() - t0, model: r.json && r.json.model,
      answer: a, sources, academicYear: r.json && r.json.academicYear, yearConfirmed: r.json && r.json.yearConfirmed, flags,
      staticReference: { grade: q.grade, answer: q.answer } });
    console.log('RESULT ' + JSON.stringify({ id: q.id, question: q.question, answer: a, sources, academicYear: r.json && r.json.academicYear, flags }));
    console.log(q.id, r.status, (Date.now() - t0) + 'ms', 'sources=' + sources.length, flags.join(','));
    await sleep(7500); // 분당 10회 제한 준수
  }
  fs.writeFileSync('results-' + PHASE + '.json', JSON.stringify(out, null, 2));
  const md = ['# 입시상담 실제 LLM·RAG 평가 (' + PHASE + ')', '', '| ID | HTTP | ms | 출처 | 플래그 |', '|---|---|---|---|---|']
    .concat(out.map((o) => `| ${o.id} | ${o.httpStatus} | ${o.ms} | ${o.sources.length} | ${o.flags.join(', ') || '-'} |`)).join('\n');
  fs.writeFileSync('summary-' + PHASE + '.md', md); console.log(md);
})().catch((e) => { console.error('RUNNER ERROR', e.message); process.exit(1); });
