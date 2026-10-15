## 목표
<이 PR이 무엇을 / 왜 — 한 문단>

## 범위
- <변경 항목 1>
- <변경 항목 2>

## 검증
- <빌드/테스트/부팅 등 실제로 돌린 것 + 결과 — 없으면 "검증 안 함"과 사유>

## 향후 (별도)
- <이번 범위 밖, 후속으로 분리할 것>

## 관련
Closes #NN

---

## 개발 규칙 확인 (DEVELOPMENT_RULES.md)

- [ ] 작업 시작 전 `DEVELOPMENT_RULES.md`를 읽었다
- [ ] 담당 파일 범위만 수정했다 (Muse: Java/백엔드 · Qoder: HTML/JS/CSS · Claude: 지정 진단)
- [ ] 기존 운영 기능·API·DB를 무단 변경하지 않았다
- [ ] API 계약 변경 시 상대 담당자에게 먼저 알렸다

## 로컬 빌드 검증 (Java 변경 시, push 전 필수)

- [ ] `./gradlew compileJava compileTestJava --stacktrace --console=plain` 실행 및 PASS
- [ ] `./gradlew clean bootJar --stacktrace --console=plain` 실행 및 PASS
- [ ] 변경 범위 단위·시나리오 테스트 실행 및 PASS
- [ ] 로컬 환경이 없어 미검증인 경우 사유: <사유>

## CI

- [ ] Build Check run ID: <run ID> — 결과: <PASS/FAIL>
- [ ] CI 실패 시 로그 확보 후 원인 분석했다 (추측성 반복 실행 금지)

## 계약 정합성

- [ ] API DTO·필드명·HTTP 상태·오류 응답이 실제 코드와 계약서(API.md 등)에 일치한다
- [ ] DB 변경 시 마이그레이션·Entity·DOMAIN.md를 함께 수정했다

## 배포

- [ ] 운영 배포는 별도 승인을 받았다 (미승인 시 체크 금지)
