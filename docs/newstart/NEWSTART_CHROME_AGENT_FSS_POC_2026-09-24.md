# NEWSTART CHROME AGENT — FSS EXTENSION PHASE 2 POC (2026-09-24)

- Worktree: `C:\Users\VIEW LIFE\feel2real-wt-newstart-chrome-agent`
- Branch: `feat/newstart-chrome-agent-fss-poc` (base `7719646c` = PR #400 head; **PR #400 미수정**)
- Scope: STEP 1~4만 (§7). 서버 Playwright/CDP/Qoder runtime 의존 **없음** (§3/§17/§24).

## 1. 아키텍처

Chrome Manifest V3 unpacked extension. 사용자 실제 Chrome 안에서만 동작:

```
FSS 페이지(www.fss.or.kr)
  └ content script: fssStepDetector.js(collectFacts→classify) + fssContent.js
        ├ shadow-DOM 우하단 미니 패널(안내문 표시만, 조작 없음)
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
  버튼/링크 텍스트(≤16자), radio name/value(선택지 정의 속성), hidden input **name만**,
  알려진 id(`#popup-alert`)의 **존재 boolean**.
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
| 4 | `forInsertAgre.do` + hidden 3종(`agreechkYn`/`sinfoSndYn`/`agreechkRgnoYn`) + substate(`#popup-alert` 존재 시 POPUP_ALERT) | handoff §3 STEP4 |
| 기타 | UNKNOWN → "현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요." | §9 |

우회 신호 검증: URL 단독 판별 금지 — control(radio/hidden/button/id)와 URL이 동시 일치해야 확정, 부분 일치이면 UNKNOWN.
합성 selector 없음(금지된 selector 발명 §8): 위 name/id/text는 전부 2026-09-24 POC/ACADEMY 실측 기록에 존재하는 것뿐.

## 5. 실제 Chrome 테스트 (§16~§18) — RESULT

(아래는 사용자 수동 테스트 완료 후 실측으로 채워짐. Qoder는 QA 관찰자; 결과 생성 대행 없음 — §17.)

- 설치: chrome://extensions → 개발자 모드 → 압축 해제된 확장 프로그램 로드 → `browser-extension/newstart-chrome-agent/`
- STEP 1~4 판별: [PENDING — 사용자 수동 진행 후 기록]
- 패널 자동 갱신(내비게이션/form submit/모달): [PENDING]
- UNKNOWN fallback: [PENDING]
- NewStart 연결 배지: [PENDING]
- Privacy QA (DevTools/network/mailstone 관찰): 전송 관측 = `FSS_STEP{step, substate, evidence라벨, version}` + `GET_STATUS`뿐, PII=0/credential=0/cookie=0/session=0 확인: [PENDING]

## 6. 성공/실패와 한계 (§23/§24)

잘 알려진 한계 (코드·문서에 정직하게 기록):
1. **STEP 2 관측 불가** — 네이티브 alert는 DOM이 없어 content script 구조상 감지 불가. 대안은 사용자 안내 문구뿐 (PoC 발견 사항; CRX 배지 등 우회 수단은 범위 밖으로 미구현).
2. FSS 화면이 `document.write`/iframe/frameset 구조라면 top-frame detect만 동작 (Phase 2 PoC에서 실측 확인 필요).
3. 세션 바인딩으로 인증 이후 화면 재접근 시 /cmmn/error.jsp — 확장은 그 화면을 UNKNOWN으로만 표시 (추측 자동화 없음).
4. jQuery validation이 synthetic event를 무시하는 것과 무관하게, 확장은 이벤트를 발생시키지 않으므로 해당 리스크 없음 (§12).

## 7. 향후 STEP 5~14 확장 계획

- STEP 5~8: ACADEMY §10 실측(forInsertAgre.do 단일 폼 내 rsltReceiveYn/rsltRplyMthdDiv 등) 근거로 control 시그니처 추가 가능 — 단 value 미열람 원칙 유지.
- STEP 9~14: 인증 이후 화면은 **사용자 실제 민원 진행 중에만** 관측 — 임의 접근 금지, 매뉴얼 기준 문구 + "현재 화면과 다를 수 있습니다" 유지.
- 최종 제출 단계: 어떤 UI에도 제출 대행·하이라이트 클릭 자동화 절대 금지 (§12).

## 8. Chrome Web Store 준비도

- 미준비 (의도적): unpacked만. 준비 시 필요 작업 — 아이콘 세트, 60자 description 검토, privacy policy(데이터 미수집 선언), Justification for host permissions, 단일 purpose 제출 문서. **이번 PoC에서 store 제출 금지 (§26).**

## 9. 다음 권고

1. STEP 1~4 live 판별이 통과하면 STEP 5~8로 detector 근거 확장(ACADEMY §10 기반, 값 미열람 유지).
2. NewStart 연결 배지는 PR #400 머지 후 production URL에서 재확인.
3. 크롬 확장 없이도 fallback(Phase 1 Guided)이 유지되므로 출시 리스크 낮음 — 단계적 rollout 가능.
