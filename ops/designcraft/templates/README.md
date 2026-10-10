# DesignCraft 한글 출판 템플릿 3종

DesignCraft v0.5.0 기반 한글 출판용 템플릿.

| 템플릿 | 판형 | 규격 | 용도 |
|---|---|---|---|
| [ko-textbook-a4](ko-textbook-a4/) | 210 × 297 mm | ISO 216 A4 | 영어 교재, 학습지 제본용 |
| [ko-workbook-b5](ko-workbook-b5/) | 182 × 257 mm | JIS B5 | 문제집, 모의고사용 |
| [ko-book-46](ko-book-46/) | 188 × 257 mm | 46판(사륙판) — 한국 출판 관행 | 일반 도서, 단행본 |

> **판형 명칭**: 188×257mm는 B5(182×257mm, JIS)와 구분되는 **46판(사륙판)**.
> 한국 출판 관행상의 명칭을 사용.

## 공통 사양

- **기본 폰트**: Noto Sans KR Regular/Bold (Document Fonts, OFL 1.1)
- **한글 조판**: `korean_char_breaks` ON, 첫줄 들여쓰기 1em
- **마스터 페이지**: A(본문용) · B(장 시작용)
- **블리드**: 3mm, 대향 페이지 ON
- **문단 스타일**: 13종 (`ko-` 접두사), **글자 스타일**: 5종

## 파일 구성 (템플릿별)

| 파일 | 설명 |
|---|---|
| `template.designcraft` | 템플릿 원본 (정본) |
| `recipe.md` | 재생성 레시피 (CLI 명령) |
| `styles.md` | 스타일 가이드 |
| `sample-manuscript.md` | 샘플 원고 |
| `sample.pdf` | 샘플 출력 PDF |
| `README.md` | 템플릿 사용법 |
| `Document Fonts/` | Noto Sans KR + OFL-1.1.txt |

## 제한 사항 (v0.5.0)

### EPUB 출력
- **본문이 비어 있는 문제**: EPUB 구조(content.opf, nav.xhtml 등)는 정상 생성되나
  본문(content.xhtml)이 비어 있음. DesignCraft v0.5.0의 제한 사항.
- **정상 EPUB 제작 기능으로 안내하지 않음.**
- PDF 출력은 정상 동작하므로 인쇄·배포용은 PDF 사용.
- EPUB 본문 누락 원인은 별도 조사 중.

### 인쇄 (PDF/X-4 · CMYK)
- PDF/X-4 및 CMYK는 **M-3 검증 전까지 인쇄 적합으로 표시하지 않음.**
- 외부 검증을 통과하기 전까지 인쇄 적합 판정 금지.

### 조판 엔진
- v0.5.0 양쪽 정렬 + 한글 문장부호 조합 시 일부 줄에 자간이 넓어질 수 있음 (엔진 제한).
- 모지쿠미·루비·켄텐·레이아웃 그리드는 미구현 (Phase 3 장기 과제).
