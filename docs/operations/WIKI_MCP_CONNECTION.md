# Wiki MCP 연결 복구

Wiki endpoint는 `https://feel2real.co.kr/problem-generator/wiki/api/mcp`다.
이 경로는 사이트의 OAuth `/mcp`(제작소)와 별개이며 기존 API-key 인증을 사용한다.
Claude 커스텀 커넥터의 2단계 인증 설정에서 **요청 헤더**에
`X-Api-Key`를 넣는다. OAuth client ID/secret에는 API 키를 넣지 않는다.

## 운영 설정

수동 `Wiki MCP connection repair` workflow를 main에서 실행한다.
입력은 운영자 컴퓨터에서 생성한 RSA 공개키 PEM의 base64이고 개인키는 외부로 전송하지 않는다.
운영 EC2는 `/etc/buildit-wiki.env`에 누락된 `BUILDIT_MCP_API_KEY`만 생성하며,
기존 키가 있으면 그대로 재사용한다. 이 파일은 현재 systemd 유닛과 향후 정식 배포가 계속 읽는다.
다른 환경설정과 DB는 보존하고 Wiki 서비스만 필요한 경우 재시작한다.

환경파일 변경 전 원본을 root 전용으로 백업한다. 인증 없는 요청/잘못된 키의 401,
정상 키의 initialize, FINAL·직보·문제 저장 도구 발견을 검사한다.
실패하면 이전 환경파일을 복원하고 Wiki를 재시작한다.
검증 시 저장 도구를 실행하지 않아 운영 학습자료에는 쓰지 않는다.

작업 로그에는 상태·도구명과 RSA-OAEP-SHA256으로 암호화한 키만 출력한다.
로그의 `credential_rsa_oaep_sha256`를 운영자의 개인키로 복호화한 뒤 Claude의
`X-Api-Key` 요청 헤더에 등록한다. 평문 키를 채팅·저장소·이슈·로그에 남기지 않는다.
공개키를 선택할 수 있는 workflow 실행 권한은 관리자에게만 허용해야 한다.

## 완료 기준

- 외부 공개 URL에서 무인증/오인증 401, 정상 키 initialize 및 tools/list 성공
- Claude의 기존 연결 또는 새 연결에 도구가 표시됨
- 서버 검증 통과와 실제 Claude 연결 성공을 구분해 보고

공식 Claude 안내:
https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp
