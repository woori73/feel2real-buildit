# HANDOFF — NewStart 회원 비밀번호 관리·재설정 (2026-10-02)

작업 브랜치: `feat/newstart-admin-password-management` (base `origin/main` @ `cf9a2277`)
상태: **구현 완료, LIVE 화면 QA 7/10단계까지 통과, PR 미생성**. 우선순위 전환(#470 먼저 마감)으로 여기서 중단하고 학원에서 이어간다.
본 문서의 코드는 커밋되어 있지만 **PR을 만들지 않는다** — 배후 지시: "PR 생성/병합은 하지 말고 학원에서 이어서 작업한다."
이어 작업하기 전: 이 브랜치의 base는 `cf9a2277`이고 그 뒤 main에 #469(V1019)·#470(V1020)이 들어왔다. 시작 시 `origin/main`(2026-10-02 병합 후 `637a475f`)을 merge한 뒤 진행한다. 본 작업의 V1021은 그보다 높은 번호라 순서가 맞으므로 out-of-order 위험은 없다.

## 1. 이 브랜치에 들어간 것

### 요구사항 매핑
- ① ADMIN 회원관리 화면: `/admin/newstart/members` (topnav `새출발 회원`) — 로그인 ID·카페 닉네임 검색, 상세(로그인 ID/닉네임/구분/계정 상태/가입일/최근 로그인 + 초기화 이력·roster 연결).
- ② ADMIN 초기화: 상세로 대상을 확정한 뒤에만 동작, `NewStartTempPasswordGenerator` 12자 1회용, 응답에서 한 번만 표시, 재조회 API 없음.
- ③ 강제 진입: `NewStartPasswordGateInterceptor` — `password_reset_required=true` 회원의 `/newstart/**`·`/api/newstart/**`를 `/newstart/password/set` 302 / API는 `428 PASSWORD_CHANGE_REQUIRED`로 막는다.
- ④ 본인 변경: `/newstart/password/set` + `POST /api/newstart/me/password` (현재/새/확인, 현재 비밀번호 검증 필수, 대상 인자 없음).
- ⑤ 기존 구조 정리: **`reset1234` 고정 비밀번호 제거**. `request`는 1회용·60분 만료 `resetUrl`만 발급(no-store), 비밀번호 변경은 `confirm`에서만. `app.password-reset.*` 설정 4개 프로필에서 삭제.
- ⑥ HARD GATE 테스트, ⑦ 감사 로그 `newstart_admin_password_resets`, ⑧ 데이터 보존(권한·roster·채무·상담·DM), docs(API/FLOWS/DOMAIN/WORKFLOW) 동기화.

### 파일
- 신규: `domain/newstart/password/**`(entity/repository/dto/service/controller 3종), `config/NewStartPasswordGateInterceptor.java`, `V1021__add_newstart_admin_password_resets.sql`, `templates/admin/newstart_members.html`, `templates/newstart/password_set.html`, `static/js/admin_newstart_members.js`, `static/js/newstart-password-set.js`, `src/test/js/newstart_admin_member_password_contract.test.js`.
- 수정: `User`(markPasswordResetRequired/clearPasswordResetRequirement/markLogin), `UserRepository`(searchByRoleAndLoginIdOrNickname/findByRole/existsByUserIdAndPasswordResetRequired), `UserService.login`(markLogin), `WebMvcConfig`(게이트 등록/제외), `AdminNewStartPageController`, `_admin_topnav.html`, `admin/newstart.html`, `_newstart_auth.html`(계정 메뉴 [비밀번호 변경]), `PasswordResetService`/`AuthController`/`PasswordResetTokenResult`/`PasswordResetRequestResponse`, `password_reset_request.js`/`.html`/`password_reset.css`, `application-{local,dev,stg,prd}.yaml`, docs 4종.

## 2. 검증 완료 (재실행할 필요 없음)

- Java 단위/슬라이스: `PasswordResetServiceTest`, `NewStartAdminPasswordResetServiceTest`, `NewStartMemberPasswordServiceTest`, `NewStartTempPasswordGeneratorTest`, `NewStartAdminMemberQueryServiceTest`, `NewStartPasswordGateInterceptorTest`, `NewStartPasswordAuthorityTest`(HARD GATE 4케이스), `AuthFlowIntegrationTest#passwordResetRequestIssuesOneTimeLink` → 통과.
- JS: `node --test src/test/js/newstart_*.test.js` → **118/118 통과** (신규 12 케이스 포함).
- **@WebMvcTest 회귀 주의**: 게이트 인터셉터는 `UserRepository`를 직접 주입받으면 모든 MVC 슬라이스가 깨진다(인터셉터가 슬라이스에 스캔됨). `ObjectProvider<UserRepository>` + 없으면 통과로 해결 — 되돌리면 6개 슬라이스 재실패.
- 로컬 기동 LIVE QA (브라우저, `localhost:8081`, ADMIN 로그인 후)까지 확인한 항목:
  1. `/admin/login` `admin/admin1234` → `/admin/students` (세션 확인).
  2. topnav에 `새출발 회원` → `/admin/newstart/members` 존재· 이동 동작.
  3. 화면 열면 부팅 검색으로 목록 3행 렌더 (`adreamersik23/새출발탕감/감사회원`, `student`, `test-student`) — 최근 로그인 `기록 없음`(과거 소급 없음 정상).
  4. 로그인 ID 검색 `student` → 2행(`student`,`test-student`), 닉네임 검색 `새출발탕감` → 1행. 페이징 `총 N명`.
  5. 헤더/주의 문구(원문·hash 조회 불가, 1회 표시, 데이터 보존) 렌더.
  6. 스크립트·화면 로딩 오류 없음(네트워크 실패 없음).

## 3. 남은 테스트 (학원에서의 다음 작업)

1. **상세 패널**: 목록 [상세] → 7개 필드 + `lastResetAt`/`lastResetBy`/`thanksRosterLinked` 표시 확인.
2. **취소 경로**: [비밀번호 초기화] → `window.confirm` 취소 → POST 없음·임시 박스 숨김 유지.
3. **초기화 실행**: 확인 후 POST 200 → `#nm-temp-value`에 12자 값 1회 표시 + `초기화했습니다...` 문구가 목록 갱신 후에도 지워지지 않는지(= `search(..., true)` keepMessage 분기) 확인.
4. **재조회 불가**: [닫기] → 값 소거, 새로고침·다시 상세 열기도 값 없음. 감사 row(`newstart_admin_password_resets`)에 대상 ID·ADMIN·시각만 남고 비밀번호 필드가 없는지 상세 화면의 `lastResetAt`으로 확인.
5. **강제 게이트**: 초기화한 테스트 계정으로 임시 비밀번호 로그인 → 어떤 `/newstart/**` 화면이든 `/newstart/password/set`으로 302, `/api/newstart/**` 호출은 `428`(`PASSWORD_CHANGE_REQUIRED`).
6. **새 비밀번호 저장**: 8자 미만/불일치 → 서버 400 문구. 정상 저장 → `/newstart` 홈으로 돌아가고 채팅·질문요청 등이 열리는지.
7. **본인 변경**: 계정 메뉴 [비밀번호 변경] → 현재 비밀번호 오답 400 / 정답 저장 성공.
8. **HARD GATE LIVE**: 일반회원·(부여 후)감사회원 세션에서 타인 `POST /api/admin/newstart/members/{id}/password-reset` → 403, 비로그인 → 401, ADMIN → 200.
9. **데이터 보존**: 초기화 전후 `memberType`(감사회원 배지), `thanksRosterLinked`, `/newstart` 기능 접근성이 그대로인지.
10. PR 생성(요구 0–11 충족 서술. V1019/V1020이 이미 main에 병합되어 V1021 out-of-order 안내는 필요 없다). MERGE/DEPLOY는 지시를 받고 실행.

## 4. 환경 메모 (재현 실패 방지)

- 로컬 DB는 공유 `localhost:3308/f2r_db`이고 **이 브랜치의 부팅이 V1021을 적용해 이제 schema max = 1021**.
- 따라서 `origin/main`(V1020까지 병합됨, V1021 없음)을 checkout한 브랜치는 로컬 기동·`@SpringBootTest`에서 `Detected applied migration not resolved locally: 1021`로 컨텍스트 로딩이 실패한다. 로컬 실행 시 `--spring.flyway.validate-on-migrate=false`를 붙일 것(커밋 설정 변경 금지, 실행 플래그로만).
- 같은 이유로 `./gradlew test` 전체는 이 머신에서 실패 항목이 생긴다(코드 회귀 아님). 스코프 지정 실행 권장:
  `./gradlew test --tests "*NewStart*Password*" --tests "*PasswordReset*" --tests "*NewStartPasswordAuthority*"`
- 8080은 다른 세션이 점유 → 이 작업은 `--server.port=8081`.
- `reset1234`는 코드·설정에서 사라졌고, 문서/테스트의 문자열은 "그 값이 없다"를 고정하는 단언과 제거 기록에만 남는다.

## 5. 지켜야 할 제약 (원문 유지)

- 비밀번호 원문 조회 기능 금지, hash 복호화·ADMIN 화면 표시 금지, 원문 DB 저장 금지(`PasswordEncoder` 사용), 임시 비밀번호·재설정 값을 로그와 감사 로그에 남기지 금지.
- 실제 회원 비밀번호를 바꾸지 말 것(로컬 시드 `student`/`test-student`/`adreamersik23` 등 테스트 계정으로만 QA), DB 직접 수정 금지, 기존 회원·권한·1:1 연결·데이터 보존(재가입 금지), 기존 통계/다른 서비스 집계 훼손 금지, 불가능한 항목은 과거 숫자를 임의 생성하지 말 것.
