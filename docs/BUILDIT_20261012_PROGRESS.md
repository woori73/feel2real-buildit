# BuildIt 2026-10-12 진행 기록 (뮤즈 담당)

> **상태 구분**
> - ✅ 구현 완료: 코드 작성·컴파일 검증 완료
> - 🧪 테스트 통과: E2E 등 검증 완료
> - ⬆️ GitHub 반영: 브랜치에 push됨
> - 🚫 운영 미배포: 운영 환경에 아직 배포되지 않음
> - ⏳ 미반영: 아직 GitHub에 없음

## 1. 운영 안전성 확인 (우선 처리) ✅

- **운영 배포 SHA**: `e4bec313` (Deploy to EC2 #38191146990, 2026-10-12 01:14 success)
- **main 마이그레이션**: V1052까지 (총 148개)
- **PR #601 마이그레이션**: V1053~V1058 (신규 6개)
- **충돌 가능성**: 없음. V1053~V1058은 main에 없어 신규 적용됨.
- **운영 DB 직접 확인**: 불가 (VM 네트워크 차단). 코드 분석 기준.
- **클로드 전달 사항**: 없음 (충돌 없음)

## 2. 클로드 기존 구현 확인 (2026-10-12 14:30 전달받음)

**브랜치**: `buildit-visual-editor/prototype`

| 항목 | 상태 |
|------|------|
| `AcademyAiCreateService` + 최초 생성 API | ✅ 구현 완료, 🧪 E2E 통과 (PC 16/16, 모바일 17/17), 🚫 운영 미배포 |
| `AcademyAiChatService` / `AcademyAiChatServiceImpl` | ✅ 구현 완료 (P0 섹션 빌더용 FieldChange 생성) |
| AI 모델 호출 품질 | ⏳ 미확인 |
| 운영 배포 | 🚫 미배포 |

**주요 구현 내용** (클로드):
- `generateDraft(message, academyName)` → `CreateDraft` DTO
- `AcademyRequirementAnalyzer`로 요구사항 구조화
- 허위 정보 방지: allowlist, 정규식 감지, XSS 방지, sanitize
- DB 미저장 (승인 전), 기존 홈페이지 미덮어쓰기

## 3. 중복 제거 (2026-10-12)

- 뮤즈 PR #602의 `AcademyAiCreateService.java`는 클로드 구현과 **동일 FQN 중복**임을 확인
- 클로드 구현이 더 완성도가 높고 E2E 통과했으므로 뮤즈 버전을 **브랜치에서 삭제** (commit 66c2f037)
- 중복 클래스 추가 방지, 덮어쓰기 없음

## 4. 뮤즈 담당: 시각 편집기용 changes[] 생성 ✅ ⬆️

**파일**: `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyAiEditService.java`

- 자연어 명령 → 시각 편집기 Proposal용 `changes[]` 생성
- 클로드의 `AcademyAiChatServiceImpl`은 P0 섹션 빌더용 FieldChange → **대상이 달라 중복 아님**
- 클로드 계약(`BUILDIT_AI_AGENT_PROPOSAL_PORT_CONTRACT.md`)의 changes[] 형식 준수
- 텍스트·스타일 변경 파싱 (이미지 변경은 2단계)
- **경계 준수**: Proposal 승인·저장 로직 미수정, 클로드 파일 건드리지 않음
- 컴파일 검증 완료 (javac, UTF-8)

## 5. Wiki AI Router 설정 확인 ✅

- `WikiRouterAcademyAiProvider` → `POST /api/ai/complete`, provider=auto
- 실제 모델은 Python 백엔드에서 관리 (Java에서 키 노출 없음 — 비밀키 로그·MD 기록 없음)
- 실제 모델 호출 품질은 ⏳ 미확인 (운영 검증 필요)

## 6. Lovable 템플릿 조사 ✅

214개 중 학원 활용 가능 5개 선별 (참고용, 코드 복제 없음):
- **Language School Site**: 어학원 등록 + 레벨 테스트
- **Course Landing Page**: 커리큘럼 아코디언
- **Life Coach Website**: 상담 신청 플로우
- **Yoga Studio Template**: 코치 프로필 그리드
- **SaaS Landing Page**: 가격 티어

디자인 다양화는 2단계 작업으로 진행 (기존 5종 프리셋 유지)

## 7. 작업 경계 준수

- ✅ 뮤즈 범위: AcademyAiEdit* (신규 파일만)
- ✅ 클로드 파일 (GrapesJS, Proposal 저장, 편집기 연결, AcademyAiCreateService) 미수정
- ✅ 기존 완료 기능 재개발 없음
- ✅ 전체 회귀 테스트 반복 없음

## 8. GitHub 반영 상태

- **브랜치**: `buildit-ai-agent/muse-20261012` (main e4bec313 기준)
- **PR**: #602 — https://github.com/woori73/feel2real-buildit/pull/602
- **상태**: open
- **포함 파일**:
  - `src/main/java/.../domain/academy/service/AcademyAiEditService.java` (신규)
  - `docs/BUILDIT_20261012_PROGRESS.md` (본 파일)
- **제외**: `AcademyAiCreateService.java` (중복으로 삭제)
- **인증**: GitHub API (vault credential) — git clone은 VM 네트워크 제한으로 불가
- **병합**: PR #601 병합·운영 배포는 클로드와 Flyway 안전성 확인 후 별도 진행

### 구현 완료 vs 테스트 통과 vs 운영 미배포 구분

| 항목 | 구현 완료 | 테스트 통과 | GitHub 반영 | 운영 배포 |
|------|----------|------------|------------|----------|
| 클로드 AI 최초 생성 | ✅ | ✅ (E2E 16/16, 17/17) | ✅ (PR #601) | 🚫 |
| 클로드 AI 채팅 (P0) | ✅ | ⏳ | ✅ (PR #601) | 🚫 |
| 뮤즈 changes[] 생성 | ✅ | ⏳ | ✅ (PR #602) | 🚫 |
| AI 모델 호출 품질 | — | ⏳ 미확인 | — | — |
| 템플릿 다양화 | ⏳ 2단계 | — | — | — |


## 9. 2단계 작업 (2026-10-12 15:00)

### 9.1 자연어 수정 품질 보완 ✅

**파일**: `AcademyAiEditService.java` (업데이트, commit ad322263)

**보완 내용**:
- 실제 projectData JSON 파싱 → 컴포넌트 인덱스 구축 (id, tagName, textContent, imgSrc)
- 편집기의 `planChanges`와 동일한 기준 (HTML id, `/^[A-Za-z][\w-]*$/`)
- 대상 탐색 우선순위:
  1. `'A'를 'B'로` 패턴 → A와 일치하는 텍스트를 가진 컴포넌트
  2. 키워드(제목/부제목/본문/버튼) → 태그(h1/h2/h3/p/button) 매칭
  3. ID 직접 지정 (예: "ve5를 바꿔줘")
- 없는 컴포넌트는 `TARGET_NOT_FOUND`로 거부 (임의 수정 방지)
- 계약 오류 코드 준수: `NO_CHANGES`, `INVALID_CHANGE`, `UNKNOWN_CHANGE_TYPE`

**검증**: 8/8 단위 테스트 통과
- 제목→ve5, 기존텍스트→ve5, 없는대상→TARGET_NOT_FOUND
- 스타일→ve5, 모바일→mediaText, 이미지→ve7, ID직접→ve6, 빈projectData→예외

### 9.2 Wiki AI Router 검증 ✅ (부분)

| 항목 | 결과 |
|------|------|
| 엔드포인트 존재 | ✅ `POST /api/ai/complete` → 403 (인증 필요, 404 아님) |
| 코드 경로 | ✅ `WikiRouterAcademyAiProvider` → `WikiAiRouterClient` → Python `ai_router.py` (provider=auto) |
| 실제 모델 호출 성공 여부 | ⏳ 미확인 (운영 인증 필요) |
| 최초 생성·수정 요청 응답 품질 | ⏳ 미확인 (운영 인증 필요) |
| 비밀키 기록 | 없음 (요구사항 준수) |

**처리 결과**: 인증 없이 확인할 수 있는 범위 완료. 실제 호출 검증은 TUTOR 로그인 후 별도 진행 필요.

### 9.3 Lovable 템플릿 확장 설계 ✅ (별도 변경)

**파일**: `docs/BUILDIT_TEMPLATE_EXPANSION_DESIGN.md` (신규, commit 7018dfc1)

**내용**:
- 기존 5종 유지 (운영 영향 없음)
- 신규 5종 제안: EXAM_INTENSIVE, LANGUAGE_KIDS, ARTS_PORTFOLIO, STUDY_CAFE, ONLINE_LIVE
- 각 템플릿의 레이아웃 차별점·섹션 구성·데이터 키 정의
- 호환성: builderDraft 키 재사용, DB 스키마 변경 없음, 편집기 파이프라인 변경 없음
- 구현은 별도 PR로 진행 (운영 배포 지연 방지)

### 9.4 작업 경계 준수

- ✅ PR #601, 시각 편집기, Proposal 승인·저장 코드 미수정
- ✅ 기존 구현 반복 수정 없음
- ✅ 클로드 브랜치와 파일 충돌 없음
