# NewStart Naver Cafe — HOME → ACADEMY Handoff (range 5754→5705)

카페: 새출발기금 부실차주 라이브러리 — cafeKey `newhopero`, clubid `31312978`
Branch: `newstart/cafe-qoder-5754-5705` (전용 worktree `feel2real-wt-cafe-5754-5705`)
Scope: **articleId 5754 → 5705 (하행, 50건). HARD STOP = 5705.**

## 수집 방식 (재현용)
- 로그인된 상단 프레임에서 `https://cafe.naver.com/newhopero/{id}` 로 **article별 top-frame navigation**.
- `#cafe_main` iframe DOM에서 읽기: 본문 `.se-main-container`, 댓글 `li.CommentItem`
  (`.comment_nick_info` / `.comment_info_date` / `.text_comment`, 실제 commentId는 `comment_id` 패턴).
- boardName → boardId 매핑(자유게시판=13 등). 추출기는 `evaluate_script` `filePath` 로 단일행 JSON으로 저장.
- 함정: **iframe `location.href` 직접 조작 금지**(SPA 셸 라우터 desync → renderer hang).
  매 navigation 후 추출기는 자체-contained IIFE로 재주입(top reload 시 전역 소멸).

---

## HOME LAST STATUS

- **HOME LAST COMPLETED ARTICLE:** `5737`
- **NEXT ARTICLE TO RESUME:** `5736`

- **TARGET RANGE:** 5754 → 5705 (50건)
- **COMPLETED:** 17건 → 5754,5753,5752,5750,5749,5748,5747,5746,5745,5744,5743,5742,5741,5740,5739,5738,5737
- **PENDING:** 33건 → 5736, 5735, 5734, 5733, 5732, 5731, 5730, 5729, 5728, 5727, 5726, 5725, 5724, 5723, 5722, 5721, 5720, 5719, 5718, 5717, 5716, 5715, 5714, 5713, 5712, 5711, 5710, 5709, 5708, 5707, 5706, 5705, + 5751(브라우저 행 재시도)
- **NOT_FOUND_IN_LIST:** 0 (없음)
- **DELETED:** 0
- **COMMENTS COLLECTED:** 54

- **LAST SUCCESSFUL PAGE:** article 5737 (자유게시판, "감면수업 문의드립니다.", 0 comments) 정상 수집.
- **LOGIN STATE AT STOP:** 로그인 유효(nid로그인 리다이렉트 없음 / logout 링크 존재 확인). 중단 원인은 세션 만료가 아니라 **renderer hang**.

### BROWSER ISSUE
`5736` 수집 중 **Qoder 내장 브라우저 renderer가 hang**. evaluate_script가 반복 타임아웃(15s)하고,
이후 이전에 정상 동작하던 `5737`로 top-frame navigation 해도 30s 타임아웃. `list_pages`(CDP 타깃 목록만)는 응답.
→ 5751과 동일한 "wedged renderer" 계열. **복구 = 사용자가 Web reading 탭을 닫고 새로 열기(수동, 자동로그인 금지).**
5736 은 NOT_FOUND/DELETED 가 아니라 **browser-hang PENDING** 으로 보존. 임의 재시도는 반복 접근(rate-limit) 위험이라 중단.

---

## ACADEMY(학원 PC) 재개 지침
1. `git fetch origin`
2. 전용 브랜치 확인: `newstart/cafe-qoder-5754-5705`
3. 최신 remote SHA 확인: `git ls-remote origin refs/heads/newstart/cafe-qoder-5754-5705`
4. 전용 worktree 생성/복구: `git worktree add <path> newstart/cafe-qoder-5754-5705`
5. 상태 파일 읽기(본 파일 + 아래 3종):
   - `docs/newstart/research/qoder_5754_5705_collection_state.json`
   - `docs/newstart/research/qoder_5754_5705_index.json`
   - `docs/newstart/research/qoder_5754_5705_checkpoint.json`
6. Qoder Web reading 에서 **네이버 수동 로그인** (새 탭/재시작 후)
7. 로그인 상태 확인(nid로그인 리다이렉트 없음, 카페 본문 렌더 확인)
8. **NEXT ARTICLE TO RESUME(5736)부터 하행 수집.** COMPLETED 목록의 article은 **재수집 금지**.
   - 5736 은 새 브라우저에서 1회 재시도; 또 hang 되면 browser-hang PENDING 으로 두고 5735 로 진행.
   - 5751 도 여유 시 재시도(현재 browser-hang PENDING).
9. **5705 에서 HARD STOP** — 5704 이하 접근 금지.

## 안전 규칙(전 승계)
- 자동 로그인 금지 / 자격증명 요구 금지. 로그인 풀리면 STOP 후 사용자 재로그인 요청.
- CAPTCHA / rate-limit / 접근차단 발생 시 우회 금지 → 진행 저장 후 STOP.
- 브라우저 세션 장애 article 은 PENDING 유지(NOT_FOUND/DELETED 로 처리 금지).
- 절대 저장 금지: 네이버 로그인 정보 / password / cookie / session / access token / Client Secret / credential / browser profile.
- `git add .` 금지 → 프로젝트 파일만 경로 지정 stage. 다른 프로젝트(OAuth/EASY/CHART/Wiki/신구중) 변경 혼입 금지.

## 진행 후 체크포인트 주기
약 10 article마다: 데이터 검증 → secret scan → 경로 지정 stage → commit → origin push.
commit 메시지 예: `data(newstart): collect cafe articles 5736-5727 (+progress state)`.
