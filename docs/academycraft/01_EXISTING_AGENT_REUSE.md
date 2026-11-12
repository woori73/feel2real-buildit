# 01. 기존 에이전트 기능 재사용 분석

기준: AcademyCraft `main` `543cdfc` (저장소 `woori73/academycraft`, `backend/src/main/java/com/rea/academy/…`). 모두 **[실제 코드]**.

## 1. 이미 있는 것

| 구성 | 위치 | 하는 일 |
|------|------|---------|
| `AgentController` | `controller/AgentController.java` | `POST /api/rea/{academyId}/agent/execute`(자연어 명령 + `writeApproved`), `GET .../agent/tools`(도구 이름·설명·입력 스키마·쓰기 여부) |
| `AgentExecutor` | `agent/AgentExecutor.java` | 파싱 → 도구 조회 → 쓰기 정책 검사 → 실행 → 감사 로그를 한 흐름으로 처리 |
| `CommandParser` | `agent/CommandParser.java`(343줄) | 키워드·정규식 기반 명령 해석. LLM을 쓰지 않는다. 삭제·제거·탈퇴·파기 키워드는 어떤 규칙보다 먼저 거부 |
| `AgentDispatcher` | `agent/AgentDispatcher.java` | 스프링 빈으로 등록된 `AgentTool` 목록에서 이름으로 도구를 찾는다(등록된 도구만 실행 가능 = 화이트리스트) |
| `AgentTool` | `agent/AgentTool.java` | 도구 인터페이스: 이름·설명·입력 스키마·`isWrite()`·`execute(input, ctx)` |
| `AgentContext` | `agent/AgentContext.java` | 주체 종류·학원 ID·역할·주체 ID·이름·`writeApproved` (불변 record) |
| `AuditRecorder` | `service/AuditRecorder.java` | `rea_audit_logs`에 append-only 기록. 행위 종류 `AGENT_TOOL_EXECUTED` 존재 |
| `ReaSecurityInterceptor` | `security/ReaSecurityInterceptor.java` | URL의 `{academyId}`가 인증된 사용자의 학원과 다르면 403(서비스 레벨 학원 스코핑과 이중 방어) |
| `BuildItGatewayFilter` | `integration/BuildItGatewayFilter.java` | BuildIt 서명 검증, 30초 시간창, nonce(메모리) 재전송 차단, 명시적 연결(binding) 확인, 학원 활성 확인, 역할별 경로 허용 |

### 도구 8개
| 도구 | 쓰기 | 서비스 재사용 |
|------|------|---------------|
| `getStudents` | 읽기 | 학생 조회 |
| `getAbsentStudents` | 읽기 | 출결 조회 |
| `getHomeworkIncomplete` | 읽기 | 숙제 현황 |
| `analyzeGradeChanges` | 읽기 | 성적 변화 분석 |
| `generateOperationReport` | 읽기 | 운영 리포트 |
| `registerStudent` | **쓰기** | 학생 등록 |
| `assignStudentToClass` | **쓰기** | `ClassService.assignStudents(ctx.academyId(), …)` — 학원 ID를 컨텍스트에서 가져와 도구 입력으로 학원을 바꿀 수 없다 |
| `createNoticeDraft` | **쓰기** | 공지 초안(외부 발송 없음 경로만 사용) |

### 이미 맞는 정책
- 쓰기 도구는 `writeApproved`가 없으면 실행하지 않고 `requiresApproval=true`로 응답한다.
- 학부모는 쓰기 도구를 쓸 수 없다.
- 성공·거부·실패 모두 감사 로그에 남는다(명령은 300자로 절단).
- 삭제류 요청은 도구가 없어서 실행되지 않는다.
- 학원 ID는 URL이 아니라 인증 컨텍스트에서 오고, 인터셉터가 URL과 대조한다.

## 2. 부족한 점 (설계가 메워야 할 것)

| # | 한계 | 근거 | 영향 |
|---|------|------|------|
| G1 | **승인이 클라이언트 불리언** | `AgentController.execute`가 요청 본문의 `writeApproved`를 그대로 `AgentContext`에 넣는다. 기존 화면 `CHAT-01-ai-chat.html`은 사용자가 체크하는 "쓰기 승인" 체크박스 값을 그대로 전송한다 | 승인 화면을 거치지 않고 `writeApproved:true`를 직접 보내면 쓰기가 실행된다. "무엇을 승인했는지"와 "무엇이 실행됐는지"가 연결되지 않는다 |
| G2 | **승인 대상과 실행 대상이 같은 요청** | 승인과 실행이 한 번의 호출이다 | 사용자가 본 초안과 실행 입력이 다를 수 있다(모델 출력 변조·재해석) |
| G3 | **1회 실행·만료·중복 방지 없음** | 서버 상태를 저장하지 않는다 | 같은 요청 재전송 시 쓰기가 반복된다 |
| G4 | **명령 해석이 규칙 기반** | `CommandParser` | "중등 영어 월수금반 만들어 줘" 같은 자유 요청을 이해하지 못한다 |
| G5 | **게이트웨이 양쪽 허용 목록에 `/agent` 없음** | BuildIt `AcademyCraftGateway.allowedPath`(정규식에 agent 없음), AcademyCraft `BuildItGatewayFilter.allowed`(`students|classes|attendance|dashboard/stats|timetable|consultations|notices`만) | BuildIt 로그인으로 들어온 사용자는 지금 에이전트를 호출할 수 없다 |
| G6 | **쓰기 도구 범위 협소** | 학생 등록·반 배정·공지 초안 3개뿐 | 반 생성·종료, 출결 입력, 상담 기록 도구 없음(해당 REST API는 존재) |
| G7 | **TUTOR의 쓰기 허용 범위 불명확** | `AgentExecutor`는 학부모만 막고 강사(TEACHER)의 쓰기는 막지 않는다 | [결정 필요] 03번 정책표 참조 |
| G8 | **초안 미리보기 없음** | 도구에 dry-run이 없다 | 승인 화면에 "무엇이 바뀌는지"를 서버가 보증하지 못한다 |
| G9 | **nonce가 프로세스 메모리** | `BuildItGatewayFilter.nonces`(ConcurrentHashMap) | 재시작·다중 인스턴스에서 재전송 방지가 약해진다. 30초 시간창이 보완하지만 승인 프로토콜은 이에 의존하지 않아야 한다 |
| G10 | **대시보드 개인화 저장 구조 없음** | — | P2 대상 |

## 3. 재사용 원칙
1. `AgentTool`·`AgentDispatcher`·`AgentExecutor`·`AuditRecorder`는 **그대로 유지**하고, 그 위에 계획(plan) 계층을 추가한다.
2. 새 도구는 기존 서비스(`ClassService` 등)를 호출하는 얇은 `AgentTool` 구현으로 추가한다. 서비스 로직을 복제하지 않는다.
3. `CommandParser`는 **삭제하지 않고** Muse Spark 장애 시 대체 해석기로 남긴다.
4. 기존 `AgentController.execute`의 `writeApproved`는 게이트웨이를 통과하는 경로에서는 쓰기 도구를 실행하는 근거로 쓰지 않는다(04번).
