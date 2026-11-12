# 02. P1 요구사항과 아키텍처

## 1. 목표
사용자가 자연어로 학원 업무를 요청하면 AI 비서가 의도를 파악하고, **기존 AcademyCraft 도구만** 사용해 안전하게 실행한다. 변경 작업은 사용자 승인 후 서버가 재검증하고 실행한다. AI 비서는 서비스의 주 진입점이 되지만 **수동 관리 화면은 언제나 그대로 쓸 수 있다.**

기본 흐름:
```
사용자 요청 → Muse Spark 의도 분석 → 도구·파라미터 서버 검증 → 실행 계획·초안 표시
 → 사용자 승인 → 서버 재검증 → 도구 실행 → 감사 로그 + 결과 표시
```

## 2. 요구사항

### 기능 (P1 범위 = 설계, 구현 순서는 06번)
| ID | 요구사항 | 구분 |
|----|----------|------|
| F1 | 자연어 요청에서 도구와 파라미터를 추출한다 | Muse Spark |
| F2 | 읽기 도구는 즉시 실행하고 결과를 카드·표로 보여준다 | 서버 |
| F3 | 쓰기 도구는 **실행하지 않고 계획(plan)을 만들어** 서버가 만든 미리보기를 보여준다 | 서버 |
| F4 | 사용자가 승인하면 서버가 모든 조건을 재검증하고 **1회만** 실행한다 | 서버 |
| F5 | 모든 단계(계획 생성·승인·거부·만료·실행·실패·취소)를 감사 로그로 남긴다 | 서버 |
| F6 | AI 실패·빈 데이터·권한 부족 시에도 수동 화면으로 이동할 수 있다 | 화면 |
| F7 | 화면은 390px 모바일 우선, 1440px PC 지원 | 화면 |

### 비기능·보안
| ID | 요구사항 |
|----|----------|
| S1 | ADMIN·TUTOR만 사용. STUDENT/PARENT는 에이전트 경로 접근 불가 |
| S2 | 학원 ID는 입력에서 받지 않는다. 서버 인증 컨텍스트에서만 얻는다 |
| S3 | 모델 출력은 **신뢰하지 않는 입력**이다. 도구 이름은 화이트리스트, 입력은 스키마·범위 검증을 통과해야 한다 |
| S4 | 승인 화면의 "변경 내용"은 모델 문장이 아니라 **서버가 도구 입력에서 생성**한 미리보기다 |
| S5 | 삭제·비활성화·외부 발송 도구는 P1~P3에서 만들지 않는다(기존 `CommandParser` 정책 유지) |
| S6 | 경로 허용은 인증·서명·역할·테넌트 검증을 대체하지 않는다 |
| S7 | 운영 DB 변경은 이 설계에서 적용하지 않는다. 마이그레이션은 별도 승인 후 순차 적용 |

## 3. 구성요소와 책임

```
[브라우저: AI 비서 홈]
      │ 같은 출처 HTTPS, JWT 쿠키(BuildIt)
      ▼
[BuildIt  AcademyCraftGateway]  ← 1차 관문: 로그인·ADMIN/TUTOR 재검증·정확 경로 허용·Origin(쓰기)·서명 생성
      │ X-BuildIt-Assertion + X-BuildIt-Signature (HMAC, 본문 해시 포함)
      ▼
[AcademyCraft  BuildItGatewayFilter]  ← 2차 관문: 서명·30초 시간창·nonce·binding·학원 활성·역할별 경로 허용
      ▼
[ReaSecurityInterceptor]  ← 3차: URL academyId == 인증 학원
      ▼
[AgentController → AgentPlanService(신규) → AgentExecutor/AgentDispatcher → AgentTool → 기존 Service]
      ▼
[AuditRecorder → rea_audit_logs] + [rea_agent_plans(신규)]
```

| 구성요소 | 책임 | 신규/기존 |
|----------|------|-----------|
| BuildIt `AssistantOrchestrator` | Muse Spark 호출, 도구 카탈로그 전달, 응답을 **제안(tool+params)** 으로 파싱 | 신규(BuildIt) |
| BuildIt `AcademyCraftGateway` | 정확 경로 허용 추가, 서명 중계 | 기존 수정 |
| AcademyCraft `BuildItGatewayFilter.allowed` | 정확 경로 허용 추가 | 기존 수정 |
| AcademyCraft `AgentPlanService` | 계획 생성·미리보기·승인·1회 실행·만료 | 신규 |
| AcademyCraft `AgentTool.preview` | 도구별 읽기 전용 미리보기(dry-run) | 신규 메서드 |
| `CommandParser` | Muse 장애 시 대체 해석 | 기존 유지 |

### Muse Spark 위치
- Muse Spark 호출은 **BuildIt 쪽**에 둔다. 이미 `MetaMuseSparkLlmTransport`가 있고 `DashboardAssistantService`가 같은 패턴(모델 고정, 입력 길이 제한, 실패 시 안내 텍스트)으로 쓴다. **[실제 코드]**
- 모델은 **제안만** 한다. 계획 생성·검증·승인·실행은 모두 AcademyCraft 서버가 한다. 모델 호출 결과는 서버 상태를 직접 바꾸지 않는다.
- 모델에게 전달하는 것: 도구 카탈로그(이름·설명·입력 스키마·쓰기 여부), 사용자 메시지(길이 제한), 최소한의 학원 문맥(이름 정도). 학생 개인정보 목록은 전달하지 않고, 필요한 조회는 **도구가 서버에서** 수행한다.
- 첨부 자료(P4)의 내용은 **데이터**이며 지시가 아니다. 파일 안의 문장이 도구 호출을 유도해도 위 S3·S4가 막는다.

### 실패 시 대체 경로 (F6)
| 상황 | 동작 |
|------|------|
| Muse Spark 실패·시간 초과 | `CommandParser`로 규칙 해석 시도 → 실패하면 "AI 연결에 문제가 있어요" + 수동 화면 버튼 |
| 해석은 됐으나 도구 없음 | "아직 지원하지 않는 요청" + 해당 수동 화면 링크 (작동하는 것처럼 표시하지 않음) |
| 권한 부족 | "원장 권한이 필요한 작업" 안내 + 읽기 대체 제안 |
| 계획 만료·취소 | 상태 표시 후 새 요청 유도. 자동 재실행 없음 |

## 4. 데이터 흐름 예 (반 배정, 쓰기)
1. 사용자: "김민수를 월수금반에 넣어줘"
2. BuildIt → Muse: 도구 카탈로그 + 메시지 → `{tool:"assignStudentToClass", params:{studentId:?, classId:?}}` 또는 이름만 → 서버가 `getStudents` 류 조회로 ID를 **서버에서 해석**(모호하면 후보를 질문)
3. AcademyCraft `AgentPlanService`: 도구 화이트리스트·스키마 검증 → `preview`가 "김민수(ID 12) → 월수금반(ID 3) 배정, 현재 소속: 없음"을 생성 → `rea_agent_plans`에 PENDING 저장, `planId`·`planHash`·`expiresAt` 응답
4. 화면: 미리보기 카드 + [승인] [취소]
5. 승인: `planId + planHash` 전송 → 서버 재검증(04번) → 1회 실행 → 감사 로그 → 결과 카드

## 5. 게이트웨이 변경 설계

### 5.1 현재 상태 **[실제 코드]**
- BuildIt `AcademyCraftGateway.allowedPath`: 화면은 18개 이름 고정 목록(#825의 `timetable-weekly.html` 포함), API는 `/api/rea/integration/(context|teachers)`와 `/api/rea/{id}/(students|classes|attendance|dashboard/stats|timetable|consultations|notices)(/.*)?`. **`agent` 없음.**
- AcademyCraft `BuildItGatewayFilter.allowed`: 같은 계열 목록. **`agent` 없음.** 학생(STUDENT) 연결은 본인 조회만 허용.
- 게이트웨이는 `POST/PUT/DELETE`에 대해 `Origin`을 정확 일치(`https://feel2real.co.kr`, 로컬 `http://localhost:8080`)로 검사하고, 본문 1MB 제한, `Cookie`·`Authorization` 등 신원 헤더를 전달하지 않는다.
- 서명: `HMAC(method \n target \n assertion \n SHA-256(body))`, 시간창 30초, nonce(메모리).

### 5.2 변경안 (양쪽 동시, 정확 경로만)
| 허용 | 메서드 | 정규식(경로) |
|------|--------|--------------|
| 도구 목록 | GET | `/api/rea/{id}/agent/tools` |
| 요청(읽기 실행 / 계획 생성) | POST | `/api/rea/{id}/agent/execute` |
| 계획 조회 | GET | `/api/rea/{id}/agent/plans/{uuid}` |
| 승인 | POST | `/api/rea/{id}/agent/plans/{uuid}/approve` |
| 취소 | POST | `/api/rea/{id}/agent/plans/{uuid}/cancel` |

- `{id}`는 `[1-9][0-9]*`, `{uuid}`는 36자 UUID 형식. `/agent/**` 같은 와일드카드는 쓰지 않는다.
- **BuildIt 쪽과 AcademyCraft 쪽을 같은 PR 묶음에서 함께 바꾼다.** 한쪽만 바꾸면 한쪽에서 403이 나며, 반대로 한쪽만 열린 상태로 배포되지 않도록 순서를 문서화한다(06번).
- `{id}`는 URL에 있지만 **권한의 근거가 아니다.** AcademyCraft는 인증 컨텍스트의 학원과 URL을 대조한다(`ReaSecurityInterceptor`, **[실제 코드]**).

### 5.3 경로 허용으로 대체되지 않는 검증 (모두 유지)
| 검증 | 위치 | 상태 |
|------|------|------|
| BuildIt 로그인(JWT 쿠키) | BuildIt | 유지 |
| ADMIN/TUTOR 역할 매 요청 재확인 | BuildIt 게이트웨이(DB 재조회) | 유지 |
| 같은 출처(Origin) 검사 — 쓰기 | BuildIt 게이트웨이 | 유지 |
| HMAC 서명·본문 해시·30초·nonce | 양쪽 | 유지 |
| 명시적 연결(binding)·학원 활성·내부 역할 | AcademyCraft 필터 | 유지 |
| URL 학원 == 인증 학원 | AcademyCraft 인터셉터 | 유지 |
| 도구 화이트리스트·스키마 검증 | AgentDispatcher | 유지 + 강화 |
| 쓰기 2단계 승인 | AgentPlanService | **신규(04번)** |
| 감사 로그 | AuditRecorder | 유지 + 이벤트 확대 |

### 5.4 AI가 우회할 수 없어야 하는 것 — 위협과 방어
| 위협 | 방어 |
|------|------|
| 모델이 임의 API 경로를 만들어 호출 | 모델은 **도구 이름과 파라미터만** 반환한다. HTTP 경로·메서드는 서버 코드가 고정. 임의 URL 실행 기능을 만들지 않는다 |
| 모델이 목록에 없는 도구 이름을 반환 | `AgentDispatcher.resolve`가 거부(화이트리스트) |
| 모델이 다른 학원 ID를 반환·삽입 | 학원 ID 입력 자체를 받지 않음. 대상 학생·반 ID는 해당 학원 소속인지 서버 재조회 |
| 프롬프트 주입(첨부 파일·학생 이름 안의 지시문) | 데이터는 지시로 취급하지 않음. 모델 출력은 신뢰하지 않는 입력 → 서버 검증. 미리보기는 서버가 생성 |
| 승인 우회(`writeApproved:true` 직접 전송) | 외부 본문의 값은 400 거부, 승인은 서버 저장 계획 ID로만 |
| 승인 후 입력 바꿔치기 | 실행 입력은 저장된 `input_json`뿐, 해시로 묶음 |
| 재전송·더블 클릭 | CAS 1회 실행, `idempotencyKey`, 게이트웨이 nonce |
| 권한 상승(강사가 원장 전용 도구 실행) | 계획 생성과 승인 모두에서 현재 역할로 도구 허용 여부 확인 |
| 과도한 호출·대량 변경 | 건수 상한, PENDING·분당 생성 상한 |
| 개인정보가 모델로 유출 | 도구 결과(학생 목록 등)는 화면에만 표시하고 모델에 재전송하지 않는다. 필요 시 집계값만 사용 |
