# HANDOFF_Q11-Q20.md — 집 PC 인계 문서

---

## 1. 저장된 Canonical Source (Repository 내)

### ACTUAL PAST EXAM CANONICAL SOURCE
```
docs/exam-generation/sources/shingu3/신구중3_2026_1학기말_영어_기출_Claude참조.html
```
- 크기: 24,491 bytes
- 내용: 신구중3 2026 1학기 기말고사 영어 실제 기출 21문항 + 2서답형 전체 구조화 HTML
- 출처: ChatGPT Library → 로컬 바탕화면 → Repository 보존

### LESSON 6 CANONICAL SOURCE
```
docs/exam-generation/sources/shingu3/신구중3_6과_학교프린트_필기(9-17).html
```
- 크기: 4,135 bytes
- 내용: Lesson 6 Make the World Beautiful (p.109-112 Main Reading, Language Use so that, Listening 1&2, Word List)
- 9-17 첨부본, 4회 제작 시 유일한 Content Source

---

## 2. Q11-Q20 제작 산출물

### CONTENT REVIEW HTML
```
docs/exam-generation/sources/shingu3/신구중3_4회_Q11-Q20_REBUILD_REVIEW.html
```
- Q11-Q20 문항 내용 검증용 HTML (조판용 아님)
- 문항번호, 공통지문, 발문, 지문, 빈칸, 밑줄, <보기>, 선택지 ①~⑤ 정확히 보존
- 밑줄 형식: `ⓐ <u>단어</u>` 준수

### Blueprint
```
docs/exam-generation/sources/shingu3/Q11-Q20_Blueprint.md
```
- 각 문항별: 기출 Reference, 기출 발문/구조, 9-17 Source, 변형 방법, 예상 정답

### 출제검수
```
docs/exam-generation/sources/shingu3/Q11-Q20_출제검수.md
```
- 문항별: 기출 Reference, 9-17 Source, 변형, 정답, ①~⑤ 개별 검증, 수정 이력, PASS

---

## 3. 집 PC에서 작업 시작 순서

```bash
cd /path/to/feel2real-buildit
git pull origin main  # 또는 작업 브랜치
```

### 읽어야 할 파일 (우선순위)
1. `docs/exam-generation/sources/shingu3/신구중3_2026_1학기말_영어_기출_Claude참조.html` — 실제 기출 21문항 전체
2. `docs/exam-generation/sources/shingu3/신구중3_6과_학교프린트_필기(9-17).html` — 9-17 Content Source
3. `docs/exam-generation/sources/shingu3/Q11-Q20_Blueprint.md` — Q11-Q20 설계도
4. `docs/exam-generation/sources/shingu3/Q11-Q20_출제검수.md` — 검수 기록
5. `docs/exam-generation/sources/shingu3/신구중3_4회_Q11-Q20_REBUILD_REVIEW.html` — 내용 검증용 HTML

---

## 4. 다음 작업: Q21-Q30

### 실제 기출 Reference (Q21-Q30)
| 번호 | 유형 | 기출 발문 |
|------|------|-----------|
| 21 | 빈칸 (Ⓐ) | (A)를 묘사한 다음 장면에서 Ⓐ에 들어갈 말은? |
| 22 | 복수정답 (단계) | 벽화 그리기 단계 <보기>에서 모두 고르면? (정답 2개) |
| 23 | 어법 (ⓐ~ⓔ) | ⓐ~ⓔ 중 어법상 틀린 것은? |
| 24 | 비교 (Minsu vs I) | Minsu와 I를 비교한 내용으로 가장 적절한 것은? |
| 25 | 문장 연결 (ㄱ~ㅁ) | (A)(B) 사이 자연스러운 연결 내용 <보기>에서 고르면? |
| 26+ | 서답형 1-2 | 가짜 상관관계 강연 / Stanley-Judge 대화 재구성 |

### 9-17 Source 활용 영역 (미사용 잔여)
- Word List: capture, exhibit, exist, imagination, indoors, inspiration, obvious, peel, pleasing, tourist attraction, wave
- Language Use: so that 변형 (so as to / in order to / in order that)
- Listening 1&2 변형 추가 대화
- p.112 DDP 세부 내용

---

## 5. 검증 완료 상태 (Q11-Q20)

| Q | 기출 Ref | 정답 | PASS |
|---|----------|------|------|
| 11 | 11번 빈칸 | ④ | PASS |
| 12 | 12번 어법 | ② | PASS |
| 13 | 13번 순서 | ④ | PASS |
| 14 | 14번 구성 | ③ | PASS |
| 15 | 15번 빈칸 | ④ | PASS |
| 16 | 16번 대체 | ③ | PASS |
| 17 | 17번 수칙 | ③ | PASS |
| 18 | 18번 의미 | ③ | PASS |
| 19 | 19번 짝짓기 | ⑤ | PASS |
| 20 | 20번 대체 | ③ | PASS |

**전체 PASS**: 10/10, HOLD: 0

---

## 6. Git 상태

```
SOURCE COMMIT SHA = [push 후 기록]
PUSH = SUCCESS
```

### 변경된 파일 (git status 확인 필요)
- `docs/exam-generation/sources/shingu3/신구중3_2026_1학기말_영어_기출_Claude참조.html` (NEW)
- `docs/exam-generation/sources/shingu3/신구중3_6과_학교프린트_필기(9-17).html` (NEW - local에 없던 파일 origin/main에서 복원)
- `docs/exam-generation/sources/shingu3/신구중3_4회_Q11-Q20_REBUILD_REVIEW.html` (NEW)
- `docs/exam-generation/sources/shingu3/Q11-Q20_Blueprint.md` (NEW)
- `docs/exam-generation/sources/shingu3/Q11-Q20_출제검수.md` (NEW)

---

## 7. 중요 원칙 상기

1. **OpenCode = 내용 제작/검수만** — 시각 편집/조판/CSS/로고/페이지 균형은 별도 ChatGPT 작업
2. **실제 기출 HTML 직접 읽기** — DNA 요약 MD만 보고 제작 금지
3. **밑줄 형식** — `ⓐ <u>단어</u>` (기호에 밑줄 금지)
4. **10문항 Batch** — 사용자 승인 전 다음 Batch 제작 금지
5. **정답 분포 기계적 조정 금지** — 문항 자연스러움 우선

---

집 PC에서 `git pull` 후 위 파일들부터 읽고 Q21-Q30 Blueprint 작성 시작.