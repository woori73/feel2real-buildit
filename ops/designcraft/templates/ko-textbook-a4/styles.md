# A4 교재 스타일 가이드

## 문단 스타일

| 이름 | 용도 | 속성 |
|---|---|---|
| `ko-body` | 본문 | Noto Sans KR Regular 10pt, 양쪽 정렬, 첫줄 들여쓰기 1em, 줄간격 16pt, koreanCharBreaks ON |
| `ko-heading1` | 제목 1 | Noto Sans KR Bold 18pt, 왼쪽 정렬, 위 18pt/아래 10pt, TOC 레벨 1 |
| `ko-heading2` | 제목 2 | Noto Sans KR Bold 14pt, 왼쪽 정렬, 위 14pt/아래 8pt, TOC 레벨 2 |
| `ko-heading3` | 제목 3 | Noto Sans KR Bold 12pt, 왼쪽 정렬, 위 12pt/아래 6pt, TOC 레벨 3 |
| `ko-passage` | 지문 | Noto Sans KR Regular 9.5pt, 양쪽 정렬, 줄간격 15pt (박스 테두리는 GUI에서 적용) |
| `ko-choice` | 선택지 | Noto Sans KR Regular 10pt, 왼쪽 정렬, 왼쪽 들여쓰기 14pt |
| `ko-footnote` | 각주 | Noto Sans KR Regular 8pt, 줄간격 11pt |
| `ko-quote` | 인용문 | Noto Sans KR Regular 9.5pt, 좌우 들여쓰기 14pt (이탤릭 미사용 — 한글 이탤릭 글리프 없음) |
| `ko-caption` | 캡션 | Noto Sans KR Regular 8.5pt, 중앙 정렬 |
| `ko-explain` | 해설 | Noto Sans KR Regular 10pt, 왼쪽 들여쓰기 6pt, 위/아래 8pt |
| `ko-vocab` | 어휘 | Noto Sans KR Regular 10pt, 줄간격 14pt |

### 스타일 계층 (basedOn 체인)

```
ko-body (기준)
├── ko-heading1 → ko-heading2 → ko-heading3
├── ko-passage, ko-choice, ko-footnote, ko-quote, ko-caption
└── 템플릿 특화 스타일
```

- `nextStyle`: 제목 다음은 `ko-body`, 선택지 다음은 `ko-choice` (연속 입력 편의)

## 글자 스타일

| 이름 | 용도 | 속성 |
|---|---|---|
| `ko-em` | 강조 | Bold (한글 이탤릭 대신) |
| `ko-underline` | 밑줄 | 밑줄 장식 |
| `ko-latin` | 영문 | 영문 전용 (현재는 동일 폰트, Phase 3에서 분리) |
| `ko-sup` | 위첨자 | 70% 크기 + 기준선 올림 |
| `ko-answer` | 정답 | Bold |

## 한글 조판 규칙

- `koreanCharBreaks`: ON — 단어 단위 줄바꿈 (KLREQ)
- 첫줄 들여쓰기: 1em (한글 출판 관행)
- 본문 줄간격: 160% (10pt/16pt)
- 한글 이탤릭 미사용 — Noto Sans KR에 한글 이탤릭 글리프 없음

## 알려진 제한 (DesignCraft v0.5.0)

- **양쪽 정렬 + 한글 문장부호**: 일부 줄에서 자간이 넓어질 수 있음.
  인용부호/괄호 등 문장부호가 많은 줄에서 금칙 처리와 저스티피케이션이
  상호작용하여 줄바꿈 위치가 최적이지 않을 수 있음.
  → GUI에서 수동으로 줄바꿈 조정 가능. 엔진 수정은 범위 외.
- **모지쿠미/루비/켄텐**: 미지원 (Phase 3)
- **지문 박스 테두리**: `ko-passage`는 글자 크기로만 구분.
  박스 테두리는 GUI에서 프레임에 직접 적용.
