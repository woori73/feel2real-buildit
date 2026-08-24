import json
import hashlib
import os
import re
import sqlite3
import struct
import zipfile
import zlib
import uuid
from datetime import datetime
from pathlib import Path
from typing import Literal, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel, ConfigDict, Field
from pydantic import ValidationError
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt

import prompt_logic as pl
import ai_router
import question_contract as qc

DB_PATH = os.getenv('BUILDIT_WIKI_DB_PATH', '/workspace/data/app.db')
WORKSPACE = Path(os.getenv('BUILDIT_WIKI_WORKSPACE', '/workspace'))
EXPORT_PATH = WORKSPACE / 'feel-to-real-project-export.zip'


def get_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with get_db() as conn:
        conn.execute('''CREATE TABLE IF NOT EXISTS studio_runs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            role TEXT NOT NULL,
            material_type TEXT NOT NULL,
            title TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT '완료',
            created_at TEXT NOT NULL
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS digital_twin_profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            school TEXT NOT NULL,
            grade TEXT NOT NULL DEFAULT '',
            exam TEXT NOT NULL DEFAULT '',
            profile_json TEXT NOT NULL,
            version INTEGER NOT NULL DEFAULT 1,
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS digital_twin_sources (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            school TEXT NOT NULL,
            grade TEXT NOT NULL,
            exam TEXT NOT NULL,
            material_type TEXT NOT NULL,
            source_filename TEXT NOT NULL,
            source_hash TEXT NOT NULL UNIQUE,
            parser TEXT NOT NULL,
            raw_text TEXT NOT NULL,
            analysis_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS digital_twin_question_dna (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id INTEGER NOT NULL,
            question_key TEXT NOT NULL,
            question_no INTEGER NOT NULL,
            dna_json TEXT NOT NULL,
            source_reference TEXT NOT NULL,
            source_hash TEXT NOT NULL,
            created_at TEXT NOT NULL,
            UNIQUE(source_id, question_key),
            FOREIGN KEY (source_id) REFERENCES digital_twin_sources(id)
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS generated_questions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            twin_id INTEGER,
            school TEXT NOT NULL DEFAULT '',
            grade TEXT NOT NULL DEFAULT '',
            exam TEXT NOT NULL DEFAULT '',
            mode TEXT NOT NULL DEFAULT 'new',
            q_kind TEXT NOT NULL DEFAULT 'mc',
            question_type TEXT NOT NULL DEFAULT '',
            stem TEXT NOT NULL DEFAULT '',
            source_text TEXT NOT NULL DEFAULT '',
            options_json TEXT NOT NULL DEFAULT '[]',
            answer_json TEXT NOT NULL DEFAULT '[]',
            multi INTEGER NOT NULL DEFAULT 0,
            score INTEGER NOT NULL DEFAULT 0,
            conditions_json TEXT NOT NULL DEFAULT '[]',
            answer_format TEXT NOT NULL DEFAULT '',
            accepted_answers_json TEXT NOT NULL DEFAULT '[]',
            sample_answer TEXT NOT NULL DEFAULT '',
            grading_rule TEXT NOT NULL DEFAULT '',
            exam_title TEXT NOT NULL DEFAULT '',
            exam_group_id TEXT NOT NULL DEFAULT '',
            favorite INTEGER NOT NULL DEFAULT 0,
            raw_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL
        )''')
        # 내신 실전 제작: 강사 문제 DB에서 선택한 문항으로 구성한 시험지를 실제로 저장한다.
        conn.execute('''CREATE TABLE IF NOT EXISTS exam_papers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL DEFAULT '',
            academy_name TEXT NOT NULL DEFAULT '',
            teacher_name TEXT NOT NULL DEFAULT '',
            question_ids_json TEXT NOT NULL DEFAULT '[]',
            created_at TEXT NOT NULL
        )''')
        # 내신 실전 제작 작업 중인 시험지의 "현재 선택 상태"를 지속시키는 단일 draft 레코드.
        # React state에만 의존하지 않고, 새로고침/페이지 이동 후에도 선택 문항과 순서를 복원하기 위해 사용한다.
        conn.execute('''CREATE TABLE IF NOT EXISTS exam_draft (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            title TEXT NOT NULL DEFAULT '',
            academy_name TEXT NOT NULL DEFAULT '',
            teacher_name TEXT NOT NULL DEFAULT '',
            question_ids_json TEXT NOT NULL DEFAULT '[]',
            updated_at TEXT NOT NULL
        )''')
        # 학생 프로필: 이름/학교/학년/학기/시험(중간·기말)만 저장한다 (단순 유지 요구사항).
        conn.execute('''CREATE TABLE IF NOT EXISTS students (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            school TEXT NOT NULL DEFAULT '',
            grade TEXT NOT NULL DEFAULT '',
            semester TEXT NOT NULL DEFAULT '',
            exam_type TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        )''')
        # 학생 오답 관리: 학생별·시험별로 오답 문제 원문(=사용자가 최종 확인/수정한 추출 텍스트)과
        # 정답을 저장한다. 각 저장은 독립된 record이며(덮어쓰지 않고 계속 누적), student_id로
        # 학생과 연결한다.
        # source_text: 오답 사진/PDF에서 추출·정리된 원본 텍스트를 함께 보관한다 (마이그레이션으로 추가).
        # title/question_type: 강사 문제 DB와 유사한 목록/간략보기를 위한 표시용 라벨 (마이그레이션으로 추가).
        conn.execute('''CREATE TABLE IF NOT EXISTS student_wrong_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL,
            exam_label TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            question_type TEXT NOT NULL DEFAULT '',
            wrong_text TEXT NOT NULL DEFAULT '',
            answer_text TEXT NOT NULL DEFAULT '',
            source_text TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            FOREIGN KEY (student_id) REFERENCES students(id)
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS educator_insights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content TEXT NOT NULL,
            school TEXT NOT NULL DEFAULT '',
            grade TEXT NOT NULL DEFAULT '',
            student_context TEXT NOT NULL DEFAULT '',
            lesson_context TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS csat_question_sources (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            exam_kind TEXT NOT NULL CHECK (exam_kind IN ('half','full')),
            source_filename TEXT NOT NULL,
            exam_metadata_json TEXT NOT NULL DEFAULT '{}',
            parser TEXT NOT NULL,
            raw_text TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        )''')
        conn.execute('''CREATE TABLE IF NOT EXISTS csat_questions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id INTEGER NOT NULL,
            exam_kind TEXT NOT NULL,
            question_no INTEGER NOT NULL,
            csat_type_id TEXT NOT NULL,
            csat_type_name TEXT NOT NULL,
            passage TEXT NOT NULL DEFAULT '',
            prompt TEXT NOT NULL DEFAULT '',
            choices_json TEXT NOT NULL DEFAULT '[]',
            answer TEXT NOT NULL DEFAULT '',
            solution TEXT NOT NULL DEFAULT '',
            footnote TEXT NOT NULL DEFAULT '',
            group_label TEXT NOT NULL DEFAULT '',
            source_reference TEXT NOT NULL DEFAULT '',
            type_mismatch INTEGER NOT NULL DEFAULT 0,
            mismatch_reason TEXT NOT NULL DEFAULT '',
            raw_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            FOREIGN KEY (source_id) REFERENCES csat_question_sources(id)
        )''')
        conn.execute('CREATE INDEX IF NOT EXISTS idx_csat_questions_kind_no ON csat_questions(exam_kind, question_no)')
        conn.execute('CREATE INDEX IF NOT EXISTS idx_csat_questions_type ON csat_questions(csat_type_id)')
        conn.execute('''CREATE TABLE IF NOT EXISTS csat_wrong_answers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_name TEXT NOT NULL,
            exam_name TEXT NOT NULL DEFAULT '',
            question_no INTEGER NOT NULL,
            csat_type_id TEXT NOT NULL,
            csat_type_name TEXT NOT NULL,
            wrong_answer TEXT NOT NULL DEFAULT '',
            correct_answer TEXT NOT NULL DEFAULT '',
            memo TEXT NOT NULL DEFAULT '',
            source_filename TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        )''')
        conn.execute('CREATE INDEX IF NOT EXISTS idx_csat_wrong_lookup ON csat_wrong_answers(student_name, exam_name, question_no, csat_type_id)')
        conn.execute('''CREATE TABLE IF NOT EXISTS ai_ab_evaluations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            school TEXT NOT NULL DEFAULT '', grade TEXT NOT NULL DEFAULT '', exam TEXT NOT NULL DEFAULT '',
            twin_id INTEGER, question_type TEXT NOT NULL DEFAULT '',
            gemini_provider TEXT NOT NULL, gemini_model TEXT NOT NULL, gemini_result_id TEXT NOT NULL,
            ox_provider TEXT NOT NULL, ox_model TEXT NOT NULL, ox_result_id TEXT NOT NULL,
            choice TEXT NOT NULL CHECK (choice IN ('gemini','ox-alpha','both-revise')),
            created_at TEXT NOT NULL
        )''')


def migrate_schema():
    """기존에 만들어져 있던 DB에는 없는 컬럼을 안전하게 추가한다.
    기존 데이터/테이블은 그대로 두고 누락된 컬럼만 ALTER TABLE로 보강한다 (데이터 삭제·재생성 없음)."""
    with get_db() as conn:
        cols = {row['name'] for row in conn.execute("PRAGMA table_info(student_wrong_notes)").fetchall()}
        if 'source_text' not in cols:
            conn.execute("ALTER TABLE student_wrong_notes ADD COLUMN source_text TEXT NOT NULL DEFAULT ''")
        if 'title' not in cols:
            conn.execute("ALTER TABLE student_wrong_notes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        if 'question_type' not in cols:
            conn.execute("ALTER TABLE student_wrong_notes ADD COLUMN question_type TEXT NOT NULL DEFAULT ''")
        source_cols = {row['name'] for row in conn.execute("PRAGMA table_info(csat_question_sources)").fetchall()}
        for name, definition in (
            ('source_hash', "TEXT NOT NULL DEFAULT ''"),
            ('original_stored', 'INTEGER NOT NULL DEFAULT 0'),
            ('owner_scope', "TEXT NOT NULL DEFAULT 'shared'"),
        ):
            if name not in source_cols:
                conn.execute(f'ALTER TABLE csat_question_sources ADD COLUMN {name} {definition}')
        conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_csat_source_hash_kind ON csat_question_sources(source_hash, exam_kind) WHERE source_hash <> ''")


def seed_default_twins():
    """기존 HTML에 내장되어 있던 6개 학교(신사중3, 신구중2, 신구중3, 압구정중2, 압구정중3, 언북중3)
    Digital Twin을 기본 데이터로 자동 Insert한다. 이미 같은 학교·학년의 Digital Twin이 있으면
    건드리지 않고, 없는 학교만 채워 넣는다 (기존 학교/업로드 자료를 삭제하거나 덮어쓰지 않음).
    이후 HTML 업로드는 이 6개를 지우지 않고 새 학교만 추가한다."""
    with get_db() as conn:
        now = datetime.now().strftime('%Y.%m.%d %H:%M')
        for profile in pl.DEFAULT_SCHOOL_PROFILES:
            existing = conn.execute(
                'SELECT id FROM digital_twin_profiles WHERE is_active = 1 AND school = ? AND grade = ?',
                (profile['school'], profile['grade']),
            ).fetchone()
            if existing:
                continue
            types = [
                {'name': pl.TYPE_DEF_BY_ID[type_id]['label'], 'description': pl.TYPE_DEF_BY_ID[type_id]['prompt'], 'ratio': '', 'difficulty': ''}
                for type_id in profile['types'] if type_id in pl.TYPE_DEF_BY_ID
            ]
            data = {
                'school': profile['school'], 'grade': profile['grade'], 'exam': '전체',
                'dna': profile['desc'],
                'description': f"{profile['school']} {profile['grade']} 기본 Digital Twin (기존 HTML 기준 데이터)",
                'types': types,
                'rules': ['기존 HTML의 학교 출제 DNA와 유형 순서를 그대로 따른다.', '같은 소재·같은 문장으로 문항을 반복하지 않는다.'],
                'difficulty': pl.DIFFICULTY_LABELS['actual'],
                'questionRatio': {}, 'trapPatterns': [], 'choiceStyle': '', 'writingStyle': '',
                'updatedAt': now, 'rawSource': '',
            }
            conn.execute(
                'INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?,?,?,?,?,1,?,?)',
                (data['school'], data['grade'], data['exam'], json.dumps(data, ensure_ascii=False), 1, now, now),
            )


app = FastAPI()
init_db()
migrate_schema()
seed_default_twins()


class StudioRun(BaseModel):
    role: str
    material_type: str
    title: str


@app.get('/api/health')
def health():
    return {'ok': True}


@app.get('/api/ai/providers')
def ai_providers():
    """Return capability flags only. Secret values never leave the server."""
    return ai_router.availability()


@app.get('/api/ai/openrouter/free-models')
def openrouter_models():
    """Public-safe model metadata only; the OpenRouter credential remains server-side."""
    return {'models': ai_router.openrouter_free_models(), 'autoModel': ai_router.FREE_ROUTER}


@app.get('/api/runs')
def list_runs():
    with get_db() as conn:
        rows = conn.execute('SELECT * FROM studio_runs ORDER BY id DESC LIMIT 20').fetchall()
        return [dict(row) for row in rows]


@app.post('/api/runs')
def create_run(run: StudioRun):
    if not run.title.strip():
        raise HTTPException(status_code=400, detail='자료 이름을 입력해 주세요.')
    created_at = datetime.now().strftime('%Y.%m.%d %H:%M')
    with get_db() as conn:
        cursor = conn.execute('INSERT INTO studio_runs (role, material_type, title, created_at) VALUES (?, ?, ?, ?)', (run.role, run.material_type, run.title.strip(), created_at))
        row = conn.execute('SELECT * FROM studio_runs WHERE id = ?', (cursor.lastrowid,)).fetchone()
        return dict(row)


# ==================================================================================
# Educator Brain — 수업 직후의 관찰과 판단을 다음 제작 작업에서 다시 쓸 수 있게 축적한다.
# 이번 범위는 기록 생성과 최근 기록 조회다. 기존 기록 수정/삭제는 별도 관리 UX가 생길 때 추가한다.
# ==================================================================================

class EducatorInsightCreate(BaseModel):
    content: str
    school: str = ''
    grade: str = ''
    student_context: str = ''
    lesson_context: str = ''


@app.post('/api/educator-insights', status_code=201)
def create_educator_insight(payload: EducatorInsightCreate):
    content = payload.content.strip()
    if not content:
        raise HTTPException(status_code=400, detail='수업에서 발견한 내용을 입력해 주세요.')
    if len(content) > 10000:
        raise HTTPException(status_code=400, detail='수업 인사이트는 10,000자까지 기록할 수 있습니다.')
    now = datetime.now().isoformat()
    with get_db() as conn:
        cursor = conn.execute(
            '''INSERT INTO educator_insights
               (content, school, grade, student_context, lesson_context, created_at)
               VALUES (?,?,?,?,?,?)''',
            (
                content, payload.school.strip()[:100], payload.grade.strip()[:50],
                payload.student_context.strip()[:200], payload.lesson_context.strip()[:200], now,
            ),
        )
        row = conn.execute('SELECT * FROM educator_insights WHERE id = ?', (cursor.lastrowid,)).fetchone()
        return dict(row)


@app.get('/api/educator-insights')
def list_educator_insights(limit: int = 5):
    safe_limit = max(1, min(limit, 20))
    with get_db() as conn:
        rows = conn.execute('SELECT * FROM educator_insights ORDER BY id DESC LIMIT ?', (safe_limit,)).fetchall()
        return [dict(row) for row in rows]


# ==================================================================================
# Digital Twin — HTML 업로드 기반 (AI 분석/Digital Twin 자동 생성 기능은 완전히 삭제됨)
# ==================================================================================

def sanitize_profile_text(value: object) -> str:
    if not isinstance(value, str):
        return ''
    cleaned = re.sub(r'<style[\s\S]*?</style>|<script[\s\S]*?</script>', ' ', value, flags=re.IGNORECASE)
    cleaned = re.sub(r':root\s*\{[^}]*\}|--[\w-]+\s*:[^;{}]+;?', ' ', cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r'<[^>]*>', ' ', cleaned)
    cleaned = re.sub(r'\s+', ' ', cleaned).strip()
    if re.search(r'(^|\s)(:root|--[\w-]+|\.\w+\s*\{|@media|display\s*:|color\s*:|background\s*:|학교명 미확인)', cleaned, re.IGNORECASE):
        return ''
    return cleaned


def sanitize_profile_data(data: dict) -> dict:
    cleaned = dict(data)
    cleaned['school'] = sanitize_profile_text(cleaned.get('school')) or '업로드 자료'
    for key in ('grade', 'exam', 'dna', 'description', 'difficulty', 'updatedAt', 'choiceStyle', 'writingStyle'):
        cleaned[key] = sanitize_profile_text(cleaned.get(key))
    cleaned['rules'] = [sanitize_profile_text(rule) for rule in cleaned.get('rules', []) if sanitize_profile_text(rule)]
    cleaned['trapPatterns'] = [sanitize_profile_text(rule) for rule in cleaned.get('trapPatterns', []) if sanitize_profile_text(rule)]
    cleaned['types'] = [
        {**item, 'name': sanitize_profile_text(item.get('name')), 'description': sanitize_profile_text(item.get('description')), 'ratio': sanitize_profile_text(item.get('ratio')), 'difficulty': sanitize_profile_text(item.get('difficulty'))}
        for item in cleaned.get('types', []) if isinstance(item, dict) and sanitize_profile_text(item.get('name'))
    ]
    return cleaned


class ImportedTwinProfile(BaseModel):
    school: str
    grade: str = ''
    exam: str = ''
    dna: str = ''
    description: str = ''
    types: list[dict] = []
    rules: list[str] = []
    difficulty: str = ''
    questionRatio: dict = {}
    trapPatterns: list[str] = []
    choiceStyle: str = ''
    writingStyle: str = ''
    updatedAt: str = ''
    rawSource: str = ''


def digital_twin_response(row: sqlite3.Row) -> dict:
    """Return the persisted row contract, not potentially stale profile_json metadata."""
    profile = sanitize_profile_data(json.loads(row['profile_json']))
    return {
        **profile,
        'id': row['id'],
        'school': sanitize_profile_text(row['school']) or profile.get('school', ''),
        'grade': sanitize_profile_text(row['grade']),
        'exam': sanitize_profile_text(row['exam']),
        'version': row['version'],
        'updatedAt': row['updated_at'],
    }


@app.get('/api/digital-twins')
def list_digital_twins(school: Optional[str] = None, grade: Optional[str] = None, exam: Optional[str] = None):
    query = 'SELECT * FROM digital_twin_profiles WHERE is_active = 1'
    values: list[str] = []
    if school:
        query += ' AND school = ?'
        values.append(school)
    if grade:
        query += ' AND grade = ?'
        values.append(grade)
    if exam:
        query += ' AND exam = ?'
        values.append(exam)
    query += ' ORDER BY updated_at DESC'
    with get_db() as conn:
        rows = conn.execute(query, values).fetchall()
        return [digital_twin_response(row) for row in rows]


@app.get('/api/digital-twins/{twin_id}')
def get_digital_twin(twin_id: int):
    with get_db() as conn:
        row = conn.execute('SELECT * FROM digital_twin_profiles WHERE id = ?', (twin_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail='Digital Twin을 찾지 못했습니다.')
    return digital_twin_response(row)


@app.post('/api/digital-twins/import')
def import_digital_twin(profile: ImportedTwinProfile):
    """강사가 만든 Digital Twin HTML을 업로드하면 파싱된 정보를 저장한다.
    이 저장을 기준으로 학교 → 학년 → 시험 계층이 자동으로 생성된다 (별도 관리자 입력 불필요)."""
    now = datetime.now().strftime('%Y.%m.%d %H:%M')
    data = sanitize_profile_data(profile.model_dump())
    data['school'] = data['school'] or '업로드 자료'
    data['types'] = data['types'] or [{'name': '기출 분석 대기', 'description': '업로드 자료의 문항 분석이 필요합니다.', 'ratio': '', 'difficulty': ''}]
    data['rules'] = data['rules'] or ['업로드 자료의 문항 구성과 난이도를 기준으로 생성']
    data['updatedAt'] = now
    data['rawSource'] = (data.get('rawSource') or '')[:50000]
    with get_db() as conn:
        latest = conn.execute('SELECT COALESCE(MAX(version), 0) AS version FROM digital_twin_profiles WHERE school = ? AND grade = ? AND exam = ?', (data['school'], data['grade'], data['exam'])).fetchone()
        version = int(latest['version']) + 1
        conn.execute('UPDATE digital_twin_profiles SET is_active = 0 WHERE school = ? AND grade = ? AND exam = ?', (data['school'], data['grade'], data['exam']))
        cursor = conn.execute('INSERT INTO digital_twin_profiles (school, grade, exam, profile_json, version, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)', (data['school'], data['grade'], data['exam'], json.dumps(data, ensure_ascii=False), version, now, now))
        return {**data, 'id': cursor.lastrowid, 'version': version}


@app.delete('/api/digital-twins/{twin_id}')
def delete_digital_twin(twin_id: int):
    with get_db() as conn:
        conn.execute('UPDATE digital_twin_profiles SET is_active = 0 WHERE id = ?', (twin_id,))
    return {'ok': True}


@app.get('/api/schools')
def school_hierarchy():
    """기존 SQLite 전체 기록에서 학교 → 학년 → 시험 계층을 만든다.
    Digital Twin만 조회하던 이전 구현 때문에 학생/문제 기록의 학교가 새 UI에서 누락됐다."""
    with get_db() as conn:
        twin_rows = conn.execute('SELECT id, school, grade, exam, version, updated_at FROM digital_twin_profiles WHERE is_active = 1').fetchall()
        question_rows = conn.execute("SELECT DISTINCT school, grade, exam FROM generated_questions WHERE TRIM(school) <> ''").fetchall()
        student_rows = conn.execute("SELECT DISTINCT school, grade, exam_type AS exam FROM students WHERE TRIM(school) <> ''").fetchall()
    tree: dict[str, dict[str, dict]] = {}
    for row in twin_rows:
        school = sanitize_profile_text(row['school']) or '학교명 미확인'
        grade = sanitize_profile_text(row['grade']) or '학년 미지정'
        exam = sanitize_profile_text(row['exam']) or '시험 미지정'
        tree.setdefault(school, {}).setdefault(grade, {})
        tree[school][grade][exam] = {'twinId': row['id'], 'version': row['version'], 'updatedAt': row['updated_at'], 'hasDigitalTwin': True}
    for row in [*question_rows, *student_rows]:
        school = sanitize_profile_text(row['school']) or '학교명 미확인'
        grade = sanitize_profile_text(row['grade']) or '학년 미지정'
        exam = sanitize_profile_text(row['exam']) or '시험 미지정'
        tree.setdefault(school, {}).setdefault(grade, {}).setdefault(
            exam, {'twinId': None, 'version': None, 'updatedAt': '', 'hasDigitalTwin': False}
        )
    result = []
    for school, grades in tree.items():
        grade_list = []
        for grade, exams in grades.items():
            exam_list = [{'exam': exam, **info} for exam, info in exams.items()]
            grade_list.append({'grade': grade, 'exams': exam_list})
        result.append({'school': school, 'grades': grade_list})
    return result


# ==================================================================================
# BuildIt Assistant — 기존 Wiki 기억을 최소 범위로 조회하는 대화형 작업 안내.
# 세션 state/history는 브라우저가 보유하며 서버는 DB를 수정하지 않는다.
# ==================================================================================

class AssistantState(BaseModel):
    goal: str = ''
    school: str = ''
    grade: str = ''
    exam: str = ''
    selected_twin_id: Optional[int] = None
    needed_materials: list[str] = Field(default_factory=list)
    recent_request: str = ''
    current_page: str = 'state'


class AssistantMessage(BaseModel):
    role: Literal['user', 'assistant']
    content: str


class AssistantAttachment(BaseModel):
    name: str
    type: str = ''


class AssistantRequest(BaseModel):
    message: str
    api_key: str = ''
    openrouter_api_key: str = ''
    provider: str = 'auto'
    state: AssistantState = Field(default_factory=AssistantState)
    history: list[AssistantMessage] = Field(default_factory=list)
    attachments: list[AssistantAttachment] = Field(default_factory=list)


def _assistant_entities(message: str, state: AssistantState) -> dict:
    school_match = re.search(r'([가-힣A-Za-z0-9]+(?:중학교|중|고등학교|고))(?=\s|\d|$)', message)
    grade_match = re.search(r'(?:중\s*)?([1-3])\s*학년', message) or re.search(r'(?:중학교|중)\s*([1-3])(?=\s|학년|문제|시험|$)', message)
    exam_match = re.search(r'((?:1|2)학기\s*)?(중간고사|기말고사|중간|기말)', message)
    school = school_match.group(1) if school_match else state.school
    grade = f'중{grade_match.group(1)}' if grade_match else state.grade
    exam = (exam_match.group(0).replace(' ', '') if exam_match else state.exam)
    if exam == '중간': exam = '중간고사'
    if exam == '기말': exam = '기말고사'
    goal = state.goal
    if re.search(r'문제|시험지|내신', message): goal = '학교별 내신 문제 제작'
    elif re.search(r'오답|학생', message): goal = '학생 오답 정리'
    elif re.search(r'인사이트|수업\s*기록', message): goal = '수업 인사이트 기록'
    elif re.search(r'Digital\s*Twin|디지털\s*트윈|기출\s*분석', message, re.I): goal = 'Digital Twin 구축'
    return {'school': school, 'grade': grade, 'exam': exam, 'goal': goal or '요청한 작업 준비'}


def _assistant_grade_number(grade: str) -> str:
    """UI/대화의 '중3'과 Digital Twin 저장값 '3학년'을 같은 학년으로 조회한다."""
    match = re.fullmatch(r'(?:중)?\s*([1-3])\s*(?:학년)?', (grade or '').strip())
    return match.group(1) if match else ''


def _assistant_context(entities: dict) -> dict:
    school, grade, exam = entities['school'], entities['grade'], entities['exam']
    grade_number = _assistant_grade_number(grade)
    context = {'twin': None, 'availableExams': [], 'questionTypes': [], 'questionCount': 0, 'studentCount': 0, 'insightCount': 0}
    if not school:
        return context
    with get_db() as conn:
        twin_query = 'SELECT * FROM digital_twin_profiles WHERE is_active=1 AND school=?'
        twin_values: list = [school]
        if grade_number:
            twin_query += " AND REPLACE(REPLACE(grade, '중', ''), '학년', '')=?"; twin_values.append(grade_number)
        if exam:
            twin_query += " AND (exam=? OR exam='전체')"; twin_values.append(exam)
        twin_query += " ORDER BY CASE WHEN exam=? THEN 0 ELSE 1 END, updated_at DESC LIMIT 1"
        twin_values.append(exam)
        twin = conn.execute(twin_query, twin_values).fetchone()
        if twin:
            context['twin'] = digital_twin_response(twin)
        exam_rows = conn.execute(
            "SELECT DISTINCT exam FROM digital_twin_profiles WHERE is_active=1 AND school=? AND (? = '' OR REPLACE(REPLACE(grade, '중', ''), '학년', '')=?) ORDER BY exam",
            (school, grade_number, grade_number),
        ).fetchall()
        context['availableExams'] = [row['exam'] for row in exam_rows if row['exam']]
        if context['twin']:
            context['questionTypes'] = [item.get('name', '') if isinstance(item, dict) else str(item) for item in context['twin'].get('types', []) if item]
        filters, values = ['school=?'], [school]
        if grade_number: filters.append("REPLACE(REPLACE(grade, '중', ''), '학년', '')=?"); values.append(grade_number)
        if exam: filters.append('exam=?'); values.append(exam)
        context['questionCount'] = conn.execute(f"SELECT COUNT(*) n FROM generated_questions WHERE {' AND '.join(filters)}", values).fetchone()['n']
        student_filters, student_values = ['school=?'], [school]
        if grade_number: student_filters.append("REPLACE(REPLACE(grade, '중', ''), '학년', '')=?"); student_values.append(grade_number)
        context['studentCount'] = conn.execute(f"SELECT COUNT(*) n FROM students WHERE {' AND '.join(student_filters)}", student_values).fetchone()['n']
        insight_filters, insight_values = ['school=?'], [school]
        if grade_number: insight_filters.append("REPLACE(REPLACE(grade, '중', ''), '학년', '')=?"); insight_values.append(grade_number)
        context['insightCount'] = conn.execute(f"SELECT COUNT(*) n FROM educator_insights WHERE {' AND '.join(insight_filters)}", insight_values).fetchone()['n']
    return context


def _attachment_route(attachments: list[AssistantAttachment], message: str, goal: str) -> tuple[str, str]:
    names = ' '.join(item.name.lower() for item in attachments)
    if re.search(r'\.(html?|json|txt)$', names): return 'twin', 'Digital Twin 가져오기'
    if re.search(r'오답|학생', message + goal): return 'wrongnote', '학생 오답 OCR 열기'
    if attachments and re.search(r'\.(pdf|png|jpe?g|hwp|hwpx|docx)$', names): return 'school', '학교 프린트 OCR 열기'
    if goal == '학교별 내신 문제 제작': return 'home', '문제 제작 화면 열기'
    if goal == '수업 인사이트 기록': return 'brain', '수업 인사이트 열기'
    if goal == 'Digital Twin 구축': return 'twin', 'Digital Twin 열기'
    return 'state', '현재 상태 보기'


def _assistant_fallback(entities: dict, context: dict, attachments: list[AssistantAttachment]) -> tuple[str, list[str]]:
    school, grade, exam, goal = entities['school'], entities['grade'], entities['exam'], entities['goal']
    target = ' '.join(part for part in (school, grade, exam) if part) or '현재 요청'
    lines = [f'{target}의 목표를 “{goal}”으로 정리했습니다.']
    twin = context['twin']
    if school and twin:
        type_text = ', '.join(context['questionTypes'][:5]) or '저장된 학교별 출제 패턴'
        exam_text = ', '.join(context['availableExams']) or '시험 미지정'
        lines.append(f'{school} {grade} Digital Twin을 확인했습니다. 연결 가능한 시험은 {exam_text}, 출제 유형은 {type_text}입니다. 저장 문제 {context["questionCount"]}개와 학생·수업 기록도 연결할 수 있습니다.')
    elif school:
        lines.append(f'{school} {grade}에 연결된 Digital Twin을 찾지 못했습니다. 기존 학교 기록은 보존한 채 기출 또는 Digital Twin 자료를 먼저 추가할 수 있습니다.')
    else:
        lines.append('연결할 기억을 찾으려면 학교·학년·시험을 알려 주세요.')
    needed = []
    if goal == '학교별 내신 문제 제작':
        if not exam: needed.append('시험')
        if not attachments: needed.extend(['시험 범위 지문 또는 학교 프린트', '시험 범위 주요 문법 사항'])
    if attachments:
        lines.append(f'첨부한 {", ".join(item.name for item in attachments)}은 기존 업로드 도구에서 처리하도록 연결하겠습니다. 파일 내용은 이 대화 API에 저장하지 않습니다.')
    elif needed:
        if goal == '학교별 내신 문제 제작':
            lines.append('이번 시험 범위 지문이나 학교 프린트와 시험 범위 주요 문법 사항이 필요합니다.' + (' 시험도 선택해 주세요.' if not exam else '') + ' 알려 주시면 저장된 출제 유형을 바로 적용하겠습니다.')
        else:
            lines.append('아직 필요한 것은 ' + ', '.join(needed) + '입니다.')
    if goal == '학교별 내신 문제 제작':
        lines.append('준비가 되면 학교·학년·Twin이 채워진 문제 제작 화면을 열겠습니다.')
    return '\n\n'.join(lines), needed


@app.post('/api/assistant/chat')
def assistant_chat(payload: AssistantRequest):
    message = payload.message.strip()
    if not message and not payload.attachments:
        raise HTTPException(status_code=400, detail='요청이나 첨부 자료를 입력해 주세요.')
    entities = _assistant_entities(message, payload.state)
    context = _assistant_context(entities)
    fallback, needed = _assistant_fallback(entities, context, payload.attachments)
    route, action = _attachment_route(payload.attachments, message, entities['goal'])
    text, model = fallback, 'rules-fallback'
    if payload.api_key.strip() or payload.openrouter_api_key.strip() or os.getenv('GEMINI_API_KEY', '').strip() or os.getenv('OPENROUTER_API_KEY', '').strip():
        minimal_context = json.dumps({'goal': entities['goal'], 'school': entities['school'], 'grade': entities['grade'], 'exam': entities['exam'], **context, 'attachments': [a.name for a in payload.attachments]}, ensure_ascii=False)
        recent = payload.history[-6:]
        prompt = f'확인된 컨텍스트: {minimal_context}\n최근 대화: ' + json.dumps([m.model_dump() for m in recent], ensure_ascii=False) + f'\n사용자 요청: {message}\n부족한 항목: {json.dumps(needed, ensure_ascii=False)}. 확인된 사실만 사용해 자연스럽게 답하고, 정적 기능 목록을 반복하지 마라.'
        try:
            result = ai_router.generate(payload.provider, '너는 BuildIt Wiki의 한국어 작업 Assistant다. 링크만 답하지 말고 목표를 요약하고, 확인된 기억과 부족한 자료, 작업 순서를 짧고 친절하게 설명한다. 제공된 최소 컨텍스트 밖의 DB 사실은 추측하지 않는다. 학생 이름·연락처 등 개인정보를 요청하거나 추측하지 않는다. DB 수정이나 저장을 완료했다고 말하지 않는다.', prompt, temperature=0.25, allow_noncritical_fallback=True)
            text, model = result.text, result.provider
        except Exception as exc:
            if payload.provider in ('gemini', 'ox-alpha'):
                _handle_gemini_error(exc)
    next_state = {**payload.state.model_dump(), **entities, 'selected_twin_id': context['twin']['id'] if context['twin'] else None, 'needed_materials': needed, 'recent_request': message}
    return {'text': text, 'state': next_state, 'context': context, 'page': route, 'action': action, 'provider': result.provider if 'result' in locals() else 'rules-fallback', 'model': result.model if 'result' in locals() else model}


# ==================================================================================
# 문제 생성 / 기존 문제 변형 — 기존 HTML의 AI 프롬프트 로직을 그대로 사용 (prompt_logic.py)
# ==================================================================================

def _load_twin(conn, twin_id: Optional[int], school: Optional[str], grade: Optional[str], exam: Optional[str]):
    row = None
    if twin_id:
        row = conn.execute('SELECT * FROM digital_twin_profiles WHERE id = ? AND is_active = 1', (twin_id,)).fetchone()
    elif school:
        query = 'SELECT * FROM digital_twin_profiles WHERE is_active = 1 AND school = ?'
        values = [school]
        if grade:
            query += ' AND grade = ?'
            values.append(grade)
        if exam:
            query += ' AND exam = ?'
            values.append(exam)
        query += ' ORDER BY updated_at DESC LIMIT 1'
        row = conn.execute(query, values).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail='선택한 학교·학년·시험의 Digital Twin을 찾지 못했습니다. Digital Twin 관리에서 HTML을 먼저 업로드해 주세요.')
    return digital_twin_response(row)


def _save_generated(conn, twin: Optional[dict], mode: str, questions: list[dict], exam_title: str, exam_group_id: str):
    """생성/변형된 문항을 DB에 저장하고, 프런트엔드(GET /api/questions와 동일한 snake_case 형태)로
    정규화한 결과를 반환한다. AI 원본 응답의 camelCase 키를 그대로 돌려주면 프런트 표시가 깨지므로 여기서 통일한다."""
    now = datetime.now().isoformat()
    twin_school = twin.get('school', '') if twin else ''
    twin_grade = twin.get('grade', '') if twin else ''
    twin_exam = twin.get('exam', '') if twin else ''
    saved = []
    for q in questions:
        q_kind = q.get('type') or ('sa' if 'conditions' in q else 'mc')
        options = q.get('options', []) or []
        answer = q.get('answer', []) or []
        conditions = q.get('conditions', []) or []
        accepted_answers = q.get('acceptedAnswers', []) or []
        question_type = q.get('questionType', '')
        stem = q.get('stem', '')
        source_text = q.get('sourceText', '')
        multi = bool(q.get('multi'))
        score = int(q.get('score') or 0)
        answer_format = q.get('answerFormat', '')
        sample_answer = q.get('sampleAnswer', '')
        grading_rule = q.get('gradingRule', '')
        row_data = (
            twin.get('id') if twin else None, twin_school, twin_grade, twin_exam, mode, q_kind,
            question_type, stem, source_text,
            json.dumps(options, ensure_ascii=False), json.dumps(answer, ensure_ascii=False),
            1 if multi else 0, score,
            json.dumps(conditions, ensure_ascii=False), answer_format,
            json.dumps(accepted_answers, ensure_ascii=False), sample_answer, grading_rule,
            exam_title, exam_group_id, json.dumps(q, ensure_ascii=False), now,
        )
        cursor = conn.execute(
            '''INSERT INTO generated_questions
            (twin_id, school, grade, exam, mode, q_kind, question_type, stem, source_text, options_json, answer_json,
             multi, score, conditions_json, answer_format, accepted_answers_json, sample_answer, grading_rule,
             exam_title, exam_group_id, raw_json, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
            row_data,
        )
        saved.append({
            'id': cursor.lastrowid, 'twin_id': twin.get('id') if twin else None,
            'school': twin_school, 'grade': twin_grade, 'exam': twin_exam,
            'mode': mode, 'q_kind': q_kind, 'question_type': question_type,
            'stem': stem, 'source_text': source_text, 'options': options, 'answer': answer,
            'multi': 1 if multi else 0, 'score': score, 'conditions': conditions,
            'answer_format': answer_format, 'accepted_answers': accepted_answers,
            'sample_answer': sample_answer, 'grading_rule': grading_rule,
            'exam_title': exam_title, 'exam_group_id': exam_group_id, 'favorite': 0, 'created_at': now,
        })
    return saved


def _handle_gemini_error(exc):
    if isinstance(exc, ai_router.ProviderError):
        status = exc.status if exc.status in (401, 403, 429, 502, 503, 504) else 502
        message = str(exc)
        if status == 429:
            message = 'AI 요청이 많아 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.'
        elif status == 504:
            message = 'AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.'
        elif status in (502, 503) and exc.provider == 'ox-alpha':
            message = 'Ox Alpha 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.'
        raise HTTPException(status_code=status, detail={'message': message, 'provider': exc.provider, 'status': status})
    if isinstance(exc, pl.GeminiCallError):
        if exc.http_status in (401, 403):
            raise HTTPException(status_code=401, detail='잘못된 API Key입니다. 설정에서 키를 다시 확인해 주세요.')
        raise HTTPException(status_code=502, detail=f'AI 요청을 완료하지 못했습니다: {exc}')
    raise HTTPException(status_code=502, detail='AI 서비스 연결을 확인하지 못했습니다. 네트워크 상태를 확인해 주세요.')


def _parse_generated_json(text: str, repair):
    """Repair only malformed JSON, once. Schema/quality failures are never retried here."""
    try:
        return json.loads(re.sub(r'```json|```', '', text).strip())
    except (json.JSONDecodeError, TypeError):
        repaired = repair()
        try:
            return json.loads(re.sub(r'```json|```', '', repaired).strip())
        except (json.JSONDecodeError, TypeError):
            raise HTTPException(status_code=502, detail='AI 응답 JSON 파싱 실패(repair 1회 포함)')


def _contract_error(exc: ValidationError):
    raise HTTPException(status_code=502, detail='AI 응답 schema validation 실패: ' + qc.validation_fields(exc))


class NewQuestionRequest(BaseModel):
    api_key: str = ''
    openrouter_api_key: str = ''
    provider: str = 'auto'
    twin_id: Optional[int] = None
    school: Optional[str] = None
    grade: Optional[str] = None
    exam: Optional[str] = None
    passage: str
    grammar: str
    count: int = 10
    difficulty: Literal['actual', 'easy', 'hard', 'killer', 'writing'] = 'actual'
    composition: Literal['balanced', 'objective', 'writing'] = 'balanced'
    types: list[str] = []  # 사용자가 직접 유형을 지정하면 이 값을 사용, 비어 있으면 Digital Twin의 유형을 자동 적용


def _composition(total: int, mode: str):
    if mode == 'objective':
        sa = max(1, round(total * 0.15))
    elif mode == 'writing':
        sa = max(2, round(total * 0.35))
    else:
        sa = max(1, round(total * 0.20))
    return total - sa, sa


def _batches(total: int, mc: int, sa: int):
    arr = []
    left, start = total, 1
    while left > 0:
        size = min(5, left)
        bsa = min(sa, max(0, round(size * sa / left))) if left else 0
        if sa > 0 and left == size:
            bsa = sa
        bsa = min(bsa, size)
        bmc = size - bsa
        arr.append({'start': start, 'end': start + size - 1, 'mc': bmc, 'sa': bsa, 'size': size})
        start += size
        left -= size
        mc -= bmc
        sa -= bsa
    return arr


@app.post('/api/questions/generate')
def generate_questions(payload: NewQuestionRequest):
    if not payload.passage.strip() or not payload.grammar.strip():
        raise HTTPException(status_code=400, detail='새 영어 지문과 핵심 문법을 입력해 주세요.')
    with get_db() as conn:
        twin = _load_twin(conn, payload.twin_id, payload.school, payload.grade, payload.exam)
        type_labels = [
            f"{pl.TYPE_DEF_BY_ID[type_id]['label']} — {pl.TYPE_DEF_BY_ID[type_id]['prompt']}"
            for type_id in payload.types if type_id in pl.TYPE_DEF_BY_ID
        ] or None
        mc_total, sa_total = _composition(payload.count, payload.composition)
        batches = _batches(payload.count, mc_total, sa_total)
        all_questions: list[dict] = []
        previous = ''
        for batch in batches:
            rules = pl.build_new_rules(twin, {
                'type_labels': type_labels, 'difficulty': payload.difficulty,
                'start': batch['start'], 'end': batch['end'], 'mc': batch['mc'], 'sa': batch['sa'], 'previous': previous,
            })
            user_msg = f"[시험 범위 주요 문법—문법 문항과 서답형 문항에 최우선 반영]\n{payload.grammar}\n\n[새 지문—내용 일치 문제에서는 이 전체 지문을 제시할 것]\n{payload.passage}"
            batch_questions: list[dict] = []
            quality_issues: list[str] = []
            for quality_attempt in range(2):
                retry_rules = rules
                if quality_issues:
                    retry_rules += ' [직전 응답 품질 오류—모두 수정] ' + ' / '.join(quality_issues)
                try:
                    result = ai_router.generate(payload.provider, retry_rules, user_msg, schema=pl.schema_new(batch['mc'], batch['sa']), temperature=0.35, gemini_key=payload.api_key, openrouter_key=payload.openrouter_api_key)
                    text = result.text
                except Exception as exc:
                    _handle_gemini_error(exc)
                    return
                def repair_json():
                    repair_prompt = '다음 응답의 내용은 바꾸지 말고 유효한 JSON 객체로만 복구하라:\n' + text
                    return ai_router.generate(payload.provider, retry_rules, repair_prompt, schema=pl.schema_new(batch['mc'], batch['sa']), temperature=0, gemini_key=payload.api_key, openrouter_key=payload.openrouter_api_key).text
                parsed = _parse_generated_json(text, repair_json)
                try:
                    parsed = qc.validate_new(parsed)
                except ValidationError as exc:
                    _contract_error(exc)
                mc_qs = [pl.prepare_question({**q, 'type': 'mc'}) for q in parsed.get('mcQuestions', [])]
                sa_qs = []
                for q in parsed.get('saQuestions', []):
                    q['type'] = 'sa'
                    q['acceptedAnswers'] = q.get('acceptedAnswers') or [q.get('sampleAnswer', '')]
                    q['answerFormat'] = q.get('answerFormat') or '정답 한 개를 정확히 작성'
                    q['gradingRule'] = q.get('gradingRule') or '철자와 문법이 모범답안과 일치해야 함'
                    sa_qs.append(q)
                batch_questions = mc_qs + sa_qs
                for question in batch_questions:
                    question['grammarFocus'] = payload.grammar.strip()
                quality_issues = pl.batch_quality_issues(batch_questions)
                if not quality_issues:
                    break
            if quality_issues:
                raise HTTPException(status_code=502, detail='AI가 시험지 형식 검증을 통과하지 못했습니다: ' + '; '.join(quality_issues[:3]))
            all_questions.extend(batch_questions)
            previous = ' || '.join(f"{i+1}. {q.get('questionType','')} / {q.get('stem','')} / {(q.get('sourceText') or '')[:120]}" for i, q in enumerate(all_questions))[-7000:]
        exam_title = f"{twin.get('school','')} {twin.get('grade','')} 맞춤형 시험지".strip()
        exam_group_id = f"exam_{int(datetime.now().timestamp())}"
        saved = _save_generated(conn, twin, 'new', all_questions, exam_title, exam_group_id)
        return {'examTitle': exam_title, 'examGroupId': exam_group_id, 'questions': saved, 'provider': result.provider, 'model': result.model, 'twin': {'id': twin['id'], 'school': twin.get('school', ''), 'version': twin['version']}}


class AbComparisonRequest(NewQuestionRequest):
    provider: str = 'auto'


def _ab_generate(provider: str, payload: AbComparisonRequest, twin: dict) -> dict:
    mc, sa = _composition(payload.count, payload.composition)
    type_labels = [f"{pl.TYPE_DEF_BY_ID[t]['label']} — {pl.TYPE_DEF_BY_ID[t]['prompt']}" for t in payload.types if t in pl.TYPE_DEF_BY_ID] or None
    rules = pl.build_new_rules(twin, {'type_labels': type_labels, 'difficulty': payload.difficulty, 'start': 1, 'end': payload.count, 'mc': mc, 'sa': sa, 'previous': ''})
    prompt = f"[시험 범위 주요 문법]\n{payload.grammar}\n\n[새 지문]\n{payload.passage}"
    result = ai_router.generate(provider, rules, prompt, schema=pl.schema_new(mc, sa), temperature=0.35, gemini_key=payload.api_key, openrouter_key=payload.openrouter_api_key)
    parsed = json.loads(re.sub(r'```json|```', '', result.text).strip())
    questions = [pl.prepare_question({**q, 'type': 'mc'}) for q in parsed.get('mcQuestions', [])]
    questions += [{**q, 'type': 'sa'} for q in parsed.get('saQuestions', [])]
    issues = pl.batch_quality_issues(questions)
    if issues:
        raise ai_router.ProviderError('JSON 문제 구조 검증 실패: ' + '; '.join(issues[:3]), provider)
    return {'resultId': uuid.uuid4().hex, 'provider': result.provider, 'model': result.model, 'questions': questions}


@app.post('/api/questions/ab-compare')
def compare_questions(payload: AbComparisonRequest):
    if not payload.passage.strip() or not payload.grammar.strip():
        raise HTTPException(status_code=400, detail='새 영어 지문과 핵심 문법을 입력해 주세요.')
    with get_db() as conn:
        twin = _load_twin(conn, payload.twin_id, payload.school, payload.grade, payload.exam)
    results, errors = {}, {}
    for provider in ('gemini', 'ox-alpha'):
        try:
            results[provider] = _ab_generate(provider, payload, twin)
        except Exception as exc:
            errors[provider] = str(exc)
    if not results:
        raise HTTPException(status_code=502, detail='두 provider 모두 비교 결과를 생성하지 못했습니다.')
    return {'contextContract': {'school': twin.get('school', ''), 'grade': twin.get('grade', ''), 'exam': twin.get('exam', ''), 'twinId': twin['id'], 'types': payload.types, 'grammar': payload.grammar}, 'results': results, 'errors': errors}


class AbEvaluationCreate(BaseModel):
    school: str = ''
    grade: str = ''
    exam: str = ''
    twin_id: Optional[int] = None
    question_type: str = ''
    gemini_model: str
    gemini_result_id: str
    ox_model: str
    ox_result_id: str
    choice: Literal['gemini', 'ox-alpha', 'both-revise']


@app.post('/api/questions/ab-evaluations', status_code=201)
def save_ab_evaluation(payload: AbEvaluationCreate):
    now = datetime.now().isoformat()
    with get_db() as conn:
        cursor = conn.execute('''INSERT INTO ai_ab_evaluations
            (school,grade,exam,twin_id,question_type,gemini_provider,gemini_model,gemini_result_id,ox_provider,ox_model,ox_result_id,choice,created_at)
            VALUES (?,?,?,?,?,'gemini',?,?,'ox-alpha',?,?,?,?)''',
            (payload.school, payload.grade, payload.exam, payload.twin_id, payload.question_type, payload.gemini_model, payload.gemini_result_id, payload.ox_model, payload.ox_result_id, payload.choice, now))
        row = conn.execute('SELECT * FROM ai_ab_evaluations WHERE id=?', (cursor.lastrowid,)).fetchone()
        return dict(row)


@app.get('/api/questions/ab-evaluations')
def list_ab_evaluations(limit: int = 100):
    with get_db() as conn:
        return [dict(row) for row in conn.execute('SELECT * FROM ai_ab_evaluations ORDER BY id DESC LIMIT ?', (max(1, min(limit, 500)),)).fetchall()]


class TransformQuestionRequest(BaseModel):
    api_key: str = ''
    openrouter_api_key: str = ''
    provider: str = 'auto'
    twin_id: Optional[int] = None
    school: Optional[str] = None
    grade: Optional[str] = None
    exam: Optional[str] = None
    original: str
    passage: str = ''
    count: int = 3
    strength: Literal['light', 'normal', 'strong'] = 'normal'
    target: Literal['same', 'related', 'different'] = 'same'
    options: bool = True
    answerPos: bool = True
    harder: bool = False
    schoolStyle: bool = True
    desiredType: Optional[str] = None  # TYPE_DEFS id, 사용자가 "원하는 유형으로 변경"을 직접 선택한 경우


@app.get('/api/question-types')
def question_types():
    """자유 유형 선택 UI용 문제 유형 카탈로그 (기존 HTML TYPE_DEFS)."""
    return pl.TYPE_DEFS


def _recommend_type(twin: dict) -> tuple[Optional[str], Optional[str]]:
    """항목 10: 유형 미지정 시 Digital Twin에서 가장 비중이 높은 유형을 추천."""
    types = twin.get('types') or []
    if not types:
        return None, None

    def ratio_value(item):
        raw = str(item.get('ratio', '') or '')
        m = re.search(r'\d+', raw)
        return int(m.group()) if m else 0

    best = sorted(types, key=ratio_value, reverse=True)[0]
    return best.get('name'), best.get('description')


@app.post('/api/questions/transform')
def transform_questions(payload: TransformQuestionRequest):
    """항목 7~10: 기존 HTML '기존 문제 변형' 기능 이식.
    학교 선택 → Digital Twin DB 읽기 → 학교 출제 DNA 적용 → 문제 변형.
    유형을 지정하지 않으면 Digital Twin을 보고 자동 추천한다."""
    if not payload.original.strip():
        raise HTTPException(status_code=400, detail='기존 문제를 입력해 주세요.')
    with get_db() as conn:
        twin = _load_twin(conn, payload.twin_id, payload.school, payload.grade, payload.exam)
        cfg = {
            'strength': payload.strength, 'target': payload.target, 'count': payload.count,
            'options': payload.options, 'answerPos': payload.answerPos, 'harder': payload.harder, 'schoolStyle': payload.schoolStyle,
        }
        if payload.desiredType:
            type_def = pl.TYPE_DEF_BY_ID.get(payload.desiredType)
            if type_def:
                cfg['desiredTypeLabel'] = type_def['label']
                cfg['desiredTypePrompt'] = type_def['prompt']
            else:
                cfg['desiredTypeLabel'] = payload.desiredType
        elif payload.target != 'same':
            recommended_name, recommended_desc = _recommend_type(twin)
            if recommended_name:
                cfg['recommended'] = recommended_name + (f'({recommended_desc})' if recommended_desc else '')
        rules = pl.build_transform_rules(twin, cfg)
        user_msg = f"[원본 문제]\n{payload.original}\n\n[관련 전체 지문]\n{payload.passage or '제공되지 않음'}"
        try:
            provider_result = ai_router.generate(payload.provider, rules, user_msg, schema=pl.schema_transform(payload.count), temperature=0.35, gemini_key=payload.api_key, openrouter_key=payload.openrouter_api_key)
            text = provider_result.text
        except Exception as exc:
            _handle_gemini_error(exc)
            return
        def repair_json():
            repair_prompt = '다음 응답의 내용은 바꾸지 말고 유효한 JSON 객체로만 복구하라:\n' + text
            return ai_router.generate(payload.provider, rules, repair_prompt, schema=pl.schema_transform(payload.count), temperature=0, gemini_key=payload.api_key, openrouter_key=payload.openrouter_api_key).text
        data = _parse_generated_json(text, repair_json)
        try:
            data = qc.validate_transform(data)
        except ValidationError as exc:
            _contract_error(exc)
        questions = []
        for q in data.get('questions', []):
            q['type'] = 'sa' if q.get('type') == 'sa' else 'mc'
            if q['type'] == 'mc':
                q = pl.repair_transform_question(q)
            else:
                q['conditions'] = q.get('conditions') or []
                q['acceptedAnswers'] = q.get('acceptedAnswers') or []
            questions.append(q)
        quality_issues = pl.batch_quality_issues(questions)
        if quality_issues:
            raise HTTPException(status_code=502, detail='AI가 변형문제 조건 검증을 통과하지 못했습니다: ' + '; '.join(quality_issues[:3]))
        exam_title = data.get('examTitle') or f"{twin.get('school','')} 기존 문제 변형"
        exam_group_id = f"transform_{int(datetime.now().timestamp())}"
        saved = _save_generated(conn, twin, 'transform', questions, exam_title, exam_group_id)
        return {'examTitle': exam_title, 'examGroupId': exam_group_id, 'questions': saved, 'provider': provider_result.provider, 'model': provider_result.model, 'twin': {'id': twin['id'], 'school': twin.get('school', ''), 'version': twin['version']}}


# ==================================================================================
# 강사 문제 DB — SQLite 영구 저장 (React 상태/로컬스토리지 저장 금지)
# ==================================================================================

@app.get('/api/questions')
def list_questions(school: Optional[str] = None, grade: Optional[str] = None, exam: Optional[str] = None,
                    mode: Optional[str] = None, search: Optional[str] = None, limit: int = 200):
    query = 'SELECT * FROM generated_questions WHERE 1=1'
    values: list = []
    for field, value in (('school', school), ('grade', grade), ('exam', exam), ('mode', mode)):
        if value:
            query += f' AND {field} = ?'
            values.append(value)
    if search:
        query += ' AND (stem LIKE ? OR source_text LIKE ? OR question_type LIKE ?)'
        needle = f'%{search}%'
        values.extend([needle, needle, needle])
    query += ' ORDER BY id DESC LIMIT ?'
    values.append(max(1, min(limit, 1000)))
    with get_db() as conn:
        rows = conn.execute(query, values).fetchall()
    result = []
    for row in rows:
        item = dict(row)
        for jkey in ('options_json', 'answer_json', 'conditions_json', 'accepted_answers_json'):
            try:
                item[jkey.replace('_json', '')] = json.loads(item.pop(jkey))
            except (TypeError, json.JSONDecodeError):
                item[jkey.replace('_json', '')] = []
        item.pop('raw_json', None)
        result.append(item)
    return result


@app.delete('/api/questions/{question_id}')
def delete_question(question_id: int):
    with get_db() as conn:
        conn.execute('DELETE FROM generated_questions WHERE id = ?', (question_id,))
    return {'ok': True}


class FavoriteUpdate(BaseModel):
    favorite: bool


@app.patch('/api/questions/{question_id}/favorite')
def update_question_favorite(question_id: int, payload: FavoriteUpdate):
    with get_db() as conn:
        conn.execute('UPDATE generated_questions SET favorite = ? WHERE id = ?', (1 if payload.favorite else 0, question_id))
    return {'ok': True}


# ==================================================================================
# 내신 실전 제작 — 강사 문제 DB에서 선택한 문항으로 구성한 시험지를 실제로 저장한다.
# ==================================================================================

class ExamPaperCreate(BaseModel):
    title: str
    academy_name: str = ''
    teacher_name: str = ''
    question_ids: list[int] = []


@app.post('/api/exam-papers')
def create_exam_paper(payload: ExamPaperCreate):
    if not payload.question_ids:
        raise HTTPException(status_code=400, detail='저장할 문항을 강사 문제 DB에서 먼저 선택해 주세요.')
    now = datetime.now().isoformat()
    with get_db() as conn:
        rows = conn.execute(
            f"SELECT id FROM generated_questions WHERE id IN ({','.join('?' * len(payload.question_ids))})",
            payload.question_ids,
        ).fetchall()
        valid_ids = [row['id'] for row in rows]
        if not valid_ids:
            raise HTTPException(status_code=400, detail='선택한 문항을 강사 문제 DB에서 찾지 못했습니다.')
        cursor = conn.execute(
            'INSERT INTO exam_papers (title, academy_name, teacher_name, question_ids_json, created_at) VALUES (?,?,?,?,?)',
            (payload.title.strip() or '내신 실전 시험지', payload.academy_name.strip(), payload.teacher_name.strip(), json.dumps(valid_ids), now),
        )
        return {'id': cursor.lastrowid, 'title': payload.title, 'academy_name': payload.academy_name, 'teacher_name': payload.teacher_name, 'question_ids': valid_ids, 'created_at': now}


@app.get('/api/exam-papers')
def list_exam_papers():
    with get_db() as conn:
        rows = conn.execute('SELECT * FROM exam_papers ORDER BY id DESC').fetchall()
    result = []
    for row in rows:
        item = dict(row)
        item['question_ids'] = json.loads(item.pop('question_ids_json'))
        result.append(item)
    return result


# ==================================================================================
# 내신 실전 제작 — 현재 작업 중인 시험지의 선택 문항/순서를 DB에 지속시키는 draft.
# React state에만 두지 않고 새로고침·페이지 이동 후에도 그대로 복원되도록 한다.
# ==================================================================================

class ExamDraftUpdate(BaseModel):
    title: str = ''
    academy_name: str = ''
    teacher_name: str = ''
    question_ids: list[int] = []


@app.get('/api/exam-draft')
def get_exam_draft():
    with get_db() as conn:
        row = conn.execute('SELECT * FROM exam_draft WHERE id = 1').fetchone()
    if not row:
        return {'title': '', 'academy_name': '', 'teacher_name': '', 'question_ids': []}
    return {
        'title': row['title'], 'academy_name': row['academy_name'], 'teacher_name': row['teacher_name'],
        'question_ids': json.loads(row['question_ids_json']),
    }


@app.put('/api/exam-draft')
def save_exam_draft(payload: ExamDraftUpdate):
    now = datetime.now().isoformat()
    with get_db() as conn:
        conn.execute(
            '''INSERT INTO exam_draft (id, title, academy_name, teacher_name, question_ids_json, updated_at)
               VALUES (1, ?, ?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET
                 title = excluded.title, academy_name = excluded.academy_name,
                 teacher_name = excluded.teacher_name, question_ids_json = excluded.question_ids_json,
                 updated_at = excluded.updated_at''',
            (payload.title, payload.academy_name, payload.teacher_name, json.dumps(payload.question_ids), now),
        )
    return {'ok': True}


# ==================================================================================
# 내신 실전 제작 — 오피스 2007(.docx) 문제지/정답지 내보내기
# ==================================================================================

_UNDERLINE_RE = re.compile(r'\[\[u\]\](.*?)\[\[/u\]\]', re.DOTALL)
_OPTION_MARKS = ['①', '②', '③', '④', '⑤']


def _render_markup_segments(text: str):
    """[[u]]...[[/u]] 형태로 저장된 밑줄 마크업을 (텍스트, 밑줄여부) 세그먼트 목록으로 변환한다."""
    text = text or ''
    segments: list[tuple[str, bool]] = []
    pos = 0
    for m in _UNDERLINE_RE.finditer(text):
        if m.start() > pos:
            segments.append((text[pos:m.start()], False))
        segments.append((m.group(1), True))
        pos = m.end()
    if pos < len(text):
        segments.append((text[pos:], False))
    if not segments:
        segments.append(('', False))
    return [(seg.replace('[[u]]', '').replace('[[/u]]', ''), underline) for seg, underline in segments]


def _set_korean_font(run, name: str = '맑은 고딕'):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), name)


def _add_markup_run(paragraph, text: str, bold: bool = False):
    for seg_text, underline in _render_markup_segments(text):
        if not seg_text:
            continue
        run = paragraph.add_run(seg_text)
        run.bold = bold
        run.underline = underline
        _set_korean_font(run)
    return paragraph


def _set_columns(section, num: int = 1):
    """섹션 본문을 num단으로 설정한다 (1=단일 칼럼, 2=신문형 2단)."""
    sect_pr = section._sectPr
    cols = sect_pr.find(qn('w:cols'))
    if cols is None:
        cols = OxmlElement('w:cols')
        sect_pr.append(cols)
    cols.set(qn('w:num'), str(num))
    if num > 1:
        cols.set(qn('w:space'), '425')  # 칼럼 사이 간격(약 0.3인치)
    elif qn('w:space') in cols.attrib:
        del cols.attrib[qn('w:space')]


def _configure_a4_section(section, columns: int = 1):
    """문제11(6차): A4 용지 크기와 여백을 명시적으로 지정한다 (python-docx 기본값은 Letter이므로
    A4로 맞춰야 한다). 문제지 본문은 2단, 표지/정답 섹션은 1단으로 재사용한다."""
    section.page_width = Mm(210)
    section.page_height = Mm(297)
    section.top_margin = Mm(15)
    section.bottom_margin = Mm(15)
    section.left_margin = Mm(14)
    section.right_margin = Mm(14)
    _set_columns(section, columns)


def _new_docx_with_korean_style() -> Document:
    doc = Document()
    normal = doc.styles['Normal']
    normal.font.name = '맑은 고딕'
    normal.font.size = Pt(10)
    rpr = normal.element.get_or_add_rPr()
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), '맑은 고딕')
    _configure_a4_section(doc.sections[0], columns=1)
    return doc


def _start_section(doc: Document, columns: int, new_page: bool):
    """문제11(6차): 표지(1단) → 본문(2단) → 정답(1단, 새 페이지)처럼 구간별로 칼럼 수가
    다른 섹션을 이어붙인다. new_page=True면 항상 새 페이지에서 시작한다(명시적 page_break 불필요)."""
    section = doc.add_section(WD_SECTION.NEW_PAGE if new_page else WD_SECTION.CONTINUOUS)
    _configure_a4_section(section, columns=columns)
    return section


def _add_doc_heading(doc: Document, title: str, subtitle: str, label: str):
    heading = doc.add_paragraph()
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    heading_run = heading.add_run(title or '문제지')
    heading_run.bold = True
    heading_run.font.size = Pt(18)
    _set_korean_font(heading_run)

    if subtitle:
        sub = doc.add_paragraph()
        sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
        sub_run = sub.add_run(subtitle)
        sub_run.font.size = Pt(11)
        _set_korean_font(sub_run)

    label_p = doc.add_paragraph()
    label_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    label_run = label_p.add_run(label)
    label_run.bold = True
    _set_korean_font(label_run)


def _build_question_section(doc: Document, questions: list[dict]):
    """문제지 섹션. 객관식 선택지는 ①~⑤ 각각 별도 문단(줄)으로 추가한다
    (한 문단에 이어 붙이지 않는다)."""
    for idx, q in enumerate(questions, start=1):
        head = doc.add_paragraph()
        num_run = head.add_run(f"{idx}. [{q.get('score', 0)}점]  ")
        num_run.bold = True
        _set_korean_font(num_run)
        _add_markup_run(head, q.get('stem', ''))

        if q.get('source_text'):
            src = doc.add_paragraph()
            _add_markup_run(src, q.get('source_text', ''))

        if q.get('q_kind') == 'sa':
            conditions = q.get('conditions') or []
            if conditions:
                cond_p = doc.add_paragraph()
                cond_run = cond_p.add_run('조건: ' + ' / '.join(conditions))
                _set_korean_font(cond_run)
            blank = doc.add_paragraph()
            blank_run = blank.add_run('답: ______________________________________________________')
            _set_korean_font(blank_run)
        else:
            options = q.get('options') or []
            for i, opt in enumerate(options):
                opt_p = doc.add_paragraph()
                mark = _OPTION_MARKS[i] if i < len(_OPTION_MARKS) else str(i + 1)
                mark_run = opt_p.add_run(f"{mark} ")
                _set_korean_font(mark_run)
                _add_markup_run(opt_p, opt)
        doc.add_paragraph('')


def _build_answer_section(doc: Document, questions: list[dict]):
    """정답 섹션. 요청하신 형태(예: "1. ③")대로 문항 번호당 한 줄씩 정답만 간단히 표시한다."""
    for idx, q in enumerate(questions, start=1):
        line = doc.add_paragraph()
        num_run = line.add_run(f"{idx}. ")
        num_run.bold = True
        _set_korean_font(num_run)
        if q.get('q_kind') == 'sa':
            ans_run = line.add_run(f"모범답안: {q.get('sample_answer', '') or '-'}")
        else:
            answer_idx = sorted(set(q.get('answer') or []))
            labels = ', '.join(_OPTION_MARKS[a - 1] if 1 <= a <= len(_OPTION_MARKS) else str(a) for a in answer_idx)
            ans_run = line.add_run(labels or '-')
        ans_run.bold = True
        _set_korean_font(ans_run)
        if q.get('q_kind') == 'sa' and q.get('grading_rule'):
            rule_p = doc.add_paragraph()
            rule_run = rule_p.add_run(f"    채점 기준: {q.get('grading_rule', '')}")
            rule_run.font.size = Pt(9.5)
            _set_korean_font(rule_run)


def _build_combined_exam_document(meta: dict, questions: list[dict]) -> Document:
    """ZIP으로 문제지·정답지 두 파일을 나눠 만들던 방식을 폐지하고, 문제지(A4, 2단) 마지막에
    새 섹션(1단)으로 정답을 이어붙인 단일 .docx(오피스 2007 호환) 하나만 생성한다."""
    doc = _new_docx_with_korean_style()  # 표지: 1단
    subtitle = ' · '.join(filter(None, [meta.get('academy_name', ''), meta.get('teacher_name', '')]))
    _add_doc_heading(doc, meta.get('title') or '내신 실전 시험지', subtitle, '문제지')

    _start_section(doc, columns=2, new_page=False)  # 본문: 2단 (표지 바로 아래에서 이어짐)
    _build_question_section(doc, questions)

    _start_section(doc, columns=1, new_page=True)  # 정답: 새 페이지, 1단
    answer_label = doc.add_paragraph()
    answer_label.alignment = WD_ALIGN_PARAGRAPH.CENTER
    answer_label_run = answer_label.add_run('정답')
    answer_label_run.bold = True
    answer_label_run.font.size = Pt(15)
    _set_korean_font(answer_label_run)
    doc.add_paragraph('')
    _build_answer_section(doc, questions)

    footer = doc.add_paragraph()
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer_run = footer.add_run(subtitle)
    footer_run.font.size = Pt(9)
    _set_korean_font(footer_run)
    return doc


def _build_wrong_note_combined_document(title: str, subtitle: str, notes: list[dict], document_label: str = '오답 복습 문제') -> Document:
    """문제9~11(6차): 학생이 선택한 오답 기록 여러 개를 하나의 통합 오답 복습 문제지로 만든다.
    내신 실전 제작(_build_combined_exam_document)과 동일한 A4/2단/맑은 고딕 10pt/페이지 나누기
    helper(_new_docx_with_korean_style, _start_section, _add_doc_heading, _set_korean_font,
    _add_markup_run)를 그대로 재사용한다 — 새로운 스타일 체계를 만들지 않는다."""
    doc = _new_docx_with_korean_style()
    _add_doc_heading(doc, title, subtitle, document_label)

    _start_section(doc, columns=2, new_page=False)
    for idx, note in enumerate(notes, start=1):
        head = doc.add_paragraph()
        num_run = head.add_run(f"{idx}. ")
        num_run.bold = True
        _set_korean_font(num_run)
        if note.get('question_type'):
            type_run = head.add_run(f"[{note['question_type']}]  ")
            type_run.bold = True
            _set_korean_font(type_run)
        if note.get('title'):
            _add_markup_run(head, note['title'])
        body = doc.add_paragraph()
        _add_markup_run(body, note.get('wrong_text', ''))
        doc.add_paragraph('')

    _start_section(doc, columns=1, new_page=True)
    answer_label = doc.add_paragraph()
    answer_label.alignment = WD_ALIGN_PARAGRAPH.CENTER
    answer_label_run = answer_label.add_run('정답')
    answer_label_run.bold = True
    answer_label_run.font.size = Pt(15)
    _set_korean_font(answer_label_run)
    doc.add_paragraph('')
    for idx, note in enumerate(notes, start=1):
        line = doc.add_paragraph()
        num_run = line.add_run(f"{idx}. ")
        num_run.bold = True
        _set_korean_font(num_run)
        ans_run = line.add_run(note.get('answer_text') or '-')
        ans_run.bold = True
        _set_korean_font(ans_run)

    footer = doc.add_paragraph()
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer_run = footer.add_run(subtitle)
    footer_run.font.size = Pt(9)
    _set_korean_font(footer_run)
    return doc


class ExamExportRequest(BaseModel):
    title: str = ''
    academy_name: str = ''
    teacher_name: str = ''
    question_ids: list[int] = []


@app.post('/api/exam-papers/export-docx')
def export_exam_docx(payload: ExamExportRequest):
    """선택된 전체 문항을 기준으로 문제지 + 정답을 하나의 오피스 2007(.docx) 파일로 내보낸다.
    각 문항은 강사 문제 DB(generated_questions)에 저장된 전체 데이터(지문/발문/선택지/조건/정답 등)를
    그대로 불러와 렌더링한다 — 실전 제작 화면에서 별도로 재구성하지 않는다."""
    if not payload.question_ids:
        raise HTTPException(status_code=400, detail='내보낼 문항을 먼저 선택해 주세요.')
    with get_db() as conn:
        rows = conn.execute(
            f"SELECT * FROM generated_questions WHERE id IN ({','.join('?' * len(payload.question_ids))})",
            payload.question_ids,
        ).fetchall()
    by_id = {row['id']: dict(row) for row in rows}
    ordered = [by_id[qid] for qid in payload.question_ids if qid in by_id]
    if not ordered:
        raise HTTPException(status_code=400, detail='선택한 문항을 강사 문제 DB에서 찾지 못했습니다.')

    questions = []
    for row in ordered:
        questions.append({
            'score': row.get('score', 0),
            'stem': row.get('stem', ''),
            'source_text': row.get('source_text', ''),
            'q_kind': row.get('q_kind', 'mc'),
            'options': json.loads(row.get('options_json') or '[]'),
            'answer': json.loads(row.get('answer_json') or '[]'),
            'conditions': json.loads(row.get('conditions_json') or '[]'),
            'sample_answer': row.get('sample_answer', ''),
            'grading_rule': row.get('grading_rule', ''),
        })
    meta = {
        'title': payload.title.strip() or '내신 실전 시험지',
        'academy_name': payload.academy_name.strip(),
        'teacher_name': payload.teacher_name.strip(),
    }
    doc = _build_combined_exam_document(meta, questions)

    export_dir = WORKSPACE / 'data' / 'exports'
    export_dir.mkdir(parents=True, exist_ok=True)
    safe_title = re.sub(r'[\\/:*?"<>|]', '_', meta['title']) or '내신_실전_시험지'
    doc_path = export_dir / 'exam-combined.docx'
    doc.save(doc_path)
    return FileResponse(
        doc_path,
        media_type='application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        filename=f'{safe_title}.docx',
    )


# ==================================================================================
# 학생 프로필 — 이름/학교/학년/학기/시험(중간·기말)만 저장한다.
# ==================================================================================

class StudentCreate(BaseModel):
    name: str
    school: str = ''
    grade: str = ''
    semester: str = ''
    exam_type: str = ''


@app.post('/api/students')
def create_student(payload: StudentCreate):
    if not payload.name.strip():
        raise HTTPException(status_code=400, detail='학생 이름을 입력해 주세요.')
    now = datetime.now().strftime('%Y.%m.%d %H:%M')
    with get_db() as conn:
        cursor = conn.execute(
            'INSERT INTO students (name, school, grade, semester, exam_type, created_at) VALUES (?,?,?,?,?,?)',
            (payload.name.strip(), payload.school.strip(), payload.grade.strip(), payload.semester.strip(), payload.exam_type.strip(), now),
        )
        row = conn.execute('SELECT * FROM students WHERE id = ?', (cursor.lastrowid,)).fetchone()
        return dict(row)


@app.get('/api/students')
def list_students():
    with get_db() as conn:
        rows = conn.execute('SELECT * FROM students ORDER BY id DESC').fetchall()
        return [dict(row) for row in rows]


@app.delete('/api/students/{student_id}')
def delete_student(student_id: int):
    with get_db() as conn:
        conn.execute('DELETE FROM student_wrong_notes WHERE student_id = ?', (student_id,))
        conn.execute('DELETE FROM students WHERE id = ?', (student_id,))
    return {'ok': True}


# ==================================================================================
# 오답 사진/PDF → 텍스트 추출 2차 정리 (Gemini).
# 1차 브라우저 추출(PDF 텍스트 레이어 + 필요 시 OCR)로 얻은 원문을, 요약·재작성 없이
# 원본 시험지 구조(문제 번호/지문/발문/선택지/밑줄/채점 표시 등) 그대로 정돈만 한다.
# 사용자에게 별도 API Key 입력 UI를 새로 만들지 않고, 이미 앱에 설정되어 있는 Gemini 키를 그대로 사용한다.
# ==================================================================================

class OcrCleanRequest(BaseModel):
    api_key: str = ''
    raw_text: str


class PassageRequest(BaseModel):
    api_key: str = ''
    source_text: str


# 2027학년도 6월 모의평가 18~45번을 기준으로 한 번호별 표준 유형이다.
# 번호는 1차 힌트이고, 저장 시 발문/문항 구조를 함께 검사해 불일치를 별도 기록한다.
CSAT_TYPE_RANGES = [
    ((18,), 'purpose', '목적', ('목적으로', 'purpose')),
    ((19,), 'emotion', '심경', ('심경', 'mood', 'feeling')),
    ((20,), 'claim', '주장', ('주장', 'claim')),
    ((21,), 'implication', '함축 의미', ('의미하는', '함축', 'imply')),
    ((22,), 'main_idea', '요지', ('요지', 'main idea')),
    ((23,), 'topic', '주제', ('주제로', 'topic')),
    ((24,), 'title', '제목', ('제목', 'title')),
    ((25,), 'chart', '도표', ('도표', 'graph', 'chart')),
    ((26,), 'content_match', '내용 일치·불일치', ('내용과 일치', '내용과 일치하지')),
    ((27, 28), 'notice', '안내문', ('안내문', 'event', 'competition')),
    ((29,), 'grammar', '어법', ('어법', 'grammar')),
    ((30,), 'vocabulary', '어휘', ('낱말', '어휘', 'word')),
    ((31, 32, 33, 34), 'blank', '빈칸', ('빈칸', 'blank')),
    ((35,), 'irrelevant', '무관 문장', ('관계 없는', '무관')),
    ((36, 37), 'order', '글의 순서', ('순서', 'order')),
    ((38, 39), 'insertion', '문장 삽입', ('들어가기에', '삽입')),
    ((40,), 'summary', '요약', ('요약', 'summary')),
    ((41, 42), 'long_complex', '장문 복합', ('41', '42', '윗글')),
    ((43, 44, 45), 'long_sequence_reference_content', '장문 순서·지칭·내용', ('43', '44', '45', '윗글')),
]
HALF_FEATURED_NUMBERS = {21, 24, 29, 30, *range(31, 35), *range(36, 43)}


def csat_type_for_number(question_no: int) -> dict:
    for numbers, type_id, name, signals in CSAT_TYPE_RANGES:
        if question_no in numbers:
            return {'id': type_id, 'name': name, 'signals': signals}
    return {'id': 'other', 'name': '기타', 'signals': ()}


def classify_csat_question(question_no: int, prompt: str, choices: list[str]) -> dict:
    expected = csat_type_for_number(question_no)
    haystack = f"{prompt} {' '.join(choices)}".lower()
    matched = any(signal.lower() in haystack for signal in expected['signals'])
    # 복합문항은 그룹 발문이 한 번만 나올 수 있어 보기 5개 또는 번호 표기도 구조 신호로 인정한다.
    if question_no >= 41 and len(choices) == 5:
        matched = True
    return {
        'type_id': expected['id'], 'type_name': expected['name'],
        'type_mismatch': not matched,
        'mismatch_reason': '' if matched else f"{question_no}번 표준 유형({expected['name']})과 발문/구조 신호가 일치하지 않습니다.",
    }


def _extract_hwpx_text(data: bytes) -> str:
    try:
        from xml.etree import ElementTree
        with zipfile.ZipFile(__import__('io').BytesIO(data)) as archive:
            section_names = sorted(name for name in archive.namelist() if re.fullmatch(r'Contents/section\d+\.xml', name))
            if not section_names:
                raise ValueError('HWPX 본문 section XML이 없습니다.')
            chunks = []
            for name in section_names:
                root = ElementTree.fromstring(archive.read(name))
                chunks.extend(node.text for node in root.iter() if node.tag.endswith('}t') and node.text)
            return '\n'.join(chunks)
    except (zipfile.BadZipFile, ValueError, ElementTree.ParseError) as error:
        raise HTTPException(status_code=400, detail=f'HWPX 본문을 읽지 못했습니다: {error}') from error


def _hwp_record_payloads(stream: bytes):
    offset = 0
    while offset + 4 <= len(stream):
        header = struct.unpack_from('<I', stream, offset)[0]
        offset += 4
        tag, size = header & 0x3ff, (header >> 20) & 0xfff
        if size == 0xfff:
            if offset + 4 > len(stream):
                raise ValueError('HWP 레코드 길이가 잘렸습니다.')
            size = struct.unpack_from('<I', stream, offset)[0]
            offset += 4
        if offset + size > len(stream):
            raise ValueError('HWP 본문 레코드가 손상되었습니다.')
        yield tag, stream[offset:offset + size]
        offset += size


def _clean_hwp_para_text(payload: bytes) -> str:
    # HWP_TAG_PARA_TEXT payload is UTF-16LE. Inline controls occupy code points below
    # U+0020; tabs/newlines are retained and other binary control markers are removed.
    text = payload.decode('utf-16le', errors='strict')
    return ''.join('\n' if char in ('\r', '\n') else '\t' if char == '\t' else char
                   for char in text if ord(char) >= 32 or char in ('\r', '\n', '\t')).strip()


def _extract_hwp5_text(data: bytes) -> str:
    """Extract HWP 5.x OLE paragraph text without invoking desktop software.

    Encrypted/distributable documents are rejected. We never pretend that partially
    decoded binary is source text.
    """
    if not data.startswith(bytes.fromhex('D0CF11E0A1B11AE1')):
        raise HTTPException(status_code=400, detail='HWP 5.x OLE 문서가 아닙니다. 구형 HWP 3.x 또는 손상 파일은 지원하지 않습니다.')
    try:
        import olefile
        with olefile.OleFileIO(data) as ole:
            required = ('FileHeader',)
            if any(not ole.exists(name) for name in required):
                raise ValueError('HWP FileHeader가 없습니다.')
            header = ole.openstream('FileHeader').read()
            if not header.startswith(b'HWP Document File') or len(header) < 40:
                raise ValueError('HWP 5.x 헤더가 올바르지 않습니다.')
            flags = struct.unpack_from('<I', header, 36)[0]
            compressed, encrypted, distributable = bool(flags & 1), bool(flags & 2), bool(flags & 4)
            if encrypted:
                raise ValueError('암호화된 HWP는 지원하지 않습니다. 암호를 해제한 사본을 올려 주세요.')
            if distributable:
                raise ValueError('배포용 HWP는 본문 추출을 지원하지 않습니다. 편집 가능한 사본을 올려 주세요.')
            sections = sorted((entry for entry in ole.listdir() if len(entry) == 2 and entry[0] == 'BodyText' and re.fullmatch(r'Section\d+', entry[1])), key=lambda entry: int(entry[1][7:]))
            if not sections:
                raise ValueError('HWP BodyText section이 없습니다.')
            paragraphs = []
            for entry in sections:
                stream = ole.openstream(entry).read()
                if compressed:
                    try: stream = zlib.decompress(stream, -15)
                    except zlib.error as error: raise ValueError('압축된 HWP 본문이 손상되었습니다.') from error
                for tag, payload in _hwp_record_payloads(stream):
                    if tag == 67:  # HWP_TAG_PARA_TEXT
                        text = _clean_hwp_para_text(payload)
                        if text: paragraphs.append(text)
            if not paragraphs:
                raise ValueError('HWP에서 읽을 수 있는 문단 텍스트를 찾지 못했습니다.')
            return '\n'.join(paragraphs)
    except HTTPException:
        raise
    except (OSError, ValueError, UnicodeDecodeError) as error:
        raise HTTPException(status_code=400, detail=f'HWP 본문을 읽지 못했습니다: {error}') from error


def _normalise_uploaded_questions(raw_text: str) -> list[dict]:
    """HWPX/TXT/JSON 어댑터의 공통 경계. JSON은 완전 구조화, 문서는 번호 블록을 보수적으로 분리한다."""
    stripped = raw_text.strip()
    if stripped.startswith(('{', '[')):
        try:
            value = json.loads(stripped)
            return value.get('questions', []) if isinstance(value, dict) else value
        except json.JSONDecodeError:
            pass
    matches = list(re.finditer(r'(?m)^\s*(1[8-9]|[2-3]\d|4[0-5])\s*[\.\)]\s*', stripped))
    result = []
    for index, match in enumerate(matches):
        block = stripped[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(stripped)].strip()
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        prompt = lines[0] if lines else ''
        choices = [re.sub(r'^[①②③④⑤]\s*', '', line) for line in lines if re.match(r'^[①②③④⑤]', line)]
        passage = '\n'.join(line for line in lines[1:] if not re.match(r'^[①②③④⑤]', line))
        result.append({'question_no': int(match.group(1)), 'stem': prompt, 'prompt': prompt, 'passage': passage, 'choices': choices})
    return result


def _canonical_question(raw: dict, exam_kind: str, metadata: dict, filename: str, raw_text: str, now: str) -> dict:
    number = int(raw.get('question_number', raw.get('question_no', 0)) or 0)
    choices = [str(choice) for choice in raw.get('choices', [])]
    stem = str(raw.get('stem', raw.get('prompt', '')))
    classification = classify_csat_question(number, stem, choices)
    return {
        'source_exam': str(metadata.get('title', metadata.get('source_exam', ''))),
        'year': str(metadata.get('year', '')), 'session': str(metadata.get('round', metadata.get('session', ''))),
        'exam_kind': exam_kind, 'question_number': number, 'question_no': number,
        'csat_type': classification['type_id'], 'csat_type_id': classification['type_id'],
        'csat_type_name': classification['type_name'], 'stem': stem, 'prompt': stem,
        'passage': str(raw.get('passage', '')), 'choices': choices,
        'answer': str(raw.get('answer', '')), 'explanation': str(raw.get('explanation', raw.get('solution', ''))),
        'solution': str(raw.get('explanation', raw.get('solution', ''))), 'footnote': str(raw.get('footnote', '')),
        'group_label': str(raw.get('group_label', '')), 'source_file': filename,
        'source_reference': f'{filename}#question-{number}', 'raw_text': raw_text,
        'created_at': now, **classification,
    }


class CsatImportCommit(BaseModel):
    exam_kind: Literal['half', 'full']
    source_filename: str
    source_hash: str = Field(pattern=r'^[0-9a-f]{64}$')
    parser: str
    metadata: dict = {}
    raw_text: str
    questions: list[dict]


def _csat_question_response(row: sqlite3.Row) -> dict:
    item = dict(row)
    item['choices'] = json.loads(item.pop('choices_json'))
    item['exam_metadata'] = json.loads(item.pop('exam_metadata_json', '{}')) if 'exam_metadata_json' in item else {}
    item['type_mismatch'] = bool(item['type_mismatch'])
    return item


@app.get('/api/csat/types')
def list_csat_types():
    return [{'numbers': list(numbers), 'id': type_id, 'name': name} for numbers, type_id, name, _ in CSAT_TYPE_RANGES]


@app.post('/api/csat/uploads', status_code=201)
async def upload_csat_exam(exam_kind: Literal['half', 'full'] = Form(...), metadata_json: str = Form('{}'), file: UploadFile = File(...)):
    filename = file.filename or 'upload'
    suffix = Path(filename).suffix.lower()
    data = await file.read()
    if suffix not in ('.hwp', '.hwpx', '.json', '.txt'):
        raise HTTPException(status_code=415, detail='현재 HWP, HWPX, JSON, TXT를 지원합니다.')
    if not data:
        raise HTTPException(status_code=400, detail='빈 파일은 업로드할 수 없습니다.')
    if len(data) > 30 * 1024 * 1024:
        raise HTTPException(status_code=413, detail='수능 자료 파일은 30MB 이하여야 합니다.')
    parser = {'hwp': 'hwp5-ole-v1', 'hwpx': 'hwpx-xml-v1', 'json': 'json', 'txt': 'txt'}[suffix[1:]]
    raw_text = _extract_hwp5_text(data) if suffix == '.hwp' else _extract_hwpx_text(data) if suffix == '.hwpx' else data.decode('utf-8-sig', errors='strict')
    questions = _normalise_uploaded_questions(raw_text)
    if not questions:
        raise HTTPException(status_code=400, detail='18~45번 문항 블록을 찾지 못했습니다. 구조화 JSON 또는 번호가 포함된 HWPX를 확인해 주세요.')
    try:
        metadata = json.loads(metadata_json or '{}')
    except json.JSONDecodeError as error:
        raise HTTPException(status_code=400, detail='시험 메타데이터 JSON 형식이 올바르지 않습니다.') from error
    now = datetime.now().isoformat()
    canonical = [_canonical_question(raw, exam_kind, metadata, filename, raw_text, now) for raw in questions]
    canonical = [item for item in canonical if 1 <= item['question_number'] <= 99]
    failures = [{'index': index + 1, 'reason': '문항번호를 인식하지 못했습니다.'} for index, raw in enumerate(questions) if not int(raw.get('question_number', raw.get('question_no', 0)) or 0)]
    if not canonical:
        raise HTTPException(status_code=400, detail='저장 가능한 문항을 찾지 못했습니다.')
    return {'source_id': None, 'source_hash': hashlib.sha256(data).hexdigest(), 'parser': parser, 'raw_text': raw_text,
            'metadata': metadata, 'source_filename': filename, 'original_stored': False, 'owner_scope': 'shared',
            'questions': canonical, 'failures': failures, 'requires_review': True}


@app.post('/api/csat/imports', status_code=201)
def commit_csat_import(payload: CsatImportCommit):
    now = datetime.now().isoformat()
    canonical = [_canonical_question(raw, payload.exam_kind, payload.metadata, payload.source_filename, payload.raw_text, now) for raw in payload.questions]
    canonical = [item for item in canonical if 1 <= item['question_number'] <= 99]
    if not canonical:
        raise HTTPException(status_code=400, detail='검수 후 저장할 문항이 없습니다.')
    saved = []
    with get_db() as conn:
        duplicate = conn.execute('SELECT id FROM csat_question_sources WHERE source_hash=? AND exam_kind=?', (payload.source_hash, payload.exam_kind)).fetchone()
        if duplicate:
            raise HTTPException(status_code=409, detail=f'같은 원본 파일이 이미 저장되어 있습니다. (source_id={duplicate["id"]})')
        source = conn.execute(
            'INSERT INTO csat_question_sources (exam_kind, source_filename, exam_metadata_json, parser, raw_text, created_at, source_hash, original_stored, owner_scope) VALUES (?,?,?,?,?,?,?,?,?)',
            (payload.exam_kind, payload.source_filename, json.dumps(payload.metadata, ensure_ascii=False), payload.parser, payload.raw_text, now, payload.source_hash, 0, 'shared'),
        )
        source_id = source.lastrowid
        for raw in canonical:
            number, choices = raw['question_number'], raw['choices']
            cursor = conn.execute('''INSERT INTO csat_questions
                (source_id, exam_kind, question_no, csat_type_id, csat_type_name, passage, prompt, choices_json,
                 answer, solution, footnote, group_label, source_reference, type_mismatch, mismatch_reason, raw_json, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                (source_id, payload.exam_kind, number, raw['type_id'], raw['type_name'], raw['passage'], raw['stem'],
                 json.dumps(choices, ensure_ascii=False), raw['answer'], raw['explanation'], raw['footnote'], raw['group_label'],
                 raw['source_reference'], int(raw['type_mismatch']), raw['mismatch_reason'], json.dumps(raw, ensure_ascii=False), now))
            saved.append(cursor.lastrowid)
        if not saved:
            raise HTTPException(status_code=400, detail='18~45번 범위의 저장 가능한 문항이 없습니다.')
        rows = conn.execute(f'''SELECT q.*, s.source_filename, s.exam_metadata_json FROM csat_questions q
            JOIN csat_question_sources s ON s.id=q.source_id WHERE q.id IN ({','.join('?' * len(saved))}) ORDER BY q.question_no''', saved).fetchall()
    return {'source_id': source_id, 'parser': payload.parser, 'original_stored': False, 'owner_scope': 'shared', 'questions': [_csat_question_response(row) for row in rows]}


@app.get('/api/csat/questions')
def list_csat_questions(exam_kind: Optional[Literal['half', 'full']] = None, question_no: Optional[int] = None, type_id: Optional[str] = None, mismatch: Optional[bool] = None):
    filters, values = [], []
    for clause, value in (('q.exam_kind=?', exam_kind), ('q.question_no=?', question_no), ('q.csat_type_id=?', type_id)):
        if value is not None: filters.append(clause); values.append(value)
    if mismatch is not None: filters.append('q.type_mismatch=?'); values.append(int(mismatch))
    where = f"WHERE {' AND '.join(filters)}" if filters else ''
    with get_db() as conn:
        rows = conn.execute(f'''SELECT q.*, s.source_filename, s.exam_metadata_json FROM csat_questions q
            JOIN csat_question_sources s ON s.id=q.source_id {where} ORDER BY q.created_at DESC, q.question_no''', values).fetchall()
    return [_csat_question_response(row) for row in rows]


class CsatWrongAnswerCreate(BaseModel):
    student_name: str
    exam_name: str = ''
    question_no: int = Field(ge=18, le=45)
    wrong_answer: str = ''
    correct_answer: str = ''
    memo: str = ''
    source_filename: str = ''


@app.post('/api/csat/wrong-answers', status_code=201)
def create_csat_wrong_answer(payload: CsatWrongAnswerCreate):
    if not payload.student_name.strip():
        raise HTTPException(status_code=400, detail='학생 이름을 입력해 주세요.')
    csat_type = csat_type_for_number(payload.question_no)
    now = datetime.now().isoformat()
    with get_db() as conn:
        cursor = conn.execute('''INSERT INTO csat_wrong_answers
            (student_name, exam_name, question_no, csat_type_id, csat_type_name, wrong_answer, correct_answer, memo, source_filename, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)''', (payload.student_name.strip(), payload.exam_name.strip(), payload.question_no, csat_type['id'], csat_type['name'],
            payload.wrong_answer.strip(), payload.correct_answer.strip(), payload.memo.strip(), payload.source_filename.strip(), now))
        return dict(conn.execute('SELECT * FROM csat_wrong_answers WHERE id=?', (cursor.lastrowid,)).fetchone())


@app.get('/api/csat/wrong-answers')
def list_csat_wrong_answers(student: Optional[str] = None, exam: Optional[str] = None, type_id: Optional[str] = None):
    filters, values = [], []
    for clause, value in (('student_name=?', student), ('exam_name=?', exam), ('csat_type_id=?', type_id)):
        if value: filters.append(clause); values.append(value)
    where = f"WHERE {' AND '.join(filters)}" if filters else ''
    with get_db() as conn:
        return [dict(row) for row in conn.execute(f'SELECT * FROM csat_wrong_answers {where} ORDER BY id DESC', values).fetchall()]


def render_csat_html(questions: list[dict], title: str = 'BuildIt 수능 영어') -> str:
    def esc(value):
        import html
        return html.escape(str(value or ''))
    blocks = []
    for q in sorted(questions, key=lambda item: item['question_no']):
        choices = ''.join(f'<li>{esc(choice)}</li>' for choice in q.get('choices', []))
        flag = '<span class="flag">유형 확인 필요</span>' if q.get('type_mismatch') else ''
        blocks.append(f'''<article class="question" data-question-id="{q.get('id', '')}"><nav class="question-tools"><button onclick="moveQuestion(this,-1)">↑</button><button onclick="moveQuestion(this,1)">↓</button><button onclick="this.closest('.question').remove()">삭제</button></nav><h2>{q['question_no']}. <span contenteditable="true">{esc(q.get('prompt'))}</span> {flag}</h2>
            <div class="passage" contenteditable="true">{esc(q.get('passage')).replace(chr(10), '<br>')}</div>
            <ol class="choices" contenteditable="true">{choices}</ol><small contenteditable="true">{esc(q.get('footnote'))}</small></article>''')
    return f'''<!doctype html><html lang="ko"><meta charset="utf-8"><title>{esc(title)}</title><style>
        @page{{size:A4;margin:12mm}}*{{box-sizing:border-box}}body{{font-family:"Noto Serif KR","Malgun Gothic",serif;margin:0;color:#111}}
        header{{display:flex;justify-content:space-between;border-bottom:2px solid #111;margin-bottom:12px}}header b{{font-size:22px}}
        .toolbar{{position:sticky;top:0;background:#fff4cf;padding:8px;text-align:center;z-index:2}}.paper{{column-count:2;column-gap:28px;column-rule:1px solid #222}}
        .question{{break-inside:avoid;page-break-inside:avoid;margin:0 0 18px;font-size:11.5px;line-height:1.55;position:relative}}.question-tools{{display:none;position:absolute;right:0;top:-5px}}.question:hover .question-tools{{display:block}}h2{{font-size:12px;margin:0 0 7px}}.passage{{text-align:justify;margin-left:14px}}
        .choices{{list-style:none;padding:0 0 0 14px;margin:8px 0;counter-reset:item}}.choices li{{counter-increment:item}}.choices li:before{{content:counter(item, circle) " ";}}
        .flag{{font:10px sans-serif;color:#a33;border:1px solid #a33;padding:1px 4px}}[contenteditable=true]:focus{{outline:2px solid #e5b94f;background:#fff9e8}}
        @media print{{.toolbar,.question-tools{{display:none!important}}.paper{{column-fill:auto}}.question:nth-of-type(4n+1):not(:first-child){{break-before:page}}}}@media(max-width:800px){{.paper{{column-count:1;column-rule:0}}}}
    </style><body><div class="toolbar">문구를 클릭해 수정할 수 있습니다. <button onclick="print()">인쇄</button> <button onclick="saveEdited()">편집 HTML 저장</button></div>
    <header><b contenteditable="true">{esc(title)}</b><span>BuildIt 자체 제작 시험지 · A4 2단 · 페이지당 4문항 목표</span></header><main class="paper">{''.join(blocks)}</main><script>function moveQuestion(button,direction){{const item=button.closest('.question'),parent=item.parentElement,sibling=direction<0?item.previousElementSibling:item.nextElementSibling;if(!sibling)return;direction<0?parent.insertBefore(item,sibling):parent.insertBefore(sibling,item)}}function saveEdited(){{const blob=new Blob(['<!doctype html>'+document.documentElement.outerHTML],{{type:'text/html;charset=utf-8'}});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='buildit-csat-edited.html';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),500)}}</script></body></html>'''


@app.get('/api/csat/exam.html', response_class=HTMLResponse)
def export_csat_exam_html(ids: str = '', exam_kind: Optional[Literal['half', 'full']] = None, title: str = 'BuildIt 수능 영어'):
    selected = [int(value) for value in ids.split(',') if value.strip().isdigit()]
    filters, values = [], []
    if selected: filters.append(f"q.id IN ({','.join('?' * len(selected))})"); values.extend(selected)
    if exam_kind: filters.append('q.exam_kind=?'); values.append(exam_kind)
    where = f"WHERE {' AND '.join(filters)}" if filters else ''
    with get_db() as conn:
        rows = conn.execute(f'SELECT q.* FROM csat_questions q {where} ORDER BY q.question_no', values).fetchall()
    items = []
    for row in rows:
        item = dict(row); item['choices'] = json.loads(item['choices_json']); item['type_mismatch'] = bool(item['type_mismatch']); items.append(item)
    return render_csat_html(items, title)


PASSAGE_MEMORY_SCHEMA = {
    'type': 'object', 'properties': {
        'title': {'type': 'string'},
        'sentences': {'type': 'array', 'items': {'type': 'object', 'properties': {
            'english': {'type': 'string'}, 'korean': {'type': 'string'},
        }, 'required': ['english', 'korean']}},
    }, 'required': ['title', 'sentences'],
}

PASSAGE_ANALYSIS_SCHEMA = {
    'type': 'object', 'properties': {
        'title': {'type': 'string'},
        'topic': {'type': 'string'}, 'mainIdea': {'type': 'string'}, 'summary': {'type': 'string'},
        'sentences': {'type': 'array', 'items': {'type': 'object', 'properties': {
            'slashed': {'type': 'string'}, 'directTranslation': {'type': 'string'},
            'translation': {'type': 'string'}, 'grammarGuide': {'type': 'string'},
        }, 'required': ['slashed', 'directTranslation', 'translation', 'grammarGuide']}},
        'tfQuestions': {'type': 'array', 'items': {'type': 'object', 'properties': {
            'language': {'type': 'string', 'enum': ['KO', 'EN']}, 'statement': {'type': 'string'},
            'answer': {'type': 'string', 'enum': ['T', 'F']},
        }, 'required': ['language', 'statement', 'answer']}},
    }, 'required': ['title', 'topic', 'mainIdea', 'summary', 'sentences', 'tfQuestions'],
}


def _passage_source(payload: PassageRequest) -> str:
    source = payload.source_text.strip()
    if not source:
        raise HTTPException(status_code=400, detail='분석할 영어 지문이 없습니다.')
    return source[:30000]


@app.post('/api/passages/memory')
def create_passage_memory(payload: PassageRequest):
    source = _passage_source(payload)
    rules = ('영어 학습용 영한 대조 자료를 만든다. 원문을 빠짐없이 자연스러운 문장 단위로 나누고, '
             '각 문장에 정확하고 자연스러운 한국어 번역을 붙인다. 영어 원문의 의미나 순서를 바꾸지 않는다.')
    try:
        ai = ai_router.generate('auto', rules, source, schema=PASSAGE_MEMORY_SCHEMA, temperature=0.15)
        result = json.loads(re.sub(r'```json|```', '', ai.text).strip())
        return {**result, 'provider': ai.provider, 'model': ai.model}
    except Exception as exc:
        _handle_gemini_error(exc)


@app.post('/api/passages/analysis')
def create_passage_analysis(payload: PassageRequest):
    source = _passage_source(payload)
    rules = ('외부 영어 지문을 밀도 높은 학습 자료로 정리한다. 영어 원문 전체를 별도 반복하지 않는다. '
             '모든 문장을 원래 순서대로 정확히 한 번만 sentences에 넣는다. slashed는 영어 문장 자체에 의미 단위 / 를 넣은 문자열이고, '
             '바로 이어 directTranslation은 슬래시 단위의 직독직해, translation은 자연스러운 정식 해석, grammarGuide는 핵심 문법과 구문 설명이다. '
             '내용일치 T/F는 정확히 10문항으로 앞 5개 한국어(KO), 뒤 5개 영어(EN), T/F를 모두 포함하고 지문만으로 판정 가능하게 한다.')
    try:
        ai = ai_router.generate('auto', rules, source, schema=PASSAGE_ANALYSIS_SCHEMA, temperature=0.2)
        result = json.loads(re.sub(r'```json|```', '', ai.text).strip())
        if len(result.get('tfQuestions', [])) != 10:
            raise HTTPException(status_code=502, detail='T/F 문항이 10개로 생성되지 않았습니다. 다시 시도해 주세요.')
        return {**result, 'provider': ai.provider, 'model': ai.model}
    except HTTPException:
        raise
    except Exception as exc:
        _handle_gemini_error(exc)


@app.post('/api/ocr/clean-text')
def clean_ocr_text(payload: OcrCleanRequest):
    raw_text = payload.raw_text.strip()
    if not raw_text:
        raise HTTPException(status_code=400, detail='정리할 추출 텍스트가 없습니다.')
    rules = pl.build_ocr_clean_rules()
    try:
        ai = ai_router.generate('auto', rules, raw_text[:20000], temperature=0.1, allow_noncritical_fallback=True)
        text = ai.text
    except Exception as exc:
        _handle_gemini_error(exc)
        return
    return {'cleanedText': text.strip(), 'provider': ai.provider, 'model': ai.model}


# ==================================================================================
# 학생 오답 관리 — 사진/PDF에서 브라우저 OCR + Gemini로 정리한 "최종 추출 텍스트"를
# 사용자가 화면에서 확인·수정한 뒤 저장한다. 이 저장 단계에서는 더 이상 AI를 호출하지
# 않는다(문제2, 6차): 예전에는 저장 버튼을 누르면 서버가 다시 한 번 Gemini를 호출해
# "틀린 문제만" 요약 추출했는데, 이 재처리 과정에서 사용자가 이미 확인한 내용의 일부가
# 잘려나가는 문제가 있었다. 이제 저장 버튼은 사용자가 화면에서 편집한 텍스트를 그대로,
# 손실 없이 DB에 넣는다. 각 저장은 독립된 record이며(덮어쓰지 않고 계속 누적),
# student_id로 학생과 연결된다 — 학교/학년 등은 note에 복사해 두지 않고, 조회 시
# students 테이블을 다시 조인해 항상 최신 정보를 보여준다(문제7).
# ==================================================================================

class WrongNoteCreate(BaseModel):
    exam_label: str = ''
    title: str = ''
    question_type: str = ''
    question_text: str
    answer_text: str = ''
    source_text: str = ''


def _wrong_note_response(row: dict, student: dict) -> dict:
    return {
        'id': row['id'], 'student_id': row['student_id'],
        'student_name': student['name'], 'student_school': student['school'], 'student_grade': student['grade'],
        'exam_label': row['exam_label'], 'title': row['title'], 'question_type': row['question_type'],
        'wrong_text': row['wrong_text'], 'answer_text': row['answer_text'], 'source_text': row['source_text'],
        'created_at': row['created_at'],
    }


@app.post('/api/students/{student_id}/wrong-notes')
def create_wrong_note(student_id: int, payload: WrongNoteCreate):
    if not payload.question_text.strip():
        raise HTTPException(status_code=400, detail='저장할 오답 문제 텍스트가 없습니다. 오답 사진을 업로드해 추출한 뒤 저장해 주세요.')
    with get_db() as conn:
        student = conn.execute('SELECT * FROM students WHERE id = ?', (student_id,)).fetchone()
        if not student:
            raise HTTPException(status_code=404, detail='학생 프로필을 찾지 못했습니다. 먼저 학생을 등록해 주세요.')
        now = datetime.now().isoformat()
        cursor = conn.execute(
            '''INSERT INTO student_wrong_notes
               (student_id, exam_label, title, question_type, wrong_text, answer_text, source_text, created_at)
               VALUES (?,?,?,?,?,?,?,?)''',
            (
                student_id, payload.exam_label.strip(), payload.title.strip(), payload.question_type.strip(),
                payload.question_text.strip()[:20000], payload.answer_text.strip()[:2000],
                payload.source_text.strip()[:20000], now,
            ),
        )
        row = conn.execute('SELECT * FROM student_wrong_notes WHERE id = ?', (cursor.lastrowid,)).fetchone()
        return _wrong_note_response(dict(row), dict(student))


@app.get('/api/students/{student_id}/wrong-notes')
def list_wrong_notes(student_id: int):
    with get_db() as conn:
        student = conn.execute('SELECT * FROM students WHERE id = ?', (student_id,)).fetchone()
        if not student:
            raise HTTPException(status_code=404, detail='학생 프로필을 찾지 못했습니다.')
        rows = conn.execute('SELECT * FROM student_wrong_notes WHERE student_id = ? ORDER BY id DESC', (student_id,)).fetchall()
        return [_wrong_note_response(dict(row), dict(student)) for row in rows]


class WrongNoteUpdate(BaseModel):
    exam_label: Optional[str] = None
    title: Optional[str] = None
    question_type: Optional[str] = None
    question_text: Optional[str] = None
    answer_text: Optional[str] = None


@app.patch('/api/students/{student_id}/wrong-notes/{note_id}')
def update_wrong_note(student_id: int, note_id: int, payload: WrongNoteUpdate):
    """문제6(6차): "전체보기"에서 수정한 뒤 다시 저장할 수 있도록 부분 수정을 지원한다."""
    with get_db() as conn:
        student = conn.execute('SELECT * FROM students WHERE id = ?', (student_id,)).fetchone()
        existing = conn.execute('SELECT * FROM student_wrong_notes WHERE id = ? AND student_id = ?', (note_id, student_id)).fetchone()
        if not student or not existing:
            raise HTTPException(status_code=404, detail='수정할 오답 자료를 찾지 못했습니다.')
        fields = {
            'exam_label': payload.exam_label, 'title': payload.title, 'question_type': payload.question_type,
            'wrong_text': payload.question_text, 'answer_text': payload.answer_text,
        }
        updates = {k: v.strip() for k, v in fields.items() if v is not None}
        if updates:
            set_clause = ', '.join(f'{k} = ?' for k in updates)
            conn.execute(f'UPDATE student_wrong_notes SET {set_clause} WHERE id = ?', (*updates.values(), note_id))
        row = conn.execute('SELECT * FROM student_wrong_notes WHERE id = ?', (note_id,)).fetchone()
        return _wrong_note_response(dict(row), dict(student))


@app.delete('/api/students/{student_id}/wrong-notes/{note_id}')
def delete_wrong_note(student_id: int, note_id: int):
    with get_db() as conn:
        conn.execute('DELETE FROM student_wrong_notes WHERE id = ? AND student_id = ?', (note_id, student_id))
    return {'ok': True}


class WrongNoteExportRequest(BaseModel):
    note_ids: list[int] = []
    title: str = ''


@app.post('/api/students/{student_id}/wrong-notes/export-docx')
def export_wrong_notes_docx(student_id: int, payload: WrongNoteExportRequest):
    """문제9~11(6차): 선택한 오답 문제들을 순서대로 하나의 통합 문제지(.docx)로 내보낸다.
    내신 실전 제작과 동일한 A4/2단/맑은 고딕 10pt/페이지 나누기 스타일을 재사용한다."""
    if not payload.note_ids:
        raise HTTPException(status_code=400, detail='통합할 오답 문항을 먼저 선택해 주세요.')
    with get_db() as conn:
        student = conn.execute('SELECT * FROM students WHERE id = ?', (student_id,)).fetchone()
        if not student:
            raise HTTPException(status_code=404, detail='학생 프로필을 찾지 못했습니다.')
        rows = conn.execute(
            f"SELECT * FROM student_wrong_notes WHERE student_id = ? AND id IN ({','.join('?' * len(payload.note_ids))})",
            (student_id, *payload.note_ids),
        ).fetchall()
    by_id = {row['id']: dict(row) for row in rows}
    ordered = [by_id[nid] for nid in payload.note_ids if nid in by_id]
    if not ordered:
        raise HTTPException(status_code=400, detail='선택한 오답 문항을 찾지 못했습니다.')

    title = payload.title.strip() or f"{student['name']} 오답 복습 문제"
    subtitle = ' · '.join(filter(None, [student['school'], student['grade'], student['name']]))
    doc = _build_wrong_note_combined_document(title, subtitle, ordered)

    export_dir = WORKSPACE / 'data' / 'exports'
    export_dir.mkdir(parents=True, exist_ok=True)
    safe_title = re.sub(r'[\\/:*?"<>|]', '_', title) or '오답_복습_문제'
    doc_path = export_dir / 'wrong-notes-combined.docx'
    doc.save(doc_path)
    return FileResponse(
        doc_path,
        media_type='application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        filename=f'{safe_title}.docx',
    )


@app.get('/api/export/project')
def export_project():
    files = ['backend/main.py', 'backend/prompt_logic.py', 'src', 'package.json', 'package-lock.json', 'vite.config.ts', 'tsconfig.json', 'index.html', 'requirements.txt', 'data/app.db']
    try:
        if EXPORT_PATH.exists():
            EXPORT_PATH.unlink()
        with zipfile.ZipFile(EXPORT_PATH, 'w', zipfile.ZIP_DEFLATED) as archive:
            for relative in files:
                target = WORKSPACE / relative
                if target.is_file():
                    archive.write(target, target.relative_to(WORKSPACE))
                elif target.is_dir():
                    for item in target.rglob('*'):
                        if item.is_file() and '.DS_Store' not in item.name and not item.name.endswith('.map'):
                            archive.write(item, item.relative_to(WORKSPACE))
    except OSError as error_message:
        raise HTTPException(status_code=500, detail='압축파일을 준비하지 못했습니다.') from error_message
    return FileResponse(EXPORT_PATH, media_type='application/zip', filename='feel-to-real-project-export.zip')

class SchoolPrintExportItem(BaseModel):
    title: str = ''
    text: str = ''
    answer: str = ''

class SchoolPrintExportRequest(BaseModel):
    title: str = ''
    subtitle: str = ''
    items: list[SchoolPrintExportItem] = []

@app.post('/api/school-prints/export-docx')
def export_school_prints_docx(payload: SchoolPrintExportRequest):
    if not payload.items:
        raise HTTPException(status_code=400, detail='내보낼 학교 프린트를 선택해 주세요.')
    notes = [{'title': x.title, 'wrong_text': x.text, 'answer_text': x.answer, 'question_type': ''} for x in payload.items]
    title = payload.title.strip() or '학교 프린트'
    doc = _build_wrong_note_combined_document(title, payload.subtitle.strip(), notes, '학교 프린트')
    export_dir = WORKSPACE / 'data' / 'exports'; export_dir.mkdir(parents=True, exist_ok=True)
    path = export_dir / 'school-prints-combined.docx'; doc.save(path)
    safe = re.sub(r'[\\/:*?"<>|]', '_', title) or '학교_프린트'
    return FileResponse(path, media_type='application/vnd.openxmlformats-officedocument.wordprocessingml.document', filename=f'{safe}.docx')


# ==================================================================================
# n8n / Hermes 독립 계약 — UI 파서를 복제하지 않고 BuildIt 서버에서 자료 분석과 생성을 수행한다.
# ==================================================================================

class DigitalTwinDnaItem(BaseModel):
    model_config = ConfigDict(extra='forbid')
    question_no: int
    question_type: str = ''
    passage: str = ''
    prompt: str = ''
    grammar_points: list[str] = Field(default_factory=list)
    vocabulary_features: list[str] = Field(default_factory=list)
    choice_structure: list[str] = Field(default_factory=list)
    trap_patterns: list[str] = Field(default_factory=list)
    difficulty: str = ''
    short_answer_features: list[str] = Field(default_factory=list)
    other_features: list[str] = Field(default_factory=list)


class DigitalTwinDnaBatch(BaseModel):
    model_config = ConfigDict(extra='forbid')
    questions: list[DigitalTwinDnaItem]


class AutomationGenerationRequest(BaseModel):
    task: str = 'generate_questions'
    school: str
    grade: str
    exam: str
    question_count: int = Field(default=3, ge=1, le=30)
    source_passage: str
    requirements: list[str] | str = Field(default_factory=list)
    digital_twin: Literal['auto'] = 'auto'


def _automation_text(filename: str, data: bytes) -> tuple[str, str]:
    suffix = Path(filename).suffix.lower()
    if suffix == '.hwp':
        return _extract_hwp5_text(data), 'hwp5-ole-v1'
    if suffix == '.hwpx':
        return _extract_hwpx_text(data), 'hwpx-xml-v1'
    if suffix in ('.txt', '.json'):
        try:
            return data.decode('utf-8-sig', errors='strict'), suffix[1:]
        except UnicodeDecodeError as error:
            raise HTTPException(status_code=400, detail='UTF-8 텍스트 파일만 지원합니다.') from error
    if suffix == '.docx':
        try:
            document = Document(__import__('io').BytesIO(data))
            return '\n'.join(p.text for p in document.paragraphs if p.text.strip()), 'docx-python-docx-v1'
        except Exception as error:
            raise HTTPException(status_code=400, detail='DOCX 본문을 읽지 못했습니다.') from error
    if suffix == '.pdf':
        try:
            from pypdf import PdfReader
            reader = PdfReader(__import__('io').BytesIO(data))
            return '\n'.join((page.extract_text() or '') for page in reader.pages), 'pdf-pypdf-v1'
        except Exception as error:
            raise HTTPException(status_code=400, detail='텍스트 레이어가 있는 PDF만 지원합니다.') from error
    raise HTTPException(status_code=415, detail='HWP, HWPX, PDF, DOCX, JSON, TXT를 지원합니다.')


def _automation_question_blocks(raw_text: str) -> list[dict]:
    stripped = raw_text.strip()
    if stripped.startswith(('{', '[')):
        try:
            value = json.loads(stripped)
            items = value.get('questions', []) if isinstance(value, dict) else value
            if isinstance(items, list):
                return [item for item in items if isinstance(item, dict)]
        except json.JSONDecodeError:
            pass
    marks = list(re.finditer(r'(?m)^\s*(\d{1,3})\s*[\.\)]\s+', stripped))
    result = []
    for index, mark in enumerate(marks):
        block = stripped[mark.end():marks[index + 1].start() if index + 1 < len(marks) else len(stripped)].strip()
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        choices = [re.sub(r'^(?:[①②③④⑤]|[1-5][\.\)])\s*', '', line) for line in lines if re.match(r'^(?:[①②③④⑤]|[1-5][\.\)])', line)]
        result.append({'question_no': int(mark.group(1)), 'prompt': lines[0] if lines else '', 'passage': '\n'.join(lines[1:]), 'choices': choices})
    return result


def _automation_dna(raw: dict, fallback_no: int) -> dict:
    def strings(key):
        value = raw.get(key, [])
        return [str(x) for x in value] if isinstance(value, list) else ([str(value)] if value else [])
    return DigitalTwinDnaItem(
        question_no=int(raw.get('question_no', raw.get('question_number', fallback_no)) or fallback_no),
        question_type=str(raw.get('question_type', raw.get('type', ''))),
        passage=str(raw.get('passage', raw.get('source_text', ''))),
        prompt=str(raw.get('prompt', raw.get('stem', ''))),
        grammar_points=strings('grammar_points'), vocabulary_features=strings('vocabulary_features'),
        choice_structure=strings('choice_structure') or strings('choices'), trap_patterns=strings('trap_patterns'),
        difficulty=str(raw.get('difficulty', '')), short_answer_features=strings('short_answer_features'),
        other_features=strings('other_features'),
    ).model_dump()


def _automation_analyze_with_qwen(raw_text: str, questions: list[dict]) -> list[dict]:
    system = (
        '영어 시험 문항을 문항별 출제 DNA canonical JSON으로 구조화한다. '
        '원문에 없는 사실을 추측하지 말고 모든 필드를 반환한다.'
    )
    prompt = json.dumps({'question_blocks': questions, 'source_text': raw_text[:50000]}, ensure_ascii=False)
    try:
        result = ai_router.generate('auto', system, prompt, schema=DigitalTwinDnaBatch.model_json_schema(), temperature=0.1)
        parsed = json.loads(re.sub(r'```json|```', '', result.text).strip())
        return [item.model_dump() for item in DigitalTwinDnaBatch.model_validate(parsed).questions]
    except (json.JSONDecodeError, ValidationError) as error:
        raise HTTPException(status_code=502, detail='Qwen 문항 DNA schema validation 실패') from error
    except Exception as error:
        _handle_gemini_error(error)
        raise


@app.post('/api/automation/digital-twin/sources', status_code=201)
async def ingest_digital_twin_source(
    school: str = Form(...), grade: str = Form(...), exam: str = Form(...),
    material_type: str = Form('past_exam'), file: UploadFile = File(...),
):
    school, grade, exam = school.strip(), grade.strip(), exam.strip()
    if not school or not grade or not exam:
        raise HTTPException(status_code=400, detail='school, grade, exam은 필수입니다.')
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail='빈 파일은 업로드할 수 없습니다.')
    if len(data) > 30 * 1024 * 1024:
        raise HTTPException(status_code=413, detail='자료 파일은 30MB 이하여야 합니다.')
    filename = file.filename or 'upload'
    source_hash = hashlib.sha256(data).hexdigest()
    raw_text, parser = _automation_text(filename, data)
    questions = _automation_question_blocks(raw_text)
    if not questions:
        raise HTTPException(status_code=400, detail='문항 번호 블록 또는 canonical JSON questions를 찾지 못했습니다.')
    if Path(filename).suffix.lower() == '.json':
        dna_items = [_automation_dna(item, index + 1) for index, item in enumerate(questions)]
    else:
        dna_items = _automation_analyze_with_qwen(raw_text, questions)
    now = datetime.now().isoformat()
    with get_db() as conn:
        duplicate = conn.execute('SELECT id FROM digital_twin_sources WHERE source_hash=?', (source_hash,)).fetchone()
        if duplicate:
            raise HTTPException(status_code=409, detail={'status': 'duplicate_source', 'source_id': duplicate['id']})
        source = conn.execute(
            '''INSERT INTO digital_twin_sources
               (school,grade,exam,material_type,source_filename,source_hash,parser,raw_text,analysis_json,created_at)
               VALUES (?,?,?,?,?,?,?,?,?,?)''',
            (school, grade, exam, material_type, filename, source_hash, parser, raw_text,
             json.dumps({'questions': dna_items}, ensure_ascii=False), now),
        )
        source_id = source.lastrowid
        for item in dna_items:
            reference = f'{filename}#question-{item["question_no"]}'
            key = hashlib.sha256(f'{source_hash}:{item["question_no"]}:{item["prompt"]}'.encode()).hexdigest()
            conn.execute(
                '''INSERT INTO digital_twin_question_dna
                   (source_id,question_key,question_no,dna_json,source_reference,source_hash,created_at)
                   VALUES (?,?,?,?,?,?,?)''',
                (source_id, key, item['question_no'], json.dumps(item, ensure_ascii=False), reference, source_hash, now),
            )
        rows = conn.execute(
            '''SELECT d.dna_json, d.source_reference, d.source_hash
               FROM digital_twin_question_dna d JOIN digital_twin_sources s ON s.id=d.source_id
               WHERE s.school=? AND s.grade=? AND s.exam=? ORDER BY d.id''',
            (school, grade, exam),
        ).fetchall()
        all_dna = [{**json.loads(row['dna_json']), 'source_reference': row['source_reference'], 'source_hash': row['source_hash']} for row in rows]
        latest = conn.execute(
            'SELECT COALESCE(MAX(version),0) version FROM digital_twin_profiles WHERE school=? AND grade=? AND exam=?',
            (school, grade, exam),
        ).fetchone()
        version = int(latest['version']) + 1
        profile = {
            'school': school, 'grade': grade, 'exam': exam, 'sourceCount': len({x['source_hash'] for x in all_dna}),
            'questionDna': all_dna, 'types': sorted({x['question_type'] for x in all_dna if x['question_type']}),
            'rules': sorted({feature for x in all_dna for feature in x['other_features']}),
            'updatedAt': now,
        }
        conn.execute('UPDATE digital_twin_profiles SET is_active=0 WHERE school=? AND grade=? AND exam=?', (school, grade, exam))
        twin = conn.execute(
            '''INSERT INTO digital_twin_profiles
               (school,grade,exam,profile_json,version,is_active,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)''',
            (school, grade, exam, json.dumps(profile, ensure_ascii=False), version, now, now),
        )
        twin_id = twin.lastrowid
    return {'status': 'stored', 'source_id': source_id, 'source_hash': source_hash, 'parser': parser,
            'original_stored': False, 'question_count': len(dna_items), 'twin_id': twin_id, 'twin_version': version}


@app.post('/api/automation/questions/generate')
def automation_generate_questions(payload: AutomationGenerationRequest):
    with get_db() as conn:
        try:
            twin = _load_twin(conn, None, payload.school, payload.grade, payload.exam)
        except HTTPException:
            return {'status': 'needs_source_material', 'needed': {'school': payload.school, 'grade': payload.grade, 'exam': payload.exam}}
    if not twin.get('sourceCount') or not twin.get('questionDna'):
        return {'status': 'needs_source_material', 'needed': {'school': payload.school, 'grade': payload.grade, 'exam': payload.exam}}
    requirements = payload.requirements if isinstance(payload.requirements, list) else [payload.requirements]
    requirement_text = '\n'.join(str(item) for item in requirements if str(item).strip()) or 'Digital Twin 출제 DNA를 따른다.'
    composition = 'writing' if any('서답형' in item for item in requirements) else 'balanced'
    generated = generate_questions(NewQuestionRequest(
        provider='auto', twin_id=twin['id'], school=payload.school, grade=payload.grade, exam=payload.exam,
        passage=payload.source_passage, grammar=requirement_text, count=payload.question_count, composition=composition,
    ))
    return {'status': 'generated', **generated}

