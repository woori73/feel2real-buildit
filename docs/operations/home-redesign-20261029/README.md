# 홈페이지 개편 — 2026-10-29 (메뉴 정리 · 영상 위로 · 콘텐츠 3개 · 게임 사이트 안 실행)

기준 main: `f5cc36c` (PR #927 병합). 운영 DB·Studio 승인 포인터·환경변수·신청/계좌 플래그는 바꾸지 않았다. 새 유료 AI 호출 없음. 2026-10-29 23:10 사용자 배포 승인 → PR #930 병합(`315e8db`) → 수동 `deploy-ec2.yml`(target=new, 나머지 스위치 unchanged) [run 40888789291](https://github.com/woori73/feel2real-buildit/actions/runs/40888789291) 성공.

## 바뀐 화면

| 영역 | 이전 | 이후 |
| --- | --- | --- |
| 상단 메뉴 | 로고 옆 `BuildIt`, 예상문제(같은 페이지 앵커), 대시보드, 이지장부, 모의고사위키, 학원 홈페이지 제작, AI AGENT 제작협업실, 내신 제작소, AI 콘텐츠 소개 + 숨은 모바일 바로가기 막대 | 문법 문제은행 · 모의고사위키 · 내신 제작소 ▾ · AI AGENT 제작협업실 · 더보기 ▾(대시보드·이지장부·학원 홈페이지 제작·BUILD IT CHART). PC 메뉴와 모바일 서랍의 링크 목록이 같다(계약 테스트로 고정). 링크 주소는 하나도 빠지지 않음 |
| 첫 화면 | 상품 안내 + 캐릭터 무대 + 같은 답을 내는 버튼 6개 + 대화창 | 상품 한 줄(2주 동안 160문항 · 20,000원) + 문의·신청 버튼 → 바로 아래 20초 홍보 영상 |
| 콘텐츠 | 게임 링크(새 탭) → 트레일러 준비 중 → … → 맨 아래 차트 | 영상 아래 버튼 3개(AI AGENT 트레일러 · 고1 10월 영어 복습 게임 · BuildIt 차트 소개) → 같은 순서로 세 섹션 |
| 트레일러 | Studio 승인 별칭(운영 404, "준비 중") | 사용자가 최종본으로 준 `preview_v16i_web.mp4`를 번들 파일로 재생(클릭 재생, 영상 12초 장면을 표지로 사용) |
| 복습 게임 | Nimble 새 탭 | "게임 시작"을 누르면 Feel2Real 안(`/games/g1-oct-review/v1/`)에서 실행. Nimble 링크는 실패 대비로만 남김 |
| AI 비서 | 큰 무대·궤도 그림·영상 2개 상시 재생·첫 인사 버튼 4개·"처음 안내 다시 보기" | 64px 얼굴(평소 사진, 캐릭터를 고를 때만 인사 영상) + 서수니/세븐 선택 + 대화 + 입력창·보내기. 첫 인사는 한 문장. 질문 판별·안내 규칙과 로그인 사용자 대시보드 연결은 그대로 |
| 그 밖의 섹션 | 맞춤 제작 과정·샘플·사례·블로그·문의·이용료 | 내용 그대로, 비서 아래에 둠 |

홍보 영상: 화면에 절반 이상 보이면 소리 없이 한 번 재생(기존 규칙). 휴대폰 데이터 절약 모드에서는 자동재생하지 않는다(재생 버튼은 그대로). 재생 속도 1.0배.

## 복습 게임 사이트 안 실행

- Nimble 페이지는 `X-Frame-Options: DENY`·`frame-ancestors 'none'`(실행 주소는 `'self'`)이라 iframe으로 넣을 수 없다(브라우저 차단 확인). 그래서 빌드를 Feel2Real에서 직접 제공한다.
- 원본: 사용자가 준 Unity 6000.4.11f1 프로젝트(`source/g1-oct-review-unity-6000.4.11f1.zip`, sha256 `035b5198…3520`). 이 zip은 프로젝트 원본이라 그대로 실행되지 않으므로, 같은 프로젝트의 공개 Nimble 빌드(version `844db24e-0f1e-4f1c-9922-cdbd41f342e6`) 4개 파일을 로그인 없이 받아 썼다. 빌드 안에 원본의 저장 키 `F2R_G1_OCT_REVIEW_v1`, 저장 플러그인 `F2R_StorageSave`가 들어 있어 같은 프로젝트임을 확인했다.
- 파일은 brotli로 미리 압축해 classpath `games/`(정적 경로 밖)에 둔다. 원본 45.9MB → 전송 10.4MB. 버전 경로라 1년 캐시(`immutable`), 두 번째 실행부터는 다시 받지 않는다. 시작 페이지는 원본 프로젝트의 `F2RAcademy` WebGL 템플릿과 같은 흐름이다.
- 정해진 4개 파일 이름만 응답(그 밖 404), br 미지원 브라우저는 406. 게임 페이지는 CSP로 같은 출처 파일만 쓴다(외부 통신 없음). iframe은 저장(XP·오답 노트, localStorage)을 위해 `allow-same-origin`을 주고, 상단 이동·폼 제출 권한은 주지 않는다. 게임 코드는 저장 플러그인 외에 브라우저 API를 쓰지 않는다(원본 `Assets/Plugins/WebGL/F2RBrowser.jslib` 확인).
- 아이폰 Safari는 iframe 전체 화면이 안 되므로 "전체 화면"은 새 창으로 연다. "게임 닫기"는 iframe을 지워 메모리를 돌려준다.
- Nimble 원본 프로젝트·공개 주소는 그대로 둔다.

| 파일 | 원본 크기 | br 크기 | 원본 sha256 |
| --- | ---: | ---: | --- |
| WebGLBuild.data | 10,052,770 | 3,962,793 | `7162e6f2…5a5b` |
| WebGLBuild.wasm | 35,397,716 | 6,380,330 | `4c88d98e…b26a` |
| WebGLBuild.framework.js | 447,030 | 77,288 | `838b4fff…9dee` |
| WebGLBuild.loader.js | 19,430 | 6,167 | `67b95e21…e0d5` |

## 파일 보존

| 파일 | sha256 | 비고 |
| --- | --- | --- |
| `video/landing/f2r-exam-remotion-v3-20261029.mp4` | `09a62f56…e0af` | 변경 없음 |
| `img/agents/seven/portrait-20261029.png` | `7896e8c4…e402` | 변경 없음 |
| `images/ai-agent-banner.png` | `e535c2d3…f5ef` | 변경 없음(제작협업실 링크 배너로 유지) |
| `video/landing/ai-agent-trailer-v16i-20261029.mp4` | `2c9fb23c…2a99` | 새 파일, 사용자 최종본 바이트 그대로(1280×720, 54.9초, 9.5MB) |
| `img/landing/ai-agent-trailer-v16i-poster.jpg` | `2d0008f7…7b6e` | 새 파일, 위 영상 12초 장면 정지 화면(생성 아님) |

Studio 트레일러 별칭(`/api/landing/videos/home-agent-trailer/stream`)과 승인 검사는 지우지 않았다. `app.home.agent-trailer-path`를 빈 값으로 두면 홈은 다시 그 별칭을 쓴다.

> 2026-10-30 변경(사용자 지시): 트레일러 영상은 그대로 두고, 영상 아래 AI AGENT 배너(화보)만 홈에서 내렸다. 배너 파일은 지우지 않았고 제작협업실은 상단 메뉴로 들어간다.
> 같은 날 2주·160문항·20,000원 안내를 위(상품 안내)와 아래(무통장입금 안내)에 한 번씩만 남겼다. 영상 아래 반복 문구·중복 신청 버튼과 결제 안내의 큰 가격 칸을 뺐다.

삭제: 홈 전용이던 `css/f2r_home_compact.css`, `js/f2r_home_presentation.js`(새 구조에서 쓰이지 않음). `e2e/pr914-production/public.spec.cjs`는 PR914 배포 당시 화면을 검사하는 수동 실행 스크립트라 손대지 않았다(이번 홈에는 맞지 않음).

## 검증 (로컬, 운영 아님)

- Java: 랜딩 계약·컨트롤러·게임·트레일러 테스트 41개 통과(`LandingEdunovaTemplateContractTest`, `LandingPageControllerTest`, `HomeReviewGameControllerTest`, `HomeAgentTrailer*`).
- JS 단위: 홈 관련 9개 파일 통과. 전체 `src/test/js`의 실패 41개는 main(`f5cc36c`)에서도 똑같이 실패하는 기존 실패이며 이번 변경과 무관(목록 비교로 확인).
- Chromium(로컬 MySQL + 실제 JAR): 홈 e2e 47개 통과 — 360·390·430·768·1440px, 대체 글꼴·Pretendard 각각. 가로 넘침 0, 메뉴, 섹션 순서, 콘텐츠 버튼 이동, 비서 입력·응답·카톡/이메일 링크, 홍보 영상 실제 재생·소리·실패 대체, 트레일러 최종본 해시·Range·재생/정지·실패 대체, 게임 실제 로딩·닫기·외부 요청 없음.
- 게임 직접 플레이: 지문 선택 → 미션 시작 → 문제(타이머) → 답 제출 → 정답·해설·XP → 저장(localStorage 기록) 확인. 10문항을 끝낸 결과 화면까지는 확인하지 않았다.

## 화면

[PC 첫 화면](screens/1440-home.png) · [모바일 첫 화면](screens/390-home.png) · [모바일 트레일러](screens/390-trailer.png) · [모바일 게임](screens/390-game.png) · [PC 게임](screens/1440-game.png) · [게임 정답·해설](screens/game-answer-explanation.png) · [모바일 AI 비서](screens/390-assistant.png)

## 운영 확인 (배포 직후, feel2real.co.kr)

- 메뉴 5개(문법 문제은행·모의고사위키·내신 제작소·AI AGENT 제작협업실·더보기), 섹션 순서(상품→영상→콘텐츠 버튼→트레일러→게임→차트→비서→기존 안내), 가로 넘침 0 — 390px·1440px.
- 홍보 영상 무음 자동재생, 트레일러 최종본 sha256 `2c9fb23c…2a99` 일치, 서수니 사진(`/agent-media/public/suni/poster.jpg`) 표시.
- 게임: 앞단을 거쳐도 `Content-Encoding: br`·`application/wasm`·1년 캐시 유지. "게임 시작" 후 6~7초에 로딩 완료, 오류·페이지 오류 0.

## 남은 일
- Nimble 이용 조건상 빌드 재배포 허용 여부는 사용자 계정에서 확인 필요(빌드는 사용자 소유 게임).

## 후속: 휴대폰 게임 화면 전체 열기 (2026-10-30)

게임은 휴대폰 세로 화면을 가로 900칸 기준으로 그려 화면 폭에 맞춰 줄인다(`F2RApp.Relayout`, `referenceResolution 900×1600`). 그래서 카드 안(약 330~350px)에서는 글자가 원래의 약 37~39%로 작아졌다. 휴대폰(폭 760px 이하)에서 "게임 시작"을 누르면 여백·테두리 없이 화면 전체(390px 폭이면 390px)로 열고, 안드로이드처럼 지원되면 브라우저 전체 화면까지 켠다. 아이폰 Safari처럼 지원하지 않으면 화면 전체 레이어로 연다. 닫으면 전체 화면과 뒤 페이지 스크롤을 원래대로 돌린다. 글자는 약 15~20% 커진다. 더 키우려면 게임 원본의 세로 기준 폭(900)을 줄여 다시 빌드해야 한다.

[휴대폰 화면 전체 게임](screens/390-game-fullscreen.png)
