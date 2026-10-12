# BuildIt 시각 편집기 — 현재 사양과 AI Agent(뮤즈) 연동 지점

> **상태:** 안정화 1차 완료 시점(브랜치 `buildit-visual-editor/prototype`)의 **구현 사양**과, AI Agent를 붙일 때 쓸 **연동 지점**을 정리한 문서다.
> AI Agent 통합은 아직 구현하지 않았다. 설계(제안·승인·권한)는 [BUILDIT_VISUAL_EDITOR_AI_AGENT_DESIGN.md](BUILDIT_VISUAL_EDITOR_AI_AGENT_DESIGN.md), API 계약은 [API.md](API.md) "BuildIt 시각 편집기"를 본다.
> 아래 내용은 모두 코드와 실제 브라우저 E2E(`e2e/visual-editor`)에서 확인한 사실이다.

## 1. 저장 구조

### 1.1 테이블 (V1057, 기존 P0/P1 데이터와 분리)

| 테이블 | 키 | 컬럼 |
|---|---|---|
| `academy_visual_editor_draft` | 학원당 1행(`uq_visual_draft_academy`) | `html` MEDIUMTEXT, `css` **TEXT(64KB)**, `project_data` MEDIUMTEXT, `revision`(JPA `@Version`), `created_by`, `updated_at` |
| `academy_visual_editor_published` | 학원당 1행 | `html`, `css`, `published_by`, `published_at` |

게시 여부와 렌더 모드는 별도 테이블이 아니라 `academies.page_settings.builderDraft["homepage.renderMode"]`(`"custom"` | `"template"`)에 저장된다. 이것이 시각 편집기와 기존 P0 데이터(`builderDraft`)의 **유일한 연결**이다(5장).

### 1.2 세 가지 표현과 누가 읽는가

| 표현 | 만든 곳 | 읽는 곳 |
|---|---|---|
| `projectData` | 편집기 `editor.getProjectData()` | **편집기 재불러오기**(우선 사용) |
| `html` | 편집기 `editor.getHtml()` (서버가 `<body>` 래퍼 제거·정제) | 게시 시 `published`로 복사 → 공개 페이지 |
| `css` | 편집기 `editor.getCss()` | 〃 |

세 표현은 **편집기가 한 번에 만들어 같이 보낸다.** 외부가 `html`만 바꾸면 어긋난다(8장 제한).

### 1.3 projectData 구조 (GrapesJS 0.23.6 실제 출력)

```jsonc
{
  "dataSources": [], "symbols": [],
  "assets": [ { "type": "image", "src": "data:image/png;base64,…" } ],     // 이미지 선택 창에 올린 파일(data URI)
  "styles": [                                                               // CSS 규칙 목록(188개 예시)
    { "selectors": ["#ve5"], "style": { "font-size": "36px", "margin": "0 0 16px" } },
    { "selectors": ["#ve5"], "style": { "font-size": "28px" }, "mediaText": "(max-width: 768px)", "atRuleType": "media" }
  ],
  "pages": [{
    "id": "…", "type": "main",                                              // ← 실제 출력에 있는 페이지 타입
    "frames": [{
      "id": "…",
      "component": {
        "type": "wrapper", "stylable": [ … ],
        "head": { "type": "head" },                                         // ← GrapesJS가 항상 붙이는 문서 껍데기
        "docEl": { "tagName": "html" },                                     // ←
        "components": [                                                     // 본문 트리
          { "tagName": "section", "attributes": { "id": "ve1", "data-preset": "ENGLISH" },
            "components": [ { "tagName": "h1", "type": "text", "attributes": { "id": "ve5" },
                              "components": [ { "type": "textnode", "content": "학원 이름" } ] } ] }
        ]
      }
    }]
  }]
}
```

- 규모 예시(P0 7섹션 불러오기 결과): `html` 7.3KB, `css` 22KB, `projectData` 51KB.
- 요소 스타일은 **`#id` 규칙**으로 저장된다(인라인 style 아님). 요소를 식별·수정할 때 `attributes.id`가 열쇠다.
- 정제(`VisualEditorSanitizer`)는 `head`/`docEl`/`pages[].type=main`을 **정해진 빈 모양일 때만** 보존한다. 그 밖의 `head` 내용, 트리 안의 `type=head`/`tagName=html`, `script`, `on*`, `javascript:`는 400이다. 허용 컴포넌트 `type`: `wrapper text textnode image video map link label comment cell row table thead tbody tfoot default`, 허용 `tagName`은 정제기 `ALLOWED_TAG_NAMES`.
- 한도: CSS < 65,536바이트(초과 400), HTML·projectData < 16MB, 요청 전체 33MB. 이미지 data URI는 `html`과 `projectData`에 **이중 저장**된다.

## 2. 저장·게시 흐름 (확정)

```text
편집 ──(250ms 디바운스)──▶ 저장본 지문과 비교 ──▶ hasChanges
   │                                              └─ true면 마지막 변경 30초 뒤 자동저장(없으면 안 함)
   └─ [저장] / Ctrl+S ──▶ PUT …/visual-editor/draft {html, css, projectData, revision}
        서버: 정제(html·css·projectData) → 크기 검사 → @Version revision 검증
              200 {revision} | 400(정제·한도·revision 누락) | 409(revision 불일치 → 편집기가 최신 불러오기 제안)
   └─ [게시] ──▶ (변경 있으면 먼저 저장) ──▶ POST …/publish {revision}
        서버: 학원 행 비관적 락(게시/게시취소 직렬화) → revision 검증 → 빈 초안이면 400
              → published 교체(draft.html/css 복사) → renderMode="custom"
              → 200 {renderMode, publicPath, publicReachable, message}
        publicReachable=false(학원이 비공개)면 메시지가 "방문자에게는 아직 보이지 않습니다"
   └─ [게시 취소] ──▶ POST …/unpublish → renderMode="template"(게시본·P0 데이터 보존)
공개: GET /api/academies/{slug}/homepage ──▶ academy_public.js가 Shadow DOM에 그림 (GNB·푸터는 P0 유지)
```

- revision: 최초 저장은 null/0, 이후는 서버가 돌려준 값. 내용이 같으면 revision은 올라가지 않는다.
- 공개 페이지에서 편집기 HTML은 Shadow DOM으로 격리되어 P0 사이트 CSS와 서로 영향이 없다(캔버스·미리보기·공개가 `visual_editor_base.css`를 공유).

## 3. GrapesJS 인스턴스 접근 방식

편집기 페이지(`/tutor/academy/editor`, TUTOR 쿠키 인증)에서 다음이 **현재 전역으로 노출**된다.

| 접근 | 내용 |
|---|---|
| `window.editor` | GrapesJS `Editor` 인스턴스(0.23.6). 아래 4장의 표준 API를 쓴다 |
| `window.__ve.state()` | `{ hasChanges, currentRevision, ready, isSaving }` — **`ready === true`가 된 뒤에만** 편집·저장을 시도한다(초안 불러오기가 끝나기 전에는 `false`) |
| `window.__ve.refreshStatus()` | 서버 `/status`를 다시 읽어 게시 상태 표시를 갱신 |
| `window.F2RVisualEditorRender` | 공유 렌더러(`mount`, `previewDoc`, `scopeCss`, `loadBaseCss`) |
| `window.F2RVisualEditorImport` | P0 불러오기 스냅샷(`snapshot({slug})`) |
| 버튼(DOM) | `#importBtn` `#previewBtn` `#saveBtn` `#publishBtn` `#unpublishBtn`, 패널 `#blocksPanel` `#stylePanel`, 상태 `#saveStatus` `#publishStatus` |

저장·게시·불러오기 **함수 자체는 노출되어 있지 않다**(IIFE 내부). 현재 프로그램적으로 저장하려면 `#saveBtn.click()`을 쓰거나, 3장 말미의 권장 어댑터를 구현 단계에서 추가해야 한다.

### 3.1 변경 감지는 프로그램적 변경도 자동으로 잡는다

편집기는 `editor.on('update')` → 250ms 뒤 `getHtml()+getCss()` 지문을 저장본과 비교한다. 따라서 `editor.setComponents/setStyle/Css.setIdRule`로 만든 변경도 **사용자가 만든 변경과 같이** `hasChanges=true`가 되고, 30초 자동저장·이탈 경고가 동작한다. Undo(`editor.UndoManager`)도 쓸 수 있다(불러오기 직후에는 `UndoManager.clear()`로 초기화됨).

### 3.2 권장 어댑터(구현 단계에서 추가, 아직 없음)

AI가 편집기를 다루는 진입점은 흩어진 전역 대신 **하나의 버전 있는 어댑터**로 고정하길 권한다.

```js
window.F2RVisualEditor = {
  version: 1,
  whenReady(): Promise<void>,                    // __ve.state().ready 대기
  snapshot(): { html, css, projectData, revision, hasChanges },
  applyProposal({ html, css }): void,            // 미리보기 캔버스가 아닌 본 캔버스에 적용(Undo 가능, 저장 아님)
  save(): Promise<boolean>,                      // 내부 saveContent(). 409/400 결과를 반환
  publish(): Promise<{ ok, publicReachable, message }>,
  onChange(cb): unsubscribe                       // 'update' 디바운스 이벤트
}
```
이 어댑터가 `window.editor`와 내부 함수를 감싸면, 에이전트 쪽은 GrapesJS 버전이 바뀌어도 이 계약만 지키면 된다.

## 4. 연동 지점 (뮤즈가 쓰는 표준 GrapesJS API)

모두 `window.editor` 기준이다.

| 목적 | API | 비고 |
|---|---|---|
| 현재 상태 읽기 | `getHtml()`, `getCss()`, `getProjectData()` | `getHtml()`은 `<body>…</body>` 래퍼를 포함 |
| 컴포넌트 트리 | `getWrapper()`, `getWrapper().find('h1')`, `getComponents()` | 요소 식별은 `getId()`/`getAttributes().id` |
| 텍스트 수정 | `cmp.components('새 문장')` 또는 `cmp.set('content', …)` | 텍스트 컴포넌트는 `textnode` 자식 |
| 구조 추가/교체 | `addComponents(html)`, `cmp.replaceWith(html)`, `setComponents(html)` | HTML은 서버 정제기를 통과해야 한다(허용 태그 한정) |
| 스타일 | `Css.setIdRule('ve5', { color: 'red' }, { mediaText: '(max-width: 768px)' })`, `cmp.addStyle()` | 모바일은 `mediaText '(max-width: 768px)'`(편집기 Mobile 디바이스 기준) |
| 이미지 교체 | `cmp.addAttributes({ src })`, `AssetManager.add({ type:'image', src })` | `src`는 `http(s)`/상대경로/`data:image/*`만(그 외 400) |
| 통째로 복원 | `loadProjectData(obj)` | 저장된 `projectData` 형태 그대로 |
| 선택/이동 | `select(cmp)`, `Canvas.scrollTo(el)` | 승인 UI에서 변경 위치 하이라이트에 사용 |
| 기기 전환 | `setDevice('Mobile'|'Tablet'|'Desktop')` | 캔버스 폭이 실제 375/768px |
| 되돌리기 | `UndoManager.undo()/redo()` | 적용 직후 사용자가 되돌리기 가능 |
| 변경 이벤트 | `on('update')`, `on('component:update')`, `on('load')` | |

**연동 지점(서버):** 현재 서버에는 에이전트용 진입점이 없다. 설계 문서 6장의 `proposals` API와 Bearer 토큰이 추가될 지점이다. 현재 쓸 수 있는 서버 API는 사용자 쿠키 기반의 [API.md](API.md) 표뿐이다.

## 5. builderDraft(P0)와의 현재 연결

- **연결은 `homepage.renderMode` 키 하나뿐**이다. 게시하면 `custom`, 게시 취소하면 `template`으로 바뀌며 **다른 `builderDraft` 키·학원 이름·소개·상태는 건드리지 않는다**(MySQL 통합 테스트로 보존 확인).
- `renderMode=custom`일 때 공개 페이지는 GNB·푸터(P0)만 유지하고 **본문은 편집기 게시본만** 그린다. 따라서 `builderDraft`의 P0 필드(원장 소개·철학·전화 등)는 **방문자 화면에 나타나지 않는다.**
- 편집기 → P0 방향의 연결은 "기존 홈페이지 불러오기" 한 번뿐이다. P0 공개 페이지를 PC·모바일 폭으로 렌더해 **보이는 결과를 HTML+CSS 스냅샷**으로 가져온다(영상·애니메이션·SVG·폼·아이콘 폰트는 제외). 이후 P0 데이터가 바뀌어도 편집기 내용은 따라가지 않는다.

## 6. AI Agent가 수정한 내용은 편집기·게시 화면에 반영되는가 (현재 빌드 실측)

기존 P0 AI(`/api/tutor/academy/ai/chat`, `/apply`)와 REST 클라이언트를 각각 확인했다.

| 경로 | 편집기 | 게시 화면(공개) |
|---|---|---|
| **P0 AI 채팅 apply** (`builderDraft` 허용 필드 15개: 원장 소개·철학·전화·카카오 등) | 반영 안 됨(연결 없음) | `renderMode=custom`이면 **반영 안 됨**(P0 본문을 그리지 않음). `template`일 때만 반영 |
| **REST로 `html`만 `PUT draft`** (`projectData` 그대로) | **옛 내용이 보인다**(`projectData` 우선 로드) | 게시하면 **새 내용이 나간다**(`html` 복사) → 편집기와 공개가 어긋남 |
| 위 상태에서 사용자가 편집기에서 저장 | 편집기의 옛 상태가 `html`을 덮어써 **에이전트 변경이 사라진다** | — |
| REST로 `projectData`를 `"{}"`로 `PUT` | 편집기가 `html`에서 다시 만들어 **보인다**(GrapesJS 메타데이터는 사라짐) | 게시하면 반영 |

→ 뮤즈가 서버 API로 `html`만 바꾸는 방식은 쓰면 안 된다. 설계 문서의 방안 C(제안 → 편집기가 적용 → 편집기가 세 표현을 함께 저장)가 필요하다.

## 7. 지금 있는 안전장치

- 저장: 서버 정제(HTML Jsoup 허용 목록, CSS 위험 패턴 제거, projectData 구조 검사) + 공개 출력 직전 재정제. 변조된 `head`/`script` 거부는 단위·통합 테스트로 고정.
- 동시성: `@Version` revision 충돌은 409. 게시·게시 취소는 학원 행 락으로 직렬화(데드락 없음).
- 접근 제어: 비로그인 편집기 403·API 401, 학생 403, 소유 학원 없음 403.
- 회귀: 실제 GrapesJS 0.23.6 출력 fixture(`src/test/resources/visual-editor/grapesjs-project.json`), 실제 브라우저 E2E 63항목(`e2e/visual-editor`).

## 8. 알려진 제한 (미해결)

1. **AI 연동 없음:** 6장의 어긋남 구조가 그대로다. 구현 전까지 에이전트가 `PUT draft`를 직접 쓰지 않아야 한다. (설계 4.1의 `projectDataHash` 보조 안전장치는 미구현)
2. **CSS 64KB 한도:** `css` 컬럼이 TEXT다. 큰 페이지(특히 P0 불러오기)는 한도에 가까워질 수 있다(예시 7섹션 22KB). 초과 시 저장이 400이며 편집기가 안내한다. 늘리려면 마이그레이션이 필요하다(DOMAIN.md 동시 갱신).
3. **불러오기 충실도:** 스냅샷이라 애니메이션·영상·SVG·폼·아이콘 폰트는 빠지고, PC(1440)·모바일(390) 두 폭의 값만 반영한다(중간 폭 반응형은 근사). 이후 P0 변경은 따라가지 않는다.
4. **외부 CDN 의존:** GrapesJS(unpkg), Pretendard(jsdelivr), font-awesome(cdnjs)을 런타임에 외부에서 받는다. 장애 시 편집기가 뜨지 않는다.
5. **이력 없음:** 초안 이력(롤백)이 없다. 저장하면 이전 초안은 사라진다.
6. **이미지 이중 저장:** 업로드 이미지가 `html`·`projectData`에 data URI로 각각 들어가 용량이 커진다(별도 업로드 저장소 없음).
7. **권한 모델:** 에이전트 전용 인증이 없다(쿠키 JWT만). 설계 문서 8장 참조.
8. **기존 테스트 실패 6건(제 변경과 무관):** `JipijigiAnswerSessionDbTest`(NewStart, H2 컨텍스트 로드 실패). 수정 전 코드에서도 동일하게 실패한다.
