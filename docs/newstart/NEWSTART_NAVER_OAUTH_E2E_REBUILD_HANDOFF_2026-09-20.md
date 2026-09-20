# NewStart 네이버 로그인 E2E 재조사 — HANDOFF (2026-09-20)

- 브랜치: `fix/newstart-naver-e2e-rebuild-20260920` (base: origin/main `ea12d85`)
- 상태: **재조사 완료 · CI 게이트 결함 1건 확인 및 수정 · 로컬 빌드/테스트 통과 · PR 생성. 병합/배포 안 함.**
- 이 문서는 "처음부터" 독립적으로 진행한 E2E 재조사 기록이다. 기존 세션들의 결론(아래 §1)을 정답으로
  가정하지 않고, 현재 `origin/main` 소스를 직접 읽고, 로컬에서 프론트 클릭 동작을 재현하고,
  운영 `/oauth/naver/start`를 READ-ONLY로 직접 호출해 하나씩 재확인했다.
- Secret/token/JWT/cookie **값**은 이 문서에 없다. (작업 중 한 차례 터미널 출력에 `Set-Cookie` 값이
  노출된 적이 있었으나 — 짧은 TTL의 서명된 일회성 state nonce와 익명 방문자 UUID였다 — 이 문서·커밋·PR
  어디에도 옮기지 않았다.)

## 1. 이미 main에 병합되어 있던 이전 수정들 (이번 세션이 만든 것 아님)

`origin/main`을 fetch한 시점에 아래 4건이 이미 순서대로 병합되어 있었다(커밋 해시는 병합 전 원커밋):

| 순서 | 커밋 | 내용 |
|---|---|---|
| 1 | `fb6eabf` | `NaverOAuthClient` 생성자 2개 공존으로 Spring이 no-arg 생성자를 찾다 부팅 실패(Deploy #331) → `@Autowired` 명시 |
| 2 | `616a36d`/`d0f5810` | `NaverOAuthClient.TOKEN_URL`이 `auth.naver.com`(EC2에서 연결 자체 실패, 000)을 가리켜 code→token 교환이 매 요청 침묵 실패 → 공식 스펙 호스트 `nid.naver.com/oauth2.0/token`으로 수정 |
| 3 | `6cccaa4` (PR #336) | `NaverOAuthClient`/`NaverOAuthController`에 stage별 WARN 로그(상태코드/예외 클래스만, PII·secret 없음) + `/newstart/chat`에 `?login=` 실패 마커를 사용자에게 노출(기존엔 조용히 사라짐) |
| 4 | `cfcd71e` (PR #337) | 로그인 모달의 "네이버로 로그인" 링크에 명시적 `click → preventDefault + location.assign(href)` 핸들러 추가(기존엔 native `<a href>` 내비게이션에만 의존) |

이 4건 모두 **이번 세션 시작 전에 이미 `origin/main`에 존재**했다. 아래 §2~§4는 이 상태를 전제로 한
독립 재검증이다.

## 2. 독립 재검증 — 각 단계 실측

### 2.1 프론트 버튼 클릭 (로컬 재현, 백엔드 없이)

`naver_login.js`의 클릭 동작만 격리해서 재현했다: `#337` 이전/이후 두 버전을 각각 정적 HTML에 로드하고
(같은 모달 마크업 + `account_menu.js` document-level click delegate 포함) 실제 브라우저로 클릭했다.

- **두 버전 모두** 클릭 시 `<a href="/oauth/naver/start?...">`의 네이티브 내비게이션이 정상 발생했다
  (로컬 정적 서버라 `/oauth/naver/start` 라우트가 없어 404가 떴을 뿐 — 이건 이 재현 서버의 한계이지
  운영과 무관하다. 운영 확인은 §2.2 참조).
- CSS 전수 검색(`pointer-events`, `aria-hidden` 연계 규칙) 결과 `.login-modal-naver`/`.login-modal-social-list`를
  건드리는 규칙 없음. `account_menu.js`의 document-level click delegate는 `[data-auth-login]`/
  `[data-login-modal-close]`/모달 backdrop/`[data-account-menu-trigger]`/`[data-account-logout]`만
  가로채고 `.login-modal-naver`는 건드리지 않음을 코드로 확인.
- 결론: `#337`이 고치려던 "클릭해도 화면 변화 없음" 증상을 **데스크톱 브라우저의 순수 DOM/CSS 로직만으로는
  재현하지 못했다** — 즉 기존 코드에 명백한 클릭 차단 로직은 없었다. `#337`의 명시적 `preventDefault`+
  `location.assign` 핸들러는 (모바일 웹뷰 등 native href 내비게이션에 덜 관대한 환경에 대한) 방어적 강화로
  타당하고 회귀 위험이 없어 **그대로 유지**한다. 증상의 더 유력한 설명은 §1의 1번(부팅 실패로 인한
  서비스 크래시 루프 구간) — 그 구간엔 네이버 로그인뿐 아니라 사이트 전체가 응답하지 않았을 것이다.

### 2.2 OAuth start — 운영 실측 (READ ONLY)

```
GET https://feel2real.co.kr/oauth/naver/start?returnUrl=/newstart
```

응답: `302` → `Location: https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id=...&state=...&redirect_uri=https%3A%2F%2Ffeel2real.co.kr%2Foauth%2Fnaver%2Fcallback`,
`Set-Cookie: NAVER_OAUTH_STATE=...`(Secure/HttpOnly/SameSite=Lax/Path=/, Max-Age=600). **PASS — 지금 이
순간 운영에서 정상 동작 확인.** (client_id는 URL에 실려 브라우저로 나가는 공개 식별자라 비밀값이 아니다.
state/cookie **값**은 본 문서에 옮기지 않았다.)

### 2.3 콜백 / state / token / profile / user / social mapping / JWT / cookie

코드 리뷰 + 기존 세션이 남긴 `docs/newstart/oauth/NAVER_OAUTH_LOGIN_STATE_DIAGNOSIS_2026-09-20.md`의
READ-ONLY SSM 진단 실측(§6, §11)을 근거 자료로 교차 확인(맹신하지 않고 현재 소스와 대조):

- `NaverOAuthController.callback` — state 검증 실패/전달 코드 없음/네이버 error 파라미터를 모두
  `/newstart?login=...` 302로 흡수하고, 회원 식별·생성 단계 예외는 `RuntimeException` catch로
  `NAVER_SOCIAL_LOGIN_FAILED reason=<클래스명>`만 로그 후 `?login=naver_failed`. 소스 대조 결과 §1 표와 일치.
- `NaverOAuthStateService` — HMAC-SHA256 서명(`app.jwt.secret` 재사용) + `MessageDigest.isEqual` 상수시간
  비교 + TTL 10분 + nonce 일치. `NaverOAuthStateServiceTest` green.
- `NaverOAuthClient.exchangeCode`/`fetchProfile` — 호스트 `nid.naver.com`/`openapi.naver.com` 확정(§1-2).
  `NaverOAuthClientTest`(실제 요청 URI/method/Authorization 검증)·`NaverOAuthClientContextTest`(실제 bean
  인스턴스화 검증) green.
- `NaverLoginService` — (provider, providerUserId) 매핑만 사용, 이메일 자동 병합 없음, 항상
  `Role.STUDENT`, `loginId = "naver_" + sha256(providerUserId)[0:16]`(충돌 시 접미사 재시도),
  비밀번호는 해석 불가능한 랜덤 bcrypt. `NaverLoginServiceTest` green.
- `SocialAccount`/`user_social_accounts`(V982) — `(provider, providerUserId)` UNIQUE, User와 같은
  `@Transactional` 안에서 저장되어 원자적. 회원 생성만 성공하고 social 저장이 실패하면 트랜잭션 전체
  롤백(의도된 설계 — 부분 상태 방지).
- JWT 발급(`JwtTokenProvider.issue`)과 쿠키 작성(`CookieUtil.writeAccessToken`)은 **기존 ID/PW 로그인과
  완전히 동일한 코드 경로**다. 운영에서 ID/PW 로그인은 지금도 정상 동작 중(users_total=241)이므로 이
  경로 자체의 결함 가능성은 낮다 — 이번 세션에서 별도 실측하지 않고 "공유 경로, 검증됨"으로 판단.

### 2.4 `/api/auth/me`, NewStart REST(`/api/newstart/chat/**`), WS(`/ws/newstart-chat`)

- `/api/auth/me`: 네이버 로그인 전용 분기 없음. `account_menu.js`/`newstart-chat.js` 모두 200/401만으로
  분기(서버 렌더 아님). 공유 경로.
- `/api/newstart/chat/**`: `credentials:'same-origin'` fetch — `ACCESS_TOKEN` 쿠키 기반, 네이버 전용
  코드 없음.
- `/ws/newstart-chat`: 브라우저 WebSocket 핸드셰이크는 same-origin 쿠키를 자동 동봉 — 네이버 전용
  코드 없음.
- 이 세 경로는 이번 세션에서 실제 인증 세션으로 재현하지 않았다(네이버 계정으로 실 로그인해야 함,
  이번 세션은 그 전 단계까지만 READ ONLY로 검증). **NOT VERIFIED (실측 X, 코드상 네이버 특이 결합 없음)**.

## 3. 이번 세션이 실제로 발견·수정한 결함

기존 4건(§1)을 재확인하는 것 외에, **CI 게이트 자체의 결함**을 새로 발견했다:

- `.github/workflows/build-check.yml`(모든 PR에서 자동 실행)이 `NaverOAuthClientTest`(TOKEN_URL 오호스트
  회귀를 직접 잡는 테스트), `FrontendAuthBootstrapTest`(버튼 배선 회귀), `NewStartChatPageContractTest`
  (콜백 실패 알림 회귀)를 **포함하지 않고 있었다**.
- `.github/workflows/deploy-ec2.yml`(배포 게이트)은 `NaverOAuthClientTest`/`NaverOAuthClientContextTest`만
  있고 `NaverOAuthStateServiceTest`/`NaverLoginServiceTest`/`NaverOAuthControllerTest`/
  `NaverOAuthControllerDisabledTest`/`FrontendAuthBootstrapTest`/`NewStartChatPageContractTest`가
  **빠져 있었다**.
- 즉 이 체인의 회귀 테스트 8개 중 어느 하나도 "PR 게이트 + 배포 게이트 양쪽 모두"에 들어있지 않았다.
  예를 들어 `NaverOAuthClientTest`(가장 결정적인 테스트 — TOKEN_URL이 다시 잘못된 호스트로 바뀌는 걸
  잡는다)는 배포 게이트에만 있고 PR 게이트에는 없었다: PR 리뷰 시점에는 이 회귀를 못 잡고, 배포
  시점에야 잡힌다. 반대로 `FrontendAuthBootstrapTest`(버튼 배선)는 **PR 게이트에도 배포 게이트에도**
  없었다 — 이 테스트가 지금 통과한다고 해서 배포가 막아주는 게 전혀 없었다는 뜻이다.

### 수정

`build-check.yml`, `deploy-ec2.yml` 두 파일에 각각 빠져 있던 테스트를 `--tests` 목록에 추가했다.
결과적으로 두 게이트 모두 네이버 E2E 체인 관련 8개 테스트 클래스를 전부 실행한다:

```
NaverOAuthStateServiceTest, NaverLoginServiceTest, NaverOAuthControllerTest,
NaverOAuthControllerDisabledTest, NaverOAuthClientContextTest, NaverOAuthClientTest,
FrontendAuthBootstrapTest, NewStartChatPageContractTest
```

프로덕션 코드/마이그레이션/설정값은 변경하지 않았다 — §1의 실제 코드 수정은 모두 정확했고 재발할
필요가 없었다. 이번 수정은 순수히 **"다음 회귀가 CI를 뚫고 나가지 못하게"** 만드는 안전망 보강이다.

## 4. 재현/검증

```bash
# 이번에 게이트에 새로 추가된 8개 테스트 (모두 green)
./gradlew test --no-daemon \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthStateServiceTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverLoginServiceTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthControllerTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthControllerDisabledTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClientContextTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClientTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.config.FrontendAuthBootstrapTest" \
  --tests "f2r.tutor_solution.private_tutor_solution_back.template.NewStartChatPageContractTest"

# 전체 bootJar
./gradlew bootJar --no-daemon

# build-check.yml의 전체 75개(now) non-MySQL 테스트 화이트리스트
./gradlew test --no-daemon <build-check.yml의 --tests 전체>
```

- `bootJar`: **BUILD SUCCESSFUL**.
- 새로 게이트에 추가한 8개: **전부 green**.
- `build-check.yml` 전체 화이트리스트(75개 클래스, 717 tests): **716 pass, 1 fail**.
  실패 1건은 `QuestionWikiPrivateReferenceCorpusContractTest`(Python 코퍼스 검증 스크립트를 서브프로세스로
  실행하는 테스트) — 이 로컬 Windows 환경에 스크립트가 기대하는 형태의 `python`이 없어 종료코드
  9009(Windows "명령을 찾을 수 없음")를 반환한 것으로, 네이버/NewStart 코드와 무관한 **환경 문제이며
  이번 브랜치의 변경과 무관하게 사전에 존재**한다(EASY/Chart/Cafe DB/내신 Wiki 영역 — 이번 작업
  범위 밖, 손대지 않음).
- MySQL 필요한 `@SpringBootTest` 통합 테스트는 로컬에 MySQL이 없어 이번 세션에서 실행하지 않았다(CI의
  `build-check.yml` 관행과 동일 — "MySQL 없는 CI에서 실행 가능한 테스트만" 원칙을 그대로 따름).

## 5. 금지선 준수

- production DB에 쓰기 없음(§2.2는 GET 1회, 상태 변경 없음).
- 기존 users 데이터 변경 없음.
- secret/token/JWT **값** 미출력(§0 참고 — cookie 값이 한 차례 터미널에 노출된 사고는 있었으나 어떤
  문서·커밋·PR에도 옮기지 않았다).
- migration 없음(V982는 기존 그대로, 손대지 않음).
- 배포 없음, merge 없음.
- EASY / Chart / Cafe DB / 내신 Wiki 관련 파일 접촉 없음.

## 6. 남은 과제 (다음 세션 후보)

- §2.4의 `/api/auth/me` 이후 실 로그인 세션으로 NewStart REST/WS까지 이어지는 완전한 실사용자 E2E는
  아직 "NOT VERIFIED" — 실제 네이버 계정으로 1회 로그인 시도 + `user_social_accounts` 1건 생성 확인이
  최종 확인 지점이다(§1의 4개 수정이 모두 반영된 이후 아직 실사용자 재시도가 기록되지 않았다는 것이
  이전 세션의 마지막 관찰이었다).
- `diagnose-naver-oauth-readonly.yml`은 자체 주석대로 "진단 완료 후 제거할 임시 워크플로우"다 — 위
  실사용자 E2E가 확인되면 별도 PR로 제거를 고려할 수 있다(이번 PR 범위 밖).
