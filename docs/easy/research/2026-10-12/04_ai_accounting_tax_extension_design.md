# AI 회계·세무 서비스 확장 설계안

## 1. 설계 원칙

AI는 판단 보조자이고 장부·세무행위의 최종 주체가 아니다.

1. 수집 원문은 변경하지 않는다.
2. AI 출력은 `제안` 상태로만 저장한다.
3. 분개·증빙 판정·신고자료 반영은 사용자가 명시 승인한다.
4. 승인 전후 값을 모두 감사로그로 남긴다.
5. 세법 설명은 공식 근거·시행일·조회일을 함께 표시한다.
6. 신고 제출, 외부 전송, 결제는 AI가 자동 실행하지 않는다.

## 2. 목표 흐름

```text
외부거래/수동입력
  → raw 보존·멱등성 검사
  → 규칙/과거이력 후보
  → AI 분류·증빙·이상 제안
  → 사용자 검토함
  → 승인/수정/거절
  → 균형 검증
  → 분개·원장 반영
  → 신고 사전검토
  → 사용자 또는 자격 있는 세무전문가의 최종 제출
```

현재 EASY의 `raw_record → import_row → transaction → journal → review/correction`을 그대로 사용한다. AI는 `import_row`와 `card_event` 사이에 제안 레이어로 끼운다.

## 3. 구성요소

### 3.1 규칙·이력 엔진

- 명시적 가맹점 매핑, 동일 거래처 과거 승인, 정규화 가맹점, 금액/방향 규칙을 먼저 실행한다.
- HIGH 신뢰 규칙도 자동 확정하지 않고 기본 선택값으로만 제공한다.
- 사용자의 수정은 사업체 범위 내 학습 데이터로 저장한다.

### 3.2 AI 분류 Agent

입력: 사업체 업종, 거래일, 금액, 방향, 거래처, 적요, 결제수단, 증빙 유형, 후보 계정, 과거 승인 예시.  
출력: 계정 후보 1~3개, 차변/대변 초안, 세무분류, 신뢰도, 이유, 필요한 추가 질문, 적용한 규칙/근거 ID.

금지 출력은 세율 확정, 가공 증빙, 신고 제출, 근거 없는 확정 분개다. 구조화 JSON schema를 강제하고 allowlist 계정 ID만 받는다.

### 3.3 반복거래 학습

- 사용자 승인 기록을 `business_id + normalized_counterparty + direction + amount_band + pay_method` 특징으로 축적한다.
- 개인/사업체 간 학습 데이터는 섞지 않는다.
- 동일 사업체 내 최소 승인 횟수와 충돌 없는 경우에만 HIGH 후보로 승격한다.
- 취소·정정 거래는 학습에서 제외하거나 원거래 연결을 상속한다.

### 3.4 증빙 누락 안내

- 비용인데 evidence가 없거나, 공급가/부가세 불일치, 카드/계좌 거래인데 증빙 연결이 없는 경우 review item을 만든다.
- 안내는 `왜 필요한가`, `가능한 증빙`, `사용자가 할 일`, `기한`으로 구성한다.
- 증빙 이미지 도입 시 OCR 텍스트와 원본 파일을 분리 저장하고 사용자가 OCR 결과를 확정해야 한다.

### 3.5 장부 오류·이상거래

규칙 기반 검사를 우선한다.

- 차변/대변 불균형
- 마감기간 전기
- 외부 ID/경제적 거래 중복
- 같은 날·금액·거래처 중복 의심
- 취소액이 원거래를 초과
- 개인성 지출 후보, 음수/반전 방향 이상
- 매출 채널 합계와 입금·수수료 불일치
- 급격한 월별 변화와 신고자료 불일치

AI는 규칙 결과를 설명하고 우선순위를 정하지만 오류를 자동 수정하지 않는다.

### 3.6 신고 사전검토 Agent

- 장부 집계와 독립 신고 reference를 비교하는 현재 `filingCheck`를 기반으로 한다.
- 부가세: 매출/매입 증빙 완전성, 공제 불가 후보, 중복, 과세/면세 구분, 기간귀속을 검사한다.
- 종소세: 사업별 손익, 비업무용 후보, 인건비/원천징수 연결, 감가상각·재고·결산 상태를 검사한다.
- 원천세: 지급내역↔원천징수 record↔지급명세서 대상 일치를 검사한다.
- 출력은 `PASS / REVIEW / BLOCK`과 근거 목록이며 신고파일을 자동 제출하지 않는다.

### 3.7 공식 근거 RAG

허용 소스는 국가법령정보센터, 국세청, 홈택스 공지/서식, 4대보험 기관의 공식 문서다. 각 근거는 다음 메타데이터를 저장한다.

- 문서명, 기관, URL
- 조문/서식/단락 식별자
- 시행일·게시일·수집일
- 적용 세목·귀속연도
- 원문 해시와 폐기/대체 상태

모델 답변은 근거가 없으면 `확인 필요`로 낮추고 세무사 상담 경로를 제시한다. 세무사법상 세무대리·서류작성·상담 범위에 대한 법률 검토가 선행되어야 한다. 근거: [세무사법 제2조·제20조](https://www.law.go.kr/LSW/lsInfoP.do?ancYnChk=0&chrClsCd=010202&efYd=20251223&lsiSeq=280355&urlMode=lsInfoP).

## 4. 신규 데이터 모델

| 테이블 | 핵심 필드 | 목적 |
|---|---|---|
| `easy_ai_suggestions` | business, subject_type/id, model, prompt_version, output_json, confidence, status | AI 제안 원본 |
| `easy_ai_decisions` | suggestion, decision, before/after, actor, decided_at | 승인·수정·거절 감사 |
| `easy_ai_features` | business, normalized key, feature hash, approved account, count | 반복거래 학습 |
| `easy_evidence_items` | transaction, file/object ref, type, OCR status/hash | 증빙 원본·OCR 분리 |
| `easy_rule_sources` | authority, URL, effective dates, content hash | 공식 근거 버전 |
| `easy_filing_reviews` | business, tax type, period, checks, status | 신고 사전검토 snapshot |

모든 테이블은 `business_id`를 필수로 하고, 외부에 보이는 ID는 추측 불가능한 UUID를 사용한다. 원문 금융정보와 모델 입력은 최소화·마스킹하며 별도 보존기간을 둔다.

## 5. API 경계

```text
POST /api/easy/businesses/{businessId}/suggestions/classify
GET  /api/easy/businesses/{businessId}/review-queue
POST /api/easy/businesses/{businessId}/suggestions/{id}/approve
POST /api/easy/businesses/{businessId}/suggestions/{id}/revise
POST /api/easy/businesses/{businessId}/suggestions/{id}/reject
POST /api/easy/businesses/{businessId}/filing-reviews
GET  /api/easy/businesses/{businessId}/filing-reviews/{id}
```

승인 API만 `transaction/journal`을 변경할 수 있다. 제안 생성 API에는 쓰기 권한을 주지 않는다. 서버는 URL의 businessId와 로그인 사용자의 membership을 다시 검증하며 request body의 소유자 값을 신뢰하지 않는다.

## 6. 승인 UX

검토 카드에는 다음을 한 화면에 보여 준다.

- 원거래와 증빙
- AI 추천 계정/세무분류/분개
- 추천 이유와 신뢰도
- 과거 동일 거래 승인 예시
- 공식 근거 링크
- `승인`, `수정 후 승인`, `보류`, `사업과 무관`, `중복`, `거절`

일괄승인은 동일 규칙·동일 사업체·동일 기간 범위에서만 허용하고 결과 건수를 명시한다. 마감기간, 신고 제출, 민감한 급여/주민번호 자료에는 일괄승인을 금지한다.

## 7. 품질평가

출시 전 골든셋을 사업체·업종·세목별로 구성한다.

- Top-1/Top-3 계정 추천 정확도
- 승인 그대로/수정 승인/거절 비율
- 미분류 감소율
- 중복·증빙 누락 precision/recall
- 잘못된 자동확정 0건(정책 지표)
- 설명 근거 URL 유효율과 시행일 일치율
- business 간 데이터 누출 0건
- 승인부터 분개까지 감사로그 완전성 100%

## 8. 운영·보안

- 모델 공급자에 주민번호, 계좌 전체번호, 카드 전체번호, 인증서/비밀번호를 보내지 않는다.
- 자격증명은 애플리케이션 DB에 평문 저장하지 않고 정식 OAuth/API 또는 별도 비밀관리·로컬 에이전트를 사용한다.
- 프롬프트·모델·룰 버전을 제안과 함께 고정한다.
- 외부 근거가 바뀌면 영향받는 신고 review를 무효화하고 재검토한다.
- AI 장애 시 규칙 기반 장부와 수동 승인 기능은 계속 동작해야 한다.


