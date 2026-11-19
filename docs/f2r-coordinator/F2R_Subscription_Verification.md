# 기존 구독 활용 범위 검증 (MCP 우선)

- 검증일: 2026-11-19
- 표기
  - **실측**: 이 계정·운영 서버에서 직접 확인했다.
  - **문서**: 공식 문서 원문을 읽었다.
  - **요약**: 공식 페이지를 검색 결과 요약으로만 확인했다. 원문 재확인이 필요하다.
  - **미확인**: 확인하지 못했다.
- 이 문서는 법률 판단이 아니다. 판단이 애매한 항목은 각 회사에 서면으로 문의해야 한다.

## 1. 이미 동작하는 것 (실측)

| 항목 | 결과 | 근거 |
|---|---|---|
| F2R 운영 MCP 켜짐 | `https://feel2real.co.kr/mcp`, OAuth 메타데이터가 scope 9개를 공개한다(studio·academy-move·student-roster·easy-collect·cafe·parent-notice 등) | `/.well-known/oauth-authorization-server` 조회(2026-11-19) |
| Claude 구독 계정 → F2R MCP | 이 계정의 커넥터로 11/18~11/19 사이 약 100건이 호출됐다(제작소 조회·업로드 티켓·버전 등록). 거의 모두 허용(ALLOW) | MCP `activity_history`(운영 감사 로그) |
| ChatGPT → F2R MCP | 개발자 모드 커넥터로 제작소 쓰기 도구를 썼다. 큰 파일은 `openai/fileParams`로 서버가 직접 받는다. 모바일에서 가끔 실패한다는 기록이 있다 | `docs/studio-collaboration/CHATGPT_STUDIO_CONNECTION.md`(2026-11-11 기록) |
| 학부모 안내문 초안 도구 | `save_parent_notice_draft` 운영 중(scope `parent-notice:draft`) | 코드·운영 scope |
| PC의 Claude Code CLI → F2R MCP | **지금은 불가.** OAuth callback 허용 목록에 `http://localhost:*`가 없다. 보안 검토 후 추가해야 한다 | `McpOAuth.java`, 위 문서 §6 |

결론: **"구독 AI 앱 → F2R MCP → F2R 업무"는 새로 만들 필요가 없고 이미 운영 중이다.** 빠진 것은 Coordinator 업무용 도구(`f2r_*`)뿐이다.

## 2. 경로별 구독 사용 가능 범위

| 경로 | 무엇을 하나 | 구독으로 가능? | 추가 비용 | 정책 판단 | 근거 |
|---|---|---|---|---|---|
| **P1. Claude 앱(Android) + F2R 커넥터** | 글·사진·(음성)으로 명령 → Claude가 F2R MCP 도구 호출 | 가능. Free·Pro·Max·Ultra·Team·Enterprise, 모바일에서도 쓸 수 있다 | 없음(구독 한도 안) | **정상 사용** — Anthropic 공식 앱의 기본 기능이다 | 문서(connectors getting-started, add-unlisted) + 실측 |
| P1 세부: 도구 승인 | 도구마다 "항상 허용 / 승인 필요 / 차단" | 가능 | — | — | 문서 |
| P1 세부: 음성 모드에서 도구 | 연결된 도구가 음성 모드에서도 동작 | 공식 예시는 Gmail·캘린더 등이다. **우리 커스텀 커넥터는 미확인** | — | — | 문서(voice mode) |
| P1 세부: 사진 첨부 + 커넥터 | 같은 대화에서 사진을 보고 도구 호출 | **미확인**(실기기 시험 필요) | — | — | — |
| **P2. Claude 앱 → PC Claude Code (Remote Control)** | 휴대폰에서 PC의 Claude Code 세션 조작. 사진 첨부 가능. PC의 MCP 설정 그대로 사용 | 가능. Pro·Max·Ultra·Team·Enterprise. **API 키로는 못 씀** | 없음 | 정상 사용(본인 PC, 공식 프로그램 그대로) | 문서(remote-control, mobile) |
| P2 제약 | PC 켜짐 + `claude` 프로세스 실행 유지. 서버 모드(`claude remote-control`, `--capacity`)는 네트워크가 약 10분 끊기면 종료 | — | — | — | 문서 |
| **P3. Claude 앱 → PC 데스크톱 앱 "Dispatch"** | 휴대폰에서 PC(Claude Desktop, Windows 지원)로 작업을 보낸다 | 가능. Pro·Max·Ultra(Team·Enterprise 불가) | 없음 | 정상 사용 | 문서(cowork dispatch) |
| P3 제약 | PC가 켜져 있고 Desktop 앱이 열려 있어야 한다. 승인 요청에 10분 동안 답이 없으면 거절된다. **Code 작업은 폴더의 MCP·CLAUDE.md를 쓰지 않는다** | — | — | — | 문서 |
| **P4. ChatGPT 앱 + F2R 커넥터** | 글·사진·음성 → ChatGPT가 F2R MCP 호출 | **플랜에 따라 다르다.** 도움말 요약에 따르면 쓰기 포함 전체 MCP는 Business·Enterprise/Edu이고, Pro는 읽기·조회만이다. Plus는 불명확. 다만 이 사이트에서는 개발자 모드로 쓰기 도구를 쓴 기록이 있다 → **사용자 플랜으로 재확인 필요** | 없음(요약) | 정상 사용(공식 앱) | 요약 + 저장소 기록 |
| P4 세부: 음성·Android | 라이브 음성에서 연결된 앱을 쓸 수 있다(2026-09 공지). 승인은 화면을 눌러야 한다. 모바일 커스텀 MCP 쓰기는 **미확인** | — | — | — | 요약 |
| **P5. ChatGPT 앱 → PC Codex(Remote connections)** | Android ChatGPT에서 Windows의 Codex 데스크톱 앱 조작(QR 연결). 파일·자격 증명은 PC에 남는다 | 가능(Plus·Pro·Business). 사용량은 플랜 공용 | 없음 | 정상 사용 | 요약(developers.openai.com/codex/remote-connections) |
| P5 제약 | PC 켜짐·로그인 유지. **Android↔Windows 연결 실패 이슈가 다수 열려 있다**(GitHub openai/codex #22733, #39856) | — | — | — | 요약 |
| **P6. 서버가 PC에 작업을 내려보내 헤드리스 실행**(`claude -p`, `codex exec`, 구독 로그인) | 앞 설계(PC Agent) | 기술적으로는 된다 | 없음(한도 안) | **회색지대.** Anthropic: 구독 로그인은 "본인의 일반적인 사용"용이고, 제품·서비스에는 API 키를 쓰라고 한다. 사용자를 대신해 요청을 중계하는 것은 금지. OpenAI: 자동화에는 API 키를 권장한다 | 문서(legal-and-compliance) / 요약(codex auth) |
| **P7. P6 + API 키** | 같은 구조, 토큰 단위 과금 | — | 월 $25~75 추정 | 안전(공식 권장) | 문서 |

### 사용량 한도 (공통 주의)

- **Claude**: 채팅과 Claude Code가 **같은 한도를 공유**한다. 5시간 한도와 주간 한도가 있다. PC에 `ANTHROPIC_API_KEY`가 있으면 Claude Code는 구독 대신 키로 과금한다. (문서: support 11145838)
- **ChatGPT/Codex**: CLI·앱·클라우드가 플랜 사용량을 공유한다. 클라우드 작업이 더 많이 쓴다. (요약)

### Anthropic 정책 원문 (legal-and-compliance, 발췌)

- "OAuth authentication is intended exclusively for purchasers of … subscription plans and is designed to support ordinary use of Claude Code and other native Anthropic applications."
- "Developers building products or services … should use API key authentication"
- "… does not permit third-party developers … to route requests through Free, Pro, Max, or Ultra plan credentials on behalf of their users."
- "Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription"

**판단**
- 원장 본인이 **공식 앱**(Claude 앱·Desktop·Claude Code 그대로)을 **본인 업무에** 쓰는 P1·P2·P3은 정상 사용 범위다.
- F2R 서버가 구독 로그인으로 AI를 자동으로 돌리는 P6, 또는 다른 강사·학생을 대신하는 사용은 피한다. 그런 경우는 API 키(P7)로 한다.

## 3. 검증 결과에 따른 설계 변경 (MCP 우선)

1. **1차(구독만, 개발 최소)**: P1(+P4)
   - 휴대폰에서는 **공식 Claude 앱(또는 ChatGPT 앱)**으로 글·사진·음성 명령을 한다. F2R 커넥터(이미 운영 중)에 `f2r_*` 도구만 추가한다.
   - 승인은 F2R 모바일 웹 확인 화면(`/coord/approvals`)에서 한다. 도구가 돌려준 링크를 한 번 눌러 승인한다.
   - 카카오톡 공유는 그 화면의 공유 버튼(Web Share API → Android 공유 시트 → 카카오톡)으로 한다.
   - **이 단계에서는 직접 만든 Android 앱과 PC Agent가 필요 없다.**
2. **2차(PC 작업, 구독)**: P2(Claude Code Remote Control) 또는 P5(Codex Remote connections)
   - PC에서만 할 수 있는 일(큰 파일, 여러 파일 편집, 로컬 자료)은 휴대폰에서 PC 세션을 직접 조작한다.
   - PC의 Claude Code·Codex가 F2R MCP를 쓰려면 **CLI OAuth callback(localhost) 허용**을 보안 검토 후 추가해야 한다.
3. **3차(자동 작업 큐, 선택)**: P6/P7
   - 사람이 없는 시간에 서버가 PC에 일을 맡기는 기능이다. **API 키로만** 한다. 구독으로 하려면 서면 확인을 받은 뒤에 한다.
   - 기존 설계의 PC Agent와 직접 만든 Android 앱은 이 단계로 미룬다.

## 4. 기능별 1차 가능 여부

| 기능 | 1차(P1) 처리 방법 | 남는 확인 |
|---|---|---|
| 1. 음성·문자 호출 | Claude/ChatGPT 앱의 음성·문자. "클로드/지피티" 구분 = 어느 앱을 여느냐 | 음성 모드에서 커스텀 커넥터가 동작하는지(실기기) |
| 2. 학습지 촬영 → HTML | 앱에서 촬영·첨부 → 모델이 직접 읽고 HTML 작성 → `f2r_document_to_html`(초안) → 승인 후 게시. 원본 사진: ChatGPT는 `fileParams`로 서버가 직접 받는다. Claude 앱에서 원본 저장 방법은 미확인 | 사진 첨부 + 커넥터 동시 사용(실기기), 원본 사진 보관 방법 |
| 3. 출결 | `f2r_student_search` → `f2r_attendance_mark`(초안, 중복 경고) → F2R 확인 화면에서 승인 → 기록 | 없음(서버 개발만) |
| 4. 복습 문제 | 모델이 `f2r_question_search` 결과로 생성 → 같은 대화나 새 대화에서 검증 → `f2r_review_submit`(AI_PROPOSED) → 강사 승인 | 학생 명부 연결(`easy_student_links`) |
| 5. 학부모 카톡 | 기존 `save_parent_notice_draft` → 확인 화면 → 공유 버튼 → 카카오톡(사용자가 발송) | 없음 |
| PC 연결·대기·재처리 | 1차에는 없음(서버 + 공식 앱만). PC 작업은 2차 | — |

## 5. 사용자가 직접 확인할 것 (10분 실기기 시험)

1. 폴드6 Claude 앱 → 새 대화 → `+` → 커넥터에서 Feel2Real 켜기 → "학원 현황 보여줘"
   - 기대: `read_admin_dashboard`가 호출되고 요약이 나온다.
2. 같은 대화에서 학습지 사진 첨부 + "이 사진 문항 수를 세고 학원 현황도 같이 알려줘"
   - 기대: 사진을 읽은 결과와 도구 결과가 함께 나온다.
3. 음성 모드로 1번 질문
   - 기대: 음성 모드에서도 도구가 호출된다(안 되면 1차는 문자 + 받아쓰기로 운영).
4. ChatGPT 앱에서 같은 시험을 하고, **설정의 플랜 이름**을 확인한다(Plus·Pro·Business).
   - 쓰기 도구(예: 안내문 초안 저장)가 되는지 본다.
5. (2차 준비) PC에서 `claude --version`, `claude auth status`, `codex --version`, `codex login status` 실행 결과를 알려 주시면 된다.

결과를 알려 주시면 1차 범위를 확정한다.
