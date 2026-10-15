# A4 교재 템플릿

- **판형**: 210 × 297 mm (595.28 × 841.89 pt)
- **규격**: ISO 216 A4
- **용도**: 영어 교재, 학습지 제본용
- **여백**: 위 20mm / 아래 20mm / 안쪽(제본) 22mm / 바깥쪽 18mm
- **블리드**: 3mm (전 방향)
- **대향 페이지**: ON (제본용)
- **특화**: 2단 조판 옵션, 지문 박스(ko-passage), 문제 번호, 해설 구분

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

### EPUB 출력 (정상 동작)

**빈 템플릿의 EPUB**
- `template.designcraft`는 스타일·마스터만 포함된 빈 템플릿 (스토리 0개)
- 빈 문서에서 EPUB을 생성하면 본문(content.xhtml)이 비어 있는 것이 **정상**
- DesignCraft는 빈 스토리(`story.text.trim().is_empty()`)를 의도적으로 제외함
  (`crates/epub/src/lib.rs:502`, storytold/designcraft)

**스토리가 있는 문서의 EPUB**
- 실제 원고가 배치된 문서에서는 한글 EPUB이 정상 출력됨
- **실측** (2026-10-10): DOCX 배치(`file.place`, `autoflow: true`) 후 28개 문단 출력 성공 —
  한글 제목 "제1장 영어 독해의 기초", 한영 혼합 본문, 스타일 클래스(`ko-heading1`, `ko-body`) 모두 포함
- PDF와 EPUB 모두 정상 동작하므로 용도에 맞게 선택

**`file.exportEpub`은 엔진 결함이 아님.** 2026-10-10 조사에서 스토리 있는 문서 3건(직접 입력·`file.place`·DOCX 배치)
모두 본문 정상 출력을 실측으로 확인. 상류 이슈 등록 불필요.

### 알려진 이슈: 샘플 생성 스크립트 (수정하지 않음)

**원인**: 샘플 생성 과정에서 `file.place`로 스토리를 배치한 뒤 `file.save`로 저장했으나,
이어서 `shutil.copy(src_dc, dst_dc)`로 빈 원본 템플릿을 덮어써 스토리가 리셋됨
(`make_samples.py`).

**수정 방안** (향후 작업):
- `file.place` 후 저장된 파일을 직접 샘플로 사용 (리셋하지 않음)
- 또는 리셋 전에 스토리 존재 여부를 확인하는 가드 추가

**이번 작업에서는 수정하지 않음.** 스크립트 파일은 변경하지 않습니다.
