# 작업 A 검증 결과

검증일: 2026-10-22 UTC. 운영 배포·운영 DB·main 병합·금융기관 접속 없음.

## 환경

- BuildIt: Spring Boot 4.0.6 / JDK 21 / localhost:8080.
- AcademyCraft: `115477f7ea0afa27dd69d9ae12318261c47a5a82` 실제 jar / localhost:8082. 소스 변경 없음.
- DB: 이 작업에서 생성한 `unified-ai-mysql` Docker MySQL 8.4.13, `127.0.0.1:3308`.
- 전용 스키마: `f2r_unified_test`와 `ac_unified_test`. 양쪽 Flyway 적용 + Hibernate `ddl-auto=validate` 기동 성공.
- BuildIt 기존 migration + 신규 V1094 + local dev-seed, AcademyCraft V1~V6 적용 성공.
- 테스트 신원: 로컬 ADMIN→학원7 원장, TUTOR→학원7 강사, 다른 TUTOR→학원8 원장, STUDENT. 장부 접근은 BuildIt 학원1/3과 테스트 사업체의 명시적 링크로 분리.
- 모바일은 Chromium 390×844, PC는 1440×1050. 실기기·실제 모바일 키보드는 미검증.

재현: [로컬 테스트 README](../../e2e/unified-assistant/README.md). 테스트 계정·게이트웨이 비밀값은 로컬 전용이며 운영 자격과 무관하다.

## 결과

| 검사 | 결과 |
|---|---|
| `compileJava`, `bootJar` | 통과 |
| 관련 JUnit 47건 | 실패 0. 아래 실행 필터와 항목 참고 |
| 실제 두 앱 + MySQL E2E 9개 시나리오 | 전부 통과, skip/flaky 0 |
| 공통 로그인 후 환영 링크 | 현재 `/home` 유지, 안내 링크 표시·닫기 통과 |
| PC/모바일 ADMIN·TUTOR | 화면 JS 오류 0, 가로 넘침 0, 안내 하이라이트·재개·UI 승인 통과 |
| 기존 JS 전체 검사 | 855건 중 815 통과 / 40 실패. 변경 전 main에서도 동일한 40개가 실패하며 신규 실패 없음 |
| `git diff --check` | 통과 |

관련 JUnit 실행:

```sh
bash gradlew test \
  --tests '*unifiedassistant.*' \
  --tests '*AcademyCraftGatewayTest' \
  --tests '*EasyAgentSecurityWebMvcTest' \
  --tests '*DashboardAssistantServiceTest' bootJar --max-workers=4
```

신규 테스트는 PR Build Check의 기존 무DB 테스트 목록에도 추가했다. E2E는 별도 AcademyCraft와 전용 DB를 요구하므로 로컬 검증 스위트로 제출한다. 전체 Java 테스트 스위트를 통과했다고 주장하지 않는다.

기존 JS 실패는 `node --test src/test/js/*.test.js`로 확인했다. 작업 전 기준 main의 `src/docs/scripts/tools/e2e`를 별도 임시 디렉터리에 추출해 같은 명령을 실행하고 실패 테스트 이름 집합을 대조했다. 855/815/40이 동일하고 신규 실패 집합은 비어 있었다. 학교 카드/기존 storefront·공개 화면/랜딩 문구·archived URL 등 이번 변경 밖의 기존 계약 실패를 수정하지 않았다.

## E2E 9개 시나리오

1. 익명 401, 학생 403, 다른 Origin 403, 강사 AI 학원 쓰기 403, 미연결 사업체 403, 타 학원 403.
2. 첫 방문 생성, 2단계 저장·재조회, 오래된 버전 409, 완료 저장, 다른 사용자 진행 격리.
3. 학생 초안 전후 학생 수 불변, 위조 해시 409, **동시 승인 6개 → 학생 1명**, 재승인 무중복, 타 사용자 404, 응답 인증키 제외, APPROVED 감사 이벤트 1건.
4. 반 생성·학생 배정·학생 정보 수정(학년/학교 보존), 수동 변경 후 초안 INVALIDATED, 새 반 수정 검증.
5. 출결 ABSENT 저장·재조회, 상담 스레드/첫 메시지 생성·후속 메시지 저장·재조회. 외부 알림 도착은 검사하지 않음.
6. DB 시각을 통한 만료, 취소 후 재승인 거부, 실제 AcademyCraft 원장→강사 변경 시 승인 거부, 중단 상태 EXECUTING 재승인 거부.
7. 증빙 INVOICE CSV 초안, 승인 전 거래 ID 없음, 승인 후 저장 1, 재승인 중복 없음, 같은 원본 재업로드 중복 1/신규 저장 0, **미분류 거래도 저장 1 + REVIEW_REQUIRED**, 미허용 사업체 업로드 거부, 학원/재무 통합 조회.
8. 실제 화면 자연어 → 폼 → 초안 → 승인 → 저장 검증 표시. 메뉴 하이라이트, 새로고침 후 단계 재개, ADMIN/TUTOR PC·모바일 화면 캡처와 가로 넘침/JS 오류 검사.
9. 공통 로그인 화면의 환영 안내가 현재 서비스를 유지하면서 대시보드로 연결되는지 검사.

최종 전체 실행은 9 passed / 0 unexpected / 0 skipped / 0 flaky였다. 테스트 재실행은 고유한 접미사의 별도 가상 거래·학원 업무 레코드를 사용한다. 실패 trace·쿠키·브라우저 storageState는 커밋하지 않는다.

## 검증에서 발견해 수정한 사항

- Spring Boot 4의 HTTP Jackson 3가 내부 Jackson 2 `JsonNode`를 직접 역직렬화할 수 없어, HTTP DTO와 응답을 일반 Map/기본형으로 변환했다. JSON 요청/응답 경계 테스트 추가.
- 메서드 보안 어노테이션만으로 학생 접근이 차단되지 않아, 새 경로의 HTTP 필터 권한을 명시했다. 기존 전역 메서드 보안 설정은 바꾸지 않았다.
- OSIV가 먼저 읽은 PENDING 객체를 보관해 행 잠금만으로 중복 실행을 막지 못한 것을 실제 동시 승인 테스트에서 발견했다. DB 조건부 선점과 회귀 단위 테스트로 보강한 뒤 동시 승인 재검증 성공.
- 늦은 초기 사업체/기록 조회가 새 초안을 지우는 UI 경쟁을 발견했다. 초기 선택 동기화와 화면 수정 버전을 추가했다. 생성·승인 UI E2E와 재실행 성공.
- 상담 API의 생성/메시지 응답이 envelope이므로 내부 실제 레코드 ID로 재조회하도록 수정했다.

## 스크린샷

모두 로컬 가상 데이터다. PC/모바일을 직접 확인했으며, 기존 장부 학생 수와 AcademyCraft 학생 수는 서로 다른 도메인 집계이므로 합산하지 않는다.

| 파일 | 내용 |
|---|---|
| [admin-desktop-draft-1440.png](screenshots/admin-desktop-draft-1440.png) | ADMIN 1440px: 안내·메뉴 강조·자연어 입력·승인 전 초안 |
| [admin-desktop-result-1440.png](screenshots/admin-desktop-result-1440.png) | ADMIN 1440px: API 실행·저장 재조회 결과 |
| [admin-390.png](screenshots/admin-390.png) | ADMIN 390px: 모바일 안내·통합 조회·업무 공간 |
| [tutor-1440.png](screenshots/tutor-1440.png) | TUTOR 1440px: 강사 연결 역할·조회 범위 |
| [tutor-390.png](screenshots/tutor-390.png) | TUTOR 390px: 모바일 역할별 화면 |

## 미검증/후속

Muse Spark 실제 응답(이 환경 자격증명 없음), 운영 화면·실제 계정·운영 스키마 적용, 실기기 키보드, 외부 SMS/메일 도착, 범용 LLM 의도 해석·영수증 OCR, 원격 API의 버전 기반 원자적 변경 검사는 미검증 또는 후속 범위다. 자연어는 규칙 기반이며 미지원 표현은 폼으로 보완한다. 금융기관 사이트 접속·Playwright 자료 수집은 작업 B 범위로 구현하지 않았다.
