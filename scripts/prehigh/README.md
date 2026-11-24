# 예비고1 실전 독해(18~45번) 회차 자료 만들기

`/naesintest/pre-high`의 **6부 고2 실전 독해 모의고사**(자료 형식은 2부 고1 실전 독해와 같다)에 싣는
회차 자료를 내용 JSON 하나로 만든다.

| 만드는 것 | 설명 |
|---|---|
| `q18.webp` ~ `q45.webp` | 문항 그림 28장(폭 940px) |
| `p41-42.webp`, `p43-45.webp` | 공통 지문 그림 2장 |
| `round.json` | 서버가 읽는 자료: 그림 이름·정답·해설·표시 좌표(marks)·어휘 50문항·내부 기록용 source |
| `paper.pdf` | 전체 문제지(A4 2단, 관리자 전용 내려받기) |

## 준비물
- Python 3 + Pillow(PIL). 그 밖의 패키지는 쓰지 않는다.
- 크롬. 기본 경로는 `C:\Program Files\Google\Chrome\Application\chrome.exe`이고,
  `--chrome 경로` 또는 환경변수 `PREHIGH_CHROME`으로 바꾼다.
- 글꼴: 영어 Liberation Serif(없으면 폭이 같은 Times New Roman), 한글 Noto Serif KR(없으면 바탕).

## 실행
```bash
python scripts/prehigh/build_round.py scripts/prehigh/content/part6/r02.json src/main/resources/prehigh-part6/r02 \
    --number 2 --check-dir <저장소 밖 확인용 폴더>
```
- `--number`: 회차 번호(없으면 내용 JSON의 `number`). 폴더 이름 `rNN`과 같아야 서버가 읽는다.
- `--grade`: 문제지 표지의 학년 표시(기본 `고2`). 표지는 `제N회 고2 영어 독해 실전 모의고사 (18~45번)`.
- `--check-dir`: 문항 그림 위에 근거(노랑)·정답(파랑 테두리)을 덧그린 PNG를 만든다. **회차마다 전부 눈으로 본다.**
- 2분쯤 걸린다(크롬을 예닐곱 번 띄운다).

회차를 실은 뒤에는 `PreHighPart6Store.ROUND_COUNT`를 올리고 `./gradlew test --tests "*PreHighPart6*"`를 돌린다.

2~20회의 편집 가능한 변형 지문·선택지·정답·해설·어휘는 `scripts/prehigh/content/part6/rNN.json`에 있다. 내용을 고칠 때는 이 파일과 해당 회차의 응시 자료를 함께 갱신한다.

## 내용 JSON 형식(round_content.json)
```json
{ "number": 1, "source": "내부 기록용 원본 이름(화면·문제지에 나오면 안 됨)",
  "reading": [ { "number": 18, "stem": "다음 글의 목적으로 가장 적절한 것은?", "answer": 3,
                 "blocks": [["p", "지문 …"], ["fn", "* word: 뜻  ** word: 뜻"]],
                 "choices": ["…", "…", "…", "…", "…"], "passageKey": null,
                 "evidence": ["해설이 인용한 영어 구절", "…"], "explanation": "정답 ③ · 글의 목적 (2점)\n…" } ],
  "passages": { "41-42": { "head": "[41~42] 다음 글을 읽고, 물음에 답하시오.", "blocks": [["p", "…"]] } },
  "words": [ { "text": "idle", "choices": ["뜻1", "뜻2", "뜻3", "뜻4", "뜻5"], "answer": 2 } ] }
```
- `reading`은 18~45번 28문항, `words`는 50문항. 41~45번은 `passageKey`(`41-42`, `43-45`)로 공통 지문을 가리킨다.
- 글 안에서는 `<u>`·`<i>`·`<b>`·`<blank/>`(빈칸)만 쓴다. 발문 끝의 ` [3점]`은 그대로 적는다.
- 블록 종류: `p`(지문) · `letter`(편지, 줄바꿈 `\n`) · `box`(주어진 글·문장 상자) · `lp`((A)(B)(C) 단락) ·
  `lpb`(장문의 (A)~(D) 상자) · `fn`(각주, 항목 사이 빈칸 2개) · `boxp`(지문+각주) · `summary`(요약문, `<b>(A)</b>`) ·
  `chart`(묶음 막대 도표: `title` 줄 목록, `legend` 2개, `rows` `[이름, 값, 값]`, `note`) ·
  `notice`(안내문: `title`, `lines` `[종류, 글]` — `lead`·`item`·`head`·`sub`·`trow`).
- 선택지 배치는 모양으로 정한다: `A … B`(요약문 표) · `(A) - (C) - (B)`(3열) · `(a)`~`(e)`(5열) ·
  짧은 낱말(3열, 길면 2열) · 그 밖(한 줄에 하나).
- `choices`가 없는 문항(도표·어법·어휘·무관한 문장·문장 삽입)은 지문 속 원문자 ①~⑤가 선택지다.

## 표시(marks)를 만드는 방법
1. `evidence` 구절을 지문 HTML에서 찾아 `<span data-m="evidence:번호:순서">`로 감싼다. 따옴표 모양, 원문자,
   `(a)` 표지, 밑줄 태그는 무시하고 찾는다. **못 찾으면 멈춘다**(해설 인용과 지문이 다르다는 뜻).
   41~45번은 공통 지문에서 먼저 찾는다.
2. 정답 자리에 `data-m="answer:번호"`를 단다: 정답 선택지 줄, 요약문 표의 행, 지문 속 정답 원문자
   (도표·무관한 문장), 정답 밑줄 낱말(어법·어휘), `( ④ )`(문장 삽입), 공통 지문의 `(d) 낱말`과 선택지 둘 다(42·44번).
3. 크롬이 그 HTML을 폭 940px로 열어, 페이지 안의 스크립트가 `getClientRects()`로 표시 위치를 재서 내보낸다
   (`--dump-dom`). 같은 HTML을 그대로 찍어 그림을 만들므로 좌표와 그림이 어긋나지 않는다.
4. 여러 줄에 걸친 구절은 줄마다 사각형 하나로 합치고, 그림 크기에 대한 1/10000 단위 정수로 바꾼다.
5. `round.json`을 쓰기 전에 서버와 같은 조건(28문항·50단어·좌표 범위·문항당 30개 이하)과
   "문항마다 근거 1개 이상·정답 1개 이상"을 확인한다.

## 출처 낱말 검사
문제지·그림 글자에 `원본`·`출처`·`학평`·`학력평가`·`변형`이나 `source` 문자열이 있으면 만들지 않고 멈춘다.
`source`는 `round.json`에만 내부 기록으로 남는다. 원본 시험 원문은 저장소에 넣지 않는다.

## 파일
- `prehigh_round.py` — 순수 함수(HTML 만들기, 인용 구절 찾기, 좌표 환산, round.json 조립·검증, 출처 낱말 검사)
- `build_round.py` — 크롬 실행, 그림 자르기, 파일 쓰기
- `test_prehigh_round.py` — 단위 테스트: `python -m unittest discover -s scripts/prehigh -p "test_*.py"`

## 2부(고1) 그림과 다른 점
2부 22회를 만든 스크립트는 남아 있지 않아 그림을 보고 다시 만들었다. 폭(940px)·본문 글자 크기(30px)·
줄 간격(45px)·상자·각주·선택지 배치는 같게 맞췄다. Windows에서 만들면 영어는 Times New Roman,
한글은 Noto Serif KR로 찍힌다(2부는 리눅스의 Liberation Serif·Noto Serif CJK).
문제지 PDF는 문항마다 발문을 적고(2부는 `[31~34]` 묶음 발문), 단은 크롬이 나눈다.
