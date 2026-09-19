# NewStart Cafe Collection Handoff — Batch 1 (2026-09-19)

## CURRENT BRANCH
`newstart/cafe-research-handoff-20260919`

## LAST COMMIT (before this batch)
`4b0a23cc03843f05fdd86119b17192a5100d924a` — docs(newstart): save cafe research handoff

## COLLECTION DATE
2026-09-19 (KST evening, logged-in session `woorinaesin`)

## CAFE / BOARD
- 카페: 새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero, clubId 31312978)
- 전체글 5,251. Batch 1은 최신글 역순 수집 (6000→6010) + 5994 정정.
- BOARD 분포 (batch 1): 자유게시판 8, 질문게시판 1(5994 정정), 부실차주 신청 1(6010), 공지사항 1(6009)

## BATCH 1 RESULT
| 항목 | 값 |
|------|-----|
| TOTAL DISCOVERED (공개 목록) | ~23 recent IDs (5987–6010) + 전체공지 3 |
| TOTAL COLLECTED (DB) | 11 articles |
| NEW | 10 (6000,6001,6002,6003,6004,6005,6007,6008,6009,6010) |
| UPDATED | 1 (5994: 게시판 초보가이드→질문게시판, 댓글 0→2, 조회 14→23, 계좌번호 마스킹) |
| UNCHANGED | 0 |
| ERROR | 1 (6006: 삭제됨 — alert 확인, retry 없음) |
| COMMENTS COLLECTED | 20 |
| FOLLOW-UP 포함 | 3 (5994 AUTHOR_ACK, 6010 ONGOING, 6002 RESULT_PARTIAL) |
| IMAGE_REQUIRED | 0 (전건 imgCount=0, 첨부 0) |

## 수집 파일
```
docs/newstart/research/
├── article_5994.json / .md   # UPDATED (정정 + 마스킹 + 댓글)
├── article_6000.json         # NEW (출석체크, 댓글 0)
├── article_6001.json         # NEW (소득/감면율 Q&A 4댓글, UNRESOLVED→REVIEW)
├── article_6002.json         # NEW (장기연체코드 등록 후기, RESULT_PARTIAL)
├── article_6003.json         # NEW (가입인사→6004 후속질문 연결)
├── article_6004.json         # NEW (회생→부실차주 Q&A, 운영자 답변)
├── article_6005.json         # NEW (가입인사, 운영자 환영 댓글)
├── article_6007.json         # NEW (가입인사, 댓글 0)
├── article_6008.json         # NEW (감면율 예측 질문, 동료 예측 댓글 — 공식기준 아님 명기)
├── article_6009.json         # NEW (Beta Open 공지, OPERATOR_GUIDE)
├── article_6010.json         # NEW (근저당 Q&A 5댓글 스레드, ONGOING)
├── index.json                # article ID index (NEW/UPDATED/ERROR)
├── collection_state.json     # 체크포인트 (NEXT START=5999)
└── NEWSTART_CAFE_COLLECTION_HANDOFF_2026-09-19.md  # 이 파일
```

## LAST ARTICLE ID / NEXT START POINT
- LAST ARTICLE: 6010 (최신 수집)
- NEXT START: **5999** (역순으로 5999→5998→5997→… 수집)
- 질문게시판 확정 ID (5994 페이지 하단 동일게시판 목록에서 확인): 5997, 5987, 5982, 5973 → batch 2 우선 포함

## KNOWN ISSUES
1. 댓글 고유 ID 미노출: 새 UI DOM에 comment ID 없음 → 로컬 id(c1..) 부여, `parseWarnings` 기록.
2. 6006 삭제 확인 (alert "삭제되었거나 존재하지 않는 게시글입니다") — index에 ERROR, 재시도 금지.
3. PoC 잔재 정정: 기존 5994 JSON의 실계좌번호는 이번 UPDATE에서 마스킹됨. Git 히스토리에는 구 커밋에 남아있음 (추후 필요시 히스토리 정리 검토).
4. 6001 미해결 (REVIEW): 소득 반영 기준 확정 답변 없음.
5. `isMine` 클래스: 로그인 사용자와 무관하게 표시되는 경우 있어 `isOperator`는 매니저 신원(지피지기자산=카페매니저)으로만 판정.

## LOGIN STATUS
LOGGED_IN (`woorinaesin` — `g_sUserId` + 본문 접근으로 검증). 세션 만료 시 STOP 후 사용자에게 재로그인 요청.

## VALIDATION (batch 1)
- article/body: PASS (11/11 `.title_text`/`.article_viewer` 추출, 빈 본문 0)
- comments: PASS (목록 표기 수와 추출 수 일치: 5994:2, 6010:5, 6009:2, 6008:1, 6007:0, 6005:2, 6004:1, 6003:1, 6002:2, 6001:4, 6000:0)
- board: PASS (전건 `.link_board` + menuid href 대조)
- URL: PASS (ArticleRead 정식 URL 저장)
- dedupe: PASS (기존 ID={5994} → UPDATE 1건만, 중복 저장 없음)
- privacy masking: PASS (계좌번호 1건 마스킹, 그 외 전화/이메일/주민번호/주소 검출 없음)

## NEXT ACTION
Batch 2: 5999부터 역순 ~15건 (5997/5987/5982/5973 질문게시판 포함) 수집 → 검증 → commit+push → handoff 갱신.
수집 패턴: `https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid={id}&referrerAllArticles=true` → 5초 대기 → `cafe_main` 프레임에서 `.title_text`/`.link_board`/`.nickname`/`.article_info`/`.article_viewer`/`.comment_list .CommentItem` 추출.
