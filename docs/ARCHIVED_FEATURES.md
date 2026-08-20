# Archived Feature Routes

> 이 alias 장치는 main에 머지 완료다(`ArchivedRouteAliasFilter` / `ArchivedRouteAccessFilter` / `ArchivedRoutesProperties`). 보존용 feature 브랜치는 역할을 다해 정리했다.

> 요구사항 변경으로 상용 화면에서 숨기거나 기존 public flow에서 제외한 기능을 **소스 복사 없이** 보존하기 위한 라우팅 정책이다.

## 목적

- 기능 코드는 삭제하지 않고 유지한다.
- 기존 컨트롤러/서비스/템플릿을 복사하지 않는다.
- 보존 접근은 `/archived/**` alias로만 제공한다.
- 운영 노출은 설정과 권한으로 통제한다.

## 라우팅 계약

`/archived/{path}`는 최초 `REQUEST` 안에서 canonical target `/{path}`로 바뀐다. servlet
forward를 만들지 않으므로 JWT, Origin/Referer, Spring Security URL 인가와 MVC handler가 모두
같은 target path를 한 번만 평가한다.

예시:

| 보존 경로 | canonical target |
|---|---|
| `/archived/conversation/dashboard` | `/conversation/dashboard` |
| `/archived/api/conversations` | `/api/conversations` |
| `/archived/tutor/academy/posts` | `/tutor/academy/posts` |

기존 Controller, Service, DTO, 소유권 검증, validation을 그대로 사용한다. 보존 라우트 권한은
target 권한을 대체하지 않고 추가된다. 즉 `archive 접근 권한 AND target URL 권한/소유권`을 모두
만족해야 한다. 예를 들어 ADMIN archive gate를 통과해도 TUTOR 전용 target은 403이다.

필터 순서는 `SubdomainAcademyFilter → ArchivedRouteAliasFilter → JwtAuthenticationFilter →
ArchivedRouteAccessFilter → OriginWhitelistFilter → UsernamePasswordAuthenticationFilter →
AuthorizationFilter`로 고정한다. 현재 보장 범위는 하나의 catch-all `SecurityFilterChain`이다.
path별 chain을 추가하면 chain 선택 전에 alias를 정규화하거나 archived path를 명시적으로 반영해야 한다.

정확히 `/archived` 또는 `/archived/**`만 alias다. `/archived-preview` 같은 near-prefix는 원래
요청으로 남고, 중첩 `/archived/archived/**`, ambiguous encoded 첫 segment, slash/backslash,
dot-segment, semicolon/matrix 첫 segment는 400으로 거부한다. `/archived`와 `/archived/`는
`/home`으로 해석된다.

## 접근 정책

기본 설정은 `application.yaml` 기준 다음과 같다.

```yaml
app:
  archived:
    enabled: true
    require-admin: true
```

- `enabled=false`: `/archived/**` 전체 404 (JWT/Origin보다 우선)
- `enabled=true`, `require-admin=true`: ADMIN만 접근 가능
- `enabled=true`, `require-admin=false`: 로그인 사용자 접근 가능

apex host의 archive write 실패 우선순위는 `disabled 404 → unauthenticated 401 → archive role
403 → Origin/Referer 403 → target 인가/handler 결과`다. Academy subdomain의 비자산 archived
경로는 alias 처리 전에 apex의 동일 `/archived/**` URL로 301된다.

상용에서 완전히 숨겨야 하면 prd 환경 설정에서 `app.archived.enabled=false`로 override한다.

## 프론트 호출 정책

공통 `fetchAuth` / `fetchJsonAuth` / `ensureCurrentUser`는 현재 pathname이 정확히 `/archived`
또는 `/archived/**`일 때만 `/api/...` 호출을 `/archived/api/...`로 자동 변환한다.

단, 일부 화면에는 `fetch('/api/...')` 직접 호출이 남아 있다. 특정 기능을 기존 경로에서 완전히 차단하고 `/archived`에서만 살릴 때는 해당 기능 화면의 직접 fetch를 다음 중 하나로 바꾼다.

- `fetchAuth('/api/...')`
- `fetchJsonAuth('/api/...')`
- `fetch(window.archiveAwareUrl('/api/...'), options)`

`archiveAwareUrl`을 거치지 않는 직접 `fetch('/api/...')`는 자동 변환되지 않는다. 이런 호출은
원래 `/api/**`로 나가므로, 원래 route를 차단하고 archive에서만 기능을 보존할 때 반드시 위 helper
중 하나로 교체해야 한다. absolute URL, non-API URL, 이미 `/archived/**`인 URL은 변환하지 않는다.

## 금지 사항

- `Archived*Service`, `Archived*Controller`, `archived_*.html` 식으로 기존 구현을 통째 복사하지 않는다.
- 보존 라우트에서 신규 기능을 개발하지 않는다.
- public 사용자에게 `/archived/**`를 메뉴/버튼으로 노출하지 않는다.

## 재활성/제거 절차

- 재활성: 기존 route의 feature gate/menu 숨김을 되돌리고 `/archived` alias는 유지 또는 비활성한다.
- 완전 제거: 기능 코드 제거 PR에서 `/archived` alias 의존 여부와 데이터 보존 정책을 별도 확인한다.
