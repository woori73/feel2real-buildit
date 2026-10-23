# F2R AI 운영 대시보드 (`/dashboard`)

ADMIN·TUTOR 공용 통합 대시보드를 캐릭터 비서(세븐·서수니)가 대화하며 업무를 안내하는 운영 화면으로 바꾼 구조와 자료 규칙.

## 화면

| 화면 | 진입 | 내용 |
|---|---|---|
| AI 메인 (기본) | `/dashboard`, `/dashboard#ai` | 캐릭터 대화 콘솔(캐릭터 영상 → 응답 → 입력창), 대화 기록, 사용 가능한 F2R 서비스 |
| 전체 대시보드 | 상단 `전체 대시보드`, `/dashboard#all` | 학원 업무 메뉴, 핵심 지표, 상세 위젯, AI 업무 지휘창(자료 분석·직접 입력·목록·감사로그) |

- 좌측 `F2R` 로고는 홈(`/`)으로 이동한다. 입력 중인 내용이 있으면 기존 `service_home_navigation.js` 확인창이 뜬다.
- 상단 `AI AGENT 제작소` 링크는 PR #892 구현(`data-studio-entry`, `studio_entry_link.js`, `/api/studio-access`)을 그대로 쓴다. 기본 hidden이며 ADMIN·modu만 보인다.
- `사용법 안내`는 언제 눌러도 1단계부터 다시 열린다. 첫 방문 자동 안내는 화면 위쪽에, 버튼으로 연 안내는 하단 고정 카드로 뜬다.
- 대화·초안·승인·저장은 기존 `unified_assistant.js` + `/api/dashboard/assistant/v1`을 그대로 쓴다.

## 캐릭터 대화 콘솔

| 상태 | 언제 | 영상 |
|---|---|---|
| 대기 | 처음, 응답 표시가 끝난 뒤 | `idle.mp4` |
| 듣는 중 | 입력창에 글을 쓰는 동안 | `idle.mp4` + 테두리 표시 |
| 생각 중 | 보낸 뒤 응답을 기다리는 동안, 승인 실행 중 | `think.mp4` + 스캔선 |
| 답변 표시 중 | 응답을 캐릭터 아래에 표시하는 동안 | `idle.mp4` (음성 미연결 시 말하는 영상을 쓰지 않음) |
| 업무 완료 | 승인한 초안이 저장 검증 완료로 바뀐 직후 | `idle.mp4` + 완료 테두리 |

- 영상은 두 개의 `<video>`를 번갈아 써서 다음 영상이 재생 준비된 뒤 겹쳐 바꾼다. 같은 녹화본에서 자른 구간만 쓰므로 얼굴이 갑자기 바뀌지 않는다.
- 응답 말풍선에는 관련 서비스 열기 버튼과 `… 초안 확인하고 승인` 버튼이 붙는다. 승인 버튼은 대화 기록의 실제 초안 카드로 이동하며, 실행은 카드의 기존 승인 버튼으로만 한다.
- 캐릭터 위 표시: 승인 대기·처리 완료·확인 필요 건수(대화 기록 카드 상태에서 계산), 연결된 서비스, 정본 승인 상태.
- 비서를 바꿔도 대화 기록·업무 학원·사업체·진행 건수는 그대로 둔다(화면 연출만 바뀜).
- 모바일은 입력창을 화면 아래에 고정하고 작은 캐릭터 상태 표시를 함께 둔다.

### 음성·립싱크 연결 지점

```js
window.F2RAgentVoice.register({
  speak(text, { agent }) { /* TTS 재생, 끝나면 resolve */ return Promise.resolve(); },
  lipSync: false  // true이고 speak.mp4가 있으면 응답 중 말하는 영상을 쓴다
});
```

연결 전에는 `텍스트 응답 · 음성 미연결`로 표시하고 응답 중에도 대기 영상만 쓴다.

## 캐릭터 자료: 비공개 폴더

공개 저장소에는 얼굴 자료를 두지 않는다. 운영 서버의 폴더를 `APP_AGENT_MEDIA_DIR`(= `app.agent-media.dir`)로 지정하면 로그인한 ADMIN·TUTOR에게만 `/dashboard/agent-media/**`로 제공한다.

```
{APP_AGENT_MEDIA_DIR}/
  manifest.json   {"seven":{"status":"CANDIDATE"},"suni":{"status":"REFERENCE_APPROVED"}}
  seven/  idle.mp4  think.mp4  poster.jpg  front.webp  half.webp   (speak.mp4는 음성 연동 시)
  suni/   idle.mp4  think.mp4  poster.jpg  front.webp  half.webp
```

| status | 화면 표시 | 의미 |
|---|---|---|
| `APPROVED` | 표시 없음 | 대표가 정본으로 승인한 자료 |
| `REFERENCE_APPROVED` | 승인 얼굴 기준 · 영상 확정 전 | 승인된 얼굴을 기준으로 만든 후보(서수니 현재 상태) |
| `CANDIDATE` | 외형 승인 전 후보 | 외형 자체가 승인 전(세븐 현재 상태). manifest 누락·오타도 이 값 |
| `MISSING` | 캐릭터 자료 미연결 | 파일 없음 → H-07/SUNI 표식 |

자료 묶음: `F2R_AGENT_DASHBOARD_V1.zip`의 `agent-media/`(Reactor Helios 녹화 재사용, 추가 생성 없음).
