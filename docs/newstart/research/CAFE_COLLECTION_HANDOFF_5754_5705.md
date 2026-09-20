# NewStart Naver Cafe — HOME → ACADEMY Handoff (range 5754→5705)

카페: 새출발기금 부실차주 라이브러리 — cafeKey `newhopero`, clubid `31312978`
Branch: `newstart/cafe-qoder-5754-5705` (전용 worktree `feel2real-wt-cafe-5754-5705`)
Scope: **articleId 5754 → 5705 (하행, 50건). HARD STOP = 5705.**

## 수집 방식 (재현용)
- 로그인된 상단 프레임에서 `https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid={id}&referrerAllArticles=true` 로 **article별 top-frame navigation**.
- `#cafe_main` iframe DOM에서 읽기: 본문 `.se-main-container`, 댓글 `li.CommentItem`
  (`.comment_nick_info` / `.comment_info_date` / `.text_comment`, 실제 commentId는 `comment_id` 패턴).
- boardName → boardId 매핑(자유게시판=13 등). 추출기는 `evaluate_script` `filePath` 로 단일행 JSON으로 저장.
- 함정: **iframe `location.href` 직접 조작 금지**(SPA 셸 라우터 desync → renderer hang).
  매 navigation 후 추출기는 자체-contained IIFE로 재주입(top reload 시 전역 소멸).

---

## OPENCODE SESSION STATUS (2026-09-20)

- **LAST COMPLETED ARTICLE:** `5725`
- **NEXT ARTICLE TO RESUME:** `5724`

- **TARGET RANGE:** 5754 → 5705 (50건)
- **COMPLETED:** 27건 → 5754,5753,5752,5750,5749,5748,5747,5746,5745,5744,5743,5742,5741,5740,5739,5738,5737,5735,5734,5733,5732,5731,5730,5729,5728,5727,5725
- **PENDING:** 22건 → 5726(삭제 다이얼로그, 목록증거 필요),5724,5723,5722,5721,5720,5719,5718,5717,5716,5715,5714,5713,5712,5711,5710,5709,5708,5707,5706,5705, + 5751(브라우저 행 재시도)
- **NOT_FOUND_IN_LIST:** 1건 → **5736** (article 5737 iframe '전체글' 리스트에서 5739,5738,5737,5735,5734 순서로 확인 — 5736 부재)
- **DELETED:** 0
- **COMMENTS COLLECTED:** 76
- **5726 NOTE (2026-09-20):** 직접 접근 시 "삭제되었거나 존재하지 않는 게시글" alert → 정책상 직접-probing 불충분, PENDING 유지. 5725 페이지에서 목록증거 추출 시도했으나 리스트 링크 미렌더로 미확정. 이후 article 페이지의 '전체글' 리스트에서 5726 인접번호(5727·5725 사이) 부재 확인 시 NOT_FOUND_IN_LIST 확정.

- **LAST SUCCESSFUL PAGE:** article 5737 (재취업 재창업 감면수업 + 정부 지원, "감면수업 문의드립니다.", 0 comments) 정상 수집.
- **LOGIN STATE:** 로그인 유효 확인됨 (nid로그인 리다이렉트 없음, 카페 본문 렌더 확인).

### BROWSER ISSUE (Qoder → OpenCode)
- Qoder 세션: `5736` 수집 중 **renderer hang** → browser-hang PENDING으로 기록됨.
- OpenCode 세션: Playwright MCP 서버 불안정 — 도구 미등록/타임아웃으로 **브라우저 자동화 불가**.
- **5736 재시도 결과:** ArticleRead.nhn 직접 접근 시 "삭제되었거나 존재하지 않는 게시글" 다이얼로그 발생. 그러나 **정책상 직접 URL probing으로 NOT_FOUND 판단 금지**.
- **목록 기반 확인:** Article 5737의 iframe 내 '전체글' 리스트에서 **5736이 누락**됨(5737 → 5735로 점프). 이를 근거로 **NOT_FOUND_IN_LIST**로 확정 기록.
- **5735 확인:** 동일 리스트에서 5735 존재 확인 ("도덕적해이기간에", 서울 설시경, 댓글 2).
- **5751:** 여전히 browser-hang PENDING (기존 Qoder 세션에서 동일 증상).

---

## RESUMPTION GUIDE (다음 세션: Qoder 또는 수동)
1. `git fetch origin`
2. 전용 브랜치 확인: `newstart/cafe-qoder-5754-5705`
3. 최신 remote SHA 확인: `git ls-remote origin refs/heads/newstart/cafe-qoder-5754-5705`
4. 전용 worktree 생성/복구: `git worktree add <path> newstart/cafe-qoder-5754-5705`
5. 상태 파일 읽기(본 파일 + 아래 3종):
   - `docs/newstart/research/qoder_5754_5705_collection_state.json`
   - `docs/newstart/research/qoder_5754_5705_index.json`
   - `docs/newstart/research/qoder_5754_5705_checkpoint.json`
6. **네이버 수동 로그인** (Chrome 새 탭 → https://cafe.naver.com/newhopero)
7. 로그인 상태 확인(nid로그인 리다이렉트 없음, 카페 본문 렌더 확인)
8. **NEXT ARTICLE TO RESUME(5735)부터 하행 수집.** COMPLETED/NOT_FOUND 목록의 article은 **재수집 금지**.
   - 5735 → 5734 → ... → 5705 순서로 진행.
   - 5751도 여유 시 재시도(현재 browser-hang PENDING).
9. **5705 에서 HARD STOP** — 5704 이하 접근 금지.
10. **작동하는 URL 패턴:** `https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid={id}&referrerAllArticles=true` → 4초 대기 → `#cafe_main` iframe 내부 DOM 추출.

## 안전 규칙(전 승계)
- 자동 로그인 금지 / 자격증명 요구 금지. 로그인 풀리면 STOP 후 사용자 재로그인 요청.
- CAPTCHA / rate-limit / 접근차단 발생 시 우회 금지 → 진행 저장 후 STOP.
- 브라우저 세션 장애 article 은 PENDING 유지(NOT_FOUND/DELETED 로 처리 금지).
- 절대 저장 금지: 네이버 로그인 정보 / password / cookie / session / access token / Client Secret / credential / browser profile.
- `git add .` 금지 → 프로젝트 파일만 경로 지정 stage. 다른 프로젝트(OAuth/EASY/CHART/Wiki/신구중) 변경 혼입 금지.

## 진행 후 체크포인트 주기
약 10 article마다: 데이터 검증 → secret scan → 경로 지정 stage → commit → origin push.
commit 메시지 예: `data(newstart): collect cafe articles 5735-5726 (+progress state)`.

---

## QODER RESUME POINT
**git fetch origin 후 해당 branch 최신 commit을 확인하고, checkpoint/index/collection_state를 읽은 뒤 NEXT ARTICLE(5735)부터 재개한다. 이미 저장된 article(17건)과 NOT_FOUND_IN_LIST(5736)는 재수집하지 않는다.**