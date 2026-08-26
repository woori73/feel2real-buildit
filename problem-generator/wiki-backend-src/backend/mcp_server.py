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

인증: 운영 사이트의 사용자 로그인 세션(Spring JWT)을 MCP 인증으로 재사용하지
않는다. 외부 MCP client는 환경변수 BUILDIT_MCP_API_KEY와 정확히 일치하는
`Authorization: Bearer <token>` 헤더가 있어야 이 아래 모든 요청을 통과한다.
키를 소스에 하드코딩하지 않고, 값이 비어 있으면(설정 안 됨) 모든 요청을
503으로 막는다(우연히 인증 없이 열리는 것을 fail-closed로 방지).
"""
import hmac
import logging
import os

from mcp.server.mcpserver import MCPServer
from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

MCP_API_KEY_ENV = 'BUILDIT_MCP_API_KEY'
MCP_ENDPOINT_PATHS = frozenset(('/api/mcp', '/api/mcp/'))
logger = logging.getLogger(__name__)

mcp = MCPServer(
    name='buildit-wiki',
    instructions=(
        '내신 Wiki 시험 Context(학교/학년/연도/학기/시험)를 읽고 FINAL/직보 완성본을 '
        '저장한다. get_final_context/get_jikbo_context가 반환한 자료를 다시 스스로 '
        '조립하지 말고 그대로 신뢰해서 사용하라. FINAL/직보 완성본을 만든 뒤에는 '
        'save_final/save_jikbo로 저장해야 Wiki에 영구 보관된다.'
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


class _BearerAuthMiddleware:
    """운영 로그인 세션과 무관한 MCP 전용 인증. 환경변수가 비어 있으면(설정 안 됨)
    항상 503으로 막아 실수로 인증 없이 열리는 것을 막는다(fail-closed)."""

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
        if auth_header != f'Bearer {self.api_key}':
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

    def __init__(self, app: ASGIApp, mcp_app: ASGIApp) -> None:
        self.app = app
        self.mcp_app = mcp_app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope['type'] == 'http' and scope.get('path') in MCP_ENDPOINT_PATHS:
            mcp_scope = dict(scope)
            mcp_scope['path'] = '/'
            mcp_scope['raw_path'] = b'/'
            await self.mcp_app(mcp_scope, receive, send)
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
