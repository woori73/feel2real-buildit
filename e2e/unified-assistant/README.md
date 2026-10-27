# 통합 비서 로컬 E2E

금융기관 접속·자료 수집 테스트가 아니다. 테스트는 `http://localhost:8080`, localhost:8082의 실제 AcademyCraft와 고정된 로컬 `unified-ai-mysql` 컨테이너만 사용한다. 테스트 안의 만료·권한회수·중단 상태 검사는 테스트 스키마를 직접 변경한다.

## 재현

1. JDK 21, Docker, Maven, Node, Chromium을 준비한다. BuildIt은 이 브랜치, AcademyCraft는 `woori73/academycraft`의 `115477f7ea0afa27dd69d9ae12318261c47a5a82`를 별도 폴더에 체크아웃한다.
2. 전용 MySQL을 만든다. 이미 다른 개발 DB가 3308을 사용한다면 그 DB를 교체하지 말고 별도 검증 환경을 사용한다.

```sh
docker run -d --name unified-ai-mysql -e MYSQL_ROOT_PASSWORD=unified-local-only -e MYSQL_DATABASE=f2r_unified_test -p 127.0.0.1:3308:3306 mysql:8.4
docker exec unified-ai-mysql mysql -uroot -punified-local-only -e 'CREATE DATABASE ac_unified_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
```

3. BuildIt 루트에서 실행한다. local Flyway와 dev-seed를 적용하고 `ddl-auto=validate`로 기동한다. 비밀번호와 게이트웨이 비밀값은 아래 **로컬 전용 상수**다.

```sh
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3308/f2r_unified_test?allowPublicKeyRetrieval=true&connectionCollation=utf8mb4_unicode_ci' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=unified-local-only \
ACADEMYCRAFT_GATEWAY_SECRET=unified-local-only-gateway-secret-32bytes \
bash gradlew bootRun --args='--spring.jpa.show-sql=false --app.security.admin-session-binding.enabled=false'
```

4. AcademyCraft **루트 pom.xml**에서 `mvn -DskipTests package`를 실행한다. 아래로 jar를 실행한다. MySQL V1~V6 마이그레이션과 Hibernate validate를 사용한다. 정적 화면을 검증하려면 마지막 옵션에 해당 checkout의 frontend 절대 경로를 쓴다.

```sh
ACADEMYCRAFT_GATEWAY_SECRET=unified-local-only-gateway-secret-32bytes \
ACADEMYCRAFT_BUILDIT_BINDINGS='[{"userId":5,"role":"ADMIN","builditAcademyId":3,"academyId":7,"memberId":101},{"userId":7,"role":"TUTOR","builditAcademyId":3,"academyId":7,"memberId":102},{"userId":3,"role":"TUTOR","builditAcademyId":1,"academyId":8,"memberId":103}]' \
java -jar target/academycraft-1.0.0.jar --server.port=8082 \
--spring.datasource.url='jdbc:mysql://127.0.0.1:3308/ac_unified_test?allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
--spring.datasource.username=root --spring.datasource.password=unified-local-only \
--spring.jpa.hibernate.ddl-auto=validate --spring.flyway.enabled=true \
--spring.web.resources.static-locations=file:/absolute/path/to/academycraft/frontend/
```

5. 두 앱이 기동된 후 BuildIt 루트에서 fixtures를 적용한다. BuildIt dev-seed의 사용자 3=`test-tutor`, 5=`admin`, 7=`tutor`와 학원 1/3이 있는 새 테스트 DB를 전제로 한다. 운영 DB에 사용하지 않는다.

```sh
docker exec -i unified-ai-mysql mysql --default-character-set=utf8mb4 -uroot -punified-local-only < e2e/unified-assistant/fixtures.sql
cd e2e/unified-assistant
npm ci
npm test
```

`CHROMIUM_PATH=/path/to/chromium npm test`로 브라우저 경로를 지정할 수 있다. 기본은 `/usr/bin/chromium`이다. 테스트 계정 비밀번호는 `admin1234`다. 테스트는 사용자 5/7의 안내 진행을 초기화하고 이전 대기 초안을 만료시키며 실제 테스트 학생·반·출결·상담·거래를 추가한다. 재실행 시 작업 이름에 고유 접미사를 붙인다.

결과 JSON과 실패 trace는 로컬 ignored 파일이다. 제출할 캡처는 `docs/unified-ai-assistant/screenshots/`에 생성한다. 저장소에 로그인 쿠키·브라우저 storageState·실제 파일은 저장하지 않는다.

추가 검증: 같은 PC 대화창에 학생 명단 CSV와 혼합 거래 CSV를 연달아 올려 승인 전 불변·승인 후 재조회·행별 실패를 검사한다. 모바일은 자연어 학생 등록을 승인하고 결과 카드가 뷰포트에 보이는지 검사한다. `model-analysis-enabled`는 local에서 true지만 자격증명 없이는 명시적으로 표시된 규칙 분석을 사용한다. 실제 모델 응답을 E2E로 검증한 것은 아니다.

## 2026-10-27 모바일 회귀

기존 `unified.spec.js` 12개에 `home-responsive.spec.js` 8개를 추가했다. 360×800·390×844·430×932·1440×1050 각각에서 글꼴 대체 상태와 실제 Pretendard Variable v1.3.9 로드 상태를 검사한다. 모든 외부 문서·통계 POST는 차단하며 카톡 창은 생성만 확인하고 외부 페이지 로드 전에 막는다. 이메일은 href만 확인하며 전송하지 않는다.

테스트 앱은 `--server.address=127.0.0.1 --buildit.unified-assistant.model-analysis-enabled=false --anthropic.enabled=false`로 실행한다. DB는 3308의 기존 개발 DB를 대신 쓰지 않고, 위 절차의 신규 `unified-ai-mysql`을 `127.0.0.1:13308`에 바인딩해 두 앱 URL도 13308로 맞춘다. 테스트는 고정 컨테이너·전용 스키마의 합성 자료만 변경한다.

```sh
curl -fLSs https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/packages/pretendard/dist/web/variable/woff2/PretendardVariable.woff2 -o /tmp/PretendardVariable.woff2
HOME_FONT_PATH=/tmp/PretendardVariable.woff2 \
UNIFIED_EVIDENCE_DIR=/absolute/path/to/new-evidence/assistant \
CHROMIUM_PATH=/path/to/playwright/chromium npm test
```

`MOBILE_EVIDENCE_PHASE=before|after`는 홈 캡처·좌표·메뉴 응답 JSON의 저장 경로만 정한다. `before`에서도 같은 검증 조건을 사용하므로 수정 전 결함은 실패로 남는다. 기존 증거 이미지를 덮어쓰지 않으려면 `UNIFIED_EVIDENCE_DIR`를 새 폴더로 지정한다. 결과·로그·수정 전후 캡처와 실행 구분은 [검증 보고서](../../docs/verification/mobile-hologram-20261027/README.md)를 참조한다.

대시보드는 처음부터 `#uaTour`를 자동 표시하지 않는다. 테스트는 안내가 닫힌 것을 확인하고 `사용법 안내` 버튼으로 연 뒤 단계 저장을 확인한다. 새로고침은 팝업을 다시 열지 않으며 저장 단계는 API로 확인한다. 버튼으로 다시 열면 승인된 동작대로 1단계부터 시작한다. 자료 첨부는 `전체 대시보드`, 대화·승인은 `AI 대화`로 실제 메뉴 전환해 검증한다. 테스트를 삭제하거나 강제 DOM 조작으로 숨은 요소를 클릭하지 않는다.
