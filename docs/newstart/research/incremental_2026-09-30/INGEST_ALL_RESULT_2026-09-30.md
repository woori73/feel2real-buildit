# 인계 원문 전량 REVIEW 적재 결과 — 2026-09-30

## 숫자 (400 = 기존 125 + 신규 275)
| 구분 | 인계 원문 | 기존 운영 DB | 신규 적재 | 실패 | 미처리 |
|---|---|---|---|---|---|
| S-A 이 Collector 확보분 | 16 | 10 | **6** | 0 | 0 |
| S-B 부실우려 게시판(menuId 6) | 248 | 8 | **240** | 0 | 0 |
| S-C Qoder 인계 article_json | 136 | 107 (cases) | **29** | 0 | 0 |
| **합계** | **400** | **125** | **275** | **0** | **0** |

- 신규 적재 성공 **275/275**, 운영 knowledge ID **175~449** 연속, 전부 **REVIEW**(ACTIVE 0, DRAFT 0). 태그 `CAFE_RAW_ALL`, dedup 키 275건 유일.
- 경로: 정상 ADMIN 세션 → `POST /api/admin/newstart/wiki`(DRAFT) → `PUT /{id}/status?status=REVIEW`. 직접 SQL/RDS 0. 기존 174행(ID 1~174) UPDATE/DELETE 0, 승인/ACTIVE 0.
- 운영 knowledge 174 → **449** (ACTIVE 136 불변, REVIEW 38 → 313).
- 이미지 전용 3건(540·3768 EMPTY_BODY, 4078 IMAGE_ONLY)도 제목·출처·댓글·이미지 URL 보존 레코드로 적재.
- **4451(ID 447)**: 원문 전체 보존 + `SAFETY_FLAG=asset-evasion`, `approval=FORBIDDEN` 태그와 본문 헤더 경고 유지. 승인 금지.
- **798001 원문 미러**: `approval=HOLD_RAW_MIRROR` 태그로 적재(K1~K7 이 별도 ACTIVE).
- 닉네임은 역할(운영자/작성자/회원)로 치환, 개인정보 패턴(전화·계좌·주민번호·이메일) 마스킹 대상 0건.

## LOCAL-ONLY = 0
- 적재 payload 275건 원본은 Git 에도 보존: `INGEST_PAYLOADS_ALL_275_2026-09-30.json` (커밋 c49dfe96). 운영 적재본과 동일 파일.
- 적재 통로로 쓴 로컬 서버·임시 복사본 삭제, 브라우저 임시 저장값 삭제.

## 오늘 하지 않은 것 (집에서 이어서)
- 분류·승인·ACTIVE 전환·RAG 품질평가: 적재된 REVIEW 275건 대상으로 재분류
- cases 16 / relations 8 운영 반영, K1 검색 보강, 6050 잔여 정보, 카페 게시(v2)
- **TODO: `내 채무상황` UI 수정 — 집에서 이어서**
- 참고: 적재된 원문 표기 개선(날짜 표기 `2026-07-22- 17:3` 등)은 후속 정리 대상
