# HOME HANDOFF — NEWSTART 10/1 PRE-OPEN (2026-09-28)

작업 PC: 학원(academy) PC. 이 문서는 집(home) PC에서 세션 없이 이어서 작업하기 위한 단일 handoff.
코드 의미는 이 commit에서 변경하지 않는다(docs-only).

```
SOURCE OF TRUTH
BRANCH:   fix/newstart-preopen-access-latency-20260928
CODE HEAD: 14967daf  (fix(newstart): 감사회원 grant ADMIN API + 지피지기 구간 실측·reasoning_budget 1024)
BASE MAIN: 09ee3b3e
PR:        #436  https://github.com/woori73/feel2real-buildit/pull/436
CI:        PASS  Build Check run 36421943776 (build pass 3m0s)
MERGE:     NO
DEPLOY:    NO
PRODUCTION WRITE: 0   (DB·secret·knowledge 모두)
NVIDIA 호출: LOCAL 4회 / production 0회 / 키 값 출력 0회
```

---

## 1. ACCESS — root cause와 최소 수정 범위

- 네이버 로그인 회원은 `NaverAccountProvisioningService`에서 항상 `Role.STUDENT`로 자동 등록된다.
- `newstart_member_grants`는 **production 0 row**(read-only census 실측). row를 만드는 코드 경로가 존재하지 않는다.
- 따라서 `NewStartEntitlementService.allows()`(ADMIN short-circuit → grant row)는 비ADMIN 회원에게 403을 낸다 — 권한 로직 결함이 아니라 **부여 경로 부재**가 원인이었다.
- PR #436은 판정·허용 범위를 바꾸지 않고 **부여/회수/조회 관리 경로만** 추가했다:
  - `domain/newstart/service/NewStartGrantService.java` (idempotent grant/revoke/list, `granted_by` 길이 50 제한)
  - `domain/newstart/controller/AdminNewStartGrantController.java` (`GET/POST/DELETE /api/admin/newstart/grants`)
  - `NewStartMemberGrantRepository`에 query 3개 추가(`findByUserIdAndCapability`, `findAllByOrderByGrantIdAsc`, `deleteByUserIdAndCapability`)
  - `NewStartEntitlementService.DENIAL_MESSAGE`: "회원 전용 기능입니다. 운영자에게 이용 신청을 문의해 주세요." → **"감사회원 전용 기능입니다. 감사회원 승인을 운영자에게 요청해 주세요."**
- **보존 확인**: `SecurityConfig` 무변경(`/api/admin/**`의 `hasRole('ADMIN')`이 그대로 게이트), 판정 단일 지점 `NewStartEntitlementService` 무변경(문구 상수만), `NewStartAiChatController`/`NewStartFssComplaintController`/`NewStartFssAgentController`/`NewStartRequestController`의 gate 호출 위치 그대로.
- **QA(LOCAL 격리 schema `f2r_nim_e2e`, 35항목 + 정정 3건, LLM 0회)**
  - 일반(STUDENT, grant 없음): 공개 읽기 200 / 감사 전용 4종 403 / grant API 403 / 익명 401.
  - `student`(user_id 6, role STUDENT)에게 ADMIN API로 부여 → 즉시 200(재로그인 불필요), 회수 → 403, 재부여 → 정상. 지피지기 실제 200 응답도 동일 STUDENT+grant 계정으로 수신(ADMIN 성공을 감사 성공으로 간주하지 않음).
  - ADMIN: grant 없이 통과, grant 목록 응답에 PII 없음.

## 2. LATENCY — root cause

`POST /api/newstart/ai/chat` 1건을 구간 로그로 분리 실측(질문 원문·키·`reasoning_content` 미기록, 길이·ms·token 수만):

| 구간 | Q1 baseline (budget 16384) |
| --- | --- |
| AUTH | 32ms |
| RAG | 625ms |
| EVIDENCE | 2ms |
| PROMPT | 9ms |
| NVIDIA | 110,642ms (`httpMs=110,546`) |
| SERIALIZATION+net | 175ms |
| TOTAL(client) | **111,487ms** |

- 세션 3질문(같은 감사회원 계정): Q1 111.5s / Q2 141.0s / Q3 112.8s → NVIDIA가 전체의 **97.9~99.5%**.
- warm-up 효과 없음(Q2가 최장, Q3은 Q1 수준 복귀, `ragMs`는 625→631→1,354로 오히려 증가). history 효과 구조적으로 없음(API는 `message`만 전송, `historyMessages=0`).
- `max_tokens=16384`는 한 번도 발동하지 않음(`finishReason=stop`, completion 3,196~4,052 tokens) → 줄이면 답변이 잘리므로 대상 아님.
- 병목 판정: **E(추론·생성 디코딩)**.
- 참고: Q2의 141.0s는 프론트 `newstart.js`의 120s abort를 넘어 실제 브라우저에서는 응답이 표시되지 않는 상태였다.

## 3. OPTIMIZATION — 단일 변수 결과

- 변수 1개, 값만 변경: `application.yaml` `app.newstart.llm.nvidia.reasoning-budget` **16384 → 1024** (env override `APP_NEWSTART_LLM_NVIDIA_REASONING_BUDGET=1024`로 실측 후 yaml 반영. `NewStartLlmProperties` 코드 기본값 16384는 javadoc 규칙대로 손대지 않음).
- 동일 Q1 BEFORE/AFTER:

| 항목 | BEFORE (16384) | AFTER (1024) |
| --- | --- | --- |
| TOTAL(client) | 111,487ms | **41,439ms** |
| serviceMs | 111,278ms | 40,601ms |
| NVIDIA `httpMs` | 110,546ms | 37,738ms |
| RAG | 625ms | 2,594ms(재기동 첫 콜) |
| promptTokens | 3,109 | 3,109(동일) |
| completionTokens | 3,347 | 1,991 |
| finishReason | stop | stop |
| answer | 478 chars | **1,530 chars** |
| sources | 공식4 + 부실차주카페4 | 공식4 + 부실차주카페4(목록 전체 동일) |
| attempts/fallback | 1 / false | 1 / false |

- 개선: 절대 **70.0s**(서버 70.7s), **62.8%**(client) / 63.5%(server).
- 품질: 답변이 짧아져 빨라진 것이 아니라 길어졌고 근거 조립 계약(#434 retrieval·우선순위·비공식 슬롯 보장)과 fallback 계약(NVIDIA primary 1회 + 기술 실패 시 Qwen 1회, 루프 없음) 유지.
- **중요 단서: 1024 품질 비교는 Q1 n=1.** Q2/Q3은 3-call 한도 소진으로 1024에서 미검증 → 집에서 필요하면 **Q2/Q3만** 추가 검증하고 완료된 baseline 3-call은 반복하지 않는다(호출 비용).
- 부수 관측: 1024 답변에 모델이 자체 생성한 `[KB2 공식]`형 인용 표기 4회 등장. DB 대조 결과 코퍼스에 없는 문구(모델 생성)이며 지칭 번호는 실제 근거 1~8번과 대응한다. 화면이 `sources`를 렌더하지 않아 현재 사용자 체감은 낮음 → 후속 항목 D와 함께 판단.
- STREAMING: 기술적으로 YES(`stream:true`)이나 미구현. 현 경로는 blocking `HttpClient.send` + `ofByteArray` + JSON `ResponseEntity` + 프론트 `res.json()`이라 적용 시 transport/SSE 엔드포인트/점진 렌더/테스트 변경이 필요(별도 대규모 UI rewrite 회피 지침에 저촉). 게다가 `reasoning_content` 폐기 전제로 첫 가시 토큰이 추론 구간 종료 후에 나오므로 1024 상태에서는 체감 이득이 decode 일부에 한정.

## 4. TEST / CI

- Java 10개 클래스 **65 tests 0 failures**: `NewStartGrantServiceTest`, `AdminNewStartGrantControllerTest`, `NewStartEntitlementServiceTest`, `NewStartAiChatServiceTest`, `NewStartLlmClientTest`, `NvidiaNimLlmTransportTest`, `NewStartAiChatControllerTest`, `NewStartRequestControllerTest`, `NewStartFssAgentControllerTest`, `NewStartFssComplaintControllerTest`.
- JS 11/11: `src/test/js/newstart_fss_agent_access_contract.test.js`, `src/test/js/newstart_requests_board_contract.test.js`.
- CI: Build Check run `36421943776` success.
- 재실행 주의(이 PC 이력): Gradle은 JDK 21 필요 — `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`, `--offline`, 스코프 없는 `test`는 10~16분 소요. 결과 판정은 `build/test-results/test/*.xml`.
- 로컬 QA 환경(학원 PC에만 존재, Git 미포함): docker `f2r_mysql_main`(host:3308) 안의 격리 schema `f2r_nim_e2e`(Flyway 1013, ACTIVE 지식 112, 사례 139, grants table 있음). shared `f2r_db`와 타 agent 컨테이너는 사용하지 않았다. QA 스크립트·아트팩트는 `Documents/Qoder/2026-09-28/c2cf8ec9/`의 `launch-preopen-app.ps1`, `qa_access_matrix.py`, `qa_access_recheck.py`, `qa_latency_3call.py`, `qa_latency_after.py`, `lat_Q1*.json`(커밋되지 않음).
- QA 로그인: dev seed `student/admin1234`(STUDENT, user_id 6), `admin/admin1234`(ADMIN). `debt-status`는 `stage`가 `ALLOWED_STAGES` 값이어야 200(예: `심사 진행 중`), 공식 업데이트 경로는 `/api/newstart/updates`다.

## 5. OPEN BLOCKERS (10/1)

**A. 감사회원 실제 부여** — production `newstart_member_grants` 0 row. #436 배포만으로는 감사회원이 생기지 않는다. 10/1 전에 운영자가 대상 user_id 목록과 부여 방법(ADMIN API vs operator SQL)을 확정해야 한다. **production grant write는 사용자 승인 전 수행 금지.**

**B. 질문요청 게시판 정책** — 현재 코드: 읽기=인증회원, 쓰기=THANKS_MEMBER. 최신 제품 정책에서는 질문요청 게시판이 **일반회원 기본 기능**에 포함된다. **임의 수정하지 말고** 진행 중인 "질문요청 게시판 간소화 + 갈무리 source 교정" 작업과 합쳐 정리한다.

**C. timeout 계약 불일치** — 프론트 abort 120s vs 서버 timeout 180s(+ fallback 90s). 1024에서 정상 Q1은 41s지만 fallback 체인·장문에서는 프론트가 먼저 끊을 수 있다. 후속에서 값 정렬(abort 상향 또는 서버 timeout 하향 중 하나).

**D. sources UI** — 백엔드는 `sources[{kind,title,url}]`를 반환하지만 `newstart.js`가 렌더하지 않는다. '공식 안내 / 운영자·실제 경험 / 사례 참고' 구분 노출이 필요하며 §3의 `[KB…]` 표기 판단과 함께 처리한다.

**E. Retrieval 후보 한도** — Wiki는 TOTAL 117 / ACTIVE 117 / REVIEW 0(별도 승인 작업 완료). `PER_STEP_LIMIT=8` 때문에 광범위 질문에서 관련 ACTIVE 지식이 후보 8개 밖으로 밀릴 수 있다. **latency 이유로 LIMIT를 단순 증가시키지 않는다.**

## 6. 다른 진행 작업과의 충돌 주의

별도 agent가 "질문요청 게시판 간소화 + 갈무리 게시판 source 교정"을 진행 예정/중이다. 따라서 #436을 바로 merge하지 말고 먼저:

```
git fetch origin
git rev-parse origin/main                 # 최신 SHA 확인 (본 문서 작성 시점 09ee3b3e)
gh pr list --repo woori73/feel2real-buildit --state open
# 게시판 PR의 changed files와 #436 비교:
git diff --name-only <게시판PR head>...<base>
gh pr diff 436 --name-only
```

특히 overlap 위험 파일: `NewStartRequestController.java`, `static/js/newstart.js`, `NewStartEntitlementService.java`, `src/test/js/newstart_requests_board_contract.test.js`, `docs/API.md`, `docs/DOMAIN.md`. semantic overlap: 질문요청 쓰기 gate(§5-B)는 두 작업이 같은 조건을 건드린다.

## 7. HOME FIRST TASK (순서)

1. PR #436 상태 + 최신 `origin/main` SHA 확인.
2. 질문요청+갈무리 agent 결과/PR 확인.
3. 두 PR의 file overlap + semantic overlap 확인(§6 명령).
4. 질문요청 쓰기 권한을 최신 정책에 맞게 조정할 위치 확정 — 정책: 일반회원은 네이버 로그인만으로 A to Z·게시판·회원 채팅 등 기본 기능, 감사회원은 거기서 ① 개인 채무 상황 AI 관리·질문답변 ② 추심 대응 금감원 민원 서비스만 추가. 즉 질문요청 게시판 자체는 감사회원 전용이 아닌 방향으로 **게시판 개선 PR과 함께** 검토(이 PR에서 혼자 고치지 않음).
5. 그 다음 #436 merge 여부 판단.

금지: 새 구현, 추가 NVIDIA 호출, 추가 Gradle test, merge, deploy, production write.
