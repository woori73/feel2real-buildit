# ChatGPT 연결 설명서

검증일: 2026-10-22. 서버 구현·공식 Python SDK 시험은 완료했다. **실제 ChatGPT 계정 연결 시험은 미실행**이다. 운영 배포를 하지 않아 `https://feel2real.co.kr/mcp`의 이 PR 구현을 이용할 수 있는 상태가 아니다.

## 지원 범위 확인

[OpenAI 공식 연결 안내](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt)에 따르면 Business·Enterprise/Edu의 웹 환경은 custom MCP 연결을 지원하며 Business는 관리자/소유자, Enterprise/Edu는 관리자가 허용한 역할이 생성할 수 있다. 같은 문서 FAQ는 Pro의 read/fetch 연결도 안내한다. Plus·Free의 이 기능을 이 문서만으로 보장하지 않는다. 모바일 custom server 설정은 지원하지 않는다고 안내한다. 이 세션에서는 사용자의 실제 요금제·워크스페이스 권한을 확인할 계정 세션이 없었다. 계정 메뉴와 조직 정책 확인이 최종 기준이다.

## 서버가 승인된 테스트 환경에 준비된 후 사용자 절차

1. 기존 ChatGPT 구독의 웹 계정으로 로그인한다. `Plugins` → `+` → `Add custom MCP server`를 연다. 구 UI는 `Settings / Workspace settings → Apps → Create`일 수 있다. 조직의 `Create plugins with MCPs` 권한이 필요하다. 새 유료 플랜을 구매하지 않는다.
2. 이름 `Feel2Real Read Only`, 테스트 서버의 공개 HTTPS MCP URL을 입력한다. 승인 후 운영 적용 시 URL은 `https://feel2real.co.kr/mcp`다. 서버에서 OpenAI API 키를 요구하지 않는다.
3. 인증은 OAuth를 사용한다. 서버는 OAuth authorization-code + S256 PKCE + RFC 8707 resource audience + RFC 9728 discovery + Dynamic Client Registration(DCR)을 지원한다.
4. 앱에 표시된 OAuth callback URI가 서버의 **정확한 redirect URI 허용 목록**과 일치하는지 확인한다. 기본 포함 값은 `https://chatgpt.com/connector_platform/oauth/callback`, `https://chat.openai.com/connector_platform/oauth/callback`이다. 계정 UI가 다른 공식 callback을 표시하면 설정 담당자가 `app.mcp.redirect-uris`에 그 정확한 URI를 추가한 테스트 환경에서 재시험한다. wildcard·임의 도메인을 허용하지 않는다. 서버는 CIMD URL 자동 fetch를 하지 않는다.
5. 연결 과정에서 Feel2Real 홈페이지에 **본인 계정으로** 로그인하고 조회 목적에 필요한 서비스만 동의한다. 기본 선택은 서비스 목록과 제작 스튜디오다. 학생·금융·상담·회원 자료는 해당 서비스 권한과 목적이 있을 때만 선택한다. `offline_access`를 선택하면 30일 이내 연결 자동 갱신을 허용한다.
6. 위험 안내를 읽고 생성한다. 도구 목록에서 `list_catalog`, `read_studio` 등 `read_*`, `search`, `fetch`, `activity_history`만 있는지 확인한다. 모든 도구는 읽기 전용이며 승인·수정·배포 도구가 없다.
7. 새 대화에서 연결을 선택하고 “제작 프로젝트 현황과 EP01 등록 자료를 조회하고 원작 출처·승인 상태·SHA-256을 표시해 주세요”라고 요청한다. 등록 파일이 없으면 업로드 대기가 정상이다.
8. [공통 비교 절차](CONNECTION_TEST.md)에 따라 Claude와 동일한 실제 자료 ID·버전·SHA를 비교하고 결과를 기록한다. 클라이언트 제한으로 일반 `read_*` 도구가 보이지 않는 read/fetch 환경에서는 `search`/`fetch`부터 시험한다.
9. 연결 폐기는 Feel2Real `/mcp/connections`에서 한다. ChatGPT에서도 연결을 제거한다. 만료된 token은 401로 거부되며 범위를 늘리려면 새 사용자 동의를 거친다.

공식 문서는 현재 UI에서 Developer Mode를 먼저 켤 필요가 없다고 안내한다. 구 UI의 플랜·조직 정책이 다르면 공식 계정 안내를 따른다. 개인 ChatGPT 계정의 메뉴 조작이나 본인 로그인은 사용자가 수행해야 하며, 서버 개발만으로 계정별 연결이 자동 생성되지는 않는다.
