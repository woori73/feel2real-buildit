# A1 중복 실행 방지와 독립 검수 인계

2026-10-22. 최신 main `930716ec` 기반 별도 개발 PR. 운영 변경·8083 기동 없음.

## 변경

Publishing/HWPX는 `UPDATE ... SET status='RUNNING' WHERE id=? AND status='QUEUED'` 변경 행 수 1인 작업자만 실행한다. bulk UPDATE 후 persistence context를 비워 오래된 QUEUED 엔티티를 재사용하지 않는다. Publishing은 프로비저닝 트랜잭션의 행 잠금을 유지하며 실패 시 시도 수/오류를 남기고 상한 이내 QUEUED, 상한 도달 FAILED. 외부 poll 예외 처리에서 다른 JVM의 RUNNING 작업을 실패로 덮어쓰던 경로는 제거했다. HWPX는 짧은 선점 커밋 뒤 CLI를 실행하므로 기존처럼 긴 DB 트랜잭션을 만들지 않는다.

`MysqlNamedLocks`는 풀과 분리된 **물리 JDBC 연결**을 소유한다. DB명을 해시한 namespace + 용도로 GET_LOCK(0), IS_USED_LOCK=CONNECTION_ID 확인, RELEASE_LOCK 후 물리 close. RELEASE 실패도 close하며 풀에 잠금 연결을 반환하지 않는다. 연결/소켓/쿼리 시간 상한 3초. 재접속은 새 물리 세션으로만 한다. 예외에 URL·DB명·사용자·서버 원문을 로그하지 않는다.

KIS는 @PostConstruct 즉시 연결을 제거하고 ApplicationReady 이후 별도 리더를 얻어 10초 대기 후 연결한다. 동일 JVM 중복 start 차단, 1초 watchdog, 연결 완료/onOpen/구독/수신 시 소유권 재검사, 상실 시 socket.abort→잠금 해제 순서. `wemade.kis.websocket-enabled`/`WEMADE_KIS_WEBSOCKET_ENABLED` **기본 false**. 운영 활성화는 KIS 실제 검수와 별도 승인 후.

위메이드/EASY/공식탐색/ALB 통계의 지정된 예약 진입점은 Spring 기존 프록시로 고정 스케줄 리더에서만 실행한다. 호출마다 잠금을 해제했다 재획득하지 않아 두 JVM이 같은 짧은 cron을 연속 실행하는 위험을 줄인다. 수동 업무 진입점은 바꾸지 않는다. 정상 종료는 실행 중 작업이 끝날 때까지 리더 세션을 유지한다. DB 연결 상실 후 새 예약 호출은 차단한다. 두 지속 리더 연결(KIS가 활성화될 때 최대 2개)이 앱 풀 외에 추가되므로 메모리/DB 연결 게이트에서 별도로 합산해야 한다.

## 16개 예약 작업 전수 분류

| # | 클래스.메서드 | 분류와 근거 |
|---|---|---|
| 1 | PublishingWorker.poll | DB 원자 선점. QUEUED 변경 행 수 1 |
| 2 | HwpxWorker.poll | DB 원자 선점. QUEUED 변경 행 수 1 |
| 3 | WemadeCollectionScheduler.collectAfterMarketClose | 리더 전용. 동일 수집/API 중복 방지 |
| 4 | WemadeCollectionScheduler.collectIntradayAnomalyMetrics | 리더 전용. 동일 파생 지표 중복 방지 |
| 5 | EasyCollectionSchedulerService.runScheduledCollections | 리더 전용. 기존 ingest/장부 흐름·승인 경계 유지 |
| 6 | ChartSiteToolService.markResearchDue | 중복 안전. RESEARCHED+기한 조건부 UPDATE. **LLM 호출 없음** |
| 7 | SiteEventRetentionJob.purge | 중복 안전. 보존 기한 기준 삭제 |
| 8 | PublishingMaintenanceGate.tick | 프로세스별 게이트 만료. 서로 독립이어야 함 |
| 9 | AcademyVideoAssetCleanupWorker.poll | 기존 DB 선점+lease token, claimNext/complete 검증 |
| 10 | AcademyVideoAssetCleanupWorker.reconcile | 기존 DB 행 잠금/cleanup enqueue. 기존 회귀 테스트 유지 |
| 11 | AcademyVideoUploadSessionService.recoverDueFinalizations | 기존 DB 비관적 잠금+recovery lease |
| 12 | AcademyVideoOptimizationWorker.poll | 기본 비활성+기존 DB 선점/lease token |
| 13 | AcademyVideoOptimizationWorker.heartbeatActiveAttempts | 기본 비활성+작업 lease token 갱신 |
| 14 | AlbAccessStatisticsWorker.poll | 기본 비활성. 활성화 시 스케줄 리더 전용 |
| 15 | NewStartOfficialUpdateDiscoveryService.scheduledDiscoveryMorning | 기본 비활성. 활성화 시 리더 전용 |
| 16 | NewStartOfficialUpdateDiscoveryService.scheduledDiscoveryEvening | 기본 비활성. 활성화 시 리더 전용 |

정확한 메서드명은 해당 소스의 @Scheduled 기준이다. 기존 기본 비활성 스위치를 켜지 않는다. KIS는 @Scheduled 16개 외 별도 리더 루프다.

## 검증과 남은 운영 조건

- 실제 MySQL 8.4, 서로 다른 JVM/세션: 동시 잠금 경쟁·정상 해제·프로세스 강제 종료·DB KILL CONNECTION·재접속·두 작업 테이블 원자 UPDATE 경쟁·트랜잭션 rollback 복구.
- 실제 HWPX 서비스: 두 작업자가 같은 ID를 처리할 때 CLI 1회/결과 1건, CLI 실패 FAILED, 입력 누락, 취소 보존. Publishing 실패 상한/성공 재시도 및 패자 무실행. 기존 maintenance gate 테스트 유지.
- 외부 KIS 실계정/네트워크 장애/긴 JVM 일시정지 검증은 미실시. GET_LOCK은 KIS 서버가 검증하는 fencing token이 아니다. DB 세션 해제 시점과 구 JVM 정지/재개 사이 절대적인 외부 연결 중복 방지를 이 단위 테스트만으로 보장하지 않는다. 10초 대기는 watchdog의 정상 지연 여유이며 무한 정지의 증명은 아니다. **실제 KIS 독립 검수 전 기본 OFF 유지 및 8083 리허설 금지.**
- 예약 작업은 durable exactly-once 실행 장부가 아니다. DB 장애 때 이미 실행 중인 외부 호출/트랜잭션을 강제로 롤백하지 않는다. 실제 장기 장애·재선출 시나리오를 Claude가 검증해야 한다. 운영 적용 가능 판정은 아직 아니다.
- HWPX의 선점 커밋 후 JVM이 죽으면 RUNNING 행은 보존된다. 타이머만 보고 자동 QUEUED로 돌리면 살아 있는 CLI를 중복 실행할 수 있어 자동 재선점하지 않는다. 소유자가 기존 취소 API로 취소하고 새 작업으로 재요청하며 기존 입력/결과/오류 행은 보존한다. 자동 crash 복구는 CLI 생존 확인/소유 토큰/lease 및 별도 검수가 필요하다.
- CI MySQL은 8.0이며 로컬은 8.4. 환경변수 F2R_CLUSTER_MYSQL_TEST=true와 localhost의 f2r_test/f2r_cluster_test 이름만 허용한다. 운영 DB는 사용하지 않는다.
- 새 서버/프레임워크/유료 AI 호출 없음. 기존 DB에 advisory lock 세션과 짧은 소유권 확인 쿼리만 추가한다. max_connections 실측에 합산 필요.

Claude 요청: 최신 HEAD CI, 16개 분류(영상 선점의 기존 경합 테스트 포함), 프록시/트랜잭션 순서, 잠금 물리 연결 종료, KIS 실패 전환을 독립 확인한다. 승인 전 main 병합·운영 배포·8083 리허설 금지.

로컬 결과: Java 31건/실패 0/스킵 0(2-JVM MySQL 5, 스케줄 리더 3, KIS 2, Publishing 3, HWPX MySQL 6, 기존 maintenance 12). 신규 CI step에서 2-JVM MySQL+HWPX를 실제 실행한다.
