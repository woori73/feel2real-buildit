# 작업 B — 보안 검증 및 격리 테스트 결과

검증일 2026-10-22. 모든 입력은 합성 CSV 또는 테스트에서 메모리로 생성한 XLS/XLSX/PDF다. 실기관 계정·실거래·운영 DB·운영 스토리지에는 연결하지 않았다.

## 실행 결과

| 검증 | 결과 / 근거 |
|---|---|
| `bash scripts/test-financial-collector.sh` | Java 98건, 실패 0, skip 0. 아래 범위를 테스트 XML로 집계했다. Node 4건 통과. |
| `bash gradlew build -x test` | 빌드·bootJar/assemble 성공. 테스트는 위의 별도 선택 실행 결과이며 전체 suite 통과를 뜻하지 않는다. |
| 파서·보안·승인 래퍼 | `EasyFinancialCollectorTest` 21건, `EasyFinancialCollectorSecurityTest` 3건. |
| 기존 수집 멱등성 | `EasyCollectionIngestIdempotencyTest` 12건. 같은 archive 재전송→같은 run, 다른 계좌 tuple 원문 보존, 승인 전 거래 0, 명시 confirm 후 거래 2와 재confirm 중복 없음 확인. |
| 기존 회계 회귀 | 승인 상태 전이 11, ADMIN API 권한 12, 장부 17, 분개 4, 신한 XLS 파서·정규화·import 18건. POI 5.5.1에서 통과. |
| `DISPLAY=:99 npm --prefix local-collector run test:e2e-mock` | 실제 headed Chromium + 임시 X 서버 + loopback mock 사이트/가짜 EASY backend. 암호화 원본 복원·SHA 검증 포함 PASS. |
| Node 단위·브리지 | 외부/credential 포함 URL 차단, 경로 탈출 이름의 무작위화, 파일 600 권한, 변조 거부, 빈/과대/금지 파일 거부, 업로드 승인 전 HTTP 0건, 메모리 복호화→로컬 multipart 계약 4건 PASS. |

Gradle test는 저장소의 DB 없는 map/repository harness, MVC 보안 컨텍스트, 단위 테스트만 실행했다. 실 MySQL 트랜잭션·락 경합을 검증하는 기존 P4/MySql 테스트와 전체 suite는 실행하지 않았다. 기존 앱의 전체 local 부팅도 수행하지 않았다. 이 환경에는 로컬 MySQL이 없으며, 운영 DB를 대체 테스트 대상으로 사용하지 않았다.

재현 환경은 JDK 21, Node 24, lockfile의 Playwright 1.63.0이다. 환경의 Java는 처음에 JRE만 있어 별도 작업 폴더에 JDK를 설치했고, Gradle의 HTTPS 프록시/CA를 설정했다. 저장소 실행 스크립트에 환경 전용 경로나 프록시를 기록하지 않았다.

## 합성 자료 결과

정규화 템플릿 한 행(`SAMPLE_SHOP`, 12,000원)은 CSV/XLS/XLSX/텍스트 PDF에서 모두 동일하게 해석됐다. 기대 1행·합계 12,000·10월 기간을 주면 SUCCESS, 수집 1 / 초안 1 / 오류 0 / REVIEW 1 / **장부 저장 0**이다.

기대 집계가 없는 파일은 PARTIAL/COMPLETENESS_UNVERIFIED다. 불가능한 날짜·기간 밖·합계/행 수 차이·변경된 외부 ID는 명시 이슈다. 수식 XLSX와 스캔 PDF는 추정하지 않고 거부한다. 전체 파싱 실패도 원본 암호화를 유지하며 FAILED를 반환한다.

map harness에서 2개 합성 행을 stage하면 수집 2 / 초안 2 / 신규 장부 저장 0이다. 테스트가 명시 confirm을 호출한 후 장부 저장 2가 되고, 재호출은 추가 거래를 만들지 않는다. 새 승인 래퍼는 변경된 reviewHash와 승인 false를 거부하고, 인증 principal의 ID로 기존 approval 서비스를 호출한다. 이미 COMMITTED이면 다시 반영하지 않는다. 이 래퍼 테스트는 기존 approval 서비스를 mock했으므로 실제 DB 원자성의 추가 증명이 아니다.

## 통제와 검증 범위

| 요구 | 구현 / 확인 |
|---|---|
| 실접속 승인 전 실행 금지 | 신규 API 기본 OFF, `local & !prd`만 생성. 기본 OFF/prd/local+prd 컨텍스트에서 API·vault 부재 확인. 공식 API 실호출 코드 없음. |
| 사용자 직접 인증·수집 동의 | headed 새 context의 완료 신호를 기다린 후 별도 TTY 동의. E2E에서 로그인 후 동의 거부→FAILED, 다운로드 없음. |
| 세션 무단 재사용 금지 | 작업마다 newContext, finally close. 두 번째 작업의 로그인 화면 확인. storageState/persistent context/cookie 읽기·전송 기능 없음. |
| 인증 우회 금지 | credential fill/인증서 접근/OTP 처리/stealth/CAPTCHA 해결 코드 없음. 실제 추가 인증 대응은 아직 실기관 어댑터가 없어 검증 대상이 아니다. |
| 외부 접속 제한 | mock origin 사전 검증 및 모든 페이지 요청의 origin 검사, service worker 차단, WebSocket 차단. 실제 브라우저의 외부 navigation이 abort되는 것 확인. |
| 원본 암호화 | 서버 vault: AES-256-GCM, 랜덤 nonce, business+UUID+object를 AAD로 결합, 원본/정규화/감사 모두 ciphertext. 파일·디렉터리 600/700. 워크트리 내 경로·symlink 거부. |
| 무결성·격리 | 암호문 변조·다른 business 파일명으로 복사 시 인증 실패, 원본 덮어쓰기 거부, 경로 탈출 ID 거부. business 권한 확인 전에 vault 읽기를 하지 않음. |
| 최소 권한 | 익명 401, TUTOR/STUDENT 403, ADMIN 허용 MVC 검증. 새 경로를 기존 collector token이나 통합 AI 전용 범용 토큰에 개방하지 않음. |
| 중복·오류 격리 | source에 dataset/account 범위 해시 포함, 승인번호에 일자/방향 포함, 충돌시 stage 거부, 잘못된 행은 archive별 키로 수정본 억제 방지. 기존 fingerprint 대사 한계는 별도 명시. |
| 승인 후 반영 | upload/stage는 confirm 호출 없음. 명시 승인+reviewHash 후 기존 approval 서비스 사용. 정산/증빙/카드 전용 경로는 일반 반영 거부. |
| 감사 | 업로드 승인, preview 결과, stage 승인/성공/실패, ledger 승인/결과를 인증 actor·run ID와 암호화 기록. 브라우저 다운로드는 기존 인증된 collector task의 상태·원본 hash/size·암호문 파일명으로 추적. 반영은 기존 approval의 상태·시각·실패 기록과 연결. |

## 남은 제한

- 이 결과는 로컬 프로토타입 검증이며 기관 이용약관 승낙·운영 보안 심사·침투 테스트 완료를 뜻하지 않는다. 실제 자료는 수집하지 않았다.
- 실제 기관별 네이티브 양식은 아직 검증하지 않았다. 신규 파서는 명시 템플릿만 지원한다. 카드·증빙·정산 전용 회계 경로 자동 연결도 후속이다.
- preview의 reviewHash는 요청 시 현재 batch를 대조한다. 기존 import API의 동시 수정까지 잠그는 버전/CAS는 구현하지 않았다. 단일 로컬 실행에서 사용하고 운영 전 배치 잠금·버전 검증을 추가해야 한다.
- filesystem/DB 원자성, 여러 JVM 동시 stage, audit 삭제 탐지/WORM, KMS 키 교체·보존기간 정책은 후속이다. 현재 GCM은 개별 파일 변조를 검출하지만 감사 파일의 삭제 자체를 증명하지 못한다.
- POI/PDF 파싱은 현재 앱 프로세스 안에서 수행한다. 크기·행/열·페이지 상한과 수식 거부를 적용했지만 압축문서의 CPU/메모리 공격을 완전히 격리하지 못한다. 운영 전 제한된 별도 프로세스가 필요하다. [Apache POI 보안 지침](https://poi.apache.org/security.html)도 문서 파싱의 자원 격리를 권고한다.
- 기존 POI 5.3.0에 OOXML을 그대로 추가하지 않고 [공식 안정 버전 5.5.1](https://poi.apache.org/download.cgi)로 맞췄다. 5.4.0 이전 OOXML 중복 ZIP 항목 문제는 [CVE-2025-31672](https://nvd.nist.gov/vuln/detail/CVE-2025-31672)에 기록돼 있다.
- 브라우저/servlet 자체 임시파일은 요청·context 수명 동안 평문일 수 있다. 애플리케이션의 장기 보관 파일은 암호화하며, 운영 시 OS 암호화 디스크·전용 private temp·retention 검증을 추가해야 한다.
- 본 작업 중 운영 배포, 운영 DB/스키마 변경, main 병합은 실행하지 않았다.
