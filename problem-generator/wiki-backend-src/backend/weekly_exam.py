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
from fastapi.responses import HTMLResponse, Response
from pydantic import BaseModel, Field
from urllib.parse import quote

import workspace as wiki_workspace

router = APIRouter(prefix='/api/weekly-exams', tags=['weekly-exams'])
_get_db: Optional[Callable] = None
_context_reader: Optional[Callable] = None
_html_sanitizer: Optional[Callable[[str], str]] = None

STATUSES = ('REVIEW', 'CHANGES_REQUESTED', 'REJECTED', 'APPROVED', 'FINAL', 'PUBLISHED')
# Storage stays backward compatible with the PR #270 CHECK constraint. Public/product state is
# deliberately only DRAFT/FINAL: legacy rows are interpreted without a destructive migration.
EFFECTIVE_STATUS = {
    'REVIEW': 'DRAFT', 'CHANGES_REQUESTED': 'DRAFT', 'REJECTED': 'DRAFT',
    'APPROVED': 'FINAL', 'FINAL': 'FINAL', 'PUBLISHED': 'FINAL',
}
SET_TYPES = ('STD',)
# 1단계 구조조정: 40/70/40A/40B 구분 제거. 신규 세트는 'STD' 단일 유형.
# 기존 DB의 구 값은 CHECK 제약에서 허용하여 조회·다운로드 호환 유지.
LEGACY_SET_TYPES = ('40', '70', '40A', '40B')
ALL_SET_TYPES = ('STD',) + LEGACY_SET_TYPES
ARTIFACTS = ('student-paper', 'answer-explanation', 'production-verification')
HTML_ARTIFACTS = ('student-paper', 'answer-explanation')

_SCRIPT_SUBTREE_RE = re.compile(r'<script\b[^>]*>.*?</script\s*>|<script\b[^>]*/>', re.IGNORECASE | re.DOTALL)


def configure(get_db: Callable, context_reader: Callable, html_sanitizer: Callable[[str], str]) -> None:
    # html_sanitizer는 과거 Weekly paper에도 적용되던 기출 본문 추출기다. Weekly artifact는
    # canonical 보존(_preserve_paper_html)이 계약이므로 더 이상 사용하지 않고 시그니처만 유지한다.
    global _get_db, _context_reader, _html_sanitizer
    _get_db, _context_reader, _html_sanitizer = get_db, context_reader, html_sanitizer



def _migrate_set_type_unified(conn) -> None:
    """기존 weekly_exam_sets 테이블의 CHECK 제약에 'STD'를 추가한다.

    SQLite는 CHECK 제약을 직접 변경할 수 없으므로 테이블을 재구축한다.
    기존 데이터(40/70/40A/40B)는 모두 보존된다.
    """
    row = conn.execute(
        "SELECT sql FROM sqlite_master WHERE type='table' AND name='weekly_exam_sets'"
    ).fetchone()
    if not row or "'STD'" in (row[0] or ""):
        return
    conn.execute("ALTER TABLE weekly_exam_sets RENAME TO weekly_exam_sets_legacy")
    # 새 스키마로 재생성 (init_schema의 정의를 재사용하지 않고 명시)
    conn.execute("""CREATE TABLE weekly_exam_sets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        school TEXT NOT NULL, grade TEXT NOT NULL, year INTEGER NOT NULL,
        semester TEXT NOT NULL, exam TEXT NOT NULL, round INTEGER NOT NULL,
        set_type TEXT NOT NULL CHECK (set_type IN ('STD','40','70','40A','40B')),
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
    conn.execute("INSERT INTO weekly_exam_sets SELECT * FROM weekly_exam_sets_legacy")
    conn.execute("DROP TABLE weekly_exam_sets_legacy")
    conn.execute("""CREATE UNIQUE INDEX IF NOT EXISTS uk_weekly_exam_version
        ON weekly_exam_sets(school,grade,year,semester,exam,round,set_type,version)""")
    conn.execute("""CREATE INDEX IF NOT EXISTS idx_weekly_exam_context
        ON weekly_exam_sets(school,grade,year,semester,exam,round,set_type,status)""")


def init_schema(conn) -> None:
    _migrate_set_type_unified(conn)
    conn.execute("""CREATE TABLE IF NOT EXISTS weekly_exam_sets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        school TEXT NOT NULL, grade TEXT NOT NULL, year INTEGER NOT NULL,
        semester TEXT NOT NULL, exam TEXT NOT NULL, round INTEGER NOT NULL,
        set_type TEXT NOT NULL CHECK (set_type IN ('STD','40','70','40A','40B')),
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
    setType: Literal['STD', '40', '70', '40A', '40B'] = 'STD'
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
    action: Literal['finalize', 'reopen']
    note: str = ''


class WeeklyExamArtifactsUpdate(BaseModel):
    studentPaper: Optional[str] = None
    answerExplanation: Optional[str] = None
    productionVerification: Optional[str] = None
    studentPaperName: str = ''
    answerExplanationName: str = ''
    productionVerificationName: str = ''
    sourceMaterialIds: Optional[list[int]] = None


class WeeklyExamMetadataUpdate(BaseModel):
    round: Optional[int] = Field(default=None, ge=1)
    setType: Optional[Literal['STD', '40', '70', '40A', '40B']] = None
    questionCount: Optional[int] = Field(default=None, ge=1, le=200)


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


def _preserve_paper_html(value: str) -> str:
    """Weekly Exam HTML canonical 보존. A4/print CSS·2단 class·문서 구조를 그대로 저장하고
    script subtree만 제거한다. 기출 본문 추출용 sanitizer(의미 본문만 남기고 style/class를
    버림)를 Weekly artifact에 적용하면 완성 시험지가 깨지므로(paper 훼손 regression) 여기서는
    사용하지 않는다. 저장된 바이트가 곧 보기/다운로드 정본이다."""
    cleaned = _SCRIPT_SUBTREE_RE.sub('', (value or '').strip())
    if not cleaned.strip():
        raise HTTPException(status_code=400, detail='HTML 본문이 비어 있습니다.')
    return cleaned


def _clean_html(value: str) -> str:
    return _preserve_paper_html(value)


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
        'previousFinals': current.get('weekly_exam_finals', [])[:10],
    }
    text = json.dumps(snapshot, ensure_ascii=False, sort_keys=True)
    return text, _sha(text)


def _row(row) -> dict:
    item = dict(row)
    item['source_material_ids'] = json.loads(item.pop('source_material_ids') or '[]')
    item['context_snapshot'] = json.loads(item['context_snapshot'])
    item['legacy_status'] = item['status']
    item['status'] = EFFECTIVE_STATUS.get(item['status'], 'DRAFT')
    for key in ('student_paper_html', 'answer_explanation_html', 'verification_md'):
        item.pop(key, None)
    return item


def _require_operator() -> None:
    """Private assets require a signed human identity. MCP calls services directly under API-key auth."""
    if wiki_workspace.current_identity() is None:
        raise HTTPException(status_code=401, detail='로그인이 필요한 운영자 자료입니다.')


def _public_row(row) -> dict:
    """Searchable projection. Never add identifiers, locators, source refs or private bodies here."""
    return {
        'school': row['school'], 'grade': row['grade'], 'year': row['year'],
        'semester': row['semester'], 'examType': row['exam'], 'round': row['round'],
        'setType': row['set_type'], 'questionCount': row['question_count'],
        'available': True, 'updatedAt': row['updated_at'],
    }


def register_draft(payload: WeeklyExamReviewRequest, *, actor: Optional[str] = None) -> dict:
    """The only agent-write contract. Physical REVIEW preserves legacy schema; effective state is DRAFT."""
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
          (weekly_exam_id,action,to_status,actor,created_at) VALUES (?,'CREATED','DRAFT',?,?)""",
          (exam_id, creator, now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())


# Compatibility alias: old clients keep working, but the effective state is DRAFT.
register_review = register_draft


def final_context_items(school: str, grade: str, year: int, semester: str, exam: str) -> list[dict]:
    """Private, bounded production history for Living Context and the next-round blueprint."""
    with _db() as conn:
        rows = conn.execute("""SELECT id,school,grade,year,semester,exam,round,set_type,
          question_count,version,status,verification_md,verification_hash,context_hash,updated_at
          FROM weekly_exam_sets WHERE school=? AND grade=? AND year=? AND semester=? AND exam=?
          AND status IN ('APPROVED','FINAL','PUBLISHED')
          ORDER BY round DESC,set_type,version DESC LIMIT 20""",
          (school, grade, year, semester, exam)).fetchall()
    return [{
        'id': row['id'], 'school': row['school'], 'grade': row['grade'], 'year': row['year'],
        'semester': row['semester'], 'exam': row['exam'], 'round': row['round'],
        'set_type': row['set_type'], 'question_count': row['question_count'],
        'version': row['version'], 'status': 'FINAL',
        'production_verification_md': row['verification_md'][:12000],
        'verification_hash': row['verification_hash'], 'context_hash': row['context_hash'],
        'updated_at': row['updated_at'],
    } for row in rows]


@router.post('/draft', status_code=201)
def create_draft(payload: WeeklyExamReviewRequest):
    _require_operator()
    return register_draft(payload)


@router.post('/review', status_code=201, deprecated=True)
def create_review_compat(payload: WeeklyExamReviewRequest):
    _require_operator()
    return register_draft(payload)


@router.get('')
def list_weekly_exams(school: str, grade: str, year: int, semester: str, exam: str):
    _require_operator()
    with _db() as conn:
        rows = conn.execute("""SELECT * FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=?
          ORDER BY round DESC,set_type,version DESC""",
          (school, grade, year, semester, exam)).fetchall()
        return [_row(row) for row in rows]


@router.get('/public-metadata')
@router.get('/published', deprecated=True)
def list_public_weekly_metadata(
    school: str = '', grade: str = '', year: Optional[int] = None,
    semester: str = '', exam: str = '',
):
    """Anonymous-safe projection. FINAL includes legacy APPROVED/FINAL/PUBLISHED; DRAFT is excluded."""
    clauses = ["status IN ('APPROVED','FINAL','PUBLISHED')"]
    params: list[object] = []
    for column, value in (
        ('school', school), ('grade', grade), ('year', year),
        ('semester', semester), ('exam', exam),
    ):
        if value not in ('', None):
            clauses.append(f'{column}=?')
            params.append(value)
    with _db() as conn:
        rows = conn.execute(
            'SELECT * FROM weekly_exam_sets WHERE ' + ' AND '.join(clauses)
            + ' ORDER BY updated_at DESC,round DESC,set_type', params).fetchall()
        return [_public_row(row) for row in rows]


@router.get('/{exam_id}/artifacts/{kind}')
def view_artifact(exam_id: int, kind: Literal['student-paper', 'answer-explanation', 'production-verification']):
    _require_operator()
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
    # HTML artifact는 저장된 canonical 원본을 그대로 렌더링한다. 별도 shell로 감싸거나
    # JSON으로 노출하지 않는다(final_deliverables /html 조회와 같은 contract).
    # 교체 직후 보기에서 과거 파일이 보이지 않도록 캐시를 저장하지 않는다.
    if kind == 'student-paper':
        return Response(row['student_paper_html'], media_type='text/html; charset=utf-8',
                        headers={'Cache-Control': 'no-store'})
    if kind == 'answer-explanation':
        return Response(row['answer_explanation_html'], media_type='text/html; charset=utf-8',
                        headers={'Cache-Control': 'no-store'})
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


_ARTIFACT_DOWNLOAD_LABELS = {'student-paper': '문제지', 'answer-explanation': '정답해설'}


def _download_filename(row, kind: str) -> tuple[str, str]:
    """다운로드 파일명. 한글 원본명은 RFC 5987 filename*로, 구형 클라이언트용으로 ASCII
    대체명을 함께 제공한다. 내용은 저장 canonical과 동일하며 파일명만 파생한다."""
    label = _ARTIFACT_DOWNLOAD_LABELS[kind]
    original = (f"{row['school']}_{row['grade']}_{row['year']}_{row['semester']}_"
                f"{row['exam']}_{row['round']}회_{label}.html")
    ascii_name = re.sub(r'[^0-9A-Za-z._-]', '_', original)
    if not ascii_name.lower().endswith('.html'):
        ascii_name += '.html'
    if not ascii_name.strip('._') or ascii_name in ('.html', '_.html'):
        ascii_name = f"weekly-exam-{row['id']}-{kind}.html"
    return ascii_name, original


@router.get('/{exam_id}/artifacts/{kind}/download')
def download_artifact(exam_id: int, kind: Literal['student-paper', 'answer-explanation']):
    """HTML artifact 원본 다운로드. 보기와 동일한 저장 canonical 바이트를 attachment로
    내려준다. MD artifact는 기존 보기 방식을 유지하므로 다운로드 대상이 아니다(404).
    운영자 전용(_require_operator)이며 Public endpoint로 노출하지 않는다."""
    _require_operator()
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
    if kind == 'student-paper':
        body = row['student_paper_html']
    elif kind == 'answer-explanation':
        body = row['answer_explanation_html']
    else:
        raise HTTPException(status_code=404, detail='HTML 다운로드 대상이 아닙니다.')
    ascii_name, original = _download_filename(row, kind)
    disposition = f'attachment; filename="{ascii_name}"; filename*=UTF-8\'\'{quote(original, safe="")}'
    return Response(body, media_type='text/html; charset=utf-8',
                    headers={'Content-Disposition': disposition, 'Cache-Control': 'no-store'})


@router.get('/{exam_id}/context-snapshot')
def view_context_snapshot(exam_id: int):
    _require_operator()
    with _db() as conn:
        row = conn.execute('SELECT context_snapshot,context_hash FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='Context Snapshot을 찾지 못했습니다.')
        return {'contextHash': row['context_hash'], 'snapshot': json.loads(row['context_snapshot'])}


@router.post('/{exam_id}/transition')
def transition_weekly_exam(exam_id: int, payload: TransitionRequest, request: Request):
    _require_operator()
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
        effective = EFFECTIVE_STATUS.get(row['status'], 'DRAFT')
        if payload.action == 'finalize' and effective == 'DRAFT':
            target, public_target, audit_action = 'FINAL', 'FINAL', 'FINALIZED'
        elif payload.action == 'reopen' and effective == 'FINAL':
            target, public_target, audit_action = 'REVIEW', 'DRAFT', 'REOPENED'
        else:
            raise HTTPException(
                status_code=409, detail=f"{effective} 상태에서 {payload.action} 전이는 허용되지 않습니다.")
        now, actor = datetime.now().isoformat(), _actor()
        conn.execute("""UPDATE weekly_exam_sets SET status=?,updated_at=?,reviewed_by=?,reviewed_at=?
          WHERE id=?""", (target, now, actor, now, exam_id))
        conn.execute("""INSERT INTO weekly_exam_audit
          (weekly_exam_id,action,from_status,to_status,actor,note,created_at) VALUES (?,?,?,?,?,?,?)""",
          (exam_id, audit_action, effective, public_target, actor, payload.note[:1000], now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())


def _draft_row_or_409(conn, exam_id: int):
    """DRAFT 계열만 관리(삭제/교체)한다. FINAL 이상은 409로 거부한다."""
    row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
    if EFFECTIVE_STATUS.get(row['status'], 'DRAFT') != 'DRAFT':
        raise HTTPException(
            status_code=409, detail=f"{EFFECTIVE_STATUS.get(row['status'])} 상태는 관리(삭제/교체)할 수 없습니다.")
    return row


def delete_draft(exam_id: int, *, actor: Optional[str] = None) -> dict:
    """DRAFT 세트 삭제. 자식 audit 행을 먼저 지운 뒤 세트를 지운다(단일 트랜잭션).

    get_db()는 FK pragma를 켜지 않지만, 자식→부모 순서로 지우면 pragma 설정과
    무관하게 무결성이 깨지지 않는다. 삭제된 id는 응답에 그대로 담는다."""
    with _db() as conn:
        row = _draft_row_or_409(conn, exam_id)
        now, who = datetime.now().isoformat(), actor or _actor()
        summary = {'id': row['id'], 'school': row['school'], 'grade': row['grade'],
                   'year': row['year'], 'semester': row['semester'], 'exam': row['exam'],
                   'round': row['round'], 'set_type': row['set_type'],
                   'question_count': row['question_count'], 'version': row['version'],
                   'deleted_by': who, 'deleted_at': now}
        conn.execute('DELETE FROM weekly_exam_audit WHERE weekly_exam_id=?', (exam_id,))
        conn.execute('DELETE FROM weekly_exam_sets WHERE id=?', (exam_id,))
        return summary


def update_metadata(exam_id: int, payload: WeeklyExamMetadataUpdate,
                    *, actor: Optional[str] = None) -> dict:
    """관리자가 세트의 식별 메타데이터만 수정한다.

    문제지·정답해설·검수 MD와 version은 건드리지 않는다. 세트 유형이 문항 수의
    source of truth이므로 70은 70문항, 40/40A/40B는 40문항으로 함께 정규화한다.
    """
    if payload.round is None and payload.setType is None and payload.questionCount is None:
        raise HTTPException(status_code=400, detail='수정할 세트 정보를 하나 이상 지정해야 합니다.')
    with _db() as conn:
        row = conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='주간 실전 모의고사를 찾지 못했습니다.')
        new_round = payload.round if payload.round is not None else row['round']
        # 1단계 구조조정: 40/70 구분 제거 — 문항 수는 questionCount를 직접 사용
        duplicate = conn.execute("""SELECT id FROM weekly_exam_sets
          WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND round=?
            AND set_type=? AND version=? AND id<>?""",
          (row['school'], row['grade'], row['year'], row['semester'], row['exam'],
           new_round, new_set_type, row['version'], exam_id)).fetchone()
        if duplicate:
            raise HTTPException(status_code=409, detail='동일 회차·세트·버전이 이미 존재합니다.')
        now, who = datetime.now().isoformat(), actor or _actor()
        conn.execute("""UPDATE weekly_exam_sets
          SET round=?,set_type=?,question_count=?,updated_at=? WHERE id=?""",
          (new_round, new_set_type, expected_count, now, exam_id))
        effective = EFFECTIVE_STATUS.get(row['status'], 'DRAFT')
        conn.execute("""INSERT INTO weekly_exam_audit
          (weekly_exam_id,action,from_status,to_status,actor,note,created_at)
          VALUES (?,'METADATA_UPDATED',?,?,?, ?,?)""",
          (exam_id, effective, effective, who,
           f"round {row['round']}→{new_round}; set_type {row['set_type']}→{new_set_type}; "
           f"question_count {row['question_count']}→{expected_count}", now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())


@router.patch('/{exam_id}/metadata')
def update_weekly_exam_metadata(exam_id: int, payload: WeeklyExamMetadataUpdate):
    _require_operator()
    if not _is_admin():
        raise HTTPException(status_code=403, detail='admin workspace access required')
    return update_metadata(exam_id, payload)


def replace_artifacts(exam_id: int, payload: WeeklyExamArtifactsUpdate,
                      *, actor: Optional[str] = None) -> dict:
    """DRAFT 세트의 artifact 교체. 제공된 파일만 교체하고 나머지는 유지한다.
    회차·세트 유형·status·version·identity는 유지하고 filename/hash와 본문만
    다시 계산한다. 세 파일이 모두 없으면 400. sourceMaterialIds가 주어지면
    _source_refs 기존 검증으로 현재 Context에 속한 ID인지 확인한다."""
    provided = [name for name, value in (
        ('studentPaper', payload.studentPaper),
        ('answerExplanation', payload.answerExplanation),
        ('productionVerification', payload.productionVerification),
    ) if value is not None]
    if not provided:
        raise HTTPException(status_code=400, detail='교체할 파일을 하나 이상 지정해야 합니다.')
    cleaned = {}
    for name in provided:
        value = getattr(payload, name)
        if name == 'productionVerification':
            if not (value or '').strip():
                raise HTTPException(status_code=400, detail='교체 본문이 비어 있습니다.')
            cleaned[name] = value
        else:
            cleaned[name] = _clean_html(value)
    with _db() as conn:
        row = _draft_row_or_409(conn, exam_id)
        refs = json.loads(row['source_material_ids'] or '[]')
        if payload.sourceMaterialIds is not None:
            if _context_reader is None:
                raise RuntimeError('weekly exam context reader is not configured')
            current = _context_reader(row['school'], row['grade'], row['year'],
                                      row['semester'], row['exam'])
            refs = [item['id'] for item in
                    _source_refs(current, payload.sourceMaterialIds)['requested']]
        now, who = datetime.now().isoformat(), actor or _actor()
        student_html = cleaned.get('studentPaper', row['student_paper_html'])
        answer_html = cleaned.get('answerExplanation', row['answer_explanation_html'])
        verification_md = cleaned.get('productionVerification', row['verification_md'])
        conn.execute("""UPDATE weekly_exam_sets SET student_paper_name=?,student_paper_html=?,
          student_paper_hash=?,answer_explanation_name=?,answer_explanation_html=?,
          answer_explanation_hash=?,verification_name=?,verification_md=?,verification_hash=?,
          source_material_ids=?,updated_at=? WHERE id=?""",
          ((payload.studentPaperName.strip() or row['student_paper_name'])[:255]
           if 'studentPaper' in cleaned else row['student_paper_name'], student_html,
           _sha(student_html),
           (payload.answerExplanationName.strip() or row['answer_explanation_name'])[:255]
           if 'answerExplanation' in cleaned else row['answer_explanation_name'],
           answer_html, _sha(answer_html),
           (payload.productionVerificationName.strip() or row['verification_name'])[:255]
           if 'productionVerification' in cleaned else row['verification_name'],
           verification_md, _sha(verification_md),
           json.dumps(refs), now, exam_id))
        conn.execute("""INSERT INTO weekly_exam_audit
          (weekly_exam_id,action,from_status,to_status,actor,created_at) VALUES (?,'UPDATED','DRAFT','DRAFT',?,?)""",
          (exam_id, who, now))
        return _row(conn.execute('SELECT * FROM weekly_exam_sets WHERE id=?', (exam_id,)).fetchone())


@router.delete('/{exam_id}')
def delete_weekly_exam(exam_id: int):
    _require_operator()
    return delete_draft(exam_id)


@router.put('/{exam_id}/artifacts')
def replace_weekly_exam_artifacts(exam_id: int, payload: WeeklyExamArtifactsUpdate):
    _require_operator()
    return replace_artifacts(exam_id, payload)
