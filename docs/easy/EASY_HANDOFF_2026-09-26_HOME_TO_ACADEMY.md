# EASY HOME → ACADEMY HANDOFF — 2026-09-26

> 학원 새 작업창에서는 이 문서를 처음부터 끝까지 읽은 후 작업을 시작한다.
> 이 문서를 현재 EASY 작업의 SOURCE OF TRUTH로 사용한다.
> 과거 ChatGPT 대화 내용을 추측해서 작업하지 않는다.

- HOME base: origin/main @4c1045e6 (2026-09-26 확인, 오전 78e3de8e 이후 이동)
- PR #418 (tax engine): OPEN, head d164fca4, CI PASS — merge하지 않음
- HOME 세션 변경: 문서 4건만 (코드 변경 없음, DB 쓰기 없음, merge/deploy 없음)

## FEEL2REAL MASTER PLAN

장기 목표: 대표자 직접노동 매출 의존도를 낮추고 AI·소프트웨어 기반 확장 구조로 전환.

- A. EDUHOLIC: 현금흐름 보습학원 + BuildIt/EASY/AI 교육 테스트베드.
- B. BUILDIT: 강사·학원용 AI business platform (내신Wiki, 문제지, 홈페이지 builder·deploy,
  학생명부·수납, EASY 장부·예상세액·분석·경영지원, 강사 확장·창업·franchise, AI tutor/robot).
- C. NEWSTART: 새출발기금 → 채무조정·추심대응 AI 사업.
- D. FUTURE AI VERTICALS: 검증 기술의 타 분야 확장.

## EASY MASTER ROLE

EASY = ACCOUNTING + TAX FORECAST + STUDENT MANAGEMENT + BUSINESS INTELLIGENCE.
원장이 벌이·비용·예상세금·세금준비금·학년별 학생증감·모집학년·직접노동/비의존 매출을 판단하는 화면이 목표.

## 2026 LEDGER (운영 실측, census 36107713327 재사용)

- ACADEMY 657 / PUBLISHING 12, 전건 POSTED·분류·provenance.
- JOURNAL 669 entries / 1,338 lines, Dr=Cr=143,968,803, diff 0, unbalanced 0.
- BATCH5: 579 total / 543 confirmed / 36 unresolved (7,656,600).
- COLLECTED ≠ PARSED ≠ MATCHED ≠ IMPORTED ≠ CLASSIFIED ≠ CONFIRMED ≠ POSTED (혼동 금지).

## SOURCE STATUS (DO NOT RECOLLECT)

- EasyShop (~09-12): COLLECTED(타 PC 파일), PARSED·IMPORTED·CLASSIFIED·CONFIRMED·POSTED (운영 Batch5).
- Shinhan (~09-23): COLLECTED(VIEW LIFE PC), 이후 전부 PENDING. 수집됨 ≠ 장부반영됨.
- KB (~09-24): COLLECTED(VIEW LIFE PC), 코드 미머지(#399/#402), 이후 전부 PENDING.

## 2026 예상세액

- 계산 가능: 사업장별 사업소득 (journal TB 기반, 조정 분리, 개인 합산).
- 차단 정상: 검증済み 세율 없음 → 세액 단계 REVIEW_REQUIRED.
- 부족: 인적공제 일체, 연금/건보, 기납부/원천, 세액공제, 신고서 대조값 (RRN 저장 금지 유지).
- 2025 세액 복사 금지. 2026 세율은 신고 시(2027-05) 공식 확인.

## 2025 TAX REFERENCE (validation only)

- 확정: 학원 109,603,000/96,621,724/12,981,276, 출판사 38,334,000/27,639,399/10,694,601,
  합산 23,675,877, 신고일 2026-05-07, 귀속 2025.
- 1,709,863/1,304,795/666,545/638,250은 CANDIDATE 유지 (위치 미확인).
- 출판사 723,712 차이: CAUSE UNKNOWN (추적 종료, 추가 연구 금지).

## PR #418 (OPEN, 재사용, merge 금지)

- BusinessTaxComputation / PersonalTaxAggregation(데이터 지정 primary) /
  TaxRuleRegistry(연도·검증게이트) / TaxReplayService / filing-check / income-tax API.
- 같은 엔진 재구현 금지. 먼저 데이터 입력구조 완성 후 판단.

## TAX PROFILE / STUDENT / KPI (AVAILABLE vs MISSING)

- Tax profile: 납세자 라벨·주사업장 코드만 파라미터. 공제·연금·기납부 전부 MISSING.
- 학생: 학교·학년·등록일·상태·수납 원시값 + 상태별 인원. 집계·cohort MISSING.
- KPI: 매출·손익·재학생수 가능. 예상세액·준비금·학년예측·채널·가맹·대표노동 구분 NEEDS DATA (가짜 숫자 금지).

## ACADEMY P1: HOMETAX + CREFIA 수집 파이프라인 (Shinhan/KB 재수집 금지)

- HOMETAX: 사용자 직접 로그인 (간편인증), 카드매출·현금영수증·세금계산서·원천/기납부 자료,
  공식 파일 → repo 밖 work-input → sha256 → parser → staging → dedupe → PENDING.
- CREFIA: 승인/매입/입금은 동일 매출 lifecycle — 별도 매출로 중복 계상 금지.
  승인번호·거래일시·카드사·금액·가맹점·매입/입금일·수수료·externalId 조사.
- 인증: Agent STOP → 사용자 인증 → 재개. 비밀번호/OTP/SMS/credential 저장 금지.
- 파이프라인: SOURCE → HUMAN AUTH → DOWNLOAD → RAW ARCHIVE → SHA256 → PARSER →
  STAGING → NORMALIZE → DEDUP/MATCH → PENDING → ADMIN 승인 → TRANSACTION → JOURNAL.
- 자동 posting 금지. provenance (source·파일명·시각·sha256·행·externalId·파서버전) 필수.
- 금융 원본 GitHub 업로드 금지.

## 학원 확인 자료

- VIEW LIFE PC 2 XLS (USB/사설 이동), EasyShop 18 CSV 위치, 실제 신고서,
  홈택스 인증 휴대폰/환경.

## 동봉 문서 (본 브랜치에 함께 commit)

- EASY_1200_CHECKPOINT_2026-09-26.md (오전 조사 저장)
- EASY_BANK_COMPLETENESS_AUDIT_2026-09-25.md (은행 완전성)
- EASY_CARD_RECONCILIATION_2026-09-25.md (카드 대조, Step 2 대기)

## 보안

- 금융 원본·credential·cookie·OTP·PII commit 없음 (검사 완료).
- local-collector/.e2e-kb-downloads/ (140B mock): LOCAL ONLY, 미커밋·미삭제.

--------------------------------------------------

[ACADEMY NEW SESSION — START HERE]

TARGET REPOSITORY:

woori73/feel2real-buildit


FIRST ACTION:

GitHub의

docs/easy/EASY_HANDOFF_2026-09-26_HOME_TO_ACADEMY.md

를 처음부터 끝까지 읽는다.


이 문서를
현재 EASY 작업의 SOURCE OF TRUTH로 사용한다.


과거 ChatGPT 대화 내용을 추측해서
작업하지 않는다.


그 다음 repository 상태 확인:

pwd
git rev-parse --show-toplevel
git remote -v
git status
git branch --show-current
git fetch origin --prune


최신 origin/main 확인.


ACADEMY P1:

HOMETAX
+
CREFIA / 여신금융협회

공식 데이터 collection pipeline 구축.


사용자가 직접 로그인/인증한다.


Agent는:

credential 저장 금지
PII commit 금지
raw financial file GitHub upload 금지.


목표:

공식 데이터
→ 원본보존
→ sha256
→ parser
→ staging
→ dedupe/match
→ PENDING

까지 실제 파일로 검증한다.


자동 journal posting은 아직 하지 않는다.


이미 확보된:

EasyShop
Shinhan
KB

자료는 재수집하지 않는다.


--------------------------------------------------
