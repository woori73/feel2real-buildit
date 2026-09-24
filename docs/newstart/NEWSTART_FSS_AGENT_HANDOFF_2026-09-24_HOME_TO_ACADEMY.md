# NewStart FSS Guided Agent — HOME → ACADEMY Handoff (2026-09-24)

## 0. 기본 정보

- REPOSITORY: `woori73/feel2real-buildit`
- WORKTREE(HOME): `C:\Users\sxxra\feel2real-wt-fss-agent`
- BRANCH: `feat/newstart-fss-guided-agent-phase1`
- BASE SHA: `25ef07b7` (main 최신 = PR #398 V1000 머지 후)
- 이 handoff 문서 커밋: 아래 §9 COMMIT/PUSH/PR 기록
- EASY worktree(`C:\Users\sxxra\feel2real-buildit`)의 dirty 변경(V993→V1000 잔여, EASY_HANDOFF 수정): **전적으로 미접촉**. 이 문서 작업은 FSS 전용 worktree에서만 수행.

## 1. ARCHITECTURE 결정 (확정)

- **Phase 1 = C. 사용자 PC Chrome + GUIDED ASSISTANT (지금 구현 대상)**
  - NewStart는 브라우저에 손을 대지 않는다. 14단계 안내 + AI 민원 초안 + 복사만 제공한다.
  - 사용자는 자기 PC Chrome의 금감원 탭에서 직접 클릭·입력·본인인증·최종 제출한다.
  - 사이트 고지 필수 문구: **"금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다."**
    + "NewStart가 단계별로 안내합니다. 개인정보 입력, 본인인증 및 최종 제출은 금융감독원 공식사이트에서 사용자가 직접 진행합니다."
  - Phase 1에서 Agent는 사용자 탭을 보지 못하므로 **"현재 화면을 확인했습니다" / "인증을 감지했습니다" 류 표현 금지.** 진행 표기는 사용자가 직접 누르는 "이 단계를 마쳤습니다" 형태여야 한다.
- **A. SERVER PLAYWRIGHT → production 사용 금지 (판정 근거 아래 §2)**
- **B. Phase 2 = NewStart Browser Assistant (Chrome Extension/Bridge) — 이번 작업 범위 밖, 구현 금지**

## 2. HOME에서 미커밋으로 남은 A 구조 코드 (중요)

12:40 KST 종료 규칙(불완전·미검증 변경 커밋 금지)에 따라, HOME 세션이 작성한
Phase1-A 코드와 테스트는 **commit되지 않은 채 HOME worktree 디스크에만 남아 있다.**
학원 PC의 git에서는 받을 수 없다. 삭제하지 않았으므로 HOME PC에서 계속 가능.

상태: `FssComplaintWorkflow.java`에 단일 컴파일 오류 잔존(라인 24 `MANUAL_DIVERGENCES`의
인접 문자열 리터럴 연결이 이 머신의 컴파일러에서 실패 — 순수 ASCII 재현됨, 한글 무관.
해결 지시: 인접 리터럴을 명시적 `+` 연결 또는 단일 리터럴로 변경).
아직 bootJar/visual QA 미실행. A 제거 작업은 착수했으나 결과물 없음.

HOME worktree 파일 목록 (`git status --porcelain`, 모두 uncommitted):

```
M  build.gradle                     ← playwright 1.52.0 추가됨 (C 전환 시 제거 대상)
M  src/main/resources/templates/newstart/fss-helper.html  ← .ns-ag-* CSS + mode selector + agent 패널 + agent IIFE (C 요건에 맞게 문구/동작 재조정 필요)
?? .../domain/newstart/fssagent/    ← 12개 main 파일 (아래 분류)
?? .../fssagent/ (test)             ← 5개 테스트 파일
```

**C 전환 시 처리 분류 (재사용 지도):**

- 재사용(A/B/C 무관 — 브라우저 비의존):
  - `FssAgentState`(단, guided 상태로 일부 재정의), `FssComplaintWorkflow`(14단계 mapping + `MANUAL_DIVERGENCES` 4종 + fail-safe/에러 회피 로직. 단 afterContinue가 snapshot 기반 → C에서는 user-confirm advance로 전환 필요), `NewStartFssAgentController`의 인증/principal 패턴(`/api/newstart/fss/agent/*`, JWT catch-all 재사용, 요청 body 없음)
  - Phase 2(B) 재사용 예정: `FssAgentAction`(sealed DSL — ClickText/SelectRadio만, 좌표클릭 표현 불가), `FssBrowserGateway`(interface), `FssPageSnapshot`, `FssScreenKind`, `FssStepDetector`(POC fixture URL/radio 탐지 — content script가 스냅샷 공급하면 그대로 재사용), 관련 단위테스트(`FssStepDetectorTest`, `FssComplaintWorkflowTest` 중 과도 조정)
- 제거/비활성(A 전용): `FssPlaywrightGateway`, `FssPlaywrightGatewayFactory` → **production path에서 삭제**, build.gradle `com.microsoft.playwright:playwright:1.52.0` 의존성 → Phase 1 production에 불필요 → **제거**
- 재작성 필요: `FssAgentService`(gateway 제거하고 순수 guided 세션으로), `FssAgentServiceTest`(FakeGateway 전제 → 폐기/재작성), `FssPrivacyContractTest`(SNAPSHOT_JS 참조 → gateway 삭제 시 해당 asserting 제거, "값/시크릿 미기록" 계약 자체는 유지), `FssAgentLiveWalkTest`(삭제 — 서버 자동화 전제이고 실제 제출 리스크 경로. POC는 `C:\Users\sxxra\ns387-qa\fss_poc1..12.mjs`에 보존되어 있고 **삭제 금지**)

## 3. POC 실측 RESULT — STEP 1~7 SOURCE OF TRUTH (2026-09-24, headless Chromium READ-ONLY)

진입 URL(매뉴얼 ①과 동일): `https://www.fss.or.kr/fss/minwon/search.jsp?query=&collection=simcase&searchField=ALL&sort=&pageNum=1&rgnlCd=&cvplCd=&cvplCd2=`

- **STEP 1**: search.jsp — 유사사례 검색 화면. 우상단 [신청] 버튼.
- **STEP 2**: [신청] 클릭 시 `fnOkClick()`이 **alert()** 팝업(확인). alert accept 후 진행.
- **STEP 3**: `forInsertSelect.do` — 매뉴얼의 "팝업창 확인→아니요" 화면 없음. 대신
  **"본인의 금융거래와 관련하여 금융회사에 사실확인 필요한 민원입니까?"(radio `minwonChk` Y/N)** + [다음](`checkForm()`). 매뉴얼 [아니요] ↔ `minwonChk=N`.
- **STEP 4**: `forInsertAgre.do` — 3개 동의 radio(`agreechkYn` 개인정보 / `sinfoSndYn` 민감 / `agreechkRgnoYn` 고유식별) 모두 Y → [다음] 시 **`#popup-alert` 모달**("재설정 / 민원신청 / 닫기") 추가됨(매뉴얼에 없음). **[민원신청] 버튼 = `onSubmit()`** → `forInsertStep1.do` (POST frm). 자동제출이 아니라 개인정보입력 화면으로의 진행 확인임.
- **STEP 5·6·7은 한 화면** `forInsertStep1.do`:
  - 5: 성명·생년월일·연락처·주소 직접 입력 + [없다]/[있다] 라디오 + [본인인증받기]. 인증 후 `pName`/`pBrdt` readonly 자동채움.
  - 6: "과거 동일 내용 민원 없음"의 **[없다]가 같은 화면의 라디오**(`avoidYn` 계열) — 매뉴얼처럼 별도 화면 아님.
  - 7: [본인인증받기] → `fnCertificationPop()`이 `certificationPop.do`를 **window.open 팝업**으로 열기(간편/공동 `fnTransfer('X')`/휴대폰 'M'/아이핀 'I'). **Agent는 절대 인증수단을 조작하지 않는다.**
- **주소찾기**: 다음 지도 `daum.net` postcode弹窗(새 창).
- **인증 세션 바인딩**: 진행 중 화면 URL 직접 재접근 → `/cmmn/error.jsp`. (C 구조에서는 사용자가 같은 탭을 유지하므로 자연 충족.)
- **STEP 8~14: 미검증** (인증 이후 화면 POC에서 진입 금지로 조사 못함). 추측 자동화·임의 selector 금지. 안내 문구: **"인증 이후 일부 메뉴명이나 위치는 현재 금융감독원 화면과 다를 수 있습니다."**
- 합성 `dispatchEvent`는 jQuery validation에 무시됨(Phase 2 확장에서는 trusted click 필요).
- 이미지: 14단계 매뉴얼 캡처 이미지는 repository에 **없음** — 가짜 생성 금지.

## 4. HOME에서 완료한 것 / 미완료

완료:
- READ-ONLY POC 전 과정(poc1~12, `C:\Users\sxxra\ns387-qa`) + FEASIBILITY REPORT(승인됨)
- A 구조 백엔드/프론트 초안 작성(미커밋, 위 §2)
- **ARCHITECTURE CHECK 보고서 제출 → A 금지 / Phase1=C / Phase2=B 확정**
- 14단계 수동 가이드·AI 민원 초안(V1, PR #381 계열 main에 이미 머지됨) 보존 확인 — fss-helper.html 기존 wizard/매뉴얼/복사 기능은 이 worktree에서도 그대로 유지되어 있음

미완료 (학원 또는 다음 HOME 세션에서):
- FssComplaintWorkflow 컴파일 오류 1건(§2) 및 A→C 전환 구현 전체
- C 전용 guided 진행 로직(user-confirm advance), Chrome 전용 notice 문구, 모바일 fallback 문구
- compileJava/compileTestJava/FSS tests/bootJar 실행, visual QA(375/390/430/768/1440)
- docs/API.md에 agent 엔드포인트 문서(Phase 1 최종 형태 확정 후), commit/push/PR

## 5. TEST / VISUAL QA 결과

- compileJava: **FAIL**(위 단일 오류 1건, 나머지 파일은 통과는 미확인)
- compileTestJava / FSS tests / bootJar: **NOT RUN**
- VISUAL QA 375/390/430/768/1440: **NOT RUN**
- PRODUCTION WRITE: **NONE** / 실제 금감원 민원 제출: **NONE** / 실제 개인정보 입력·인증 수행: **NONE**

## 6. ACADEMY FIRST ACTION (필수 순서)

1. 학원 PC(`C:\Users\VIEW LIFE\...`)에서 새 클론 또는 기존 feel2real-buildit에서:
   `git fetch origin --prune`
2. `feat/newstart-fss-guided-agent-phase1` checkout(이 handoff 문서 커밋이 있는 브랜치).
   단 §2의 미커밋 코드는 HOME PC에만 존재 — 학원은 **문서 기준으로 C 구현을 새로 시작**하거나, HOME이 다음 연결 때 push하기 전까지 이 문서를 구현 스펙으로 사용.
3. 실제 사용자 관점 E2E: PC Chrome → NewStart(test) → 금감원 공식사이트. 사용자가 직접 개인정보 입력·본인인증(Agent/Agent 도구는 개입·기록 금지).
4. **인증 성공 직후 STEP 8~14 실사용 화면 조사**: 각 단계 URL·title·heading·button 텍스트·label·radio/select 구조·다음 동작만 기록. **개인정보 value·주민번호·인증번호·비밀번호·인증서 정보 절대 기록 금지.**
5. 기존 14단계 매뉴얼과 비교 → workflow/안내문 수정 → 1~14 guided QA → **최종 제출 직전 STOP(실제 제출 금지).**

## 7. Phase 2 (기록용 — 이번 구현 금지)

NewStart Browser Assistant = Chrome Extension/Bridge 하나:
- 사용자의 실제 Chrome에서 금감원 탭의 DOM 구조(값 제외) 감지 → 현재 STEP 자동 판정(`FssStepDetector` 재사용) → 버튼 highlight → 허가된 안전 navigation/click 보조(`FssAgentAction` DSL 재사용)
- 수집 금지: field value, password, OTP, 인증서 비밀번호
- FSS → Credit4U → 새출발기금 신청 → 진행상태 → 전자소송 5개 workflow 공용
- Octop/Anybridge 등 서드파티 원격브리지 설치 금지(자체 확장으로 설계)

## 8. 보안·범위 규칙(학원 세션에도 승계)

- 서버에 사용자 브라우저 세션/쿠키 보관 금지(Phase 1). 로그는 userId+단계 label만.
- 실민원 제출/실개인정보 입력/인증 수행을 Agent 경로로 절대 금지. STEP 8~14 추측 자동화 금지.
- 손대지 않는 것: Credit4U·신청·결과조회·전자소송 Agent, 최신 업데이트/FAQ, EASY, CHOONGGI.
- MERGE 금지 / DEPLOY 금지(운영 배포는 사용자 수동 승인 후 GitHub Actions).

## 9. COMMIT / PUSH / PR

- COMMIT: (이 문서의 커밋 sha — 아래 push 실행 후 git log에서 확인)
- PUSH: (실행 결과 기록)
- PR: **NO** (미완료 코드 없음, 문서 전용 푸시)
- HOME worktree의 A 코드: **미커밋 보존** (reset/clean/stash 금지 규칙 준수)

관련: `docs/newstart/NEWSTART_FSS_COMPLAINT_HELPER_V1_CHECKPOINT1_2026-09-23.md`(V1 도우미),
`fss-helper.html`의 14단계 manual(`newstart/fss/fss_complaint_sources.json` siteGuide, 원문 verbatim).
