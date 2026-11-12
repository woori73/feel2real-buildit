# 04. 서버 검증형 2단계 승인 프로토콜

> 이 문서의 SQL은 **설계용 초안**이다. `db/migration`에 파일로 두지 않았고 어떤 DB에도 적용하지 않았다. 적용은 별도 승인 후 AcademyCraft 저장소의 순차 마이그레이션(현재 `V5`까지 존재 → 다음은 `V6`, 적용 시점에 재확인)으로만 한다.

## 1. `writeApproved` 불리언의 한계

| # | 한계 | 구체 시나리오 |
|---|------|---------------|
| L1 | 클라이언트가 값을 정한다 | 승인 화면 없이 `{"writeApproved":true}`를 직접 전송하면 실행된다. 기존 `CHAT-01` 화면도 체크박스 값을 그대로 보낸다 |
| L2 | 승인과 실행이 한 번의 요청 | 사용자가 본 초안과 실제 실행 입력이 같다는 보증이 없다(모델 재해석·변조) |
| L3 | "무엇을" 승인했는지 기록되지 않는다 | 감사 로그에는 명령 문자열만 남고 실행된 입력과 연결되지 않는다 |
| L4 | 1회성이 아니다 | 같은 요청을 재전송하면 쓰기가 반복된다(네트워크 재시도, 더블 클릭) |
| L5 | 만료가 없다 | 오래전 요청을 나중에 재사용할 수 있다 |
| L6 | 권한·학원 변경이 승인에 반영되지 않는다 | 승인 후 역할이 바뀌거나 연결이 해제돼도 이미 보낸 요청은 그대로 처리된다 |
| L7 | AI 출력과 실행 사이에 서버 검증 단계가 없다 | 모델이 만든 파라미터가 곧바로 도구에 들어간다 |

## 2. 설계 원칙
1. **쓰기 도구는 요청 한 번으로 절대 실행되지 않는다.** 계획 생성(1단계)과 승인 실행(2단계)을 분리한다.
2. 계획은 **서버가 저장**하고 고유 ID를 준다. 실행 입력은 저장된 값만 쓰며, 승인 요청에서 입력을 다시 받지 않는다.
3. 승인 요청은 `planId`와 `planHash`(사용자가 본 미리보기의 해시)만 보낸다. **본 것 = 실행할 것**을 해시로 묶는다.
4. 실행 전 모든 조건을 **서버가 다시** 확인한다(권한·학원·도구·입력 유효성·만료·상태).
5. 상태 전이는 **조건부 갱신(CAS)** 으로만 한다. 한 번 실행되면 끝이다.
6. 쓰기 실패는 **자동 재시도하지 않는다.** 다시 하려면 새 계획과 새 승인이 필요하다.
7. 경로 허용(게이트웨이)과 이 프로토콜은 별개의 층이다. 어느 하나가 다른 하나를 대체하지 않는다.

## 3. 단계와 엔드포인트 (정확 경로)

| 단계 | 메서드·경로 | 동작 |
|------|-------------|------|
| 도구 목록 | `GET /api/rea/{academyId}/agent/tools` | 이름·설명·스키마·쓰기 여부(기존) |
| 요청 | `POST /api/rea/{academyId}/agent/execute` | **읽기 도구**: 즉시 실행·결과 반환. **쓰기 도구**: 실행하지 않고 계획 생성 후 `planId`·`preview`·`planHash`·`expiresAt` 반환. 본문의 `writeApproved:true`는 **거부(400)** |
| 조회 | `GET /api/rea/{academyId}/agent/plans/{planId}` | 상태·미리보기·결과 |
| 승인 | `POST /api/rea/{academyId}/agent/plans/{planId}/approve` | 본문 `{planHash}` → 재검증 후 1회 실행 |
| 취소 | `POST /api/rea/{academyId}/agent/plans/{planId}/cancel` | PENDING만 취소 |

`planId`는 UUID. 경로 정규식은 `[1-9][0-9]*`(학원 ID)와 UUID 형식으로 고정한다.

멱등 키: `POST …/execute` **본문의 `idempotencyKey`(UUID)** 로 받는다. 같은 학원·같은 키는 **같은 계획**을 돌려준다. 헤더가 아니라 본문에 두는 이유: BuildIt 게이트웨이는 `Content-Type` 외 요청 헤더를 업스트림에 전달하지 않고(**[실제 코드]**), 본문은 서명(본문 SHA-256)에 포함되어 변조를 막기 때문이다.

## 4. 저장 구조 (초안)

```sql
-- 설계 초안 — 미적용. 적용 시 AcademyCraft db/migration 의 다음 순번 V 파일로 승인 후 추가.
CREATE TABLE rea_agent_plans (
  plan_id            CHAR(36)      NOT NULL PRIMARY KEY,         -- UUID
  academy_id         BIGINT        NOT NULL,
  principal_type     VARCHAR(20)   NOT NULL,                     -- MEMBER
  principal_id       BIGINT        NOT NULL,                     -- 계획 생성자(회원)
  buildit_user_id    BIGINT        NULL,
  role_at_plan       VARCHAR(30)   NOT NULL,                     -- ACADEMY_OWNER / TEACHER
  binding_fingerprint CHAR(64)     NOT NULL,                     -- 계획 시점 연결(binding) 지문
  tool_name          VARCHAR(80)   NOT NULL,
  input_json         JSON          NOT NULL,                     -- 정규화된 입력(저장된 값만 실행)
  input_sha256       CHAR(64)      NOT NULL,
  preview_json       JSON          NOT NULL,                     -- 서버가 생성한 미리보기
  plan_hash          CHAR(64)      NOT NULL,                     -- 아래 5절
  source             VARCHAR(20)   NOT NULL,                     -- ASSISTANT / RULE / MANUAL
  status             VARCHAR(20)   NOT NULL,                     -- PENDING/EXECUTING/EXECUTED/FAILED/CANCELLED/EXPIRED/INVALIDATED
  idempotency_key    CHAR(36)      NULL,
  created_at         DATETIME(6)   NOT NULL,
  expires_at         DATETIME(6)   NOT NULL,
  approved_at        DATETIME(6)   NULL,
  approved_by        BIGINT        NULL,
  executed_at        DATETIME(6)   NULL,
  result_json        JSON          NULL,                         -- 크기 제한·개인정보 최소화
  failure_code       VARCHAR(40)   NULL,
  UNIQUE KEY uq_plan_idem (academy_id, idempotency_key),
  KEY ix_plan_academy_status (academy_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
- 추가 전용(additive) 변경이다. 기존 테이블을 수정하지 않는다.
- 감사는 **기존 `rea_audit_logs` 재사용**. `AuditActionType`에 `AGENT_PLAN_CREATED / APPROVED / REJECTED / EXPIRED / EXECUTED / FAILED / CANCELLED / INVALIDATED`를 추가한다(열거형 값 추가 → 컬럼이 문자열인지 구현 시 확인).

## 5. 연결 관계와 해시

`plan_hash = SHA-256( schemaVersion | academy_id | principal_id | role_at_plan | binding_fingerprint | tool_name | input_sha256 | expires_at )`

- `binding_fingerprint = SHA-256( buildit_user_id | buildit_role | buildit_academy_id | academy_id | member_id )` — 현재 연결 정보로부터 계산.
- 사용자·학원·권한·도구·입력값이 모두 해시에 들어가므로 하나라도 달라지면 승인할 수 없다.
- 사용자는 미리보기와 함께 받은 `planHash`를 승인 요청에 되돌려 보낸다 → "내가 본 계획"임을 서버가 확인한다.

## 6. 승인·실행 알고리즘 (서버, 한 요청)

```
approve(planId, planHash, ctx):
 1. [트랜잭션 시작] SELECT … FROM rea_agent_plans WHERE plan_id=? FOR UPDATE
 2. 없음 → 404.  plan.academy_id != ctx.academyId → 404(존재 숨김) + 감사(REJECTED, 학원 불일치)
 3. status == EXECUTED → 저장된 result 반환(재실행 없음, 멱등).  FAILED/CANCELLED/EXPIRED/INVALIDATED → 409
 4. now > expires_at → status=EXPIRED, 감사, 410
 5. plan.principal_id != ctx.principalId → 403 + 감사 (생성자만 승인)
 6. 현재 역할(ctx.role) ≠ plan.role_at_plan, 또는 해당 도구가 현재 역할에 허용되지 않음 → INVALIDATED + 감사, 403
 7. 현재 binding_fingerprint ≠ plan.binding_fingerprint → INVALIDATED + 감사, 403   (연결·학원 변경)
 8. dispatcher.resolve(tool_name) 가능하고 isWrite 일치, 입력 스키마 재검증 통과 → 아니면 INVALIDATED, 422
 9. 재계산한 plan_hash == 저장값 == 요청 planHash → 아니면 403 + 감사(변조 의심)
10. UPDATE … SET status='EXECUTING', approved_at=now, approved_by=? WHERE plan_id=? AND status='PENDING'
    영향 행 수 != 1 → 409 (다른 요청이 먼저 처리)       ← 1회 실행 보증(CAS)
11. [커밋] — 이후 도구 실행은 별도 트랜잭션(도구가 기존 서비스의 @Transactional 사용)
12. tool.execute(저장된 input_json, ctx[writeApproved=서버가 내부에서 true 로 설정])
13. 성공 → status=EXECUTED, result 저장, 감사(EXECUTED).  예외 → status=FAILED, failure_code, 감사(FAILED). 자동 재시도 없음
```
- 2단계 승인 요청 본문에는 입력이 없다. **실행 입력은 항상 저장된 `input_json`** 이다.
- `writeApproved`는 서버가 내부에서만 설정한다. 외부 요청 본문의 값은 무시하지 않고 **400으로 거부**한다(조용히 무시하면 클라이언트가 동작을 오해한다).

## 7. 만료·1회·중복·재실행
| 항목 | 정책 |
|------|------|
| 만료 | 생성 후 **10분**(초안) [결정 필요: 5~15분]. 만료 계획은 승인 불가, 읽기 전용으로 남음 |
| 1회 실행 | 10단계 CAS로 `PENDING → EXECUTING`은 한 번만 성공 |
| 중복 요청 | 계획 생성: `idempotencyKey`로 동일 계획 반환. 승인: 상태별 응답(EXECUTED면 저장된 결과) |
| 재실행 | 쓰기는 자동 재시도 금지. 실패·만료 후에는 **새 계획 + 새 승인** |
| 중간 중단 | `EXECUTING`에서 프로세스가 죽으면 상태가 남는다. 일정 시간(예: 5분) 지난 `EXECUTING`은 `FAILED(UNKNOWN)`으로 표시하고 **재실행하지 않으며**, 사용자에게 "결과를 직접 확인하세요"를 안내(이미 일부 반영됐을 수 있음) |
| 네트워크 재전송 | BuildIt 게이트웨이의 30초 시간창·nonce가 1차로 막고, 프로토콜은 이에 의존하지 않는다(nonce는 메모리 저장) |
| 동시 승인 | 같은 계획에 대한 동시 `approve` 중 하나만 CAS 성공 |
| 대량 방지 | 사용자·학원당 PENDING 계획 상한(예: 20), 분당 생성 상한 |

## 8. 권한·학원 변경 시 무효화
- 승인 시점 재검증(6·7단계)이 **주된 방어선**이다. 별도 폴링에 의존하지 않는다.
- 연결(binding)은 현재 환경 설정(JSON)으로 읽히므로 변경은 재시작으로 반영된다. 따라서 `binding_fingerprint` 불일치가 곧 무효화다. **[실제 코드]** 구현 시 연결 저장 방식이 DB로 바뀌면 같은 방식으로 유지된다.
- 재시작·설정 변경 직후 오래된 PENDING 계획은 만료 시각이 지나기 전이라도 승인 시 7단계에서 걸러진다.
- 학원이 비활성화되면 게이트웨이 필터(학원 활성 확인)에서 먼저 차단된다. **[실제 코드]**

## 9. 감사·실패·재시도
| 이벤트 | 기록 |
|--------|------|
| 계획 생성 | 도구, 입력 해시·요약, 출처, 만료 시각 |
| 승인 시도 거부 | 사유 코드(학원 불일치·권한 변경·해시 불일치·만료·상태) |
| 실행 성공·실패 | 결과 코드, 실패 시 `failure_code`(상세 메시지에 개인정보 제외) |
| 만료·취소 | 상태 변경 |
- 감사 `detail`에는 전체 입력을 넣지 않고 **입력 해시와 대상 건수·ID 요약**만 넣는다(상담 내용 등 민감정보 보호).
- 읽기 도구는 계획을 만들지 않고 기존처럼 실행 기록만 남긴다.

## 10. 롤백·복구 방안 (문서 제안)
| 상황 | 방안 |
|------|------|
| 적용 전 | 대상 DB 백업(`mysqldump` 또는 스냅샷)을 먼저 확보하고 복원 절차를 리허설한다 |
| 마이그레이션 | Flyway Community에는 undo가 없다. 변경이 **테이블 추가뿐**이라 앱이 새 테이블을 쓰지 않으면 영향이 없다 |
| 기능 비활성 | 설정 플래그(예: `academycraft.agent.approval.enabled`)로 쓰기 도구를 즉시 막을 수 있어야 한다. 비활성이면 쓰기 도구는 "일시 중지" 안내만 반환 |
| 앱 롤백 | 새 테이블은 이전 버전 앱이 무시한다 → 앱만 되돌려도 데이터 정합성에 영향 없음 |
| 테이블 제거(최후) | `DROP TABLE rea_agent_plans;` + `flyway_schema_history`의 해당 행 처리는 DBA 판단. **사전 백업 필수, 자동 실행 금지** |
| 감사 로그 | 열거형 값 추가는 되돌리지 않는다(기존 행 보존) |
| 검증 | 로컬 MySQL(Testcontainers 또는 로컬 DB)에서 적용·롤백 리허설 후에만 운영 적용 승인 요청 |

## 11. 로컬 검증 계획 (구현 단계)
- 단위: 해시 계산, 상태 전이(CAS), 만료, 역할 변경 무효화, 학원 불일치, 해시 변조, 동시 승인(스레드 2개 중 1개만 성공), `writeApproved:true` 거부.
- 통합: 실제 MySQL로 `FOR UPDATE`+CAS 동작, 중복 `idempotencyKey`, `EXECUTING` 장기 체류 처리.
- 보안: 다른 학원 `planId`로 승인 시도 → 404, STUDENT 연결로 `/agent/*` 호출 → 403.
