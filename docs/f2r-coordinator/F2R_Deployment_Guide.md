# F2R AI Coordinator V1 — 설치 및 배포 방법

> 상태: **구현 전 계획.** 실제 운영 배포는 **사용자의 별도 승인 후** 진행한다. 이 문서에 적힌 명령은 구현이 끝난 뒤의 절차다.

## 0. 배포 전 결정 사항 (사용자)

1. 실행 방식: **API 키**(권장)인지, 구독 로그인(정책 서면 확인 후)인지
2. 지정 PC 1대와 Windows 계정
3. 학습지 HTML 저장 위치: 위키 저장소인지 AI AGENT 제작소인지
4. 학생 명부 연결을 누가·언제 만들지(ADMIN 화면)
5. 월 비용 상한(예: API 월 $50, 하루 $5)

## 1. 서버 (기존 F2R, EC2)

1. 로컬에서 개발·검증한다. CLAUDE.md 규칙을 따른다: `./scripts/run-local.sh`, 단위·MySQL 테스트, `build-check.yml` 목록 추가.
2. PR → CI 통과 → **사용자 승인** → 병합한다.
3. 운영 배포: GitHub Actions `deploy-ec2.yml`을 수동 실행한다(`target=new`).
   - Flyway가 V1123~를 적용한다. 새 테이블만 만들고 기존 테이블은 바꾸지 않는다.
4. 배포 확인
   - `prod-readonly-status.yml`(`section=flyway-history`)로 적용 버전을 확인한다.
   - `/api/coord/agent/heartbeat`에 토큰 없이 요청하면 401이 나와야 한다.
5. 기능 스위치: `app.coordinator.enabled`(기본값 `false`). 운영에서 켜는 것도 별도 승인 후 `/etc/f2r/env`로 한다.
   - 데모 단계의 Claude 비활성 스위치(`anthropic.enabled`)와는 별개다. Coordinator는 서버에서 Claude API를 부르지 않고 PC CLI를 쓰기 때문이다.
6. 비밀값: 서버는 Anthropic/OpenAI 키를 갖지 않는다. 키는 PC에만 둔다(API 키 방식일 때).

## 2. PC Agent (Windows)

1. 준비
   - Node.js 20 LTS
   - Claude Code: PowerShell에서 `irm https://claude.ai/install.ps1 | iex`
   - Codex: `irm https://chatgpt.com/codex/install.ps1 | iex` 또는 `npm i -g @openai/codex`
   - 설치 명령은 공식 문서를 다시 확인한 뒤 실행한다.
2. 로그인(택1)
   - **API 키 방식(권장)**
     - Claude: 사용자 환경 변수 `ANTHROPIC_API_KEY`(Windows 자격 증명 관리자나 사용자 환경 변수)
     - Codex: `codex login --with-api-key`
   - **구독 방식**(서면 확인 후)
     - Claude: `claude auth login`. PC에 `ANTHROPIC_API_KEY`가 **없어야 한다.** `-p`는 키가 있으면 키를 먼저 쓰기 때문이다.
     - Codex: `codex login`(ChatGPT)
   - 확인: `claude auth status`, `codex login status`
3. Agent 설치
   ```powershell
   git clone (또는 배포 zip) → cd coord-agent
   npm ci
   .\install.ps1     # 작업 스케줄러 "로그온 시 실행" 등록, 실패 시 재시작
   node src\cli.js pair <웹에서 받은 일회용 코드>
   ```
4. 확인: 웹 「Coordinator 기기」 화면에서 PC가 "연결됨"이고, 실행 환경(Claude/Codex·버전·로그인 방식)이 보인다.
5. 해지·제거: 웹에서 해지 → `.\uninstall.ps1`(작업 스케줄러 삭제, `%LOCALAPPDATA%\F2R` 삭제)

## 3. Android 앱 (Galaxy Z Fold6)

1. 서명한 APK를 원장 기기에 설치한다. 「출처를 알 수 없는 앱」은 설치할 때만 허용하고, 설치 후 다시 끈다.
2. 앱 실행 → 아이디·비밀번호 로그인(ADMIN/TUTOR) → 「이 기기 등록」
3. 권한: 카메라·마이크(사용할 때 요청)
4. 해지: 웹 기기 화면에서 해지하거나, 앱 설정에서 「등록 해지」

## 4. 운영 점검

| 주기 | 할 일 |
|---|---|
| 매일 | 웹 기기 화면(연결 상태), `failed` 작업 확인 |
| 매주 | API 사용량·비용(Anthropic·OpenAI 콘솔), 작업 수·실패율 |
| 매월 | 기기 토큰 목록 점검(쓰지 않는 기기 해지), 사진 보관 기간 삭제 배치 결과 |

## 5. 되돌리기

- 기능 스위치 `app.coordinator.enabled=false`로 끄면 앱·PC 요청이 모두 503이 된다. 기존 기능에는 영향이 없다.
- 새 테이블은 기존 테이블과 FK로 묶지 않는다. 그래서 되돌릴 때 기존 데이터에 영향이 없다. 적용된 V 파일은 바꾸지 않는다(정책). 정리가 필요하면 새 V 파일로 한다.
