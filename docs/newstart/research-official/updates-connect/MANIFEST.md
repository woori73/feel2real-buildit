# Official updates connect — 2026-10-01 (MANIFEST)

## SOURCE OF TRUTH (재수집 금지)

- k13~k42 원본: `docs/newstart/research-official/ccrs/` (commit 044c8d6d, PR #444)
- FSC 신규 3건: `../fsc/fsc_connect_2026-10-01.json` (원문 실측 2026-10-01)
- HOPE 신규 3건: `../hope/hope_connect_2026-10-01.json` (원문 실측 2026-10-01)

## Payload

- `official_updates_connect_2026-10-01.json` — 24행 (admin create shape)
  - CCRS 3 (k13, k14, k15) / NEWSTART_FUND 3 (k21, k28, k40)
  - FSC 5 (k23, k24 + 신규 87188/86357/85859)
  - CREDIT4U 2 (k22, k27) / FSS 4 (k29, k30, k31, k34)
  - COURT 4 (k36, k37, k38, k42) / HOPE 3 (신규 117933/117934/121182)
- k-derived 행의 `publishedAt`은 확인일(2026-09-30)이다. 원문 게시일이 아닌 값을
  게시일로 속이지 않기 위해 기록한다. FSC/HOPE 신규 행은 실제 게시일이다.
- summary는 270자 이내 단어 경계 절단, 끝이 `...`이 되지 않게 한다
  (PARTIAL이 아닌 FULL 판정을 위한 기존 계약 준수).

## 제외 12건 (사유 기록 — 삭제 아님, knowledge REVIEW는 그대로)

- k16 kamco / k17 sinbo / k18,k19,k20 gcgf — 연결 channel 없음
- k25 kcredit(올크레딧, Credit4U와 다른 기관) / k26 easylaw(Credit4U 귀속 부적합)
- k32,k33 법령원문(RAG 지식으로 충분)
- k35,k39 easylaw(법원 귀속 부적합) / k41 klac(법률구조공단, channel 없음)
- HOPE 원스톱폐업지원(sbiz.or.kr) — 원문 직접확인 실패로 unverified 적재 금지

## Loader

- `load_official_updates_prod.py` — insert-only. DRY_RUN 기본.
- workflow: `newstart-official-updates-connect.yml` (DRY_RUN / WRITE).
- WRITE는 admin create(REVIEW) 후 admin publish를 같은 실행에서 수행한다
  (reviewer = ADMIN login id, 사후 reject/archive 가능).
  knowledge REVIEW·RAG·cafe 영역은 건드리지 않는다.
