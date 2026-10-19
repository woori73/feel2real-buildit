import copy
import unittest
from prepare_grammar_review_import import adapt

class AdapterFixtureTest(unittest.TestCase):
    def setUp(self):
        self.q={'external_id':'TEST-1','grade':'중1','recommended_grade':'중2','recommended_difficulty':'기본',
            'primary_grammar':'시제','detail_type':'현재 동사 형태','concept_code':'tense',
            'new_question':'현재 습관을 나타내도록 완성하시오. He _____ every day.',
            'choices':[{'label':str(i+1),'text':s} for i,s in enumerate(['walks','walk','walking','to walk','have walked'])],
            'answer':'1','answer_text':'walks','explanation':'현재 단수 주어의 동사 형태이다.',
            'choice_explanations':[{'label':str(i+1),'text':'형태 확인','is_correct':i==0} for i in range(5)],
            'provenance':{'original_ids':['source-1']}}
    def test_lossless_metadata_and_original_grade(self):
        payload,meta,report=adapt([self.q])
        self.assertEqual(payload['records'][0]['school_level'],'M1')
        self.assertEqual(payload['records'][0]['difficulty'],'EASY')
        self.assertEqual(meta[0]['metadata'],self.q)
        self.assertFalse(report['registration_ready'])
    def test_idempotency_does_not_overwrite(self):
        _,meta,_=adapt([self.q]);sha=meta[0]['record_sha256']
        payload,_,report=adapt([self.q],{'TEST-1':sha})
        self.assertEqual(payload['records'],[]);self.assertEqual(report['duplicates'],['TEST-1'])
        payload,_,report=adapt([self.q],{'TEST-1':'different'})
        self.assertEqual(payload['records'],[]);self.assertEqual(report['conflicts'],['TEST-1'])
    def test_bad_answer_and_duplicate_key_rejected(self):
        with self.assertRaises(ValueError):adapt([self.q,self.q])
        q=copy.deepcopy(self.q);q['answer_text']='incorrect'
        with self.assertRaises(ValueError):adapt([q])
    def test_same_stem_and_different_choices_blocked(self):
        q=copy.deepcopy(self.q);q['external_id']='TEST-2'
        q['choices'][1]['text']='had walked'
        with self.assertRaisesRegex(ValueError,'stem hash collision'):adapt([self.q,q])
    def test_pending_grade_preserved(self):
        self.q['recommended_grade']=None
        _,meta,_=adapt([self.q]);self.assertIsNone(meta[0]['metadata']['recommended_grade'])

if __name__=='__main__':unittest.main()
