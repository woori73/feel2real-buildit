# 입시 임베딩 파이프라인 운영 배포 절차 및 롤백 계획 (2026-10-21)

> 전제: 이 문서는 절차 설계다. 운영 DB 적재·ACTIVE 승격·임베딩 서버 구축은
> 각각 별도 승인 후에만 실행한다. 본 PR 병합만으로 운영 동작은 바뀌지 않는다
> (신규 관리자 API는 호출 전까지 무동작, 임베딩 설정 기본값은 기존과 동일).

## 0. 사전 확인 (배포 전)

1. 운영 RDS가 실제 앱 연결 대상인지 확정하고, PITR 활성화와 최신 스냅샷 존재를 확인한다.
   (미확인 상태에서는 운영 DB를 수정하지 않는다 — 지시서 B의 전제.)
2. 임베딩 서버 준비 상태 확인: `app.admissions.embedding.service-url`에 연결할
   EmbeddingGemma 서버가 구축돼 있는가. 없으면 벡터 경로는 비활성(LIKE 폴백)으로 두고
   서버 구축을 별도 과제로 분리한다. 앱 배포와 서버 구축은 독립이다.
3. PR CI 전체 PASS 확인 후에만 병합한다 (기존 게이트와 동일).

## 1. 배포 절차

1. PR 병합 → 기존 deploy 파이프라인으로 운영 배포 (스키마 변경 없음 — Flyway 신규 마이그레이션 없음).
2. 배포 직후 확인: 서비스 health, `/admissions/workspace` 화면, 기존 상담 플로우 정상 동작.
   벡터 인덱스 로그 "입시 벡터 인덱스 로드: N건" — 초기에는 0건이 정상이다.
3. 자료 적재 (별도 승인 후, 기존 경로 유지): 관리자 도구로 seed-v3.2 적재 → DRAFT 상태 확인.
4. 지식 항목 생성: `POST /api/admin/admissions/knowledge/build` → created 건수가
   전형+일정+자료 행 수와 일치하는지 확인.
5. 승격 (별도 승인 후, 기존 경로 유지): 구조화 행 승격(activate-all)과 지식 항목 검수·승격.
6. 임베딩 생성: `POST /api/admin/admissions/knowledge/reindex` (mode=MISSING)
   → embedded/failed 건수 확인. 실패가 있으면 사유(서버 미연결 등)를 먼저 해결한다.
7. 인덱스 확인: `/index/reload` 후 인덱스 건수 = ACTIVE 항목 수와 일치 확인.
8. 실검증: 골든 질문 6문항(대학명·학과/전형·수능최저·제출서류·일정·캠퍼스 구분)으로
   운영 답변의 근거 출처를 대조한다. 근거 없는 질문 1건으로 답변 제한도 확인한다.
9. 공개 API 활성화(`app.admissions.public-api.enabled`)는 위 검증 통과 후 별도 승인으로 진행한다.

## 2. 롤백 계획

- 코드 롤백: 본 PR은 신규 서비스 2개 + 관리자 엔드포인트 2개 + 문서만 추가한다.
  기존 검색 경로는 건드리지 않았으므로, 문제 시 직전 배포 SHA로 재배포하면 원상 복귀한다.
- 데이터 롤백: 생성된 지식 항목은 dedup_key 접두(`type:`/`schedule:`/`material:`)로 식별 가능하며
  DRAFT 상태이므로 검색에 노출되지 않는다. 필요 시 해당 dedup_key 항목과 그 임베딩
  (CASCADE)을 삭제한다. 구조화 원본 행(전형·일정·자료)은 build가 수정하지 않으므로 영향 없다.
- 임베딩 롤백: `admissions_knowledge_embeddings`에서 model_version 행을 삭제하고
  `/index/reload`를 호출하면 벡터 검색은 비활성화되고 LIKE 폴백으로 자동 전환된다.
- 설정 롤백: `app.admissions.embedding.enabled=false`로 두면 하이브리드는 LIKE 전용으로 동작한다
  (기존 운영 상태와 동일).

## 3. 환경변수 설정 목록 (비밀값 제외)

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.admissions.embedding.enabled` | true | 벡터 경로 사용 여부. false면 LIKE 전용 |
| `app.admissions.embedding.service-url` | (빈 값) | EmbeddingGemma 서버 주소. 비어 있으면 임베딩 불가(폴백) |
| `app.admissions.embedding.model-version` | `embeddinggemma-2` | 임베딩 세대 식별자. 바꾸면 전체 재색인 필요 |
| `app.admissions.embedding.dimension` | 768 | 벡터 차원 |
| `app.admissions.embedding.timeout-ms` | 10000 | 임베딩 서버 호출 타임아웃 |
| `app.admissions.embedding.vector-limit` | 20 | 벡터 후보 수 |
| `app.admissions.embedding.like-weight` | 0.4 | 하이브리드 LIKE 가중치 |
| `app.admissions.embedding.vector-weight` | 0.6 | 하이브리드 벡터 가중치 |
| `app.admissions.embedding.official-bonus` | 0.1 | 공식자료 가산점 |
| `app.admissions.public-api.enabled` | false | 공개 상담 API 활성화 (별도 승인 대상) |

참고 (지피지기 측, 본 PR과 무관): `newstart.embedding.*` (enabled 기본 false, model-version `embeddinggemma-2`, dimension 768, timeout-ms 5000).

운영 환경의 실제 설정값은 저장소에 없으며, 이 문서에 운영값을 기록하지 않는다.
