# EASY 자동수집: AI 비서 브라우저 자동화 설계

> 작성: 2026-10-17 Muse | 상태: 설계 검증 완료 (구현 미착수)
> 연결 모델: **muse-spark-1.3-contributor** (reasoning 모델, OpenAI 호환 API)
> 승인: 사용자 (2026-10-17)

## 1. 실측 결과

### 1.1 Muse Spark 1.3 contributor 함수 호출 지원

**결과: 지원 확인 (문서 기준)**

| 항목 | 내용 | 출처 |
|------|------|------|
| 함수 호출 | 지원 (`tools`·`tool_choice` 파라미터) | openrouter.ai FAQ |
| 병렬 호출 | 지원 (`parallel_tool_calls`) | shipwithmuse.live |
| 구조화 출력 | 지원 (JSON schema) | openrouter.ai FAQ |
| `tool_choice` | `"auto"`만 지원, 특정 함수 강제 불가 (400 반환) | shipwithmuse.live |

**제약:**
- `tool_choice: "required"` 또는 특정 함수 지정 불가 — `"auto"`만 사용
- AI가 함수 호출 여부를 자율 판단하므로, 프롬프트에서 명확히 유도 필요

### 1.2 사이트별 자동화 가능 범위·인증 제약

| 사이트 | 수집 대상 | 인증 방식 | 자동화 가능 범위 | 제약 |
|--------|-----------|-----------|------------------|------|
| 여신금융협회 | 카드매출 (승인·입금 내역) | 사업자등록번호 로그인 | 로그인 후 조회·엑셀 다운로드 | 공동인증서 필요 가능, 세션 유지 필요 |
| 홈택스 | 현금영수증 매출내역 | 공동인증서 (사업자) | 로그인 후 조회·엑셀 다운로드 | 공동인증서 필수, 보안 프로그램 필요 |
| 은행 | 입출금 내역 | 공동인증서/OTP | 로그인 후 조회·엑셀 다운로드 | 은행별 UI 상이, OTP 필요 |
| 카드사 | 지출 내역 | ID/PW + OTP | 로그인 후 조회·엑셀 다운로드 | 카드사별 UI 상이 |

**공통 제약:**
- 모든 사이트가 사용자 직접 로그인 필수 (자동 로그인 불가)
- 공동인증서가 필요한 사이트는 브라우저에 인증서 설치 필요
- 공식 무료 API 없음 (유료 제3자 API만 존재: 바로빌 등)
- **결론: Playwright 브라우저 자동화 + 사용자 직접 로그인이 유일한 무료 방식**

## 2. 아키텍처

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  대시보드    │     │  Muse Spark 1.3  │     │  Collection Worker   │
│  AI 채팅 UI  │────▶│  contributor     │────▶│  (별도 프로세스)      │
│  /dashboard  │     │  (판단·대화)      │     │  Playwright 실행     │
└─────────────┘     └──────────────────┘     └─────────────────────┘
                           │                          │
                           │ 함수 호출                 │ 사이트 접속
                           │ (tools/auto)             │ 파일 다운로드
                           ▼                          ▼
                    ┌──────────────────┐     ┌─────────────────────┐
                    │  EASY 장부 API    │◀────│  파일 스토리지        │
                    │  (중복검사·분류·  │     │  (암호화·임시 보관)   │
                    │   승인 후 반영)   │     └─────────────────────┘
                    └──────────────────┘
```

### 2.1 Playwright Worker 구조 (별도 프로세스)

**BuildIt 앱 내장 금지.** 별도 Worker로 분리하는 이유:
- 브라우저 실행은 메모리/CPU 소모가 큼 (EC2 t3.small 2GB 제약)
- 브라우저 크래시가 앱 서버에 영향 주지 않도록 격리
- 독립적인 배포·재시작 가능

```
collection-worker/
├── src/
│   ├── downloaders/
│   │   ├── credit_finance.py    # 여신금융협회
│   │   ├── hometax.py           # 홈택스
│   │   ├── bank.py              # 은행 (은행별 어댑터)
│   │   └── card.py              # 카드사 (카드사별 어댑터)
│   ├── session_manager.py       # 브라우저 세션 관리
│   └── api_server.py            # BuildIt 앱과 통신 (REST)
└── requirements.txt             # playwright
```

**통신:**
- BuildIt 앱 → Worker: `POST /jobs` (다운로드 작업 요청)
- Worker → BuildIt 앱: webhook 또는 폴링으로 상태 보고
- 작업 상태: `PENDING` → `WAITING_LOGIN` → `DOWNLOADING` → `COMPLETED`/`FAILED`

### 2.2 역할 분리

| 구성요소 | 담당 |
|----------|------|
| Muse Spark 1.3 contributor | 사용자 의도 파악·대화·함수 호출 결정 |
| Collection Worker | Playwright 실행·사이트 접속·파일 다운로드 |
| EASY 장부 API | 중복검사·거래분류·사용자 승인 후 장부 반영 |
| 사용자 | 각 사이트에 직접 로그인 (비밀번호·인증정보는 AI에 전달 금지) |

## 3. AI 함수 정의

```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "request_card_sales_download",
        "description": "여신금융협회에서 카드매출 내역 다운로드를 요청한다. 사용자에게 로그인 안내가 표시된다.",
        "parameters": {
          "type": "object",
          "properties": {
            "business_id": {"type": "string", "description": "사업자등록번호"},
            "from_date": {"type": "string", "description": "시작일 (YYYY-MM-DD)"},
            "to_date": {"type": "string", "description": "종료일 (YYYY-MM-DD)"}
          },
          "required": ["business_id", "from_date", "to_date"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "request_cash_receipt_download",
        "description": "홈택스에서 현금영수증 매출내역 다운로드를 요청한다.",
        "parameters": {
          "type": "object",
          "properties": {
            "business_id": {"type": "string"},
            "from_date": {"type": "string"},
            "to_date": {"type": "string"}
          },
          "required": ["business_id", "from_date", "to_date"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "request_bank_statement_download",
        "description": "은행에서 입출금 내역 다운로드를 요청한다.",
        "parameters": {
          "type": "object",
          "properties": {
            "bank_code": {"type": "string", "description": "은행 코드"},
            "from_date": {"type": "string"},
            "to_date": {"type": "string"}
          },
          "required": ["bank_code", "from_date", "to_date"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "check_download_status",
        "description": "다운로드 작업 상태를 확인한다.",
        "parameters": {
          "type": "object",
          "properties": {
            "job_id": {"type": "string", "description": "작업 ID"}
          },
          "required": ["job_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "preview_import",
        "description": "다운로드된 파일의 장부 반영 미리보기를 조회한다 (중복검사·분류 결과 포함).",
        "parameters": {
          "type": "object",
          "properties": {
            "file_id": {"type": "string"},
            "business_id": {"type": "string"}
          },
          "required": ["file_id", "business_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "confirm_import",
        "description": "사용자 승인 후 다운로드된 자료를 EASY 장부에 반영한다.",
        "parameters": {
          "type": "object",
          "properties": {
            "file_id": {"type": "string"},
            "business_id": {"type": "string"},
            "approved_items": {"type": "array", "items": {"type": "string"}, "description": "승인된 항목 ID 목록"}
          },
          "required": ["file_id", "business_id", "approved_items"]
        }
      }
    }
  ],
  "tool_choice": "auto"
}
```

## 4. 동작 흐름

### 4.1 시나리오: "이번달 카드매출 가져와줘"

```
1. 사용자 (/dashboard AI 채팅): "이번달 카드매출 가져와줘"
        │
2. Muse Spark 1.3: 의도 파악 → request_card_sales_download 호출
        │  { business_id: "759-91-01977", from_date: "2026-10-01", to_date: "2026-10-31" }
        ▼
3. BuildIt 앱 → Collection Worker: POST /jobs
        │
4. Worker: Playwright로 여신금융협회 접속
        │
5. 시스템 → 사용자: "여신금융협회에 로그인해주세요" + 브라우저 화면 표시
        │
6. 사용자: 직접 로그인 (ID/PW·인증서는 Worker에만 전달, AI 모델에는 전달 안 됨)
        │
7. Worker: 매출 내역 엑셀 다운로드 → 암호화 저장 → 상태 COMPLETED
        │
8. Muse Spark 1.3: check_download_status → preview_import 호출
        │
9. 시스템 → 사용자: 중복검사·분류 결과 미리보기 표시 + 승인 요청
        │
10. 사용자: 승인 (전체 또는 선택)
        │
11. Muse Spark 1.3: confirm_import 호출 → EASY 장부 반영
        │
12. Muse Spark 1.3: "카드매출 00건을 장부에 반영했습니다" 응답
```

### 4.2 보안: 로그인 정보 격리

```
┌─────────────────────────────────────────────────┐
│  AI 모델 (Muse Spark 1.3)                        │
│  ─────────────────────────                      │
│  전달됨: business_id, 기간, job_id, file_id      │
│  전달 안 됨: 비밀번호, 공동인증서, OTP, 세션 쿠키  │
└─────────────────────────────────────────────────┘
                        │
                        ▼ (함수 호출만)
┌─────────────────────────────────────────────────┐
│  Collection Worker (별도 프로세스)                │
│  ────────────────────────────────                │
│  보유: 브라우저 세션 (메모리만, 디스크 저장 금지)   │
│  작업 종료 시 세션 파기                           │
└─────────────────────────────────────────────────┘
```

## 5. 중복검사·거래분류·승인 플로우

### 5.1 중복검사

| 검사 항목 | 방법 |
|-----------|------|
| 동일 파일 재업로드 | 파일 해시 (SHA-256) 비교 |
| 동일 거래 중복 | 승인번호 + 거래일자 + 금액 복합키 |
| 기간 중복 | 기존 수집 이력과 기간 겹침 검사 |

중복으로 판단된 항목은 미리보기에서 "중복" 표시, 기본 제외.

### 5.2 거래 분류

| 분류 | 규칙 |
|------|------|
| 수입 (카드매출) | 여신금융협회 승인내역 → 수입 |
| 수입 (현금영수증) | 홈택스 매출내역 → 수입 |
| 지출 | 카드사·은행 출금내역 → 지출 (가맹점명 기반 자동 분류 시도) |
| 미분류 | 자동 분류 실패 시 "미분류"로 표시, 사용자 수동 분류 |

### 5.3 사용자 승인

```
미리보기 화면:
┌─────────────────────────────────────┐
│ 수집 결과 미리보기                    │
│ 파일: card_sales_202610.xlsx         │
│ ─────────────────────────────────   │
│ ☑ 10/01 카드매출 150,000원 (수입)    │
│ ☑ 10/02 카드매출 200,000원 (수입)    │
│ ☐ 10/03 카드매출 150,000원 (중복)    │ ← 기본 제외
│ ☑ 10/05 카드매출 80,000원 (수입)     │
│ ─────────────────────────────────   │
│ [전체 선택] [선택 반영] [취소]        │
└─────────────────────────────────────┘
```

- 승인된 항목만 장부에 반영
- 미승인 항목은 보관 (나중에 승인 가능) 또는 삭제

## 6. 기존 화면 연동

### 6.1 진입점

| 화면 | 연동 방식 |
|------|-----------|
| `/dashboard` | AI 비서 채팅에서 자연어로 요청 |
| `/ledger` | "자동수집" 버튼 → 수집 이력·미리보기·승인 화면 |
| `/easy` | "자동수집" 버튼 (ADMIN 전용) |

### 6.2 테넌트 격리

- 모든 수집 작업은 `businessId` 기준
- TUTOR: `EasyTutorAccessService.requireAllowedBusiness()`로 검증
  - 본인 학원 연결 사업체만 수집 요청 가능
  - 타 학원 `businessId` 지정 시 403
- ADMIN: 전체 사업체 가능
- Worker도 `businessId`를 작업 단위로 격리 (파일 저장 경로 분리)

### 6.3 기존 수집 인프라 연동

기존 `EasyCollectionController` 활용:
- `POST /api/admin/easy/collection/connections` — 수집 연결 등록
- `POST /api/admin/easy/collection/connections/{id}/run` — 수집 실행
- `GET /api/admin/easy/collection/runs` — 실행 이력
- 신규: `source` 코드에 `CREDIT_FINANCE`·`HOMETAX`·`BANK`·`CARD` 추가

## 7. 단계별 구현 계획

### Phase 1: Worker 기반 구축 (예상 2주)
- [ ] Collection Worker 프로세스 구축 (Playwright + REST API)
- [ ] 여신금융협회 다운로더 구현
- [ ] 작업 상태 관리 (PENDING → WAITING_LOGIN → DOWNLOADING → COMPLETED)
- [ ] BuildIt 앱 ↔ Worker 통신

### Phase 2: AI 연동 (예상 1주)
- [ ] Muse Spark 1.3에 tools 등록
- [ ] 함수 호출 → Worker 라우팅
- [ ] `/dashboard` AI 채팅 UI 연동

### Phase 3: 중복검사·승인 (예상 1주)
- [ ] 파일 해시·거래 중복검사
- [ ] 미리보기·승인 UI (`/ledger` 내)
- [ ] EASY 장부 반영 API

### Phase 4: 확장 (예상 2주)
- [ ] 홈택스 현금영수증 다운로더
- [ ] 은행·카드사 다운로더 (주요 은행부터)
- [ ] 거래 자동 분류 고도화

## 8. 미결정 사항 (구현 전 확인 필요)

- [ ] Worker 실행 위치 (에네이 CONTAINER 활용 검토)
- [ ] 브라우저 화면 공유 방식 (사용자 PC 화면 vs 서버 스트리밍)
- [ ] 공동인증서가 필요한 사이트의 인증서 관리 방식
- [ ] 다운로드 파일 보관 기간·암호화 방식
- [ ] 대량 다운로드 시 타임아웃·재시도 정책
