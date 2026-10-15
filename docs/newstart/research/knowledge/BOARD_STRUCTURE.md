# BOARD STRUCTURE — 새출발기금 부실차주 라이브러리 (2026-09-19 실측)

> 출처: 로그인 Chrome + Playwright MCP --extension bridge, `https://cafe.naver.com/newhopero` 좌측 게시판 메뉴 실측.
> 기존 HANDOFF의 게시판/menuid를 현재값이라고 가정하지 않고 실측으로 확정.
> 카페: 새출발기금 부실차주 라이브러리 / clubid=31312978 / 멤버 2,123 / 등급 가지3단계 / 전체글 5,250.

## 확정 게시판 목록

| boardName | menuId | URL | 성격 | NewStart 분류 | 수집 우선순위 |
|---|---|---|---|---|---|
| 전체글보기 | - (없음) | /ArticleList.nhn?search.clubid=31312978&search.boardtype=L | 전 게시판 통합 | INDEX | P2 (ID 탐색용) |
| 인기글 | - (`/f-e/cafes/31312978/popular`) | /f-e/cafes/31312978/popular | 조회/댓글 기반 선별 | SIGNAL | P2 |
| 공지사항 | 14 | /ArticleList.nhn?search.clubid=31312978&search.menuid=14&search.boardtype=L | 운영 공지 | OPERATOR_GUIDE | P1 |
| 초보가이드 | 16 | /ArticleList.nhn?search.clubid=31312978&search.menuid=16&search.boardtype=L | 입문/절차 안내 (PoC 당시 5994 위치로 오인, 실제는 질문게시판) | OPERATOR_GUIDE | P1 |
| 질문게시판 | 1 | /ArticleList.nhn?search.clubid=31312978&search.menuid=1&search.boardtype=L | 개별 질문 + 운영자/회원 답변 (article_5994 실제 위치) | Q&A_CORE | P0 (FIRST BATCH) |
| 자유게시판 | 13 | /ArticleList.nhn?search.clubid=31312978&search.menuid=13&search.boardtype=L | 잡담/가입인사 혼재 (새 게시글 아이콘 확인) | NOISE_MIXED | P3 |
| 재기를 꿈꾼다 | 15 | /ArticleList.nhn?search.clubid=31312978&search.menuid=15&search.boardtype=L | 수기/응원 | PERSONAL_EXPERIENCE | P2 |
| 추심 민원 샘플 | 11 | /ArticleList.nhn?search.clubid=31312978&search.menuid=11&search.boardtype=L | 민원서식 공유 | TEMPLATE | P1 |
| 부실우려 | 6 | /ArticleList.nhn?search.clubid=31312978&search.menuid=6&search.boardtype=L | 부실우려 단계 상담 | CASE_QA | P0 |
| 부동산 차량 등 담보 | 7 | /ArticleList.nhn?search.clubid=31312978&search.menuid=7&search.boardtype=L | 담보/재산가액 쟁점 | CASE_QA | P0 |
| 부실차주 자격 연체 추심 | 2 | /ArticleList.nhn?search.clubid=31312978&search.menuid=2&search.boardtype=L | 자격/연체/추심 핵심 | CASE_QA | P0 |
| 부실차주 신청 | 3 | /ArticleList.nhn?search.clubid=31312978&search.menuid=3&search.boardtype=L | 신청 절차/서류 (새 게시글 아이콘 확인) | PROCESS_QA | P0 |
| 부실차주 임시안부터 확정안 | 4 | /ArticleList.nhn?search.clubid=31312978&search.menuid=4&search.boardtype=L | 심의안~확정안 추적 | CASE_TRACK | P0 |
| 재취업 재창업 감면수업 + 정부 지원 | 5 | /ArticleList.nhn?search.clubid=31312978&search.menuid=5&search.boardtype=L | 추가감면 교육/지원사업 | OPERATOR_GUIDE | P1 |
| 하랑랑 스페셜 | 9 | /ArticleList.nhn?search.clubid=31312978&search.menuid=9&search.boardtype=L | 운영자 특집 (성격은 본문 확인 필요) | OPERATOR_GUIDE | P2 |
| 감면율과 확정금액 예측 게시판 | 22 | /ArticleList.nhn?search.clubid=31312978&search.menuid=22&search.boardtype=L | 감면율 예측 전용 | PREDICTION | P0 |

## 정정 사항 (provenance)

- article_5994: PoC/HANDOFF에 `초보가이드(menuid=16)`로 기록 → 2026-09-19 실제 breadcrumb/게시판 표시 기준 `질문게시판(menuid=1)`로 정정.
- 근거: 게시글 상단 `질문게시판` 링크 `search.menuid=1`, 하단 `'질문게시판' 게시판 글` 목록 포함.
- menuid=16은 referrer 잔재로 판단. URL 추측이 아니라 화면 표시를 기준으로 metadata를 저장한다.

## Pagination 구조 (실측 2026-09-19)

- 대문 전체글 영역: 공지 3건 고정 + 최신글 연속 렌더 (6008→5986 확인).
- 게시글 하단 `'질문게시판' 게시판 글` 영역: 페이지 버튼 `1, 2, 3` + `전체보기` 링크 (`search.menuid=1&search.page=1`).
- 목록 행: 제목 링크(`/ArticleRead.nhn?clubid=31312978&articleid={id}`), 작성자 버튼, 작성일(`2026.09.18.` 형식), 조회수, 댓글수(`[N]`), 사진/링크 아이콘.
- 게시판 목록 URL 패턴: `https://cafe.naver.com/f-e/cafes/31312978/articles/{id}?menuid={menuId}&referrerAllArticles=false`.
- 글 단위 안정 키는 `articleid`이며 menuid는 이동 가능하므로 breadcrumb로 재확인한다.
