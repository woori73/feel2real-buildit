# 2028학년도 지식자료(`changes2028.json` 8항목) 적재 API 설계안 — 구현 전

## 조사 결과
- 기존 `POST /api/admin/admissions/load/knowledge-base`는 `question_types`만 읽고 학년도 2027·확정·GUIDE 역할·`kb_<key>` 중복키로 고정한다. 8항목을 이 형식에 넣으면 2028 계획이 2027 확정으로 섞이므로 사용하지 않는다.
- 항목 필드: `announced_date, checked_at, document_title, source_url, status(확정/예정), summary, topic`.
- 지식 항목 엔티티(`admissions_knowledge_items`)는 이미 학년도·확정 여부·역할·출처 URL·중복키·검증 메모·상태를 가진다. 스키마 변경은 필요 없다.

## 제안 API (별도 승인 후 구현)
`POST /api/admin/admissions/load/knowledge-items` (ADMIN, 기존 `/api/admin/**` 보안)
- body `{"items":[{"academicYear":2028,"title":"...","content":"...","category":"2028변경","sourceUrl":"...","sourceTitle":"...","yearConfirmed":true|false,"announcedDate":"..."}]}`
- 매핑: academicYear 필수(2027/2028만), `yearConfirmed` = 항목 status가 확정이면 true·예정이면 false, role = FACT, sourceClass = OFFICIAL, status = **DRAFT 고정**, dedupKey = `y<학년도>_<topic 슬러그>`, verificationNote에 출처 문서명·발표일·확인일 기록.
- 출처 필수: sourceUrl이 http(s)가 아니거나 비어 있으면 그 항목은 거부(전체 롤백).
- 재실행 안전: dedupKey가 있으면 건너뜀(0건 추가).
- 승인: 기존 `/knowledge/{id}/review`(review → approve)로 항목별 승인. 이 API는 승격하지 않는다.
- 학년도 구분: 검색은 이미 질문 학년도로 먼저 거르고(미지정=2027), 2028 질문은 2028 근거만 쓴다. 2028 항목은 계획 단계 고지 대상(`yearConfirmed=false` 또는 계획 문구).

## 테스트 계획
적재 DRAFT·건수, 출처 없는 항목 거부·롤백, 재실행 0건, 2027 질문에 2028 항목 미노출, 2028 질문에 ACTIVE 항목 노출, DRAFT 항목 미노출.
