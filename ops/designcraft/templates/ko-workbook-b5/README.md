# B5 문제집 템플릿

- **판형**: 182 × 257 mm (515.91 × 728.50 pt)
- **규격**: JIS B5
- **용도**: 문제집, 모의고사용
- **여백**: 위 18mm / 아래 18mm / 안쪽(제본) 20mm / 바깥쪽 16mm
- **블리드**: 3mm (전 방향)
- **대향 페이지**: ON (제본용)
- **특화**: OMR 답안란 영역, 시간 표시, 1단/2단 전환 가능

## 파일 구성

| 파일 | 설명 |
|---|---|
| `template.designcraft` | 템플릿 원본 (정본) |
| `recipe.md` | 재생성 레시피 (CLI 명령) |
| `styles.md` | 스타일 가이드 |
| `sample-manuscript.md` | 샘플 원고 (Markdown) |
| `sample.pdf` | 샘플 출력 PDF |
| `Document Fonts/` | Noto Sans KR Regular/Bold + OFL-1.1.txt |

## 사용법

1. `template.designcraft`를 복사하여 작업본 생성
2. `Document Fonts/` 폴더를 작업본과 같은 디렉토리에 유지
3. DesignCraft GUI 또는 CLI로 열기
4. 원고 배치: `file.place {"path": "원고.docx", "autoflow": true}`
5. 목차 생성: `toc.generate {"styles": ["ko-heading1", "ko-heading2", "ko-heading3"]}`
6. PDF 출력: `file.exportPdf {"marks": {"crop": true, "bleed": true, "pageInfo": true}}`

## 주의사항

- PDF/X-4 및 CMYK는 M-3 검증 전까지 인쇄 적합으로 표시하지 않음
- v0.5.0 양쪽 정렬 + 한글 문장부호 조합 시 일부 줄에 자간이 넓어질 수 있음 (엔진 제한, styles.md 참조)
- `ko-em`은 볼드 사용 (한글 이탤릭 글리프 없음)

## 제한 사항 (v0.5.0)

### EPUB 출력
- **본문이 비어 있는 문제**: EPUB 구조(content.opf, nav.xhtml 등)는 정상 생성되나
  본문(content.xhtml)이 비어 있음. DesignCraft v0.5.0의 제한 사항.
- **정상 EPUB 제작 기능으로 안내하지 않음.**
- PDF 출력은 정상 동작하므로 인쇄·배포용은 PDF 사용.
- EPUB 본문 누락 원인은 별도 조사 중.
