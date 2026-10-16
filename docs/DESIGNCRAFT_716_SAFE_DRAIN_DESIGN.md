# DesignCraft #716 안전한 CLI 교체 통제 설계 (구현 전)
2026-10-16 KST. 승인 범위: 읽기 전용 확인·설계. 이 문서는 앞선 '현재 프로세스가 없으면 원자 전환' 방식의 부족한 신규 호출 통제를 보완한다.

## 현재 상태
- #716 open / ready for review(draft=false), head f244717581c5dce7413858407ca80fb95d833f03 유지.
- #715 open / unmerged 유지. #716 병합과 main CI 성공 전 종료하지 않는다.
- 기존 검증/CI는 재실행하지 않았다. 운영 CLI 설치/전환, 앱 배포, Spring 재시작, AWS/IAM/DB 변경 없음.
- 기존 Actions 실행 목록을 읽었으며, 마지막 OIDC 실패(run 38871839790) 이후 복구 성공을 확인할 근거는 없었다. 별도 복구 작업과 충돌하지 않도록 인증 재시도·IAM 수정은 안 했다.
- 현재 운영 SHA/경로/실행 작업/대기 작업 재확인은 복구 확인 후 수행할 미완료 항목이다. 과거 SHA를 현재 값으로 재표기하지 않는다.

## 최소 변경 권장안: DesignCraft 전용 admission gate + operation drain
셸 wrapper나 nginx만 변경하는 대신 Spring 내부의 작은 공통 gate를 제안한다. 현재 배포된 앱에 없는 기능이며 **추가 구현 승인과 별도 배포/재시작 승인이 필요하다.** 이번에는 코드/설정을 구현·배포하지 않는다.

범위:
1. 공통 PublishingMaintenanceGate 하나: OPEN → DRAINING → CLOSED 상태와 활성 operation 수.
2. /api/publishing/** 전용 filter/interceptor: DRAINING/CLOSED이면 서비스·트랜잭션·파일 쓰기에 진입하기 전에 503 + Retry-After + 안전한 유지보수 메시지 반환. 신규 조판뿐 아니라 기존 문서의 미리보기·검수·승인 적용·PDF 등 CLI 호출 경로를 함께 막는다. 단순·명확한 차단을 위해 해당 API 전체를 잠시 중단한다. 다른 서비스 API와 로그인/정적 페이지/health에는 적용하지 않는다.
3. PublishingWorker.poll 진입에서 gate lease 확보, 차단 중이면 DB 조회/claim/attempt 증가 전에 스킵한다. 대기 작업을 FAILED/CANCELLED로 바꾸지 않는다.
4. DesignCraftCliExecutor의 모든 프로세스 시작점에 유효한 operation lease 확인을 추가하여 filter를 우회한 내부 호출도 차단한다. 'CLI 한 번 실행'만의 락이 아니라 import/inspect/edit/render 같은 여러 CLI와 파일·트랜잭션 처리가 끝날 때까지 operation lease를 유지한다.
5. 기존 승인된 작업의 하위 CLI 호출은 DRAINING 중에도 기존 lease로 완료할 수 있게 한다. 내부 중첩 호출에서 새 lease를 재발급하거나 drain 때문에 기존 작업을 중간 중단하지 않는다. 신규 사용자 요청/신규 Worker operation만 거절한다.

동시성:
- admission 여부 검사와 active 증가, OPEN→DRAINING 전환을 같은 동기화 영역에서 처리하여 TOCTOU를 막는다.
- 완료/예외/timeout/취소 모두 finally에서 lease를 회수한다. DB transaction 완료와 최종 파일 저장 이후에만 active를 감소시킨다.
- 비동기 Servlet/Executor 작업이 있다면 operation token을 명시적으로 전달하고 최종 완료까지 lease를 연장한다. HTTP 응답이 먼저 끝났다는 이유로 active=0으로 만들지 않는다.
- scheduler/worker와 CLI 직접 호출의 전체 진입점을 검색해 누락을 확인해야 한다. 모든 진입 경로를 덮었다는 것은 구현 후 검증할 사항이다.
- 관리자는 drain 호출을 받으면 새 admission을 먼저 닫고, active=0 및 실제 OS CLI 프로세스=0을 확인한 뒤에만 CLOSED로 판단한다. RUNNING DB 상태가 오래 유지될 수 있으므로 DB 상태 숫자를 실행 중 프로세스 수로 대체하지 않는다.
- drain deadline은 초기 제안 180초. 기존 개별 CLI timeout 120초와 여러 CLI가 이어지는 작업을 고려하되 전체 완료가 보장되는 값으로 표현하지 않는다. 초과하면 **교체를 취소**하고 기존 바이너리를 유지한다. 강제 프로세스 종료/DB 상태 수정은 하지 않는다.

## 관리 진입점과 재시작 복구
기존 ADMIN 인증·인가를 재사용하는 내부 전용 control/status endpoint(이름은 구현 시 API 문서와 함께 확정)를 제안한다.
- 상태 조회: gate 상태·활성 operation 수·Worker 진입 상태·현재 CLI SHA만 반환. 문서/키/argv/사용자 데이터 없음.
- drain/resume: ADMIN 권한, CSRF 보호, 명시적 조작. 인터넷에 무인증으로 노출하지 않음. 프록시 헤더/localhost 주장만 신뢰하지 않음. 내부 운영 접근 방법은 별도 확정하며 IAM/네트워크 정책 변경을 자동 포함하지 않는다.
- drain 상태는 전용 로컬 상태파일에 원자 저장하고 프로세스 재기동 시 유지한다. 저장 실패면 교체 가능한 CLOSED를 보고하지 않는다. gate 재진입과 marker 반영 사이에 신규 admission이 열리지 않도록 한다.
- 유지보수 상태를 시간 만료로 자동 해제하지 않는다. 운영자 연결이 끊기거나 앱이 재기동해도 교체 중 요청이 재개되지 않게 한다.
- 정상 CLI canary·해시/경로 검증 완료 뒤 명시적 resume. 복구 중에는 상태파일/권한을 함부로 삭제하지 않는다.
- 여러 Spring 인스턴스/별도 프로세스 Worker가 존재하면 JVM 하나의 gate로 충분하지 않다. 모든 실행 주체를 확인해 동일 drain을 완료할 수 없으면 중단한다. 새 분산 락 인프라는 이번 최소안에 포함하지 않는다.

## OIDC 복구 후 허용된 읽기 전용 재확인
별도 작업의 복구 성공 통지를 받은 뒤 기존 진단 역할/대상만 사용한다. 필요한 권한이 없으면 중단하며 IAM을 수정하지 않는다.
- 서비스 MainPID/User, publishing CLI 설정 출처/경로, realpath/mode/소유자/운영 SHA 확인. 전체 env/argv는 출력하지 않는다.
- 교체 artifact SHA는 기존 검증값과 대조. 운영에 전달하거나 실행파일을 설치하지 않는다.
- /proc의 exe/comm/PPID만으로 실제 CLI 프로세스 수를 여러 시점에서 확인. 문서 경로가 담긴 argv는 기록하지 않는다. 0건은 관측 순간의 값이며 신규 호출 통제를 뜻하지 않는다.
- 대기 작업은 기존 DB 연결 권한을 메모리에서만 사용해 **읽기 전용 transaction의 집계 SELECT**로 확인한다. 비밀번호를 SSM 매개변수/Actions 로그/명령행으로 전달하지 않는다. 기존 보안 접속 경로가 없으면 추가 권한·접속 방법을 요청하고 진행하지 않는다.
  - 코드의 실제 테이블: publishing_jobs
  - 조회: SELECT status, COUNT(*) AS count FROM publishing_jobs GROUP BY status;
  - QUEUED / RUNNING / DONE / FAILED / CANCELLED 집계만 반환. 문서명/원문/사용자/작업별 레코드 미수집.
  - READ ONLY session/transaction, 짧은 timeout, 종료 시 rollback/close. Flyway·DDL·DML·계정 생성·DB 상태 보정 금지.
- 신규 호출 gate와 실제 실행 인스턴스 수, 유입 경로 및 background 실행 주체를 재확인한다. 지금은 gate가 확보됐다고 판정하지 않는다.

## 검증할 신규 범위 (기존 QA 반복 없음)
추가 구현 승인 후 새 gate 자체만 격리 검증한다:
- 신규 요청과 drain 동시 도착에서 admission 누수 없음.
- 이미 시작한 다중 CLI operation은 완료, 이후 요청/Worker는 CLI를 새로 시작하지 않음.
- drain 중 QUEUED 상태/attempt/원문/리비전 변동 없음, 유지보수 거절은 부작용 전 실행.
- 예외·timeout·async·취소에서 lease 유실/영구 active 없음, deadline 초과 시 교체 금지.
- 유지보수 중 프로세스 재기동/상태파일 오류는 안전하게 닫힘, 권한 없는 drain/resume 거절.
- /ledger·/builder·/photocraft·/testprep·로그인/health 등 다른 경로가 gate를 통과하며 응답에 영향 없는지 신규 범위의 경계만 확인.
- source에서 CLI 시작점 누락 없음, 다중 프로세스라면 모두 drain 확인 전 교체 불가.
기존 로그인→AI 수정→PDF 사용자 흐름과 기존 회귀 suite 전체를 다시 수행하는 계획이 아니다.

## 운영 적용 순서와 복구
현재는 아래 모두 미승인이다.
1. gate 구현/격리 검증을 별도 변경으로 준비.
2. 코드 병합·배포·Spring 재시작은 각각 별도 승인 후 gate를 설치한다. 최초 gate 설치는 공통 Spring 재시작을 동반하므로 BuildIt 전체에 영향을 줄 수 있다.
3. 설치된 gate를 drain하고 active/실제 CLI=0 확인. 대기 작업은 보존.
4. 새 바이너리의 이전 승인 요청서상 SHA/설치/백업·파일 canary 절차 수행, 안정 경로 원자 전환.
5. 안정 경로 canary 및 같은 서비스 사용자 실행·PDF 재열기 확인. 실제 Spring 호출 관측과 파일 canary의 증거를 구분한다.
6. 성공한 경우에만 resume. 실패하면 gate를 유지한 채 기존 binary 복원·검증 후 복구 판단.

영향:
- **평상시:** DesignCraft의 작은 gate 판정만 추가. 다른 서비스의 executor/DB pool/라우팅을 멈추지 않는다. 실제 성능 영향은 신규 구현 테스트 전 미확정.
- **drain/교체 중:** DesignCraft API는 503으로 일시 중단(미리보기/편집/PDF 요청 포함), 백그라운드 신규 시작은 대기. 기존 operation은 종료를 기다린다. 다른 BuildIt 서비스는 계속 동작하도록 범위를 제한한다.
- **최초 gate 배포:** shared Spring 재시작 때문에 다른 서비스에도 중단 영향이 있다. 기존 앱 배포 추정 1~3분/장애 여유 10분이며 실측 SLA 아님. 현재 이 재시작을 승인받은 상태가 아니다.
- **gate 해제:** CLI/앱 정상 상태에서 명시적 resume으로 DesignCraft만 재개. 이전 CLI 자체는 glibc 비호환이므로 구 CLI 롤백만으로 조판이 정상화되지 않는다. 이 경우 gate를 닫아 유지하고 사용자 안내 후 추가 복구 승인을 받는다.
- gate 코드 자체에 장애가 있으면 검증된 이전 JAR 복구/재시작이 별도 필요할 수 있다. DB/사용자 문서를 롤백하거나 기존 대기 작업을 삭제하지 않는다.

## 필요한 추가 승인 (지금은 대기)
A. 위 gate의 **코드 구현과 신규 범위의 격리 검증** 승인. 현재 지시는 설계까지만이므로 구현하지 않았다.
B. 검증된 gate 변경의 병합·운영 앱 배포 및 Spring 재시작 승인. #716 병합 승인과 동일시하지 않는다.
C. 최신 운영 사전점검 및 gate drain 성공 후에만, 고정 SHA CLI 설치/경로 전환·synthetic canary·필요 시 수동 롤백 승인.
OIDC 복구와 그 IAM 변경은 별도 작업에 맡긴다. 현재 CLI 교체·운영 변경은 보류하며 승인 대기한다.
