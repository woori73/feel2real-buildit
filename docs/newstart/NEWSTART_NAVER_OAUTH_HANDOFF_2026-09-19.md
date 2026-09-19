# NewStart 네이버 OAuth 로그인 — 작업 HANDOFF (2026-09-19)

- 브랜치: `feat/newstart-naver-oauth-20260919` (base: origin/main `52c7ca48263cd8475decb1e6b0cb90b286b0d6d5`)
- 상태: **구현 완료 · 로컬 테스트 통과 · PR 생성. 병합/배포 안 함.**
- 이 작업은 학원 PC ef395ba 복구가 아니다. GitHub 클라우드 전수 조사(313 branches + PR refs + diff pickaxe)로
  remote에 네이버 OAuth 코드가 존재하지 않음을 확인 후, 최신 main 기준 신규 구현했다.

## 무엇을 만들었나

기존 Feel2Real 인증(JWT HttpOnly 쿠키)을 **그대로 재사용**하는 네이버 소셜 로그인. 별도 회원 시스템 없음.

| 영역 | 내용 |
|------|------|
| 백엔드 | `domain/user/social/`: `SocialProvider`, `SocialAccount`(entity `user_social_accounts`), `SocialAccountRepository`, `NaverOAuthProperties`, `NaverOAuthStateService`(서명 쿠키 state), `NaverOAuthClient`(token/profile, java.net.http), `NaverLoginService`(식별/생성), `NaverOAuthController`(`GET /oauth/naver/start`, `GET /oauth/naver/callback`) |
| DB | `V982__add_user_social_accounts.sql` — UNIQUE(provider, provider_user_id) + UNIQUE(user_id, provider), FK CASCADE. `users` 테이블은 손대지 않음 |
| UI | `static/js/naver_login.js`가 `/newstart`, `/newstart/chat` 로그인 모달의 기존 `.login-modal-social-list` placeholder를 "네이버로 로그인" 버튼으로 치환. 기존 이메일/비밀번호 로그인 UI 그대로. CSS `account_menu.css`에 `.login-modal-naver` 추가 |
| 설정 | `application.yaml`에 `app.naver.oauth.{client-id,client-secret,redirect-uri}` ← `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` / `NAVER_OAUTH_REDIRECT_URI`. **미설정 시에도 부팅 정상, 버튼 진입 시 `/newstart?login=naver_unconfigured`** |
| 문서 | API.md §3에 start/callback 계약, DOMAIN.md §4.54에 `user_social_accounts` |
| 테스트/CI | 4개 클래스 19+개 테스트 green (`domain.user.social.*`). build-check.yml 화이트리스트에 추가 |

## 보안 설계 요약

- state: HttpSession 없는 stateless 서버라 **HMAC-SHA256 서명 HttpOnly 쿠키** `NAVER_OAUTH_STATE`(payload `nonce|returnUrl|issuedSec`.sig, 키=`app.jwt.secret`, TTL 10분, 상수시간 비교).
- open redirect 방어: `returnUrl`을 서버가 sanitize(站内 `/`만, `//`· 프로토콜·백슬래시·제어문자·`|` 거부, 이중 디코딩 루프)하고 서명 payload에 봉인 → callback 복귀지는 start 시점 값만 사용.
- 이메일 자동 병합 없음: 연결 키는 (NAVER, providerUserId)뿐.
- 생성 회원 role 항상 STUDENT — ADMIN 자동 부여 경로 없음. `/admin/newstart` 등 기존 권한 설정 무변경.
- 비밀번호 NOT NULL 회피: 로그인 ID `naver_<sha256 16자리>` + 랜덤 bcrypt 해시(비밀번호 로그인 불가 의도됨).
- Secret은 환경변수만. 코드/HTML/GitHub에 값 없음. 실패 리다이렉트는 `?login=naver_failed` 단일 마커(상세 비노출).

## 재현/검증 방법

```bash
./gradlew test --tests "f2r.tutor_solution.private_tutor_solution_back.domain.user.social.*"   # green
```
- 기존 실패와의 구분: AcademyLmsSecurityIntegrationTest 등 일부 통합 테스트 실패는 pristine base(`52c7ca4`)
  worktree 대조로 **기존 문제** 증명. MySQL(localhost:3308) 의존 테스트는 로컬 Docker 엔진 고장으로 실행 불가(CI 관행과 동일하게 화이트리스트 단위 테스트만 검증).
- 실제 브라우저 E2E(네이버 로그인 창)는 아래 선행조건 없이는 불가능.

## USER ACTION REQUIRED — 네이버 개발자센터 (이 단계에서 STOP)

1. https://developers.naver.com → 내 애플리케이션 등록 (서비스명 "새출발"/BuildIt 등).
2. **Outbound Computer / Mobile both** 선택, OAuth 2.0 신청.
3. 등록할 **Callback URI** (prd 기준): `https://{prd 도메인}/oauth/naver/callback`
   - local에서 테스트할 경우 해당 host의 같은 경로도 추가 등록 (또는 `NAVER_OAUTH_REDIRECT_URI`로 override).
4. Client ID / Client Secret 발급 → 서버 환경변수 주입 (**GitHub 커밋 금지**):
   `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, (선택) `NAVER_OAUTH_REDIRECT_URI`
5. 이후 `./scripts/run-local.sh` 또는 prd Actions 디플로이에서 flyway가 V982 자동 적용 → `/newstart` → 로그인 모달 → "네이버로 로그인" 흐름 확인.

## 다음 작업 후보

- prd/apex 환경별 redirect URI 값 확정 후 GH Actions 시크릿/환경설정 등록(이 저장소는 앱 env 주입 방식 따름).
- 카카오/구글 등 provider 확장 시 `SocialProvider` enum + start/callback 대칭화(현재 NAVER 단일에 집중).
- 네이버 연결 회원의 프로필 수정 시 name/nickname 초기값 정책 재확인.
