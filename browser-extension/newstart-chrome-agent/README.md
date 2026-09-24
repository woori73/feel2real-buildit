# NewStart Chrome Agent — FSS Phase 2 PoC (Manifest V3)

NewStart가 개발한 **읽기·판별·안내 전용** Chrome 확장 Prototype.
사용자의 실제 Chrome에서 금융감독원(FSS) 민원 화면의 **구조적 DOM 사실만**으로
현재 단계를 판별하고, 화면 우하단 미니 패널로 다음 절차를 안내한다.

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
  그림자 DOM(minimal) 우하단 패널로 안내. 입력값 미열람.
- `src/newstartContent.js` — NewStart 페이지 배지 갱신 + 최소 postMessage 응답 계약.

## STEP 커버리지 (PoC 범위: 1~4)

| STEP | 판별 근거(2026-09-24 실측 evidence) |
|---|---|
| 1 | `/fss/minwon/search.jsp` + `[신청]` 버튼 + 제목 `민원신청` |
| 2 | **관측 불가(의도)**: `[신청]` 클릭 시 네이티브 `alert()`라 DOM이 없음 → search.jsp에 STEP 1으로 머무르며 GUIDE에 [확인] 처리 안내 (PoC 발견 사항) |
| 3 | `forInsertSelect.do` + `minwonChk` Y/N radio + `[다음]` |
| 4 | `forInsertAgre.do` + hidden 3종(`agreechkYn`·`sinfoSndYn`·`agreechkRgnoYn`), `#popup-alert` 존재 시 substate=`POPUP_ALERT` |
| 5~14 | PoC 범위 밖 (수동 안내 / Phase 1 Guided Agent fallback) |

확인 불가 시 `UNKNOWN` → 패널 문구:
`현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요.`

## 테스트

```
node --test browser-extension/newstart-chrome-agent/test/detector.test.mjs
```

fixture은 장난감 사실 팩만 사용(실제 개인정보 HTML/캡처 금지).

## 금지된 것 (구현하지 않음)

`eval`, 원격 실행 JavaScript, 외부 호스팅 코드, 자격증명 가로채기, 키스트로크 로깅,
비밀번호 필드 리스너, 쿠키 export, 브라우저 profile 복사, CDP 노출, 임의 domain DOM 접근.
MV3 CSP 준수.
