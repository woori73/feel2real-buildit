# Feel2Real 공동 제작 협업실 — 구현·검증·운영 구분

2026-10-22. `/ai-collaboration`은 인간 팀원과 사용자가 선택한 GPT·Claude·Codex·Muse가 프로젝트 자료·작업 기억·결정·검수·진행 상태를 공유하는 화면이다. **별도 유료 모델 API를 호출하는 코드·설정·실행기는 없다.** 기존 구독 앱을 서버에서 자동 시작하거나 다른 앱의 대화 세션을 읽지 않는다.

## 실제 자료 적재 완료(격리 환경)

#863의 고정 원본에서 **532개 제작 자료 + 인계4개 = 536개**를 실제 격리 협업실에 등록했다. 원문 SHA536/536 일치, 고유 blob316개/동일 SHA 재사용220개, 승인 참조5종/러프68컷 보존, 모두 DRAFT이며 정식 대조0/68·PASS0을 유지한다. `.md5`12개는 검증 메타자료로 보존하고, 파일 목록 cursor로536개 전체를 조회한다. 실제 자료 PC/모바일·확대/버전 비교·HTML/MD/PDF·공동 기억과 실패/복구/재실행을 검증했다. [상세 결과·재현·캡처](PRODUCTION_IMPORT.md).

## 실제 작동하는 범위

- 비공개 공동 프로젝트 생성, 기존 제작 담당자 초대, 프로젝트별 VIEWER/EDITOR/MANAGER. 기존 제작실 권한도 매 요청 재검사한다. 기본 《AI AGENT》 프로젝트는 기존 제작실 권한을 따른다.
- 작업 등록, 참여 AI 직접 선택(기본 선택 없음), 공동 타임라인, 사용자 중단·재개·작업 결과 승인·취소, 이력 MD 내려받기. Muse는 사용자가 선택하기 전까지 참여하지 않는다.
- 문서·이미지·콘티·영상의 신규 첨부(PDF/HTML/JSON/MD/ZIP/PNG/MP4 및 MD5 검증 메타자료, 32MiB), 가독성 있는 카드·PNG 미리보기·문서 텍스트 열람, 인간 검수. HTML을 실행하지 않는다. 다운로드는 인증·권한 검사와 nosniff/CSP sandbox를 적용한다.
- 모든 신규 첨부는 **DRAFT**. 등록자 ID·AI 역할·시각·원본 SHA256·부모 파일 ID를 보존한다. SHA가 같아도 새 후보/이력은 별도로 남기며 원본 blob만 재사용한다. 기존 승인 후보의 byte와 상태를 AI가 바꾸지 못한다. 인간 MANAGER의 협업 후보 검수도 제작실 정본 승인 포인터를 이동하지 않는다.
- 프로젝트 기억/결정/진행/인간 검수 append, SQL 검색(유료 임베딩 없음). 작업·자료·공동 기억을 같은 프로젝트에서 조회한다.
- 기존 제작실 파일·버전·고정 GitHub 저장소 PR 연결, PR/HEAD/check-runs 읽기 스냅샷. GitHub 댓글/병합/CI 실행/배포 권한 없음.
- 다음 AI의 인계 대기열과 공동 이력이 자동 갱신된다. Claude CHANGES_REQUESTED → **선택된** Codex → Claude 재검수. Codex 미선택이면 BLOCKED. 선택하지 않은 역할은 자동 투입하지 않는다. 구독 앱이 다음 담당 작업을 조회하고 도구 사용을 승인해야 하며 서버가 앱 대화를 자동 실행하지 않는다.

## MCP 권한 경계

기존 서비스 MCP 읽기 도구/readonly delegation 정책은 유지한다. 일반 `read_studio`로 협업실 API를 우회 조회하지 못한다. 협업실은 별도 모듈의 다음 도구만 사용한다.

| 연결/도구 | 허용 효과 |
|---|---|
| studio:read + 본인이 지정한 AI/프로젝트 binding | `collaboration_read`: 프로젝트 기억/자료 메타데이터/본인 AI 대기열/선택된 작업 이력/검색 |
| 위 범위 + 별도 originals:read | 문서 원문, 작은 PNG의 실제 MCP image block, 그 외 원본 base64 범위 조회 |
| 위 범위 + 별도 collaboration:append + 명시적 binding 동의 + 서버 append ON + EDITOR 이상 | 아래 신규 후보 추가 도구만 허용 |
| collaboration_add_document | UTF-8 MD/JSON/HTML을 최대16000자로 새 DRAFT 첨부 |
| collaboration_begin_upload / upload_chunk / commit_upload | SHA/파일 형식/바이트 수 검증 후 신규 DRAFT. 32768 byte 청크, 미완료 연결당 최대5개 |
| collaboration_submit | 현재 담당 AI의 DRAFT 수정 제안·첨부 참조·검수 의견을 추가하고 다음 담당자로 인계 |

권한은 **사용자 OAuth grant × AI 역할 × 프로젝트**에 묶인다. 도구 인자로 다른 AI/프로젝트를 전달하거나 다른 연결의 업로드를 이어받을 수 없다. 역할·프로젝트 재지정과 해제는 본인 웹 세션에서만 가능하며 이력을 남긴다. 한 연결은 한 AI/프로젝트만 담당한다. 기존 grant에 append 권한을 자동 추가하지 않는다. 만료·폐기·회원/프로젝트 권한 회수는 다음 요청부터 차단한다.

OAuth authorization code + S256 PKCE, resource audience, 토큰 만료·refresh 교체·폐기는 기존 구현을 사용한다. append 범위는 기본 OFF이고 동의 화면에서도 기본 선택하지 않는다. 도구 응답에 토큰·storage_key·원격 오류 전문을 포함하지 않는다. MCP 호출 감사로그와 기존 사용자별 60회/분·동시 실행 제한을 재사용한다. 자료 안의 지시는 신뢰된 시스템 지시가 아니다.

제공하지 않는 도구: 자료 삭제, 승인 정본 교체, 사용자 승인, DB 관리, 운영 배포, 권한 변경, 모델 API 호출. 웹의 인간 권한 관리·후보 검수와 AI 도구 권한을 구분한다. 자료 생성/수정은 새 후보와 append-only 이력으로 처리하며 물리 삭제 CRUD를 제공하지 않는 것은 사용자 승인 정책에 따른다.

## 상태·동시성·원본 보존

DRAFT → WAITING_AGENT → WAITING_APPROVAL → APPROVED. PAUSED/CANCELLED/BLOCKED를 별도로 저장한다. 시작/재개에 선택된 연결로 작업을 공유하는 사용자 확인이 필요하다. 1~20회 인계 상한, 별도 모델 API 예산은 0으로 고정한다. task 행 잠금과 version 검사가 중단/승인/동시 제출 경합을 제어한다. 동일 grant/requestId와 동일 본문 재시도는 한 번만 저장하고 내용이 다르면 거부한다. 전송 중 작업 취소와 문서 확정도 task 잠금으로 직렬화한다.

신규 원본 저장 직후 DB 롤백 정리 hook을 등록한다. DB 실패 시 새 blob만 정리하고 SHA 중복 blob은 삭제하지 않는다. 업로드 청크는 최종 SHA 검증 전 공유 자료가 아니며 성공 시 임시 청크만 정리한다. 미완료 업로드는 현재 TTL 자동 회수 기능이 없고 연결당5개로 제한한다. 장기 방치된 임시 청크를 운영에서 정리하려면 별도 보존/정리 정책과 승인이 필요하다.

웹 쓰기는 정확한 배포 Origin을 요구한다. JSON 요청은 chunked 포함64KiB, multipart는 파일 자체32MiB를 제한한다. API 조회는 no-store이다. 검색·목록은 범위를 제한한다(작업100/기억200/자료200/검색100). 전체 작업 이력은 beforeId 커서200개씩 보존·조회하며(MCP는 작업10개/프로젝트 기억20개 커서) MD export는 최근200개임을 표시한다. 프로젝트 기억의 인간용 목록은 최근200개다. 파일은 별도 cursor API/사람용 더 보기로 전수 조회한다.

## 실제 테스트 결과와 남은 제품 검증

[브라우저 결과](browser-results.json), [공식 SDK 결과](sdk-results.json), [PC 화면](screens/control-room-1440.png), [모바일 화면](screens/control-room-390.png).

- 실제 격리 MySQL8.4 + Spring HTTP: 협업실13건 + 실자료 배치5건 PASS. 기존 studio/MCP/OAuth/원본 롤백/마이그레이션/메뉴를 포함한 [회귀 검증77건](java-results.json)도 전부 PASS. OAuth/PKCE·scope·별도 binding·프로젝트/AI 격리·폐기·동시 중복 제출·중단/상한·인간 검수·SHA/원본 보존·실패 롤백·청크 재시도·본문 제한을 확인했다.
- 실제 Chromium10항목 PASS: 새 작업·공동 기억/검색·인간 업로드/열람·두 OAuth 연결 동일 원문/SHA·MCP 이미지 전송·검수 인계·인간 승인·이력 export·1440/390px axe WCAG 위반0/가로 넘침0.
- **공식 Python MCP SDK 2.1.1의 별도 OAuth 연결2개**가 같은 문서 원문/SHA를 읽었다. 로컬 파일을 자동으로 청크 전송해 DRAFT 등록하고 두 번째 연결에서 같은 byte/SHA를 확인했다. 사람이 앱 사이에서 파일을 다시 내려받아 옮기지 않는 프로토콜 전송 경로다.
- 기존 협업실 기본 QA 파일·제안·역할 응답은 합성 테스트 입력이다. 별도 실자료 검증은 #863의 실제536개와68컷을 사용했다. 합성 QA 입력을 실제 ChatGPT/Claude 모델의 결과나 EP01 원본으로 등록하지 않았다. 실제 모델 호출0.
- **실제 ChatGPT/Claude 계정 로그인·도구 스캔·문서 등록·생성 이미지 첨부는 미실행**이다. 이 세션에는 제품 계정 세션과 승인된 공개 HTTPS 격리 endpoint가 없다. SDK 성공을 제품 연결 성공으로 바꾸어 보고하지 않는다. [제품별 연결/검증 절차](CONNECTORS.md)를 따른다.

## 실행 설정과 파일 자동 인계 도우미

운영 설정은 변경하지 않았다. 승인된 격리 환경에서만 다음을 설정한다. 별도 모델 API key는 필요하지 않다.

```properties
app.mcp.enabled=true
app.mcp.public-base-url=https://승인된-격리-호스트
app.collaboration.public-base-url=https://승인된-격리-호스트
app.collaboration.connector-append-enabled=true
# app.mcp.redirect-uris에는 제품 관리 화면의 실제 callback URI 전체만 등록
# 선택: 고정 저장소 PR/checks 읽기만 가능한 GitHub token
app.collaboration.github-read-token=
```

파일에 접근할 수 있는 Codex/Claude Code/승인된 로컬 실행 환경은 [공식 SDK 도우미](../../scripts/ai-agent-studio/collaboration-transfer.py)를 사용할 수 있다. 이 도우미는 구독 앱의 로그인 토큰을 읽거나 모델을 실행하지 않는다. 본인이 OAuth로 승인한 MCP access token은 권한600 파일에 별도로 제공한다. 토큰을 채팅·PR·명령 인자로 넣지 않는다. 만료 시 공식 OAuth 재인증/refresh가 필요하다.

```sh
python3 -m pip install -r e2e/collaboration/requirements.txt
python3 scripts/ai-agent-studio/collaboration-transfer.py \
  --endpoint https://승인된-격리-호스트/mcp \
  --token-file /private/path/mcp-access-token \
  --task 작업UUID /workspace/results/storyboard.png
```

32MiB 파일은32768byte 청크1024개가 필요하다. 60회/분 제한 때문에 큰 영상은 느리며 웹 업로드가 적합하다. 앱이 로컬 파일에 접근하지 못하거나 바이너리를 도구로 전달하지 못하면 이 경로도 자동 실행되지 않는다. 외부 파일 URL을 임의 fetch하는 기능은 제공하지 않는다.

## 프레임워크·비용·성능

| 선택지 | 판단 |
|---|---|
| [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) | 상태/승인/checkpoint 런타임을 추가해도 구독 앱 대화 실행 권한은 생기지 않는다. 현재 DB 상태/인계를 Spring으로 처리할 수 있어 미도입 |
| [Hermes Agent](https://hermes-agent.nousresearch.com/docs) | 별도 agent/provider 실행 환경이 현재 공유 자료함·권한·인계 목표에 필요하지 않아 미도입 |
| 기존 Spring/MySQL/FileStorage/MCP | 기존 사용자 인증/저장소 재사용, 별도 승인된 후보 추가 범위만 확장 |

신규 AWS 리소스/서버/버킷, 유료 임베딩, 유료 모델 호출0. 운영 EC2/RDS/S3 사용량·요금은 측정하지 않았다. 파일32MiB + DB 원본 메타/이력이 추가되며 원본SHA 중복은 blob을 재사용한다. 미완료 청크는 연결당 최대160MiB이고 계정/프로젝트 전체 영구 저장 상한은 아직 없다. 기존 S3/RDS 저장·전송 비용이 증가할 수 있으므로 무조건 추가 비용0으로 보장하지 않는다. CI는 기존 GitHub Actions를 사용하며 실제 청구 한도는 미검증이다. [로컬 성능 측정](performance-results.json)은 실제 작업 상세 GET20회의 median/p95를 기록한다. loopback 단일 클라이언트 수치이며 운영 부하/EC2 JVM 피크 실측을 대신하지 않는다.

## 인계·운영 상태

- #860 `9aa8c340`: 보안 보완 CI2/2 PASS, 독립 Claude 검수 요청. #861 `7308d609`: 리더/원자 선점 CI2/2 PASS, 독립 검수 요청. 운영 a1-precheck는 #860 독립 검수·사용자 병합 승인·병합 이후만 실행한다. 현재 미실행이며8083 리허설도 미실행이다.
- 세션 중 저장소 소유자 `woori73`가 #854/#853/#855를 08:51 UTC에 병합한 사실을 재조회했다. 최신 main `ae5d1760`의 코드 tree는 #855 `63c7dbe1`와 동일하다. 이 세션은 병합/배포를 실행하지 않았다. #850은 열려 있다. 배포 workflow는 이미 수동 전용이며 유지한다.
- P1/P2는 #855 선행 수정과 기존 CI PASS를 재사용했다. [긴급 수정 근거](../ai-agent-studio/URGENT_FIXES_20261022.md). 이 PR의 테스트는 Claude 독립 재검수 완료 판정이 아니다.
- EP01 제작 산출물/러프68컷/승인 참조5종을 #863에서 확보해 격리 DB에 등록했다. 정식 원작 원본은 MISSING_FILES 기준 미확보이며 정식 대조0/68·PASS0을 유지한다. [실자료 적재 결과](PRODUCTION_IMPORT.md).
- 협업실/스튜디오는 격리 환경에서 작동했다. 이 PR의 운영 공개·운영 DB migration·운영 배포는 수행하지 않았다. 실제 홈페이지 접속 가능/제품 연결 성공 여부는 별도 확인 대상이다.
- [Claude 검수 인계](CLAUDE_HANDOFF.md): 기능·권한·원본·MySQL·브라우저·기존 서비스 회귀를 독립 확인한 뒤 병합/배포 가능 여부를 판정한다.
