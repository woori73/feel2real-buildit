# AI AGENT 제작 스튜디오 + 통합 MCP 개발 보고서

현재 검수 반영·통합 결과는 [REVIEW_FIXES.md](REVIEW_FIXES.md)가 기준이다. 아래 최초 개발 수치·실행 기록은 이전 성공 증거로 보존한다. 실제 계정 연결과 Claude 재검수는 미완료이며 병합·운영 배포 금지다.

최초 개발 기록: Codex 단일 에이전트, 단일 개발 브랜치 `feat/ai-agent-studio-unified-mcp`. 기준 main `07d9316a293a30a84e13a09c42957bd64be71bd8`. 운영 DB/S3·원본 자료를 읽거나 변경하지 않았다. PR #850의 홈·3D 파일과 기존 금융 수집 PR #853의 구현 파일은 수정하지 않았다.

## 실제 구현

| 산출물 | 구현·검증 |
|---|---|
| `/ai-agent-studio` | Spring·Thymeleaf·실제 MySQL·기존 FileStorage 사용, PC/모바일 |
| 14화·EP01 68컷 | 실제 자료 미등록 항목은 업로드 대기. 임의 작품 파일·콘티·캐릭터·영상 시드 없음 |
| 자료 관리 | 각본·콘티·대사·연출·캐릭터 설정·영상·생성 작업 기록·검수, 파일 연결·새 버전·비교·작업 이력 |
| 원본·승인 | 7종 업로드, stream SHA-256·동시 중복 방지, 원본 불변, ORIGINAL/AI_PROPOSAL/USER_EDIT·사용자 승인 포인터 구분 |
| 자료 열람 | PDF 텍스트/HTML·JSON·MD 원문/ZIP 목록/PNG·MP4 미디어/원본 다운로드. PDF OCR·영상 생성 API를 호출하지 않음 |
| 권한 | ADMIN 개설·멤버 관리, VIEWER·EDITOR·APPROVER 명시적 부여, 원래 서비스 권한 재사용 |
| 작품 검색·채팅 | 등록 제목·본문·대사·연출의 키워드 검색과 근거 링크. 외부 생성형 AI 답변은 연결한 구독 클라이언트에서 수행 |
| 전체 서비스 조사 | 현재 통합 런타임 매핑 1098개, 읽기 허용 경로 405개 (최초 1079/483에서 축소·갱신), Wiki Python 선언 161개(미mount 구분). [전체 목록](SERVICE_INVENTORY.md) |
| `/mcp` | 원격 stateless Streamable HTTP, 도구 19개, OAuth·PKCE·scope·만료/폐기·감사·속도 제한, 기본 OFF |
| 기존 서비스 조회 | URL 목록 외 실제 기존 GET API 호출. 같은 사용자·원래 필터·소유권·tenant 검사 및 읽기 전용/rollback. 별도 Wiki 서명·SQLite mode=ro |
| 설명서 | [ChatGPT](CHATGPT_CONNECTION.md), [Claude](CLAUDE_CONNECTION.md), [실제 연결 비교 절차](CONNECTION_TEST.md) |
| 검증·인계 | [보안·성능·비용](SECURITY_PERFORMANCE_COST.md), [Claude 인계](CLAUDE_HANDOFF.md), JSON 시험 결과·PC/모바일 캡처 |

파일 삭제·기존 버전 덮어쓰기는 원본 불변 요구에 따라 제공하지 않았다. 수정은 새 버전, 반려·승인은 별도 이력으로 보존한다. 생성 작업은 사용자가 관리하는 기록이며 유료 모델 호출/실제 렌더 작업 생성 기능이 아니다.

## 실행한 시험

- Java 106건·Python 34건 통과. Java는 실제 MySQL 신규 스키마 부팅 및 HTTP OAuth 흐름과 기존 보안·Gateway·홈 회귀 포함. 전체 저장소 스위트는 아니다.
- 공식 Python MCP SDK 2.1.1: 두 개별 OAuth client의 같은 자료 응답 SHA 동일, 도구 탐색·검색·쓰기 거부 성공. `protocol-results.json` 참조.
- 최초 Chromium PC 1440/모바일 390: 20개 검사 통과 (검수 보강 후 22개, 최신 REVIEW_FIXES 참조), PC 6개 탭·모바일 axe 위반 0. `browser-results.json`, `screens/` 참조. 모든 검증 파일·회원·자료는 **격리 환경의 합성 테스트 자료**다. 실제 EP01 정본을 만든 것으로 간주하지 않는다.
- 실제 ChatGPT·Claude 구독 계정과 운영 외부 서비스 대조·Claude 독립 검수는 아직 실행하지 않았다. 이것이 완료 조건의 남은 부분이며 테스트 성공으로 대체하지 않는다.

## 재현 (격리 환경만)

JDK 21·Docker·Python 3.12·Node가 필요하다. 이미 다른 테스트 DB가 있으면 임의 삭제하지 말고 포트/컨테이너 충돌을 확인한다. 아래 이름과 계정은 합성 테스트 전용이다.

```bash
docker run -d --name f2r-studio-test-db -p 127.0.0.1:3318:3306 \
  -e MYSQL_ROOT_PASSWORD=isolated_test_root -e MYSQL_DATABASE=f2r_studio_test \
  -e MYSQL_USER=f2r_test -e MYSQL_PASSWORD=isolated_test_password mysql:8.4
F2R_STUDIO_MYSQL_TEST=true bash gradlew test --tests '*StudioServiceTest' --tests '*McpStudioMySqlTest' bootJar
pip install -r problem-generator/wiki-backend-src/requirements.txt pytest httpx
python -m pytest problem-generator/wiki-backend-src/tests/test_workspace_isolation.py problem-generator/wiki-backend-src/tests/test_mcp_readonly.py -q
bash scripts/ai-agent-studio/run-isolated.sh
```

Java MySQL 테스트는 `/tmp/f2r-browser-fixture.json`에 격리 세션을 남기며 저장소에 커밋하지 않는다. UI 검증은 `browser-qa.cjs`, SDK 검증은 `protocol-qa.py`를 별도 터미널에서 실행한다. `npm install --prefix /tmp/f2r-qa-deps playwright @axe-core/playwright`, `npx --prefix /tmp/f2r-qa-deps playwright install chromium`가 필요하다. `python scripts/ai-agent-studio/make-qa-fixtures.py`가 ffmpeg로 합성 업로드 fixture 7종을 `/tmp/f2r-qa-assets`에 생성한다. catalog 갱신은 `build-inventory.py /tmp/f2r-route-inventory.json`으로 수행한다. 테스트의 `/tmp` cookie/token을 공유·아티팩트 업로드하지 않는다.

## 승인된 원격 환경에 적용할 때의 설정 (아직 미적용)

기존 EC2·RDS·S3를 재사용한다. V1095 번호와 원래 서비스의 실제 배포 버전을 확인하고 격리 검증 후 설정 담당자가 적용한다. 새 유료 API·서버는 필요하지 않다.

```yaml
app:
  mcp:
    enabled: false # 운영 활성화 금지; 별도 승인된 격리 시험에서만 true
    public-base-url: https://feel2real.co.kr
    internal-base-url: http://127.0.0.1:8080
    redirect-uris:
      - https://claude.ai/api/mcp/auth_callback
      # ChatGPT의 실제 callback은 관리 화면에서 복사 후 정확히 추가. 합성 테스트 URI를 사용하지 않는다.
```

기본값 OFF를 변경하지 않았으므로 배포만으로 원격 MCP가 켜지지 않는다. 기존 HTTPS reverse proxy가 `/mcp`, `/mcp/oauth/*`, `/.well-known/oauth-*`를 동일 앱으로 전달해야 한다. Wiki Python의 read-only 검증 코드를 Spring과 함께 적용해야 한다. 구 Python에서 읽기 전용 서명을 무시하는 상태로 운용하지 않는다. 별도 AcademyCraft는 기존 Gateway secret·사용자/학원 연결이 필요하며 이 PR이 신규 계정이나 원격 DB 권한을 만들지 않는다.

두 AI의 계정별 연결은 사용자의 본인 로그인·scope 동의가 필요하다. 정확한 절차와 남은 시험은 연결 설명서에 있다. Claude 검수 결과를 받으면 같은 PR에서 수정·재검증한다. 현재 상태를 전체 완료 또는 운영 적용 완료라고 선언하지 않는다.
