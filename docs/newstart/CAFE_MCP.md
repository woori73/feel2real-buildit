# 개인워크아웃 카페 글쓰기 연결

카페 `31312978`의 운영자가 만든 개인워크아웃 게시판 7개에 정리한 자료와 이미지를 새 글로 올린다. 기존 회원 네이버 로그인과 지피지기 답변 승인 흐름은 변경하지 않는다. 공식 글쓰기 API의 create-only 구현이다. 공식 문서에는 게시판 생성과 기존 글 수정·삭제 API가 제공되지 않아 이 기능은 카페 화면에서 처리한다. 자동 수집·예약 발행은 제공하지 않는다. 발행 이력은 보존한다.

## 도구와 화면

- 기존 `/mcp`를 재사용한다. 새 `cafe:write`는 기본 미선택이며 ADMIN과 별도 민감 범위 동의가 필요하다. 기존 grant를 자동 확장하지 않는다.
- `cafe_status`: 연결 상태, 게시판별 menuId, 설정 화면 주소. `cafe_publication_history`: 본인 최근 100건. 둘은 읽기 전용이다.
- `publish_cafe_article({boardKey,title,content,idempotencyKey})`: 제목 150자·본문 8000자 이하 일반 텍스트/기본 HTML. 설정한 게시판에 멤버공개·검색 비허용으로 올린다. 임의 카페/URL/쿠키/토큰 인자는 없다.
- 선택 인자 `image1`부터 `image5`까지 호스트가 넣는 파일 객체 `{download_url,file_id,mime_type,file_name?}`를 받는다. `_meta["openai/fileParams"]`에 5개 인자를 등록한다. PNG/JPEG만 각 4 MiB·총 12 MiB·최대 5장, 한 변 12,000px·1,600만 화소 이내다. 파일 호스트/공인 IP/리다이렉트 검사는 기존 `McpFileFetcher`를 재사용하고 실제 이미지 해독·MIME 일치도 검사한다. 임의 본문 `<img src>`는 계속 제거한다.
- 이미지는 인자 번호 순서대로 공식 multipart `image` 파트로 첨부한다. 본문 중간 삽입 위치를 보장하는 API 계약은 없으므로 잡지식 배치는 카페 편집 화면에서 확인한다. 이미지의 실제 본문·순서가 중복 해시에 포함되며 같은 글에 다른 이미지를 보내는 것은 별도 내용이다. 같은 멱등 키로 이미지만 바꾸면 409다. 이미지 바이트와 내려받기 URL·원래 파일명은 DB/이력/로그에 보존하지 않는다. 원래 파일명 대신 `image-1.png` 같은 파일명을 전송한다.
- boardKey: getting-started, debt-reduction, collection, assets-income, repayment, cases, comparison.
- 근거와 기준일은 내부 자료에 보존하고, 독자에게는 확인된 규정과 사례의 차이를 자연스럽게 설명한다. 원글 번호·운영자 인용·수집 과정은 본문에 나열하지 않는다. 본문 미확보 검색목록을 확인된 사례처럼 게시하지 않는다. DB 자료를 자동 공개하지 않는다.
- `/admin/newstart/cafe`: 네이버 계정 연결·게시판 번호·등록 이력. `/admin/newstart`에서 진입할 수 있다.

## 운영 연결 순서

1. 검증한 PR을 병합·배포한다. 신규 빈 테이블 V1109은 작업 시작 main의 V1108 다음 번호다. 배포 전 실제 Flyway max와 최신 main의 번호 충돌을 확인한다. 적용된 V_파일은 변경하지 않는다.
2. 네이버 개발자센터에서 글쓰기 전용 앱을 등록한다. 기존 회원 로그인 앱은 변경하지 않는다. 네이버 로그인·카페 글쓰기 권한과 Callback URL `https://feel2real.co.kr/admin/newstart/cafe/callback`을 설정한다. 개발 상태에서는 운영자 테스트 계정을 등록하고, 공개 서비스로 전환할 경우 네이버 검수를 따른다.
3. 서버의 비밀 설정 경로에 아래 값들을 주입한다. 값·토큰을 채팅/GitHub/로그에 붙이지 않는다.

| 환경변수 | 의미 |
|---|---|
| NEWSTART_CAFE_ENABLED | 기본 false, 준비 후 true |
| NEWSTART_CAFE_NAVER_CLIENT_ID | 글쓰기용 앱 Client ID |
| NEWSTART_CAFE_NAVER_CLIENT_SECRET | 글쓰기용 앱 Client Secret |
| NEWSTART_CAFE_TOKEN_KEY_BASE64 | 별도 무작위 32바이트 키의 표준 Base64. 백업하고 교체 시 계정 재연결 |
| NEWSTART_CAFE_PUBLIC_BASE_URL | 기본 https://feel2real.co.kr |

4. 관리자 연결 화면에서 각 게시판 주소 `menus/{숫자}`의 번호를 입력하고 운영자 네이버 계정으로 직접 인증한다. 서로 다른 양의 정수 7개만 받는다. 이름과 실제 번호의 일치는 운영자가 확인한다.
5. 명시적 APP_MCP_ALLOWED_SCOPES override가 있으면 기존 항목을 보존하며 `cafe:write`를 추가한다. override가 없으면 운영 기본 목록에 포함된다. MCP 앱의 도구를 새로고침하고 재인증하여 카페 글 등록 범위를 선택한다. 기존 Feel2Real 연결을 재사용하므로 서버를 중복 등록할 필요는 없다.
6. cafe_status로 연결·번호 확인 후 요청받은 짧은 안내 글 한 건으로 실제 게시·한글·줄바꿈·멤버공개를 확인한다. 실제 외부 인증/게시 결과는 코드 테스트와 구분한다.

## 인증·오류·중복

- 토큰은 별도 키의 AES-256-GCM·계정 ID AAD로 암호화한다. OAuth state는 계정에 결합하고 10분 내 한 번만 소비한다. 연결 해제/재연결 도중 오래된 callback은 토큰을 복원하지 못한다.
- 관리자 폼 화면은 `Referrer-Policy: same-origin`으로 같은 출처 POST의 Origin 검증을 유지한다. 외부 사이트에는 referrer를 보내지 않으며, 인증 callback은 `no-referrer`로 code 유출을 막는다.
- 전송 전에 PENDING 이력을 커밋한다. `(owner_id,key_hash)`와 `(cafe_id,menu_id,payload_hash)` 유일 키로 동시 요청과 다른 키의 같은 글을 차단한다. 다른 내용의 같은 키는 409다. 텍스트만 있는 기존 해시는 그대로 유지한다. 첨부 이미지가 있으면 MIME·SHA-256을 순서대로 해시에 추가한다.
- 성공 articleId 확인 시만 POSTED. 타임아웃·응답 오류·성공 결과의 DB 저장 실패는 UNKNOWN이며 자동 재전송하지 않는다. 서버 중단으로 남은 PENDING도 실제 카페 확인이 필요하다. 자동 복구·재게시 도구는 없다.
- 운영자별 서울 날짜 하루 180회 상한으로 공식 계정당 200건에 여유를 둔다. 다른 앱의 동일 네이버 계정 사용량까지 합산한 보장은 아니다.
- 연결 해제는 로컬 토큰·대기 state를 제거하고 이력을 보존한다. 네이버 앱 권한 자체 철회는 네이버 연결된 서비스 관리에서 한다. 이미 전송 중인 글은 취소할 수 없다.

## 검증

실행 결과는 PR 검증란에 기록한다. 합성 자료로 state 재사용·타 계정 사용·연결 해제 경합, ADMIN/동의 권한, 한글 인코딩, 암호문 변조, 만료 토큰 갱신, 동시 중복, 응답 불명 재시도 방지를 검사한다. 네이버 인증정보 없는 상태에서는 실카페 게시 성공을 주장하지 않는다.

공식 근거: [카페 API](https://developers.naver.com/docs/login/cafe-api/cafe-api.md), [로그인](https://developers.naver.com/docs/login/devguide/devguide.md), [ChatGPT MCP](https://developers.openai.com/api/docs/guides/custom-mcp-server).
