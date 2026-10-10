# 46판 단행본 템플릿

- **판형**: 188 × 257 mm (532.91 × 728.50 pt)
- **규격**: 46판(사륙판) — 한국 출판 관행. B5(182×257mm, JIS)와 구분.
- **용도**: 일반 도서, 단행본
- **여백**: 위 22mm / 아래 22mm / 안쪽(제본) 24mm / 바깥쪽 20mm
- **블리드**: 3mm (전 방향)
- **대향 페이지**: ON (제본용)
- **특화**: 1단 조판, 장/절 구분, TOC 자동 생성 태깅, 인용문

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
