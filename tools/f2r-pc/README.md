# F2R 학원 PC 작업 설치 안내 (윈도우)

> 2026-11-19부터 **기본은 클라우드**입니다. 휴대폰 Claude 앱 → Code → 새 세션에서 「F2R 대기 작업 처리해줘(worker=CLOUD)」라고 하면 설치 없이 처리됩니다.
> 이 안내는 PC에만 있는 원본(HWP·PDF)이나 프린터가 필요해 「학원 PC에 맡겨줘」로 맡길 때만 필요합니다.

휴대폰에서 "학원 PC에 신구중2 기말 학습지 맡겨줘"라고 하면 F2R 대기열에 쌓이고, 이 PC의 Claude Code가 가져가 내신 Wiki에 저장합니다.
F2R 서버는 Claude를 직접 실행하지 않습니다 — 원장님 Claude 구독으로 이 PC의 Claude Code가 처리합니다(추가 비용 없음).

## 처음 한 번 설치

1. Claude Code 설치: https://code.claude.com 의 Windows 설치 안내대로 설치한 뒤, 명령 프롬프트에서 `claude`를 실행하고 **원장님 Claude 계정(구독)으로 로그인**합니다.
   - API 키(`ANTHROPIC_API_KEY`)를 설정하지 마세요. 키가 있으면 claude.ai 커넥터(F2R·BuildIt Wiki)가 불러와지지 않습니다.
2. claude.ai 설정 → 커넥터에 **AI Agent Studio(F2R)** 와 **BuildIt Wiki**가 연결돼 있어야 합니다(이미 연결됨). Claude Code에서 `/mcp`로 두 개가 보이는지 확인합니다.
3. 이 폴더(`tools/f2r-pc`)를 PC의 편한 위치(예: `C:\F2R-PC`)에 복사합니다.
4. `worker.txt`에 이 컴퓨터 이름을 적습니다: 학원 PC는 `ACADEMY_PC`, 집 노트북은 `HOME_LAPTOP`.
5. `start-f2r-pc.bat`을 실행합니다. 처음에 "Enable Remote Control?"이 나오면 `y`. 켜 둔 채로 둡니다.
   - PC를 켤 때마다 자동으로 켜려면 `start-f2r-pc.bat` 바로가기를 시작 프로그램 폴더(`Win+R` → `shell:startup`)에 넣습니다.
6. 절전 모드로 들어가지 않게 설정합니다(설정 → 시스템 → 전원 → 절전 "안 함").

## 이미 설치한 PC (2026-11-22 이후 한 번)

검수 기준이 바뀌었습니다(검수 3곳, 확인 항목 5개). 이 폴더의 `CLAUDE.md`를 PC에 복사해 둔 폴더(예: `C:\F2R-PC`)에 덮어쓰고 `start-f2r-pc.bat`을 다시 켭니다. `worker.txt`와 `outputs`는 그대로 둡니다.
새 `CLAUDE.md`는 기준을 적지 않고 서버가 내려 주는 지시문을 따르므로, 다음부터는 기준이 바뀌어도 다시 덮을 필요가 없습니다.

## 매일 쓰는 법

- 맡기기(어디서든): 휴대폰 Claude 앱 → "학원 PC에 압구정중1 2학기말 학습지 40문항 맡겨줘".
- 처리하기: 휴대폰 Claude 앱 → **Code** → 초록 점이 있는 학원 PC 세션 → "대기 작업 처리해줘".
  Claude Code가 작업을 가져가 그 학교 최근 기출 형식대로 만들고(첫 페이지 F2R 로고), **새 세션 3개로 검수**를 받은 뒤 원장님께 알립니다.
  원장님이 동의하면 내신 Wiki에 저장합니다. 디지털 모의고사 탑재는 원장님이 따로 지시할 때만 준비합니다.
- 학원 PC가 꺼져 있으면: 맡긴 뒤 30분이 지나면 집 노트북(같은 설치, `worker.txt`=`HOME_LAPTOP`)이나 클라우드가 대신 가져갈 수 있습니다.
- 진행 확인: 휴대폰 Claude에 "PC 작업 어떻게 됐어?" → `f2r_pc_task_list`.

## 주의

- 한 번에 한 작업씩 처리합니다. 만든 파일은 `outputs\<작업번호>`에도 남습니다.
- 학생 실명·연락처는 학습지에 넣지 않습니다.
