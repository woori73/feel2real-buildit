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
| `app.admissions.embedding.timeout-ms` | 1500 | 임베딩 서버 호출 타임아웃 (초과 시 LIKE 폴백) |
| `app.admissions.embedding.min-similarity` | 0.5 | 벡터 단독 관련성 하한. 실제 EmbeddingGemma 라벨 질문셋으로 보정 후 확정 |
| `app.admissions.embedding.circuit-failure-threshold` | 2 | 연속 실패 이 횟수에 서킷 오픈 |
| `app.admissions.embedding.circuit-open-ms` | 30000 | 서킷 오픈 유지 시간 (이후 재시도) |
| `app.admissions.embedding.vector-limit` | 20 | 벡터 후보 수 |
| `app.admissions.embedding.like-weight` | 0.4 | 하이브리드 LIKE 가중치 |
| `app.admissions.embedding.vector-weight` | 0.6 | 하이브리드 벡터 가중치 |
| `app.admissions.embedding.official-bonus` | 0.1 | 공식자료 가산점 |
| `app.admissions.public-api.enabled` | false | 공개 상담 API 활성화 (별도 승인 대상) |

참고 (지피지기 측, 본 PR과 무관): `newstart.embedding.*` (enabled 기본 false, model-version `embeddinggemma-2`, dimension 768, timeout-ms 5000).

운영 환경의 실제 설정값은 저장소에 없으며, 이 문서에 운영값을 기록하지 않는다.

## 4. 운영 적용 순서 (v3.3 시드, 관리자 UI 기준)

1. 시드 v3.3(`docs/admissions/seed-v33/seed-v3.3-ops.json`) 적재 → 2. 행 전체 ACTIVE 승격(activate-all) →
3. 지식 항목 생성(knowledge/build) → 4. activate-all 한 번 더(항목 승격, 근거 행이 ACTIVE인 항목만) →
5. 재색인(knowledge/reindex MISSING) → 6. 인덱스 reload 확인.
`enabled`/`service-url`은 실제 모델로 `min-similarity`를 보정하기 전까지 비워 둔다(LIKE+구조화 검색만 동작).

## 재색인 안정화(백그라운드 잡) 운영 메모

- 재색인은 `POST /api/admin/admissions/knowledge/reindex`(MISSING)가 **백그라운드 잡**을 시작하고 즉시 202를 돌려준다. 관리자 화면에서 진행률(전체/완료/미완료/실패 사유)을 보고, 필요하면 "재색인 중단"을 누른다. 진행 상태 API: `GET .../knowledge/reindex/status`.
- 실측(EC2 t3.large, `admissions-ops.yml` action=bench, 합성 문서·읽기 전용): 일정형 117자 ≈1.3s, 전형형 1900자 ≈2.9s(최대 3.0s), 임베딩 서버 피크 메모리 ≈1.15GB(상한 2GB), load1 피크 0.5, 앱 health 평균 8ms. 재색인 타임아웃은 20s(`app.admissions.embedding.index-timeout-ms`), 질문 타임아웃 1.5s·서킷브레이커와 분리.
- 예상 소요: 188건 ≈ 전형 116×2.9s + 일정 72×1.3s + 항목 간 0.2s ≈ **8분 이내**(실제 전형 본문은 대부분 더 짧아 더 빠름).
- 재색인 중에는 임베딩 서버가 동시성 1이라 사용자 질문의 벡터 검색이 일시적으로 503→키워드 검색으로 폴백한다(답변은 유지, 벡터 가산점만 일시 없음).
- 실패 시 재시도: 같은 버튼(MISSING) 재실행 — 이미 성공한 항목은 대상에서 빠진다. 앱이 재시작돼도 완료분은 DB에 남고 진행률은 DB 기준으로 이어서 보인다(실패 사유 메모리분은 로그 `admissions reindex item failed`에서 확인).
- 롤백: 배포 롤백은 이전 JAR로 되돌리기(스키마 변경 없음). 이미 만든 임베딩 행은 무해하며 `app.admissions.embedding.enabled=false`로 벡터 검색을 끌 수 있다(키워드 검색 유지).
