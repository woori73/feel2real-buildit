"""BuildIt Wiki MCP Server — 기존 Workspace Context API 위의 얇은 adapter.

단방향 책임:
  SQLite -> Wiki Backend Context API(main.py) -> MCP Server(이 파일) -> LLM
  LLM -> MCP save tool(이 파일) -> Wiki Backend Context API(main.py) -> SQLite

이 파일은 Digital Twin/학교 프린트/학생 오답/수업 인사이트를 직접 조회하거나
다시 조립하지 않는다. main.py의 get_final_workspace_context /
get_jikbo_workspace_context / create_final_deliverable을 그대로 호출만 한다 —
Wiki backend(main.py)가 Context의 single source of truth라는 원칙을 지킨다.

main과의 순환 import를 피하기 위해 각 tool 함수 안에서 지연 import한다
(main.py가 이 모듈을 mount하므로 모듈 최상단에서 서로를 import할 수 없다).
같은 이유로 backend 디렉터리의 다른 형제 모듈(question_contract 등)도 모듈
최상단이 아니라 각 tool 함수 안에서 import한다 — 이 파일이 `backend.mcp_server`
(패키지 상대 import, 예: tests/test_mcp_routing.py)로도, `mcp_server`
(backend를 sys.path에 넣고 스크립트로, 예: tests/test_naesin_wiki_v1.py)로도
둘 다 임포트되므로, 모듈 최상단에서 형제 모듈을 `import question_contract`처럼
직접 끌어오면 전자의 임포트 방식에서 깨진다.

인증: 운영 사이트의 사용자 로그인 세션(Spring JWT)을 MCP 인증으로 재사용하지
않는다. 외부 MCP client는 환경변수 BUILDIT_MCP_API_KEY와 정확히 일치하는
`Authorization: Bearer <token>` 헤더 또는 `X-Api-Key: <token>` 헤더 중 하나만
있으면 이 아래 모든 요청을 통과한다 — 일부 MCP client(예: Claude Custom
Connector)가 Authorization 헤더를 예약어처럼 취급해 커스텀 값을 그대로 보내지
못하는 경우의 대안 경로다. 두 비교 모두 constant-time(hmac.compare_digest)으로
한다. 키를 소스에 하드코딩하지 않고, 값이 비어 있으면(설정 안 됨) 모든 요청을
503으로 막는다(우연히 인증 없이 열리는 것을 fail-closed로 방지).
"""
import hmac
import logging
import os
import uuid

if __package__:
    from . import workspace as wiki_workspace
else:
    import workspace as wiki_workspace

from mcp.server.mcpserver import MCPServer
from pydantic import ValidationError
from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

MCP_API_KEY_ENV = 'BUILDIT_MCP_API_KEY'
MCP_ENDPOINT_PATHS = frozenset(('/api/mcp', '/api/mcp/'))
logger = logging.getLogger(__name__)

mcp = MCPServer(
    name='buildit-wiki',
    # save_generated_test 추가 시 bump — 일부 MCP client가 serverInfo.version 변화로
    # tool 목록 캐시를 무효화한다(재접속 없이도 새 tool을 다시 discover하도록).
    version='2',
    instructions=(
        '내신 Wiki 시험 Context(학교/학년/연도/학기/시험)를 읽고 FINAL/직보 완성본을 '
        '저장한다. get_final_context/get_jikbo_context가 반환한 자료를 다시 스스로 '
        '조립하지 말고 그대로 신뢰해서 사용하라. FINAL/직보 완성본을 만든 뒤에는 '
        'save_final/save_jikbo로 저장해야 Wiki에 영구 보관된다. '
        'Wiki 학습자료(교과서 지문·프린트 등)를 대상으로 시험문항을 생성했다면, 마지막 단계로 '
        'save_generated_test를 호출해 Generated Question Store에 자동 저장한다 — 사용자가 따로 '
        '"저장해줘"라고 말하지 않아도 이것이 기본 동작이다. 저장이 실패하면 문제 생성 성공과 '
        '구분해서 generation: success / storage: failed 로 보고한다.'
    ),
)


@mcp.tool()
def get_final_context(school: str, grade: str, year: int, semester: str, exam: str) -> dict:
    """FINAL Workspace Context를 반환한다. 출제자 Digital Twin, 학교 프린트, 학생 오답,
    수업 인사이트, 기존 제작 문제, 기존 FINAL 완성본을 시험 Context(학교/학년/연도/학기/시험)
    기준으로 한 번에 담아 반환한다. 운영 GET /api/workspaces/final/context와 완전히
    같은 데이터이며, 화면을 다시 조립하지 않고 DB 원본 그대로 전달한다."""
    from main import get_final_workspace_context
    return get_final_workspace_context(school=school, grade=grade, year=year, semester=semester, exam=exam)


@mcp.tool()
def get_jikbo_context(school: str, grade: str, year: int, semester: str, exam: str) -> dict:
    """직보 Workspace Context를 반환한다. FINAL 완성본이 아직 없으면 ready=false와 함께
    빈 자료만 반환한다. FINAL이 있으면 latest_final, FINAL 완성 이후에 기록된 수업
    인사이트(post_final_insights), 학생 반복 오답만 담는다 — 학교 프린트 전체/제작 문제
    전체/Digital Twin 전체는 FINAL 제작 과정에서 이미 반영된 자료라 다시 담지 않는다.
    운영 GET /api/workspaces/jikbo/context와 완전히 같은 데이터다."""
    from main import get_jikbo_workspace_context
    return get_jikbo_workspace_context(school=school, grade=grade, year=year, semester=semester, exam=exam)


def _save_deliverable(kind: str, school: str, grade: str, year: int, semester: str, exam: str,
                       title: str, html_text: str, json_text: str) -> dict:
    school, grade, semester, exam = school.strip(), grade.strip(), semester.strip(), exam.strip()
    if not school or not grade or not semester or not exam:
        raise ValueError('school/grade/semester/exam은 비어 있을 수 없습니다.')
    if not html_text.strip() and not json_text.strip():
        raise ValueError('html_text 또는 json_text 중 최소 하나는 있어야 합니다.')
    from fastapi import HTTPException
    from main import create_final_deliverable
    try:
        return create_final_deliverable(
            kind, school, grade, year, semester, exam, title,
            None, None, json_text, html_text=html_text,
        )
    except HTTPException as exc:
        raise ValueError(str(exc.detail)) from exc


@mcp.tool()
def save_final(school: str, grade: str, year: int, semester: str, exam: str,
               title: str = '', html_text: str = '', json_text: str = '') -> dict:
    """LLM이 완성한 FINAL 결과물을 kind='final'로 저장한다. html_text(완성된 HTML) 또는
    json_text(구조화된 JSON 결과물) 중 최소 하나는 반드시 있어야 한다. 기존
    POST /api/final-deliverables와 완전히 같은 저장 경로를 그대로 사용하며, 새 저장
    로직을 만들지 않는다. 저장 직후 get_final_context를 다시 호출하면 결과가 보인다."""
    return _save_deliverable('final', school, grade, year, semester, exam, title, html_text, json_text)


@mcp.tool()
def save_jikbo(school: str, grade: str, year: int, semester: str, exam: str,
               title: str = '', html_text: str = '', json_text: str = '') -> dict:
    """LLM이 완성한 직보 결과물을 kind='jikbo'로 저장한다. save_final과 같은 저장
    경로를 kind만 바꿔 그대로 사용한다. 저장 직후 get_jikbo_context를 다시 호출하면
    jikbo_deliverables에서 결과가 보인다."""
    return _save_deliverable('jikbo', school, grade, year, semester, exam, title, html_text, json_text)


@mcp.tool()
def save_generated_questions(school: str, grade: str, exam: str, questions: list[dict],
                              exam_title: str = '', exam_group_id: str = '') -> dict:
    """[쓰기 도구] 사용자가 "이 문제들을 Wiki에 저장해줘"처럼 명시적으로 저장을 요청했을 때만
    사용한다. LLM이 검수를 마친 문항을 강사 문제 DB(generated_questions)에 저장한다.

    questions의 각 항목은 새 스키마를 만들지 말고 기존 '기존 문제 변형' 문항 계약과 완전히 같은
    canonical 필드를 그대로 갖춰야 한다 — type('mc'|'sa'), questionType, sourceText, stem,
    options(list[str]), answer(list[int]), multi(bool), score(int), conditions(list[str]),
    answerFormat, acceptedAnswers(list[str]), sampleAnswer, gradingRule. mc/sa 어느 쪽이든 모든
    필드가 필수이며, 해당 없는 필드는 빈 값([] 또는 '')으로 채운다(예: mc 문항도 conditions=[],
    answerFormat='' 등을 반드시 포함). 저장 전 이 계약으로 전체 문항을 검증하며, 하나라도
    검증에 실패하면 아무 것도 저장하지 않고 오류를 반환한다(all-or-nothing). 저장 직후
    get_final_context를 다시 호출하면 generated_questions에서 결과가 보인다.

    원본 학습자료·페이지·출제자 Digital Twin·난이도·target vocabulary까지 함께 묶어 '시험' 단위로
    영구 저장하려면 save_generated_test를 쓴다 — Wiki 생성 문항의 기본 저장 경로다."""
    import question_contract as qc
    from fastapi import HTTPException
    from main import _save_generated, get_db

    school, grade, exam = school.strip(), grade.strip(), exam.strip()
    if not school or not grade or not exam:
        raise ValueError('school/grade/exam은 비어 있을 수 없습니다.')
    if not questions:
        raise ValueError('questions는 최소 1개 이상이어야 합니다.')
    try:
        validated = qc.validate_transform({'examTitle': exam_title, 'questions': questions})
    except ValidationError as exc:
        raise ValueError('문항 검증 실패(저장하지 않음): ' + qc.validation_fields(exc)) from exc

    title = exam_title.strip() or validated.get('examTitle') or f'{school} {grade} MCP 저장 문항'
    group_id = exam_group_id.strip() or f'mcp_{uuid.uuid4().hex}'
    twin_like = {'id': None, 'school': school, 'grade': grade, 'exam': exam}
    try:
        with get_db() as conn:
            saved = _save_generated(conn, twin_like, 'mcp', validated['questions'], title, group_id)
    except HTTPException as exc:
        raise ValueError(str(exc.detail)) from exc
    return {'examTitle': title, 'examGroupId': group_id, 'questions': saved}


@mcp.tool()
def save_generated_test(school: str, grade: str, source_material: str, questions: list[dict],
                        source_page: str = '', test_type: str = '', title: str = '',
                        digital_twin: str = '', twin_pattern: str = '', difficulty: str = '',
                        target_vocabulary: list[str] = [], target_grammar: list[str] = [],
                        target_passage: str = '', year: int | None = None, semester: str = '',
                        exam: str = '전체', publisher: str = '', lesson: str = '',
                        material_type: str = '', external_id: str = '') -> dict:
    """Wiki에서 생성한 시험문항을 '시험(Test)' 단위로 Generated Question Store에 영구 저장한다.

    Wiki 학습자료로 문제를 생성했으면 사용자가 따로 저장을 지시하지 않아도 문제 생성 workflow의
    마지막 단계로 이 도구를 호출한다(생성 → 검증 → save_generated_test).

    관계: Source Material(school+grade+source_material로 upsert) → Generated Test(이 저장 1건) →
    Questions. source_page로 원본 페이지에, digital_twin/twin_pattern으로 출제자 Digital Twin에 연결된다.

    questions의 각 항목은 새 스키마를 만들지 말고 save_generated_questions와 동일한 canonical 계약을
    그대로 갖춘다 — type('mc'|'sa'), questionType, sourceText, stem, options, answer, multi, score,
    conditions, answerFormat, acceptedAnswers, sampleAnswer, gradingRule (해당 없는 필드는 [] 또는 '').
    저장 전 전체 문항을 검증하고, 하나라도 실패하면 아무 것도 저장하지 않는다(all-or-nothing).

    idempotency: external_id가 비면 요청 내용으로 안정적인 키를 파생한다. 같은 external_id로 다시
    저장하면 새 시험을 만들지 않고 기존 test_id를 그대로 돌려준다(duplicate=True).

    반환: {saved: true, test_id, question_count, source_material, source_page, duplicate}.
    저장 실패 시 문제 생성 성공과 구분되도록 'storage: failed —' 접두가 붙은 오류를 낸다."""
    import question_contract as qc
    from fastapi import HTTPException
    from main import GeneratedTestCreate, create_generated_test

    try:
        payload = GeneratedTestCreate(
            school=school, grade=grade, source_material=source_material, questions=list(questions or []),
            source_page=source_page, test_type=test_type, title=title, digital_twin=digital_twin,
            twin_pattern=twin_pattern, difficulty=difficulty, target_vocabulary=list(target_vocabulary or []),
            target_grammar=list(target_grammar or []), target_passage=target_passage, year=year,
            semester=semester, exam=exam or '전체', publisher=publisher, lesson=lesson,
            material_type=material_type, external_id=external_id,
        )
    except ValidationError as exc:
        raise ValueError('입력 검증 실패: ' + qc.validation_fields(exc)) from exc
    try:
        result = create_generated_test(payload)
    except HTTPException as exc:
        raise ValueError('storage: failed — ' + str(exc.detail)) from exc
    return {
        'saved': True, 'test_id': result['test_id'], 'question_count': result['question_count'],
        'source_material': result['source_material'], 'source_page': result['source_page'],
        'duplicate': result.get('duplicate', False),
    }


@mcp.tool()
def save_class_insights(content: str, source: str, school: str = '', grade: str = '',
                         year: int | None = None, semester: str = '', exam: str = '',
                         student_context: str = '', lesson_context: str = '') -> dict:
    """[쓰기 도구] 사용자가 "이 인사이트를 Wiki에 저장해줘"처럼 명시적으로 저장을 요청했을 때만
    사용한다. 문제 제작 과정에서 얻은 재사용 가능한 인사이트를 기존 수업 인사이트
    저장소(educator_insights)에 저장한다. source는 반드시 이 기록을 실제로 만든 주체를
    가리켜야 한다 — 'director'(박원장), 'school_teacher'(학교 선생님), 'claude'(Claude),
    'chatgpt'(ChatGPT) 중 하나. 저장 직후 get_final_context를 다시 호출하면 class_insights에서
    source 값과 함께 결과가 보인다."""
    import question_contract as qc
    from fastapi import HTTPException
    from main import EducatorInsightCreate, create_educator_insight

    try:
        payload = EducatorInsightCreate(
            content=content, school=school, grade=grade, student_context=student_context,
            lesson_context=lesson_context, year=year, semester=semester, exam=exam, source=source,
        )
    except ValidationError as exc:
        raise ValueError('인사이트 검증 실패: ' + qc.validation_fields(exc)) from exc
    try:
        return create_educator_insight(payload)
    except HTTPException as exc:
        raise ValueError(str(exc.detail)) from exc


class _BearerAuthMiddleware:
    """운영 로그인 세션과 무관한 MCP 전용 인증. `Authorization: Bearer <key>` 또는
    `X-Api-Key: <key>` 중 하나만 정확히 일치하면 통과시킨다(constant-time 비교).
    환경변수가 비어 있으면(설정 안 됨) 항상 503으로 막아 실수로 인증 없이 열리는
    것을 막는다(fail-closed)."""

    def __init__(self, app: ASGIApp, api_key: str) -> None:
        self.app = app
        self.api_key = api_key

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope['type'] != 'http':
            await self.app(scope, receive, send)
            return
        if not self.api_key:
            response = JSONResponse(
                {'error': f'MCP server is not configured ({MCP_API_KEY_ENV} is not set)'}, status_code=503)
            await response(scope, receive, send)
            return
        raw_headers = scope.get('headers') or []
        headers = dict(raw_headers)
        auth_header = headers.get(b'authorization', b'').decode('latin-1')
        api_key_header = headers.get(b'x-api-key', b'').decode('latin-1')
        bearer_ok = hmac.compare_digest(auth_header, f'Bearer {self.api_key}')
        api_key_ok = bool(api_key_header) and hmac.compare_digest(api_key_header, self.api_key)
        if not (bearer_ok or api_key_ok):
            authorization_header_count = sum(
                1 for name, _value in raw_headers if name.lower() == b'authorization')
            bearer_prefix_present = auth_header.startswith('Bearer ')
            received_token = auth_header[len('Bearer '):] if bearer_prefix_present else ''
            logger.warning(
                'MCP bearer authentication rejected: '
                'authorization_present=%s authorization_header_count=%d '
                'bearer_prefix_present=%s received_token_length=%d expected_token_length=%d '
                'compare_digest_match=%s header_outer_whitespace=%s token_outer_whitespace=%s',
                authorization_header_count > 0,
                authorization_header_count,
                bearer_prefix_present,
                len(received_token),
                len(self.api_key),
                hmac.compare_digest(received_token, self.api_key),
                auth_header != auth_header.strip(),
                received_token != received_token.strip(),
            )
            response = JSONResponse({'error': 'unauthorized'}, status_code=401)
            await response(scope, receive, send)
            return
        await self.app(scope, receive, send)


class McpEndpointMiddleware:
    """두 공개 MCP URL을 redirect 없이 같은 내부 SDK endpoint로 전달한다.

    Starlette의 ``app.mount('/api/mcp', ...)``는 mount root인 ``/api/mcp``를
    ``/api/mcp/``로 307 redirect한다. 일부 MCP client는 redirect 시
    Authorization 헤더를 보존하지 않으므로, 두 exact path를 직접 가로채 SDK가
    등록한 내부 ``/`` route로 전달한다. 그 외 Wiki API path는 변경하지 않는다.
    """

    def __init__(
        self, app: ASGIApp, mcp_app: ASGIApp,
        workspace: wiki_workspace.WikiWorkspace | None = None,
    ) -> None:
        self.app = app
        self.mcp_app = mcp_app
        self.workspace = workspace

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope['type'] == 'http' and scope.get('path') in MCP_ENDPOINT_PATHS:
            mcp_scope = dict(scope)
            mcp_scope['path'] = '/'
            mcp_scope['raw_path'] = b'/'
            token = wiki_workspace.set_current_workspace(self.workspace) if self.workspace else None
            try:
                await self.mcp_app(mcp_scope, receive, send)
            finally:
                if token is not None:
                    wiki_workspace.reset_current_workspace(token)
            return
        await self.app(scope, receive, send)


def build_mcp_asgi_app() -> Starlette:
    """main.py가 exact-path middleware로 연결할 인증 적용된 MCP ASGI app을 만든다.
    stateless_http=True: 리버스 프록시(Spring/nginx) 뒤에서 세션을 오래 들고 있지
    않도록 각 요청을 독립적으로 처리한다 — 소규모 운영에 가장 단순하고 안정적인
    구성이다(§ 조사 보고 참고)."""
    inner_app = mcp.streamable_http_app(streamable_http_path='/', stateless_http=True)
    api_key = os.getenv(MCP_API_KEY_ENV, '').strip()
    return _BearerAuthMiddleware(inner_app, api_key)
