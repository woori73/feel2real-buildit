# FEEL2REAL 작업 인계 (2026-10-20 갱신 2)

> 이미 끝난 작업은 반복하지 않는다. 아래 "미검증·미해결"만 이어서 처리한다.

## 현재 운영 상태 (신규 AWS 계정 425805163960)
- **현재 운영 SHA: `5343d785`** — Deploy to EC2 run 39344173037(2026-10-19 23:51 UTC 시작, `target=new`, 성공). **PR #816(구글 서치콘솔 소유확인 메타태그 추가, 커밋 `2cd93021`, 병합 `5343d785`)의 배포와 연결된 변경**이다. 변경은 `templates/layouts/public.html` 2줄(주석+메타태그)뿐이며(`3f07c144..5343d785` diff 확인), 운영 홈에서 `google-site-verification` 메타태그가 확인됨(값은 문서에 적지 않음).
- 직전 운영 SHA는 `3f07c144`(run 39323583313, #814·#815 배포). 같은 SHA의 재배포 run 39341504078(23:25 UTC)도 있었다. 이 두 배포(run 39341504078, 39344173037)는 이 Claude 세션이 실행하지 않았다. 워크플로 실행 계정이 Claude 작업과 같아 실행 주체는 구분할 수 없다.
- main 최신(`9c570df3`)은 운영보다 앞서 있으나 앞선 변경은 문서(#817 `1c2ec1ff`)와 읽기 전용 진단 CI 단계(#818 `9c570df3`)뿐이라 **앱 재배포는 하지 않았다.** 운영 앱 코드 = `5343d785`.
- 배포 이력(홈 관련, 모두 신규 계정): #793·#794 `3fe2aea7` → #796 `81da6216` → #812 `d1f09245` → #813 `bbfb647d` → #814·#815 `3f07c144` → #816 `5343d785`.
- 포함된 PR: #814(비회원 `/dashboard` → 로그인 안내 `/?login=required&returnUrl=…`, 이메일 주소 표시·복사 안내) 병합 `00f8f644`, #815(AI AGENT 확정 배너 + Cinematic Editorial 디자인 + 상단 로고) 병합 `3f07c144`.
- 신규 ALB: `https://f2r-alb-2551937.ap-northeast-2.elb.amazonaws.com` (공식 도메인은 샌드박스 프록시가 막음 — 아래 참고).

## 완료 (반복 금지)
- AWS 신규 계정 전환·DNS 전환 완료, 롤백 경로 검증 완료. 신규 ALB가 운영 트래픽 처리. 구 AWS는 변경·종료하지 않았다.
- 홈(`templates/public/landing.html`, `static/css/landing_edunova.css`): 순서 = 히어로(확정 배너 `ai-agent-banner.*`, 1회 20,000원·40문항) → 공지 → 적중 성과(블로그 링크) → 소개 → 서비스 카드 3종 → BUILD IT CHART(마지막, 실제 `/api/chart` 데이터).
- 디자인 확정: Cinematic Editorial(웜 화이트 `#faf8f4`, 스톤 `#efebe4`, 차콜 `#1f2328`, 스틸 블루 `#3f5b78`). 명조는 큰 제목·주요 숫자만, 버튼 6~8px. 다크 테마(`001fdfbc`)는 되돌림 — 다시 쓰지 말 것.
- 상단: F2R 로고 이미지(`/img/F2R_logo_pure.svg`) + `FEEL2REAL`(Google Fonts **Russo One**, 실패 시 Montserrat→고딕) + `BuildIt`(1280px 이하에선 숨김). 표기는 BuildIt으로 통일(EDUNOVA 삭제), 3번째 카드명은 "AI 콘텐츠 제작"(COMING SOON). CSS 파일명 `landing_edunova.css`·테스트 클래스명은 유지(변경 금지 지시).
- **예상문제 의뢰 = 기존 카톡(open.kakao.com/o/sMf4ifRi) + 이메일(woorinaesin@naver.com).** 자체 접수 폼/DB는 사용자 지시로 제거함 — 다시 만들지 말 것.
- 대시보드: 카드 6개, 이지장부=`/ledger`, 에듀홀릭 장부=`/easy`, 대시보드 AI 비서(`POST /api/dashboard/assistant`, Muse Spark 1.3 Contributor, 읽기 전용). AcademyCraft는 별도 앱·미배포라 비링크 "준비 중".
- 기존 실패(main에서도 동일): `TemplateBrandingTest` 2건, JS 39건, MySQL 필요 테스트.

## 운영 검증 결과 (2026-10-19, `3f07c144`, ALB 기준)
- 이후 `5343d785`(#816) 배포는 메타태그만 추가했다. 2026-10-20 ALB 확인: 홈 200, CSS `v=20261020-8`·`FEEL2REAL` 로고 유지(배너·디자인 유지). 아래 검증 항목은 `3f07c144` 기준이며 #816 이후 전체를 다시 돌리지는 않았다.
- 통과: PC 1440·모바일 390·360 — 확정 배너(모바일은 `-m.webp`), Editorial 배경·버튼·카드, 로고·로그인·회원가입 버튼 겹침/줄바꿈 없음, 가로 스크롤 없음, 의뢰 버튼 높이 48px, 이메일 주소 표시, CHART 실제 5건, 로그인 창 열림, 푸터, EDUNOVA 표기 없음. 비로그인 `/dashboard` → 302 로그인 안내, `/api/dashboard/widgets`·`/api/easy/workspace` → 401.
- 계정 메뉴: **로그인 상태를 시뮬레이션**(브라우저에서 `/api/auth/me` 응답만 대체, 서버 영향 없음)해 메뉴 열림·항목(강사 홈/프로필/로그아웃)만 확인. **실제 로그인 검증 아님.**
- 폰트 차단(Google Fonts 요청 abort) 상태에서도 로고 글자 표시·겹침 없음(대체 폰트 렌더 확인).

## ALB 진단 결과 (신규 계정, `alb-diag` run 42, 구간 2026-10-19 20:30~21:30 UTC, 읽기 전용)
- 실행: `migration-infra-build.yml` step=`alb-diag`(#818, `scripts/operations/alb-diag-lib.sh`). 조회만 수행, 구 계정(`067573916809`) 진단 워크플로는 사용하지 않았다.
- **ALB 503: 0건.** **ALB 5xx: 2건**(21:00·21:01 UTC 각 1건, TargetConnectionError도 같은 분에 각 1건 — 제 배포로 `f2r-app`이 중지(21:00:31)→기동(21:01:13)되던 시간과 일치. 단일 대상이라 배포 때마다 약 40초 연결 오류 가능). 대상 5xx 0, 거부 연결 0.
- **두 대상 그룹(`f2r-app-tg` 8080, `f2r-academycraft-tg` 8082) healthy**: 구간 내 HealthyHostCount 최소 1, UnHealthyHostCount 최대 0.
- EC2 `i-0414de33d2803c723`(t3.medium, CPU 크레딧 `unlimited`): CPU 평균 약 3%·5분 최대 51.6%(21:05 구간 순간 상승), **CPU 크레딧 소진 없음**(잔액 576 고정, 추가 크레딧 청구 0), 상태 점검 실패 0.
- 호스트(SSM, 현재 값): **메모리 여유(available) 약 1.7GB/3.8GB**, 로드 0.27, 디스크 13%, 구간 내 커널 OOM 없음, TCP listen overflow 누적 85회(서버 부팅 이후 합계 — 발생 시각 불명, 원인 미확인).
- ALB 설정: idle timeout 180초, 접근 로그 꺼짐, 삭제 방지 켜짐. 대상 그룹 헬스체크 `/actuator/health` 30초 간격·타임아웃 5초(app-tg: 정상 2회/비정상 3회, deregistration delay 60초).
- 한계: 분 단위 RequestCount 목록은 출력 상한으로 21:07~21:09를 확인하지 못했다. 메모리는 과거 지표가 없다(CloudWatch 에이전트 없음).

## 미검증·미해결
1. **ALB 일시 503 — 원인 미확인(유지).** 2026-10-19 ~21:08 UTC(앱 로그 시각으로 추정) 운영 검증 중 연속 11개 요청(정적·API·화면 혼합, 본문 98바이트)이 모두 503이었고 곧 정상화. 재현 시도(2회, 62건, 최대 14 동시) 503 없음. 아래 "ALB 진단 결과"에서 같은 구간에 **ALB 503 0건·대상 5xx 0건**이어서 ALB·앱이 만든 503으로 기록되지 않았다. 작업 환경 프록시(egress 게이트웨이)가 만든 503일 가능성이 있으나 **증거 없는 추정**이다. 요청별 원인을 볼 ALB 접근 로그는 꺼져 있다(**사용자 승인 없이 켜지 말 것**). 정상 현상이라고 단정하지 말 것.
2. **실제 로그인(기존 인증 세션) 검증 미수행** — 환경에 테스트 계정 정보가 없어 ADMIN·TUTOR 로그인 후 메뉴·권한(`/dashboard`, `/ledger`, `/easy`, `/admin/*`, AcademyCraft 링크, 입시상담 AI)을 확인하지 못함. 코드 수준(MockMvc `EasySecurityWebMvcTest`)에서는 TUTOR/ADMIN/STUDENT/익명 매트릭스 통과. 사용자가 세션 확인 또는 검증 전용 계정 제공 필요.
3. **공식 도메인 `feel2real.co.kr` 접근:** 샌드박스에서는 CONNECT 403 — 프록시 상태 기록에 `example.com`·`www.naver.com`도 같은 방식(게이트웨이 403)으로 거절됨 → 작업 환경의 egress 허용 목록 제한으로 판단. 서비스 장애 증거는 없음(ALB는 정상 응답, 사용자가 휴대폰 화면으로 문제를 보고했으나 접속 주소는 확인하지 않음). 그러나 도메인→신규 ALB 경로를 외부에서 직접 확인한 것은 아님 → 사용자 PC/휴대폰에서 최종 확인.
4. 실기기(Android) 확인, 카톡·메일 버튼 실제 클릭 동작, 네이버 블로그 실제 열림(샌드박스에서 네이버 차단) 미검증.
5. 사용자 "정상" 확인 후 구 AWS 과금 리소스 종료, 신규 RDS gp3/백업 7일, 임시 RDS·스냅샷·관리자 역할 정리(명시 허가 전 금지).
6. GPU: 11/1 개통 후 `docs/gpu-pipeline/GPU_PIPELINE_PLAN.md` 순서. AcademyCraft 연결은 별도 배포+인증 연동 결정 필요(임의 링크 금지).

## 배포 중단 완화 0단계 (구현만 — 운영 배포 전)
- **적용한 것:** ① `application.yaml`에 `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s` 명시. **동작은 바뀌지 않는다** — Spring Boot 4.0.6 기본값이 이미 graceful(옛 jar에서도 "Commencing graceful shutdown" 확인)이라 "고정"일 뿐이다. ② 배포 로그에 `[DEPLOY] service_gap_seconds=N`(journald의 `Stopping f2r-app`→`Tomcat started` 간격) 출력 — health gate 통과 뒤·읽기 전용·`|| true`. ③ 테스트: `GracefulShutdownTest` 6건(CI 목록에 등록), `f2r_app_guard.test.sh`의 GAP·E11. 롤백·env 스냅샷·health gate 코드는 그대로.
- **공백은 두 구간이다.** (a) 종료 구간: 유휴면 약 0.5초(실측), 진행 중 요청이 있으면 최대 30초 대기 — graceful이 보호하는 부분. (b) 기동 구간: 새 JVM이 포트를 열 때까지 — 운영 실측 41초(2026-10-19 21:00:32→21:01:13). **graceful은 (b)를 줄이지 못한다.**
- **기동 시간 실측(로컬 4코어·로컬 MySQL, 운영과 같은 218MB jar, `-Xmx2048m`, SQL 로그 끔, Flyway 적용 완료 상태, 3회):** 현행 fat jar 27.7·30.8·30.7초(중앙 30.7) / jar 압축 해제 실행 22.5·22.8·24.4초(중앙 22.8, −26%) / 압축 해제+AppCDS 17.7·18.1·18.2초(중앙 18.1) / fat jar+AppCDS 23.2·23.8·23.9초. **로컬 값이라 운영(t3.medium+RDS)에 그대로 대입할 수 없다**(운영 41초 vs 로컬 fat jar 30.7초) — 실제 효과는 배포 로그의 `service_gap_seconds`로 확인.
- **기동 구간 구성(로컬 fat jar 약 29초):** JVM·컴포넌트 스캔 ~3초, JPA 저장소 211개 스캔·Tomcat 초기화 ~2초, **Flyway 약 0.4초(검증 0.07초 — 마이그레이션이 없으면 거의 무비용)**, Hibernate 초기화 ~7초, 저장소·서비스 빈 생성 ~13초. 빈 DB에서 마이그레이션 192건 적용은 9.7초 — **스키마 변경이 큰 배포는 그만큼 기동이 늘어난다**(앱 기동 시 `spring-boot-starter-flyway`가 자동 실행).
- **적용하지 않은 방법과 이유:** ① jar 압축 해제 실행(−26%): `ExecStart`·롤백(`app.jar.previous` 교체) 구조를 바꿔야 해서 호스트 변경+롤백 재설계가 필요 — 별도 승인·검증 후. ② AppCDS: **jar가 바뀌면(수정 시각만 달라져도) 아카이브가 거부**되어 배포 직후 첫 기동(공백이 생기는 그 기동)에 효과가 없었다(28.8초, 재시작해도 재생성 안 됨), 아카이브 생성 종료는 11.6초 지연. ③ `lazy-initialization`·JPA 지연 부트스트랩: `@Scheduled`·WebSocket 빈이 늦게 뜨거나 health가 DB 준비 전에 UP이 될 위험.
- **스케줄러·WebSocket 영향:** 현재는 `systemctl restart`(중지→시작, 순차)라 두 JVM이 겹치지 않아 **중복 실행은 없다.** 다만 실행 중인 작업은 재시작 때 끊긴다(기존과 동일). 분산 락(ShedLock 등)은 코드에 없다 — 인스턴스를 둘로 늘리는 설계(A·B안) 전에 필요. 실행 시각(KST): 02:30 장부 수집, 03:15 차트 재조사, 04:20 이벤트 정리, 11:00·21:00 뉴스타트 수집, 평일 09:10~15:50 10분마다·16:30 위메이드 수집, 퍼블리싱·HWPX 워커 5~10초 폴링 → 배포는 이 시각 ±10분과 평일 09:00~16:40을 피하는 편이 안전(예: 05:00~08:50). 열린 WebSocket 연결은 graceful 종료를 막지 않지만(테스트 검증) 재시작 때 끊기므로 클라이언트 재접속이 필요하다.
- **남은 위험:** 280초까지 가는 스트리밍(RAG 내보내기 등)은 30초 안에 끝나지 않으면 끊긴다. 위 실측은 로컬 값이다. 호스트 유닛은 생성 템플릿(`scripts/deploy/provision-new-host.sh`, `TimeoutStopSec` 미지정 = 기본 90초) 기준으로만 판단했고 **운영 호스트의 실제 유닛 파일은 직접 확인하지 못했다**(변경도 하지 않음).

## 신규 AWS 배포 절차
병합 후 Actions `deploy-ec2.yml` ref=main, input `target=new`(나머지 `unchanged`) → 완료 확인 → ALB 주소에서 확인(샌드박스는 `feel2real.co.kr` 차단, ALB는 인증서 호스트 불일치라 `curl -k`/`ignoreHTTPSErrors`). 읽기 전용 신규 계정 점검: `migration-infra-build.yml` step=`app-diag`·`alb-diag`·`validate`·`muse-check`(`alb-diag`는 입력 `diag_start`/`diag_end`로 구간 지정, UTC·24시간 이내). 구 AWS 변경·종료, 운영 DB 삭제·변경 금지. 단일 대상이라 배포 때마다 약 40초 연결 오류가 날 수 있다(ALB 진단 결과 참고). 요청을 짧은 시간에 연달아 보내면 503이 섞인 적이 있으니(미해결 1번) 검증은 간격을 두고 한다.
