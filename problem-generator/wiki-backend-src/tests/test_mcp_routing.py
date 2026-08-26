from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from backend.mcp_server import McpEndpointMiddleware, _BearerAuthMiddleware


async def _mcp_endpoint(request):
    return JSONResponse({'path': request.scope['path']})


async def _wiki_endpoint(_request):
    return JSONResponse({'wiki': True})


def _test_app(api_key: str = 'test-key') -> TestClient:
    mcp_app = Starlette(routes=[Route('/', _mcp_endpoint, methods=['POST'])])
    authenticated_mcp_app = _BearerAuthMiddleware(mcp_app, api_key)
    wiki_app = Starlette(routes=[Route('/api/health', _wiki_endpoint)])
    wiki_app.add_middleware(McpEndpointMiddleware, mcp_app=authenticated_mcp_app)
    return TestClient(wiki_app, follow_redirects=False)


def test_mcp_paths_are_handled_directly_with_the_same_authentication():
    client = _test_app()

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path, headers={'Authorization': 'Bearer test-key'})

        assert response.status_code == 200
        assert response.history == []
        assert response.json() == {'path': '/'}


def test_mcp_paths_both_reject_invalid_bearer_token():
    client = _test_app()

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path, headers={'Authorization': 'Bearer wrong-key'})

        assert response.status_code == 401
        assert response.history == []
        assert response.json() == {'error': 'unauthorized'}


def test_mcp_paths_both_fail_closed_when_api_key_is_not_configured():
    client = _test_app(api_key='')

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path, headers={'Authorization': 'Bearer anything'})

        assert response.status_code == 503
        assert response.history == []


def test_non_mcp_wiki_api_is_unchanged():
    response = _test_app().get('/api/health')

    assert response.status_code == 200
    assert response.json() == {'wiki': True}
