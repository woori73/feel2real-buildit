# BuildIt 시각 편집기 — AI Agent 연결 설계

> **상태: 설계 문서. 구현은 별도 승인 후 착수한다.** 이 문서는 코드를 바꾸지 않는다.
> 대상 브랜치: `buildit-visual-editor/prototype` (안정화 1차 이후 기준)
> 선행 문서: [BUILDIT_PHILOSOPHY.md](BUILDIT_PHILOSOPHY.md), [academy-builder-page.md](academy-builder-page.md)
> 현재 구현 사양·GrapesJS 인스턴스 접근/연동 지점: [BUILDIT_VISUAL_EDITOR_INTEGRATION_GUIDE.md](BUILDIT_VISUAL_EDITOR_INTEGRATION_GUIDE.md)

## 1. 목적과 범위

BuildIt 본부가 제작한 홈페이지를, **사용자(교육자)가 이후 직접 수정·추가**한다. AI Agent는 이 수정·추가를 돕는다.

- AI는 교육자가 이미 가진 문장·사진·색깔을 **증폭**하는 도구다. 교육자의 고유한 색깔을 지우고 모두를 비슷하게 만들지 않는다. (철학 문서: AI Operating Rules 5·6, 개발 의사결정 체크)
- AI는 **제안**만 한다. 공개 홈페이지를 바꾸는 최종 결정(적용·게시)은 항상 교육자가 한다. (규칙 8: 승인 전 초안을 공개 사실로 만들지 않는다)
- 이번 문서의 범위: ① HTML 유실 문제 해결 구조 ② HTML·CSS·projectData 동기화 ③ revision 충돌 방지 ④ 미리보기·승인 ⑤ 인증·권한 ⑥ 기존 P0 AI와의 연결. **비범위:** 실제 LLM 호출·프롬프트·에이전트 호스팅 구현.

## 2. 현재 구조와 문제 (실측 근거)

### 2.1 한 페이지가 세 가지 표현으로 저장된다

| 컬럼 | 쓰임 | 누가 읽나 |
|---|---|---|
| `html` | 정제된 본문 | 게시 시 `published`로 복사 → 공개 페이지 |
| `css` | 정제된 스타일 | 〃 |
| `project_data` | GrapesJS 프로젝트(컴포넌트 트리·스타일·에셋) | **편집기 재불러오기가 우선 사용** |

편집기는 `projectData`가 있으면 그것으로 복원하고 `html`은 쓰지 않는다. 공개 페이지는 `html/css`만 쓴다. **두 표현이 어긋날 수 있는 구조**다.

### 2.2 확인된 유실 시나리오 (안정화 1차 점검에서 재현)

에이전트가 REST로 `GET draft → html 문자열 치환 → PUT draft(revision)`를 했을 때:

1. `PUT`은 200이고 `html`은 바뀐다. `projectData`는 그대로다.
2. 편집기를 열면 **옛 제목이 보인다**(`projectData` 우선). 에이전트 변경은 편집기에 안 보인다.
3. 게시하면 **공개 페이지에는 에이전트 변경이 나간다**(`html` 복사). → 편집기와 공개가 달라진다.
4. 사용자가 편집기에서 저장하면 편집기의 옛 상태가 `html`을 덮어써 **에이전트 변경이 조용히 사라진다**.

(`projectData`를 `'{}'`로 보내면 편집기는 `html`에서 다시 만들므로 보이지만, 컴포넌트 메타데이터가 사라진다.)

### 2.3 기존 P0 AI(`/api/tutor/academy/ai/*`)는 편집기와 연결이 없다

- 채팅 서비스(`AcademyAiChatServiceImpl`)는 키워드로 **`builderDraft`의 허용 필드 15개**(원장 소개·철학·전화·카카오 등)만 제안한다. 적용은 `applyDraftChanges`+스냅샷(`AcademyPageVersionService`)+감사 로그(`AcademyAiAction`).
- `renderMode=custom`이면 공개 페이지는 편집기 게시본만 그린다. **P0 필드를 바꿔도 방문자 화면은 그대로**다.
- `AcademyAiContextService`의 컨텍스트에는 `renderMode`도 편집기 HTML도 없다.

### 2.4 인증은 사용자 쿠키(JWT)뿐이다

에이전트 전용 인증이 없다(Bearer 토큰은 401). 외부 에이전트가 쓰려면 교육자 계정으로 로그인해야 하는데, 이는 에이전트에 **교육자의 전체 권한**(학원 설정·게시·삭제 포함)을 주는 것과 같다.

## 3. 설계 원칙

1. **AI는 draft를 직접 쓰지 않는다.** AI의 결과는 항상 *제안(proposal)* 이다. draft(그리고 그 `projectData`)를 확정하는 것은 **사용자 브라우저의 편집기**뿐이다. → 2.2의 유실이 구조적으로 불가능해진다.
2. **승인 전에는 비공개.** 제안은 공개 페이지·게시본·draft에 영향이 없다.
3. **미리보기 후 승인.** PC·모바일 폭으로 전/후를 보고 사용자가 적용한다.
4. **revision에 고정.** 제안은 만들 때의 `baseRevision`을 기억하고, 그사이 draft가 바뀌었으면 *STALE*로 표시해 자동 덮어쓰지 않는다.
5. **전 구간 정제.** 제안의 HTML/CSS도 저장 시·적용 시·공개 출력 시 `VisualEditorSanitizer`를 거친다.
6. **최소 권한·감사.** 에이전트 토큰은 "읽기+제안"만 가능하고 모든 호출을 기록한다.

## 4. HTML·CSS·projectData 동기화 구조

세 가지 방안을 비교했다.

| 방안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| A. 서버가 projectData를 패치 | AI가 컴포넌트 id 기반 JSON 패치 → 서버가 html/css 재생성 | 서버만으로 완결 | html/css 직렬화를 서버(Java)에 GrapesJS와 똑같이 재구현해야 함. 버전 차이 위험 |
| B. 서버에 헤드리스 GrapesJS | Node 워커가 HTML→projectData 변환 | 서버에서 정규화 가능 | 새 런타임·보안 격리·운영 비용 |
| **C. 편집기에서 정규화 (권장)** | 제안(html/css)을 사용자 브라우저의 편집기가 받아 적용 → 편집기가 `getProjectData/getHtml/getCss`로 **세 표현을 한꺼번에** 만들어 기존 저장 API로 저장 | 새 인프라 없음. 세 표현이 항상 같은 GrapesJS에서 나와 일치. 사용자 승인과 자연스럽게 결합 | 승인(=편집기 열림)이 있어야 반영됨 — 요구사항(사용자 승인)과 일치 |

**권장: C.** 흐름은 다음과 같다.

```text
AI Agent ──(Bearer)──▶ POST proposals {baseRevision, html, css, summary}
                         서버: 정제 + 크기 검사 + PENDING 저장 (draft/게시본 불변)
교육자가 편집기를 열면: "AI 제안 N건" 배지
  → 제안 열기: 편집기가 별도 미리보기 캔버스에 제안 적용(PC/태블릿/모바일 폭) + 변경 요약
  → [적용]: 편집기 본 캔버스에 setComponents/setStyle (Undo 가능, 아직 저장 아님)
  → 사용자가 확인·추가 수정 후 [저장]: PUT draft {html, css, projectData, revision, appliedProposalId}
        서버: revision 검증 → draft 갱신 → proposal APPLIED
  → 게시는 사용자가 별도로(기존 게시 버튼)
```

### 4.1 보조 안전장치 (Phase 0, 작은 변경)

외부 클라이언트가 `PUT draft`를 직접 호출하는 경로 자체도 막아야 한다(제안 API 도입 전에도 유실 방지):

- 편집기는 저장 시 `projectDataHash = sha256(html)`을 함께 보낸다(`projectData`가 만들어진 시점의 HTML 지문).
- 서버는 `sha256(받은 html) ≠ projectDataHash`이면 **`projectData`를 폐기**(`'{}'`)하고 응답에 `projectDataDiscarded: true`를 담는다. 편집기는 그 경우 `html`에서 다시 만든다.
- 효과: HTML만 바꾼 요청이 "편집기에 안 보이는" 상태가 되지 않고, 편집기 저장이 에이전트 변경을 덮어쓰지도 않는다(편집기가 `html`에서 복원하기 때문).

## 5. 데이터 모델 (구현 시. 마이그레이션 번호·DOMAIN.md 동시 갱신 필수)

| 테이블 | 핵심 컬럼 | 비고 |
|---|---|---|
| `academy_visual_editor_proposal` | `proposal_id`, `academy_id`(FK, CASCADE), `base_revision`, `status`(PENDING/APPLIED/REJECTED/STALE/EXPIRED), `html`, `css`, `summary`, `change_summary_json`, `created_by_kind`(AGENT/USER), `agent_token_id`, `created_at`, `decided_at` | 학원당 PENDING 상한(예: 5)·만료(예: 14일) |
| `academy_agent_token` | `token_id`, `academy_id`, `token_hash`, `scopes`, `label`, `expires_at`, `last_used_at`, `revoked_at` | 평문은 발급 시 1회만 노출 |
| `academy_visual_editor_draft_history` | `academy_id`, `revision`, `html`, `css`, `project_data`, `saved_by`, `saved_at` | 최근 N개 보관 → 롤백·감사. **현재는 이력이 없다** |

기존 `AcademyAiAction`(감사)·`AcademyPageVersion`(스냅샷)의 패턴을 재사용하되, 시각 편집기는 별도 테이블을 쓴다(기존 P0/P1 데이터와 분리라는 현재 원칙 유지).

## 6. API 설계 (초안)

**에이전트(Bearer, 학원 1개에 바인딩)** — `ChartAgentTokenFilter` 패턴, 경로 allowlist

| 메서드·경로 | 설명 | scope |
|---|---|---|
| `GET /api/agent/academy/visual-editor/context` | `renderMode`, `revision`, 본문 구조 요약(컴포넌트 id·태그·텍스트·이미지 슬롯), 허용 편집 범위 | `ve:read` |
| `POST /api/agent/academy/visual-editor/proposals` | `{baseRevision, html, css, summary}` → 정제 후 PENDING 저장 | `ve:propose` |
| `GET /api/agent/academy/visual-editor/proposals/{id}` | 상태 조회 | `ve:read` |

**사용자(쿠키)** — 승인 UI가 사용

| 메서드·경로 | 설명 |
|---|---|
| `GET /api/tutor/academy/page-settings/visual-editor/proposals` | 내 학원의 PENDING 목록 |
| `POST …/proposals/{id}/reject` | 거절 |
| `PUT …/visual-editor/draft` (`appliedProposalId` 추가) | 편집기가 적용 결과를 저장. 서버가 revision을 검증하고 제안을 APPLIED로 |

에이전트는 `draft`/`published`/`renderMode`/게시 API를 **호출할 수 없다**(경로 allowlist에 없음).

## 7. 미리보기와 승인 UX

- 편집기에 **"AI 제안 N건"** 배지 → 제안 패널.
- 제안 카드: 에이전트의 요약 문장 + 변경 요소 수 + 만든 시각 + `baseRevision` 대비 상태(최신/STALE).
- **전/후 비교:** 같은 렌더러(`visual_editor_render.js`: 캔버스·미리보기·공개가 공유)로 PC·태블릿·모바일(375px) 폭 미리보기. 변경된 텍스트·이미지·스타일 요소는 id 기준으로 하이라이트하고 텍스트는 diff로 보여준다.
- **적용 단위:** 전체 적용 / 거절 / (확장) 섹션 단위 선택 적용.
- **적용 = 저장이 아니다.** 적용하면 편집기 변경(저장되지 않은 변경)이 되고 Undo로 되돌릴 수 있다. 저장·게시는 사용자가 따로 누른다.
- STALE 제안은 "그사이 내용이 바뀌었습니다 — 다시 제안받기"를 안내하고, 사용자가 원하면 현재 draft 기준으로 다시 요청한다.

## 8. 인증·수정 권한 범위

| 주체 | 인증 | 가능 | 불가 |
|---|---|---|---|
| 교육자(사용자) | 쿠키 JWT(현행, TUTOR·본인 학원) | 편집·저장·게시·제안 승인/거절 | 타 학원 |
| AI Agent | **학원 바인딩 Bearer 토큰**(신규) | 컨텍스트 읽기, 제안 생성 | draft/게시본 직접 쓰기, 게시, `renderMode`·학원 설정·LMS·결제, 토큰 발급 |
| 관리자 | 현행 | 현행 | — |

- 토큰: 해시 저장, 발급 시 1회 노출, 만료·회전·즉시 폐기, 호출별 `last_used_at`과 감사 기록, 학원당 rate limit.
- 입력 제한: 제안 HTML/CSS는 기존 크기 한도(CSS 64KB, HTML MEDIUMTEXT)와 `VisualEditorSanitizer`(태그·속성·URL·CSS 허용 목록)를 그대로 적용. `script`·`on*`·`javascript:` 차단은 우회 예외가 없다.
- 프롬프트 인젝션: 페이지 텍스트는 에이전트에게 **데이터**로만 전달한다. 에이전트 출력은 문장을 믿지 않고 구조 검증(허용 태그·CSS·URL)만 신뢰한다. 제안이 외부 URL 이미지를 넣으면 승인 화면에서 출처를 표시한다.

## 9. 기존 P0 AI와의 연결 방식

| 상황 | 동작 |
|---|---|
| `renderMode=template` (편집기 미게시) | 현행 P0 AI(`/ai/chat` → 허용 필드 제안 → `/apply`) 그대로 |
| `renderMode=custom` (편집기 게시 중) | 홈페이지 수정 요청은 **시각 편집기 제안**으로 라우팅. P0 AI는 "게시 중인 편집기 홈페이지에는 반영되지 않습니다"를 안내하고 필드 변경을 막거나 경고 |
| 공통 | 승인 UX·감사·스냅샷 원칙은 같다. `AcademyAiContext`에 `renderMode`와 편집기 본문 구조 요약을 추가해 AI가 어느 흐름인지 안다 |

장기 옵션(별도 설계 필요): 편집기 HTML의 특정 요소에 `data-ve-field="principalDesc"` 같은 **필드 바인딩**을 두어, P0 필드(원장 소개·전화 등)를 바꾸면 편집기 콘텐츠도 함께 갱신되게 한다. 한 번 바꾸면 두 화면이 같이 바뀌지만, 편집기 콘텐츠에 사용자가 직접 쓴 문장과 충돌할 수 있어 신중해야 한다.

## 10. 단계별 구현 계획

| 단계 | 내용 | 비고 |
|---|---|---|
| Phase 0 | `projectDataHash` 보조 안전장치(4.1), draft 이력 테이블 | 작고 독립적. AI 없이도 유실 방지 |
| Phase 1 | 제안 테이블·API, 편집기 제안 패널(미리보기·적용·거절), STALE 처리 | AI 없이 "사람이 만든 제안"으로 흐름 검증 가능 |
| Phase 2 | 에이전트 토큰·Bearer 필터·`context` API·감사·rate limit | 보안 검토 필요 |
| Phase 3 | LLM 에이전트 연결(프롬프트·평가), `renderMode` 라우팅, P0 AI 안내 | 비용·품질 평가 필요 |

## 11. 결정이 필요한 항목 (구현 승인 시 확인)

1. 에이전트 실행 위치와 모델(현재 AI provider는 `wiki-router`).
2. 이미지 교체·생성 정책(외부 URL 허용 여부, 사용자 업로드만 허용할지).
3. 제안 적용 단위(전체 vs 섹션 단위)와 학원당 PENDING 상한.
4. `data-ve-field` 필드 바인딩을 도입할지(9장 장기 옵션).
5. draft 이력 보관 개수와 기간.

## 12. 철학 점검 (BUILDIT_PHILOSOPHY)

- **교육자의 색깔 강화:** 제안은 기본적으로 교육자가 쓴 문장·이미지를 유지하고 다듬는 방향이다. 새 문구를 만들 때도 사용자 승인 없이는 반영되지 않는다.
- **교육자를 통제하지 않음:** AI가 평가·통제하지 않고, 적용·게시 결정은 교육자가 한다.
- **AI 해석과 실제 evidence 구분:** 제안에는 "AI 제안"임을 표시하고 승인 전에는 공개되지 않는다.
- **일회성이 아닌 자산:** draft 이력·제안 이력이 남아 교육자의 홈페이지 변화가 기록으로 축적된다.
