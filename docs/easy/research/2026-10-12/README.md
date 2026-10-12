# EASY 회계·세무 서비스 조사 산출물

기준일: 2026-10-12

1. [이지샵 기능 및 사용자 흐름 분석서](01_easyshop_feature_flow_analysis.md)
2. [기존 EASY 코드·DB 실사 보고서](02_easy_code_db_audit.md)
3. [이지샵 vs EASY 기능 비교표](03_easyshop_vs_easy_comparison.md)
4. [AI 회계·세무 서비스 확장 설계안](04_ai_accounting_tax_extension_design.md)
5. [개발 우선순위·예상 기간·비용 보고서](05_development_priority_schedule_cost.md)
6. [REA 분석 결과와 안전한 설정 절차](REA_ANALYSIS_AND_SETUP.md)
7. 상위 인수인계: [`../../EASY_WORK_HANDOFF.md`](../../EASY_WORK_HANDOFF.md)

## 조사 제한

- 조사 단계에서 운영 DB 변경, GitHub 코드 수정, PR/배포를 하지 않았다.
- REA 설치·MCP 등록·유료 결제를 하지 않았다.
- 외부 서비스에 실제 사업자·금융정보를 입력하지 않았다.
- 이지샵은 사용자가 허가한 정상 계정으로 조회만 했으며 결제·수집·신고 제출·설정 변경을 하지 않았다.
- 로그인 자격정보, 쿠키, 세션, API 키, 실제 사업자·금융 데이터는 이 폴더에 포함하지 않았다.
- 운영 feel2real EASY 화면은 조사 환경에서 실제 렌더링이 제한되어 정확한 경로와 권한은 GitHub 코드로 확인했다.

## 판정 원칙

- 화면에서 직접 확인한 내용, 공식 문서로 확인한 내용, 기술적 추정을 구분한다.
- 코드에서 확인되지 않은 기능은 구현 완료로 판단하지 않는다.
- 시간에 따라 바뀔 수 있는 요금·법령·외부 API 조건은 후속 착수 시 공식 출처에서 다시 확인한다.


