"""Authenticated Wiki workspace resolution and idempotent SQLite ownership migration."""

from __future__ import annotations

import contextvars
import hashlib
import hmac
import os
import sqlite3
import time
import uuid
from dataclasses import dataclass
from pathlib import Path


LEGACY_WORKSPACE_ID = "legacy"
# The shared "legacy" Wiki DB is owned by the real ADMIN principal plus one designated legacy
# TUTOR account. ADMIN is judged only by the signed identity's role (the Spring authenticated
# principal, carried HMAC-signed in X-BuildIt-Role) — never by login_id: in Production the admin
# account's login_id is "admin-f2r" (V2 migration), not the dev-seed's literal "admin" this set
# used to hardcode, so the real admin fell through to an empty personal workspace. "modu" is one
# specific tutor account, not a role, so it stays matched by login_id.
LEGACY_TUTOR_LOGIN_IDS = frozenset({"modu"})
IDENTITY_HEADER_PREFIX = "x-buildit-"
WORKSPACE_OVERRIDE_HEADER = "x-buildit-wiki-workspace"
OWNED_TABLES = (
    "digital_twin_profiles", "generated_questions", "exam_papers", "exam_draft",
    "students", "student_wrong_notes", "educator_insights", "exam_material_sets",
    "final_deliverables", "csat_question_sources", "csat_questions",
    "csat_wrong_answers", "csat_assessments", "csat_assessment_items",
    "csat_question_usage", "source_materials", "generated_tests", "ai_ab_evaluations",
    "textbook_materials", "textbook_questions", "weekly_exam_sets", "weekly_exam_audit",
)


@dataclass(frozen=True)
class WikiIdentity:
    user_id: str
    login_id: str
    role: str


@dataclass(frozen=True)
class WikiWorkspace:
    workspace_id: str
    database_path: str
    legacy: bool


_workspace_context: contextvars.ContextVar[WikiWorkspace | None] = contextvars.ContextVar(
    "wiki_workspace", default=None
)
_identity_context: contextvars.ContextVar[WikiIdentity | None] = contextvars.ContextVar(
    "wiki_identity", default=None
)


def identity_secret() -> str:
    # Server-to-server HMAC key shared with the Spring proxy (BuildItWikiProxyController
    # signs with ``app.wiki.identity-secret`` -> ``app.jwt.secret`` -> ``JWT_SECRET``).
    # This FastAPI service runs as its own systemd unit and does not inherit Spring's
    # EnvironmentFile, so the deploy step feeds it the same value as ``WIKI_IDENTITY_SECRET``
    # (derived from Spring's ``JWT_SECRET`` at deploy time). ``JWT_SECRET`` stays accepted so
    # a single env line works if both services ever share one file. An empty result is fatal
    # in ``verify_identity`` — the request is rejected rather than trusted.
    return os.getenv("WIKI_IDENTITY_SECRET") or os.getenv("JWT_SECRET", "")


def signature_payload(identity: WikiIdentity, timestamp: str) -> bytes:
    return f"{identity.user_id}\n{identity.login_id}\n{identity.role}\n{timestamp}".encode()


def verify_identity(headers, *, now: int | None = None) -> WikiIdentity:
    identity = WikiIdentity(
        str(headers.get("x-buildit-user-id", "")).strip(),
        str(headers.get("x-buildit-login-id", "")).strip(),
        str(headers.get("x-buildit-role", "")).strip().upper(),
    )
    timestamp = str(headers.get("x-buildit-timestamp", "")).strip()
    supplied = str(headers.get("x-buildit-signature", "")).strip().lower()
    secret = identity_secret()
    if not secret or not all((identity.user_id, identity.login_id, identity.role, timestamp, supplied)):
        raise ValueError("missing trusted Wiki identity")
    try:
        issued_at = int(timestamp)
    except ValueError as exc:
        raise ValueError("invalid Wiki identity timestamp") from exc
    if abs((now or int(time.time())) - issued_at) > 300:
        raise ValueError("expired Wiki identity")
    expected = hmac.new(secret.encode(), signature_payload(identity, timestamp), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, supplied):
        raise ValueError("invalid Wiki identity signature")
    return identity


def catalog_path(legacy_database_path: str) -> str:
    return os.getenv("BUILDIT_WIKI_WORKSPACE_CATALOG", str(legacy_database_path) + ".workspaces")


def _init_catalog(conn: sqlite3.Connection, legacy_database_path: str) -> None:
    conn.execute("""CREATE TABLE IF NOT EXISTS wiki_workspaces (
        workspace_id TEXT PRIMARY KEY,
        workspace_key TEXT NOT NULL UNIQUE,
        database_path TEXT NOT NULL UNIQUE,
        is_legacy INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )""")
    conn.execute("""CREATE TABLE IF NOT EXISTS wiki_workspace_members (
        user_id TEXT PRIMARY KEY,
        login_id TEXT NOT NULL,
        workspace_id TEXT NOT NULL,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (workspace_id) REFERENCES wiki_workspaces(workspace_id)
    )""")
    conn.execute("""INSERT OR IGNORE INTO wiki_workspaces
        (workspace_id, workspace_key, database_path, is_legacy)
        VALUES (?, 'legacy', ?, 1)""", (LEGACY_WORKSPACE_ID, legacy_database_path))


def is_legacy_member(identity: WikiIdentity) -> bool:
    """Who shares the single legacy Wiki DB: the ADMIN principal, or the designated legacy tutor."""
    return identity.role == "ADMIN" or identity.login_id.casefold() in LEGACY_TUTOR_LOGIN_IDS


def resolve_workspace(identity: WikiIdentity, legacy_database_path: str) -> WikiWorkspace:
    legacy_database_path = str(legacy_database_path)
    catalog = catalog_path(legacy_database_path)
    Path(catalog).parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(catalog) as conn:
        conn.row_factory = sqlite3.Row
        _init_catalog(conn, legacy_database_path)
        login = identity.login_id.casefold()
        if is_legacy_member(identity):
            # Re-point membership to legacy on every request (upsert) so an identity that was
            # briefly mis-routed to a personal workspace heals itself without touching any
            # workspace's data DB.
            conn.execute("""INSERT INTO wiki_workspace_members(user_id, login_id, workspace_id)
                VALUES (?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET
                login_id=excluded.login_id, workspace_id=excluded.workspace_id""",
                (identity.user_id, login, LEGACY_WORKSPACE_ID))
            return WikiWorkspace(LEGACY_WORKSPACE_ID, legacy_database_path, True)

        member = conn.execute(
            "SELECT w.* FROM wiki_workspace_members m JOIN wiki_workspaces w USING(workspace_id) WHERE m.user_id=?",
            (identity.user_id,),
        ).fetchone()
        if member:
            return WikiWorkspace(member["workspace_id"], member["database_path"], bool(member["is_legacy"]))

        workspace_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"buildit-wiki-user:{identity.user_id}"))
        base = Path(legacy_database_path)
        database_path = str(base.with_name(f"{base.stem}.workspace-{workspace_id}{base.suffix}"))
        conn.execute("""INSERT OR IGNORE INTO wiki_workspaces
            (workspace_id, workspace_key, database_path, is_legacy) VALUES (?, ?, ?, 0)""",
            (workspace_id, f"user:{identity.user_id}", database_path))
        conn.execute("""INSERT INTO wiki_workspace_members(user_id, login_id, workspace_id)
            VALUES (?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET
            login_id=excluded.login_id, workspace_id=excluded.workspace_id""",
            (identity.user_id, identity.login_id, workspace_id))
        return WikiWorkspace(workspace_id, database_path, False)


def is_workspace_admin(identity: WikiIdentity) -> bool:
    """Super-admin = the signed Spring principal whose role is ADMIN (login_id-agnostic —
    Production admin login_id is "admin-f2r", not the literal "admin")."""
    return identity.role == "ADMIN"


def get_workspace(workspace_id: str, legacy_database_path: str) -> WikiWorkspace | None:
    catalog = catalog_path(str(legacy_database_path))
    Path(catalog).parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(catalog) as conn:
        conn.row_factory = sqlite3.Row
        _init_catalog(conn, str(legacy_database_path))
        row = conn.execute(
            "SELECT workspace_id, database_path, is_legacy FROM wiki_workspaces WHERE workspace_id=?",
            (workspace_id,),
        ).fetchone()
    if not row:
        return None
    return WikiWorkspace(row["workspace_id"], row["database_path"], bool(row["is_legacy"]))


def list_workspaces(legacy_database_path: str) -> list[dict[str, object]]:
    """Return catalog metadata only; database paths are deliberately never exposed."""
    catalog = catalog_path(str(legacy_database_path))
    Path(catalog).parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(catalog) as conn:
        conn.row_factory = sqlite3.Row
        _init_catalog(conn, str(legacy_database_path))
        rows = conn.execute("""SELECT workspace_id, is_legacy, created_at
            FROM wiki_workspaces ORDER BY is_legacy DESC, created_at""").fetchall()
        result = []
        for row in rows:
            members = [dict(member) for member in conn.execute(
                "SELECT user_id, login_id FROM wiki_workspace_members WHERE workspace_id=? ORDER BY lower(login_id)",
                (row["workspace_id"],),
            ).fetchall()]
            owner = members[0] if not row["is_legacy"] and len(members) == 1 else {}
            result.append({
                "workspace_id": row["workspace_id"],
                "legacy": bool(row["is_legacy"]),
                "created_at": row["created_at"],
                "user_id": owner.get("user_id"),
                "login_id": owner.get("login_id"),
                "members": members,
            })
    return result


def set_current_workspace(workspace: WikiWorkspace):
    return _workspace_context.set(workspace)


def reset_current_workspace(token) -> None:
    _workspace_context.reset(token)


def current_workspace() -> WikiWorkspace | None:
    return _workspace_context.get()


def set_current_identity(identity: WikiIdentity):
    return _identity_context.set(identity)


def reset_current_identity(token) -> None:
    _identity_context.reset(token)


def current_identity() -> WikiIdentity | None:
    return _identity_context.get()


def legacy_workspace(legacy_database_path: str) -> WikiWorkspace:
    """Explicit workspace contract for API-key authenticated external MCP clients."""
    return WikiWorkspace(LEGACY_WORKSPACE_ID, str(legacy_database_path), True)


def migrate_owned_tables(conn: sqlite3.Connection, workspace_id: str) -> dict[str, tuple[int, int]]:
    """Add/backfill ownership without deleting or duplicating rows; safe to rerun."""
    result: dict[str, tuple[int, int]] = {}
    tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    for table in OWNED_TABLES:
        if table not in tables:
            continue
        before = conn.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
        columns = {row[1] for row in conn.execute(f'PRAGMA table_info("{table}")')}
        if "workspace_id" not in columns:
            conn.execute(f'ALTER TABLE "{table}" ADD COLUMN workspace_id TEXT')
        conn.execute(f'UPDATE "{table}" SET workspace_id=? WHERE workspace_id IS NULL OR workspace_id=""', (workspace_id,))
        conn.execute(f'CREATE INDEX IF NOT EXISTS "idx_{table}_workspace" ON "{table}"(workspace_id)')
        trigger = f"trg_{table}_workspace_insert"
        safe_workspace_id = workspace_id.replace("'", "''")
        conn.execute(f'''CREATE TRIGGER IF NOT EXISTS "{trigger}"
            AFTER INSERT ON "{table}" WHEN NEW.workspace_id IS NULL OR NEW.workspace_id=''
            BEGIN UPDATE "{table}" SET workspace_id='{safe_workspace_id}' WHERE rowid=NEW.rowid; END''')
        after = conn.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
        if before != after:
            raise RuntimeError(f"workspace migration changed row count for {table}: {before} -> {after}")
        result[table] = (before, after)
    return result
