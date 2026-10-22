# Claude 독립 검수 긴급 수정 — 2026-10-22

구현은 Codex, 독립 판정은 Claude 담당이다. main에는 병합하지 않았다. 브랜치 ancestry는 main → #854 → #853 → #855이며 일반 merge로 전달한다. squash/rebase/force push를 사용하지 않는다. 운영 배포·DB 변경·MCP 활성화·금융기관 접속은 실행하지 않는다.

## P1 수집 배치 승인 경계

- `EasyImportService.confirm`은 서버 `EasyCollectionRun.importBatchId` 연결 및 예약 수집 출처(FILE_CSV/FINANCIAL_FILE/CARDSALES)를 확인해 일반 확정을 거부한다. 승인된 수집 배치라도 일반 확정은 허용하지 않는다.
- 일반 `selectAccount`, prepare/reclassify/discard도 동일 가드로 검토 배치를 변경하지 못한다. 일반 CSV·이지샵 등록은 기존 경로를 유지한다.
- 승인 전용 `confirmApprovedCollection`은 기존 쓰기 트랜잭션을 요구한다(MANDATORY). 실제 사용자 사업체 접근, 승인 행 잠금, APPROVED/COMMIT_FAILED 상태, 승인·실행·연결·배치 사업체 일치, 배치 출처/실행 ID 연결을 검증한다. 승인 서비스 외 HTTP 진입점이나 caller boolean bypass는 없다.
- FINANCIAL_FILE의 기존 서버 승인 메모 `financial-collector reviewed batch <SHA-256>`를 재사용한다. 승인 때와 확정 직전에 현재 DTO 해시를 재계산한다. 누락·오류는 승인 전 상태 유지, 승인 후 불일치는 장부 롤백·COMMIT_FAILED다. 정상 재시도는 중복 저장하지 않는다.
- 수집 원문의 PUBLISHING_CONFIRMED 같은 필드가 승인 범위 밖 사업체로 장부를 이동시킬 수 없다. 일반 기존 장부의 사업체 분류 동작은 유지한다.

### Claude 원본 재현 테스트

`origin/ccr-d295072e-waeyz7`의 `cf3c12a5`(최초 handoff `5bc0a3a`)에서 받았다. [원본 txt](../reviews/handoff/EasyCollectionConfirmBypassMySqlTest.java.txt)는 바이트 그대로 보존했다(SHA-256 `dc69d9fa582d56a3dc1267571282232a71b3f27d2b939238150a1f079c7deaad`).

정식 `EasyCollectionConfirmBypassIndependentMySqlTest`는 기존 Codex 테스트와 이름이 겹쳐 클래스명만 변경했다. 두 원본 시험 본문·준비 단언·예외 단언은 바꾸지 않았다. 실제 MySQL에서 2/2 PASS. 별도 Codex 경계 시험 5건과 금융 검토 해시 양성/변조 대조 2건도 PASS. #853/#855 Build Check의 MySQL job에 모두 편입했다.

### 추가 호출 경로 근거

- `EasyAgentService.applyAccount` → `EasyTutorImportService.selectAccount` → `requireOrdinaryBatch`: 수집 배치 403, 행 변경 전 거부.
- `EasyAgentService.applyConfirm` → `EasyTutorImportService.confirm` → 공통 `EasyImportService.confirm`: 수집 배치 403. AI action의 일반 승인은 수집 전용 승인으로 인정하지 않는다.
- `EasyAuditService.resolve`와 `resolveWorkspace`의 IMPORT_UNCONFIRMED 분기 → 같은 공통 confirm. 예외가 검토 종료 이전에 발생하고 기존 @Transactional 경계에서 롤백된다. 감사 화면의 일반 승인으로 수집을 확정할 수 없다.
- UnifiedAssistantService LEDGER_ACCOUNT의 기존 본인 생성 배치 가드도 유지한다. MCP의 모든 금융 확정 POST는 허용 목록 밖이다.

## P2 신규 객체 롤백 정리

`StudioService.upload`은 저장소에 **새 UUID 객체를 생성한 직후, SQL 전에** `TransactionalFileDeletion.onRollback`을 등록한다. checked 예외도 롤백한다. SHA 중복 반환은 hook 등록보다 앞이므로 기존 객체는 삭제 대상으로 들어가지 않는다. 명확한 STATUS_ROLLED_BACK에서만 새 키를 정리한다. 커밋·결과 불명에서는 보존하며 정리 실패는 경고하고 다른 hook 실행을 막지 않는다.

`StudioUploadRollbackMySqlTest`는 실제 LocalFileStorage와 MySQL trigger를 사용한다. 68컷 ZIP에서 첫 컷은 기존 승인 원본을 재사용하고 나머지는 각기 다른 신규 파일이다. 30번째 revision INSERT를 실제 SQLSTATE 45000으로 두 번 실패시킨다. 매번 DB 전체 스냅샷·원본/ZIP bytes·기존 revision·승인 포인터/이력이 동일하고 새 파일 누적은 0이다. trigger 제거 후 68컷 성공, 중복 재시도 거부 후에도 파일/이력 불변을 확인했다.

**한계:** 파일 저장소와 DB는 분산 트랜잭션이 아니다. 프로세스 강제 종료, saveRaw가 키를 반환하기 전 부분 저장, 저장소 삭제 실패, 트랜잭션 결과 불명에는 미참조 객체가 남을 수 있다. 결과 불명에서 기존 참조 파일을 추측 삭제하지 않는다. 별도 저장소 inventory/DB 대조와 운영자 검토가 필요하다. 자동 S3 대조·운영 정리는 이번에 실행하지 않는다.

## MCP 보완

- +82 휴대·지역전화, 구분자 없는 10~19자리 식별번호, 4자리 그룹 카드번호를 보완했다. JSON 숫자형 계좌/카드 필드도 마스킹하며 숫자형 금액·일반 ID는 유지한다. 자유 텍스트의 긴 숫자는 주문번호/타임스탬프와 구분이 어려워 과다 마스킹할 수 있고 완전한 PII 탐지가 아니다.
- 바이너리 원본은 서비스 scope + 기존 권한 + 별도 `originals:read`가 모두 필요하다. 원본 권한은 기본 해제, 전 역할에서 별도 경고 확인을 강제하고 SENSITIVE_ACK를 남긴다. 원본 byte/SHA는 바꾸지 않는다. 도구 인자로 동의를 대신할 수 없다. 권한 미선택 시 원본 응답 403, 선택해도 미허가 제작자는 403이다. 금융 원문·계좌·증빙은 여전히 허용하지 않고 사업체 월 합계 8필드만 제공한다.
- OAuth 화면에서 권한 설명이 비어 있던 템플릿 문제를 재현·수정했다. 렌더링 단위 시험과 PC/모바일의 모든 권한 설명 표시 검사를 추가했다.
- 운영 설정은 `forward-headers-strategy=framework`. MCP는 framework wrapper 적용 전 socket peer/XFF를 기존 CIDR resolver에 전달한다. 실제 ForwardedHeaderFilter를 통과한 7개 신뢰/비신뢰/위조/포트/중첩 체인을 시험했다. framework 모드에서 CIDR 미설정/오류는 MCP 503으로 차단한다. 실제 ALB 대역·보안그룹은 이번에 조회/변경하지 않았다.
- 운영 MCP는 기본 false 유지. 실제 ChatGPT·Claude 제품 연결은 **미검증**이며 SDK 두 클라이언트 시험과 구분한다. 운영 활성화는 별도 승인과 실제 연결 검증 이후다.

## 검증 증거

겹치는 시험이 있으므로 아래 숫자를 합산하지 않는다. [Java 결과](urgent-test-results.json).

| 검증 | 결과 |
|---|---|
| 일반 장부·승인 경계·기존 CSV/AI 장부 흐름 | 56 PASS |
| Studio 신규 객체 보상·파일 검증 | 9 PASS |
| MCP/native OAuth/read-only/격리/마스킹/Studio | 52 PASS |
| Claude 재현·검토 해시·수집 회귀·최종 프록시 경계 | 63 PASS |
| 원본 설명 누락 템플릿 재현 후 수정 | 1 PASS |
| Flyway 실제 MySQL 8.4 순차/신규/기존 검증 | 1 통합 시나리오 PASS |
| Chromium PC1440·모바일390 승인/복원·68컷·7종 파일 | [22 PASS](browser-results.json) |
| Chromium 동의 화면·기본 해제·경고 미확인 거부·권한 설명 | [8 PASS](consent-browser-results.json) |
| 공식 MCP SDK 2.1.1 독립 OAuth 2개·원본 SHA·쓰기 거부 | [PASS](protocol-results.json), 실제 제품 연결 아님 |

Flyway 전용 시험은 Spring의 기존 자동 repair 전략을 거치지 않고 직접 validate/migrate만 사용한다. V1093 → V1094 1건 → V1095 1건, 새 DB versioned 190건, 기존 DB 재적용 0건. V1094 체크섬 **-1031052192**, V1095 **-1470147673**, 파일 SHA는 작업 전과 같다. 기존 main의 `FlywayRepairConfig` 자동 repair 코드는 변경하지 않았고 일반 앱 기동 로그에는 해당 호출이 있다. 전용 시험은 repair 없이도 체크섬/순차 업그레이드가 통과함을 별도로 증명한다.

CI는 [#854 checks](https://github.com/woori73/feel2real-buildit/pull/854/checks), [#853 checks](https://github.com/woori73/feel2real-buildit/pull/853/checks), [#855 checks](https://github.com/woori73/feel2real-buildit/pull/855/checks)의 최신 HEAD를 기준으로 제출 시 확인한다. 운영 배포 job은 실행하지 않는다.

화면: [스튜디오 PC](screens/studio-pc-1440.png), [모바일 승인](screens/approve-mobile-390.png), [모바일 복원](screens/restore-mobile-390.png), [원본 동의 PC](screens/consent-pc-1440.png), [원본 동의 모바일](screens/consent-mobile-390.png).

## Claude 재검수 요청

1. 원본 재현 2건 + 정식 경계/검토 해시 시험을 최신 #855에서 재실행하고 승인 우회 판정을 갱신한다. Agent/Audit 두 분기의 공통 confirm 도달 근거도 대조한다.
2. SQL 30번째 컷 실패/2회 재시도에서 기존 파일·revision·승인 이력과 새 객체만 정리되는지 확인한다. 강제 종료/삭제 실패/결과 불명 한계는 유지한다.
3. 새 원본 scope 동의 우회, JSON 숫자형·+82 마스킹, framework 전달 헤더 위조와 CIDR 미설정 503을 재검수한다. 실제 ALB 설정은 활성화 전 별도 확인 대상이다.
4. 최신 main의 #859를 보존한 순차 병합과 V1094/V1095 불변을 대조한다. 실제 ChatGPT·Claude 연결·실제 EP01 정본·운영 RDS 버전 확인은 미검증이며 운영 배포/활성화의 완료로 보고하지 않는다.

이 문서는 재검수용 전달물이다. Claude 또는 다른 사람에게 별도 메시지를 발송하지 않았다.
