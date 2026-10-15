package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.RankingComputation.RegionKeyed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 순위 배정 순수 로직. 입력은 이미 publication 순서(= total_score DESC, candidate_id ASC)라고 가정한다.
 *   · 전국 순위 = 입력 위치(1..N), 모든 교육자가 고유 순위.
 *   · 지역 순위 = 같은 (province, cityDistrict) 안에서의 위치.
 *   · TOP100 = 전국 순위 <= 100.
 */
class RankingComputationTest {

  private static RegionKeyed p(long id, String prov, String city) { return new RegionKeyed(id, prov, city); }

  @Test
  void assign_overallIsInputPosition_regionIsPositionWithinRegion() {
    var input = List.of(
        p(10, "서울특별시", "강남구"),   // overall 1, 강남 1
        p(11, "경기도", "성남시 분당구"), // overall 2, 분당 1
        p(12, "서울특별시", "강남구"),   // overall 3, 강남 2
        p(13, "경기도", "성남시 분당구"), // overall 4, 분당 2
        p(14, "서울특별시", "마포구"));  // overall 5, 마포 1

    Map<Long, RankingComputation.Ranked> r = RankingComputation.assign(input);

    assertThat(r.get(10L).overallRank()).isEqualTo(1);
    assertThat(r.get(12L).overallRank()).isEqualTo(3);
    assertThat(r.get(14L).overallRank()).isEqualTo(5);
    assertThat(r.get(10L).overallTotal()).isEqualTo(5);

    assertThat(r.get(10L).regionRank()).isEqualTo(1);
    assertThat(r.get(12L).regionRank()).isEqualTo(2);
    assertThat(r.get(12L).regionTotal()).isEqualTo(2);
    assertThat(r.get(11L).regionRank()).isEqualTo(1);
    assertThat(r.get(13L).regionRank()).isEqualTo(2);
    assertThat(r.get(14L).regionRank()).isEqualTo(1);
    assertThat(r.get(14L).regionTotal()).isEqualTo(1);
  }

  @Test
  void assign_top100Boundary() {
    List<RegionKeyed> input = new ArrayList<>();
    IntStream.rangeClosed(1, 130).forEach(i -> input.add(p(i, "서울특별시", i % 2 == 0 ? "강남구" : "마포구")));
    Map<Long, RankingComputation.Ranked> r = RankingComputation.assign(input);

    assertThat(r.get(100L).overallRank()).isEqualTo(100);
    assertThat(r.get(100L).top100()).isTrue();
    assertThat(r.get(101L).overallRank()).isEqualTo(101);
    assertThat(r.get(101L).top100()).isFalse();
    // 전국 100위 밖이어도 지역 순위는 정상
    assertThat(r.get(130L).regionRank()).isPositive();
  }

  @Test
  void assign_emptyAndSingle() {
    assertThat(RankingComputation.assign(List.of())).isEmpty();
    Map<Long, RankingComputation.Ranked> one = RankingComputation.assign(List.of(p(7, "부산광역시", "해운대구")));
    assertThat(one.get(7L).overallRank()).isEqualTo(1);
    assertThat(one.get(7L).regionRank()).isEqualTo(1);
    assertThat(one.get(7L).regionTotal()).isEqualTo(1);
    assertThat(one.get(7L).top100()).isTrue();
  }

  @Test
  void assign_nullRegionFieldsGroupTogether() {
    var input = List.of(p(1, null, null), p(2, "서울특별시", null), p(3, null, null));
    Map<Long, RankingComputation.Ranked> r = RankingComputation.assign(input);
    assertThat(r.get(1L).regionRank()).isEqualTo(1);
    assertThat(r.get(3L).regionRank()).isEqualTo(2);   // 1,3 같은 빈 지역 그룹
    assertThat(r.get(1L).regionTotal()).isEqualTo(2);
    assertThat(r.get(2L).regionRank()).isEqualTo(1);
  }

  @Test
  void region_filtersToRequestedRegion_keepsOverallFromFullList() {
    var input = List.of(
        p(10, "서울특별시", "강남구"),
        p(11, "경기도", "성남시 분당구"),
        p(12, "서울특별시", "강남구"),
        p(13, "경기도", "성남시 분당구"));

    var gangnam = RankingComputation.region(input, "서울특별시", "강남구");
    assertThat(gangnam).extracting(RankingComputation.Ranked::candidateId).containsExactly(10L, 12L);
    assertThat(gangnam.get(0).overallRank()).isEqualTo(1);
    assertThat(gangnam.get(1).overallRank()).isEqualTo(3);   // 전국 기준 유지
    assertThat(gangnam.get(1).regionRank()).isEqualTo(2);

    // province 만 주면 그 시·도 전체
    var gyeonggi = RankingComputation.region(input, "경기도", null);
    assertThat(gyeonggi).extracting(RankingComputation.Ranked::candidateId).containsExactly(11L, 13L);
  }
}
