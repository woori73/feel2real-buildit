# DesignCraft admission / operation drain 구현

별도 브랜치 `feat/designcraft-operation-drain`, base main `3cb035f947612b8752fb6335779fef39aca6dac3`. #715/#716의 커밋을 부모로 재사용하지 않았다. CLI 버전 업데이트/AI 편집/UI/폰트 수정은 이번 변경에 없다.

## 변경 경계
- `PublishingMaintenanceGate`: 원자 admission/drain, operation 참조 수, 실제 CLI onExit 추적, 명시적인 비동기 전달/취소 회수, 상태 파일과 단일 디렉터리 소유 FileLock.
- `PublishingAdmissionFilter`: `/api/publishing` 경계만 차단, Servlet async 완료·Callable·AsyncContext.start 추적. 다른 경로는 통과.
- `PublishingOperationProxy`: Spring infrastructure advisor로 publishing.service 전체 public 경계에서 작업을 보호. transaction advisor보다 바깥에 있어 commit까지 추적. Worker poll 차단은 저장소 호출 전 skip. CompletionStage가 실제 완료될 때까지 retain.
- `PublishingMaintenanceController`: 기존 ADMIN API 규칙 + ADMIN 재확인 + 전용 헤더/동일 출처 검증. SecurityConfig 및 기존 서비스 권한 규칙은 수정하지 않음.
- 기존 `DesignCraftCliExecutor`: 프로세스 생성 직후 operation에 실제 프로세스 수명 등록 1줄만 추가. 실제 종료 전에는 Java timeout/예외가 발생해도 operation이 완료되지 않음.

## 모든 CLI 진입점 조사
전체 Java 소스의 designcraft-cli / DesignCraftCliExecutor / ProcessBuilder 검색 결과:
- publishing의 ProcessBuilder는 `DesignCraftCliExecutor.runProcess` 하나.
- public entry: execute, inspect, executeOnDocument(두 overload), pageCount, renderPage, renderAllPages. 모두 동일 서비스 advisor 적용 대상. pageCount 내부 inspect/self-call도 이미 확보한 바깥 operation 안에서 실행.
- caller: PublishingEditService, PdfExportService. 모두 같은 advisor 적용. 사용자 요청 filter가 여러 서비스·CLI 호출을 하나의 operation으로 유지.
- scheduled entry: PublishingWorker.poll 한 곳. @Async 업무 메서드 없음. stdout 수집 CompletableFuture는 새 업무/CLI를 실행하지 않음.
- HWPX Python `HwpxCliExecutor`는 별도 기능으로 이 DesignCraft CLI 교체 통제 대상이 아님. 다른 서비스 executor도 변경 안 함.
- Spring bean 밖에서 직접 new로 만드는 기존 단위 테스트 객체는 프록시 대상이 아니다. 운영 Java 소스에 그런 생성은 없음을 조사. 외부 셸/다른 JVM은 차단하지 못한다.

## 재시작 / 실패 정책
매 시작은 CLOSED다. 이전 OPEN 상태도 자동 복원하지 않아, 교체 중 비정상 종료·파일 쓰기 실패 뒤 재시작으로 admission이 열리지 않는다. 디렉터리가 사라져도 새 인스턴스는 CLOSED로 시작. 상태 파일은 force+atomic move로 저장한다. 권한/atomic move/FileLock 실패는 FAULT로 DesignCraft만 차단하며 공통 Spring startup을 실패시키지 않는다.

동일 state-dir에 두 번째 JVM은 락 획득 실패/FAULT. 다른 디렉터리·서버의 인스턴스는 막지 못하므로 `fleetSafeToReplace=false`를 고정한다. 운영 토폴로지·별도 Worker·실제 OS CLI inventory 미확인 상태에서는 **운영 적용/교체 불가**로 판정한다. status의 singleProcessDrained는 이 인스턴스의 추적 범위만 뜻한다.

## 격리 검증 범위
신규 테스트만 `bash gradlew test --tests '*PublishingMaintenance*Test' --no-daemon --max-workers=2`로 실행한다. DB/AWS/Muse/실제 DesignCraft CLI는 사용하지 않는다. 신규 lifecycle 테스트의 프로세스는 synthetic CLI stub 또는 짧은 sleep뿐이다.

Gate 경쟁·예외·비동기 완료/실행 전 취소/실행 중 취소·실제 CLI 종료·파일 쓰기 실패·두 번째 인스턴스·재시작, Spring MVC/실제 service advisor/transaction commit/Worker skip/ADMIN/출처 보호 및 다른 경로 통과를 검증한다. 타 서비스 검증은 경로 경계 테스트이며 실제 장부/Builder/TESTPREP 업무 QA를 재실행한 것은 아니다.

검증 완료: [isolated run 38880273850](https://github.com/woori73/feel2real-buildit/actions/runs/38880273850), tested source head `9b397aa1dc06cb973094bcbb8c56fe3afb1d1e37`. Gate 단위 8건 + Spring MVC/service/transaction 통합 10건 = **18 PASS / failure 0 / error 0 / skipped 0**. 집계는 `test-results.json`, 원본 JUnit/HTML은 해당 실행 artifact에 보존한다.

로컬은 Java 네트워크/인증서 프록시 제약으로 Gradle 의존성 확보 단계에서 중단돼 테스트가 실행되지 않았다. TLS 검증을 끄지 않고 격리 GitHub runner에서 신규 범위만 검증했다. 초기 Spring 7 타입/Mock async 처리/timeout 하네스 문제는 수정 후 최종 실행에서 통과했다. PR 자동 CI가 발견한 인증 경계 2건(비회원 401 및 학생의 기존 소유자 격리 404까지 gate 503이 앞섬)을 수정했다. admission filter를 보안 필터 뒤(order 0)로 이동하고, gate 차단 중 TUTOR·ADMIN 외 로그인 사용자는 서비스 조회 없이 403으로 거부한다. OPEN의 기존 인증·소유자 정책에는 새 역할 제한을 추가하지 않는다. Boot 4 기본 보안 필터 order -100을 사용하며 이 설정을 바꾸면 순서 재검증이 필요하다. 신규 인증 경계 테스트를 추가했고 matrix URI는 보안 firewall의 400과 gate의 경로 매칭을 별도로 확인한다. 최종 문서·결과 집계 commit은 이 source head의 제품/테스트 코드를 바꾸지 않는다.

최신 main `3cb035f947612b8752fb6335779fef39aca6dac3` 재확인(분기 이후 변경 없음). #715/#716은 변경/병합하지 않았다. #716과 이 PR은 `DesignCraftCliExecutor`의 프로세스 시작 부근에 접점이 있으므로 어느 쪽이 먼저 병합되면 후속 PR에서 실제 충돌과 process tracking 1줄 유지 여부를 다시 확인해야 한다.

## 운영 적용과 영향 (미승인, 실행 안 함)
1. OIDC는 Claude 작업 범위다. 이 PR에서 IAM/OIDC를 수정하거나 복구를 시도하지 않음.
2. 코드 병합·운영 앱 배포·Spring 재시작을 별도 승인받고, persistent state-dir를 서비스 사용자 소유 0700으로 준비한다. 상태 디렉터리 생성 자체도 운영 승인 후 수행한다.
3. 최초 gate 배포 시 공통 Spring 재시작 영향이 있으므로 BuildIt 전체 유지보수 승인이 필요. 이 PR이 자동 배포를 추가하지는 않는다.
4. 시작 CLOSED 상태/ADMIN 인증 확인, 정상 운영 재개는 명시적 resume. 이후 교체 승인 시 drain으로 DesignCraft API 전체(미리보기/다운로드 포함)를 잠시 503, Worker 새 시작 skip. /ledger /builder /testprep /photocraft /로그인 /health 등은 gate 대상 밖이다.
5. 기존 작업 종료·추적 CLI 0과 외부 OS CLI/전체 인스턴스·대기 작업 집계의 읽기 전용 확인. 대기 상태/attempt/문서·리비전은 관리 명령으로 수정하지 않는다.
6. 180초 내 drain이 안 되거나 external inventory가 불명확하면 교체 금지. 자동 강제 종료/DB 보정/자동 resume 없음.
7. 별도 CLI 설치 승인 이후에만 이미 제출된 SHA/백업/canary/원자 전환 계획 적용. 파일 canary와 실제 Spring 사용자 흐름 검증을 혼동하지 않는다.
8. 성공 후 ADMIN resume. 실패하면 차단 유지, 별도 승인된 binary 복구와 검증 후 resume 여부 결정. 기존 0.6 CLI는 glibc 비호환이므로 단순 롤백이 정상화를 보장하지 않는다.

Gate 코드 자체 롤백은 이전 JAR 복원과 Spring 재시작이므로 별도 운영 승인 필요. 이전 앱에는 gate가 없으므로 롤백하면 차단 통제도 사라진다. 바이너리 전환 도중 앱을 무심코 롤백하지 말고, 기존 경로/파일 상태 복구 및 신규 호출 통제까지 포함한 순서로 승인받아야 한다. 유지보수 상태파일을 삭제해 강제로 재개하지 않는다.

## OPEN TTL (시간 제한) — 영구 보호
`resume()` 후 gate가 스스로 닫히는 경로가 없어 B안 테스트는 서버 측 watchdog(임시)에 의존했다. 이를 코드로 대체한다.
- `publishing.maintenance.open-ttl-seconds`(env `PUBLISHING_MAINTENANCE_OPEN_TTL_SECONDS`) 기본 **1800초**. `0`(무제한)은 명시 설정 시에만 허용하고 빈 값·음수·비숫자는 1800.
- 만료 = ADMIN `drain`과 같은 경로(`startDrain`): 신규 admission 즉시 503, 진행 중 operation/추적 CLI는 끝까지 수행 후 CLOSED. 취소·삭제·FAILED 처리 없음.
- 판정 위치: `enter()`·`status()`(지연 판정, 요청 시점에 항상 반영) + `@Scheduled tick()` 5초(요청이 없어도 상태 파일이 CLOSED). 모두 gate 모니터 안에서 직렬화.
- deadline은 메모리 전용(`System.nanoTime`). 재시작은 항상 CLOSED이므로 저장하지 않는다. 수동 drain은 deadline을 지우고 resume은 새로 계산한다.
- `Status.openRemainingSeconds`(올림, OPEN+TTL일 때만, 아니면 null)를 ADMIN 상태 응답에 노출.
- 테스트: `PublishingMaintenanceGateTtlTest`(가짜 시계 단위 12건 + 경쟁 2건: 만료와 동시 진입 8스레드, tick/drain/enter/status 동시 호출) 및 `PublishingMaintenanceIntegrationTest`(ADMIN 응답, 설정값 해석).
