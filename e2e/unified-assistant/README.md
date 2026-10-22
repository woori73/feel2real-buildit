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
