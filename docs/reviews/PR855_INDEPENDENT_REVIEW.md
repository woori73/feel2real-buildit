# PR #855 독립 검수 보고서 — AI AGENT 제작 스튜디오 · 통합 MCP

- 대상: `feat/ai-agent-studio-unified-mcp` @ `1a280ced` (base `main` @ `07d9316a`), Draft 유지
- 방식: 코드 정적 검수(OAuth·게이트웨이·카탈로그·스튜디오·SQL·Wiki 읽기전용·프런트) + 정책 파일 483건 집계 + 공식 문서 대조(웹) + PR #854와 병합 시뮬레이션(`git merge-tree`)
- 하지 않은 것: 앱 기동, 실제 ChatGPT/Claude 연결, 유료 API 호출, 운영 접근, Codex 브랜치 수정, 병합·배포

## 0. 최종 판정: **조건부 가능** (연결 기능은 "완료" 아님)

보안 설계(읽기 전용 위임 토큰, 정확한 대상 바인딩, scope + 기존 권한 이중 검사, 승인본/최신본 분리)는 우수하다.
다만 아래 3가지는 병합 전 필수다. ① **V1094 번호가 #854와 충돌**, ② **ChatGPT 리디렉션 URI 허용 목록이 현행 규격과 달라 연결 실패 가능성이 높음**, ③ **실제 ChatGPT·Claude 연결·실제 데이터 대조는 아무도 검증하지 못함**.

## 1. 검증 범위와 한계

| 항목 | 상태 |
|---|---|
| CI(build / mysql-integration-test / isolated) | 확인됨, 3건 success (head `1a280ced`) |
| 자체 보고(Java 106+15, Python 34, Chromium 20, SDK client A·B) | 재실행 안 함. 결과 파일과 코드의 일치만 확인 |
| 실제 `/ai-agent-studio` 화면 | **직접 확인 불가** — 이 세션에 Docker/MySQL 없음. PR의 `browser-results.json`·캡처와 코드로 판단 |
| 1,079 매핑 | 런타임(`RequestMappingHandlerMapping`) 산출이라 **앱 기동 없이 재현 불가**. 정책 파일 483건만 직접 집계 |
| ChatGPT·Claude 실제 연결 | **미검증**(공개 HTTPS endpoint + 사용자 본인 계정 필요) |
| AcademyCraft 실데이터·타 학원 대조 | **미검증**(별도 백엔드) |

## 2. 제작 스튜디오 검수

양호:
- 접근: 비멤버 403, ADMIN은 APPROVER 자동, 멤버십 VIEWER/EDITOR/APPROVER. 프로젝트 개설·멤버 관리는 ADMIN 전용.
- **정본 보호**: 새 버전 저장은 `head_id`만 바꾸고 `approved_id`는 건드리지 않음. 승인(`APPROVE`)은 APPROVER만, 이벤트 기록. AI 제안(`AI_PROPOSAL`)은 자동 승인 불가. 동시 저장은 `FOR UPDATE` + parent 비교로 409.
- 업로드: 확장자 화이트리스트(pdf/html/json/md/zip/png/mp4) + pdf/png/zip/mp4 매직넘버 + html/json/md 엄격 UTF-8·NUL 검사, 100MiB 제한, 스트리밍 SHA-256 계산, 동일 SHA 중복 방지, 파일명 경로문자 치환.
- 열람 안전: 원본은 `attachment` + `CSP sandbox` + `nosniff`, ZIP은 목록만(압축 해제 안 함), 화면은 `innerHTML` 미사용(0건) → 저장 HTML 스크립트 실행 차단. `storage_key`는 응답에서 제거.
- 쓰기는 `Origin == app.studio.public-base-url` 검사(설정값이라 #854보다 낫다).
- 마이그레이션은 신규 테이블만 추가. 기존 데이터 영향 없음.

결함:
- **P2 버전 "복원" 기능이 없다.** 서버·UI 어디에도 복원 동작이 없고(검색 0건), 과거 버전을 "승인"으로 지정하는 것이 유일한 대체 수단이다. 요청한 "버전 복원"은 미구현으로 보고한다.
- **P2 EP01 r7 일괄 등록 수단이 없다.** 컷은 UI에서 한 컷씩 폼 저장해야 한다(68회). ZIP/JSON을 올려도 보관만 되고 컷으로 분해·등록되지 않는다. 라벨 `r7` 자체는 허용(`[a-zA-Z0-9._-]`)되고, 실제 EP01 자료는 아직 없어 등록 가능 여부는 **파일 단위 보관만 가능, 컷 단위는 수작업**으로 판정한다.
- **P2 컷 현황이 최근 200건 창에 의존한다.** 목록 API는 `ORDER BY created_at DESC LIMIT 200`이고 UI는 그 결과에서 컷을 찾는다. 68컷 × 3버전(≈204건)이 넘으면 오래된 컷이 "업로드 대기"로 잘못 표시될 수 있다(이 구성은 코드로 추론, 실행 미확인). 또한 목록이 컷 본문(MEDIUMTEXT) 전체를 포함하므로 응답이 매우 커질 수 있다.
- **P2 메모리.** `/files/{id}/content`가 최대 100MiB를 한 번에 `byte[]`로 읽는다(동시 2개 제한 → 최대 ~200MiB 힙). PR도 "운영 부하 검증 남음"으로 인정. ZIP 미리보기는 임시파일에 전체를 다시 쓴다.
- **P2 모바일 승인.** 브라우저 결과는 모바일에서 "68컷 열람·가로 넘침 0·axe 0"만 확인한다. **모바일에서의 승인 동작은 시험 항목에 없다**(승인 UI는 코드상 `<dialog>` 모달로 존재). 실기기 확인 필요.
- **P3** 같은 SHA 파일을 다른 이름으로 올리면 조용히 기존 파일을 반환(이름 불일치 안내 없음). JSON 파일은 UTF-8만 검사하고 JSON 문법은 검사하지 않음. `origin`(ORIGINAL/AI_PROPOSAL/USER_EDIT)은 편집자가 자기 신고하는 값.
- **P3** 멤버 추가는 users 존재만 확인 — STUDENT 등 임의 역할 회원도 부여 가능(관리자 책임 설계).
- 확인 불가: 프록시/nginx의 업로드 본문 한도(앱 설정은 prd 200MB). 100MiB mp4 업로드는 인프라 한도에 좌우.

## 3. 통합 MCP 검수

도구 19개 확인: `list_catalog` 1 + `read_*` 15(scope별) + `search` + `fetch` + `activity_history` = 19. 모두 `readOnlyHint=true`. 기본 OFF(`app.mcp.enabled=false`).

읽기 안전성(코드 확인):
- 내부 위임: 게이트웨이가 사용자 신원으로 **15초짜리, 정확한 GET 대상 1건에 바인딩된 JWT**(`mcp_get` 클레임)를 발급해 `127.0.0.1`의 기존 API를 호출. 다른 경로·메서드면 403. 요청은 read-only 트랜잭션 + 강제 rollback. Wiki(Python)는 HMAC 서명 + SQLite `mode=ro` + `query_only`. 내부 URL은 loopback만 허용, 리다이렉트 비추종. **양호.**
- 경로 매개변수는 `[\p{L}\p{N}_-]{1,100}`만 허용 → 경로 이탈·SSRF 차단. 쿼리는 해당 엔드포인트의 `@RequestParam` 목록만, `size/limit` ≤100. 응답 2MiB 상한, JSON은 `password/secret/token/credential/cookie/storage_key` 키 제거.
- 정책 파일 없는 경로는 `endpoint_not_read_only`(403)로 차단(허용목록 방식). 쓰기·미검토 경로는 "readable=false".

OAuth(코드 확인): PKCE S256 필수, `resource` 바인딩, 코드 120초 1회용, 액세스 토큰 15분, 리프레시 회전 + **재사용 탐지 시 grant 폐기**, 자격증명 스탬프/역할 변경 시 토큰 무효, 동의 화면에서 scope 선택, 사용자별 연결 해제, 토큰은 해시 저장, 동의 시 감사 기록, 분당 호출 제한(사용자 60 / 엔드포인트 120 / 등록 5).

### 서비스별 조회 범위 (정책 483건 직접 집계)

| 서비스 | 정책 건수 | 구분 |
|---|---|---|
| AI AGENT 스튜디오(studio) | 10 | 실데이터 API 9 + 화면 1 (스튜디오 권한 그대로 적용) |
| AcademyCraft(academy) | 15 | 게이트웨이 프록시 13(실데이터: 학생·반·출결·상담·공지·시간표 등) + 도움말 페이지 2 |
| 이지장부(easy) | 66 | API 57(실데이터) + 화면 9 |
| 입시상담 AI(admissions) | **6** | API 5 + 화면 1 — 범위가 매우 얕음(상담 대화 전수 조회 여부 불명) |
| 내신·모의고사 위키(wiki) | 79 | 프록시 27(Python 위키 실데이터) + 나머지 |
| 문법 문제은행(grammar) | 15 | API 13 + 화면 2 |
| 홈페이지 빌더 | (buildit 35 / members 10 일부) | 별도 `builder` scope 없음. 학원 페이지·빌더 화면 중심. **"빌더 편집 상태" 전용 조회는 불명확** |
| 관리자 대시보드 | (analytics 5 / members 10) | 화면 + 일부 API. 통합 대시보드 전용 조회 아님 |

전체 483건 중 `/api/…` 실데이터 성격 323, 화면(SSR 본문 텍스트) 성격 160. 화면 조회는 `<script>/<style>/hidden input`을 제거한 본문 텍스트만 반환하며, **JS가 채우는 동적 데이터는 포함되지 않는다**(응답 `trust` 문구에 명시). 즉 "페이지 정보"와 "실데이터"는 구분되어 있다.

결함:
- **P1 (연결) ChatGPT 리디렉션 URI 불일치 가능성.** 허용 목록이 `chatgpt.com/connector_platform/oauth/callback`, `chat.openai.com/connector_platform/oauth/callback`인데, OpenAI Apps SDK 문서는 현행 콜백을 커넥터별 `https://chatgpt.com/connector/oauth/{callback_id}`로 안내하고 기존 `connector_platform_oauth_redirect`는 이미 게시된 앱의 레거시로만 유지한다고 명시한다. 동적 등록(DCR) 시 허용 외 URI는 `invalid_redirect_uri`로 거절되므로 **ChatGPT 연결이 실패할 가능성이 높다.** 해결: 설정값으로 분리하고 `https://chatgpt.com/connector/oauth/*` 패턴(또는 앱 관리 화면의 정확한 URL)을 허용. (출처는 §8)
- **P2 (연결) Claude** 콜백 `claude.ai/api/mcp/auth_callback`은 맞지만, Anthropic 문서는 `claude.com/api/mcp/auth_callback`으로 바뀔 수 있으니 둘 다 허용하라고 안내한다. Origin 허용(`claude.ai`)도 `claude.com` 누락. Claude Desktop(loopback 콜백)은 미지원.
- **P2 속도 제한 키.** `transport:`+`getRemoteAddr()` 300/분은 리버스 프록시/LB 뒤에서 **모든 사용자가 같은 IP로 집계**될 수 있다. `forward-headers-strategy` 설정과 함께 확인 필요(운영 구성 미확인).
- **P2 전역 동시성 2.** `Semaphore(2)`가 서버 전체 공유라 한 사용자의 느린 조회가 다른 사용자에게 `429 concurrency_limit`을 유발할 수 있다. 사용자별 제한 권장.
- **P2 `search`는 서비스별로 최대 5개 엔드포인트를 순차 호출**하며 각 호출이 기존 API를 실제로 때린다(요청 1건 = 내부 호출 최대 5회). 한 번 호출이 `research` 용도로 반복되면 DB 부하가 커진다.
- **P2 HTML 본문 텍스트는 키 기반 redaction이 적용되지 않는다**(JSON만). 화면 본문에 포함된 개인정보는 그대로 반환된다 — 권한은 기존 SSR 인가에 의존.
- **P2 대용량 binary는 `offset`까지 스트림을 읽고 버린다**(`skipNBytes`). 반복 청크 조회는 매번 O(offset) I/O. PR도 부하 검증 미완료로 명시.
- **P3** `list_catalog`가 URL을 `https://feel2real.co.kr`로 하드코딩(설정값 `public-base-url` 미사용).
- 미검증: 정책 483건 중 **외부 비용을 유발하는 GET이 없는지**는 샘플(경로명 89건 위험 키워드 스캔) 수준이며, 정책의 `reason`에 "LLM·임베딩 호출 없음"을 적은 항목 외 전수 감사는 못 했다. 신규 코드 자체에는 OpenAI/Claude/임베딩 호출이 없다.

타 학원·사업체·회원 비공개 정보: 게이트웨이는 **새 인가를 만들지 않고 기존 컨트롤러·서비스의 소유권 검사를 사용자 신원으로 통과**시키는 구조라 설계상 안전하다. 그러나 이 보장은 "기존 API가 올바르다"는 전제에 의존하며, 특히 AcademyCraft(별도 백엔드)와 ADMIN 전용 `/api/admin/**` 읽기는 **실데이터 대조가 이뤄지지 않았다.** ADMIN 계정이 동의하면 외부 AI가 관리자 전용 조회 전체(`/api/admin/easy/transactions/export` 등)에 접근 가능하므로, 동의 화면에서 ADMIN 전용 scope를 별도로 경고하길 권고(P2).

## 4. ChatGPT · Claude 연결

| 단계 | 상태 |
|---|---|
| MCP 프로토콜(`initialize`/`tools/list`/`tools/call`/`ping`, 2025-03-26~11-25) | 코드 확인. 독립 Python SDK client 시험은 PR 보고(미재실행) |
| OAuth 메타데이터·DCR·PKCE | 코드 확인 |
| `401` + `WWW-Authenticate resource_metadata` | 코드 확인 |
| `GET /mcp` 405(무상태 Streamable HTTP POST 전용) | 가능하나 일부 클라이언트의 SSE 스트림 개방 시도는 405로 응답 — 대부분 허용되지만 실연결로 확인 필요 |
| ChatGPT 실제 연결 | **미확인 (+ 리디렉션 URI 수정 필요)** |
| Claude 실제 연결 | **미확인** |
| 필요한 사용자 단계 | ① MCP 켜기(`app.mcp.enabled`) ② 공개 HTTPS 도메인 ③ ChatGPT: 개발자 모드/커넥터 추가 → 로그인·동의 ④ Claude: 설정 > 커넥터 > 커스텀 커넥터 URL 추가 → 로그인·동의. 본인 구독 계정 필요 |

**연결이 확인되지 않았으므로 완료로 보고하지 않는다.**

## 5. 추가 AI API 비용

신규 코드에 OpenAI/Claude/Muse/임베딩 호출 없음(diff 검색 확인). 비용 발생 경로는 (a) 기존 인프라 DB/디스크 부하, (b) 외부 AI 제품(ChatGPT·Claude) 쪽 사용자 구독 — 서비스 비용 아님. 단, 위 "비용 유발 GET 전수 감사 미완" 한계가 남는다.

## 6. PR #854와의 충돌

- **V1094 중복(블로커):** #854 `V1094__unified_assistant.sql`, #855 `V1094__studio_and_mcp.sql`. CLAUDE.md가 명시한 "운영 위치 버전 중복 금지(= prd 502 원인)"에 해당. **먼저 병합되는 쪽이 V1094, 나중 쪽이 V1095로 변경**해야 하며, 두 PR 모두 번호가 문서(DOMAIN.md)에도 적혀 있으므로 함께 수정.
- **텍스트 충돌:** `git merge-tree` 결과 `docs/API.md`, `docs/DOMAIN.md`, `docs/FLOWS.md` 3개 충돌(양쪽이 같은 위치에 절 추가 — 해결은 둘 다 유지). `SecurityConfig.java`는 자동 병합 성공. 구현 파일 충돌 없음.
- 병합 순서 권고: #854는 P1 수정 대기 상태이므로, 먼저 확정된 PR이 V1094를 가져가고 다른 쪽을 리베이스.

## 7. 수정 필요 사항 (Codex 전달용)

| 우선 | 항목 | 위치 |
|---|---|---|
| P0 | 없음 (비공개 정보 노출·무단 변경 경로 미발견) | — |
| P1 | ChatGPT 리디렉션 URI 허용 목록 수정(`connector/oauth/*`), 설정값화 | `McpOAuth.redirects` |
| P1 | V1094 번호 충돌 해소(#854와 순번 합의, DOMAIN.md 동시 수정) | `db/migration` |
| P1 | 실제 ChatGPT·Claude 연결 및 실데이터 대조(사용자 계정) — 완료 전 "연결 완료" 표기 금지 | 운영 절차 |
| P2 | 버전 복원 기능 추가 또는 "미지원" 명시 | `StudioService`/UI |
| P2 | EP01 컷 일괄 등록(JSON/ZIP → 컷 분해, 미리보기 후 승인) 또는 수작업 가이드 | Studio |
| P2 | 컷 현황 200건 창·목록 응답 크기: 컷 현황 전용 집계 API, 목록에서 본문 제외 | `StudioService.revisions` |
| P2 | 모바일 승인 시나리오를 브라우저 시험에 추가 | `browser-qa.cjs` |
| P2 | 속도 제한의 클라이언트 IP(프록시 헤더) 및 사용자별 동시성 | `McpTransportSecurity`, `McpGateway` |
| P2 | Claude `claude.com` 콜백/Origin 허용 추가 | `McpOAuth`, `McpTransportSecurity` |
| P2 | HTML 본문 개인정보 redaction, ADMIN scope 동의 경고, `search` 내부 호출 상한 | Gateway/동의 화면 |
| P2 | 원본 100MiB 메모리 적재 → 스트리밍 응답 | `StudioController.content` |
| P3 | `list_catalog` URL 하드코딩, SHA 중복 시 파일명 안내, JSON 문법 검사 | 각 위치 |

## 8. 운영 배포 전 필수 조건

1. #854와 V1094 정리 후 로컬/스테이징 MySQL로 Flyway 전체 적용 확인(번호 역행·중복 없음).
2. 실제 ChatGPT·Claude 계정으로 연결 → 동의 → `tools/list` → 실제 자료 대조(타 학원·타 사업체 계정 포함 음성 시험).
3. 정책 483건 중 외부 비용·부수효과 GET 전수 감사 결과 첨부.
4. 대용량 원본 조회·`search` 부하 시험, 프록시 뒤 속도 제한 확인.
5. `app.mcp.enabled`는 승인 전까지 OFF 유지, 켤 때 `app.mcp.public-base-url`·리디렉션 URI 설정 명시.
6. 사용자(소유자) 승인. 본 검수 및 Codex 자체 시험은 실제 연결을 대신하지 않는다.

## 출처 (연결 규격)

- OpenAI Apps SDK 인증 문서: https://developers.openai.com/apps-sdk/build/auth
- OpenAI 커뮤니티(레거시 콜백 불일치 사례): https://community.openai.com/t/chatgpt-plugin-submission-cimd-json-is-missing-redirect-uri-needed/1397623
- Anthropic 커스텀 커넥터 안내: https://support.claude.com/en/articles/11503834 · https://claude.com/docs/connectors/building/authentication
