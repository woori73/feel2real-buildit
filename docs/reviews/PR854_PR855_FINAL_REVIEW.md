# PR #854 · #855 통합 수정 및 최종 검수 보고서

작성: 2026-10-22 (Claude). 기준 문서: [PR854 검수](PR854_INDEPENDENT_REVIEW.md) · [PR855 검수](PR855_INDEPENDENT_REVIEW.md) · [PR853 점검 노트](PR853_NOTES.md)
두 PR은 **Draft 유지**, main 병합·운영 배포·운영 DB 변경·앱 재시작·MCP 운영 활성화·유료 API 호출은 하지 않았다.

## 0. 먼저 알아야 할 일: Codex가 같은 브랜치에 동시에 push했다

이 작업 중 Codex(사용자 계정 커밋)가 #854·#855 브랜치에 04:43~05:42 사이 커밋을 올렸고, #855에는 스튜디오 복원·68컷 일괄 등록·OAuth 보강·GET 감사와 **#853·#854 병합**까지 들어갔다. 제가 병렬로 만든 스튜디오 구현(복원·일괄 등록·컷 요약)은 이와 중복이라 **폐기**했다(참고용 브랜치 `wt/studio-impl`만 로컬에 있음). 대신 Codex 최신 원격을 기준으로 삼고, 중복되지 않는 수정만 얹었다. **두 에이전트가 같은 브랜치에 쓰면 충돌·중복이 계속 생기므로, 병합 전까지 한쪽만 쓰도록 정해 주세요.**

## 1. #854 최신 HEAD 및 CI

- 브랜치 `feat/unified-ai-assistant` HEAD **`2f7d0db`** (이전 `bdbbfbf` + 최신 main 병합 + Codex 2커밋 + Claude 수정 3커밋).
- GitHub CI (head `2f7d0db`): **build ✅ · mysql-integration-test ✅** ([run 39731009114](https://github.com/woori73/feel2real-buildit/actions/runs/39731009114))
- 로컬 재검증: JUnit `*unifiedassistant*` 75건 통과(실패 0), `DashboardWidgetControllerTest` 포함 시 88건, JS `unified_assistant_tour` 5건 통과.

## 2. #855 최신 HEAD 및 CI

- 브랜치 `feat/ai-agent-studio-unified-mcp` HEAD **`537303f`** (Codex `db5b27e` 위에 Claude 4커밋 + #854 병합 2회).
- GitHub CI (head `537303f`): **build ✅ · mysql-integration-test ✅ · isolated(Studio/MCP) ✅ · sample-only(수집기) ✅** ([Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/39731469483), [Studio/MCP](https://github.com/woori73/feel2real-buildit/actions/runs/39731468791), [수집기](https://github.com/woori73/feel2real-buildit/actions/runs/39731468754))
- 로컬 재검증(CI 워크플로와 동일 명령, 실제 MySQL 8.0.46): Studio·MCP·OAuth·#854 통합 **169건 통과**(실패 0, 건너뜀 0) + `bootJar`; Wiki Python 34건 통과.
- **주의:** 이 브랜치는 #853과 #854 코드를 병합한 상태다(Codex가 병합). #855를 병합하면 #853·#854도 함께 들어간다.

## 3. Flyway 충돌 해결 결과

| 항목 | 결과 |
|---|---|
| 최신 main 최고 번호 | V1093 (`7472d91`) |
| #854 | `V1094__unified_assistant.sql` 유지 |
| #855 | `V1095__studio_and_mcp.sql`로 변경 (Codex와 같은 결론) |
| 신규 DB에 전체 적용 | 통과 — 194건, 앱 기동·Hibernate `validate` 통과 (실제 MySQL 8.0.46) |
| **운영과 같은 업그레이드** (main 스키마 V1093 → #855 브랜치) | 통과 — V1094, V1095 두 건만 적용, 앱 기동 통과 |
| 순서를 뒤집은 경우 (V1095 적용 후 V1094) | **기동 실패** `Detected resolved migration not applied: 1094` — 운영 502 재현 시나리오 |
| 문서 충돌 (`API/DOMAIN/FLOWS`) | 두 PR 내용 모두 보존하여 해결 (#855에 #854 병합 시 충돌 3건 → 양쪽 유지) |

**필수 규칙:** `V1094`(#854)가 반드시 먼저 병합·배포되어야 한다. #855(V1095)를 먼저 배포하면 이후 V1094를 적용할 수 없다(앱 기동 실패). `outOfOrder`는 켜지 않았다.
**주의:** #854의 `V1094`는 감사 테이블 컬럼(`operation`, `academy_id`, `business_id`) 추가를 위해 같은 파일을 수정했다. main·운영에는 미적용이라 규칙 위반은 아니지만, **이전 V1094를 이미 적용한 개발자 로컬 DB는 체크섬 불일치**가 나므로 로컬 DB를 재생성하거나 `flyway repair` 해야 한다.
검증 DB는 실제 MySQL 8.0.46이다(MariaDB는 기존 `V972`의 `DROP CHECK` 때문에 처음부터 불가). 운영 RDS 버전 차이는 확인하지 못했다.

## 4. OAuth 연결 준비 및 실접속 결과

**공식 규격 확인 (웹 문서 대조)**

| 대상 | 공식 규격 | 서버 상태 |
|---|---|---|
| ChatGPT | 커넥터별 콜백 `https://chatgpt.com/connector/oauth/{callback_id}` (구 `connector_platform_oauth_redirect`는 레거시) — [OpenAI Apps SDK 인증](https://developers.openai.com/apps-sdk/build/auth) | 설정(`app.mcp.redirect-uris`)에 **정확한 URL을 등록**하고 형식 정규식도 통과해야 허용(Codex의 보수적 설계 유지). 와일드카드 등록은 없음 |
| Claude | `https://claude.ai/api/mcp/auth_callback`, 향후 `claude.com`도 허용 권장 — [Anthropic 안내](https://support.claude.com/en/articles/11503834) | 두 콜백을 기본 허용으로 추가, Origin에도 `claude.com` 추가 |

**서버 측 시험 (직접 수행)**

- 공식 Python MCP SDK 2.3.0 클라이언트 2개가 각각 등록→동의→PKCE 교환→`initialize`→`tools/list`(19개)→실제 조회→검색→**쓰기 거부**를 수행했고 두 응답 내용이 동일(`sameContent=true`). 결과: `docs/ai-agent-studio/protocol-results-claude-rerun.json`.
- 실제 HTTP 시험(`McpOAuthDefaultsHttpTest`, `McpStudioMySqlTest`): 허용 외 redirect 거절, 상태(state) 누락·잘못된 `resource`·PKCE `plain` 거절, 무토큰·위조 토큰 401 + `WWW-Authenticate: resource_metadata`, 코드 1회용/만료, 리프레시 재사용 시 grant 폐기, 권한 회수 후 접근 중단, 관리자 민감 범위 확인 체크 없이 동의 시 400.
- 인증 실패·토큰 만료·권한 거부는 위 시험에 포함.
- 설명서: `CHATGPT_CONNECTION.md`, `CLAUDE_CONNECTION.md`, `CONNECTION_TEST.md`.

**실제 ChatGPT·Claude 접속: 하지 못했다 (미완료).** 이 환경에는 공개 HTTPS 주소와 두 제품의 로그인 세션이 없다. Claude가 가능한 단계(서버 구현, SDK 시험, 설정 설명서)까지 완료했고, 이후는 사용자 조작이 필요하다.

1. 격리 테스트 서버를 공개 HTTPS로 띄우고 `app.mcp.enabled=true`, `app.mcp.public-base-url`, `app.mcp.redirect-uris`(ChatGPT 앱 관리 화면에 표시되는 정확한 콜백 포함) 설정 — 운영 활성화는 별도 승인.
2. ChatGPT / Claude에서 커스텀 커넥터 추가(OAuth 자동 등록) → Feel2Real 본인 계정 로그인 → 필요한 서비스 범위만 동의.
3. [CONNECTION_TEST.md](../ai-agent-studio/CONNECTION_TEST.md) 절차로 동일 자료 ID·SHA를 두 제품과 스튜디오에서 대조.

## 5. 제작 스튜디오 완성도

| 요구 | 상태 | 비고 |
|---|---|---|
| EP01 68컷 ZIP·JSON 일괄 등록 | 구현·시험됨 (Codex) | 업로드 → `fileId`로 등록. **정확히 68컷** 필요, ZIP은 항목 100개·해제 합 20 MiB 이하. 미리보기 없음 |
| 각본·콘티·이미지·대사·연출·검수 상태 연결 | 부분 | 컷 단위로 content/dialogue/direction/이미지 1개 연결, 상태는 REVIEW 고정. **영상 연결 없음** |
| 이전 버전 복원 | 구현·시험됨 (Codex) | 새 USER_EDIT 버전, 승인본 불변, 최신 변경 시 409 |
| 200건 제한으로 인한 누락 | 해결 (Codex `/api/studio/cuts`) | 컷 보드가 68개 head를 별도 조회 |
| PC·모바일 컷별 열람·비교·승인 | 시험됨 (Codex 브라우저 QA) | 모바일 390px 승인·복원 포함. **이번 세션에서 브라우저 QA는 재실행하지 못함** |
| 원본 SHA-256 보존 | 시험됨 | 복원·일괄 등록은 기존 행 불변 |
| 사용자 승인 없는 정본 변경 차단 | 시험됨 | 일괄 등록·복원·AI 제안 모두 `approved_id` 미변경 |
| r14~r8 업로드 가능 여부 | **형식·라벨 확인, 실물 미보유** | `R14_R8_READINESS.md`. 20 MiB 초과 ZIP은 일괄 등록 불가(분할 필요) |
| JSON 문법 검사 | Claude 추가 | 깨진 JSON은 업로드 단계에서 400 |

## 6. MCP 서비스별 실제 조회 가능 범위

정책(실행 허용) **406건**, 운영 호출은 하지 않음. 카탈로그에 보이는 경로(1,098)와 실행 허용은 구분한다. 새 엔드포인트는 자동 허용되지 않는다.

| 서비스 | 허용 경로 | 실데이터 / 비고 |
|---|---:|---|
| AI AGENT 스튜디오 | 11 | 실데이터 10(자료·파일·이력, 스튜디오 권한 적용) |
| AcademyCraft | 15 | 게이트웨이 프록시 13(학생·반·출결·상담·공지 등, 학원 권한 재사용) — 별도 백엔드 실데이터 대조는 **미검증** |
| 이지장부 | **1** | `/api/mcp/finance/summary` 월 합계만(사업체 허용 검사). 거래 원문·증빙·계좌는 제외 |
| 입시상담 AI | **1** | 본인 상담 이력만. 관리자 지식·원자료·통계·재색인 상태는 제외 |
| 내신·모의고사 위키 | 74 | 프록시 27(사용자별 SQLite `mode=ro`) |
| 문법 문제은행 | 15 | API 13 |
| 홈페이지 빌더(BUILD IT) | 35 | 학원 페이지·작업 이력 |
| 관리자 대시보드·회원 | analytics 5 · members 10 | 화면 + 일부 API (ADMIN) |
| 기타 | learning 88, content 62, newstart 35, wemade 27, chart 19, choonggi 8 | |

- **참고(Codex 결정):** 이지장부·입시상담 범위는 개인정보·금융정보 최소 노출을 위해 대폭 **축소**되었다. 사용자께서 요청하신 "입시상담 AI 조회 범위 **개선**"과 방향이 다르므로(보수적으로 줄임), 확대할지는 사용자 결정이 필요하다. 읽기 도구가 없어도 대화 자체는 POST(LLM 비용)라 MCP에 노출하지 않는다.
- 학생·회원 정보 최소 노출: 전화번호·이메일·주민번호·계좌번호 형태를 응답에서 마스킹(`McpRedaction`, 휴리스틱이며 권한 검사를 대체하지 않음).
- 동시 호출: 서버 전체 8 + **사용자별 2**(Claude 추가), 사용자별 호출 한도(Codex), 검색 한 번의 내부 호출 3건 이하 + 호출마다 한도 집계(Claude).
- 관리자 민감 범위 동의 경고·확인 체크: 동의 화면에 경고, 체크 없이는 400(Codex 구현, Claude HTTP 시험).
- GET 부수효과·외부 비용 조사: 두 조사(Codex `GET_SAFETY_AUDIT.md`, Claude 정적 조사 `GET_SIDE_EFFECT_SURVEY.md`)가 일치. 쓰기·외부 호출 GET(상담 열람 상태 저장 2건, 외부 위키 호출 5건 등)은 정책에서 제거됨. 허용 경로에서 DB/파일 쓰기 표지 0건, 외부 AI 호출 0건. 정적 분석의 한계(프록시 대상 업스트림, S3 읽기 48건 비용)는 문서에 있음.
- 초기 MCP는 읽기 전용 유지. 쓰기 도구 없음.

## 7. 변경 전후 비교표 (내 검수 보고서 기준)

| # | 이전 지적 | 우선 | 처리 | 상태 |
|---|---|---|---|---|
| 854-1 | 출결 "조회"가 쓰기 초안으로 분류 | P1 | 조회 의도 우선 판정, 읽기 전용 `ATTENDANCE_QUERY`. "출결 **기록** 조회"도 조회로(Claude 추가 수정) | 해결 |
| 854-2 | 출결 "정정"이 새 기록 추가 | P1 | 정정 API 근거 없어 **차단 + 기존 화면 안내** | 해결(기능은 미제공) |
| 854-3 | 공지 초안 미지원 | P1 | 저장·발송 없는 문안 초안 `NOTICE_DRAFT` | 해결 |
| 854-4 | Muse 사용량 제한 없음 | P1 | 사용자별 분/일/자료당 상한 + 규칙 폴백 + 연락처 포함 텍스트는 모델 미전송 | 해결(인스턴스 단위) |
| 854-5 | Origin 하드코딩 | P2 | `allowed-origins` 설정화 | 해결 |
| 854-6 | 감사로그 범위 부족 | P2 | operation·학원/사업체 추가 | 해결 |
| 854-7 | EXECUTING 잔류 | P2 | 5분 경과 시 UNKNOWN 전환(자동 재실행 없음) | 해결 |
| 854-8 | 투어-이동 미연동·목적 추천 없음 | P2 | 목적 선택, 메뉴 클릭 시 서버 단계 저장 후 이동 | 해결(JS 단위시험, 실브라우저 미확인) |
| 854-9 | 약속 문구 불일치 | P3 | 정정 문구 정정 | 해결 |
| 854-10 | LLM 개인정보 | P2 | 연락처 패턴 포함 텍스트는 모델 호출 안 함 | 해결 |
| 854-11 | 승인 전 import 배치 저장 | P2 | 의도된 동작으로 문서화, 고아 배치 정리는 향후 | 문서화 |
| 853-link | #854가 #853의 승인 전 배치 계정 변경/확정 가능 | P2 | `LEDGER_ACCOUNT`는 **비서가 만든 배치만** 허용(403) + 시험 | 해결 |
| 855-1 | ChatGPT redirect URI | P1 | 현행 규격 반영(정확 등록 + 형식 검사), claude.com 추가 | 해결(실접속 미확인) |
| 855-2 | V1094 충돌 | P1 | V1095, 실DB 연속·업그레이드 시험, 역순 실패 확인 | 해결 |
| 855-3 | 복원 없음 | P2 | Codex 구현 확인 | 해결 |
| 855-4 | 일괄 등록 없음 | P2 | Codex 구현 확인 | 해결(제약 있음) |
| 855-5 | 200건 창 누락 | P2 | `/cuts` | 해결 |
| 855-6 | 모바일 승인 미시험 | P2 | Codex QA에 포함 | 해결(재실행 못 함) |
| 855-7 | 프록시 뒤 IP 제한 | P2 | Codex `AdmissionsRateLimiter` 신뢰 프록시 판별 사용 | 해결(운영 토폴로지 미확인) |
| 855-8 | 전역 동시성 2 | P2 | 서버 8 + 사용자별 2 | 해결 |
| 855-9 | 관리자 민감 범위 경고 | P2 | Codex 구현 + HTTP 시험 | 해결 |
| 855-10 | HTML/JSON 개인정보 | P2 | 마스킹 추가 | 해결(휴리스틱) |
| 855-11 | 부수효과 GET | P2 | 정책에서 제거 + 정적 조사 | 해결 |
| 855-12 | 대용량 원본 byte[] 적재 | P2 | `FileStorage`에 스트림 API 없어 **미해결** | 열림 |
| 855-13 | 입시상담 조회 범위 | P2 | 오히려 축소됨 | 사용자 결정 |

## 8. 남은 결함

- **P0:** 없음.
- **P1:** (a) 실제 ChatGPT·Claude 연결 미실행(사용자 조작 필요). (b) 병합 순서 위반 시 기동 실패(규칙으로 관리).
- **P2:** 출결 정정 기능 미제공(차단만) · Muse 한도가 인스턴스 단위(다중 인스턴스 시 공유 저장소 필요) · 실제 Muse 응답 미검증 · #854 Playwright E2E를 Docker 부재로 이번에 재실행하지 못함 · 스튜디오 원본 100 MiB `byte[]` 적재와 일괄 등록 20 MiB·정확히 68컷 제약 · 일괄 등록 중 파일 저장이 DB 트랜잭션 안이라 롤백 시 고아 파일 가능 · 승인 전 import 배치 고아 정리 · 영상 컷 연결 없음 · 이전 V1094 적용 로컬 DB 체크섬 불일치 · #855 브랜치에 #853·#854가 포함됨 · 프록시 토폴로지(IP 제한)·업로드 한도(nginx) 운영 구성 미확인 · AcademyCraft 실데이터 격리 미검증.
- **P3:** 마스킹이 휴리스틱 · S3 읽기 48건 비용 · 기존 JS 테스트 40건 실패(변경 전 main과 동일) · `AcademyCraftGateway`의 Origin 하드코딩(기존 코드) · 로컬 MariaDB 비호환(기존 `V972`).

## 9. #853과 연동 시 주의

(상세: [PR853_NOTES.md](PR853_NOTES.md), 점검 시점 헤드 `f4ab861`)
- #853 자체: P0 없음. 자격증명·쿠키·OTP 저장/로그 없음, 사업체 격리 양호, Playwright는 loopback mock 도메인으로만 코드 차단(실기관 접속 불가). 최신 CI 3건 성공.
- #853은 #854를 포함한다(마이그레이션 자체는 없음). 병합 순서는 **#854 → #853 → #855**.
- 병합 시뮬레이션(`git merge-tree`, 2026-10-22 06:10): #854+#853 충돌 없음, #854+#855 충돌 없음. #853 브랜치(`60479f1`)의 `db/` 변경은 #854와 동일(= Claude의 V1094 수정본까지 반영됨).
- MCP는 #853 수집 API·#854 초안 승인 POST를 호출할 수 없다(GET 전용, 정확 경로 허용목록). 이지장부는 월 합계 1경로만 허용하므로 #853이 stage한 승인 전 배치의 원문은 MCP로 읽히지 않는다.
- #854의 `LEDGER_ACCOUNT`는 이번 수정으로 비서가 만든 배치만 변경한다. **남은 갭(기존 구조):** 기존 `POST …/imports/{id}/confirm`은 배치 출처를 보지 않아 #853 수집 배치를 수집 승인 없이 확정할 수 있다 — 서버측 가드는 #853 범위라 수정하지 않았다(P2).
- #854 CSV 경로는 #853의 `FIN_` 범위 키가 없어 정규화 차이 시 중복 반영 가능성(P2, 실 MySQL 검증 필요).

## 10. 운영 배포 전 필요한 사용자 승인

1. 병합 순서 승인: #854 → #853 → #855 (Draft 해제 포함).
2. `buildit.unified-assistant.write-enabled`, `model-analysis-enabled`의 운영 활성화 여부(기본 OFF), 실제 Muse 호출 비용 상한.
3. `app.mcp.enabled` 운영 활성화 및 `public-base-url`·`redirect-uris` 값, 외부 AI에 제공할 서비스 범위(관리자 민감 범위 포함).
4. 입시상담·이지장부 MCP 조회 범위를 현재(최소)로 둘지 확대할지.
5. 출결 정정 기능 개발 여부(AcademyCraft 정정 API 규격 확인 필요).
6. 실제 ChatGPT·Claude 연결 시험(본인 계정) 및 실데이터 대조.
7. 운영 프록시 구성(신뢰 프록시·본문 한도) 확인, 로컬 개발 DB 재생성 안내.

## 11. 권장 병합 순서

1. **#854** (V1094) — CI 성공 확인 후.
2. **#853** — #854 병합 후 최신 main으로 동기화(자체 마이그레이션 없음), CI 성공 확인.
3. **#855** (V1095) — #853·#854가 이미 main에 있으면 diff가 #855 고유 변경으로 줄어든다. 병합 직전 `git merge origin/main`으로 충돌·번호 재확인.
각 단계 배포 후 앱 기동(Flyway validate) 확인 뒤 다음 단계로 진행한다.

## 12. 최종 판정

- **#854: 조건부 가능.** 지적한 P1·P2를 해결했고 CI·로컬 시험 통과. 조건: 승인 사항 10-2, 실브라우저(E2E) 재확인, 실제 Muse 응답 미검증 인지.
- **#855: 조건부 가능.** 보안 설계·스튜디오 기능·Flyway는 검증됨. 조건: ChatGPT·Claude **실제 연결 확인 전에는 연결 기능을 완료로 보고하지 않는다**, #854·#853이 먼저 병합, 대용량 원본 부하 확인.
- 두 PR 모두 사용자 최종 병합·배포 승인만 남은 상태가 아니라, **위 조건(특히 실제 연결 시험과 승인 사항)이 남아 있다.**
