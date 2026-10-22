# Claude 연결 설명서

검증일: 2026-10-22. **실제 Claude 구독 계정 연결·독립 검수는 미실행**이다. 이 세션에 Claude 계정 세션·CLI·공개 테스트 서버가 없으며 유료 Claude API로 대체하지 않았다.

[Claude 공식 안내](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)는 Free·Pro·Max·Team·Enterprise의 remote custom connectors를 지원하며 Free는 custom connector 1개 제한을 안내한다. Team은 Owner/Primary Owner, Enterprise는 해당 관리 권한이 있는 사용자가 조직에 추가한 뒤 각 회원이 개별 연결한다. 실제 계정의 조직 제한·사용량 한도는 이 세션에서 확인하지 못했다.

1. 승인된 격리 테스트 서버에 이 브랜치를 실행하고 외부에서 접근 가능한 HTTPS `/mcp`를 준비한다. 운영 배포 전에는 운영 URL의 신규 구현에 연결할 수 없다. Claude 원격 connector의 요청은 사용자 PC가 아니라 Anthropic 서버에서 오므로 `localhost`, VPN 내부 주소만으로는 연결되지 않는다. 신규 유료 서버나 비승인 유료 터널을 만들지 않는다.
2. Pro/Max 등의 개인 계정: `Customize → Connectors → + Add → Add custom connector`. Team/Enterprise: 관리자가 `Organization settings → Connectors → Add → Custom → Web`에서 먼저 추가한다.
3. 이름 `Feel2Real Read Only`와 승인된 HTTPS `/mcp` URL을 입력한다. 운영 적용이 승인된 후 사용할 URL은 `https://feel2real.co.kr/mcp`다.
4. Authentication은 `Sign in now` 또는 `Sign in when needed`, OAuth client는 **`Register automatically`**를 선택한다. 이 서버는 DCR을 지원한다. 기본 권장 메뉴인 `Use Claude's published identity`의 CIMD fetch는 구현하지 않았으므로 그 선택을 사용하지 않는다. 공유 관리자 API key·고정 bearer header를 넣지 않는다.
5. 기본 callback은 `https://claude.ai/api/mcp/auth_callback`이다. UI가 다른 공식 callback을 요구하면 운영 설정 담당자가 그 정확한 URI만 `app.mcp.redirect-uris`에 추가한 테스트 환경에서 확인한다. arbitrary redirect·wildcard는 사용하지 않는다.
6. Feel2Real 본인 계정으로 로그인하고 필요한 서비스별 읽기 범위만 동의한다. 제작 담당 권한이 없는 계정은 studio scope에 동의해도 제작 자료를 읽을 수 없다. `offline_access`는 선택 사항이다.
7. 대화의 `+ → Connectors`에서 연결을 활성화하고 실제 자료를 요청한다. [동일 자료 비교 절차](CONNECTION_TEST.md)를 수행한다.
8. Feel2Real `/mcp/connections`에서 폐기하고 Claude `Customize → Connectors`에서도 제거한다. 폐기 즉시 해당 grant의 access·refresh 모두 무효다.

MCP 서버는 Anthropic/OpenAI API를 호출하지 않는다. Claude의 기존 구독 내 사용량 제한은 그대로 적용된다. Claude Desktop의 로컬 stdio 설정은 이번 원격 OAuth 연결 시험을 대체하지 않는다.
