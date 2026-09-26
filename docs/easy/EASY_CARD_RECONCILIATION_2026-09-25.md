# EASY 카드 대조 (Step 1) — 2026-09-25

> READ / PARSE / MATCH / REPORT ONLY. IMPORT 없음, DB 쓰기 없음.
> 본 문서는 집계값·경로만 기록한다 (카드번호/승인번호/개인정보 없음, 원본 XLS 미포함).

## 1. 대조 기준 장부 (운영 실측, census run 36107713327)

- ACADEMY POSTED: 657 (2026-01-01~12-28, revenue 61,186,000 / expense 70,902,803)
- PUBLISHING POSTED: 12 (expense 11,880,000)
- JOURNAL: 669 entries / 1,338 lines / Dr 143,968,803 = Cr 143,968,803 / diff 0 / unbalanced 0
- BATCH5: total 579 / IMPORTED 543 / READY 36 (7,656,600) / txn-link 543 / journal-link 543
- TXN 604~646: 43건 존재, 43건 journal 연결
- SOURCE: EASYSHOP 645 / MANUAL 24 / SHINHAN 0 / KB 0 / CARD_EVENTS 0 / TUITION 0

## 2. 실파일 상태 (2026-09-25, sxxra PC)

| 항목 | 상태 |
|---|---|
| SHINHAN 개인사업자이용내역 (~09-23) | COLLECTED, NOT_AVAILABLE_ON_THIS_PC |
| KB 이용내역 09-01~09-24 (46080B) | COLLECTED, NOT_AVAILABLE_ON_THIS_PC |
| 마지막 확인 위치 | C:\Users\VIEW LIFE\Downloads\ (해당 프로필이 본 PC에 없음) |

재다운로드하지 않음. "미수집"으로 판단하지 않음.

## 3. 파서 상태 (main @ 작업시점)

- SHINHAN: parser / normalizer / importer 모두 main에 존재 (PENDING까지, 자동전기 없음)
- KB: main에 없음 — PR #399 / #402 브랜치에만 존재 (미머지)
- 파서 버그 확정: 없음 (실파일 대조 전이므로 PENDING)

## 4. 대조 결과

실파일이 본 PC에 없어 row-level 매칭 미실행:

- SHINHAN: TOTAL/ALREADY/NEW/REVERSAL/AMBIGUOUS 모두 PENDING
- KB: 모두 PENDING
- BATCH5 36건 교차 확인: PENDING (파일 확보 후)
- SHINHAN ↔ KB 중복: PENDING (파일 확보 후)

## 5. 중복 방지 원칙 (Step 2 전제)

- EasyShop 반영분(657건, fingerprint/sourceRef 보유)을 카드 원본으로 재import 금지
- EXACT(date+amount+direction+정규화 merchant) 일치분은 ALREADY_IN_LEDGER로 제외
- NEAR는 AMBIGUOUS로 사람 확인 (자동 확정 금지)
- 취소/환불은 원거래 연결 후 별도 처리

## 6. 다음 행동 (정확한 지시)

1. VIEW LIFE PC에서 두 XLS를 USB/사설경로로 sxxra PC로 이동 (GitHub 경유 금지)
2. 파일 해시·행수·기간을 본 문서 §2와 대조 후 기록
3. Step 2 (대조 실행) 착수 — 파서 실행은 로컬 전용, DB 쓰기 없음
