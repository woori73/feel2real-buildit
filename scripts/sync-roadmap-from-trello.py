#!/usr/bin/env python3
"""Sync docs/ROADMAP.md from Trello board state.

Default: print a comparison report only.
Use --apply to rewrite docs/ROADMAP.md from Trello Todo/In Progress plus preserved local sections.

Required env:
  TRELLO_KEY
  TRELLO_TOKEN
Optional env:
  TRELLO_BOARD_ID (default: 4ZmQjQHe)
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
ROADMAP = ROOT / "docs" / "ROADMAP.md"
DEFAULT_BOARD_ID = "4ZmQjQHe"
ENV_FILE = Path.home() / ".config" / "f2r" / "env"
IGNORED_LISTS = {"Trello Starter Guide"}
DONE_LISTS = {"Done", "Archived"}
ACTIVE_LISTS = {"Todo", "In Progress"}
TEST_TITLE_PATTERNS = [
    re.compile(r"테스트"),
    re.compile(r"trello[- ]?test", re.I),
    re.compile(r"trello .*확인", re.I),
    re.compile(r"라벨/Priority 자동 적용 확인"),
]


@dataclass(frozen=True)
class Card:
    name: str
    desc: str
    url: str
    list_name: str
    closed: bool

    @property
    def issue_no(self) -> int | None:
        m = re.search(r"#\s*(\d+)", self.name)
        return int(m.group(1)) if m else None

    @property
    def title(self) -> str:
        text = self.name.strip()
        # "image.png" card currently stores the real task in desc.
        if text.lower() == "image.png" and self.desc.strip():
            text = self.desc.strip().splitlines()[0]
        text = re.sub(r"^#\s*\d+\s*", "", text).strip()
        text = re.sub(r"^\[(FEAT|BUG|DONE)\]\s*", "", text, flags=re.I).strip()
        text = text.replace("\\#", "#")
        return text or self.name.strip()

    @property
    def is_test(self) -> bool:
        return any(p.search(self.name) for p in TEST_TITLE_PATTERNS)


def load_env_file() -> None:
    if not ENV_FILE.exists():
        return
    for raw in ENV_FILE.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or not line.startswith("export "):
            continue
        key_value = line.removeprefix("export ")
        if "=" not in key_value:
            continue
        key, value = key_value.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def fetch_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)


def fetch_trello_cards() -> list[Card]:
    load_env_file()
    key = os.environ.get("TRELLO_KEY")
    token = os.environ.get("TRELLO_TOKEN")
    board_id = os.environ.get("TRELLO_BOARD_ID", DEFAULT_BOARD_ID)
    if not key or not token:
        raise SystemExit(
            "Missing TRELLO_KEY/TRELLO_TOKEN. Put them in env or ~/.config/f2r/env."
        )

    query = urllib.parse.urlencode(
        {
            "cards": "all",
            "lists": "open",
            "card_fields": "name,desc,url,idList,closed,labels,dateLastActivity",
            "list_fields": "name",
            "key": key,
            "token": token,
        }
    )
    data = fetch_json(f"https://api.trello.com/1/boards/{board_id}?{query}")
    lists = {item["id"]: item["name"] for item in data.get("lists", [])}
    cards: list[Card] = []
    for item in data.get("cards", []):
        list_name = lists.get(item.get("idList"), "")
        if list_name in IGNORED_LISTS:
            continue
        cards.append(
            Card(
                name=item.get("name", ""),
                desc=item.get("desc", ""),
                url=item.get("url", ""),
                list_name=list_name,
                closed=bool(item.get("closed")),
            )
        )
    return cards


def gh_open_issues() -> dict[int, str]:
    try:
        out = subprocess.check_output(
            [
                "gh",
                "issue",
                "list",
                "--repo",
                "Juyudang/private_tutor_solution_back",
                "--state",
                "open",
                "--limit",
                "100",
                "--json",
                "number,title",
            ],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except Exception:
        return {}
    return {int(i["number"]): i["title"] for i in json.loads(out)}


def md_link(card: Card) -> str:
    issue = f" [#{card.issue_no}](https://github.com/Juyudang/private_tutor_solution_back/issues/{card.issue_no})," if card.issue_no else ""
    return f"- {card.title} ({issue} [Trello]({card.url}))".replace("(  ", "(").replace("( [", "([")


def numbered(cards: Iterable[Card]) -> list[str]:
    return [f"{idx}. {card.title} ([Trello]({card.url}))" for idx, card in enumerate(cards, 1)]


def extract_section(text: str, heading: str) -> str:
    m = re.search(rf"^## {re.escape(heading)}\n(?P<body>.*?)(?=\n---\n\n## |\Z)", text, re.S | re.M)
    return m.group("body").strip() if m else ""


def extract_refactor(old: str) -> list[str]:
    m = re.search(r"### 리팩토링\n\n(?P<body>.*?)(?=\n\n---|\Z)", old, re.S)
    if not m:
        return ["1. conversation/* 사이드바를 Thymeleaf fragment로 단일화. 현재 각 페이지 중복 마크업 (deep interview 권장)"]
    body = m.group("body").strip()
    return body.splitlines() if body else []


def extract_done_lines(old: str) -> list[str]:
    body = extract_section(old, "완료")
    return [line for line in body.splitlines() if line.strip().startswith("-")]


def build_roadmap(cards: list[Card], old: str) -> str:
    in_progress = [c for c in cards if c.list_name == "In Progress" and not c.closed]
    todo = [c for c in cards if c.list_name == "Todo" and not c.closed]
    completed = [c for c in cards if c.list_name in DONE_LISTS or c.closed]

    done_lines = extract_done_lines(old)
    seen = "\n".join(done_lines)
    for card in completed:
        if card.is_test or not card.issue_no:
            continue
        if f"#{card.issue_no}" in seen:
            continue
        done_lines.append(f"- {card.title} ([#{card.issue_no}](https://github.com/Juyudang/private_tutor_solution_back/issues/{card.issue_no}))")
        seen += f"\n#{card.issue_no}"

    refactor = extract_refactor(old)

    parts = [
        "# Roadmap",
        "",
        "> 생각나는 대로 자유롭게 작성. 작업 시작 전 Claude에게 읽히고 정리 후 deep interview로 스펙화.",
        "> 구현 완료된 항목은 MVP.md로 이동 또는 삭제.",
        "",
        "---",
        "",
        "## 진행 중 (dev 브랜치)",
        "",
        *(md_link(c) for c in in_progress),
        "",
        "---",
        "",
        "## 작업 예정",
        "",
        "### 기능",
        "",
        *numbered(todo),
        "",
        "### 리팩토링",
        "",
        *refactor,
        "",
        "---",
        "",
        "## 완료",
        "",
        *done_lines,
        "",
    ]
    return "\n".join(parts)


def report(cards: list[Card], open_issues: dict[int, str]) -> str:
    by_list: dict[str, list[Card]] = {}
    for card in cards:
        by_list.setdefault(card.list_name, []).append(card)

    lines = ["# Trello ↔ GitHub ↔ ROADMAP sync report", ""]
    for list_name in ["In Progress", "Todo", "Done", "Archived"]:
        items = by_list.get(list_name, [])
        if not items:
            continue
        lines.append(f"## {list_name}")
        for card in items:
            if list_name in ACTIVE_LISTS and card.closed:
                continue
            if list_name in DONE_LISTS and card.is_test:
                continue
            issue = f"#{card.issue_no}" if card.issue_no else "no issue"
            gh = ""
            if card.issue_no:
                gh = " / GitHub OPEN" if card.issue_no in open_issues else " / GitHub not open or unavailable"
            lines.append(f"- {issue}{gh}: {card.title}")
        lines.append("")

    trello_issue_nos = {c.issue_no for c in cards if c.issue_no}
    missing = sorted(set(open_issues) - trello_issue_nos)
    if missing:
        lines.append("## GitHub OPEN issues not found as Trello-numbered cards")
        for no in missing:
            lines.append(f"- #{no}: {open_issues[no]}")
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="rewrite docs/ROADMAP.md")
    parser.add_argument("--report", default="docs/temp/roadmap-sync-report.md", help="report output path")
    args = parser.parse_args()

    cards = fetch_trello_cards()
    open_issues = gh_open_issues()
    report_text = report(cards, open_issues)
    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report_text)
    print(report_text)
    print(f"Report written: {report_path.relative_to(ROOT)}")

    if args.apply:
        old = ROADMAP.read_text() if ROADMAP.exists() else ""
        ROADMAP.write_text(build_roadmap(cards, old))
        print(f"Updated: {ROADMAP.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
