# 지피지기(NewStart) 임베딩·검색 시스템 전수 분석

- 조사일: 2026-10-21 (읽기 전용, 코드 수정 없음)
- 대상 저장소: `~/workspace/feel2real-buildit-src` (woori73/feel2real-buildit, main)
- 근거 표기: 파일 경로·클래스명·설정 키 기준. 비밀값은 기록하지 않음.

## 핵심 결론 (요약)

| 항목 | 결론 |
|---|---|
| 실제 모델 | **임베딩 전용 모델 `google/embeddinggemma-2`** (생성형 Gemma 아님). modelVersion 문자열 `embeddinggemma-2` |
| 차원 | **768** (float32, L2 정규화; Matryoshka로 축소 가능하나 운영 설정은 768 고정) |
| 호출 방식 | 앱 → **별도 임베딩 HTTP 서버** `POST {serviceUrl}/embed`, 본문 `{"texts":[...]}`, 응답 `{"embeddings":[[...]]}`. 인증 헤더 없음 |
| 벡터 저장 | MySQL 테이블 `newstart_knowledge_embedding` (BLOB, float32 little-endian 직렬화), 지식 1건당 벡터 1개 |
| 검색 | 앱 내 **인메모리 브루트포스 코사인**(내적) + **하이브리드**(LIKE 0.3 / 벡터 0.7 / 공식 가산 0.1). 리랭커 없음 |
| 청킹 | **없음** — 문서 단위 단일 임베딩 (테스트 프레임워크 기준 제목+요약+본문 앞 6,000자) |
| ⚠️ 운영 상태 | **코드는 배포됐으나 운영 미적재·미활성**: 마지막 실측(2026-10-09, 2026-10-16 재확인)에서 임베딩 테이블 **0건**, 질의 임베딩 서버 **미구축**, `enabled=false` → 운영 검색은 **LIKE 전용**. 그 이후 상태는 미확인 |

---

## 1. 임베딩 코드 위치

### 1.1 지피지기 본체 (`domain/newstartknowledge`, `domain/newstart/jipijigi`)

| 역할 | 파일 |
|---|---|
| 임베딩 HTTP 클라이언트 | `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/newstartknowledge/service/EmbeddingClient.java` |
| 인메모리 벡터 인덱스 | `.../domain/newstartknowledge/service/KnowledgeVectorIndex.java` |
| 설정 프로퍼티 | `.../domain/newstartknowledge/service/NewstartEmbeddingProperties.java` |
| 벡터 엔티티 | `.../domain/newstartknowledge/entity/NewstartKnowledgeEmbedding.java` |
| 벡터 리포지토리 | `.../domain/newstartknowledge/repository/NewstartKnowledgeEmbeddingRepository.java` (plain JpaRepository) |
| 하이브리드 검색 소비처 | `.../domain/newstart/jipijigi/service/JipijigiResearchService.java` (`searchActiveKnowledge`, `searchActiveKnowledgeHybrid`, `searchActiveKnowledgeLike`) |
| LIKE 전용 검색(폴백·기타 경로) | `.../domain/newstartknowledge/service/NewstartKnowledgeRetrievalService.java` |
| 지식 원본 엔티티 | `.../domain/newstartknowledge/entity/NewstartKnowledgeItem.java` (테이블 `newstart_knowledge_items`, V985) |
| 벡터 테이블 마이그레이션 | `src/main/resources/db/migration/V1051__newstart_knowledge_embedding.sql` |
| 단위 테스트 | `src/test/java/.../domain/newstartknowledge/service/KnowledgeVectorIndexTest.java`, `.../jipijigi/service/JipijigiResearchServiceTest.java` |

### 1.2 모델 서버·테스트 프레임워크 (앱 저장소 밖, 워크스페이스)

| 역할 | 위치 |
|---|---|
| 모델 래퍼 (transformers 직접 로드) | `~/workspace/newstart-rag/test-env/embedding_wrapper.py` (`EmbeddingGemma2Wrapper`) |
| LIKE/벡터/하이브리드 비교 프레임워크 | `~/workspace/newstart-rag/test-env/rag_compare.py`, `run_comparison.py`, `build_phase1.py`, `run_phase1.py` |
| 모델 파일 | `~/workspace/newstart-rag/test-env/models/embeddinggemma-2/` (SentenceTransformers 형식, `config.json`의 architectures=`EmbeddingGemma2Model`) |
| 사전 조사·테스트 보고서 | `~/workspace/newstart-rag/embeddinggemma2_conflict_investigation_20261009.md`, `embeddinggemma2_test_report_20261009.md` |

### 1.3 입시 측 병렬 복사본 (기존 존재 — 참고)

`domain/admissions/service/AdmissionsEmbeddingClient.java`, `AdmissionsVectorIndex.java`, `domain/admissions/config/AdmissionsEmbeddingProperties.java`, `domain/admissions/entity/AdmissionsKnowledgeEmbedding.java` — 지피지기 패턴을 입시 전용 설정(prefix `app.admissions.embedding`)으로 복제한 구조가 이미 main에 있다. 단, 입시 측도 적재·검증은 미실행 상태(2026-10-16 기록).

---

## 2. 실제 사용 모델

- **모델**: `google/embeddinggemma-2` (HuggingFace). 텍스트 전용 270M 파라미터 계열(전체 멀티모달 740M), Apache 2.0, 컨텍스트 8,192 토큰, 100+ 언어.
  - 근거: `~/workspace/newstart-rag/embeddinggemma2_conflict_investigation_20261009.md` §2 스펙표, `embeddinggemma2_test_report_20261009.md` §1.1.
- **임베딩 전용 모델이며 생성 모델이 아니다.** 텍스트를 넣으면 768차원 벡터만 반환한다. 답변 생성용 LLM(Gemma 생성형)과 무관하다.
  - 코드 근거: `EmbeddingClient.embed()`는 `float[]`만 반환하고 생성 파라미터가 전혀 없음. 모델 파일의 SentenceTransformers 설정(`config_sentence_transformers.json`)은 `similarity_fn_name: cosine`과 용도별 프롬프트(Document=`title: none | text: `, query=`task: search result | query: `)만 정의.
- **제공 방식**: 앱 내장 라이브러리가 아니라 **별도 HTTP 서버**. Java 앱은 `java.net.http.HttpClient`로 호출만 한다 (`EmbeddingClient.java:48-87`).
  - 엔드포인트: `POST {serviceUrl}/embed`
  - 요청: `{"texts": ["..."]}` (Java 클라이언트는 1건씩 전송)
  - 응답: `{"embeddings": [[...768개...]]}`, 차원이 설정값과 다르면 실패(null) 처리
  - **인증 헤더 없음** (Authorization 미전송) — 같은 호스트/내부망 전제.
- **설정 키** (`NewstartEmbeddingProperties`, prefix `newstart.embedding`):
  - `enabled` (기본 **false**), `service-url` (기본 `""`, 주석 예시 `http://127.0.0.1:8001`), `timeout-ms` (기본 5000), `like-weight` (0.3), `vector-weight` (0.7), `official-bonus` (0.1), `vector-limit` (20), `model-version` (기본 `embeddinggemma-2`), `dimension` (기본 768)
  - `application.yaml`에는 이 키들이 **없다** → 운영값은 환경변수(relaxed binding, 예: `NEWSTART_EMBEDDING_*`)로 주입되는 구조. 운영 실제값은 이 저장소에서 확인 불가 (**미확인**; 마지막 실측 기록은 enabled=false).

---

## 3. 벡터 저장

- **테이블**: `newstart_knowledge_embedding` (V1051, InnoDB)
  - `knowledge_id` BIGINT PK — `newstart_knowledge_items.knowledge_id` FK (지식 1건 ↔ 벡터 1건, 1:1)
  - `embedding` **BLOB NOT NULL**
  - `model_version` VARCHAR(50), 기본 `embeddinggemma-2`, 인덱스 `idx_nke_model`
  - `updated_at` DATETIME (자동 갱신)
- **차원**: 768.
- **직렬화**: float32 × 768 = 3,072바이트, **little-endian**, **L2 정규화된 벡터**를 그대로 BLOB 저장. 역직렬화는 `KnowledgeVectorIndex.bytesToFloats()` — 길이가 `dim*4`와 다르면 무효 처리(null).
- **별도 벡터 DB 없음** (pgvector·Milvus·FAISS 서버 등 미사용). DB는 보관만 하고, 실제 검색은 앱 메모리가 담당(§5).
- 규모 근거: 인덱스 클래스 주석 "4,722건 × 768차원 ≈ 14MB" — 지식 총량 약 4.7천 건 기준 설계.

---

## 4. 청크 분할

- **청킹 없음.** 지식 문서 1건 = 임베딩 1개 (문서 단위). 청크 테이블·청크 컬럼·오버랩 개념이 스키마와 코드 어디에도 없다.
  - 근거: `newstart_knowledge_embedding`의 PK가 `knowledge_id` 단일 키; `KnowledgeVectorIndex`도 knowledgeId→벡터 맵.
- **임베딩 입력 텍스트 구성** (운영 적재가 실행된 적이 없어, 검증된 테스트 프레임워크 기준):
  - `rag_compare.py`의 `Document.search_text()`: **제목 + 요약 + 본문 앞 6,000자**를 개행으로 연결. 본문 전문이 아니라 앞부분만 (모델 8K 토큰 제한 대응).
  - 토크나이저 절단: `embedding_wrapper.py`에서 `truncation=True, max_length=8192`.
- **메타데이터 보존**: 벡터 자체에는 메타데이터가 없다. 출처·유효기간·상태는 원본 행(`newstart_knowledge_items`: source_type, category, tags, source_url, source_date, effective_from/to, status)에 있고, 검색 후처리에서 원본 행을 다시 조회해 필터·표시에 쓴다. 즉 "벡터=유사도 전용, 메타데이터=원본 테이블" 분리 구조.

---

## 5. 검색

소비처: `JipijigiResearchService.searchActiveKnowledge()` (지피지기 조사 STEP 1).

### 5.1 분기 (안전장치)

```
if (embedding.enabled && vectorIndex.isReady()) → 하이브리드 시도
    하이브리드 결과가 비면 → LIKE 폴백
아니면 → LIKE 전용
임베딩 호출 실패(embed()=null) → 하이브리드 빈 결과 → LIKE 폴백
```

### 5.2 벡터 검색 (`KnowledgeVectorIndex.search`)

- 방식: **브루트포스 전수 코사인**. 벡터가 L2 정규화돼 있어 **내적 = 코사인 유사도**로 계산 (`dotProduct`).
- 인덱스: 앱 기동 시(`@PostConstruct`) DB 전량을 메모리에 로드한 `ConcurrentHashMap<Long, float[]>`. 로드 시 `model_version`이 설정값과 같은 행만, 차원 검사를 통과한 행만 적재.
- 상위 K: `vectorLimit` 기본 **20**건까지 후보로 사용.
- 유사도 임계값: **없음** (상위 K 절단만).

### 5.3 하이브리드 결합 (`searchActiveKnowledgeHybrid`)

- LIKE 후보 점수: 키워드 일치 수 — (제목·태그·카테고리 일치 ×2) + (본문 일치 ×1), 후보군 내 최대값으로 정규화.
- 벡터 점수: 코사인(-1~1)을 `(sim+1)/2`로 0~1 정규화.
- **최종 점수 = 0.3 × LIKE정규화 + 0.7 × 벡터정규화 + 공식 규정 가산 0.1 (OFFICIAL_RULE 분류 시) + 운영자 경험 가산 (OPERATOR_KNOWLEDGE 시 키워드 보너스×0.05)**
- 후보: LIKE 후보 ∪ 벡터 후보의 합집합을 원본 테이블에서 재조회한 뒤 점수 정렬.
- **필터 (후처리)**: `status = ACTIVE`, `effective_from ≤ 오늘 ≤ effective_to` (null 허용).
- 최종 반환: 단계당 상한 `PER_STEP_LIMIT = 8`건.
- **재정렬(reranker): 없음.** 코드 전수 검색에서 rerank 관련 코드 미발견. BGE Reranker는 검토·평가 대상이었으나 미구현·미실행 (2026-10-16 재확인 기록).

### 5.4 LIKE 검색 (기존·폴백)

- JPA Specification으로 `title/content/tags/category`에 키워드 OR-LIKE, `status=ACTIVE` 필수, 후보 창 160건(`CASE_CANDIDATE_WINDOW = 8×20`)을 키워드 일치 수로 재정렬해 8건 반환 (`JipijigiResearchService.searchActiveKnowledgeLike`, `NewstartKnowledgeRetrievalService`).

---

## 6. 재색인

- **앱 내 적재 코드 없음 (중요).** Java 코드 어디에도 `newstart_knowledge_embedding`에 쓰는 경로가 없다 — 리포지토리는 `KnowledgeVectorIndex`의 `findAll()` 읽기 전용으로만 사용된다. `EmbeddingClient.embed()`의 유일한 호출자는 질의 시점의 `JipijigiResearchService`뿐이다.
- **인덱스 갱신 트리거**: `KnowledgeVectorIndex.reload()` (DB 전량 재로드)가 있으나, 이를 호출하는 관리자 API·배치가 앱에 **없다**. 실제 호출 지점은 앱 기동 시 `@PostConstruct load()`뿐 → **사실상 재색인 = 앱 재시작**.
- **중복 방지 (설계상)**: PK가 `knowledge_id`라 외부 적재 시 upsert로 중복 불가. 모델 교체 시에는 `model_version` 값으로 세대 분리 (인덱스는 설정된 버전 행만 로드).
- **부분 재색인**: 앱 차원에서는 미지원 (전량 로드만). 외부 적재 도구가 행 단위로 넣으면 다음 재시작/재로드에 반영되는 구조.
- **실패 처리**:
  - 질의 임베딩 실패 → null → 하이브리드 빈 결과 → LIKE 폴백 (검색 자체는 항상 동작).
  - 차원 불일치 행 → 로드 시 건너뜀. 인덱스가 비면 `isReady()=false` → LIKE 전용.
  - 임베딩 서버 오류(HTTP≠200) → 로그 경고 후 null.
- **운영 적재 실행 여부**: 2026-10-09 마지막 실측에서 운영 테이블 **0건** — 즉 운영에서는 적재 파이프라인이 한 번도 돌지 않았다 (2026-10-16 재확인 기록과 일치). 그 이후 적재 여부는 **미확인**.

---

## 7. 실행 환경

- **모델 서버 위치 (계획·테스트 기준)**: 운영 앱과 분리된 별도 서비스. 테스트는 로컬 VM(`~/workspace/newstart-rag/test-env/`)에서 수행했고, 운영 EC2(t3.small, 2vCPU·RAM 약 2GB)에는 모델을 직접 올리지 않는 것이 조사 결론이었다 (`embeddinggemma2_conflict_investigation_20261009.md` §4). 운영 호스트의 systemd 서비스 목록에 embeddinggemma 관련 서비스가 인벤토리 대상인 것은 `.github/workflows/migration-infra-build.yml`의 점검 항목으로 확인되나, **운영 모델 서버의 실제 배포·기동 여부는 미확인** (2026-10-16 기록: "질의 임베딩 서비스는 미구축").
- **모델 로드 방식 (테스트 실측)**: `transformers`의 `AutoModel`+`AutoTokenizer` 직접 로드, `trust_remote_code=True`, mean pooling, L2 정규화. 기본 device=**CPU** (`embedding_wrapper.py:18`). 배치 크기 8.
- **사양 실측 (테스트 VM, FP16 로드)**: 모델 로드 증가분 약 **349MB RAM**, 질의 임베딩+검색 약 **98ms/건**, 하이브리드 평균 129ms. 양자화 시 모델 약 191MB (공식 스펙).
- **GPU**: 테스트 venv에 CUDA 빌드 torch가 포함돼 있으나, 래퍼 기본값은 CPU이고 실측도 CPU 기준이다. **운영 GPU 사용 근거 없음/미확인.** (GPUaaS는 영상 파이프라인용으로 별도이며 임베딩과 무관.)
- **앱 측 부하**: 인메모리 인덱스 약 14MB(4.7천 건 기준), 전수 내적 검색 10ms 이내(클래스 주석). 질의당 HTTP 1회 (타임아웃 기본 5초).

---

## 8. 입시상담 재사용 가능성

### 8.1 그대로 재사용 가능한 구성요소 (패턴·코드)

| 구성요소 | 재사용 형태 |
|---|---|
| `EmbeddingClient` (HTTP `/embed` 계약) | 동일 임베딩 서버를 공유하면 클라이언트는 설정만 바꿔 복제 가능. 실제로 입시 측 `AdmissionsEmbeddingClient`가 이미 이 방식으로 복제돼 있음 |
| `KnowledgeVectorIndex` (인메모리 코사인) | 입시 측 `AdmissionsVectorIndex`로 이미 복제됨. 수만 건 이하 규모에서 그대로 유효 |
| `NewstartEmbeddingProperties` 패턴 | 입시 측 `AdmissionsEmbeddingProperties` (prefix `app.admissions.embedding`)로 이미 분리. 가중치만 다름 (LIKE 0.4 / 벡터 0.6) |
| 벡터 테이블 설계 (V1051) | 입시 측 `admissions_knowledge_embeddings` 테이블이 이미 같은 설계로 존재 (2026-10-16 기록) |
| 하이브리드 점수식·폴백 구조 | `JipijigiResearchService.searchActiveKnowledgeHybrid`의 결합식(정규화 가중합+공식 가산)과 "실패 시 LIKE 폴백" 안전장치를 입시 `AdmissionsResearchService`가 참조하는 구조 |
| 테스트 프레임워크 | `~/workspace/newstart-rag/test-env/`의 래퍼·비교 도구(Recall@5·MRR 측정)를 입시 자료로 재실행 가능 |

### 8.2 입시용으로 반드시 분리해야 할 부분

- **네임스페이스/테이블**: 벡터는 반드시 입시 전용 테이블(`admissions_knowledge_embeddings`)에 분리 — 지피지기 테이블은 `newstart_knowledge_items` FK로 묶여 있어 물리적으로도 공유 불가.
- **인덱스 인스턴스**: 앱 내 인메모리 인덱스는 도메인별로 별도 빈이어야 한다 (이미 분리됨). 공유하면 지식 ID 충돌·필터 오염 발생.
- **적재 파이프라인 (신규 구축 필요)**: 지피지기에도 앱 내 적재 코드가 없으므로 "재사용"할 적재기가 존재하지 않는다. 입시 자료(문서 20건 규모)용 임베딩 적재는 **신규로 만들어야** 하며, 문서 텍스트 구성(제목+요약+본문 규칙)과 재색인 트리거(관리자 API 또는 배치)를 입시 쪽에서 정의해야 한다.
- **필터 체계**: 지피지기의 ACTIVE·유효기간 필터에 대응하는 입시 필터(학년도 2027/2028 분리, 대학·캠퍼스, 전형)는 입시 메타데이터 기준으로 별도 구현 필요. 벡터 자체에는 메타데이터가 없으므로 원본 행 후처리 필터로 구현하는 패턴은 동일하게 적용 가능.
- **청킹 정책**: 지피지기는 문서 단위 단일 임베딩이다. 입시 모집요강은 문서가 길고(수십~백쪽 PDF 추출본) 문항·전형 단위 근거 위치가 필요하므로, 문서 단위 방식을 그대로 쓰면 근거 위치 반환이 안 된다. **입시는 청크 단위 임베딩(청크 테이블 + 원문 위치 메타데이터)이 별도 설계로 필요** — 이 부분은 지피지기에 재사용할 선례가 없다.
- **활성화 안전장치**: `enabled` 기본값이 지피지기는 false, 입시 복사본은 true로 다르다. 입시에서 켜기 전 임베딩 서버 URL이 비어 있으면 질의마다 실패 후 폴백하므로, 적재·서버 확인 전에는 비활성 유지가 안전하다.

### 8.3 결합 지점 요약

- 임베딩 서버 1대를 두 도메인이 공유하는 것이 전제 (모델·차원·정규화가 같아야 벡터 공간이 호환됨). 서버가 없으면 양쪽 다 LIKE로만 동작한다.
- 앱 쪽 결합은 설정(prefix 분리)과 클라이언트 계약(`/embed`, `{"texts":[...]}`)뿐이며, 도메인 코드는 서로 독립이다.

---

## 미확인 항목

1. 운영 EC2의 `newstart.embedding.*` 실제 설정값 (enabled 여부·serviceUrl) — 저장소에 없고, 마지막 실측(2026-10-09)은 enabled=false·서버 미구축. 이후 변경 여부 미확인.
2. 운영 `newstart_knowledge_embedding`의 현재 행 수 — 마지막 실측 0건, 이후 적재 여부 미확인 (운영 DB 직접 조회 불가 환경).
3. 운영 임베딩 서버의 구현체(프레임워크·포트·systemd 유닛명) — 테스트 환경 구현(transformers 래퍼)만 확인됨. 운영 배포본 존재 자체가 미확인.
4. 운영 적재 시 문서 텍스트 구성 규칙 — 운영 적재 미실행이라 확정 규칙 없음. 테스트 프레임워크의 "제목+요약+본문 앞 6,000자"만 선례로 존재.
