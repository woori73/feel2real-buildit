# en-worksheet-a4 레시피 (영어 지문 분석 학습지)

## 템플릿 생성

```bash
designcraft-cli script create-template.txt --in ../ko-textbook-a4/template.designcraft
# → template.designcraft (A4, ws-* 스타일 8종 + ws-c-* 문자 스타일 6종 + ws-* 스와치 5종 CMYK)
```

## 스타일

| 이름 | 용도 |
|---|---|
| `ws-passage` | 지문 원문 (10pt 양쪽정렬) |
| `ws-sent` | 문장별 해석 (9.5pt) |
| `ws-vocab` | 어휘 (9.5pt) |
| `ws-syntax` | 구문 분석 (9.5pt) |
| `ws-grammar` | 문법 설명 (10pt) |
| `ws-question` | 서술형 문제 (10pt) |
| `ws-answer` | 정답·해설 (9.5pt Bold) |
| `ws-blank` | 작성 공간 (10pt, 넓은 줄간격) |
| `ws-c-passage/sent/vocab/syntax/grammar/answer` | 색상 문자 스타일 (CMYK) |

## 3종 출력 빌드

```bash
python3 build3.py
# manuscript-original.md → original.pdf (4p)
# manuscript-workbook.md → workbook.pdf (5p)
# manuscript-teacher.md  → teacher.pdf (6p)
```

### 원고 형식

- 지문 섹션 시작에 `<!-- worksheet:passage-lock -->` 마커 필수
  → 서버가 해당 스토리를 원문 보호 잠금
- 어휘는 탭 구분 텍스트 (`단어\t품사\t뜻\t예문`) → `table.convertFromText`로 표 변환
- 표 너비는 텍스트 프레임(481.9pt) 이내로 자동 조정

## 원문 보호

- `PublishingEditService.PASSAGE_LOCK_MARKER` 참조
- AI 계획: `PublishingAiService.plan()`에서 잠금 스토리 대상 차단
- 실행: `expand()`에서 `userConfirmed=true` 없이 차단 (403)
- AI 경로: `applyPlan()`에서 `userConfirmed` 강제 제거
