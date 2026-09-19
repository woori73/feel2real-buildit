# 05 — GitHub 오픈소스 조사 (한국 자동수집 관련)

> 조사일: 2026-09-20 (PHASE 0). 방법: GitHub REST API + raw.githubusercontent.com로 **실제 소스 파일 정독**(README만 읽지 않음). 검증 파일 사본: `%LOCALAPPDATA%\Temp\jangbu\`.
> 결론 요약: **connectedId 수명주리의 권위 레퍼런스는 `codef-io` 공식 샘플(MIT)뿐**. `korean-jangbu-for`는 CODEF 연결 모델을 빠뜨려 수집기로는 재사용 부적합하나 정규화/dedupe/마스킹 패턴은 Apache-2.0 범위 내 복제 가치 있음. 홈택스 직접 스크레이핑 실전 코드는 `finecodekr/hometaxbot`(Apache-2.0)이 사실상 유일.

## 1. kimlawtech/korean-jangbu-for 심층 분석

### 1.1 메타
- ★83 / fork 17 / **License: Apache-2.0** (LICENSE 파일 원문 확인) / Python 193KB 핵심
- created 2026-04-22, last push 2026-08-29, v0.2.0. 실수집 코드 커밋은 4/22~23에 집중, 이후는 README 갱신만 → **사실상 동면**
- 정체: SaaS 백엔드가 아니라 **Claude Code 스킬 + 로컬 Python MCP(stdio) 서버**, 단일 사용자·local-first·BYOK
- 지시서의 "jangbu-connect"의 실체: `skills/jangbu-connect/SKILL.md`(자연어 자격증명 발급 가이드, 코드 아님) + `mcp-server/jangbu_mcp/connectors/codef.py`(실 CODEF 연동, 12KB)

### 1.2 CODEF 연동 (`connectors/codef.py` 정독 결과)
- SDK 없음, urllib 직접 HTTP. OAuth2 client_credentials → 토큰 `~/.jangbu/codef_token.json`(chmod 600) 캐시
- RSA(PKCS1v15)로 **password 필드만 전송 암호화**; 공개키 미설정 시 **평문 전송 폴백**(저장 암호화 아님)
- 하드코딩 엔드포인트: 홈택스 증명발급 4종(`/v1/kr/public/nt/proof-issue/*`, org `0001`), 은행 `/v1/kr/bank/p/account/transaction-list`, 카드 `/v1/kr/card/p/account/approval-list`

### 1.3 치명 결함 (EASY 비교 관점)
| 항목 | jangbu 실제 코드 | 평가 |
|---|---|---|
| **connectedId 수명주기** | **전무** — `/v1/account/create`→connectedId 발급/저장/갱신/삭제 코드 없음, "connectedId" 문자열 자체가 미등장. 매 호출마다 account+password 직접 전송 | CODEF 표준 모델 위반, 프로덕션 동작 가능성 낮음 |
| raw payload 보존 | CODEF 응답 원본 저장 안 함, 즉시 normalize 후 INSERT. "원본 응답 저장" 주장과 달리 코드 없음 | **EASY가 반드시 자체 구현** |
| retry/scheduler | grep(retry\|backoff\|schedule\|cron) 0건. on-demand(LLM 트리거)만 | 부재 |
| 복식분개 | 미구현 — 거래당 단일 1행 CSV, VAT=total/11 하드코딩, BS는 "간이" | EASY의 EasyJournalService가 훨씬 완결 |
| 인증서 | 공동인증서 처리 코드 없음(CODEF 간편인증 위임) | — |
| dead code | `map_tax_invoice()` 호출자 없음, tax-certificate만 하드코딩된 간편인증 헬퍼 | 검증 부재 신호 |

### 1.4 연결 기관 (코드 CONFIG 기준)
- 은행 8개 매핑: kb`0004` shinhan`0088` woori`0020` hana`0081` ibk`0003` nh`0011` kakao`0090` toss`0092`
- 카드 9개: shinhan`0306` kb`0301` samsung`0302` hyundai`0303` lotte`0304` bc`0305` woori`0307` hana`0308` nh`0309`
- 홈택스: **증명서 발급 4종만** (세금계산서/현금영수증/카드매출 조회 미연동)
- **여신금융협회(cardsales): 미연동** — 관련 OSS 자체가 GitHub 검색 0건

### 1.5 dedupe 구현 (복제 가치 최고)
- 표준 13필드 transaction + SQLite `UNIQUE(source, source_ref)`; INSERT 예외로 중복 조용히 skip, duplicates = fetched − inserted
- source_ref 구성 원문:
  - 은행: `codef:{resAccountTrNo 또는 fallback account:date:desc:amount}`
  - 카드: `codef:{resCardApprovalNo 또는 fallback ...}` ← organization 누락으로 카드사 간 승인번호 충돌 가능
  - 세금계산서: `codef:invoice:{resAuthorizationNo}:{issue_date}:{total}`
  - CSV: `{bank}:{account_id}:{date}:{desc}:{amount}` / 카드 approval_no 우선
- **약점**: composite 폴백 키에 자유텍스트(desc/merchant) 포함 → 공백/노이즈에 취약. EASY 설계에서는 1차=외부 고유ID, 2차=normalized composite+승인번호 등 보완 필요(→13번 문서)

### 1.6 재사용 구분 (Apache-2.0, LICENSE/NOTICE 유지 조건)
**코드 직접 가져올 수 있음:**
- `parsers.py` BANK_COLUMN_MAPS(KB/신한/우리/하나/IBK 한글 헤더 매핑), EUC-KR/CP949 인코딩 폴백, `_to_decimal`/`_norm_date`
- `masking.py` 사업자번호/주민번호/카드번호/계좌번호 정규식 + 토큰화(tokens.db 분리 저장) 설계
- `storage.py` 13필드 표준 스키마 + `UNIQUE(source,source_ref)` dedupe 패턴
- `rules.py` 분류 룰 엔진(priority ASC, 조건 필터, user-learn=확정을 counterparty_exact 규칙로 자동 학습) — **EASY의 classification_rules와 동일한 구조이므로 학습-loop 아이디어만 참고**
- `codef.py` `map_*()`의 CODEF 실제 응답 필드 사전(`resAccountTrDate/resAccountOut/In/resAccountTrNo`, `resUsedDate/resUsedAmount/resMemberStoreName/resMemberStoreRegNo/resCardApprovalNo`, `resTotalAmount/resSupplyAmount/resTaxAmount/resInvoiceType/resAuthorizationNo`)

**설계 참고 전용(복제 부적합):** MCP+스킬 패키징, keyring/envfile BYOK 우선순위, append-only audit.log. 단일사용자·local sqlite·스케줄러 부재 → 멀티테넌트 SaaS 수집 레이어로 재활용 불가.

## 2. CODEF 공식 샘플 SDK — org `codef-io` (⭐ connectedId의 정답)

https://github.com/codef-io · 전부 **MIT** · API 클라이언트 라이브러리(자동수집 로직 없음)

| repo | ★ | lang | last push | 비고 |
|---|---|---|---|---|
| `codef-io/easycodef-java` | 10 | Java | 2026-06-30 | **최활발, v1 SDK — EASY(Java/Spring) 1차 참고** |
| `codef-io/easycodef-java-v2` | 7 | Java | 2025-11-03 | v2 |
| `codef-io/codef-python` | 8 | Python | 2022-01-04 | connectedId 코드 최명료 |
| `codef-io/easycodef-node` | 7 | TS | 2024-04-30 | |
| 기타(easycodefpy/go/rb/legacy) | 0~4 | | 2020~24 | |

`codef-io/codef-python` 파일 정독으로 확인한 **표준 수명주기**:
- `RegisterAccount.py`: `POST /v1/account/create` → `data.connectedId` 반환. `/v1/account/add`(계좌 추가), `/v1/account/update`(비밀번호 변경→신규 connectedId), `/v1/account/delete`(revoke)
- connectionItems에 `organization` 코드 + `environment:"real"` + RSA(PKCS1_v1_5) 암호화 password 전송
- `ConnectedIdList.py`(`/v1/account/connectedId-list`), 조회 body는 `{'connectedId','organization'}` — jangbu가 빠진 모델
- `http_sender`: **401 → 토큰 재발급 → 동일 요청 재시도** 루프
- `CertManager.py`: 공동인증서 `.der/.key` base64 인코딩 전송 (공동인증서 필요 기관용)
- 은행 **법인보유계좌** 샘플 존재: `/v1/kr/bank/b/account/account-list` (`sample/bk/TestKR_BK_1_B_001.py`) — 사업자 계좌 경로 실재 확인

## 3. finecodekr/hometaxbot — 홈택스 실전 라이브 스크레이퍼 (유일)

https://github.com/finecodekr/hometaxbot · ★73 · Python · **Apache-2.0** · pushed 2026-08-10(활성) · CODEF 경유 아닌 hometax.go.kr 직접 접속
- `scraper/transactions.py`: 전자세금계산서 **매입·매출** 기간 조회(분기 분할 `split_date_range`), invoice_type 01전자세금계산서/02위수탁/03전자계산서, 조회기준(작성/발급/전송일자)
- `crypto.py`: **진짜 공동인증서 처리** — PKCS#12 KDF(RFC7292), ARC2-40/3DES PBE 복호화, KISA rand_num OID, pypinksign 서명/검증, 만료 검사
- `k1~k8`: 홈택스 `common_te-min.js` 리버스(HMAC-SHA256, nts_hash_param, snake_oil_encrypt) — **fragile 지점, daily cache**
- `browser.py` Playwright persistent context + `taxagent.py` 직접 HTTP/XML 하이브리드, `Throttled` 속도제한 처리, `hometax_xml_fields.yml` 필드 사전
- 분개/장부 없음(수집·파싱 전용)
- **평가**: 코드 복제 가능(Apache-2.0)하나 JS 리버스 의존 + 스크레이핑 자체의 **홈택스 이용약관/법적 리스크**가 커서 EASY에서는 최후의 수단으로만 평가. 설계 아이디어(분기 분할, XML 필드 사전, 인증서 수명주기 관리)만 차용 가치

## 4. 기타 조사 결과

| repo | 메타 | 판정 |
|---|---|---|
| `lbiz-partners/hometax-doum` | ★53, NOASSERTION 라이선스, 초활성(2026-09-19 push). 세무업무 **스킬/체크리스트**지 자동수집 아님("로그인·제출은 본인이") | reference-only, 라이선스 불명확 |
| `odinsoft-lab/open.etaxbill` | ★20, 소스 회수됨(공개 종료). 과거 .NET 전자세금계산서 엔진: collector→signer→reporter→responser→mailer→provider 파이프라인 분리 | 문서상 파이프라인 분리 개념만 참고 |
| `dydals3440/korean-account` | ★10, TS, MIT, 활성(2026-09-16). KFTC 계좌번호 체계 레지스트리 57기관 + confidence 스코어링 | MIT — 계좌→기관 resolution 라이브러리 차용 가능 |
| `popbill/*` SDK (linkhub-sdk-python/java/php) | MIT, 발행/조회 도메인 모델, 헤더 IP+timestamp+salt(SHA512) 인증 | 팝빌 채택 시 SDK 기반 |
| `leemhoon00/barobill-sdk` | ★1, TS, 무라이선스. 세금계산서·현금영수증·**카드조회**·계좌조회 | 참고만 |
| `gabyyyyy/tax-invoice-automation` | ★0, Popbill+Cronicle 스케줄 자동 발행 | 스케줄러 참고 |
| 세법 MCP류 (`minsooparkk/korean-tax-agent` ★13 MIT, `taxwoong/nts-tax-mcp` ★3 등) | 검색/안내용, 수집 아님 | 무관 |

**부재 확인(중요)**: `여신금융협회`/`cardsales`/`bank scraping korea`/`openbanking korea` 검색 **0건** — cardsales 자동수집 OSS는 이 세상에 존재하지 않음. `NPIOpenSourcePlatform/OpenBankingApi` **404(조직 자체가 없음)** — 한국 오픈뱅킹 공식 OSS는 GitHub에 없고 developers.kftc.or.kr 배포. `user:finexer` 저장소 0건. 공식 오픈뱅킹 Java 래퍼 없음 → 필요 시 자체 구현.

## 5. EASY 관점 종합 시사점

1. **cardsales(여신금융협회)는 OSS 선례 0건** → 02번 문서의 공식 경로(파일 다운로드/제휴) 조사 결과에 전적으로 의존. 자동화의 선행 사례가 없다는 것 자체가 리스크.
2. **CODEF를 쓰는 경우 connectedId 수명주기(create/add/update/delete + 401 retry)는 `codef-io` MIT 샘플이 유일하고 정확한 모델** — Java SDK `easycodef-java`에서 시작.
3. **정규화/dedupe/마스킹**은 jangbu(Apache-2.0)에서 복제하되 결함 보완: organization 포함 keyed dedupe, 외부ID 우선, 텍스트 정규화 강화.
4. **복식분개·스케줄러·raw 보존·retry는 어떤 OSS에도 없음** → EASY가 자체 설계(이미 분개 엔진은 최상위 수준).
5. **홈택스 직접 접속은 hometaxbot이 유일한 실전 코드**이지만 리버스 엔지니어링 기반이라 법적/취약성 리스크 큼.
