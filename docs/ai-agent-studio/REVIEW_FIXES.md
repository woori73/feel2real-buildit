# PR #855 독립 검수 반영 및 #853·#854 연동

기준일 2026-10-22. 최신 main `7472d91e0857db8cb245e1231ba24c161b8e95cf`를 fetch해 마이그레이션 상한 V1093을 확인했다. 이번 수정은 사용자가 전달한 Claude 검수 항목을 기준으로 Codex가 수행했다. GitHub #855 comment 조회에는 검수 원문이 없었으며, **Claude가 이번 수정까지 재검수했다고 보고하지 않는다.**

## 병합 전 구조

검토 순서 **#854 → #853 → #855**. 최종 #854 `4553d10b`의 출결 조회/정정 구분·모델 호출 제한·감사 범위·EXECUTING 복구·투어 연동도 포함한다. 기능 브랜치끼리 최신 내용을 통합했으며 세 PR은 모두 main 대상으로 Draft를 유지한다. #855에 두 선행 PR 내용이 포함되어 있으므로 선행 PR 병합 후 변경 범위를 다시 확인한다. main 병합·운영 배포·운영 DB 변경·운영 MCP 활성화·실제 금융기관 접속은 수행하지 않았다.

| 항목 | 수정·검증 |
|---|---|
| Flyway 충돌 | #854의 V1094 유지, 미배포 #855만 V1095로 변경. 신규 MySQL 8.4에서 전체 190개 성공, 실패 0, V1094/1095 각각 한 번. 최종 #854의 미병합 V1094 감사 범위 컬럼 3개를 포함한 새 격리 DB로 다시 검증. 이전 테스트 컨테이너는 보관, checksum repair 또는 운영 이력 변경 없음 |
| 문서 충돌 | `docs/API.md`, `docs/DOMAIN.md`, `docs/FLOWS.md`의 세 기능 섹션과 최신 main #856 내용을 모두 보존. 추가 API·권한·승인 경계를 명시 |
| ChatGPT OAuth | 관리 화면의 실제 `https://chatgpt.com/connector/oauth/{callback_id}`를 정확히 등록. 기존 잘못된 두 callback 제거. 공식 URI 형태와 명시한 전체 URI의 이중 검사. query/fragment/사용자 정보/가짜 host/임의 suffix/미등록 callback 거부 |
| 표준 고정 callback | `https://chatgpt.com/connector_platform_oauth_redirect`는 실제 관리 화면에서 발급/제시한 URI만 명시 등록. 이 서버는 RFC9207 issuer 응답·CIMD를 광고하지 않으므로 신규 연결에 고정 callback이 발급된다고 가정하지 않음 |
| 연결 준비 | [ChatGPT](CHATGPT_CONNECTION.md), [Claude](CLAUDE_CONNECTION.md), [실연결 대조](CONNECTION_TEST.md). 공개 격리 HTTPS 주소·제품 계정 로그인/동의는 별도 사용자 인증 단계, 아직 미실행 |
| 이전 버전 복원 | 기존 revision/file 변경 없이 USER_EDIT·REVIEW 새 버전 추가. expectedHead CAS, RESTORE 감사. 기존 승인 포인터 유지, 복원 초안을 별도로 승인해야 정본 변경 |
| EP01 일괄 등록 | [사용법](BULK_AND_RESTORE.md). JSON 또는 root manifest.json ZIP, 1~68 중복 없는 정확히 68컷. 첨부 asset 형식/내용 사전 검사, ZIP 100항목·디렉터리 payload 포함 해제 20MiB·경로 이동 거부. 68개 revision은 DB 한 트랜잭션, 원본과 승인 이력 보존 |
| 200건 누락 | 이력 offset/limit 페이지, 비교 화면 전체 페이지 조회. 컷은 `/api/studio/cuts`로 전체 현재 head 별도 조회. 205개 추가 이력이 있어도 68개 컷 유지 시험 |
| 모바일 | 실제 Chromium 390×844에서 승인 저장 재조회와 미승인 복원, 가로 넘침·axe 검사. PC 1440×1000도 검증 |
| 프록시별 호출 제한 | 기존 신뢰 프록시 resolver 재사용. 유효 bearer는 IP 대신 userId로 transport 제한, 실제 도구 호출도 사용자 60/분. 공개 DCR은 임의 Authorization 헤더를 바꿔도 IP당 5/분이며 6번째 실제 HTTP 요청 429를 검증. 같은 프록시의 A 제한 초과 시 B 허용, 위조 XFF 무시 시험 |
| 동시 호출 | JSON-RPC 전체 JVM당 기본 8개, 내부 upstream 읽기 2개. 429 Retry-After 및 finally permit 반환. 여러 replica 전체 합산 동시성 제한은 아님 |
| ADMIN 민감정보 | 외부 AI로 민감정보를 보낸다는 별도 경고와 서버 강제 체크. 미확인 동의 거부·SENSITIVE_ACK 감사 시험 |
| 입시·GET 감사 | 관리자 입시 지식/자료/통계·화면 제외, 본인 history만. [627개 GET 전수 정적 감사](GET_SAFETY_AUDIT.md), 확인된 저장/외부 HTTP GET 8개 제외 |
| 금융 최소화 | 원문 정책 66개 제거, 허용 사업체 월 요약 8필드만. 실제 두 backend에서 학원·사업체 격리와 authKey 제거 검증 |
| #853/#854 승인 | MCP는 두 서비스 승인 POST를 호출할 수 없음. 실제 #853 synthetic CSV preview 후 userApprovedLedgerCommit=false 거부와 MCP bearer 직접 확정 거부, 장부 행 수 불변 확인 |
| 기존 비서 회귀 | 빠른 새로고침 직후 학원 범위 로딩 전 제출 경쟁을 발견해 #854에서 수정하고 선행 브랜치에 전파. 범위/기록 조회가 끝나야 제출 가능 |
| CI 메모리 | 최신 main의 다수 Spring context 캐시로 기존 MySQL CI가 OOM 발생. 테스트 제외 없이 context cache 상한 4로 제한하여 자원 반환. 재실행 결과는 PR checks에서 최신 HEAD로 확인 |

## 실행 증거

- 최종 #854 반영 후 영향 범위 Java **98건, 실패/오류/skip 0** 및 bootJar 통과. 새 DB의 V1094 감사 컬럼도 확인. 투어 JS **5건** 통과. 이전 통합 Java **224건, 실패/오류/skip 0** 증거도 보존. 스튜디오·MCP·JWT·Wiki 경계·통합 비서·회계/수집 회귀. 최종 ZIP asset bytes/SHA·디렉터리 항목 압축 데이터 20MiB 한도·UUID label 보강 후 영향 범위 **25건 재통과**. [Java 결과](review-java-results.json)
- 스튜디오 Chromium **22개 검사**, PC 6개 탭과 모바일 axe 위반 0. 7종 업로드·SHA/download 동일·HTML 스크립트 차단·승인·비교·모바일 복원·미허가자 403. [결과](browser-results.json), [PC](screens/studio-pc-1440.png), [모바일 승인](screens/approve-mobile-390.png), [모바일 복원](screens/restore-mobile-390.png)
- 공식 MCP SDK 2.1.1의 독립 OAuth A/B, PKCE·도구 19개·동일 원문 SHA·쓰기 거부 성공. [결과](protocol-results.json). 합성 ChatGPT callback은 로컬 실행 옵션에만 등록하며 실제 callback 발급 증거가 아님.
- 실제 localhost BuildIt/AcademyCraft와 격리 MySQL의 경계 검사 **6건**: 다른 학원/사업체 거부, 8필드 재무 요약, 금융 원문/수집 preview·승인 POST 거부, #853 승인 없는 확정 시 장부 불변. [결과](integration-results.json)
- local-collector Node 단위 시험 **4건 통과**. 금융기관 접속 없이 기존 mocked adapter 시험만 수행.
- 기존 Python Wiki 격리/read-only 34건 성공 증거는 원본 보고서에 보존. 이번 변경은 Python 원본을 수정하지 않았으며 최신 CI가 해당 시험을 다시 실행한다.
- #854 실제 backend+Chromium **12개 시나리오 전부 통과 (44.7초)**. 빠른 새로고침 후 초안 제출, 같은 대화창 학생·금융 자료 승인, 모바일 실제 승인·메뉴 강조·결과 재조회 및 새 조회/정정 안내/공지 초안의 무쓰기 확인 포함. [결과](assistant-regression-results.json), [PC](screens/integration-assistant-pc-1440.png), [모바일](screens/integration-assistant-mobile-390.png). CI 전체 PASS는 최신 HEAD checks로 별도 확인한다.

## 남은 위험과 Claude 재검수 요청

1. 실제 ChatGPT·Claude 계정 연결 **미검증**. 승인된 공개 격리 HTTPS endpoint 및 사용자 로그인·계정/조직 메뉴·실제 callback 값 확인이 필요하다. 운영 활성화로 우회하지 않는다.
2. 실제 EP01 정본 미제공. JSON/ZIP 68컷 시험은 **합성 자료**이며 작품 원본 등록 완료가 아니다. 실제 파일로 사전 형식 검사 후 일괄 등록·원문 SHA·승인 포인터 대조 필요.
3. 새 UUID 파일 저장과 DB 트랜잭션은 분산 atomic commit이 아니다. 모든 asset 사전 검사는 했지만 저장 후 DB 실패 시 미연결 파일이 남을 수 있다. 기존 원본/이력을 삭제하는 보정은 하지 않는다.
4. 동시성은 JVM 단위이며 replica 전체 합산은 제한하지 않는다. 사용자 rate는 MySQL 공유. 운영 proxy 신뢰 목록은 실제 배치와 대조해야 하며 임의로 넓히지 않는다.
5. GET 전수 감사는 정적 근거다. 허용 S3 읽기 48개는 요청·전송 비용이 있고 실제 S3는 미검증. SSR 35·Wiki 27·AcademyCraft 13개 프록시의 동적/외부 계약은 별도 검수 대상이다. 운영 모든 endpoint의 실자료 대조를 주장하지 않는다.
6. 정확한 callback 등록/제거, consent 민감정보 체크 우회, proxy 속도 제한·JVM permit 해제, 금융·입시 축소 정책, DB/파일 복원 불변성, 대용량 ZIP 사전검사, #854 초안 승인 경쟁, 세 PR 병합 후 Flyway/doc diff를 Claude가 독립 재검수해야 한다.

Claude에게 전달하거나 메시지를 발송한 것은 아니다. 재검수에 사용할 코드·근거·절차를 이 문서에 준비했다.
