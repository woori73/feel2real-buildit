# A/B INGEST READY 보고 — 2026-09-30 (SAFE HEAD 25e3f387 이후, READ ONLY)

운영 쓰기 **없음**. 재수집 없음. 산출물: `INGEST_READY_MANIFEST_AB_2026-09-30.json`(생성기 `build_ingest_manifest.py`, 결정적).

## 1. 기존 ADMIN ingest API 확인 (repository read-only)

| 항목 | 확인 결과 |
|---|---|
| knowledge 생성 | `POST /api/admin/newstart/wiki` → 항상 **DRAFT** 로 저장 (`NewstartKnowledgeService.create`). DTO 필드: sourceType, category, title, summary, content, authority, tags, sourceUrl, sourceDate, effectiveFrom/To, confidence, relatedCaseIds, relatedRuleIds |
| REVIEW 전환 | `PUT /api/admin/newstart/wiki/{id}/status?status=REVIEW` (허용 전이: DRAFT→REVIEW) |
| 승인 | `POST /api/admin/newstart/wiki/review/{id}/approve` (reason 필수, 경고 있으면 confirmed 필요). `update()`로 ACTIVE 직접 설정은 서비스가 거부 |
| dedupe | 이 경로는 **중복검사 없음**(title/dedup_key 검사 없이 save). `dedup_key`(V1012 UNIQUE)는 DTO에 없어 API로 채울 수 없음 → 중복 판정은 **호출 전 GET 목록 대조로 우리 쪽에서** 해야 함 |
| relation 생성 | **REST endpoint 없음**. relation 은 curated/official ingest 서비스 내부에서만 생성. `relatedCaseIds` 텍스트 필드로만 참조 가능 |
| empirical case 생성 | REST payload 경로 없음. `POST /ingest/cases`, `/ingest/cases/repair` 는 **서버 디스크의 디렉터리**(`articlesDir`)를 읽는다. `data/newstart-cafe/incr_20260930/articles/*.json` 16개는 저장소에 있으나 서버에 배치하는 표준 경로는 기존 `newstart-seed.yml`(EMPIRICAL phase는 `initial_seed` 107건 게이트 하드코딩)뿐 |
| 배포 상태 | 운영 jar에 `ingest/cases/repair` 포함(GET 401). cafe-qa 전용 ingest 없음 |

**ADMIN API PATH: 부분 확인.** knowledge(생성→REVIEW→승인)는 기존 API로 가능. **relations 와 empirical cases 는 기존 API로 원격 payload 주입 불가** — 새 API/migration/framework 개발 금지 지침상 이 둘은 별도 결정 필요(아래 §6).

## 2. 결과 수치

- A/B INGEST 후보: **23건** (A 9 + B 9[798001 미러 1 + 799029 1 + K1~K7 7] + phase1 지정수집 5)
- REVIEW PAYLOAD READY: **20건** — 제외 3건: 6040·6044(LOW_INFO, 구조화할 사실 없음), 4078(이미지 전용, 텍스트 본문 없음)
- DUPLICATE(확정): **0건**
- DUPLICATE CHECK REQUIRED: **2건** — 5568, 4451 (운영 OPERATOR_KNOWLEDGE 1·CAFE_ARTICLE 1건과 충돌 가능, 이전 curation `NEWSTART_OPERATOR_FAQ_5568_4451_CURATION_2026-09-22` 이력)
- NEW: empirical cases 16건(운영 107건 = article_id 5855~6010, 미포함). knowledge 후보 23건 중 5568·4451 을 뺀 21건은 제목 고유 → NEW 예상(이 중 payload 미준비 3건 포함), 단 **운영 119건 제목 목록은 ADMIN 없이 볼 수 없어** "PROD_TITLE_CHECK_REQUIRED"
- RELATIONS READY(정의): **8건** — K1~K7→798001 미러(SIMILAR 7) + K1↔6050(토픽 1). 기존 9건 중 **K1↔156(5834)은 운영 cases에 5834가 없어 제외**. 또한 대상 ID는 HOME 로컬 348/346/156이라 운영에서 재매핑 필요. 생성 경로는 현재 **없음**(§1)
- C OWNERSHIP EXCLUDED: **8** (5369 5372 5374 5388 5393 5429 5451 5582 → `feat/newstart-cafe-qa-phase1-20260930`, A-to-Z Agent 소유. 이 Collector 는 ingest 하지 않음)

## 3. 반드시 지켜야 할 발견 사항 (승인 전 게이트)

1. **4451 SAFETY_FLAG**: 원문 Q8·Q21·Q22·Q27·Q28·Q35 에 타인 계좌 이체·현금화 등 재산심사 회피성 권고. phase1 SQL(`increment_phase1.sql`)의 4451 knowledge 미러는 **원문 그대로**라 그대로 승인하면 RAG 가 회피 권고를 답할 위험. → **phase1 미러 승인 금지**, manifest의 삭제본(해당 Q&A 블록 치환)으로만 REVIEW 생성. 원문은 raw 보존.
2. **5568**: Q14·Q15(재산 정리, 보험 계약자·수익자 가족 변경) 승인 전 SAFETY 재검수. Q28은 부동산 매도·명의변경 금지를 명시(안전).
3. **798001 RAW 미러**: CONFIRMED/CLAIM/INFERENCE 혼재 → 미러 승인 보류, **K1~K7 분리 항목 승인** 권고. 라벨: K1·K2=CONFIRMED(사용자 캡처 대조, 공식 링크 재대조 권장), K3=CLAIM(2차 해석), K4·K5=CLAIM(상담 제보 20M/35M, 서로 상충, MERGE 금지), K6·K7=INFERENCE(RULE/감면율 산식 변환 금지). 기관은 **신복위**, 새출발기금 자동 적용 금지.
4. 799029 는 **개인워크아웃(신복위)** 단일 사례 — 새출발기금 부실차주 규칙과 혼용 금지.
5. 6046/6055 는 서로 다른 경로 권고(부실우려→실효 vs 바로 부실차주). 우열 판단 없이 병존 표기.
6. 6050 은 운영자 관찰과 회원 경험이 상반 → CONFLICT_REVIEW.
7. 개인정보 스캔(전화·계좌·주민번호·이메일): raw 전체 0건.

## 4. D CCRS JUNE (기록)
- LIST COLLECTION COMPLETE = **557 UNIQUE**
- ORIGINAL BODY MAPPING = **NOT COMPLETE**
- STRUCTURED = **NO**
- DB INGEST = **NO**
- P1/P3/LOW(170/180/140) = **TITLE-HEURISTIC DRAFT**. 원문 없이 사례 내용·상담 답변을 만들지 않았다.
- 고가치 P1 5건 보충은 별도 FOLLOW-UP(현재 Claude 환경 Naver 접근 불가, 로그인·우회 시도 없음).

## 5. EDITORIAL
- `DRAFT_924on_roundup.md` 검수 → **NEEDS REVISION**: 범위(CCRS·9/24 기존 게시 주제 혼입), 원문 오기 3곳(2천/3,500만원 제보 문구 및 출처, 6043 원장초기화 소요기간, 6050 상반 경험 누락), 6047 운영자 답변 누락, 이행 불가 예고("6월 편").
- 수정본 `cafe_editorial/DRAFT_v2_newhopero_0925on.md` — newhopero 9/28~9/30 7건만, 사례→대응→주의→공식 확인→미확인 순. **EDITORIAL READY, POST 안 함.** 9/24 기존 게시물(6029)과의 실제 겹침은 게시 시점에 원문 대조 필요(본문이 저장소에 없어 지금은 검증 불가).

## 6. ADMIN 로그인 후 순서 (STOP 지점 포함)
1. production API read-only census: `GET /api/admin/newstart/wiki/source-type/CAFE_ARTICLE`, `/OPERATOR_KNOWLEDGE`, `/cases/{articleId}` 로 A/B 중복 대조 (5568/4451 우선)
2. missing only: `POST /wiki` → `PUT /{id}/status?status=REVIEW` (manifest 의 `payloadReady=true` 20건, 4451 은 삭제본, 798001 미러 제외)
3. **여기서 STOP 하여 REVIEW 생성 결과 보고**
4. 이후 승인 → ACTIVE → relations 확인 → RAG QA (CONFIRMED / CLAIM / EXPERIENCE / INFERENCE 구분 유지)
- 별도 결정 필요: relations 8건과 empirical cases 16건은 기존 API로 원격 주입 불가. (a) 기존 seed workflow 를 서버 디렉터리 방식으로 확장하는 것은 "새 seed workflow를 표준으로 만들지 않는다" 지침과 충돌하므로 승인 전 실행하지 않음, (b) knowledge 만 먼저 REVIEW 로 적재하고 cases/relations 는 보류하는 안이 지침에 부합.
