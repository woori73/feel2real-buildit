import datetime as dt
import unittest
import newstart_roster_sync as r


class RosterSyncTest(unittest.TestCase):
    def item(self):
        return {'cafe_id':31312978,'member_tag':'test1234abcd','nickname':'합성회원',
                'naver_id_prefix':'synt','grade':'감사멤버','member_level':150,
                'verified_by':'합성운영자','observed_at':dt.datetime.now(dt.timezone.utc).isoformat()}

    def test_existing_roster_only_append_no_account_grant_or_old_updates(self):
        sql=r.statement(self.item())
        self.assertIn('INSERT INTO newstart_thanks_roster',sql)
        self.assertNotRegex(sql,r'\b(?:UPDATE|DELETE|ALTER|CREATE|DROP)\b')
        self.assertNotIn('newstart_member_grants',sql)
        self.assertIn("'UNMATCHED',NULL,NULL",sql)
        self.assertIn('@tag_exists=0 AND @nick_exists=0',sql)

    def test_only_fresh_real_cafe_grade_accepts(self):
        for changes in [{'grade':'일반멤버'},{'member_level':100},{'cafe_id':1},
                        {'member_tag':'bad'},{'verified_by':''},
                        {'observed_at':'2020-01-01T00:00:00+00:00'}]:
            with self.assertRaises(ValueError):r.statement({**self.item(),**changes})

    def test_nickname_is_encoded_not_interpolated_sql(self):
        item={**self.item(),'nickname':"합성'); DROP TABLE users; --"}
        sql=r.statement(item)
        self.assertNotIn('DROP TABLE',sql)
        self.assertIn(item['nickname'].encode().hex(),sql)

    def test_idempotent_existing_receipt_and_conflict(self):
        self.assertEqual(r.sync(self.item(),lambda _: 'ROSTER\t19\nRESULT\tOK\n'),19)
        for response in ['RESULT\tIDENTITY_REVIEW\n','RESULT\tLOCK_FAILED\n','']:
            with self.assertRaises(RuntimeError):r.sync(self.item(),lambda _:response)


if __name__=='__main__':unittest.main()
