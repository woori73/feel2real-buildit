# BuildIt AI Agent — Proposal 연동 최종 설계서 (4차 수정본)

- 기준 코드: SHA `2724f78298fbe1c94d49b8e1ee9c94fa18ed04c7` (시각 편집기 안정화 1차 완료본)
- 상태: **설계 확정, 구현 대기(별도 승인 필요).** 코드·마이그레이션·PR·병합·운영 배포는 이 문서 범위에 포함되지 않는다.
- 선행 문서: [BUILDIT_VISUAL_EDITOR_AI_AGENT_DESIGN.md](BUILDIT_VISUAL_EDITOR_AI_AGENT_DESIGN.md)(배경·원칙), [BUILDIT_VISUAL_EDITOR_INTEGRATION_GUIDE.md](BUILDIT_VISUAL_EDITOR_INTEGRATION_GUIDE.md)(편집기 현재 사양). 뮤즈 4차 설계 중 아래 "0. 확정 사항"과 충돌하는 부분은 이 문서가 우선한다.
- 표기: **[실제 코드]** = 2724f78에서 확인한 사실, **[설계]** = 구현 전 확정 사양, **[결정 필요]** = 사용자 결정 대기.

---

## 0. 확정 사항 (사용자 지시 반영)

| # | 확정 | 비고 |
|---|---|---|
| 1 | 저장 연동은 **A안**: 기존 `PUT …/visual-editor/draft`에 선택 필드 `appliedProposalId` 추가 | 저장과 APPLIED 전이는 **한 트랜잭션** |
| 2 | 409(revision 충돌) 시 **전체 롤백**, Proposal은 **PENDING 유지** | 409에서 STALE로 바꾸지 않는다 |
| 3 | **제거:** `currentRevision` 폴링, MutationObserver 감지, 별도 `POST …/proposal-applied`, 재시도 fetch, 정합 배치(10분), 수동 정합 SQL | 원자 처리로 불일치가 생기지 않으므로 불필요 |
| 4 | **제외:** `builderPublished` 분리, V1061 데이터 이관, `academy_page_published`·`academy_migration_failure` | 기존 홈페이지 보호 |
| 5 | **유지:** `academy_visual_editor_published` 구조, `academies.page_settings`의 `builderDraft`(변경 없음) | 공개 렌더링·LMS 설정 영향 없음 |
| 6 | 운영 `DROP TABLE` 지침, `./gradlew flywayRepair` 지침 **삭제** | §8 |
| 7 | 검증은 **fail-closed**: 비교 대상 누락·파싱 실패·스텁은 모두 FAIL | §4 |

---

## 1. API 계약 (실제 코드와 일치)

### 1.1 기존 저장 API [실제 코드]

- 경로: `PUT /api/tutor/academy/page-settings/visual-editor/draft` (`AcademyEditorApiController.saveDraft`)
- 인증: 쿠키 JWT, `SecurityConfig`의 `/api/tutor/**` → `hasRole("TUTOR")`. CSRF는 전역 비활성.
- 요청 본문(JSON 객체, 타입 엄격): `html`(문자열), `css`(문자열), `projectData`(**JSON을 문자열로 직렬화한 값**, 없으면 `"{}"`), `revision`(숫자 또는 숫자 문자열)
- 처리 순서: 타입 검사 → 초안 유무별 revision 규칙 → 크기(33MB) → `VisualEditorSanitizer`(html/css/projectData) → `VisualEditorService.saveDraft`(`@Transactional`, `@Version` 낙관적 잠금)
- 성공 200: `{"status":"DRAFT_SAVED","revision":<long>,"updatedAt":"<ISO>","message":"초안이 저장되었습니다."}`
- 오류 본문 형식 [실제 코드]: `{"status":<int>,"message":"…","error":"Bad Request|Conflict|…"}`
  - 400: 타입 오류, 크기 초과, 초안 있는데 revision 없음
  - 403: 학원 없음/권한 없음 (`AccessDeniedException`)
  - 409: revision 불일치 또는 초안 없음+revision≠0 (`DraftConflictException`)

### 1.2 확장 [설계]

**요청:** 선택 필드 `appliedProposalId` 추가.

- 타입: 숫자 또는 숫자 문자열(`asRevision`과 같은 엄격 파싱). 그 외 타입은 400. 생략/`null`이면 **기존 동작과 완전히 동일**.
- 의미: "이 저장 내용에는 제안 N의 변경이 적용되어 있다"는 **신고**일 뿐이며, 서버가 §4 검증으로 사실 확인 후에만 APPLIED로 바꾼다.

**성공 200 (필드가 있을 때만 `appliedProposal` 추가):**

```json
{
  "status": "DRAFT_SAVED",
  "revision": 13,
  "updatedAt": "2026-10-12T09:00:00",
  "message": "초안이 저장되었습니다.",
  "appliedProposal": { "proposalId": 77, "status": "APPLIED", "appliedRevision": 13 }
}
```

- `appliedRevision`은 **저장 후 DB 엔티티의 revision**이다(요청이 보낸 값이 아님).
- `appliedProposalId`를 안 보낸 요청의 응답에는 `appliedProposal` 키가 없다(기존 클라이언트 호환).

**오류 — 기존 형식에 `code` 필드를 추가한다.** 클라이언트는 `code`로 분기하고, `code`가 없으면 기존 revision 충돌로 취급한다.

| HTTP | `code` | 의미 | 트랜잭션 | Proposal 상태 |
|---|---|---|---|---|
| 409 | `REVISION_CONFLICT` | revision 불일치 | 롤백 | **PENDING 유지** |
| 404 | `PROPOSAL_NOT_FOUND` | 없음 **또는 다른 학원의 제안**(구분 불가) | 롤백 | 변화 없음 |
| 409 | `PROPOSAL_NOT_PENDING` | 이미 APPLIED/REJECTED/EXPIRED/STALE | 롤백 | 변화 없음 |
| 422 | `PROPOSAL_VERIFICATION_FAILED` | §4 검증 실패(`details`에 항목별 사유) | 롤백 | **PENDING 유지** |
| 400 | — | `appliedProposalId` 타입 오류 등 | — | — |

- 실패 시 초안도 저장되지 않는다(원자성). 사용자의 편집 내용은 브라우저에 그대로 있으므로 클라이언트가 "제안 없이 저장"으로 다시 보낼 수 있다(§5).
- 에러 코드 선택 근거: 현재 편집기의 409 처리는 "최신 초안을 불러오시겠습니까? 현재 수정 내용은 사라집니다"이다. 제안 관련 실패에 같은 409를 쓰면 사용자가 편집 내용을 잃을 수 있어 `code` 분기가 필수다. 검증 실패는 409와 구분되도록 422를 쓴다.

### 1.3 뮤즈 측 API [설계] (이 문서는 계약만 정의)

| 호출자 | 메서드·경로 | 설명 |
|---|---|---|
| Agent(Bearer, 학원 바인딩) | `GET /api/agent/academy/visual-editor/context` | revision·구조 요약 |
| Agent | `POST /api/agent/academy/visual-editor/proposals` | `{baseRevision, summary, changes[]}` → 정제·검증 후 PENDING 저장 (draft·게시본 불변) |
| Agent | `GET /api/agent/academy/visual-editor/proposals/{id}` | 상태 조회(토큰 학원 한정) |
| 교육자(쿠키) | `GET /api/tutor/academy/page-settings/visual-editor/proposals` | 내 학원 PENDING 목록(+`applicable` 계산 필드) |
| 교육자 | `POST …/proposals/{id}/reject` | 거절 |

- `changes[]` 항목: `{componentId, type: "text"|"image"|"style", after, styles?, mediaText?}`. **component 단위 변경이 1건 미만인 제안은 생성 시 400으로 거부**한다(검증 대상이 없는 제안은 허용하지 않음).
- Agent는 draft·published·renderMode·게시·LMS 설정 API를 호출할 수 없다(`/api/agent/**` 경로 allowlist 밖).

---

## 2. Proposal 상태 전이와 트랜잭션

### 2.1 상태

저장되는 상태는 `PENDING`, `APPLIED`, `REJECTED`, `EXPIRED`, `STALE`. **`APPLYING`은 서버 상태로 두지 않는다**(브라우저 안의 일시 상태일 뿐이며, A안은 저장과 APPLIED가 한 트랜잭션이라 서버가 "적용 중"인 순간이 없다).

| 전이 | 트리거 | 주체 | 비고 |
|---|---|---|---|
| (생성) → PENDING | Agent `POST proposals` | 뮤즈 | 학원당 PENDING 상한, 정제·크기 검사 |
| PENDING → APPLIED | `PUT draft` + `appliedProposalId` 성공 | **클로드 PUT 트랜잭션 안에서 뮤즈 서비스 호출** | 저장과 원자적 |
| PENDING → REJECTED | 교육자 `reject` | 뮤즈 | 소유권 검증 |
| PENDING → EXPIRED | 만료 배치(`expires_at` 경과) | 뮤즈 | 사용자 입력 없는 시스템 작업, `WHERE status='PENDING'` 한정 |
| PENDING → STALE | 목록 조회 시 **dry-run 검증이 `TARGET_NOT_FOUND`** | 뮤즈 | 아래 2.3 |
| 그 외 | 불가 | — | APPLIED·REJECTED·EXPIRED는 종결, 재전이 금지 |

### 2.2 저장 + APPLIED 트랜잭션 [설계]

`VisualEditorService.saveDraft`는 이미 `@Transactional`이며 [실제 코드] draft를 `update` 후 `flush`한다. 그 **같은 트랜잭션 안**에서 다음을 수행한다.

1. (기존) academy 확정 = `requireMyAcademy(principal)` — **토큰 사용자의 학원이며 요청값이 아니다.**
2. (기존) revision 검증·draft 갱신·`flush` → `@Version`이 새 revision을 확정.
3. `appliedProposalId`가 있으면 뮤즈가 제공하는 포트를 호출한다.

```java
// 계약(인터페이스는 클로드 쪽 패키지에 두고 뮤즈가 구현). Propagation.MANDATORY: 트랜잭션 밖 호출 불가.
public interface VisualEditorProposalPort {
  ProposalApplyResult applyInSaveTransaction(
      long academyId,          // 서버가 확정한 학원
      long userId,             // principal.userId()
      long proposalId,         // 요청의 appliedProposalId
      long savedRevision,      // flush 후 draft.getRevision() (DB 값)
      String sanitizedProjectData); // 정제 후 저장된 projectData
}
// 실패 시 ProposalException(code, httpStatus, details) — 런타임 예외 → 전체 롤백
```

포트 내부(뮤즈 구현):

1. `SELECT … FROM proposal WHERE proposal_id = :id AND academy_id = :academyId FOR UPDATE` (`PESSIMISTIC_WRITE`). 없으면 `PROPOSAL_NOT_FOUND`(404).
2. `status != PENDING`이면 `PROPOSAL_NOT_PENDING`(409).
3. §4 검증 실행. 하나라도 실패하면 `PROPOSAL_VERIFICATION_FAILED`(422).
4. `status=APPLIED`, `applied_revision=savedRevision`, `applied_at=now`, `applied_by=userId` 저장.

예외는 모두 `RuntimeException`이라 `saveDraft`의 트랜잭션이 롤백되어 **draft 갱신도 함께 취소**된다. 컨트롤러는 `ProposalException`을 §1.2 표대로 변환한다(기존 `@ExceptionHandler` 패턴과 동일).

- "요청자가 전달한 revision만으로 적용 성공 처리 금지": 요청의 `revision`은 낙관적 잠금 비교에만 쓰이고(기존 동작), APPLIED 기록값은 DB에서 읽은 `savedRevision`이다. 별도의 `savedRevision` 요청 필드는 **존재하지 않는다**.
- 동일 제안에 대한 동시 저장 2건: 제안 행 잠금으로 직렬화되어 한쪽은 `PROPOSAL_NOT_PENDING`(또는 revision 충돌)로 실패한다.
- 네트워크 오류로 응답을 못 받은 경우: 클라이언트는 재시도하지 않고 `GET draft`+제안 목록으로 상태를 확인한다(저장이 성공했다면 revision이 올라 있고 제안은 APPLIED다).

### 2.3 STALE 정의

- revision 차이만으로는 STALE이 아니다(사용자의 자동저장 때문에 revision은 계속 오른다).
- **적용 불가(대상 컴포넌트가 현재 draft에 없음)일 때만 STALE.** 교육자 목록 조회 트랜잭션이 §4의 같은 검증 함수를 dry-run으로 호출해 `TARGET_NOT_FOUND`면 STALE로 바꾸고 응답에 `applicable:false, staleReason`을 담는다. 저장 경로(409)에서는 STALE을 만들지 않는다.

---

## 3. 학원별 권한 검증

원칙: **학원은 항상 토큰/쿠키 주체에서 도출하고, 요청의 어떤 값도 학원 식별에 쓰지 않는다.**

| 동작 | 주체 | 학원 확정 방법 | 타 학원 ID 접근 시 |
|---|---|---|---|
| Proposal 생성 | Agent 토큰 | 토큰→학원 바인딩(`academy_agent_token.academy_id`) | 해당 없음(요청에 학원 ID 없음) |
| Proposal 조회(Agent) | Agent 토큰 | 〃, `findByIdAndAcademyId` | 404 |
| 목록 조회 | 교육자(TUTOR) | `academyService.getMine(principal)` | 해당 없음 |
| 거절 | 교육자 | 〃 + `findByIdAndAcademyId` | 404 |
| 적용(저장) | 교육자 | `requireMyAcademy` + 행 잠금 쿼리에 `academy_id` 포함 | 404(`PROPOSAL_NOT_FOUND`) |
| 만료 | 시스템 배치 | 사용자 입력 없음, `status`·`expires_at` 조건만 | — |

- 모든 Repository 메서드는 `academyId` 파라미터가 **필수**이며, ID만으로 조회하는 메서드(`findById`)는 Proposal에 대해 사용하지 않는다(코드 리뷰 체크리스트 및 ArchUnit/단위 테스트로 강제).
- 존재 여부를 노출하지 않기 위해 "없음"과 "다른 학원 것"은 같은 404·같은 메시지로 응답한다.
- ADMIN 우회 없음(현재 편집기 `AcademyEditorController`가 본인 학원만 열도록 구현된 것과 동일). Agent 토큰은 `ve:read`/`ve:propose` scope만 가지며 사용자 쿠키 API를 호출할 수 없다.
- Agent 토큰은 해시로만 저장하고, 폐기·만료를 검사하며, 호출 로그는 `AcademyAiAction` 패턴으로 기록한다.

---

## 4. 검증 로직 (실제 GrapesJS projectData 기준, fail-closed)

저장 요청은 컨트롤러에서 이미 `VisualEditorSanitizer`로 정제되므로, 검증은 **정제 후 저장되는 값**(`sanitizedProjectData`)에 대해 수행한다.

### 4.1 공통 규칙 (FAIL 조건)

다음은 모두 `PROPOSAL_VERIFICATION_FAILED`다. **성공을 기본값으로 반환하는 경로는 없다.**

| 코드 | 조건 |
|---|---|
| `NO_CHANGES` | 제안의 `changes`가 비어 있음 |
| `PROJECT_DATA_MISSING` | `projectData`가 null/빈 문자열/`{}`이거나 JSON 파싱 실패, `pages[0].frames[0].component` 없음 |
| `TARGET_NOT_FOUND` | `attributes.id == componentId`인 컴포넌트가 없음 |
| `TARGET_AMBIGUOUS` | 같은 id의 컴포넌트가 2개 이상 |
| `TEXT_MISMATCH` / `IMAGE_MISMATCH` / `STYLE_MISMATCH` | 4.2~4.4의 값 불일치 |
| `UNKNOWN_CHANGE_TYPE` | `text`/`image`/`style` 외 |

검증은 예외를 삼키지 않는다(파싱 예외 포함 모두 위 코드로 변환해 FAIL).

### 4.2 text

- 대상 컴포넌트의 **모든 하위 `textnode.content`를 깊이 우선으로 이어 붙인 값**과 제안의 `after`(생성 시 정제된 값 `after_sanitized`)를 비교한다. 비교 전 양쪽을 동일하게 공백 정규화(연속 공백→1칸, trim)한다.
- 첫 자식만 보는 방식은 쓰지 않는다(`<h1>제목 <b>강조</b></h1>` 같은 중첩 텍스트에서 오탐).

### 4.3 image

- 대상 컴포넌트의 `attributes.src`가 정제된 `after`와 정확히 같아야 한다. `src`가 없거나 정제 과정에서 제거되었으면 `IMAGE_MISMATCH`.

### 4.4 style (스텁 제거, 실제 함수)

`extractComponentValue`의 `style` 분기는 `verifyComponentStyle(projectData, componentId, expectedStyles, mediaText)`를 **직접 호출하고 `false`면 `STYLE_MISMATCH`를 던진다**("VERIFIED" 문자열 반환·`return true` 스텁은 코드에 남기지 않는다).

- 규칙 탐색: `projectData.styles[]`에서 `selectors`에 `"#"+componentId`가 있는 규칙. GrapesJS 버전/설정에 따라 id 규칙이 `selectorsAdd`("#id")로 저장되는 경우도 허용한다.
- `mediaText`: 제안이 null이면 규칙에 `mediaText`가 없거나 빈 값이어야 하고, 값이 있으면 문자열이 같아야 한다(모바일은 `(max-width: 768px)`).
- 값 비교: 속성명은 소문자, 값은 공백 정규화 후 비교. 기대 속성 **전부** 일치해야 한다.
- **[결정 필요 아님 / 구현 첫 단계 확인 사항]** `selectors:["#ve5"]` 형식은 통합 가이드에 기록된 것이다. 구현 첫 단계에서 실제 GrapesJS 0.23.6 출력(E2E 산출 projectData)으로 형식을 재확인하고, 위 두 형식을 모두 테스트에 포함한다.
- 보조 확인: 저장된 `css` 문자열에도 해당 선언이 있는지 확인하여 `projectData`와 `css`가 어긋난 저장을 FAIL로 만든다.

### 4.5 사용자가 적용 후 추가 수정한 경우 **[결정 필요]**

"확인·추가 수정 후 저장" 흐름에서 사용자가 제안의 대상 요소를 다시 고치면 4.2~4.4가 불일치로 FAIL한다.

- **기본안(엄격, 권고):** FAIL로 처리한다. 클라이언트는 "제안 내용과 달라졌습니다. 제안 없이 저장하시겠습니까?"를 보여주고, 사용자가 동의하면 `appliedProposalId` 없이 저장한다. 제안은 PENDING으로 남아 사용자가 거절할 수 있다.
- 대안(완화): 요소별로 "제안 값과 다르면 `APPLIED_MODIFIED`로 기록". 상태가 늘어나고 "무엇이 적용됐는지" 의미가 흐려져 권고하지 않는다.

### 4.6 dry-run 공유

목록 조회의 STALE 판정(§2.3)은 위 함수에서 **TARGET 계열 검증만** 호출한다. 검증 코드를 한 곳에 두어 판정이 갈라지지 않게 한다.

---

## 5. 클라이언트(편집기) 동작 [설계, 클로드 담당]

1. 뮤즈의 제안 패널(JS)이 `window.__ve.applyProposal(proposal)`을 호출한다. 편집기는 `changes[]`를 **component 단위**로 적용한다(`editor.getWrapper().find('#id')` 기반 텍스트·`src`·`Css.setIdRule`). 전체 `setComponents/setStyle` 교체는 사용하지 않아 나머지 projectData가 보존된다.
2. 적용은 **한 번의 Undo 단위**로 묶는다(GrapesJS UndoManager의 그룹 동작은 구현 시 E2E로 확인하고, 불가하면 적용 직전 `getProjectData()` 스냅샷으로 되돌리는 보조 경로를 둔다). 적용 직후는 "저장되지 않은 변경"이며 저장·게시는 사용자가 한다.
3. 적용 시 `pendingAppliedProposalId`를 보관한다. **적용 후 첫 저장 성공까지 모든 PUT(자동저장 30초·수동 저장·`pendingSave` 재귀 호출 포함)에 같은 값을 실어 보낸다.**
4. 응답 처리:
   - 200 + `appliedProposal.status=APPLIED` → `pendingAppliedProposalId` 해제, `CustomEvent('ve:saved', {detail:{revision, appliedProposal}})` 발행(**폴링 대체**).
   - 409 + `REVISION_CONFLICT`(또는 `code` 없음) → 기존 동작 그대로(최신 초안 불러오기 확인). 불러오면 `pendingAppliedProposalId`도 해제하고 제안은 PENDING으로 남는다.
   - 404 `PROPOSAL_NOT_FOUND` / 409 `PROPOSAL_NOT_PENDING` / 422 → 편집 내용은 유지하고 메시지를 보여준 뒤, 사용자 동의 시에만 `appliedProposalId` 없이 재저장.
5. 사용자가 Undo로 적용을 되돌리면 대상 값이 달라져 서버 검증이 FAIL한다(안전). 클라이언트는 Undo 후 `pendingAppliedProposalId`를 해제할 수 있다.
6. 변경 감지(`sig()` = html+css)는 그대로 둔다. 제안 3종(텍스트·이미지·CSS)은 모두 html 또는 css를 바꾸므로 자동저장 대상이 된다. `projectData`만 바뀌는 변경은 제안 범위에 포함하지 않는다(기존 편집기 동작 변경 없음).
7. `window.__ve.state()`는 `pendingAppliedProposalId` 필드를 **추가**한다(기존 필드·E2E 사용 방식 불변).

---

## 6. 변경 파일과 담당

### 6.1 클로드

| 파일 | 변경 | 기존 동작 영향 |
|---|---|---|
| `AcademyEditorApiController.java` | `appliedProposalId` 파싱, `ProposalException` 변환(`code` 포함), 응답에 `appliedProposal` 추가 | 필드 미지정 시 동일 |
| `VisualEditorService.java` | `saveDraft`에 선택 파라미터 추가, flush 후 `VisualEditorProposalPort` 호출 | 〃 |
| `VisualEditorProposalPort.java`(신규, 인터페이스·예외) | 계약 정의 | 구현체 없으면 no-op/비활성(필드 지정 시 503이 아니라 400 `PROPOSAL_NOT_AVAILABLE`로 거부) |
| `academy_editor.html` | `applyProposal`, Undo 묶음, `pendingAppliedProposalId`, 응답 분기, `ve:saved` 이벤트 | 기존 저장·게시·자동저장 흐름 불변 |
| `docs/API.md` | PUT draft 확장·오류 `code`·`ve:saved` 계약 | — |
| E2E(`e2e/visual-editor/*`)·단위/통합 테스트 | §7 | — |

`AcademyEditorController.java`는 변경하지 않는다.

### 6.2 뮤즈

| 파일 | 내용 |
|---|---|
| `V1060__…proposal/agent_token.sql`(번호는 착수 시 V1057 이후 최신으로 재확인) | **신규 테이블만** `CREATE TABLE IF NOT EXISTS` (`academy_visual_editor_proposal`, `academy_agent_token`). DML 없음 |
| `VisualEditorProposal`·Repository·`ProposalChange` | 엔티티(`@Version` 포함). 모든 조회에 `academyId` |
| `VisualEditorProposalService`(포트 구현) | §2.2, §4 검증, STALE dry-run, 거절, 만료 배치 |
| Agent 토큰 엔티티·필터·`SecurityConfig` allowlist | `/api/agent/**` Bearer, scope, 학원 바인딩 (`SecurityConfig`는 공용 파일이므로 변경 전 상호 공지) |
| Agent·교육자용 Proposal 컨트롤러 | §1.3 |
| `academy_ai_proposal.js`(패널 UI) | 목록·미리보기·"적용"→`__ve.applyProposal`·거절. 저장 호출은 하지 않는다 |
| `AcademyAiChatServiceImpl.java` | 제안 생성 연동 **추가만**(기존 builderDraft 15개 필드 경로 불변) |
| `docs/DOMAIN.md` | 신규 테이블·Entity 동기화 |

### 6.3 경계

- **계약 두 가지만 공유한다:** (a) PUT draft의 `appliedProposalId`와 §1.2 응답·오류, (b) 편집기 JS 인터페이스 `window.__ve.applyProposal(proposal)` + `ve:saved` 이벤트. 뮤즈는 편집기 저장 코드를, 클로드는 Proposal 테이블·Agent 인증을 건드리지 않는다.
- 포트 인터페이스 변경은 양측 합의 후에만 한다.

### 6.4 구현 순서

1. 포트 인터페이스·예외·에러 `code` 계약 확정(문서)
2. 뮤즈: V1060 + 엔티티 + 서비스(검증 포함) + 단위·MySQL 통합 테스트
3. 클로드: PUT 확장 + 포트 연동(구현체 연결 후) + 서버 테스트
4. 클로드: 편집기 `applyProposal`·Undo·이벤트 + E2E
5. 뮤즈: Agent 토큰·생성 API·패널 UI
6. 통합 E2E(§7)와 기존 회귀 전체 → 승인 후 병합

---

## 7. 테스트 계획

모든 서버 테스트는 given/when/then 구조. MySQL 통합 테스트는 기존 `VisualEditorMySqlIntegrationTest` 패턴(동시성·롤백 검증은 H2가 아니라 MySQL).

### 7.1 API·트랜잭션 (MySQL 통합)

| ID | 시나리오 | 기대 |
|---|---|---|
| T-01 | `appliedProposalId` 없는 PUT | 기존 응답과 동일(`appliedProposal` 키 없음), 기존 테스트 전부 무수정 통과 |
| T-02 | 정상 적용 PUT | 200, draft 갱신, 제안 APPLIED, `appliedRevision == 응답 revision == DB revision` |
| T-03 | revision 불일치(409) | draft 불변, 제안 **PENDING 유지** |
| T-04 | 제안 검증 실패(422) | draft 불변(롤백), 제안 PENDING |
| T-05 | 같은 제안으로 동시 PUT 2건 | 정확히 1건 성공, 나머지 409(`PROPOSAL_NOT_PENDING` 또는 revision), 제안 APPLIED 1회 |
| T-06 | 이미 APPLIED/REJECTED/EXPIRED 제안으로 PUT | 409 `PROPOSAL_NOT_PENDING`, draft 불변 |
| T-07 | 응답을 못 받은 재전송(같은 revision) | revision 충돌 409, 중복 APPLIED 없음 |
| T-08 | `appliedProposalId` 타입 오류("abc", 객체) | 400 |
| T-09 | 포트 구현체 미연결 상태에서 필드 지정 | 400 `PROPOSAL_NOT_AVAILABLE`, draft 불변 |

### 7.2 학원 격리·권한

| ID | 시나리오 | 기대 |
|---|---|---|
| S-01 | 학원 A 교육자가 학원 B 제안 ID로 PUT | 404 `PROPOSAL_NOT_FOUND`, B 제안 불변, A draft 불변 |
| S-02 | 존재하지 않는 ID와 타 학원 ID | 응답 본문·시간대 동일 |
| S-03 | A가 B 제안 reject | 404, 상태 불변 |
| S-04 | Agent 토큰 A로 B 제안 조회 | 404 |
| S-05 | Agent 토큰으로 `PUT draft`/publish/`renderMode` 호출 | 401/403 |
| S-06 | 학생·비로그인이 목록/적용 | 기존 규칙(401/403) |
| S-07 | Repository에 `findById(Proposal)` 사용 금지 | 아키텍처 테스트 |

### 7.3 검증 로직 (단위, 실제 GrapesJS 출력 fixture)

| ID | 시나리오 | 기대 |
|---|---|---|
| V-01 | text 변경(중첩 `<b>` 포함) | 일치 시 통과, 다르면 `TEXT_MISMATCH` |
| V-02 | image src 변경/제거 | 일치 통과, 제거는 `IMAGE_MISMATCH` |
| V-03 | style 기본·`mediaText` 규칙 | 일치 통과, 속성 하나라도 다르면 `STYLE_MISMATCH` |
| V-04 | `selectors` / `selectorsAdd` 두 형식 | 둘 다 인식 |
| V-05 | **실제 E2E 산출 projectData**로 id 규칙 형식 확인 | 형식 가정이 사실과 일치 |
| V-06 | 대상 없음 / 같은 id 2개 | `TARGET_NOT_FOUND` / `TARGET_AMBIGUOUS` |
| V-07 | projectData `{}`·null·깨진 JSON·pages 누락 | `PROJECT_DATA_MISSING` (예외로 새지 않음) |
| V-08 | `changes` 비어 있음 / 알 수 없는 type | `NO_CHANGES` / `UNKNOWN_CHANGE_TYPE` |
| V-09 | 스텁 부재 | 어떤 입력으로도 검증이 기본 성공을 반환하지 않음(속성 기반 테스트) |
| V-10 | 정제 후 값이 달라지는 입력(`<script>` 포함) | 정제 결과 기준으로 판정 |

### 7.4 편집기 E2E (실제 브라우저, 기존 63건 유지)

| ID | 시나리오 | 기대 |
|---|---|---|
| E-01 | 제안 적용 → 30초 자동저장 | 제안 APPLIED, `ve:saved` 발행, 다른 요소 보존 |
| E-02 | 적용 → 수동 저장 / Ctrl+S | 동일 |
| E-03 | 적용 후 다른 탭에서 저장(충돌) → 409 | 제안 PENDING, 기존 409 UX 유지 |
| E-04 | 적용 → Undo | 적용 전 상태 복귀, 저장되지 않음 |
| E-05 | 적용 후 대상 요소 재수정 → 저장 | 422 안내, "제안 없이 저장" 선택 시 일반 저장 |
| E-06 | 적용 후 새로고침 | 편집 내용 폐기, 제안 PENDING 유지 |
| E-07 | 이미지·CSS(모바일 `mediaText`) 제안 | 미리보기·저장·재불러오기 일치 |
| E-08 | `projectData` 보존 | 적용 전후 비대상 컴포넌트 JSON 동일 |

### 7.5 기존 기능 회귀

- 기존 단위·MySQL 통합·E2E 전체(편집기 63건, MySQL 35+8) 무수정 통과
- 공개 홈페이지 불변 검증: 제안 적용·저장 전후로 `GET /academies/{slug}` 응답 본문을 비교(양쪽 모두 비어 있으면 **FAIL**). 게시 전에는 동일해야 하고, 게시 후에만 달라진다.
- `renderMode=template/custom` 각각에서 위 비교 실행
- `AcademyAiChatServiceImpl`의 builderDraft 15개 필드·LMS 설정 저장/조회 회귀

---

## 8. 마이그레이션·운영 지침

- 이번 범위의 DB 변경은 **신규 테이블 2개 추가뿐**이다(V1060, 번호는 착수 시 재확인). 기존 테이블 변경·데이터 이관(DML)은 없다.
- 파일은 `CREATE TABLE IF NOT EXISTS`를 쓴다(V1057과 동일). MySQL DDL은 암시적 커밋이라, 중간 실패 시 일부 테이블만 남을 수 있으나 **신규·빈 테이블이라 서비스에 영향이 없고** 같은 파일 재실행이 안전하다.
- 실패 시 원칙: [docs/WORKFLOW.md](WORKFLOW.md) §4(적용된 V_파일 불변, 번호 역행 금지)를 따른다. 로컬·테스트 DB는 `./scripts/run-local.sh`로 재생성한다. **운영 DB에서 테이블을 삭제하거나 임의로 repair를 실행하는 절차는 이 설계에 없다.** 운영 마이그레이션 실패 복구는 별도 승인과 별도 절차서로 정의한다.
- (삭제됨) `DROP TABLE` 복구 지침, `./gradlew flywayRepair`(이 프로젝트에는 Flyway Gradle 플러그인이 없고 운영은 jar 기동 시 Flyway가 실행되므로 해당 명령은 실행 불가).
- (삭제됨) `scripts/verify-migration-html.sh` HTML 비교 스크립트: 데이터 이관이 없어 필요하지 않다. 대신 §7.5의 자동화된 공개 페이지 불변 테스트를 쓴다(비교 대상 누락 시 FAIL).

---

## 9. 기존 홈페이지에 영향이 없다는 근거

**실제 코드 사실**

1. 공개 홈페이지의 편집기 렌더링은 `academy_visual_editor_published`만 읽는다(`VisualEditorService.toPublishedHomepage`). 초안(`academy_visual_editor_draft`)은 공개 경로에서 읽지 않는다.
2. `renderMode`는 `academies.page_settings`의 `builderDraft["homepage.renderMode"]`에 있고, 변경은 `publish`/`unpublish`(같은 학원 행 비관적 락 트랜잭션)에서만 일어난다. 저장(PUT draft)은 `renderMode`를 건드리지 않는다.
3. P0/P1 빌더·AI 채팅·LMS 설정(`LmsBuilderSettingsService`)은 `builderDraft`를 쓰며, 이 설계는 해당 코드·데이터를 수정하지 않는다.

**설계로 보장하는 사항**

4. Proposal은 PENDING일 때 draft·게시본·`page_settings` 어디에도 쓰지 않는다. APPLIED가 되어도 변경되는 것은 **draft뿐**이며, 방문자 화면은 사용자가 별도로 게시할 때만 바뀐다(게시는 기존 `publish` 흐름과 revision 검증 그대로).
5. 신규 DDL은 신규 테이블 2개뿐이고 기존 테이블에 컬럼·제약을 추가하지 않는다. 신규 테이블의 FK는 `academies`를 참조(CASCADE)할 뿐 기존 행을 변경하지 않는다.
6. `builderPublished` 분리·V1061 이관을 제외했으므로 "게시본 없음 → 준비 중" 같은 기존 페이지 동작 변경이 없다.
7. `appliedProposalId`가 없는 모든 요청은 기존 코드 경로와 같고(T-01), 기존 회귀 테스트 전체가 무수정으로 통과해야 병합할 수 있다(§7.5).
8. 비공개 데이터 노출: Proposal·토큰 테이블은 공개 렌더러에서 참조하지 않으며, 조회 API는 모두 학원 소유권 검증을 거친다(§3). 오류 응답은 타 학원 제안의 존재 여부를 노출하지 않는다.

---

## 10. 남은 결정

| # | 결정 | 기본안 |
|---|---|---|
| 1 | 적용 후 사용자가 대상 요소를 추가 수정했을 때(§4.5) | 엄격 FAIL + "제안 없이 저장" 선택 |
| 2 | Agent 제안의 학원당 PENDING 상한·만료 기간 | 5건 / 14일(뮤즈 설계 유지) |
| 3 | STALE 판정 주체(§2.3) | 목록 조회 시 dry-run (뮤즈 구현) |

구현은 사용자 별도 승인 후 진행한다. 코드 수정·PR·main 병합·운영 배포는 하지 않는다.
