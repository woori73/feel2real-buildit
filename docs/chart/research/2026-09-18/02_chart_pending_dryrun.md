# Chart Pending Dry-Run — 2026-09-18 (UTC 2026-09-18)

Read-only dry-run. Source: public detail JSON `GET https://feel2real.co.kr/api/chart/educators/{id}` (format=text) for 9 RESEARCH_PENDING candidates.
No repo changes, no DB writes, no production transitions. Gates applied per stated server rules only, using publicly visible data.

## Gate definitions applied

MINIMUM GATE (assumed PASS — already stored; has evidence URL): noted as PASS/assumed for all 9. All 9 have ≥1 evidence with non-blank sourceUrl.

EVALUATION GATE (public-data checkable):
1. philosophy describable: educationPhilosophy non-blank AND whitespace-stripped len ≥ 30 AND not generic-ad-only (generic list removed, remainder < 15 chars = FAIL). Generic set: {학생중심,학생을중심,성적향상,성적up,최선을다합니다,최선을다하겠습니다,책임감있게,1대1맞춤,1:1맞춤,개인별맞춤,꼼꼼하게지도,즐거운수업,행복한수업} + particles. Result: no generic hits in any non-null philosophy (verified); all non-null pass length + generic sub-checks.
2. curriculum describable: curriculum non-blank AND stripped len ≥ 30 AND not subject-list-only (tokens split on whitespace/,·|~-; FAIL only if every token ∈ {초등영어,중등영어,고등영어,초등,중등,고등,내신,수능,영어,회화,문법,독해,어휘,초중고,정규반,특강반,방학특강}). Result: all 9 curriculums are full sentences → none is list-only → length decides (all ≥ 30 → all PASS).
3. evidence has current-year (2026) activityDate (any evidence with activityDate starting 2026).
4. evidence has OFFLINE_ACTIVITY kind (any evidence, regardless of date).
5. evidence has educationally-informative kind in {PHILOSOPHY,CURRICULUM,TEACHING_METHOD,MATERIAL,STUDENT_GUIDANCE,SUBJECT_EXPERTISE,EDUCATIONAL_CONTENT,OFFLINE_ACTIVITY}. Approximation (noted): public JSON does not expose educationallyInformative flag; any of these kinds treated as informative=true.
6. offlineActivityConfirmed / currentYearActivityConfirmed investigator flags: NOT public → UNKNOWN for all (stated explicitly).
7. ranking pool extra: needs a score snapshot; none exist for pending → FAIL "no snapshot" for all (stated; does NOT block EVALUATABLE verdict, only PUBLISHABLE/TOP100 entry — otherwise no candidate could ever be WOULD-BE).

Verdict rule: WOULD-BE-EVALUATABLE iff gates 1–5 ALL PASS (6 = UNKNOWN noted separately, 7 = FAIL/no-snapshot noted separately as publish-blocker). Else STAY RESEARCH_PENDING + fail reasons.

Scoring formula (from `recalc_chart.py score_detail`, preview only): fields = non-blank count among [identitySummary, educationPhilosophy, curriculum, distinctiveTeaching]; identity = min(35, fields×10); teaching = min(20, |kinds ∩ {TEACHING_METHOD,STUDENT_GUIDANCE,OFFLINE_ACTIVITY}|×7); months = distinct activityDate[:7]; persistence = min(15, nmonths×5); professionalism = min(15, |kinds ∩ {MATERIAL,SUBJECT_EXPERTISE,EDUCATIONAL_CONTENT}|×5); verification = min(10, nuniqueSourceUrls×2); regional = 5 if OFFLINE_ACTIVITY ∈ kinds else 0; total = sum. Public evidenceKinds observed here are only OFFLINE_ACTIVITY / EDUCATIONAL_CONTENT / MATERIAL, so teaching ≤ 7 and professionalism ≤ 10 in this batch.

## Per-candidate table

| id | name / academy | philosophy (stripped chars + verdict) | curriculum (stripped chars + verdict) | evidence kinds present | 2026-dated? | OFFLINE_ACTIVITY? | informative? (approx) | overall DRY-RUN verdict | preview score (formula) |
|---|---|---|---|---|---|---|---|---|---|
| 62 | 신원경 / 윤선생IGSE아카데미 옥천학원 | NULL (0) → FAIL (missing) | 117 chars → PASS (≥30, full sentence, no generic-hit concept; subject-list check: not-list-only) | OFFLINE_ACTIVITY×2, EDUCATIONAL_CONTENT×1 | PASS (2026-08-29, 2026-01-08; 3rd null) | PASS | PASS (OFFLINE_ACTIVITY + EDUCATIONAL_CONTENT) | STAY RESEARCH_PENDING — fail: (1) philosophy missing | n/a (stays pending). Hypothetical recalc 63 = ident 30 (3 fields) + teach 7 + persist 10 (2 mo) + prof 5 + verif 6 (3 urls) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 91 | 신희진 / 과천보듬교육학원 | NULL (0) → FAIL (missing) | 85 chars → PASS (full sentence, not-list-only) | EDUCATIONAL_CONTENT×1, OFFLINE_ACTIVITY×2, MATERIAL×1 | PASS (2026-09-10; 3 null) | PASS | PASS (EDUCATIONAL_CONTENT + OFFLINE_ACTIVITY; MATERIAL not in informative set but others qualify) | STAY RESEARCH_PENDING — fail: (1) philosophy missing | n/a (stays pending). Hypothetical recalc 55 = ident 20 (2 fields) + teach 7 + persist 5 (1 mo) + prof 10 (MATERIAL+EDU_CONTENT) + verif 8 (4 urls) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 93 | 백준호 / 백준호영어전문학원 | NULL (0) → FAIL (missing) | 70 chars → PASS (full sentence, not-list-only) | EDUCATIONAL_CONTENT×1, OFFLINE_ACTIVITY×1 | FAIL (only 2025-05-14 + null; no 2026) | PASS | PASS (both kinds informative) | STAY RESEARCH_PENDING — fail: (1) philosophy missing; (3) no 2026-dated evidence | n/a (stays pending). Hypothetical recalc 56 = ident 30 (3 fields) + teach 7 + persist 5 (1 mo 2025-05) + prof 5 + verif 4 (2 urls) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 96 | 홍세미 / HLS어학원 | NULL (0) → FAIL (missing) | 82 chars → PASS (full sentence, not-list-only) | EDUCATIONAL_CONTENT×2, OFFLINE_ACTIVITY×2 | PASS (2026-09-01, 2026-08-31; 2 null) | PASS | PASS | STAY RESEARCH_PENDING — fail: (1) philosophy missing | n/a (stays pending). Hypothetical recalc 55 = ident 20 (2 fields) + teach 7 + persist 10 (2 mo) + prof 5 + verif 8 (4 urls) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 100 | 강수민 / 링키영어 대원포레나점 | 73 chars → PASS (≥30, generic_hit=false, remainder 73 ≥ 15) | 71 chars → PASS (full sentence, not-list-only) | OFFLINE_ACTIVITY×1, EDUCATIONAL_CONTENT×1 | PASS (2026-05-27; 2nd null) | PASS | PASS | WOULD-BE-EVALUATABLE (gates 1–5 all PASS; G6 UNKNOWN pending investigator confirm; G7 no-snapshot blocks publish only) | **61** = identity 35 (4/4 fields) + teaching 7 (OFFLINE×1) + persistence 5 (1 mo: 2026-05) + professionalism 5 (EDU_CONTENT×1) + verification 4 (2 urls) + regional 5. |
| 101 | 박은주 / MBC&Winclass | NULL (0) → FAIL (missing) | 86 chars → PASS (full sentence, not-list-only) | OFFLINE_ACTIVITY×1 | FAIL (only 2025-04-06) | PASS | PASS (OFFLINE_ACTIVITY ∈ informative set, approx) | STAY RESEARCH_PENDING — fail: (1) philosophy missing; (3) no 2026-dated evidence | n/a (stays pending). Hypothetical recalc 49 = ident 30 (3 fields) + teach 7 + persist 5 (1 mo 2025-04) + prof 0 + verif 2 (1 url) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 102 | 이선혜 / 이지키즈잉글리쉬 | 68 chars → PASS (generic_hit=false, remainder 68) | 68 chars → PASS (full sentence, not-list-only) | OFFLINE_ACTIVITY×1 | FAIL (only 2025-03-13) | PASS | PASS (approx) | STAY RESEARCH_PENDING — fail: (3) no 2026-dated evidence | n/a (stays pending). Hypothetical recalc 54 = ident 35 (4 fields) + teach 7 + persist 5 (2025-03) + prof 0 + verif 2 (1 url) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 108 | 서경희 / 윤선생 우리집앞영어교실 | 59 chars → PASS (generic_hit=false, remainder 59) | 79 chars → PASS (full sentence, not-list-only) | OFFLINE_ACTIVITY×1 | FAIL (only 2025-06-27) | PASS | PASS (approx) | STAY RESEARCH_PENDING — fail: (3) no 2026-dated evidence | n/a (stays pending). Hypothetical recalc 54 = ident 35 (4 fields) + teach 7 + persist 5 (2025-06) + prof 0 + verif 2 (1 url) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |
| 109 | 최정윤 / KJC 국제어학당삼계학원 | 68 chars → PASS (generic_hit=false, remainder 68) | 53 chars → PASS (≥30, full sentence, not-list-only) | OFFLINE_ACTIVITY×1 | FAIL (only 2025-12-24; year=2025 ≠ 2026) | PASS | PASS (approx) | STAY RESEARCH_PENDING — fail: (3) no 2026-dated evidence | n/a (stays pending). Hypothetical recalc 49 = ident 30 (3 fields: distinctiveTeaching null) + teach 7 + persist 5 (2025-12) + prof 0 + verif 2 (1 url) + regional 5. G6 UNKNOWN, G7 FAIL/no-snapshot. |

Notes:
- Char counts are whitespace-stripped lengths of the public `educationPhilosophy` / `curriculum` strings (Python `''.join(s.split())`), verified per candidate.
- Generic-phrase scan: none of the 9 curriculums/philosophies contained any generic token, so remainder length = full stripped length in every case (all ≥ 15 → none flagged generic-ad-only).
- Subject-list scan: every curriculum tokenizes to many out-of-allowlist tokens (e.g. 확인일, 인터뷰, 원문, 과정이…) → all NOT-list-only.
- Gate 6 UNKNOWN × 9; Gate 7 FAIL/no-snapshot × 9 (publish-blocker only).
- All 9 currently `eligibilityStatus: RESEARCH_PENDING` in public JSON — consistent with dry-run except #100, which is the sole WOULD-BE on public data (still needs investigator confirm + snapshot to actually transition).

## Counts

- WOULD-BE-EVALUATABLE × 1: #100
- STAY-PENDING (RESEARCH_PENDING 유지) × 8: #62, #91, #93, #96, #101, #102, #108, #109

Fail-reason summary: philosophy-missing ×5 (#62, #91, #93, #96, #101); no-2026-evidence ×5 (#93, #101, #102, #108, #109); overlap both ×2 (#93, #101).
