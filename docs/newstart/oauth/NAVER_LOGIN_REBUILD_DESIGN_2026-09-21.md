# NewStart 네이버 간편로그인 재구축 — PHASE 1 조사·설계 문서

- 작성일: 2026-09-21
- 브랜치: `research/newstart-naver-login-rebuild-20260921`
- 베이스: `origin/main` @ `270af6d0a453d703381a5a91c9dbf256c7b235a8` (PR #342 merge 커밋 — clean baseline)
- 문서 성격: **조사·설계 ONLY.** 이 문서 자체를 제외하고 코드/설정/DB/UI 변경 없음.
- 이 PHASE에서 다시 가져오지 않은 과거 구현: V1(`/oauth/naver/*`), V2(`/auth/naver/v2/*`), PR #341(Spring Security OAuth2 Client 첫 시도). 세 구현 모두 cherry-pick, 부분 재활용 없이 전부 백지에서 재설계함.

---

## 1. 목적과 범위

NewStart 회원 채팅(`/newstart/chat`)의 "네이버 로그인 준비 중" 비활성 버튼을 실제로 동작하는 네이버 간편로그인으로 교체하기 위한 **재설계**를 수행한다. 지금까지 세 번의 구현 시도(V1, V2, PR #341)가 있었고 모두 제거되어 현재 `main`은 "네이버 로그인을 아직 구현하지 않은 깨끗한 상태"이다. 이번 PHASE 1은 그 위에서:

1. 네이버 공식 문서를 직접 조사하고,
2. 공개된 참고 구현을 조사하고,
3. Feel2Real 현재 인증 아키텍처를 실제 코드 기준으로 분석하고,
4. 과거 `no_stable_id` 실패의 근본 원인을 공식 문서로 확정하고,
5. 단일 구현 방식을 선정하고,
6. 4단계 점진 롤아웃과 관측성/보안/테스트/롤백 계획을 설계

하는 것만을 목적으로 한다. 코드 구현은 STEP 1부터 별도 세션/PHASE에서 진행한다.

---

## 2. CLEAN BASELINE 검증 결과

| 항목 | 결과 |
|---|---|
| `origin/main` HEAD | `270af6d0a453d703381a5a91c9dbf256c7b235a8` — 지시된 SHA와 **일치** |
| Naver 컨트롤러 매핑 (`src/main/java` 전체) | 없음 |
| `SecurityConfig.java` 내 `oauth2Login` / `ClientRegistrationRepository` | 없음 |
| `domain/user/social/` 패키지 구성 | `SocialAccount.java`, `SocialAccountRepository.java`, `SocialProvider.java` (공용 인프라) 3개만 존재. V1/V2 전용 클래스 없음 |
| `build.gradle`의 `spring-boot-starter-oauth2-client` | 없음 |
| NewStart 채팅 화면 로그인 버튼 | `<button class="ns-btn ns-btn-primary" type="button" disabled aria-disabled="true">네이버 로그인 준비 중</button>` — href/endpoint 없음, 비활성 상태 확인 |
| Spring Boot 버전 (`build.gradle`) | `4.0.6` |

**CLEAN BASELINE = PASS.**

---

## 3. 조사 방법론 및 환경 제약

- `WebFetch https://developers.naver.com/docs/login/devguide/devguide.md` → **차단됨** ("Claude Code is unable to fetch from developers.naver.com"). developers.naver.com 도메인 전체가 이 환경에서 직접 조회 불가.
- 대안: 네이버가 직접 관리하는 공식 GitHub 저장소 **`naver/naver-openapi-guide`**를 1차 출처로 사용. 이 저장소는 developers.naver.com 문서를 마크다운으로 그대로 미러링한다. `gh api repos/naver/naver-openapi-guide/contents/<path> -q '.content' | base64 -d` 방식으로 조회.
- 조회한 정확한 파일과 조회 일시:
  - `ko/login/api/api.md` (556줄, 네이버 로그인 API 전체 스펙 — 인가/토큰/갱신/삭제 요청 파라미터, 콜백 응답, 에러코드) — 2026-09-21 확인.
  - `ko/login/profile/profile.md` (481줄, 프로필 API 스펙 — 응답 JSON 구조, 필드별 설명) — 2026-09-21 확인.
- 출처 URL(참고용, 직접 fetch는 안 되었으나 GitHub 미러 내용과 대응하는 공식 문서 주소): `https://developers.naver.com/docs/login/api/api.md`, `https://developers.naver.com/docs/login/profile/profile.md`.
- 공개 GitHub 참고 구현 2건 조사 (§9).

---

## 4. 공식 네이버 로그인 API — Authorization

출처: `naver/naver-openapi-guide` `ko/login/api/api.md` (확인일 2026-09-21).

- **인가 요청 엔드포인트**: `GET https://nid.naver.com/oauth2.0/authorize`
- 필수 파라미터: `response_type=code`, `client_id`, `redirect_uri`, `state`(CSRF 방지용 임의 문자열, 애플리케이션이 생성)
- 선택 파라미터: `scope` 등
- **콜백**: 사용자가 동의하면 `redirect_uri`로 `code`, `state` 쿼리 파라미터와 함께 302 리다이렉트됨. `redirect_uri`는 반드시 네이버 개발자센터에 등록된 Callback URL과 **정확히 일치**해야 하며, 불일치 시 네이버 측에서 에러를 반환하고 콜백 자체가 발생하지 않는다.
- 실패 시 `redirect_uri?error=...&error_description=...&state=...` 형태로 콜백됨(사용자가 동의 거부 등).

## 5. 공식 네이버 로그인 API — Token Exchange

- **토큰 요청 엔드포인트**: `GET` 또는 `POST https://nid.naver.com/oauth2.0/token`
- 파라미터: `grant_type=authorization_code`, `client_id`, `client_secret`, `code`, `state`
- **응답**(JSON): `access_token`, `refresh_token`, `token_type`("bearer"), `expires_in`(초)
- 갱신: `grant_type=refresh_token` + `refresh_token`
- 폐기(revoke): `grant_type=delete`, `service_provider=NAVER`, `access_token`

> 참고: V1의 초기 실패 원인 중 하나가 토큰 엔드포인트 호스트를 잘못 지정한 것이었다(이전 세션 진단, 이번 PHASE 1에서 코드 재조사는 하지 않았으나 공식 문서상 `nid.naver.com/oauth2.0/token`이 유일한 정답임을 재확인함).

## 6. 공식 네이버 프로필 API — 요청/응답 구조

출처: `naver/naver-openapi-guide` `ko/login/profile/profile.md` (확인일 2026-09-21).

- **엔드포인트**: `GET https://openapi.naver.com/v1/nid/me`
- **헤더**: `Authorization: Bearer {access_token}`
- **응답 JSON 최상위 구조**:
  ```json
  {
    "resultcode": "00",
    "message": "success",
    "response": {
      "id": "...",
      "nickname": "...",
      "name": "...",
      "email": "...",
      "gender": "...",
      "age": "...",
      "birthday": "...",
      "profile_image": "...",
      "birthyear": "...",
      "mobile": "..."
    }
  }
  ```

**핵심 확인 사항: 필드는 `"response"` 키 아래에 중첩된다. `"result"` 키가 아니다.**

## 7. `response.id` 심층 분석 — 과거 `no_stable_id` 미스터리 확정 해결

공식 문서(`ko/login/profile/profile.md`)에서 명시적으로 확인한 내용:

- `response.id`는 **애플리케이션(서비스)별로 발급되는 회원 고유 식별 값**이며, 동일 네이버 회원이 동일 애플리케이션으로 로그인하면 항상 동일한 값을 반환한다.
- `response.id`는 **사용자의 실제 네이버 로그인 아이디가 아니다.** 문서는 개발자가 이 값을 이용해 "자체적으로 회원정보를 구성"할 것을 명시적으로 안내한다 — 즉 애플리케이션 DB의 provider user id로 사용하도록 설계된 값이다.
- 응답에는 `enc_id`라는 추가 필드가 존재할 수 있으나, 문서는 이를 **네이버 내부 전용**으로 명시하며 애플리케이션 개발자가 사용해서는 안 된다고 규정한다.

**결론**: V1/V2가 겪은 `no_stable_id` 계열 실패는 프로필 JSON을 `"result"` 키로 파싱해 필드가 항상 비어있었기 때문일 가능성이 매우 높다(V2가 실제로 `"result"` 키를 사용한 코드였음은 이전 세션 코드 리뷰에서 확인됨). 공식 문서 기준 **정답은 `"response"` 키 + `response.id` 필드를 `SocialAccount.providerUserId`로 사용하는 것**이다. `enc_id`는 어떤 경우에도 사용하지 않는다.

## 8. GitHub 공식/공개 참고 구현 조사

### 8.1 `naver/naver-openapi-guide` (네이버 공식)
문서 저장소 자체이며 실행 가능한 Spring 예제 코드는 포함하지 않음. §4~7의 근거로 사용.

### 8.2 `gunkim/springboot-oauth2-sample` — 채택 참고
- `pushed_at: 2025-03-25`, `updated_at: 2025-09-19` — 활성 유지보수 중, 최신에 가까움.
- `build.gradle` 확인: Spring Boot `3.2.2`, 의존성으로 `spring-boot-starter-oauth2-client`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-web`, Lombok, H2, `spring-security-test` 사용.
- 패턴: Spring Security 표준 `.oauth2Login(...)` + 커스텀 `OAuth2UserService<OAuth2UserRequest, OAuth2User>` 구현으로 프로바이더별 속성 언랩(네이버의 `"response"` 중첩 구조 처리)을 수행하는 정석적인 방식.
- Spring Boot 3.2.2는 Feel2Real의 4.0.6보다 한 세대 낮지만, `spring-boot-starter-oauth2-client`의 `ClientRegistration`/`OAuth2UserService` API 표면은 두 버전 사이에서 안정적이다 — 이는 이전 세션에서 PR #341이 Spring Boot 4.0.6/Spring Security 7.0.5 위에서 동일 패턴으로 실제 성공한 것으로 실증됨(§10 참고).

### 8.3 `jooyeokkim/Naver-Login-with-SpringBoot` — 참고 제외(구식)
- `pushed_at: 2021-08-09` — 5년 이상 정체. 사용자 지시("오래된 예제는 후순위")에 따라 심층 분석하지 않음. Spring Security 구버전 API(`WebSecurityConfigurerAdapter` 계열일 가능성이 높음, 현재 Spring Security 7과 API 자체가 다름) 기반으로 추정되며 참고 가치를 인정하지 않는다.

### 8.4 추가 조사 여부
공식 문서(§4~7) + 활성 유지보수 참고 구현(§8.2) + Feel2Real 자체의 실증된 과거 시도(§10)만으로 방식 선정에 필요한 근거가 충분하다고 판단하여, 추가 3~4번째 GitHub 참고 구현 조사는 진행하지 않았다.

## 9. 현재 Feel2Real 인증 아키텍처 (실제 코드 확인, `origin/main` 기준)

### 9.1 `SecurityConfig.java`
- 세션 정책: `STATELESS` (JWT 쿠키 기반, 서버 세션 없음)
- CSRF: `disable` (동일 출처 + `SameSite=Lax` 쿠키로 대체)
- `formLogin`/`httpBasic`/`logout`: 모두 `disable` — 로그인은 `/api/auth/login` API + JWT 쿠키로만 이루어짐, 공통 로그인 모달 UI
- 인가 규칙은 좁은 매처 → 넓은 매처 순서(first-match-wins)로 정교하게 계층화되어 있으며, catch-all은 `anyRequest().permitAll()` (API는 `/api/**`가 별도로 `authenticated()`로 닫혀 있음)
- 커스텀 필터 체인 순서: `SubdomainAcademyFilter` → `ArchivedRouteAliasFilter` → `JwtAuthenticationFilter` → `ChartAgentTokenFilter` → `ArchivedRouteAccessFilter` → `OriginWhitelistFilter` → (비활성) `UsernamePasswordAuthenticationFilter` → `SiteVisitFilter`
- `AcademyRepository`, `SiteVisitRecordingService` 등은 `ObjectProvider`로 지연 주입 — `@WebMvcTest` 슬라이스 호환을 위한 기존 컨벤션.

### 9.2 `JwtTokenProvider.java`
- HS256 단일 access token(별도 refresh token 없음)
- `issue(userId, loginId, role)` → subject=userId, claim `loginId`, claim `role`, `iat`/`exp`
- `parse(token)` → `AuthenticatedPrincipal(userId, loginId, role)` record 반환, 서명/만료 오류는 `JwtException`으로 전파 → 필터가 401 처리

### 9.3 `CookieUtil.java` / `JwtProperties.java`
- 쿠키명: `app.jwt.cookie-name` (환경설정값, 운영/로컬 동일)
- `HttpOnly=true`, `SameSite=Lax`, `Path=/`
- `Secure`: `app.jwt.secure-cookie` — 운영(prd)=true, 로컬=false
- `Domain`: `app.jwt.cookie-domain` — 운영(prd)만 `.feel2real.co.kr`(서브도메인 공유), 로컬/dev/stg는 미설정(host-only)
- `writeAccessToken`/`clearAccessToken` 모두 동일한 `Domain` 적용 로직 공유(좀비 쿠키 방지)

### 9.4 `SocialAccount` / `SocialProvider` (공용 인프라, 이미 존재)
- `SocialAccount`: `userId`, `provider`(`SocialProvider` enum), `providerUserId`(VARCHAR(64)) — provider identity로만 식별, 이메일 기반 자동 병합은 의도적으로 미지원
- `SocialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)` — 재로그인 시 중복 계정 생성 방지에 사용
- `SocialProvider` enum: 현재 `NAVER` 하나만 존재
- **결론**: `response.id` (§7)를 그대로 `SocialAccount.providerUserId`에 저장하면 기존 스키마/레포지토리 변경 없이 그대로 재사용 가능.

### 9.5 Flyway
- `V982 user_social_accounts` 마이그레이션이 이미 존재하며 PR #342 cleanup에서도 롤백되지 않았다 — 재사용 가능, 신규 마이그레이션 불필요(스키마 변경 없음).

## 10. 등록된 Naver Developers Console Callback URL — 확인 상태

**추측하지 않는다.** 이번 세션에서 Naver Developers Console에 직접 접근하지 않았으므로 "현재 등록된 전체 Callback URL 목록"을 코드/설정만으로 100% 확정할 수 없다.

다만 다음은 **과거 세션의 대화 기록 자체(추측이 아닌 사용자 발화 및 read-only SSM 진단 로그로 확인된 사실)**로 뒷받침된다:

- 이전 세션에서 사용자가 명시적으로 진술: "네이버 개발자센터에 `https://feel2real.co.kr/login/oauth2/code/naver`를 추가한다. 기존 callback 2개는 아직 삭제하지 않는다." → 이 시점 기준 최소 3개 Callback URL이 등록되어 있었음(V1용 `/oauth/naver/callback`, V2용 `/auth/naver/v2/callback`, 그리고 신규 `/login/oauth2/code/naver`).
- 세 경로 모두 실제 운영 환경에서 네이버로부터 정상적으로 콜백을 수신한 이력이 있음(V1: 콜백 도달, V2: `NAVER_V2_CALLBACK_RECEIVED` 다수 관측, PR #341: `NAVER_OAUTH2_LOGIN_SUCCESS`까지 도달) — Callback URL이 등록과 불일치하면 네이버가 리다이렉트 자체를 거부하므로, 이 성공 이력은 각 URL이 **그 시점에는** 등록되어 있었음을 강하게 뒷받침한다.
- 이후 어느 것도 콘솔에서 제거되었다는 증거는 없으나, **제거되지 않았다는 것 역시 콘솔을 직접 확인하지 않는 한 확정할 수 없다.**

**보고**:
- `https://feel2real.co.kr/login/oauth2/code/naver` — **등록 근거 있음** (사용자 본인의 명시적 발화 + 실제 성공 콜백 이력). 이번 재구축에서 Option A(§12)를 채택할 경우 Spring Security의 기본 리다이렉트 URI 규칙(`{baseUrl}/login/oauth2/code/{registrationId}`)이 정확히 이 경로와 일치하므로, **콘솔에 새 URL을 추가할 필요가 없다.**
- V1/V2용 나머지 두 URL — **UNKNOWN(현재도 등록되어 있는지) — MANUAL CONFIRMATION REQUIRED.** 이번 재구축에서는 사용하지 않으므로 재구축 자체에는 영향 없음. 정리 차원에서 필요하다면 사용자가 직접 콘솔에서 확인 후 정리할 것을 권고.
- **STEP 1 착수 전 사용자 확인 필요 항목**: 실제 Client ID/Client Secret이 현재도 유효한지, `https://feel2real.co.kr/login/oauth2/code/naver`가 현재도 등록되어 있는지 콘솔에서 직접 재확인. 이번 PHASE 1에서는 새 Client ID/Secret 생성이나 콘솔 설정 변경을 하지 않았고, 하지도 않을 것이다.

## 11. 비교표

| 구분 | 네이버 공식 방식 | GitHub 참고(`gunkim/springboot-oauth2-sample`) | 현재 Feel2Real 구조 | 최종 선택 |
|---|---|---|---|---|
| Authorization 요청 | `GET nid.naver.com/oauth2.0/authorize` + `state` | Spring Security `.oauth2Login()`이 자동 생성(`/oauth2/authorization/naver`) | 없음(신규) | Spring Security 자동 생성 사용 |
| Callback 처리 | `redirect_uri?code&state` | `/login/oauth2/code/{registrationId}` 표준 경로, 프레임워크가 자동 라우팅 | 없음(신규) | 표준 경로 그대로 사용(§10 근거로 등록 URL과 일치) |
| State(CSRF) 관리 | 애플리케이션이 생성/검증 | Spring Security가 자동 생성/검증(`AuthorizationRequestRepository`) | 없음(신규) | 프레임워크 위임(직접 구현 안 함) |
| Token Exchange | `GET/POST nid.naver.com/oauth2.0/token` | `DefaultAuthorizationCodeTokenResponseClient`가 자동 처리 | 없음(신규) | 프레임워크 위임 |
| Profile 요청 | `GET openapi.naver.com/v1/nid/me`, Bearer 헤더 | 커스텀 `OAuth2UserService`가 `user-info-uri`로 자동 호출 | 없음(신규) | 프레임워크 호출 + 커스텀 언랩 |
| Profile JSON 언랩 | `response.*` 중첩 | 커스텀 `OAuth2UserService`에서 `"response"` 맵 추출 | 없음(신규) | 커스텀 `OAuth2UserService`에서 `"response"` 추출(§7 확정 근거) |
| Provider User ID | `response.id` (앱별 고유, 실제 ID 아님) | `response.id`를 principal attribute로 매핑 | `SocialAccount.providerUserId` (VARCHAR64, 이미 존재) | `response.id` → `providerUserId` 그대로 저장 |
| User/SocialAccount 연동 | 문서 범위 밖(자체 구현 안내) | 예제 앱 자체 User 엔티티 | `SocialAccountRepository.findByProviderAndProviderUserId` 존재 | 기존 레포지토리 재사용, 신규 스키마 없음 |
| JWT 발급 | 문서 범위 밖 | 예제 앱 자체 처리 | `JwtTokenProvider.issue(userId, loginId, role)` 기존 존재 | STEP 3에서 기존 그대로 재사용 |
| Cookie 발급 | 문서 범위 밖 | 예제 앱 자체 처리 | `CookieUtil.writeAccessToken` 기존 존재(HttpOnly/SameSite=Lax/Secure/Domain 정책 포함) | STEP 3에서 기존 그대로 재사용 |
| 성공 후 리다이렉트 | 문서 범위 밖 | `AuthenticationSuccessHandler` 커스텀 | 없음(신규) | 커스텀 `AuthenticationSuccessHandler`(STEP 3/4에서 설계) |
| 실패 처리 | `error`/`error_description` 쿼리 | `AuthenticationFailureHandler` 커스텀 | 없음(신규) | 커스텀 `AuthenticationFailureHandler`, 사용자 대상 메시지는 비식별 |

## 12. 구현 방식 선정

### 선정: **Option A — Spring Security 공식 OAuth2 Client**(`spring-boot-starter-oauth2-client` + `.oauth2Login(...)`)

**선정 근거**:
1. **실증됨.** V1(커스텀 REST, 토큰 엔드포인트 오류로 실패), V2(커스텀 REST, `"result"` 키 오류로 실패)와 달리, PR #341(Option A와 동일 방식)은 이전 세션에서 PC 실기기 테스트로 `NAVER_OAUTH2_LOGIN_SUCCESS`까지 완주하고 실제 User/SocialAccount 레코드 생성까지 확인된 유일한 시도였다. 모바일 실패는 방식 자체의 결함이 아니라 NewStart 버튼이 구버전(V2) endpoint를 가리키던 **UI 배선 버그**로 진단되었다 — 공식 플로우 자체가 모바일에서 실패했다는 근거는 없다.
2. **저수준 OAuth 로직을 직접 구현하지 않는다.** State 생성/검증, PKCE, 토큰 교환 HTTP 호출, 콜백 라우팅을 모두 Spring Security가 담당 — V1/V2가 겪은 것과 같은 종류의 수작업 버그(잘못된 호스트, 잘못된 JSON 키) 발생 여지를 원천적으로 줄인다.
3. Feel2Real 현재 스택(Spring Boot 4.0.6 / Spring Security 7.0.5)과의 호환성이 이미 실증됨(1번 근거와 동일).
4. 활성 유지보수 중인 공개 참고 구현(§8.2)이 존재해 패턴 검증이 가능하다.
5. 기존 `SocialAccount`/`JwtTokenProvider`/`CookieUtil` 인프라와 통합 지점이 명확하다 — `OAuth2UserService`에서 `response.id` 추출 → `SocialAccountRepository` 조회/생성 → `JwtTokenProvider.issue` → `CookieUtil.writeAccessToken` → 커스텀 `AuthenticationSuccessHandler`에서 리다이렉트, 로 STEP 3까지 자연스럽게 이어진다.
6. 콘솔에 이미 등록된 것으로 확인되는 `/login/oauth2/code/naver`(§10)를 그대로 재사용할 수 있어 콘솔 설정 변경이 불필요하다.

### 기각: **Option B — 커스텀 REST 기반 OAuth 구현**

**기각 근거**:
1. 이 코드베이스에서 독립적으로 두 번(V1, V2) 시도되었고 **두 번 다 실제 버그로 실패**했다 — 이는 이론적 우려가 아니라 이 프로젝트의 실제 개발 이력이 보여주는 재현된 실패 패턴이다.
2. State 생성/서명, 쿠키 관리, HTTP 클라이언트 호출, JSON 파싱을 전부 직접 구현해야 하며 각 지점이 잠재적 버그 지점이다.
3. 이 프로젝트 전체 시도 중 완전한 성공(`response.id` 획득까지 도달)을 실기기로 증명한 유일한 사례가 Option A(PR #341)이므로, 실증 데이터가 Option A를 명확히 지지한다.

**두 방식을 혼합하지 않는다** — 이번 재구축은 Option A 단일 방식으로만 진행한다.

## 13. 4단계 점진 롤아웃 설계

### STEP 1 — OAuth 라운드트립만 (User/SocialAccount/JWT/Cookie 생성 금지)
- 목표: `/oauth2/authorization/naver` → 네이버 로그인/동의 → `/login/oauth2/code/naver` 콜백 → 토큰 교환 → 프로필 조회 → `response.id` 획득까지만 구현.
- **금지**: 이 단계에서 `User` 엔티티 생성, `SocialAccount` 레코드 생성, JWT 발급, 쿠키 발급을 절대 하지 않는다. 성공 시 단순 로그/디버그 응답만 남긴다(예: 임시 확인용 200 응답에 `providerIdPresent=true`만 표시, 실제 값 노출 금지).
- **완료 기준**: 실제 PC 브라우저 1회 + 실제 모바일 브라우저 1회로 로그인 동의 → 콜백 성공 → `NAVER_PROFILE_SUCCESS` 로그 확인. **MockMvc 단위테스트만으로는 완료로 간주하지 않는다.**

### STEP 2 — User/SocialAccount 연동
- STEP 1에서 얻은 `response.id`를 `SocialAccountRepository.findByProviderAndProviderUserId(NAVER, id)`로 조회.
- 없으면 신규 `User` + `SocialAccount(userId, NAVER, id)` 생성. 있으면 기존 `userId` 재사용(중복 계정 생성 방지).
- 이메일 기반 자동 병합은 하지 않는다(기존 `SocialAccount` 클래스 주석에 명시된 기존 정책 그대로 유지).
- 완료 기준: 동일 네이버 계정으로 2회 로그인해도 `user_social_accounts`에 중복 행이 생기지 않음을 실제 DB 조회로 확인.

### STEP 3 — 기존 JWT/Cookie/인증에 연결(신규 인증 체계 생성 금지)
- 커스텀 `AuthenticationSuccessHandler`에서 STEP 2로 확보한 `userId`로 `JwtTokenProvider.issue(userId, loginId, role)` 호출 → `CookieUtil.writeAccessToken(response, token)`으로 기존과 동일한 쿠키 발급.
- 새로운 토큰 형식, 새로운 쿠키, 새로운 인증 필터를 만들지 않는다 — `JwtAuthenticationFilter`가 그대로 이 쿠키를 읽어 인증 처리하도록 한다.
- 완료 기준: 로그인 후 `/api/**` 보호 엔드포인트가 정상 인증됨을 확인(예: 기존 `/api/auth`류 엔드포인트로 principal 확인).

### STEP 4 — NewStart UI 연결
- NewStart 채팅 화면의 "네이버 로그인 준비 중" 비활성 버튼을 `href="/oauth2/authorization/naver"`(또는 동등한 클릭 핸들러)로 교체, `disabled`/`aria-disabled` 제거.
- 완료 기준: 실제 PC + 실제 모바일에서 버튼 클릭 → 네이버 로그인 → NewStart 채팅으로 복귀 → 로그인 상태 UI 반영까지 실기기로 확인.

각 STEP은 이전 STEP이 실기기로 검증 완료된 뒤에만 착수한다.

## 14. 관측성(로그 마커) 설계

**원칙**: 토큰(access/refresh), authorization code, client secret, `response.id` 실제 값, 이메일 등 개인정보는 어떤 로그에도 절대 출력하지 않는다. 존재 여부를 나타내는 boolean만 사용한다.

성공 경로:
- `NAVER_AUTH_START` — `/oauth2/authorization/naver` 요청 진입
- `NAVER_CALLBACK_RECEIVED` — `/login/oauth2/code/naver`에 `code` 파라미터 수신(값은 로그하지 않음)
- `NAVER_STATE_VALID` — Spring Security가 state 검증 통과(프레임워크 내부 처리이므로 커스텀 핸들러 진입 시점에 기록)
- `NAVER_TOKEN_SUCCESS` — 토큰 교환 성공(access_token 값 로그 금지, 존재 여부만)
- `NAVER_PROFILE_SUCCESS providerIdPresent=true` — 프로필 조회 성공, `response.id` 존재 여부만 기록
- `NAVER_OAUTH_SUCCESS` — STEP 2/3 완료, User/SocialAccount 연결 및 쿠키 발급 완료(STEP 1에서는 이 마커 없음)

실패 경로(대응하는 실패 변형):
- `NAVER_CALLBACK_ERROR reason=<naver_error_code>` — 네이버가 `error` 쿼리로 콜백한 경우(`error_description` 등 세부 텍스트는 로그하지 않고 네이버가 준 에러 코드만)
- `NAVER_TOKEN_FAILURE` — 토큰 교환 실패(HTTP 상태코드만 기록, 응답 바디 금지 — client_secret이 요청에 포함되므로 요청/응답 전체 로그 금지)
- `NAVER_PROFILE_FAILURE` — 프로필 조회 실패(HTTP 상태코드만)
- `NAVER_OAUTH_FAILURE reason=<internal_code>` — STEP 2/3 처리 중 내부 오류(예: DB 저장 실패) — 스택트레이스는 서버 로그에는 허용되나 사용자 응답에는 노출하지 않음

## 15. 보안 설계

- **Client Secret**: 환경변수로만 주입(기존 `JWT_SECRET` 패턴과 동일하게 `NAVER_CLIENT_SECRET` 등), `application.yaml`에는 플레이스홀더만. 코드/문서/로그/테스트 출력/PR 본문 어디에도 실제 값 기록 금지 — 이 문서에도 기록하지 않았음.
- **State/CSRF**: Spring Security의 `.oauth2Login()`이 기본 제공하는 `state` 생성·검증에 위임한다. 직접 구현하지 않는다.
- **PKCE**: Spring Security 7의 기본 `OAuth2AuthorizationRequestResolver`는 `code_challenge`/`code_challenge_method=S256`을 자동 첨부한다. 네이버 공식 문서는 PKCE를 명시하지 않지만, 이전 세션의 실기기 테스트(PR #341)에서 이 파라미터가 포함된 상태로 전체 플로우가 성공했으므로 네이버 서버가 이를 무시/허용하는 것으로 실증되었다. 별도 비활성화 조치를 하지 않는다.
- **Redirect URI 고정**: `application.yaml`의 `redirect-uri`는 등록된 값과 정확히 일치하는 고정 문자열만 사용, 요청 파라미터로 동적 override 하지 않는다.
- **로그**: §14 원칙 그대로 — 토큰/코드/시크릿/실제 provider id/개인정보 미기록.
- **쿠키**: 기존 `CookieUtil` 정책(HttpOnly, SameSite=Lax, 운영 Secure=true, 운영 Domain=`.feel2real.co.kr`) 그대로 재사용, 신규 쿠키 정책 도입 없음.

## 16. 테스트 계획

- **단위/슬라이스 테스트(MockMvc)**: `OAuth2UserService`의 `"response"` 언랩 로직, `SocialAccountRepository` 연동 로직(신규/기존 계정 분기), `JwtTokenProvider`/`CookieUtil` 연동을 목(mock) 프로필 데이터로 검증. **이 테스트들의 통과만으로는 STEP 1 완료로 인정하지 않는다** — 실제 네이버 서버와의 라운드트립(도메인 소유권 검증, 실제 콘솔 등록 상태, 실제 동의 화면 UX)은 MockMvc로 재현 불가능하기 때문이다.
- **실기기 검증(필수, STEP별)**:
  - STEP 1: 실제 PC 브라우저 + 실제 모바일 브라우저 각 1회 이상, 콘솔 로그에서 `NAVER_PROFILE_SUCCESS`까지 확인.
  - STEP 2: 동일 계정 2회 로그인 후 DB에서 `user_social_accounts` 행 수 확인(중복 없음).
  - STEP 3: 로그인 후 보호된 API 호출로 인증 통과 확인.
  - STEP 4: NewStart 화면에서 버튼 클릭부터 로그인 상태 UI 반영까지 PC+모바일 각 1회.
- **회귀 테스트**: 기존 `NewStartAuthUiContractTest`, `NewStartChatPageContractTest` 등 PR #342에서 "비활성 버튼" 계약으로 재작성된 테스트들은 STEP 4에서 "활성 버튼 + 실제 endpoint" 계약으로 다시 업데이트해야 한다(이번 PHASE 1에서는 수정하지 않음, 설계만).

## 17. 롤백 계획

- 각 STEP은 독립 커밋/PR 단위로 진행하며, 문제 발생 시 해당 STEP의 PR만 revert하면 이전 STEP까지의 상태로 복귀 가능하도록 설계한다(STEP 1 PR을 revert하면 다시 클린 베이스라인).
- DB 마이그레이션 신규 추가 없음(§9.5) — 롤백 시 DB 되돌림 이슈 없음.
- STEP 4(UI 연결) 배포 후 문제 발생 시, 버튼을 다시 `disabled`로 되돌리는 것이 가장 빠른 완화책이며 별도 배포 없이 프론트엔드 파일만 되돌리면 된다.
- 콘솔의 Client ID/Secret/Callback URL은 이번 재구축 전 과정에서 변경하지 않으므로 롤백 시 콘솔 조작이 필요 없다.

## 18. 신규로 필요한 설정 항목(참고, 구현 아님)

STEP 1 구현 시 `application.yaml`에 추가가 필요할 것으로 예상되는 항목(실제 값은 기입하지 않음, 이번 문서에서 코드/설정 변경 없음):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          naver:
            client-id: ${NAVER_CLIENT_ID}
            client-secret: ${NAVER_CLIENT_SECRET}
            redirect-uri: "https://feel2real.co.kr/login/oauth2/code/naver"
            authorization-grant-type: authorization_code
            client-name: Naver
        provider:
          naver:
            authorization-uri: https://nid.naver.com/oauth2.0/authorize
            token-uri: https://nid.naver.com/oauth2.0/token
            user-info-uri: https://openapi.naver.com/v1/nid/me
            user-name-attribute: response
```

`user-name-attribute: response`가 §7에서 확정한 "response 키 아래 중첩" 구조를 프레임워크에 알려주는 지점이다. 로컬/dev 환경은 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET` 미설정 시 이 등록 자체가 비활성화되도록 조건부 구성이 필요하다(기존 `ChartAgentTokenFilter`가 토큰 미설정 시 비활성화되는 패턴과 동일한 방식 검토).

## 19. 영향 범위 확인

- 변경 대상: 이 설계 문서(`docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md`) 1개 파일뿐.
- EASY, 부실차주 Wiki, 카페 DB/RAG, AI, 내신 Wiki, member chat, WebSocket: 무변경.
- DB: 무변경(신규 마이그레이션 없음, 기존 데이터 무변경).
- NewStart UI: 무변경(여전히 비활성 버튼).
- Naver Developers 콘솔: 무변경(신규 Client ID/Secret 생성 없음, Callback URL 변경 없음).

## 20. STEP 1 착수 전 사용자 확인 필요 사항 (BLOCKER 아님, 확인 권장)

1. Naver Developers Console에서 `https://feel2real.co.kr/login/oauth2/code/naver`가 현재도 등록되어 있는지 직접 확인.
2. 현재 Client ID/Client Secret이 여전히 유효한지 확인(콘솔에서 재발급 이력이 없는지).
3. STEP 1 구현을 진행할 세션/브랜치를 새로 열지, 이번 `research/newstart-naver-login-rebuild-20260921` 브랜치를 이어서 쓸지 결정.

## 21. 요약 — 선정 결과

| 항목 | 값 |
|---|---|
| 선정 방식 | Option A: Spring Security OAuth2 Client |
| 기각 방식 | Option B: 커스텀 REST OAuth (V1/V2와 동일 계열, 실패 이력 있음) |
| Provider User ID | `response.id` (공식 문서 확정, `enc_id` 사용 금지) |
| Callback 경로 | `/login/oauth2/code/naver` (등록 근거 있음, §10) |
| 신규 DB 마이그레이션 | 없음(`V982 user_social_accounts` 재사용) |
| 신규 인증 체계 | 없음(기존 `JwtTokenProvider`/`CookieUtil` 재사용) |
| 과거 코드 재사용 | V1: 없음 / V2: 없음 / PR #341: 없음(설계 패턴만 재확인, 코드 재사용 아님) |

## 22. 다음 세션에서 할 일 (구현 시작점)

STEP 1 구현은 별도 세션/PHASE에서 다음 순서로 진행한다:
1. `application.yaml`에 §18 형태의 `spring.security.oauth2.client.registration.naver` 조건부 구성 추가(로컬은 비활성).
2. `NaverOAuth2UserService`(신규, `DefaultOAuth2UserService` 확장) — `"response"` 언랩만 수행, User/SocialAccount 생성 안 함.
3. 임시 진단용 성공 핸들러(§13 STEP 1) — `NAVER_PROFILE_SUCCESS providerIdPresent=true`만 로그.
4. 실기기 PC+모바일 라운드트립 검증.
5. 검증 완료 후 STEP 2로 진행.

## 23. 최종 상태

- CODE IMPLEMENTATION: NO
- FILES CHANGED: 이 문서 1개(`docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md`)
- OLD V1/V2/#341 CODE REUSED: NONE
- DB / NewStart UI / EASY / 부실차주 Wiki: UNCHANGED
- MERGE: NO
- DEPLOY: NO

**STEP 1 구현 준비 완료**
