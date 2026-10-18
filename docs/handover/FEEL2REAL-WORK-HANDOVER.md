# FEEL2REAL 작업 인계 (2026-10-18)

## 브랜치·PR
- 브랜치 `feat/home-edunova` → PR #793 (홈 개편). 최신 커밋 SHA는 PR 화면 기준(이 문서 커밋이 마지막일 수 있음).
- 운영 배포 SHA: **미배포** (아래 갱신 칸).

## 완료 (반복 금지)
- AWS 신규 계정(425805163960) 전환·DNS 전환 완료, 롤백 경로 검증 완료. 신규 ALB가 운영 트래픽 처리.
- 홈 신규 디자인(`templates/public/landing.html`, `static/css/landing_edunova.css`, `static/images/ai-agent-hero.jpg`): 메뉴 6개, 공지, 회사소개, 카드 3종.
- **예상문제 의뢰 = 기존 카톡(open.kakao.com/o/sMf4ifRi) + 이메일(woorinaesin@naver.com) 재사용.** 자체 접수 폼/DB(V1090)는 만들었다가 사용자 지시로 제거함 — 다시 만들지 말 것.
- 대시보드·관리자 허브: DesignCraft/기출분석 노출 제거, 의뢰 링크는 카톡. (`/exam-analysis`·`/designcraft` 라우트·데이터는 유지)
- 제작기(`tutor/academy_editor.html`): 초기화(작업 초안만 비움, 게시본 유지)·홈으로·제목 링크 `/`; 빌더(`academy_builder.html`) 로고·홈으로.
- 구 랜딩 계약 테스트 교체(`LandingEdunovaTemplateContractTest`), 카탈로그/브랜딩 테스트 조정.
- 기존 실패(main에서도 동일): `TemplateBrandingTest` 2건, JS 39건, MySQL 필요 테스트.

## 미완료
1. PR #793 CI 확인 → 병합
2. `deploy-ec2.yml` `{"target":"new"}` 실행 → 신규 ALB에서 확인
3. PC(1440)·모바일(390) 스크린샷, `/dashboard`·`/ledger`·`/wiki`·`/builder` 접근 확인 (샌드박스는 feel2real.co.kr 차단 → ALB 호스트 사용)
4. 제작기 초기화·저장·미리보기·게시 실브라우저 검증(로그인 필요 → 미검증 항목으로 보고)
5. 사용자 "정상" 확인 후 구 AWS 과금 리소스 종료, 신규 RDS gp3/백업 7일, 임시 RDS·스냅샷·관리자 역할 정리
6. GPU: 11/1 개통 후 `docs/gpu-pipeline/GPU_PIPELINE_PLAN.md` 순서

## 신규 AWS 배포 상태
- 앱·위키는 신규 계정에서 운영 중(전환 완료). 이번 홈 개편은 **아직 배포 전**.

## 다음 작업 순서
CI green → 병합 → deploy(new) → 검증·캡처 → 최종 보고(배포 SHA, 완료/미검증 항목) → 구 AWS 정리 승인 대기.
