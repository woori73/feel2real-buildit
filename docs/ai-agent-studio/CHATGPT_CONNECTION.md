# ChatGPT 연결 준비와 사용자 인증

2026-10-22 공식 [OpenAI 인증 문서](https://developers.openai.com/plugins/build/auth)의 Redirect URL·Client registration 절을 확인했다. **실제 ChatGPT 계정 연결은 미검증**이다. SDK 시험은 제품 계정 연결을 대체하지 않는다. 운영 MCP는 OFF이며 이 절차로 운영 활성화·배포를 실행하지 않는다.

## 정확한 callback 계약

- 일반 신규 연결: `https://chatgpt.com/connector/oauth/{callback_id}`. `{callback_id}`는 ChatGPT MCP 서버 관리 화면에서 복사한 실제 값이다.
- 안정형: `https://chatgpt.com/connector_platform_oauth_redirect`. 공식 문서상 RFC 9207 issuer 식별 요건을 충족하거나 기존 안정형 연결인 경우에 사용한다. 현재 구현은 `authorization_response_iss_parameter_supported`를 광고하지 않으므로 신규 연결을 안정형으로 가정하지 않는다.
- 기존 코드의 `/connector_platform/oauth/callback`과 `chat.openai.com` callback은 제거했다.
- 기본 허용 목록에는 Claude hosted callback만 있다. 설정 담당자는 **격리 테스트 설정** `app.mcp.redirect-uris`에 관리 화면의 실제 ChatGPT URI 전체를 정확히 추가한다. URI 전체 일치와 공식 호스트/경로 형식을 모두 검사한다. 임의 도메인·wildcard·다른 callback ID·query·fragment·userinfo·http·추가 slash는 거부한다.
- `isolated-test-callback`은 자동 테스트에서만 쓰는 합성 ID다. 실제 연결 URI 또는 기본값으로 사용하지 않는다.

## 개발자가 준비할 것

1. 승인된 격리 DB·파일 저장소·본인 테스트 계정만 사용한다. MCP 기본 OFF를 유지하고 운영 URL에 이 브랜치를 배포하지 않는다.
2. 실제 계정 시험을 할 때만 승인된 격리 HTTPS endpoint를 준비한다. `/.well-known/oauth-protected-resource/mcp`, `/.well-known/oauth-authorization-server`, `/mcp/oauth/*`, `/mcp`가 같은 issuer/resource를 사용해야 한다. 현재 공개 격리 endpoint 준비는 미완료다.
3. ChatGPT에서 표시된 callback을 정확히 등록한 뒤 DCR을 사용한다. CIMD는 광고하거나 자동 fetch하지 않는다. 서버는 public client `none`, authorization code, S256 PKCE, resource audience, code 일회성·만료, refresh 교체·폐기를 지원한다. OpenAI API key를 제공하지 않는다.
4. 아래 사용자 인증 이후 실제 tool discovery와 동일 자료 ID·버전·SHA·승인 포인터 대조를 기록한다. [공통 검증표](CONNECTION_TEST.md)를 채우기 전에는 연결 성공으로 보고하지 않는다.

## 사용자가 직접 해야 하는 단계

1. 본인 ChatGPT 웹 계정에서 조직 정책과 MCP 생성 권한을 확인하고 `Plugins → Add custom MCP server` 또는 계정의 `Apps → Create` 화면을 연다. UI·요금제·관리자 권한은 실제 계정과 [공식 연결 안내](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt)를 따른다.
2. 격리 HTTPS MCP URL과 OAuth를 선택한다. 관리 화면의 정확한 redirect URI를 설정 담당자에게 전달한다. 다른 사용자의 client ID·공유 관리자 token을 재사용하지 않는다.
3. Feel2Real **본인 계정으로 직접 로그인**하고 필요한 scope만 동의한다. 기본은 catalog/studio이다. 원본 byte/SHA 대조가 필요한 경우에만 기본 해제된 `originals:read`를 별도로 선택하고 원본이 마스킹 없이 외부로 제공된다는 경고를 확인한다(전 역할 공통). 미선택 상태에서는 바이너리 원본이 403이며 metadata/마스킹 텍스트 조회는 유지한다. 관리자 민감 범위를 선택할 때는 별도 외부 제공 경고 확인이 필요하다. 금융 범위도 사업체별 월 합계만 제공하며 원문·증빙·거래처·학생 목록은 제공하지 않는다.
4. Scan Tools/연결을 완료하고 `read_studio`, `search`, `fetch`로 실제 등록 자료를 확인한다. 다른 학원/사업체 접근·승인/확정 요청이 거부되는지 확인하고 Claude와 같은 ID/SHA를 비교한다.
5. 만료·refresh·연결 폐기까지 시험한다. 본인 로그인·동의·제품 계정 시험은 자동화된 합성 OAuth 시험과 구분한다.
