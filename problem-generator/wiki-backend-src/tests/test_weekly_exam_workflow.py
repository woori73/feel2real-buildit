import os
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

BACKEND=Path(__file__).resolve().parents[1]/'backend'
sys.path.insert(0,str(BACKEND))
os.environ['BUILDIT_WIKI_DB_PATH']=str(Path(tempfile.gettempdir())/'weekly-bootstrap.db')
import main
import weekly_exam
import workspace as wiki_workspace


class WeeklyExamWorkflowTest(unittest.TestCase):
    CTX=dict(school='신구중',grade='3학년',year=2026,semester='2학기',examType='중간고사')

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        main.DB_PATH=str(Path(self.tmp.name)/'test.db')
        main.init_db();main.migrate_schema()
        self.identity_token=wiki_workspace.set_current_identity(wiki_workspace.WikiIdentity('1','teacher','TUTOR'))
        weekly_exam.configure(main.get_db,self.context,main._sanitize_exam_html)

    def tearDown(self):
        wiki_workspace.reset_current_identity(self.identity_token);self.tmp.cleanup()

    def context(self,school,grade,year,semester,exam):
        return {'exam_context':dict(school=school,grade=grade,year=year,semester=semester,exam=exam),
          'digital_twin':{'id':7,'version':3},'past_exams':[{'id':11,'material_type':'PAST_EXAM'}],
          'school_prints':[{'id':12,'material_type':'SCHOOL_PRINT'}],'ai_work_logs':[{'id':13,'material_type':'AI_WORK_LOG'}],
          'student_errors':[],'class_insights':[],'generated_questions':[],'generated_tests':[],'context_budget':{}}

    def payload(self,**overrides):
        values={**self.CTX,'round':1,'setType':'40A','questionCount':40,
          'studentPaper':'<h1>신구중3 문제</h1><script>alert(1)</script>',
          'answerExplanation':'<h1>정답</h1><p>1. ③</p>','productionVerification':'# 출제근거\n- 실제 기출 연결',
          'sourceMaterialIds':[11,12,13],'contextStatus':'LIVING'}
        values.update(overrides);return weekly_exam.WeeklyExamReviewRequest(**values)

    def test_registers_three_artifacts_as_review_with_snapshot(self):
        saved=weekly_exam.register_review(self.payload(),actor='mcp-agent')
        self.assertEqual('REVIEW',saved['status']);self.assertEqual([11,12,13],saved['source_material_ids'])
        self.assertEqual(3,saved['context_snapshot']['digitalTwin']['version'])
        self.assertEqual('LIVING',saved['context_snapshot']['contextStatus'])
        for kind in weekly_exam.ARTIFACTS:
            response=weekly_exam.view_artifact(saved['id'],kind)
            self.assertNotIn('<script',response.body.decode().lower())

    def test_agent_contract_cannot_supply_privileged_status(self):
        self.assertNotIn('status',weekly_exam.WeeklyExamReviewRequest.model_fields)
        self.assertEqual('REVIEW',weekly_exam.register_review(self.payload(),actor='claude')['status'])

    def test_duplicate_active_key_conflicts_instead_of_overwriting(self):
        weekly_exam.register_review(self.payload())
        with self.assertRaises(HTTPException) as raised: weekly_exam.register_review(self.payload())
        self.assertEqual(409,raised.exception.status_code)

    def test_snapshot_rejects_material_outside_selected_context(self):
        with self.assertRaises(HTTPException) as raised:
            weekly_exam.register_review(self.payload(sourceMaterialIds=[999]))
        self.assertEqual(400,raised.exception.status_code)

    def test_admin_gate_and_ordered_transitions(self):
        saved=weekly_exam.register_review(self.payload())
        with self.assertRaises(HTTPException) as raised:
            weekly_exam.transition_weekly_exam(saved['id'],weekly_exam.TransitionRequest(action='approve'),None)
        self.assertEqual(403,raised.exception.status_code)
        wiki_workspace.reset_current_identity(self.identity_token)
        self.identity_token=wiki_workspace.set_current_identity(wiki_workspace.WikiIdentity('2','admin-f2r','ADMIN'))
        approved=weekly_exam.transition_weekly_exam(saved['id'],weekly_exam.TransitionRequest(action='approve'),None)
        final=weekly_exam.transition_weekly_exam(saved['id'],weekly_exam.TransitionRequest(action='finalize'),None)
        published=weekly_exam.transition_weekly_exam(saved['id'],weekly_exam.TransitionRequest(action='publish'),None)
        self.assertEqual(('APPROVED','FINAL','PUBLISHED'),(approved['status'],final['status'],published['status']))

    def test_only_published_is_returned_by_public_query(self):
        first=weekly_exam.register_review(self.payload())
        second=weekly_exam.register_review(self.payload(round=2))
        self.assertEqual([],weekly_exam.list_published_weekly_exams('신구중','3학년',2026,'2학기','중간고사'))
        wiki_workspace.reset_current_identity(self.identity_token)
        self.identity_token=wiki_workspace.set_current_identity(wiki_workspace.WikiIdentity('2','admin-f2r','ADMIN'))
        for action in ('approve','finalize','publish'):
            weekly_exam.transition_weekly_exam(first['id'],weekly_exam.TransitionRequest(action=action),None)
        public=weekly_exam.list_published_weekly_exams('신구중','3학년',2026,'2학기','중간고사')
        self.assertEqual([first['id']],[row['id'] for row in public])
        self.assertNotEqual(second['id'],public[0]['id'])


if __name__=='__main__':unittest.main()
