# A/B REVIEW 생성 결과 — 2026-09-30 (application API 경유, REVIEW ONLY)

- 경로: 정상 ADMIN 세션 → `POST /api/admin/newstart/wiki`(DRAFT) → `PUT /{id}/status?status=REVIEW`. 직접 SQL/RDS write 0, approve 0, ACTIVE 0.
- 운영 사전 상태: knowledge **127건**(ID 1~127; ACTIVE 119 + REVIEW 8[ID 120~127, 다른 에이전트 `admin-f2r` b6 적재분]). 전체를 GET으로 읽고 원문 번호·제목·URL 대조.
- 사후 상태: **144건** (ACTIVE 119 불변 / REVIEW 25). 새 행은 ID **128~144, 17건 전부 REVIEW, 이력 DRAFT→REVIEW**.

## 1. 숫자 체계 (단위별 분리, 합계 검증)

| 단위 | 수 | 설명 |
|---|---|---|
| SOURCE ITEMS (원문 글) | **16** | A 9(6037 6038 6040 6043 6044 6046 6047 6050 6055) + B 2(798001 799029) + phase1 지정수집 5(3401 3438 4078 4451 5568) |
| KNOWLEDGE CANDIDATES (행) | **23** | 원문 16 → 원문별 1행 16 + 798001 분리 K1~K7 7행 |
| ㄴ EXCLUDED | 3 | 6040·6044(LOW_INFO), 4078(이미지 전용, 본문 없음) |
| ㄴ HOLD | 2 | 4451(재산심사 회피성 조언), 798001 RAW 미러 |
| ㄴ DEDUPED(운영 중복) | 1 | 6050 ↔ 운영 118·119 |
| ㄴ NEW → REVIEW 생성 | **17** | 23 − 3 − 2 − 1 |
| CASE CANDIDATES (행) | 16 | 원문 1건당 1행, **write 0** |
| RELATION CANDIDATES (행) | 8 | K1~K7→798001 미러 7 + K1↔6050 토픽 1, **write 0** |

검산: 3 + 2 + 1 + 17 = 23 ✓. 6050이 dedupe 되면서 relation "K1↔6050"의 대상 knowledge 는 새 행이 아닌 **운영 118/119**로 재매핑해야 함(아래 §4).

## 2. manifest → 운영 ID 매핑 (추적)

| manifest | 운영 knowledgeId | 상태 | evidence |
|---|---|---|---|
| AB-6037 | 128 | REVIEW | CLAIM |
| AB-6038 | 129 | REVIEW | CLAIM |
| AB-6043 | 130 | REVIEW | CLAIM |
| AB-6046 | 131 | REVIEW | CLAIM |
| AB-6047 | 132 | REVIEW | CLAIM |
| AB-6055 | 133 | REVIEW | EXPERIENCE |
| AB-799029 | 134 | REVIEW | EXPERIENCE |
| AB-798001-K1 | 135 | REVIEW | CONFIRMED(사용자 캡처 대조, 공식 링크 미대조) |
| AB-798001-K2 | 136 | REVIEW | CONFIRMED(동) |
| AB-798001-K3 | 137 | REVIEW | CLAIM |
| AB-798001-K4 | 138 | REVIEW | CLAIM |
| AB-798001-K5 | 139 | REVIEW | CLAIM |
| AB-798001-K6 | 140 | REVIEW | INFERENCE |
| AB-798001-K7 | 141 | REVIEW | INFERENCE |
| AB-3401 | 142 | REVIEW | CLAIM |
| AB-3438 | 143 | REVIEW | CLAIM |
| AB-5568 | 144 | REVIEW (SAFETY REVIEW 대기) | CLAIM |

## 3. 중복·HOLD 판정
- 정확 중복(원문 번호/제목/dedup 태그): 0건. 운영 knowledge 전체 127건 본문·URL 검색에서 후보 원문 번호 매칭 없음.
- **6050 → DEDUPED**: 운영 118(OPERATOR_KNOWLEDGE, 2024 기준선 사례)·119(CAFE_ARTICLE, 2026-08 이후 변화 관찰)와 내용 중복. 새 행을 만들지 않음. 6050 고유 잔여(회원 반례 "소명 없었다", 운영자의 '사용처 소명 요구' 관찰)는 별도 결정 필요.
- **4451 → HOLD**: 원문에 재산심사 회피성 조언(Q8·Q21·Q22·Q27·Q28·Q35). 운영에 넣지 않음. 삭제본 manifest 는 검토용으로만 보존. phase1 SQL 미러는 승인 금지.
- **5568 → REVIEW 생성, SAFETY REVIEW 대기**: 운영 knowledge 에 5568/4451 제목·URL 없음(이전 우려 해소: 운영 OPERATOR_KNOWLEDGE 1건=118, CAFE_ARTICLE 1건=119). Q14·Q15 답변은 치환 표시 2곳으로 저장(원문 raw 보존). 승인 전 재검수 필수. Q13("보증금을 별도로 소명할 필요는 없습니다")은 6050 관찰과 상충 → 승인 시 충돌 표기 필요.
- **798001 RAW 미러 → HOLD**(생성 안 함). K1~K7 이 provenance 를 유지한 채 대체.
- K1·K2: 운영 공식 자료 107(`[공식] 부실우려차주 신청절차…`)은 금융자산·9/7 언급이 없어 **교차검증 근거가 되지 못함**. 공식 링크 대조는 별도 필요.

## 4. CASES / RELATIONS — 운영 write 경로 확인 (결과만)
- 코드 전수 검색 결과: `NewstartEmpiricalCase` 저장은 `NewstartDataIngestionService`(`/ingest/cases`, `/ingest/cases/repair`, **서버 디렉터리** `articlesDir` 읽기)뿐. `NewstartKnowledgeRelation` 저장은 `NewstartCuratedReviewIngestService`·`NewstartOfficialReviewIngestService` **내부(디렉터리 기반 ingest)**뿐. 원격 payload 를 받는 REST 는 **없음**. Jipijigi 서비스는 읽기 전용.
- 따라서 지금 cases 16·relations 8 을 운영에 넣을 정상 경로는 **기존 API만으로는 없음**. HOME 로컬 ID(348/346/156)는 사용하지 않았고 사용하지 않는다. relation 은 운영에서 생성된 실제 ID(128~144, 118·119)로 재매핑해야 하며, 대상은 다음과 같다.
  - K1~K7(135~141) → 798001 원문 미러: 미러를 HOLD 로 두었으므로 **대상 행이 운영에 없음** — 미러 생성 여부 또는 대체 앵커 결정 필요
  - K1(135) ↔ 118/119(폐업 보증금 서류 요구, 토픽 연결)
- 새 seed workflow/migration/framework 는 구현하지 않았다.

## 5. 참고 사항
- 브라우저 탭 동결로 작업 결과 확인이 지연되었으나, 실행된 POST/PUT 는 정확히 17회분(ID 128~144)이며 중복 생성 없음(전 행 제목·dedup 태그 유일, `CASE_COLLECTOR_AB` 태그 17건).
- 다른 에이전트(A-to-Z Agent)의 REVIEW 8건(ID 120~127, C 담당)은 건드리지 않았다.
