# NewStart Cafe Collection — Rolling Handoff

## CURRENT BRANCH
`newstart/cafe-research-handoff-20260919`

## COLLECTION DATE
2026-09-19 (KST evening, logged-in session `woorinaesin`, stable — no CAPTCHA/blocks)

## CAFE
새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero, clubId 31312978) — 전체글 5,251

## CUMULATIVE
| 항목 | 값 |
|------|-----|
| TOTAL COLLECTED | 25 articles |
| NEW | 24 / UPDATED 1 (5994) / ERROR 1 (6006 삭제) |
| COMMENTS | 42 |
| IMAGE_REQUIRED | 1 (5996) |
| NEXT START | **5984** (역순) |

## BATCH LOG
### batch1 (commit 64643ea)
- 범위: 6000–6010 + 5994 UPDATE. NEW 10, UPDATED 1, ERROR 1(6006 삭제), 댓글 20.
- 5994 정정: 초보가이드→질문게시판(menuid=1), 댓글 0→2, 조회 14→23, 계좌번호 마스킹.
- BOARD: 자유 8, 질문 1, 부실차주신청 1, 공지 1.

### batch2 (this commit)
- 범위: 5985–5999 (5994 제외). NEW 14, ERROR 0, 댓글 22, author follow-up 5, result cases 2 (5988 ONGOING, 5986 AUTHOR_ACK).
- BOARD: 자유 11, 질문 2 (5997 담당자배정/신용정보, 5987 지급명령/이의신청), 임시안→확정안 1 (5986 배우자명의 승계).
- 특기: 5988 감면율 장문 사례(조회 143, 댓글 5) + 국민신문고/2차 행정처리 진행 중. 5996 IMAGE_REQUIRED(본문空+이미지1).
- 검증: dedupe PASS / board PASS(전건 menuid 대조) / comments PASS(표기수=추출수 전건 일치) / provenance PASS / PII PASS(민감정보 검출 없음).

## POLICY (batch3부터 속도 우선)
- 30건/batch → 검증 PASS 시 50건/batch. 10건마다 collection_state 저장.
- 단건 실패는 FAILED 기록 후 계속, batch 종료 후 재시도. 전체 중단 금지.
- 원문(raw) 보존, 요약/태그는 별도 layer. 질문→답변→후속 연결 유지.

## KNOWN ISSUES
- 댓글 고유 ID 미노출 → 로컬 id(c1..) 부여.
- 6001 UNRESOLVED(REVIEW): 소득 반영 기준 확정 답변 없음.
- 동료 회원 감면율 예측(6008 80~90%, 5988 70% 등)은 미검증 개인 주장으로 저장 — 공식 기준으로 사용 금지.

## NEXT ACTION
Batch 3: 5984부터 역순 30건 (5982/5973 질문게시판 확정 포함) → 검증 → commit+push.
수집 패턴: `ArticleRead.nhn?clubid=31312978&articleid={id}&referrerAllArticles=true` → 5초 대기 → `cafe_main` 프레임 `.title_text`/`.link_board`/`.nickname`/`.article_info`/`.article_viewer`/`.comment_list .CommentItem` 추출.
