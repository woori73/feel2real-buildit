# 문서 인덱스 (작업 전 라우팅)

> **모든 에이전트(OMC/Claude, OMX/Codex)는 작업 착수 전 이 표에서 해당 문서를 로드한다.**
> 규칙·스펙의 단일 출처는 `docs/`다. CLAUDE.md / AGENTS.md는 이 인덱스로 가는 얇은 진입점일 뿐이다.

## 무슨 작업 → 어떤 문서

| 작업 | 먼저 읽을 문서 |
|---|---|
| **모든 BuildIt 제품·기능 설계 및 수정** | [BUILDIT_PHILOSOPHY.md](BUILDIT_PHILOSOPHY.md) |
| **내신 예상문제 / 학교별 시험지 / 인쇄용 문제지 HTML 제작·수정** | **[exam/내신_시험지_제작원칙.md](exam/내신_시험지_제작원칙.md) — 작업 전 필독 + 완성 후 체크리스트 검수 필수** |
| **학교별 Wiki Context / Digital Twin / 기출·프린트 기반 내신 문제 제작** | **[exam/WIKI_TUTOR_BROWSER_CONTEXT.md](exam/WIKI_TUTOR_BROWSER_CONTEXT.md) + [exam/내신_시험지_제작원칙.md](exam/내신_시험지_제작원칙.md) — MCP empty 시 TUTOR 브라우저 확인, Context Pack·정답 독립검증 필수** |
| **DB 스키마 / 마이그레이션 / Entity** | [WORKFLOW.md](WORKFLOW.md) §4 번호정책 + [DOMAIN.md](DOMAIN.md) |
| **로컬 실행 / DB 자동 생성 / PR / 배포** | [WORKFLOW.md](WORKFLOW.md) |
| **ChatGPT Work / Codex 세션에서 repo 열기 (stale checkout·`origin/main` 오판 방지)** | [WORK_GIT_WORKSPACE_WORKFLOW.md](WORK_GIT_WORKSPACE_WORKFLOW.md) |
| **Windows에서 처음 환경 세팅 (WSL2/Docker/JDK)** | [LOCAL_SETUP_WINDOWS.md](LOCAL_SETUP_WINDOWS.md) |
| **요구사항 변경으로 기능 숨김/보존(/archived)** | [ARCHIVED_FEATURES.md](ARCHIVED_FEATURES.md) + [API.md](API.md) |
| **기능 화면·API·상태 흐름 구현** | [FLOWS.md](FLOWS.md) + [API.md](API.md) |
| **수능영어 Wiki ③④⑤ 학생 오답 입력(DAY-2)·약점 DNA 도출 규칙** | [MONDAY_DAY2_HANDOFF.md](MONDAY_DAY2_HANDOFF.md) |
| **랜딩 통계 / 서비스 지표 / GA4 경계** | [service-statistics-design.md](service-statistics-design.md) |
| **Controller endpoint 추가/변경** | [API.md](API.md) (계약 동기화 필수) |
| **Academy 발행 API / 미래 LLM 콘텐츠 생성** | [academy-publish-design.md](academy-publish-design.md) |
| **Academy Builder 페이지(`/builder`) / 랜딩 발행 배선** | [academy-builder-page.md](academy-builder-page.md) |
| **학원 온라인 LMS(`{handle}.../lms`) / 서브도메인 경로·권한** | [academy-lms-design.md](academy-lms-design.md) |
| **제품 범위·사용자모델·기술스택** | [MVP.md](MVP.md) |
| **프로젝트 전체 인수인계 / 현재 상태 빠른 파악** | [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md) |
| **Thymeleaf head / nav / blog 등 UI 컨벤션** | [CONVENTIONS.md](CONVENTIONS.md) |
| **다음 할 일 / 우선순위** | [ROADMAP.md](ROADMAP.md) |
| **인프라 / EC2 배포 상세** | [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md) |
| **선택적 worktree / 과거 dev·stg 정책 확인** | [WORKFLOW.md](WORKFLOW.md) §6 |
| **GitHub 이슈 / PR 작성** | [CONTRIBUTING.md](CONTRIBUTING.md) |

## 문서 동기화 규칙 (어느 하나만 수정 금지)
- 스키마 변경 → `db/migration` V_파일 + Entity + [DOMAIN.md](DOMAIN.md) **셋 동시**
- 플로우/엔드포인트 변경 → 코드 + [FLOWS.md](FLOWS.md) / [API.md](API.md)
- 워크플로/마이그레이션 정책 변경 → [WORKFLOW.md](WORKFLOW.md) (CLAUDE.md·AGENTS.md는 포인터만)
- 내신 시험지 제작/조판 규칙 변경 → [exam/내신_시험지_제작원칙.md](exam/내신_시험지_제작원칙.md) 동시 갱신
- 학교별 Wiki Context 조회·문제제작 절차 변경 → [exam/WIKI_TUTOR_BROWSER_CONTEXT.md](exam/WIKI_TUTOR_BROWSER_CONTEXT.md) 동시 갱신

## 작업 전 필수 확인 (모든 에이전트)
1. BuildIt 관련 작업이면 [BUILDIT_PHILOSOPHY.md](BUILDIT_PHILOSOPHY.md)를 먼저 읽기
2. 이 인덱스에서 해당 작업 문서 로드
3. 내신 시험지 작업이면 [exam/내신_시험지_제작원칙.md](exam/내신_시험지_제작원칙.md)를 작업 전 읽고 완성 후 체크리스트 검수
4. 학교별 Wiki Context/Digital Twin/기출·프린트 기반 문제 제작이면 [exam/WIKI_TUTOR_BROWSER_CONTEXT.md](exam/WIKI_TUTOR_BROWSER_CONTEXT.md)를 추가로 읽고, MCP empty 시 TUTOR 브라우저 확인 후 실제 Context를 확보할 것
5. 현재 git 상태 / 브랜치 확인
6. DB/마이그레이션 건드리면 [WORKFLOW.md](WORKFLOW.md) §4 번호정책 **반드시** 확인
