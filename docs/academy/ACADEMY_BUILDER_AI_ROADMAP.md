# Academy Builder — AI-Neutral Architecture Roadmap

작성일: 2026-09-05

목표: Academy Builder를 "입력 폼 중심"에서 "핵심 정보만 입력하면 AI가 설명·작성·다듬기를
도와주는 Builder"로 단계적으로 전환한다. 이번 PR은 **설계 문서화 + 최소 UI/데이터 정리**까지만
다루고, 실제 AI 연동(API key, 과금, MCP 서버)은 하지 않는다.

기존 코드 조사 결과, 이 프로젝트에는 이미 두 가지 검증된 AI 연동 패턴이 있다 — 아래 설계는
이 패턴을 재사용하는 것을 전제로 하며, 새 프레임워크를 만들지 않는다.

## 0. 기존에 이미 있는 것 (중복 구현 금지)

| 패턴 | 위치 | 용도 |
|---|---|---|
| **WebMCP Site Tools** | `static/js/admin_chart_site_tools.js` + `ChartSiteToolController`/`ChartSiteToolService` + `ChartSiteToolsContractTest` | 브라우저의 `document.modelContext.registerTool()`로 관리자 페이지 REST 엔드포인트를 AI 도구로 등록. 별도 API key/MCP 서버 없이 페이지의 기존 ADMIN 세션을 그대로 쓴다. `docs/DOMAIN.md`(~3394-3557행)에 워크스페이스 헤더 규칙까지 문서화되어 있다. |
| **외부 MCP 서버 프록시** | `BuildItWikiProxyController` → Python FastAPI `buildit-wiki.service`(별도 저장소, `deploy-ec2.yml`에서 배포) | Claude/GPT 같은 외부 MCP client가 Bearer/API-Key로 인증해 접근. Wiki 학습자료·생성문항 컨텍스트 제공용. |
| AI provider 상태 | `GET /api/ai/providers`, `docs/AWS_DEPLOYMENT.md:406` | OpenRouter/Gemini/OpenAI 키 구성 여부를 노출(관리자 `admin/chart.html`에서 서버 메모리에만 보관, DB/Git에 저장하지 않음). |
| `docs/academy-ai-page-generation.md` | 기존 문서 | Academy 공개 페이지를 AI가 채워주는 것에 대한 이전 설계(Model A: 고정 렌더러 + AI가 typed content만 채움 — 2026-07-10 확정. Model B: AI가 HTML/CSS 전체 생성 — 보류). **이번 로드맵은 Model A 방향과 일치한다**: AI는 구조를 만들지 않고 텍스트만 채운다. |

새 AI 기능은 이 셋 중 하나의 확장으로 설계한다 — Academy Builder AI 버튼은 아래 §2의 Task/Provider
분리를 따르되, 실행 경로는 (1) API AI(§4, 신규 소량), (2) Browser AI(§5, WebMCP 패턴 재사용),
(3) 외부 MCP(§6, BuildItWikiProxyController 패턴 재사용) 세 갈래로 열어 둔다.

## 1. 사용자 경험 원칙

각 입력 항목 옆에 인라인 버튼 3개만 둔다. 별도 채팅창을 열지 않는다.

```
[교육 철학 입력란]
💡 설명해줘   → 이 칸에 뭘 쓰면 되는지 1~2문장 안내(정적 텍스트, AI 호출 없음 — 가장 싸고 먼저 만들 수 있음)
✨ 작성해줘   → 현재 Academy Context로 초안 생성(AI 호출, 짧은 output)
✏️ 다듬어줘   → 사용자가 쓴 문장을 홈페이지 톤으로 개선(AI 호출, 짧은 input+output)
```

"설명해줘"는 AI가 전혀 필요 없는 정적 힌트라서 §7 Context Layer 없이도 이번 PR 이후 바로 구현
가능하다. "작성해줘"/"다듬어줘"만 실제 AI 호출이 필요하다.

## 2. 전체 파이프라인

```
Academy Data (DB)
      ↓
AI Context Layer  (§3 — task별 최소 context만 조립, 전체 DB 스캔 금지)
      ↓
AI Task            (§7 — "무엇을 하는가", provider를 모른다)
      ↓
AI Gateway         (§8 — task를 provider로 라우팅)
   ↙     ↓      ↘
API AI   Browser AI   MCP        (§4, §5, §6 — provider 구현)
(신규)   (WebMCP 재사용)  (프록시 재사용)
```

핵심 자산은 특정 모델이 아니라 **"학원의 구조화된 Context + AI가 실행할 수 있는 Task 목록"**이다.
Provider를 나중에 통째로 바꿔도 Task 정의와 UI 버튼은 그대로 유지되도록 설계한다.

## 3. AI Context Layer (설계만, 이번 PR에서 구현 안 함)

Academy 전체 row를 AI에게 넘기지 않는다. Task마다 필요한 최소 필드만 조립한다.

```java
// 설계 스케치 — 이번 PR에서 실제 클래스를 만들지 않는다.
interface AcademyAiContext {
  String taskId();          // 예: "rewrite_director_intro"
  Map<String, String> fields();  // task가 선언한 필드만 포함
}
```

예시 매핑(§1의 field inventory 문서 기준, A급 필드 우선):

| taskId | context 필드 | 근거 |
|---|---|---|
| `rewrite_director_intro` | `directorName`(principalName), `academyName`(name), `currentIntro`(principalDesc), `philosophy`(philosophyItems) | 원장 소개문은 학원 정체성과 철학을 알아야 자연스럽다 |
| `rewrite_lesson_summary` | `lessonName`, `targetStudent`(learnerNeed), `currentSummary`(kicker), `keyFeatures`(highlight) | 수업 카드 단위로 완결된 context |
| `rewrite_philosophy_item` | `academyName`, `subjects`, `targetGroups`, `currentText` | 학원 정체성 + 현재 문구 |
| `explain_field`(💡) | 없음(정적 안내 문자열 사전 정의) | AI 호출 자체가 필요 없음 |

Context 조립은 §1 문서의 A급 필드 목록에서만 가져온다 — C/D/E급 필드(가격/일정 상세, 내부
`creationKey` 등)는 어떤 task의 context에도 넣지 않는다(불필요한 토큰 낭비 + 개인정보/운영정보
노출 최소화).

## 4. API AI 경로 (이번 PR에서 실제 연결 안 함)

Builder 내부 AI 버튼의 기본 경로가 될 후보. 향후 지원해야 할 것(구현은 NEXT 이후):

- 사용자(강사)별 quota, 일/월 사용량 카운터
- Context Layer가 만든 짧은 context만 전송(§3), 짧은 output 강제(max_tokens 제한)
- 저비용 모델 우선(예: Haiku급), 캐시(같은 academy+task+field 조합 반복 호출 방지)
- rate limit(초당/분당)
- **API key는 프론트엔드에 절대 노출하지 않는다** — 기존 `ChartGeminiClient`/`LunaProblemTransformationService`처럼 서버 사이드에서만 호출

## 5. Browser AI 경로 (이번 PR에서 실제 연결 안 함)

기존 WebMCP Site Tools 패턴(`admin_chart_site_tools.js` 계열)을 재사용한다. Academy Builder에
적용할 때 고려할 것:

- 사람이 보는 Builder 화면 자체는 지금처럼 단순하게 유지한다(AI 때문에 DOM을 복잡하게 만들지
  않는다).
- AI-readable 구조가 필요하면 화면과 별도로 semantic 메타데이터 계층을 둔다(예: 각 필드의
  taskId/context 스키마를 `data-ai-task` 속성이나 별도 JSON으로 제공) — 사람이 보는 마크업을
  오염시키지 않는다.
- 기존 `ChartSiteToolController`/`ChartSiteToolService` + `ChartSiteToolsContractTest` 구조를
  그대로 본떠 `AcademyBuilderSiteToolController`(가칭)를 나중에 추가하는 방식을 권장 — 새 패턴을
  만들지 않는다.

## 6. MCP 경로 (이번 PR에서 전체 구현 안 함)

외부 AI(ChatGPT/Claude 등)가 Academy 데이터를 사용할 수 있게 READ/WRITE를 명확히 분리한다.
`BuildItWikiProxyController` + Python FastAPI MCP 서버 패턴을 재사용하는 것을 전제로 한다.

```
READ (인증만 있으면 바로 가능한 후보)
  get_academy_profile(academyId)
  get_director_profile(academyId)
  get_lessons(academyId)
  get_homepage_sections(academyId)

WRITE (반드시 사용자 승인 + 인증/권한 + validation + audit 전제)
  update_director_intro(academyId, text)
  update_lesson_summary(lessonId, text)
  update_homepage_section(academyId, sectionKey, text)
```

WRITE는 기존 `AcademyService`의 `validateBuilderDraft`(화이트리스트) / `validatePageSettings` 같은
검증을 그대로 통과해야 하며, 별도 우회 경로를 만들지 않는다 — MCP WRITE도 결국 같은
`PUT /api/tutor/academy(/page-settings)` 경로를 내부적으로 타야 한다(§ 5-2 hidden-field audit에서
확인한 것처럼 이 경로들의 보존 규칙이 이미 이번 PR로 하나 더 단단해졌다).

## 7. Provider/Task 분리 원칙

```
Task:     rewrite_director_intro
Provider: OpenAI | OpenRouter | Browser AI | MCP client | (future)
```

Task는 Provider 구현을 직접 알지 않는다. 인터페이스 스케치:

```java
interface AiTask {
  String id();
  AcademyAiContext buildContext(Academy academy, ...);
  String promptTemplate();     // provider-agnostic
}

interface AiProvider {
  String complete(String prompt, AiTaskLimits limits);
}
```

새 provider가 생기면 `AiProvider` 구현체 하나만 추가한다 — Task 정의나 Builder UI는 바뀌지 않는다.
아직 공식 API/MCP 사양이 확정되지 않은 외부 서비스는 추측 구현하지 않는다.

## 8. 진행 상태

### DONE

- 대표 수업 기본 UI 간소화(이전 작업)
- 대표 수업 이미지 즉시 미리보기 수정(PR #172)
- **대표 원장 기본 원형 아바타 제거**(이번 PR)
- **대표 원장 영상 / 수업 영상 분리**(`academy_videos.purpose`, 이번 PR)
- **대표 수업 이미지 크기/비율 개선**(object-fit: contain + 실측 aspect-ratio, 이번 PR)
- **Builder field inventory 문서화**(이번 PR, `ACADEMY_BUILDER_FIELD_INVENTORY.md`)
- **hidden-field preservation audit**(이번 PR — `builderDraft` 전체 유실 위험 발견 및 수정)
- **AI-neutral architecture 설계**(이번 문서)

### NEXT

- 공통 AI Action UI(💡/✨/✏️ 버튼 컴포넌트) — "설명해줘"부터 시작(AI 호출 없이 정적 힌트만)
- AI Context Layer 실제 구현(§3의 인터페이스를 실제 Java 클래스로)
- API AI 소규모 PoC(원장 소개문 "다듬어줘" 1개 task만, 저비용 모델, 엄격한 output 길이 제한)
- 사용자별 quota/사용량 저장 스키마

### LATER

- Browser AI adapter(§5, WebMCP Site Tools 패턴을 Academy Builder로 확장)
- MCP tools(§6, READ 먼저 → WRITE는 승인/감사 체계까지 검증 후)
- 외부 AI provider adapter 추가(공식 사양 확정 후에만)
- AI 홈페이지 전체 점검(Model B 재검토 여부는 `docs/academy-ai-page-generation.md`의 2026-07-10
  결정을 재확인한 뒤 판단)

## 9. 이번 PR에서 하지 않은 것 (명시)

- OpenAI/Claude API 본격 연결, API key 추가, AI 과금 시스템
- 전체 MCP server 구현
- 불필요한 대규모 DB migration
- 기존 Academy 데이터 삭제
- 홈페이지 전체 redesign
- 기존 정상 기능 대규모 refactor
