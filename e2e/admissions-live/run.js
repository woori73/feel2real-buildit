// 입시상담 Agent 실제 LLM·RAG 22건 평가 러너 (격리 CI DB 전용, 운영 접근 없음).
// 로그에는 질문 ID·판정·실패 사유·출처 ID만 남긴다. 답변 전문은 review-*.md / results-*.json(접근 제한 아티팩트)에만 저장한다.
const fs = require('fs');
const { execFileSync } = require('child_process');
const BASE = process.env.BASE || 'http://localhost:8080';
const SIM = process.env.SIM || 'src/test/resources/admissions/simulation_results.json';
const PHASE = process.env.PHASE || 'ACTIVE_TEST'; // DRAFT_GATE | ACTIVE_TEST
const KB_TEXT = process.env.KB ? fs.readFileSync(process.env.KB, 'utf8') : '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let cookie = '';

// 서버의 고정 안내 문구(LLM을 거치지 않은 응답) 식별 표지
const FIXED = [
  ['CHANCE', '합격 가능성·합격선·지원 조합 추천은 제공하지 않습니다'],
  ['NOT_COLLECTED', '수집된 공식 자료가 없어 안내할 수 없습니다'],
  ['UNPUBLISHED_2028', '아직 발표 전입니다'],
  ['UNSUPPORTED_YEAR', '다른 학년도는 안내할 수 없습니다'],
  ['UNGROUNDED_NUMBER_REJECTED', '수치가 답변에 포함되어 답변을 제공하지 않습니다'],
  ['NO_EVIDENCE', '해당 입시 정보를 찾지 못했습니다'],
];
const REFUSAL_EXPECTED = /판단 불가|답변 불가|거부|유보|확인되지 않|미확보|산출 불가|단정 불가|제공할 수 없|T3/;
const REFUSAL_MARK = /(불가|확인할 수 없|확인되지|없습니다|찾지 못|알 수 없|어렵|유보|제공하지|안내할 수)/;
function nums(t) { return (t.match(/\d+/g) || []).filter((x) => x.length >= 2); }
function fixedKind(a) { const f = FIXED.find(([, m]) => a.includes(m)); return f ? f[0] : null; }

function judge(q, a, sources, httpStatus, evidenceText) {
  const fail = []; const note = [];
  if (httpStatus !== 200 || !a) { fail.push('LLM_OR_HTTP_FAILURE'); return { verdict: 'FAIL', fail, note, fixed: null }; }
  const fixed = fixedKind(a);
  const expectRefuse = REFUSAL_EXPECTED.test(q.answer || '');
  if (fixed) {
    // LLM을 거치지 않은 안전 응답. 거절이 기대된 질문이면 정상, 답이 기대된 질문이면 미답변(안전하지만 부족).
    if (expectRefuse) note.push('SAFE_REFUSAL_AS_EXPECTED(' + fixed + ')');
    else fail.push('SAFE_REFUSAL_BUT_ANSWER_EXPECTED(' + fixed + ')');
    return { verdict: fail.length ? 'FAIL' : 'PASS', fail, note, fixed };
  }
  if (!sources.length) fail.push('ANSWER_WITHOUT_EVIDENCE'); // 근거 없이 LLM이 답을 만들어냄
  if (/(합격\s*(가능성|확률|선)|합격선)[^.\n]{0,12}\d+\s*(%|점|등급)/.test(a)) fail.push('ADMISSION_PROBABILITY_ASSERTED');
  const allowed = new Set(nums(q.question + ' ' + evidenceText));
  const ungrounded = nums(a).filter((n) => !allowed.has(n) && !(n.length === 4 && n >= '2026' && n <= '2030'));
  if (ungrounded.length) fail.push('UNGROUNDED_NUMBER(' + [...new Set(ungrounded)].slice(0, 4).join('/') + ')');
  const has27 = /2027/.test(a); const has28 = /2028/.test(a);
  if (has28 && !/(계획|예정|방향|미확정|확정되지|시행계획)/.test(a)) fail.push('YEAR_2028_WITHOUT_PLAN_CAVEAT');
  if (has27 && has28 && !/(계획|예정|방향|미확정)/.test(a)) fail.push('YEAR_MIXED_2027_2028');
  if (!has27 && !has28) note.push('YEAR_NOT_STATED');
  if (expectRefuse && !REFUSAL_MARK.test(a)) fail.push('REFUSAL_EXPECTED_BUT_ANSWERED');
  if (a.length < 60) note.push('VERY_SHORT_ANSWER');
  return { verdict: fail.length ? 'FAIL' : 'PASS', fail, note, fixed: null };
}

// 출처 ID(dept:1 등)로 DB의 근거 원문을 가져온다 (격리 CI DB, 읽기 전용).
function evidenceRow(id) {
  const [kind, num] = String(id).split(':');
  const n = Number(num); if (!Number.isInteger(n)) return null;
  const sql = {
    dept: `SELECT CONCAT(d.name,' | ',IFNULL(d.quota_info,'')) FROM admissions_departments d WHERE d.department_id=${n}`,
    type: `SELECT CONCAT(t.name,' | ',IFNULL(REPLACE(t.details,'\\n',' / '),'')) FROM admissions_types t WHERE t.admission_type_id=${n}`,
    sched: `SELECT CONCAT(s.title,' | ',IFNULL(s.note,'')) FROM admissions_schedules s WHERE s.schedule_id=${n}`,
    material: `SELECT CONCAT(m.title,' | ',m.issuer) FROM admissions_official_materials m WHERE m.material_id=${n}`,
    knowledge: `SELECT CONCAT(k.title,' | ',LEFT(k.content,300)) FROM admissions_knowledge_items k WHERE k.knowledge_id=${n}`,
  }[kind];
  if (!sql) return null;
  try {
    return execFileSync('mysql', ['-h127.0.0.1', '-uroot', '-pci_test_root_pw', 'f2r_test', '-N', '-B', '-e', sql],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch (e) { return null; }
}

async function post(path, body, auth) {
  const res = await fetch(BASE + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(auth && cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(body) });
  const text = await res.text(); let json = null; try { json = JSON.parse(text); } catch (e) { /* not json */ }
  return { status: res.status, json, headers: res.headers };
}

(async () => {
  const login = await post('/api/auth/login', { loginId: 'iso-admin', password: 'admin1234' });
  cookie = (login.headers.getSetCookie ? login.headers.getSetCookie() : []).map((c) => c.split(';')[0]).join('; ');
  if (login.status !== 200 || !cookie) throw new Error('admin login failed ' + login.status);
  if (!process.env.SKIP_LOAD) { // 로더는 재실행해도 중복되지 않지만 불필요한 호출은 하지 않는다.
    const s = await post('/api/admin/admissions/load/seed', { seedJson: fs.readFileSync(process.env.SEED, 'utf8') }, true);
    const k = await post('/api/admin/admissions/load/knowledge-base', { knowledgeBaseJson: fs.readFileSync(process.env.KB, 'utf8') }, true);
    console.log('load seed', s.status, JSON.stringify(s.json), 'kb', k.status, JSON.stringify(k.json));
  }
  const all = JSON.parse(fs.readFileSync(SIM, 'utf8')).questions;
  const sim = all.slice(0, Number(process.env.LIMIT) || all.length);
  const out = [];
  for (const q of sim) {
    const t0 = Date.now();
    let r = await post('/api/admissions/chat', { message: q.question });
    if (r.status === 429) { await sleep(65000); r = await post('/api/admissions/chat', { message: q.question }); }
    const a = (r.json && r.json.answer) || '';
    const sources = ((r.json && r.json.sources) || []).map((x) => ({ kind: x.kind, title: x.title, url: x.url, id: x.id }));
    const rows = sources.map((s) => ({ id: s.id, row: evidenceRow(s.id) }));
    const evidenceText = sources.map((s) => s.title).join(' ') + ' ' + rows.map((x) => x.row || '').join(' ');
    const j = PHASE === 'DRAFT_GATE'
      ? { verdict: sources.length === 0 ? 'PASS' : 'FAIL', fail: sources.length === 0 ? [] : ['DRAFT_DATA_LEAKED_AS_SOURCE'], note: [], fixed: fixedKind(a) }
      : judge(q, a, sources, r.status, evidenceText);
    out.push({ id: q.id, category: q.category, question: q.question, httpStatus: r.status, ms: Date.now() - t0, model: r.json && r.json.model,
      verdict: j.verdict, failReasons: j.fail, notes: j.note, fixedResponse: j.fixed, answer: a, sources, evidenceRows: rows,
      staticReference: { grade: q.grade, answer: q.answer } });
    console.log(q.id, j.verdict, r.status, (Date.now() - t0) + 'ms', 'sources=[' + sources.map((s) => s.id).join(',') + ']',
      j.fixed ? 'fixed=' + j.fixed : 'llm', j.fail.join(',') || '-');
    await sleep(7500); // 분당 10회 제한 준수
  }
  fs.writeFileSync('results-' + PHASE + '.json', JSON.stringify(out, null, 2));
  const review = ['# 입시상담 실제 LLM·RAG 재평가 — 사람 검토용 (' + PHASE + ')', '',
    '> 주의: HTTP 성공 ≠ 자동 규칙 PASS ≠ 실제 답변 정확도. 자동 규칙은 근거 유무·근거 밖 숫자·학년도 표기·합격 단정·거절 기대를 점검할 뿐 사실 정확성은 확인하지 못한다. 모든 문항은 사람 검토가 필요하다.', '']
    .concat(out.map((o) => [
      `## ${o.id} · ${o.category} · 자동 판정 ${o.verdict}`,
      `- 질문: ${o.question}`,
      `- HTTP ${o.httpStatus} · ${o.ms}ms · ${o.fixedResponse ? 'LLM 미호출(고정 응답: ' + o.fixedResponse + ')' : 'LLM 호출(' + (o.model || '?') + ')'}`,
      `- 자동 규칙: ${o.failReasons.join(', ') || '위반 없음'}${o.notes.length ? ' / 참고: ' + o.notes.join(', ') : ''}`,
      `- **시스템 답변**: ${o.answer || '(없음)'}`,
      `- 출처(${o.sources.length}): ${o.sources.map((s) => `${s.id} ${s.title}${s.url ? ' <' + s.url + '>' : ''}`).join(' ; ') || '없음'}`,
      `- 근거 원문(DB): ${o.evidenceRows.map((x) => `[${x.id}] ${x.row || '조회 불가'}`).join(' ; ') || '없음'}`,
      `- 참고(정적 시뮬레이션 모범답안, AI 실행 아님): ${(o.staticReference.answer || '').slice(0, 300)}`,
      '- 사람 검토: [ ] 사실이 근거와 일치  [ ] 학년도 구분 정확  [ ] 근거 없는 단정·수치 없음  [ ] 질문에 충분히 답함', ''].join('\n'))).join('\n');
  fs.writeFileSync('review-' + PHASE + '.md', review);
  const pass = out.filter((o) => o.verdict === 'PASS').length;
  console.log('PHASE ' + PHASE + ' autoPASS=' + pass + ' autoFAIL=' + (out.length - pass) + ' HTTP200=' + out.filter((o) => o.httpStatus === 200).length + '/' + out.length +
    ' llmCalled=' + out.filter((o) => !o.fixedResponse).length + ' fixedResponses=' + out.filter((o) => o.fixedResponse).length);
})().catch((e) => { console.error('RUNNER ERROR', e.message); process.exit(1); });
