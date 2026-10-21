# 입시상담 DB 구조 · seed-v3.2 정합성 분석 (읽기 전용, 2026-10-21)

- 대상 저장소: `woori73/feel2real-buildit` (`~/workspace/feel2real-buildit-src`)
- 기준 코드: 로컬 체크아웃 main `4cdc95a`(#828) + origin/main 추가분(#831·#832·#835·#837 등)은 `git show`로 대조. **로컬 체크아웃이 origin/main보다 뒤처져 있음** — admissions 관련 origin/main 추가: `AdmissionsActivationService` 신설, `AdmissionsAdminController`에 `POST /activate-all` 추가, `AdmissionsChatService`·`AdmissionsFactSearchService` 변경(+112줄).
- 운영 DB 실측은 하지 않았다(읽기 전용 코드·파일 분석만). 운영 건수는 기존 진단(0건) 기준.

---

## 과제 A. 입시 DB 구조

### A-1. 마이그레이션과 테이블 10개

입시 전용 마이그레이션은 **`V1078__add_admissions_tables.sql` 단 1개**뿐이다(다른 마이그레이션에 `admissions_` 참조 없음). 여기서 10개 테이블이 한 번에 생성된다.

| # | 테이블 | 역할 | 주요 컬럼 | 인덱스 · FK |
|---|---|---|---|---|
| 1 | `admissions_universities` | 대학 | `name`(UK), `region`, `type`, `homepage_url` | UK(name), idx(region) |
| 2 | `admissions_official_materials` | 공식자료(모집요강 원문 메타) | `university_id` FK, `academic_year`, `title`, `issuer`, `publish_date`, `document_url`, `file_key`, `status`(기본 DRAFT) | idx(university_id, academic_year), idx(academic_year) |
| 3 | `admissions_departments` | 모집단위(학과) | `university_id` FK, `academic_year`, `name`, `category`, `quota_info`(TEXT, 전형별 인원 JSON), `source_id` FK→materials, `status` | idx(univ, year), idx(name) |
| 4 | `admissions_types` | 전형 | `university_id` FK, `academic_year`, `name`, `category`, `details`(TEXT: 모집인원/자격/전형방법/수능최저), `source_id` FK, `status` | idx(univ, year), idx(name) |
| 5 | `admissions_schedules` | 일정 | `university_id` FK, `academic_year`, `title`, `start_date`, `end_date`, `note`(TEXT, 날짜 원문), `source_id` FK, `status` | idx(univ, year), idx(start_date, end_date) |
| 6 | `admissions_knowledge_items` | 상담 지식(핵심 RAG 원천) | `academic_year`, `year_confirmed`, `title`, `content`(LONGTEXT), `category`, `tags`, `source_class`, `source_id` FK→materials, `source_url`, `status`, `role`(FACT/GUIDE), `verification_note`, `dedup_key`(UK), `version` | UK(dedup_key), idx(status, source_class), idx(year, year_confirmed), idx(category) |
| 7 | `admissions_knowledge_embeddings` | **임베딩 벡터 저장** | `knowledge_id` FK(ON DELETE CASCADE), `model_version`, `dimension`(기본 768), `vector`(LONGBLOB) | UK(knowledge_id, model_version), idx(knowledge_id) |
| 8 | `admissions_conflicts` | 충돌대장 | `academic_year`, `title`, `description`, `related_knowledge_ids`, `status`(OPEN 등), `resolution_note` | idx(status), idx(year) |
| 9 | `admissions_youtube_analyses` | 유튜브 분석(의견, 사실 근거 사용 금지) | `video_title`, `video_url`, `channel`, `analysis_summary`, `related_knowledge_id` FK, `reliability` | idx(related_knowledge_id) |
| 10 | `admissions_counseling_sessions` | 상담 이력(세션 토큰, 개인정보 저장 금지) | `session_token`, `question`, `question_fingerprint`, `answer`, `evidence_summary`, `student_profile`, `academic_year` | idx(token), idx(fingerprint), idx(created_at) |

상태값(전 테이블 공통 관례): `DRAFT → REVIEW → ACTIVE (+ARCHIVED)`. ACTIVE만 검색 대상.

### A-2. 도메인 코드와 현재 검색 방식

구성: 엔티티 10 · 리포지토리 10 · 서비스 9 · 컨트롤러 5 (`domain/admissions/`).

**검색은 2개 층이다.**

1. **사실 검색 — `AdmissionsFactSearchService`** (학과·전형·일정)
   - 구조화 행(departments/types/schedules)을 전수 로드해 **메모리에서 필터**: ACTIVE + 출처 자료 ACTIVE·동일 학년도 + 학년도 집합(미지정 시 2027) + 대학 선택.
   - 매칭은 문자열 규칙: 학과명 멘션/핵심어 부분일치, 전형명 조각 포함 여부, 일정은 `SCHEDULE_TERMS` 교집합. **벡터 검색 없음.**
   - 모집인원은 `quota_info` JSON을 파싱해 근거 문장으로 변환.
2. **지식 검색 — `AdmissionsResearchService`** (지식 항목, STEP1~5 사다리)
   - **하이브리드 이미 구현**: `app.admissions.embedding.enabled`(기본 true)이고 인메모리 인덱스가 준비되면 LIKE+벡터 결합 점수(LIKE `likeWeight` 0.4 / 벡터 `vectorWeight` 0.6 / 공식 보너스 0.1, `AdmissionsEmbeddingProperties`), 아니면 **LIKE 폴백** (제목·본문·태그·카테고리, 동의어 확장·조사 제거 키워드 추출).
   - 필터: ACTIVE + OFFICIAL + FACT 역할 + `year_confirmed`(STEP1 2027 확정 / STEP2 2028 계획 분리) + 학년도.
   - 벡터 인덱스 `AdmissionsVectorIndex`: 기동 시 `admissions_knowledge_embeddings`를 전수 로드해 **인메모리 코사인 브루트포스** (NewStart `KnowledgeVectorIndex` 패턴, 입시 전용 별도 인스턴스·별도 테이블 — NewStart와 혼합 없음). 관리자 `POST /api/admin/admissions/index/reload`로 재로드.
   - 쿼리 임베딩 `AdmissionsEmbeddingClient`: 외부 임베딩 서비스 `POST {serviceUrl}/embed` (설정 `app.admissions.embedding.service-url`, 모델 버전 기본 `embeddinggemma-2`, 768차원). URL 미설정·실패 시 null → LIKE 폴백.

**적재·승인 도구**
- `AdmissionsDataLoader.loadSeed`: 시드 JSON(`ipsi_universities/documents/departments/admission_types/schedules`) 적재. 전부 DRAFT 시작, 재실행 중복 방지(대학명/자료 제목/대학+학년도+학과·전형명/대학+학년도+일정 제목+날짜 자연키), 학년도 2027·2028 외 행은 skip, 구조 오류 시 전체 롤백. `loadKnowledgeBase`는 `question_types`만 2027·GUIDE로 고정 적재.
- 관리자 API `AdmissionsAdminController` (`/api/admin/admissions/**`, ADMIN): 지식·자료 목록/상세, `POST /knowledge/{id}/review`·`/materials/{id}/review` (review→approve 2단계), `POST /load/seed`, `POST /load/knowledge-base`, `POST /index/reload`, `GET /stats`.
- **PR #837 (MERGED, origin/main)**: 가이드 페이지 ADMIN 패널에 적재 도구 UI (`admissions-admin-load.js`) — 시드 파일 선택 → 브라우저에서 건수·참조 무결성 미리보기 → 확인 후 `/load/seed` 실행, **학년도 전체 승격**(`AdmissionsActivationService.activateAll`, 확인 문자열 `ACTIVATE-ALL-<학년도>` 필요, DRAFT·REVIEW만, 한 트랜잭션), 인덱스 갱신 버튼. origin/main의 `POST /api/admin/admissions/activate-all`이 유일한 전형·일정·학과 승격 경로다.
- 공개 API 플래그: `AdmissionsChatController`의 `app.admissions.public-api.enabled` (기본 **false**; false면 503).

### A-3. 임베딩 저장 구조 — 있는 것과 없는 것

**이미 있음 (신규 테이블 불필요)**
- 벡터 저장: `admissions_knowledge_embeddings` — 지식 항목 1건당 모델 버전별 1벡터, 768차원 LONGBLOB(little-endian float32), UK(knowledge_id, model_version), 지식 삭제 시 CASCADE.
- 인메모리 인덱스·하이브리드 결합·재로드 API까지 구현 완료. 입시 전용 네임스페이스 분리는 테이블·인덱스 인스턴스 단위로 이미 달성.

**없음 — 구축이 필요한 부분**
1. **임베딩 생성(쓰기) 경로가 코드에 없다.** `AdmissionsKnowledgeEmbedding`을 save하는 코드, `floatsToBytes` 호출처가 main 코드에 없다(검색 측 읽기 전용). 즉 지식 항목이 ACTIVE가 돼도 벡터를 만드는 적재 파이프라인(청킹→임베딩 서비스 호출→INSERT)이 부재하다. 운영에서 인덱스가 비어 있는 구조적 이유.
2. **청크 분할 없음.** 벡터 단위 = 지식 항목 전체(1항목 1벡터). 공식자료 원문을 청크로 나누는 구조가 필요하면 `knowledge_id` 하위 청크 식별자(예: chunk_index 컬럼 또는 별도 청크 테이블)가 추가로 필요하다. 현재 지식 항목 본문이 짧은 단위(전형·일정 행 수준)로 적재되는 동안은 항목 단위 벡터로 충분할 수 있다 — **판단은 미확인(실제 항목 본문 길이 분포는 운영 적재 후 확인 필요)**.
3. **사실 행(학과·전형·일정)에는 벡터가 없다.** `AdmissionsFactSearchService`는 규칙 기반 매칭만 사용. 지시서의 "학과별 모집요강·수능최저 검색" 품질은 현재 키워드/조각 매칭에 의존한다.
4. 재색인 단위: 항목 변경 시 해당 항목 벡터만 재생성하는 경로도 생성 파이프라인과 함께 신설 필요(인덱스 reload 자체는 존재).

---

## 과제 B. seed-v3.2-ops.json 정합성 검사

대상: PR #836 브랜치 `docs/admissions-converted-seed-20261021`, 경로 `docs/admissions/handover-20261021/converted/` (`seed-v3.2-ops.json` 183,624 bytes, `convert_seed.py`로 v3.1 계열 원본 + 추출본에서 변환).

### B-1. 수치 검사 결과 (python 로드)

| 항목 | 결과 |
|---|---|
| 최상위 섹션 | `ipsi_universities` 10 · `ipsi_documents` 20 · `ipsi_departments` **0** · `ipsi_admission_types` 116 · `ipsi_schedules` 72 |
| 대학 10 | SNU 서울대 · YON 연세대 · KOREA 고려대 · SKKU 성균관대 · HANYANG 한양대 · SOGANG 서강대 · CAU 중앙대 · KHU 경희대 · EWHA 이화여대 · HUFS 한국외대 |
| 문서 학년도 | 20건 전부 2027 |
| 문서 출처 유형 | official_pdf 14 · official_portal 5 · mirror_unverified 1 (중앙대 수시) |
| 문서 status 필드 | **없음 (20건 전부)** — 적재 시 로더 기본값 DRAFT로 시작하는 설계와 일치 |
| document_key 중복 | 0 |
| 문서 빈 필수값 | 제목·source_url·institution_name 누락 0 |
| 전형 116 | 전부 2027 · 미지 대학 코드 0 · source_document_key 불일치 0 · 빈 전형명 0 · `min_academic_standard`(수능최저) 필드 116건 전부 존재 |
| 일정 72 | 전부 2027 · 미지 코드 0 · 출처 불일치 0 · 빈 항목·빈 날짜 0 |
| 학과 0 | 모집단위 행이 없다 — 학과별 모집인원 근거는 전형 행의 `recruitment_count`로만 제공됨 (README 명시) |

### B-2. 캠퍼스 구분 (고려대)

- 대학 행은 **고려대학교 1개(KOREA)**뿐 — 서울/세종이 별도 대학 행으로 분리돼 있지 않다.
- 구분은 명칭 수준에서만 유지: 수시 문서명 `고려대학교(세종캠퍼스) 2027학년도 수시모집요강`, 세종 전형 7건에 `[세종캠퍼스]` 접두, 세종 일정 `type_name`에 `수시(세종캠퍼스)` 표기. 정시(일반전형·교과우수전형 6건)는 서울 본교.
- **알려진 한계 (KNOWN_ISSUES KI-1, 미해결)**: "고려대 서울캠퍼스" 질문에도 세종 전형·일정이 근거로 섞인다. 현 완화책은 표기+프롬프트 규칙(PR #835)뿐이며, 검색 단계 캠퍼스 필터는 미구현. 구조적 해법(자료/행에 캠퍼스 속성)은 별도 작업 필요.

### B-3. 필드 존재

- 문서: `document_key, document_name, institution_name, published_at, applicable_academic_year, source_type, source_url, verification_note` — 출처(source_url)·발행기관·검증 메모 포함. **원문 위치(쪽수)는 이 시드에 없다** — 근거 쪽수는 별도 파일 `evidence-pages.json`(시드 미포함, README §1 명시).
- 전형: `university_code, academic_year, type_name, eligibility, min_academic_standard, recruitment_count, student_record_method, source_document_key` — 지원자격·전형방법·수능최저·모집인원이 행 단위로 구조화됨. 단 **`type_category`가 없어** 로더 적재 시 전형 `category`가 null이 된다(로더는 `type_category`를 읽음).
- 일정: `university_code, academic_year, item, type_name, date, source_document_key` — 날짜는 `date` 텍스트(로더가 note로 적재, start/end_date 분리 없음).

### B-4. 동봉 문서 3종 요지

- `README.md`: 변환 관계(대학·자료 20 ← v3.1 / 전형·일정 ← 추출 13+3+5, 서강 정시 부분본 제외), 기존 API만으로는 전형·일정 승격 불가라는 H2 실검증 결과(자료만 ACTIVE 시 근거 0건), 관리자 수동 실행 절차(스냅샷→적재→자료 승인→승격 API→인덱스 reload→진단·6문항 검증).
- `KNOWN_ISSUES.md`: KI-1 고려대 캠퍼스 혼합(미해결), KI-2 수시·정시 근거 혼합(PR #835로 수정됨).
- `KNOWLEDGE2028_LOADER_DESIGN.md`: 2028 자료(changes2028 8항목)는 기존 knowledge-base 경로로 적재 불가(2027·GUIDE 고정). 신규 `POST /load/knowledge-items`(FACT·DRAFT 고정·dedup `y<학년도>_`·출처 필수) 설계안 — **미구현**.

### B-5. v3.1 시드와의 구조 차이

| 구분 | v3.1 (`draft-seed-2027-v3.1-20261021.json`) | v3.2-ops (PR #836) |
|---|---|---|
| 섹션 | 대학 10 · 문서 20 **만** | 동일 2개 + **전형 116 · 일정 72 · 학과 0 섹션 추가** |
| 대학·문서 필드 | 동일 집합 | 동일 (필드 추가/삭제 없음) |
| 내용 정정 | — | 서강 수시=논술가이드북 표기, 고려 수시=(세종캠퍼스) 표기, 중앙 수시=미러·내용 행 없음, 경희 정시 링크 기관 주소로·수능최저 "확인되지 않음(원문 p.12 대조 필요)" |
| 적재 가능성 | 문서만 적재돼 전형·일정 근거 0건 | 전형·일정까지 적재 가능 (승격은 #837 activate-all 필요) |

---

## 핵심 결론

1. **테이블 10개는 V1078 하나로 완결**돼 있고, 지시서의 전제(대학 0·자료 0)는 코드가 아니라 데이터 상태다.
2. **현재 검색은 이미 하이브리드 구조**다: 사실(학과·전형·일정)은 구조화 행 + 규칙 매칭, 지식 항목은 LIKE + 벡터(0.4/0.6) 결합·LIKE 폴백. 단, **임베딩 생성 파이프라인이 없어** 벡터 경로는 운영에서 빈 인덱스로 동작할 수밖에 없다 — 구축의 본체는 신규 스키마가 아니라 ① 지식 항목 생성/청킹 ② 임베딩 쓰기 경로 ③ 항목 단위 재색인이다.
3. **seed-v3.2-ops.json 정합성은 통과 수준**이다: 문서 20·전형 116·일정 72, 참조 무결성·필수값·중복 모두 이상 없음. 단 학과 행 0건, 고려대 캠퍼스는 명칭 표기 수준(검색 혼합 KI-1 미해결), 원문 쪽수는 시드 밖(evidence-pages.json)에 있다.
4. 임베딩 저장에 필요한 변경: **신규 테이블 불필요.** 필요 시 지식 항목 하위 청크 구조(컬럼 또는 청크 테이블)만 선택 사항이며, 우선은 항목 단위 임베딩 쓰기 경로 신설이 본체다.

## 미확인 항목

- 운영 RDS의 실제 적용 스키마·행 수 (운영 DB 직접 조회는 본 작업 범위 밖 — 기존 진단 0건에 의존)
- 임베딩 서비스(EmbeddingGemma 계열)의 운영 배포 위치·가동 여부 (`service-url` 운영값 미확인; 코드 기본값은 빈 문자열 → LIKE 폴백)
- 지식 항목 본문의 실제 길이 분포 (청크 필요성 판단 근거 — 운영 적재 후 확인 가능)
- origin/main 대비 로컬 체크아웃 지연으로, #838 이후 admissions 변경 유무는 미확인
