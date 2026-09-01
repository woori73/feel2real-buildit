import hashlib
import hmac
import os
import sqlite3
import tempfile
import time
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

import main
import workspace


SECRET = 'wiki-test-secret-at-least-32-bytes-long'


def identity_headers(user_id: str, login_id: str, role: str = 'TUTOR') -> dict[str, str]:
    timestamp = str(int(time.time()))
    identity = workspace.WikiIdentity(user_id, login_id, role)
    signature = hmac.new(SECRET.encode(), workspace.signature_payload(identity, timestamp), hashlib.sha256).hexdigest()
    return {
        'X-BuildIt-User-Id': user_id,
        'X-BuildIt-Login-Id': login_id,
        'X-BuildIt-Role': role,
        'X-BuildIt-Timestamp': timestamp,
        'X-BuildIt-Signature': signature,
    }


def workspace_headers(headers: dict[str, str], workspace_id: str) -> dict[str, str]:
    return {**headers, 'X-BuildIt-Wiki-Workspace': workspace_id}


class WorkspaceIsolationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old_db = main.DB_PATH
        self.old_catalog = os.environ.get('BUILDIT_WIKI_WORKSPACE_CATALOG')
        self.old_unsigned = os.environ.pop('BUILDIT_WIKI_ALLOW_UNSIGNED_IDENTITY', None)
        self.old_wiki_secret = os.environ.get('WIKI_IDENTITY_SECRET')
        self.old_jwt_secret = os.environ.get('JWT_SECRET')
        self.old_app_jwt_secret = os.environ.get('APP_JWT_SECRET')
        main.DB_PATH = str(Path(self.temp.name) / 'legacy.db')
        os.environ['BUILDIT_WIKI_WORKSPACE_CATALOG'] = str(Path(self.temp.name) / 'catalog.db')
        os.environ['WIKI_IDENTITY_SECRET'] = SECRET
        main.init_db()
        main.migrate_schema()
        with main.get_db() as conn:
            workspace.migrate_owned_tables(conn, workspace.LEGACY_WORKSPACE_ID)
        self.client = TestClient(main.app)

    def tearDown(self):
        main.DB_PATH = self.old_db
        if self.old_catalog is None:
            os.environ.pop('BUILDIT_WIKI_WORKSPACE_CATALOG', None)
        else:
            os.environ['BUILDIT_WIKI_WORKSPACE_CATALOG'] = self.old_catalog
        if self.old_unsigned is not None:
            os.environ['BUILDIT_WIKI_ALLOW_UNSIGNED_IDENTITY'] = self.old_unsigned
        for key, value in (
            ('WIKI_IDENTITY_SECRET', self.old_wiki_secret),
            ('JWT_SECRET', self.old_jwt_secret),
            ('APP_JWT_SECRET', self.old_app_jwt_secret),
        ):
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        self.temp.cleanup()

    def create_student(self, headers: dict[str, str], name: str):
        response = self.client.post('/api/students', headers=headers, json={
            'name': name, 'school': '격리중', 'grade': '3학년', 'semester': '2학기', 'exam_type': '중간'
        })
        self.assertEqual(200, response.status_code, response.text)
        return response.json()

    def test_admin_and_modu_share_legacy_but_new_users_start_empty(self):
        admin = identity_headers('1', 'admin', 'ADMIN')
        modu = identity_headers('2', 'modu', 'TUTOR')
        user_a = identity_headers('101', 'new-a')
        self.create_student(admin, 'legacy student')
        self.assertEqual(['legacy student'], [row['name'] for row in self.client.get('/api/students', headers=modu).json()])
        self.assertEqual([], self.client.get('/api/students', headers=user_a).json())

    def test_app_jwt_secret_fallback_accepts_signed_admin_and_modu_bootstrap(self):
        os.environ.pop('WIKI_IDENTITY_SECRET', None)
        os.environ.pop('JWT_SECRET', None)
        os.environ['APP_JWT_SECRET'] = SECRET
        for user_id, login_id, role in (('1', 'admin', 'ADMIN'), ('2', 'modu', 'TUTOR')):
            response = self.client.get('/api/workspace/me', headers=identity_headers(user_id, login_id, role))
            self.assertEqual(200, response.status_code, response.text)
            self.assertEqual('legacy', response.json()['workspace_id'])

    def test_personal_workspace_persists_and_blocks_other_resource_ids(self):
        user_a = identity_headers('101', 'new-a')
        user_b = identity_headers('202', 'new-b')
        a = self.create_student(user_a, 'A student')
        self.create_student(user_b, 'B student one')
        b = self.create_student(user_b, 'B student two')
        self.assertEqual(['A student'], [row['name'] for row in self.client.get('/api/students', headers=user_a).json()])
        denied = self.client.delete(f"/api/students/{b['id']}?role=ADMIN", headers=user_a)
        self.assertIn(denied.status_code, (204, 404))
        self.assertEqual(['B student two', 'B student one'], [row['name'] for row in self.client.get('/api/students', headers=user_b).json()])
        self.assertEqual(a['id'], self.client.get('/api/students', headers=user_a).json()[0]['id'])

    def test_migration_is_idempotent_and_preserves_row_counts(self):
        with sqlite3.connect(main.DB_PATH) as conn:
            conn.execute("INSERT INTO students(name, created_at) VALUES ('legacy', 'now')")
            before = conn.execute('SELECT COUNT(*) FROM students').fetchone()[0]
            first = workspace.migrate_owned_tables(conn, workspace.LEGACY_WORKSPACE_ID)
            second = workspace.migrate_owned_tables(conn, workspace.LEGACY_WORKSPACE_ID)
            after = conn.execute('SELECT COUNT(*) FROM students').fetchone()[0]
            owner = conn.execute('SELECT workspace_id FROM students').fetchone()[0]
        self.assertEqual(before, after)
        self.assertEqual(first['students'], second['students'])
        self.assertEqual(workspace.LEGACY_WORKSPACE_ID, owner)

    def test_unsigned_or_forged_identity_is_rejected(self):
        self.assertEqual(401, self.client.get('/api/students').status_code)
        forged = identity_headers('999', 'admin', 'ADMIN')
        forged['X-BuildIt-Signature'] = '0' * 64
        self.assertEqual(401, self.client.get('/api/students', headers=forged).status_code)

    def test_only_signed_admin_can_list_and_override_user_workspaces(self):
        admin = identity_headers('1', 'admin', 'ADMIN')
        modu = identity_headers('2', 'modu', 'TUTOR')
        user_a = identity_headers('101', 'new-a')
        user_b = identity_headers('202', 'new-b')

        legacy = self.create_student(admin, 'legacy student')
        student_a = self.create_student(user_a, 'A student')
        student_b = self.create_student(user_b, 'B student')
        workspace_a = self.client.get('/api/workspace/me', headers=user_a).json()['workspace_id']
        workspace_b = self.client.get('/api/workspace/me', headers=user_b).json()['workspace_id']

        self.assertEqual(['legacy student'], [row['name'] for row in self.client.get('/api/students', headers=admin).json()])
        self.assertEqual(['legacy student'], [row['name'] for row in self.client.get('/api/students', headers=modu).json()])
        self.assertEqual(['A student'], [row['name'] for row in self.client.get('/api/students', headers=user_a).json()])
        self.assertEqual(['B student'], [row['name'] for row in self.client.get('/api/students', headers=user_b).json()])

        listing = self.client.get('/api/admin/workspaces', headers=admin)
        self.assertEqual(200, listing.status_code, listing.text)
        self.assertEqual({'legacy', workspace_a, workspace_b}, {row['workspace_id'] for row in listing.json()})
        self.assertNotIn('database_path', listing.text)
        self.assertEqual(403, self.client.get('/api/admin/workspaces', headers=modu).status_code)
        self.assertEqual(403, self.client.get('/api/admin/workspaces', headers=user_a).status_code)
        self.assertEqual(200, self.client.get(f'/api/admin/workspaces/{workspace_a}', headers=admin).status_code)
        self.assertEqual(404, self.client.get('/api/admin/workspaces/missing', headers=admin).status_code)

        self.assertEqual(403, self.client.get('/api/students', headers=workspace_headers(modu, workspace_a)).status_code)
        self.assertEqual(403, self.client.get('/api/students', headers=workspace_headers(user_a, workspace_b)).status_code)
        query_escalation = self.client.get(
            '/api/students?role=ADMIN', headers=workspace_headers(user_a, workspace_b)
        )
        self.assertEqual(403, query_escalation.status_code)

        self.assertEqual(['A student'], [row['name'] for row in self.client.get('/api/students', headers=workspace_headers(admin, workspace_a)).json()])
        self.assertEqual(['B student'], [row['name'] for row in self.client.get('/api/students', headers=workspace_headers(admin, workspace_b)).json()])

        note = self.client.post(
            f"/api/students/{student_a['id']}/wrong-notes",
            headers=user_a,
            json={'question_text': 'before'},
        ).json()
        updated = self.client.patch(
            f"/api/students/{student_a['id']}/wrong-notes/{note['id']}",
            headers=workspace_headers(admin, workspace_a),
            json={'question_text': 'after'},
        )
        self.assertEqual(200, updated.status_code, updated.text)
        self.assertEqual('after', updated.json()['wrong_text'])
        deleted = self.client.delete(
            f"/api/students/{student_a['id']}/wrong-notes/{note['id']}",
            headers=workspace_headers(admin, workspace_a),
        )
        self.assertEqual(200, deleted.status_code, deleted.text)
        self.assertEqual([], self.client.get(
            f"/api/students/{student_a['id']}/wrong-notes", headers=user_a
        ).json())

        back_to_default = self.client.get('/api/students', headers=admin)
        self.assertEqual([legacy['id']], [row['id'] for row in back_to_default.json()])


if __name__ == '__main__':
    unittest.main()
