import logging

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


def test_mcp_paths_accept_x_api_key_when_authorization_is_absent():
    client = _test_app()

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path, headers={'X-Api-Key': 'test-key'})

        assert response.status_code == 200
        assert response.json() == {'path': '/'}


def test_mcp_paths_reject_wrong_x_api_key_with_no_authorization():
    client = _test_app()

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path, headers={'X-Api-Key': 'wrong-key'})

        assert response.status_code == 401
        assert response.json() == {'error': 'unauthorized'}


def test_mcp_paths_reject_missing_authorization_and_x_api_key():
    client = _test_app()

    for path in ('/api/mcp', '/api/mcp/'):
        response = client.post(path)

        assert response.status_code == 401
        assert response.json() == {'error': 'unauthorized'}


def test_mcp_paths_accept_valid_bearer_even_with_wrong_x_api_key_present():
    client = _test_app()

    response = client.post(
        '/api/mcp', headers={'Authorization': 'Bearer test-key', 'X-Api-Key': 'wrong-key'})

    assert response.status_code == 200


def test_mcp_paths_accept_valid_x_api_key_even_with_wrong_bearer_present():
    client = _test_app()

    response = client.post(
        '/api/mcp', headers={'Authorization': 'Bearer wrong-key', 'X-Api-Key': 'test-key'})

    assert response.status_code == 200


def test_mcp_401_logs_only_safe_bearer_diagnostics(caplog):
    secret_token = 'never-log-this-secret-token'

    with caplog.at_level(logging.WARNING, logger='backend.mcp_server'):
        response = _test_app(api_key='expected-key').post(
            '/api/mcp', headers={'Authorization': f' Bearer {secret_token} '})

    assert response.status_code == 401
    assert len(caplog.records) == 1
    message = caplog.records[0].getMessage()
    assert 'authorization_present=True' in message
    assert 'authorization_header_count=1' in message
    assert 'bearer_prefix_present=False' in message
    assert 'received_token_length=0' in message
    assert 'expected_token_length=12' in message
    assert 'compare_digest_match=False' in message
    assert 'header_outer_whitespace=True' in message
    assert 'token_outer_whitespace=False' in message
    assert secret_token not in message
    assert 'Authorization' not in message


def test_mcp_401_logs_bearer_token_length_and_whitespace_without_value(caplog):
    secret_token = 'wrong-secret '

    with caplog.at_level(logging.WARNING, logger='backend.mcp_server'):
        response = _test_app(api_key='expected-key').post(
            '/api/mcp/', headers={'Authorization': f'Bearer {secret_token}'})

    assert response.status_code == 401
    message = caplog.records[0].getMessage()
    assert 'authorization_present=True' in message
    assert 'authorization_header_count=1' in message
    assert 'bearer_prefix_present=True' in message
    assert f'received_token_length={len(secret_token)}' in message
    assert 'expected_token_length=12' in message
    assert 'compare_digest_match=False' in message
    assert 'header_outer_whitespace=True' in message
    assert 'token_outer_whitespace=True' in message
    assert secret_token.strip() not in message


def test_mcp_401_logs_missing_authorization_header_safely(caplog):
    with caplog.at_level(logging.WARNING, logger='backend.mcp_server'):
        response = _test_app(api_key='expected-key').post('/api/mcp')

    assert response.status_code == 401
    message = caplog.records[0].getMessage()
    assert 'authorization_present=False' in message
    assert 'authorization_header_count=0' in message
    assert 'bearer_prefix_present=False' in message
    assert 'received_token_length=0' in message
    assert 'expected_token_length=12' in message
    assert 'compare_digest_match=False' in message
    assert 'header_outer_whitespace=False' in message
    assert 'token_outer_whitespace=False' in message


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
