# 지피지기 Agent — NAVER 공식 API 통합 실사 (2026-09-23)

조사 주체: NewStart 지피지기 Agent V1 (branch `feat/newstart-jipijigi-agent-v1`)
조사 방법: 네이버 개발자 센터 공식 문서 확인(cloud 문서가 아닌 developers.naver.com 원문),
카페 주소/수집 JSON 실측, 로컬 저장 코드 grep. **추측으로 "될 것 같다"를 쓰지 않는다.**

## 1. 결론 요약

| 항목 | 결론 | 근거 |
| --- | --- | --- |
| 카페 **글쓰기** 공식 API | **존재함 (OAuth 로그인 제품 산하)** | `POST https://openapi.naver.com/v1/cafe/{clubid}/menu/{menuid}/articles` |
| 카페 **가입** 공식 API | 존재 (공개·즉시가입 카페만) | `POST https://openapi.naver.com/v1/cafe/{clubid}/members` (payload `nickname`) |
| 이미지 첨부 | 지원 (`multipart/form-data`, `image` 키 반복으로 복수 첨부) | 카페 가입·글쓰기 API 명세 |
| **댓글 쓰기** API | **없음** (명세에 댓글 등록 엔드포인트 없음) | 동일 문서 |
| 카페글 **조회** | 쓰기 API와 별개의 Search API(검색 한정, X-Naver-Client-Id/Secret 헤더) | `docs/serviceapi/search/cafearticle` |
| 인증 방식 | 네이버 로그인 앱 등록 → Client ID/Secret → OAuth 2.0 → `Authorization: Bearer <access_token>` | 카페 튜토리얼 |
| V1 실행 범위 | **게시 미구현(의도적)**. 초안 승인까지만 서버에서 지원하고 게시는 운영자 수동 | §5 |

## 2. 공식 명세에서 확인한 사실

문서: [카페 가입 · 글쓰기 API 명세](https://developers.naver.com/docs/login/cafe-api/cafe-api.md),
[카페 튜토리얼](https://developers.naver.com/docs/login/cafe-tutorial/cafe-tutorial.md),
[검색 > 카페글](https://developers.naver.com/docs/serviceapi/search/cafearticle/cafearticle.md)

- 글쓰기 요청 파라미터: 경로 `clubid`, `menuid` + 본문 `subject`, `content`,
  그리고 문서에 명시된 플래그 `openyn`, `searchopen`, `replyyn`, `scrapyn`, `metoo`,
  `autosourcing`, `rclick`, `ccl`(각 플래그의 의미·기본값은 구현 전 문서를 재확인해야 한다).
- 제약(문서 원문 근거):
  - `<script>` 태그 사용 불가(서식 제한).
  - 문자열은 **UTF-8 → MS949 재인코딩** 처리가 필요하다(한글 깨짐 방지).
  - **상품게시판은 입력 불가.**
  - 가입 API는 "공개 및 바로 가입 가능한 카페만" 대상.
- 요청 한도/자동화 관련 문서는 호출 한도 수치나 처벌 조건을 명시하지 않는다 →
  운영 정책(네이버 서비스 약관/운영정책) 확인과 무인 대량 게시 금지가 전제다.
  **CAPTCHA·보안인증 우회는 어떤 경로로도 시도하지 않는다(프로젝트 절대 금지).**

## 3. 대상 카페 식별 (실측 값만)

| 값 | 확인 방법 | 상태 |
| --- | --- | --- |
| clubid `31312978` (새출발기금 부실차주 라이브러리) | 카페 주소/호출로 실제 clubid 확인 (2026-09-23 실측) | 확인 |
| menuid `1` 질문게시판 | 저장된 수집 article JSON의 `boardId` 필드 (docs/newstart/research) | 확인 |
| menuid `3` 부실차주 신청, `6` 부실우려, `13` 자유게시판, `22` 감면율예측게시판 | 동일 source | 확인 |
| menuid **"지피지기 agent" 전용 게시판** | 미확인 — 브라우저 패널 응답 실패로 실측 못함 | **운영자 확인 필요** |

→ 스키마는 이를 강제한다: `newstart_jipijigi_target_boards.menu_id`는 **NULL 허용**이고
NULL이면 `isPostable() == false`. 확인하지 않은 menuid를 코드에 하드코딩하지 않는다.

## 4. 게시 신원 정책

- 별도 AI 네이버 계정을 만들지 않는다. **게시 주체는 운영자(지피지기) 계정**이며,
  OAuth도 운영자 계정으로 발급한 토큰만 사용한다.
- 모든 게시 문단 하단에 고지를 강제한다:
  `이 글은 지피지기 Agent가 자료를 조사·정리하고, 카페 운영자 지피지기가 확인하여 게시합니다.`
  (`JipijigiDraftComposer.AI_DISCLAIMER`가 초안 생성 시점에 본문에 포함시킨다.)
- 토큰/Client Secret/Cookie/Session은 커밋·로그출력·DB 저장이 금지된다. 환경변수/시크릿 스토어만 사용.
  이번 PR에는 어떤 credential도 추가되지 않았다(`git status`/diff로 검증).
- 운영자의 로그인 세션을 복제하지 않는다(쿠키 재사용 방식 배제, 공식 OAuth만 검토).

## 5. V1 결정과 V2 로드맵

V1(이 PR):
1. 게시 API 클라이언트 **구현 없음**. `JipijigiWorkflowService.publish()`는 설정 플래그
   (`app.newstart.jipijigi.publish-enabled=false`)와 무관하게 항상 거부된다(이중 차단).
2. 화면의 [게시] 버튼은 409 `PUBLISH_DISABLED`를 띄우고, 운영자는 [원문 복사]로 직접 게시한다.
3. 게시 대상은 서버측 allowlist 테이블의 enabled 행만 인정한다(질문게시판 등 오게시 방지).

V2(별도 PR, 선행조건 순서):
1. 운영자가 "지피지기 agent" 게시판 menuid 실측 제공 → allowlist 등록·enabled.
2. 네이버 로그인 앱 등록 + 카페 글쓰기 API 권한 확인(clubid 기준 실제 응답 테스트는
   소량 1건, 운영자 입회 하에).
3. `NaverCafePostingClient`: Bearer 토큰 주입(env), subject/content MS949 재인코딩,
   `<script>` 금지 검증, multipart 이미지 첨부는 추후(지금은 이미지 업로드 요구 없음).
4. 실패 응답 article id/URL 저장(`published_url`), 재시도 멱등성(같은 초안 중복 게시 방지),
   게시 후 운영자 알림. 대량/자동 연발 게시는 금지하고 1초안 1게시 원칙 유지.

## 6. 미해결(운영자 확인 대기)

- "지피지기 agent" 게시판 menuid.
- `subject`/`content` 외 플래그의 카페별 기본값(글 공개/답글 허용/스크랩 허용 등) 정책 확정.
- 토큰 갱신 주기(네이버 액세스 토큰 만료 정책)와 재승인 UX.
- 카페 운영 정책상 AI 고지 문구 게재 허용 범위(운영자=카페 주인이 결재).
