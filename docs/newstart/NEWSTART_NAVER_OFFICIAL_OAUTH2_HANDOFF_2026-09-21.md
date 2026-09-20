# NewStart 네이버 로그인 — 공식 Spring Security OAuth2 Client 표준 흐름 HANDOFF (2026-09-21)

- 브랜치: `feat/newstart-naver-oauth2-client-20260920` (base: origin/main `ffc228a`, PR #339 병합 직후)
- 상태: **구현 완료 · "가장 중요한 검증"(302 → nid.naver.com) PASS · 로컬 빌드/테스트 통과 · UI 미연결(지시대로). 병합/배포 안 함.**
- 이번 작업은 기존 V1(`/oauth/naver/*`)·V2(`/auth/naver/v2/*`) 손수 작성한 OAuth 흐름과
  **완전히 독립**이다 — 셋을 섞지 않았다. 둘 다 그대로 유지된다.

## 1. 근거 자료 확인

- **PRIMARY**: 네이버 공식 문서는 이 세션 도구로 `developers.naver.com` 직접 fetch가 차단되어
  대신 참고 구현(REFERENCE)으로 검증했다.
- **REFERENCE**: `gunkim/springboot-oauth2-sample`의
  `src/main/resources/application-oauth.yml`을 GitHub API로 직접 읽어 실제 `provider.naver` 설정값을
  확인했다:
  ```yaml
  provider:
    naver:
      authorization-uri: https://nid.naver.com/oauth2.0/authorize
      token-uri: https://nid.naver.com/oauth2.0/token
      user-info-uri: https://openapi.naver.com/v1/nid/me
      user-name-attribute: response
  ```
  같은 저장소의 `NaverOAuthAttributesAdapter.java`(`Map<String,Object> response = attributes.get("response")`)로
  네이버 프로필 응답이 `{resultcode, message, response:{id, email, name, ...}}` 형태로 실제 필드가
  **"response" 아래에 한 번 더 감싸져 있음**을 코드로 재확인했다.
  - **중요 발견**: V1/V2(이 저장소, 이전 라운드들)는 프로필 파싱에 `"result"` 키를 썼다 —
    공식/참고 구현과 다른 값이다. V1/V2로는 실제 성공 토큰까지 도달한 적이 없어 이 차이가
    실제로 발현됐는지는 미확인이지만, 이번 공식 흐름 구현에는 검증된 `"response"`를 그대로 썼다.
    (V1/V2 코드는 이번 작업 범위 밖이라 손대지 않았다 — 참고용 기록만 남긴다.)
- **두 번째 참고 저장소**(jooyeokkim/Naver-Login-with-SpringBoot)는 위 확인만으로 충분해 별도 조회하지
  않았다(지시대로 "두 예제를 조합하지 않는다" — REFERENCE 하나만 직접 인용했다).

## 2. SPRING BOOT / SECURITY 버전, 호환성

```
SPRING BOOT VERSION: 4.0.6
SPRING SECURITY VERSION: 7.0.5 (resolved via ./gradlew dependencies)
```

`spring-boot-starter-oauth2-client`는 기존에 없어 신규 추가했다(`build.gradle`). Spring Security 7의
OAuth2 Client 개념(`ClientRegistrationRepository`, `OAuth2UserService`, `oauth2Login()`)은 6.x와
동일한 API 표면을 쓴다 — 호환성 문제 없음, 코드 컴파일/테스트로 확인.

## 3. 로그인 경로 — 공식 흐름 하나만

```
GET /oauth2/authorization/naver   → 이 저장소 어디에도 이 경로를 처리하는 @RequestMapping이 없다.
                                     Spring Security의 OAuth2AuthorizationRequestRedirectFilter가
                                     처리한다(공식 프레임워크 동작, 손수 controller 작성 안 함).
GET /login/oauth2/code/naver      → 동일하게 Spring Security의 OAuth2LoginAuthenticationFilter가
                                     처리한다. token 교환/state 검증도 프레임워크가 담당.
```

state/PKCE/authorization code 저장은 Spring Boot 기본 `HttpSessionOAuth2AuthorizationRequestRepository`
그대로 쓴다(커스텀 저장소를 새로 만들지 않았다 — 지시사항 "공식 흐름 하나만 사용" 준수). 이 앱은
`SessionCreationPolicy.STATELESS`(JWT 쿠키 기반)이지만, 이 정책은 Spring Security 자신의
SecurityContext 영속화만 막을 뿐 원시 서블릿 세션 생성 자체를 막지 않는다 — OAuth2 로그인 핸드셰이크
동안만 짧게 세션이 생겼다가(주로 임시 `JSESSIONID`) 콜백 이후로는 이 앱의 어떤 코드도 세션을 참조하지
않으므로 무해하다(로컬 테스트로 302/state 세션 저장까지 정상 확인됨).

## 4. Naver Provider — 공식 endpoint 3종만

`config/security/oauth2/NaverClientRegistrationConfig.java`에 고정:

```
authorization: https://nid.naver.com/oauth2.0/authorize
token:         https://nid.naver.com/oauth2.0/token
user info:     https://openapi.naver.com/v1/nid/me
```

다른 후보 endpoint(예: `nid.naver.com/v2/oauth/token`, `auth.naver.com/...`)는 전혀 넣지 않았다.

`application.yaml`의 `spring.security.oauth2.client.registration.*` 자동 바인딩은 **의도적으로 쓰지
않았다** — `client-id`가 비어 있으면 `ClientRegistration.build()`가 "clientId cannot be empty"로
즉시 예외를 던져 **로컬/CI처럼 `NAVER_CLIENT_ID` 미설정 환경에서 앱 부팅 자체가 실패**한다. 대신
`NaverClientRegistrationConfig`가 직접 bean을 만들고, 기존 `NaverOAuthProperties.configured()`와
동일한 관례로 미설정 시 등록 0건 repository로 대체해 부팅에 영향이 없게 했다(단위테스트로 두 경우
모두 확인).

## 5. Client ID/Secret

기존 운영 환경변수 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET`를 그대로 재사용한다(V1/V2와 동일 —
`NaverOAuthProperties`를 그대로 재사용, 새 키 발급 없음, 코드/문서/로그 어디에도 실제 값 없음).

## 6. NewStart 내부 구조와의 연결

`config/security/oauth2/NaverOAuth2UserService.java`(공식 `OAuth2UserService` 인터페이스 구현)가:

1. `DefaultOAuth2UserService`로 실제 프로필을 받아온다(Spring 표준 위임, 손수 HTTP 코드 없음).
2. "response" 래퍼를 풀어 `id`/`name`/`nickname`/`email`을 추출한다.
3. 기존 `UserRepository`/`SocialAccountRepository`/`SocialProvider`(V1/V2와 동일 테이블 재사용)로
   (provider=NAVER, providerUserId) 매핑을 찾거나, 없으면 내부 User를 자동 생성한다 — **회원가입
   화면 없음**. 이메일 동일이라는 이유만으로 다른 계정을 병합하지 않는다(V1/V2와 동일 정책).
   같은 테이블을 쓰므로 V1/V2로 이미 연결된 네이버 계정이면 이 경로도 같은 User를 재사용한다
   (중복 가입 없음 — 테스트로 고정).
4. ROLE: `Role.STUDENT`를 의미 부여 없는 기술적 placeholder로만 쓴다(V1/V2와 동일 이유 — users.role은
   마이그레이션 없이 확장 불가한 MySQL 네이티브 ENUM). **DB 마이그레이션 없음.**

`config/security/oauth2/NaverOAuth2LoginSuccessHandler.java`가 이 결과(f2rUserId/f2rLoginId/f2rRole)를
받아 **기존 `JwtTokenProvider.issue(...)` + `CookieUtil.writeAccessToken(...)`**만 호출하고
`/newstart`로 리다이렉트한다 — 새 인증 로직 없음, 기존 것 재사용만.

`config/security/oauth2/NaverOAuth2LoginFailureHandler.java`는 state 불일치/인가 거부/token·profile
실패를 전부 `?login=naver_failed`로 모은다(원인은 `NAVER_OAUTH2_*` 로그로만 구분, 값은 미노출).

## 7. SecurityConfig 배선 — 다른 서비스에 영향 없음

`filterChain(...)`에 4개 `ObjectProvider<T>` 파라미터(`ClientRegistrationRepository`,
`OAuth2UserService<...>`, 성공/실패 핸들러)를 추가하고, repository가 있을 때만
`.oauth2Login(...)`을 호출하도록 조건부로 배선했다 — 기존 `AcademyRepository`/
`SiteVisitRecordingService`에 이미 쓰인 것과 동일한 지연주입 패턴이다. 이유: `filterChain()`은
전체 앱의 유일한 `SecurityFilterChain` bean이라 수십 개의 기존 `@WebMvcTest`가 전부
`@Import(SecurityConfig.class)`로 이걸 가져다 쓰는데, 새 파라미터가 필수(non-ObjectProvider)였다면
그 테스트들이 새 bean을 모르는 컨텍스트라 전부 깨졌을 것이다. `ObjectProvider`로 감싸 "없으면
건너뛴다"로 만들어 기존 회귀를 전부 통과시켰다(§9).

## 8. 302 검증 — 가장 중요한 검증, UI 연결 전 확인 완료

```
GET /oauth2/authorization/naver
→ 302
→ Location: https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id=...
            &state=...&redirect_uri=http://localhost/login/oauth2/code/naver
            &code_challenge=...&code_challenge_method=S256
```
(`NaverOAuth2LoginStartTest`, MockMvc 실측 — 위 Location은 실제 응답 헤더를 그대로 옮긴 것,
state/code_challenge 값 자체는 매 실행 랜덤이라 이 문서에서도 값은 남기지 않는다.)

- `redirect_uri`가 실제로 `/login/oauth2/code/naver`로 풀리는지 확인(추측 아님).
- **참고(다음 실사용자 검증에서 관찰 필요)**: Spring Security 7이 `authorization_code` grant에
  PKCE(`code_challenge`/`code_challenge_method=S256`)를 기본으로 함께 보낸다. 네이버가 이 부가
  파라미터를 무시하는 표준 동작을 하는지는 실제 네이버 인가 화면까지 가봐야 확인 가능하다 — 코드로
  선제적으로 비활성화하지 않았다("네이버 공식 최신 Spring Boot 명세를 우선"이라는 지시에 따라
  프레임워크 기본 동작을 그대로 두고 실측으로 판단하기로 함).

**이 302 검증이 PASS하기 전에는 NewStart 로그인 버튼을 이 경로에 연결하지 않는다는 지시를
그대로 지켰다 — 이번 PR은 UI를 전혀 건드리지 않았다.**

## 9. 회귀 확인

기존 `@WebMvcTest`(`SecurityConfig.class` import) 샘플 + 전체 `build-check.yml` non-MySQL
allowlist를 이 브랜치에서 재실행 — 전부 통과(§ TESTS 참고). `filterChain()`에 새 필수 파라미터를
직접 추가하지 않고 `ObjectProvider`로 감싼 설계가 정확히 이 회귀를 막기 위한 것이었다.

## 10. 보존 확인

NewStart UI, NewStart AI, 회원 채팅, WebSocket, 관리자, Easy, Wiki, Chart, 카페 리서치 —
이번 PR은 `build.gradle`, `application.yaml`, `SecurityConfig.java`(추가 파라미터만),
`domain/newstart/controller/NewStartPageController.java`(테스트에서 import만, 무변경) 외에는
**전부 신규 파일**(`config/security/oauth2/*`)이다. 기존 UI 템플릿·JS 파일은 단 하나도 건드리지
않았다 — 다른 작업(NewStart UI 별도 작업 중)과 충돌 없음.
