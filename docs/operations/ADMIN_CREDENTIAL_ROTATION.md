# ADMIN 로그인 ID·비밀번호 교체 절차

> 상태: **코드 구현·로컬 검증 완료, 운영 적용 전(별도 승인 필요)**. 이 문서에는 어떤 ID·비밀번호도 적지 않는다.

## 1. 현재 구조 (조사 결과)
- 계정은 `users` 테이블의 ADMIN 행 1개(운영은 V2 마이그레이션이 만든 최초 계정). 비밀번호는 BCrypt 해시만 저장.
- 로그인은 `POST /api/auth/login` → JWT(HS256)를 HttpOnly 쿠키로 발급. **JWT는 서버에 저장되지 않아** 비밀번호를 바꿔도 기존 토큰은 만료까지 살아 있었다(→ 이번에 ADMIN 토큰 stamp 검사로 해결).
- 기존 `POST /api/auth/admin/password-change`는 최초 ID에 고정돼 있어 ID 교체 후에는 쓸 수 없다.
- 위키 백엔드는 ID가 아니라 서명된 role=ADMIN으로 관리자를 판정한다(ID 변경 영향 없음). 에이전트 토큰(Chart/NewStart/EasyCollector/VisualEditor)은 별도 토큰이라 영향 없음.
- 과거 레코드의 `created_by`/`updated_by`에는 당시 로그인 ID 문자열이 남는다(교체 후에도 보존, 이후 기록부터 새 ID).

## 2. 안전한 교체 방법 (구현됨)
로그인한 ADMIN이 `/admin/account`(상단 메뉴 "계정 보안")에서 **본인이 직접** 입력한다. 채팅·코드·로그·문서로 전달하지 않는다.
- 입력: 현재 비밀번호 + 새 로그인 ID + 새 비밀번호(확인). 규칙은 `docs/API.md`의 `POST /api/admin/account/credentials`.
- 같은 users 행을 갱신 → 관리자 권한·user_id·운영 데이터·FK 유지. 새 비밀번호는 BCrypt 해시로만 저장.
- 성공 즉시: 이전 ID·비밀번호 로그인 불가, 기존 ADMIN 세션 전부 무효(다른 기기 포함), 현재 쿠키 삭제 → 새 ID·비밀번호로 재로그인.
- 일반 회원·TUTOR는 영향 없음(토큰·로그인·DB 모두 불변, 검사 대상 아님).
- DB 스키마 변경 없음(마이그레이션 없음). **코드 배포만 필요.**

## 3. 운영 적용 순서 (승인 후)
1. (배포 승인 후) 이 PR이 포함된 빌드를 배포. 배포 직후 기존 ADMIN 세션은 stamp가 없어 무효가 되므로 관리자는 **기존 ID·비밀번호로 한 번 다시 로그인**한다.
2. 소유자가 `/admin/account`에서 새 ID·비밀번호를 직접 입력해 교체한다(비밀번호 관리자 도구에 저장 권장).
3. 확인: 이전 ID/비밀번호 로그인 실패, 새 ID 로그인 성공, `/admin/analytics`·`/admin/members` 등 정상, 일반 회원 로그인 정상.
4. 확인용 읽기 전용 점검(운영 DB, 값은 출력하지 않는다): ADMIN 행 수, `user_social_accounts`에 ADMIN user_id 연결 여부, `login_id='admin'` 계정 존재 여부(dev-seed는 운영에 없어야 함).

## 4. 롤백
- 스위치 `app.security.admin-session-binding.enabled=false`로 stamp 검사만 끌 수 있다(로그인·교체 기능은 유지).
- 새 비밀번호를 잊은 경우: 기존 비밀번호 확인이 필요하므로 화면으로는 복구 불가 → DB에서 **로컬에서 만든 BCrypt 해시**로 `UPDATE users SET password=… WHERE user_id=…`(별도 DB 변경 승인 필요). 해시는 `htpasswd -nBC 10 ''` 등으로 본인 PC에서 대화형 입력으로 생성하고 평문은 남기지 않는다.

## 5. 남는 위험·권고
- **구 최초 ID는 폐기 후 재사용될 수 있다**: 가입 경로(일반/위메이드/NewStart)에 예약 ID 차단이 없다. 공개 가입으로 해당 ID를 선점해도 STUDENT 권한뿐이지만 감사 로그 혼동 가능 → 예약 ID 차단은 별도 작업으로 권고.
- 최초 비밀번호 평문이 과거 마이그레이션 주석·git 이력·`AdminPasswordHashTest`에 남아 있다. 교체 후 그 값은 무효지만, 같은 비밀번호를 다른 서비스에서 썼다면 함께 바꿔야 한다. (적용된 V2 파일은 Flyway 체크섬 때문에 수정하지 않는다.)
- 네이버·위메이드·NewStart 로그인 경로는 ADMIN 토큰에 stamp를 넣지 않는다 → 그 경로로 얻은 ADMIN 세션은 거부된다(ADMIN은 `/admin/login` ID·비밀번호로만 로그인). 일반 회원 네이버 로그인은 변경 없음.
