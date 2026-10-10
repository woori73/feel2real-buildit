# A4 교재 재생성 레시피

- **생성일**: 2026-10-10
- **도구**: DesignCraft CLI 0.5.0 (`designcraft-cli run`)
- **판형**: 210 × 297 mm (ISO 216 A4)

## 재생성 방법

아래 CLI 명령을 순서대로 실행하면 `template.designcraft`가 재생성된다.
(스타일 속성 스키마: 글꼴·크기 → `chars`, 정렬·들여쓰기 → `para`)

```text
# 한글 교재 템플릿 (A4) 생성 레시피
# A4 210×297mm (ISO 216) 영어 교재·학습지용
# 생성일: 2026-10-10, DesignCraft CLI 0.5.0
# 실행: designcraft-cli run --cmd 'file.new={"width": 595.28, "height": 841.89, "pages": 4, "facingPages": true, "margins": {"top": 56.69, "bottom": 56.69, "inside": 62.36, "outside": 51.02}, "bleed": 8.5, "title": "한글 교재 템플릿 (A4)"}' --cmd 'style.paragraph.create={"name": "ko-body", "chars": {"fontFamily": "Noto Sans KR", "size": 10, "leading": {"kind": "points", "value": 16}}, "para": {"align": "fullyJustified", "firstLineIndent": 10, "koreanCharBreaks": true}}' --cmd 'style.paragraph.create={"name": "ko-heading1", "chars": {"fontFamily": "Noto Sans KR", "size": 18, "fontStyle": "Bold", "leading": {"kind": "points", "value": 22}}, "para": {"align": "left", "spaceBefore": 18, "spaceAfter": 10}, "basedOn": "ko-body", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-heading2", "chars": {"fontFamily": "Noto Sans KR", "size": 14, "fontStyle": "Bold", "leading": {"kind": "points", "value": 18}}, "para": {"align": "left", "spaceBefore": 14, "spaceAfter": 8}, "basedOn": "ko-heading1", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-heading3", "chars": {"fontFamily": "Noto Sans KR", "size": 12, "fontStyle": "Bold", "leading": {"kind": "points", "value": 15}}, "para": {"align": "left", "spaceBefore": 12, "spaceAfter": 6}, "basedOn": "ko-heading2", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-passage", "chars": {"fontFamily": "Noto Sans KR", "size": 9.5, "leading": {"kind": "points", "value": 15}}, "para": {"align": "fullyJustified", "firstLineIndent": 10, "koreanCharBreaks": true, "spaceBefore": 6, "spaceAfter": 6}, "basedOn": "ko-body", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-choice", "chars": {"fontFamily": "Noto Sans KR", "size": 10, "leading": {"kind": "points", "value": 16}}, "para": {"align": "left", "leftIndent": 14, "koreanCharBreaks": true}, "basedOn": "ko-body", "nextStyle": "ko-choice"}' --cmd 'style.paragraph.create={"name": "ko-footnote", "chars": {"fontFamily": "Noto Sans KR", "size": 8, "leading": {"kind": "points", "value": 11}}, "para": {"align": "left", "koreanCharBreaks": true}, "basedOn": "ko-body", "nextStyle": "ko-footnote"}' --cmd 'style.paragraph.create={"name": "ko-quote", "chars": {"fontFamily": "Noto Sans KR", "size": 9.5, "leading": {"kind": "points", "value": 15}}, "para": {"align": "fullyJustified", "leftIndent": 14, "rightIndent": 14, "koreanCharBreaks": true, "spaceBefore": 6, "spaceAfter": 6}, "basedOn": "ko-body", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-caption", "chars": {"fontFamily": "Noto Sans KR", "size": 8.5, "leading": {"kind": "points", "value": 12}}, "para": {"align": "center", "spaceBefore": 4, "spaceAfter": 8}, "basedOn": "ko-body", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-explain", "chars": {"fontFamily": "Noto Sans KR", "size": 10, "leading": {"kind": "points", "value": 16}}, "para": {"align": "fullyJustified", "firstLineIndent": 10, "koreanCharBreaks": true, "leftIndent": 6, "spaceBefore": 8, "spaceAfter": 8}, "basedOn": "ko-body", "nextStyle": "ko-body"}' --cmd 'style.paragraph.create={"name": "ko-vocab", "chars": {"fontFamily": "Noto Sans KR", "size": 10, "leading": {"kind": "points", "value": 14}}, "para": {"align": "left", "koreanCharBreaks": true}, "basedOn": "ko-body", "nextStyle": "ko-vocab"}' --cmd 'style.character.create={"name": "ko-em", "chars": {"fontFamily": "Noto Sans KR", "fontStyle": "Bold"}}' --cmd 'style.character.create={"name": "ko-underline", "chars": {"fontFamily": "Noto Sans KR", "underline": true}}' --cmd 'style.character.create={"name": "ko-latin", "chars": {"fontFamily": "Noto Sans KR", "size": 10}}' --cmd 'style.character.create={"name": "ko-sup", "chars": {"fontFamily": "Noto Sans KR", "size": 7, "baselineShift": 3}}' --cmd 'style.character.create={"name": "ko-answer", "chars": {"fontFamily": "Noto Sans KR", "fontStyle": "Bold"}}' --cmd 'layout.parents.new={"prefix": "A", "name": "A-본문마스터"}' --cmd 'layout.parents.new={"prefix": "B", "name": "B-장시작마스터"}' --cmd 'file.save={"path": "/home/hatch/workspace/m2work/templates/ko-textbook-a4/template.designcraft"}'

# file.new
{
 "width": 595.28,
```

## 전체 명령 목록

`create-template.txt`에 전체 JSON 명령이 기록되어 있다.
