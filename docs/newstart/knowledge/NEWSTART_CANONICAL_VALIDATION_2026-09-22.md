# NEWSTART CANONICAL VALIDATION (2026-09-22)

- SOURCE: branch `docs/newstart-canonical-integration-20260922`, HEAD `4b807bd7` (fetch+prune 후 확인, 일치)
- WORK BRANCH: `docs/newstart-canonical-validation-20260922` (source HEAD에서 분기, Agent1 브랜치 미수정)
- POLICY: `NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md` — CLASSIFY FIRST. INFORM SECOND. JUDGE ONLY WHEN ASKED. (변경 없음)
- 방법: JSON 재계산 + 재실행 가능 validator (`NEWSTART_CANONICAL_VALIDATE_2026-09-22.py`, 47 checks, DB/network 불필요)
- 원칙: 불일치는 임의 수정 금지 → 원인 추적 후 본 보고서에 기록. Canonical JSON 3종 무수정.

## 1. Counts (재계산, 전부 일치)

| 집합 | 기대 | 실측 | 판정 |
|---|---|---|---|
| FAQ total (OP 83 + CHAT 26 + OFFICIAL 21, ID prefix 기준) | 130 | 130 | PASS |
| CASE total (CAFE 30 + CHAT 12 + CCRS 20, ID prefix 기준) | 62 | 62 | PASS |
| RELATIONS total | 186 | 186 | PASS |
| PARTIALLY_SUPPORTED_BY / RELATED_FAQ / UNEXPLAINED_BY_OFFICIAL / CONFLICT_REVIEW / CONTRADICTS / RELATED_CASE | 63/76/22/19/3/3 | 63/76/22/19/3/3 | PASS |
| conflictRegistry | C-01~C-04 | C-01~C-04 | PASS |
| unresolvedRefs (canonical 밖 pointer, overlap 0) | 108 | 108 | PASS |
| patterns PAT-01/02/03 members | 6/6/5 | 6/6/5 | PASS |

모델링 주의 (결함 아님): `classification`은 CHAT FAQ 26을 OPERATOR로 묶음(OPERATOR 109 = 83+26).
구분은 ID prefix + sourceType(`OPERATOR_KNOWLEDGE` 83 / `CHAT_TRANSCRIPT_PRIVATE` 26 / `OFFICIAL_SITE` 21)으로 유지됨.
동일하게 CASE-CHAT 12는 classification OPERATOR + sourceType CHAT_TRANSCRIPT,
CASE-CCRS 20은 classification CAFE_CASE + sourceType CAFE + `gradedFrom` provenance로 식별.
Agent1 소비 계약(후술)에 ID-prefix 규칙을 명시했으므로 validator는 prefix 기준을 정석으로 한다.

## 2. Source classification audit — PASS

- CASE→OFFICIAL 승격 0. classification=OFFICIAL은 FAQ-OFFICIAL-* 21건뿐이며 전원 sourceType=OFFICIAL_SITE.
- 허용 외 class 없음(실측 집합: FAQ {OPERATOR, OFFICIAL}, CASE {CAFE_CASE, OPERATOR}, REL 6종 + 어휘 8종).

## 3. Official status (V-01~V-21) — PASS + WARNING 1건(기지의 혼합판정)

- Entry 실측: OFFICIAL_CURRENT 16 / NOT_FOUND 3(V-05, V-08, V-20) / INSUFFICIENT_EVIDENCE 2(V-07, V-21).
- `officialVerificationSummary`는 V-11을 INSUFFICIENT bucket에 넣어 15/3/3으로 표기.
- 원인: V-11 혼합판정 — 추심중단 원칙 CURRENT(FAQ-59) + 금감원 민원 실효성 INSUFFICIENT.
  entry의 `officialStatusNote`에 양쪽이 병기되어 있고 HANDOFF §3이
  "혼합 판정(V-11·V-20)은 PR #370 판정표 원문이 우선"이라 못 박았으므로 entry가 우선한다.
- 조치: Canonical JSON 무수정. validator는 entry 기준을 PASS로, summary 차이를 WARNING(문서화된 기지 상태)으로 보고.
  향후 summary를 고친다면 entry가 아닌 summary 쪽을 `OFFICIAL_CURRENT 16 / INSUFFICIENT [V-07, V-21] + V-11 혼합주석`으로 additive 수정 (이번 작업 범위 밖, RESEARCH 아님).
- NOT_FOUND≠FALSE 오독 0. NOT_FOUND 3건 모두 `officialStatusNote`에 범위 한정("날짜만", "원칙은 CURRENT, 세부 NOT_FOUND") 병기.

## 4. NOT_FOUND + CASE coexistence — PASS (regression fixture 확정)

- V-05: FAQ-OFFICIAL-V-05 NOT_FOUND + case PRESENT(5568-Q08/Q30) + UNEXPLAINED REL-008/REL-030.
- V-08: FAQ-OFFICIAL-V-08 NOT_FOUND + OPERATOR 2 + CASE-CAFE-5862/CASE-CHAT-004/CASE-CCRS-784675/FAQ-CHAT-020 + UNEXPLAINED REL-011/058/171/172/173/174.
- V-20: FAQ-OFFICIAL-V-20 NOT_FOUND + OPERATOR 2(4451-Q37/Q38) + TRUE GAP §6 8행 + UNEXPLAINED REL-069/070/175~182.
- Golden QA fixture C로 고정 (ID 목록은 GOLDEN_QA manifest 참조).

## 5. Repeated patterns — PASS + WARNING 1건(어휘 선택 차이)

- PAT-01/02/03 전부 officialRule=false, member resolve dangling 0, member 수 6/6/5.
- PAT-02의 classification이 REPEATED_CASE_PATTERN이 아닌 NEEDS_MORE_CASES.
  원인: relation 어휘에 NEEDS_MORE_CASES가 정식 등재되어 있고, PAT-02 note가
  "predictionVsActual=UNKNOWN 추적 대상. 예측값 기준화 금지"로 비규칙 지위를 명시 — 승격 위험 없음.
  validator WARNING(문서화된 상태)으로 보고, JSON 무수정.

## 6. Conflict audit — PASS

- C-01 감면율: CONTRADICTS 3건(REL-009/032/073, FAQ-OP-009/032/074 ↔ V-06) 병존, 판정 없음, 근거 인용만.
- C-02 민원 실효성: CONFLICT_REVIEW REL-169/170 + registry에 "5856: CASE_INDEX 서술이 JSON 코퍼스에 미지원 — pipeline conflict 보존" 명시.
- C-03 재산축소 11건: CONFLICT_REVIEW + 권고 승격 금지.
- C-04 수식 표현: relation 결측이 정상임을 registry에 기록. 삭제·정답처리 0.

## 7. TRUE GAP audit — PASS (overclaim 0)

- Registry: FSS 풀체인 0 / COURT 풀체인 0 / 15일 공식출처 0 / Credit4U ABSENT / HF 0 / 주담대연장 0.
- RESULT_CONFIRMED 15건(카페 2 + 챗 3 + CCRS 10, 전원 finalRate 보유 또는 참여자 직접 보고) 중
  GAP article(5987/5752/5988/5901/6010/5947/5957/5868/5883/5749/5968/5997/5920)과 겹치는 확인 0건.
- doNotCanonicalize 10건 보존. RESULT_PARTIAL 25 / UNRESOLVED_CASE 16 / OPERATOR_GUIDANCE 5 / THIRD_PARTY_RESULT 1 — 격상 없음.

## 8. Provenance — PASS (checker 1차 오탐 해소)

- 1차 checker가 OFFICIAL provenance(site+citations+verificationDoc)를 협소한 CAFE 키로만 판정해 21건 missing 오탐.
  Per-class 규칙(§validator `check_provenance`)으로 교정 후 missing 0.
- CAFE: cafeKey+article / CHAT: file+lineRefs+REVIEW_ONLY / CCRS: cafeKey+article+gradedFrom(branch+commit) / OFFICIAL: site+citations+PR#370 doc.
- 추측 보완 0. 없는 값은 null/UNKNOWN 유지.

## 9. PII — PASS (findings 0)

- 전화/이메일 정규식 0건. contentRaw/authorNickname 필드 없음.
- FAQ-CHAT 26건 answer=null + notes "참여자 식별정보 제거" — REVIEW_ONLY 미검증 상태를 null로 보존한 설계이므로 결함 아님.
  Agent1 계약에 "CHAT answer=null = UNVERIFIED, 답변으로 제시 금지"로 명시.

## 10. Referential integrity — PASS

- JSON parse error 0, duplicate ID 0 (378 unique), dangling relation 0,
  unresolvedRefs의 canonical overlap 0, orphan conflictTag 0, relation 내 판정문구 0 (전원 "판정·정답 확정 금지, 병존 기록").

## 11. Validator 결과

- `python NEWSTART_CANONICAL_VALIDATE_2026-09-22.py` → 47 checks, 0 FAIL, 2 WARNING(§3 V-11 summary, §5 PAT-02 class — 둘 다 문서화된 기지 상태).
- Production DB 불필요, network 불필요, Canonical JSON만으로 실행.

## 12. RESEARCH_NEEDED (새 조사 금지 — 기록만)

1. V-05 종료일 FSS 보도자료 추적 (기존 핸드오프 이관 항목 유지).
2. V-07/V-08 예규 PDF, V-20 유예 세부 (공식 확보 시 INSUFFICIENT/NOT_FOUND 해소 가능).
3. 5904/5948/5957 예측 대비 실제, 6010/5988/5997 후속 (PAT-02 추적).
4. (신규 발견) summary V-11 표기 정리 시 entry 우선 원칙 재확인 — 데이터 수정이 아닌 문서 정합성 작업으로만.

## 13. 변경 금지 준수

- Production DB write 0, ACTIVE promotion 0, merge 0, deploy 0, 새 리서치 0, Agent1 코드 수정 0.
- Canonical JSON 3종 + POLICY + HANDOFF 무수정. 본 브랜치 추가분은 validator/QA/문서 4종뿐.
