import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ai_guard as ag  # noqa: E402
import paper_mark as pm  # noqa: E402


class DecoyDigitsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.band = ag.render_decoy_digits('47')

    def test_given_decoy_band_when_ink_measured_then_every_area_is_25_percent(self):
        # given
        band = self.band
        h, w = len(band), len(band[0])
        # when / then — 숫자 칸과 바탕 칸의 잉크 양이 같다(사람 눈에는 같은 회색)
        self.assertAlmostEqual(pm.ink_coverage(band, 0, 0, w, h), 0.25)
        self.assertAlmostEqual(pm.ink_coverage(band, 48, 48, 48, 48), 0.25)

    def test_given_decoy_band_when_human_view_then_digits_unreadable(self):
        # given
        band = self.band
        # when
        got = ag.read_decoy_digits(pm.human_view(band), 2)
        # then
        self.assertIsNone(got)

    def test_given_decoy_band_when_close_photo_at_any_grid_offset_then_digits_read(self):
        # given — 카메라 픽셀 1칸 = 인쇄 2px(가까이 찍은 사진)
        band = self.band
        for off in (0, 1):
            # when
            got = ag.read_decoy_digits(pm.camera_view(band, 2, off), 2)
            # then
            self.assertEqual(got, '47', f'offset {off}')

    def test_given_close_photo_when_ai_shrinks_input_then_digits_still_read(self):
        # given
        photo = pm.camera_view(self.band, 2, 0)
        # when
        got = ag.read_decoy_digits(pm.ai_input_view(photo, 4), 2)
        # then
        self.assertEqual(got, '47')

    def test_given_non_digit_text_when_render_then_rejected(self):
        with self.assertRaises(ValueError):
            ag.render_decoy_digits('4a')


class DecoyFlagTest(unittest.TestCase):
    def test_given_all_decoys_picked_and_rare_for_humans_when_checked_then_flagged(self):
        # given — 사람만 푼 예비 시험에서 미끼 오답을 고른 비율 5%, 미끼 문항 3개
        # when
        got = ag.decoy_flag(3, 3, 0.05)
        # then — 우연히 3개 모두 고를 확률 0.05³ = 0.000125
        self.assertAlmostEqual(got['p_value'], 0.000125)
        self.assertTrue(got['flag'])

    def test_given_one_decoy_picked_when_checked_then_not_flagged(self):
        # given / when
        got = ag.decoy_flag(1, 3, 0.05)
        # then — 1개는 정직한 학생도 약 14% 확률로 고른다 → 신호 아님
        self.assertAlmostEqual(got['p_value'], 1 - 0.95 ** 3)
        self.assertFalse(got['flag'])

    def test_given_common_decoy_for_humans_when_all_picked_then_not_flagged(self):
        # given — 사람도 30%가 고르는 미끼는 신호가 약하다(미끼 설계를 다시 해야 함)
        # when
        got = ag.decoy_flag(2, 2, 0.3)
        # then
        self.assertFalse(got['flag'])

    def test_given_invalid_counts_or_rate_when_checked_then_rejected(self):
        for args in ((4, 3, 0.05), (0, 0, 0.05), (1, 3, 0.0), (1, 3, 1.0)):
            with self.assertRaises(ValueError, msg=str(args)):
                ag.decoy_flag(*args)


if __name__ == '__main__':
    unittest.main()
