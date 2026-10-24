# ChatGPT·Claude ↔ AI AGENT 제작소 연결 (기존 MCP 재사용)

상태: **코드·격리 검증 단계. 운영 MCP는 아직 꺼져 있다(`/mcp` → 503 `MCP_DISABLED`, 2026-10-24 확인).** 실제 ChatGPT 채팅 연결 시험은 운영 활성화 승인 후 수행한다.

## 1. 기존 구현 확인 결과 (2026-10-24, main `e1e832fb`)

| 항목 | 상태 |
|---|---|
| PR #855 (통합 MCP + 제작 스튜디오) | 병합·배포됨. `/api/studio/project` 운영 응답 401(로그인 필요) = 경로 존재 |
| 운영 MCP | `https://feel2real.co.kr/mcp`, `/.well-known/oauth-*` 모두 **503 `MCP_DISABLED`** — `APP_MCP_ENABLED` 미설정 |
| 인증 | OAuth 2.1 + PKCE(S256), 동적 등록, 정확한 callback 허용 목록(`claude.ai`, `chatgpt.com/connector/oauth/*`, `chatgpt.com/connector_platform_oauth_redirect`), 15분 access token, 30일 refresh |
| 관리자 권한 | 제작소 전 경로는 ADMIN + 운영 계정(modu)만(`StudioAccessGuard`). MCP 도구도 같은 판정을 서비스에서 다시 검사 |
| 기존 도구 | 읽기 전용 21개(`read_studio`, `studio_document`, `studio_image` 등). 쓰기 도구 0 |

ChatGPT가 연결되지 않은 직접 원인은 **운영 MCP 비활성**이다. 코드가 없어서가 아니다.

## 2. 이번 PR이 보완한 것

기존 `/mcp`·OAuth·제작소 서비스를 그대로 쓰고, 부족한 도구만 추가했다. 새 서버·새 인증은 없다.

| 도구 | 필요 동의 | 동작 |
|---|---|---|
| `list_studio_assets` | `studio:read` | 작업 ID별 최신 버전 목록(작업 ID·버전·파일 URL·SHA·검수 상태) |
| `read_studio_asset` | `studio:read` (+이미지는 `originals:read`) | 문서 본문 / 콘티 이미지 블록 / ZIP 목록 / 영상·음악 메타데이터·SHA |
| `get_asset_versions` | `studio:read` | 모든 버전·검수 보고서·수정 작업·사람 결정 (v1↔v2 비교용) |
| `list_studio_tasks` | `studio:read` | Claude/ChatGPT/사람 담당 수정 작업 |
| `submit_studio_review` | `studio:propose` | 검수 보고서(PASS·NEEDS_CHANGES·COMMENT, 문제점·타임코드·컷) — **승인 아님** |
| `create_studio_task` | `studio:propose` | Claude 수정 작업 생성(검수 보고서 연결) |
| `upload_studio_asset` | `studio:propose` | 개선 파일을 **새 버전**으로 등록(base64 ≤ 8 MiB) |
| `update_studio_task` | `studio:propose` | 작업 IN_PROGRESS·DONE·CANCELLED, 결과 버전 연결 |

대용량(러프 영상 MP4 등 ≤ 100 MiB)은 같은 OAuth 토큰으로 `POST /mcp/studio/files`(multipart)에 등록한다.

### 최소 권한
- `studio:propose`는 **별도 체크박스·기본 해제·위험 확인 필수**. 읽기 연결(`studio:read`)만 준 ChatGPT는 쓰기 도구가 `studio_propose_scope_required`로 거부된다.
- 승인·승인 해제·복원·삭제·공개·배포 도구는 **어떤 scope로도 없다**. 승인은 제작소 화면의 사람 APPROVER만 한다.
- 연결 동의는 프로젝트 역할을 대신하지 않는다. VIEWER 회원은 동의가 있어도 등록할 수 없다(403).
- AI 등록은 항상 `AI_PROPOSAL` 새 버전. 기존 파일·이전 버전·승인 포인터는 바뀌지 않고, 오래된 `expectedHeadRevisionId`는 409.
- 업로드는 서버가 bytes를 끝까지 받아 SHA-256·확장자/실제 형식(MP4/MOV/WAV/MP3/FLAC/PNG/JPG/WEBP/PDF/MD/HTML/JSON/ZIP)을 확인한 뒤에만 등록하고 `serverVerified=true`를 돌려준다. 실패 시 새 S3 객체는 롤백 정리된다.
- 검수 보고서·작업에는 연결 client 이름·grant ID·실제 사용자 ID가 남는다. client/모델 이름은 클라이언트가 신고한 값이며 모델 추론의 증명은 아니다.
- `/mcp` 요청 본문 상한은 기존 64 KiB를 유지하고, `studio:propose` 토큰만 12 MiB까지 허용한다.
- 공개 중간 결과물과 내부 제작 파일은 분리: 모든 파일 URL은 로그인·제작소 권한이 필요한 `/api/studio/files/{id}/content`다. 비인증 공개 URL을 만들지 않는다.

### DB
`V1096__studio_ai_reviews_and_tasks.sql` — `studio_reviews`, `studio_tasks` 추가만. 기존 테이블·V1094/V1095 불변. (참고: 병합 제외된 #862 브랜치도 V1096을 쓰므로 되살릴 경우 번호를 바꿔야 한다.)

## 3. 운영 활성화

운영 사전 점검(2026-10-24, `prod-readonly-status` 읽기 전용): Flyway 190건·최신 V1095·실패 0, V1094/V1095 checksum 저장소와 일치, **V1096 미적용**(→ #862의 V1096~V1098도 운영에 적용된 적 없음, 충돌 없음). ALB 대상 2개 healthy, actuator 200. `APP_MCP_ENABLED`·`APP_ADMISSIONS_RATE_LIMIT_TRUSTED_PROXIES` 미설정.

활성화는 `Deploy to EC2` 수동 실행의 `mcp_switch=studio-chatgpt`로만 한다(기존 assistant/hwpx 스위치와 같은 방식, env 스냅샷·배포 실패 시 자동 복원):
- `APP_MCP_ENABLED=true`, `APP_MCP_REDIRECT_URIS=https://claude.ai/api/mcp/auth_callback,https://chatgpt.com/connector_platform_oauth_redirect`
- 신뢰 프록시가 비어 있으면 이 인스턴스의 VPC IPv4 대역(IMDSv2)을 기록한다. 구할 수 없으면 MCP를 켜지 않는다. 이 값은 입시상담·문의·방문 통계 rate limit의 실제 클라이언트 IP 판별에도 쓰인다(현재는 모두 ALB IP로 묶여 있음).
- **허용 scope는 `application-prd.yaml`의 `app.mcp.allowed-scopes` = `studio:read studio:propose originals:read offline_access`**. 학원·재무·입시·회원·위키 등 다른 서비스 scope는 메타데이터·동의 화면·도구 목록에서 빠지고, 예전 grant에 있어도 실행 시 무시된다.
- 끄기: `mcp_switch=off`.

확인: `GET /.well-known/oauth-authorization-server` 200, `scopes_supported`가 위 4개.

## 4. ChatGPT 채팅 연결 절차 (사용자 작업)

1. ChatGPT → 설정 → Apps & Connectors → 고급 → **개발자 모드** 켜기(쓰기 도구 사용에 필요).
2. 커넥터 만들기: URL `https://feel2real.co.kr/mcp`, 인증 OAuth.
3. 열린 Feel2Real 동의 화면에서 ADMIN 또는 modu로 로그인 → `AI AGENT 제작 자료`, `제작소 검수 의견·수정 작업·새 버전 등록`, (콘티 이미지 열람 시) `원본·이미지 파생본` 선택 → 위험 확인 체크 → 연결.
4. 새 채팅에서 커넥터를 켜고 시험(§5).

callback이 위 허용 목록과 다르면 동의 화면 전에 `invalid_redirect_uri`가 난다. 그때는 ChatGPT가 보여 준 callback을 `APP_MCP_REDIRECT_URIS`에 정확히 추가한다(와일드카드 금지).

## 5. 실제 검증 기준 (`ai-agent-part1`)

| 단계 | 판정 근거 |
|---|---|
| 1 Claude 테스트 파일 등록 | 응답 workId·revisionId·version·file.url·sha256·reviewStatus, `serverVerified=true` |
| 2 ChatGPT 목록 조회 | ChatGPT 화면에 같은 workId·SHA |
| 3 ChatGPT 실제 파일 읽기 | 문서 본문 일부/콘티 이미지 세부를 메타데이터에 없는 내용으로 답함 |
| 4 ChatGPT 검수 의견 등록 | `studio_reviews` 행: author_type=AI_CONNECTOR, ai_client=ChatGPT 연결 이름 |
| 5 Claude 의견 확인 | Claude 세션의 `get_asset_versions`에 같은 reviewId·문제점 |

**Claude 자체 시험(공식 callback으로 등록한 로컬 시험 클라이언트)과 실제 ChatGPT 채팅 시험을 구분해 보고한다.** 격리 시험 통과는 실제 ChatGPT 연결 완료를 뜻하지 않는다.

## 6. 알려진 한계

- 영상·음악은 MCP 대화에 재생 형태로 전달되지 않는다. ChatGPT는 메타데이터·SHA·변경 내용·검수 이력으로 검토하고, 원본 bytes는 `originals:read` 동의 후 청크로만 받는다. 프레임 추출(콘택트 시트)은 후속 과제.
- ChatGPT가 대화 안에서 만든 큰 파일을 base64로 보내는 것은 모델 출력 길이에 막힐 수 있다. 실무상 검수 문서(MD/JSON)·작은 이미지가 대상이다.
- Claude Code CLI의 로컬 callback(`http://localhost:*`)은 허용 목록에 없다. Claude는 claude.ai 커넥터로 연결한다. CLI 직접 연결은 별도 보안 검토 후 추가한다.
