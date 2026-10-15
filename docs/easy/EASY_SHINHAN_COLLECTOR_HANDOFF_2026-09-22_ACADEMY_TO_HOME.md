# EASY Shinhan Collector — Academy → Home Handoff (2026-09-22)

> 학원 PC 작업 종료 시점 인계 문서. 집 PC에서 이어서 작업함.
> 이번 문서가 다루는 범위: Shinhan 카드 Excel → EasyCardEvent 파이프라인 (PENDING_REVIEW까지, posting 없음).

---

## 1. GITHUB STATE

**Repo:** woori73/feel2real-buildit
**Branch:** `feat/easy-shinhan-card-event-pipeline`
**Base:** `feat/easy-visible-local-collector-v1` (**PR #369**, 아직 merge 안 됨) — main이 아님
**Commits:** `b1315a75` (pipeline 구현), `6f013e21` (CI allowlist 추가)
**PR:** [#373](https://github.com/woori73/feel2real-buildit/pull/373) — OPEN, base=#369 branch, MERGEABLE
**CI:** Build Check PASS (bootJar + 17 신규 테스트 + 기존 EASY 카드회계 회귀 전부 포함, 재검증 완료)

이 브랜치는 PR #369에 종속된다 — #369가 merge되기 전엔 이 PR도 merge 대상이 아니다.

---

## 2. IMPLEMENTED (이번 세션)

- `ShinhanCardExcelParser` — 실제 BIFF8(.xls) 11개 컬럼 파싱, 헤더를 행 인덱스가 아니라 내용("거래일")으로 탐색, "합계" 행에서 정지
- `ShinhanCardNormalizer` — 부호 있는 금액 그대로 보존, 동일 내용 반복 행(예: 교원라이프 59,900원 4건 동일자 동일가)을 `occurrenceIndex`로 구분
- `ShinhanCardImportService` — FIFO 매칭(카드+가맹점+상품구분+절대값)으로 양수/음수 페어링, fingerprint 기반 재수집 idempotency, boundary dedupe(기존 easy_transactions과 business+date+amount 대조)
- `EasyCardEvent` + `EasyBusinessCardAccountingService` — `confirmationDate` 필드 추가(거래일과 절대 합치지 않음)
- **Migration V995** — `easy_card_events.confirmation_date DATE NULL` (nullable, additive, 기존 row 영향 없음; V992에서 renumber, main CHOONGGI V992와 충돌 회피)
- PENDING_REVIEW까지만 — `approveAndPost`(기존, 무변경) 전에는 `easy_transactions`/journal 어디도 안 건드림

---

## 3. TESTS

- 신규 17개(파서 4 + normalizer 4 + import service 9) — **CI PASS**, §12 required assertion 전부 커버
- 기존 `EasyBusinessCardAccountingDomainTest`/`EasyBusinessCardAccountingServiceTest`(entity/service 시그니처 변경에 가장 노출된 테스트) — **CI PASS**, 회귀 없음
- `bootJar` — **CI PASS**. 로컬 gradle은 이 PC에서도(academy) JDK NIO loopback/Unix-domain-socket 실패로 실행 불가(JDK 17/21 둘 다 동일 실패, 다른 PC에서 기존에 기록된 것과 동일 증상) — 이번 세션에서 생긴 문제 아님, CI로 대체 검증

---

## 4. REAL SOURCE QA — NOT YET RUN

이번 세션은 실제 다운로드 파일(`개인사업자이용내역download.xls`, 학원 PC Downloads 폴더)을
파이프라인에 넣어보지 않았다. synthetic fixture로만 검증됨.

**PRODUCTION IMPORT: 0**
**REAL DATA IN GIT: 0** (실제 파일은 Git에 올라간 적 없음, 앞으로도 올리지 않는다)

---

## 5. NEXT EXACT STEP (집 PC)

```
1. git fetch origin --prune
2. gh pr view 373 --repo woori73/feel2real-buildit  (PR/CI 상태 재확인)
3. 학원 PC의 개인사업자이용내역download.xls를 Git에 넣지 않고 로컬 경로만 확보
   (파일 자체를 옮기거나 재다운로드 — 방법은 그때 판단, Git 경유 금지)
4. READ/PARSE/NORMALIZE만 먼저 실행 (ShinhanCardExcelParser + ShinhanCardNormalizer만,
   ShinhanCardImportService/EasyCardEvent 생성은 아직 호출하지 않음)
5. 검증:
   - 29건 거래 행
   - 2026-09-12 boundary 행(더빈마켓 송파점, 15,600원)
   - 거래일/확정일 분리
   - 부호 있는 금액(음수 2건: 09/19 쿠팡 페어 매칭, 09/20 쿠팡 할부3개월 unmatched)
   - 가맹점명
   - 합계 1,026,892원과 일치
6. 파싱 결과를 원본 Excel과 수동 대조
7. 파싱 QA 통과 후에만 통제된 EasyCardEvent import QA 설계
```

**절대 하지 말 것**: 파싱에서 바로 production posting으로 건너뛰지 않는다.
ACCOUNTING POSTING은 여전히 ADMIN APPROVAL → `approveAndPost` 뒤에만 있다.

KB Kookmin은 Shinhan real-source QA가 끝난 뒤에만 시작한다.

---

## 6. FORBIDDEN (이어서도 유지)

- KB Kookmin / 은행 collector 시작 금지 (Shinhan QA 완료 전)
- Production DB write 금지
- 실제 Excel Git 업로드 금지
- MERGE 금지 (PR #373은 #369 의존 — #369도 아직 merge 안 됨)
- DEPLOY 금지
