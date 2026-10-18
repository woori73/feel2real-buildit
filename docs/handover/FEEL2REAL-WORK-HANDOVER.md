# FEEL2REAL 작업 인계 (2026-10-18)

## 브랜치·PR
- PR #793(홈 개편)·#794(히어로 잘림·모바일 EDUNOVA 표기) 병합 완료.
- **신규 AWS 운영 배포 SHA: `3fe2aea7`** (deploy-ec2 run 39141856162, target=new, 성공).

## 완료 (반복 금지)
- AWS 신규 계정(425805163960) 전환·DNS 전환 완료, 롤백 경로 검증 완료. 신규 ALB가 운영 트래픽 처리.
- 홈 신규 디자인(`templates/public/landing.html`, `static/css/landing_edunova.css`, `static/images/ai-agent-hero.jpg`): 메뉴 6개, 공지, 회사소개, 카드 3종.
- **예상문제 의뢰 = 기존 카톡(open.kakao.com/o/sMf4ifRi) + 이메일(woorinaesin@naver.com) 재사용.** 자체 접수 폼/DB(V1090)는 만들었다가 사용자 지시로 제거함 — 다시 만들지 말 것.
- 대시보드·관리자 허브: DesignCraft/기출분석 노출 제거, 의뢰 링크는 카톡. (`/exam-analysis`·`/designcraft` 라우트·데이터는 유지)
- 제작기(`tutor/academy_editor.html`): 초기화(작업 초안만 비움, 게시본 유지)·홈으로·제목 링크 `/`; 빌더(`academy_builder.html`) 로고·홈으로.
- 구 랜딩 계약 테스트 교체(`LandingEdunovaTemplateContractTest`), 카탈로그/브랜딩 테스트 조정.
- 기존 실패(main에서도 동일): `TemplateBrandingTest` 2건, JS 39건, MySQL 필요 테스트.

## 미완료
1. (완료) 병합·배포·PC/모바일 캡처·`/ledger`·`/wiki`·`/builder` 200 확인
2. 제작기 초기화·저장·미리보기·게시 실브라우저 검증(로그인 필요 → 미검증 항목으로 보고)
3. 사용자 "정상" 확인 후 구 AWS 과금 리소스 종료, 신규 RDS gp3/백업 7일, 임시 RDS·스냅샷·관리자 역할 정리
4. GPU: 11/1 개통 후 `docs/gpu-pipeline/GPU_PIPELINE_PLAN.md` 순서

## 신규 AWS 배포 상태
- 홈 개편 포함 `3fe2aea7`이 신규 계정에서 운영 중. 미검증: 로그인 필요 화면(/dashboard 내용, /ledger 내용, 제작기 초기화·저장·게시).

## 다음 작업 순서
로그인 상태 수동 확인(사용자) → "정상" 확인 시 구 AWS 정리(3번) → GPU(11/1).

## 2026-10-18 후속: PR #796 (운영 화면 최종 수정)
- 브랜치 `feat/ops-final`, head SHA는 PR #796 화면 기준(푸시 완료·로컬 변경 없음). base main `27514265`(#795 병합 포함).
- 운영 배포 SHA: 마지막은 `3fe2aea7`(홈). **#796은 CI 통과 후 병합→미배포**.
- 완료(코드, #796): 홈 3번째 카드 EduNova+COMING SOON(비링크) / 대시보드 카드 6개(상담신청 제거, 로고→/builder/entry) / 에듀홀릭 장부=/easy, 이지장부=/ledger 명칭 / /ledger AI 모바일(마스코트 크기·키보드 입력창) / `POST /api/dashboard/assistant`(Muse Spark 1.3 Contributor, 읽기 전용) + 대시보드 AI 패널 / `muse-check` 점검 단계.
- 미완료: ① #796 CI 확인→squash 병합 ② 신규 AWS 배포 ③ 배포 후 `migration-infra-build.yml` step=`muse-check` 실행(Muse 실제 응답·키 존재 확인) ④ 신규 ALB PC/모바일 캡처 ⑤ 실기기 Android 확인(미검증).
- AcademyCraft: 별도 앱(`woori73/academycraft`, 인증키 로그인), BuildIt/신규 AWS에 미배포 → 대시보드엔 비링크 "준비 중". 연결하려면 별도 배포+인증 연동 필요(임의 링크 금지).
- 테스트: DashboardAssistantServiceTest 4, LandingEdunovaTemplateContractTest, AdminTopnav 통과. 모바일 하네스(360/390/430, 키보드 330px 모사) 통과. 기존 실패(main 동일): TemplateBrandingTest 2, JS 39, DB 필요 테스트.
- 신규 AWS 배포 절차: 병합 후 Actions `deploy-ec2.yml` ref=main, input `target=new` → 완료 확인 → ALB `https://f2r-alb-2551937.ap-northeast-2.elb.amazonaws.com`(샌드박스는 feel2real.co.kr 차단)에서 확인. 구 AWS 변경·종료, 운영 DB 삭제 금지.
