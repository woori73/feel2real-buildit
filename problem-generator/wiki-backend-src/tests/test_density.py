import sys
import unittest
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
import density as dd  # noqa: E402


def q(exam_key, order, *tags):
    return dd.ExamQuestion(exam_key=exam_key, exam_order=order, tags=frozenset(tags))


def sample_stats():
    # 옛 시험 2회: 관계대명사가 드물다. 최근 시험 2회: 관계대명사가 크게 늘었다. 가정법은 꾸준하다.
    questions = [
        q('2025-1-중간', '2025-04', '가정법'), q('2025-1-중간', '2025-04', '가정법'),
        q('2025-1-중간', '2025-04', '수동태'), q('2025-1-중간', '2025-04', '관계대명사'),
        q('2025-1-기말', '2025-07', '가정법'), q('2025-1-기말', '2025-07', '수동태'),
        q('2025-1-기말', '2025-07', '수동태'), q('2025-1-기말', '2025-07', '가정법'),
        q('2025-2-중간', '2025-10', '관계대명사'), q('2025-2-중간', '2025-10', '관계대명사'),
        q('2025-2-중간', '2025-10', '가정법'), q('2025-2-중간', '2025-10', '관계대명사'),
        q('2025-2-기말', '2025-12', '관계대명사'), q('2025-2-기말', '2025-12', '가정법'),
        q('2025-2-기말', '2025-12', '관계대명사'), q('2025-2-기말', '2025-12', '수동태'),
    ]
    return dd.ExamStats(questions)


class ExamStatsTest(unittest.TestCase):
    def test_given_exam_history_when_share_by_period_then_recent_two_exams_are_split_from_prior(self):
        # given
        stats = sample_stats()
        # when
        total = stats.exam_share(['관계대명사'])
        recent = stats.recent_share(['관계대명사'])
        prior = stats.prior_share(['관계대명사'])
        # then
        self.assertAlmostEqual(total, 6 / 16)
        self.assertAlmostEqual(recent, 5 / 8)
        self.assertAlmostEqual(prior, 1 / 8)

    def test_given_no_tags_when_share_then_zero(self):
        # given
        stats = sample_stats()
        # when / then
        self.assertEqual(stats.exam_share([]), 0.0)
        self.assertEqual(stats.recent_share(['없는태그']), 0.0)


class NoiseRatioTest(unittest.TestCase):
    def test_given_repeated_lines_and_symbols_when_noise_ratio_then_higher_than_clean_text(self):
        # given
        clean = 'The man who lives next door is a doctor.\n관계대명사 who는 사람을 꾸민다.'
        noisy = ('다음 글을 읽고 물음에 답하시오.\n■■■■■■■■■■\n---------------\n'
                 'The man who lives next door is a doctor.\n---------------\n■■■■■■■■■■')
        # when
        r_clean = dd.noise_ratio(clean)
        r_noisy = dd.noise_ratio(noisy)
        # then
        self.assertLess(r_clean, r_noisy)
        self.assertTrue(0.0 <= r_clean <= 1.0 and 0.0 <= r_noisy <= 1.0)

    def test_given_empty_text_when_noise_ratio_then_treated_as_all_noise(self):
        self.assertEqual(dd.noise_ratio(''), 1.0)


class DensityScoreTest(unittest.TestCase):
    def test_given_rising_tag_when_density_score_then_ranks_above_steady_tag_of_same_length(self):
        # given — 같은 길이·같은 노이즈의 청크, 관계대명사는 최근 급증, 가정법은 꾸준
        stats = sample_stats()
        text = 'The book which I bought yesterday is interesting.'
        # when
        rising = dd.density_score(text, ['관계대명사'], stats)
        steady = dd.density_score(text, ['가정법'], stats)
        # then
        self.assertGreater(rising, steady)

    def test_given_untagged_chunk_when_density_score_then_zero(self):
        # given
        stats = sample_stats()
        # when / then — 기출 근거가 없는 청크를 D로 끌어올리지 않는다
        self.assertEqual(dd.density_score('아무 문장', [], stats), 0.0)


class AllocateContextTest(unittest.TestCase):
    def test_given_token_budget_when_allocate_then_high_d_chunks_fill_budget_first(self):
        # given — 예산 40토큰(약 100자)
        stats = sample_stats()
        chunks = [
            {'id': 'steady', 'text': 'If I were you, I would study harder for the test.', 'tags': ['가정법'], 'similarity': 0.9},
            {'id': 'rising', 'text': 'The girl who won the prize is my sister.', 'tags': ['관계대명사'], 'similarity': 0.8},
            {'id': 'passive', 'text': 'The window was broken by the boy yesterday.', 'tags': ['수동태'], 'similarity': 0.85},
        ]
        # when
        result = dd.allocate_context(chunks, stats, token_budget=40)
        # then
        ids = [c['id'] for c in result['selected']]
        self.assertEqual(ids[0], 'rising')
        self.assertLessEqual(result['used_tokens'], 40)
        self.assertTrue(all(c['reason'] == 'over_budget' for c in result['dropped']))

    def test_given_low_similarity_chunk_when_allocate_then_dropped_even_if_high_d(self):
        # given — 관계대명사(높은 D)지만 질문과 관련이 낮은 청크
        stats = sample_stats()
        chunks = [{'id': 'off-topic', 'text': 'The girl who won the prize is my sister.', 'tags': ['관계대명사'], 'similarity': 0.1}]
        # when
        result = dd.allocate_context(chunks, stats, token_budget=1000, min_similarity=0.3)
        # then
        self.assertEqual(result['selected'], [])
        self.assertEqual(result['dropped'][0]['reason'], 'low_similarity')


if __name__ == '__main__':
    unittest.main()
