# F2R 자체 LLM 작업 목록

작성일: 2026-11-08 · 계획: [PLAN.md](PLAN.md)

## 규칙
- 작업을 시작하기 전에 이 표의 `담당`과 `상태`를 PR로 먼저 고친다. 같은 파일을 두 쪽이 동시에 고치지 않기 위해서다.
- PR은 만든 쪽이 아닌 다른 쪽(클로드 ↔ GPT)이 검토한 뒤 병합한다. 병합·운영 배포는 대표님 승인 후에만 한다.
- 문항 검증의 재풀이는 그 문항을 생성하지 않은 쪽 모델이 한다.
- GPU 포인트 사용, 운영 배포, 대량 DB 변경은 실행 전에 대표님 확인을 받는다.
- 학생 개인정보와 채무상담 데이터는 학습 데이터에 넣지 않는다. 베이스 모델은 상업 이용 가능한 라이선스만 쓴다.

상태: `예정` · `진행` · `검토` · `완료` · `보류`

## 작업

| ID | 작업 | 담당 | 교차 검토 | 상태 | 대상 경로 |
|---|---|---|---|---|---|
| T00 | `docs/f2r-llm/`·`training/` 뼈대, AGENTS.md·CLAUDE.md 안내 | 클로드 | GPT | 검토 | `docs/f2r-llm/`, `training/` |
| T01 | 청킹·색인(BM25+임베딩), `context_chunks` 테이블 — PLAN 4-1 | 클로드 | GPT | 예정 | `problem-generator/wiki-backend-src/backend/chunk_index.py` |
| T02 | D 점수 계산 — PLAN 4-2, [DYNAMIC_DENSITY.md](DYNAMIC_DENSITY.md) | 클로드 | GPT | 예정 | `backend/density.py` |
| T03 | 하이브리드 검색(RRF + D 재정렬), MCP `retrieval_mode`·`top_k` — PLAN 4-3 | 클로드 | GPT | 예정 | `backend/retrieval.py`, `backend/mcp_server.py` |
| T04 | 모델 라우터(상용 AI / `llama-server`) — PLAN 4-4 | 클로드 | GPT | 예정 | `backend/model_router.py` |
| T05 | 검증 파이프라인(형식·근거 대조·재풀이·배포 게이트·샘플 오딧) — PLAN 4-5 | 클로드 | GPT | 예정 | `backend/verify_pipeline.py` |
| T06 | 클로드 vs v0 학교·시험별 비교 화면 | 클로드 | GPT | 예정 | `problem-generator/wiki-src` |
| T07 | 학습 데이터 내보내기(JSONL)·평가 세트 날짜 분리 — PLAN 4-6 | 클로드 | GPT | 예정 | `backend/dataset_export.py`, `training/export/` |
| T08 | 평가 하니스(지표 계산, 결과 JSON) — PLAN 4-6 | GPT | 클로드 | 완료 | `backend/eval_harness.py`, `training/eval/` |
| T09 | v0 생성 스크립트(베이스 모델 + 근거·출제 원칙, 학습 없음) | GPT | 클로드 | 완료 | `training/v0/` |
| T10 | QLoRA 학습 스크립트·설정 — PLAN 4-7 | GPT | 클로드 | 완료 | `training/qlora/` |
| T11 | LoRA 병합 → GGUF 4비트 변환 스크립트 — PLAN 4-7 | GPT | 클로드 | 완료 | `training/gguf/` |
| T12 | 베이스 모델 후보·라이선스 확인표 | GPT | 클로드 | 완료 | `training/README.md` |

## 진행 순서 (협업환경 문서 5장)
1. T00 병합.
2. GPT(Codex)가 GPT 담당 작업(T08~T12)을 맡는다.
3. 압구정중2 기말: 클로드 생성 → v0 생성(런유어AI) → 공통 검증 → 비교. 런유어AI 대여 전 예상 시간·포인트를 대표님께 확인받는다.
4. 12월: 검수 데이터로 v1 학습 계획 확정(목표 1,500쌍 이상).

## 데이터 준비 상태 (2026-11-08 기준)

| 학교·학년 | 기말 자료 | 비고 |
|---|---|---|
| 압구정중2 | 기출 텍스트·사진, 분석 1건, 학교 프린트 2건, 디지털 트윈 | 바로 진행 가능 |
| 신구중2 | 디지털 트윈, 기출 사진 8장(본문 텍스트 없음) | 사진 전사, 기말 학교 프린트 필요 |
| 압구정중1 | 없음 | 자료 업로드 필요 |
| 신구중1 | 없음 | 자료 업로드 필요 |

## GPT 작업 기록
- 2026-11-08: T08~T12 구현 시작. T00(PR #1007) 미병합 상태이므로 해당 문서 브랜치를 기반으로 후속 Draft PR을 준비합니다. 병합·운영 배포·GPU 실행은 하지 않습니다.


- 2026-11-08: PR #1008에 T08~T12 코드·파일 계약·실행 안내·라이선스 확인표를 구현했습니다. 합성 데이터 기반 단위/CLI/HTTP 테스트 29개 통과, Python 컴파일 검사 통과. CUDA 학습·실제 모델 생성·실제 GGUF 변환은 미실행이며 클로드 교차 검토 및 대표님 승인 대기입니다.
- 2026-11-10 (클로드 교차 검토): 평가 task를 generate(교사 검수 `review.answer_key_correct`로 채점, 미검수는 `unreviewed_generated_count`)/solve(gold 비교)로 분리, v0 응답 오류(truncated·invalid_json·missing_question)는 오류행으로 남기고 계속 진행, GGUF는 bf16 병합·BF16 변환 후 Q4_K_M. 테스트 34개 통과. T08~T12 도구 코드는 완료로 두고, 실제 GPU 학습·생성·변환은 대표님 승인 후 별도로 실행합니다.
