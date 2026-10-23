# F2R AI 운영 대시보드 (`/dashboard`)

ADMIN·TUTOR 공용 통합 대시보드를 캐릭터 비서(세븐·서수니) 중심의 운영 화면으로 바꾼 구조와 자료 규칙.

## 화면

| 화면 | 진입 | 내용 |
|---|---|---|
| AI 메인 (기본) | `/dashboard`, `/dashboard#ai` | 캐릭터 영상, 비서 선택, 시스템 상태창, AI 비서 대화, 사용 가능한 F2R 서비스 |
| 전체 대시보드 | 상단 `전체 대시보드`, `/dashboard#all` | 학원 업무 메뉴, 핵심 지표, 상세 위젯, AI 업무 지휘창(자료 분석·직접 입력·목록·감사로그) |

- 좌측 `F2R` 로고는 홈(`/`)으로 이동한다. 입력 중인 내용이 있으면 기존 `service_home_navigation.js` 확인창이 뜬다.
- `사용법 안내`는 언제 눌러도 1단계부터 다시 열린다. 진행 기록을 읽지 못해도 화면 안내는 열린다.
- 대화·초안·승인은 기존 `unified_assistant.js` + `/api/dashboard/assistant/v1`을 그대로 쓴다. 새 API·DB 변경 없음.
- 대화 내용의 키워드로 관련 서비스를 활성화한다(`f2r_agent_dashboard.js`의 `SERVICES.keywords`). 서버에 쓰지 않는다.
- 비서 선택(`f2r.dashboard.agent`)과 마지막 화면(`f2r.dashboard.view`)은 브라우저 localStorage에만 저장한다.

## 캐릭터 자료 슬롯 (저장소 미포함)

`<meta name="f2r-agent-media" content="/agent-media/">` 아래 경로를 읽는다. 파일이 없으면 상태창 표식(H-07 / SUNI)으로 대체된다.

```
agent-media/
  seven/  idle.mp4  talk.mp4  poster.jpg  front.webp  half.webp
  suni/   idle.mp4  talk.mp4  poster.jpg  front.webp  half.webp
```

- `idle.mp4`: 대기, 완료 상태. `talk.mp4`: 분석·응답 상태. 640px 폭, 무음, 앞뒤 이어 붙인 반복 영상.
- 캐릭터 얼굴 자료는 공개 저장소이므로 **대표 승인 전에는 커밋하지 않는다**(`.gitignore`에 `static/agent-media/` 등록).
  승인 후 이 폴더에 넣어 커밋하거나, 별도 저장소 경로를 meta에 지정한다.
- 서수니: 승인 원본(SHA-256 461926ff…) 기준 후보에서 만든 Reactor Helios 영상. 세븐: 외형 승인 전 후보 기반.
