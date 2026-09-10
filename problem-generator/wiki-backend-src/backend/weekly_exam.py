"""Weekly Exam review workflow.

The live exam context stays in exam_material_sets/Digital Twin tables. A weekly set stores
only its three deliverables, immutable source references/hashes, and an immutable context
snapshot. Both HTTP and MCP registration call register_review(); agents can only create
REVIEW records. Human-only transitions are checked server-side.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
from datetime import datetime
from typing import Callable, Literal, Optional

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

import workspace as wiki_workspace

router = APIRouter(prefix='/api/weekly-exams', tags=['weekly-exams'])
_get_db: Optional[Callable] = None
_context_reader: Optional[Callable] = None
_html_sanitizer: Optional[Callable[[str], str]] = None

STATUSES = ('REVIEW', 'CHANGES_REQUESTED', 'REJECTED', 'APPROVED', 'FINAL', 'PUBLISHED')
SET_TYPES = ('40', '70', '40A', '40B')
ARTIFACTS = ('student-paper', 'answer-explanation', 'production-verification')


def configure(get_db: Callable, context_reader: Callable, html_sanitizer: Callable[[str], str]) -> None:
    global _get_db, _context_reader, _html_sanitizer
    _get_db, _context_reader, _html_sanitizer = get_db, context_reader, html_sanitizer


def init_schema(conn) -> None:
    conn.execute("""CREATE TABLE IF NOT EXISTS weekly_exam_sets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        school TEXT NOT NULL, grade TEXT NOT NULL, year INTEGER NOT NULL,
        semester TEXT NOT NULL, exam TEXT NOT NULL, round INTEGER NOT NULL,
        set_type TEXT NOT NULL CHECK (set_type IN ('40','70','40A','40B')),
        question_count INTEGER NOT NULL,
        student_paper_name TEXT NOT NULL, student_paper_html TEXT NOT NULL, student_paper_hash TEXT NOT NULL,
        answer_explanation_name TEXT NOT NULL, answer_explanation_html TEXT NOT NULL, answer_explanation_hash TEXT NOT NULL,
        verification_name TEXT NOT NULL, verification_md TEXT NOT NULL, verification_hash TEXT NOT NULL,
        source_material_ids TEXT NOT NULL DEFAULT '[]',
        context_snapshot TEXT NOT NULL, context_hash TEXT NOT NULL,
        version INTEGER NOT NULL DEFAULT 1,
        status TEXT NOT NULL DEFAULT 'REVIEW'
          CHECK (status IN ('REVIEW','CHANGES_REQUESTED','REJECTED','APPROVED','FINAL','PUBLISHED')),
        created_by TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL, reviewed_by TEXT NOT NULL DEFAULT '',
        reviewed_at TEXT NOT NULL DEFAULT '', published_by TEXT NOT NULL DEFAULT '',
        published_at TEXT NOT NULL DEFAULT ''
    )""")
    conn.execute("""CREATE UNIQUE INDEX IF NOT EXISTS uk_weekly_exam_version
        ON weekly_exam_sets(school,grade,year,semester,exam,round,set_type,version)""")
    conn.execute("""CREATE INDEX IF NOT EXISTS idx_weekly_exam_context
        ON weekly_exam_sets(school,grade,year,semester,exam,round,set_type,status)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS weekly_exam_audit (
        id INTEGER PRIMARY KEY AUTOINCREMENT, weekly_exam_id INTEGER NOT NULL,
        action TEXT NOT NULL, from_status TEXT NOT NULL DEFAULT '', to_status TEXT NOT NULL DEFAULT '',
        actor TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL,
        FOREIGN KEY (weekly_exam_id) REFERENCES weekly_exam_sets(id)
    )""")


class WeeklyExamReviewRequest(BaseModel):
    school: str
    grade: str
    year: int
    semester: str
    examType: str
    round: int = Field(ge=1)
    setType: Literal['40', '70', '40A', '40B']
    questionCount: int = Field(ge=1, le=200)
    studentPaper: str = Field(min_length=1)
    answerExplanation: str = Field(min_length=1)
    productionVerification: str = Field(min_length=1)
    studentPaperName: str = 'student-paper.html'
    answerExplanationName: str = 'answer-explanation.html'
    productionVerificationName: str = 'production-verification.md'
    sourceMaterialIds: list[int] = Field(default_factory=list)
    contextStatus: Literal['PARTIAL', 'LIVING', 'COMPLETE'] = 'LIVING'


class TransitionRequest(BaseModel):
    action: Literal['approve', 'request_changes', 'reject', 'finalize', 'publish']
    note: str = ''


def _db():
    if _get_db is None:
        raise RuntimeError('weekly exam service is not configured')
    return _get_db()


def _sha(text: str) -> str:
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def _actor() -> str:
    identity = wiki_workspace.current_identity()
    return identity.login_id if identity else 'mcp-agent'


def _is_admin() -> bool:
    identity = wiki_workspace.current_identity()
    return bool(identity and wiki_workspace.is_workspace_admin(identity))


def _clean_html(value: str) -> str:
    cleaned = _html_sanitizer(value) if _html_sanitizer else value
    if not cleaned.strip():
        raise HTTPException(status_code=400, detail='HTML 본문이 비어 있습니다.')
    return cleaned


def _source_refs(context: dict, requested_ids: list[int]) -> dict:
    groups = ('past_exams', 'school_prints', 'ai_work_logs', 'student_errors')
    allowed = {int(item['id']): {'id': int(item['id']), 'type': item.get('material_type') or item.get('type', '')}
               for key in groups for item in context.get(key, []) if item.get('id') is not None}
    missing = sorted(set(requested_ids) - set(allowed))
    if missing:
        raise HTTPException(status_code=400, detail=f'현재 시험 Context에 없는 source material ID입니다: {missing}')
    return {'requested': [allowed[item_id] for item_id in requested_ids], 'available': list(allowed.values())}


def _snapshot(payload: WeeklyExamReviewRequest) -> tuple[str, str]:
    if _context_reader is None:
        raise RuntimeError('weekly exam context reader is not configured')
    current = _context_reader(payload.school, payload.grade, payload.year, payload.semester, payload.examType)
    refs = _source_refs(current, payload.sourceMaterialIds)
    twin = current.get('digital_twin') or {}
    snapshot = {
        'schemaVersion': 'weekly-context-snapshot-v1',
        'generatedAt': datetime.now().isoformat(),
        'contextStatus': payload.contextStatus,
        'examContext': current.get('exam_context', {}),
        'digitalTwin': {'id': twin.get('id'), 'version': twin.get('version')},
        'sourceMaterials': refs,
        'counts': {key: len(current.get(key, [])) for key in
                   ('past_exams', 'school_prints', 'ai_work_logs', 'student_errors',
                    'class_insights', 'generated_questions', 'generated_tests')},
        'contextBudget': current.get('context_budget', {}),
    }
    text = json.dumps(snapshot, ensure_ascii=False, sort_keys=True)
    return text, _sha(text)


def _row(row) -> dict:
    item = dict(row)
    item['source_material_ids'] = json.loads(item.pop('source_material_ids') or '[]')
    item['context_snapshot'] = json.loads(item['context_snapshot'])
    for key in ('student_paper_html', 'answer_explanation_html', 'verification_md'):
        item.pop(key, None)
    return item


def register_review(payload: WeeklyExamReviewRequest, *, actor: Optional[str] = None) -> dict:
    """The only agent-write contract. Status is server-owned and always REVIEW."""
    student_html = _clean_html(payload.studentPaper)
    answer_html = _clean_html(payload.answerExplanation)
    snapshot, snapshot_hash = _snapshot(payload)
    now, creator = datetime.now().isoformat(), actor or _actor()
    with _db() as conn:
        active = conn.execute("""SELECT id,status FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND round=? AND set_type=?
            AND status <> 'REJECTED' ORDER BY version DESC LIMIT 1""",
          (payload.school.strip(), payload.grade.strip(), payload.year, payload.semester.strip(),
           payload.examType.strip(), payload.round, payload.setType)).fetchone()
        if active:
            raise HTTPException(status_code=409, detail=f"동일 회차·세트가 이미 {active['status']} 상태로 존재합니다.")
        version = conn.execute("""SELECT COALESCE(MAX(version),0)+1 v FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND round=? AND set_type=?""",
          (payload.school.strip(), payload.grade.strip(), payload.year, payload.semester.strip(),
           payload.examType.strip(), payload.round, payload.setType)).fetchone()['v']
        cursor = conn.execute("""INSERT INTO weekly_exam_sets
          (school,grade,year,semester,exam,round,set_type,question_count,
           student_paper_name,student_paper_html,student_paper_hash,
           answer_explanation_name,answer_explanation_html,answer_explanation_hash,
           verification_name,verification_md,verification_hash,source_material_ids,
           context_snapshot,context_hash,version,status,created_by,created_at,updated_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'REVIEW',?,?,?)""",
          (payload.school.strip(), payload.grade.strip(), payload.year, payload.semester.strip(),
           payload.examType.strip(), payload.round, payload.setType, payload.questionCount,
           payload.studentPaperName[:255], student_html, _sha(student_html),
           payload.answerExplanationName[:255], answer_html, _sha(answer_html),
           payload.productionVerificationName[:255], payload.productionVerification, _sha(payload.productionVerification),
           json.dumps(payload.sourceMaterialIds), snapshot, snapshot_hash, version, creator, now, now))
        exam_id = cursor.lastrowid
        conn.execute("""INSERT INTO weekly_exam_audit
          (weekly_exam_id,action,to_status,actor,created_at) VALUES (?,'REGISTER_REVIEW','REVIEW',?,?)""",
          (exam_id, creator, now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())


@router.post('/review', status_code=201)
def create_review(payload: WeeklyExamReviewRequest):
    return register_review(payload)


@router.get('')
def list_weekly_exams(school: str, grade: str, year: int, semester: str, exam: str):
    with _db() as conn:
        rows = conn.execute("""SELECT * FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=?
          ORDER BY round DESC,set_type,version DESC""",
          (school, grade, year, semester, exam)).fetchall()
        return [_row(row) for row in rows]


@router.get('/published')
def list_published_weekly_exams(school: str, grade: str, year: int, semester: str, exam: str):
    with _db() as conn:
        rows = conn.execute("""SELECT * FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND status='PUBLISHED'
          ORDER BY round DESC,set_type""", (school, grade, year, semester, exam)).fetchall()
        return [_row(row) for row in rows]


@router.get('/{exam_id}/artifacts/{kind}')
def view_artifact(exam_id: int, kind: Literal['student-paper', 'answer-explanation', 'production-verification']):
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
    if kind == 'student-paper':
        body, title = row['student_paper_html'], row['student_paper_name']
    elif kind == 'answer-explanation':
        body, title = row['answer_explanation_html'], row['answer_explanation_name']
    else:
        escaped = html.escape(row['verification_md'])
        escaped = re.sub(r'^### (.+)$', r'<h3>\1</h3>', escaped, flags=re.M)
        escaped = re.sub(r'^## (.+)$', r'<h2>\1</h2>', escaped, flags=re.M)
        escaped = re.sub(r'^# (.+)$', r'<h1>\1</h1>', escaped, flags=re.M)
        escaped = re.sub(r'^[-*] (.+)$', r'<li>\1</li>', escaped, flags=re.M)
        body, title = f'<article class="markdown-view">{escaped.replace(chr(10), "<br>")}</article>', row['verification_name']
    document = f"""<!doctype html><html><head><meta charset="utf-8"><title>{html.escape(title)}</title>
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:">
      <style>body{{font-family:Arial,'Malgun Gothic',sans-serif;margin:24px;line-height:1.65;overflow-wrap:anywhere}}
      table{{border-collapse:collapse;max-width:100%}}td,th{{border:1px solid #bbb;padding:6px}}@media(max-width:600px){{body{{margin:12px}}}}</style>
      </head><body>{body}</body></html>"""
    return HTMLResponse(document, headers={'Content-Security-Policy': "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:"})


@router.get('/{exam_id}/context-snapshot')
def view_context_snapshot(exam_id: int):
    with _db() as conn:
        row = conn.execute('SELECT context_snapshot,context_hash FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='Context Snapshot을 찾지 못했습니다.')
        return {'contextHash': row['context_hash'], 'snapshot': json.loads(row['context_snapshot'])}


@router.post('/{exam_id}/transition')
def transition_weekly_exam(exam_id: int, payload: TransitionRequest, request: Request):
    if not _is_admin():
        raise HTTPException(status_code=403, detail='사용자 관리자 승인 권한이 필요합니다.')
    transitions = {
        ('REVIEW', 'approve'): 'APPROVED',
        ('REVIEW', 'request_changes'): 'CHANGES_REQUESTED',
        ('REVIEW', 'reject'): 'REJECTED',
        ('CHANGES_REQUESTED', 'reject'): 'REJECTED',
        ('APPROVED', 'finalize'): 'FINAL',
        ('FINAL', 'publish'): 'PUBLISHED',
    }
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
        target = transitions.get((row['status'], payload.action))
        if not target:
            raise HTTPException(status_code=409, detail=f"{row['status']} 상태에서 {payload.action} 전이는 허용되지 않습니다.")
        now, actor = datetime.now().isoformat(), _actor()
        reviewed_by, reviewed_at = (actor, now) if target in ('APPROVED','CHANGES_REQUESTED','REJECTED') else (row['reviewed_by'], row['reviewed_at'])
        published_by, published_at = (actor, now) if target == 'PUBLISHED' else (row['published_by'], row['published_at'])
        conn.execute("""UPDATE weekly_exam_sets SET status=?,updated_at=?,reviewed_by=?,reviewed_at=?,
          published_by=?,published_at=? WHERE id=?""",
          (target, now, reviewed_by, reviewed_at, published_by, published_at, exam_id))
        conn.execute("""INSERT INTO weekly_exam_audit
          (weekly_exam_id,action,from_status,to_status,actor,note,created_at) VALUES (?,?,?,?,?,?,?)""",
          (exam_id, payload.action.upper(), row['status'], target, actor, payload.note[:1000], now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())
