# NewStart Chrome Agent — FSS Phase 2 PoC (Manifest V3)

NewStart가 개발한 **읽기·판별·안내 전용** Chrome 확장 Prototype (v0.3.0).
사용자의 실제 Chrome에서 금융감독원(FSS) 민원 화면의 **구조적 DOM 사실만**으로
현재 단계를 판별하고, 화면 **상단 중앙** 플로팅 어시스턴트 카드로 다음 절차를 안내한다.

> 이 확장은 **어떤 자동 조작도 하지 않는다**: 클릭·체크·폼 입력·인증·최종 제출 대행 없음.
> Qoder / Playwright / Selenium / CDP 디버깅 포트 없이 **일반 Chrome에서만** 동작한다.

## 설치 (unpacked, 개발자 모드)

1. `chrome://extensions` 열기 → 우상단 **개발자 모드** 켜기
2. **압축 해제된 확장 프로그램 로드** … → 이 폴더
   `browser-extension/newstart-chrome-agent/` 선택
3. NewStart FSS 화면(`feel2real.co.kr/newstart/fss-helper`)에서
   배지가 `Chrome Agent 연결됨`으로 바뀌면 설치 성공

## 권한과 그 사유 (최소 권한 원칙)

| 항목 | 값 | 사유 |
|---|---|---|
| `permissions` | `storage` | 마지막 판별 단계·근거 카테고리·버전만 로컬 저장(단계 번호 외 개인정보성 데이터 없음) |
| `host_permissions` | `https://feel2real.co.kr/*` | NewStart 페이지에서 연결 상태 배지를 갱신하기 위한 content script 주입 |
| `host_permissions` | `https://www.fss.or.kr/*` | 금감원 민원 화면(조사 실측 origin)의 구조 사실 판별 content script 주입 |
| `content_scripts` | 위 두 origin 한정 | `<all_urls>` 미사용 |
| `content_scripts`(dev) | `http://localhost(:port)/newstart*`, `http://127.0.0.1:*/newstart*` | §13 연결 배지 QA용: Phase 1 PR #400이 아직 unmerged라 production feel2real.co.kr에 fss-helper 화면이 없음 → 로컬 bootJar 빌드에서 배지 확인. **host_permissions에는 추가하지 않음** (주입+자체 messaging에만 필요, 크로스오린 요청 불가) |

**미사용 권한**: cookies, history, downloads, clipboardRead, webRequest, debugger, tabs, host 권한 외 전부.

## 개인정보 보호 경계 (절대 원칙)

- 읽지·전송하지 않음: 성명·주민등록번호·생년월일·전화·주소·이메일·금융회사 입력값·
  민원 본문 입력값·공동인증서 정보/비밀번호·OTP·휴대폰 인증번호·iPIN·쿠키·세션 토큰.
- 판별은 **구조적 DOM**만으로: URL path, 페이지 title, heading/legend 텍스트,
  버튼/링크 텍스트, radio의 name/value **선택지 정의 속성**, hidden input의 **name**,
  알려진 요소 id(`#popup-alert`)의 **존재 여부**.
- 텍스트 계열 `input`의 `value`, `textarea`, `password` 필드는 **조회조차 하지 않는다**
  (`collectFacts`가 해당 속성을 읽지 않도록 구현 + 단위 테스트로 강제).
- 서버 전송 자체가 없음: 백그라운드로 가는 메시지는 `{step, substate, evidence 카테고리, version}`.
  evidence 카테고리는 `URL:…`, `RADIO:minwonChk(Y,N)` 류의 라벨이지 화면 내용이 아니다.

## 동작 / 파일

- `manifest.json` — MV3, 위 권한.
- `src/background.js` — service worker. 마지막 판별 상태를 `chrome.storage`에 보관,
  NewStart content script의 `GET_STATUS` 응답, FSS content script의 `FSS_STEP` 수신.
- `src/fssStepDetector.js` — `collectFacts()`(DOM→구조 사실) + 순수 `classify()`(STEP 판별) + `GUIDE`.
- `src/fssContent.js` — FSS 페이지 로드·DOM 변경(MutationObserver/debounce)마다 재판별,
  그림자 DOM 상단 중앙 카드(top:24px, left:50% translateX, 폭 450px·모바일 상단 full-width,
  비차단 — 모달 오버레이 아님)로 안내. 입력값 미열람.
  단계 표기는 **두 체계 분리**: 금감원 자체 4단계("금감원 1단계 · 개인정보동의", 화면 타이틀 실측) 우선,
  NewStart 내부 1~14 흐름(STEP N)은 보조 표기.
- `src/newstartContent.js` — NewStart 페이지 배지 갱신 + 최소 postMessage 응답 계약.

## STEP 커버리지 (PoC 범위: 1~4)

| STEP | 판별 근거(2026-09-24 실측 evidence) |
|---|---|
| 1 | `/fss/minwon/search.jsp` + `[신청]` 버튼 + 제목 `민원신청` |
| 2 | **관측 불가(의도)**: `[신청]` 클릭 시 네이티브 `alert()`라 DOM이 없음 → search.jsp에 STEP 1으로 머무르며 GUIDE에 [확인] 처리 안내 (PoC 발견 사항) |
| 3 | `forInsertSelect.do` + `minwonChk` Y/N radio + `[다음]` |
| 4 | `forInsertAgre.do` + 동의 3종 — **v0.3.0 실측 교정: 실제 DOM은 `agreechkYn`·`sinfoSndYn`·`agreechkRgnoYn` Y/N `radio`** (v0.2.0까지 hidden으로 가정해 UNKNOWN 오검출). hidden 3종 also 인정. `#popup-alert` 존재 시 substate=`POPUP_ALERT`. 타이틀 `1단계 개인정보동의` → fssStage 1 표시 |
| 5 | `forInsertStep1.do`(실측: `/fss/cvpl/ctrbsUnlawFncdl/…`) + 구조 신호(타이틀 `2단계 개인정보입력` 또는 본인인증/실명인증/개인정보입력 문구, radio 선택지 정의) → **인식·안내 전용**: 직접 입력·직접 인증 고지. 자동 입력/클릭/다음/인증/제출 대행 일절 없음 |
| 6~14 | PoC 범위 밖 (수동 안내 / Phase 1 Guided Agent fallback) |

> 2026-09-24 실제 Chrome QA에서 `.do` 화면들의 실제 prefix는 `/fss/minwon/`이 아니라
> `/fss/cvpl/ctrbsUnlawFncdl/`임이 확인됐다. 판별은 파일명(`forInsertSelect.do` 등) 기준으로
> 두 prefix 모두 호환된다.
> 같은 QA에서 `[신청]→[확인]` 후 `search.jsp`에서 **바로 `forInsertAgre.do`(금감원 1단계)로 진입**하는
> 경로가 실측됐다(`forInsertSelect.do` 경유 없음, `minwonChk`는 agre 화면에서 hidden으로 옴) —
> 두 경로 모두 판별된다. 금감원 진행nav 3·4단계(민원정보입력·민원등록) 화면은 구조 실측이
> 아직 없어 **의도적으로 미매핑(UNKNOWN)** — 임의 selector 발명 금지(§8).

확인 불가 시 `UNKNOWN` → 패널 문구:
`현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요.`

## 테스트

```
node --test browser-extension/newstart-chrome-agent/test/detector.test.mjs browser-extension/newstart-chrome-agent/test/panel.contract.test.mjs
```

fixture은 장난감 사실 팩만 사용(실제 개인정보 HTML/캡처 금지).
`panel.contract.test.mjs`는 소스 정적 계약: 상단 중앙 카드 좌표, 모달 오버레이 금지,
접기/펼치기, 단계 체계 분리, 그리고 4개 스크립트 전체에서 `.click()`/`.submit(`/
`dispatchEvent`/`value` 대입/`checked` 조작/`window.open`/`eval` 부재.

## 금지된 것 (구현하지 않음)

`eval`, 원격 실행 JavaScript, 외부 호스팅 코드, 자격증명 가로채기, 키스트로크 로깅,
비밀번호 필드 리스너, 쿠키 export, 브라우저 profile 복사, CDP 노출, 임의 domain DOM 접근.
MV3 CSP 준수.
