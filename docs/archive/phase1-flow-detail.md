# Phase 1 자료 업로드 + LLM 분석 플로우 — 상세 구현 노트

> FLOWS.md §5.0(구 MVP §5.0)에서 분리된 아카이브. 모든 단계 ✅ 구현 완료.
> 현재 흐름 요약은 `docs/FLOWS.md §5.0` 참고.

---

## 1단계 — 학생 파일 업로드 (1개 이상, 묶음 단위)

```
POST /api/sources                         multipart/form-data
  files=...                               파일 1개 이상 (한 묶음 = 한 시험 단위)
  contentKind=GENERAL|EXAM_BLANK          ← 학생이 폼에서 "일반 자료"/"시험지" 선택 (필수)
                                            (EXAM_GRADED는 학생이 직접 보낼 수 없음 — 서버 promote 전용)
  schoolName, grade                       ← students 정보로 default
  examType, examYear, examSemester        ← nullable (시험지이고 학생도 모르면 LLM 유추)
  ↓
  → 각 파일 sources INSERT (STUDENT_MATERIAL, contentKind 세팅, status=PENDING)
  → 트랜잭션 commit 후 묶음 단위로 SourceAnalysisService.analyzeBatchAsync() 1회 호출
```

`SourceService.saveBatch` — 한 묶음 = 한 시험 단위. 묶음 안의 모든 파일은 같은 contentKind/메타를 공유하며, 시험지면 모두 같은 `exam_id`로 연결된다.

## 2단계 — 자료 타입 분류

학생이 1단계 폼에서 라디오 버튼으로 "일반 자료" 또는 "시험지"를 선택한다 (LLM 분류 호출 없음).
- "일반 자료" → `GENERAL`
- "시험지" → `EXAM_BLANK` (분석 후 채점 흔적 감지 시 `EXAM_GRADED`로 promote — 4단계)

## 3단계 — GENERAL 묶음 즉시 완료

contentKind가 `GENERAL`이면 묶음의 모든 source를 즉시 `COMPLETED`로 전이. LLM 호출 0회. 학생 대시보드에 바로 표시되고 채팅 컨텍스트로 사용 가능.

## 4단계 — 시험지 묶음 자동 분석 (백그라운드)

`SourceAnalysisService.analyzeBatchAsync` — contentKind가 `EXAM_BLANK`(또는 `EXAM_GRADED`)인 묶음에 대해 비동기 실행.

```
1) 모든 source → ANALYZING
2) 메타 합성 (LLM 추출값 우선, 학생 폼은 fallback)
   - ExamMetaInferenceService LLM 호출은 학생 입력 유무와 무관하게 항상 1회 수행
   - 시험지에 적힌 학교/학년이 진실에 가까우므로 LLM 응답을 신뢰
   - LLM이 null로 응답한 필드만 학생 폼 값으로 보충
   - LLM 호출 실패 시 학생 폼 값만 사용 (FAILED로 가지 않음)
3) exams dedup → INSERT 또는 재사용
   - 메타 6필드가 모두 채워졌으면 INDEX idx_exams_dedup_key로 검색
   - 매치 시 기존 exam_id 재사용 / 비매치 시 신규 INSERT (display_name 자동 조합)
   - 묶음의 모든 source에 같은 exam_id 연결
4) ExamExtractionService.extractFromBatch — 묶음 전체 이미지 1회 LLM 호출
   - 응답: {
       meta,
       passages[{id: "P1", content: "...본문..."}],
       questions[{question_no, question_text, question_type, choices,
                  correct_answer, student_answer, is_correct,
                  point: 3,
                  category: "vocabulary"|"grammar"|"memorization"|"reading"|"other"|null,
                  passage_ref: "P1"|null}, ...]
     }
   - passages 분리 기준: 여러 문제가 공유하는 지문이거나 충분히 긴 본문(5~6줄 이상)만 분리.
     한 문제에만 딸린 짧은 본문은 questions.question_text 안에 포함.
5) questions 처리
   - dedup 비매치 → passages 먼저 INSERT(title=content 첫 줄 60자 컷, source_origin=exam.display_name)
     → LLM 임시 id("P1") ↔ passage_id 맵 구축
     → questions INSERT (source=READFILE, exam_id 세팅, category lookup, passage 맵 lookup)
   - dedup 매치   → 기존 passages/questions 재사용 (INSERT skip)
6) 채점 흔적 판별 + 분기 종료
   - questions 중 is_correct가 채워진 게 1개라도 있으면 → EXAM_GRADED promote
       · analysis_result JSON 저장 → status = PENDING_REVIEW
   - 없으면 → EXAM_BLANK 유지, analysis_result NULL → status = COMPLETED
```

> **LLM 호출 횟수**: 시험지 묶음당 항상 2회 — `inferMetaFromBatch`(~256토큰) + `extractFromBatch`(~8192토큰).

## 5단계 — 학생 검증 페이지 (메타 + 채점 한 화면)

`EXAM_GRADED` + `PENDING_REVIEW` 상태인 source만 진입.

```
GET /api/sources/{id}/exam-review  → analysis_result JSON 반환
```

UI 구성: 시험 메타 수정 + 채점 토글(is_correct) + 배점 input. `returnUrl` 쿼리로 완료 후 돌아갈 위치 결정.

## 6단계 — 학생 [전체 확정] 백엔드 처리

`ExamReviewService.confirm`:

```
POST /api/sources/{sourceId}/exam-confirm
  body: { meta: {...}, answers: [{question_no, is_correct, point}, ...] }

0) IDOR 가드: verifyOwnsSource
1) exam.updateMeta(meta) — exam_id 유지, display_name 재계산
2) questions 매핑 → student_answers INSERT + point UPDATE
3) student_scores INSERT (totalPoints>0이면 점수, 아니면 정답률만)
4) source.status = COMPLETED
5) propagateExamScoreToConversations
```

> 책임 분리: 4단계(`SourceAnalysisService`) → exams/questions INSERT. 6단계(`ExamReviewService`) → 메타 update + student_answers + student_scores.

## 7단계 — 대화 metadata 동기화

```json
{
  "student": { "name": "...", "grade": 1, "school_name": "..." },
  "subject_id": 1,
  "sources": [
    { "source_id": 12, "exam_id": 5, "is_exam": true, "exam_score": "75/100 (8/10)" }
  ]
}
```

`exam_score` 형식: 절대점수 있으면 `"75/100 (8/10)"`, 없으면 `"8/10"`.
