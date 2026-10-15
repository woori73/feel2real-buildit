# HWPX Python Worker 검증 보고서

> 작성일: 2026-10-15 · 환경: 격리 venv (`~/workspace/hwpx-validation/venv/`)
> 라이브러리: python-hwpx 6.8.0 · 시험 파일: 모두의창업 사업계획서 HWP

## 1. 구현 산출물

`~/workspace/hwpx-validation/worker/`:
- `hwpx_cli.py` — CLI 진입점 (`info` / `edit` / `validate`)
- `hwpx_editor.py` — 편집 엔진 (python-hwpx 래퍼)
- `requirements.txt` — python-hwpx==6.8.0
- `Dockerfile` — python:3.12-slim 기반
- `DESIGN.md` — 설계 문서

## 2. 기능 검증 (PASS)

| 명령 | 결과 |
|------|------|
| `info --in 사업계획서_원본.hwp` | 표 9개·단락 28개·병합셀 46개·이미지 2개·텍스트 3,556자 |
| `edit` (일반 셀 2개 수정) | exit 0, 수정 반영, 의도적 교체 외 손실 0 |
| `edit` (병합 셀 수정) | exit 0, origin 셀에 적용, 구조 보존 |
| `validate` | exit 0, `issues=()` |

## 3. 보존 검증 (PASS)

- 표 9개 → 9개 (100%)
- 단락 28개 → 28개 (100%)
- 병합 셀 46개 → 46개 (100%)
- 이미지 2개 → 2개 (100%, BinData 확인)

## 4. 발견된 이슈와 해결

**이슈**: python-hwpx의 `cell.set_text()` / `cell.text` setter가
다단락 병합 셀에서 문서 구조를 깨뜨려 244단어 손실 발생.

**원인**: 병합 셀(47개 단락)에 `set_text` 호출 시 내부 XML 재구성 오류.

**해결**: `_set_cell_text_safe()` 구현 —
첫 단락만 남기고 `paragraph.text`로 직접 설정, 나머지 단락 제거.
병합 셀은 `_find_merge_origin()`으로 origin 셀을 찾아 수정.

**검증**: 일반 셀 수정 시 의도적 교체(4단어) 외 손실 0건.

## 5. 미검증 항목

- 한컴오피스에서 출력 HWPX 열기 (Windows 환경 필요)
- Spring Boot 통합 (`/api/hwpx/*`, HwpxWorker) — PR #687 병합 후 진행
- Docker 이미지 빌드·실행

## 6. 다음 단계

1. PR #687 병합 대기 (Claude 진행 중)
2. 최신 main 반영 후 별도 브랜치에 Spring 통합 코드 작성
3. PR 생성 → CI PASS → 병합 승인 요청
