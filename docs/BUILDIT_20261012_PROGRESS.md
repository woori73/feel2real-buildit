# BuildIt 개발 현황 및 작업 연속성 기록

- 기준일: 2026-10-12 (KST)
- 목적: 작업을 잊거나 중복 지시하지 않도록 현재 상태, 결정사항, 분업, 다음 작업을 한 문서로 관리한다.
- 갱신 규칙: 단계 완료마다 §8 로그에 행을 추가하고 §3·§5·§6을 실측으로 고친다. 이 문서의 **'확인'은 기록자가 실제로 실행·조회한 것만** 쓴다(운영 서버·GitHub 상태는 해당 행에 명시).
- 이 문서는 사용자가 제공한 `BUILDIT_20261012_PROGRESS.md`를 저장소로 가져와 클로드가 실측으로 갱신한 것이다.

## 1. 최종 사용자 목표
TUTOR가 BuildIt에 로그인해 **AI와 대화로 홈페이지를 처음 제작**하고, 화면에서 확인한 뒤 대화 또는 시각 편집기로 수정·승인·저장·게시하고 공개 URL로 접속할 수 있게 한다. 본부 ADMIN의 대행 제작도 유지한다.

## 2. 운영 중인 기능 (뮤즈 10/12 조사 기준 — 클로드가 운영 서버에서 재검증하지 않음)
| 기능 | 상태 | 비고 |
|---|---|---|
| TUTOR 로그인·학원별 관리 | 운영 중 | 기존 권한체계 |
| 섹션 빌더 / 디자인 프리셋 5종(EXAM·ENGLISH·ARTS·STUDY·ONLINE) / AI Guide | 운영 중 | 18개 섹션 수동 입력 |
| 기존 홈페이지 미리보기·저장·게시 | 운영 중 | 섹션 빌더 기준, 공개 `/academies/{slug}` |
| P1 AI 채팅 API | **운영에 없음**(배포 이력상 main만 배포됨) | §3 "Flyway 게이트" 참고 |
| 본부 ADMIN 대행 제작 | 운영 중 | 유지 |

## 3. 개발 상태 (실측, 브랜치 `buildit-visual-editor/prototype`, PR #601)
| 항목 | 상태 | 증거 |
|---|---|---|
| GrapesJS 시각 편집기 | **구현·검증 완료**, main 미병합 | 기존 E2E 63/63 |
| 저장·되돌리기·충돌 보호(미저장 내용 백업/복원) | **구현·검증 완료** | `e2e-proposal.js` 48/48 |
| Proposal 구현체(V1058 테이블, 검증, 저장 트랜잭션 승인) | **구현 완료**(클로드) | MySQL 통합 테스트(원자성·롤백·동시 승인·타 학원 404) 통과 |
| Agent 인증·API(`vea_` 토큰, 컨텍스트·제안 생성·조회) | **구현 완료** | 단위·통합·E2E |
| 제안 패널(미리보기·승인·거절·AI 수정 요청·토큰) 모바일 390px | **구현·검증 완료** | `e2e-agent-flow.js` 35/35 |
| **AI 최초 생성 → 편집기 초안** (A안) | **구현·검증 완료**(클로드) | `e2e-ai-create.js` PC 16/16 · 모바일 17/17 |
| 섹션 빌더(builderDraft) ↔ 시각 편집기 | 최초 import만 연결, 별도 동기화 없음 | 의도적 분리(공개는 `renderMode`+게시본 테이블) |
| PR #601 병합·배포 | 병합 `65634006`, **자동 배포 실패(Flyway V1055) → 자동 롤백**, 운영은 이전 버전 정상 | 아래 사고·수정 |

**AI 최초 생성 구현 방식(A안, 확정):** 기존 `AcademyAiCreateService`(`POST /api/tutor/academy/ai/chat/create`)가 돌려주는 프리셋·문구를 편집기가 받아 고정 id가 있는 첫 홈페이지(히어로·소개·특징 3카드)로 조립한다. 승인 전에는 숨은 편집기 미리보기만 보이고 서버 초안·섹션 빌더(builderDraft)·공개 응답은 바뀌지 않는다. 승인하면 편집기 내용으로 삼아 저장한다(저장 전에는 Ctrl+Z). 이후 수정은 기존 Proposal 흐름(AI 수정 요청·Agent 제안)이며 고정 id 덕에 요소를 정확히 가리킨다.

### Flyway 게이트 — 확인 결과와 사고(2026-10-12 05:45 UTC)
**사고:** PR #601 병합(`65634006`) 후 자동 배포(`Deploy to EC2` run 38209282392)가 `V1055`에서 실패했고 서비스는 **자동 롤백**되어 이전 버전으로 정상 가동 중이다(`service-status` 확인: active, 05:46:32 재기동). 배포 시도 중 약 2~3분간 재시작이 반복되어 **짧은 중단**이 있었다.
**원인(실측, 읽기 전용 진단 `flyway-recent`):** 운영 `flyway_schema_history`에는 version **1053이 다른 파일**(`V1053__academy_tenant_features_eduholic.sql`, 2026-10-10 적용)로 이미 있었다. `FlywayRepairConfig`의 `repair()`가 이 이력을 P1의 V1053 파일에 맞춰 덮어쓰고(설명·체크섬 정렬) V1053을 "적용됨"으로 취급 → P1의 V1053(`academy_page_versions` 등 생성)이 **실행되지 않았다.** V1054는 정상 적용(`academy_ai_idempotency` 생성, 이력 rank 150). 이어 V1055가 존재하지 않는 `academy_page_versions`를 ALTER해 실패했다.
**이전 판단의 오류:** 배포 이력(Actions)에서 P1/prototype 브랜치 배포가 없다는 사실만으로 "V1053 이상은 운영에 없다"고 결론냈으나, 운영 이력에는 main에서 이미 사라진 다른 V1053이 있었다. 운영 이력을 직접 조회하지 않고 병합한 것이 잘못이었다. **이후 규칙: 마이그레이션이 있는 병합 전에는 운영 `flyway_schema_history`를 직접 조회한다**(진단 `flyway-recent`).
**운영 DB 현재 상태(변경 최소):** V1054만 추가 적용됨(`academy_ai_idempotency` 테이블 신규, 비어 있음), V1053 이력 행의 설명·체크섬이 repair로 정렬됨. 기존 테이블·데이터는 변경 없음.
**수정:** `V1055`가 ALTER 전에 같은 3개 테이블을 `CREATE TABLE IF NOT EXISTS`로 보장한다(V1053은 불변). 운영 이력 상태를 그대로 재현한 로컬 시뮬레이션(1052까지 → 가짜 V1053 행 삽입 → repair+1054 → 전체)에서 V1055~V1058 적용·앱 기동 성공, 회귀 테스트 `FlywayProdHistoryMySqlTest`(수정 전 실패/수정 후 통과)를 CI에 추가했다.
**`main`은 수정 병합 전까지 배포 불가 상태**다 — 그 사이 `main`에 다른 PR을 병합하면 같은 실패와 짧은 중단이 반복된다. 병합 보류.

### PR #602(뮤즈) 검토 결과 — 무조건 병합하지 않음
| 항목 | 판단 |
|---|---|
| `AcademyAiCreateService` | #602 최종 diff에는 **없음**(뮤즈가 중복으로 판단해 삭제). 이 브랜치의 기존 서비스가 이미 허위 정보 방지(요청에 없는 경력·성적·실적 미생성)를 포함 → **반영할 새 내용 없음**. |
| 정보 날조 방지 | 위와 동일(기존 코드에 이미 있음). |
| 프롬프트 개선 | #602에 프롬프트 변경이 없음(문서 기재만). 반영할 것 없음. |
| `AcademyAiEditService`(자연어→changes[]) | 그대로는 **병합 불가**: 컨트롤러 미연결, 대상 id를 `ve5`/`ve6`으로 하드코딩(실제 id와 무관), id 정규식 `ve\d+`가 실제 id(`hero-title` 등)를 거부, 이미지는 `UnsupportedOperationException`. 이미 구현된 `ProposalAiService`(AI + 실제 요소 목록 + 검증기)와 기능이 겹친다. |
| 새로운 아이디어 → **반영함** | AI 없이도 동작하는 결정적 규칙 파서(따옴표 새 문구 + 키워드, 색·크기). `RuleBasedEditParser`로 **실제 요소 목록 기준**으로 재구현해 `ProposalAiService`의 AI 실패 시 대체 경로로 연결(뮤즈 아이디어, 테스트 포함). |
| `docs/BUILDIT_20261012_PROGRESS.md` | #602가 **같은 경로를 add**하므로 #601 병합 후 충돌한다 → 뮤즈 기록은 이 문서 §8 로그로 합쳐야 한다. |
| 결론 | #602는 병합하지 않는다(superseded). 뮤즈에는 PR에 위 사유 코멘트, 후속(이미지 변경 파싱·레이아웃 다양화·AI 생성 품질)은 이 브랜치 기준으로 새 PR. |

## 4. 확정 설계 결정 (재논의·역행 금지)
- 저장 연동은 A안: 기존 `PUT …/visual-editor/draft`의 선택 필드 `appliedProposalId`, 저장과 APPLIED는 한 트랜잭션, 409는 전체 롤백·Proposal PENDING 유지.
- `builderPublished` 분리·V1061 이관은 제외(기존 홈페이지 보호). 별도 폴링·재시도·정합 배치 도입 안 함.
- 최초 생성 연결은 A안(위). P1 채팅을 제작 엔진으로 단순 확장하지 않는다.
- 계약 문서: [BUILDIT_AI_AGENT_PROPOSAL_PORT_CONTRACT.md](BUILDIT_AI_AGENT_PROPOSAL_PORT_CONTRACT.md), 설계: [BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md](BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md).
- TUTOR는 본인 학원만 관리, 본부 제작형 유지.
- 이슈 #600(기존 회귀 실패)은 별도 관리하며 오늘 핵심 기능 개발을 지연시키지 않는다.

## 5. 테스트 이력 (실측)
- 신규 서버 테스트: 검증기·입력검증·토큰·AI 서비스 단위, 저장 API 슬라이스·서비스, MySQL 통합(전체 흐름 22 + 원자성 8) 통과.
- 기존 편집기 테스트(API 20, MySQL 35, 정제기 18·11) 무수정 통과.
- 전체 `./gradlew test` **한 번 확정**: 3,210건 중 실패 75건. 같은 조건의 `main`은 76건. 새로 생긴 실패 없음(`AcademyAiDefectsRegressionMySqlTest` 1건은 P1 코드가 있는 브랜치에서만 보이는 기존 실패). 이전에 보고된 "새 실패 22건"은 테스트 JVM 힙(512MB) OOM이었고 `build.gradle` 힙 1536m으로 해소.
- **이후 회귀는 반복하지 않는다.** 변경 영향 범위(편집기·Proposal) 테스트와 보안·무결성 게이트만 실행한다.

## 6. 우선순위·분업

### 6.1 오늘 목표 흐름
AI 최초 생성 → 시각 편집 → 수정(AI 제안) → 승인 → 저장 → 게시 → 공개 URL (E2E 완료, 운영 확인은 배포 후)

### 6.2 담당
| 영역 | 클로드 | 뮤즈 |
|---|---|---|
| 배포 | PR #601 CI·병합·배포·운영 TUTOR 화면 확인 (Flyway 게이트 통과 후) | 운영 Flyway 이력·배포 SHA 확인 및 기록 |
| 최초 생성 | 생성 결과 → 편집기 초안 연결, 미리보기·승인·저장·게시 통합 (완료) | **AI 생성 품질**: `AcademyAiCreateService` 요구 분석·템플릿 선택·문구, 허위 정보 방지, 출력 검증 |
| 대화 수정 | 제안 API·승인 저장·편집기 적용 (완료) | **대화 수정안**: 대화/요청 → `changes[]` 생성 품질·프롬프트 |
| AI 공급자 | — | **Wiki AI Router** 운영 설정·모델 확인(공급자 실패 시 기능이 503으로 무해하게 꺼짐) |
| 보안·테스트 | 인증·학원 격리·트랜잭션 테스트, E2E | 생성 엔진 입력·출력 검증 테스트 |
| 문서 | 이 문서 갱신(단계마다) | 전수조사 결과 반영 |

파일 경계: 클로드 `domain/academy/proposal/**`, `academy_editor.html`, `VisualEditorService`, `AcademyEditorApiController` / 뮤즈 `AcademyAiCreate*`, `AcademyAiChat*`, 프롬프트·요구 분석기. 공유 계약은 `VisualEditorProposalPort`와 Proposal `changes[]` 스키마(`docs/API.md`).

### 6.3 남은 일 (우선순위 순)
1. PR #601 CI 결과 확인 → Flyway 게이트 → 병합 → 배포 → 운영 TUTOR 화면 확인. 운영 AI 공급자가 꺼져 있으면 "AI 수정 요청"만 503 안내가 나오고 편집·승인은 정상이다.
2. (뮤즈) 운영 Flyway 확인 결과 기록, AI 생성 품질 개선, 대화 수정안 생성.
3. 이슈 #600 갱신(전체 76건 기준)과 담당 배정 — 별도 관리.

## 7. 작업 운영 규칙
- 새 세션 시작 시 이 문서와 GitHub 최신 브랜치/커밋, 운영 배포 SHA를 비교해 현재 상태부터 확인한다.
- 단계 완료마다 §8에 날짜·담당·커밋·실측 결과·남은 일을 추가한다.
- 이미 구현·검증한 항목은 재작성하지 않는다. 테스트는 변경 영향 범위 중심으로, 보안·무결성·배포 게이트는 생략하지 않는다.
- **배포 전에는 '완료'로 표시하지 않는다.** 미완성은 정확히 기록한다.

## 8. 단계별 진행 로그
| 일자 | 담당 | 작업 | 결과/증거 | 다음 작업/차단 요인 |
|---|---|---|---|---|
| 10/12 | 뮤즈 | 개발 현황 전수조사 | 운영 섹션 빌더 확인; 시각 편집기 프로토타입; Proposal 구현체 없음 보고 | — |
| 10/12 | 클로드 | 편집기·포트 계약 `d86c7e4` | 테스트·E2E 통과, 푸시 | Proposal 구현체 필요 |
| 10/12 | 클로드 | Proposal 구현(V1058)·Agent 인증·패널·AI 수정 요청 | `6be43f8`, `ec8e3a7`; 신규 테스트 통과, E2E 48+35 통과 | CI·Flyway 게이트 |
| 10/12 | 클로드 | 전체 회귀 확정 | 3,210건 중 75 실패(main 76), 신규 0; OOM은 힙 상향 `3776c4e`로 해소 | 반복 금지 |
| 10/12 | 클로드 | PR #601 생성 | https://github.com/woori73/feel2real-buildit/pull/601 (이슈 #600 참조) | CI 확인, 운영 Flyway 확인 후 병합 |
| 10/12 | 클로드 | AI 최초 생성 → 편집기 초안(A안) 구현 | `e2e-ai-create.js` PC 16/16·모바일 17/17, 기존 E2E 회귀 재실행 | 커밋·푸시, 뮤즈에 생성 품질·Flyway 확인 요청 |
| 10/12 | 클로드 | PR #602 검토·반영 | 규칙 기반 수정 파서 반영(`RuleBasedEditParser`, 단위 9 + MySQL 흐름 1), #602 병합 보류 사유 코멘트 | 뮤즈: #602 정리·후속 PR |
| 10/12 | 클로드 | Flyway 게이트 확인 | Actions 배포 이력: main(`e4bec313`)만 배포, P1/prototype 브랜치 배포 0건 → V1053~1058 신규 적용 예상 | CI 통과 후 병합→자동 배포→진단 워크플로로 기동 확인 |
| 10/12 | 클로드 | PR #601 CI 통과·병합 | head `5e09d002` CI 2개 success → merge commit `65634006`(main). `main` Build Check run #1233 진행 → 성공 시 `Deploy to EC2` 자동 | 배포 후 진단(health-check·journal-errors), 운영 TUTOR 화면 확인은 운영 계정 보유자 |
| 10/12 | 클로드 | 운영 자동 배포 실패·롤백 확인 | run 38209282392 Failed(V1055 `academy_page_versions` 없음), 서비스 active(롤백). 진단 `flyway-recent`로 원인 확정(운영 1053이 다른 파일) | 수정 PR 병합→재배포 |
| 10/12 | 클로드 | V1055 보강 + 운영 이력 재현 테스트 | 로컬 시뮬레이션 성공, `FlywayProdHistoryMySqlTest` 2건(수정 전 실패/후 통과) | PR→CI→병합→재배포→진단 |
| 10/12 | 클로드 | #605 병합·재배포·운영 반영 확인 | #605 CI 2개 success → 병합 `e1bd7b68`; 이후 `8a42a94d` 배포 run 38211763224 **성공**. 읽기 전용 진단: `flyway-recent` — rank 151~155로 V1055·1056·1057·1058·1059 모두 success=1, academy_visual_editor_{draft,published,proposal,agent_token}·academy_page_versions·academy_ai_{sessions,actions,idempotency} 존재 / `health-check` — `{"status":"UP"}`, 8080 LISTEN / `service-status` — active(running), NRestarts=0, 기동 06:17:50 UTC | 운영 TUTOR 화면 확인(운영 계정 보유자): 로그인 → 편집기 진입 → AI로 처음 만들기 → 수정 → 저장 → 게시 → 공개 URL. 로그의 WEMADE KIS 경고는 본 기능과 무관 |
