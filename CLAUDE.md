# Claude Code 가이드라인

## 성격 및 말투
- 간결하고 명확하게 답변할 것.
- 주니어 가르치듯 가능한 상세하게 설명할것

## 코딩 스타일
- 함수 생성시 언제나 unit test 생성
- 테스트 코드는 given/when/then 구조로 생성


## 작업 착수 전 (필수)
1. **BuildIt 관련 기능을 수정하기 전에 `docs/BUILDIT_PHILOSOPHY.md`를 먼저 읽는다.**
2. **`docs/INDEX.md`를 읽고** 해당 작업 문서를 로드한다.
3. 현재 git 상태 / 브랜치 확인. **ChatGPT Work / Codex 세션은 [docs/WORK_GIT_WORKSPACE_WORKFLOW.md](docs/WORK_GIT_WORKSPACE_WORKFLOW.md)를 먼저 읽는다** (세션별 stale checkout·`origin/main` 오판 방지, GitHub 최신 main이 authoritative).
4. DB/마이그레이션을 건드리면 **`docs/WORKFLOW.md` §4 번호정책**을 반드시 확인한다.

> 규칙·스펙의 단일 출처는 `docs/`다. 이 파일은 얇은 진입점 + Claude 전용 규칙(아래)만 둔다.
> AGENTS.md(OMX용)와 핵심 규칙은 동일하게 유지한다.

## 핵심 규칙 (요약 — 상세는 docs/)
- **main 단일 로컬 개발**: 기본 작업은 `main`에서 수행하고 `./scripts/run-local.sh`가 로컬 MySQL(`localhost:3308/f2r_db`)을 자동 생성·재사용한다. 검증 후 PR로만 병합하고, 운영 배포는 수동 GitHub Actions로만 수행한다. → [docs/WORKFLOW.md](docs/WORKFLOW.md)
- **마이그레이션 번호**: `db/migration`=운영 순차(다음 V909+), `db/dev-seed`=`R__` repeatable(버전번호 미사용), 운영 위치 버전 중복·번호 역행 금지(= prd 502 원인), 적용된 V_파일 불변. → [docs/WORKFLOW.md](docs/WORKFLOW.md) §4
- **스키마 동기화**: `db/migration` V_파일 + Entity + [docs/DOMAIN.md](docs/DOMAIN.md) 셋 동시 수정.
- **API 계약**: endpoint 변경 시 [docs/API.md](docs/API.md) 동시 수정.
- **PR/배포**: 로컬 검증 후 PR로 `main`에 병합한다. prd는 EC2 환경변수의 `prd` profile + 수동 GitHub Actions 배포만 사용한다. main 직접 push 금지. → [docs/WORKFLOW.md](docs/WORKFLOW.md)
- **CRUD 범위 확인**: 사용자가 객체를 생성하게 만들 때, 구현 전 Update/Delete 포함 여부 + 삭제 정책(물리/soft/참조차단/cascade) + 권한을 확인. Create-only면 사유 보고.
- **문서 동기화**: 코드·스키마·문서 중 하나만 수정 금지 → [docs/INDEX.md](docs/INDEX.md)
- **이슈/PR 작성**: [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) 형식 그대로 (타입 prefix + 목표/범위/검증/향후/관련).

## 제품 스펙
- 범위·도메인·플로우: [docs/INDEX.md](docs/INDEX.md) → MVP.md / DOMAIN.md / FLOWS.md
- MVP 핵심 원칙: Spring Boot가 DB 직접 접근(Python 서버 없음) · 인증 JWT(HS256) HTTP-only Cookie · 프론트 Thymeleaf SSR · **모든 Claude API 호출에 Prompt Caching 필수**.

## Anthropic Claude API 사용 규칙

### 의존성
```groovy
// build.gradle
implementation 'com.anthropic:anthropic-java:2.27.0'
```
버전은 [Maven Central](https://central.sonatype.com/artifact/com.anthropic/anthropic-java)에서 최신 stable 버전으로 유지한다.

### API Key 관리
- **YAML 파일에 절대 키를 저장하지 않는다**
- **로컬 (direnv 기반, 프로젝트 진입 시에만 환경변수 로드)**:
  - 실제 비밀값은 repo 바깥인 `~/.config/f2r/env`에 보관: `export ANTHROPIC_API_KEY=sk-ant-...` (perm 600)
  - repo 루트의 `.envrc`가 위 파일을 source — `cd` 시점에 direnv가 자동 로드, 디렉터리를 벗어나면 unload
  - `./scripts/run-local.sh`는 이 파일을 선택적으로 로드하지만, 기본 부팅에는 키가 필수가 아님(`anthropic.enabled: false`)
  - direnv 미설치 환경에서는 `.envrc`가 무시되므로 빌드/배포에 영향 없음
- **운영 (EC2)**: `JWT_SECRET`과 동일한 패턴으로 `/etc/f2r/env` + systemd `EnvironmentFile`에 `ANTHROPIC_API_KEY`를 함께 주입
- 코드는 양쪽 모두 `AnthropicOkHttpClient.fromEnv()`로 `ANTHROPIC_API_KEY` 환경변수를 자동으로 읽는다
- `application-*.yaml`에는 모델명 등 비민감 설정만 기재한다
- **Claude Code Bash 도구 사용 규칙**: 로컬 앱 실행은 `./scripts/run-local.sh`를 우선 사용한다. 이 스크립트가 DB 자동 기동과 `local` profile 고정을 담당한다. `ANTHROPIC_API_KEY`가 필요한 별도 명령은 `direnv exec . <cmd>`로 로컬 `.envrc`를 명시적으로 로드한다.

```yaml
# application-local.yaml 예시
anthropic:
  # api-key는 ANTHROPIC_API_KEY 환경변수로 주입
  model: claude-sonnet-4-6
```

### 클라이언트 초기화 패턴
```java
// Spring Bean으로 등록하거나 서비스 생성자에서 초기화
AnthropicClient client = AnthropicOkHttpClient.fromEnv();
```

- 단위 테스트에서는 `mock(AnthropicClient.class)`를 주입하는 패키지 전용 생성자를 제공한다
- 실제 API 호출이 필요한 통합 테스트는 별도 클래스(`*IntegrationTest`)로 분리한다

### Claude 비활성 스위치 (데모 단계 — 전 환경 기본 OFF)

데모 단계에는 **모든 환경(prd 포함)에서 Claude 연동을 끈다.** ANTHROPIC_API_KEY 없이도 부팅되고, 관련 기능 호출 시 503 + 데모 안내가 나간다.

- **전역 스위치**: base `application.yaml`의 `anthropic.enabled: false`. 프로필 config(`application-<p>.yaml`)는 이 값을 상속한다(override 안 하면 전부 OFF).
- **서비스 게이팅**: Claude를 쓰는 서비스는 `@Value("${anthropic.enabled:true}")`로 플래그를 읽고, 생성자에서 `this.client = enabled ? AnthropicOkHttpClient.fromEnv() : null`. → **false면 startup에 `fromEnv()`를 호출하지 않아 키 없이 부팅**된다. 대상: `ClaudeChatService`, `ExamExtractionService`, `ExamMetaInferenceService`, `SourceAnalysisService`.
- **호출 차단**: 비활성 상태에서 기능을 호출하면 `DemoFeatureDisabledException`을 던지고 `GlobalExceptionHandler`가 **503 + 안내 메시지**로 변환한다.
- **⚠️ 기본값 함정**: `@Value`의 default가 `true`라 **base yaml에 `anthropic.enabled: false`가 없는 브랜치는 자동으로 켜진다** → 키 삭제 상태면 부팅 크래시. 전 환경 OFF를 유지하려면 base yaml 스위치가 반드시 있어야 한다.
- **재활성(데모 종료)**: base를 `true`로 바꾸거나, 특정 프로필 config에서 `anthropic.enabled: true`로 override.

### Prompt Caching 적용 규칙 (필수)

**모든 Claude API 호출에 캐싱을 적용한다.** 대화가 길어질수록 히스토리 + 파일 내용이 매 요청마다 누적되므로, 캐싱 없이는 토큰 비용이 감당 불가능하다.

| 캐싱 대상 | 이유 |
|-----------|------|
| 시스템 프롬프트 | 모든 요청에서 동일 |
| 첨부 파일 내용 (이미지/텍스트) | 대화 내내 변하지 않음 |
| 대화 히스토리 앞부분 | 새 메시지 추가 전까지 변하지 않음 |

캐시 TTL은 5분이며, 캐시 히트 시 **입력 토큰 90% 절감**된다.

```java
// 시스템 프롬프트에 캐싱 적용
SystemParam systemWithCache = SystemParam.ofTextBlockParams(List.of(
    TextBlockParam.builder()
        .text(systemPrompt)
        .cacheControl(CacheControlEphemeral.builder().build())  // ← 필수
        .build()
));

// 첨부 파일(이미지)에 캐싱 적용
ContentBlockParam imageBlock = ContentBlockParam.ofImage(
    ImageBlockParam.builder()
        .source(Base64ImageSource.builder()
            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
            .data(base64EncodedData)
            .build())
        .cacheControl(CacheControlEphemeral.builder().build())  // ← 필수
        .build()
);

// 텍스트 블록에 캐싱 적용 (긴 파일 내용 등)
ContentBlockParam textBlock = ContentBlockParam.ofText(
    TextBlockParam.builder()
        .text(fileContent)
        .cacheControl(CacheControlEphemeral.builder().build())  // ← 필수
        .build()
);
```

**대화(Conversation) API 호출 패턴:**
```java
// 1. 시스템 프롬프트 (캐싱)
// 2. 첨부 파일 블록들 (캐싱) — 대화 내내 변하지 않으므로 캐시 효과 극대화
// 3. 대화 히스토리 메시지들
// 4. 새 유저 메시지
Message response = client.messages().create(
    MessageCreateParams.builder()
        .model(model)
        .maxTokens(4096)
        .system(systemWithCache)
        .messages(buildMessagesWithCachedFiles(conversationId, newMessage))
        .build()
);
```

### 메시지 생성 패턴 (Vision — 단일 호출)
```java
// 캐싱 없이 단발성 Vision 분석 (숙제 문제 추출 등)
ContentBlockParam imageBlock = ContentBlockParam.ofImage(
    ImageBlockParam.builder()
        .source(Base64ImageSource.builder()
            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
            .data(base64EncodedData)
            .build())
        .build()
);

Message message = client.messages().create(
    MessageCreateParams.builder()
        .model(model)
        .maxTokens(4096)
        .addUserMessageOfBlockParams(List.of(imageBlock, textBlock))
        .build()
);

// 응답 텍스트 추출
String text = message.content().stream()
    .flatMap(block -> block.text().stream())
    .map(TextBlock::text)
    .findFirst()
    .orElseThrow();
```

> 단발성 Vision 호출(숙제 문제 추출, 진단 문제 생성)은 반복 요청이 없으므로 캐싱 효과가 없다. 대화처럼 반복 전송되는 콘텐츠에만 캐싱이 의미 있다.

### PDF 지원 (미구현 — TODO)
PDF는 Files API(beta) 사용이 필요하다. 구현 시 `com.anthropic.models.beta.files` 패키지를 참고한다.

---

# 행동 가이드라인 (Andrej Karpathy Skills 기반)

> 출처: https://github.com/forrestchang/andrej-karpathy-skills/blob/main/CLAUDE.md
> LLM 코딩 시 흔한 실수를 줄이기 위한 행동 지침. 프로젝트별 지시사항과 병행 적용.

**Tradeoff:** 이 가이드라인은 속도보다 신중함을 우선한다. 사소한 작업에는 판단에 맡길 것.

## 1. 코딩 전에 먼저 생각하라

**가정하지 말 것. 혼란을 숨기지 말 것. 트레이드오프를 드러낼 것.**

구현 전에:
- 가정을 명시적으로 밝힐 것. 불확실하면 질문할 것.
- 여러 해석이 가능하면 모두 제시할 것 — 혼자 선택하지 말 것.
- 더 간단한 접근법이 있다면 말할 것. 필요시 반박할 것.
- 불명확한 게 있으면 멈출 것. 무엇이 헷갈리는지 명명할 것. 질문할 것.

## 2. 단순함 우선

**문제를 해결하는 최소한의 코드. 추측성 코드 금지.**

- 요청되지 않은 기능 추가 금지.
- 단발성 코드를 위한 추상화 금지.
- 요청되지 않은 "유연성"이나 "설정 가능성" 금지.
- 일어날 수 없는 시나리오에 대한 에러 핸들링 금지.
- 200줄을 썼는데 50줄로 가능하면 다시 쓸 것.

자문할 것: "시니어 엔지니어가 이걸 보면 과하다고 할까?" 그렇다면 단순화할 것.

## 3. 외과적 변경

**필요한 것만 건드릴 것. 본인이 만든 흔적만 정리할 것.**

기존 코드 수정 시:
- 인접 코드, 주석, 포맷팅을 "개선"하지 말 것.
- 망가지지 않은 것을 리팩토링하지 말 것.
- 본인이라면 다르게 했더라도 기존 스타일에 맞출 것.
- 관련 없는 죽은 코드를 발견하면 — 삭제하지 말고 언급만 할 것.

본인 변경으로 고아가 생기면:
- **본인 변경으로** 사용되지 않게 된 import/변수/함수를 제거할 것.
- 요청받지 않은 한 기존의 죽은 코드는 제거하지 말 것.

테스트: 변경된 모든 라인은 사용자 요청과 직접 연결되어야 한다.

## 4. 목표 주도 실행

**성공 기준을 정의할 것. 검증될 때까지 반복할 것.**

작업을 검증 가능한 목표로 변환할 것:
- "검증 추가" → "잘못된 입력에 대한 테스트를 작성한 후 통과시키기"
- "버그 수정" → "버그를 재현하는 테스트를 작성한 후 통과시키기"
- "X 리팩토링" → "리팩토링 전후로 테스트가 통과하는지 확인하기"

다단계 작업의 경우 짧은 계획을 명시할 것:
```
1. [단계] → 검증: [확인]
2. [단계] → 검증: [확인]
3. [단계] → 검증: [확인]
```

강력한 성공 기준이 있어야 독립적으로 루프를 돌 수 있다. 약한 기준("작동시켜")은 끊임없는 명확화를 요구한다.

---

**이 가이드라인이 잘 작동하고 있다면:** diff에 불필요한 변경이 줄어들고, 과도한 복잡성으로 인한 재작성이 줄어들고, 명확화 질문이 실수 후가 아닌 구현 전에 나온다.

---

# Trello ↔ ROADMAP 동기화 커맨드

Trello 보드가 현재 작업 상태의 source of truth다. GitHub Issue는 생성 시 Trello Todo 카드가 생기고, Trello 카드를 Done으로 이동하면 GitHub Issue가 closed될 수 있다. 따라서 ROADMAP 정리 시 Trello 상태를 우선한다.

## 인증

로컬 Trello API 인증값은 Git에 커밋하지 않고 아래 개인 env 파일에 둔다.

```bash
~/.config/f2r/env
```

필요 변수:

```bash
export TRELLO_KEY="..."
export TRELLO_TOKEN="..."
# 선택: export TRELLO_BOARD_ID="4ZmQjQHe"
```

## 실행

비교 리포트만 생성:

```bash
scripts/sync-roadmap-from-trello.sh
```

ROADMAP까지 Trello 기준으로 갱신:

```bash
scripts/sync-roadmap-from-trello.sh --apply
```

기본 리포트 출력 위치:

```bash
docs/temp/roadmap-sync-report.md
```

## 구현 파일

- `scripts/sync-roadmap-from-trello.sh` — env 로드 후 Python 스크립트 실행
- `scripts/sync-roadmap-from-trello.py` — Trello 보드, GitHub open issue, `docs/ROADMAP.md` 비교/갱신

## 운영 메모

- Trello `Todo`, `In Progress`는 활성 작업으로 본다.
- Trello `Done`, `Archived`는 완료/처리됨으로 본다.
- Trello Starter Guide 및 테스트성 완료 카드는 리포트/동기화에서 일부 무시한다.
- GitHub Issue가 OPEN이어도 Trello에서 Archived/Done이면 Trello를 우선해 완료 처리할 수 있다.
