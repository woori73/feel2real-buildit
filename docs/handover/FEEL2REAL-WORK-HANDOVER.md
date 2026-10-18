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
