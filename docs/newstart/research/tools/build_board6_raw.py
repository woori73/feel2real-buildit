#!/usr/bin/env python3
"""board6(부실우려) RAW 보존: article.cafe gw/v4 JSON 수집 결과 -> per-article canonical RAW.

입력: work-output/batches/api_a*.json, api_p01.json, api_comments_full.json (브라우저 fetch dump)
출력: docs/newstart/research/cafe-qa-phase1-b6/articles/article_{id}.json
      docs/newstart/research/cafe-qa-phase1-b6/INDEX.json

commentCount != 수집 댓글 수인 글은 v3 comments(size=200) dump로 덮어쓴다.
"""
import json, glob, os, datetime

SRC = "work-output/batches"
OUT = "docs/newstart/research/cafe-qa-phase1-b6/articles"
CAFE_CLUB, CAFE_KEY, BOARD_ID, BOARD_NAME = 31312978, "newhopero", 6, "부실우려"
COLLECTED_AT = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")


def load(path):
    raw = open(path, encoding="utf-8").read()
    d = json.loads(raw)
    if isinstance(d, str):
        d = json.loads(d)
    return d


def main():
    recs = {}
    for p in sorted(glob.glob(os.path.join(SRC, "api_a*.json"))) + [os.path.join(SRC, "api_p01.json")]:
        if not os.path.exists(p):
            continue
        for r in load(p)["out"]:
            if not r.get("err"):
                recs[str(r["articleId"])] = r

    full = os.path.join(SRC, "api_comments_full.json")
    overlaid = []
    if os.path.exists(full):
        for r in load(full)["out"]:
            aid2 = str(r["articleId"])
            if aid2 in recs and r["n"] >= recs[aid2]["apiComments"]:
                recs[aid2]["comments"] = r["comments"]
                recs[aid2]["apiComments"] = r["n"]
                overlaid.append(aid2)

    os.makedirs(OUT, exist_ok=True)
    index = []
    for aid, r in sorted(recs.items(), key=lambda kv: int(kv[0])):
        ms = r.get("writeDateMs") or r.get("writeDate")
        iso = r.get("writeDate")
        if not iso and isinstance(ms, (int, float)):
            iso = datetime.datetime.fromtimestamp(ms / 1000, datetime.timezone.utc).isoformat(timespec="seconds")
        text = r.get("text") or ""
        body_raw = text.split("\n")
        op_comments = sum(1 for c in r["comments"] if c.get("level") == "카페매니저")
        status = "COMPLETE" if text.strip() else "EMPTY_BODY"
        if r["commentCount"] != len(r["comments"]):
            status = "COMMENTS_PARTIAL"
        art = {
            "articleId": int(aid),
            "boardId": BOARD_ID,
            "boardName": BOARD_NAME,
            "cafeClubId": CAFE_CLUB,
            "cafeKey": CAFE_KEY,
            "title": r.get("subject"),
            "author": {"nickname": r.get("writerNick"), "memberGrade": r.get("writerLevel")},
            "createdAt": iso,
            "createdAtMs": r.get("writeDateMs"),
            "viewCount": r.get("readCount"),
            "commentCount": r.get("commentCount"),
            "sourceUrl": "https://cafe.naver.com/newhopero/%s" % aid,
            "sourceType": "CAFE_ARTICLE",
            "topicTags": ["부실우려", "board:6"],
            "bodyRaw": body_raw,
            "bodyNormalized": text,
            "bodyHtml": r.get("rawContentHtml"),
            "images": r.get("images") or [],
            "attachments": r.get("attach") or [],
            "comments": [
                {
                    "commentId": c.get("id"),
                    "parentCommentId": c.get("refId") if c.get("isRef") else None,
                    "author": c.get("nick"),
                    "memberGrade": c.get("level"),
                    "createdAt": c.get("date"),
                    "createdAtMs": c.get("dateMs"),
                    "text": c.get("content"),
                    "isAuthor": bool(c.get("isArticleWriter")),
                    "isOperator": c.get("level") == "카페매니저",
                    "isDeleted": bool(c.get("isDeleted")),
                    "sourceType": "CAFE_COMMENT",
                }
                for c in r["comments"]
            ],
            "operatorCommentCount": op_comments,
            "imageRequired": bool(r.get("images")),
            "collectedAt": COLLECTED_AT,
            "extractionMethod": "CAFE_GW_V4_JSON",
            "collectionStatus": status,
            "parseWarnings": []
            if status == "COMPLETE"
            else ["%s: commentCount=%s collected=%s" % (status, r["commentCount"], len(r["comments"]))],
        }
        with open(os.path.join(OUT, "article_%s.json" % aid), "w", encoding="utf-8") as f:
            json.dump(art, f, ensure_ascii=False, indent=1)
        index.append(
            {
                "articleId": int(aid),
                "title": art["title"],
                "createdAt": art["createdAt"],
                "bodyLen": len(text),
                "commentCount": len(art["comments"]),
                "operatorCommentCount": op_comments,
                "images": len(art["images"]),
                "collectionStatus": status,
            }
        )

    meta = {
        "board": {"clubid": CAFE_CLUB, "cafeKey": CAFE_KEY, "menuId": BOARD_ID, "menuName": BOARD_NAME},
        "generatedAt": COLLECTED_AT,
        "extractionMethod": "CAFE_GW_V4_JSON",
        "sourceIdList": "work-output/board6_ids_1.json (249 unique board-6 articleIds)",
        "total": len(index),
        "commentApiOverlay": sorted(overlaid, key=int),
        "statusCounts": {s: sum(1 for i in index if i["collectionStatus"] == s) for s in {i["collectionStatus"] for i in index}},
        "articles": index,
    }
    with open("docs/newstart/research/cafe-qa-phase1-b6/INDEX.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print("articles=%d overlay=%s status=%s" % (len(index), meta["commentApiOverlay"], meta["statusCounts"]))


if __name__ == "__main__":
    main()
