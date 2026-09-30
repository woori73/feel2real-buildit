# 수능영어 Wiki — MONDAY DAY-2 HANDOFF (학생 오답 → 약점 DNA)

생성: 2026-09-27(DAY-1 배포 당일). PR #430(`feat/wiki-csat-english-dna-v1`)을 머지·배포한 뒤의 상태를 전제로 한다.

## 0. 전제·불변 규칙

- SOURCE PRIORITY: **평가원 기출(LEVEL 1) > 이명학 공개 분석(LEVEL 2) > Wiki 종합(LEVEL 3)**. 강사 의견을 평가원의 공식 출제 의도로 표시하지 않는다.
- 기존 `OFFICIAL_SOURCE` / `ACTUAL_EXAM` / `WIKI_SYNTHESIS` 행과 57개 문항 정답은 DAY-2에도 변경·덮어쓰기 금지. 관점 차이는 새 행으로 병존시킨다.
- **학생 약점은 "몇 번 틀렸다"만으로 생성하지 않는다.** 반복되는 DNA `tag`(dimension)과 학생이 **실제 선택한 오답 선지의 TRAP**을 근거로 분석한다.
- 근거 없는 학생 분석·가짜 placeholder 데이터는 화면에 표시하지 않는다. 데이터가 없으면 empty-state("준비 중")가 정상 상태다.
- 학생 오답 사진 원본·OCR 전문은 저장하지 않는다. `source_filename` 참조만 남긴다.

## 1. 월요일 입력(실제 자료)

| 학생 | 학교 | 입력 |
|---|---|---|
| 최승원 | 세화고3 | 오답 사진(회차별) |
| 박민서 | 영동고3 | 오답 사진(회차별) |
| 이하진 | 영동고3 | 오답 사진(회차별) |

사진은 LOCAL/학생 workspace에서만 다루고 **production에 학생 오답 사진을 넣지 않는다.**

## 2. 사진 입력 계약(최소 필드)

`csat_wrong_answers`에 다음을 채운다(기존 DDL 그대로 사용, 마이그레이션 불필요):

1. `student_name` — 학생
2. `exam_name` — 시험 회차(예: `2027학년도 6월 모의평가 영어`) → `csat_exams.label`과 일치시켜 회차로 해석
3. `question_no` — 문항번호
4. `wrong_answer` — 학생이 고른 선지(1~5/1~7)
5. `correct_answer` — 평가원 정답(문항 등록값과 대조)
6. `source_filename` — 사진 파일명 참조(원본 업로드·보관 없음)
7. 자동 파생: `question_id`(= `csat_questions`에서 `exam_id`+`question_no` 조인), `csat_type_id`/`csat_type_name`(`main.csat_type_for_number`)

OCR/vision 파이프라인은 DAY-2에 구현하되, 위 6필드가 채워지면 이후 단계는 전부 기존 테이블로 자동 연결된다.

## 3. 12단계 분석 흐름(학생 1명 1회차 기준)

1. 사진 → 6필드 추출(§2)
2. `exam_name` → `csat_exams.id` (회차 확정; 실패하면 추측하지 않고 회차 목록에서 고르게 한다)
3. `exam_id + question_no` → `csat_questions.id`(정답·선지·해설 확보)
4. `csat_questions.answer` vs `wrong_answer` → 오답 확정(정답과 같으면 행 만들지 않음)
5. 문항 표준유형(`csat_type_id`) → 유형별 오답 분포(예: 빈칸 추론 4개 중 3개)
6. `csat_question_dna WHERE question_id=? AND entry_type='TRAP'` → 같은 문항의 함정 목록
7. `TRAP.choice_no == wrong_answer` → **학생이 실제로 고른 선지에 매핑된 함정 태그** 확정
8. 함정 태그 + 그 문항의 `entry_type='DNA'` 태그 → 사고 능력 dimension으로 승격
9. `csat_dna_claims WHERE exam_id=?` → 해당 dimension의 회차 수준 CLAIM과 연결
10. `csat_dna_comparisons WHERE (exam_a_id, exam_b_id)` → 6월↔9월에서 그 dimension이 REPEATED/STRENGTHENED/REDUCED인지 연결
11. 행에 `expert_source_id`가 있으면 `csat_expert_sources.video_url + timestamp_start` → "이명학 분석 · 구간" 링크로 연결
12. 학생 약점 DNA 요약 생성 규칙: **2회차·2문항 이상에서 반복된 dimension + 반복된 trap**만 약점으로 기록. 단일 오답은 "관찰"로만 표시하고 약점 CLAIM을 만들지 않는다.

## 4. 화면(③④⑤) 연결 지점

- ③ 학생 오답: §2 필드를 회차·문항번호별로 목록화. 각 행에서 해당 문항(`csat-q-N`)으로 이동.
- ④ 학생 약점 DNA: dimension(tag) 기준으로 학생 오답을 그룹핑 → 근거 문항 chips + 매핑된 TRAP + CLAIM + 비교 판정 + 강사 구간 링크.
- ⑤ 개인별 대비: ④의 상위 약점 순서대로 "회차별 반복 문항형" 목록. 예측·단정 문구 금지(§14).
- 3개 탭 모두 현재는 DAY-2 준비 중 empty-state. 데이터가 들어와도 provenance/STATUS 배지 규약(실제 문항 근거 / 공식 자료 / 강사 분석 / Wiki 종합)을 그대로 사용한다.

## 5. 재현 커맨드(로컬)

```bash
# 1) Wiki 백엔드(스키마 additive ALTER 자동) + DAY-1 시드 DB
cd problem-generator/wiki-backend-src/backend
BUILDIT_WIKI_DB_PATH='C:/Users/sxxra/csat-sources/wiki-dna-day1.db' \
BUILDIT_WIKI_ALLOW_UNSIGNED_IDENTITY=1 python -m uvicorn main:app --host 127.0.0.1 --port 8000

# 2) 이명학 EXPERT_ANALYSIS 29건 멱등 시드(이미 있으면 전부 skip)
cd problem-generator/wiki-backend-src
python scripts/seed_csat_expert_lee.py --base http://127.0.0.1:8000

# 3) 게이트
python -m pytest tests/ -q                      # ai_router/type_contract 6건은 main 기저 실패(본 PR과 무관)
cd ../wiki-src && npx tsc --noEmit && npx vite --port 5173
```

## 6. 검증된 기저 수치(2026-09-27 실측, 로컬 `wiki-dna-day1.db`)

- 평가 3회차 · 문항 57 · 원본 자료 7 · 기존 DNA 114 / CLAIM 9 / 비교 5 (변경 0)
- `EXPERT_ANALYSIS` 29건 = 출처 영상 2 + 문항 DNA 12 + CLAIM 12 + 비교 5
- agreement_kind 분포: AGREEMENT 7 / DIFFERENT_EMPHASIS 12 / EXPERT_ONLY 10 (UNKNOWN 0)
- 문항 정답指纹 `md5 = a2e654e57c4365e2f1dee1c4c7e62ba9`(57문항, 시드 전후 동일)
- 검증 제외 목록(출처 불충족 → 강사 분석으로 등록하지 않음): 작년 수능 3.11%/1등급 비율 관련 통계, 23·25학년도 9평 비교, 31번 오답률 수치 순위, 사과문 인용, 898~917초 "6평 수준으로 공부" 진술
