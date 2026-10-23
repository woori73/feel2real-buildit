"""Isolated read-only delegation tests. No production paths or real episode assets."""
import os
import sys
import time
import hmac
import hashlib
import sqlite3
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_workspace_isolation import WorkspaceIsolationTest, identity_headers, SECRET
import main
import workspace

class McpReadOnlyTest(WorkspaceIsolationTest):
    def readonly_headers(self, uid, login, path, method='GET'):
        headers = identity_headers(uid, login)
        timestamp = str(int(time.time()))
        payload = f'mcp-read-only\n{uid}\nGET\n{path}\n{timestamp}'.encode()
        headers.update({'X-BuildIt-ReadOnly-Time':timestamp,
            'X-BuildIt-ReadOnly-Signature':hmac.new(SECRET.encode(),payload,hashlib.sha256).hexdigest()})
        return headers

    def test_existing_workspace_read_preserves_catalog_and_db(self):
        self.client.get('/api/schools', headers=identity_headers('t1','tutor-one'))
        resolved = workspace.existing_workspace(workspace.WikiIdentity('t1','tutor-one','TUTOR'), main.DB_PATH)
        paths = [Path(os.environ['BUILDIT_WIKI_WORKSPACE_CATALOG']),Path(resolved.database_path)]
        before = [p.read_bytes() for p in paths]
        response = self.client.get('/api/schools',headers=self.readonly_headers('t1','tutor-one','/api/schools'))
        self.assertEqual(response.status_code,200,response.text)
        self.assertEqual(before,[p.read_bytes() for p in paths])

    def test_new_workspace_is_not_provisioned(self):
        catalog = Path(os.environ['BUILDIT_WIKI_WORKSPACE_CATALOG'])
        before = catalog.read_bytes() if catalog.exists() else None
        response = self.client.get('/api/schools',headers=self.readonly_headers('unseen','new-user','/api/schools'))
        self.assertEqual(response.status_code,409,response.text)
        self.assertEqual(before,catalog.read_bytes() if catalog.exists() else None)

    def test_signature_cannot_be_moved_or_used_on_write(self):
        headers=self.readonly_headers('t1','tutor-one','/api/schools')
        self.assertEqual(self.client.get('/api/students',headers=headers).status_code,403)
        self.assertEqual(self.client.post('/api/schools',headers=headers,json={}).status_code,403)

    def test_sqlite_connection_rejects_dml(self):
        token=workspace._read_only_context.set(True)
        try:
            with main.get_db() as conn:
                with self.assertRaises(sqlite3.OperationalError):
                    conn.execute("CREATE TABLE forbidden_write (id INTEGER)")
        finally:
            workspace._read_only_context.reset(token)
