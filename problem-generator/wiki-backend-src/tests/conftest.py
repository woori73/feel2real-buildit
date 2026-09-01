import os

# Unit tests exercise the FastAPI app directly, without the authenticated Spring reverse proxy.
# Production defaults to fail-closed because this explicit flag is absent there.
os.environ.setdefault('BUILDIT_WIKI_ALLOW_UNSIGNED_IDENTITY', '1')
os.environ.setdefault('WIKI_IDENTITY_SECRET', 'wiki-test-secret-at-least-32-bytes-long')
