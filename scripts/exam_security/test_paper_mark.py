import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import paper_mark as pm  # noqa: E402


def two_textures(mark_dot=pm.MARK_DOT, mark_step=pm.MARK_STEP, size=144):
    img = pm.blank(size, size * 2)
    pm.fill_texture(img, 0, 0, size, size, 'base')
    pm.fill_texture(img, size, 0, size, size, 'mark', mark_dot, mark_step)
    return img


def halves_diff(view):
    h, w = len(view), len(view[0])
    half = w // 2
    return pm.region_mean(view, 2, 2, half - 4, h - 4) - pm.region_mean(view, half + 2, 2, half - 4, h - 4)


class TextureTest(unittest.TestCase):
    def test_given_base_and_mark_when_ink_measured_then_both_cover_25_percent(self):
        # given
        img = two_textures()
        # when / then — 잉크 양이 같아 사람 눈에는 같은 회색
        self.assertAlmostEqual(pm.ink_coverage(img, 0, 0, 144, 144), 0.25)
        self.assertAlmostEqual(pm.ink_coverage(img, 144, 0, 144, 144), 0.25)

    def test_given_mark_dot_not_half_of_step_when_fill_then_rejected(self):
        with self.assertRaises(ValueError):
            pm.fill_texture(pm.blank(8, 8), 0, 0, 8, 8, 'mark', 3, 8)


class ViewModelTest(unittest.TestCase):
    def test_given_two_textures_when_human_view_then_mean_brightness_is_equal(self):
        # given
        img = two_textures()
        # when
        diff = halves_diff(pm.human_view(img))
        # then
        self.assertAlmostEqual(diff, 0.0, places=6)

    def test_given_two_textures_when_close_camera_view_then_mark_area_is_darker(self):
        # given — 카메라 픽셀 1칸 = 인쇄 2px(가까이 찍은 사진), 격자 어긋남 0·1
        img = two_textures()
        # when / then — 대비 강화·8비트 자르기 뒤 바탕은 밝아지고 큰 점은 남는다
        for off in (0, 1):
            self.assertGreater(halves_diff(pm.camera_view(img, 2, off)), 0.08)

    def test_given_default_mark_when_far_camera_view_then_result_depends_on_grid_offset(self):
        # given — 카메라 픽셀 1칸 = 인쇄 3px(멀리서 찍은 사진): 기본 무늬(3/6)는 격자 어긋남에 따라
        #          드러나기도 하고 사라지기도 한다(알려진 한계 — 멀리서 찍으면 믿을 수 없다)
        img = two_textures()
        # when
        diffs = [abs(halves_diff(pm.camera_view(img, 3, off))) for off in (0, 1, 2)]
        # then
        self.assertLess(min(diffs), 0.02)
        self.assertGreater(max(diffs), 0.05)

    def test_given_larger_mark_when_far_camera_view_then_still_darker_but_more_visible_to_eye(self):
        # given — 큰 점(4/8)은 멀리서도 드러나지만 사람 눈 모형에서 결이 더 보인다(트레이드오프)
        small, large = two_textures(3, 6), two_textures(4, 8)
        # when
        far = halves_diff(pm.camera_view(large, 3, 0))
        vis_small = pm.texture_visibility(pm.human_view(small), 160, 20, 110, 100)
        vis_large = pm.texture_visibility(pm.human_view(large), 160, 20, 110, 100)
        # then
        self.assertGreater(far, 0.05)
        self.assertGreater(vis_large, vis_small)


class SeatCodeTest(unittest.TestCase):
    def test_given_seat_when_bits_round_trip_then_same_seat_and_bad_check_rejected(self):
        # given
        bits = pm.seat_bits(555)
        # when
        broken = list(bits)
        broken[3] ^= 1
        # then
        self.assertEqual(len(bits), pm.N_BITS)
        self.assertEqual(pm.bits_to_seat(bits), 555)
        self.assertIsNone(pm.bits_to_seat(broken))

    def test_given_seat_strip_when_human_view_then_unreadable(self):
        # given
        strip = pm.render_seat_strip(37)
        # when / then — 사람 눈 모형에서는 고른 회색이라 읽히지 않는다
        self.assertIsNone(pm.read_seat_strip(pm.human_view(strip)))

    def test_given_shifted_bands_when_close_photo_at_any_grid_offset_then_seat_read(self):
        # given
        for seat in (0, 37, 555, 1023):
            bands = pm.render_seat_bands(seat)
            for off in (0, 1):
                # when
                got = pm.read_seat_bands([pm.camera_view(b, 2, off) for b in bands])
                # then
                self.assertEqual(got, seat, f'seat {seat} offset {off}')

    def test_given_close_photo_when_ai_shrinks_input_then_seat_still_read(self):
        # given — AI가 사진을 4배 줄여 받아도 칸(48px) 단위 밝기 차이는 남는다
        photo = pm.camera_view(pm.render_seat_strip(555), 2, 0)
        # when / then
        self.assertEqual(pm.read_seat_strip(pm.ai_input_view(photo, 4)), 555)


class AllocateMarksTest(unittest.TestCase):
    def test_given_budget_when_allocate_then_high_d_regions_first_and_reading_text_protected(self):
        # given — 보기 칸(정답 결정 정보 큼, AI가 최근 잘 풂, 글자 적음)이 본문보다 먼저
        regions = [
            {'id': 'choices', 'area': 0.10, 'answer_info': 0.9, 'ai_trend': 0.6, 'read_cost': 0.3},
            {'id': 'margin', 'area': 0.08, 'answer_info': 0.2, 'ai_trend': 0.6, 'read_cost': 0.05},
            {'id': 'key-sentence', 'area': 0.12, 'answer_info': 1.0, 'ai_trend': 0.6, 'read_cost': 1.0},
            {'id': 'header', 'area': 0.05, 'answer_info': 0.05, 'ai_trend': 0.0, 'read_cost': 0.1},
        ]
        # when
        r = pm.allocate_marks(regions, area_budget=0.20)
        # then
        picked = [x['id'] for x in r['picked']]
        self.assertEqual(picked[0], 'margin')
        self.assertIn('choices', picked)
        self.assertLessEqual(r['used_area'], 0.20)
        reasons = {x['id']: x['reason'] for x in r['skipped']}
        self.assertEqual(reasons['key-sentence'], 'reading_area')


if __name__ == '__main__':
    unittest.main()
