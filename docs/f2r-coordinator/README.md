# F2R AI Coordinator V1 (설계 단계)

Galaxy Z Fold6에서 음성·문자·사진으로 지시하면, 지정한 PC의 Claude Code·Codex와 F2R 서버가 함께 일을 처리하는 시스템의 조사·설계 문서다.
**구현·DB 변경·배포는 아직 하지 않았다.** 운영 배포는 별도 승인 후 진행한다.

| 문서 | 내용 |
|---|---|
| [F2R_COORD_Audit.md](F2R_COORD_Audit.md) | 기존 시스템 조사(재사용 가능 기능, 빈틈, 결정 필요 사항) |
| [F2R_Subscription_Verification.md](F2R_Subscription_Verification.md) | **기존 구독 활용 범위 검증(MCP 우선) — 1·2·3차 단계 근거** |
| [F2R_Coordinator_Architecture.md](F2R_Coordinator_Architecture.md) | 최종 설계: 구성·상태 머신·데이터·API·과금 구분·비용·보안 |
| [F2R_MCP_Tools.md](F2R_MCP_Tools.md) | `f2r_*` MCP 도구 10종 명세 |
| [mobile/F2R_Mobile_MCP_Design.md](mobile/F2R_Mobile_MCP_Design.md) | **Mobile MCP 기술 설계서 + Android↔PC MCP 연동 구조도**(별도 Gateway 서버 없이 기존 서버 안 모듈) |
| [mobile/F2R_Mobile_Auth_Security.md](mobile/F2R_Mobile_Auth_Security.md) | 모바일 인증·보안 설계 |
| [mobile/F2R_Mobile_MCP_Tools.md](mobile/F2R_Mobile_MCP_Tools.md) | `mobile_*` 7종 + PC·모바일 공통 도구 명세 |
| [mobile/F2R_Mobile_Integration_Tests.md](mobile/F2R_Mobile_Integration_Tests.md) | 모바일 통합 테스트 계획 M1~M10(**미실행**) |
| [mobile/F2R_BusinessPlan_Q1-Q4_Revision.md](mobile/F2R_BusinessPlan_Q1-Q4_Revision.md) | 1R 사업계획서 Q1~Q4 개정 문안 |
| [mobile/F2R_Budget_Schedule_Revision.md](mobile/F2R_Budget_Schedule_Revision.md) | 수정 예산(총액 고정)·추진 일정 |
| [F2R_PC_Agent.md](F2R_PC_Agent.md) | Windows PC 실행 프로그램 설계 |
| [F2R_Android_App.md](F2R_Android_App.md) | Android 앱 설계 |
| [F2R_Integration_Tests.md](F2R_Integration_Tests.md) | 통합 시험 계획·기록 양식(**미실행**) |
| [F2R_Deployment_Guide.md](F2R_Deployment_Guide.md) | 설치·배포 절차(구현 후) |
