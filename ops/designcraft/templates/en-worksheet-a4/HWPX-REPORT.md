# HWPX 출력 — 현 구조 가능 범위·한계 보고 (2026-10-18)

## 현 구조

- `HwpxJobController`: `POST /api/hwpx/jobs` (multipart: file + title + edits JSON)
- Python Worker (`hwpx_editor.py`): 기존 HWPX 열기 → 표 셀 수정 → HWPX 저장
- edits 형식: `[{table, row, col, text}]`

## 가능한 범위 (현재 구조로)

| 항목 | 가능 여부 |
|---|---|
| 기존 HWPX 파일의 표 셀 텍스트 교체 | ✅ 가능 |
| 미리 제작된 학습지 HWPX 템플릿에 지문·어휘·해설 채우기 | ✅ 가능 (템플릿 필요) |
| DesignCraft → HWPX 직접 변환 | ❌ 불가 |
| HWPX 신규 생성 (빈 문서에서 작성) | ❌ 불가 (Worker에 생성 기능 없음) |
| 표·문단·섹션 추가/삭제 | ❌ 불가 (셀 수정만) |
| 가변 길이 콘텐츠 (지문 길이에 따른 표 행 자동 증가) | ❌ 불가 (고정 구조) |

## 한계

1. **템플릿 파일 필요**: 학습지용 HWPX 템플릿(.hwpx)을 별도로 제작해야 함.
   현재 저장소에 HWPX 템플릿 파일 없음.
2. **고정 레이아웃**: 지문 길이나 어휘 개수에 따라 레이아웃이 자동 조정되지 않음.
   템플릿의 표 행 수를 초과하면 내용이 잘림.
3. **수식·색상**: 셀 텍스트만 교체 가능. DesignCraft의 색상 문자 스타일은
   HWPX에 반영되지 않음.

## 결론

P1 범위에서는 HWPX를 **보조 출력**으로만 제공한다:
- 사용자가 HWPX 템플릿을 업로드하면 Worker가 셀에 내용을 채움
- DesignCraft PDF가 주 출력, HWPX는 템플릿 기반 셀 채우기로 한정

**직접 HWPX 내보내기(DesignCraft→HWPX 변환 엔진)가 필요하면 별도 승인을 요청합니다.**
