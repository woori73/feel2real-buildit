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
| 5 | code→token 교환 | `NaverOAuthClient.exchangeCode` → `POST https://auth.naver.com/oauth2.0/token` | **UNKNOWN** — 실패 시 `Optional.empty` → `302 /newstart/chat?login=naver_failed` (**무로그·침묵 실패**). EC2 outbound egress 확인 필요 |
| 6 | 프로필 조회 | `fetchProfile` → `GET https://openapi.naver.com/v1/nid/me` | **UNKNOWN** — 동일하게 침묵 실패 가능 |
| 7 | 소셜 연결/회원 생성 | `NaverLoginService.loginOrUpdate` (STUDENT 전용, 이메일 병합 없음) | **UNKNOWN** — 예외 시 `log.warn("naver callback failed (detail suppressed): …")`. 최근 저널 grep 결과 없음(12h 윈도우에는 18:53 이전 부팅 로그만 확인). DB `user_social_accounts` count로 간접 확정 예정 |
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
