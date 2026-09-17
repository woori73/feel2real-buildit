# Easy AI 자동장부 아키텍처 (V975)

> 목표: 사용자가 복식장부를 직접 쓰는 프로그램이 아니라
> AI가 장부를 정리하고 예외만 확인받는 시스템.
> 이번 문서는 전체 흐름과 단계별 구현 상태를 고정한다.

## 전체 흐름

```text
외부 거래 source (은행/카드/영수증/수동 — 수집은 staging으로만)
↓
raw/staging (easy_import_batches/rows — 확정 전에는 장부 아님)
↓
중복검사 (fingerprint 의심 탐지 — 자동합치기 없음)
↓
자동분류 (easy_classification_rules — rule 우선, 매칭 없으면 미분류)
↓
AI Audit (easy_review_items — "확인 필요"만, 판정 아님)
↓
사용자 예외 확인 (resolve — 사업장·계정·증빙을 장부에 반영)
↓
확정 장부 (easy_transactions — 반드시 사업체 연결)
↓
계정별 집계 (easy_accounts 기준)
↓
BS/PL (향후 — 복식분개 테이블 추가 시)
↓
연간결산 (easy_annual_closings — 사업장별, DB 합치지 않음)
↓
개인 종합소득세 준비 (귀속연도별 사업소득 합산 조회 — 신고서 아님)
```

## 구현 상태 구분

### 현재 구현 (V973/V974, PR #292/#293)

- 학원/출판사 business 분리 + 전체 합산 dashboard
- 거래 CRUD, 학생 원장, 수납/미수 관리
- easy_accounts (잠정 12종) + mappings + classification rules
- import staging → confirm/discard, 규칙 자동분류, CSV export
- supply/vat/evidence 필드 (세율 자동계산 없음)

### 이번 PR 구현 (V975)

- 경영 대시보드: 올해 1/1~오늘 vs 작년 동기 (매출/비용/손익/증감률/미수금/학생 수)
- `easy_review_items`: AI가 확인할 장부 저장 큐
  (UNCLASSIFIED/EVIDENCE/BUSINESS/WITHHOLDING/DUPLICATE_SUSPECT/IMPORT_UNCONFIRMED)
- `rebuild`: 장부 스캔 → 확인 필요 재생성 (멱등, 처리済 항목 보존)
- `resolve`: 사업장·계정·증빙 상태를 장부에 반영 (import 확정은 기존 staging API 재사용)
- 거래 출처 추적: `source_kind`/`source_ref`/`fingerprint` (V975부터 생성분에 기록)
- 증빙 검토 상태 `evidence_review` (UNKNOWN/NEEDS_REVIEW/VERIFIED/MISSING/NOT_REQUIRED)
- 원천징수 최소 기반: `withholding_review` + `easy_withholding_records`
  (지급대상/총액/실지급액/원천징수액/지급일/관련거래/신고·명세서 확인)
- 연도 마감 `easy_annual_closings` (OPEN→REVIEW→CLOSED, 강제 마감 사유 기록, 재개방 가능)
- 경영 브리핑: DB 숫자로 계산 가능한 문장만 (`briefing` — LLM 입력용 계약)
- 종소세 예상: placeholder 계약만 (`PENDING_REAL_DATA`, 가짜 세액 없음)

### Claude 실제 이지샵 자료 확보 후 구현

- 실제 이지샵 CSV/Excel 형식에 맞는 importer (형식 추측 금지 — 자료 먼저)
- 내부 계정 확정 (현재 12종은 잠정치) + NTS 표준계정 매핑 확정
- 중복 판정 규칙 고도화 (카드매출→입금 reconciliation 등)
- BS/PL 생성 (복식분개 테이블 추가 — 단식 v1 구조는 유지)
- 종소세 예상 계산 로직 (실제 신고서 구조 확인 후)
- LLM 자연어 브리핑 (`briefing` 목록을 입력으로)

### 향후 금융기관 연동 (이번 범위 밖)

- CODEF 등 외부 수집은 조사 대상으로만 본다
- 카드사/은행 scraping, credential·인증서 저장, 금융 로그인 자동화는 하지 않는다
- HomeTax/VAT 자동신고는 하지 않는다 (세무사 전달 CSV까지만)

## 판단 금지 규칙

- 금액만으로 "비용 인정/불인정", "가산세"를 판정하지 않는다
- AI는 세무판정을 확정하지 않는다 — "확인 필요" + 사용자 답변의 장부 반영이 전부다
- 중복 의심은 목록에만 올린다 — 자동합치기 금지
- 확인되지 않은 세율·표준계정 코드 하드코딩 금지

## 참조 (코드 복사 없음)

- kimlawtech/korean-jangbu-for (Apache-2.0): 거래 표준화, source/source_ref,
  중복 제거 식별자, rule 우선·사용자 확인 구조, 내부↔표준계정 이중 구조 발상
- aivance-kr/AICassa: 공급/부가 분리·증빙유형 발상 (V974 반영)
- sebae1/SBS: 적격증빙 분류 발상 (V974 반영)
- 상세 조사: `research/korean-accounting-oss` 브랜치의
  `docs/easy/KOREAN_ACCOUNTING_OPEN_SOURCE_RESEARCH.md`
