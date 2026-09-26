# NEWSTART RELEASE LEDGER — 2026-09-26

> 이 문서는 NewStart 배포 상태의 **source of truth**이다.
> 기억·이전 보고서 대신 이 표와 LIVE 화면을 기준으로 한다.

## BASE / HEAD (audit 시점)

- origin/main = **c1d5ae33** = LIVE = 2026-09-26 #424 머지본.
  - Build Check + "Deploy to EC2" 모두 success (09:42Z, #420 자동 deploy).
  - → **LIVE와 origin/main은 동일 SHA** (이번 감사에서 실측 확인).
- Chrome Extension Store 승인본 = **v0.4.0** (PUBLIC). 화면에 하드코드하지 않고
  설치된 Extension이 보내는 메시지 `d.version`에서 실시간 읽는다.
  단, Extension **소스**는 main이 아니라 `feat/newstart-chrome-agent-fss-poc` 브랜치에만 존재(참고용).

## FEATURE STATUS

STATUS = LIVE / MAIN_NOT_DEPLOYED / OPEN_PR / LOCAL_ONLY / HOLD 중 하나.

| # | FEATURE | STATUS | SOURCE PR / COMMIT | LIVE URL | LIVE VERIFIED | REMAINING |
|---|---------|--------|--------------------|----------|---------------|-----------|
| 1 | Chrome Extension v0.4.0 | LIVE | Store 승인본 / #414 | chrome web store (page reads d.version) | 화면 버전 하드코드 없음 확인 | 배포 불요 |
| 2 | Chrome Store 설치 연결 | LIVE | #414 (cd92b821) | /newstart/fss-helper | CTA + 설치 배선 LIVE | — |
| 3 | 금감원 민원 도우미 (기능) | LIVE | #409/#414 | /newstart/fss-helper | 200, 4단계 기능 LIVE | 단순화만 남은 상태 (row 4) |
| 4 | FSS 단순화 UX (범위 A) | OPEN_PR | release/newstart-20260926 (42a583aa) | /newstart/fss-helper | LOCAL만 (미배포) | PR 머지→LIVE QA |
| 5 | FSS Chrome Agent (서버측) | LIVE | #409/#414 | /newstart/fss-helper | contract test main에 존재 | — |
| 6 | Credit4U 실전 안내 (범위 B) | OPEN_PR | #416 → release/newstart-20260926 | /newstart/tools/credit4u | 기존 정적 페이지 LIVE | 확장분 PR 머지 대기 |
| 7 | 본인 채무상태 연결 (범위 B) | OPEN_PR | #416 → release (V1007→**V1009**) | /newstart #ns-status-panel | 미배포 | PR 머지 + V1009 적용 |
| 8 | 지피지기 AI 명칭 (범위 C) | OPEN_PR | release/newstart-20260926 (9a691dfa) | HOME/AI/FSS/Credit4U | LIVE는 아직 "NewStart AI" | PR 머지→LIVE 확인 |
| 9 | HOME Quick Action | LIVE | #421/기존 main | /newstart | 4핵심 카드 LIVE 확인 | — |
| 10 | A to Z 연결 | LIVE | ato_z rebuild (5b6972f7, merged) | /newstart/a-to-z | main 조상 확인 | — |
| 11 | 질문/감사회원 (Agent2) | HOLD | feat/newstart-thanks-member-questions | — | — | 오늘 범위 제외 |
| 12 | 사례/갈무리 지식 보존 | LIVE | #424 (c1d5ae33) | /newstart/updates 등 | docs main 반영·배포됨 | — |
| 13 | 12시간 지식진화 (Agent3) | HOLD | feat/newstart-agent3-knowledge-evolution | — | — | worktree preserved, 오늘 제외 |

## TODAY SCOPE

- INCLUDED: (A) FSS 단순화 UX · (B) Credit4U + 본인채무 상태 (V1009) · (C) 지피지기 AI 노출 문자열.
- EXCLUDED/HOLD: Agent2 질문·감사, Agent3 지식진화/scheduler/PII-upsert-audit, 신규 migration, 기타 리팩터.

## CRITICAL FINDING — Flyway V-number

- #416 원본은 `V1007__add_newstart_member_debt_status.sql`를 담지만,
  prod에는 이미 **V1008**가 적용돼 있다(#421). prd Flyway는 out-of-order 비활성
  (application-prd.yaml: outOfOrder 미설정, validate-on-migrate=true) → V1007 그대로 머지 시 deploy 실패.
- 조치: release 브랜치에서 **V1007 → V1009**로 renumber (98b2225c). 전 origin ref 스캔 결과 V1009 충돌 없음.
- V1009는 **schema-only** (신규 1:1 테이블 `newstart_member_debt_status`). **prod 데이터 백필 불요.**

## BUILD / TEST (audit 마감 시점)

- JS contract (newstart 7종): **39/39 PASS**.
- Gradle compile + targeted Java tests: 실행 중이었으나 완료 미확인 — 20:40 하드스톱으로 PASS 미확정.
- Release diff vs main: 18 파일, 오늘 3범위만 포함 — Agent2/Agent3 파일 없음(확인).

## DEPLOY

- 20:40 하드스톱 기준 compile/test PASS 미확정 → **배포하지 않음**.
- 보존: release/newstart-20260926 브랜치 push + PR open (머지 안 함).
- PR 머지 시 #420 자동 deploy가 main으로 흐름 → 이후 §5 LIVE 최소 QA 필요.
