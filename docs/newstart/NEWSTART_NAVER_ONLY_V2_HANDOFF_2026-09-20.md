# NewStart 네이버 전용 로그인 V2 — HANDOFF (2026-09-20)

- 브랜치: `fix/newstart-naver-only-v2-20260920` (base: origin/main `0796302`)
- 상태: **V2 구현 완료 · 로컬 빌드/테스트 통과 · 기존 V1은 그대로 유지(병행 운영). 병합/배포 안 함.**
- 배경: PR #338(관측성 보강) 배포 후 실사용자 시크릿 모드 재현에서도 V1 콜백이 `NAVER_CALLBACK_RECEIVED`
  조차 남기지 못하는 근본 원인 미확정 상태가 지속됨 — 5~6시간 추적 후 원인 규명을 중단하고, 방향을
  "V1 수리"에서 "NewStart 전용 최소 V2 신규 구현 + 로그인 수단을 네이버 하나로 단순화"로 전환했다.

## 1. A vs B 결정 — Spring Security OAuth2 Client vs 최소 수동 구현

`build.gradle` 확인 결과 `spring-boot-starter-security`만 있고 `spring-boot-starter-oauth2-client`는
**없다**. OAuth2 Client 방식(A)을 쓰려면:
- 신규 의존성 추가.
- 네이버는 Spring의 `CommonOAuth2Provider`에 없는 비표준 provider라 `ClientRegistration` 수동 등록
  + 커스텀 `OAuth2UserService`(profile 응답 매핑) 필요.
- 이 앱은 stateless(JWT 쿠키) 구조인데 `oauth2Login()`의 기본 authorization-request 저장소는
  세션 기반이라, 세션을 켜거나 커스텀 `AuthorizationRequestRepository`를 새로 만들어야 함.
- Spring이 관리하는 `OAuth2AuthenticationToken`/SecurityContext를 성공 핸들러에서 가로채 기존
  JWT/쿠키 모델로 다시 변환해야 하므로, 두 인증 모델이 한 요청 생명주기 안에 겹친다.
- 무엇보다 `oauth2Login()`은 공유 `SecurityFilterChain` 레벨 설정이라 BuildIt/Wiki 등 다른 서비스의
  인증 경로에 영향을 줄 위험이 생긴다.

최소 수동 구현(B)은 신규 의존성 0개, `SecurityConfig` 변경 0줄(기존 `anyRequest().permitAll()`
catch-all을 그대로 씀 — V1의 `/oauth/naver/**`가 이미 이 방식으로 production에서 동작 확인됨),
새 클래스 4개로 완결된다. **B를 선택했다** — 변경 범위가 압도적으로 작고, 다른 서비스 인증에 손을
대지 않는다는 요구사항과 가장 잘 맞는다.

## 2. STEP 0~1 — 격리

다른 세션들이 동시에 쓰는 공유 worktree(`C:/Users/sxxra/feel2real-buildit`, 계속 다른 브랜치로
체크아웃되는 중)를 건드리지 않기 위해 별도 worktree(`C:/Users/sxxra/feel2real-wt-naver-v2`)를
새로 만들어 작업했다. 기존 `NaverOAuthController`(V1)·`naver_login.js`·`fragments/_account_menu.html`
어느 것도 삭제·수정하지 않았다 — V1은 지금 이대로 존재한다(경로/쿠키/테스트/CI 게이트 전부 그대로).

## 3. NEW V2 — 파일 구성

전부 신규 파일, V1과 클래스·경로·쿠키 이름이 하나도 겹치지 않는다.

| 파일 | 역할 |
|---|---|
| `domain/user/social/v2/NaverV2Controller.java` | `GET /auth/naver/v2/start`, `GET /auth/naver/v2/callback` |
| `domain/user/social/v2/NaverV2StateService.java` | state 발급/검증. 쿠키 `NAVER_V2_OAUTH_STATE`(V1의 `NAVER_OAUTH_STATE`와 별개). 기본 returnUrl = `/newstart/chat` |
| `domain/user/social/v2/NaverV2Client.java` | token/profile 호출. `TOKEN_URL=https://nid.naver.com/oauth2.0/token`(V1이 겪은 auth.naver.com 오호스트 반복 안 함) |
| `domain/user/social/v2/NaverV2LoginService.java` | User 조회/자동가입 + SocialAccount 연결 |
| `templates/fragments/_newstart_auth.html` | NewStart 전용 인증 UI(신규 fragment, `_account_menu.html`은 무변경) |

**재사용(내부 회원/인증 기반, 그대로 씀)**: `User`, `UserRepository`, `SocialAccount`,
`SocialAccountRepository`, `SocialProvider`, `LoginResponse`, `JwtTokenProvider`, `CookieUtil`,
그리고 client_id/secret 설정 바인딩만 `NaverOAuthProperties`(단순 env var 매핑이라 "연결부"로
보지 않음 — 실제 outbound 호출/상태관리/콜백 로직은 전부 새로 작성).

**독립(새로 작성, V1과 무관)**: state 서명/검증, token/profile 호출, callback 분기 로직, 로그 마커.

`SocialAccountRepository`를 그대로 쓰기 때문에, 만약 어떤 네이버 실사용자가 V1으로 이미 연결된
적이 있다면 V2도 (provider=NAVER, providerUserId) 매칭으로 **같은 User를 찾아 재사용**한다 —
중복 가입 없음(`NaverV2LoginServiceTest#reusesAccountAlreadyLinkedByV1`로 고정).

## 4. ROLE 전략 (STEP 6 — 임의 부여 아님, 명시적 결정)

`users.role`은 MySQL 네이티브 `ENUM('STUDENT','ADMIN','TUTOR') NOT NULL`이다(V1 스키마 주석:
"STUDENT / ADMIN 사용, TUTOR는 예약값"). 마이그레이션 없이는 제4의 값을 저장할 방법이 없다.
불필요한 migration을 피하기 위해(지시사항 준수) V2도 V1과 동일하게 `Role.STUDENT`를 **의미 부여가
아닌 기술적 placeholder**로만 쓴다 — 화면/문구 어디에도 "학생"을 노출하지 않는다(계정 메뉴에서
`[data-account-home]`을 아예 렌더링하지 않아 `account_menu.js`의 `roleHome()`이 "학생 홈"/
`/stdt_dashboard`로 잘못 표시하는 것도 막았다). 전용 role이 정말 필요해지면 이후 별도 PR로
`ALTER TABLE users MODIFY COLUMN role ENUM(...)` 마이그레이션을 제안한다 — 이번 PR에는 없다.

## 5. NewStart UI (STEP 4)

`fragments/_account_menu.html`(BuildIt/Wiki 등이 쓰는 공용 회원가입/학생·강사 가입/ID·PW 로그인
모달)은 **한 글자도 건드리지 않았다**. 대신 `newstart/index.html`·`newstart/chat.html`이 새
fragment(`_newstart_auth :: authControls`)를 쓰도록 교체했다:

- 비로그인: `<a href="/auth/naver/v2/start">네이버로 로그인</a>` 하나뿐(모달 없음 — 필드가 하나뿐이라
  모달 자체가 불필요해졌고, V1에서 계속 의심받았던 "동적 생성 앵커 + preventDefault" 클래스의 클릭
  문제를 구조적으로 없앴다).
- 로그인 후: 계정 메뉴("회원 채팅"/"로그아웃")만 — `[data-account-home]` 없음(위 ROLE 항목 참고).
- `newstart-chat.js`의 로그인 안내 버튼(`#ns-chat-login-btn`)도 동일하게 직접 링크로 교체,
  `window.openLoginModal()` 의존 제거.
- `naver_login.js`(V1의 JS 주입 방식) 참조를 NewStart 페이지에서 제거했다(파일 자체는 삭제하지
  않음 — 다른 곳에서 참조하지 않던 파일이라 남겨둬도 무해).

## 6. 로그 마커 (STEP 7)

`NaverV2Controller.callback()`은 진입 즉시 `NAVER_V2_CALLBACK_RECEIVED`부터 시작해서 모든 분기·
경계마다 마커를 남긴다(secret/token/code/개인정보 값은 로그에 없음) — V1에서 "콜백이 도달했는지조차
로그로 구분 불가능했던" 관측성 공백을 처음부터 설계에 반영했다. 전체 목록은 `NaverV2Controller.java`
Javadoc과 `NaverV2ControllerTest`의 `CapturedOutput` 검증 참고.

## 7. 테스트/빌드

```
./gradlew test --tests "f2r...domain.user.social.v2.*" \
               --tests "f2r...template.NewStartAuthUiContractTest" \
               --tests "f2r...template.NewStartChatPageContractTest"
```
→ 29개 전부 green(NaverV2StateServiceTest 8, NaverV2ClientTest 4, NaverV2ClientContextTest 1,
NaverV2LoginServiceTest 6, NaverV2ControllerTest 7, NewStartAuthUiContractTest 2,
NewStartChatPageContractTest 1).

기존 V1 회귀(변경 없이 그대로 통과 확인): `NaverOAuthStateServiceTest`, `NaverLoginServiceTest`,
`NaverOAuthControllerTest`, `NaverOAuthControllerDisabledTest`, `NaverOAuthClientContextTest`,
`NaverOAuthClientTest`, `FrontendAuthBootstrapTest`, `AdminNewStartNavContractTest` — 전부 green.

`./gradlew bootJar` — BUILD SUCCESSFUL.

**미실행(로컬 MySQL 없음, CI 관행과 동일)**: `AuthFlowIntegrationTest` 등 `@SpringBootTest` 통합
테스트 — 이번 변경이 `SecurityConfig`/`AuthController`/`UserRepository`/기존 회원가입 로직 어느
파일도 건드리지 않았으므로(신규 파일 추가 + NewStart 템플릿 한정 수정) 회귀 위험은 구조적으로 낮다고
판단했다. 다음 세션에서 MySQL 가능한 환경에서 1회 확인 권장.

## 8. NAVER 개발자센터에 등록해야 할 URL

```
NAVER V2 CALLBACK URL:
https://feel2real.co.kr/auth/naver/v2/callback
```

`NaverV2Controller.CALLBACK_PATH`(코드)와 `resolveRedirectUri()`가 실제로 만드는 값 그대로다 —
추측 아님. client_id/secret은 기존 것 그대로 재사용(같은 네이버 앱), **이 Callback URL만 네이버
개발자센터의 같은 애플리케이션에 "추가 등록"**하면 된다(기존 `/oauth/naver/callback` 등록은
지우지 않는다 — STEP 11에 따라 당분간 병행).

## 9. 남은 절차 (STEP 11, 이번 PR 범위 밖)

1. ~~V2 구현~~ ✅
2. ~~테스트 PASS~~ ✅
3. ~~bootJar PASS~~ ✅
4. ~~commit + push~~ ✅ (아래)
5. PR 생성 — 이 문서와 함께 ✅
6. ~~정확한 callback URL 보고~~ ✅ (§8)
7. **다음 단계(이 PR 밖)**: 사용자가 네이버 개발자센터에 §8 URL 등록.
8. V2 실제 인증 1회 검증(운영 배포 후).
9. 성공 확인.
10. NewStart 로그인 버튼을 V2로 최종 전환(이미 이번 PR에서 UI는 V2를 가리키고 있음 — 실제
    "전환"은 배포 시점에 발생. V1 엔드포인트는 당분간 유지해 병행 가능).

## 10. Git Safety 준수

전용 worktree(`feel2real-wt-naver-v2`)에서 별도 branch로 작업, 다른 worktree/branch 무변경.
EASY/Wiki/Chart/시험지/카페 리서치 파일 접촉 없음. `main` 직접 commit 없음. force push/`git clean`
없음. production DB 변경 없음(코드만). 의미 단위로 커밋 후 push, PR 생성. **MERGE: NO / DEPLOY: NO**.
