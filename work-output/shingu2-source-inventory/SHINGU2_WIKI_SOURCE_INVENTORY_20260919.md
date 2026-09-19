# SHINGU2 WIKI SOURCE INVENTORY — 2026-09-19 (READ ONLY 조사 결과)

SOURCE OF TRUTH: Feel2Real 내신 Wiki (admin 세션, workspace `legacy · admin/modu`)
조사 시각: 2026-09-19. Wiki 데이터 수정 없음.

## Wiki 접근 식별자

- UI: `https://feel2real.co.kr/problem-generator/wiki/index.html?role=ADMIN`
- API base: `/problem-generator/wiki/api/`
- Context 파라미터(정확한 값): `school=신구중&grade=2학년&year=2026&semester=2학기&exam=중간고사`
  - ⚠ `grade=2`, `exam=중간`으로 조회하면 빈 배열이 반환됨. 값은 UI select의 label이 아니라 `2학년`/`중간고사`.
- 주요 endpoint (GET, read-only 검증 완료):
  - `exam-materials?{ctx}&material_type=PAST_EXAM&all_years=true` — 기출 목록
  - `exam-materials/{id}/body`, `exam-materials/{id}/html` — 본문/HTML
  - `weekly-exams?{ctx}` — 회차 세트 목록
  - `weekly-exams/{id}/artifacts/{student-paper|answer-explanation|production-verification}` — 제작물
  - `educator-insights?limit=100&{ctx}` — 수업 인사이트
  - `digital-twins/2` — 신구중2 Twin
  - `workspaces/final/context?{ctx}` — 통합 Context (PARTIAL 표시)

## A. 실제 기출 (PAST_EXAM, 8건) — 내용 판독 확인됨

| Wiki ID | title | 비고 |
|---|---|---|
| 49 | 2026년 신구중2 1학기말 기출 (modu, 09-09) | AI참조 통합본. 표지+7쪽 전 구조화. 본문 16KB 판독 확인 |
| 57~63 | 2026년 신구중2 1학기말 기출 원본 1~7 (admin-f2r, 09-11) | JPG 7장 전사본. 본문 1.1~2.4KB/쪽 확인. id62=Q21~23(Viral Marketing/Anchoring) 페이지 |

- 시험 정보(id49 본문): 신구중2, 2026-07-01, 영어(과목코드10), 100점, 선택형 25(2점4+3점12+4점9)=80점, 서답형 4(6점2+4점2)=20점.
- 출제 유형 DNA 전체 분석: `~/Documents/feel2real-buildit-r6/work-output/shingu2-round6-70/ACTUAL_EXAM_DNA.md` (S1~S21).
- 미 verification: 기출 Q22(보기 a~e 이미지) — 전사본에 "판독 불가" 메모. 신규 문제의 직접 Source 사용 금지 유지.

## B. 학교 프린트 (SCHOOL_PRINT, 4건)

| Wiki ID | title | 상태 |
|---|---|---|
| 11 | 5과 학습지 원본 (The City as a Canvas / Street Art in London) | HTML 89KB 판독 확인 |
| 12 | 5과 학습지 필기 | HTML 100KB, 단어+뜻 병기 필기본 |
| 112 | 6과 학습지 원본 (English 2 L6. The Greatest Love for All) | 2026-09-18 사진 대조본 교체 업로드, 36KB 확인 |
| 113 | 6과 학습지 필기(본) | 2026-09-18 업로드, 파랑=학생필기/빨강=오답교정 표시, 38KB 확인 |

- 로컬 `print_id64/65`(r6 sources)는 6과 구버전. Wiki id112/113이 최신 정본.

## C. 기존 제작 문제 (weekly-exams, 7회차)

| 회차 | Wiki id | 문항 | 상태 | 구성 확인 (전지 발문 추출) |
|---|---|---|---|---|
| 1회(40) | 3 | FINAL | Q1~29 객관식 + Q30~40 서답형 | 5과 중심(v2.3). 서답형 포함 |
| 2회(70) | 4 | FINAL | 전문항 객관식 | 빈칸/영영/개수세기/대화/관계사 대량 |
| 3회(40) | 10 | FINAL | 전문항 객관식 | 5과 어휘·영영·관계사·대화 위주 |
| 4회(70) | 13 | FINAL | FORMAT MASTER | 로컬 8PAGE_FINAL v15와 동일 계보 |
| 5회(40) | 18 | DRAFT | 전문항 객관식 | 로컬 최신본과 동일 계보 |
| 6회(70) | 21 | DRAFT | 전문항 객관식 | 로컬 r6 FINAL(270KB) 업로드본 |
| 7회(40) | 24 | DRAFT | 전문항 객관식 | "오답 모의고사"(5·6과 오답 복습) 성격, 09-18 제작. Schofield 소재 재사용함 |

- Hub 표시: 다음 회차 = 8회. 최근 FINAL = 4회·70.
- 1~3회 전지 발문 전수 확보 → ROUNDS1-5_DUPLICATION_MAP의 "PARTIAL" 공백 해소 가능(본 파일 §D-2).

## D. 제작 참고자료

### D-1. Digital Twin / 인사이트 / Context
- Digital Twin id2 (신구중2 기본): DNA "영영풀이·패러프레이징·수동태/관계사 어법·복수정답·조건표 서답형", 유형 8종, 규칙: 기존 HTML 출제 DNA·유형 순서 준칙 / 같은 소재·문장 반복 금지.
- educator-insight id10 (박원장 인사이트, ★중요): **"1학기와 비교해서 2학기는 서답형 문제가 출제되지 않을 예정"** + 문법 오류 강조점 4가지:
  1) 4형식→3형식 전환 시 전치사  2) continue류 부정사/동명사 겸용 동사  3) 지각동사+목적어+pp  4) too…to/enough to → so…that 전환(시제 일치/의미상 주어 명시/문말 목적어 it·them)
- Context 상태: PARTIAL (기출8·프린트4·Twin연결, AI 작업기록 0·학생오답 0·제작검수 0)
- 저장된 문제 40건(STEP3 questions), 오답 모의고사 회차 "오답 1회" 사진 0장(진행 중).

### D-2. 로컬 보조 (template/rules/analysis — feel2real-buildit + feel2real-buildit-r6)
- FORMAT MASTER: `work-output/round4-70/신구중2_..._8PAGE_FINAL_v15_BROWSER_VERIFIED.html` (A4 2단, 9.5pt/1.42, 12/11/10/11mm, qblock 불분리)
- 규칙: `docs/exam-generation/HTML_EXAM_PROMPT_AND_EDITING_RULES.md`, `docs/exam-generation/SHINGU2_ROUND5_EDITING_LESSONS_20260915.md` (§11 실행순서, §12 FINAL 금지 조건)
- 6회 분석 산출물(`~/Documents/feel2real-buildit-r6/work-output/shingu2-round6-70/`, branch `origin/docs/shingu2-round6-standard`):
  ACTUAL_EXAM_DNA.md / ROUND4_70_STRUCTURE_ANALYSIS.md / ROUNDS1-5_DUPLICATION_MAP.md / Q1-Q70_BLUEPRINT.md / FINAL_QA.md / sources(past_id49,57~63 + print_id11,12,64,65)
- 과사용 회피 지시(4+5회 합산, 8회에도 유효): 빈칸 30→결합형만, 보기진술 12→개수·진위결합, 영영 10→동시판정, 관계사 9+어법 7→결합형, 대화 14→추론·순서통합, 짝짓기표 7→2개 이내 4요소.

## E. 1~3회 전수 대조 결과 (8회 중복검사용 핵심)

- 1회: Q1~29 객관 = 어휘영영(1,3,5,9,29), 빈칸(2,6,7,26,27), 어법개수(8), 예문적절(10), 내용일치/불일치(11,13,15), 밑줄의미(12,14), Wilson(16), 주제종합(17), 순서(18,20), 복수정답(19,24), 관계사(23,25,28), 대화(21,22) / Q30~40 서답형 11개
- 2회(70): 어휘·영영 대량(1~10, 57~70), 빈칸(13,25,52,53,59,62,66), 내용(11,14,17,20,22,23,24,28,30), 개수세기(16,19,26,43), 관계사(32,42,47,48), 대화(34~41), 번역(45,51), used to(54), 수정(49,50,56)
- 3회(40): 어휘·영영(1~8,29), 빈칸(5,9,15,21,32,40), 밑줄의미(10,16,22), 관계사(13,20,25), 어법(12,14,18,19,36,38), 내용(11,17,23,24), 대화(27~34), 번역(39)
- 1~3회 모두 5과 중심(6과는 4회부터本格). 8회가 5·6과 통합 신규 회차로서 최적.
