import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import verify_pipeline as vp  # noqa: E402

PASSAGE = ('Many cities are planting trees along busy streets. Trees provide shade, which lowers the '
           'temperature of the pavement during hot summer days.')
CHUNKS = [{'id': 'c1', 'text': PASSAGE}]


def mc(answer=(2,), source=PASSAGE, multi=False):
    return {'type': 'mc', 'questionType': '내용 일치', 'sourceText': source, 'stem': '다음 글의 내용과 일치하는 것은?',
            'options': ['a', 'b', 'c', 'd', 'e'], 'answer': list(answer), 'multi': multi, 'score': 2,
            'conditions': [], 'answerFormat': '', 'acceptedAnswers': [], 'sampleAnswer': '', 'gradingRule': ''}


REFS = [{'id': 'c1', 'quote': 'Trees provide shade'}]


def solver(model, answer):
    return lambda q: {'model': model, 'answer': answer}


class VerifyPipelineTest(unittest.TestCase):
    def test_given_matching_independent_answer_when_verify_then_auto_pass(self):
        # given / when
        c = vp.verify_question(mc(), REFS, CHUNKS, solver=solver('model-b', [2]), generator_model='model-a')
        # then
        self.assertEqual(c.status, '일치')
        self.assertTrue(c.auto_pass)
        self.assertEqual(c.verify_field()['independent_answer'], '2')
        self.assertEqual(c.eval_fields()['verification'], {'model': 'model-b', 'answer': [2]})

    def test_given_different_answer_when_verify_then_mismatch_and_held(self):
        c = vp.verify_question(mc(), REFS, CHUNKS, solver=solver('model-b', [4]), generator_model='model-a')
        self.assertEqual(c.status, '불일치')
        self.assertEqual(vp.release_gate([c])['status'], '검수 대기')

    def test_given_same_model_when_verify_then_not_independent(self):
        c = vp.verify_question(mc(), REFS, CHUNKS, solver=solver('model-a', [2]), generator_model='model-a')
        self.assertEqual(c.status, '')
        self.assertFalse(c.auto_pass)
        self.assertNotIn('verification', c.eval_fields())

    def test_given_two_answers_for_single_answer_question_then_multiple_answer_suspected(self):
        c = vp.verify_question(mc(), REFS, CHUNKS, solver=solver('model-b', [2, 3]), generator_model='model-a')
        self.assertEqual(c.status, '복수정답 의심')

    def test_given_quote_not_in_chunk_when_verify_then_evidence_weak(self):
        refs = [{'id': 'c1', 'quote': 'Trees cause floods'}]
        c = vp.verify_question(mc(), refs, CHUNKS, solver=solver('model-b', [2]), generator_model='model-a')
        self.assertFalse(c.evidence_ok)
        self.assertEqual(c.status, '해설 근거 부족')

    def test_given_rewritten_passage_when_passage_must_match_then_flagged(self):
        changed = 'Many towns are cutting trees near quiet roads. Shade from trees raises the heat of roads at night.'
        ok, issues, ratio = vp.check_evidence(mc(source=changed), REFS, CHUNKS)
        self.assertFalse(ok)
        self.assertEqual(ratio, 0)
        ok2, _, _ = vp.check_evidence(mc(source=changed), REFS, CHUNKS, passage_must_match=False)
        self.assertTrue(ok2)

    def test_given_explanation_with_other_answer_when_verify_then_mismatch(self):
        c = vp.verify_question(mc(), REFS, CHUNKS, solver=solver('model-b', [2]), generator_model='model-a',
                               explanation='정답 ④ · 내용 일치')
        self.assertEqual(c.status, '불일치')

    def test_given_multi_answer_explanation_when_verify_then_all_numbers_compared(self):
        q = mc(answer=(2, 4), multi=True)
        c = vp.verify_question(q, REFS, CHUNKS, solver=solver('model-b', [2, 4]), generator_model='model-a',
                               explanation='정답 ②, ④ · 옳지 않은 것 모두')
        self.assertEqual(c.status, '일치')

    def test_given_bad_contract_when_verify_then_fails_before_solving(self):
        q = mc()
        del q['score']
        called = []
        c = vp.verify_question(q, REFS, CHUNKS, solver=lambda x: called.append(x), generator_model='a')
        self.assertFalse(c.contract_ok)
        self.assertEqual(called, [])

    def test_blind_hides_answer(self):
        self.assertEqual(vp.blind(mc())['answer'], [])

    def test_select_audit_is_reproducible_and_at_least_one(self):
        ids = [f'q{i}' for i in range(25)]
        self.assertEqual(vp.select_audit(ids, 0.1, seed=7), vp.select_audit(ids, 0.1, seed=7))
        self.assertEqual(len(vp.select_audit(ids, 0.1, seed=7)), 2)
        self.assertEqual(len(vp.select_audit(['a'], 0.1, seed=1)), 1)
        self.assertEqual(vp.select_audit([], 0.1), [])


if __name__ == '__main__':
    unittest.main()
