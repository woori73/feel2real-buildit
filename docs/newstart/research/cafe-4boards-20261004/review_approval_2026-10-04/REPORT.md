# 카페 4boards 패키지 REVIEW 339건 → ACTIVE 승인 결과 (2026-10-04)

## 방법
- 대상: knowledgeId 3827~4501 패키지 675행 중 REVIEW 상태 339행 (전수).
- 339행의 실제 본문/댓글 텍스트를 운영 DB에서 직접 내려받아(`tmp` 경유, 파일: decisions_all_339.json) 건별로 읽고 판정했다. MISSING_SOURCE / UNKNOWN_RISK / CONFLICT_RISK / 댓글 부분수집 플래그 자체는 보류 사유로 쓰지 않았다.
- 승인 채널: `POST /api/admin/newstart/wiki/review/{id}/approve` (ADMIN 세션, reason + confirmed:true). 직접 SQL·UPDATE·상태 강제변경 없음. 신규 INSERT 없음. 기존 ACTIVE 336행은 읽기 전용으로만 참조하고 수정/재등록하지 않았다(재실측으로 원형 확인).

## 결과 (운영 DB read-only 재실측)

| 항목 | 값 |
|---|---|
| 검사 대상 REVIEW | 339 |
| 신규 ACTIVE 전환 | 152 (전부 HTTP 200, 오류 0) |
| REVIEW 유지 | 187 |
| 패키지 최종 | ACTIVE 488 / REVIEW 187 / DRAFT 0 (675) |
| 운영 DB 전체 | ACTIVE 4289 / REVIEW 189 / DRAFT 0 (4501) |

DB 전체 REVIEW 189 = 패키지 유지 187 + 패키지 밖 기존 행 2(본 작업과 무관한 선행 보류 행).

## REVIEW 유지 187건 사유 분류

- 인사·축하·감사·응원 등 의사표현만 있고 사례/질문/답변 근거가 되는 구체 정보 없음 — 137건
- 같은 게시글 본문이 unitType만 달라서 중복 게시 — 대표 행만 ACTIVE 승인 — 21건
- 본문이 이미지(첨부 화면)로만 구성된 운영자 정보글 — 텍스트만으로 핵심 내용 확인 불가 — 13건
- 질문-답변 연결 파손 / 참조 대상이 행에 없음 — 10건
- 광고·전자책/단톡방 참여 유도성 내용 — 3건
- 본문이 외부 링크 1개뿐 — 설명 텍스트 없음 — 2건
- 이미지 1장 게시물에 달린 댓글 — 원문 이미지 없이는 단독 의미 불확립 — 1건

행별 knowledgeId·카페 글 ID·unitType·본문 발췌·사유는 `review_hold_187.json`에 수록했다.

## 하랑랑 스페셜 운영자 15 article / 16 unit 재확인
- 15건(OPERATOR_GUIDE)은 모두 "본문 텍스트 없음 — 이미지 N장으로만 구성" 또는 외부 링크 1개-only 원문이어서, 텍스트만으로는 어떤 절차를 확인하는 글인지 특정할 수 없다 → 전부 REVIEW 유지.
- 나머지 1개 unit(m9#330 회원 댓글 "3회이하면 고소를 일단 하는거군요")은 이미지 게시물에 붙은 반응이라 단독으로는 규정 내용을 담지 않는다 → REVIEW 유지.
- 텍스트만으로 독립 정보가 성립하는 unit은 해당 패키지 내에 없었다.

## 지피지기 에이전트 retrieval 테스트 (10문항)
- 실측 경로: `GET /api/muse/newstart/knowledge/search?q=…&limit=8` (ACTIVE 전용 retrieval, 지피지기 에이전트가 실제로 쓰는 서비스).
- 10문항 전부 결과를 반환했고, 상위 80건 중 36건이 이번 승인 newly ACTIVE 행이었다. 상세는 `retrieval_test.json`.
- 출처 구분은 그대로 보존: 카페 경험자료는 title前置 `[카페 4boards m{menu}#{aid}·커뮤니티 의견]`, authority는 적재 당시 값("… 카페 4boards REVIEW 적재"), tags의 `ck=CAFE4B:…`, `officialCheck=NOT_CHECKED`, `historicalCANDIDATE` 그대로. 공식 규정 행과 혼동되지 않도록 provenance를 손대지 않았다.
- 미관측: "도덕적해이 심사 기간" 문항은 상위 8건이 모두 선행 ACTIVE 행이라 신규 행이 노출되지 않았다(키워드 랭킹 특성). 승인 실패가 아니다.
