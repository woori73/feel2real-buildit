# 공개 격리 HTTPS 환경 — ChatGPT·Claude 실접속 및 EP01 실제 등록 시험 절차서

**이 문서는 절차서이며, 아직 어떤 단계도 실행되지 않았다(NOT TESTED).** 운영 MCP는 활성화하지 않는다. 사용자(소유자) 승인 후 사용자 본인 계정으로 수행한다.

## 0. 전제와 금지

- 대상 브랜치: #854 → #853 → #855 **병합 후 main이 아니라, 격리 환경에 배포한 #855 헤드**(또는 병합 순서 승인 후 main 빌드).
- **운영 DB·운영 S3·운영 도메인(`feel2real.co.kr`)에 연결하지 않는다.** 격리 환경은 별도 MySQL(8.0 또는 운영과 같은 버전), 별도 파일 저장소, 별도 JWT 비밀값, 합성 계정만 사용한다.
- `app.mcp.enabled=true`는 **격리 인스턴스에만** 설정한다. 운영 설정 파일·환경변수는 수정하지 않는다.
- 기본 OFF 유지: `buildit.unified-assistant.write-enabled`, `model-analysis-enabled`(MCP 시험에는 불필요).
- 실제 EP01 파일은 격리 환경 저장소에만 올린다. 시험 후 폐기 방법(§6)을 미리 정한다.

## 1. 격리 서버 준비 (사용자/운영 담당)

| 항목 | 값 |
|---|---|
| 공개 주소 | 별도 도메인 또는 임시 터널의 **HTTPS** 주소(예: `https://mcp-staging.<도메인>`). ChatGPT·Claude 서버가 인터넷에서 접속할 수 있어야 함 |
| 앱 설정 | `SPRING_PROFILES_ACTIVE`는 운영(prd)과 같은 구성이되 DB·저장소·비밀값은 격리용 |
| MCP | `app.mcp.enabled=true`, `app.mcp.public-base-url=<격리 HTTPS 주소>`, `app.mcp.internal-base-url=http://127.0.0.1:<포트>` |
| 콜백 | `app.mcp.redirect-uris=` 아래 §2·§3에서 확인한 **정확한 콜백 URL**만 쉼표로 나열 |
| 프록시 | ALB/nginx 뒤라면 `app.admissions.rate-limit.trusted-proxies=<프록시 대역 CIDR>` **필수**(비어 있으면 모든 익명 요청이 프록시 주소 하나로 집계) |
| 사용자 | 합성 ADMIN 1, TUTOR 1, 제작 담당(EDITOR/APPROVER) 각 1, 무권한 사용자 1 |
| 확인 | `GET https://<주소>/.well-known/oauth-protected-resource/mcp`와 `/.well-known/oauth-authorization-server`가 JSON으로 응답, `POST /mcp`(토큰 없이)가 401 + `WWW-Authenticate` |

## 2. ChatGPT 연결

1. ChatGPT(계정/조직이 커스텀 MCP 서버를 허용하는 플랜)에서 앱/커넥터 추가 화면을 열고, 이름 `Feel2Real 격리`, URL `https://<주소>/mcp`, 인증 OAuth를 선택한다.
2. 화면에 표시되는 **콜백 URL**(`https://chatgpt.com/connector/oauth/{callback_id}` 형태)을 그대로 복사해 §1의 `redirect-uris`에 추가하고 서버를 재기동한다. (서버는 설정에 정확히 등록된 값만 허용)
3. 연결 → Feel2Real 로그인(합성 사용자) → 동의 화면에서 **최소 범위**(`catalog:read`, `studio:read`)만 선택.
4. 도구 목록에 읽기 도구 19개만 있고 쓰기·승인 도구가 없는지 확인.
5. 증거(스크린샷/로그)는 개인정보·토큰이 없도록 마스킹해 저장.

## 3. Claude 연결

1. Claude의 커스텀 커넥터 추가에서 같은 URL을 입력, OAuth 클라이언트는 자동 등록(DCR)을 선택.
2. 콜백이 `https://claude.ai/api/mcp/auth_callback`이면 기본 허용. Anthropic 문서는 향후 `https://claude.com/api/mcp/auth_callback`으로 바뀔 수 있다고 안내한다 — **현재 서버는 `claude.com` 콜백을 허용하지 않으므로**, 화면이 이 값을 쓰면 코드 변경(Codex)이 필요하다.
3. 로그인·동의 후 §2-4와 동일하게 도구 목록 확인.

## 4. 동일 자료 조회·원본 SHA 대조 (두 제품 모두)

1. 스튜디오에서 자료 하나를 업로드(실제 EP01 파일은 §5 이후) → 화면에 표시된 `file.id`, 전체 `sha256`, `size_bytes`, `revision.id`, 승인 포인터를 기록.
2. 각 제품에서 `read_studio`로 `GET /api/studio/files/{id}`와 `GET /api/studio/revisions/{id}`를 호출해 위 값과 일치하는지 확인.
3. 원문은 `GET /api/studio/files/{id}/content`를 `offset=0, limit=65536`부터 청크 조회. **청크 SHA와 전체 파일 SHA를 혼동하지 말 것**; 전체 SHA는 모든 청크를 이어 붙인 바이트에서 계산해 스튜디오의 값과 비교.
4. 두 제품에서 얻은 결과를 서로, 그리고 스튜디오 화면과 3자 대조.

## 5. EP01 실제 원본 등록 (실제 파일 제공 후)

1. 먼저 로컬에서 manifest 형식을 확인한다: `episode=1`, `cuts` 배열이 **정확히 68개**, `cutNumber` 1~68 중복 없음, 항목은 `cutNumber/title/content/dialogue/direction/file`만. ZIP이면 루트 `manifest.json`, 항목 100개 이하, 해제 합계 20 MiB 이하.
2. 스튜디오 파일 탭에 업로드 → 화면의 SHA-256을 **원본 파일의 로컬 `sha256sum`과 대조**.
3. 일괄 등록 실행 → 결과가 "68컷 등록, 승인 0"인지, 모든 컷이 **미승인** 상태인지, 기존 승인 포인터가 그대로인지 확인.
4. 승인 권한자가 컷을 하나씩 승인 → 승인 포인터와 감사 이력 확인. 이전 버전 복원 → 새 미승인 버전이 생기고 기존 버전 행이 그대로인지 확인.
5. 모바일(390px)에서 컷 열람·비교·승인을 실기기 또는 브라우저 에뮬레이션으로 확인.
6. **합성 자료로 통과한 시험은 이 단계의 성공으로 보지 않는다.**
7. 등록이 실패하면(오류·시간 초과) 저장소에 미연결 파일이 남을 수 있다(현재 알려진 결함). 시험 후 §6의 확인 절차를 수행.

## 6. 부정 시험과 종료

- 부정 시험: 무권한 사용자 계정으로 연결해 스튜디오 자료가 거부되는지, TUTOR가 타 학원/사업체 ID로 조회 시 거부되는지, ADMIN이 민감 범위(AcademyCraft·이지장부·입시상담·회원·NewStart) 선택 시 **확인란 없이는 동의가 거부**되는지, 연결 해제(`/mcp/connections`) 후 같은 토큰이 401인지.
- 속도 제한: 짧은 시간에 다수 호출 시 429와 `Retry-After`.
- 종료: ① 두 제품에서 커넥터 삭제 ② `/mcp/connections`에서 연결 폐기 ③ 격리 인스턴스 `app.mcp.enabled=false`로 되돌리거나 인스턴스 종료 ④ 격리 DB/저장소의 합성·시험 자료 폐기(실제 EP01 파일은 소유자 지시에 따라 보관 또는 삭제) ⑤ 격리 저장소에서 `studio_files`에 연결되지 않은 객체가 있는지 확인(고아 파일 점검).
- 결과 기록 형식: 항목별 PASS/FAIL, 일시, 수행자, 사용 계정 종류(합성/실), 증거 파일명. 실제로 수행하지 않은 항목은 NOT TESTED로 남긴다.
