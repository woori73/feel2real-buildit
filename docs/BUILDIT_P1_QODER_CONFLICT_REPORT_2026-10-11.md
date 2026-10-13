# BuildIt P1 — Qoder 프론트엔드 착수 전 충돌 보고 (2026-10-11)

> `docs/BUILDIT_PHILOSOPHY.md` §개발 의사결정 체크의 "철학과 충돌하면 구현을 먼저 진행하지 않고 보고한다"에 따라 작성.
> 기준: `origin/main` = `05e13a2227c07c5bf8f4353682c75f3c4036cdd1`, 작업 브랜치 `buildit-p1/qoder-ui`.

## 목표

Muse 1일차 보고(`buildit-p1/muse-backend`, 커밋 `f8767f4`, Flyway V1053, `AcademyAiChatController`)와
Qoder 2일차 지시(AI 채팅·미리보기·PC/모바일 전환·전후 비교·승인/적용/복원·배포 UI·모바일 390px)를
현재 GitHub 저장소 실제 상태와 대조하고, 구현 착수 여부를 판단한다.

## 검증 결과 1 — Muse 1일차 산출물이 GitHub에 없다

`woori73` 계정으로 접근 가능한 저장소는 `feel2real-buildit`(실제 BuildIt, 커밋 prefix `[BuildIt]`),
`buildit-editor`(README 1커밋), `feel2real-buildit2`(empty)다.

| 항목 | GitHub 실측 |
|---|---|
| 커밋 `f8767f4` | 세 저장소 모두 `422 No commit found` |
| 브랜치 `buildit-p1/muse-backend` | `git ls-remote --heads origin` 결과 없음 |
| 브랜치 `buildit-p1/qoder-ui` | 착수 시점 없음 → 이번에 `05e13a2`에서 생성·push |
| Flyway `V1053` | `db/migration` 최고 버전은 `V1052__add_audit_member_saengseonjangsu_20261010.sql` |
| `AcademyAiChatController` / `AcademyAiChatDtos` / `academy_ai_sessions` | code search 0건 |

즉 Day-1 백엔드는 로컬에만 있고 push되지 않았다. 기준 SHA 대조·API 계약 실측·PR 충돌 확인·통합이
현재 불가능하다.

## 검증 결과 2 — 지시된 7개 화면 중 5개가 이미 구현되어 있다

`docs/academy/ACADEMY_BUILDER_AI_ROADMAP.md` §0은 "중복 구현 금지"를 명시한다. 실측 결과:

| 지시 항목 | 저장소 실측 상태 |
|---|---|
| AI 채팅 | 부분 중복. `[AI로 Academy 전체 점검]` compose 모달이 자연어 `instructions` → 진단 → 제안카드 → 적용 흐름을 이미 구현 (`academy_builder.js:3888` `requestAcademyProposal`, `:3940` `composeCardHtml`) |
| 홈페이지 브라우저형 미리보기 | 이미 구현. `.builder-public-preview` + 실제 공개 페이지 렌더러 (`academy_builder.html:305-321`) |
| PC·모바일 전환 | 이미 구현. `setView('pc'/'tablet'/'mobile')`, 뷰 토글 UI `academy_builder.html:1080-1086` |
| 모바일 390px | 이미 구현. 위 토글이 모바일 390px (`academy_builder.html:370`, `:1505`) |
| 배포(발행) UI | 이미 구현. `wirePublish` + `pendingAcademyPublish` (`academy_builder.js:2`, `:236`) |
| 수정 전후 비교 | 부분 존재. 카드가 `현재 →` / `제안 →` 2줄 비교 (`academy_builder.js:3947-3948`). jsdiff 기반 리치 diff는 없음 = **신규** |
| 승인·적용·복원 | 부분 존재. 적용은 `applyComposeChanges` (`:3989`), 복원은 세션 스냅샷 `captureComposeSnapshot` (`:3975`). 서버 영속 버전 목록/복원은 없음 = **신규** |

## 검증 결과 3 — 제품 원칙과의 충돌

`docs/academy/ACADEMY_BUILDER_AI_ROADMAP.md` §1 사용자 경험 원칙:

> 각 입력 항목 옆에 인라인 버튼 3개만 둔다. **별도 채팅창을 열지 않는다.**

"AI 채팅" 화면을 신규 채팅창으로 만들면 이 원칙과 정면 충돌한다. 기존 AI Guide 패널
(`academy_builder_ai_guide.js`)과 compose 모달이 이 원칙의 구현체다.

## 검증 결과 4 — API 네임스페이스·계약 충돌

- 기존 `AcademyAiController`가 `@RequestMapping("/api/tutor/academy/ai")`를 **이미 점유**하고 있으며
  `/context`, `/status`, `/suggestions`, `/academy-proposal`, `/guide-suggestions`, `/completion`을 제공한다.
  Muse의 `AcademyAiChatController`가 같은 base path에 `/chat`, `/chat/apply`, `/chat/versions`를 추가하면
  컨트롤러 2개가 동일 네임스페이스를 나눠 갖는다 (충돌 자체는 아니지만 매핑·보안 설정 정합성 확인 필요).
- 기존 확정 계약(실측, `AcademyAiProposal`):
  `POST /api/tutor/academy/ai/academy-proposal`, body `{ instructions }`, 응답
  `{ diagnosis, proposals: [{ id, type: TEXT|SECTION_ORDER, title, current, suggestion, target, textValue, sectionOrder, safeToApply }] }`.
  TEXT의 `target`은 allowlist 19개 builderDraft key (`academy_builder.js:3796` `COMPOSE_TEXT_TARGETS`).
  인증은 `@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal`, `credentials: 'same-origin'`,
  academyId는 body/param으로 받지 않는다.
- Muse 보고서의 `ChatProposal`/`FieldChange`/`ValidationResult`는 위 `AcademyAiProposal.Item`과 **다른 형태의
  병행 제안 계약**이다. 둘 중 하나로 통일하지 않으면 프론트가 두 종류의 제안 카드를 따로 처리해야 한다.
- 기존 설계는 "서버는 완전히 stateless — DB에 저장하지 않는다"(제안 생명주기 = 열린 Builder 세션)이고
  "Publish는 AI가 절대 실행하지 않는다". Muse의 `academy_page_versions` 영속 스냅샷은 stateless 원칙을
  바꾸는 추가 설계이므로 합의가 필요하다.
- `docs/API.md`에는 위 academy AI 엔드포인트 4개가 **미문서화**다 (AGENTS.md "endpoint 변경 시 docs/API.md
  동시 수정" 위반 상태). `ai/chat` 문자열은 NewStart(`/api/newstart/ai/chat`) 문서에서만 나타난다.

## 범위 (이 문서를 제외한 코드 변경 없음)

- 브랜치 `buildit-p1/qoder-ui` 생성 및 이 충돌 보고 문서 추가. Java·DB·Flyway·Muse 담당 파일 미수정.
- main 직접 push 없음, force push 없음.

## 판단 — 구현 착수 보류 사유

1. API 계약 미확정: `ChatRequest/ChatProposal/ApplyRequest/VersionInfo` 필드명·오류 코드·버전 복원 규격이
   저장소에 없다. 지시 §3 "API 필드명을 추정하여 확정 구현하지 않는다"에 해당.
2. 중복 개발 위험: 7개 중 5개가 기구현. 남은 신규분은 리치 전후 비교(jsdiff)와 서버 영속 버전/복원뿐.
3. 제품 원칙 충돌: 별도 채팅창 금지 원칙.
4. 통합 불가: Muse 산출물이 GitHub에 없어 기준 SHA·충돌·CI 대조 불가.

## Muse에게 요청할 계약 항목

1. `AcademyAiChatDtos` 실코드 또는 필드 정의 — `ChatRequest`, `ChatResponse`, `ChatProposal`, `FieldChange`,
   `ValidationResult`, `ApplyRequest`, `ApplyResponse`, `VersionInfo`.
2. 기존 `AcademyAiProposal`과 통합 여부. 분리 유지라면 두 제안 타입의 UI 우선순위.
3. 오류 응답 규격 — allowlist 검증 실패(보안 2항), 버전 재확인 실패(보안 5항), 미인증 401/403의
   status code + body 형태.
4. 인증 조건 — 기존과 동일하게 세션 쿠키(`same-origin`) + principal 기반 academyId 확정 여부.
5. `GET /versions`, `POST /versions/{id}/restore` 응답 규격과 복원 후 클라이언트 갱신 방법
   (전체 preview 재조회인지 변경 필드만인지).
6. `buildit-p1/muse-backend` push — 통합·충돌 확인의 전제.
7. Flyway V1053 번호 확정 — `docs/WORKFLOW.md` §4 번호정책상 main의 V1052 이후 순차인지 재확인.

## 향후 (별도)

- 계약 확정 후 진행할 Qoder 신규분: jsdiff(MIT) 기반 전후 비교 컴포넌트, 서버 영속 버전 목록·복원 UI.
- 기존 화면(미리보기·뷰 토글·발행 UI)은 수정이 아니라 회귀 검증만 수행.
- `docs/API.md`에 academy AI 엔드포인트 문서화 (담당자 합의 필요 — API 문서는 계약 소유자 Muse 측 소관).

## 관련

- `docs/BUILDIT_PHILOSOPHY.md`, `docs/academy/ACADEMY_BUILDER_AI_ROADMAP.md`, `docs/academy-ai-page-generation.md`
- `AGENTS.md` 작업 착수 전 필수 문서 게이트
