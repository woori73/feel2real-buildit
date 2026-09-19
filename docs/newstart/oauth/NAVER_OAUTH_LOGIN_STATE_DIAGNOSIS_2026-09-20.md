# NewStart 네이버 OAuth — 로그인 상태 미반영 진단 (2026-09-20)

> 상태: **PHASE 1 진단 진행 중**. 이 문서는 Secret/token/JWT 값을 절대 포함하지 않는다.
> 최종 root cause와 수정은 진단 워크플로우(#332) 결과 수집 후 아래에 추가한다.

## 1. 증상

네이버 로그인 → 개인정보 동의 → `https://feel2real.co.kr/newstart/chat` 복귀 성공.
그런데 헤더 우측 상단이 계속 `로그인 / 회원가입` (비로그인 상태 표시).

## 2. 코드 기준 흐름 추적 (main 116ceae, deploy #331 빌드 = 2026-09-19 18:52 UTC 기동분)

| # | 단계 | 코드 위치 | 판정 |
|---|------|-----------|------|
| 1 | `GET /oauth/naver/start` | `NaverOAuthController.start` | **PASS (실측)**: 302 → `nid.naver.com/oauth2.0/authorize?response_type=code&client_id=…&state=…&redirect_uri=https%3A%2F%2Ffeel2real.co.kr%2Foauth%2Fnaver%2Fcallback`, `Set-Cookie: NAVER_OAUTH_STATE` (Secure/HttpOnly/Lax/Path=/, Max-Age=600) |
| 2 | 네이버 인가·동의 | 외부 | PASS (사용자 확인: 동의 화면 진입·동의 클릭) |
| 3 | `GET /oauth/naver/callback` 도달 | `callback()` | **PASS (실측)**: 위조 param 호출 시 `302 → /newstart?login=naver_invalid_state` — 컨트롤러·라우팅·first-match fallback 모두 정상 |
| 4 | state 검증 | `NaverOAuthStateService.validate` | 코드 정상이며, 사용자가 `/newstart/chat`(returnUrl)으로 복귀한 사실 = **state 검증 통과 = 실사용자flow PASS** |
| 5 | code→token 교환 | `NaverOAuthClient.exchangeCode` → `POST https://auth.naver.com/oauth2.0/token` | **FAIL ← ROOT CAUSE**: 호스트 오류(auth.naver.com은 EC2에서 연결 실패 000). 공식 endpoint는 `nid.naver.com/oauth2.0/token`. `Optional.empty` → `302 {returnUrl}?login=naver_failed` (**무로그·침묵 실패**) |
| 6 | 프로필 조회 | `fetchProfile` → `GET https://openapi.naver.com/v1/nid/me` | NOT_REACHED (5에서 단절; host 자체는 EC2 도달 401 확인 = 코드 정상) |
| 7 | 소셜 연결/회원 생성 | `NaverLoginService.loginOrUpdate` (STUDENT 전용, 이메일 병합 없음) | **NOT_REACHED (실측)** — `user_social_accounts` 0건, `naver_%` users 0건. 코드 정책 자체는 리뷰 정상 |
| 8 | JWT 발급 | `tokenProvider.issue(userId, loginId, role)` — 일반 로그인과 **동일 코드** (HS256, subject=userId). `User.userId`는 IDENTITY 생성이라 save 후 null 불가 | PASS (코드 리뷰) |
| 9 | `ACCESS_TOKEN` 쿠키 설정 | `CookieUtil.writeAccessToken` — `Path=/`, `HttpOnly`, `Secure`(prd=true), `SameSite=Lax`, `Domain=.feel2real.co.kr`(prd yaml 80행), Max-Age=86400. 일반 로그인과 동일한 경로 | 코드 정상. 실제 Set-Cookie 실측은 PHASE 2 예약(5단계가 침묵 실패면 미도달) |
| 10 | `/newstart/chat` 리다이렉트 | `sendRedirect(target)` | 관찰됨 (단, URL 쿼리 `?login=naver_failed` 여부를 사용자 미확인 — **결정적 분기**) |
| 11 | 서버 인증 | `JwtAuthenticationFilter` → `ROLE_<role>` → `/api/auth/me` (`.api/** .authenticated()`) | 코드 정상, 일반 로그인과 동일. 익명 실측: `GET /api/auth/me` → 401 (정상이 기준선) |
| 12 | 헤더 UI | `account_menu.js`가 `GET /api/auth/me` 응답 200/401로만 분기 (서버 렌더 아님, 세그먼트 조건 없음). `newstart-chat.js`도 동일 계약 | 401이면 무조건 "로그인/회원가입" — **UI 버그 아님, 인증 자체가 안 된 것** |

## 3. 유력 가설 (우선순위)

- **H1 (유력)**: 5/6단계 서버→네이버 토큰/프로필 호출 실패 → `naver_failed` 쿼리가 붙은 채
  `/newstart/chat`로 복귀했고, 화면이 동일해 사용자가 정상 복귀로 인식.
  근거: (a) 침묵 실패 코드 경로, (b) journalctl "No entries"는 예외 없음과 양립,
  (c) 현재 저널에 naver warn 없음.
- **H2**: 토큰/회원 생성까지 성공했으나 쿠키가 브라우저에 미저장/미동반
  (Domain/Secure/브라우저 설정). 근거 약함 — 일반 로그인이 동일 설정으로 동작 중.
- H3: 헤더 UI 판별 버그 — 코드 리뷰로 **기각** (12단계).

## 4. 결정적 확인 항목 (워크플로우 #332로 수집)

1. EC2 → `auth.naver.com` / `openapi.naver.com` outbound HTTP 코드 (egress 확인)
2. journal `naver|oauth` 라인 (24h)
3. `user_social_accounts` count/created_at + `login_id LIKE 'naver%'` count — 1 이상이면 H1 기각, 0이면 H1 확정 방향
4. 배포 jar에 Naver 클래스 존재, `NAVER_*` env 변수 존재 여부(값 비공개), `deployed` 18:52 UTC가 #331 아님(=116ceae? HEAD=116ceae와 코드 동일성 확인)
5. 재현 시 사용자 협조: 최종 URL 쿼리문(`?login=` 유무)과 DevTools 쿠키 목록에 `ACCESS_TOKEN` 존재 여부

## 5. 금지선 준수

네이버 개발자센터 설정·redirect URI·클라이언트 자격증명·authorize URL 변경 없음.
production DB 쓰기 없음(READ ONLY count 조회만), migration 없음, secret/JWT 값 출력 없음.

---
(PHASE 2/3 결과는 아래에 추가 기재)

## 6. PHASE 2 실측 결과 (진단 워크플로우 run 35465520255, 2026-09-19 19:49~19:50 UTC)

```
CALLBACK_REACHED: YES (curl 위조 param → /newstart?login=naver_invalid_state, 라우팅 정상)
NAVER_TOKEN_EXCHANGE: FAIL ← ROOT CAUSE (아래)
PROFILE_FETCH: NOT_REACHED (교환 실패로 이후 단계 미도달)
SOCIAL_ACCOUNT: NOT_FOUND (user_social_accounts 총 0건)
USER: NOT_CREATED (login_id LIKE 'naver%' = 0명)
JWT_CREATED: NO (8단계 미도달)
SET_COOKIE_PRESENT: NO (callback 성공 경로 자체가 실행되지 않음)
REDIRECT_TARGET: {returnUrl}?login=naver_failed — 화면이 쿼리를 무시해 /newstart/chat과 구분 불가
AUTH_AFTER_REDIRECT: NO
HEADER_AUTH_STATE: LOGGED_OUT (account_menu.js는 /api/auth/me 401 시 항상 로그인/회원가입 표시 — UI 버그 아님)
```

- 배포 상태: f2r-app active, app.jar 17:21 UTC 빌드(#331 이후), Naver 6개 클래스 포함.
  NAVER_CLIENT_ID / NAVER_CLIENT_SECRET / NAVER_OAUTH_REDIRECT_URI 모두 SET_NONEMPTY(값 미출력).
- EC2 outbound 실측: `auth.naver.com/oauth2.0/token` → **HTTP 코드 000(연결 실패, 10ms)** /
  `openapi.naver.com/v1/nid/me` → 401(도달 정상). 즉 특정 호스트 자체 문제지 general egress 아님.
- journal 24h `naver|oauth`: 16:28 이전 부팅 실패(#331에서 해결된 Bean 오류)만 있고
  callback 관련 라인은 없음 — `exchangeCode` 실패 경로가 무로그 침묵 실패인 것과 일치.

## 7. ROOT CAUSE (확정)

`NaverOAuthClient.TOKEN_URL`이 **잘못된 호스트 `https://auth.naver.com/oauth2.0/token`**을 가리켰다.
네이버 공식 API 명세(devs.naver.com 로그인 API)의 token endpoint는 authorize와 동일 호스트
**`https://nid.naver.com/oauth2.0/token`**이다. EC2에서 auth.naver.com은 연결 자체가 실패(000)하므로
code→token 교환이 매번 `IOException → Optional.empty`로 침묵 실패 → callback은 예외 없이
`/newstart/chat?login=naver_failed`로 302(화면은 쿼리를 표시하지 않아 사용자에게 "정상 복귀"로 보임) →
JWT/`ACCESS_TOKEN` 쿠키 발급 자체가 Never → 헤더가 계속 비로그인. 기존 ID/PW 로그인은 같은
JWT/쿠키/필터를 거치므로 정상 — naver-only 증상과 일치.

## 8. PHASE 3 수정 (최소)

1. `NaverOAuthClient.TOKEN_URL` → `https://nid.naver.com/oauth2.0/token` (1줄 + 근거 comment).
2. 회귀 테스트 `NaverOAuthClientTest` 신설: HttpClient만 mock해 **실제 요청 URI/method/Authorization**을
   검증 — token=POST nid.naver.com/oauth2.0/token, profile=GET openapi.naver.com/v1/nid/me.
   (기존 컨트롤러 테스트는 client를 mock하므로 URL 오류를 원리적으로 잡을 수 없어 신설)
3. `deploy-ec2.yml` 게이트에 `NaverOAuthClientTest` 추가(다음 오경유도 배포 전 차단).
4. 미수정(정상 확인): controller/cookie/filter/header 렌더링 — 모두 코드 정상.
5. 변경 없음(금지선 준수): 네이버 개발자센터·redirect URI·env·authorize URL·migration·DB 쓰기.

## 9. 남은 검증 (배포 후)

- E2E: 실제 네이버 동의 → `/newstart/chat` 복귀 시 `?login=` 없이 ACCESS_TOKEN 쿠키 설정,
  헤더 로그인 상태 표시, `user_social_accounts`에 NAVER 1건 생성 확인(count 조회).
- 신규 STUDENT 회원은 `naver_` 접두 loginId로 생성될 예정(기존 계정과 email 병합 없음).
- 진단 임시 워크플로우(`diagnose-naver-oauth-readonly.yml`)는 E2E 확인 후 제거 PR 진행.

## 10. 배포 및 배포 후 실측 (2026-09-19 20:08~20:13 UTC)

- PR #333 CI(build) PASS → main 머지. `Deploy to EC2 (manual)` run 35466456801 **SUCCESS**(자동 health gate 통과).
- health: `GET /actuator/health` → `status: UP`, `/oauth/naver/start` → 302, `/newstart/chat` → 200.
- SSM 재진단(run 35466720999): f2r-app active, `app.jar` mtime **20:10**(=신규 수정 빌드),
  EC2→`nid.naver.com/oauth2.0/token` **200 도달 확인**(기존 auth.naver.com은 000 그대로 — 수정이 유효한 호스트로 우회했음을 실증).
- `user_social_accounts` / `naver_%` users = 아직 0 (배포 후 실제 네이버 로그인 시도가 없었으므로 정상).
  **남은 것: 사용자의 실제 1회 E2E 로그인**(동의 → 헤더 로그인 표시 → social 1건). 확인 후 이 섹션에 결과 기록 및 임시 진단 워크플로우 제거 PR 진행.
