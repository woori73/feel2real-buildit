# HOME → ACADEMY HANDOFF — 2026-10-01 (감사회원 셀프 인증 재설계, PR #463)

> **상태: 코드 push 완료 / 테스트 일부 미완 / CI 미실행 / 머지·배포 금지.**
> 학원 PC의 새 Claude 세션은 이 문서와 PR #463을 읽고 "9. 가장 먼저 실행할 것"부터 이어서 진행한다.
> 실패·미확인 항목을 PASS로 바꾸지 말 것(아래 7·8 참고).

## 1. 브랜치 / PR / HEAD

- repository: `woori73/feel2real-buildit`
- branch: `feat/newstart-thanks-member-self-verify`
- PR: **#463** — https://github.com/woori73/feel2real-buildit/pull/463 (OPEN, **머지·배포 안 함**)
- 코드 HEAD(핸드오프 직전 push): **`899c104ed7fdd1f3408554dee5df88fa12b96425`** — 이 문서는 그 다음 커밋으로 올라간다. `git log -3`으로 최신 SHA 확인.
- base: origin/main `c45a6b4c` 기준에서 분기(이후 main 이 앞서 갔을 수 있음 — 이어받을 때 `git fetch` 후 충돌/V1017 번호 충돌 여부 확인).
- ⚠ PR #463 본문(설명)은 **옛 설계(운영자 승인 + 인증코드)** 그대로다. 재설계 내용에 맞게 PR 본문을 갱신해야 한다.

## 2. 재설계 완료 범위 (코드 push 됨)

사용자 지시: "503명을 운영자가 확인하는 시스템"이 아니라 **"대부분 자동/셀프 인증 + 예외만 운영자 처리"**.

- 회원: 네이버 로그인 → **카페 닉네임만 1회 입력** → 서버 자동 확인 → roster ↔ OAuth userId 1:1 연결 + `THANKS_MEMBER` 자동 부여 → 이후 네이버 로그인만으로 사용. 추가 입력(가입일·등급변경일·인증코드) 없음.
- 예외만 ADMIN **'감사회원 인증 예외'함** 적재 + **운영자 이메일 알림**. 정상 인증은 운영자에게 알리지 않음.
- 이전 방식(인증코드 발급 → 카페 쪽지 → 운영자 승인)의 코드·테스트·화면은 **전부 제거**됐다.
- 신규/변경 주요 파일
  - `NewStartThanksVerificationService` — 자동 인증 판정, 예외 적재, 운영자 resolve/dismiss
  - `ThanksExceptionNotifier`(+`ThanksExceptionReason`) — 예외 메일(SMTP 환경변수, 비동기, 미설정 시 건너뜀)
  - `NewStartThanksProperties`(`app.newstart.thanks.*`), `NewStartThanksVerification` 엔티티/레포
  - `NewStartThanksVerificationController`(`/api/newstart/thanks-verification`), `AdminNewStartThanksExceptionController`(`/api/admin/newstart/thanks-exceptions`)
  - 화면: 회원 `/newstart/thanks-guide` 하단 섹션(`newstart-thanks-verify.js`), 운영자 `/admin/newstart/thanks-exceptions`(`admin_newstart_thanks_exceptions.js`), 관리자 메뉴 카드
  - `V1017__add_newstart_thanks_self_verification.sql`(파일명 변경: 구 `..._claims.sql`) — **미머지·운영 미적용**이라 내용을 통째로 재작성했다
  - `docs/API.md`, `docs/DOMAIN.md` 갱신, `build.gradle`에 `spring-boot-starter-mail` 추가

## 3. Naver OAuth email 관련 변경 (조사 결과 포함)

- **조사**: authorize 요청에 `scope`를 보내지 않는다(`application-prd.yaml`에 scope 없음) — 네이버는 개발자센터 "제공 정보" 설정 + 회원 동의로 필드를 준다. 운영 DB 집계(2026-10-01, 개수만): 네이버 계정 **1개**, 이메일 있음 1·`@naver.com` 1(표본이 1이라 일반화 불가). 코드상 **이메일은 최초 가입(provisioning) 때만 `users.email`에 저장**되고 기존 회원 재로그인에서는 갱신되지 않았다(= 동의 이전 가입자·이메일 변경자는 영구히 비어 있음).
- **변경**: `user_social_accounts.provider_email`(V1017) 추가, 소문자 정규화. 신규 가입 시 저장(`NaverAccountProvisioningService`), **기존 회원도 로그인마다 갱신**(`NaverAccountResolutionService.refreshProviderEmail`, 실패해도 로그인 비차단, 이메일 로그 미출력). 이메일이 제공되지 않은 로그인은 기존 값을 지우지 않는다. 판정 전용이며 화면·메일에 노출하지 않는다.
- **이메일을 못 받는 경우(조사 결과)**: ① 개발자센터 "제공 정보"에 이메일 항목이 꺼져 있음 ② 선택 항목이라 회원이 동의 화면에서 해제함 ③ 이메일 동의 이전에 가입한 기존 회원(provider_email 컬럼이 이번에 생겼으므로 다음 로그인에서 동의돼 있어야 채워짐) ④ 네이버 계정 대표 이메일이 `@naver.com`이 아닌 외부 도메인 — ③④는 `EMAIL_MISSING`/`EMAIL_NOT_NAVER` 예외로 운영자에게 간다. (재동의 유도 `auth_type=reprompt` 링크는 로그인 흐름 변경이라 **구현하지 않음** — 후속 후보)
- 확인하지 못한 것: 개발자센터 콘솔 설정(이 PC에서 확인 불가), 실제 신규/기존 회원 다수의 이메일 제공률.

## 4. 자동 인증 조건과 예외 처리

자동 인증(**전부 만족**, prefix 단독·닉네임 단독 금지):
1. 입력 닉네임(NFC·공백 정규화, 대소문자 무시)이 roster와 **정확히 일치**하고 후보가 **정확히 1명**
2. OAuth 이메일이 `@naver.com`이고 로컬파트(= 네이버 로그인 ID)가 해당 roster `naver_id_prefix`로 시작. **접두부는 회원에게 보여주지 않음.** 접두부가 `min-auto-prefix-length`(기본 **4**) 미만이면 자동 인증하지 않고 예외(`SHORT_PREFIX`) — 접두부 2자 회원이 **69명**(4자 434명)이라 이들은 예외로 간다. ⚠ 사용자 요구의 "접두부 일치"에 더한 **안전 강화**이므로 사용자 확인 필요(환경변수 `NEWSTART_THANKS_MIN_AUTO_PREFIX`로 조정).
3. roster가 다른 계정과 미연결, 현재 OAuth 계정도 다른 roster와 미연결
→ 한 트랜잭션에서 `roster.link` + `NewStartGrantService.grant(grantedBy="thanks-self")`. DB UNIQUE(`linked_user_id`, `linked_provider_user_id`)가 1:1 최후 방어선.

예외(자동 처리 불가) → ADMIN 예외함 + 이메일: `EMAIL_MISSING`, `EMAIL_NOT_NAVER`, `SHORT_PREFIX`, `AMBIGUOUS_CANDIDATES`(후보 2+), `ROSTER_ALREADY_LINKED`, `DUPLICATE_OAUTH_LINK`(계정 중복 연결 시도), `REPEATED_FAILURE`(24시간 단순 실패 3회), `CONCURRENT_CONFLICT`(UNIQUE 경합 → controller 가 `recordConflict`로 REQUIRES_NEW 기록).
- 단순 불일치(닉네임 없음/이메일 접두부 불일치)는 `FAILED`로 기록만 하고 **어느 항목이 틀렸는지 알려주지 않는 일반 문구** 반환.
- 같은 회원에게 처리 대기 예외(OPEN)가 있으면 새 시도·중복 알림을 만들지 않는다.
- 운영자 처리: `resolve`(연결 승인 — 같은 불변식 + 네이버 subject 재확인, grantedBy=`thanks-exception:<운영자>`, 후보 미특정 예외는 body.rosterId 로 선택), `dismiss`(종료). DB UNIQUE 경합은 409.
- 메일: 닉네임·사유·발생시각(KST)·관리자 링크만. 이메일 주소/네이버 ID/접두부/OAuth 식별값/비밀값 없음, 입력 닉네임은 제어문자 제거·50자 제한. 커밋 직후 비동기 발송, 실패/미설정이어도 인증 흐름 영향 없음(`email_status`=SENT/FAILED/NOT_CONFIGURED).
- **SMTP 설정은 운영 환경변수로만**(코드·Repository 에 비밀값 없음): `NEWSTART_SMTP_HOST`, `NEWSTART_SMTP_PORT`(587), `NEWSTART_SMTP_USER`, `NEWSTART_SMTP_PASSWORD`, `NEWSTART_ALERT_FROM`, `NEWSTART_THANKS_ALERT_TO`(기본 `woorinaesin@naver.com`, 운영자가 지정한 수신 주소), `NEWSTART_THANKS_ADMIN_URL`. **현재 운영에 SMTP 미등록 → 등록 전에는 예외함 적재 + 로그만, 메일은 발송되지 않는다**(사용자가 네이버 SMTP 앱 비밀번호 또는 다른 SMTP 를 EC2 `/etc/f2r/env` 에 등록해야 함).

## 5. ADMIN 전체 기능 bypass 상태

- 기존 `ROLE_ADMIN` 그대로 최상위 우회(새 계정/권한 없음). `NewStartEntitlementService.allows`가 ADMIN 이면 grant 조회 없이 통과하고, 감사회원 전용 지점 4곳(지피지기 `/chat`·`/chat/official`, 민원 컨트롤러, FSS Agent 컨트롤러)이 모두 이 서비스를 거친다 — 현재 코드로 확인(`grep`).
- 인증 절차 면제: `status`/`verify`가 ADMIN 이면 `state=ADMIN` 반환 + **어떤 기록·roster 연결·grant·알림도 만들지 않음**, 회원 화면은 ADMIN 에게 인증 섹션을 숨김(JS 계약 테스트로 고정).
- 잠금 테스트 `NewStartEntitlementAdminBypassTest`: **모든 `NewStartCapability` 값 순회**로 ADMIN 통과·일반회원 거절 → 향후 추가되는 capability 도 자동 보호.

## 6. 완료된 테스트 (정확한 결과)

로컬 `gradlew test`(깨끗한 인메모리/mock 테스트) — 모두 PASS:
- `NewStartThanksVerificationServiceTest` **18/18**
- `ThanksExceptionNotifierTest` **6/6**
- `NewStartThanksVerificationControllerTest` **7/7**
- `NewStartEntitlementAdminBypassTest` **2/2**
- `SocialAccountProviderEmailTest` **2/2**
- `NaverOAuth2LoginSuccessHandlerTest` **4/4**
→ 위 6개 클래스 **39건 PASS**. 같은 실행 묶음에서 기존 `NewStartEntitlementServiceTest` 4/4, `NewStartGrantServiceTest` 6/6 도 PASS(합 49).
- JS: `node --test src/test/js/newstart_thanks_verify_contract.test.js` **6/6 PASS**(회원 화면: 닉네임 입력 1개·ADMIN 숨김·APPROVED/FAILED/EXCEPTION·innerHTML 미사용).
- (이번 세션 이전 PR 단계에서 확인: 구 설계 기준 실 MySQL 마이그레이션·E2E — **구 설계 것이라 재설계 코드에는 유효하지 않음**)

## 7. 아직 완료하지 못한 것 (PASS 아님)

- **`NaverAccountResolutionServiceTest`(`@SpringBootTest`, 5건) — 실패/미확인.**
  - 공용 로컬 DB `f2r_db` 로 실행했을 때 **5건 모두 FAIL**: `FlywayValidateException: Validate failed: Migrations have failed validation`(컨텍스트 로드 단계 실패, 테스트 로직 실패 아님). 원인은 아래 8번(로컬 DB 이력 불일치) — 이번 변경의 버그로 단정하지 못했지만 **이번 변경이 원인이 아니라고 증명하지도 못함**.
  - 깨끗한 임시 DB(`f2r_qa_thanks2`)로 재실행을 시도했으나 **결과를 얻기 전에 중단**(작업 종료 지시). 테스트 XML 에 남은 것은 이전 f2r_db 실행 결과라 신뢰하면 안 된다 → **현재 상태 = 미확인**.
  - 이 테스트는 `provider_email` 매핑(Hibernate)과 V1017 마이그레이션, 로그인 시 이메일 갱신(`refreshProviderEmail`)을 실제 DB 로 검증할 수 있는 유일한 기존 테스트라 **반드시 깨끗한 DB로 확인**해야 한다.
- **재설계된 전체 앱 부팅 + E2E 미실행**(신규 엔티티 `NewStartThanksVerification` Hibernate 매핑, V1017 전체 적용, `provider_email` 매핑, 메일 의존성이 부팅을 깨지 않는지). 구 설계 E2E 스크립트(`e2e_thanks.py`, 저장소에 없음)는 구 API 용이라 폐기.
- **실 MySQL 8.4 에서 새 V1017 적용 검증 미실행**(구 V1017 은 검증했지만 재작성됨).
- 메일 실제 발송 검증 없음(단위테스트는 본문·미설정·실패 기록만 확인, SMTP 미등록).
- `bootJar`/전체 `compileJava` 는 위 테스트 실행 중 컴파일되어 성공했으나 CI 의 `./gradlew build` 전체는 미실행.

## 8. 공용 로컬 `f2r_db` Flyway 이력 불일치

- 위치: 로컬 Docker `f2r_mysql_main`(localhost:3308) 의 DB `f2r_db`. 여러 worktree/브랜치가 공유한다(`run-local.sh` 가 이 DB 를 재사용).
- 관측(이번 날 bootRun 시도 로그): `Detected applied migration not resolved locally: 1001`, `Migration checksum mismatch for migration version 1002 (Applied 271285456 / Resolved -2068983789)`. 즉 다른 브랜치의 V1001/V1002 가 적용돼 현재 소스와 맞지 않는다. 테스트(`@SpringBootTest`)의 `FlywayValidateException` 도 같은 DB 라 같은 원인으로 **추정**(테스트 XML 에는 세부 사유가 남지 않았음).
- 대응(이번 세션): **`f2r_db` 는 건드리지 않았다**(repair/삭제 금지). 대신 같은 MySQL 컨테이너에 임시 DB(`f2r_qa_*`)를 만들어 `SPRING_DATASOURCE_URL`/`USERNAME=root`/`PASSWORD=f2r_root_password` 환경변수로 앱/테스트를 띄웠고, 임시 DB 는 이어받을 필요 없다. **학원 PC는 이 DB 가 아닌 새 환경**이므로 이 문제가 없을 가능성이 높다.

## 9. 학원 PC 에서 깨끗한 환경으로 가장 먼저 실행할 것

1. `git fetch origin && git checkout feat/newstart-thanks-member-self-verify && git pull` → HEAD 확인, `git diff origin/main --stat` 로 main 이 앞서 갔는지·V1017 번호 충돌(`ls src/main/resources/db/migration | sort -V | tail`) 확인.
2. **깨끗한 MySQL 8.4 DB** 로 가장 먼저 `NaverAccountResolutionServiceTest` 5건:
   ```
   # 새 DB 생성 후 (예: docker exec <mysql> mysql -uroot -p... -e "CREATE DATABASE f2r_ns_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
   SPRING_DATASOURCE_URL=jdbc:mysql://<host>:<port>/f2r_ns_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci \
   SPRING_DATASOURCE_USERNAME=<user> SPRING_DATASOURCE_PASSWORD=<pw> \
   ./gradlew test --tests '*NaverAccountResolutionServiceTest'
   ```
   (이 테스트가 V1001~V1017 전체를 새 DB 에 적용하므로 새 V1017 문법·`provider_email` Hibernate 매핑·로그인 시 이메일 갱신까지 같이 검증된다.)
3. 통과하면 같은 방식의 새 DB 로 앱을 띄워(`./gradlew bootRun`) 신규 엔티티 부팅 확인 후 E2E(수동 또는 스크립트 재작성): ① 일반회원 A(네이버 계정 + `provider_email='<prefix>…@naver.com'`) 가 roster 닉네임 입력 → 자동 APPROVED + grant + roster 연결, 운영자 알림 없음 ② 닉네임만 맞고 이메일 접두부 다름 → FAILED(부여 없음) ③ 이메일 없음 → EXCEPTION + 예외함 적재(+`email_status=NOT_CONFIGURED`) ④ 이미 연결된 roster → EXCEPTION ⑤ ADMIN → state=ADMIN 이며 기록 0 ⑥ 예외 resolve 후 감사회원 게이트 통과.
4. PR #463 본문을 재설계 내용(위 2~5)으로 갱신, `./gradlew build`(또는 CI) 실행.
5. **머지·배포는 사용자 승인 후에만.**

## 10. CI 상태

- PR #463 의 마지막 CI(Build Check)는 **구 설계 커밋 `5bae58c8` 에서 SUCCESS** 였다. **재설계 커밋 `899c104e` 이후 CI 는 실행/확인되지 않았다** — push 로 자동 실행됐을 수 있으니 `gh run list --branch feat/newstart-thanks-member-self-verify` 로 확인하고, 실패하면 로그부터 확인한다(예: 메일 의존성 해석, `NaverAccountResolutionServiceTest` 의 DB, 컴파일).

## 11. 사용자 결정/주의 사항

- `min-auto-prefix-length=4`(접두부 2자 69명이 예외로 감) — 사용자 확인 필요(요구서에는 없던 안전 강화).
- SMTP 비밀값 등록(운영 환경변수) — 사용자 몫. 수신 주소는 `woorinaesin@naver.com` 으로 사용자가 지정.
- 기존(이메일 동의 이전) 회원 재동의 유도(`auth_type=reprompt`)는 미구현 — 오픈 전이라 영향은 작지만 오픈 후 `EMAIL_MISSING` 예외가 많으면 검토.
- 운영 DB 에는 아무것도 적용되지 않았다(V1017 migrate 는 머지·배포 시). 503명 roster(V1015/V1016)는 변경하지 않았다.
- 다른 agent(FREEBUCK/OpenCode) 작업·프로세스는 건드리지 않았다. 이번 세션에서 만든 임시 diag 브랜치는 모두 삭제했다.
- 이 문서·HANDOFF 에 비밀값은 없다. (참고: 이전에 사용자가 대화에 노출한 운영자 계정 정보는 어디에도 기록하지 않았다 — 변경을 권장.)
