import unittest

from training.newstart.build_dataset import PRIVATE, make_row
from training.newstart.counsel_eval import evaluate


class NewstartTests(unittest.TestCase):
    def test_given_missing_data_when_invented_deadline_then_fail(self):
        cases=[(str(i),'개별 일정이 궁금합니다','담당 창구에 확인해 주세요') for i in range(4)]
        row=make_row(1,'자료없음',cases[0],cases)
        result=evaluate([row],[dict(id=row['id'],answer='기관 확인이 필요하고 3일입니다')])
        self.assertEqual(result['abstain_correct'],0)
        self.assertEqual(result['fabricated_amounts_deadlines'],1)

    def test_given_contact_or_account_when_scanned_then_exclude(self):
        for example in ('010-0000-0000','sample@example.com','123-456-789012','가상로 12'):
            self.assertRegex(example,PRIVATE)
