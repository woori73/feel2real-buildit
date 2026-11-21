import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import paper_mark as pm  # noqa: E402

try:
    import make_sample_pdf as msp  # noqa: E402  (Pillow 필요)
except ImportError:  # CI에 Pillow가 없으면 건너뛴다
    msp = None


@unittest.skipIf(msp is None, 'Pillow not installed')
class SamplePdfPatternTest(unittest.TestCase):
    def test_given_same_kind_when_texture_image_then_matches_paper_mark_model_pixel_by_pixel(self):
        # given — 견본 PDF 그림과 시뮬레이션 모형이 같은 무늬를 써야 측정 결과를 모형과 비교할 수 있다
        for kind, md, ms in (('base', 3, 6), ('mark', 3, 6), ('mark', 4, 8)):
            model = pm.blank(24, 24)
            pm.fill_texture(model, 0, 0, 24, 24, kind, md, ms)
            # when
            img = msp.texture_image(24, 24, kind, 0, md, ms)
            # then
            got = [[1.0 if img.getpixel((x, y)) else 0.0 for x in range(24)] for y in range(24)]
            self.assertEqual(got, model, f'{kind} {md}/{ms}')

    def test_given_seat_when_band_image_then_ink_is_25_percent_like_model(self):
        # given
        img = msp.seat_band_image(555, 48, 0)
        # when
        ink = 1 - sum(1 for v in img.getdata() if v) / (img.width * img.height)
        # then
        self.assertEqual(img.size, (48 * pm.N_BITS, 48))
        self.assertAlmostEqual(ink, 0.25, places=2)

    def test_given_mm_when_converted_then_1200dpi_pixels(self):
        self.assertEqual(msp.mm_to_px(25.4), 1200)
        self.assertEqual(msp.mm_to_px(25), 1181)


if __name__ == '__main__':
    unittest.main()
