# A1 블루·그린 무중단 배포 설계 (설계·사전 점검 단계 — 적용 전 승인 필요)

> 상태: **설계 + 읽기 전용 사전 점검 도구만 포함.** 트래픽 전환·기존 앱 종료·운영 DB 변경·새 타깃 그룹·SG/IAM 변경은 별도 승인 전까지 하지 않는다.
> 배경: 2026-10-22 06:24:57~06:25:46 UTC 약 50초 502 — 같은 포트 중지→교체→재시작 구간(단일 앱, 같은 서버).

## 1. 방식 (기존 EC2·ALB 재사용, 추가 EC2 없음)
- 블루=8080(현재 `f2r-app`), 그린=8083(새 유닛 `f2r-app-green`). 배포마다 색을 맞바꾼다.
- 새 JAR를 비활성 색 포트에서 기동 → 로컬 헬스·스모크 → 새 타깃 그룹(`f2r-app-tg-green`) healthy → ALB 443 리스너 **기본 동작만** 새 TG로 변경(원자적). 규칙 우선순위 1(`academy.feel2real.co.kr`→8082)은 건드리지 않는다.
- 이전 앱은 전환 후 5분(스트리밍 요청 상한 280초 + 여유) 유지 후 정상 종료. 문제 시 리스너를 되돌린다(수초).

## 2. 사전 점검 8항목과 현재 판단
| # | 항목 | 코드·설정 근거 | 판단 |
|---|---|---|---|
| 1 | 메모리·JVM 최대 사용 | `-Xmx2048m`, 호스트 7.8GB. 실측은 `a1-precheck`(free·cgroup·VmHWM·jcmd) | 실측 후 확정 |
| 2 | 두 JVM 동시 OOM 위험 | 힙 상한 2×2048MB + 메타스페이스·스레드·임베딩 서버(MemoryMax 2000M)·AcademyCraft·위키 | 게이트 `a1_mem_gate`(가용 ≥2500MB) 필수, 그린 `-Xmx`는 1536m로 낮추는 안 검토 |
| 3 | `@Scheduled` 16건 중복 | 아래 §3 | **그대로 중복 기동 금지** — 스위치 필요 |
| 4 | KIS 웹소켓 중복 | `WemadeKisWebSocket.@PostConstruct`가 키가 있으면 즉시 연결(스위치 없음) | 그린은 키 없이 기동하거나 코드 스위치 필요 |
| 5 | DB 풀·RDS 여유 | Hikari 기본(최대 10, yaml에 설정 없음), RDS max 60, 현재 18 | 중복 시 최대 +10 → 게이트 `a1_conn_gate`(≤40) |
| 6 | Flyway 동시 실행 | prd `flyway.enabled: true, validate-on-migrate`. Flyway는 DB 잠금으로 직렬화 | 같은 SHA면 no-op, **새 마이그레이션이 있으면 그린 기동이 곧 DB 변경** → 추가 전용(expand) 규칙 + 별도 승인 |
| 7 | ALB 기존 연결·스트리밍 | 전환은 새 요청만 새 TG로. 기존 연결은 이전 앱이 계속 처리 | 이전 앱 5분 유지, ALB idle timeout ≥ 300 확인 필요(precheck 출력) |
| 8 | 8083 외부 차단 | SG 인바운드 확인(`a1-precheck`). 그린 TG용 규칙은 **ALB SG에서만** 허용 | 0.0.0.0/0 노출 시 중단 |

## 3. 스케줄러·워커 중복 실행 위험 (코드 기준)
| 작업 | 위치 | 중복 시 위험 | 그린 대기 중 끄는 방법 |
|---|---|---|---|
| 퍼블리싱 워커(10초 폴링) | `PublishingWorker.poll/claimAndProvision` — `findById` 후 상태 변경, 행 잠금·@Version 없음(주석은 "비관적 락") | **같은 작업 이중 처리 가능** | `PUBLISHING_WORKER_ENABLED=false` |
| HWPX 워커(10초) | `HwpxWorker.claim` — 동일 패턴 | 이중 처리 가능 | `HWPX_WORKER_ENABLED=false` |
| 위메이드 수집(평일 16:30, 장중 10분) | `WemadeCollectionScheduler` cron | 중복 수집(DB 중복 적재 여부 미확인), KIS 호출 중복 | 평일 장중·16:20~16:50 KST 회피, 코드 스위치 추가 권장 |
| KIS 웹소켓 | 위 4 | 같은 키 이중 연결 → 한쪽 끊김 | 키 없는 그린(PENDING으로 비활성) 또는 스위치 |
| EASY 수집 02:30 | `easy.collection.cron` | RUNNING 체크 있으나 경합 여지 | `EASY_COLLECTION_CRON=-` |
| 차트 재조사 03:15 | `chart.re-research-cron` | 중복 LLM 호출 | `CHART_RE_RESEARCH_CRON=-` |
| 이벤트 보존 삭제 04:20 | `SiteEventRetentionJob` | 멱등 삭제 | 시각 회피 |
| 공식 업데이트 탐색 11:00/21:00 | 기본 `scheduler-enabled:false` | 없음 | 해당 없음 |
| 영상 정리·업로드 복구 | `transactions.claimNext/claimRecovery`(DB 선점) | 선점 방식이라 낮음 | 확인 완료 후 유지 |
| ALB 통계 워커 | `app.statistics.alb.enabled`(기본 false) | 없음 | 해당 없음 |
| 영상 최적화 | `optimization-enabled:false` | 없음 | 해당 없음 |
| 퍼블리싱 점검 게이트 tick | 프로세스 내 상태 | 프로세스별 독립 | 해당 없음 |

**핵심 한계**: 환경변수로 끈 그린은 전환 후에도 스케줄러가 꺼진 채라 최종 앱이 될 수 없다. 해결책(코드 변경·별도 PR·승인 필요): MySQL `GET_LOCK('f2r-leader')` 기반 **리더 게이트** — 스케줄 작업과 KIS 웹소켓이 리더일 때만 동작, 이전 앱이 내려가면 연결이 닫히며 잠금이 풀려 그린이 수 초 내 승계. 퍼블리싱·HWPX 선점은 `UPDATE … WHERE status='QUEUED'` 원자 갱신으로 바꾼다.

## 4. 헬스체크 권장 (현재: 30초 간격, 정상 2, 불량 3)
| 항목 | 현재 | 권장 | 이유 |
|---|---|---|---|
| interval | 30s | **10s** | 불량 판정 90s→30s, 그린 편입 60s→20s |
| timeout | (precheck로 확인) | **5s** (interval보다 작아야 함) | DB 점검 포함 health의 일시 지연 허용 |
| healthy threshold | 2 | **2** | 편입 20s |
| unhealthy threshold | 3 | **3** | GC·일시 지연 1~2회로 제외되는 플래핑 방지 |
| deregistration delay | (확인) | 구 TG **300s** | 스트리밍 280s 완료 |
- 앱 기동 ≈ 50s, graceful shutdown 30s. 헬스 경로 `/actuator/health`.
- **헬스체크 단축만으로 단일 앱 재시작 502는 없어지지 않는다**: 타깃이 1개뿐이면 ALB는 불량이어도 fail-open으로 보내고, 연결 거부는 즉시 502다. 단축은 감지·편입 시간만 줄이므로 A1 전환과 함께 검증한다. 변경은 TG 속성 수정이라 승인 후 적용.

## 5. 리허설(8083, 전환 없음) 조건 — 모두 충족해야 실행
1. `a1-precheck` 게이트 PASS(가용 메모리 ≥2500MB, 예상 DB 연결 ≤40, 8083 free, 8083 외부 노출 없음).
2. **같은 JAR(운영 SHA `2bed3918`)** 사용 → Flyway 대기 마이그레이션 없음(no-op).
3. 환경: `PUBLISHING_WORKER_ENABLED=false`, `HWPX_WORKER_ENABLED=false`, `EASY_COLLECTION_CRON=-`, `CHART_RE_RESEARCH_CRON=-`, KIS 키 미주입(또는 코드 스위치), `SERVER_PORT=8083`.
4. 시각: 평일 장중(09:00~15:40 KST)·16:20~16:50·02:20~04:30 회피.
5. 흐름: 기동 → 로컬 `/actuator/health` UP → 종료. ALB·SG·DB 변경 없음, 소요 ≤3분.
6. 기동 시 `ApplicationReadyEvent` 시드(`WemadeSeedService`, `ChartInitialPublicationRunner` 등)는 데이터 존재 확인 후 건너뛰는 멱등 구조이고 운영 앱이 재시작할 때마다 이미 같은 방식으로 실행된다(`WemadeSeedService.loadPrices`·차트 publication은 확인, 나머지 시드의 멱등성은 리허설 전 재확인). 알려진 잔여 위험: 기동 중 DB 연결 약 10개 추가, 2~4항 스위치를 놓치면 중복 실행.

## 6. 비용
A1: 추가 EC2 없음, 타깃 그룹·리스너 변경 무료, 헬스체크 증가분 무시 가능 → 월 약 $0. 서버 2대 롤링(C안)은 별도 승인.
