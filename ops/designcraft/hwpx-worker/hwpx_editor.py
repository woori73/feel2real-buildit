"""HWPX 편집 코어: python-hwpx 래퍼.

DesignCraft HWPX Worker의 편집 엔진. HWP/HWPX 열기, 표 셀 수정,
HWPX 저장, 무결성 검증을 제공한다.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from hwpx import HwpxDocument


@dataclass
class EditResult:
    ok: bool
    output_path: str = ""
    tables: int = 0
    paragraphs: int = 0
    merged_cells: int = 0
    images: int = 0
    applied_edits: int = 0
    errors: list = field(default_factory=list)


@dataclass
class DocInfo:
    tables: int = 0
    paragraphs: int = 0
    merged_cells: int = 0
    images: int = 0
    text_length: int = 0
    format: str = ""


def _count_merged_cells(doc: HwpxDocument) -> int:
    count = 0
    for tbl in doc.tables:
        for r in range(tbl.row_count):
            for c in range(tbl.column_count):
                try:
                    cell = tbl.cell(r, c)
                    # 병합 셀은 span 정보로 판단
                    addr = cell.address if hasattr(cell, "address") else None
                    if addr is not None:
                        count += 1
                except Exception:
                    pass
    return count


def _count_images(doc: HwpxDocument) -> int:
    """문서 내 이미지 수 (doc.media.images)."""
    try:
        images = doc.media.images
        if callable(images):
            images = images()
        return len(list(images))
    except Exception:
        return 0


def get_info(input_path: str | Path) -> DocInfo:
    """문서 정보 조회."""
    p = Path(input_path)
    doc = HwpxDocument.open(str(p))
    tables = list(doc.tables)
    return DocInfo(
        tables=len(tables),
        paragraphs=len(doc.paragraphs),
        merged_cells=_count_merged_cells(doc),
        images=_count_images(doc),
        text_length=len(doc.text.plain()),
        format=p.suffix.lstrip(".").lower(),
    )


def _set_cell_text_safe(cell, text: str) -> None:
    """셀 텍스트를 안전하게 설정한다.

    python-hwpx의 cell.set_text()는 다단락 셀에서 문서 구조를
    깨뜨려 텍스트 손실을 일으킨다. 대신 단락을 직접 조작한다:
    첫 단락만 남기고 텍스트를 설정, 나머지는 제거.
    """
    paras = cell.paragraphs
    if not paras:
        # 빈 셀: 단락 추가
        cell.add_paragraph(text)
        return
    # 첫 단락 제외 모두 제거
    for p in paras[1:]:
        try:
            p.remove()
        except Exception:
            pass
    # 첫 단락에 텍스트 설정
    cell.paragraphs[0].text = text


def _find_merge_origin(tbl, row: int, col: int) -> tuple[int, int]:
    """병합 셀의 origin(좌상단) 셀 좌표를 찾는다.

    python-hwpx에서 병합 영역 내 모든 셀은 동일한 span을 반환한다.
    origin은 해당 span을 가진 가장 좌상단의 셀이다.
    """
    try:
        span = tbl.cell(row, col).span
    except Exception:
        return (row, col)
    if span == (1, 1):
        return (row, col)
    # 위쪽으로 스캔
    r = row
    while r > 0:
        try:
            if tbl.cell(r - 1, col).span != span:
                break
            r -= 1
        except Exception:
            break
    # 왼쪽으로 스캔
    c = col
    while c > 0:
        try:
            if tbl.cell(r, c - 1).span != span:
                break
            c -= 1
        except Exception:
            break
    return (r, c)


def apply_edits(
    input_path: str | Path,
    edits: list[dict],
    output_path: str | Path,
) -> EditResult:
    """표 셀 수정 후 HWPX로 저장.

    edits: [{"table": int, "row": int, "col": int, "text": str}, ...]
    """
    result = EditResult(ok=False)
    try:
        doc = HwpxDocument.open(str(input_path))
    except Exception as e:
        result.errors.append(f"입력 파일 열기 실패: {e}")
        return result

    tables = list(doc.tables)
    result.tables = len(tables)
    result.paragraphs = len(doc.paragraphs)

    for i, edit in enumerate(edits):
        try:
            t_idx = int(edit["table"])
            row = int(edit["row"])
            col = int(edit["col"])
            text = str(edit["text"])
        except (KeyError, ValueError, TypeError) as e:
            result.errors.append(f"edits[{i}] 형식 오류: {e}")
            continue

        if not (0 <= t_idx < len(tables)):
            result.errors.append(
                f"edits[{i}] 표 인덱스 범위 초과: {t_idx} (전체 {len(tables)}개)"
            )
            continue

        tbl = tables[t_idx]
        if not (0 <= row < tbl.row_count and 0 <= col < tbl.column_count):
            result.errors.append(
                f"edits[{i}] 셀 범위 초과: ({row}, {col}) "
                f"(표 {t_idx}: {tbl.row_count}x{tbl.column_count})"
            )
            continue

        try:
            # 병합 셀이면 origin 셀에 수정 (병합 영역 전체에 반영)
            origin_row, origin_col = _find_merge_origin(tbl, row, col)
            _set_cell_text_safe(tbl.cell(origin_row, origin_col), text)
            result.applied_edits += 1
        except Exception as e:
            result.errors.append(f"edits[{i}] 셀 수정 실패: {e}")

    if result.errors:
        return result

    try:
        out = Path(output_path)
        out.parent.mkdir(parents=True, exist_ok=True)
        doc.save_to_path(str(out))
        result.output_path = str(out)
    except Exception as e:
        result.errors.append(f"저장 실패: {e}")
        return result

    # 저장 후 재오픈 검증
    try:
        verify = HwpxDocument.open(str(out))
        report = verify.validate()
        issues = getattr(report, "issues", ())
        if issues:
            result.errors.append(f"무결성 검증 이슈: {issues}")
            return result
        result.merged_cells = _count_merged_cells(verify)
        result.images = _count_images(verify)
        # 표/단락 수 보존 확인
        if len(list(verify.tables)) != result.tables:
            result.errors.append("표 개수 변경 감지")
            return result
        if len(verify.paragraphs) != result.paragraphs:
            result.errors.append("단락 개수 변경 감지")
            return result
    except Exception as e:
        result.errors.append(f"검증 실패: {e}")
        return result

    result.ok = True
    return result


def load_edits(edits_path: str | Path) -> list[dict]:
    data = json.loads(Path(edits_path).read_text(encoding="utf-8"))
    if isinstance(data, dict) and "edits" in data:
        return data["edits"]
    if isinstance(data, list):
        return data
    raise ValueError("edits.json 형식 오류: {'edits': [...]} 또는 [...] 필요")
