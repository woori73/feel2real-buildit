"""오답 모의고사 회차 관리 (Wrong Mock workflow).

한 회차(예: 오답 24회)는 20문항 단위로 선생님이 직접 생성하고, 해당 회차에 쓸
원본 오답 사진을 직접 올린 뒤, 사진 등록 완료로 제작대기 상태가 된다. 이후
문제지 HTML + 간략해설 HTML 두 artifact가 모두 등록되면 제작완료가 된다.

기존 exam_material_sets를 재사용하지 않고 별도 테이블 3개를 두는 이유:
1. exam_material_sets에는 회차(round)/상태(status)/편집잠금(edit lock) 개념이
   없어 사진등록중→제작대기→제작완료 상태머신을 표현할 수 없다.
2. core_type='student_wrong_note' 경로는 student_id + JSON을 강제하는데,
   오답모의고사 사진은 학생 귀속이 아니라 회차 귀속이다.
3. exam_material_sets row는 Living Context(student_errors)로 그대로 흘러가서,
   수집 중인 미완성 회차 사진이 AI Context를 오염시킨다. 분리해야 향후
   OpenCode가 READY 회차만 정확히 가져갈 수 있다.
4. 문제지/해설 HTML inline canonical 보존은 weekly_exam_sets 패턴이 이미
   검증한 계약이라 같은 방식을 따른다(final_deliverables는 시험 Context 단위
   + kind 계약이라 회차 단위와 맞지 않는다).

사진 개수와 문항 개수를 DB constraint로 묶지 않는다. 한 사진에 여러 문항이
있거나 한 문항이 여러 사진에 걸쳐 있을 수 있으므로, 사진 등록 완료는 개수와
무관하게 선생님이 직접 버튼으로 확정한다.

상태: COLLECTING(사진 등록 중) -> READY(제작대기) -> COMPLETED(제작완료).
편집 잠금(edit_locked): READY/COMPLETED 전이 시 자동 잠금. 잠금 상태에서는
사진 추가·제목수정·순서변경·삭제/교체와 artifact 등록/교체가 모두 409로
거부된다. 잠금을 풀면(편집 ON) 수정 후 다시 잠글 수 있다.
회차 자체 삭제는 COLLECTING 상태에서만 허용된다(완성 회차 보호).
"""

from __future__ import annotations

import hashlib
import html
import json
import re
from datetime import datetime
from typing import Callable, Literal, Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel, Field
from urllib.parse import quote

import workspace as wiki_workspace

router = APIRouter(prefix='/api/wrong-mock', tags=['wrong-mock'])
_get_db: Optional[Callable] = None

# 물리 상태. 화면 표시 라벨은 EFFECTIVE_LABEL.
STATUSES = ('COLLECTING', 'READY', 'COMPLETED')
EFFECTIVE_LABEL = {
    'COLLECTING': '사진 등록 중',
    'READY': '제작대기',
    'COMPLETED': '제작완료',
}
ARTIFACTS = ('paper', 'explanation')

QUESTION_COUNT = 20

# §11 제작 규격. 이번 단계에서 generation engine을 만들지 않지만, 향후 agent가
# 읽을 수 있도록 회차마다 metadata로 보존한다(docs/wiki/WRONG_MOCK_STANDARD.md와 동일 내용).
SPEC = {
    'schemaVersion': 'wrong-mock-spec-v1',
    'questionCount': QUESTION_COUNT,
    'titlePattern': '오답 {round}회',
    'paper': 'A4 세로, 2단 구성, 불필요한 여백 최소화, 페이지 최대한 활용',
    'noClip': '지문/문항/선택지 잘림 금지',
    'sourceLabel': '문제번호 위에 작은 출처 표시(예: 2021년 고3 3월 서울교육청)',
    'renumber': '원래 기출 번호를 새 시험 문제번호로 사용하지 않고 1~20번으로 재번호 부여',
    'answer': '사진에 표시된 정답을 읽어 사용',
    'restore': '원문 문제 구조 최대한 정확하게 복원',
    'explanationTemplate': 'BUILD_IT_오답_23회_간략해설_A4_FINAL 계열 디자인 유지. 첫 페이지에 정답&간략해설+전체 어휘(왼쪽 영어/오른쪽 한국어), 이후 빠른 정답 1~20과 문제별 유형·정답·핵심어·RELATION·SKILL·풀이',
}

_MAX_PHOTO_BYTES = 30 * 1024 * 1024

_SCRIPT_SUBTREE_RE = re.compile(r'<script\b[^>]*>.*?</script\s*>|<script\b[^>]*/>', re.IGNORECASE | re.DOTALL)


def configure(get_db: Callable) -> None:
    global _get_db
    _get_db = get_db


def init_schema(conn) -> None:
    conn.execute("""CREATE TABLE IF NOT EXISTS wrong_mock_rounds (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        school TEXT NOT NULL, grade TEXT NOT NULL, year INTEGER NOT NULL,
        semester TEXT NOT NULL, exam TEXT NOT NULL, round INTEGER NOT NULL,
        title TEXT NOT NULL DEFAULT '',
        question_count INTEGER NOT NULL DEFAULT 20,
        status TEXT NOT NULL DEFAULT 'COLLECTING'
          CHECK (status IN ('COLLECTING','READY','COMPLETED')),
        paper_name TEXT NOT NULL DEFAULT '', paper_html TEXT NOT NULL DEFAULT '',
        paper_hash TEXT NOT NULL DEFAULT '',
        expl_name TEXT NOT NULL DEFAULT '', expl_html TEXT NOT NULL DEFAULT '',
        expl_hash TEXT NOT NULL DEFAULT '',
        spec_json TEXT NOT NULL DEFAULT '{}',
        edit_locked INTEGER NOT NULL DEFAULT 0,
        created_by TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        completed_by TEXT NOT NULL DEFAULT '', completed_at TEXT NOT NULL DEFAULT ''
    )""")
    conn.execute("""CREATE UNIQUE INDEX IF NOT EXISTS uk_wrong_mock_round
        ON wrong_mock_rounds(school,grade,year,semester,exam,round)""")
    conn.execute("""CREATE INDEX IF NOT EXISTS idx_wrong_mock_context
        ON wrong_mock_rounds(school,grade,year,semester,exam,status)""")
    # 원본 사진은 영구 Source다. 제작완료 후에도 삭제하지 않으며, 사진 개수와
    # 문항 개수를 동일시하는 constraint를 두지 않는다(UNIQUE 없음).
    conn.execute("""CREATE TABLE IF NOT EXISTS wrong_mock_photos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        round_id INTEGER NOT NULL,
        title TEXT NOT NULL DEFAULT '',
        original_name TEXT NOT NULL DEFAULT '',
        original_type TEXT NOT NULL DEFAULT 'application/octet-stream',
        original_blob BLOB NOT NULL,
        content_hash TEXT NOT NULL DEFAULT '',
        sort_order INTEGER NOT NULL DEFAULT 0,
        uploaded_by TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL,
        FOREIGN KEY (round_id) REFERENCES wrong_mock_rounds(id)
    )""")
    conn.execute("""CREATE INDEX IF NOT EXISTS idx_wrong_mock_photo_round
        ON wrong_mock_photos(round_id,sort_order,id)""")
    conn.execute("""CREATE INDEX IF NOT EXISTS idx_wrong_mock_photo_hash
        ON wrong_mock_photos(content_hash)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS wrong_mock_audit (
        id INTEGER PRIMARY KEY AUTOINCREMENT, round_id INTEGER NOT NULL,
        action TEXT NOT NULL, from_status TEXT NOT NULL DEFAULT '',
        to_status TEXT NOT NULL DEFAULT '',
        actor TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL,
        FOREIGN KEY (round_id) REFERENCES wrong_mock_rounds(id)
    )""")


class WrongMockRoundRequest(BaseModel):
    school: str
    grade: str
    year: int
    semester: str
    exam: str
    round: Optional[int] = Field(default=None, ge=1)
    title: str = ''


class WrongMockArtifactsUpdate(BaseModel):
    paperHtml: Optional[str] = None
    paperName: str = ''
    explHtml: Optional[str] = None
    explName: str = ''


class PhotoTitleUpdate(BaseModel):
    title: str = ''


class PhotoReorderRequest(BaseModel):
    orderedIds: list[int] = Field(default_factory=list)


class EditLockRequest(BaseModel):
    locked: bool


def _db():
    if _get_db is None:
        raise RuntimeError('wrong mock service is not configured')
    return _get_db()


def _sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha_text(text: str) -> str:
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def _actor() -> str:
    identity = wiki_workspace.current_identity()
    return identity.login_id if identity else 'mcp-agent'


def _require_operator() -> None:
    """Private assets require a signed human identity. MCP calls services directly under API-key auth."""
    if wiki_workspace.current_identity() is None:
        raise HTTPException(status_code=401, detail='로그인이 필요한 운영자 자료입니다.')


def _preserve_artifact_html(value: str) -> str:
    """Weekly Exam HTML canonical 보존과 같은 계약. A4/print CSS·2단 class·문서
    구조를 그대로 저장하고 script subtree만 제거한다. 저장된 바이트가 곧
    보기/다운로드 정본이다."""
    cleaned = _SCRIPT_SUBTREE_RE.sub('', (value or '').strip())
    if not cleaned.strip():
        raise HTTPException(status_code=400, detail='HTML 본문이 비어 있습니다.')
    return cleaned


def _photo_row(row) -> dict:
    return {
        'id': row['id'], 'round_id': row['round_id'],
        'title': row['title'], 'original_name': row['original_name'],
        'original_type': row['original_type'],
        'size': len(row['original_blob']) if row['original_blob'] is not None else 0,
        'content_hash': row['content_hash'], 'sort_order': row['sort_order'],
        'uploaded_by': row['uploaded_by'], 'created_at': row['created_at'],
        'original_api_path': f"/api/wrong-mock/photos/{row['id']}/original",
    }


def _row(row, *, photo_count: int = 0) -> dict:
    item = dict(row)
    for key in ('paper_html', 'expl_html'):
        item.pop(key, None)
    try:
        item['spec'] = json.loads(item.pop('spec_json') or '{}')
    except (TypeError, ValueError):
        item['spec'] = {}
    item['photo_count'] = photo_count
    item['has_paper'] = bool(item.get('paper_hash'))
    item['has_explanation'] = bool(item.get('expl_hash'))
    item['status_label'] = EFFECTIVE_LABEL.get(item.get('status'), item.get('status'))
    item['edit_locked'] = bool(item.get('edit_locked'))
    return item


def _get_round_or_404(conn, round_id: int):
    row = conn.execute('SELECT * FROM wrong_mock_rounds WHERE id=?', (round_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail='오답 모의고사 회차를 찾지 못했습니다.')
    return row


def _photo_count(conn, round_id: int) -> int:
    return conn.execute('SELECT COUNT(*) c FROM wrong_mock_photos WHERE round_id=?',
                        (round_id,)).fetchone()['c']


def _require_editable(row) -> None:
    """COLLECTING이 아니면서 편집이 잠겨 있으면(편집 OFF) 수정을 거부한다."""
    if row['status'] == 'COLLECTING':
        return
    if not row['edit_locked']:
        return
    raise HTTPException(status_code=409, detail='편집이 잠겨 있습니다. 편집 ON으로 전환한 뒤 수정하세요.')


def _audit(conn, round_id: int, action: str, from_status: str, to_status: str,
           actor: str, note: str = '') -> None:
    conn.execute("""INSERT INTO wrong_mock_audit
        (round_id,action,from_status,to_status,actor,note,created_at)
        VALUES (?,?,?,?,?,?,?)""",
        (round_id, action, from_status, to_status, actor, note[:1000], datetime.now().isoformat()))


def create_round(payload: WrongMockRoundRequest, *, actor: Optional[str] = None) -> dict:
    school, grade, semester, exam = (payload.school.strip(), payload.grade.strip(),
                                     payload.semester.strip(), payload.exam.strip())
    if not school or not grade or not semester or not exam:
        raise HTTPException(status_code=400, detail='학교·학년·학기·시험을 모두 입력해 주세요.')
    with _db() as conn:
        if payload.round is None:
            nxt = conn.execute("""SELECT COALESCE(MAX(round),0)+1 v FROM wrong_mock_rounds
                WHERE school=? AND grade=? AND year=? AND semester=? AND exam=?""",
                (school, grade, payload.year, semester, exam)).fetchone()['v']
            round_no = int(nxt)
        else:
            round_no = int(payload.round)
            dup = conn.execute("""SELECT id FROM wrong_mock_rounds
                WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND round=?""",
                (school, grade, payload.year, semester, exam, round_no)).fetchone()
            if dup:
                raise HTTPException(status_code=409, detail=f'오답 {round_no}회는 이미 존재합니다.')
        title = payload.title if payload.title else f'오답 {round_no}회'
        now, who = datetime.now().isoformat(), actor or _actor()
        cursor = conn.execute("""INSERT INTO wrong_mock_rounds
            (school,grade,year,semester,exam,round,title,question_count,status,
             spec_json,edit_locked,created_by,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,'COLLECTING',?,0,?,?,?)""",
            (school, grade, payload.year, semester, exam, round_no, title,
             QUESTION_COUNT, json.dumps(SPEC, ensure_ascii=False), who, now, now))
        round_id = cursor.lastrowid
        _audit(conn, round_id, 'CREATED', '', 'COLLECTING', who, title)
        return _row(conn.execute('SELECT * FROM wrong_mock_rounds WHERE id=?', (round_id,)).fetchone())


def list_rounds(school: str, grade: str, year: int, semester: str, exam: str) -> list[dict]:
    with _db() as conn:
        rows = conn.execute("""SELECT * FROM wrong_mock_rounds
            WHERE school=? AND grade=? AND year=? AND semester=? AND exam=?
            ORDER BY round DESC""", (school, grade, year, semester, exam)).fetchall()
        out = []
        for row in rows:
            out.append(_row(row, photo_count=_photo_count(conn, row['id'])))
        return out


def get_round(round_id: int) -> dict:
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        photos = conn.execute("""SELECT * FROM wrong_mock_photos
            WHERE round_id=? ORDER BY sort_order,id""", (round_id,)).fetchall()
        item = _row(row, photo_count=len(photos))
        item['photos'] = [_photo_row(photo) for photo in photos]
        return item


def _check_duplicate(conn, round_id: int, content_hash: str, *, exclude_photo_id: int = 0) -> None:
    """같은 회차 중복은 차단, 다른 회차 중복은 경고(강제 등록 가능)."""
    same = conn.execute("""SELECT id FROM wrong_mock_photos
        WHERE round_id=? AND content_hash=? AND id<>? LIMIT 1""",
        (round_id, content_hash, exclude_photo_id)).fetchone()
    if same:
        raise HTTPException(status_code=409, detail='이미 등록된 사진입니다.')
    row = _get_round_or_404(conn, round_id)
    others = conn.execute("""SELECT DISTINCT r.round, r.title FROM wrong_mock_photos p
        JOIN wrong_mock_rounds r ON r.id=p.round_id
        WHERE p.content_hash=? AND p.round_id<>? AND r.school=? AND r.grade=?
          AND r.year=? AND r.semester=? AND r.exam=? AND p.id<>?
        ORDER BY r.round""",
        (content_hash, round_id, row['school'], row['grade'], row['year'],
         row['semester'], row['exam'], exclude_photo_id)).fetchall()
    if others:
        labels = ', '.join(f"오답 {other['round']}회" for other in others)
        raise HTTPException(
            status_code=409,
            detail=f'이 사진은 {labels}에도 등록되어 있습니다. 계속 등록하시겠습니까?',
        )


def add_photos(round_id: int, files: list[tuple[str, str, bytes, str]],
               *, actor: Optional[str] = None, force: bool = False) -> dict:
    """files: (original_name, content_type, data, title). title은 원본 그대로
    보존한다(임의 변경·유실 금지)."""
    if not files:
        raise HTTPException(status_code=400, detail='등록할 사진을 하나 이상 선택해 주세요.')
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        _require_editable(row)
        prepared = []
        for original_name, content_type, data, title in files:
            if not data or len(data) > _MAX_PHOTO_BYTES:
                raise HTTPException(status_code=400, detail='사진은 30MB 이하의 파일이어야 합니다.')
            content_hash = _sha_bytes(data)
            if not force:
                _check_duplicate(conn, round_id, content_hash)
            # title이 비면 원본 파일명을 그대로 쓴다. strip· truncation 없음.
            prepared.append((original_name or '', content_type or 'application/octet-stream',
                             data, content_hash, title if title else (original_name or '')))
        now, who = datetime.now().isoformat(), actor or _actor()
        base_order = conn.execute(
            'SELECT COALESCE(MAX(sort_order),-1)+1 v FROM wrong_mock_photos WHERE round_id=?',
            (round_id,)).fetchone()['v']
        for index, (original_name, content_type, data, content_hash, title) in enumerate(prepared):
            conn.execute("""INSERT INTO wrong_mock_photos
                (round_id,title,original_name,original_type,original_blob,content_hash,
                 sort_order,uploaded_by,created_at) VALUES (?,?,?,?,?,?,?,?,?)""",
                (round_id, title, original_name[:255], content_type,
                 data, content_hash, base_order + index, who, now))
        conn.execute('UPDATE wrong_mock_rounds SET updated_at=? WHERE id=?', (now, round_id))
        _audit(conn, round_id, 'PHOTOS_ADDED', row['status'], row['status'], who,
               f'{len(prepared)}장 등록')
    return get_round(round_id)


def rename_photo(photo_id: int, title: str, *, actor: Optional[str] = None) -> dict:
    with _db() as conn:
        photo = conn.execute('SELECT * FROM wrong_mock_photos WHERE id=?', (photo_id,)).fetchone()
        if not photo:
            raise HTTPException(status_code=404, detail='사진을 찾지 못했습니다.')
        row = _get_round_or_404(conn, photo['round_id'])
        _require_editable(row)
        now, who = datetime.now().isoformat(), actor or _actor()
        conn.execute('UPDATE wrong_mock_photos SET title=? WHERE id=?', (title, photo_id))
        conn.execute('UPDATE wrong_mock_rounds SET updated_at=? WHERE id=?', (now, row['id']))
        _audit(conn, row['id'], 'PHOTO_RENAMED', row['status'], row['status'], who, title[:100])
        return _photo_row(conn.execute('SELECT * FROM wrong_mock_photos WHERE id=?', (photo_id,)).fetchone())


def reorder_photos(round_id: int, ordered_ids: list[int], *, actor: Optional[str] = None) -> dict:
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        _require_editable(row)
        current = [item['id'] for item in conn.execute(
            'SELECT id FROM wrong_mock_photos WHERE round_id=?', (round_id,)).fetchall()]
        if sorted(current) != sorted(ordered_ids) or len(set(ordered_ids)) != len(current):
            raise HTTPException(status_code=400, detail='해당 회차의 사진 목록과 순서가 일치하지 않습니다.')
        now, who = datetime.now().isoformat(), actor or _actor()
        for order, photo_id in enumerate(ordered_ids):
            conn.execute('UPDATE wrong_mock_photos SET sort_order=? WHERE id=? AND round_id=?',
                         (order, photo_id, round_id))
        conn.execute('UPDATE wrong_mock_rounds SET updated_at=? WHERE id=?', (now, round_id))
        _audit(conn, round_id, 'PHOTOS_REORDERED', row['status'], row['status'], who, '')
    return get_round(round_id)


def delete_photo(photo_id: int, *, actor: Optional[str] = None) -> dict:
    with _db() as conn:
        photo = conn.execute('SELECT * FROM wrong_mock_photos WHERE id=?', (photo_id,)).fetchone()
        if not photo:
            raise HTTPException(status_code=404, detail='사진을 찾지 못했습니다.')
        row = _get_round_or_404(conn, photo['round_id'])
        _require_editable(row)
        now, who = datetime.now().isoformat(), actor or _actor()
        conn.execute('DELETE FROM wrong_mock_photos WHERE id=?', (photo_id,))
        conn.execute('UPDATE wrong_mock_rounds SET updated_at=? WHERE id=?', (now, row['id']))
        _audit(conn, row['id'], 'PHOTO_DELETED', row['status'], row['status'], who,
               photo['title'][:100])
        return {'id': photo_id, 'round_id': row['id'], 'deleted_by': who, 'deleted_at': now}


def replace_photo(photo_id: int, original_name: str, content_type: str, data: bytes,
                  title: Optional[str] = None, *, actor: Optional[str] = None,
                  force: bool = False) -> dict:
    """사진 교체. id·순서는 유지하고 파일·제목·hash만 바꾼다."""
    if not data or len(data) > _MAX_PHOTO_BYTES:
        raise HTTPException(status_code=400, detail='사진은 30MB 이하의 파일이어야 합니다.')
    with _db() as conn:
        photo = conn.execute('SELECT * FROM wrong_mock_photos WHERE id=?', (photo_id,)).fetchone()
        if not photo:
            raise HTTPException(status_code=404, detail='사진을 찾지 못했습니다.')
        row = _get_round_or_404(conn, photo['round_id'])
        _require_editable(row)
        content_hash = _sha_bytes(data)
        if not force:
            _check_duplicate(conn, row['id'], content_hash, exclude_photo_id=photo_id)
        now, who = datetime.now().isoformat(), actor or _actor()
        new_title = photo['title'] if title is None else title
        conn.execute("""UPDATE wrong_mock_photos SET title=?,original_name=?,
            original_type=?,original_blob=?,content_hash=? WHERE id=?""",
            (new_title, (original_name or '')[:255],
             content_type or 'application/octet-stream', data, content_hash, photo_id))
        conn.execute('UPDATE wrong_mock_rounds SET updated_at=? WHERE id=?', (now, row['id']))
        _audit(conn, row['id'], 'PHOTO_REPLACED', row['status'], row['status'], who,
               new_title[:100])
        return _photo_row(conn.execute('SELECT * FROM wrong_mock_photos WHERE id=?', (photo_id,)).fetchone())


def complete_photo_collection(round_id: int, *, actor: Optional[str] = None) -> dict:
    """사진 등록 완료. COLLECTING -> READY(제작대기)로 바꾸고 편집을 잠근다.
    사진 개수와 무관하게 선생님이 직접 확정한다(개수로 자동 전이하지 않음)."""
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        if row['status'] != 'COLLECTING':
            raise HTTPException(
                status_code=409,
                detail=f"{EFFECTIVE_LABEL.get(row['status'], row['status'])} 상태에서는 사진 등록 완료를 할 수 없습니다.")
        now, who = datetime.now().isoformat(), actor or _actor()
        conn.execute("""UPDATE wrong_mock_rounds SET status='READY',edit_locked=1,
            updated_at=? WHERE id=?""", (now, round_id))
        _audit(conn, round_id, 'PHOTOS_COMPLETED', 'COLLECTING', 'READY', who,
               f"{_photo_count(conn, round_id)}장 확정")
    return get_round(round_id)


def register_artifacts(round_id: int, payload: WrongMockArtifactsUpdate,
                       *, actor: Optional[str] = None) -> dict:
    """문제지/간략해설 HTML 등록·교체. 동일 저장 contract를 사람 업로드와
    OpenCode 자동 등록이 함께 쓴다. 두 artifact가 모두 갖춰지면 제작완료."""
    provided = [name for name, value in (
        ('paperHtml', payload.paperHtml), ('explHtml', payload.explHtml)) if value is not None]
    if not provided:
        raise HTTPException(status_code=400, detail='등록할 문제지 또는 간략해설 HTML을 입력해 주세요.')
    cleaned = {name: _preserve_artifact_html(getattr(payload, name)) for name in provided}
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        if row['status'] == 'COLLECTING':
            raise HTTPException(status_code=409, detail='사진 등록을 먼저 완료해 주세요.')
        _require_editable(row)
        now, who = datetime.now().isoformat(), actor or _actor()
        paper_html = cleaned.get('paperHtml', row['paper_html'])
        expl_html = cleaned.get('explHtml', row['expl_html'])
        paper_name = ((payload.paperName.strip() or row['paper_name'])[:255]
                      if 'paperHtml' in cleaned else row['paper_name'])
        expl_name = ((payload.explName.strip() or row['expl_name'])[:255]
                     if 'explHtml' in cleaned else row['expl_name'])
        if not paper_name:
            paper_name = f"오답_{row['round']}회_20문항_문제지.html"
        if not expl_name:
            expl_name = f"BUILD_IT_오답_{row['round']}회_간략해설_A4_FINAL.html"
        status, completed_by, completed_at = row['status'], row['completed_by'], row['completed_at']
        action = 'ARTIFACTS_UPDATED'
        if paper_html.strip() and expl_html.strip() and status != 'COMPLETED':
            status, action = 'COMPLETED', 'COMPLETED'
            completed_by, completed_at = who, now
        conn.execute("""UPDATE wrong_mock_rounds SET paper_name=?,paper_html=?,paper_hash=?,
            expl_name=?,expl_html=?,expl_hash=?,status=?,edit_locked=?,updated_at=?,
            completed_by=?,completed_at=? WHERE id=?""",
            (paper_name, paper_html, _sha_text(paper_html) if paper_html.strip() else '',
             expl_name, expl_html, _sha_text(expl_html) if expl_html.strip() else '',
             status, 1 if status == 'COMPLETED' else row['edit_locked'],
             now, completed_by, completed_at, round_id))
        _audit(conn, round_id, action, row['status'], status, who, ','.join(provided))
    return get_round(round_id)


def set_edit_lock(round_id: int, locked: bool, *, actor: Optional[str] = None) -> dict:
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        now, who = datetime.now().isoformat(), actor or _actor()
        conn.execute('UPDATE wrong_mock_rounds SET edit_locked=?,updated_at=? WHERE id=?',
                     (1 if locked else 0, now, round_id))
        _audit(conn, round_id, 'EDIT_UNLOCKED' if not locked else 'EDIT_LOCKED',
               row['status'], row['status'], who, '')
    return get_round(round_id)


def delete_round(round_id: int, *, actor: Optional[str] = None) -> dict:
    """회차 삭제는 사진 등록 중(COLLECTING) 상태에서만 허용된다."""
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
        if row['status'] != 'COLLECTING':
            raise HTTPException(
                status_code=409,
                detail=f"{EFFECTIVE_LABEL.get(row['status'], row['status'])} 상태의 회차는 삭제할 수 없습니다.")
        now, who = datetime.now().isoformat(), actor or _actor()
        summary = {'id': row['id'], 'school': row['school'], 'grade': row['grade'],
                   'year': row['year'], 'semester': row['semester'], 'exam': row['exam'],
                   'round': row['round'], 'title': row['title'],
                   'deleted_by': who, 'deleted_at': now}
        conn.execute('DELETE FROM wrong_mock_photos WHERE round_id=?', (round_id,))
        conn.execute('DELETE FROM wrong_mock_audit WHERE round_id=?', (round_id,))
        conn.execute('DELETE FROM wrong_mock_rounds WHERE id=?', (round_id,))
        return summary


def view_artifact(round_id: int, kind: Literal['paper', 'explanation']) -> Response:
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
    # 저장된 canonical 원본을 그대로 렌더링한다. 교체 직후 과거 파일이 보이지
    # 않도록 캐시를 저장하지 않는다.
    if kind == 'paper':
        body = row['paper_html']
    elif kind == 'explanation':
        body = row['expl_html']
    else:
        raise HTTPException(status_code=404, detail='문제지 또는 간략해설만 볼 수 있습니다.')
    if not (body or '').strip():
        raise HTTPException(status_code=404, detail='등록된 자료가 없습니다.')
    return Response(body, media_type='text/html; charset=utf-8',
                    headers={'Cache-Control': 'no-store'})


_ARTIFACT_DOWNLOAD_LABELS = {'paper': '문제지', 'explanation': '간략해설'}


def download_artifact(round_id: int, kind: Literal['paper', 'explanation']) -> Response:
    with _db() as conn:
        row = _get_round_or_404(conn, round_id)
    if kind == 'paper':
        body = row['paper_html']
    elif kind == 'explanation':
        body = row['expl_html']
    else:
        raise HTTPException(status_code=404, detail='HTML 다운로드 대상이 아닙니다.')
    if not (body or '').strip():
        raise HTTPException(status_code=404, detail='등록된 자료가 없습니다.')
    label = _ARTIFACT_DOWNLOAD_LABELS[kind]
    original = (f"{row['school']}_{row['grade']}_{row['year']}_{row['semester']}_"
                f"{row['exam']}_오답_{row['round']}회_{label}.html")
    ascii_name = re.sub(r'[^0-9A-Za-z._-]', '_', original)
    if not ascii_name.lower().endswith('.html'):
        ascii_name += '.html'
    if not ascii_name.strip('._') or ascii_name in ('.html', '_.html'):
        ascii_name = f"wrong-mock-{row['id']}-{kind}.html"
    disposition = f'attachment; filename="{ascii_name}"; filename*=UTF-8\'\'{quote(original, safe="")}'
    return Response(body, media_type='text/html; charset=utf-8',
                    headers={'Content-Disposition': disposition, 'Cache-Control': 'no-store'})


def view_photo_original(photo_id: int) -> Response:
    with _db() as conn:
        photo = conn.execute(
            'SELECT original_blob,original_type,original_name FROM wrong_mock_photos WHERE id=?',
            (photo_id,)).fetchone()
        if not photo:
            raise HTTPException(status_code=404, detail='사진을 찾지 못했습니다.')
    safe_name = re.sub(r'[^0-9A-Za-z._-]', '_', photo['original_name'] or 'photo')
    suffix = (photo['original_name'] or '').lower().rsplit('.', 1)[-1] if '.' in (photo['original_name'] or '') else ''
    ctype = (photo['original_type'] or '').lower()
    # 사용자 HTML을 같은 origin에서 inline 실행하지 않는다. 이미지는 브라우저에서
    # 바로 확인할 수 있게 inline으로, HTML은 attachment로만 내려준다.
    if suffix in ('html', 'htm'):
        disposition = 'attachment'
    elif suffix in ('jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf') or ctype.startswith('image/') or ctype == 'application/pdf':
        disposition = 'inline'
    else:
        disposition = 'attachment'
    return Response(photo['original_blob'], media_type=photo['original_type'] or 'application/octet-stream',
                    headers={'Content-Disposition': f'{disposition}; filename="{safe_name}"',
                             'Cache-Control': 'no-store'})


def list_ready_rounds(school: str, grade: str, year: int, semester: str, exam: str) -> list[dict]:
    """제작대기(READY) 회차 목록. 향후 OpenCode가 가져갈 읽기 계약으로,
    사진 제목 metadata와 원본 경로를 함께 담는다."""
    with _db() as conn:
        rows = conn.execute("""SELECT * FROM wrong_mock_rounds
            WHERE school=? AND grade=? AND year=? AND semester=? AND exam=? AND status='READY'
            ORDER BY round DESC""", (school, grade, year, semester, exam)).fetchall()
        out = []
        for row in rows:
            photos = conn.execute("""SELECT * FROM wrong_mock_photos
                WHERE round_id=? ORDER BY sort_order,id""", (row['id'],)).fetchall()
            item = _row(row, photo_count=len(photos))
            item['photos'] = [_photo_row(photo) for photo in photos]
            out.append(item)
        return out


@router.post('/rounds', status_code=201)
def api_create_round(payload: WrongMockRoundRequest):
    _require_operator()
    return create_round(payload)


@router.get('/rounds')
def api_list_rounds(school: str, grade: str, year: int, semester: str, exam: str):
    _require_operator()
    return list_rounds(school, grade, year, semester, exam)


@router.get('/ready')
def api_list_ready_rounds(school: str, grade: str, year: int, semester: str, exam: str):
    _require_operator()
    return list_ready_rounds(school, grade, year, semester, exam)


@router.get('/rounds/{round_id}')
def api_get_round(round_id: int):
    _require_operator()
    return get_round(round_id)


@router.post('/rounds/{round_id}/photos', status_code=201)
def api_add_photos(round_id: int, files: list[UploadFile] = File(...),
                   titles: str = Form('[]'), force: bool = Form(False)):
    _require_operator()
    try:
        parsed = json.loads(titles) if titles else []
    except (TypeError, ValueError):
        raise HTTPException(status_code=400, detail='사진 제목 형식이 올바르지 않습니다.')
    if not isinstance(parsed, list):
        raise HTTPException(status_code=400, detail='사진 제목 형식이 올바르지 않습니다.')
    uploads = []
    for index, item in enumerate(files):
        data = item.file.read()
        title = parsed[index] if index < len(parsed) and isinstance(parsed[index], str) else ''
        uploads.append((item.filename or '', item.content_type or 'application/octet-stream', data, title))
    return add_photos(round_id, uploads, force=force)


@router.get('/photos/{photo_id}/original')
def api_view_photo_original(photo_id: int):
    _require_operator()
    return view_photo_original(photo_id)


@router.patch('/photos/{photo_id}')
def api_rename_photo(photo_id: int, payload: PhotoTitleUpdate):
    _require_operator()
    return rename_photo(photo_id, payload.title)


@router.post('/rounds/{round_id}/photos/reorder')
def api_reorder_photos(round_id: int, payload: PhotoReorderRequest):
    _require_operator()
    return reorder_photos(round_id, payload.orderedIds)


@router.delete('/photos/{photo_id}')
def api_delete_photo(photo_id: int):
    _require_operator()
    return delete_photo(photo_id)


@router.put('/photos/{photo_id}')
def api_replace_photo(photo_id: int, file: UploadFile = File(...),
                      title: Optional[str] = Form(None), force: bool = Form(False)):
    _require_operator()
    return replace_photo(photo_id, file.filename or '',
                         file.content_type or 'application/octet-stream',
                         file.file.read(), title, force=force)


@router.post('/rounds/{round_id}/complete-photos')
def api_complete_photo_collection(round_id: int):
    _require_operator()
    return complete_photo_collection(round_id)


@router.put('/rounds/{round_id}/artifacts')
def api_register_artifacts(round_id: int, payload: WrongMockArtifactsUpdate):
    _require_operator()
    return register_artifacts(round_id, payload)


@router.post('/rounds/{round_id}/edit-lock')
def api_set_edit_lock(round_id: int, payload: EditLockRequest):
    _require_operator()
    return set_edit_lock(round_id, payload.locked)


@router.delete('/rounds/{round_id}')
def api_delete_round(round_id: int):
    _require_operator()
    return delete_round(round_id)


@router.get('/rounds/{round_id}/artifacts/{kind}')
def api_view_artifact(round_id: int, kind: Literal['paper', 'explanation']):
    _require_operator()
    return view_artifact(round_id, kind)


@router.get('/rounds/{round_id}/artifacts/{kind}/download')
def api_download_artifact(round_id: int, kind: Literal['paper', 'explanation']):
    _require_operator()
    return download_artifact(round_id, kind)
