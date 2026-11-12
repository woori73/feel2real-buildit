#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""하랑랑 스페셜(menuId 9) 운영자 15건 보존 레코드를 build_cafe_4boards_raw.py의 --dump 형태 변환한다.

변환 이유: 4개 게시판 파이프라인의 canonical RAW 생성자는 dump 레코드 계약
(author.memberGrade 객체, writtenAt 문자열, comments 원본 스토어 형태)을 기대한다.
수집 사실(2f2a2d6d 브라우저 Vue store에서 보존한 값)은 그대로 옮기고, 판정·요약은 추가하지 않는다.
"""
from __future__ import annotations

import datetime as dt
import json
import pathlib
import sys

KST = dt.timezone(dt.timedelta(hours=9))
COLLECTED_AT = "2026-10-04"


def main() -> int:
    pkg = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                       pathlib.Path(__file__).resolve().parent.parent
                       / "cafe-4boards-20261004")
    src = json.loads((pkg / "f2r_menu9_operator15.json").read_text(encoding="utf-8"))
    out = []
    for r in src["records"]:
        iso = dt.datetime.fromtimestamp(r["writeDateMs"] / 1000, KST)
        images = r.get("images") or []
        links = sorted(set(r.get("links") or []))
        text = (r.get("bodyText") or "").strip()
        content_form = ("LINK_PREVIEW" if r.get("bodyTextKind") == "LINK_PREVIEW"
                        else "IMAGE_ONLY" if not text else "TEXT")
        out.append({
            "articleId": r["articleId"],
            "menuId": r["menuId"],
            "title": r["title"],
            "author": {"nickname": r["author"]["nickname"],
                       "memberGrade": {"value": r["author"]["memberLevel"],
                                       "name": r["author"]["memberGrade"]}},
            "writtenAt": iso.strftime("%y.%m.%d. %H:%M"),
            "viewCount": r["readCount"],
            "commentCount": r["commentCount"],
            "sourceUrl": r["sourceUrl"],
            "bodyNormalized": text,
            "bodyRaw": [text] if text else [],
            "images": images,
            "links": links,
            "attachments": images,
            "contentForm": content_form,
            "bodyHtmlLength": r.get("bodyHtmlLength"),
            "operatorRuling": src["userRuling"],
            "comments": [
                {"id": c.get("commentId"),
                 "nickname": {"nick": c.get("author")} if c.get("author") else None,
                 "memberLevelName": c.get("memberGrade"),
                 "writtenAt": c.get("createdAtMs"),
                 "content": c.get("text"),
                 "refId": c.get("parentCommentId") or c.get("commentId"),
                 "isArticleWriter": False,
                 "isDeleted": False}
                for c in (r.get("comments") or [])
            ],
            "parseWarnings": [],
            "imageRequired": content_form == "IMAGE_ONLY",
            "collectedAt": COLLECTED_AT,
        })
    dst = pkg / "f2r_menu9_operator15_as_dump.json"
    dst.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print("CONVERTED=%d -> %s" % (len(out), dst))
    forms = {}
    for o in out:
        forms[o["contentForm"]] = forms.get(o["contentForm"], 0) + 1
    print("BY_FORM " + " ".join("%s=%d" % kv for kv in sorted(forms.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
