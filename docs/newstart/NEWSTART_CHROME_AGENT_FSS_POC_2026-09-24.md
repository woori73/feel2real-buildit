# NEWSTART CHROME AGENT — FSS EXTENSION PHASE 2 POC (2026-09-24)

- Worktree: `C:\Users\VIEW LIFE\feel2real-wt-newstart-chrome-agent`
- Branch: `feat/newstart-chrome-agent-fss-poc` (base `7719646c` = PR #400 head; **PR #400 미수정**)
- Scope: STEP 1~4만 (§7). 서버 Playwright/CDP/Qoder runtime 의존 **없음** (§3/§17/§24).

## 1. 아키텍처

Chrome Manifest V3 unpacked extension. 사용자 실제 Chrome 안에서만 동작:

```
FSS 페이지(www.fss.or.kr)
  └ content script: fssStepDetector.js(collectFacts→classify) + fssContent.js
        ├ shadow-DOM 상단 중앙 플로팅 카드(top:24px·450px·비차단, 모달 아님; 안내문 표시만, 조작 없음)
        └ chrome.runtime.sendMessage {FSS_STEP: step번호/substate/evidence카테고리/version}
              └ background service worker → chrome.storage(마지막 판별 1건, 10분 TTL)
NewStart 페이지(feel2real.co.kr/newstart* 또는 로컬 /newstart*)
  └ content script: newstartContent.js → GET_STATUS → 배지 "Chrome Agent 연결됨"/기본 "미설치"
```

- 서버와의 통신: **전무** (fetch/XHR/WebSocket 어떤 것도 없음).
- Phase 1 Guided Agent(서버 워크플로)는 그대로 fallback — 확장 미설치 사용자에게 영향 없음.

## 2. Manifest 권한과 사유 (§5 최소 권한)

| 권한 | 값 | 사유 |
|---|---|---|
| permissions | `storage` | 마지막 단계 번호·근거 카테고리·버전의 로컬 보관뿐 (개인정보 무관) |
| host_permissions | `https://feel2real.co.kr/*` | NewStart 배지 content script |
| host_permissions | `https://www.fss.or.kr/*` | FSS 화면 구조 판별 content script (POC 실측 origin) |
| content_scripts만 | `http://localhost(:port)/newstart*`, `127.0.0.1` | §13 배지 QA: PR #400 unmerged → 로컬 빌드에서 확인. host_permissions 미추가(주입+자체 messaging에만 필요) |

미사용: `<all_urls>`, cookies, history, downloads, clipboardRead/Write(webRead 계열), webRequest, debugger, tabs.

## 3. 개인정보 보호 경계 (§6 절대 원칙)

- 판별 입력: **구조적 DOM만** — URL path(소문자화), document.title, heading/legend/caption 텍스트,
  버튼/링크 텍스트(≤16자), label 텍스트(≤30자), `N단계` 진행nav 텍스트, radio name/value(선택지 정의 속성),
  hidden input **name만**, 알려진 id(`#popup-alert`)의 **존재 boolean**.
- 텍스트/비밀번호 `input`의 `value`, `textarea` content, `checked`/`selected` 상태, 쿼리스트링,
  cookie, localStorage, 인증 팝업(`certificationPop.do`) 내부: **미열람** (수집 함수 자체가 조회하지 않음 + 단위 테스트로 회귀 방지).
- 전송 payload: `{step, substate, evidence 카테고리 라벨 ≤6, version}` — 화면 텍스트 원문은 어떤 메시지에도 포함되지 않음.
- 인증 팝업(new window)에도 content script가 들어가지만 UNKNOWN fallback만 표시 (별도 근거 없음).

## 4. STEP detector (§7~§9, 실측 evidence만 근거)

| STEP | 판별 | 근거 출처 |
|---|---|---|
| 1 | `/fss/minwon/search.jsp` + `[신청]` + title/heading `민원신청` | handoff §3 STEP1 + §10 ACADEMY |
| 2 | **의도적 미판별**: `[신청]`→네이티브 `alert()`(DOM 부재) → STEP 1 유지, GUIDE에 [확인] 안내 포함 | handoff §3 STEP2 |
| 3 | `forInsertSelect.do` + `minwonChk` Y/N + `[다음]` | handoff §3 STEP3 + §10 |
| 4 | `forInsertAgre.do` + 동의 3종 — **v0.3.0 실측 교정: 실제 DOM은 Y/N `radio`(`agreechkYn`/`sinfoSndYn`/`agreechkRgnoYn`)**, hidden 3종도 인정. substate(`#popup-alert` 존재 시 POPUP_ALERT). 타이틀 `1단계 개인정보동의` → fssStage(1) 병행 표시 | handoff §3 STEP4(가정) → 2026-09-24 2차 QA CDP 읽기전용 probe 실측으로 radio 확정 |
| 5 (v0.2.0, v0.3.0 강화) | `forInsertStep1.do` 파일명 + 구조 신호(타이틀 `2단계 개인정보입력` / 본인인증·실명인증·개인정보 입력 문구 / radio 선택지) → substate=`PII_INPUT`, **인식·직접 입력 안내만** — 자동 입력·클릭·제출 없음. 신호 약하면 UNKNOWN | 2026-09-24 REAL CHROME QA 실측 URL + probe |
| 기타 | UNKNOWN → "현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요." | §9 |

**금감원 자체 4단계 모델 (v0.3.0, 2026-09-24 실측)**: FSS 진행표시는 1단계 개인정보동의 / 2단계 개인정보입력 /
3단계 민원정보입력 / 4단계 민원등록이며, 좌측 nav가 4개 문구를 전부 항상 나열하므로 **nav만으로는 현재 단계를 못 정한다**.
현재 단계 식별 근거는 `document.title`("… | 1단계 개인정보동의 …" 형태) — `deriveFssStage()`가 title에서만 파싱.
NewStart 내부 1~14 흐름 번호와 **절대 혼용하지 않고 패널에서 분리 표시**(주: "금감원 N단계 · NAME", 보조: "NewStart 흐름 STEP N · 14단계 기준").
`fssStage`는 패널 표시용이며 background 전송 payload에는 포함하지 않는다(payload는 §3 그대로).

우회 신호 검증: URL 단독 판별 금지 — control(radio/hidden/button/id)와 URL이 동시 일치해야 확정, 부분 일치이면 UNKNOWN.
합성 selector 없음(금지된 selector 발명 §8): 위 name/id/text는 전부 2026-09-24 POC/ACADEMY 실측 기록에 존재하는 것뿐.

## 5. 실제 Chrome 테스트 (§16~§18) — RESULT

(아래는 사용자 수동 테스트 실측. Qoder는 QA 관찰자; 결과 생성 대행 없음 — §17.)

### 1차 QA (v0.1.0)
- 설치: chrome://extensions 개발자 모드 → 압축 해제 로드 — 성공
- §13 연결: `http://localhost:8081/newstart/fss-helper` 배지 → **"Chrome Agent 연결됨 (v0.1.0)"** — PASS
- STEP 1(search.jsp)→2(alert [확인])→3(forInsertSelect.do)→4(forInsertAgre.do) 자동 판별·panel 갱신: 사용자 보고 정상
- **발견 1 (실패 정직 기록)**: 개인정보 화면 실URL `https://www.fss.or.kr/fss/cvpl/ctrbsUnlawFncdl/forInsertStep1.do`
  → v0.1.0 매핑에 없음 → 패널 **"단계 자동 확인 실패 / 수동 안내"** 표시(= §9 동작 정상, 오인 SUCCESS 없음).
  단, 해당 화면이 STEP 5(개인정보 입력)임을 인식하지 못함. 매뉴얼/handoff §3의 `forInsertStep1.do`는
  `/fss/minwon/`이 아니라 `/fss/cvpl/ctrbsUnlawFncdl/` prefix를 쓴다는 것이 실측으로 확인됨.
- **발견 2 (UX)**: 우하단 고정 패널은 사용자가 안내를 발견·활용하기 어려움 → 우상단 고정으로 변경 요구.
- 사용자는 STEP 5에서 STOP: 개인정보 입력 0, 인증 0, 최종 제출 0.

### v0.2.0 수정 (같은 브랜치)
- detector: `/forinsertstep1.do` 파일명 매칭 + 구조 신호(본인인증 버튼 또는 radio/hidden 존재)로 STEP 5 `PII_INPUT` 인식,
  **인식·안내 전용**(직접 입력·직접 인증 고지, 자동 입력/클릭/다음/인증/제출 없음). 구조 신호 없으면 UNKNOWN 유지.
- 실제 `.do` prefix(`/fss/cvpl/ctrbsUnlawFncdl/`) 대응: 기존 STEP 3/4 판별은 파일명 substring이라 무영향(회귀 없음),
  test fixture도 실측 prefix로 갱신.
- UNKNOWN 진단 `path.slice(0,40)`이 `.do`를 잘라 매칭 누락으로 오인되던 표시 문제 → 60자로 완화.
- 패널: 데스크톱 우상단 고정(top:90px/right:20px), 좁은 화면(≤640px) 상단 full-width, 접기/펼치기 유지,
  시각 순위 1단계→2행동→3근거→4수동 안내, 실패 메시지 즉각 표시(빨강).
- targeted tests: `node --test test/detector.test.mjs test/panel.contract.test.mjs` → **22/22 pass**
  (전체 Gradle suite 재실행 전 extension targeted만 실행 — 지시대로)

### 2차 QA (v0.2.0) — 결과 (2026-09-24 사용자 수동 실측)
- 로드·§13 연결 배지(v0.2.0): PASS.
- **발견 1 (UX)**: 우상단 카드가 FSS 화면과 섞여 인식 곤란 → **상단 중앙 플로팅 카드** 재설계 요구.
- **발견 2 (판별 실패)**: 실제 `forInsertAgre.do`(금감원 1단계 개인정보동의)에서 패널 UNKNOWN
  "동의 항목 미확인". 사용자 지시: URL 하나로 기존 STEP 번호에 억지 맞춤 금지, AGENT 흐름과 FSS 자체 단계 분리,
  실제 DOM 근거와 결합, 자동 동의/입력/인증/클릭/submit 절대 금지.
- (추가 보고) 사용자가 이후 화면을 직접 진행하여 **민원등록 버튼(금감원 3·4단계 화면)까지 도달**,
  일부러 다른 정보로 입력 후 **등록은 하지 않음**(최종 제출 0). 단 해당 화면들의 구조 실측 기록이 없어
  3·4단계 매핑은 **의도적 보류(UNKNOWN 유지)** — §8 selector 발명 금지.

### ROOT CAUSE (발견 2)
v0.2.0 detector는 `forInsertAgre.do`에서 hidden input 3종(`agreechkYn` 등)을 요구했으나,
**실제 DOM은 Y/N radio 3종**이었다(hidden 3종 가정은 handoff §3 STEP4 기록 기반 — 실측과 상이).
읽기전용 CDP probe로 수집한 구조 사실(값 미열람, 확장 패널 subtree 제외)에서
radio name+선택지(Y/N), 타이틀 `1단계 개인정보동의`, `#minwon-popup`(있음)/`#popup-alert`(없음) 확인.
또한 `search.jsp` `[신청]→[확인]` 후 `forInsertSelect.do`를 **경유하지 않고 바로 `forInsertAgre.do`로 진입**하는
실경로 관측 — select 화면의 `minwonChk`는 agre 화면에서 hidden으로 전달됨. 두 경로 모두 판별 유지.

### v0.3.0 수정 (같은 브랜치)
- detector: 동의 3종을 **radio(Y/N) 또는 hidden** 모두 인정 + 타이틀 `N단계 NAME` 파싱(`deriveFssStage`, title 전용)
  → STEP 4 확정. STEP 5는 title `2단계 개인정보입력` 등 실측 신호 우선으로 강화. label/진행nav 텍스트 수집 추가(텍스트 원문 전송 없음).
- 패널: **상단 중앙 카드**(fixed top:24px, left:50% translateX(-50%), 450px, max-width calc(100vw-32px),
  ≤560px 상단 full-width), 금감원 카드와 구분되는 남색 브랜드 헤더 + accent border + 강한 shadow,
  **모달 오버레이 아님(비차단)**, 접으면 상단 중앙 bar "NewStart 안내 · STEP 라벨",
  단계 체계 분리 표시(주: 금감원 N단계 · NAME / 보조: NewStart 흐름 STEP N).
- 버전 0.3.0 (manifest + 3 scripts), payload 계약 불변(step/substate/evidence≤6/version — fssStage 미전송).
- targeted tests: `node --test test/detector.test.mjs test/panel.contract.test.mjs` → **27/27 pass**
  (실제 동의 DOM fixture 회귀, radio 값 읽기/변경 금지 트랩, click/submit/value 대입 정적 금지 스캔,
  패널 레이아웃/접기/단계분리 계약, STEP 1~4 regression, UNKNOWN fallback).

### 3차 QA (v0.3.0) — 재테스트 대기
- chrome://extensions ⟳ 후 배지 "Chrome Agent 연결됨 (v0.3.0)", 동의 화면에서 "금감원 1단계 · 개인정보동의" 확인,
  개인정보 입력 화면 "금감원 2단계", 상단 중앙 카드/접기 bar, 미지원 화면 UNKNOWN 회귀 확인.


## 6. 성공/실패와 한계 (§23/§24)

잘 알려진 한계 (코드·문서에 정직하게 기록):
1. **STEP 2 관측 불가** — 네이티브 alert는 DOM이 없어 content script 구조상 감지 불가. 대안은 사용자 안내 문구뿐 (PoC 발견 사항; CRX 배지 등 우회 수단은 범위 밖으로 미구현).
2. FSS 화면이 `document.write`/iframe/frameset 구조라면 top-frame detect만 동작 (Phase 2 PoC에서 실측 확인 필요).
3. 세션 바인딩으로 인증 이후 화면 재접근 시 /cmmn/error.jsp — 확장은 그 화면을 UNKNOWN으로만 표시 (추측 자동화 없음).
4. jQuery validation이 synthetic event를 무시하는 것과 무관하게, 확장은 이벤트를 발생시키지 않으므로 해당 리스크 없음 (§12).

## 7. 향후 STEP 6~14 확장 계획 (STEP 5는 v0.2.0에서 인식·안내 전용 추가 완료)

- STEP 5~8: ACADEMY §10 실측(forInsertAgre.do 단일 폼 내 rsltReceiveYn/rsltRplyMthdDiv 등) 근거로 control 시그니처 추가 가능 — 단 value 미열람 원칙 유지.
- STEP 9~14: 인증 이후 화면은 **사용자 실제 민원 진행 중에만** 관측 — 임의 접근 금지, 매뉴얼 기준 문구 + "현재 화면과 다를 수 있습니다" 유지.
- 최종 제출 단계: 어떤 UI에도 제출 대행·하이라이트 클릭 자동화 절대 금지 (§12).

## 8. Chrome Web Store 준비도

- 미준비 (의도적): unpacked만. 준비 시 필요 작업 — 아이콘 세트, 60자 description 검토, privacy policy(데이터 미수집 선언), Justification for host permissions, 단일 purpose 제출 문서. **이번 PoC에서 store 제출 금지 (§26).**

## 9. 다음 권고

1. STEP 1~4 live 판별이 통과하면 STEP 5~8로 detector 근거 확장(ACADEMY §10 기반, 값 미열람 유지).
2. NewStart 연결 배지는 PR #400 머지 후 production URL에서 재확인.
3. 크롬 확장 없이도 fallback(Phase 1 Guided)이 유지되므로 출시 리스크 낮음 — 단계적 rollout 가능.
