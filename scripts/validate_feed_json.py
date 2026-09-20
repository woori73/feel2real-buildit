#!/usr/bin/env python3
"""
NewStart Feed JSON 동일성 검증 스크립트

Canonical: data/newstart/feeds/latest_links.json
Deploy Copy: src/main/resources/static/data/newstart/feeds/latest_links.json

두 파일이 바이트 단위로 동일해야 배포 안전.
"""

import json
import sys
from pathlib import Path

CANONICAL = Path("data/newstart/feeds/latest_links.json")
DEPLOY_COPY = Path("src/main/resources/static/data/newstart/feeds/latest_links.json")

def load_json(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        text = f.read()
        return text, json.loads(text)

def main():
    if not CANONICAL.exists():
        print("[FAIL] Canonical not found: " + str(CANONICAL))
        return 1
    if not DEPLOY_COPY.exists():
        print("[FAIL] Deploy copy not found: " + str(DEPLOY_COPY))
        return 1

    canon_text, canon_data = load_json(CANONICAL)
    deploy_text, deploy_data = load_json(DEPLOY_COPY)

    # 1. 바이트 단위 동일성
    if canon_text == deploy_text:
        print("[OK] Byte-for-byte identical")
    else:
        print("[FAIL] Content differs between canonical and deploy copy")
        return 1

    # 2. 구조 검증
    required_keys = {"version", "updatedAt", "description", "sources", "items"}
    if not required_keys.issubset(canon_data.keys()):
        print("[FAIL] Missing required keys: " + str(required_keys - set(canon_data.keys())))
        return 1

    # 3. items 검증
    items = canon_data.get("items", [])
    if not items:
        print("[FAIL] No items in feed")
        return 1

    seen = set()
    for i, item in enumerate(items):
        req = ["id", "sourceType", "sourceName", "sourceArticleId", "title", "publishedDate", "url", "summary", "topicTags", "addedAt", "featured", "official"]
        for k in req:
            if k not in item:
                print("[FAIL] Item " + str(i) + " missing field: " + k)
                return 1
        if item["id"] in seen:
            print("[FAIL] Duplicate id: " + item["id"])
            return 1
        seen.add(item["id"])
        # URL 유효성 (기본 체크)
        if not item["url"].startswith("http"):
            print("[FAIL] Item " + str(i) + " invalid URL: " + item["url"])
            return 1

    print("[OK] All validations passed (" + str(len(items)) + " items)")
    return 0

if __name__ == "__main__":
    sys.exit(main())