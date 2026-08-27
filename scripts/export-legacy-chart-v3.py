#!/usr/bin/env python3
"""Validate and export the legacy V3 Chart database as a private re-research queue.

This command never writes to either SQLite or BuildIt. It deliberately omits phone,
email, and street-address fields and preserves the V3 score only as historical data.
"""

import argparse
import json
import sqlite3
from pathlib import Path

EXPECTED_STATUS_COUNTS = {"VERIFIED": 43, "REJECTED": 79, "HOLD": 3}
EXPECTED_EDUCATORS = 43


def connect_read_only(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{path.resolve().as_posix()}?mode=ro", uri=True)


def parse_json_array(value: str | None) -> list:
    if not value:
        return []
    parsed = json.loads(value)
    if not isinstance(parsed, list):
        raise ValueError("source_urls_json must be a JSON array")
    return parsed


def validate(connection: sqlite3.Connection) -> dict:
    statuses = dict(connection.execute(
        "SELECT status, COUNT(*) FROM v3_candidates GROUP BY status"
    ).fetchall())
    educator_count = connection.execute("SELECT COUNT(*) FROM v3_educators").fetchone()[0]
    linked_verified = connection.execute(
        "SELECT COUNT(*) FROM v3_candidates WHERE status='VERIFIED' AND educator_id IS NOT NULL"
    ).fetchone()[0]
    if statuses != EXPECTED_STATUS_COUNTS:
        raise ValueError(f"unexpected v3 candidate status counts: {statuses}")
    if educator_count != EXPECTED_EDUCATORS or linked_verified != EXPECTED_EDUCATORS:
        raise ValueError(
            f"expected {EXPECTED_EDUCATORS} educators and linked verified candidates; "
            f"got educators={educator_count}, linked={linked_verified}"
        )
    return {
        "candidateCount": sum(statuses.values()),
        "candidateStatusCounts": statuses,
        "educatorCount": educator_count,
        "linkedVerifiedCount": linked_verified,
    }


def export_queue(connection: sqlite3.Connection) -> dict:
    summary = validate(connection)
    rows = connection.execute("""
        SELECT e.id, e.dedup_key, e.name, e.role, e.academy, e.region,
               e.specialty, e.discovered_at, e.verified_at, e.last_updated_at,
               e.build_score, e.score_breakdown_json, e.activity_summary,
               e.improvement_suggestion, e.discovery_source, e.homepage,
               e.naver_blog, e.instagram, e.youtube, e.other_url,
               e.education_career, e.target_audience, e.specialty_detail,
               e.signature_class, e.teaching_style, e.education_philosophy,
               e.education_content, e.notable_feature, c.source_urls_json
          FROM v3_educators e
          JOIN v3_candidates c ON c.educator_id = e.id AND c.status = 'VERIFIED'
         ORDER BY e.id
    """).fetchall()
    columns = [item[0] for item in connection.execute("""
        SELECT e.id AS legacy_educator_id, e.dedup_key, e.name, e.role, e.academy, e.region,
               e.specialty, e.discovered_at, e.verified_at, e.last_updated_at,
               e.build_score AS legacy_score, e.score_breakdown_json AS legacy_score_breakdown_json,
               e.activity_summary, e.improvement_suggestion, e.discovery_source, e.homepage,
               e.naver_blog, e.instagram, e.youtube, e.other_url,
               e.education_career, e.target_audience, e.specialty_detail,
               e.signature_class, e.teaching_style, e.education_philosophy,
               e.education_content, e.notable_feature, c.source_urls_json
          FROM v3_educators e JOIN v3_candidates c ON 1=0
    """).description]
    queue = []
    for row in rows:
        item = dict(zip(columns, row))
        item["source_urls"] = parse_json_array(item.pop("source_urls_json"))
        item["legacy_score_breakdown"] = item.pop("legacy_score_breakdown_json")
        item["queue_status"] = "PENDING_CURRENT_RESEARCH"
        item["legacy_score_is_current_score"] = False
        item["auto_approved"] = False
        item["published"] = False
        queue.append(item)
    return {
        "schemaVersion": "buildit-chart-legacy-v3-research-queue-v1",
        "purpose": "historical input for current-year public-web re-research only",
        "privacy": "phone, email, and street address are intentionally excluded",
        "scorePolicy": "legacy_score is historical and must never be used as current BuildIt Score",
        "summary": summary,
        "queue": queue,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path)
    parser.add_argument("--output", type=Path, help="Write sanitized JSON; stdout when omitted")
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    with connect_read_only(args.database) as connection:
        payload = validate(connection) if args.validate_only else export_queue(connection)
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    else:
        print(rendered)


if __name__ == "__main__":
    main()
