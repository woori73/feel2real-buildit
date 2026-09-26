# NEWSTART KNOWLEDGE PRESERVATION — 2026-09-26

유실 방지 전용 보존(백로그 cleanup phase 2). 코드/마이그레이션/DB 쓰기 0건.
수집·검증 자체는 원본 PR들이 수행했고, 본 문서는 어느 자료와 어떤 PR에서 보존됐는지의 출처 기록(manifest)이다.

| Preserved files | Source PR | 내용 | 상태 메모 |
|---|---|---|---|
| `data/newstart-cafe/gamian/articles/5672.json … 5957.json` (37) | #353 | 감면율 게시판 공개 글 원문+댓글/답글 (NNNN.json collector 형식) | main에는 `article_NNNN.json` 평탄화 형식 11건만 존재; 5904/5947/5948/5957은 ID 중복이나 형식이 달라 댓글 데이터가 유일한 사본 |
| `data/newstart-cafe/gamian/structured/cases.json` / `.jsonl` / `statistics.json` | #353 | 37개 실증사례 구조화 (case_type, 채무/재산, 감면율, manager prediction) | #417 Repair API / #421 seed와 스키마 정합은 AGENT 3 후속 |
| `data/newstart-cafe/gamian/{checkpoint,collection_state,index}.json`, `handoff.md` | #353 | 수집 진행 상태·인덱스·수집 handoff | 재현/감사용 |
| `docs/newstart/research-official/ccrs/01~05_*.md` | #353 | 신복위 공식자료 5종 (개인워크아웃/새출발기금/crosswalk/재산/통신채무), 30+ 출처 인용 | main에 없던 문서 |
| `docs/newstart/research/NEWSTART_CHAT_FAQ_CASE_CURATION_2026-09-22.md` + `newstart_chat_faq_case_candidates_2026-09-22.json` | #365 | 비식별 FAQ 26 + CASE 12 (OFFICIAL_VERIFY/CASE_SUPPORTED/CONFLICT_REVIEW 태깅, 줄 번호 provenance) | raw 상담방 TXT는 정책상 GitHub 비업로드 — 본 사본이 유일한 영구 기록 |
| `docs/newstart/research/NEWSTART_OPERATOR_FAQ_5568_4451_CURATION_2026-09-22.md` + candidates JSON + `NEWSTART_CASE_FOLLOWUP_GAPS_2026-09-22.md` | #368 | 운영자 FAQ 83 Q&A(33+50) machine-readable candidates + 5920/5987/5988/5752 후속-gap | 원문 글 5568/4451/5920 미커밋 — 유일한 사본 |
| `docs/newstart/knowledge/NEWSTART_AGENT4_OFFICIAL_VERIFICATION_2026-09-22.md` | #370 | V-01~V-21 공식 검증 판정(FOUND/NOT_FOUND/INSUFFICIENT) + CONFLICT 근거 C-01~C-04 | CASE→VERIFICATION→KNOWLEDGE 검증 레이어 |
| `docs/newstart/NEWSTART_JIPIJIGI_AUG_SEP_2026_KNOWLEDGE_CLASSIFICATION_DRYRUN.md` | #401 | 8·9월 카페 355건 분류(167 knowledge/188 noise), §17 419-row ingest proposal, §18 schema gap | ingest 실행은 AGENT 3 후속 별도 PR |
| `src/test/resources/canonical-golden/GOLDEN_OFFLINE_FIXTURES_2026-09-23.json` | #374 | canonical offline QA 골든 800줄 (CHAT_TRANSCRIPT 인용부는 provenance만 포함, raw 업로드 없음) | 코드 wiring은 #409에 흡수; fixture만 보존 |

## 비보존(제외) 항목

- #24 n8n 출력 unwrap(`N8nProblemGenerationService` +10줄/test/contract JSON): runtime 코드라 preservation 범위에서 제외. 원본은 close된 #24 브랜치(`feat/n8n-digital-twin-automation`, 삭제 안 함)에 그대로 있으며, 필요 시 별도 후속 PR로 이식한다.
- #353의 src 코드 전체(839 behind, main이 이미 초월), #368/#365의 그 외 handoff성 파일.

## 개인정보/시크릿 감사 메모리 (2026-09-26)

- 패턴 스캔(휴대폰/유선/이메일/계좌/카드/주민번호/token/secret): 0건.
- gamian 원문은 Naver 카페 **공개** 게시글이며 닉네임은 익명 식별자 — main의 기존 `data/newstart-cafe/gamian/articles/article_*.json`(authorNickname + contentRaw)과 동일한 수용 posture.
- private chat raw TXT 포함 없음(프로버넌스상 파일명·줄번호 참조만).
- JSON 45건 parse PASS, JSONL 전 줄 parse PASS, UTF-8 validate PASS, 빈 파일 0, 경로 중복 0.
