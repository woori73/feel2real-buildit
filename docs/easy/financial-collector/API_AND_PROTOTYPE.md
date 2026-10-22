# 작업 B — 독립 수집 API와 샘플 프로토타입

이 기능은 기본 OFF이며 `local & !prd` 프로필에서만 생성된다. `local,prd` 조합에서도 생성되지 않는다. 운영 설정·DB·마이그레이션을 변경하지 않는다. 기관 지원 판정은 [조사 보고서](INSTITUTION_SUPPORT_2026-10-22.md)를 따른다.

## 실행과 샘플

JDK 21, Node 24, 로컬 EASY 개발 환경을 사용한다. 기존 로컬 DB를 실행할 경우 저장소의 `scripts/run-local.sh`를 따른다. 아래 설정은 **별도의 로컬 개발 환경**에만 전달한다. 실기관 계정 또는 금융기관 인증정보는 필요하지 않다.

| 설정 | 의미 |
|---|---|
| `APP_EASY_FINANCIALCOLLECTOR_ENABLED=true` | Spring relaxed binding으로 `app.easy.financial-collector.enabled=true`. 확실한 명시 설정은 실행 인자 `--app.easy.financial-collector.enabled=true`를 사용한다. |
| `--app.easy.financial-collector.vault-dir=/absolute/private/path` | 작업 트리 밖의 절대 경로, POSIX private 디렉터리. 저장소·static 디렉터리를 지정하지 않는다. |
| `--app.easy.financial-collector.key-base64=<키>` | 독립적으로 생성한 32바이트 난수의 Base64. 실행환경의 비밀 주입을 사용하고 커맨드 이력에 실제 키를 입력하지 않는다. JVM `-D`/명령 인자 대신 환경변수 `APP_EASY_FINANCIALCOLLECTOR_KEYBASE64`를 권장한다. |

원본·snapshot·감사 이벤트는 AES-256-GCM으로 저장한다. 키를 잃으면 복구할 수 없다. 개발자가 키를 읽어 로그·문서에 출력하거나 Git에 저장해서는 안 된다. 테스트는 임시 키와 임시 디렉터리를 만든다.

샘플은 [sample-bank.csv](sample-bank.csv), [sample-evidence.csv](sample-evidence.csv)이며 모든 행은 합성 자료다. XLS/XLSX/PDF 템플릿은 `EasyFinancialCollectorTest`가 메모리에서 생성한다.

## HTTP 계약

공통 prefix: `/api/admin/easy/financial-collector`. 기존 EASY의 로그인 쿠키·ROLE_ADMIN을 사용하며, 모든 파일/상태/반영 요청에서 활성 business 접근 권한을 확인한다. 파일명은 확장자 판정에만 쓰고 디스크 경로·로그에 보존하지 않는다. 기관 비밀번호·OTP·인증서·쿠키·URL·selector를 받는 필드는 없다.

| 메서드 / 경로 | 요청 | 결과 / 부작용 |
|---|---|---|
| `GET /capabilities` | 없음 | 기관·dataset·파일형식·postingRoute. 실기관 browserDownloadEnabled는 모두 false. API 존재 여부와 사용 승인은 구분한다. |
| `POST /previews` | multipart: `businessId`, `dataset`, `accountRef`, `from`, `to`, `file`, `userApprovedUpload=true`, 선택 `expectedCount`, `expectedTotal` | 201 + `Status`. 암호화 원본·초안·감사 이벤트만 생성. 파싱 불가는 201 + report.status=FAILED로 보존하고 보고한다. 미지원 확장자/잘못된 scope/크기 초과는 요청 거부. |
| `GET /previews/{collectionId}?businessId=...` | UUID + 사업체 | 재시작 후에도 암호화 snapshot과 기존 DB run/batch/approval을 읽어 현재 상태·실제 장부 저장 수를 반환한다. |
| `POST /previews/{collectionId}/stage?businessId=...` | JSON `{"reviewed":true}` | BANK_ACCOUNT만 기존 수집→raw→import staging으로 전달. 명시 검토 없으면 거부. archive UUID로 재시도 시 같은 committed run을 반환한다. 장부 저장 없음. |
| `POST /previews/{collectionId}/approve-and-commit?businessId=...` | JSON `{"userApprovedLedgerCommit":true,"reviewHash":"현재 상태 응답의 해시"}` | 기존 approval service를 호출한다. 검토 후 batch가 바뀌면 거부한다. 승인자 식별은 본문의 임의 이름이 아니라 인증 principal.userId. 반영 실패는 COMMIT_FAILED로 조회하고 같은 경로로 재시도한다. COMMITTED 재호출은 재반영하지 않는다. |

`accountRef`는 `sample-bank-a` 같은 지속적인 **불투명 참조**다. 실제 계좌번호·카드번호·비밀번호를 넣지 않는다. 같은 계좌·dataset에는 동일 참조를 사용한다. 기간은 ISO 날짜, 최대 1년이다.

`dataset`: `BANK_ACCOUNT`, `CARD_PURCHASE`, `CARD_SALES`, `CARD_SETTLEMENT`, `TAX_INVOICE`, `CASH_RECEIPT`.

`Status` 주요 필드:

```json
{
  "collectionId": "generated-uuid",
  "businessId": 1,
  "institution": "BANK",
  "dataset": "BANK_ACCOUNT",
  "postingRoute": "IMPORT_APPROVAL",
  "sha256": "original-sha256",
  "report": {
    "status": "SUCCESS|PARTIAL|FAILED",
    "fetched": 2, "valid": 2, "duplicateSuspects": 0,
    "failed": 0, "reviewRequired": 2, "total": 15000,
    "issues": [], "drafts": []
  },
  "run": null, "batch": null, "ledgerSaved": 0,
  "approvalId": null, "approvalStatus": null, "reviewHash": null,
  "nextAction": "REVIEW_THEN_STAGE"
}
```

실제 응답의 `drafts`는 행 번호·날짜·입출금·금액·거래처·적요·원문 계정·REVIEW 상태를 담는다. `run`에는 fetched/accepted/duplicate/error와 importBatchId, `batch`에는 기존 분류 제안·실제 행 상태가 들어간다. `ledgerSaved`는 `batch.confirmedRows`를 읽으며 수집 수로 추정하지 않는다.

`expectedCount`는 대상 파일의 데이터 행 수, `expectedTotal`은 유효 행 금액의 **입출금을 상계하지 않은 합계**다. 화면·명세서의 집계와 비교해 COUNT_MISMATCH/TOTAL_MISMATCH를 보고한다. 기간 밖 행·잘못된 날짜·해석 실패·외부 ID 충돌도 보고한다. 집계가 없으면 COMPLETENESS_UNVERIFIED / PARTIAL이며, 거래가 없는 날만으로 누락을 단정하지 않는다. PDF 전체 파싱 실패는 행 수를 알 수 없어 `fetched=0,failed=0`과 PARSER_REJECTED를 반환한다.

같은 날짜·방향·외부 ID의 내용이 다르면 EXTERNAL_ID_CONFLICT로 stage를 막는다. 원본을 자동 교체하지 않는다. tuple만 같으면 중복 의심으로 표시하고 파일 내 여러 출현을 raw 단계에서 보존한다. 오류 행의 dedupe 키는 해당 archive에 격리해 나중에 수정한 유효 행을 억제하지 않는다.

## 작업 A와의 경계

통합 AI의 자율 도구는 **capabilities·status 읽기**, 그리고 사용자가 파일을 선택하고 업로드를 승인한 UI 이벤트로 호출하는 preview 생성까지다. AI의 추정으로 reviewed/userApprovedLedgerCommit을 true로 만들지 않는다. stage/approve-and-commit은 사람의 검토 화면이 직접 호출하는 명령이며 자율 도구 allowlist에 넣지 않는다.

별도의 전체 사업체 API 키를 부여하지 않고 기존 EASY의 인증 principal과 business 경계를 따른다. 향후 TUTOR에 공개할 때는 URL 권한을 완화하는 것과 함께 기존 Academy→business 연결과 구현 권한을 검증해야 한다.

현재 새 API의 stage/commit은 BANK_ACCOUNT만 지원한다. 카드 구매는 카드 이벤트 검토, 매출은 economic sale, 입금은 settlement, 세무 증빙은 evidence match로 연결해야 하므로 이번 일반 반영 API에서는 거부한다. 이 전용 경로로의 자동 연결은 미구현이다.

## Playwright와 암호화 파일 연결

`local-collector`는 기존 outbound task/status API를 사용한다. `CARD_COMPANY_TEST`만 해석하고 loopback 밖은 거부한다. 일반 실행에서는 visible Chromium의 같은 page에서 사람이 로그인하고, 인증 후 TTY에서 기간을 확인해 `y`를 입력해야 수집을 시작한다.

`EASY_COLLECTOR_FILE_KEY_BASE64`는 별도 32바이트 키로 비밀 주입한다. 다운로드는 크기 상한을 두고 메모리에서 암호화해 UUID 이름 `.enc`, 600 권한으로 저장한다. Chromium 자체의 임시 다운로드는 finally에서 삭제하고 context도 닫는다. 완료 메타데이터의 size/hash는 원본 기준이며 암호문 기준이 아니다.

암호문을 새 preview API로 전달하는 선택적 로컬 브리지:

```bash
node local-collector/src/uploadPreview.js /absolute/uuid.enc csv 1 BANK_ACCOUNT sample-bank-a 2026-10-01 2026-10-31
```

`EASY_BASE_URL`은 명시 포트의 loopback이다. `EASY_PREVIEW_ACCESS_TOKEN`은 **EASY 앱 자체의 개발용 로그인 JWT**다. 금융기관 토큰을 넣지 않는다. 이를 파일에 보관하지 않으며, 대화형 업로드 승인 후 복호화한 내용을 메모리에서 multipart 전송한다. Cookie의 다른 호스트 전달을 막기 위해 redirect를 거부한다. 브리지는 stage/commit을 호출하지 않는다.

## 수정·삭제와 후속 구현

원본·감사는 증적이므로 덮어쓰기·임의 DELETE API를 추가하지 않았다. 수정 파일은 별도 archive로 업로드한다. 이번 범위는 범용 CRUD가 아닌 추기 방식의 수집·승인 명령이다.

운영화에는 기관별 계약·허가, 별도 실접속 승인, 실제 내보내기 파일을 익명화한 fixture, 전용 이벤트 경로 연결이 필요하다. DB archive 참조·outbox, 여러 프로세스의 일의성 제약, KMS 키 버전·교체, 보존기간·삭제와 감사의 양립, 격리 parser worker, WORM/감사로그 보전도 후속 범위다. 이번 로컬 파일시스템과 DB 처리는 분산 트랜잭션이 아니다. DB 저장 후 audit 쓰기가 실패해도 GET은 실제 DB를 읽고 archive ID로 같은 run에 복귀한다.
