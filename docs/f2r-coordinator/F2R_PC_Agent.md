# F2R AI Coordinator V1 — PC Agent 설계

> 상태: **설계안(미구현).** 기반은 기존 `local-collector/`(Node.js, 외부로 나가는 폴링만 쓰고 포트를 열지 않는 구조)이다.

## 1. 개요

- **형태**: Node.js 20 LTS 단일 프로세스. 저장소에 `coord-agent/` 폴더를 새로 만든다(Java 빌드와 분리).
- **설치 대상**: 원장이 지정한 Windows 10/11 PC 1대. V1에서는 PC 여러 대를 지원하지 않는다.
- **하는 일**
  - 서버 연결 유지: heartbeat를 30초마다 보낸다.
  - 작업 받기: `next-job`을 5초마다 묻는다. 대기 작업이 없으면 204.
  - 실행: ClaudeRunner, CodexRunner
  - 결과 업로드, 상태·오류 보고
- **하지 않는 일**
  - 포트를 열지 않는다.
  - DB에 접속하지 않는다.
  - 브라우저 쿠키·비밀번호를 읽지 않는다.
  - 셸 명령을 임의로 실행하지 않는다.
  - 저장소(git) 폴더에서 작업하지 않는다.

## 2. 자동 실행과 연결 유지

- **Windows 작업 스케줄러**에 "로그온 시 실행" 작업을 등록한다. 설치 스크립트 `install.ps1`이 한다.
  - Windows 서비스(SYSTEM 계정)로 돌리지 않는다. Claude Code·Codex 자격 증명은 **사용자 프로필에** 있기 때문이다(`%USERPROFILE%\.claude`, `~\.codex\auth.json`).
- **감시 루프**
  - 프로세스가 죽으면 작업 스케줄러가 다시 시작한다(실패 시 재시작 설정).
  - 서버에 연결하지 못하면 대기 시간을 늘려 가며 다시 시도한다: 5초 → 최대 5분.
- **PC가 꺼졌을 때**
  - 작업은 서버에 `queued`로 남는다.
  - PC가 켜지면 Agent가 이어서 처리한다.
  - 하던 작업의 임대 시간이 끝나면 서버가 `retry_pending` → `queued`로 되돌린다.

## 3. 등록(페어링)

1. 원장이 웹 「Coordinator 기기」 화면에서 「PC 등록」을 누른다. 서버는 등록 코드(일회용, 10분)를 QR과 문자열로 보여 준다.
2. PC에서 `coord-agent pair <코드>`를 실행한다. 서버는 기기 토큰을 한 번만 돌려준다.
   - Agent는 이 토큰을 **Windows DPAPI**(현재 사용자 범위)로 암호화해 `%LOCALAPPDATA%\F2R\agent.json`에 저장한다.
   - 원문 토큰은 서버에도, 로그에도 남기지 않는다.
3. 웹에서 언제든 해지할 수 있다. 해지하면 다음 요청부터 401이 나고 Agent는 멈춘다.

## 4. 실행 환경 확인(capabilities)

- **확인 시점**: 시작할 때와 6시간마다.
- **확인 명령**: `claude --version`, `claude auth status`(구독인지 API 키인지), `codex --version`, `codex login status`
- **보고**: 결과를 heartbeat에 담아 보낸다. 비밀값은 담지 않고 **방식만** 보낸다.
- **경고 조건**
  - `ANTHROPIC_API_KEY`가 환경에 있는데 구독 모드를 고르면 경고한다. 그 상태에서는 `-p`가 키로 과금되기 때문이다.
  - 실행 가능한 Runner가 없으면 서버에 `NO_RUNNER`로 보고한다. 앱은 "PC에서 Claude/Codex를 쓸 수 없음"으로 표시한다.

## 5. 작업 처리 순서

1. `GET next-job` → 작업 정보를 받는다(가명화된 입력, 파일 다운로드 URL, 작업 전용 MCP 토큰 15분).
2. 작업 폴더를 새로 만든다: `%LOCALAPPDATA%\F2R\jobs\<jobId>\`
   - 들어가는 것: `input.json`, 사진, `prompt.md`(서버가 만든 지시문)
   - `.mcp.json`·hooks·`CLAUDE.md`는 두지 않는다. MCP는 Agent가 실행 인자로만 넘긴다.
3. Runner를 실행한다. 제한은 다음과 같다.
   - 시간: 작업당 10분
   - 횟수: `--max-turns 20`
   - 비용: Claude는 `--max-budget-usd`(API 키 모드)
   - Codex는 `--sandbox workspace-write`
4. 결과를 모은다: `result.html`/`result.json`, 표준 출력 JSON(`session_id`, `total_cost_usd`)
   - 업로드 → `POST result` → 서버가 `review_required`로 바꾼다.
5. 실패하면 `POST failed`(오류 종류: `TIMEOUT`/`RUNNER_ERROR`/`AUTH`/`LIMIT`)를 보낸다. 재시도할지는 서버가 정한다.
6. 작업 폴더는 24시간 뒤 지운다. 사진은 업로드한 뒤 바로 지운다.

## 6. Runner 어댑터

```ts
interface Runner {
  name: 'CLAUDE' | 'CODEX';
  available(): Promise<{ ok: boolean; auth: 'subscription' | 'api_key' | 'none'; version: string }>;
  run(job: JobSpec, dir: string, signal: AbortSignal): Promise<RunResult>;
}
```

- **ClaudeRunner**
  - 실행: `spawn('claude', ['-p', prompt, '--output-format', 'json', '--allowedTools', 'Read,Write', '--permission-mode', 'dontAsk', '--max-turns', '20', '--mcp-config', mcpFile, '--strict-mcp-config'], { cwd: dir })`
  - 사진은 지시문에 절대 경로로 적는다.
  - 셸 도구(Bash/PowerShell)는 허용하지 않는다. Windows 기본 환경에는 샌드박스가 없기 때문이다.
- **CodexRunner**
  - 실행: `spawn('codex', ['exec', prompt, '-C', dir, '-i', photo, '--sandbox', 'workspace-write', '--json', '-o', 'last.txt', '--skip-git-repo-check', '--ephemeral'])`
  - MCP는 작업용 `CODEX_HOME`의 `config.toml`(`bearer_token_env_var`)로 넘긴다.
- **플래그 확인**: 플래그 이름과 동작은 2026-11-19 공식 문서와 소스 기준이다. 설치된 버전에서 `--help`로 다시 확인한 뒤 확정한다(버전마다 다를 수 있다).

## 7. 로그·오류

- **로그 위치**: `%LOCALAPPDATA%\F2R\logs\agent-YYYYMMDD.log`, 7일 보관
- **로그에 남기지 않는 것**: 토큰, 프롬프트 원문의 학생 정보, 사진 내용
- **서버로 보내는 것**: 상태 변경과 오류 요약(500자)만 `coord_job_events`로 보낸다.

## 8. 테스트

- **단위 테스트**(`node --test`, given/when/then)
  - 임대 시간 연장
  - 재시도 대기 시간 계산
  - 작업 폴더 격리(`..` 경로 차단)
  - Runner 인자 조립(셸 도구가 들어가지 않는지)
  - DPAPI 래퍼(모의 객체)
- **통합 테스트**: 모의 서버(`local-collector/mock-site` 방식) + 가짜 Runner(정해진 JSON을 출력)
