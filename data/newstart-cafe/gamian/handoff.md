# 감면율 게시판 수집 및 구조화 완료 인수인계

## 기본 정보
- **카페**: 새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero)
- **게시판**: 감면율 (감면율과 확정금액 예측 게시판)
- **메뉴 ID**: 22
- **게시판 URL**: https://cafe.naver.com/f-e/cafes/31312978/menus/22?viewType=L

## 완료 현황 (2026-09-20 16:00 기준)
- **총 페이지 수**: 3
- **총 게시글 수**: 37
- **수집 완료**: 37 (100%)
- **수집 실패**: 0
- **구조화 완료**: 37 (100%)

## 수집 및 구조화 성과
- **원본 게시글**: 37개 (전체 3페이지)
- **댓글 수집**: 122개 (본문 댓글)
- **대댓글 수집**: 82개
- **매니저 예측**: 36건 (지피지기자산)
- **작성자 후속 결과**: 7건
- **실제 감면율 확인**: 2건 (5700: 75%, 5727: 74%)
- **예측 vs 실제 비교**: 2건 (5700: 예측 75-80% → 실제 75%, 5727: 예측 75-77% → 실제 74%)

## 산출물
- **원본 수집 데이터**: `data/newstart-cafe/gamian/articles/*.json` (37개)
- **구조화 데이터**: `data/newstart-cafe/gamian/structured/cases.json` / `cases.jsonl` (37건)
- **통계**: `data/newstart-cafe/gamian/structured/statistics.json`
- **인덱스**: `data/newstart-cafe/gamian/index.json`
- **상태 관리**: `collection_state.json`, `checkpoint.json`

## 구조화 데이터 스키마
각 케이스는 다음 필드 포함:
- article_id, boardName, title, author, createdAt, viewCount
- content_raw (원문), debt_*, asset_*, income_*, personal info
- predicted_reduction_rate, final_reduction_rate, temp_reduction_rate
- predicted_repayment_amount, final_repayment_amount
- repayment_period_months, grace_period_months, monthly_payment
- comments[] (role 분류: AUTHOR_RESULT, AUTHOR_FOLLOWUP, COMMUNITY_PREDICTION, etc.)
- manager_prediction, author_followup_result, prediction_vs_actual
- key_insights, actual_reduction_rate_from_followup

## 검증 결과
- 게시판 목록 37건 ↔ 수집 인덱스 37건: **MISSING = 0** ✓
- 37/37 게시글 수집 완료 ✓
- 37/37 구조화 완료 ✓
- 댓글 역할 분류: AUTHOR_RESULT 4, AUTHOR_FOLLOWUP 27, COMMUNITY_PREDICTION 39, COMMUNITY_OPINION 2
- 매니저 예측 추출: 36/37 (97%)

## 다음 작업 (AI 활용 단계)
1. 구조화 데이터 기반 유사 사례 검색 인덱스 구축
2. 사용자 조건 필터링 → 유사 사례 검색 → 근거 제시 파이프라인 구현
3. 감면율/확정금액 예측 모델 학습 데이터로 활용

## 주의사항
- 표본 37건에 불과함 → 전체 새출발기금 이용자 통계로 일반화 금지
- 원본 수집 데이터는 `articles/` 폴더에 보존됨 (수정 금지)
- 개인정보 포함 가능성 → 활용 시 마스킹 필수
- AI가 사례만으로 감면율/확정금액을 보장/단정하지 않도록 프롬프트 설계 필요

---
*완료 일시: 2026-09-20 16:00*
*작업자: NewStart 감면율 게시판 수집/구조화 자동화*