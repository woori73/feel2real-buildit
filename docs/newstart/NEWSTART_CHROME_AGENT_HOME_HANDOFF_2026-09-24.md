# NEWSTART CHROME AGENT — HOME HANDOFF (2026-09-24 퇴근 시점)

- PROJECT: NewStart Chrome Agent FSS PoC (읽기·판별·안내 전용, 자동 조작 0)
- BRANCH: `feat/newstart-chrome-agent-fss-poc` (origin에 push 완료)
- VERSION: 0.4.0
- COMMIT: `fe383e39` (v0.4.0), 직전 `2754d26a` (v0.3.0), base `7719646c` = PR #400 head — **PR #400 무접촉 유지** (head는 별도 브랜치 `feat/newstart-fss-guided-agent-phase1`)
- TEST: extension targeted **37/37 PASS** (`node --test test/detector.test.mjs test/panel.contract.test.mjs`)

## REAL QA STATUS (academy Chrome, 사용자 수동)
PASS:
- 금감원 1단계 개인정보동의 (`forInsertAgre.do`, radio 3종 실측 반영)
- 금감원 2단계 개인정보입력 (`forInsertStep1.do`) — 직접 입력만, 값 미열람

## NEXT HOME QA (4차, v0.4.0)
1. `chrome://extensions` ⟳ → 버전 0.4.0 확인 (배지: `http://localhost:8081/newstart/fss-helper` "연결됨 (v0.4.0)")
2. [본인인증받기] **사용자 직접** 클릭 → 인증 팝업에서 빨간 실패 대신 **"본인인증 진행" 중립 카드**(낮은 창은 접힌 bar "NewStart 안내 · 본인인증") 확인
3. Agent가 인증을 조작하지 않는 것 관찰(DETECT+GUIDE+WAIT뿐)
4. 인증 후 팝업 닫고 메인 창 포커스 → 실제 근거로 재판별되는지 확인
5. 미지원 화면 UNKNOWN(빨강) 회귀 확인

## DO NOT
- 인증정보 읽기 / 인증 자동화 / 최종 민원 등록·실제 제출
- PR #400 접촉, merge, deploy, Web Store 제출
- 금감원 3·4단계(민원정보입력/민원등록) 매핑은 실측 evidence 확보 전까지 보류(§8 발명 금지) — 재테스트 시 **주소창 URL과 패널 문구(구조만)** 보고가 다음 근거

## HOME FIRST STEP
```
git fetch origin --prune
git switch feat/newstart-chrome-agent-fss-poc
```
또는 별도 worktree: `git worktree add <path> feat/newstart-chrome-agent-fss-poc`
(공용 PC worktree `feel2real-wt-newstart-chrome-agent`와 충돌 없이 원격 브랜치 기준 신규 checkout 가능)

상세: `docs/newstart/NEWSTART_CHROME_AGENT_FSS_POC_2026-09-24.md`(1~4차 QA/권한/프라이버시 경계), `browser-extension/newstart-chrome-agent/README.md`(설치·테스트).
