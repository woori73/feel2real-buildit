# 구독 앱 연결과 실제 파일 기능 검증

2026-10-22 공식 문서 확인. **아래는 사용자 조작이 필요한 절차이며 실제 제품 로그인 테스트는 미실행이다.** 모델 API key나 별도 모델 API 청구를 요구하지 않는다. 기존 구독 사용량과 조직 정책은 계속 적용된다.

## 먼저 준비할 환경

운영 배포와 분리된 승인된 공개 HTTPS 테스트 주소, 격리 DB/파일 저장소, 본인 제작 담당 계정이 필요하다. MCP와 collaboration append는 기본 OFF이다. 개발자가 README 설정을 격리 환경에만 적용하고 실제 callback을 정확히 등록한다. 로컬127.0.0.1 SDK 검증 주소는 제품 원격 커넥터 연결 주소가 아니다. 사용자 승인 없는 신규 유료 서버/터널을 만들지 않는다.

## ChatGPT

1. [공식 custom MCP 안내](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt)에서 본인 플랜/조직의 생성·게시 권한을 확인한다. 현재 안내는 웹의 Business/Enterprise/Edu 전체 MCP와 관리/RBAC를 명시한다. 다른 플랜에서 동일한 쓰기 기능이 된다고 가정하지 않는다. 계정별 지원 여부는 미검증이다.
2. 관리 화면의 `Apps → Create` 또는 실제 표시되는 custom MCP 메뉴에서 격리 HTTPS `/mcp`, OAuth를 선택한다. [기존 ChatGPT 안내](../ai-agent-studio/CHATGPT_CONNECTION.md)의 callback 계약을 따른다. 관리 화면에 표시된 실제 `https://chatgpt.com/connector/oauth/{callback_id}` 전체를 개발자에게 전달한다. callback ID를 추측하지 않는다.
3. Feel2Real 본인 계정으로 로그인한다. `studio:read`로 시작한다. 원문/이미지 제공은 별도 `originals:read`에 동의한다. 후보 추가가 필요한 **이 연결에만** `collaboration:append`를 선택한다. 기존 읽기 전용 연결은 그대로 둔다.
4. Feel2Real `/ai-collaboration`에서 프로젝트를 선택하고 본인 OAuth 연결과 GPT 역할을 지정한다. DRAFT 추가 확인란을 직접 선택한다. 도구 목록을 다시 스캔/연결 갱신하여 `collaboration_read`, 별도 허용 시 추가 도구가 표시되는지 확인한다.
5. 아래 공통 비교표를 실행한다. 도구 승인은 ChatGPT의 실제 확인 UI를 따른다. 승인 정본 교체/삭제/사용자 승인 요청은 실행할 도구가 없어야 한다.

[공식 파일 API](https://developers.openai.com/plugins/reference)의 `window.openai.uploadFile`, 파일 선택/다운로드 helper, `openai/fileParams`는 선택적 제품/위젯 기능이다. **이번 구현은 이 위젯/임시 다운로드 URL 수집 경로를 구현하지 않았다.** 따라서 ChatGPT가 만든 PNG/MP4를 대화에서 자동으로 가져와 저장했다고 주장하지 않는다. 이번 서버는 텍스트 문서 도구와 범용 MCP 청크 전송을 지원하며 실제 ChatGPT 앱이 바이너리 전달을 지원하는지는 별도 검증해야 한다.

## Claude

1. [공식 custom connector 안내](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)를 따른다. Free/Pro/Max/Team/Enterprise를 안내하며 Free는 custom connector1개 제한이다. Team/Enterprise는 관리자 등록·조직 제한을 확인한다. 실제 계정/사용량 한도는 미검증이다.
2. 개인 계정의 `Customize → Connectors → + Add → Add custom connector`, 조직의 관리자 custom connector 화면에서 HTTPS `/mcp`를 입력한다. `Register automatically` DCR을 선택한다. [기존 Claude 안내](../ai-agent-studio/CLAUDE_CONNECTION.md)의 hosted callback과 OAuth 계약을 따른다.
3. 원격 connector는 Anthropic 클라우드에서 접속한다. 사용자 PC에서만 열리는localhost/VPN 주소로는 되지 않는다. 본인 Feel2Real 계정으로 `studio:read`, 필요 시 별도 `originals:read`와 `collaboration:append`를 선택한다.
4. 협업실에서 해당 연결을 **Claude 역할 + 지정 프로젝트**로 묶고 DRAFT 추가를 직접 확인한다. Claude 대화에서 연결을 활성화/도구를 갱신한다.
5. 공통 비교표를 실행한다. Claude Desktop/Code 로컬 파일 접근은 [원격 connector와 별도 기능](https://support.claude.com/en/articles/11725091-when-to-use-desktop-and-web-connectors)이다. 원격 connector가 모든 로컬/생성 파일을 자동으로 전달한다고 가정하지 않는다.

## 공통 검증표 — 아직 실제 계정 결과 없음

| 단계 | ChatGPT | Claude | 합격 증거 |
|---|---|---|---|
| 본인 로그인/OAuth/도구 스캔 | 미실행 | 미실행 | 실제 플랜·scope·callback·툴 목록(토큰 제외) |
| 동일 문서 조회 | 미실행 | 미실행 | 같은 fileId·본문·SHA256·등록자·시각 |
| 동일 이미지 조회 | 미실행 | 미실행 | 같은 fileId·SHA·실제 이미지 인지 결과 |
| 새 문서 등록 | 미실행 | 미실행 | collaboration_add_document 반환 DRAFT, 웹에 같은 SHA |
| 실제 생성 PNG 첨부 | 미실행 | 미실행 | 원본 byte SHA 일치; 모델이 지어낸 base64는 실패로 기록 |
| 프로젝트/역할 격리 | 미실행 | 미실행 | 다른 프로젝트와 미선택 AI 작업403 |
| 검수 인계 | 미실행 | 미실행 | Claude 수정 요청→선택된 Codex 대기열→재검수 이력 |
| 연결 폐기 | 미실행 | 미실행 | /mcp/connections 폐기 후 access/refresh401 |

시험용 프롬프트: “협업실 연결의 내 인계 대기열을 조회하고 작업 지시와 공동 이력을 읽으세요. 지정된 문서 ID의 원문·SHA를 확인한 뒤 새 MD 수정 제안을 DRAFT로 등록하고 해당 파일 ID를 포함해 현재 작업을 인계하세요. 사용자 승인·정본 교체·삭제·배포는 하지 마세요.”

서버가 다음 작업/공동 이력을 전달하는 것은 구현되어 있다. 다음 구독 앱의 대화 자동 시작·앱 사이 실시간 세션 중계는 구현되어 있지 않다. 도구 호출이 가능한 활성 대화/공식 실행 환경이 필요하다. 지원되지 않는 파일 전달은 인간 웹 업로드 또는 승인된 로컬 공식 SDK 도우미를 대안으로 기록하고 완료로 표시하지 않는다.
