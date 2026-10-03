## Wiki 접근 정책 (2026-09)

- 공개: `GET /wiki`, `GET /api/wiki/home-feed`(비회원은 최신 자료/인기 자료 메타데이터만), `GET /api/wiki/popular-downloads`, `POST /api/wiki/visits`.
- 회원 전용: `/wiki/**` 실제 화면, `/api/wiki/**` 자료·문항·Thread·미리보기·파일(위 공개 예외 제외), `/problem-generator/wiki/api/**`.
- Wiki가 공유하는 open-community 경로도 `scope=wiki` 목록과 Wiki 게시글 상세를 회원 전용으로 검사한다. 공유 이미지/첨부 원본 endpoint는 Wiki 파일 우회를 막기 위해 로그인 회원 전용이다.
- 비회원 HTML 직접 접근은 `/?login=required&returnUrl=...`로 이동하고, JSON/API는 401을 반환한다. 로그인 성공 후 안전한 내부 Wiki URL로 복귀한다.
- ADMIN/TUTOR의 기존 역할 권한과 데이터 모델은 변경하지 않는다.

## NewStart Official Updates V1 (2026-09)

### ✅ GET /api/newstart/updates

- 목적: NewStart 홈과 전체 업데이트 화면에 공식 업데이트를 제공한다.
- 인증/권한: 공개. 서버가 `reviewStatus=PUBLISHED`만 조회한다.
- Query: `sourceType` 선택값 `NEWSTART_FUND|FSC|CCRS|FSS|PUBLIC_AGENCY|HOPE_RETURN_PACKAGE|CREDIT4U|COURT`.
- Response: `[{ updateId, sourceType, sourceName, title, summary, originalUrl, publishedAt, effectiveFrom, effectiveTo, category, reviewStatus, featured }]`.
- 정렬: `featured DESC`, `publishedAt DESC`, `updateId DESC`.
- 원문: `originalUrl`은 검토된 공식 URL이며 클라이언트가 URL을 생성하지 않는다.
- 화면: `/newstart` 최신 3건, `/newstart/updates` 전체 목록.

### ✅ ADMIN /api/admin/newstart/updates

- 인증/권한: `ROLE_ADMIN`.
- `POST /api/admin/newstart/updates`: 후보를 항상 `REVIEW`로 등록한다. 수동 등록 경로이며 자동 게시 대상이 아니다.
- `GET /api/admin/newstart/updates?sourceType=&status=`: 검토 목록.
- `POST /api/admin/newstart/updates/{id}/publish`: REVIEW 자료 게시. 공식 원문 URL이 없으면 400.
- `POST /api/admin/newstart/updates/{id}/reject`: REVIEW 자료 거절.
- `POST /api/admin/newstart/updates/{id}/review`: REJECTED 자료 재검토.
- `POST /api/admin/newstart/updates/{id}/archive`: PUBLISHED 자료 보관.
- `POST /api/admin/newstart/updates/{id}/featured?enabled=true|false`: 게시 자료 주요 표시.
- 중복 방지 우선순위: 공식 문서 ID → 정규화 URL → 출처 유형+정규화 제목+발표일.
- DB: `newstart_official_updates` (V988).

## NewStart Official Discovery Phase 2 (2026-09)

### ✅ ADMIN POST /api/admin/newstart/updates/discovery/run

- 목적: 공식 업데이트 discovery 수동 실행 (관리자).
- 인증/권한: `ROLE_ADMIN` (기존 `/api/admin/**` 매처 재사용).
- Query: `sinceDays` 선택값(기본 1) — 지정 일수 이후 게시 항목만 수집.
- Response: 소스별 합산 결과 JSON (`success`, `discovered`, `newReviews`, `duplicates`, `irrelevant`, `errorMessage`, `subResults`).
- 계약(2026-09-23 자동 게시 정책): 정상 수집된 공식 항목(제목+원문 URL 확보)은 중요도·관련성과 무관하게 즉시 `PUBLISHED` (`reviewedBy=AUTO_DISCOVERY`). 관리자는 사후 `reject`/`archive`로만 개입한다. 자동 게시는 뉴스 게시판까지이며 Knowledge `ACTIVE`/`OFFICIAL_CURRENT` 승격과 완전히 분리된다.
- 최소 기술 차단(게시 대상 아님): 소스 조회/파싱 실패, 제목 없음, 원문 URL 없음/invalid, `NOT_IMPLEMENTED`, 동일 `dedup_key` 중복 — 기존 FAILED/duplicate 집계 정책 사용.
- 어댑터: NSF 게시판·NSF FAQ(eGov), FSC(`po040301`), CCRS(`bbsViewList.do` JSON), 희망리턴패키지는 sbiz24 SPA 이전으로 `NOT_IMPLEMENTED` failure 기록(SUCCESS 위장 금지).
- 스케줄러: `0 0 11 * * *` / `0 0 21 * * *` (`Asia/Seoul`). `app.newstart.discovery.scheduler-enabled` 기본 `false` — 브랜치 머지만으로 운영 자동 실행되지 않으며 값 명시 설정 시에만 동작.
- DB: `V991__add_newstart_official_updates_phase2.sql` (main의 V990 EASY 충돌로 번호만 양보, 내용 불변).

## NewStart AI (2026-09)

### ✅ POST /api/newstart/ai/chat

- 목적: NewStart 화면의 AI 질문. **NewStart 전용 DIRECT LLM**(`NewStartLlmClient`)을 쓴다 — primary NVIDIA NIM Nemotron Lightning(`https://integrate.api.nvidia.com/v1`, 공식 sample 계약 `temperature=1`·`top_p=0.95`·`max_tokens=16384`·`chat_template_kwargs.enable_thinking=true`), `reasoning_budget`은 1024로 운영(2026-09-28 실측 근거는 아래 latency 항목), 기술적 실패 시에만 OpenRouter Qwen(`qwen/qwen3.7-flash`) DIRECT 1회. 공용 BuildIt Wiki AI Router는 이 경로에서 경유하지 않는다(Router·`WikiAiRouterClient` 자체는 내신Wiki/Academy 등 다른 기능이 계속 사용). 모델 호출은 질문 1건당 정상 1회, fallback은 최대 1회이며 재검색·재시도 루프가 없다. 키는 `NVIDIA_API_KEY`/`OPENROUTER_API_KEY` 환경변수만 읽고 provider의 `reasoning_content`(내부 사고)는 화면·로그·저장에 노출하지 않는다. 설정은 `app.newstart.llm.*`에서 override 가능.
- 현재 사용처: `/newstart` NewStart AI 질문창. 답변 아래 `sources`를 "이 답변의 근거" 목록으로 렌더한다(2026-09-29 — 이전엔 백엔드가 내려주는 sources를 프론트가 폐기). `kind`는 사용자 언어로 표시(`공식 자료`→`공식 안내`, `실증 사례(DB)`→`실증 사례`), `url`은 `http/https`만 새 탭 `rel="noopener noreferrer"` 링크. `model`·provider 등 내부 정보는 화면에 내지 않는다.
- 인증/권한: 비로그인은 기존 `/api/**` catch-all이 `401`. 로그인 회원은 **THANKS_MEMBER** grant가 있어야 실행할 수 있다(`newstart_member_grants` row, ADMIN은 grant 없이 통과) — 미부여 회원은 `403` + `errorMessage` 안내. AI 호출(Router 비용)이 드는 실행형 기능이므로 "로그인 회원 = 전원 실행"으로 두지 않는다. 화면에서 입력창을 숨기는 것과 무관하게 API에서 강제한다.
- Request JSON: `message` (필수, 1~2000자)
- Response JSON: `status`(`ok`/`error`), `answer`, `errorMessage`(실패 시 사용자용 안내 문구), `model`(호출된 LLM이 반환한 모델 식별자 — `nvidia/nemotron-3.5-lightning-30b-a3b` 또는 fallback 시 `qwen/qwen3.7-flash`, 검증용), `sources`(배열; 답변 조립에 실제 사용된 근거 출처 최대 8건 — 각 `{kind,title,url}`, `kind`는 `공식 자료`/`운영자·전자책 자료`/`실증 사례(DB)`/`신복위 카페 사례`/`부실차주 카페 사례`/`참고` 라벨)
- 성공: `200` + `{"status":"ok","answer":"...","model":"...","sources":[...]}`
- Timeout 계약(2026-09-29 정렬): 서버 상한 = primary NVIDIA `timeout-seconds=180` + fallback Qwen `timeout-seconds=90` = 최대 270s. 프론트(`newstart.js`) abort는 **300s**로 서버 상한보다 크게 두어, 답변 생성 도중 프론트가 먼저 끊는 사고를 막는다(과거 120s abort는 budget 16384 시절 141s 답변을 잘랐다). 정상 경로(budget 1024) 실측은 약 41s.
- 실패: `400`(빈 질문·길이 초과·지식베이스 조회 실패·provider/네트워크 오류 공통), `403`(THANKS_MEMBER 미부여 회원) 모두 `{"status":"error","errorMessage":"..."}`. 내부 exception·provider 응답·키 값은 노출하지 않는다. 지식베이스 조회 실패 시 근거 없이 답하지 않고 정직하게 실패한다.
- RAG(2026-09-25 연결): 질문은 지피지기 Agent V1 강제 조사 사다리(`JipijigiResearchService`)를 통과한다 — 승인(ACTIVE) 지식·공식 업데이트·수집 사례·운영자 승인 답변 세션만 근거로 사용하며 REVIEW 자료는 검색되지 않는다. 컨텍스트 조립(2026-10-01 2단계 개편): STEP 1 = 전자책·운영자 자료 → 실제 사례(승인 카페 지식 → 축적 답변 → 실증 사례), STEP 2 = 현재 공식 자료만((refType,refId) 중복 제거, 각 최대 12건/8건). 답변 프롬프트는 근거 성격을 '전자책에서는'/'카페 사례에서는'/'공식 안내에 따르면'으로 구분하며 STEP 1에서는 공식 표현을 금지한다.
- 레이턴시 실측·로그(2026-09-28 LOCAL, NVIDIA 4회): 구간별 INFO 로그 — `NewStartAiChatController`(`authMs/serviceMs/handlerMs`), `NewStartAiChatService`(`ragMs/evidenceMs/promptMs/llmMs` + `systemPromptChars/promptChars/inputTokenEstimate/evidenceCount/answerChars`), `NewStartLlmClient`(`attempts/fallbackUsed/primaryMs/fallbackMs`), `NvidiaNimLlmTransport`(`httpMs/parseMs/promptTokens/completionTokens/finishReason/maxTokens/reasoningBudget`). 질문 원문·키·`reasoning_content`는 남기지 않고 길이와 ms·token 수만 남긴다. LOCAL 10개 질문 데이터 기준 `httpMs`가 전체의 97.9~99.5%이고 `finishReason=stop`(3~4k tokens)으로 `max_tokens`는 상한만 작용 → 병목은 생성/추론 구간. `reasoning-budget` 16384→1024(yaml 값만 변경, 코드 불변) 단일 변수 테스트: 같은 질문에서 서버 111.3s→40.6s(−63.5%), `promptTokens` 3109 동일·근거 12건·출처 8건(공식 4 + 부실차주 카페 4) 동일, 답변 478→1530자로 짧아지기는커녕 증가. 16384 상태의 세션 3질문은 111.5/141.0/112.8s였고 141.0s건은 프론트의 120s abort(`newstart.js`)로 화면에 못 닿는다. 부수 관측: 예산을 줄인 답변에 모델이 자체 작성한 `[KB2 공식]` 형태 인용 표기가 나타날 수 있다(DB 대조 결과 코퍼스 문구가 아니며, 지칭 번호는 실제 근거 1~8번과 대응) — 화면 `sources` 렌더가 없는 현재는 사용자 노출 차이가 크지 않으므로 후속 확인 항목.

### ✅ 지피지기 LLM primary = 기존 내신Wiki AI Router(Qwen) (2026-10-01 결정)

- `/api/newstart/ai/chat`·`/chat/official`의 LLM 호출 primary를 **기존 Wiki AI Router(`WikiAiRouterClient`, `provider=auto` → `qwen/qwen3.7-flash`)**로 바꿨다. NVIDIA NIM Nemotron은 응답시간이 29초~180초 초과로 크게 변동해(2026-09-30~10-01 LIVE 실측, 10-01 STEP 1이 180초 timeout) primary에서 제외했다.
- NewStart RAG(전자책·운영자 경험·카페/상담 사례, STEP 2 공식자료)는 그대로다. **NewStart가 먼저 검색·조립한 근거 system prompt를 Router에 전달해 답변만 생성**하며 Router/Qwen이 RAG를 하지 않는다. STEP 1 → 안내문 → STEP 2의 2단계 구조·사례 우선 원칙도 그대로다.
- Wiki Router의 설정·모델·API key는 변경·복사하지 않는다(NewStart 코드는 어떤 secret도 읽지 않는다). 응답 provider/model은 Router가 돌려준 값을 로그에만 남긴다.
- 선택 설정: `app.newstart.llm.primary`(환경변수 `NEWSTART_LLM_PRIMARY`) = `wiki-router`(기본) | `nvidia`. NVIDIA transport·설정은 삭제하지 않고 비교·재사용용으로 보존한다. fallback은 기존대로 OpenRouter Qwen DIRECT(앱 자신의 `OPENROUTER_API_KEY`가 있을 때만 동작 — 현재 운영 미설정이므로 사실상 비활성).
- Timeout: Router 호출 90초(Wiki provider 45초) 안쪽이며 프론트 abort 300초보다 작다. 아래 NVIDIA 180초 기술은 `primary=nvidia`일 때만 해당한다.

### ✅ 지피지기 2단계 답변 (2026-10-01) — `POST /api/newstart/ai/chat` → `POST /api/newstart/ai/chat/official`

운영원칙(질문 의도 → 전자책/실제 사례/운영자 경험/카페·상담 사례 → 실제 진행·대응 → 현재 공식자료 확인·보완 → 차이·불확실성)을 실제 호출 순서로 구현한다. 한 번의 LLM 호출에 공식자료를 함께 넣지 않는다.

- **STEP 1 — `POST /api/newstart/ai/chat`** (기존 경로·request 유지): 전자책·운영자 자료 + 실제 사례(승인 카페 지식·축적 답변·실증 사례)만 근거로 1차 답변을 만든다. **공식 자료는 근거·출처에 섞지 않는다.** 전자책이 컨텍스트를 채워도 직접 관련 사례에 최소 6칸(컨텍스트)·4칸(출처)을 보장한다. Response에 `stage:"CASE"` 추가(additive), 나머지 필드 불변.
- **A to Z 단계 연결(§7, 2026-10-02, additive)**: STEP 1 성공 응답에 질문을 **결정적 키워드**로 A to Z 단계에 연결한 `atozStepNo`/`atozStepTitle`/`atozStepUrl`(예 `2`/`지급명령 · 전자소송`/`/newstart/a-to-z#step-2`)을 추가한다. 신호가 없으면 세 필드 모두 null(존재하지 않는 것과 동일 취급). `NewStartAtozStep` 단일 출처(14단계, AI 판단·LLM 호출 아님)로 계산하며 **RAG 조사 사다리·답변 순서는 그대로** 두는 표시 계층만 얹는다. 화면(`newstart.js`)은 1차 답변 아래에 단계 이동 링크 버튼을 렌더한다. STEP 2는 이 필드를 붙이지 않는다.
- 화면은 STEP 1 응답을 받는 즉시 표시하고, 그 아래에 정확히 `관련된 현재 규정을 확인해서 알려드리겠습니다.`를 표시한 뒤 STEP 2를 요청한다.
- **STEP 2 — `POST /api/newstart/ai/chat/official`**: Request `{message, step1Answer}` (`step1Answer`=이미 표시된 1차 답변, 비교 입력일 뿐 서버는 수정·재생성하지 않고 길이만 2500자로 제한). 질문과 직접 관련된 현재 공식 자료(게시된 공식 업데이트 + ACTIVE 공식 지식)만 별도 조사해 "현재 공식 자료에서 확인되는 내용 / 1차 답변과 다른 점(시점 차이) / 추가 주의사항"을 2차 답변으로 만든다. Response `stage:"OFFICIAL"`, `sources`는 공식 자료만. 공식 자료를 찾지 못하면 LLM을 부르지 않고 확인할 기관만 안내한다(규정을 지어내지 않음). 인증·THANKS_MEMBER 판정은 STEP 1과 동일.
- **Timeout/실패**: STEP 2 실패·timeout은 STEP 2만의 실패(`400`)다. 화면은 이미 표시된 STEP 1을 유지하고 `현재 규정 확인이 지연되고 있습니다. 위 답변은 전자책과 실제 사례를 바탕으로 먼저 안내드렸습니다.`만 추가한다. STEP 1이 실패하면 STEP 2를 요청하지 않는다.
- 두 응답 모두 서버가 모델 출력의 `[KB2 공식]`류 인용표와 근거 컨텍스트에 없는 전화번호를 제거한다.

### ✅ 감사회원 최초 1회 셀프 인증 (2026-10-01) — 회원 `/api/newstart/thanks-verification` · 운영자 예외함 `/api/admin/newstart/thanks-exceptions`

- 목적: 감사회원(roster 503명) 대부분이 **운영자 승인 없이** 스스로 인증한다. 최초 1회 인증 후 roster ↔ OAuth userId 를 1:1 로 연결하고 `THANKS_MEMBER`를 자동 부여한다. 이후에는 입력 없이 네이버 로그인만으로 감사회원 기능을 쓴다(판정은 기존 `NewStartEntitlementService`의 grant row).
- **자동 인증 조건(전부 만족해야 하며 prefix 단독·닉네임 단독 인증은 없다)**: ① 회원이 입력한 카페 닉네임이 roster와 정확히 일치하고 후보가 정확히 1명 ② 네이버 OAuth 이메일이 `@naver.com`이고 로컬파트(= 네이버 로그인 ID)가 그 roster의 `naver_id_prefix`로 시작(접두부는 회원에게 보여주지 않으며, 2글자 접두부 포함 503명 전원에게 같은 로직을 적용한다) ③ roster가 다른 계정과 연결되지 않았고 현재 OAuth 계정도 다른 roster와 연결되지 않음. 만족하면 한 트랜잭션에서 roster `link` + `NewStartGrantService.grant`(grantedBy=`thanks-self`). 정상 인증은 운영자에게 알리지 않는다.
- 회원 API(로그인 필수): `GET /status` → `{state, message}`(`NONE|APPROVED|EXCEPTION|ADMIN`), `POST /` `{nickname}` → `{state, message}`(`APPROVED|FAILED|EXCEPTION|ADMIN|ERROR`; `ERROR`만 400). 실패 응답은 어느 항목이 틀렸는지 알리지 않는다. 24시간 내 단순 실패 3회(`max-failures-per-day`)면 예외로 승격한다. 같은 회원의 처리 대기 예외가 있으면 새 시도·중복 알림을 만들지 않는다.
- **예외 → ADMIN 예외함 + 운영자 이메일**(자동 인증이 안전하게 끝날 수 없는 경우만): `EMAIL_MISSING`(OAuth 이메일 없음), `EMAIL_NOT_NAVER`(@naver.com 아님), `AMBIGUOUS_CANDIDATES`(후보 2명 이상), `ROSTER_ALREADY_LINKED`, `DUPLICATE_OAUTH_LINK`(한 계정이 두 번째 roster 시도), `REPEATED_FAILURE`, `CONCURRENT_CONFLICT`(DB UNIQUE 경합). 예외는 커밋 직후 비동기로 메일을 보낸다 — 본문은 **닉네임·사유·발생시각(KST)·관리자 확인 링크만**(이메일 주소·네이버 ID·접두부·OAuth 식별값·비밀값 없음). SMTP는 환경변수(`NEWSTART_SMTP_HOST/PORT/USER/PASSWORD`, `NEWSTART_ALERT_FROM`, `NEWSTART_THANKS_ALERT_TO`)로만 설정하며 미설정이면 예외함 적재만 하고 `email_status=NOT_CONFIGURED`로 남긴다.
- 운영자 예외함(ROLE_ADMIN): `GET ?status=OPEN`, `POST /{id}/resolve {rosterId?}`(연결 승인 — 자동 인증과 같은 불변식과 네이버 subject를 다시 확인, grantedBy=`thanks-exception:<운영자>`), `POST /{id}/dismiss {note?}`. DB UNIQUE 경합은 `409`. 정상 인증은 예외함에 나오지 않는다.
- **ADMIN**: 인증 절차를 거치지 않는다. `GET /status`·`POST /`가 `state=ADMIN`을 돌려주고 아무 기록·연결·부여·알림도 만들지 않으며, 화면은 인증 섹션을 보여주지 않는다. 감사회원 전용 기능 판정은 `NewStartEntitlementService` 한 곳에서 ADMIN을 항상 통과시킨다(모든 현재·향후 `NewStartCapability`).
- OAuth 이메일: 로그인마다 OAuth가 준 이메일을 `user_social_accounts.provider_email`에 보관한다(`users.email`은 최초 가입 때만 채워져 이메일 동의 이전 가입자·이메일 변경자는 비어 있었다). 판정 전용이며 화면·메일·로그에 노출하지 않는다.
- 화면: 회원 `/newstart/thanks-guide`(카페 닉네임 입력 1개), 운영자 `/admin/newstart/thanks-exceptions`.
- **신규 감사회원 사전등록**(카페 roster에 없는 회원, 운영자 전용 `/api/admin/newstart/thanks-preregistrations`): `GET` 목록, `POST {email, memo, cafeNickname?, naverIdPrefix?, cafeMemberTag?, memberGrade?}` 등록(`@naver.com` 형식만, 소문자 정규화, 중복 400/409; identity 를 주면 roster 503명·기존 사전등록과 카페 멤버 태그/닉네임이 겹칠 때 거절 — 동일인을 두 번째 identity 로 만들지 않는다. 카페 접두부는 OAuth 이메일 로컬파트와 달라도 되며 인증 판정에는 **이메일 정확 일치만** 쓴다), `DELETE /{id}` 미연결 건만 삭제. 인증은 **회원이 이메일을 입력하지 않는다** — 회원 `GET /api/newstart/thanks-verification/status`(및 `POST`) 호출 시 **Naver OAuth가 실제 반환한 이메일**(`user_social_accounts.provider_email`)이 등록값과 정확히 일치하면 OAuth userId와 1:1 연결 + `THANKS_MEMBER` 부여(grantedBy=`thanks-prereg`), 이후 네이버 로그인만으로 이용. 이메일은 소스에 두지 않고 DB에만 저장하며 회원/공개 응답에는 노출하지 않는다(운영자 응답에만 포함). 기존 roster 503명의 자동 인증은 그대로.
- DB: `V1017__add_newstart_thanks_self_verification.sql` — `user_social_accounts.provider_email`, 신규 `newstart_thanks_verifications`(시도·예외 기록), `newstart_thanks_roster.linked_user_id`·`linked_provider_user_id` UNIQUE(1:1을 DB가 강제, NULL 다중 허용). V1015(503명)·V1016(naver_id_prefix)은 수정하지 않는다.

### ✅ 감사회원(THANKS_MEMBER) 권한 부여·회수 — ADMIN

- 목적: 지피지기 AI·금감원 Agent 세션·민원 초안 정리를 회원이 쓰려면 `newstart_member_grants` row가 있어야 한다. 그 row를 SQL 없이(curl·운영 화면) 넣고 뺄 수 있는 ADMIN API. (질문요청 쓰기는 2026-09-29부터 grant 대상이 아니다 — 일반회원 기본 기능.)
- `GET /api/admin/newstart/grants` — 목록. 항목은 `{grantId,userId,capability,grantedBy}`.
- `POST /api/admin/newstart/grants` — body `{userId, capability?}`(생략 시 `THANKS_MEMBER`). **idempotent**(이미 있으면 기존 row 반환 — `UNIQUE(user_id, capability)`와 겹치는 INSERT를 하지 않는다). 존재하지 않는 user_id는 `400` + `errorMessage`.
- `DELETE /api/admin/newstart/grants/{userId}?capability=THANKS_MEMBER` — 회수. row가 없으면 `400`.
- 인증: `/api/admin/**`는 SecurityConfig의 `hasRole('ADMIN')` — controller에 별도 규칙을 두지 않는다(AdminNewStartRequestController와 동일).
- PII: 요청·응답은 숫자 user_id와 capability만 다룬다(email·실명·닉네임은 이 경로를 통과하지 않는다).
- 부여는 즉시 반영된다 — `NewStartEntitlementService.allows()`가 매 요청에서 grant table을 보므로 회원의 재로그인·토큰 재발급이 필요 없다.


### NewStart 회원 채팅 (공용방 1개, 텍스트 전용)

회원 ↔ 회원 실시간 채팅. NewStart AI(`POST /api/newstart/ai/chat`)와 무관하며 회원 대화는 AI(Router/RAG/학습)에 사용하지 않는다. 인증은 기존 `/api/**` catch-all(인증 필요)을 따른다 — 비로그인은 401. 작성자·표시 이름은 서버가 principal에서만 결정한다(클라이언트 userId/nickname 전송값 없음). 표시 이름은 `nickname` 우선 → `loginId`(ADMIN=관리자)로 서버에서 resolve해 내려주며 실명·이메일·전화는 노출하지 않는다.

- `GET /api/newstart/chat/messages?before={id}&limit={1~50, 기본 30}` — 최근 메시지(삭제 제외)를 오래된 순으로. `{ "messages": [ { "id", "senderUserId", "displayName", "message", "deleted", "createdAt" } ], "hasMore" }`. `before` 지정 시 해당 id보다 오래된 메시지(위로 스크롤 pagination).
- `GET /api/newstart/chat/after?after={id}` — 재접속 복구. 해당 id보다 새로운 메시지(오래된 순, 최대 50건).
- `DELETE /api/newstart/chat/messages/{messageId}` — 본인 메시지 삭제(ADMIN 허용). 타인 메시지는 403. soft delete — 성공 `{ "status": "ok" }`, 실패 `{ "status": "error", "errorMessage" }`.
- `WS /ws/newstart-chat` — raw WebSocket(동일 origin + 로그인만 handshake 통과, 비로그인 401/교차 origin 403). 클라이언트 → 서버 `{ "message": "본문" }`(500자 상한, 빈 값 거부, 도배 방지 10건/15초). 서버 → 클라이언트 `{ "type": "new", "id", "senderUserId", "displayName", "message", "createdAt" }` / `{ "type": "deleted", "id" }` / `{ "type": "error", "message" }`.

DB: `newstart_chat_messages`(V981) — `message_id` PK, `user_id` FK(users.user_id), `message` VARCHAR(500), `deleted` soft delete flag. 화면: `/newstart/chat`(공개 — 데이터는 전부 인증 뒤, 비로그인 시 로그인 모달 안내).

### NewStart 접속상태 + 내부 쪽지 (V1017)

HOME 왼쪽 패널(PC 상시 레일 + 모바일 슬라이드)의 접속 회원 목록과 1:1 내부 쪽지. GRANT/OAuth/THANKS 판정과 무관 — 인증 회원 전체가 사용하고, 비로그인은 `/api/**` catch-all이 `401`.

- `POST /api/newstart/presence/ping` — heartbeat 수신 + 본인 설정 반환. 60초 이내 중복은 쓰기 생략.
- `GET /api/newstart/presence/online` — 현재 접속 회원(숨김·본인 제외, 최근 본 순, 최대 100). `{ userId, displayNickname, lastSeenAt }` — 별명은 서버 resolve(식별자 아님).
- `GET /api/newstart/presence/settings` / `PUT /api/newstart/presence/settings {hideOnline, dmBlocked}` — 본인 설정 조회·변경.
- `POST /api/newstart/dm/send {receiverUserId, message}` — 전송(≤1000자). 수신 거부·본인·없는 회원·도배는 거부(`400`, 도배만 `429`).
- `GET /api/newstart/dm/inbox?before=&limit={1~50, 기본 20}` — 받은 쪽지함(최신순).
- `GET /api/newstart/dm/unread-count` — `{ unreadCount }`.
- `POST /api/newstart/dm/{messageId}/read` — 읽음 표시(수신자 본인만).
- `DELETE /api/newstart/dm/{messageId}` — 받은 쪽지 삭제(수신자 본인만, 단일 복사본 삭제).

DB: `newstart_member_presence`(V1017) + `newstart_direct_messages`(V1017). 쪽지 삭제·수정 endpoint 없음. RAG·AI 학습에 사용하지 않는다.

### NewStart 질문요청 게시판 (설계 §5, V1012)

[질문·조사 요청]과 [사이트 개선 요청]을 한 인박스로 받는 게시판. **읽기·쓰기(작성·취소) 모두 인증 회원 전체**다 — 질문요청은 일반회원의 기본 기능이며 감사회원(THANKS_MEMBER) grant를 요구하지 않는다(2026-09-29 운영정책, §5-B blocker 해제). 운영자의 상태 관리는 `/api/admin/**`(hasRole ADMIN) 경로로만 들어온다. GitHub issue 자동 생성은 없다 — `githubRef`는 반영 시점에 운영자가 수기로 기입한다.

- `GET /newstart/questions` — 화면 shell(채팅과 동일: 페이지는 공개, 데이터는 전부 인증 뒤. 비로그인은 로그인 안내).
- `GET /api/newstart/requests?kind={QUESTION|SITE_IMPROVEMENT}&page={0..}&size={1~50, 기본 20}` — 최근 작성 순. `{ "requests": [ RequestView ], "hasMore" }`. `kind` 생략 시 두 인박스 합산.
- `GET /api/newstart/requests/{requestId}` — 상세. 목록과 달리 `statusLogs`(상태 이동 이력, `actor`는 "운영자"로 통일 노출)를 싣는다. 삭제·없음은 `404` + `errorMessage`.
- `POST /api/newstart/requests` — 작성(인증 회원 누구나). 화면 폼은 2026-09-29 단순화로 `kind`(필수), `title`(필수 ≤200자), `problem`(필수 ≤4000자)만 입력받는다. Request JSON에 `proposal`(≤4000자), `screenLocation`(≤120자), `aToZStep`(`"13-2"` 형태)을 여전히 받으며 저장도 된다(구 데이터·운영 경로 보존, `screenLocation`/`aToZStep`은 `SITE_IMPROVEMENT`에서만 저장). 작성자 userId는 본문이 아니라 principal에서만 채워진다.
- `DELETE /api/newstart/requests/{requestId}` — 취소(본인 또는 ADMIN). soft delete이며 `title`/`problem`/`proposal` 원문은 파기하고 이력 row만 남긴다. 타인 것은 `403`.
- `GET /api/admin/newstart/requests/queue?status={8종, 기본 RECEIVED}` — 검토 큐(같은 상태가 오래된 순).
- `POST /api/admin/newstart/requests/{requestId}/review` — 상태 이동. Request JSON `status`, `answer`, `reason`, `githubRef`, `resolvedVersion`. `REJECTED`/`DUPLICATE`는 `reason` 필수(미기입 `400`), `RESOLVED`일 때만 `githubRef`/`resolvedVersion`을 기록하고 `resolvedAt`을 세운다.

`RequestView`: `{ id, kind, title, screenLocation, aToZStep, problem, proposal, status, answer, answeredBy, resolvedAt, githubRef, resolvedVersion, authorId, author, createdAt, statusLogs }` — `author`는 서버가 resolve한 공개 표기(실명·이메일·전화 아님), `authorId`는 취소 버튼 표시 판정용(채팅 `senderUserId`와 같은 공개 범위)이고 실제 권한 근거는 항상 서버 principal이다.

상태 8종: `RECEIVED`(접수) · `REVIEWING`(검토 중) · `ANSWERED`(답변 완료) · `ACCEPTED`(채택) · `IN_PROGRESS`(반영 진행 중) · `RESOLVED`(해결 완료) · `REJECTED`(거절) · `DUPLICATE`(중복).

인증/권한 요약: 비로그인은 `/api/**` catch-all이 `401`. 작성은 인증 회원 전체(2026-09-29 정책 — grant/THANKS_MEMBER 판정 없음). 검증 실패는 `400` + `errorMessage`.

### NewStart 갈무리 게시판 (설계 §6, V1012)

운영자 승인(`ACTIVE`) 지식 항목을 회원에게 노출하는 **read-only feed**. 새 테이블·새 게시판이 아니라 `newstart_knowledge_items`를 그대로 읽는다(§6 "중복 테이블 금지"). **쓰기 endpoint가 없다** — POST는 `405`. 회원이 글을 올리는 공간이 아니라 채팅·카페·사례·질문요청을 분석·비식별화·요약해 승인한 결과물을 보는 화면이기 때문이다.

- `GET /newstart/galmuri` — 화면 shell(채팅·질문요청과 동일: 셸은 공개, 데이터는 인증 뒤).
- `GET /api/newstart/galmuri?category={분류}&page={0..}&size={1~50, 기본 20}` — ACTIVE이면서 **오늘이 효력 구간인** 항목을 출처 기준시점(`source_date`, 없으면 등록시점) 최신순. `{ "items": [ GalmuriCard ], "hasMore" }`. **노출 출처 경계(2026-09-29 source 교정)**: feed는 community 수집 출처(`CASE`·`CAFE_ARTICLE`·`CAFE_COMMENT`·`CHAT_DISCOVERY`·`REQUEST_DISCOVERY`)만 담는다. `OFFICIAL_*`/`AUDIT`/`EBOOK`/`DERIVED_RULE`/`OPERATOR_KNOWLEDGE`(= Wiki·KB)는 갈무리 게시물이 아니라 지피지기 RAG의 보조 근거이므로 목록·상세에서 모두 `404`/미노출.
- `GET /api/newstart/galmuri/{knowledgeId}` — 상세(본문 `content` + `sourceUrl`). REVIEW/DRAFT/ARCHIVED이거나 `effective_to`가 지났으면 목록과 같은 기준으로 `404` + `errorMessage`(본문 회수).

`GalmuriCard`: `{ id, title, summary, category, label, tone, authority, sourceDate, effectiveTo, tags[] }` / 상세 추가 필드: `{ content, effectiveFrom, sourceUrl }`.

- `label`(§4 신뢰 등급)은 **서버가 `source_type`에서 결정**한다 — 프론트가 자체 판정하면 라벨과 배지 색이 어긋난다. `OFFICIAL_CURRENT`→`[공식·현재]`, `OFFICIAL_HISTORICAL`→`[공식·과거]`, `OPERATOR_KNOWLEDGE`/`AUDIT`→`[운영자 실제 경험]`, `CASE`/`CAFE_*`/`CHAT_DISCOVERY`/`REQUEST_DISCOVERY`→`[사례 참고]`, `EBOOK`/`DERIVED_RULE`→`[전자책·운영 정리]`. `tone`은 `official`/`operator`/`case` 3계(화면은 색만 입힌다).
- 회원 개인정보성 필드(`created_by`, `related_case_ids`, `related_rule_ids`)는 view에 존재하지 않는다. 원출처 링크는 `http/https`만 렌더(`javascript:` 차단) + `rel="noopener noreferrer"`.
- 자동 적재(Agent 분석 → 중복 제거 → 갈무리)는 **미구현·별도 승인** — §8에 따라 scheduler와 production 자동 write를 이 단계에서 만들지 않는다. 배선은 읽기 side + 중복 앵커(`dedup_key`) + `source_type` 확장(`CHAT_DISCOVERY`, `REQUEST_DISCOVERY`)까지.

# API 명세

## BuildIt Wiki 학교 프린트·문제 모델

- `POST /problem-generator/wiki/api/questions/generate`, `.../questions/transform`: 문제 화면이 선택한 OpenRouter provider/model을 명시한다. 기본은 `google/gemini-3.7-flash`이며 명시적 선택은 실패 시 무료 모델로 fallback하지 않는다. 응답의 실제 `provider`/`model`을 UI에 표시한다.
- `POST /problem-generator/wiki/api/school-prints/reconstruct`: 브라우저 Tesseract/PDF 텍스트 레이어로 1차 추출한 페이지별 `ocr_text`와 각 원본 `image_data_url`을 함께 받아 Qwen `qwen/qwen3.7-flash`로 실제 편집 가능한 `html`과 `canonicalJson`을 반환한다. `canonicalJson`은 source/page linkage, 페이지별 `extractedText`, `editedHtml`, `restorationStatus`, `restorationVersion`을 보존한다. 2차 실패 시 프론트는 API 오류를 받아 이미 확보한 1차 텍스트를 편집 HTML로 유지한다.
- `POST /problem-generator/wiki/api/school-prints/patch-html`: 현재 HTML/canonical JSON과 수정 지시를 받아 지적된 부분만 수정한다.

> 현재 프론트엔드는 Thymeleaf SSR 중심이지만, 향후 React 클라이언트와 Python 서버 연동을 고려해 이 문서를 **API 계약의 단일 기준 문서**로 유지한다.

## 0. 운영 규칙

### ✅ POST /problem-generator/wiki/api/passages/memory

- 목적: 외부 영어 지문을 문장별 영문·한국어 해석 쌍으로 변환한다.
- 현재 사용처: Wiki `학생 → 지문 암기`
- 인증/권한: 기존 Wiki 프록시 정책을 따른다.
- Request JSON: `source_text` (`api_key`는 이전 클라이언트 호환용으로 허용하지만 무시)
- Response JSON: `title`, `sentences[{english,korean}]`, `provider`, `model`
- 실패: 빈 지문 `400`, Qwen 호출 실패 `502/503/504`. 핵심 분석이므로 무료 모델 fallback은 하지 않는다.

### ✅ POST /problem-generator/wiki/api/passages/analysis

- 목적: 6페이지형 지문 분석 데이터와 내용일치 T/F 10문항을 생성한다.
- 현재 사용처: Wiki `만들기 → 지문 정리 + 내용일치 문제`
- 인증/권한: 기존 Wiki 프록시 정책을 따른다.
- Request JSON: `source_text` (`api_key`는 이전 클라이언트 호환용으로 허용하지만 무시)
- Response JSON: `title`, `topic`, `mainIdea`, `summary`, `sentences[{slashed,directTranslation,translation,grammarGuide}]`, `tfQuestions[{language,statement,answer}]`, `provider`, `model`
- 검증: T/F가 정확히 10개가 아니면 `502`로 재시도를 요청한다.

### BuildIt Wiki AI 공통 정책

- 일반 요청의 `provider=auto`는 서버 환경변수 `OPENROUTER_API_KEY`로 OpenRouter의 `qwen/qwen3.7-flash`를 사용한다.
- Assistant, Wiki, 기출/자료 분석, JSON 구조화, Digital Twin 분석, 일반 문제 생성, 기존문제 변형의 기본 모델은 Qwen3.7 Flash다.
- 일반 문제 생성·기존문제 변형·자료 분석 등 핵심 결과는 Qwen 실패 시 무료 모델로 조용히 fallback하지 않는다.
- Assistant/OCR 같은 비핵심 보조 호출만 Qwen → Ox Alpha → `openrouter/free` 백업 체인을 명시적으로 허용한다.
- `gemini`, `ox-alpha`, `openrouter:<model>` 명시 선택은 admin 진단 호환을 위해 유지하며 자동 fallback하지 않는다.
- 요청의 `api_key`/`openrouter_api_key` 레거시 필드는 호환을 위해 허용하되 인증에 사용하지 않는다. 키는 응답·로그에 포함하지 않는다.
- AI 응답은 가능한 모든 endpoint에서 실제 응답의 `provider`와 `model`을 그대로 반환한다.
- GPT-5.6 Luna는 향후 변형문제 제작·중요 검수 확장 지점이며 현재 구매나 연결을 요구하지 않는다.

### 문서화 대상

다음 변경이 있으면 코드 변경과 같은 커밋/작업 단위에서 이 문서를 함께 수정한다.

- Controller endpoint 추가/삭제/변경
- HTTP method 또는 URL path 변경
- path/query parameter 변경
- form field 또는 request body 구조 변경
- response body, redirect 경로, status code 변경
- 에러 응답 형식 변경
- Thymeleaf SSR 전용 endpoint라도 React/Python에서 호출할 가능성이 있는 계약 변경

### 작성 형식

각 endpoint는 가능한 한 아래 형식을 따른다.

```md
### 상태마커 METHOD /path

- 목적:
- 현재 사용처:
  - Thymeleaf SSR / JavaScript fetch / 내부 호출 / 향후 예정 중 선택
- 향후 사용 가능성:
  - React client / Python server / 없음 중 선택
- 인증/권한:
- Request:
  - Path parameters:
  - Query parameters:
  - Form fields 또는 JSON body:
- Response:
  - 성공:
  - 실패:
- Redirect:
- 관련 문서:
```

### 상태 마커

- ✅ 구현됨: 실제 코드에 존재하고 현재 동작하는 endpoint
- ⚠️ 부분 구현: endpoint는 있으나 인증, 검증, 응답 형식 등 보완이 필요한 상태
- 🚧 예정: 문서상 계획만 있고 아직 코드가 없는 상태

### 수정하지 않는 경우

API 계약 변경이 없는 작업은 최종 보고에 다음처럼 명시한다.

```text
API.md: endpoint/request/response/redirect 변경 없음 → 수정 없음
```

## 1. 공통 계약

### 이미지 업로드/서빙 포맷

모든 이미지 업로드 경로는 `ImageSanitizer.sanitize`를 거친다. 무검증 통과는 없다.

| 입력 | 저장 포맷 | 저장 키 확장자 | 서빙 Content-Type |
|---|---|---|---|
| 애니메이션 GIF (프레임 2장 이상) | GIF, **프레임·지연·반복 보존** (재인코딩) | `.gif` | `image/gif` |
| 애니메이션 WebP (VP8X ANIM) | WebP, **픽셀 원본 그대로** (검증 + 메타데이터 제거) | `.webp` | `image/webp` |
| 그 외 (JPEG/PNG/정지 GIF/정지 WebP/HEIC 등) | JPEG (품질 0.82) | `.jpg` | `image/jpeg` |

- **Content-Type은 추측하지 않는다.** Content-Type 컬럼이 있는 곳(`community_images`,
  `academy_blog_sources`)은 그 값을, 없는 곳(Source·Academy 로고/커버·히어로)은 저장 키의 확장자를
  쓴다(`ImageSanitizer.contentTypeForStorageKey`).
- 기존 JPEG 오브젝트는 backfill하지 않는다. 확장자 없는 레거시 키는 JPEG로 판정돼 그대로 서빙된다.
- ffmpeg가 만드는 영상 썸네일과 PDF 페이지는 항상 JPEG다(위 정책 대상 아님).
- Claude Vision에 보내는 media type도 원본 파일명이 아니라 저장 키로 판정한다
  (`VisionMediaType.forStorageKey`).

#### 재인코딩 경로 (GIF / JPEG)

- 최장변 1600px(`ImageSanitizer.MAX_DIMENSION`)로 축소한다.
- 재인코딩 자체가 악성 페이로드와 EXIF를 떨궈내는 방어선이다.
- 애니메이션 GIF는 프레임 300장, 논리 화면 4천만 픽셀, 출력 20MB 상한을 넘으면 **거부**한다.
  잘린(trailer 없는) GIF도 거부한다 — 관대하게 읽으면 프레임이 조용히 사라진다.

#### 애니메이션 WebP만 재인코딩하지 않는 이유 (`AnimatedWebp`)

WebP 인코더가 없다. JDK ImageIO에도, 이 환경의 ffmpeg에도 없다(`--enable-libwebp` 미포함이라
네이티브 디코더가 ANIM/ANMF를 처리하지 못한다). 인코더 없이 가능한 유일한 대안인 GIF 변환은 같은
애니메이션을 **2.8배**로 불린다(측정: 380KB → 1.06MB). WebP를 쓰는 이유가 용량이므로 채택하지 않았다.

대신 **픽셀 데이터(VP8/VP8L/ALPH)는 원본 바이트 그대로 두고** 다음을 수행한다.

1. 구조 검증 — 청크 워크가 파일 끝에 정확히 맞아떨어지는지(잘린 파일 거부)
2. 용량 예산 — 20MB / 프레임 1500장 / 캔버스 4천만 픽셀 초과 시 거부
3. **컨테이너와 디코더의 프레임 수 일치 확인** — 프레임이 조용히 유실된 파일 거부
4. `EXIF`/`XMP `/`ICCP` 청크 제거 + VP8X 플래그 정리 — 사진의 GPS 좌표가 공개 페이지로 새는 것 방지

**픽셀은 디코딩하지 않는다.** 두 방식을 시도했다가 모두 걷어냈다.

- **전수 디코딩** — 469프레임(629x452, 13MB) 파일에서 **15.6초 / 힙 301MB**. 업로드 요청 안에서
  감당할 수 없고, 큰 움짤 몇 개로 요청 스레드를 묶는 DoS가 된다.
- **표본(첫·마지막) 디코딩** — 값어치가 없었다. **우리 디코더가 브라우저보다 약하다.** 실제 파일에서
  `ALPH`(알파) 프레임 28개 중 10개가 `ArrayIndexOutOfBoundsException: Coordinate out of bounds!`로
  깨졌는데, 컨테이너상 캔버스를 벗어나지 않는 정상 프레임이었고 브라우저는 정상 재생했다
  (TwelveMonkeys 3.11.0·3.12.0 동일). 이걸로 판정하면 **멀쩡한 움짤 업로드가 막힌다** — 실제로 막혔다.
  반대로 페이로드가 쓰레기여도 리더는 캔버스 크기 이미지를 그냥 돌려주므로 "디코딩되니까 정상"이라는
  보증도 못 준다.

재인코딩이 아니라 원본 바이트를 그대로 저장하므로, 확인용 디코더가 실제 렌더러보다 엄격할 이유가 없다.
현재 방식은 같은 파일에서 **87ms**다.

> 프레임 상한이 1500인 이유: 실제 움짤은 프레임이 많다(위 테스트 파일이 469프레임 = 15초 클립).
> 검증 비용이 프레임 수에 비례하지 않으므로 실질 상한은 20MB 쪽이다.

**보장하지 않는 것** (재인코딩 경로와의 차이 — 리뷰 시 이 차이를 인지할 것):

- 픽셀 스트림에 심어진 페이로드를 떨궈내지 못한다. 재인코딩만이 그걸 한다.
- 픽셀 내용의 무결성도 보장하지 않는다 — VP8은 오류 내성 코덱이라 페이로드가 수천 바이트 손상돼도
  예외 없이 "깨진 그림"으로 디코딩된다(실측).
- 프레임이 실제로 그려지는지도 보장하지 않는다 — 픽셀을 디코딩하지 않는다(위 참고).
- 최장변 1600px 축소가 걸리지 않는다. 그래서 `AnimatedWebp.MAX_BYTES`(20MB)로 직접 묶는다.

> 정지 WebP는 현행대로 JPEG로 재인코딩된다. WebP가 JPEG보다 작으므로 정지 이미지도 WebP로
> 저장하는 것이 용량상 유리하지만, 그건 모든 이미지에 영향을 주는 별도 범위라 분리한다.

### 인증/인가

- 인증 방식: JWT(HS256) HTTP-only Cookie.
- 로그인 성공 시 `POST /api/auth/login`이 access token cookie를 내려준다.
- SSR view route와 정적 리소스는 대부분 public shell이다. 실제 데이터 API는 SecurityConfig에서 보호한다.
- 예외: `/conversation/chat`, `/conversation/chat.html`은 ADMIN/TUTOR 전용이다. 인증된 STUDENT가 직접 접근하면 `/stdt_dashboard`로 302 redirect된다.
- Public API:
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `POST /api/auth/password-reset/request`
  - `POST /api/auth/password-reset/confirm`
  - `POST /api/users/students`
  - `GET /api/subjects`
- Admin API:
  - `/api/admin/**`는 `ROLE_ADMIN` 필요.
- Archived alias:
  - 정확히 `/archived` 또는 `/archived/**`만 기존 화면/API를 복사하지 않고 원래 target path로
    단일 `REQUEST`에서 정규화하는 보존 라우트다. servlet forward는 사용하지 않는다.
  - 기본 설정은 `app.archived.enabled=true`, `app.archived.require-admin=true`이며 ADMIN만 접근 가능하다.
  - 예: `GET /archived/conversation/dashboard` → `GET /conversation/dashboard`, `POST /archived/api/conversations` → `POST /api/conversations`.
  - archive gate와 target 인가/소유권/validation은 AND로 합성된다. archive ADMIN이라도 target이
    TUTOR 전용이면 403이며, mutating `/archived/api/**`도 canonical `/api/**` Origin/Referer 검사를 받는다.
  - `/archived`와 `/archived/`는 `/home` target이며, 중첩 alias·ambiguous encoded 첫 segment·
    semicolon/matrix 첫 segment는 400이다. `enabled=false`는 downstream 검사 전에 404다.
  - 보안 보장은 현재 하나의 catch-all `SecurityFilterChain`을 전제로 한다.
  - 상세 정책은 [ARCHIVED_FEATURES.md](ARCHIVED_FEATURES.md)를 따른다.
- Tutor API:
  - `/api/tutor/**`는 `ROLE_TUTOR` 필요. `/api/tutor/conversations/**`는 추가로 강사 본인 소유 기록만 허용한다(Admin도 제외).
- 그 외 `/api/**`는 인증 필요.
- Admin 임퍼소네이트:
  - 일부 학생/자료 API는 `targetStudentId` 또는 `targetUserId` query/form field를 받는다.
  - `AuthenticatedUserResolver`가 ADMIN이면 target 값을 허용하고, STUDENT면 자기 자원으로 고정하거나 403 처리한다.

### 공통 실패 응답

`GlobalExceptionHandler` 기준 현재 공통 실패 응답은 다음과 같다.

| 상황 | HTTP | Body |
|------|------|------|
| 인증 없음/만료 | 401 | Spring Security 기본 body 없음 |
| 권한/IDOR 차단 | 403 | `text/plain` 에러 메시지 |
| `IllegalArgumentException` | 400 | `text/plain` 에러 메시지 |
| `RejectedFilesException` | 400 | `{ "message": "업로드 파일 중 일부가 거부되었습니다.", "rejectedFiles": [{ "fileName": string, "reason": string }] }` |
| `SourceProcessingNotCompleteException` | 409 | `{ "message": string, "pendingSources": [{ "sourceId": number, "fileName": string, "status": "PENDING"|"ANALYZING"|"PENDING_REVIEW"|"COMPLETED"|"FAILED" }] }` |
| 파일 preview/download IO 실패 | 404 | body 없음 |

### 404 Page Redirect

- `GET`/`HEAD /error`에서 `RequestDispatcher.ERROR_STATUS_CODE == 404`이고 원 요청 URI가 일반 화면 route이면 `/home`으로 302 redirect한다.
- 일반 화면 route 제외 대상: `/api/**`, `/css/`, `/js/`, `/img/`, `/swagger-ui/`, `/v3/api-docs/`, `/actuator/`, `/favicon.ico`, `/robots.txt`, `/sitemap.xml`, 마지막 path segment에 파일 확장자가 있는 URI.
- 위 조건을 만족하지 않는 error 요청은 원 HTTP status를 body 없이 반환한다. 예: API/static/file-extension/non-GET 404는 404 유지.

### 주요 ENUM

- `Role`: `STUDENT`, `ADMIN`, `TUTOR`
- `ContentKind`: `GENERAL`, `EXAM_BLANK`, `EXAM_GRADED`, `UNCLASSIFIED`
- `ProcessingStatus`: `PENDING`, `ANALYZING`, `PENDING_REVIEW`, `COMPLETED`, `FAILED`
- `ExamType`: `MIDTERM`, `FINAL`, `MOCK`, `OTHER`
- `QuestionType`: `MULTIPLE_CHOICE`, `SHORT_ANSWER`
- `AcademyStatus`: `DRAFT`, `PUBLISHED`
- `TargetGroup`: `ELEMENTARY`, `MIDDLE`, `HIGH`, `UNIVERSITY`, `ADULT`
- `LessonType`: `SUBSCRIPTION`, `SHORT_TERM`
- `PriceUnit`: `MONTH`, `COURSE`, `NEGOTIABLE`
- `ScheduleType`: `WEEKLY`, `PERIOD`, `CONSULTATION`
- `LessonStatus`: `DRAFT`, `PUBLISHED`, `HIDDEN`
- `LessonWeekday`: `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, `SUNDAY`
- `LearningMaterialStatus`: `ASSIGNED`, `COMPLETED`
- `SourceType`: `STUDENT_MATERIAL`, `HOMEWORK_PROBLEM`, `HOMEWORK_ANSWER`, `TUTOR_MATERIAL`, `HOMEWORK_FEEDBACK` (V11 추가 — 강사 피드백 PDF용, PR2에서 엔티티·엔드포인트 구현 완료), `WRONG_NOTE` (V904 — 오답노트 수동 업로드), `CONSULTATION` (V911 — 강사/Admin 상담 피드백 자료)
- `LessonApplicationStatus`: `APPROVED`, `CANCELED`

## CHOONGGI public vlog (2026-09)

- `GET /choonggi`: Vlog-first home with confirmed Sep 25 journey, Sep 30 tournament status and Season 1 journal.
- `GET /choonggi/vlog`: Season 1 episode index.
- `GET /choonggi/vlog/{slug}`: Episode placeholder/detail; unknown slugs return 404.
- `GET /choonggi/gamer`, `/choonggi/india`, `/choonggi/about`: public, read-only journal pages.
- No API or database is added. Unconfirmed facts remain `---`, `TBA` or `RESULT PENDING`.
- Canonical root: `https://feel2real.co.kr/choonggi`.

## 2. SSR View Routes

`WebMvcConfig`가 template view를 직접 매핑한다. 대부분 public이며, 페이지 진입 후 JS가 API 인증 상태를 확인한다. AI Chat 화면만 SecurityConfig에서 ADMIN/TUTOR 전용으로 보호한다.

| Route | View template | 현재 사용처 |
|-------|---------------|-------------|
| `/` | `public/landing` | ✅ **Build It** 브랜드 랜딩(2026-08 개편, 시안 `docs/archive/archived_html/홈피개선.html`). Hero → Build It Web / Shorts / Report / Wiki → 문의 순의 스크롤 연출 페이지. `LandingPageController`가 주입하는 SSR 모델은 `academyShortsVideo`(=`ACADEMY_SHORTS_DEMO` 슬롯, Build It Web 섹션 영상) 하나뿐이다 — 공개+managed asset이면 공개 proxy stream, 번들이면 정적 mp4, 숨김이면 media element 대신 준비 중 프레임을 렌더한다. Wiki 섹션 3단계 중 `기출 분석`/`변형문제 생성`은 번들 정적 mp4(`digital_twin_ex.mp4` / `how2use_digitaltwin.mp4`)를 직접 참조하고, `학교별 Wiki`는 영상 없이 mock 대시보드다. 개편으로 구 랜딩의 실시간 빌더 미리보기·베타 의견 폼·후기 캐러셀은 제거됐다(`betaCarousel` 미주입). 브랜드 표기는 nav·footer 모두 Build It 워드마크이며 F2R 로고는 쓰지 않는다 — footer는 공용 fragment의 `publicFooterBuildIt` 변형을 쓴다(본문·사업자 정보는 `publicFooterBranded(buildIt)` 하나를 `/home`과 공유). 사용설명서(`/manual/index.html`)는 같은 탭으로 이동하며 매뉴얼 좌상단 `.manual-back`으로 돌아온다. |
| `/home`, `/home.html` | `public/home` | F2R 메인 랜딩 — AI 기반 내신 영어 학습 플랫폼 (학생 대상). 로그인 여부와 무관한 최종 student landing이며 미로그인 서비스 진입은 로그인 모달을 연다. |
| `/academy` | `home` | 디지털 학원 창업 플랫폼 랜딩 (강사/창업 대상, 구 `/home`). 우측 상단 [로그인] 버튼은 공통 로그인 모달을 연다. |
| `/eduholic` | — (301) | 구 학생 랜딩 URL. `/home`으로 **301 영구 리다이렉트** (북마크/외부 링크 보존) |
| `/login`, `/login.html` | redirect | 일반 로그인 페이지는 제거됨. 직접 접근 시 `/home`으로 302 redirect. 로그인은 공통 모달에서 `/api/auth/login` 호출. |
| `/register`, `/register.html` | `register` | 학생/강사 통합 회원가입. `?role=tutor`면 강사 탭 기본 선택 |
| `/signin/reset/password` | `auth/password_reset_request` | 계정 찾기/비밀번호 재설정 페이지. 이메일로 아이디 목록 조회, 아이디+이메일로 임시 비밀번호 재설정 |
| `/signin/reset/password/confirm` | `auth/password_reset_confirm` | reset token 기반 새 비밀번호 설정 페이지 |
| `/dashboard`, `/dashboard.html`, `/stdt_dashboard`, `/stdt_dashboard.html` | `stdt_dashboard` | 학생 대시보드 / Admin 임퍼소네이트 진입 |
| `/tutor/academy` | `tutor_academy` | ✅ 강사 Academy 관리. **수업 상품 섹션은 LMS로 이관됨** — 강좌 등록·수정·삭제 진입이 여기에 없다. 대신 ①`온라인 LMS` 카드의 `LMS로 이동`(→ `/lms/{handle}`, **handle 없으면 카드 자체가 렌더되지 않는다**) ②`수업 운영` 목록(수업명 링크 → `/tutor/lesson/{id}` 신청·수강생 관리)만 남는다. 기존 Academy의 `Academy 수정`은 `/builder`로 이동 |
| `/tutor/academy/lessons/editor` | `tutor/lesson_product_editor` | ✅ 수업 상품 생성·수정 전용 편집기. `?new=true`는 신규 draft를 추가하고 `?lessonId={id}`는 해당 수업을 활성화한다. `LessonProductEditorPageController`가 JWT principal과 `TUTOR` role을 확인하며, 비로그인·학생·ADMIN은 렌더하지 않고 랜딩(`/`)으로 리다이렉트한다(`/builder`와 동일 규칙) |
| `/tutor/lesson/{lessonId}` | `tutor/tutor_lesson_detail` | ✅ 강사용 수업 운영 상세. 수업 정보, 신청/수강생, 커뮤니티 진입을 유지하고 수정은 전용 편집기로 이동 |
| `/tutor/academy/setup` | `tutor_academy_setup` | ✅ Academy 생성/데이터 편집(임시). 공개주소(handle) 수정 가능. 저장 성공 후 `/tutor/academy`로 이동 |
| `/builder` | `tutor/academy_builder` | ✅ 학원빌더. 기존 Academy 초기값은 Thymeleaf SSR로 주입하며 canvas는 iframe 없이 공개 페이지 모양 DOM을 직접 표시. 기존 Academy는 학원명/소개/상세 소개/커리큘럼 통합 제목 inline 편집을 지원하고, 신규 발행 전 데이터·로고 data URL preview·섹션 표시 변경은 sessionStorage preview/pending payload 사용 |
| `/academies` | `academies` | ✅ 공개 Academy 목록/필터 페이지 |
| `/academies/{slug}` | `academy_public` | ✅ 공개 Academy 페이지 |
| `/academies/{slug}/preview` | `public/academy_layout_preview` | ✅ Academy Builder 레이아웃 기반 공개 렌더(SSR, Phase 3). `resolveLayoutSchema` block을 order대로 렌더 |
| `/community` | `public/community` | ✅ 전체 공개 커뮤니티 목록(읽기 anyopen, 글쓰기/추천 로그인). 목록은 `scope=community`(기본) — F2R 게시판(FREE/QNA/SUBJECT)만 |
| `/community/posts/{postId}` | `public/community_post` | ✅ 공개 커뮤니티 글 상세 + 댓글. wiki 게시판 글 상세도 이 화면 재사용 |
| `/community/posts/new` | `public/community_new` | ✅ F2R 커뮤니티(`/community`) 글 작성/수정. (구 `?board=`/`?return=` wiki 진입은 폐기 — Wiki는 전용 화면 사용) |
| `/wiki`, `/wiki/originals`, `/wiki/analysis` | `public/wiki`, `public/wiki_originals`, `public/wiki_analysis` | ✅ **`WikiPageController`**(`@Controller` — `Cache-Control: private, no-store` 목적만, role 로직 없음). 자료 업로드 진입점(`＋ 원본 업로드`/`＋ 분석 자료 업로드` 버튼 + `#wikiUploadModal` 모달 + `wiki_admin_upload.js`)은 **`fragments/_wiki_admin_upload :: modal` 로 principal 무관하게 항상 렌더** — "UI 표시 ≠ 권한 부여". 버튼 클릭 시 `wiki_admin_upload.js` 가 `GET /api/auth/me` 로 상태 확인(비로그인→로그인 안내, 비ADMIN→"관리자만" 안내, ADMIN→모달). 현재 학년 탭 기본값, 성공 시 새로고침 없이 해당 목록만 재조회(`window.wikiHub` / `window.wikiListReload`). **최종 권한은 `POST /api/admin/wiki/{originals,analysis-materials}` 의 `/api/admin/**` → ROLE_ADMIN** (frontend 판단 아님). 기존 wikioriginal/wikianalysis 도메인만 사용(analyzes 미사용, 새 저장소 없음). 상단 헤더 `BuildIt 홈`(→ `/`, Wiki 내부 `← BuildIt Wiki로 돌아가기`→`/wiki` 와 별개). 목록 데이터는 JS. 게시판 화면(/wiki/board/**, /wiki/posts/**)은 view-controller 유지 |
| `/wiki/board/{boardKey}` | `public/wiki_board` | ✅ Wiki 게시판별 목록. `boardKey` = `review`(시험 후기) / `variant`(변형문제) / `share`(자료 공유·요청). `/api/open-community/posts?scope=wiki` 재사용 |
| `/wiki/board/{boardKey}/new` | `public/wiki_post_new` | ✅ Wiki 전용 글 작성/수정(`?edit={id}`). 게시판은 URL로 고정, variant/share만 문서 첨부. 저장 후 `/wiki/posts/{id}`. `/home`·`/community`로 이동하지 않음 |
| `/wiki/posts/{postId}` | `public/wiki_post` | ✅ Wiki 전용 글 상세. 본문 + 첨부 다운로드/추가/삭제 + 답변. "← BuildIt Wiki로 돌아가기" → `/wiki`. 비-wiki 게시판 글이면 `/community/posts/{id}` 안내 |
| `/admin/wiki-originals` | `admin/wiki_originals` | ✅ (ADMIN) 모의고사 원본 PDF 등록/수정/공개전환/삭제. 셸은 permitAll, `/api/admin/wiki/originals*` 가 ADMIN 게이트 |
| `/admin/wiki-analysis` | `admin/wiki_analysis` | ✅ (ADMIN) 분석 자료 문서(PDF/HWP/HWPX/DOCX 등) 관리. `/api/admin/wiki/analysis-materials*` ADMIN 게이트 |
| `/lessons` | `lesson_search` | ✅ 학생용 수업 검색 페이지 |
| `/tutor/lessons/{lessonId}/students` | `tutor/lesson_students` | ✅ 강사용 수업별 신청자 목록 |
| `/tutor/conversation_dashboard` | `tutor/tutor_conversation_dashboard` | ✅ 강사 소유 대화 기록 목록. 학생 `/stdt_dashboard`처럼 카드 생성/열기/이름변경/삭제만 담당하며, 생성/열기는 상세 화면으로 이동 |
| `/tutor/conversation/dashboard?id={id}` | `tutor/tutor_conversation_detail` | ✅ 강사 소유 conversation 상세 대시보드. 학생 `/conversation/dashboard?id={id}`처럼 자료 카드/현황 카드 중심이며 AI Chat은 별도 화면으로 이동 |
| `/tutor/conversation/materials?id={id}` | `tutor/tutor_conversation_materials` | ✅ 강사 소유 자료 모음. 학생 materials 사이드바 슬롯 중 `종합 현황판/시험지 대시보드/학습자료 대시보드/학교 자료 모음/AI Chat`만 유지 |
| `/tutor/conversation/chat?id={id}` | `tutor/tutor_conversation_chat` | ✅ 강사 소유 AI Chat. 학생 `/conversation/chat?id={id}`의 파일 패널 + 채팅 패널 레이아웃을 tutor API context로 재사용 |
| `/tutor/conversations/{id}` | redirect | ✅ 강사 기록 legacy deep link. `/tutor/conversation/dashboard?id={id}`로 302 redirect |
| `/my/profile` | `my_profile` | ✅ 내 정보 수정/회원탈퇴. ADMIN 접근 시 `/home`으로 redirect |
| `/tutor_dashboard`, `/tutor_dashboard.html` | `tutor_dashboard` | ⚠️ legacy/잔존 페이지. 신규 강사 플로우는 `/tutor/academy` 사용 |
| `/conversation/chat`, `/conversation/chat.html` | `conversation_chat` | AI Chat. ADMIN/TUTOR 전용(사이드바도 role 노출), STUDENT 직접 접근 시 `/stdt_dashboard`로 302 redirect. `?id=`가 있으면 학생 소유 대화 guard를 먼저 통과해야 함 |
| `/conversation/dashboard`, `/conversation/dashboard.html` | `conversation_dashboard` | 종합 현황판(사이드바 1번). `ConversationPageController`가 mainTitle 주입. 제목 inline 편집(PATCH) 제공. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/exam-materials`, `/conversation/exam-materials.html` | `conversation_materials` | 시험지 대시보드(2번). `ConversationMaterialsPageController` — 학생 업로드 EXAM_* 필터. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/study-materials`, `/conversation/study-materials.html` | `conversation_materials` | 학습자료 대시보드(3번). 학생 업로드 GENERAL 필터. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/materials`, `/conversation/materials.html` | `conversation_materials` | 학교 자료 모음(4번). 학생 업로드 전체(union). `ConversationMaterialsPageController`. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/tutor-materials`, `/conversation/tutor-materials.html` | `conversation_tutor_materials` | 시험대비 자료(5번). 강사 업로드 참고자료(TUTOR_MATERIAL) 열람/업로드. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/homework`, `/conversation/homework.html` | `conversation_homework` | 숙제 및 피드백(6번). 강사 숙제 부여 + 학생 제출. 피드백은 placeholder. `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/review`, `/conversation/review.html` | `conversation_review` | 오답 노트(7번). EXAM_GRADED 기반 오답만(숙제 부여 섹션은 6번으로 이관). `?id=`는 학생 소유 대화 guard 적용 |
| `/conversation/report`, `/conversation/report.html` | `conversation_report` | 대화 리포트. 라우트·페이지 유지하되 신규 사이드바에는 미노출(성취도는 종합 현황판 카드에 포함). `?id=`는 학생 소유 대화 guard 적용 |
| `/exam_review`, `/exam_review.html` | `exam_review` | 채점된 시험지 검증 |
| `/admin/login`, `/admin/login.html` | `admin/login` | Admin 로그인 |
| `/admin/dashboard`, `/admin/dashboard.html` | `admin/dashboard` | ⚠️ 일부 legacy 호출 포함 |
| `/admin/students`, `/admin/students.html` | `admin/students` | Admin 학생 목록 |
| `/admin/landing` | `admin/landing` | ✅ 랜딩 관리. 고정 영상 3슬롯 미리보기·교체·공개/숨김 + 베타 의견 모더레이션(승인/숨김/뱃지)을 한 화면에서 처리한다. 화면 route부터 `ROLE_ADMIN` 필요(비인증 401, STUDENT/TUTOR 403). |

### 2.1 학원 온라인 LMS (`/lms/{handle}/**`)

> 설계·결정 배경은 [academy-lms-design.md](academy-lms-design.md). 1단계는 **데이터 MOCK, 인증·권한 진짜**인
> clickable prototype이라 신규 REST API·Flyway 변경이 0이다.

**URL 두 형태 — 정본은 로컬 형태 하나다.**

| 환경 | 사용자에게 보이는 URL | 학원 식별 |
|---|---|---|
| prd | `{handle}.feel2real.co.kr/lms/{feature}` | Host 서브도메인 |
| local/apex | `/lms/{handle}/{feature}` | 경로 1번째 세그먼트 |

`SubdomainAcademyFilter`가 서브도메인 요청을 **인가 이전에** `HttpServletRequestWrapper`로 로컬 형태로
정규화한다(301도 forward도 아니다 — forward를 쓰면 시큐리티 체인 나머지가 실행되지 않아 인증·인가가
통째로 우회된다). 따라서 `SecurityConfig` 매처와 컨트롤러 매핑은 아래 로컬 형태 **한 벌만** 존재한다.
식별자는 `slug`가 아니라 `handle`이며(slug는 한글 허용이라 서브도메인 불가), handle이 없는 Academy는
LMS 주소가 성립하지 않는다(404).

| Route (정규화 형태) | View template | 인가 | 컨트롤러 · 가드 |
|---|---|---|---|
| `GET /lms/{handle}` | `lms/lms_courses` | permitAll | `AcademyLmsPageController` · `getPublishedEntityByHandle` → 미발행/미존재 404 |
| `GET /lms/{handle}/courses/{courseId}` | `lms/lms_course_detail` | permitAll | 〃 (`courseId`는 1단계에서 검증 없이 모델 통과 — 데이터가 MOCK) |
| `GET /lms/{handle}/cart` | `lms/lms_cart` | authenticated | 〃 (학생 포함) |
| `GET /lms/{handle}/checkout` | `lms/lms_checkout` | authenticated | 〃 |
| `GET /lms/{handle}/my` | `lms/lms_my` | authenticated | 〃 |
| `GET /lms/{handle}/lecture/{courseId}` | `lms/lms_lecture` | authenticated | 〃 |
| `GET /lms/{handle}/manage` | `lms/lms_manage` | `hasRole("TUTOR")` | `AcademyLmsManagePageController` · **slug owner 전용** — `requireOwnedAcademyByHandle`, 타 학원 TUTOR도 403, ADMIN 우회 없음 |
| `GET /lms/{handle}/contents` | `lms/lms_contents` | `hasRole("TUTOR")` | 〃 |
| `GET /lms/{handle}/review/{lessonId}` | `lms/lms_review` | `authenticated`(owner 또는 ACTIVE 수강 학생, `LmsLearnerAccessService`) | `LmsReviewPageController`(신규, `domain.lmscontent`). §2.1이 열거하는 8개 MOCK 화면과 별개다 — 재원생 복습영상 실 API 전용 화면이라 URL·id 공간을 분리했다(MOCK `courseId`와 실제 `LessonProduct.lessonId`가 섞이지 않게). `SecurityConfig`에 이 경로 전용 매처를 추가하지 않았다 — `/lms/**` fail-closed catch-all(아래 줄)이 이미 인증을 강제하고, owner/수강 판정은 컨트롤러가 한다. |

- 매처에 열거되지 않은 `/lms/**` 하위 경로는 **fail-closed**(최소 인증). catch-all이 `permitAll`이라 이 줄이 없으면 새어 나간다.
- 비로그인 + `Accept: text/html`로 인증 필요 화면에 접근하면 **302 `/`**(빈 401 대신). LMS 보호 화면은 로그인 상태 전용이고 로그아웃도 `/`로 가므로(`static/js/account_menu.js`) 비로그인 목적지를 `/`로 맞춘다. 그 외(예: `Accept: application/json`)와 LMS 밖 경로는 기존 **401** 계약 그대로다 — `SecurityConfig`의 `defaultAuthenticationEntryPointFor` 2분기.
- ⚠ 이건 페이지 404 정책(`PageNotFoundRedirectController` → `/home`)과 **다른 흐름**이다. 존재하지 않는 handle처럼 컨트롤러가 404를 던진 경우는 그쪽 정책을 타 `/home`으로 간다. 두 목적지 통일은 별도 이슈(#283)로 분리했다.
- 운영자 화면의 403 응답은 HTML 에러 페이지가 아니라 `GlobalExceptionHandler`(`@RestControllerAdvice`)의 평문 본문이다.
- 사이드바의 강좌관리·콘텐츠 관리 링크는 `lmsIsOwner`일 때만 렌더된다. **표시일 뿐 통제가 아니다.**
- `HandlePolicy.RESERVED`에 `lms`를 추가했다 — 플랫폼용 `lms.{base-domain}`을 학원이 선점하지 못하게 한다.

> `/academy` 행(위 표)은 이 LMS 경로와 무관하다. LMS는 `/academy`·`/academies` 어느 계열도 쓰지 않는다.

### 2.2 LMS 콘텐츠/수강권 API (Phase 1, 신규)

> 도메인 배경은 [DOMAIN.md § 학원 LMS 콘텐츠/수강권](DOMAIN.md). §2.1의 8개 화면(courses~contents)은
> 계속 MOCK이고, 이 절의 API만 `lms_contents`/`lms_enrollments` 실 DB를 쓴다.

**강사(운영자) — `/api/tutor/**`, hasRole("TUTOR"), handle owner는 서비스가 재검증**

| Route | 설명 |
|---|---|
| `GET /api/tutor/lms/{handle}/contents` | 강좌 선택 목록 + 등록된 콘텐츠 전체 |
| `POST /api/tutor/lms/{handle}/contents` (multipart) | 강좌 선택+메타데이터+영상 업로드를 한 번에 저장. 길이 상한은 `app.lms.content.max-duration-sec`(기본 30분) — 홍보영상 120초 제한과 무관 |
| `PUT /api/tutor/lms/{handle}/contents/{id}` | 메타데이터만 수정(영상 교체는 Phase 1 미지원) |
| `PATCH /api/tutor/lms/{handle}/contents/{id}/publish` | 공개/비공개 토글 |
| `DELETE /api/tutor/lms/{handle}/contents/{id}` | 삭제(행 삭제 + storage best-effort 삭제) |
| `GET /api/tutor/lms/{handle}/enrollments/lessons/{lessonId}` | 배정 후보(`lesson_applications` APPROVED) + 현재 수강권 목록 |
| `POST /api/tutor/lms/{handle}/enrollments/lessons/{lessonId}` | 수강권 부여(grantSource=ACADEMY 고정). 후보가 아닌 studentId는 400 |
| `DELETE /api/tutor/lms/{handle}/enrollments/lessons/{lessonId}/{enrollmentId}` | 수강권 회수(REVOKED) |
| `GET`/`PUT /api/tutor/academy/lms-settings` | Builder "LMS 설정"(표시명/메인제목/소개/섹션명/사용여부). `page_settings.builderDraft`에 저장 — 기존 `/api/tutor/academy/page-settings`는 builderDraft를 받지 않으므로 별도 엔드포인트 |

**학생(재원생) — `/api/lms/{handle}/**`, 일반 `authenticated()`(catch-all), owner/수강 판정은 서비스가 함**

| Route | 설명 |
|---|---|
| `GET /api/lms/{handle}/my/lessons` | 내가 ACTIVE 수강권을 가진 강좌 목록(+공개 콘텐츠 수) |
| `GET /api/lms/{handle}/my/lessons/{lessonId}` | 강좌의 공개(PUBLISHED) 콘텐츠 목록. owner 또는 수강 학생만(`LmsLearnerAccessService`, 아니면 403) |
| `GET`/`HEAD /api/lms/{handle}/contents/{contentId}/stream` | Range 지원 영상 스트리밍. 학생은 PUBLISHED만, owner는 상태 무관 미리보기 허용 |

## 3. Auth API

### ✅ POST /api/auth/login

- 목적: loginId/password 검증 후 JWT cookie 발급.
- 현재 사용처:
  - `templates/home.html`
  - `templates/admin/login.html`
  - `templates/auth/register.html`의 강사 가입 후 자동 로그인
- 향후 사용 가능성:
  - React client
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    {
      "loginId": "student01",
      "password": "plain-password"
    }
    ```
- Response:
  - 성공: `200 OK`, `Set-Cookie`로 access token 저장, body:
    ```json
    {
      "userId": 1,
      "loginId": "student01",
      "name": "홍길동",
      "nickname": "길동",
      "displayName": "길동",
      "role": "STUDENT"
    }
    ```
  - 실패: 로그인 검증 실패 시 service 예외에 따라 400 text 가능.
- 관련 코드:
  - `AuthController.login`
  - `LoginRequest`
  - `LoginResponse`

### ✅ POST /api/auth/login-id/find

- 목적: 가입 이메일로 연결된 아이디 목록을 조회한다. 같은 이메일로 학생/강사 등 여러 계정이 있을 수 있으므로 배열을 반환한다.
- 현재 사용처:
  - `templates/auth/password_reset_request.html`
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    { "email": "student@example.com" }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "email": "student@example.com",
      "accounts": [
        { "loginId": "student01", "role": "STUDENT", "roleLabel": "학생" },
        { "loginId": "tutor01", "role": "TUTOR", "roleLabel": "강사" }
      ]
    }
    ```
  - 매칭 계정 없음: `200 OK` + `accounts: []`
  - 실패: `400 text/plain` — 이메일 누락/형식 오류.
- 관련 코드: `AuthController.findLoginIds`, `PasswordResetService.findLoginIds`

### ✅ POST /api/auth/password-reset/request

- 목적: 아이디와 가입 이메일을 함께 확인한 뒤 해당 계정 비밀번호를 임시 비밀번호로 재설정한다. SMTP 발송은 현재 우선순위에서 제외되어 있으며, 운영 포함 모든 환경 기본 임시 모드에서는 비밀번호를 `reset1234`로 직접 변경한다.
- 현재 사용처:
  - `templates/auth/password_reset_request.html`
- 향후 사용 가능성:
  - React client
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    {
      "loginId": "student01",
      "email": "student@example.com"
    }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "email": "student@example.com",
      "temporaryPassword": "reset1234"
    }
    ```
  - 실패: `400 text/plain`
    - `아이디를 입력해 주세요.`
    - `이메일을 입력해 주세요.`
    - `이메일 형식이 올바르지 않습니다.`
    - `아이디와 이메일이 일치하는 계정이 없습니다.`
- 관련 코드:
  - `AuthController.requestPasswordReset`
  - `PasswordResetService.requestReset`
  - 운영 기본값도 `temporary-direct-reset=true`로 두어 SMTP 연동 전까지 `reset1234` 직접 리셋을 사용한다.

### ✅ POST /api/auth/password-reset/confirm

- 목적: 재설정 링크의 token을 검증하고 새 비밀번호를 BCrypt hash로 저장한다. token은 1회 사용 처리된다.
- 현재 사용처:
  - `templates/auth/password_reset_confirm.html`
- 향후 사용 가능성:
  - React client
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    {
      "token": "reset-token-from-link",
      "newPassword": "new-password"
    }
    ```
- Response:
  - 성공: `204 No Content`
  - 실패: `400 text/plain`
    - `비밀번호 재설정 링크가 올바르지 않습니다.`
    - `비밀번호 재설정 링크가 만료되었습니다.`
    - `이미 사용된 비밀번호 재설정 링크입니다.`
    - `비밀번호는 8자 이상이어야 합니다.`
- 관련 코드:
  - `AuthController.confirmPasswordReset`
  - `PasswordResetService.confirmReset`

### ✅ POST /api/auth/admin/password-change

- 목적: 운영 ADMIN(admin-f2r) 비밀번호를 `/admin/login` 화면에서 변경한다. 대상은 서버가 고정하므로 요청에 userId/role이 없고, 기존 비밀번호를 모르면 변경할 수 없다.
- 현재 사용처:
  - `templates/admin/login.html`의 비밀번호 변경 폼
- 향후 사용 가능성:
  - 없음 (운영 ADMIN 전용)
- 인증/권한: public (로그인 화면에서 호출). 권한 판정은 서비스가 수행 — 대상이 admin-f2r ADMIN이 아니면 403, 현재 비번 불일치면 401.
- Request:
  - JSON body:
    ```json
    {
      "loginId": "admin-f2r",
      "currentPassword": "current-plain-password",
      "newPassword": "next-plain-password-16-or-more",
      "confirmPassword": "next-plain-password-16-or-more"
    }
    ```
- Response:
  - 성공: `204 No Content`
  - 실패:
    - `403` — 대상이 admin-f2r ADMIN이 아님 (STUDENT/TUTOR 포함).
    - `401` — 현재 비밀번호 불일치.
    - `400 text/plain` — 새 비밀번호/확인값 불일치, 16자 미만, 72바이트 초과.
    - `429` — 인증 실패 반복 시 loginId별 sliding window 차단 (기본 10분 내 5회).
- 관련 코드:
  - `AuthController.changeAdminPassword`
  - `AdminPasswordChangeService.changePassword`
  - `AdminPasswordChangeRequest`
- 주의: 평문·해시를 로그·응답에 포함하지 않는다. 기존 학생/강사 변경(`PATCH /api/me`)과 무관하며 ADMIN은 해당 경로에서 거부되므로 이 endpoint가 유일한 운영자 변경 경로다.

### ✅ POST /api/auth/logout

- 목적: access token cookie 삭제.
- 현재 사용처:
  - `templates/admin/students.html`
  - `templates/admin/dashboard.html`
  - `templates/tutor_dashboard.html`
  - `templates/tutor_academy_setup.html`
  - `static/js/stdt_dashboard.js`
- 향후 사용 가능성:
  - React client
- 인증/권한: public. 인증 상태와 무관하게 빈 cookie + `Max-Age=0`으로 삭제.
- Request: body 없음.
- Response:
  - 성공: `204 No Content`, body 없음.
- 관련 코드:
  - `AuthController.logout`

### ✅ GET /api/auth/me

- 목적: cookie 기반 현재 로그인 사용자 정보 조회.
- 현재 사용처:
  - ✅ `/login`, `/login.html`은 `/home`으로 redirect한다. `/home` 로드 시 role dashboard auto-bounce는 하지 않는다.
  - ✅ 공통 account menu는 현재 사용자를 조회해 role별 홈(`/stdt_dashboard`, `/admin/students`, `/tutor/academy`) 링크를 갱신한다.
  - ✅ public 작성/업로드 액션은 `fetchAuth(..., { redirectOnAuthError: false })`로 401/403 응답을 직접 받아 alert + 로그인 모달만 띄우고 현재 페이지 입력을 보존한다.
- 향후 사용 가능성:
  - React client session bootstrap
- 인증/권한: 인증 필요.
- Request: 없음.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "userId": 1,
      "loginId": "student01",
      "name": "홍길동",
      "nickname": "길동",
      "displayName": "길동",
      "role": "STUDENT"
    }
    ```
  - 실패: 401.
- 관련 코드:
  - `AuthController.me`
  - `LoginResponse`

## 3-2. Easy 회계 API (ADMIN 전용)

- 목적: 에듀홀릭 회계·학생원장 v1. `/easy` 화면 4종이 사용한다.
- 인증/권한: 전부 `/api/admin/**` → ROLE_ADMIN. SSR `/easy/**`도 `hasRole("ADMIN")`. Wiki `users`와 연결하지 않는다.
- 금액: 전부 long(원). 상태(미납/일부납/완납)는 저장 없이 청구/납부 기준 자동 계산.

### ✅ GET /api/admin/easy/businesses

- 목적: 활성 사업체 목록 (이름 하드코딩 없이 code/name으로 참조).
- Response: `200 OK` — `[{ businessId, code, name }]`.
- 관련 코드: `EasyBusinessController`, `EasyBusinessService`.

### ✅ GET /api/admin/easy/summary?businessId&month=YYYY-MM

- 목적: 월 손익 5카드 + 최근 거래 10 + 미수 학생 10. `businessId` 생략 시 전체 합산.
- 모든 숫자는 같은 scope다. `studentCount`는 전체 조회 시 재원 수, 사업체 선택 시 해당 사업체 수납 이력 학생 수다.
- Response: `200 OK`
  ```json
  {
    "month": "2026-09", "businessId": null, "businessName": "전체",
    "income": 800000, "expense": 100000, "profit": 700000,
    "receivable": 200000, "studentCount": 12,
    "recentTxns": [], "overdueStudents": []
  }
  ```
- 관련 코드: `EasyLedgerController.summary`, `EasyLedgerService.summary`.

### ✅ GET/POST /api/admin/easy/transactions, PUT/DELETE /api/admin/easy/transactions/{txnId}

- 목적: 수입·지출 등록/수정/삭제. 삭제는 화면 confirm 후 `204`.
- 목록 필터(Query, 모두 선택): `businessId`, `from`/`to`(YYYY-MM-DD), `direction`(INCOME/EXPENSE), `category`.
  `businessId` 지정 시 해당 사업체 거래만 반환한다 (학원/출판사 혼합 없음).
- 목록 Response 항목에 분류상태가 포함된다: `accountId`/`accountName`(NULL 가능),
  `evidenceReview`(NULL/NEEDS_REVIEW 등), `taxClassification`(NULL=REVIEW_REQUIRED 취급).
- 등록 body: `{ businessId, occurredOn, direction, amount(0 이상), category, counterparty, description, payMethod, memo, receiptRef }`.
- Response: 목록 `200 OK` 배열, 등록/수정 `200 OK` 객체, 삭제 `204 No Content`. 실패 `400 text/plain`.
- 관련 코드: `EasyLedgerController`, `EasyLedgerService`.

### ✅ GET/POST /api/admin/easy/students, GET /api/admin/easy/students/{studentId}/ledger, PUT /api/admin/easy/students/{studentId}

- 목적: 학생 원장 목록/등록/수정 + 상세(수납 합계 청구액/납부액/미수금 + 월별 내역 + 수납 기준 소속 사업체).
- 등록 body: `{ name(필수), school, grade, phone?, parentPhone?, status(ENROLLED/WITHDRAWN/GRADUATED), enrolledOn?, memo? }`.
- 관련 코드: `EasyStudentController`, `EasyStudentService`.

### ✅ GET/POST /api/admin/easy/payments, PUT/DELETE /api/admin/easy/payments/{paymentId}

- 목적: 월 수납 등록/수정/삭제. 상태 자동 계산.
- 목록 필터(Query, 모두 선택): `studentId`, `month`(YYYY-MM), `status`(UNPAID/PARTIAL/PAID).
- 등록 body: `{ studentId, businessId, targetMonth(YYYY-MM), billedAmount, paidAmount, paidOn?, payMethod, memo? }`. 동일 (학생, 사업체, 월) 중복 시 `400`.
- 관련 코드: `EasyTuitionController`, `EasyTuitionService`.

### ✅ GET/POST /api/admin/easy/accounts, PUT /api/admin/easy/accounts/{accountId}

- 목적: 계정과목 master 조회/등록/수정. Work 실데이터 도착 전 잠정치.
- 목록 필터: `direction`(INCOME/EXPENSE/ASSET, 생략 시 전체).
- 관련 코드: `EasyAccountController`, `EasyAccountService`.

### ✅ GET/POST /api/admin/easy/accounts/mappings, DELETE /api/admin/easy/accounts/mappings/{mappingId}

- 목적: 내부 계정 ↔ 외부(이지샵) 코드 ↔ 국세청 표준계정 매핑. 표준 코드/명은 확정 전까지 비워 둘 수 있다.
- 관련 코드: `EasyAccountController`, `EasyAccountService`.

### ✅ GET/POST /api/admin/easy/accounts/rules, PUT/DELETE /api/admin/easy/accounts/rules/{ruleId}

- 목적: 거래처/적요 패턴 → 내부 계정 자동분류 규칙 (우선순위·금액구간·방향 조건, 잘못된 정규식 스킵).
- 관련 코드: `EasyAccountController`, `EasyAccountService`.

### ✅ GET/POST /api/admin/easy/imports, GET /api/admin/easy/imports/{batchId}, POST /api/admin/easy/imports/{batchId}/confirm, POST /api/admin/easy/imports/{batchId}/prepare, DELETE /api/admin/easy/imports/{batchId}

- 목적: import staging. EASY 운영 원칙 — 실제 거래를 HOLD하지 않는다.
  확정 시 PENDING 행은 같은 매핑→규칙 경로로 자동 준비되고, 계정 미분류 행도
  장부에 적재된다 (account NULL·category `미분류`·`evidenceReview=NEEDS_REVIEW`·
  `taxClassification=REVIEW_REQUIRED`, 분개 생략). 분류 확정이 적재 조건이 아니다.
- 확정 가드: 장부 fingerprint 중복은 `DUPLICATE` 확정 제외 (자동합치기 없음).
  원천 판정(`raw_data.primary_disposition`)이 대출·계좌이체면 세무구분을
  `NOT_INCOME`/`NOT_EXPENSE`로 두어 손익 왜곡을 막는다 (목록에는 보인다).
  원천 출판사 확정분(`business=PUBLISHING_CONFIRMED`)은 출판사 장부에 귀속된다.
- 손익 합계(대시보드·연 P&L·경영요약)는 `NOT_INCOME`/`NOT_EXPENSE`를 제외한다.
  `REVIEW_REQUIRED`(NULL 포함)는 후보 손익으로 포함된다.
- `prepare`: 장부 적재 없이 PENDING→READY 분류 상태만 미리 본다.
- 등록 body: `{ businessId, sourceName, sourceKind, rows: [{ occurredOn, direction, amount, counterparty, description, payMethod, supplyAmount?, vatAmount?, evidenceType?, rawData? }] }` (최대 2000행).
- 관련 코드: `EasyImportController`, `EasyImportService`.

### ✅ GET /api/admin/easy/transactions/export?businessId&from&to

- 목적: 거래 CSV 내보내기 (Excel 한글 호환 BOM, 세무사 전달 기초 형식).
- Response: `200 OK` + `text/csv` attachment.
- 관련 코드: `EasyLedgerController`, `EasyLedgerService.exportCsv`.

### Easy 거래 확장 필드 (V974, backward compatible)

- `POST/PUT /api/admin/easy/transactions` body 추가 (전부 선택): `accountId`, `supplyAmount`, `vatAmount`, `evidenceType`(TAX_INVOICE/CREDIT_CARD/CASH_RECEIPT/INVOICE/NONE/OTHER).
- 합계(`amount`) 집계는 기존과 동일. 세율 자동계산은 하지 않는다 (세법 확정 전).

### ✅ GET /api/admin/easy/executive-summary?businessId

- 목적: 경영 대시보드. 올해 1/1~오늘 vs 작년 동기 비교. `businessId` 생략 시 전체 합산.
- Response: `200 OK` — 올해/작년 매출·비용·손익 + 증감액/증감률(비교 기준 0원이면 null) + 미수금 + 학생 수 + 사업장별 slice + `briefing`(DB 숫자 문장) + `incomeTaxPreview`(현재 `PENDING_REAL_DATA`, 세액 계산 없음).
- 관련 코드: `EasyInsightController`, `EasyExecutiveService`.

### ✅ GET /api/admin/easy/reviews/summary?businessId, GET /api/admin/easy/reviews?businessId&kind&status

- 목적: "AI가 확인할 장부" 개수·목록. kind(UNCLASSIFIED/EVIDENCE/BUSINESS/WITHHOLDING/DUPLICATE_SUSPECT/IMPORT_UNCONFIRMED), status(OPEN, 생략 시 OPEN).
- 관련 코드: `EasyAuditController`, `EasyAuditService`.

### ✅ POST /api/admin/easy/reviews/rebuild?businessId

- 목적: 장부 스캔 후 확인 필요 항목 재생성. 이미 OPEN인 항목은 중복 생성하지 않고 RESOLVED/IGNORED는 보존한다. BUSINESS 종류는 자동 생성하지 않는다 (사업장 추측 금지).
- 관련 코드: `EasyAuditController`, `EasyAuditService.rebuild`.

### ✅ POST /api/admin/easy/reviews/{reviewId}/resolve, POST /api/admin/easy/reviews/flag-business

- 목적: 검토 확인. body `{ resolution, businessId?, accountId?, evidenceReview?, withholdingReview?, ignore? }` — 연결 거래의 사업장·계정·검토 상태를 장부에 반영하고 항목을 닫는다. import 확정은 기존 staging API를 재사용한다. `flag-business` body `{ txnId, note? }`는 "어느 사업장 거래입니까?" 저장용이다.
- 관련 코드: `EasyAuditController`, `EasyAuditService`.

### ✅ GET/POST /api/admin/easy/withholdings?businessId, PUT/DELETE /api/admin/easy/withholdings/{recordId}

- 목적: 원천징수 검토 기록 최소 기반 (신고 기능 아님). body `{ businessId, payee, grossAmount, netAmount?, withholdingAmount?, paidOn?, txnId?(같은 사업체만), filingChecked?, statementChecked?, memo? }`. 타 사업체 거래 연결 거부.
- 관련 코드: `EasyAuditController`, `EasyAuditService`.

### ✅ GET/POST /api/admin/easy/closings, POST /api/admin/easy/closings/{closingId}/review|close|reopen

- 목적: 연도 마감. 생성 body `{ businessId, fiscalYear }` (사업체+연도 UNIQUE). 마감은 해당 귀속연도의 OPEN 검토 항목이 있으면 차단되며 (다른 연도 항목은 차단 안 함) `force:true` + 사유로만 강제 마감한다 (스냅샷에 기록). 귀속연도 미확정 staging은 임의 배정 없이 모든 연도를 차단한다. 마감 해제는 항상 가능하다.
- 관련 코드: `EasyAuditController`, `EasyAuditService`.

### Easy 거래 확장 필드 (V975, backward compatible)

- `POST/PUT /api/admin/easy/transactions` body 추가 (전부 선택): `sourceKind`, `sourceRef`, `evidenceReview`(UNKNOWN/NEEDS_REVIEW/VERIFIED/MISSING/NOT_REQUIRED), `withholdingReview`(NONE/NEEDS_REVIEW/CHECKED). `fingerprint`는 서버가 계산한다.
- 증빙·원천징수 검토는 확인 표시이며 세법 판정이 아니다.
- `GET /api/admin/easy/transactions`에 `accountId` 필터 추가 (P&L drill-down용, 생략 시 전체).

### ✅ POST /api/admin/easy/imports/easyshop

- 목적: EasyShop 실데이터 staging (source_kind=EASYSHOP). 컬럼 계약은 `docs/easy/EASYSHOP_IMPORT_PLAN.md`.
- 등록 body: `{ businessId?(기본 사업체), sourceName, rows: [{ date, direction, amount, counterparty?, description?, payMethod?, accountName?, businessCode?, refNo?, raw? }] }` (최대 2000행).
- sourceRowNo?: CSV provenance row number, 1-based excluding header (e.g. 2nd data row only -> 2). Present -> sourceRef #orig-row-N, absent -> legacy #row-N. No DB change.
- 사업체 코드 혼합 시 사업장별 배치로 분리. 미확정 행이 있으면 staging 전 전체 거부. 장부 중복은 `DUPLICATE`로 확정 제외. 외부 계정명은 확정 매핑만 자동 적용한다.
- 관련 코드: `EasyImportController`, `EasyShopImportService`.

### ✅ 자동수집 foundation (V983) — POST /api/admin/easy/collections/ingest/csv, GET /api/admin/easy/collections/runs?businessId, GET /api/admin/easy/collections/runs/{runId}, GET /api/admin/easy/collections/sources

- 목적: CSV 파일(UTF-8/CP949) 수집 → run → 3단계 dedupe → 기존 import staging 인계(sourceKind=FILE_CSV, sourceName=`파일명#run:{runId}`). **인계만 한다 — 확정은 기존 `/api/admin/easy/imports/{batchId}/confirm`만 유효** (자동 collect ≠ 자동 confirm).
- ingest: multipart `businessId`, `file`, 선택 `label`. Response `{ run: { status, fetchedCount, acceptedCount, duplicateCount, errorCount, importBatchId, ... }, rowErrors: [] }`.
- dedupe 3단계: ①외부 ID(승인번호 등) → `EXT` 키(내용이 달라도 동일 ID면 중복) ②정규화 튜플 해시+파일 내 등장 순번 → `T` 키(파일명·행 순서 무관 내용 기반) ③기존 장부 `fingerprint` 충돌 → raw는 `LEDGER_DUPLICATE_EXCLUDED`로 보존하고 import에 넘기지 않는다.
- 파싱 실패 행은 원문을 raw `ERROR`로 보존하고 run은 `PARTIAL`. XLSX는 미지원(명시 거부). 인계는 수집 1회당 최대 2000행.
- 관련 코드: `EasyCollectionController`, `EasyCollectionIngestService`, `EasyCsvFileAdapter`, `EasyCollectionDedupeKeys`. 상세: `docs/easy/auto-collection/PHASE1_COLLECTION_FOUNDATION_IMPLEMENTATION.md`.

### ✅ GET /api/admin/easy/pl?businessId&year

- 목적: 연도 손익. 분개 기준 집계 + 장부 교차검증(`matchesLedger`, `journalBalanced`, `unpostedTxnCount`) + 월별 + 계정별 + 전년 비교. `businessId` 생략 시 전체 합산.
- 관련 코드: `EasyReportController`, `EasyReportService`.

### ✅ GET/POST /api/admin/easy/filing-references, DELETE /api/admin/easy/filing-references/{refId}

- 목적: 과거 신고 기준자료 수동 등록 (Library 자료, seed 없음). upsert body `{ businessId, fiscalYear, revenue?, expense?, incomeAmount?, source?, note? }` (사업체+연도 UNIQUE).
- 관련 코드: `EasyReportController`, `EasyReportService`.

### ✅ GET /api/admin/easy/filing-check?businessId&fiscalYear

- 목적: 장부 vs 신고자료 독립 비교. 라인별 `cause`(MATCHED/NEEDS_REVIEW/UNKNOWN). 역산하지 않는다.
- 관련 코드: `EasyReportController`, `EasyReportService`.

### ✅ GET /api/admin/easy/income-tax?year

- 목적: 종소세 준비 — 사업장별 분리 합산 + 주사업장(학원) 표시. `PREPARING`, 결정세액 미표시.
- 관련 코드: `EasyReportController`, `EasyReportService`.

### ✅ POST /api/admin/easy/corrections/preview, GET /api/admin/easy/corrections/logs?businessId&txnId

- 목적: AI 수정 계약 — preview는 미적용 before/after + 변경점/경고, logs는 append-only 감사 로그. 수정·resolve·삭제 시 자동 기록된다.
- 관련 코드: `EasyAuditController`, `EasyAuditService`.

## 4. User API

### ✅ GET /api/me

- 목적: 현재 로그인 사용자의 개인정보 수정 화면 pre-fill 데이터 조회.
- 현재 사용처:
  - `templates/my_profile.html`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. `STUDENT`, `TUTOR` 허용. `ADMIN`은 403.
- Request: 없음.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "userId": 1,
      "loginId": "student01",
      "role": "STUDENT",
      "name": "홍길동",
      "nickname": "길동",
      "email": "student@example.com",
      "phone": "010-0000-0000",
      "studentId": 10,
      "schoolName": "OO중학교",
      "grade": 2,
      "parentName": "학부모",
      "parentPhone": "010-1111-2222"
    }
    ```
  - `TUTOR`는 `studentId`, `schoolName`, `grade`, `parentName`, `parentPhone`이 `null`.
  - 실패: 401, 403, 400.
- 관련 코드:
  - `MeController.getMyProfile`
  - `MyProfileResponse`

### ✅ PATCH /api/me

- 목적: 현재 로그인 사용자의 개인정보 수정. `STUDENT`는 학생 상세 정보도 함께 수정 가능.
- 현재 사용처:
  - `templates/my_profile.html`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. `STUDENT`, `TUTOR` 허용. `ADMIN`은 403.
- Request:
  - JSON body. `loginId`, `role`은 받지 않는다. 모든 필드는 nullable이며 null이면 기존 값을 유지한다.
    ```json
    {
      "name": "홍길동",
      "nickname": "길동",
      "email": "student@example.com",
      "phone": "010-0000-0000",
      "currentPassword": "old-password",
      "newPassword": "new-password",
      "schoolName": "OO중학교",
      "grade": 2,
      "parentName": "학부모",
      "parentPhone": "010-1111-2222"
    }
    ```
- Response:
  - 성공: `200 OK`, body는 `GET /api/me`와 동일.
  - 실패: 401, 403, 400. `newPassword`가 있으면 `currentPassword` 검증 실패 시 400.
- 관련 코드:
  - `MeController.updateMyProfile`
  - `UpdateMyProfileRequest`

### ✅ DELETE /api/me

- 목적: 현재 로그인 사용자의 회원탈퇴.
- 현재 사용처:
  - `templates/my_profile.html`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. `STUDENT`, `TUTOR` 허용. `ADMIN`은 403.
- Request:
  - JSON body:
    ```json
    {
      "currentPassword": "plain-password"
    }
    ```
- Response:
  - 성공: `204 No Content`.
  - 실패: 401, 403, 400. 현재 비밀번호 불일치 시 400.
- 삭제 정책:
  - `users` 삭제를 루트로 수행하고 같은 트랜잭션에서 flush한다.
  - `STUDENT` 소유 데이터는 FK cascade로 삭제된다.
  - `TUTOR`가 배포자로만 연결된 `learning_materials.assigned_by`는 `NULL`로 남는다.
- 관련 코드:
  - `MeController.withdraw`
  - `WithdrawRequest`

### ✅ POST /api/users/students

- 목적: 학생 회원가입.
- 현재 사용처:
  - `templates/register.html`
  - `templates/tutor_dashboard.html`의 잔존 학생 생성 UI
- 향후 사용 가능성:
  - React client
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    {
      "loginId": "student01",
      "password": "plain-password",
      "name": "홍길동",
      "nickname": "길동",
      "email": "student@example.com",
      "phone": "010-0000-0000",
      "schoolName": "OO중학교",
      "grade": 2,
      "parentName": "학부모",
      "parentPhone": "010-1111-2222"
    }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "userId": 1,
      "loginId": "student01",
      "name": "홍길동",
      "nickname": "길동",
      "displayName": "길동",
      "phone": "010-0000-0000",
      "role": "STUDENT"
    }
    ```
  - 실패: 400 text 가능.
- 관련 코드:
  - `UserController.createStudent`
  - `CreateStudentRequest`
  - `UserResponse`

### ✅ GET /api/users/check-login-id

- 목적: 회원가입 화면에서 loginId 중복 여부를 사전 확인한다. 학생/강사 통합 회원가입에서 공통 사용한다. 닉네임은 중복 허용 정책이므로 중복 확인 API를 제공하지 않는다.
- 현재 사용처:
  - `templates/auth/register.html`
- 인증/권한: public.
- Request:
  - Query parameters:
    - `loginId` required string — 확인할 아이디.
- Response:
  - 성공: `200 OK`
    ```json
    { "available": true }
    ```
- 관련 코드:
  - `UserController.checkLoginId`
  - `UserService.isLoginIdAvailable`

### ✅ GET /api/users/students

- 목적: 전체 학생 목록 조회.
- 현재 사용처:
  - ⚠️ 현재 주요 Admin 화면은 `/api/admin/students`를 사용. 이 endpoint는 Controller에는 존재하나 프론트 직접 사용은 확인되지 않음.
- 향후 사용 가능성:
  - React Admin client
- 인증/권한: 인증 필요. 주석상 ADMIN 전용이나 SecurityConfig path 기준으로는 `/api/users/students`는 인증만 필요하다. ⚠️ service/controller 가드 확인 필요.
- Request: 없음.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "studentId": 1,
        "userId": 1,
        "name": "홍길동",
        "loginId": "student01",
        "phone": "010-0000-0000",
        "schoolName": "OO중학교",
        "grade": 2,
        "parentName": "학부모",
        "parentPhone": "010-1111-2222"
      }
    ]
    ```
- 관련 코드:
  - `UserController.getAllStudents`
  - `StudentSummaryResponse`

### ✅ GET /api/users/students/me

- 목적: 현재 로그인한 학생 프로필 조회. Admin은 target user 임퍼소네이트 가능.
- 현재 사용처:
  - `templates/home.html`
  - `static/js/stdt_dashboard.js`
- 향후 사용 가능성:
  - React client session bootstrap
- 인증/권한: 인증 필요.
- Request:
  - Query parameters:
    - `targetUserId` optional number — ADMIN 임퍼소네이트용.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "studentId": 1,
      "userId": 1,
      "name": "홍길동",
      "loginId": "student01",
      "phone": "010-0000-0000",
      "schoolName": "OO중학교",
      "grade": 2,
      "parentName": "학부모",
      "parentPhone": "010-1111-2222"
    }
    ```
  - 실패: 401, 403, 400 text 가능.
- 관련 코드:
  - `UserController.getMyStudentProfile`
  - `StudentSummaryResponse`


### ✅ POST /api/users/tutors

- 목적: 강사 회원가입. 학생 row를 만들지 않고 `users.role=TUTOR` 사용자만 생성한다.
- 현재 사용처:
  - `templates/auth/register.html`
- 향후 사용 가능성:
  - React 강사 온보딩 client
- 인증/권한: public.
- Request:
  - JSON body:
    ```json
    {
      "loginId": "tutor01",
      "password": "plain-password",
      "name": "강사",
      "nickname": "강사닉",
      "email": "tutor@example.com",
      "phone": "010-0000-0000"
    }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "userId": 10,
      "loginId": "tutor01",
      "name": "강사",
      "nickname": "강사닉",
      "displayName": "강사닉",
      "phone": "010-0000-0000",
      "role": "TUTOR"
    }
    ```
  - 실패: 400 text 가능.
- 관련 코드:
  - `UserController.createTutor`
  - `CreateTutorRequest`
  - `UserService.createTutor`

## 5. Subject API

### ✅ GET /api/subjects

- 목적: 과목 마스터 조회.
- 현재 사용처:
  - `templates/admin/dashboard.html`
  - `templates/tutor_dashboard.html`
  - `templates/tutor_academy_setup.html`
- 향후 사용 가능성:
  - React client
  - Python server가 subject id/name lookup에 사용할 수 있음
- 인증/권한: public.
- Request: 없음.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "createdAt": "2026-05-23T00:00:00",
        "updatedAt": "2026-05-23T00:00:00",
        "createdBy": "system",
        "updatedBy": "system",
        "subjectId": 1,
        "nameEn": "English",
        "nameKo": "영어"
      }
    ]
    ```
    ⚠️ Entity를 직접 반환하므로 BaseEntity 필드 직렬화 포함 여부는 Jackson/JPA 설정에 따라 확인 필요.
- 관련 코드:
  - `SubjectController.getSubjects`
  - `Subject`

## 6. Admin API

### ✅ GET /api/admin/students

- 목적: Admin 대시보드 학생 목록 + 활동 요약 조회.
- 현재 사용처:
  - `templates/admin/students.html`
- 향후 사용 가능성:
  - React Admin client
- 인증/권한: `ROLE_ADMIN` 필요.
- Request: 없음.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "studentId": 1,
        "userId": 1,
        "loginId": "student01",
        "name": "홍길동",
        "schoolName": "OO중학교",
        "grade": 2,
        "sourceCount": 3,
        "conversationCount": 2,
        "createdAt": "2026-05-23T00:00:00"
      }
    ]
    ```
  - 실패: 401, 403.
- 관련 코드:
  - `AdminController.listStudents`
  - `AdminStudentSummary`

## 7. Conversation API

### ✅ POST /api/conversations

- 목적: 학생 대화 세션 생성.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. STUDENT/ADMIN만 생성 가능. ADMIN은 `targetStudentId`로 임퍼소네이트 가능. TUTOR는 403(강사 전용 생성은 `POST /api/tutor/conversations`).
- Request:
  - Query parameters:
    - `targetStudentId` optional number — ADMIN 임퍼소네이트용.
  - Body 없음.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "conversationId": 1,
      "title": "새 대화",
      "createdAt": "2026-05-23T00:00:00",
      "lastMessageAt": "2026-05-23T00:00:00",
      "sourceCount": 0,
      "messageCount": 0
    }
    ```
  - 실패: 401, 403, 400 text 가능.
- 관련 코드:
  - `ConversationController.create`
  - `ConversationSummaryResponse`

### ✅ GET /api/conversations

- 목적: 학생 대화 목록 조회.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. STUDENT는 자기 목록, ADMIN은 `targetStudentId` 임퍼소네이트, TUTOR는 담당 학생(APPROVED) `targetStudentId` 지정 시 student-owned 목록 조회 가능.
- Request:
  - Query parameters:
    - `targetStudentId` optional number — ADMIN 임퍼소네이트/TUTOR 담당 학생 조회용.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "conversationId": 1,
        "title": "새 대화",
        "createdAt": "2026-05-23T00:00:00",
        "lastMessageAt": "2026-05-23T00:00:00",
        "sourceCount": 1,
        "messageCount": 2
      }
    ]
    ```
- 관련 코드:
  - `ConversationController.list`
  - `ConversationSummaryResponse`

### ✅ GET /api/conversations/{id}

- 목적: 대화 상세, 첨부 source, 메시지, 학생 정보 조회.
- 현재 사용처:
  - `static/js/conversation.js`
  - `static/js/conversation_dashboard.js`
  - `static/js/conversation_materials.js`
  - `static/js/conversation_report.js`
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. 학생형 상세 API. `ConversationService.getDetail`에서 `requireStudentOwnedConversationAccess`로 학생 소유 대화만 허용한다. STUDENT=본인, ADMIN=통과, TUTOR=APPROVED 담당 학생 대화만 읽기 가능. tutor-owned id는 403.
- Request:
  - Path parameters:
    - `id` number — conversation id.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "conversationId": 1,
      "title": "대화 제목",
      "createdAt": "2026-05-23T00:00:00",
      "student": {
        "studentId": 1,
        "name": "홍길동",
        "schoolName": "OO중학교",
        "grade": 2
      },
      "sources": [
        {
          "sourceId": 1,
          "fileName": "exam.pdf",
          "fileType": "PDF",
          "processingStatus": "COMPLETED",
          "contentKind": "EXAM_GRADED",
          "sourceType": "STUDENT_MATERIAL",
          "examId": 1,
          "examDisplayName": "OO중학교 2학년 MIDTERM 2026-1",
          "pageCount": 3
        }
      ],
      "messages": [
        {
          "messageId": 1,
          "role": "USER",
          "content": "질문",
          "createdAt": "2026-05-23T00:00:00"
        }
      ],
      "lessonId": 5,
      "lessonName": "고등 영어 내신반",
      "academyName": "테스트영어학원"
    }
    ```
  - `lessonId` / `lessonName` / `academyName`: nullable. APPROVED 신청으로 연결된 수업이 있을 때만 값이 채워짐. 없으면 `null`.
  - conversation dashboard는 `lessonName` 존재 여부로 lesson-linked 여부를 판단해 title/sidebar를 분기 렌더링한다.
- 관련 코드:
  - `ConversationController.detail`
  - `ConversationDetailResponse`
  - `LessonApplicationRepository.findFirstByConversation_ConversationIdAndStatusOrderByAppliedAtDesc`

### ✅ PATCH /api/conversations/{id}

- 목적: 학생이 본인 대화의 이름(대화명)을 직접 수정. 종합 현황판(`/conversation/dashboard`) 제목 옆 inline 편집(✎)에서 호출.
- 현재 사용처:
  - `static/js/conversation_dashboard.js` (`startTitleEdit`)
- 향후 사용 가능성:
  - React client inline 편집
- 인증/권한: 인증 필요. 학생형 수정 API. 소유 학생/ADMIN만 허용하며 TUTOR는 403. tutor-owned id도 403. `Conversation.title` 갱신(`updateTitle()`). `lessonName`은 lesson join 파생값이라 미수정.
- Request:
  - Path parameters:
    - `id` number — conversation id.
  - JSON body:
    - `title` string required — 새 대화명(trim 후 1~200자).
- Response:
  - 성공: `200 OK` — `{ "conversationId": 1, "title": "중간고사 대비" }`.
  - 실패: 400(빈/200자 초과), 401, 403(비소유자), 404.
- Redirect: 없음.
- 관련 코드: `ConversationController.updateTitle`, `ConversationService.updateTitle`, `UpdateConversationTitleRequest`, `ConversationTitleResponse`.

### ✅ POST /api/conversations/{id}/tutor-materials

- 목적: 강사/Admin이 학생 대화에 '시험대비 참고자료'(제출 불필요)를 업로드. `시험대비 자료`(`/conversation/tutor-materials`) 페이지에서 호출.
- 현재 사용처:
  - `static/js/conversation_tutor_materials.js` (`handleTutorUpload`)
- 향후 사용 가능성:
  - React client multipart upload
- 인증/권한: ADMIN/TUTOR 전용(`verifyAdminOrTutor`) + 대화 소유 검증(`verifyOwnsConversation` — TUTOR는 APPROVED 매칭 대화만). 학생 호출 시 403. 저장되는 Source는 `source_type=TUTOR_MATERIAL`, `content_kind=GENERAL` 고정, `uploaded_by`=호출 강사/Admin.
- Request:
  - Path parameters:
    - `id` number — conversation id.
  - Multipart form fields:
    - `files` file[] required — 1~20개 (PDF / JPG / PNG / WebP).
- Response:
  - 성공: `200 OK` — `POST /sources`와 동일한 `SourceAttachResponse[]` 형식.
  - 실패: 400(파일 없음/초과), 401, 403(학생 또는 비매칭 TUTOR), 404.
- Redirect: 없음.
- 관련 코드: `ConversationController.attachTutorMaterials`, `ConversationService.attachTutorMaterials`.
- 비고: 학생은 첨부된 TUTOR_MATERIAL을 `GET /api/sources/{sourceId}/file|download`로 열람한다 — `verifyOwnsSource`가 **conversation-scoped**로 확장되어, 업로더(강사)가 아니어도 본인 소유 대화에 첨부된 자료면 통과한다.

### ✅ POST /api/conversations/{conversationId}/consultation-materials

- 목적: 강사/Admin이 conversation 대시보드 "개별 상담 및 피드백" 카드에 상담 자료(파일만)를 업로드. 숙제/시험 매칭 없이 `conversation_sources`로 직접 매핑.
- 현재 사용처:
  - `static/js/conversation_dashboard.js` (`uploadConsultationMaterials`)
- 인증/권한: ADMIN/TUTOR 전용(`verifyAdminOrTutor`) + 대화 소유 검증(`verifyOwnsConversation` — TUTOR는 APPROVED 매칭 대화만). 학생 호출 시 403. 저장되는 Source는 `source_type=CONSULTATION`, `content_kind=GENERAL` 고정, `uploaded_by`=호출 강사/Admin.
- Request:
  - Path parameters:
    - `conversationId` number — conversation id.
  - Multipart form fields:
    - `files` file[] required — 1개 이상 (PDF / JPG / PNG / WebP).
- Response:
  - 성공: `200 OK` — `ConsultationMaterialResponse[]` (`{ sourceId, fileName, fileType, pageCount }`).
  - 실패: 400(파일 없음/형식 위반), 401, 403(학생 또는 비매칭 TUTOR), 404(대화 없음).
- Redirect: 없음.
- 관련 코드: `ConsultationMaterialController.upload`, `ConsultationMaterialService.upload`.
- 비고: 조회는 `GET /api/conversations/{id}`의 `sources`를 `sourceType=CONSULTATION`으로 필터, 썸네일/다운로드는 `GET /api/sources/{sourceId}/file|download?page=N`을 재활용한다(`verifyOwnsSource` conversation-scoped).

### ✅ DELETE /api/conversations/{conversationId}/consultation-materials/{sourceId}

- 목적: 강사(본인 담당)/Admin이 상담 자료를 물리삭제. Source + ConversationSource + 저장 파일 모두 제거.
- 현재 사용처:
  - `static/js/conversation_dashboard.js` (`deleteConsultationMaterial`)
- 인증/권한: ADMIN/TUTOR 전용(`verifyAdminOrTutor`) + 대화 소유 검증(`verifyOwnsConversation`). 학생 호출 시 403.
- Request:
  - Path parameters:
    - `conversationId` number — conversation id.
    - `sourceId` number — 삭제할 상담자료 source id.
- Response:
  - 성공: `204 No Content`.
  - 실패: 401, 403(학생 또는 비매칭 TUTOR), **404** — IDOR 가드: ① 이 conversation에 매핑되지 않은 sourceId, ② `source_type != CONSULTATION`(시험지/TUTOR_MATERIAL 등), ③ 미존재 sourceId. `deleteTrusted`(owner 검증 skip) 호출 전 위 가드가 유일 방어선이다.
- Redirect: 없음.
- 관련 코드: `ConsultationMaterialController.delete`, `ConsultationMaterialService.delete`, `ConversationSourceRepository.existsByConversationIdAndSourceId`.

### ✅ POST /api/conversations/{id}/sources

- 목적: 대화에 자료 파일 1개 이상 첨부. 업로드 후 분석 워크플로가 진행된다.
- 현재 사용처:
  - `static/js/conversation_upload.js`
- 향후 사용 가능성:
  - React client multipart upload
  - Python 분석 서버 연동 시 source 생성 트리거
- 인증/권한: 인증 필요. ADMIN은 `targetUserId` form field로 임퍼소네이트 가능.
- Request:
  - Path parameters:
    - `id` number — conversation id.
  - Multipart form fields:
    - `files` file[] required — 업로드 파일 1~N개.
    - `contentKind` optional enum — `GENERAL` 또는 `EXAM_BLANK`; 없으면 서버가 `GENERAL`로 처리.
    - `schoolName` optional string — 시험지 메타.
    - `grade` optional number — 시험지 메타.
    - `examType` optional enum — `MIDTERM`, `FINAL`, `MOCK`, `OTHER`.
    - `examYear` optional number.
    - `examSemester` optional number.
    - `targetUserId` optional number — ADMIN 임퍼소네이트용.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "sourceId": 1,
        "fileName": "exam.pdf",
        "fileType": "PDF",
        "fileSize": 123456,
        "processingStatus": "PENDING"
      }
    ]
    ```
  - 실패:
    - 400 rejected files JSON 가능.
    - 401, 403.
- 관련 코드:
  - `ConversationController.attachSources`
  - `SourceUploadRequest`
  - `SourceAttachResponse`

### ✅ POST /api/conversations/{id}/messages

- 목적: 대화 메시지 전송 후 assistant 응답 생성/저장.
- 현재 사용처:
  - `static/js/conversation.js`
- 향후 사용 가능성:
  - React chat client
- 인증/권한: 인증 필요. 학생/ADMIN형 메시지 API. TUTOR의 담당 학생(APPROVED) student-owned 접근은 읽기 전용이다. TUTOR는 tutor-owned 레코드 발송 시 `/api/tutor/conversations/{id}/messages`를 사용한다. tutor-owned id는 403.
- Request:
  - Path parameters:
    - `id` number — conversation id.
  - Query parameters:
    - `targetUserId` optional number — ADMIN 임퍼소네이트용.
  - JSON body:
    ```json
    {
      "content": "이 문제 설명해줘"
    }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "userMessage": {
        "messageId": 1,
        "role": "USER",
        "content": "이 문제 설명해줘",
        "createdAt": "2026-05-23T00:00:00"
      },
      "assistantMessage": {
        "messageId": 2,
        "role": "ASSISTANT",
        "content": "풀이 내용",
        "createdAt": "2026-05-23T00:00:01"
      }
    }
    ```
  - 실패:
    - 409 source processing not complete JSON 가능.
    - 401, 403, 400 text 가능.
- 관련 코드:
  - `ConversationController.sendMessage`
  - `MessageRequest`
  - `MessageResponse`


## 7.1 Tutor Conversation API

강사 개인 작업용 대화 기록. `/api/tutor/conversations/**`는 `ROLE_TUTOR` + `AuthenticatedUserResolver.requireTutorOwnedConversationAccess/Manage`가 모두 필요하다. 학생 소유 id, 다른 강사 id, ADMIN 호출은 403. API는 `TutorConversationController`, 화면/redirect는 `TutorConversationPageController`가 담당한다.

### ✅ POST /api/tutor/conversations

- 목적: 강사 소유 기록 생성.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js` (`createConversationAndOpen`)
- 인증/권한: TUTOR 본인만. `Conversation.forTutor(tutorUser, "새 강사 기록", null)`로 생성.
- Response: `200 OK` — `ConversationSummaryResponse` (`sourceCount=0`, `messageCount=0`, lesson fields null).
- 실패: 401, 403.
- 관련 코드: `TutorConversationController.create`, `ConversationService.createForTutor`.

### ✅ GET /api/tutor/conversations

- 목적: 강사 본인 기록 목록 조회.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js` (`loadList`)
- 인증/권한: TUTOR 본인만. `tutor_user_id` 기준 최신순.
- Response: `200 OK` — `ConversationSummaryResponse[]`. `sourceCount`는 강사 기록에 첨부된 자료 수, lesson fields는 null.
- 실패: 401, 403.
- 관련 코드: `TutorConversationController.list`, `ConversationService.listForTutor`.

### ✅ GET /api/tutor/conversations/{id}

- 목적: 강사 기록 상세(업로드 자료 + 메시지 포함) 조회.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js` (`openConversationDetail`, `refreshMaterials`), `static/js/conversation_materials.js`, `static/js/conversation.js`(tutor chat context)
- 인증/권한: TUTOR 본인 소유 tutor-owned id만. 학생 소유 id/다른 강사 id/ADMIN은 403.
- Response: `200 OK`
  ```json
  {
    "conversationId": 10,
    "title": "새 강사 기록",
    "createdAt": "2026-07-10T00:00:00",
    "student": null,
    "sources": [
      {
        "sourceId": 20,
        "fileName": "worksheet.pdf",
        "fileType": "PDF",
        "processingStatus": "COMPLETED",
        "contentKind": "GENERAL",
        "sourceType": "STUDENT_MATERIAL",
        "examId": null,
        "examDisplayName": null,
        "pageCount": 1
      }
    ],
    "messages": [],
    "lessonId": null,
    "lessonName": null,
    "academyName": null
  }
  ```
- 비고: 같은 `ConversationDetailResponse`를 쓰며 tutor-owned는 `student=null`, `lessonId/lessonName/academyName=null`이다. `sources`에는 강사 기록에 업로드한 자료가 포함된다.
- 관련 코드: `TutorConversationController.detail`, `ConversationService.getTutorDetail`, `ConversationDetailResponse`.

### ✅ PATCH /api/tutor/conversations/{id}

- 목적: 강사 기록명 변경.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js` (`renameConversation`)
- 인증/권한: TUTOR 본인 소유 tutor-owned id만.
- Request: JSON `{ "title": "기록명" }` — trim 후 1~200자.
- Response: `200 OK` — `{ "conversationId": 10, "title": "기록명" }`.
- 실패: 400(빈/200자 초과), 401, 403.
- 관련 코드: `TutorConversationController.updateTitle`, `ConversationService.updateTutorTitle`.

### ✅ DELETE /api/tutor/conversations/{id}

- 목적: 강사 기록 삭제.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js` (`deleteConversation`)
- 인증/권한: TUTOR 본인 소유 tutor-owned id만.
- Response: `204 No Content`.
- 실패: 401, 403.
- 관련 코드: `TutorConversationController.delete`, `ConversationService.deleteTutorConversation`.

### ✅ POST /api/tutor/conversations/{id}/sources

- 목적: 강사 소유 conversation 상세 대시보드에서 자료 업로드. 학생 `/conversation/dashboard`의 업로드 모듈을 endpoint만 바꿔 재사용한다.
- 현재 사용처: `static/js/tutor_conversation_dashboard.js`, `static/js/conversation_materials.js`, `static/js/conversation.js`(tutor chat context) + `static/js/conversation_upload.js` (`uploadUrl=/api/tutor/conversations/{id}/sources`).
- 인증/권한: TUTOR 본인 소유 tutor-owned id만. 학생 소유 id/다른 강사 id/ADMIN은 403.
- Request: `multipart/form-data`
  - `files`: 1~20개
  - `contentKind`: `GENERAL` 또는 `EXAM_BLANK` (없으면 `GENERAL`)
  - `schoolName`, `grade`: 시험지 저장 필수 메타. 강사 기록에서 누락하면 서버가 `schoolName=강사 학원명(없으면 강사 loginId)`, `grade=0`으로 보정.
  - `examType`, `examYear`, `examSemester`: 시험지 업로드 시 선택값. Claude 메타 추론이 꺼진 개발 환경에서는 강사 업로드 모달이 기본값(`OTHER`, 현재 연도, 현재 학기 추정)을 채워 보낸다.
- 저장: `SourceType=STUDENT_MATERIAL`, `uploaded_by`=호출 강사, `conversation_sources` 매핑 생성, `Conversation.metadata.sources` 갱신. 제목이 `새 강사 기록`이면 첫 파일명으로 갱신.
- Response: `200 OK` — `SourceAttachResponse[]`.
- 실패: 400(파일 없음/20개 초과), 401, 403.
- 관련 코드: `TutorConversationController.attachSources`, `ConversationService.attachTutorConversationSources`, `ConversationUpload`.

### ✅ POST /api/tutor/conversations/{id}/messages

- 목적: 강사 기록에 메시지 저장 + Claude 응답 저장.
- 현재 사용처: `static/js/conversation.js`의 tutor chat context (`/tutor/conversation/chat?id={id}`)
- 인증/권한: TUTOR 본인 소유 tutor-owned id만.
- Request: JSON `{ "content": "강사용 메모나 질문" }`.
- Response: `200 OK` — `MessageResponse` (`userMessage`, `assistantMessage`). 첫 메시지면 제목을 메시지 앞 30자로 갱신. 첨부 자료가 분석 중/PENDING_REVIEW이면 학생형 chat과 동일하게 전송을 차단한다.
- 실패: 401, 403, 409(자료 분석/검증 대기), 400 text 가능.
- 관련 코드: `TutorConversationController.sendMessage`, `ConversationService.sendTutorMessage`.

## 8. Source / Exam Review API

### ✅ GET /api/sources/pending-review

- 목적: 학생 대시보드의 검증 대기 시험지 목록 조회.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`
- 향후 사용 가능성:
  - React student dashboard
- 인증/권한: 인증 필요. ADMIN은 `targetUserId` query로 임퍼소네이트 가능.
- Request:
  - Query parameters:
    - `targetUserId` optional number — ADMIN 임퍼소네이트용.
- Response:
  - 성공: `200 OK`
    ```json
    [
      {
        "sourceId": 1,
        "originalFileName": "graded-exam.jpg",
        "examId": 1,
        "examDisplayName": "OO중학교 2학년 MIDTERM 2026-1",
        "createdAt": "2026-05-23T00:00:00"
      }
    ]
    ```
- 관련 코드:
  - `SourceController.listPendingReview`
  - `PendingReviewSourceResponse`

### ✅ GET /api/sources/{sourceId}/exam-review

- 목적: 채점된 시험지 검증 페이지에 메타 + 분석 결과 제공.
- 현재 사용처:
  - `static/js/exam_review.js`
- 향후 사용 가능성:
  - React exam review page
- 인증/권한: 인증 필요. `ExamReviewService.review`에서 `authResolver.verifyOwnsSource`로 본인 자료 접근만 허용한다.
- Request:
  - Path parameters:
    - `sourceId` number.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "sourceId": 1,
      "originalFileName": "graded-exam.jpg",
      "processingStatus": "PENDING_REVIEW",
      "contentKind": "EXAM_GRADED",
      "analysisResult": {
        "meta": {
          "school_name": "OO중학교",
          "grade": 2,
          "exam_type": "MIDTERM",
          "exam_year": 2026,
          "exam_semester": 1,
          "subject_id": 1
        },
        "matched_exam_id": 1,
        "questions": [
          {
            "question_no": 1,
            "question_label": "1",
            "question_text": "문항 내용",
            "question_type": "MULTIPLE_CHOICE",
            "choices": ["A", "B", "C", "D"],
            "correct_answer": "A",
            "student_answer": "B",
            "is_correct": false,
            "point": 3,
            "category": "grammar",
            "passage_ref": "P1"
          }
        ],
        "passages": [
          { "id": "P1", "content": "지문 내용" }
        ],
        "total_questions": 1,
        "correct_count": 0
      }
    }
    ```
  - 실패: 400 text 가능.
- 관련 코드:
  - `SourceController.getReview`
  - `ExamReviewResponse`
  - `ExamAnalysisResult`

### ✅ POST /api/sources/{sourceId}/exam-confirm

- 목적: 학생이 검증한 시험지 메타/채점 결과 확정. `questions`, `student_answers`, `student_scores`, `exams` 저장 및 source 완료 전이.
- 현재 사용처:
  - `static/js/exam_review.js`
- 향후 사용 가능성:
  - React exam review page
  - Python 분석 서버가 만든 결과의 최종 confirm API
- 인증/권한: 인증 필요. ADMIN은 `targetUserId` query로 임퍼소네이트 가능.
- Request:
  - Path parameters:
    - `sourceId` number.
  - Query parameters:
    - `targetUserId` optional number — ADMIN 임퍼소네이트용.
  - JSON body:
    ```json
    {
      "meta": {
        "schoolName": "OO중학교",
        "grade": 2,
        "examType": "MIDTERM",
        "examYear": 2026,
        "examSemester": 1
      },
      "answers": [
        {
          "questionNo": 1,
          "isCorrect": false,
          "point": 3
        }
      ]
    }
    ```
- Response:
  - 성공: `200 OK`
    ```json
    {
      "sourceId": 1,
      "examId": 1,
      "scoreId": 1,
      "totalQuestions": 20,
      "correctCount": 15,
      "dedupMatched": true
    }
    ```
  - 실패: 401, 403, 400 text 가능.
- 관련 코드:
  - `SourceController.confirm`
  - `ExamConfirmRequest`
  - `ExamConfirmResponse`

### ✅ DELETE /api/sources/{sourceId}

- 목적: 자료 영구 삭제. 관련 매핑/답안/점수/storage 파일 정리.
- 현재 사용처:
  - `static/js/conversation_dashboard.js`
  - `static/js/conversation_materials.js`
- 향후 사용 가능성:
  - React materials page
- 인증/권한: 인증 필요. `SourceService.delete`에서 `authResolver.verifyOwnsSource`로 본인 자료 접근만 허용한다.
- Request:
  - Path parameters:
    - `sourceId` number.
- Response:
  - 성공: `204 No Content`, body 없음.
  - 실패: 401, 403/400 text 가능.
- 관련 코드:
  - `SourceController.deleteSource`
  - `SourceService.delete`

### ✅ GET /api/sources/{sourceId}/file

- 목적: 라이트박스 미리보기용 이미지 bytes 제공 (JPEG, 애니메이션은 GIF/WebP 원본 포맷).
- 현재 사용처:
  - `static/js/conversation.js`
  - `static/js/conversation_materials.js`
- 향후 사용 가능성:
  - React file preview
- 인증/권한: 인증 필요. `authResolver.verifyOwnsSource(src)`로 본인 자료 검증.
- Request:
  - Path parameters:
    - `sourceId` number.
  - Query parameters:
    - `page` optional number, default `1`. PDF 변환 이미지 페이지 번호. IMAGE는 무시.
- Response:
  - 성공: `200 OK`, `Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`, body `byte[]`, header `Cache-Control: private, max-age=300`.
  - Content-Type은 저장 키의 확장자에서 판정한다(`ImageSanitizer.contentTypeForStorageKey`). PDF 페이지는 항상 JPEG.
  - 실패: 404 body 없음, 403 text 가능.
- 관련 코드:
  - `SourceController.getFile`

### ✅ GET /api/sources/{sourceId}/download

- 목적: 학생 자료 페이지 단위 다운로드 (JPEG, 애니메이션은 GIF/WebP 원본 포맷).
- 현재 사용처:
  - `static/js/conversation.js`
  - `static/js/conversation_materials.js`
- 향후 사용 가능성:
  - React file download
- 인증/권한: 인증 필요. `authResolver.verifyOwnsSource(src)`로 본인 자료 검증.
- Request:
  - Path parameters:
    - `sourceId` number.
  - Query parameters:
    - `page` optional number, default `1`.
- Response:
  - 성공: `200 OK`, `Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`, body `byte[]`, headers:
    - `Cache-Control: private, no-store`
    - `Content-Disposition: attachment; filename="..."; filename*=UTF-8''...`
  - 실패: 404 body 없음, 403 text 가능.
- 관련 코드:
  - `SourceController.downloadFile`

## 9. Dashboard Data API

### ✅ GET /api/conversations/{id}/weakness

- 목적: 대화에 첨부된 시험지 기준 카테고리별 정답률 radar 데이터 조회.
- 현재 사용처:
  - `static/js/conversation_dashboard.js`
- 향후 사용 가능성:
  - React conversation dashboard
- 인증/권한: 인증 필요. `WeaknessAnalysisService.analyzeForConversation`에서 `authResolver.verifyOwnsConversation`으로 본인 대화 접근만 허용한다.
- Request:
  - Path parameters:
    - `id` number — conversation id.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "categories": [
        {
          "categoryNameEn": "grammar",
          "categoryNameKo": "문법",
          "total": 10,
          "correct": 7,
          "accuracyPercent": 70.0
        }
      ]
    }
    ```
- 관련 코드:
  - `DashboardController.getWeakness`
  - `WeaknessRadarResponse`

### ✅ GET /api/conversations/{id}/wrong-answers

- 목적: 대화에 첨부된 시험지에서 학생이 틀린 문제를 시험별 그룹으로 묶어 반환. 오답노트 페이지 본문 데이터.
- 현재 사용처:
  - `static/js/conversation_review.js`
- 향후 사용 가능성:
  - React conversation review page
  - Admin이 학생 약점 분석 후 맞춤 학습자료 부여 흐름의 기초 데이터 (후속 §5.5 예정)
- 인증/권한: 인증 필요. `WrongAnswerService.analyzeForConversation`에서 `authResolver.verifyOwnsConversation`으로 본인 대화 접근만 허용. ADMIN은 임퍼소네이트로 통과.
- Request:
  - Path parameters:
    - `id` number — conversation id.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "exams": [
        {
          "examId": 1,
          "examDisplayName": "OO중학교 2학년 MIDTERM 2026-1",
          "wrongCount": 2,
          "totalCount": 10,
          "questions": [
            {
              "questionId": 50001,
              "questionLabel": "1",
              "orderNum": 1,
              "questionText": "문항 내용",
              "questionType": "MULTIPLE_CHOICE",
              "choices": "[\"A\", \"B\", \"C\", \"D\"]",
              "correctAnswer": "A",
              "studentAnswer": "B",
              "explanation": "해설 (nullable)",
              "categoryNameEn": "grammar",
              "categoryNameKo": "문법",
              "point": 3
            }
          ]
        }
      ]
    }
    ```
    - `exams`: 시험별 그룹. `order_num` 순으로 정렬된 오답 카드 목록을 포함한다.
    - `wrongCount`: 그룹 내 오답 수. `totalCount`: 그 시험의 학생 답안 총 개수(정답 + 오답).
    - `choices`: DB에 저장된 JSON 문자열 그대로(`questions.choices` raw). 프론트가 `JSON.parse`해서 사용한다. 단답형이면 null.
    - `categoryNameEn` / `categoryNameKo`: 매핑되지 않은 문제는 null — 프론트는 "기타"로 표시한다.
  - 실패: 401, 403.
- 빈 케이스: 첨부된 시험지가 없거나 모두 정답이면 `{ "exams": [] }` 반환.
- 관련 코드:
  - `DashboardController.getWrongAnswers`
  - `WrongAnswerService`
  - `WrongAnswerResponse`

### ✅ GET /api/students/{studentId}/score-timeline

- 목적: 학생 시험 점수 시계열 line chart 데이터 조회.
- 현재 사용처:
  - `static/js/conversation_dashboard.js`
- 향후 사용 가능성:
  - React student/conversation dashboard
- 인증/권한: 인증 필요. 학생은 자기 `studentId`만 가능, ADMIN은 path `studentId`로 임퍼소네이트 가능.
- Request:
  - Path parameters:
    - `studentId` number.
- Response:
  - 성공: `200 OK`
    ```json
    {
      "points": [
        {
          "examId": 1,
          "examDisplayName": "OO중학교 2학년 MIDTERM 2026-1",
          "examType": "MIDTERM",
          "examDate": "2026-05-23",
          "totalQuestions": 20,
          "correctCount": 15,
          "totalPoints": 100,
          "earnedPoints": 75,
          "accuracyPercent": 75,
          "confirmedAt": "2026-05-23T00:00:00"
        }
      ]
    }
    ```
  - 실패: 401, 403, 400 text 가능.
- 관련 코드:
  - `DashboardController.getScoreTimeline`
  - `ScoreTimelineResponse`


## 10. Academy / Lesson API

### ✅ GET /api/academies

- 목적: 공개 Academy 목록 조회. 카드 목록 UI에서 로고, 이름, 지역, 과목, 대상 학년을 표시한다.
- 현재 사용처:
  - `templates/academies.html`
  - `static/js/academies.js`
- 향후 사용 가능성:
  - React public academy marketplace/list
- 인증/권한: public. `PUBLISHED` Academy만 반환한다.
- Request query parameters:
  - `subjectId` optional integer
  - `targetGroup` optional enum `ELEMENTARY | MIDDLE | HIGH | UNIVERSITY | ADULT`
  - `city` optional string
  - `district` optional string
- Response:
  - 성공: `200 OK`, `AcademyListResponse[]`
  - 각 item: `academyId`, `slug`, `name`, `logoUrl`, `subjects`, `targetGroups`, `regions`
  - `targetGroups`는 저장 순서와 무관하게 항상 `ELEMENTARY → MIDDLE → HIGH → UNIVERSITY → ADULT` 순으로 정렬해 응답한다(`TargetGroup.canonicalOrder`). 배지 표기 순서를 화면마다 고정하기 위한 서버 보장이다.
- 관련 코드:
  - `AcademyController.listPublicAcademies`
  - `AcademyService.listPublic`
  - `AcademyRepository.findPublicList`
  - `AcademyListResponse`

### ✅ GET /api/academies/{slug}

- 목적: 공개 Academy 상세와 `sec-lessons`의 접이식 탭 프리뷰에 사용할 공개 수업상품 목록 조회.
- 현재 사용처:
  - `templates/academy_public.html`
  - `static/js/academy_public.js`
- 향후 사용 가능성:
  - 학생 검색/마켓플레이스 client
- 인증/권한: public. `PUBLISHED` Academy만 반환, 없으면 404.
- Request:
  - Path variable: `slug`
- Response:
  - 성공: `200 OK`, `AcademyResponse` with `lessons`. storage key와 수업 작성용 내부 메타데이터 `creationKey`, `authoringRevision`은 JSON property 자체를 응답하지 않는다.
  - `targetGroups`는 저장 순서와 무관하게 항상 `ELEMENTARY → MIDDLE → HIGH → UNIVERSITY → ADULT` 순으로 정렬해 응답한다(`TargetGroup.canonicalOrder`). hero 대상 배지 순서가 공개 페이지/Builder에서 동일하게 보이도록 하는 보장이다.
- 관련 코드:
  - `AcademyController.getPublicAcademy`
  - `AcademyService.getPublic`
  - `AcademyResponse`, `LessonResponse`

### ✅ GET /api/academies/{slug}/visits

- 목적: 공개 Academy의 오늘·누적·최근 7일 방문 횟수 조회. 소유 강사/Admin 화면 확인은 카운터를 늘리지 않는다.
- 인증/권한: public. `PUBLISHED && visible` Academy만 조회, 없으면 404.
- Response: `200 OK` + `{ today, total, recentDays: [{ date, count }] }`. 최근 7일은 방문이 없는 날짜도 `0`으로 포함한다.
- 관련 코드: `AcademyController.getVisitStatistics`, `AcademyVisitStatisticsService`.

### ✅ POST /api/academies/{slug}/visits

- 목적: 공개 Academy 방문을 기록하고 갱신된 통계를 반환한다.
- 인증/권한: public. `PUBLISHED && visible` Academy만 기록한다.
- 중복 정책: 서버 HTTP 세션·Academy·`Asia/Seoul` 날짜별 1회. 같은 세션의 새로고침은 증가하지 않는다.
- 개인정보: IP, User-Agent, 브라우저 ID를 저장하지 않는다.
- Response: GET과 같은 `AcademyVisitStatisticsResponse`.
- 관련 코드: `AcademyController.recordVisit`, `AcademyDailyVisitRepository.increment`.

### ✅ GET /api/admin/academies (방문 통계 포함)

- 기존 Academy/수업 노출 관리 응답의 각 Academy에 `visitStatistics`를 포함한다.
- Admin은 숨김 Academy를 포함한 모든 학원의 오늘·누적·최근 7일 통계를 확인한다.

### ✅ GET /api/academies/{slug}/logo

- 목적: 공개 Academy 로고 조회 (JPEG, 애니메이션은 GIF/WebP). storage key는 노출하지 않는다.
- 현재 사용처:
  - `static/js/academy_public.js`
  - `static/js/tutor_academy.js`
- 향후 사용 가능성:
  - React public academy card/list
- 인증/권한: public. `PUBLISHED` Academy만 조회.
- Request: Path variable `slug`.
- Response: 성공 `200 OK`, `Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`, body bytes. 실패 404.
- 관련 코드: `AcademyController.getPublicLogo`, `AcademyService.readPublicLogo`

### ✅ GET /api/academies/{slug}/cover

- 목적: 공개 Academy 커버 조회 (JPEG, 애니메이션은 GIF/WebP). 커버가 없으면 404.
- 현재 사용처:
  - `static/js/academy_public.js`
  - `static/js/tutor_academy.js`
- 향후 사용 가능성:
  - React public academy page
- 인증/권한: public. `PUBLISHED` Academy만 조회.
- Request: Path variable `slug`.
- Response: 성공 `200 OK`, `Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`, body bytes. 실패 404.
- 관련 코드: `AcademyController.getPublicCover`, `AcademyService.readPublicCover`

### ✅ GET /api/academies/{slug}/layout

- 목적: 공개 Academy 페이지의 해석된 레이아웃(schema + blocks) 조회. React 공개 preview 렌더용. 저장 레이아웃이 없는 레거시 Academy도 기본 레이아웃을 결정적으로 반환한다. 기존 SSR `/academies/{slug}/preview`와 동일 데이터의 JSON 버전.
- 현재 사용처:
  - (신규) React public academy preview
- 인증/권한: public. `PUBLISHED` Academy만 조회(`getPublishedEntity`). `/api/academies/**` GET permitAll 규칙에 포함.
- Request: Path variable `slug`.
- Response: `AcademyPageLayoutSchema` = `{ version: int, blocks: [...] }`. 실패 404(미공개/없음).
- 관련 코드: `AcademyController.getPublicLayout`, `AcademyService.getPublishedEntity`, `AcademyPageLayoutService.resolveLayoutSchema`

### ✅ GET /api/tutor/academy/me

- 목적: 로그인한 TUTOR의 Academy 관리 데이터 조회. 본인 Academy가 없으면 `204 No Content`.
- 현재 사용처:
  - `static/js/tutor_academy.js`
  - `static/js/tutor_academy_setup.js`
  - `static/js/lesson_product_editor.js` — `204`이면 unattached draft를 만들지 않고 `/builder`로 이동. `pageSettings.builderDraft.lessonsLayout == 'horizontal'`이면 편집기 미리보기를 공개 storefront와 같은 `programCardHtml`로 렌더한다(V2-7).
  - ~~`static/js/landing_publish.js`~~ — 구 랜딩 발행 폼 전용. 2026-08 Build It 랜딩 개편으로 `/`에서 더 이상 로드하지 않는다(파일은 정리 대상으로 남겨둠). 개편 랜딩의 "Build It 시작하기"는 `static/js/landing_buildit.js`가 `GET /api/auth/me`만 확인해 TUTOR면 `/builder`, 아니면 `/register?returnUrl=/builder`로 보낸다.
- 향후 사용 가능성:
  - React tutor dashboard bootstrap
- 인증/권한: `ROLE_TUTOR` + 서비스 owner guard.
- Request: 없음.
- Response: Academy가 있으면 `200 OK` + `AcademyResponse`, 없으면 `204 No Content`. 관리 응답의 `lessons`는 `HIDDEN`을 제외한 `DRAFT`/`PUBLISHED` 수업을 포함하고, 각 수업의 `creationKey`와 유효 `authoringRevision`(레거시 null은 `0`)을 반환한다.
- 관련 코드: `AcademyController.getMyAcademy`, `AcademyService.getMine`

### ✅ GET /api/tutor/academy/access-statistics

- 목적: TUTOR 본인 Academy의 ALB 요청 기반 누적·일별 접근 통계를 조회한다.
- 인증/권한: `ROLE_TUTOR` + 본인 Academy owner guard.
- Query: `days` optional, 기본 `30`, 허용 범위 `1~90`.
- Response: `200 OK`
  - `{ "metric": "alb_successful_root_requests", "total": 123, "daily": [{"date":"2026-08-03","count":4}], "lastAggregatedAt":"...Z" }`
  - 요청이 없던 날짜도 `count: 0`으로 채운다.
- 집계 정의: 성공(`200~399`)한 `GET https://{handle}.feel2real.co.kr/` 요청 수. 사람·순방문자·세션 수가 아니다.
- handle 변경: 요청 시점의 handle 이력을 `academy_id`로 해석하므로 기존 누적·일별 통계를 유지한다.
- 관련 코드: `AcademyAccessStatisticsController`, `AcademyAccessStatisticsQueryService`

### ✅ POST /api/tutor/academy

- 목적: TUTOR 본인 Academy 생성. 로고를 생략하면 서버가 기본 로고를 저장하며, 생성 결과는 즉시 PUBLISHED. handle을 생략하면 서버가 임시 공개주소(`academy-{userId 해시}`)를 자동 부여한다.
- **하나의 경로/서비스(`create`)를 Content-Type으로 분기한다** — 로고 파일 업로드는 multipart, 로고 없는 일반 클라이언트는 JSON도 가능하다. `/builder` 최초 발행은 로고 선택 여부와 무관하게 `academy` JSON part를 담은 multipart를 사용하고, 로고가 있으면 `logo` part를 추가한다. 별도 `/publish` 엔드포인트는 없다.
- 현재 사용처:
  - `static/js/tutor_academy_setup.js` (multipart)
  - `templates/public/landing.html` 발행 폼 (JSON)
- 향후 사용 가능성:
  - React tutor onboarding, LLM 클라이언트(JSON)
- 인증/권한: `ROLE_TUTOR`. 이미 Academy가 있으면 **`409 Conflict`**.
- Request (둘 중 하나):
  - **`multipart/form-data`** parts:
    - `academy`: JSON `{ name, handle, intro, subjectIds, targetGroups, regions }`
      - `handle`: optional. 비우면 서버가 `academy-{hash}` 임시값을 생성한다. 입력하면 3~30자 소문자/숫자/하이픈, 예약어/중복 차단.
      - `intro`: required, max 1000자
      - `regions`: 1~3개. SSR은 시/구 드롭다운으로 입력하며 구/군 미선택 시 `ALL` 저장
    - `logo`: image file, optional. 미첨부/null이면 서버가 기본 JPEG 로고를 `FileStorage`에 저장해 `logoStorageKey`를 채운다.
    - `cover`: image file, optional
  - **`application/json`** body `{ name, handle, intro, subjectIds, targetGroups, regions }`:
    - 필수값은 multipart와 동일(`targetGroups`·`regions`는 hero badge 공개 렌더 → 폼에서 수집).
    - 랜딩 발행 폼은 handle을 보내지 않는다. 서버가 임시 handle을 자동 부여하고, 사용자는 `/tutor/academy/setup`에서 이후 수정한다.
    - 로고/커버 입력 없음 → 로고는 서버 기본 로고, 커버는 null. `/builder`에서 발행 전 선택한 로고는 같은 multipart `POST`의 `logo` part로 저장하며, 이후 multipart `PUT`으로 교체 가능.
- Response: 성공 `200 OK`, `AcademyResponse`.
- 관련 코드: `AcademyController.createAcademy`(multipart)·`createAcademyJson`(JSON), `AcademyService.create`

### ✅ PUT /api/tutor/academy

- 목적: TUTOR 본인 Academy 수정. 기존 로고가 있으면 logo part 생략 가능. 공개주소(handle)는 setup 화면에서 수정 가능하며, 새 값은 형식/예약어/중복 검증을 통과해야 한다.
- 현재 사용처:
  - `static/js/tutor_academy_setup.js` — multipart 기본정보/이미지 수정
  - `static/js/academy_builder.js` — `/builder` 텍스트 inline 편집(학원명/소개/상세 소개/커리큘럼 통합 제목/히어로 지표), 공개 주소(handle) 편집, 기본 데이터 모달(수업대상/과목/지역), 로고 이미지 저장. 기존 subjectIds/pageSettings 등 필수값은 SSR Academy JSON에서 보존해 함께 전송한다.
- 향후 사용 가능성:
  - React tutor settings
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request: `POST /api/tutor/academy`와 동일한 구조.
  - `multipart/form-data`: setup 화면 기본정보/이미지 수정, Builder 로고 이미지 수정.
  - `application/json`: Builder 텍스트 inline 저장처럼 파일 변경 없이 기존 필수값을 보존해 수정.
- Response: 성공 `200 OK`, `AcademyResponse`.
- 관련 코드: `AcademyController.updateAcademy`(multipart), `AcademyController.updateAcademyJson`(JSON), `AcademyService.update`

### ✅ PUT /api/tutor/academy/page-settings

- 목적: Builder에서 공개 페이지 표시 설정을 부분 업데이트한다. 현재 사용 범위는 섹션 표시/숨김(`sectionVisibility`), 레이아웃 탭 섹션 순서(`sectionOrder`), 외부 연동 URL(`blogUrl`/`naverMapUrl`), 학원별 공개 footer 사업자 정보(`businessInfo`)다. `heroYoutubeUrl` 필드는 하위 호환으로 계속 받는다. Builder 상세 문구, 대표 수업 선택(`builderDraft.featuredLessonIds`, lesson ID 최대 4개 쉼표 결합), 홈페이지 업로드 영상 선택(`builderDraft.heroVideoId`/`principalVideoId`), 히어로 지표(`builderDraft.monthlyTuition`, `builderDraft.scoreImprovement`, `builderDraft.operationYears`), 섹션별 독립 제목(`builderDraft.videosSectionTitle`·`socialSectionTitle` — 미설정 시 프런트가 학원명 파생값/고정 문구로 fallback, 마이그레이션 불필요. GNB는 기존 `headerTitle`, 커리큘럼은 기존 `curriculumTitle`), Education Storefront 문구(`builderDraft.featuredEyebrow`/`featuredTitle`/`featuredDescription`/`featuredItem1..4`/`featuredCtaLabel`, `builderDraft.aiProcessEyebrow`/`aiProcessTitle`/`aiProcessDescription`/`aiProcessStep1..5`/`aiProcessNote` — 전부 재사용 가능한 generic key로 학교명·연도·학원명을 하드코딩하지 않는다)는 허용 key/길이 검증 후 `POST/PUT /api/tutor/academy`의 `pageSettings` payload로 저장한다.
- 현재 사용처:
  - `static/js/academy_builder.js` — `/builder` 블록 탭의 실제 데이터 섹션(`sec-intro`, `sec-principal`, `sec-lessons`, `sec-social`) 표시 토글. 기존 Academy는 이 endpoint로 즉시 저장하고, 신규 발행 전 Academy는 `POST /api/tutor/academy`의 `pageSettings.sectionVisibility`에 포함한다. 미래 5개 섹션(`sec-reviews`/`sec-curriculum`/`sec-effect`/`sec-market`/`sec-payment`)은 모든 정상 write payload에서 명시적 `false`로 정규화한다.
  - `static/js/academy_builder.js` — `/builder` 레이아웃 탭 up/down 버튼. 기존 Academy는 `{sectionOrder:[...]}`만 보내 즉시 저장하고, 신규 발행 전 Academy는 `pageSettings.sectionOrder`에 포함한다.
  - `static/js/academy_builder.js` — 히어로/원장 영상 영역 클릭 → 업로드 영상 또는 YouTube 선택 → 기존 Academy는 상단 발행의 Full PUT으로, 신규 발행 전 Academy는 pending payload로 저장한다. `sec-social`의 네이버 블로그/지도 URL 등록 버튼은 각각 `blogUrl`/`naverMapUrl` 하나씩만 저장하고, 저장 URL은 썸네일형 프리뷰 카드로 표시하며, Builder preview에서는 클릭 시 실제 이동 대신 URL 수정 모달을 연다.
  - `static/js/academy_builder.js` — Builder preview의 fixed-chrome footer 또는 특정 값 텍스트를 클릭하면 상호명·대표·주소·사업자등록번호·통신판매업신고번호·전화·이메일 7개 값만 다른 섹션과 같은 field-scoped `contenteditable` 상태와 노란색 테두리로 표시한다. 별도 form/modal은 열지 않으며 footer 우상단 Apply 컨트롤로 반영한다. 사업자등록번호 입력은 숫자만 취해 최대 10자리까지 `000-00-00000` 형식으로 자동 포맷한다. 기존 Academy는 `businessInfo`를 즉시 저장하고, 신규 발행 전 Academy는 authoritative `pendingAcademyPublish.pageSettings.businessInfo`에 포함한다. 공개 홈과 해당 Academy 블로그 목록/상세가 같은 값을 렌더한다.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request JSON:
  - `{ "sectionVisibility": { "sec-intro": true, "sec-principal": true, "sec-lessons": true, "sec-social": false, "sec-reviews": false, "sec-curriculum": false, "sec-effect": false, "sec-digitaltwin": false, "sec-market": false, "sec-payment": false } }`
  - `{ "sectionOrder": ["sec-hero", "sec-intro", "sec-principal", "sec-videos", "sec-reviews", "sec-lessons", "sec-curriculum", "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social"] }
  - `{ "naverMapUrl": "https://map.naver.com/p/entry/place/1422644365" }`
  - `{ "heroYoutubeUrl": "https://www.youtube.com/watch?v=VIDEO_ID" }`
  - `{ "businessInfo": { "businessName": "샘플 영어학원", "representative": "대표자명", "address": "00시 00구 00로 00", "businessRegistrationNumber": "000-00-00000", "mailOrderRegistrationNumber": "0000-00-0000-0000", "phone": "000-0000-0000", "email": "academy@example.com" } }`
  - `blogUrl`, `storeUrl`, `naverMapUrl`, `heroYoutubeUrl`, `sectionVisibility`, `sectionOrder`, `businessInfo`가 `null`/미포함이면 부분 업데이트에서 기존 값을 유지한다. Full `PUT /api/tutor/academy`도 구형 client가 `pageSettings` 또는 `businessInfo`를 생략하면 기존 businessInfo를 보존한다. 7개 direct field를 명시적으로 빈 문자열로 보내면 raw blank로 clear한다.
  - `businessInfo`의 null/누락/빈 문자열은 공개 렌더에서 필드별 마스킹 dummy로 fallback하며, dummy는 저장 payload로 역유입되지 않는다. 상호명 fallback만 Academy 이름을 사용하고 공용 랜딩의 실제 사업자 정보는 복사하지 않는다.
  - 예외: 공개 홈 우상단 `상담 신청` 모달의 `tel:`/`mailto:` 링크는 마스킹 dummy를 쓰지 않는다. `phone`/`email` 중 nonblank 값만 옵션으로 노출하고 둘 다 blank면 모달을 열지 않는다(기존 데모 안내 유지). → [FLOWS.md](FLOWS.md) §5.5
  - Builder의 `상담 신청` 연락처 모달도 endpoint를 추가하지 않고 이 `PUT /api/tutor/academy/page-settings`의 `businessInfo`를 그대로 쓴다. `phone`/`email`만 바꾸고 나머지 5개 field는 현재 값을 함께 실어 보낸다(부분 payload를 보내면 생략한 field가 blank로 clear된다). 전화 형식(`0` 시작 9~11자리, 서울 `02`=2-3-4/2-4-4, 그 외=3-3-4/3-4-4)과 이메일 형식은 **client 검증**이며 서버 계약은 기존 길이/이메일 검증 그대로다. → [FLOWS.md](FLOWS.md) §5.5
  - `businessInfoUrl`은 response/legacy JSON 호환 field로만 유지한다. Request에 임의 URL이 있어도 무시하며, nonblank 사업자등록번호에서 숫자 10자리를 추출해 서버가 `https://www.ftc.go.kr/bizCommPop.do?wrkr_no={digits}`로 canonicalize한다. Blank 등록번호는 저장 URL이 null이고 화면에서만 masked FTC link를 사용한다. Invalid legacy 번호는 번호는 표시하되 FTC navigation을 만들지 않으며, 새 write는 invalid digit count를 거부한다. `email` 형식과 각 field 최대 길이도 검증한다. `storeUrl`은 기존 데이터 호환용으로 유지하고, Builder UI의 지도 링크는 `naverMapUrl`을 우선 사용한다. 공개 주소(handle)는 `/builder` 브라우저 URL 영역에서 수정하되 저장은 기존 `POST/PUT /api/tutor/academy`의 `handle` 필드를 사용하고, 모달의 `중복확인` 버튼 및 적용 시 기존 `GET /api/tutor/academy/handle-available`로 unique 여부를 확인한다.
  - `sectionOrder` canonical default full order는 `sec-hero`, `sec-featured`, `sec-intro`, `sec-principal`, `sec-philosophy`, `sec-videos`, `sec-reviews`, `sec-lessons`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`, `sec-social`이다. non-null이면 null/unknown/duplicate id, hero 포함 시 첫 번째 아님을 400으로 거부한다. **관리 섹션 누락은 더 이상 400이 아니다** — 서버가 빠진 섹션을 canonical 위치에 채워 넣어 완전한 순서로 저장한다(`normalizeSectionOrderForWrite`). 서버 목록과 3개 JS 파일(`academy_public.js`/`academy_builder.js`)의 목록이 어긋나도 옛/캐시된 클라이언트의 레이아웃 저장이 깨지지 않게 하는 안전장치다.
  - Builder 블록 탭은 실제 데이터 섹션만 토글한다. 미래 5개 섹션(`sec-reviews`/`sec-curriculum`/`sec-effect`/`sec-market`/`sec-payment`)은 legacy true/null/누락에도 effective OFF이고 Builder 진입 시 별도 write를 발생시키지 않는다. Education Storefront 섹션 `sec-featured`(시즌/Featured 프로그램)와 `sec-digitaltwin`(AI Native 교육 과정)은 **opt-in**이라 `sectionVisibility[id] === true`일 때만 공개/Builder에 렌더된다(기본 OFF — 기존 학원 사이트 불변). 나머지 데이터 섹션은 반대로 명시적 `false`일 때만 숨는다. 레이아웃 탭은 ON 섹션만 보여주고 up/down 버튼으로 조정한다. OFF 섹션은 화면에서 제외되지만 저장된 full order slot은 유지한다. 서버는 `sectionVisibility`와 `sectionOrder`를 독립 값으로 저장한다.
  - URL 필드는 `http://` 또는 `https://`만 허용한다. 프론트는 `heroYoutubeUrl`에 YouTube watch/youtu.be/embed URL만 입력 가능하게 제한하고 공개 렌더 시 embed URL로 정규화한다.
- Response: 성공 `200 OK`, `AcademyResponse`. 공개 페이지(`/academies/{slug}`)는 `pageSettings.sectionOrder` 순서로 섹션 stack을 렌더하고, 누락/null legacy rows는 canonical default로 fallback한다. `pageSettings.sectionVisibility[id] === false`인 관리 섹션은 렌더하지 않지만 전체 순서에는 남아 다시 보이면 같은 slot에 복귀한다. `sec-featured`/`sec-digitaltwin`은 opt-in이라 `sectionVisibility[id] === true`가 아니면 렌더에서 제외된다. hero(`sec-hero`)는 항상 첫 번째/ON으로 고정하며 미래 5개 섹션은 저장값과 무관하게 공개/Builder 렌더에서 제외한다. Footer는 이 설정들과 분리된 fixed page chrome이라 숨김·삭제·이동할 수 없고 ordered sections 다음에 항상 렌더한다. 공개 홈과 `/academies/{academySlug}/posts`, `/academies/{academySlug}/posts/{postSlug}` 학원 블로그는 `pageSettings.businessInfo`를 공통 footer에 표시한다.
- 관련 코드: `AcademyController.updatePageSettings`, `AcademyService.updatePageSettings`

### ✅ DELETE /api/tutor/academy

- 목적: TUTOR 본인 Academy 삭제. 삭제 후 같은 강사는 새 Academy를 다시 생성할 수 있다.
- 현재 사용처:
  - `static/js/tutor_academy.js` — `/tutor/academy` 상단 `Academy 삭제` 버튼
- 향후 사용 가능성:
  - React tutor settings / academy management
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request: 없음.
- Response:
  - 성공: `204 No Content`
  - 본인 Academy 없음: `404`
  - 승인된 신청/수강생이 있는 lesson이 하나라도 있으면 `409 Conflict`, 삭제 차단
  - 영상, 업로드 세션, 최적화 job, DELETING tombstone 또는 cleanup row가 하나라도 남아 있으면 `409 Conflict`. 각 영상을 삭제하고 durable cleanup/물리 삭제가 끝난 뒤 재시도한다.
- 삭제 정책:
  - tutor 소유 Academy 행을 pessimistic lock한 뒤 guard를 검사한다. 업로드 세션 initiation의 동일 부모행 lock과 직렬화되어 guard 통과 뒤 새 예약이 생길 수 없다.
  - 수강생과 모든 영상 lifecycle state가 없을 때만 물리 삭제한다.
  - DB FK cascade로 Academy 하위 subject/target/region/lesson 관계가 정리된다. 영상 asset 정리에는 cascade를 사용하지 않는다.
  - logo/cover storage 파일은 DB 삭제 후 best-effort로 삭제한다.
- 관련 코드: `AcademyController.deleteAcademy`, `AcademyService.delete`

### ✅ GET /api/tutor/academy/layout

- 목적: 빌더 초기 로딩용. 내 Academy의 레이아웃 schema(`{version, blocks[]}`)를 반환한다. 저장본이 없으면 `AcademyDefaultLayoutFactory` 기본 레이아웃.
- 인증/권한: `ROLE_TUTOR` + owner guard (`AcademyService.requireMyAcademy`).
- Response: `200` schema JSON / 본인 Academy 없음 `404`.

### ✅ PUT /api/tutor/academy/layout

- 목적: 빌더가 보낸 전체 semantic schema로 레이아웃을 **upsert**(생성+덮어쓰기). 1 Academy 1 레이아웃.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request: body = `AcademyPageLayoutSchema` (`{version:int, blocks:[{type,order,config}]}`). allowlist 외 block 타입은 `400`.
- Response: `200` 해석된 schema / allowlist 위반 `400` / 본인 Academy 없음 `404`.

### ✅ DELETE /api/tutor/academy/layout

- 목적: 저장 레이아웃 물리 삭제. 이후 `resolveLayoutSchema`가 기본 레이아웃으로 fallback 하므로 공개 렌더는 계속 동작. 저장본 없어도 멱등.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Response: `204 No Content` / 본인 Academy 없음 `404`.
- 관련 코드: `AcademyLayoutApiController`, `AcademyPageLayoutService.saveLayout/deleteLayout`

### ✅ PUT /api/tutor/academy/lessons/collection

- 목적: 전용 편집기의 신규 draft와 변경된 기존 수업을 한 트랜잭션에서 원자적으로 저장한다. 요청 항목 하나라도 실패하면 전체 요청을 롤백한다.
- 현재 사용처:
  - `static/js/lesson_product_editor.js`
- 인증/권한: `ROLE_TUTOR` + Academy owner guard.
- Request: `LessonCollectionSaveRequest` = `{ "items": LessonCollectionItemRequest[] }`.
  - 편집기는 모든 신규 draft와 `draft != serverSnapshot`인 기존 수업만 포함한다. 요청에서 빠진 기존 수업은 조회·잠금·revision 검사·수정·숨김 대상이 아니다.
  - 기존 항목은 `lessonId`와 `expectedAuthoringRevision`을 보내고 `creationKey`는 `null`로 보낸다.
  - 신규 항목은 Academy 범위에서 안정적인 UUID `creationKey`를 보내고 `lessonId`/`expectedAuthoringRevision`은 `null`로 보낸다.
  - `lessonId`와 `creationKey`는 정확히 하나만 있어야 하며, 한 요청 안에서 ID/key/해석된 대상 수업이 중복되면 `400 Bad Request`다.
  - 신규 수업의 `authoringRevision`은 1에서 시작한다. 기존 수업은 아래 작성 상태가 실제로 바뀔 때 한 번 증가한다.

| `LessonCollectionItemRequest` 필드 | 규칙 |
|---|---|
| `lessonId`, `creationKey`, `expectedAuthoringRevision` | 저장 대상 식별과 재시도/동시 수정 검증용. `creationKey`는 36자 UUID, 기존 revision은 0 이상 필수 |
| `name`, `description` | 수업상품명 필수·100자 이하(예시 HTML이 요구하는 유일한 필수값), 설명 nullable |
| `schoolName`, `gradeLabel` | 학교·학년 자유문구. 각각 100/50자 이하, trim, blank→null. 두 필드는 canonical replay/stale/creation-key binding 비교에 포함한다 |
| `lessonType`, `targetGroup`, `status` | 전부 nullable. 편집기는 셋 다 보내지 않는다. `status`가 null이면 서버가 `PUBLISHED`로 정규화하고, 값이 오면 `DRAFT|PUBLISHED`만 허용한다. `lessonType`/`targetGroup`은 null을 그대로 저장한다(기본값 주입 없음) |
| `priceAmount`, `priceUnit`, `priceSummary`, `maxCapacity` | 전부 nullable이며 amount/unit은 서버 collection 계약에서 독립적이다. amount가 있으면 1원 이상만 검사하므로 레거시 `positive/null`도 replay·학교/학년 수정 round-trip이 가능하다. 편집기 신규/가격수정 UI만 `null/null`, `positive/MONTH|COURSE`, `null/NEGOTIABLE` matrix를 강제한다. `maxCapacity`가 null이면 무제한 접수 |
| `scheduleType`, `weeklyCount`, `weekdays`, `startDate`, `endDate`, `durationWeeks`, `durationMonths`, `scheduleSummary` | 전부 nullable. 편집기가 받지 않는다. `scheduleType`이 명시된 경우에만 하위 규칙을 검사한다(`WEEKLY`는 횟수와 요일, `PERIOD`는 유효한 날짜 범위 또는 양수 기간) |
| `tabGuidanceLabel`, `tabShortName` | 탭 안내(120자), 탭 짧은 이름(80자) |
| `kicker`, `highlight`, `learnerNeed`, `coreDetailTitle` | 공개 페이지 문구. 각각 200/300/300/200자 이하 |
| `explanation1`, `explanation2`, `explanation3` | 핵심 설명 각 500자 이하 |
| `theme` | `NAVY|TEAL|PURPLE|ORANGE|SKY|PINK|GREEN`; null은 `NAVY`로 정규화 |
| `mediaImageKey`, `mediaVideoId` | 대표 미디어. **둘 다 non-null이면 `400`**(XOR). `mediaImageKey`는 `POST /api/tutor/academy/media/lesson-hero`가 돌려준 key(500자 이하), `mediaVideoId`는 **내 학원 소유** `academy_videos.video_id`여야 한다(아니면 `400` — IDOR 차단). 둘 다 null이면 미디어 해제 |
| `otVideoId` | 수업 OT 영상. 대표 미디어와 **독립적으로** 저장·교체·해제하며, 내 학원 소유 `academy_videos.video_id`만 허용한다. null이면 OT 영상 해제 |

- **대표 미디어는 이 endpoint로만 저장된다.** 레거시 `PUT /api/tutor/academy/lessons/{lessonId}`는 미디어 필드를 받지 않으며, 그 경로로 수정해도 기존 미디어는 보존된다(`updateDetails`가 현재 값을 forwarding).
- 대표 미디어 또는 OT 영상만 바뀐 저장도 canonical 작성 상태 변경으로 취급해 `updated`로 분류되고 revision이 오른다(`replayed` 아님).

- Response: `200 OK`, `LessonCollectionSaveResponse`.
  - `savedItems`: 요청 순서와 같은 `LessonResponse[]`.
  - `requestedCount`, `createdCount`, `updatedCount`, `replayedCount`: 요청/생성/변경/상태 동일 재처리 개수.
  - `LessonResponse`는 기존 운영 필드와 `schoolName`/`gradeLabel`에 위 공개 페이지 문구, `theme`, `creationKey`, 유효 `authoringRevision`을 함께 반환한다. 레거시 null theme/revision은 각각 `NAVY`/`0`으로 반환한다. 공개 응답은 학교·학년 property 자체를 생략한다.
  - 대표 미디어는 `mediaType`(`IMAGE|VIDEO`, 두 컬럼에서 파생하며 없으면 필드 자체가 빠짐)과 `mediaVideoId`로 반환한다. `mediaImageKey`는 **강사 응답에만** 담기고 공개 응답(`fromPublic`)에서는 제외된다. 별도 OT 영상은 `otVideoId`로 반환한다.
- 동시성/재시도:
  - 서버는 Academy 범위 creation-key binding을 scalar로 먼저 해석한 뒤 대상 lesson ID 합집합을 오름차순 `PESSIMISTIC_WRITE`로 한 번 잠근다.
  - `expectedAuthoringRevision`이 현재 값과 달라도 제출한 canonical 작성 상태가 현재 서버 상태와 같으면 성공 재처리로 계산하고 revision을 올리지 않는다.
  - 신규 `creationKey`가 이미 같은 작성 상태에 묶여 있어도 성공 재처리다. 같은 key의 상태가 다르면 binding conflict다.
- 실패:
  - 일반 검증 오류는 `400` text, 소유권 위반은 `403`, 누락/타 Academy 대상은 `404`다.
  - 아래 동시성 충돌은 모두 `409 Conflict`와 JSON `{ "code": string, "message": string, "retryable": boolean }`을 반환한다.

| code | retryable | 의미 |
|---|:-:|---|
| `LESSON_AUTHORING_STALE` | `false` | revision이 오래됐고 제출 상태도 최신 서버 상태와 다름 |
| `LESSON_CREATION_KEY_BINDING_CONFLICT` | `false` | creation key가 다른 canonical 작성 상태의 수업에 이미 연결됨 |
| `LESSON_CREATION_KEY_RACE` | `true` | 동일 Academy/creation key의 동시 insert가 정확한 unique constraint에서 충돌 |
| `LESSON_AUTHORING_LOCK_CONFLICT` | `true` | lesson row lock timeout/deadlock |

- 관련 코드: `AcademyController.saveLessonCollection`, `LessonCollectionService`, `LessonCollectionExceptionHandler`

### ✅ GET /api/tutor/lms/{handle}/lessons

- 목적: `/lms/{handle}/manage` 강좌관리 탭에 owner의 실제 `LessonProduct`를 표시한다. 다른 LMS 화면과 후기 탭은 계속 MOCK을 사용한다.
- 인증/권한: `ROLE_TUTOR` + `AcademyService.requireOwnedAcademyByHandle(handle)`. 익명 `401`, STUDENT/ADMIN `403`, 다른 학원 TUTOR `403`, 미존재 handle `404`.
- 조회: 해당 Academy의 `DRAFT|PUBLISHED`만 포함하고 `HIDDEN`은 제외한다. `isVisible=false`라도 HIDDEN이 아니면 강사 작성 목록에 포함한다. 정렬은 `createdAt ASC, lessonId ASC`.
- Response: `200 OK`, `LmsManageLessonResponse[]`. allowlist는 정확히 `lessonId`, `schoolName`, `gradeLabel`, `name`, `priceAmount`, `priceUnit`, `description`이며 creation/revision/media/일정/정원/마케팅 작성 필드는 노출하지 않는다.
- 삭제는 신규 endpoint가 아니라 기존 `DELETE /api/tutor/academy/lessons/{lessonId}`를 재사용한다. 성공 `204`는 HIDDEN 처리, 승인 신청/수강생이 있으면 `409`이며 표 row/count를 유지한다.
- 관련 코드: `AcademyLmsManageApiController`, `AcademyService.listOwnedLmsLessonsByHandle`, `LmsManageLessonResponse`

### ✅ GET /api/tutor/academy/lessons/{lessonId}

- 목적: 강사용 수업 운영 상세 조회. `HIDDEN` 수업은 `404`로 처리한다.
- 인증/권한: `ROLE_TUTOR` + lesson.academy owner guard.
- Response: 성공 `200 OK`, `LessonResponse`. 실패 404/403 가능.
- 관련 코드: `AcademyController.getLesson`, `LessonProductService.getMine`

### ✅ POST /api/tutor/academy/videos

- 목적: 강사 브라우저가 영상을 application 서버로 multipart 업로드한다. 서버가 요청 안에서 ffprobe
  검증, H.264/AAC MP4 정규화, JPEG thumbnail 생성을 마친 뒤 private storage와 READY row를 저장한다.
- 인증/권한: `ROLE_TUTOR` + Academy owner guard.
- Request: `multipart/form-data`, part 이름 `video`.
- 제한: 원본/변환 산출물 각각 `<=209715200` bytes, 길이 `<=120초`, 학원당 최대 30개(직접 업로드 + 외부 링크 합산).
  화면은 MP4/MOV만 선택하게 하고 서버는 실제 codec과 길이를 다시 검사한다.
- 저장 정합: 변환과 storage IO는 DB transaction 밖에서 수행한다. 마지막 row insert가 실패하면 이미
  저장한 MP4/thumbnail을 보상 삭제한다.
- Response: `200 AcademyVideoResponse` / 제한·변환 실패 `400` / 동시 변환 permit 초과 `503`.
- 현재 사용처: `static/js/tutor_academy_videos.js`.

### ✅ POST /api/tutor/academy/videos/link

- 목적: 네이버·YouTube 등에 이미 올라간 학원 영상을 **URL 자체만** Academy 영상 자산으로 등록한다.
  BuildIt 서버로 다운로드·복제·트랜스코딩하지 않는다.
- 인증/권한: `ROLE_TUTOR` + Academy owner guard (`/api/tutor/**` 기존 규칙, 신규 SecurityConfig 없음).
- Request: `application/json` `{ "url": "https://...", "title": "표시 제목", "purpose": "LESSON|DIRECTOR|NONE" }`.
- 검증(`ExternalVideoUrlValidator`, 네트워크 호출 없음 = SSRF-safe): `https` scheme만 허용
  (`http`/`javascript:`/`data:` 거부), host로 provider 분류(`NAVER`=naver.me·`*.naver.com` / `YOUTUBE` /
  `GENERIC`). YouTube는 video id를 뽑아 `external_embed_url=https://www.youtube.com/embed/<id>`를 저장하고,
  그 외 provider는 embed 없이 `[영상 보기]` CTA로만 노출한다(`naver.me` redirect는 따라가지 않는다).
- 저장: `academy_videos` 행 하나(`source_kind='EXTERNAL_LINK'`, `active_source='EXTERNAL'`,
  `lifecycle_status='READY'`, storage key 없음). 개수 상한은 직접 업로드와 합산(최대 30개).
- Response: `200 AcademyVideoResponse`(`sourceKind`·`externalUrl`·`embedUrl`·`provider`, `videoUrl`/`thumbnailUrl`는 null) / 검증·개수 실패 `400`.
- 삭제: `DELETE /api/tutor/academy/videos/{videoId}` 동일 엔드포인트. 외부 링크는 자산이 없어 tombstone 없이
  행을 직접 삭제하며 `midterm_live_video_links`는 FK `ON DELETE CASCADE`로 정리된다.
- 현재 사용처: `static/js/tutor_academy_videos.js` "영상 링크 등록" 카드.

### ⚠ 호환성 전용 POST /api/tutor/academy/videos/uploads

- 현재 브라우저 UI는 사용하지 않는다. 배포 전 생성된 terminal session의 cleanup/recovery 호환을 위해
  서버 모델과 endpoint만 유지한다. 신규 화면은 위 same-origin multipart endpoint만 호출한다.
- 목적: 200MiB 이하 원본 영상의 durable multipart 업로드 세션을 시작한다. 한 세션의 승인 deadline은 DB `CURRENT_TIMESTAMP(6)` 기준 생성 시각부터 정확히 180초다.
- 인증/권한: `ROLE_TUTOR` + owner guard. Academy 행을 잠근 짧은 트랜잭션에서 `DELETING`이 아닌 영상(직접 업로드 + 외부 링크) + 만료되지 않은 `INITIATED|COMPLETING` 예약을 합산하며 최대 30개다.
- Request JSON: `{ "filename": string, "declaredSizeBytes": long }`.
  - 허용 크기: `1..209715200`(inclusive).
- Response `200`: `{sessionId, serverNow, deadlineAt, partSizeBytes:20971520, parts:[{partNumber,uploadUrl}]}`.
  - `uploadUrl`은 TLS에서만 전달하는 opaque bearer capability다. bucket/key/uploadId/credential을 별도 필드로 반환하지 않고 서버 상태·로그에도 URL을 저장하지 않는다.
  - production S3는 exact key/uploadId/part number에 묶인 presigned `UploadPart`; local은 같은 DTO 계약의 same-origin `PUT` capability다.
  - servlet multipart 설정은 이 direct upload의 크기/시간 제한이 아니다. 배포 IAM/CORS/lifecycle 계약은
    `docs/AWS_DEPLOYMENT.md`를 따른다.

### ⚠ 호환성 전용 PUT /api/tutor/academy/videos/uploads/{sessionId}/parts/{partNumber} (local profile)

- 목적: 로컬 개발에서 production S3 `UploadPart`와 같은 역할을 하는 bounded part endpoint.
- Request: `application/octet-stream`, initiate가 반환한 opaque URL 그대로 사용한다.
- Response: `204`, `ETag` header. 중간 part는 정확히 20MiB, 마지막 part는 선언 크기의 나머지 이하다.

### ⚠ 호환성 전용 POST /api/tutor/academy/videos/uploads/{sessionId}/complete

- Request JSON: `{ "parts": [{"partNumber":1,"eTag":"...", "checksum":null}, ...] }`. part 번호는 1부터 연속·오름차순이어야 한다. storage key/upload ID는 받지 않는다.
- 완료는 persisted manifest/hash/finalization token을 사용하는 3단계 saga다.
  1. 짧은 Phase A 트랜잭션에서 `INITIATED → COMPLETING`.
  2. 트랜잭션 밖에서 multipart complete/HeadObject, 한 개 permit의 private temp materialization, ffprobe 검증을 수행한다.
  3. DB `now <= deadlineAt`일 때만 한 트랜잭션에서 `READY+RAW` 영상, unique `PENDING` 최적화 job, session `ACCEPTED`를 함께 commit한다.
- raw 제한: 실제 저장소 크기와 streaming counter 모두 `<=209715200`; duration `<=120000ms`; MP4/MOV/WebM/Matroska 영상 트랙 및 허용 codec만 승인한다.
- idempotency/recovery:
  - `ACCEPTED` replay는 deadline 뒤에도 `200`과 같은 `acceptedVideoId`.
  - 같은 manifest의 `COMPLETING` client replay는 외부 작업/claim 없이 `202 + Retry-After`; due retry는 recovery worker만 claim한다.
  - 다른 manifest는 `409`; permanent validation은 `422`; transient는 `503 + Retry-After`; deadline 초과는 `408`.
  - multipart 완료 직전/직후 crash는 persisted manifest와 deterministic staged object로 재개한다.

### ⚠ 호환성 전용 DELETE /api/tutor/academy/videos/uploads/{sessionId}

- `INITIATED|COMPLETING → ABORTED` terminal CAS, token invalidation, multipart/staged cleanup-outbox insert를 한 짧은 트랜잭션에서 수행하고 `204`.
- abort가 먼저 commit되면 late Phase C는 영상/job을 만들지 못한다. Phase C가 먼저 `ACCEPTED`를 commit하면 abort는 상태를 덮지 않고 `200` accepted outcome을 반환한다. 반복 abort는 멱등이다.
- 활성 materialization/ffprobe는 local signal과 최대 2초 DB token poll로 협력 취소된다.

### ⚠ 호환성 전용 GET /api/tutor/academy/videos/uploads/{sessionId}

- 목적: 새로고침 뒤 업로드 결과를 안전하게 복구한다. owner guard를 통과한 세션의
  `{sessionId,state,acceptedVideoId,errorCode,retryAfterSeconds,serverNow,deadlineAt}`만 반환한다.
- storage key/upload ID/part capability는 반환하지 않는다. 브라우저는 저장해 둔 ordered ETag manifest만
  재전송하며, `serverNow/deadlineAt`은 클라이언트의 monotonic 180초 budget을 단축하는 데만 사용한다.
- `ACCEPTED`이면 즉시 Ready 목록으로 복구하고, `COMPLETING`이면 같은 manifest로 complete를 replay한다.

### ✅ GET /api/tutor/academy/videos

- 목적: 강사 대시보드 목록. 본인 Academy의 `READY` 영상만 최신순으로 반환한다. 내부 tombstone인 `DELETING`과 migration-only `UNAVAILABLE`은 query 단계에서 제외한다. 최적화 job의 `FAILED`는 영상 lifecycle이 READY/RAW인 한 목록과 공개 재생에서 유지된다.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Response: `200` `AcademyVideoResponse[]`. `availabilityStatus=READY`는 즉시 사용 가능함을 뜻하고,
  `optimizationStatus=PENDING|PROCESSING|FAILED|...`는 강사 화면에만 최적화 진행 상태로 표시한다.
  thumbnail이 아직 없으면 `thumbnailUrl=null`이며 UI는 깨진 이미지 대신 중립 placeholder를 표시한다.

### ✅ DELETE /api/tutor/academy/videos/{videoId}

- 목적: 되돌릴 수 없는 **비동기 물리 삭제 요청**. 응답 트랜잭션은 video를 `DELETING`으로 tombstone하고 알려진 raw/normalized/thumbnail/attempt/staging/multipart asset cleanup을 durable outbox에 원자적으로 넣는다.
- 인증/권한: `ROLE_TUTOR` + owner guard. 조회 조건에 academy_id가 들어가 타 학원 영상은 `404`.
- 삭제 순서: video 잠금 → `DELETING`/source version 증가 + job 취소/신호 + atomic dedupe outbox insert → commit 즉시 tutor/public 목록 제외 → worker가 object 부재를 확인한 cleanup만 `DONE` → 모든 row가 DONE이면 cleanup/session/job/video 순으로 FK-safe 물리 삭제. 부분 실패는 보이지 않는 `DELETING` 상태로 남아 backoff/reclaim된다. multipart abort는 `NoSuchUpload`/확정 404만 성공으로 간주하고 transient/unknown 실패는 retry한다.
- **참조 차단**: 수업상품의 대표/OT 미디어 또는 홈페이지 Builder의 히어로/원장 소개 영상으로 쓰는 영상은 `400`으로 막는다. 홈페이지 참조는 `pageSettings.builderDraft.heroVideoId`/`principalVideoId`를 확인하고 먼저 빌더에서 해제하도록 안내한다. DB FK가 `ON DELETE SET NULL`인 수업 미디어와 JSON 참조 모두 삭제 전에 차단하지 않으면 화면이 조용히 비고 파일까지 사라진다.
- Response: `204 No Content` / 없거나 타 학원 소유 `404` / 수업상품·홈페이지에서 사용 중 `400`.
- 관련 코드: `AcademyVideoController`, `AcademyVideoService`, `LessonMediaReferenceGuard`

### ✅ POST /api/tutor/academy/media/lesson-hero

- 목적: 수업상품 **대표 이미지** 업로드. `multipart/form-data`, part 이름 `image`.
- 인증/권한: `ROLE_TUTOR` + owner guard(`/api/tutor/**` 규칙이 이미 커버).
- **`lessonId`를 받지 않는다.** 편집기에서 새로 만든 수업상품은 컬렉션 저장 전까지 id가 없어, id를 요구하면 "먼저 저장하세요" 동선이 강제된다. 소유는 학원 단위로 판정하고 수업상품과의 연결은 컬렉션 저장이 맡는다.
- 처리: 바이트 상한(`UploadSizeGuard`, `app.upload.max-document-bytes` 20MB) → 픽셀 상한(`app.image.max-decoded-pixels`) → `ImageSanitizer`로 최장변 1600px 정규화. **애니메이션 GIF(프레임 2장 이상)는 프레임을 보존한 GIF로, 그 외는 JPEG로** 재인코딩한다. HEIC처럼 `ImageIO`가 못 읽는 포맷은 ffmpeg로 PNG를 거쳐 같은 sanitizer에 넣는다.
- 동시 디코딩은 `app.image.max-concurrent-decodes`로 제한한다(영상 세마포어와 공유하지 않는다 — 공유하면 영상 변환 중 모든 이미지 업로드가 503이 된다).
- Response: `200` `{ "imageKey": "..." }` / 용량·해상도 초과 `400` / 동시 처리 상한 초과 `503`.
- 비고: **DELETE 엔드포인트를 두지 않는다.** 저장 전 이미지는 DB row가 없어 클라이언트가 준 key 문자열 외에 인가 근거가 없다. 고아 정리는 후속 GC 잡의 몫이다.

### ✅ GET /api/academies/{slug}/lessons/{lessonId}/hero-image

- 목적: 공개 수업상품 히어로 이미지 (`Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`).
- 인증/권한: 없음(permitAll). 공개 Academy(`PUBLISHED && visible=true`)만.
- 비고: 응답 DTO(`fromPublic`)에 storage key를 싣지 않으므로 **이 경로가 유일한 공개 노출 지점**이다.
- Response: `200` bytes / 비공개·없음·미등록 `404`.

### ✅ GET /api/tutor/academy/lessons/{lessonId}/hero-image

- 목적: 강사 **빌더 프리뷰**용 히어로 이미지 (`Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`).
- 인증/권한: `ROLE_TUTOR` + owner guard.
- 비고: 빌더는 아직 발행되지 않은(DRAFT) Academy도 렌더하는데 공개 경로는 발행 상태를 요구해 `404`가 난다. 그래서 소유자 전용 경로를 따로 둔다.
- Response: `200` bytes / 타 학원·없음·미등록 `404`.

### ✅ POST /api/tutor/academy/media/hero/{slot}

- 목적: Academy 단일 이미지 슬롯 업로드. `slot` = `principal` | `textbook1` | `textbook2` | `textbook3` | `schedule`.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request: `multipart/form-data`, part 이름 `image`.
- 비고: 수업상품 대표 이미지와 달리 대상이 Academy 하나로 고정이라 **업로드가 곧 저장**이다(key를 돌려주지 않는다). 저장 위치는 `academies.principal_photo_storage_key` / `textbook_cover1~3_storage_key`(V940) / `schedule_image_storage_key`(V948).
- `schedule` 슬롯은 히어로가 아니라 `sec-lessons` 하단 개설 시간표(`.lesson-schedule-inner`)의 달력 이미지이며, **이 섹션의 유일한 본문**이다. 원래 수업상품에서 파생하던 행 목록(`.schedule-list`)이 달력형 시간표로 보이지 않는다는 이유로 걷어냈고, 시간표는 원장이 올린 달력 이미지로만 낸다. 슬롯별로 endpoint를 복제하지 않기 위해 히어로와 같은 경로를 쓴다(enum `HeroMediaSlot.SCHEDULE`). 공개 페이지는 이미지가 없으면 **섹션 자체를 렌더하지 않고**(수업상품이 있어도 마찬가지 — 본문 없는 머리말만 남기지 않는다), 빌더는 비어 있어도 업로드용 placeholder를 그린다. 시간표 공개 토글(`is-schedule-hidden`)을 끄면 달력이 숨는다.
- Response: `204 No Content` / 빈 파일·허용되지 않은 slot `400` / 동시 디코드 한도 초과 `503` / **Academy 미존재 `404`**.
- ⚠ **발행 전에는 호출하면 안 된다.** `requireMyAcademy`가 Academy row를 요구하므로 발행 전 호출은
  본문 없는 `404`가 된다. 빌더(`academy_builder.js`)는 로고와 같은 방식으로 파일을 로컬에 보류했다가
  발행 성공 직후(`flushPendingHeroMedia`) 이 endpoint로 올린다. 미리보기는 그동안 `objectURL`을 쓴다.
- 관련 코드: `AcademyVideoController.uploadHeroMedia`, `AcademyMediaService.uploadHeroMedia`

### ✅ DELETE /api/tutor/academy/media/hero/{slot}

- 목적: 이미지 슬롯 비우기(`slot` 값은 업로드와 동일). 시안 placeholder로 되돌아간다. `schedule`이면 공개 페이지에서 개설 시간표 섹션이 통째로 사라진다(본문이 달력뿐이라 대체할 내용이 없다).
- 인증/권한: `ROLE_TUTOR` + owner guard.
- 비고: storage의 실제 파일은 지우지 않고 key만 끊는다(로고 교체와 동일). 발행본이 캐시된 URL을 들고 있을 수 있어 즉시 물리 삭제하지 않는다.
- Response: `204 No Content`.

### ✅ GET /api/academies/{slug}/hero-media/{slot}

- 목적: 공개 이미지 슬롯 바이트 (`Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`). `slot` = `principal` | `textbook1~3` | `schedule`.
- 인증/권한: 없음(permitAll). 공개 Academy(`PUBLISHED && visible=true`)만.
- 비고: `AcademyResponse`에 storage key를 싣지 않으므로 **이 경로가 유일한 공개 노출 지점**이다. 어떤 슬롯이 채워졌는지는 `AcademyResponse.heroMediaSlots`(key 목록)로 알려준다.
- Response: `200` bytes / 비공개·없음·미등록 `404`.

### ✅ GET /api/tutor/academy/hero-media/{slot}

- 목적: 강사 **빌더 프리뷰**용 이미지 슬롯 바이트 (`Content-Type`은 저장 포맷에 따라 `image/jpeg` / `image/gif` / `image/webp`). `slot` = `principal` | `textbook1~3` | `schedule`.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- 비고: 빌더는 미발행(DRAFT) Academy도 렌더하는데 공개 경로는 발행 상태를 요구해 `404`가 난다. 수업상품 히어로 이미지와 같은 이유의 쌍둥이 경로다. 빌더는 캐시버스팅용 `?v={heroMediaVersion}`을 붙인다.
- Response: `200` bytes / 미등록 `404`.

### ✅ GET /api/academies/{slug}/videos

- 목적: 공개 페이지 `sec-videos` 섹션 렌더용. `READY` 영상만 최신순 최대 10개. 빈 배열이면 화면은 섹션을 숨기지 않고 준비중 placeholder를 그린다.
- 인증/권한: 없음(permitAll). 공개 Academy(`PUBLISHED && visible=true`)만.
- Response: `200` `AcademyVideoResponse[]` / 비공개·없음 `404`.
- 비고: `AcademyResponse`에 싣지 않고 별도 endpoint로 둔다 — 목록 API(`listPublic`)까지 영상이 딸려가는 N+1을 피하기 위해서다.

### ✅ GET /api/academies/{slug}/videos/{videoId}/stream

- 목적: 현재 활성 소스(RAW 또는 정규화 MP4)를 progressive streaming으로 전달한다.
- 인증/권한: 없음(permitAll). 공개 Academy의 `READY` 영상만.
- `Content-Type`은 활성 소스에 저장된 canonical media type을 사용한다. RAW WebM/MOV는 각각 `video/webm`/`video/quicktime`, 정규화본은 `video/mp4`다.
- local profile은 단일 `Range`를 지원한다. 정상 범위는 `206` + `Content-Range`, 범위 없음은 bounded streaming `200`, 만족 불가능하거나 다중 범위는 `416` + `Content-Range: bytes */{length}`다. 어떤 경우에도 전체 영상을 JVM `byte[]`로 만들지 않는다. `200`/`206` 응답은 `Cache-Control: private, max-age=604800, immutable`(정규화본은 videoId 당 불변).
- S3 profile은 `302`로 TTL 1시간 이하(기본 15분)의 opaque signed GET에 연결한다. `302`는 그 TTL(-60s 안전마진) 안에서 `Cache-Control: private, max-age`로 재사용되고 signed target은 `private, max-age=604800, immutable`이다 — `<video preload="metadata">`로 받은 앞부분을 나중의 첫 클릭 재생이 그대로 재사용해 "첫 영상 첫 클릭 버퍼링"을 없앤다. 브라우저의 `Range`는 redirect target으로 전달된다. TTL은 새 요청 인가 시한이며 이미 S3가 수락한 전송의 중단 시한이 아니다.
- Response: local `200|206|416`, S3 `302`, 권한/상태/소스 누락 `404`.

### ✅ HEAD /api/academies/{slug}/videos/{videoId}/stream

- 목적: GET과 같은 공개 Academy/`READY` 인가를 적용해 활성 소스의 `Content-Type`, `Content-Length`, `Accept-Ranges: bytes`만 반환한다.
- 인증/권한: 없음(permitAll).
- Response: `200` + `Cache-Control: no-store`, body 없음, S3 profile에서도 redirect 없음. 비공개·삭제·누락은 `404`.

### ✅ GET /api/academies/{slug}/videos/{videoId}/thumbnail

- 목적: poster 썸네일 (`Content-Type: image/jpeg`).
- 인증/권한: 없음(permitAll). `READY` 영상만.
- Response: `200` bytes / `404`.

### ✅ POST /api/tutor/academy/lessons

- 목적: 기존 클라이언트 호환용 단일 수업 상품 생성. 전용 편집기는 collection endpoint를 사용한다.
- 인증/권한: `ROLE_TUTOR` + owner guard.
- Request: `LessonUpsertRequest` (`name`, `description`, `lessonType`, `targetGroup`, `priceAmount`, `priceUnit`, `priceSummary`, `maxCapacity`, `scheduleType`, `weeklyCount`, `weekdays`, `startDate`, `endDate`, `durationWeeks`, `durationMonths`, `scheduleSummary`, `status`). 공개 페이지 문구/theme/creation key는 받지 않는다.
- Response: 성공 `200 OK`, `LessonResponse`.
- 관련 코드: `AcademyController.createLesson`, `LessonProductService.create`

### ✅ PUT /api/tutor/academy/lessons/{lessonId}

- 목적: 기존 클라이언트 호환용 단일 수업 상품 수정. 공개 페이지 문구/theme은 보존한다.
- 인증/권한: `ROLE_TUTOR` + lesson.academy owner guard.
- Request: `POST /api/tutor/academy/lessons`와 동일한 `LessonUpsertRequest`.
- Response: 성공 `200 OK`, `LessonResponse`. 실패 404/403 가능.
- 동시성: 대상 row를 `PESSIMISTIC_WRITE`로 잠그고 실제 작성 상태가 바뀌면 `authoringRevision`을 한 번 증가시킨다.
- 관련 코드: `AcademyController.updateLesson`, `LessonProductService.update`

### ✅ DELETE /api/tutor/academy/lessons/{lessonId}

- 목적: TUTOR 관리 목록에서 수업을 숨긴다. 물리 삭제가 아니라 `status=HIDDEN`, `isVisible=false`로 변경한다.
- 인증/권한: `ROLE_TUTOR` + lesson.academy owner guard.
- Response: 성공 `204 No Content`; 승인 신청/수강생이 있으면 `409 Conflict`; 실패 404/403 가능.
- 동시성: 대상 row를 `PESSIMISTIC_WRITE`로 잠그며, 처음 HIDDEN으로 전환할 때 `authoringRevision`을 한 번 증가시킨다.
- 관련 코드: `AcademyController.deleteLesson`, `LessonProductService.hide`


## 11. Lesson Search / Application API

학생이 공개 수업을 검색하고 신청/취소하는 marketplace MVP API. 강사 승인/거절, 결제, 채팅, 알림은 없다.

### ✅ GET /api/lessons/search

- 목적: 공개 Academy의 공개 수업 상품 검색.
- 현재 사용처:
  - `templates/lesson_search.html`
  - `static/js/lesson_search.js`
- 향후 사용 가능성:
  - React 학생 marketplace 검색 화면
- 인증/권한: public. `AcademyStatus.PUBLISHED` + `LessonStatus.PUBLISHED`만 반환.
- Request query parameters: optional
  - `subjectId` number
  - `targetGroup` enum — `lesson_products.target_group` 기준으로 필터링
  - `city` string
  - `district` string
  - `lessonType` enum
  - `weekday` enum
  - `maxPrice` number
  - `weeklyCount` number
- Response: `200 OK`, `LessonSearchResponse[]`
  - `lessonId`, `lessonName`, `academyName`, `academySlug`, `lessonType`, `targetGroup`, `priceAmount`, `priceUnit`, `priceSummary`, `maxCapacity`, `currentCount`, `remainingCapacity`, `full`, `scheduleType`, `weeklyCount`, `weekdays`, `scheduleSummary`
- 관련 코드:
  - `LessonApplicationController.searchLessons`
  - `LessonApplicationService.search`
  - `LessonProductRepository.searchPublishedLessons`

### ✅ POST /api/lesson-applications

- 목적: STUDENT가 수업을 신청한다. 정원 여유가 있으면 즉시 `APPROVED`가 되고 conversation이 연결된다.
- 현재 사용처:
  - `static/js/lesson_search.js` 신청 버튼 (conversation 선택 모달 → POST)
  - `static/js/academy_public.js` 수업 카드 신청 버튼
- 향후 사용 가능성:
  - React lesson detail 신청 CTA
- 인증/권한: 인증 필요. STUDENT role만 허용.
- Request body:
  ```json
  {
    "lessonId": 123,
    "conversationId": 456
  }
  ```
  - `conversationId`: optional (nullable). null이면 "{수업명} 학습 대화" 제목으로 conversation 자동 생성. 값이 있으면 본인 소유 conversation 검증 후 연결 (다른 학생 conversation 지정 시 403).
- Response:
  - 성공: `200 OK`, `LessonApplicationResponse`
  - 실패: 401/403, 404 공개 수업 없음, 409 이미 신청, 400/500 text 가능
- 동작:
  - `lesson_products` row pessimistic lock
  - 정원 검증 후 `current_count += 1`
  - conversation 해석: `conversationId` null → 자동 생성, non-null → 소유 검증 후 연결
  - 기존 `CANCELED` row가 있으면 재활성화 + conversation 연결
  - 기존 `APPROVED` row가 있으면 중복 신청 거부
- 관련 코드:
  - `LessonApplicationController.apply`
  - `LessonApplicationService.apply`
  - `LessonApplyRequest`

### ✅ GET /api/lesson-applications/me

- 목적: STUDENT 본인의 수업 신청 목록 조회.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`의 "신청한 수업" 섹션
- 향후 사용 가능성:
  - React student dashboard
- 인증/권한: 인증 필요. STUDENT role만 허용.
- Response: `200 OK`, `LessonApplicationResponse[]`
- 관련 코드: `LessonApplicationController.listMine`, `LessonApplicationService.listMine`

### ✅ DELETE /api/lesson-applications/{applicationId}

- 목적: STUDENT가 본인 수업 신청을 취소한다.
- 현재 사용처:
  - `static/js/stdt_dashboard.js` 신청 취소 버튼
- 향후 사용 가능성:
  - React student application management
- 인증/권한: 인증 필요. 신청 owner STUDENT만 허용.
- Response:
  - 성공: `200 OK`, 취소된 `LessonApplicationResponse`
  - 실패: 401/403/404/409 가능
- 동작:
  - `status=CANCELED`, `canceled_at` 세팅
  - `lesson_products.current_count -= 1`
  - `conversation_id` 연결은 이력 보존을 위해 유지 (끊지 않음)
- 관련 코드: `LessonApplicationController.cancel`, `LessonApplicationService.cancel`

### ✅ GET /api/tutor/lesson-applications

- 목적: TUTOR가 본인 Academy/Lesson의 신청/수강생 목록을 조회한다. 승인/거절 버튼은 없다.
- 현재 사용처:
  - `static/js/tutor_academy.js` 신청/수강생 목록
- 향후 사용 가능성:
  - React tutor CRM/list page
- 인증/권한: `ROLE_TUTOR`; service는 tutor user id 기준으로 본인 Academy/Lesson 신청만 조회.
- Response: `200 OK`, `LessonApplicationResponse[]`
- 관련 코드: `LessonApplicationController.listTutorApplications`, `LessonApplicationService.listTutorApplications`

### ✅ GET /api/tutor/lessons/{lessonId}/applications

- 목적: TUTOR가 특정 수업의 APPROVED 신청자 목록과 연결된 conversation 정보를 조회한다.
- 현재 사용처:
  - 🚧 `templates/tutor/lesson_students.html` (Task #6에서 구현 예정)
- 향후 사용 가능성:
  - React tutor CRM / 수업별 수강생 관리
- 인증/권한: `ROLE_TUTOR`. 본인 소유 수업(lesson.academy.tutor == 본인)만 조회 허용, 남의 수업 lessonId → 403.
- Request:
  - Path parameters:
    - `lessonId` number — lesson id.
- Response:
  - 성공: `200 OK`, `LessonApplicationResponse[]` (APPROVED 상태만, appliedAt 내림차순)
  - 실패: 401, 403, 404
- 관련 코드 (🚧 예정):
  - `LessonApplicationController.listLessonApplications`
  - `LessonApplicationService.listLessonApplications`
  - `LessonApplicationRepository.findApprovedByLessonId`

### LessonApplicationResponse

```json
{
  "applicationId": 500,
  "lessonId": 123,
  "lessonName": "중3 영어 내신반",
  "academyId": 40,
  "academyName": "영어학원",
  "academySlug": "english-academy",
  "studentId": 30,
  "studentName": "학생",
  "status": "APPROVED",
  "appliedAt": "2026-06-01T10:00:00",
  "canceledAt": null,
  "conversationId": 77,
  "conversationTitle": "중3 영어 내신반 학습 대화"
}
```

> `conversationId` / `conversationTitle`: ✅ `LessonApplicationResponse`에 필드 추가됨. nullable — conversation 미연결 시 null.

## 12. Learning Material API

Phase 2 §5.7 — Admin/TUTOR가 학생에게 PDF 학습자료를 부여하고, 학생이 풀이 결과(이미지/PDF)를 재업로드.

### ✅ POST /api/conversations/{id}/learning-materials

- 목적: ADMIN 또는 TUTOR가 학생에게 학습자료를 부여 (한 대화 컨텍스트 안에서). PDF는 선택 — 없으면 title·description만으로 부여(V13).
- 현재 사용처:
  - `static/js/conversation_homework.js`의 [+ 숙제 부여] 모달
- 향후 사용 가능성:
  - React Admin/Tutor client
- 인증/권한: 인증 필요. `verifyAdminOrTutor()` 통과(role) + `verifyOwnsConversation(conv)` 통과(TUTOR면 `lesson_applications.status=APPROVED` 담당 학생만 — V9 이후).
- Request:
  - Path parameters:
    - `id` number — conversation id (부여 컨텍스트).
  - Multipart form fields:
    - `file` file **optional** — PDF 1개. 있으면 확장자 검사 (PDF 외 거부). **없으면(빈 파일 포함) `problem_source_id=NULL`로 부여** (V13).
    - `title` string required — 학생에게 보이는 제목 (비공백, 최대 200자).
    - `description` string optional — 안내 메시지 (최대 500자).
- Response:
  - 성공: `200 OK`. body는 `LearningMaterialResponse`(아래 공통 스키마 참고). PDF 없이 부여 시 `problemSourceId`/`problemFileName`은 `null`, `hasProblemFile=false`.
  - 실패: 400 text (비-PDF / 공백 title), 401, 403.
- 동작 변경 (PR1 / V13):
  - 부여 시 PDF가 있으면 `problem_source`(HOMEWORK_PROBLEM)를 `conversation_sources`에 링크 + `metadata` SourceSummary append → AI Chat이 해당 문제 파일에 접근 가능.
  - **PDF가 없으면(V13) source 생성/링크를 스킵하고 material만 저장한다.**
- 관련 코드:
  - `LearningMaterialController.assign`
  - `LearningMaterialService.assign`
  - `LearningMaterialService.linkSourceToConversation`

### ✅ GET /api/learning-materials

- 목적: 학생 본인이 받은 학습자료 목록. ADMIN/TUTOR는 임퍼소네이트.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`의 "받은 학습자료" 섹션
- 향후 사용 가능성:
  - React student dashboard
- 인증/권한: 인증 필요. ADMIN/TUTOR는 `targetStudentId` query로 임퍼소네이트 (TUTOR는 `lesson_applications.status=APPROVED` 담당 학생만 — V9 이후).
- Request:
  - Query parameters:
    - `targetStudentId` optional number — ADMIN/TUTOR 임퍼소네이트용.
- Response:
  - 성공: `200 OK`, `LearningMaterialResponse[]` (최신 부여순).
  - 실패: 401, 403.
- 관련 코드:
  - `LearningMaterialController.listForStudent`
  - `LearningMaterialService.listForStudent`

### ✅ GET /api/conversations/{id}/learning-materials

- 목적: 한 conversation에서 부여된 학습자료 전체 (강사/Admin이 부여 이력 확인용).
- 현재 사용처:
  - `static/js/conversation_review.js`의 "부여된 학습자료" 섹션 (TUTOR/ADMIN-as-impersonator만 노출)
- 향후 사용 가능성:
  - React conversation review page
- 인증/권한: 인증 필요. `verifyOwnsConversation`로 본인 대화/ADMIN/매핑 TUTOR만 통과.
- Request:
  - Path parameters:
    - `id` number — conversation id.
- Response:
  - 성공: `200 OK`, `LearningMaterialResponse[]` (최신 부여순).
  - 실패: 401, 403.
- 관련 코드:
  - `LearningMaterialController.listForConversation`
  - `LearningMaterialService.listForConversation`

### ✅ GET /api/learning-materials/{id}/download

- 목적: 문제 PDF 다운로드. attachment 강제.
- 현재 사용처:
  - `static/js/stdt_dashboard.js` — 학생의 "PDF 받기" 버튼
  - `static/js/conversation_review.js` — 부여 목록의 "PDF 보기" 링크
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. `verifyOwnsLearningMaterial`로 본인 학생/ADMIN/매핑 TUTOR만 통과.
- Request:
  - Path parameters:
    - `id` number — material id.
- Response:
  - 성공: `200 OK`, `Content-Type: application/pdf`, body = raw PDF bytes, headers:
    - `Cache-Control: private, no-store`
    - `Content-Disposition: attachment; filename="learning-material-{id}.pdf"; filename*=UTF-8''...`
  - 실패: 401, 403, 400 text 가능. **404 — PDF 없이 부여된 숙제(`problem_source_id=NULL`)는 내려줄 파일이 없어 NOT_FOUND** (V13). 프론트는 `hasProblemFile=false`인 카드에서 다운로드 버튼을 숨기므로 정상 흐름에선 호출되지 않는다.
- 관련 코드:
  - `LearningMaterialController.downloadProblem`
  - `LearningMaterialService.getProblemFileBundle`

### ✅ GET /api/learning-materials/{id}/view

- 목적: 문제 PDF inline 미리보기. 화면(iframe)에 띄워 보기용. `/download`와 같은 bytes·가드지만 `Content-Disposition: inline`.
- 현재 사용처:
  - `static/js/conversation_homework.js` — 숙제 카드 클릭 시 문제 PDF 뷰어 모달(`#homeworkPdfModal` iframe)
- 향후 사용 가능성:
  - React client (PDF 뷰어 임베드)
- 인증/권한: 인증 필요. `getProblemFileBundle` → `verifyOwnsLearningMaterial`로 본인 학생/ADMIN/매핑 TUTOR만 통과 (`/download`와 동일 가드).
- Request:
  - Path parameters:
    - `id` number — material id.
- Response:
  - 성공: `200 OK`, `Content-Type: application/pdf`, body = raw PDF bytes, headers:
    - `Cache-Control: private, max-age=300` — inline 미리보기 캐싱. `/download`의 `no-store`와는 **의도적으로 다름** (answer-file inline 미리보기 선례와 일치).
    - `Content-Disposition: inline; filename="learning-material-{id}.pdf"`
  - 실패: 401, 403, 400 text 가능 (가드 위반 시). **404 — PDF 없이 부여된 숙제는 NOT_FOUND** (V13, `/download`와 동일). 프론트는 `hasProblemFile=false` 카드에서 뷰어 클릭을 비활성화한다.
- 관련 코드:
  - `LearningMaterialController.viewProblem`
  - `LearningMaterialService.getProblemFileBundle` (재사용)

### ✅ POST /api/learning-materials/{id}/complete

- 목적: 학생이 풀이 결과 파일(이미지/PDF) 업로드. 이미 결과가 있으면 대치 모드(기존 row+storage 모두 정리 후 재INSERT).
- 현재 사용처:
  - `static/js/stdt_dashboard.js`의 풀이 업로드 모달
- 향후 사용 가능성:
  - React client
- 인증/권한: 인증 필요. STUDENT 본인 또는 ADMIN만 허용. **TUTOR는 차단**. `verifyOwnsLearningMaterial`(조회용 가드) 통과 후 `verifyCanSubmitAnswer` 추가 가드 적용.
- Request:
  - Path parameters:
    - `id` number — material id.
  - Multipart form fields:
    - `files` file[] required — 1개 이상. 확장자 화이트리스트: PDF / JPG / JPEG / PNG / WebP.
- Response:
  - 성공: `200 OK`, 갱신된 `LearningMaterialResponse` (status=`COMPLETED`, answers 채워짐, reviewed=false).
  - 실패: 400 text (확장자 거부 / 빈 파일 / files 누락 등), 401, 403.
- 동작 변경 (PR1):
  - 제출 시 `answer_source`(HOMEWORK_ANSWER)를 `conversation_sources`에 링크 + `metadata` SourceSummary append → AI Chat 접근 가능.
  - 재제출 시 기존 answer source를 `conversation_sources`에서 먼저 제거(FK-safe) 후 source/storage 삭제. 그 후 신규 answer source를 링크.
  - 재제출 시 `reviewed=false`, `reviewed_at=NULL`, `reviewed_by=NULL` 리셋 — 강사 확인 재필요 표시.
  - 제출 가능 role: STUDENT 본인 + ADMIN. TUTOR가 시도하면 403.
- 동작 변경 (PR2):
  - 재제출 시 기존 피드백(HOMEWORK_FEEDBACK source)도 stale로 판단해 함께 삭제 — `conversation_sources` 링크 해제 + source/storage 정리. 재제출 후 강사가 피드백을 새로 업로드해야 한다.
- 관련 코드:
  - `LearningMaterialController.complete`
  - `LearningMaterialService.complete`
  - `LearningMaterialService.verifyCanSubmitAnswer`

### ✅ GET /api/learning-materials/{id}/answers/{aid}/file

- 목적: 풀이 파일 1건의 inline 미리보기.
- 현재 사용처:
  - `static/js/stdt_dashboard.js`의 풀이 결과 chip 링크
  - `static/js/conversation_review.js`의 부여 목록 → 학생 풀이 chip 링크
- 향후 사용 가능성:
  - React preview / inline 표시
- 인증/권한: 인증 필요. `verifyOwnsLearningMaterial` + answer가 material 소속인지 검증.
- Request:
  - Path parameters:
    - `id` number — material id.
    - `aid` number — answer id (`learning_material_answers.answer_id`).
- Response:
  - 성공: `200 OK`, `Content-Type`은 원본 확장자로 추론 (`application/pdf` / `image/jpeg` / `image/png` / `image/webp` / `application/octet-stream`). body는 raw bytes.
    - `Cache-Control: private, max-age=300`
  - 실패: 401, 403, 400 text 가능.
- 관련 코드:
  - `LearningMaterialController.getAnswerFile`
  - `LearningMaterialService.getAnswerFile`

### ✅ PATCH /api/learning-materials/{id}/reviewed

- 목적: 강사/Admin이 학생 제출물을 확인(채점 완료) 상태로 토글. `reviewed=true`로 세팅하면 확인자(reviewer)와 시각이 기록된다. `reviewed=false`로 세팅하면 확인 기록이 초기화된다.
- 현재 사용처:
  - 🚧 프론트 UI는 PR2에서 구현 예정. endpoint는 구현 완료.
- 향후 사용 가능성:
  - React 강사용 숙제 확인 UI
- 인증/권한: 인증 필요. `verifyAdminOrTutor()` + `verifyOwnsLearningMaterial()`(TUTOR는 APPROVED 담당 학생만). 학생 호출 시 403.
- Request:
  - Path parameters:
    - `id` number — material id.
  - JSON body:
    ```json
    { "reviewed": true }
    ```
    - `reviewed` boolean required — `true`: 확인 완료, `false`: 확인 취소.
- Response:
  - 성공: `200 OK`, 갱신된 `LearningMaterialResponse` (reviewed/reviewedAt/reviewedByName 반영).
  - 실패: 400 text (material 없음 등), 401, 403.
- 관련 코드:
  - `LearningMaterialController.setReviewed`
  - `LearningMaterialController.ReviewedRequest`
  - `LearningMaterialService.setReviewed`

### ✅ DELETE /api/learning-materials/{id}

- 목적: 학습자료 영구 삭제. cascade로 answers + answer source/storage + material row + problem source/storage 모두 정리.
- 현재 사용처:
  - `static/js/conversation_review.js`의 [삭제] 버튼
- 향후 사용 가능성:
  - React admin/tutor management UI
- 인증/권한: 인증 필요. **ADMIN 또는 부여자(`assigned_by`) 본인만** 통과. 다른 TUTOR가 부여한 자료는 못 지움.
- Request:
  - Path parameters:
    - `id` number — material id.
- Response:
  - 성공: `204 No Content`, body 없음.
  - 실패: 401, 403 text, 400 text 가능.
- 동작 변경 (PR1):
  - 삭제 시 problem source + 모든 answer source를 `conversation_sources`에서 먼저 제거(FK-safe) 후 source/storage 삭제.
- 동작 변경 (PR2):
  - 삭제 시 모든 feedback source도 `conversation_sources`에서 링크 제거 + source/storage 삭제. cascade 순서: answers → feedbacks → material row → problem source.
- 관련 코드:
  - `LearningMaterialController.delete`
  - `LearningMaterialService.delete`
  - `LearningMaterialService.unlinkSourceFromConversation`

### ✅ POST /api/learning-materials/{id}/feedback

- 목적: 강사/Admin이 학생 풀이에 대한 피드백 PDF를 업로드(append). 기존 피드백에 이어 붙이는 방식이며, `conversation_sources`에 편입되어 AI Chat 접근 가능.
- 현재 사용처:
  - `static/js/conversation_homework.js` — 숙제 카드의 [피드백 업로드] 버튼
- 향후 사용 가능성:
  - React 강사용 숙제 피드백 UI
- 인증/권한: 인증 필요. `verifyAdminOrTutor()` + `verifyOwnsLearningMaterial()`(TUTOR는 APPROVED 담당 학생만). 학생 호출 시 403.
- Request:
  - Path parameters:
    - `id` number — material id.
  - Multipart form fields:
    - `files` file[] required — 1개 이상. **PDF만** 허용 (확장자 검사).
- Response:
  - 성공: `200 OK`, 갱신된 `LearningMaterialResponse` (feedbacks 채워짐).
  - 실패: 400 text (비-PDF / 빈 파일 / files 누락 등), 401, 403.
- 비고:
  - 학생이 풀이를 재제출(`POST /api/learning-materials/{id}/complete`)하면 기존 피드백이 모두 삭제된다(stale 피드백 정리). 재제출 후 강사가 다시 업로드해야 한다.
- 관련 코드:
  - `LearningMaterialController.addFeedback`
  - `LearningMaterialService.addFeedback`

### ✅ GET /api/learning-materials/{id}/feedback/{fid}/file

- 목적: 피드백 PDF 1건의 inline 미리보기. `GET /api/learning-materials/{id}/answers/{aid}/file`과 동일 패턴.
- 현재 사용처:
  - `static/js/conversation_homework.js` — 숙제 카드의 피드백 chip 링크
  - `static/js/stdt_dashboard.js` — 학생 대시보드의 받은 피드백 chip 링크
- 향후 사용 가능성:
  - React preview / inline 표시
- 인증/권한: 인증 필요. `loadOwned`(`verifyOwnsLearningMaterial`) — 학생 본인 / ADMIN / 매핑 TUTOR 통과. feedback이 해당 material 소속인지 추가 검증.
- Request:
  - Path parameters:
    - `id` number — material id.
    - `fid` number — feedback id (`learning_material_feedbacks.feedback_id`).
- Response:
  - 성공: `200 OK`, `Content-Type: application/pdf`. body는 raw bytes.
    - `Cache-Control: private, max-age=300`
  - 실패: 401, 403, 400 text (feedback이 다른 material 소속이거나 미존재 시).
- 관련 코드:
  - `LearningMaterialController.getFeedbackFile`
  - `LearningMaterialService.getFeedbackFile`

### ✅ DELETE /api/learning-materials/{id}/feedback/{fid}

- 목적: 피드백 1건 삭제. feedback source를 `conversation_sources`에서 링크 해제 + source/storage 정리 후 갱신된 material 반환.
- 현재 사용처:
  - `static/js/conversation_homework.js` — 숙제 카드의 피드백 chip [✕] 버튼 (강사/Admin만 노출)
- 향후 사용 가능성:
  - React 강사용 피드백 관리 UI
- 인증/권한: 인증 필요. `verifyAdminOrTutor()` + `verifyOwnsLearningMaterial()`(TUTOR는 APPROVED 담당 학생만). 학생 호출 시 403.
- Request:
  - Path parameters:
    - `id` number — material id.
    - `fid` number — feedback id (`learning_material_feedbacks.feedback_id`).
- Response:
  - 성공: `200 OK`, 갱신된 `LearningMaterialResponse` (삭제된 feedback 제외).
  - 실패: 401, 403, 400 text (feedback 미존재 또는 다른 material 소속 시).
- 관련 코드:
  - `LearningMaterialController.deleteFeedback`
  - `LearningMaterialService.deleteFeedback`

### 공통 스키마 — LearningMaterialResponse

```json
{
  "materialId": 1,
  "conversationId": 1,
  "problemSourceId": 12,
  "problemFileName": "Unit3-grammar.pdf",
  "hasProblemFile": true,
  "title": "Unit 3 — Grammar 보충",
  "description": "내일까지 풀어와",
  "status": "ASSIGNED",
  "assignedAt": "2026-05-24T14:00:00",
  "completedAt": null,
  "assignedByUserId": 10,
  "assignedByName": "강사",
  "studentId": 200,
  "studentName": "홍길동",
  "reviewed": false,
  "reviewedAt": null,
  "reviewedByName": null,
  "answers": [
    {
      "answerId": 4001,
      "sourceId": 9000,
      "originalFileName": "page1.jpg",
      "orderNum": 1
    }
  ],
  "feedbacks": [
    {
      "feedbackId": 5001,
      "sourceId": 9001,
      "originalFileName": "feedback1.pdf",
      "orderNum": 1
    }
  ]
}
```

- `status`: `ASSIGNED` 또는 `COMPLETED`.
- `problemSourceId` / `problemFileName`: 문제 PDF source id / 원본 파일명. **PDF 없이 부여된 숙제(V13)는 둘 다 `null`.**
- `hasProblemFile`: 문제 PDF 존재 여부 (V13). `false`면 `/view`·`/download`가 404이므로 프론트는 뷰어/다운로드 버튼을 숨기고 `description`을 본문에 노출한다.
- `answers`: 학생이 풀이 결과를 올리지 않은 상태면 `[]`. COMPLETED면 1개 이상.
- `feedbacks`: 강사/Admin이 업로드한 피드백 PDF 목록. 없으면 `[]`. 학생이 재제출하면 자동으로 `[]`로 리셋.
- `reviewed`: 강사/Admin 확인 여부. 기본 `false`. 재제출 시 자동으로 `false` 리셋.
- `reviewedAt`: 확인 시각 (nullable). `reviewed=false`이면 `null`.
- `reviewedByName`: 확인한 사용자 이름 (nullable). `reviewed=false`이면 `null`.

## 13. 프론트 잔존 호출 / 현재 미구현 또는 확인 필요 API

다음 endpoint는 template/static JS에서 호출 흔적이 있으나 현재 Controller 목록에는 없다. 코드 변경 시 삭제/구현/문서화 중 하나로 정리해야 한다.

### 🚧 GET /api/users/tutors/{userId}/students

- 현재 사용처: `templates/tutor_dashboard.html` legacy 화면
- 상태: Controller 없음. 신규 TUTOR 플로우는 `/api/tutor/academy/**`를 사용한다. 학생-강사 매핑/학생 목록은 후속 단계.

### 🚧 POST /api/questions/import-vision

- 현재 사용처: `templates/admin/dashboard.html`
- 상태: Controller 없음. Admin 시험/문항 import legacy UI 호출로 보이며 현재 MVP 1차 범위와 불일치 가능.

## 14. 조사 근거

### Controller 목록

- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/admin/controller/AdminController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/conversation/controller/ConversationController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/dashboard/controller/DashboardController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/source/controller/SourceController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/subject/controller/SubjectController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/user/controller/AuthController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/user/controller/UserController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/controller/AcademyController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/learningmaterial/controller/LearningMaterialController.java`
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/lessonapplication/controller/LessonApplicationController.java`

### 주요 프론트 사용처

- `src/main/resources/templates/home.html`
- `src/main/resources/templates/register.html`
- `src/main/resources/templates/admin/login.html`
- `src/main/resources/templates/admin/students.html`
- `src/main/resources/templates/admin/dashboard.html`
- `src/main/resources/templates/tutor_dashboard.html`
- `src/main/resources/templates/tutor_academy.html`
- `src/main/resources/templates/tutor_academy_setup.html`
- `src/main/resources/templates/academy_public.html`
- `src/main/resources/static/js/tutor_academy.js`
- `src/main/resources/static/js/tutor_academy_setup.js`
- `src/main/resources/static/js/academy_public.js`
- `src/main/resources/static/js/stdt_dashboard.js`
- `src/main/resources/static/js/conversation.js`
- `src/main/resources/static/js/conversation_upload.js`
- `src/main/resources/static/js/conversation_dashboard.js`
- `src/main/resources/static/js/conversation_materials.js`
- `src/main/resources/static/js/conversation_report.js`
- `src/main/resources/static/js/exam_review.js`
- `src/main/resources/static/js/api.js`

### 사용한 확인 명령

```bash
find src/main/java -name '*Controller.java' -print | sort
rg -n '@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)' src/main/java src/main/resources/templates src/main/resources/static
rg -n 'fetch\(|axios|\.href|form|action=|/api/' src/main/resources/templates src/main/resources/static -g '*.js' -g '*.html'
```


## Academy Blog API

### ✅ GET /api/tutor/academy/blog-posts
- 목적: 강사 본인 Academy의 블로그 글 목록 조회
- 현재 사용처: Thymeleaf SSR / JavaScript fetch
- 인증/권한: ROLE_TUTOR, 본인 Academy만
- Response: `AcademyBlogPostResponse[]`

### ✅ POST /api/tutor/academy/blog-posts
- 목적: Academy 블로그 글 생성
- 인증/권한: ROLE_TUTOR, 본인 Academy만
- Request JSON: `title`, `slug`, `summary`, `contentJson`, `contentHtml`, `metaTitle`, `metaDescription`, `status(DRAFT|PUBLISHED)`, `visible`, `sourceIds[]`
- Response: 생성된 `AcademyBlogPostResponse`

### ✅ GET /api/tutor/academy/blog-posts/{postId}
- 목적: 강사용 글 수정 화면 데이터 조회
- 인증/권한: ROLE_TUTOR, 본인 Academy 글만
- Response: `AcademyBlogPostResponse`

### ✅ PUT /api/tutor/academy/blog-posts/{postId}
- 목적: Academy 블로그 글 수정
- 인증/권한: ROLE_TUTOR, 본인 Academy 글만
- Request JSON: 생성 API와 동일
- Response: 수정된 `AcademyBlogPostResponse`

### ✅ DELETE /api/tutor/academy/blog-posts/{postId}
- 목적: Academy 블로그 글 삭제
- 인증/권한: ROLE_TUTOR, 본인 Academy 글만
- Response: 204 No Content

### ✅ POST /api/tutor/academy/blog-sources/images
- 목적: Tiptap 본문 이미지 업로드
- 인증/권한: ROLE_TUTOR, 본인 Academy만
- Request: multipart `image`
- Response: `AcademyBlogSourceResponse` (`sourceId`, `fileUrl`, `contentType`, `fileSize`)

### ✅ GET /api/academy-posts
- 목적: 공개 Academy 블로그 글 모아보기 목록 조회
- 인증/권한: public. Academy와 글이 모두 공개 상태여야 함
- Query: `keyword` optional, `sort=latest|recommended` (기본 latest)
- 추천순: DB 기반 최근 `app.academy-blog.recommendation-window-days`일 좋아요 수 DESC, 동률 최신순
- Response: 공개 `AcademyBlogPostResponse[]` (`likeCount`, `recentLikeCount`, `likedByMe`, `likeAllowed`, `likeAllowedUntil` 포함)

### ✅ POST /api/academy-posts/{postId}/like
- 목적: 공개 Academy 블로그 글 좋아요/추천 토글
- 인증/권한: 로그인 사용자. 사용자별 글 1회 제한, 다시 누르면 취소
- 정책: 글 발행 후 `app.academy-blog.like-allowed-days`일(기본 30일)까지만 신규 좋아요/취소 가능
- Response: 갱신된 `AcademyBlogPostResponse`

### ✅ GET /api/academies/{academySlug}/blog-posts
- 목적: 공개 Academy 블로그 글 목록 조회
- 인증/권한: public. Academy와 글이 모두 공개 상태여야 함
- Response: 공개 `AcademyBlogPostResponse[]`

### ✅ GET /api/academies/{academySlug}/blog-posts/{postSlug}
- 목적: 공개 Academy 블로그 글 상세 조회
- 인증/권한: public. Academy와 글이 모두 공개 상태여야 함
- Response: 공개 `AcademyBlogPostResponse`

### ✅ GET /api/academies/{academySlug}/blog-sources/{sourceId}/file
- 목적: 블로그 본문 이미지 inline 조회
- 인증/권한: public 글에 연결된 이미지는 public, 미발행/작성 중 이미지는 소유 TUTOR만
- Response: `image/jpeg` / `image/gif` / `image/webp` (저장 포맷에 따름) bytes. Content-Type은 업로드 시 `academy_blog_sources.content_type`에 저장된 값을 그대로 쓴다.

### ✅ GET /sitemap.xml / GET /robots.txt
- 목적: 공개 Academy/블로그 글의 검색엔진 접근성 제공
- 인증/권한: public

## 15. Lesson Community / Q&A API

Lesson 단위 커뮤니티 MVP. 접근자는 해당 lesson 소유 TUTOR, 해당 lesson APPROVED STUDENT, ADMIN이다.

### ✅ GET /lessons/{lessonId}/community
- 목적: lesson community Thymeleaf 화면.
- View: `lesson_community`
- JS: `static/js/lesson_community.js`

### ✅ GET /api/lessons/{lessonId}/community/context
- 목적: 화면 초기 컨텍스트(lesson/academy/role/권한) 조회.
- 인증/권한: lesson participant only.

### ✅ GET /api/lessons/{lessonId}/community/categories
- 목적: lesson community 커스텀 탭 목록. DB 호환상 endpoint/table 이름은 categories를 유지하되 UI 용어는 “탭”으로 통일한다.
- 인증/권한: lesson participant only.

### ✅ POST /api/lessons/{lessonId}/community/categories
- 목적: 강사/Admin이 lesson community 커스텀 탭 생성.
- 인증/권한: lesson 소유 TUTOR 또는 ADMIN.
- Request JSON: `{ "name": "숙제방", "sortOrder": 0 }`
- 기본 탭명(`전체글`, `일반글`, `Q&A`, `공지`)은 커스텀 탭명으로 사용할 수 없다.

### ✅ PUT /api/lessons/{lessonId}/community/categories/{categoryId}
- 목적: 강사/Admin이 커스텀 탭 이름/정렬 수정.
- 인증/권한: lesson 소유 TUTOR 또는 ADMIN.

### ✅ DELETE /api/lessons/{lessonId}/community/categories/{categoryId}
- 목적: 강사/Admin이 커스텀 탭 하드 삭제.
- 삭제 전 해당 탭의 게시글은 일반글(`categoryId=null`)로 이동한다.

### ✅ GET /api/lessons/{lessonId}/community/posts?tab=&categoryId=
- 목적: 게시글 목록 + 최신 공지 고정 데이터 조회.
- `tab`: `all`, `general`, `qna`, `notice`, `custom`
- 커스텀 탭 조회 시 `tab=custom&categoryId={id}`를 사용한다.
- Response JSON: `{ "pinnedNotice": LessonCommunityPostSummaryResponse|null, "posts": [...] }`
- 최신 공지는 `notice` 탭을 제외한 모든 탭 상단에 1건 고정되며, 목록과 중복 반환하지 않는다.
- Q&A 비공개 글은 작성 학생 + lesson 소유 강사 + ADMIN에게만 반환한다.

### ✅ POST /api/lessons/{lessonId}/community/posts
- 목적: 게시글 생성.
- Content-Type: `multipart/form-data`
- Fields: `postType`, `title`, `content`, `categoryId`, `qnaPublic`, `files` 또는 `files[]`
- 제한: title 150자, content 5000자, attachments 최대 10개/파일당 10MB 이하.
- NOTICE는 lesson 소유 TUTOR/ADMIN만 생성 가능.

### ✅ GET /api/community/posts/{postId}
- 목적: 게시글 상세 + 첨부 + 댓글 조회.
- 인증/권한: 부모 lesson participant + Q&A private 정책.

### ✅ PUT /api/community/posts/{postId}
- 목적: 작성자가 자기 글 수정.
- Request JSON: `LessonCommunityPostRequest`
- 첨부 수정은 MVP 범위에서 제외.

### ✅ DELETE /api/community/posts/{postId}
- 목적: 게시글 숨김 처리(`is_visible=false`).
- 권한: 작성자, lesson 소유 TUTOR, ADMIN.

### ✅ POST /api/community/posts/{postId}/comments
- 목적: 댓글 생성.
- Content-Type: `multipart/form-data`
- Fields: `content`
- 제한: content 5000자. 댓글 첨부는 MVP 범위에서 비활성화한다.

### ✅ PUT /api/community/comments/{commentId}
- 목적: 작성자가 자기 댓글 수정.

### ✅ DELETE /api/community/comments/{commentId}
- 목적: 댓글 숨김 처리(`is_visible=false`).
- 권한: 작성자, lesson 소유 TUTOR, ADMIN.

### ✅ GET /api/community/attachments/{attachmentId}/file
- 목적: community 첨부파일 조회. PDF/일반 이미지(svg 제외)는 inline, 그 외 타입은 `application/octet-stream` + attachment로 내려준다.
- 인증/권한: 부모 글을 읽을 수 있는 사용자. 숨김 댓글의 첨부는 직접 URL 접근도 404.

### ✅ GET /admin/lesson-communities
- 목적: Admin이 모든 수업 커뮤니티를 검색하고 접근하는 Thymeleaf 화면.
- View: `admin/lesson_communities`
- JS: `static/js/admin_lesson_communities.js`

### ✅ GET /api/admin/lesson-communities?keyword=
- 목적: Admin 수업 커뮤니티 검색. 결과 단위는 lesson community이다.
- 인증/권한: ADMIN only.
- 검색 대상: 수업명, 학원명, 강사명, visible 게시글 제목/본문/작성자명.
- 응답: lesson/academy/tutor 식별자와 공개 상태, 게시글 수, 댓글 수, 최근 게시글 요약.
- MVP 제한: 최대 100개 표시, 숨김/복구/삭제 작업은 기존 community 상세 화면 정책을 따른다.

## 14. 공개 커뮤니티 Q&A API (`/api/open-community`)

StackOverflow식 2계층 Q&A(`/community`): **질문(posts) → 답변(answers)** + 게시판. (V917에서 댓글 폐기 — 답변과 겹침.) 읽기는 비로그인 포함 누구나(anyopen), 작성/답변/좋아요는 로그인, 수정=작성자, 삭제=작성자/Admin soft hide. lesson community와 별개. 본문은 Tiptap(contentJson/contentHtml, sanitize). **게시판 모델(V916)**: `boardType` enum — F2R 커뮤니티 `FREE`(자유게시판) / `QNA`(Q&A) / `SUBJECT`(과목, `subjectId`로 `subjects` 동적 참조) + 공개 BuildIt Wiki 전용 `EXAM_REVIEW`(시험 후기) / `VARIANT`(변형문제) / `MATERIAL`(자료 공유·요청). `SUBJECT`일 때만 `subjectId` 필수. UI 라벨=`boardLabel`. 같은 `community_posts` 테이블을 쓰되 목록은 `GET /api/open-community/posts?scope=` 로 공간이 분리된다 — `scope` 미지정/`community` = F2R 게시판만(기존 `/community` 전체 탭), `scope=wiki` = Wiki 게시판만(`/wiki`). `boardType` 이 요청 scope에 없으면 400. Wiki enum 값 추가는 마이그레이션 불필요(`board_type` VARCHAR + `@Enumerated(STRING)`, CHECK 없음).

**보안 경계**: `GET /api/open-community/posts`·`/posts/**`·`/images/**`만 permitAll. 나머지(write/answer/like, `/answers/**`)는 `/api/**` catch-all 인증. **404/403**: 숨김/미존재 글·답변에 write/update/delete/like → **404**(존재 누설 금지); visible 대상 wrong-author → **403**. **transitive 가시성**: 답변은 답변·답변의 질문이 모두 visible해야 노출. **keyword 검색은 제목(title)만**(본문 LOB라 검색 제외).

### 질문
- `GET /posts?scope=&boardType=&subjectId=&keyword=&sort=` (공개) — scope(`community` 기본 = FREE/QNA/SUBJECT, `wiki` = EXAM_REVIEW/VARIANT/MATERIAL, 그 외 400), boardType(scope 내 값, null=scope 전체, scope 밖이면 400), subjectId(`boardType=SUBJECT`일 때만 의미), keyword=제목, sort=latest|recommended(최근 N일 좋아요). → `CommunityPostListResponse[]`(postId, boardType, subjectId, boardLabel, title, author*, createdAt, answerCount, likeCount, likedByMe, likeAllowed).
- `GET /posts/{postId}` (공개) — 숨김/미존재 404. → `CommunityPostDetailResponse`(boardType, subjectId, boardLabel, title, contentHtml, author*, like*, answers[](answerId, contentHtml, author*, createdAt), **attachments[]**(attachmentId, originalFileName, contentType, fileSize, downloadUrl) — F2R 게시판 글은 빈 배열).
- `POST /posts` (인증) — **JSON**(`Content-Type: application/json`) body `{boardType, subjectId, title, contentJson, contentHtml}`. boardType 불가/누락 400, `SUBJECT`인데 subjectId 누락·미존재 과목 400. 응답 상세.
- `POST /posts` (인증) — **multipart/form-data**(`boardType, subjectId?, title, contentJson?, contentHtml?` + `files`/`files[]`) — 글 + 문서/이미지 첨부를 **한 트랜잭션에서 원자적으로** 생성한다. 첨부 검증·저장 실패 시 글까지 롤백되어 "글만 남는" 고아 글이 생기지 않는다. 첨부 규칙은 아래 `POST /posts/{postId}/attachments` 와 동일. 첨부 없는 multipart 요청도 허용(= JSON 생성과 동일 동작). Wiki 글쓰기(`/wiki/board/{key}/new`) 신규 저장 경로.
- `PUT /posts/{postId}` (작성자, 타인 403, 숨김/미존재 404) — body 동일(JSON). 수정 화면의 새 첨부는 `POST /posts/{postId}/attachments` 로 이어서 올린다(글이 이미 존재하므로 고아 위험 없음).
- `DELETE /posts/{postId}` (작성자/Admin soft hide, 204).
- `POST /posts/{postId}/like` (인증 토글) — 작성 후 N일(`app.community.like-allowed-days`, 기본 30) 경과 400(count 유지), 사용자-질문당 1회(UNIQUE).

### 답변
- `POST /posts/{postId}/answers` (인증) — 숨김/미존재 질문 404. body `{contentJson, contentHtml}`. 응답 상세.
- `PUT /answers/{answerId}` (작성자, 숨김 답변/질문 404, 타인 403).
- `DELETE /answers/{answerId}` (작성자/Admin soft hide, 204).

### 본문 이미지
- `POST /images` (인증, multipart `image`) — 재인코딩 저장(애니메이션 GIF는 GIF로 프레임 보존, 애니메이션 WebP는 WebP 원본 유지, 그 외는 JPEG) → `{imageId, url}`. 비로그인 401.
- `GET /images/{imageId}` (공개 serve). Content-Type은 업로드 시 `community_images.content_type`에 저장된 값(`image/jpeg` / `image/gif` / `image/webp`).

### 게시글 문서 첨부 (V958, `community_post_attachments`) — Wiki 변형문제·자료 공유·요청 전용
- `POST /posts/{postId}/attachments` (인증·작성자, multipart `files`/`files[]`) — 게시판이 `VARIANT`/`MATERIAL`이 아니면 400, 기존 visible + 신규 > 10이면 400, `DocumentUploadValidator.validateAttachment`(확장자 **문서** `pdf/hwp/hwpx/doc/docx/xls/xlsx/ppt/pptx/zip` **+ 이미지** `jpg/jpeg/png/webp` + 매직바이트 교차검증 + 실행파일(MZ/ELF/`#!`) 차단 + 파일당 20MB) 실패 400. content_type은 확장자로 추론해 저장(이미지 = `image/jpeg|png|webp`). tx 롤백 시 storage 정리. → 게시글 상세.
- `DELETE /attachments/{attachmentId}` (작성자 또는 ADMIN, 204) — row + storage 파일 삭제.
- `GET /attachments/{attachmentId}/file` (공개 다운로드) — 글/첨부 비visible 404. **[내려받기] 버튼이므로 파일 종류(이미지·PDF 포함)와 무관하게 항상 `Content-Disposition: attachment`** (`FileDownloadResponses.attachment`) — 브라우저 뷰어로 펼치지 않고 실제 다운로드. 저장된 content_type 유지, RFC5987(UTF-8) 파일명, `X-Content-Type-Options: nosniff`.

- 관련 코드: `CommunityController`, `CommunityService`/`CommunityImageService`, `domain/community/*`(CommunityPost=질문/Answer/CommunityImage, BoardType), `CommunityProperties`(`app.community.*`). 마이그레이션: **`V914`(원본, 불변) + `V915`(Q&A 전환) + `V916`(board_type+subject_id) + `V917`(community_comments DROP — 댓글 폐기)**, 모두 in-place 업그레이드. ※ 적용된 V_파일 재작성 금지, 신규 번호로 추가.

## 15-1. 모의고사 원본 API (`/api/wiki/originals`) — 로그인 회원 BuildIt Wiki

공개 `/wiki` 의 모의고사 원본 PDF(문제지/정답지). `exam_analyses`(HTML 분석 리포트)와 목적이 달라 별도 테이블(`wiki_exam_originals`, V956)·도메인(`domain/wikioriginal/*`). 파일은 `FileStorage.saveRaw` 원본 저장, 다운로드로만 제공.

**보안 경계**: `GET /api/wiki/originals`·`/originals/**` 만 permitAll. `/api/admin/wiki/originals*` 는 `/api/admin/**` → ROLE_ADMIN.

### 로그인 회원
- `GET /api/wiki/originals?grade=` — deleted=false·visible=true, grade(1~3, null=전체) 필터, 최신순. → `WikiExamOriginalResponse[]`(wikiExamOriginalId, title, grade, examLabel, docType, docTypeLabel, originalFileName, fileSize, isVisible, createdAt, updatedAt, downloadUrl).
- `GET /api/wiki/originals/{id}/download` — 로그인 회원만. 비회원은 401이며 파일 조회·다운로드 로그를 수행하지 않는다. 공개 자료에 한해 `Content-Disposition: attachment` + 확장자로 추론한 content-type(pdf/hwp/hwpx) + RFC5987 파일명 + `nosniff` (`FileDownloadResponses.attachment`). 미존재/비공개 404.

### 관리 (ADMIN)
- `GET /api/admin/wiki/originals` — deleted=false 전체(비공개 포함).
- `POST /api/admin/wiki/originals` (multipart) — params `title, grade(1~3), examLabel, docType(QUESTION|ANSWER), visible(기본 true)`, part `file`(`.pdf/.hwp/.hwpx` + 매직바이트 교차검증, `app.upload.max-document-bytes` 이내). 검증 실패 400.
- `PUT /api/admin/wiki/originals/{id}` (multipart) — 위와 동일, `file` 선택(있으면 교체 + 이전 파일 삭제).
- `PATCH /api/admin/wiki/originals/{id}/visibility` — body `{isVisible}`.
- `DELETE /api/admin/wiki/originals/{id}` — soft delete(deleted=true, visible=false), 204.
- `DELETE /api/admin/wiki/originals/{id}/hard` — 물리 삭제 + storage 파일 삭제, 204.

- 관련 코드: `WikiExamOriginalController`/`WikiExamOriginalService`/`WikiExamOriginalRepository`, `domain/wikioriginal/*`(WikiExamOriginal, WikiExamOriginalDocType). 마이그레이션 **`V956`**(신규 테이블). 업로드 검증은 공용 `DocumentUploadValidator`(원본은 `.pdf`로 좁혀 사용).

## 15-2. 분석 자료 API (`/api/wiki/analysis-materials`) — 공개 BuildIt Wiki

공개 `/wiki` 의 분석 자료 **문서 파일**(PDF/HWP/HWPX/DOC/DOCX/XLS/XLSX/PPT/PPTX/ZIP). `exam_analyses`(HTML 분석 콘텐츠, `/analyzes`)와 **완전히 분리** — 별도 테이블(`wiki_analysis_materials`, V957)·도메인(`domain/wikianalysis/*`). `FileStorage.saveRaw` 원본 저장, 다운로드로만 제공.

**보안 경계**: `GET /api/wiki/analysis-materials`·`/analysis-materials/**` 만 permitAll. `/api/admin/wiki/analysis-materials*` 는 `/api/admin/**` → ROLE_ADMIN.

### 공개
- `GET /api/wiki/analysis-materials?grade=` — deleted=false·visible=true, grade(1~3, null=전체), 최신순. → `WikiAnalysisMaterialResponse[]`(wikiAnalysisMaterialId, title, description, schoolName, grade, year, semester, examType, subject, contentType, originalFileName, fileSize, isVisible, createdAt, updatedAt, downloadUrl).
- `GET /api/wiki/analysis-materials/{id}/download` — 로그인 회원만. 비회원은 401이며 파일 조회·다운로드 로그를 수행하지 않는다. 공개 자료에 한해 저장된 `content_type` + `Content-Disposition: attachment; filename*=UTF-8''…` + `nosniff`. 미존재/비공개 404.

### 관리 (ADMIN)
- `GET /api/admin/wiki/analysis-materials` — deleted=false 전체.
- `POST /api/admin/wiki/analysis-materials` (multipart) — params `title, description?, schoolName?, grade(1~3), year, semester, examType, subject?(blank→"영어"), visible`, part `file`(`DocumentUploadValidator` 통과, 20MB 이내).
- `PUT /api/admin/wiki/analysis-materials/{id}` (multipart) — `file` 선택(있으면 교체 + 이전 파일·content_type 갱신).
- `PATCH /api/admin/wiki/analysis-materials/{id}/visibility` — body `{isVisible}`.
- `DELETE /api/admin/wiki/analysis-materials/{id}` — soft delete, 204.
- `DELETE /api/admin/wiki/analysis-materials/{id}/hard` — 물리 삭제 + storage 파일 삭제, 204.

- 관련 코드: `WikiAnalysisMaterialController`/`Service`/`Repository`, `domain/wikianalysis/*`. 마이그레이션 **`V957`**. 공용: `infrastructure/storage/DocumentUploadValidator`, `infrastructure/web/FileDownloadResponses`.

## 15-3. 문항별 Question Wiki + THREAD API (`/api/wiki/questions`) — 공개 BuildIt Wiki (V959)

한 모의고사 문항을 중심으로 질문·해설·토론·변형문제·기출을 축적한다. 하위 데이터는 안정적 `questionWikiId` 로 연결되고, 향후 AI 는 `GET /api/wiki/questions/{id}/context` 한 번으로 문항 전체 맥락을 읽는다(MCP `get_question_wiki_context(questionWikiId)` 가 그대로 감쌀 수 있게).

**보안 경계**: `GET /api/wiki/questions`·`/questions/**` 만 permitAll(목록/상세/Context/첨부 다운로드). write(글/답글/수정/삭제/첨부)는 `/api/**` catch-all → 인증(STUDENT/TUTOR/ADMIN 누구나). 자료 링크는 `/api/admin/wiki/questions/**` → ROLE_ADMIN. 작성자만 수정, 작성자·ADMIN 만 숨김. `@AuthenticationPrincipal` 기준 — request body user id 불신뢰.

### 공개 (read)
- `GET /api/wiki/questions?subject=&grade=&examYear=&examLabel=` — visible 문항, `(grade, questionNumber)` 순. → `QuestionWikiSummaryResponse[]`(questionWikiId, subject, grade, examYear, examLabel, questionNumber, passageGroupKey, title, displayContext, threadCount).
- `GET /api/wiki/questions/{id}` — 단건 요약.
- `GET /api/wiki/questions/{id}/context` — 문항 전체 Context. `QuestionWikiContextResponse`: `question`(문항 정보 + 공동지문 번호), `materials[]`(연결 자료 + downloadUrl), `threads[]`(DISCUSSION 최상위 + `replies[]` 1단계 중첩), `variants[]`(GENERATED_PRACTICE + `variantDetail`), `examEvidence[]`(EXAM_EVIDENCE + `examEvidence`), `aiSummary`(최신 APPROVED 또는 null), `counts`. 각 글은 `category`/`categoryLabel` + `dataKind`/`dataKindLabel` + `participantType`(교육자/학생/운영자) + `mine`/`canManage` + `attachments[]`.
- `GET /api/wiki/questions/attachments/{attachmentId}/file` — `Content-Disposition: attachment` 강제(`FileDownloadResponses.attachment`).

`materials[]`의 현재 문항 연결 HTML에는 AI용 `aiText`가 포함된다. 기존 `wiki_analysis_materials` 파일을 서버에서 읽어 text로 추출한 값이며, 원문을 읽지 못하면 null로 두고 AI 호출 전에 생성이 차단된다. 다른 문항 자료를 조회하거나 자동 연결하지 않는다.\n\n`materials[]` 항목은 사람이 읽을 카드 정보만 담는다: `linkId`, `kindLabel`("모의고사 원본"/"학습자료"), `label`(자료 종류 자유 입력, 예 "상세 해설지"), `title`·`description`·`fileName`(원본 자료에서 해석), `viewUrl`, `previewKind`(`"HTML"` 또는 null), `previewUrl`(HTML 일 때 `/api/wiki/questions/materials/{linkId}/preview`). 삭제된 원본은 응답에서 제외된다. 각 글의 `category`/`dataKind` 는 내부 필드로 유지되나(향후 AI 자동분류·MCP 용) UI 에는 노출하지 않는다.

- `GET /api/wiki/questions/materials/{linkId}/preview` — HTML 학습자료 격리 미리보기(공개). `Content-Type: text/html; charset=UTF-8` + `Content-Disposition: inline` + **`Content-Security-Policy: sandbox; default-src 'none'; img-src data: https:; style-src 'unsafe-inline' https:; font-src https: data:; frame-ancestors 'self'`** + `nosniff`. 프런트는 이 URL 을 `<iframe sandbox>` 로만 연다(부모 DOM 삽입 X, 스크립트 차단, CSS/이미지 유지). HTML 링크가 아니면 404. 파일 다운로드는 여전히 `viewUrl`(attachment 강제)이 담당한다.

### write (인증)
- `POST /api/wiki/questions/{id}/posts` (JSON) — `QuestionThreadPostRequest`{category?, dataKind?, title?, content, parentPostId?, variant?, examEvidence?}. **단순화된 UI 는 `{content}` 만 보낸다** → 서버가 `category=ETC`(미분류) + `dataKind=DISCUSSION` 기본값으로 저장(향후 Browser AI 가 이 값을 갱신). `dataKind=GENERATED_PRACTICE`(API 직접 호출 시) → `variant.questionText` 필수, `EXAM_EVIDENCE` → `examEvidence.schoolName` 필수. → 갱신된 Context.
- `POST /api/wiki/questions/{id}/posts/{parentPostId}/replies` (JSON) — 1단계 답글(답글의 답글 400, 답글은 항상 DISCUSSION). → Context.
- `PUT /api/wiki/questions/posts/{postId}` (JSON) — 작성자만. **`variant`/`examEvidence` 가 요청에 실제로 들어온 경우에만** 상세를 갱신한다 → 단순화 UI(내용만)로 기존 GENERATED_PRACTICE/EXAM_EVIDENCE 글을 수정해도 원문·상세가 보존된다. → Context.
- `DELETE /api/wiki/questions/posts/{postId}` — 작성자·ADMIN soft-hide. → Context.
- `POST /api/wiki/questions/posts/{postId}/attachments` (multipart, `files`/`files[]`) — 작성자만, 글당 10개·파일당 10MB. → Context.
- `DELETE /api/wiki/questions/attachments/{attachmentId}` — 업로더·글 작성자·ADMIN, 204.

### 관리 (ADMIN) — 학습자료
- `GET /api/admin/wiki/questions/materials/search?q=` — "Wiki 자료에서 찾기": 기존 자료실(`wiki_exam_originals` + `wiki_analysis_materials`, 비삭제)에서 `q`(제목·학교·파일명, 대소문자 무시) 매칭 후보를 통합 반환(최대 60). → `LinkableMaterialResponse[]`(materialKind, kindLabel, materialId, title, meta).
- `POST /api/admin/wiki/questions/{id}/material-links` — 검색해서 고른 자료를 연결. body `{materialKind: EXAM_ORIGINAL|ANALYSIS_MATERIAL, materialId, label?}`. 원본 미존재 404, 중복 409. → `QuestionWikiMaterialLinkResponse`.
- `POST /api/admin/wiki/questions/{id}/materials/upload` (multipart) — "직접 올리기": params `title?`(비우면 문항 정보로 자동 생성), `description?`, `docLabel?`(자료 종류), part `file`. **기존 `WikiAnalysisMaterialService` 의 저장/검증(매직바이트 교차검증, 20MB)을 그대로 재사용**하여 `wiki_analysis_materials` row 를 만들고(grade/year 는 문항에서 파생, semester "공통"·examType=문항 examLabel·schoolName "전국연합") 현재 문항에만 링크를 건다(자동 매핑 없음). Question Wiki 전용 파일 시스템 없음. → `QuestionWikiMaterialLinkResponse`.
  - **허용 확장자 = PDF·HWP·HWPX·DOC·DOCX·XLS·XLSX·PPT·PPTX·ZIP + `.html`/`.htm`** (`DocumentUploadValidator.DOCUMENT_WITH_HTML_EXTENSIONS`). **HTML 은 이 경로에서만 허용** — 전역 자료실(`POST /api/admin/wiki/analysis-materials`)의 `DocumentUploadValidator.validate(file)` 정책은 그대로 HTML 을 거부한다. HTML 은 매직바이트가 없어 실행파일(MZ/ELF/`#!`) 배제 후 선행 공백/BOM 을 건너뛴 첫 바이트가 `'<'` 인지로만 판별한다. `content_type` 은 `text/html` 로 저장.
- `DELETE /api/admin/wiki/questions/material-links/{linkId}` — 연결만 해제(자료 자체는 유지), 204.

- 관련 코드: `domain/questionwiki/*`(`QuestionWikiController`/`QuestionWikiAdminController`/`QuestionWikiPageController`, `QuestionWikiService`/`QuestionThreadService`). SSR: `/wiki/questions`, `/wiki/questions/{id}`(`Cache-Control: no-store`). 마이그레이션 **`V959`**. 화면 단순화 이후 상세 화면은 [학습자료]/[이 문항에 대한 의견] 두 영역 + `+ 의견 남기기` 모달만 노출한다.

## 16. Landing Video Admin / Public API

관리형 영상은 임의 목록이 아니라 아래 고정 3슬롯이다. 슬롯 생성·삭제·정렬 API는 제공하지 않는다.

- `HERO_BACKGROUND`
- `ACADEMY_SHORTS_DEMO`
- `PARENT_REPORT_DEMO`

> ⚠️ 2026-08 Build It 랜딩 개편 이후 `/`가 실제로 쓰는 슬롯은 `ACADEMY_SHORTS_DEMO`(Build It Web 섹션) 하나다.
> `HERO_BACKGROUND`와 `PARENT_REPORT_DEMO`는 `/admin/landing`에서 여전히 업로드·공개 전환이 가능하지만 랜딩 어디에도 렌더되지 않는다.
> 슬롯 정리(제거 또는 Build It 섹션 재배치)는 별도 작업으로 남겨둔다 — enum 값 삭제는 기존 `landing_video_slot` row를 고아로 만든다.

### LandingVideoAdminResponse

```json
{
  "slot": "HERO_BACKGROUND",
  "label": "히어로 배경",
  "description": "랜딩 첫 화면의 자동 재생 배경 영상",
  "visible": true,
  "sourceType": "MANAGED",
  "hasManagedAsset": true,
  "originalFilename": "hero.mov",
  "durationSec": 12,
  "sizeBytes": 8388608,
  "width": 1280,
  "height": 720,
  "previewUrl": "/api/admin/landing/videos/HERO_BACKGROUND/stream",
  "thumbnailUrl": "/api/admin/landing/videos/HERO_BACKGROUND/thumbnail"
}
```

- `sourceType`: `MANAGED`(관리자 업로드) 또는 `BUNDLED`(프로젝트 기본 mp4).
- 번들 상태는 `hasManagedAsset=false`, 기술 메타데이터와 `thumbnailUrl`이 `null`이며 `previewUrl`은 기존 정적 mp4 경로다.
- managed 상태의 `previewUrl`/`thumbnailUrl`은 숨김 상태에서도 ADMIN이 확인할 수 있는 보호 endpoint다.
- 응답에는 `storageKey`, S3 key, 로컬 절대경로를 포함하지 않는다. `visible`은 source 상태와 독립적이다.

### ✅ GET /api/admin/landing/videos

- 목적: 관리자 화면의 고정 3슬롯을 enum 표시 순서대로 조회한다.
- 현재 사용처: `static/js/admin_landing.js`.
- 인증/권한: `ROLE_ADMIN` 전용. 비인증 `401`, STUDENT/TUTOR `403`.
- Response: `200 OK`, `LandingVideoAdminResponse[3]`.
- 비고: 방어적으로 row가 없으면 공개 상태의 `BUNDLED` 응답을 만든다.

### ✅ PUT /api/admin/landing/videos/{slot}

- 목적: 지정 슬롯의 영상을 교체한다. 동기 ffmpeg 변환이 끝난 뒤 응답한다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Request: `multipart/form-data`, part 이름 `video`.
- 서버 검증:
  - 빈 파일, 업로드 100MiB 초과, 길이가 비정상(`N/A`, non-finite, 0초 이하)이거나 60초 이상,
    미지원 video codec은 `400`.
  - 입력 codec: `h264`, `hevc`, `vp8`, `vp9`, `av1`, `mpeg4`.
  - 정규화된 MP4를 다시 probe해 실제 길이를 같은 규칙으로 검증하며, 이 실제 duration/해상도를 저장한다.
  - 변환 mp4 30MiB 초과 또는 poster JPEG 2MiB 초과는 저장 전에 `400`.
- Response: `200 OK`, 교체된 `LandingVideoAdminResponse`.
- 실패:
  - 제한 위반·변환 실패 `400` + `{ "message": string }`.
  - ffmpeg/ffprobe 부재 또는 동시 변환 포화 `503` + `{ "message": string }`.
  - 유효하지 않은 `{slot}` `400`.
- 교체 정합성:
  - 새 mp4/thumbnail 저장 → 슬롯 row lock + `REQUIRES_NEW` DB 원자 교환 → 해당 transaction
    commit 완료 뒤 이전 파일 best-effort 정리 순서다.
  - 저장/DB 실패 시 새 파일만 보상 삭제하고 기존 공개본과 `visible` 값을 유지한다.
  - 숨김 슬롯을 교체해도 자동 공개하지 않는다.

### ✅ PATCH /api/admin/landing/videos/{slot}/visibility

- 목적: asset을 지우지 않고 랜딩 공개/숨김 상태만 변경한다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Request:
  ```json
  { "visible": false }
  ```
- `visible`은 non-null 필수값이다. 누락/null 또는 유효하지 않은 `{slot}`은 `400`.
- Response: `200 OK`, 갱신된 `LandingVideoAdminResponse`.
- 비고: 다시 공개하면 기존 managed asset을 재사용하고, asset이 없으면 번들 mp4로 복원한다.

### ✅ GET /api/admin/landing/videos/{slot}/stream

- 목적: managed mp4 관리자 미리보기. 숨김 슬롯도 조회할 수 있다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Response: `200`, `Content-Type: video/mp4`, `Content-Length`, `Cache-Control: no-store`.
- managed asset이 없으면 `404`; 저장소 읽기/크기 검증 실패 또는 동시 전송 상한 초과는 `503`.

### ✅ GET /api/admin/landing/videos/{slot}/thumbnail

- 목적: managed poster JPEG 관리자 미리보기. 숨김 슬롯도 조회할 수 있다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Response: `200`, `Content-Type: image/jpeg`, `Content-Length`, `Cache-Control: no-store`.
- managed thumbnail이 없으면 `404`; 저장소 읽기/2MiB 상한 검증 실패 또는 동시 전송 상한 초과는 `503`.

### ✅ GET /api/landing/videos/{slot}/stream

- 목적: SSR 랜딩이 선택한 공개 managed mp4 proxy stream.
- 인증/권한: 없음. **GET만** 익명 허용한다.
- Response: `200`, `Content-Type: video/mp4`, `Content-Length`, `Cache-Control: no-store`.
- row 없음, 숨김, 번들 상태 또는 managed asset 없음은 `404`.
- 저장 크기 정보 오류, 실제 바이트 불일치/초과, 저장소 IO 실패 또는 동시 전송 상한 초과는 `503`.
- 교체 정리와 읽기가 경합하면 DB의 최신 key를 다시 읽고, key가 바뀐 경우에만 한 번 재시도한다. public 재시도 시에도 최신 공개 상태를 다시 검사한다.

### 전송/캐시 제한

- public/admin managed 응답은 `FileStorage.readSingleLimited`로 제한된 전체 바이트를 읽어 전송한다.
- 기본 상한은 영상 30MiB, thumbnail 2MiB, 랜딩 동시 전송 3개다. permit은 응답 body 쓰기가 끝날 때까지 유지해 영상 retained payload를 대략 90MiB 이하로 묶는다.
- 현재 HTTP Range를 처리하지 않으며 `206 Partial Content`/`Content-Range`를 제공하지 않는다. 요청은 `200` 전체 응답이다. Range/progressive streaming/CDN은 후속 범위다.
- 안정 URL이 교체 뒤 같은 경로를 사용하므로 public/admin stream 모두 `Cache-Control: no-store`다.

### 삭제 범위

- `DELETE` endpoint와 물리 삭제 UI는 없다.
- 운영자는 숨김/복원 또는 교체만 수행한다. 교체 성공 뒤 이전 asset 정리는 내부 best-effort 작업이며, 임의 슬롯 삭제나 변경 이력/복구 기능은 이번 범위가 아니다.

## 17. Beta Comment API (랜딩 베타 의견)

구 랜딩(`/`)의 "베타 서비스에 한마디 남겨주세요" 폼과 "베타 참여자들의 목소리" 캐러셀을 잇던 API다.

> ⚠️ 2026-08 Build It 랜딩 개편으로 **공개 UI가 사라졌다.** `POST /api/beta-comments`와 어드민 모더레이션
> (`/admin/landing`)은 그대로 살아 있지만, 의견을 남길 화면과 캐러셀을 노출하는 화면이 현재 없다.
> `LandingPageController`도 `betaCarousel`을 더 이상 주입하지 않는다(`BetaCommentService.renderCarousel()`은 미사용 상태).
> 다시 노출하려면 Build It 랜딩에 대응 섹션을 새로 배치해야 한다. 상세: [DOMAIN.md §4.40](DOMAIN.md).

`participantType` enum: `ACADEMY_PRINCIPAL` / `ACADEMY_TUTOR` / `STUDY_ROOM` / `TEACHING_ROOM` / `OTHER`.
`status` enum: `PENDING` / `APPROVED` / `HIDDEN`. 등록은 `APPROVED`로 시작하므로 `PENDING`은 사전 모더레이션을 켠 경우에만 쓰인다.
`highlightLabel` enum: `NEW` / `REVIEWING` / `APPLYING` / `APPLIED` (null = 뱃지 미노출).

### ✅ POST /api/beta-comments

- 목적: 로그인 사용자가 베타 의견을 등록한다. 결과는 `APPROVED`로 저장되어 **랜딩에 즉시 노출된다**(사전 승인 없음). 사후에 어드민이 숨긴다.
- 인증/권한: **인증 필수.** `/api/**` catch-all(`.authenticated()`)이 담당하며 permitAll 예외를 두지 않는다. 미로그인 `401`.
- Request:
  ```json
  { "nickname": "민지", "participantType": "STUDY_ROOM", "comment": "상담 신청 알림 기능이 있으면 좋겠어요." }
  ```
- 서버 검증 (클라이언트 `maxlength`에 의존하지 않고 `BetaComment` 팩토리가 단일 지점에서 판정):
  - `nickname` 필수, trim 후 1~12자. 초과/공백만 `400`.
  - `comment` 필수, trim 후 1~80자. 초과/공백만 `400`.
  - `participantType` 필수, enum 화이트리스트. 누락/미지원 값 `400`.
- Response: `201 Created`, 본문 없음. 승인 전에는 노출되지 않으므로 클라이언트는 성공 여부만 알면 된다.
- 실패: 검증 위반 `400` + **평문 메시지**(JSON 아님 — `GlobalExceptionHandler.handleIllegalArgument`).
- 등록 횟수 제한 없음. 스팸 게이트는 어드민 모더레이션이 유일하다.
- 현재 사용처: `static/js/landing_beta_comment.js`. 401/403이면 공통 로그인 모달(`openLoginModal`)을 연다 — `api.js`의 `fetchAuth`는 401에서 `/home`으로 이탈시키므로 이 폼에서는 쓰지 않는다.

### ✅ GET /api/admin/beta-comments

- 목적: 모더레이션 화면의 한 번 조회 분량. 상태별 목록 3개와 현재 랜딩 구간 판단용 수치를 함께 내린다.
- 인증/권한: `ROLE_ADMIN` 전용 (`/api/admin/**`). 비인증 `401`, STUDENT/TUTOR `403`.
- Response: `200 OK`
  ```json
  {
    "pending": [ { "id": 12, "nickname": "민지", "participantType": "STUDY_ROOM",
                   "participantLabel": "공부방 운영", "comment": "…",
                   "status": "APPROVED", "highlightLabel": null, "highlightText": null,
                   "createdAt": "2026-07-30T13:20:11" } ],
    "approved": [],
    "hidden": [],
    "approvedCount": 0,
    "carouselMaxCards": 10,
    "animationMinCards": 5,
    "highlightOptions": [ { "value": "NEW", "label": "신규" } ]
  }
  ```
- `approvedCount` / `animationMinCards` / `carouselMaxCards`를 내리는 이유: 승인 건수가 0건 / 1~4건 / 5건 이상에서 랜딩 렌더가 달라지므로 어드민이 현재 어느 구간인지 알아야 한다.
- `highlightOptions`는 한글 라벨을 서버가 내려 화면에 리터럴이 중복되지 않게 한다.
- 목록에 상한을 두지 않는다(캐러셀 10건 상한은 공개 렌더에만 적용).
- 현재 사용처: `static/js/admin_beta_comments.js`.

### ✅ PATCH /api/admin/beta-comments/{id}/status

- 목적: 승인/숨김. 승인한 것만 랜딩에 노출된다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Request:
  ```json
  { "status": "APPROVED" }
  ```
- `status`는 non-null 필수. 누락/null `400`.
- **`PENDING`으로는 되돌릴 수 없다** — 이미 검토한 의견이 미검토 큐로 섞여 들어가는 것을 막는다. 시도하면 `400`.
- Response: `200 OK`, 갱신된 항목 1건.
- 실패: 없는 `{id}` `404`.

### ✅ PATCH /api/admin/beta-comments/{id}/highlight

- 목적: 카드 뱃지(`feedback-live`) 지정/해제.
- 인증/권한: `ROLE_ADMIN` 전용.
- Request:
  ```json
  { "highlightLabel": "APPLYING" }
  ```
- **`highlightLabel: null`은 유효한 요청이며 뱃지 해제를 뜻한다** — null을 거르면 해제 경로가 사라진다.
- 미지원 enum 값 `400`, 없는 `{id}` `404`.
- Response: `200 OK`, 갱신된 항목 1건.
- 뱃지가 자유 입력이 아니라 고정 4집합인 이유는 뱃지 폭이 카드 레이아웃을 넘기지 않도록 보장하기 위함이다.

### 관련 코드

- `domain/betacomment/` — `BetaCommentController`(공개 등록), `BetaCommentAdminController`, `BetaCommentService`, `BetaComment`, `BetaCommentStatus`/`BetaCommentHighlight`/`BetaParticipantType`, `BetaCommentCarousel`(복제 불변식).
- 화면: `templates/public/landing.html`(폼 + 캐러셀), `templates/admin/landing.html`(모더레이션 섹션, `/admin/landing`).
- 마이그레이션: `V939__add_beta_comments.sql`.
## 18. Problem Generator API (`/problem-generator`, 1단계)

### ✅ SSR 화면 `/problem-generator/**`

- 목적: AI 문제 생성기 7메뉴 스튜디오 목업을 Thymeleaf + vanilla JS로 제공한다.
- 경로:
  - `/problem-generator` — 문제 생성기
  - `/problem-generator/digital-twin` — Digital Twin 자료 업로드
  - `/problem-generator/questions` — 강사 문제 DB
  - `/problem-generator/exam` — 내신 실전 제작
  - `/problem-generator/wrong-notes` — 학생별 오답노트
  - `/problem-generator/textbooks` — 교과서 DB
  - `/problem-generator/schools` — 학교별 DB
- 인증/권한: STUDENT / TUTOR / ADMIN. 비로그인 HTML 요청은 `/?login=required`로 302 리다이렉트한다. 목적지는 비로그인 목적지 통일 원칙대로 `/`를 유지하고, 랜딩(`static/js/landing_buildit.js`)이 `login=required`를 읽어 안내 alert + 공통 로그인 모달을 연 뒤 쿼리 마커를 지운다. 비로그인 JSON 요청은 기존대로 401이다.
- 비고: 자료 업로드/조회와 Gemini BYO 키 기반 문제 생성 테스트가 실동작한다. Gemini 키는 요청 바디로만 전달하며 서버 DB/설정 파일에 저장하지 않는다.
- Wiki FastAPI 문제 생성은 선택된 Digital Twin의 유형 ID를 서버 카탈로그의 한글명·세부 지침으로 변환한다. `grammar`는 화면의 `시험 범위 주요 문법 사항` 전용 입력값이며 문법·서답형 우선 지시로 전달되고 각 문항 `raw_json.grammarFocus`에 추적 저장된다. 응답은 밑줄/선택지/순서형 구조와 서답형 완결성·단어 수 조건을 검증하며 1회 자동 보정 재요청 후에도 실패하면 `502`로 거부하고 저장하지 않는다.


### ✅ POST /api/problem-generator/gemini/generate

- 목적: 브라우저에서 입력한 BYO Gemini API 키로 중등 영어 내신 문제를 생성한다. 브라우저 CORS 회피를 위해 서버가 Google Gemini `generateContent` 호출을 프록시한다.
- 인증/권한: 인증 필수(STUDENT / TUTOR / ADMIN).
- 저장 정책: `apiKey`는 요청 처리 중에만 사용하며 DB/로그/설정 파일에 저장하지 않는다. 프론트는 `sessionStorage`에만 보관한다.
- Request JSON:
  ```json
  {
    "apiKey": "<사용자 BYO Gemini API key>",
    "grammar": "분사구문, 관계대명사 what",
    "passage": "새 지문 또는 출제 조건",
    "count": 3,
    "difficulty": "학교 평균",
    "materialTitle": "압구정중 2학년 1학기 기출 분석"
  }
  ```
- 외부 호출: `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent`, 헤더 `x-goog-api-key`.
- `materialTitle`은 선택한 Digital Twin 자료명을 생성 프롬프트 컨텍스트로 전달한다. 자료 원문/이미지 bytes는 전송하지 않는다.
- Response: `200 OK`
  ```json
  {
    "model": "gemini-3.6-flash",
    "text": "{\"questions\":[...],\"summary\":...}"
  }
  ```
- 실패: 키/프롬프트 누락 `400` 계열, Gemini 인증/모델/쿼터 오류는 `5xx` 응답 메시지로 표시한다.


### ✅ Problem Generator 저장 문제 API

#### POST /api/problem-generator/question-sets

- 목적: Gemini 생성 결과를 사용자별 문제 묶음으로 저장한다. 긴 지문은 `passages`, 개별 문항은 기존 `questions(source=GENERATED)`에 저장하고, `problem_generator_question_sets` / `problem_generator_set_questions`가 소유자·묶음·정렬을 관리한다.
- 인증: `STUDENT` / `TUTOR` / `ADMIN`
- Request(JSON):
  - `title` — 저장 묶음 제목(없으면 Gemini summary fallback)
  - `grammar` — 생성 시 핵심 문법(optional)
  - `promptPassage` — 생성 시 공유 지문/paragraph(optional, 있으면 `passages`에 1건 저장 후 문항들이 공유)
  - `model` — 예: `gemini-3.6-flash`
  - `rawResponseJson` — Gemini 생성 JSON(`questions[]` 필수)
  - `materialId` — 문제 생성 화면에서 선택한 Digital Twin 자료 ID(optional). 저장 묶음과 자료를 연결한다.
- Response: `QuestionSetDetailResponse`

#### GET /api/problem-generator/question-sets

- 목적: 내 PRIVATE 문제 묶음 + PUBLIC 문제 묶음을 최신순 조회한다.
- Query: `page`, `size`
- Response: Spring `Page<QuestionSetSummaryResponse>`

#### GET /api/problem-generator/question-sets/{setId}

- 목적: 저장 묶음 상세와 포함 문항, 공유 지문 내용을 조회한다.
- 권한: 소유자, ADMIN, 또는 PUBLIC 묶음.

#### PATCH /api/problem-generator/question-sets/{setId}

- 목적: 묶음 제목/핵심 문법/공유 prompt passage/visibility를 수정한다.
- 권한: 소유자 또는 ADMIN. `PUBLIC` 전환은 ADMIN만 가능.

#### PATCH /api/problem-generator/question-sets/{setId}/questions/{questionId}

- 목적: 저장된 GENERATED 문항의 발문, 선택지, 정답, 해설, 배점과 공유 지문 내용을 수정한다.
- Request(JSON): `questionType`, `questionText`, `choices[]`, `correctAnswer`, `explanation`, `questionLabel`, `point`, `passageContent`

#### DELETE /api/problem-generator/question-sets/{setId}

- 목적: 저장 문제 묶음을 삭제한다. 연결된 GENERATED `questions`와 이 묶음에서 만든 `passages`도 함께 정리한다.
- 권한: 소유자 또는 ADMIN.

### ✅ POST /api/problem-generator/gemini/chat

- 목적: 우측 `박원장 AI` assistant가 브라우저에 임시 저장된 BYO Gemini 키로 작업 컨텍스트 기반 대화를 수행한다.
- 인증/권한: 인증 필수(STUDENT / TUTOR / ADMIN).
- 저장 정책: `apiKey`는 요청 처리 중에만 사용하며 DB/로그/설정 파일에 저장하지 않는다. 프론트는 문제 생성기와 동일하게 `sessionStorage`의 `f2r_msc_gemini_key`를 읽는다.
- Request JSON:
  ```json
  {
    "apiKey": "<사용자 BYO Gemini API key>",
    "message": "이 지문으로 문법 문제 3개 가능해?",
    "context": "현재 작업 컨텍스트 문구"
  }
  ```
- Response: `200 OK`
  ```json
  { "model": "gemini-3.6-flash", "text": "가능합니다..." }
  ```

### ✅ POST /api/problem-generator/materials

- 목적: 로그인 사용자가 Digital Twin 개인 자료(PRIVATE)를 업로드한다.
- 인증/권한: 인증 필수. 업로드 결과는 본인만 조회/삭제 가능하다.
- Content-Type: `multipart/form-data`
- Request parts/params:
  - `files`: 이미지 파일 1~20개. 1단계는 이미지(`image/*`)만 허용한다.
  - `title`: 필수, trim 후 1~150자.
- 저장: `UploadedFileIngestor`를 통해 20MB 문서 상한(`UploadSizeGuard`)과 `ImageSanitizer` 정규화(최장변 1600px, GIF/WebP 보존 정책 포함)를 공유한다.
- Response: `201 Created`, `List<MaterialResponse>`
  ```json
  [{
    "materialId": 1,
    "title": "압구정중 2학년 기출",
    "originalFileName": "exam.png",
    "fileType": "IMAGE",
    "fileSize": 12345,
    "visibility": "PRIVATE",
    "uploadedBy": 10,
    "pageCount": 1,
    "createdAt": "2026-08-08T14:00:00"
  }]
  ```
- 실패: 빈 파일/비이미지/제목 오류 `400`, 비로그인 `401`.

### ✅ POST /problem-generator/wiki/api/educator-insights

- 목적: Wiki `지능 → 수업 인사이트 기록`에서 수업 직후의 관찰과 교육자 판단을 Educator Brain 컨텍스트로 저장한다.
- 현재 사용처: React Wiki `InsightRecorder`.
- 전달 경로: Wiki frontend → Spring `BuildItWikiProxyController` → FastAPI `POST /api/educator-insights`.
- Request JSON: `content`(필수, 최대 10,000자), `school`, `grade`, `student_context`, `lesson_context`(선택).
- Response: `201 Created`, 저장된 insight row.
- 실패: 내용 공백 또는 길이 초과 `400`.
- 비고: 이번 범위는 생성/최근 조회이며 수정·삭제 API는 제공하지 않는다.

### ✅ GET /problem-generator/wiki/api/educator-insights?limit=5

- 목적: 최근 수업 인사이트를 최신순으로 조회한다.
- 현재 사용처: React Wiki `InsightRecorder` 최근 기록, 내신Wiki V1 STEP 2 `수업 인사이트 기록` 목록(NaesinWikiV1이 `limit=100` + school/grade/year/semester/exam Context로 조회).
- 전달 경로: Wiki frontend → Spring proxy → FastAPI `GET /api/educator-insights`.
- Query: `limit` 1~200(범위 밖 값은 서버에서 보정), 기본 5. 선택 필터 `school`, `grade`, `year`, `semester`, `exam`.
- Response: `200 OK`, insight row 배열.

### ✅ GET /problem-generator/wiki/api/schools

- 목적: Wiki 학교 선택 UI에 기존 SQLite 기록의 학교 → 학년 → 시험 계층과 Digital Twin 연결 상태를 제공한다.
- 전달 경로: Wiki frontend → Spring `BuildItWikiProxyController` → FastAPI `GET /api/schools`.
- 데이터 원천: `digital_twin_profiles`의 active row와 기존 `generated_questions`, `students`의 학교·학년·시험 값을 합친다. 기존 row를 이동하거나 새 DB로 복사하지 않는다.
- Response: `[{ school, grades: [{ grade, exams: [{ exam, twinId, version, updatedAt, hasDigitalTwin }] }] }]`. 기존 학교 기록만 있고 Twin이 없으면 `twinId/version=null`, `hasDigitalTwin=false`다.

### ✅ Generated Question Store (MCP 생성문항 자동저장)

- 목적: Claude MCP가 Wiki 학습자료로 생성한 시험문항을 `Source Material → Generated Test → Questions` 관계를 유지한 채 영구 저장한다. 새 저장 로직을 만들지 않고 기존 `question_contract.validate_transform` + `_save_generated`(mode='mcp')를 재사용한다.
- 스키마: `source_materials`((school,grade,title) unique upsert), `generated_tests`(external_id idempotency, source_material_id FK, source_page, twin_id/twin_label/twin_pattern, difficulty, target_vocabulary/target_grammar JSON, generation_source, question_count), `generated_questions.test_id`(nullable, 기존 row는 NULL).
- MCP tool: `save_generated_test(school, grade, source_material, questions[, source_page, test_type, title, digital_twin, twin_pattern, difficulty, target_vocabulary, target_grammar, target_passage, year, semester, exam, publisher, lesson, material_type, external_id])`. 문항 계약 검증을 어떤 쓰기보다 먼저 하고(all-or-nothing), 저장 성공 시에만 `{saved:true, test_id, question_count, source_material, source_page, duplicate}`를 반환한다. 저장 실패는 `storage: failed —` 접두 오류로 생성 성공과 구분한다. `external_id` 미지정 시 요청 내용으로 파생해 같은 요청 재시도가 중복 저장되지 않는다. Wiki 문제 생성 workflow의 기본 마지막 단계다.
- `POST /problem-generator/wiki/api/generated-tests`: MCP tool과 동일 입력(JSON)을 받는 REST 경로. Response `200`(dup 포함) / `422`(문항 검증 실패, 아무 것도 저장 안 함) / `500`(저장 중 실패).
- `GET /problem-generator/wiki/api/source-materials?school=&grade=`: `source_materials` 목록 + `test_count`.
- `GET /problem-generator/wiki/api/source-materials/{id}/generated-tests`: 해당 학습자료의 생성 시험 목록.
- `GET /problem-generator/wiki/api/generated-tests/{id}`: `{ test: {...메타, target_vocabulary/grammar 파싱}, questions: [list_questions 형태, test_id로 필터·id 오름차순] }`.
- `GET /problem-generator/wiki/api/workspaces/final/context` 응답에 `generated_tests` 키가 추가된다(추적성). 기존 키는 그대로 유지.
- 사용처: React Wiki `생성 문제` 페이지(`GeneratedTestBrowser`) — 학교/학년 → 학습자료 → 생성 시험 → 학생용 문제 / 정답·해설 / 출제 정보.

### ✅ POST /problem-generator/wiki/api/assistant/chat

- 목적: Wiki Assistant가 현재 목표와 관련된 최소 DB 컨텍스트를 확인하고 대화형 작업 안내를 반환한다.
- 전달 경로: Wiki frontend → Spring `BuildItWikiProxyController` → FastAPI `POST /api/assistant/chat`. 요청의 임시 `api_key`가 있으면 해당 Gemini 키를 우선 사용하고, 없으면 서버 `GEMINI_API_KEY`, 둘 다 없거나 호출 실패 시에만 규칙 기반 응답으로 전환한다. 키는 저장하지 않는다.
- Request JSON: `message`, 세션 structured `state`, 최근 `history`(최대 8개 전송), 첨부의 `name/type` 메타데이터. 첨부 파일 본문은 저장하거나 전송하지 않는다.
- Response: `text`, 갱신된 `state`, 최소 조회 결과 `context`, 기존 Wiki 화면 `page/action`, 응답 `model`.
- AI: 서버 환경의 `GEMINI_API_KEY`가 있을 때 기존 Gemini 텍스트 호출을 재사용한다. 키 미설정·호출 실패 시 `rules-fallback` 답변을 반환한다. 브라우저에 서버 키를 노출하지 않는다.
- 부작용: 조회 전용이며 DB 생성·수정·삭제를 하지 않는다.

### ✅ Wiki AI Router / A/B API

- `GET /problem-generator/wiki/api/ai/providers`: Gemini/OpenRouter의 server-key availability와 모델 ID만 반환한다. 키 값은 반환하지 않는다.
- `GET /problem-generator/wiki/api/ai/openrouter/free-models`: 백엔드가 OpenRouter 공식 `/api/v1/models`를 조회해 무료 text-generation 모델을 반환한다. `:free` 또는 prompt/completion 가격이 모두 0인 모델을 무료로 판정하며 `id`, `name`, `contextLength`, `structuredOutput`만 노출한다. 조회 실패 시 마지막 성공 cache 또는 빈 목록을 반환한다.
- `POST /problem-generator/wiki/api/questions/generate`: `provider=auto|gemini|openrouter-free|ox-alpha|openrouter:<model-id>`를 받는다. 일반 UI는 API 키를 전송하지 않으며 서버 `GEMINI_API_KEY`/`OPENROUTER_API_KEY`를 사용한다. legacy request key 필드는 관리자·개발 호환용 optional override로만 유지한다.
- `auto`만 Gemini → OpenRouter 무료 자동 fallback을 허용한다. 명시적으로 고른 provider/model은 실패해도 다른 결과로 바꾸지 않는다. `openrouter-free`는 `openrouter/free`, Ox Alpha는 `stealth/ox-alpha`를 호출한다.
- 성공 응답은 실제 `provider`, `model`을 반환한다. 키 원문은 응답·DB·로그에 저장하지 않는다.
- `POST /problem-generator/wiki/api/questions/ab-compare`: 같은 school/grade/exam/twin/types/grammar/passage 계약을 Gemini와 Ox Alpha에 각각 전송하고 provider별 `resultId`, `model`, 검증된 questions를 반환한다. 한 provider 실패 시 다른 결과와 provider별 오류를 반환한다.
- `POST /problem-generator/wiki/api/questions/ab-evaluations` 및 `GET .../ab-evaluations`: 기존 A/B 평가 저장·조회 계약을 유지한다.

### ✅ GET /api/problem-generator/materials

- 목적: 현재 사용자가 접근 가능한 자료 목록을 페이지로 조회한다.
- 인증/권한: 인증 필수. 결과는 `본인 PRIVATE + 전체 PUBLIC`만 포함한다.
- Query: `page`, `size` (`Pageable`, 기본 size 20).
- Response: `200 OK`, Spring `Page<MaterialResponse>` JSON.

### ✅ GET /api/problem-generator/materials/{materialId}/file?page=N

- 목적: 접근 가능한 자료 파일을 inline bytes로 조회한다. PDF 저장 키의 경우 `page`는 1-based 페이지 번호다.
- 인증/권한: PUBLIC이거나 업로더 본인이거나 ADMIN일 때만 200. 타인 PRIVATE는 403.
- Response: `200 OK`, 이미지 bytes. 읽기 실패/없는 자료는 404.

### ✅ DELETE /api/problem-generator/materials/{materialId}

- 목적: 자료 row와 storage 객체를 삭제한다.
- 인증/권한: 업로더 본인 또는 ADMIN. 타인 PRIVATE/PUBLIC 삭제 시도는 403.
- Response: `204 No Content`. storage 삭제는 best-effort이며, 이미 사라진 객체는 성공으로 간주한다.

### ✅ POST /api/admin/problem-generator/materials

- 목적: ADMIN이 공용(PUBLIC) Digital Twin 자료를 업로드한다.
- 인증/권한: `ROLE_ADMIN` 전용(`/api/admin/**`). STUDENT/TUTOR는 403.
- Content-Type / Request / Response: `POST /api/problem-generator/materials`와 동일하되 `visibility=PUBLIC`으로 저장한다.

### ✅ PATCH /api/admin/problem-generator/materials/{materialId}/visibility

- 목적: ADMIN이 자료 공개 범위를 PRIVATE/PUBLIC으로 변경한다.
- 인증/권한: `ROLE_ADMIN` 전용.
- Request:
  ```json
  { "visibility": "PUBLIC" }
  ```
- Response: `200 OK`, 갱신된 `MaterialResponse`. 없는 자료는 404.

### 관련 코드

- `domain/problemgenerator/` — `ProblemGeneratorPageController`, `ProblemGeneratorMaterialController`, `ProblemGeneratorGeminiController`, `ProblemGeneratorMaterialService`, `GeminiProblemGenerationService`, `ProblemGeneratorMaterial`, `MaterialVisibility`.
- 업로드 공용 컴포넌트: `infrastructure/storage/UploadedFileIngestor`.
- 화면: `templates/problem-generator/page.html`, `fragments/_problem_generator_shell.html`, `static/js/problem_generator.js`, `static/css/problem_generator.css`.
- 마이그레이션: `V945__add_problem_generator_materials.sql`.

### Wiki 고3 수능 영어 API

- `POST /problem-generator/wiki/api/csat/uploads` (`multipart/form-data`): `exam_kind=half|full`, `metadata_json`, `file`을 받는다. HWP 5.x OLE/HWPX/JSON/TXT를 서버에서 추출해 canonical 문항과 실패 목록을 검수용으로 반환하며 아직 저장하지 않는다. HWP는 `olefile`과 HWP 레코드/zlib 추출을 사용한다. 암호화·배포용·구형 HWP 3.x·손상 파일은 `400`, 30MB 초과는 `413`, 미지원 확장자는 `415`다. 키/원본 파일은 저장하지 않는다.
- `POST /problem-generator/wiki/api/csat/imports` (`application/json`): 검수·수정한 canonical 문항, source SHA-256, parser, metadata, 추출 원문을 SQLite에 저장한다. 같은 `(source_hash, exam_kind)`는 `409`다. 현재 owner scope는 `shared`이며 원본 파일은 저장하지 않는다.
- `GET /problem-generator/wiki/api/csat/types`: 18~45 번호별 표준 유형 메타데이터를 반환한다.
- `GET /problem-generator/wiki/api/csat/questions`: `exam_kind`, `question_no`, `type_id`, `mismatch` 필터로 저장 문항을 조회한다. 번호와 발문/구조가 어긋나면 `type_mismatch`, `mismatch_reason`을 포함한다.
- `GET /problem-generator/wiki/api/csat/exam.html`: `ids`, `exam_kind`, `title`로 BuildIt 표기의 A4 2단 인쇄 HTML을 렌더링한다. 페이지당 4문항을 목표로 긴 문항 `break-inside`를 방지하고, 본문 편집·문항 순서 이동·삭제·인쇄·편집 HTML 다운로드를 지원한다.
- `POST/GET /problem-generator/wiki/api/csat/wrong-answers`: 기존 `student_wrong_notes`와 분리된 고3 수능 오답을 학생·시험·문항번호·표준유형·오답·정답·메모로 저장/조회한다.

외부지문 API는 `/passages/memory`와 `/passages/analysis` 두 작업만 제공한다. analysis의 문장 데이터는 `slashed → directTranslation → translation → grammarGuide` 순서이며 영어 원문 전체를 별도 중복 필드로 반환하지 않는다.

## 17. BUILD IT CHART

### ✅ GET /chart

- 목적: BuildIt Chart 공개 화면. **지역 선택 → BuildIt이 발견한 우리 동네 영어쌤**이 1차 진입이고, BuildIt TOP100 은 그 아래 큐레이션 섹션이다. 서비스명은 계속 BuildIt Chart.
- 현재 사용처: Thymeleaf SSR shell + JavaScript fetch (`/js/chart_public.js`).
- 인증/권한: public.
- Response: `templates/public/chart.html`.
- 데이터: `/api/chart/regions`(지역 선택), `/api/chart/educators/search`(지역 교육자), `/api/chart/rankings`(지역·전국 순위), `/api/chart/criteria`(평가 기준), `/api/chart/publication/current`(TOP100). 교육자 카드/행은 `/chart/educators/{id}` 로 연결(modal 없음).

### ✅ GET /admin/chart

- 목적: BUILD IT CHART Google Discovery V2 관리자 화면.
- 현재 사용처: Thymeleaf SSR shell + JavaScript fetch.
- 인증/권한: `ROLE_ADMIN`.
- 기능: 검색 설정 수정/기본값 복원, 압구정·신사 V2 검색 실행, 후보 원문 확인, 승인/제외.

### ✅ GET /api/chart/zones

- 목적: 공개/관리 공통 검색권역 목록 조회.
- 인증/권한: public read.
- Response: `[{ id, name, searchAreas[], schoolTerms[], allowed }]`.

### ✅ GET /api/chart/educators

- 목적: 공개 BUILD IT CHART 기본 권역(압구정·신사) TOP 100 조회.
- 인증/권한: public read.
- Response: `ChartEducatorResponse[]` — rank, 이름, 원장/강사, 소속, 지역, 승인 시각, 원문 활동 링크.
- 보장: `chart_educators` 승인 row만 반환한다. Google 후보(`chart_candidates`)는 승인 전 공개되지 않는다.

### ✅ GET /api/chart/zones/{zoneId}/educators

- 목적: 특정 권역 공개 TOP 100 조회.
- 인증/권한: public read.
- 현재 범위: 데이터 생성은 압구정·신사만 허용. 다른 zone은 빈 목록 가능.

### ✅ GET /api/chart/educators/search  (PR-B)

- 목적: 전국에 흩어진 초·중·고 영어교육자 공개정보를 일반 사용자가 검색한다. **TOP100 publication(`/publication/current`)과 분리된 교육자 아카이브 검색**이다. 학원 directory가 아니라 교육자 개인의 정체성·교육철학 중심으로 반환한다.
- 인증/권한: public read (`GET /api/chart/**`).
- Query params (모두 선택): `province`(시·도 정확일치), `cityDistrict`(시·군·구 정확일치), `q`(이름·학원/활동기관·활동지역·교육 정체성 부분일치, LIKE 메타문자 이스케이프), `status`(`EVALUATABLE`|`RESEARCH_PENDING`, 생략 시 둘 다), `page`(0-base, 음수→0), `size`(기본 20, 최대 100).
- 공개 대상: `eligibility_status IN ('EVALUATABLE','RESEARCH_PENDING')` 이고 `excluded_from_chart` 아님. `RESEARCH_PENDING` 도 결과에 포함되나 `researchPending:true` 로 표시되고 **Score·순위는 어떤 필드로도 반환하지 않는다**.
- Response: `{ content: [{ id, name, role, academyName, province, cityDistrict, activityRegion, identitySummary, educationPhilosophy, eligibilityStatus, researchPending, profileImageUrl, lastResearchedAt }], totalElements, totalPages, page, size }`.
- 정렬: 최근 조사(`updated_at`) 내림차순, `candidate_id` tie-break. Score 정렬 아님.
- `status` 가 허용값이 아니면 `400`.

### ✅ GET /api/chart/educators/{id}  (PR-B)

- 목적: 공개 교육자 상세. `{id}` = `candidate_id`(숫자). DB에 **실제 확인된 사실만** 반환하며 없는 값은 `null`/빈 배열(생성·추측 금지).
- 인증/권한: public read. 공개 대상이 아니거나(비제외·공개 eligibility 아님) 존재하지 않으면 `404`.
- Response: `{ id, name, role, academyName, province, cityDistrict, activityRegion, identitySummary, educationPhilosophy, curriculum, distinctiveTeaching, recentActivity, strengths[], improvementSuggestions[], officialChannels{homepage,blog,youtube,social}|null, profileImageUrl, evidence[{contentType, summary, sourceUrl, activityDate, evidenceKind}], discoveryReason, lastResearchedAt, eligibilityStatus, researchPending, disclaimer }`.
- `evidence` 의 `sourceUrl` 은 조사 시 검증된 공개 http(s) 출처만(기존 정책). 광고·모집 문구를 전문성으로 표현하지 않는다.
- 비노출: 개인 전화번호·이메일·상세주소, `person_key`, `source_url_hash`, `eligibility_reasons_json`(내부 판정), `deep_search_result`·`exclusion_check`·`google_query`(내부 조사 메모), `legacy_metadata_json`, `excluded_reason`, `created_by`/`updated_by`, BuildIt Score·TOP100 순위.
- `researchPending:true` → `disclaimer` 가 "평가 근거 미확인, Score·TOP100 대상 아님, 확인된 사실만 표시" 를 명시한다.

### ✅ GET /api/chart/regions  (PR-C)

- 목적: 공개 검색이 가능한(= BuildIt 이 이미 발견해 기록한) 지역 목록. 지역 선택 UI 를 채운다. 새 지역 DB 체계가 아니라 `chart_candidates` 의 공개 대상 행에서 집계한 것.
- 인증/권한: public read.
- Response: `{ provinces: [{ province, educatorCount, cityDistricts: [{ cityDistrict, educatorCount, evaluatableCount, researchPendingCount }] }] }` (시·도·시군구 오름차순).

### ✅ GET /api/chart/rankings  (PR-C)

- 목적: BuildIt Ranking 목록. **publication 과 독립** — TOP100 publication 이 아니라 `chart_candidates`(공개 대상) × candidate 별 최신 유효 Score snapshot 에서 직접 계산. Score 재계산·publication 재생성 없음. pool = `(eligibility_status='EVALUATABLE' OR legacy_source IS NOT NULL)` AND 미제외 AND PR-A 의 grandfather 규칙(`PUBLISHABLE_PROFILE`) AND 최신 Score snapshot 존재. RESEARCH_PENDING / Score 없는 후보 자동 제외. publication 에 아직 없는 scored 교육자도 순위에 포함된다(전체 Ranking 이 100명을 넘을 수 있음).
- 인증/권한: public read.
- Query: `province` 없으면 **전국 순위 상위 `limit`**(기본·최대 100 = BuildIt TOP100 큐레이션). `province`(+선택 `cityDistrict`)가 있으면 **해당 지역 순위**(지역 순위 오름차순). `province` 만 있고 값이 비면 400.
- 순위 규칙: pool 을 `total_score DESC, candidate_id ASC`(= `autoPublishNationwide` 와 동일 규칙, 동점은 candidate_id 오름차순 — 우열의 의미 아님) 로 정렬한 위치. 전국 순위 = pool 안의 위치(1..N). 지역 순위 = 같은 (province, cityDistrict) 안에서의 위치. 지역별로 평가 기준·가중치를 바꾸지 않는다. `top100 = overallRank <= 100`(현재 데이터에서는 기존 publication RANKED 집합과 동일).
- Response: `{ scope: "NATIONWIDE"|"REGION", province, cityDistrict, criteriaVersion, total, educators: [{ id, name, role, academyName, province, cityDistrict, activityRegion, identitySummary, educationPhilosophy, profileImageUrl, buildItScore, overallRank, overallTotal, regionRank, regionTotal, top100 }], disclaimer }`.

### ✅ GET /api/chart/rankings/{id}  (PR-C)

- 목적: 교육자 1명의 BuildIt Ranking(상세 페이지용). `{id}` = `candidate_id`(숫자).
- 인증/권한: public read. Ranking pool 에 없는 교육자(RESEARCH_PENDING·미채점·excluded·미존재)는 **항상 200 `{ id, ranked:false, disclaimer }`** (순위·점수 필드는 모두 `null`).
- Response(ranked): `{ id, ranked:true, buildItScore, overallRank, overallTotal, regionRank, regionTotal, province, cityDistrict, top100, criteriaVersion, disclaimer }`.

### ✅ GET /api/chart/criteria  (PR-C)

- 목적: BuildIt AI 평가 기준 공개("AI는 무엇을 보고 평가하나요?"). 항목·가중치는 실제 `ChartSiteToolService.score()` 산식과 1:1(테스트로 검증). 사용자-facing 라벨/설명은 코드 의미를 왜곡하지 않는 한국어.
- 인증/권한: public read.
- Response: `{ criteriaVersion, maxScore:100, criteria: [{ key, label, weight, description }] (합계 100), notEvaluated: [광고·규모·유명세·프랜차이즈·광고성 표현·학생 성과 등], rankingDisclaimer, scoreDisclaimer }`.
- `rankingDisclaimer` 핵심: "BuildIt Ranking은 선생님의 실제 수업 능력 순위가 아닙니다."

### ✅ GET /chart/educators/{id}  (PR-C)

- 목적: 교육자 공개 상세 페이지 — 공유·뒤로가기·검색엔진용 안정 URL. Thymeleaf SSR 로 제목·메타·기본 식별 정보만 심고, 실제 데이터는 클라이언트가 `/api/chart/educators/{id}` + `/api/chart/rankings/{id}` 로 채운다.
- 인증/권한: public (SecurityConfig `/chart/educators/**`). 비공개/미존재 교육자는 `/chart` 로 redirect.

### ✅ GET /api/admin/chart/settings

- 목적: 관리자 검색 설정 조회.
- 인증/권한: `ROLE_ADMIN`.
- Response: `{ searchZone, includeActivities, excludedTargets, candidateLimit, deepSearchEnabled, extraInstruction }`.

### ✅ PUT /api/admin/chart/settings

- 목적: 관리자 검색 설정 수정. 코드 수정 없이 검색 지시를 조정한다.
- 인증/권한: `ROLE_ADMIN`.
- Request JSON: `searchZone`, `includeActivities`, `excludedTargets`, `candidateLimit`, `deepSearchEnabled`, `extraInstruction`.
- Response: 갱신된 settings.
- 검증: `candidateLimit`은 1~10으로 제한, `searchZone`은 지원 zone이어야 한다.

### ✅ POST /api/admin/chart/settings/reset

- 목적: 검색 설정을 압구정·신사 기본값으로 복원.
- 인증/권한: `ROLE_ADMIN`.


### ✅ POST /api/admin/chart/llm-key

- 목적: `/admin/chart`에서 Gemini 또는 ChatGPT(OpenAI) 테스트 키를 서버 메모리에 임시 적용.
- 인증/권한: `ROLE_ADMIN`.
- Request JSON: `{ provider: "gemini" | "openai", apiKey: "..." }`.
- 보안: DB/Git/브라우저 저장소에 저장하지 않고 서버 재시작 시 사라진다. 응답은 설정 여부/출처/provider/model만 반환한다.

### ✅ POST /api/admin/chart/llm-key/clear

- 목적: 서버 메모리 임시 LLM 키 초기화.
- 인증/권한: `ROLE_ADMIN`.

### ✅ GET /api/admin/chart/candidates?zoneId=apgujeong-sinsa

- 목적: 후보별 이름/역할/소속/지역/발견 콘텐츠/Deep Search/원문 링크/판정/활동 근거 조회.
- 인증/권한: `ROLE_ADMIN`.
- Response: `ChartCandidateResponse[]`.

### ✅ POST /api/admin/chart/discovery/run

- 목적: 현재 settings 기준으로 Google Educator Discovery V2 실제 검색 실행.
- 인증/권한: `ROLE_ADMIN`.
- 외부 호출: 서버에서만 `GEMINI_API_KEY`/`GEMINI_MODEL` 또는 `OPENAI_API_KEY`/`OPENAI_MODEL` 환경변수를 읽는다. 관리자가 `/admin/chart`에서 Gemini 또는 ChatGPT(OpenAI) 키를 입력하면 서버 메모리 임시 키로 해당 provider를 사용한다. 프론트로 키를 재전달하지 않는다.
- Timeout: LLM 호출마다 120초.
- 현재 범위: `apgujeong-sinsa`만 허용. 검색 품질 확인 전 강남구 전체/다른 권역 실행은 400으로 차단.
- Response: 호출 수, 원천 후보 수, 중복 병합 수, VERIFIED/REVIEW_REQUIRED/제외/저장 수, webSearchQueries, 저장 후보/제외 후보 요약.

### ✅ POST /api/admin/chart/candidates/{candidateId}/approve

- 목적: 후보를 교육자로 승인하고 `chart_educators`에 upsert.
- 인증/권한: `ROLE_ADMIN`.
- Response: 갱신 후보.
- 보장: 이 액션이 공개 Chart 반영의 유일한 경로다.

### ✅ POST /api/admin/chart/candidates/{candidateId}/reject

- 목적: 후보 제외.
- 인증/권한: `ROLE_ADMIN`.
- Response: 갱신 후보.

### ✅ POST /api/admin/chart/candidates/{candidateId}/enrichment  (PR-D)

- 목적: **이미 저장된 교육자(`chart_candidates`)의 Educator Intelligence 보완**. 신규 발굴 저장(`saveDraft`, INSERT)과 분리된 **기존 `candidate_id` 전용 UPDATE**. 서울 legacy 41명처럼 요약 필드가 비어 있는 교육자를 이후 재조사해 근거와 함께 채워 넣는 경로다.
- 인증/권한: `ROLE_ADMIN`. WebMCP/AI tool 로 노출하지 않는다(관리자 REST 전용).
- Request body: `{ educationPhilosophySummary?, curriculumSummary?, distinctiveTeachingSummary?, recentActivitySummary?, strengths?: string[], improvementSuggestions?: string[], overwrite?: boolean, evidence?: [{ contentType, contentSummary, sourceUrl, activityDate?, evidenceKind?, educationallyInformative? }] }`.
- 병합 규칙:
  - 요약 4필드: 비어 있으면 채우고, 이미 값이 있으면 `overwrite:true` 일 때만 교체한다. **null/blank 로는 절대 기존 값을 지우지 않는다.**
  - `strengths` / `improvementSuggestions`: 기존 항목을 보존한 채 합집합으로 누적(삭제 없음).
  - `evidence`: `chart_candidate_activities` 에 원 출처 URL 로만 추가(누적, `(candidate_id, source_url)` 중복은 무시). **네이버 AI·검색 결과 URL, Gemini grounding redirect(`vertexaisearch.cloud.google.com`), placeholder(`buildit.example`) 는 400 으로 거부.** 기존 Evidence 는 삭제하지 않는다.
- 변경 안 함(요청 record 에 해당 필드가 없음): Score/Score snapshot, Ranking, `eligibility_status`, publication, `excluded_from_chart`, `legacy_source`, identity(`name`·`academy_name`·`activity_*`·`person_key`·`zone_id`). `saveDraft` 의 교육철학·커리큘럼 2~3문장 평가 게이트는 적용하지 않는다(확인된 필드만 부분 보완).
- 오류: 빈 요청(요약·리스트·근거 모두 없음) → 400. 존재하지 않는 `candidateId` → 404. 원 출처 아닌 Evidence URL → 400.
- Response: `{ candidateId, changedFields: string[], evidenceAdded, evidenceSkippedDuplicate, eligibilityStatus, updatedAt }`.

### ✅ POST /api/admin/chart/tools/candidates/{candidateId}/reevaluate (RESEARCH_PENDING 재평가)

- 목적: RESEARCH_PENDING 후보를 현재 저장 데이터로 재평가한다. 최초 판정과 동일한 `assess` 기준을 재사용하며, PASS일 때만 EVALUATABLE로 전환하고 FAIL이면 유지한다. enrichment만으로는 승격되지 않는다.
- 인증/권한: `ROLE_ADMIN` 전용 (Research Agent allowlist 밖).
- Query: `dryRun` (기본 true — 조회만, 쓰기 없음).
- 변경 (승격 시, dryRun=false): `eligibility_status`·`eligibility_reasons_json`·`updated_at` 세 컬럼만. profile/evidence 불변.
- Response: `{ candidateId, dryRun, assessmentStatus, promote, applied, before, after, reasons, passReasons, holdReasons, rejectReasons }`.

### ✅ GET /api/admin/chart/tools/scores/stale (재채점 대상 조회, 읽기 전용)

- 목적: 최신 profile/evidence 변경이 최신 Score snapshot보다 새로운 후보(= review → snapshot → publication 후속 처리 대상)를 식별한다. 스키마 변경 없이 `updated_at`/`researched_at` vs `calculated_at` 비교다. 아무 것도 변경하지 않는다.
- 인증/권한: `ROLE_ADMIN` 전용 (Research Agent allowlist 밖).
- Response: `[{ candidateId, name, academyName, eligibilityStatus, lastDataChange, lastSnapshotAt }]` (lastSnapshotAt null = snapshot 없음).

### 🧪 Chart Site tool 1차 계약 (`/api/admin/chart/tools/**`)

모든 endpoint는 기본 `ROLE_ADMIN` 전용이다. 단, 조사 파이프라인 최소 범위는 Research Agent도
`CHART_AGENT_TOKEN` Bearer 토큰으로 호출할 수 있다(신규 인증 연결, 기존 JWT 쿠키·WebMCP 경로 유지).
ChatGPT가 공개 웹 조사를 담당하며 이 API가 다시 Gemini/OpenAI 검색을 호출하지 않는다.

`/admin/chart`는 지원 브라우저에서 `document.modelContext.registerTool()`로 아래 REST 계약을 WebMCP Site tools로 등록한다. Site tools는 현재 페이지의 ADMIN 로그인 세션과 `fetchAuth` 인증을 그대로 사용하며 별도 API key, MCP 서버 또는 우회 권한을 만들지 않는다. 페이지를 닫거나 로그아웃하면 도구도 사용할 수 없다.

| Site tool | 권한 | REST 연결 |
|---|---|---|
| `get_chart_context` | read | `GET /context` |
| `get_next_regions` | read | `GET /regions/next` |
| `claim_region` | Write | `POST /regions/{regionId}/claim` |
| `complete_region` | Write | `POST /regions/{regionId}/complete` (서버 자동 발행 트리거) |
| `search_existing_educators` | read | `POST /educators/search-existing` |
| `save_research_drafts` | Draft Write | 각 초안을 `POST /research-drafts`로 검증·저장 |
| `preview_buildit_scores` | read | 각 후보를 `GET /scores/preview`로 결정론적 계산 |
| `submit_chart_review_batch` | Audit Write | `POST /review-batches` (audit record) |
| `inspect_chart_review_batch` | read | `GET /review-batches/{reviewBatchId}` (사고 조사) |
| `revert_chart_review_batch` | Recovery Write | `POST /review-batches/{reviewBatchId}/revert` (잘못 발행 batch 되돌리기) |

publication Site tool은 등록하지 않는다. publication은 AI/WebMCP 직접 명령으로 만들 수 없고(`POST /publications`는 항상 `409`), 정상 조사 파이프라인 완료(`complete_region`) 시 서버 내부 workflow가 전국 누적 snapshot을 발행한다.

#### Research Agent 인증 (`CHART_AGENT_TOKEN`)

- 브라우저가 없는 Research Agent(Muse/OpenCode)는 관리자 JWT 쿠키를 가질 수 없으므로, 서버 환경변수
  `CHART_AGENT_TOKEN` 과 일치하는 `Authorization: Bearer <token>` 헤더로 조사 파이프라인만 호출한다.
- 허용 범위(그 외는 `ROLE_ADMIN` 전용으로 401/403): `GET /context`, `GET /regions/next`,
  `POST /regions/{id}/claim`, `POST /educators/search-existing`, `POST /research-drafts`,
  `GET /scores/preview`, `POST /review-batches`, `GET /review-batches`,
  `GET /review-batches/{id}`, `POST /regions/{id}/complete`.
- 제외(allowlist 밖 — 토큰이 무시되어 익명과 동일하게 401): `decision`/`finalize`/`revert`/`rebuild`/
  `publications`/`web-draft-requests` 및 `/api/admin/chart` 일반 관리 API.
- 토큰 미설정 시 에이전트 경로는 완전히 비활성화된다(fail-closed). 토큰은 코드·저장소에 넣지 않고
  운영 환경변수(EC2 `/etc/f2r/env` 등)로만 주입한다. 감사 추적용 principal은 `chart-agent`.
- 기존 Service·validation·scoring·duplicate check를 그대로 통과하므로 별도 import API를 만들지 않는다.

- `GET /context`: 조사 파이프라인, 저장 최소 게이트 / 평가 게이트, 제외 정책(학원 규모·프랜차이즈·광고량·유명세는 제외 사유 아님), Score 가중치, 비평가 신호, 개인정보·공개 고지를 조회한다. 범위: 대한민국 초·중·고 영어교육. read.
- `GET /regions/next?limit=1`: 신규 조사 또는 재조사 예정(`DUE`)/오류 지역만 조회한다. `RESEARCHED`는 제외. `chart_regions`는 V952로 대한민국 전국 시·군·구가 seed되어 있고 지역 추가는 migration seed로만 한다. read.
- `POST /regions/{regionId}/claim`: `UNRESEARCHED`/`DUE`/`ERROR` 지역을 `IN_PROGRESS`로 점유한다. Write.
- `POST /regions/{regionId}/complete`: body `{selectedCandidateIds, selectedCount, rejectedCount, note}`. **`selectedCandidateIds` 에 넣은 후보만** 이 지역 최종 선정자로 인정한다 — `save_research_drafts` 저장은 후보 풀 등록일 뿐 선정이 아니며, `zone_id` 로 draft 를 재유도하지 않는다. 각 후보는 이 지역(`zone_id`) 소속·`EVALUATABLE`(또는 Legacy grandfather)·미제외여야 하며 아니면 `422`(`RESEARCH_PENDING` 후보는 선정 불가). 빈 목록이면 0명으로 조사 완료(`RESEARCHED_ZERO` audit batch 만 남기고 publication 무변경). 비어있지 않으면 audit batch + 현행 Score snapshot 생성 후 전국 누적 publication(직전 snapshot ∪ 이번 선정자)을 재생성하고 지역을 `RESEARCHED`(+`re-research-days`일 뒤 재조사)로. 단일 트랜잭션, 실패 시 전체 롤백. `selectedCount` 는 교차 확인용(불일치 시 응답에 표기). Write.
- `POST /educators/search-existing`: 이름·학원 기반으로 기존 후보/교육자를 조회한다. read.
- `POST /research-drafts`: 시/도·시군구, 교육 정체성, 교육철학·커리큘럼 요약(선택 — 부족하면 `RESEARCH_PENDING` 저장), 고유 수업 방식, 최근 활동 요약, 강점/개선 제안, 프로필 이미지 URL, 공개 공식 채널, 최소 게이트 확인값, 실제 evidence URL·활동일·종류를 저장한다. Draft Write. **최소 게이트**(교육자 개인 식별 / `exclusionFree` = 현재 초·중·고 현장 영어교육 범위 / 공개 근거 URL) 실패만 `422`(행 생성 안 함). "공개 근거 URL" 은 evidence 배열 중 **실제 유효한 공개 http(s) URL** 이 하나라도 있으면 통과하며, 그 evidence 를 대표 evidence 로 사용한다(`evidence[0]` 고정이 아님). 유효 URL 이 없는 evidence 행은 저장 시 건너뛴다(보정·추측 없음). **평가 게이트**(현재 오프라인 활동·최근 연도 근거·교육철학·커리큘럼·비광고 교육 근거) 실패는 `eligibility_status='RESEARCH_PENDING'` 으로 저장되고 검색에 노출되나 점수·TOP100 대상이 아니다. 응답: `candidateId`, `eligibilityStatus`(`EVALUATABLE`|`RESEARCH_PENDING`), `evaluationHold`, `holdReasons`, `skippedEvidence`(건너뛴 evidence 행 수), `published:false`. 중복은 `409`. 학원 규모·프랜차이즈·광고량·유명세는 거부·감점 사유가 아니다.
- `GET /scores/preview?candidateId=`: 저장된 근거로 결정론적 Score와 potential signals를 계산한다. `EVALUATABLE`(또는 Legacy) 후보만 — `RESEARCH_PENDING` 은 `422`. DB를 변경하지 않는 preview.
- `POST /review-batches`: 조사 audit record(`AUDIT_RECORDED`) + 후보별 Score snapshot을 남긴다. 관리자 사전 승인 gate가 아니며 공개 데이터를 변경하지 않는다.
- `GET /review-batches`: 최근 조사 audit batch를 조회한다(최대 100건). read.
- `POST /review-batches/{reviewBatchId}/decision`: 관리자가 audit batch를 `APPROVED`/`REJECTED`로 표기한다(사후 품질관리). 공개는 변경하지 않는다.
- `POST /review-batches/{reviewBatchId}/finalize`: 이미 Draft/Score/Batch가 있는 조사(예: 분당 Review Batch 1)를 새 자동발행 코드에서 publication 단계부터 resume한다. 새 Draft/Score를 만들지 않고, 현행 Hard Gate(교육철학·커리큘럼 요약 보유, Legacy 선정자는 grandfather) 통과 후보만 전국 snapshot에 포함한다. 멱등(이미 발행됐으면 `ALREADY_FINALIZED`). 서버/관리자 트리거용(WebMCP 도구 아님).
- `POST /educators/{candidateId}/web-draft-requests`: 교육자 동의 시각을 필수로 받아 Chart 기록의 BuildIt Web draft 요청 payload를 만든다. 가입 여부는 Score에 반영하지 않는다.
- `POST /publications`: 항상 `409`. publication은 AI 직접 명령이 아니라 조사 파이프라인 완료의 서버 결과다.
- `POST /publications/rebuild`: 전국 누적 publication (재)생성. `PUBLISHED` 0건이면 PUBLISHABLE 한 기존 선정자(Legacy import·분당 batch) 전체로 첫 snapshot; 이미 있으면 직전 snapshot 교육자(제외자 제거) 기준으로만 재생성한다. 서버/관리자 트리거(AI/WebMCP 아님), `ROLE_ADMIN`. 앱 시작 시 `ChartInitialPublicationRunner` 가 `PUBLISHED` 0건이면 1회 자동 호출.
- `GET /review-batches/{reviewBatchId}`: 사고 조사용. batch 에 연결된 candidate/score snapshot/publication entry 를 조회한다(읽기 전용). 응답: batch id·status, `scoreSnapshots[]`(candidateId·name·zoneId·activityRegion·legacySource·totalScore), `publicationEntries[]`. `ROLE_ADMIN`. WebMCP: `inspect_chart_review_batch`.
- `POST /review-batches/{reviewBatchId}/revert`: 잘못 발행된 batch 를 안전하게 되돌린다. 해당 batch 의 `chart_publication_entries`·`chart_score_snapshots` 만 제거하고 batch 를 `REVERTED` 로 표시한 뒤 서버가 직전 정상 명단으로 전국 publication 을 재생성한다(별도 rebuild 호출 불필요). entry 가 제거된 candidate 라도 **다른 정상 review batch 의 snapshot 이 남아 공개 자격을 유지하면 계속 게시**되고, 이 batch 로만 유입된 candidate 만 빠진다. `chart_candidates`(Living Archive)·activity·Legacy 데이터·Score 산식·`chart_regions` 상태(`RESEARCHED` 유지)는 변경하지 않는다. 이미 `REVERTED` 면 아무 것도 바꾸지 않는다(idempotent, `alreadyReverted:true`). 응답: `removedPublicationEntries`, `removedScoreSnapshots`, `affectedCandidateIds`, `candidatesKeptElsewhere`, `finalPublicationId`, `finalPublishedEducatorCount`. `ROLE_ADMIN`. WebMCP: `revert_chart_review_batch`.

### ✅ GET /api/chart/publication/current

- 목적: `/chart`와 메인 TOP5가 같은 최신 `PUBLISHED` 전국 누적 snapshot을 읽는다(source of truth). 홈은 `rankedTop100[0:5]`, `/chart`는 전체.
- 응답: `publicationId`, `updatedAt`(publication `published_at`, 하드코딩 금지), `updatedLabel`("최근 업데이트 YYYY.MM.dd"), `weeklyUpdateDate`(하위호환), `rankedTop100`/`entries`(최대 100, `chartRank`·`previousRank`·Score·교육철학·커리큘럼·강점·개선제안·활동·검증근거), `discovered`(순위 밖 Hard Gate 통과 교육자 = BUILD IT DISCOVERED), `promisingEducators`, `disclaimer`. 개인정보 필드 없음.
- 호환: 아직 발행본이 없으면 기존 승인 교육자 목록을 legacy 응답으로 제공한다.

`research-drafts`는 전화번호, 이메일, 개인주소 입력 필드를 제공하지 않는다. 공식 URL은 `http(s)`만 허용한다. 현재 연도 판정은 서버 실행 연도를 사용하며 2026을 하드코딩하지 않는다.

### Legacy Chart V3 재조사 경계

`scripts/export-legacy-chart-v3.py <app.db>`는 Legacy SQLite를 read-only URI로 열어 `125 candidates / 43 verified educators` 계약을 검증하고, VERIFIED 43명을 `PENDING_CURRENT_RESEARCH` JSON 큐로 내보낸다. 출력은 현행 DB import나 승인을 수행하지 않는다.

- 전화번호·이메일·상세주소는 출력 스키마에서 제외한다.
- `legacy_score`와 breakdown은 역사적 기록으로만 표시하며 현행 Score로 사용할 수 없다.
- 모든 항목은 `auto_approved=false`, `published=false`다.
- 실제 현행 후보 저장은 WebMCP 조사 → Hard Gate → 관리자 Review 흐름을 다시 통과해야 한다.


### Wiki AI 오류/timeout 계약

- OpenRouter 단일 호출은 `AI_PROVIDER_TIMEOUT_SECONDS`(기본 90초, 10~120초 범위)로 제한한다. 명시적 `ox-alpha` 선택은 Gemini로 fallback하지 않으며, `auto`에서만 Gemini → Ox Alpha 순 fallback을 허용한다.
- AI 실패 응답은 `detail: { message, provider, status }` JSON으로 정규화한다. 429/502/504의 게이트웨이 HTML이나 외부 provider 원문을 브라우저에 전달하지 않는다.
- 문제 생성 성공 응답과 Assistant 응답은 실제 `provider`, `model`을 포함한다.

# 내신 Wiki V1 자료 API

- `POST /problem-generator/wiki/api/exam-materials` (`multipart/form-data`): `core_type`, 시험 Context,
  선택 학생(`student_wrong_note`일 때), `original`, `json_file` 또는 `json_text`를 받아 원본+JSON 한 세트로 저장한다.
- `GET /problem-generator/wiki/api/exam-materials`: `school`, `grade`, `year`, `semester`, `exam`이 모두 같은 세트만 조회한다.
- `GET /problem-generator/wiki/api/exam-materials/{id}/original`: 원본을 inline으로 연다.
- `GET /problem-generator/wiki/api/exam-materials/{id}/json`: canonical JSON을 연다.
- `POST /problem-generator/wiki/api/students/wrong-restore` (JSON): 오답 사진 AI 복원. 브라우저 OCR(tesseract.js/pdfjs-dist)로 1차 추출한 `pages[{filename, ocrText}]`와 선택 학생 정보를 받아 school-print reconstruct와 동일한 멀티모달 패턴(ai_router, 이미지 있으면 image_data_url 포함)로 **원래 시험 문제 구조**를 복원한 편집 가능 `html`과 `canonicalJson`을 반환한다. 응답은 `reviewMarkCount`/`reviewMarkText`(판독 불확실 표시 개수·안내)와 실제 `provider`/`model`을 포함한다. AI는 새 문제를 만들지 않으며 지문·발문·보기·선택지·밑줄·빈칸을 그대로 복원하고, 불확실 부분은 `<mark class="review-required">검토 필요</mark>`로 남긴다. 이 엔드포인트는 저장하지 않는다(순수 변환). 누적 저장은 승인 후 기존 계약(`POST /exam-materials` `core_type=student_wrong_note` 원본+JSON, `POST /students/{student_id}/wrong-notes` 누적)으로만 수행한다.
- `GET /problem-generator/wiki/api/students/{student_id}/wrong-materials`: 학생별 보관 원본 조회(읽기 전용, 신규 DDL 없음). 오답 사진 AI 복원 승인 저장분이 `exam_material_sets`(`core_type=student_wrong_note`, 사진별 1행)로 보관된 것을 `{id, title, original_name, original_type, created_at}` 목록으로 반환한다. blob 응답은 없고, 원본 열기는 기존 `GET /exam-materials/{id}/original` 계약을 그대로 쓰는다.
- 수업 인사이트 생성/조회는 기존 `educator-insights` API에 `year`, `semester`, `exam`,
  `source=director|school_teacher`를 선택적으로 추가한다. 과거 row의 빈 Context도 안전하게 유지한다.

## 수능 HTML Batch Import

### ✅ POST /api/csat/html-imports/preview

- 목적: 최대 50개의 수능 HTML 파일을 한 번에 받아 파일별 문항을 분리하고 발문 기반 번호 유형 후보를 검수용으로 반환한다.
- 현재 사용처: React Wiki `고3 수능 영어 자료 제작`.
- 인증/권한: Spring Wiki 프록시와 기존 인증 정책을 그대로 따른다. URL query의 `role` 값은 권한 근거로 사용하지 않는다.
- Request: `multipart/form-data`
  - `files`: `.html` 또는 `.htm`, 파일당 10MB 이하, 전체 30MB 이하, 최대 50개, UTF-8.
  - `exam_kind`: `half` 또는 `full`.
  - `title`, `year`, `round`: 선택 메타데이터.
- Response `201`: `file_count`, `question_count`, `review_count`, `files[]`. 각 파일에는 기존 import preview 계약과 `source_question_no`, `candidate_numbers`, `classification_status`, `classification_reason`, `question_html`, `explanation_html`이 포함될 수 있다.
- 저장 정책: preview 단계에서는 SQLite를 변경하지 않는다. 검수 후 기존 `POST /api/csat/imports/commit`으로 파일별 저장하며 HTML fragment는 문항 `raw_json`에 보존한다.
- 실패: 지원하지 않는 확장자/비 UTF-8/제한 초과/문항 미인식은 `400`; 전체 파일이 실패하면 preview를 생성하지 않는다.

## 수능 Assessment와 Usage History

### ✅ POST /api/csat/assessments

- 목적: 기존 `csat_questions` 문항으로 HALF/FULL/WRONG/DAILY 공통 Assessment를 저장한다.
- Request JSON: `assessment_type`, `title`, `template_id`, `question_ids`, `question_no`, `type_id`, `count`, `days`, `exclude_used`.
- DAILY: 목표 문항 수는 `days × 4`이며 저장 item에 1부터 시작하는 `day_no`를 기록한다.
- 사용 이력: Assessment와 item 저장이 성공한 같은 transaction에서만 `csat_question_usage`를 기록한다. 검색·preview·HTML render는 사용 이력을 만들지 않는다.
- `exclude_used=true`: 이미 저장된 Assessment에 포함된 Question ID를 후보에서 제외한다.
- Response `201`: Assessment 메타데이터와 순서가 보존된 `items`, `question_ids`.
- 실패: 후보 부족 또는 사용 이력 제외 후 부족하면 `409`; 부분 Assessment/usage는 저장하지 않는다.

### ✅ GET /api/csat/assessments · GET /api/csat/assessments/{id}

- 목적: Wiki와 WebMCP가 동일한 저장 Assessment를 다시 조회한다.
- 상세 응답의 `items`가 문제·해설 양쪽 renderer의 유일한 Question ID 순서 정본이다.

### ✅ GET /api/csat/question-usage

- Query: `question_id`, `assessment_type` 선택.
- 목적: Question ID별 실제 저장 Assessment 사용 이력을 조회한다.

### ✅ GET /api/csat/assessments/{id}/problem.html · /explanation.html

- 목적: 저장된 동일 Assessment item 순서로 편집 가능한 문제/해설 HTML을 렌더한다.
- DAILY는 두 문서에 동일한 `data-question-id`와 `data-day-no` 순서를 출력한다.
- 호출 자체는 사용 이력을 추가하지 않는다.

### ✅ PATCH /api/csat/assessments/{id}/items/{position}

- 목적: 저장된 Assessment의 한 위치를 다른 기존 Question ID로 교체한다.
- DAILY 문제/해설은 별도 배열이 아니라 갱신된 같은 item을 렌더하므로 함께 교체된다. `day_no`와 position은 유지한다.
- 같은 Assessment 안의 Question 중복은 `409`, 없는 Assessment/item/Question은 `404`.
- 실제 저장 변경이므로 기존 usage를 새 Question ID의 usage로 같은 transaction에서 교체한다.

### ✅ PATCH /api/csat/wrong-answers/{id}/question

- 목적: 기존 `csat_wrong_answers` record를 삭제·대체하지 않고 정확한 `csat_questions.id`에 연결한다.
- 오답 번호와 Question의 수능 번호가 다르면 `400`으로 거부한다.

### ✅ POST /api/csat/wrong-assessments

- Request JSON: `title`, `wrong_answer_ids`, `exclude_used`.
- 목적: 선택한 오답의 연결된 Question ID 순서로 공통 WRONG Assessment를 저장한다.
- 연결되지 않은 오답이나 중복 Question은 `409`; 저장 성공 전에는 usage를 기록하지 않는다.

### DAILY 전용 Renderer

- 저장된 `assessment_type=daily`의 problem/explanation URL은 공식 DAILY Reference를 반영한 전용 Template을 사용한다.
- Problem: 금색 Day 배너, 흰 A4 문제 페이지, Day당 4문항.
- Explanation: 크림색 A4, 금색 강조, 2단 해설.
- 두 HTML은 같은 `csat_assessment_items`에서 동일 `data-question-id`와 `data-day-no` 순서를 출력한다.

## 수능영어 Wiki V1: 평가원 기출과 출제 DNA

내신Wiki의 학교 `digital_twin` DNA와 저장소를 분리한 평가원 중심 API다. SOURCE(원본 문제·정답)와 ANALYSIS(DNA)를 구분하고, 모든 DNA는 CLAIM+EVIDENCE 구조이며 evidence의 `question_no` 클릭으로 실제 문항으로 이동한다. 쓰기는 서명 identity의 ADMIN 역할만 허용한다(`?role=ADMIN` 표시값이 아니라 JWT/identity 역할; 로컬은 unsigned fallback).

### ✅ GET /api/csat/dna/taxonomy

- 목적: DNA 분류 체계(16개 차원 `dimensions`, 10개 함정 `traps`, `provenance`: ACTUAL_EXAM/OFFICIAL_SOURCE/EXPERT_ANALYSIS/WIKI_SYNTHESIS, `statuses`: AI_PROPOSED/TEACHER_CONFIRMED/TEACHER_EDITED/REJECTED, 비교 `verdicts`: REPEATED/STRENGTHENED/REDUCED/NEW/CHANGED, `agreement_kinds`: AGREEMENT/DIFFERENT_EMPHASIS/EXPERT_ONLY/UNKNOWN, `claim_types`, `exam_types`, `resource_types`)를 반환한다.

### ✅ POST /api/csat/exams · GET /api/csat/exams · GET /api/csat/exams/{id}

- Request: `exam_year`(학년도, 2000~2100), `exam_type`(CSAT/JUNE_MOCK/SEPTEMBER_MOCK), `subject`(기본 ENGLISH), `exam_date`, `label`.
- `(exam_year, exam_type, subject)` UNIQUE — 중복이면 `409`. 응답에 `question_count`/`resource_count`/`dna_count`/`claim_count`(REJECTED 제외)와 `expert_count`(해당 회차에 연결된 `provenance=EXPERT_ANALYSIS` DNA+CLAIM 건수)를 포함한다.
- 상세(`GET /{id}`)는 `resources[]`(id, resource_type, original_name, original_type)를 함께 반환한다.

### ✅ POST /api/csat/exams/{id}/resources · GET /api/csat/exam-resources/{id}/original · DELETE /api/csat/exam-resources/{id}

- `multipart/form-data`: `resource_type`(QUESTION_PAPER/ANSWER/ANALYSIS/TEACHER_NOTE) + `file`. 원본 바이트를 `csat_exam_resources.original_blob`에 그대로 보관한다(30MB 초과 `413`, 빈 파일/알 수 없는 종류 `400`).
- `/original`은 저장된 원본을 원래 MIME으로 inline 반환 → 업로드-다운로드 바이트 동일성 검증에 사용한다.
- DELETE는 ADMIN만. DNA와 다르게 원본 자료는 대체 삭제 경로로 관리한다.

### ✅ POST /api/csat/exams/{id}/questions · GET /api/csat/exams/{id}/questions

- Request: `{ questions: [{question_no, csat_type_id?, csat_type_name?, passage, prompt, choices[], answer, solution, footnote, group_label, source_reference}], source_filename }`. `csat_type_id`가 비면 번호로 표준 유형을 자동 분류한다.
- 평가 단위로 `csat_question_sources`(parser `csat-exam-v1`) 1건 + `csat_questions` 행을 `exam_id`로 연결해 저장한다. 문항번호 중복 `400`, 이미 문항이 있는 평가에 재등록하면 `409`(부분 저장이 아니라 전체 거부).
- Query `from_no`/`to_no`로 번호 범위 조회; 각 문항에 REJECTED를 제외한 `dna[]`(evidence 포함)를 붙여 반환한다.

### ✅ POST /api/csat/questions/{id}/dna · GET /api/csat/questions/{id}/dna · PUT /api/csat/dna/{dna_id} · DELETE /api/csat/dna/{dna_id}

- Create 검증: `entry_type`은 DNA/TRAP; DNA tag는 16개 차원, TRAP tag는 10개 함정 집합 소속; TRAP은 `choice_no`(1~7) 필수; `provenance=ACTUAL_EXAM`이면 evidence 필수.
- `provenance=EXPERT_ANALYSIS` 게이트(LEVEL 2): `expert` + `expert_source_id`(실존하는 `csat_expert_sources.id`) + 유효한 `timestamp_start`/`timestamp_end`(초, `end > start ≥ 0`) + `agreement_kind`(AGREEMENT/DIFFERENT_EMPHASIS/EXPERT_ONLY/UNKNOWN) + evidence가 **모두** 필수. 하나라도 빠지면 `400`(evidence 없는 강사 인용 등록 불가). `agreement_kind`는 EXPERT_ANALYSIS에서만 쓰므로 다른 provenance에서 보내면 `400`.
- evidence 항목은 `{question_no: int(1~99), exam_id 또는 exam_year+exam_type}` 형태여야 한다.
- `(question_id, entry_type, tag, choice_no, provenance)` dup은 `409`. provenance가 키에 포함되므로 같은 태그·선지에 평가원 근거와 강사 분석을 별개 행으로 보존한다(덮어쓰기 금지). Create 시 상태는 항상 `AI_PROPOSED`, `updated_by`에 identity login_id를 기록한다.
- PUT(ADMIN 교정 플로우): tag/note/evidence/importance(1~3)/provenance/expert/agreement/agreement_kind/expert_source_id/timestamp_*/status 수정 가능 — `AI_PROPOSED → TEACHER_CONFIRMED/TEACHER_EDITED`, 오기는 `REJECTED`(조회·집계에서 제외). 수정 후 병합 행이 EXPERT_ANALYSIS 게이트를 위반하면 `400`.
- GET 목록·집계· Create/PUT 응답은 REJECTED를 제외하고 반환하며, `expert_source_id`가 있는 행에는 출처 메타데이터(`expert_video_url`/`expert_video_title`/`expert_published_at`)를 붙인다.

### ✅ GET /api/csat/exams/{id}/dna

- 시험별 DNA 집계: `exam`, `tags[]`(entry_type+tag → 근거 question_nos·note·provenance), `traps[]`(함정 우선 정렬), `per_question[]`(문항번호별 entries), `claims[]`(시험 수준 CLAIM). REJECTED 제외. `tags[].notes[]`와 `claims[]`는 강사 분석이면 `agreement_kind`/`timestamp_start`/`video_url`을 함께 담는다(UI는 `[이름 분석 · 합의분류 · mm:ss]` + `&t=초` 영상 구간 링크로 표시).

### ✅ POST /api/csat/expert-sources · GET /api/csat/expert-sources

- LEVEL-2 강사 분석의 **출처**를 등록한다. 자막 전문·영상 파일은 저장하지 않고 URL과 메타데이터(발표일·제목·검증 메모)와 구간(초)만 보존한다.
- Request: `expert`(예: 이명학), `target_exam_id`(실존 평가 필수), `video_url`(`https://(www.)youtube.com/watch?v=…` 형식, 그 외 `400`), `source_type`(현재 `EXPERT_ANALYSIS`만 허용), `video_title`, `published_at`, `verified_note`.
- `(expert, video_url)` dup은 `409`. 쓰기는 ADMIN만(`403`).
- GET은 출처별로 연결된 `claim_count`/`dna_count`/`comparison_count`(REJECTED 제외)를 붙여 반환한다.

### ✅ POST /api/csat/exams/{id}/dna-claims · PUT /api/csat/dna-claims/{claim_id} · DELETE /api/csat/dna-claims/{claim_id}

- `claim_type`(CORE_TRAIT/REPEATED_SKILL/MAJOR_TRAP/HIGH_DIFFICULTY/SKILL_DEMAND) + `claim_text` 필수, `tag`는 DNA∪TRAP 집합(선택).
- `provenance`가 ACTUAL_EXAM/WIKI_SYNTHESIS면 evidence(CLAIM+EVIDENCE 규칙) 필수. §14에 따라 "반드시 나온다"류 단정은 데이터로 저장하지 않는다. `EXPERT_ANALYSIS`면 위 DNA 게이트와 동일하게 expert/expert_source_id/timestamps/agreement_kind/evidence 필수(`400`).
- 주의: dna-claims에는 UNIQUE 제약이 없다 — API 클라이언트(시드 스크립트 등)는 `(claim_type, tag, provenance, expert_source_id)` 선조회로 멱등성을 확보한다.

### ✅ POST /api/csat/dna-comparisons · GET /api/csat/dna-comparisons?exam_a_id&exam_b_id · PUT /api/csat/dna-comparisons/{id}

- 두 시험(예: 6월↔9월)의 차이를 REPEATED/STRENGTHENED/REDUCED/NEW/CHANGED 판정으로 기록한다. `exam_a_id == exam_b_id`면 `400`, evidence 필수, `(exam_a, exam_b, tag, verdict)` dup은 `409`.
- GET은 두 exam id가 필수(둘 다 실존하는 평가여야 함). 비교 행에는 `importance` 컬럼이 없다.
- 비교 문구는 문항 evidence를 가져야 하며(예: 순서 36번이 양 시험 모두 지시어 회상 구조), 근거 없는 출제 예측은 저장하지 않는다. `EXPERT_ANALYSIS` 비교는 위와 같은 expert 게이트(expert/expert_source_id/timestamps/agreement_kind)를 적용한다. 기존 평가원·WIKI_SYNTHESIS 비교와 같은 태그여도 판정(verdict)이 다르면 별개 행으로 **관점 차이로 보존**하고 덮어쓰지 않는다.
- 시드: `scripts/seed_csat_expert_lee.py --base http://127.0.0.1:8000`가 `fixtures/csat_day1/dna_expert_lee.json`(출처 2·CLAIM 12·문항 DNA 12·비교 5 = EXPERT_ANALYSIS 29건)을 멱등하게 등록한다. 기존 ACTUAL_EXAM/OFFICIAL_SOURCE/WIKI_SYNTHESIS 행은 읽기만 한다.

### ✅ GET /api/csat/students/{id}/weakness-dna · POST /api/csat/students/{id}/weakness-dna/analyze

학생 ④ 약점 DNA 화면(「수능영어 Wiki → ④ 학생 약점 DNA」) 전용. SOURCE OF TRUTH는 **③에 등록된 학생 오답 원문 HTML**이고, 연결 근거는 ① 평가원 기출·② 평가원 출제 DNA뿐이다 — 임의 생성 금지, 원문에 없는 정보(학생이 고른 선지 등)는 만들지 않는다.

- `GET .../weakness-dna`: 저장된 분석 결과를 읽는다(읽기 전용, 쓰기 권한 없음). 분석 전에는 `{analysis: null, dna: []}`를 반환한다(가짜 데이터 금지). 학생이 없으면 `404`.
- `POST .../weakness-dna/analyze`: 오답 원문 전체를 재분석해 저장한다 — ADMIN 전용(`_require_csat_admin`, 미서명/비ADMIN `403`). 오답 원문 미등록 `404`, 원문에서 문항을 찾지 못하면 `400`. 재분석은 멱등(upsert analysis + DNA 삭제 후 재insert)이라 결과가 항상 원문 현재 상태와 일치한다.
- 반복 규칙(약점 확정 조건): **서로 다른 시험 2개 이상 × 문항 2개 이상**의 유형(dimension·trap)만 약점 카드로 올라가고, 미달 건은 `observations`의 "관찰"로만 남긴다(DAY-2 핸드오프 규칙).
- 응답 구조(양쪽 동일): `analysis`(student, wrong_html_id, original_name, wrong_total, dna_count, observation_count, **student_answer_known**, analyzed_at, prescription[], observations[]) + `dna[]`(카드: key/category/title/wrong_count/source_count + evidence 근거 문항 원문 링크용 wrong_html_id·question_no·exam_label, 평가원 DNA 연결 tags/claims, 잘못된 풀이 과정·앞으로의 풀이 행동).
- **학생 선택 답 정책**: 오답 원문에 학생 선택 답 표기가 없어(`student_answer_known=false`) 추정하지 않는다. UI도 "원문 미표기(추정하지 않음)"로 명시한다.
- `prescription`은 행동 단위 「최승원의 수능영어 풀이 처방」, 저장 payload는 ⑤ 개인별 대비(맞춤 복습 문제지) 데이터 구조로 그대로 재사용한다(`csat_weakness_analysis`/`csat_weakness_dna`, 학생당 1 analysis + dna_key UNIQUE).
- 실제 데이터 검증(2026-10): 오답 원문 176문항 → 약점 DNA 10개 + 관찰 1건(글의순서 39·빈칸 36·문장삽입 35·어휘 21·어법 12·제목 11·함축의미 10·주제 4·무관한문장 4·요지 3, 관찰=필자 주장 1).
- 관련 코드: `backend/csat_weakness.py`(parse/분류/연결/analyze), `backend/main.py` ④ 엔드포인트 3종, `tests/test_csat_weakness_dna.py`.

## 수능 WebMCP Site Tools

- 등록 위치: top-level React Wiki의 `document.modelContext.registerTool()`.
- 인증: 현재 열린 Production Wiki의 동일 signed-in session과 기존 Spring proxy/API 정책을 사용한다. `role=ADMIN` query를 인증으로 사용하지 않는다.
- 미지원 브라우저: `document.modelContext`가 없으면 기존 Wiki UI만 정상 동작한다.

| Tool | 성격 | 기존 API |
|---|---|---|
| `search_csat_questions` | read-only | `GET /api/csat/questions` |
| `get_csat_assessment` | read-only | `GET /api/csat/assessments/{id}` |
| `create_csat_assessment` | write | `POST /api/csat/assessments` |
| `create_csat_wrong_assessment` | write | `POST /api/csat/wrong-assessments` |
| `replace_csat_assessment_question` | write | `PATCH /api/csat/assessments/{id}/items/{position}` |

- write tool은 `annotations.readOnlyHint=false`이며 브라우저의 Site tools 안전 검토/확인 정책을 따른다.
- Tool handler는 DOM을 클릭하지 않고 동일 CSAT API를 호출한다.
- WRONG tool input/output은 record ID와 Assessment Question asset만 사용하고 학생 식별정보를 반환하지 않는다.
## Wiki authenticated workspace identity

- `GET /problem-generator/wiki/api/workspace/me` returns the server-resolved Wiki workspace identity used by the frontend for browser-storage namespacing.
- The Spring Wiki proxy resolves the JWT principal from `SecurityContext` and signs `userId`, `loginId`, `role`, and a short-lived timestamp with HMAC-SHA256. FastAPI rejects missing, expired, or forged assertions.
- `?role=ADMIN` and `?role=TUTOR` are display-only legacy query values and never participate in workspace or authorization decisions.
- `admin` and `modu` resolve to the shared legacy workspace by default. Every other authenticated user resolves to one private workspace and starts with an empty Wiki database.
- `GET /problem-generator/wiki/api/admin/workspaces` lists workspace catalog metadata and members; database paths are never returned. `GET /problem-generator/wiki/api/admin/workspaces/{workspaceId}` returns one workspace or `404`. Both require the signed Spring principal to be the canonical `admin` account with `ADMIN` role.
- A normal Wiki API request may include `X-BuildIt-Wiki-Workspace: {workspaceId}` as an explicit target selector. FastAPI honors it only for that signed `admin` identity. `modu` and ordinary users receive `403`; an unknown target receives `404`. Omitting or clearing the header restores admin to `legacy`.
- Principal-less external MCP requests retain `MCP_API_KEY` authentication and are explicitly bound to the legacy workspace; they never fall through browser HMAC identity resolution.

## 19. 통합 Analytics API (`/api/admin/analytics`) — 서비스별 방문 + Wiki 다운로드 (V962, V964~V966)

관리자 "통합 Analytics" 화면(`/admin/analytics`) 전용. F2R 전체 페이지 방문(PV/UV), 그 중 Wiki 하위 방문, Wiki 자료(ORIGINAL/ANALYSIS/LEARNING) 다운로드를 한 곳에서 집계한다. 기존 `wiki_daily_visits`(§Wiki 운영 현황, 날짜당 UV 집계 1행)와는 별개 테이블(`site_page_visits`, `wiki_download_logs`)이다 — 둘 다 계속 독립적으로 동작하며 서로 대체하지 않는다.

**보안 경계**: `GET /api/admin/analytics/summary` 는 `/api/admin/**` → ROLE_ADMIN (SecurityConfig). STUDENT/TUTOR 는 403.

### 방문 기록 (자동, 클라이언트 호출 없음)
- `SiteVisitFilter`(SecurityConfig 필터체인, JwtAuthenticationFilter 뒤)가 모든 실제 GET 페이지 응답에서 자동 기록한다. `/api/**`, `/css/**`, `/js/**`, `/img/**`, `/video/**` 등은 경로로 우선 제외하고, 남은 요청도 응답이 `200` + `Content-Type: text/html` 인 경우에만 기록 — 정적 리소스/리다이렉트/API 응답은 집계되지 않는다.
- UV 식별: 로그인 `"U:{userId}"`, 비로그인 `F2R_VISITOR` first-party cookie의 UUID(`"V:{uuid}"`). cookie는 Secure, HttpOnly, SameSite=Lax, Path=/, 90일이다. 기존 `"S:{HttpSession id}"` 행은 삭제·변환하지 않고 호환 집계한다. IP/fingerprint는 사용하지 않는다.
- 명확한 bot/crawler User-Agent는 `traffic_kind=BOT`으로 저장하고 일반 UV/PV에서는 제외한다. 관리자 화면의 `botRequests`에서 별도 요청 수를 본다. 개선 이전 행은 식별 불가능하므로 HUMAN으로 보존된다.
- `service_area`: 실제 route를 HOME(`/`, `/home`), CHART(`/chart/**`), WEB(`/academies/**`, `/academy-posts/**`, `/subdomain-home`), ACADEMY(`/builder`, `/lessons/**`, `/tutor/academy/**`, `/lms/**`), WIKI(`/wiki/**`), NEWSTART(`/newstart`, `/newstart/**` — 운영자 콘솔 `/admin/newstart/**`는 제외), OTHER(그 외)로 분류한다. 과거 row는 저장된 `path` 기준으로 재분류한다(V1020).
- 로그인 사용자는 `users.name`(실명)을 방문 시점 스냅샷으로 `display_name` 에 저장한다(닉네임 아님 — TUTOR도 동일).

### 다운로드 기록 (자동, 다운로드 endpoint 내부)
- `WikiExamOriginalController`/`WikiAnalysisMaterialController`/`WikiLearningMaterialController` 의 기존 `/{id}/download` 가 파일을 정상적으로 읽어온 직후 `WikiDownloadRecordingService.record(...)` 를 호출한다. 프론트 버튼 클릭은 집계하지 않는다. 기존 다운로드 응답(바이트/파일명/Content-Type)은 변경하지 않는다.
- category: `ORIGINAL`/`ANALYSIS`/`LEARNING`. 세 종류 모두 로그인 회원만 다운로드할 수 있으며 성공한 다운로드만 로그인 사용자 정보로 기록된다. 인증이 차단한 GUEST 요청은 로그에 기록하지 않는다. 기존 GUEST 과거 로그는 삭제하거나 변경하지 않는다.

### `GET /api/admin/analytics/summary`
→ `AdminAnalyticsResponse`:
- `siteVisitors`/`siteViews`/`wikiVisitors`/`wikiViews`/`newStartVisitors`: HUMAN만 계산한다. `wikiDownloads`는 기존 다운로드 건수이며, 각 값은 `{today, recent7, recent30, total}`이다. `newStartVisitors`는 `service_area=NEWSTART` UV(상단 "NewStart 방문자 (UV)" 카드).
- `botRequests`: 일반 방문에서 제외된 BOT PV의 `{today, recent7, recent30, total}`.
- `serviceTraffic`: HOME/CHART/WEB/ACADEMY/WIKI/NEWSTART/OTHER별 `{service, visitors, views}`. visitors/views는 각각 기간별 metric이다.
- `statsSince`: 최초 방문 기록 날짜(`site_page_visits` 최솟값). 기록이 없으면 `null` — 프론트가 "통계 수집 시작일 이후 데이터"임을 안내한다.
- `recentWikiVisits`: 최근 Wiki 방문 20건 — `{displayName, role, area("WIKI"|"F2R"), path, visitedAt}`.
- `recentVisits`: 최근 HUMAN 전체 방문 20건 — `{displayName, role, area(HOME|CHART|WEB|ACADEMY|WIKI|NEWSTART|OTHER), path, visitedAt}`. visitor key 원문은 반환하지 않는다.
- `recentDownloads`: 최근 다운로드 20건 — `{displayName, role, materialCategory, materialTitle, downloadedAt}`.
- `topDownloads`: 자료별 누적 다운로드 수 TOP 10 — `{materialCategory, materialTitle, downloadCount}`.

- 관련 코드: `domain/analytics/*`, `config/SiteVisitFilter`. 마이그레이션 `V962`(기본 로그 테이블), `V964~V966`(nullable 추가 → 기존 path backfill → NOT NULL/인덱스), `V1020`(NewStart 분리 재집계).

## Wiki 학교별 실제 기출 원본 (PAST_EXAM) + AI 작업기록 (AI_WORK_LOG)

- `POST /problem-generator/wiki/api/exam-materials`: 기존 multipart 저장 계약을 재사용한다. `core_type=school_print`, `material_type=PAST_EXAM`, school/grade/year/semester/exam/title/original을 전송한다. 실제 기출은 PDF/HWP/HWPX/HTML/HTM/MD/MARKDOWN/JPG/JPEG/PNG 원본을 허용하며 파일당 30MB 제한을 유지한다. HTML은 같은 origin에서 실행되지 않도록 attachment로만 제공한다.
- HTML 업로드는 `_sanitize_exam_html`로 `<script>`/`<style>`/편집기 toolbar·화면전용 UI를 제거하고 제목·문항·지문·선택지·표·보기·밑줄/강조·서답형 작성요령만 남긴 정제본을 `body_text`(`body_format=html`)로 저장한다. MD/TXT는 원문을 그대로 `body_text`에 저장한다(`markdown`|`text`). PDF·이미지·HWP(X)는 본문 추출 대상이 아니라 원본 BLOB만 보관하고 `body_text`는 비운다. **원본 파일(BLOB)은 정제 여부와 무관하게 `/original`로 그대로 보존한다.**
- `material_type=AI_WORK_LOG`: Claude/ChatGPT 등이 시험 준비 과정에서 남긴 작업 로그. `core_type=school_print`로 등록하고 MD/MARKDOWN/TXT/HTML(파일당 10MB) 원본만 허용한다. 본문을 `body_text`로 저장해 다음 세션에서 MCP가 이어서 읽는다.
- `GET /problem-generator/wiki/api/exam-materials?...&material_type=PAST_EXAM&all_years=true`(또는 `AI_WORK_LOG`): 같은 학교·학년·학기·시험의 연도별 목록을 최신 연도순으로 반환하며 `has_body`/`body_format`을 포함한다. 기존 기본 조회는 선택 연도만 반환한다.
- `GET /problem-generator/wiki/api/exam-materials/{id}/original`(원본 BLOB), `GET .../{id}/body`(정제 본문; HTML은 attachment), `DELETE .../{id}`: 기존 원본 접근·삭제 및 workspace/로그인 권한 계약을 그대로 사용한다.
- `POST /problem-generator/wiki/api/exam-materials/{id}/reprocess-body`(admin 전용): `body_text` 저장 계약 이전에 등록된 legacy PAST_EXAM row의 `original_blob`을 현재 업로드와 동일한 extraction(`_extract_uploaded_body` + `_sanitize_exam_html`, HTML 최소구조 검증 포함)으로 다시 처리해 `body_text`/`body_format` 두 컬럼만 in-place UPDATE한다. id·`original_blob`·메타데이터는 불변. 실패 시 기존 body를 훼손하지 않는다. 응답 `{id, material_type, body_format, body_chars, has_body}`.
- IMAGE PAST_EXAM(JPG/JPEG/PNG)도 동일 endpoint에서 처리한다. 원본 magic bytes(JPEG/PNG 구분 검증, 10MB 상한)로 data URL을 만들어 기존 `ai_router.generate_multimodal` 1회 호출로 구조화 HTML(문항/발문/선택지/보기/밑줄/빈칸/서답형 조건·배점, `[판독 필요]` 표시)을 추출한 뒤 동일 `_sanitize_exam_html`로 저장한다(`body_format=html`). 업로드 시점에는 동기 vision을 하지 않고 원본만 안전 저장한다(timeout/latency/비용/실패 처리상 명시적 admin reprocess 방식). frontend/backend 모두 JPG/JPEG/PNG 허용이 이미 일치하므로 계약 변경 없음.
- `GET /problem-generator/wiki/api/workspaces/final/context`와 MCP `get_final_context`는 `past_exams`·`ai_work_logs`에 material_type, exam_year, original_api_path, body_api_path, has_body, body_format, uploaded_by/uploaded_at, provenance를 반환하고, 각 항목에 `body_included`/`body_truncated`/`body_length`(원문 전체)/`body_chars`(실린 길이)를 붙인다. 본문은 항목당 `_CONTEXT_BODY_MAX_CHARS`(200,000자), `past_exams`+`ai_work_logs` 합산 `_CONTEXT_BODY_TOTAL_BUDGET`(600,000자)로 제한하며, 저장 순서(year DESC, id DESC → past_exams 다음 ai_work_logs)대로 deterministic 하게 배분한다. 응답 최상위 `context_budget`에 `body_char_limit_per_item`/`body_char_budget_total`/`body_chars_used`/`truncated_items`/`omitted_items`를 담아 budget 때문에 잘리거나 빠진 자료를 명시한다(silent omission 금지). 원본 BLOB 전체는 Context에 넣지 않는다.
- JPG/JPEG/PNG와 PDF는 기존 원본 endpoint에서 inline 확인할 수 있고, 사용자 HTML은 script 실행 방지를 위해 attachment 다운로드로만 제공한다.


## 학교별 내신 Weekly Exam API

- `POST /problem-generator/wiki/api/weekly-exams/review`: 로그인된 Wiki 사용자가 문제지 HTML, 정답해설 HTML, 제작·검수 Markdown을 한 세트로 등록한다. 상태 입력 필드는 없으며 서버가 항상 `REVIEW`로 저장한다. 현재 Living Context의 material ID, Digital Twin version, 항목별 count와 context status를 immutable Snapshot/hash로 함께 기록한다.
- `GET /problem-generator/wiki/api/weekly-exams`: 선택한 school/grade/year/semester/exam의 모든 내부 상태를 조회한다.
- `GET /problem-generator/wiki/api/weekly-exams/published`: 같은 Context에서 backend가 `PUBLISHED`만 필터링한다.
- `GET /problem-generator/wiki/api/weekly-exams/{id}/artifacts/{student-paper|answer-explanation}`: 저장된 canonical HTML 원본을 `text/html; charset=utf-8` + `Cache-Control: no-store`로 그대로 렌더링한다(보기=다운로드=등록 당시 바이트, script만 제거). `production-verification`은 escaped Markdown View를 CSP sandbox와 함께 반환한다(기존 유지).
- `GET /problem-generator/wiki/api/weekly-exams/{id}/artifacts/{student-paper|answer-explanation}/download`: 동일 canonical 바이트를 `Content-Disposition: attachment` + RFC 5987 한글 파일명으로 다운로드한다. MD는 다운로드 대상이 아니다.
- `GET /problem-generator/wiki/api/weekly-exams/{id}/context-snapshot`: 제작 당시 immutable Context와 hash를 반환한다.
- `POST /problem-generator/wiki/api/weekly-exams/{id}/transition`: workspace ADMIN 전용. `REVIEW→APPROVED|CHANGES_REQUESTED|REJECTED`, `APPROVED→FINAL`, `FINAL→PUBLISHED`만 허용한다.
- MCP `register_weekly_exam_review`: HTTP 등록과 동일 service를 호출하며 상태는 항상 `REVIEW`다. MCP에는 승인·FINAL·PUBLISHED tool이 없다.


## Academy Midterm LIVE update feed
`GET /api/academies/{handle}/midterm-campaign/live-updates`는 published campaign과 signed tutor workspace의 실제 Wiki metadata를 결합한다. school, grade, year, semester, examType, kind, label, round, setType, questionCount, updatedAt만 반환하며 원문·정답·검수 MD·source ID·경로·Context snapshot은 반환하지 않는다.


## Weekly Exam private workflow
- `POST /problem-generator/wiki/api/weekly-exams/draft`: signed operator only; effective state DRAFT.
- `GET /problem-generator/wiki/api/weekly-exams`: current signed workspace only.
- artifact/context-snapshot URLs: signed operator workspace only; HTML download endpoint included (operator only, no public exposure).
- `POST .../{id}/transition`: `finalize|reopen` only.
- `DELETE .../{id}`: signed operator only; effective DRAFT(REVIEW/CHANGES_REQUESTED/REJECTED)만 삭제, FINAL 계열은 409 거부. 자식 audit 행을 먼저 지우는 단일 트랜잭션.
- `PUT .../{id}/artifacts`: signed operator only; DRAFT 한정 파일별 교체(문제지·정답해설·출제근거 중 제공된 것만 교체, 나머지 유지, identity·회차·세트·status·version 유지, filename/hash 재계산, sourceMaterialIds 지정 시 기존 `_source_refs` 검증 재사용, 교체 파일 0건이면 400). 교체 후 보기·다운로드는 `Cache-Control: no-store`라 즉시 새 파일이 보인다.
- `GET .../public-metadata`: FINAL public-safe projection only. Bodies, identifiers, source IDs, hashes, snapshot and locators are excluded.
- `PATCH .../{id}/metadata`: signed workspace ADMIN only. `round`·`setType`(40|70|40A|40B)만 수정하고 artifact 본문·status·version은 유지한다. `setType`이 문항 수의 source of truth다: 70은 `question_count=70`, 40·40A·40B는 40으로 정규화하며, `questionCount` 입력이 정규화 값과 어긋나면 400, 수정 결과가 동일 identity·회차·세트·version의 기존 행과 겹치면 409. 수정은 `METADATA_UPDATED` audit 행으로 기록한다.
Legacy REVIEW/CHANGES_REQUESTED/REJECTED is read as DRAFT; APPROVED/FINAL/PUBLISHED is read as FINAL without destructive migration.


## NewStart 부실차주 Wiki ADMIN API (V985)

SSR 화면 `/admin/newstart/wiki`(WebMvcConfig view → `templates/admin/newstart_wiki.html`)와
그 화면이 호출하는 JSON API. `SecurityConfig`의 `/admin/newstart/**`·`/api/admin/**` `hasRole('ADMIN')`
규칙을 그대로 상속받는다 — 일반 회원/비로그인은 화면·API 모두 접근 불가. actor는 서버 principal
(`JwtTokenProvider.AuthenticatedPrincipal.loginId()`)에서만 결정하고 요청값으로 받지 않는다.

### 지식 `/api/admin/newstart/wiki`

- `GET ""`(`q`, `sourceType`, `category`, `status`, `page`, `size`(기본 20), `sort`): 페이지 조회.
  `status`/`sourceType`은 Entity enum 문자열과 동일 값(`REVIEW`, `ACTIVE`, `OFFICIAL_CURRENT` …)을 넘긴다.
- `GET /{id}`: 단건(없으면 404). `GET /active`: ACTIVE 목록(retrieval과 같은 상태 의미).
  `GET /source-type/{sourceType}`, `GET /category/{category}`: 필터 목록.
- `GET /stats`: 상태별·소스별 카운트(`totalKnowledge`, `draftCount`, `reviewCount`, `activeCount`,
  `archivedCount`, `officialCurrentCount`, `officialHistoricalCount`, `auditCount`, `ebookCount`,
  `cafeArticleCount`, `cafeCommentCount`, `caseCount`, `operatorKnowledgeCount`, `derivedRuleCount`).
  프론트의 `knowledgeGaps`는 백엔드가 계산하지 않는다(항상 0 표시).
- `POST ""` / `PUT /{id}`: 생성·수정. `PUT /{id}`로 ACTIVE 직행은 거부된다(승인 API 경유 필수).
  `PUT /{id}/status`(`status`, `reason`): 상태 변경 + `newstart_knowledge_status_log` 기록.
  허용 전이만 통과(`DRAFT→REVIEW/ARCHIVED`, `REVIEW→ACTIVE/ARCHIVED/DRAFT`,
  `ACTIVE→ARCHIVED`, `ARCHIVED→DRAFT/REVIEW`), `ACTIVE`/`ARCHIVED`에는 `reason` 필수,
  `REVIEW→ACTIVE` 경고 존재 시 409(`CONFIRMATION_REQUIRED`).
- 승인 워크플로: `GET /review`(REVIEW 큐), `GET /review/{id}`(본문+경고+이력),
  `POST /review/{id}/approve`(`reason` 필수, 경고 있으면 `confirmed:true` 필수, 미확인 시 409),
  `POST /review/{id}/keep`(REVIEW 유지+메모 기록), `POST /review/{id}/archive`
  (`REVIEW`/`ACTIVE→ARCHIVED`, `reason` 필수), `GET /{id}/history`(provenance),
  `DELETE /{id}`(하드 삭제 아님, `ARCHIVED` 처리). `IllegalState/Argument` 위반은 400
  (`REVIEW_REQUEST_REJECTED`). OFFICIAL/CASE/EBOOK 모두 동일 workflow, 경고는 차단이
  아니라 관리자 확인 대상(`CASE_DERIVED` 포함).
- `POST /ingest/cases`(`articlesDir`, 기본 `data/newstart-cafe/gamian/articles`),
  `POST /ingest/generate-rules`: 카페 사례→knowledge 파생(별도 컨트롤러 `NewstartIngestionAdminController`).
- `POST /ingest/cases/repair`(`articlesDir`, 기본 `data/newstart-cafe/repair_staging/articles`):
  staging articleId만 대상 비파괴 병합(insert / null→fill / contentRaw는 더 긴 쪽만), 재실행 멱등,
  `created_by/updated_by=REPAIR_BOT`, knowledge 테이블 무접촉 (V1009 이전 #417 계약).
- `POST /ingest/curated-review`(`ebookSections` 기본 `docs/newstart/ebook/ebook_sections.json`,
  `gamianDir` 기본 `data/newstart-cafe/gamian/articles`): Agent3 큐레이션 산출물(EBOOK 19 section +
  CASE 11건) REVIEW 전용 적재 — ACTIVE 생성 경로 없음, title/articleId 멱등, CONFLICT_REVIEW 태그·
  CONTRADICTS 관계는 대응 공식 item 존재 시에만 생성.
- `POST /ingest/official-review`(`officialDir` 기본 `docs/newstart/research-official`,
  `dryRun` 기본 **true**): `NewstartOfficialReviewIngestService`를 그대로 호출하는 ADMIN 전용
  공식 자료 REVIEW 시드. dryRun=true면 수집·title 멱등 판정만 수행하고 쓰지 않는다.
  응답 `{dryRun, sourceCount, created, skipped, conflictReview, relations, failed, activeCreated, errors}`
  — `activeCreated`는 항상 0(ACTIVE 전환은 `/review/{id}/approve` 승인 워크플로 전용),
  `newstart_official_updates` 테이블에는 접근하지 않는다.

### 사례 `/api/admin/newstart/wiki/cases`

- `GET ""`(`articleId`, `authorNickname`, `finalReductionRate`, `householdType`, `minAge`/`maxAge`,
  `minDebtTotal`/`maxDebtTotal`, `minIncome2025`/`maxIncome2025`, `householdTypeFilter`,
  `hasFinalReductionRate`, `page`, `size`, `sort`): 감면율 사례 검색.
- `GET /{articleId}`, `GET /with-actual-rate`, `GET /by-household/{householdType}`, `GET /stats`.

### 공식자료 REVIEW ingest

`NewstartOfficialReviewIngestService.ingestOfficialReview(docs/newstart/research-official)`가
`comparison/*.json` + `ccrs/k*.json` + `evidence_*.json` + `concepts.json`을 읽어
**`status=REVIEW`로만** 적재한다(ACTIVE 생성 경로 없음, 동일 title skip=멱등, UNKNOWN/CONFLICT
보존). HTTP 호출은 `POST /api/admin/newstart/wiki/ingest/official-review`(위 항목, ROLE_ADMIN,
dryRun 기본 true). Top10 차주 질문 근거 충분성 판정은 `NewstartTop10Answerability`(AI 생성 없음,
ANSWERABLE/PARTIAL/UNKNOWN/CONFLICT)로 ADMIN preview 수준.


## 오답 모의고사 (Wrong Mock) API

- `POST /problem-generator/wiki/api/wrong-mock/rounds`: signed operator only. 회차를 먼저 생성한다(`{school,grade,year,semester,exam,round?,title?}`). `round`를 비우면 같은 Context 마지막 회차 다음 번호를 제안하고, `title`을 비우면 `오답 {N}회`가 된다. 같은 Context 중복 회차는 409. 상태는 항상 `COLLECTING`(사진 등록 중)으로 시작한다.
- `GET /problem-generator/wiki/api/wrong-mock/rounds?...`: 같은 시험 Context의 회차 목록(사진 개수·문제지/해설 보유 여부·편집 잠금 포함, BLOB 제외).
- `GET /problem-generator/wiki/api/wrong-mock/rounds/{id}`: 회차 상세 + 원본 사진 목록(제목 metadata·파일명·크기·hash·순서·`original_api_path`, BLOB 제외).
- `POST .../rounds/{id}/photos`(multipart `files[]` + `titles` JSON + `force`): 사진 등록. 제목은 원본 그대로 보존한다. 같은 회차 동일 이미지(SHA-256)는 409 `이미 등록된 사진입니다.` 다른 회차 동일 이미지는 409 경고(이미 등록된 회차 표시)이며 `force=true`로 계속 등록할 수 있다. 사진 개수와 문항 개수를 동일시하는 constraint가 없다.
- `GET /problem-generator/wiki/api/wrong-mock/photos/{id}/original`: 원본 사진 바이트. 이미지/PDF는 inline, HTML은 attachment, `Cache-Control: no-store`.
- `PATCH .../photos/{id}`(제목 수정), `POST .../rounds/{id}/photos/reorder`(`orderedIds`), `DELETE .../photos/{id}`, `PUT .../photos/{id}`(교체, id·순서 유지): 사진 관리. `COLLECTING`이 아니면서 편집이 잠겨 있으면(편집 OFF) 409.
- `POST .../rounds/{id}/complete-photos`: 사진 등록 완료. `COLLECTING→READY`(제작대기)로 바꾸고 편집을 잠근다. 사진 개수와 무관하게 선생님이 직접 확정한다(개수로 자동 전이하지 않음).
- `PUT .../rounds/{id}/artifacts`(`{paperHtml?,paperName?,explHtml?,explName?}`): 문제지·간략해설 HTML 등록·교체. 사람 업로드와 OpenCode 자동 등록이 같은 저장 contract를 쓴다. 저장 시 script subtree만 제거하고 canonical을 그대로 보존한다. 두 artifact가 모두 갖춰지면 `COMPLETED`(제작완료) + 편집 잠금. `COLLECTING` 상태 등록과 잠금 상태 등록·교체는 409.
- `POST .../rounds/{id}/edit-lock`(`{locked}`): 편집 OFF/ON 전환.
- `DELETE .../rounds/{id}`: `COLLECTING` 상태에서만 회차 삭제(사진·audit 함께 삭제, 단일 트랜잭션). `READY`/`COMPLETED`는 409.
- `GET .../rounds/{id}/artifacts/{paper|explanation}`(보기, `text/html; charset=utf-8` + `no-store`), `GET .../artifacts/{kind}/download`(RFC 5987 한글 파일명): 완성본 확인·인쇄는 브라우저 보기가 중심이다.
- `GET /problem-generator/wiki/api/wrong-mock/ready?...`: 제작대기(READY) 회차 목록 + 사진 제목 metadata + 원본 경로 + 제작규격(spec). 향후 OpenCode가 가져갈 읽기 계약이다. Living Context(`student_errors`)에는 오답모의고사 사진을 섞지 않는다.

### EASY 자동수집 Phase 3A 안전 계약

- Provider 수집은 `EasyCollectionTriggerType.MANUAL|SCHEDULED`를 명시한다.
- run 생성·완료·실패만 짧은 DB transaction에서 처리하며, 외부 `adapter.fetch()`는 DB transaction 밖에서 실행한다.
- provider 실패는 `AUTH_REQUIRED`, `PROVIDER_TEMPORARY`, `INVALID_DATA`, `CONFIGURATION_ERROR`로 분류한다. `AUTH_REQUIRED`만 connection을 `NEEDS_REAUTH`로 바꾼다.
- 실패해도 run은 `FAILED`, `completedAt`, 안전한 error code/summary와 함께 보존한다. credential/token/cookie/certificate/card number/connectionRef는 응답·로그에 포함하지 않는다.
- CREFIA real API는 활성화되지 않았다. V983의 `CARDSALES` source는 inactive이며, 공개 확인되지 않은 placeholder endpoint와 APPROVAL/PURCHASE/DEPOSIT 회계 lifecycle은 fail-closed다.
- collection은 기존 import staging까지만 수행한다. 자동수집은 회계 자동확정이 아니다.



### EASY 자동수집 Phase 3B lifecycle/checkpoint 계약

- provider-neutral dataset type: `APPROVAL|PURCHASE|DEPOSIT|CANCELLATION|CORRECTION|UNKNOWN`. 이는 공식 CREFIA dataset 의미를 확정하는 계약이 아니다.
- provider event와 회계 transaction은 분리된다. 같은 economic sale에 여러 lifecycle event를 저장할 수 있지만 어느 event도 자동으로 INCOME/import/confirm되지 않는다.
- cancellation/correction은 original event 관계를 요구하며 정상 매출 event로 취급하지 않는다.
- checkpoint scope는 connection+dataset이다. event와 관계 저장이 모두 성공한 뒤에만 같은 transaction에서 advance하며 fetch 또는 persistence 실패 시 advance하지 않는다.
- 동일 external event는 connection+dataset event key로 멱등 처리하고 기존 raw dedupe는 그대로 유지한다.
- CREFIA real API/endpoint는 활성화하지 않았고 외부 호출도 없다. collection auto-confirm은 계속 금지한다.


### EASY 자동수집 Phase 3C ADMIN readiness API

모든 endpoint는 `ROLE_ADMIN` 전용이며 provider secret, token, credential, `connectionRef`, cursor/checkpoint 원문을 반환하지 않는다.

- `GET /api/admin/easy/collections/connections?businessId={id}`: connection/source/status/readiness/최근 run/checkpoint 존재 여부 조회.
- `PUT /api/admin/easy/collections/connections/{id}/enabled?businessId={id}` body `{"enabled":true|false}`: connection 활성/비활성. 활성화는 provider readiness가 `READY`일 때만 허용한다.
- `GET /api/admin/easy/collections/connections/{id}/checkpoints?businessId={id}`: dataset, 존재 여부, `hasCursor`, 성공/갱신시각만 반환한다.
- `POST /api/admin/easy/collections/connections/{id}/run?businessId={id}`: readiness와 중복 RUNNING 검사를 통과한 연결만 MANUAL 수집한다.
- readiness: `READY|NOT_CONFIGURED|DISABLED|UNVERIFIED|NEEDS_REAUTH`. 현재 production provider verification allow-list는 비어 있으며 CREFIA는 반드시 `UNVERIFIED`다.
- 실제 CREFIA/Card API는 비활성이고 provider event는 accounting transaction이 아니다. 자동 confirm은 계속 금지한다.


### EASY 자동수집 Phase 4 실행 안전 계약

- MANUAL과 SCHEDULED provider 수집은 동일한 DB-backed execution gate를 통과한다.
- gate는 connection 행의 `PESSIMISTIC_WRITE` lock 아래에서 readiness 확인, 만료 lease 복구, active RUNNING 확인, 새 ownership claim을 한 transaction으로 처리한다.
- RUNNING run은 외부에 노출하지 않는 random `lease_token`, `lease_acquired_at`, `lease_expires_at`, `heartbeat_at`을 가진다. heartbeat는 run ID, connection ID, ownership token이 모두 일치하고 RUNNING일 때만 lease를 연장한다.
- 기본 lease는 300초, heartbeat는 30초, orchestration deadline은 120초다. 각각 `easy.collection.lease-seconds`, `easy.collection.heartbeat-seconds`, `easy.collection.execution-deadline-seconds`로 조정할 수 있다.
- deadline 초과는 interrupt 요청 후 `FAILED / EXECUTION_TIMEOUT`으로 기록한다. `Thread.stop` 같은 비안전 종료는 사용하지 않으며 provider fetch 중 DB transaction을 열지 않는다.
- 만료 lease는 `FAILED / STALE_RUN_RECOVERED`로 종료한 후에만 다음 run을 claim한다. legacy `RUNNING + lease NULL`은 기본 900초 age threshold 이후에만 stale로 복구한다.
- scheduler도 readiness gate를 적용하므로 `DISABLED|NOT_CONFIGURED|UNVERIFIED|NEEDS_REAUTH` provider는 adapter에 진입하지 않는다.
- CREFIA는 계속 `UNVERIFIED`이며 manual/scheduler 모두 실제 network call에 도달하지 않는다.
- stale/deadline 실패는 checkpoint를 advance하거나 lifecycle/accounting transaction을 생성하지 않는다. 자동 confirm은 계속 금지한다.


### EASY 사업용 카드 Phase 5A 서비스 계약

- `EasyBusinessCardAccountingService`는 provider-neutral 내부 service foundation이며 public/provider API endpoint를 추가하지 않는다.
- purchase 관찰은 expense candidate와 동일 금액 payable을 생성하지만 `easy_transactions`/journal을 자동 생성하지 않는다.
- bank payment settlement은 allocation을 통해 payable만 감소시키며 비용을 추가하지 않는다. 하나의 payment→여러 payable, 하나의 payable→여러 payment를 허용한다.
- cancellation/correction은 original event relation을 보존한다. cancellation은 outstanding을 초과할 수 없다.
- external ID 우선, 없으면 provider/connection/safe-card-token/type/time/amount/reference 기반 SHA-256 fingerprint로 멱등성을 보장한다.
- full PAN, credential, raw provider secret을 입력·응답·로그에 노출하지 않는다. 실제 provider endpoint와 network 호출은 Phase 5A 범위 밖이다.
- 승인 전 상태는 `PENDING_REVIEW`이며 AUTO CONFIRM은 금지된다.


## CHOONGGI Creator (2026-09)

- Creator SSR: `GET /choonggi/admin`, `/choonggi/admin/posts`, `/choonggi/admin/posts/new`, `/choonggi/admin/posts/{id}/edit|preview` — ROLE_ADMIN.
- Write API: `/api/admin/choonggi/**` — existing ROLE_ADMIN rule. Draft create/update, media upload, cover selection, publish, unpublish and soft delete.
- Public media: `GET /api/choonggi/media/{id}`. Metadata is in MySQL; bytes are in existing FileStorage (S3 in prd, local in dev).
- Photos are decoded, resized and re-encoded by ImageSanitizer, removing EXIF/GPS. Videos accept validated MP4/MOV up to 50MB; no autoplay.
- DRAFT rows never appear in public queries. Published posts integrate into `/choonggi` and `/choonggi/vlog/{slug}`.


## NewStart 지피지기 Agent ADMIN API (V1004·V1005)

SSR 화면 `/admin/newstart/jipijigi`(WebMvcConfig view → `templates/admin/newstart_jipijigi.html`)가 호출하는
JSON API. `SecurityConfig`의 `/api/admin/**` `hasRole('ADMIN')` 규칙을 그대로 상속받는다(일반 회원 403, 익명 401).
actor는 서버 principal의 `loginId`에서만 결정하고 요청값으로 받지 않는다. 상태 전이 위반·allowlist 위반은 400
(`AGENT_REQUEST_REJECTED`), 게시 시도는 항상 409(`PUBLISH_DISABLED`).
운영 규칙의 정본: [`newstart/jipijigi/OPERATING_PRINCIPLES_2026-09-23.md`](newstart/jipijigi/OPERATING_PRINCIPLES_2026-09-23.md).

- `GET /api/admin/newstart/jipijigi/inventory`: KB 실제 보유량(상태·소스별 카운트, 공식 업데이트, 실증 사례,
  질문·초안 수)과 조회 능력 플래그(`keywordLikeRetrieval=true`, `vectorSearch/fullTextIndex/embeddingPipeline=false`)
  + 갭 메모. "수집했다고 알려진 것"이 아니라 DB 실제 카운트만 노출한다.
- `GET|POST /boards`, `PUT /boards/{id}/enabled`: 게시 allowlist. `menuId`는 양수 실측값만 저장(0·음수 400),
  미확인은 NULL로 두고 `postable=false`로 반환한다.
- `POST /commands`(`instruction`, `commandType?`): 수동 명령 실행. type 생략 시 명령어 키워드로 분류한다.
  응답은 `{command, drafts[]}`이며 `resultSummary`에 "게시 미수행"을 명시한다. `GET /commands`: 실행 이력.
- `POST /questions`(수집 결과 upsert, `articleId`·`title` 필수)/ `GET /questions?limit=`: 질문 저장소.
  본문 없으면 `bodyStatus=UNREADABLE`, 닉네임·연락처·이메일·계좌·전화는 저장 전 마스킹.
- `GET /drafts?status=`, `GET /drafts/{id}`: 초안 목록·상세(본문 + STEP별 trace + 상태 이력 + sourceClass별
  hit 수; `ref_id=GAP` 행은 hit 계산에서 제외). `PUT /drafts/{id}`: DRAFT/READY_FOR_REVIEW에서만 수정·
  대상 게시판 재지정.
- 상태 액션: `POST /drafts/{id}/review`(검토 요청), `/approve`(enabled allowlist 게시판 필수 + 사유),
  `/hold`, `/reject`(사유 필수), `/publish` — V1은 설정 플래그·allowlist·게시 클라이언트 3중 차단으로
  항상 409이며, 화면은 대신 `[원문 복사]`로 운영자 계정 게시용 최종 텍스트를 전달한다.
- **답변 세션 축적(V1005)**: 초안 생성 성공 시점에 `newstart_jipijigi_answer_sessions`에 자동 저장된다
  (게시 성공 여부와 무관, `fingerprint`로 동일 질문·동일 근거 dedupe). `/approve`는 세션을 `ACCEPTED`로,
  `/reject`는 `REJECTED`로 전이시키고 — `ACCEPTED` 행만 다음 질문 STEP1 RAG에서 외부 카페 검색보다 먼저 조회된다.
  - `GET /sessions?page=&size=`, `GET /sessions/{id}`: 축적된 답변 세션(원 질문·최종 답변·출처별 근거 JSON·
    `dbHits`·`externalSearched`·`confidence`·`operatorEdited`·`publishedAs`·`reviewState`).
  - `POST /drafts/{id}/exposure-report`(`publishedAs=POST|COMMENT`, `publishedUrl`, `commentId`, `followUpRef`):
    **운영자가 실제 카페에 노출한 결과를 보고**하는 유일한 경로. Agent가 스스로 게시 성공을 기록하지 않으며
    `NONE` 보고는 400이다.
  - `PUT /sessions/{id}/follow-up`(`followUpRef`): 이후 회원 후속 댓글·실제 결과를 이 답변에 연결한다.

## 금감원 민원 도우미 API (`/api/newstart/fss`) (2026-09, Phase 1 v2)

- 인증: 비로그인은 기존 `/api/**` catch-all이 `401`로 막는다. **설계 §7 경계** — 설명/예시/공식 안내는 읽기, AI 실행은 THANKS_MEMBER(`newstart_member_grants` row, ADMIN은 grant 없이 통과). 미부여 회원의 실행 호출은 `403` + `{"status":"error","errorMessage":"감사회원 전용 기능입니다..."}`이고 AI는 호출되지 않는다.
- 무상태 원칙: 민원 원문·인터뷰 내용·초안은 DB에 저장하지 않고 로그에도 남기지 않는다. 자동 제출은 존재하지 않으며, 제출·본인인증은 사용자가 금감원 공식 사이트에서 직접 수행한다.
- `GET /api/newstart/fss/companion` — 화면 1회 로딩 payload: `samples`(전자책 예시 3종 FS-1..FS-3, ebookRef·sourceLines 포함), `manual`(전자책 기반 안내), `official`(§7 소관 분기 안내: 금감원 공식 페이지 3종 출처·카테고리별 소관·기타 기관 경로·조회 기준일), `agent`(Guided Agent 14단계 정적 안내, PC Chrome 고지 포함).
- `POST /api/newstart/fss/compose` — **THANKS_MEMBER 실행**. 두 경로. V1 자유서술 `{sampleId, situationText}` 또는 guided interview `{interview: {category, facts, company, events: [{date, party, happened, evidence}], contacts, ownResponses, evidence: [], outcome}}`(`interview` 존재 시 이 경로 우선). 응답 `{status, composedText, subject, body, errorMessage, model}`. 실패 시 400이며 응답에 사용자 원문·내부 상세를 넣지 않는다.
- `GET /api/newstart/fss/agent/workflow` — 14단계 정적 안내(개인정보 없음). **익명 열람 허용**(SecurityConfig permitAll, GET 한정) — 설명은 누구나 본다.
- `GET /api/newstart/fss/agent/session`, `POST /api/newstart/fss/agent/advance|back|restart` — **THANKS_MEMBER 실행**(세션 생성·진행). 미부여 회원은 403, 화면은 안내 문구를 그대로 보여주되 14단계 목록 렌더는 계속 진행한다(메모리 세션, 개인정보 미저장).


## NewStart 본인채무 상태창 API (`/api/newstart/debt-status`) (2026-09, V1009)

전자책 실전 가이드 → 한국신용정보원 Credit4U → 기존 `/newstart#ns-status-panel` 상태창을 하나의 흐름으로 연결한다. 상태창이 SOURCE OF TRUTH이며 별도 채무관리 화면/중복 저장 테이블은 없다.

- 인증: 기존 `/api/**` catch-all(로그인 필요)을 그대로 따른다. 권한 규칙은 새로 만들지 않는다. 대상 회원은 서버 principal(userId)로만 결정되고 요청 바디에 userId가 없다.
- `GET /api/newstart/debt-status/me` — 회원별 1:1 상태. row가 없으면 기본 빈 상태(`status=NOT_STARTED`). 응답은 확인 플래그 6종(`credit4uChecked|creditorConfirmed|personalLoanConfirmed|overdueRegisteredConfirmed|debtGuaranteeConfirmed|creditorChangeConfirmed`), `debtSummary`, `stage`, 파생 `status`(`NOT_STARTED|IN_PROGRESS|COMPLETED`).
- `PUT /api/newstart/debt-status` — upsert 전용(초기화는 값 비운 저장, DELETE API 없음). `stage`는 화면 select allowlist(정보 확인 중/신청 준비 중/신청 완료/심사 진행 중/협약·약정 단계/기타)만 허용, 위반·1000자 초과 메모는 400(`errorMessage`)이며 원문을 자르지 않는다.
- 파생 상태는 저장값이 아니라 표시값: 체크 0개 → 미확인, 일부 → 확인 중, 6개 전부 + 메모 또는 단계 → 정리 완료(HOME `#ns-debt-home-state` 배지).
- `/newstart/tools/credit4u`는 전자책 3줄 stub을 공식 메뉴 기준 9단계 실전 안내(일반신용정보→신용정보 등록현황→신용도판단정보/공공정보/개인대출정보/개설·발급정보/채무보증정보→채권자 변동정보→개인회생 신청 등 정보→대출채권소각정보)로 교체했고, 하단 CTA `[확인한 채무정보 정리하기 →]`가 `/newstart#ns-status-panel`로 복귀해 상태창을 자동 연다.
- 개인정보 원칙: Credit4U ID/PW·주민번호·공동/금융인증서 비밀번호·OTP는 절대 수신·저장하지 않고 대신 로그인하지 않는다. 채무 원본(금액·기관 목록 데이터)이 아니라 사용자가 직접 정리한 메모만 저장한다.
- Credit4U JPG·PNG·PDF 자동 읽기는 `/newstart#ns-status-panel` 브라우저 안에서 자체 호스팅한 Tesseract.js/PDF.js로만 처리한다. 원본 파일과 OCR 원문을 서버 API로 전송하거나 저장하지 않으며, 편집 가능한 5개 항목(채권기관·채무종류·잔액·연체/장기연체·채권자 변동)을 사용자가 확인한 뒤 기존 `PUT /api/newstart/debt-status`를 눌러야 메모에 포함해 저장한다.
- 지피지기 연결: 상태창 버튼은 메모·단계를 AI 질문창에 실제로 채워 `/api/newstart/ai/chat` 전달 문의로 사용한다. ai/chat 서버가 DB의 채무상황 row를 자동 컨텍스트로 주입하지는 않는다(미연결 — 후속 작업).

## NewStart 감사회원 1:1 비공개 상담 API (`/api/newstart/consultations`, `/api/admin/newstart/consultations`) (2026-10, V1019)

감사회원과 운영자 사이의 비공개 개인 상담. 카카오톡 상담방(공개 질문·경험)과 분리되며 개인 채무·개인 상담은 이 안에서만 오간다(설계 §1·§6). 설계·테이블 상세는 [`DOMAIN.md`](DOMAIN.md) "NewStart 감사회원 1:1 비공개 상담 (V1019)".

인증 경계: `/api/newstart/consultations`는 `/api/**` catch-all로 로그인 회원만 도달하고, 서비스 `requireThanksMember`가 감사회원(THANKS_MEMBER grant, ADMIN은 grant 없이 통과)만 통과시킨다(미부여 403 `{"status":"error","errorMessage"}`). 발신 회원은 서버 principal에서만 결정 — 요청에 userId·authorRole을 받지 않는다. 소유권 HARD GATE는 서비스에서 강제(타인 상담 403/404, §8). 운영자 API는 `/api/admin/**` `hasRole('ADMIN')` + 서비스 `requireAdmin` 이중 확인.

### 회원 측 `/api/newstart/consultations`
- `POST /`(`{content, linkDebt}`) — 새 상담 작성. 첫 질문을 MEMBER 메시지로 저장하고 질문에서 결정적 키워드로 A to Z 단계를 연결(신호 없으면 NULL). `content` 2000자 초과·빈 값 400(`errorMessage`). 201 `ConsultationThreadView`.
- `GET /` — 내 상담 목록. 각 행 `{consultationId, status, statusLabel, preview, lastMessageAt, newAnswerCount(못 본 운영자 답변 수), linkDebt}`.
- `GET /{id}` — 상담 스레드 상세. `messages`는 MEMBER·OPERATOR만(**AI_RESEARCH 원천 제외**, §5). 열람 시 읽음 커서 갱신.
- `POST /{id}/messages`(`{content}`) — 같은 스레드 추가 질문. 운영자 답변 뒤면 상태 MEMBER_FOLLOWUP.
- `GET /unread-count` — 헤더/홈 배지용 `{unreadAnswerCount}`.
- 회원 경로에는 조사 엔드포인트가 없다(AI 결과를 회원 API로 절대 내리지 않음, §5).

### 운영자 측 `/api/admin/newstart/consultations` (ROLE_ADMIN)
- `GET /` — 상담함 목록. 회원 닉네임만 노출. `{consultationId, memberNickname, status, statusLabel, preview, lastMessageAt, memberQuestionUnread, linkDebt}`.
- `GET /{id}` — 상담 상세 한 화면(§3: 닉네임/연결 허용한 채무상황(`linkDebt`일 때만)/현재 A to Z 단계 title·url/이번 질문/이전 대화(시간순, AI_RESEARCH 포함 운영자만)/관련 지피지기 실제 사례). 열람 시 NEW→AWAITING_ANSWER 전이·읽음 갱신.
- `POST /{id}/research` — `[지피지기에게 조사시키기]`: 회원 최신 질문을 `JipijigiSanitizer.mask`(이름·닉네임·표시별명 + 연락처/주민번호/계좌/이메일)로 **익명화한 뒤** `NewStartAiChatService.ask`/`askOfficial`에 조사. 결과는 **운영자 전용** `AI_RESEARCH` 메시지로만 저장하고 회원에게 자동 전송하지 않는다(상태·정렬 시각 미변경). 200 `ConsultationMessageView(authorRole=AI_RESEARCH)`.
- `POST /{id}/answer`(`{content}`) — `[회원에게 답변]`: 운영자가 읽고 수정·보완한 텍스트만 OPERATOR 메시지로 회원 비공개 스레드에 전달. 상태 OPERATOR_ANSWERED. 200 `ConsultationMessageView(authorRole=OPERATOR)`.
- 400 처리: `IllegalArgumentException`(빈/초과 본문, 조사할 회원 질문 없음) → `{"status":"error","errorMessage"}`.
- 화면 진입: `/newstart/consultations`(공개 셸), `/admin/newstart/consultations`(ROLE_ADMIN).
