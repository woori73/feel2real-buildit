# 코디 Android 시험 서비스

연산4의 비-main 배포 워크플로는 같은 커밋에서 빌드한 JAR와 이 디렉터리의 두 파일을 사용한다. EC2의 JVM은 Java 21이다.

| 파일 | 설치 위치 | 소유권·권한 |
| --- | --- | --- |
| SHA를 확인한 서버 JAR | `/opt/staging/app.jar` | root 소유, 서비스가 읽을 수 있게 설치 |
| `start-codi.py` | `/opt/staging/start-codi.py` | root 소유, 0644 |
| `buildit-staging.service` | `/etc/systemd/system/buildit-staging.service` | root 소유, 0644 |
| 스테이징 비밀 파일 | `/etc/f2r-codi-staging/runtime.env` | root 소유, 0600 |

기존 Publishing Staging의 setup·db-setup 단계는 서비스 및 환경 파일을 다시 쓴다. 코디 배포에서는 그 단계 대신 위 서비스 정의를 사용한다. `systemctl cat buildit-staging.service`로 기존 drop-in까지 확인한다. 서비스에는 운영 `/etc/f2r/env`와 Publishing용 `/etc/f2r/staging.env`를 연결하지 않는다.

## 런타임 설정

`runtime.env`에 다음 값만 넣는다. 비밀번호와 JWT 키는 비밀 채널에서 생성·관리한다.

| 변수 | 값 |
| --- | --- |
| `CODI_DB_HOST` | 시험 DB 호스트 |
| `CODI_DB_PORT` | MySQL 포트. 생략하면 3306 |
| `CODI_DB_PASSWORD` | 전용 DB 사용자 `codi_staging`의 비밀번호 |
| `CODI_JWT_SECRET` | 운영과 별도로 생성한 무작위 서명 키. 최소 32바이트 |

런처는 위 값만 JVM에 전달하고 JAR 내부의 `config/application-codi-staging.yaml`을 단독으로 로드한다. DB 이름은 `feel2real_staging`으로 고정된다. 전용 사용자는 이 스키마에만 마이그레이션·앱 실행 권한을 갖도록 구성한다. 실제 DB에서 `SHOW GRANTS FOR CURRENT_USER`와 운영 스키마에 대한 `USE` 거부를 확인한다. 전체 서버 권한과 운영 DB 계정은 사용하지 않는다.

Flyway는 `db/migration`만 적용한다. 이 경로에도 V2·V910 등 초기 로그인 계정을 만드는 마이그레이션이 있다. 외부 접근을 열기 전에 스테이징의 초기 계정을 점검하고 기본 비밀번호를 무효화한 뒤 가명 시험 계정을 제공한다. 운영 DB 백업이나 운영 학생 자료를 가져오지 않는다.

## 호스트와 연결

- 타깃 포트는 8081, 헬스 체크는 `/actuator/health`다. 전용 HTTPS 호스트의 루트에서 JAR에 포함된 코디 화면·정적 파일·API를 함께 제공한다. 앱 진입점은 `/codi`다.
- 8081 인바운드는 ALB 보안그룹으로 제한한다. 앱은 이 경로의 전달 헤더를 이용해 HTTPS 로그인 복귀 주소를 만든다. 스테이징 JWT 쿠키는 `STAGING_ACCESS_TOKEN`이고 Secure·HttpOnly·호스트 전용이다.
- 서비스는 자체 상태 디렉터리와 임시 디렉터리를 사용한다. 주소 필터의 기본값은 `IPAddressDeny=any`다. 호스트별 systemd drop-in의 `[Service]`에서 ALB 노드가 사용하는 서브넷, 시험 DB 주소 및 필요한 VPC DNS 리졸버 주소만 `IPAddressAllow=`로 추가한다. 이 설정은 송수신 양쪽에 적용된다. 공유 서브넷에서는 타깃 보안그룹의 ALB 소스 제한도 필요하다. EC2의 systemd/cgroup이 해당 필터를 지원하는지 서비스 로그에서 확인한다.
- `codi-staging` 프로필에서는 예약 작업을 등록하지 않는다. Claude·결제·MCP·출판 워커는 꺼진다. 공유 AcademyCraft의 8082 포트에는 게이트웨이 비밀을 제공하지 않아 API가 503으로 응답한다. 전용 연동을 구성한 뒤 실제 Claude 검색 및 AcademyCraft 의존 업무를 검수한다.

개통 검수 시 `https://codi-staging.feel2real.co.kr/codi`에서 로그인, 새싹 마스코트, 학원 업무·일반 대화 탭, 상단 메뉴의 학원관리 복귀를 확인한다. 로그인 직후 쿠키와 API Origin이 스테이징 호스트 범위인지 확인한다.
