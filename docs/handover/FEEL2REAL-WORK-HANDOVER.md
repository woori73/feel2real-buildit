# FEEL2REAL 작업 인계 (2026-10-20 갱신)

> 이미 끝난 작업은 반복하지 않는다. 아래 "미검증·미해결"만 이어서 처리한다.

## 현재 운영 상태 (신규 AWS 계정 425805163960)
- **운영 배포 SHA: `3f07c144`** — Deploy to EC2 run 39323583313, `target=new`, 성공. main = `3f07c144`, main Build Check 성공.
- 배포 이력(홈 관련, 모두 신규 계정): #793·#794 `3fe2aea7` → #796 `81da6216` → #812 `d1f09245` → #813 `bbfb647d` → #814·#815 `3f07c144`.
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
- 통과: PC 1440·모바일 390·360 — 확정 배너(모바일은 `-m.webp`), Editorial 배경·버튼·카드, 로고·로그인·회원가입 버튼 겹침/줄바꿈 없음, 가로 스크롤 없음, 의뢰 버튼 높이 48px, 이메일 주소 표시, CHART 실제 5건, 로그인 창 열림, 푸터, EDUNOVA 표기 없음. 비로그인 `/dashboard` → 302 로그인 안내, `/api/dashboard/widgets`·`/api/easy/workspace` → 401.
- 계정 메뉴: **로그인 상태를 시뮬레이션**(브라우저에서 `/api/auth/me` 응답만 대체, 서버 영향 없음)해 메뉴 열림·항목(강사 홈/프로필/로그아웃)만 확인. **실제 로그인 검증 아님.**
- 폰트 차단(Google Fonts 요청 abort) 상태에서도 로고 글자 표시·겹침 없음(대체 폰트 렌더 확인).

## 미검증·미해결
1. **ALB 일시 503 — 원인 미확인.** 2026-10-19 ~21:08 UTC(배포 완료 약 5분 뒤) 운영 검증 중 연속 11개 요청(정적·API·화면 혼합, 본문 98바이트)이 모두 503이었고 곧 정상화. 재현 시도(2회, 62건, 최대 14 동시) 503 없음. 신규 호스트 `app-diag`(읽기 전용, run 41): 서비스 active, 앱 재시작은 배포 시점뿐(PID 114913 21:01 시작 후 유지), OOM·Killed·APPLICATION FAILED 없음, 메모리 가용 약 1.8GB/3.8GB. **ALB 대상 상태 이력·CloudWatch(HTTPCode_ELB_503_Count, UnHealthyHostCount, TargetResponseTime, EC2 CPU/CPU 크레딧)·ALB 액세스 로그는 미조사** — 기존 워크플로에 해당 단계가 없고 구 계정을 가리키는 진단 워크플로(`diagnose-*`, `prod-diagnostic`, `ec2-diagnose`: role `067573916809`)는 사용 금지. 샌드박스 egress 게이트웨이가 만든 503일 가능성도 배제하지 못함(미확인). 정상 현상이라고 단정하지 말 것.
2. **실제 로그인(기존 인증 세션) 검증 미수행** — 환경에 테스트 계정 정보가 없어 ADMIN·TUTOR 로그인 후 메뉴·권한(`/dashboard`, `/ledger`, `/easy`, `/admin/*`, AcademyCraft 링크, 입시상담 AI)을 확인하지 못함. 코드 수준(MockMvc `EasySecurityWebMvcTest`)에서는 TUTOR/ADMIN/STUDENT/익명 매트릭스 통과. 사용자가 세션 확인 또는 검증 전용 계정 제공 필요.
3. **공식 도메인 `feel2real.co.kr` 접근:** 샌드박스에서는 CONNECT 403 — 프록시 상태 기록에 `example.com`·`www.naver.com`도 같은 방식(게이트웨이 403)으로 거절됨 → 작업 환경의 egress 허용 목록 제한으로 판단. 서비스 장애 증거는 없음(ALB는 정상 응답, 사용자가 휴대폰 화면으로 문제를 보고했으나 접속 주소는 확인하지 않음). 그러나 도메인→신규 ALB 경로를 외부에서 직접 확인한 것은 아님 → 사용자 PC/휴대폰에서 최종 확인.
4. 실기기(Android) 확인, 카톡·메일 버튼 실제 클릭 동작, 네이버 블로그 실제 열림(샌드박스에서 네이버 차단) 미검증.
5. 사용자 "정상" 확인 후 구 AWS 과금 리소스 종료, 신규 RDS gp3/백업 7일, 임시 RDS·스냅샷·관리자 역할 정리(명시 허가 전 금지).
6. GPU: 11/1 개통 후 `docs/gpu-pipeline/GPU_PIPELINE_PLAN.md` 순서. AcademyCraft 연결은 별도 배포+인증 연동 결정 필요(임의 링크 금지).

## 신규 AWS 배포 절차
병합 후 Actions `deploy-ec2.yml` ref=main, input `target=new`(나머지 `unchanged`) → 완료 확인 → ALB 주소에서 확인(샌드박스는 `feel2real.co.kr` 차단, ALB는 인증서 호스트 불일치라 `curl -k`/`ignoreHTTPSErrors`). 읽기 전용 신규 계정 점검: `migration-infra-build.yml` step=`app-diag`·`validate`·`muse-check`. 구 AWS 변경·종료, 운영 DB 삭제·변경 금지. 요청을 짧은 시간에 연달아 보내면 503이 섞일 수 있으니(위 1번) 검증은 간격을 두고 한다.
