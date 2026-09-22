# Agent2 HOME → ACADEMY Handoff (2026-09-22 morning close)

- Writer: Agent2 OpenCode (Knowledge Platform)
- Base: `origin/main 1cd88f64` (PR #361 merged state + #362/#364/#367)
- Agent1 Claude: NOT STARTED / WAITING (RAG work not begun)
- Next Agent2 work: CANONICALIZATION (afternoon, academy PC)
- This handoff only. No code / ingest / DB / ACTIVE / merge / deploy.

## PR status (read-only, do not merge)

- PR #365 (`docs/newstart-chat-faq-case-curation-20260922`, OPEN, head `59283d61`):
  `NEWSTART_CHAT_FAQ_CASE_CURATION_2026-09-22.md` + `newstart_chat_faq_case_candidates_2026-09-22.json`
  = FAQ 26 (OFFICIAL_VERIFY 15 / CASE_SUPPORTED 9 / CASE_ONLY 1 / CONFLICT_REVIEW 1)
  + CASE 12 (CONFIRMED 3 / PARTIAL 7 / UNRESOLVED 2). Raw transcript NOT on GitHub.
- PR #368 (`docs/newstart-operator-knowledge-5568-4451-20260922`, OPEN):
  `NEWSTART_OPERATOR_FAQ_5568_4451_CURATION_2026-09-22.md`
  + `newstart_operator_faq_5568_4451_candidates_2026-09-22.json` (33 KB, counts 33+50=83)
  + `NEWSTART_CASE_FOLLOWUP_GAPS_2026-09-22.md`. No ingest/DB content, docs only.

## Resolved (previously MISSING → FOUND)

- 5568: 33/33 structured (초보가이드, 2026-06-29, OPERATOR_KNOWLEDGE)
- 4451: 50/50 structured (초보가이드, 2026-03-06, OPERATOR_KNOWLEDGE)
- 5920: source FOUND — Credit4U 채권양도 후 연체정보 편차 질문, 댓글 0 → UNRESOLVED_CASE (detailed guide still missing)
- 5987: 지급명령 + 이의신청 게시, 댓글 2, 결과 없음 → RESULT_PARTIAL 이하, follow-up target
- 5988: 감면 0% → 9/8 신문고 → 재연락 예고, 9/17 추가증빙 예정 → RESULT_PARTIAL, follow-up target
- 5752: "등기를 받고나서 2주 카운트" 회원발언 1건 (OPERATOR 아님). 15일 법정기간 승격 금지 유지.

## TRUE REMAINING GAPS

- A. FSS full-chain RESULT_CONFIRMED = 0 (method + template 3종 있으나 해결체인 없음)
- B. COURT full-chain = 0 (5987 추적중이 유일 후보)
- C. CREDIT4U detailed guide 없음 (연체/공공/판단정보/채권자변동 읽기법)
- D. 주담대/HF 연장 성공·실패·조건부·유지 실측 0
- E. FOLLOW-UP 9건: 5987/5988/6010/5904/5948/5957/5868/5968/5947

## GUIDE 0~6 status

- G0 PRE-DEFAULT: partial (EBK-5-1/8-x, 4451-Q14, 6002, 5823/5884, 6010, 4451-Q18)
- G1 FSS: strongest (EBK-6-1 method + EBK-6-3 templates + 5568-Q16/17 + 4451-Q29/31 + 5856 이견 병존). 결과보장 표현 금지.
- G2 CREDIT4U: shell/collecting (5920 질문만)
- G3 COURT: partial/shell (5752 + 5987, "15일" 표기 금지)
- G4 HOME/GARNISHMENT: partial (EBK-7-x/9-1, L유형 5988·6010 판정확정; A·B·C·F·G·H·J·K missing)
- G5 MORTGAGE/HF: shell/collecting (EBK-9-1 + 4451-Q37/38 서술만, 실측 0)
- G6 NAVIGATOR: partial (단계 마일스톤 연결, SLA는 경험치 표기)

## Conflicts C-01~C-04 (병존, 판정 없음)

- C-01 감면율 수치 (5568-Q9/Q32 vs 4451-Q41 vs EBK vs EV-NS-PROP-2026 vs CASE 90%)
- C-02 민원 실효성 (5568-Q16~20 + 4451-Q29~31 vs 5856 반론 vs 5988 신문고)
- C-03 재산축소 서술군 (4451-Q8/Q21/Q22/Q27/Q28/Q34/Q35 + Q23/Q50 vs 사해·사후검증 서술) — 권고 승격 금지
- C-04 수식 표현 (4451-Q20 원문 보존)
- Plus: 10일 vs 31일, 신규 014 비상장주식·코인, EBK-6-2 10일, EBK-2-1 배우자

## V-01~V-21 official verify queue (Agent4 전달용, 판정 금지)

V-01 자격·한도 / V-02 연체·등록 / V-03 30%룰 / V-04 실효경로 / V-05 제도기한 /
V-06 감면율 / V-07 재산공제 / V-08 차량 / V-09 폐업 / V-10 금융재산 /
V-11 추심민원 / V-12 가압류 / V-13 절차 / V-14 추가감면·재기 / V-15 기간 /
V-16 심사·조사 / V-17 제도연계 / V-18 신용회복 / V-19 상계 / V-20 주담대 / V-21 소득

## Standing principles

- SOURCE PRESERVATION / NO-JUDGMENT: PRESERVE SOURCE·CASE·CONFLICT, SHOW PROVENANCE.
  DELETE BECAUSE STRANGE / INVENT EXPERIENCE / GENERAL-RULE AUTO PROMOTION /
  HIDE CONTRADICTION / MODEL JUDGMENT = NO.
- CASE metadata: DIRECT_SELF_REPORT / THIRD_PARTY_CASE / OPERATOR_REPORTED_CASE /
  SOURCE_UNCLEAR + RESULT_CONFIRMED / RESULT_PARTIAL / UNRESOLVED_CASE (확인범위 표기).
- 5568/4451 = OPERATOR_KNOWLEDGE 유지, 회원글과 provenance 병합 금지, 83 Q&A 압축 금지.

## RAG ANSWER CONTRACT (draft, no implementation)

TOPIC / OFFICIAL(source+content+effective date, 없으면 "미확인") /
OPERATOR(source+content) / EBOOK(section+content) / CAFE·CASE(source+content+result status) /
DIFFERENCES(병렬 표시, 판정 없음) / FOLLOW-UP·UNKNOWN / SOURCE LINKS·PROVENANCE.
합성 금지, C-03군 권고승격 금지, 15일·90%·"반드시 해결" 확정표현 금지, OPERATOR→공식 승격 금지.
Retrieval: `status=ACTIVE` 고정 + category 15종 + sourceType + relations
(SUPPORTED_BY / RELATED_CASE / CONTRADICTS — Java enum 확장 예정, migration 불요).

## Next: CANONICALIZATION (Agent2, academy)

- Input ready: 83건 (rel SAME/PARTIAL/UNIQUE + verify V-01~V-21 + links 완비)
- Schema: V985 reusable, migration 불요. CHAT/FAQ_CANONICAL sourceType + SUPPORTED_BY/RELATED_CASE는
  Java enum 확장으로 (아직 시작 금지 — 별도 지시 후)
- Still forbidden: ingest, DB write, ACTIVE, PR merge (#365/#368), deploy, RAG code, Agent1 start

## Academy resume order

1. `git fetch origin --prune` → 최신 main 확인
2. 본 HANDOFF 읽기 (`docs/newstart/knowledge/NEWSTART_AGENT2_HANDOFF_2026-09-22_HOME_TO_ACADEMY.md`)
3. PR #365 / #368 상태 확인 (OPEN 유지인지)
4. Agent4 official-verify 결과 확인 (V-01~V-21)
5. Agent2 CANONICALIZATION 재개
