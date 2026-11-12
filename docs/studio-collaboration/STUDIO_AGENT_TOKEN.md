# 제작소 AI 작업 세션 토큰 (`STUDIO_AGENT_TOKEN`)

## 왜 필요한가
MCP 도구(`read_studio_asset`·`upload_studio_asset`)는 파일을 base64 인자로만 주고받는다. 그래서 AI가 파일 내용을 직접 글자로 써야 하고, 3 MB짜리 PNG 한 장도 사실상 옮길 수 없다.
`/mcp/studio/files`는 큰 파일을 받을 수 있지만 MCP OAuth access token이 필요하다. 클라우드 Claude 세션의 명령창에는 이 토큰이 없다. (MCP 연결은 있지만 토큰을 직접 쓸 수 없는 셸 클라이언트는 이 비밀 토큰 대신 `begin_studio_upload` 1회용 티켓 업로드를 쓸 수 있다 — [API.md](../API.md) `/mcp/studio/uploads`.)
그래서 클라우드 세션은 Studio 원본을 내려받지도, 결과물을 올리지도 못했다(2026-11-11 S11 r10 작업에서 확인).

## 무엇을 열었나
| 경로 | 하는 일 |
|---|---|
| `GET /api/studio-agent/files/{id}/content?projectId=` | 원본 내려받기 |
| `POST /api/studio-agent/files` (multipart) | 새 버전 등록. 항상 AI_PROPOSAL·REVIEW |

- 인증: `Authorization: Bearer <STUDIO_AGENT_TOKEN>`만 받는다. 상수 시간으로 비교하고, 로그에 남기지 않는다.
- 처리 계정: `app.studio.agent.login-id`(기본 `modu`)로 처리된다. 제작소 가드와 프로젝트 권한 검사를 그대로 거친다.
- 승인·삭제·복원·공개 경로는 없다. 승인은 지금처럼 제작소 화면에서 사람이 한다.
- 토큰이 없으면 두 경로 모두 404다. 기본은 꺼져 있다.

## 켜는 방법 (사용자 작업, 1회)
1. 긴 무작위 값을 하나 만든다. 예: `openssl rand -hex 32`
2. GitHub 저장소 Settings → Secrets and variables → Actions에 `STUDIO_AGENT_TOKEN` 이름으로 등록한다.
3. `Deploy to EC2`를 한 번 실행한다. 배포가 값을 `/etc/f2r/env`에 동기화한다. 시크릿이 없으면 env를 건드리지 않는다.
4. 클라우드 Claude 환경에 같은 값을 넣는다. 세션이 `request_credential`(host `feel2real.co.kr`, Bearer) 입력 창을 띄우면 거기에 입력한다.
   - 값은 환경의 네트워크 비밀로 저장되고, 프록시가 요청에 붙인다. 채팅에는 붙여 넣지 않는다.
   - 환경 네트워크 허용 목록에 `feel2real.co.kr`을 추가해야 한다.

## 끄기·교체
- 끄기: GitHub 시크릿을 지운다. 그다음 EC2 `/etc/f2r/env`에서 `STUDIO_AGENT_TOKEN` 줄을 지우고 재시작한다. 배포는 시크릿이 없을 때 기존 값을 지우지 않는다.
- 교체: 시크릿 값을 바꾸고 다시 배포한다. 클라우드 환경의 비밀 값도 함께 바꾼다.

## 확인
- 토큰 없이 `GET /api/studio-agent/files/x/content` → 404(꺼짐) 또는 401(켜짐)
- 토큰으로 기존 fileId를 내려받았을 때 SHA-256이 `list_studio_assets`의 sha256과 같아야 한다.
- 업로드 응답이 `serverVerified=true`이고, `get_asset_versions`에 origin AI_PROPOSAL로 보여야 한다.
