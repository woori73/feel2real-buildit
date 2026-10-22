# Feel2Real AI 협업 관제실 — 개발·검증·운영 상태

2026-10-22. #855 제작실/MCP 구현을 재사용하는 별도 후속 브랜치. #850/#853/#854/#855 보류 상태 유지, main 병합·배포·운영 DB/S3 변경 없음. #859는 기존 구현을 보존한다.

## 작동하는 범위

`/ai-collaboration`에서 작업 등록, 명시적 GPT/Muse/Codex/Claude 선택, 자료 연결, 공동 메시지·모델 제안·판단·승인 이력을 저장한다. 관리자 메뉴와 제작실에서 연결된다. 참여 AI 기본 선택 없음. 기본 순서는 선택된 역할 중 GPT→Muse→Codex→Claude이며, Claude의 CHANGES_REQUESTED는 선택된 Codex→Claude로 전달한다. Codex가 미선택이면 자동 투입하지 않고 검수 결과를 BLOCKED로 남긴다. Muse의 기존 지피지기·업무 비서 설정/업무는 변경하지 않는다.

AI 역할의 응답은 **제안**이다. 이 관제실의 Codex 역할은 지정한 OpenAI API 모델로 텍스트/코드 제안을 만드는 어댑터이며 이 ChatGPT Work 세션·Codex CLI 작업공간과 연결된 코드 실행기가 아니다. Claude 역할의 제안도 이 PR의 독립 Claude 검수 완료 판정을 대신하지 않는다. 서버 명령·GitHub 쓰기·main 병합·운영 배포·제작실 승인 포인터 변경 도구를 모델에 제공하지 않는다.

사용자가 연결한 제작실 버전의 텍스트/대사/연출, 파일 메타데이터(SHA 포함), 최근 공동 타임라인만 외부 문맥으로 전달한다. 원본 파일 byte/이미지/영상은 모델에 전송하지 않는다. 파일 공유·다운로드는 제작실 기존 권한/불변 원본 경로를 이용한다. 화면에서 파일 ID 또는 버전 ID를 연결하고, 새 파일은 제작실에 먼저 올린다. 임의 URL/저장소 키는 받지 않는다. 결과와 이력은 MD attachment로 내려받으며 최근 200건 범위를 표시한다. 전체 이력은 beforeId 페이지로 계속 조회할 수 있고 DB에서 삭제하지 않는다.

## 권한·상태·비용

- 프로젝트를 열람할 수 있는 제작 담당자만 공동 작업/자료를 본다. VIEWER는 조회만, EDITOR는 본인이 등록한 작업 제어, ADMIN은 관리 가능. 가입만 한 비허가 회원은 403이다. 다음 호출 직전 DB의 현재 사용자 역할과 제작실 권한을 다시 검사한다.
- 모든 쓰기는 배포 원본 Origin 정확 일치, 64 KiB body 상한(길이 없는 chunked 포함), task.version 비교, task 행 잠금. 조회에 모델 호출·복구 쓰기 없음. MFA/계정 인증은 기존 홈페이지 인증을 사용하며 API 키를 브라우저에 전달하지 않는다.
- 작업은 DRAFT→RUNNING→WAITING_APPROVAL→APPROVED. PAUSED/CANCELLED/BLOCKED/UNKNOWN을 별도로 저장한다. 자동 호출은 1~20회 상한. 사용자 승인은 확인한 버전을 기록하며 정본·PR 승인·배포를 실행하지 않는다. 제목/참여 AI/상한을 몰래 바꾸는 수정과 물리 삭제는 제공하지 않고 새 작업 또는 취소를 사용한다.
- 각 호출을 DB 트랜잭션에서 선점/비용 예약한 뒤 **커밋 후** 네트워크에 보낸다. 한 작업에 active_run은 1개, 일일 예산 행을 원자 갱신한다. 예산 행 INSERT IGNORE의 공유 잠금 승격 교착을 경쟁 시험에서 재현해 exclusive upsert로 고쳤다. 네트워크 전에 롤백된 DB 선점만 제한적으로 재시도한다.
- 외부 타임아웃·오류는 UNKNOWN, 예약 비용 유지, 자동 재시도 없음. 중단은 다음 호출을 막으며 이미 보낸 요청의 원격 과금 취소를 보장하지 않는다. 정상 반환은 기록하고 다음 단계 전 PAUSED를 유지한다. 서버 종료 시 강제로 재선점하지 않는다. 10분 지난 미완료 호출은 사용자가 과금 불명을 확인해 recover→UNKNOWN으로 전환한다. 늦은 이전 run 응답은 무시한다.
- USD를 1,000,000분의 1 단위(μUSD) 정수로 보관한다. 작업 상한(최대100 USD)과 서버 일일 상한을 모두 만족해야 한다. UTF-8 byte+envelope 여유 및 출력 토큰 상한으로 계산한 **보수적 예약액 전체**를 사용액으로 유지한다. 실제 청구액 영수증이 아니며 설정 단가가 실제 모델 단가 이상임을 운영자가 검증해야 한다. 할인/캐시 환급을 가정하지 않는다.
- 실행기 JVM 동시 호출 2, 대기열 8. DB의 active_run/일일 상한은 여러 JVM에서도 유지된다. API 오류 원문·키·요청 전문을 로그하지 않는다. 본문과 모델 출력은 허가된 작업 이력 DB에 보존하므로 개인정보를 선택해 첨부할 때 외부 제공 목적/권한을 확인해야 한다.

## 실제 연결과 테스트 대체 구현의 구분

현재 `app.collaboration.api-enabled=false` 기본값이다. 전역 ON만으로 실행되지 않으며 각 provider enabled + billing-approved + 서버 일일 상한 + 단가 + credential/model + 작업 예산 + 매 시작 시 외부 제공 동의가 필요하다. 환경에 승인된 서버용 GPT/Claude 자격증명이 제공되지 않았고 유료 API 호출을 하지 않았다. **실제 GPT·Claude 자동 협업 성공은 미검증**이다.

공식 API wire contract 시험은 HTTPS endpoint/인증 헤더/요청 구조/최종 text 추출을 로컬 HttpClient 대체 구현으로 확인한 것이다. 실제 인증·계정 모델 접근권·과금·사용량 한도는 증명하지 않는다. 브라우저와 MySQL은 실제 실행했고 모델 답변만 명시적 mock이다. 소비자 앱의 기존 대화 세션을 읽거나 브라우저 쿠키로 API 인증하지 않는다.

## 프레임워크 비교와 결정

| 선택지 | 공식 확인 범위 | 이번 결정 |
|---|---|---|
| [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) | 장기 상태·checkpoint·human approval·재개를 제공하는 별도 orchestration runtime | 현재 단계/예산/승인/이력을 기존 Spring 트랜잭션·MySQL로 처리할 수 있어 추가 런타임 미도입 |
| [Hermes Agent](https://hermes-agent.nousresearch.com/docs) | 자체 agent 실행 환경, 메시지 gateway, MCP 및 외부 provider 통합 | 홈페이지 기존 권한/저장소와 별도 실행 환경을 늘릴 필요가 없어 미도입 |
| 기존 Feel2Real MCP + Spring/MySQL | 기존 파일·회원 권한·읽기 전용 Gateway 재사용. MCP 자체가 소비자 앱 대화 중계나 모델 추론 API는 아님 | GET 관제 자료 조회만 MCP에 추가하고 제어/모델 호출은 인증된 사이트 POST로 분리 |

## 공식 인증 검증 및 사용자 설정 절차

2026-10-22 공식 문서를 확인했다. 서버 실제 계정 인증 시험은 하지 않았다.

1. [OpenAI Sign in with ChatGPT](https://developers.openai.com/siwc/quickstart)는 적격 앱/오픈소스 클라이언트의 별도 등록·사용자 동의·Responses scopes와 계획 사용 조건을 안내하며 **기존 대화 접근 권한을 주지 않는다**. [등록·로그인](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)과 [preview 한계](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)를 계정/제품별로 확인해야 한다. Feel2Real에 등록된 해당 client와 사용자 동의가 없어 이번 구현은 이 구독 경로를 활성화하지 않았다. 구독이 있다는 사실만으로 서버용 inference가 준비되었다고 보지 않는다.
2. 이번 OpenAI 어댑터는 공식 `POST https://api.openai.com/v1/responses`, 서버 API key, 별도 지정 model을 사용한다. GPT/Codex는 각각 명시적 설정이다. 기존 앱의 로그인 토큰·대화 ID를 재사용하지 않는다.
3. [Claude API](https://platform.claude.com/docs/en/api/overview)의 `POST /v1/messages`, Console API key(이 구현은 x-api-key의 공식 호환 경로), version header를 사용한다. [API 청구 안내](https://support.claude.com/en/articles/8977456-how-do-i-pay-for-my-claude-api-usage)와 계정별 모델/한도를 확인한다. 기존 Claude 앱의 대화 세션은 연결하지 않는다. Workload Identity Federation 등 다른 인증 경로를 이번에 구현했다고 주장하지 않는다.
4. Muse는 기존 MetaMuseSparkLlmTransport를 선택된 작업에서만 재사용한다. 기존 업무 비서의 사용량 설정을 덮어쓰지 않는다. 전용 협업 단가·승인·상한이 없으면 호출하지 않는다.
5. 운영자가 **승인된 격리 환경**에 서버 자격증명과 모델·단가·일일 상한을 secret 설정으로 넣는다. 키를 채팅/PR/브라우저에 붙이지 않는다. 선택한 작업의 외부 제공/예산을 사용자 확인 후 작은 실제 작업으로 시작한다. GPT→Codex→Claude 전달과 Claude 수정 요청→Codex→재검수, 실패·중단·상한을 실제 계정에서 확인하고 그 기록을 별도로 제출한다. 현재 이 단계는 미실행이다.
6. 사이트 MCP는 #855의 [ChatGPT](../ai-agent-studio/CHATGPT_CONNECTION.md)·[Claude](../ai-agent-studio/CLAUDE_CONNECTION.md) 연결 절차 그대로다. `read_studio`에서 관제실 GET 목록/상세/이력을 조회한다. 원래 사용자 권한을 유지하며 제어 POST는 모두 거부된다. 실제 두 제품 계정 연결은 여전히 미검증이다.

설정 키(기본 OFF, 저장소에는 실제 값을 넣지 않음):

```properties
app.collaboration.api-enabled=false
app.collaboration.public-base-url=https://feel2real.co.kr
app.collaboration.daily-budget-microusd=0
# 각 gpt/codex/claude/muse: enabled, billing-approved,
# input-microusd-per-million-tokens, output-microusd-per-million-tokens
# 각 gpt/codex/claude: api-key, model
# Muse credential/model/token 상한은 기존 app.newstart.llm.meta 설정을 그대로 참조
app.collaboration.github-read-token=
```

GitHub는 고정 저장소의 PR 번호와 해당 HEAD check-runs만 조회한다. 익명으로 접근할 수 없는 경우 이 저장소 pull requests/checks 읽기만 가능한 토큰을 설정해야 한다. 응답의 raw PR 본문/헤더/오류/임의 URL을 저장하지 않는다. 연결된 PR·CI 결과를 타임라인 스냅샷으로 보존하며 merge/리뷰 작성/CI 실행 권한은 없다. 실제 홈페이지 서버의 GitHub token 연결은 미검증이다.

## 데이터/API 계약과 검증

V1096은 보류 중 #854 V1094/#855 V1095 뒤의 추가 테이블만 만든다. 이미 적용된 마이그레이션·원본·정본 포인터는 바꾸지 않는다. #855를 base로 한 후속 PR이며 #855 보류를 해제하거나 종속 PR을 운영에 자동 적용하지 않는다. entity 4개와 [DOMAIN](../DOMAIN.md), [API](../API.md), [FLOWS](../FLOWS.md)를 함께 갱신한다.

| 실제 경로 | 데이터/권한/효과 |
|---|---|
| GET /ai-collaboration | Thymeleaf 화면. 서버 데이터는 아래 API에서 조회, 제작실 권한 필요 |
| GET /api/collaboration/agents | 안전한 연결 준비 enum, secret/실제 인증 결과 없음 |
| GET /api/collaboration/tasks | 프로젝트 공동 작업 최근100개. 제작실 VIEWER 이상 |
| GET /api/collaboration/tasks/{id} | 작업·최근200 이력·불변 파일 참조. 제작실 VIEWER 이상 |
| GET /api/collaboration/tasks/{id}/events?beforeId= | 전체 이력 커서200개. 모델/복구 쓰기 없음 |
| GET /api/collaboration/tasks/{id}/export | 최근200 이력 MD attachment, 읽기만 수행. MCP 원본 경로로 추가하지 않음 |
| POST /api/collaboration/tasks | EDITOR 이상 새 작업. 선택 AI/상한 고정 |
| POST /api/collaboration/tasks/{id}/control | 소유자/ADMIN start/pause/resume/approve/cancel/recover, version/동의 검사 |
| POST /api/collaboration/tasks/{id}/messages | 소유자/ADMIN USER 메시지. AI 저자로 위장 불가 |
| POST /api/collaboration/tasks/{id}/artifacts | 소유자/ADMIN, DRAFT/PAUSED에서 기존 파일·버전·PR 중 하나 연결 |
| POST /api/collaboration/tasks/{id}/github-sync | 소유자/ADMIN, 고정 저장소 PR/HEAD CI 읽기만 수행 후 이력 저장 |

[브라우저 결과](browser-results.json), [PC](screens/control-room-1440.png), [모바일](screens/control-room-390.png). 격리 MySQL8.4 Java15건(서비스/HTTP12 + provider 계약3) PASS, Chromium8개 검사 PASS, 두 크기 axe0. 모두 합성 데이터이며 actualExternalModelCalls=0이다. 단순 설계/모의 화면이 아니라 실제 DB/API/UI 실행이며 외부 모델만 시험용 대체 구현으로 검증했다. 최신 CI는 제출 PR HEAD에서 별도 확인한다.

신규 AWS 서버·유료 임베딩·유료 모델 호출 없음. 기존 앱/MySQL/파일 저장소 재사용. 작업당 지시 최대8000자·메시지4000자·모델 결과16000자, 20호출이면 결과 body만 약320KB(문자 인코딩/인덱스/사용자 메시지는 별도). 실제 RDS 저장 증가/청구액·운영 EC2 부하는 측정하지 않았다. CI는 기존 GitHub Actions 한도를 사용하며 실청구 비용은 미확정이다.

## 남은 조건과 독립 검수 인계

- #860 `9aa8c340` CI2/2 PASS, #861 `7308d609` CI2/2 PASS. 각각 PR에 Claude 검수를 요청했다. #860 독립 검수·사용자 병합 승인·병합 후에만 운영 a1-precheck를 실행한다. 실제 최신 main 배포 workflow는 이미 수동 전용이며 유지한다.
- #853 P1/#855 P2는 선행 커밋에 이미 수정·검증되어 반복 구현하지 않았다. [URGENT_FIXES](../ai-agent-studio/URGENT_FIXES_20261022.md)와 최신 #855 CI를 근거로 독립 재검수를 진행한다. 본 관제실 테스트가 그 독립 판정을 대체하지 않는다.
- 실제 EP01 68컷: workspace 원본 후보 및 연결 Drive의 EP01/68컷/콘티 검색에서 확보하지 못했다. 스크린샷·합성 QA 파일·배너를 원본으로 등록하지 않았다. 실제 원본 전달/검증/업로드 전까지 대기다.
- Claude는 선택되지 않은 AI 호출0, 과금 기본OFF, 일일 예산 경합, 승인/정본 분리, 실행 중 권한 회수, 미완료 호출 복구/늦은 응답, 실제 MySQL·브라우저·MCP GET 경계를 독립 확인한다. 실제 모델/구독 계정·GitHub 서버 token·운영 성능 검증은 자격증명/승인된 격리 endpoint 준비 후 진행한다.
- 운영 공개/실제 AI 협업 완료로 보고하지 않는다. 현재 승인 요청 대상은 검수 가능한 개발 PR이며 운영 배포 승인 요청 준비는 미완료다.
