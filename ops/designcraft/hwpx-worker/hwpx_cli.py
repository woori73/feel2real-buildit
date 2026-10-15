#!/usr/bin/env python3
"""HWPX Worker CLI.

DesignCraft HWPX 통합의 독립 Python Worker 진입점.
Spring Boot(HwpxCliExecutor)가 프로세스로 호출한다.

종료 코드: 0=성공, 1=입력 오류, 2=편집 실패, 3=검증 실패.
표준출력: JSON 결과.
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from hwpx_editor import apply_edits, get_info, load_edits  # noqa: E402


def cmd_info(args: argparse.Namespace) -> int:
    try:
        info = get_info(args.input)
    except Exception as e:
        print(json.dumps({"ok": False, "error": f"정보 조회 실패: {e}"}))
        return 1
    print(json.dumps({"ok": True, **asdict(info)}, ensure_ascii=False))
    return 0


def cmd_edit(args: argparse.Namespace) -> int:
    in_path = Path(args.input)
    if not in_path.exists():
        print(json.dumps({"ok": False, "error": f"입력 파일 없음: {in_path}"}))
        return 1
    if in_path.suffix.lower() not in (".hwp", ".hwpx"):
        print(json.dumps({"ok": False, "error": f"지원하지 않는 형식: {in_path.suffix}"}))
        return 1

    try:
        edits = load_edits(args.edits)
    except Exception as e:
        print(json.dumps({"ok": False, "error": f"edits.json 오류: {e}"}))
        return 1

    result = apply_edits(in_path, edits, args.output)
    out = asdict(result)
    print(json.dumps(out, ensure_ascii=False))

    if not result.ok:
        # 편집은 됐으나 검증 실패 vs 편집 자체 실패 구분
        if result.output_path and result.applied_edits > 0:
            return 3
        return 2
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    try:
        info = get_info(args.input)
    except Exception as e:
        print(json.dumps({"ok": False, "error": f"열기 실패: {e}"}))
        return 1

    from hwpx import HwpxDocument

    try:
        doc = HwpxDocument.open(args.input)
        report = doc.validate()
        issues = list(getattr(report, "issues", ()))
    except Exception as e:
        print(json.dumps({"ok": False, "error": f"검증 실패: {e}"}))
        return 3

    if issues:
        print(json.dumps({"ok": False, "issues": issues}, ensure_ascii=False))
        return 3
    print(json.dumps({"ok": True, **asdict(info)}, ensure_ascii=False))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="HWPX Worker CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    p_info = sub.add_parser("info", help="문서 정보 조회")
    p_info.add_argument("--in", dest="input", required=True)
    p_info.set_defaults(func=cmd_info)

    p_edit = sub.add_parser("edit", help="표 셀 수정 후 HWPX 저장")
    p_edit.add_argument("--in", dest="input", required=True)
    p_edit.add_argument("--edits", required=True, help="edits.json 경로")
    p_edit.add_argument("--out", dest="output", required=True)
    p_edit.set_defaults(func=cmd_edit)

    p_val = sub.add_parser("validate", help="HWPX 무결성 검증")
    p_val.add_argument("--in", dest="input", required=True)
    p_val.set_defaults(func=cmd_validate)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
