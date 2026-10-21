# 입시 DB 및 임베딩 인덱스 설계서 (2026-10-21)

## 1. 현재 구조 (실측)

- 입시 스키마는 마이그레이션 **V1078 단일 파일**로 완결된 10개 테이블이다.
  `admissions_universities`, `admissions_official_materials`, `admissions_departments`,
  `admissions_types`, `admissions_schedules`, `admissions_knowledge_items`,
  `admissions_knowledge_embeddings`, `admissions_conflicts`,
  `admissions_youtube_analyses`, `admissions_counseling_sessions`.
- 상태 흐름은 전 테이블 공통 `DRAFT → REVIEW → ACTIVE (+ARCHIVED)`.
- 검색은 이미 2층으로 구현돼 있다.
  - 사실 검색 `AdmissionsFactSearchService`: 학과·전형·일정 구조화 행 대상 문자열 규칙 매칭 (벡터 없음).
  - 지식 검색 `AdmissionsResearchService`: 하이브리드 — LIKE 0.4 + 벡터 0.6 + 공식 가산 0.1,
    LIKE 폴백, 필터 ACTIVE + OFFICIAL + FACT + 학년도 확정 여부. 벡터는
    `AdmissionsVectorIndex` 인메모리 코사인 (L2 정규화 전제, 내적).
- 임베딩 저장 구조도 이미 있다: `admissions_knowledge_embeddings`
  (지식 항목당 모델별 1벡터, LONGBLOB 768차원 float32 LE, UK(knowledge_id, model_version), CASCADE).

## 2. 비어 있던 부분 (본 PR의 구축 범위)

| 결손 | 내용 |
|---|---|
| 지식 항목 생성 | 지식 항목은 상담 Q&A(`loadKnowledgeBase`)에서만 생성됐다. 공식자료·전형·일정 구조화 데이터는 지식 항목이 없어 벡터 검색 대상이 아니었다. |
| 임베딩 쓰기 | embeddings 테이블에 save하는 코드가 없었다. 항목이 ACTIVE가 돼도 벡터가 생성되지 않아 인덱스는 항상 비어 있었다. |
| 재색인 | 인덱스 reload API(`/index/reload`)만 있고, 임베딩 생성·교체 경로가 없었다. |

신규 테이블은 만들지 않는다. 기존 스키마로 전부 해결된다.

## 3. 지식 항목 생성 설계 (`AdmissionsKnowledgeBuildService`)

- 입력: `admissions_types`, `admissions_schedules`, `admissions_official_materials` 전체 행.
- 출력: `admissions_knowledge_items` (source_class=OFFICIAL, role=FACT, **status=DRAFT** — 자동 승격 금지 원칙 유지).
- 항목 단위 = 임베딩 단위 (기존 설계: 항목당 벡터 1개). 전형·일정 단위로 쪼개는 것이
  사실상 청크 분할이며, 각 항목이 출처 자료(source FK)와 연결돼 근거 위치를 반환할 수 있다.
- 본문 합성:
  - 전형: 제목 줄 + `details`(모집인원·지원자격·전형방법·수능최저 라벨 텍스트) + "근거 자료: {자료명}".
  - 일정: 대학·학년도·제목·기간·비고 + 근거 자료 줄.
  - 자료 개요: 자료명·발행처·발행일·원문 URL.
- 검색 메타데이터: 학년도(academic_year), 학년도 확정 여부(year_confirmed = 학년도 ≤ 2027),
  대학(source→university), 캠퍼스(전형명의 `[세종캠퍼스]` 등 명시 표기를 tags에 보존),
  출처(document_key는 원본 구조화 행의 source FK로 추적, source_url 병기).
- 멱등·중복 방지: `dedup_key` UK 활용 — `type:{대학ID}:{학년도}:{전형ID}`,
  `schedule:{대학ID}:{학년도}:{일정ID}`, `material:{자료ID}`.
  재실행 시 본문이 같으면 변경 없음, 다르면 본문 갱신 + version+1, 변경된 항목 ID를 반환해 재색인 연동.
- 2027/2028 분리: academic_year + year_confirmed가 분리 축이다.
  검색 STEP1(확정)과 STEP2(계획)이 이미 이 축으로 나뉘어 있어 혼합되지 않는다.

## 4. 임베딩 생성·재색인 설계 (`AdmissionsEmbeddingService`)

- 임베딩 입력: `제목 + 본문`. 클라이언트는 기존 `AdmissionsEmbeddingClient`
  (지피지기와 동일 HTTP 계약 `POST {service-url}/embed`, 모델 `embeddinggemma-2`, 768차원)를 재사용.
- 저장 전 L2 정규화를 서비스에서 보장한다 (인덱스가 내적=코사인을 전제하므로 서버 반환값에 의존하지 않음).
- upsert: UK(knowledge_id, model_version)로 조회해 있으면 벡터 교체, 없으면 신규 — 재처리해도 중복 행이 생기지 않는다.
- 재색인 범위: 단건(reindexItem), 미적재분(reindexMissing — ACTIVE 중 벡터 없는 항목), 지정 목록, 전체.
  자료가 바뀌어 build에서 변경된 항목은 changedKnowledgeIds로 받아 해당 청크(항목)만 재색인한다.
- 완료 후 `AdmissionsVectorIndex.reload()`로 인메모리 인덱스를 갱신한다 (앱 재시작 불필요).
- 실패 처리: 임베딩 서버 미연결·차원 불일치·비활성(enabled=false)은 건별 실패 사유로 집계해 반환한다.
  조용히 성공으로 처리하지 않는다.
- 네임스페이스 분리: 입시 전용 테이블·전용 인덱스 인스턴스·전용 설정 prefix(`app.admissions.embedding`)로
  지피지기(newstart_knowledge_embedding)와 이미 분리돼 있다. 공유하지 않는다.

## 5. 성능·운영 적합성 (기존 실측 근거)

- 임베딩 모델 서버 실측 (지피지기 테스트 환경, CPU): 모델 로드 RAM 약 +349MB(FP16),
  질의 임베딩+검색 약 98ms/건. 운영 EC2(t3.small 2GB)에 모델을 직접 올리는 것은 부적합 판정이 이미 있다
  → 임베딩 서버는 앱과 분리된 별도 서비스로 두는 기존 설계를 유지한다.
- 앱 측 부하: 인메모리 인덱스는 768차원 float 기준 항목당 약 3KB + 오버헤드.
  현재 규모(항목 수백 건)에서는 수 MB 수준으로 무시 가능하다.
- 항목 생성·재색인은 관리자 수동 실행 배치라 운영 트래픽과 경합하지 않는다.

## 6. 스키마 변경 제안 (본 PR에서는 적용하지 않음)

- 없음. 본문 변경 감지는 dedup_key + 본문 직접 비교로 해결했고, 해시 컬럼이 필요해지는 규모
  (항목 수만 건 이상)가 되면 `content_hash` 컬럼 추가를 별도 마이그레이션으로 제안한다.
