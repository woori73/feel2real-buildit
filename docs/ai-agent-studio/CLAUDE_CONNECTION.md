# Claude 연결 설명서

검증일: 2026-10-22. **실제 Claude 구독 계정 연결은 미실행**이다. 이전 Claude 독립 검수는 사용자 제공 브랜치에서 확인했고, 긴급 수정 후 새 HEAD의 독립 재검수는 대기 중이다. 이 세션에 Claude 계정 세션·CLI·공개 테스트 서버가 없으며 유료 Claude API로 대체하지 않았다.

[Claude 공식 안내](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)는 Free·Pro·Max·Team·Enterprise의 remote custom connectors를 지원하며 Free는 custom connector 1개 제한을 안내한다. Team은 Owner/Primary Owner, Enterprise는 해당 관리 권한이 있는 사용자가 조직에 추가한 뒤 각 회원이 개별 연결한다. 실제 계정의 조직 제한·사용량 한도는 이 세션에서 확인하지 못했다.

1. 승인된 격리 테스트 서버에 이 브랜치를 실행하고 외부에서 접근 가능한 HTTPS `/mcp`를 준비한다. 운영 배포 전에는 운영 URL의 신규 구현에 연결할 수 없다. Claude 원격 connector의 요청은 사용자 PC가 아니라 Anthropic 서버에서 오므로 `localhost`, VPN 내부 주소만으로는 연결되지 않는다. 신규 유료 서버나 비승인 유료 터널을 만들지 않는다.
2. Pro/Max 등의 개인 계정: `Customize → Connectors → + Add → Add custom connector`. Team/Enterprise: 관리자가 `Organization settings → Connectors → Add → Custom → Web`에서 먼저 추가한다.
3. 이름 `Feel2Real Read Only`와 승인된 HTTPS `/mcp` URL을 입력한다. 운영 적용이 승인된 후 사용할 URL은 `https://feel2real.co.kr/mcp`다.
4. Authentication은 `Sign in now` 또는 `Sign in when needed`, OAuth client는 **`Register automatically`**를 선택한다. 이 서버는 DCR을 지원한다. 기본 권장 메뉴인 `Use Claude's published identity`의 CIMD fetch는 구현하지 않았으므로 그 선택을 사용하지 않는다. 공유 관리자 API key·고정 bearer header를 넣지 않는다.
5. [Claude 개발 공식 문서](https://claude.com/docs/connectors/building)의 Callback 안내와 일치하는 hosted callback은 `https://claude.ai/api/mcp/auth_callback`이다. UI가 다른 callback을 요구하면 연결을 중단하고 공식 규격을 재확인한다. 현재 구현은 다른 Claude callback을 설정에 추가해도 허용하지 않는다. arbitrary redirect·wildcard는 사용하지 않는다.
6. Feel2Real 본인 계정으로 로그인하고 필요한 서비스별 읽기 범위만 동의한다. 제작 담당 권한이 없는 계정은 studio scope에 동의해도 제작 자료를 읽을 수 없다. `offline_access`는 선택 사항이다. 바이너리 원본의 byte/SHA 대조가 필요한 경우에만 별도 `originals:read`를 선택하고 전 역할 공통 원본 외부 제공 경고를 확인한다. 기본은 해제이며 미선택 시 원본 403이 정상이다. 기존 grant는 재동의 없이 원본 권한이 늘지 않는다.
7. 대화의 `+ → Connectors`에서 연결을 활성화하고 실제 자료를 요청한다. [동일 자료 비교 절차](CONNECTION_TEST.md)를 수행한다.
8. Feel2Real `/mcp/connections`에서 폐기하고 Claude `Customize → Connectors`에서도 제거한다. 폐기 즉시 해당 grant의 access·refresh 모두 무효다.

MCP 서버는 Anthropic/OpenAI API를 호출하지 않는다. Claude의 기존 구독 내 사용량 제한은 그대로 적용된다. Claude Desktop의 로컬 stdio 설정은 이번 원격 OAuth 연결 시험을 대체하지 않는다.

관리자가 studio/catalog/chart/grammar 외 민감 범위를 선택하면 외부 제공 경고를 별도로 확인해야 한다. 금융은 명시한 허용 사업체의 월 합계만 제공한다. OAuth 준비·SDK 검증과 실제 사용자 로그인·동의·도구 스캔·동일 자료 대조는 별개이며, 현재 실제 Claude 연결은 미검증이다.

Callback 재확인(2026-10-22): [공식 Callback URLs](https://claude.com/docs/connectors/building/authentication)는 hosted URI를 `https://claude.ai/api/mcp/auth_callback` 하나로 명시한다. `https://claude.com/api/mcp/auth_callback`은 등록 허용 목록에서 제외하며 HTTP 거부 시험을 한다. 도메인이 공식 사이트라는 사실만으로 callback 경로를 추정하지 않는다.
