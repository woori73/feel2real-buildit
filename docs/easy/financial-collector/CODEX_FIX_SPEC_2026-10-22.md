# PR #853 수정 기준 및 Codex 인계 (Claude 확정본)

기준 검수: [REVIEW_PR853_2026-10-22.md](REVIEW_PR853_2026-10-22.md) · 작성 2026-10-22
**PR #853 병합 보류.** 운영 배포·운영 DB 변경·main 병합 금지. 코드 수정은 Codex가 각 PR 브랜치에서 수행하고, Claude는 이 문서의 기준으로 재검수만 한다(Claude는 PR 브랜치 파일을 수정하지 않는다).

## 0. 확정된 결정 (사용자 결정, 변경 금지)

| 항목 | 결정 |
|---|---|
| 운영 정책 | 수집·파싱·분류·**임시 장부 자동 저장**은 자동. **확정 장부 반영은 사용자 승인 후에만**. |
| 승인 검증 | AI/클라이언트가 보낸 불리언만으로 승인 판단 금지. **서버가 검증하는 일회성 승인 토큰 + 재인증**. |
| POI | 5.5.1 + poi-ooxml **조건부 승인** — 회귀·의존성 보안·메모리 검증 통과가 조건. |

### 0-1. "임시 장부"의 정의 (Codex 착수 전 확인할 가정)
이 정책은 기존 설계(stage=사람 검토 후)를 바꾼다. Claude가 해석한 정의:
- **임시 장부 = 기존 import batch의 `STAGED`(미확정) 행.** `EasyTransaction`(확정 장부)·분개·재무제표·세무 집계에는 **들어가지 않는다.**
- 업로드/수집 완료 즉시 서버가 자동 stage(사람 `reviewed` 불필요). 단 `EXTERNAL_ID_CONFLICT`·파싱 전체 실패는 stage하지 않고 보고.
- 불완전 자료(§4-7)도 임시 저장은 허용하되 `INCOMPLETE` 표식을 붙이고 **확정 차단**.
- 별도 임시 테이블을 새로 만들지 않는다(재사용). 만약 기존 batch가 임시 저장 요구를 충족하지 못한다고 판단되면 구현 전에 Claude에 보고.
- 이 변경은 `MASTER_OPERATING_PRINCIPLES`(수집 즉시 저장)와 정합 방향이다. `docs/FLOWS.md`·`docs/API.md`를 같은 PR에서 갱신(문서 동기화 규칙).

## 1. 담당·파일 소유 (동시 수정 방지)

| PR 브랜치 | 소유 파일 | 비고 |
|---|---|---|
| #853 `feat/financial-data-collector` | `domain/easy/**` 금융 수집 클래스, `local-collector/**`, `build.gradle`(POI), 금융 docs | 보안 기반(§2~§6) 전부 여기 |
| #854 `feat/unified-ai-assistant` | `domain/unifiedassistant/**`, 어시스턴트 UI | §3·§8만. 금융 서비스 코드는 수정 금지, #853이 제공하는 facade만 호출 |
| #855 `feat/ai-agent-studio-unified-mcp` | `domain/mcp/**`, `mcp/read-policy.json` | §9만 |

- `SecurityConfig.java`는 #854·#855 모두 수정한다. **#854 먼저 병합 → #855가 rebase**로 충돌 해소(동시에 두 사람이 만지지 않는다).
- 한 PR의 작업이 다른 PR 파일을 요구하면 직접 수정하지 말고 인터페이스(메서드/DTO)만 합의 후 순차 진행.

## 2. 병합 순서 (Flyway 포함)

main 마지막 마이그레이션 = V1093. 현재 #854·#855가 모두 `V1094`를 가짐 → **중복**.

1. #854 → `V1094`(유지)
2. #855 → `V1095`로 재번호
3. #853 → 이번 수정이 DB 테이블(§5 감사, 승인 토큰 §3)을 추가하면 `V1096`
4. 기존 적용 V_파일 불변. `db/dev-seed`는 `R__`만. 병합 직전 Claude가 **그 시점의 main 최신 번호를 재확인**하고 번호를 확정한다(다른 PR이 먼저 들어오면 재번호).
5. 검증: 빈 MySQL에서 Flyway 전 구간 적용 + 번호 중복 검사 스크립트(파일명 `V<n>__` 유일성)를 CI에 추가.

## 3. 승인 토큰·재인증 (P1-1, 최우선) — #853 + #854

**요구**
- 확정 반영(`approve-and-commit`)은 요청 본문 불리언 불인정. 서버가 발급·검증한 **일회성 토큰** 필수.
- 토큰 발급 조건: ① 인증된 사용자 ② **최근 재인증**(비밀번호 재입력 또는 N분 이내 신규 로그인, 기본 5분) ③ 대상 `collectionId`+`businessId`+`reviewHash`(+완결성 이슈 확인 목록 해시) 바인딩.
- 토큰 속성: 128bit 이상 난수, DB에는 **해시만 저장**, TTL ≤ 5분, **단일 사용**(원자적 `UPDATE … WHERE status='ISSUED' AND expires_at>now()` 영향행 1일 때만 성공), 발급 사용자와 사용 사용자 동일, 사용 후 폐기. 실패/만료/재사용은 모두 감사 기록.
- 토큰은 AI 모델 컨텍스트·로그·MCP 응답에 절대 포함하지 않는다. 발급 응답은 `Cache-Control: no-store`.
- 재인증은 사람만 가진 비밀(비밀번호 등)을 입력해야 한다. #854 AI/실행기는 이 값을 알 수 없어야 한다(저장·전달 금지).
- `userApprovedUpload`·`reviewed`·`userApprovedLedgerCommit` 불리언 입력 필드 제거(또는 무시하고 토큰만 판단).

**#854 차단 (P1-1 ②)**
- #854 도구 allowlist/실행기에서 `/api/admin/easy/financial-collector/**` 중 **확정(approve-and-commit)·토큰 발급 경로는 무조건 deny**. 허용은 capabilities·status 조회, 사용자가 선택한 파일의 preview 업로드(§8)까지.
- 이 deny는 문자열 allowlist뿐 아니라 **서버 측에서도** 강제: 확정 API는 어시스턴트 실행기 경유 요청(내부 서명 마커/별도 principal)이면 토큰이 있어도 403.
- 자연어 "승인"은 계속 실행 불가(기존 유지).

**수용 테스트(given/when/then)**
- 토큰 없음/불리언만 → 403 · 만료 → 거부 · 재사용 → 거부 · 다른 사용자 → 거부 · 다른 collection/hash → 거부 · reviewHash 변경 후 → 거부
- 재인증 없이 발급 시도 → 거부
- #854 실행기가 확정 경로 호출 → 403, 토큰 포함해도 403
- 동시 2요청으로 같은 토큰 사용 → 정확히 1건만 성공(멀티스레드 테스트)
- 확정 전 `EasyTransaction` 증가 0건

## 4. #853 P1/P2 상세 수정 기준

### 4-1 사업체별 접근 권한 (P1-2)
- ADMIN=전 사업체 허용 폐기. `금융수집 접근 grant`(user ↔ business, 역할 READ/STAGE/COMMIT, 만료 가능)를 신설하거나 기존 `Academy↔Business` 링크를 재사용. 기본 deny.
- 모든 엔드포인트(조회 포함)에서 grant 검사, 역할 분리: COMMIT 권한자와 업로더 분리 가능(**업로더≠승인자 옵션**, 설정 가능).
- 모든 **조회(GET)도 감사 기록**(누가·언제·어느 collection).
- 테스트: grant 없는 ADMIN 403, 다른 사업체 id 403, 만료 grant 403, 조회 감사 생성.

### 4-2 암호화 키 버전·교체·폐기 (P1-3, "설계" 포함)
- 구조: **사업체별 DEK**(데이터 키) + **KEK(keyId)** 로 래핑. 파일 envelope에 `version | keyId | wrappedDek | nonce` 기록. AAD에 keyId 포함.
- 설정: 키링(`keyId → 키`) 다중 로드, `activeKeyId`로 신규 암호화. 복호화는 envelope의 keyId로.
- 교체(rotation): 신규 활성 키 지정 → 백그라운드 **재래핑**(DEK만 재암호화, 본문 재암호화 불필요) 명령. 중단 후 재개 가능.
- 폐기(revocation/destroy): 키 상태 `ACTIVE/DECRYPT_ONLY/DISABLED/DESTROYED`. DESTROYED DEK = 해당 자료 영구 복구 불가(= crypto-shredding, §4-3).
- KMS 연동은 인터페이스(`KeyProvider`)로 추상화, 구현은 로컬 키링 + 운영용 어댑터 자리(KMS 실연동은 별도 승인).
- 키·평문 로그 금지, 키 값을 응답/예외 메시지에 넣지 않음. Node 수집기(`local-collector`)도 같은 envelope 규약(keyId 포함) 사용.
- 테스트: 구키로 암호화 → 교체 후 복호화 성공, DECRYPT_ONLY 키로 신규 암호화 거부, DESTROYED 후 복호화 실패, 변조 envelope 거부, keyId 바꿔치기(AAD) 거부.

### 4-3 보존·파기 (P1-4)
- 정책(설정화): 원본 vault 보존기간(예: 기본 N개월, 세무 보존 요구는 **사용자/세무 담당 확정 필요 — 임의 값 확정 금지**), 임시 장부 자동 만료(미확정 N일 후 폐기 또는 알림).
- 파기 = ① 원본·스냅샷은 DEK 파기(crypto-shredding) + 파일 삭제 ② 감사 로그는 **삭제하지 않고** 본문 없이 이벤트 메타만 유지(파기 사실도 감사).
- 파기 실행은 grant 권한 + 재인증 + 감사. 법적 보존 필요(legal hold) 플래그 시 파기 거부.
- 확정 장부 반영 완료 건의 원본 증적 보존 규칙을 명시(삭제 금지 기간).
- 테스트: 만료 후 스케줄러 파기, legal hold 거부, 파기 후 복호화 불가·감사 유지, 파기 멱등.

### 4-4 감사로그 무결성 (P1-4)
- 파일 이벤트 → **DB append-only 테이블**(`UPDATE/DELETE` 권한 없음, 트리거로 방어) + 사업체별 **해시 체인**(`seq`, `prev_hash`, `hash = H(prev_hash || canonical(event))`).
- 주기적 앵커(최신 해시를 별도 위치에 기록 — 운영 시 WORM/외부 저장은 후속, 인터페이스만).
- 검증 API/명령: 체인 전수 검증, 누락(seq gap)·변조 탐지.
- 이벤트 종류: 업로드, 조회, stage, 토큰 발급/사용/실패, 확정, 키 교체, 파기, 권한 거부. 행 원문·파일명·세션·예외 본문은 기록 금지.
- 감사 기록 실패 시 해당 동작은 **실패(fail-closed)**. 확정 후 감사 기록 실패는 재시도 큐 + 알림(확정은 이미 커밋되므로 조정 가능한 상태로).
- 테스트: 중간 이벤트 변조/삭제 → 검증 실패, seq 중복 방지, 감사 실패 시 stage 중단.

### 4-5 파서 격리·업로드 크기 (P1-5)
- 파싱을 **별도 프로세스**(`ProcessBuilder`, 고정 `-Xmx`, 벽시계 타임아웃, 네트워크/파일시스템 접근 최소, stdin 바이트 → stdout 정규화 CSV)에서 실행. 실패/타임아웃/OOM은 앱 JVM에 영향 없이 `PARSER_REJECTED`.
- OS 수준 제한(가능한 범위): 비특권 사용자, `ulimit`/cgroup, 작업 임시 디렉터리 private. 불가한 환경은 문서에 명시하고 로컬 한정.
- POI ZipSecureFile 설정(최소 inflate ratio, 엔트리 최대 크기, 전체 압축해제 상한), XLSX 엔트리 수 상한, 외부 엔티티(XXE) 차단 확인, PDF 페이지·텍스트 상한 유지.
- **업로드 크기 조기 차단**: 전역 multipart 한도(105MB)는 영상 때문이므로 유지하되, 이 경로는 **필터에서 `Content-Length` > 상한(예: 11MB)이면 multipart 파싱 전에 413**. chunked 전송도 스트리밍 누적 상한으로 차단.
- 테스트: 압축폭탄 XLSX(작은 힙에서 격리 프로세스만 실패), 무한루프 문서 타임아웃, 11MB 초과 즉시 413(본문 미읽음), 정상 파일 결과 동일.

### 4-6 확정 차단: 불완전 자료 (P2-7, 요구 7)
- 임시 저장은 허용(§0-1). **확정은 아래 중 하나라도 해당하면 차단**: `COUNT_MISMATCH`, `TOTAL_MISMATCH`, `COMPLETENESS_UNVERIFIED`(기대 집계 없음), 실패 행 존재, `EXTERNAL_ID_CONFLICT`, 기간 밖 행, `DUPLICATE_SUSPECT` 미해결.
- 해소 경로: 사용자가 기대 건수/합계를 입력해 재검증 통과, 또는 문제 행을 제외한 **새 archive로 재업로드**. "무시하고 확정" 우회 불가(관리자도 불가).
- 확정 토큰(§3)은 **차단 사유가 0일 때만** 발급. 이슈 목록 해시를 토큰에 바인딩.
- 테스트: 사유별 확정 거부, 해소 후 확정 성공, 이슈 변경 후 구토큰 거부.

### 4-7 민감정보 마스킹 (요구 8)
- `externalAccount`(및 계좌/카드 번호 패턴)는 **저장 전 정규화·마스킹**(뒤 4자리만 유지), API 응답·로그·감사·MCP에는 마스킹본만. 원문이 필요한 경우 vault 내부 암호화 영역에만.
- 카드번호는 PAN 패턴(Luhn) 검출 시 전체 마스킹. 이름·연락처는 응답 최소화.
- 예외 메시지·`rawJson`에 원문이 남는지 점검(`rawJson`은 DB 평문 → 마스킹 후 저장).
- 테스트: 응답/DB/로그 어디에도 풀 계좌번호 문자열 없음(grep 방식 테스트), 마스킹 후에도 중복 판정 동일(해시는 원문 기준 키드 해시 사용 — 단순 SHA 금지, 키드 HMAC).

### 4-8 동시성·직무분리 (P2-9/10)
- `synchronized` 제거 → DB 락/유니크 제약. batch 버전(CAS)으로 hash 확인↔승인 사이 변경 차단.
- 감사에 업로더·검토자·승인자를 각각 기록. 직무분리 옵션 적용(§4-1).

## 5. CSV 입력 경로 일원화 (요구 9) — #853 facade + #854 어댑터

- #853이 **단일 진입 facade**(`FinancialIngestFacade.submit(principal, businessId, dataset, scope, bytes, source)`)를 제공. 모든 금융/거래 파일(CSV·XLS·XLSX·PDF)은 이 경로만 사용: vault 암호화 → 격리 파싱 → 마스킹 → 중복/완결성 → 임시 장부(stage).
- #854 `LEDGER_IMPORT`는 기존 importer 직접 호출을 **제거**하고 facade 호출로 교체(첨부 파일 ≤256KB 제한 유지). 자체 CSV 파서·fingerprint 경로 삭제 또는 facade 위임.
- 파일 sha256 + businessId + scope 기준 **멱등**: 같은 파일 재업로드 → 기존 collection 반환(이중 반영 방지).
- 기존 `EasyTutorImportService` 등 학원/튜터 직접 CSV 경로가 별도로 존재하면 목록화해 보고(삭제는 사용자 결정).
- 테스트: #854 경유 업로드와 #853 직접 업로드가 같은 파일에서 동일 collection·동일 중복키를 만든다 / 같은 파일 2회 → 장부 1회분 / #854 쪽에 독립 파서가 남지 않았음을 검증하는 아키텍처 테스트.

## 6. POI 조건부 승인 검증 (요구 12)

통과해야 병합 가능:
1. **회귀**: 기존 신한카드 XLS 파서·장부·분개 테스트 전체 + 신규 XLSX/XLS 골든 파일 비교.
2. **의존성 보안**: `poi`, `poi-ooxml`, 전이 의존성(xmlbeans, commons-compress, commons-io, log4j-api 등) 취약점 스캔(OWASP dependency-check 또는 osv-scanner) 결과 첨부. High/Critical 미해결 시 병합 불가.
3. **메모리**: 대용량(허용 상한 근처) XLSX/XLS를 `-Xmx` 제한 JVM에서 파싱 시 피크 힙 측정, 격리 프로세스 한도 내 동작. 압축폭탄 거부 확인.
4. **번들 영향**: bootJar 크기 증가량, 앱 기동 시간·메모리 기준선 비교(기능 OFF 상태). 클래스패스 충돌 없음.
5. 결과는 `docs/easy/financial-collector/POI_VALIDATION_<날짜>.md`에 수치로 기록.

## 7. #854 연동 계약 확정 (정합성)

| 기능 | 계약 |
|---|---|
| 수집 요청 | AI는 요청 **초안**만. 파일 선택·업로드는 사람 UI 이벤트 → facade |
| 진행 상태 | 단일 job 상태 enum: `UPLOADED→PARSED→STAGED(임시)→READY_TO_COMMIT\|BLOCKED(사유)→COMMITTED\|COMMIT_FAILED`. collector task id ↔ collectionId 매핑 필드 추가 |
| 완료 알림 | #854 진행 이벤트에 `COLLECTION_STAGED`, `COLLECTION_BLOCKED` 추가(폴링 기반 허용) |
| 분류 초안 | 임시 장부 행의 기존 분류 제안 + 신뢰도. AI 변경 제안은 기존 분류 변경 승인 경로 |
| 승인 요청 | #854 UI가 "확정 요청 카드" 표시 → 사용자가 재인증 후 토큰 발급 → **#853 확정 API 직접 호출은 사람 브라우저 세션에서만**. #854 실행기는 호출 불가(§3) |
| 결과 조회 | `ledgerSaved`(= confirmed rows)와 재조회 값(거래 ID·일자·금액·방향) |

권한: 금융 수집은 grant 기반(§4-1). TUTOR 개방은 이번 범위 아님(ADMIN grant 보유자만).
계약 변경은 `docs/API.md`·`docs/FLOWS.md`·`docs/DOMAIN.md`(테이블 추가 시) 동시 수정.

## 8. #855 MCP (요구 11)

- 초기 제공은 **권한 검증된 요약 조회만**, 신규 엔드포인트 2~3개:
  - `GET …/financial-collector/summary/jobs` — id, dataset, 기관 구분, 기간, 상태, 건수, 차단 사유 **코드**, 생성 시각
  - `GET …/financial-collector/summary/unposted` — 임시 장부 건수·금액 합계(방향별), 차단 건수
  - (선택) `…/summary/errors` — 오류 코드별 건수
- 응답 DTO에서 **금지**: 거래처, 적요, 계좌/카드번호(마스킹본 포함), 파일명, sha256 전체, 토큰, 원문 행, 세션/자격증명 관련 필드. 필드 allowlist 방식으로 직렬화(블랙리스트 금지).
- `read-policy.json`에는 위 summary 경로만 추가. `/financial-collector/previews/**`, 확정/토큰/파기 경로는 **명시 deny**하고, 정책 생성 스크립트(`build-inventory.py`)에도 deny 규칙 + CI 테스트(정책에 금지 경로가 있으면 실패).
- MCP 호출도 §4-1 grant 검사를 거치고 감사에 기록. 읽기 전용 트랜잭션 필터는 유지.
- 별도 점검 요청: 기존 정책의 `/api/admin/easy/transactions`·`/transactions/export`, `collections/runs`, `collector/tasks`의 응답 필드에 원문·토큰이 없는지 필드 단위로 확인 후 결과를 #855 보고서에 기재(현재 Claude도 미확인).

## 9. 구현 순서 (권장)

1. (#853) §4-1 grant + 조회 감사 → §4-4 감사 DB·체인(마이그레이션 V1096 후보)
2. (#853) §4-2 키 구조 → §4-7 마스킹 → §4-5 파서 격리·크기 필터
3. (#853) §0-1 자동 stage + §4-6 확정 차단 + §4-8 동시성
4. (#853) §3 토큰·재인증 → §4-3 보존·파기
5. (#853) §6 POI 검증, §5 facade
6. (#854) §5 어댑터 교체 + §3 deny + §7 계약 이벤트
7. §2 Flyway 재번호(#855→V1095) + 중복 검사 CI
8. (#855) §8 summary 정책 + deny
9. 전체 CI → Claude 재검수

## 10. 완료 조건 및 Claude 재검수 체크리스트

- [ ] P1-1~6 전부 해소 (각 항목의 수용 테스트 통과)
- [ ] 기존 테스트 유지(신한카드·장부·분개·승인·Academy 회귀) + 신규 보안 테스트 통과
- [ ] CI: build / mysql-integration-test / 금융 수집 격리 job / MCP·어시스턴트 job 전부 green
- [ ] 빈 DB Flyway 적용 성공, V 번호 중복 없음, 기존 V_파일 불변
- [ ] #853·#854·#855 계약 문서와 코드 일치, 문서 동기화(API/FLOWS/DOMAIN)
- [ ] POI 검증 문서(회귀·취약점·메모리·번들) 첨부
- [ ] 토큰 없이 확정 불가, #854 경로로 확정 불가를 **실제 요청 테스트**로 확인
- [ ] 응답·DB·로그·MCP에 풀 계좌번호/자격증명/원문 없음
- [ ] 운영 배포·운영 DB·main 병합 없음, 기본 OFF·`local & !prd` 유지(운영 활성화는 별도 승인)

Claude는 위가 충족된 뒤 재검수하고 **최종 승인 요청서**를 제출한다. 미충족 항목이 있으면 항목별로 반려한다.

## 11. 열린 질문 (사용자 결정 필요)

1. 원본·거래 보존기간의 실제 값(세무 보존 의무 기준) — 임의 확정하지 않는다.
2. 재인증 방식: 비밀번호 재입력 vs 추가 인증 수단.
3. 업로더≠승인자를 기본으로 강제할지(1인 운영이면 옵션 OFF).
4. 임시 장부 자동 만료일(N일).
