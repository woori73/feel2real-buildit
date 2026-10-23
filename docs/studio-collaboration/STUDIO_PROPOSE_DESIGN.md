# 별도 동의형 AI 후보 등록 연결 — 3단계 설계

상태: **DESIGN ONLY / 미구현 / 비활성**. 이 문서는 endpoint·scope·DB를 활성화하지 않는다. 기존 `/mcp`는 읽기 전용이며 `studio:propose`를 발급하거나 POST를 실행하지 않는다. 실제 AI 직접 등록 성공을 보고하지 않는다. 사람의 기존 업로드/AI_PROPOSAL 라벨과 AI 인증 쓰기 연결은 구분한다.

## 권한 경계

| 연결 | resource/audience | 허용 | 금지 |
|---|---|---|---|
| 기존 읽기 MCP | 기존 `/mcp` | 동의한 GET·문서·이미지 | 모든 자료 쓰기·승인·삭제·배포 |
| 제안용 연결(예정) | 별도 `/studio-proposals` | 해당 프로젝트 DRAFT 제출만 | 정본/승인 포인터·회원·기억 덮어쓰기·삭제·장부 확정·배포 |
| 사람 제작 화면 | 기존 로그인/프로젝트 역할 | 후보 확인, 허용 역할 검수 | 권한 없는 프로젝트 접근 |

별도 클라이언트 등록·grant/token 저장소·audience·scope 집합을 사용한다. 기존 `McpOAuth.SCOPES`/`mcp_grants`에 쓰기 권한을 추가하지 않는다. OAuth 2.1 authorization-code/PKCE S256, 단회 code, 정확한 callback 등록/검사, resource 바인딩, 현재 credential stamp/역할/프로젝트 membership 재검사를 기존 보안 패턴과 동일하게 적용한다. 임의 redirect/와일드카드/서로의 token 수용 금지. 운영은 기본 OFF. 연결 생성과 프로젝트별 `studio:propose` 동의는 명시적인 사용자 동작으로만 가능하며 기억/문서 속 지시가 동의를 대체하지 않는다.

서버는 grant의 사용자·등록 client ID·등록 AI 작성 주체 ID를 기록한다. `Claude/Codex/Muse` 같은 모델명 문자열만으로 작성자를 인증하지 않는다. client attestation과 실제 모델 추론 여부는 별개이며 `modelReported`는 미검증 참고값으로 표시한다. VIEWER/초대 대기/보관/폐기/타 프로젝트는 제출 불가. EDITOR/APPROVER 사용자도 AI에 위임하는 것은 후보 제출뿐이다. 토큰·API key·원문 금융/상담 자료는 작업 기억·로그에 넣지 않는다.

## 제출·사용자 확인 계약(예정)

1. AI가 projectId/itemKey/expectedHead/source revision IDs/source file IDs와 원본 출처(repo/PR/commit/path 또는 허용된 문서 근거), idempotencyKey, 파일 선언 SHA/size/MIME를 보낸다. 서버가 모든 참조를 같은 프로젝트에서 재확인한다. 서버가 임의 외부 URL을 가져오지 않는다(SSRF·비용 방지).
2. 실제 byte stream을 제한된 파일 저장 경로로 받으며 SHA/크기/형식 재계산, 기존 upload의 신규 객체 rollback 정리 패턴을 재사용한다. 파일당20MiB, PNG/MD/JSON/PDF부터 지원한다. 실행 파일/HTML script 실행/임의 ZIP 추출/금융기관 접속 없음. 확장 형식은 별도 검토한다.
3. 별도 제안 레코드에 DRAFT + 실제 actor/client/modelReported/source/expectedHead/파일 SHA를 append-only 저장한다. **이 시점에 `studio.save`를 호출해 head를 변경하지 않는다.** 원본·이전 revision·approved_id 불변. 같은 idempotency key+같은 payload는 동일 DRAFT, 다른 payload는409.
4. 사람의 대기함에서 파일·설명·SHA·출처·AI 작성 주체·기존 승인본 비교를 표시한다. 사용자가 현재 head를 확인하고 후보 반영을 눌러야 `AI_PROPOSAL` 새 revision으로 등록한다. stale expectedHead는409. 거절/철회는 상태 이력을 남기며 영구 삭제하지 않는다.
5. 후보 반영은 승인과 다르다. 기존 APPROVER의 별도 검수 동작만 승인 포인터를 바꾼다. AI 연결에는 확인/승인/복원/삭제 도구를 제공하지 않는다. 결과를 DB와 저장 bytes에서 재조회해 성공/실패/불명 상태를 구분한다. 재시도 전에 idempotency 상태부터 확인한다.

DB는 별도 proposal/attachment/append-only event가 필요하다. **마이그레이션 번호는 이 설계에서 예약하지 않는다.** 구현 시 authoritative main과 #862 등 다른 PR 번호를 다시 조사하고 Entity·DOMAIN·Flyway 전체/업그레이드 검증을 함께 제출한다. 기존 V1094/V1095 수정 금지. DB와 파일은 분산 트랜잭션이 아니며 crash·정리 실패는 미참조 신규 객체를 남길 수 있다. 기존 원본을 정리 대상으로 추정하지 않고 신규 객체 ID 기준 재조정 절차를 설계한다.

## 구현 PR의 필수 검증

- 읽기 token으로 쓰기 및 제안 token으로 기존 MCP/승인/삭제/장부 확정 요청 모두 거부
- 별도 동의 취소·역할 변경·초대 미수락·보관·타 프로젝트·다른 학원/사업체 참조 거부
- 인증된 client/사람과 모델 자기주장 구분; source/실제 SHA 불일치 거부
- 30번째 컷 실패/재시도/idempotency·고아 파일 정리·기존5승인 원본/SHA/버전 불변
- PC1440/모바일390 사용자 후보 확인·stale 충돌·독립된 APPROVER 승인 재조회
- 새 세션에서 후보/결정/최신 SHA를 제작실에서 재조회, 중간 파일 전달 없는 AI 직접 제출

현 단계 증거는 읽기 MCP의 쓰기 거부, 실제 자료 판독 전달 준비, 사람의 후보·댓글·기억 기능이다. **AI 직접 제출과 외부 ChatGPT/Claude 실제 연결은 이 설계로 검증되지 않는다.** Claude 독립 검수 후 별도 구현 PR에서 위 경계를 검증한다.
